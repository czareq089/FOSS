package com.foss.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.WorkoutSummary
import kotlinx.coroutines.launch

@Composable
fun WorkoutHistoryScreen(viewModel: WorkoutViewModel) {
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.data, key = { it.workoutId }) { workout ->
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(workout.routineName, style = MaterialTheme.typography.titleMedium)
                                        Text(workout.date, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { pendingDelete = workout }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete workout")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { workout ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete workout?") },
            text = { Text("This will permanently delete this workout and all logged sets. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (viewModel.deleteWorkout(workout.workoutId)) viewModel.loadWorkoutHistory()
                        pendingDelete = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}