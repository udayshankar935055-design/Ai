package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.LocalMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun buildSuggestedTxtFileName(
    conversationTitle: String,
    timestampMillis: Long = System.currentTimeMillis()
): String {
    val cleanedTitle = conversationTitle
        .trim()
        .replace(Regex("[^A-Za-z0-9\\-_\\s]"), "")
        .replace(Regex("\\s+"), "_")
        .trim('_')
        .take(40)
        .ifBlank { "SmartAI_Chat" }

    val datePart = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(timestampMillis))
    return "${cleanedTitle}_$datePart.txt"
}

fun formatConversationAsText(
    conversationTitle: String,
    messages: List<LocalMessageEntity>,
    exportedAtMillis: Long = System.currentTimeMillis()
): String {
    val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val msgTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val safeTitle = conversationTitle.trim().ifBlank { "SMART AI Conversation" }

    return buildString {
        appendLine("==================================================")
        appendLine("SMART AI — Conversation Export")
        appendLine("Title: $safeTitle")
        appendLine("Exported: ${fullDateFormat.format(Date(exportedAtMillis))}")
        appendLine("Messages: ${messages.size}")
        appendLine("==================================================")
        appendLine()

        messages.forEachIndexed { index, msg ->
            val sender = if (msg.role == "user") {
                "You"
            } else {
                "SMART AI (${msg.modelId})"
            }
            val timestamp = msgTimeFormat.format(Date(msg.createdAtMillis))

            appendLine("[$timestamp] $sender:")

            if (!msg.attachmentType.isNullOrBlank()) {
                val labelSuffix = msg.attachmentLabel?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                appendLine("[Attachment: ${msg.attachmentType}$labelSuffix]")
            }

            if (!msg.thoughtText.isNullOrBlank()) {
                appendLine()
                appendLine("--- Reasoning Summary ---")
                appendLine(msg.thoughtText.trim())
                appendLine("-------------------------")
            }

            if (msg.text.isNotBlank()) {
                appendLine(msg.text.trim())
            }

            if (msg.sources.isNotEmpty()) {
                appendLine()
                appendLine("Sources:")
                msg.sources.forEach { source ->
                    appendLine("- $source")
                }
            }

            if (index < messages.lastIndex) {
                appendLine()
                appendLine("--------------------------------------------------")
                appendLine()
            }
        }
    }
}

suspend fun writeTextToUri(
    context: Context,
    uri: Uri,
    textContent: String
): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(textContent.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: return@withContext Result.failure(
            IllegalStateException("Unable to open destination file for writing.")
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun shareConversationTextFile(
    context: Context,
    fileName: String,
    textContent: String
): Result<Unit> {
    return try {
        val uri = withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = if (fileName.endsWith(".txt", ignoreCase = true)) fileName else "$fileName.txt"
            val file = File(exportDir, safeName)
            file.writeText(textContent, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, fileName.removeSuffix(".txt"))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Conversation as Text File"))
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

fun buildShareTextIntent(text: String, subject: String = "Smart AI Response"): Intent {
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
}

fun sharePlainText(context: Context, text: String, chooserTitle: String): Result<Unit> {
    return try {
        val sendIntent = buildShareTextIntent(text = text)
        context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
