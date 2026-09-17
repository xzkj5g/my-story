package com.example.videocompiler.ui

import com.example.videocompiler.domain.model.CompileJob
import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.model.SelectionSequence

data class MediaCompilerUiState(
    val selectionSequence: SelectionSequence = SelectionSequence(),
    val outputSettings: OutputSettings = OutputSettings.default(),
    val compileJob: CompileJob? = null,
    val message: String? = null,
    val selectionWarning: String? = null,
    val compileWarning: String? = null,
    val permissionDenied: Boolean = false,
)
