package com.example.videocompiler.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.videocompiler.domain.model.CompileJob
import com.example.videocompiler.domain.model.CompileJobStatus
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.usecase.MediaValidationResult
import com.example.videocompiler.domain.usecase.ValidateSourceMediaItem
import com.example.videocompiler.service.CompileForegroundService
import com.example.videocompiler.service.CompileSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MediaCompilerController(
    context: Context,
    private val validateSourceMediaItem: ValidateSourceMediaItem =
        ValidateSourceMediaItem(context.applicationContext),
    private val deviceResourceMonitor: DeviceResourceMonitor =
        DeviceResourceMonitor(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(MediaCompilerUiState())

    val uiState: StateFlow<MediaCompilerUiState> = _uiState.asStateFlow()

    init {
        refreshSelectionWarning()
        scope.launch {
            CompileSessionStore.jobState.collectLatest { job ->
                _uiState.value = _uiState.value.copy(
                    compileJob = job,
                    compileWarning = when {
                        job?.status == CompileJobStatus.RUNNING -> deviceResourceMonitor.warningMessage()
                        job?.status == CompileJobStatus.FAILED -> job.failureReason
                        else -> _uiState.value.compileWarning
                    },
                    message = job.toSuccessMessage() ?: _uiState.value.message,
                )
            }
        }
    }

    fun addPickedUris(uris: List<Uri>) {
        uris.forEach(::addPickedUri)
        refreshSelectionWarning()
    }

    fun addPickedUri(uri: Uri) {
        val mediaType = appContext.detectMediaType(uri) ?: run {
            setMessage("Unsupported file type: ${uri.lastPathSegment.orEmpty()}")
            return
        }
        when (val result = validateSourceMediaItem(uri, mediaType)) {
            is MediaValidationResult.Valid -> {
                val updatedSequence = _uiState.value.selectionSequence.append(result.item)
                _uiState.value = _uiState.value.copy(
                    selectionSequence = updatedSequence,
                    message = null,
                    permissionDenied = false,
                )
            }
            is MediaValidationResult.Invalid -> {
                val fileName = result.displayName ?: uri.lastPathSegment.orEmpty()
                setMessage("$fileName was rejected: ${result.reason.name.lowercase().replace('_', ' ')}")
            }
        }
    }

    /** US2 (FR-003): reorders the displayed sequence immediately; used by `ui/sequence`. */
    fun moveItem(fromIndex: Int, toIndex: Int) {
        _uiState.value = _uiState.value.copy(
            selectionSequence = _uiState.value.selectionSequence.move(fromIndex, toIndex),
        )
    }

    /** US2 (FR-004): removes an item, keeping remaining items' relative order with no gap. */
    fun removeItem(index: Int) {
        _uiState.value = _uiState.value.copy(
            selectionSequence = _uiState.value.selectionSequence.remove(index),
        )
        refreshSelectionWarning()
    }

    /** US3 (FR-005): applies the user's chosen resolution/aspect ratio/frame rate preset. */
    fun setOutputSettings(settings: OutputSettings) {
        _uiState.value = _uiState.value.copy(outputSettings = settings)
    }

    fun onPermissionsDenied() {
        _uiState.value = _uiState.value.copy(
            permissionDenied = true,
            message = "Media access was denied. Retry or open settings to allow selecting videos and photos.",
        )
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", appContext.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun startCompile() {
        val sequence = _uiState.value.selectionSequence
        if (sequence.items.isEmpty()) {
            setMessage("Select at least one valid video or photo before compiling.")
            return
        }
        _uiState.value = _uiState.value.copy(
            compileWarning = deviceResourceMonitor.warningMessage(),
            message = null,
        )
        CompileForegroundService.start(
            context = appContext,
            sequence = sequence,
            settings = _uiState.value.outputSettings,
        )
    }

    fun cancelCompile() {
        CompileForegroundService.cancel(appContext)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun dispose() {
        scope.cancel()
    }

    private fun refreshSelectionWarning() {
        _uiState.value = _uiState.value.copy(
            selectionWarning = deviceResourceMonitor.warningMessage(),
        )
    }

    private fun setMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }
}

private fun Context.detectMediaType(uri: Uri): MediaType? {
    val mimeType = contentResolver.getType(uri) ?: return null
    return when {
        mimeType.startsWith("video/") -> MediaType.VIDEO
        mimeType.startsWith("image/") -> MediaType.PHOTO
        else -> null
    }
}

private fun CompileJob?.toSuccessMessage(): String? =
    if (this?.status == CompileJobStatus.SUCCEEDED) {
        "Compilation complete: ${outputUri.orEmpty()}"
    } else {
        null
    }
