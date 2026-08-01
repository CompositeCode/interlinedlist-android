package com.interlinedlist.android.feature.profile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    /** Emits the current signed-in user's cached profile (or null before first load). */
    @Query("SELECT * FROM profile WHERE isCurrentUser = 1 LIMIT 1")
    fun observeCurrentUser(): Flow<ProfileEntity?>

    /** Emits a cached profile by username (or null) and re-emits on every change. */
    @Query("SELECT * FROM profile WHERE username = :username LIMIT 1")
    fun observeByUsername(username: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    /** Clears the current-user flag from any prior row before a fresh sign-in caches a new one. */
    @Query("UPDATE profile SET isCurrentUser = 0 WHERE isCurrentUser = 1")
    suspend fun clearCurrentUserFlag()

    @Query("DELETE FROM profile")
    suspend fun clear()
}
