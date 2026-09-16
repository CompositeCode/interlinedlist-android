package com.interlinedlist.android.feature.profile.domain

/**
 * The account's profile location — the `latitude`/`longitude` pair carried by
 * `GET /api/user` and accepted by `PATCH /api/user/update` (live-verified as JSON
 * floats: `47.6062` / `-122.3321`).
 *
 * The two numbers are modelled as one value because they only mean anything together:
 * half a coordinate would put the account somewhere its owner has never been. Every
 * write therefore sends both, and a stored location exists only when the API returned
 * both (see [UserSettings.coordinates]).
 *
 * The help centre describes the field as "Optional location for your profile, used by
 * the Weather widget and similar location-aware features" (`/help/settings`, *Profile
 * location*) — so nothing in the app may depend on it being set.
 */
data class Coordinates(val latitude: Double, val longitude: Double)

/**
 * The range each coordinate may take. These are the definition of latitude and
 * longitude, not a server limit — the API publishes none — so they are enforced
 * client-side and an entry outside them is refused before any request is made.
 */
object CoordinateBounds {

    /** Degrees north (+) or south (−) of the equator. */
    val LATITUDE: ClosedFloatingPointRange<Double> = -90.0..90.0

    /** Degrees east (+) or west (−) of the prime meridian. */
    val LONGITUDE: ClosedFloatingPointRange<Double> = -180.0..180.0
}

/**
 * True when both numbers are real coordinates. `NaN` and the infinities fall outside
 * every range, so they are rejected here too rather than reaching the API.
 */
val Coordinates.isValid: Boolean
    get() = latitude in CoordinateBounds.LATITUDE && longitude in CoordinateBounds.LONGITUDE

/**
 * The location stored on the account, or null when it has none.
 *
 * A half-populated pair reads as "no location": showing one coordinate on its own
 * would imply a position the account has not actually stored.
 */
val UserSettings.coordinates: Coordinates?
    get() {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        return Coordinates(lat, lon)
    }

/**
 * What a `PATCH /api/user/update` should do to the account's coordinates: set them, or
 * clear them. Absence (a null [UserSettingsUpdate.location]) means "leave them alone".
 *
 * Clearing has to be its own case because the two are *not* the same request. The
 * module's JSON config drops null properties (`explicitNulls = false`), so a Kotlin
 * null would be omitted from the body — which is exactly how "leave it alone" is
 * expressed. [Clear] is serialised as an explicit JSON `null` instead; see
 * `SettingsMappers`.
 */
sealed interface LocationUpdate {

    /** Store [coordinates] on the account. */
    data class Set(val coordinates: Coordinates) : LocationUpdate

    /**
     * Remove the account's stored location.
     *
     * **The live API does not support this, so nothing in the app sends it.** Probed
     * against a real account: whenever the `latitude` key is present the endpoint
     * validates it as a required number, so every way of saying "no value" is refused
     * with `400 {"error":"latitude must be a number between -90 and 90",
     * "code":"bad_request"}` — an explicit JSON `null`, an empty string, and the
     * string `"null"` alike. Omitting the key leaves the stored value untouched, and
     * `0,0` is accepted but is Null Island, a real position off the coast of Africa,
     * not an absence.
     *
     * The case is kept because the modelling is right and the serialisation is
     * already correct (see `SettingsMappers` and its tests): the day the endpoint
     * accepts a null, wiring this back up is a one-liner. Until then the UI offers no
     * way to reach it — see `ProfileLocationViewModel`, which can only ever send
     * [Set]. This is an API gap, not an app one.
     */
    data object Clear : LocationUpdate
}

/**
 * The same position rounded to about a kilometre.
 *
 * Applied to readings taken **from the device**, never to what the user typed. The app
 * asks for `ACCESS_COARSE_LOCATION`, whose fix is only accurate to roughly that anyway,
 * and the stored value exists to drive city-level surfaces (the weather and location
 * widgets the help centre describes). Writing a street-level position to the account
 * would record more about the user than the feature can use, so it is rounded off
 * before it is ever sent. A coordinate the user enters by hand is left exactly as
 * typed — that one is their own choice, to whatever precision they chose.
 */
fun Coordinates.coarsened(): Coordinates = Coordinates(
    latitude = latitude.roundToCoarse(),
    longitude = longitude.roundToCoarse(),
)

/** Two decimal places ≈ 1.1 km — the granularity a coarse fix actually carries. */
private fun Double.roundToCoarse(): Double = kotlin.math.round(this * 100.0) / 100.0
