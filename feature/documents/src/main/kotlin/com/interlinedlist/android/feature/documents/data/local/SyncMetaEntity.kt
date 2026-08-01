package com.interlinedlist.android.feature.documents.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A tiny key/value row for module-local sync bookkeeping — primarily the delta-sync
 * cursor (`lastSyncAt`). Kept inside this module's own Room DB so the feature stays
 * self-contained and never reaches into `core:datastore`.
 */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String?,
) {
    companion object {
        const val KEY_CURSOR = "documents_last_sync_at"
    }
}
