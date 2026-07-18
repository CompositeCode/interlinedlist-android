package com.interlinedlist.android.feature.notifications.domain

/**
 * A single in-app notification, normalised from the API wire model into a
 * platform-independent domain type.
 *
 * The OpenAPI extract does not pin the notification schema, so this captures the
 * fields the web app surfaces: a [type] (what happened), the [actor] who caused it
 * (with an avatar), a short [subject]/body describing it, a creation timestamp, and
 * whether the recipient has read it yet. An optional [target] lets the app deep-link
 * to whatever the notification is about (a message/user/list); it is best-effort and
 * often null.
 */
data class Notification(
    val id: String,
    /** Coarse category driving the icon/label; see [NotificationType]. */
    val type: NotificationType,
    /** The user who triggered the notification, when the API supplies one. */
    val actor: NotificationActor?,
    /** Short human-readable summary line (already rendered server-side, if present). */
    val subject: String,
    /** Optional secondary line (e.g. a message excerpt). */
    val body: String?,
    /** ISO-8601 creation instant, used to derive a relative timestamp for display. */
    val createdAt: String?,
    /** Whether the recipient has read this notification (drives unread styling). */
    val read: Boolean,
    /** Optional deep-link target this notification refers to; null when self-contained. */
    val target: NotificationTarget?,
) {
    /** Best available display label for the actor, or null when there is no actor. */
    val actorLabel: String?
        get() = actor?.let { it.displayName?.takeIf(String::isNotBlank) ?: it.username.takeIf(String::isNotBlank) }
}

/** The user who caused a notification (liked, replied, followed, …). */
data class NotificationActor(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
)

/**
 * A deep-link target a notification points at. [kind] categorises what is being
 * referenced so the app can route to the right screen; [id] identifies the entity.
 * Kept deliberately small — the app decides how (or whether) to navigate.
 */
data class NotificationTarget(
    val kind: NotificationTargetKind,
    val id: String,
)

/** What a notification's target refers to. Unknown kinds fall back to [OTHER]. */
enum class NotificationTargetKind {
    MESSAGE,
    USER,
    LIST,
    OTHER,
}

/**
 * Coarse notification category. The API sends free-form type strings; anything we
 * don't recognise maps to [OTHER] so the list still renders sensibly (defensive by
 * design — see [NotificationType.fromWire]).
 */
enum class NotificationType {
    /** Someone liked/dug the recipient's content. */
    LIKE,

    /** Someone replied to or commented on the recipient's content. */
    REPLY,

    /** Someone mentioned the recipient. */
    MENTION,

    /** Someone started following the recipient. */
    FOLLOW,

    /** Someone shared a list (or added the recipient to one). */
    LIST_SHARE,

    /** A direct/private message notification. */
    MESSAGE,

    /** System/account notification (billing, security, …). */
    SYSTEM,

    /** Unrecognised type; still displayed generically. */
    OTHER;

    companion object {
        /**
         * Maps a free-form wire `type` string to a [NotificationType]. Matching is
         * case-insensitive and substring-based so minor server naming variations
         * ("message_reply", "new-follower", …) still resolve; unknowns become [OTHER].
         */
        fun fromWire(raw: String?): NotificationType {
            val value = raw?.trim()?.lowercase().orEmpty()
            return when {
                value.isEmpty() -> OTHER
                value.contains("like") || value.contains("dig") || value.contains("favorite") -> LIKE
                value.contains("reply") || value.contains("comment") -> REPLY
                value.contains("mention") || value.contains("tag") -> MENTION
                value.contains("follow") -> FOLLOW
                value.contains("list") || value.contains("share") -> LIST_SHARE
                value.contains("message") || value.contains("dm") -> MESSAGE
                value.contains("system") || value.contains("account") ||
                    value.contains("billing") || value.contains("security") -> SYSTEM
                else -> OTHER
            }
        }
    }
}
