package io.github.alirezajavan.downpour.sample.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alirezajavan.downpour.sample.core.displayName
import io.github.alirezajavan.downpour.sample.core.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = viewModel()) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(downloads, key = { it.id }) { item ->
                    FilterChip(
                        selected = item.id == selectedId,
                        onClick = { viewModel.select(item.id) },
                        label = { Text(item.displayName()) },
                    )
                }
            }

            val current = report
            if (current == null) {
                Text(
                    "Select a download above to inspect its diagnostic report.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DiagnosticRow("State", current.state::class.simpleName.orEmpty())
                                DiagnosticRow("Resume supported", current.isResumeSupported.toString())
                                DiagnosticRow("Retry count", current.retryCount.toString())
                                DiagnosticRow(
                                    "Downloaded",
                                    "${formatBytes(current.downloadedBytes)} / ${formatBytes(current.totalBytes)}",
                                )
                                DiagnosticRow("ETag", current.etag ?: "—")
                                DiagnosticRow("Last-Modified", current.lastModified ?: "—")
                                current.lastError?.let { error ->
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    DiagnosticRow("Last error", error.message ?: error::class.simpleName.orEmpty())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
