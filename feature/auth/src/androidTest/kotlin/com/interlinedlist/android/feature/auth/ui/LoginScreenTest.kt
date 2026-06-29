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
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Hosts the stateless [LoginScreen] with a tiny in-memory state holder. */
    private fun setLogin(onSubmit: () -> Unit = {}) {
        composeRule.setContent {
            var state by mutableStateOf(LoginUiState())
            InterlinedListTheme {
                LoginScreen(
                    state = state,
                    onEmailChange = { state = state.copy(email = it, errorMessage = null) },
                    onPasswordChange = { state = state.copy(password = it, errorMessage = null) },
                    onSubmit = onSubmit,
                )
            }
        }
    }

    @Test
    fun submitButton_disabledUntilBothFieldsFilled() {
        setLogin()

        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsNotEnabled()

        composeRule.onNodeWithTag(LoginTestTags.EMAIL).performTextInput("you@example.com")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsNotEnabled()

        composeRule.onNodeWithTag(LoginTestTags.PASSWORD).performTextInput("secret")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsEnabled()
    }

    @Test
    fun submit_invokesCallback_whenEnabled() {
        var submitted = false
        setLogin(onSubmit = { submitted = true })

        composeRule.onNodeWithTag(LoginTestTags.EMAIL).performTextInput("you@example.com")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD).performTextInput("secret")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).performClick()

        assert(submitted)
    }
}
