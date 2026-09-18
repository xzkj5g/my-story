# Feature Specification: Media Sequence Compiler

**Feature Branch**: `001-video-compilation`

**Created**: 2026-09-17

**Status**: Draft

**Input**: User description: "i want to have an android native app to output a video from selected videos on device. a couple of features must have: 1) user should be able to define output video resolution, aspect ratio, frame-rate. 2) the output video is compiled in a sequential way from the selected ones, and user should be able to re-order the sequence of the selected ones regardless the order when they were selected"
Amendment: the user also wants to be able to select photos, not just videos, as part of the
same sequence (identified after initial planning; incorporated via follow-up clarification below).

## Clarifications

### Session 2026-09-17

- Q: When only one video is selected, should compilation be blocked, or should it still produce
  an output equal to that single clip? → A: Allow — a single selected video is still compiled
  (re-encoded to the chosen output settings) and produced as the output.
- Q: If the user backgrounds or closes the app while compilation is running, should compilation
  keep running, or be cancelled? → A: Use a foreground service with a persistent progress
  notification; compilation continues while the app is backgrounded/screen is off, and stops if
  the app is force-closed/swiped away from Recents.
- Q: Is there a maximum number of clips or maximum total duration the app must support per
  compilation job? → A: No hard limit — bounded only by device storage/memory, with a warning
  shown to the user if device resources run low.
- Q: How should the app handle a selected source file that is invalid/corrupted/unsupported? →
  A: Validate every video at selection time and block adding an unreadable/corrupted file to the
  sequence in the first place. If a previously valid clip becomes unreadable or unavailable later
  (e.g., deleted, moved, or access revoked) before or during compilation, the app MUST notify the
  user immediately and MUST NOT silently proceed using that clip.
- Q: When a selected clip has no audio track (or some clips have audio and others don't), should
  the app insert silence for that clip's segment, or leave the output with gaps in audio? → A:
  Insert silence for video-only clip segments so the compiled output has one continuous audio
  track for its full duration.
- Q: Should the user also be able to select photos (not just videos) into the same sequence? →
  A: Yes — photos are a supported source media type alongside videos, discovered as a scope gap
  after initial planning.
- Q: How long should each photo display in the compiled output? → A: A fixed default duration
  (3 seconds) applied to every photo; not user-adjustable per-photo in v1.
- Q: Should photos follow the same fit-without-cropping rule as videos when their aspect ratio
  doesn't match the output? → A: Yes — identical fit/pad behavior as videos (no crop, no
  stretch).
- Q: Should photos have a pan/zoom (Ken Burns) effect, or display as a static frame? → A: Static
  — photos display as a still frame for their duration; no pan/zoom effect in v1.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Combine selected media (photos and videos) into one output (Priority: P1)

A user picks two or more media items — videos, photos, or a mix of both — already stored on
their device and produces a single output video that plays each selected item one after another,
in the order the user last arranged them. Photos display as a still frame for a fixed duration;
videos play in full.

**Why this priority**: This is the core value of the app — without it there is nothing to
reorder or configure. It is the smallest slice that is independently useful: a user can already
turn several media items into one shareable video.

**Independent Test**: Select 2+ existing media items (mixing photos and videos), confirm
compilation with default output settings, and verify a single new video file is produced that
plays the selected items back-to-back in the chosen order, with each photo shown for its fixed
display duration.

**Acceptance Scenarios**:

1. **Given** the user has at least two media items (photos and/or videos) on their device,
   **When** they select several of them and start compilation, **Then** the app produces one
   output video file containing each selected item, played/displayed in full, one after another.
2. **Given** only one media item is selected, **When** the user starts compilation, **Then** the
   app produces an output video equal to that single item (re-encoded video, or the photo shown
   for its fixed display duration), using the chosen output settings.
3. **Given** compilation is in progress, **When** the user backgrounds the app or the screen
   turns off, **Then** compilation continues running with a visible progress notification, and
   completes normally when the app is reopened.
4. **Given** compilation is in progress, **When** the user force-closes the app (e.g., swipes it
   away from Recents), **Then** compilation stops and no partial/corrupted output file is left in
   the user's media library.
5. **Given** a photo is included in the sequence, **When** compilation runs, **Then** the photo
   appears in the output as a static frame (no pan/zoom effect) for a fixed default duration of
   3 seconds.

---

### User Story 2 - Reorder selected media before compiling (Priority: P2)

After selecting several media items (in any order), the user rearranges the playback sequence to
be different from the order in which the items were originally selected.

**Why this priority**: Selection order rarely matches the desired storytelling order. This
capability is what makes the app useful for intentional editing rather than an arbitrary
concatenation tool, but it depends on User Story 1 already working.

**Independent Test**: Select 3+ videos, reorder them into a sequence different from the
selection order, compile, and verify the output plays clips in the newly defined order — not the
original selection order.

