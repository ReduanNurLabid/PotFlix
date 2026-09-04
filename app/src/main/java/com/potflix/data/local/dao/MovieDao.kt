package com.potflix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.potflix.data.local.entity.CategoryEntity
import com.potflix.data.local.entity.MovieEntity
import com.potflix.data.local.entity.MovieWithDetails
import com.potflix.data.local.entity.GenreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    
    @Transaction
    @Query("SELECT * FROM movies WHERE category = :category GROUP BY title, year ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomMoviesByCategory(category: String, limit: Int): List<MovieWithDetails>

    @Transaction
    @Query("SELECT * FROM movies WHERE is_imdb_top_250 = 1 GROUP BY title, year ORDER BY RANDOM() LIMIT :limit")
    suspend fun getImdbTop250Movies(limit: Int): List<MovieWithDetails>

    @Transaction
    @Query("SELECT * FROM movies GROUP BY title, year ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomMoviesAll(limit: Int): List<MovieWithDetails>

    @Transaction
    @Query("SELECT * FROM movies WHERE category NOT LIKE '%Series%' AND category NOT LIKE 'tvshows%' AND CAST(year AS INTEGER) >= :minYear GROUP BY title, year ORDER BY COALESCE(rating, 0.0) DESC, year DESC LIMIT :limit")
    suspend fun getRecentHighRatedMovies(minYear: Int, limit: Int): List<MovieWithDetails>
    
    @Transaction
    @Query("SELECT * FROM movies WHERE (category LIKE '%Series%' OR category LIKE 'tvshows%') AND CAST(year AS INTEGER) >= :minYear GROUP BY title, year ORDER BY COALESCE(rating, 0.0) DESC, year DESC LIMIT :limit")
    suspend fun getRecentHighRatedTv(minYear: Int, limit: Int): List<MovieWithDetails>

    @Transaction
    @Query("SELECT * FROM movies WHERE CAST(year AS INTEGER) >= :minYear GROUP BY title, year ORDER BY COALESCE(rating, 0.0) DESC, year DESC LIMIT :limit")
    suspend fun getRecentHighRatedAll(minYear: Int, limit: Int): List<MovieWithDetails>

    @Transaction
    @Query("SELECT * FROM movies WHERE tmdb_id IN (:tmdbIds) AND (category LIKE '%Series%' OR category LIKE 'tvshows%')")
    suspend fun getTvSeriesByTmdbIds(tmdbIds: List<Long>): List<MovieWithDetails>

    @Transaction
    @Query("SELECT * FROM movies WHERE tmdb_id IN (:tmdbIds) AND category NOT LIKE '%Series%' AND category NOT LIKE 'tvshows%'")
    suspend fun getMoviesByTmdbIds(tmdbIds: List<Long>): List<MovieWithDetails>

    @Transaction
    @Query("SELECT * FROM movies WHERE tmdb_id IN (:tmdbIds)")
    suspend fun getAllByTmdbIds(tmdbIds: List<Long>): List<MovieWithDetails>

    @Transaction
    @Query("""
        SELECT * FROM movies 
        WHERE title LIKE '%' || :query || '%' COLLATE NOCASE 
        GROUP BY title, year
        ORDER BY 
            CASE WHEN title LIKE :query COLLATE NOCASE THEN 0 
                 WHEN title LIKE :query || '%' COLLATE NOCASE THEN 1 
                 ELSE 2 END,
            year DESC 
        LIMIT 100
    """)
    suspend fun searchMovies(query: String): List<MovieWithDetails>

    @Transaction
    @Query("SELECT * FROM movies WHERE id = :movieId LIMIT 1")
    suspend fun getMovieById(movieId: Long): MovieWithDetails?

    @Query("SELECT COUNT(*) FROM movies")
    suspend fun getMovieCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovie(movie: MovieEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenres(genres: List<com.potflix.data.local.entity.GenreEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenre(genre: com.potflix.data.local.entity.GenreEntity): Long

    @Query("SELECT * FROM genres WHERE name = :name LIMIT 1")
    suspend fun getGenreByName(name: String): com.potflix.data.local.entity.GenreEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovieGenreCrossRef(crossRef: com.potflix.data.local.entity.MovieGenreCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideo(video: com.potflix.data.local.entity.VideoEntity)

    @Query("SELECT id FROM movies WHERE title = :title AND year = :year LIMIT 1")
    suspend fun getMovieIdByTitleAndYear(title: String, year: String?): Long?

    // Categories (Legacy - we can keep this for any dynamic categories they had)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)
    
    @Query("SELECT * FROM categories")
    suspend fun getCategories(): List<CategoryEntity>
    
    @Query("SELECT DISTINCT category FROM movies WHERE category IS NOT NULL AND category != ''")
    suspend fun getDistinctCategories(): List<String>
    
    @Query("SELECT COUNT(*) FROM movies WHERE category = :category")
    suspend fun getCountForCategory(category: String): Int

    @Query("SELECT COUNT(*) FROM movies WHERE category = :category AND year = :year")
    suspend fun getCountForCategoryAndYear(category: String, year: String): Int
    
    @Query("SELECT * FROM categories")
    fun getCategoriesFlow(): Flow<List<CategoryEntity>>

    // New Genre Queries
    @Query("SELECT * FROM genres ORDER BY name ASC")
    suspend fun getAllGenres(): List<GenreEntity>

    @Transaction
    @Query("""
        SELECT m.* FROM movies m
        INNER JOIN movie_genres mg ON m.id = mg.movie_id
        INNER JOIN genres g ON mg.genre_id = g.id
        WHERE g.id = :genreId AND m.category NOT LIKE '%Series%' AND m.category NOT LIKE 'tvshows%'
        GROUP BY m.title, m.year
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getMoviesByGenreId(genreId: Long, limit: Int): List<MovieWithDetails>

    @Transaction
    @Query("""
        SELECT m.* FROM movies m
        INNER JOIN movie_genres mg ON m.id = mg.movie_id
        INNER JOIN genres g ON mg.genre_id = g.id
        WHERE g.id = :genreId AND (m.category LIKE '%Series%' OR m.category LIKE 'tvshows%')
        GROUP BY m.title, m.year
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getTvSeriesByGenreId(genreId: Long, limit: Int): List<MovieWithDetails>

    @Query("""
        UPDATE movies SET 
            tmdb_id = :tmdbId, 
            title = CASE WHEN :newTitle IS NOT NULL AND :newTitle != '' THEN :newTitle ELSE title END,
            overview = COALESCE(:overview, overview), 
            poster_url = COALESCE(:posterUrl, poster_url), 
            rating = COALESCE(:rating, rating) 
        WHERE id = (
            SELECT m.id FROM movies m 
            LEFT JOIN videos v ON m.id = v.movie_id 
            WHERE v.url = :videoUrl OR m.title = :originalTitle 
            LIMIT 1
        )
    """)
    suspend fun updateMovieTmdbInfo(
        videoUrl: String,
        originalTitle: String,
        newTitle: String?,
        tmdbId: Long,
        overview: String?,
        posterUrl: String?,
        rating: Double?
    ): Int

    @Transaction
    @Query("SELECT * FROM movies WHERE title = :title LIMIT 1")
    suspend fun getMovieByTitle(title: String): MovieWithDetails?

    @Transaction
    @Query("""
        SELECT m.* FROM movies m 
        LEFT JOIN videos v ON m.id = v.movie_id 
        WHERE v.url = :videoUrl OR m.title = :title 
        LIMIT 1
    """)
    suspend fun getMovieByUrlOrTitle(videoUrl: String, title: String): MovieWithDetails?
}
