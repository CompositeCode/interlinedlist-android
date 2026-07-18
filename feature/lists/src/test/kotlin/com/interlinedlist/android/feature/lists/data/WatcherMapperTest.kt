package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.lists.data.remote.dto.WatcherDto
import com.interlinedlist.android.feature.lists.data.remote.dto.WatcherUserDto
import com.interlinedlist.android.feature.lists.domain.WatcherRole
import org.junit.Test

/**
 * Watcher rows arrive flattened or nested and with varied role strings; the mapper
 * tolerates both shapes and normalises the role so a watcher is never dropped.
 */
class WatcherMapperTest {

    @Test
    fun `maps a flattened watcher row`() {
        val watcher = WatcherMapper.watcherFromDto(
            WatcherDto(userId = "u1", username = "ada", displayName = "Ada", role = "editor"),
        )

        assertThat(watcher).isNotNull()
        assertThat(watcher!!.userId).isEqualTo("u1")
        assertThat(watcher.username).isEqualTo("ada")
        assertThat(watcher.label).isEqualTo("Ada")
        assertThat(watcher.role).isEqualTo(WatcherRole.EDITOR)
    }

    @Test
    fun `maps a nested user object and defaults an unknown role to viewer`() {
        val watcher = WatcherMapper.watcherFromDto(
            WatcherDto(
                role = "wizard",
                user = WatcherUserDto(id = "u2", username = "grace", displayName = null),
            ),
        )

        assertThat(watcher!!.userId).isEqualTo("u2")
        assertThat(watcher.username).isEqualTo("grace")
        // No display name → the row labels by username.
        assertThat(watcher.label).isEqualTo("grace")
        assertThat(watcher.role).isEqualTo(WatcherRole.VIEWER)
    }

    @Test
    fun `returns null when no user id can be resolved`() {
        assertThat(WatcherMapper.watcherFromDto(WatcherDto(role = "viewer"))).isNull()
    }

    @Test
    fun `maps a candidate user`() {
        val candidate = WatcherMapper.candidateFromDto(
            WatcherUserDto(id = "u3", username = "linus", displayName = "Linus"),
        )

        assertThat(candidate.userId).isEqualTo("u3")
        assertThat(candidate.label).isEqualTo("Linus")
    }
}
