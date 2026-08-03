package com.interlinedlist.android.feature.lists.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ListSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the edit-list metadata form seeds from the current summary, renders its
 * fields, and hands the edited values (title, description, visibility) back on save.
 */
@RunWith(AndroidJUnit4::class)
class ListMetadataEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val summary = ListSummary("L1", "Reading", "Books", 3, null, isPublic = false, updatedAt = null)

    @Test
    fun rendersSeededFieldsAndActions() {
        composeRule.setContent {
            InterlinedListTheme {
                ListMetadataEditor(summary = summary, isSaving = false, onSave = { _, _, _ -> }, onCancel = {})
            }
        }

        composeRule.onNodeWithTag(ListMetadataEditorTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(ListMetadataEditorTestTags.DESCRIPTION).assertIsDisplayed()
        composeRule.onNodeWithTag(ListMetadataEditorTestTags.VISIBILITY).assertIsDisplayed()
        composeRule.onNodeWithTag(ListMetadataEditorTestTags.SAVE).assertIsDisplayed()
    }

    @Test
    fun editsAndSubmitsTheNewValues() {
        var saved: Triple<String, String?, Boolean>? = null
        composeRule.setContent {
            InterlinedListTheme {
                ListMetadataEditor(
                    summary = summary,
                    isSaving = false,
                    onSave = { title, description, isPublic -> saved = Triple(title, description, isPublic) },
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag(ListMetadataEditorTestTags.TITLE).performTextClearance()
        composeRule.onNodeWithTag(ListMetadataEditorTestTags.TITLE).performTextInput("Reading v2")
        composeRule.onNodeWithTag(ListMetadataEditorTestTags.VISIBILITY).performClick()
        composeRule.onNodeWithTag(ListMetadataEditorTestTags.SAVE).performClick()

        assert(saved != null) { "onSave was not invoked" }
        assert(saved!!.first == "Reading v2") { "Expected new title, got ${saved!!.first}" }
        assert(saved!!.third) { "Expected visibility toggled to public" }
    }
}
