package com.foss.app

import com.google.gson.annotations.SerializedName

data class SetLogRequest(
    @SerializedName("workout_exercise_id") val workoutExerciseId: Int,
    @SerializedName("set_number") val setNumber: Int,
    @SerializedName("reps") val reps: Int,
    @SerializedName("weight_kg") val weightKg: Double,
    @SerializedName("rir") val rir: Int,
    @SerializedName("set_type") val setType: String,
    @SerializedName("time_seconds") val timeSeconds: Int = 0,
    @SerializedName("distance_km") val distanceKm: Double = 0.0
)

data class SetLogResponse(
    @SerializedName("status") val status: String = "success",
    @SerializedName("message") val message: String = "",
    @SerializedName("is_pr") val isPr: Boolean = false,
    @SerializedName("pr_type") val prType: String? = null,
    @SerializedName("pr_label") val prLabel: String? = null
)