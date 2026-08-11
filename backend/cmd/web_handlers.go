package main

import (
	"fmt"
	"net/http"
)

func handleWebIndex(w http.ResponseWriter, r *http.Request) {
	http.ServeFile(w, r, "web/index.html")
}

func handleWebDashboardWidgets(w http.ResponseWriter, r *http.Request) {
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
}

func handleWebDashboardVolume(w http.ResponseWriter, r *http.Request) {
	fmt.Fprint(w, "0")
}
