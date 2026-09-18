package com.example.videocompiler

import android.content.Context
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.videocompiler.domain.model.AspectRatio
import com.example.videocompiler.domain.model.CompileJobStatus
import com.example.videocompiler.domain.model.FrameRatePreset
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.model.ResolutionTier
import com.example.videocompiler.media.compiler.CompileEngine
import com.example.videocompiler.testutil.MediaTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * US3 quickstart.md Scenario 4: choose a 1080p/16:9/30fps preset, compile a sequence including a
 * portrait clip, and verify the output file is exactly 1920x1080 at 30fps (SC-003) with the
 * portrait clip's full image visible and padded — never cropped, never stretched (SC-004).
 */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class OutputSettingsInstrumentedTest {

    @Test
    fun portraitClipIsPaddedToFitThe1080p16by9Preset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val portrait = MediaTestUtils.importAsset(context, "video_red_portrait.mp4", MediaType.VIDEO)
            createdUris += portrait
            val sequence = MediaTestUtils.sequenceOf(portrait to MediaType.VIDEO, context = context)
            val settings = OutputSettings(
                resolutionTier = ResolutionTier.R1080P,
                aspectRatio = AspectRatio.RATIO_16_9,
                frameRate = FrameRatePreset.FPS_30,
            )

            val job = CompileEngine(context).compile(sequence, settings)

            assertEquals(CompileJobStatus.SUCCEEDED, job.status)
            val outputUri = Uri.parse(requireNotNull(job.outputUri))
            createdUris += outputUri

            val (width, height, frameRate) = readVideoFormat(context, outputUri)
            assertEquals(1920, width)
            assertEquals(1080, height)
            assertTrue("expected ~30fps but was $frameRate", frameRate in 25f..35f)

            // Portrait content, scaled to fit inside a landscape frame, is pillarboxed: the
            // source's red content is visible in the horizontal center, while the far-left and
            // far-right columns are padding (black), never cropped or stretched to fill them.
            MediaTestUtils.assertCenterColor(context, outputUri, 500L, MediaTestUtils.colorRed())
            assertPixelColor(context, outputUri, 500L, xFraction = 0.02f, expectedBlack = true)
            assertPixelColor(context, outputUri, 500L, xFraction = 0.98f, expectedBlack = true)
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
        }
    }

    /**
     * T053 (SC-003 coverage): a second preset combination — 9:16 portrait output at 720p/24fps —
     * to substantiate SC-003 beyond the single 1080p/16:9/30fps case above. Uses a landscape
     * source clip so the letterboxing (top/bottom padding) direction is the opposite of the
     * pillarboxing case above.
     */
    @Test
    fun landscapeClipIsLetterboxedToFitThe720p9by16At24fpsPreset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val landscape = MediaTestUtils.importAsset(context, "video_yellow_noaudio.mp4", MediaType.VIDEO)
            createdUris += landscape
            val sequence = MediaTestUtils.sequenceOf(landscape to MediaType.VIDEO, context = context)
            val settings = OutputSettings(
                resolutionTier = ResolutionTier.R720P,
                aspectRatio = AspectRatio.RATIO_9_16,
                frameRate = FrameRatePreset.FPS_24,
            )

            val job = CompileEngine(context).compile(sequence, settings)

            assertEquals(CompileJobStatus.SUCCEEDED, job.status)
            val outputUri = Uri.parse(requireNotNull(job.outputUri))
            createdUris += outputUri

            // The encoder may keep the physical buffer in its efficient (landscape) orientation
            // and instead attach a container-level `rotation-degrees` flag; a 90/270 flag means
            // width/height are swapped relative to the displayed (and requested) dimensions.
            val (rawWidth, rawHeight, frameRate) = readVideoFormat(context, outputUri)
            val rotation = readRotationDegrees(context, outputUri)
            val (width, height) = if (rotation == 90 || rotation == 270) {
                rawHeight to rawWidth
            } else {
                rawWidth to rawHeight
            }
            assertEquals(settings.outputWidth, width)
            assertEquals(settings.outputHeight, height)
            assertTrue("expected ~24fps but was $frameRate", frameRate in 20f..28f)
            MediaTestUtils.assertCenterColor(context, outputUri, 500L, MediaTestUtils.colorYellow())
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
        }
    }

    /**
     * T053 (SC-003 coverage): a third preset combination — square 1:1 output at 2K/60fps —
     * an otherwise untested resolution tier plus the highest frame rate preset. R2K (not R4K)
     * is used deliberately: the emulator's software AVC encoder is capped at Level 4
     * (~8192 macroblocks, i.e. up to roughly 1920x1088), so a true 2160x2160 (R4K square)
     * request gets silently clamped by the codec on this hardware, which would make the
     * assertion below flaky in CI/emulator environments rather than validating real app logic.
     *
     * <p>Uses a source clip that is natively 60fps: Media3's frame rate setting only ever acts
     * as a *maximum* cap for video (it drops frames to reach the cap but never invents new ones
     * to upsample), so requesting FPS_60 against a lower-native-fps source would legitimately
     * leave the output at the source's rate rather than exercising the 60fps preset.
     */
    @Test
    fun portraitClipFitsThe2k1by1At60fpsPreset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val portrait = MediaTestUtils.importAsset(context, "video_blue_portrait_60fps.mp4", MediaType.VIDEO)
            createdUris += portrait
            val sequence = MediaTestUtils.sequenceOf(portrait to MediaType.VIDEO, context = context)
            val settings = OutputSettings(
                resolutionTier = ResolutionTier.R2K,
                aspectRatio = AspectRatio.RATIO_1_1,
                frameRate = FrameRatePreset.FPS_60,
            )

            val job = CompileEngine(context).compile(sequence, settings)

            assertEquals(CompileJobStatus.SUCCEEDED, job.status)
            val outputUri = Uri.parse(requireNotNull(job.outputUri))
            createdUris += outputUri

            val (width, height, frameRate) = readVideoFormat(context, outputUri)
            assertEquals(settings.outputWidth, width)
            assertEquals(settings.outputHeight, height)
            assertEquals(settings.outputWidth, settings.outputHeight)
            assertTrue("expected ~60fps but was $frameRate", frameRate in 50f..70f)
            MediaTestUtils.assertCenterColor(context, outputUri, 500L, MediaTestUtils.colorBlue())
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
        }
    }

    /**
     * T054 (FR-007/FR-008, SC-003 Acceptance Scenario 4): a source clip natively slower than
     * the requested preset — `video_red_audio.mp4` is 30fps — compiled against `FPS_60`. Media3
     * alone only ever *caps* frame rate downward, so this exercises `CompileEngine`'s
     * frame-duplication upsampling path and asserts the output genuinely reaches ~60fps rather
     * than silently staying at the source's native 30fps.
     */
    @Test
    fun slowerSourceIsUpsampledToTheRequestedHigherFpsPreset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val source = MediaTestUtils.importAsset(context, "video_red_audio.mp4", MediaType.VIDEO)
            createdUris += source
            val sequence = MediaTestUtils.sequenceOf(source to MediaType.VIDEO, context = context)
            val settings = OutputSettings(
                resolutionTier = ResolutionTier.R1080P,
                aspectRatio = AspectRatio.RATIO_16_9,
                frameRate = FrameRatePreset.FPS_60,
            )

            val job = CompileEngine(context).compile(sequence, settings)

            assertEquals(CompileJobStatus.SUCCEEDED, job.status)
            val outputUri = Uri.parse(requireNotNull(job.outputUri))
            createdUris += outputUri

            val (_, _, frameRate) = readVideoFormat(context, outputUri)
            assertTrue("expected ~60fps but was $frameRate", frameRate in 50f..70f)
            assertEquals(actualFrameCount(context, outputUri).toLong(), 60L, 6L)
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
        }
    }

    /** Asserts [actual] is within [tolerance] of [expected]. */
    private fun assertEquals(expected: Long, actual: Long, tolerance: Long) {
        assertTrue(
            "expected $actual to be within $tolerance of $expected",
            kotlin.math.abs(actual - expected) <= tolerance,
        )
    }

    /** Counts actual decoded video frames in [uri] by advancing a [MediaExtractor] to the end. */
    private fun actualFrameCount(context: Context, uri: Uri): Int {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val videoTrackIndex = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            extractor.selectTrack(videoTrackIndex)
            var count = 0
            val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
            while (extractor.readSampleData(buffer, 0) >= 0) {
                count++
                extractor.advance()
            }
            count
        } finally {
            extractor.release()
        }
    }

    private fun readVideoFormat(context: Context, uri: Uri): Triple<Int, Int, Float> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val format = (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .first { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            Triple(
                format.getInteger(MediaFormat.KEY_WIDTH),
                format.getInteger(MediaFormat.KEY_HEIGHT),
                if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                } else {
                    30f
                },
            )
        } finally {
            extractor.release()
        }
    }

    /** Returns the container-level rotation flag (0 if absent), e.g. 90 for a rotated buffer. */
    private fun readRotationDegrees(context: Context, uri: Uri): Int {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val format = (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .first { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            if (format.containsKey("rotation-degrees")) format.getInteger("rotation-degrees") else 0
        } finally {
            extractor.release()
        }
    }

    private fun assertPixelColor(
        context: Context,
        uri: Uri,
        timeMs: Long,
        xFraction: Float,
        expectedBlack: Boolean,
    ) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val frame = requireNotNull(
                retriever.getFrameAtTime(timeMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST),
            )
            val x = (frame.width * xFraction).toInt().coerceIn(0, frame.width - 1)
            val pixel = frame.getPixel(x, frame.height / 2)
            val isNearBlack = Color.red(pixel) < 40 &&
                Color.green(pixel) < 40 &&
                Color.blue(pixel) < 40
            frame.recycle()
            assertEquals(
                "expected padding-black=$expectedBlack at x-fraction $xFraction but pixel was $pixel",
                expectedBlack,
                isNearBlack,
            )
        } finally {
            retriever.release()
        }
    }
}
