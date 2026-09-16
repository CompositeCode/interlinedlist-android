package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.feature.notifications.domain.Notification

/** Records every [post] call so tests can assert which notifications were raised. */
class RecordingSystemNotificationRaiser : SystemNotificationRaiser {

    /** Each element is one [post] call: the batch and the cap it was given. */
    data class Posted(val items: List<Notification>, val maxIndividual: Int)

    val posts = mutableListOf<Posted>()

    /** Each element is the batch handed to one [post] call. */
    val batches: List<List<Notification>> get() = posts.map { it.items }

    /** Flattened ids across all batches, in order. */
    val postedIds: List<String> get() = batches.flatten().map { it.id }

    /** The cap handed to the single [post] call a test made. */
    val maxIndividual: Int get() = posts.single().maxIndividual

    override fun post(items: List<Notification>, maxIndividual: Int) {
        posts += Posted(items, maxIndividual)
    }
}
