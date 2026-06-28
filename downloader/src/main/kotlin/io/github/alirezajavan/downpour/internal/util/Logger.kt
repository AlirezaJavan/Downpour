package io.github.alirezajavan.downpour.internal.util

import android.util.Log
import io.github.alirezajavan.downpour.api.DownloadLogger
import io.github.alirezajavan.downpour.api.LogLevel

internal interface Logger {
    fun d(message: String)

    fun i(message: String)

    fun w(
        message: String,
        throwable: Throwable? = null,
    )

    fun e(
        message: String,
        throwable: Throwable? = null,
    )
}

internal class AndroidLogger(
    private val enabled: Boolean,
) : Logger {
    override fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    override fun i(message: String) {
        if (enabled) Log.i(TAG, message)
    }

    override fun w(
        message: String,
        throwable: Throwable?,
    ) {
        if (enabled) Log.w(TAG, message, throwable)
    }

    override fun e(
        message: String,
        throwable: Throwable?,
    ) {
        if (enabled) Log.e(TAG, message, throwable)
    }

    private companion object {
        const val TAG = "Downpour"
    }
}

internal class DownloadLoggerAdapter(
    private val delegate: DownloadLogger,
) : Logger {
    override fun d(message: String) = delegate.log(LogLevel.DEBUG, message, null)

    override fun i(message: String) = delegate.log(LogLevel.INFO, message, null)

    override fun w(
        message: String,
        throwable: Throwable?,
    ) = delegate.log(LogLevel.WARN, message, throwable)

    override fun e(
        message: String,
        throwable: Throwable?,
    ) = delegate.log(LogLevel.ERROR, message, throwable)
}

internal object NoOpLogger : Logger {
    override fun d(message: String) {}

    override fun i(message: String) {}

    override fun w(
        message: String,
        throwable: Throwable?,
    ) {}

    override fun e(
        message: String,
        throwable: Throwable?,
    ) {}
}
