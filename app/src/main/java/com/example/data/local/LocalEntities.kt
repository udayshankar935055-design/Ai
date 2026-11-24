package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.json.JSONArray

@Entity(tableName = "conversations")
data class LocalConversationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val modelMode: String,
    val lastMessagePreview: String,
    val messageCount: Long,
    val pinned: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = LocalConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index("userId")]
)
data class LocalMessageEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val conversationId: String,
    val role: String,
    val text: String,
    val thoughtText: String?,
    val modelId: String,
    val attachmentType: String?,
    val attachmentLabel: String?,
    val sources: List<String>,
    val createdAtMillis: Long
)

class Converters {
    @TypeConverter
    fun fromStringList(list: List<String>): String {
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotEmpty() } }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
