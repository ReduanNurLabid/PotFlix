package com.potflix.util

import com.potflix.data.local.preferences.ServerConfig
import com.potflix.data.local.preferences.ServerType

object ServerUrlMapper {
    private val DHAKAFLIX_TO_NAGORDOLA_MAP = mapOf(
        "Animation Movies (1080p)" to "movies/animations-english",
        "Animation Movies" to "movies/animations-english",
        "English Movies (1080p)" to "movies/movies-english",
        "English Movies" to "movies/movies-english",
        "Foreign Movies" to "movies/movies-foreign",
        "Hindi Movies (1080p)" to "movies/movies-hindi",
        "Hindi Movies" to "movies/movies-hindi",
        "IMDb Top 250" to "movies/movies-english", // Fallback
        "Kolkata Bangla" to "movies/movies-bangla",
        "KOREAN Web Series" to "tv-series/tvshows-korean",
        "SOUTH INDIAN" to "movies/movies-tamil", // Approximate, usually tamil/telugu mixed
        "TV-WEB-Series" to "tv-series/tvshows-english"
    )

    fun mapUrl(originalUrl: String, activeServer: ServerConfig): String {
        // Only map Dhakaflix FTP URLs
        if (!originalUrl.contains("172.16.50.")) return originalUrl

        // If the active server is FTP (Dhakaflix), return original
        if (activeServer.type == ServerType.FTP) return originalUrl

        // If the active server is ALIST (Nagordola CDN)
        if (activeServer.id == "nagordola") {
            try {
                // Example: http://172.16.50.14/DHAKA-FLIX-14/Animation Movies/(2024)/10 Lives (2024) 720p WEBRip/file.mkv
                val prefixRegex = Regex("http://172\\.16\\.50\\.\\d+/DHAKA-FLIX-\\d+/(.*?)/(.*)")
                val match = prefixRegex.find(java.net.URLDecoder.decode(originalUrl, "UTF-8"))
                
                if (match != null) {
                    val categoryFolder = match.groupValues[1]
                    var restOfPath = match.groupValues[2]

                    // Remove the (YYYY) intermediate folder if present
                    // e.g. "(2024)/10 Lives (2024) 720p WEBRip/file.mkv" -> "10 Lives (2024) 720p WEBRip/file.mkv"
                    val yearFolderRegex = Regex("^\\(\\d{4}\\)/")
                    restOfPath = restOfPath.replaceFirst(yearFolderRegex, "")

                    val mappedCategory = DHAKAFLIX_TO_NAGORDOLA_MAP[categoryFolder] ?: "movies"

                    val cdnPath = "p/$mappedCategory/$restOfPath"
                    return activeServer.baseUrl + cdnPath.replace(" ", "%20")
                }
            } catch (e: Exception) {
                // Fallback to original if parsing fails
                return originalUrl
            }
        }

        return originalUrl
    }
}
