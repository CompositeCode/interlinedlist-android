package com.interlinedlist.android.feature.profile.domain

/**
 * An active login session (sync token) for the current account, shown on the
 * Active Sessions screen. [isCurrent] marks the device the user is signed in on
 * right now, which cannot be revoked from here.
 *
 * Timestamps are kept as the raw ISO-8601 strings the API returns; the UI layer
 * formats [lastUsedAt] into a relative label so the domain stays free of
 * presentation concerns.
 */
data class LoginSession(
    val id: String,
    val deviceLabel: String,
    val createdAt: String?,
    val lastUsedAt: String?,
    val isCurrent: Boolean,
) {
    /** A non-blank label to show for the device, falling back to a generic one. */
    val displayLabel: String
        get() = deviceLabel.takeIf { it.isNotBlank() } ?: "Unknown device"
}
