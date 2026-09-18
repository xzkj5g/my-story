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
class CompilePhotoVideoMixInstrumentedTest {

    @Test
    fun compilePhotoThenVideoRendersStaticThreeSecondPhotoWithContinuousAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val photo = MediaTestUtils.importAsset(context, "photo_green.jpg", MediaType.PHOTO)
            val video = MediaTestUtils.importAsset(context, "video_red_audio.mp4", MediaType.VIDEO)
            createdUris += listOf(photo, video)
            val sequence = MediaTestUtils.sequenceOf(
                photo to MediaType.PHOTO,
                video to MediaType.VIDEO,
                context = context,
            )

            val job = CompileEngine(context).compile(sequence, MediaTestUtils.defaultOutputSettings)

            assertEquals(CompileJobStatus.SUCCEEDED, job.status)
            val outputUri = Uri.parse(requireNotNull(job.outputUri))
            createdUris += outputUri
            MediaTestUtils.assertApproxDuration(context, outputUri, 4_000L)
            MediaTestUtils.assertCenterColor(context, outputUri, 500L, MediaTestUtils.colorGreen())
            MediaTestUtils.assertCenterColor(context, outputUri, 2_500L, MediaTestUtils.colorGreen())
            MediaTestUtils.assertCenterColor(context, outputUri, 3_500L, MediaTestUtils.colorRed())
            MediaTestUtils.assertAudioTrackCovers(context, outputUri, 3_900_000L)
        } finally {
            createdUris.forEach { MediaTestUtils.deleteUri(context, it) }
        }
    }
}
