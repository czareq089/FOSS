package main

import (
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"math"
	"net/http"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

func writeJSON(w http.ResponseWriter, status int, data any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(data); err != nil {
		log.Printf("writeJSON encode error: %v", err)
	}
}

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
	TimeSeconds       int     `json:"time_seconds"`
	DistanceKm        float64 `json:"distance_km"`
}

type Routine struct {
	ID         int    `json:"id"`
	Name       string `json:"name"`
	GroupName  string `json:"group_name"`
	IsArchived bool   `json:"is_archived"`
}

type ExerciseInfo struct {
	WorkoutExerciseID int            `json:"workout_exercise_id"`
	ExerciseID        int            `json:"exercise_id"`
	Name              string         `json:"name"`
	Type              string         `json:"type"`
	Position          int            `json:"position"`
	TemplateSets      []RoutineSet   `json:"template_sets"`
	LastSets          []LastSetValue `json:"last_sets"`
	BestWeightKg      float64        `json:"best_weight_kg"`
	BestReps          int            `json:"best_reps"`
	RestSeconds       int            `json:"rest_seconds"`
}

type SetLogResponse struct {
	Status  string `json:"status"`
	Message string `json:"message"`
	IsPr    bool   `json:"is_pr"`
	PrType  string `json:"pr_type,omitempty"`
	PrLabel string `json:"pr_label,omitempty"`
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
	WorkoutID     int     `json:"workout_id"`
	Date          string  `json:"date"`
	RoutineName   string  `json:"routine_name"`
	TotalSets     int     `json:"total_sets"`
	TotalReps     int     `json:"total_reps"`
	TotalWeightKg float64 `json:"total_weight_kg"`
	PrCount       int     `json:"pr_count"`
}

type LastSetValue struct {
	SetNumber   int     `json:"set_number"`
	WeightKg    float64 `json:"weight_kg"`
	Reps        int     `json:"reps"`
	Rir         int     `json:"rir"`
	SetType     string  `json:"set_type"`
	TimeSeconds int     `json:"time_seconds"`
	DistanceKm  float64 `json:"distance_km"`
}

