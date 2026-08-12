package com.foss.app

import com.google.gson.annotations.SerializedName

data class SetLogRequest(
    @SerializedName("workout_exercise_id") val workoutExerciseId: Int,
    @SerializedName("set_number") val setNumber: Int,
    @SerializedName("reps") val reps: Int,
    @SerializedName("weight_kg") val weightKg: Double,
    @SerializedName("rir") val rir: Int,
    @SerializedName("set_type") val setType: String
)