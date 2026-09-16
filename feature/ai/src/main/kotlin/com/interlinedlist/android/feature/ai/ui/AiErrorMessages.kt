package com.interlinedlist.android.feature.ai.ui

import com.interlinedlist.android.feature.ai.domain.AiError

/**
 * The one place an [AiError] becomes something a person can read, so all five AI
 * surfaces word the daily-limit and provider cases identically.
 */
fun AiError.toUserMessage(): String = when (this) {
    is AiError.Network -> "No connection. Check your network and try again."
    is AiError.NotAuthenticated -> message ?: "Please sign in again."
    is AiError.NotSubscribed -> message ?: "AI writing assistance requires an active subscription."
    is AiError.Forbidden -> message ?: "Your account can't use this right now."
    is AiError.ProviderUnconfigured -> "AI writing assistance is unavailable right now."
    is AiError.QuotaExceeded -> "Daily AI limit reached. Try again tomorrow."
    is AiError.RateLimited -> retryAfterSeconds
        ?.let { "Too many AI requests. Try again in $it seconds." }
        ?: "Too many AI requests. Please wait a moment and try again."
    is AiError.InvalidInput -> message ?: "That input can't be used. Try rewording it."
    is AiError.InvalidOutput -> "The AI response couldn't be used. Try again."
    is AiError.ProviderFailure -> "The AI service is having trouble right now. Try again shortly."
    is AiError.Unknown -> message ?: "Something went wrong. Please try again."
}
