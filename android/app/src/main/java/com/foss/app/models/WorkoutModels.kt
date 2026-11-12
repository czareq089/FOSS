package com.foss.app.models

import com.google.gson.annotations.SerializedName
import java.util.Locale

enum class ExerciseTrackingType {
    WEIGHT_AND_REPS,
    REPS_ONLY,
    TIME_ONLY,
    TIME_AND_DISTANCE
}

fun getExerciseTrackingType(typeString: String?): ExerciseTrackingType {
    val t = typeString?.trim()?.lowercase(Locale.ROOT) ?: return ExerciseTrackingType.WEIGHT_AND_REPS
    return when {
        t == "reps only" || t == "reps_only" -> ExerciseTrackingType.REPS_ONLY
        t == "time only" || t == "time_only" -> ExerciseTrackingType.TIME_ONLY
        t == "time & distance" || t == "time_distance" || t == "time and distance" || t == "cardio" -> ExerciseTrackingType.TIME_AND_DISTANCE
        else -> ExerciseTrackingType.WEIGHT_AND_REPS
    }
}

fun formatSecondsToTime(totalSeconds: Int): String {
    if (totalSeconds <= 0) return ""
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun parseTimeToSeconds(input: String): Int {
    val clean = input.trim()
    if (clean.isEmpty()) return 0
    if (clean.contains(":")) {
        val parts = clean.split(":")
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return minutes * 60 + seconds
    }
    return clean.toIntOrNull() ?: 0
}

data class Routine(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)

data class ExerciseInfo(
    @SerializedName("workout_exercise_id") val workoutExerciseId: Int,
    @SerializedName("exercise_id") val exerciseId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String = "Weight & Reps",
    @SerializedName("position") val position: Int,
    @SerializedName("template_sets") val templateSets: List<RoutineSet>?,
    @SerializedName("last_sets") val lastSets: List<LastSetValue>?,
    @SerializedName("best_weight_kg") val bestWeightKg: Double = 0.0,
    @SerializedName("best_reps") val bestReps: Int = 0
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
    @SerializedName("type") val type: String = "Weight & Reps",
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
    @SerializedName("rir") val rir: Int,
    @SerializedName("set_type") val setType: String = "standard",
    @SerializedName("time_seconds") val timeSeconds: Int = 0,
    @SerializedName("distance_km") val distanceKm: Double = 0.0
)

data class WorkoutSummary(
    @SerializedName("workout_id") val workoutId: Int,
    @SerializedName("date") val date: String,
    @SerializedName("routine_name") val routineName: String,
    @SerializedName("total_sets") val totalSets: Int = 0,
    @SerializedName("total_reps") val totalReps: Int = 0,
    @SerializedName("total_weight_kg") val totalWeightKg: Double = 0.0,
    @SerializedName("pr_count") val prCount: Int = 0
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

data class UpdateExerciseReq(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("equipment") val equipment: String
)

object ExerciseConstants {
    val TYPES = listOf(
        "Weight & Reps",
        "Reps Only",
        "Time & Distance",
        "Time Only",
        "Strength",
        "Cardio",
        "Stretching",
        "Plyometrics",
        "Powerlifting",
        "Olympic Weightlifting"
    )

    val EQUIPMENTS = listOf(
        "Barbell",
        "Dumbbell",
        "Machine",
        "Cable",
        "Body Only",
        "Kettlebell",
        "Bands",
        "Medicine Ball",
        "Exercise Ball",
        "E-Z Curl Bar",
        "Other"
    )
}

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
    @SerializedName("rir") val rir: Int,
    @SerializedName("set_type") val setType: String = "standard",
    @SerializedName("is_pr") val isPr: Boolean = false,
    @SerializedName("time_seconds") val timeSeconds: Int = 0,
    @SerializedName("distance_km") val distanceKm: Double = 0.0
)

data class WorkoutDetailExercise(
    @SerializedName("workout_exercise_id") val workoutExerciseId: Int,
    @SerializedName("exercise_id") val exerciseId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String = "Weight & Reps",
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

data class UpdateRoutineReq(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)

data class ConsistencyStats(
    @SerializedName("workout_dates") val workoutDates: List<String> = emptyList()
)