package io.github.alirezajavan10.downpour.internal.engine

import io.github.alirezajavan10.downpour.api.DownloadError

internal sealed interface TaskResult {
    data class Completed(
        val totalBytes: Long,
    ) : TaskResult

    data class Failed(
        val error: DownloadError,
    ) : TaskResult
}
