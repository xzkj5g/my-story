# Media Sequence Compiler

A native Android app that lets you pick videos and photos from your device,
arrange them into a single ordered sequence, choose an output resolution/
aspect ratio/frame rate, and compile everything into one MP4 video saved to
your gallery.

## Features

- **Select** any mix of videos and photos from device storage (system Photo
  Picker on Android 13+, classic storage-backed picker as a fallback on older
  versions).
- **Reorder & remove** items in the selected sequence before compiling, via
  simple up/down/remove controls.
- **Choose output settings**: resolution tier, aspect ratio, and frame rate,
  picked from a fixed set of presets (default: 1080p, 16:9, 30fps).
- **Compile** the sequence into a single output video:
  - Photos are shown for a fixed display duration with synthesized silent
    audio; videos play at their original length.
  - Every segment (video or photo) is resized/letterboxed to the chosen
    output resolution/aspect ratio and resampled to the chosen frame rate.
  - Compilation runs in a foreground service, so it continues if you leave
    the app, and shows live progress plus a completion/failure notification.
  - The finished video is saved to the device's `Movies/VideoCompiler`
    gallery folder via `MediaStore` (no broader-than-necessary storage
    permissions are requested; see `specs/001-video-compilation/research.md`
    §9 for the permission model).

## Project structure

This app was built using a spec-driven workflow (Spec Kit); the full spec,
plan, research, data model, and task breakdown live under
`specs/001-video-compilation/`.

```text
app/src/main/kotlin/com/example/videocompiler/
├── domain/        # Pure Kotlin models & use cases (SourceMediaItem, OutputSettings,
│                  # SelectionSequence, CompileJob, validation)
├── data/          # MediaStore-backed repository for querying/saving media
├── media/compiler/# CompositionPlanner (pure planning logic) + CompileEngine
│                  # (Media3 Transformer-backed encoder)
├── service/       # CompileForegroundService, notification, session store
└── ui/            # Compose screens: selection, sequence (reorder), output
                   # settings, compile progress; MediaCompilerController holds
                   # the shared UI state
```

## Requirements

- Android 8.0 (API 26) or newer.
- Devices without the system Photo Picker (pre-Android 13) will be prompted
  for `READ_EXTERNAL_STORAGE` (and `WRITE_EXTERNAL_STORAGE` on API 26-28
  only, required to save the compiled output pre-scoped-storage).

## Building & testing

From the `sdd-demo/` directory (requires JDK 17, Android SDK, and an
emulator/device for instrumented tests):

```sh
./gradlew detekt                      # static analysis
./gradlew testDebugUnitTest           # unit tests (domain + composition-planning logic)
./gradlew connectedDebugAndroidTest   # instrumented tests (real Media3 encode, on-device)
```

See `specs/001-video-compilation/quickstart.md` for manual end-to-end
validation scenarios.
