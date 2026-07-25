package com.potflix.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.potflix.domain.model.Movie

@Entity(tableName = "watchlist")
data class LocalMovieEntity(
    @PrimaryKey val url: String,
    val title: String,
    val year: Int?,
    val quality: String?,
    val type: String,
    val poster: String?,
    val backdrop: String?,
    val overview: String?,
    val rating: Double?,
    val genres: String?,
    val cast: String?,
    val language: String?,
    val runtime: Int?,
    val timestamp: Long = System.currentTimeMillis()
)

fun LocalMovieEntity.toMovie(): Movie {
    return Movie(
        title = title,
        url = url,
        year = year,
        quality = quality,
        type = type,
        poster = poster,
        backdrop = backdrop,
        overview = overview,
        rating = rating,
        categoryId = "watchlist",
        genres = genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
        cast = cast?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
        language = language,
        runtime = runtime
    )
}

fun Movie.toLocalMovieEntity(): LocalMovieEntity {
    return LocalMovieEntity(
        url = url,
        title = title,
        year = year,
        quality = quality,
        type = type,
        poster = poster,
        backdrop = backdrop,
        overview = overview,
        rating = rating,
        genres = genres?.joinToString(","),
        cast = cast?.joinToString(","),
        language = language,
        runtime = runtime
    )
}
