# Downpour Download Manager Master Plan

This roadmap outlines the evolution of the **Downpour** library from a core engine to a production-grade, feature-rich Android download manager.

---

## Phase 1: Storage & Reliability (Foundation)
*Focus: Prevent "silent" failures and ensure modern Android compatibility.*

*   **[x] Disk Space Pre-flight**: Added check in `DownloadTask` to verify available bytes.
*   **[x] File Conflict Policies**: `ConflictStrategy` (`Overwrite`, `Rename`, `Fail`) added to `DownloadRequest` and handled.
*   **[x] Scoped Storage Bridge**: `DownloadDestination` (File vs Uri) implemented to support `MediaStore` and `ContentResolver`.
*   **[x] Sample app**: `ConflictStrategy` is a segmented-button toggle in the New Download sheet (Overwrite/Rename/Fail) — re-enqueuing the same URL exercises a real collision. **Remaining**: a `DownloadDestination.Uri`/MediaStore example isn't wired up yet (form only builds `File` destinations). Disk-space pre-flight has no separate UI surface — skip it.

## Phase 2: Network Intelligence & Performance
*Focus: Efficiency and data-saving features.*

*   **[x] Metadata Probing**: Refactored engine to use `HEAD` requests and parse content metadata.
*   **[x] Metered Network Handling**: Added `WAITING_FOR_NETWORK` state and automated pause/resume based on `NetworkType` constraints.
*   **[x] Global Rate Limiting**: Throttling mechanism implemented and configurable.
*   **[x] Sample app**: `NetworkType` is a segmented-button toggle in the New Download sheet, and the global bandwidth cap is a Settings slider. **Remaining**: no per-download `maxBytesPerSecond` field in the form yet (global cap only).

## Phase 3: Enhanced UX & Notifications
*Focus: Deep integration with the Android System.*

*   **[x] Interactive Notifications**: Added Pause, Resume, and Cancel buttons to notifications.
*   **[x] MIME Type Detection**: `FilenameResolver` resolves extensions from `Content-Type` and `Content-Disposition`.
*   **[x] FileProvider Integration**: Standard `Downpour.getFileUri` provided for opening files securely.
*   **[x] Sample app**: Downloads screen shows an "Open" action on completed items using `Downpour.getFileUri(...)`. Interactive notifications and MIME detection are already observable via the real notification tray/filename once any download runs.

## Phase 4: Developer Experience (DX) & Extensibility
*Focus: Making the library a joy to use.*

*   **[x] Request Interceptors**: `DownloadInterceptor` interface for global header injection and logging.
*   **[x] Post-Processing Workers**: `DownloadWorker` system for tasks like unzipping or decryption after completion.
*   **[x] Jetpack Compose Artifact**: Added `DownloadItemCard` and Compose dependencies.
*   **[x] Sample app**: `SampleDownpour` wires a `HeaderProvider` (fake token), a `DownloadPostProcessor`, and a `DownloadListener` — each surfaces a Snackbar via a small event bus so the hooks are visibly exercised, not just present in config. The Downloads screen uses the real `DownloadItemCard`.

## Phase 5: Management & Advanced Control
*Focus: Professional-grade management features.*

*   **[x] Tag-based Batching**: Added `pauseByTag`, `cancelByTag`, and `removeByTag` for grouped management.
*   **[x] Diagnostic Export**: `getDiagnosticReport(id)` returns detailed logs, error history, and connection metadata.
*   **[x] Dynamic Concurrency**: `DownloadPlanner` supports dynamic connection count; `DownloadTask` placeholder implemented.
*   **[x] Sample app**: added the Tags & Groups screen (`pauseByTag`/`resumeByTag`/`cancelByTag`/`removeByTag`, `observeByTag`, `observeGroupProgress`) and a Diagnostics screen (`getDiagnosticReport(id)` per item, refreshable). Dynamic concurrency has no standalone UI — it's covered by Phase 6's own sample step below.

