package com.interlinedlist.android.feature.organizations.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Tolerates a boolean that the API may send either as a JSON boolean (`true`) or
 * as a string (`"true"` / `"1"`). The OpenAPI extract types several flags as
 * strings, but real payloads mix the two, so we normalise both to [Boolean].
 */
object FlexibleBooleanSerializer : KSerializer<Boolean?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Boolean? {
        val input = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val element = input.decodeJsonElement() as? JsonPrimitive ?: return null
        element.booleanOrNull?.let { return it }
        return when (element.content.trim().lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no", "" -> false
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        encoder.encodeBoolean(value ?: false)
    }
}
