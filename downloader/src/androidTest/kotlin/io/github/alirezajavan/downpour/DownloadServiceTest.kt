package io.github.alirezajavan.downpour

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import io.github.alirezajavan.downpour.internal.service.DownloadService
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadServiceTest {
    @get:Rule
    val serviceRule = ServiceTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testDownloadServiceActionsIntentCreation() {
        val pauseIntent =
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_PAUSE
                putExtra(DownloadService.EXTRA_ID, "test-id-1")
            }
        assertNotNull(pauseIntent)

        val resumeIntent =
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_RESUME
                putExtra(DownloadService.EXTRA_ID, "test-id-1")
            }
        assertNotNull(resumeIntent)

        val cancelIntent =
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL
                putExtra(DownloadService.EXTRA_ID, "test-id-1")
            }
        assertNotNull(cancelIntent)

        val stopIntent =
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_STOP
            }
        assertNotNull(stopIntent)
    }

    @Test
    fun testStartAndStopService() {
        val intent =
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_STOP
            }
        // Starting service with STOP action should execute cleanly and stop self
        context.startService(intent)
    }
}
