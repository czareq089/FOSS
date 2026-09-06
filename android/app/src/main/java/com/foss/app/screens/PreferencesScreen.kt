package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.foss.app.AppViewModel
import com.foss.app.UiState
import com.foss.app.ui.theme.AccentBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadAlgorithmSettings()
    }

    val state = viewModel.algorithmSettingsState.value
    var showRir by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is UiState.Success) {
            showRir = state.data.showRIR
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferences") },
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
                                    val current = (state as? UiState.Success)?.data ?: return@launch
                                    val ok = viewModel.saveAlgorithmSettings(current.copy(showRIR = showRir))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedCard(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Log RIR During Workouts", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Display the Reps In Reserve column when recording sets in active workouts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = showRir,
                        onCheckedChange = { showRir = it }
                    )
                }
            }
        }
    }
}