package com.potflix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.potflix.domain.model.Movie

@Entity(
    tableName = "movies",
    indices = [
        androidx.room.Index("title", name = "idx_movies_title"),
        androidx.room.Index("category", name = "idx_movies_category"),
        androidx.room.Index("region", name = "idx_movies_region")
    ]
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val title: String,
    val year: String?,
    
    @ColumnInfo(name = "poster_url")
    val posterUrl: String?,
    
    val category: String?,
    val region: String?,
    
    @ColumnInfo(name = "is_imdb_top_250", defaultValue = "0")
    val isImdbTop250: Boolean = false,
    
    @ColumnInfo(name = "tmdb_id")
    val tmdbId: Long? = null,
    
    val overview: String? = null,
    
    val rating: Double? = null
)
