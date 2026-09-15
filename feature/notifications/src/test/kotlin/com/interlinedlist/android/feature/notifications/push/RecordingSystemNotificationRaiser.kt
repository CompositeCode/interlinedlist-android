package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.feature.notifications.domain.Notification

/** Records every [post] call so tests can assert which notifications were raised. */
class RecordingSystemNotificationRaiser : SystemNotificationRaiser {

    /** Each element is the batch handed to one [post] call. */
    val batches = mutableListOf<List<Notification>>()

    /** Flattened ids across all batches, in order. */
    val postedIds: List<String> get() = batches.flatten().map { it.id }

    override fun post(items: List<Notification>) {
        batches += items
    }
}
