package main

import (
	"archive/zip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

const projectRoot = `C:\Users\vstkxj\GolandProjects\FOSS`

type zipCandidate struct {
	path    string
	modTime time.Time
	name    string
}

func main() {
	downloadsDir, err := getDownloadsDir()
	if err != nil {
		fatal(err)
	}

	zipPath, err := findNewestZip(downloadsDir)
	if err != nil {
		fatal(err)
	}

	if _, err := os.Stat(projectRoot); err != nil {
		fatal(fmt.Errorf("katalog projektu nie istnieje: %s: %w", projectRoot, err))
	}

	backupRoot := filepath.Join(
		projectRoot,
		".change_files_backup",
		time.Now().Format("2006-01-02_15-04-05"),
	)

	fmt.Println("Wybrany ZIP:")
	fmt.Println(" ", zipPath)
	fmt.Println()
	fmt.Println("Projekt:")
	fmt.Println(" ", projectRoot)
	fmt.Println()
	fmt.Println("Mapowanie:")
	fmt.Println(`  cmd\...      -> backend\cmd\...`)
	fmt.Println(`  android\...  -> android\...`)
	fmt.Println()

	changed, backedUp, skipped, err := applyZip(zipPath, projectRoot, backupRoot)
	if err != nil {
		fatal(err)
	}

	fmt.Println()
	fmt.Printf("Gotowe. Podmieniono plików: %d\n", changed)
	fmt.Printf("Pominięto plików: %d\n", skipped)

	if backedUp > 0 {
		fmt.Printf("Backup utworzono dla %d plików:\n", backedUp)
		fmt.Println(" ", backupRoot)
	}
}

func getDownloadsDir() (string, error) {
	userProfile := os.Getenv("USERPROFILE")
	if userProfile == "" {
		home, err := os.UserHomeDir()
		if err != nil {
			return "", fmt.Errorf("nie udało się ustalić katalogu użytkownika: %w", err)
		}
		userProfile = home
	}

	downloads := filepath.Join(userProfile, "Downloads")
	info, err := os.Stat(downloads)
	if err != nil {
		return "", fmt.Errorf("nie znaleziono katalogu Downloads: %s: %w", downloads, err)
	}
	if !info.IsDir() {
		return "", fmt.Errorf("%s nie jest katalogiem", downloads)
	}

	return downloads, nil
}

func findNewestZip(downloadsDir string) (string, error) {
	entries, err := os.ReadDir(downloadsDir)
	if err != nil {
		return "", fmt.Errorf("nie udało się odczytać Downloads: %w", err)
	}

	var preferred []zipCandidate
	var allZips []zipCandidate

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}

		name := entry.Name()
		if !strings.EqualFold(filepath.Ext(name), ".zip") {
			continue
		}

		info, err := entry.Info()
		if err != nil {
			continue
		}

		candidate := zipCandidate{
			path:    filepath.Join(downloadsDir, name),
			modTime: info.ModTime(),
			name:    name,
		}

		allZips = append(allZips, candidate)

		if strings.HasPrefix(strings.ToLower(name), "foss_changed_files") {
			preferred = append(preferred, candidate)
		}
	}

	candidates := preferred
	if len(candidates) == 0 {
		candidates = allZips
	}

	if len(candidates) == 0 {
		return "", fmt.Errorf("w %s nie znaleziono żadnego ZIP-a", downloadsDir)
	}

	sort.Slice(candidates, func(i, j int) bool {
		return candidates[i].modTime.After(candidates[j].modTime)
	})

	return candidates[0].path, nil
}

