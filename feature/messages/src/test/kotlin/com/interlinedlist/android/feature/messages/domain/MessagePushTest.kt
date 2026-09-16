package com.interlinedlist.android.feature.messages.domain

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.messages.ui.sampleMessage
import com.interlinedlist.android.feature.messages.ui.samplePushedMessage
import org.junit.Test

/**
 * The push (repost) / quote rules: how a re-share is recognised in the feed, and
 * which messages may be re-shared at all. Both actions post a `pushedMessageId`,
 * so one predicate governs them.
 */
class MessagePushTest {

    @Test
    fun `a push is a re-share with no comment of its own`() {
        val push = sampleMessage(
            content = "",
            pushedMessageId = "orig",
            pushedMessage = samplePushedMessage(),
        )

        assertThat(push.isReshare).isTrue()
        assertThat(push.isPush).isTrue()
        assertThat(push.isQuote).isFalse()
        // The embedded original arrives with the feed payload — no second fetch.
        assertThat(push.pushedMessage?.content).isEqualTo("the original post")
    }

    @Test
    fun `a quote is a re-share the author added a note to`() {
        val quote = sampleMessage(
            content = "worth reading",
            pushedMessageId = "orig",
            pushedMessage = samplePushedMessage(),
        )

        assertThat(quote.isReshare).isTrue()
        assertThat(quote.isQuote).isTrue()
        assertThat(quote.isPush).isFalse()
    }

    @Test
    fun `an ordinary message is neither a push nor a quote`() {
        val message = sampleMessage(content = "just a post")

        assertThat(message.isReshare).isFalse()
        assertThat(message.isPush).isFalse()
        assertThat(message.isQuote).isFalse()
        assertThat(message.pushCount).isEqualTo(0)
    }

    @Test
    fun `a re-share is still recognised when the server omits the embedded original`() {
        val push = sampleMessage(content = "", pushedMessageId = "orig", pushedMessage = null)

        assertThat(push.isReshare).isTrue()
        assertThat(push.isPush).isTrue()
    }

    @Test
    fun `someone else's public message can be pushed`() {
        assertThat(sampleMessage(mine = false, publiclyVisible = true).canBePushed).isTrue()
    }

    @Test
    fun `your own message cannot be pushed`() {
        // /help/messages: push and quote re-share "someone else's" message.
        assertThat(sampleMessage(mine = true, publiclyVisible = true).canBePushed).isFalse()
    }

    @Test
    fun `a private message cannot be pushed`() {
        // /help/api/messages: pushedMessageId reposts "this public message ID".
        assertThat(sampleMessage(mine = false, publiclyVisible = false).canBePushed).isFalse()
    }

    @Test
    fun `a push cannot itself be pushed`() {
        val push = sampleMessage(
            mine = false,
            content = "",
            pushedMessageId = "orig",
            pushedMessage = samplePushedMessage(),
        )

        assertThat(push.canBePushed).isFalse()
    }

    @Test
    fun `a quote cannot be pushed either`() {
        val quote = sampleMessage(
            mine = false,
            content = "worth reading",
            pushedMessageId = "orig",
            pushedMessage = samplePushedMessage(),
        )

        assertThat(quote.canBePushed).isFalse()
    }

    @Test
    fun `narrowing a message to an embedded original keeps what the inset card shows`() {
        val original = sampleMessage(id = "orig", content = "hello", authorUsername = "quinn")

        val embedded = original.asPushedOriginal()

        assertThat(embedded.id).isEqualTo("orig")
        assertThat(embedded.content).isEqualTo("hello")
        assertThat(embedded.authorUsername).isEqualTo("quinn")
        assertThat(embedded.authorLabel).isEqualTo("Adron")
    }
}
