package com.foss.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.Routine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    viewModel: WorkoutViewModel,
    onRoutineSelected: (Routine) -> Unit,
    showTopBar: Boolean = true
) {
    LaunchedEffect(Unit) { viewModel.loadRoutines() }
    val state = viewModel.routinesState.value

    val content: @Composable (PaddingValues) -> Unit = { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is UiState.Loading, UiState.Idle -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Couldn't load routines", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadRoutines() }) { Text("Try again") }
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Text("You don't have any routines yet.", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.data) { routine ->
                                RoutineCard(routine = routine, onClick = { onRoutineSelected(routine) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTopBar) {
        Scaffold(topBar = { TopAppBar(title = { Text("Your routines") }) }) { padding -> content(padding) }
    } else {
        content(PaddingValues())
    }
}

@Composable
private fun RoutineCard(routine: Routine, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(routine.name, style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}