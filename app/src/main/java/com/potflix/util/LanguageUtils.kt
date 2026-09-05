package com.potflix.util

import org.videolan.libvlc.MediaPlayer

object LanguageUtils {
    data class LanguageOption(
        val code: String,
        val displayName: String,
        val matchKeywords: List<String>
    )

    val AUDIO_LANGUAGES = listOf(
        LanguageOption("auto", "Default / Original", emptyList()),
        LanguageOption("en", "English", listOf("english", "eng", "en")),
        LanguageOption("bn", "Bengali (বাংলা)", listOf("bengali", "bangla", "ben", "bn")),
        LanguageOption("hi", "Hindi (हिंदी)", listOf("hindi", "hin", "hi")),
        LanguageOption("ja", "Japanese (日本語)", listOf("japanese", "jpn", "ja")),
        LanguageOption("ko", "Korean (한국어)", listOf("korean", "kor", "ko")),
        LanguageOption("es", "Spanish (Español)", listOf("spanish", "spa", "es")),
        LanguageOption("fr", "French (Français)", listOf("french", "fre", "fra", "fr")),
        LanguageOption("de", "German (Deutsch)", listOf("german", "ger", "deu", "de")),
        LanguageOption("ar", "Arabic (العربية)", listOf("arabic", "ara", "ar")),
        LanguageOption("ta", "Tamil (தமிழ்)", listOf("tamil", "tam", "ta")),
        LanguageOption("te", "Telugu (తెలుగు)", listOf("telugu", "tel", "te")),
        LanguageOption("ru", "Russian (Русский)", listOf("russian", "rus", "ru")),
        LanguageOption("zh", "Chinese (中文)", listOf("chinese", "chi", "zho", "zh"))
    )

    val SUBTITLE_LANGUAGES = listOf(
        LanguageOption("off", "Off", emptyList()),
        LanguageOption("en", "English", listOf("english", "eng", "en")),
        LanguageOption("bn", "Bengali (বাংলা)", listOf("bengali", "bangla", "ben", "bn")),
        LanguageOption("hi", "Hindi (हिंदी)", listOf("hindi", "hin", "hi")),
        LanguageOption("ja", "Japanese (日本語)", listOf("japanese", "jpn", "ja")),
        LanguageOption("ko", "Korean (한국어)", listOf("korean", "kor", "ko")),
        LanguageOption("es", "Spanish (Español)", listOf("spanish", "spa", "es")),
        LanguageOption("fr", "French (Français)", listOf("french", "fre", "fra", "fr")),
        LanguageOption("de", "German (Deutsch)", listOf("german", "ger", "deu", "de")),
        LanguageOption("ar", "Arabic (العربية)", listOf("arabic", "ara", "ar")),
        LanguageOption("ru", "Russian (Русский)", listOf("russian", "rus", "ru")),
        LanguageOption("zh", "Chinese (中文)", listOf("chinese", "chi", "zho", "zh"))
    )

    fun getAudioLanguageDisplayName(code: String): String {
        return AUDIO_LANGUAGES.find { it.code == code }?.displayName ?: "English"
    }

    fun getSubtitleLanguageDisplayName(code: String): String {
        return SUBTITLE_LANGUAGES.find { it.code == code }?.displayName ?: "Off"
    }

    fun findMatchingTrack(
        tracks: List<MediaPlayer.TrackDescription>,
        languageCode: String,
        isSubtitle: Boolean = false
    ): MediaPlayer.TrackDescription? {
        if (tracks.isEmpty()) return null

        val options = if (isSubtitle) SUBTITLE_LANGUAGES else AUDIO_LANGUAGES
        val option = options.find { it.code == languageCode } ?: return null
        if (option.matchKeywords.isEmpty()) return null

        // Priority 1: Exact whole word or bracket match: e.g. "[eng]", "(eng)", " English "
        for (track in tracks) {
            if (track.id == -1) continue
            val lower = track.name.lowercase()
            for (keyword in option.matchKeywords) {
                if (lower.contains("[$keyword]") || lower.contains("($keyword)") || lower.contains(" $keyword ") || lower.startsWith("$keyword ") || lower.endsWith(" $keyword")) {
                    return track
                }
            }
        }

        // Priority 2: Substring match: e.g. "English [Original]", "Track 1 - eng"
        for (track in tracks) {
            if (track.id == -1) continue
            val lower = track.name.lowercase()
            for (keyword in option.matchKeywords) {
                if (keyword.length > 2 && lower.contains(keyword)) {
                    return track
                }
            }
        }

        return null
    }
}
