package com.interlinedlist.android.feature.notifications.data.remote.dto

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.notifications.domain.NotificationTargetKind
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import kotlinx.serialization.json.Json
import org.junit.Test

class NotificationDtoMapperTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `maps core wire fields into the domain model`() {
        val dto = NotificationDto(
            id = "n1",
            type = "reply",
            actor = NotificationActorDto(id = "u1", username = "amy", displayName = "Amy", avatar = "a.png"),
            subject = "Amy replied to your post",
            body = "\"nice work\"",
            createdAt = "2026-07-18T10:00:00Z",
            read = false,
        )

        val notification = dto.toDomain()

        assertThat(notification.id).isEqualTo("n1")
        assertThat(notification.type).isEqualTo(NotificationType.REPLY)
        assertThat(notification.actor?.username).isEqualTo("amy")
        assertThat(notification.actor?.displayName).isEqualTo("Amy")
        assertThat(notification.actor?.avatarUrl).isEqualTo("a.png")
        assertThat(notification.subject).isEqualTo("Amy replied to your post")
        assertThat(notification.body).isEqualTo("\"nice work\"")
        assertThat(notification.createdAt).isEqualTo("2026-07-18T10:00:00Z")
        assertThat(notification.read).isFalse()
    }

    @Test
    fun `unknown type falls back to OTHER`() {
        val notification = NotificationDto(id = "n2", type = "quantum-entanglement").toDomain()
        assertThat(notification.type).isEqualTo(NotificationType.OTHER)
    }

    @Test
    fun `type matching is substring and case insensitive`() {
        assertThat(NotificationDto(id = "1", type = "NEW_FOLLOWER").toDomain().type)
            .isEqualTo(NotificationType.FOLLOW)
        assertThat(NotificationDto(id = "2", type = "message_reply").toDomain().type)
            .isEqualTo(NotificationType.REPLY)
        assertThat(NotificationDto(id = "3", type = "post-liked").toDomain().type)
            .isEqualTo(NotificationType.LIKE)
    }

    @Test
    fun `reads the actor from the sender alternate key`() {
        val notification = NotificationDto(
            id = "n3",
            sender = NotificationActorDto(id = "u9", username = "ben", name = "Ben"),
        ).toDomain()

        assertThat(notification.actor?.username).isEqualTo("ben")
        // displayName falls back to the `name` alternate.
        assertThat(notification.actor?.displayName).isEqualTo("Ben")
    }

    @Test
    fun `an actor with no identity is dropped`() {
        val notification = NotificationDto(
            id = "n4",
            actor = NotificationActorDto(),
        ).toDomain()
        assertThat(notification.actor).isNull()
    }

    @Test
    fun `read state is derived from isRead alternate`() {
        assertThat(NotificationDto(id = "1", isRead = true).toDomain().read).isTrue()
        assertThat(NotificationDto(id = "2").toDomain().read).isFalse()
    }

    @Test
    fun `read state is derived from a readAt timestamp`() {
        val notification = NotificationDto(id = "5", readAt = "2026-07-18T11:00:00Z").toDomain()
        assertThat(notification.read).isTrue()
    }

    @Test
    fun `subject falls back across title message and text keys`() {
        assertThat(NotificationDto(id = "1", title = "T").toDomain().subject).isEqualTo("T")
        assertThat(NotificationDto(id = "2", message = "M").toDomain().subject).isEqualTo("M")
        assertThat(NotificationDto(id = "3", text = "X").toDomain().subject).isEqualTo("X")
        assertThat(NotificationDto(id = "4").toDomain().subject).isEmpty()
    }

    @Test
    fun `resolves a nested target`() {
        val notification = NotificationDto(
            id = "n6",
            target = NotificationTargetDto(type = "message", id = "m42"),
        ).toDomain()

        assertThat(notification.target?.kind).isEqualTo(NotificationTargetKind.MESSAGE)
        assertThat(notification.target?.id).isEqualTo("m42")
    }

    @Test
    fun `resolves a flat target`() {
        val notification = NotificationDto(
            id = "n7",
            targetType = "user",
            targetId = "u7",
        ).toDomain()

        assertThat(notification.target?.kind).isEqualTo(NotificationTargetKind.USER)
        assertThat(notification.target?.id).isEqualTo("u7")
    }

    @Test
    fun `an unknown target type maps to OTHER`() {
        val notification = NotificationDto(
            id = "n8",
            targetType = "widget",
            targetId = "w1",
        ).toDomain()
        assertThat(notification.target?.kind).isEqualTo(NotificationTargetKind.OTHER)
    }

    @Test
    fun `a target without an id is dropped`() {
        val notification = NotificationDto(id = "n9", targetType = "message").toDomain()
        assertThat(notification.target).isNull()
    }

    @Test
    fun `tolerates unknown keys and a minimal body`() {
        // A payload with extra fields and only an id still decodes and maps cleanly.
        val dto = json.decodeFromString(
            NotificationDto.serializer(),
            """{ "id": "n10", "somethingNew": true, "nested": { "x": 1 } }""",
        )
        val notification = dto.toDomain()
        assertThat(notification.id).isEqualTo("n10")
        assertThat(notification.type).isEqualTo(NotificationType.OTHER)
        assertThat(notification.read).isFalse()
        assertThat(notification.actor).isNull()
        assertThat(notification.target).isNull()
    }
}
