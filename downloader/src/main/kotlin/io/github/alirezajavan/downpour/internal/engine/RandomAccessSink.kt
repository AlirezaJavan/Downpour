package io.github.alirezajavan.downpour.internal.engine

import java.io.Closeable

internal interface RandomAccessSink : Closeable {
    fun seek(position: Long)

    fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    )

    fun length(): Long

    fun setLength(newLength: Long)
}
