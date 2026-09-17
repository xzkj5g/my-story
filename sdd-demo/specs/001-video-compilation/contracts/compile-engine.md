# Contract: Compile Engine

Boundary between the UI/domain layer and the `media/compiler` component that wraps AndroidX
Media3 Transformer/Composition (research.md §1-4, §6b). Corresponds to FR-006 through FR-011,
and FR-017 for photo handling.

## `CompileEngine.compile(sequence: SelectionSequence, settings: OutputSettings): CompileJob`

**Preconditions**:
- `sequence.items` has at least 1 entry (FR-012), and every entry's `validationState == VALID`
  as of the most recent `RevalidateSourceMediaItem` check (see `contracts/media-validation.md`).
- `settings` fields are all set to defined presets (data-model.md `OutputSettings`).

**Behavior guarantees**:
1. Produces output items strictly in `sequence.items` order — videos played in full, photos
   displayed for `PHOTO_DISPLAY_DURATION_MS` — back-to-back with no gap (FR-006, SC-002).
2. Output resolution/aspect ratio/frame rate exactly equal `settings` (FR-007, SC-003).
3. Any source item (video or photo) whose native aspect ratio differs from
   `settings.aspectRatio` is fit inside the output frame with padding — never cropped, never
   stretched (FR-008, SC-004).
4. Any source clip whose native frame rate differs from `settings.frameRate` is resampled to the
   target frame rate without altering its playback speed/duration (User Story 3, Acceptance
   Scenario 4). Each photo segment is rendered at `settings.frameRate` as a static (non-animated)
   frame — no pan/zoom effect (FR-017).
5. Any clip segment with no audio track, and every photo segment, receives generated silence for
   its full duration so the composed output has one continuous audio track (FR-009).
6. On success: writes the result to `MediaStore` under `Movies/VideoCompiler/…` (research.md §8),
   sets `CompileJob.status = SUCCEEDED` and `outputUri` accordingly (FR-011).
7. On any failure (I/O error, storage exhausted, an item becoming unreadable mid-job): deletes
   any partial output file, sets `CompileJob.status = FAILED` with a `failureReason`, and never
   leaves a corrupted/partial file visible to the user (FR-010).
8. Emits `progressPercent` updates throughout, consumed by `CompileForegroundService` to update
   its notification (FR-013).

## `CompileEngine.cancel(job: CompileJob)`

**Behavior guarantees**:
- Stops processing as soon as feasible.
- Deletes any partial output file.
- Sets `CompileJob.status = CANCELLED`.
- Called by `CompileForegroundService` when the hosting process is being torn down (e.g., the
  app was force-closed) per FR-013.
