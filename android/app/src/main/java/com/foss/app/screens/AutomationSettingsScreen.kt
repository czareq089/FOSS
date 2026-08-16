package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.UserAlgorithmSettings
import com.foss.app.ui.theme.AccentBlue
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsScreen(
    viewModel: WorkoutViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadAlgorithmSettings()
    }

    val state = viewModel.algorithmSettingsState.value
    var warmupEnabled by remember { mutableStateOf(true) }
    var warmupBase by remember { mutableStateOf("first_working_set") }
    var dropEnabled by remember { mutableStateOf(true) }
    var dropPercentage by remember { mutableFloatStateOf(20f) }
    var backoffEnabled by remember { mutableStateOf(true) }
    var backoffPercentage by remember { mutableFloatStateOf(10f) }

    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is UiState.Success) {
            warmupEnabled = state.data.warmupEnabled
            warmupBase = state.data.warmupBase
            dropEnabled = state.data.dropEnabled
            dropPercentage = state.data.dropPercentage.toFloat()
            backoffEnabled = state.data.backoffEnabled
            backoffPercentage = state.data.backoffPercentage.toFloat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automation & Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val checkSource = remember { MutableInteractionSource() }
                    val isCheckPressed by checkSource.collectIsPressedAsState()

                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .alpha(if (isSaving || isCheckPressed) 0.4f else 1f)
                            .clickable(
                                enabled = !isSaving && state is UiState.Success,
                                interactionSource = checkSource,
                                indication = null
                            ) {
                                scope.launch {
                                    isSaving = true
                                    val payload = UserAlgorithmSettings(
                                        warmupEnabled = warmupEnabled,
                                        warmupBase = warmupBase,
                                        dropEnabled = dropEnabled,
                                        dropPercentage = dropPercentage.toDouble(),
                                        backoffEnabled = backoffEnabled,
                                        backoffPercentage = backoffPercentage.toDouble()
                                    )
                                    val ok = viewModel.saveAlgorithmSettings(payload)
                                    isSaving = false
                                    if (ok) onBack()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = "Save", tint = AccentBlue)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is UiState.Loading, UiState.Idle -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Column(modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.loadAlgorithmSettings() }) { Text("Retry") }
                    }
                }
                is UiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedCard(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Warmup Ramp-up Calculation", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Auto-fill ramp-up weights for warmup sets",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = warmupEnabled,
                                        onCheckedChange = { warmupEnabled = it }
                                    )
                                }

                                if (warmupEnabled) {
                                    Spacer(Modifier.height(12.dp))
                                    Text("Baseline Reference", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(6.dp))
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = warmupBase == "first_working_set",
                                            onClick = { warmupBase = "first_working_set" },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                        ) {
                                            Text("1st Working Set", style = MaterialTheme.typography.labelSmall)
                                        }
                                        SegmentedButton(
                                            selected = warmupBase == "heaviest_set",
                                            onClick = { warmupBase = "heaviest_set" },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                        ) {
                                            Text("Heaviest Set", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedCard(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Drop Set Auto-calculation", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Auto-reduce weight from preceding set",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = dropEnabled,
                                        onCheckedChange = { dropEnabled = it }
                                    )
                                }

                                if (dropEnabled) {
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Weight Reduction", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(String.format(Locale.US, "-%.0f%%", dropPercentage), color = AccentBlue, style = MaterialTheme.typography.titleSmall)
                                    }
                                    Slider(
                                        value = dropPercentage,
                                        onValueChange = { dropPercentage = it },
                                        valueRange = 10f..40f,
                                        steps = 5
                                    )
                                }
                            }
                        }
                        
                        OutlinedCard(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Back-off Set Auto-calculation", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Auto-reduce weight for volume back-off sets",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = backoffEnabled,
                                        onCheckedChange = { backoffEnabled = it }
                                    )
                                }

                                if (backoffEnabled) {
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Weight Reduction", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(String.format(Locale.US, "-%.0f%%", backoffPercentage), color = AccentBlue, style = MaterialTheme.typography.titleSmall)
                                    }
                                    Slider(
                                        value = backoffPercentage,
                                        onValueChange = { backoffPercentage = it },
                                        valueRange = 5f..25f,
                                        steps = 3
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}