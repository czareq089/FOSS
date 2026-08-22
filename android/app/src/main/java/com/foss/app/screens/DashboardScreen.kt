package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.foss.app.UiState
import com.foss.app.AppViewModel
import com.foss.app.components.ConsistencyHeatmapCard
import com.foss.app.components.VolumeWidgetCard
import com.foss.app.ui.theme.AccentBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onProfileClick: () -> Unit
) {
    var selectedRange by remember { mutableStateOf("7d") }

    LaunchedEffect(selectedRange) {
        viewModel.loadDashboardVolume(selectedRange)
        viewModel.loadConsistencyStats()
    }

    val profileInteractionSource = remember { MutableInteractionSource() }
    val isProfilePressed by profileInteractionSource.collectIsPressedAsState()
    val consistencyState = viewModel.consistencyStatsState.value
    val workoutDates = (consistencyState as? UiState.Success)?.data?.workoutDates ?: emptyList()

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

            VolumeWidgetCard(
                selectedRange = selectedRange,
                onRangeSelected = { selectedRange = it },
                state = viewModel.dashboardVolumeState.value,
                modifier = Modifier.fillMaxWidth()
            )

            ConsistencyHeatmapCard(
                workoutDatesStrings = workoutDates,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}