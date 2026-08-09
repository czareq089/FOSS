package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net/http"

	_ "modernc.org/sqlite"
)

const dbPath = "database/foss.db"

// SetLogRequest reprezentuje paczkę JSON wysyłaną przez aplikację mobilną
type SetLogRequest struct {
	WorkoutExerciseID int     `json:"workout_exercise_id"`
	SetNumber         int     `json:"set_number"`
	Reps              int     `json:"reps"`
	WeightKg          float64 `json:"weight_kg"`
	RIR               int     `json:"rir"`
}

func main() {
	// 1. Serwowanie głównego widoku
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		http.ServeFile(w, r, "web/index.html")
	})

	// 2. Endpoint dla dashboardu - wyzerowane dane początkowe
	http.HandleFunc("/api/dashboard/widgets", func(w http.ResponseWriter, r *http.Request) {
		htmlResponse := `
		<!-- WIDGET 1: Lifted Volume -->
		<div class="widget">
			<h3>Volume Lifted (kg)</h3>
			<div role="group" style="margin-bottom: 1.5rem;">
				<button class="secondary outline" hx-get="/api/dashboard/volume?range=1d" hx-target="#volume-value">1 Day</button>
				<button class="secondary outline" hx-get="/api/dashboard/volume?range=7d" hx-target="#volume-value">7 Days</button>
				<button class="secondary outline" hx-get="/api/dashboard/volume?range=1m" hx-target="#volume-value">1 Month</button>
				<button class="secondary outline" hx-get="/api/dashboard/volume?range=all" hx-target="#volume-value">All Time</button>
			</div>
			<div id="volume-value" class="value">0</div>
		</div>

		<!-- WIDGET 2: Nutrition & Macros -->
		<div class="widget">
			<h3>Nutrition Target</h3>
			<div class="macro-label"><span>Calories</span><span>0 / 2700 kcal</span></div>
			<progress value="0" max="2700"></progress>
			<div class="macro-label"><span>Protein</span><span>0 / 140 g</span></div>
			<progress value="0" max="140"></progress>
			<div class="macro-label"><span>Fats</span><span>0 / 75 g</span></div>
			<progress value="0" max="75"></progress>
			<div class="macro-label"><span>Carbs</span><span>0 / 350 g</span></div>
			<progress value="0" max="350"></progress>
		</div>`
		fmt.Fprint(w, htmlResponse)
	})

	// 3. Endpoint dynamicznego ładownia objętości (wyzerowany)
	http.HandleFunc("/api/dashboard/volume", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, "0")
	})

	// 4. NOWOŚĆ: Odbiornik API dla aplikacji Android (Logowanie serii)
	http.HandleFunc("/api/workouts/log", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "Only POST method is allowed", http.StatusMethodNotAllowed)
			return
		}

		var req SetLogRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "Invalid JSON body", http.StatusBadRequest)
			return
		}

		db, err := sql.Open("sqlite", dbPath)
		if err != nil {
			http.Error(w, "Database connection error", http.StatusInternalServerError)
			return
		}
		defer db.Close()

		// Wstawianie odebranych danych do tabeli training_workout_sets
		query := `INSERT INTO training_workout_sets (workout_exercise_id, set_number, reps, weight_kg, rir) 
				  VALUES (?, ?, ?, ?, ?)`

		_, err = db.Exec(query, req.WorkoutExerciseID, req.SetNumber, req.Reps, req.WeightKg, req.RIR)
		if err != nil {
			http.Error(w, "Failed to insert set into database", http.StatusInternalServerError)
			return
		}

		// Zwracamy odpowiedź do mobilki o sukcesie
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		fmt.Fprint(w, `{"status": "success", "message": "Set logged successfully"}`)
	})

	port := ":8080"
	fmt.Printf("F.O.S.S. Server running at: http://localhost%s\n", port)
	if err := http.ListenAndServe(port, nil); err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
