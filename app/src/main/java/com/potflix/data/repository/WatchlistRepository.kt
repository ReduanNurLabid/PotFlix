package com.potflix.data.repository

import com.potflix.data.local.dao.LocalMovieDao
import com.potflix.data.local.entity.LocalMovieEntity
import com.potflix.data.remote.FirebaseSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepository @Inject constructor(
    private val localMovieDao: LocalMovieDao,
    private val firebaseSyncManager: FirebaseSyncManager
) {
    fun getWatchlist(): Flow<List<LocalMovieEntity>> {
        return localMovieDao.getWatchlist()
    }

    fun isInWatchlist(url: String): Flow<Boolean> {
        return localMovieDao.isInWatchlist(url)
    }

    suspend fun addToWatchlist(movie: LocalMovieEntity) {
        localMovieDao.addToWatchlist(movie)
        syncWatchlistToFirebase()
    }

    suspend fun removeFromWatchlist(movie: LocalMovieEntity) {
        localMovieDao.removeFromWatchlist(movie)
        syncWatchlistToFirebase()
    }
    
    suspend fun removeByUrl(url: String) {
        localMovieDao.removeByUrl(url)
        syncWatchlistToFirebase()
    }

    private suspend fun syncWatchlistToFirebase() {
        val currentWatchlist = localMovieDao.getWatchlist().firstOrNull() ?: emptyList()
        firebaseSyncManager.syncWatchlist(currentWatchlist)
    }
}
