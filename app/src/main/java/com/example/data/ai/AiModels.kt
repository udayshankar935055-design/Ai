package com.example.data.ai

import android.graphics.Bitmap
import com.google.firebase.ai.type.AspectRatio

enum class AiMode(
    val label: String,
    val shortLabel: String,
    val modelId: String,
    val description: String
) {
    GENERAL(
        label = "Smart Assistant",
        shortLabel = "Smart",
        modelId = "gemini-4-hydrogen",
        description = "Balanced intelligence for everyday questions, writing & coding"
    ),
    DEEP_THINKING(
        label = "Deep Think",
        shortLabel = "Think",
        modelId = "gemini-4-carbon",
        description = "High thinking reasoning for complex math, STEM & architecture"
    ),
    FAST(
        label = "Flash Lite",
        shortLabel = "Fast",
        modelId = "gemini-3.1-flash-lite",
        description = "Ultra-low latency instant answers"
    ),
    SEARCH(
        label = "Web Search",
        shortLabel = "Search",
        modelId = "gemini-4-hydrogen",
        description = "Grounded with real-time Google Search data"
    ),
    MULTIMODAL(
        label = "Vision & Video",
        shortLabel = "Vision",
        modelId = "gemini-4-neon",
        description = "Deep image & video content analysis"
    )
}

enum class ImageAspectRatio(val label: String, val apiValue: AspectRatio) {
    RATIO_1_1("1:1", AspectRatio.SQUARE_1x1),
    RATIO_2_3("2:3", AspectRatio.PORTRAIT_2x3),
    RATIO_3_2("3:2", AspectRatio.LANDSCAPE_3x2),
    RATIO_3_4("3:4", AspectRatio.PORTRAIT_3x4),
    RATIO_4_3("4:3", AspectRatio.LANDSCAPE_4x3),
    RATIO_9_16("9:16", AspectRatio.PORTRAIT_9x16),
    RATIO_16_9("16:9", AspectRatio.LANDSCAPE_16x9),
    RATIO_21_9("21:9", AspectRatio.LANDSCAPE_21x9)
}

enum class ImageQualityTier(val label: String, val modelId: String) {
    STANDARD("Nano Banana 2.1", "gemini-nano-banana-2.1"),
    STUDIO_PRO("3 Pro Studio", "gemini-3-pro-image-preview")
}

sealed interface PendingAttachment {
    val label: String

    data class ImageAttachment(
        val bitmap: Bitmap,
        override val label: String
    ) : PendingAttachment

    data class VideoAttachment(
        val bytes: ByteArray,
        val mimeType: String,
        override val label: String
    ) : PendingAttachment
}

data class StreamChunkUpdate(
    val answerText: String,
    val thoughtText: String,
    val sources: List<String>,
    val modelId: String
)

data class GeneratedAudioData(
    val bytes: ByteArray,
    val mimeType: String
)
