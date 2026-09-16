package com.interlinedlist.android.core.network.dto

import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.core.model.User
import com.interlinedlist.android.core.model.ViewingPreference
import kotlinx.serialization.Serializable

/** Wire model for the user object returned by the auth/user endpoints. */
@Serializable
data class UserDto(
    val id: String,
    val username: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val avatar: String? = null,
    val bio: String? = null,
    val emailVerified: Boolean = false,
    val customerStatus: String? = null,
    /** The account's default post visibility preference; public when absent. */
    val defaultPubliclyVisible: Boolean = true,
    /**
     * Raw `viewingPreference` wire value (`all_messages`, `my_messages`,
     * `following_only`, `followers_only`). Kept as a String here so an unknown
     * server value deserialises rather than failing; [ViewingPreference.fromWireOrDefault]
     * resolves it.
     */
    val viewingPreference: String? = null,
)

/** Maps the wire model into the domain [User]. */
fun UserDto.toDomain(): User = User(
    id = id,
    username = username,
    displayName = displayName,
    email = email,
    avatarUrl = avatar,
    bio = bio,
    emailVerified = emailVerified,
    customerStatus = CustomerStatus.fromApiValue(customerStatus),
    defaultPubliclyVisible = defaultPubliclyVisible,
    viewingPreference = ViewingPreference.fromWireOrDefault(viewingPreference),
)
