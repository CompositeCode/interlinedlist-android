package com.interlinedlist.android.feature.profile.data

import com.interlinedlist.android.feature.profile.data.local.ProfileDao
import com.interlinedlist.android.feature.profile.data.local.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ProfileDao] mirroring the real DAO's query semantics, so repository
 * tests can assert cache writes without Room/Robolectric.
 */
class FakeProfileDao : ProfileDao {
    private val rows = MutableStateFlow<List<ProfileEntity>>(emptyList())

    fun snapshot(): List<ProfileEntity> = rows.value

    override fun observeCurrentUser(): Flow<ProfileEntity?> =
        rows.map { list -> list.firstOrNull { it.isCurrentUser } }

    override fun observeByUsername(username: String): Flow<ProfileEntity?> =
        rows.map { list -> list.firstOrNull { it.username == username } }

    override suspend fun getByUsername(username: String): ProfileEntity? =
        rows.value.firstOrNull { it.username == username }

    override suspend fun upsert(profile: ProfileEntity) {
        rows.value = rows.value.filterNot { it.username == profile.username } + profile
    }

    override suspend fun clearCurrentUserFlag() {
        rows.value = rows.value.map { if (it.isCurrentUser) it.copy(isCurrentUser = false) else it }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}
