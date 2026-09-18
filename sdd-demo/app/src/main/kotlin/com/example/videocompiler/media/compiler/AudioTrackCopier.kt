package com.example.videocompiler.media.compiler

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import java.nio.ByteBuffer

/**
 * Copies a source's audio track through unmodified into a [MediaMuxer] being used to build a
 * re-encoded (frame-rate-upsampled) video file. Split out from [FrameRateUpsampler] to keep
 * that object focused on the video decode/encode pipeline.
 */
internal object AudioTrackCopier {

    private const val SAMPLE_BUFFER_SIZE = 1 shl 20

    /**
     * Registers the source's audio track (if any) with [muxer] via [MediaMuxer.addTrack] and
     * returns its muxer track index. Must be called before [muxer] is started.
     */
    fun addTrackIfPresent(context: Context, sourceUri: Uri, muxer: MediaMuxer): Int? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, sourceUri, null)
            val audioTrackIndex = findAudioTrackIndex(extractor) ?: return null
            muxer.addTrack(extractor.getTrackFormat(audioTrackIndex))
        } finally {
            extractor.release()
        }
    }

    /** Copies the source's audio samples (if any) into [muxer]'s [muxerAudioTrackIndex] track. */
    fun copySamples(context: Context, sourceUri: Uri, muxer: MediaMuxer, muxerAudioTrackIndex: Int) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, sourceUri, null)
            val audioTrackIndex = findAudioTrackIndex(extractor) ?: return
            extractor.selectTrack(audioTrackIndex)
            copySelectedTrackSamples(extractor, muxer, muxerAudioTrackIndex)
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrackIndex(extractor: MediaExtractor): Int? =
        MediaTrackUtils.findTrackIndex(extractor) { it.startsWith("audio/") }

    private fun copySelectedTrackSamples(extractor: MediaExtractor, muxer: MediaMuxer, muxerTrackIndex: Int) {
        val bufferInfo = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocate(SAMPLE_BUFFER_SIZE)
        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) {
                return
            }
            val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
            bufferInfo.set(0, sampleSize, extractor.sampleTime, flags)
            muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }
    }
}
