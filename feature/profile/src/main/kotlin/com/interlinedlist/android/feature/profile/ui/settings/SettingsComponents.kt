package com.interlinedlist.android.feature.profile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * A titled block of related settings, mirroring the grouping of the web Settings page.
 * Each settings group (view preferences, message settings, notifications, …) is one of
 * these, so groups added later look and behave the same.
 */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
        content()
    }
}

/** One choice in a group of mutually exclusive settings (e.g. the feed filter). */
@Composable
fun SettingsRadioRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(tag)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** An on/off setting with a label and a short explanation. */
@Composable
fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** Test tag of the decrement button of the [SettingsNumberRow] tagged [tag]. */
fun settingsDecrementTag(tag: String): String = "${tag}Decrement"

/** Test tag of the increment button of the [SettingsNumberRow] tagged [tag]. */
fun settingsIncrementTag(tag: String): String = "${tag}Increment"

/** Test tag of the validation message of the [SettingsNumberRow] tagged [tag]. */
fun settingsNumberErrorTag(tag: String): String = "${tag}Error"

/**
 * Parses [text] as a whole number inside [range], or null when it is neither.
 *
 * This is the guard that keeps a typed value from reaching the API: the row only
 * reports a change when this returns non-null, so an empty, malformed or
 * out-of-range entry is never saved.
 */
internal fun parseBoundedInt(text: String, range: IntRange): Int? =
    text.trim().toIntOrNull()?.takeIf { it in range }

/**
 * A whole-number setting: a validated field flanked by minus/plus buttons.
 *
 * The field accepts digits only and reports a change just once the entry is a whole
 * number inside [range] — on Done, or on one of the stepper buttons — so the caller
 * can never be handed a value it would have to reject. Leaving the field with an
 * invalid entry restores the stored value rather than leaving a number on screen
 * that was never saved, and because the field is keyed on [value] it also resyncs
 * when the caller rolls a failed save back.
 *
 * @param value the currently stored value.
 * @param range the values a user may enter.
 * @param step how much the minus/plus buttons move; the field handles bigger jumps.
 */
@Composable
fun SettingsNumberRow(
    label: String,
    description: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
) {
    // Keyed on `value` so a rollback, or any change from elsewhere, re-seeds the field.
    var text by remember(value) { mutableStateOf(value.toString()) }
    var wasFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val parsed = parseBoundedInt(text, range)
    val isInvalid = parsed == null
    val stepBase = parsed ?: value

    fun commit(candidate: Int) {
        val clamped = candidate.coerceIn(range)
        text = clamped.toString()
        if (clamped != value) onValueChange(clamped)
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { commit(stepBase - step) },
                enabled = stepBase > range.first,
                modifier = Modifier.testTag(settingsDecrementTag(tag)),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            OutlinedTextField(
                value = text,
                onValueChange = { entry -> text = entry.filter(Char::isDigit).take(MAX_DIGITS) },
                singleLine = true,
                isError = isInvalid,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        // An invalid entry keeps focus and its message so it can be fixed.
                        parsed?.let {
                            commit(it)
                            focusManager.clearFocus()
                        }
                    },
                ),
                modifier = Modifier
                    .width(120.dp)
                    .testTag(tag)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            wasFocused = true
                        } else if (wasFocused) {
                            wasFocused = false
                            // Leaving the field saves a valid entry and discards anything else.
                            if (parsed != null) commit(parsed) else text = value.toString()
                        }
                    },
            )
            IconButton(
                onClick = { commit(stepBase + step) },
                enabled = stepBase < range.last,
                modifier = Modifier.testTag(settingsIncrementTag(tag)),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
        if (isInvalid) {
            Text(
                text = "Enter a whole number from ${range.first} to ${range.last}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(settingsNumberErrorTag(tag)),
            )
        }
    }
}

/** Widest entry the field accepts — keeps a pasted number from overflowing an Int. */
private const val MAX_DIGITS = 6
