package com.interlinedlist.android.feature.profile.ui

import com.interlinedlist.android.feature.profile.data.mapper.toRequest
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * The same JSON configuration the network module uses for request bodies: null
 * fields are dropped rather than serialised as `null`.
 */
private val patchJson = Json { explicitNulls = false }

/**
 * The keys this update would actually put on the `PATCH /api/user/update` body,
 * obtained by running it through the real mapper and serializer. Lets a ViewModel
 * test assert "this preference PATCHed alone" against the wire shape rather than
 * against a hand-maintained list of fields.
 */
fun UserSettingsUpdate.touchedFieldNames(): Set<String> =
    (patchJson.encodeToJsonElement(toRequest()) as JsonObject).keys
