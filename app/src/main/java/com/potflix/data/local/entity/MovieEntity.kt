package com.potflix.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.potflix.domain.model.Movie

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey
    val url: String, // Use URL as primary key to avoid duplicates
    val title: String,
    val year: Int?,
    val quality: String?,
    val type: String,
    val categoryId: String,
    val categoryName: String?,
    
    // TMDB metadata (fetched lazily)
    val poster: String? = null,
    val backdrop: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val releaseDate: String? = null,
    val genres: String? = null, // Comma-separated
    val cast: String? = null, // Comma-separated
    val language: String? = null,
    val runtime: Int? = null
) {
    fun toDomainModel(): Movie {
        return Movie(
            title = title,
            url = url,
            year = year,
            quality = quality,
            type = type,
            categoryId = categoryId,
            poster = poster,
            backdrop = backdrop,
            overview = overview,
            rating = rating,
            releaseDate = releaseDate,
            genres = genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
            cast = cast?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
            language = language,
            runtime = runtime
        )
    }
}