---

## Phase 6: Adaptive Performance

*Focus: Make multi-connection downloads actually adapt to the network instead of using a fixed connection count.*

### 6.1 Dynamic concurrency tuning

*   **[x] Where**: `internal/engine/DownloadPlanner.kt`, `internal/engine/DownloadTask.kt`, `internal/engine/DownloadPlan.kt`, `internal/engine/SpeedMeter.kt` (reuse), new `internal/engine/ConnectionTuner.kt`.
*   **[x] What**: `DownloadPlanner.plan()` currently takes a static `activeConnections` (falls back to `entity.maxConnections`). Add a feedback loop:
    1. Track per-part throughput in `DownloadTaskRunner`/`PartDownloader` (bytes/sec per `PartPlan.index`), reusing the existing `SpeedMeter`.
    2. Every N seconds (config, e.g. `DownloadManagerConfig.concurrencyReevaluationInterval`, default 5s), compute aggregate throughput and compare against the throughput at the last connection-count change.
    3. If adding a connection previously increased aggregate speed roughly linearly, allow `DownloadEngine` to request a re-plan with `activeConnections + 1` (up to `entity.maxConnections` and the existing `MAX_PARTS = 16` cap in `DownloadPlanner`); if throughput has plateaued or a new connection stalls (server throttling per-IP), hold or reduce.
    4. Persist the current effective connection count per download so recovery after process death resumes with the last known-good count rather than always restarting from `entity.maxConnections`.
*   **[x] Constraints to respect**: don't fight the existing single-lock state machine in `DownloadEngine` — re-planning must go through the same transition path pause/resume already uses, not a side-channel connection change while `RUNNING`.
*   **[x] Config additions**: `DownloadManagerConfig.adaptiveConcurrency: Boolean = false` (opt-in, off by default so existing behavior is unchanged unless requested), `minConnections`, `concurrencyReevaluationInterval`.
*   **[x] Tests**: extend `DownloadPlannerTest.kt` and `DownloadEngineTest.kt` with a fake clock/speed source to verify scale-up, scale-down, and plateau behavior deterministically (no real network timing in unit tests).
*   **[x] Acceptance**: a download on a simulated variable-bandwidth `HttpDownloadDataSource` fake converges to a stable connection count within the reevaluation window and never exceeds `maxConnections` or drops below `minConnections`.
*   **[x] Sample app**: added an `adaptiveConcurrency` toggle plus `minConnections`/re-evaluation-interval sliders to the Settings screen. Since `Downpour.getInstance` is a process-wide singleton that ignores later config changes, "Apply & restart" persists the settings and relaunches the process rather than pretending a live rebuild is possible. **Remaining**: no per-item live effective-connection-count readout (would need a new field on `DiagnosticReport`/`DownloadItem`, out of scope for a sample-app-only change).

---

## Phase 7: Scheduling & Automation

*Focus: Let downloads run on a time window, not just a network/battery condition.*

### 7.1 Time-window scheduling

*   **Where**: `api/DownloadRequest.kt` (new builder option), `internal/device/DeviceStateMonitor.kt` (add a clock-based condition alongside existing network/battery/storage checks), `internal/engine/DownloadEngine.kt` (gate scheduling decisions).
*   **What**: Add `scheduleWindow(startHour, startMinute, endHour, endMinute)` (or a `LocalTime` range) to the request DSL, e.g. `scheduleWindow(2, 0, 6, 0)` meaning "only run between 2am–6am local time." Downloads outside the window enter (or add) a new `DownloadState.Scheduled` sub-state — reuse `WaitingForNetwork`'s reactive re-evaluation pattern rather than polling: register a `AlarmManager`/`WorkManager` one-shot trigger for the window start, and re-evaluate on each `DeviceStateMonitor` tick already in place.
*   **Persistence**: add `scheduleStartMinuteOfDay` / `scheduleEndMinuteOfDay` (nullable Int) columns to `DownloadEntity` with a Room migration (follow the pattern in `DownloadDatabaseMigrationTest.kt`).
*   **Interaction with existing constraints**: schedule window is ANDed with `NetworkType`/`requiresCharging`/etc — all must be satisfied simultaneously, consistent with how those are combined today.
*   **Tests**: `DownloadEngineStateTest.kt` — inject a fake clock, verify a download outside its window stays `Scheduled` and transitions to `Queued`/`Running` exactly at window start; verify a download already `Running` when its window closes pauses gracefully (same path as a lost network constraint).
*   **Acceptance**: `README.md` "Network & device constraints" section gets a matching example.
*   **[ ] Sample app**: add a time-window picker to the Constraints screen (Phase 11) so a request can be built with `scheduleWindow(...)`, and show the `Scheduled` state distinctly in the Downloads list.

