package com.interlinedlist.android.blog

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.ILSurfaceDark
import com.interlinedlist.android.core.designsystem.theme.ILSurfaceLight
import org.junit.Test

/**
 * The launch chain is Custom Tab → plain browser → give up. All three outcomes are
 * reachable on a real device: a Custom Tabs-capable browser is not guaranteed to be
 * installed, and on a locked-down or browser-less device *nothing* can handle an
 * `https` VIEW intent. Neither case may crash the app, so the decision logic is kept
 * free of Android types and pinned here.
 */
class BlogLauncherTest {

    @Test
    fun `a working custom tab is used and the browser is never touched`() {
        var browserAttempts = 0

        val result = BlogLauncher.launchWithFallback(
            customTab = { /* succeeds */ },
            browser = { browserAttempts++ },
        )

        assertThat(result).isEqualTo(BlogLaunchResult.CUSTOM_TAB)
        assertThat(browserAttempts).isEqualTo(0)
    }

    @Test
    fun `no custom tabs provider falls back to a plain browser intent`() {
        var browserAttempts = 0

        val result = BlogLauncher.launchWithFallback(
            customTab = { throw IllegalStateException("no activity found to handle the intent") },
            browser = { browserAttempts++ },
        )

        assertThat(result).isEqualTo(BlogLaunchResult.BROWSER)
        assertThat(browserAttempts).isEqualTo(1)
    }

    @Test
    fun `no browser installed at all reports failure instead of crashing`() {
        val result = BlogLauncher.launchWithFallback(
            customTab = { throw IllegalStateException("no activity found to handle the intent") },
            browser = { throw IllegalStateException("no activity found to handle the intent") },
        )

        assertThat(result).isEqualTo(BlogLaunchResult.UNAVAILABLE)
    }

    @Test
    fun `a security exception from the browser is contained too`() {
        val result = BlogLauncher.launchWithFallback(
            customTab = { throw SecurityException("permission denial") },
            browser = { throw SecurityException("permission denial") },
        )

        assertThat(result).isEqualTo(BlogLaunchResult.UNAVAILABLE)
    }

    // ---- the tab follows the user's appearance setting ----------------------

    @Test
    fun `the app's dark surface selects the dark tab scheme`() {
        assertThat(BlogLauncher.isDarkSurface(ILSurfaceDark)).isTrue()
        assertThat(BlogLauncher.isDarkSurface(Color.Black)).isTrue()
    }

    @Test
    fun `the app's light surface selects the light tab scheme`() {
        assertThat(BlogLauncher.isDarkSurface(ILSurfaceLight)).isFalse()
        assertThat(BlogLauncher.isDarkSurface(Color.White)).isFalse()
    }
}
