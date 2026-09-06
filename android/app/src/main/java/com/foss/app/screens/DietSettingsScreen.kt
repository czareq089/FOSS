package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foss.app.AppViewModel
import com.foss.app.UiState
import com.foss.app.models.UserDietSettings
import com.foss.app.models.WeightHistoryPoint
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
import kotlin.math.roundToInt

private val RANGES = listOf("1m" to "1M", "3m" to "3M", "6m" to "6M", "1y" to "1Y", "all" to "All")
private val TargetColor = Color(0xFF34D399)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietSettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadUserDietSettings()
        viewModel.loadWeightHistory()
    }

    val state = viewModel.userDietSettingsState.value
    val weightHistoryState = viewModel.weightHistoryState.value
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var heightInput by remember { mutableStateOf("174") }
    var currentWeightInput by remember { mutableStateOf("70.0") }
    var targetWeightInput by remember { mutableStateOf("78.0") }
    var targetKcalInput by remember { mutableStateOf("2700") }
    var targetProteinInput by remember { mutableStateOf("140") }
    var targetFatInput by remember { mutableStateOf("75") }
    var targetCarbsInput by remember { mutableStateOf("350") }

    LaunchedEffect(state) {
        if (state is UiState.Success) {
            val d = state.data
            heightInput = if (d.heightCm % 1.0 == 0.0) d.heightCm.toInt().toString() else d.heightCm.toString()
            currentWeightInput = if (d.currentWeightKg % 1.0 == 0.0) d.currentWeightKg.toInt().toString() else d.currentWeightKg.toString()
            targetWeightInput = if (d.targetWeightKg % 1.0 == 0.0) d.targetWeightKg.toInt().toString() else d.targetWeightKg.toString()
            targetKcalInput = d.targetKcal.toInt().toString()
            targetProteinInput = d.targetProtein.toInt().toString()
            targetFatInput = d.targetFat.toInt().toString()
            targetCarbsInput = d.targetCarbs.toInt().toString()
        }
    }

    var selectedRange by remember { mutableStateOf("all") }
    val rawHistory = (weightHistoryState as? UiState.Success)?.data ?: emptyList()

    val filteredHistory = remember(rawHistory, currentWeightInput, selectedRange) {
        val baseList = if (rawHistory.isEmpty()) {
            val curW = currentWeightInput.toDoubleOrNull() ?: 70.0
            listOf(WeightHistoryPoint(date = LocalDate.now().toString(), weightKg = curW))
        } else {
            rawHistory
        }
        filterWeightHistoryByRange(baseList, selectedRange)
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val targetWeightVal = targetWeightInput.toDoubleOrNull() ?: 78.0

    var selectedPointIndex by remember(filteredHistory) {
        mutableIntStateOf(if (filteredHistory.isNotEmpty()) filteredHistory.size - 1 else -1)
    }

    val hasSinglePoint = filteredHistory.size == 1

    LaunchedEffect(filteredHistory, targetWeightVal) {
        if (filteredHistory.isNotEmpty()) {
            val rawValues: List<Float> = filteredHistory.map { it.weightKg.toFloat() }

            withContext(Dispatchers.Default) {
                modelProducer.runTransaction {
                    lineSeries {
                        if (hasSinglePoint) {
                            series(listOf(40f, 40f))
                            series(listOf(targetWeightVal.toFloat(), targetWeightVal.toFloat()))
                            series(listOf(100f, 100f))
                        } else {
                            series(rawValues)
                            series(List(rawValues.size) { targetWeightVal.toFloat() })
                            series(List(rawValues.size) { 40f })
                            series(List(rawValues.size) { 100f })
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diet & Metabolic Goals") },
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
                    val checkSource = remember { MutableInteractionSource() }
                    val isCheckPressed by checkSource.collectIsPressedAsState()

                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .alpha(if (isSaving || isCheckPressed) 0.4f else 1f)
                            .clickable(
                                enabled = !isSaving && state is UiState.Success,
                                interactionSource = checkSource,
                                indication = null
                            ) {
                                scope.launch {
                                    isSaving = true
                                    val payload = UserDietSettings(
                                        heightCm = heightInput.toDoubleOrNull() ?: 174.0,
                                        currentWeightKg = currentWeightInput.toDoubleOrNull() ?: 70.0,
                                        targetWeightKg = targetWeightInput.toDoubleOrNull() ?: 78.0,
                                        targetKcal = targetKcalInput.toDoubleOrNull() ?: 2700.0,
                                        targetProtein = targetProteinInput.toDoubleOrNull() ?: 140.0,
                                        targetFat = targetFatInput.toDoubleOrNull() ?: 75.0,
                                        targetCarbs = targetCarbsInput.toDoubleOrNull() ?: 350.0,
                                        userId = 1
                                    )
                                    val ok = viewModel.saveUserDietSettings(payload)
                                    isSaving = false
                                    if (ok) onBack()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = "Save", tint = AccentBlue)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                        Button(onClick = { viewModel.loadUserDietSettings() }) { Text("Retry") }
                    }
                }
                is UiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedCard(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Body Measurements", style = MaterialTheme.typography.titleMedium)

                                OutlinedTextField(
                                    value = heightInput,
                                    onValueChange = { heightInput = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Height (cm)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = currentWeightInput,
                                        onValueChange = { currentWeightInput = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Current Weight (kg)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = targetWeightInput,
                                        onValueChange = { targetWeightInput = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Target Weight (kg)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    )
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Weight Progress", style = MaterialTheme.typography.titleMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(shape = RoundedCornerShape(2.dp), color = AccentBlue, modifier = Modifier.size(10.dp)) {}
                                            Spacer(Modifier.width(4.dp))
                                            Text("Current", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(shape = RoundedCornerShape(2.dp), color = TargetColor, modifier = Modifier.size(10.dp)) {}
                                            Spacer(Modifier.width(4.dp))
                                            Text("Target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    RANGES.forEach { (key, label) ->
                                        FilterChip(
                                            selected = selectedRange == key,
                                            onClick = { selectedRange = key },
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
                                            "No weight logs found",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    val activePoint = filteredHistory.getOrNull(selectedPointIndex) ?: filteredHistory.last()

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Column {
                                            Text(
                                                text = String.format(Locale.US, "%.1f kg", activePoint.weightKg),
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = AccentBlue
                                            )
                                            Text(
                                                text = activePoint.date.take(10),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = String.format(Locale.US, "Target: %.1f kg", targetWeightVal),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TargetColor
                                            )
                                            val diff = targetWeightVal - activePoint.weightKg
                                            val diffStr = if (diff >= 0) String.format(Locale.US, "+%.1f kg left", diff) else String.format(Locale.US, "%.1f kg over", -diff)
                                            Text(
                                                text = diffStr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    var chartWidth by remember { mutableIntStateOf(1) }
                                    var chartHeight by remember { mutableIntStateOf(1) }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .onSizeChanged {
                                                chartWidth = maxOf(1, it.width)
                                                chartHeight = maxOf(1, it.height)
                                            }
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
                                                        val step = chartWidth.toFloat() / maxOf(1, filteredHistory.size)
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
                                                        // Seria 0: Linia wagi (niewidoczna przy 1 punkcie, widoczna przy >=2)
                                                        rememberLine(
                                                            fill = LineCartesianLayer.LineFill.single(
                                                                fill(if (hasSinglePoint) Color.Transparent else AccentBlue)
                                                            ),
                                                            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0f),
                                                            areaFill = null
                                                        ),
                                                        // Seria 1: Target (zielona linia pozioma)
                                                        rememberLine(
                                                            fill = LineCartesianLayer.LineFill.single(fill(TargetColor)),
                                                            thickness = 2.dp,
                                                            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0f),
                                                            areaFill = null
                                                        ),
                                                        // Seria 2: Dół 40 kg
                                                        rememberLine(
                                                            fill = LineCartesianLayer.LineFill.single(fill(Color.Transparent)),
                                                            thickness = 0.dp,
                                                            areaFill = null
                                                        ),
                                                        // Seria 3: Góra 100 kg
                                                        rememberLine(
                                                            fill = LineCartesianLayer.LineFill.single(fill(Color.Transparent)),
                                                            thickness = 0.dp,
                                                            areaFill = null
                                                        )
                                                    )
                                                ),
                                                startAxis = rememberStartAxis(
                                                    valueFormatter = { value, _, _ ->
                                                        val intVal = value.roundToInt()
                                                        if (intVal in listOf(40, 60, 80, 100)) "$intVal" else ""
                                                    }
                                                ),
                                                bottomAxis = rememberBottomAxis()
                                            ),
                                            modelProducer = modelProducer,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Rysujemy kropkę (punkt) wagi przy dokładnie 1 pomiarze
                                        if (hasSinglePoint) {
                                            Canvas(modifier = Modifier.fillMaxSize().padding(start = 36.dp, bottom = 24.dp, top = 8.dp, end = 8.dp)) {
                                                val yRatio = ((activePoint.weightKg - 40.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
                                                val yPos = size.height * (1f - yRatio)
                                                val xPos = size.width / 2f
                                                drawCircle(
                                                    color = AccentBlue,
                                                    radius = 5.dp.toPx(),
                                                    center = Offset(xPos, yPos)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedCard(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Daily Nutrition Targets", style = MaterialTheme.typography.titleMedium)

                                OutlinedTextField(
                                    value = targetKcalInput,
                                    onValueChange = { targetKcalInput = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Target Calories (kcal)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = targetCarbsInput,
                                        onValueChange = { targetCarbsInput = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Carbs (g)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = targetFatInput,
                                        onValueChange = { targetFatInput = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Fats (g)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = targetProteinInput,
                                        onValueChange = { targetProteinInput = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Protein (g)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun filterWeightHistoryByRange(history: List<WeightHistoryPoint>, range: String): List<WeightHistoryPoint> {
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