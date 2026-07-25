package com.potflix.data.repository

import com.potflix.data.remote.PotFlixApi
import com.potflix.data.remote.PotFlixScraper
import com.potflix.domain.model.Category
import com.potflix.domain.model.Movie
import com.potflix.domain.model.Season
import com.potflix.domain.model.Episode
import com.potflix.domain.repository.MovieRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val api: PotFlixApi,
    private val searchIndexManager: SearchIndexManager
) : MovieRepository {

    private val categoriesList = listOf(
        Category("english-movies", "English Movies", "movies", "http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/", "🎬"),
        Category("english-movies-1080p", "English Movies 1080p", "movies", "http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20%281080p%29/", "🎬"),
        Category("hindi-movies", "Hindi Movies", "movies", "http://172.16.50.14/DHAKA-FLIX-14/Hindi%20Movies/", "🎭"),
        Category("south-indian", "South Indian Movies", "movies", "http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/South%20Movies/", "🪷"),
        Category("south-indian-hindi", "South-Movie Hindi Dubbed", "movies", "http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/Hindi%20Dubbed/", "🪷"),
        Category("kolkata-bangla", "Kolkata Bangla Movies", "movies", "http://172.16.50.7/DHAKA-FLIX-7/Kolkata%20Bangla%20Movies/", "🐯"),
        Category("animation", "Animation Movies", "movies", "http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies/", "🧸"),
        Category("animation-1080p", "Animation Movies 1080p", "movies", "http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies%20%281080p%29/", "🧸"),
        Category("foreign", "Foreign Language Movies", "movies", "http://172.16.50.7/DHAKA-FLIX-7/Foreign%20Language%20Movies/", "🌍"),
        Category("imdb-top-250", "IMDb Top 250", "movies", "http://172.16.50.14/DHAKA-FLIX-14/IMDb%20Top-250%20Movies/", "⭐"),
        Category("3d-movies", "3D Movies", "movies", "http://172.16.50.7/DHAKA-FLIX-7/3D%20Movies/", "👓"),
        Category("english-tv", "TV & WEB Series", "tv", "http://172.16.50.12/DHAKA-FLIX-12/TV-WEB-Series/", "📺"),
        Category("korean-tv", "Korean TV & WEB Series", "tv", "http://172.16.50.14/DHAKA-FLIX-14/KOREAN%20TV%20%26%20WEB%20Series/", "🇰🇷"),
        Category("cartoon-tv", "Cartoon TV Series", "tv", "http://172.16.50.9/DHAKA-FLIX-9/Anime%20%26%20Cartoon%20TV%20Series/", "🦄"),
        Category("documentary", "Documentary", "movies", "http://172.16.50.9/DHAKA-FLIX-9/Documentary/", "🎥"),
        Category("wwe", "WWE & AEW Wrestling", "tv", "http://172.16.50.9/DHAKA-FLIX-9/WWE%20%26%20AEW%20Wrestling/", "🤼"),
        Category("awards", "Award & TV Shows", "tv", "http://172.16.50.9/DHAKA-FLIX-9/Awards%20%26%20TV%20Shows/", "🏆"),
        Category("satyajit-ray", "Satyajit Ray Films", "movies", "http://172.16.50.7/DHAKA-FLIX-7/Kolkata%20Bangla%20Movies/Satyajit%20Ray%20Films/", "🎞️")
    )

    init {
        searchIndexManager.buildIndexInBackground(categoriesList)
    }

    override suspend fun getCategories(): Result<List<Category>> {
        return Result.success(categoriesList)
    }

    override suspend fun getLatestMovies(categoryId: String, limit: Int): Result<List<Movie>> {
        return try {
            val category = categoriesList.find { it.id == categoryId }
                ?: return Result.failure(Exception("Category not found"))

            val entries = PotFlixScraper.scrapeDirectory(category.url)
            val results = mutableListOf<Movie>()
            
            // Strategy 1: Year folders
            val yearFolders = entries.filter { it.isDirectory && it.type == "yearFolder" }
                .sortedByDescending { it.year ?: 0 }
                .take(3)
                
            if (yearFolders.isNotEmpty()) {
                for (yf in yearFolders) {
                    if (results.size >= 30) break
                    try {
                        val movies = PotFlixScraper.scrapeDirectory(yf.url)
                        val movieEntries = movies.filter { it.isDirectory && (it.type == "movie" || it.type == "tv") }
                        results.addAll(movieEntries.take(30 - results.size).map {
                            Movie(
                                title = it.title ?: it.name,
                                url = it.url,
                                year = it.year,
                                quality = it.quality,
                                type = it.type ?: "movie",
                                categoryId = category.id
                            )
                        })
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }

            // Strategy 2: Direct movies
            if (results.isEmpty()) {
                val direct = entries.filter { it.isDirectory && (it.type == "movie" || it.type == "tv") }
                results.addAll(direct.take(30).map {
                    Movie(
                        title = it.title ?: it.name,
                        url = it.url,
                        year = it.year,
                        quality = it.quality,
                        type = it.type ?: "movie",
                        categoryId = category.id
                    )
                })
            }

            // Strategy 3: Auto-flatten
            if (results.isEmpty()) {
                val subfolders = entries.filter { it.isDirectory }
                
                // If it's a TV series root, it might have grouping folders like A-Z or 0-9
                val isAlphabetGrouping = subfolders.any { 
                    it.name.contains("TV Series", ignoreCase = true) || 
                    it.name.contains("★", ignoreCase = true) ||
                    Regex("^[A-Z]\\s*-\\s*[A-Z]$").matches(it.name)
                }

                val foldersToCrawl = if (isAlphabetGrouping) subfolders else subfolders.take(8)

                coroutineScope {
                    val crawls = foldersToCrawl.map { sf ->
                        async {
                            try {
                                PotFlixScraper.scrapeDirectory(sf.url)
                                    .filter { it.isDirectory && (it.type == "movie" || it.type == "tv") }
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }
                    crawls.awaitAll().forEach { list ->
                        if (results.size < 30) {
                            results.addAll(list.take(30 - results.size).map {
                                Movie(
                                    title = it.title ?: it.name,
                                    url = it.url,
                                    year = it.year,
                                    quality = it.quality,
                                    type = it.type ?: "movie",
                                    categoryId = category.id
                                )
                            })
                        }
                    }
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMovies(query: String): Result<List<Movie>> {
        return try {
            val q = query.lowercase().trim()
            val words = q.split(Regex("\\s+"))
            
            val index = searchIndexManager.getIndex()
            val scored = index.mapNotNull { entry ->
                val titleLower = (entry.title ?: entry.name).lowercase()
                var score = 0
                if (titleLower.contains(q)) {
                    score = if (titleLower.startsWith(q)) 100 else 80
                } else if (words.all { titleLower.contains(it) }) {
                    score = 60
                } else if (words.size >= 2) {
                    val matches = words.count { titleLower.contains(it) }
                    if (matches >= words.size * 0.6) {
                        score = 30 + (matches * 20 / words.size)
                    }
                }
                
                if (score > 0) Pair(entry, score) else null
            }.sortedByDescending { it.second }
            
            val results = scored.take(50).map { (it, _) ->
                Movie(
                    title = it.title ?: it.name,
                    url = it.url,
                    year = it.year,
                    quality = it.quality,
                    type = it.type ?: "movie"
                )
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMovieDetails(movie: Movie): Result<Movie> {
        return try {
            val searchType = if (movie.type == "tv") "tv" else "movie"
            val tmdbRes = if (movie.type == "tv") {
                api.searchTv(query = movie.title, year = movie.year?.toString())
            } else {
                api.searchMovie(query = movie.title, year = movie.year?.toString())
            }
            
            if (tmdbRes.results.isNotEmpty()) {
                val tmdbMovie = tmdbRes.results.first()
                var trailerKey: String? = null
                try {
                    val videos = if (movie.type == "tv") {
                        api.getTvVideos(tmdbMovie.id)
                    } else {
                        api.getMovieVideos(tmdbMovie.id)
                    }
                    val trailer = videos.results.find { it.site == "YouTube" && it.type == "Trailer" }
                        ?: videos.results.find { it.site == "YouTube" }
                    trailerKey = trailer?.key
                } catch (e: Exception) { e.printStackTrace() }
                
                Result.success(movie.copy(
                    overview = tmdbMovie.overview,
                    poster = tmdbMovie.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                    backdrop = tmdbMovie.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    rating = tmdbMovie.voteAverage,
                    releaseDate = tmdbMovie.releaseDate ?: tmdbMovie.firstAirDate,
                    trailerKey = trailerKey
                ))
            } else {
                Result.success(movie)
            }
        } catch (e: Exception) {
            Result.success(movie)
        }
    }

    override suspend fun getSeriesEpisodes(url: String): Result<List<Season>> {
        return try {
            val entries = PotFlixScraper.scrapeDirectory(url)
            val seasons = mutableListOf<Season>()
            
            // Find explicit season folders (e.g., "Season 1", "S01")
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
                // Check if there are direct episodes in the root folder or inside a single subfolder (like "1080p")
                val directVideos = entries.filter { it.isVideo }
                
                val videosToUse = if (directVideos.isNotEmpty()) {
                    directVideos
                } else {
                    // Traverse into any subdirectories and find videos
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

    override suspend fun getTrendingSuggestions(type: String): Result<List<Movie>> {
        return try {
            val res = if (type == "tv") api.getTrendingTv() else api.getTrendingMovies()
            val movies = res.results.map { tmdbMovie ->
                Movie(
                    title = tmdbMovie.title ?: tmdbMovie.name ?: "",
                    url = "",
                    year = (tmdbMovie.releaseDate ?: tmdbMovie.firstAirDate)?.take(4)?.toIntOrNull(),
                    type = type,
                    overview = tmdbMovie.overview,
                    poster = tmdbMovie.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                    backdrop = tmdbMovie.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    rating = tmdbMovie.voteAverage
                )
            }
            // Ideally we cross-reference this with the local index to populate the URLs,
            // but for suggestions that's complex, we just return the TMDB data.
            Result.success(movies)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getTrendingSuggestionsFlow(type: String): kotlinx.coroutines.flow.Flow<List<Movie>> = kotlinx.coroutines.flow.flow {
        val result = getTrendingSuggestions(type)
        if (result.isSuccess) {
            emit(result.getOrNull() ?: emptyList())
        }
    }

    override suspend fun getMovieStreamUrl(folderUrl: String): Result<String> {
        return try {
            val ext = folderUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
            if (ext in listOf("mkv", "mp4", "avi", "wmv", "mov", "flv", "webm", "ts", "m2ts", "vob", "m4v")) {
                return Result.success(folderUrl)
            }

            val entries = PotFlixScraper.scrapeDirectory(folderUrl)
            val video = entries.find { it.isVideo }
            if (video != null) {
                return Result.success(video.url)
            }

            // Check subfolders if video is nested in a subdirectory
            val subFolders = entries.filter { it.isDirectory }
            for (sub in subFolders) {
                val subEntries = PotFlixScraper.scrapeDirectory(sub.url)
                val subVideo = subEntries.find { it.isVideo }
                if (subVideo != null) {
                    return Result.success(subVideo.url)
                }
            }

            Result.failure(Exception("No video file found in folder"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTmdbSeasonDetails(tvId: Int, seasonNumber: Int): Result<com.potflix.data.remote.TmdbSeasonResponse> {
        return Result.failure(Exception("Not implemented in this repository"))
    }
}
