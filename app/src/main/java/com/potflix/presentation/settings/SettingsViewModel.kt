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

@androidx.annotation.Keep
data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val apkUrlArm64: String? = null,
    val apkUrlArmV7: String? = null,
    val apkUrlUniversal: String? = null
) {
    fun getDownloadUrlForDevice(): String {
        val abis = android.os.Build.SUPPORTED_ABIS ?: emptyArray()
        return when {
            abis.contains("arm64-v8a") && !apkUrlArm64.isNullOrEmpty() -> apkUrlArm64
            abis.contains("armeabi-v7a") && !apkUrlArmV7.isNullOrEmpty() -> apkUrlArmV7
            !apkUrlUniversal.isNullOrEmpty() -> apkUrlUniversal
            else -> apkUrl
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val serverPreferences: com.potflix.data.local.preferences.ServerPreferences,
    private val firebaseSyncManager: com.potflix.data.remote.FirebaseSyncManager
) : ViewModel() {

    private val _cacheSize = MutableStateFlow("Calculating...")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache: StateFlow<Boolean> = _isClearingCache.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val isSyncing = MutableStateFlow(false)
    val syncProgress = MutableStateFlow("Starting sync...")
    
    private val _lastSyncTime = MutableStateFlow("Never")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private var wasSyncing = false

    val activeServer = serverPreferences.activeServer
    
    val currentUserEmail = firebaseSyncManager.currentUserEmail

    val dailyNotificationEnabled = serverPreferences.dailyNotificationEnabled

    fun setDailyNotificationEnabled(enabled: Boolean) {
        serverPreferences.setDailyNotificationEnabled(enabled)
        if (enabled) {
            com.potflix.worker.DailyNotificationScheduler.scheduleNextDailyNotification(application)
            _toastMessage.value = "Daily recommendations enabled"
        } else {
            com.potflix.worker.DailyNotificationScheduler.cancelDailyNotification(application)
            _toastMessage.value = "Daily recommendations disabled"
        }
    }

    val preferredAudioLanguage = serverPreferences.preferredAudioLanguage
    val preferredSubtitleLanguage = serverPreferences.preferredSubtitleLanguage

    fun setPreferredAudioLanguage(langCode: String) {
        serverPreferences.setPreferredAudioLanguage(langCode)
        _toastMessage.value = "Audio set to: ${com.potflix.util.LanguageUtils.getAudioLanguageDisplayName(langCode)}"
    }

    fun setPreferredSubtitleLanguage(langCode: String) {
        serverPreferences.setPreferredSubtitleLanguage(langCode)
        _toastMessage.value = "Subtitles set to: ${com.potflix.util.LanguageUtils.getSubtitleLanguageDisplayName(langCode)}"
    }

    val customTmdbApiKey = serverPreferences.customTmdbApiKey

    fun setCustomTmdbApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        serverPreferences.setCustomTmdbApiKey(trimmed)
        if (trimmed.isBlank()) {
            _toastMessage.value = "Reset to default TMDB API key"
        } else {
            _toastMessage.value = "Custom TMDB API key saved"
        }
    }

    init {
        calculateCacheSize()
        fetchLastSyncTime()
        observeWorkManager()
    }
    
    private fun observeWorkManager() {
        androidx.work.WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkLiveData("ManualSync")
            .observeForever { workInfos ->
                if (workInfos.isNullOrEmpty()) return@observeForever
                val workInfo = workInfos[0]
                
                isSyncing.value = workInfo.state == androidx.work.WorkInfo.State.RUNNING || 
                                  workInfo.state == androidx.work.WorkInfo.State.ENQUEUED
                                  
                if (workInfo.state == androidx.work.WorkInfo.State.RUNNING) {
                    syncProgress.value = "Scanning servers for new content..."
                }
                
                if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    syncProgress.value = "Sync complete"
                    if (wasSyncing) {
                        _toastMessage.value = "Database Sync Complete!"
                        // Update last sync time
                        val prefs = application.getSharedPreferences("potflix_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
                        fetchLastSyncTime()
                    }
                }
                wasSyncing = isSyncing.value
            }
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
        val inputData = androidx.work.workDataOf(
            "isFullSync" to true,
            "isInitialLoad" to false
        )
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.potflix.worker.SyncWorker>()
            .setInputData(inputData)
            .build()
        androidx.work.WorkManager.getInstance(application)
            .enqueueUniqueWork("ManualSync", androidx.work.ExistingWorkPolicy.REPLACE, workRequest)
    }

    private val _showRestartDialog = MutableStateFlow(false)
    val showRestartDialog: StateFlow<Boolean> = _showRestartDialog.asStateFlow()

    fun dismissRestartDialog() {
        _showRestartDialog.value = false
    }

    fun setActiveServer(server: com.potflix.data.local.preferences.ServerConfig) {
        if (server.id == activeServer.value.id) {
            _toastMessage.value = "${server.name} is already selected."
            return
        }
        _toastMessage.value = "Testing connection to ${server.name}..."
        viewModelScope.launch(Dispatchers.IO) {
            val isReachable = try {
                val urlStr = server.baseUrl.trimEnd('/') + if (server.type == com.potflix.data.local.preferences.ServerType.ALIST) "/api/fs/list" else "/"
                val url = java.net.URL(urlStr)
                val connection = url.openConnection() as java.net.HttpURLConnection
                
                if (server.type == com.potflix.data.local.preferences.ServerType.ALIST) {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.outputStream.write("{\"path\":\"/\",\"password\":\"\",\"page\":1,\"per_page\":10}".toByteArray())
                } else {
                    connection.requestMethod = "HEAD"
                }
                
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val code = connection.responseCode
                code in 200..299 || code == 405 || code == 403 
            } catch (e: Exception) {
                false
            }
            
            withContext(Dispatchers.Main) {
                if (!isReachable) {
                    _toastMessage.value = "Warning: ${server.name} is unreachable. Server switch cancelled."
                    return@withContext
                }
                serverPreferences.setActiveServer(server)
                syncDatabase() // Re-sync when server is changed
                _showRestartDialog.value = true
            }
        }
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun logout() {
        viewModelScope.launch {
            firebaseSyncManager.logout()
            _toastMessage.value = "Logged out successfully"
        }
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
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // 1. Try querying GitHub Releases API for the latest published release
            try {
                val ghUrl = "https://api.github.com/repos/ReduanNurLabid/PotFlix/releases/latest"
                val ghRequest = okhttp3.Request.Builder()
                    .url(ghUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "PotFlix-Android-App")
                    .build()

                val ghResponse = client.newCall(ghRequest).execute()
                if (ghResponse.isSuccessful) {
                    val bodyStr = ghResponse.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val json = com.google.gson.JsonParser.parseString(bodyStr).asJsonObject
                        val tagName = json.get("tag_name")?.asString ?: ""
                        val releaseName = json.get("name")?.asString ?: tagName
                        val releaseBody = json.get("body")?.asString ?: "New version available with improvements and fixes."

                        if (isNewerVersion(tagName, com.potflix.BuildConfig.VERSION_NAME)) {
                            val assetsArray = json.getAsJsonArray("assets")
                            var arm64Url: String? = null
                            var armV7Url: String? = null
                            var universalUrl: String? = null
                            var defaultApkUrl: String? = null

                            if (assetsArray != null) {
                                for (assetElem in assetsArray) {
                                    val assetObj = assetElem.asJsonObject
                                    val name = assetObj.get("name")?.asString ?: ""
                                    val downloadUrl = assetObj.get("browser_download_url")?.asString ?: ""

                                    if (name.contains("arm64-v8a", ignoreCase = true)) {
                                        arm64Url = downloadUrl
                                    } else if (name.contains("armeabi-v7a", ignoreCase = true)) {
                                        armV7Url = downloadUrl
                                    } else if (name.contains("universal", ignoreCase = true)) {
                                        universalUrl = downloadUrl
                                    } else if (name.endsWith(".apk", ignoreCase = true) && defaultApkUrl == null) {
                                        defaultApkUrl = downloadUrl
                                    }
                                }
                            }

                            val resolvedApkUrl = arm64Url ?: armV7Url ?: universalUrl ?: defaultApkUrl
                                ?: "https://github.com/ReduanNurLabid/PotFlix/releases/latest"

                            val update = AppUpdate(
                                versionCode = parseVersionCode(tagName),
                                versionName = tagName.replace(Regex("[^0-9.]"), "").ifEmpty { releaseName },
                                apkUrl = resolvedApkUrl,
                                apkUrlArm64 = arm64Url,
                                apkUrlArmV7 = armV7Url,
                                apkUrlUniversal = universalUrl,
                                releaseNotes = releaseBody
                            )

                            withContext(Dispatchers.Main) {
                                _updateAvailable.value = update
                            }
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SettingsViewModel", "GitHub Releases check failed, trying update.json fallback: ${e.message}")
            }

            // 2. Fallback to update.json (checking android branch then main)
            val updateUrls = listOf(
                "https://raw.githubusercontent.com/ReduanNurLabid/PotFlix/android/update.json",
                "https://raw.githubusercontent.com/ReduanNurLabid/PotFlix/main/update.json"
            )

            for (updateUrl in updateUrls) {
                try {
                    val request = okhttp3.Request.Builder().url(updateUrl).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val update = com.google.gson.Gson().fromJson(body, AppUpdate::class.java)
                            val isNewer = update.versionCode > com.potflix.BuildConfig.VERSION_CODE ||
                                    isNewerVersion(update.versionName, com.potflix.BuildConfig.VERSION_NAME)
                            if (isNewer) {
                                withContext(Dispatchers.Main) {
                                    _updateAvailable.value = update
                                }
                                return@launch
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SettingsViewModel", "Failed to check update from $updateUrl: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                _toastMessage.value = "You are on the latest version! (v${com.potflix.BuildConfig.VERSION_NAME})"
            }
        }
    }

    private fun isNewerVersion(remoteStr: String, currentStr: String): Boolean {
        val cleanRemote = remoteStr.replace(Regex("[^0-9.]"), "").split(".").mapNotNull { it.toIntOrNull() }
        val cleanCurrent = currentStr.replace(Regex("[^0-9.]"), "").split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(cleanRemote.size, cleanCurrent.size)
        for (i in 0 until maxLen) {
            val r = cleanRemote.getOrElse(i) { 0 }
            val c = cleanCurrent.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersionCode(versionStr: String): Int {
        val parts = versionStr.replace(Regex("[^0-9.]"), "").split(".").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 100 + parts[1] * 10 + parts[2]
            2 -> parts[0] * 100 + parts[1] * 10
            1 -> parts[0]
            else -> com.potflix.BuildConfig.VERSION_CODE + 1
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