**Acceptance Scenarios**:

1. **Given** 3+ media items have been selected, **When** the user moves an item to a new
   position in the sequence, **Then** the displayed sequence updates immediately to reflect the
   new order.
2. **Given** the user has reordered the sequence, **When** they start compilation, **Then** the
   output video plays/displays items strictly in the reordered sequence, not the original
   selection order.
3. **Given** the user is reordering, **When** they remove an item from the sequence, **Then**
   the remaining items keep their relative order and no gap or duplicate remains.

---

### User Story 3 - Configure output resolution, aspect ratio, and frame rate (Priority: P3)

Before compiling, the user chooses the output video's resolution, aspect ratio, and frame rate
from a set of common presets, and the app fits every selected media item (photo or video) into
that format.

**Why this priority**: Useful and expected by most users, but the app already delivers value via
P1 and P2 even with a single fixed default output format, so this is layered on top.

**Independent Test**: Select media items with different native resolutions/aspect ratios/frame
rates than a chosen preset, pick a specific output preset, compile, and verify the output file
matches the selected resolution, aspect ratio, and frame rate, with every source item fully
visible (no cropping) inside that frame.

**Acceptance Scenarios**:

1. **Given** the user opens output settings, **When** they view the options, **Then** they see a
   defined list of common resolution, aspect ratio, and frame rate presets to choose from (not a
   free-form numeric entry).
2. **Given** the user selects a specific resolution/aspect ratio/frame rate preset, **When** they
   compile, **Then** the output file's resolution, aspect ratio, and frame rate match the chosen
   preset exactly.
3. **Given** a selected media item's (photo or video) native aspect ratio differs from the
   chosen output aspect ratio, **When** compilation runs, **Then** the app fits the full item
   inside the output frame (adding padding as needed) without cropping any part of its image and
   without distorting/stretching it.
4. **Given** a selected source clip's native frame rate differs from the chosen output frame
   rate, **When** compilation runs, **Then** the clip plays at the output frame rate without
   changing its playback speed/duration.

---

### Edge Cases

- A selected media item that is deleted, moved, or becomes inaccessible after selection but
  before/during compilation: the app notifies the user immediately and does not silently proceed
  using that item (see Clarifications).
- A source clip with no audio track (or a mix of clips with/without audio) has silence inserted
  for its segment so the output maintains one continuous audio track (see Clarifications).
- A selected photo is in an unsupported/corrupted image format: rejected at selection time, same
  as an unsupported video (see FR-015, generalized to all media types).
- If the device runs out of storage space during compilation, this is treated as a compilation
  failure: the app notifies the user clearly and does not leave a partial/corrupted output file
  (see FR-010).
- Selecting a very large number of items or a very long combined duration is expected and
  supported up to device storage/memory limits; the app warns the user if device resources run
  low rather than enforcing an arbitrary product limit (see Clarifications).
- Invalid/corrupted/unsupported source files are rejected at selection time and cannot be added
  to the sequence in the first place (see Clarifications).
- Backgrounding/closing the app during compilation is addressed via a foreground service with a
  progress notification; force-closing the app stops compilation cleanly (see Clarifications).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST allow the user to select multiple existing media items — videos
  and/or photos — from the device's local storage.
- **FR-002**: The app MUST display the currently selected media items as an ordered sequence,
  independent of the order in which they were selected.
- **FR-003**: The app MUST allow the user to reorder the selected media items into any sequence
  before compiling.
- **FR-004**: The app MUST allow the user to remove a media item from the selected sequence
  before compiling.
- **FR-005**: The app MUST allow the user to choose the output video's resolution, aspect ratio,
  and frame rate from a defined list of common presets prior to compilation.
- **FR-006**: The app MUST compile the selected media items into a single output video file that
  plays/displays each selected item in full, back-to-back, in the exact order defined by the
  user's sequence.
- **FR-007**: The app MUST produce an output video whose resolution, aspect ratio, and frame rate
  match the preset chosen by the user, regardless of the native resolution, aspect ratio, or frame
  rate of the individual source items.
- **FR-008**: When a source media item's native aspect ratio does not match the chosen output
  aspect ratio, the app MUST fit the entire item within the output frame (padding as needed)
  rather than cropping any part of its visible image or distorting its proportions. This applies
  identically to both photos and videos.
- **FR-009**: The app MUST preserve each source clip's original audio track (when present) in the
  compiled output, in sync with its video content, and MUST insert silence for any clip segment
  (or any photo segment) that has no audio track so the output has one continuous audio track for
  its full duration.
- **FR-010**: The app MUST notify the user clearly if compilation fails (e.g., inaccessible
  source file, insufficient storage) and MUST NOT leave a corrupted or partial output file in the
  user's media library.
- **FR-011**: The app MUST save the successfully compiled output video to the device's local
  storage in a commonly playable format.
