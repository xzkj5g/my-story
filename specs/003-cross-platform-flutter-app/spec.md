# Feature Specification: Cross-platform My Story app

**Feature Branch**: `cursor/cross-platform-flutter-app-c959`
**Created**: 2026-11-26
**Status**: Implemented (Android verified on an emulator; iOS verified by CI only)
**Supersedes**: the Android-only UI of `001-video-compilation` (its compile engine is reused) and the
Android-only pipeline of `002-github-actions-android-cicd`.

**Input**: A user wants to create a video from the photos and videos in their library on Android,
iPhone and iPad, using the supplied Figma template (Splash and Home pages) as the visual anchor,
export it locally, keep a library of created stories, and upload stories to YouTube.

## User Scenarios & Testing

### User Story 1 - Pick materials on Home (Priority: P1)
The user opens the app, sees the splash, then Home with the template header and the two top buttons.
Below them every photo and video in the library is shown as cards; the user scrolls and multi-selects,
and presses **create story**.

Acceptance:
1. Given library access is granted, Home lists photos and videos newest first, loading more on scroll.
2. Selected cards show their order number; the selection bar shows the count, **Clear** and **create story**.
3. Given access is denied, Home explains why and offers to open settings; limited access offers to manage the selection.
4. Given nothing is selected, **create story** explains that at least one item is needed.

### User Story 2 - Review and create a story (Priority: P1)
After **create story** the user sees the selection as a thumbnail timeline (not a video-editor timeline),
chooses frame rate (24/30/60) and aspect ratio (16:9, 9:16, 1:1), then confirms with **Start to create**.
A progress view shows percentage with cancel; the result is a single MP4 with all items concatenated.

Acceptance:
1. Items can be reordered by long-press drag and removed; total duration updates (photos count 3 s).
2. Only frame rate and aspect ratio are offered; resolution is fixed at 1080p class.
3. Photos become 3 s clips with silent audio; videos keep length and audio; segments are letterboxed to the frame.
4. The file is saved to `Movies/MyStory` (Android) or the Photos library (iOS/iPadOS), shown as ready and added to Library.
5. Cancelling stops the job and removes partial output; failures show a readable message.
6. Android keeps compiling in a foreground service with a notification if the app is left.

### User Story 3 - Manage stories in Library (Priority: P2)
The Library tab lists stories created by the app. Tapping one plays it; the viewer can delete it
(with the system confirmation) or upload it to YouTube. Sharing is out of scope.

Acceptance:
1. Stories whose video was removed from the gallery disappear from Library.
2. Declining the system delete prompt keeps the story.

### User Story 4 - Connect YouTube and upload (Priority: P2)
Settings offers **Connect with YouTube** using the standard Google sign-in flow with the
`youtube.upload` scope. Connected users upload a story from the viewer, choosing privacy (default Private)
and seeing progress; a link to the video is shown when done.

Acceptance:
1. Without OAuth client ids the build explains that sign-in is not configured instead of failing.
2. Uploads are chunked and resumable, retry on transient errors, and refresh the token once after a 401.
3. Disconnecting revokes access locally.

### User Story 5 - Build every target in CI (Priority: P2)
A GitHub Actions pipeline on a macOS runner analyzes and tests the code, runs Android unit tests, builds the
Android APK, runs the Swift engine tests on an iOS simulator and builds an unsigned iPhone/iPad app.
A manual release publishes the signed Android build and the unsigned iOS app.

## Requirements
- **FR-001** Shared Flutter UI for Android, iPhone and iPad; tabs Home, Library, Settings.
- **FR-002** Text, fonts, colors and layout follow the Figma Splash and Home pages.
- **FR-003** Native engines behind `my_story/compiler` (+ `my_story/compiler_events`) with identical semantics.
- **FR-004** Library data is limited to ids of videos this app created; videos live in the device gallery.
- **FR-005** YouTube uploads use the Data API v3 resumable protocol and the `youtube.upload` scope only.
- **FR-006** No secrets in the repository; OAuth client ids and signing material are supplied by the environment.

## Out of scope
Sharing, in-app trimming or effects, other video platforms (YouTube first), App Store/Play publishing automation.

## Success Criteria
- SC-001 `flutter analyze` is clean and `flutter test` passes.
- SC-002 A mixed photo/video story is created and plays back on an Android emulator.
- SC-003 CI produces an Android APK and an unsigned iOS app and runs the Swift engine tests.
