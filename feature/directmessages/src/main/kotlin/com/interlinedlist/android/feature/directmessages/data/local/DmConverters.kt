package com.interlinedlist.android.feature.directmessages.data.local

import androidx.room.TypeConverter

/** Room converters for the module's non-primitive columns. */
class DmConverters {

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        if (value.isEmpty()) "" else value.joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(SEPARATOR)

    private companion object {
        // Image URLs never contain a newline, so it is a safe delimiter.
        const val SEPARATOR = "\n"
    }
}
