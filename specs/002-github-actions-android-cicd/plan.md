# Implementation Plan: GitHub Actions Android CI/CD

**Branch**: `002-github-actions-android-cicd` | **Date**: 2026-09-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-github-actions-android-cicd/spec.md`

## Summary

Add separate GitHub Actions validation and release workflows. Validation runs on pull requests and
pushes to `main`, prepares Java 17 and Android API 36, runs detekt, unit tests, and a debug APK
build, then uploads diagnostics and artifacts. The manually triggered release workflow accepts a unique `YYYY.MM.DD` date tag, permits publication
only from `main`, repeats required validation, assembles and signs
the release APK with one project-owned keystore loaded from protected GitHub Secrets or an
Environment, verifies the signature, computes a SHA-256 checksum, and publishes the APK plus
fingerprint/checksum metadata as a GitHub Release. No app-store publication is included.

## Technical Context

**Language/Version**: GitHub Actions YAML; Gradle/Kotlin Android project targeting Java 17

**Primary Dependencies**: GitHub-hosted Ubuntu runner; stable major-version GitHub Actions:
`actions/checkout@v6`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`,
`android-actions/setup-android@v4`, `actions/upload-artifact@v7`, and
`softprops/action-gh-release@v3`; Gradle Wrapper; JDK `apksigner`/`keytool`

**Storage**: GitHub Actions artifacts for CI outputs and reports; GitHub Releases for the signed APK,
certificate fingerprint, checksum, and release notes; protected GitHub Secrets/Environment for the
Base64-encoded keystore and signing passwords

**Testing**: Static workflow/contract tests; `./gradlew detekt`; `./gradlew testDebugUnitTest`;
`./gradlew assembleDebug`; `./gradlew assembleRelease`; APK signature verification with
`apksigner verify`; SHA-256 checksum generation; instrumented tests remain a local/device
validation step unless a later scope explicitly provisions an emulator

**Target Platform**: GitHub Actions Ubuntu runner; Android API 36 compile/target SDK and API 26
minimum SDK

**Project Type**: CI/CD automation for a native Android application

**Performance Goals**: A developer can start an authorized release in under 5 minutes of
interactive effort; validation exposes setup and failing stages without local environment setup

**Constraints**: Releases are manual and `main`-only; required checks pass before publication;
release tags use unique `YYYY.MM.DD` values; concurrent releases are serialized with
`cancel-in-progress: false`; release signing material is never committed or printed; temporary
keystore files are removed; CI APKs and diagnostic reports are retained for 14 days; no app-store
publication is included

**Scale/Scope**: One Android application module, one release workflow at a time, direct APK
distribution through GitHub Releases, no multi-platform matrix or multi-module orchestration

## Constitution Check

| Principle | Check | Status |
|---|---|---|
| I. User-Story-First Development | Validation, on-demand signed release, and diagnostics are independently testable stories with P1/P2 priorities. | PASS |
| II. Specification Before Implementation | Workflow behavior, signing lifecycle, branch restrictions, artifacts, and release policy are defined in `spec.md` before workflow implementation. | PASS |
| III. Simplicity & YAGNI | Uses the existing Gradle wrapper and standard GitHub Actions; signing is limited to the requested GitHub Release APK and does not add app-store automation. | PASS |
| IV. Automated Testing Required | Static contracts, Gradle checks, `apksigner verify`, checksum generation, and release dry-run/test-release validation cover each story. | PASS |
| V. Spec-Plan-Tasks-Code Consistency | Workflow paths, commands, signing secrets, permissions, inputs, metadata, and publication destination are defined in this plan and the contracts artifact. | PASS |
| VI. Small, Reviewable Commits | Validation workflow, release workflow/signing, documentation, tests, and polish remain separable changes. | PASS |
| Technology Stack Requirements (Android) | CI invokes the existing Kotlin/Gradle/Android project and does not alter application architecture. | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/002-github-actions-android-cicd/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── github-actions.md
└── tasks.md
```

### Source Code (repository root)

```text
.github/
└── workflows/
    ├── android-ci.yml
    ├── android-release.yml
    └── README.md
scripts/
└── ci/
    ├── validate_workflows.py
    ├── test_validate_workflows.py
    ├── test_ci_commands.py
    ├── test_release_workflow.py
    ├── test_release_signing.py
    ├── test_workflow_diagnostics.py
    ├── run_tests.sh
    ├── README.md
    └── (no keystore files or signing secrets)
README.md
app/
gradlew
```

**Structure Decision**: Keep automation in `.github/workflows/`, static checks in `scripts/ci/`,
and use the existing Gradle wrapper. The release workflow receives protected signing material only
at runtime, signs and verifies a temporary APK, publishes public fingerprint/checksum metadata,
and removes temporary signing files.

## Complexity Tracking

No constitution violations or unnecessary architecture are required.
