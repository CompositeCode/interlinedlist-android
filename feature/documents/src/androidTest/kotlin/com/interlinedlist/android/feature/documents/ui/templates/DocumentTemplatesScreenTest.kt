package com.interlinedlist.android.feature.documents.ui.templates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.documents.domain.DocumentTemplate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentTemplatesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: DocumentTemplatesUiState,
        onSeedDefaults: () -> Unit = {},
        onUseTemplate: (DocumentTemplate) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                DocumentTemplatesScreen(
                    state = state,
                    onSeedDefaults = onSeedDefaults,
                    onUseTemplate = onUseTemplate,
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun emptyState_offersSeedDefaults_andInvokesCallback() {
        var seeded = false
        setContent(
            state = DocumentTemplatesUiState(isLoading = false, templates = emptyList()),
            onSeedDefaults = { seeded = true },
        )

        composeRule.onNodeWithTag(DocumentTemplatesTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentTemplatesTestTags.SEED_BUTTON).assertIsEnabled().performClick()
        assert(seeded)
    }

    @Test
    fun templateRow_isRendered_andUsable() {
        var usedId: String? = null
        setContent(
            state = DocumentTemplatesUiState(
                isLoading = false,
                templates = listOf(DocumentTemplate("t1", "Recipe", "Ingredients")),
            ),
            onUseTemplate = { usedId = it.id },
        )

        composeRule.onNodeWithTag(DocumentTemplatesTestTags.row("t1")).assertIsDisplayed().performClick()
        assert(usedId == "t1")
    }

    @Test
    fun subscriptionGate_isShown_whenRequired() {
        setContent(
            state = DocumentTemplatesUiState(
                isLoading = false,
                subscriptionRequired = true,
                errorMessage = "Templates require an active subscription.",
            ),
        )
        composeRule.onNodeWithTag(DocumentTemplatesTestTags.SUBSCRIPTION_GATE).assertIsDisplayed()
    }
}
