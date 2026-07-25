package com.potflix.data.remote.dto

data class TmdbResultDto(
    val id: Int? = null,
    val title: String,
    val overview: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val rating: Double? = null,
    val releaseDate: String? = null,
    val genreIds: List<Int>? = null,
    val trailerKey: String? = null,
    val error: String? = null
)
