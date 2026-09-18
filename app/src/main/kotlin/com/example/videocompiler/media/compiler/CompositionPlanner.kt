package com.example.videocompiler.media.compiler

import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.model.SelectionSequence
import com.example.videocompiler.domain.model.SourceMediaItem

/**
 * Pure planning logic for `CompileEngine`: maps a [SelectionSequence] to ordered segment
 * decisions the encoder must honor.
 */
object CompositionPlanner {

    fun plan(sequence: SelectionSequence, settings: OutputSettings): CompositionPlan {
        require(sequence.items.isNotEmpty()) { "compile requires at least one source item" }
        val segments = buildList {
            var nextStartMs = 0L
            sequence.items.forEachIndexed { index, item ->
                val durationMs = item.segmentDurationMs(settings)
                add(
                    MediaSegmentPlan(
                        source = item,
                        sequenceIndex = index,
                        startTimeMs = nextStartMs,
                        durationMs = durationMs,
                        endTimeMs = nextStartMs + durationMs,
                        requiresSilentAudio = item.requiresSilentAudio(),
                    ),
                )
                nextStartMs += durationMs
            }
        }
        return CompositionPlan(segments)
    }

    private fun SourceMediaItem.segmentDurationMs(settings: OutputSettings): Long = when (mediaType) {
        com.example.videocompiler.domain.model.MediaType.PHOTO -> SourceMediaItem.PHOTO_DISPLAY_DURATION_MS
        com.example.videocompiler.domain.model.MediaType.VIDEO -> {
            requireNotNull(durationMs) {
                "video ${displayName.ifBlank { uri }} must provide duration for ${settings.frameRate}"
            }
        }
    }

    private fun SourceMediaItem.requiresSilentAudio(): Boolean =
        mediaType == com.example.videocompiler.domain.model.MediaType.PHOTO || !hasAudioTrack
}
