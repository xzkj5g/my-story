---

description: "Task list template for feature implementation"
---

# Tasks: Media Sequence Compiler

**Input**: Design documents from `/specs/001-video-compilation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md (all present)

**Tests**: The constitution's Principle IV (Automated Testing Required, NON-NEGOTIABLE) mandates
automated tests for every user story's acceptance criteria, so test tasks are included and are
**not optional** for this feature.

**Organization**: Tasks are grouped by user story (from spec.md, priority order P1 → P2 → P3) to
enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Paths are relative to `sdd-demo/` and match the `app/` module layout defined in plan.md's
  Project Structure section.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create the Android app module skeleton per plan.md's Project Structure: `app/`
      module with `src/main/kotlin/com/example/videocompiler/{ui,domain,data,media,service}`,
      `src/test/kotlin/com/example/videocompiler/`, and
      `src/androidTest/kotlin/com/example/videocompiler/` directories, plus `app/build.gradle.kts`
      and root `settings.gradle.kts`.
- [X] T002 Configure `app/build.gradle.kts` dependencies: `androidx.media3:media3-transformer`,
      `media3-effect`, `media3-common`, `media3-exoplayer`; Jetpack Compose + Activity/Lifecycle/
      ViewModel; AndroidX Core (foreground service APIs) — per plan.md's Primary Dependencies.
- [X] T003 Set `minSdk = 26`, and `compileSdk`/`targetSdk` to the latest stable Android SDK, plus
      JVM 17 target bytecode, in `app/build.gradle.kts` (plan.md Technical Context).
- [X] T004 [P] Configure Kotlin linting/formatting (e.g., ktlint or detekt) in
      `build.gradle.kts` / `config/detekt/detekt.yml`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core domain models, data access, and validation logic that every user story depends on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 [P] Create `SourceMediaItem` domain model in
      `app/src/main/kotlin/com/example/videocompiler/domain/model/SourceMediaItem.kt` per
      data-model.md: fields `uri` (content URI), `mediaType` (enum `VIDEO`, `PHOTO`),
      `displayName`, `durationMs` (video-only), `width`/`height`, `frameRate` (video-only),
      `hasAudioTrack` (video-only, always `false` for photos), `validationState` (enum `VALID`,
      `INVALID_UNREADABLE`); include the constant `PHOTO_DISPLAY_DURATION_MS = 3000`.
- [X] T006 [P] Create `OutputSettings` domain model in
      `app/src/main/kotlin/com/example/videocompiler/domain/model/OutputSettings.kt` per
      data-model.md: `resolutionTier` (enum `R720P`, `R1080P`, `R2K`, `R4K`), `aspectRatio` (enum
      `RATIO_16_9`, `RATIO_9_16`, `RATIO_1_1`), `frameRate` (enum `FPS_24`, `FPS_30`, `FPS_60`);
      all three fields required, no free-form/custom numeric entry.
- [X] T007 [P] Create `CompileJob` domain model in
      `app/src/main/kotlin/com/example/videocompiler/domain/model/CompileJob.kt` per
      data-model.md: `sequence` (SelectionSequence snapshot), `outputSettings` (OutputSettings
      snapshot), `status` (enum `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED` — never transitions
      away from a terminal state), `progressPercent` (int 0-100), `failureReason` (nullable
      string), `outputUri` (nullable content URI).
- [X] T008 [P] Create `CompiledOutputVideo` domain model in
      `app/src/main/kotlin/com/example/videocompiler/domain/model/CompiledOutputVideo.kt` per
      data-model.md: `uri`, `resolution`/`aspectRatio`/`frameRate` (must exactly match the
      `OutputSettings` used to produce it), `itemOrder` (ordered list of `SourceMediaItem`
      references matching the originating `SelectionSequence.items` order exactly).
- [X] T009 Create `SelectionSequence` domain model in
      `app/src/main/kotlin/com/example/videocompiler/domain/model/SelectionSequence.kt` per
      data-model.md: ordered `items: List<SourceMediaItem>`; operations `append(sourceMediaItem)`
      (adds a validated item to the end), `move(fromIndex, toIndex)`, `remove(index)` — all MUST
      leave no gaps/duplicates; MAY contain as few as 1 entry; MUST NOT accept an item whose
      `validationState != VALID`; no product-imposed maximum length (depends on T005).
- [X] T010 Implement `MediaStoreRepository` in
      `app/src/main/kotlin/com/example/videocompiler/data/mediastore/MediaStoreRepository.kt`:
      query available videos/photos via `MediaStore` `ContentResolver`, and save the compiled
      output under the `Movies/VideoCompiler/` collection (research.md §8) (depends on T005).
- [X] T011 Implement `ValidateSourceMediaItem` use case in
      `app/src/main/kotlin/com/example/videocompiler/domain/usecase/ValidateSourceMediaItem.kt`
      per `contracts/media-validation.md`: input a selected `content://` URI (video or photo);
      output `Valid(SourceMediaItem)` or `Invalid(reason: UNREADABLE | UNSUPPORTED_FORMAT |
      INACCESSIBLE)`; probe videos via Media3 `MediaExtractor`/`MetadataRetriever`, probe photos
      via `BitmapFactory.decodeStream` (bounds-only) or `ImageDecoder`; MUST be re-callable
      without caching a stale result; MUST have no side effects on device storage (depends on
      T005).
