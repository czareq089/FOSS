package com.foss.app

import com.foss.app.models.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface FossApi {
    @GET("/api/routines")
    suspend fun getRoutines(@Query("user_id") userId: Int = 1): Response<List<Routine>>

    @DELETE("/api/routines")
    suspend fun deleteRoutine(@Query("id") routineId: Int): Response<Unit>

    @GET("/api/routines/exercises")
    suspend fun getRoutineExercises(
        @Query("routine_id") routineId: Int,
        @Query("user_id") userId: Int = 1
    ): Response<List<RoutineExercisePreview>>

    @PATCH("/api/routines/exercises/reorder")
    suspend fun reorderExercises(@Body request: ReorderRequest): Response<Unit>

    @PATCH("/api/workouts/exercises/reorder")
    suspend fun reorderWorkoutExercises(@Body request: WorkoutReorderRequest): Response<Unit>

    @POST("/api/workouts/start")
    suspend fun startWorkout(@Body request: StartWorkoutRequest): Response<StartWorkoutResponse>

    @POST("/api/workouts/log")
    suspend fun logSet(@Body request: SetLogRequest): Response<Unit>

    @GET("/api/workouts")
    suspend fun getWorkoutHistory(@Query("user_id") userId: Int = 1): Response<List<WorkoutSummary>>

    @DELETE("/api/workouts")
    suspend fun deleteWorkout(@Query("id") workoutId: Int): Response<Unit>

    @GET("/api/mobile/dashboard/volume")
    suspend fun getDashboardVolume(
        @Query("range") range: String,
        @Query("user_id") userId: Int = 1
    ): Response<VolumeResponse>

    @GET("/api/exercises")
    suspend fun getAllExercises(): Response<List<ExerciseItem>>

    @POST("/api/exercises/create")
    suspend fun createExercise(@Body request: CreateExerciseReq): Response<ExerciseItem>

    @POST("/api/routines/exercises/add")
    suspend fun addExerciseToRoutine(@Body request: AddRoutineExerciseReq): Response<Unit>

    @DELETE("/api/routines/exercises/remove")
    suspend fun removeExerciseFromRoutine(
        @Query("routine_id") routineId: Int,
        @Query("exercise_id") exerciseId: Int
    ): Response<Unit>

    @POST("/api/routines/create")
    suspend fun createRoutine(@Body request: CreateRoutineReq): Response<Unit>

    @POST("/api/workouts/exercises/add")
    suspend fun addExerciseToWorkout(@Body request: AddWorkoutExerciseReq): Response<ExerciseInfo>

    @GET("/api/workouts/details")
    suspend fun getWorkoutDetails(@Query("workout_id") workoutId: Int): Response<WorkoutDetailResponse>

    @POST("/api/routines/exercises/sets/update")
    suspend fun updateRoutineSets(@Body request: UpdateRoutineSetsReq): Response<Unit>

    @POST("/api/routines/sync")
    suspend fun syncRoutineFromWorkout(@Body request: SyncRoutineReq): Response<Unit>

    @GET("/api/exercises/analytics")
    suspend fun getExerciseAnalytics(
        @Query("exercise_id") exerciseId: Int,
        @Query("range") range: String = "all",
        @Query("user_id") userId: Int = 1
    ): Response<ExerciseDetailAnalytics>
}