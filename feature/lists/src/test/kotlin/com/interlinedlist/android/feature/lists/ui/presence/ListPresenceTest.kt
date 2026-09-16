package com.interlinedlist.android.feature.lists.ui.presence

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.lists.domain.ListPresence
import org.junit.Test

/**
 * How a present person is labelled. The presence payload's field names were not
 * observable live (the array was empty on a single-user list), so every part of
 * the name is optional and the fallbacks have to hold.
 */
class ListPresenceTest {

    @Test
    fun `prefers a display name`() {
        val person = ListPresence("u2", displayName = "Casey Jones", username = "casey")
        assertThat(person.label).isEqualTo("Casey Jones")
        assertThat(person.initial).isEqualTo("C")
    }

    @Test
    fun `falls back to the username then the id`() {
        assertThat(ListPresence("u2", displayName = "  ", username = "casey").label).isEqualTo("casey")
        assertThat(ListPresence("u2").label).isEqualTo("u2")
        assertThat(ListPresence("u2").initial).isEqualTo("U")
    }

    @Test
    fun `describes who is here for a screen reader`() {
        assertThat(describe(emptyList())).isEmpty()
        assertThat(describe(listOf(ListPresence("u2", displayName = "Casey"))))
            .isEqualTo("Casey is also here")
        assertThat(
            describe(
                listOf(
                    ListPresence("u2", displayName = "Casey"),
                    ListPresence("u3", username = "robin"),
                ),
            ),
        ).isEqualTo("Casey, robin are also here")
    }
}
