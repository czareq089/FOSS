package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

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
	SetType           string  `json:"set_type"`
}

type Routine struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

type ExerciseInfo struct {
	WorkoutExerciseID int            `json:"workout_exercise_id"`
	ExerciseID        int            `json:"exercise_id"`
	Name              string         `json:"name"`
	Position          int            `json:"position"`
	TemplateSets      []RoutineSet   `json:"template_sets"`
	LastSets          []LastSetValue `json:"last_sets"`
}

type StartWorkoutRequest struct {
	RoutineID int `json:"routine_id"`
	UserID    int `json:"user_id"`
}

type StartWorkoutResponse struct {
	WorkoutID int            `json:"workout_id"`
	RoutineID int            `json:"routine_id"`
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
	RoutineExerciseID int            `json:"routine_exercise_id"`
	ExerciseID        int            `json:"exercise_id"`
	Name              string         `json:"name"`
	Position          int            `json:"position"`
	TemplateSets      []RoutineSet   `json:"template_sets"`
	LastSets          []LastSetValue `json:"last_sets"`
}

type ReorderPosition struct {
	ExerciseID int `json:"exercise_id"`
	Position   int `json:"position"`
}

type ReorderRequest struct {
	RoutineID int               `json:"routine_id"`
	Positions []ReorderPosition `json:"positions"`
}

type WorkoutReorderRequest struct {
	WorkoutID int               `json:"workout_id"`
	Positions []ReorderPosition `json:"positions"`
}

