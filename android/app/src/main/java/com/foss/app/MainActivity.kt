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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
import com.foss.app.screens.EquipmentScreen
import com.foss.app.screens.ExerciseDetailScreen
import com.foss.app.screens.ExerciseSelectionScreen
import com.foss.app.screens.ProfileSettingsScreen
import com.foss.app.screens.RoutineDetailScreen
import com.foss.app.screens.TrainingScreen
import com.foss.app.screens.WorkoutDetailScreen
import com.foss.app.screens.WorkoutLoggingScreen
import com.foss.app.ui.theme.FOSSTheme
import kotlinx.coroutines.launch

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

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                FossBottomNavBar(navController = navController, currentRoute = currentRoute)
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
                DietScreen()
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
                                if (success) navController.popBackStack()
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
                    onFinish = {
                        navController.navigate(BottomNavItem.Training.route) {
                            popUpTo(BottomNavItem.Dashboard.route)
                        }
                    },
                    onCancelWorkout = {
                        navController.navigate(BottomNavItem.Training.route) {
                            popUpTo(BottomNavItem.Dashboard.route)
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
                    onNavigateToEquipment = {
                        navController.navigate("equipment")
                    },
                    onNavigateToAutomation = {
                        navController.navigate("automationSettings")
                    },
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
        }
    }
}