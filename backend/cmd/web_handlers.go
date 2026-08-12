package main

import (
	"fmt"
	"net/http"
)

func handleWebIndex(w http.ResponseWriter, r *http.Request) {
	http.ServeFile(w, r, "web/index.html")
}

// ==========================================
// MODUŁOWE WIDŻETY (HTMX Lazy Loading)
// ==========================================

func handleWidgetVolume(w http.ResponseWriter, r *http.Request) {
	html := `
		<div class="widget">
			<h3>Volume Lifted (kg)</h3>
			<div class="filter-group">
				<button class="secondary outline">1D</button>
				<button class="secondary outline">7D</button>
				<button class="secondary outline">1M</button>
				<button class="secondary outline">All</button>
			</div>
			<div class="value">Awaiting...</div>
		</div>`
	fmt.Fprint(w, html)
}

func handleWidgetReps(w http.ResponseWriter, r *http.Request) {
	html := `
		<div class="widget">
			<h3>Total Reps</h3>
			<div class="filter-group">
				<button class="secondary outline">1D</button>
				<button class="secondary outline">7D</button>
				<button class="secondary outline">1M</button>
				<button class="secondary outline">All</button>
			</div>
			<div class="value">Awaiting...</div>
		</div>`
	fmt.Fprint(w, html)
}

func handleWidgetMacros(w http.ResponseWriter, r *http.Request) {
	html := `
		<div class="widget">
			<h3>Daily Macros Target</h3>
			<div class="macro-label"><span>Calories</span><span>0 / 2700 kcal</span></div>
			<progress value="0" max="2700"></progress>
			<div class="macro-label"><span>Protein</span><span>0 / 140 g</span></div>
			<progress value="0" max="140"></progress>
			<div class="macro-label"><span>Fats</span><span>0 / 75 g</span></div>
			<progress value="0" max="75"></progress>
			<div class="macro-label"><span>Carbs</span><span>0 / 350 g</span></div>
			<progress value="0" max="350"></progress>
		</div>`
	fmt.Fprint(w, html)
}

func handleWidgetTopExercises(w http.ResponseWriter, r *http.Request) {
	html := `
		<div class="widget">
			<h3>Most Frequent Exercises</h3>
			<div style="color: var(--color-text-dim); text-align: center; margin-top: 1rem; font-size: 0.9rem;">
				<p>Log some workouts to see your top movements here.</p>
			</div>
		</div>`
	fmt.Fprint(w, html)
}

func handleWidgetBiggestProgress(w http.ResponseWriter, r *http.Request) {
	html := `
		<div class="widget">
			<h3>Biggest Progress</h3>
			<div style="margin-top: 1rem; display: flex; flex-direction: column; gap: 0.5rem;">
				<div class="macro-label"><span>1. Deadlift</span><span style="color: var(--color-success);">+ 15 kg</span></div>
				<div class="macro-label"><span>2. Bench Press</span><span style="color: var(--color-success);">+ 5 kg</span></div>
				<div class="macro-label"><span>3. Dumbbell Row</span><span style="color: var(--color-success);">+ 2.5 kg</span></div>
			</div>
			<div style="color: var(--color-text-dim); text-align: center; margin-top: auto; padding-top: 1rem; font-size: 0.75rem;">
				*Mock data. Chart coming soon.
			</div>
		</div>`
	fmt.Fprint(w, html)
}

func handleWidgetOverallProgress(w http.ResponseWriter, r *http.Request) {
	html := `
		<div class="widget">
			<h3>Overall Progress</h3>
			<div style="color: var(--color-text-dim); text-align: center; margin-top: 1rem; font-size: 0.9rem;">
				<p>Chart placeholder: Volume trend line over the last 6 months.</p>
			</div>
			<div class="value" style="color: var(--color-success); font-size: 2rem;">+ 12.4%</div>
			<div style="color: var(--color-text-dim); text-align: center; margin-top: auto; padding-top: 1rem; font-size: 0.75rem;">
				*Mock data. Line chart coming soon.
			</div>
		</div>`
	fmt.Fprint(w, html)
}

func handleWidgetMacroDeficit(w http.ResponseWriter, r *http.Request) {
	html := `
		<div class="widget">
			<h3>Most Missing Macro</h3>
			<div style="color: var(--color-text-dim); text-align: center; margin-top: 1rem; font-size: 0.9rem;">
				<p>Your average daily deficit based on last 7 days.</p>
			</div>
			<div class="value" style="color: var(--color-danger); font-size: 1.8rem;">Protein: -35g</div>
			<div style="color: var(--color-text-dim); text-align: center; margin-top: auto; padding-top: 1rem; font-size: 0.75rem;">
				*Awaiting Diet Module logic.
			</div>
		</div>`
	fmt.Fprint(w, html)
}

func handleWebDashboardVolume(w http.ResponseWriter, r *http.Request) {
	fmt.Fprint(w, "0")
}
