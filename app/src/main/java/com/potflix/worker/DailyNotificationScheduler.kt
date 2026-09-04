package com.potflix.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object DailyNotificationScheduler {
    const val WORK_NAME = "potflix_daily_engagement"

    /**
     * Schedules the next engagement notification at a randomized hour during prime-time (7:30 PM - 12:00 AM).
     *
     * @param context Application context
     * @param forceNextDay If true, schedules strictly for tomorrow at a random hour (used after a notification fires).
     *                     If false, uses ExistingWorkPolicy.KEEP to preserve today's pending notification if one exists.
     */
    fun scheduleNextDailyNotification(context: Context, forceNextDay: Boolean = false) {
        val workManager = WorkManager.getInstance(context)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            // Prime-time movie night window: 7:30 PM (19:30) to 11:59 PM (23:59)
            val startMinuteOfDay = 19 * 60 + 30
            val randomOffsetMinutes = Random.nextInt(0, 270) // 270 minutes = 4.5 hours
            val targetMinuteOfDay = startMinuteOfDay + randomOffsetMinutes

            set(Calendar.HOUR_OF_DAY, targetMinuteOfDay / 60)
            set(Calendar.MINUTE, targetMinuteOfDay % 60)
            set(Calendar.SECOND, Random.nextInt(0, 60))
            set(Calendar.MILLISECOND, 0)
        }

        // If forced or target time today has already passed (or is within 5 minutes), schedule for tomorrow
        if (forceNextDay || target.timeInMillis <= now.timeInMillis + 5 * 60 * 1000L) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val delayMillis = (target.timeInMillis - now.timeInMillis).coerceAtLeast(60 * 1000L)

        val workRequest = OneTimeWorkRequestBuilder<DailyNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()

        val policy = if (forceNextDay) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }

        workManager.enqueueUniqueWork(
            WORK_NAME,
            policy,
            workRequest
        )
    }

    /**
     * Cancels any pending daily engagement notifications (e.g. if disabled in Settings).
     */
    fun cancelDailyNotification(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
