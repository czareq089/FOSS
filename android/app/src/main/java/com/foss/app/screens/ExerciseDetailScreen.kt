package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foss.app.AppViewModel
import com.foss.app.UiState
import com.foss.app.models.ExerciseConstants
import com.foss.app.models.ExerciseDetailAnalytics
import com.foss.app.models.ExerciseHistoryPoint
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ChartMetric(val label: String, val unit: String) {
    VOLUME("Total Volume", "kg"),
    MAX_WEIGHT("Max Weight", "kg"),
    ONE_RM("Est. 1RM", "kg")
}

private val RANGES = listOf("1m" to "1M", "3m" to "3M", "6m" to "6M", "1y" to "1Y", "all" to "All")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    viewModel: AppViewModel,
    exerciseId: Int,
    onBack: () -> Unit
) {
    var selectedRange by remember { mutableStateOf("all") }
    var selectedMetric by remember { mutableStateOf(ChartMetric.VOLUME) }
    var showEditDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(exerciseId) {
        viewModel.loadExerciseAnalytics(exerciseId, "all")
    }

    val state = viewModel.exerciseAnalyticsState.value
    val analyticsData = (state as? UiState.Success)?.data
    val title = analyticsData?.name ?: "Exercise Progress"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
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
                    if (analyticsData != null) {
                        val editSource = remember { MutableInteractionSource() }
                        val isEditPressed by editSource.collectIsPressedAsState()

                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(36.dp)
                                .alpha(if (isEditPressed) 0.4f else 1f)
                                .clickable(
                                    interactionSource = editSource,
                                    indication = null
                                ) { showEditDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit Exercise",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state) {
                is UiState.Loading, UiState.Idle -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadExerciseAnalytics(exerciseId, "all") }) {
                            Text("Retry")
                        }
                    }
                }
                is UiState.Success -> {
                    AnalyticsContent(
                        analytics = state.data,
                        selectedMetric = selectedMetric,
                        onMetricSelect = { selectedMetric = it },
                        selectedRange = selectedRange,
                        onRangeSelect = { selectedRange = it },
                        onEditClick = { showEditDialog = true }
                    )
                }
            }
        }
    }

    if (showEditDialog && analyticsData != null) {
        EditExerciseDialog(
            currentName = analyticsData.name,
            currentType = analyticsData.type,
            currentEquipment = analyticsData.equipment,
            onDismiss = { showEditDialog = false },
            onSave = { newName, newType, newEquipment ->
                scope.launch {
                    val ok = viewModel.updateExercise(exerciseId, newName, newType, newEquipment)
                    showEditDialog = false
                    if (ok) {
                        viewModel.loadExerciseAnalytics(exerciseId, selectedRange)
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsContent(
    analytics: ExerciseDetailAnalytics,
    selectedMetric: ChartMetric,
    onMetricSelect: (ChartMetric) -> Unit,
    selectedRange: String,
    onRangeSelect: (String) -> Unit,
    onEditClick: () -> Unit
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val filteredHistory = remember(analytics.history, selectedRange) {
        filterHistoryByRange(analytics.history, selectedRange)
    }

    var selectedPointIndex by remember(filteredHistory, selectedMetric) {
        mutableIntStateOf(if (filteredHistory.isNotEmpty()) filteredHistory.size - 1 else -1)
    }

    LaunchedEffect(filteredHistory, selectedMetric) {
        if (filteredHistory.isNotEmpty()) {
            val rawValues: List<Float> = filteredHistory.map { pt ->
                when (selectedMetric) {
                    ChartMetric.VOLUME -> pt.volume.toFloat()
                    ChartMetric.MAX_WEIGHT -> pt.maxWeight.toFloat()
                    ChartMetric.ONE_RM -> pt.estOneRM.toFloat()
                }
            }
            // Brak zera: przy 1 punkcie powielamy realną wartość
            val baseSeries = if (rawValues.size == 1) listOf(rawValues[0], rawValues[0]) else rawValues
            val maxVal = baseSeries.maxOrNull() ?: 0f
            val targetCeiling = if (maxVal > 0f) maxVal * 1.25f else 10f
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = analytics.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = analytics.type.ifBlank { "General" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = analytics.equipment.ifBlank { "None" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChartMetric.entries.forEachIndexed { index, metric ->
                SegmentedButton(
                    selected = selectedMetric == metric,
                    onClick = { onMetricSelect(metric) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ChartMetric.entries.size)
                ) {
                    Text(metric.label, fontSize = 12.sp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RANGES.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedRange == key,
                            onClick = { onRangeSelect(key) },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (filteredHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No logged workouts found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val activePoint = filteredHistory.getOrNull(selectedPointIndex) ?: filteredHistory.last()
                    val activeValue = when (selectedMetric) {
                        ChartMetric.VOLUME -> activePoint.volume
                        ChartMetric.MAX_WEIGHT -> activePoint.maxWeight
                        ChartMetric.ONE_RM -> activePoint.estOneRM
                    }

                    Text(
                        text = String.format(Locale.US, "%.1f %s", activeValue, selectedMetric.unit),
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
                            .height(200.dp)
                            .onSizeChanged { chartWidth = maxOf(1, it.width) }
                            .pointerInput(filteredHistory) {
                                detectTapGestures { offset ->
                                    if (filteredHistory.isNotEmpty()) {
                                        val step = chartWidth.toFloat() / filteredHistory.size
                                        val index = (offset.x / step).toInt().coerceIn(0, filteredHistory.size - 1)
                                        selectedPointIndex = index
                                    }
                                }
                            }
                            .pointerInput(filteredHistory) {
                                detectDragGestures { change, _ ->
                                    if (filteredHistory.isNotEmpty()) {
                                        val step = chartWidth.toFloat() / historySize(filteredHistory)
                                        val index = (change.position.x / step).toInt().coerceIn(0, filteredHistory.size - 1)
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

private fun historySize(history: List<ExerciseHistoryPoint>): Int = maxOf(1, history.size)

private fun filterHistoryByRange(history: List<ExerciseHistoryPoint>, range: String): List<ExerciseHistoryPoint> {
    if (range == "all" || history.isEmpty()) return history
    val now = LocalDate.now()

    val cutoff = when (range) {
        "1m" -> now.minusMonths(1)
        "3m" -> now.minusMonths(3)
        "6m" -> now.minusMonths(6)
        "1y" -> now.minusYears(1)
        else -> null
    } ?: return history

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    return history.filter {
        try {
            val rawDate = it.date.trim().take(10)
            val date = LocalDate.parse(rawDate, formatter)
            !date.isBefore(cutoff)
        } catch (_: Exception) {
            true
        }
    }
}

@Composable
private fun EditExerciseDialog(
    currentName: String,
    currentType: String,
    currentEquipment: String,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, equipment: String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var type by remember { mutableStateOf(currentType) }
    var equipment by remember { mutableStateOf(currentEquipment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Exercise", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
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

                Text(
                    text = "Exercise Type / Tracking",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ExerciseConstants.TYPES) { t ->
                        FilterChip(
                            selected = type.equals(t, ignoreCase = true),
                            onClick = { type = t },
                            label = { Text(t, fontSize = 12.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Category / Type") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Equipment",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ExerciseConstants.EQUIPMENTS) { eq ->
                        FilterChip(
                            selected = equipment.equals(eq, ignoreCase = true),
                            onClick = { equipment = eq },
                            label = { Text(eq, fontSize = 12.sp) }
                        )
                    }
                }
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
                        onSave(name.trim(), type.trim(), equipment.trim())
                    }
                }
            ) {
                Text("Save", color = MaterialTheme.colorScheme.primary)
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
