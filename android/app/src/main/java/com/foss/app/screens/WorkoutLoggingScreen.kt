package com.foss.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.ExerciseInfo
import kotlinx.coroutines.launch

class SetRowState(val setNumber: Int) {
    var weight by mutableStateOf("")
    var reps by mutableStateOf("")
    var rir by mutableStateOf("")
    var confirmed by mutableStateOf(false)
    var submitting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var autoFilled by mutableStateOf(true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLoggingScreen(
    viewModel: WorkoutViewModel,
    onAddExerciseClick: () -> Unit,
    onFinish: () -> Unit,
    onCancelWorkout: () -> Unit
) {
    val state = viewModel.workoutState.value
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // FIX: Przechwytywanie systemowego przycisku "Wstecz" na telefonie
    BackHandler {
        showCancelDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log workout") },
                navigationIcon = {
                    IconButton(onClick = { showCancelDialog = true }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel workout")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(8.dp) // Ostry, techniczny róg
            ) {
                Text("Finish workout")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state is UiState.Success) {
                val exercises = state.data.second
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = exercises,
                        key = { it.workoutExerciseId }
                    ) { exercise ->
                        ExerciseLogCard(viewModel = viewModel, exercise = exercise)
                    }

                    item {
                        OutlinedButton(
                            onClick = onAddExerciseClick,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(8.dp), // Ostry, techniczny róg
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text("Add exercise to active workout", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Text("No active workout.", modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    // FIX: Surowy, dopasowany motyw dla okna anulowania
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { if (!cancelling) showCancelDialog = false },
            title = { Text("Cancel this workout?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Any sets you've already logged will be deleted. This can't be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    enabled = !cancelling,
                    onClick = {
                        val workoutId = viewModel.currentWorkoutId()
                        if (workoutId == null) {
                            showCancelDialog = false
                            onCancelWorkout()
                            return@TextButton
                        }
                        cancelling = true
                        scope.launch {
                            viewModel.deleteWorkout(workoutId)
                            cancelling = false
                            showCancelDialog = false
                            onCancelWorkout()
                        }
                    }
                ) { Text("Cancel workout", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !cancelling, onClick = { showCancelDialog = false }) {
                    Text("Keep going", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp) // Wymuszony ostry róg
        )
    }
}

@Composable
private fun ExerciseLogCard(viewModel: WorkoutViewModel, exercise: ExerciseInfo) {
    val scope = rememberCoroutineScope()
    val sets = remember(exercise.workoutExerciseId) {
        mutableStateListOf(SetRowState(setNumber = 1))
    }

    fun propagateFromFirstRow() {
        val first = sets.firstOrNull() ?: return
        sets.drop(1).forEach { row ->
            if (!row.confirmed && row.autoFilled) {
                row.weight = first.weight
                row.reps = first.reps
                row.rir = first.rir
            }
        }
    }

    // FIX: Płaska, obrysowana karta z technicznym wykończeniem zamiast cieniowanej wypukłości
    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Set", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Weight", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Reps", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("RIR", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(0.9f))
            }

            Spacer(Modifier.height(8.dp))

            sets.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${row.setNumber}", modifier = Modifier.weight(0.6f), color = MaterialTheme.colorScheme.onSurface)

                    OutlinedTextField(
                        value = row.weight,
                        onValueChange = {
                            row.weight = it.filter { c -> c.isDigit() || c == '.' }
                            if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                        },
                        enabled = !row.confirmed && !row.submitting,
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
                        value = row.reps,
                        onValueChange = {
                            row.reps = it.filter(Char::isDigit)
                            if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                        },
                        enabled = !row.confirmed && !row.submitting,
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
                        value = row.rir,
                        onValueChange = {
                            row.rir = it.filter(Char::isDigit)
                            if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                        },
                        enabled = !row.confirmed && !row.submitting,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(modifier = Modifier.weight(0.9f), verticalAlignment = Alignment.CenterVertically) {
                        if (row.submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Checkbox(
                                checked = row.confirmed,
                                enabled = !row.confirmed,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val reps = row.reps.toIntOrNull()
                                        val weight = row.weight.toDoubleOrNull()
                                        if (reps == null || weight == null) {
                                            row.error = "Enter weight and reps first"
                                            return@Checkbox
                                        }
                                        row.error = null

                                        // Optymistyczne logowanie (zakładamy z góry sukces)
                                        row.confirmed = true
                                        row.submitting = true

                                        val rir = row.rir.toIntOrNull() ?: 0
                                        scope.launch {
                                            val success = viewModel.logSet(
                                                workoutExerciseId = exercise.workoutExerciseId,
                                                setNumber = row.setNumber,
                                                reps = reps,
                                                weightKg = weight,
                                                rir = rir
                                            )
                                            row.submitting = false
                                            if (success) {
                                                if (sets.last() === row) {
                                                    sets.add(SetRowState(setNumber = row.setNumber + 1))
                                                }
                                            } else {
                                                row.confirmed = false
                                                row.error = "Failed to save"
                                            }
                                        }
                                    }
                                }
                            )
                            if (!row.confirmed && sets.size > 1) {
                                IconButton(onClick = { sets.remove(row) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel set", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                row.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }

            TextButton(
                onClick = {
                    val nextSetNumber = (sets.maxOfOrNull { it.setNumber } ?: 0) + 1
                    sets.add(SetRowState(setNumber = nextSetNumber))
                },
                modifier = Modifier.align(Alignment.End)
            ) { Text("+ Add set", color = MaterialTheme.colorScheme.primary) }
        }
    }
}