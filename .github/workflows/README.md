# GitHub Actions CI/CD

## CI workflow

`ci.yml` runs on pull requests, on pushes to `main` and `cursor/**` branches, and on demand. It uses
Flutter 3.50.1, Java 17, Android API 36 and stable major-version action tags:

| Action | Major version |
|---|---|
| `actions/checkout` | v6 |
| `actions/setup-java` | v6 |
| `subosito/flutter-action` | v2 |
| `android-actions/setup-android` | v4 |
| `actions/upload-artifact` | v7 |
| `softprops/action-gh-release` | v3 |

Jobs:

1. `checks` (Ubuntu): validates the workflows (`scripts/ci`), `flutter analyze`, `flutter test`.
2. `build` (macOS, after `checks`): Android unit tests (`android/gradlew :app:testDebugUnitTest`), release APK,
   iOS native XCTests on an iPhone simulator (`scripts/ci/select_ios_simulator.sh` picks the device),
   and an unsigned release iOS app (`TARGETED_DEVICE_FAMILY=1,2`, so it covers iPhone and iPad).

Artifacts (`android-apk`, `ios-app-unsigned`, `validation-reports`) are kept with `retention-days: 14`.

Optional repository variables `GOOGLE_SERVER_CLIENT_ID` and `GOOGLE_IOS_CLIENT_ID` enable YouTube
sign-in in CI builds (see `docs/youtube-setup.md`).

## Release workflow

`release.yml` is manual and must run from `main`. Enter a unique date tag in `YYYY.MM.DD` format, such
as `2026.09.18`. Runs are serialized with `cancel-in-progress: false`; an existing tag or GitHub
Release fails visibly before building.

Required protected GitHub Secrets or `release` Environment values:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Generate one project-owned keystore outside the repository, back it up securely and Base64-encode it for
the secret. CI decodes it under `$RUNNER_TEMP`, writes a temporary `android/key.properties`, builds the
signed APK and AAB, runs `apksigner verify`, computes a SHA-256 checksum, extracts certificate metadata and
deletes the temporary files in an unconditional cleanup step.

A successful GitHub Release contains the signed APK and AAB, `app-release.apk.sha256`, `certificate.txt`,
the unsigned iOS app zip, and release notes with the source revision and date tag. App Store signing,
TestFlight and Google Play uploads are not part of this workflow yet.

## Permissions and troubleshooting

CI has `contents: read`. Only the release workflow has `contents: write`. Signing secrets are never
available to pull-request builds and must not be printed in logs.

Use `python3 scripts/ci/validate_workflows.py` and `python3 scripts/ci/validate_release_secrets.py` for
local static checks. If a workflow fails, inspect the named step and download the 14-day artifacts (the
`validation-reports` artifact contains the iOS `.xcresult` bundle).
