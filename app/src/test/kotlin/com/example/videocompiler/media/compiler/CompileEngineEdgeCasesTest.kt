package com.example.videocompiler.media.compiler

import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.model.SelectionSequence
import com.example.videocompiler.domain.model.SourceMediaItem
import com.example.videocompiler.domain.model.ValidationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Polish-phase edge case coverage (T049) for the pure planning logic backing `CompileEngine`:
 * an empty [SelectionSequence] must be blocked before any encode work starts, and a photo-only
 * sequence (no video items at all) must still plan correctly.
 */
class CompileEngineEdgeCasesTest {

    private val settings = OutputSettings.default()

    private fun photoItem(name: String) = SourceMediaItem(
        uri = "content://media/$name",
        mediaType = MediaType.PHOTO,
        displayName = name,
        durationMs = null,
        width = 1080,
        height = 1080,
        frameRate = null,
        hasAudioTrack = false,
        validationState = ValidationState.VALID,
    )

    @Test(expected = IllegalArgumentException::class)
    fun `planning an empty SelectionSequence is blocked`() {
        CompositionPlanner.plan(SelectionSequence(emptyList()), settings)
    }

    @Test
    fun `a photo-only sequence plans one segment per photo with the fixed display duration`() {
        val sequence = SelectionSequence(listOf(photoItem("a"), photoItem("b"), photoItem("c")))

        val plan = CompositionPlanner.plan(sequence, settings)

        assertEquals(3, plan.segments.size)
        plan.segments.forEach { segment ->
            assertEquals(SourceMediaItem.PHOTO_DISPLAY_DURATION_MS, segment.durationMs)
            assertTrue("every photo segment must synthesize silent audio", segment.requiresSilentAudio)
        }
        // Segments remain back-to-back with no gap (FR-006), even for an all-photo sequence.
        assertEquals(0L, plan.segments[0].startTimeMs)
        assertEquals(SourceMediaItem.PHOTO_DISPLAY_DURATION_MS, plan.segments[1].startTimeMs)
        assertEquals(2 * SourceMediaItem.PHOTO_DISPLAY_DURATION_MS, plan.segments[2].startTimeMs)
    }
}
