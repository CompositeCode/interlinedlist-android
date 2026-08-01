package com.interlinedlist.android.feature.organizations.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OrganizationDao {

    /** Emits all cached organizations, name-sorted, re-emitting on every change. */
    @Query("SELECT * FROM cached_organization ORDER BY name COLLATE NOCASE ASC")
    fun observeOrganizations(): Flow<List<CachedOrganizationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(orgs: List<CachedOrganizationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(org: CachedOrganizationEntity)

    @Query("DELETE FROM cached_organization WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cached_organization")
    suspend fun clear()

    /**
     * Replaces the whole cache with [orgs] in one transaction — used when a full
     * first page is fetched so removals on the server are reflected locally.
     */
    @Transaction
    suspend fun replaceAll(orgs: List<CachedOrganizationEntity>) {
        clear()
        upsertAll(orgs)
    }
}
