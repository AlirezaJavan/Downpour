package io.github.alirezajavan.downpour.api

public class DownloadRequest private constructor(
    public val url: String,
    public val destination: DownloadDestination,
    public val headers: Map<String, String>,
    public val priority: Priority,
    public val conflictStrategy: ConflictStrategy,
    public val networkType: NetworkType,
    public val maxConnections: Int,
    public val retryPolicy: RetryPolicy,
    public val maxBytesPerSecond: Long,
    public val checksum: Checksum?,
    public val tag: String?,
    public val workerClass: String?,
    public val metadata: Map<String, String>,
    public val mirrors: List<String>,
    public val requiresCharging: Boolean,
    public val requiresBatteryNotLow: Boolean,
    public val requiresStorageNotLow: Boolean,
    public val scheduleStartMinuteOfDay: Int?,
    public val scheduleEndMinuteOfDay: Int?,
    public val scheduledAtMillis: Long?,
) {
    @Deprecated("Use destination instead", ReplaceWith("destination"))
    public val destinationPath: String
        get() = (destination as? DownloadDestination.File)?.path ?: ""

    public class Builder(
        private val url: String,
        private val destination: DownloadDestination,
    ) {
        public constructor(url: String, destinationPath: String) :
            this(url, DownloadDestination.File(destinationPath))

        private val headers = mutableMapOf<String, String>()
        private val metadata = mutableMapOf<String, String>()
        private val mirrors = mutableListOf<String>()
        private var priority: Priority = Priority.NORMAL
        private var conflictStrategy: ConflictStrategy = ConflictStrategy.OVERWRITE
        private var networkType: NetworkType = NetworkType.ANY
        private var maxConnections: Int = DEFAULT_MAX_CONNECTIONS
        private var retryPolicy: RetryPolicy = RetryPolicy()
        private var maxBytesPerSecond: Long = UNLIMITED
        private var checksum: Checksum? = null
        private var tag: String? = null
        private var workerClass: String? = null
        private var requiresCharging: Boolean = false
        private var requiresBatteryNotLow: Boolean = false
        private var requiresStorageNotLow: Boolean = false
        private var scheduleStartMinuteOfDay: Int? = null
        private var scheduleEndMinuteOfDay: Int? = null
        private var scheduledAtMillis: Long? = null

        public fun header(
            name: String,
            value: String,
        ): Builder = apply { headers[name] = value }

        public fun headers(values: Map<String, String>): Builder = apply { headers.putAll(values) }

        public fun priority(priority: Priority): Builder = apply { this.priority = priority }

        public fun conflictStrategy(strategy: ConflictStrategy): Builder = apply { this.conflictStrategy = strategy }

        public fun networkType(networkType: NetworkType): Builder = apply { this.networkType = networkType }

        public fun maxConnections(count: Int): Builder =
            apply {
                require(count in MIN_CONNECTIONS..MAX_CONNECTIONS) {
                    "maxConnections must be within $MIN_CONNECTIONS..$MAX_CONNECTIONS"
                }
                this.maxConnections = count
            }

        public fun retryPolicy(policy: RetryPolicy): Builder = apply { this.retryPolicy = policy }

        public fun maxBytesPerSecond(limit: Long): Builder =
            apply {
                require(limit >= 0) { "maxBytesPerSecond must be >= 0 (0 = unlimited)" }
                this.maxBytesPerSecond = limit
            }

        public fun checksum(checksum: Checksum): Builder = apply { this.checksum = checksum }

        public fun tag(tag: String): Builder = apply { this.tag = tag }

        public fun workerClass(className: String): Builder = apply { this.workerClass = className }

        public fun mirror(url: String): Builder = apply { mirrors.add(url) }

        public fun mirrors(urls: List<String>): Builder = apply { mirrors.addAll(urls) }

        public fun requiresCharging(required: Boolean): Builder = apply { this.requiresCharging = required }

        public fun requiresBatteryNotLow(required: Boolean): Builder = apply { this.requiresBatteryNotLow = required }

        public fun requiresStorageNotLow(required: Boolean): Builder = apply { this.requiresStorageNotLow = required }

        public fun scheduleWindow(
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int,
        ): Builder =
            apply {
                require(startHour in 0..23) { "startHour must be in 0..23" }
                require(startMinute in 0..59) { "startMinute must be in 0..59" }
                require(endHour in 0..23) { "endHour must be in 0..23" }
                require(endMinute in 0..59) { "endMinute must be in 0..59" }
                this.scheduleStartMinuteOfDay = startHour * 60 + startMinute
                this.scheduleEndMinuteOfDay = endHour * 60 + endMinute
            }

        public fun scheduleAt(timestampMillis: Long): Builder = apply { this.scheduledAtMillis = timestampMillis }

        public fun metadata(
            key: String,
            value: String,
        ): Builder = apply { metadata[key] = value }

        public fun build(): DownloadRequest {
            require(url.isNotBlank()) { "url must not be blank" }
            return DownloadRequest(
                url = url,
                destination = destination,
                headers = headers.toMap(),
                priority = priority,
                conflictStrategy = conflictStrategy,
                networkType = networkType,
                maxConnections = maxConnections,
                retryPolicy = retryPolicy,
                maxBytesPerSecond = maxBytesPerSecond,
                checksum = checksum,
                tag = tag,
                workerClass = workerClass,
                metadata = metadata.toMap(),
                mirrors = mirrors.toList(),
                requiresCharging = requiresCharging,
                requiresBatteryNotLow = requiresBatteryNotLow,
                requiresStorageNotLow = requiresStorageNotLow,
                scheduleStartMinuteOfDay = scheduleStartMinuteOfDay,
                scheduleEndMinuteOfDay = scheduleEndMinuteOfDay,
                scheduledAtMillis = scheduledAtMillis,
            )
        }
    }

    public companion object {
        public const val DEFAULT_MAX_CONNECTIONS: Int = 4
        public const val MIN_CONNECTIONS: Int = 1
        public const val MAX_CONNECTIONS: Int = 16
        public const val UNLIMITED: Long = 0L
    }
}

public inline fun downloadRequest(
    url: String,
    destinationPath: String,
    configure: DownloadRequest.Builder.() -> Unit = {},
): DownloadRequest = DownloadRequest.Builder(url, destinationPath).apply(configure).build()

public inline fun downloadRequest(
    url: String,
    destination: DownloadDestination,
    configure: DownloadRequest.Builder.() -> Unit = {},
): DownloadRequest = DownloadRequest.Builder(url, destination).apply(configure).build()