---

## Phase 8: Data Portability & Dedup

*Focus: Queue survives more than process death — it survives reinstall, and doesn't double-download.*

### 8.1 Queue export/import

*   **Where**: new `api/QueueSnapshot.kt` (Kotlinx Serialization data class), new methods on `DownloadManager` interface + `DefaultDownloadManager`: `exportQueue(): String` (JSON) and `importQueue(json: String, conflictStrategy: ConflictStrategy)`.
*   **What**: Serialize pending/paused/failed `DownloadItem`s (not `Completed`/`Cancelled` by default, configurable) into a JSON snapshot: url, destination, headers, priority, tags, metadata, checksum, mirrors, constraints. Exclude live progress/state — imported items re-enter as `Queued` and re-probe the server (reuse the existing metadata-probing path), since byte offsets from another install/device are not trustworthy.
*   **Tests**: round-trip test in `DownpourTest.kt` — export, wipe DB, import, assert requests reconstruct equivalently (excluding transient state).

### 8.2 Duplicate detection

*   **Where**: `internal/DefaultDownloadManager.kt` (`enqueue`), `internal/data/DownloadRepository.kt` (add a lookup by URL+destination or checksum).
*   **What**: Before creating a new `DownloadEntity`, check for an existing non-terminal (`Queued`/`Running`/`Paused`/`WaitingForNetwork`) entity with the same URL *and* destination path. Default behavior: return the existing id instead of enqueuing a duplicate (configurable via a new `DuplicatePolicy` enum: `REUSE_EXISTING` (default) | `ALLOW_DUPLICATE`, set globally in `DownloadManagerConfig` or per-request).
*   **Tests**: `DefaultDownloadManagerTest.kt` — enqueue the same request twice, assert single entity id returned under `REUSE_EXISTING`; assert two ids under `ALLOW_DUPLICATE`.
*   **[ ] Sample app**: add Export/Import buttons (Settings screen, Phase 11) that call `exportQueue()`/`importQueue(...)` against a file in app-private storage, and enqueue the same URL twice from the UI to demonstrate `DuplicatePolicy.REUSE_EXISTING` returning the same id.

---

## Phase 9: Quality & Tooling

*Focus: Catch regressions the current unit-test suite can't reach.*

*   **[ ] Detekt**: add `io.gitlab.arturbosch.detekt` alongside existing Spotless/ktlint in `build.gradle.kts`, with a baseline generated from current code so it doesn't block on pre-existing style. Wire into the same CI workflow as `android.yml`.
*   **[ ] Instrumented tests**: add `downloader/src/androidTest` covering `DownloadService` foreground behavior and notification action `PendingIntent`s (Pause/Resume/Cancel actually reaching `DownloadManager`), which Robolectric can't fully exercise. Use `androidx.test.uiautomator` or `ServiceTestRule`.
*   **[ ] CI**: publish snapshot builds (`-SNAPSHOT` version) to a snapshot repo on every merge to `master`, separate from the tagged Maven Central release flow.
*   **[ ] Sample app**: not needed — this phase is internal tooling/CI with no library-facing API surface to showcase.

