---
name: fullstack-parallel
description: Standard-Ablauf für JEDE Implementierung im ERP (Feature, Umbau, Redesign, Erweiterung) – Pflicht laut CLAUDE.md seit 31.10.2026. Die Hauptsession ist Orchestrator: kurzes Brainstorming mit dem Nutzer, API-Vertrag festlegen, dann Backend-Agent und Frontend-Agent parallel per Subagent bauen lassen, toten Code löschen, erst wenn alles fertig ist EIN gemeinsames /review-and-ship, PR bis zum Merge begleiten, aufräumen (Server, Container, Issues, Branches). Betrifft die Aufgabe nur Backend oder nur Frontend, läuft nur der passende Agent, alle übrigen Schritte gelten. Auch explizit über „/fullstack-parallel“. Andere Abläufe (/bugfix, /loese-problem) nur, wenn der Nutzer sie ausdrücklich aufruft.
---

# fullstack-parallel – du orchestrierst, Backend- und Frontend-Agent bauen parallel, Review am Schluss

**Gilt für jede Implementierung** (Nutzervorgabe 31.10.2026, verankert in `.claude/CLAUDE.md`).
Betrifft die Aufgabe nur eine Seite, läuft nur der passende Agent:

- **Nur Backend:** Schritte 0–2, 3 nur mit Backend-Agent (3a), Toten Code löschen, 5–8; kein
  API-Vertrag für ein neues Frontend, aber bestehende Aufrufer im Frontend greppen, damit nichts bricht.
- **Nur Frontend:** Schritte 0–2, 3 nur mit Frontend-Agent (3b), Toten Code löschen, 5–8; den
  bestehenden API-Vertrag aus den Java-DTOs/Controllern lesen statt neu festlegen.
- Kleine Korrekturen innerhalb einer laufenden Aufgabe brauchen keinen neuen Durchlauf.

**Ankündigen:** „Ich nutze den fullstack-parallel-Skill: kurzes Brainstorming, dann bauen Backend- und Frontend-Agent parallel, ich koordiniere und prüfe am Schluss.“

**Rollen (feste Aufteilung):**

| Wer | Was | Liest | Sicherheit |
| --- | --- | --- | --- |
| **Orchestrator (du, Hauptsession)** | Brainstorming, API-Vertrag, Briefing beider Agenten, Info-Button-Texte, Gegenproben, Review, Commit, PR. **Schreibt selbst keinen Produktivcode.** | `FRONTEND_UI.md` + Skill `handwerkerprogramm-design` und `BACKEND_ARCH.md` – du briefst beide Seiten und musst ihre Regeln kennen | prüft im Briefing, dass jede Seite ihre Sicherheitssicht mitbekommt |
| **Backend-Agent** (`erp-backend`, Hintergrund) | Service, Controller, Flyway, Backend-Tests | `BACKEND_ARCH.md` (inkl. „Sicherheit im Backend“), `TESTING_SECURITY.md` | nur Backend-Sicherheitssicht |
| **Frontend-Agent** (`erp-frontend`, Hintergrund) | Oberflächen in `react-pc-frontend` / `react-zeiterfassung`, Frontend-Tests, Playwright-Spec | `FRONTEND_UI.md` (inkl. „Sicherheit im Frontend“), Skill `handwerkerprogramm-design` | nur Frontend-Sicherheitssicht |
| **Reviewer** (`erp-code-reviewer`, einmal am Ende) | prüft **beide** Seiten, Findings getrennt nach `[BACKEND]` und `[FRONTEND]` | alle drei Docs + Skill | beide Sichten |

Warum dieser Ablauf: Backend und Frontend hängen nur über die API zusammen. Steht der
Vertrag (Endpoints, Parameter, JSON-Felder, Fehlercodes) einmal fest, können beide Seiten
gleichzeitig entstehen – das halbiert die Wartezeit. Jede Seite baut ein Experte, der nur
die Regeln seines Gebiets im Kopf hat; du hältst den Überblick, sprichst mit dem Nutzer
und sorgst dafür, dass beide Hälften zusammenpassen. Ein Review über halbfertigen Code
kostet dagegen eine Runde und findet Dinge, die sich ohnehin noch ändern. Deshalb:
parallel bauen, aber **einmal** am Ende prüfen.

## 0. Leitlinien beider Seiten lesen

