package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	_ "modernc.org/sqlite"
)

func handleWebIndex(w http.ResponseWriter, r *http.Request) {
	http.ServeFile(w, r, "web/index.html")
}

func handleWebTrainingPage(w http.ResponseWriter, r *http.Request) {
	http.ServeFile(w, r, "web/training.html")
}

// ==========================================
// WIDŻETY DASHBOARDU (HTMX)
// ==========================================

// 1. Widżet Tonażu (Volume Lifted)
func handleWidgetVolume(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	rangeQuery := strings.ToLower(r.URL.Query().Get("range"))
	if rangeQuery == "" {
		rangeQuery = "7d"
	}

	var interval string
	switch rangeQuery {
	case "1d":
		interval = "-1 day"
	case "7d":
		interval = "-7 day"
	case "1m":
		interval = "-1 month"
	case "all":
		interval = ""
	default:
		interval = "-7 day"
		rangeQuery = "7d"
	}

	baseQuery := `
		SELECT COALESCE(SUM(s.weight_kg * s.reps), 0)
		FROM training_workout_sets s
		JOIN training_workout_exercises we ON s.workout_exercise_id = we.id
		JOIN training_workouts w ON we.workout_id = w.id
		WHERE w.user_id = 1 AND s.weight_kg > 0 AND s.reps > 0`

	var args []interface{}
	query := baseQuery
	if interval != "" {
		query += ` AND w.date >= datetime('now', ?)`
		args = append(args, interval)
	}

	var totalVolume float64
	_ = db.QueryRow(query, args...).Scan(&totalVolume)

	btnClass := func(targetRange string) string {
		if rangeQuery == targetRange {
			return "segmented-btn active"
		}
		return "segmented-btn"
	}

	html := fmt.Sprintf(`
		<div class="widget">
			<div class="widget-header">
				<span class="widget-title">Volume Lifted</span>
				<div class="segmented-control">
					<button class="%s" hx-get="/api/widgets/volume?range=1d" hx-target="closest .widget" hx-swap="outerHTML">1D</button>
					<button class="%s" hx-get="/api/widgets/volume?range=7d" hx-target="closest .widget" hx-swap="outerHTML">7D</button>
					<button class="%s" hx-get="/api/widgets/volume?range=1m" hx-target="closest .widget" hx-swap="outerHTML">1M</button>
					<button class="%s" hx-get="/api/widgets/volume?range=all" hx-target="closest .widget" hx-swap="outerHTML">ALL</button>
				</div>
			</div>
			<div class="value" style="color: var(--color-accent);">%.1f kg</div>
			<div style="font-size: 0.75rem; color: var(--color-text-dim); margin-top: 0.25rem;">Working sets volume</div>
		</div>`, btnClass("1d"), btnClass("7d"), btnClass("1m"), btnClass("all"), totalVolume)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, html)
}

// 2. Widżet Powtórzeń i Serii (Total Reps & Sets)
func handleWidgetReps(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	rangeQuery := strings.ToLower(r.URL.Query().Get("range"))
	if rangeQuery == "" {
		rangeQuery = "7d"
	}

	var interval string
	switch rangeQuery {
	case "1d":
		interval = "-1 day"
	case "7d":
		interval = "-7 day"
	case "1m":
		interval = "-1 month"
	case "all":
		interval = ""
	default:
		interval = "-7 day"
		rangeQuery = "7d"
	}

	baseQuery := `
		SELECT COALESCE(SUM(s.reps), 0), COUNT(s.id)
		FROM training_workout_sets s
		JOIN training_workout_exercises we ON s.workout_exercise_id = we.id
		JOIN training_workouts w ON we.workout_id = w.id
		WHERE w.user_id = 1 AND s.reps > 0`

	var args []interface{}
	query := baseQuery
	if interval != "" {
		query += ` AND w.date >= datetime('now', ?)`
		args = append(args, interval)
	}

	var totalReps, totalSets int
	_ = db.QueryRow(query, args...).Scan(&totalReps, &totalSets)

	btnClass := func(targetRange string) string {
		if rangeQuery == targetRange {
			return "segmented-btn active"
		}
		return "segmented-btn"
	}

	html := fmt.Sprintf(`
		<div class="widget">
			<div class="widget-header">
				<span class="widget-title">Total Reps & Sets</span>
				<div class="segmented-control">
					<button class="%s" hx-get="/api/widgets/reps?range=1d" hx-target="closest .widget" hx-swap="outerHTML">1D</button>
					<button class="%s" hx-get="/api/widgets/reps?range=7d" hx-target="closest .widget" hx-swap="outerHTML">7D</button>
					<button class="%s" hx-get="/api/widgets/reps?range=1m" hx-target="closest .widget" hx-swap="outerHTML">1M</button>
					<button class="%s" hx-get="/api/widgets/reps?range=all" hx-target="closest .widget" hx-swap="outerHTML">ALL</button>
				</div>
			</div>
			<div class="value">
				%d <span style="font-size: 1rem; color: var(--color-text-dim); font-weight: normal;">reps (%d sets)</span>
			</div>
			<div style="font-size: 0.75rem; color: var(--color-text-dim); margin-top: 0.25rem;">Logged exercise repetitions</div>
		</div>`, btnClass("1d"), btnClass("7d"), btnClass("1m"), btnClass("all"), totalReps, totalSets)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, html)
}

