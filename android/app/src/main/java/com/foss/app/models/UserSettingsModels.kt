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
    @SerializedName("warmup_base") val warmupBase: String = "first_working_set",
    @SerializedName("drop_enabled") val dropEnabled: Boolean = true,
    @SerializedName("drop_percentage") val dropPercentage: Double = 20.0,
    @SerializedName("backoff_enabled") val backoffEnabled: Boolean = true,
    @SerializedName("backoff_percentage") val backoffPercentage: Double = 10.0
)

data class PlateBreakdown(
    val perSideWeight: Double,
    val platesPerSide: List<Double>,
    val extraSinglePlate: Double? = null,
    val totalWeight: Double,
    val remainder: Double
)

object PlateCalculator {
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

    fun calculatePlatesPerSide(
        totalTargetWeight: Double,
        plates: List<UserPlate>
    ): PlateBreakdown {
        if (totalTargetWeight <= 0.0) {
            return PlateBreakdown(
                perSideWeight = 0.0,
                platesPerSide = emptyList(),
                extraSinglePlate = null,
                totalWeight = 0.0,
                remainder = 0.0
            )
        }

        val targetPerSide = totalTargetWeight / 2.0
        val availablePairs = plates.filter { it.count >= 2 }.sortedByDescending { it.weightKg }

        val resultPlates = mutableListOf<Double>()
        var remainingPerSide = targetPerSide

        for (plate in availablePairs) {
            val pairsAvailable = plate.count / 2
            var pairsUsed = 0
            while (pairsUsed < pairsAvailable && remainingPerSide >= (plate.weightKg - 0.001)) {
                resultPlates.add(plate.weightKg)
                remainingPerSide -= plate.weightKg
                pairsUsed++
            }
        }

        val achievedPerSide = targetPerSide - remainingPerSide
        var achievedTotal = achievedPerSide * 2.0
        var totalRemainder = totalTargetWeight - achievedTotal
        var extraSingle: Double? = null

        if (totalRemainder >= 0.05) {
            val singles = plates.filter { it.count > 0 }
                .sortedByDescending { it.weightKg }

            for (plate in singles) {
                val pairsUsed = resultPlates.count { it == plate.weightKg }
                val remainingCount = plate.count - (pairsUsed * 2)
                if (remainingCount > 0 && totalRemainder >= (plate.weightKg - 0.001)) {
                    extraSingle = plate.weightKg
                    achievedTotal += plate.weightKg
                    totalRemainder -= plate.weightKg
                    break
                }
            }
        }

        return PlateBreakdown(
            perSideWeight = achievedPerSide,
            platesPerSide = resultPlates,
            extraSinglePlate = extraSingle,
            totalWeight = achievedTotal,
            remainder = totalRemainder
        )
    }
}