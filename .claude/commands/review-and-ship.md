---
name: review-and-ship
description: Führt /pre-merge durch, prüft alles nochmals, erstellt dann Commit und Push – nur wenn alle Checks bestanden.
---

# Review & Ship

Du bist ein strenger Code-Reviewer. Deine Aufgabe: alles prüfen, dann nur bei 100% grüner Ampel committen und pushen.

**STOPP-REGEL:** Sobald ein Check fehlschlägt → Abbruch mit detaillierter Fehlermeldung. Kein Commit, kein Push. Der Nutzer muss den Fehler zuerst beheben.

---

## Phase 1: /pre-merge ausführen

Rufe den `/pre-merge`-Skill auf und arbeite alle Punkte der Checkliste durch:

### 1a. Secrets-Scan (KRITISCH – bei Fund sofort abbrechen)
```bash
git diff main...HEAD -- "*.properties" "*.yml" "*.yaml" "*.env"
git diff main...HEAD | grep -iE "(api_key|password=|token=|secret=|passwd)" | grep -v "test@example\|mustermann\|musterstraße"
```
- Credentials in Properties/YAML-Dateien?
- Sensitive Patterns in Java-/TypeScript-Dateien?
- `application-local.properties` im Diff?
- Echte Nutzerdaten in Tests (echte E-Mails, Namen, Adressen)?
- `uploads/`-Verzeichnis versehentlich versioniert?

**Falls JA bei einem dieser Punkte → SOFORTIGER ABBRUCH.**

### 1b. Backend-Check
```bash
./mvnw clean package -DskipTests 2>&1 | tail -20
./mvnw.cmd test 2>&1 | tail -40
```
- Kompilierung erfolgreich?
- Alle Tests grün?

### 1c. Desktop-Frontend-Check
```bash
cd react-pc-frontend && npm run lint 2>&1 | tail -30
cd react-pc-frontend && npm run build 2>&1 | tail -20
cd react-pc-frontend && npm run test 2>&1 | tail -30
```

### 1d. Mobile-Frontend-Check
```bash
cd react-zeiterfassung && npm run lint 2>&1 | tail -30
cd react-zeiterfassung && npm run build 2>&1 | tail -20
cd react-zeiterfassung && npm run test 2>&1 | tail -30
```

---

## Phase 2: Zweiter unabhängiger Review (Gegenchecks)

Lese alle geänderten Dateien seit dem letzten Commit auf main und prüfe eigenständig:

```bash
git diff main...HEAD --name-only
git diff main...HEAD --stat
```

### 2a. Architektur & Code-Qualität
Für jede geänderte Java-Datei prüfen:
- Enthält der Controller Business-Logik? (Verletzung der Schichtentrennung)
- Werden Entities direkt in REST-Responses zurückgegeben? (DTOs fehlen)
- Gibt es String-Konkatenation in JPQL/SQL statt parametrisierten Queries?
- Field Injection statt Constructor Injection?
- `System.out.println` oder `console.log` im Commit?
- Auskommentierter Code?
- TODOs ohne Ticket-Referenz?

### 2b. Frontend-Qualität
Für jede geänderte TSX/TS-Datei prüfen:
- `dangerouslySetInnerHTML` ohne Sanitizer?
- URL-Parameter ohne `encodeURIComponent()`?
- Farben: indigo/blue statt rose/slate?
- HTML `<select>` statt `Select`-Komponente?
- `<input type="date">` statt `DatePicker`-Komponente?
- `<iframe src={url}>` für PDFs statt `DocumentPreviewModal`?
- Neue Seite ohne Page-Header-Pattern?

### 2c. Flyway-Migrationen
Falls neue SQL-Dateien im Diff:
```bash
git diff main...HEAD --name-only -- "src/main/resources/db/migration/"
```
- Versionsnummer höher als alle bestehenden?
- Bestehende Migrationsdateien verändert? (ABSOLUTES VERBOT)

### 2d. Test-Coverage-Check
- Neue Service-Methoden ohne Tests?
- Neue Controller-Endpoints ohne Happy-Path und Fehlerfall-Test?
- Mindestens 80% Branch-Coverage im Coverage-Report erreicht?
- 100% Branch-Coverage bei kritischen Stellen!!
- Tests mit echten Personendaten?

### 2e. Dokumentations-Check
- Neue UI-Patterns oder Komponenten ohne Update in `.github/DEVELOPMENT.md`?

---

## Phase 3: Entscheidung

### Wenn ALLE Checks bestanden:

**Commit-Nachricht ableiten** aus `git diff main...HEAD --stat` und `git log --oneline -5`:
- Typ bestimmen: `feat` / `fix` / `refactor` / `test` / `docs` / `chore`
- Scope aus betroffenen Modulen ableiten
- Beschreibung: Was wurde geändert und WARUM (nicht nur "was")

Dann ausführen:
```bash
git add -p   # Nur relevante Änderungen – interaktiv prüfen
# ODER wenn git status sauber und alles gewollt:
git status   # Nochmal prüfen was gestaged wird
```

Commit erstellen (HEREDOC-Format):
```bash
git commit -m "$(cat <<'EOF'
<typ>(<scope>): <kurze Beschreibung>

<Ursache/Motivation für die Änderung>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

Dann pushen:
```bash
git push origin HEAD
```

### Wenn ein Check FEHLGESCHLAGEN ist:

**KEIN Commit, KEIN Push.**

Ausgabe im Format:
```
❌ REVIEW FEHLGESCHLAGEN – Kein Commit erstellt

Phase: [1a/1b/1c/1d/2a/2b/2c/2d/2e]
Problem: [Genaue Beschreibung]
Datei(en): [Betroffene Dateien mit Zeilennummern]

Bitte beheben und /review-and-ship erneut ausführen.
```

---

## Phase 4: GitHub-Issues aktualisieren (PFLICHT nach erfolgreichem Push)

Sobald der Push durchgelaufen ist, **MUSST du den `gh-issue-manager`-Agent aufrufen**, damit er die betroffenen GitHub-Issues kommentiert (und auf Rückfrage ggf. schließt).

Nutze dazu das Agent-Tool mit `subagent_type: "gh-issue-manager"` und übergib ihm folgenden Kontext im `prompt`:

- Commit-Hash und Commit-Message des gerade erstellten Commits
- Branch-Name
- Liste der geänderten Dateien (`git diff --name-only HEAD~1`)
- Etwaige Issue-Nummern aus Commit-Message oder Branch-Name (z.B. `#42`)

Beispiel-Prompt an den Agent:
```
Review-and-Ship ist abgeschlossen. Kommentiere die betroffenen GitHub-Issues mit einer
Zusammenfassung der Änderungen.

Commit: <hash>
Branch: <branch>
Message: <commit-message>
Geänderte Dateien:
<liste>

Referenzierte Issues: <#nummern oder "keine gefunden – bitte via gh issue list suchen">

Schließe Issues NICHT eigenständig – nur kommentieren, ausser der User hat den Abschluss
vorher explizit bestätigt.
```

Erst **nach** dem Agent-Aufruf den Abschlussbericht ausgeben.

---

## Abschlussbericht

Nach erfolgreichem Commit, Push und Issue-Update:
```
✅ SHIPPED

Commit: <hash>
Branch: <branch>
Geprüfte Checks: [Liste aller bestandenen Checks]
Push: origin/<branch>
Issues aktualisiert: [Liste der kommentierten Issue-Nummern oder "keine gefunden"]
```
