package com.example.videocompiler.media.compiler

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.videocompiler.data.mediastore.MediaStoreRepository
import com.example.videocompiler.domain.model.AppResult
import com.example.videocompiler.domain.model.CompileJob
import com.example.videocompiler.domain.model.CompileJobStatus
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.model.SelectionSequence
import com.example.videocompiler.domain.usecase.MediaValidationResult
import com.example.videocompiler.domain.usecase.RevalidateSourceMediaItem
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@UnstableApi
class CompileEngine(
    private val context: Context,
    private val mediaStoreRepository: MediaStoreRepository =
        MediaStoreRepository(context.contentResolver),
    private val revalidateSourceMediaItem: RevalidateSourceMediaItem =
        RevalidateSourceMediaItem(context),
) {

    private val activeExportLock = Any()
    private var activeExport: ActiveExport? = null

    fun compile(
        sequence: SelectionSequence,
        settings: OutputSettings,
        onProgress: (CompileJob) -> Unit = {},
    ): CompileJob {
        val plan = CompositionPlanner.plan(sequence, settings)
        val runningJob = CompileJob(
            sequence = sequence,
            outputSettings = settings,
            status = CompileJobStatus.RUNNING,
            progressPercent = 0,
        )
        onProgress(runningJob)

        val finalJob = revalidateSequence(sequence)?.let { validationFailure ->
            failJob(
                job = runningJob,
                failureReason = validationFailure,
                cause = null,
                onProgress = onProgress,
            )
        } ?: compileToReservedOutput(
            job = runningJob,
            plan = plan,
            settings = settings,
            onProgress = onProgress,
        )
        return finalJob
    }

    fun cancel(job: CompileJob): CompileJob {
        val cancelledJob = if (job.status != CompileJobStatus.RUNNING) {
            job
        } else {
            val export = synchronized(activeExportLock) { activeExport }
            if (export == null || export.sequence != job.sequence || export.settings != job.outputSettings) {
                job.transitionTo(CompileJobStatus.CANCELLED, progressPercent = job.progressPercent)
            } else {
                export.cancelRequested.set(true)
                export.cancel()
                job.transitionTo(CompileJobStatus.CANCELLED, progressPercent = job.progressPercent)
            }
        }
        return cancelledJob
    }

    private fun cancelRunningJob(
        job: CompileJob,
        export: ActiveExport,
        onProgress: (CompileJob) -> Unit,
    ): CompileJob {
        export.cancel()
        mediaStoreRepository.deleteOutput(export.outputUri)
        val cancelled = job.transitionTo(
            newStatus = CompileJobStatus.CANCELLED,
            progressPercent = job.progressPercent,
        )
        onProgress(cancelled)
        return cancelled
    }

    private fun failJob(
        job: CompileJob,
        failureReason: String,
        cause: Throwable?,
        outputUri: Uri? = null,
        onProgress: (CompileJob) -> Unit,
    ): CompileJob {
        outputUri?.let(mediaStoreRepository::deleteOutput)
        val finalReason = cause?.message?.takeIf { it.isNotBlank() } ?: failureReason
        val failed = job.transitionTo(
            newStatus = CompileJobStatus.FAILED,
            progressPercent = job.progressPercent,
            failureReason = finalReason,
        )
        onProgress(failed)
        return failed
    }

    private fun revalidateSequence(sequence: SelectionSequence): String? =
        sequence.items.firstNotNullOfOrNull { item ->
            when (revalidateSourceMediaItem(item)) {
                is MediaValidationResult.Valid -> null
                is MediaValidationResult.Invalid ->
                    "Selected item became unavailable: ${item.displayName}"
            }
        }

    private fun compileToReservedOutput(
        job: CompileJob,
        plan: CompositionPlan,
        settings: OutputSettings,
        onProgress: (CompileJob) -> Unit,
    ): CompileJob {
        val outputReservation = mediaStoreRepository.saveCompiledOutput(defaultOutputDisplayName())
        return when (outputReservation) {
            is AppResult.Success -> runExport(
                job = job,
                plan = plan,
                settings = settings,
                outputUri = outputReservation.value,
                onProgress = onProgress,
            )
            is AppResult.Failure -> failJob(
                job = job,
                failureReason = outputReservation.message,
                cause = outputReservation.cause,
                onProgress = onProgress,
            )
        }
    }

    private fun runExport(
        job: CompileJob,
        plan: CompositionPlan,
        settings: OutputSettings,
        outputUri: Uri,
        onProgress: (CompileJob) -> Unit,
    ): CompileJob {
        var runningJob = job
        val tempFile = File(context.cacheDir, "compile-${UUID.randomUUID()}.mp4")
        val export = ActiveExport(
            context = context,
            outputUri = outputUri,
            tempFile = tempFile,
            sequence = job.sequence,
            settings = settings,
        )
        synchronized(activeExportLock) {
            activeExport = export
        }

        return try {
            export.start(buildComposition(plan, settings))
            while (!export.completionLatch.await(PROGRESS_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                runningJob = updateProgress(runningJob, export.readProgressPercent(), onProgress)
                if (export.cancelRequested.get()) {
                    return cancelRunningJob(runningJob, export, onProgress)
                }
            }
            finalizeExport(runningJob, export, outputUri, onProgress)
        } catch (e: IOException) {
            failJob(runningJob, e.message ?: "Failed to save compiled output", e, outputUri, onProgress)
        } finally {
            synchronized(activeExportLock) {
                if (activeExport === export) {
                    activeExport = null
                }
            }
            export.release()
            tempFile.delete()
        }
    }

    private fun updateProgress(
        job: CompileJob,
        progressPercent: Int,
        onProgress: (CompileJob) -> Unit,
    ): CompileJob {
        return if (progressPercent > job.progressPercent) {
            job.copy(progressPercent = progressPercent).also(onProgress)
        } else {
            job
        }
    }

    private fun finalizeExport(
        job: CompileJob,
        export: ActiveExport,
        outputUri: Uri,
        onProgress: (CompileJob) -> Unit,
    ): CompileJob {
        val finalJob = when {
            export.cancelRequested.get() -> cancelRunningJob(job, export, onProgress)
            export.exportException != null -> failJob(
                job = job,
                failureReason = export.exportException?.message ?: "Compilation failed",
                cause = export.exportException,
                outputUri = outputUri,
                onProgress = onProgress,
            )
            else -> completeJob(job, export.tempFile, outputUri, onProgress)
        }
        return finalJob
    }

    @Throws(IOException::class)
    private fun completeJob(
        job: CompileJob,
        tempFile: File,
        outputUri: Uri,
        onProgress: (CompileJob) -> Unit,
    ): CompileJob {
        copyTempFileToOutput(context, tempFile, outputUri)
        mediaStoreRepository.markOutputComplete(outputUri)
        val succeeded = job.transitionTo(
            newStatus = CompileJobStatus.SUCCEEDED,
            progressPercent = 100,
            outputUri = outputUri.toString(),
        )
        onProgress(succeeded)
        return succeeded
    }

    private companion object {
        const val PROGRESS_POLL_INTERVAL_MS = 250L
    }
}

private fun defaultOutputDisplayName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "media-sequence-$timestamp.mp4"
}

