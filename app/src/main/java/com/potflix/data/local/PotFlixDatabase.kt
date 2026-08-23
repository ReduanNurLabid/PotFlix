package com.potflix.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.potflix.data.local.dao.LocalDownloadDao
import com.potflix.data.local.dao.LocalMovieDao
import com.potflix.data.local.entity.LocalDownloadEntity
import com.potflix.data.local.entity.LocalMovieEntity
import com.potflix.data.local.entity.MovieEntity
import com.potflix.data.local.entity.CategoryEntity
import com.potflix.data.local.dao.MovieDao

@Database(
    entities = [
        LocalMovieEntity::class, 
        LocalDownloadEntity::class,
        MovieEntity::class,
        CategoryEntity::class,
        com.potflix.data.local.entity.GenreEntity::class,
        com.potflix.data.local.entity.VideoEntity::class,
        com.potflix.data.local.entity.MovieGenreCrossRef::class
    ],
    version = 10,
    exportSchema = false
)
abstract class PotFlixDatabase : RoomDatabase() {
    abstract val localMovieDao: LocalMovieDao
    abstract val localDownloadDao: LocalDownloadDao
    abstract val movieDao: MovieDao
    
    companion object {
        const val DATABASE_NAME = "potflix_db"
    }
}
