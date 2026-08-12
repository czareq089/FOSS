package com.foss.app.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.foss.app.WorkoutViewModel
import com.foss.app.models.Routine
import com.foss.app.models.WorkoutSummary

private val TABS = listOf("Routines", "Workouts")

@Composable
fun TrainingScreen(
    viewModel: WorkoutViewModel,
    // FIX 1: Przywrócone parametry dla zakładki rutyn
    onRoutineSelected: (Routine) -> Unit,
    onRoutineEdit: (Routine) -> Unit,
    // Parametry dla zakładki historii treningów
    onWorkoutSelected: (WorkoutSummary) -> Unit,
    onWorkoutEdit: (WorkoutSummary) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.statusBarsPadding(),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) },
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.titleMedium) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> RoutinesScreen(
                    viewModel = viewModel,
                    onRoutineSelected = onRoutineSelected,
                    onRoutineEdit = onRoutineEdit,
                    showTopBar = false
                )
                1 -> WorkoutHistoryScreen(
                    viewModel = viewModel,
                    onWorkoutSelected = onWorkoutSelected,
                    onWorkoutEdit = onWorkoutEdit
                )
            }
        }
    }
}