package com.interlinedlist.android.feature.billing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpsellScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun upsell_rendersSubscribeAndManage_andInvokesCallbacks() {
        var subscribed = false
        var managed = false
        composeRule.setContent {
            InterlinedListTheme {
                UpsellScreen(
                    state = UpsellUiState(),
                    onSubscribe = { subscribed = true },
                    onManage = { managed = true },
                    onDismissError = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(UpsellTestTags.SUBSCRIBE).assertIsDisplayed()
        composeRule.onNodeWithTag(UpsellTestTags.MANAGE).assertIsDisplayed()

        composeRule.onNodeWithTag(UpsellTestTags.SUBSCRIBE).performClick()
        composeRule.onNodeWithTag(UpsellTestTags.MANAGE).performClick()

        assert(subscribed)
        assert(managed)
    }
}