// mapArchivePath odwzorowuje strukturę paczki ChatGPT na rzeczywistą strukturę repo FOSS.
//
// ZIP:
//
//	cmd/api_handlers.go
//	android/app/...
//
// Repo:
//
//	backend/cmd/api_handlers.go
//	android/app/...
//
// Nieznane katalogi główne są odrzucane, żeby skrypt przypadkiem
// nie tworzył nowych folderów w root projektu.
func mapArchivePath(zipName string) (string, error) {
	name := strings.ReplaceAll(zipName, `\`, `/`)
	name = strings.TrimPrefix(name, "/")

	cleanSlash := filepath.ToSlash(filepath.Clean(filepath.FromSlash(name)))

	if cleanSlash == "." || cleanSlash == "" {
		return "", fmt.Errorf("pusta ścieżka")
	}
	if cleanSlash == ".." || strings.HasPrefix(cleanSlash, "../") {
		return "", fmt.Errorf("niebezpieczna ścieżka: %q", zipName)
	}

	switch {
	case cleanSlash == "cmd":
		return "backend/cmd", nil

	case strings.HasPrefix(cleanSlash, "cmd/"):
		return "backend/" + cleanSlash, nil

	case cleanSlash == "android":
		return "android", nil

	case strings.HasPrefix(cleanSlash, "android/"):
		return cleanSlash, nil

	// Jeżeli przyszłe paczki będą już zawierały poprawny prefix backend/,
	// również je obsłużymy.
	case cleanSlash == "backend":
		return "backend", nil

	case strings.HasPrefix(cleanSlash, "backend/"):
		return cleanSlash, nil

	default:
		return "", fmt.Errorf(
			"nieznana ścieżka w ZIP-ie %q; dozwolone rooty: cmd/, backend/, android/",
			zipName,
		)
	}
}

func applyZip(zipPath, destinationRoot, backupRoot string) (changed, backedUp, skipped int, err error) {
	reader, err := zip.OpenReader(zipPath)
	if err != nil {
		return 0, 0, 0, fmt.Errorf("nie udało się otworzyć ZIP-a %s: %w", zipPath, err)
	}
	defer reader.Close()

	rootAbs, err := filepath.Abs(destinationRoot)
	if err != nil {
		return 0, 0, 0, err
	}

	// Najpierw walidujemy całą paczkę, zanim podmienimy choć jeden plik.
	type mappedFile struct {
		file     *zip.File
		relative string
		target   string
	}

	var files []mappedFile

	for _, zf := range reader.File {
		if zf.FileInfo().IsDir() {
			continue
		}

		relative, mapErr := mapArchivePath(zf.Name)
		if mapErr != nil {
			return 0, 0, 0, mapErr
		}

		relative = filepath.Clean(filepath.FromSlash(relative))
		target := filepath.Join(rootAbs, relative)

		targetAbs, absErr := filepath.Abs(target)
		if absErr != nil {
			return 0, 0, 0, absErr
		}

		if !isInsideRoot(rootAbs, targetAbs) {
			return 0, 0, 0, fmt.Errorf("ścieżka wychodzi poza repo: %q", zf.Name)
		}

		// Ważne: rodzic pliku musi już należeć do istniejącej struktury projektu.
		// Nie pozwalamy stworzyć przypadkowego nowego top-level directory.
		parent := filepath.Dir(targetAbs)
		if _, statErr := os.Stat(parent); statErr != nil {
			if os.IsNotExist(statErr) {
				return 0, 0, 0, fmt.Errorf(
					"katalog docelowy nie istnieje:\n  ZIP: %s\n  CEL: %s\n\n"+
						"Skrypt zatrzymał się zamiast tworzyć błędną strukturę.",
					zf.Name,
					parent,
				)
			}
			return 0, 0, 0, statErr
		}

		files = append(files, mappedFile{
			file:     zf,
			relative: relative,
			target:   targetAbs,
		})
	}

	fmt.Println("Pliki do podmiany:")
	for _, item := range files {
		fmt.Printf("  %-65s -> %s\n", item.file.Name, item.relative)
	}
	fmt.Println()

	for _, item := range files {
		info, statErr := os.Stat(item.target)

		if statErr == nil && !info.IsDir() {
			backupPath := filepath.Join(backupRoot, item.relative)

			if err := copyFile(item.target, backupPath, info.Mode()); err != nil {
				return changed, backedUp, skipped, fmt.Errorf(
					"backup %s nie powiódł się: %w",
					item.target,
					err,
				)
			}
			backedUp++
		} else if os.IsNotExist(statErr) {
			// Nowy plik jest dozwolony tylko w istniejącym katalogu.
			fmt.Printf("NOWY:      %s\n", item.relative)
		} else if statErr != nil {
			return changed, backedUp, skipped, statErr
		}

		if err := extractFile(item.file, item.target); err != nil {
			return changed, backedUp, skipped, err
		}

		fmt.Printf("PODMIENIONO: %s\n", item.relative)
		changed++
	}

	if changed == 0 {
		return 0, backedUp, skipped, fmt.Errorf("ZIP nie zawiera plików do podmiany")
	}

	return changed, backedUp, skipped, nil
}

func isInsideRoot(root, path string) bool {
	rel, err := filepath.Rel(root, path)
	if err != nil {
		return false
	}

	return rel != ".." &&
		!strings.HasPrefix(rel, ".."+string(os.PathSeparator)) &&
		!filepath.IsAbs(rel)
}

func extractFile(zf *zip.File, targetPath string) error {
	src, err := zf.Open()
	if err != nil {
		return fmt.Errorf("nie udało się otworzyć %s z ZIP-a: %w", zf.Name, err)
	}
	defer src.Close()

	mode := zf.Mode()
	if mode == 0 {
		mode = 0644
	}

	dst, err := os.OpenFile(targetPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		return fmt.Errorf("nie udało się otworzyć %s: %w", targetPath, err)
	}

	_, copyErr := io.Copy(dst, src)
	closeErr := dst.Close()

	if copyErr != nil {
		return fmt.Errorf("błąd kopiowania do %s: %w", targetPath, copyErr)
	}
	if closeErr != nil {
		return fmt.Errorf("błąd zamykania %s: %w", targetPath, closeErr)
	}

	return nil
}

func copyFile(source, destination string, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(destination), 0755); err != nil {
		return err
	}

	src, err := os.Open(source)
	if err != nil {
		return err
	}
	defer src.Close()

	dst, err := os.OpenFile(destination, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		return err
	}

	_, copyErr := io.Copy(dst, src)
	closeErr := dst.Close()

	if copyErr != nil {
		return copyErr
	}
	return closeErr
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr)
	fmt.Fprintln(os.Stderr, "BŁĄD:", err)
	os.Exit(1)
}
