package com.potflix.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL

data class ScrapedEntry(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val isVideo: Boolean = false,
    val isImage: Boolean = false,
    val title: String? = null,
    val year: Int? = null,
    val type: String? = null,
    val quality: String? = null,
    val isDualAudio: Boolean = false,
    val seriesInfo: String? = null
)

object PotFlixScraper {

    suspend fun scrapeDirectory(url: String): List<ScrapedEntry> = withContext(Dispatchers.IO) {
        try {
            val safeUrl = url.replace("[", "%5B").replace("]", "%5D")
            val doc = Jsoup.connect(safeUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Connection", "close") // Prevent connection pool exhaustion
                .timeout(15000)
                .get()

            val entries = mutableListOf<ScrapedEntry>()
            val links = doc.select("a")

            for (link in links) {
                val href = link.attr("href")
                val text = link.text().trim()

                if (href.isEmpty() || text.isEmpty() ||
                    text == "Parent Directory" ||
                    text == "powered by SamOnline" ||
                    text == "modern browsers" ||
                    href.startsWith("http://browsehappy") ||
                    href.startsWith("https://larsjung") ||
                    href == "../" ||
                    href == "/" ||
                    href.startsWith("#") ||
                    href.startsWith("?")
                ) {
                    continue
                }

                val fullUrl = try {
                    URL(URL(url), href).toString()
                        .replace("[", "%5B")
                        .replace("]", "%5D")
                } catch (e: Exception) {
                    continue
                }

                val isDirectory = href.endsWith("/")
                val name = try {
                    java.net.URLDecoder.decode(text, "UTF-8").trim()
                } catch (e: Exception) {
                    text.trim()
                }

                val entry = parseMovieName(name, fullUrl, isDirectory)
                entries.add(entry)
            }

            // Deduplicate by URL
            entries.distinctBy { it.url }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun parseMovieName(folderName: String, url: String, isDirectory: Boolean): ScrapedEntry {
        // Remove file extension if present
        var name = folderName.replace(Regex("\\.[a-zA-Z0-9]{2,4}$"), "")
        // Strip numbering prefixes like "003. " or "22 - "
        name = name.replace(Regex("^\\d{1,4}\\s*[\\.\\-]\\s*"), "")

        var isVideo = false
        var isImage = false
        if (!isDirectory) {
            val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
            isVideo = ext in listOf("mkv", "mp4", "avi", "wmv", "mov", "flv", "webm", "ts", "m2ts", "vob", "m4v")
            isImage = ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        }

        // Pattern: Title (Year) Quality [extras]
        val movieRegex = Regex("^(.+?)\\s*\\((\\d{4})\\)\\s*(.*)")
        val movieMatch = movieRegex.find(name)

        if (movieMatch == null) {
            // TV Series pattern: Title (TV Series YYYY-YYYY) Quality
            val tvRegex = Regex("^(.+?)\\s*\\((TV(?:\\s+Mini)?\\s+Series\\s+\\d{4}.*?)\\)\\s*(.*)")
            val tvMatch = tvRegex.find(name)

            if (tvMatch != null) {
                val title = tvMatch.groupValues[1].trim()
                val seriesInfo = tvMatch.groupValues[2].trim()
                val meta = tvMatch.groupValues[3]
                
                return ScrapedEntry(
                    name = folderName,
                    url = url,
                    isDirectory = isDirectory,
                    isVideo = isVideo,
                    isImage = isImage,
                    title = title,
                    type = "tv",
                    seriesInfo = seriesInfo,
                    quality = extractQuality(meta),
                    isDualAudio = Regex("dual\\s*audio", RegexOption.IGNORE_CASE).containsMatchIn(meta)
                )
            }

            // Year-only folder like "(2025)"
            val yearRegex = Regex("^\\((\\d{4})\\)(\\s+.*)?$")
            val yearMatch = yearRegex.find(name)

            if (yearMatch != null) {
                return ScrapedEntry(
                    name = folderName,
                    url = url,
                    isDirectory = isDirectory,
                    isVideo = isVideo,
                    isImage = isImage,
                    title = yearMatch.groupValues[1],
                    year = yearMatch.groupValues[1].toIntOrNull(),
                    type = "yearFolder"
                )
            }

            // Unmatched
            return ScrapedEntry(
                name = folderName,
                url = url,
                isDirectory = isDirectory,
                isVideo = isVideo,
                isImage = isImage,
                title = name,
                quality = extractQuality(name)
            )
        }

        val title = movieMatch.groupValues[1].trim().replace("-", ": ").replace(Regex("\\s+"), " ")
        val year = movieMatch.groupValues[2].toIntOrNull()
        val meta = movieMatch.groupValues[3]

        return ScrapedEntry(
            name = folderName,
            url = url,
            isDirectory = isDirectory,
            isVideo = isVideo,
            isImage = isImage,
            title = title,
            year = year,
            quality = extractQuality(meta),
            isDualAudio = Regex("dual\\s*audio", RegexOption.IGNORE_CASE).containsMatchIn(meta)
        )
    }

    private fun extractQuality(str: String): String? {
        val match = Regex("(2160p|1080p|720p|480p|360p)", RegexOption.IGNORE_CASE).find(str)
        return match?.groupValues?.get(1)?.lowercase()
    }
}
