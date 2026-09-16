package com.interlinedlist.android.feature.lists.data

/**
 * Supplies the signed-in user's id so the views UI can tell which saved views
 * belong to the current user — only their own may be renamed or deleted, and
 * somebody else's shared view is forked instead. Abstracted from `SessionStore`
 * (which is Android-backed) so the ViewModel stays unit-testable on the JVM.
 */
fun interface CurrentUserIdProvider {
    fun currentUserId(): String?
}
