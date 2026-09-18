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
