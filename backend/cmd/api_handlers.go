package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net/http"

	_ "modernc.org/sqlite"
)

// ==========================================
// STRUKTURY DANYCH (JSON)
// ==========================================

type SetLogRequest struct {
	WorkoutExerciseID int     `json:"workout_exercise_id"`
	SetNumber         int     `json:"set_number"`
	Reps              int     `json:"reps"`
	WeightKg          float64 `json:"weight_kg"`
	RIR               int     `json:"rir"`
}

type Routine struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

type ExerciseInfo struct {
	WorkoutExerciseID int    `json:"workout_exercise_id"`
	ExerciseID        int    `json:"exercise_id"`
	Name              string `json:"name"`
	Position          int    `json:"position"`
}

type StartWorkoutRequest struct {
	RoutineID int `json:"routine_id"`
	UserID    int `json:"user_id"`
}

type StartWorkoutResponse struct {
	WorkoutID int            `json:"workout_id"`
	Exercises []ExerciseInfo `json:"exercises"`
}

type WorkoutSummary struct {
	WorkoutID   int    `json:"workout_id"`
	Date        string `json:"date"`
	RoutineName string `json:"routine_name"`
}

type LastSetValue struct {
	SetNumber int     `json:"set_number"`
	WeightKg  float64 `json:"weight_kg"`
	Reps      int     `json:"reps"`
	Rir       int     `json:"rir"`
}

type RoutineExercisePreview struct {
	ExerciseID  int            `json:"exercise_id"`
	Name        string         `json:"name"`
	Position    int            `json:"position"`
	DefaultSets int            `json:"default_sets"`
	LastSets    []LastSetValue `json:"last_sets"`
}

type ReorderPosition struct {
	ExerciseID int `json:"exercise_id"`
	Position   int `json:"position"`
}

type ReorderRequest struct {
	RoutineID int               `json:"routine_id"`
	Positions []ReorderPosition `json:"positions"`
}

type VolumeResponse struct {
	VolumeKg float64 `json:"volume_kg"`
}

type ExerciseItem struct {
	ID        int    `json:"id"`
	Name      string `json:"name"`
	Type      string `json:"type"`
	Equipment string `json:"equipment"`
}

type AddRoutineExerciseReq struct {
	RoutineID  int `json:"routine_id"`
	ExerciseID int `json:"exercise_id"`
}

type CreateRoutineReq struct {
	UserID int    `json:"user_id"`
	Name   string `json:"name"`
}

type AddWorkoutExerciseReq struct {
	WorkoutID  int `json:"workout_id"`
	ExerciseID int `json:"exercise_id"`
}

// ==========================================
// FUNKCJE OBSŁUGI API
// ==========================================

