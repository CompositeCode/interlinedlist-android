package com.interlinedlist.android.feature.documents.ui.powered

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A rejected `/suggest` still burns one of the 50 daily generations, so the
 * Research URL is checked here before any request leaves the device.
 */
class ResearchUrlTest {

    @Test
    fun `accepts http and https addresses`() {
        assertThat(ResearchUrl.normalise("https://example.com/a/b?c=d"))
            .isEqualTo("https://example.com/a/b?c=d")
        assertThat(ResearchUrl.normalise("http://sub.example.co.uk")).isEqualTo("http://sub.example.co.uk")
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertThat(ResearchUrl.normalise("  https://example.com  ")).isEqualTo("https://example.com")
    }

    @Test
    fun `refuses anything the server's fetcher would not fetch`() {
        val refused = listOf(
            "",
            "   ",
            "example.com",
            "not a url",
            "ftp://example.com/file.txt",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "https://",
            "https://localhost",
            "https://example .com",
        )
        refused.forEach { assertThat(ResearchUrl.normalise(it)).isNull() }
    }
}
