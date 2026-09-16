package com.interlinedlist.android.feature.messages.data.local

import androidx.room.TypeConverter
import com.interlinedlist.android.feature.messages.domain.LinkPreview
import com.interlinedlist.android.feature.messages.domain.PushedMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room [TypeConverter]s for the message table's composite columns. Media URL lists
 * and the link-preview card are stored as JSON strings so a single flat table can
 * still cache the richer message shape without extra tables/joins.
 */
class MessageConverters {

    @TypeConverter
    fun stringListToJson(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(String.serializer()), value) }
            .getOrDefault(emptyList())

    @TypeConverter
    fun linkPreviewToJson(value: LinkPreview?): String? =
        value?.let { json.encodeToString(LinkPreviewSurrogate.serializer(), it.toSurrogate()) }

    @TypeConverter
    fun jsonToLinkPreview(value: String?): LinkPreview? =
        if (value.isNullOrBlank()) null
        else runCatching {
            json.decodeFromString(LinkPreviewSurrogate.serializer(), value).toDomain()
        }.getOrNull()

    @TypeConverter
    fun pushedMessageToJson(value: PushedMessage?): String? =
        value?.let { json.encodeToString(PushedMessageSurrogate.serializer(), it.toSurrogate()) }

    @TypeConverter
    fun jsonToPushedMessage(value: String?): PushedMessage? =
        if (value.isNullOrBlank()) null
        else runCatching {
            json.decodeFromString(PushedMessageSurrogate.serializer(), value).toDomain()
        }.getOrNull()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Serializable mirror of the domain [LinkPreview] so the domain type can stay a
 * plain data class (no serialization annotations leaking into the domain layer).
 */
@Serializable
private data class LinkPreviewSurrogate(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
)

private fun LinkPreview.toSurrogate() = LinkPreviewSurrogate(url, title, description, imageUrl, siteName)
private fun LinkPreviewSurrogate.toDomain() = LinkPreview(url, title, description, imageUrl, siteName)

/** Serializable mirror of the domain [PushedMessage], for the same reason. */
@Serializable
private data class PushedMessageSurrogate(
    val id: String,
    val content: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String? = null,
    val authorAvatarUrl: String? = null,
    val createdAt: String? = null,
)

private fun PushedMessage.toSurrogate() = PushedMessageSurrogate(
    id, content, authorUsername, authorDisplayName, authorAvatarUrl, createdAt,
)

private fun PushedMessageSurrogate.toDomain() = PushedMessage(
    id, content, authorUsername, authorDisplayName, authorAvatarUrl, createdAt,
)
