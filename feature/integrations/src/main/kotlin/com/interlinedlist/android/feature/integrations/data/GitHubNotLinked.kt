package com.interlinedlist.android.feature.integrations.data

import com.interlinedlist.android.core.common.result.AppError

/**
 * When GitHub isn't connected, every `/api/github/…` endpoint answers HTTP 400
 * with `{ "error": "GitHub account not linked" }`. `safeApiCall` doesn't map 400
 * to a dedicated type, so it arrives as [AppError.Unknown] carrying that message.
 * This recognises it by message so the UI can show a "connect GitHub" state
 * rather than a generic error.
 */
fun AppError.isGitHubNotLinked(): Boolean =
    message?.contains("not linked", ignoreCase = true) == true ||
        message?.contains("not connected", ignoreCase = true) == true
