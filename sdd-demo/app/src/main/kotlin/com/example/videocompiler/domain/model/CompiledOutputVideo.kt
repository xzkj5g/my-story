package com.example.videocompiler.domain.model

/**
 * The single video file produced by a successfully [CompileJobStatus.SUCCEEDED] [CompileJob]
 * (FR-006, FR-011).
 *
 * @property uri `MediaStore` location (`Movies/VideoCompiler/…`, research.md §8).
 * @property resolutionTier Same enum as the originating [OutputSettings]; MUST exactly match
 *   the settings used to produce it (FR-007, SC-003).
 * @property aspectRatio Same enum as the originating [OutputSettings]; MUST exactly match the
 *   settings used to produce it (FR-007, SC-003).
 * @property frameRate Same enum as the originating [OutputSettings]; MUST exactly match the
 *   settings used to produce it (FR-007, SC-003).
 * @property itemOrder Ordered list of [SourceMediaItem] references; MUST match the originating
 *   `SelectionSequence.items` order exactly (SC-002).
 */
data class CompiledOutputVideo(
    val uri: String,
    val resolutionTier: ResolutionTier,
    val aspectRatio: AspectRatio,
    val frameRate: FrameRatePreset,
    val itemOrder: List<SourceMediaItem>,
) {
    init {
        require(itemOrder.isNotEmpty()) { "itemOrder must not be empty (FR-012 allows 1 item)" }
    }

    /**
     * Reconstructs the [OutputSettings] this output was produced from, for equality
     * comparisons against the job's original settings (FR-007, SC-003).
     */
    fun toOutputSettings(): OutputSettings = OutputSettings(resolutionTier, aspectRatio, frameRate)
}
