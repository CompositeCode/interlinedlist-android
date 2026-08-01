package com.interlinedlist.android.feature.integrations.domain

/**
 * The four CSV exports the web app offers under `/api/exports/`. Each value
 * knows its API path segment, a user-facing label, and the base name used for
 * the downloaded file, so the UI and repository can be driven off this one enum.
 */
enum class ExportType(
    val pathSegment: String,
    val label: String,
    val description: String,
    val fileBaseName: String,
) {
    FOLLOWS(
        pathSegment = "follows",
        label = "Follows",
        description = "The people you follow and who follow you.",
        fileBaseName = "follows",
    ),
    LISTS(
        pathSegment = "lists",
        label = "Lists",
        description = "Your lists and their metadata.",
        fileBaseName = "lists",
    ),
    MESSAGES(
        pathSegment = "messages",
        label = "Messages",
        description = "Your sent and received messages.",
        fileBaseName = "messages",
    ),
    LIST_DATA_ROWS(
        pathSegment = "list-data-rows",
        label = "List data rows",
        description = "Every row of data across all your lists.",
        fileBaseName = "list-data-rows",
    ),
}
