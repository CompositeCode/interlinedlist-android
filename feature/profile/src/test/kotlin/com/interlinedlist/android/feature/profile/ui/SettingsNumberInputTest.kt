package com.interlinedlist.android.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.profile.ui.settings.parseBoundedInt
import org.junit.Test

/**
 * The validation behind `SettingsNumberRow`'s field. It is what stops a typed value
 * from reaching the ViewModel — and therefore the API — unless it is a whole number
 * inside the row's range.
 */
class SettingsNumberInputTest {

    private val range = 10..30

    @Test
    fun `accepts a whole number inside the range`() {
        assertThat(parseBoundedInt("10", range)).isEqualTo(10)
        assertThat(parseBoundedInt("20", range)).isEqualTo(20)
        assertThat(parseBoundedInt("30", range)).isEqualTo(30)
        assertThat(parseBoundedInt(" 25 ", range)).isEqualTo(25)
    }

    @Test
    fun `rejects values outside the range`() {
        assertThat(parseBoundedInt("9", range)).isNull()
        assertThat(parseBoundedInt("31", range)).isNull()
        assertThat(parseBoundedInt("0", range)).isNull()
    }

    @Test
    fun `rejects anything that is not a whole number`() {
        assertThat(parseBoundedInt("", range)).isNull()
        assertThat(parseBoundedInt("   ", range)).isNull()
        assertThat(parseBoundedInt("abc", range)).isNull()
        assertThat(parseBoundedInt("2.5", range)).isNull()
        assertThat(parseBoundedInt("-20", range)).isNull()
        assertThat(parseBoundedInt("20abc", range)).isNull()
    }

    @Test
    fun `rejects a number too large to be an Int instead of overflowing`() {
        assertThat(parseBoundedInt("99999999999999", range)).isNull()
    }
}
