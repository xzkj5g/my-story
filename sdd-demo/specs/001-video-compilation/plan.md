# Implementation Plan: Media Sequence Compiler

**Branch**: `001-video-compilation` | **Date**: 2026-09-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-video-compilation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Build a native Android app that lets a user select multiple existing media items — videos and/or
photos — from the device, arrange/reorder them into a sequence independent of selection order,
choose an output resolution + aspect ratio + frame rate from common presets, and compile the
sequence into a single output video saved to local device storage. Source clips whose aspect
ratio differs from the chosen output are fitted (letterboxed/pillarboxed) rather than cropped or
stretched — the same rule applies to photos. Photos display as a static frame (no pan/zoom) for a
fixed default duration of 3 seconds. Clips (and photo segments) without audio get silence
inserted so the output has one continuous audio track. Compilation runs as an Android foreground
service with a progress notification so it survives backgrounding, and stops cleanly if the app
is force-closed. Technical approach: use the official AndroidX Media3 Transformer/Composition
APIs (`androidx.media3:media3-transformer`) to perform concatenation, scaling/letterboxing,
frame-rate conversion, silent-audio generation, and static-image-to-video rendering, without
hand-rolling a custom MediaCodec pipeline, per the constitution's Simplicity/YAGNI and Android
tech stack principles.

## Technical Context

**Language/Version**: Kotlin (latest stable Kotlin toolchain shipped with the current stable
Android Gradle Plugin), targeting JVM 17 bytecode as per current Android Studio defaults.

**Primary Dependencies**: AndroidX Media3 (`media3-transformer`, `media3-effect`,
`media3-common`, `media3-exoplayer` for validation/probing) for video composition, encoding, and
static-image-to-video rendering (photos); AndroidX Jetpack Compose + Activity/Lifecycle/ViewModel
for UI; AndroidX Core (`ForegroundService` APIs) for the compile service; AndroidX MediaStore
APIs (via `MediaStore` ContentResolver, no extra library) for reading source media items
(videos/photos) and saving the output.

**Storage**: Device local storage only — source media items are referenced via `MediaStore`
(`content://` URIs), the compiled output is written back to `MediaStore` (`Movies` collection).
No database/cloud storage is required for v1 (Selection Sequence and Output Settings are
in-memory/ViewModel state for the duration of a single compile session; not persisted across app
restarts for v1).

**Testing**: JUnit 5/4 + Kotlin test for unit tests (domain logic: sequence reordering, preset
validation, silence/letterbox decision logic); AndroidX Test + Espresso/Compose UI testing for
instrumented UI flows (selection, reorder, output settings, compile trigger); a small set of
on-device instrumented tests using `media3-transformer`'s test utilities to assert produced output
resolution/aspect ratio/frame rate/clip order against fixture videos, plus assertions that
photos render as the correct fixed-duration static frame.

**Target Platform**: Android, minSdk 26 (Android 8.0) — chosen for broad device coverage while
still supporting the foreground service APIs and Media3 Transformer requirements needed by this
feature; compileSdk/targetSdk = latest stable Android SDK available at implementation time.

**Project Type**: Mobile app (single native Android app module; no separate backend/API).

**Performance Goals**: Compilation is CPU/GPU-bound hardware-encoder-driven work with no fixed
throughput target; success is measured by the spec's Success Criteria (e.g., SC-001: under 5
minutes of *interactive* user effort, excluding render/processing wait time) rather than a
frames/sec target.

**Constraints**: Fully offline-capable (no network dependency for core compile flow); no
product-imposed cap on media item count or total duration (FR-014) — bounded only by device
storage/memory, with a low-resource warning; compilation must survive backgrounding via a
foreground service (FR-013) and must never leave a partial/corrupted output file (FR-010).

**Scale/Scope**: Single-user, on-device, one compilation job at a time; source media items are
expected to include high-resolution footage (e.g., 2K at 60fps, ~1-2 minutes each) plus photos,
with potentially many items per job, per the user's stated usage pattern during clarification.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. User-Story-First Development | Feature is already decomposed into 3 independently testable, priority-ordered user stories (P1 compile, P2 reorder, P3 output settings) in spec.md; tasks (next phase) will preserve this decomposition. | PASS |
| II. Specification Before Implementation | spec.md + clarifications are complete and were approved before this plan was written; this plan only selects *how*, not *what*. | PASS |
| III. Simplicity & YAGNI | Uses official AndroidX Media3 Transformer/Composition APIs instead of a custom MediaCodec/FFmpeg pipeline; no additional abstraction layers introduced beyond standard UI/domain/data separation. | PASS |
| IV. Automated Testing Required (NON-NEGOTIABLE) | Testing section defines unit + instrumented test strategy covering every user story's acceptance scenarios; tasks phase will generate per-story tests before implementation. | PASS |
| V. Spec-Plan-Tasks-Code Consistency | This plan traces every functional requirement (FR-001..FR-017) to a component in Project Structure / data-model; research.md documents rationale for each technical decision, including the photo-support scope amendment added after initial planning. | PASS |
| VI. Small, Reviewable Commits | Not an architectural concern; will be enforced during `/speckit-tasks` and implementation by keeping tasks small and story-scoped. | PASS (deferred to implementation) |
| Technology Stack Requirements (Android) | Kotlin + Gradle + AndroidX/Jetpack (Compose, Media3) used throughout; architecture follows unidirectional data flow (UI → ViewModel → domain/data); Media3 is a first-party Jetpack library, so no extra third-party dependency justification is needed. | PASS |

No violations identified. Complexity Tracking table below is left empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-video-compilation/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
├── checklists/
│   └── requirements.md  # Spec quality checklist (/speckit-specify + /speckit-clarify)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
# Option: Single native Android app module (no backend/API — everything runs on-device)
app/
├── src/main/kotlin/com/example/videocompiler/
│   ├── ui/
│   │   ├── selection/        # Media picker screen — videos & photos (User Story 1)
│   │   ├── sequence/         # Reorder/remove screen (User Story 2)
│   │   ├── outputsettings/   # Resolution/aspect ratio/frame rate preset picker (User Story 3)
│   │   └── compile/          # Compile trigger + progress UI
│   ├── domain/
│   │   ├── model/            # SourceMediaItem (Video|Photo), SelectionSequence, OutputSettings, CompileJob
│   │   └── usecase/          # ReorderSequence, ValidateSourceMediaItem, StartCompileJob, etc.
│   ├── data/
│   │   └── mediastore/       # MediaStore-backed repository: list/query/save media items
│   ├── media/
│   │   └── compiler/         # Media3 Transformer/Composition wrapper (concat, fit, silence, photo-to-video)
│   └── service/
│       └── CompileForegroundService.kt   # FR-013 background-survivable compile job runner
├── src/test/kotlin/…          # Unit tests: domain + media composition-decision logic
└── src/androidTest/kotlin/…   # Instrumented tests: UI flows + end-to-end compile assertions
```

**Structure Decision**: Single Android app module (`app/`) organized by feature area under
`ui/` (mirroring the three user stories) with shared `domain/`, `data/`, `media/`, and `service/`
layers, per the constitution's required unidirectional UI → domain/data architecture. No separate
backend/API module is needed since all processing happens on-device.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations — table intentionally left empty.
