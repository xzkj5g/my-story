package com.example.videocompiler.data.mediastore

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.videocompiler.domain.model.AppResult
import com.example.videocompiler.domain.model.MediaType

/**
 * `MediaStore`-backed repository: queries available videos/photos and saves the compiled output
 * to the `Movies/VideoCompiler/` collection (research.md §8, FR-011).
 *
 * Wraps a [ContentResolver] rather than the framework's static `MediaStore` calls directly, so
 * this class can be unit-tested with a fake/mock resolver.
 */
class MediaStoreRepository(
    private val contentResolver: ContentResolver,
) {

    /** One row of media metadata as read from `MediaStore` (used to build a `SourceMediaItem`). */
    data class MediaStoreEntry(
        val uri: String,
        val mediaType: MediaType,
        val displayName: String,
        val durationMs: Long?,
        val width: Int,
        val height: Int,
    )

    /**
     * Queries `MediaStore` for the video or photo at [uri] and returns its metadata, or a
     * [AppResult.Failure] if it cannot be resolved/read (feeds `ValidateSourceMediaItem`,
     * FR-015).
     */
    fun queryEntry(uri: Uri, mediaType: MediaType): AppResult<MediaStoreEntry> {
        val projection = when (mediaType) {
            MediaType.VIDEO -> arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
            )
            MediaType.PHOTO -> arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
            )
        }
        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return AppResult.Failure("No MediaStore row found for $uri")
            }
            AppResult.Success(toMediaStoreEntry(cursor, uri, mediaType))
        } ?: AppResult.Failure("ContentResolver.query returned null for $uri")
    }

    private fun toMediaStoreEntry(cursor: Cursor, uri: Uri, mediaType: MediaType): MediaStoreEntry {
        val displayNameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val durationMs = if (mediaType == MediaType.VIDEO) {
            val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            if (durationCol >= 0) cursor.getLong(durationCol) else null
        } else {
            null
        }
        return MediaStoreEntry(
            uri = uri.toString(),
            mediaType = mediaType,
            displayName = if (displayNameCol >= 0) {
                cursor.getString(displayNameCol)
            } else {
                uri.lastPathSegment.orEmpty()
            },
            durationMs = durationMs,
            width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
            height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
        )
    }

    /**
     * Saves the compiled output file's bytes under `Movies/VideoCompiler/` via `MediaStore`
     * (research.md §8, FR-011). Returns the new content `Uri` on success.
     */
    fun saveCompiledOutput(displayName: String, mimeType: String = "video/mp4"): AppResult<Uri> {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoCompiler")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = contentResolver.insert(collection, values)
            ?: return AppResult.Failure("Failed to create MediaStore entry for $displayName")
        return AppResult.Success(uri)
    }

    /**
     * Marks a previously-inserted pending output ([saveCompiledOutput]) as no longer pending,
     * making it visible in the gallery (FR-011).
     */
    fun markOutputComplete(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            contentResolver.update(uri, values, null, null)
        }
    }

    /** Deletes a partial/failed output file so no corrupted output is left behind (FR-010). */
    fun deleteOutput(uri: Uri) {
        contentResolver.delete(uri, null, null)
    }
}
