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
}
