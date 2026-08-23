package com.potflix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "movie_genres",
    primaryKeys = ["movie_id", "genre_id"],
    foreignKeys = [
        ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["id"],
            childColumns = ["movie_id"]
        ),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["id"],
            childColumns = ["genre_id"]
        )
    ],
    indices = [
        Index("movie_id", name = "idx_movie_genres_movie_id"),
        Index("genre_id", name = "idx_movie_genres_genre_id")
    ]
)
data class MovieGenreCrossRef(
    @ColumnInfo(name = "movie_id")
    val movieId: Long,
    
    @ColumnInfo(name = "genre_id")
    val genreId: Long
)
