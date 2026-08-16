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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.ExerciseInfo
import com.foss.app.models.ReorderPosition
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
    onAddExerciseClick: () -> Unit,
    onExerciseClick: (Int) -> Unit,
    onFinish: () -> Unit,
    onCancelWorkout: () -> Unit
) {
    val state = viewModel.workoutState.value
    var showCancelDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var activeSetRowForType by remember { mutableStateOf<SetRowState?>(null) }
    var activeExerciseForTimer by remember { mutableStateOf<ExerciseInfo?>(null) }
    val exerciseRestTimes = remember { mutableStateMapOf<Int, Int>() }
    val sheetState = rememberModalBottomSheetState()

    var timerRemaining by remember { mutableIntStateOf(0) }
    var timerTotal by remember { mutableIntStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }

    var isMenuExpanded by remember { mutableStateOf(false) }
    var isReordering by remember { mutableStateOf(false) }
    var hasStructureChanged by remember { mutableStateOf(false) }

    val exercises = remember { mutableStateListOf<ExerciseInfo>() }

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

    BackHandler { showCancelDialog = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log workout") },
                navigationIcon = { IconButton(onClick = { showCancelDialog = true }) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } },
                actions = {
                    if (isReordering) {
                        IconButton(onClick = {
                            scope.launch {
                                val wId = viewModel.currentWorkoutId() ?: return@launch
                                val positions = exercises.mapIndexed { index, ex -> ReorderPosition(exerciseId = ex.exerciseId, position = index + 1) }
                                viewModel.reorderWorkoutExercises(wId, positions)
                                viewModel.setWorkoutExercises(exercises.toList())
                                hasStructureChanged = true
                                isReordering = false
                            }
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "Done")
                        }
                    } else {
                        Box {
                            IconButton(onClick = { isMenuExpanded = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = MaterialTheme.colorScheme.surfaceVariant)) {
                                DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    DropdownMenuItem(
                                        text = { Text("Reorder exercises", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = { isMenuExpanded = false; isReordering = true },
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
                                        modifier = Modifier.fillMaxWidth()
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
                                                                    val from = draggedIndex!!
                                                                    val safeTo = targetIndex!!.coerceIn(0, exercises.size - 1)
                                                                    val moved = exercises.removeAt(from)
                                                                    exercises.add(safeTo, moved)
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
                                            onStartTimer = { seconds -> timerTotal = seconds; timerRemaining = seconds; isTimerRunning = true },
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

@Composable
private fun ExerciseLogContent(
    viewModel: WorkoutViewModel,
    exercise: ExerciseInfo,
    restSeconds: Int,
    onRestTimeClick: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onOpenSetType: (SetRowState) -> Unit
) {
    val scope = rememberCoroutineScope()

    val sets = remember(exercise.workoutExerciseId) {
        val initialList = mutableStateListOf<SetRowState>()
        val templateCount = exercise.templateSets?.size ?: 0
        val lastCount = exercise.lastSets?.size ?: 0
        val totalCount = maxOf(1, maxOf(templateCount, lastCount))

        for (i in 1..totalCount) {
            val template = exercise.templateSets?.find { it.setNumber == i }
            val prevSet = exercise.lastSets?.find { it.setNumber == i }

            initialList.add(SetRowState(i).apply {
                this.setType = template?.setType ?: "standard"
                if (prevSet != null) {
                    this.fallbackWeight = if (prevSet.weightKg % 1.0 == 0.0) prevSet.weightKg.toInt().toString() else prevSet.weightKg.toString()
                    this.fallbackReps = prevSet.reps.toString()
                    this.fallbackRir = prevSet.rir.toString()
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

    Column(modifier = Modifier.fillMaxWidth()) {
        val timerInteractionSource = remember { MutableInteractionSource() }
        val isTimerPressed by timerInteractionSource.collectIsPressedAsState()

        Row(
            modifier = Modifier
                .padding(top = 4.dp, bottom = 12.dp)
                .clickable(interactionSource = timerInteractionSource, indication = null) { onRestTimeClick() }
                .alpha(if (isTimerPressed) 0.4f else 1f)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Timer, contentDescription = "Rest", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(String.format(Locale.US, "%02d:%02d", restSeconds / 60, restSeconds % 60), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
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

                Row(modifier = Modifier.weight(0.9f), verticalAlignment = Alignment.CenterVertically) {
                    if (row.submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Checkbox(
                            checked = row.confirmed,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (row.weight.isEmpty() && row.fallbackWeight.isNotEmpty()) row.weight = row.fallbackWeight
                                    if (row.reps.isEmpty() && row.fallbackReps.isNotEmpty()) row.reps = row.fallbackReps
                                    if (row.rir.isEmpty() && row.fallbackRir.isNotEmpty()) row.rir = row.fallbackRir

                                    val reps = row.reps.toIntOrNull() ?: 0
                                    val weight = row.weight.toDoubleOrNull() ?: 0.0
                                    row.error = null

                                    row.confirmed = true
                                    row.submitting = true
                                    onStartTimer(restSeconds)

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
                                } else row.confirmed = false
                            }
                        )
                        if (!row.confirmed && sets.size > 1) {
                            IconButton(onClick = { sets.remove(row) }, modifier = Modifier.size(28.dp)) {
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