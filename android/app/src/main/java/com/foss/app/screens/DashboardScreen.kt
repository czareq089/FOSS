package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.ui.theme.AccentBlue
import java.util.Locale

private data class VolumeRange(val key: String, val label: String)

private val VOLUME_RANGES = listOf(
    VolumeRange("1d", "1D"),
    VolumeRange("7d", "7D"),
    VolumeRange("1m", "1M"),
    VolumeRange("all", "All Time")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: WorkoutViewModel,
    onProfileClick: () -> Unit
) {
    var selectedRange by remember { mutableStateOf("7d") }

    LaunchedEffect(selectedRange) {
        viewModel.loadDashboardVolume(selectedRange)
    }

    val profileInteractionSource = remember { MutableInteractionSource() }
    val isProfilePressed by profileInteractionSource.collectIsPressedAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(38.dp)
                            .alpha(if (isProfilePressed) 0.4f else 1f)
                            .clickable(
                                interactionSource = profileInteractionSource,
                                indication = null,
                                onClick = onProfileClick
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "U",
                                style = MaterialTheme.typography.titleMedium,
                                color = AccentBlue
                            )
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
            Text("Overview", style = MaterialTheme.typography.titleLarge)

            VolumeWidget(
                selectedRange = selectedRange,
                onRangeSelected = { selectedRange = it },
                state = viewModel.dashboardVolumeState.value
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeWidget(
    selectedRange: String,
    onRangeSelected: (String) -> Unit,
    state: UiState<Double>
) {
    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Volume Lifted (kg)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VOLUME_RANGES.forEach { range ->
                    FilterChip(
                        selected = selectedRange == range.key,
                        onClick = { onRangeSelected(range.key) },
                        label = { Text(range.label) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.outline,
                            enabled = true,
                            selected = selectedRange == range.key
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    is UiState.Loading, UiState.Idle -> {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                    is UiState.Error -> {
                        Text(
                            "Couldn't load data",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is UiState.Success -> {
                        Text(
                            text = String.format(Locale.US, "%.1f", state.data),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}