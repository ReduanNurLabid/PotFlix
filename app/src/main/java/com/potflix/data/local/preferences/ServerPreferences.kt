package com.potflix.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)

    private val _activeServer = MutableStateFlow(getActiveServerConfig())
    val activeServer: StateFlow<ServerConfig> = _activeServer.asStateFlow()

    fun getActiveServerConfig(): ServerConfig {
        val serverId = prefs.getString("active_server_id", ServerConfig.DHAKAFLIX.id) ?: ServerConfig.DHAKAFLIX.id
        return ServerConfig.BUILT_IN_SERVERS.find { it.id == serverId } ?: ServerConfig.DHAKAFLIX
    }

    fun setActiveServer(server: ServerConfig) {
        prefs.edit().putString("active_server_id", server.id).apply()
        _activeServer.value = server
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
    }
}
