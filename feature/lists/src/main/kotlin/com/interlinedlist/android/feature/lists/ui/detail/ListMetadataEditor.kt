package com.interlinedlist.android.feature.lists.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.feature.lists.domain.ListSummary

/** Stable test tags for the list metadata editor form. */
object ListMetadataEditorTestTags {
    const val TITLE = "listEditTitle"
    const val DESCRIPTION = "listEditDescription"
    const val VISIBILITY = "listEditVisibility"
    const val SAVE = "listEditSave"
    const val CANCEL = "listEditCancel"
}

/**
 * Edit form for a list's metadata: title, description, and public/private
 * visibility. Seeds from the current [summary]; [onSave] hands back the edited
 * values (title, description, isPublic) for the caller to persist.
 */
@Composable
fun ListMetadataEditor(
    summary: ListSummary,
    isSaving: Boolean,
    onSave: (title: String, description: String?, isPublic: Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable(summary.id) { mutableStateOf(summary.title) }
    var description by rememberSaveable(summary.id) { mutableStateOf(summary.description.orEmpty()) }
    var isPublic by rememberSaveable(summary.id) { mutableStateOf(summary.isPublic) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Edit list", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            isError = title.isBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ListMetadataEditorTestTags.TITLE),
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ListMetadataEditorTestTags.DESCRIPTION),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Public", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (isPublic) "Anyone with the link can view" else "Only you and watchers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = isPublic,
                onCheckedChange = { isPublic = it },
                modifier = Modifier.testTag(ListMetadataEditorTestTags.VISIBILITY),
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(ListMetadataEditorTestTags.CANCEL),
            ) { Text("Cancel") }
            Button(
                onClick = { onSave(title, description.ifBlank { null }, isPublic) },
                enabled = !isSaving && title.isNotBlank(),
                modifier = Modifier.testTag(ListMetadataEditorTestTags.SAVE),
            ) { Text("Save") }
        }
    }
}
