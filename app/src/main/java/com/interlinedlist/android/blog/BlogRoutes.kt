package com.interlinedlist.android.blog

import java.net.URLEncoder

/**
 * The in-app destination for the blog's email list, and the mapping from a tapped
 * email link onto it.
 *
 * Kept free of Android types so the mapping is exercised by plain JVM unit tests, the
 * same way `AuthRoutes.routeForEmailChangeLink` is: the app resolves the launch intent
 * through here rather than relying on implicit `navDeepLink` matching, so a link that
 * would silently fail to route is caught by a test rather than by a user.
 *
 * One screen serves all three jobs (subscribe, confirm, unsubscribe) because they are
 * three states of the same thing — "am I on the blog's mailing list?" — and splitting
 * them would duplicate the double opt-in explanation across screens.
 */
object BlogRoutes {

    /** Path of the destination, without arguments. */
    private const val PATH = "blog/subscription"

    /** Nav argument naming which email-list action the screen should perform. */
    const val ACTION_ARG = "action"

    /** Nav argument carrying the one-time token from the emailed link. */
    const val TOKEN_ARG = "token"

    /**
     * Route pattern. Both arguments are optional: with neither, the screen offers to
     * subscribe; with both, it completes the tapped link.
     */
    const val SUBSCRIPTION = "$PATH?$ACTION_ARG={$ACTION_ARG}&$TOKEN_ARG={$TOKEN_ARG}"

    /** The subscribe entry point, as opened from the Account hub. */
    fun subscription(): String = PATH

    /** The route completing a tapped [action] link carrying [token]. */
    fun subscription(action: BlogSubscriptionAction, token: String): String =
        "$PATH?$ACTION_ARG=${action.name}&$TOKEN_ARG=${encode(token)}"

    /**
     * Maps a tapped confirm/unsubscribe link onto the in-app route, or returns null
     * when the URI is not one of them.
     *
     * A link with an empty token still resolves: the app claims those URLs, so it owes
     * the user an explanation rather than silence (see [BlogSubscriptionLink]).
     */
    fun routeForSubscriptionLink(uri: String?): String? =
        BlogLink.subscriptionLinkFor(uri)?.let { subscription(it.action, it.token) }

    /**
     * Percent-encodes a token for the route's query string. Tokens are opaque, so a
     * space or an `&` in one must not be able to split the route apart — `URLEncoder`
     * emits `+` for a space, which the nav decoder would keep verbatim, so it is
     * rewritten to `%20`.
     */
    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}