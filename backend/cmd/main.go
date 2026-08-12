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
	http.HandleFunc("/api/dashboard/widgets", handleWebDashboardWidgets)
	http.HandleFunc("/api/dashboard/volume", handleWebDashboardVolume)

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
	http.HandleFunc("/api/routines/exercises/add", handleAPIRoutineExerciseAdd)
	http.HandleFunc("/api/routines/exercises/remove", handleAPIRoutineExerciseRemove)

	// Uruchomienie serwera
	port := ":8080"
	fmt.Printf("F.O.S.S. Server running at: http://localhost%s\n", port)
	if err := http.ListenAndServe(port, nil); err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
