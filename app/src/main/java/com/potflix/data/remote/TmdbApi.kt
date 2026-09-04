package com.potflix.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApi {
    @GET("3/search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("3/search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("primary_release_year") year: Int? = null,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("3/search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("first_air_date_year") year: Int? = null,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse
    
    @GET("3/trending/all/week")
    suspend fun getTrending(
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("3/movie/popular")
    suspend fun getPopularMovies(
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("3/movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("3/tv/popular")
    suspend fun getPopularTv(
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("3/tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeason(
        @retrofit2.http.Path("tv_id") tvId: Int,
        @retrofit2.http.Path("season_number") seasonNumber: Int,
        @Query("language") language: String = "en-US"
    ): TmdbSeasonResponse
    
    @GET("3/movie/{movie_id}")
    suspend fun getMovieDetail(
        @retrofit2.http.Path("movie_id") movieId: Int,
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("language") language: String = "en-US"
    ): TmdbDetailDto
    
    @GET("3/tv/{tv_id}")
    suspend fun getTvDetail(
        @retrofit2.http.Path("tv_id") tvId: Int,
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("language") language: String = "en-US"
    ): TmdbDetailDto
}

data class TmdbSearchResponse(
    val results: List<TmdbMovieDto>
)

data class TmdbMovieDto(
    val id: Int,
    val title: String?,
    val name: String?,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val vote_average: Double?,
    val release_date: String?,
    val first_air_date: String?,
    val media_type: String?,
    val genre_ids: List<Int>?,
    val original_language: String?
)

data class TmdbSeasonResponse(
    val _id: String?,
    val name: String?,
    val overview: String?,
    val season_number: Int,
    val episodes: List<TmdbEpisodeDto>
)

data class TmdbEpisodeDto(
    val id: Int,
    val name: String?,
    val overview: String?,
    val episode_number: Int,
    val still_path: String?
)

data class TmdbDetailDto(
    val id: Int? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val genres: List<TmdbGenre>? = null,
    val original_language: String? = null,
    val runtime: Int? = null, // For movies
    val episode_run_time: List<Int>? = null, // For TV
    val credits: TmdbCredits? = null,
    val vote_average: Double? = null
)

data class TmdbGenre(
    val id: Int,
    val name: String
)

data class TmdbCredits(
    val cast: List<TmdbCast>?
)

data class TmdbCast(
    val name: String,
    val character: String?,
    val order: Int
)
