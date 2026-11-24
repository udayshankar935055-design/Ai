package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.SmartAiService
import com.example.data.local.SettingsDataStore
import com.example.network.ConnectivityObserver
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.liveAudioConversationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "LiveVoiceViewModel"

enum class LiveConnectionStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class LiveTranscriptItem(
    val id: String,
    val isUser: Boolean,
    val text: String
)

@OptIn(PublicPreviewAPI::class)
class LiveVoiceViewModel(
    private val aiService: SmartAiService,
    private val settingsDataStore: SettingsDataStore,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline

    private val _status = MutableStateFlow(LiveConnectionStatus.IDLE)
    val status: StateFlow<LiveConnectionStatus> = _status.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _transcripts = MutableStateFlow<List<LiveTranscriptItem>>(emptyList())
    val transcripts: StateFlow<List<LiveTranscriptItem>> = _transcripts.asStateFlow()

    private var activeSession: LiveSession? = null
    private var liveJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startConversation() {
        if (_status.value == LiveConnectionStatus.CONNECTING || _status.value == LiveConnectionStatus.CONNECTED) {
            return
        }
        if (!isOnline.value) {
            _status.value = LiveConnectionStatus.ERROR
            _errorMessage.value = "Internet connection is required for Gemini Live Voice."
            return
        }

        _errorMessage.value = null
        _status.value = LiveConnectionStatus.CONNECTING

        liveJob = viewModelScope.launch {
            try {
                val prefs = settingsDataStore.settings.first()
                val liveModel = aiService.createLiveVoiceModel(
                    voiceName = prefs.ttsVoice,
                    preferredLanguage = prefs.preferredLanguage
                )
                val session = withTimeout(30_000L) {
                    liveModel.connect()
                }
                activeSession = session
                _status.value = LiveConnectionStatus.CONNECTED

                val config = liveAudioConversationConfig {
                    enableInterruptions = true
                    transcriptHandler = { userTx, modelTx ->
                        val userStr = userTx?.text?.trim().orEmpty()
                        val modelStr = modelTx?.text?.trim().orEmpty()
                        if (userStr.isNotBlank()) {
                            appendTranscript(isUser = true, chunk = userStr)
                        }
                        if (modelStr.isNotBlank()) {
                            appendTranscript(isUser = false, chunk = modelStr)
                        }
                    }
                    goAwayHandler = {
                        _errorMessage.value = "Live session ending soon. Tap Start to reconnect."
                    }
                }
                session.startAudioConversation(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live voice session failed", e)
                _status.value = LiveConnectionStatus.ERROR
                _errorMessage.value = e.localizedMessage ?: "Unable to connect to Gemini Live Voice."
            }
        }
    }

    private fun appendTranscript(isUser: Boolean, chunk: String) {
        val current = _transcripts.value.toMutableList()
        val last = current.lastOrNull()
        if (last != null && last.isUser == isUser) {
            current[current.lastIndex] = last.copy(text = (last.text + " " + chunk).trim())
        } else {
            current.add(
                LiveTranscriptItem(
                    id = "${System.nanoTime()}",
                    isUser = isUser,
                    text = chunk
                )
            )
        }
        _transcripts.value = current
    }

    fun sendTextPromptInLive(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val session = activeSession ?: return
        viewModelScope.launch {
            try {
                appendTranscript(isUser = true, chunk = clean)
                session.send(clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send text in live session", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to send message."
            }
        }
    }

    fun stopConversation() {
        liveJob?.cancel()
        liveJob = null
        val sessionToClose = activeSession
        activeSession = null
        _status.value = LiveConnectionStatus.IDLE
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    sessionToClose?.stopAudioConversation()
                } catch (_: Exception) {
                }
                try {
                    sessionToClose?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        stopConversation()
    }
}
