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

// ==========================================
// WEB OBSŁUGA TRAINING / ROUTINES (HTMX)
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
				<div>
					<button class="btn-danger-outline"
						hx-delete="/web/training/list?id=%d"
						hx-target="#routines-container"
						hx-confirm="Are you sure you want to delete '%s'?">
						Delete
					</button>
				</div>
			</div>
		`, id, id, name, count, id, name))
	}

	if !hasRoutines {
		sb.WriteString(`<div style="color: var(--color-text-dim); text-align: center; padding: 2rem;">No training routines found. Create your first routine above!</div>`)
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
			}
			setTags += fmt.Sprintf(`<span style="background: %s; padding: 2px 8px; border-radius: 4px; font-size: 0.75rem; margin-right: 4px;">%s</span>`, badgeColor, badgeText)
		}
		tRows.Close()

		if setTags == "" {
			setTags = `<span style="color: var(--color-text-dim); font-size: 0.8rem;">Standard sets</span>`
		}

		exercisesHTML.WriteString(fmt.Sprintf(`
			<div style="background: var(--color-surface-2); padding: 12px; border-radius: var(--radius-md); margin-bottom: 8px;">
				<div style="display: flex; justify-content: space-between; align-items: center;">
					<strong>%d. %s</strong>
					<span style="font-size: 0.8rem; color: var(--color-text-dim);">%s • %s</span>
				</div>
				<div style="margin-top: 8px;">%s</div>
			</div>
		`, idx, exName, exType, exEq, setTags))
		idx++
	}

	if idx == 1 {
		exercisesHTML.WriteString(`<p style="color: var(--color-text-dim);">No exercises configured in this routine.</p>`)
	}

	html := fmt.Sprintf(`
		<header class="modal-header">
			<h3 style="margin: 0; color: #fff;">%s</h3>
		</header>
		<div style="max-height: 60vh; overflow-y: auto; margin-top: 1rem;">
			%s
		</div>`, routineName, exercisesHTML.String())

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
