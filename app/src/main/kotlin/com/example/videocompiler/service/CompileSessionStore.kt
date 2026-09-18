package com.example.videocompiler.service

import com.example.videocompiler.domain.model.CompileJob
import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.model.SelectionSequence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CompileSessionStore {

    data class PendingRequest(
        val sequence: SelectionSequence,
        val settings: OutputSettings,
    )

    private val lock = Any()
    private var pendingRequest: PendingRequest? = null
    private val _jobState = MutableStateFlow<CompileJob?>(null)

    val jobState: StateFlow<CompileJob?> = _jobState.asStateFlow()

    fun queueRequest(sequence: SelectionSequence, settings: OutputSettings) {
        synchronized(lock) {
            pendingRequest = PendingRequest(sequence, settings)
        }
    }

    fun takePendingRequest(): PendingRequest? = synchronized(lock) {
        pendingRequest.also { pendingRequest = null }
    }

    fun updateJob(job: CompileJob?) {
        _jobState.value = job
    }
}
