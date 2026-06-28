package io.github.alirezajavan.downpour.internal

import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.DownloadListener
import io.github.alirezajavan.downpour.api.DownloadState
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

internal class DownloadEventDispatcher(
    scope: CoroutineScope,
    repository: DownloadRepository,
    initialListeners: List<DownloadListener>,
) {
    private val listeners = CopyOnWriteArraySet(initialListeners)
    private val lastPhase = ConcurrentHashMap<String, String>()

    init {
        scope.launch {
            repository.observeAllItems().collect { items -> dispatch(items) }
        }
    }

    fun add(listener: DownloadListener) {
        listeners.add(listener)
    }

    fun remove(listener: DownloadListener) {
        listeners.remove(listener)
    }

    // Fire once per lifecycle transition (Queued -> Running -> Completed/Failed/...), not on the
    // progress ticks within Running, so listeners get clean state-change events rather than a stream.
    private fun dispatch(items: List<DownloadItem>) {
        items.forEach { item ->
            val phase = item.state.phase()
            if (lastPhase.put(item.id, phase) != phase) {
                listeners.forEach { it.onStateChanged(item) }
            }
        }
    }

    private fun DownloadState.phase(): String = this::class.simpleName ?: toString()
}
