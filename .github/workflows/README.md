# Android GitHub Actions CI/CD

## Validation workflow

`android-ci.yml` runs on pull requests and pushes to `main`. It uses Java 17, Android API 36,
the Gradle wrapper, and stable major-version action tags:

| Action | Major version |
|---|---|
| `actions/checkout` | v6 |
| `actions/setup-java` | v6 |
| `gradle/actions/setup-gradle` | v6 |
| `android-actions/setup-android` | v4 |
| `actions/upload-artifact` | v7 |
| `softprops/action-gh-release` | v3 |

The workflow runs detekt, debug unit tests, and a debug APK build. Debug APKs and diagnostics are
uploaded with `retention-days: 14`. Local reproduction:

```sh
./gradlew detekt
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Signed release workflow

The release workflow is manual and must be run from the fixed `main` branch. Enter a unique date
tag in `YYYY.MM.DD` format, such as `2026.09.18`. The workflow serializes release runs with
`cancel-in-progress: false`; an existing tag or GitHub Release causes a visible failure before
signing.

Required protected GitHub Secrets or `release` Environment values:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Generate one project-owned keystore outside the repository, back it up securely, and Base64-encode
it for the protected secret. Never commit the keystore, encoded content, or passwords. CI decodes
the keystore only under `$RUNNER_TEMP`, signs the release APK, runs `apksigner verify`, computes a
SHA-256 checksum, extracts certificate metadata, and removes temporary signing files.

Successful GitHub Releases contain:

- the release-signed APK,
- `app-release.apk.sha256`,
- `certificate.txt`, and
- release notes containing the source revision and date tag.

The certificate fingerprint and checksum are public verification metadata. Google Play or another
app store is not part of this workflow.

## Permissions and troubleshooting

The validation workflow has `contents: read`. Only the release workflow has `contents: write`.
Signing secrets are never available to pull-request builds and must not be printed in logs.

Use `python3 scripts/ci/validate_workflows.py` and
`python3 scripts/ci/validate_release_secrets.py` for local static checks. Review major-version tags
during planned action maintenance. If a workflow fails,
inspect the named setup, validation, signing, verification, or publication step and download the
14-day diagnostic artifacts.
