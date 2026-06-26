# Downpour

[![Android CI](https://github.com/alirezajavan10/downpour/actions/workflows/android.yml/badge.svg)](https://github.com/alirezajavan10/downpour/actions/workflows/android.yml)

**Downpour** is a production-grade, coroutine-first download manager library for Android. It is designed to be highly reliable, performant, and easy to integrate into modern Android applications.

## Features

- 🚀 **Multi-connection downloads**: Parallel segment downloading for maximum speed.
- ⏸️ **Pause & Resume**: Robust state management for interruptible transfers.
- 🔄 **Auto-retry**: Configurable exponential backoff policies for transient network errors.
- 🛠️ **Storage Management**: 
  - Scoped Storage & MediaStore support via `Uri` destinations.
  - Pre-flight disk space checks.
  - Conflict strategies: Overwrite, Rename (automatic unique naming), or Fail.
- 📡 **Network Intelligence**: 
  - Automated pause/resume based on network type (e.g., Wait for WiFi).
  - Bandwidth throttling (Global and per-download).
- 🔔 **Interactive Notifications**: Built-in foreground service with Pause/Resume/Cancel actions.
- 🧩 **Jetpack Compose**: Native Composable components for download status.
- 🔍 **Diagnostics**: Detailed reporting of HTTP headers, error history, and connection metadata.
- 📦 **Post-processing**: Hook system for unzipping, decrypting, or moving files after completion.

## Installation

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.alirezajavan10:downpour:1.0.0")
}
```

## Quick Start

### 1. Initialize the Manager

```kotlin
val config = DownloadManagerConfig(
    maxConcurrentDownloads = 3,
    maxBytesPerSecond = DownloadManagerConfig.UNLIMITED
)

val downloadManager = Downpour.getInstance(context, config)
```

### 2. Create and Enqueue a Request

```kotlin
val request = downloadRequest(
    url = "https://example.com/large-file.zip",
    destinationPath = context.getExternalFilesDir(null)!!.absolutePath + "/file.zip"
) {
    priority(Priority.HIGH)
    networkType(NetworkType.UNMETERED) // Download only on WiFi
    conflictStrategy(ConflictStrategy.RENAME)
    tag("backups")
}

val downloadId = downloadManager.enqueue(request)
```

### 3. Observe Progress

```kotlin
downloadManager.observe(downloadId).collect { item ->
    when (val state = item?.state) {
        is DownloadState.Running -> {
            println("Progress: ${state.progress.percent}% at ${state.progress.bytesPerSecond} B/s")
        }
        is DownloadState.Completed -> println("Finished: ${state.destination}")
        is DownloadState.Failed -> println("Error: ${state.error.message}")
    }
}
```

## Contributing

We welcome contributions! To get started:

1.  **Fork** the repository.
2.  **Clone** your fork.
3.  **Create a branch** for your feature or fix.
4.  **Format** your code: Run `./gradlew spotlessApply` to ensure consistent style.
5.  **Test** your changes: Ensure all tests pass with `./gradlew test`.
6.  **Submit a Pull Request** with a detailed description of your changes.

### Development Requirements
- JDK 17
- Android SDK 35+
- A Git client with pre-commit hook support (configured automatically).

## License

Downpour is available under the MIT License. See [LICENSE](LICENSE) for details.
