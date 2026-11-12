package com.foss.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.foss.app.ui.theme.setTypeColor
import com.foss.app.ui.theme.setTypeDisplay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.foss.app.AppViewModel
import com.foss.app.UiState
import com.foss.app.models.ExerciseInfo
import com.foss.app.models.ExerciseTrackingType
import com.foss.app.models.PlateCalculator
import com.foss.app.models.ReorderPosition
import com.foss.app.models.formatSecondsToTime
import com.foss.app.models.getExerciseTrackingType
import com.foss.app.models.parseTimeToSeconds
import com.foss.app.models.UserAlgorithmSettings
import com.foss.app.models.UserPlate
import androidx.compose.ui.platform.LocalContext
import com.foss.app.notifications.RestTimerNotificationManager
import com.foss.app.ui.theme.AccentBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class SetRowState(initialSetNumber: Int) {
    var setNumber by mutableIntStateOf(initialSetNumber)
    var weight by mutableStateOf("")
    var reps by mutableStateOf("")
    var rir by mutableStateOf("")
    var confirmed by mutableStateOf(false)
    var submitting by mutableStateOf(false)
    var isPr by mutableStateOf(false)
    var prLabel by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var autoFilled by mutableStateOf(true)
    var setType by mutableStateOf("standard")

    var fallbackWeight by mutableStateOf("")
    var fallbackReps by mutableStateOf("")
    var fallbackRir by mutableStateOf("")

    var timeSeconds by mutableIntStateOf(0)
    var timeInput by mutableStateOf("")
    var isStopwatchRunning by mutableStateOf(false)
    var distanceKm by mutableStateOf("")

    var fallbackTimeSeconds by mutableIntStateOf(0)
    var fallbackDistanceKm by mutableStateOf("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLoggingScreen(
    viewModel: AppViewModel,
    workoutId: Int,
    onAddExerciseClick: () -> Unit,
    onExerciseClick: (Int) -> Unit,
    onLeaveWorkout: () -> Unit,
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
    val workoutStartedAt = viewModel.activeWorkoutStartedAtMillis.value
    var elapsedSeconds by remember(workoutId) { mutableLongStateOf(viewModel.activeWorkoutElapsedSeconds()) }

    var activeSetRowForType by remember { mutableStateOf<SetRowState?>(null) }
    var activeExerciseForTimer by remember { mutableStateOf<ExerciseInfo?>(null) }
    var activeExerciseForPlates by remember { mutableStateOf<Pair<ExerciseInfo, Double>?>(null) }

    val exerciseRestTimes = remember { mutableStateMapOf<Int, Int>() }
    val exerciseSetsMap = remember { mutableStateMapOf<Int, SnapshotStateList<SetRowState>>() }
    val sheetState = rememberModalBottomSheetState()

    var prBannerText by remember { mutableStateOf<String?>(null) }
    var prBannerSetType by remember { mutableStateOf<String?>(null) }
    var lastPrBannerText by remember { mutableStateOf("") }
    var lastPrBannerSetType by remember { mutableStateOf("standard") }

    LaunchedEffect(workoutId, workoutStartedAt) {
        while (true) {
            elapsedSeconds = viewModel.activeWorkoutElapsedSeconds()
            exerciseSetsMap.values.forEach { setList ->
                setList.forEach { row ->
                    if (row.isStopwatchRunning && !row.confirmed) {
                        row.timeSeconds += 1
                        row.timeInput = formatSecondsToTime(row.timeSeconds)
                        row.autoFilled = false
                    }
                }
            }
            delay(1000L)
        }
    }

    val formattedDuration = remember(elapsedSeconds) {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    LaunchedEffect(prBannerText) {
        if (prBannerText != null) {
            delay(3500L)
            prBannerText = null
        }
    }

    val timerRemaining = RestTimerNotificationManager.timerRemaining
    val timerTotal = RestTimerNotificationManager.timerTotal
    val isTimerRunning = RestTimerNotificationManager.isTimerRunning

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

    val context = LocalContext.current
    val handleFinish = {
        RestTimerNotificationManager.cancelTimer(context)
        onFinish()
    }

    BackHandler {
        if (isReordering) {
            if (hasPendingEdits()) {
                showDiscardEditDialog = true
            } else {
                isReordering = false
            }
        } else {
            RestTimerNotificationManager.cancelTimer(context)
            onLeaveWorkout()
        }
    }

    val allActiveSets = exerciseSetsMap.values.flatten()
    val confirmedSetsCount = allActiveSets.count { it.confirmed }
    val prCount = allActiveSets.count { it.confirmed && it.isPr }
    val totalVolumeKg = allActiveSets.filter { it.confirmed }.sumOf { row ->
        val w = row.weight.toDoubleOrNull() ?: row.fallbackWeight.toDoubleOrNull() ?: 0.0
        val r = row.reps.toIntOrNull() ?: row.fallbackReps.toIntOrNull() ?: 0
        w * r
    }
    val formattedVolume = if (totalVolumeKg % 1.0 == 0.0) {
        totalVolumeKg.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", totalVolumeKg)
    }

    val userPlates = (viewModel.userPlatesState.value as? UiState.Success)?.data ?: emptyList()
    val algoSettings = (viewModel.algorithmSettingsState.value as? UiState.Success)?.data ?: UserAlgorithmSettings()
    val showRir = algoSettings.showRIR

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isReordering) "Edit exercises" else "Log workout",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
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
                                    RestTimerNotificationManager.cancelTimer(context)
                                    onLeaveWorkout()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = if (isReordering) "Discard edits" else "Leave workout",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
                                    DropdownMenuItem(
                                        text = { Text("Cancel workout", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            isMenuExpanded = false
                                            showCancelDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
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
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Button(
                            onClick = {
                                if (hasStructureChanged) showSyncDialog = true else handleFinish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Finish workout")
                        }
                    }
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
                    contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isReordering) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formattedDuration,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentBlue
                                    )

                                    Text(
                                        text = "$formattedVolume kg",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = "$confirmedSetsCount sets",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Stars,
                                            contentDescription = "PR count",
                                            tint = if (prCount > 0) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "$prCount PR",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (prCount > 0) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(items = exercises, key = { _, exercise -> exercise.workoutExerciseId }) { index, exercise ->
                        val currentRestTime = exerciseRestTimes[exercise.workoutExerciseId] ?: 90

                        val isDragged = draggedIndex == index
                        val isTarget = targetIndex == index && draggedIndex != index
                        val isMovingDown = targetIndex != null && draggedIndex != null && targetIndex!! > draggedIndex!!
                        val zIndex = if (isDragged) 10f else 0f
                        val elevation by animateDpAsState(if (isDragged) 16.dp else 0.dp, label = "elev")

                        val currentOffset = listState.layoutInfo.visibleItemsInfo.find { it.index == index }?.offset ?: 0
                        val compensationOffset = if (isDragged) (initialItemOffset - currentOffset).toFloat() else 0f

                        val setsList = exerciseSetsMap.getOrPut(exercise.workoutExerciseId) {
                            val initialList = mutableStateListOf<SetRowState>()
                            val templateSets = exercise.templateSets ?: emptyList()
                            val totalCount = if (templateSets.isNotEmpty()) templateSets.size else maxOf(1, exercise.lastSets?.size ?: 1)

                            val workingSets = exercise.lastSets.orEmpty()
                                .filter { it.setType == "standard" }
                            val baselineWeight = if (algoSettings.warmupBase == "heaviest_set") {
                                workingSets.maxOfOrNull { it.weightKg } ?: 0.0
                            } else {
                                workingSets.firstOrNull()?.weightKg ?: 0.0
                            }

                            val warmupTemplates = templateSets.filter { it.setType == "warmup" }
                            val totalWarmups = warmupTemplates.size

                            val cachedDrafts = viewModel.activeWorkoutInputs[exercise.workoutExerciseId] ?: emptyMap()

                            for (i in 1..totalCount) {
                                val template = templateSets.find { it.setNumber == i }
                                val currentType = template?.setType ?: "standard"

                                // Autofill jest przypisany do dokładnego klucza (set_number + set_type).
                                // Np. standard #2 NIE może dostać danych z warmup #2 ani z standard #1.
                                val prevSet = exercise.lastSets.orEmpty().firstOrNull { previous ->
                                    previous.setNumber == i && previous.setType == currentType
                                }

                                var calcWeight = prevSet?.weightKg ?: 0.0
                                var calcReps = prevSet?.reps ?: 0
                                var calcRir = prevSet?.rir ?: 0

                                // Historia tego samego typu serii ma pierwszeństwo.
                                // Algorytm procentowy jest fallbackiem tylko wtedy, gdy takiej serii wcześniej nie było.
                                if (prevSet == null) {
                                    when (currentType) {
                                        "warmup" -> {
                                            if (algoSettings.warmupEnabled && baselineWeight > 0.0) {
                                                val warmupIndex = warmupTemplates.indexOf(template) + 1
                                                val fraction = if (totalWarmups <= 1) 0.60 else 0.50 + (0.35 * ((warmupIndex - 1).toDouble() / maxOf(1, totalWarmups - 1)))
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
                                }

                                val draft = cachedDrafts[i]

                                initialList.add(SetRowState(i).apply {
                                    this.setType = draft?.setType ?: currentType
                                    val calcTimeSeconds = prevSet?.timeSeconds ?: 0
                                    val calcDistanceKm = prevSet?.distanceKm ?: 0.0

                                    // Ustawiamy wyłącznie fallbacki jako podpowiedź (hint)
                                    if (calcWeight > 0.0) {
                                        this.fallbackWeight = if (calcWeight % 1.0 == 0.0) calcWeight.toInt().toString() else calcWeight.toString()
                                    }
                                    if (calcReps > 0) {
                                        this.fallbackReps = calcReps.toString()
                                    }
                                    if (calcRir >= 0 && (prevSet != null || currentType != "standard")) {
                                        this.fallbackRir = calcRir.toString()
                                    }
                                    if (calcTimeSeconds > 0) {
                                        this.fallbackTimeSeconds = calcTimeSeconds
                                    }
                                    if (calcDistanceKm > 0.0) {
                                        this.fallbackDistanceKm = if (calcDistanceKm % 1.0 == 0.0) calcDistanceKm.toInt().toString() else calcDistanceKm.toString()
                                    }

                                    // Przywracamy szkic użytkownika jeśli istniał, w przeciwnym wypadku pole jest puste
                                    if (draft != null) {
                                        this.weight = draft.weight
                                        this.reps = draft.reps
                                        this.rir = draft.rir
                                        this.confirmed = draft.confirmed
                                        this.autoFilled = false
                                        this.timeSeconds = draft.timeSeconds
                                        this.timeInput = draft.timeInput
                                        this.distanceKm = draft.distanceKm
                                    } else {
                                        this.weight = ""
                                        this.reps = ""
                                        this.rir = ""
                                        this.confirmed = false
                                        this.autoFilled = true
                                        this.timeSeconds = 0
                                        this.timeInput = ""
                                        this.distanceKm = ""
                                    }
                                })
                            }
                            initialList
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
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
                                                                        hasStructureChanged = true
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
                                            sets = setsList,
                                            restSeconds = currentRestTime,
                                            showRir = showRir,
                                            onRestTimeClick = { activeExerciseForTimer = exercise },
                                            onPlateCalculatorClick = { currentWeight ->
                                                activeExerciseForPlates = Pair(exercise, currentWeight)
                                            },
                                            onStartTimer = { seconds -> RestTimerNotificationManager.startTimer(context, seconds) },
                                            onCancelTimer = { RestTimerNotificationManager.cancelTimer(context) },
                                            onOpenSetType = { row -> activeSetRowForType = row },
                                            onPrAchieved = { msg, setType ->
                                                prBannerText = msg
                                                prBannerSetType = setType
                                                lastPrBannerText = msg
                                                lastPrBannerSetType = setType
                                            },
                                            onStructureChanged = { hasStructureChanged = true }
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
                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                OutlinedButton(onClick = onAddExerciseClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add exercise to active workout", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                Text("No active workout.", modifier = Modifier.align(Alignment.Center))
            }

            AnimatedVisibility(
                visible = prBannerText != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .zIndex(20f)
            ) {
                val currentSetType = prBannerSetType ?: lastPrBannerSetType
                val bannerColor = setTypeColor(currentSetType)
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.5.dp, bannerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = bannerColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Stars,
                                    contentDescription = "PR",
                                    tint = bannerColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NEW PERSONAL RECORD!",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = bannerColor
                            )
                            Text(
                                text = prBannerText ?: lastPrBannerText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
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
                            TextButton(onClick = {
                                RestTimerNotificationManager.adjustTimer(context, -30)
                            }) { Text("-30s", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(String.format(Locale.US, "%02d:%02d", timerRemaining / 60, timerRemaining % 60), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = {
                                RestTimerNotificationManager.adjustTimer(context, 30)
                            }) { Text("+30s", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                            exerciseSetsMap.remove(ex.workoutExerciseId)
                            viewModel.activeWorkoutInputs.remove(ex.workoutExerciseId)
                            hasStructureChanged = true
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
                    hasStructureChanged = true
                    activeSetRowForType = null
                }
                SetTypeOption("standard", "Standard set", null, Color.White) { updateType("standard") }
                SetTypeOption("warmup", "Warmup set", "W", Color(0xFFFFC107)) { updateType("warmup") }
                SetTypeOption("failure", "Failure set", "F", Color(0xFFEF4444)) { updateType("failure") }
                SetTypeOption("drop", "Drop set", "D", Color(0xFFA855F7)) { updateType("drop") }
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
            onDismissRequest = { showSyncDialog = false; handleFinish() },
            title = { Text("Update original routine?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("You changed the exercises or sets. Do you want to update the original routine template for the future?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                            handleFinish()
                        }
                    }
                ) { Text("Update Routine", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showSyncDialog = false; handleFinish() }) {
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
                        RestTimerNotificationManager.cancelTimer(context)
                        scope.launch {
                            viewModel.deleteWorkout(wId)
                            showCancelDialog = false
                            onCancelWorkout()
                        }
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
                    text = "Each side: ${formatPlateWeight(breakdown.perSideWeight)} kg",
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
                        text = "+ 1x Single plate (Pin / Center):",
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
    viewModel: AppViewModel,
    exercise: ExerciseInfo,
    sets: SnapshotStateList<SetRowState>,
    restSeconds: Int,
    showRir: Boolean,
    onRestTimeClick: () -> Unit,
    onPlateCalculatorClick: (Double) -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onOpenSetType: (SetRowState) -> Unit,
    onPrAchieved: (String, String) -> Unit,
    onStructureChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()

    fun syncSetDraft(row: SetRowState) {
        viewModel.updateSetDraft(
            workoutExerciseId = exercise.workoutExerciseId,
            setNumber = row.setNumber,
            weight = row.weight,
            reps = row.reps,
            rir = row.rir,
            confirmed = row.confirmed,
            setType = row.setType,
            timeSeconds = row.timeSeconds,
            timeInput = row.timeInput,
            distanceKm = row.distanceKm
        )
    }

    fun propagateFromFirstRow() {
        val first = sets.firstOrNull() ?: return
        sets.drop(1).forEach { row ->
            if (!row.confirmed && row.autoFilled) {
                row.weight = first.weight
                row.reps = first.reps
                row.rir = first.rir
                row.timeSeconds = first.timeSeconds
                row.timeInput = first.timeInput
                row.distanceKm = first.distanceKm
                syncSetDraft(row)
            }
        }
    }

    fun renumberSets() {
        sets.forEachIndexed { i, s ->
            s.setNumber = i + 1
            syncSetDraft(s)
        }
    }

    fun resolveTargetPlateWeight(): Double {
        val lastConfirmedWeight = sets.filter { it.confirmed }.lastOrNull()?.weight?.toDoubleOrNull()
        if (lastConfirmedWeight != null && lastConfirmedWeight > 0.0) return lastConfirmedWeight

        val activeRowWeight = sets.firstOrNull { !it.confirmed }?.weight?.toDoubleOrNull()
        if (activeRowWeight != null && activeRowWeight > 0.0) return activeRowWeight

        val firstRowFallback = sets.firstOrNull()?.fallbackWeight?.toDoubleOrNull()
        if (firstRowFallback != null && firstRowFallback > 0.0) return firstRowFallback

        val lastTrainingWeight = exercise.lastSets
            ?.firstOrNull { it.setType == "standard" }
            ?.weightKg
        if (lastTrainingWeight != null && lastTrainingWeight > 0.0) return lastTrainingWeight

        return 0.0
    }

    val trackingType = getExerciseTrackingType(exercise.type)

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

            if (trackingType == ExerciseTrackingType.WEIGHT_AND_REPS) {
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
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Set", modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (trackingType) {
                ExerciseTrackingType.WEIGHT_AND_REPS -> {
                    Text("Weight", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Reps", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (showRir) {
                        Text("RIR", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ExerciseTrackingType.REPS_ONLY -> {
                    Text("Reps", modifier = Modifier.weight(2.2f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ExerciseTrackingType.TIME_ONLY -> {
                    Text("Time", modifier = Modifier.weight(2.2f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ExerciseTrackingType.TIME_AND_DISTANCE -> {
                    Text("Time", modifier = Modifier.weight(1.4f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Km", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.weight(0.9f))
        }

        Spacer(Modifier.height(8.dp))

        sets.forEachIndexed { rowIndex, row ->
            key(row.setNumber) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val typeColor = setTypeColor(row.setType)
                    val typeInteractionSource = remember { MutableInteractionSource() }
                    val isTypePressed by typeInteractionSource.collectIsPressedAsState()

                    val isPrSet = row.confirmed && row.isPr
                    val prBrightColor = lerp(typeColor, Color.White, 0.35f)

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(0.6f)
                            .clickable(interactionSource = typeInteractionSource, indication = null, enabled = !row.confirmed) { onOpenSetType(row) }
                            .alpha(if (isTypePressed) 0.4f else 1f)
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = setTypeDisplay(row.setType, row.setNumber),
                                color = when {
                                    isPrSet -> prBrightColor
                                    row.confirmed -> typeColor
                                    row.setType != "standard" -> typeColor
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isPrSet) {
                                Spacer(Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Filled.Stars,
                                    contentDescription = "Personal Record",
                                    tint = lerp(typeColor, Color.White, 0.45f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    val rowTextColor = MaterialTheme.colorScheme.onSurface
                    val completedBorderColor = if (isPrSet) prBrightColor else typeColor

                    when (trackingType) {
                        ExerciseTrackingType.WEIGHT_AND_REPS -> {
                            OutlinedTextField(
                                value = row.weight,
                                onValueChange = {
                                    row.weight = it.filter { c -> c.isDigit() || c == '.' }
                                    syncSetDraft(row)
                                    if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                                },
                                enabled = !row.confirmed && !row.submitting,
                                singleLine = true,
                                placeholder = {
                                    if (row.fallbackWeight.isNotEmpty()) {
                                        Text(
                                            text = row.fallbackWeight,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = rowTextColor,
                                    unfocusedTextColor = rowTextColor,
                                    disabledTextColor = rowTextColor,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledBorderColor = if (row.confirmed) completedBorderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = row.reps,
                                onValueChange = {
                                    row.reps = it.filter(Char::isDigit)
                                    syncSetDraft(row)
                                    if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                                },
                                enabled = !row.confirmed && !row.submitting,
                                singleLine = true,
                                placeholder = {
                                    if (row.fallbackReps.isNotEmpty()) {
                                        Text(
                                            text = row.fallbackReps,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = rowTextColor,
                                    unfocusedTextColor = rowTextColor,
                                    disabledTextColor = rowTextColor,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledBorderColor = if (row.confirmed) completedBorderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            if (showRir) {
                                OutlinedTextField(
                                    value = row.rir,
                                    onValueChange = {
                                        row.rir = it.filter(Char::isDigit)
                                        syncSetDraft(row)
                                        if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                                    },
                                    enabled = !row.confirmed && !row.submitting,
                                    singleLine = true,
                                    placeholder = {
                                        if (row.fallbackRir.isNotEmpty()) {
                                            Text(
                                                text = row.fallbackRir,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = rowTextColor,
                                        unfocusedTextColor = rowTextColor,
                                        disabledTextColor = rowTextColor,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledBorderColor = if (row.confirmed) completedBorderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                        ExerciseTrackingType.REPS_ONLY -> {
                            OutlinedTextField(
                                value = row.reps,
                                onValueChange = {
                                    row.reps = it.filter(Char::isDigit)
                                    syncSetDraft(row)
                                    if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                                },
                                enabled = !row.confirmed && !row.submitting,
                                singleLine = true,
                                placeholder = {
                                    if (row.fallbackReps.isNotEmpty()) {
                                        Text(
                                            text = row.fallbackReps,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        )
                                    } else {
                                        Text(
                                            text = "-",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(2.2f).padding(horizontal = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = rowTextColor,
                                    unfocusedTextColor = rowTextColor,
                                    disabledTextColor = rowTextColor,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledBorderColor = if (row.confirmed) completedBorderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        ExerciseTrackingType.TIME_ONLY -> {
                            OutlinedTextField(
                                value = row.timeInput,
                                onValueChange = {
                                    row.timeInput = it
                                    row.timeSeconds = parseTimeToSeconds(it)
                                    syncSetDraft(row)
                                    if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                                },
                                enabled = !row.confirmed && !row.submitting,
                                singleLine = true,
                                placeholder = {
                                    val pl = if (row.fallbackTimeSeconds > 0) formatSecondsToTime(row.fallbackTimeSeconds) else "00:00"
                                    Text(
                                        text = pl,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (!row.confirmed && !row.submitting) {
                                                if (!row.isStopwatchRunning) {
                                                    if (row.timeSeconds == 0 && row.timeInput.isNotEmpty()) {
                                                        row.timeSeconds = parseTimeToSeconds(row.timeInput)
                                                    }
                                                    row.isStopwatchRunning = true
                                                } else {
                                                    row.isStopwatchRunning = false
                                                    row.timeInput = formatSecondsToTime(row.timeSeconds)
                                                    syncSetDraft(row)
                                                }
                                            }
                                        },
                                        enabled = !row.confirmed && !row.submitting,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (row.isStopwatchRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (row.isStopwatchRunning) "Stop timer" else "Start timer",
                                            tint = if (row.isStopwatchRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.weight(2.2f).padding(horizontal = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = rowTextColor,
                                    unfocusedTextColor = rowTextColor,
                                    disabledTextColor = rowTextColor,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = if (row.isStopwatchRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    disabledBorderColor = if (row.confirmed) completedBorderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        ExerciseTrackingType.TIME_AND_DISTANCE -> {
                            OutlinedTextField(
                                value = row.timeInput,
                                onValueChange = {
                                    row.timeInput = it
                                    row.timeSeconds = parseTimeToSeconds(it)
                                    syncSetDraft(row)
                                    if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                                },
                                enabled = !row.confirmed && !row.submitting,
                                singleLine = true,
                                placeholder = {
                                    val pl = if (row.fallbackTimeSeconds > 0) formatSecondsToTime(row.fallbackTimeSeconds) else "00:00"
                                    Text(
                                        text = pl,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (!row.confirmed && !row.submitting) {
                                                if (!row.isStopwatchRunning) {
                                                    if (row.timeSeconds == 0 && row.timeInput.isNotEmpty()) {
                                                        row.timeSeconds = parseTimeToSeconds(row.timeInput)
                                                    }
                                                    row.isStopwatchRunning = true
                                                } else {
                                                    row.isStopwatchRunning = false
                                                    row.timeInput = formatSecondsToTime(row.timeSeconds)
                                                    syncSetDraft(row)
                                                }
                                            }
                                        },
                                        enabled = !row.confirmed && !row.submitting,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (row.isStopwatchRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (row.isStopwatchRunning) "Stop timer" else "Start timer",
                                            tint = if (row.isStopwatchRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.weight(1.4f).padding(horizontal = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = rowTextColor,
                                    unfocusedTextColor = rowTextColor,
                                    disabledTextColor = rowTextColor,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = if (row.isStopwatchRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    disabledBorderColor = if (row.confirmed) completedBorderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = row.distanceKm,
                                onValueChange = {
                                    row.distanceKm = it.filter { c -> c.isDigit() || c == '.' }
                                    syncSetDraft(row)
                                    if (rowIndex == 0) propagateFromFirstRow() else row.autoFilled = false
                                },
                                enabled = !row.confirmed && !row.submitting,
                                singleLine = true,
                                placeholder = {
                                    val pl = if (row.fallbackDistanceKm.isNotEmpty()) row.fallbackDistanceKm else "-"
                                    Text(
                                        text = pl,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = rowTextColor,
                                    unfocusedTextColor = rowTextColor,
                                    disabledTextColor = rowTextColor,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledBorderColor = if (row.confirmed) completedBorderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Row(modifier = Modifier.weight(0.9f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        if (row.submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            val checkRowSource = remember { MutableInteractionSource() }
                            val isCheckRowPressed by checkRowSource.collectIsPressedAsState()

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (row.confirmed) {
                                    if (isPrSet) prBrightColor else typeColor
                                } else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (row.confirmed) {
                                    if (isPrSet) prBrightColor else typeColor
                                } else MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .size(28.dp)
                                    .alpha(if (isCheckRowPressed) 0.4f else 1f)
                                    .clickable(
                                        interactionSource = checkRowSource,
                                        indication = null
                                    ) {
                                        if (!row.confirmed) {
                                            if (row.isStopwatchRunning) {
                                                row.isStopwatchRunning = false
                                                row.timeInput = formatSecondsToTime(row.timeSeconds)
                                            }

                                            val finalTimeSeconds = if (row.timeInput.isNotEmpty()) {
                                                parseTimeToSeconds(row.timeInput).coerceAtLeast(row.timeSeconds)
                                            } else if (row.fallbackTimeSeconds > 0) {
                                                row.fallbackTimeSeconds
                                            } else {
                                                row.timeSeconds
                                            }
                                            if (finalTimeSeconds > 0) {
                                                row.timeSeconds = finalTimeSeconds
                                                row.timeInput = formatSecondsToTime(finalTimeSeconds)
                                            }

                                            val finalDistanceStr = if (row.distanceKm.isNotEmpty()) row.distanceKm else row.fallbackDistanceKm
                                            row.distanceKm = finalDistanceStr
                                            val finalDistance = finalDistanceStr.toDoubleOrNull() ?: 0.0

                                            val finalWeightStr = if (row.weight.isNotEmpty()) row.weight else row.fallbackWeight
                                            val finalRepsStr = if (row.reps.isNotEmpty()) row.reps else row.fallbackReps
                                            val finalRirStr = if (row.rir.isNotEmpty()) row.rir else row.fallbackRir

                                            row.weight = finalWeightStr
                                            row.reps = finalRepsStr
                                            row.rir = finalRirStr

                                            val reps = row.reps.toIntOrNull() ?: 0
                                            val weight = row.weight.toDoubleOrNull() ?: 0.0

                                            val allTimeBestWeight = exercise.bestWeightKg
                                            val allTimeBestReps = exercise.bestReps
                                            val priorConfirmedSets = sets.filter { it !== row && it.confirmed }
                                            val currentPriorMaxWeight = priorConfirmedSets.maxOfOrNull { it.weight.toDoubleOrNull() ?: 0.0 } ?: 0.0
                                            val currentPriorMaxReps = priorConfirmedSets.maxOfOrNull { it.reps.toIntOrNull() ?: 0 } ?: 0
                                            val currentPriorMaxTime = priorConfirmedSets.maxOfOrNull { it.timeSeconds } ?: 0
                                            val currentPriorMaxDistance = priorConfirmedSets.maxOfOrNull { it.distanceKm.toDoubleOrNull() ?: 0.0 } ?: 0.0

                                            val isWeightPr = weight > 0.0 && weight > maxOf(allTimeBestWeight, currentPriorMaxWeight)
                                            val isRepsPr = reps > 0 && reps > maxOf(allTimeBestReps.toDouble(), currentPriorMaxReps.toDouble()).toInt()
                                            val isTimePr = finalTimeSeconds > 0 && finalTimeSeconds > currentPriorMaxTime
                                            val isDistancePr = finalDistance > 0.0 && finalDistance > currentPriorMaxDistance

                                            val isNewPr = when (trackingType) {
                                                ExerciseTrackingType.WEIGHT_AND_REPS -> isWeightPr || isRepsPr
                                                ExerciseTrackingType.REPS_ONLY -> isRepsPr
                                                ExerciseTrackingType.TIME_ONLY -> isTimePr
                                                ExerciseTrackingType.TIME_AND_DISTANCE -> isTimePr || isDistancePr
                                            }
                                            val localPrLabel = when {
                                                isWeightPr -> "Heaviest: ${if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString()} kg"
                                                isRepsPr -> "Max Reps: $reps reps"
                                                isTimePr -> "Best Time: ${formatSecondsToTime(finalTimeSeconds)}"
                                                isDistancePr -> "Best Distance: $finalDistanceStr km"
                                                else -> ""
                                            }

                                            if (isNewPr && localPrLabel.isNotEmpty()) {
                                                row.isPr = true
                                                row.prLabel = localPrLabel
                                                onPrAchieved("${exercise.name} - $localPrLabel", row.setType)
                                            }

                                            row.error = null
                                            row.confirmed = true
                                            row.submitting = true
                                            syncSetDraft(row)

                                            val nextIndex = rowIndex + 1
                                            val isNextSetDrop = if (nextIndex < sets.size) {
                                                sets[nextIndex].setType == "drop"
                                            } else false

                                            if (isNextSetDrop) {
                                                onCancelTimer()
                                            } else {
                                                onStartTimer(restSeconds)
                                            }

                                            val rir = if (showRir) (row.rir.toIntOrNull() ?: 0) else 0
                                            scope.launch {
                                                val res = viewModel.logSet(
                                                    workoutExerciseId = exercise.workoutExerciseId,
                                                    setNumber = row.setNumber,
                                                    reps = reps,
                                                    weightKg = weight,
                                                    rir = rir,
                                                    setType = row.setType,
                                                    timeSeconds = finalTimeSeconds,
                                                    distanceKm = finalDistance
                                                )
                                                row.submitting = false
                                                if (res != null) {
                                                    if (res.isPr) {
                                                        row.isPr = true
                                                        val label = res.prLabel ?: localPrLabel
                                                        row.prLabel = label
                                                        onPrAchieved("${exercise.name} - $label", row.setType)
                                                    } else if (!isNewPr) {
                                                        row.isPr = false
                                                    }
                                                } else {
                                                    row.confirmed = false
                                                    row.isPr = false
                                                    row.error = "Failed"
                                                    syncSetDraft(row)
                                                }
                                            }
                                        } else {
                                            row.confirmed = false
                                            row.isPr = false
                                            syncSetDraft(row)
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
                                        ) {
                                            sets.remove(row)
                                            renumberSets()
                                            onStructureChanged()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = {
                val nextSetNum = (sets.maxOfOrNull { it.setNumber } ?: 0) + 1
                val lastSet = sets.lastOrNull()
                val newRow = SetRowState(nextSetNum).apply {
                    this.setType = lastSet?.setType ?: "standard"
                    if (lastSet != null) {
                        this.fallbackWeight = lastSet.weight.ifEmpty { lastSet.fallbackWeight }
                        this.fallbackReps = lastSet.reps.ifEmpty { lastSet.fallbackReps }
                        this.fallbackRir = lastSet.rir.ifEmpty { lastSet.fallbackRir }
                        this.fallbackTimeSeconds = if (lastSet.timeSeconds > 0) lastSet.timeSeconds else lastSet.fallbackTimeSeconds
                        this.fallbackDistanceKm = lastSet.distanceKm.ifEmpty { lastSet.fallbackDistanceKm }
                    }
                }
                sets.add(newRow)
                syncSetDraft(newRow)
                onStructureChanged()
            },
            modifier = Modifier.align(Alignment.End)
        ) { Text("+ Add set", color = MaterialTheme.colorScheme.primary) }
    }
}
