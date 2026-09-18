package com.example.videocompiler.domain.model

/** Enumerates the kinds of source media supported by a compilation job (FR-001). */
enum class MediaType {
    VIDEO,
    PHOTO,
}

/** Result of probing a [SourceMediaItem] for readability (FR-015, FR-016). */
enum class ValidationState {
    VALID,
    INVALID_UNREADABLE,
}

/**
 * A video or photo file already existing on the user's device, referenced (not copied) into a
 * compilation job.
 *
 * See `data-model.md`'s `SourceMediaItem` section for the full field contract.
 *
 * @property uri `MediaStore` URI identifying the file; the durable identity of this item.
 * @property mediaType Discriminates which fields/behavior apply (video-only fields are `null`
 *   for photos).
 * @property displayName Human-readable file name, for UI display during selection/reordering.
 * @property durationMs Native duration in milliseconds; `null` for photos, which use the fixed
 *   [PHOTO_DISPLAY_DURATION_MS] instead.
 * @property width Native pixel width, used to compute native aspect ratio for fit/letterbox
 *   decisions (FR-008).
 * @property height Native pixel height, used to compute native aspect ratio for fit/letterbox
 *   decisions (FR-008).
 * @property frameRate Native frame rate in frames per second; `null` for photos.
 * @property hasAudioTrack Whether the source has an audio track; always `false` for photos
 *   (silence is generated for their full display duration per FR-009).
 * @property validationState Set at selection time by `ValidateSourceMediaItem` (FR-015); only
 *   [ValidationState.VALID] entries may be added to a `SelectionSequence`.
 */
data class SourceMediaItem(
    val uri: String,
    val mediaType: MediaType,
    val displayName: String,
    val durationMs: Long?,
    val width: Int,
    val height: Int,
    val frameRate: Float?,
    val hasAudioTrack: Boolean,
    val validationState: ValidationState,
) {
    init {
        require(width > 0 && height > 0) { "width and height must be positive" }
        if (mediaType == MediaType.PHOTO) {
            require(durationMs == null) { "photos must not have a durationMs" }
            require(frameRate == null) { "photos must not have a frameRate" }
            require(!hasAudioTrack) { "photos must not have an audio track" }
        }
    }

    companion object {
        /**
         * The fixed default duration every photo is displayed for in the compiled output
         * (FR-017); not user-adjustable per photo in v1.
         */
        const val PHOTO_DISPLAY_DURATION_MS: Long = 3000L
    }
}
