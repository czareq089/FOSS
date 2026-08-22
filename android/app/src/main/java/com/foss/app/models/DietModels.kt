package com.foss.app.models

import com.google.gson.annotations.SerializedName

data class DietProduct(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("barcode") val barcode: String? = null,
    @SerializedName("package_weight") val packageWeight: Double? = null,
    @SerializedName("serving_size") val servingSize: Double? = null,
    @SerializedName("kcal") val kcal: Double? = null,
    @SerializedName("protein") val protein: Double? = null,
    @SerializedName("fat") val fat: Double? = null,
    @SerializedName("carbs") val carbs: Double? = null
)

data class DietLogEntry(
    @SerializedName("id") val id: Int,
    @SerializedName("product_id") val productId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("amount") val amountG: Double,
    @SerializedName("servings_count") val servingsCount: Double? = null,
    @SerializedName("kcal") val kcal: Double = 0.0,
    @SerializedName("protein") val protein: Double = 0.0,
    @SerializedName("fat") val fat: Double = 0.0,
    @SerializedName("carbs") val carbs: Double = 0.0,
    @SerializedName("logged_at") val loggedAt: String
)

data class DailyDietSummary(
    @SerializedName("consumed_kcal") val consumedKcal: Double = 0.0,
    @SerializedName("consumed_p") val consumedP: Double = 0.0,
    @SerializedName("consumed_f") val consumedF: Double = 0.0,
    @SerializedName("consumed_c") val consumedC: Double = 0.0,
    @SerializedName("target_kcal") val targetKcal: Double = 2700.0,
    @SerializedName("target_p") val targetP: Double = 140.0,
    @SerializedName("target_f") val targetF: Double = 75.0,
    @SerializedName("target_c") val targetC: Double = 350.0,
    @SerializedName("logs") val logs: List<DietLogEntry> = emptyList()
)

data class LogDietRequest(
    @SerializedName("user_id") val userId: Int = 1,
    @SerializedName("product_id") val productId: Int,
    @SerializedName("amount") val amount: Double
)

data class CreateProductRequest(
    @SerializedName("name") val name: String,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("barcode") val barcode: String? = null,
    @SerializedName("package_weight") val packageWeight: Double? = null,
    @SerializedName("serving_size") val servingSize: Double? = null,
    @SerializedName("kcal") val kcal: Double? = null,
    @SerializedName("protein") val protein: Double? = null,
    @SerializedName("fat") val fat: Double? = null,
    @SerializedName("carbs") val carbs: Double? = null
)