package com.interlinedlist.android.feature.profile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.domain.ProfileUser

/**
 * Locally cached profile. Keyed by [username] (the stable handle the UI navigates
 * by) so both the current user and viewed users share one table. [isCurrentUser]
 * lets the account screen observe "my" profile without knowing the id up front.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val username: String,
    val id: String,
    val displayName: String?,
    val avatarUrl: String?,
    val bio: String?,
    val customerStatus: String,
    val isCurrentUser: Boolean,
)

fun ProfileEntity.toDomain(): ProfileUser = ProfileUser(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio,
    customerStatus = CustomerStatus.fromApiValue(customerStatus),
    isCurrentUser = isCurrentUser,
)

fun ProfileUser.toEntity(): ProfileEntity = ProfileEntity(
    username = username,
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio,
    customerStatus = customerStatus.apiValue,
    isCurrentUser = isCurrentUser,
)
