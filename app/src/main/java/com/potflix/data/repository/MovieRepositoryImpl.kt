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
    private val serverPreferences: com.potflix.data.local.preferences.ServerPreferences,
    private val aListScraper: com.potflix.data.remote.AListScraper,
    private val firebaseSyncManager: com.potflix.data.remote.FirebaseSyncManager,
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

    override suspend fun getCategories(): Result<List<Category>> {
        return try {
            val distinctNames = movieDao.getDistinctCategories()
            
            // Format "movies-english" into "Movies English"
            val dynamicCategories = distinctNames.map { rawName ->
                val formattedTitle = rawName.split("-").joinToString(" ") { word -> 
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                }
                
                // Try to assign a relevant emoji
                val emoji = when {
                    rawName.contains("animation", ignoreCase = true) -> "🧸"
                    rawName.contains("hindi", ignoreCase = true) -> "🎭"
                    rawName.contains("korean", ignoreCase = true) -> "🇰🇷"
                    rawName.contains("bangla", ignoreCase = true) -> "🐯"
                    rawName.contains("foreign", ignoreCase = true) -> "🌍"
                    rawName.contains("tv", ignoreCase = true) -> "📺"
                    else -> "🎬"
                }

                Category(
                    id = rawName,
                    name = formattedTitle,
                    type = "movies", // Default, not heavily used for filtering right now
                    url = "", 
                    icon = emoji
                )
            }
            
            Result.success(dynamicCategories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLatestMovies(categoryId: String, limit: Int): Result<List<Movie>> {
        return try {
            val moviesWithDetails = if (categoryId == "IMDB Top 250") {
                movieDao.getImdbTop250Movies(limit)
            } else {
                movieDao.getRandomMoviesByCategory(categoryId, limit)
            }
            val activeServer = serverPreferences.activeServer.value
            Result.success(moviesWithDetails.map { it.toDomainModel(activeServer) })
        } catch (e: Exception) {
            android.util.Log.e("MovieRepositoryImpl", "Error getting latest movies", e)
            Result.failure(e)
        }
    }

    override suspend fun searchMovies(query: String): Result<List<Movie>> {
        return try {
            val results = movieDao.searchMovies(query)
            val activeServer = serverPreferences.activeServer.value
            Result.success(results.map { it.toDomainModel(activeServer) })
        } catch (e: Exception) {
            android.util.Log.e("MovieRepositoryImpl", "Error searching movies", e)
            Result.failure(e)
        }
    }

    override suspend fun getMovieDetails(movie: Movie): Result<Movie> {
        var currentMovie = movie
        try {
            val dbMovie = movieDao.getMovieByTitle(movie.title) ?: movieDao.getMovieByUrlOrTitle(movie.url, movie.title)
            if (dbMovie != null) {
                val activeServer = serverPreferences.activeServer.value
                val domain = dbMovie.toDomainModel(activeServer)
                currentMovie = currentMovie.copy(
                    title = if (domain.title.isNotBlank()) domain.title else currentMovie.title,
                    tmdbId = domain.tmdbId ?: currentMovie.tmdbId,
                    poster = domain.poster ?: currentMovie.poster,
                    backdrop = domain.backdrop ?: currentMovie.backdrop,
                    overview = domain.overview ?: currentMovie.overview,
                    rating = domain.rating ?: currentMovie.rating,
                    year = if ((domain.year ?: 0) > 0) domain.year else currentMovie.year
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("MovieRepositoryImpl", "Could not check local DB for updated movie details", e)
        }

        val tmdbId = currentMovie.tmdbId ?: return Result.success(currentMovie)
        
        return try {
            val tmdbDetails = if (currentMovie.type == "tv") {
                api.getTvDetail(tmdbId)
            } else {
                api.getMovieDetail(tmdbId)
            }
            
            val updatedMovie = currentMovie.copy(
                runtime = tmdbDetails.runtime ?: tmdbDetails.episode_run_time?.firstOrNull(),
                language = tmdbDetails.original_language ?: currentMovie.language,
                cast = tmdbDetails.credits?.cast?.map { it.name }?.take(10),
                rating = tmdbDetails.vote_average ?: currentMovie.rating
            )
            Result.success(updatedMovie)
        } catch (e: Exception) {
            android.util.Log.e("MovieRepositoryImpl", "Failed to fetch tmdb details", e)
            Result.success(currentMovie)
        }
    }

    override suspend fun getSeriesEpisodes(url: String): Result<List<Season>> {
        return try {
            val activeServer = serverPreferences.activeServer.value
            val scraper = if (activeServer.type == com.potflix.data.local.preferences.ServerType.ALIST) aListScraper else PotFlixScraper
            
            val mappedUrl = if (activeServer.type == com.potflix.data.local.preferences.ServerType.ALIST) {
                com.potflix.util.ServerUrlMapper.mapUrl(url, activeServer)
            } else url

            val entries = scraper.scrapeDirectory(mappedUrl)
            val seasonFolderRegex = Regex("""(?:season|s)[.\s_-]*(\d{1,2})""", RegexOption.IGNORE_CASE)
            val seasonFolders = entries.filter { it.isDirectory && seasonFolderRegex.containsMatchIn(it.name) }
                .sortedBy { seasonFolderRegex.find(it.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }

            val allEpisodes = mutableListOf<Episode>()

            if (seasonFolders.isNotEmpty()) {
                for (sf in seasonFolders) {
                    val sfSeasonNum = seasonFolderRegex.find(sf.name)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("\\d+").find(sf.name)?.value?.toIntOrNull() ?: 1
                    try {
                        val seasonEntries = scraper.scrapeDirectory(sf.url)
                        val videoEntries = seasonEntries.filter { it.isVideo }
                        for (e in videoEntries) {
                            val sMatch = Regex("""S(\d{1,2})[.\s_-]*E(\d{1,2})""", RegexOption.IGNORE_CASE).find(e.name)
                                ?: Regex("""(?:season|s)[.\s_-]*(\d{1,2})[.\s_-]*(?:episode|ep|e)[.\s_-]*(\d{1,2})""", RegexOption.IGNORE_CASE).find(e.name)
                            val epOnlyMatch = Regex("""(?:episode|ep|e)[.\s_-]*(\d{1,3})""", RegexOption.IGNORE_CASE).find(e.name)

                            val epSeason = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: sfSeasonNum
                            val epNumber = sMatch?.groupValues?.get(2)?.toIntOrNull()
                                ?: epOnlyMatch?.groupValues?.get(1)?.toIntOrNull()

                            val resolvedEpisodeUrl = if (activeServer.type == com.potflix.data.local.preferences.ServerType.ALIST && e.url.contains(activeServer.baseUrl)) {
                                e.url.replace(activeServer.baseUrl, activeServer.baseUrl.trimEnd('/') + "/d/")
                            } else e.url

                            allEpisodes.add(Episode(
                                title = if (epNumber != null) "Episode $epNumber" else e.name,
                                url = resolvedEpisodeUrl,
                                season = epSeason,
                                number = epNumber
                            ))
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            } else {
                // No explicit "season" folders. Could be direct video files or multi-season pack folders (like S01-S04, Complete, etc.)
                val directVideos = entries.filter { it.isVideo }
                val videosToUse = if (directVideos.isNotEmpty()) directVideos else {
                    val subDirs = entries.filter { it.isDirectory }
                    val allVideos = mutableListOf<com.potflix.data.remote.ScrapedEntry>()
                    for (sub in subDirs) {
                        try {
                            val subEntries = scraper.scrapeDirectory(sub.url)
                            allVideos.addAll(subEntries.filter { it.isVideo })
                        } catch (e: Exception) {}
                    }
                    allVideos
                }

                for (e in videosToUse) {
                    val sMatch = Regex("""S(\d{1,2})[.\s_-]*E(\d{1,2})""", RegexOption.IGNORE_CASE).find(e.name)
                        ?: Regex("""(?:season|s)[.\s_-]*(\d{1,2})[.\s_-]*(?:episode|ep|e)[.\s_-]*(\d{1,2})""", RegexOption.IGNORE_CASE).find(e.name)
                    val epOnlyMatch = Regex("""(?:episode|ep|e)[.\s_-]*(\d{1,3})""", RegexOption.IGNORE_CASE).find(e.name)

                    val epSeason = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val epNumber = sMatch?.groupValues?.get(2)?.toIntOrNull()
                        ?: epOnlyMatch?.groupValues?.get(1)?.toIntOrNull()

                    val resolvedEpisodeUrl = if (activeServer.type == com.potflix.data.local.preferences.ServerType.ALIST && e.url.contains(activeServer.baseUrl)) {
                        e.url.replace(activeServer.baseUrl, activeServer.baseUrl.trimEnd('/') + "/d/")
                    } else e.url

                    allEpisodes.add(Episode(
                        title = if (epNumber != null) "Episode $epNumber" else e.name,
                        url = resolvedEpisodeUrl,
                        season = epSeason,
                        number = epNumber
                    ))
                }
            }

            val seasons = mutableListOf<Season>()
            val groupedBySeason = allEpisodes.groupBy { it.season ?: 1 }
            for ((seasonNum, eps) in groupedBySeason.toSortedMap()) {
                seasons.add(Season(
                    name = "Season $seasonNum",
                    number = seasonNum,
                    episodes = eps.sortedBy { it.number ?: 0 }
                ))
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
            val activeServer = serverPreferences.activeServer.value
            Result.success(highRated.map { it.toDomainModel(activeServer) })
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
                val activeServer = serverPreferences.activeServer.value
                Result.success(sortedMatches.map { it.toDomainModel(activeServer) })
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
        val isDirectVideo = folderUrl.endsWith(".mkv", true) || 
                            folderUrl.endsWith(".mp4", true) || 
                            folderUrl.endsWith(".avi", true) || 
                            folderUrl.endsWith(".webm", true) ||
                            folderUrl.endsWith(".ts", true)
        if (isDirectVideo) {
            return Result.success(folderUrl)
        }

        return try {
            val activeServer = serverPreferences.activeServer.value
            val scraper = if (activeServer.type == com.potflix.data.local.preferences.ServerType.ALIST) aListScraper else PotFlixScraper
            val mappedUrl = if (activeServer.type == com.potflix.data.local.preferences.ServerType.ALIST) {
                com.potflix.util.ServerUrlMapper.mapUrl(folderUrl, activeServer)
            } else folderUrl

            val entries = scraper.scrapeDirectory(mappedUrl)
            val videos = entries.filter { it.isVideo }
            val getQualityScore: (com.potflix.data.remote.ScrapedEntry) -> Int = { entry ->
                when (entry.quality?.lowercase()) {
                    "4k", "2160p" -> 4
                    "1080p" -> 3
                    "720p" -> 2
                    "480p" -> 1
                    else -> 0
                }
            }
            if (videos.isNotEmpty()) {
                val best = videos.maxByOrNull(getQualityScore) ?: videos.first()
                val resolvedUrl = if (activeServer.type == com.potflix.data.local.preferences.ServerType.ALIST && best.url.contains(activeServer.baseUrl)) {
                    best.url.replace(activeServer.baseUrl, activeServer.baseUrl.trimEnd('/') + "/d/")
                } else best.url
                Result.success(resolvedUrl)
            } else {
                val subDirs = entries.filter { it.isDirectory }
                for (sub in subDirs) {
                    try {
                        val subEntries = scraper.scrapeDirectory(sub.url)
                        val subVideos = subEntries.filter { it.isVideo }
                        if (subVideos.isNotEmpty()) {
                            val best = subVideos.maxByOrNull(getQualityScore) ?: subVideos.first()
                            val resolvedUrl = if (activeServer.type == com.potflix.data.local.preferences.ServerType.ALIST && best.url.contains(activeServer.baseUrl)) {
                                best.url.replace(activeServer.baseUrl, activeServer.baseUrl.trimEnd('/') + "/d/")
                            } else best.url
                            return Result.success(resolvedUrl)
                        }
                    } catch (e: Exception) {}
                }
                Result.failure(Exception("No video stream found in folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            val activeServer = serverPreferences.activeServer.value
            Result.success(movies.map { it.toDomainModel(activeServer) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addToWatchHistory(movie: Movie) {
        val currentHistory = _watchHistory.value.toMutableList()
        // Remove if already exists to move it to the front
        val existingIndex = currentHistory.indexOfFirst { it.url == movie.url }
        val existingMovie = if (existingIndex != -1) currentHistory.removeAt(existingIndex) else null
        
        // Preserve existing playback data when re-inserting
        val movieToInsert = if (existingMovie != null) {
            movie.copy(
                playbackPosition = existingMovie.playbackPosition ?: movie.playbackPosition,
                duration = existingMovie.duration ?: movie.duration,
                lastPlayedStreamUrl = existingMovie.lastPlayedStreamUrl ?: movie.lastPlayedStreamUrl,
                isWatched = existingMovie.isWatched
            )
        } else {
            movie
        }
        
        currentHistory.add(0, movieToInsert)
        
        // Keep only top 15
        if (currentHistory.size > 15) {
            currentHistory.removeLast()
        }
        
        _watchHistory.value = currentHistory
        
        // Save to SharedPreferences asynchronously so it doesn't block
        prefs.edit().putString("history", gson.toJson(currentHistory)).apply()
        firebaseSyncManager.syncWatchHistory(currentHistory)
    }

    override fun getWatchHistoryFlow(): Flow<List<Movie>> {
        return _watchHistory.asStateFlow()
    }

    override suspend fun updateWatchProgress(movieUrl: String, streamUrl: String, position: Long, duration: Long) {
        if (position <= 0L && duration <= 0L) return // Nothing to save
        
        val currentHistory = _watchHistory.value.toMutableList()
        val index = currentHistory.indexOfFirst { it.url == movieUrl }
        val isWatched = duration > 0 && position >= (duration * 0.9)
        
        if (index != -1) {
            currentHistory[index] = currentHistory[index].copy(
                playbackPosition = if (isWatched) 0L else position,
                duration = duration,
                lastPlayedStreamUrl = streamUrl,
                isWatched = isWatched
            )
        } else {
            // Movie not in history yet — add it
            val title = movieUrl.substringAfterLast("/").substringBeforeLast(".")
            currentHistory.add(0, Movie(
                title = title,
                url = movieUrl,
                type = "movie",
                playbackPosition = if (isWatched) 0L else position,
                duration = duration,
                lastPlayedStreamUrl = streamUrl,
                isWatched = isWatched
            ))
            if (currentHistory.size > 15) {
                currentHistory.removeLast()
            }
        }
        
        _watchHistory.value = currentHistory
        prefs.edit().putString("history", gson.toJson(currentHistory)).apply()
        firebaseSyncManager.syncWatchHistory(currentHistory)
        
        // Globally track watched streams
        if (isWatched) {
            val watchedSet = prefs.getStringSet("watched_streams", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            watchedSet.add(streamUrl)
            prefs.edit().putStringSet("watched_streams", watchedSet).apply()
        }
    }

    override suspend fun removeFromWatchHistory(movieUrl: String) {
        val currentHistory = _watchHistory.value.toMutableList()
        val index = currentHistory.indexOfFirst { it.url == movieUrl }
        if (index != -1) {
            currentHistory.removeAt(index)
            _watchHistory.value = currentHistory
            prefs.edit().putString("history", gson.toJson(currentHistory)).apply()
            firebaseSyncManager.syncWatchHistory(currentHistory)
        }
    }

    override suspend fun searchTmdb(query: String, type: String): Result<List<com.potflix.data.remote.TmdbMovieDto>> {
        return try {
            val response = if (type.equals("tv", ignoreCase = true)) {
                tmdbApi.searchTv(query = query)
            } else {
                tmdbApi.searchMovie(query = query)
            }
            Result.success(response.results)
        } catch (e: Exception) {
            android.util.Log.e("MovieRepositoryImpl", "Error searching TMDB", e)
            Result.failure(e)
        }
    }

    override suspend fun getTmdbDetail(tmdbId: Int, type: String): Result<com.potflix.data.remote.TmdbDetailDto> {
        return try {
            val details = if (type.equals("tv", ignoreCase = true)) {
                tmdbApi.getTvDetail(tmdbId)
            } else {
                tmdbApi.getMovieDetail(tmdbId)
            }
            Result.success(details)
        } catch (e: Exception) {
            android.util.Log.e("MovieRepositoryImpl", "Error fetching TMDB detail", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMovieTmdbMatch(
        originalMovie: Movie,
        newTmdbId: Long,
        type: String,
        newTitle: String,
        newOverview: String?,
        newPoster: String?,
        newBackdrop: String?,
        newRating: Double?
    ): Result<Movie> {
        return try {
            movieDao.updateMovieTmdbInfo(
                videoUrl = originalMovie.url,
                originalTitle = originalMovie.title,
                newTitle = newTitle,
                tmdbId = newTmdbId,
                overview = newOverview,
                posterUrl = newPoster,
                rating = newRating
            )
            val updated = originalMovie.copy(
                title = newTitle,
                tmdbId = newTmdbId.toInt(),
                type = type,
                poster = newPoster ?: originalMovie.poster,
                backdrop = newBackdrop ?: originalMovie.backdrop,
                overview = newOverview ?: originalMovie.overview,
                rating = newRating ?: originalMovie.rating
            )
            Result.success(updated)
        } catch (e: Exception) {
            android.util.Log.e("MovieRepositoryImpl", "Error updating movie TMDB match", e)
            Result.failure(e)
        }
    }
}
