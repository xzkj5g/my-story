package com.example.videocompiler.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for US3: combining [ResolutionTier] + [AspectRatio] must derive the exact
 * `outputWidth x outputHeight` per research.md §7's preset table (e.g., 1080p + 9:16 -> 1080x1920).
 */
class OutputSettingsDimensionsTest {

    private fun settings(resolution: ResolutionTier, aspectRatio: AspectRatio) =
        OutputSettings(resolution, aspectRatio, FrameRatePreset.FPS_30)

    @Test
    fun `1080p 16-9 is 1920x1080`() {
        val s = settings(ResolutionTier.R1080P, AspectRatio.RATIO_16_9)
        assertEquals(1920, s.outputWidth)
        assertEquals(1080, s.outputHeight)
    }

    @Test
    fun `1080p 9-16 is 1080x1920`() {
        val s = settings(ResolutionTier.R1080P, AspectRatio.RATIO_9_16)
        assertEquals(1080, s.outputWidth)
        assertEquals(1920, s.outputHeight)
    }

    @Test
    fun `720p 16-9 is 1280x720`() {
        val s = settings(ResolutionTier.R720P, AspectRatio.RATIO_16_9)
        assertEquals(1280, s.outputWidth)
        assertEquals(720, s.outputHeight)
    }

    @Test
    fun `2K 16-9 is 2560x1440`() {
        val s = settings(ResolutionTier.R2K, AspectRatio.RATIO_16_9)
        assertEquals(2560, s.outputWidth)
        assertEquals(1440, s.outputHeight)
    }

    @Test
    fun `4K 16-9 is 3840x2160`() {
        val s = settings(ResolutionTier.R4K, AspectRatio.RATIO_16_9)
        assertEquals(3840, s.outputWidth)
        assertEquals(2160, s.outputHeight)
    }

    @Test
    fun `1080p 1-1 is square`() {
        val s = settings(ResolutionTier.R1080P, AspectRatio.RATIO_1_1)
        assertEquals(s.outputWidth, s.outputHeight)
    }
}
