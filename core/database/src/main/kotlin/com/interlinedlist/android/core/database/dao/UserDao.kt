package com.interlinedlist.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.interlinedlist.android.core.database.entity.CachedUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    /** Emits the cached user (or null) and re-emits on every change. */
    @Query("SELECT * FROM cached_user WHERE id = :id")
    fun observeUser(id: String): Flow<CachedUserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: CachedUserEntity)

    @Query("DELETE FROM cached_user")
    suspend fun clear()
}
