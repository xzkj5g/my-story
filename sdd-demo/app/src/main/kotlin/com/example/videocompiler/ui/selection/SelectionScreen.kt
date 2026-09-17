@file:Suppress("FunctionNaming")

package com.example.videocompiler.ui.selection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.videocompiler.ui.MediaCompilerController
import com.example.videocompiler.ui.MediaCompilerUiState

@Composable
fun SelectionScreen(
    state: MediaCompilerUiState,
    controller: MediaCompilerController,
) {
    val context = LocalContext.current
    val launcherSet = rememberPickerLaunchers(controller)
    val launchPicker = remember(context, launcherSet) {
        {
            when {
                MediaPermissionHelper.isPhotoPickerAvailable() -> launcherSet.launchPhotoPicker()
                MediaPermissionHelper.hasRequiredPermissions(context) -> launcherSet.launchDocumentPicker()
                else -> launcherSet.requestPermissions()
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Selection")
            Text("Pick videos, photos, or a mix. Invalid files are rejected immediately.")
            Button(
                onClick = launchPicker,
                modifier = Modifier.testTag("launchPickerButton"),
            ) {
                Text("Add media")
            }
            SelectionMessages(state = state, onOpenSettings = controller::openAppSettings)
            SelectedItemsList(state = state)
        }
    }
}

private val MIME_TYPES = arrayOf("image/*", "video/*")

private data class PickerLauncherSet(
    val launchPhotoPicker: () -> Unit,
    val launchDocumentPicker: () -> Unit,
    val requestPermissions: () -> Unit,
)

@Composable
private fun rememberPickerLaunchers(controller: MediaCompilerController): PickerLauncherSet {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = controller::addPickedUris,
    )
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = controller::addPickedUris,
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { granted ->
            if (granted.values.all { it }) {
                documentPickerLauncher.launch(MIME_TYPES)
            } else {
                controller.onPermissionsDenied()
            }
        },
    )
    return remember(photoPickerLauncher, documentPickerLauncher, permissionLauncher) {
        PickerLauncherSet(
            launchPhotoPicker = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            },
            launchDocumentPicker = { documentPickerLauncher.launch(MIME_TYPES) },
            requestPermissions = { permissionLauncher.launch(MediaPermissionHelper.requiredPermissions()) },
        )
    }
}

@Composable
private fun SelectionMessages(
    state: MediaCompilerUiState,
    onOpenSettings: () -> Unit,
) {
    if (state.permissionDenied) {
        OutlinedButton(onClick = onOpenSettings) {
            Text("Open app settings")
        }
    }
    state.message?.let { message ->
        Text(
            text = message,
            modifier = Modifier.testTag("messageText"),
        )
    }
    state.selectionWarning?.let { warning ->
        Text(
            text = warning,
            modifier = Modifier.testTag("selectionWarningText"),
        )
    }
}

@Composable
private fun SelectedItemsList(state: MediaCompilerUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.selectionSequence.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("selectedItem-$index"),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${index + 1}. ${item.displayName}")
                Text(item.mediaType.name.lowercase())
            }
        }
    }
}
