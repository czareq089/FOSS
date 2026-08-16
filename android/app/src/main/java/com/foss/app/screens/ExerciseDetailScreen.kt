package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.ExerciseDetailAnalytics
import com.foss.app.models.ExerciseHistoryPoint
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ChartMetric(val label: String, val unit: String) {
    ONE_RM("Est. 1RM", "kg"),
    VOLUME("Total Volume", "kg"),
    MAX_WEIGHT("Max Weight", "kg")
}

private val RANGES = listOf("1m" to "1M", "3m" to "3M", "6m" to "6M", "1y" to "1Y", "all" to "All")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    viewModel: WorkoutViewModel,
    exerciseId: Int,
    onBack: () -> Unit
) {
    var selectedRange by remember { mutableStateOf("all") }
    var selectedMetric by remember { mutableStateOf(ChartMetric.ONE_RM) }

    LaunchedEffect(exerciseId) {
        viewModel.loadExerciseAnalytics(exerciseId, "all")
    }

    val state = viewModel.exerciseAnalyticsState.value
    val title = (state as? UiState.Success)?.data?.name ?: "Exercise Progress"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        onRangeSelect = { selectedRange = it }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsContent(
    analytics: ExerciseDetailAnalytics,
    selectedMetric: ChartMetric,
    onMetricSelect: (ChartMetric) -> Unit,
    selectedRange: String,
    onRangeSelect: (String) -> Unit
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val filteredHistory = remember(analytics.history, selectedRange) {
        filterHistoryByRange(analytics.history, selectedRange)
    }

    LaunchedEffect(filteredHistory, selectedMetric) {
        if (filteredHistory.isNotEmpty()) {
            val rawValues: List<Float> = filteredHistory.map { pt ->
                when (selectedMetric) {
                    ChartMetric.ONE_RM -> pt.estOneRM.toFloat()
                    ChartMetric.VOLUME -> pt.volume.toFloat()
                    ChartMetric.MAX_WEIGHT -> pt.maxWeight.toFloat()
                }
            }
            val yValues = if (rawValues.size == 1) listOf(rawValues[0], rawValues[0]) else rawValues

            withContext(Dispatchers.Default) {
                modelProducer.runTransaction {
                    lineSeries { series(yValues) }
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
                    val latestValue = when (selectedMetric) {
                        ChartMetric.ONE_RM -> filteredHistory.last().estOneRM
                        ChartMetric.VOLUME -> filteredHistory.last().volume
                        ChartMetric.MAX_WEIGHT -> filteredHistory.last().maxWeight
                    }

                    Text(
                        text = String.format(Locale.US, "%.1f %s", latestValue, selectedMetric.unit),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Latest recorded value (${filteredHistory.size} sessions)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(),
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis()
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }
    }
}

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
        } catch (e: Exception) {
            true
        }
    }
}