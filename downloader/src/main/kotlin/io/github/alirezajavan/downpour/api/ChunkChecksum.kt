package io.github.alirezajavan.downpour.api

import kotlinx.serialization.Serializable

/**
 * Represents a map of checksums for fixed-size segments of a file.
 * Used for verifying the integrity of individual chunks during or after transfer.
 *
 * @property chunkSize The size of each chunk in bytes.
 * @property algorithm The hashing algorithm used (e.g. SHA-256).
 * @property checksums A map where the key is the start offset of the chunk and the value is the hex-encoded checksum.
 */
@Serializable
public data class ChunkChecksum(
    val chunkSize: Long,
    val algorithm: ChecksumAlgorithm,
    val checksums: Map<Long, String>,
)