type SyncRoutineReq struct {
	RoutineID int `json:"routine_id"`
	WorkoutID int `json:"workout_id"`
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

type CreateExerciseReq struct {
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

type WorkoutDetailSet struct {
	SetID     int     `json:"set_id"`
	SetNumber int     `json:"set_number"`
	WeightKg  float64 `json:"weight_kg"`
	Reps      int     `json:"reps"`
	Rir       int     `json:"rir"`
}

type WorkoutDetailExercise struct {
	WorkoutExerciseID int                `json:"workout_exercise_id"`
	ExerciseID        int                `json:"exercise_id"`
	Name              string             `json:"name"`
	Position          int                `json:"position"`
	Sets              []WorkoutDetailSet `json:"sets"`
}

type WorkoutDetailResponse struct {
	WorkoutID   int                     `json:"workout_id"`
	RoutineName string                  `json:"routine_name"`
	Date        string                  `json:"date"`
	Exercises   []WorkoutDetailExercise `json:"exercises"`
}

type RoutineSet struct {
	SetNumber int    `json:"set_number"`
	SetType   string `json:"set_type"`
}

type UpdateRoutineSetsReq struct {
	RoutineExerciseID int          `json:"routine_exercise_id"`
	Sets              []RoutineSet `json:"sets"`
}

type ExerciseHistoryPoint struct {
	Date      string  `json:"date"`
	MaxWeight float64 `json:"max_weight"`
	EstOneRM  float64 `json:"est_one_rm"`
	Volume    float64 `json:"volume"`
}

type ExerciseDetailAnalytics struct {
	ExerciseID int                    `json:"exercise_id"`
	Name       string                 `json:"name"`
	Type       string                 `json:"type"`
	Equipment  string                 `json:"equipment"`
	History    []ExerciseHistoryPoint `json:"history"`
}

type RoutineHistoryPoint struct {
	WorkoutID int     `json:"workout_id"`
	Date      string  `json:"date"`
	VolumeKg  float64 `json:"volume_kg"`
	TotalReps int     `json:"total_reps"`
}

type RoutineAnalyticsResponse struct {
	RoutineID int                   `json:"routine_id"`
	Name      string                `json:"name"`
	History   []RoutineHistoryPoint `json:"history"`
}

type UpdateWorkoutDetailsReq struct {
	WorkoutID int                     `json:"workout_id"`
	Exercises []WorkoutDetailExercise `json:"exercises"`
}

type UserPlate struct {
	WeightKg float64 `json:"weight_kg"`
	Count    int     `json:"count"`
}

type UpdatePlatesReq struct {
	UserID int         `json:"user_id"`
	Plates []UserPlate `json:"plates"`
}

type UserAlgorithmSettings struct {
	UserID            int     `json:"user_id"`
	WarmupEnabled     bool    `json:"warmup_enabled"`
	WarmupBase        string  `json:"warmup_base"`
	DropEnabled       bool    `json:"drop_enabled"`
	DropPercentage    float64 `json:"drop_percentage"`
	BackoffEnabled    bool    `json:"backoff_enabled"`
	BackoffPercentage float64 `json:"backoff_percentage"`
}

type ConsistencyStatsResponse struct {
	WorkoutDates []string `json:"workout_dates"`
}

// ==========================================
// MODUŁ DIETY (STRUKTURY I HANDLERY JSON)
// ==========================================

type UserDietSettingsModel struct {
	UserID          int     `json:"user_id"`
	HeightCm        float64 `json:"height_cm"`
	CurrentWeightKg float64 `json:"current_weight_kg"`
	TargetWeightKg  float64 `json:"target_weight_kg"`
	TargetKcal      float64 `json:"target_kcal"`
	TargetProtein   float64 `json:"target_protein"`
	TargetFat       float64 `json:"target_fat"`
	TargetCarbs     float64 `json:"target_carbs"`
}

type DietProductItem struct {
	ID            int      `json:"id"`
	Name          string   `json:"name"`
	Brand         *string  `json:"brand"`
	Barcode       *string  `json:"barcode"`
	PackageWeight *float64 `json:"package_weight"`
	ServingSize   *float64 `json:"serving_size"`
	Kcal          *float64 `json:"kcal"`
	Protein       *float64 `json:"protein"`
	Fat           *float64 `json:"fat"`
	Carbs         *float64 `json:"carbs"`
}

type CreateProductReq struct {
	Name          string   `json:"name"`
	Brand         *string  `json:"brand"`
	Barcode       *string  `json:"barcode"`
	PackageWeight *float64 `json:"package_weight"`
	ServingSize   *float64 `json:"serving_size"`
	Kcal          *float64 `json:"kcal"`
	Protein       *float64 `json:"protein"`
	Fat           *float64 `json:"fat"`
	Carbs         *float64 `json:"carbs"`
}

type LogDietReq struct {
	UserID    int     `json:"user_id"`
	ProductID int     `json:"product_id"`
	AmountG   float64 `json:"amount"`
}

type DietLogItem struct {
	ID            int      `json:"id"`
	ProductID     int      `json:"product_id"`
	Name          string   `json:"name"`
	AmountG       float64  `json:"amount"`
	ServingsCount *float64 `json:"servings_count"`
	Kcal          float64  `json:"kcal"`
	Protein       float64  `json:"protein"`
	Fat           float64  `json:"fat"`
	Carbs         float64  `json:"carbs"`
	LoggedAt      string   `json:"logged_at"`
}

type DailyDietSummaryResp struct {
	ConsumedKcal float64       `json:"consumed_kcal"`
	ConsumedP    float64       `json:"consumed_p"`
	ConsumedF    float64       `json:"consumed_f"`
	ConsumedC    float64       `json:"consumed_c"`
	TargetKcal   float64       `json:"target_kcal"`
	TargetP      float64       `json:"target_p"`
	TargetF      float64       `json:"target_f"`
	TargetC      float64       `json:"target_c"`
	Logs         []DietLogItem `json:"logs"`
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
		rows, err := db.Query(`SELECT id, name FROM training_routines WHERE user_id = ? ORDER BY name COLLATE NOCASE ASC`, userID)
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

	var exists bool
	err = db.QueryRow(`SELECT EXISTS(SELECT 1 FROM training_workout_sets WHERE workout_exercise_id = ? AND set_number = ?)`,
		req.WorkoutExerciseID, req.SetNumber).Scan(&exists)

	if err != nil {
		http.Error(w, "Database query error", http.StatusInternalServerError)
		return
	}

	if exists {
		_, err = db.Exec(`UPDATE training_workout_sets SET reps = ?, weight_kg = ?, rir = ?, set_type = ? 
						  WHERE workout_exercise_id = ? AND set_number = ?`,
			req.Reps, req.WeightKg, req.RIR, req.SetType, req.WorkoutExerciseID, req.SetNumber)
	} else {
		_, err = db.Exec(`INSERT INTO training_workout_sets (workout_exercise_id, set_number, reps, weight_kg, rir, set_type) 
						  VALUES (?, ?, ?, ?, ?, ?)`,
			req.WorkoutExerciseID, req.SetNumber, req.Reps, req.WeightKg, req.RIR, req.SetType)
	}

	if err != nil {
		http.Error(w, "Failed to save set into database", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	fmt.Fprint(w, `{"status": "success", "message": "Set logged/updated successfully"}`)
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
		SELECT re.id, re.exercise_id, e.name, re.position
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
		if err := rows.Scan(&ex.RoutineExerciseID, &ex.ExerciseID, &ex.Name, &ex.Position); err != nil {
			continue
		}

		tRows, _ := db.Query(`SELECT set_number, set_type FROM training_routine_sets WHERE routine_exercise_id = ? ORDER BY set_number`, ex.RoutineExerciseID)
		ex.TemplateSets = []RoutineSet{}
		for tRows.Next() {
			var ts RoutineSet
			tRows.Scan(&ts.SetNumber, &ts.SetType)
			ex.TemplateSets = append(ex.TemplateSets, ts)
		}
		tRows.Close()

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
			JOIN training_workout_sets s ON s.workout_exercise_id = we.id
			WHERE w.user_id = ? AND we.exercise_id = ?
			GROUP BY we.id
			ORDER BY w.date DESC, w.id DESC LIMIT 1`, userID, exercises[i].ExerciseID).Scan(&lastWorkoutExerciseID)
		if err == nil {
			setRows, errSet := db.Query(`SELECT set_number, weight_kg, reps, rir FROM training_workout_sets WHERE workout_exercise_id = ? ORDER BY set_number`, lastWorkoutExerciseID)
			if errSet == nil {
				lastSets := []LastSetValue{}
				for setRows.Next() {
					var s LastSetValue
					setRows.Scan(&s.SetNumber, &s.WeightKg, &s.Reps, &s.Rir)
					lastSets = append(lastSets, s)
				}
				setRows.Close()
				exercises[i].LastSets = lastSets
			}
		}
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

func handleAPIWorkoutReorderExercises(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPatch {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req WorkoutReorderRequest
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
			UPDATE training_workout_exercises
			SET position = ?
			WHERE workout_id = ? AND exercise_id = ?`, p.Position, req.WorkoutID, p.ExerciseID); err != nil {
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
	SELECT re.id, re.exercise_id, e.name, re.position
	FROM training_routine_exercises re
	JOIN training_exercises e ON e.id = re.exercise_id
	WHERE re.routine_id = ?
	ORDER BY re.position`, req.RoutineID)
	if err != nil {
		http.Error(w, "Failed to fetch routine exercises", http.StatusInternalServerError)
		return
	}

	type routineExercise struct {
		id         int
		exerciseID int
		name       string
		position   int
	}
	var routineExercises []routineExercise
	for rows.Next() {
		var re routineExercise
		if err := rows.Scan(&re.id, &re.exerciseID, &re.name, &re.position); err != nil {
			continue
		}
		routineExercises = append(routineExercises, re)
	}
	rows.Close()

	exercises := []ExerciseInfo{}
	for _, re := range routineExercises {
		weRes, err := db.Exec(`INSERT INTO training_workout_exercises (workout_id, exercise_id, position) VALUES (?, ?, ?)`, workoutID, re.exerciseID, re.position)
		if err != nil {
			continue
		}
		weID, _ := weRes.LastInsertId()

		tSets := []RoutineSet{}
		tRows, errSets := db.Query(`SELECT set_number, set_type FROM training_routine_sets WHERE routine_exercise_id = ? ORDER BY set_number ASC`, re.id)
		if errSets == nil {
			for tRows.Next() {
				var ts RoutineSet
				if err := tRows.Scan(&ts.SetNumber, &ts.SetType); err == nil {
					tSets = append(tSets, ts)
				}
			}
			tRows.Close()
		}

		lSets := []LastSetValue{}
		var lastWeID int
		err = db.QueryRow(`
			SELECT we.id 
			FROM training_workout_exercises we 
			JOIN training_workouts w ON w.id = we.workout_id 
			JOIN training_workout_sets s ON s.workout_exercise_id = we.id
			WHERE w.user_id = ? AND we.exercise_id = ? AND w.id != ? 
			GROUP BY we.id
			ORDER BY w.date DESC, w.id DESC LIMIT 1`, req.UserID, re.exerciseID, workoutID).Scan(&lastWeID)
		if err == nil {
			lsRows, _ := db.Query(`SELECT set_number, weight_kg, reps, rir FROM training_workout_sets WHERE workout_exercise_id = ? ORDER BY set_number`, lastWeID)
			for lsRows.Next() {
				var ls LastSetValue
				lsRows.Scan(&ls.SetNumber, &ls.WeightKg, &ls.Reps, &ls.Rir)
				lSets = append(lSets, ls)
			}
			lsRows.Close()
		}

		exercises = append(exercises, ExerciseInfo{
			WorkoutExerciseID: int(weID),
			ExerciseID:        re.exerciseID,
			Name:              re.name,
			Position:          re.position,
			TemplateSets:      tSets,
			LastSets:          lSets,
		})
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(StartWorkoutResponse{
		WorkoutID: int(workoutID),
		RoutineID: req.RoutineID,
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

func handleAPIExerciseCreate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req CreateExerciseReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON body", http.StatusBadRequest)
		return
	}

	if strings.TrimSpace(req.Name) == "" {
		http.Error(w, "Exercise name cannot be empty", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	res, err := db.Exec(`INSERT INTO training_exercises (name, type, equipment) VALUES (?, ?, ?)`,
		strings.TrimSpace(req.Name), strings.TrimSpace(req.Type), strings.TrimSpace(req.Equipment))
	if err != nil {
		http.Error(w, "Exercise already exists or database error", http.StatusConflict)
		return
	}

	newID, _ := res.LastInsertId()

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(ExerciseItem{
		ID:        int(newID),
		Name:      req.Name,
		Type:      req.Type,
		Equipment: req.Equipment,
	})
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

	tx, err := db.Begin()
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	var maxPos int
	_ = tx.QueryRow(`SELECT COALESCE(MAX(position), 0) FROM training_routine_exercises WHERE routine_id = ?`, req.RoutineID).Scan(&maxPos)

	res, err := tx.Exec(`INSERT INTO training_routine_exercises (routine_id, exercise_id, position, default_sets) VALUES (?, ?, ?, 3)`,
		req.RoutineID, req.ExerciseID, maxPos+1)
	if err != nil {
		tx.Rollback()
		http.Error(w, "Failed to add exercise", http.StatusInternalServerError)
		return
	}

	newReID, _ := res.LastInsertId()

	// Automatycznie wstawiamy 3 domyślne serie do szablonu nowo dodanego ćwiczenia
	for s := 1; s <= 3; s++ {
		_, err = tx.Exec(`INSERT INTO training_routine_sets (routine_exercise_id, set_number, set_type) VALUES (?, ?, 'standard')`, newReID, s)
		if err != nil {
			tx.Rollback()
			http.Error(w, "Failed to insert default routine sets", http.StatusInternalServerError)
			return
		}
	}

	if err := tx.Commit(); err != nil {
		http.Error(w, "Failed to commit transaction", http.StatusInternalServerError)
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

func handleAPIWorkoutDetails(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	workoutID := r.URL.Query().Get("workout_id")
	if workoutID == "" {
		http.Error(w, "Missing workout_id", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	var resp WorkoutDetailResponse

	err = db.QueryRow(`
		SELECT id, date, COALESCE((SELECT name FROM training_routines WHERE id = routine_id), 'Custom workout') 
		FROM training_workouts WHERE id = ?`, workoutID).Scan(&resp.WorkoutID, &resp.Date, &resp.RoutineName)

	if err != nil {
		http.Error(w, "Workout not found", http.StatusNotFound)
		return
	}

	resp.Exercises = []WorkoutDetailExercise{}

	rows, err := db.Query(`
		SELECT we.id, we.exercise_id, e.name, we.position 
		FROM training_workout_exercises we 
		JOIN training_exercises e ON e.id = we.exercise_id 
		WHERE we.workout_id = ? ORDER BY we.position`, workoutID)
	if err == nil {
		defer rows.Close()
		for rows.Next() {
			var ex WorkoutDetailExercise
			rows.Scan(&ex.WorkoutExerciseID, &ex.ExerciseID, &ex.Name, &ex.Position)
			ex.Sets = []WorkoutDetailSet{}

			setRows, errSet := db.Query(`
				SELECT id, set_number, weight_kg, reps, rir 
				FROM training_workout_sets 
				WHERE workout_exercise_id = ? ORDER BY set_number`, ex.WorkoutExerciseID)
			if errSet == nil {
				for setRows.Next() {
					var s WorkoutDetailSet
					setRows.Scan(&s.SetID, &s.SetNumber, &s.WeightKg, &s.Reps, &s.Rir)
					ex.Sets = append(ex.Sets, s)
				}
				setRows.Close()
			}
			resp.Exercises = append(resp.Exercises, ex)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

func handleAPIUpdateRoutineSets(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req UpdateRoutineSetsReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
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
		http.Error(w, "Transaction error", http.StatusInternalServerError)
		return
	}

	_, _ = tx.Exec(`DELETE FROM training_routine_sets WHERE routine_exercise_id = ?`, req.RoutineExerciseID)
	for _, s := range req.Sets {
		_, _ = tx.Exec(`INSERT INTO training_routine_sets (routine_exercise_id, set_number, set_type) VALUES (?, ?, ?)`, req.RoutineExerciseID, s.SetNumber, s.SetType)
	}
	_ = tx.Commit()
	w.WriteHeader(http.StatusOK)
}

func handleAPISyncRoutineFromWorkout(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req SyncRoutineReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}
	db, _ := sql.Open("sqlite", dbPath)
	defer db.Close()

	tx, _ := db.Begin()

	tx.Exec(`DELETE FROM training_routine_exercises WHERE routine_id = ?`, req.RoutineID)

	rows, _ := tx.Query(`SELECT id, exercise_id, position FROM training_workout_exercises WHERE workout_id = ? ORDER BY position`, req.WorkoutID)
	type wEx struct {
		id   int
		exID int
		pos  int
	}
	var wExercises []wEx
	for rows.Next() {
		var we wEx
		rows.Scan(&we.id, &we.exID, &we.pos)
		wExercises = append(wExercises, we)
	}
	rows.Close()

	for _, we := range wExercises {
		res, _ := tx.Exec(`INSERT INTO training_routine_exercises (routine_id, exercise_id, position, default_sets) VALUES (?, ?, ?, 3)`, req.RoutineID, we.exID, we.pos)
		newReID, _ := res.LastInsertId()

		setRows, _ := tx.Query(`SELECT set_number, set_type FROM training_workout_sets WHERE workout_exercise_id = ? ORDER BY set_number`, we.id)
		for setRows.Next() {
			var sNum int
			var sType string
			setRows.Scan(&sNum, &sType)
			tx.Exec(`INSERT INTO training_routine_sets (routine_exercise_id, set_number, set_type) VALUES (?, ?, ?)`, newReID, sNum, sType)
		}
		setRows.Close()
	}

	tx.Commit()
	w.WriteHeader(http.StatusOK)
}

func handleAPIExerciseAnalytics(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	exerciseID := r.URL.Query().Get("exercise_id")
	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}
	rangeParam := r.URL.Query().Get("range")

	var interval string
	switch rangeParam {
	case "1m":
		interval = "-1 month"
	case "3m":
		interval = "-3 month"
	case "6m":
		interval = "-6 month"
	case "1y":
		interval = "-1 year"
	default:
		interval = ""
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	var resp ExerciseDetailAnalytics
	err = db.QueryRow(`SELECT id, name, type, equipment FROM training_exercises WHERE id = ?`, exerciseID).
		Scan(&resp.ExerciseID, &resp.Name, &resp.Type, &resp.Equipment)
	if err != nil {
		http.Error(w, "Exercise not found", http.StatusNotFound)
		return
	}

	query := `
		SELECT 
			w.date as log_date,
			COALESCE(MAX(s.weight_kg), 0) as max_w,
			COALESCE(MAX(s.weight_kg * (1.0 + (s.reps / 30.0))), 0) as max_1rm,
			COALESCE(SUM(s.weight_kg * s.reps), 0) as total_vol
		FROM training_workout_exercises we
		JOIN training_workouts w ON w.id = we.workout_id
		JOIN training_workout_sets s ON s.workout_exercise_id = we.id
		WHERE we.exercise_id = ? AND w.user_id = ?`

	var args []interface{}
	if interval == "" {
		query += ` GROUP BY w.id HAVING total_vol > 0 ORDER BY w.date ASC, w.id ASC`
		args = []interface{}{exerciseID, userID}
	} else {
		query += ` AND w.date >= datetime('now', ?) GROUP BY w.id HAVING total_vol > 0 ORDER BY w.date ASC, w.id ASC`
		args = []interface{}{exerciseID, userID, interval}
	}

	rows, err := db.Query(query, args...)
	if err != nil {
		http.Error(w, "Query execution error", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	resp.History = []ExerciseHistoryPoint{}
	for rows.Next() {
		var pt ExerciseHistoryPoint
		if err := rows.Scan(&pt.Date, &pt.MaxWeight, &pt.EstOneRM, &pt.Volume); err == nil {
			resp.History = append(resp.History, pt)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

func handleAPIRoutineAnalytics(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	routineID := r.URL.Query().Get("routine_id")
	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	var resp RoutineAnalyticsResponse
	var createdAt string
	err = db.QueryRow(`SELECT id, name, DATE(created_at) FROM training_routines WHERE id = ?`, routineID).
		Scan(&resp.RoutineID, &resp.Name, &createdAt)
	if err != nil {
		http.Error(w, "Routine not found", http.StatusNotFound)
		return
	}

	rows, err := db.Query(`
		SELECT 
			w.id,
			DATE(w.date) as log_date,
			COALESCE(SUM(s.weight_kg * s.reps), 0) as total_volume,
			COALESCE(SUM(s.reps), 0) as total_reps
		FROM training_workouts w
		JOIN training_workout_exercises we ON we.workout_id = w.id
		JOIN training_workout_sets s ON s.workout_exercise_id = we.id
		WHERE w.routine_id = ? AND w.user_id = ?
		GROUP BY w.id
		HAVING total_volume > 0
		ORDER BY w.date ASC, w.id ASC`, routineID, userID)
	if err != nil {
		http.Error(w, "Query error", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	resp.History = []RoutineHistoryPoint{}
	for rows.Next() {
		var pt RoutineHistoryPoint
		if err := rows.Scan(&pt.WorkoutID, &pt.Date, &pt.VolumeKg, &pt.TotalReps); err == nil {
			resp.History = append(resp.History, pt)
		}
	}
	if len(resp.History) > 0 && createdAt != "" && resp.History[0].Date > createdAt {
		zeroPoint := RoutineHistoryPoint{
			WorkoutID: 0,
			Date:      createdAt,
			VolumeKg:  0.0,
			TotalReps: 0,
		}
		resp.History = append([]RoutineHistoryPoint{zeroPoint}, resp.History...)
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

func handleAPIWorkoutUpdateDetails(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req UpdateWorkoutDetailsReq
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

	if _, err := tx.Exec(`
		DELETE FROM training_workout_sets 
		WHERE workout_exercise_id IN (
			SELECT id FROM training_workout_exercises WHERE workout_id = ?
		)`, req.WorkoutID); err != nil {
		tx.Rollback()
		http.Error(w, "Failed to clean old sets", http.StatusInternalServerError)
		return
	}

	if _, err := tx.Exec(`DELETE FROM training_workout_exercises WHERE workout_id = ?`, req.WorkoutID); err != nil {
		tx.Rollback()
		http.Error(w, "Failed to clean old exercises", http.StatusInternalServerError)
		return
	}

	for exPos, ex := range req.Exercises {
		res, err := tx.Exec(`
			INSERT INTO training_workout_exercises (workout_id, exercise_id, position) 
			VALUES (?, ?, ?)`, req.WorkoutID, ex.ExerciseID, exPos+1)
		if err != nil {
			tx.Rollback()
			http.Error(w, "Failed to insert workout exercise", http.StatusInternalServerError)
			return
		}
		newWeID, _ := res.LastInsertId()

		for sPos, s := range ex.Sets {
			_, err := tx.Exec(`
				INSERT INTO training_workout_sets (workout_exercise_id, set_number, weight_kg, reps, rir, set_type) 
				VALUES (?, ?, ?, ?, ?, 'standard')`,
				newWeID, sPos+1, s.WeightKg, s.Reps, s.Rir)
			if err != nil {
				tx.Rollback()
				http.Error(w, "Failed to insert workout set", http.StatusInternalServerError)
				return
			}
		}
	}

	if err := tx.Commit(); err != nil {
		http.Error(w, "Failed to commit changes", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
}

func handleAPIUserPlates(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	if r.Method == http.MethodGet {
		rows, err := db.Query(`SELECT weight_kg, count FROM user_plates WHERE user_id = ? ORDER BY weight_kg ASC`, userID)
		if err != nil {
			http.Error(w, "Database query error", http.StatusInternalServerError)
			return
		}
		defer rows.Close()

		var plates []UserPlate
		for rows.Next() {
			var p UserPlate
			if err := rows.Scan(&p.WeightKg, &p.Count); err == nil {
				plates = append(plates, p)
			}
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(plates)
		return
	}

	if r.Method == http.MethodPost {
		var req UpdatePlatesReq
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "Invalid body", http.StatusBadRequest)
			return
		}

		tx, err := db.Begin()
		if err != nil {
			http.Error(w, "Database error", http.StatusInternalServerError)
			return
		}

		_, _ = tx.Exec(`DELETE FROM user_plates WHERE user_id = ?`, req.UserID)
		for _, p := range req.Plates {
			if p.Count > 0 {
				_, _ = tx.Exec(`INSERT INTO user_plates (user_id, weight_kg, count) VALUES (?, ?, ?)`, req.UserID, p.WeightKg, p.Count)
			}
		}
		tx.Commit()
		w.WriteHeader(http.StatusOK)
		return
	}

	http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
}

func handleAPIUserAlgorithms(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	if r.Method == http.MethodGet {
		var s UserAlgorithmSettings
		s.UserID = 1
		s.WarmupEnabled = true
		s.WarmupBase = "first_working_set"
		s.DropEnabled = true
		s.DropPercentage = 20.0
		s.BackoffEnabled = true
		s.BackoffPercentage = 10.0

		err := db.QueryRow(`
			SELECT warmup_enabled, warmup_base, drop_enabled, drop_percentage, backoff_enabled, backoff_percentage 
			FROM user_algorithm_settings WHERE user_id = ?`, userID).
			Scan(&s.WarmupEnabled, &s.WarmupBase, &s.DropEnabled, &s.DropPercentage, &s.BackoffEnabled, &s.BackoffPercentage)

		if err != nil && err != sql.ErrNoRows {
			http.Error(w, "Database query error", http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(s)
		return
	}

	if r.Method == http.MethodPost {
		var s UserAlgorithmSettings
		if err := json.NewDecoder(r.Body).Decode(&s); err != nil {
			http.Error(w, "Invalid body", http.StatusBadRequest)
			return
		}

		_, err := db.Exec(`
			INSERT INTO user_algorithm_settings (user_id, warmup_enabled, warmup_base, drop_enabled, drop_percentage, backoff_enabled, backoff_percentage)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT(user_id) DO UPDATE SET
				warmup_enabled = excluded.warmup_enabled,
				warmup_base = excluded.warmup_base,
				drop_enabled = excluded.drop_enabled,
				drop_percentage = excluded.drop_percentage,
				backoff_enabled = excluded.backoff_enabled,
				backoff_percentage = excluded.backoff_percentage`,
			s.UserID, s.WarmupEnabled, s.WarmupBase, s.DropEnabled, s.DropPercentage, s.BackoffEnabled, s.BackoffPercentage)

		if err != nil {
			http.Error(w, "Database save error", http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusOK)
		return
	}

	http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
}

func handleGetConsistencyStats(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	query := `
       SELECT DISTINCT date(date)
       FROM training_workouts
       WHERE date >= date('now', '-120 days')
       ORDER BY date(date) ASC
    `
	rows, err := db.Query(query)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var dates []string
	for rows.Next() {
		var d string
		if err := rows.Scan(&d); err == nil {
			dates = append(dates, d)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(ConsistencyStatsResponse{
		WorkoutDates: dates,
	})
}

// -------------------------------------------------------------
// ENDPOINTY MODUŁU DIETY
// -------------------------------------------------------------

func handleAPIUserDietSettings(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	if r.Method == http.MethodGet {
		var s UserDietSettingsModel
		s.UserID = 1
		s.HeightCm = 174.0
		s.TargetWeightKg = 78.0
		s.TargetKcal = 2700.0
		s.TargetProtein = 140.0
		s.TargetFat = 75.0
		s.TargetCarbs = 350.0

		_ = db.QueryRow(`
			SELECT weight_kg FROM user_daily_metrics 
			WHERE user_id = ? AND weight_kg > 0 
			ORDER BY date DESC, id DESC LIMIT 1`, userID).Scan(&s.CurrentWeightKg)

		if s.CurrentWeightKg == 0 {
			s.CurrentWeightKg = 70.0
		}

		var surplus, pPerKg, fPerKg float64
		err := db.QueryRow(`
			SELECT height_cm, target_weight_kg, surplus_kcal, target_p_per_kg, target_f_per_kg
			FROM user_diet_settings WHERE user_id = ?`, userID).
			Scan(&s.HeightCm, &s.TargetWeightKg, &surplus, &pPerKg, &fPerKg)

		if err == nil {
			s.TargetKcal = 2400.0 + surplus
			s.TargetProtein = pPerKg * s.TargetWeightKg
			s.TargetFat = fPerKg * s.TargetWeightKg
			remainingCalories := s.TargetKcal - (s.TargetProtein*4.0 + s.TargetFat*9.0)
			if remainingCalories > 0 {
				s.TargetCarbs = remainingCalories / 4.0
			}
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(s)
		return
	}

	if r.Method == http.MethodPost {
		var req UserDietSettingsModel
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "Invalid body", http.StatusBadRequest)
			return
		}
		if req.UserID == 0 {
			req.UserID = 1
		}

		tx, err := db.Begin()
		if err != nil {
			http.Error(w, "Database error", http.StatusInternalServerError)
			return
		}

		if req.CurrentWeightKg > 0 {
			_, _ = tx.Exec(`
				INSERT INTO user_daily_metrics (user_id, date, weight_kg)
				VALUES (?, date('now'), ?)
				ON CONFLICT(user_id, date) DO UPDATE SET weight_kg = excluded.weight_kg`,
				req.UserID, req.CurrentWeightKg)
		}

		surplus := req.TargetKcal - 2400.0
		pPerKg := 2.0
		fPerKg := 1.0
		if req.TargetWeightKg > 0 {
			pPerKg = req.TargetProtein / req.TargetWeightKg
			fPerKg = req.TargetFat / req.TargetWeightKg
		}

		_, err = tx.Exec(`
			INSERT INTO user_diet_settings (user_id, height_cm, target_weight_kg, surplus_kcal, target_p_per_kg, target_f_per_kg, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
			ON CONFLICT(user_id) DO UPDATE SET
				height_cm = excluded.height_cm,
				target_weight_kg = excluded.target_weight_kg,
				surplus_kcal = excluded.surplus_kcal,
				target_p_per_kg = excluded.target_p_per_kg,
				target_f_per_kg = excluded.target_f_per_kg,
				updated_at = datetime('now')`,
			req.UserID, req.HeightCm, req.TargetWeightKg, surplus, pPerKg, fPerKg)

		if err != nil {
			tx.Rollback()
			http.Error(w, "Failed to save diet settings", http.StatusInternalServerError)
			return
		}

		_ = tx.Commit()
		w.WriteHeader(http.StatusOK)
		return
	}

	http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
}

func handleAPIDietProducts(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	rows, err := db.Query(`
		SELECT id, name, brand, barcode, package_weight, serving_size, kcal, protein, fat, carbs 
		FROM diet_products ORDER BY name ASC`)
	if err != nil {
		http.Error(w, "Failed to fetch products", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	products := []DietProductItem{}
	for rows.Next() {
		var p DietProductItem
		if err := rows.Scan(&p.ID, &p.Name, &p.Brand, &p.Barcode, &p.PackageWeight, &p.ServingSize, &p.Kcal, &p.Protein, &p.Fat, &p.Carbs); err == nil {
			products = append(products, p)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(products)
}

func handleAPIDietProductCreate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req CreateProductReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid body", http.StatusBadRequest)
		return
	}

	if strings.TrimSpace(req.Name) == "" {
		http.Error(w, "Product name cannot be empty", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	res, err := db.Exec(`
		INSERT INTO diet_products (name, brand, barcode, package_weight, serving_size, kcal, protein, fat, carbs)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		strings.TrimSpace(req.Name), req.Brand, req.Barcode, req.PackageWeight, req.ServingSize, req.Kcal, req.Protein, req.Fat, req.Carbs)

	if err != nil {
		http.Error(w, "Failed to save product", http.StatusInternalServerError)
		return
	}

	newID, _ := res.LastInsertId()
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(DietProductItem{
		ID:            int(newID),
		Name:          req.Name,
		Brand:         req.Brand,
		Barcode:       req.Barcode,
		PackageWeight: req.PackageWeight,
		ServingSize:   req.ServingSize,
		Kcal:          req.Kcal,
		Protein:       req.Protein,
		Fat:           req.Fat,
		Carbs:         req.Carbs,
	})
}

func handleAPIDietDaySummary(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}
	dateStr := r.URL.Query().Get("date")
	if dateStr == "" {
		dateStr = "now"
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	var resp DailyDietSummaryResp
	resp.TargetKcal = 2700.0
	resp.TargetP = 140.0
	resp.TargetF = 75.0
	resp.TargetC = 350.0
	resp.Logs = []DietLogItem{}

	var surplus, targetWeight, pPerKg, fPerKg float64
	err = db.QueryRow(`
		SELECT target_weight_kg, surplus_kcal, target_p_per_kg, target_f_per_kg 
		FROM user_diet_settings WHERE user_id = ?`, userID).
		Scan(&targetWeight, &surplus, &pPerKg, &fPerKg)

	if err == nil {
		resp.TargetKcal = 2400.0 + surplus
		resp.TargetP = pPerKg * targetWeight
		resp.TargetF = fPerKg * targetWeight
		remainingCalories := resp.TargetKcal - (resp.TargetP*4.0 + resp.TargetF*9.0)
		if remainingCalories > 0 {
			resp.TargetC = remainingCalories / 4.0
		}
	}

	rows, err := db.Query(`
		SELECT 
			l.id, l.product_id, p.name, l.amount,
			COALESCE(p.kcal, 0) * (l.amount / 100.0) as calculated_kcal,
			COALESCE(p.protein, 0) * (l.amount / 100.0) as calculated_p,
			COALESCE(p.fat, 0) * (l.amount / 100.0) as calculated_f,
			COALESCE(p.carbs, 0) * (l.amount / 100.0) as calculated_c,
			strftime('%H:%M', l.logged_at) as log_time,
			p.serving_size
		FROM diet_logs l
		JOIN diet_products p ON p.id = l.product_id
		WHERE l.user_id = ? AND date(l.logged_at) = date(?)
		ORDER BY l.logged_at DESC, l.id DESC`, userID, dateStr)

	if err == nil {
		defer rows.Close()
		for rows.Next() {
			var log DietLogItem
			var servSize *float64
			if err := rows.Scan(&log.ID, &log.ProductID, &log.Name, &log.AmountG, &log.Kcal, &log.Protein, &log.Fat, &log.Carbs, &log.LoggedAt, &servSize); err == nil {
				if servSize != nil && *servSize > 0 {
					val := log.AmountG / *servSize
					log.ServingsCount = &val
				}
				resp.ConsumedKcal += log.Kcal
				resp.ConsumedP += log.Protein
				resp.ConsumedF += log.Fat
				resp.ConsumedC += log.Carbs
				resp.Logs = append(resp.Logs, log)
			}
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

func handleAPIDietLog(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req LogDietReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid body", http.StatusBadRequest)
		return
	}

	if req.UserID == 0 {
		req.UserID = 1
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	_, err = db.Exec(`INSERT INTO diet_logs (user_id, product_id, amount) VALUES (?, ?, ?)`,
		req.UserID, req.ProductID, req.AmountG)
	if err != nil {
		http.Error(w, "Failed to log food", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusCreated)
}

func handleAPIDietLogDelete(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	logID := r.URL.Query().Get("id")
	if logID == "" {
		http.Error(w, "Missing id", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	_, err = db.Exec(`DELETE FROM diet_logs WHERE id = ?`, logID)
	if err != nil {
		http.Error(w, "Failed to delete log", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleGetDietConsistencyStats(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer db.Close()

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	query := `
       SELECT DISTINCT date(logged_at)
       FROM diet_logs
       WHERE user_id = ? AND date(logged_at) >= date('now', '-120 days')
       ORDER BY date(logged_at) ASC
    `
	rows, err := db.Query(query, userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var dates []string
	for rows.Next() {
		var d string
		if err := rows.Scan(&d); err == nil {
			dates = append(dates, d)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(ConsistencyStatsResponse{
		WorkoutDates: dates,
	})
}
