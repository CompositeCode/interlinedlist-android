package com.interlinedlist.android.feature.profile.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.profile.data.mapper.toRequest
import com.interlinedlist.android.feature.profile.data.mapper.toUserSettings
import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileUserDto
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import com.interlinedlist.android.feature.profile.domain.ViewingPreference
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class SettingsMappersTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * The four values the live API accepts, quoted from the server's own allow-list:
     * `viewingPreference must be one of: my_messages, all_messages, followers_only,
     * following_only` (and `GET /api/user` returns `all_messages`).
     */
    @Test
    fun `each viewing preference maps to the wire value the API accepts`() {
        assertThat(ViewingPreference.ALL.wire).isEqualTo("all_messages")
        assertThat(ViewingPreference.MINE.wire).isEqualTo("my_messages")
        assertThat(ViewingPreference.FOLLOWING.wire).isEqualTo("following_only")
        assertThat(ViewingPreference.FOLLOWERS.wire).isEqualTo("followers_only")
    }

    @Test
    fun `each wire value the API accepts round-trips back to its option`() {
        assertThat(ViewingPreference.fromWire("all_messages")).isEqualTo(ViewingPreference.ALL)
        assertThat(ViewingPreference.fromWire("my_messages")).isEqualTo(ViewingPreference.MINE)
        assertThat(ViewingPreference.fromWire("following_only")).isEqualTo(ViewingPreference.FOLLOWING)
        assertThat(ViewingPreference.fromWire("followers_only")).isEqualTo(ViewingPreference.FOLLOWERS)
        // ...and nothing drifts: every constant parses back to itself.
        ViewingPreference.entries.forEach { preference ->
            assertThat(ViewingPreference.fromWire(preference.wire)).isEqualTo(preference)
        }
    }

    @Test
    fun `parsing tolerates casing, separators and the shorthand spellings`() {
        assertThat(ViewingPreference.fromWire("ALL_MESSAGES")).isEqualTo(ViewingPreference.ALL)
        assertThat(ViewingPreference.fromWire("all")).isEqualTo(ViewingPreference.ALL)
        assertThat(ViewingPreference.fromWire("my")).isEqualTo(ViewingPreference.MINE)
        assertThat(ViewingPreference.fromWire("My Messages")).isEqualTo(ViewingPreference.MINE)
        assertThat(ViewingPreference.fromWire("following-only")).isEqualTo(ViewingPreference.FOLLOWING)
        assertThat(ViewingPreference.fromWire("Followers Only")).isEqualTo(ViewingPreference.FOLLOWERS)
    }

    @Test
    fun `an unknown or missing wire value falls back to the default`() {
        assertThat(ViewingPreference.fromWire("nonsense")).isNull()
        assertThat(ViewingPreference.fromWire(null)).isNull()
        assertThat(ViewingPreference.fromWireOrDefault("nonsense")).isEqualTo(ViewingPreference.ALL)
        assertThat(ViewingPreference.fromWireOrDefault(null)).isEqualTo(ViewingPreference.ALL)
    }

    @Test
    fun `wire user maps onto the settings the feed and settings screen read`() {
        val settings = ProfileUserDto(
            id = "u1",
            username = "adron",
            viewingPreference = "following_only",
            showPreviews = false,
            notificationTrayLimit = 10,
        ).toUserSettings()

        assertThat(settings.viewingPreference).isEqualTo(ViewingPreference.FOLLOWING)
        assertThat(settings.showPreviews).isFalse()
        assertThat(settings.notificationTrayLimit).isEqualTo(10)
    }

    @Test
    fun `an update serialises only the fields it set`() {
        val body = json.encodeToString(
            UserSettingsUpdate(showPreviews = true).toRequest(),
        )

        assertThat(body).isEqualTo("""{"showPreviews":true}""")
    }

    @Test
    fun `an update carries every field when they are all set`() {
        val request = UserSettingsUpdate(
            displayName = "Adron",
            bio = "bio",
            avatar = "https://cdn/a.png",
            theme = "dark",
            maxMessageLength = 666,
            defaultPubliclyVisible = true,
            messagesPerPage = 20,
            viewingPreference = ViewingPreference.MINE,
            showPreviews = false,
            showAdvancedPostSettings = true,
            latitude = 45.52,
            longitude = -122.68,
            isPrivateAccount = true,
            githubDefaultRepo = "adron/notes",
            notificationTrayLimit = 25,
        ).toRequest()

        val body = json.parseToJsonElement(json.encodeToString(request))
        assertThat((body as JsonObject).keys).containsExactly(
            "displayName", "bio", "avatar", "theme", "maxMessageLength",
            "defaultPubliclyVisible", "messagesPerPage", "viewingPreference", "showPreviews",
            "showAdvancedPostSettings", "latitude", "longitude", "isPrivateAccount",
            "githubDefaultRepo", "notificationTrayLimit",
        )
        assertThat(request.viewingPreference).isEqualTo("my_messages")
    }
}
