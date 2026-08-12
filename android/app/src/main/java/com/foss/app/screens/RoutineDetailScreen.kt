package com.foss.app.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.ReorderPosition
import com.foss.app.models.RoutineExercisePreview
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    viewModel: WorkoutViewModel,
    routineId: Int,
    startInEditMode: Boolean = false,
    onStartWorkout: (workoutId: Int) -> Unit,
    onAddExerciseClick: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(routineId) {
        viewModel.resetWorkoutState()
        viewModel.loadRoutineExercises(routineId)
    }

    val previewState = viewModel.routineExercisesState.value
    val startState = viewModel.workoutState.value
    val scope = rememberCoroutineScope()

    val routineName = (viewModel.routinesState.value as? UiState.Success)
        ?.data?.firstOrNull { it.id == routineId }?.name ?: "Routine"

    LaunchedEffect(startState) {
        if (startState is UiState.Success) onStartWorkout(startState.data.first)
    }

    var editMode by remember { mutableStateOf(startInEditMode) }
    val exercises = remember { mutableStateListOf<RoutineExercisePreview>() }

    LaunchedEffect(previewState) {
        if (previewState is UiState.Success) {
            exercises.clear()
            exercises.addAll(previewState.data)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routineName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (editMode) {
                            val positions = exercises.mapIndexed { index, ex ->
                                ReorderPosition(exerciseId = ex.exerciseId, position = index + 1)
                            }
                            scope.launch {
                                viewModel.reorderExercises(routineId, positions)
                                viewModel.loadRoutineExercises(routineId)
                            }
                            editMode = false
                        } else {
                            editMode = true
                        }
                    }) {
                        Icon(
                            if (editMode) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = if (editMode) "Save routine" else "Edit routine"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!editMode) {
                Surface(shadowElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (startState is UiState.Error) {
                            Text(
                                startState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Button(
                            onClick = { viewModel.startWorkout(routineId) },
                            enabled = startState !is UiState.Loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (startState is UiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Start workout")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (previewState) {
                is UiState.Loading, UiState.Idle -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Couldn't load exercises", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(previewState.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadRoutineExercises(routineId) }) { Text("Try again") }
                    }
                }
                is UiState.Success -> {
                    if (exercises.isEmpty() && !editMode) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("This routine has no exercises yet.")
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { editMode = true }) {
                                Text("Add exercise")
                            }
                        }
                    } else if (editMode) {
                        ReorderableExerciseList(
                            routineId = routineId,
                            viewModel = viewModel,
                            exercises = exercises,
                            onAddExerciseClick = onAddExerciseClick
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(exercises, key = { _, ex -> ex.exerciseId }) { _, exercise ->
                                ExercisePreviewCard(exercise)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExercisePreviewCard(exercise: RoutineExercisePreview) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text("Default: ${exercise.defaultSets} sets", style = MaterialTheme.typography.labelSmall)
            }
            if (exercise.lastSets.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                val summary = exercise.lastSets.joinToString(separator = "  ") { s ->
                    String.format(Locale.US, "%.1fkg×%d", s.weightKg, s.reps)
                }
                Text(
                    "Last time: $summary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val DRAG_ROW_HEIGHT = 64.dp

@Composable
private fun ReorderableExerciseList(
    routineId: Int,
    viewModel: WorkoutViewModel,
    exercises: androidx.compose.runtime.snapshots.SnapshotStateList<RoutineExercisePreview>,
    onAddExerciseClick: () -> Unit
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { DRAG_ROW_HEIGHT.toPx() }
    val scope = rememberCoroutineScope()

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Long-press to reorder, or delete exercises",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            exercises.forEachIndexed { index, exercise ->
                val isDragged = draggedIndex == index
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DRAG_ROW_HEIGHT)
                        .padding(vertical = 4.dp)
                        .graphicsLayer { translationY = if (isDragged) dragOffsetY else 0f }
                        .pointerInput(exercises.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedIndex = index; dragOffsetY = 0f },
                                onDragEnd = { draggedIndex = null; dragOffsetY = 0f },
                                onDragCancel = { draggedIndex = null; dragOffsetY = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val currentIndex = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                    val targetIndex = (currentIndex + (dragOffsetY / rowHeightPx).toInt())
                                        .coerceIn(0, exercises.size - 1)
                                    if (targetIndex != currentIndex) {
                                        val moved = exercises.removeAt(currentIndex)
                                        exercises.add(targetIndex, moved)
                                        draggedIndex = targetIndex
                                        dragOffsetY -= (targetIndex - currentIndex) * rowHeightPx
                                    }
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DragHandle, contentDescription = "Drag to reorder")
                            Spacer(Modifier.width(12.dp))
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        }

                        IconButton(onClick = {
                            scope.launch {
                                val success = viewModel.removeExerciseFromRoutine(routineId, exercise.exerciseId)
                                if (success) {
                                    exercises.removeAt(index)
                                }
                            }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete exercise",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onAddExerciseClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add exercise")
        }
    }
}