package main

import (
	"fmt"
	"log"
	"net/http"
)

const dbPath = "database/foss.db"

func main() {
	// ==========================================
	// 1. WEB HANDLERS (Dashboard HTML / HTMX)
	// ==========================================
	http.HandleFunc("/", handleWebIndex)
	http.HandleFunc("/api/dashboard/volume", handleWebDashboardVolume)
	http.HandleFunc("/api/widgets/volume", handleWidgetVolume)
	http.HandleFunc("/api/widgets/reps", handleWidgetReps)
	http.HandleFunc("/api/widgets/macros", handleWidgetMacros)
	http.HandleFunc("/api/widgets/top-exercises", handleWidgetTopExercises)
	http.HandleFunc("/api/widgets/biggest-progress", handleWidgetBiggestProgress)
	http.HandleFunc("/api/widgets/overall-progress", handleWidgetOverallProgress)
	http.HandleFunc("/api/widgets/macro-deficit", handleWidgetMacroDeficit)

	// ==========================================
	// 2. API HANDLERS (Aplikacja Mobilna JSON)
	// ==========================================
	http.HandleFunc("/api/routines", handleAPIRoutines)
	http.HandleFunc("/api/routines/exercises", handleAPIRoutineExercises)
	http.HandleFunc("/api/routines/exercises/reorder", handleAPIReorderExercises)
	http.HandleFunc("/api/workouts/start", handleAPIStartWorkout)
	http.HandleFunc("/api/workouts/log", handleAPILogSet)
	http.HandleFunc("/api/workouts", handleAPIWorkouts)
	http.HandleFunc("/api/mobile/dashboard/volume", handleAPIMobileDashboardVolume)
	http.HandleFunc("/api/exercises", handleAPIExercisesList)
	http.HandleFunc("/api/exercises/create", handleAPIExerciseCreate)
	http.HandleFunc("/api/routines/exercises/add", handleAPIRoutineExerciseAdd)
	http.HandleFunc("/api/routines/exercises/remove", handleAPIRoutineExerciseRemove)
	http.HandleFunc("/api/routines/create", handleAPIRoutineCreate)
	http.HandleFunc("/api/workouts/exercises/add", handleAPIWorkoutExerciseAdd)
	http.HandleFunc("/api/workouts/details", handleAPIWorkoutDetails)
	http.HandleFunc("/api/workouts/exercises/reorder", handleAPIWorkoutReorderExercises)
	http.HandleFunc("/api/routines/sync", handleAPISyncRoutineFromWorkout)
	http.HandleFunc("/api/exercises/analytics", handleAPIExerciseAnalytics)
	http.HandleFunc("/api/routines/analytics", handleAPIRoutineAnalytics)

	// Uruchomienie serwera
	port := ":8080"
	fmt.Printf("F.O.S.S. Server running at: http://localhost%s\n", port)
	if err := http.ListenAndServe(port, nil); err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
