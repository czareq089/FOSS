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
import androidx.compose.material.icons.filled.Edit
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
import com.foss.app.models.MacroTargets
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
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.AxisValueOverrider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val RANGES = listOf("1m" to "1M", "3m" to "3M", "6m" to "6M", "1y" to "1Y", "all" to "All")
private val TargetColor = Color(0xFF34D399)

private val GOALS = listOf(
    "bulk" to "Bulk",
    "cut" to "Cut",
    "recomp" to "Recomp",
    "maintain" to "Maintain"
)

data class StrategyDraft(
    val kcal: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = ""
)

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
    var selectedGoal by remember { mutableStateOf("bulk") }

    var isEditingStrategies by remember { mutableStateOf(false) }
    val strategyDrafts = remember { mutableStateMapOf<String, StrategyDraft>() }

    LaunchedEffect(state) {
        if (state is UiState.Success) {
            val d = state.data
            heightInput = if (d.heightCm % 1.0 == 0.0) d.heightCm.toInt().toString() else d.heightCm.toString()
            currentWeightInput = if (d.currentWeightKg % 1.0 == 0.0) d.currentWeightKg.toInt().toString() else d.currentWeightKg.toString()
            targetWeightInput = if (d.targetWeightKg % 1.0 == 0.0) d.targetWeightKg.toInt().toString() else d.targetWeightKg.toString()
            selectedGoal = if (d.goal.isNotEmpty()) d.goal else "bulk"

            val targetsMap = d.strategyTargets ?: emptyMap()
            GOALS.forEach { (key, _) ->
                val custom = targetsMap[key]
                if (custom != null && custom.kcal > 0) {
                    strategyDrafts[key] = StrategyDraft(
                        kcal = custom.kcal.toInt().toString(),
                        protein = custom.protein.toInt().toString(),
                        fat = custom.fat.toInt().toString(),
                        carbs = custom.carbs.toInt().toString()
                    )
                } else if (key == selectedGoal && d.targetKcal > 0) {
                    strategyDrafts[key] = StrategyDraft(
                        kcal = d.targetKcal.toInt().toString(),
                        protein = d.targetProtein.toInt().toString(),
                        fat = d.targetFat.toInt().toString(),
                        carbs = d.targetCarbs.toInt().toString()
                    )
                } else {
                    val curW = d.currentWeightKg.coerceAtLeast(40.0)
                    val (defKcal, defP, defF, defC) = when (key) {
                        "bulk" -> listOf(2800.0, 1.9 * curW, 1.1 * curW, ((2800.0 - 1.9 * curW * 4 - 1.1 * curW * 9) / 4))
                        "cut" -> listOf(2000.0, 2.3 * curW, 0.8 * curW, ((2000.0 - 2.3 * curW * 4 - 0.8 * curW * 9) / 4))
                        "recomp" -> listOf(2300.0, 2.2 * curW, 0.9 * curW, ((2300.0 - 2.2 * curW * 4 - 0.9 * curW * 9) / 4))
                        else -> listOf(2400.0, 1.8 * curW, 1.0 * curW, ((2400.0 - 1.8 * curW * 4 - 1.0 * curW * 9) / 4))
                    }
                    strategyDrafts[key] = StrategyDraft(
                        kcal = defKcal.toInt().toString(),
                        protein = defP.toInt().toString(),
                        fat = defF.toInt().toString(),
                        carbs = defC.toInt().coerceAtLeast(0).toString()
                    )
                }
            }
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

    val curWeight = remember(filteredHistory, currentWeightInput) {
        filteredHistory.lastOrNull()?.weightKg ?: currentWeightInput.toDoubleOrNull() ?: 75.0
    }

    val chartMinY by remember(filteredHistory, targetWeightVal, curWeight) {
        derivedStateOf {
            val avgWeight = (curWeight + targetWeightVal) / 2.0
            val minLogged = minOf(filteredHistory.minOfOrNull { it.weightKg } ?: curWeight, targetWeightVal)
            val seventyFivePercent = avgWeight * 0.75
            if (minLogged >= 60.0 && seventyFivePercent <= 62.0) {
                60f
            } else {
                val floorVal = minOf(seventyFivePercent, minLogged - 1.0)
                (floor(floorVal / 5.0) * 5.0).coerceAtLeast(0.0).toFloat()
            }
        }
    }

    val chartMaxY by remember(filteredHistory, targetWeightVal, curWeight) {
        derivedStateOf {
            val avgWeight = (curWeight + targetWeightVal) / 2.0
            val maxLogged = maxOf(filteredHistory.maxOfOrNull { it.weightKg } ?: curWeight, targetWeightVal)
            val candidate = maxOf(avgWeight * 1.08, maxLogged + 1.5)
            if (maxLogged <= 80.0 && candidate <= 82.0) {
                80f
            } else {
                (ceil(candidate / 5.0) * 5.0).toFloat()
            }
        }
    }

    LaunchedEffect(filteredHistory, targetWeightVal, chartMinY, chartMaxY) {
        if (filteredHistory.isNotEmpty()) {
            val rawValues: List<Float> = filteredHistory.map { it.weightKg.toFloat() }

            withContext(Dispatchers.Default) {
                modelProducer.runTransaction {
                    lineSeries {
                        if (hasSinglePoint) {
                            val singleVal = rawValues.firstOrNull() ?: targetWeightVal.toFloat()
                            series(listOf(singleVal, singleVal))
                            series(listOf(targetWeightVal.toFloat(), targetWeightVal.toFloat()))
                        } else {
                            series(rawValues)
                            series(List(rawValues.size) { targetWeightVal.toFloat() })
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
                                    val customMap = strategyDrafts.mapValues { (_, draft) ->
                                        MacroTargets(
                                            kcal = draft.kcal.toDoubleOrNull() ?: 0.0,
                                            protein = draft.protein.toDoubleOrNull() ?: 0.0,
                                            fat = draft.fat.toDoubleOrNull() ?: 0.0,
                                            carbs = draft.carbs.toDoubleOrNull() ?: 0.0
                                        )
                                    }
                                    val activeDraft = strategyDrafts[selectedGoal]
                                    val payload = UserDietSettings(
                                        userId = 1,
                                        heightCm = heightInput.toDoubleOrNull() ?: 174.0,
                                        currentWeightKg = currentWeightInput.toDoubleOrNull() ?: 70.0,
                                        targetWeightKg = targetWeightInput.toDoubleOrNull() ?: 78.0,
                                        goal = selectedGoal,
                                        targetKcal = activeDraft?.kcal?.toDoubleOrNull() ?: 0.0,
                                        targetProtein = activeDraft?.protein?.toDoubleOrNull() ?: 0.0,
                                        targetFat = activeDraft?.fat?.toDoubleOrNull() ?: 0.0,
                                        targetCarbs = activeDraft?.carbs?.toDoubleOrNull() ?: 0.0,
                                        strategyTargets = customMap
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
                        // 1. Wybór celu (Goal) i edycja makro per opcja
                        OutlinedCard(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Dietary Strategy", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = if (isEditingStrategies)
                                                "Edit target calories and macros per option below."
                                            else
                                                "Choose your objective. Tap the pencil to customize targets.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable { isEditingStrategies = !isEditingStrategies },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit Strategy Targets",
                                            tint = if (isEditingStrategies) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GOALS.forEach { (key, label) ->
                                        val isSelected = selectedGoal == key
                                        val draft = strategyDrafts[key] ?: StrategyDraft()

                                        OutlinedCard(
                                            onClick = { selectedGoal = key },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (isSelected) AccentBlue else MaterialTheme.colorScheme.outline),
                                            colors = CardDefaults.outlinedCardColors(
                                                containerColor = if (isSelected) AccentBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                            color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (isSelected) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = AccentBlue.copy(alpha = 0.15f)
                                                            ) {
                                                                Text(
                                                                    text = "Active",
                                                                    color = AccentBlue,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = { selectedGoal = key }
                                                    )
                                                }

                                                if (!isEditingStrategies) {
                                                    if (draft.kcal.isNotEmpty()) {
                                                        Text(
                                                            text = "${draft.kcal} kcal • ${draft.protein}g P • ${draft.fat}g F • ${draft.carbs}g C",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                    val subtext = when (key) {
                                                        "bulk" -> "Controlled surplus for strength & size gains"
                                                        "cut" -> "Fat loss with muscle retention (-400 kcal, high protein)"
                                                        "recomp" -> "Calorie cycling: surplus on workout days, deficit on rest"
                                                        else -> "Stable energy balance and weight maintenance"
                                                    }
                                                    Text(
                                                        text = subtext,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    OutlinedTextField(
                                                        value = draft.kcal,
                                                        onValueChange = { newVal ->
                                                            strategyDrafts[key] = draft.copy(kcal = newVal.filter { it.isDigit() })
                                                        },
                                                        label = { Text("Target Calories (kcal)") },
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        OutlinedTextField(
                                                            value = draft.protein,
                                                            onValueChange = { newVal ->
                                                                strategyDrafts[key] = draft.copy(protein = newVal.filter { it.isDigit() })
                                                            },
                                                            label = { Text("Protein (g)") },
                                                            singleLine = true,
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        OutlinedTextField(
                                                            value = draft.fat,
                                                            onValueChange = { newVal ->
                                                                strategyDrafts[key] = draft.copy(fat = newVal.filter { it.isDigit() })
                                                            },
                                                            label = { Text("Fat (g)") },
                                                            singleLine = true,
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        OutlinedTextField(
                                                            value = draft.carbs,
                                                            onValueChange = { newVal ->
                                                                strategyDrafts[key] = draft.copy(carbs = newVal.filter { it.isDigit() })
                                                            },
                                                            label = { Text("Carbs (g)") },
                                                            singleLine = true,
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }

                                                    val pVal = draft.protein.toIntOrNull() ?: 0
                                                    val fVal = draft.fat.toIntOrNull() ?: 0
                                                    val cVal = draft.carbs.toIntOrNull() ?: 0
                                                    val macroKcal = pVal * 4 + fVal * 9 + cVal * 4
                                                    Text(
                                                        text = "Macros total: $macroKcal kcal",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Wymiary ciała
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

                        // 3. Wykres wagi
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
                                                        rememberLine(
                                                            fill = LineCartesianLayer.LineFill.single(
                                                                fill(if (hasSinglePoint) Color.Transparent else AccentBlue)
                                                            ),
                                                            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0f),
                                                            areaFill = null
                                                        ),
                                                        rememberLine(
                                                            fill = LineCartesianLayer.LineFill.single(fill(TargetColor)),
                                                            thickness = 1.5.dp,
                                                            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0f),
                                                            areaFill = null
                                                        )
                                                    ),
                                                    axisValueOverrider = remember(chartMinY, chartMaxY) {
                                                        AxisValueOverrider.fixed(
                                                            minY = chartMinY.toDouble(),
                                                            maxY = chartMaxY.toDouble()
                                                        )
                                                    }
                                                ),
                                                startAxis = rememberStartAxis(
                                                    valueFormatter = { value, _, _ ->
                                                        val intVal = value.roundToInt()
                                                        if (intVal % 5 == 0) "$intVal" else ""
                                                    },
                                                    itemPlacer = remember { VerticalAxis.ItemPlacer.step({ 5.0 }) }
                                                ),
                                                bottomAxis = rememberBottomAxis()
                                            ),
                                            modelProducer = modelProducer,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (hasSinglePoint) {
                                            Canvas(modifier = Modifier.fillMaxSize().padding(start = 36.dp, bottom = 24.dp, top = 8.dp, end = 8.dp)) {
                                                val span = (chartMaxY - chartMinY).coerceAtLeast(1f)
                                                val yRatio = ((activePoint.weightKg.toFloat() - chartMinY) / span).coerceIn(0f, 1f)
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

                        // 4. Podgląd celów aktywnej strategii
                        val activeDraft = strategyDrafts[selectedGoal] ?: StrategyDraft()
                        val activeKcal = activeDraft.kcal.ifEmpty { "0" }
                        val activeP = activeDraft.protein.ifEmpty { "0" }
                        val activeF = activeDraft.fat.ifEmpty { "0" }
                        val activeC = activeDraft.carbs.ifEmpty { "0" }

                        OutlinedCard(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Active Daily Targets", style = MaterialTheme.typography.titleMedium)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AccentBlue.copy(alpha = 0.15f)
                                    ) {
                                        val goalLabel = GOALS.find { it.first == selectedGoal }?.second ?: selectedGoal.replaceFirstChar { it.uppercase() }
                                        Text(
                                            text = goalLabel,
                                            color = AccentBlue,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Target calories and macronutrients for your active dietary strategy ($selectedGoal).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(4.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Calories", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$activeKcal kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AccentBlue)
                                        }
                                        Column {
                                            Text("Carbs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${activeC}g", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Fats", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${activeF}g", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("Protein", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${activeP}g", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
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