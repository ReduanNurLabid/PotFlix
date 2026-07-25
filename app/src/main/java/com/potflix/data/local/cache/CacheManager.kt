package com.potflix.data.local.cache

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.potflix.domain.model.Movie
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("potflix_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveTrendingCache(type: String, movies: List<Movie>) {
        val json = gson.toJson(movies)
        prefs.edit().putString("trending_$type", json).apply()
    }

    fun getTrendingCache(type: String): List<Movie> {
        val json = prefs.getString("trending_$type", null) ?: return emptyList()
        return try {
            val typeToken = object : TypeToken<List<Movie>>() {}.type
            gson.fromJson(json, typeToken)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
