package com.foss.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.foss.app.screens.DashboardScreen
import com.foss.app.screens.DietScreen
import com.foss.app.screens.RoutineDetailScreen
import com.foss.app.screens.TrainingScreen
import com.foss.app.screens.WorkoutLoggingScreen
import com.foss.app.ui.theme.FOSSTheme

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
    val viewModel: WorkoutViewModel = viewModel()

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
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Dashboard.route) {
                DashboardScreen()
            }

            composable(BottomNavItem.Training.route) {
                TrainingScreen(
                    viewModel = viewModel,
                    onRoutineSelected = { routine -> navController.navigate("routineDetail/${routine.id}") }
                )
            }

            composable(BottomNavItem.Diet.route) {
                DietScreen()
            }

            composable(
                route = "routineDetail/{routineId}",
                arguments = listOf(navArgument("routineId") { type = NavType.IntType })
            ) { entry ->
                val routineId = entry.arguments?.getInt("routineId") ?: return@composable
                RoutineDetailScreen(
                    viewModel = viewModel,
                    routineId = routineId,
                    onStartWorkout = { workoutId ->
                        navController.navigate("workoutLogging/$workoutId") {
                            popUpTo("routineDetail/$routineId") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "workoutLogging/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = NavType.IntType })
            ) {
                WorkoutLoggingScreen(
                    viewModel = viewModel,
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
        }
    }
}