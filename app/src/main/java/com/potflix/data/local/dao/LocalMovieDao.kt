package com.potflix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.potflix.data.local.entity.LocalMovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMovieDao {
    @Query("SELECT * FROM watchlist ORDER BY timestamp DESC")
    fun getWatchlist(): Flow<List<LocalMovieEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE url = :url)")
    fun isInWatchlist(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(movie: LocalMovieEntity)

    @Delete
    suspend fun removeFromWatchlist(movie: LocalMovieEntity)
    
    @Query("DELETE FROM watchlist WHERE url = :url")
    suspend fun removeByUrl(url: String)
}
