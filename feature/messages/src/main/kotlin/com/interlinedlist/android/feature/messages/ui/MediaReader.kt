package com.interlinedlist.android.feature.messages.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** A picked media file read into memory, ready to upload. */
data class PickedMedia(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)

/**
 * Reads the bytes and display name of a picked media [uri] via the platform
 * [android.content.ContentResolver]. Returns null when the URI can't be opened, so
 * the caller can quietly skip an unreadable pick. Kept in the UI layer so the
 * ViewModel/repository stay free of Android URI/ContentResolver dependencies.
 */
fun readMediaBytes(context: Context, uri: Uri, isVideo: Boolean): PickedMedia? {
    val resolver = context.contentResolver
    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null

    val mimeType = resolver.getType(uri) ?: if (isVideo) "video/*" else "image/*"
    val fallbackName = if (isVideo) "video" else "image"
    val name = queryDisplayName(context, uri) ?: "$fallbackName-${System.currentTimeMillis()}"
    return PickedMedia(bytes = bytes, fileName = name, mimeType = mimeType)
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    }.getOrNull()
