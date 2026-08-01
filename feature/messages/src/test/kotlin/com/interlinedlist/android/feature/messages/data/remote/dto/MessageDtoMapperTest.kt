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
    fun `drops a link preview without a url`() {
        val message = MessageDto(
            id = "m7",
            content = "x",
            linkMetadata = LinkMetadataDto(title = "no url"),
        ).toDomain(currentUserId = null)

        assertThat(message.linkPreview).isNull()
    }
}
