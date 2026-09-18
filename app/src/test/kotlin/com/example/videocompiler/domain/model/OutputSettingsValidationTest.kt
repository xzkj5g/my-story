package com.example.videocompiler.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for US3 Acceptance Scenario 1: [OutputSettings] only accepts the fixed preset enum
 * values for `resolutionTier`, `aspectRatio`, and `frameRate` — no free-form numeric entry. This
 * is primarily enforced by the type system (the constructor only accepts enum members, so a
 * caller cannot pass an arbitrary width/height/fps); this test locks down that the preset sets
 * themselves match research.md §7 exactly, so a future change can't silently add/remove a preset
 * without a test failing.
 */
class OutputSettingsValidationTest {

    @Test
    fun `ResolutionTier defines exactly the four documented presets`() {
        assertEquals(
            setOf(
                ResolutionTier.R720P,
                ResolutionTier.R1080P,
                ResolutionTier.R2K,
                ResolutionTier.R4K,
            ),
            ResolutionTier.entries.toSet(),
        )
    }

    @Test
    fun `AspectRatio defines exactly the three documented presets`() {
        assertEquals(
            setOf(AspectRatio.RATIO_16_9, AspectRatio.RATIO_9_16, AspectRatio.RATIO_1_1),
            AspectRatio.entries.toSet(),
        )
    }

    @Test
    fun `FrameRatePreset defines exactly the three documented fps values`() {
        assertEquals(
            setOf(24, 30, 60),
            FrameRatePreset.entries.map { it.fps }.toSet(),
        )
    }

    @Test
    fun `every ResolutionTier x AspectRatio x FrameRatePreset combination constructs successfully`() {
        for (resolution in ResolutionTier.entries) {
            for (aspectRatio in AspectRatio.entries) {
                for (frameRate in FrameRatePreset.entries) {
                    // Must not throw: only defined preset enum values are ever accepted.
                    OutputSettings(resolution, aspectRatio, frameRate)
                }
            }
        }
    }
}
