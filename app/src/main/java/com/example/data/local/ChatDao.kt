package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY pinned DESC, updatedAtMillis DESC")
    fun observeConversations(userId: String): Flow<List<LocalConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND userId = :userId ORDER BY createdAtMillis ASC")
    fun observeMessages(userId: String, conversationId: String): Flow<List<LocalMessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND userId = :userId ORDER BY createdAtMillis ASC")
    suspend fun getMessagesOnce(userId: String, conversationId: String): List<LocalMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: LocalConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(conversations: List<LocalConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: LocalMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<LocalMessageEntity>)

    @Query("UPDATE conversations SET title = :title, updatedAtMillis = :updatedAtMillis WHERE id = :conversationId AND userId = :userId")
    suspend fun renameConversation(userId: String, conversationId: String, title: String, updatedAtMillis: Long)

    @Query("UPDATE conversations SET pinned = :pinned, updatedAtMillis = :updatedAtMillis WHERE id = :conversationId AND userId = :userId")
    suspend fun updatePinned(userId: String, conversationId: String, pinned: Boolean, updatedAtMillis: Long)

    @Query("UPDATE conversations SET modelMode = :modelMode, updatedAtMillis = :updatedAtMillis WHERE id = :conversationId AND userId = :userId")
    suspend fun updateModelMode(userId: String, conversationId: String, modelMode: String, updatedAtMillis: Long)

    @Query("UPDATE conversations SET lastMessagePreview = :preview, messageCount = :messageCount, updatedAtMillis = :updatedAtMillis WHERE id = :conversationId AND userId = :userId")
    suspend fun updateConversationSummary(
        userId: String,
        conversationId: String,
        preview: String,
        messageCount: Long,
        updatedAtMillis: Long
    )

    @Query("DELETE FROM conversations WHERE id = :conversationId AND userId = :userId")
    suspend fun deleteConversation(userId: String, conversationId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun deleteMessagesForConversation(userId: String, conversationId: String)

    @Query("DELETE FROM messages WHERE id = :messageId AND userId = :userId")
    suspend fun deleteMessage(userId: String, messageId: String)

    @Query("DELETE FROM messages WHERE userId = :userId")
    suspend fun clearUserMessages(userId: String)

    @Query("DELETE FROM conversations WHERE userId = :userId")
    suspend fun clearUserConversations(userId: String)
}
