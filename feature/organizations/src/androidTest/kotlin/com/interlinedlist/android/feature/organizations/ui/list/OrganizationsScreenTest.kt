package com.interlinedlist.android.feature.organizations.ui.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.organizations.domain.Organization
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI coverage for the stateless [OrganizationsScreen]. Runs on-device; the
 * orchestrator executes instrumented tests after merge (no emulator in the
 * worktree), so this is written to compile and be correct.
 */
@RunWith(AndroidJUnit4::class)
class OrganizationsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: OrganizationsUiState,
        onOpenOrg: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                OrganizationsScreen(
                    state = state,
                    onOpenOrg = onOpenOrg,
                    onBack = {},
                    onLoadMore = {},
                    onCreateOrganization = { _, _, _ -> },
                )
            }
        }
    }

    @Test
    fun rendersCards_andOpensOrgOnTap() {
        var opened: String? = null
        setScreen(
            state = OrganizationsUiState(
                organizations = listOf(
                    Organization("1", "Acme Corp", "Makers", null, false, 3, null, null),
                    Organization("2", "Open", null, null, true, 0, null, null),
                ),
                isRefreshing = false,
            ),
            onOpenOrg = { opened = it },
        )

        composeRule.onNodeWithTag(OrganizationsTestTags.row("1")).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationsTestTags.row("2")).assertIsDisplayed()

        composeRule.onNodeWithTag(OrganizationsTestTags.row("1")).performClick()
        assert(opened == "1")
    }

    @Test
    fun showsEmptyState_whenNoOrgs() {
        setScreen(state = OrganizationsUiState(organizations = emptyList(), isRefreshing = false))

        composeRule.onNodeWithTag(OrganizationsTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun showsSubscriptionGate_whenRequired() {
        setScreen(
            state = OrganizationsUiState(
                subscriptionRequired = true,
                errorMessage = "Organizations require an active subscription",
            ),
        )

        composeRule.onNodeWithTag(OrganizationsTestTags.SUBSCRIPTION).assertIsDisplayed()
    }

    @Test
    fun openingCreateDialog_showsNameField() {
        setScreen(state = OrganizationsUiState(organizations = emptyList(), isRefreshing = false))

        composeRule.onNodeWithTag(OrganizationsTestTags.CREATE_FAB).performClick()
        composeRule.onNodeWithTag(OrganizationsTestTags.CREATE_NAME).assertIsDisplayed()
    }
}
