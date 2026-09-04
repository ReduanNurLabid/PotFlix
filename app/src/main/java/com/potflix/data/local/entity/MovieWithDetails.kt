package com.potflix.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.potflix.domain.model.Movie

data class MovieWithDetails(
    @Embedded val movie: MovieEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MovieGenreCrossRef::class,
            parentColumn = "movie_id",
            entityColumn = "genre_id"
        )
    )
    val genres: List<GenreEntity>,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "movie_id"
    )
    val videos: List<VideoEntity>
) {
    fun toDomainModel(serverConfig: com.potflix.data.local.preferences.ServerConfig? = null): Movie {
        // Pick the highest quality video
        val sortedVideos = videos.sortedByDescending { 
            when(it.quality?.lowercase()) {
                "4k" -> 4
                "1080p" -> 3
                "720p" -> 2
                "480p" -> 1
                else -> 0
            }
        }
        val mainVideo = sortedVideos.firstOrNull()
        
        var resolvedQuality = mainVideo?.quality
        if (resolvedQuality.equals("folder", ignoreCase = true)) {
            resolvedQuality = "Tv & Web Series"
        }
        
        var resolvedUrl = mainVideo?.url ?: ""
        if (serverConfig != null) {
            if (serverConfig.type == com.potflix.data.local.preferences.ServerType.ALIST && resolvedUrl.contains(serverConfig.baseUrl)) {
                // If it's a CDN URL from SyncWorker, it needs the /d/ prefix for raw video
                resolvedUrl = resolvedUrl.replace(serverConfig.baseUrl, serverConfig.baseUrl.trimEnd('/') + "/d/")
            } else {
                resolvedUrl = com.potflix.util.ServerUrlMapper.mapUrl(resolvedUrl, serverConfig)
            }
        }
        
        var resolvedPoster = movie.posterUrl
        if (serverConfig != null && resolvedPoster != null) {
            resolvedPoster = com.potflix.util.ServerUrlMapper.mapUrl(resolvedPoster, serverConfig)
        }

        return Movie(
            title = movie.title,
            url = resolvedUrl,
            year = movie.year?.toIntOrNull(),
            quality = resolvedQuality,
            type = when {
                movie.category?.contains("Series", ignoreCase = true) == true -> "tv"
                movie.category?.contains("tvshows", ignoreCase = true) == true -> "tv"
                videos.any { it.quality == "Tv & Web Series" } -> "tv"
                movie.category == "Animation" -> "Animation"
                else -> "movie"
            },
            categoryId = movie.category ?: "",
            poster = resolvedPoster,
            backdrop = null, // TMDB backdrop could be fetched dynamically if needed, but not in DB
            overview = movie.overview,
            tmdbId = movie.tmdbId?.toInt(),
            rating = movie.rating,
            releaseDate = movie.year,
            genres = genres.map { it.name }.filter { !it.equals("tv series", ignoreCase = true) },
            cast = null,
            language = movie.region,
            runtime = null,
            videos = videos.map { 
                var vUrl = it.url ?: ""
                if (serverConfig != null) {
                    if (serverConfig.type == com.potflix.data.local.preferences.ServerType.ALIST && vUrl.contains(serverConfig.baseUrl)) {
                        vUrl = vUrl.replace(serverConfig.baseUrl, serverConfig.baseUrl.trimEnd('/') + "/d/")
                    } else {
                        vUrl = com.potflix.util.ServerUrlMapper.mapUrl(vUrl, serverConfig)
                    }
                }
                com.potflix.domain.model.VideoSource(
                    quality = it.quality ?: "720p", 
                    url = vUrl
                ) 
            }
        )
    }
}
