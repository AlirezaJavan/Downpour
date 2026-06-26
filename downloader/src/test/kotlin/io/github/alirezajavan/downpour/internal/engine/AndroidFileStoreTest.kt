package io.github.alirezajavan.downpour.internal.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.DownloadDestination
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidFileStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var fileStore: AndroidFileStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fileStore = AndroidFileStore(context)
    }

    @Test
    fun `lengthOf returns file length for existing file`() {
        val file = tempFolder.newFile("test.bin")
        file.writeBytes(ByteArray(100))
        val destination = DownloadDestination.File(file.absolutePath)

        val length = fileStore.lengthOf(destination)
        assertThat(length).isEqualTo(100)
    }

    @Test
    fun `delete removes the file`() {
        val file = tempFolder.newFile("delete.me")
        val destination = DownloadDestination.File(file.absolutePath)

        fileStore.delete(destination)
        assertThat(file.exists()).isFalse()
    }
}
