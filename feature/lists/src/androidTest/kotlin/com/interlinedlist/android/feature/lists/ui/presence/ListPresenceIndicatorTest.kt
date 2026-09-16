package com.interlinedlist.android.feature.lists.ui.presence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ListPresence
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "who else is here" cluster on the list detail screen: an avatar per person,
 * a "+N" chip past the cap, and nothing at all when nobody else is present.
 */
@RunWith(AndroidJUnit4::class)
class ListPresenceIndicatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun person(id: String, name: String) = ListPresence(id, displayName = name, username = name.lowercase())

    private fun setContent(participants: List<ListPresence>) {
        composeRule.setContent {
            InterlinedListTheme { ListPresenceIndicator(participants = participants) }
        }
    }

    @Test
    fun rendersAnAvatarPerPerson() {
        setContent(listOf(person("u2", "Casey"), person("u3", "Robin")))

        composeRule.onNodeWithTag(ListPresenceTestTags.ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(ListPresenceTestTags.avatar("u2")).assertIsDisplayed()
        composeRule.onNodeWithTag(ListPresenceTestTags.avatar("u3")).assertIsDisplayed()
        composeRule.onNodeWithText("C").assertIsDisplayed()
        composeRule.onNodeWithText("R").assertIsDisplayed()
    }

    @Test
    fun collapsesTheTailIntoAnOverflowChip() {
        setContent((1..5).map { person("u$it", "Person $it") })

        composeRule.onNodeWithTag(ListPresenceTestTags.OVERFLOW).assertIsDisplayed()
        composeRule.onNodeWithText("+2").assertIsDisplayed()
    }

    @Test
    fun showsNothingWhenNobodyElseIsHere() {
        setContent(emptyList())

        composeRule.onNodeWithTag(ListPresenceTestTags.ROW).assertDoesNotExist()
    }
}
