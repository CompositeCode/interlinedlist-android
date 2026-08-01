package com.interlinedlist.android.feature.directmessages.data

/**
 * Supplies the signed-in user's id so the repository can distinguish messages
 * the user sent from ones they received. Abstracted from `SessionStore` (which
 * is Android-backed) so the repository stays unit-testable on the plain JVM.
 */
fun interface CurrentUserIdProvider {
    fun currentUserId(): String?
}
