package com.potflix.data.repository

import com.potflix.data.local.dao.MovieDao
import com.potflix.data.remote.PotFlixApi
import com.potflix.data.remote.PotFlixScraper

import com.potflix.domain.model.Category
import com.potflix.domain.model.Movie
import com.potflix.domain.model.Genre
import com.potflix.domain.model.Season
import com.potflix.domain.model.Episode
import com.potflix.domain.repository.MovieRepository
import javax.inject.Inject
import javax.inject.Singleton

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.potflix.data.remote.TmdbApi

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val movieDao: MovieDao,
    private val api: PotFlixApi,
    private val tmdbApi: TmdbApi,
    @ApplicationContext private val context: Context
) : MovieRepository {

    private val prefs = context.getSharedPreferences("continue_watching_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _watchHistory = MutableStateFlow<List<Movie>>(loadWatchHistory())

    private fun loadWatchHistory(): List<Movie> {
        val json = prefs.getString("history", null) ?: return emptyList()
        val type = object : TypeToken<List<Movie>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val categoriesList = listOf(
        Category("Hollywood", "Hollywood Movies", "movies", "", "🎬"),
        Category("Bollywood", "Bollywood Movies", "movies", "", "🎭"),
        Category("Animation", "Animation Movies", "movies", "", "🧸"),
        Category("Tollywood", "Kolkata Bangla", "movies", "", "🐯"),
        Category("South Indian", "South Indian", "movies", "", "🪷"),
        Category("Foreign", "Foreign Movies", "movies", "", "🌍"),
        Category("TV Series", "English TV Series", "movies", "", "📺"),
        Category("Korean TV Series", "Korean Web Series", "movies", "", "🇰🇷"),
        Category("IMDB Top 250", "IMDb Top 250", "movies", "", "⭐")
    )

    override suspend fun getCategories(): Result<List<Category>> {
        return Result.success(categoriesList)
    }

    override suspend fun getLatestMovies(categoryId: String, limit: Int): Result<List<Movie>> {
        return try {
            val moviesWithDetails = if (categoryId == "IMDB Top 250") {
                movieDao.getImdbTop250Movies(limit)
            } else {
                movieDao.getRandomMoviesByCategory(categoryId, limit)
            }
            Result.success(moviesWithDetails.map { it.toDomainModel() })
        } catch (e: Exception) {
            android.util.Log.e("MovieRepositoryImpl", "Error getting latest movies", e)
            Result.failure(e)
        }
    }

    override suspend fun searchMovies(query: String): Result<List<Movie>> {
        return try {
            val results = movieDao.searchMovies(query)
            Result.success(results.map { it.toDomainModel() })
        } catch (e: Exception) {
            android.util.Log.e("MovieRepositoryImpl", "Error searching movies", e)
            Result.failure(e)
        }
    }

    override suspend fun getMovieDetails(movie: Movie): Result<Movie> {
        if (movie.tmdbId == null) return Result.success(movie)
        
        return try {
            val tmdbDetails = if (movie.type == "tv") {
                api.getTvDetail(movie.tmdbId)
            } else {
                api.getMovieDetail(movie.tmdbId)
            }
            
            val updatedMovie = movie.copy(
                runtime = tmdbDetails.runtime ?: tmdbDetails.episode_run_time?.firstOrNull(),
                language = tmdbDetails.original_language ?: movie.language,
                cast = tmdbDetails.credits?.cast?.map { it.name }?.take(10),
                rating = tmdbDetails.vote_average ?: movie.rating
            )
            Result.success(updatedMovie)
        } catch (e: Exception) {
            android.util.Log.e("MovieRepositoryImpl", "Failed to fetch tmdb details", e)
            Result.success(movie)
        }
    }

    override suspend fun getSeriesEpisodes(url: String): Result<List<Season>> {
        // For TV Shows, we still need to scrape the FTP because TV shows are not fully modeled in movies.db yet
        return try {
            val entries = PotFlixScraper.scrapeDirectory(url)
            val seasons = mutableListOf<Season>()
            
            val seasonFolders = entries.filter { it.isDirectory && Regex("(season|s)\\s*\\d+", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
                .sortedBy { Regex("\\d+").find(it.name)?.value?.toIntOrNull() ?: 0 }
                
            if (seasonFolders.isNotEmpty()) {
                for (sf in seasonFolders) {
                    try {
                        val seasonEntries = PotFlixScraper.scrapeDirectory(sf.url)
                        val episodes = seasonEntries.filter { it.isVideo }.map { e ->
                            val epMatch = Regex("S(\\d{1,2})E(\\d{1,2})", RegexOption.IGNORE_CASE).find(e.name)
                                ?: Regex("E(\\d{1,2})", RegexOption.IGNORE_CASE).find(e.name)
                            Episode(
                                title = if (epMatch != null) "Episode ${epMatch.groupValues.last().toInt()}" else e.name,
                                url = e.url,
                                season = Regex("\\d+").find(sf.name)?.value?.toIntOrNull(),
                                number = epMatch?.groupValues?.last()?.toIntOrNull()
                            )
                        }.sortedBy { it.number ?: 0 }
                        
                        seasons.add(Season(
                            name = sf.name,
                            number = Regex("\\d+").find(sf.name)?.value?.toIntOrNull() ?: 0,
                            episodes = episodes
                        ))
                    } catch (e: Exception) { e.printStackTrace() }
                }
            } else {
                val directVideos = entries.filter { it.isVideo }
                val videosToUse = if (directVideos.isNotEmpty()) directVideos else {
                    val subDirs = entries.filter { it.isDirectory }
                    val allVideos = mutableListOf<com.potflix.data.remote.ScrapedEntry>()
                    for (sub in subDirs) {
                        try {
                            val subEntries = PotFlixScraper.scrapeDirectory(sub.url)
                            allVideos.addAll(subEntries.filter { it.isVideo })
                        } catch (e: Exception) {}
                    }
                    allVideos
                }

                if (videosToUse.isNotEmpty()) {
                    val episodes = videosToUse.map { e ->
                        val epMatch = Regex("E(\\d{1,3})", RegexOption.IGNORE_CASE).find(e.name)
                            ?: Regex("Episode\\s*(\\d{1,3})", RegexOption.IGNORE_CASE).find(e.name)
                        Episode(
                            title = if (epMatch != null) "Episode ${epMatch.groupValues.last().toInt()}" else e.name,
                            url = e.url,
                            season = 1,
                            number = epMatch?.groupValues?.last()?.toIntOrNull() ?: 0
                        )
                    }.sortedBy { it.number ?: 0 }

                    seasons.add(Season(
                        name = "Season 1",
                        number = 1,
                        episodes = episodes
                    ))
                }
            }
            Result.success(seasons)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getFallbackTrending(type: String): Result<List<Movie>> {
        return try {
            val calendar = java.util.Calendar.getInstance()
            val minYear = calendar.get(java.util.Calendar.YEAR) - 2
            
            val highRated = when (type) {
                "tv" -> movieDao.getRecentHighRatedTv(minYear, 10)
                "movie" -> movieDao.getRecentHighRatedMovies(minYear, 10)
                else -> movieDao.getRecentHighRatedAll(minYear, 10)
            }
            Result.success(highRated.map { it.toDomainModel() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrendingSuggestions(type: String): Result<List<Movie>> {
        return try {
            // Ask TMDB for actual world-wide popular content
            val tmdbResponse = when(type) {
                "tv" -> tmdbApi.getPopularTv()
                "movie" -> tmdbApi.getPopularMovies()
                else -> tmdbApi.getTrending() 
            }
            val tmdbIds = tmdbResponse.results.map { it.id.toLong() }
            
            if (tmdbIds.isEmpty()) {
                return getFallbackTrending(type)
            }
            
            // Check which popular items exist on our FTP server
            val localMatches = when(type) {
                "tv" -> movieDao.getTvSeriesByTmdbIds(tmdbIds)
                "movie" -> movieDao.getMoviesByTmdbIds(tmdbIds)
                else -> movieDao.getAllByTmdbIds(tmdbIds)
            }
            
            // Re-sort local matches to strictly preserve TMDB's official global ranking order
            val sortedMatches = localMatches.sortedBy { match -> tmdbIds.indexOf(match.movie.tmdbId) }
            
            if (sortedMatches.isNotEmpty()) {
                Result.success(sortedMatches.map { it.toDomainModel() })
            } else {
                getFallbackTrending(type)
            }
        } catch (e: Exception) {
            android.util.Log.e("MovieRepositoryImpl", "Error getting TMDB trending, falling back", e)
            getFallbackTrending(type)
        }
    }

    override fun getTrendingSuggestionsFlow(type: String): kotlinx.coroutines.flow.Flow<List<Movie>> = kotlinx.coroutines.flow.flow {
        val result = getTrendingSuggestions(type)
        if (result.isSuccess) {
            emit(result.getOrNull() ?: emptyList())
        }
    }

    override suspend fun getMovieStreamUrl(folderUrl: String): Result<String> {
        // If it's a direct file URL, just return it.
        // Or if it's already a quality URL from videos list, just use it.
        return Result.success(folderUrl)
    }

    override suspend fun getTmdbSeasonDetails(tvId: Int, seasonNumber: Int): Result<com.potflix.data.remote.TmdbSeasonResponse> {
        return try {
            val response = api.getTvSeason(tvId, seasonNumber)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGenres(): Result<List<Genre>> {
        return try {
            val entities = movieDao.getAllGenres()
            Result.success(entities.map { Genre(id = it.id, name = it.name) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMoviesByGenre(genreId: Long, type: String, limit: Int): Result<List<Movie>> {
        return try {
            val movies = if (type == "tv") {
                movieDao.getTvSeriesByGenreId(genreId, limit)
            } else {
                movieDao.getMoviesByGenreId(genreId, limit)
            }
            Result.success(movies.map { it.toDomainModel() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addToWatchHistory(movie: Movie) {
        val currentHistory = _watchHistory.value.toMutableList()
        // Remove if already exists to move it to the front
        val existingIndex = currentHistory.indexOfFirst { it.url == movie.url }
        if (existingIndex != -1) {
            currentHistory.removeAt(existingIndex)
        }
        currentHistory.add(0, movie)
        
        // Keep only top 15
        if (currentHistory.size > 15) {
            currentHistory.removeLast()
        }
        
        _watchHistory.value = currentHistory
        
        // Save to SharedPreferences asynchronously so it doesn't block
        prefs.edit().putString("history", gson.toJson(currentHistory)).apply()
    }

    override fun getWatchHistoryFlow(): Flow<List<Movie>> {
        return _watchHistory.asStateFlow()
    }
}