- [X] T012 Implement `RevalidateSourceMediaItem` use case in
      `app/src/main/kotlin/com/example/videocompiler/domain/usecase/RevalidateSourceMediaItem.kt`
      per `contracts/media-validation.md`: same `Valid`/`Invalid` output shape as
      `ValidateSourceMediaItem`, invoked by the compile pipeline immediately before/at the moment
      each item is consumed (depends on T011).
- [X] T013 [P] Implement runtime media permission handling (research.md §9): request
      `READ_MEDIA_VIDEO`/`READ_MEDIA_IMAGES` (Android 13+) or `READ_EXTERNAL_STORAGE` (older),
      or evaluate using the system Photo Picker (`PickVisualMedia`) to avoid broad permissions,
      in a shared helper under `app/src/main/kotlin/com/example/videocompiler/ui/selection/`;
      show a clear in-app message with retry/settings shortcut if denied.
- [X] T014 [P] Configure a shared error-handling/result type and logging wrapper (used by
      `domain`, `data`, `media`, and `service` layers) in
      `app/src/main/kotlin/com/example/videocompiler/domain/model/AppResult.kt` (or equivalent).

**Checkpoint**: Foundation ready — domain models, validation, and MediaStore access are in
place; user story implementation can now begin.

---

## Phase 3: User Story 1 - Combine selected media (photos and videos) into one output (Priority: P1) 🎯 MVP

**Goal**: A user selects 2+ media items (videos, photos, or a mix), compiles them into a single
output video that plays each item back-to-back in selection order, survives backgrounding, and
never leaves a partial/corrupted file behind.

**Independent Test**: Select 2+ existing media items (mixing photos and videos), confirm
compilation with default output settings, and verify a single new video file is produced that
plays the selected items back-to-back in order, with each photo shown for its fixed display
duration.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T015 [P] [US1] Unit test: `CompileEngine.compile` produces output items strictly in
      `sequence.items` order — videos played in full, photos displayed for
      `PHOTO_DISPLAY_DURATION_MS` — back-to-back with no gap, per `contracts/compile-engine.md`
      guarantee 1 (FR-006, SC-002), in
      `app/src/test/kotlin/com/example/videocompiler/media/compiler/CompileEngineOrderTest.kt`.
- [X] T016 [P] [US1] Unit test: `CompileEngine` inserts generated silence for any clip segment
      or photo segment with no audio track so the composed output has one continuous audio
      track (FR-009), in
      `app/src/test/kotlin/com/example/videocompiler/media/compiler/CompileEngineSilenceTest.kt`.
- [X] T017 [P] [US1] Unit test: `CompileEngine.compile` with a single-item `SelectionSequence`
      (FR-012) produces an output equal to that single item, re-encoded/rendered to the chosen
      `OutputSettings`, in
      `app/src/test/kotlin/com/example/videocompiler/media/compiler/CompileEngineSingleItemTest.kt`.
- [X] T018 [P] [US1] Instrumented test: quickstart.md Scenario 1 — compile 2 sample videos in
      selection order and verify a new file appears in `Movies/VideoCompiler/…` playing both
      clips back-to-back with no gap/overlap, in
      `app/src/androidTest/kotlin/com/example/videocompiler/CompileSequenceInstrumentedTest.kt`.
- [X] T019 [P] [US1] Instrumented test: quickstart.md Scenario 1b — mixed photo+video sequence;
      verify the photo shows as a static frame for exactly 3 seconds (no pan/zoom) with silent
      audio during its segment, then the video plays in full (FR-009, FR-017, SC-008), in
      `app/src/androidTest/kotlin/com/example/videocompiler/CompilePhotoVideoMixInstrumentedTest.kt`.
