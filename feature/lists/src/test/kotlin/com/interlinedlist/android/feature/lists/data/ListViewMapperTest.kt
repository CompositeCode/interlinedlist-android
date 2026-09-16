package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.lists.data.remote.dto.ListViewDto
import com.interlinedlist.android.feature.lists.domain.ListViewConfig
import com.interlinedlist.android.feature.lists.domain.ListViewDensity
import com.interlinedlist.android.feature.lists.domain.ListViewMode
import com.interlinedlist.android.feature.lists.domain.ListViewScope
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test

/** Mapping rules for saved views: scope parsing, config passthrough, ownership. */
class ListViewMapperTest {

    @Test
    fun `an absent config falls back to the server's own defaults`() {
        val view = ListViewMapper.fromDto(
            ListViewDto(id = "v1", name = "Roadmap", scope = "personal"),
            listId = "L1",
        )

        assertThat(view.listId).isEqualTo("L1")
        assertThat(view.config).isEqualTo(ListViewConfig.DEFAULT)
        assertThat(view.config.mode).isEqualTo(ListViewMode.RECORDS)
        assertThat(view.config.density).isEqualTo(ListViewDensity.COMFORTABLE)
        assertThat(view.config.filters).isEmpty()
    }

    @Test
    fun `config keys the client does not model are carried through untouched`() {
        val stored = buildJsonObject {
            put("mode", JsonPrimitive("records"))
            put("density", JsonPrimitive("compact"))
            put("groupBy", JsonPrimitive("status"))
        }

        val view = ListViewMapper.fromDto(
            ListViewDto(id = "v1", name = "Roadmap", scope = "shared", config = stored),
            listId = "L1",
        )

        assertThat(view.config.raw).isEqualTo(stored)
        assertThat(view.config.density).isEqualTo(ListViewDensity.COMPACT)
    }

    @Test
    fun `an unrecognised scope is treated as shared so no destructive action is offered`() {
        val view = ListViewMapper.fromDto(
            ListViewDto(id = "v1", name = "Odd", scope = "team", userId = null),
            listId = "L1",
        )

        assertThat(view.scope).isEqualTo(ListViewScope.SHARED)
        assertThat(view.isOwnedBy("me")).isFalse()
    }

    @Test
    fun `ownership compares user ids when both are known`() {
        val mine = ListViewMapper.fromDto(
            ListViewDto(id = "v1", name = "Mine", scope = "shared", userId = "me"),
            listId = "L1",
        )
        val theirs = mine.copy(userId = "someone-else")

        assertThat(mine.isOwnedBy("me")).isTrue()
        assertThat(theirs.isOwnedBy("me")).isFalse()
    }

    @Test
    fun `a personal view with no ids is the caller's own, since the API returns no others`() {
        val view = ListViewMapper.fromDto(
            ListViewDto(id = "v1", name = "Mine", scope = "personal", userId = null),
            listId = "L1",
        )

        assertThat(view.isOwnedBy(currentUserId = null)).isTrue()
    }
}
