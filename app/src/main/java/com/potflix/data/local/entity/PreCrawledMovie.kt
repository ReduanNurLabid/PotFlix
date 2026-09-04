package com.potflix.data.local.entity

import androidx.annotation.Keep

@Keep
data class PreCrawledVideo(
    val name: String,
    val url: String,
    val quality: String
)

@Keep
data class PreCrawledMovie(
    val title: String,
    val year: String?,
    val category: String,
    val posterUrl: String?,
    val tmdbId: Long?,
    val overview: String?,
    val rating: Double?,
    val isTvSeries: Boolean,
    val videos: List<PreCrawledVideo>
)
