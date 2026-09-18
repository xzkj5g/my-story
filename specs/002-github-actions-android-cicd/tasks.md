---

description: "Task list for GitHub Actions Android CI/CD"
---

# Tasks: GitHub Actions Android CI/CD

**Input**: Design documents from `/specs/002-github-actions-android-cicd/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Contract tests and local Gradle checks are required by the specification and constitution.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelizable only when tasks affect different files and have no incomplete dependency.
- **[Story]**: Maps a task to its specification user story.
- Every task includes an exact file path.

## Phase 1: Setup

**Purpose**: Establish automation paths and maintainable action-version conventions.

- [X] T001 Document workflow names, artifact names, Java 17, Android API 36, Gradle wrapper usage, and stable major-version action tags in `.github/workflows/README.md`.
- [X] T002 Document the fixed `main` release branch, `YYYY.MM.DD` tag format, duplicate-tag policy, serialized release policy, 14-day artifact retention, and protected secret names in `.github/workflows/README.md`.
- [X] T003 Document the action update policy and the tradeoff between stable major tags and immutable SHA pins in `specs/002-github-actions-android-cicd/research.md`.

---

## Phase 2: Foundational

**Purpose**: Provide shared workflow and signing-material validation before user-story work.

**CRITICAL**: Complete this phase before workflow implementation.

- [X] T004 [P] Create `scripts/ci/validate_workflows.py` to parse every `.github/workflows/*.yml` file and fail on invalid YAML or missing required workflow keys.
- [X] T005 Extend `scripts/ci/validate_workflows.py` with least-privilege permission and secret-safety checks.
- [X] T006 Add `scripts/ci/validate_release_secrets.py` to validate required secret references without printing values and reject tracked keystore files.

**Checkpoint**: Static workflow and signing-material checks are ready.

---

## Phase 3: User Story 1 - Validate changes with an Android build (Priority: P1) 🎯 MVP

**Goal**: Validate pull requests and pushes to `main` with the supported Android toolchain.

**Independent Test**: Run workflow validation, inspect `android-ci.yml`, and verify CI runs detekt, unit tests, debug assembly, and artifact uploads.

### Tests for User Story 1

- [X] T007 [P] [US1] Add trigger, toolchain, and permission contract tests in `scripts/ci/test_validate_workflows.py`.
- [X] T008 [P] [US1] Add command, artifact, retention, and stable major-version action-tag tests in `scripts/ci/test_ci_commands.py`.

### Implementation for User Story 1

- [X] T009 [US1] Create `.github/workflows/android-ci.yml` with pull-request and `main` push triggers, Ubuntu setup, Java 17, Android API 36, Gradle caching, and stable major-version action tags from `plan.md`.
- [X] T010 [US1] Add `./gradlew --no-daemon detekt`, `testDebugUnitTest`, and `assembleDebug` steps to `.github/workflows/android-ci.yml`.
- [X] T011 [US1] Add debug APK and diagnostic report uploads with 14-day retention and failure-time diagnostics to `.github/workflows/android-ci.yml`.
- [X] T012 [US1] Document validation triggers, stages, artifacts, local reproduction, and action-tag update policy in `.github/workflows/README.md`.

**Checkpoint**: Pull requests and `main` pushes provide reproducible Android validation results.

---

## Phase 4: User Story 2 - Trigger an on-demand signed release from the default branch (Priority: P1)

**Goal**: Publish a release-signed APK to GitHub Releases from `main`.

**Independent Test**: Validate the release workflow, run it with a unique date tag from `main`, and verify branch, duplicate, signing, failure, and publication behavior.

### Tests for User Story 2

- [X] T013 [US2] Add release trigger, input, branch, and permission contract tests in `scripts/ci/test_release_workflow.py`.
- [X] T014 [US2] Add safety-order, concurrency, duplicate-release, and calendar-date validation tests in `scripts/ci/test_release_workflow.py`.
- [X] T015 [US2] Add signing contract tests for protected secrets, temporary storage, `apksigner verify`, certificate metadata, checksum generation, and cleanup in `scripts/ci/test_release_signing.py`.

### Implementation for User Story 2

- [X] T016 [US2] Create `.github/workflows/android-release.yml` with manual dispatch, `main` guard, Java 17, Android API 36, Gradle caching, and stable major-version action tags from `plan.md`.
- [X] T017 [US2] Add serialized concurrency and duplicate tag/release preflight before signing or publication in `.github/workflows/android-release.yml`.
- [X] T018 [US2] Add detekt, debug unit tests, and `assembleRelease` before signing or publication in `.github/workflows/android-release.yml`.
- [X] T019 [US2] Load the four protected signing secrets into temporary runner storage and remove signing material unconditionally in `.github/workflows/android-release.yml`.
- [X] T020 [US2] Sign and verify the release APK with `apksigner verify`, failing before publication on verification errors in `.github/workflows/android-release.yml`.
- [X] T021 [US2] Generate certificate metadata, SHA-256 checksum, source revision, and release notes/assets in `.github/workflows/android-release.yml`.
- [X] T022 [US2] Publish the signed APK and public verification metadata with `softprops/action-gh-release@v3` in `.github/workflows/android-release.yml`.
- [X] T023 [US2] Document keystore generation, secure backup, protected secret setup, release triggering, signature verification, and GitHub Release outputs in `.github/workflows/README.md`.

**Checkpoint**: A valid `main` run publishes one traceable signed APK; unsafe or failed runs publish nothing.

---

## Phase 5: User Story 3 - Make build and release results diagnosable (Priority: P2)

**Goal**: Make setup, validation, signing, failure, and publication evidence easy to locate.

**Independent Test**: Inspect workflow summaries, logs, artifacts, signed APK metadata, certificate fingerprint, checksum, and release notes.

### Tests for User Story 3

- [X] T024 [P] [US3] Add workflow-stage contract tests in `scripts/ci/test_workflow_diagnostics.py`.
- [X] T025 [US3] Add artifact retention, source revision, failure-report, certificate, checksum, summary, and readiness-evidence tests in `scripts/ci/test_workflow_diagnostics.py`.

### Implementation for User Story 3

- [X] T026 [US3] Add named workflow summary steps and failure-time diagnostic collection to `.github/workflows/android-ci.yml`.
- [X] T027 [US3] Add signing, verification, source revision, checksum, cleanup, and publication evidence without secret exposure to `.github/workflows/android-release.yml`.
- [X] T028 [US3] Update `README.md` with validation, signed-release, branch, secret, artifact, fingerprint, checksum, troubleshooting, and app-store scope documentation.

**Checkpoint**: Developers can identify failed stages and verify a downloaded release APK.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Run local checks, quickstart validation, and final review.

- [X] T029 [P] Add `scripts/ci/run_tests.sh` to execute workflow validation, Python contract tests, and documented Gradle preflight commands.
- [X] T030 [P] Document static checks, safe test-release validation, keystore setup, and secret hygiene in `scripts/ci/README.md`.
- [X] T031 Run all locally verifiable scenarios in `specs/002-github-actions-android-cicd/quickstart.md` and record release-readiness evidence without committing credentials or key material.
- [X] T032 Review `.github/workflows/`, `scripts/ci/`, and `README.md` for permissions, secret leakage, cleanup, duplicate-release behavior, and stable action-tag consistency.
- [ ] T033 Configure the protected GitHub `release` Environment, run one disposable unique-date signed release from `main`, and record branch, signature, fingerprint, checksum, publication, and cleanup evidence.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup and blocks all stories.
- **User Story 1 (Phase 3)**: Depends on Phase 2 and delivers the MVP validation pipeline.
- **User Story 2 (Phase 4)**: Depends on Phase 2 and reuses US1 toolchain conventions.
- **User Story 3 (Phase 5)**: Depends on US1 and US2 workflow stages.
- **Polish (Phase 6)**: Depends on all desired stories; T033 also requires GitHub Environment configuration.

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2; independently testable.
- **US2 (P1)**: Starts after Phase 2; independently testable with protected secrets.
- **US3 (P2)**: Starts after the US1 and US2 workflow files exist.

### Within Each User Story

- Tests are written before implementation and should fail for missing behavior.
- Workflow structure precedes commands, signing, artifacts, and publication.
- Static contracts pass before GitHub Actions runs are triggered.
- Live release validation occurs only after protected secrets are configured.

### Parallel Opportunities

- T004 and T006 can run in parallel after Setup.
- T007 and T008 can run in parallel before US1 implementation.
- T013, T014, and T015 can run in parallel because they target separate test concerns in the same release-contract area only after coordination.
- T024 can run in parallel with US3 implementation preparation; T025 follows T024.
- T029 and T030 can run in parallel during Polish.

## Parallel Example: User Story 1

```text
Task: T007 Add trigger/toolchain contract tests in scripts/ci/test_validate_workflows.py
Task: T008 Add command/artifact/action-tag tests in scripts/ci/test_ci_commands.py
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Setup and Foundational phases.
2. Implement and test `.github/workflows/android-ci.yml`.
3. Run static checks and Gradle preflight.
4. Trigger CI on GitHub and verify artifacts.

### Incremental Delivery

1. Add US1 validation.
2. Add US2 signed GitHub Release publication.
3. Add US3 diagnostics and verification metadata.
4. Run the quickstart and live release validation.

### Notes

- Never commit keystores, Base64 keystore content, passwords, tokens, or generated release secrets.
- Instrumented tests remain outside the baseline workflow unless emulator provisioning is specified.
- Stable major-version action tags are intentional; update them during planned maintenance.
