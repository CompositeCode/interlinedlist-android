package com.interlinedlist.android.feature.messages.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.feature.messages.domain.ReportReason

/** Stable test tags for the report dialog. */
object ReportDialogTags {
    const val DIALOG = "reportDialog"
    const val DETAIL = "reportDetail"
    const val SUBMIT = "reportSubmit"
}

/**
 * Report dialog: pick a [ReportReason] and optionally add free-text detail, then
 * submit. Reason selection is required to enable the submit button.
 *
 * @param onSubmit invoked with the chosen reason and (possibly blank) detail.
 */
@Composable
fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (ReportReason, String) -> Unit,
    isSubmitting: Boolean = false,
) {
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var detail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ReportDialogTags.DIALOG),
        title = { Text("Report message") },
        text = {
            Column {
                Text(
                    text = "Why are you reporting this?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ReportReason.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = reason == option,
                                onClick = { reason = option },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Spacer(Modifier.height(0.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    placeholder = { Text("Add details (optional)") },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ReportDialogTags.DETAIL),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { reason?.let { onSubmit(it, detail) } },
                enabled = reason != null && !isSubmitting,
                modifier = Modifier.testTag(ReportDialogTags.SUBMIT),
            ) {
                Text("Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        },
    )
}
