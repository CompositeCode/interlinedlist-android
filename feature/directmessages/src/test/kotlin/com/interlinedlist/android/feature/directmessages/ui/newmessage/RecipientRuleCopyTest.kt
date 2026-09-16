package com.interlinedlist.android.feature.directmessages.ui.newmessage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the empty-picker copy against the rule the server actually enforces
 * (https://interlinedlist.com/help/direct-messages → *Who you can message*):
 * mutual follows only.
 */
class RecipientRuleCopyTest {

    @Test
    fun `the explanation states the mutual-follow rule`() {
        assertThat(RecipientRuleCopy.EXPLANATION).contains("follow them")
        assertThat(RecipientRuleCopy.EXPLANATION).contains("follow you back")
    }

    @Test
    fun `the empty state does not read as a failure`() {
        val copy = (RecipientRuleCopy.TITLE + " " + RecipientRuleCopy.EXPLANATION).lowercase()
        listOf("error", "failed", "went wrong", "try again", "couldn't").forEach { word ->
            assertThat(copy).doesNotContain(word)
        }
    }

    @Test
    fun `the call to action points at following people`() {
        assertThat(RecipientRuleCopy.FIND_PEOPLE.lowercase()).contains("follow")
    }
}