- [X] T020 [P] [US1] Instrumented test: quickstart.md Scenario 6 — compilation continues to
      completion when the app is backgrounded/screen locked (progress notification visible,
      output correct), and stops cleanly with no partial/corrupted output when the app is
      swiped away from Recents (FR-010, FR-013, SC-006), in
      `app/src/androidTest/kotlin/com/example/videocompiler/CompileBackgroundServiceInstrumentedTest.kt`.
- [X] T021 [P] [US1] Instrumented test: quickstart.md Scenario 7 — attempting to select a
      corrupted/truncated video file and a corrupted image file is rejected immediately with a
      clear error message and neither is added to the Selection Sequence (FR-015, SC-007), in
      `app/src/androidTest/kotlin/com/example/videocompiler/SelectionValidationInstrumentedTest.kt`.
- [X] T022 [P] [US1] Instrumented test: quickstart.md Scenario 8 — a selected item deleted from
      the device before compile starts causes the app to notify the user and not silently
      compile using it (FR-016), in
      `app/src/androidTest/kotlin/com/example/videocompiler/RevalidationInstrumentedTest.kt`.

### Implementation for User Story 1

- [X] T023 [US1] Implement `CompileEngine` in
      `app/src/main/kotlin/com/example/videocompiler/media/compiler/CompileEngine.kt` per
      `contracts/compile-engine.md`, using AndroidX Media3 `Transformer`/`Composition`/
      `EditedMediaItemSequence` APIs to concatenate `sequence.items` in order; render each photo
      as a static-frame `EditedMediaItem` with `durationUs` = `PHOTO_DISPLAY_DURATION_MS` and no
      animation (research.md §6b) (depends on T009, T012).
- [X] T024 [US1] Implement silent-audio generation in `media/compiler` for any clip or photo
      segment lacking an audio track, so the composed output has one continuous audio track
      (FR-009, research.md §4) (depends on T023).
- [X] T025 [US1] Implement `CompileEngine.compile` success path: write the result to `MediaStore`
      under `Movies/VideoCompiler/…`, set `CompileJob.status = SUCCEEDED` and `outputUri`
      accordingly (FR-011) (depends on T023, T010).
- [X] T026 [US1] Implement `CompileEngine.compile` failure handling: on I/O error, storage
      exhaustion, or a `RevalidateSourceMediaItem` `Invalid` result mid-job, stop processing
      further items, delete any partial output file, and set `CompileJob.status = FAILED` with a
      `failureReason` identifying the item (FR-010, FR-016) (depends on T025, T012).
- [X] T027 [US1] Implement `CompileEngine.cancel(job)`: stop processing as soon as feasible,
      delete any partial output file, set `CompileJob.status = CANCELLED` (depends on T023).
- [X] T028 [US1] Implement `CompileForegroundService` in
      `app/src/main/kotlin/com/example/videocompiler/service/CompileForegroundService.kt` per
      `contracts/compile-service.md`: promote to foreground with a progress notification before
      encode work begins; continue running through backgrounding/screen-off; call
      `CompileEngine.cancel` (treating the job as `CANCELLED`) if the hosting process is killed
      (depends on T023, T027).
- [X] T029 [US1] Implement the `ui/selection` media picker screen in
      `app/src/main/kotlin/com/example/videocompiler/ui/selection/` for videos and photos,
      calling `ValidateSourceMediaItem` before `SelectionSequence.append`, and showing a clear
      rejection message naming the file for `Invalid` results (FR-001, FR-015) (depends on T009,
      T011, T013).
- [X] T030 [US1] Implement the `ui/compile` trigger + progress screen in
      `app/src/main/kotlin/com/example/videocompiler/ui/compile/`, bound to
      `CompileJob.progressPercent` and `CompileForegroundService` notification state (FR-013)
      (depends on T028).
- [X] T031 [US1] Add a low-resource warning surfaced during selection and compilation when
      device storage/memory runs low, without enforcing any hard item-count/duration cap
      (FR-014) in `ui/selection` and `ui/compile`.
- [X] T032 [US1] Wire a sensible default `OutputSettings` (e.g., 1080p / 16:9 / 30fps) into the
      compile flow so User Story 1 is fully compilable before User Story 3's preset picker UI
      exists (depends on T006).

**Checkpoint**: At this point, User Story 1 should be fully functional and testable
independently (MVP).

