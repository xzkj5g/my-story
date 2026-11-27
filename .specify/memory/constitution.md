<!--
Sync Impact Report
- Version change: 1.0.0 → 2.0.0
- Modified principles: none renamed; Principle IV clarified to cover both Dart and native tests
- Modified sections:
  - Technology Stack Requirements: Android-only (Kotlin/Gradle) → cross-platform Flutter UI with
    native video engines per platform (Kotlin/Media3 on Android, Swift/AVFoundation on iOS and iPadOS)
- Added sections: none
- Removed sections: none
- Amendment reason: the product now targets Android, iPhone and iPad from one UI codebase
  (see specs/003-cross-platform-flutter-app/). Redefining the mandatory stack is a MAJOR change.
- Follow-up TODOs:
  - TODO(RATIFICATION_DATE): original adoption date not provided by user; set when known.
-->

# my-story Constitution
<!-- Example: Spec Constitution, TaskFlow Constitution, etc. -->

## Core Principles

### I. User-Story-First Development
Every feature MUST be broken down and built as one small, independently testable user story at
a time. Each story MUST deliver working, verifiable behavior on its own before the next story
begins. Rationale: incremental delivery limits risk, keeps feedback loops short, and ensures the
project always has a working state.

### II. Specification Before Implementation
Behavior and acceptance criteria MUST be defined for a user story before implementation details
(architecture, libraries, UI layout) are chosen. Implementation MUST trace back to an approved
specification. Rationale: separating "what" from "how" avoids solving the wrong problem and
keeps specs, plans, and tasks reusable across implementation changes.

### III. Simplicity & YAGNI
Code MUST stay as simple as possible. Abstractions, frameworks, or design patterns MUST NOT be
introduced until they solve demonstrated duplication or complexity — not anticipated future
needs. Rationale: premature abstraction increases maintenance cost and obscures intent without
proven benefit.

### IV. Automated Testing Required (NON-NEGOTIABLE)
Every user story MUST have automated tests covering its acceptance criteria (Dart tests for shared
logic and UI, native unit or instrumented tests for each platform engine). Tests MUST be run
and MUST pass before the story is considered complete. Rationale: automated tests are the only
reliable, repeatable way to guarantee a story's behavior and to protect it from regressions.

### V. Spec-Plan-Tasks-Code Consistency
When requirements change, the specification, plan, tasks, and implementation MUST be updated
together so they remain consistent with one another. No artifact may be left describing behavior
that the code no longer implements, or vice versa. Rationale: stale artifacts erode trust in the
spec-driven workflow and cause future work to be based on wrong assumptions.

### VI. Small, Reviewable Commits
Work MUST be committed in small increments that each represent a working, reviewable state.
Commits MUST NOT bundle unrelated changes or leave the project in a broken state. Rationale:
small commits are easier to review, bisect, and revert, and they reinforce incremental,
story-by-story delivery.

## Technology Stack Requirements

This project is a cross-platform mobile application for Android, iPhone and iPad. The user
interface and application logic MUST be written once in Flutter (Dart) and shared by all
platforms. Video composition MUST run in native engines behind a single, documented platform
channel contract: Kotlin with AndroidX Media3 on Android, and Swift with AVFoundation on iOS and
iPadOS. Platform code is limited to media access, encoding and background execution; it MUST NOT
contain product UI. Android builds use the Gradle build system, and iOS builds use Xcode through
`flutter build`. Both platforms MUST follow the official guidance of their SDKs for permissions
(photo library, notifications, background work). Third-party dependencies MUST be justified against
Principle III (Simplicity & YAGNI) before adoption. Secrets (keystores, provisioning profiles,
OAuth credentials, tokens) MUST NOT be committed.

## Development Workflow

Each user story follows: (1) write/refine the specification and acceptance criteria, (2) plan
the implementation approach, (3) break the plan into tasks, (4) write automated tests from the
acceptance criteria, (5) implement the minimal code to pass the tests, (6) commit in small,
working increments, (7) update spec/plan/tasks if reality diverged. Code review (self or peer)
MUST confirm each of the above steps occurred before a story is marked done.

## Governance

This constitution supersedes all other project practices and conventions. Amendments require:
(1) a documented proposal describing the change and its rationale, (2) an explicit version bump
following semantic versioning — MAJOR for backward-incompatible principle removals/redefinitions,
MINOR for new principles or materially expanded guidance, PATCH for clarifications and wording
fixes, and (3) recording the date and reason for the change in this document's amendment history
(via the Sync Impact Report and the Last Amended date below). All plans, tasks, and reviews MUST
verify compliance with this constitution; any deviation MUST be justified in writing in the
relevant plan or task before proceeding.

**Version**: 2.0.0 | **Ratified**: TODO(RATIFICATION_DATE): original adoption date unknown | **Last Amended**: 2026-11-26