Bevor du dem Nutzer etwas vorschlägst, lädst du die Regeln beider Seiten – du briefst
beide Agenten und musst wissen, was ihr Gebiet verlangt (Pflicht-Komponenten,
Paginierung, Info-Buttons, Größenregeln, Sicherheitssichten):

- `Read` → `docs/agent instructions/docs/FRONTEND_UI.md`
- `Skill` → `handwerkerprogramm-design` (vollständig lesen)
- `Read` → `docs/agent instructions/docs/BACKEND_ARCH.md`

`TESTING_SECURITY.md` brauchst du nur, wenn du selbst Tests anfasst.

## 1. Kurzes Brainstorming (Minuten, nicht Stunden)

Erst den betroffenen Code lesen (Grep/Read, bei breiten Fragen `scripts/graphify`), dann
dem Nutzer **einen konkreten Vorschlag** machen statt offener Fragen:

- Was genau gebaut wird, in Handwerker-Sprache (welcher Knopf, wo, was passiert).
- Was es schon gibt und wiederverwendet/verallgemeinert wird.
- Höchstens 2–3 echte Entscheidungsfragen, nur wenn die Antwort den Bau ändert.
- **Info-Punkte planen:** Welche Stellen sind erklärungsbedürftig (etwas passiert im
  Hintergrund, eine Einstellung wirkt auf andere Bereiche, Folgen sind nicht
  rückgängig zu machen, Fachbegriff)? Diese Stellen im Vorschlag nennen – sie bekommen
  einen Info-Button (siehe Abschnitt „Info-Buttons“).
- **Auslagerung/Refactoring** (Komponente, Hook, Service verallgemeinern) im Vorschlag
  mit nennen, damit klar ist, was sich mitändert – eine eigene Freigabe braucht es nicht
  (dauerhafte Nutzerfreigabe, siehe CLAUDE.md „Auslagern und wiederverwenden“).

Erst nach dem „ja passt“ weiter. Ist der Weg schon völlig klar und vom Nutzer
vorgegeben, genügt ein Satz Zusammenfassung.

## 2. API-Vertrag festlegen

Vor dem Aufteilen den Vertrag schriftlich festhalten – er ist die einzige Abhängigkeit
zwischen beiden Hälften:

- Methode + Pfad, Query-/Body-Parameter mit Typen und Pflichtfeldern
- Antwort-JSON Feld für Feld (Namen exakt, `null`-Fälle)
- Bei Listen: `page`/`size`, Standard- und Höchstgröße, Sortierung, `gesamt` bzw. `hatMehr`
- Statuscodes: 200, 400 (mit `{message}` in Handwerker-Sprache), 401, 403, 404
- Welche alten Endpoints bleiben (die das Frontend weiter nutzt)

Bestehende DTOs wiederverwenden, wo die Felder passen (z. B. `DokumentRef`) – dann muss
das Frontend keine neuen Typen erfinden.

## 3. Beide Agenten starten (Hintergrund, in einer Nachricht)

Backend- und Frontend-Agent **in derselben Nachricht** starten, damit sie wirklich
gleichzeitig laufen: zwei `Agent`-Aufrufe mit `subagent_type: "erp-backend"` bzw.
`"erp-frontend"`, `run_in_background: true`, beide **im selben Working Tree** (kein
Worktree – die Dateien überschneiden sich nicht, und ein späteres Zusammenführen
entfällt). Jeder Prompt muss allein tragen; die Agenten kennen das Gespräch nicht.

### 3a. Briefing Backend-Agent

1. **Pflichtlektüre:** steht in der Agent-Definition `erp-backend` (`BACKEND_ARCH.md`, `TESTING_SECURITY.md`). Im Prompt nur kurz daran erinnern. Frontend-Dokumente bekommt er bewusst nicht.
2. **Ziel aus Nutzersicht** inkl. eines konkreten Beispiels aus dem Gespräch.
3. **Bestehende Basis** mit Pfaden (Services, Controller, Entities, DTOs, Nachbar-Endpoints, deren Rechteprüfung er übernimmt, vorhandene Tests).
4. **Der API-Vertrag aus Schritt 2** wörtlich, inkl. Paginierung und 400-Meldungstexten.
5. **Fachliche Regeln** aus dem Brainstorming (was passiert im Hintergrund, was ändert sich nicht, Grenzfälle) – daraus entstehen auch die Info-Button-Texte, die du dem Frontend-Agenten gibst.
6. **Grenzen:** nur Backend-Dateien, Frontend nicht anfassen, **nicht committen**.
7. **Bericht:** wie in der Agent-Definition, vor allem jede Abweichung vom Vertrag und das tatsächliche Verhalten für die Info-Texte.

