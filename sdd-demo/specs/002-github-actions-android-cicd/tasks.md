---

description: "Task list for GitHub Actions Android CI/CD"
---

# Tasks: GitHub Actions Android CI/CD

**Input**: Design documents from `/specs/002-github-actions-android-cicd/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Contract and integration validation tasks are included because the specification requires
automated validation, signing verification, artifact integrity, and release safety.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelizable only when tasks use different files and have no incomplete dependency.
- **[Story]**: Maps a task to the specification's user story.
- Every task includes an exact file path.

## Phase 1: Setup

**Purpose**: Establish automation paths and safe signing conventions.

- [X] T001 Create `.github/workflows/` and `scripts/ci/` directories and document workflow/artifact naming in `.github/workflows/README.md`.
- [X] T002 Document Java 17, Android API 36, Gradle wrapper, exact SHA-pinned action dependencies, and repository permission assumptions in `.github/workflows/README.md`.
- [X] T003 Document the fixed `main` release branch, `YYYY.MM.DD` release-tag pattern, duplicate-tag policy, serialized `cancel-in-progress: false` policy, 14-day CI artifact retention, and protected signing-secret names in `.github/workflows/README.md`.

---

## Phase 2: Foundational

**Purpose**: Provide shared workflow validation and signing-material safety checks.

**⚠️ CRITICAL**: Complete this phase before user-story workflows.

- [X] T004 [P] Create `scripts/ci/validate_workflows.py` to parse every `.github/workflows/*.yml` file and fail on invalid YAML or missing required workflow keys.
- [X] T005 Extend `scripts/ci/validate_workflows.py` with permissions and secret-safety checks: validation must use `contents: read`, only release publication may use `contents: write`, and workflows must not print tokens, keystore content, or passwords.
- [X] T006 Add `scripts/ci/validate_release_secrets.py` to validate required signing secret names/configuration without printing values, and to reject repository-tracked keystore files.

**Checkpoint**: Static workflow and signing-material safety checks are ready.

---

## Phase 3: User Story 1 - Validate changes with an Android build (Priority: P1) 🎯 MVP

**Goal**: Validate pull requests and pushes to `main` with the supported Android toolchain.

**Independent Test**: Run `python3 scripts/ci/validate_workflows.py`, inspect `android-ci.yml`, and trigger it with a valid and intentionally failing change.

### Tests for User Story 1

- [X] T007 [P] [US1] Add trigger/toolchain contract tests in `scripts/ci/test_validate_workflows.py` for pull requests, pushes to `main`, Java 17, Android API 36, Gradle wrapper usage, `contents: read`, and the exact SHA-pinned checkout/setup/cache/SDK action references from `plan.md`.
- [X] T008 [P] [US1] Add command/artifact contract tests in `scripts/ci/test_ci_commands.py` for `detekt`, `testDebugUnitTest`, `assembleDebug`, debug APK upload, diagnostic-report upload, and `retention-days: 14`.

### Implementation for User Story 1

- [X] T009 [US1] Create `.github/workflows/android-ci.yml` with pull-request and `main` push triggers, Ubuntu setup, the exact SHA-pinned actions from `plan.md`, Java 17, Android API 36 preparation, and Gradle caching.
- [X] T010 [US1] Add required `./gradlew detekt`, `./gradlew testDebugUnitTest`, and `./gradlew assembleDebug` steps to `.github/workflows/android-ci.yml`, with command failures failing the job.
- [X] T011 [US1] Add debug APK and Gradle/test/quality report uploads to `.github/workflows/android-ci.yml` with documented retention and failure-time diagnostics.
- [X] T012 [US1] Document validation triggers, stages, artifacts, local reproduction, and action-version policy in `.github/workflows/README.md`.

**Checkpoint**: Pull requests and `main` pushes provide reproducible Android validation results.

---

## Phase 4: User Story 2 - Trigger an on-demand signed release from the default branch (Priority: P1)

**Goal**: Let an authorized developer publish a release-signed APK to GitHub Releases from `main`.

**Independent Test**: Validate the release workflow statically, run a test release with a unique tag from `main`, and verify wrong-branch, duplicate-tag, failed-build, missing-secret, and signature-failure paths publish nothing.

### Tests for User Story 2

- [X] T013 [US2] Add release trigger/input/permission contract tests in `scripts/ci/test_release_workflow.py` for `workflow_dispatch`, required `YYYY.MM.DD` release-tag validation, fixed `main` publication guard, and minimum write permission.
- [X] T014 [US2] Extend `scripts/ci/test_release_workflow.py` with safety-order tests asserting validation precedes signing/publication, `cancel-in-progress: false` serialization is configured, duplicate date tags/releases fail visibly, and publication cannot silently overwrite an existing release.
- [X] T015 [US2] Add signing contract tests in `scripts/ci/test_release_signing.py` asserting protected secret references, temporary keystore handling, `apksigner verify`, certificate fingerprint output, SHA-256 checksum generation, and cleanup steps.

### Implementation for User Story 2

- [X] T016 [US2] Create `.github/workflows/android-release.yml` with `workflow_dispatch`, required unique `YYYY.MM.DD` release-tag input, Ubuntu setup, the exact SHA-pinned actions from `plan.md`, Java 17, Android API 36, and Gradle wrapper caching.
- [X] T017 [US2] Add the fixed `main` ref guard, one-release-at-a-time concurrency group with `cancel-in-progress: false`, and duplicate date-tag/release preflight to `.github/workflows/android-release.yml`, failing before publication when unsafe.
- [X] T018 [US2] Add detekt, debug unit tests, and `assembleRelease` to `.github/workflows/android-release.yml` before any signing or publication step.
- [X] T019 [US2] Add protected `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` handling to `.github/workflows/android-release.yml`, decoding only to temporary runner storage and deleting the keystore after use.
- [X] T020 [US2] Configure the release build/signing step in `.github/workflows/android-release.yml` to sign the release APK with the project-owned key, run `apksigner verify`, and fail on verification errors.
- [X] T021 [US2] Add certificate fingerprint extraction, SHA-256 checksum generation, source revision metadata, and release notes/assets to `.github/workflows/android-release.yml`.
- [X] T022 [US2] Add the GitHub Release publication step to `.github/workflows/android-release.yml` using `contents: write`, the signed APK, fingerprint/checksum metadata, and no token/secret output.
- [X] T023 [US2] Document keystore generation, secure backup, Base64 secret storage, required secret names, release trigger, signature verification, and GitHub Release output in `.github/workflows/README.md`.

**Checkpoint**: A valid `main` run publishes one traceable signed APK; unsafe or failed runs publish nothing.

---

## Phase 5: User Story 3 - Make build and release results diagnosable (Priority: P2)

**Goal**: Make setup, validation, signing, failure, and publication evidence easy to locate.

**Independent Test**: Inspect workflow summaries, logs, uploaded reports, signed APK metadata, certificate fingerprint, checksum, and release notes after validation and release runs.

### Tests for User Story 3

- [X] T024 [P] [US3] Add workflow-stage contract tests in `scripts/ci/test_workflow_diagnostics.py` for named setup, validation, build, signing, verification, artifact, and publication stages.
- [X] T025 [US3] Extend `scripts/ci/test_workflow_diagnostics.py` with artifact/metadata tests for `retention-days: 14`, source revision, failure-report uploads, certificate fingerprint, SHA-256 checksum, and release-readiness evidence locations.

### Implementation for User Story 3

- [X] T026 [US3] Add named summary steps and failure-time diagnostic collection to `.github/workflows/android-ci.yml`.
- [X] T027 [US3] Add signing, verification, source revision, checksum, and publication evidence to `.github/workflows/android-release.yml` without exposing secret values.
- [X] T028 [US3] Update `README.md` with validation and signed-release instructions, `main` branch policy, secret handling, artifact locations, fingerprint/checksum verification, troubleshooting, and app-store scope boundaries.

**Checkpoint**: Developers can identify the failed stage and verify a downloaded release APK.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Run local checks, quickstart validation, and final security review.

- [X] T029 [P] Add `scripts/ci/run_tests.sh` to execute workflow validation, Python contract tests, and documented Gradle preflight commands.
- [X] T030 [P] Add `scripts/ci/README.md` describing static checks, safe test-release validation, keystore setup, and secret hygiene.
- [X] T031 Run all locally verifiable scenarios in `specs/002-github-actions-android-cicd/quickstart.md`, record 95% validation, 5-minute release-start, and 2-minute evidence-location results, and keep credentials/key material out of commits; defer live GitHub release execution until protected secrets are configured.
- [X] T032 Review `.github/workflows/`, `scripts/ci/`, and `README.md` for least-privilege permissions, secret leakage, keystore cleanup, duplicate-release behavior, and focused reviewable changes.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup and blocks all stories.
- **User Story 1 (Phase 3)**: Depends on Phase 2 and delivers the MVP validation pipeline.
- **User Story 2 (Phase 4)**: Depends on Phase 2 and reuses US1 toolchain commands.
- **User Story 3 (Phase 5)**: Depends on US1 and US2 workflow stages.
- **Polish (Phase 6)**: Depends on all desired stories.

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2; independent MVP.
- **US2 (P1)**: Starts after Phase 2; shares validation conventions with US1 but must pass its own release tests.
- **US3 (P2)**: Starts after the US1/US2 workflow files exist.

### Within Each User Story

- Tests are written before implementation and should fail for missing behavior.
- Workflow structure precedes command, signing, artifact, and publication steps.
- Static contracts pass before GitHub Actions runs are triggered.
- Story checkpoint validation completes before the next story.

### Parallel Opportunities

- T004, T006 can run in parallel after setup.
- T007 and T008 can run in parallel before US1 implementation.
- T024 can run in parallel with other US3 preparation; T025 follows T024 because both use the same file.
- T029 and T030 can run in parallel during polish.
- T013–T015 are intentionally sequential because T013/T014 share a file and T015 validates the completed signing contract.

## Parallel Example: User Story 1

```text
Task: T007 Add trigger/toolchain contract tests in scripts/ci/test_validate_workflows.py
Task: T008 Add command/artifact contract tests in scripts/ci/test_ci_commands.py
```

## User Story 2 Ordering

```text
T013 → T014 → T015 → T016 → T017 → T018 → T019 → T020 → T021 → T022 → T023
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Setup and Foundational phases.
2. Implement and test `.github/workflows/android-ci.yml`.
3. Run static checks and local Gradle validation.
4. Trigger the workflow on GitHub and verify artifacts.

### Incremental Delivery

1. Add US1 for automated build validation.
2. Add US2 for controlled, signed GitHub Release APK publication.
3. Add US3 for diagnostics and user verification metadata.
4. Run the complete quickstart and security review before delivery.

### Notes

- `[P]` is used only where tasks affect different files and have no incomplete dependency.
- Never commit keystores, Base64 keystore content, passwords, tokens, or generated release secrets.
- Instrumented tests remain outside the baseline GitHub-hosted workflow unless emulator provisioning is separately specified.

## Phase 7: Convergence

- [X] T033 Add normalized-date equality validation and negative contract tests for invalid `YYYY.MM.DD` dates in `.github/workflows/android-release.yml` and `scripts/ci/test_release_workflow.py` per FR-005 (partial).
- [X] T034 Add explicit `$GITHUB_STEP_SUMMARY` setup, validation, build, signing, verification, artifact, and publication evidence to `.github/workflows/android-ci.yml`, `.github/workflows/android-release.yml`, and `scripts/ci/test_workflow_diagnostics.py` per FR-010/T026 (missing).
- [ ] T035 Configure the protected GitHub `release` Environment with the four signing secrets, execute a disposable unique-date GitHub Release from `main`, and record the live verification result without committing credentials or key material per FR-008/US2-AC3 (partial).
- [X] T036 Add Python cache patterns to `/home/xzkj5g/work-utilities/.gitignore` and remove generated `scripts/ci/__pycache__/` files from the deliverable per plan source hygiene (missing).
- [X] T037 Extend `scripts/ci/test_validate_workflows.py` to assert every action SHA listed in `plan.md`, including artifact upload and release publication pins per plan: exact action dependencies (partial).

## Phase 8: Convergence

- [ ] T038 Complete the protected GitHub `release` Environment setup and run a disposable unique-date signed release from `main`, recording branch, signature, fingerprint, checksum, publication, and cleanup evidence without committing credentials or key material per FR-008/US2-AC3 (partial).
