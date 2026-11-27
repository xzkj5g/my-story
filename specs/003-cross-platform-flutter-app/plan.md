# Implementation Plan: Cross-platform My Story app

**Spec**: [spec.md](spec.md)

## Technical context
- Flutter 3.50 / Dart 3.14 for the UI; packages: `provider`, `photo_manager` (+ image provider),
  `video_player`, `shared_preferences`, `google_sign_in`, `http`, `flutter_svg`, `intl`.
- Android: Kotlin, AndroidX Media3 Transformer 1.10 (the engine from spec 001), foreground service, `MediaStore`.
- iOS/iPadOS: Swift, AVFoundation (`AVMutableComposition`, `AVAssetExportSession`, `AVAssetWriter` for
  still images), Photos. Deployment target iOS 15, `TARGETED_DEVICE_FAMILY = 1,2`.
- Bundle/application id `com.mystory.app`.

## Decisions
1. **Flutter over React Native / Kotlin Multiplatform**: one pixel-faithful UI for three form factors and
   direct reuse of the proven Android engine behind a thin channel.
2. **Shared channel contract**; Android saves into `MediaStore` itself and returns an `assetId`, iOS returns a
   temp file path that Dart saves into Photos, so each OS uses its own storage rules.
3. **Story library = list of asset ids** in `shared_preferences`; the video file stays in the gallery, so the
   user keeps ownership and no extra storage permission is required.
4. **Concatenation only**: the user chooses fps and aspect ratio; resolution is fixed at 1080p class
   (long edge 1920; 9:16 swaps the sides; 1:1 is square of the short side).
5. **YouTube** via `google_sign_in` (scope `youtube.upload`) and a hand-written resumable uploader over `http`
   (chunks of 256 KiB multiples, `Content-Range`, resume with `bytes */total`). Client ids come from
   `--dart-define-from-file` and an optional git-ignored `OAuth.xcconfig`.
6. **CI**: one macOS job builds everything; fast static/Dart checks run first on Ubuntu.

## Layout
`lib/` (UI, state, services), `android/` (host + engine), `ios/Runner/StoryCompiler/` (engine),
`test/`, `ios/RunnerTests/`, `docs/youtube-setup.md`, `.github/workflows/{ci,release}.yml`, `scripts/ci/`.

## Constitution check
I-III satisfied by five small stories and no extra services; IV by Dart, Kotlin and XCTest suites;
V by this spec set plus the amended constitution (v2.0.0); VI by small commits.
