package com.interlinedlist.android.feature.documents.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.documents.domain.ShareLink
import com.interlinedlist.android.feature.documents.domain.ShareRole

/** Stable test tags for the document share sheet. */
object DocumentShareTestTags {
    const val SHEET = "docShareSheet"
    const val LINKS = "docShareLinks"
    const val CREATE = "docShareCreate"
    const val EMPTY = "docShareEmpty"
    const val PROGRESS = "docShareProgress"
    const val ERROR = "docShareError"
    fun link(token: String) = "docShareLink_$token"
    fun copy(token: String) = "docShareCopy_$token"
    fun revoke(token: String) = "docShareRevoke_$token"
    fun role(role: ShareRole) = "docShareRole_${role.apiValue}"
}

/**
 * Hilt-wired share sheet for a document, shown as a modal bottom sheet over the
 * editor; [onDismiss] closes it. Reads its `documentId` from the nav SavedStateHandle
 * (see [SHARE_DOCUMENT_ID_ARG]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentShareRoute(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DocumentShareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(DocumentShareTestTags.SHEET),
    ) {
        DocumentShareSheetContent(
            state = state,
            onSelectRole = viewModel::selectRole,
            onCreate = viewModel::createLink,
            onRevoke = viewModel::revokeLink,
        )
    }
}

/** Stateless share sheet body — role picker + create control + existing links. */
@Composable
fun DocumentShareSheetContent(
    state: DocumentShareUiState,
    onSelectRole: (ShareRole) -> Unit,
    onCreate: () -> Unit,
    onRevoke: (ShareLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Text("Share this document", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Create a link and choose what people who open it can do.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareRole.entries.forEach { role ->
                FilterChip(
                    selected = state.selectedRole == role,
                    onClick = { onSelectRole(role) },
                    label = { Text(role.label) },
                    modifier = Modifier.testTag(DocumentShareTestTags.role(role)),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onCreate,
            enabled = !state.isCreating,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DocumentShareTestTags.CREATE),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("  Create ${state.selectedRole.label.lowercase()} link")
        }

        if (state.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(DocumentShareTestTags.ERROR),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Existing links", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        when {
            state.isLoading -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.testTag(DocumentShareTestTags.PROGRESS)) }

            state.isEmpty -> Text(
                text = "No share links yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(DocumentShareTestTags.EMPTY),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .testTag(DocumentShareTestTags.LINKS),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.activeLinks, key = { it.token }) { link ->
                    DocumentShareLinkRow(
                        link = link,
                        onCopy = { clipboard.setText(AnnotatedString(link.url())) },
                        onRevoke = { onRevoke(link) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentShareLinkRow(
    link: ShareLink,
    onCopy: () -> Unit,
    onRevoke: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DocumentShareTestTags.link(link.token)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(link.role.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = link.url(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.testTag(DocumentShareTestTags.copy(link.token)),
            ) { Icon(Icons.Outlined.Share, contentDescription = "Copy link") }
            IconButton(
                onClick = onRevoke,
                modifier = Modifier.testTag(DocumentShareTestTags.revoke(link.token)),
            ) { Icon(Icons.Default.Close, contentDescription = "Revoke link") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DocumentShareSheetPreview() {
    InterlinedListTheme {
        DocumentShareSheetContent(
            state = DocumentShareUiState(
                links = listOf(
                    ShareLink("1", "abc123", ShareRole.VIEW, null, null, null),
                    ShareLink("2", "def456", ShareRole.EDIT, null, null, null),
                ),
                isLoading = false,
            ),
            onSelectRole = {},
            onCreate = {},
            onRevoke = {},
        )
    }
}
