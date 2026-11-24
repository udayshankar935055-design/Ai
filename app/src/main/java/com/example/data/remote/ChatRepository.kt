package com.example.data.remote

import com.example.data.ai.AiMode
import com.example.data.local.ChatDao
import com.example.data.local.LocalConversationEntity
import com.example.data.local.LocalMessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.UUID

const val GUEST_USER_ID = "guest_local"

class ChatRepository(
    private val chatDao: ChatDao,
    private val userId: String = GUEST_USER_ID
) {
    fun observeLocalConversations(): Flow<List<LocalConversationEntity>> =
        chatDao.observeConversations(userId)

    fun observeLocalMessages(conversationId: String): Flow<List<LocalMessageEntity>> =
        chatDao.observeMessages(userId, conversationId)

    suspend fun createConversation(
        title: String,
        modelMode: AiMode
    ): Result<String> {
        return try {
            val conversationId = "conv_${UUID.randomUUID().toString().replace("-", "")}"
            val safeTitle = title.trim().ifBlank { "New Chat" }.take(120)
            val nowMs = System.currentTimeMillis()

            val localEntity = LocalConversationEntity(
                id = conversationId,
                userId = userId,
                title = safeTitle,
                modelMode = modelMode.name,
                lastMessagePreview = "",
                messageCount = 0L,
                pinned = false,
                createdAtMillis = nowMs,
                updatedAtMillis = nowMs
            )
            chatDao.upsertConversation(localEntity)
            Result.success(conversationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        text: String,
        modelId: String,
        thoughtText: String? = null,
        attachmentType: String? = null,
        attachmentLabel: String? = null,
        sources: List<String> = emptyList(),
        updatedTitleIfFirst: String? = null,
        newMessageCount: Long
    ): Result<String> {
        return try {
            require(conversationId.isNotBlank()) { "Conversation ID cannot be blank" }
            val messageId = "msg_${UUID.randomUUID().toString().replace("-", "")}"
            val safeRole = if (role == "user") "user" else "model"
            val safeText = text.take(32000)
            val safeThought = thoughtText?.takeIf { it.isNotBlank() }?.take(32000)
            val safeModel = modelId.ifBlank { "gemini-4-hydrogen" }.take(80)
            val safeAttachmentType = attachmentType?.takeIf {
                it in listOf("IMAGE", "VIDEO", "AUDIO", "GENERATED_IMAGE")
            }
            val safeAttachmentLabel = attachmentLabel?.takeIf { it.isNotBlank() }?.take(160)
            val safeSources = sources.map { it.take(300) }.take(10)
            val nowMs = System.currentTimeMillis()

            val localMessage = LocalMessageEntity(
                id = messageId,
                userId = userId,
                conversationId = conversationId,
                role = safeRole,
                text = safeText,
                thoughtText = safeThought,
                modelId = safeModel,
                attachmentType = safeAttachmentType,
                attachmentLabel = safeAttachmentLabel,
                sources = safeSources,
                createdAtMillis = nowMs
            )
            chatDao.upsertMessage(localMessage)

            val preview = safeText.replace("\n", " ").trim().take(200)
            chatDao.updateConversationSummary(
                userId = userId,
                conversationId = conversationId,
                preview = preview,
                messageCount = newMessageCount,
                updatedAtMillis = nowMs
            )

            if (!updatedTitleIfFirst.isNullOrBlank()) {
                val cleanTitle = updatedTitleIfFirst.trim().take(120)
                chatDao.renameConversation(userId, conversationId, cleanTitle, nowMs)
            }

            Result.success(messageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameConversation(conversationId: String, newTitle: String): Result<Unit> {
        return try {
            val clean = newTitle.trim().take(120)
            require(clean.isNotEmpty()) { "Title cannot be empty" }
            chatDao.renameConversation(userId, conversationId, clean, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun togglePinned(conversationId: String, pinned: Boolean): Result<Unit> {
        return try {
            chatDao.updatePinned(userId, conversationId, pinned, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateConversationMode(conversationId: String, mode: AiMode): Result<Unit> {
        return try {
            chatDao.updateModelMode(userId, conversationId, mode.name, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearConversationMessages(conversationId: String): Result<Unit> {
        return try {
            chatDao.deleteMessagesForConversation(userId, conversationId)
            chatDao.updateConversationSummary(
                userId = userId,
                conversationId = conversationId,
                preview = "",
                messageCount = 0L,
                updatedAtMillis = System.currentTimeMillis()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> {
        return try {
            chatDao.deleteMessage(userId, messageId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        return try {
            chatDao.deleteMessagesForConversation(userId, conversationId)
            chatDao.deleteConversation(userId, conversationId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllLocalHistory(): Result<Unit> {
        return try {
            chatDao.clearUserMessages(userId)
            chatDao.clearUserConversations(userId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
