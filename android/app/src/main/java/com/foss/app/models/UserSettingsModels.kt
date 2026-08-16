package com.foss.app.models

import com.google.gson.annotations.SerializedName

data class UserPlate(
    @SerializedName("weight_kg") val weightKg: Double,
    @SerializedName("count") var count: Int
)

data class UpdatePlatesReq(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("plates") val plates: List<UserPlate>
)

data class UserAlgorithmSettings(
    @SerializedName("user_id") val userId: Int = 1,
    @SerializedName("warmup_enabled") val warmupEnabled: Boolean = true,
    @SerializedName("warmup_base") val warmupBase: String = "first_working_set", // "first_working_set" | "heaviest_set"
    @SerializedName("drop_enabled") val dropEnabled: Boolean = true,
    @SerializedName("drop_percentage") val dropPercentage: Double = 20.0,
    @SerializedName("backoff_enabled") val backoffEnabled: Boolean = true,
    @SerializedName("backoff_percentage") val backoffPercentage: Double = 10.0
)

object PlateCalculator {
    /**
     * Zaokrągla targetWeight w dół/w górę do najbliższej możliwej wagi
     * do ułożenia z posiadanych par talerzy (z uwzględnieniem obustronności).
     */
    fun findClosestAchievableWeight(targetWeight: Double, plates: List<UserPlate>): Double {
        if (targetWeight <= 0.0) return 0.0
        val availablePairs = plates.filter { it.count >= 2 }.sortedByDescending { it.weightKg }
        if (availablePairs.isEmpty()) return targetWeight
        var achievable = setOf(0.0)
        for (plate in availablePairs) {
            val maxPairs = plate.count / 2
            val step = plate.weightKg * 2.0
            val nextAchievable = mutableSetOf<Double>()
            for (current in achievable) {
                for (k in 0..maxPairs) {
                    nextAchievable.add(current + (k * step))
                }
            }
            achievable = nextAchievable
        }

        val nonZeroAchievable = achievable.filter { it > 0.0 }
        if (nonZeroAchievable.isEmpty()) return targetWeight

        return nonZeroAchievable.minByOrNull { kotlin.math.abs(it - targetWeight) } ?: targetWeight
    }
}