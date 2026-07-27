package main

import (
	"fmt"
	"log"
	"net/http"
)

func main() {
	// Prosty endpoint testowy
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, "<h1>F.O.S.S. System is running!</h1>")
	})

	port := ":8080"
	fmt.Printf("Serwer F.O.S.S. wystartował pod adresem: http://localhost%s\n", port)

	if err := http.ListenAndServe(port, nil); err != nil {
		log.Fatalf("Błąd uruchamiania serwera: %v", err)
	}
}
