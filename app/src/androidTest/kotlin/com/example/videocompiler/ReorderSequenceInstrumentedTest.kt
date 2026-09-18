package com.example.videocompiler

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.videocompiler.domain.model.CompileJobStatus
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.media.compiler.CompileEngine
import com.example.videocompiler.testutil.MediaTestUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * US2 quickstart.md Scenario 3: select 3 videos (A, B, C), move C to the first position, remove
 * B, compile, and verify the output plays C then A only (FR-003, FR-004, FR-006).
 */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class ReorderSequenceInstrumentedTest {

    @Test
    fun reorderAndRemoveProducesExpectedOutputOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val a = MediaTestUtils.importAsset(context, "video_red_audio.mp4", MediaType.VIDEO)
            val b = MediaTestUtils.importAsset(context, "video_yellow_noaudio.mp4", MediaType.VIDEO)
            val c = MediaTestUtils.importAsset(context, "video_blue_audio.mp4", MediaType.VIDEO)
            createdUris += listOf(a, b, c)

            // Selection order: A, B, C.
            var sequence = MediaTestUtils.sequenceOf(
                a to MediaType.VIDEO,
                b to MediaType.VIDEO,
                c to MediaType.VIDEO,
                context = context,
            )

            // Move C (index 2) to first position -> [C, A, B].
            sequence = sequence.move(fromIndex = 2, toIndex = 0)
            // Remove B (now at index 2) -> [C, A].
            sequence = sequence.remove(index = 2)

            val job = CompileEngine(context).compile(sequence, MediaTestUtils.defaultOutputSettings)

            assertEquals(CompileJobStatus.SUCCEEDED, job.status)
            val outputUri = Uri.parse(requireNotNull(job.outputUri))
            createdUris += outputUri
            MediaTestUtils.assertApproxDuration(context, outputUri, 2_000L)
            // C (blue) plays first, then A (red); B (yellow) must not appear anywhere.
            MediaTestUtils.assertCenterColor(context, outputUri, 500L, MediaTestUtils.colorBlue())
            MediaTestUtils.assertCenterColor(context, outputUri, 1_500L, MediaTestUtils.colorRed())
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
        }
    }
}
