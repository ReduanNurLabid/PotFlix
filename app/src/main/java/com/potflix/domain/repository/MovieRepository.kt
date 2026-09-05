package com.potflix.domain.repository

import com.potflix.domain.model.*
import kotlinx.coroutines.flow.Flow

interface MovieRepository {
    suspend fun getCategories(): Result<List<Category>>
    suspend fun getLatestMovies(categoryId: String, limit: Int = 30): Result<List<Movie>>
    suspend fun searchMovies(query: String): Result<List<Movie>>
    suspend fun getMovieDetails(movie: Movie): Result<Movie>
    suspend fun getSeriesEpisodes(url: String): Result<List<Season>>
    suspend fun getTrendingSuggestions(type: String): Result<List<Movie>>
    fun getTrendingSuggestionsFlow(type: String): Flow<List<Movie>>
    suspend fun getMovieStreamUrl(folderUrl: String): Result<String>
    suspend fun getTmdbSeasonDetails(tvId: Int, seasonNumber: Int): Result<com.potflix.data.remote.TmdbSeasonResponse>
    
    // Genre functions
    suspend fun getGenres(): Result<List<Genre>>
    suspend fun getMoviesByGenre(genreId: Long, type: String, limit: Int = 30): Result<List<Movie>>
    
    // History functions
    suspend fun addToWatchHistory(movie: Movie)
    suspend fun updateWatchProgress(movieUrl: String, streamUrl: String, position: Long, duration: Long)
    suspend fun removeFromWatchHistory(movieUrl: String)
    fun getWatchHistoryFlow(): Flow<List<Movie>>

    // TMDB metadata editing & corrections
    suspend fun searchTmdb(query: String, type: String): Result<List<com.potflix.data.remote.TmdbMovieDto>>
    suspend fun getTmdbDetail(tmdbId: Int, type: String): Result<com.potflix.data.remote.TmdbDetailDto>
    suspend fun updateMovieTmdbMatch(
        originalMovie: Movie,
        newTmdbId: Long,
        type: String,
        newTitle: String,
        newOverview: String?,
        newPoster: String?,
        newBackdrop: String?,
        newRating: Double?
    ): Result<Movie>
}
