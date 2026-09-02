package com.foss.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val DietOrangeColor = Color(0xFFFF8A00)

@Composable
fun DietConsistencyCard(
    dates: List<String>,
    modifier: Modifier = Modifier
) {
    ConsistencyHeatmapCard(
        title = "Diet Logging",
        datesStrings = dates,
        activeColor = DietOrangeColor,
        emptyText = "No meals logged yet",
        streakUnit = "wk",
        streakUnitPlural = "wks",
        modifier = modifier
    )
}