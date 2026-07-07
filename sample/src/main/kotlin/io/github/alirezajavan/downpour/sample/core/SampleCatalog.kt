package io.github.alirezajavan.downpour.sample.core

/** One-tap presets so each library capability can be tried without hand-typing a request. */
object SampleCatalog {
    const val DEFAULT_URL = "https://ash-speed.hetzner.com/100MB.bin"
    const val SMALL_URL = "https://proof.ovh.net/files/10Mb.dat"

    /** A URL guaranteed to 404, paired with a real mirror -- demonstrates fallback-mirror rotation. */
    const val BROKEN_PRIMARY_URL = "https://ash-speed.hetzner.com/this-file-does-not-exist.bin"
    const val WORKING_MIRROR_URL = DEFAULT_URL
}
