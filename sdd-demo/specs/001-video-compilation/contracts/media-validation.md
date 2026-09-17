# Contract: Media Validation Use Case

This app has no external/network API. This contract documents the internal boundary between the
UI selection layer and the media validation logic (`domain/usecase`), so both sides (and their
tests) agree on behavior independent of implementation. Applies to both videos and photos.
Corresponds to FR-001, FR-015, FR-016.

## `ValidateSourceMediaItem`

**Input**: a `content://` URI selected by the user from the system media picker (video or
photo).

**Output**: one of:

| Result | Condition | Consumer behavior |
|---|---|---|
| `Valid(SourceMediaItem)` | URI resolves to a readable video track, or a readable/decodable image (probing succeeds) | UI adds the `SourceMediaItem` to the `SelectionSequence` (FR-015). |
| `Invalid(reason: UNREADABLE \| UNSUPPORTED_FORMAT \| INACCESSIBLE)` | Probing fails, format unsupported, or URI can no longer be resolved | UI MUST NOT add the item to the sequence, and MUST show the user a clear rejection message naming the file (FR-015). |

**Guarantees**:
- MUST be callable again for the same URI later (used for `RevalidateSourceMediaItem` below) and
  produce a fresh result — it does not cache a stale validity determination.
- MUST NOT have side effects on device storage (read-only probing).
- Applies the same pass/fail contract regardless of `mediaType` (VIDEO or PHOTO).

## `RevalidateSourceMediaItem` (pre-compile / at compile time)

**Input**: an existing `SourceMediaItem` already present in a `SelectionSequence`.

**Output**: same `Valid`/`Invalid` shape as above.

**Contract**: Invoked by the compile pipeline immediately before/at the moment each item is
consumed. If any item in the job's snapshot sequence returns `Invalid`, the compile pipeline
MUST:
1. Stop processing further items for that job.
2. Transition the `CompileJob.status` to `FAILED` with `failureReason` identifying the item.
3. Delete any partial output already written.
4. Surface a user-facing notification identifying which item became unavailable (FR-016).

It MUST NOT silently skip the item and continue with the remaining items (this was an explicit
decision recorded in the Clarifications — distinguishing this from a generic "skip bad items"
policy). This applies identically whether the unavailable item is a video or a photo.