### 3b. Briefing Frontend-Agent

Hinein gehört:

1. **Pflichtlektüre:** steht in der Agent-Definition `erp-frontend` (`FRONTEND_UI.md`, Skill `handwerkerprogramm-design` samt `PRUEFUNG.md`, `TESTING_SECURITY.md`). Im Prompt nur kurz daran erinnern: „Lies zuerst deine Pflichtlektüre, sonst blockt der Edit-Hook.“ Backend-Dokumente bekommt er bewusst nicht.
2. **Ziel aus Nutzersicht** inkl. eines konkreten Beispiels aus dem Gespräch.
3. **Bestehende Basis** mit Pfaden und ungefähren Zeilen (Komponenten, Hilfsdateien, Tests).
4. **Der API-Vertrag aus Schritt 2** wörtlich.
5. **Umsetzungsschritte** und wo neue Knöpfe hinkommen.
6. **Info-Buttons mit fertigen Texten:** Für jede erklärungsbedürftige Stelle aus dem
   Brainstorming den Ort (neben welchem Feld/welcher Überschrift) und den Inhalt
   vorgeben – „Was passiert im Hintergrund?“ und „Was bewirkt das?“. Du kennst das
   Backend-Verhalten aus Brainstorming und Vertrag, der Frontend-Agent nicht; deshalb
   schreibst **du** die Texte.
   Baustein: `react-pc-frontend/src/components/ui/info-button.tsx` (Regeln unten).
7. **Tests:** Vitest + Testing Library, bestehende grün halten, E2E unter
   `react-pc-frontend/e2e/` mit `page.route`-Mocks; `npm run lint`, `npm run test -- --run`,
   `npm run build`; Playwright-Design-Prüfung nach `PRUEFUNG.md` (im Skill `handwerkerprogramm-design`). Den Abschnitt
   „Schnelle E2E-Tests“ unten wörtlich in den Prompt übernehmen.
8. **Grenzen:** nur Dateien unter `react-pc-frontend/` (bzw. `react-zeiterfassung/`),
   `src/main/java` nicht anfassen, **nicht committen**.
9. **Toten Code löschen:** den Abschnitt „Toten Code immer löschen“ unten wörtlich in den
   Prompt übernehmen.
10. **Bericht:** vollständige Liste geänderter/neuer/gelöschter Dateien, Testzahlen,
   Lint/Build-Status, alle Info-Button-Stellen, gelöschter toter Code, offene Punkte.

Ändert sich der Vertrag später (z. B. weil der Backend-Agent eine Abweichung meldet),
**beiden** laufenden Agenten per `SendMessage` Bescheid geben – nicht neue starten.

### Info-Buttons (Wunsch des Nutzers, gilt für jedes Feature)

Der Nutzer möchte bei komplexeren Dingen immer einen Info-Button, der genauer erklärt,
**was im Hintergrund passiert** und **welche Auswirkungen** etwas hat. Handwerker sollen
eine Einstellung verstehen, bevor sie sie ändern – ohne Handbuch.

- **Wann:** Einstellungen mit Folgen für andere Bereiche (z. B. Export, Lohn, Glocke,
  Rechte), Aktionen, die etwas im Hintergrund auslösen oder nicht rückgängig zu machen
  sind, und unvermeidbare Fachbegriffe (Lohnart, Personalnummer, Soll/Ist). Nicht bei
  selbsterklärenden Feldern wie „Name“ oder „Telefon“ – zu viele (i) machen die Seite
  unruhig.
- **Wo:** direkt neben dem Feld bzw. der Überschrift, auf die sich die Info bezieht –
  nie gesammelt am Seitenende oder in einer separaten Hilfeseite.
- **Inhalt:** zwei kurze Teile in Handwerker-Sprache – „Was passiert im Hintergrund?“
  und „Was bewirkt das?“ (inkl. was sich *nicht* ändert, z. B. „bereits abgeschlossene
  Monate bleiben unverändert“). Wenige Sätze, keine internen Begriffe (Endpoint, Cache,
  Tabelle). Texte müssen zum tatsächlichen Backend-Verhalten passen.
