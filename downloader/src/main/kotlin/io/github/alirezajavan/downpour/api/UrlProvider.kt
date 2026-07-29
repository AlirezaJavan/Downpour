package io.github.alirezajavan.downpour.api

/**
 * Strategy for providing a fresh URL when a download fails due to authentication
 * or authorization errors (HTTP 401/403).
 */
public fun interface UrlProvider {
    /**
     * Called when a download fails with HTTP 401 Unauthorized or 403 Forbidden.
     * Implementations should fetch a fresh URL (e.g. with new tokens) and return it.
     *
     * @param id The unique ID of the download.
     * @param oldUrl The URL that failed.
     * @return The fresh URL to resume with, or `null` to fail the download permanently.
     */
    public suspend fun getNewUrl(
        id: String,
        oldUrl: String,
    ): String?
}
