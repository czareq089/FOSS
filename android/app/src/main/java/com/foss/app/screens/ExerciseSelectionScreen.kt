package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.ExerciseItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelectionScreen(
    viewModel: WorkoutViewModel,
    onExerciseSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var submittingId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadAllExercises()
    }

    val state = viewModel.exercisesListState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when (state) {
                    is UiState.Loading, UiState.Idle -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is UiState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is UiState.Success -> {
                        val filtered = remember(searchQuery, state.data) {
                            if (searchQuery.isBlank()) state.data
                            else state.data.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                        it.type.contains(searchQuery, ignoreCase = true) ||
                                        it.equipment.contains(searchQuery, ignoreCase = true)
                            }
                        }

                        if (filtered.isEmpty()) {
                            Text(
                                "No exercises found",
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(filtered, key = { it.id }) { exercise ->
                                    ExerciseSelectCard(
                                        exercise = exercise,
                                        isSubmitting = submittingId == exercise.id,
                                        onClick = {
                                            if (submittingId == null) {
                                                submittingId = exercise.id
                                                onExerciseSelected(exercise.id)
                                            }
                                        }
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

@Composable
private fun ExerciseSelectCard(
    exercise: ExerciseItem,
    isSubmitting: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSubmitting, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${exercise.type} • ${exercise.equipment}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}