package com.interlinedlist.android.feature.messages.domain

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.messages.ui.sampleMessage
import org.junit.Test

/**
 * Covers the visibility domain rules: the wire mapping, the card's "Private"
 * marker predicate, and the push/quote always-public invariant.
 */
class MessageVisibilityTest {

    @Test
    fun `maps the wire boolean both ways`() {
        assertThat(MessageVisibility.of(publiclyVisible = true)).isEqualTo(MessageVisibility.PUBLIC)
        assertThat(MessageVisibility.of(publiclyVisible = false)).isEqualTo(MessageVisibility.PRIVATE)
        assertThat(MessageVisibility.PUBLIC.publiclyVisible).isTrue()
        assertThat(MessageVisibility.PRIVATE.publiclyVisible).isFalse()
    }

    @Test
    fun `push and quote posts are always public`() {
        assertThat(MessageVisibility.PUSH_OR_QUOTE).isEqualTo(MessageVisibility.PUBLIC)
        assertThat(MessageVisibility.PUSH_OR_QUOTE.publiclyVisible).isTrue()
    }

    @Test
    fun `own private message is marked private on the card`() {
        val message = sampleMessage(mine = true, publiclyVisible = false)

        assertThat(message.visibility).isEqualTo(MessageVisibility.PRIVATE)
        assertThat(message.showsPrivateBadge).isTrue()
    }

    @Test
    fun `own public message is not badged`() {
        val message = sampleMessage(mine = true, publiclyVisible = true)

        assertThat(message.visibility).isEqualTo(MessageVisibility.PUBLIC)
        assertThat(message.showsPrivateBadge).isFalse()
    }

    @Test
    fun `another user's message never shows the private marker`() {
        // The feed only ever returns others' public messages; defensive either way.
        val message = sampleMessage(mine = false, publiclyVisible = false)

        assertThat(message.showsPrivateBadge).isFalse()
    }

    @Test
    fun `messages default to public`() {
        assertThat(sampleMessage().publiclyVisible).isTrue()
    }
}
