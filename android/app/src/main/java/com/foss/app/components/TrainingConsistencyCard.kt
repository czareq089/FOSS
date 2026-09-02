package com.foss.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foss.app.ui.theme.AccentBlue

@Composable
fun TrainingConsistencyCard(
    dates: List<String>,
    modifier: Modifier = Modifier
) {
    ConsistencyHeatmapCard(
        title = "Trainings",
        datesStrings = dates,
        activeColor = AccentBlue,
        emptyText = "No workouts yet",
        streakUnit = "wk",
        streakUnitPlural = "wks",
        modifier = modifier
    )
}