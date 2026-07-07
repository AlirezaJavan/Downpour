package io.github.alirezajavan.downpour.sample.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alirezajavan.downpour.sample.core.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(viewModel: GroupsViewModel = viewModel()) {
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    val progress by viewModel.groupProgress.collectAsStateWithLifecycle()
    val taggedDownloads by viewModel.taggedDownloads.collectAsStateWithLifecycle()

    val tags = remember(allDownloads) { allDownloads.mapNotNull { it.tag }.distinct().sorted() }

    Scaffold(topBar = { TopAppBar(title = { Text("Tags & Groups") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tags) { tag ->
                        FilterChip(
                            selected = tag == selectedTag,
                            onClick = { viewModel.selectTag(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("\"$selectedTag\" progress", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${progress.completed}/${progress.total} completed" +
                                if (progress.failed > 0) " • ${progress.failed} failed" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Running: ${progress.running} • Queued: ${progress.queued}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                BulkActionsRow(viewModel)
            }

            items(taggedDownloads, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.displayName())
                        Text(item.state::class.simpleName.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkActionsRow(viewModel: GroupsViewModel) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = viewModel::pauseTag) { Text("Pause tag") }
        OutlinedButton(onClick = viewModel::resumeTag) { Text("Resume tag") }
        OutlinedButton(onClick = viewModel::cancelTag) { Text("Cancel tag") }
        OutlinedButton(onClick = viewModel::removeTag) { Text("Remove tag") }
    }
}
