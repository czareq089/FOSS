package com.foss.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.ExerciseInfo
import com.foss.app.models.PlateCalculator
import com.foss.app.models.ReorderPosition
import com.foss.app.models.UserAlgorithmSettings
import com.foss.app.models.UserPlate
import com.foss.app.ui.theme.AccentBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class SetRowState(val setNumber: Int) {
    var weight by mutableStateOf("")
    var reps by mutableStateOf("")
    var rir by mutableStateOf("")
    var confirmed by mutableStateOf(false)
    var submitting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var autoFilled by mutableStateOf(true)
    var setType by mutableStateOf("standard")

    var fallbackWeight by mutableStateOf("")
    var fallbackReps by mutableStateOf("")
    var fallbackRir by mutableStateOf("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLoggingScreen(
    viewModel: WorkoutViewModel,
    workoutId: Int,
    onAddExerciseClick: () -> Unit,
    onExerciseClick: (Int) -> Unit,
    onFinish: () -> Unit,
    onCancelWorkout: () -> Unit
) {
    LaunchedEffect(workoutId) {
        viewModel.resumeWorkout(workoutId)
        viewModel.loadUserPlates()
        viewModel.loadAlgorithmSettings()
    }
    val state = viewModel.workoutState.value
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDiscardEditDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var exerciseToDelete by remember { mutableStateOf<ExerciseInfo?>(null) }
    var cancelling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var activeSetRowForType by remember { mutableStateOf<SetRowState?>(null) }
    var activeExerciseForTimer by remember { mutableStateOf<ExerciseInfo?>(null) }
    var activeExerciseForPlates by remember { mutableStateOf<Pair<ExerciseInfo, Double>?>(null) }

    val exerciseRestTimes = remember { mutableStateMapOf<Int, Int>() }
    val sheetState = rememberModalBottomSheetState()

    var timerRemaining by remember { mutableIntStateOf(0) }
    var timerTotal by remember { mutableIntStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }

    var isMenuExpanded by remember { mutableStateOf(false) }
    var isReordering by remember { mutableStateOf(false) }
    var hasStructureChanged by remember { mutableStateOf(false) }

    val exercises = remember { mutableStateListOf<ExerciseInfo>() }
    var initialExercisesBackup by remember { mutableStateOf<List<ExerciseInfo>>(emptyList()) }

    fun hasPendingEdits(): Boolean {
        if (exercises.size != initialExercisesBackup.size) return true
        return exercises.map { it.workoutExerciseId } != initialExercisesBackup.map { it.workoutExerciseId }
    }

    LaunchedEffect(state) {
        if (state is UiState.Success) {
            val incoming = state.data.third
            if (exercises.isEmpty()) {
                exercises.addAll(incoming)
            } else {
                val existingIds = exercises.map { it.workoutExerciseId }.toSet()
                val newItems = incoming.filter { it.workoutExerciseId !in existingIds }
                if (newItems.isNotEmpty()) {
                    exercises.addAll(newItems)
                    hasStructureChanged = true
                }
            }
        }
    }

    LaunchedEffect(isTimerRunning, timerRemaining) {
        if (isTimerRunning && timerRemaining > 0) {
            delay(1000L)
            timerRemaining -= 1
            if (timerRemaining <= 0) { isTimerRunning = false }
        }
    }

    BackHandler {
        if (isReordering) {
            if (hasPendingEdits()) {
                showDiscardEditDialog = true
            } else {
                isReordering = false
            }
        } else {
            showCancelDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isReordering) "Edit exercises" else "Log workout") },
                navigationIcon = {
                    val closeSource = remember { MutableInteractionSource() }
                    val isClosePressed by closeSource.collectIsPressedAsState()

                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(36.dp)
                            .alpha(if (isClosePressed) 0.4f else 1f)
                            .clickable(
                                interactionSource = closeSource,
                                indication = null
                            ) {
                                if (isReordering) {
                                    if (hasPendingEdits()) {
                                        showDiscardEditDialog = true
                                    } else {
                                        isReordering = false
                                    }
                                } else {
                                    showCancelDialog = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = if (isReordering) "Discard edits" else "Cancel workout", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (isReordering) {
                        val checkSource = remember { MutableInteractionSource() }
                        val isCheckPressed by checkSource.collectIsPressedAsState()

                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(36.dp)
                                .alpha(if (isCheckPressed) 0.4f else 1f)
                                .clickable(
                                    interactionSource = checkSource,
                                    indication = null
                                ) {
                                    scope.launch {
                                        val wId = viewModel.currentWorkoutId() ?: return@launch
                                        val positions = exercises.mapIndexed { index, ex -> ReorderPosition(exerciseId = ex.exerciseId, position = index + 1) }
                                        viewModel.reorderWorkoutExercises(wId, positions)
                                        viewModel.setWorkoutExercises(exercises.toList())
                                        hasStructureChanged = true
                                        isReordering = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Done", tint = AccentBlue)
                        }
                    } else {
                        Box {
                            val settingsSource = remember { MutableInteractionSource() }
                            val isSettingsPressed by settingsSource.collectIsPressedAsState()

                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(36.dp)
                                    .alpha(if (isSettingsPressed) 0.4f else 1f)
                                    .clickable(
                                        interactionSource = settingsSource,
                                        indication = null
                                    ) { isMenuExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = MaterialTheme.colorScheme.surfaceVariant)) {
                                DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    DropdownMenuItem(
                                        text = { Text("Edit exercises", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            isMenuExpanded = false
                                            initialExercisesBackup = exercises.toList()
                                            isReordering = true
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isReordering) {
                Button(
                    onClick = {
                        if (hasStructureChanged) showSyncDialog = true else onFinish()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Finish workout")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state is UiState.Success) {
                var draggedIndex by remember { mutableStateOf<Int?>(null) }
                var targetIndex by remember { mutableStateOf<Int?>(null) }
                var dragOffsetY by remember { mutableFloatStateOf(0f) }
                var initialItemOffset by remember { mutableIntStateOf(0) }
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(items = exercises, key = { _, exercise -> exercise.workoutExerciseId }) { index, exercise ->
                        val currentRestTime = exerciseRestTimes[exercise.workoutExerciseId] ?: 90

                        val isDragged = draggedIndex == index
                        val isTarget = targetIndex == index && draggedIndex != index
                        val isMovingDown = targetIndex != null && draggedIndex != null && targetIndex!! > draggedIndex!!
                        val zIndex = if (isDragged) 10f else 0f
                        val elevation by animateDpAsState(if (isDragged) 16.dp else 0.dp, label = "elev")

                        val currentOffset = listState.layoutInfo.visibleItemsInfo.find { it.index == index }?.offset ?: 0
                        val compensationOffset = if (isDragged) (initialItemOffset - currentOffset).toFloat() else 0f

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(zIndex)
                        ) {
                            if (isTarget && !isMovingDown && isReordering) {
                                Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                                Spacer(Modifier.height(4.dp))
                            }

                            OutlinedCard(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.outlinedCardElevation(defaultElevation = elevation),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        if (isDragged) {
                                            translationY = dragOffsetY + compensationOffset
                                            scaleX = 1.02f
                                            scaleY = 1.02f
                                            alpha = 1f
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isReordering) {
                                                Icon(
                                                    Icons.Filled.DragHandle,
                                                    contentDescription = "Drag to reorder",
                                                    tint = if (isDragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .pointerInput(exercise, index) {
                                                            detectVerticalDragGestures(
                                                                onDragStart = {
                                                                    draggedIndex = index
                                                                    targetIndex = index
                                                                    dragOffsetY = 0f
                                                                    initialItemOffset = listState.layoutInfo.visibleItemsInfo.find { it.index == index }?.offset ?: 0
                                                                },
                                                                onDragEnd = {
                                                                    if (draggedIndex != null && targetIndex != null && draggedIndex != targetIndex) {
                                                                        val from = draggedIndex!!.coerceIn(0, exercises.size - 1)
                                                                        val to = targetIndex!!.coerceIn(0, exercises.size - 1)
                                                                        val moved = exercises.removeAt(from)
                                                                        val safeInsert = to.coerceIn(0, exercises.size)
                                                                        exercises.add(safeInsert, moved)
                                                                    }
                                                                    draggedIndex = null
                                                                    targetIndex = null
                                                                    dragOffsetY = 0f
                                                                },
                                                                onDragCancel = {
                                                                    draggedIndex = null
                                                                    targetIndex = null
                                                                    dragOffsetY = 0f
                                                                },
                                                                onVerticalDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    dragOffsetY += dragAmount

                                                                    val currentDragged = draggedIndex ?: return@detectVerticalDragGestures
                                                                    val layoutInfo = listState.layoutInfo
                                                                    val draggedItemInfo = layoutInfo.visibleItemsInfo.find { it.index == currentDragged }

                                                                    if (draggedItemInfo != null) {
                                                                        val draggedHeight = draggedItemInfo.size
                                                                        val centerOnScreen = initialItemOffset + dragOffsetY + (draggedHeight / 2f)

                                                                        val target = layoutInfo.visibleItemsInfo.find {
                                                                            it.index < exercises.size &&
                                                                                    centerOnScreen > it.offset &&
                                                                                    centerOnScreen < it.offset + it.size
                                                                        }
                                                                        if (target != null) {
                                                                            targetIndex = target.index
                                                                        }
                                                                    }
                                                                }
                                                            )
                                                        }
                                                        .padding(4.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }

                                            val titleInteractionSource = remember { MutableInteractionSource() }
                                            val isTitlePressed by titleInteractionSource.collectIsPressedAsState()

                                            Text(
                                                text = exercise.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier
                                                    .clickable(
                                                        interactionSource = titleInteractionSource,
                                                        indication = null,
                                                        enabled = !isReordering
                                                    ) { onExerciseClick(exercise.exerciseId) }
                                                    .alpha(if (isTitlePressed && !isReordering) 0.4f else 1f)
                                            )
                                        }

                                        if (isReordering) {
                                            val deleteExSource = remember { MutableInteractionSource() }
                                            val isDeleteExPressed by deleteExSource.collectIsPressedAsState()

                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .alpha(if (isDeleteExPressed) 0.4f else 1f)
                                                    .clickable(
                                                        interactionSource = deleteExSource,
                                                        indication = null
                                                    ) {
                                                        exerciseToDelete = exercise
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete exercise", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = !isDragged && !isReordering,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        ExerciseLogContent(
                                            viewModel = viewModel,
                                            exercise = exercise,
                                            restSeconds = currentRestTime,
                                            onRestTimeClick = { activeExerciseForTimer = exercise },
                                            onPlateCalculatorClick = { currentWeight ->
                                                activeExerciseForPlates = Pair(exercise, currentWeight)
                                            },
                                            onStartTimer = { seconds -> timerTotal = seconds; timerRemaining = seconds; isTimerRunning = true },
                                            onCancelTimer = { timerTotal = 0; timerRemaining = 0; isTimerRunning = false },
                                            onOpenSetType = { row -> activeSetRowForType = row }
                                        )
                                    }
                                }
                            }

                            if (isTarget && isMovingDown && isReordering) {
                                Spacer(Modifier.height(4.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                            }
                        }
                    }

                    if (!isReordering) {
                        item {
                            OutlinedButton(onClick = onAddExerciseClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text("Add exercise to active workout", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                Text("No active workout.", modifier = Modifier.align(Alignment.Center))
            }

            if (isTimerRunning || timerRemaining > 0) {
                OutlinedCard(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(0.85f).align(Alignment.BottomCenter).padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { timerRemaining = maxOf(0, timerRemaining - 30) }) { Text("-30s", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(String.format(Locale.US, "%02d:%02d", timerRemaining / 60, timerRemaining % 60), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { timerRemaining += 30; timerTotal += 30 }) { Text("+30s", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        LinearProgressIndicator(progress = { if (timerTotal > 0) timerRemaining.toFloat() / timerTotal else 0f }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }
    }

    if (showDiscardEditDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardEditDialog = false },
            title = { Text("Discard edits?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Are you sure you want to discard changes made to exercises?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        exercises.clear()
                        exercises.addAll(initialExercisesBackup)
                        isReordering = false
                        showDiscardEditDialog = false
                    }
                ) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardEditDialog = false }) {
                    Text("Keep editing", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
    }

    if (exerciseToDelete != null) {
        AlertDialog(
            onDismissRequest = { exerciseToDelete = null },
            title = { Text("Remove exercise?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Are you sure you want to remove \"${exerciseToDelete?.name}\" from this workout session?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ex = exerciseToDelete
                        if (ex != null) {
                            exercises.removeAll { it.workoutExerciseId == ex.workoutExerciseId }
                        }
                        exerciseToDelete = null
                    }
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { exerciseToDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
    }

    if (activeSetRowForType != null) {
        ModalBottomSheet(onDismissRequest = { activeSetRowForType = null }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
            val row = activeSetRowForType!!
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                val updateType = { type: String ->
                    row.setType = type
                    activeSetRowForType = null
                }
                SetTypeOption("standard", "Standard set", null, Color.White) { updateType("standard") }
                SetTypeOption("warmup", "Warmup set", "W", Color(0xFFFFC107)) { updateType("warmup") }
                SetTypeOption("failure", "Failure set", "F", Color(0xFFEF4444)) { updateType("failure") }
                SetTypeOption("drop", "Drop set", "D", Color(0xFF3B82F6)) { updateType("drop") }
                SetTypeOption("back_off", "Back-off set", "B", Color(0xFF34D399)) { updateType("back_off") }
            }
        }
    }

    if (activeExerciseForPlates != null) {
        val (exercise, initialWeight) = activeExerciseForPlates!!
        val plates = (viewModel.userPlatesState.value as? UiState.Success)?.data ?: emptyList()
        PlateMathBottomSheet(
            exerciseName = exercise.name,
            initialWeight = initialWeight,
            plates = plates,
            onDismiss = { activeExerciseForPlates = null }
        )
    }

    if (activeExerciseForTimer != null) {
        val exercise = activeExerciseForTimer!!
        var tempSeconds by remember { mutableIntStateOf(exerciseRestTimes[exercise.workoutExerciseId] ?: 90) }
        var dragAccumulator by remember { mutableFloatStateOf(0f) }

        ModalBottomSheet(onDismissRequest = { activeExerciseForTimer = null }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Set Rest Timer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulator += dragAmount
                                if (dragAccumulator > 40f) { if (tempSeconds >= 15) tempSeconds -= 15; dragAccumulator = 0f }
                                else if (dragAccumulator < -40f) { tempSeconds += 15; dragAccumulator = 0f }
                            },
                            onDragEnd = { dragAccumulator = 0f }
                        )
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = if (tempSeconds >= 15) String.format(Locale.US, "%02d:%02d", (tempSeconds - 15) / 60, (tempSeconds - 15) % 60) else " ", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (tempSeconds >= 15) tempSeconds -= 15 }.padding(vertical = 12.dp), textAlign = TextAlign.Center)
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(String.format(Locale.US, "%02d:%02d", tempSeconds / 60, tempSeconds % 60), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(String.format(Locale.US, "%02d:%02d", (tempSeconds + 15) / 60, (tempSeconds + 15) % 60), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tempSeconds += 15 }.padding(vertical = 12.dp), textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = { exerciseRestTimes[exercise.workoutExerciseId] = tempSeconds; activeExerciseForTimer = null }, modifier = Modifier.fillMaxWidth(0.8f), shape = RoundedCornerShape(8.dp)) { Text("Save Timer") }
            }
        }
    }

    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false; onFinish() },
            title = { Text("Update original routine?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("You changed the exercises or their order. Do you want to update the original routine template for the future?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val rId = viewModel.currentRoutineId()
                            val wId = viewModel.currentWorkoutId()
                            if (rId != null && wId != null) {
                                viewModel.syncRoutineFromWorkout(rId, wId)
                            }
                            showSyncDialog = false
                            onFinish()
                        }
                    }
                ) { Text("Update Routine", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showSyncDialog = false; onFinish() }) {
                    Text("No, just finish", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { if (!cancelling) showCancelDialog = false },
            title = { Text("Cancel this workout?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Any sets you've already logged will be deleted.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    enabled = !cancelling,
                    onClick = {
                        val wId = viewModel.currentWorkoutId() ?: return@TextButton
                        cancelling = true
                        scope.launch { viewModel.deleteWorkout(wId); showCancelDialog = false; onCancelWorkout() }
                    }
                ) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !cancelling, onClick = { showCancelDialog = false }) { Text("Keep going", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

private fun formatPlateWeight(weight: Double): String {
    return if (weight % 1.0 == 0.0) {
        weight.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", weight).trimEnd('0').trimEnd('.')
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlateMathBottomSheet(
    exerciseName: String,
    initialWeight: Double,
    plates: List<UserPlate>,
    onDismiss: () -> Unit
) {
    val smallestPlate = plates.filter { it.count > 0 }.minByOrNull { it.weightKg }?.weightKg
    val minStep = if (smallestPlate != null && smallestPlate > 0.0) smallestPlate else 1.25

    var weightText by remember {
        mutableStateOf(if (initialWeight > 0.0) formatPlateWeight(initialWeight) else "0")
    }

    val currentWeight = weightText.toDoubleOrNull() ?: 0.0

    val breakdown = remember(currentWeight, plates) {
        PlateCalculator.calculatePlatesPerSide(currentWeight, plates)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(exerciseName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val next = maxOf(0.0, currentWeight - minStep)
                        weightText = formatPlateWeight(next)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("-${formatPlateWeight(minStep)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.width(12.dp))

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' } },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(130.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(Modifier.width(12.dp))

                OutlinedButton(
                    onClick = {
                        val next = currentWeight + minStep
                        weightText = formatPlateWeight(next)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("+${formatPlateWeight(minStep)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "Each side: ${String.format(Locale.US, "%.2f", breakdown.perSideWeight).replace(".00", "")} kg",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            if (breakdown.platesPerSide.isEmpty() && breakdown.extraSinglePlate == null) {
                Text(
                    text = "No plates needed (0 kg)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                if (breakdown.platesPerSide.isNotEmpty()) {
                    Text(
                        text = "Plates on each side:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        breakdown.platesPerSide.forEach { p ->
                            Surface(
                                shape = CircleShape,
                                color = AccentBlue.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AccentBlue),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = formatPlateWeight(p),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentBlue
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (breakdown.extraSinglePlate != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Extra single plate (Center / Pin):",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFC107),
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFC107).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFFFC107)),
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Start)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = formatPlateWeight(breakdown.extraSinglePlate),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFC107)
                            )
                        }
                    }
                }
            }

            if (breakdown.remainder > 0.05) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Missing ${formatPlateWeight(breakdown.remainder)} kg (out of smaller plates)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ExerciseLogContent(
    viewModel: WorkoutViewModel,
    exercise: ExerciseInfo,
    restSeconds: Int,
    onRestTimeClick: () -> Unit,
    onPlateCalculatorClick: (Double) -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onOpenSetType: (SetRowState) -> Unit
) {
    val scope = rememberCoroutineScope()

    val userPlates = (viewModel.userPlatesState.value as? UiState.Success)?.data ?: emptyList()
    val algoSettings = (viewModel.algorithmSettingsState.value as? UiState.Success)?.data ?: UserAlgorithmSettings()

    val sets = remember(exercise.workoutExerciseId) {
        val initialList = mutableStateListOf<SetRowState>()
        val templateCount = exercise.templateSets?.size ?: 0
        val lastCount = exercise.lastSets?.size ?: 0
        val totalCount = maxOf(1, maxOf(templateCount, lastCount))

        val workingSets = exercise.lastSets?.filter { it.setNumber > 0 } ?: emptyList()
        val baselineWeight = if (algoSettings.warmupBase == "heaviest_set") {
            workingSets.maxOfOrNull { it.weightKg } ?: 0.0
        } else {
            workingSets.firstOrNull()?.weightKg ?: 0.0
        }

        val warmupTemplates = exercise.templateSets?.filter { it.setType == "warmup" } ?: emptyList()
        val totalWarmups = warmupTemplates.size

        for (i in 1..totalCount) {
            val template = exercise.templateSets?.find { it.setNumber == i }
            val prevSet = exercise.lastSets?.find { it.setNumber == i }
            val currentType = template?.setType ?: "standard"

            var calcWeight = prevSet?.weightKg ?: 0.0
            var calcReps = prevSet?.reps ?: 0
            var calcRir = prevSet?.rir ?: 0

            when (currentType) {
                "warmup" -> {
                    if (algoSettings.warmupEnabled && baselineWeight > 0.0) {
                        val warmupIndex = warmupTemplates.indexOf(template) + 1
                        val fraction = if (totalWarmups <= 1) {
                            0.60
                        } else {
                            0.50 + (0.35 * ((warmupIndex - 1).toDouble() / maxOf(1, totalWarmups - 1)))
                        }
                        val targetRaw = baselineWeight * fraction
                        calcWeight = PlateCalculator.findClosestAchievableWeight(targetRaw, userPlates)
                        calcReps = if (totalWarmups <= 1) 5 else maxOf(1, 6 - (warmupIndex * 2) + 1)
                        calcRir = 5
                    }
                }
                "drop" -> {
                    if (algoSettings.dropEnabled && baselineWeight > 0.0) {
                        val targetRaw = baselineWeight * (1.0 - (algoSettings.dropPercentage / 100.0))
                        calcWeight = PlateCalculator.findClosestAchievableWeight(targetRaw, userPlates)
                        calcRir = 0
                    }
                }
                "back_off" -> {
                    if (algoSettings.backoffEnabled && baselineWeight > 0.0) {
                        val targetRaw = baselineWeight * (1.0 - (algoSettings.backoffPercentage / 100.0))
                        calcWeight = PlateCalculator.findClosestAchievableWeight(targetRaw, userPlates)
                        calcRir = 1
                    }
                }
            }

            initialList.add(SetRowState(i).apply {
                this.setType = currentType
                if (calcWeight > 0.0) {
                    this.fallbackWeight = if (calcWeight % 1.0 == 0.0) calcWeight.toInt().toString() else calcWeight.toString()
                }
                if (calcReps > 0) {
                    this.fallbackReps = calcReps.toString()
                }
                if (calcRir >= 0 && (prevSet != null || currentType != "standard")) {
                    this.fallbackRir = calcRir.toString()
                }
            })
        }
        initialList
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

    fun resolveTargetPlateWeight(): Double {
        val lastConfirmedWeight = sets.filter { it.confirmed }.lastOrNull()?.weight?.toDoubleOrNull()
        if (lastConfirmedWeight != null && lastConfirmedWeight > 0.0) return lastConfirmedWeight

        val activeRowWeight = sets.firstOrNull { !it.confirmed }?.weight?.toDoubleOrNull()
        if (activeRowWeight != null && activeRowWeight > 0.0) return activeRowWeight

        val firstRowFallback = sets.firstOrNull()?.fallbackWeight?.toDoubleOrNull()
        if (firstRowFallback != null && firstRowFallback > 0.0) return firstRowFallback

        val lastTrainingWeight = exercise.lastSets?.firstOrNull()?.weightKg
        if (lastTrainingWeight != null && lastTrainingWeight > 0.0) return lastTrainingWeight

        return 0.0
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val timerInteractionSource = remember { MutableInteractionSource() }
        val isTimerPressed by timerInteractionSource.collectIsPressedAsState()

        val plateInteractionSource = remember { MutableInteractionSource() }
        val isPlatePressed by plateInteractionSource.collectIsPressedAsState()

        Row(
            modifier = Modifier
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clickable(interactionSource = timerInteractionSource, indication = null) { onRestTimeClick() }
                    .alpha(if (isTimerPressed) 0.4f else 1f)
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Timer, contentDescription = "Rest", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(String.format(Locale.US, "%02d:%02d", restSeconds / 60, restSeconds % 60), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier
                    .clickable(interactionSource = plateInteractionSource, indication = null) {
                        onPlateCalculatorClick(resolveTargetPlateWeight())
                    }
                    .alpha(if (isPlatePressed) 0.4f else 1f)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Balance, contentDescription = "Plates", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Set", modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Weight", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Reps", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("RIR", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(0.9f))
        }

        Spacer(Modifier.height(8.dp))

        sets.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val typeColor = when(row.setType) {
                    "warmup" -> Color(0xFFFFC107); "failure" -> Color(0xFFEF4444); "back_off" -> Color(0xFF34D399); "drop" -> Color(0xFF3B82F6); else -> MaterialTheme.colorScheme.onSurface
                }
                val typeInteractionSource = remember { MutableInteractionSource() }
                val isTypePressed by typeInteractionSource.collectIsPressedAsState()

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(0.6f)
                        .clickable(interactionSource = typeInteractionSource, indication = null, enabled = !row.confirmed) { onOpenSetType(row) }
                        .alpha(if (isTypePressed) 0.4f else 1f)
                        .padding(vertical = 8.dp)
                ) {
                    Text("${row.setNumber}", color = typeColor, style = MaterialTheme.typography.titleMedium)
                }

                OutlinedTextField(
                    value = row.weight,
                    onValueChange = { row.weight = it.filter { c -> c.isDigit() || c == '.' }; if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false },
                    enabled = !row.confirmed && !row.submitting,
                    singleLine = true,
                    placeholder = { if (row.fallbackWeight.isNotEmpty()) Text(row.fallbackWeight, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = row.reps,
                    onValueChange = { row.reps = it.filter(Char::isDigit); if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false },
                    enabled = !row.confirmed && !row.submitting,
                    singleLine = true,
                    placeholder = { if (row.fallbackReps.isNotEmpty()) Text(row.fallbackReps, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = row.rir,
                    onValueChange = { row.rir = it.filter(Char::isDigit); if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false },
                    enabled = !row.confirmed && !row.submitting,
                    singleLine = true,
                    placeholder = { if (row.fallbackRir.isNotEmpty()) Text(row.fallbackRir, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(modifier = Modifier.weight(0.9f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (row.submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        val checkRowSource = remember { MutableInteractionSource() }
                        val isCheckRowPressed by checkRowSource.collectIsPressedAsState()

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (row.confirmed) AccentBlue else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (row.confirmed) AccentBlue else MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .size(28.dp)
                                .alpha(if (isCheckRowPressed) 0.4f else 1f)
                                .clickable(
                                    interactionSource = checkRowSource,
                                    indication = null
                                ) {
                                    if (!row.confirmed) {
                                        if (row.weight.isEmpty() && row.fallbackWeight.isNotEmpty()) row.weight = row.fallbackWeight
                                        if (row.reps.isEmpty() && row.fallbackReps.isNotEmpty()) row.reps = row.fallbackReps
                                        if (row.rir.isEmpty() && row.fallbackRir.isNotEmpty()) row.rir = row.fallbackRir

                                        val reps = row.reps.toIntOrNull() ?: 0
                                        val weight = row.weight.toDoubleOrNull() ?: 0.0
                                        row.error = null

                                        row.confirmed = true
                                        row.submitting = true

                                        val nextIndex = rowIndex + 1
                                        val isNextSetDrop = if (nextIndex < sets.size) {
                                            sets[nextIndex].setType == "drop"
                                        } else false

                                        if (isNextSetDrop) {
                                            onCancelTimer()
                                        } else {
                                            onStartTimer(restSeconds)
                                        }

                                        val rir = row.rir.toIntOrNull() ?: 0
                                        scope.launch {
                                            val success = viewModel.logSet(exercise.workoutExerciseId, row.setNumber, reps, weight, rir, row.setType)
                                            row.submitting = false
                                            if (success) {
                                                if (sets.last() === row) { sets.add(SetRowState(row.setNumber + 1)) }
                                            } else {
                                                row.confirmed = false
                                                row.error = "Failed"
                                            }
                                        }
                                    } else {
                                        row.confirmed = false
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (row.confirmed) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Confirmed",
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (!row.confirmed && sets.size > 1) {
                            val deleteSource = remember { MutableInteractionSource() }
                            val isDeletePressed by deleteSource.collectIsPressedAsState()

                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(24.dp)
                                    .alpha(if (isDeletePressed) 0.4f else 1f)
                                    .clickable(
                                        interactionSource = deleteSource,
                                        indication = null
                                    ) { sets.remove(row) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = { sets.add(SetRowState((sets.maxOfOrNull { it.setNumber } ?: 0) + 1)) },
            modifier = Modifier.align(Alignment.End)
        ) { Text("+ Add set", color = MaterialTheme.colorScheme.primary) }
    }
}