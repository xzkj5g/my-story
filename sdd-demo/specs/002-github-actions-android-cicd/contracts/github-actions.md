# GitHub Actions CI/CD Contract

## Validation workflow

**Path**: `.github/workflows/android-ci.yml`

| Contract | Required behavior |
|---|---|
| Events | Pull requests and pushes to `main` |
| Permissions | `contents: read` |
| Runtime | Ubuntu runner, Java 17, Android API 36, Gradle wrapper |
| Required checks | detekt, debug unit tests, debug APK build |
| Outputs | Debug APK and diagnostics as workflow artifacts |
| Failure | Any required command failure fails the workflow |

## Release workflow

**Path**: `.github/workflows/android-release.yml`

| Contract | Required behavior |
|---|---|
| Trigger | `workflow_dispatch` only |
| Input | Required unique `YYYY.MM.DD` release tag, for example `2026.09.18` |
| Source branch | Publication allowed only from fixed branch `main` |
| Permissions | `contents: write` only for release publication |
| Required checks | detekt, debug unit tests, release build |
| Signing | Decode protected keystore secrets temporarily, sign APK, run `apksigner verify`, delete temporary material |
| Publication | Create a GitHub Release with signed APK, source revision, certificate fingerprint, and SHA-256 checksum |
| Duplicate policy | Existing tag or release causes visible failure before signing; no silent overwrite |
| Concurrency | One release run at a time; later runs wait and are not silently cancelled |
| CI retention | Debug APKs and diagnostics use `retention-days: 14` |
| Failure | No release is published if validation, signing, verification, or build fails |
| Security | No token, keystore, or password is printed |

## Documentation contract

`README.md` and `.github/workflows/README.md` must document:

- validation workflow events and local commands,
- manual release trigger and default branch (`main`),
- release-tag input format,
- release keystore generation, backup, and protected-secret names,
- generated artifact, fingerprint, checksum, and GitHub Release locations,
- app-store publication remains out of scope.
