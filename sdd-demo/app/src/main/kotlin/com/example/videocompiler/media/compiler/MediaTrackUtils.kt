package com.example.videocompiler.media.compiler

import android.media.MediaExtractor
import android.media.MediaFormat

/** Small shared helper for locating a track index in a [MediaExtractor] by MIME type prefix. */
internal object MediaTrackUtils {
    fun findTrackIndex(extractor: MediaExtractor, matches: (String) -> Boolean): Int? {
        for (trackIndex in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(trackIndex).getString(MediaFormat.KEY_MIME) ?: continue
            if (matches(mime)) {
                return trackIndex
            }
        }
        return null
    }
}