private fun buildComposition(plan: CompositionPlan, settings: OutputSettings): Composition {
    // Applied per-item rather than at the Composition level: Composition-level video effects
    // are only guaranteed to run when multiple sequences are combined (e.g. overlays); for a
    // single video sequence, the Presentation resize/pad effect must be attached to each
    // EditedMediaItem to reliably take effect (FR-007/FR-008, SC-003/SC-004).
    val presentationEffect = Presentation.createForWidthAndHeight(
        settings.outputWidth,
        settings.outputHeight,
        Presentation.LAYOUT_SCALE_TO_FIT,
    )
    val sequence = EditedMediaItemSequence(
        plan.segments.map { segment ->
            val mediaItemBuilder = MediaItem.Builder().setUri(segment.source.uri)
            if (segment.source.mediaType == MediaType.PHOTO) {
                mediaItemBuilder.setImageDurationMs(segment.durationMs)
            }
            val editedItemBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
            if (segment.source.mediaType == MediaType.PHOTO) {
                editedItemBuilder.setDurationUs(segment.durationMs * 1_000L)
            }
            // Resample every segment (video or photo) to the target frame rate (FR-008
            // Acceptance Scenario 4) without altering its playback speed/duration — Media3
            // duplicates/drops frames internally to hit this rate.
            editedItemBuilder
                .setFrameRate(settings.frameRate.fps)
                .setEffects(Effects(emptyList(), listOf(presentationEffect)))
            editedItemBuilder.build()
        },
    )
    return Composition.Builder(sequence)
        .experimentalSetForceAudioTrack(true)
        .build()
}

@Throws(IOException::class)
private fun copyTempFileToOutput(context: Context, tempFile: File, outputUri: Uri) {
    context.contentResolver.openOutputStream(outputUri, "w").use { outputStream ->
        requireNotNull(outputStream) {
            "Unable to open output stream for $outputUri"
        }
        tempFile.inputStream().use { inputStream ->
            inputStream.copyTo(outputStream)
        }
    }
}

private fun updateProgress(
    job: CompileJob,
    progressPercent: Int,
    onProgress: (CompileJob) -> Unit,
): CompileJob {
    return if (progressPercent > job.progressPercent) {
        job.copy(progressPercent = progressPercent).also(onProgress)
    } else {
        job
    }
}

private class ActiveExport(
    private val context: Context,
    val outputUri: Uri,
    val tempFile: File,
    val sequence: SelectionSequence,
    val settings: OutputSettings,
) {
    val cancelRequested = AtomicBoolean(false)
    val completionLatch = CountDownLatch(1)
    private val thread = HandlerThread("compile-transformer").apply { start() }
    private val handler = Handler(thread.looper)
    private lateinit var transformer: Transformer

    @Volatile
    var exportException: ExportException? = null

    fun start(composition: Composition) {
        onTransformerThread {
            transformer = Transformer.Builder(context)
                .setLooper(thread.looper)
                .addListener(listener)
                .build()
            transformer.start(composition, tempFile.absolutePath)
        }
    }

    fun cancel() {
        if (::transformer.isInitialized) {
            onTransformerThread { transformer.cancel() }
        }
    }

    fun readProgressPercent(): Int {
        if (!::transformer.isInitialized) {
            return 0
        }
        return onTransformerThread {
            val progressHolder = ProgressHolder()
            val progressState = transformer.getProgress(progressHolder)
            if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                progressHolder.progress
            } else {
                0
            }
        }
    }

    fun release() {
        thread.quitSafely()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> onTransformerThread(block: () -> T): T {
        val latch = CountDownLatch(1)
        var result: T? = null
        var failure: Exception? = null
        handler.post {
            try {
                result = block()
            } catch (e: Exception) {
                // Media3/Handler-thread APIs surface failures as runtime exceptions; capture and
                // rethrow them on the caller thread so compile/cancel can fail deterministically.
                failure = e
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private val listener = object : Transformer.Listener {
        override fun onCompleted(
            composition: Composition,
            exportResult: ExportResult,
        ) {
            completionLatch.countDown()
        }

        override fun onError(
            composition: Composition,
            exportResult: ExportResult,
            exportException: ExportException,
        ) {
            this@ActiveExport.exportException = exportException
            completionLatch.countDown()
        }
    }
}
