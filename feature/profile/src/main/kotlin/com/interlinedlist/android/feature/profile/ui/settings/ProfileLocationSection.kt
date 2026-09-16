package com.interlinedlist.android.feature.profile.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.domain.CoordinateBounds
import com.interlinedlist.android.feature.profile.domain.Coordinates

/** Stable test tags for the profile-location section and its permission rationale. */
object ProfileLocationTestTags {
    const val GROUP = "settingsGroupProfileLocation"
    const val VALUE = "settingsLocationValue"
    const val PRIVACY_NOTE = "settingsLocationPrivacyNote"
    const val USE_DEVICE = "settingsLocationUseDevice"
    const val LATITUDE = "settingsLocationLatitude"
    const val LONGITUDE = "settingsLocationLongitude"
    const val SAVE = "settingsLocationSave"
    const val CLEAR = "settingsLocationClear"
    const val NOTICE = "settingsLocationNotice"
    const val CANNOT_REMOVE = "settingsLocationCannotRemove"
    const val ENTRY_ERROR = "settingsLocationEntryError"
    const val RATIONALE = "locationRationale"
    const val RATIONALE_CONTINUE = "locationRationaleContinue"
    const val RATIONALE_DISMISS = "locationRationaleDismiss"
}

/**
 * What the section says about where the coordinates end up.
 *
 * The first two sentences are facts: `PATCH /api/user/update` stores them on the
 * account, and the public profile (`GET /api/users/{username}`, verified live) returns
 * only name, bio, avatar and counts — no coordinates. The third is the honest gap: the
 * help centre documents the field as "Optional location for your profile, used by the
 * Weather widget and similar location-aware features" and never says who else can read
 * it, so the section does not claim to know.
 */
internal const val LOCATION_PRIVACY_NOTE: String =
    "This is saved on your InterlinedList account, not just on this device. Your public " +
        "profile doesn't include it. The help centre doesn't say who else can see it, and " +
        "a saved location can be replaced but not removed — so save only a location " +
        "you're happy to keep on your account."

/**
 * Why "Clear location" is offered but cannot be pressed.
 *
 * `PATCH /api/user/update` validates `latitude` as a required number whenever the key
 * is present, so a null, an empty string and the string `"null"` are all answered with
 * `400 {"error":"latitude must be a number between -90 and 90"}`, and omitting the key
 * leaves the stored value alone. There is therefore no request this app can send that
 * removes a location. The button stays visible, and disabled, with this note beside it:
 * the question "how do I remove this?" is answered where it gets asked, rather than by
 * a control that would fail every time it was pressed. (`0, 0` is accepted, but it is a
 * real position in the Gulf of Guinea, not an absence — writing it would be worse than
 * doing nothing.)
 */
internal const val LOCATION_CANNOT_REMOVE_NOTE: String =
    "InterlinedList can't remove a saved location: its API rejects an empty value, so " +
        "there's nothing this app can send to unset it. You can replace it with different " +
        "coordinates at any time."

/**
 * Parses a typed coordinate, or null when it is not a number inside [range].
 *
 * The guard that keeps a bad entry off the wire: the field reports a value only when
 * this returns non-null, so an empty, malformed, or impossible entry is never saved.
 * The view model repeats the range check, because it also takes readings from the
 * device.
 */
internal fun parseCoordinate(text: String, range: ClosedFloatingPointRange<Double>): Double? =
    text.trim().toDoubleOrNull()?.takeIf { it in range }

/** The stored pair as the section shows it, e.g. `47.6062, -122.3321`. */
internal fun Coordinates.display(): String = "$latitude, $longitude"

/**
 * The "Profile location" group, wired to its view model.
 *
 * This is the only place in the app that can reach the device's position, and it does
 * so along one path: the user presses **Use my location**, reads the rationale, and
 * agrees to the system dialog. The rationale is shown **before** the system prompt —
 * after it, the decision has already been made and an explanation is just an excuse —
 * and it is shown every time the permission is not already held, so nobody is asked to
 * decide without being told what the app will do with the answer.
 *
 * Declining costs nothing: the coordinates can still be typed in, and no other part of
 * Settings changes.
 */
@Composable
fun ProfileLocationSection(
    modifier: Modifier = Modifier,
    viewModel: ProfileLocationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showRationale by rememberSaveable { mutableStateOf(false) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.useDeviceLocation()
        } else {
            viewModel.onPermissionDenied(permanently = !context.canAskForCoarseLocation())
        }
    }

    ProfileLocationGroup(
        state = state,
        onUseDeviceLocation = {
            // Already granted: read it. Otherwise explain first, and only then ask.
            if (context.hasCoarseLocationPermission()) {
                viewModel.useDeviceLocation()
            } else {
                showRationale = true
            }
        },
        onSaveLocation = viewModel::saveLocation,
        onDismissNotice = viewModel::dismissNotice,
        modifier = modifier,
    )

    if (showRationale) {
        LocationPermissionRationaleDialog(
            onContinue = {
                showRationale = false
                requestPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onDismiss = { showRationale = false },
        )
    }
}

/**
 * Stateless "Profile location" UI: what is stored, a one-press capture from the
 * device, and manual entry.
 *
 * The stored line and the fields both come from [state], which only ever holds what
 * the server last confirmed — so the section never shows a location the account does
 * not actually have. Removing a location is not offered as an action because the API
 * cannot do it; see [LOCATION_CANNOT_REMOVE_NOTE].
 */
