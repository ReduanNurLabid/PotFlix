package com.potflix.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.potflix.data.local.dao.LocalDownloadDao
import com.potflix.data.local.entity.LocalDownloadEntity
import com.potflix.domain.model.Movie
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localDownloadDao: LocalDownloadDao
) {

    fun startDownload(title: String, streamUrl: String, poster: String?) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val uri = Uri.parse(streamUrl)
        val request = DownloadManager.Request(uri)
            .setTitle("Downloading $title")
            .setDescription("PotFlix Download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "PotFlix/$title.mp4")

        val downloadId = downloadManager.enqueue(request)

        // Save to DB
        CoroutineScope(Dispatchers.IO).launch {
            val entity = LocalDownloadEntity(
                downloadId = downloadId,
                title = title,
                streamUrl = streamUrl,
                localUri = null,
                poster = poster,
                status = DownloadManager.STATUS_PENDING,
                progress = 0
            )
            localDownloadDao.insert(entity)
        }
    }

    fun deleteDownload(downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadId)
        
        CoroutineScope(Dispatchers.IO).launch {
            localDownloadDao.deleteById(downloadId)
        }
    }
}
