# Feature Specification: GitHub Actions Android CI/CD

**Feature Branch**: `002-github-actions-android-cicd`

**Created**: 2026-09-18

**Status**: Draft

**Input**: User description: "As a developer i want to have working CICD pipeline on github using github action solution, so that i can trigger android build and release process from default branch on demand"

## Clarifications

### Session 2026-09-18

- Q: Should the first CI/CD release publish the current `assembleRelease` APK without adding signing-key management, or should it require a signed APK using repository secrets? → A: Require a release-signed APK using one project-owned private signing key stored in protected GitHub Secrets or an Environment.
- Q: What release tag format should the manual workflow require? → A: A date tag in `YYYY.MM.DD` format, such as `2026.09.18`.
- Q: When two manual release requests run at the same time, should the second request wait for the first to finish or be cancelled immediately? → A: Serialize releases; the second run waits, then checks the tag again.
- Q: How long should CI debug APKs and diagnostic reports remain available as workflow artifacts? → A: 14 days.
- Q: Is `main` the fixed default branch from which releases must be published? → A: Yes, releases must run from `main`.
- Q: Should the 95%, 5-minute, and 2-minute success criteria be measured as release-readiness evidence or enforced as automated workflow gates? → A: Record them as release-readiness evidence, not workflow gates.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Validate changes with an Android build (Priority: P1)

As a developer, I want GitHub Actions to build and validate the Android project so that changes can be checked consistently in the repository.

**Why this priority**: A reliable build is the foundation for any release and provides immediate feedback when code or configuration breaks.

**Independent Test**: Push a change or open a pull request, observe the workflow run, and verify that it completes successfully for a valid project or reports a clear failure for an invalid change.

**Acceptance Scenarios**:

1. **Given** a pull request or push event for a supported branch, **When** the workflow starts, **Then** it checks out the repository, prepares the required Android build environment, and runs the project's build and automated validation commands.
2. **Given** the project does not compile or a required validation command fails, **When** the workflow finishes, **Then** the run is marked failed and the failing step is visible in the workflow summary.
3. **Given** the build and validation commands pass, **When** the workflow finishes, **Then** the run is marked successful and generated build outputs are available as workflow artifacts when appropriate.

### User Story 2 - Trigger an on-demand release from the default branch (Priority: P1)

As a developer, I want to manually trigger a release workflow from the repository's default branch so that I can produce a release build when I choose.

**Why this priority**: The requested release process must be explicitly controlled and must not publish artifacts unexpectedly from unrelated branches or events.

**Independent Test**: Start the workflow manually from the default branch, select the requested release options, and verify that a release build is produced and published to the configured release destination.

**Acceptance Scenarios**:

1. **Given** the workflow is viewed from the default branch, **When** a developer selects the manual run action, **Then** the workflow is available through the repository's Actions interface and accepts the release inputs defined by the project.
2. **Given** a manual release run is requested from a non-default branch, **When** the workflow evaluates the request, **Then** it refuses to publish a release and reports that releases are allowed only from the default branch.
3. **Given** a manual release run is requested from the default branch and validation succeeds, **When** the workflow completes, **Then** it creates the configured release package and publishes it to the repository's release destination.
4. **Given** a manual release run fails before publication, **When** the workflow exits, **Then** no incomplete release is published and the failure is visible to the developer.

### User Story 3 - Make build and release results diagnosable (Priority: P2)

As a developer, I want build logs, test results, and release outputs to be easy to locate so that I can diagnose failures and verify what was published.

**Why this priority**: Clear evidence reduces recovery time and makes the automated process trustworthy for future releases.

**Independent Test**: Run both validation and release paths, then inspect the workflow summary, logs, retained artifacts, and release metadata.

**Acceptance Scenarios**:

1. **Given** a workflow run completes, **When** a developer opens the run, **Then** the run shows distinct steps for setup, validation, build, and release activities.
2. **Given** a validation or build failure occurs, **When** a developer inspects the run, **Then** the relevant logs and test results identify the failed command or stage.
3. **Given** a release succeeds, **When** a developer inspects the published release, **Then** the release identifies the source revision, includes the release-signed APK, and provides the signing certificate fingerprint and file checksum.

### Edge Cases

