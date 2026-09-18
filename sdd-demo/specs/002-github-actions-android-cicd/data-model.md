# Data Model: GitHub Actions Android CI/CD

## Workflow Run

One validation or release execution.

| Field | Description | Validation |
|---|---|---|
| workflow | Validation or release workflow | Must match a configured workflow |
| event | Pull request, push, or manual dispatch | Must be supported by that workflow |
| source revision | Commit SHA checked out | Must be available in the repository |
| source ref | Branch or tag | Release publication requires `main` |
| status | Queued, running, succeeded, or failed | Required command failure fails the run |
| logs | Step output | Must not contain secrets |
| artifacts | APKs, reports, and metadata | Retained according to workflow policy |

## Release Request

A manual request to publish a GitHub Release.

| Field | Description | Validation |
|---|---|---|
| release tag | Unique date identifier such as `2026.09.18` | Required and must match `YYYY.MM.DD` |
| source branch | Publication source | Must be `main` |
| release title | Human-readable title | Derived from or consistent with date tag |
| signing inputs | Keystore, alias, and passwords | Loaded only from protected secrets |

## Release Keystore

The long-lived project signing identity generated outside the repository.

| Field | Description | Protection |
|---|---|---|
| keystore file | Private key container | Secure offline backup and protected secret storage |
| alias | Release key alias | Secret/configuration value |
| keystore password | Container password | Protected secret |
| key password | Private-key password | Protected secret |
| certificate fingerprint | Public identity evidence | Safe to publish with releases |

## Signed Build Artifact

The release APK distributed through GitHub Releases.

| Field | Description | Validation |
|---|---|---|
| APK file | Release-configured Android package | Must be generated and signed before publication |
| source revision | Commit used to build | Must match validated `main` revision |
| signature | APK signing identity | `apksigner verify` must pass |
| SHA-256 | Download integrity checksum | Must be computed from the published APK |

## Published Release

The GitHub Release record and public assets.

| Field | Description | Validation |
|---|---|---|
| release tag | Immutable identifier | Existing tag causes visible failure |
| source revision | Validated commit | Must be from `main` |
| signed APK | Downloadable release asset | Uploaded only after all checks pass |
| fingerprint/checksum | Public verification metadata | Must not expose private key material |

## State Transitions

```text
Release Request
    → rejected (wrong branch, invalid/duplicate tag, missing secret, concurrent policy)
    → validating
    → signing
    → failed (validation, signing, verification, or publication error)
    → ready to publish
    → published
```
