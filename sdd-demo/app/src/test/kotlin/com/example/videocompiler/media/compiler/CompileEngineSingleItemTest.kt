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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompileEngineSingleItemTest {

    private val defaultSettings = OutputSettings(
        resolutionTier = ResolutionTier.R1080P,
        aspectRatio = AspectRatio.RATIO_16_9,
        frameRate = FrameRatePreset.FPS_30,
    )

    @Test
    fun `single video item becomes one segment with its full native duration`() {
        val item = SourceMediaItem(
            uri = "content://media/solo.mp4",
            mediaType = MediaType.VIDEO,
            displayName = "solo.mp4",
            durationMs = 4_500L,
            width = 1920,
            height = 1080,
            frameRate = 60f,
            hasAudioTrack = true,
            validationState = ValidationState.VALID,
        )

        val plan = CompositionPlanner.plan(SelectionSequence(listOf(item)), defaultSettings)

        assertEquals(1, plan.segments.size)
        assertEquals(item, plan.segments.single().source)
        assertEquals(4_500L, plan.segments.single().durationMs)
        assertFalse(plan.segments.single().requiresSilentAudio)
    }

    @Test
    fun `single photo item becomes one three-second static segment`() {
        val item = SourceMediaItem(
            uri = "content://media/solo.jpg",
            mediaType = MediaType.PHOTO,
            displayName = "solo.jpg",
            durationMs = null,
            width = 1080,
            height = 1350,
            frameRate = null,
            hasAudioTrack = false,
            validationState = ValidationState.VALID,
        )

        val plan = CompositionPlanner.plan(SelectionSequence(listOf(item)), defaultSettings)

        assertEquals(1, plan.segments.size)
        assertEquals(SourceMediaItem.PHOTO_DISPLAY_DURATION_MS, plan.segments.single().durationMs)
        assertTrue(plan.segments.single().requiresSilentAudio)
    }
}
