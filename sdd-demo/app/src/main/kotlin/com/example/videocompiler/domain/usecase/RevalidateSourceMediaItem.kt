package com.example.videocompiler.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.videocompiler.domain.model.SourceMediaItem

/**
 * Re-probes an already-selected [SourceMediaItem] immediately before/at the moment it is
 * consumed by the compile pipeline (`contracts/media-validation.md`). Delegates to
 * [ValidateSourceMediaItem] so both use cases share one probing implementation and never disagree
 * on what counts as readable — this use case only adapts the "already-selected item" input shape
 * to that shared probe.
 *
 * Per contract: if `Invalid`, the compile pipeline MUST stop processing further items for that
 * job, transition `CompileJob.status` to `FAILED`, delete any partial output, and surface a
 * user-facing message naming the unavailable item (FR-016) — that orchestration lives in
 * `CompileEngine`, not here; this use case only reports the up-to-date validity.
 */
open class RevalidateSourceMediaItem(context: Context) {

    private val validate = ValidateSourceMediaItem(context)

    open operator fun invoke(item: SourceMediaItem): MediaValidationResult =
        validate(Uri.parse(item.uri), item.mediaType)
}