// 3. Widżet Najczęstszych Ćwiczeń
func handleWidgetTopExercises(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	query := `
		SELECT te.name, COUNT(tws.id) as set_count
		FROM training_exercises te
		JOIN training_workout_exercises twe ON te.id = twe.exercise_id
		JOIN training_workouts tw ON twe.workout_id = tw.id
		JOIN training_workout_sets tws ON twe.id = tws.workout_exercise_id
		WHERE tw.user_id = 1
		GROUP BY te.id
		ORDER BY set_count DESC
		LIMIT 4`

	rows, err := db.Query(query)
	itemsHTML := ""
	if err == nil {
		defer rows.Close()
		rank := 1
		for rows.Next() {
			var name string
			var count int
			if err := rows.Scan(&name, &count); err == nil {
				itemsHTML += fmt.Sprintf(`
					<div style="display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid var(--color-border);">
						<span style="font-size: 0.95rem;">%d. %s</span>
						<strong style="color: var(--color-accent); font-size: 0.9rem;">%d sets</strong>
					</div>`, rank, name, count)
				rank++
			}
		}
	}

	if itemsHTML == "" {
		itemsHTML = `<div style="color: var(--color-text-dim); text-align: center; margin-top: 1rem; font-size: 0.9rem;"><p>No workout data logged yet.</p></div>`
	} else {
		itemsHTML = `<div style="margin-top: 0.5rem; display: flex; flex-direction: column;">` + itemsHTML + `</div>`
	}

	html := fmt.Sprintf(`
		<div class="widget">
			<div class="widget-header">
				<span class="widget-title">Most Frequent Exercises</span>
			</div>
			%s
		</div>`, itemsHTML)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, html)
}

// 4. Widżet Największego Progresu (1RM Epley)
func handleWidgetBiggestProgress(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	query := `
		WITH ExerciseSessions AS (
			SELECT 
				te.name as ex_name,
				tw.id as workout_id,
				tw.date as w_date,
				MAX(tws.weight_kg * (1.0 + (tws.reps / 30.0))) as best_1rm
			FROM training_exercises te
			JOIN training_workout_exercises twe ON te.id = twe.exercise_id
			JOIN training_workouts tw ON twe.workout_id = tw.id
			JOIN training_workout_sets tws ON twe.id = tws.workout_exercise_id
			WHERE tw.user_id = 1 AND tws.weight_kg > 0 AND tws.reps > 0
			GROUP BY te.id, tw.id
		),
		RankedSessions AS (
			SELECT 
				ex_name,
				best_1rm,
				ROW_NUMBER() OVER(PARTITION BY ex_name ORDER BY w_date ASC) as rn_first,
				ROW_NUMBER() OVER(PARTITION BY ex_name ORDER BY w_date DESC) as rn_last
			FROM ExerciseSessions
		)
		SELECT 
			f.ex_name,
			(l.best_1rm - f.best_1rm) as diff_1rm
		FROM RankedSessions f
		JOIN RankedSessions l ON f.ex_name = l.ex_name AND l.rn_last = 1
		WHERE f.rn_first = 1 AND (l.best_1rm - f.best_1rm) > 0
		ORDER BY diff_1rm DESC
		LIMIT 3`

	rows, err := db.Query(query)
	itemsHTML := ""
	if err == nil {
		defer rows.Close()
		rank := 1
		for rows.Next() {
			var name string
			var diff float64
			if err := rows.Scan(&name, &diff); err == nil {
				itemsHTML += fmt.Sprintf(`
					<div style="display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid var(--color-border);">
						<span style="font-size: 0.95rem;">%d. %s</span>
						<strong style="color: var(--color-success); font-size: 0.9rem;">+ %.1f kg</strong>
					</div>`, rank, name, diff)
				rank++
			}
		}
	}

	if itemsHTML == "" {
		itemsHTML = `<div style="color: var(--color-text-dim); text-align: center; margin-top: 1rem; font-size: 0.9rem;"><p>Need multiple sessions to compute progress.</p></div>`
	} else {
		itemsHTML = `<div style="margin-top: 0.5rem; display: flex; flex-direction: column;">` + itemsHTML + `</div>`
	}

	html := fmt.Sprintf(`
		<div class="widget">
			<div class="widget-header">
				<span class="widget-title">Top Strength Gain (1RM)</span>
			</div>
			%s
		</div>`, itemsHTML)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, html)
}

