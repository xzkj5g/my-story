package com.example.videocompiler.testutil

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.videocompiler.domain.model.CompileJob
import com.example.videocompiler.domain.model.CompileJobStatus
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.model.SelectionSequence
import com.example.videocompiler.domain.usecase.MediaValidationResult
import com.example.videocompiler.domain.usecase.ValidateSourceMediaItem
import com.example.videocompiler.service.CompileSessionStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlin.math.abs

object MediaTestUtils {

    val defaultOutputSettings: OutputSettings = OutputSettings.default()

    fun importAsset(context: Context, assetName: String, mediaType: MediaType): Uri {
        val collection = when (mediaType) {
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaType.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val displayName = "${System.nanoTime()}-$assetName"
        val mimeType = when (mediaType) {
            MediaType.VIDEO -> "video/mp4"
            MediaType.PHOTO -> "image/jpeg"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    when (mediaType) {
                        MediaType.VIDEO -> "Movies/MediaSequenceCompilerTests"
                        MediaType.PHOTO -> "Pictures/MediaSequenceCompilerTests"
                    },
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(context.contentResolver.insert(collection, values))
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetName).use { input ->
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output)
                input.copyTo(output)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
        return uri
    }

    fun validateItem(context: Context, uri: Uri, mediaType: MediaType) =
        when (val result = ValidateSourceMediaItem(context)(uri, mediaType)) {
            is MediaValidationResult.Valid -> result.item
            is MediaValidationResult.Invalid ->
                error("Expected valid $mediaType asset for $uri but was $result")
        }

    fun sequenceOf(vararg pairs: Pair<Uri, MediaType>, context: Context): SelectionSequence =
        SelectionSequence(
            pairs.map { (uri, mediaType) -> validateItem(context, uri, mediaType) },
        )

    fun assertApproxDuration(context: Context, uri: Uri, expectedMs: Long, toleranceMs: Long = 700L) {
        val actual = readDurationMs(context, uri)
        assertTrue("expected duration near $expectedMs ms but was $actual", abs(actual - expectedMs) <= toleranceMs)
    }

    fun readDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                ?: error("Missing duration for $uri")
        } finally {
            retriever.release()
        }
    }

    fun assertCenterColor(
        context: Context,
        uri: Uri,
        timeMs: Long,
        expected: Int,
        tolerance: Int = 150,
    ) {
        val frame = readFrame(context, uri, timeMs)
        val pixel = frame.getPixel(frame.width / 2, frame.height / 2)
        assertTrue(
            "Expected color ${Color.red(expected)},${Color.green(expected)},${Color.blue(expected)} " +
                "at $timeMs ms but was ${Color.red(pixel)},${Color.green(pixel)},${Color.blue(pixel)}",
            abs(Color.red(expected) - Color.red(pixel)) <= tolerance &&
                abs(Color.green(expected) - Color.green(pixel)) <= tolerance &&
                abs(Color.blue(expected) - Color.blue(pixel)) <= tolerance,
        )
        frame.recycle()
    }

    fun assertAudioTrackCovers(context: Context, uri: Uri, atLeastDurationUs: Long) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val audioDuration = (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?.getLong(MediaFormat.KEY_DURATION)
            assertNotNull("Expected audio track for $uri", audioDuration)
            assertTrue(
                "expected audio duration >= $atLeastDurationUs us but was $audioDuration",
                audioDuration!! >= atLeastDurationUs,
            )
        } finally {
            extractor.release()
        }
    }

    fun assertSavedUnderVideoCompiler(context: Context, uri: Uri) {
        val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            assertTrue(cursor.moveToFirst())
            val relativePath = cursor.getString(0)
            assertTrue("Expected Movies/VideoCompiler path but was $relativePath", relativePath.contains("Movies/VideoCompiler"))
        }
    }

    fun waitForTerminalJob(timeoutMs: Long = 90_000L): CompileJob {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start <= timeoutMs) {
            val job = CompileSessionStore.jobState.value
            if (job != null && job.status != CompileJobStatus.RUNNING) {
                return job
            }
            Thread.sleep(250L)
        }
        error("Timed out waiting for terminal job state")
    }

    fun waitForRunningJob(timeoutMs: Long = 20_000L): CompileJob {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start <= timeoutMs) {
            val job = CompileSessionStore.jobState.value
            if (job?.status == CompileJobStatus.RUNNING) {
                return job
            }
            Thread.sleep(100L)
        }
        error("Timed out waiting for running job state")
    }

    fun grantRuntimePermissions() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        listOf(
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_EXTERNAL_STORAGE",
        ).forEach { permission ->
            executeShell("pm grant $packageName $permission")
        }
    }

    fun executeShell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        descriptor.close()
    }

    fun deleteUri(context: Context, uri: Uri) {
        context.contentResolver.delete(uri, null, null)
    }

    fun removeUnderlyingFile(context: Context, uri: Uri) {
        executeShell("content delete --uri \"$uri\"")
        val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return
            }
            val relativePath = cursor.getString(0)
            val displayName = cursor.getString(1)
            val absolutePath = "/sdcard/${relativePath.orEmpty()}${displayName.orEmpty()}"
            executeShell("rm -f \"$absolutePath\"")
        }
    }

    fun colorRed(): Int = Color.RED

    fun colorBlue(): Int = Color.BLUE

    fun colorGreen(): Int = Color.GREEN

    fun colorYellow(): Int = Color.YELLOW

    private fun readFrame(context: Context, uri: Uri, timeMs: Long) =
        MediaMetadataRetriever().let { retriever ->
            try {
                retriever.setDataSource(context, uri)
                requireNotNull(
                    retriever.getFrameAtTime(
                        timeMs * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                    ),
                ) { "Expected frame at $timeMs ms for $uri" }
            } finally {
                retriever.release()
            }
        }
}
