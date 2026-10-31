---
name: fullstack-parallel
description: Ablauf für Aufgaben im ERP, die Backend (Spring Boot) UND Frontend (react-pc-frontend / react-zeiterfassung) betreffen – kurzes Brainstorming mit dem Nutzer, API-Vertrag festlegen, Frontend parallel per Subagent bauen lassen, Backend selbst bauen, und erst wenn beides fertig ist EIN gemeinsames /review-and-ship. Unbedingt verwenden, sobald ein Wunsch neue oder geänderte Endpoints plus neue Knöpfe/Dialoge/Seiten braucht („mach das auch im Frontend“, „neuer Dialog mit Vorschlägen“, „Endpoint + Anzeige“), auch wenn der Nutzer nicht „parallel“ sagt. Auch explizit über „/fullstack-parallel“. NICHT für reine Backend- oder reine Frontend-Änderungen und nicht für kleine Fixes; für große, mehrteilige Features mit Issue/PR-Pipeline ist /loese-problem gedacht.
---

# fullstack-parallel – Backend selbst, Frontend parallel, Review am Schluss

**Ankündigen:** „Ich nutze den fullstack-parallel-Skill: kurzes Brainstorming, dann Backend und Frontend parallel, Review am Schluss.“

**Rollen (feste Aufteilung):**

| Wer | Was | Liest | Sicherheit |
| --- | --- | --- | --- |
| **Hauptsession (du)** | Backend: Service, Controller, Flyway, Backend-Tests; Vertrag, Brainstorming, Review, Commit, PR | `BACKEND_ARCH.md` (inkl. „Sicherheit im Backend“), `TESTING_SECURITY.md` | nur Backend-Sicherheitssicht |
| **Frontend-Agent** (`erp-frontend`, Hintergrund) | Oberflächen in `react-pc-frontend` / `react-zeiterfassung`, Frontend-Tests, Playwright-Spec | `FRONTEND_UI.md` (inkl. „Sicherheit im Frontend“), Skill `handwerkerprogramm-design` | nur Frontend-Sicherheitssicht |
| **Reviewer** (`erp-code-reviewer`, einmal am Ende) | prüft **beide** Seiten, Findings getrennt nach `[BACKEND]` und `[FRONTEND]` | alle drei Docs + Skill | beide Sichten |

Warum dieser Ablauf: Backend und Frontend hängen nur über die API zusammen. Steht der
Vertrag (Endpoints, Parameter, JSON-Felder, Fehlercodes) einmal fest, können beide Seiten
gleichzeitig entstehen – das halbiert die Wartezeit. Ein Review über halbfertigen Code
kostet dagegen eine Runde und findet Dinge, die sich ohnehin noch ändern. Deshalb:
parallel bauen, aber **einmal** am Ende prüfen.

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
- Statuscodes: 200, 400 (mit `{message}` in Handwerker-Sprache), 401, 403, 404
- Welche alten Endpoints bleiben (die das Frontend weiter nutzt)

Bestehende DTOs wiederverwenden, wo die Felder passen (z. B. `DokumentRef`) – dann muss
das Frontend keine neuen Typen erfinden.

## 3. Frontend-Subagent starten (Hintergrund)

`Agent` mit `subagent_type: "erp-frontend"`, `run_in_background: true`, **im selben
Working Tree** (kein Worktree – die Dateien überschneiden sich nicht, und ein späteres
Zusammenführen entfällt). Der Prompt muss allein tragen; der Agent kennt das Gespräch
nicht. Hinein gehört:

1. **Pflichtlektüre:** steht in der Agent-Definition `erp-frontend` (`FRONTEND_UI.md`, Skill `handwerkerprogramm-design` samt `PRUEFUNG.md`, `TESTING_SECURITY.md`). Im Prompt nur kurz daran erinnern: „Lies zuerst deine Pflichtlektüre, sonst blockt der Edit-Hook.“ Backend-Dokumente bekommt er bewusst nicht.
2. **Ziel aus Nutzersicht** inkl. eines konkreten Beispiels aus dem Gespräch.
3. **Bestehende Basis** mit Pfaden und ungefähren Zeilen (Komponenten, Hilfsdateien, Tests).
4. **Der API-Vertrag aus Schritt 2** wörtlich.
5. **Umsetzungsschritte** und wo neue Knöpfe hinkommen.
6. **Info-Buttons mit fertigen Texten:** Für jede erklärungsbedürftige Stelle aus dem
   Brainstorming den Ort (neben welchem Feld/welcher Überschrift) und den Inhalt
   vorgeben – „Was passiert im Hintergrund?“ und „Was bewirkt das?“. Du kennst das
   Backend-Verhalten, der Frontend-Agent nicht; deshalb schreibst **du** die Texte.
   Baustein: `react-pc-frontend/src/components/ui/info-button.tsx` (Regeln unten).
