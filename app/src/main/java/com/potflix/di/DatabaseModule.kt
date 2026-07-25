package com.potflix.di

import android.app.Application
import androidx.room.Room
import com.potflix.data.local.PotFlixDatabase
import com.potflix.data.local.dao.LocalMovieDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePotFlixDatabase(app: Application): PotFlixDatabase {
        return Room.databaseBuilder(
            app,
            PotFlixDatabase::class.java,
            PotFlixDatabase.DATABASE_NAME
        )
            .createFromAsset("database/potflix_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideLocalMovieDao(db: PotFlixDatabase): LocalMovieDao {
        return db.localMovieDao
    }

    @Provides
    @Singleton
    fun provideLocalDownloadDao(db: PotFlixDatabase): com.potflix.data.local.dao.LocalDownloadDao {
        return db.localDownloadDao
    }

    @Provides
    @Singleton
    fun provideMovieDao(db: PotFlixDatabase): com.potflix.data.local.dao.MovieDao {
        return db.movieDao
    }
}
