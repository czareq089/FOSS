package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foss.app.UiState
import com.foss.app.AppViewModel
import com.foss.app.models.UserPlate
import com.foss.app.ui.theme.AccentBlue
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentScreen(
    viewModel: AppViewModel,
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
                title = { Text("Plate Inventory") },
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
                                    val ok = viewModel.saveUserPlates(platesList)
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
                        Button(onClick = { viewModel.loadUserPlates() }) { Text("Retry") }
                    }
                }
                is UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "Set the total quantity of each plate available. Plate-math calculators will strictly respect this inventory.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                            )
                        }

                        itemsIndexed(platesList) { index, plate ->
                            ModernPlateCard(
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
private fun ModernPlateCard(
    plate: UserPlate,
    onCountChange: (Int) -> Unit
) {
    val discSize = when {
        plate.weightKg >= 20.0 -> 48.dp
        plate.weightKg >= 10.0 -> 44.dp
        plate.weightKg >= 5.0 -> 40.dp
        plate.weightKg >= 2.5 -> 36.dp
        plate.weightKg >= 1.25 -> 32.dp
        else -> 28.dp
    }

    val hasPlates = plate.count > 0

    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (hasPlates) AccentBlue.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (hasPlates) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (hasPlates) AccentBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.5.dp, if (hasPlates) AccentBlue else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(discSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                shape = CircleShape,
                                color = if (hasPlates) AccentBlue else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(6.dp)
                            ) {}
                        }
                    }
                }

                Column {
                    Text(
                        text = String.format(Locale.US, "%.2f kg", plate.weightKg).replace(".00", ""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasPlates) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (plate.count >= 2) "${plate.count / 2} pair(s) • ${plate.count} pcs" else "${plate.count} pcs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    val minusSource = remember { MutableInteractionSource() }
                    val isMinusPressed by minusSource.collectIsPressedAsState()

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .alpha(if (plate.count == 0) 0.3f else if (isMinusPressed) 0.4f else 1f)
                            .clickable(
                                enabled = plate.count > 0,
                                interactionSource = minusSource,
                                indication = null
                            ) { onCountChange(plate.count - 1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Decrease",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "${plate.count}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (hasPlates) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .widthIn(min = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    val plusSource = remember { MutableInteractionSource() }
                    val isPlusPressed by plusSource.collectIsPressedAsState()

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .alpha(if (isPlusPressed) 0.4f else 1f)
                            .clickable(
                                interactionSource = plusSource,
                                indication = null
                            ) { onCountChange(plate.count + 1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Increase",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}