package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foss.app.UiState
import com.foss.app.AppViewModel
import com.foss.app.formatUtcToLocal
import com.foss.app.models.WorkoutSummary
import com.foss.app.ui.theme.AccentBlue
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WorkoutHistoryScreen(
    viewModel: AppViewModel,
    onWorkoutSelected: (WorkoutSummary) -> Unit,
    onWorkoutEdit: (WorkoutSummary) -> Unit
) {
    LaunchedEffect(Unit) { viewModel.loadWorkoutHistory() }
    val state = viewModel.workoutHistoryState.value
    var pendingDelete by remember { mutableStateOf<WorkoutSummary?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is UiState.Loading, UiState.Idle -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is UiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Couldn't load workouts", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadWorkoutHistory() }) { Text("Try again") }
                }
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Text("No workouts logged yet.", modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.data, key = { it.workoutId }) { workout ->
                            WorkoutSummaryCard(
                                workout = workout,
                                onClick = { onWorkoutSelected(workout) },
                                onEditClick = { onWorkoutEdit(workout) },
                                onDeleteClick = { pendingDelete = workout }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { workout ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete workout?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("This will permanently delete this workout and all logged sets. This can't be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    val wId = workout.workoutId
                    pendingDelete = null
                    scope.launch {
                        val success = viewModel.deleteWorkout(wId)
                        if (success) viewModel.loadWorkoutHistory()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun WorkoutSummaryCard(
    workout: WorkoutSummary,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workout.routineName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatUtcToLocal(workout.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    val moreSource = remember { MutableInteractionSource() }
                    val isMorePressed by moreSource.collectIsPressedAsState()

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .alpha(if (isMorePressed) 0.4f else 1f)
                            .clickable(
                                interactionSource = moreSource,
                                indication = null
                            ) { expanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = MaterialTheme.colorScheme.surfaceVariant)) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = { expanded = false; onEditClick() },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { expanded = false; onDeleteClick() },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(Modifier.height(10.dp))

            val weightFormatted = if (workout.totalWeightKg % 1.0 == 0.0) {
                "${workout.totalWeightKg.toInt()} kg"
            } else {
                String.format(Locale.US, "%.1f kg", workout.totalWeightKg)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Weight",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = weightFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column {
                    Text(
                        text = "Sets",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${workout.totalSets}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column {
                    Text(
                        text = "Reps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${workout.totalReps}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column {
                    Text(
                        text = "PRs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (workout.prCount > 0) {
                            Icon(
                                imageVector = Icons.Filled.Stars,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = if (workout.prCount == 1) "1 PR" else "${workout.prCount} PRs",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (workout.prCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (workout.prCount > 0) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}