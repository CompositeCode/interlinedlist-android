package com.interlinedlist.android.core.model

/**
 * Subscription tier reported by the API as `customerStatus` on the user object.
 * Premium features (media attachments, scheduling, cross-posting, document
 * creation) are gated on [isSubscriber].
 */
enum class CustomerStatus(val apiValue: String) {
    FREE("free"),
    SUBSCRIBER("subscriber"),
    SUBSCRIBER_MONTHLY("subscriber:monthly"),
    SUBSCRIBER_ANNUAL("subscriber:annual"),
    UNKNOWN("");

    /** True when the account has any active paid subscription. */
    val isSubscriber: Boolean
        get() = this == SUBSCRIBER || this == SUBSCRIBER_MONTHLY || this == SUBSCRIBER_ANNUAL

    companion object {
        /** Maps an API string (or null) to a known tier, defaulting to [UNKNOWN]. */
        fun fromApiValue(value: String?): CustomerStatus =
            entries.firstOrNull { it.apiValue == value } ?: UNKNOWN
    }
}
