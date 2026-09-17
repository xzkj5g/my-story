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

class CompileEngineSilenceTest {

    private val defaultSettings = OutputSettings(
        resolutionTier = ResolutionTier.R1080P,
        aspectRatio = AspectRatio.RATIO_16_9,
        frameRate = FrameRatePreset.FPS_30,
    )

    @Test
    fun `planner flags photo and video-only segments for synthetic silence`() {
        val silentVideo = video(name = "silent.mp4", hasAudioTrack = false)
        val voicedVideo = video(name = "voiced.mp4", hasAudioTrack = true)
        val photo = photo(name = "still.jpg")

        val plan = CompositionPlanner.plan(
            SelectionSequence(listOf(silentVideo, voicedVideo, photo)),
            defaultSettings,
        )

        assertEquals(
            listOf(true, false, true),
            plan.segments.map { it.requiresSilentAudio },
        )
        assertEquals(
            listOf(1_500L, 1_500L, SourceMediaItem.PHOTO_DISPLAY_DURATION_MS),
            plan.segments.map { it.durationMs },
        )
    }

    private fun video(name: String, hasAudioTrack: Boolean) = SourceMediaItem(
        uri = "content://media/$name",
        mediaType = MediaType.VIDEO,
        displayName = name,
        durationMs = 1_500L,
        width = 1280,
        height = 720,
        frameRate = 30f,
        hasAudioTrack = hasAudioTrack,
        validationState = ValidationState.VALID,
    )

    private fun photo(name: String) = SourceMediaItem(
        uri = "content://media/$name",
        mediaType = MediaType.PHOTO,
        displayName = name,
        durationMs = null,
        width = 720,
        height = 720,
        frameRate = null,
        hasAudioTrack = false,
        validationState = ValidationState.VALID,
    )
}
