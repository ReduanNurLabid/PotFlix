package com.potflix.presentation.onboarding

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.potflix.data.local.preferences.ServerConfig
import com.potflix.data.local.preferences.ServerPreferences
import com.potflix.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val serverPreferences: ServerPreferences,
    private val application: Application,
    private val aListScraper: com.potflix.data.remote.AListScraper
) : ViewModel() {

    data class DiscoveredFolder(
        val name: String,
        val url: String,
        val parentType: String
    )

    private val _serverStatuses = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val serverStatuses = _serverStatuses.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    private val _syncProgress = MutableStateFlow("Starting...")
    val syncProgress = _syncProgress.asStateFlow()
    
    private val _syncPercentage = MutableStateFlow(0f)
    val syncPercentage = _syncPercentage.asStateFlow()
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete = _onboardingComplete.asStateFlow()

    init {
        checkServers()
        observeWorkManager()
    }

    fun checkServers() {
        _isChecking.value = true
        val statuses = mutableMapOf<String, Boolean?>()
        ServerConfig.BUILT_IN_SERVERS.forEach { statuses[it.id] = null }
        _serverStatuses.value = statuses

        ServerConfig.BUILT_IN_SERVERS.forEach { server ->
            viewModelScope.launch(Dispatchers.IO) {
                val isReachable = try {
                    val urlStr = server.baseUrl.trimEnd('/') + if (server.type == com.potflix.data.local.preferences.ServerType.ALIST) "/api/fs/list" else "/"
                    val url = URL(urlStr)
                    val connection = url.openConnection() as HttpURLConnection
                    
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
                    val current = _serverStatuses.value.toMutableMap()
                    current[server.id] = isReachable
                    _serverStatuses.value = current
                    
                    if (!current.values.contains(null)) {
                        _isChecking.value = false
                    }
                }
            }
        }
    }

    fun selectServer(server: ServerConfig) {
        serverPreferences.setActiveServer(server)
        val inputData = androidx.work.workDataOf("isInitialLoad" to true)
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(application)
            .enqueueUniqueWork("ManualSync", ExistingWorkPolicy.REPLACE, workRequest)
    }

    private fun observeWorkManager() {
        WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkLiveData("ManualSync")
            .observeForever { workInfos ->
                if (workInfos.isNullOrEmpty()) return@observeForever
                val workInfo = workInfos[0]
                
                _isSyncing.value = workInfo.state == androidx.work.WorkInfo.State.RUNNING || 
                                  workInfo.state == androidx.work.WorkInfo.State.ENQUEUED
                                  
                if (workInfo.state == androidx.work.WorkInfo.State.RUNNING) {
                    val msg = workInfo.progress.getString("progress_msg") ?: "Scanning servers for content..."
                    val pct = workInfo.progress.getFloat("progress_pct", 0f)
                    _syncProgress.value = msg
                    _syncPercentage.value = pct / 100f
                }
                
                if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    _syncProgress.value = "Sync complete!"
                    serverPreferences.setOnboardingCompleted(true)
                    _onboardingComplete.value = true
                }
                
                if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                    _syncProgress.value = "Sync failed. You can retry later from settings."
                    serverPreferences.setOnboardingCompleted(true)
                    _onboardingComplete.value = true
                }
            }
    }
}
