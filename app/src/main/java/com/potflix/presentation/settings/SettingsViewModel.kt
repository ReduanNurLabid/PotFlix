package com.potflix.presentation.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val syncManager: com.potflix.data.sync.CatalogSyncManager
) : ViewModel() {

    private val _cacheSize = MutableStateFlow("Calculating...")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache: StateFlow<Boolean> = _isClearingCache.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val isSyncing = syncManager.isSyncing
    val syncProgress = syncManager.syncProgress
    
    private val _lastSyncTime = MutableStateFlow("Never")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    init {
        calculateCacheSize()
        fetchLastSyncTime()
    }
    
    fun fetchLastSyncTime() {
        val prefs = application.getSharedPreferences("potflix_prefs", Context.MODE_PRIVATE)
        val time = prefs.getLong("last_sync_time", 0L)
        if (time == 0L) {
            _lastSyncTime.value = "Never"
        } else {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault())
            _lastSyncTime.value = sdf.format(java.util.Date(time))
        }
    }
    
    fun syncDatabase() {
        viewModelScope.launch {
            syncManager.syncCatalog()
            fetchLastSyncTime()
            _toastMessage.value = "Database Sync Complete!"
        }
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    private fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val size = getDirSize(application.cacheDir)
            val coilCacheSize = getDirSize(File(application.cacheDir, "image_cache"))
            val formattedSize = formatSize(size + coilCacheSize)
            withContext(Dispatchers.Main) {
                _cacheSize.value = formattedSize
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _isClearingCache.value = true
            
            // Delete cache dir
            deleteDir(application.cacheDir)
            
            // Re-calculate
            calculateCacheSize()
            
            withContext(Dispatchers.Main) {
                _isClearingCache.value = false
                _toastMessage.value = "Cache cleared successfully"
            }
        }
    }

    fun exportDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = application.getDatabasePath("potflix_db")
                if (!dbFile.exists()) {
                    withContext(Dispatchers.Main) { _toastMessage.value = "Database file not found!" }
                    return@launch
                }
                
                // Copy to public Downloads directory
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, "potflix_db")
                
                dbFile.copyTo(destFile, overwrite = true)
                
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Database exported to Downloads folder!"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Export failed: ${e.message}"
                }
            }
        }
    }

    private val _updateAvailable = MutableStateFlow<AppUpdate?>(null)
    val updateAvailable: StateFlow<AppUpdate?> = _updateAvailable.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // To release updates, host this JSON file on the main branch of your GitHub repo
                val updateUrl = "https://raw.githubusercontent.com/ReduanNurLabid/PotFlix/main/update.json"
                
                val request = okhttp3.Request.Builder().url(updateUrl).build()
                val client = okhttp3.OkHttpClient()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val update = com.google.gson.Gson().fromJson(body, AppUpdate::class.java)
                        if (update.versionCode > com.potflix.BuildConfig.VERSION_CODE) {
                            withContext(Dispatchers.Main) {
                                _updateAvailable.value = update
                            }
                            return@launch
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "You are on the latest version!"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Failed to check for updates. Setup update.json online first."
                }
            }
        }
    }
    
    fun dismissUpdateDialog() {
        _updateAvailable.value = null
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
        } else if (dir.exists() && dir.isFile) {
            size += dir.length()
        }
        return size
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (i in children.indices) {
                    val success = deleteDir(File(dir, children[i]))
                    if (!success) {
                        return false
                    }
                }
            }
        }
        return dir?.delete() ?: false
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
