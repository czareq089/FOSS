package com.foss.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foss.app.AppViewModel
import com.foss.app.UiState
import com.foss.app.models.CreateProductRequest
import com.foss.app.models.DailyDietSummary
import com.foss.app.models.DietLogEntry
import com.foss.app.models.DietProduct
import com.foss.app.ui.theme.AccentBlue
import kotlinx.coroutines.launch
import java.util.Locale

private val CarbsColor = Color(0xFF34D399)   // Green
private val FatsColor = Color(0xFFFFC107)    // Yellow
private val ProteinColor = Color(0xFFEF4444) // Red

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietScreen(
    viewModel: AppViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadDietData()
    }

    val summaryState = viewModel.dietSummaryState.value
    val productsState = viewModel.dietProductsState.value
    val scope = rememberCoroutineScope()

    var showFoodSheet by remember { mutableStateOf(false) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var prefillProductName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diet Tracker") },
                actions = {
                    val addSource = remember { MutableInteractionSource() }
                    val isAddPressed by addSource.collectIsPressedAsState()

                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .alpha(if (isAddPressed) 0.4f else 1f)
                            .clickable(
                                interactionSource = addSource,
                                indication = null,
                                onClick = { showFoodSheet = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Log Food",
                            tint = AccentBlue
                        )
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
            when (summaryState) {
                is UiState.Loading, UiState.Idle -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(summaryState.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadDietData() }) { Text("Retry") }
                    }
                }
                is UiState.Success -> {
                    val summary = summaryState.data
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            MacrosOverviewCard(summary = summary)
                        }

                        item {
                            Text(
                                text = "Logged Items",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                            )
                        }

                        if (summary.logs.isEmpty()) {
                            item {
                                Text(
                                    text = "No food logged yet today.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        } else {
                            items(summary.logs, key = { it.id }) { log ->
                                DietLogCard(
                                    log = log,
                                    onDelete = {
                                        scope.launch {
                                            viewModel.deleteFoodLog(log.id)
                                        }
                                    }
                                )
                            }
                        }

                        item {
                            OutlinedButton(
                                onClick = { showFoodSheet = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Log food item", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFoodSheet) {
        val products = (productsState as? UiState.Success)?.data ?: emptyList()
        AddFoodBottomSheet(
            products = products,
            onDismiss = { showFoodSheet = false },
            onAddProductClick = { query ->
                prefillProductName = query
                showFoodSheet = false
                showAddProductDialog = true
            },
            onFoodLogged = { product, finalGrams ->
                scope.launch {
                    viewModel.logFood(product.id, finalGrams)
                }
                showFoodSheet = false
            }
        )
    }

    if (showAddProductDialog) {
        AddProductDialog(
            initialName = prefillProductName,
            onDismiss = { showAddProductDialog = false },
            onProductAdded = { req ->
                scope.launch {
                    viewModel.createProduct(req)
                    showAddProductDialog = false
                    showFoodSheet = true
                }
            }
        )
    }
}

@Composable
private fun MacrosOverviewCard(summary: DailyDietSummary) {
    val remainingKcal = summary.targetKcal - summary.consumedKcal
    val isOverGoal = remainingKcal < 0
    val consumedColor = if (isOverGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${summary.consumedKcal.toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = consumedColor
                    )
                    Text(
                        text = "/ ${summary.targetKcal.toInt()} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Text(
                    text = if (!isOverGoal) "${remainingKcal.toInt()} left" else "${-remainingKcal.toInt()} over",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!isOverGoal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = {
                    if (summary.targetKcal > 0) (summary.consumedKcal / summary.targetKcal).toFloat().coerceIn(0f, 1f)
                    else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (isOverGoal) MaterialTheme.colorScheme.error else AccentBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroRow(label = "Carbs", consumed = summary.consumedC, target = summary.targetC, color = CarbsColor)
                MacroRow(label = "Fats", consumed = summary.consumedF, target = summary.targetF, color = FatsColor)
                MacroRow(label = "Protein", consumed = summary.consumedP, target = summary.targetP, color = ProteinColor)
            }
        }
    }
}

@Composable
private fun MacroRow(
    label: String,
    consumed: Double,
    target: Double,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format(Locale.US, "%.0f / %.0f g", consumed, target),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = {
                if (target > 0) (consumed / target).toFloat().coerceIn(0f, 1f)
                else 0f
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun DietLogCard(
    log: DietLogEntry,
    onDelete: () -> Unit
) {
    val deleteSource = remember { MutableInteractionSource() }
    val isDeletePressed by deleteSource.collectIsPressedAsState()

    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                val portionLabel = if (log.servingsCount != null && log.servingsCount > 0) {
                    val s = if (log.servingsCount % 1.0 == 0.0) "${log.servingsCount.toInt()} serv" else String.format(Locale.US, "%.1f serv", log.servingsCount)
                    "${log.amountG.toInt()} g ($s)"
                } else {
                    "${log.amountG.toInt()} g"
                }
                Text(
                    text = String.format(
                        Locale.US,
                        "%s • %.0f kcal (C: %.1f | F: %.1f | P: %.1f)",
                        portionLabel, log.kcal, log.carbs, log.fat, log.protein
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .alpha(if (isDeletePressed) 0.4f else 1f)
                    .clickable(
                        interactionSource = deleteSource,
                        indication = null,
                        onClick = onDelete
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFoodBottomSheet(
    products: List<DietProduct>,
    onDismiss: () -> Unit,
    onAddProductClick: (String) -> Unit,
    onFoodLogged: (DietProduct, Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<DietProduct?>(null) }
    var inputMode by remember { mutableStateOf("g") }
    var amountInput by remember { mutableStateOf("100") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedProduct == null) "Select Product" else "Log Portion",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (selectedProduct == null) {
                    val addProductSource = remember { MutableInteractionSource() }
                    val isAddProductPressed by addProductSource.collectIsPressedAsState()

                    Text(
                        text = "Add product",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue,
                        modifier = Modifier
                            .alpha(if (isAddProductPressed) 0.4f else 1f)
                            .clickable(
                                interactionSource = addProductSource,
                                indication = null,
                                onClick = { onAddProductClick(searchQuery.trim()) }
                            )
                            .padding(4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (selectedProduct == null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search food...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(Modifier.height(12.dp))

                val filtered = products.filter {
                    it.name.contains(searchQuery, ignoreCase = true) || (it.brand?.contains(searchQuery, ignoreCase = true) == true)
                }

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No products found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onAddProductClick(searchQuery.trim()) }) {
                            Text("Add product '${searchQuery.trim()}'", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered) { product ->
                            OutlinedCard(
                                onClick = {
                                    selectedProduct = product
                                    if (product.servingSize != null) {
                                        inputMode = "servings"
                                        amountInput = "1"
                                    } else {
                                        inputMode = "g"
                                        amountInput = "100"
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(product.name, fontWeight = FontWeight.SemiBold)
                                        val servingText = if (product.servingSize != null) " • Serv: ${product.servingSize.toInt()}g" else ""
                                        val kcalText = if (product.kcal != null) "${product.kcal.toInt()} kcal" else "No kcal"
                                        Text(
                                            "${product.brand ?: "Standard"} • $kcalText$servingText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val prod = selectedProduct!!
                val hasServing = prod.servingSize != null

                val kcalStr = prod.kcal?.toInt()?.toString() ?: "-"
                val carbsStr = prod.carbs?.toString() ?: "-"
                val fatStr = prod.fat?.toString() ?: "-"
                val proteinStr = prod.protein?.toString() ?: "-"

                Text(
                    text = "${prod.name} (C: $carbsStr | F: $fatStr | P: $proteinStr • $kcalStr kcal)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                if (hasServing) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = inputMode == "servings",
                            onClick = {
                                inputMode = "servings"
                                amountInput = "1"
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Servings (${prod.servingSize!!.toInt()}g)", fontSize = 12.sp)
                        }
                        SegmentedButton(
                            selected = inputMode == "g",
                            onClick = {
                                inputMode = "g"
                                amountInput = "100"
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Grams", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                if (prod.packageWeight != null) {
                    OutlinedButton(
                        onClick = {
                            inputMode = "g"
                            amountInput = prod.packageWeight.toInt().toString()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Whole pack (${prod.packageWeight.toInt()}g)", fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(if (inputMode == "servings") "Servings / Pieces" else "Weight (grams)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val enteredVal = amountInput.toDoubleOrNull() ?: 0.0
                val calculatedGrams = if (inputMode == "servings") enteredVal * (prod.servingSize ?: 0.0) else enteredVal
                val ratio = calculatedGrams / 100.0

                val liveKcal = ((prod.kcal ?: 0.0) * ratio).toInt()
                val liveCarbs = String.format(Locale.US, "%.1f", (prod.carbs ?: 0.0) * ratio)
                val liveFat = String.format(Locale.US, "%.1f", (prod.fat ?: 0.0) * ratio)
                val liveProtein = String.format(Locale.US, "%.1f", (prod.protein ?: 0.0) * ratio)

                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${calculatedGrams.toInt()} g • $liveKcal kcal",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("C: $liveCarbs", color = CarbsColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Text("F: $liveFat", color = FatsColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Text("P: $liveProtein", color = ProteinColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { selectedProduct = null },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            if (calculatedGrams > 0.0) {
                                onFoodLogged(prod, calculatedGrams)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Log Food")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProductDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onProductAdded: (CreateProductRequest) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var brand by remember { mutableStateOf("") }
    var packageWeight by remember { mutableStateOf("") }
    var servingSize by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add product", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product name *", fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand", fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = kcal,
                        onValueChange = { kcal = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Kcal", fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = servingSize,
                        onValueChange = { servingSize = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Serving", fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Carbs", fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Fat", fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Protein", fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = packageWeight,
                    onValueChange = { packageWeight = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Package weight", fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        val req = CreateProductRequest(
                            name = name.trim(),
                            brand = brand.trim().ifEmpty { null },
                            packageWeight = packageWeight.toDoubleOrNull(),
                            servingSize = servingSize.toDoubleOrNull(),
                            kcal = kcal.toDoubleOrNull(),
                            protein = protein.toDoubleOrNull(),
                            fat = fat.toDoubleOrNull(),
                            carbs = carbs.toDoubleOrNull()
                        )
                        onProductAdded(req)
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