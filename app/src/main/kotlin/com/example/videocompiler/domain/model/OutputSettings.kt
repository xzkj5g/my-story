package com.example.videocompiler.domain.model

/** Output resolution "tier"; combined with [AspectRatio] to derive final pixel dimensions. */
enum class ResolutionTier {
    R720P,
    R1080P,
    R2K,
    R4K,
}

/** Output aspect ratio preset (FR-005). */
enum class AspectRatio {
    RATIO_16_9,
    RATIO_9_16,
    RATIO_1_1,
}

/** Output frame rate preset (FR-005). */
enum class FrameRatePreset(val fps: Int) {
    FPS_24(24),
    FPS_30(30),
    FPS_60(60),
}

/**
 * The user-chosen combination of resolution, aspect ratio, and frame rate presets that the
 * Compiled Output Video must conform to (FR-005, FR-007). All three fields are required and
 * MUST be one of the defined preset enum values; no free-form/custom numeric entry in v1 (see
 * spec.md Clarifications).
 */
data class OutputSettings(
    val resolutionTier: ResolutionTier,
    val aspectRatio: AspectRatio,
    val frameRate: FrameRatePreset,
) {
    /**
     * Derives the effective output pixel dimensions by combining [resolutionTier] and
     * [aspectRatio], per research.md §7's preset table. The "long edge" pixel count for each
     * tier is scaled to fit the target aspect ratio's shorter edge.
     */
    val outputWidth: Int
        get() = dimensions().first

    val outputHeight: Int
        get() = dimensions().second

    private fun dimensions(): Pair<Int, Int> {
        // Long-edge pixel count per resolution tier (research.md §7).
        val longEdge = when (resolutionTier) {
            ResolutionTier.R720P -> 1280
            ResolutionTier.R1080P -> 1920
            ResolutionTier.R2K -> 2560
            ResolutionTier.R4K -> 3840
        }
        return when (aspectRatio) {
            AspectRatio.RATIO_16_9 -> longEdge to (longEdge * 9 / 16)
            AspectRatio.RATIO_9_16 -> (longEdge * 9 / 16) to longEdge
            AspectRatio.RATIO_1_1 -> {
                val square = longEdge * 9 / 16
                square to square
            }
        }
    }

    companion object {
        fun default(): OutputSettings = OutputSettings(
            resolutionTier = ResolutionTier.R1080P,
            aspectRatio = AspectRatio.RATIO_16_9,
            frameRate = FrameRatePreset.FPS_30,
        )
    }
}
