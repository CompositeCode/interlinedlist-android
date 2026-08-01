package com.interlinedlist.android.feature.messages.domain

/**
 * The reason a message is being reported. Mirrors the web app's `ReportReason`
 * union; [wireValue] is the exact string the report endpoint expects, and [label]
 * is the human-readable option shown in the report dialog.
 */
enum class ReportReason(val wireValue: String, val label: String) {
    SPAM("spam", "Spam"),
    HARASSMENT("harassment", "Harassment or bullying"),
    HATE_SPEECH("hate_speech", "Hate speech"),
    MISINFORMATION("misinformation", "Misinformation"),
    SEXUAL_CONTENT("sexual_content", "Sexual content"),
    VIOLENCE("violence", "Violence or threats"),
    OTHER("other", "Something else"),
}
