package com.potflix.data.remote

import com.potflix.data.remote.dto.TmdbSearchResponse
import com.potflix.data.remote.dto.TmdbVideoResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PotFlixApi {
    @GET("3/search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("primary_release_year") year: String? = null
    ): TmdbSearchResponse

    @GET("3/search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("first_air_date_year") year: String? = null
    ): TmdbSearchResponse

    @GET("3/movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int
    ): TmdbVideoResponse

    @GET("3/tv/{tv_id}/videos")
    suspend fun getTvVideos(
        @Path("tv_id") tvId: Int
    ): TmdbVideoResponse

    @GET("3/trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("page") page: Int = 1
    ): TmdbSearchResponse
    
    @GET("3/trending/tv/week")
    suspend fun getTrendingTv(
        @Query("page") page: Int = 1
    ): TmdbSearchResponse
    
    @GET("3/tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeason(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int
    ): com.potflix.data.remote.dto.TmdbSeasonResponse
}