type RoutineExercisePreview struct {
	RoutineExerciseID int            `json:"routine_exercise_id"`
	ExerciseID        int            `json:"exercise_id"`
	Name              string         `json:"name"`
	Type              string         `json:"type"`
	Position          int            `json:"position"`
	TemplateSets      []RoutineSet   `json:"template_sets"`
	LastSets          []LastSetValue `json:"last_sets"`
	RestSeconds       int            `json:"rest_seconds"`
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

type UpdateExerciseReq struct {
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
	UserID    int    `json:"user_id"`
	Name      string `json:"name"`
	GroupName string `json:"group_name,omitempty"`
}

type AddWorkoutExerciseReq struct {
	WorkoutID  int `json:"workout_id"`
	ExerciseID int `json:"exercise_id"`
}

type WorkoutDetailSet struct {
	SetID       int     `json:"set_id"`
	SetNumber   int     `json:"set_number"`
	WeightKg    float64 `json:"weight_kg"`
	Reps        int     `json:"reps"`
	Rir         int     `json:"rir"`
	SetType     string  `json:"set_type"`
	IsPr        bool    `json:"is_pr"`
	TimeSeconds int     `json:"time_seconds"`
	DistanceKm  float64 `json:"distance_km"`
}

type WorkoutDetailExercise struct {
	WorkoutExerciseID int                `json:"workout_exercise_id"`
	ExerciseID        int                `json:"exercise_id"`
	Name              string             `json:"name"`
	Type              string             `json:"type"`
	Position          int                `json:"position"`
	Sets              []WorkoutDetailSet `json:"sets"`
	RestSeconds       int                `json:"rest_seconds"`
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
	RestSeconds       *int         `json:"rest_seconds,omitempty"`
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

type UpdateRoutineReq struct {
	ID         int     `json:"id"`
	Name       string  `json:"name"`
	GroupName  *string `json:"group_name,omitempty"`
	IsArchived *bool   `json:"is_archived,omitempty"`
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
	ShowRIR           bool    `json:"show_rir"`
}

type WeightHistoryPoint struct {
	Date     string  `json:"date"`
	WeightKg float64 `json:"weight_kg"`
}

type DailyMetricsTodayResp struct {
	Date     string   `json:"date"`
	WeightKg *float64 `json:"weight_kg"`
}

type ConsistencyStatsResponse struct {
	WorkoutDates []string `json:"workout_dates"`
}

// ==========================================
// MODUŁ DIETY (STRUKTURY I HANDLERY JSON)
// ==========================================

type MacroTargets struct {
	Kcal    float64 `json:"kcal"`
	Protein float64 `json:"protein"`
	Fat     float64 `json:"fat"`
	Carbs   float64 `json:"carbs"`
}

type UserDietSettingsModel struct {
	UserID          int                     `json:"user_id"`
	HeightCm        float64                 `json:"height_cm"`
	CurrentWeightKg float64                 `json:"current_weight_kg"`
	TargetWeightKg  float64                 `json:"target_weight_kg"`
	Goal            string                  `json:"goal"`
	TargetKcal      float64                 `json:"target_kcal"`
	TargetProtein   float64                 `json:"target_protein"`
	TargetFat       float64                 `json:"target_fat"`
	TargetCarbs     float64                 `json:"target_carbs"`
	StrategyTargets map[string]MacroTargets `json:"strategy_targets,omitempty"`
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
	Date      string  `json:"date,omitempty"`
}

type CustomDietEntryReq struct {
	UserID  int     `json:"user_id"`
	Name    string  `json:"name"`
	Kcal    float64 `json:"kcal"`
	Carbs   float64 `json:"carbs"`
	Protein float64 `json:"protein"`
	Fat     float64 `json:"fat"`
	Date    string  `json:"date,omitempty"`
}

type UpdateDietLogAmountReq struct {
	LogID   int     `json:"log_id"`
	AmountG float64 `json:"amount"`
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
// SILNIK ALGORYTMÓW METABOLICZNYCH
// ==========================================

// calculateStaticDietTargets zwraca statyczne cele kaloryczne i makroskładniki
// skonfigurowane przez użytkownika. Nie stosuje żadnych dynamicznych modyfikatorów
// (np. bonusu treningowego, kroków, adaptacji wagi), zapewniając stałe wartości.
func calculateStaticDietTargets(db *sql.DB, userID string) (kcal, protein, fat, carbs float64, currentWeight float64, height float64, goal string, targetWeight float64, allStrategies map[string]MacroTargets) {
	height = 174.0
	targetWeight = 78.0
	goal = "maintain"

	var rawStrategyTargets sql.NullString
	var manualKcal, manualProtein, manualFat, manualCarbs sql.NullFloat64

	_ = db.QueryRow(`
       SELECT height_cm, target_weight_kg, COALESCE(goal, 'maintain'),
              strategy_targets, target_kcal, target_protein, target_fat, target_carbs
       FROM user_diet_settings WHERE user_id = ?`, userID).
		Scan(&height, &targetWeight, &goal,
			&rawStrategyTargets, &manualKcal, &manualProtein, &manualFat, &manualCarbs)

	if goal == "" {
		goal = "maintain"
	}

	// Ostatnia znana waga użytkownika
	_ = db.QueryRow(`
       SELECT weight_kg FROM user_daily_metrics 
       WHERE user_id = ? AND weight_kg > 0 
       ORDER BY date DESC, id DESC LIMIT 1`, userID).Scan(&currentWeight)

	if currentWeight <= 0 {
		currentWeight = 70.0
	}

	// Wiek użytkownika z daty urodzenia
	age := 24.0
	var birthday sql.NullString
	_ = db.QueryRow(`SELECT birthday_date FROM users WHERE id = ?`, userID).Scan(&birthday)
	if birthday.Valid && len(birthday.String) >= 4 {
		var birthYear int
		if _, err := fmt.Sscanf(birthday.String[:4], "%d", &birthYear); err == nil && birthYear > 1900 {
			age = float64(time.Now().Year() - birthYear)
		}
	}

	// Wzór Mifflina-St Jeora (BMR)
	bmr := 10.0*currentWeight + 6.25*height - 5.0*age + 5.0
	sedentaryBase := math.Round(bmr * 1.2)

	// Statyczne domyślne dla 4 strategii
	bulkKcal := sedentaryBase + 300.0
	bulkP := math.Round(1.9 * currentWeight)
	bulkF := math.Round(1.1 * currentWeight)
	bulkC := math.Max(0, math.Round((bulkKcal-(bulkP*4.0+bulkF*9.0))/4.0))

	cutKcal := math.Max(1400.0, sedentaryBase-400.0)
	cutP := math.Round(2.3 * currentWeight)
	cutF := math.Round(0.8 * currentWeight)
	cutC := math.Max(0, math.Round((cutKcal-(cutP*4.0+cutF*9.0))/4.0))

	recompKcal := math.Max(1500.0, sedentaryBase-100.0)
	recompP := math.Round(2.2 * currentWeight)
	recompF := math.Round(0.9 * currentWeight)
	recompC := math.Max(0, math.Round((recompKcal-(recompP*4.0+recompF*9.0))/4.0))

	maintainKcal := sedentaryBase
	maintainP := math.Round(1.8 * currentWeight)
	maintainF := math.Round(1.0 * currentWeight)
	maintainC := math.Max(0, math.Round((maintainKcal-(maintainP*4.0+maintainF*9.0))/4.0))

	allStrategies = map[string]MacroTargets{
		"bulk":     {Kcal: bulkKcal, Protein: bulkP, Fat: bulkF, Carbs: bulkC},
		"cut":      {Kcal: cutKcal, Protein: cutP, Fat: cutF, Carbs: cutC},
		"recomp":   {Kcal: recompKcal, Protein: recompP, Fat: recompF, Carbs: recompC},
		"maintain": {Kcal: maintainKcal, Protein: maintainP, Fat: maintainF, Carbs: maintainC},
	}

	// Nałożenie ręcznie zapisanych celów ze strategy_targets jeśli istnieją
	if rawStrategyTargets.Valid && strings.TrimSpace(rawStrategyTargets.String) != "" {
		var custom map[string]MacroTargets
		if err := json.Unmarshal([]byte(rawStrategyTargets.String), &custom); err == nil {
			for k, v := range custom {
				if v.Kcal > 0 {
					allStrategies[strings.ToLower(k)] = v
				}
			}
		}
	}

	goalKey := strings.ToLower(strings.TrimSpace(goal))
	if goalKey == "" {
		goalKey = "maintain"
	}

	activeTargets, ok := allStrategies[goalKey]
	if !ok {
		activeTargets = allStrategies["maintain"]
		goalKey = "maintain"
	}

	if manualKcal.Valid && manualKcal.Float64 > 0 {
		activeTargets.Kcal = manualKcal.Float64
	}
	if manualProtein.Valid && manualProtein.Float64 > 0 {
		activeTargets.Protein = manualProtein.Float64
	}
	if manualFat.Valid && manualFat.Float64 > 0 {
		activeTargets.Fat = manualFat.Float64
	}
	if manualCarbs.Valid && manualCarbs.Float64 > 0 {
		activeTargets.Carbs = manualCarbs.Float64
	}
	allStrategies[goalKey] = activeTargets

	return activeTargets.Kcal, activeTargets.Protein, activeTargets.Fat, activeTargets.Carbs, currentWeight, height, goal, targetWeight, allStrategies
}

// ==========================================
// FUNKCJE OBSŁUGI API
// ==========================================

// normalizeSetType keeps the API tolerant of older clients while preserving set-specific history.
func normalizeSetType(setType string) string {
	switch strings.ToLower(strings.TrimSpace(setType)) {
	case "warmup", "standard", "drop", "back_off", "failure":
		return strings.ToLower(strings.TrimSpace(setType))
	default:
		return "standard"
	}
}

// loadLastSetsByCompositeKey zwraca ostatnią znaną wartość osobno dla każdego
// dokładnego klucza (set_number, set_type). Dzięki temu np. warmup #1 i
// standard #1 są dwiema niezależnymi pozycjami historii autofillu.
func loadLastSetsByCompositeKey(db *sql.DB, userID any, exerciseID int, excludeWorkoutID int) []LastSetValue {
	query := `
        SELECT s.set_number, s.weight_kg, s.reps, s.rir, s.set_type, COALESCE(s.time_seconds, 0), COALESCE(s.distance_km, 0)
        FROM training_workout_sets s
        JOIN training_workout_exercises we ON we.id = s.workout_exercise_id
        JOIN training_workouts w ON w.id = we.workout_id
        WHERE w.user_id = ?
          AND we.exercise_id = ?
          AND (? = 0 OR w.id != ?)
          AND s.id = (
              SELECT s2.id
              FROM training_workout_sets s2
              JOIN training_workout_exercises we2 ON we2.id = s2.workout_exercise_id
              JOIN training_workouts w2 ON w2.id = we2.workout_id
              WHERE w2.user_id = ?
                AND we2.exercise_id = ?
                AND s2.set_number = s.set_number
                AND s2.set_type = s.set_type
                AND (? = 0 OR w2.id != ?)
              ORDER BY w2.date DESC, w2.id DESC, we2.id DESC, s2.id DESC
              LIMIT 1
          )
        ORDER BY s.set_number, s.set_type`

	rows, err := db.Query(
		query,
		userID, exerciseID, excludeWorkoutID, excludeWorkoutID,
		userID, exerciseID, excludeWorkoutID, excludeWorkoutID,
	)
	if err != nil {
		return make([]LastSetValue, 0)
	}
	defer closeRows(rows)

	result := make([]LastSetValue, 0)
	for rows.Next() {
		var item LastSetValue
		if err := rows.Scan(&item.SetNumber, &item.WeightKg, &item.Reps, &item.Rir, &item.SetType, &item.TimeSeconds, &item.DistanceKm); err != nil {
			continue
		}
		item.SetType = normalizeSetType(item.SetType)
		result = append(result, item)
	}
	return result
}

func handleAPIRoutines(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer closeDB(db)

	switch r.Method {
	case http.MethodGet:
		userID := r.URL.Query().Get("user_id")
		if userID == "" {
			userID = "1"
		}
		rows, err := db.Query(`SELECT id, name, COALESCE(group_name, ''), COALESCE(is_archived, 0) FROM training_routines WHERE user_id = ? ORDER BY is_archived ASC, group_name COLLATE NOCASE, name COLLATE NOCASE`, userID)
		if err != nil {
			http.Error(w, "Failed to fetch routines", http.StatusInternalServerError)
			return
		}
		defer closeRows(rows)

		routines := make([]Routine, 0)
		for rows.Next() {
			var rt Routine
			var isArchivedInt int
			if err := rows.Scan(&rt.ID, &rt.Name, &rt.GroupName, &isArchivedInt); err != nil {
				continue
			}
			rt.IsArchived = (isArchivedInt != 0)
			routines = append(routines, rt)
		}

		writeJSON(w, http.StatusOK, routines)

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

		if _, err := tx.Exec(`UPDATE training_workouts SET routine_name = (SELECT name FROM training_routines WHERE id = ?) WHERE routine_id = ? AND (routine_name IS NULL OR routine_name = '')`, routineID, routineID); err != nil {
			_ = tx.Rollback()
			http.Error(w, "Failed to preserve workout routine names", http.StatusInternalServerError)
			return
		}
		if _, err := tx.Exec(`UPDATE training_workouts SET routine_id = NULL WHERE routine_id = ?`, routineID); err != nil {
			_ = tx.Rollback()
			http.Error(w, "Failed to update workouts", http.StatusInternalServerError)
			return
		}
		if _, err := tx.Exec(`DELETE FROM training_routine_exercises WHERE routine_id = ?`, routineID); err != nil {
			_ = tx.Rollback()
			http.Error(w, "Failed to delete routine exercises", http.StatusInternalServerError)
			return
		}
		if _, err := tx.Exec(`DELETE FROM training_routines WHERE id = ?`, routineID); err != nil {
			_ = tx.Rollback()
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
	defer closeDB(db)

	var exists bool
	err = db.QueryRow(`SELECT EXISTS(SELECT 1 FROM training_workout_sets WHERE workout_exercise_id = ? AND set_number = ?)`,
		req.WorkoutExerciseID, req.SetNumber).Scan(&exists)

	if err != nil {
		http.Error(w, "Database query error", http.StatusInternalServerError)
		return
	}

	if exists {
		_, err = db.Exec(`UPDATE training_workout_sets SET reps = ?, weight_kg = ?, rir = ?, set_type = ?, time_seconds = ?, distance_km = ? 
                      WHERE workout_exercise_id = ? AND set_number = ?`,
			req.Reps, req.WeightKg, req.RIR, req.SetType, req.TimeSeconds, req.DistanceKm, req.WorkoutExerciseID, req.SetNumber)
	} else {
		_, err = db.Exec(`INSERT INTO training_workout_sets (workout_exercise_id, set_number, reps, weight_kg, rir, set_type, time_seconds, distance_km) 
                      VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
			req.WorkoutExerciseID, req.SetNumber, req.Reps, req.WeightKg, req.RIR, req.SetType, req.TimeSeconds, req.DistanceKm)
	}

	if err != nil {
		http.Error(w, "Failed to save set into database", http.StatusInternalServerError)
		return
	}

	var userID, exerciseID, workoutID int
	var exerciseName string
	_ = db.QueryRow(`
		SELECT w.user_id, we.exercise_id, w.id, e.name
		FROM training_workout_exercises we
		JOIN training_workouts w ON w.id = we.workout_id
		JOIN training_exercises e ON e.id = we.exercise_id
		WHERE we.id = ?`, req.WorkoutExerciseID).Scan(&userID, &exerciseID, &workoutID, &exerciseName)

	var maxPriorW, maxPriorR, maxPriorV, maxPrior1RM, maxPriorT, maxPriorD float64
	var priorCount int
	_ = db.QueryRow(`
		SELECT 
			COALESCE(MAX(s.weight_kg), 0),
			COALESCE(MAX(s.reps), 0),
			COALESCE(MAX(s.weight_kg * s.reps), 0),
			COALESCE(MAX(s.weight_kg * (1.0 + CAST(s.reps AS REAL)/30.0)), 0),
			COALESCE(MAX(s.time_seconds), 0),
			COALESCE(MAX(s.distance_km), 0),
			COUNT(s.id)
		FROM training_workout_sets s
		JOIN training_workout_exercises we2 ON we2.id = s.workout_exercise_id
		JOIN training_workouts w2 ON w2.id = we2.workout_id
		WHERE w2.user_id = ? AND we2.exercise_id = ?
		  AND NOT (we2.id = ? AND s.set_number = ?)
		  AND (s.weight_kg > 0 OR s.reps > 0 OR s.time_seconds > 0 OR s.distance_km > 0)`,
		userID, exerciseID, req.WorkoutExerciseID, req.SetNumber).Scan(&maxPriorW, &maxPriorR, &maxPriorV, &maxPrior1RM, &maxPriorT, &maxPriorD, &priorCount)

	isPr := false
	prType := ""
	prLabel := ""
	currentVol := req.WeightKg * float64(req.Reps)
	var current1RM float64
	if req.Reps > 0 {
		current1RM = req.WeightKg * (1.0 + float64(req.Reps)/30.0)
	}

	if priorCount > 0 && (req.Reps > 0 || req.WeightKg > 0 || req.TimeSeconds > 0 || req.DistanceKm > 0) {
		if req.WeightKg > 0 && req.WeightKg > maxPriorW {
			isPr = true
			prType = "weight"
			if req.WeightKg == float64(int(req.WeightKg)) {
				prLabel = fmt.Sprintf("Heaviest: %.0f kg", req.WeightKg)
			} else {
				prLabel = fmt.Sprintf("Heaviest: %.1f kg", req.WeightKg)
			}
		} else if current1RM > 0 && current1RM > maxPrior1RM && req.WeightKg > 0 {
			isPr = true
			prType = "1rm"
			prLabel = fmt.Sprintf("Est. 1RM: %.1f kg", current1RM)
		} else if req.Reps > 0 && float64(req.Reps) > maxPriorR {
			isPr = true
			prType = "reps"
			prLabel = fmt.Sprintf("Max Reps: %d reps", req.Reps)
		} else if req.TimeSeconds > 0 && float64(req.TimeSeconds) > maxPriorT {
			isPr = true
			prType = "time"
			prLabel = fmt.Sprintf("Best Time: %02d:%02d", req.TimeSeconds/60, req.TimeSeconds%60)
		} else if req.DistanceKm > 0 && req.DistanceKm > maxPriorD {
			isPr = true
			prType = "distance"
			prLabel = fmt.Sprintf("Best Distance: %.2f km", req.DistanceKm)
		} else if currentVol > 0 && currentVol > maxPriorV {
			isPr = true
			prType = "volume"
			prLabel = fmt.Sprintf("Set Volume: %.0f kg", currentVol)
		}
	}

	writeJSON(w, http.StatusOK, SetLogResponse{
		Status:  "success",
		Message: "Set logged/updated successfully",
		IsPr:    isPr,
		PrType:  prType,
		PrLabel: prLabel,
	})
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
	defer closeDB(db)

	rows, err := db.Query(`
       SELECT re.id, re.exercise_id, e.name, COALESCE(e.type, 'weight_reps'), re.position, COALESCE(re.rest_seconds, 90)
       FROM training_routine_exercises re
       JOIN training_exercises e ON e.id = re.exercise_id
       WHERE re.routine_id = ?
       ORDER BY re.position`, routineID)
	if err != nil {
		http.Error(w, "Failed to fetch routine exercises", http.StatusInternalServerError)
		return
	}

	exercises := make([]RoutineExercisePreview, 0)
	for rows.Next() {
		var ex RoutineExercisePreview
		if err := rows.Scan(&ex.RoutineExerciseID, &ex.ExerciseID, &ex.Name, &ex.Type, &ex.Position, &ex.RestSeconds); err != nil {
			continue
		}

		tRows, _ := db.Query(`SELECT set_number, set_type FROM training_routine_sets WHERE routine_exercise_id = ? ORDER BY set_number`, ex.RoutineExerciseID)
		ex.TemplateSets = make([]RoutineSet, 0)
		for tRows.Next() {
			var ts RoutineSet
			if err := tRows.Scan(&ts.SetNumber, &ts.SetType); err == nil {
				ex.TemplateSets = append(ex.TemplateSets, ts)
			}
		}
		closeRows(tRows)

		ex.LastSets = make([]LastSetValue, 0)
		exercises = append(exercises, ex)
	}
	closeRows(rows)

	for i := range exercises {
		exercises[i].LastSets = loadLastSetsByCompositeKey(db, userID, exercises[i].ExerciseID, 0)
	}

	writeJSON(w, http.StatusOK, exercises)
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
	defer closeDB(db)

	var query string
	var args []any
	baseQuery := `
       SELECT COALESCE(SUM(s.weight_kg * s.reps), 0)
       FROM training_workout_sets s
       JOIN training_workout_exercises we ON s.workout_exercise_id = we.id
       JOIN training_workouts w ON we.workout_id = w.id
       WHERE w.user_id = ?`

	if interval == "" {
		query = baseQuery
		args = []any{userID}
	} else {
		query = baseQuery + ` AND w.date >= datetime('now', ?)`
		args = []any{userID, interval}
	}

	var volume float64
	if err := db.QueryRow(query, args...).Scan(&volume); err != nil {
		http.Error(w, "Failed to compute volume", http.StatusInternalServerError)
		return
	}

	writeJSON(w, http.StatusOK, VolumeResponse{VolumeKg: volume})
}

func handleAPIWorkouts(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer closeDB(db)

	switch r.Method {
	case http.MethodGet:
		userID := r.URL.Query().Get("user_id")
		if userID == "" {
			userID = "1"
		}

		type exBests struct {
			maxW float64
			maxR float64
			maxV float64
		}
		exMap := make(map[int]*exBests)
		prCountPerWorkout := make(map[int]int)

		setRows, setErr := db.Query(`
           SELECT w.id, we.exercise_id, s.weight_kg, s.reps
           FROM training_workout_sets s
           JOIN training_workout_exercises we ON we.id = s.workout_exercise_id
           JOIN training_workouts w ON w.id = we.workout_id
           WHERE w.user_id = ?
           ORDER BY w.date, w.id, we.position, s.set_number`, userID)
		if setErr == nil {
			for setRows.Next() {
				var wID, exID, reps int
				var wKg float64
				if err := setRows.Scan(&wID, &exID, &wKg, &reps); err != nil {
					continue
				}
				b, ok := exMap[exID]
				if !ok {
					b = &exBests{}
					exMap[exID] = b
				}
				vol := wKg * float64(reps)
				if reps > 0 || wKg > 0 {
					if (wKg > 0 && wKg > b.maxW) || (reps > 0 && float64(reps) > b.maxR) || (vol > 0 && vol > b.maxV) {
						prCountPerWorkout[wID]++
					}
					if wKg > b.maxW {
						b.maxW = wKg
					}
					if float64(reps) > b.maxR {
						b.maxR = float64(reps)
					}
					if vol > b.maxV {
						b.maxV = vol
					}
				}
			}
			closeRows(setRows)
		}

		rows, err := db.Query(`
           SELECT 
              w.id, 
              w.date, 
              COALESCE(r.name, NULLIF(w.routine_name, ''), 'Custom workout'),
              COALESCE(COUNT(s.id), 0) as total_sets,
              COALESCE(SUM(s.reps), 0) as total_reps,
              COALESCE(SUM(s.weight_kg * s.reps), 0) as total_weight_kg
           FROM training_workouts w
           LEFT JOIN training_routines r ON r.id = w.routine_id
           LEFT JOIN training_workout_exercises we ON we.workout_id = w.id
           LEFT JOIN training_workout_sets s ON s.workout_exercise_id = we.id
           WHERE w.user_id = ?
           GROUP BY w.id, w.date, r.name, w.routine_name
           ORDER BY w.date DESC, w.id DESC`, userID)
		if err != nil {
			http.Error(w, "Failed to fetch workouts", http.StatusInternalServerError)
			return
		}
		defer closeRows(rows)

		workouts := make([]WorkoutSummary, 0)
		for rows.Next() {
			var ws WorkoutSummary
			if err := rows.Scan(&ws.WorkoutID, &ws.Date, &ws.RoutineName, &ws.TotalSets, &ws.TotalReps, &ws.TotalWeightKg); err != nil {
				continue
			}
			ws.PrCount = prCountPerWorkout[ws.WorkoutID]
			workouts = append(workouts, ws)
		}

		writeJSON(w, http.StatusOK, workouts)

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
			_ = tx.Rollback()
			http.Error(w, "Failed to delete sets", http.StatusInternalServerError)
			return
		}
		if _, err := tx.Exec(`DELETE FROM training_workout_exercises WHERE workout_id = ?`, workoutID); err != nil {
			_ = tx.Rollback()
			http.Error(w, "Failed to delete workout exercises", http.StatusInternalServerError)
			return
		}
		if _, err := tx.Exec(`DELETE FROM training_workouts WHERE id = ?`, workoutID); err != nil {
			_ = tx.Rollback()
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
	defer closeDB(db)

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
			_ = tx.Rollback()
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
	defer closeDB(db)

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
			_ = tx.Rollback()
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
	defer closeDB(db)

	var rName string
	_ = db.QueryRow(`SELECT name FROM training_routines WHERE id = ?`, req.RoutineID).Scan(&rName)
	if rName == "" {
		rName = "Custom workout"
	}

	res, err := db.Exec(`INSERT INTO training_workouts (user_id, routine_id, routine_name) VALUES (?, ?, ?)`, req.UserID, req.RoutineID, rName)
	if err != nil {
		http.Error(w, "Failed to create workout", http.StatusInternalServerError)
		return
	}
	workoutID, _ := res.LastInsertId()

	rows, err := db.Query(`
    SELECT re.id, re.exercise_id, e.name, COALESCE(e.type, 'weight_reps'), re.position, COALESCE(re.rest_seconds, 90)
    FROM training_routine_exercises re
    JOIN training_exercises e ON e.id = re.exercise_id
    WHERE re.routine_id = ?
    ORDER BY re.position`, req.RoutineID)
	if err != nil {
		http.Error(w, "Failed to fetch routine exercises", http.StatusInternalServerError)
		return
	}

	type routineExercise struct {
		id          int
		exerciseID  int
		name        string
		exType      string
		position    int
		restSeconds int
	}
	var routineExercises []routineExercise
	for rows.Next() {
		var re routineExercise
		if err := rows.Scan(&re.id, &re.exerciseID, &re.name, &re.exType, &re.position, &re.restSeconds); err != nil {
			continue
		}
		routineExercises = append(routineExercises, re)
	}
	closeRows(rows)

	exercises := make([]ExerciseInfo, 0)
	for _, re := range routineExercises {
		weRes, err := db.Exec(`INSERT INTO training_workout_exercises (workout_id, exercise_id, position, rest_seconds) VALUES (?, ?, ?, ?)`, workoutID, re.exerciseID, re.position, re.restSeconds)
		if err != nil {
			continue
		}
		weID, _ := weRes.LastInsertId()

		tSets := make([]RoutineSet, 0)
		tRows, errSets := db.Query(`SELECT set_number, set_type FROM training_routine_sets WHERE routine_exercise_id = ? ORDER BY set_number`, re.id)
		if errSets == nil {
			for tRows.Next() {
				var ts RoutineSet
				if err := tRows.Scan(&ts.SetNumber, &ts.SetType); err == nil {
					tSets = append(tSets, ts)
				}
			}
			closeRows(tRows)
		}

		lSets := loadLastSetsByCompositeKey(db, req.UserID, re.exerciseID, int(workoutID))

		var bestW float64
		var bestR int
		_ = db.QueryRow(`
			SELECT COALESCE(MAX(s.weight_kg), 0), COALESCE(MAX(s.reps), 0)
			FROM training_workout_sets s
			JOIN training_workout_exercises we ON we.id = s.workout_exercise_id
			JOIN training_workouts w ON w.id = we.workout_id
			WHERE w.user_id = ? AND we.exercise_id = ?`,
			req.UserID, re.exerciseID).Scan(&bestW, &bestR)

		exercises = append(exercises, ExerciseInfo{
			WorkoutExerciseID: int(weID),
			ExerciseID:        re.exerciseID,
			Name:              re.name,
			Type:              re.exType,
			Position:          re.position,
			TemplateSets:      tSets,
			LastSets:          lSets,
			BestWeightKg:      bestW,
			BestReps:          bestR,
			RestSeconds:       re.restSeconds,
		})
	}

	writeJSON(w, http.StatusOK, StartWorkoutResponse{
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
	defer closeDB(db)

	rows, err := db.Query(`SELECT id, name, type, equipment FROM training_exercises ORDER BY name`)
	if err != nil {
		http.Error(w, "Query error", http.StatusInternalServerError)
		return
	}
	defer closeRows(rows)

	exercises := make([]ExerciseItem, 0)
	for rows.Next() {
		var ex ExerciseItem
		if err := rows.Scan(&ex.ID, &ex.Name, &ex.Type, &ex.Equipment); err == nil {
			exercises = append(exercises, ex)
		}
	}

	writeJSON(w, http.StatusOK, exercises)
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
	defer closeDB(db)

	res, err := db.Exec(`INSERT INTO training_exercises (name, type, equipment) VALUES (?, ?, ?)`,
		strings.TrimSpace(req.Name), strings.TrimSpace(req.Type), strings.TrimSpace(req.Equipment))
	if err != nil {
		http.Error(w, "Exercise already exists or database error", http.StatusConflict)
		return
	}

	newID, _ := res.LastInsertId()

	writeJSON(w, http.StatusCreated, ExerciseItem{
		ID:        int(newID),
		Name:      req.Name,
		Type:      req.Type,
		Equipment: req.Equipment,
	})
}

func handleAPIExerciseUpdate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodPut {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req UpdateExerciseReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON body", http.StatusBadRequest)
		return
	}

	if req.ID <= 0 || strings.TrimSpace(req.Name) == "" {
		http.Error(w, "Exercise ID and name are required", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer closeDB(db)

	_, err = db.Exec(`UPDATE training_exercises SET name = ?, type = ?, equipment = ? WHERE id = ?`,
		strings.TrimSpace(req.Name), strings.TrimSpace(req.Type), strings.TrimSpace(req.Equipment), req.ID)
	if err != nil {
		http.Error(w, "Failed to update exercise: "+err.Error(), http.StatusInternalServerError)
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
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
	defer closeDB(db)

	tx, err := db.Begin()
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	var maxPos int
	_ = tx.QueryRow(`SELECT COALESCE(MAX(position), 0) FROM training_routine_exercises WHERE routine_id = ?`, req.RoutineID).Scan(&maxPos)

	res, err := tx.Exec(`INSERT INTO training_routine_exercises (routine_id, exercise_id, position, default_sets) VALUES (?, ?, ?, 1)`,
		req.RoutineID, req.ExerciseID, maxPos+1)
	if err != nil {
		_ = tx.Rollback()
		http.Error(w, "Failed to add exercise", http.StatusInternalServerError)
		return
	}

	newReID, _ := res.LastInsertId()

	for s := 1; s <= 1; s++ {
		_, err = tx.Exec(`INSERT INTO training_routine_sets (routine_exercise_id, set_number, set_type) VALUES (?, ?, 'standard')`, newReID, s)
		if err != nil {
			_ = tx.Rollback()
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
	defer closeDB(db)

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

	req.Name = strings.TrimSpace(req.Name)
	req.GroupName = strings.TrimSpace(req.GroupName)
	if req.UserID <= 0 || req.Name == "" {
		http.Error(w, "Invalid user ID or routine name", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer closeDB(db)

	_, err = db.Exec(`INSERT INTO training_routines (user_id, name, group_name, is_archived) VALUES (?, ?, ?, 0)`, req.UserID, req.Name, req.GroupName)
	if err != nil {
		http.Error(w, "Failed to create routine", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
}

func handleAPIRoutineUpdate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodPut {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req UpdateRoutineReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON body", http.StatusBadRequest)
		return
	}

	req.Name = strings.TrimSpace(req.Name)
	if req.ID <= 0 {
		http.Error(w, "Invalid routine ID", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer closeDB(db)

	if req.Name != "" {
		_, err = db.Exec(`UPDATE training_routines SET name = ? WHERE id = ?`, req.Name, req.ID)
		if err != nil {
			http.Error(w, "Failed to update routine name", http.StatusInternalServerError)
			return
		}
	}
	if req.GroupName != nil {
		gName := strings.TrimSpace(*req.GroupName)
		_, err = db.Exec(`UPDATE training_routines SET group_name = ? WHERE id = ?`, gName, req.ID)
		if err != nil {
			http.Error(w, "Failed to update routine group", http.StatusInternalServerError)
			return
		}
	}
	if req.IsArchived != nil {
		archVal := 0
		if *req.IsArchived {
			archVal = 1
		}
		_, err = db.Exec(`UPDATE training_routines SET is_archived = ? WHERE id = ?`, archVal, req.ID)
		if err != nil {
			http.Error(w, "Failed to update routine archive state", http.StatusInternalServerError)
			return
		}
	}
	w.WriteHeader(http.StatusOK)
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
	defer closeDB(db)

	var maxPos int
	_ = db.QueryRow(`SELECT COALESCE(MAX(position), 0) FROM training_workout_exercises WHERE workout_id = ?`, req.WorkoutID).Scan(&maxPos)

	res, err := db.Exec(`INSERT INTO training_workout_exercises (workout_id, exercise_id, position) VALUES (?, ?, ?)`,
		req.WorkoutID, req.ExerciseID, maxPos+1)
	if err != nil {
		http.Error(w, "Failed to add exercise to workout", http.StatusInternalServerError)
		return
	}

	weID, _ := res.LastInsertId()

	var exName, exType string
	_ = db.QueryRow(`SELECT name, COALESCE(type, 'weight_reps') FROM training_exercises WHERE id = ?`, req.ExerciseID).Scan(&exName, &exType)

	var workoutUserID int
	_ = db.QueryRow(`SELECT user_id FROM training_workouts WHERE id = ?`, req.WorkoutID).Scan(&workoutUserID)

	var bestW float64
	var bestR int
	_ = db.QueryRow(`
		SELECT COALESCE(MAX(s.weight_kg), 0), COALESCE(MAX(s.reps), 0)
		FROM training_workout_sets s
		JOIN training_workout_exercises we ON we.id = s.workout_exercise_id
		JOIN training_workouts w ON w.id = we.workout_id
		WHERE w.user_id = ? AND we.exercise_id = ?`,
		workoutUserID, req.ExerciseID).Scan(&bestW, &bestR)

	newExercise := ExerciseInfo{
		WorkoutExerciseID: int(weID),
		ExerciseID:        req.ExerciseID,
		Name:              exName,
		Type:              exType,
		Position:          maxPos + 1,
		TemplateSets:      make([]RoutineSet, 0),
		LastSets:          loadLastSetsByCompositeKey(db, workoutUserID, req.ExerciseID, req.WorkoutID),
		BestWeightKg:      bestW,
		BestReps:          bestR,
	}

	writeJSON(w, http.StatusOK, newExercise)
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
	defer closeDB(db)

	var resp WorkoutDetailResponse
	var workoutUserID int
	var workoutDate string

	err = db.QueryRow(`
       SELECT id, user_id, date, COALESCE((SELECT name FROM training_routines WHERE id = routine_id), NULLIF(routine_name, ''), 'Custom workout') 
       FROM training_workouts WHERE id = ?`, workoutID).Scan(&resp.WorkoutID, &workoutUserID, &workoutDate, &resp.RoutineName)
	resp.Date = workoutDate

	if err != nil {
		http.Error(w, "Workout not found", http.StatusNotFound)
		return
	}

	resp.Exercises = make([]WorkoutDetailExercise, 0)

	rows, err := db.Query(`
       SELECT we.id, we.exercise_id, e.name, COALESCE(e.type, 'weight_reps'), we.position, COALESCE(we.rest_seconds, 90) 
       FROM training_workout_exercises we 
       JOIN training_exercises e ON e.id = we.exercise_id 
       WHERE we.workout_id = ? ORDER BY we.position`, workoutID)
	if err == nil {
		defer closeRows(rows)
		for rows.Next() {
			var ex WorkoutDetailExercise
			if err := rows.Scan(&ex.WorkoutExerciseID, &ex.ExerciseID, &ex.Name, &ex.Type, &ex.Position, &ex.RestSeconds); err != nil {
				continue
			}
			ex.Sets = make([]WorkoutDetailSet, 0)

			// Determine PRs: get prior bests for this user and this exercise prior to this workout
			var maxPriorW, maxPriorR, maxPriorV float64
			_ = db.QueryRow(`
                SELECT 
                   COALESCE(MAX(s.weight_kg), 0),
                   COALESCE(MAX(s.reps), 0),
                   COALESCE(MAX(s.weight_kg * s.reps), 0)
                FROM training_workout_sets s
                JOIN training_workout_exercises we2 ON we2.id = s.workout_exercise_id
                JOIN training_workouts w2 ON w2.id = we2.workout_id
                WHERE w2.user_id = ? AND we2.exercise_id = ? 
                  AND (w2.date < ? OR (w2.date = ? AND w2.id < ?))`,
				workoutUserID, ex.ExerciseID, workoutDate, workoutDate, workoutID).Scan(&maxPriorW, &maxPriorR, &maxPriorV)

			setRows, errSet := db.Query(`
             SELECT id, set_number, weight_kg, reps, rir, set_type, COALESCE(time_seconds, 0), COALESCE(distance_km, 0) 
             FROM training_workout_sets 
             WHERE workout_exercise_id = ? ORDER BY set_number`, ex.WorkoutExerciseID)
			if errSet == nil {
				for setRows.Next() {
					var s WorkoutDetailSet
					if err := setRows.Scan(&s.SetID, &s.SetNumber, &s.WeightKg, &s.Reps, &s.Rir, &s.SetType, &s.TimeSeconds, &s.DistanceKm); err != nil {
						continue
					}
					vol := s.WeightKg * float64(s.Reps)
					if s.Reps > 0 || s.WeightKg > 0 {
						if (s.WeightKg > 0 && s.WeightKg > maxPriorW) ||
							(s.Reps > 0 && float64(s.Reps) > maxPriorR) ||
							(vol > 0 && vol > maxPriorV) {
							s.IsPr = true
						}
						if s.WeightKg > maxPriorW {
							maxPriorW = s.WeightKg
						}
						if float64(s.Reps) > maxPriorR {
							maxPriorR = float64(s.Reps)
						}
						if vol > maxPriorV {
							maxPriorV = vol
						}
					}
					ex.Sets = append(ex.Sets, s)
				}
				closeRows(setRows)
			}
			resp.Exercises = append(resp.Exercises, ex)
		}
	}

	writeJSON(w, http.StatusOK, resp)
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
	defer closeDB(db)

	tx, err := db.Begin()
	if err != nil {
		http.Error(w, "Transaction error", http.StatusInternalServerError)
		return
	}

	_, _ = tx.Exec(`DELETE FROM training_routine_sets WHERE routine_exercise_id = ?`, req.RoutineExerciseID)
	for _, s := range req.Sets {
		_, _ = tx.Exec(`INSERT INTO training_routine_sets (routine_exercise_id, set_number, set_type) VALUES (?, ?, ?)`, req.RoutineExerciseID, s.SetNumber, s.SetType)
	}
	if req.RestSeconds != nil && *req.RestSeconds > 0 {
		_, _ = tx.Exec(`UPDATE training_routine_exercises SET rest_seconds = ? WHERE id = ?`, *req.RestSeconds, req.RoutineExerciseID)
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
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer closeDB(db)

	tx, err := db.Begin()
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	if _, err := tx.Exec(`DELETE FROM training_routine_exercises WHERE routine_id = ?`, req.RoutineID); err != nil {
		_ = tx.Rollback()
		http.Error(w, "Failed to clear routine exercises", http.StatusInternalServerError)
		return
	}

	rows, err := tx.Query(`SELECT id, exercise_id, position FROM training_workout_exercises WHERE workout_id = ? ORDER BY position`, req.WorkoutID)
	if err != nil {
		_ = tx.Rollback()
		http.Error(w, "Failed to query workout exercises", http.StatusInternalServerError)
		return
	}
	type wEx struct {
		id   int
		exID int
		pos  int
	}
	var wExercises []wEx
	for rows.Next() {
		var we wEx
		if err := rows.Scan(&we.id, &we.exID, &we.pos); err != nil {
			continue
		}
		wExercises = append(wExercises, we)
	}
	closeRows(rows)

	for _, we := range wExercises {
		var restSec int
		_ = tx.QueryRow(`SELECT COALESCE(rest_seconds, 90) FROM training_workout_exercises WHERE id = ?`, we.id).Scan(&restSec)
		if restSec <= 0 {
			restSec = 90
		}
		res, err := tx.Exec(`INSERT INTO training_routine_exercises (routine_id, exercise_id, position, default_sets, rest_seconds) VALUES (?, ?, ?, 3, ?)`, req.RoutineID, we.exID, we.pos, restSec)
		if err != nil {
			continue
		}
		newReID, _ := res.LastInsertId()

		setRows, err := tx.Query(`SELECT set_number, set_type FROM training_workout_sets WHERE workout_exercise_id = ? ORDER BY set_number`, we.id)
		if err == nil {
			for setRows.Next() {
				var sNum int
				var sType string
				if err := setRows.Scan(&sNum, &sType); err == nil {
					_, _ = tx.Exec(`INSERT INTO training_routine_sets (routine_exercise_id, set_number, set_type) VALUES (?, ?, ?)`, newReID, sNum, sType)
				}
			}
			closeRows(setRows)
		}
	}

	if err := tx.Commit(); err != nil {
		http.Error(w, "Failed to commit routine sync", http.StatusInternalServerError)
		return
	}
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
	defer closeDB(db)

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

	var args []any
	if interval == "" {
		query += ` GROUP BY w.id, w.date HAVING SUM(s.weight_kg * s.reps) > 0 ORDER BY w.date, w.id`
		args = []any{exerciseID, userID}
	} else {
		query += ` AND w.date >= datetime('now', ?) GROUP BY w.id, w.date HAVING SUM(s.weight_kg * s.reps) > 0 ORDER BY w.date, w.id`
		args = []any{exerciseID, userID, interval}
	}

	rows, err := db.Query(query, args...)
	if err != nil {
		http.Error(w, "Query execution error", http.StatusInternalServerError)
		return
	}
	defer closeRows(rows)

	resp.History = make([]ExerciseHistoryPoint, 0)
	for rows.Next() {
		var pt ExerciseHistoryPoint
		if err := rows.Scan(&pt.Date, &pt.MaxWeight, &pt.EstOneRM, &pt.Volume); err == nil {
			resp.History = append(resp.History, pt)
		}
	}

	writeJSON(w, http.StatusOK, resp)
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
	defer closeDB(db)

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
       GROUP BY w.id, w.date
       HAVING SUM(s.weight_kg * s.reps) > 0
       ORDER BY w.date, w.id`, routineID, userID)
	if err != nil {
		http.Error(w, "Query error", http.StatusInternalServerError)
		return
	}
	defer closeRows(rows)

	resp.History = make([]RoutineHistoryPoint, 0)
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

	writeJSON(w, http.StatusOK, resp)
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
	defer closeDB(db)

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
		_ = tx.Rollback()
		http.Error(w, "Failed to clean old sets", http.StatusInternalServerError)
		return
	}

	if _, err := tx.Exec(`DELETE FROM training_workout_exercises WHERE workout_id = ?`, req.WorkoutID); err != nil {
		_ = tx.Rollback()
		http.Error(w, "Failed to clean old exercises", http.StatusInternalServerError)
		return
	}

	for exPos, ex := range req.Exercises {
		res, err := tx.Exec(`
          INSERT INTO training_workout_exercises (workout_id, exercise_id, position) 
          VALUES (?, ?, ?)`, req.WorkoutID, ex.ExerciseID, exPos+1)
		if err != nil {
			_ = tx.Rollback()
			http.Error(w, "Failed to insert workout exercise", http.StatusInternalServerError)
			return
		}
		newWeID, _ := res.LastInsertId()

		for sPos, s := range ex.Sets {
			_, err := tx.Exec(`
             INSERT INTO training_workout_sets (workout_exercise_id, set_number, weight_kg, reps, rir, set_type, time_seconds, distance_km) 
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
				newWeID, sPos+1, s.WeightKg, s.Reps, s.Rir, normalizeSetType(s.SetType), s.TimeSeconds, s.DistanceKm)
			if err != nil {
				_ = tx.Rollback()
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
	defer closeDB(db)

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	if r.Method == http.MethodGet {
		rows, err := db.Query(`SELECT weight_kg, count FROM user_plates WHERE user_id = ? ORDER BY weight_kg`, userID)
		if err != nil {
			http.Error(w, "Database query error", http.StatusInternalServerError)
			return
		}
		defer closeRows(rows)

		plates := make([]UserPlate, 0)
		for rows.Next() {
			var p UserPlate
			if err := rows.Scan(&p.WeightKg, &p.Count); err == nil {
				plates = append(plates, p)
			}
		}
		writeJSON(w, http.StatusOK, plates)
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
		_ = tx.Commit()
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
	defer closeDB(db)

	_, _ = db.Exec(`ALTER TABLE user_algorithm_settings ADD COLUMN show_rir INTEGER NOT NULL DEFAULT 1`)

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
		s.ShowRIR = true

		var showRirInt int
		err := db.QueryRow(`
          SELECT warmup_enabled, warmup_base, drop_enabled, drop_percentage, backoff_enabled, backoff_percentage, show_rir
          FROM user_algorithm_settings WHERE user_id = ?`, userID).
			Scan(&s.WarmupEnabled, &s.WarmupBase, &s.DropEnabled, &s.DropPercentage, &s.BackoffEnabled, &s.BackoffPercentage, &showRirInt)

		if err != nil && !errors.Is(err, sql.ErrNoRows) {
			http.Error(w, "Database query error", http.StatusInternalServerError)
			return
		}
		s.ShowRIR = showRirInt == 1

		writeJSON(w, http.StatusOK, s)
		return
	}

	if r.Method == http.MethodPost {
		var s UserAlgorithmSettings
		if err := json.NewDecoder(r.Body).Decode(&s); err != nil {
			http.Error(w, "Invalid body", http.StatusBadRequest)
			return
		}
		if s.UserID == 0 {
			s.UserID = 1
		}

		showRirInt := 0
		if s.ShowRIR {
			showRirInt = 1
		}

		_, err := db.Exec(`
          INSERT INTO user_algorithm_settings (user_id, warmup_enabled, warmup_base, drop_enabled, drop_percentage, backoff_enabled, backoff_percentage, show_rir)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT(user_id) DO UPDATE SET
             warmup_enabled = excluded.warmup_enabled,
             warmup_base = excluded.warmup_base,
             drop_enabled = excluded.drop_enabled,
             drop_percentage = excluded.drop_percentage,
             backoff_enabled = excluded.backoff_enabled,
             backoff_percentage = excluded.backoff_percentage,
             show_rir = excluded.show_rir`,
			s.UserID, s.WarmupEnabled, s.WarmupBase, s.DropEnabled, s.DropPercentage, s.BackoffEnabled, s.BackoffPercentage, showRirInt)

		if err != nil {
			http.Error(w, "Database save error: "+err.Error(), http.StatusInternalServerError)
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
	defer closeDB(db)

	query := `
       SELECT DISTINCT date(date)
       FROM training_workouts
       WHERE date >= date('now', '-120 days')
       ORDER BY date(date)
    `
	rows, err := db.Query(query)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer closeRows(rows)

	dates := make([]string, 0)
	for rows.Next() {
		var d string
		if err := rows.Scan(&d); err == nil {
			dates = append(dates, d)
		}
	}

	writeJSON(w, http.StatusOK, ConsistencyStatsResponse{
		WorkoutDates: dates,
	})
}

func handleAPIUserWeightHistory(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer closeDB(db)

	rows, err := db.Query(`
       SELECT date, weight_kg 
       FROM user_daily_metrics 
       WHERE user_id = ? AND weight_kg IS NOT NULL AND weight_kg > 0 
       ORDER BY date`, userID)
	if err != nil {
		http.Error(w, "Query error: "+err.Error(), http.StatusInternalServerError)
		return
	}
	defer closeRows(rows)

	history := make([]WeightHistoryPoint, 0)
	for rows.Next() {
		var pt WeightHistoryPoint
		if err := rows.Scan(&pt.Date, &pt.WeightKg); err == nil {
			history = append(history, pt)
		}
	}

	writeJSON(w, http.StatusOK, history)
}

func handleAPIGetDailyMetricsToday(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer closeDB(db)

	var resp DailyMetricsTodayResp
	resp.Date = ""

	err = db.QueryRow(`
       SELECT date, weight_kg 
       FROM user_daily_metrics 
       WHERE user_id = ? AND date = date('now')`, userID).
		Scan(&resp.Date, &resp.WeightKg)

	if errors.Is(err, sql.ErrNoRows) {
		_ = db.QueryRow(`SELECT date('now')`).Scan(&resp.Date)
	} else if err != nil {
		http.Error(w, "Query error: "+err.Error(), http.StatusInternalServerError)
		return
	}

	writeJSON(w, http.StatusOK, resp)
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
	defer closeDB(db)

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	if r.Method == http.MethodGet {
		kcal, p, f, c, curWeight, height, goal, targetWeight, allStrategies := calculateStaticDietTargets(db, userID)

		resp := UserDietSettingsModel{
			UserID:          1,
			HeightCm:        height,
			CurrentWeightKg: curWeight,
			TargetWeightKg:  targetWeight,
			Goal:            goal,
			TargetKcal:      kcal,
			TargetProtein:   p,
			TargetFat:       f,
			TargetCarbs:     c,
			StrategyTargets: allStrategies,
		}

		writeJSON(w, http.StatusOK, resp)
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

		if req.Goal == "" {
			req.Goal = "bulk"
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

		stratBytes, _ := json.Marshal(req.StrategyTargets)

		_, err = tx.Exec(`
          INSERT INTO user_diet_settings (user_id, height_cm, target_weight_kg, goal, target_kcal, target_protein, target_fat, target_carbs, strategy_targets, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
          ON CONFLICT(user_id) DO UPDATE SET
             height_cm = excluded.height_cm,
             target_weight_kg = excluded.target_weight_kg,
             goal = excluded.goal,
             target_kcal = excluded.target_kcal,
             target_protein = excluded.target_protein,
             target_fat = excluded.target_fat,
             target_carbs = excluded.target_carbs,
             strategy_targets = excluded.strategy_targets,
             updated_at = datetime('now')`,
			req.UserID, req.HeightCm, req.TargetWeightKg, req.Goal, req.TargetKcal, req.TargetProtein, req.TargetFat, req.TargetCarbs, string(stratBytes))

		if err != nil {
			_ = tx.Rollback()
			http.Error(w, "Failed to save diet settings: "+err.Error(), http.StatusInternalServerError)
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
	defer closeDB(db)

	rows, err := db.Query(`
       SELECT id, name, brand, barcode, package_weight, serving_size, kcal, protein, fat, carbs 
       FROM diet_products ORDER BY name`)
	if err != nil {
		http.Error(w, "Failed to fetch products", http.StatusInternalServerError)
		return
	}
	defer closeRows(rows)

	products := make([]DietProductItem, 0)
	for rows.Next() {
		var p DietProductItem
		if err := rows.Scan(&p.ID, &p.Name, &p.Brand, &p.Barcode, &p.PackageWeight, &p.ServingSize, &p.Kcal, &p.Protein, &p.Fat, &p.Carbs); err == nil {
			products = append(products, p)
		}
	}

	writeJSON(w, http.StatusOK, products)
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
	defer closeDB(db)

	res, err := db.Exec(`
       INSERT INTO diet_products (name, brand, barcode, package_weight, serving_size, kcal, protein, fat, carbs)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		strings.TrimSpace(req.Name), req.Brand, req.Barcode, req.PackageWeight, req.ServingSize, req.Kcal, req.Protein, req.Fat, req.Carbs)

	if err != nil {
		http.Error(w, "Failed to save product", http.StatusInternalServerError)
		return
	}

	newID, _ := res.LastInsertId()
	writeJSON(w, http.StatusCreated, DietProductItem{
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
	defer closeDB(db)

	targetKcal, targetP, targetF, targetC, _, _, _, _, _ := calculateStaticDietTargets(db, userID)

	var resp DailyDietSummaryResp
	resp.TargetKcal = targetKcal
	resp.TargetP = targetP
	resp.TargetF = targetF
	resp.TargetC = targetC
	resp.Logs = make([]DietLogItem, 0)

	rows, err := db.Query(`
       SELECT 
          l.id, 
          COALESCE(l.product_id, 0), 
          COALESCE(p.name, l.custom_name, 'Custom entry'), 
          l.amount,
          CASE 
             WHEN l.product_id IS NOT NULL THEN COALESCE(p.kcal, 0) * (l.amount / 100.0)
             ELSE COALESCE(l.kcal, 0) * (l.amount / 100.0)
          END as calculated_kcal,
          CASE 
             WHEN l.product_id IS NOT NULL THEN COALESCE(p.protein, 0) * (l.amount / 100.0)
             ELSE COALESCE(l.protein, 0) * (l.amount / 100.0)
          END as calculated_p,
          CASE 
             WHEN l.product_id IS NOT NULL THEN COALESCE(p.fat, 0) * (l.amount / 100.0)
             ELSE COALESCE(l.fat, 0) * (l.amount / 100.0)
          END as calculated_f,
          CASE 
             WHEN l.product_id IS NOT NULL THEN COALESCE(p.carbs, 0) * (l.amount / 100.0)
             ELSE COALESCE(l.carbs, 0) * (l.amount / 100.0)
          END as calculated_c,
          strftime('%H:%M', l.logged_at) as log_time,
          p.serving_size
       FROM diet_logs l
       LEFT JOIN diet_products p ON p.id = l.product_id
       WHERE l.user_id = ? AND date(l.logged_at) = date(?)
       ORDER BY l.logged_at DESC, l.id DESC`, userID, dateStr)

	if err == nil {
		defer closeRows(rows)
		for rows.Next() {
			var item DietLogItem
			var servSize *float64
			if err := rows.Scan(&item.ID, &item.ProductID, &item.Name, &item.AmountG, &item.Kcal, &item.Protein, &item.Fat, &item.Carbs, &item.LoggedAt, &servSize); err == nil {
				if servSize != nil && *servSize > 0 {
					val := item.AmountG / *servSize
					item.ServingsCount = &val
				}
				resp.ConsumedKcal += item.Kcal
				resp.ConsumedP += item.Protein
				resp.ConsumedF += item.Fat
				resp.ConsumedC += item.Carbs
				resp.Logs = append(resp.Logs, item)
			}
		}
	}

	writeJSON(w, http.StatusOK, resp)
}

func handleAPIDietLog(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Only POST method is allowed", http.StatusMethodNotAllowed)
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
	defer closeDB(db)

	if req.Date != "" && req.Date != "now" {
		_, err = db.Exec(`
          INSERT INTO diet_logs (user_id, product_id, amount, logged_at) 
          VALUES (?, ?, ?, datetime(?, '12:00:00'))`,
			req.UserID, req.ProductID, req.AmountG, req.Date)
	} else {
		_, err = db.Exec(`
          INSERT INTO diet_logs (user_id, product_id, amount, logged_at) 
          VALUES (?, ?, ?, datetime('now'))`,
			req.UserID, req.ProductID, req.AmountG)
	}

	if err != nil {
		http.Error(w, "Failed to log food: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusCreated)
}

func handleAPICustomDietLog(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req CustomDietEntryReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid body", http.StatusBadRequest)
		return
	}

	if strings.TrimSpace(req.Name) == "" {
		http.Error(w, "Name is required", http.StatusBadRequest)
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
	defer closeDB(db)

	var query string
	var args []any

	if req.Date != "" && req.Date != "now" {
		query = `
          INSERT INTO diet_logs (user_id, product_id, custom_name, amount, kcal, protein, fat, carbs, logged_at) 
          VALUES (?, NULL, ?, 100.0, ?, ?, ?, ?, datetime(?, '12:00:00'))`
		args = []any{req.UserID, strings.TrimSpace(req.Name), req.Kcal, req.Protein, req.Fat, req.Carbs, req.Date}
	} else {
		query = `
          INSERT INTO diet_logs (user_id, product_id, custom_name, amount, kcal, protein, fat, carbs, logged_at) 
          VALUES (?, NULL, ?, 100.0, ?, ?, ?, ?, datetime('now'))`
		args = []any{req.UserID, strings.TrimSpace(req.Name), req.Kcal, req.Protein, req.Fat, req.Carbs}
	}

	if _, err := db.Exec(query, args...); err != nil {
		http.Error(w, "Failed to log custom entry: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusCreated)
}

func handleAPIUpdateDietLogAmount(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPatch {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req UpdateDietLogAmountReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid body", http.StatusBadRequest)
		return
	}

	if req.LogID == 0 || req.AmountG <= 0 {
		http.Error(w, "Invalid log_id or amount", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer closeDB(db)

	_, err = db.Exec(`UPDATE diet_logs SET amount = ? WHERE id = ?`, req.AmountG, req.LogID)
	if err != nil {
		http.Error(w, "Failed to update log: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
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
	defer closeDB(db)

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
	defer closeDB(db)

	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		userID = "1"
	}

	query := `
       SELECT DISTINCT date(logged_at)
       FROM diet_logs
       WHERE user_id = ? AND date(logged_at) >= date('now', '-120 days')
       ORDER BY date(logged_at)
    `
	rows, err := db.Query(query, userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer closeRows(rows)

	dates := make([]string, 0)
	for rows.Next() {
		var d string
		if err := rows.Scan(&d); err == nil {
			dates = append(dates, d)
		}
	}

	writeJSON(w, http.StatusOK, ConsistencyStatsResponse{
		WorkoutDates: dates,
	})
}
