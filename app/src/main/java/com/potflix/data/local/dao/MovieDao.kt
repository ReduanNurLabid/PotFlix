package com.potflix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.potflix.data.local.entity.CategoryEntity
import com.potflix.data.local.entity.MovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    // Movies
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovies(movies: List<MovieEntity>)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovie(movie: MovieEntity)
    
    @Update
    suspend fun updateMovie(movie: MovieEntity)

    @Query("SELECT * FROM movies WHERE type = :type ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomMovies(type: String, limit: Int): List<MovieEntity>

    @Query("SELECT * FROM movies ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomMoviesAll(limit: Int): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getMoviesByCategory(categoryId: String, limit: Int): List<MovieEntity>
    
    @Query("SELECT * FROM movies WHERE title LIKE '%' || :query || '%' COLLATE NOCASE LIMIT 80")
    suspend fun searchMovies(query: String): List<MovieEntity>
    
    @Query("SELECT * FROM movies")
    fun getAllMoviesFlow(): Flow<List<MovieEntity>>
    
    @Query("SELECT COUNT(*) FROM movies")
    suspend fun getMovieCount(): Int
    
    @Query("SELECT * FROM movies WHERE url = :url LIMIT 1")
    suspend fun getMovieByUrl(url: String): MovieEntity?
    
    @Query("DELETE FROM movies")
    suspend fun clearAllMovies()

    // Categories
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)
    
    @Query("SELECT * FROM categories")
    suspend fun getCategories(): List<CategoryEntity>
    
    @Query("SELECT * FROM categories")
    fun getCategoriesFlow(): Flow<List<CategoryEntity>>
}
