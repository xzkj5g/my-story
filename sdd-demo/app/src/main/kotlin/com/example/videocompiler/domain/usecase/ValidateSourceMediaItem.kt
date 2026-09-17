package com.example.videocompiler.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.videocompiler.domain.model.MediaType
import com.example.videocompiler.domain.model.SourceMediaItem
import com.example.videocompiler.domain.model.ValidationState
import java.io.FileNotFoundException

/**
 * Probes a user-selected `content://` [Uri] (video or photo) and produces
 * [MediaValidationResult.Valid]/[MediaValidationResult.Invalid], per
 * `contracts/media-validation.md`. Read-only: never mutates device storage. Re-callable for the
 * same [Uri] any number of times — never caches a stale determination (also relied upon by
 * [RevalidateSourceMediaItem]).
 *
 * @param context Used only to obtain a [ContentResolver]; no other Android framework state is
 *   retained across calls.
 */
open class ValidateSourceMediaItem(private val context: Context) {

    /**
     * @param uri the user-selected content URI.
     * @param mediaType whether [uri] is expected to be a video or a photo (as reported by the
     *   picker/MediaStore query that produced it).
     */
    open operator fun invoke(uri: Uri, mediaType: MediaType): MediaValidationResult {
        val displayName = uri.lastPathSegment
        return try {
            when (mediaType) {
                MediaType.VIDEO -> probeVideo(uri, displayName)
                MediaType.PHOTO -> probePhoto(uri, displayName)
            }
        } catch (
            @Suppress("SwallowedException")
            e: SecurityException,
        ) {
            // Translated into the domain-level INACCESSIBLE result per contracts/media-validation.md;
            // the exception itself carries no information the caller needs beyond that.
            MediaValidationResult.Invalid(MediaValidationResult.InvalidReason.INACCESSIBLE, displayName)
        } catch (
            @Suppress("SwallowedException")
            e: FileNotFoundException,
        ) {
            MediaValidationResult.Invalid(MediaValidationResult.InvalidReason.INACCESSIBLE, displayName)
        }
    }

    private fun probeVideo(uri: Uri, displayName: String?): MediaValidationResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            if (isUnprobeable(durationMs, width, height)) {
                return MediaValidationResult.Invalid(
                    MediaValidationResult.InvalidReason.UNSUPPORTED_FORMAT,
                    displayName,
                )
            }
            MediaValidationResult.Valid(
                SourceMediaItem(
                    uri = uri.toString(),
                    mediaType = MediaType.VIDEO,
                    displayName = displayName.orEmpty(),
                    durationMs = durationMs,
                    width = checkNotNull(width),
                    height = checkNotNull(height),
                    // Falls back to a standard default when the container omits capture frame
                    // rate metadata (common for non-camera-captured video files).
                    frameRate = frameRate ?: DEFAULT_VIDEO_FRAME_RATE,
                    hasAudioTrack = hasAudio,
                    validationState = ValidationState.VALID,
                ),
            )
        } catch (
            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            e: RuntimeException,
        ) {
            // MediaMetadataRetriever throws a bare RuntimeException for unreadable/unsupported
            // sources (per Android platform docs); there is no more specific checked exception
            // to catch, and it is intentionally translated into the UNREADABLE domain result.
            MediaValidationResult.Invalid(MediaValidationResult.InvalidReason.UNREADABLE, displayName)
        } finally {
            retriever.release()
        }
    }

    private fun probePhoto(uri: Uri, displayName: String?): MediaValidationResult {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { stream ->
            if (stream == null) {
                return MediaValidationResult.Invalid(
                    MediaValidationResult.InvalidReason.INACCESSIBLE,
                    displayName,
                )
            }
            BitmapFactory.decodeStream(stream, null, options)
        }
        return if (options.outWidth <= 0 || options.outHeight <= 0) {
            MediaValidationResult.Invalid(
                MediaValidationResult.InvalidReason.UNSUPPORTED_FORMAT,
                displayName,
            )
        } else {
            MediaValidationResult.Valid(
                SourceMediaItem(
                    uri = uri.toString(),
                    mediaType = MediaType.PHOTO,
                    displayName = displayName.orEmpty(),
                    durationMs = null,
                    width = options.outWidth,
                    height = options.outHeight,
                    frameRate = null,
                    hasAudioTrack = false,
                    validationState = ValidationState.VALID,
                ),
            )
        }
    }

    private companion object {
        const val DEFAULT_VIDEO_FRAME_RATE = 30f

        fun isUnprobeable(durationMs: Long?, width: Int?, height: Int?): Boolean =
            durationMs == null || durationMs <= 0L ||
                width == null || width <= 0 ||
                height == null || height <= 0
    }
}
