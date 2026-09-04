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

    private val _dailyNotificationEnabled = MutableStateFlow(isDailyNotificationEnabled())
    val dailyNotificationEnabled: StateFlow<Boolean> = _dailyNotificationEnabled.asStateFlow()

    fun isDailyNotificationEnabled(): Boolean {
        return prefs.getBoolean("daily_notification_enabled", true)
    }

    fun setDailyNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("daily_notification_enabled", enabled).apply()
        _dailyNotificationEnabled.value = enabled
    }

    private val _preferredAudioLanguage = MutableStateFlow(getPreferredAudioLanguage())
    val preferredAudioLanguage: StateFlow<String> = _preferredAudioLanguage.asStateFlow()

    fun getPreferredAudioLanguage(): String {
        return prefs.getString("pref_audio_language", "en") ?: "en"
    }

    fun setPreferredAudioLanguage(langCode: String) {
        prefs.edit().putString("pref_audio_language", langCode).apply()
        _preferredAudioLanguage.value = langCode
    }

    private val _preferredSubtitleLanguage = MutableStateFlow(getPreferredSubtitleLanguage())
    val preferredSubtitleLanguage: StateFlow<String> = _preferredSubtitleLanguage.asStateFlow()

    fun getPreferredSubtitleLanguage(): String {
        return prefs.getString("pref_subtitle_language", "off") ?: "off"
    }

    fun setPreferredSubtitleLanguage(langCode: String) {
        prefs.edit().putString("pref_subtitle_language", langCode).apply()
        _preferredSubtitleLanguage.value = langCode
    }
}
