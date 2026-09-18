# Phase 0 Research: Video Sequence Compiler

All items below were either already concrete in the Technical Context (no `NEEDS
CLARIFICATION` markers were left) or are design decisions deferred from the spec's Assumptions.
This document records the decision, rationale, and rejected alternatives for each.

## 1. Video composition/encoding engine

- **Decision**: Use AndroidX Media3's `Transformer` + `Composition`/`EditedMediaItemSequence`
  APIs (`androidx.media3:media3-transformer`, `media3-effect`) to concatenate clips, convert
  frame rate, and fit mismatched aspect ratios via the built-in `Presentation` effect
  (letterbox/pillarbox scaling).
- **Rationale**: This is Google's official, currently supported API for exactly this use case
  (sequencing multiple source clips into one output with transformations), actively maintained,
  hardware-accelerated where the device supports it, and requires no native/NDK code or
  third-party licensing (unlike FFmpeg). Directly satisfies the constitution's Simplicity/YAGNI
  principle and its Android tech-stack requirement to prefer official Jetpack libraries.
- **Alternatives considered**:
  - **General hand-rolled `MediaCodec`/`MediaMuxer` pipeline**: full control, but a large,
    error-prone amount of code to reimplement scaling, padding, composition, and audio silence
    generation that Media3 already provides — rejected as unnecessary complexity (violates
    Simplicity/YAGNI). A narrowly scoped exception is retained for low-fps upsampling because
    Media3 1.10.1 cannot produce additional output frames when the source is below the target fps.
  - **FFmpeg via a wrapper library (e.g., mobile-ffmpeg forks)**: powerful and format-flexible,
    but adds a large third-party native dependency, licensing considerations (GPL/LGPL builds),
    and duplicates functionality Media3 already covers — rejected.

## 2. Handling mismatched aspect ratio (fit without crop/stretch)

- **Decision**: Apply Media3's `Presentation` effect configured to fit-within-bounds (equivalent
  to "letterbox/pillarbox"), which scales each source frame to fit inside the target frame while
  preserving its native aspect ratio, then pads the remaining area (typically black bars).
- **Rationale**: Matches FR-008 exactly (no cropping, no distortion) and is a built-in, tested
  Media3 effect rather than custom canvas/matrix math.
- **Alternatives considered**: Custom `GlEffect`/shader-based scaling — rejected as
  reinventing an already-solved, first-party capability.

## 3. Frame rate conversion

- **Decision**: Configure the `Transformer`'s output video format with the user-selected target
  frame rate for normal conversion. Media3 1.10.1 correctly caps higher-fps sources by dropping
  frames, but does not upsample a source whose native fps is below the target. For that direction,
  `FrameRateUpsampler` sequentially decodes display-order frames with `MediaCodec`, holds each
  decoded YUV frame, duplicates it into evenly spaced target-fps slots, and re-encodes the
  temporary video before it enters the Media3 composition.
- **Rationale**: This hybrid keeps Media3 responsible for composition, scaling, audio, and photo
  rendering while satisfying FR-007 for lower-fps sources. Sequential decode avoids corrupting
  B-frame reference order and avoids the O(n²) behavior of independently seeking with
  `MediaMetadataRetriever.getFrameAtTime()`. The implementation is covered by the T054
  instrumented 30fps-to-60fps test and the existing output-settings suite.
- **Alternatives considered**: Container-level encoded-sample duplication was rejected because
  rewriting timestamps without decoding corrupts B-frame picture-order/reference continuity.
  Per-output-slot `MediaMetadataRetriever` seeking was rejected because it re-decodes each GOP
  repeatedly and becomes O(n²). A general custom MediaCodec pipeline remains out of scope.

## 4. Silent audio track insertion for video-only clips

- **Decision**: For any `EditedMediaItem` whose source clip has no audio track, generate a
  silent audio track for that segment's duration (Media3 supports composing sequences with mixed
  audio/no-audio inputs by supplying a matching silent audio source, e.g., via
  `SilenceMediaSource`/an `EditedMediaItem` audio-only silent clip of the same duration) so the
  composed output has one continuous audio track, per the Clarifications' resolved decision.
- **Rationale**: Directly satisfies FR-009 and avoids audio-track gaps that can cause playback
  glitches in some players.
- **Alternatives considered**: Leaving audio gaps — rejected per explicit user clarification.

## 5. Foreground service for background-survivable compilation

- **Decision**: Run the compile job inside a Started Foreground Service
  (`CompileForegroundService`) using the `dataSync` foreground service type — resolved during
  implementation (T028) since `mediaProcessing` is only a valid `foregroundServiceType` value
  starting at API 35, while this project's `compileSdk`/`targetSdk` is 34 — showing an ongoing
  `Notification` with progress. The service is stopped and the job aborted cleanly (no partial
  output committed to `MediaStore`) if the process is killed (e.g., user force-closes the app
  from Recents).
- **Rationale**: Directly satisfies FR-013/SC-006 and matches documented Android guidance and
  industry-standard behavior for long-running media export (confirmed during `/speckit-clarify`).
- **Alternatives considered**: `WorkManager` for the compile job — rejected as the primary
  mechanism because `WorkManager` is optimized for deferrable/retryable background work, not for
  a user-initiated, time-sensitive, progress-visible job the user is actively waiting on; a
  foreground service is the documented pattern for this scenario. `WorkManager` may still be used
  later for auxiliary deferrable work (out of scope for v1).

## 6. Source video validation at selection time (reject corrupted/unsupported files)

