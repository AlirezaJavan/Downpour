package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity

/**
 * Executes a single download attempt for an entity and reports the outcome.
 *
 * Extracted as an interface so the engine's state machine can be driven by a controllable fake in
 * tests (one that simulates progress writes, hangs, succeeds, or fails on demand) without standing
 * up the full HTTP/file stack.
 */
internal fun interface DownloadTaskRunner {
    suspend fun run(entity: DownloadEntity): TaskResult
}
