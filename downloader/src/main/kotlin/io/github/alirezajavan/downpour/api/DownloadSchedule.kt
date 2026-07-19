package io.github.alirezajavan.downpour.api

/**
 * Defines the specific time range when a download is allowed to run.
 *
 * @property startTimeMillis Absolute UTC timestamp after which the download is allowed to start.
 * @property endTimeMillis Absolute UTC timestamp after which the download must stop.
 */
public data class DownloadSchedule(
    val startTimeMillis: Long? = null,
    val endTimeMillis: Long? = null,
) {
    public val hasStart: Boolean
        get() = startTimeMillis != null

    public val hasEnd: Boolean
        get() = endTimeMillis != null

    public val isEmpty: Boolean
        get() = !hasStart && !hasEnd
}
