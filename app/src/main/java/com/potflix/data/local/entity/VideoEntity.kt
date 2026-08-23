package com.potflix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "videos",
    foreignKeys = [
        ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["id"],
            childColumns = ["movie_id"]
        )
    ],
    indices = [
        Index(value = ["movie_id"])
    ]
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "movie_id")
    val movieId: Long,
    
    val quality: String?,
    val url: String?,
    
    @ColumnInfo(name = "file_name")
    val fileName: String?
)