- **FR-012**: The app MUST allow compilation with only one selected media item, producing an
  output equal to that single item, re-encoded/rendered to the chosen output settings.
- **FR-013**: The app MUST continue running a compilation job (via a foreground service with a
  visible progress notification) when the app is backgrounded or the screen turns off, and MUST
  stop the job cleanly, with no partial/corrupted output, if the app is force-closed.
- **FR-014**: The app MUST NOT enforce an arbitrary maximum number of media items or maximum
  total duration; it MUST support as many items/duration as device storage and memory allow, and
  MUST warn the user if device resources run low during selection or compilation.
- **FR-015**: The app MUST validate each media item at selection time and MUST block adding an
  unreadable, corrupted, or unsupported file (video or photo) to the selection sequence.
- **FR-016**: If a previously valid selected media item becomes unreadable or unavailable before
  or during compilation (e.g., deleted, moved, or access revoked), the app MUST notify the user
  immediately and MUST NOT silently proceed with compiling that item.
- **FR-017**: The app MUST display each selected photo in the compiled output as a static frame,
  with no pan/zoom or other motion effect, for a fixed default duration of 3 seconds.

### Key Entities

- **Source Media Item**: A video or photo file already existing on the user's device, referenced
  (not copied) during selection; identified by its device storage location and a media type
  (Video or Photo), with attributes such as native resolution, aspect ratio, and — for videos —
  frame rate and duration (photos use the fixed default display duration instead).
- **Selection Sequence**: The user-defined, ordered list of Source Media Items (videos and/or
  photos) chosen for a single compilation job; order is independent of and may differ from the
  order in which items were selected.
- **Output Settings**: The user-chosen preset combination of resolution, aspect ratio, and frame
  rate that the compiled output video must conform to.
- **Compiled Output Video**: The single video file produced by joining all items in the Selection
  Sequence, conforming to the chosen Output Settings, saved to device storage.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can select media items, arrange them into a desired sequence, and produce a
  compiled output video in under 5 minutes of interactive effort (excluding processing/wait time).
- **SC-002**: 95% of compiled outputs play back all selected items in the exact user-defined
  order with no missing, duplicated, or truncated items.
- **SC-003**: 100% of compiled outputs match the user-selected resolution, aspect ratio, and
  frame rate preset.
- **SC-004**: 100% of source items (photos and videos) whose native aspect ratio differs from
  the output preset appear in the compiled output with their full image visible (no cropped
  content).
- **SC-005**: Users can successfully reorder a sequence of at least 10 selected media items
  without the app losing or misordering any entry.
- **SC-006**: Compilation jobs continue to completion when the app is backgrounded or the screen
  is turned off, with no loss of progress, for the full duration of the render.
- **SC-007**: 100% of invalid/corrupted/unsupported media files are rejected at selection time,
  before they can be added to the sequence.
- **SC-008**: 100% of photos in a compiled output display for exactly the fixed default duration
  (3 seconds) as a static frame, with no pan/zoom motion applied.

## Assumptions

- Target platform is a native Android application; source media items are read from on-device
  storage the user already has permission to access (e.g., gallery/media store).
- "Common presets" for resolution/aspect ratio/frame rate follow widely used industry defaults
  (e.g., resolutions such as 720p/1080p/4K, aspect ratios such as 16:9, 9:16, and 1:1, and frame
  rates such as 24/30/60 fps); the exact preset list is a design decision to be finalized during
  planning, not a per-user custom entry.
- Mismatched source aspect ratios are resolved by fitting the full clip into the output frame with
  padding, per explicit user decision — cropping and stretching are both out of scope for v1.
- Output videos are saved locally on the device; cloud upload/sharing integrations are out of
  scope for v1.
- A minimum of two selected media items is not required; a single selected item is a valid
  compilation job and produces a re-encoded/rendered output of that item (see Clarifications).
- Audio tracks, when present on a source clip, are carried through unmodified (no re-mixing,
  volume normalization, or muting) in v1; clips (and photo segments, which have no audio) have
  silence inserted for their segment to keep one continuous output audio track (see
  Clarifications).
- Compilation runs as a foreground service with a progress notification so it survives
  backgrounding/screen-off, consistent with standard Android media-processing app behavior; it is
  not guaranteed to survive a force-close of the app.
- There is no product-imposed cap on media item count or total duration; the practical ceiling
  is device storage and memory, with a low-resource warning as the only guardrail.
- Photos display for a fixed default duration of 3 seconds each, as a static frame with no
  pan/zoom effect; this duration is not user-adjustable per photo in v1 (see Clarifications).
- Supported photo formats follow standard Android-supported image formats (e.g., JPEG, PNG,
  WebP, HEIC where supported by the device); unsupported/corrupted formats are rejected at
  selection time per FR-015.
