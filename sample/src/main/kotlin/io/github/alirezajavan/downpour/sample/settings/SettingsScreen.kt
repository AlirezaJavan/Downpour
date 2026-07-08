package io.github.alirezajavan.downpour.sample.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(title = "Global throughput") {
                    LabeledSlider(
                        label = "Max concurrent downloads: ${settings.maxConcurrentDownloads}",
                        value = settings.maxConcurrentDownloads.toFloat(),
                        range = 1f..10f,
                        steps = 8,
                        onChange = { viewModel.update { s -> s.copy(maxConcurrentDownloads = it.toInt()) } },
                    )
                    val capLabel =
                        if (settings.globalBandwidthCapMbps ==
                            0
                        ) {
                            "Global bandwidth cap: unlimited"
                        } else {
                            "Global bandwidth cap: ${settings.globalBandwidthCapMbps} MB/s"
                        }
                    LabeledSlider(
                        label = capLabel,
                        value = settings.globalBandwidthCapMbps.toFloat(),
                        range = 0f..50f,
                        steps = 49,
                        onChange = { viewModel.update { s -> s.copy(globalBandwidthCapMbps = it.toInt()) } },
                    )
                }
            }

            item {
                SettingsSection(title = "Adaptive concurrency") {
                    LabeledSwitch(
                        label = "Enable adaptive tuning",
                        checked = settings.adaptiveConcurrency,
                        onCheckedChange = { viewModel.update { s -> s.copy(adaptiveConcurrency = it) } },
                    )
                    if (settings.adaptiveConcurrency) {
                        LabeledSlider(
                            label = "Minimum connections: ${settings.minConnections}",
                            value = settings.minConnections.toFloat(),
                            range = 1f..16f,
                            steps = 14,
                            onChange = { viewModel.update { s -> s.copy(minConnections = it.toInt()) } },
                        )
                        LabeledSlider(
                            label = "Re-evaluate every: ${settings.reevaluationIntervalSeconds}s",
                            value = settings.reevaluationIntervalSeconds.toFloat(),
                            range = 2f..30f,
                            steps = 27,
                            onChange = { viewModel.update { s -> s.copy(reevaluationIntervalSeconds = it.toInt()) } },
                        )
                    }
                    Text(
                        "Downpour observes per-part throughput and grows or shrinks the number of " +
                            "connections for a transfer within [min, maxConnections] instead of using a fixed count.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection(title = "Logging") {
                    LabeledSwitch(
                        label = "Verbose logging",
                        checked = settings.verboseLogging,
                        onCheckedChange = { viewModel.update { s -> s.copy(verboseLogging = it) } },
                    )
                }
            }

            item {
                SettingsSection(title = "Networking") {
                    LabeledSwitch(
                        label = "Prefer IPv4",
                        checked = settings.preferIpv4,
                        onCheckedChange = { viewModel.update { s -> s.copy(preferIpv4 = it) } },
                    )
                    Text(
                        "Forces IPv4-only DNS resolution. Enable this if downloads hang or time out on " +
                            "networks with a broken or unreliable IPv6 path (connect attempts stalling for " +
                            "the full connect timeout instead of failing fast).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Rebuilds the download engine in place. Any in-flight downloads are " +
                            "requeued and resume automatically under the new settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { viewModel.apply(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null)
                        Text(" Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
