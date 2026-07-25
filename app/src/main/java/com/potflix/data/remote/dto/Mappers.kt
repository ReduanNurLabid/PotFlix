package com.potflix.data.remote.dto

import com.potflix.domain.model.*

fun CategoryDto.toCategory(): Category {
    return Category(
        id = id,
        name = name,
        type = type,
        url = url,
        icon = icon
    )
}

fun MovieEntryDto.toMovie(): Movie {
    return Movie(
        title = title ?: name,
        url = url,
        year = year,
        quality = quality,
        type = type ?: "movie",
        categoryId = categoryId
    )
}

fun TmdbResultDto.toMovie(url: String = ""): Movie {
    return Movie(
        title = title,
        url = url,
        year = releaseDate?.take(4)?.toIntOrNull(),
        type = "movie", // Default or determine from DTO
        poster = poster,
        backdrop = backdrop,
        overview = overview,
        rating = rating,
        releaseDate = releaseDate,
        trailerKey = trailerKey,
        genreIds = genreIds
    )
}

fun TmdbResultDto.toMovie(originalMovie: Movie): Movie {
    return originalMovie.copy(
        poster = poster,
        backdrop = backdrop,
        overview = overview,
        rating = rating,
        releaseDate = releaseDate,
        trailerKey = trailerKey,
        genreIds = genreIds
    )
}

fun SeasonDto.toSeason(): Season {
    return Season(
        name = name,
        number = number,
        episodes = episodes.map { it.toEpisode() }
    )
}

fun EpisodeDto.toEpisode(): Episode {
    return Episode(
        title = episodeLabel,
        url = url,
        season = season,
        number = episode,
        quality = quality
    )
}
