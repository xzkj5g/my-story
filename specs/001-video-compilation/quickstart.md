# Quickstart: Validating Media Sequence Compiler

This guide describes how to manually validate that the implemented feature meets `spec.md`'s
Acceptance Scenarios and Success Criteria end-to-end on a physical or emulator Android device. It
does not include implementation code — see `data-model.md` and `contracts/` for component
behavior, and `tasks.md` (from `/speckit-tasks`) for build tasks.

## Prerequisites

- Android device or emulator running the app's minSdk (Android 8.0) or newer, with at least
  ~2 GB free storage.
- At least 3 sample video files already present on the device/emulator (e.g., pushed via
  `adb push sample1.mp4 sample2.mp4 sample3.mp4 /sdcard/Movies/`), ideally with **different
  native resolutions/aspect ratios/frame rates** from each other so User Story 3 can be verified.
- At least 2 sample photo files (e.g., pushed via `adb push photo1.jpg photo2.jpg
  /sdcard/Pictures/`), ideally with a different aspect ratio than the videos, to validate photo
  support (FR-017).
- At least 1 sample video with **no audio track** (e.g., generated via any video tool with `-an`)
  to validate FR-009 silence insertion.
- The debug build of the app installed (`./gradlew installDebug` or run from Android Studio).

## Scenario 1 — Compile a sequence in selection order (User Story 1, P1)

1. Launch the app and open the media picker.
2. Select 2 of the sample videos (in any order).
3. Start compilation using default output settings.
4. **Expect**: a new video appears in the device gallery (`Movies/VideoCompiler/…`) that plays
   the first selected clip fully, then the second, with no gap/overlap (SC-002).

## Scenario 1b — Mixed photo + video sequence (User Story 1, P1; FR-017)

1. Select 1 sample photo and 1 sample video (in that order).
2. Start compilation using default output settings.
3. **Expect**: output video shows the photo as a static frame for exactly 3 seconds (no
   pan/zoom), then plays the video clip in full, with continuous audio (silence during the
   photo segment) (FR-009, FR-017, SC-008).

## Scenario 2 — Single-item compile (FR-012)

1. Select only 1 video (repeat with only 1 photo).
2. Start compilation.
3. **Expect**: output video equals that single item — the video re-encoded, or the photo shown
   for its fixed 3-second duration — to the chosen output settings (not blocked).

## Scenario 3 — Reorder before compiling (User Story 2, P2)

1. Select 3 sample videos in the order A, B, C.
2. In the sequence screen, move C to the first position (new order: C, A, B).
3. Remove B.
4. Start compilation.
5. **Expect**: output plays C, then A only — reflecting the edited order, not the original
   selection order (Acceptance Scenarios 1-3 of User Story 2).

## Scenario 4 — Output settings + fit without cropping (User Story 3, P3)

1. Select 2 videos with a native aspect ratio different from 16:9 (e.g., one portrait clip).
2. Open output settings; confirm only preset options are shown (no free-form numeric entry) per
   FR-005/research.md §7.
3. Choose a 16:9, 1080p, 30fps preset.
4. Compile.
5. **Expect**: output file is exactly 1920×1080 at 30fps (SC-003); the portrait clip's full image
   is visible with padding (no cropped edges, no stretching) (SC-004).

## Scenario 5 — Silent clip audio handling (FR-009)

1. Include the no-audio sample video in the sequence alongside clips that do have audio.
2. Compile.
3. **Expect**: output has one continuous audio track for its entire duration — silence during the
   no-audio clip's segment, not a gap or crash.

## Scenario 6 — Backgrounding survives, force-close does not (FR-013, SC-006)

1. Select several videos with enough total duration that compilation takes at least ~15-20
   seconds.
2. Start compilation; immediately press the device Home button (background the app) and lock the
   screen.
3. **Expect**: a progress notification is visible; wait for it to reach 100%/"complete"; reopen
   the app or tap the notification.
4. **Expect**: the compiled output exists in the gallery matching the selected sequence.
5. Repeat, but this time swipe the app away from Recents shortly after starting compilation.
6. **Expect**: the job stops, the notification is dismissed/cancelled, and no partial/corrupted
   file appears in the gallery (FR-010).

## Scenario 7 — Reject invalid/corrupted file at selection time (FR-015, SC-007)

1. Prepare a corrupted/truncated video file (e.g., a `.mp4` with random bytes appended after
   truncation) and a corrupted image file (e.g., a `.jpg` truncated mid-file), and place both on
   the device.
2. Attempt to select each of them in the media picker.
3. **Expect**: the app rejects both immediately with a clear error message; neither appears in
   the Selection Sequence.

## Scenario 8 — Item becomes unavailable before compile (FR-016)

1. Select 2 valid media items (e.g., 1 video, 1 photo).
2. Before starting compilation, delete one of the selected files from the device (e.g., via
   `adb shell rm` or a file manager) without removing it from the app's in-progress sequence.
3. Start compilation.
4. **Expect**: the app notifies the user that an item became unavailable and does not silently
   compile using it (per `contracts/media-validation.md`'s `RevalidateSourceMediaItem` contract).

## Success Criteria Traceability

| Success Criterion | Verified by |
|---|---|
| SC-001 (under 5 min interactive effort) | Manual timing during Scenarios 1/3/4. |
| SC-002 (exact user-defined order, no gaps/dupes) | Scenario 3. |
| SC-003 (output matches chosen preset exactly) | Scenario 4. |
| SC-004 (mismatched items fully visible, no cropping) | Scenario 4. |
| SC-005 (reorder ≥10 items without loss) | Repeat Scenario 3 with 10+ items. |
| SC-006 (continues through backgrounding) | Scenario 6. |
| SC-007 (invalid files rejected at selection) | Scenario 7. |
| SC-008 (photos display for fixed 3s, static, no motion) | Scenario 1b. |
