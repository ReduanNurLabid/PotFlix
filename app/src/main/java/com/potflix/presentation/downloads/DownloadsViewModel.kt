package com.potflix.presentation.downloads

import android.app.DownloadManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.data.local.dao.LocalDownloadDao
import com.potflix.data.local.entity.LocalDownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val localDownloadDao: LocalDownloadDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val downloads = localDownloadDao.getAllDownloads().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun deleteDownload(downloadId: Long) {
        val intent = android.content.Intent(context, com.potflix.service.DownloadService::class.java).apply {
            action = com.potflix.service.DownloadService.ACTION_CANCEL
            putExtra(com.potflix.service.DownloadService.EXTRA_ID, downloadId)
        }
        context.startService(intent)
    }

    fun pauseDownload(downloadId: Long) {
        val intent = android.content.Intent(context, com.potflix.service.DownloadService::class.java).apply {
            action = com.potflix.service.DownloadService.ACTION_PAUSE
            putExtra(com.potflix.service.DownloadService.EXTRA_ID, downloadId)
        }
        context.startService(intent)
    }

    fun resumeDownload(download: com.potflix.data.local.entity.LocalDownloadEntity) {
        val intent = android.content.Intent(context, com.potflix.service.DownloadService::class.java).apply {
            action = com.potflix.service.DownloadService.ACTION_RESUME
            putExtra(com.potflix.service.DownloadService.EXTRA_ID, download.downloadId)
            putExtra(com.potflix.service.DownloadService.EXTRA_URL, download.streamUrl)
            putExtra(com.potflix.service.DownloadService.EXTRA_TITLE, download.title)
            putExtra(com.potflix.service.DownloadService.EXTRA_POSTER, download.poster)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
