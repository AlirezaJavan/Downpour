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
)

/**
 * [Downpour.getInstance] builds its engine once per process and ignores later config changes, so
 * these values only take effect on the next process start. The Settings screen makes this explicit
 * via an "Apply & Restart" action instead of pretending a live rebuild is possible.
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
        )

    fun save(settings: SampleSettings) {
        prefs.edit {
            putInt(KEY_MAX_CONCURRENT, settings.maxConcurrentDownloads)
            putInt(KEY_BANDWIDTH_CAP, settings.globalBandwidthCapMbps)
            putBoolean(KEY_ADAPTIVE, settings.adaptiveConcurrency)
            putInt(KEY_MIN_CONNECTIONS, settings.minConnections)
            putInt(KEY_REEVAL, settings.reevaluationIntervalSeconds)
            putBoolean(KEY_VERBOSE, settings.verboseLogging)
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
    }
}
