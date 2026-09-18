# Data Model: Media Sequence Compiler

Derived from the Key Entities in `spec.md` and the Functional Requirements. This is a logical
model for in-app/in-memory state (no persistent database is required for v1, per research.md §2).

## SourceMediaItem

Represents one media item (video or photo) already on the user's device, referenced (not
copied) into a compilation job.

| Field | Type | Notes |
|---|---|---|
| `uri` | content URI (string) | `MediaStore` URI identifying the file; the durable identity of a SourceMediaItem (FR-001). |
| `mediaType` | enum: `VIDEO`, `PHOTO` | Discriminates which fields/behavior apply (e.g., `frameRate`/`hasAudioTrack` are video-only; photos use the fixed display duration instead). |
| `displayName` | string | Human-readable file name, for UI display during selection/reordering. |
| `durationMs` | long, video-only | Native duration; used for progress estimation and audio silence generation length. Not applicable to photos (see `PHOTO_DISPLAY_DURATION_MS` below). |
| `width`, `height` | int | Native pixel dimensions; used to compute native aspect ratio for fit/letterbox decisions (FR-008), for both videos and photos. |
| `frameRate` | float, video-only | Native frame rate; compared against the chosen Output Settings frame rate (FR-008 acceptance scenario 4). Not applicable to photos. |
| `hasAudioTrack` | boolean, video-only | Determines whether silent audio must be generated for this clip's segment (FR-009). Always treated as `false` for photos (photos have no audio; silence is generated for their full display duration). |
| `validationState` | enum: `VALID`, `INVALID_UNREADABLE` | Set at selection time by the validation use case (FR-015); only `VALID` entries may be added to a Selection Sequence. |

**Constant**: `PHOTO_DISPLAY_DURATION_MS = 3000` — the fixed default duration every photo is
displayed for in the compiled output (FR-017); not user-adjustable per photo in v1.

**Validation rules**:
- A SourceMediaItem MUST have `validationState == VALID` (readable video/image content) before
  it can be added to a Selection Sequence (FR-015).
- If a SourceMediaItem becomes unreadable after being added (e.g., underlying file deleted/moved),
  its state transitions to `INVALID_UNREADABLE` and the containing Selection Sequence surfaces a
  user notification before/at compile time (FR-016) — see Lifecycle below.

## SelectionSequence

The user-defined, ordered list of `SourceMediaItem` entries (videos and/or photos) chosen for
one compilation job. Order is independent of the order in which items were originally selected
(FR-002).

| Field | Type | Notes |
|---|---|---|
| `items` | ordered list of `SourceMediaItem` references | Defines playback/display order in the Compiled Output Video (FR-006). |

**Operations** (all mutate `items` and MUST leave no gaps/duplicates per spec Acceptance
Scenarios):
- `append(sourceMediaItem)` — adds a validated SourceMediaItem to the end of the sequence.
- `move(fromIndex, toIndex)` — reorders an entry (FR-003).
- `remove(index)` — removes an entry, shifting subsequent entries up with no gap (FR-004).

**Validation rules**:
- MAY contain as few as 1 entry (FR-012 allows single-item compilation).
- MUST NOT contain a SourceMediaItem whose `validationState != VALID` at the moment it is added.
- No product-imposed maximum length; bounded only by device storage/memory (FR-014).

## OutputSettings

The user-chosen combination of resolution, aspect ratio, and frame rate presets that the
Compiled Output Video must conform to (FR-005, FR-007).

| Field | Type | Notes |
|---|---|---|
| `resolutionTier` | enum: `R720P`, `R1080P`, `R2K`, `R4K` | See research.md §7 for concrete pixel values per tier. |
| `aspectRatio` | enum: `RATIO_16_9`, `RATIO_9_16`, `RATIO_1_1` | Combined with `resolutionTier` to derive final output width×height. |
| `frameRate` | enum: `FPS_24`, `FPS_30`, `FPS_60` | Target output frame rate. |

**Derived value**: `outputWidth × outputHeight` = the pixel dimensions produced by combining
`resolutionTier` and `aspectRatio` (research.md §7 table).

**Validation rules**: All three fields are required and MUST be one of the defined preset enum
values; no free-form/custom numeric entry in v1 (per Clarifications).

## CompileJob

Represents one in-progress or completed compilation run, tying a `SelectionSequence` and
`OutputSettings` together with runtime status (drives the foreground service / progress UI,
FR-013).

| Field | Type | Notes |
|---|---|---|
| `sequence` | `SelectionSequence` (snapshot) | The ordered media items being compiled; snapshotted at job start so later selection edits don't affect an in-flight job. |
| `outputSettings` | `OutputSettings` (snapshot) | The target format for this job. |
| `status` | enum: `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED` | `FAILED`/`CANCELLED` MUST NOT leave a partial output file (FR-010). |
| `progressPercent` | int (0-100) | Drives the foreground service notification. |
| `failureReason` | string, nullable | Set when `status == FAILED` (e.g., "source media item became unavailable" per FR-016, "insufficient storage"). |
| `outputUri` | content URI, nullable | Set when `status == SUCCEEDED`; the saved `MediaStore` location of the Compiled Output Video (FR-011). |

**Lifecycle / state transitions**:

```text
RUNNING → SUCCEEDED   (all items compiled, output saved to MediaStore)
RUNNING → FAILED      (an item became unreadable [FR-016], storage ran out, or another I/O error;
                        any partial output is deleted, user is notified)
RUNNING → CANCELLED   (app force-closed while running; service stops, no partial output kept)
```

A `CompileJob` never transitions away from a terminal state (`SUCCEEDED`/`FAILED`/`CANCELLED`).

## CompiledOutputVideo

The single video file produced by a successfully `SUCCEEDED` CompileJob (FR-006, FR-011).

| Field | Type | Notes |
|---|---|---|
| `uri` | content URI | `MediaStore` location (`Movies/VideoCompiler/…`, research.md §8). |
| `resolution`, `aspectRatio`, `frameRate` | same enums as `OutputSettings` | MUST exactly match the `OutputSettings` used to produce it (FR-007, SC-003). |
| `itemOrder` | ordered list of `SourceMediaItem` references | MUST match the originating `SelectionSequence.items` order exactly (SC-002). |

## Entity Relationships

```text
SelectionSequence 1 ──── * SourceMediaItem   (ordered membership; VIDEO or PHOTO)
CompileJob        1 ──── 1 SelectionSequence (snapshot)
CompileJob        1 ──── 1 OutputSettings   (snapshot)
CompileJob        1 ──── 0..1 CompiledOutputVideo  (present only when status == SUCCEEDED)
```
