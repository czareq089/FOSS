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

    // FIX: Przechowuje teraz Triple(WorkoutId, RoutineId, ListaCwiczen)
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

    fun startWorkout(routineId: Int) {
        viewModelScope.launch {
            workoutState.value = UiState.Loading
            try {
                val response = api.startWorkout(StartWorkoutRequest(routineId, currentUserId))
                val body = response.body()
                workoutState.value = if (response.isSuccessful && body != null)
                    UiState.Success(Triple(body.workoutId, body.routineId, body.exercises)) else UiState.Error("Server error: ${response.code()}")
            } catch (e: Exception) {
                workoutState.value = UiState.Error(e.message ?: "Unknown connection error")
            }
        }
    }

    fun resetWorkoutState() {
        workoutState.value = UiState.Idle
    }

    fun currentWorkoutId(): Int? = (workoutState.value as? UiState.Success)?.data?.first
    fun currentRoutineId(): Int? = (workoutState.value as? UiState.Success)?.data?.second

    // Pozwala na sztywne zaktualizowanie lokalnego stanu po przesunięciach Drag & Drop
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
            api.deleteWorkout(workoutId).isSuccessful
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
}