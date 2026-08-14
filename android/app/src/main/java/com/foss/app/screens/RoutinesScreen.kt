package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.Routine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    viewModel: WorkoutViewModel,
    onRoutineSelected: (Routine) -> Unit,
    onRoutineEdit: (Routine) -> Unit,
    showTopBar: Boolean = true
) {
    LaunchedEffect(Unit) { viewModel.loadRoutines() }
    val state = viewModel.routinesState.value

    var showCreateDialog by remember { mutableStateOf(false) }
    var newRoutineName by remember { mutableStateOf("") }

    var pendingDelete by remember { mutableStateOf<Routine?>(null) }
    val scope = rememberCoroutineScope()

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
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.data, key = { it.id }) { routine ->
                                RoutineCard(
                                    routine = routine,
                                    onClick = { onRoutineSelected(routine) },
                                    onEditClick = { onRoutineEdit(routine) },
                                    onDeleteClick = { pendingDelete = routine }
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create routine")
            }
        }
    }

    if (showTopBar) {
        Scaffold(topBar = { TopAppBar(title = { Text("Your routines") }) }) { padding -> content(padding) }
    } else {
        content(PaddingValues())
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Routine", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = newRoutineName,
                    onValueChange = { newRoutineName = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newRoutineName.isNotBlank()) {
                        val nameToSave = newRoutineName
                        showCreateDialog = false
                        newRoutineName = ""
                        scope.launch {
                            val success = viewModel.createRoutine(nameToSave)
                            if (success) {
                                viewModel.loadRoutines()
                            }
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
    }

    pendingDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete routine?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Are you sure you want to delete '${routine.name}'? Your workout history will remain intact.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    val routineId = routine.id
                    pendingDelete = null
                    scope.launch {
                        val success = viewModel.deleteRoutine(routineId)
                        if (success) viewModel.loadRoutines()
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
private fun RoutineCard(
    routine: Routine,
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = routine.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = MaterialTheme.colorScheme.surfaceVariant)) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
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
    }
}