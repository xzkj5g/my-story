package com.example.videocompiler.media.compiler

import com.example.videocompiler.domain.model.AspectRatio
import com.example.videocompiler.domain.model.FrameRatePreset
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.model.ResolutionTier
import com.example.videocompiler.domain.model.SelectionSequence
import com.example.videocompiler.domain.model.SourceMediaItem
import com.example.videocompiler.domain.model.ValidationState
import org.junit.Assert.assertEquals
import org.junit.Test

class CompileEngineOrderTest {

    private val defaultSettings = OutputSettings(
        resolutionTier = ResolutionTier.R1080P,
        aspectRatio = AspectRatio.RATIO_16_9,
        frameRate = FrameRatePreset.FPS_30,
    )

    @Test
    fun `planner preserves source order and stitches segments back to back`() {
        val videoA = video(name = "video-a.mp4", durationMs = 1_200L, hasAudioTrack = true)
        val photo = photo(name = "photo-b.jpg")
        val videoC = video(name = "video-c.mp4", durationMs = 2_400L, hasAudioTrack = true)

        val plan = CompositionPlanner.plan(
            SelectionSequence(listOf(videoA, photo, videoC)),
            defaultSettings,
        )

        assertEquals(listOf(videoA, photo, videoC), plan.segments.map { it.source })
        assertEquals(listOf(0L, 1_200L, 4_200L), plan.segments.map { it.startTimeMs })
        assertEquals(
            listOf(1_200L, 3_000L, 2_400L),
            plan.segments.map { it.durationMs },
        )
        assertEquals(listOf(1_200L, 4_200L, 6_600L), plan.segments.map { it.endTimeMs })
        assertEquals(6_600L, plan.totalDurationMs)
    }

    private fun video(name: String, durationMs: Long, hasAudioTrack: Boolean) = SourceMediaItem(
        uri = "content://media/$name",
        mediaType = MediaType.VIDEO,
        displayName = name,
        durationMs = durationMs,
        width = 1920,
        height = 1080,
        frameRate = 30f,
        hasAudioTrack = hasAudioTrack,
        validationState = ValidationState.VALID,
    )

    private fun photo(name: String) = SourceMediaItem(
        uri = "content://media/$name",
        mediaType = MediaType.PHOTO,
        displayName = name,
        durationMs = null,
        width = 1440,
        height = 1080,
        frameRate = null,
        hasAudioTrack = false,
        validationState = ValidationState.VALID,
    )
}
