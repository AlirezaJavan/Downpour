package io.github.alirezajavan.downpour.sample.core

import android.content.Context
import androidx.core.content.edit

/** Persisted [DownloadManagerConfig]-shaping knobs the Settings screen lets a user tweak. */
data class SampleSettings(
    val maxConcurrentDownloads: Int = 3,
    val globalBandwidthCapMbps: Int = 0,
    val adaptiveConcurrency: Boolean = false,
    val minConnections: Int = 1,
    val reevaluationIntervalSeconds: Int = 5,
    val verboseLogging: Boolean = true,
    val preferIpv4: Boolean = false,
)

/**
 * The Settings screen's "Apply" action pushes these values into a live rebuild of the shared
 * [io.github.alirezajavan.downpour.api.DownloadManager] via [SampleDownpour.applySettings] and
 * [Downpour.reconfigure] -- no process restart needed.
 */
class SampleSettingsStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SampleSettings =
        SampleSettings(
            maxConcurrentDownloads = prefs.getInt(KEY_MAX_CONCURRENT, 3),
            globalBandwidthCapMbps = prefs.getInt(KEY_BANDWIDTH_CAP, 0),
            adaptiveConcurrency = prefs.getBoolean(KEY_ADAPTIVE, false),
            minConnections = prefs.getInt(KEY_MIN_CONNECTIONS, 1),
            reevaluationIntervalSeconds = prefs.getInt(KEY_REEVAL, 5),
            verboseLogging = prefs.getBoolean(KEY_VERBOSE, true),
            preferIpv4 = prefs.getBoolean(KEY_PREFER_IPV4, false),
        )

    fun save(settings: SampleSettings) {
        // commit() (not apply()) so the write lands on disk synchronously before the engine
        // rebuild that immediately follows reads it back via load().
        prefs.edit(commit = true) {
            putInt(KEY_MAX_CONCURRENT, settings.maxConcurrentDownloads)
            putInt(KEY_BANDWIDTH_CAP, settings.globalBandwidthCapMbps)
            putBoolean(KEY_ADAPTIVE, settings.adaptiveConcurrency)
            putInt(KEY_MIN_CONNECTIONS, settings.minConnections)
            putInt(KEY_REEVAL, settings.reevaluationIntervalSeconds)
            putBoolean(KEY_VERBOSE, settings.verboseLogging)
            putBoolean(KEY_PREFER_IPV4, settings.preferIpv4)
        }
    }

    private companion object {
        const val PREFS_NAME = "downpour_sample_settings"
        const val KEY_MAX_CONCURRENT = "max_concurrent"
        const val KEY_BANDWIDTH_CAP = "bandwidth_cap_mbps"
        const val KEY_ADAPTIVE = "adaptive_concurrency"
        const val KEY_MIN_CONNECTIONS = "min_connections"
        const val KEY_REEVAL = "reeval_seconds"
        const val KEY_VERBOSE = "verbose_logging"
        const val KEY_PREFER_IPV4 = "prefer_ipv4"
    }
}