- **Baustein:** immer den gemeinsamen `InfoButton` aus
  `react-pc-frontend/src/components/ui/info-button.tsx` nutzen (Klick öffnet ein
  Popover, Tastatur + Touch, Escape schließt) – nicht pro Seite neu bauen. Fehlt in
  `react-zeiterfassung/` ein Gegenstück, das mit zur Freigabe stellen.

### Schnelle E2E-Tests (Wunsch des Nutzers vom 11.10.2026, gilt für jede neue Spec)

Die PC-Suite hat rund 1000 Testläufe, weil jede Spec in drei Bildschirmgrößen läuft.
Jede Sekunde in einem Test kostet also drei. Damit sie schnell **und** verlässlich
bleibt:

- **Richtige Ebene wählen.** Logik, Formatierung, Validierung und die Bedienung
  einer einzelnen Komponente (Tastatur, ARIA, Pflichtfeld) gehören in Vitest +
  Testing Library neben die Datei (`*.test.tsx`). Das dauert Millisekunden statt
  Sekunden. Playwright nur für echte Abläufe über Seiten und Dialoge und für das
  Layout in den drei Größen.
- **Gegen den Build testen, nicht gegen Dev-Server-Interna.** Die E2E-Tests laufen
  gegen den Produktions-Build (`vite preview`). Importe wie
  `/node_modules/.vite/deps/…` oder `/src/…` per `page.evaluate` gibt es dort nicht.
  Wer eine Komponente isoliert mounten will, schreibt einen Vitest-Test.
- **Ein Ablauf pro Test, nicht eine Zusicherung pro Test.** Jeder `page.goto` lädt
  die App neu. Mehrere Prüfungen am selben Seitenzustand gehören in einen Test.
- **Auf Zustände warten, nie auf die Uhr.** Kein `page.waitForTimeout(…)` als
  „gleich fertig“: zu kurz wackelt der Test, zu lang bremst er jeden Lauf. Besser
  `await expect(…).toBeVisible()`, `expect.poll(…)` oder `page.waitForResponse(…)`.
- **Erst die Daten, dann die Aktion.** Der Build ist schnell genug, um zu klicken,
  bevor nachgeladene Daten da sind. Am besten auf etwas Sichtbares warten, das erst
  mit den Daten erscheint (die Option in der Liste, die Zeile in der Tabelle). Gibt
  es das nicht, `page.waitForResponse` auf genau diese Anfrage. Und nicht auf
  Automatiken bauen, um die es im Test gar nicht geht. Beispiel: Der Upload-Dialog
  ordnet den Lieferanten nur zu, wenn seine Liste schneller lädt als die Analyse,
  darum wählt `e2e/systemeingaben-task-10.spec.ts` ihn ausdrücklich aus.
- **Animationen nicht selbst abwarten.** Vor Screenshots und Messungen
  `designPruefung` bzw. `uebergaengeAusklingenLassen` aus `e2e/hilfen/design.ts`
  nutzen, keine eigenen Pausen.
- **Wackel-Probe vor dem Abgeben:** die eigene Spec mit
  `E2E_PORT=<port> npx playwright test e2e/<spec> --repeat-each=5 --workers=1`.
  Schnelle Einzelläufe decken Wettläufe auf, die unter Last verborgen bleiben.
  (PowerShell: `$env:E2E_PORT=<port>; npx playwright test …`)

### Toten Code immer löschen (Nutzervorgabe 31.10.2026, gilt für Backend und Frontend)

Was durch die Änderung niemand mehr benutzt, wird im selben PR gelöscht – nicht
auskommentiert, nicht „für später“ stehen gelassen, nicht mit `@Deprecated` markiert.

- **Was:** Endpoints, die kein Frontend (PC, Zeiterfassung) und kein anderer Dienst mehr
  aufruft; Services/Methoden/DTOs/Repository-Abfragen ohne Aufrufer; Komponenten, Hooks,
  Hilfsfunktionen, Typen, CSS-Klassen und Konstanten ohne Verwendung; doppelt gepflegte
  Listen, die durch eine gemeinsame Quelle ersetzt wurden; Tests, die nur toten Code prüfen.