// 5. Widżet Liczby Treningów
func handleWidgetOverallProgress(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	var totalWorkouts int
	_ = db.QueryRow(`SELECT COUNT(id) FROM training_workouts WHERE user_id = 1`).Scan(&totalWorkouts)

	html := fmt.Sprintf(`
		<div class="widget">
			<div class="widget-header">
				<span class="widget-title">Consistency</span>
			</div>
			<div class="value" style="color: var(--color-success);">%d</div>
			<div style="color: var(--color-text-dim); font-size: 0.85rem;">
				Total recorded sessions
			</div>
		</div>`, totalWorkouts)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, html)
}

// 6. Widżet Makroskładników
func handleWidgetMacros(w http.ResponseWriter, r *http.Request) {
	html := `
		<div class="widget">
			<div class="widget-header">
				<span class="widget-title">Daily Macros Target</span>
			</div>
			<div class="macro-label"><span>Calories</span><span>0 / 2700 kcal</span></div>
			<progress value="0" max="2700"></progress>
			<div class="macro-label"><span>Protein</span><span>0 / 140 g</span></div>
			<progress value="0" max="140"></progress>
			<div class="macro-label"><span>Fats</span><span>0 / 75 g</span></div>
			<progress value="0" max="75"></progress>
			<div class="macro-label"><span>Carbs</span><span>0 / 350 g</span></div>
			<progress value="0" max="350"></progress>
			<div style="color: var(--color-text-dim); text-align: center; font-size: 0.75rem; margin-top: 0.5rem;">
				*Awaiting Diet Module implementation.
			</div>
		</div>`
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, html)
}

// 7. Widżet Deficytu Makro
func handleWidgetMacroDeficit(w http.ResponseWriter, r *http.Request) {
	html := `
		<div class="widget">
			<div class="widget-header">
				<span class="widget-title">Most Missing Macro</span>
			</div>
			<div style="color: var(--color-text-dim); font-size: 0.85rem; margin-bottom: 0.5rem;">
				Average daily deficit (last 7 days).
			</div>
			<div class="value" style="color: var(--color-danger); font-size: 1.75rem;">Protein: -35g</div>
			<div style="color: var(--color-text-dim); font-size: 0.75rem; margin-top: 0.5rem;">
				*Awaiting Diet Module implementation.
			</div>
		</div>`
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, html)
}

