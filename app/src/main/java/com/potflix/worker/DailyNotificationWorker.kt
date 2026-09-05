package com.potflix.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.potflix.MainActivity
import com.potflix.R
import com.potflix.data.local.dao.MovieDao
import com.potflix.data.local.preferences.ServerPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class DailyNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val movieDao: MovieDao,
    private val serverPreferences: ServerPreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "daily_recommendations_channel"
        const val NOTIFICATION_ID = 2001
        private const val TAG = "DailyNotificationWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Check if user has opted out of daily engagement notifications
            if (!serverPreferences.isDailyNotificationEnabled()) {
                Log.d(TAG, "Daily notifications are disabled in preferences. Skipping.")
                return@withContext Result.success()
            }

            // Create notification channel
            createNotificationChannel()

            // Pick an engaging movie or fallback hook
            val (title, body) = selectEngagementContent()

            // Build intent to open app when notification is tapped
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_potflix_logo_vector)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(0xFFE50914.toInt()) // PotFlix Red
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.areNotificationsEnabled()) {
                try {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    Log.d(TAG, "Daily engagement notification sent: $title")
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException while notifying: ${e.message}")
                }
            } else {
                Log.d(TAG, "Notifications are disabled at the system level.")
            }

            // Schedule the next notification for tomorrow at a fresh random hour
            DailyNotificationScheduler.scheduleNextDailyNotification(context, forceNextDay = true)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in DailyNotificationWorker", e)
            // Even on error, reschedule for tomorrow to keep the daily engagement loop running
            DailyNotificationScheduler.scheduleNextDailyNotification(context, forceNextDay = true)
            Result.success()
        }
    }

    private suspend fun selectEngagementContent(): Pair<String, String> {
        val movies = try {
            movieDao.getRandomMoviesAll(limit = 10)
        } catch (e: Exception) {
            emptyList()
        }

        return if (movies.isNotEmpty()) {
            val item = movies.random()
            val movieTitle = item.movie.title
            val yearStr = item.movie.year?.let { " ($it)" } ?: ""

            val titleTemplates = listOf(
                "🍿 Tonight's Pick: $movieTitle$yearStr",
                "🎬 Ready for movie night? Check out $movieTitle!",
                "🔥 Trending on PotFlix: $movieTitle$yearStr",
                "✨ Recommended for you: $movieTitle",
                "🌟 Looking for something great to watch? $movieTitle"
            )

            val overview = item.movie.overview
            val body = if (!overview.isNullOrBlank()) {
                if (overview.length > 180) overview.take(177) + "..." else overview
            } else {
                "Stream now in high quality on PotFlix. Tap to start watching!"
            }

            Pair(titleTemplates.random(), body)
        } else {
            val genericHooks = listOf(
                Pair("🍿 Movie night is calling!", "Discover thousands of movies and TV series streaming in high quality on PotFlix."),
                Pair("🎬 Looking for something to watch?", "Browse top-rated movies and trending TV shows ready for streaming."),
                Pair("✨ Weekend watchlist ready?", "Pick up where you left off or find a new favorite story on PotFlix."),
                Pair("🔥 Binge-worthy shows are waiting!", "Catch up on trending TV series and high-definition cinema today.")
            )
            genericHooks.random()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Recommendations",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Personalized daily movie and TV series suggestions"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
