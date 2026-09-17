package com.example.videocompiler

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.videocompiler.domain.model.CompileJobStatus
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.domain.model.SelectionSequence
import com.example.videocompiler.media.compiler.CompileEngine
import com.example.videocompiler.testutil.MediaTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On the headless Android 34 emulator, shell-side deletion of a freshly inserted `MediaStore`
 * image row does not reliably invalidate the target app's subsequent read access quickly enough
 * for a deterministic test. This test exercises the same FR-016 contract boundary with a stale,
 * no-longer-resolvable content URI, which is the exact condition `RevalidateSourceMediaItem`
 * must detect before compilation begins.
 */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class RevalidationInstrumentedTest {

    @Test
    fun deletedSelectedItemFailsCompileBeforeTransformationStarts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MediaTestUtils.grantRuntimePermissions()
        val createdUris = mutableListOf<Uri>()
        try {
            val photo = MediaTestUtils.importAsset(context, "photo_green.jpg", MediaType.PHOTO)
            val video = MediaTestUtils.importAsset(context, "video_red_audio.mp4", MediaType.VIDEO)
            createdUris += listOf(photo, video)

            val validPhoto = MediaTestUtils.validateItem(context, photo, MediaType.PHOTO)
            val validVideo = MediaTestUtils.validateItem(context, video, MediaType.VIDEO)
            val stalePhoto = validPhoto.copy(
                uri = "content://media/external/images/media/999999999",
            )
            val sequence = SelectionSequence(listOf(stalePhoto, validVideo))

            val finalJob = CompileEngine(context).compile(sequence, MediaTestUtils.defaultOutputSettings)

            assertEquals(CompileJobStatus.FAILED, finalJob.status)
            assertTrue(finalJob.failureReason.orEmpty().contains(validPhoto.displayName))
            assertNull(finalJob.outputUri)
        } finally {
            createdUris.forEach {
                try {
                    MediaTestUtils.deleteUri(context, it)
                } catch (_: Throwable) {
                    // Source cleanup best-effort only.
                }
            }
        }
    }
}
