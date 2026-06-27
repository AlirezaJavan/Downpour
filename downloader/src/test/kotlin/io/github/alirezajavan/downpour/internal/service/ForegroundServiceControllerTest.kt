package io.github.alirezajavan.downpour.internal.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForegroundServiceControllerTest {
    private lateinit var context: Context
    private lateinit var controller: ForegroundServiceController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        controller = ForegroundServiceController(context, notificationsEnabled = true)
    }

    @Test
    fun `onActiveCountChanged starts service when count gt 0`() {
        controller.onActiveCountChanged(1)
    }

    @Test
    fun `onActiveCountChanged stops service when count is 0`() {
        controller.onActiveCountChanged(0)
    }
}