---

## Phase 4: User Story 2 - Reorder selected media before compiling (Priority: P2)

**Goal**: After selecting several media items, the user rearranges the playback sequence into an
order different from the selection order, and removes items without leaving gaps/duplicates.

**Independent Test**: Select 3+ videos, reorder them into a sequence different from the selection
order, compile, and verify the output plays clips in the newly defined order.

### Tests for User Story 2 ⚠️

- [X] T033 [P] [US2] Unit test: `SelectionSequence.move(fromIndex, toIndex)` reorders an entry
      correctly with no gaps/duplicates (Acceptance Scenario 1), in
      `app/src/test/kotlin/com/example/videocompiler/domain/model/SelectionSequenceMoveTest.kt`.
- [X] T034 [P] [US2] Unit test: `SelectionSequence.remove(index)` shifts remaining entries to
      keep their relative order with no gap or duplicate (Acceptance Scenario 3), in
      `app/src/test/kotlin/com/example/videocompiler/domain/model/SelectionSequenceRemoveTest.kt`.
- [X] T035 [P] [US2] Instrumented test: quickstart.md Scenario 3 — select 3 videos (A, B, C),
      move C to first position, remove B, compile, and verify the output plays C then A only, in
      `app/src/androidTest/kotlin/com/example/videocompiler/ReorderSequenceInstrumentedTest.kt`.
- [X] T036 [P] [US2] Instrumented test: SC-005 — reorder a sequence of at least 10 selected media
      items without the app losing or misordering any entry, in
      `app/src/androidTest/kotlin/com/example/videocompiler/ReorderLargeSequenceInstrumentedTest.kt`.

### Implementation for User Story 2

- [X] T037 [US2] Implement the `ui/sequence` reorder screen in
      `app/src/main/kotlin/com/example/videocompiler/ui/sequence/`: display the current
      `SelectionSequence` as an ordered list independent of selection order (FR-002), and bind
      drag-to-reorder to `SelectionSequence.move` with the displayed sequence updating
      immediately (FR-003, Acceptance Scenario 1) (depends on T009).
- [X] T038 [US2] Implement the remove-item action in the `ui/sequence` screen bound to
      `SelectionSequence.remove`, keeping remaining items' relative order with no gap/duplicate
      (FR-004, Acceptance Scenario 3) (depends on T009, T037).
- [X] T039 [US2] Ensure the compile flow (`ui/compile`, T030) always consumes the current,
      possibly-reordered `SelectionSequence` — never the original selection order (FR-006)
      (depends on T030, T037).

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently.

---

## Phase 5: User Story 3 - Configure output resolution, aspect ratio, and frame rate (Priority: P3)

**Goal**: Before compiling, the user chooses resolution/aspect ratio/frame rate from presets, and
every selected media item (photo or video) is fit into that format without cropping/stretching.

**Independent Test**: Select media items with different native resolutions/aspect ratios/frame
rates than a chosen preset, pick a specific output preset, compile, and verify the output file
matches the preset exactly with every item fully visible.

### Tests for User Story 3 ⚠️

- [X] T040 [P] [US3] Unit test: `OutputSettings` only accepts the defined preset enum values for
      `resolutionTier`, `aspectRatio`, and `frameRate` — no free-form numeric entry (Acceptance
      Scenario 1), in
      `app/src/test/kotlin/com/example/videocompiler/domain/model/OutputSettingsValidationTest.kt`.
- [X] T041 [P] [US3] Unit test: combining `resolutionTier` + `aspectRatio` derives the correct
      `outputWidth × outputHeight` per the research.md §7 preset table (e.g., 1080p + 9:16 →
      1080×1920), in
      `app/src/test/kotlin/com/example/videocompiler/domain/model/OutputSettingsDimensionsTest.kt`.
- [X] T042 [P] [US3] Instrumented test: quickstart.md Scenario 4 — choose a 1080p/16:9/30fps
      preset, compile a sequence including a portrait clip, and verify the output file is exactly
      1920×1080 at 30fps (SC-003) with the portrait clip's full image visible and padded, no
      cropping or stretching (SC-004), in
      `app/src/androidTest/kotlin/com/example/videocompiler/OutputSettingsInstrumentedTest.kt`.

### Implementation for User Story 3

- [X] T043 [US3] Implement the `ui/outputsettings` preset picker screen in
      `app/src/main/kotlin/com/example/videocompiler/ui/outputsettings/` for `resolutionTier`,
      `aspectRatio`, and `frameRate` — defined preset values only, no free-form numeric entry
      (FR-005) (depends on T006).
