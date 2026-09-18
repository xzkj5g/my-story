# Phase 0 Research: GitHub Actions Android CI/CD

## 1. Workflow split

- **Decision**: Use separate validation and release workflows.
- **Rationale**: Pull requests and pushes need non-publishing feedback, while releases need manual
  approval, branch checks, signing secrets, publication permissions, and concurrency control.
- **Alternatives considered**: One conditional workflow was rejected because it makes permissions
  and signing behavior harder to audit.

## 2. Java, Gradle, and Android SDK

- **Decision**: Run the Gradle wrapper with Java 17 and explicitly prepare Android API 36.
- **Rationale**: The project targets Java 17, uses Gradle 8.13/AGP 8.9.1, and compiles against
  API 36. Explicit setup avoids runner-image drift and JDK 25 incompatibility.
- **Alternatives considered**: Using the runner default JDK/SDK was rejected because defaults can
  change independently of the repository.

## 3. Action dependencies

- **Decision**: Pin every third-party action to a full commit SHA and retain the major version in
  a comment. Use `actions/checkout@v6`
  (`de7274f081f381c8f8158605e0321c36c376e2e6`), `actions/setup-java@v7`
  (`043fb46d1a93c77aae656e7c1c64a875d1fc6a0a`), `gradle/actions/setup-gradle@v6`
  (`9c971963bec38e04b3d30dcc455b5382be2fdbfb`), `android-actions/setup-android@v4`
  (`be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd`), `actions/upload-artifact@v8`
  (`3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c`), and
  `softprops/action-gh-release@v3` (`efb35369e0ad2afab669f228072c1b0d510eae64`).
- **Rationale**: Full SHA pinning provides reproducible action code and reduces supply-chain drift;
  comments preserve maintainability when Dependabot or a planned review updates a pin.
- **Alternatives considered**: Mutable major tags and runner-preinstalled SDK assumptions were
  rejected due to reproducibility and supply-chain risk. GitHub CLI remains a viable publication
  alternative, but the pinned release action gives a declarative asset contract.

## 4. Validation scope

- **Decision**: CI runs detekt, debug unit tests, and a debug APK build. Release repeats detekt and
  unit tests, builds the release APK, signs it, verifies it, and produces checksum metadata.
- **Rationale**: These commands work without a connected device and match the project's documented
  local checks. Instrumented tests remain an explicit on-device path until emulator provisioning is
  separately specified.
- **Alternatives considered**: Adding an emulator matrix now was rejected as excessive runtime and
  maintenance for the requested baseline pipeline.

## 5. Release signing lifecycle

- **Decision**: Generate one project-owned release keystore once on a secure development machine,
  back it up securely, store its Base64 content plus passwords in protected GitHub Secrets or an
  Environment, decode it only into temporary runner storage, sign the APK, verify with `apksigner`,
  and delete temporary material.
- **Rationale**: A stable signing identity lets Android recognize future APKs as updates while
  keeping private credentials out of source control. Signature verification and checksum metadata
  provide evidence that the published artifact is the intended build.
- **Alternatives considered**: Unsigned release artifacts were rejected because they are not normal
  installable distribution packages. Debug signing was rejected because it is development identity
  material. App-store managed signing was rejected because store distribution is out of scope.

## 6. Release destination and metadata

- **Decision**: Publish the signed APK to a GitHub Release with the source revision, certificate
  fingerprint, and SHA-256 checksum in release notes or adjacent public assets.
- **Rationale**: GitHub Releases directly support the requested download flow and give users public
  verification information without exposing the private key.
- **Alternatives considered**: Google Play/App Store publication was rejected as out of scope.

## 7. Branch, input, duplicate, and concurrency policy

- **Decision**: Release uses `workflow_dispatch`, requires a unique `YYYY.MM.DD` date tag such as
  `2026.09.18`, rejects publication unless the ref is `main`, fails for an existing tag or
  release, and serializes runs with `cancel-in-progress: false`.
- **Rationale**: These rules prevent accidental branch publication, races, and silent overwrites.
- **Alternatives considered**: Automatic version increments and cancelling active releases were
  rejected because they could surprise release operators.

## 9. Artifact retention and readiness evidence

- **Decision**: Retain CI debug APKs and diagnostic reports for 14 days. GitHub Release assets are
  retained with the release. Record the 95% validation success, five-minute release-start, and
  two-minute evidence-location criteria during quickstart/release-readiness validation rather than
  enforcing them as per-run workflow gates.
- **Rationale**: Fourteen days supports normal investigation without indefinite artifact storage;
  historical and operator-experience measures cannot be validly evaluated from one workflow run.
- **Alternatives considered**: Thirty- and ninety-day retention were rejected as unnecessary for
  the current project, and per-run gates were rejected because they require historical samples.

## 8. Permissions and secret handling

- **Decision**: CI uses `contents: read`. Release uses only the minimum `contents: write` needed
  for GitHub Release publication; signing secrets are referenced only by signing steps and never
  printed.
- **Rationale**: Least privilege limits blast radius and satisfies the security requirements.
- **Alternatives considered**: Long-lived personal access tokens and repository-stored keystores were
  rejected because the built-in token and protected secret storage are sufficient.
