package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.LocalMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "SmartAiService"
private const val AI_TIMEOUT_MS = 60_000L

open class SmartAiService {

    private fun buildSystemInstruction(preferredLanguage: String): Content {
        val languageGuidance = if (preferredLanguage == "Auto-detect") {
            "Always respond in the same language the user writes or speaks in (including Hindi, English, Hinglish, and any other world language)."
        } else {
            "Prefer responding in $preferredLanguage unless the user explicitly asks for another language."
        }
        return content {
            text(
                """
                You are SMART AI, an intelligent, accurate, warm, and highly capable personal AI assistant.
                - Explain complex topics in clear, simple language with helpful structure, examples, and step-by-step reasoning when useful.
                - Assist across education, science, mathematics, technology, general knowledge, programming (with clean code blocks), creative stories, and brainstorming.
                - Maintain awareness of the ongoing conversation context.
                - $languageGuidance
                """.trimIndent()
            )
        }
    }

    private val transcribeModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-transcribe")
    }

    private fun createChatModel(mode: AiMode, preferredLanguage: String): GenerativeModel {
        val sysInstruction = buildSystemInstruction(preferredLanguage)
        return when (mode) {
            AiMode.GENERAL -> Firebase.ai.generativeModel(
                modelName = AiMode.GENERAL.modelId,
                systemInstruction = sysInstruction
            )

            AiMode.DEEP_THINKING -> Firebase.ai.generativeModel(
                modelName = AiMode.DEEP_THINKING.modelId,
                generationConfig = generationConfig {
                    // High thinking mode: do NOT set maxOutputTokens
                    thinkingConfig = thinkingConfig {
                        thinkingLevel = ThinkingLevel.HIGH
                        includeThoughts = true
                    }
                },
                systemInstruction = sysInstruction
            )

            AiMode.FAST -> Firebase.ai.generativeModel(
                modelName = AiMode.FAST.modelId,
                systemInstruction = sysInstruction
            )

            AiMode.SEARCH -> Firebase.ai.generativeModel(
                modelName = AiMode.SEARCH.modelId,
                tools = listOf(Tool.googleSearch()),
                systemInstruction = sysInstruction
            )

            AiMode.MULTIMODAL -> Firebase.ai.generativeModel(
                modelName = AiMode.MULTIMODAL.modelId,
                systemInstruction = sysInstruction
            )
        }
    }

    open fun streamChatResponse(
        mode: AiMode,
        preferredLanguage: String,
        history: List<LocalMessageEntity>,
        userPrompt: String,
        attachment: PendingAttachment?
    ): Flow<StreamChunkUpdate> = flow {
        val effectiveMode = if (attachment != null && mode != AiMode.DEEP_THINKING && mode != AiMode.SEARCH) {
            AiMode.MULTIMODAL
        } else {
            mode
        }
        val model = createChatModel(effectiveMode, preferredLanguage)

        val rawHistory = history.takeLast(24).map { msg ->
            val role = if (msg.role == "user") "user" else "model"
            role to msg.text.ifBlank { "(media attachment)" }
        }
        // Ensure strict user/model alternation and avoid ending history with an unanswered user message
        val alternatingHistory = mutableListOf<Pair<String, String>>()
        for ((role, text) in rawHistory) {
            if (alternatingHistory.isEmpty() && role == "model") continue
            if (alternatingHistory.isNotEmpty() && alternatingHistory.last().first == role) {
                val prev = alternatingHistory.removeAt(alternatingHistory.lastIndex)
                alternatingHistory.add(role to "${prev.second}\n\n$text")
            } else {
                alternatingHistory.add(role to text)
            }
        }
        if (alternatingHistory.lastOrNull()?.first == "user") {
            alternatingHistory.removeAt(alternatingHistory.lastIndex)
        }
        val chatHistory = alternatingHistory.map { (role, text) ->
            content(role = role) { text(text) }
        }
        val chat = model.startChat(history = chatHistory)

        val promptContent = content("user") {
            when (attachment) {
                is PendingAttachment.ImageAttachment -> {
                    image(attachment.bitmap)
                }
                is PendingAttachment.VideoAttachment -> {
                    inlineData(attachment.bytes, attachment.mimeType)
                }
                null -> Unit
            }
            if (userPrompt.isNotBlank()) {
                text(userPrompt)
            } else if (attachment != null) {
                text("Analyze this media carefully and explain its key details.")
            }
        }

        val answerBuilder = StringBuilder()
        val thoughtBuilder = StringBuilder()
        val collectedSources = linkedSetOf<String>()

        withTimeout(AI_TIMEOUT_MS) {
            chat.sendMessageStream(promptContent).collect { chunk ->
                val candidate = chunk.candidates.firstOrNull()
                val textParts = candidate?.content?.parts.orEmpty().filterIsInstance<TextPart>()
                if (textParts.isNotEmpty()) {
                    textParts.forEach { textPart ->
                        if (textPart.isThought) {
                            thoughtBuilder.append(textPart.text)
                        } else {
                            answerBuilder.append(textPart.text)
                        }
                    }
                } else {
                    chunk.text?.let { answerBuilder.append(it) }
                }

                candidate?.groundingMetadata?.groundingChunks?.forEach { gChunk ->
                    val web = gChunk.web
                    val entry = web?.uri?.takeIf { it.isNotBlank() } ?: web?.title
                    if (!entry.isNullOrBlank()) {
                        collectedSources.add(entry)
                    }
                }

                emit(
                    StreamChunkUpdate(
                        answerText = answerBuilder.toString(),
                        thoughtText = thoughtBuilder.toString(),
                        sources = collectedSources.take(10),
                        modelId = effectiveMode.modelId
                    )
                )
            }
        }
    }

    open suspend fun transcribeAudio(audioBytes: ByteArray): String {
        return withTimeout(AI_TIMEOUT_MS) {
            val response = transcribeModel.generateContent(
                content {
                    inlineData(audioBytes, "audio/mp4")
                    text("Transcribe the spoken audio accurately in its original language (Hindi, English, or any language). Output only the transcribed text with proper punctuation.")
                }
            )
            response.text?.trim().orEmpty()
        }
    }

    open suspend fun generateOrEditImage(
        prompt: String,
        aspectRatio: ImageAspectRatio,
        qualityTier: ImageQualityTier,
        sourceBitmap: Bitmap?
    ): Bitmap? {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(aspectRatio.apiValue)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
        val model = Firebase.ai.generativeModel(
            modelName = qualityTier.modelId,
            generationConfig = config
        )

        return withTimeout(AI_TIMEOUT_MS) {
            val response = if (sourceBitmap != null) {
                model.generateContent(
                    content {
                        image(sourceBitmap)
                        text(prompt)
                    }
                )
            } else {
                model.generateContent(prompt)
            }

            response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image
        }
    }

    @OptIn(PublicPreviewAPI::class)
    open suspend fun synthesizeSpeech(text: String, voiceName: String): GeneratedAudioData? {
        val cleanText = text.replace(Regex("```[\\s\\S]*?```"), "Code block omitted.")
            .replace(Regex("[*_`#>]"), "")
            .trim()
            .take(3000)

        if (cleanText.isBlank()) return null

        val ttsModel = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash-tts",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(Voice(voiceName))
            }
        )

        return withTimeout(AI_TIMEOUT_MS) {
            val response = ttsModel.generateContent(
                content {
                    text("Read the following text naturally and clearly in its native language:\n$cleanText")
                }
            )
            val audioPart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<InlineDataPart>()
                ?.firstOrNull { it.mimeType.startsWith("audio") }
                ?: response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<InlineDataPart>()
                    ?.firstOrNull()

            audioPart?.let {
                Log.d(TAG, "TTS audio generated: mime=${it.mimeType}, size=${it.inlineData.size}")
                GeneratedAudioData(bytes = it.inlineData, mimeType = it.mimeType)
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun createLiveVoiceModel(voiceName: String, preferredLanguage: String): LiveGenerativeModel {
        return Firebase.ai.liveModel(
            modelName = "gemini-3.8-live",
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(Voice(voiceName))
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
            },
            systemInstruction = buildSystemInstruction(preferredLanguage)
        )
    }
}
