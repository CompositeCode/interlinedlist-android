package com.interlinedlist.android.feature.organizations.data

/**
 * Supplies the signed-in user's id. Leaving an organization is expressed by the
 * API as deleting your own membership row
 * (`DELETE /api/organizations/{id}/members/{userId}`), so the repository needs it.
 * Abstracted from `SessionStore` (which is Android-backed) so the repository stays
 * unit-testable on the plain JVM.
 */
fun interface CurrentUserIdProvider {
    fun currentUserId(): String?
}