7. **Tests:** Vitest + Testing Library, bestehende grün halten, E2E unter
   `react-pc-frontend/e2e/` mit `page.route`-Mocks; `npm run lint`, `npm run test -- --run`,
   `npm run build`; Playwright-Design-Prüfung nach `PRUEFUNG.md` (im Skill `handwerkerprogramm-design`). Den Abschnitt
   „Schnelle E2E-Tests“ unten wörtlich in den Prompt übernehmen.
8. **Grenzen:** nur Dateien unter `react-pc-frontend/` (bzw. `react-zeiterfassung/`),
   `src/main/java` nicht anfassen, **nicht committen**.
9. **Bericht:** vollständige Liste geänderter/neuer/gelöschter Dateien, Testzahlen,
   Lint/Build-Status, alle Info-Button-Stellen, offene Punkte.

Ändert sich der Vertrag später, dem laufenden Agenten per `SendMessage` Bescheid geben –
nicht einen zweiten starten.

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

## 4. Backend selbst bauen (währenddessen)

`docs/agent instructions/docs/BACKEND_ARCH.md` (mit „Sicherheit im Backend“) und `TESTING_SECURITY.md` lesen, dann (nur Backend; Frontend-Sicherheit ist Sache des Frontend-Agenten):

- Logik in einen Service (Controller bleibt dünn), Constructor Injection, Flyway nur
  neu und idempotent.
- Endpoints mit Sichtbarkeits-/Rechteprüfung wie die Nachbar-Endpoints, Validierung
  (`@Valid`, `@Positive`), verständliche 400-Meldungen.
- Tests: Service (Mockito), Controller/Security (MockMvc: 401, CSRF 403, 404 nicht
  sichtbar, 400 ungültige IDs/SQL-Injection/XSS-Strings), Dummy-Daten („Max Mustermann“).
- Alle Konstruktor-Aufrufe in bestehenden Tests mitziehen (`grep -rn "new <Klasse>("`).
- `./mvnw -q compile`, gezielte Tests, danach die **komplette Suite im Hintergrund**.

## 5. Warten, bis beides fertig ist

Kein Review, kein Commit, solange der Frontend-Agent läuft oder Tests offen sind –
auch wenn der Stop-Hook „uncommitted changes“ meldet. Dem Nutzer kurz sagen, worauf
gewartet wird. Nicht pollen: die Benachrichtigungen kommen von selbst.

Wenn der Frontend-Bericht da ist:

- Gegenprobe Vertrag: Endpoints und Feldnamen im Frontend greppen und mit dem Backend
  vergleichen (häufigster Fehler: ein umbenanntes Feld).
- Gegenprobe Info-Buttons: Sind alle geplanten Stellen da, und stimmen die Texte noch
  mit dem gebauten Backend überein (Backend-Details ändern sich beim Bauen oft)?
- Frontend selbst einmal `lint`, `test -- --run`, `build` laufen lassen.
- Rote Tests, die nichts mit der Änderung zu tun haben: mehrfach einzeln laufen lassen,
  als unzuverlässig benennen statt still zu ignorieren.

## 6. Ein gemeinsames /review-and-ship

Jetzt `Skill` → `review-and-ship` über den **gesamten** Diff (Backend + Frontend). Der Reviewer prüft beide Seiten, jede gegen ihre eigene Sicherheitssicht und ihre Checkliste. Dort
gelten dessen Regeln: ein Reviewer-Subagent, höchstens 2 Runden, nur eigene Dateien
stagen (die Liste aus deinen Edits + dem Bericht des Frontend-Agenten), Build-Artefakte
aus `npm run build` dürfen mit.

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
3. **Nach dem Merge:** Issue-Status prüfen (`review-and-ship` Phase 4d), Worktree aufräumen.

## Abschluss an den Nutzer

Kurz und in Handwerker-Sprache: was jetzt wo geht (welcher Knopf, welcher Reiter), was
bewusst nicht gemacht wurde, Testzahlen, Commit/Branch, PR-Link, Stand von CI und Auto-Merge.
