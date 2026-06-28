package io.github.alirezajavan.downpour.api

public interface DownloadLogger {
    public fun log(
        level: LogLevel,
        message: String,
        throwable: Throwable?,
    )
}

public enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}
