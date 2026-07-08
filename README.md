# Downpour

[![Android CI](https://github.com/alirezajavan/downpour/actions/workflows/android.yml/badge.svg)](https://github.com/alirezajavan/downpour/actions/workflows/android.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.alirezajavan/downpour)](https://central.sonatype.com/artifact/io.github.alirezajavan/downpour)
[![API](https://img.shields.io/badge/API-23%2B-brightgreen.svg)](https://android-arsenal.com/api?level=23)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A production-grade, coroutine-first **download manager** for Android.

---

## Overview

**Downpour** is a Kotlin library that handles the hard parts of downloading files on Android so your app doesn't have to. It manages a persistent queue of downloads, splits large files across multiple connections for speed, survives process death and network changes, and keeps the user informed through an interactive foreground notification — all behind a small, coroutine-first API.

It is built for real-world conditions: flaky networks, metered data, low battery, app restarts, and concurrent downloads of the same file. State lives in a Room database, transfers run inside a foreground service, and every public surface is reactive through Kotlin `Flow`.

Beyond the defaults, Downpour is designed to be **extended**: you can plug in your own filename resolution, logging, completion processing, dynamic auth headers, notification UI, and networking stack without forking the library.

## Why Downpour

- **Reliable by design** — resumable transfers, process-death recovery, and a state machine that guarantees a paused or cancelled download can never keep advancing.
- **Fast** — multi-connection segmented downloading with automatic single/multi selection based on file size and server support.
- **Battery- and data-aware** — gate downloads on network type, charging, battery, and free-storage conditions; everything re-evaluates reactively.
- **Coroutine-first** — `suspend` operations and `Flow` observation throughout; no callbacks unless you want them.
- **Customizable** — pluggable strategies and hooks for filenames, logging, post-processing, auth, and notifications.
- **Drop-in UI** — an optional Jetpack Compose card component for download status and controls.

## Built With

| Concern | Technology |
|---|---|
| Language | Kotlin 2.x, `explicitApi()` strict mode |
| Concurrency | Kotlin Coroutines & `Flow` / `StateFlow` |
| Networking | OkHttp 5 (HTTP range requests, connection reuse) |
| Persistence | Room (KSP) with schema migrations |
| Serialization | Kotlinx Serialization (JSON column converters) |
| Background work | Foreground `Service`, WorkManager recovery, AndroidX Startup |
| Lifecycle | AndroidX Lifecycle Service |
| UI (optional) | Jetpack Compose + Material 3 |
| Build & quality | Gradle Version Catalogs, Spotless/ktlint, Dokka, JUnit 5 + Robolectric + MockK |

## Requirements

- **minSdk 24** (Android 7.0), **compileSdk 37**
- **JDK 17+**

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.alirezajavan:downpour:0.3.0")
}
```

To open downloaded files via `Downpour.getFileUri(...)`, declare a `FileProvider` with the authority `${applicationId}.downpour.fileprovider` in your manifest.

---

## Quick Start

### 1 — Get a manager

```kotlin
val downloadManager = Downpour.getInstance(
    context,
    DownloadManagerConfig(maxConcurrentDownloads = 3),
)
```

### 2 — Enqueue a download

```kotlin
val request = downloadRequest(
    url = "https://example.com/large-file.zip",
    destinationPath = context.getExternalFilesDir(null)!!.absolutePath + "/file.zip",
) {
    priority(Priority.HIGH)
    networkType(NetworkType.UNMETERED)       // WiFi / unmetered only
    conflictStrategy(ConflictStrategy.RENAME)
    tag("backups")
}

val id: String = downloadManager.enqueue(request)
```

### 3 — Observe it

```kotlin
downloadManager.observe(id).collect { item ->
    when (val state = item?.state) {
        is DownloadState.Running   -> println("${state.progress.percent}%  ${state.progress.bytesPerSecond} B/s")
        is DownloadState.Completed -> println("Saved to ${state.destination}")
        is DownloadState.Failed    -> println("Failed: ${state.error.message}")
        else -> Unit
    }
}
```

---

## Core Concepts

- **`DownloadManager`** — the single entry point. Returned by `Downpour.getInstance(context, config)`. Call `Downpour.reconfigure(context, config)` to rebuild the engine in place with a new config (e.g. after a settings change) — in-flight downloads are requeued and resumed by the new engine, no process restart required.
- **`DownloadRequest`** — an immutable description of *what* to download and *how*, built with the `downloadRequest { }` DSL.
- **`DownloadItem`** — a snapshot of a download (id, url, destination, `state`, tag, metadata, timestamps).
- **`DownloadState`** — a sealed type: `Queued`, `Running`, `Paused`, `Completed`, `Failed`, `WaitingForNetwork`, `Cancelled`.

---

## Features

### Lifecycle operations

```kotlin
downloadManager.pause(id)
downloadManager.resume(id)
downloadManager.cancel(id)                 // stops and discards partial data
downloadManager.retry(id)
downloadManager.remove(id, deleteFile = true)

// Bulk variants
downloadManager.pauseAll(); downloadManager.resumeAll(); downloadManager.cancelAll()
```

### Per-download options

Everything is configured through the builder DSL:

```kotlin
downloadRequest(url, destinationPath) {
    header("Authorization", "Bearer $token")
    headers(mapOf("X-Client" to "myapp"))
    priority(Priority.HIGH)
    maxConnections(8)                                       // segmented download
    maxBytesPerSecond(2 * 1024 * 1024)                      // 2 MB/s per-download cap
    conflictStrategy(ConflictStrategy.RENAME)               // OVERWRITE | RENAME | FAIL
    checksum(Checksum(ChecksumAlgorithm.SHA256, expectedHex))
    retryPolicy(RetryPolicy(maxRetries = 5))
    tag("episode-42")
    metadata("title", "My File")

    // Fallback mirrors (tried in rotation across retries)
    mirror("https://cdn2.example.com/file.zip")
    mirrors(listOf("https://cdn3.example.com/file.zip"))

    // Device constraints
    requiresCharging(true)
    requiresBatteryNotLow(true)
    requiresStorageNotLow(true)
}
```

### Multi-connection & throttling

Downpour automatically splits eligible downloads into parallel segments (controlled per request via `maxConnections`, and globally via `DownloadManagerConfig.minSizeForMultiConnection`). Bandwidth can be capped globally (`config.maxBytesPerSecond`) and per download (`maxBytesPerSecond { }`).

### Runtime queue control

```kotlin
val ids = downloadManager.enqueueAll(listOf(req1, req2, req3))

downloadManager.setPriority(ids[2], Priority.HIGH)   // reorder a queued item
downloadManager.moveToFront(ids[2])                  // jump ahead of everything queued
```

### Tags & group progress

```kotlin
downloadManager.pauseByTag("backups")
downloadManager.resumeByTag("backups")
downloadManager.cancelByTag("backups")
downloadManager.removeByTag("backups", deleteFiles = true)

downloadManager.observeByTag("backups").collect { items -> /* list */ }

downloadManager.observeGroupProgress("backups").collect { g ->
    println("${g.completed}/${g.total} • ${(g.fraction * 100).toInt()}%")
}
```

### Network & device constraints

`NetworkType` (`ANY`, `UNMETERED`, `NOT_ROAMING`) plus per-request `requiresCharging` / `requiresBatteryNotLow` / `requiresStorageNotLow`. A running download that loses its required network moves to `WaitingForNetwork`; a constrained download starts automatically the moment conditions are met — both are re-evaluated reactively.

### Fallback mirrors

Provide alternate URLs with `mirror(...)` / `mirrors(...)`. On each retry, Downpour rotates to the next source, so a failing CDN edge fails over without losing already-downloaded bytes.

### Integrity verification

```kotlin
downloadRequest(url, path) {
    checksum(Checksum(ChecksumAlgorithm.SHA256, "9f86d08...")) // MD5 | SHA1 | SHA256
}
```
The file is streamed through the digest on completion; a mismatch fails the download with `DownloadError.ContentValidation`.

### Extension hooks

**Post-processing** — run work after every completion (unzip, decrypt, move to MediaStore):

```kotlin
DownloadManagerConfig(
    postProcessors = listOf(
        DownloadPostProcessor { item -> unzip(item.destination) },
    ),
)
```

**Dynamic headers / auth** — fresh headers on every request *and* retry (expiring tokens, resumes):

```kotlin
DownloadManagerConfig(
    headerProvider = HeaderProvider { url -> mapOf("Authorization" to "Bearer ${tokens.current()}") },
)
```

**State listeners** — one callback per lifecycle transition, no flow collection required:

```kotlin
downloadManager.addListener(DownloadListener { item ->
    if (item.state is DownloadState.Completed) onFinished(item)
})
```

### Pluggable strategies

```kotlin
DownloadManagerConfig(
    filenameResolver = FilenameResolver { meta ->                 // name files from server metadata
        sanitize(meta.contentDisposition ?: meta.url.substringAfterLast('/'))
    },
    logger = object : DownloadLogger {                            // route logs to Timber/crash reporting
        override fun log(level: LogLevel, message: String, throwable: Throwable?) =
            Timber.log(level.toPriority(), throwable, message)
    },
    ioDispatcher = Dispatchers.IO,                                // inject for tests
)
```

### Notifications

A foreground-service notification shows aggregate progress with Pause/Resume/Cancel actions out of the box. Customize it:

```kotlin
DownloadManagerConfig(
    notification = NotificationConfig(
        smallIconRes = R.drawable.ic_download,
        showSpeed = true,
        showCompletionNotification = true,                       // needs POST_NOTIFICATIONS on API 33+
        contentIntentProvider = ContentIntentProvider { _ ->
            PendingIntent.getActivity(context, 0, openAppIntent, FLAG_IMMUTABLE)
        },
        customizer = NotificationCustomizer { builder, items ->
            builder.setSubText("${items.size} active")
        },
    ),
)
```

### Networking

```kotlin
DownloadManagerConfig(
    okHttpClient = myOkHttpClient,                               // share interceptors/cache/TLS
    proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("10.0.0.1", 8080)),
    cookieJar = myCookieJar,
    connectTimeout = 30.seconds,
    readTimeout = 30.seconds,
    preferIpv4 = true,
)
```

### Auto-cleanup

```kotlin
DownloadManagerConfig(expireCompletedAfter = 7.days)             // prune old completed records
```

### Diagnostics

```kotlin
val report: DiagnosticReport? = downloadManager.getDiagnosticReport(id)
// id, url, state, retryCount, lastError, isResumeSupported,
// totalBytes, downloadedBytes, etag, lastModified, timestamps
```

### Jetpack Compose

An optional ready-made component renders status, progress, and controls:

```kotlin
val scope = rememberCoroutineScope()

DownloadItemCard(
    item = item,
    onPause  = { id -> scope.launch { downloadManager.pause(id) } },
    onResume = { id -> scope.launch { downloadManager.resume(id) } },
    onCancel = { id -> scope.launch { downloadManager.cancel(id) } },
    onRemove = { id -> scope.launch { downloadManager.remove(id) } },
)
```

---

## Configuration Reference

`DownloadManagerConfig` (all parameters optional):

| Parameter | Default | Description |
|---|---|---|
| `maxConcurrentDownloads` | `3` | Active downloads run in parallel. |
| `defaultRetryPolicy` | `RetryPolicy()` | Backoff applied when a request omits its own. |
| `progressUpdateInterval` | `500ms` | How often progress is persisted/emitted. |
| `connectTimeout` / `readTimeout` | `30s` | OkHttp timeouts. |
| `minSizeForMultiConnection` | `5 MB` | Threshold above which multi-connection is used. |
| `maxBytesPerSecond` | unlimited | Global bandwidth cap. |
| `okHttpClient` | new client | Bring your own OkHttp instance. |
| `proxy` / `cookieJar` | `null` | Proxy and cookie support. |
| `interceptors` | empty | Mutate requests before enqueue. |
| `postProcessors` | empty | Run after each completion. |
| `headerProvider` | `null` | Dynamic per-request headers. |
| `listeners` | empty | State-change callbacks. |
| `filenameResolver` | built-in | Resolve filenames from server metadata. |
| `logger` | Android Log | Route library logs. |
| `ioDispatcher` | `Dispatchers.IO` | IO coroutine dispatcher. |
| `notification` | `NotificationConfig()` | Foreground notification settings. |
| `expireCompletedAfter` | `null` | Auto-remove old completed records. |
| `verbose` | `false` | Enable debug logging. |
| `preferIpv4` | `false` | Prefer IPv4 DNS results. |

---

## How It Works

```
DownloadManager  →  DownloadEngine (scheduler + state machine)
                        │
        ┌───────────────┼────────────────────┐
        ▼               ▼                    ▼
   DownloadTask    Room database      Foreground Service
 (segmented HTTP)  (durable state)    (notification + DATA_SYNC)
```

The engine serializes every state transition under a single lock, so user actions (pause/cancel) and the scheduler can never interleave incorrectly. Progress writes are gated on the `RUNNING` state, guaranteeing a stopped download can't keep advancing. On process restart, leaked `RUNNING` rows are recovered back to the queue.

## Notes & Trade-offs

- **Checksums** are verified once on completion by streaming the file through the digest in 64 KB chunks. A live streaming hash is intentionally avoided because it cannot be computed correctly for multi-connection (out-of-order) or resumed (mid-file) transfers without persisting intermediate digest state.
- **Storage** uses the built-in scoped-storage/`Uri` backend. Random-access writes underpin resume and multi-connection correctness, so storage is not a consumer-pluggable extension point.

## Contributing

1. **Fork** and **clone** the repository.
2. **Create a branch** for your feature or fix.
3. **Style** — official Kotlin style (`kotlin.code.style=official`); `explicitApi()` is enforced. Format with `./gradlew spotlessApply`.
4. **Test** — `./gradlew test`.
5. **Open a PR** with a clear description.

## License

Downpour is available under the Apache License 2.0. See [LICENSE](LICENSE) for details.

Copyright © 2026 Alireza Javan
