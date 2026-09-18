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

@RunWith(AndroidJUnit4::class)
@UnstableApi
class CompileSequenceInstrumentedTest {

    @Test
    fun compileTwoVideosKeepsSelectionOrderWithoutGap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val first = MediaTestUtils.importAsset(context, "video_red_audio.mp4", MediaType.VIDEO)
            val second = MediaTestUtils.importAsset(context, "video_blue_audio.mp4", MediaType.VIDEO)
            createdUris += listOf(first, second)
            val sequence = MediaTestUtils.sequenceOf(
                first to MediaType.VIDEO,
                second to MediaType.VIDEO,
                context = context,
            )

            val job = CompileEngine(context).compile(sequence, MediaTestUtils.defaultOutputSettings)

            assertEquals(CompileJobStatus.SUCCEEDED, job.status)
            val outputUri = Uri.parse(requireNotNull(job.outputUri))
            createdUris += outputUri
            // T052: CompileJob must expose a CompiledOutputVideo matching the settings/order used.
            val compiledOutput = requireNotNull(job.compiledOutput)
            assertEquals(outputUri.toString(), compiledOutput.uri)
            assertEquals(MediaTestUtils.defaultOutputSettings, compiledOutput.toOutputSettings())
            assertEquals(sequence.items, compiledOutput.itemOrder)
            MediaTestUtils.assertSavedUnderVideoCompiler(context, outputUri)
            MediaTestUtils.assertApproxDuration(context, outputUri, 2_000L)
            MediaTestUtils.assertCenterColor(context, outputUri, 500L, MediaTestUtils.colorRed())
            MediaTestUtils.assertCenterColor(context, outputUri, 1_500L, MediaTestUtils.colorBlue())
            MediaTestUtils.assertAudioTrackCovers(context, outputUri, 1_900_000L)
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
        }
    }
}
