package com.potflix.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TmdbSearchResponse(
    val page: Int,
    val results: List<TmdbMovieDto>
)

data class TmdbMovieDto(
    val id: Int,
    val title: String?,
    val name: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("genre_ids") val genreIds: List<Int>?
)

data class TmdbVideoResponse(
    val id: Int,
    val results: List<TmdbVideoDto>
)

data class TmdbVideoDto(
    val site: String,
    val type: String,
    val key: String
)

data class TmdbSeasonResponse(
    val name: String?,
    val overview: String?,
    @SerializedName("season_number") val seasonNumber: Int,
    val episodes: List<TmdbEpisodeDto>?
)

data class TmdbEpisodeDto(
    val id: Int,
    val name: String?,
    val overview: String?,
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("still_path") val stillPath: String?
)
