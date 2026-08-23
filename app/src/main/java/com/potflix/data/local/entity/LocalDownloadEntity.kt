package com.potflix.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class LocalDownloadEntity(
    @PrimaryKey val downloadId: Long,
    val title: String,
    val streamUrl: String,
    val localUri: String?,
    val poster: String?,
    val status: Int, // e.g., DownloadManager.STATUS_RUNNING
    val progress: Int, // 0 to 100
    val totalBytes: Long = 0L,
    val bytesDownloaded: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val etaSeconds: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