@Composable
internal fun ProfileLocationGroup(
    state: ProfileLocationUiState,
    onUseDeviceLocation: () -> Unit,
    onSaveLocation: (Double, Double) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the stored pair so a save, a capture or a refresh re-seeds the fields.
    var latitudeText by remember(state.coordinates) {
        mutableStateOf(state.coordinates?.latitude?.toString().orEmpty())
    }
    var longitudeText by remember(state.coordinates) {
        mutableStateOf(state.coordinates?.longitude?.toString().orEmpty())
    }
    val latitude = parseCoordinate(latitudeText, CoordinateBounds.LATITUDE)
    val longitude = parseCoordinate(longitudeText, CoordinateBounds.LONGITUDE)
    val entryIsInvalid = (latitudeText.isNotBlank() && latitude == null) ||
        (longitudeText.isNotBlank() && longitude == null)

    SettingsGroup(
        title = "Profile location",
        description = "Optional. The coordinates saved on your account, used by " +
            "location-aware features such as the weather widget.",
        modifier = modifier.testTag(ProfileLocationTestTags.GROUP),
    ) {
        Text(
            text = state.coordinates?.let { "Saved location: ${it.display()}" }
                ?: "No location saved.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp)
                .testTag(ProfileLocationTestTags.VALUE),
        )
        Text(
            text = LOCATION_PRIVACY_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag(ProfileLocationTestTags.PRIVACY_NOTE),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onUseDeviceLocation,
                enabled = !state.isReadingDevice && !state.isSaving,
                modifier = Modifier.testTag(ProfileLocationTestTags.USE_DEVICE),
            ) {
                Text("Use my location")
            }
            if (state.isReadingDevice || state.isSaving) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoordinateField(
                label = "Latitude",
                value = latitudeText,
                isError = latitudeText.isNotBlank() && latitude == null,
                onValueChange = { latitudeText = it.filterCoordinate() },
                tag = ProfileLocationTestTags.LATITUDE,
                modifier = Modifier.weight(1f),
            )
            CoordinateField(
                label = "Longitude",
                value = longitudeText,
                isError = longitudeText.isNotBlank() && longitude == null,
                onValueChange = { longitudeText = it.filterCoordinate() },
                tag = ProfileLocationTestTags.LONGITUDE,
                modifier = Modifier.weight(1f),
            )
        }
        if (entryIsInvalid) {
            Text(
                text = "Latitude runs from -90 to 90 and longitude from -180 to 180.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .testTag(ProfileLocationTestTags.ENTRY_ERROR),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (latitude != null && longitude != null) {
                        onSaveLocation(latitude, longitude)
                    }
                },
                enabled = latitude != null && longitude != null && !state.isSaving,
                modifier = Modifier.testTag(ProfileLocationTestTags.SAVE),
            ) {
                Text("Save location")
            }
            // Shown, never pressable: the endpoint has no way to unset a location, so
            // the note below says so rather than the button failing on every press.
            TextButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.testTag(ProfileLocationTestTags.CLEAR),
            ) {
                Text("Clear location")
            }
        }
        if (state.coordinates != null) {
            Text(
                text = LOCATION_CANNOT_REMOVE_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .testTag(ProfileLocationTestTags.CANNOT_REMOVE),
            )
        }
        if (state.notice != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.notice.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.notice.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f).testTag(ProfileLocationTestTags.NOTICE),
                )
                TextButton(onClick = onDismissNotice) { Text("Dismiss") }
            }
        }
    }
}

/** One coordinate entry field: signed decimals only, saved from the button. */
@Composable
private fun CoordinateField(
    label: String,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        modifier = modifier.testTag(tag),
    )
}

/** Keeps the field to the characters a signed decimal can contain. */
private fun String.filterCoordinate(): String =
    filter { it.isDigit() || it == '-' || it == '.' }.take(MAX_COORDINATE_CHARS)

/** Enough for any coordinate, short enough that a paste cannot fill the field. */
private const val MAX_COORDINATE_CHARS = 12

/**
 * The rationale shown **before** Android's permission dialog.
 *
 * It says what will be read (approximate position, once), what will happen to it (it
 * is saved to the account as the profile location), how precise it will be (rounded to
 * about a kilometre), and that saying no costs nothing. Everything here is what the
 * code actually does — see `SystemDeviceLocationSource` and `Coordinates.coarsened`.
 */
@Composable
internal fun LocationPermissionRationaleDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(ProfileLocationTestTags.RATIONALE),
        title = { Text("Use this device's location?") },
        text = {
            Column {
                Text(
                    "Android will ask for permission to read your approximate location. " +
                        "InterlinedList reads it once, right now — never in the background.",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The reading is rounded to about a kilometre and saved to your account " +
                        "as your profile location, which powers location-aware features such " +
                        "as the weather widget.",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Once saved, a location can be replaced with different coordinates but " +
                        "not removed — InterlinedList has no way to unset it.",
                )
                Spacer(Modifier.height(8.dp))
                Text("You can say no and type your coordinates in instead, or leave them unset.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.testTag(ProfileLocationTestTags.RATIONALE_CONTINUE),
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(ProfileLocationTestTags.RATIONALE_DISMISS),
            ) {
                Text("Not now")
            }
        },
    )
}

/** True when `ACCESS_COARSE_LOCATION` is already granted to the app. */
private fun Context.hasCoarseLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Whether Android would still show the permission dialog.
 *
 * Only consulted immediately after a refusal, where `false` means the system will not
 * ask again — the point at which the app has to say where the decision can be changed
 * instead of offering a button that would now do nothing.
 */
private fun Context.canAskForCoarseLocation(): Boolean {
    val activity = findActivity() ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun ProfileLocationGroupPreview() {
    InterlinedListTheme {
        ProfileLocationGroup(
            state = ProfileLocationUiState(coordinates = Coordinates(47.6062, -122.3321)),
            onUseDeviceLocation = {},
            onSaveLocation = { _, _ -> },
            onDismissNotice = {},
        )
    }
}
