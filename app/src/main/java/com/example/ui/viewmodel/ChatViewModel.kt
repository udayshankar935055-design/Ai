package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlaybackHelper
import com.example.audio.AudioRecorderHelper
import com.example.data.ai.AiMode
import com.example.data.ai.PendingAttachment
import com.example.data.ai.SmartAiService
import com.example.data.local.LocalConversationEntity
import com.example.data.local.LocalMessageEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.UserSettings
import com.example.data.remote.ChatRepository
import com.example.network.ConnectivityObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ChatViewModel"

data class StreamingMessageState(
    val isGenerating: Boolean = false,
    val answerText: String = "",
    val thoughtText: String = "",
    val sources: List<String> = emptyList(),
    val modelId: String = ""
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val aiService: SmartAiService,
    private val settingsDataStore: SettingsDataStore,
    private val audioRecorder: AudioRecorderHelper,
    private val audioPlayback: AudioPlaybackHelper,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline

    val settings: StateFlow<UserSettings> = settingsDataStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserSettings()
        )

    val conversations: StateFlow<List<LocalConversationEntity>> =
        repository.observeLocalConversations()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<LocalMessageEntity>>(emptyList())
    val messages: StateFlow<List<LocalMessageEntity>> = _messages.asStateFlow()

    private val _selectedMode = MutableStateFlow(AiMode.GENERAL)
    val selectedMode: StateFlow<AiMode> = _selectedMode.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    private val _streamingState = MutableStateFlow(StreamingMessageState())
    val streamingState: StateFlow<StreamingMessageState> = _streamingState.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _ttsLoadingMessageId = MutableStateFlow<String?>(null)
    val ttsLoadingMessageId: StateFlow<String?> = _ttsLoadingMessageId.asStateFlow()

    val playingMessageId: StateFlow<String?> = audioPlayback.playingMessageId

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private var generationJob: Job? = null
    private var messageObserverJob: Job? = null
    private var ttsJob: Job? = null
    @Volatile
    private var suppressPartialSaveOnCancel = false

    init {
        viewModelScope.launch {
            settingsDataStore.settings.collectLatest { prefs ->
                if (_activeConversationId.value == null) {
                    _selectedMode.value = prefs.defaultAiMode
                }
            }
        }
        viewModelScope.launch {
            val seenActiveIds = mutableSetOf<String>()
            conversations.collect { list ->
                val activeId = _activeConversationId.value
                if (activeId != null) {
                    if (list.any { it.id == activeId }) {
                        seenActiveIds.add(activeId)
                    } else if (seenActiveIds.remove(activeId)) {
                        _activeConversationId.value = null
                        _messages.value = emptyList()
                        messageObserverJob?.cancel()
                    }
                }
            }
        }
    }

    fun selectConversation(conversationId: String) {
        if (_activeConversationId.value == conversationId) return
        stopGenerating()
        _activeConversationId.value = conversationId
        conversations.value.firstOrNull { it.id == conversationId }?.let { conv ->
            _selectedMode.value = AiMode.entries.firstOrNull { it.name == conv.modelMode }
                ?: AiMode.GENERAL
        }
        observeMessagesFor(conversationId)
    }

    fun startNewChat() {
        stopGenerating()
        audioPlayback.stop()
        messageObserverJob?.cancel()
        _activeConversationId.value = null
        _messages.value = emptyList()
        _pendingAttachment.value = null
        _errorBanner.value = null
        _selectedMode.value = settings.value.defaultAiMode
    }

    private fun observeMessagesFor(conversationId: String) {
        messageObserverJob?.cancel()
        messageObserverJob = viewModelScope.launch {
            repository.observeLocalMessages(conversationId)
                .collect { list ->
                    _messages.value = list
                }
        }
    }

    fun setInputText(value: String) {
        _inputText.value = value
    }

    fun setAiMode(mode: AiMode) {
        _selectedMode.value = mode
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            repository.updateConversationMode(convId, mode)
        }
    }

    fun attachImage(bitmap: Bitmap, label: String) {
        _pendingAttachment.value = PendingAttachment.ImageAttachment(bitmap, label)
        if (_selectedMode.value == AiMode.GENERAL || _selectedMode.value == AiMode.FAST) {
            _selectedMode.value = AiMode.MULTIMODAL
        }
    }

    fun attachVideo(bytes: ByteArray, mimeType: String, label: String) {
        _pendingAttachment.value = PendingAttachment.VideoAttachment(bytes, mimeType, label)
        _selectedMode.value = AiMode.MULTIMODAL
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun dismissError() {
        _errorBanner.value = null
    }

    fun sendMessage(overridePrompt: String? = null) {
        if (_streamingState.value.isGenerating) return
        val rawPrompt = (overridePrompt ?: _inputText.value).trim()
        val attachment = _pendingAttachment.value
        if (rawPrompt.isBlank() && attachment == null) return

        if (!isOnline.value) {
            _errorBanner.value = "You appear to be offline. Connect to the internet to ask Smart AI."
            return
        }

        val promptToSend = rawPrompt.ifBlank {
            when (attachment) {
                is PendingAttachment.ImageAttachment -> "Analyze this image and describe its key information."
                is PendingAttachment.VideoAttachment -> "Analyze this video and summarize its key moments and information."
                null -> ""
            }
        }

        if (overridePrompt == null) {
            _inputText.value = ""
        }
        _pendingAttachment.value = null
        _errorBanner.value = null

        val mode = _selectedMode.value
        val language = settings.value.preferredLanguage

        generationJob = viewModelScope.launch {
            try {
                var convId = _activeConversationId.value
                val isFirstMessage = convId == null || _messages.value.isEmpty()

                if (convId == null) {
                    val generatedTitle = promptToSend.replace("\n", " ").trim().take(48)
                        .ifBlank { "New Chat" }
                    val createResult = repository.createConversation(generatedTitle, mode)
                    convId = createResult.getOrElse { err ->
                        _errorBanner.value = err.localizedMessage ?: "Could not create conversation."
                        return@launch
                    }
                    _activeConversationId.value = convId
                    observeMessagesFor(convId)
                }

                val historyBeforeSend = _messages.value
                val attachmentTypeStr = when (attachment) {
                    is PendingAttachment.ImageAttachment -> "IMAGE"
                    is PendingAttachment.VideoAttachment -> "VIDEO"
                    null -> null
                }

                val userMsgCount = historyBeforeSend.size.toLong() + 1L
                val firstTitle = if (isFirstMessage) {
                    promptToSend.replace("\n", " ").trim().take(48).ifBlank { "New Chat" }
                } else null

                repository.addMessage(
                    conversationId = convId,
                    role = "user",
                    text = promptToSend,
                    modelId = mode.modelId,
                    attachmentType = attachmentTypeStr,
                    attachmentLabel = attachment?.label,
                    newMessageCount = userMsgCount,
                    updatedTitleIfFirst = firstTitle
                )

                streamAndSaveAssistantReply(
                    conversationId = convId,
                    mode = mode,
                    preferredLanguage = language,
                    history = historyBeforeSend,
                    prompt = promptToSend,
                    attachment = attachment,
                    nextMessageCount = userMsgCount + 1L
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _errorBanner.value = e.localizedMessage ?: "Failed to generate response."
                _streamingState.value = StreamingMessageState()
            }
        }
    }

    private suspend fun streamAndSaveAssistantReply(
        conversationId: String,
        mode: AiMode,
        preferredLanguage: String,
        history: List<LocalMessageEntity>,
        prompt: String,
        attachment: PendingAttachment?,
        nextMessageCount: Long
    ) {
        var latestAnswer = ""
        var latestThought = ""
        var latestSources = emptyList<String>()
        var usedModelId = mode.modelId

        _streamingState.value = StreamingMessageState(
            isGenerating = true,
            modelId = mode.modelId
        )

        try {
            aiService.streamChatResponse(
                mode = mode,
                preferredLanguage = preferredLanguage,
                history = history,
                userPrompt = prompt,
                attachment = attachment
            ).collect { chunk ->
                latestAnswer = chunk.answerText
                latestThought = chunk.thoughtText
                latestSources = chunk.sources
                usedModelId = chunk.modelId
                _streamingState.value = StreamingMessageState(
                    isGenerating = true,
                    answerText = latestAnswer,
                    thoughtText = latestThought,
                    sources = latestSources,
                    modelId = usedModelId
                )
            }

            if (latestAnswer.isBlank()) {
                _errorBanner.value = "The AI model returned an empty response. Please try again."
            } else {
                val addResult = repository.addMessage(
                    conversationId = conversationId,
                    role = "model",
                    text = latestAnswer,
                    thoughtText = latestThought.takeIf { it.isNotBlank() },
                    modelId = usedModelId,
                    sources = latestSources,
                    newMessageCount = nextMessageCount
                )
                val savedId = addResult.getOrNull()
                if (savedId != null && settings.value.autoSpeakResponses) {
                    speakMessage(savedId, latestAnswer)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            _errorBanner.value = "Response timed out after 60 seconds. Try again or switch to Flash Lite."
        } catch (e: CancellationException) {
            // User pressed Stop Generating; preserve partial text unless clearing/deleting chat
            if (!suppressPartialSaveOnCancel && latestAnswer.isNotBlank()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    repository.addMessage(
                        conversationId = conversationId,
                        role = "model",
                        text = "$latestAnswer\n\n_(Generation stopped)_",
                        thoughtText = latestThought.takeIf { it.isNotBlank() },
                        modelId = usedModelId,
                        sources = latestSources,
                        newMessageCount = nextMessageCount
                    )
                }
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI streaming error", e)
            _errorBanner.value = friendlyAiError(e)
        } finally {
            suppressPartialSaveOnCancel = false
            _streamingState.value = StreamingMessageState()
        }
    }

    fun stopGenerating() {
        suppressPartialSaveOnCancel = false
        generationJob?.cancel()
        generationJob = null
        _streamingState.value = StreamingMessageState()
    }

    fun regenerateLastResponse() {
        if (_streamingState.value.isGenerating) return
        val convId = _activeConversationId.value ?: return
        val currentMessages = _messages.value
        val lastUserIndex = currentMessages.indexOfLast { it.role == "user" }
        if (lastUserIndex == -1) return

        val lastUserMsg = currentMessages[lastUserIndex]
        val trailingModelMsgs = currentMessages.drop(lastUserIndex + 1).filter { it.role == "model" }
        val historyBeforeUser = currentMessages.take(lastUserIndex)

        if (!isOnline.value) {
            _errorBanner.value = "You appear to be offline. Connect to regenerate the answer."
            return
        }

        val mode = _selectedMode.value
        val language = settings.value.preferredLanguage

        generationJob = viewModelScope.launch {
            try {
                trailingModelMsgs.forEach { oldModel ->
                    repository.deleteMessage(convId, oldModel.id)
                }
                streamAndSaveAssistantReply(
                    conversationId = convId,
                    mode = mode,
                    preferredLanguage = language,
                    history = historyBeforeUser,
                    prompt = lastUserMsg.text,
                    attachment = null,
                    nextMessageCount = (historyBeforeUser.size + 2).toLong()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Regenerate failed", e)
                _errorBanner.value = friendlyAiError(e)
            }
        }
    }

    fun toggleVoiceRecording() {
        if (_isRecordingVoice.value) {
            stopAndTranscribeVoice()
        } else {
            if (!isOnline.value) {
                _errorBanner.value = "Internet connection is required for voice transcription."
                return
            }
            val started = audioRecorder.startRecording()
            if (started) {
                _isRecordingVoice.value = true
                _errorBanner.value = null
            } else {
                _errorBanner.value = "Unable to start microphone recording."
            }
        }
    }

    private fun stopAndTranscribeVoice() {
        _isRecordingVoice.value = false
        val audioBytes = audioRecorder.stopAndReadBytes()
        if (audioBytes == null || audioBytes.isEmpty()) {
            _errorBanner.value = "No audio captured. Please speak clearly and try again."
            return
        }
        if (!isOnline.value) {
            _errorBanner.value = "Internet connection is required to transcribe audio."
            return
        }

        viewModelScope.launch {
            _isTranscribing.value = true
            try {
                val transcript = aiService.transcribeAudio(audioBytes)
                if (transcript.isNotBlank()) {
                    val existing = _inputText.value.trim()
                    _inputText.value = if (existing.isEmpty()) transcript else "$existing $transcript"
                } else {
                    _errorBanner.value = "Could not detect speech in the recording."
                }
            } catch (e: TimeoutCancellationException) {
                _errorBanner.value = "Audio transcription timed out. Please try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                _errorBanner.value = friendlyAiError(e)
            } finally {
                _isTranscribing.value = false
            }
        }
    }

    fun speakMessage(messageId: String, text: String) {
        if (playingMessageId.value == messageId || _ttsLoadingMessageId.value == messageId) {
            stopSpeaking()
            return
        }
        if (!isOnline.value) {
            _errorBanner.value = "Internet connection is required for Gemini voice synthesis."
            return
        }

        ttsJob?.cancel()
        audioPlayback.stop()

        ttsJob = viewModelScope.launch {
            _ttsLoadingMessageId.value = messageId
            try {
                val audio = aiService.synthesizeSpeech(text, settings.value.ttsVoice)
                _ttsLoadingMessageId.value = null
                if (audio != null) {
                    audioPlayback.playAudio(
                        messageId = messageId,
                        bytes = audio.bytes,
                        mimeType = audio.mimeType,
                        onError = { msg -> _errorBanner.value = msg }
                    )
                } else {
                    _errorBanner.value = "Unable to generate speech audio for this message."
                }
            } catch (e: TimeoutCancellationException) {
                _ttsLoadingMessageId.value = null
                _errorBanner.value = "Text-to-speech timed out."
            } catch (e: CancellationException) {
                _ttsLoadingMessageId.value = null
                throw e
            } catch (e: Exception) {
                _ttsLoadingMessageId.value = null
                Log.e(TAG, "TTS failed", e)
                _errorBanner.value = friendlyAiError(e)
            }
        }
    }

    fun stopSpeaking() {
        ttsJob?.cancel()
        ttsJob = null
        _ttsLoadingMessageId.value = null
        audioPlayback.stop()
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            val result = repository.renameConversation(conversationId, newTitle)
            result.onFailure { _errorBanner.value = it.localizedMessage ?: "Rename failed." }
        }
    }

    fun togglePinned(conversationId: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinned(conversationId, !currentlyPinned)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                suppressPartialSaveOnCancel = true
                generationJob?.cancelAndJoin()
                generationJob = null
                startNewChat()
            }
            val result = repository.deleteConversation(conversationId)
            result.onFailure { _errorBanner.value = it.localizedMessage ?: "Delete failed." }
        }
    }

    fun clearCurrentChat() {
        val convId = _activeConversationId.value ?: return
        stopSpeaking()
        viewModelScope.launch {
            suppressPartialSaveOnCancel = true
            generationJob?.cancelAndJoin()
            generationJob = null
            _streamingState.value = StreamingMessageState()
            val result = repository.clearConversationMessages(convId)
            result.onFailure { _errorBanner.value = it.localizedMessage ?: "Clear chat failed." }
        }
    }

    fun clearAllHistory() {
        stopSpeaking()
        viewModelScope.launch {
            suppressPartialSaveOnCancel = true
            generationJob?.cancelAndJoin()
            generationJob = null
            startNewChat()
            val result = repository.clearAllLocalHistory()
            result.onFailure { _errorBanner.value = it.localizedMessage ?: "Failed to clear history." }
        }
    }

    private fun friendlyAiError(e: Exception): String {
        val msg = e.localizedMessage.orEmpty()
        return when {
            msg.contains("403") || msg.contains("App Check", ignoreCase = true) ->
                "AI service authorization error (App Check). Please restart the app or check Firebase provisioning."
            msg.contains("quota", ignoreCase = true) || msg.contains("429") ->
                "AI rate limit reached. Please wait a moment and try again."
            msg.contains("network", ignoreCase = true) || msg.contains("unable to resolve host", ignoreCase = true) ->
                "Network unavailable. Please check your internet connection."
            msg.isNotBlank() -> msg
            else -> "Smart AI encountered an error. Please try again."
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopAndDiscard()
        audioPlayback.stop()
    }
}
