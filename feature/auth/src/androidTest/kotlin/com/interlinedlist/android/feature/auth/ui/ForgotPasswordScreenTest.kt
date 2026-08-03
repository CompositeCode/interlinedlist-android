package com.interlinedlist.android.feature.auth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForgotPasswordScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Hosts the stateless [ForgotPasswordScreen]. On submit it flips a local
     * `emailSent` flag, standing in for the ViewModel's success transition so
     * the confirmation swap can be asserted without a repository.
     */
    private fun setForgot() {
        composeRule.setContent {
            var state by mutableStateOf(ForgotPasswordUiState())
            InterlinedListTheme {
                ForgotPasswordScreen(
                    state = state,
                    onEmailChange = { state = state.copy(email = it, errorMessage = null) },
                    onSubmit = { state = state.copy(emailSent = true) },
                    onBackToLogin = {},
                )
            }
        }
    }

    @Test
    fun submit_disabledUntilEmailEntered() {
        setForgot()

        composeRule.onNodeWithTag(ForgotPasswordTestTags.SUBMIT).assertIsNotEnabled()
        composeRule.onNodeWithTag(ForgotPasswordTestTags.EMAIL).performTextInput("me@example.com")
        composeRule.onNodeWithTag(ForgotPasswordTestTags.SUBMIT).assertIsEnabled()
    }

    @Test
    fun submit_showsCheckYourEmailConfirmation() {
        setForgot()

        composeRule.onNodeWithTag(ForgotPasswordTestTags.EMAIL).performTextInput("me@example.com")
        composeRule.onNodeWithTag(ForgotPasswordTestTags.SUBMIT).performClick()

        // The form is replaced by the confirmation; the email field goes away.
        composeRule.onNodeWithTag(ForgotPasswordTestTags.CONFIRMATION).assertExists()
        composeRule.onNodeWithTag(ForgotPasswordTestTags.EMAIL).assertDoesNotExist()
    }
}