- **Wie prüfen:** vor dem Löschen greppen – Backend über `src/main/java` und `src/test/java`,
  Frontend über `react-pc-frontend/src`, `react-zeiterfassung/src` und `e2e/`; bei
  Endpoints zusätzlich nach dem Pfad suchen (auch Skripte, `deployment/`, `MobileApiPolicy`,
  `LocalRagService.ROUTE_TO_FILE_KEYWORDS`). Bei breiter Wirkung `scripts/graphify affected "X"`.
- **Grenze:** nur, was durch die eigene Änderung tot wurde oder im angefassten Bereich
  offensichtlich tot ist. Größere Aufräumaktionen in fremdem Bestand dem Nutzer vorschlagen.
- **Im Bericht/PR nennen**, was gelöscht wurde, damit der Reviewer es gegenprüfen kann.
- **In beide Briefings** (3a und 3b) diesen Abschnitt wörtlich übernehmen.

## 4. Währenddessen: koordinieren, nicht selbst bauen

Du schreibst keinen Backend- oder Frontend-Code, solange die Agenten laufen – sonst
überschreibt ihr euch gegenseitig, und die Aufteilung nach Experten geht verloren.

- Rückfragen und Meldungen der Agenten beantworten; betrifft eine den Vertrag, beide
  Agenten informieren (siehe oben).
- Meldet ein Agent eine zu große Bestandsklasse oder -datei, **dem Nutzer den
  Auslagerungsvorschlag vorlegen und fragen** und die Antwort an den Agenten weitergeben.
- Fachliche Fragen, die nur der Nutzer beantworten kann, sofort an ihn weiterreichen.
- Nicht pollen: die Benachrichtigungen kommen von selbst.

## 5. Warten, bis beides fertig ist

Kein Review, kein Commit, solange einer der beiden Agenten läuft oder Tests offen sind –
auch wenn der Stop-Hook „uncommitted changes“ meldet. Dem Nutzer kurz sagen, worauf
gewartet wird. Nicht pollen: die Benachrichtigungen kommen von selbst.

Wenn beide Berichte da sind:

- Gegenprobe Vertrag: Endpoints und Feldnamen im Frontend greppen und mit den Java-DTOs
  und Controllern vergleichen (häufigster Fehler: ein umbenanntes Feld).
- Gegenprobe Info-Buttons: Sind alle geplanten Stellen da, und stimmen die Texte noch
  mit dem Verhalten überein, das der Backend-Agent berichtet (Details ändern sich beim
  Bauen oft)? Abweichungen per `SendMessage` an den Frontend-Agenten.
- Gegenprobe toter Code: Endpoints und Bausteine, die das neue Frontend nicht mehr nutzt,
  greppen und vom zuständigen Agenten löschen lassen (siehe „Toten Code immer löschen“).
- Beide Seiten selbst einmal laufen lassen: `./mvnw -q compile` + Backend-Tests,
  Frontend `lint`, `test -- --run`, `build`.
- Rote Tests, die nichts mit der Änderung zu tun haben: mehrfach einzeln laufen lassen,
  als unzuverlässig benennen statt still zu ignorieren.

## 6. Ein gemeinsames /review-and-ship

Jetzt `Skill` → `review-and-ship` über den **gesamten** Diff (Backend + Frontend). Der Reviewer prüft beide Seiten, jede gegen ihre eigene Sicherheitssicht und ihre Checkliste. Dort
gelten dessen Regeln: ein Reviewer-Subagent, höchstens 2 Runden, nur eigene Dateien
stagen (die Listen aus den Berichten beider Agenten), Build-Artefakte
aus `npm run build` dürfen mit.

**Findings einarbeiten lassen:** `[BACKEND]`-Findings per `SendMessage` an den
Backend-Agenten, `[FRONTEND]`-Findings an den Frontend-Agenten – sie haben den Kontext
und kennen die Regeln ihres Gebiets. Du prüfst danach Build und Tests und startest die
zweite Review-Runde. Nur bei reinen Kleinigkeiten (Tippfehler, ein Import) darfst du
selbst korrigieren.

## 7. Pull Request bis zum Merge begleiten (Nutzervorgabe 11.10.2026, immer)

`main` nimmt nur Pull Requests an. PR anlegen und Checks beobachten läuft nach
`review-and-ship` Phase 4. Bei diesem Skill zusätzlich, **ohne erneute Rückfrage**:

