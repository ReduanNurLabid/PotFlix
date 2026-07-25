package com.potflix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.potflix.data.local.entity.LocalDownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<LocalDownloadEntity>>

    @Query("SELECT * FROM downloads WHERE downloadId = :id")
    suspend fun getDownloadById(id: Long): LocalDownloadEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE streamUrl = :url)")
    fun isDownloaded(url: String): Flow<Boolean>

    @Query("SELECT * FROM downloads WHERE streamUrl = :url LIMIT 1")
    fun getDownloadByUrl(url: String): Flow<LocalDownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: LocalDownloadEntity)

    @Update
    suspend fun update(download: LocalDownloadEntity)

    @Delete
    suspend fun delete(download: LocalDownloadEntity)

    @Query("DELETE FROM downloads WHERE downloadId = :id")
    suspend fun deleteById(id: Long)
}
