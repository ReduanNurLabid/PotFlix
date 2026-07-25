package com.potflix.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.potflix.data.remote.PotFlixScraper
import com.potflix.data.remote.ScrapedEntry
import com.potflix.domain.model.Category
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchIndexManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val cacheFile = File(context.filesDir, "search_cache.json")
    private var searchIndex: List<ScrapedEntry> = emptyList()
    private var isBuilding = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        loadCache()
    }

    private fun loadCache() {
        if (cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val type = object : TypeToken<List<ScrapedEntry>>() {}.type
                searchIndex = gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getIndex(): List<ScrapedEntry> = searchIndex

    fun buildIndexInBackground(categories: List<Category>) {
        if (isBuilding) return
        isBuilding = true

        scope.launch {
            try {
                val newIndex = mutableListOf<ScrapedEntry>()
                for (cat in categories) {
                    try {
                        val entries = PotFlixScraper.scrapeDirectory(cat.url)
                        for (entry in entries) {
                            if (entry.isDirectory && entry.type == "yearFolder") {
                                val movies = PotFlixScraper.scrapeDirectory(entry.url)
                                newIndex.addAll(movies.filter { it.isDirectory && (it.type == "movie" || it.type == "tv") })
                            } else if (entry.isDirectory && (entry.type == "movie" || entry.type == "tv")) {
                                newIndex.add(entry)
                            } else if (entry.isDirectory && entry.type == null) {
                                // Sub-folders like A-L
                                val subEntries = PotFlixScraper.scrapeDirectory(entry.url)
                                newIndex.addAll(subEntries.filter { it.isDirectory && (it.type == "movie" || it.type == "tv") })
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                searchIndex = newIndex
                cacheFile.writeText(gson.toJson(searchIndex))
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isBuilding = false
            }
        }
    }
}
