package com.interlinedlist.android.feature.messages.data.local

import androidx.room.TypeConverter
import com.interlinedlist.android.feature.messages.domain.LinkPreview
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
