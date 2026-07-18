package com.interlinedlist.android.feature.lists.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.SchemaField

object RowEditorTestTags {
    const val SAVE = "rowEditorSave"
    const val CANCEL = "rowEditorCancel"
    fun field(key: String) = "rowField_$key"
}

/**
 * A form generated entirely from the list [schema]. Each [SchemaField] maps to an
 * input appropriate for its [FieldType] (text/number/url text fields, a boolean
 * switch, or single-select chips). Editing an existing [row] seeds the fields.
 *
 * Values are collected as strings keyed by field key and returned to [onSave];
 * the repository serialises them into the row's dynamic `data` map.
 */
@Composable
fun RowEditor(
    schema: ListSchema,
    row: ListRow?,
    isSaving: Boolean,
    onSave: (Map<String, String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One editable value per schema field, seeded from the row when editing.
    val values = remember(row, schema) {
        mutableStateMapOf<String, String>().apply {
            schema.fields.forEach { field -> put(field.key, row?.valueFor(field.key).orEmpty()) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (row == null) "Add row" else "Edit row",
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
        )

        schema.fields.forEach { field ->
            FieldInput(
                field = field,
                value = values[field.key].orEmpty(),
                onValueChange = { values[field.key] = it },
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(RowEditorTestTags.CANCEL),
            ) { Text("Cancel") }
            Button(
                onClick = { onSave(values.toMap()) },
                enabled = !isSaving,
                modifier = Modifier.testTag(RowEditorTestTags.SAVE),
            ) { Text(if (row == null) "Add" else "Save") }
        }
    }
}

/** Renders the input control matched to the field's type. */
@Composable
private fun FieldInput(
    field: SchemaField,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val tag = RowEditorTestTags.field(field.key)
    when (field.type) {
        FieldType.BOOLEAN -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(field.label)
            Switch(
                checked = value.equals("true", ignoreCase = true),
                onCheckedChange = { onValueChange(it.toString()) },
                modifier = Modifier.testTag(tag),
            )
        }

        FieldType.SELECT -> Column {
            Text(field.label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.forEach { option ->
                    FilterChip(
                        selected = value == option,
                        onClick = { onValueChange(option) },
                        label = { Text(option) },
                    )
                }
            }
        }

        else -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(field.label + if (field.required) " *" else "") },
            singleLine = field.type != FieldType.TEXT,
            keyboardOptions = KeyboardOptions(keyboardType = field.type.keyboardType()),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag),
        )
    }
}

private fun FieldType.keyboardType(): KeyboardType = when (this) {
    FieldType.NUMBER -> KeyboardType.Number
    FieldType.URL -> KeyboardType.Uri
    else -> KeyboardType.Text
}
