package com.example.videocompiler.domain.model

/** Lifecycle status of a [CompileJob] (FR-010, FR-013). */
enum class CompileJobStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/**
 * Represents one in-progress or completed compilation run, tying a [SelectionSequence] and
 * [OutputSettings] together with runtime status. Drives the foreground service / progress UI
 * (FR-013).
 *
 * A [CompileJob] never transitions away from a terminal state ([CompileJobStatus.SUCCEEDED],
 * [CompileJobStatus.FAILED], or [CompileJobStatus.CANCELLED]); see `data-model.md`'s Lifecycle
 * section for the full state transition diagram.
 *
 * @property sequence Snapshot of the ordered media items being compiled; taken at job start so
 *   later selection edits don't affect an in-flight job.
 * @property outputSettings Snapshot of the target format for this job.
 * @property status Current lifecycle status.
 * @property progressPercent Progress in the range 0-100; drives the foreground service
 *   notification.
 * @property failureReason Set when [status] is [CompileJobStatus.FAILED] (e.g., "source media
 *   item became unavailable" per FR-016, "insufficient storage").
 * @property outputUri Set when [status] is [CompileJobStatus.SUCCEEDED]; the saved `MediaStore`
 *   location of the Compiled Output Video (FR-011).
 * @property compiledOutput Set when [status] is [CompileJobStatus.SUCCEEDED]; the
 *   [CompiledOutputVideo] produced by this job, per data-model.md's `CompileJob 1──0..1
 *   CompiledOutputVideo` relationship (FR-007, SC-002, SC-003).
 */
data class CompileJob(
    val sequence: SelectionSequence,
    val outputSettings: OutputSettings,
    val status: CompileJobStatus,
    val progressPercent: Int,
    val failureReason: String? = null,
    val outputUri: String? = null,
    val compiledOutput: CompiledOutputVideo? = null,
) {
    init {
        require(progressPercent in 0..100) { "progressPercent must be in 0..100" }
        require(status != CompileJobStatus.FAILED || failureReason != null) {
            "a FAILED job must have a failureReason"
        }
        require(status != CompileJobStatus.SUCCEEDED || outputUri != null) {
            "a SUCCEEDED job must have an outputUri"
        }
        require(status != CompileJobStatus.SUCCEEDED || compiledOutput != null) {
            "a SUCCEEDED job must have a compiledOutput"
        }
    }

    /**
     * Returns a copy of this job transitioned to a new [status], enforcing that a [CompileJob]
     * never transitions away from a terminal state.
     */
    fun transitionTo(
        newStatus: CompileJobStatus,
        progressPercent: Int = this.progressPercent,
        failureReason: String? = null,
        outputUri: String? = null,
        compiledOutput: CompiledOutputVideo? = null,
    ): CompileJob {
        check(status == CompileJobStatus.RUNNING) {
            "cannot transition a CompileJob out of a terminal state ($status)"
        }
        return copy(
            status = newStatus,
            progressPercent = progressPercent,
            failureReason = failureReason,
            outputUri = outputUri,
            compiledOutput = compiledOutput,
        )
    }
}
