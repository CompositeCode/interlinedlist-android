package com.interlinedlist.android.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.profile.domain.CoordinateBounds
import com.interlinedlist.android.feature.profile.domain.Coordinates
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.coarsened
import com.interlinedlist.android.feature.profile.domain.coordinates
import com.interlinedlist.android.feature.profile.domain.isValid
import com.interlinedlist.android.feature.profile.ui.settings.display
import com.interlinedlist.android.feature.profile.ui.settings.parseCoordinate
import org.junit.Test

/**
 * The pure parts of the location section: what the entry fields accept, how a device
 * reading is rounded, and when the account counts as having a location.
 */
class ProfileLocationInputTest {

    @Test
    fun `a coordinate inside its range parses`() {
        assertThat(parseCoordinate("47.6062", CoordinateBounds.LATITUDE)).isEqualTo(47.6062)
        assertThat(parseCoordinate("-122.3321", CoordinateBounds.LONGITUDE)).isEqualTo(-122.3321)
        assertThat(parseCoordinate(" 0 ", CoordinateBounds.LATITUDE)).isEqualTo(0.0)
    }

    @Test
    fun `the exact bounds parse and anything beyond them does not`() {
        assertThat(parseCoordinate("90", CoordinateBounds.LATITUDE)).isEqualTo(90.0)
        assertThat(parseCoordinate("-90", CoordinateBounds.LATITUDE)).isEqualTo(-90.0)
        assertThat(parseCoordinate("90.0001", CoordinateBounds.LATITUDE)).isNull()
        assertThat(parseCoordinate("180.1", CoordinateBounds.LONGITUDE)).isNull()
        assertThat(parseCoordinate("-181", CoordinateBounds.LONGITUDE)).isNull()
    }

    @Test
    fun `an empty or malformed entry does not parse`() {
        assertThat(parseCoordinate("", CoordinateBounds.LATITUDE)).isNull()
        assertThat(parseCoordinate("-", CoordinateBounds.LATITUDE)).isNull()
        assertThat(parseCoordinate("4-7", CoordinateBounds.LATITUDE)).isNull()
        assertThat(parseCoordinate("north", CoordinateBounds.LATITUDE)).isNull()
        assertThat(parseCoordinate("NaN", CoordinateBounds.LATITUDE)).isNull()
        assertThat(parseCoordinate("Infinity", CoordinateBounds.LONGITUDE)).isNull()
    }

    @Test
    fun `a real position is valid and an impossible one is not`() {
        assertThat(Coordinates(47.6062, -122.3321).isValid).isTrue()
        assertThat(Coordinates(90.0, 180.0).isValid).isTrue()
        assertThat(Coordinates(90.1, 0.0).isValid).isFalse()
        assertThat(Coordinates(0.0, -180.1).isValid).isFalse()
        assertThat(Coordinates(Double.NaN, 0.0).isValid).isFalse()
    }

    @Test
    fun `a device reading is rounded to about a kilometre`() {
        // Coarse location is no more accurate than this, and the account has no use
        // for a street-level position.
        assertThat(Coordinates(47.60621, -122.33207).coarsened())
            .isEqualTo(Coordinates(47.61, -122.33))
        assertThat(Coordinates(-0.004, 0.006).coarsened()).isEqualTo(Coordinates(-0.0, 0.01))
        assertThat(Coordinates(47.6, -122.3).coarsened()).isEqualTo(Coordinates(47.6, -122.3))
    }

    @Test
    fun `rounding never pushes a coordinate out of range`() {
        assertThat(Coordinates(90.0, 180.0).coarsened().isValid).isTrue()
        assertThat(Coordinates(-90.0, -180.0).coarsened().isValid).isTrue()
    }

    @Test
    fun `an account has a location only when both coordinates are present`() {
        assertThat(UserSettings(latitude = 47.6062, longitude = -122.3321).coordinates)
            .isEqualTo(Coordinates(47.6062, -122.3321))
        assertThat(UserSettings(latitude = 47.6062, longitude = null).coordinates).isNull()
        assertThat(UserSettings(latitude = null, longitude = -122.3321).coordinates).isNull()
        assertThat(UserSettings().coordinates).isNull()
    }

    @Test
    fun `the stored pair is shown as latitude then longitude`() {
        assertThat(Coordinates(47.6062, -122.3321).display()).isEqualTo("47.6062, -122.3321")
    }
}