func handleWidgetVolumeHistory(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, "Database connection error", http.StatusInternalServerError)
		return
	}
	defer db.Close()
	query := `
		SELECT 
			w.id,
			COALESCE(r.name, 'Trening') || ' (' || strftime('%d.%m', w.date) || ')' as label,
			COALESCE(SUM(s.weight_kg * s.reps), 0) as total_volume
		FROM training_workouts w
		LEFT JOIN training_routines r ON r.id = w.routine_id
		LEFT JOIN training_workout_exercises we ON we.workout_id = w.id
		LEFT JOIN training_workout_sets s ON s.workout_exercise_id = we.id AND s.weight_kg > 0 AND s.reps > 0
		WHERE w.user_id = 1
		GROUP BY w.id
		ORDER BY w.date ASC, w.id ASC
		LIMIT 15`

	rows, err := db.Query(query)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	type ChartResponse struct {
		Labels []string  `json:"labels"`
		Data   []float64 `json:"data"`
	}

	resp := ChartResponse{
		Labels: make([]string, 0),
		Data:   make([]float64, 0),
	}

	for rows.Next() {
		var id int
		var label string
		var vol float64
		if err := rows.Scan(&id, &label, &vol); err == nil {
			resp.Labels = append(resp.Labels, label)
			resp.Data = append(resp.Data, vol)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

// ==========================================
// WEB OBSŁUGA TRAINING (ROUTINES & WORKOUTS)
// ==========================================

func handleWebTrainingList(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	if r.Method == http.MethodPost {
		name := strings.TrimSpace(r.FormValue("name"))
		if name != "" {
			_, _ = db.Exec(`INSERT INTO training_routines (user_id, name) VALUES (1, ?)`, name)
		}
	} else if r.Method == http.MethodDelete {
		routineID := r.URL.Query().Get("id")
		if routineID != "" {
			_, _ = db.Exec(`UPDATE training_workouts SET routine_id = NULL WHERE routine_id = ?`, routineID)
			_, _ = db.Exec(`DELETE FROM training_routine_exercises WHERE routine_id = ?`, routineID)
			_, _ = db.Exec(`DELETE FROM training_routines WHERE id = ? AND user_id = 1`, routineID)
		}
	}

	rows, err := db.Query(`
		SELECT r.id, r.name, COUNT(re.id) as ex_count
		FROM training_routines r
		LEFT JOIN training_routine_exercises re ON re.routine_id = r.id
		WHERE r.user_id = 1
		GROUP BY r.id
		ORDER BY r.created_at DESC`)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var sb strings.Builder
	hasRoutines := false

	for rows.Next() {
		hasRoutines = true
		var id, count int
		var name string
		rows.Scan(&id, &name, &count)

		sb.WriteString(fmt.Sprintf(`
			<div class="routine-card" id="routine-%d">
				<div style="flex: 1; cursor: pointer;" hx-get="/web/training/detail?id=%d" hx-target="#routine-detail-modal-content" hx-on::after-request="document.getElementById('routine-modal').showModal()">
					<h4 style="margin: 0 0 0.25rem 0; font-size: 1.15rem; color: #fff;">%s</h4>
					<span style="font-size: 0.85rem; color: var(--color-text-dim);">%d exercises</span>
				</div>
				<div style="display: flex; gap: 8px;">
					<button class="btn btn-secondary" style="padding: 6px 12px; font-size: 0.85rem;"
						hx-get="/web/training/routine/edit?id=%d" 
						hx-target="#routine-edit-modal-content" 
						hx-on::after-request="document.getElementById('routine-edit-modal').showModal()">
						Edit
					</button>
					<button class="btn-danger-outline" style="padding: 6px 12px; font-size: 0.85rem;"
						hx-delete="/web/training/list?id=%d"
						hx-target="#training-view-container"
						hx-confirm="Are you sure you want to delete '%s'?">
						Delete
					</button>
				</div>
			</div>
		`, id, id, name, count, id, id, name))
	}

	if !hasRoutines {
		sb.WriteString(`<div style="color: var(--color-text-dim); text-align: center; padding: 2rem; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-lg);">No training routines found. Create your first routine above!</div>`)
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, sb.String())
}

func handleWebTrainingDetail(w http.ResponseWriter, r *http.Request) {
	routineID := r.URL.Query().Get("id")
	if routineID == "" {
		http.Error(w, "Missing ID", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	var routineName string
	err = db.QueryRow(`SELECT name FROM training_routines WHERE id = ? AND user_id = 1`, routineID).Scan(&routineName)
	if err != nil {
		fmt.Fprint(w, "<p>Routine not found.</p>")
		return
	}

	rows, err := db.Query(`
		SELECT re.id, e.name, e.type, e.equipment
		FROM training_routine_exercises re
		JOIN training_exercises e ON e.id = re.exercise_id
		WHERE re.routine_id = ?
		ORDER BY re.position`, routineID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var exercisesHTML strings.Builder
	idx := 1
	for rows.Next() {
		var reID int
		var exName, exType, exEq string
		rows.Scan(&reID, &exName, &exType, &exEq)

		tRows, _ := db.Query(`SELECT set_number, set_type FROM training_routine_sets WHERE routine_exercise_id = ? ORDER BY set_number`, reID)
		setTags := ""
		for tRows.Next() {
			var sNum int
			var sType string
			tRows.Scan(&sNum, &sType)
			badgeColor := "var(--color-surface-2)"
			badgeText := fmt.Sprintf("Set %d", sNum)
			if sType == "warmup" {
				badgeColor = "#d97706"
				badgeText = fmt.Sprintf("W%d", sNum)
			} else if sType == "failure" {
				badgeColor = "#dc2626"
				badgeText = fmt.Sprintf("F%d", sNum)
			} else if sType == "drop" {
				badgeColor = "#7c3aed"
				badgeText = fmt.Sprintf("D%d", sNum)
			} else if sType == "back_off" {
				badgeColor = "#059669"
				badgeText = fmt.Sprintf("B%d", sNum)
			}
			setTags += fmt.Sprintf(`<span style="background: %s; color: #fff; padding: 3px 8px; border-radius: 4px; font-size: 0.75rem; margin-right: 6px; font-weight: 600;">%s</span>`, badgeColor, badgeText)
		}
		tRows.Close()

		if setTags == "" {
			setTags = `<span style="color: var(--color-text-dim); font-size: 0.8rem;">Standard sets</span>`
		}

		exercisesHTML.WriteString(fmt.Sprintf(`
			<div style="background: var(--color-surface-2); padding: 12px 16px; border-radius: var(--radius-md); margin-bottom: 10px; border: 1px solid var(--color-border);">
				<div style="display: flex; justify-content: space-between; align-items: center;">
					<strong style="color: #fff; font-size: 1rem;">%d. %s</strong>
					<span style="font-size: 0.8rem; color: var(--color-text-dim);">%s • %s</span>
				</div>
				<div style="margin-top: 10px; display: flex; align-items: center; flex-wrap: wrap; gap: 4px;">%s</div>
			</div>
		`, idx, exName, exType, exEq, setTags))
		idx++
	}

	if idx == 1 {
		exercisesHTML.WriteString(`<p style="color: var(--color-text-dim); text-align: center; padding: 1.5rem;">No exercises configured in this routine yet.</p>`)
	}

	html := fmt.Sprintf(`
		<header class="modal-header">
			<h3 style="margin: 0; color: #fff;">%s</h3>
		</header>
		<div style="max-height: 60vh; overflow-y: auto; margin-top: 1rem;">
			%s
		</div>
		<div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 1.5rem;">
			<button class="btn btn-secondary" onclick="document.getElementById('routine-modal').close()">Close</button>
			<button class="btn btn-primary"
				hx-get="/web/training/routine/edit?id=%s" 
				hx-target="#routine-edit-modal-content" 
				hx-on::after-request="document.getElementById('routine-modal').close(); document.getElementById('routine-edit-modal').showModal();">
				Edit Routine
			</button>
		</div>`, routineName, exercisesHTML.String(), routineID)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, html)
}

func handleWebRoutineEditModal(w http.ResponseWriter, r *http.Request) {
	routineID := r.URL.Query().Get("id")
	if routineID == "" {
		http.Error(w, "Missing ID", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	var routineName string
	err = db.QueryRow(`SELECT name FROM training_routines WHERE id = ? AND user_id = 1`, routineID).Scan(&routineName)
	if err != nil {
		fmt.Fprint(w, "<p>Routine not found.</p>")
		return
	}

	allExRows, _ := db.Query(`SELECT id, name FROM training_exercises ORDER BY name ASC`)
	var allExercisesHTML strings.Builder
	allExercisesHTML.WriteString(`<option value="">-- Choose exercise to add --</option>`)
	for allExRows.Next() {
		var id int
		var name string
		allExRows.Scan(&id, &name)
		allExercisesHTML.WriteString(fmt.Sprintf(`<option value="%d">%s</option>`, id, name))
	}
	allExRows.Close()

	rows, err := db.Query(`
		SELECT re.id, re.exercise_id, e.name, e.type, e.equipment
		FROM training_routine_exercises re
		JOIN training_exercises e ON e.id = re.exercise_id
		WHERE re.routine_id = ?
		ORDER BY re.position`, routineID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var sb strings.Builder
	sb.WriteString(fmt.Sprintf(`
		<header class="modal-header">
			<h3 style="margin: 0; color: #fff;">Edit Routine</h3>
		</header>
		<form hx-post="/web/training/routine/save" hx-target="#training-view-container" hx-on::after-request="document.getElementById('routine-edit-modal').close();">
			<input type="hidden" name="routine_id" value="%s">
			
			<div style="margin-top: 1rem;">
				<label style="font-size: 0.85rem; color: var(--color-text-dim); display: block; margin-bottom: 4px;">Routine Name</label>
				<input type="text" name="routine_name" value="%s" class="form-input" required>
			</div>

			<h4 style="margin: 1.5rem 0 0.5rem 0; font-size: 1rem; color: #fff;">Exercises & Sets Template</h4>
			<div id="routine-exercises-list" style="max-height: 48vh; overflow-y: auto; padding-right: 4px;">
	`, routineID, routineName))

	idx := 0
	for rows.Next() {
		var reID, exID int
		var exName, exType, exEq string
		rows.Scan(&reID, &exID, &exName, &exType, &exEq)

		sb.WriteString(fmt.Sprintf(`
			<div class="routine-ex-card" style="background: var(--color-surface-2); padding: 12px; border-radius: var(--radius-md); margin-bottom: 12px; border: 1px solid var(--color-border);" id="ex-card-%d">
				<div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
					<div>
						<strong style="color: #fff;">%d. %s</strong>
						<span style="font-size: 0.75rem; color: var(--color-text-dim); margin-left: 8px;">%s • %s</span>
					</div>
					<button type="button" class="btn-danger-outline" style="padding: 2px 8px; font-size: 0.75rem;" onclick="document.getElementById('ex-card-%d').remove()">Remove</button>
				</div>
				<input type="hidden" name="exercise_ids" value="%d">
				
				<div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;" id="sets-container-%d">
		`, idx, idx+1, exName, exType, exEq, idx, exID, idx))

		tRows, _ := db.Query(`SELECT set_number, set_type FROM training_routine_sets WHERE routine_exercise_id = ? ORDER BY set_number`, reID)
		setCount := 0
		for tRows.Next() {
			var sNum int
			var sType string
			tRows.Scan(&sNum, &sType)
			setCount++

			sb.WriteString(fmt.Sprintf(`
				<div class="set-badge" style="display: inline-flex; align-items: center; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: 4px; padding: 2px 6px;">
					<span style="font-size: 0.75rem; margin-right: 4px; color: var(--color-text-dim);">S%d:</span>
					<select name="set_types_%d" style="background: transparent; border: none; color: #fff; font-size: 0.75rem;">
						<option value="standard" %s>Standard</option>
						<option value="warmup" %s>Warmup</option>
						<option value="failure" %s>Failure</option>
						<option value="drop" %s>Drop</option>
						<option value="back_off" %s>Back-off</option>
					</select>
				</div>
			`, sNum, exID,
				selectedAttr(sType == "standard"),
				selectedAttr(sType == "warmup"),
				selectedAttr(sType == "failure"),
				selectedAttr(sType == "drop"),
				selectedAttr(sType == "back_off")))
		}
		tRows.Close()

		if setCount == 0 {
			for s := 1; s <= 3; s++ {
				sb.WriteString(fmt.Sprintf(`
					<div class="set-badge" style="display: inline-flex; align-items: center; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: 4px; padding: 2px 6px;">
						<span style="font-size: 0.75rem; margin-right: 4px; color: var(--color-text-dim);">S%d:</span>
						<select name="set_types_%d" style="background: transparent; border: none; color: #fff; font-size: 0.75rem;">
							<option value="standard" selected>Standard</option>
							<option value="warmup">Warmup</option>
							<option value="failure">Failure</option>
							<option value="drop">Drop</option>
							<option value="back_off">Back-off</option>
						</select>
					</div>
				`, s, exID))
			}
		}

		sb.WriteString(fmt.Sprintf(`
				</div>
				<button type="button" class="btn btn-secondary" style="margin-top: 8px; padding: 2px 8px; font-size: 0.75rem;" onclick="addSetBadge(%d, %d)">+ Add Set</button>
			</div>
		`, idx, exID))

		idx++
	}

	sb.WriteString(fmt.Sprintf(`
			</div>

			<div style="margin-top: 1rem; padding: 12px; background: var(--color-surface-2); border-radius: var(--radius-md); border: 1px dashed var(--color-border);">
				<label style="font-size: 0.85rem; color: var(--color-text-dim); display: block; margin-bottom: 4px;">Add Exercise to Routine</label>
				<div style="display: flex; gap: 8px;">
					<select id="new-ex-select" class="form-input" style="flex: 1;">
						%s
					</select>
					<button type="button" class="btn btn-secondary" onclick="addExerciseToRoutineUI()">+ Add</button>
				</div>
			</div>

			<div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 1.5rem;">
				<button type="button" class="btn btn-secondary" onclick="document.getElementById('routine-edit-modal').close()">Cancel</button>
				<button type="submit" class="btn btn-primary">Save Routine</button>
			</div>
		</form>
	`, allExercisesHTML.String()))

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, sb.String())
}

func selectedAttr(cond bool) string {
	if cond {
		return "selected"
	}
	return ""
}

func handleWebRoutineSave(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	_ = r.ParseForm()
	routineID := r.FormValue("routine_id")
	routineName := strings.TrimSpace(r.FormValue("routine_name"))
	if routineID == "" || routineName == "" {
		http.Error(w, "Missing fields", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	tx, err := db.Begin()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	_, _ = tx.Exec(`UPDATE training_routines SET name = ? WHERE id = ? AND user_id = 1`, routineName, routineID)
	_, _ = tx.Exec(`DELETE FROM training_routine_sets WHERE routine_exercise_id IN (SELECT id FROM training_routine_exercises WHERE routine_id = ?)`, routineID)
	_, _ = tx.Exec(`DELETE FROM training_routine_exercises WHERE routine_id = ?`, routineID)

	exerciseIDs := r.Form["exercise_ids"]
	for pos, exIDStr := range exerciseIDs {
		var exID int
		fmt.Sscanf(exIDStr, "%d", &exID)
		if exID == 0 {
			continue
		}

		res, err := tx.Exec(`INSERT INTO training_routine_exercises (routine_id, exercise_id, position, default_sets) VALUES (?, ?, ?, 3)`, routineID, exID, pos+1)
		if err != nil {
			continue
		}
		newReID, _ := res.LastInsertId()

		setTypes := r.Form[fmt.Sprintf("set_types_%d", exID)]
		if len(setTypes) == 0 {
			setTypes = []string{"standard", "standard", "standard"}
		}

		for sIdx, sType := range setTypes {
			_, _ = tx.Exec(`INSERT INTO training_routine_sets (routine_exercise_id, set_number, set_type) VALUES (?, ?, ?)`, newReID, sIdx+1, sType)
		}
	}

	_ = tx.Commit()
	handleWebTrainingList(w, r)
}

func handleWebWorkoutsTab(w http.ResponseWriter, r *http.Request) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	if r.Method == http.MethodDelete {
		workoutID := r.URL.Query().Get("id")
		if workoutID != "" {
			tx, _ := db.Begin()
			tx.Exec(`DELETE FROM training_workout_sets WHERE workout_exercise_id IN (SELECT id FROM training_workout_exercises WHERE workout_id = ?)`, workoutID)
			tx.Exec(`DELETE FROM training_workout_exercises WHERE workout_id = ?`, workoutID)
			tx.Exec(`DELETE FROM training_workouts WHERE id = ?`, workoutID)
			tx.Commit()
		}
	}

	query := `
		SELECT 
			w.id,
			w.date,
			COALESCE(r.name, 'Custom Workout') as routine_name,
			COALESCE(SUM(CASE WHEN s.weight_kg > 0 AND s.reps > 0 THEN s.weight_kg * s.reps ELSE 0 END), 0) as total_volume,
			COUNT(s.id) as total_sets
		FROM training_workouts w
		LEFT JOIN training_routines r ON r.id = w.routine_id
		LEFT JOIN training_workout_exercises we ON we.workout_id = w.id
		LEFT JOIN training_workout_sets s ON s.workout_exercise_id = we.id
		WHERE w.user_id = 1
		GROUP BY w.id, w.date, r.name
		ORDER BY w.date DESC, w.id DESC`

	rows, err := db.Query(query)
	if err != nil {
		http.Error(w, fmt.Sprintf("Query error: %v", err), http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var sb strings.Builder
	hasWorkouts := false

	sb.WriteString(`
		<div style="background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-lg); overflow: hidden; padding: 0.5rem 1rem;">
		<table style="width: 100%; border-collapse: collapse; text-align: left;">
			<thead>
				<tr style="border-bottom: 1px solid var(--color-border); color: var(--color-text-dim); font-size: 0.85rem;">
					<th style="padding: 12px 8px;">Date</th>
					<th style="padding: 12px 8px;">Routine</th>
					<th style="padding: 12px 8px;">Volume</th>
					<th style="padding: 12px 8px;">Sets</th>
					<th style="padding: 12px 8px; text-align: right;">Actions</th>
				</tr>
			</thead>
			<tbody>`)

	for rows.Next() {
		hasWorkouts = true
		var id, sets int
		var dateStr, name string
		var volume float64
		if err := rows.Scan(&id, &dateStr, &name, &volume, &sets); err != nil {
			continue
		}

		displayDate := dateStr
		if len(dateStr) >= 16 {
			displayDate = strings.Replace(dateStr[:16], "T", " ", 1)
		}

		sb.WriteString(fmt.Sprintf(`
			<tr style="border-bottom: 1px solid var(--color-border);">
				<td style="padding: 12px 8px; font-size: 0.9rem; color: var(--color-text-dim);">%s</td>
				<td style="padding: 12px 8px; font-size: 0.95rem; font-weight: 600; color: #fff;">%s</td>
				<td style="padding: 12px 8px; font-size: 0.95rem; color: var(--color-accent); font-weight: 600;">%.1f kg</td>
				<td style="padding: 12px 8px; font-size: 0.9rem; color: var(--color-text-dim);">%d</td>
				<td style="padding: 12px 8px; text-align: right;">
					<button class="btn btn-secondary" style="padding: 4px 10px; font-size: 0.8rem; margin-right: 6px;"
						hx-get="/web/training/workouts/edit?id=%d" 
						hx-target="#workout-edit-modal-content" 
						hx-on::after-request="document.getElementById('workout-edit-modal').showModal()">
						Edit
					</button>
					<button class="btn-danger-outline" style="padding: 4px 10px; font-size: 0.8rem;"
						hx-delete="/web/training/workouts/tab?id=%d" 
						hx-target="#training-view-container" 
						hx-confirm="Are you sure you want to delete this workout?">
						Delete
					</button>
				</td>
			</tr>`, displayDate, name, volume, sets, id, id))
	}

	sb.WriteString(`</tbody></table></div>`)

	if !hasWorkouts {
		sb.Reset()
		sb.WriteString(`<div style="color: var(--color-text-dim); text-align: center; padding: 3rem; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-lg);">No workouts logged yet. Finish a session in the mobile app to see it here!</div>`)
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, sb.String())
}

func handleWebWorkoutEditModal(w http.ResponseWriter, r *http.Request) {
	workoutID := r.URL.Query().Get("id")
	if workoutID == "" {
		http.Error(w, "Missing workout ID", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	var workoutDate, routineName string
	err = db.QueryRow(`
		SELECT w.date, COALESCE(r.name, 'Custom Workout') 
		FROM training_workouts w 
		LEFT JOIN training_routines r ON r.id = w.routine_id 
		WHERE w.id = ? AND w.user_id = 1`, workoutID).Scan(&workoutDate, &routineName)

	if err != nil {
		fmt.Fprint(w, "<p>Workout not found.</p>")
		return
	}

	rows, err := db.Query(`
		SELECT we.id, we.exercise_id, e.name 
		FROM training_workout_exercises we
		JOIN training_exercises e ON e.id = we.exercise_id
		WHERE we.workout_id = ?
		ORDER BY we.position`, workoutID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	type wEx struct {
		weID int
		exID int
		name string
	}
	var exercises []wEx
	for rows.Next() {
		var item wEx
		rows.Scan(&item.weID, &item.exID, &item.name)
		exercises = append(exercises, item)
	}

	var sb strings.Builder
	sb.WriteString(fmt.Sprintf(`
		<header class="modal-header">
			<div>
				<h3 style="margin: 0; color: #fff;">Edit Workout: %s</h3>
				<span style="font-size: 0.85rem; color: var(--color-text-dim);">Logged: %s</span>
			</div>
		</header>
		<form hx-post="/web/training/workouts/save" hx-target="#training-view-container" hx-on::after-request="document.getElementById('workout-edit-modal').close();">
			<input type="hidden" name="workout_id" value="%s">
			<div style="max-height: 60vh; overflow-y: auto; margin-top: 1rem; padding-right: 4px;">
	`, routineName, workoutDate, workoutID))

	for exIdx, ex := range exercises {
		sb.WriteString(fmt.Sprintf(`
			<div class="edit-exercise-block" style="background: var(--color-surface-2); padding: 12px; border-radius: var(--radius-md); margin-bottom: 12px;">
				<div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
					<strong>%d. %s</strong>
					<input type="hidden" name="exercise_id_%d" value="%d">
				</div>
				<table style="width: 100%%; font-size: 0.85rem;">
					<thead>
						<tr style="color: var(--color-text-dim); text-align: left;">
							<th style="width: 15%%;">Set</th>
							<th style="width: 35%%;">Weight (kg)</th>
							<th style="width: 25%%;">Reps</th>
							<th style="width: 25%%;">RIR</th>
						</tr>
					</thead>
					<tbody>
		`, exIdx+1, ex.name, exIdx, ex.exID))

		setRows, _ := db.Query(`SELECT set_number, weight_kg, reps, rir FROM training_workout_sets WHERE workout_exercise_id = ? ORDER BY set_number`, ex.weID)
		setIdx := 1
		for setRows.Next() {
			var setNum, reps, rir int
			var weight float64
			setRows.Scan(&setNum, &weight, &reps, &rir)

			sb.WriteString(fmt.Sprintf(`
				<tr>
					<td><strong>%d</strong></td>
					<td><input type="number" step="0.5" min="0" name="weight_%d_%d" value="%.1f" style="background: var(--color-surface); border: 1px solid var(--color-border); color: #fff; padding: 4px 8px; border-radius: 4px; width: 90%%;"></td>
					<td><input type="number" min="0" name="reps_%d_%d" value="%d" style="background: var(--color-surface); border: 1px solid var(--color-border); color: #fff; padding: 4px 8px; border-radius: 4px; width: 90%%;"></td>
					<td><input type="number" min="0" max="10" name="rir_%d_%d" value="%d" style="background: var(--color-surface); border: 1px solid var(--color-border); color: #fff; padding: 4px 8px; border-radius: 4px; width: 90%%;"></td>
				</tr>
			`, setIdx, exIdx, setIdx-1, weight, exIdx, setIdx-1, reps, exIdx, setIdx-1, rir))
			setIdx++
		}
		setRows.Close()

		sb.WriteString(fmt.Sprintf(`
					</tbody>
				</table>
				<input type="hidden" name="sets_count_%d" value="%d">
			</div>
		`, exIdx, setIdx-1))
	}

	sb.WriteString(fmt.Sprintf(`
			<input type="hidden" name="exercises_count" value="%d">
			</div>
			<div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 1.5rem;">
				<button type="button" class="btn btn-secondary" onclick="document.getElementById('workout-edit-modal').close()">Cancel</button>
				<button type="submit" class="btn btn-primary">Save Changes</button>
			</div>
		</form>
	`, len(exercises)))

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, sb.String())
}

func handleWebWorkoutSave(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	_ = r.ParseForm()
	workoutID := r.FormValue("workout_id")
	if workoutID == "" {
		http.Error(w, "Missing workout_id", http.StatusBadRequest)
		return
	}

	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer db.Close()

	tx, err := db.Begin()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	_, _ = tx.Exec(`DELETE FROM training_workout_sets WHERE workout_exercise_id IN (SELECT id FROM training_workout_exercises WHERE workout_id = ?)`, workoutID)
	_, _ = tx.Exec(`DELETE FROM training_workout_exercises WHERE workout_id = ?`, workoutID)

	var exCount int
	fmt.Sscanf(r.FormValue("exercises_count"), "%d", &exCount)

	for i := 0; i < exCount; i++ {
		exIDStr := r.FormValue(fmt.Sprintf("exercise_id_%d", i))
		var exID int
		fmt.Sscanf(exIDStr, "%d", &exID)

		res, err := tx.Exec(`INSERT INTO training_workout_exercises (workout_id, exercise_id, position) VALUES (?, ?, ?)`, workoutID, exID, i+1)
		if err != nil {
			continue
		}
		weID, _ := res.LastInsertId()

		var setsCount int
		fmt.Sscanf(r.FormValue(fmt.Sprintf("sets_count_%d", i)), "%d", &setsCount)

		for s := 0; s < setsCount; s++ {
			var weight float64
			var reps, rir int
			fmt.Sscanf(r.FormValue(fmt.Sprintf("weight_%d_%d", i, s)), "%f", &weight)
			fmt.Sscanf(r.FormValue(fmt.Sprintf("reps_%d_%d", i, s)), "%d", &reps)
			fmt.Sscanf(r.FormValue(fmt.Sprintf("rir_%d_%d", i, s)), "%d", &rir)

			_, _ = tx.Exec(`
				INSERT INTO training_workout_sets (workout_exercise_id, set_number, weight_kg, reps, rir, set_type)
				VALUES (?, ?, ?, ?, ?, 'standard')`,
				weID, s+1, weight, reps, rir)
		}
	}

	_ = tx.Commit()
	handleWebWorkoutsTab(w, r)
}