1. **Auto-Merge einschalten:** `mcp__ccd_pr__set_auto_merge` (Squash). GitHub mergt dann selbst,
   sobald alle Pflicht-Checks grün sind – nie mit `--admin` oder sonst an Checks vorbei.
2. **Auf Events reagieren:**
   - **Roter Check:** Log lesen, Ursache beheben (nicht grün-fummeln), lokal Build/Tests der
     betroffenen Seite, committen, pushen. Bei Frontend-Änderung `npm run build` + Assets mit.
   - **Review-Kommentar** (Mensch, Copilot, CodeQL): sachlich prüfen, berechtigte Punkte umsetzen,
     pushen und im Thread kurz antworten, was geändert wurde. Hält man einen Kommentar für falsch,
     nicht still ignorieren, sondern im Thread begründen und dem Nutzer vorlegen.
   - **Merge-Konflikt:** Branch mit `origin/main` zusammenführen (im App-Worktree über
     `mcp__ccd_host__sync_with_base_branch`), Konflikte lösen, bei Asset-Konflikten neu bauen.
3. **Nach dem Merge:** Issue-Status prüfen (`review-and-ship` Phase 4d), dann aufräumen
   (siehe „Aufräumen“).

## 8. Aufräumen (Nutzervorgabe 31.10.2026, immer)

Nichts bleibt liegen, was die Aufgabe angelegt oder gestartet hat:

- **Server und Prozesse:** selbst gestartete Dev-Server, `vite preview`, Backends
  (`spring-boot:run`, Prod-Kopie-Backend) und Hintergrund-Testläufe beenden, sobald sie
  nicht mehr gebraucht werden – spätestens vor dem Abschlussbericht. Gezielt über die
  eigene Prozess- bzw. Server-ID (`preview_stop`, Hintergrund-Task stoppen), nie breit per
  `pkill -f`, sonst trifft es parallele Sessions.
- **Container und Images:** selbst angelegte Wegwerf-Container (z. B. `postgres:16` für
  `DatenbankKompatibilitaet*Test`) stoppen und löschen (`docker rm -f <name>`), dazu
  ungenutzte, selbst gezogene Images/Volumes, wenn sie nur für diese Aufgabe da waren.
  **Wenn es Sinn macht** heißt: Dauer-Container wie `erp-dev-pg` oder die Prod-Kopie
  (`erp-prodkopie-db`, eigenes Lösch-Kommando `prodkopie-starten.sh --loeschen`) bleiben,
  solange der Nutzer sie weiter braucht – im Zweifel im Abschluss nennen statt löschen.
  Erzeugte PDFs unter `uploads/` des Worktrees vor dem Entfernen löschen.
- **Issues schließen oder kommentieren:** Der PR-Text verlinkt jedes bearbeitete Issue mit
  `Closes #<nr>` (auch teilweise erledigte, Restpunkte unter `## Offene Restpunkte`) oder
  sagt `Kein Issue` – ohne das mergt die PR-Automatik nicht. Nach dem Merge jedes Issue mit
  `gh issue view <nr> --json state` nachprüfen und notfalls selbst schließen bzw. die
  Restpunkte kommentieren (`review-and-ship` Phase 4d).
- **Branches nach dem Merge immer löschen:** Remote-Branch (`git push origin --delete <branch>`,
  falls GitHub ihn nicht schon automatisch gelöscht hat) und lokalen Branch
  (`git branch -D <branch>`, nach Squash-Merge ist `-d` wirkungslos). Vorher prüfen, dass
  der PR wirklich gemergt ist (`gh pr view <nr> --json state` = `MERGED`) und kein anderer
  offener PR auf dem Branch aufbaut (gestapelte PRs: erst den Folge-PR auf `main` umstellen).
- **Worktree:** nach dem Merge entfernen (im App-Worktree über die App bzw.
  `mcp__ccd_host__clean_up_worktrees`, sonst `git worktree remove`).
- Im Abschlussbericht kurz nennen, was aufgeräumt wurde und was bewusst stehen bleibt.

## Abschluss an den Nutzer

Kurz und in Handwerker-Sprache: was jetzt wo geht (welcher Knopf, welcher Reiter), was
bewusst nicht gemacht wurde, Testzahlen, Commit/Branch, PR-Link, Stand von CI und Auto-Merge.
