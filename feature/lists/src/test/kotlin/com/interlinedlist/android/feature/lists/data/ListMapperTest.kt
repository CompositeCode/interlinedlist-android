package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.lists.data.remote.dto.ListDto
import org.junit.Test

class ListMapperTest {

    @Test
    fun `summary resolves item count from any of the count fields`() {
        assertThat(ListMapper.summaryFromDto(ListDto(id = "1", itemCount = 7)).itemCount).isEqualTo(7)
        assertThat(ListMapper.summaryFromDto(ListDto(id = "1", rowCount = 3)).itemCount).isEqualTo(3)
        assertThat(ListMapper.summaryFromDto(ListDto(id = "1", count = 9)).itemCount).isEqualTo(9)
        assertThat(ListMapper.summaryFromDto(ListDto(id = "1")).itemCount).isEqualTo(0)
    }

    @Test
    fun `entity round trip preserves summary fields`() {
        val summary = ListMapper.summaryFromDto(
            ListDto(
                id = "42",
                title = "Reading",
                description = "Books",
                itemCount = 5,
                folderId = "f1",
                isPublic = true,
                updatedAt = "2026-01-01",
            ),
        )

        val restored = ListMapper.summaryFromEntity(ListMapper.summaryToEntity(summary))

        assertThat(restored).isEqualTo(summary)
    }
}
