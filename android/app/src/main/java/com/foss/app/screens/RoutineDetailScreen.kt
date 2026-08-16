package com.foss.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.ReorderPosition
import com.foss.app.models.RoutineExercisePreview
import com.foss.app.models.RoutineSet
import com.foss.app.ui.theme.AccentBlue
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    viewModel: WorkoutViewModel,
    routineId: Int,
    startInEditMode: Boolean = false,
    onStartWorkout: (workoutId: Int) -> Unit,
    onAddExerciseClick: () -> Unit,
    onExerciseClick: (exerciseId: Int) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(routineId) {
        viewModel.resetWorkoutState()
        viewModel.loadRoutineExercises(routineId)
        viewModel.loadRoutineAnalytics(routineId)
    }

    val previewState = viewModel.routineExercisesState.value
    val analyticsState = viewModel.routineAnalyticsState.value
    val startState = viewModel.workoutState.value
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    var activeSetForType by remember { mutableStateOf<Pair<RoutineExercisePreview, Int>?>(null) }

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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        if (editMode) {
                            scope.launch {
                                val positions = exercises.mapIndexed { index, ex -> ReorderPosition(exerciseId = ex.exerciseId, position = index + 1) }
                                viewModel.reorderExercises(routineId, positions)
                                exercises.forEach { ex ->
                                    val safeSets = ex.mutableTemplateSets.mapIndexed { i, s -> RoutineSet(i + 1, s.setType) }
                                    viewModel.updateRoutineSets(ex.routineExerciseId, safeSets)
                                }
                                viewModel.loadRoutineExercises(routineId)
                                viewModel.loadRoutineAnalytics(routineId)
                                editMode = false
                            }
                        } else editMode = true
                    }) {
                        Icon(if (editMode) Icons.Filled.Check else Icons.Filled.Edit, contentDescription = "Edit/Save")
                    }
                }
            )
        },
        bottomBar = {
            if (!editMode) {
                Surface(shadowElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { viewModel.startWorkout(routineId) },
                            enabled = startState !is UiState.Loading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (startState is UiState.Loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text("Start workout")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (previewState) {
                is UiState.Loading, UiState.Idle -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is UiState.Error -> { }
                is UiState.Success -> {
                    if (editMode) {
                        ReorderableExerciseList(
                            routineId = routineId,
                            viewModel = viewModel,
                            exercises = exercises,
                            onAddExerciseClick = onAddExerciseClick,
                            onOpenSetType = { ex, index -> activeSetForType = Pair(ex, index) }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                RoutineVolumeChartCard(analyticsState)
                            }

                            item {
                                Text(
                                    text = "Exercises",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )
                            }

                            itemsIndexed(
                                items = exercises,
                                key = { _, ex -> ex.exerciseId }
                            ) { _, exercise ->
                                ExercisePreviewCard(
                                    exercise = exercise,
                                    onExerciseClick = { onExerciseClick(exercise.exerciseId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (activeSetForType != null) {
            ModalBottomSheet(
                onDismissRequest = { activeSetForType = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val (ex, index) = activeSetForType!!
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    val updateType = { type: String ->
                        ex.mutableTemplateSets[index] = ex.mutableTemplateSets[index].copy(setType = type)
                        activeSetForType = null
                    }
                    SetTypeOption("standard", "Standard set", null, Color.White) { updateType("standard") }
                    SetTypeOption("warmup", "Warmup set", "W", Color(0xFFFFC107)) { updateType("warmup") }
                    SetTypeOption("failure", "Failure set", "F", Color(0xFFEF4444)) { updateType("failure") }
                    SetTypeOption("drop", "Drop set", "D", Color(0xFF3B82F6)) { updateType("drop") }
                    SetTypeOption("back_off", "Back-off set", "B", Color(0xFF34D399)) { updateType("back_off") }
                }
            }
        }
    }
}

@Composable
private fun RoutineVolumeChartCard(state: UiState<com.foss.app.models.RoutineAnalyticsResponse>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val history = (state as? UiState.Success)?.data?.history ?: emptyList()

    var selectedPointIndex by remember(history) {
        mutableIntStateOf(if (history.isNotEmpty()) history.size - 1 else -1)
    }

    LaunchedEffect(history) {
        if (history.isNotEmpty()) {
            val rawValues: List<Float> = history.map { it.volumeKg.toFloat() }
            val baseSeries = if (rawValues.size == 1) listOf(0f, rawValues[0]) else rawValues
            val maxVal = baseSeries.maxOrNull() ?: 0f
            val targetCeiling = if (maxVal > 0f) maxVal * 2.0f else 10f
            val ceilingSeries = List(baseSeries.size) { targetCeiling }

            withContext(Dispatchers.Default) {
                modelProducer.runTransaction {
                    lineSeries {
                        series(baseSeries)
                        series(ceilingSeries)
                    }
                }
            }
        }
    }

    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Volume Trend (Total kg)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            when {
                state is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                history.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No logged workouts for this routine yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val activePoint = history.getOrNull(selectedPointIndex) ?: history.last()
                    Text(
                        text = String.format(Locale.US, "%.1f kg", activePoint.volumeKg),
                        style = MaterialTheme.typography.headlineMedium,
                        color = AccentBlue
                    )
                    Text(
                        text = activePoint.date.take(10),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    var chartWidth by remember { mutableIntStateOf(1) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .onSizeChanged { chartWidth = maxOf(1, it.width) }
                            .pointerInput(history) {
                                detectTapGestures { offset ->
                                    if (history.isNotEmpty()) {
                                        val step = chartWidth.toFloat() / history.size
                                        val index = (offset.x / step).toInt().coerceIn(0, history.size - 1)
                                        selectedPointIndex = index
                                    }
                                }
                            }
                            .pointerInput(history) {
                                detectDragGestures { change, _ ->
                                    if (history.isNotEmpty()) {
                                        val step = chartWidth.toFloat() / history.size
                                        val index = (change.position.x / step).toInt().coerceIn(0, history.size - 1)
                                        selectedPointIndex = index
                                    }
                                }
                            }
                    ) {
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                rememberLineCartesianLayer(
                                    lineProvider = LineCartesianLayer.LineProvider.series(
                                        rememberLine(
                                            fill = LineCartesianLayer.LineFill.single(fill(AccentBlue)),
                                            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0f),
                                            areaFill = null
                                        ),
                                        rememberLine(
                                            fill = LineCartesianLayer.LineFill.single(fill(Color.Transparent)),
                                            thickness = 0.dp,
                                            areaFill = null
                                        )
                                    )
                                ),
                                startAxis = rememberStartAxis(),
                                bottomAxis = rememberBottomAxis()
                            ),
                            modelProducer = modelProducer,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetTypeOption(type: String, label: String, letter: String?, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 16.dp, horizontal = 24.dp)
    ) {
        if (letter != null) Text(text = letter, color = color, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterStart))
        Text(text = label, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ExercisePreviewCard(
    exercise: RoutineExercisePreview,
    onExerciseClick: () -> Unit
) {
    val titleInteractionSource = remember { MutableInteractionSource() }
    val isTitlePressed by titleInteractionSource.collectIsPressedAsState()

    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable(
                        interactionSource = titleInteractionSource,
                        indication = null,
                        onClick = onExerciseClick
                    )
                    .alpha(if (isTitlePressed) 0.4f else 1f)
            )

            val displaySets = exercise.mutableTemplateSets
            if (displaySets.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    displaySets.forEach { set ->
                        val typeColor = when(set.setType) {
                            "warmup" -> Color(0xFFFFC107)
                            "failure" -> Color(0xFFEF4444)
                            "back_off" -> Color(0xFF34D399)
                            "drop" -> Color(0xFF3B82F6)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text("${set.setNumber}", color = typeColor, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableExerciseList(
    routineId: Int,
    viewModel: WorkoutViewModel,
    exercises: androidx.compose.runtime.snapshots.SnapshotStateList<RoutineExercisePreview>,
    onAddExerciseClick: () -> Unit,
    onOpenSetType: (RoutineExercisePreview, Int) -> Unit
) {
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var initialItemOffset by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isReordering = draggedIndex != null

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        itemsIndexed(
            items = exercises,
            key = { _, exercise -> exercise.exerciseId }
        ) { index, exercise ->
            val isDragged = draggedIndex == index
            val isTarget = targetIndex == index && draggedIndex != index
            val isMovingDown = targetIndex != null && draggedIndex != null && targetIndex!! > draggedIndex!!

            val zIndex = if (isDragged) 10f else 0f
            val elevation by animateDpAsState(if (isDragged) 16.dp else 0.dp, label = "elevationAnim")
            val currentOffset = listState.layoutInfo.visibleItemsInfo.find { it.index == index }?.offset ?: 0
            val compensationOffset = if (isDragged) (initialItemOffset - currentOffset).toFloat() else 0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .zIndex(zIndex)
            ) {
                if (isTarget && !isMovingDown) {
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                                Text(exercise.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { scope.launch { if(viewModel.removeExerciseFromRoutine(routineId, exercise.exerciseId)) exercises.removeAt(index) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        AnimatedVisibility(
                            visible = !isReordering,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text("Set", modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Weight", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Reps", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("RIR", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.weight(0.5f))
                                }

                                Spacer(Modifier.height(8.dp))

                                exercise.mutableTemplateSets.forEachIndexed { sIndex, set ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {

                                        val typeColor = when(set.setType) {
                                            "warmup" -> Color(0xFFFFC107)
                                            "failure" -> Color(0xFFEF4444)
                                            "back_off" -> Color(0xFF34D399)
                                            "drop" -> Color(0xFF3B82F6)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }

                                        val typeInteractionSource = remember { MutableInteractionSource() }
                                        val isTypePressed by typeInteractionSource.collectIsPressedAsState()

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(0.6f)
                                                .clickable(interactionSource = typeInteractionSource, indication = null) { onOpenSetType(exercise, sIndex) }
                                                .alpha(if (isTypePressed) 0.4f else 1f)
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Text("${sIndex + 1}", color = typeColor, style = MaterialTheme.typography.titleMedium)
                                        }

                                        OutlinedTextField(
                                            value = "", onValueChange = {}, enabled = false,
                                            placeholder = { Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                            modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp),
                                            colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        OutlinedTextField(
                                            value = "", onValueChange = {}, enabled = false,
                                            placeholder = { Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                            colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        OutlinedTextField(
                                            value = "", onValueChange = {}, enabled = false,
                                            placeholder = { Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                            colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(8.dp)
                                        )

                                        Box(modifier = Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
                                            IconButton(onClick = { exercise.mutableTemplateSets.removeAt(sIndex) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Filled.Close, contentDescription = "Remove set", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = { exercise.mutableTemplateSets.add(RoutineSet(exercise.mutableTemplateSets.size + 1, "standard")) },
                                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                                ) {
                                    Text("+ Add set", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                if (isTarget && isMovingDown) {
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        item {
            OutlinedButton(onClick = onAddExerciseClick, modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 8.dp), shape = RoundedCornerShape(8.dp)) {
                Text("Add exercise")
            }
        }
    }
}