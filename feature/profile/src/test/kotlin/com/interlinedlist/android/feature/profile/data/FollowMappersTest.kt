package com.interlinedlist.android.feature.profile.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.profile.data.mapper.toFollowCounts
import com.interlinedlist.android.feature.profile.data.mapper.toFollowStatus
import com.interlinedlist.android.feature.profile.data.mapper.toFollowUser
import com.interlinedlist.android.feature.profile.data.mapper.toFollowUserOrNull
import com.interlinedlist.android.feature.profile.data.remote.dto.FollowCountsResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.FollowRequestDto
import com.interlinedlist.android.feature.profile.data.remote.dto.FollowStatusResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileUserDto
import com.interlinedlist.android.feature.profile.domain.FollowStatus
import org.junit.Test

class FollowMappersTest {

    @Test
    fun `status string tokens map to the domain status`() {
        assertThat(FollowStatusResponse(status = "following").toFollowStatus())
            .isEqualTo(FollowStatus.FOLLOWING)
        assertThat(FollowStatusResponse(status = "requested").toFollowStatus())
            .isEqualTo(FollowStatus.REQUESTED)
        assertThat(FollowStatusResponse(status = "pending").toFollowStatus())
            .isEqualTo(FollowStatus.REQUESTED)
        assertThat(FollowStatusResponse(status = "none").toFollowStatus())
            .isEqualTo(FollowStatus.NOT_FOLLOWING)
        assertThat(FollowStatusResponse(status = "not_following").toFollowStatus())
            .isEqualTo(FollowStatus.NOT_FOLLOWING)
    }

    @Test
    fun `status booleans resolve when no explicit string is present`() {
        assertThat(FollowStatusResponse(isFollowing = true).toFollowStatus())
            .isEqualTo(FollowStatus.FOLLOWING)
        assertThat(FollowStatusResponse(pending = true).toFollowStatus())
            .isEqualTo(FollowStatus.REQUESTED)
        assertThat(FollowStatusResponse().toFollowStatus())
            .isEqualTo(FollowStatus.NOT_FOLLOWING)
    }

    @Test
    fun `counts prefer the plain fields but fall back to the suffixed aliases`() {
        assertThat(FollowCountsResponse(followers = 3, following = 4).toFollowCounts().followers).isEqualTo(3)
        val aliased = FollowCountsResponse(followersCount = 9, followingCount = 2).toFollowCounts()
        assertThat(aliased.followers).isEqualTo(9)
        assertThat(aliased.following).isEqualTo(2)
        assertThat(FollowCountsResponse().toFollowCounts().followers).isEqualTo(0)
    }

    @Test
    fun `follow user maps to a lightweight entry with a display label`() {
        val user = ProfileUserDto(id = "1", username = "ada", displayName = "Ada", avatar = "u").toFollowUser()
        assertThat(user.id).isEqualTo("1")
        assertThat(user.username).isEqualTo("ada")
        assertThat(user.avatarUrl).isEqualTo("u")
        assertThat(user.displayLabel).isEqualTo("Ada")
    }

    @Test
    fun `request dto resolves a nested requester`() {
        val dto = FollowRequestDto(user = ProfileUserDto(id = "r1", username = "eve"))
        assertThat(dto.toFollowUserOrNull()?.username).isEqualTo("eve")
    }

    @Test
    fun `request dto resolves an inlined requester`() {
        val dto = FollowRequestDto(id = "r2", username = "frank", displayName = "Frank")
        val user = dto.toFollowUserOrNull()
        assertThat(user?.id).isEqualTo("r2")
        assertThat(user?.displayLabel).isEqualTo("Frank")
    }

    @Test
    fun `request dto with no user is dropped`() {
        assertThat(FollowRequestDto().toFollowUserOrNull()).isNull()
    }
}
