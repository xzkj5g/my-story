package com.example.videocompiler.media.compiler

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Genuinely upsamples a video source to a higher target frame rate (FR-007/FR-008, SC-003
 * Acceptance Scenario 4).
 *
 * Media3 1.10.1's `setFrameRate()` only ever caps a source's frame rate downward (it drops
 * frames when the source is faster than the target, but has no effect when the source is
 * slower) — there is no public Media3 API that duplicates frames to reach a higher target
 * rate. This object fills that gap with its own single-pass decode/re-encode pipeline:
 * the source video is decoded *sequentially* (in bitstream order, letting the system decoder
 * correctly resolve display order for B-frame/GOP structures), and each decoded frame is held
 * and replayed into a new H.264 encoder for every evenly-spaced target-fps output slot it
 * covers, until the next decoded frame arrives. The audio track, if present, is copied through
 * unmodified.
 *
 * An earlier version used [MediaMetadataRetriever.getFrameAtTime] independently for every
 * output slot. That is correct but was replaced: each such call re-seeks to the nearest sync
 * frame and re-decodes forward from there, so for sources with one keyframe per GOP (typical
 * camera output), decoding output slot N re-decodes frames 0..N from scratch — an O(n^2) cost
 * that stalled/crashed the pipeline on anything but the shortest clips. A single sequential
 * decode pass fixes that.
 *
 * An even earlier version avoided decoding entirely by re-writing the same encoded sample
 * bytes at new presentation timestamps (pure container-level duplication). That corrupts
 * Picture Order Count / reference-picture continuity for sources with B-frames, causing
 * decoders downstream (including Media3's Transformer) to stall or crash.
 */
internal object FrameRateUpsampler {

    private const val MICROS_PER_SECOND = 1_000_000L
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val VIDEO_BIT_RATE_BPS = 6_000_000
    private const val I_FRAME_INTERVAL_SECONDS = 1

    /**
     * Produces a new local MP4 file with [sourceUri]'s video re-encoded at [targetFps] and its
     * audio track (if any) copied through unmodified. Returns `null` if [sourceUri] has no
     * decodable video track.
     */
    fun upsample(context: Context, sourceUri: Uri, targetFps: Int, cacheDir: File): File? {
        val videoInfo = probeVideoTrack(context, sourceUri) ?: return null
        val targetFrameCount = ((videoInfo.durationUs.toDouble() * targetFps) / MICROS_PER_SECOND)
            .roundToLong()
            .coerceAtLeast(1L)
        val outputFile = File(cacheDir, "upsampled-${UUID.randomUUID()}.mp4")
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val state = EncodeMuxState()
        try {
            // The audio track must be registered with addTrack() before the muxer starts, and
            // MediaMuxer requires *all* addTrack() calls to happen up front before any
            // writeSampleData() call. The video encoder's output format (and thus its track)
            // is only known partway through reencodeVideoTrack(), which is also where
            // muxer.start() is triggered — so the audio track must be added here first.
            val audioTrackIndex = AudioTrackCopier.addTrackIfPresent(context, sourceUri, muxer)
            reencodeVideoTrack(context, sourceUri, videoInfo, targetFrameCount, targetFps, muxer, state)
            if (audioTrackIndex != null) {
                AudioTrackCopier.copySamples(context, sourceUri, muxer, audioTrackIndex)
            }
        } finally {
            // Only call stop() if start() actually succeeded: MediaMuxer.stop() throws
            // IllegalStateException from the INITIALIZED state, which would otherwise mask
            // the real exception that prevented the muxer from ever starting.
            if (state.muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        }
        return outputFile
    }

    private data class VideoTrackInfo(val width: Int, val height: Int, val durationUs: Long)

    private fun probeVideoTrack(context: Context, sourceUri: Uri): VideoTrackInfo? {
        val extractor = MediaExtractor()
        val trackFormat = try {
            extractor.setDataSource(context, sourceUri, null)
            val videoTrackIndex = MediaTrackUtils.findTrackIndex(extractor) { it.startsWith("video/") }
                ?: return null
            extractor.getTrackFormat(videoTrackIndex)
        } finally {
            extractor.release()
        }
        val width = trackFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = trackFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(context, sourceUri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
        return if (isValidVideoTrack(durationMs, width, height)) {
            VideoTrackInfo(width, height, requireNotNull(durationMs) * 1_000L)
        } else {
            null
        }
    }

    private fun isValidVideoTrack(durationMs: Long?, width: Int, height: Int): Boolean {
        if (durationMs == null || durationMs <= 0) {
            return false
        }
        return width > 0 && height > 0
    }

    /** Tracks the muxer's video track index and whether [MediaMuxer.start] has been called. */
    private class EncodeMuxState {
        var videoTrackIndex: Int = -1
        var muxerStarted: Boolean = false

        fun startMuxerIfReady(muxer: MediaMuxer) {
            if (!muxerStarted && videoTrackIndex >= 0) {
                muxer.start()
                muxerStarted = true
            }
        }
    }

    /** Tracks progress through the evenly-spaced target-fps output timeline during decoding. */
    private class SequentialDecodeState {
        var nextTargetIndex: Long = 0
        var heldFrame: YuvFrame? = null
    }

    private fun reencodeVideoTrack(
        context: Context,
        sourceUri: Uri,
        videoInfo: VideoTrackInfo,
        targetFrameCount: Long,
        targetFps: Int,
        muxer: MediaMuxer,
        state: EncodeMuxState,
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            extractor.setDataSource(context, sourceUri, null)
            val videoTrackIndex = MediaTrackUtils.findTrackIndex(extractor) { it.startsWith("video/") }
                ?: return
            extractor.selectTrack(videoTrackIndex)
            val decoderFormat = extractor.getTrackFormat(videoTrackIndex)
            val mime = requireNotNull(decoderFormat.getString(MediaFormat.KEY_MIME))
            val activeDecoder = MediaCodec.createDecoderByType(mime)
            decoder = activeDecoder
            activeDecoder.configure(decoderFormat, null, null, 0)
            activeDecoder.start()

            encoder.configure(buildEncoderFormat(videoInfo, targetFps), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            decodeAndReencodeFrames(
                extractor,
                activeDecoder,
                encoder,
                muxer,
                state,
                bufferInfo,
                videoInfo,
                targetFrameCount,
                targetFps,
            )

            val frameDurationUs = MICROS_PER_SECOND / targetFps
            feedEncoderFrame(
                encoder,
                frame = null,
                presentationTimeUs = targetFrameCount * frameDurationUs,
                endOfStream = true,
            )
            drainEncoder(encoder, muxer, state, bufferInfo, drainUntilEndOfStream = true)
        } finally {
            decoder?.stop()
            decoder?.release()
            encoder.stop()
            encoder.release()
            extractor.release()
        }
    }

    private fun buildEncoderFormat(videoInfo: VideoTrackInfo, targetFps: Int): MediaFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoInfo.width, videoInfo.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE_BPS)
            setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
        }

    @Suppress("LongParameterList")
    private fun decodeAndReencodeFrames(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        state: EncodeMuxState,
        bufferInfo: MediaCodec.BufferInfo,
        videoInfo: VideoTrackInfo,
        targetFrameCount: Long,
        targetFps: Int,
    ) {
        val frameDurationUs = MICROS_PER_SECOND / targetFps
        val decodeState = SequentialDecodeState()
        val decoderInfo = MediaCodec.BufferInfo()
        var sawDecoderInputEos = false
        var isDecoderEos = false
        while (!isDecoderEos) {
            if (!sawDecoderInputEos) {
                sawDecoderInputEos = feedDecoderInput(extractor, decoder)
            }
            val outputIndex = decoder.dequeueOutputBuffer(decoderInfo, CODEC_TIMEOUT_US)
            if (outputIndex < 0) {
                continue
            }
            isDecoderEos = decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            if (decoderInfo.size > 0) {
                val image = decoder.getOutputImage(outputIndex)
                if (image != null) {
                    emitHeldFrameForElapsedSlots(
                        decodeState,
                        decoderInfo.presentationTimeUs,
                        frameDurationUs,
                        targetFrameCount,
                        encoder,
                        muxer,
                        state,
                        bufferInfo,
                    )
                    decodeState.heldFrame = YuvFrame.capture(image, videoInfo.width, videoInfo.height)
                }
            }
            decoder.releaseOutputBuffer(outputIndex, false)
        }
        emitHeldFrameForElapsedSlots(
            decodeState,
            Long.MAX_VALUE,
            frameDurationUs,
            targetFrameCount,
            encoder,
            muxer,
            state,
            bufferInfo,
        )
    }

    /** Reads one compressed sample into [decoder]; returns `true` once end-of-stream is queued. */
    private fun feedDecoderInput(extractor: MediaExtractor, decoder: MediaCodec): Boolean {
        val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        val inputBuffer = if (inputIndex >= 0) decoder.getInputBuffer(inputIndex) else null
        if (inputIndex < 0 || inputBuffer == null) {
            return false
        }
        val sampleSize = extractor.readSampleData(inputBuffer, 0)
        return if (sampleSize < 0) {
            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            true
        } else {
            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
            extractor.advance()
            false
        }
    }

    /**
     * Feeds [decodeState]'s currently-held frame into the encoder for every evenly-spaced
     * target-fps output slot whose timestamp falls before [upToPts] (the next decoded frame's
     * presentation time, or [Long.MAX_VALUE] to flush all remaining slots at the end).
     */
    @Suppress("LongParameterList")
    private fun emitHeldFrameForElapsedSlots(
        decodeState: SequentialDecodeState,
        upToPts: Long,
        frameDurationUs: Long,
        targetFrameCount: Long,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        state: EncodeMuxState,
        bufferInfo: MediaCodec.BufferInfo,
    ) {
        val heldFrame = decodeState.heldFrame ?: return
        while (decodeState.nextTargetIndex < targetFrameCount &&
            decodeState.nextTargetIndex * frameDurationUs < upToPts
        ) {
            val targetTimeUs = decodeState.nextTargetIndex * frameDurationUs
            feedEncoderFrame(encoder, heldFrame, targetTimeUs, endOfStream = false)
            drainEncoder(encoder, muxer, state, bufferInfo, drainUntilEndOfStream = false)
            decodeState.nextTargetIndex++
        }
    }

    /** Queues one encoder input frame; pass `frame = null` only for the end-of-stream signal. */
    private fun feedEncoderFrame(
        encoder: MediaCodec,
        frame: YuvFrame?,
        presentationTimeUs: Long,
        endOfStream: Boolean,
    ) {
        var inputIndex: Int
        do {
            inputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        } while (inputIndex < 0)
        val flags = if (endOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        if (frame == null) {
            encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, flags)
            return
        }
        val image = encoder.getInputImage(inputIndex)
        if (image != null) {
            frame.writeTo(image)
        }
        val bufferSize = image?.let { it.width * it.height * 3 / 2 } ?: 0
        encoder.queueInputBuffer(inputIndex, 0, bufferSize, presentationTimeUs, flags)
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        state: EncodeMuxState,
        bufferInfo: MediaCodec.BufferInfo,
        drainUntilEndOfStream: Boolean,
    ) {
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!drainUntilEndOfStream) return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    state.videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                    state.startMuxerIfReady(muxer)
                }
                outputIndex >= 0 -> {
                    val isConfigSample = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (bufferInfo.size > 0 && state.muxerStarted && !isConfigSample) {
                        val outputBuffer = requireNotNull(encoder.getOutputBuffer(outputIndex))
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(state.videoTrackIndex, outputBuffer, bufferInfo)
                    }
                    val isEndOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (isEndOfStream) {
                        return
                    }
                }
            }
        }
    }
}
