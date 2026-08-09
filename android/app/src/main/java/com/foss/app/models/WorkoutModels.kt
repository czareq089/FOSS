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
    @SerializedName("position") val position: Int
)

data class StartWorkoutRequest(
    @SerializedName("routine_id") val routineId: Int,
    @SerializedName("user_id") val userId: Int
)

data class StartWorkoutResponse(
    @SerializedName("workout_id") val workoutId: Int,
    @SerializedName("exercises") val exercises: List<ExerciseInfo>
)

data class RoutineExercisePreview(
    @SerializedName("exercise_id") val exerciseId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("position") val position: Int,
    @SerializedName("default_sets") val defaultSets: Int,
    @SerializedName("last_sets") val lastSets: List<LastSetValue>
)

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