package main

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"fmt"
	_ "modernc.org/sqlite"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

func TestAllRequirements(t *testing.T) {
	_ = os.Chdir("..") // switch to backend/ directory so dbPath ("database/foss.db") resolves
	defer func() { _ = os.Chdir("cmd") }()

	migrateDB()

	// 1. Requirement 1: Create routine with group, archive, unarchive, change group
	testCreateReq := map[string]interface{}{
		"user_id":    1,
		"name":       "Test Routine PPL",
		"group_name": "Push Pull Legs",
	}
	body, _ := json.Marshal(testCreateReq)
	req := httptest.NewRequest("POST", "/api/routines/create", bytes.NewReader(body))
	w := httptest.NewRecorder()
	handleAPIRoutineCreate(w, req)
	if w.Code != http.StatusCreated {
		t.Fatalf("Failed to create routine: %d %s", w.Code, w.Body.String())
	}

	// Fetch routines to find the created one
	req = httptest.NewRequest("GET", "/api/routines?user_id=1", nil)
	w = httptest.NewRecorder()
	handleAPIRoutines(w, req)
	var routines []Routine
	_ = json.Unmarshal(w.Body.Bytes(), &routines)
	var createdRoutine *Routine
	for _, r := range routines {
		if r.Name == "Test Routine PPL" {
			createdRoutine = &r
			break
		}
	}
	if createdRoutine == nil || createdRoutine.GroupName != "Push Pull Legs" || createdRoutine.IsArchived {
		t.Fatalf("Created routine mismatch: %+v", createdRoutine)
	}

	// Archive the routine
	isArchivedTrue := true
	updateReq := map[string]interface{}{
		"id":          createdRoutine.ID,
		"name":        createdRoutine.Name,
		"is_archived": &isArchivedTrue,
	}
	body, _ = json.Marshal(updateReq)
	req = httptest.NewRequest("POST", "/api/routines/update", bytes.NewReader(body))
	w = httptest.NewRecorder()
	handleAPIRoutineUpdate(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("Failed to archive routine: %d", w.Code)
	}

	// Verify archived
	req = httptest.NewRequest("GET", "/api/routines?user_id=1", nil)
	w = httptest.NewRecorder()
	handleAPIRoutines(w, req)
	_ = json.Unmarshal(w.Body.Bytes(), &routines)
	for _, r := range routines {
		if r.ID == createdRoutine.ID {
			if !r.IsArchived {
				t.Fatalf("Expected routine to be archived!")
			}
			break
		}
	}

	// Unarchive and change group
	isArchivedFalse := false
	newGroup := "Upper Lower"
	updateReq = map[string]interface{}{
		"id":          createdRoutine.ID,
		"name":        createdRoutine.Name,
		"group_name":  &newGroup,
		"is_archived": &isArchivedFalse,
	}
	body, _ = json.Marshal(updateReq)
	req = httptest.NewRequest("POST", "/api/routines/update", bytes.NewReader(body))
	w = httptest.NewRecorder()
	handleAPIRoutineUpdate(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("Failed to update routine group/unarchive: %d", w.Code)
	}

	req = httptest.NewRequest("GET", "/api/routines?user_id=1", nil)
	w = httptest.NewRecorder()
	handleAPIRoutines(w, req)
	_ = json.Unmarshal(w.Body.Bytes(), &routines)
	for _, r := range routines {
		if r.ID == createdRoutine.ID {
			if r.IsArchived || r.GroupName != "Upper Lower" {
				t.Fatalf("Expected unarchived and group 'Upper Lower', got: %+v", r)
			}
			break
		}
	}

	// 2. Requirement 3 & 4: Adding exercises, updating sets, setting rest timer
	// Add Exercise 1 (exercise_id = 1)
	addExReq := map[string]interface{}{
		"routine_id":  createdRoutine.ID,
		"exercise_id": 1,
	}
	body, _ = json.Marshal(addExReq)
	req = httptest.NewRequest("POST", "/api/routines/exercises/add", bytes.NewReader(body))
	w = httptest.NewRecorder()
	handleAPIRoutineExerciseAdd(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("Failed to add exercise 1")
	}

	// Fetch routine exercises
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/routines/exercises?routine_id=%d", createdRoutine.ID), nil)
	w = httptest.NewRecorder()
	handleAPIRoutineExercises(w, req)
	var previewExercises []RoutineExercisePreview
	_ = json.Unmarshal(w.Body.Bytes(), &previewExercises)
	if len(previewExercises) != 1 {
		t.Fatalf("Expected 1 exercise")
	}
	re1 := previewExercises[0]

	// Update sets of exercise 1 to have 3 sets and rest timer 150 seconds
	restSec := 150
	updateSetsReq := map[string]interface{}{
		"routine_exercise_id": re1.RoutineExerciseID,
		"sets": []map[string]interface{}{
			{"set_number": 1, "set_type": "standard"},
			{"set_number": 2, "set_type": "warmup"},
			{"set_number": 3, "set_type": "failure"},
		},
		"rest_seconds": restSec,
	}
	body, _ = json.Marshal(updateSetsReq)
	req = httptest.NewRequest("POST", "/api/routines/exercises/sets/update", bytes.NewReader(body))
	w = httptest.NewRecorder()
	handleAPIUpdateRoutineSets(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("Failed to update sets")
	}

	// Now add exercise 2 (exercise_id = 2)
	addExReq2 := map[string]interface{}{
		"routine_id":  createdRoutine.ID,
		"exercise_id": 2,
	}
	body, _ = json.Marshal(addExReq2)
	req = httptest.NewRequest("POST", "/api/routines/exercises/add", bytes.NewReader(body))
	w = httptest.NewRecorder()
	handleAPIRoutineExerciseAdd(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("Failed to add exercise 2")
	}

	// Fetch routine exercises again: verify exercise 1 STILL HAS 3 sets and rest_seconds 150!
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/routines/exercises?routine_id=%d", createdRoutine.ID), nil)
	w = httptest.NewRecorder()
	handleAPIRoutineExercises(w, req)
	_ = json.Unmarshal(w.Body.Bytes(), &previewExercises)
	if len(previewExercises) != 2 {
		t.Fatalf("Expected 2 exercises, got %d", len(previewExercises))
	}
	if len(previewExercises[0].TemplateSets) != 3 {
		t.Fatalf("Exercise 1 sets were reset! Expected 3 sets, got %d", len(previewExercises[0].TemplateSets))
	}
	if previewExercises[0].RestSeconds != 150 {
		t.Fatalf("Expected rest_seconds 150, got %d", previewExercises[0].RestSeconds)
	}

	// 3. Requirement 2: Start workout, delete routine, ensure workout keeps original routine name
	startWkReq := map[string]interface{}{
		"routine_id": createdRoutine.ID,
		"user_id":    1,
	}
	body, _ = json.Marshal(startWkReq)
	req = httptest.NewRequest("POST", "/api/workouts/start", bytes.NewReader(body))
	w = httptest.NewRecorder()
	handleAPIStartWorkout(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("Failed to start workout: %d", w.Code)
	}
	var startWkResp StartWorkoutResponse
	_ = json.Unmarshal(w.Body.Bytes(), &startWkResp)
	if startWkResp.WorkoutID == 0 {
		t.Fatalf("Invalid workout ID")
	}
	if len(startWkResp.Exercises) > 0 && startWkResp.Exercises[0].RestSeconds != 150 {
		t.Fatalf("Expected workout exercise to have rest timer 150, got %d", startWkResp.Exercises[0].RestSeconds)
	}

	// Now delete the routine!
	req = httptest.NewRequest("DELETE", fmt.Sprintf("/api/routines?id=%d", createdRoutine.ID), nil)
	w = httptest.NewRecorder()
	handleAPIRoutines(w, req)
	if w.Code != http.StatusNoContent {
		t.Fatalf("Failed to delete routine")
	}

	// Check historical workouts list
	req = httptest.NewRequest("GET", "/api/workouts?user_id=1", nil)
	w = httptest.NewRecorder()
	handleAPIWorkouts(w, req)
	var workoutList []WorkoutSummary
	_ = json.Unmarshal(w.Body.Bytes(), &workoutList)
	var testWorkout *WorkoutSummary
	for _, wk := range workoutList {
		if wk.WorkoutID == startWkResp.WorkoutID {
			testWorkout = &wk
			break
		}
	}
	if testWorkout == nil {
		t.Fatalf("Workout not found in list")
	}
	if testWorkout.RoutineName != "Test Routine PPL" {
		t.Fatalf("Historical workout routine name was overwritten! Expected 'Test Routine PPL', got '%s'", testWorkout.RoutineName)
	}

	// Check workout details
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/workouts/details?workout_id=%d", startWkResp.WorkoutID), nil)
	w = httptest.NewRecorder()
	handleAPIWorkoutDetails(w, req)
	var detailResp WorkoutDetailResponse
	_ = json.Unmarshal(w.Body.Bytes(), &detailResp)
	if detailResp.RoutineName != "Test Routine PPL" {
		t.Fatalf("Workout details routine name was overwritten! Expected 'Test Routine PPL', got '%s'", detailResp.RoutineName)
	}

	// Cleanup test workout
	db, _ := sql.Open("sqlite", dbPath)
	_, _ = db.Exec("DELETE FROM training_workout_exercises WHERE workout_id = ?", startWkResp.WorkoutID)
	_, _ = db.Exec("DELETE FROM training_workouts WHERE id = ?", startWkResp.WorkoutID)
	db.Close()
}
