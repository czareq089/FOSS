package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foss.app.AppViewModel
import com.foss.app.UiState
import com.foss.app.models.UserDietSettings
import com.foss.app.ui.theme.AccentBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietSettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadUserDietSettings()
    }

    val state = viewModel.userDietSettingsState.value
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diet & Metabolic Goals") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                        targetCarbs = targetCarbsInput.toDoubleOrNull() ?: 350.0
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