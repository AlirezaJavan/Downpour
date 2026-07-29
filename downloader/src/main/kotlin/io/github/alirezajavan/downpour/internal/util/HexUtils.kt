package io.github.alirezajavan.downpour.internal.util

internal object HexUtils {
    private const val HEX_BYTE_MASK = 0xFF
    private const val HEX_BASE = 16
    private const val HEX_WIDTH = 2

    fun ByteArray.toHex(): String =
        joinToString("") { byte ->
            (byte.toInt() and HEX_BYTE_MASK).toString(HEX_BASE).padStart(HEX_WIDTH, '0')
        }
}