- The workflow must fail clearly when the required Android SDK, Java runtime, Gradle wrapper, or project dependency cannot be prepared.
- A release request must not publish if the workflow is not running from the default branch.
- A release request must not publish when its release tag is not in `YYYY.MM.DD` format or has already been used.
- CI debug APKs and diagnostic reports must remain available for the documented 14-day retention period.
- A release request must not publish if build or validation steps fail.
- Concurrent manual release requests must be serialized; the second run waits for the first to finish, then fails visibly if its date tag is already used.
- Missing or insufficient repository permissions must produce a visible failure before publication.
- Re-running a failed workflow must not create duplicate or misleading release artifacts.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST provide an automated GitHub Actions workflow that validates the Android project on supported repository events.
- **FR-002**: The validation workflow MUST run the project's configured build and automated test or quality-validation commands using the repository's supported toolchain.
- **FR-003**: The workflow MUST fail the run when compilation, tests, linting, or another required validation command fails.
- **FR-004**: The project MUST provide an on-demand release workflow trigger available from the repository's default branch.
- **FR-005**: The release workflow MUST require and validate a release tag in `YYYY.MM.DD` format, such as `2026.09.18`, and MUST reject malformed or already-used tags before signing or publication.
- **FR-006**: The release workflow MUST permit publication only when the workflow is running from the repository's default branch.
- **FR-007**: The release workflow MUST run required build and validation steps before publishing any release artifact.
- **FR-008**: The release workflow MUST publish a commonly consumable Android release artifact to the configured repository release destination after all required checks pass.
- **FR-008a**: The release artifact MUST be signed with one project-owned release key that is generated outside the repository and reused for every release.
- **FR-009**: The release workflow MUST stop without publishing an incomplete or failed release artifact when any required step fails.
- **FR-010**: The workflow MUST expose clear logs and step results for environment setup, validation, build, and release stages.
- **FR-011**: The workflow MUST retain CI debug APKs and diagnostic reports for 14 days; published GitHub Release assets remain available through the GitHub Release.
- **FR-012**: The workflow MUST use repository-managed credentials and permissions with least privilege for release publication, and MUST NOT expose secrets in logs.
- **FR-012a**: The workflow MUST load the release keystore and its passwords only from protected GitHub Secrets or an Environment, MUST verify the APK signature before publication, and MUST remove temporary signing material after use.
- **FR-012b**: The published release MUST include the signing certificate fingerprint and a SHA-256 checksum for the APK without exposing the private key or passwords.
- **FR-013**: The project MUST document how developers trigger validation and on-demand releases, which branch is the default release source, what inputs are required, and where outputs are published.
- **FR-014**: The release process MUST define behavior for repeated or concurrent release requests so that they cannot silently overwrite or duplicate releases.
- **FR-014a**: Concurrent release runs MUST be serialized, and a run whose date tag already exists MUST fail before signing or publication without overwriting the existing release.

### Key Entities

- **Workflow Run**: One execution of a validation or release workflow, identified by its event, source revision, branch, status, logs, and outputs.
- **Release Request**: A developer-initiated request containing the source branch and release inputs needed to create a release.
- **Build Artifact**: A generated Android package and associated validation evidence produced by a successful workflow run.
- **Published Release**: The repository release record and artifact made available after a successful, authorized release run.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Release-readiness evidence MUST record whether at least 95% of sampled valid pull-request or supported-branch validation runs completed successfully without manual environment setup; this is not an individual workflow gate.
- **SC-002**: 100% of validation runs with a compilation or required test failure are reported as failed and identify the failing workflow step.
- **SC-003**: Release-readiness evidence MUST record whether a developer can start an authorized release from `main` in no more than 5 minutes of interactive effort; this is not an individual workflow gate.
- **SC-004**: 100% of published releases originate from the default branch and have passed all required validation steps.
- **SC-005**: 100% of failed release runs leave no incomplete published release artifact.
- **SC-006**: Release-readiness evidence MUST record whether a developer can locate build logs, validation results, and generated release artifacts from the workflow run or linked release within 2 minutes; this is not an individual workflow gate.
- **SC-007**: Release credentials and secret values are absent from workflow logs in all validation and release runs.
- **SC-008**: Repeated or concurrent release requests follow the documented policy with no silent overwrites or duplicate release identifiers.

## Assumptions

- The repository's `main` branch is the authoritative source for releases and is protected through normal repository review controls.
- GitHub Actions is the only CI/CD platform in scope for this feature.
- The release destination is a GitHub repository release unless the planning phase documents a more specific repository-supported destination.
- The project already contains a Gradle wrapper and its normal local build and test commands remain the source of truth for CI validation.
- Signing credentials, if required for a release package, will be supplied through repository or environment secrets and will not be committed to source control.
- The release keystore will be generated once on a secure development machine, backed up securely, and treated as long-lived project identity material; losing it prevents seamless updates and exposing it compromises release identity.
- GitHub Actions will receive the keystore as protected secret material, commonly Base64-encoded, and will decode it only into temporary runner storage for signing.
- Uploading to external app stores is out of scope unless explicitly added in a later specification.
