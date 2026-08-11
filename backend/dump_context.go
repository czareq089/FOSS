package main

import (
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

// 1. ZMIANA: Jesteśmy już w folderze backend, więc baza jest tuż obok w 'database/'
const dbPath = "database/foss.db"

func dumpSchema(file *os.File) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		fmt.Println("Nie udało się otworzyć bazy:", err)
		return
	}
	defer db.Close()

	rows, err := db.Query(`SELECT sql FROM sqlite_master WHERE type = 'table' AND sql IS NOT NULL ORDER BY name`)
	if err != nil {
		fmt.Println("Nie udało się odczytać schematu:", err)
		return
	}
	defer rows.Close()

	file.WriteString("### Database Schema\n```sql\n")
	for rows.Next() {
		var stmt string
		if err := rows.Scan(&stmt); err != nil {
			continue
		}
		file.WriteString(stmt + ";\n\n")
	}
	file.WriteString("```\n\n")
}

func main() {
	// 2. ZMIANA: Tworzymy folder .llm_context jeden poziom wyżej (w głównym katalogu FOSS)
	outDir := "../.llm_context"
	_ = os.MkdirAll(outDir, os.ModePerm)

	timestamp := time.Now().Format("2006-01-02_15-04-05")
	outPath := filepath.Join(outDir, fmt.Sprintf("context_%s.md", timestamp))

	file, err := os.Create(outPath)
	if err != nil {
		fmt.Println("Błąd tworzenia pliku:", err)
		return
	}
	defer file.Close()

	file.WriteString("# F.O.S.S. - Kontekst Projektu\n\n")

	dumpSchema(file)

	ignoreDirs := map[string]bool{
		".git": true, ".idea": true, ".llm_context": true,
		"database": true, "bin": true, "build": true, ".gradle": true,
	}

	validExts := map[string]bool{
		".go": true, ".html": true, ".kt": true,
		".xml": true, ".sql": true, ".mod": true,
	}

	fmt.Println("Skanowanie projektu...")

	// 3. ZMIANA KLUCZOWA: ".." każe skryptowi skanować katalog nadrzędny (główny folder projektu)
	err = filepath.WalkDir("..", func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return nil
		}

		if d.IsDir() {
			if ignoreDirs[d.Name()] {
				return filepath.SkipDir
			}
			return nil
		}

		ext := filepath.Ext(path)
		if !validExts[ext] {
			return nil
		}

		content, err := os.ReadFile(path)
		if err != nil {
			return nil
		}

		lang := strings.TrimPrefix(ext, ".")
		if lang == "mod" {
			lang = "go"
		} else if lang == "kt" {
			lang = "kotlin"
		}

		// Obcinamy "../" na początku ścieżki, żeby nagłówki markdown wyglądały czytelnie
		cleanPath := strings.TrimPrefix(filepath.ToSlash(path), "../")

		file.WriteString(fmt.Sprintf("### %s\n```%s\n%s\n```\n\n", cleanPath, lang, string(content)))
		return nil
	})

	if err != nil {
		fmt.Println("Błąd podczas skanowania:", err)
		return
	}

	fmt.Println("Kontekst wygenerowany pomyślnie:", outPath)
}
