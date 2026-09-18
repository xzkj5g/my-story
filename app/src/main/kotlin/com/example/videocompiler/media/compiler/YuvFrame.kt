package com.example.videocompiler.media.compiler

import android.media.Image

/**
 * An immutable CPU-side snapshot of one decoded YUV420 video frame, captured from a
 * [android.media.MediaCodec] decoder's output [Image] (whose underlying buffers are only valid
 * until `releaseOutputBuffer()` is called) so it can be replayed into a different encoder's
 * input [Image] an arbitrary number of times later — once per duplicated output frame slot when
 * upsampling to a higher frame rate.
 *
 * Copying raw Y/U/V plane bytes (rather than converting through [android.graphics.Bitmap]/RGB)
 * avoids an unnecessary color-space round trip and works regardless of whether the decoder or
 * encoder negotiated planar (I420) or semi-planar (NV12) layout, since each plane's own
 * `rowStride`/`pixelStride` is used on both the read and write side.
 */
internal class YuvFrame private constructor(
    private val width: Int,
    private val height: Int,
    private val yPlane: PlaneSnapshot,
    private val uPlane: PlaneSnapshot,
    private val vPlane: PlaneSnapshot,
) {

    /** Writes this frame's pixel data into an encoder input [image] (from `getInputImage()`). */
    fun writeTo(image: Image) {
        yPlane.copyTo(image.planes[0], width, height)
        uPlane.copyTo(image.planes[1], width / 2, height / 2)
        vPlane.copyTo(image.planes[2], width / 2, height / 2)
    }

    private class PlaneSnapshot(private val bytes: ByteArray, private val planeWidth: Int) {
        fun copyTo(destination: Image.Plane, copyWidth: Int, copyHeight: Int) {
            val destBuffer = destination.buffer
            val destRowStride = destination.rowStride
            val destPixelStride = destination.pixelStride
            for (row in 0 until copyHeight) {
                val srcRowStart = row * planeWidth
                val destRowStart = row * destRowStride
                for (col in 0 until copyWidth) {
                    destBuffer.put(destRowStart + col * destPixelStride, bytes[srcRowStart + col])
                }
            }
        }
    }

    companion object {
        /**
         * Captures a snapshot of [image] at its logical [width]x[height] (the encoder's target
         * resolution, expected to match the decoder's configured video track dimensions).
         */
        fun capture(image: Image, width: Int, height: Int): YuvFrame =
            YuvFrame(
                width = width,
                height = height,
                yPlane = capturePlane(image.planes[0], width, height),
                uPlane = capturePlane(image.planes[1], width / 2, height / 2),
                vPlane = capturePlane(image.planes[2], width / 2, height / 2),
            )

        private fun capturePlane(plane: Image.Plane, planeWidth: Int, planeHeight: Int): PlaneSnapshot {
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val source = plane.buffer
            val bytes = ByteArray(planeWidth * planeHeight)
            var destIndex = 0
            for (row in 0 until planeHeight) {
                val rowStart = row * rowStride
                for (col in 0 until planeWidth) {
                    bytes[destIndex] = source.get(rowStart + col * pixelStride)
                    destIndex++
                }
            }
            return PlaneSnapshot(bytes, planeWidth)
        }
    }
}
