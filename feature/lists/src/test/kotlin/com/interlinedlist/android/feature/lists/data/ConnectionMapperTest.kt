package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.lists.data.remote.dto.ConnectionDto
import com.interlinedlist.android.feature.lists.data.remote.dto.RefreshResultDto
import org.junit.Test

/** Connections and refresh results map defensively, filling missing titles from ids. */
class ConnectionMapperTest {

    @Test
    fun `uses supplied titles when present`() {
        val connection = ConnectionMapper.connectionFromDto(
            ConnectionDto(
                id = "c1",
                fromListId = "l1",
                toListId = "l2",
                label = "blocks",
                fromListTitle = "Backlog",
                toListTitle = "Roadmap",
            ),
        )

        assertThat(connection.fromListTitle).isEqualTo("Backlog")
        assertThat(connection.toListTitle).isEqualTo("Roadmap")
        assertThat(connection.label).isEqualTo("blocks")
    }

    @Test
    fun `falls back to ids for titles and nulls out a blank label`() {
        val connection = ConnectionMapper.connectionFromDto(
            ConnectionDto(id = "c2", fromListId = "l1", toListId = "l2", label = "  "),
        )

        assertThat(connection.fromListTitle).isEqualTo("l1")
        assertThat(connection.toListTitle).isEqualTo("l2")
        assertThat(connection.label).isNull()
    }

    @Test
    fun `refresh summary reports only the non-zero counts`() {
        val result = ConnectionMapper.refreshFromDto(
            RefreshResultDto(added = 3, updated = 0, removed = 1),
        )

        assertThat(result.summary).isEqualTo("3 added, 1 removed")
    }

    @Test
    fun `refresh summary is null when nothing changed`() {
        val result = ConnectionMapper.refreshFromDto(RefreshResultDto(added = 0, updated = 0, removed = 0))

        assertThat(result.summary).isNull()
    }
}
