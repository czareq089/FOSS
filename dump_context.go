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

const dbPath = "backend/database/foss.db"

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
	outDir := ".llm_context"
	// Tworzy ukryty folder, jeśli nie istnieje
	_ = os.MkdirAll(outDir, os.ModePerm)

	// Zawsze poprawna, uniwersalna data i godzina
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
	// Czarna lista folderów
	ignoreDirs := map[string]bool{
		".git": true, ".idea": true, ".llm_context": true,
		"database": true, "bin": true, "build": true, ".gradle": true,
	}

	// Biała lista rozszerzeń plików
	validExts := map[string]bool{
		".go": true, ".html": true, ".kt": true,
		".xml": true, ".sql": true, ".mod": true,
	}

	fmt.Println("Skanowanie projektu...")

	err = filepath.WalkDir(".", func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return nil
		}

		// Pomijanie całych drzew niechcianych katalogów
		if d.IsDir() {
			if ignoreDirs[d.Name()] {
				return filepath.SkipDir
			}
			return nil
		}

		// Filtrowanie po rozszerzeniach
		ext := filepath.Ext(path)
		if !validExts[ext] {
			return nil
		}

		// Wczytywanie zawartości
		content, err := os.ReadFile(path)
		if err != nil {
			return nil
		}

		// Formatowanie pod kolorowanie składni w Markdown
		lang := strings.TrimPrefix(ext, ".")
		if lang == "mod" {
			lang = "go"
		} else if lang == "kt" {
			lang = "kotlin"
		}

		// Zapis do pliku MD
		file.WriteString(fmt.Sprintf("### %s\n```%s\n%s\n```\n\n", filepath.ToSlash(path), lang, string(content)))
		return nil
	})

	if err != nil {
		fmt.Println("Błąd podczas skanowania:", err)
		return
	}

	fmt.Println("Kontekst wygenerowany pomyślnie:", outPath)
}
