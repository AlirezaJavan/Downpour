package io.github.alirezajavan10.downpour.api

public enum class ChecksumAlgorithm(
    internal val javaName: String,
) {
    MD5("MD5"),
    SHA1("SHA-1"),
    SHA256("SHA-256"),
}

public data class Checksum(
    val algorithm: ChecksumAlgorithm,
    val expectedHex: String,
) {
    init {
        require(expectedHex.isNotBlank()) { "expectedHex must not be blank" }
    }

    public fun matches(actualHex: String): Boolean = actualHex.equals(expectedHex, ignoreCase = true)
}
