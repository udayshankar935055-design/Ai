package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiMode
import com.example.data.local.ChatDao
import com.example.data.local.SettingsDataStore
import com.example.data.local.UserSettings
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val currentUserId: String,
    private val settingsDataStore: SettingsDataStore,
    private val chatDao: ChatDao
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsDataStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserSettings()
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setDefaultAiMode(mode: AiMode) {
        viewModelScope.launch { settingsDataStore.setDefaultAiMode(mode) }
    }

    fun setPreferredLanguage(language: String) {
        viewModelScope.launch { settingsDataStore.setPreferredLanguage(language) }
    }

    fun setTtsVoice(voice: String) {
        viewModelScope.launch { settingsDataStore.setTtsVoice(voice) }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoSpeak(enabled) }
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            chatDao.clearUserMessages(currentUserId)
            chatDao.clearUserConversations(currentUserId)
        }
    }
}
