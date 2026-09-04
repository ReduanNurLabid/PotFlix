package com.potflix.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AListScraper(
    private val client: OkHttpClient,
    private val baseUrl: String
) : DirectoryScraper {

    override suspend fun scrapeDirectory(url: String): List<ScrapedEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<ScrapedEntry>()
        try {
            // Safe URL parsing
            val javaUrl = java.net.URL(url)
            val path = java.net.URLDecoder.decode(javaUrl.path, "UTF-8").ifEmpty { "/" }

            // Ensure baseUrl is clean for API calls (remove /d/ if user added it, as API is at root /api/fs/list)
            val cleanBase = baseUrl.replace(Regex("/d/?$"), "/").trimEnd('/')
            
            // Clean path for AList (remove /d/ from path if it bled in, because AList fs/list doesn't want it)
            val cleanPath = if (path.startsWith("/d/")) path.substring(2) else path

            var page = 1
            var hasMore = true
            while(hasMore && page <= 10) { // Max 10,000 items to avoid infinite loops
                val jsonBody = JSONObject().apply {
                    put("path", cleanPath)
                    put("password", "")
                    put("page", page)
                    put("per_page", 1000)
                }.toString()

                val request = Request.Builder()
                    .url("$cleanBase/api/fs/list")
                    .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val root = JSONObject(responseBody)
                        if (root.getInt("code") == 200) {
                            val data = root.getJSONObject("data")
                            val contentArray = data.getJSONArray("content")
                            
                            if (contentArray.length() == 0) {
                                hasMore = false
                            } else {
                                for (i in 0 until contentArray.length()) {
                                    val item = contentArray.getJSONObject(i)
                                    val name = item.getString("name")
                                    val isDir = item.getBoolean("is_dir")

                                    // Reconstruct the URL carefully to avoid raw spaces
                                    val encodedPath = cleanPath.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                                    val encodedName = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                                    
                                    val itemUrl = "${baseUrl.trimEnd('/')}$encodedPath/$encodedName".replace(Regex("(?<!:)//+"), "/")
                                    
                                    val entry = PotFlixScraper.parseMovieName(name, itemUrl, isDir)
                                    entries.add(entry)
                                }
                                page++
                            }
                        } else {
                            hasMore = false
                        }
                    } else {
                        hasMore = false
                    }
                } else {
                    hasMore = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        entries
    }
}
