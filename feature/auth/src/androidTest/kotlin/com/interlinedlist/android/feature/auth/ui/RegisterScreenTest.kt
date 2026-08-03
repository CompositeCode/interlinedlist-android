package com.interlinedlist.android.feature.auth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegisterScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Hosts the stateless [RegisterScreen] with an in-memory state holder. */
    private fun setRegister() {
        composeRule.setContent {
            var state by mutableStateOf(RegisterUiState())
            InterlinedListTheme {
                RegisterScreen(
                    state = state,
                    onDisplayNameChange = { state = state.copy(displayName = it, errorMessage = null) },
                    onUsernameChange = { state = state.copy(username = it, errorMessage = null) },
                    onEmailChange = { state = state.copy(email = it, errorMessage = null) },
                    onPasswordChange = { state = state.copy(password = it, errorMessage = null) },
                    onConfirmPasswordChange = { state = state.copy(confirmPassword = it, errorMessage = null) },
                    onSubmit = {},
                    onBackToLogin = {},
                )
            }
        }
    }

    @Test
    fun submit_disabledUntilAllRequiredFieldsFilledAndPasswordsMatch() {
        setRegister()

        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT).assertIsNotEnabled()

        composeRule.onNodeWithTag(RegisterTestTags.USERNAME).performTextInput("newbie")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL).performTextInput("new@example.com")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD).performTextInput("s3cret!!")

        // Mismatched confirmation keeps submit disabled and shows the hint.
        composeRule.onNodeWithTag(RegisterTestTags.CONFIRM).performTextInput("different")
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT).assertIsNotEnabled()
        composeRule.onNodeWithTag(RegisterTestTags.MISMATCH).assertExists()
    }

    @Test
    fun submit_enabledWhenPasswordsMatch() {
        setRegister()

        composeRule.onNodeWithTag(RegisterTestTags.USERNAME).performTextInput("newbie")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL).performTextInput("new@example.com")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD).performTextInput("s3cret!!")
        composeRule.onNodeWithTag(RegisterTestTags.CONFIRM).performTextInput("s3cret!!")

        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT).assertIsEnabled()
    }
}
