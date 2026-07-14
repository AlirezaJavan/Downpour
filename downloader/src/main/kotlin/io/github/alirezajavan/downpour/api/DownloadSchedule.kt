package io.github.alirezajavan.downpour.api

/**
 * Defines when a download is allowed to run.
 *
 * @property scheduleStartMinuteOfDay Local time minute of day (0..1439) when the download can start.
 * @property scheduleEndMinuteOfDay Local time minute of day (0..1439) when the download must pause.
 * @property scheduledAtMillis Absolute UTC timestamp after which the download is allowed to start.
 */
public data class DownloadSchedule(
    val scheduleStartMinuteOfDay: Int? = null,
    val scheduleEndMinuteOfDay: Int? = null,
    val scheduledAtMillis: Long? = null,
) {
    public val hasWindow: Boolean
        get() = scheduleStartMinuteOfDay != null && scheduleEndMinuteOfDay != null

    public val hasDate: Boolean
        get() = scheduledAtMillis != null

    public val isEmpty: Boolean
        get() = !hasWindow && !hasDate
}