- [X] T044 [US3] Configure the `Transformer`'s output `Format`/`TransformationRequest` width,
      height, and frame rate from the chosen `OutputSettings` (`resolutionTier` × `aspectRatio` →
      pixel dimensions per research.md §7; `frameRate` enum → target fps) so the compiled output
      exactly matches the user-selected preset regardless of source item dimensions (FR-007,
      SC-003), and implement `Presentation`-effect-based fit/letterbox logic in `media/compiler`
      so any source item (photo or video) whose native aspect ratio differs from
      `settings.aspectRatio` is scaled to fit inside that output frame with padding — never
      cropped, never stretched (FR-008, SC-004) (depends on T023).
- [X] T045 [US3] Implement frame-rate conversion in `media/compiler` via the `Transformer`
      output format configuration, resampling/duplicating/dropping frames to match
      `settings.frameRate` without altering clip playback speed/duration (FR-008 Acceptance
      Scenario 4) (depends on T023).
- [X] T046 [US3] Wire the user's `ui/outputsettings` preset selection into the `ui/compile` flow
      so `CompileEngine.compile` is called with the user's chosen `OutputSettings` instead of the
      T032 default (depends on T043, T030).

**Checkpoint**: All user stories should now be independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T047 [P] Update project documentation (e.g., `README.md`) describing the final feature set
      (video + photo sequence compilation, reorder, output presets).
- [X] T048 Code cleanup and refactoring pass across `domain/`, `data/`, `media/`, and `service/`
      layers for consistency and to remove any duplication introduced across US1-US3.
- [X] T049 [P] Add unit tests for remaining edge cases: attempting to compile an empty
      `SelectionSequence` is blocked, and a photo-only sequence compiles correctly, in
      `app/src/test/kotlin/com/example/videocompiler/media/compiler/CompileEngineEdgeCasesTest.kt`.
- [X] T050 Security/permission hardening review: confirm scoped-storage compliance and that no
      broader-than-necessary permissions are requested (research.md §9).
- [X] T051 Run the full `quickstart.md` validation pass (all 8 scenarios, including 1b) end-to-end
      on a physical device or emulator and record the results.

---

## Phase 7: Convergence

