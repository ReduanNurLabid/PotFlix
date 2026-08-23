package com.potflix.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.potflix.data.local.dao.MovieDao
import com.potflix.data.remote.PotFlixApi
import com.potflix.data.remote.PotFlixScraper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val movieDao: MovieDao,
    private val api: PotFlixApi
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("SyncWorker", "Starting background sync...")

            // 1. Sync Movies from recent years
            syncMovies(
                categoryName = "Hollywood",
                baseUrl = "http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/",
                yearsToSync = listOf("2026", "2025")
            )
            syncMovies(
                categoryName = "Bollywood",
                baseUrl = "http://172.16.50.14/DHAKA-FLIX-14/Hindi%20Movies/",
                yearsToSync = listOf("2026", "2025")
            )
            // Can add others like Animation, South Indian here...

            // 2. Sync TV Series
            syncTvSeries(
                categoryName = "TV Series",
                baseUrl = "http://172.16.50.12/DHAKA-FLIX-12/TV-WEB-Series/" // Based on user's new URL
            )

            Log.d("SyncWorker", "Background sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.failure()
        }
    }

    private suspend fun syncMovies(categoryName: String, baseUrl: String, yearsToSync: List<String>) {
        val rootEntries = PotFlixScraper.scrapeDirectory(baseUrl)
        
        for (year in yearsToSync) {
            val yearFolder = rootEntries.find { it.isDirectory && it.name.contains("($year)") }
            if (yearFolder != null) {
                Log.d("SyncWorker", "Syncing $categoryName for year $year")
                val movieFolders = PotFlixScraper.scrapeDirectory(yearFolder.url)
                
                for (mf in movieFolders.filter { it.isDirectory }) {
                    val title = mf.title ?: mf.name
                    
                    // Check if already in DB
                    val existing = movieDao.getMovieIdByTitleAndYear(title, year)
                    if (existing == null) {
                        Log.d("SyncWorker", "New movie discovered: $title ($year)")
                        
                        // Scrape video files
                        val contents = PotFlixScraper.scrapeDirectory(mf.url)
                        val allVideos = contents.filter { it.isVideo }
                        val ftpPoster = contents.find { it.isImage }?.url
                        
                        // Fetch TMDB metadata
                        val tmdbSearch = api.searchMovie(title, year)
                        val tmdbMatch = tmdbSearch.results.firstOrNull {
                            when (categoryName) {
                                "Bollywood" -> it.originalLanguage == "hi"
                                "South Indian" -> it.originalLanguage in listOf("te", "ta", "ml", "kn")
                                "Tollywood" -> it.originalLanguage == "bn"
                                "Hollywood", "Animation", "Foreign" -> it.originalLanguage == "en" || it.originalLanguage != "hi"
                                else -> true
                            }
                        } ?: tmdbSearch.results.firstOrNull()
                        
                        // Insert Movie
                        val movieId = movieDao.insertMovie(
                            com.potflix.data.local.entity.MovieEntity(
                                title = title,
                                year = year,
                                posterUrl = ftpPoster ?: tmdbMatch?.posterPath?.let { "https://image.tmdb.org/t/p/w342\$it" },
                                category = categoryName,
                                region = null,
                                tmdbId = tmdbMatch?.id?.toLong(),
                                overview = tmdbMatch?.overview,
                                rating = tmdbMatch?.voteAverage,
                                isImdbTop250 = categoryName == "IMDB Top 250"
                            )
                        )
                        
                        // Insert Videos
                        if (movieId > 0) {
                            if (allVideos.isNotEmpty()) {
                                for (v in allVideos) {
                                    movieDao.insertVideo(
                                        com.potflix.data.local.entity.VideoEntity(
                                            movieId = movieId,
                                            quality = v.quality ?: mf.quality ?: "720p",
                                            url = v.url,
                                            fileName = v.name
                                        )
                                    )
                                }
                            } else {
                                movieDao.insertVideo(
                                    com.potflix.data.local.entity.VideoEntity(
                                        movieId = movieId,
                                        quality = mf.quality ?: "720p",
                                        url = mf.url,
                                        fileName = mf.name
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun syncTvSeries(categoryName: String, baseUrl: String) {
        // Recursively find series folders up to depth 2
        suspend fun crawl(url: String, depth: Int) {
            if (depth > 2) return
            val entries = PotFlixScraper.scrapeDirectory(url)
            for (folder in entries.filter { it.isDirectory }) {
                // If it looks like a series folder (has year or specific name structure)
                // Or if it's a structural folder like "A-L"
                if (folder.name.contains("TV Series", ignoreCase = true) || 
                    folder.name.contains("Season", ignoreCase = true) || 
                    folder.name.matches(Regex(".*\\((19|20)\\d{2}.*\\).*"))) {
                    processSeriesFolder(folder.name, folder.url, categoryName)
                } else {
                    crawl(folder.url, depth + 1)
                }
            }
        }
        crawl(baseUrl, 0)
    }

    private suspend fun processSeriesFolder(folderName: String, folderUrl: String, categoryName: String) {
        val title = folderName.substringBefore("(").trim().takeIf { it.isNotEmpty() } ?: folderName
        
        // Search if exists
        val search = movieDao.searchMovies(title)
        val exists = search.any { it.movie.category == categoryName && it.movie.title.equals(title, ignoreCase = true) }
        
        if (!exists) {
            Log.d("SyncWorker", "New TV Series discovered: $title")
            val tmdbSearch = api.searchTv(title, null)
            val tmdbMatch = tmdbSearch.results.firstOrNull()
            
            val movieId = movieDao.insertMovie(
                com.potflix.data.local.entity.MovieEntity(
                    title = title,
                    year = "", 
                    posterUrl = tmdbMatch?.posterPath?.let { "https://image.tmdb.org/t/p/w342\$it" }, 
                    category = categoryName,
                    region = "English",
                    tmdbId = tmdbMatch?.id?.toLong(),
                    overview = tmdbMatch?.overview,
                    rating = tmdbMatch?.voteAverage
                )
            )
            if (movieId > 0) {
                movieDao.insertVideo(
                    com.potflix.data.local.entity.VideoEntity(
                        movieId = movieId,
                        quality = "Tv & Web Series",
                        url = folderUrl,
                        fileName = folderName
                    )
                )
            }
        }
    }
}
