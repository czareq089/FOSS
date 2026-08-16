package com.foss.app

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foss.app.models.*
import com.foss.app.network.NetworkModule
import kotlinx.coroutines.launch

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class WorkoutViewModel : ViewModel() {

    private val api = NetworkModule.api
    private val currentUserId = 1

    var routinesState = mutableStateOf<UiState<List<Routine>>>(UiState.Idle)
        private set
    var routineExercisesState = mutableStateOf<UiState<List<RoutineExercisePreview>>>(UiState.Idle)
        private set

    var workoutState = mutableStateOf<UiState<Triple<Int, Int, List<ExerciseInfo>>>>(UiState.Idle)
        private set

    var workoutHistoryState = mutableStateOf<UiState<List<WorkoutSummary>>>(UiState.Idle)
        private set
    var dashboardVolumeState = mutableStateOf<UiState<Double>>(UiState.Idle)
        private set
    var exercisesListState = mutableStateOf<UiState<List<ExerciseItem>>>(UiState.Idle)
        private set
    var workoutDetailsState = mutableStateOf<UiState<WorkoutDetailResponse>>(UiState.Idle)
        private set

    fun loadRoutines() {
        viewModelScope.launch {
            if (routinesState.value !is UiState.Success) {
                routinesState.value = UiState.Loading
            }
            try {
                val response = api.getRoutines(currentUserId)
                routinesState.value = if (response.isSuccessful && response.body() != null)
                    UiState.Success(response.body()!!) else UiState.Error("Server error: ${response.code()}")
            } catch (e: Exception) {
                routinesState.value = UiState.Error(e.message ?: "Unknown connection error")
            }
        }
    }

    fun loadRoutineExercises(routineId: Int) {
        viewModelScope.launch {
            routineExercisesState.value = UiState.Loading
            try {
                val response = api.getRoutineExercises(routineId, currentUserId)
                routineExercisesState.value = if (response.isSuccessful && response.body() != null)
                    UiState.Success(response.body()!!) else UiState.Error("Server error: ${response.code()}")
            } catch (e: Exception) {
                routineExercisesState.value = UiState.Error(e.message ?: "Unknown connection error")
            }
        }
    }

    suspend fun startWorkout(routineId: Int): Int? {
        workoutState.value = UiState.Loading
        return try {
            val response = api.startWorkout(StartWorkoutRequest(routineId, currentUserId))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                workoutState.value = UiState.Success(Triple(body.workoutId, body.routineId, body.exercises))
                body.workoutId
            } else {
                workoutState.value = UiState.Error("Server error: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            workoutState.value = UiState.Error(e.message ?: "Unknown connection error")
            null
        }
    }

    fun resumeWorkout(workoutId: Int) {
        val current = workoutState.value
        if (current is UiState.Success && current.data.first == workoutId) return

        viewModelScope.launch {
            workoutState.value = UiState.Loading
            try {
                val response = api.getWorkoutDetails(workoutId)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val mappedExercises = body.exercises.map { we ->
                        ExerciseInfo(
                            workoutExerciseId = we.workoutExerciseId,
                            exerciseId = we.exerciseId,
                            name = we.name,
                            position = we.position,
                            templateSets = null,
                            lastSets = we.sets.map { s ->
                                LastSetValue(s.setNumber, s.weightKg, s.reps, s.rir)
                            }
                        )
                    }
                    workoutState.value = UiState.Success(Triple(body.workoutId, 0, mappedExercises))
                } else {
                    workoutState.value = UiState.Error("Failed to restore workout")
                }
            } catch (e: Exception) {
                workoutState.value = UiState.Error(e.message ?: "Connection error")
            }
        }
    }

    fun resetWorkoutState() {
        workoutState.value = UiState.Idle
    }

    fun currentWorkoutId(): Int? = (workoutState.value as? UiState.Success)?.data?.first
    fun currentRoutineId(): Int? = (workoutState.value as? UiState.Success)?.data?.second

    fun setWorkoutExercises(exercises: List<ExerciseInfo>) {
        val currentState = workoutState.value
        if (currentState is UiState.Success) {
            workoutState.value = UiState.Success(Triple(currentState.data.first, currentState.data.second, exercises))
        }
    }

    suspend fun logSet(workoutExerciseId: Int, setNumber: Int, reps: Int, weightKg: Double, rir: Int, setType: String): Boolean {
        return try {
            api.logSet(
                SetLogRequest(workoutExerciseId, setNumber, reps, weightKg, rir, setType)
            ).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateRoutineSets(routineExerciseId: Int, sets: List<RoutineSet>): Boolean {
        return try {
            api.updateRoutineSets(UpdateRoutineSetsReq(routineExerciseId, sets)).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteWorkout(workoutId: Int): Boolean {
        return try {
            val ok = api.deleteWorkout(workoutId).isSuccessful
            if (ok) resetWorkoutState()
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun reorderWorkoutExercises(workoutId: Int, positions: List<ReorderPosition>): Boolean {
        return try {
            api.reorderWorkoutExercises(WorkoutReorderRequest(workoutId, positions)).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun syncRoutineFromWorkout(routineId: Int, workoutId: Int): Boolean {
        return try {
            api.syncRoutineFromWorkout(SyncRoutineReq(routineId, workoutId)).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    fun loadWorkoutHistory() {
        viewModelScope.launch {
            if (workoutHistoryState.value !is UiState.Success) {
                workoutHistoryState.value = UiState.Loading
            }
            try {
                val response = api.getWorkoutHistory(currentUserId)
                workoutHistoryState.value = if (response.isSuccessful && response.body() != null)
                    UiState.Success(response.body()!!) else UiState.Error("Server error: ${response.code()}")
            } catch (e: Exception) {
                workoutHistoryState.value = UiState.Error(e.message ?: "Unknown connection error")
            }
        }
    }

    suspend fun reorderExercises(routineId: Int, positions: List<ReorderPosition>): Boolean {
        return try {
            api.reorderExercises(ReorderRequest(routineId, positions)).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    fun loadDashboardVolume(range: String) {
        viewModelScope.launch {
            dashboardVolumeState.value = UiState.Loading
            try {
                val response = api.getDashboardVolume(range, currentUserId)
                val body = response.body()
                dashboardVolumeState.value = if (response.isSuccessful && body != null)
                    UiState.Success(body.volumeKg) else UiState.Error("Server error: ${response.code()}")
            } catch (e: Exception) {
                dashboardVolumeState.value = UiState.Error(e.message ?: "Unknown connection error")
            }
        }
    }

    fun loadAllExercises() {
        viewModelScope.launch {
            exercisesListState.value = UiState.Loading
            try {
                val response = api.getAllExercises()
                exercisesListState.value = if (response.isSuccessful && response.body() != null)
                    UiState.Success(response.body()!!) else UiState.Error("Server error: ${response.code()}")
            } catch (e: Exception) {
                exercisesListState.value = UiState.Error(e.message ?: "Unknown connection error")
            }
        }
    }

    suspend fun createExercise(name: String, type: String, equipment: String): ExerciseItem? {
        return try {
            val response = api.createExercise(CreateExerciseReq(name, type, equipment))
            if (response.isSuccessful && response.body() != null) {
                loadAllExercises()
                response.body()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun loadWorkoutDetails(workoutId: Int) {
        viewModelScope.launch {
            workoutDetailsState.value = UiState.Loading
            try {
                val response = api.getWorkoutDetails(workoutId)
                workoutDetailsState.value = if (response.isSuccessful && response.body() != null)
                    UiState.Success(response.body()!!) else UiState.Error("Server error: ${response.code()}")
            } catch (e: Exception) {
                workoutDetailsState.value = UiState.Error(e.message ?: "Unknown connection error")
            }
        }
    }

    suspend fun addExerciseToRoutine(routineId: Int, exerciseId: Int): Boolean {
        return try {
            api.addExerciseToRoutine(AddRoutineExerciseReq(routineId, exerciseId)).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeExerciseFromRoutine(routineId: Int, exerciseId: Int): Boolean {
        return try {
            api.removeExerciseFromRoutine(routineId, exerciseId).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createRoutine(name: String): Boolean {
        return try {
            api.createRoutine(CreateRoutineReq(userId = currentUserId, name = name)).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteRoutine(routineId: Int): Boolean {
        return try {
            api.deleteRoutine(routineId).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun addExerciseToActiveWorkout(workoutId: Int, exerciseId: Int): Boolean {
        return try {
            val response = api.addExerciseToWorkout(AddWorkoutExerciseReq(workoutId, exerciseId))
            if (response.isSuccessful && response.body() != null) {
                val currentState = workoutState.value
                if (currentState is UiState.Success) {
                    val currentList = currentState.data.third.toMutableList()
                    currentList.add(response.body()!!)
                    workoutState.value = UiState.Success(Triple(workoutId, currentState.data.second, currentList))
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    var exerciseAnalyticsState = mutableStateOf<UiState<ExerciseDetailAnalytics>>(UiState.Idle)
        private set

    fun loadExerciseAnalytics(exerciseId: Int, range: String = "all") {
        viewModelScope.launch {
            exerciseAnalyticsState.value = UiState.Loading
            try {
                val response = api.getExerciseAnalytics(exerciseId, range, currentUserId)
                exerciseAnalyticsState.value = if (response.isSuccessful && response.body() != null)
                    UiState.Success(response.body()!!) else UiState.Error("Server error: ${response.code()}")
            } catch (e: Exception) {
                exerciseAnalyticsState.value = UiState.Error(e.message ?: "Unknown connection error")
            }
        }
    }

    var routineAnalyticsState = mutableStateOf<UiState<RoutineAnalyticsResponse>>(UiState.Idle)
        private set

    fun loadRoutineAnalytics(routineId: Int) {
        viewModelScope.launch {
            routineAnalyticsState.value = UiState.Loading
            try {
                val response = api.getRoutineAnalytics(routineId, currentUserId)
                routineAnalyticsState.value = if (response.isSuccessful && response.body() != null)
                    UiState.Success(response.body()!!) else UiState.Error("Server error: ${response.code()}")
            } catch (e: Exception) {
                routineAnalyticsState.value = UiState.Error(e.message ?: "Unknown connection error")
            }
        }
    }

    suspend fun updateWorkoutDetails(workoutId: Int, exercises: List<WorkoutDetailExercise>): Boolean {
        return try {
            val response = api.updateWorkoutDetails(UpdateWorkoutDetailsReq(workoutId, exercises))
            if (response.isSuccessful) {
                loadWorkoutDetails(workoutId)
                loadWorkoutHistory()
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}