package com.interlinedlist.android.feature.messages.data.remote.dto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageDtoMapperTest {

    private fun dto(
        id: String = "m1",
        authorId: String = "u1",
        isOwn: Boolean = false,
    ) = MessageDto(
        id = id,
        content = "hello",
        author = MessageAuthorDto(id = authorId, username = "adron", displayName = "Adron", avatar = "a.png"),
        createdAt = "2026-07-18T10:00:00Z",
        digCount = 3,
        replyCount = 2,
        dugByCurrentUser = true,
        parentId = null,
        isOwn = isOwn,
    )

    @Test
    fun `maps all wire fields into the domain model`() {
        val message = dto().toDomain(currentUserId = "someone-else")

        assertThat(message.id).isEqualTo("m1")
        assertThat(message.content).isEqualTo("hello")
        assertThat(message.authorUsername).isEqualTo("adron")
        assertThat(message.authorDisplayName).isEqualTo("Adron")
        assertThat(message.authorAvatarUrl).isEqualTo("a.png")
        assertThat(message.digCount).isEqualTo(3)
        assertThat(message.replyCount).isEqualTo(2)
        assertThat(message.dugByMe).isTrue()
    }

    @Test
    fun `flags message as mine when author id matches current user`() {
        val message = dto(authorId = "u1").toDomain(currentUserId = "u1")
        assertThat(message.mine).isTrue()
    }

    @Test
    fun `flags message as mine when the payload sets isOwn`() {
        val message = dto(authorId = "u1", isOwn = true).toDomain(currentUserId = "different")
        assertThat(message.mine).isTrue()
    }

    @Test
    fun `is not mine when author differs and isOwn is false`() {
        val message = dto(authorId = "u1").toDomain(currentUserId = "u2")
        assertThat(message.mine).isFalse()
    }

    @Test
    fun `null author yields empty author fields without crashing`() {
        val message = MessageDto(id = "m2", content = "x", author = null).toDomain(currentUserId = null)
        assertThat(message.authorId).isEmpty()
        assertThat(message.authorUsername).isEmpty()
        assertThat(message.mine).isFalse()
    }

    @Test
    fun `author label falls back to username when display name is blank`() {
        val message = MessageDto(
            id = "m3",
            content = "x",
            author = MessageAuthorDto(id = "u9", username = "handle", displayName = "  "),
        ).toDomain(currentUserId = null)
        assertThat(message.authorLabel).isEqualTo("handle")
    }

    @Test
    fun `maps attached media and scheduled time`() {
        val message = MessageDto(
            id = "m4",
            content = "look",
            imageUrls = listOf("a.png", "b.png"),
            videoUrls = listOf("v.mp4"),
            scheduledAt = "2026-07-19T09:00:00Z",
        ).toDomain(currentUserId = null)

        assertThat(message.imageUrls).containsExactly("a.png", "b.png").inOrder()
        assertThat(message.videoUrls).containsExactly("v.mp4")
        assertThat(message.hasMedia).isTrue()
        assertThat(message.scheduledAt).isEqualTo("2026-07-19T09:00:00Z")
    }

    @Test
    fun `maps link metadata into a preview when it carries content`() {
        val message = MessageDto(
            id = "m5",
            content = "read this",
            linkMetadata = LinkMetadataDto(
                url = "https://example.com",
                title = "Example",
                description = "A page",
                image = "https://example.com/og.png",
                siteName = "Example",
            ),
        ).toDomain(currentUserId = null)

        val preview = message.linkPreview
        assertThat(preview).isNotNull()
        assertThat(preview!!.url).isEqualTo("https://example.com")
        assertThat(preview.title).isEqualTo("Example")
    }

    @Test
    fun `drops an empty link preview`() {
        val message = MessageDto(
            id = "m6",
            content = "no preview",
            linkMetadata = LinkMetadataDto(url = "https://example.com"),
        ).toDomain(currentUserId = null)

        assertThat(message.linkPreview).isNull()
    }

    @Test
    fun `flags an edited message when updatedAt differs from createdAt`() {
        val message = MessageDto(
            id = "m8",
            content = "edited",
            createdAt = "2026-07-18T10:00:00Z",
            updatedAt = "2026-07-18T11:30:00Z",
        ).toDomain(currentUserId = null)

        assertThat(message.isEdited).isTrue()
        assertThat(message.editedAt).isEqualTo("2026-07-18T11:30:00Z")
    }

    @Test
    fun `does not flag as edited when updatedAt equals createdAt`() {
        val message = MessageDto(
            id = "m9",
            content = "fresh",
            createdAt = "2026-07-18T10:00:00Z",
            updatedAt = "2026-07-18T10:00:00Z",
        ).toDomain(currentUserId = null)

        assertThat(message.isEdited).isFalse()
        assertThat(message.editedAt).isNull()
    }

    @Test
    fun `is not edited when updatedAt is absent`() {
        val message = MessageDto(
            id = "m10",
            content = "no updatedAt",
            createdAt = "2026-07-18T10:00:00Z",
        ).toDomain(currentUserId = null)

        assertThat(message.isEdited).isFalse()
    }

    @Test
    fun `drops a link preview without a url`() {
        val message = MessageDto(
            id = "m7",
            content = "x",
            linkMetadata = LinkMetadataDto(title = "no url"),
        ).toDomain(currentUserId = null)

        assertThat(message.linkPreview).isNull()
    }

    @Test
    fun `maps publiclyVisible into the domain`() {
        val message = MessageDto(id = "m11", content = "shh", publiclyVisible = false)
            .toDomain(currentUserId = null)

        assertThat(message.publiclyVisible).isFalse()
    }

    @Test
    fun `defaults to public when the payload omits publiclyVisible`() {
        val message = MessageDto(id = "m12", content = "hi").toDomain(currentUserId = null)

        assertThat(message.publiclyVisible).isTrue()
    }

    // --- push / quote ------------------------------------------------------

    @Test
    fun `maps a push with its embedded original`() {
        val message = MessageDto(
            id = "p1",
            content = "",
            user = MessageAuthorDto(id = "u2", username = "pusher"),
            pushCount = 0,
            pushedMessageId = "orig",
            pushedMessage = PushedMessageDto(
                id = "orig",
                content = "the original post",
                user = MessageAuthorDto(id = "u9", username = "quinn", displayName = "Quinn"),
                createdAt = "2026-07-18T09:00:00Z",
            ),
        ).toDomain(currentUserId = null)

        assertThat(message.isPush).isTrue()
        assertThat(message.pushedMessageId).isEqualTo("orig")
        assertThat(message.pushedMessage?.content).isEqualTo("the original post")
        assertThat(message.pushedMessage?.authorLabel).isEqualTo("Quinn")
        assertThat(message.pushedMessage?.createdAt).isEqualTo("2026-07-18T09:00:00Z")
    }

    @Test
    fun `maps a quote with both the note and the embedded original`() {
        val message = MessageDto(
            id = "q1",
            content = "worth reading",
            pushedMessageId = "orig",
            pushedMessage = PushedMessageDto(
                id = "orig",
                content = "the original post",
                author = MessageAuthorDto(id = "u9", username = "quinn"),
            ),
        ).toDomain(currentUserId = null)

        assertThat(message.isQuote).isTrue()
        assertThat(message.content).isEqualTo("worth reading")
        // The embedded original keys its author as `author` here, `user` above.
        assertThat(message.pushedMessage?.authorUsername).isEqualTo("quinn")
    }

    @Test
    fun `maps the push count`() {
        val message = MessageDto(id = "m13", content = "popular", pushCount = 7)
            .toDomain(currentUserId = null)

        assertThat(message.pushCount).isEqualTo(7)
    }

    @Test
    fun `an ordinary message carries no pushed original`() {
        val message = MessageDto(id = "m14", content = "plain").toDomain(currentUserId = null)

        assertThat(message.isReshare).isFalse()
        assertThat(message.pushedMessage).isNull()
        assertThat(message.pushedMessageId).isNull()
    }

    @Test
    fun `takes the pushed id from the embedded original when the flat field is missing`() {
        val message = MessageDto(
            id = "p2",
            content = "",
            pushedMessage = PushedMessageDto(id = "orig", content = "x"),
        ).toDomain(currentUserId = null)

        assertThat(message.pushedMessageId).isEqualTo("orig")
    }

    @Test
    fun `drops an embedded original the server sent without an id`() {
        val message = MessageDto(
            id = "p3",
            content = "hm",
            pushedMessage = PushedMessageDto(content = "no id"),
        ).toDomain(currentUserId = null)

        assertThat(message.pushedMessage).isNull()
        assertThat(message.isReshare).isFalse()
    }

    @Test
    fun `carries the tags the feed returned, verbatim`() {
        val message = MessageDto(
            id = "m3",
            content = "tagged",
            // Free-form labels: mixed case and inner punctuation are both real.
            tags = listOf("lists", "Lego", "life is short, o brave girl"),
        ).toDomain(currentUserId = null)

        assertThat(message.tags)
            .containsExactly("lists", "Lego", "life is short, o brave girl").inOrder()
        assertThat(message.hasTags).isTrue()
    }

    @Test
    fun `an untagged message has no tags`() {
        val message = MessageDto(id = "m4", content = "plain").toDomain(currentUserId = null)
        assertThat(message.tags).isEmpty()
        assertThat(message.hasTags).isFalse()
    }
}
