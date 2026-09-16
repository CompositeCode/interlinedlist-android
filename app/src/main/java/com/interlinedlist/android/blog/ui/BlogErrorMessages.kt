package com.interlinedlist.android.blog.ui

import com.interlinedlist.android.core.common.result.AppError

/** Maps a normalised [AppError] to a concise, user-facing message for the blog UI. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Network -> "No connection. Check your network and try again."
    // The subscribe endpoint rate-limits per address and per IP, but answers 200 when
    // it trips — so a real 429 here is the shared limiter, not the mailing list.
    is AppError.RateLimited -> "Too many attempts. Please wait a moment and try again."
    is AppError.Server -> "InterlinedList is having trouble right now. Try again shortly."
    else -> message ?: "Something went wrong. Please try again."
}
