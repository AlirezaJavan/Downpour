package io.github.alirezajavan.downpour.internal.network

import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Protocol
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Remembers, for the lifetime of the process, whether IPv6 connectivity has proven broken.
 *
 * Some networks advertise IPv6 addresses via DNS that then black-hole (no ICMP/RST, just a
 * connect timeout), which defeats OkHttp's `fastFallback` racing since the losing attempt still
 * has to time out on its own. Rather than pay that timeout on every request, we pay it once: the
 * first observed IPv6 connect failure trips this flag, and every request after that skips IPv6
 * entirely via [AdaptiveDns].
 */
internal class Ipv6HealthTracker(
    startTripped: Boolean = false,
) {
    private val tripped = AtomicBoolean(startTripped)

    val isIpv6Broken: Boolean get() = tripped.get()

    fun reportIpv6Failure() {
        tripped.set(true)
    }
}

/**
 * Trips [tracker] the first time OkHttp reports a failed connect attempt to an IPv6 address.
 */
internal class Ipv6FailureListener(
    private val tracker: Ipv6HealthTracker,
) : EventListener() {
    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        if (inetSocketAddress.address is Inet6Address) {
            tracker.reportIpv6Failure()
        }
    }
}

/**
 * Dual-stack DNS by default; once [tracker] has observed an IPv6 failure, filters to IPv4-only
 * for every subsequent lookup so later requests don't retry a route already known to be broken.
 */
internal class AdaptiveDns(
    private val tracker: Ipv6HealthTracker,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (!tracker.isIpv6Broken) return addresses
        return addresses.filter { it is Inet4Address }.ifEmpty { addresses }
    }
}
