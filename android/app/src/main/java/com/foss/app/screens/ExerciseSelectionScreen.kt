package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAllExercises()
    }

    val state = viewModel.exercisesListState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select exercise") },
                navigationIcon = {
                    val backSource = remember { MutableInteractionSource() }
                    val isBackPressed by backSource.collectIsPressedAsState()

                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(36.dp)
                            .alpha(if (isBackPressed) 0.4f else 1f)
                            .clickable(
                                interactionSource = backSource,
                                indication = null
                            ) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    val addSource = remember { MutableInteractionSource() }
                    val isAddPressed by addSource.collectIsPressedAsState()

                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .alpha(if (isAddPressed) 0.4f else 1f)
                            .clickable(
                                interactionSource = addSource,
                                indication = null
                            ) { showCreateDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add new exercise", tint = MaterialTheme.colorScheme.primary)
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
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search exercise...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
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
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "No exercises found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { showCreateDialog = true }) {
                                    Text("Create '${searchQuery.trim()}'", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
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

    if (showCreateDialog) {
        CreateExerciseDialog(
            initialName = searchQuery,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type, equipment ->
                scope.launch {
                    val created = viewModel.createExercise(name, type, equipment)
                    showCreateDialog = false
                    if (created != null) {
                        onExerciseSelected(created.id)
                    }
                }
            }
        )
    }
}

@Composable
private fun CreateExerciseDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, type: String, equipment: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var type by remember { mutableStateOf("Strength") }
    var equipment by remember { mutableStateOf("Barbell") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Exercise", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise name") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Category / Type") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = equipment,
                    onValueChange = { equipment = it },
                    label = { Text("Equipment (e.g. Barbell, Machine)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim(), type.trim(), equipment.trim())
                    }
                }
            ) {
                Text("Save & Add", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    )
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