- **Decision**: When a media item (video or photo) is added to the selection, probe it before
  allowing it into the Selection Sequence: videos via Media3's `MediaExtractor`/
  `MetadataRetriever` (or `ExoPlayer`'s format probing) to confirm a readable video track;
  photos via Android's `BitmapFactory.decodeStream` (bounds-only decode) or
  `ImageDecoder`/`ExifInterface` to confirm a readable, supported image. On failure, reject
  immediately with an error message and do not add it (FR-015).
- **Rationale**: Reuses the same media stack already in place for compilation/rendering; no extra
  third-party parsing library needed for either media type.
- **Alternatives considered**: Deferring all validation to compile time — rejected per
  Clarifications (validate at selection time to avoid losing a large in-progress selection to one
  bad file).

## 6b. Rendering photos as video segments

- **Decision**: Use AndroidX Media3's built-in support for image inputs in a `Composition`
  (an `EditedMediaItem` built from an image URI with an explicit `durationUs` and the sequence's
  target frame rate) to render each selected photo as a static-frame video segment of the fixed
  3-second default duration, with no animation/effects applied (matching the "static, no Ken
  Burns" decision). This is composed into the same `EditedMediaItemSequence` as video items, so
  concatenation, fit/letterboxing, and silent-audio insertion apply identically to photo segments
  (FR-008, FR-009, FR-017).
- **Rationale**: Avoids a separate/custom image-to-video encoding path; Media3 already unifies
  image and video assets in one composition pipeline, keeping the Simplicity/YAGNI principle
  intact.
- **Alternatives considered**: Manually rendering each photo to a short-lived local video file
  (e.g., via `MediaCodec` + `Canvas` frame generation) before feeding it into the same pipeline as
  videos — rejected as unnecessary custom encoding work that Media3's image-asset support already
  covers.

## 7. Output resolution / aspect ratio / frame rate preset list

- **Decision**: Ship a fixed, non-custom preset list for v1:
  - **Resolutions**: 720p (1280×720), 1080p (1920×1080), 2K (2560×1440), 4K (3840×2160)
  - **Aspect ratios**: 16:9 (landscape), 9:16 (portrait), 1:1 (square)
  - **Frame rates**: 24 fps, 30 fps, 60 fps
  - The effective output pixel dimensions are derived by combining the chosen resolution "tier"
    with the chosen aspect ratio (e.g., 1080p + 9:16 → 1080×1920).
- **Rationale**: Covers the common short-form/landscape/portrait use cases (matches the user's
  stated 2K/60fps source footage) without the added scope of free-form numeric entry, per the
  Clarifications decision (presets-only).
- **Alternatives considered**: Free-form width/height/fps entry — explicitly rejected by the
  user during `/speckit-specify` clarification (chose "presets only").

## 8. Output file save location

- **Decision**: Save the compiled output via `MediaStore.Video` into the standard `Movies`
  collection (e.g., `Movies/VideoCompiler/`), making it immediately visible in the device's
  gallery/media apps, consistent with FR-011 ("commonly playable format" saved to local storage).
- **Rationale**: Standard, discoverable location using the public `MediaStore` API; no extra
  permission beyond scoped storage write access via `MediaStore` is required on modern Android.
- **Alternatives considered**: App-private storage (`getExternalFilesDir`) — rejected because the
  output would not appear in the user's gallery, which is the expected place a saved video shows
  up.

## 9. Media/storage permission handling

- **Decision**: Request the appropriate runtime read permissions for media (`READ_MEDIA_VIDEO`
  and `READ_MEDIA_IMAGES` on Android 13+, `READ_EXTERNAL_STORAGE` on older versions, handled via
  the AndroidX permission APIs) before allowing video/photo selection; if denied, show a clear
  in-app message explaining the picker cannot function without it and offer a retry/settings
  shortcut. This is an implementation-level detail of FR-001, not a new functional requirement.
- **Rationale**: Standard Android runtime permission pattern; no new product decision required
  beyond what the spec already implies (the app needs to read the user's videos and photos).
- **Alternatives considered**: Using the system Photo Picker (`ACTION_PICK_IMAGES`/
  `PickVisualMedia`) to avoid broad storage permissions entirely — noted as a strong candidate to
  reduce permission friction, and a particularly good fit now that photos are in scope (the
  system Photo Picker natively supports mixed photo/video selection); will be evaluated further
  during task breakdown as it may allow skipping the broad `READ_MEDIA_VIDEO`/`READ_MEDIA_IMAGES`
  permissions for the selection flow (Android 13+ Photo Picker does not require them). Documented
  here as the preferred direction, to be finalized in tasks.
- **T050 polish-phase finding**: On API 26-28 (below API 29's scoped storage), inserting the
  compiled output into `MediaStore.Video` also requires `WRITE_EXTERNAL_STORAGE` — from API 29
  onward, scoped storage lets an app write its own `MediaStore` entries without this permission.
  Since this app's `minSdk` is 26, `WRITE_EXTERNAL_STORAGE` (capped with
  `android:maxSdkVersion="28"`) was added to the manifest and to
  `MediaPermissionHelper.requiredPermissions()`'s pre-Q branch so it is requested at runtime
  alongside `READ_EXTERNAL_STORAGE`. No permission beyond this is requested at any API level; the
  app does not request `MANAGE_EXTERNAL_STORAGE` or any all-files-access permission.

## Summary

All previously open Technical Context items are resolved above; there are no remaining `NEEDS
CLARIFICATION` markers in this plan. §6b and the updates to §9 reflect the photo-support scope
amendment added after the initial planning pass.
