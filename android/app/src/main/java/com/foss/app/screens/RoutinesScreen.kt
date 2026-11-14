package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foss.app.AppViewModel
import com.foss.app.UiState
import com.foss.app.models.Routine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    viewModel: AppViewModel,
    onRoutineSelected: (Routine) -> Unit,
    onRoutineEdit: (Routine) -> Unit,
    showTopBar: Boolean = true
) {
    LaunchedEffect(Unit) { viewModel.loadRoutines() }
    val state = viewModel.routinesState.value

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active, 1 = Archived
    var selectedGroupFilter by remember { mutableStateOf<String?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newRoutineName by remember { mutableStateOf("") }
    var newRoutineGroup by remember { mutableStateOf("") }

    var groupRoutineTarget by remember { mutableStateOf<Routine?>(null) }
    var targetGroupName by remember { mutableStateOf("") }

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
                    val allRoutines = state.data
                    val activeRoutines = allRoutines.filter { !it.isArchived }
                    val archivedRoutines = allRoutines.filter { it.isArchived }
                    val existingGroups = activeRoutines
                        .map { it.groupName.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Segmented Tab for Active vs Archived
                        PrimaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) }
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Active (${activeRoutines.size})", style = MaterialTheme.typography.titleSmall) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Archived (${archivedRoutines.size})", style = MaterialTheme.typography.titleSmall) }
                            )
                        }

                        if (selectedTab == 0) {
                            // Active Routines Tab
                            if (activeRoutines.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("You don't have any active routines.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                // Group filter chips if multiple groups exist
                                if (existingGroups.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedGroupFilter == null,
                                            onClick = { selectedGroupFilter = null },
                                            label = { Text("All") }
                                        )
                                        existingGroups.forEach { group ->
                                            FilterChip(
                                                selected = selectedGroupFilter == group,
                                                onClick = {
                                                    selectedGroupFilter = if (selectedGroupFilter == group) null else group
                                                },
                                                label = { Text(group) },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                                                }
                                            )
                                        }
                                    }
                                }

                                val displayedRoutines = if (selectedGroupFilter != null) {
                                    activeRoutines.filter { it.groupName.trim().equals(selectedGroupFilter, ignoreCase = true) }
                                } else {
                                    activeRoutines
                                }

                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (selectedGroupFilter == null && existingGroups.isNotEmpty()) {
                                        // Display grouped with headers
                                        existingGroups.forEach { group ->
                                            val groupItems = displayedRoutines.filter { it.groupName.trim().equals(group, ignoreCase = true) }
                                            if (groupItems.isNotEmpty()) {
                                                item(key = "header_$group") {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Folder,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = group,
                                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = "(${groupItems.size})",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                items(groupItems, key = { it.id }) { routine ->
                                                    RoutineCard(
                                                        routine = routine,
                                                        onClick = { onRoutineSelected(routine) },
                                                        onEditClick = { onRoutineEdit(routine) },
                                                        onChangeGroupClick = {
                                                            groupRoutineTarget = routine
                                                            targetGroupName = routine.groupName
                                                        },
                                                        onArchiveClick = {
                                                            scope.launch {
                                                                viewModel.archiveRoutine(routine.id, true)
                                                                viewModel.loadRoutines()
                                                            }
                                                        },
                                                        onDeleteClick = { pendingDelete = routine }
                                                    )
                                                }
                                            }
                                        }

                                        // Ungrouped items
                                        val ungroupedItems = displayedRoutines.filter { it.groupName.trim().isEmpty() }
                                        if (ungroupedItems.isNotEmpty()) {
                                            item(key = "header_ungrouped") {
                                                Text(
                                                    text = "Other",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                                )
                                            }
                                            items(ungroupedItems, key = { it.id }) { routine ->
                                                RoutineCard(
                                                    routine = routine,
                                                    onClick = { onRoutineSelected(routine) },
                                                    onEditClick = { onRoutineEdit(routine) },
                                                    onChangeGroupClick = {
                                                        groupRoutineTarget = routine
                                                        targetGroupName = routine.groupName
                                                    },
                                                    onArchiveClick = {
                                                        scope.launch {
                                                            viewModel.archiveRoutine(routine.id, true)
                                                            viewModel.loadRoutines()
                                                        }
                                                    },
                                                    onDeleteClick = { pendingDelete = routine }
                                                )
                                            }
                                        }
                                    } else {
                                        // Flat list (or single group filter)
                                        items(displayedRoutines, key = { it.id }) { routine ->
                                            RoutineCard(
                                                routine = routine,
                                                onClick = { onRoutineSelected(routine) },
                                                onEditClick = { onRoutineEdit(routine) },
                                                onChangeGroupClick = {
                                                    groupRoutineTarget = routine
                                                    targetGroupName = routine.groupName
                                                },
                                                onArchiveClick = {
                                                    scope.launch {
                                                        viewModel.archiveRoutine(routine.id, true)
                                                        viewModel.loadRoutines()
                                                    }
                                                },
                                                onDeleteClick = { pendingDelete = routine }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Archived Routines Tab
                            if (archivedRoutines.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No archived routines.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(archivedRoutines, key = { it.id }) { routine ->
                                        RoutineCard(
                                            routine = routine,
                                            onClick = { onRoutineSelected(routine) },
                                            onEditClick = { onRoutineEdit(routine) },
                                            onChangeGroupClick = {
                                                groupRoutineTarget = routine
                                                targetGroupName = routine.groupName
                                            },
                                            onArchiveClick = {
                                                scope.launch {
                                                    viewModel.archiveRoutine(routine.id, false)
                                                    viewModel.loadRoutines()
                                                }
                                            },
                                            onDeleteClick = { pendingDelete = routine }
                                        )
                                    }
                                }
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

    // Dialog: Create Routine
    if (showCreateDialog) {
        val existingGroups = (state as? UiState.Success)?.data
            ?.map { it.groupName.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.sorted() ?: emptyList()

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Routine", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newRoutineName,
                        onValueChange = { newRoutineName = it },
                        label = { Text("Routine name") },
                        placeholder = { Text("e.g. Push A") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newRoutineGroup,
                        onValueChange = { newRoutineGroup = it },
                        label = { Text("Group / Folder (optional)") },
                        placeholder = { Text("e.g. Push Pull Legs") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (existingGroups.isNotEmpty()) {
                        Text("Existing groups:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            existingGroups.forEach { group ->
                                SuggestionChip(
                                    onClick = { newRoutineGroup = group },
                                    label = { Text(group, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newRoutineName.isNotBlank()) {
                        val nameToSave = newRoutineName.trim()
                        val groupToSave = newRoutineGroup.trim().ifEmpty { null }
                        showCreateDialog = false
                        newRoutineName = ""
                        newRoutineGroup = ""
                        scope.launch {
                            val success = viewModel.createRoutine(nameToSave, groupToSave)
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

    // Dialog: Change Group
    groupRoutineTarget?.let { routine ->
        val existingGroups = (state as? UiState.Success)?.data
            ?.map { it.groupName.trim() }
            ?.filter { it.isNotEmpty() && !it.equals(routine.groupName.trim(), ignoreCase = true) }
            ?.distinct()
            ?.sorted() ?: emptyList()

        AlertDialog(
            onDismissRequest = { groupRoutineTarget = null },
            title = { Text("Organize '${routine.name}'", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = targetGroupName,
                        onValueChange = { targetGroupName = it },
                        label = { Text("Group / Folder name") },
                        placeholder = { Text("e.g. Push Pull Legs") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (existingGroups.isNotEmpty()) {
                        Text("Suggested groups:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            existingGroups.forEach { group ->
                                SuggestionChip(
                                    onClick = { targetGroupName = group },
                                    label = { Text(group, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetId = routine.id
                    val gName = targetGroupName.trim()
                    groupRoutineTarget = null
                    scope.launch {
                        viewModel.updateRoutineGroup(targetId, gName)
                        viewModel.loadRoutines()
                    }
                }) { Text("Save Group") }
            },
            dismissButton = {
                if (routine.groupName.isNotBlank()) {
                    TextButton(onClick = {
                        val targetId = routine.id
                        groupRoutineTarget = null
                        scope.launch {
                            viewModel.updateRoutineGroup(targetId, "")
                            viewModel.loadRoutines()
                        }
                    }) { Text("Remove from group", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { groupRoutineTarget = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
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
    onChangeGroupClick: () -> Unit,
    onArchiveClick: () -> Unit,
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
            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (routine.groupName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = routine.groupName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
                            text = { Text(if (routine.groupName.isBlank()) "Assign group" else "Change group", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { expanded = false; onChangeGroupClick() },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (routine.isArchived) "Restore routine" else "Archive routine", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { expanded = false; onArchiveClick() },
                            leadingIcon = {
                                Icon(
                                    if (routine.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
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
