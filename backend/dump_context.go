package main

import (
	"database/sql"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

var (
	reMultipleNewlines = regexp.MustCompile(`\n{3,}`)
	reXMLComments      = regexp.MustCompile(`<!--[\s\S]*?-->`)
)

func cleanCodeContent(raw string, ext string) string {
	cleaned := raw
	if ext == ".xml" {
		cleaned = reXMLComments.ReplaceAllString(cleaned, "")
	}
	// Usunięcie nadmiarowych pustych linii
	cleaned = reMultipleNewlines.ReplaceAllString(cleaned, "\n\n")
	return strings.TrimSpace(cleaned)
}

func dumpSchemaToString(dbPath string) string {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		return ""
	}
	defer db.Close()

	rows, err := db.Query(`SELECT sql FROM sqlite_master WHERE type = 'table' AND sql IS NOT NULL ORDER BY name`)
	if err != nil {
		return ""
	}
	defer rows.Close()

	var sb strings.Builder
	sb.WriteString("### Database Schema\n```sql\n")
	for rows.Next() {
		var stmt string
		if err := rows.Scan(&stmt); err == nil {
			sb.WriteString(stmt + ";\n\n")
		}
	}
	sb.WriteString("```\n\n")
	return sb.String()
}

func scanDirectory(rootPath string) (string, error) {
	// 1. Foldery całkowicie ignorowane
	ignoreDirs := map[string]bool{
		".git": true, ".idea": true, ".llm_context": true,
		"database": true, "bin": true, "build": true, ".gradle": true,
		"androidTest": true, "test": true, // Pomija domyślne testy
	}

	// 2. Dozwolone rozszerzenia
	validExts := map[string]bool{
		".go": true, ".html": true, ".kt": true,
		".xml": true, ".sql": true, ".mod": true,
	}

	var sb strings.Builder

	err := filepath.WalkDir(rootPath, func(path string, d os.DirEntry, err error) error {
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

		cleanPath := filepath.ToSlash(path)
		cleanPath = strings.TrimPrefix(cleanPath, "../")

		// 3. Ignorowanie grafiki wektorowej XML i szablonów ikon
		if ext == ".xml" {
			if strings.Contains(cleanPath, "drawable") || strings.Contains(cleanPath, "mipmap") || strings.Contains(cleanPath, "xml/") {
				if !strings.HasSuffix(cleanPath, "AndroidManifest.xml") {
					return nil
				}
			}
		}

		contentBytes, err := os.ReadFile(path)
		if err != nil {
			return nil
		}

		sanitizedContent := cleanCodeContent(string(contentBytes), ext)

		lang := strings.TrimPrefix(ext, ".")
		if lang == "mod" {
			lang = "go"
		} else if lang == "kt" {
			lang = "kotlin"
		}

		sb.WriteString(fmt.Sprintf("### %s\n```%s\n%s\n```\n\n", cleanPath, lang, sanitizedContent))
		return nil
	})

	return sb.String(), err
}

func main() {
	androidFlag := flag.Bool("android", false, "Zrzuca tylko moduł Android")
	backendFlag := flag.Bool("backend", false, "Zrzuca tylko moduł Backend")
	noDbFlag := flag.Bool("no-db", false, "Pomija schemat bazy danych")
	separateFlag := flag.Bool("separate", false, "Zapisuje sekcje do osobnych plików .md")
	flag.Parse()

	dbPath := "database/foss.db"
	outDir := "../.llm_context"
	_ = os.MkdirAll(outDir, os.ModePerm)
	timestamp := time.Now().Format("2006-01-02_15-04-05")

	dumpAll := !*androidFlag && !*backendFlag
	includeAndroid := dumpAll || *androidFlag
	includeBackend := dumpAll || *backendFlag
	includeDB := !*noDbFlag

	if *separateFlag {
		if includeDB {
			dbContent := dumpSchemaToString(dbPath)
			path := filepath.Join(outDir, fmt.Sprintf("schema_%s.md", timestamp))
			_ = os.WriteFile(path, []byte("# F.O.S.S. - Database Schema\n\n"+dbContent), 0644)
		}
		if includeBackend {
			content, _ := scanDirectory(".")
			path := filepath.Join(outDir, fmt.Sprintf("backend_%s.md", timestamp))
			_ = os.WriteFile(path, []byte("# F.O.S.S. - Backend Context\n\n"+content), 0644)
		}
		if includeAndroid {
			content, _ := scanDirectory("../android")
			path := filepath.Join(outDir, fmt.Sprintf("android_%s.md", timestamp))
			_ = os.WriteFile(path, []byte("# F.O.S.S. - Android Context\n\n"+content), 0644)
		}
	} else {
		var combined strings.Builder
		combined.WriteString("# F.O.S.S. - Kontekst Projektu\n\n")

		if includeDB {
			combined.WriteString(dumpSchemaToString(dbPath))
		}
		if includeBackend {
			backendContent, _ := scanDirectory(".")
			combined.WriteString(backendContent)
		}
		if includeAndroid {
			androidContent, _ := scanDirectory("../android")
			combined.WriteString(androidContent)
		}

		suffix := "full"
		if *androidFlag && !*backendFlag {
			suffix = "android"
		} else if *backendFlag && !*androidFlag {
			suffix = "backend"
		}

		outPath := filepath.Join(outDir, fmt.Sprintf("context_%s_%s.md", suffix, timestamp))
		_ = os.WriteFile(outPath, []byte(combined.String()), 0644)
		fmt.Printf("Wygenerowano zoptymalizowany plik: %s\n", outPath)
	}
}
