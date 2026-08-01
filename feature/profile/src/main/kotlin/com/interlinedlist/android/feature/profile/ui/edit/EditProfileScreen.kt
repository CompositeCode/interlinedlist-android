package com.interlinedlist.android.feature.profile.ui.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.ui.common.UserAvatar

/** Stable test tags for the edit-profile form. */
object EditProfileTestTags {
    const val DISPLAY_NAME = "editDisplayName"
    const val BIO = "editBio"
    const val AVATAR_URL = "editAvatarUrl"
    const val SET_AVATAR_URL = "editSetAvatarUrl"
    const val PICK_AVATAR = "editPickAvatar"
    const val SAVE = "editSave"
    const val BACK = "editBack"
    const val ERROR = "editError"
    const val PROGRESS = "editProgress"
    const val SAVE_PROGRESS = "editSaveProgress"
}

/**
 * Edit-profile route. Resolves a picked image to bytes via [android.content.ContentResolver]
 * here (keeping the ViewModel/repository free of Android I/O), then hands the bytes
 * to the ViewModel for upload.
 *
 * @param onBack pop back to the account screen.
 * @param onSaved invoked after a successful save (the caller typically also pops).
 */
@Composable
fun EditProfileRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val resolver = context.contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val mime = resolver.getType(uri) ?: "image/*"
                val name = "avatar.${mime.substringAfter('/', "jpg")}"
                viewModel.uploadAvatar(bytes, name, mime)
            }
        }
    }

    EditProfileScreen(
        state = state,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onBioChange = viewModel::onBioChange,
        onAvatarUrlInputChange = viewModel::onAvatarUrlInputChange,
        onSetAvatarFromUrl = viewModel::setAvatarFromUrl,
        onPickAvatar = { pickImage.launch("image/*") },
        onSave = { viewModel.save(onSaved) },
        onBack = onBack,
        modifier = modifier,
    )
}

/** Stateless edit-profile form — easy to preview and to drive from Compose tests. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    state: EditProfileUiState,
    onDisplayNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onAvatarUrlInputChange: (String) -> Unit,
    onSetAvatarFromUrl: () -> Unit,
    onPickAvatar: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(EditProfileTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = state.canSave,
                        modifier = Modifier.testTag(EditProfileTestTags.SAVE),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .testTag(EditProfileTestTags.SAVE_PROGRESS),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                UserAvatar(
                    avatarUrl = state.avatarUrl,
                    seedLabel = state.displayName.ifBlank { "?" },
                )
                if (state.isUploadingAvatar) {
                    CircularProgressIndicator(Modifier.testTag(EditProfileTestTags.PROGRESS))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPickAvatar,
                enabled = !state.isUploadingAvatar,
                modifier = Modifier.testTag(EditProfileTestTags.PICK_AVATAR),
            ) {
                Text("Upload photo")
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = state.avatarUrlInput,
                    onValueChange = onAvatarUrlInputChange,
                    label = { Text("Avatar URL") },
                    singleLine = true,
                    enabled = !state.isUploadingAvatar,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(EditProfileTestTags.AVATAR_URL),
                )
                TextButton(
                    onClick = onSetAvatarFromUrl,
                    enabled = state.avatarUrlInput.isNotBlank() && !state.isUploadingAvatar,
                    modifier = Modifier.testTag(EditProfileTestTags.SET_AVATAR_URL),
                ) {
                    Text("Set")
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Display name") },
                singleLine = true,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EditProfileTestTags.DISPLAY_NAME),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.bio,
                onValueChange = onBioChange,
                label = { Text("Bio") },
                minLines = 3,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EditProfileTestTags.BIO),
            )

            if (state.errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(EditProfileTestTags.ERROR),
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save changes")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditProfileScreenPreview() {
    InterlinedListTheme {
        EditProfileScreen(
            state = EditProfileUiState(
                displayName = "Adron Hall",
                bio = "Building things.",
                isLoading = false,
            ),
            onDisplayNameChange = {},
            onBioChange = {},
            onAvatarUrlInputChange = {},
            onSetAvatarFromUrl = {},
            onPickAvatar = {},
            onSave = {},
            onBack = {},
        )
    }
}
