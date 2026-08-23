package com.potflix.domain.model

data class Movie(
    val title: String,
    val url: String,
    val year: Int? = null,
    val quality: String? = null,
    val type: String, // "movie" or "tv"
    val poster: String? = null,
    val backdrop: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val releaseDate: String? = null,
    val trailerKey: String? = null,
    val genreIds: List<Int>? = null,
    val genres: List<String>? = null,
    val cast: List<String>? = null,
    val language: String? = null,
    val runtime: Int? = null,
    val categoryId: String? = null,
    val tmdbId: Int? = null,
    val videos: List<VideoSource>? = null
)

data class VideoSource(
    val quality: String,
    val url: String
)
