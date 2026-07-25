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

    fun checkDownloadStatus() {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        viewModelScope.launch {
            downloads.value.forEach { entity ->
                if (entity.status != DownloadManager.STATUS_SUCCESSFUL) {
                    val query = DownloadManager.Query().setFilterById(entity.downloadId)
                    val cursor = downloadManager.query(query)
                    
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        
                        val status = cursor.getInt(statusIndex)
                        val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                        val bytesTotal = cursor.getLong(bytesTotalIndex)
                        val localUri = cursor.getString(uriIndex)
                        
                        val progress = if (bytesTotal > 0) (bytesDownloaded * 100 / bytesTotal).toInt() else 0
                        
                        if (status != entity.status || progress != entity.progress) {
                            localDownloadDao.update(
                                entity.copy(
                                    status = status,
                                    progress = progress,
                                    localUri = localUri ?: entity.localUri
                                )
                            )
                        }
                    }
                    cursor.close()
                }
            }
        }
    }
    fun deleteDownload(downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadId)
        
        viewModelScope.launch(Dispatchers.IO) {
            localDownloadDao.deleteById(downloadId)
        }
    }
}
