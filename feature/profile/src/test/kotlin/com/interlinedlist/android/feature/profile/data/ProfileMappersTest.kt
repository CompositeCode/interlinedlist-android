package com.interlinedlist.android.feature.profile.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.data.mapper.toProfileUser
import com.interlinedlist.android.feature.profile.data.mapper.toSearchResult
import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileUserDto
import org.junit.Test

class ProfileMappersTest {

    @Test
    fun `profile user maps all fields and resolves customer status`() {
        val dto = ProfileUserDto(
            id = "u1",
            username = "adron",
            displayName = "Adron Hall",
            avatarUrl = "https://cdn/av.png",
            bio = "Hello",
            customerStatus = "subscriber",
        )

        val user = dto.toProfileUser(isCurrentUser = true)

        assertThat(user.id).isEqualTo("u1")
        assertThat(user.username).isEqualTo("adron")
        assertThat(user.displayName).isEqualTo("Adron Hall")
        assertThat(user.avatarUrl).isEqualTo("https://cdn/av.png")
        assertThat(user.bio).isEqualTo("Hello")
        assertThat(user.customerStatus).isEqualTo(CustomerStatus.SUBSCRIBER)
        assertThat(user.isSubscriber).isTrue()
        assertThat(user.isCurrentUser).isTrue()
    }

    @Test
    fun `avatar falls back to the legacy avatar field when avatarUrl is absent`() {
        val dto = ProfileUserDto(id = "u1", username = "x", avatar = "https://cdn/legacy.png")
        assertThat(dto.toProfileUser(isCurrentUser = false).avatarUrl).isEqualTo("https://cdn/legacy.png")
    }

    @Test
    fun `display label prefers display name and falls back to at-username`() {
        val named = ProfileUserDto(id = "1", username = "adron", displayName = "Adron").toProfileUser(false)
        assertThat(named.displayLabel).isEqualTo("Adron")

        val unnamed = ProfileUserDto(id = "2", username = "adron", displayName = null).toProfileUser(false)
        assertThat(unnamed.displayLabel).isEqualTo("@adron")

        val blank = ProfileUserDto(id = "3", username = "adron", displayName = "  ").toProfileUser(false)
        assertThat(blank.displayLabel).isEqualTo("@adron")
    }

    @Test
    fun `unknown customer status is not treated as a subscriber`() {
        val user = ProfileUserDto(id = "1", username = "x", customerStatus = null).toProfileUser(false)
        assertThat(user.customerStatus).isEqualTo(CustomerStatus.UNKNOWN)
        assertThat(user.isSubscriber).isFalse()
    }

    @Test
    fun `search result maps to a lightweight entry`() {
        val dto = ProfileUserDto(id = "1", username = "ada", displayName = "Ada", avatar = "u")
        val result = dto.toSearchResult()
        assertThat(result.id).isEqualTo("1")
        assertThat(result.username).isEqualTo("ada")
        assertThat(result.displayLabel).isEqualTo("Ada")
        assertThat(result.avatarUrl).isEqualTo("u")
    }

    @Test
    fun `response resolves the wrapped user object`() {
        val response = ProfileResponse(user = ProfileUserDto(id = "u1", username = "adron"))
        assertThat(response.userOrSelf?.id).isEqualTo("u1")
    }

    @Test
    fun `response tolerates an inlined top-level user`() {
        val response = ProfileResponse(id = "u2", username = "ada", displayName = "Ada")
        val user = response.userOrSelf
        assertThat(user?.id).isEqualTo("u2")
        assertThat(user?.username).isEqualTo("ada")
        assertThat(user?.displayName).isEqualTo("Ada")
    }

    @Test
    fun `response is null when there is no user payload at all`() {
        assertThat(ProfileResponse().userOrSelf).isNull()
    }
}
