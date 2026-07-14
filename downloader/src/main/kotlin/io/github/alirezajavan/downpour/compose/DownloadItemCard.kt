package io.github.alirezajavan.downpour.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.DownloadState
import io.github.alirezajavan.downpour.internal.util.ByteFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun DownloadItemCard(
    item: DownloadItem,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            val fileName =
                when (val dest = item.destination) {
                    is DownloadDestination.File -> dest.path.substringAfterLast('/')
                    is DownloadDestination.Uri -> dest.uriString.substringAfterLast('/')
                }
            Text(text = fileName)
            Text(text = describe(item.state))

            val fraction =
                when (val state = item.state) {
                    is DownloadState.Running -> state.progress.fraction
                    is DownloadState.Paused -> state.progress.fraction
                    is DownloadState.Completed -> 1f
                    else -> 0f
                }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            // FlowRow so the action buttons wrap onto the next line on narrow screens instead of
            // overflowing / clipping the last button.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(onClick = { onPause(item.id) }) { Text("Pause") }
                Button(onClick = { onResume(item.id) }) { Text("Resume") }
                Button(onClick = { onCancel(item.id) }) { Text("Cancel") }
                Button(onClick = { onRemove(item.id) }) { Text("Remove") }
            }
        }
    }
}

private fun describe(state: DownloadState): String =
    when (state) {
        is DownloadState.Queued -> {
            "Queued"
        }

        is DownloadState.Running -> {
            val speed = ByteFormatter.formatSpeed(state.progress.bytesPerSecond)
            val speedPart = if (speed.isNotEmpty()) " • $speed" else ""
            "${state.progress.percent}%$speedPart"
        }

        is DownloadState.Paused -> {
            "Paused at ${state.progress.percent}%"
        }

        is DownloadState.Completed -> {
            "Completed"
        }

        is DownloadState.Failed -> {
            "Failed: ${state.error.message}"
        }

        is DownloadState.Cancelled -> {
            "Cancelled"
        }

        is DownloadState.WaitingForNetwork -> {
            "Waiting for network"
        }

        is DownloadState.Scheduled -> {
            "Scheduled for window"
        }
    }
