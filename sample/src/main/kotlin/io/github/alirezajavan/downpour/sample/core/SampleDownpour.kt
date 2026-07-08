package io.github.alirezajavan.downpour.sample.core

import android.content.Context
import io.github.alirezajavan.downpour.api.DownloadListener
import io.github.alirezajavan.downpour.api.DownloadManager
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.DownloadPostProcessor
import io.github.alirezajavan.downpour.api.DownloadState
import io.github.alirezajavan.downpour.api.Downpour
import io.github.alirezajavan.downpour.api.HeaderProvider
import kotlin.time.Duration.Companion.seconds

/**
 * Single shared [DownloadManager] for the whole sample app, built from the persisted
 * [SampleSettings] and wired with a visible example of every Phase 4 extension hook so they're
 * exercised just by using the app rather than only from unit tests. Rebuilt in place (no process
 * restart) whenever [applySettings] is called.
 */
object SampleDownpour {
    @Volatile
    private var manager: DownloadManager? = null

    fun getInstance(context: Context): DownloadManager =
        manager ?: synchronized(this) {
            manager ?: buildManager(context, SampleSettingsStore(context).load()).also { manager = it }
        }

    /**
     * Rebuilds the manager from [settings] in place -- callers that call [getInstance] again (e.g.
     * a screen recreated by navigating away and back) see the new one immediately, no process
     * restart required. Screens already holding the old reference keep using the shut-down engine
     * until they re-fetch.
     */
    fun applySettings(
        context: Context,
        settings: SampleSettings,
    ): DownloadManager =
        synchronized(this) {
            buildManager(context, settings, reconfigure = true).also { manager = it }
        }

    private fun buildManager(
        context: Context,
        settings: SampleSettings,
        reconfigure: Boolean = false,
    ): DownloadManager {
        val config =
            DownloadManagerConfig(
                maxConcurrentDownloads = settings.maxConcurrentDownloads,
                maxBytesPerSecond = settings.globalBandwidthCapMbps.toLong() * BYTES_PER_MB,
                adaptiveConcurrency = settings.adaptiveConcurrency,
                minConnections = settings.minConnections,
                concurrencyReevaluationInterval = settings.reevaluationIntervalSeconds.seconds,
                verbose = settings.verboseLogging,
                preferIpv4 = settings.preferIpv4,
                // Dynamic per-request auth headers, recomputed on every retry.
                headerProvider = HeaderProvider { url -> mapOf("X-Sample-Token" to "demo-${url.hashCode().toUInt()}") },
                // Post-processing hook -- runs after every completion.
                postProcessors =
                    listOf(
                        DownloadPostProcessor { item -> SampleEvents.emit("Post-processed • ${item.displayName()}") },
                    ),
                // State-change listener -- fires without needing to collect a Flow.
                listeners =
                    listOf(
                        DownloadListener { item ->
                            if (item.state is DownloadState.Completed) {
                                SampleEvents.emit("Completed • ${item.displayName()}")
                            }
                        },
                    ),
            )
        return if (reconfigure) Downpour.reconfigure(context, config) else Downpour.getInstance(context, config)
    }

    private const val BYTES_PER_MB = 1024L * 1024L
}