---

## Phase 10: Advanced Diagnostics

*Focus: Surface what `getDiagnosticReport` already collects.*

*   **Where**: new `compose/DiagnosticsScreen.kt` alongside the existing `compose/DownloadItemCard.kt`.
*   **What**: A Compose screen listing retry history, last error, resume-support flag, and connection-level part progress (start/end/downloaded per `PartPlan`, surfaced through a new field on `DiagnosticReport` if not already exposed — check `api/DiagnosticReport.kt` first, extend rather than duplicate).
*   **Tests**: `DiagnosticReportTest.kt` extended for any new fields; Compose screen gets a Paparazzi/Compose UI test if the project already has that infra (check before adding a new test framework).
*   **[ ] Sample app**: this phase's `DiagnosticsScreen` *is* the sample-app deliverable — wire it into the Diagnostics destination added in Phase 5's sample step, replacing the plain `getDiagnosticReport` text dump.

---

## Phase 11: Sample App Showcase

*Focus: the `sample` module currently only has `DownloadsScreen.kt` / `DownloadsViewModel.kt` / `MainActivity.kt` — one screen exercising basic enqueue/observe. Every public capability the library ships should be demonstrable from the sample app, so it doubles as living documentation and a manual smoke-test surface for each phase above.*

*   **Navigation shell**: Add a bottom navigation bar (Compose `NavigationBar` + `NavHost`) in `MainActivity.kt` so each feature area gets its own screen instead of overloading `DownloadsScreen`. Suggested destinations:
    *   **Downloads** (existing `DownloadsScreen`) — enqueue, observe, pause/resume/cancel/retry/remove, priority reordering (`setPriority`, `moveToFront`).
    *   **Tags & Groups** — `pauseByTag`/`resumeByTag`/`cancelByTag`/`removeByTag`, `observeByTag`, `observeGroupProgress`.
    *   **Constraints** — a request-builder form exposing `NetworkType`, `requiresCharging`/`requiresBatteryNotLow`/`requiresStorageNotLow`, `maxBytesPerSecond`, `maxConnections`, mirrors, checksum — so every `DownloadRequest` DSL option is reachable from UI, not just hardcoded in sample code.
    *   **Diagnostics** — per-item `getDiagnosticReport(id)` (and the Phase 10 `DiagnosticsScreen` once built).
    *   **Settings** — a `DownloadManagerConfig` picker (global bandwidth cap, `maxConcurrentDownloads`, `adaptiveConcurrency` toggle from Phase 6, notification customization) that rebuilds the `Downpour` instance, since config is set once at `getInstance(...)`.
*   **Coverage checklist** (each must have a working, tappable path in the sample app, not just be called from a unit test):
    *   Lifecycle ops: pause/resume/cancel/retry/remove + bulk variants.
    *   Conflict strategies (`OVERWRITE`/`RENAME`/`FAIL`) — a UI toggle plus a way to trigger a real name collision (re-enqueue same destination).
    *   Checksum verification — one sample download with a known-good hash, one with a deliberately wrong hash to show `DownloadError.ContentValidation`.
    *   Fallback mirrors — a sample request with a deliberately broken primary URL and a working mirror, to show failover visibly.
    *   Post-processors, header providers, listeners, filename resolver, logger — wire at least one visible example of each into the sample's `DownloadManagerConfig` (e.g. a post-processor that shows a toast, a header provider reading a fake token).
    *   Compose `DownloadItemCard` — used in the Downloads screen instead of a hand-rolled row, so the drop-in component itself gets exercised.
    *   `Downpour.getFileUri(...)` — an "Open" action on completed items using the `FileProvider`.
*   **Tests**: sample app is a demo, not a library surface — no new unit-test obligations — but each new screen should get a manual pass noted in the PR description (this repo's `verify` skill / manual smoke test) since Compose UI here isn't under CI.