- [X] T052 [P] Construct and expose a `CompiledOutputVideo` (per data-model.md's `CompileJob
      1──0..1 CompiledOutputVideo` relationship) when a `CompileJob` transitions to `SUCCEEDED`,
      wiring `resolution`/`aspectRatio`/`frameRate` from the job's `outputSettings` snapshot and
      `itemOrder` from its `sequence` snapshot, in
      `app/src/main/kotlin/com/example/videocompiler/domain/model/CompileJob.kt` and
      `app/src/main/kotlin/com/example/videocompiler/media/compiler/CompileEngine.kt` (partial;
      `CompiledOutputVideo.kt` from T008 is currently defined but never constructed or
      referenced anywhere in `app/src/main` or tests) per data-model.md, FR-007, SC-002, SC-003.
- [X] T053 [P] Add instrumented tests for at least 2 more `OutputSettings` preset combinations
      beyond the single 1080p/16:9/30fps case already covered by `OutputSettingsInstrumentedTest`
      (T042) — e.g., a `9:16` case and a non-30fps case — verifying the real compiled output file
      matches each chosen preset exactly, in
      `app/src/androidTest/kotlin/com/example/videocompiler/OutputSettingsInstrumentedTest.kt`
      (partial; SC-003's "100%" claim is currently substantiated end-to-end for only 1 of 36
      possible `resolutionTier × aspectRatio × frameRate` combinations) per SC-003. While adding
      the new 9:16/720p/24fps and 1:1/2K/60fps combos, discovered and fixed a real bug: Media3
      1.4.1's `EditedMediaItem.setFrameRate()` was a no-op for video (frame-rate presets only
      ever took effect for photos); upgraded to Media3 1.10.1 (which added true max-frame-rate
      capping for video), which required bumping `compileSdk`/`targetSdk` to 36, AGP to 8.9.1,
      and the Gradle wrapper to 8.13, plus migrating off the now-removed deprecated
      `EditedMediaItemSequence`/`Composition.Builder` constructors in `CompileEngine.kt`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories.
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion.
  - User Story 1 (P1) can start immediately after Foundational.
  - User Story 2 (P2) depends on `SelectionSequence` (T009, Foundational) and on the
    `ui/compile` flow (T030, US1) to demonstrate reordered output — build after or alongside US1.
  - User Story 3 (P3) depends on `OutputSettings` (T006, Foundational) and on `CompileEngine`
    (T023, US1) — build after or alongside US1.
- **Polish (Phase 6)**: Depends on all desired user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: No dependency on other stories — the true MVP slice.
- **User Story 2 (P2)**: Builds on US1's `ui/compile` flow (T030) to prove reordering changes
  compile output, but its own reorder/remove logic (T037-T039) is independently testable via
  unit tests (T033-T034) without US1's UI.
- **User Story 3 (P3)**: Builds on US1's `CompileEngine` (T023) to prove presets take effect, but
  its own preset validation/derivation logic (T040-T041) is independently testable via unit tests
  without US1's UI.

### Within Each User Story

- Tests (T015-T022, T033-T036, T040-T042) MUST be written and FAIL before implementation.
- Domain/model tasks before service/use-case tasks.
- Use-case/service tasks before UI tasks.
- Core implementation before integration with other stories' UI.

### Parallel Opportunities

- All Setup tasks marked [P] (T004) can run in parallel with T001-T003 once the module exists.
- Foundational tasks T005-T008 (independent model files) can run in parallel; T013-T014 can run
  in parallel with each other and with T005-T008.
- All US1 test tasks (T015-T022) can run in parallel with each other.
- All US2 test tasks (T033-T036) can run in parallel with each other.
- All US3 test tasks (T040-T042) can run in parallel with each other.
- Once Foundational completes, US2's test-writing (T033-T036) and US3's test-writing
  (T040-T042) can start in parallel with US1's implementation, since they target independent
  files.

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Unit test CompileEngine order guarantee in CompileEngineOrderTest.kt"
Task: "Unit test CompileEngine silence insertion in CompileEngineSilenceTest.kt"
Task: "Unit test CompileEngine single-item compile in CompileEngineSingleItemTest.kt"
Task: "Instrumented test Scenario 1 in CompileSequenceInstrumentedTest.kt"
Task: "Instrumented test Scenario 1b in CompilePhotoVideoMixInstrumentedTest.kt"
Task: "Instrumented test Scenario 6 in CompileBackgroundServiceInstrumentedTest.kt"
Task: "Instrumented test Scenario 7 in SelectionValidationInstrumentedTest.kt"
Task: "Instrumented test Scenario 8 in RevalidationInstrumentedTest.kt"

# Launch independent Foundational models together:
Task: "Create SourceMediaItem model in SourceMediaItem.kt"
Task: "Create OutputSettings model in OutputSettings.kt"
Task: "Create CompileJob model in CompileJob.kt"
Task: "Create CompiledOutputVideo model in CompiledOutputVideo.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories).
3. Complete Phase 3: User Story 1 (tests first, then implementation).
4. **STOP and VALIDATE**: Run quickstart.md Scenarios 1, 1b, 2, 6, 7, 8 independently.
5. Deploy/demo if ready — this alone delivers a working "select media → compile in order" app.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready.
2. Add User Story 1 → Validate via quickstart Scenarios 1/1b/2/6/7/8 → Deploy/Demo (MVP!).
3. Add User Story 2 → Validate via quickstart Scenario 3 (+ SC-005 with 10+ items) →
   Deploy/Demo.
4. Add User Story 3 → Validate via quickstart Scenario 4 → Deploy/Demo.
5. Each story adds value without breaking previous stories.

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together.
2. Once Foundational is done:
   - Developer A: User Story 1 (`CompileEngine`, `CompileForegroundService`, selection/compile
     UI).
   - Developer B: User Story 2 (`ui/sequence` reorder/remove UI + tests) — can write/run unit
     tests immediately against T009; integrates with US1's `ui/compile` once available.
   - Developer C: User Story 3 (`ui/outputsettings` + fit/frame-rate logic + tests) — can
     write/run unit tests immediately against T006; integrates with US1's `CompileEngine` once
     available.
3. Stories complete and integrate independently.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- Automated tests are mandatory per constitution Principle IV — do not skip the "Tests for User
  Story N" sections.
- Verify tests fail before implementing (red-green-refactor).
- Commit after each task or logical group, per constitution Principle VI (Small, Reviewable
  Commits).
- Stop at any checkpoint to validate a story independently via the matching quickstart.md
  scenario(s).
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break independence.
