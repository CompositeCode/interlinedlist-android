package com.interlinedlist.android.feature.messages.navigation

import java.net.URLEncoder

/**
 * Route keys this module owns, kept next to the screens that consume them so the
 * app can host them without hand-building route strings.
 */
object MessagesDestinations {

    /** Nav argument carrying the tag a feed is filtered to. */
    const val ARG_TAG = "tag"

    /** Feed filtered to one tag; build a concrete route with [tagFeedRoute]. */
    const val TAG_FEED = "messages/tag/{$ARG_TAG}"

    /**
     * Route for the feed of [tag] — the single entry point for opening a tag feed,
     * whether from a tag on a message card, a trending list, or a deep link.
     *
     * The tag is percent-encoded into the path segment (with `%20` rather than
     * `+`, which Navigation would hand back literally), because a tag is free-form
     * and may contain spaces, commas and even slashes.
     */
    fun tagFeedRoute(tag: String): String = "messages/tag/${encode(tag)}"

    /**
     * Maps a tapped tag URL onto an in-app route, or null when the URI is not one.
     *
     * The app resolves the launch intent through here — the same way a tapped
     * notification goes through `NotificationLaunch` and an email-change link goes
     * through `AuthRoutes.routeForEmailChangeLink` — rather than relying on
     * implicit `navDeepLink` matching, so the behaviour is covered by plain unit
     * tests instead of only firing on a real device.
     */
    fun routeForTagLink(uri: String?): String? = TagFeedLink.parse(uri)?.let(::tagFeedRoute)

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
