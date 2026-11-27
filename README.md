# My Story

Turn the photos and videos in your library into one video. Pick materials from your camera roll,
review them as a simple timeline, choose a frame rate and aspect ratio, and My Story concatenates
everything into a single MP4 saved on your device. Finished stories live in the in-app Library, and
you can upload them to YouTube from the app.

The UI follows the Figma template (Splash and Home pages) for text, fonts, colors and layout.

## What the app does

- **Home**: the header and the two top buttons from the template, then every photo and video in the
  library as a scrollable card grid. Tap cards to multi-select (numbered in tap order) and press
  **create story**.
- **Your story** (after create story): the selection as a horizontal thumbnail timeline (long-press
  and drag to reorder, tap the cross to remove), a title, the only two choices - **frame rate**
  (24 / 30 / 60) and **aspect ratio** (16:9, 9:16, 1:1) - and a **Start to create** button that leads
  to a progress view with cancel.
- **Library**: every story created by the app. Tap to play, delete from the viewer, or upload to
  YouTube.
- **Settings**: connect YouTube with the standard Google sign-in flow so stories can be uploaded.

How a story is built: items are concatenated in order. Photos are shown for 3 seconds with silent
audio, videos keep their length and audio, and every segment is fitted (letterboxed) into the chosen
frame. Output is 1080p-class (long edge 1920) at the chosen fps. The video is saved to `Movies/MyStory`
on Android and into the Photos library on iOS and iPadOS.

## Architecture

```text
lib/                  Flutter UI (shared by Android, iPhone and iPad)
  screens/ widgets/   Splash, Home shell, Story editor, Library, Viewer, Settings
  state/              ChangeNotifier controllers (selection, gallery, creation, library, YouTube)
  services/           Interfaces + implementations: gallery (photo_manager), compiler channel,
                      story repository, YouTube auth + resumable uploader
android/              Android host app and the Media3 Transformer compile engine (Kotlin)
ios/Runner/StoryCompiler/  AVFoundation compile engine (Swift)
test/                 Dart unit and widget tests (fake services)
ios/RunnerTests/      XCTest coverage of the Swift engine
specs/                Spec Kit artifacts; 003 describes the Flutter app
scripts/ci/           Static checks for the GitHub Actions workflows
```

Both native engines implement the same contract: method channel `my_story/compiler`
(`startCompile`, `cancelCompile`, `resourceWarning`) and event channel `my_story/compiler_events`
(`running` / `succeeded` / `failed` / `cancelled` with `progress`). Android returns the `assetId` of
the video it saved into the gallery; iOS returns a temporary file that Dart saves into Photos.

## Run it locally

Requirements: Flutter 3.50 (Dart 3.14), JDK 17 and the Android SDK for Android, Xcode 16+ for iOS.

```sh
flutter pub get
flutter run                       # a connected device or emulator
flutter analyze && flutter test   # Dart checks
(cd android && ./gradlew testDebugUnitTest)   # Kotlin engine unit tests
```

YouTube sign-in needs OAuth client ids; without them the app runs and Settings says sign-in is not
configured. See [docs/youtube-setup.md](docs/youtube-setup.md), then run with
`flutter run --dart-define-from-file=config/oauth.json`.

Emulators often have weak video encoders, so Android may fall back to a smaller output size there;
physical devices encode at the requested size.

## CI/CD

GitHub Actions (see `.github/workflows/README.md`):

- **CI** (`ci.yml`): workflow checks, `flutter analyze`, `flutter test`, then on a macOS runner the
  Android unit tests and release APK, the iOS native XCTests on a simulator, and an unsigned iPhone
  and iPad app. APK and iOS app are kept as artifacts for 14 days.
- **Release** (`release.yml`): manual, from `main`, with a unique `YYYY.MM.DD` tag. Builds a signed
  APK and AAB (protected secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) plus an unsigned iOS app and publishes them to a GitHub
  Release. App Store signing and upload are not part of the pipeline yet.

Never commit keystores, provisioning profiles, tokens or `config/oauth.json`.
