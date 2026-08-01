package com.interlinedlist.android.feature.notifications.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPreferencesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun preference(
        key: String,
        label: String,
        description: String = "",
        channels: Map<NotificationChannel, Boolean>,
    ) = NotificationPreference(key = key, label = label, description = description, channels = channels)

    /** Hosts the stateless screen, applying toggles to a tiny in-memory state holder. */
    private fun setScreen(
        initial: NotificationPreferencesUiState,
        onBack: () -> Unit = {},
        onToggle: (String, NotificationChannel, Boolean) -> Unit = { _, _, _ -> },
    ) {
        composeRule.setContent {
            var state by mutableStateOf(initial)
            InterlinedListTheme {
                NotificationPreferencesScreen(
                    state = state,
                    onBack = onBack,
                    onRetry = {},
                    onToggle = { key, channel, enabled ->
                        onToggle(key, channel, enabled)
                        // Reflect the toggle so the Switch flips on-screen, like the ViewModel would.
                        state = state.copy(
                            preferences = state.preferences.map {
                                if (it.key == key) it.withChannel(channel, enabled) else it
                            },
                        )
                    },
                )
            }
        }
    }

    private val sampleState = NotificationPreferencesUiState(
        preferences = listOf(
            preference(
                key = "dig",
                label = "Digs on your messages",
                description = "When someone digs your message.",
                channels = mapOf(
                    NotificationChannel.PUSH to true,
                    NotificationChannel.IN_APP to false,
                ),
            ),
            preference(
                key = "follow",
                label = "New followers",
                description = "When someone follows you.",
                channels = mapOf(
                    NotificationChannel.PUSH to true,
                    NotificationChannel.EMAIL to true,
                ),
            ),
        ),
    )

    @Test
    fun events_areRendered_withLabelsAndDescriptions() {
        setScreen(sampleState)
        composeRule.onNodeWithTag(NotificationPreferencesTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Digs on your messages").assertIsDisplayed()
        composeRule.onNodeWithText("When someone digs your message.").assertIsDisplayed()
        composeRule.onNodeWithText("New followers").assertIsDisplayed()
    }

    @Test
    fun onlyAvailableChannels_haveToggles() {
        setScreen(sampleState)
        // "dig" offers push + in-app, but NOT email.
        composeRule.onNodeWithTag(
            NotificationPreferencesTags.toggle("dig", NotificationChannel.PUSH),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            NotificationPreferencesTags.toggle("dig", NotificationChannel.IN_APP),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            NotificationPreferencesTags.toggle("dig", NotificationChannel.EMAIL),
        ).assertDoesNotExist()

        // "follow" offers push + email, but NOT in-app.
        composeRule.onNodeWithTag(
            NotificationPreferencesTags.toggle("follow", NotificationChannel.EMAIL),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            NotificationPreferencesTags.toggle("follow", NotificationChannel.IN_APP),
        ).assertDoesNotExist()
    }

    @Test
    fun togglingAChannel_invokesCallback_andFlipsTheSwitch() {
        var toggled: Triple<String, NotificationChannel, Boolean>? = null
        setScreen(sampleState, onToggle = { k, c, e -> toggled = Triple(k, c, e) })

        val digInApp = NotificationPreferencesTags.toggle("dig", NotificationChannel.IN_APP)
        composeRule.onNodeWithTag(digInApp).assertIsOff()
        composeRule.onNodeWithTag(digInApp).performClick()

        assert(toggled == Triple("dig", NotificationChannel.IN_APP, true))
        composeRule.onNodeWithTag(digInApp).assertIsOn()
    }

    @Test
    fun back_invokesOnBack() {
        var backed = false
        setScreen(sampleState, onBack = { backed = true })
        composeRule.onNodeWithTag(NotificationPreferencesTags.BACK).performClick()
        assert(backed)
    }

    @Test
    fun emptyState_isShown_whenThereAreNoPreferences() {
        setScreen(NotificationPreferencesUiState(preferences = emptyList()))
        composeRule.onNodeWithTag(NotificationPreferencesTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun errorState_isShown_whenLoadFails() {
        setScreen(NotificationPreferencesUiState(errorMessage = "No connection."))
        composeRule.onNodeWithTag(NotificationPreferencesTags.ERROR).assertIsDisplayed()
    }
}
