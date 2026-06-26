package io.github.alirezajavan10.downpour.internal.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan10.downpour.api.NotificationConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadNotificationFactoryTest {
    private lateinit var context: Context
    private lateinit var factory: DownloadNotificationFactory

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        factory = DownloadNotificationFactory(context, NotificationConfig())
    }

    @Test
    fun `build creates notification for empty list`() {
        val notification = factory.build(emptyList())
        assertThat(notification).isNotNull()
    }

    @Test
    fun `build creates notification for active downloads`() {
        val item = mockk<io.github.alirezajavan10.downpour.api.DownloadItem>(relaxed = true)
        every { item.id } returns "id"
        every { item.state } returns io.github.alirezajavan10.downpour.api.DownloadState.Queued

        val notification = factory.build(listOf(item))
        assertThat(notification).isNotNull()
    }
}
