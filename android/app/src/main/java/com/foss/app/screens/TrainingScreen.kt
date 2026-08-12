package com.foss.app.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.foss.app.WorkoutViewModel
import com.foss.app.models.Routine

private val TABS = listOf("Routines", "Workouts")

@Composable
fun TrainingScreen(
    viewModel: WorkoutViewModel,
    onRoutineSelected: (Routine) -> Unit,
    onRoutineEdit: (Routine) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        when (selectedTab) {
            0 -> RoutinesScreen(
                viewModel = viewModel,
                onRoutineSelected = onRoutineSelected,
                onRoutineEdit = onRoutineEdit,
                showTopBar = false
            )
            1 -> WorkoutHistoryScreen(viewModel = viewModel)
        }
    }
}