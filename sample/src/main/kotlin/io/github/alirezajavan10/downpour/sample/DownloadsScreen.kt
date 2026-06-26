package io.github.alirezajavan10.downpour.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alirezajavan10.downpour.compose.DownloadItemCard

private const val SAMPLE_URL = "https://ash-speed.hetzner.com/100MB.bin"

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(SAMPLE_URL) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { viewModel.enqueueSample(url) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text("Download")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(downloads, key = { it.id }) { item ->
                DownloadItemCard(
                    item = item,
                    onPause = { viewModel.pause(it) },
                    onResume = { viewModel.resume(it) },
                    onCancel = { viewModel.cancel(it) },
                    onRemove = { viewModel.remove(it) },
                )
            }
        }
    }
}