func handleAPIRoutines(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	switch r.Method {
	case http.MethodGet:
		userID := r.URL.Query().Get("user_id")
		if userID == "" {
			userID = "1"
		}
		rows, err := db.Query(`SELECT id, name FROM training_routines WHERE user_id = ? ORDER BY created_at DESC`, userID)
		if err != nil {
			http.Error(w, "Failed to fetch routines", http.StatusInternalServerError)
			return
		}
		defer rows.Close()

		routines := []Routine{}
		for rows.Next() {
			var rt Routine
			if err := rows.Scan(&rt.ID, &rt.Name); err != nil {
				continue
			}
			routines = append(routines, rt)
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(routines)

	case http.MethodDelete:
		routineID := r.URL.Query().Get("id")
		if routineID == "" {
			http.Error(w, "Missing id", http.StatusBadRequest)
			return
		}

		tx, err := db.Begin()
		if err != nil {
			http.Error(w, "Database error", http.StatusInternalServerError)
			return
		}

		if _, err := tx.Exec(`UPDATE training_workouts SET routine_id = NULL WHERE routine_id = ?`, routineID); err != nil {
			tx.Rollback()
			http.Error(w, "Failed to update workouts", http.StatusInternalServerError)
			return
		}
		if _, err := tx.Exec(`DELETE FROM training_routine_exercises WHERE routine_id = ?`, routineID); err != nil {
			tx.Rollback()
			http.Error(w, "Failed to delete routine exercises", http.StatusInternalServerError)
			return
		}
		if _, err := tx.Exec(`DELETE FROM training_routines WHERE id = ?`, routineID); err != nil {
			tx.Rollback()
			http.Error(w, "Failed to delete routine", http.StatusInternalServerError)
			return
		}

		if err := tx.Commit(); err != nil {
			http.Error(w, "Failed to commit deletion", http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusNoContent)

	default:
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
	}
}

func handleAPILogSet(w http.ResponseWriter, r *http.Request) {
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

	query := `INSERT INTO training_workout_sets (workout_exercise_id, set_number, reps, weight_kg, rir) 
			  VALUES (?, ?, ?, ?, ?)`

	_, err = db.Exec(query, req.WorkoutExerciseID, req.SetNumber, req.Reps, req.WeightKg, req.RIR)
	if err != nil {
		http.Error(w, "Failed to insert set into database", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	fmt.Fprint(w, `{"status": "success", "message": "Set logged successfully"}`)
}

func handleAPIRoutineExercises(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Only GET method is allowed", http.StatusMethodNotAllowed)
		return
	}

	routineID := r.URL.Query().Get("routine_id")
	if routineID == "" {
		http.Error(w, "Missing routine_id", http.StatusBadRequest)
		return
	}
	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	rows, err := db.Query(`
		SELECT re.exercise_id, e.name, re.position, re.default_sets
		FROM training_routine_exercises re
		JOIN training_exercises e ON e.id = re.exercise_id
		WHERE re.routine_id = ?
		ORDER BY re.position`, routineID)
	if err != nil {
		http.Error(w, "Failed to fetch routine exercises", http.StatusInternalServerError)
		return
	}

	exercises := []RoutineExercisePreview{}
	for rows.Next() {
		var ex RoutineExercisePreview
		if err := rows.Scan(&ex.ExerciseID, &ex.Name, &ex.Position, &ex.DefaultSets); err != nil {
			continue
		}
		ex.LastSets = []LastSetValue{}
		exercises = append(exercises, ex)
	}
	rows.Close()

	for i := range exercises {
		var lastWorkoutExerciseID int
		err := db.QueryRow(`
			SELECT we.id
			FROM training_workout_exercises we
			JOIN training_workouts w ON w.id = we.workout_id
			WHERE w.user_id = ? AND we.exercise_id = ?
			ORDER BY w.date DESC
			LIMIT 1`, userID, exercises[i].ExerciseID).Scan(&lastWorkoutExerciseID)
		if err != nil {
			continue
		}

		setRows, err := db.Query(`
			SELECT set_number, weight_kg, reps, rir
			FROM training_workout_sets
			WHERE workout_exercise_id = ?
			ORDER BY set_number`, lastWorkoutExerciseID)
		if err != nil {
			continue
		}
		var lastSets []LastSetValue
		for setRows.Next() {
			var s LastSetValue
			if err := setRows.Scan(&s.SetNumber, &s.WeightKg, &s.Reps, &s.Rir); err != nil {
				continue
			}
			lastSets = append(lastSets, s)
		}
		setRows.Close()
		exercises[i].LastSets = lastSets
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(exercises)
}

func handleAPIMobileDashboardVolume(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Only GET method is allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	var interval string
	switch r.URL.Query().Get("range") {
	case "1d":
		interval = "-1 day"
	case "7d":
		interval = "-7 day"
	case "1m":
		interval = "-1 month"
	case "all", "":
		interval = ""
	default:
		http.Error(w, "Invalid range", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	var query string
	var args []interface{}
	baseQuery := `
		SELECT COALESCE(SUM(s.weight_kg * s.reps), 0)
		FROM training_workout_sets s
		JOIN training_workout_exercises we ON s.workout_exercise_id = we.id
		JOIN training_workouts w ON we.workout_id = w.id
		WHERE w.user_id = ?`

	if interval == "" {
		query = baseQuery
		args = []interface{}{userID}
	} else {
		query = baseQuery + ` AND w.date >= datetime('now', ?)`
		args = []interface{}{userID, interval}
	}

	var volume float64
	if err := db.QueryRow(query, args...).Scan(&volume); err != nil {
		http.Error(w, "Failed to compute volume", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(VolumeResponse{VolumeKg: volume})
}

func handleAPIWorkouts(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	switch r.Method {
	case http.MethodGet:
		userID := r.URL.Query().Get("user_id")
		if userID == "" {
			userID = "1"
		}
		rows, err := db.Query(`
			SELECT w.id, w.date, COALESCE(r.name, 'Custom workout')
			FROM training_workouts w
			LEFT JOIN training_routines r ON r.id = w.routine_id
			WHERE w.user_id = ?
			ORDER BY w.date DESC`, userID)
		if err != nil {
			http.Error(w, "Failed to fetch workouts", http.StatusInternalServerError)
			return
		}
		defer rows.Close()

		workouts := []WorkoutSummary{}
		for rows.Next() {
			var ws WorkoutSummary
			if err := rows.Scan(&ws.WorkoutID, &ws.Date, &ws.RoutineName); err != nil {
				continue
			}
			workouts = append(workouts, ws)
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(workouts)

	case http.MethodDelete:
		workoutID := r.URL.Query().Get("id")
		if workoutID == "" {
			http.Error(w, "Missing id", http.StatusBadRequest)
			return
		}

		tx, err := db.Begin()
		if err != nil {
			http.Error(w, "Database error", http.StatusInternalServerError)
			return
		}

		if _, err := tx.Exec(`
			DELETE FROM training_workout_sets
			WHERE workout_exercise_id IN (
				SELECT id FROM training_workout_exercises WHERE workout_id = ?
			)`, workoutID); err != nil {
			tx.Rollback()
			http.Error(w, "Failed to delete sets", http.StatusInternalServerError)
			return
		}
		if _, err := tx.Exec(`DELETE FROM training_workout_exercises WHERE workout_id = ?`, workoutID); err != nil {
			tx.Rollback()
			http.Error(w, "Failed to delete workout exercises", http.StatusInternalServerError)
			return
		}
		if _, err := tx.Exec(`DELETE FROM training_workouts WHERE id = ?`, workoutID); err != nil {
			tx.Rollback()
			http.Error(w, "Failed to delete workout", http.StatusInternalServerError)
			return
		}
		if err := tx.Commit(); err != nil {
			http.Error(w, "Failed to commit deletion", http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusNoContent)

	default:
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
	}
}

func handleAPIReorderExercises(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPatch {
		http.Error(w, "Only PATCH method is allowed", http.StatusMethodNotAllowed)
		return
	}

	var req ReorderRequest
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

	tx, err := db.Begin()
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	for _, p := range req.Positions {
		if _, err := tx.Exec(`
			UPDATE training_routine_exercises
			SET position = ?
			WHERE routine_id = ? AND exercise_id = ?`, p.Position, req.RoutineID, p.ExerciseID); err != nil {
			tx.Rollback()
			http.Error(w, "Failed to update positions", http.StatusInternalServerError)
			return
		}
	}
	if err := tx.Commit(); err != nil {
		http.Error(w, "Failed to commit reorder", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func handleAPIStartWorkout(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Only POST method is allowed", http.StatusMethodNotAllowed)
		return
	}

	var req StartWorkoutRequest
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

	res, err := db.Exec(`INSERT INTO training_workouts (user_id, routine_id) VALUES (?, ?)`, req.UserID, req.RoutineID)
	if err != nil {
		http.Error(w, "Failed to create workout", http.StatusInternalServerError)
		return
	}
	workoutID, _ := res.LastInsertId()

	rows, err := db.Query(`
	SELECT re.exercise_id, e.name, re.position
	FROM training_routine_exercises re
	JOIN training_exercises e ON e.id = re.exercise_id
	WHERE re.routine_id = ?
	ORDER BY re.position`, req.RoutineID)
	if err != nil {
		http.Error(w, "Failed to fetch routine exercises", http.StatusInternalServerError)
		return
	}

	type routineExercise struct {
		exerciseID int
		name       string
		position   int
	}
	var routineExercises []routineExercise
	for rows.Next() {
		var re routineExercise
		if err := rows.Scan(&re.exerciseID, &re.name, &re.position); err != nil {
			continue
		}
		routineExercises = append(routineExercises, re)
	}
	rows.Close()

	exercises := []ExerciseInfo{}
	for _, re := range routineExercises {
		weRes, err := db.Exec(`INSERT INTO training_workout_exercises (workout_id, exercise_id, position) VALUES (?, ?, ?)`, workoutID, re.exerciseID, re.position)
		if err != nil {
			log.Printf("Failed to insert workout_exercise (workout_id=%d, exercise_id=%d): %v", workoutID, re.exerciseID, err)
			continue
		}
		weID, _ := weRes.LastInsertId()
		exercises = append(exercises, ExerciseInfo{
			WorkoutExerciseID: int(weID),
			ExerciseID:        re.exerciseID,
			Name:              re.name,
			Position:          re.position,
		})
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(StartWorkoutResponse{
		WorkoutID: int(workoutID),
		Exercises: exercises,
	})
}

func handleAPIExercisesList(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	rows, err := db.Query(`SELECT id, name, type, equipment FROM training_exercises ORDER BY name`)
	if err != nil {
		http.Error(w, "Query error", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var exercises []ExerciseItem
	for rows.Next() {
		var ex ExerciseItem
		if err := rows.Scan(&ex.ID, &ex.Name, &ex.Type, &ex.Equipment); err == nil {
			exercises = append(exercises, ex)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(exercises)
}

func handleAPIRoutineExerciseAdd(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req AddRoutineExerciseReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON body", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	// Pobieramy najwyższą pozycję w aktualnej rutynie
	var maxPos int
	_ = db.QueryRow(`SELECT COALESCE(MAX(position), 0) FROM training_routine_exercises WHERE routine_id = ?`, req.RoutineID).Scan(&maxPos)

	// Dodajemy ćwiczenie na pozycję maxPos + 1 (na sam koniec) z domyślną liczbą 3 serii
	_, err = db.Exec(`INSERT INTO training_routine_exercises (routine_id, exercise_id, position, default_sets) VALUES (?, ?, ?, 3)`,
		req.RoutineID, req.ExerciseID, maxPos+1)

	if err != nil {
		http.Error(w, "Failed to add exercise", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusOK)
}

func handleAPIRoutineExerciseRemove(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	routineID := r.URL.Query().Get("routine_id")
	exerciseID := r.URL.Query().Get("exercise_id")

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	_, err = db.Exec(`DELETE FROM training_routine_exercises WHERE routine_id = ? AND exercise_id = ?`, routineID, exerciseID)
	if err != nil {
		http.Error(w, "Failed to delete exercise", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func handleAPIRoutineCreate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req CreateRoutineReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON body", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	_, err = db.Exec(`INSERT INTO training_routines (user_id, name) VALUES (?, ?)`, req.UserID, req.Name)
	if err != nil {
		http.Error(w, "Failed to create routine", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
}

func handleAPIWorkoutExerciseAdd(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req AddWorkoutExerciseReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON body", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	var maxPos int
	_ = db.QueryRow(`SELECT COALESCE(MAX(position), 0) FROM training_workout_exercises WHERE workout_id = ?`, req.WorkoutID).Scan(&maxPos)

	res, err := db.Exec(`INSERT INTO training_workout_exercises (workout_id, exercise_id, position) VALUES (?, ?, ?)`,
		req.WorkoutID, req.ExerciseID, maxPos+1)
	if err != nil {
		http.Error(w, "Failed to add exercise to workout", http.StatusInternalServerError)
		return
	}

	weID, _ := res.LastInsertId()

	var exName string
	_ = db.QueryRow(`SELECT name FROM training_exercises WHERE id = ?`, req.ExerciseID).Scan(&exName)

	newExercise := ExerciseInfo{
		WorkoutExerciseID: int(weID),
		ExerciseID:        req.ExerciseID,
		Name:              exName,
		Position:          maxPos + 1,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(newExercise)
}
