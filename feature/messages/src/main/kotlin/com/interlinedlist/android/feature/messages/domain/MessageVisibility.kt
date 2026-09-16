package com.interlinedlist.android.feature.messages.domain

/**
 * Who can see a message.
 *
 * A **public** message appears on the feed and on its author's profile; a
 * **private** one is visible only to its author. On the wire this is the single
 * boolean `publiclyVisible`, both on the create request and on the message
 * itself, so this enum exists purely to keep call sites unambiguous (a bare
 * boolean named `visible` reads badly at the ViewModel/UI layer).
 *
 * The composer seeds its choice from the account preference
 * (`defaultPubliclyVisible` on `GET /api/user`) and the user may override it per
 * message.
 */
enum class MessageVisibility(val publiclyVisible: Boolean) {
    PUBLIC(true),
    PRIVATE(false),
    ;

    companion object {
        /** Maps the wire `publiclyVisible` boolean into the enum. */
        fun of(publiclyVisible: Boolean): MessageVisibility =
            if (publiclyVisible) PUBLIC else PRIVATE

        /**
         * The visibility a push (repost) or quote post must be created with.
         *
         * A push/quote amplifies another user's message, so it is always public —
         * there is no private amplification and the composer's per-message choice
         * does not apply. This is the one place that invariant is expressed: the
         * repository posts it instead of the caller's selection whenever a
         * `pushedMessageId` is present, and the composer reads it to lock the
         * visibility control and show its "this will be public" banner.
         */
        val PUSH_OR_QUOTE: MessageVisibility = PUBLIC
    }
}
