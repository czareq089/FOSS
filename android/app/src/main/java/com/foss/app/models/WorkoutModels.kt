package com.foss.app.models

import com.google.gson.annotations.SerializedName

data class Routine(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)

data class ExerciseInfo(
    @SerializedName("workout_exercise_id") val workoutExerciseId: Int,
    @SerializedName("exercise_id") val exerciseId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("position") val position: Int,
    @SerializedName("template_sets") val templateSets: List<RoutineSet>?,
    @SerializedName("last_sets") val lastSets: List<LastSetValue>?
)

data class StartWorkoutRequest(
    @SerializedName("routine_id") val routineId: Int,
    @SerializedName("user_id") val userId: Int
)

data class StartWorkoutResponse(
    @SerializedName("workout_id") val workoutId: Int,
    @SerializedName("routine_id") val routineId: Int,
    @SerializedName("exercises") val exercises: List<ExerciseInfo>
)

data class RoutineExercisePreview(
    @SerializedName("routine_exercise_id") val routineExerciseId: Int,
    @SerializedName("exercise_id") val exerciseId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("position") val position: Int,
    @SerializedName("template_sets") val templateSets: List<RoutineSet>?,
    @SerializedName("last_sets") val lastSets: List<LastSetValue>?
) {
    @Transient
    private var _mutableTemplateSets: androidx.compose.runtime.snapshots.SnapshotStateList<RoutineSet>? = null

    val mutableTemplateSets: androidx.compose.runtime.snapshots.SnapshotStateList<RoutineSet>
        get() {
            if (_mutableTemplateSets == null) {
                _mutableTemplateSets = androidx.compose.runtime.mutableStateListOf<RoutineSet>().apply {
                    if (templateSets != null) addAll(templateSets) else add(RoutineSet(1, "standard"))
                }
            }
            return _mutableTemplateSets!!
        }
}

data class VolumeResponse(
    @SerializedName("volume_kg") val volumeKg: Double
)

data class LastSetValue(
    @SerializedName("set_number") val setNumber: Int,
    @SerializedName("weight_kg") val weightKg: Double,
    @SerializedName("reps") val reps: Int,
    @SerializedName("rir") val rir: Int
)

data class WorkoutSummary(
    @SerializedName("workout_id") val workoutId: Int,
    @SerializedName("date") val date: String,
    @SerializedName("routine_name") val routineName: String
)

data class ReorderPosition(
    @SerializedName("exercise_id") val exerciseId: Int,
    @SerializedName("position") val position: Int
)

data class ReorderRequest(
    @SerializedName("routine_id") val routineId: Int,
    @SerializedName("positions") val positions: List<ReorderPosition>
)

data class WorkoutReorderRequest(
    @SerializedName("workout_id") val workoutId: Int,
    @SerializedName("positions") val positions: List<ReorderPosition>
)

data class SyncRoutineReq(
    @SerializedName("routine_id") val routineId: Int,
    @SerializedName("workout_id") val workoutId: Int
)

data class ExerciseItem(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("equipment") val equipment: String
)

data class CreateExerciseReq(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("equipment") val equipment: String
)

data class AddRoutineExerciseReq(
    @SerializedName("routine_id") val routineId: Int,
    @SerializedName("exercise_id") val exerciseId: Int
)

data class CreateRoutineReq(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("name") val name: String
)

data class AddWorkoutExerciseReq(
    @SerializedName("workout_id") val workoutId: Int,
    @SerializedName("exercise_id") val exerciseId: Int
)

data class WorkoutDetailSet(
    @SerializedName("set_id") val setId: Int,
    @SerializedName("set_number") val setNumber: Int,
    @SerializedName("weight_kg") val weightKg: Double,
    @SerializedName("reps") val reps: Int,
    @SerializedName("rir") val rir: Int
)

data class WorkoutDetailExercise(
    @SerializedName("workout_exercise_id") val workoutExerciseId: Int,
    @SerializedName("exercise_id") val exerciseId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("position") val position: Int,
    @SerializedName("sets") val sets: List<WorkoutDetailSet>
)

data class WorkoutDetailResponse(
    @SerializedName("workout_id") val workoutId: Int,
    @SerializedName("routine_name") val routineName: String,
    @SerializedName("date") val date: String,
    @SerializedName("exercises") val exercises: List<WorkoutDetailExercise>
)

data class RoutineSet(
    @SerializedName("set_number") val setNumber: Int,
    @SerializedName("set_type") val setType: String
)

data class UpdateRoutineSetsReq(
    @SerializedName("routine_exercise_id") val routineExerciseId: Int,
    @SerializedName("sets") val sets: List<RoutineSet>
)

data class ExerciseHistoryPoint(
    @SerializedName("date") val date: String,
    @SerializedName("max_weight") val maxWeight: Double,
    @SerializedName("est_one_rm") val estOneRM: Double,
    @SerializedName("volume") val volume: Double
)

data class ExerciseDetailAnalytics(
    @SerializedName("exercise_id") val exerciseId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("equipment") val equipment: String,
    @SerializedName("history") val history: List<ExerciseHistoryPoint>
)

data class RoutineHistoryPoint(
    @SerializedName("workout_id") val workoutId: Int,
    @SerializedName("date") val date: String,
    @SerializedName("volume_kg") val volumeKg: Double,
    @SerializedName("total_reps") val totalReps: Int
)

data class RoutineAnalyticsResponse(
    @SerializedName("routine_id") val routineId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("history") val history: List<RoutineHistoryPoint>
)

data class UpdateWorkoutDetailsReq(
    @SerializedName("workout_id") val workoutId: Int,
    @SerializedName("exercises") val exercises: List<WorkoutDetailExercise>
)