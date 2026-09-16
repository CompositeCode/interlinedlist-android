package com.interlinedlist.android.feature.profile.domain

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.datastore.ThemeMode
import org.junit.Test

/**
 * Issue #36: the rule that decides which side wins when the device's theme and the
 * account's `theme` disagree.
 *
 * The rule is deliberately **not** "newest timestamp wins" (neither side carries one)
 * and **not** "the account always wins" (that would silently undo a change made with
 * no connection). It is: *an unsynced local choice wins; otherwise the account wins*.
 * Both halves are asserted here so the behaviour cannot drift into whatever the
 * repository happens to do.
 */
class ThemeSyncTest {

    // --- Wire mapping --------------------------------------------------------

    /**
     * `/help/settings` publishes the account's own vocabulary: "Theme: Light, dark, or
     * system (follows your device preference)" — so "follows the system" is a real
     * account value and a local SYSTEM choice does **not** have to be coerced into
     * light or dark. Live `GET /api/user` returns these lower-case.
     */
    @Test
    fun `each mode maps to the theme value the web publishes`() {
        assertThat(ThemeMode.LIGHT.wire).isEqualTo("light")
        assertThat(ThemeMode.DARK.wire).isEqualTo("dark")
        assertThat(ThemeMode.SYSTEM.wire).isEqualTo("system")
    }

    @Test
    fun `every mode round-trips through the wire value`() {
        ThemeMode.entries.forEach { mode ->
            assertThat(themeModeFromWire(mode.wire)).isEqualTo(mode)
        }
    }

    @Test
    fun `parsing tolerates casing and the obvious synonym for system`() {
        assertThat(themeModeFromWire("DARK")).isEqualTo(ThemeMode.DARK)
        assertThat(themeModeFromWire(" Light ")).isEqualTo(ThemeMode.LIGHT)
        assertThat(themeModeFromWire("auto")).isEqualTo(ThemeMode.SYSTEM)
    }

    /**
     * The live server accepts *any* string for `theme` — a PATCH of "sepia" (or of "")
     * is answered 200 and stored verbatim — so an unrenderable value really can come
     * back. It must parse to null rather than to a mode we would then adopt.
     */
    @Test
    fun `a value the app cannot render does not parse`() {
        assertThat(themeModeFromWire("sepia")).isNull()
        assertThat(themeModeFromWire("")).isNull()
        assertThat(themeModeFromWire(null)).isNull()
    }

    // --- The account wins when nothing changed here ---------------------------

    /**
     * The headline case from the issue: dark was chosen on the web, this is a fresh
     * install whose local default is SYSTEM and was never touched, so the account's
     * choice is adopted.
     */
    @Test
    fun `a fresh install adopts the account theme`() {
        val outcome = reconcileTheme(
            local = ThemeMode.SYSTEM,
            hasUnsyncedLocalChange = false,
            accountTheme = "dark",
        )

        assertThat(outcome).isEqualTo(ThemeReconciliation.AdoptAccount(ThemeMode.DARK))
    }

    @Test
    fun `a synced device adopts a theme changed elsewhere`() {
        val outcome = reconcileTheme(
            local = ThemeMode.DARK,
            hasUnsyncedLocalChange = false,
            accountTheme = "light",
        )

        assertThat(outcome).isEqualTo(ThemeReconciliation.AdoptAccount(ThemeMode.LIGHT))
    }

    @Test
    fun `agreement is left alone`() {
        val outcome = reconcileTheme(
            local = ThemeMode.DARK,
            hasUnsyncedLocalChange = false,
            accountTheme = "dark",
        )

        assertThat(outcome).isEqualTo(ThemeReconciliation.InSync)
    }

    /**
     * Nothing to adopt and nothing the user asked to push: an account with no stored
     * theme (or one we cannot render) leaves the device's own setting alone. In
     * particular we never seed the account from a default the user never chose, and
     * never clobber a value the web understands and we do not.
     */
    @Test
    fun `an absent or unrenderable account theme changes nothing`() {
        assertThat(
            reconcileTheme(ThemeMode.DARK, hasUnsyncedLocalChange = false, accountTheme = null),
        ).isEqualTo(ThemeReconciliation.InSync)
        assertThat(
            reconcileTheme(ThemeMode.DARK, hasUnsyncedLocalChange = false, accountTheme = "sepia"),
        ).isEqualTo(ThemeReconciliation.InSync)
    }

    // --- An unsynced local choice wins ----------------------------------------

    /**
     * The offline case. The user picked dark with no connection, so the account still
     * says light; the account value is stale by construction and the local choice is
     * pushed instead of being overwritten.
     */
    @Test
    fun `an unsynced local choice is pushed rather than overwritten`() {
        val outcome = reconcileTheme(
            local = ThemeMode.DARK,
            hasUnsyncedLocalChange = true,
            accountTheme = "light",
        )

        assertThat(outcome).isEqualTo(ThemeReconciliation.PushLocal(ThemeMode.DARK))
    }

    @Test
    fun `an unsynced local choice is pushed to an account with no theme`() {
        val outcome = reconcileTheme(
            local = ThemeMode.SYSTEM,
            hasUnsyncedLocalChange = true,
            accountTheme = null,
        )

        assertThat(outcome).isEqualTo(ThemeReconciliation.PushLocal(ThemeMode.SYSTEM))
    }

    /**
     * The account already agrees (the push landed but its confirmation was lost, or
     * the same choice was made on the web). Re-sending it would be pointless; the
     * device just records the account as holding it, which clears the pending flag.
     */
    @Test
    fun `an unsynced choice the account already holds is only marked synced`() {
        val outcome = reconcileTheme(
            local = ThemeMode.DARK,
            hasUnsyncedLocalChange = true,
            accountTheme = "dark",
        )

        assertThat(outcome).isEqualTo(ThemeReconciliation.AdoptAccount(ThemeMode.DARK))
    }

    /**
     * An unrenderable account value does not block a pending push: the user's own
     * choice is what they asked for, so it wins over a value neither side can show.
     */
    @Test
    fun `an unsynced local choice beats an unrenderable account theme`() {
        val outcome = reconcileTheme(
            local = ThemeMode.LIGHT,
            hasUnsyncedLocalChange = true,
            accountTheme = "sepia",
        )

        assertThat(outcome).isEqualTo(ThemeReconciliation.PushLocal(ThemeMode.LIGHT))
    }
}
