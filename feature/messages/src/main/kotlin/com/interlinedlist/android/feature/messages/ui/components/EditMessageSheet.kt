package com.interlinedlist.android.feature.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Stable test tags for the in-place edit sheet. */
object EditMessageSheetTags {
    const val SHEET = "editMessageSheet"
    const val INPUT = "editMessageInput"
    const val SAVE = "editMessageSave"
}

/**
 * In-place editor for one of the caller's own messages: a bottom sheet seeded with
 * the current content. Saving PATCHes the new text; the feed/detail update
 * optimistically and gain an "edited" marker.
 *
 * @param onSave invoked when the user confirms the edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMessageSheet(
    text: String,
    canSave: Boolean,
    isSaving: Boolean,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(EditMessageSheetTags.SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Edit message",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Edit your message…") },
                enabled = !isSaving,
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EditMessageSheetTags.INPUT),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.testTag(EditMessageSheetTags.SAVE),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
