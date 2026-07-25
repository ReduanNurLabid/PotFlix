package com.potflix.data.sync

import android.util.Log
import com.potflix.data.local.dao.MovieDao
import com.potflix.data.local.entity.CategoryEntity
import com.potflix.data.local.entity.MovieEntity
import com.potflix.data.remote.PotFlixScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogSyncManager @Inject constructor(
    private val movieDao: MovieDao,
    private val application: android.app.Application
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()
    
    private val _syncProgress = MutableStateFlow("")
    val syncProgress = _syncProgress.asStateFlow()

    private val categories = listOf(
        CategoryEntity("english-movies", "English Movies", "http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/", "movie", "🎬"),
        CategoryEntity("english-movies-1080p", "English Movies 1080p", "http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20(1080p)/", "movie", "🎬"),
        CategoryEntity("imdb-top-250", "IMDb Top 250", "http://172.16.50.14/DHAKA-FLIX-14/IMDb%20Top-250%20Movies/", "movie", "⭐"),
        CategoryEntity("hindi-movies", "Hindi Movies", "http://172.16.50.14/DHAKA-FLIX-14/Hindi%20Movies/", "movie", "🎭"),
        CategoryEntity("south-indian", "South Indian Movies", "http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/South%20Movies/", "movie", "🎭"),
        CategoryEntity("south-hindi-dubbed", "South Hindi Dubbed", "http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/Hindi%20Dubbed/", "movie", "🎭"),
        CategoryEntity("kolkata-bangla", "Kolkata Bangla Movies", "http://172.16.50.7/DHAKA-FLIX-7/Kolkata%20Bangla%20Movies/", "movie", "🎭"),
        CategoryEntity("animation", "Animation Movies", "http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies/", "movie", "🧸"),
        CategoryEntity("animation-1080p", "Animation Movies 1080p", "http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies%20(1080p)/", "movie", "🧸"),
        CategoryEntity("foreign", "Foreign Language Movies", "http://172.16.50.7/DHAKA-FLIX-7/Foreign%20Language%20Movies/", "movie", "🌍"),
        CategoryEntity("3d-movies", "3D Movies", "http://172.16.50.7/DHAKA-FLIX-7/3D%20Movies/", "movie", "🥽"),
        CategoryEntity("tv-web-series", "TV & WEB Series", "http://172.16.50.12/DHAKA-FLIX-12/TV-WEB-Series/", "tv", "📺"),
        CategoryEntity("korean-tv", "Korean TV & WEB Series", "http://172.16.50.14/DHAKA-FLIX-14/KOREAN%20TV%20%26%20WEB%20Series/", "tv", "🇰🇷"),
        CategoryEntity("cartoon-tv", "Cartoon TV Series", "http://172.16.50.9/DHAKA-FLIX-9/Anime%20%26%20Cartoon%20TV%20Series/", "tv", "🎨"),
        CategoryEntity("documentary", "Documentary", "http://172.16.50.9/DHAKA-FLIX-9/Documentary/", "tv", "📖"),
        CategoryEntity("awards-tv-shows", "Awards & TV Shows", "http://172.16.50.9/DHAKA-FLIX-9/Awards%20%26%20TV%20Shows/", "tv", "🏆")
    )

    suspend fun syncCatalog() = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext
        
        try {
            _isSyncing.value = true
            _syncProgress.value = "Starting sync..."
            
            movieDao.insertCategories(categories)
            
            var totalAdded = 0
            
            for (category in categories) {
                _syncProgress.value = "Syncing ${category.name}..."
                val entries = PotFlixScraper.scrapeDirectory(category.url)
                
                val moviesToInsert = mutableListOf<MovieEntity>()
                
                for (entry in entries) {
                    if (entry.isDirectory && entry.type == "yearFolder") {
                        kotlinx.coroutines.delay(50)
                        // Crawl into year folders
                        val subEntries = PotFlixScraper.scrapeDirectory(entry.url)
                        for (subEntry in subEntries) {
                            if (subEntry.isDirectory && (subEntry.type == "movie" || subEntry.type == "tv")) {
                                moviesToInsert.add(createMovieEntity(subEntry, category))
                            }
                        }
                    } else if (entry.isDirectory && (entry.type == "movie" || entry.type == "tv")) {
                        moviesToInsert.add(createMovieEntity(entry, category))
                    } else if (entry.isDirectory && entry.type == null) {
                        kotlinx.coroutines.delay(50)
                        // Crawl into alphabetical ranges like A-L
                        val subEntries = PotFlixScraper.scrapeDirectory(entry.url)
                        for (subEntry in subEntries) {
                            if (subEntry.isDirectory && (subEntry.type == "movie" || subEntry.type == "tv")) {
                                moviesToInsert.add(createMovieEntity(subEntry, category))
                            }
                        }
                    }
                }
                
                if (moviesToInsert.isNotEmpty()) {
                    movieDao.insertMovies(moviesToInsert)
                    totalAdded += moviesToInsert.size
                }
                
                // Heavy yield to keep the media server free for UI stream fetching
                kotlinx.coroutines.delay(100)
            }
            
            val prefs = application.getSharedPreferences("potflix_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
            
            _syncProgress.value = "Synced $totalAdded items"
        } catch (e: Exception) {
            Log.e("CatalogSync", "Sync failed", e)
            _syncProgress.value = "Sync failed: ${e.message}"
        } finally {
            _isSyncing.value = false
        }
    }
    
    private fun createMovieEntity(entry: com.potflix.data.remote.ScrapedEntry, category: CategoryEntity): MovieEntity {
        val title = entry.title ?: entry.name
        return MovieEntity(
            url = entry.url,
            title = title,
            year = entry.year,
            quality = entry.quality,
            type = entry.type ?: category.type,
            categoryId = category.id,
            categoryName = category.name
        )
    }
}
