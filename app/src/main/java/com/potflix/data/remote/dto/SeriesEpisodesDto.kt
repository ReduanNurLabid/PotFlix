package com.potflix.data.remote.dto

data class SeriesEpisodesDto(
    val seasons: List<SeasonDto>,
    val looseVideos: List<EpisodeDto>
)

data class SeasonDto(
    val name: String,
    val url: String,
    val number: Int,
    val episodes: List<EpisodeDto>
)

data class EpisodeDto(
    val name: String,
    val url: String,
    val isVideo: Boolean,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeLabel: String,
    val quality: String? = null
)
