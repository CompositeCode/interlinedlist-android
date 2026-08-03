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
import com.interlinedlist.android.feature.messages.ui.feed.ModerationAction
import com.interlinedlist.android.feature.messages.ui.feed.ModerationTarget

/** Stable test tags for the author-moderation confirm dialog. */
object ModerationDialogTags {
    const val DIALOG = "moderationDialog"
    const val DETAIL = "moderationDetail"
    const val CONFIRM = "moderationConfirm"
}

/**
 * Confirmation dialog for an author-moderation action. Block and Mute are simple
 * confirms; Report additionally collects a [ReportReason] (required) and optional
 * free-text detail before it can be submitted.
 *
 * @param onConfirm invoked with the chosen reason (Report only) and detail.
 */
@Composable
fun ModerationDialog(
    target: ModerationTarget,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ReportReason?, String) -> Unit,
) {
    val label = target.authorLabel
    val isReport = target.action == ModerationAction.REPORT
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var detail by remember { mutableStateOf("") }

    val (title, body, confirmLabel) = when (target.action) {
        ModerationAction.BLOCK ->
            Triple("Block $label?", "You won't see their messages, and they can't interact with you.", "Block")
        ModerationAction.MUTE ->
            Triple("Mute $label?", "Their messages will be hidden from your feed.", "Mute")
        ModerationAction.REPORT ->
            Triple("Report $label", "Why are you reporting this user?", "Report")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ModerationDialogTags.DIALOG),
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isReport) {
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
                            .testTag(ModerationDialogTags.DETAIL),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason, detail) },
                enabled = !isSubmitting && (!isReport || reason != null),
                modifier = Modifier.testTag(ModerationDialogTags.CONFIRM),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        },
    )
}
