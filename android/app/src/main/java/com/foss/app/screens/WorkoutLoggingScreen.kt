package com.foss.app.screens

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
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Finish workout")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state is UiState.Success) {
                val exercises = state.data.second
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(exercises) { exercise ->
                        ExerciseLogCard(viewModel = viewModel, exercise = exercise)
                    }

                    item {
                        OutlinedButton(
                            onClick = onAddExerciseClick,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add exercise to active workout")
                        }
                    }
                }
            } else {
                Text("No active workout.", modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { if (!cancelling) showCancelDialog = false },
            title = { Text("Cancel this workout?") },
            text = { Text("Any sets you've already logged will be deleted. This can't be undone.") },
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
                TextButton(enabled = !cancelling, onClick = { showCancelDialog = false }) { Text("Keep going") }
            }
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

    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Set", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall)
                Text("Weight (kg)", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall)
                Text("Reps", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("RIR", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.weight(0.9f))
            }

            sets.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${row.setNumber}", modifier = Modifier.weight(0.6f))

                    OutlinedTextField(
                        value = row.weight,
                        onValueChange = {
                            row.weight = it.filter { c -> c.isDigit() || c == '.' }
                            if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                        },
                        enabled = !row.confirmed && !row.submitting,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp)
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
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
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
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
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
                                                row.confirmed = true
                                                if (sets.last() === row) {
                                                    sets.add(SetRowState(setNumber = row.setNumber + 1))
                                                }
                                            } else {
                                                row.error = "Failed to save, try again"
                                            }
                                        }
                                    }
                                }
                            )
                            if (!row.confirmed && sets.size > 1) {
                                IconButton(onClick = { sets.remove(row) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel set", modifier = Modifier.size(16.dp))
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
            ) { Text("+ Add set") }
        }
    }
}