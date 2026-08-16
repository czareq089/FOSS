package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foss.app.UiState
import com.foss.app.WorkoutViewModel
import com.foss.app.models.UserPlate
import com.foss.app.ui.theme.AccentBlue
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentScreen(
    viewModel: WorkoutViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadUserPlates()
    }

    val state = viewModel.userPlatesState.value
    val platesList = remember { mutableStateListOf<UserPlate>() }
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is UiState.Success) {
            platesList.clear()
            platesList.addAll(state.data.map { it.copy() })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Available Plates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val ok = viewModel.saveUserPlates(platesList)
                                isSaving = false
                                if (ok) onBack()
                            }
                        },
                        enabled = !isSaving && state is UiState.Success
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = "Save")
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
                        Button(onClick = { viewModel.loadUserPlates() }) { Text("Retry") }
                    }
                }
                is UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "Specify the total quantity of each plate you have available. The algorithms will only calculate weights achievable with this inventory.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        itemsIndexed(platesList) { index, plate ->
                            PlateCounterCard(
                                plate = plate,
                                onCountChange = { newCount ->
                                    platesList[index] = plate.copy(count = newCount.coerceAtLeast(0))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlateCounterCard(
    plate: UserPlate,
    onCountChange: (Int) -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = String.format(Locale.US, "%.2f kg", plate.weightKg).replace(".00", ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentBlue
                )
                Text(
                    text = "${plate.count / 2} pairs available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(
                    onClick = { onCountChange(plate.count - 1) },
                    enabled = plate.count > 0,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                }

                Text(
                    text = "${plate.count}",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .widthIn(min = 40.dp)
                        .padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                OutlinedIconButton(
                    onClick = { onCountChange(plate.count + 1) },
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}