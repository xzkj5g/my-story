package com.mystory.app.domain.usecase

import com.mystory.app.domain.model.SourceMediaItem

/**
 * Outcome of [ValidateSourceMediaItem] / [RevalidateSourceMediaItem], per
 * `contracts/media-validation.md`. Distinct from [com.mystory.app.domain.model.ValidationState]:
 * this carries the richer rejection reason needed for user-facing messaging (FR-015, FR-016),
 * while `SourceMediaItem.validationState` only tracks the two-state VALID/INVALID_UNREADABLE
 * flag persisted on the item itself (data-model.md).
 */
sealed class MediaValidationResult {
    data class Valid(val item: SourceMediaItem) : MediaValidationResult()
    data class Invalid(val reason: InvalidReason, val displayName: String?) : MediaValidationResult()

    enum class InvalidReason {
        UNREADABLE,
        UNSUPPORTED_FORMAT,
        INACCESSIBLE,
    }
}
