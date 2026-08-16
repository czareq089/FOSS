package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.formatUtcToLocal
import com.foss.app.models.WorkoutDetailExercise
import com.foss.app.models.WorkoutDetailSet
import kotlinx.coroutines.launch
import java.util.Locale

class EditableDetailSetState(
    var setNumber: Int,
    initialWeight: Double,
    initialReps: Int,
    initialRir: Int
) {
    var weight by mutableStateOf(if (initialWeight % 1.0 == 0.0) initialWeight.toInt().toString() else initialWeight.toString())
    var reps by mutableStateOf(initialReps.toString())
    var rir by mutableStateOf(initialRir.toString())
}

class EditableDetailExerciseState(
    val workoutExerciseId: Int,
    val exerciseId: Int,
    val name: String,
    initialSets: List<WorkoutDetailSet>
) {
    val sets = mutableStateListOf<EditableDetailSetState>().apply {
        addAll(initialSets.map { EditableDetailSetState(it.setNumber, it.weightKg, it.reps, it.rir) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    viewModel: WorkoutViewModel,
    workoutId: Int,
    startInEditMode: Boolean = false,
    onBack: () -> Unit
) {
    LaunchedEffect(workoutId) {
        viewModel.loadWorkoutDetails(workoutId)
    }

    val state = viewModel.workoutDetailsState.value
    var editMode by remember { mutableStateOf(startInEditMode) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val editableExercises = remember { mutableStateListOf<EditableDetailExerciseState>() }

    LaunchedEffect(state) {
        if (state is UiState.Success) {
            editableExercises.clear()
            editableExercises.addAll(
                state.data.exercises.map { ex ->
                    EditableDetailExerciseState(
                        workoutExerciseId = ex.workoutExerciseId,
                        exerciseId = ex.exerciseId,
                        name = ex.name,
                        initialSets = ex.sets
                    )
                }
            )
        }
    }

    val title = if (state is UiState.Success) state.data.routineName else "Workout"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is UiState.Success) {
                        IconButton(
                            onClick = {
                                if (editMode) {
                                    scope.launch {
                                        isSaving = true
                                        val payload = editableExercises.mapIndexed { index, ex ->
                                            WorkoutDetailExercise(
                                                workoutExerciseId = ex.workoutExerciseId,
                                                exerciseId = ex.exerciseId,
                                                name = ex.name,
                                                position = index + 1,
                                                sets = ex.sets.mapIndexed { sIndex, s ->
                                                    WorkoutDetailSet(
                                                        setId = 0,
                                                        setNumber = sIndex + 1,
                                                        weightKg = s.weight.toDoubleOrNull() ?: 0.0,
                                                        reps = s.reps.toIntOrNull() ?: 0,
                                                        rir = s.rir.toIntOrNull() ?: 0
                                                    )
                                                }
                                            )
                                        }
                                        val ok = viewModel.updateWorkoutDetails(workoutId, payload)
                                        isSaving = false
                                        if (ok) editMode = false
                                    }
                                } else {
                                    editMode = true
                                }
                            },
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    if (editMode) Icons.Filled.Check else Icons.Filled.Edit,
                                    contentDescription = if (editMode) "Save" else "Edit"
                                )
                            }
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
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Couldn't load workout", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadWorkoutDetails(workoutId) }) { Text("Try again") }
                    }
                }
                is UiState.Success -> {
                    val workout = state.data
                    if (editableExercises.isEmpty()) {
                        Text("This workout has no logged exercises.", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    text = formatUtcToLocal(workout.date),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            itemsIndexed(editableExercises, key = { _, ex -> ex.workoutExerciseId }) { index, exercise ->
                                WorkoutDetailEditableCard(
                                    exercise = exercise,
                                    editMode = editMode,
                                    onDeleteExercise = { editableExercises.removeAt(index) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutDetailEditableCard(
    exercise: EditableDetailExerciseState,
    editMode: Boolean,
    onDeleteExercise: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (editMode) {
                    IconButton(onClick = onDeleteExercise, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete exercise", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (exercise.sets.isEmpty()) {
                Text("No sets recorded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("Set", modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Weight", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Reps", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("RIR", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (editMode) Spacer(modifier = Modifier.weight(0.5f))
                }

                exercise.sets.forEachIndexed { sIndex, set ->
                    if (editMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${sIndex + 1}",
                                modifier = Modifier.weight(0.6f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedTextField(
                                value = set.weight,
                                onValueChange = { set.weight = it.filter { c -> c.isDigit() || c == '.' } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = set.reps,
                                onValueChange = { set.reps = it.filter(Char::isDigit) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = set.rir,
                                onValueChange = { set.rir = it.filter(Char::isDigit) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Box(modifier = Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
                                IconButton(onClick = { exercise.sets.removeAt(sIndex) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove set", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${sIndex + 1}", modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                            Text(String.format(Locale.US, "%.1f kg", set.weight.toDoubleOrNull() ?: 0.0), modifier = Modifier.weight(1.2f), color = MaterialTheme.colorScheme.onSurface)
                            Text(set.reps, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            Text(set.rir, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            if (editMode) {
                TextButton(
                    onClick = { exercise.sets.add(EditableDetailSetState(exercise.sets.size + 1, 0.0, 0, 0)) },
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                ) {
                    Text("+ Add set", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}