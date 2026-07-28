package io.github.alirezajavan.downpour.sample.constraints

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alirezajavan.downpour.api.ChecksumAlgorithm
import io.github.alirezajavan.downpour.api.ConflictStrategy
import io.github.alirezajavan.downpour.api.DuplicatePolicy
import io.github.alirezajavan.downpour.api.NetworkType
import io.github.alirezajavan.downpour.api.Priority
import io.github.alirezajavan.downpour.sample.core.SampleCatalog
import io.github.alirezajavan.downpour.sample.downloads.NewDownloadForm
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class Preset(
    val label: String,
    val apply: (NewDownloadForm) -> NewDownloadForm,
)

private val PRESETS =
    listOf(
        Preset("100 MB") { it.copy(url = SampleCatalog.DEFAULT_URL, mirrorUrl = "", checksumHex = "") },
        Preset("10 MB") { it.copy(url = SampleCatalog.SMALL_URL, mirrorUrl = "", checksumHex = "") },
        Preset("Broken + mirror") {
            it.copy(
                url = SampleCatalog.BROKEN_PRIMARY_URL,
                mirrorUrl = SampleCatalog.WORKING_MIRROR_URL,
                checksumHex = "",
            )
        },
        Preset("Invalid Checksum") {
            it.copy(
                url = SampleCatalog.SMALL_URL,
                checksumAlgorithm = ChecksumAlgorithm.SHA256,
                checksumHex = SampleCatalog.INVALID_CHECKSUM_HEX,
            )
        },
        Preset("Collision Test") {
            it.copy(url = "https://proof.ovh.net/files/10Mb.dat?name=${SampleCatalog.COLLISION_FILENAME}")
        },
    )

@Suppress("LongMethod", "FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstraintsScreen(viewModel: ConstraintsViewModel = viewModel()) {
    var form by remember { mutableStateOf(NewDownloadForm()) }
    var advancedExpanded by remember { mutableStateOf(true) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("DSL & Constraints") }) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Configure every DownloadRequest DSL option interactively before enqueuing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PRESETS) { preset ->
                    AssistChip(onClick = { form = preset.apply(form) }, label = { Text(preset.label) })
                }
            }

            OutlinedTextField(
                value = form.url,
                onValueChange = { form = form.copy(url = it) },
                label = { Text("URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Section(title = "Priority") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Priority.entries.forEachIndexed { index, priority ->
                        SegmentedButton(
                            selected = form.priority == priority,
                            onClick = { form = form.copy(priority = priority) },
                            shape = SegmentedButtonDefaults.itemShape(index, Priority.entries.size),
                        ) { Text(priority.name) }
                    }
                }
            }

            Section(title = "Network") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    NetworkType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = form.networkType == type,
                            onClick = { form = form.copy(networkType = type) },
                            shape = SegmentedButtonDefaults.itemShape(index, NetworkType.entries.size),
                        ) { Text(type.name) }
                    }
                }
            }

            Section(title = "If file exists (ConflictStrategy)") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ConflictStrategy.entries.forEachIndexed { index, strategy ->
                        SegmentedButton(
                            selected = form.conflictStrategy == strategy,
                            onClick = { form = form.copy(conflictStrategy = strategy) },
                            shape = SegmentedButtonDefaults.itemShape(index, ConflictStrategy.entries.size),
                        ) { Text(strategy.name) }
                    }
                }
            }

            Section(title = "If duplicate enqueued (DuplicatePolicy)") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    DuplicatePolicy.entries.forEachIndexed { index, policy ->
                        SegmentedButton(
                            selected = form.duplicatePolicy == policy,
                            onClick = { form = form.copy(duplicatePolicy = policy) },
                            shape = SegmentedButtonDefaults.itemShape(index, DuplicatePolicy.entries.size),
                        ) { Text(policy.name) }
                    }
                }
            }

            Section(title = "Max Connections: ${form.maxConnections}") {
                Slider(
                    value = form.maxConnections.toFloat(),
                    onValueChange = { form = form.copy(maxConnections = it.toInt()) },
                    valueRange = 1f..16f,
                    steps = 14,
                )
            }

            OutlinedTextField(
                value = form.tag,
                onValueChange = { form = form.copy(tag = it) },
                label = { Text("Tag (for grouping)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Advanced constraints & schedule", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { advancedExpanded = !advancedExpanded }) {
                    Icon(
                        imageVector = if (advancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle options",
                    )
                }
            }

            if (advancedExpanded) {
                LabeledSwitch("Requires charging", form.requiresCharging) { form = form.copy(requiresCharging = it) }
                LabeledSwitch("Requires battery not low", form.requiresBatteryNotLow) {
                    form = form.copy(requiresBatteryNotLow = it)
                }
                LabeledSwitch("Requires storage not low", form.requiresStorageNotLow) {
                    form = form.copy(requiresStorageNotLow = it)
                }

                var algorithmExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = algorithmExpanded,
                    onExpandedChange = { algorithmExpanded = it },
                ) {
                    OutlinedTextField(
                        value = form.checksumAlgorithm.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Checksum algorithm") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = algorithmExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                    )
                    ExposedDropdownMenu(
                        expanded = algorithmExpanded,
                        onDismissRequest = { algorithmExpanded = false },
                    ) {
                        ChecksumAlgorithm.entries.forEach { algorithm ->
                            DropdownMenuItem(
                                text = { Text(algorithm.name) },
                                onClick = {
                                    form = form.copy(checksumAlgorithm = algorithm)
                                    algorithmExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = form.checksumHex,
                    onValueChange = { form = form.copy(checksumHex = it) },
                    label = { Text("Expected checksum (hex)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.mirrorUrl,
                    onValueChange = { form = form.copy(mirrorUrl = it) },
                    label = { Text("Fallback mirror URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Section(title = "Schedule Window") {
                    DatePickerInput(
                        label = "Start time",
                        selectedTimestamp = form.schedule.startTimeMillis,
                        onTimestampChange = {
                            form = form.copy(schedule = form.schedule.copy(startTimeMillis = it))
                        },
                    )
                    DatePickerInput(
                        label = "End time",
                        selectedTimestamp = form.schedule.endTimeMillis,
                        onTimestampChange = {
                            form = form.copy(schedule = form.schedule.copy(endTimeMillis = it))
                        },
                    )
                }
            }

            Button(
                onClick = { viewModel.enqueue(form) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enqueue Download with DSL Configuration")
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun DatePickerInput(
    label: String,
    selectedTimestamp: Long?,
    onTimestampChange: (Long?) -> Unit,
) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val text =
        remember(selectedTimestamp) {
            selectedTimestamp?.let { formatter.format(Date(it)) } ?: "Not set"
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.weight(1f),
            label = { Text(label) },
        )
        TextButton(onClick = {
            val now = Calendar.getInstance()
            android.app
                .DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        android.app
                            .TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    val calendar = Calendar.getInstance()
                                    calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                                    onTimestampChange(calendar.timeInMillis)
                                },
                                now.get(Calendar.HOUR_OF_DAY),
                                now.get(Calendar.MINUTE),
                                true,
                            ).show()
                    },
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH),
                    now.get(Calendar.DAY_OF_MONTH),
                ).show()
        }) {
            Text("Pick")
        }
        if (selectedTimestamp != null) {
            TextButton(onClick = { onTimestampChange(null) }) {
                Text("Clear")
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Suppress("FunctionNaming")
@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
