package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.ai.AiMode
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_ai_settings")

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultAiMode: AiMode = AiMode.GENERAL,
    val preferredLanguage: String = "Auto-detect",
    val ttsVoice: String = "Kore",
    val autoSpeakResponses: Boolean = false
)

class SettingsDataStore(
    private val context: Context,
    private val store: DataStore<Preferences> = context.applicationContext.dataStore
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_AI_MODE = stringPreferencesKey("default_ai_mode")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
    }

    val settings: Flow<UserSettings> = store.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let { raw ->
            ThemeMode.entries.firstOrNull { it.name == raw }
        } ?: ThemeMode.DARK

        val mode = prefs[Keys.DEFAULT_AI_MODE]?.let { raw ->
            AiMode.entries.firstOrNull { it.name == raw }
        } ?: AiMode.GENERAL

        UserSettings(
            themeMode = theme,
            defaultAiMode = mode,
            preferredLanguage = prefs[Keys.PREFERRED_LANGUAGE] ?: "Auto-detect",
            ttsVoice = prefs[Keys.TTS_VOICE] ?: "Kore",
            autoSpeakResponses = prefs[Keys.AUTO_SPEAK] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultAiMode(mode: AiMode) {
        store.edit { it[Keys.DEFAULT_AI_MODE] = mode.name }
    }

    suspend fun setPreferredLanguage(language: String) {
        store.edit { it[Keys.PREFERRED_LANGUAGE] = language }
    }

    suspend fun setTtsVoice(voice: String) {
        store.edit { it[Keys.TTS_VOICE] = voice }
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        store.edit { it[Keys.AUTO_SPEAK] = enabled }
    }
}
