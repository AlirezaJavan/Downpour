package io.github.alirezajavan.downpour.internal.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadRecoveryWorkerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `doWork returns success`() =
        runTest {
            val worker = TestListenableWorkerBuilder<DownloadRecoveryWorker>(context).build()
            val result = worker.doWork()
            assertThat(result).isEqualTo(ListenableWorker.Result.success())
        }
}
