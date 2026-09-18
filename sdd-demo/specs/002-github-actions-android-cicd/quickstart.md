# Quickstart: Validate GitHub Actions Android CI/CD

## Prerequisites

- GitHub Actions enabled and permission to view workflow runs.
- Release validation permission to create a test GitHub Release.
- A project-owned release keystore generated once on a secure development machine.
- Protected secrets or Environment values configured for the keystore content, alias, and passwords.
- The current default branch is `main`.

## Generate and persist the release key once

Do this outside the repository on a secure development machine:

```sh
keytool -genkeypair -v \
  -keystore video-compiler-release.jks \
  -alias video-compiler \
  -keyalg RSA -keysize 4096 -validity 10000
```

Back up the keystore securely. Encode a copy for protected GitHub secret storage:

```sh
base64 -w 0 video-compiler-release.jks > release-keystore.base64
```

Configure protected values such as:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Never commit the keystore, encoded content, or passwords.

## Local preflight

```sh
./gradlew detekt
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

## Validation workflow

1. Push a branch or open a pull request targeting `main`.
2. Open **Actions** and select the Android validation workflow.
3. Inspect setup, detekt, unit-test, build, and artifact-upload steps.
4. Confirm the debug APK and diagnostics are downloadable.

## Signed release workflow

1. Ensure the desired release commit is on `main`.
2. Open **Actions**, select the Android release workflow, and choose **Run workflow** from `main`.
3. Enter a unique release tag such as `2026.09.18`.
4. Confirm validation, signing, `apksigner verify`, and checksum steps complete before publication.
5. Open the GitHub Release and verify the signed APK, source revision, certificate fingerprint, and SHA-256 checksum.

## Negative checks

- Attempt a manual run from a non-`main` ref and verify publication is rejected.
- Reuse an existing date tag and verify the workflow fails without overwriting the release.
- Temporarily cause a validation/build/signing failure and verify no GitHub Release is created.
- Inspect logs to confirm no token, keystore, or password is printed.
- Verify temporary keystore files are removed from the runner workspace after the job.

## Release-readiness evidence

Record these checks during representative validation and release runs rather than treating them as
per-run workflow gates:

- Whether at least 95% of sampled valid validation runs complete without manual environment setup.
- Whether an authorized release from `main` can be started within 5 minutes of interactive effort.
- Whether logs, validation results, and release assets can be located within 2 minutes.
