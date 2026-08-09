package com.foss.app

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface FossApi {
    @POST("/api/workouts/log")
    suspend fun logSet(@Body request: SetLogRequest): Response<Unit>
}