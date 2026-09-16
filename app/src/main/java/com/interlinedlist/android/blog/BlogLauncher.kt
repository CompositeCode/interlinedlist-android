package com.interlinedlist.android.blog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** How a blog URL ended up being opened — or that it could not be. */
enum class BlogLaunchResult {
    /** Opened in a Custom Tab, themed from the app's colour scheme. */
    CUSTOM_TAB,

    /** No Custom Tabs-capable browser; opened in whatever browser handles the URL. */
    BROWSER,

    /** Nothing on the device can open a web URL. Nothing happened, nothing crashed. */
    UNAVAILABLE,
}

/**
 * Opens a blog URL in a Custom Tab themed to match the app.
 *
 * A Custom Tab is the honest ceiling for the blog today: it is server-rendered with no
 * public JSON endpoint (see [BlogLink]), so the alternative would be scraping HTML.
 * The tab is coloured from the live [ColorScheme], which `InterlinedListTheme` has
 * already resolved from the user's System/Light/Dark setting, so the tab follows that
 * setting without any extra plumbing.
 *
 * Custom Tabs are not guaranteed to exist: the fallback is a plain `ACTION_VIEW`
 * intent, and if even that finds no handler the call is a no-op rather than a crash.
 */
object BlogLauncher {

    /**
     * Opens [url] and reports how. Never throws: a device with no browser at all
     * yields [BlogLaunchResult.UNAVAILABLE].
     */
    fun open(context: Context, url: String, colorScheme: ColorScheme): BlogLaunchResult {
        val uri = Uri.parse(url)
        return launchWithFallback(
            customTab = { customTabsIntent(colorScheme).launchUrl(context, uri) },
            browser = { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) },
        )
    }

    /**
     * The launch chain, free of Android types so it can be unit tested: try the Custom
     * Tab, fall back to the browser, and swallow a failure of both.
     *
     * Both lambdas throw when nothing on the device can handle the intent
     * (`ActivityNotFoundException`), which is a perfectly ordinary device
     * configuration — so neither failure may escape.
     */
    internal fun launchWithFallback(customTab: () -> Unit, browser: () -> Unit): BlogLaunchResult {
        if (runCatching(customTab).isSuccess) return BlogLaunchResult.CUSTOM_TAB
        if (runCatching(browser).isSuccess) return BlogLaunchResult.BROWSER
        return BlogLaunchResult.UNAVAILABLE
    }

    /**
     * True when [surface] is a dark-theme surface. The app theme's own surface colour
     * is the single source of truth for light/dark here, so the tab matches whatever
     * the user chose without the setting having to be threaded down to every call site.
     */
    internal fun isDarkSurface(surface: Color): Boolean = surface.luminance() < 0.5f

    private fun customTabsIntent(colorScheme: ColorScheme): CustomTabsIntent {
        val colors = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(colorScheme.surface.toArgb())
            .setSecondaryToolbarColor(colorScheme.surfaceVariant.toArgb())
            .setNavigationBarColor(colorScheme.surface.toArgb())
            .build()
        return CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setColorScheme(
                if (isDarkSurface(colorScheme.surface)) {
                    CustomTabsIntent.COLOR_SCHEME_DARK
                } else {
                    CustomTabsIntent.COLOR_SCHEME_LIGHT
                },
            )
            .setDefaultColorSchemeParams(colors)
            .build()
    }
}
