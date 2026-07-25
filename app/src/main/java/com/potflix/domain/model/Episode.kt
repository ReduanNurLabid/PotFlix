package com.potflix.domain.model

data class Episode(
    val title: String,
    val url: String,
    val season: Int? = null,
    val number: Int? = null,
    val quality: String? = null,
    val overview: String? = null,
    val stillPath: String? = null
)

data class Season(
    val name: String,
    val number: Int,
    val episodes: List<Episode>
)
