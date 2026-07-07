package io.github.alirezajavan.downpour.sample.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alirezajavan.downpour.api.DownloadState
import io.github.alirezajavan.downpour.compose.DownloadItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = viewModel()) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Downloads") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showSheet = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New download") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(),
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyState(padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(downloads, key = { it.id }) { item ->
                    Column {
                        DownloadItemCard(
                            item = item,
                            onPause = viewModel::pause,
                            onResume = viewModel::resume,
                            onCancel = viewModel::cancel,
                            onRemove = viewModel::remove,
                        )
                        val state = item.state
                        if (state is DownloadState.Completed || (state is DownloadState.Failed && state.error.isRetryable)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (state is DownloadState.Completed) {
                                    TextButton(onClick = { viewModel.openFile(item) }) { Text("Open") }
                                }
                                if (state is DownloadState.Failed) {
                                    TextButton(onClick = { viewModel.retry(item.id) }) { Text("Retry") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        NewDownloadSheet(
            onDismiss = { showSheet = false },
            onStart = { form ->
                viewModel.enqueue(form)
                showSheet = false
            },
        )
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap \"New download\" to try priorities, network constraints, checksums and mirrors.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
