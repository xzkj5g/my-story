# Contract: Compile Foreground Service

Boundary between the UI (`ui/compile`) and `service/CompileForegroundService`, which hosts a
running `CompileEngine.compile(...)` call so it survives backgrounding (FR-013, SC-006).

## Start

**Trigger**: UI calls `CompileForegroundService.start(sequence, settings)` when the user
initiates compilation.

**Guarantees**:
- Service is promoted to foreground (with the current Android-recommended media-processing
  foreground service type, research.md §5) before any long-running encode work begins, showing an
  ongoing `Notification` with `progressPercent` from the underlying `CompileJob`.
- Continues running when the app is backgrounded or the screen turns off (Acceptance Scenario 3,
  User Story 1).
- If the hosting process is killed (e.g., app force-closed from Recents), the job is treated as
  `CANCELLED` per `contracts/compile-engine.md`'s `cancel` guarantees — no partial output is left
  (Acceptance Scenario 4, User Story 1).

## Progress / Completion

**Contract**: The service surfaces `CompileJob.status` transitions to the UI (if visible) and
always to the notification:
- `RUNNING` → notification shows `progressPercent`.
- `SUCCEEDED` → notification shows completion; tapping it opens the app to the result; service
  stops itself.
- `FAILED` → notification shows a clear failure message (using `CompileJob.failureReason`);
  service stops itself.
- `CANCELLED` → notification is dismissed; service stops itself.

## Cancel (user-initiated)

**Trigger**: user taps "Cancel" from the notification or in-app UI while a job is `RUNNING`.

**Guarantees**: Same as `CompileEngine.cancel` — no partial output, `status` becomes `CANCELLED`.
