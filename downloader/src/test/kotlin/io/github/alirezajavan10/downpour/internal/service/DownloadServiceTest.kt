package io.github.alirezajavan10.downpour.internal.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadServiceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `service starts correctly`() {
        val intent = Intent(context, DownloadService::class.java)
        Robolectric.buildService(DownloadService::class.java, intent).create().startCommand(0, 0)
    }
}
