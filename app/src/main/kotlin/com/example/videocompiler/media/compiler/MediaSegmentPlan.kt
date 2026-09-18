package com.example.videocompiler.media.compiler

import com.example.videocompiler.domain.model.SourceMediaItem

/**
 * A pure planning-layer description of one output segment before Media3 encoding begins.
 *
 * This layer exists so ordering/duration/audio-gap decisions can be unit-tested on the JVM
 * without invoking Media3, MediaCodec, or Android framework media components.
 */
data class MediaSegmentPlan(
    val source: SourceMediaItem,
    val sequenceIndex: Int,
    val startTimeMs: Long,
    val durationMs: Long,
    val endTimeMs: Long,
    val requiresSilentAudio: Boolean,
)

data class CompositionPlan(
    val segments: List<MediaSegmentPlan>,
) {
    val totalDurationMs: Long
        get() = segments.lastOrNull()?.endTimeMs ?: 0L
}
