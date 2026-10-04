package com.foss.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.foss.app.navigation.BottomNavItem
import com.foss.app.navigation.FossBottomNavBar
import com.foss.app.screens.AutomationSettingsScreen
import com.foss.app.screens.DashboardScreen
import com.foss.app.screens.DietScreen
import com.foss.app.screens.DietSettingsScreen
import com.foss.app.screens.EquipmentScreen
import com.foss.app.screens.ExerciseDetailScreen
import com.foss.app.screens.ExerciseSelectionScreen
import com.foss.app.screens.PreferencesScreen
import com.foss.app.screens.ProfileSettingsScreen
import com.foss.app.screens.RoutineDetailScreen
import com.foss.app.screens.TrainingScreen
import com.foss.app.screens.WorkoutDetailScreen
import com.foss.app.screens.WorkoutLoggingScreen
import com.foss.app.ui.theme.FOSSTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FOSSTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FossApp()
                }
            }
        }
    }
}

@Composable
fun FossApp() {
    val navController = rememberNavController()
    val viewModel: AppViewModel = viewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = setOf(BottomNavItem.Dashboard.route, BottomNavItem.Training.route, BottomNavItem.Diet.route)
    val showBottomBar = currentRoute in topLevelRoutes
    val activeWorkoutId = viewModel.currentWorkoutId()
    val activeWorkoutRoutineName =
        (viewModel.routinesState.value as? UiState.Success)?.data
            ?.firstOrNull { it.id == viewModel.currentRoutineId() }?.name
            ?: viewModel.activeWorkoutRoutineName.value
            ?: "Active workout"
    val activeWorkoutStartedAt = viewModel.activeWorkoutStartedAtMillis.value
    val isWorkoutLoggingRoute = currentRoute == "workoutLogging/{workoutId}"
    val scope = rememberCoroutineScope()

    var footerElapsedSeconds by remember(activeWorkoutId) {
        mutableLongStateOf(viewModel.activeWorkoutElapsedSeconds())
    }
    var showCancelActiveWorkoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeWorkoutId, activeWorkoutStartedAt) {
        if (activeWorkoutId == null) {
            footerElapsedSeconds = 0L
            return@LaunchedEffect
        }
        while (true) {
            footerElapsedSeconds = viewModel.activeWorkoutElapsedSeconds()
            delay(1000L)
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                if (activeWorkoutId != null && !isWorkoutLoggingRoute) {
                    ActiveWorkoutBanner(
                        routineName = activeWorkoutRoutineName,
                        elapsedSeconds = footerElapsedSeconds,
                        onClick = {
                            navController.navigate("workoutLogging/$activeWorkoutId") {
                                launchSingleTop = true
                            }
                        },
                        onCancel = { showCancelActiveWorkoutDialog = true }
                    )
                }

                if (showBottomBar) {
                    FossBottomNavBar(navController = navController, currentRoute = currentRoute)
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Dashboard.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { 100 },
                    animationSpec = tween(150, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(150))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -100 },
                    animationSpec = tween(150, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(150))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -100 },
                    animationSpec = tween(150, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(150))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { 100 },
                    animationSpec = tween(150, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(150))
            }
        ) {
            composable(BottomNavItem.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onProfileClick = {
                        navController.navigate("profileSettings")
                    }
                )
            }

            composable(BottomNavItem.Training.route) {
                TrainingScreen(
                    viewModel = viewModel,
                    onRoutineSelected = { routine ->
                        navController.navigate("routineDetail/${routine.id}?edit=false")
                    },
                    onRoutineEdit = { routine ->
                        navController.navigate("routineDetail/${routine.id}?edit=true")
                    },
                    onWorkoutSelected = { workout ->
                        navController.navigate("workoutDetail/${workout.workoutId}?edit=false")
                    },
                    onWorkoutEdit = { workout ->
                        navController.navigate("workoutDetail/${workout.workoutId}?edit=true")
                    }
                )
            }

            composable(BottomNavItem.Diet.route) {
                DietScreen(viewModel = viewModel)
            }

            composable(
                route = "routineDetail/{routineId}?edit={edit}",
                arguments = listOf(
                    navArgument("routineId") { type = NavType.IntType },
                    navArgument("edit") { type = NavType.BoolType; defaultValue = false }
                )
            ) { entry ->
                val routineId = entry.arguments?.getInt("routineId") ?: return@composable
                val edit = entry.arguments?.getBoolean("edit") ?: false

                RoutineDetailScreen(
                    viewModel = viewModel,
                    routineId = routineId,
                    startInEditMode = edit,
                    onStartWorkout = { workoutId ->
                        navController.navigate("workoutLogging/$workoutId") {
                            popUpTo("routineDetail/${routineId}?edit=$edit") { inclusive = true }
                        }
                    },
                    onAddExerciseClick = {
                        navController.navigate("exerciseSelection/routine/$routineId")
                    },
                    onExerciseClick = { exerciseId ->
                        navController.navigate("exerciseDetail/$exerciseId")
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "workoutDetail/{workoutId}?edit={edit}",
                arguments = listOf(
                    navArgument("workoutId") { type = NavType.IntType },
                    navArgument("edit") { type = NavType.BoolType; defaultValue = false }
                )
            ) { entry ->
                val workoutId = entry.arguments?.getInt("workoutId") ?: return@composable
                val edit = entry.arguments?.getBoolean("edit") ?: false

                WorkoutDetailScreen(
                    viewModel = viewModel,
                    workoutId = workoutId,
                    startInEditMode = edit,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "exerciseSelection/{type}/{id}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType }
                )
            ) { entry ->
                val type = entry.arguments?.getString("type") ?: "routine"
                val id = entry.arguments?.getInt("id") ?: return@composable
                val scope = rememberCoroutineScope()

                ExerciseSelectionScreen(
                    viewModel = viewModel,
                    onExerciseSelected = { exerciseId ->
                        scope.launch {
                            if (type == "routine") {
                                val success = viewModel.addExerciseToRoutine(id, exerciseId)
                                if (success) {
                                    navController.navigate("routineDetail/$id?edit=true") {
                                        popUpTo("routineDetail/$id?edit=true") { inclusive = true }
                                    }
                                }
                            } else if (type == "workout") {
                                val success = viewModel.addExerciseToActiveWorkout(id, exerciseId)
                                if (success) navController.popBackStack()
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "workoutLogging/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = NavType.IntType })
            ) { entry ->
                val workoutId = entry.arguments?.getInt("workoutId") ?: return@composable
                WorkoutLoggingScreen(
                    viewModel = viewModel,
                    workoutId = workoutId,
                    onAddExerciseClick = {
                        navController.navigate("exerciseSelection/workout/$workoutId")
                    },
                    onExerciseClick = { exerciseId ->
                        navController.navigate("exerciseDetail/$exerciseId")
                    },
                    onLeaveWorkout = {
                        navController.navigate(BottomNavItem.Training.route) {
                            launchSingleTop = true
                        }
                    },
                    onFinish = {
                        viewModel.resetWorkoutState()
                        navController.navigate(BottomNavItem.Training.route) {
                            popUpTo(BottomNavItem.Dashboard.route)
                            launchSingleTop = true
                        }
                    },
                    onCancelWorkout = {
                        navController.navigate(BottomNavItem.Training.route) {
                            popUpTo(BottomNavItem.Dashboard.route)
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "exerciseDetail/{exerciseId}",
                arguments = listOf(navArgument("exerciseId") { type = NavType.IntType })
            ) { entry ->
                val exerciseId = entry.arguments?.getInt("exerciseId") ?: return@composable
                ExerciseDetailScreen(
                    viewModel = viewModel,
                    exerciseId = exerciseId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("profileSettings") {
                ProfileSettingsScreen(
                    viewModel = viewModel,
                    onNavigateToPreferences = {
                        navController.navigate("preferences")
                    },
                    onNavigateToEquipment = {
                        navController.navigate("equipment")
                    },
                    onNavigateToAutomation = {
                        navController.navigate("automationSettings")
                    },
                    onNavigateToDietSettings = {
                        navController.navigate("dietSettings")
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("preferences") {
                PreferencesScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("equipment") {
                EquipmentScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("automationSettings") {
                AutomationSettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("dietSettings") {
                DietSettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }


    if (showCancelActiveWorkoutDialog && activeWorkoutId != null) {
        AlertDialog(
            onDismissRequest = { showCancelActiveWorkoutDialog = false },
            title = {
                Text(
                    text = "Cancel workout?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Saved sets from this ongoing workout will be deleted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val workoutIdToCancel = activeWorkoutId
                        showCancelActiveWorkoutDialog = false
                        scope.launch {
                            viewModel.deleteWorkout(workoutIdToCancel)
                        }
                    }
                ) {
                    Text(
                        text = "Cancel workout",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelActiveWorkoutDialog = false }) {
                    Text(
                        text = "Go back",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

private fun formatWorkoutDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun ActiveWorkoutBanner(
    routineName: String,
    elapsedSeconds: Long,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routineName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Elapsed: ${formatWorkoutDuration(elapsedSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel workout",
                    tint = MaterialTheme.colorScheme.error
                )
            }

            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Return to workout",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
