package io.github.alirezajavan.downpour.internal.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.NetworkType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
class NetworkMonitorTest {
    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var shadowConnectivityManager: ShadowConnectivityManager
    private lateinit var networkMonitor: NetworkMonitor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowConnectivityManager = shadowOf(connectivityManager)
        networkMonitor = NetworkMonitor(context)
    }

    @Test
    fun `snapshot returns correct status when connected`() {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)

        shadowConnectivityManager.setNetworkCapabilities(connectivityManager.activeNetwork, capabilities)

        val status = networkMonitor.snapshot()
        assertThat(status.isConnected).isTrue()
        assertThat(status.isMetered).isFalse()
        assertThat(status.isNotRoaming).isTrue()
    }

    @Test
    fun `status satisfies network types correctly`() {
        val status = NetworkStatus(isConnected = true, isMetered = false, isNotRoaming = true)

        assertThat(status.satisfies(NetworkType.ANY)).isTrue()
        assertThat(status.satisfies(NetworkType.UNMETERED)).isTrue()
        assertThat(status.satisfies(NetworkType.NOT_ROAMING)).isTrue()

        val meteredStatus = NetworkStatus(isConnected = true, isMetered = true, isNotRoaming = true)
        assertThat(meteredStatus.satisfies(NetworkType.UNMETERED)).isFalse()
    }
}
