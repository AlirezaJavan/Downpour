package io.github.alirezajavan.downpour.sample.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fire-and-forget event bus so background hooks (post-processors, listeners) that don't run on the
 * main thread and don't own a Composable can still surface a Snackbar.
 */
object SampleEvents {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun emit(message: String) {
        _messages.tryEmit(message)
    }
}
