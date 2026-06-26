package io.github.alirezajavan.downpour.internal.engine

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.net.toUri
import io.github.alirezajavan.downpour.api.DownloadDestination
import java.io.File
import java.io.RandomAccessFile

internal interface FileStore {
    fun lengthOf(destination: DownloadDestination): Long

    fun usableSpaceFor(destination: DownloadDestination): Long

    fun ensureParentExists(destination: DownloadDestination)

    fun preallocate(
        destination: DownloadDestination,
        size: Long,
    )

    fun delete(destination: DownloadDestination)

    fun openWritable(destination: DownloadDestination): RandomAccessSink

    fun openReadable(destination: DownloadDestination): java.io.InputStream
}

internal class AndroidFileStore(
    private val context: Context,
) : FileStore {
    private val storageManager by lazy {
        context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    }

    override fun lengthOf(destination: DownloadDestination): Long =
        when (destination) {
            is DownloadDestination.File -> {
                val file = File(destination.path)
                if (file.exists()) file.length() else 0
            }

            is DownloadDestination.Uri -> {
                context.contentResolver
                    .openAssetFileDescriptor(destination.uriString.toUri(), "r")
                    ?.use { it.length } ?: 0
            }
        }

    override fun usableSpaceFor(destination: DownloadDestination): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && destination is DownloadDestination.File) {
            runCatching {
                val file = File(destination.path)
                val uuid = storageManager.getUuidForPath(file)
                return storageManager.getAllocatableBytes(uuid)
            }
        }
        return when (destination) {
            is DownloadDestination.File -> {
                val file = File(destination.path)
                val anchor = file.parentFile ?: file
                anchor.usableSpace
            }

            is DownloadDestination.Uri -> {
                Long.MAX_VALUE
            }
        }
    }

    override fun ensureParentExists(destination: DownloadDestination) {
        if (destination is DownloadDestination.File) {
            File(destination.path).parentFile?.mkdirs()
        }
    }

    override fun preallocate(
        destination: DownloadDestination,
        size: Long,
    ) {
        if (size <= 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && destination is DownloadDestination.File) {
            runCatching {
                val file = File(destination.path)
                val uuid = storageManager.getUuidForPath(file)
                storageManager.allocateBytes(uuid, size)
            }
        }

        openWritable(destination).use { sink ->
            if (sink.length() != size) sink.setLength(size)
        }
    }

    override fun delete(destination: DownloadDestination) {
        when (destination) {
            is DownloadDestination.File -> {
                val file = File(destination.path)
                if (file.exists()) file.delete()
            }

            is DownloadDestination.Uri -> {
                context.contentResolver.delete(destination.uriString.toUri(), null, null)
            }
        }
    }

    override fun openWritable(destination: DownloadDestination): RandomAccessSink =
        when (destination) {
            is DownloadDestination.File -> RafSink(File(destination.path))
            is DownloadDestination.Uri -> UriSink(context, destination.uriString.toUri())
        }

    override fun openReadable(destination: DownloadDestination): java.io.InputStream =
        when (destination) {
            is DownloadDestination.File -> {
                File(destination.path).inputStream()
            }

            is DownloadDestination.Uri -> {
                context.contentResolver.openInputStream(destination.uriString.toUri())
                    ?: throw IllegalStateException("Could not open stream for ${destination.uriString}")
            }
        }

    private class RafSink(
        file: File,
    ) : RandomAccessSink {
        private val raf = RandomAccessFile(file, "rw")

        override fun seek(position: Long) = raf.seek(position)

        override fun write(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ) = raf.write(buffer, offset, length)

        override fun length(): Long = raf.length()

        override fun setLength(newLength: Long) = raf.setLength(newLength)

        override fun close() = raf.close()
    }

    private class UriSink(
        context: Context,
        uri: Uri,
    ) : RandomAccessSink {
        private val pfd =
            context.contentResolver.openFileDescriptor(uri, "rw")
                ?: throw IllegalStateException("Could not open PFD for $uri")
        private val channel = java.io.FileOutputStream(pfd.fileDescriptor).channel

        override fun seek(position: Long) {
            channel.position(position)
        }

        override fun write(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ) {
            channel.write(java.nio.ByteBuffer.wrap(buffer, offset, length))
        }

        override fun length(): Long = channel.size()

        override fun setLength(newLength: Long) {
            channel.truncate(newLength)
        }

        override fun close() {
            channel.close()
            pfd.close()
        }
    }
}
