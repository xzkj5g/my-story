# Tasks: Cross-platform My Story app

## US1 Home
- [X] T001 Theme, fonts and assets from Figma in `lib/theme/`, `assets/`
- [X] T002 Splash and Home shell with bottom tabs in `lib/screens/splash_screen.dart`, `lib/screens/home_shell.dart`
- [X] T003 Gallery service and paged grid with permission states in `lib/services/photo_manager_gallery_service.dart`, `lib/state/gallery_controller.dart`, `lib/screens/home_screen.dart`
- [X] T004 Selection controller with ordering in `lib/state/selection_controller.dart` (+ `test/selection_controller_test.dart`)

## US2 Create
- [X] T005 Story editor with thumbnail timeline, fps and aspect ratio in `lib/screens/story_editor_screen.dart`
- [X] T006 Channel client and creation controller in `lib/services/platform_compiler_service.dart`, `lib/state/story_creation_controller.dart` (+ tests)
- [X] T007 Android bridge in `android/app/src/main/kotlin/com/mystory/app/bridge/` (+ `StoryRequestTest`)
- [X] T008 iOS engine in `ios/Runner/StoryCompiler/` (+ `ios/RunnerTests/RunnerTests.swift`)

## US3 Library
- [X] T009 Story repository, library controller and viewer in `lib/services/story_repository.dart`, `lib/state/library_controller.dart`, `lib/screens/library_screen.dart`, `lib/screens/story_viewer_screen.dart`

## US4 YouTube
- [X] T010 Auth, resumable uploader and service in `lib/services/youtube/` (+ `test/youtube_uploader_test.dart`)
- [X] T011 Settings screen and upload sheet in `lib/screens/settings_screen.dart`, `lib/screens/story_viewer_screen.dart`
- [X] T012 OAuth setup guide in `docs/youtube-setup.md`

## US5 CI
- [X] T013 `ci.yml` and `release.yml` on macOS in `.github/workflows/`
- [X] T014 Update workflow validators and contract tests in `scripts/ci/`

## Open follow-ups
- [ ] T015 Run the first CI build on GitHub and fix any iOS compile findings (iOS was written without a Mac).
- [ ] T016 Add Google OAuth client ids and test a real YouTube upload.
- [ ] T017 iOS signing, TestFlight and App Store submission.
