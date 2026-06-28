package io.github.alirezajavan.downpour.api

public fun interface HeaderProvider {
    public fun headers(url: String): Map<String, String>
}
