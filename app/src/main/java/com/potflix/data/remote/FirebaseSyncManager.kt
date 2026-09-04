package com.potflix.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.potflix.data.local.entity.LocalMovieEntity
import com.potflix.domain.model.Movie
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncManager @Inject constructor() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun initAuth() {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
                Log.d("FirebaseSyncManager", "Signed in anonymously: ${auth.currentUser?.uid}")
            } catch (e: Exception) {
                Log.e("FirebaseSyncManager", "Anonymous auth failed", e)
            }
        }
    }

    suspend fun signUp(email: String, password: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser != null && currentUser.isAnonymous) {
                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                currentUser.linkWithCredential(credential).await()
            } else {
                auth.createUserWithEmailAndPassword(email, password).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        auth.signOut()
        initAuth() // Fallback to anonymous immediately
    }

    val currentUserEmail: kotlinx.coroutines.flow.Flow<String?> = kotlinx.coroutines.flow.callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null && !user.isAnonymous) {
                trySend(user.email)
            } else {
                trySend(null)
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private fun getUserId(): String? = auth.currentUser?.uid

    fun syncWatchHistory(history: List<Movie>) {
        val uid = getUserId() ?: return
        
        // Convert the history to a map
        val data = history.associate { movie ->
            movie.url.hashCode().toString() to mapOf(
                "title" to movie.title,
                "url" to movie.url,
                "poster" to movie.poster,
                "playbackPosition" to movie.playbackPosition,
                "duration" to movie.duration,
                "lastPlayedStreamUrl" to movie.lastPlayedStreamUrl,
                "isWatched" to movie.isWatched
            )
        }
        
        firestore.collection("users").document(uid).collection("data").document("history")
            .set(data, SetOptions.merge())
            .addOnFailureListener { e -> Log.e("FirebaseSyncManager", "Failed to sync history", e) }
    }

    fun syncWatchlist(watchlist: List<LocalMovieEntity>) {
        val uid = getUserId() ?: return
        
        val data = watchlist.associate { movie ->
            movie.url.hashCode().toString() to mapOf(
                "title" to movie.title,
                "url" to movie.url,
                "poster" to movie.poster,
                "timestamp" to movie.timestamp
            )
        }
        
        // Overwrite the entire "watchlist" document
        firestore.collection("users").document(uid).collection("data").document("watchlist")
            .set(data)
            .addOnFailureListener { e -> Log.e("FirebaseSyncManager", "Failed to sync watchlist", e) }
    }
}
