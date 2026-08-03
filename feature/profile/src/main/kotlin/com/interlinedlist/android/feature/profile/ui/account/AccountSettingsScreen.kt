package com.interlinedlist.android.feature.profile.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import kotlinx.coroutines.flow.Flow

/** Stable test tags for the Account settings screen. */
object AccountSettingsTestTags {
    const val BACK = "accountSettingsBack"
    const val EMAIL_FIELD = "accountSettingsEmailField"
    const val CHANGE_EMAIL = "accountSettingsChangeEmail"
    const val EMAIL_REQUESTED = "accountSettingsEmailRequested"
    const val ERROR = "accountSettingsError"
    const val DELETE_ACCOUNT = "accountSettingsDeleteAccount"
    const val DELETE_DIALOG = "accountSettingsDeleteDialog"
    const val DELETE_USERNAME_FIELD = "accountSettingsDeleteUsernameField"
    const val DELETE_EMAIL_FIELD = "accountSettingsDeleteEmailField"
    const val DELETE_CONFIRM = "accountSettingsDeleteConfirm"
}

/**
 * The Account settings screen (route `account/settings`): change the account email and,
 * behind a type-to-confirm guard, delete the account.
 *
 * @param onBack pop back to the account hub.
 * @param onSignedOut invoked after the account is deleted; the app clears the session and
 *   navigates away (the profile module does not own session state).
 */
@Composable
fun AccountSettingsRoute(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountSettingsEffects(effects = viewModel.effects, onSignedOut = onSignedOut)
    AccountSettingsScreen(
        state = state,
        onChangeEmail = viewModel::requestEmailChange,
        onAcknowledgeEmailChange = viewModel::acknowledgeEmailChange,
        onDeleteAccount = viewModel::deleteAccount,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Collects the one-shot signed-out effect and forwards it to the app. */
@Composable
private fun AccountSettingsEffects(
    effects: Flow<AccountSettingsEffect>,
    onSignedOut: () -> Unit,
) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                AccountSettingsEffect.SignedOut -> onSignedOut()
            }
        }
    }
}

/** Stateless Account settings UI. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    state: AccountSettingsUiState,
    onChangeEmail: (String) -> Unit,
    onAcknowledgeEmailChange: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var newEmail by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Account settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(AccountSettingsTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            // --- Change email ---
            Text("Change email", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "We'll send a verification link to the new address before it takes effect.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it },
                label = { Text("New email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AccountSettingsTestTags.EMAIL_FIELD),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onChangeEmail(newEmail) },
                enabled = newEmail.isNotBlank() && !state.isChangingEmail,
                modifier = Modifier.testTag(AccountSettingsTestTags.CHANGE_EMAIL),
            ) {
                if (state.isChangingEmail) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Request email change")
                }
            }

            if (state.emailChangeRequested) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Check your new inbox for a verification link.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(AccountSettingsTestTags.EMAIL_REQUESTED),
                )
                LaunchedEffect(Unit) { newEmail = "" }
            }

            if (state.errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(AccountSettingsTestTags.ERROR),
                )
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(32.dp))

            // --- Delete account (danger zone) ---
            Text(
                text = "Delete account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This permanently deletes your account and all its data. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.testTag(AccountSettingsTestTags.DELETE_ACCOUNT),
            ) {
                Text("Delete account")
            }
        }
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            expectedUsername = state.username,
            isDeleting = state.isDeletingAccount,
            onConfirm = { email ->
                onDeleteAccount(email)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/**
 * The type-to-confirm delete guard: the user must re-type their exact username and enter
 * their email before the destructive confirm button enables.
 */
@Composable
private fun DeleteAccountDialog(
    expectedUsername: String,
    isDeleting: Boolean,
    onConfirm: (email: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var typedUsername by remember { mutableStateOf("") }
    var typedEmail by remember { mutableStateOf("") }
    val canDelete = typedUsername.trim() == expectedUsername &&
        typedEmail.isNotBlank() &&
        !isDeleting

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        modifier = Modifier.testTag(AccountSettingsTestTags.DELETE_DIALOG),
        title = { Text("Delete account?") },
        text = {
            Column {
                Text(
                    text = "To confirm, type your username \"$expectedUsername\" and your email.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typedUsername,
                    onValueChange = { typedUsername = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AccountSettingsTestTags.DELETE_USERNAME_FIELD),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = typedEmail,
                    onValueChange = { typedEmail = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AccountSettingsTestTags.DELETE_EMAIL_FIELD),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(typedEmail) },
                enabled = canDelete,
                modifier = Modifier.testTag(AccountSettingsTestTags.DELETE_CONFIRM),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AccountSettingsScreenPreview() {
    InterlinedListTheme {
        AccountSettingsScreen(
            state = AccountSettingsUiState(username = "adron"),
            onChangeEmail = {},
            onAcknowledgeEmailChange = {},
            onDeleteAccount = {},
            onBack = {},
        )
    }
}
