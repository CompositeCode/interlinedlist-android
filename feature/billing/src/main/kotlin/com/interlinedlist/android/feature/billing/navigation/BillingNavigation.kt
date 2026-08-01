package com.interlinedlist.android.feature.billing.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.interlinedlist.android.feature.billing.ui.UpsellRoute

/** Route keys for the billing graph. */
object BillingDestinations {
    /** Subscription upsell — the graph's entry (and only) route. */
    const val UPSELL = "billing/upsell"
}

/** Convenience navigation helper so callers don't hand-build route strings. */
fun NavController.navigateToUpsell() = navigate(BillingDestinations.UPSELL)

/**
 * Registers the billing destinations into the host graph.
 *
 * The app wires this into its top-level NavHost (see the module's report for the
 * exact snippet plus how to route here from a 403 subscription gate and the
 * Account hub). [onBack] pops the current destination.
 */
fun NavGraphBuilder.billingGraph(
    onBack: () -> Unit,
) {
    composable(BillingDestinations.UPSELL) {
        UpsellRoute(onBack = onBack)
    }
}
