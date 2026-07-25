package com.potflix.data.repository

import com.potflix.BuildConfig
import com.potflix.data.local.dao.MovieDao
import com.potflix.data.remote.PotFlixScraper
import com.potflix.data.remote.TmdbApi
import com.potflix.domain.model.*
import com.potflix.domain.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalScraperRepositoryImpl @Inject constructor(
    private val movieDao: MovieDao,
    private val tmdbApi: TmdbApi,
    private val cacheManager: com.potflix.data.local.cache.CacheManager
) : MovieRepository {

    override suspend fun getCategories(): Result<List<Category>> = withContext(Dispatchers.IO) {
        try {
            val categories = movieDao.getCategories().map { it.toDomainModel() }
            if (categories.isEmpty()) {
                // Return defaults if database is empty
                return@withContext Result.success(listOf(
                    Category("english-movies", "English Movies", "http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/", "movie", "🎬"),
                    Category("tv-web-series", "TV & WEB Series", "http://172.16.50.12/DHAKA-FLIX-12/TV-WEB-Series/", "tv", "📺")
                ))
            }
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun List<com.potflix.data.local.entity.MovieEntity>.deduplicate(): List<com.potflix.data.local.entity.MovieEntity> {
        return this.groupBy { it.title.trim().lowercase() }
            .map { (_, versions) ->
                versions.maxByOrNull {
                    when {
                        it.quality?.contains("4k", ignoreCase = true) == true || it.quality?.contains("2160p") == true -> 4
                        it.quality?.contains("1080p") == true -> 3
                        it.quality?.contains("720p") == true -> 2
                        it.quality?.contains("480p") == true -> 1
                        else -> 0
                    }
                } ?: versions.first()
            }
    }

    override suspend fun getLatestMovies(categoryId: String, limit: Int): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            // Fetch more to ensure we have enough after deduplication
            val entities = movieDao.getMoviesByCategory(categoryId, limit * 3).deduplicate().take(limit)
            Result.success(entities.map { it.toDomainModel() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMovies(query: String): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val entities = movieDao.searchMovies(query).deduplicate()
            Result.success(entities.map { it.toDomainModel() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMovieDetails(movie: Movie): Result<Movie> = withContext(Dispatchers.IO) {
        try {
            val searchType = if (movie.type == "tv") "tv" else "movie"
            val response = if (searchType == "tv") {
                tmdbApi.searchTv(movie.title, movie.year)
            } else {
                tmdbApi.searchMovie(movie.title, movie.year)
            }

            val result = response.results.firstOrNull()
            if (result != null) {
                // Fetch full details for cast, genres, runtime, language
                val detailResponse = try {
                    if (searchType == "tv") {
                        tmdbApi.getTvDetail(result.id)
                    } else {
                        tmdbApi.getMovieDetail(result.id)
                    }
                } catch (e: Exception) {
                    null
                }

                val tmdbPoster = result.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                val tmdbBackdrop = result.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
                
                val mappedGenres = detailResponse?.genres?.map { it.name }
                val mappedCast = detailResponse?.credits?.cast?.sortedBy { it.order }?.take(10)?.map { it.name }
                val mappedRuntime = detailResponse?.runtime ?: detailResponse?.episode_run_time?.firstOrNull()
                val mappedLanguage = detailResponse?.original_language?.uppercase()
                
                // Always create the enriched movie model to return to the UI instantly
                val enrichedMovie = movie.copy(
                    tmdbId = result.id,
                    poster = movie.poster.takeIf { !it.isNullOrEmpty() && !it.endsWith("\$it") } ?: tmdbPoster,
                    backdrop = movie.backdrop.takeIf { !it.isNullOrEmpty() && !it.endsWith("\$it") } ?: tmdbBackdrop,
                    overview = movie.overview.takeIf { !it.isNullOrEmpty() } ?: result.overview,
                    rating = movie.rating.takeIf { it != null && it > 0.0 } ?: result.vote_average,
                    releaseDate = movie.releaseDate.takeIf { !it.isNullOrEmpty() } ?: (result.release_date ?: result.first_air_date),
                    genres = movie.genres ?: mappedGenres,
                    cast = movie.cast ?: mappedCast,
                    runtime = movie.runtime ?: mappedRuntime,
                    language = movie.language ?: mappedLanguage
                )

                // Update the local database if it was missing this TMDB data
                if (movie.poster.isNullOrEmpty() || movie.poster.endsWith("\$it") || movie.overview.isNullOrEmpty() || movie.cast == null) {
                    val entity = movieDao.getMovieByUrl(movie.url)
                    if (entity != null) {
                        val updatedEntity = entity.copy(
                            poster = enrichedMovie.poster,
                            backdrop = enrichedMovie.backdrop,
                            overview = enrichedMovie.overview,
                            rating = enrichedMovie.rating,
                            releaseDate = enrichedMovie.releaseDate,
                            genres = enrichedMovie.genres?.joinToString(","),
                            cast = enrichedMovie.cast?.joinToString(","),
                            runtime = enrichedMovie.runtime,
                            language = enrichedMovie.language
                        )
                        movieDao.updateMovie(updatedEntity)
                    }
                }
                
                return@withContext Result.success(enrichedMovie)
            }
            Result.success(movie)
        } catch (e: Exception) {
            Result.success(movie) // Return original on TMDB failure
        }
    }

    override suspend fun getSeriesEpisodes(url: String): Result<List<Season>> = withContext(Dispatchers.IO) {
        try {
            val entries = PotFlixScraper.scrapeDirectory(url)
            val seasons = mutableListOf<Season>()
            
            val seasonFolders = entries.filter { 
                it.isDirectory && it.name.contains("Season", ignoreCase = true) 
            }.sortedBy { 
                Regex("\\d+").find(it.name)?.value?.toIntOrNull() ?: 0 
            }

            for (sf in seasonFolders) {
                val seasonEntries = PotFlixScraper.scrapeDirectory(sf.url)
                val episodes = seasonEntries.filter { it.isVideo }.mapNotNull {
                    val epMatch = Regex("S(\\d{1,2})E(\\d{1,2})", RegexOption.IGNORE_CASE).find(it.name)
                    Episode(
                        title = it.name,
                        url = it.url,
                        season = epMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                        number = epMatch?.groupValues?.get(2)?.toIntOrNull() ?: 1,
                        quality = it.quality
                    )
                }.sortedBy { it.number }

                if (episodes.isNotEmpty()) {
                    seasons.add(Season(
                        name = sf.name,
                        number = Regex("\\d+").find(sf.name)?.value?.toIntOrNull() ?: 0,
                        episodes = episodes
                    ))
                }
            }
            
            // Loose videos without season folders
            val looseVideos = entries.filter { it.isVideo }.map {
                Episode(
                    title = it.name,
                    url = it.url,
                    season = 1,
                    number = Regex("S(\\d{1,2})E(\\d{1,2})", RegexOption.IGNORE_CASE).find(it.name)?.groupValues?.get(2)?.toIntOrNull() ?: 1,
                    quality = it.quality
                )
            }
            
            if (looseVideos.isNotEmpty()) {
                seasons.add(Season("Episodes", 1, looseVideos))
            }

            Result.success(seasons)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrendingSuggestions(type: String): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            // First fetch from TMDB
            val tmdbMovies = when (type) {
                "movie" -> tmdbApi.getNowPlayingMovies().results
                "tv" -> tmdbApi.getPopularTv().results
                else -> tmdbApi.getTrending().results
            }
            
            // Try to match with local DB
            val matchedMovies = mutableListOf<Movie>()
            for (tmdb in tmdbMovies) {
                val title = (tmdb.title ?: tmdb.name)?.lowercase() ?: continue
                val localMatches = movieDao.searchMovies(title)
                if (localMatches.isNotEmpty()) {
                    val bestMatch = localMatches.first()
                    // Update the local match with TMDB data
                    val updatedEntity = bestMatch.copy(
                        poster = tmdb.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        backdrop = tmdb.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        overview = tmdb.overview,
                        rating = tmdb.vote_average,
                        releaseDate = tmdb.release_date ?: tmdb.first_air_date
                    )
                    movieDao.updateMovie(updatedEntity)
                    matchedMovies.add(updatedEntity.toDomainModel())
                }
            }
            
            // Fallback to random movies from local DB if no matches
            if (matchedMovies.isEmpty()) {
                val randomDbMovies = if (type == "all") {
                    movieDao.getRandomMoviesAll(30).deduplicate().take(15)
                } else {
                    movieDao.getRandomMovies(type, 30).deduplicate().take(15)
                }
                matchedMovies.addAll(randomDbMovies.map { it.toDomainModel() })
            }
            
            Result.success(matchedMovies)
        } catch (e: Exception) {
            val random = if (type == "all") {
                movieDao.getRandomMoviesAll(30).deduplicate().take(15)
            } else {
                movieDao.getRandomMovies(type, 30).deduplicate().take(15)
            }
            Result.success(random.map { it.toDomainModel() })
        }
    }

    override fun getTrendingSuggestionsFlow(type: String): kotlinx.coroutines.flow.Flow<List<Movie>> = kotlinx.coroutines.flow.flow {
        // 1. Emit instantly from cache
        val cached = cacheManager.getTrendingCache(type)
        if (cached.isNotEmpty()) {
            emit(cached)
        }
        
        // 2. Fetch fresh from network & DB
        val freshResult = getTrendingSuggestions(type)
        freshResult.onSuccess { freshList ->
            if (freshList.isNotEmpty()) {
                cacheManager.saveTrendingCache(type, freshList)
                emit(freshList)
            }
        }
    }

    override suspend fun getMovieStreamUrl(folderUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val entries = PotFlixScraper.scrapeDirectory(folderUrl)
            val video = entries.firstOrNull { it.isVideo }
            if (video != null) {
                Result.success(video.url)
            } else {
                Result.failure(Exception("No video file found in directory"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTmdbSeasonDetails(tvId: Int, seasonNumber: Int): Result<com.potflix.data.remote.TmdbSeasonResponse> = withContext(Dispatchers.IO) {
        try {
            val response = tmdbApi.getTvSeason(tvId, seasonNumber)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
