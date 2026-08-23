package com.potflix.util

import android.content.Context
import android.content.Intent
import android.os.Build
import com.potflix.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startDownload(title: String, streamUrl: String, poster: String?) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_ID, System.currentTimeMillis())
            putExtra(DownloadService.EXTRA_URL, streamUrl)
            putExtra(DownloadService.EXTRA_TITLE, title)
            putExtra(DownloadService.EXTRA_POSTER, poster)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pauseDownload(id: Long) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE
            putExtra(DownloadService.EXTRA_ID, id)
        }
        context.startService(intent)
    }

    fun resumeDownload(id: Long, url: String, title: String, poster: String?) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME
            putExtra(DownloadService.EXTRA_ID, id)
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra(DownloadService.EXTRA_TITLE, title)
            putExtra(DownloadService.EXTRA_POSTER, poster)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun deleteDownload(id: Long) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
            putExtra(DownloadService.EXTRA_ID, id)
        }
        context.startService(intent)
    }
}
