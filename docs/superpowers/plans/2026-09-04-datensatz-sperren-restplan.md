# Plan: Datensatz-Sperren Fundament — Restarbeiten

Issue: #82
Feature-Branch: claude/eloquent-ramanujan-gz0w2t
Kontext-Log: C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/lock-arbeit/docs/superpowers/plans/2026-09-04-datensatz-sperren-log.md
Spec: docs/superpowers/specs/2026-09-03-datensatz-sperren-fundament.md (aus Issue #82 zurückgeholt)

> **Herkunft:** Der Originalplan (19 Tasks) ging mit dem Cloud-Container verloren. Dieser
> Plan deckt die Restarbeiten ab, abgeleitet aus Spec + Code-Stand. Abschnitte 1–3 des
> Originalplans sind abgenommen und liegen auf origin.

## Global Constraints

- Pflichtlektüre vor dem ersten Edit laut `.claude/CLAUDE.md`: `BACKEND_ARCH.md` für Java,
  `FRONTEND_UI.md` für React, `TESTING_SECURITY.md` für Tests. Dazu
  `.claude/skills/loese-problem/references/kriterien.md`.
- Testgetrieben nach `superpowers:test-driven-development`. Roter Test zuerst, Grund des
  Fehlschlags verstehen, dann umsetzen.
- Nur die unter `Files` genannten Dateien anfassen.
- Deutsche Bezeichner und Kommentare wie im Bestand. Meldungstexte in Handwerker-Sprache,
  keine Klassennamen im Response-Body.
- Testdaten nur mit Dummy-Namen (DSGVO).
- Keine Klarnamen, E-Mails oder Adressen ins Log.
- **Frontend-Tasks (ab Abschnitt 6):** Marvin hat am 04.09.2026 auf `main` das eigene
  Design-System als Skill verdrahtet (`2a684d57`). Vor dem ersten Edit an `.ts`/`.tsx` in
  `react-pc-frontend/` ist der Skill `handwerkerprogramm-design` Pflicht — **nicht** die
  generischen `ui-ux-pro-max`-Skills, die sind nur Ergänzung. Dazu die neuen Regeln in
  `FRONTEND_UI.md` (`4e773e27`): jede fehlgeschlagene Aktion zeigt einen Toast
  (`useToast` aus `src/components/ui/toast.tsx`), Farben ausschließlich rose/slate,
  deaktivierte Buttons erklären per Tooltip, **warum** sie deaktiviert sind, kein Emoji in
  der Oberfläche, Icons nur aus Lucide.
- **Test-Falle, in Abschnitt 5 gefunden:** `userEvent.click()` hängt unter
  `vi.useFakeTimers()` zuverlässig, auch mit `delay: null`. Entweder den UI-Test mit echten
  Timern fahren oder `userEvent.setup({ advanceTimers: vi.advanceTimersByTime })` nutzen.
  Immer `vi.useRealTimers()` im `afterEach`, sonst kippt ein abgebrochener Fake-Timer-Test
  alle folgenden in undurchsichtige Timeouts.
- **Vor Abschnitt 6 muss `origin/main` in den Feature-Branch gemerged sein**, sonst fehlen
  Skill und Hook in den Worktrees. Probe-Merge am 04.09.2026 war konfliktfrei
  (`git merge-tree`, einzige beidseitig geänderte Datei `loese-problem/SKILL.md` merged sauber).
  Erst nach Review 3, weil der Reviewer bis dahin im Feature-Worktree arbeitet.

## Baseline (gemessen am 04.09.2026 auf 5f556b19)

- Backend `./mvnw -B test`: 2442 Tests, 0 Failures, **4 vorbestehende Errors** —
  `AuditChainRepairIntegrationTest` (2) und `AuditHashRoundtripDiagnoseTest` (2), alle
  `CannotCreateTransaction: Could not open JPA EntityManager`. Umgebungsbedingt (keine
  echte DB lokal). Nicht reparieren.
- Frontend `npm test`: 83 Dateien, 972 Tests, alle grün.
- Frontend `npm run lint`: 0 Fehler, 1 vorbestehende Warnung
  (`BelegeKasseEditor.tsx:1204`, react-hooks/exhaustive-deps). Nicht reparieren.
- Frontend `npm run build`: grün.
- **Abnahmeregel:** grün = genau diese 4 Backend-Errors und diese 1 Lint-Warnung.
  Der 5. Fehler bzw. die 2. Warnung ist neu und gehört dem Task, der sie verursacht hat.

## Umgebung

- Java 23 (Temurin), Node 24.19, npm 11.17. `./mvnw` läuft, voller Backend-Testlauf ~10 Min.
- `node_modules` im Haupt-Worktree ist eine Junction auf das Haupt-Repo. Neue Worktrees
  brauchen eine eigene Junction — der Orchestrator legt sie beim Worktree-Anlegen mit an.
- `DateiSpeicherServiceTest` legt bei jedem Backend-Lauf einen Streuordner mit Backslashes
  im Namen an. Bekannt, kein Fehler dieses Vorhabens, vor dem Löschen trotzdem prüfen.

## Abschnitt 4 (Nachbau des verlorenen Abschnitts)

### Task 4a — DatensatzLockController
- Branch: lock/task-4a-controller
- Worktree: ../wt/task-4a
- Files:
  - `src/main/java/org/example/kalkulationsprogramm/controller/DatensatzLockController.java` (neu)
  - `src/test/java/org/example/kalkulationsprogramm/controller/DatensatzLockControllerTest.java` (neu)
- Interfaces:
  - Produces: Routen `/api/datensatz-locks/{typ}/{id}/acquire`, `/heartbeat`, `DELETE /{typ}/{id}`
  - Consumes: `DatensatzLockService`, `SperrbarerTyp` (beide fertig auf dem Feature-Branch)
- Steps:
  - [ ] `DokumentLockController` als Vorbild lesen — Pfad- und DTO-Schema übernehmen, nicht neu erfinden
  - [ ] Typ-Auflösung über `SperrbarerTyp.ausText()`; unbekannter Typ → 400
  - [ ] Kein Principal → 401; `LOCKED_BY_OTHER` → 409 mit DTO im Body; sonst 200
  - [ ] Test: je ein Fall für 200, 409, 400 (unbekannter Typ), 401, und DELETE → 204
  - [ ] Den alten `DokumentLockController` NICHT anfassen — der fliegt später als Ganzes raus

### Task 4b — Versionskonflikt kommt als 409 durch
- Branch: lock/task-4b-durchgriff
- Worktree: ../wt/task-4b
- Files:
  - `src/main/java/org/example/kalkulationsprogramm/controller/AusgangsGeschaeftsDokumentController.java`
  - `src/test/java/org/example/kalkulationsprogramm/controller/AusgangsGeschaeftsDokumentControllerTest.java`
- Interfaces:
  - Produces: nichts, das andere importieren
  - Consumes: `RestExceptionHandler.handleOptimisticLockingFailure` (fertig)
- Steps:
  - [ ] Nachweisbares Ziel: Versionskonflikt beim `PUT /{id}` ⇒ **409** mit der
        Handwerker-Meldung, **kein Klassenname im Body**. Übrige Fehlerfälle liefern
        weiterhin 400.
  - [ ] `catch (RuntimeException e)` in `update` (Z. ~225) gezielt **verengen**, nicht
        streichen — sonst kippen die anderen Fehlerfälle mit
  - [ ] Roter Test zuerst: `service.aktualisieren` wirft
        `ObjectOptimisticLockingFailureException`, erwartet 409
  - [ ] Zweiter Test hält fest, dass ein gewöhnlicher Fehler weiterhin 400 liefert
  - [ ] Die anderen `catch (RuntimeException)`-Stellen derselben Datei nur anfassen, wenn
        sie denselben Speicherweg betreffen — sonst als Bedenken ins Kontext-Log

### Task 4c — noopener raus
- Branch: lock/task-4c-noopener
- Worktree: ../wt/task-4c
- Files:
  - `react-pc-frontend/src/pages/DokumentUebersichtEditor.tsx`
  - zugehörige Testdatei (neu oder bestehend, je nach Bestand)
- Interfaces:
  - Produces: nichts
  - Consumes: nichts
- Steps:
  - [ ] Z. 223: `window.open(..., '_blank', 'noopener')` — das dritte Argument entfernen.
        Grund: mit `noopener` verliert der geöffnete Tab die Verbindung zum Öffner, und
        der neue X-Button-Ablauf braucht sie
  - [ ] Roter Test zuerst: prüft, dass `window.open` mit genau zwei Argumenten gerufen wird
  - [ ] Erklärenden Kommentar direkt darüber setzen, damit niemand das `noopener` später
        "reparierend" wieder einfügt
  - [ ] Andere `noopener`-Stellen im Projekt (`rel="noopener noreferrer"` an `<a>`-Tags)
        NICHT anfassen — die sind richtig so

## Abschnitt 5 (Hook + Backend-Verbraucher)

### Task 5a — useDatensatzLock
- Branch: lock/task-5a-hook
- Worktree: ../wt/task-5a
- Files:
  - `react-pc-frontend/src/components/lock/useDatensatzLock.ts` (neu)
  - `react-pc-frontend/src/components/lock/useDatensatzLock.test.tsx` (neu)
- Interfaces:
  - Produces: Hook `useDatensatzLock(typ, id)` — der fünfte Baustein aus der Spec
  - Consumes: Routen aus Task 4a
- Steps:
  - [ ] `useDocumentLock.ts` als Vorbild — Verhalten übernehmen, auf `SperrbarerTyp` und
        `/api/datensatz-locks/` umstellen
  - [ ] Zusätzlich zum Vorbild: `verbindungWeg` nach mehreren fehlgeschlagenen Heartbeats
        in Folge (`BearbeitenLeiste` erwartet das Feld), und `seit`-Minuten für
        `GesperrtHinweis`
  - [ ] Modus `lesen`/`bearbeiten` und eine Möglichkeit, das Lock aktiv freizugeben —
        `BearbeitenLeiste` braucht beides
  - [ ] Tests testgetrieben, mit gefaktem `fetch` wie in `useKonfliktMeldung.test.tsx`

### Task 5b — Backend-Verbraucher auf DatensatzLockService
- Branch: lock/task-5b-backend-verbraucher
- Worktree: ../wt/task-5b
- Files:
  - `src/main/java/org/example/kalkulationsprogramm/controller/LieferantDokumentController.java`
  - `src/test/java/org/example/kalkulationsprogramm/controller/LieferantDokumentControllerTest.java`
- Interfaces:
  - Consumes: `DatensatzLockService` (fertig)
- Steps:
  - [ ] `DokumentLockService` → `DatensatzLockService`, `TYP_EINGANG` → `SperrbarerTyp.EINGANG`
  - [ ] Verhalten unverändert lassen; nur die Abhängigkeit tauschen
  - [ ] Bestehende Tests mitziehen

### Task 5c — heartbeat-Zweige absichern
- Branch: lock/task-5c-heartbeat-tests
- Worktree: ../wt/task-5c
- Files:
  - `src/test/java/org/example/kalkulationsprogramm/controller/DatensatzLockControllerTest.java`
- Interfaces:
  - Consumes: `DatensatzLockController` aus Task 4a (gemerged)
- Herkunft: Hinweis aus dem Review von Abschnitt 4.
- Steps:
  - [ ] `heartbeat` hat bisher keinen einzigen Test. Seine 409- und 401-Zweige sind
        unbewiesene Kopien aus `acquire` — sieht richtig aus, ist aber nicht belegt.
  - [ ] Tests ergänzen: 200 (eigenes Lock verlängert), 409 (fremdes Lock), 401 (kein
        Principal), 400 (unbekannter Typ).
  - [ ] Nur die Testdatei anfassen. Findet sich dabei ein echter Fehler im Controller:
        **melden statt beheben** — der Controller gehört diesem Task nicht.

> `AusgangsGeschaeftsDokumentController` wird bewusst NICHT hier umgestellt — Task 4b fasst
> dieselbe Datei an. Die Umstellung folgt in Abschnitt 6.

## Abschnitt 6 — ERSETZT, siehe „Planänderung nach Review 5" weiter unten
<!-- Ursprünglicher Schnitt, zur Nachvollziehbarkeit stehen gelassen -->
## Abschnitt 6 (Frontend-Verbraucher) [alt]

### Task 6a — DocumentEditorPage auf das neue Fundament
- Files: `react-pc-frontend/src/pages/DocumentEditorPage.tsx` + Test,
  **zusätzlich** `react-pc-frontend/src/components/lock/BearbeitenLeiste.tsx` (nur der
  Kommentar zu `kannBearbeiten`, siehe unten — 6b darf die Datei deshalb nicht anfassen)
- Consumes: Task 5a
- Nur-Lesen-Modus mit `GesperrtHinweis` statt hartem `DocumentLockedModal`.
- **Semantik von `kannBearbeiten`, festgelegt in Review 5 / Nachbesserung 2:** „Ein Klick auf
  Bearbeiten ist gerade sinnvoll" — true bei `idle`, `locked-by-other` (Klick = Übernahmeversuch,
  bei 409 bleibt es `lesen`) und `acquired`; false nur bei `loading` und `error`. Der
  Prop-Kommentar in `BearbeitenLeiste.tsx:52` sagt noch „false = ein anderer hält das Lock" —
  das stimmt nicht mehr und muss auf die neue Bedeutung umgeschrieben werden. Kein
  Verhaltenscode in der Komponente ändern.
- Der Hook exportiert `status`; bei `'error'` zeigt die Seite einen Hinweis mit Neu-laden
  **und** einen Toast (`toast.error`, Handwerker-Wording, z.B. „Sperre konnte nicht geholt
  werden — bitte neu laden.").
- Ist der Bearbeiten-Knopf deaktiviert (`loading`/`error`), bekommt er laut `FRONTEND_UI.md`
  einen Tooltip mit dem Grund. Das ist Sache der Seite (oder ein zusätzlicher optionaler
  Prop an `BearbeitenLeiste` — dann in 6a, weil 6a die Datei ohnehin anfasst).

### Task 6b — LieferantDokumentModal auf das neue Fundament
- Files: `react-pc-frontend/src/components/LieferantDokumentModal.tsx` + Test
- Consumes: Task 5a

### Task 6c — Ausgangs-Controller: Lock-Service tauschen und die übrigen Speicherwege nachziehen
- Files: `AusgangsGeschaeftsDokumentController.java` + Test
- Consumes: Task 4b (dieselbe Datei, muss vorher gemerged sein)
- Zwei Dinge in einem Task, weil beide dieselbe Datei anfassen:
  1. `DokumentLockService` → `DatensatzLockService`, `TYP_AUSGANG` → `SperrbarerTyp.AUSGANG`.
  2. **Befund aus Task 4b:** `buchen`, `emailVersendet`, `pdfSpeichern`, `stornieren` und
     `delete` ändern denselben versionierten Aggregate-Root über denselben Service und
     verschlucken den Versionskonflikt genauso wie `update` es tat. Task 4b hat bewusst nur
     `PUT /{id}` behandelt (beauftragter Scope) und den Rest gemeldet. Die Spec verspricht
     409 für **alle** Speicherwege — also hier nachziehen, je Speicherweg mit rotem Test
     zuerst. Wieder verengen, nicht streichen.

## Abschnitt 7 (X-Button-Ablauf — die eigentliche Ursache) [alt, ERSETZT — siehe unten]

### Task 7a — doppelter Heartbeat raus UND neuer X-Button-Ablauf (ein Task, eine Datei)
- Files: `react-pc-frontend/src/components/document-editor/index.tsx`, `index.test.tsx`
- Consumes: 6a (Seite auf `useDatensatzLock`), 5a
- Zwei Schritte in einem Task, weil beide dieselbe Datei anfassen und zwei Tasks in einem
  Abschnitt nie dieselbe Datei schreiben dürfen:
  1. Den doppelten, nie gestoppten zweiten Heartbeat (Z. ~1142–1190) entfernen;
     `index.test.tsx` Z. ~107 hängt daran. Roter Test zuerst: „nach Unmount kein weiterer
     Heartbeat-Request".
  2. Neuer X-Button-Ablauf laut Spec: (1) Warnung bei ungespeicherten Änderungen,
     (2) speichern, (3) Sperre aktiv freigeben (`freigeben()` aus dem Hook), (4) Tab
     schließen versuchen, (5) existiert der Tab nach ~150 ms noch: Hinweisseite „Dokument
     gespeichert und freigegeben — du kannst diesen Tab jetzt schließen". Design nach
     `handwerkerprogramm-design` (rose/slate, Lucide, kein Emoji).

### Task 7b — Generationsprüfung nach `res.json()` im 409-Zweig
- Files: `react-pc-frontend/src/components/lock/useDatensatzLock.ts`,
  `useDatensatzLock.test.tsx`
- Herkunft: 🟡 aus Review 5 (Durchgang 2 und 3), Zeilen ~131–140 und ~190–196.
- Ein von `freigeben()` überholtes 409-Ergebnis schreibt nach dem zweiten `await`
  (`res.json()`) noch `halterName` und `status='locked-by-other'`. Harmlos (kein
  Phantom-Lock), zeigt aber kurz einen falschen Halter. Fix: zweite
  `gen !== generationRef.current`-Prüfung nach dem `json()`. Roter Test zuerst mit
  künstlich langsamer `json()`.

## Planänderung nach Review 5 (04.09.2026) — gültiger Schnitt für Abschnitt 6 und 7

**Warum umgestellt:** Im alten Schnitt hätte Abschnitt 6 die Editor-Seite auf
`useDatensatzLock` gezogen, während `document-editor/index.tsx` seine eigene Lock-Logik
(zweiter Heartbeat, eigenes Acquire, eigenes `isLocked`) bis Abschnitt 7 behalten hätte.
Ergebnis: nach „Fertig" gibt die Seite das Lock frei, der Editor hält sich aber für
entsperrt und bleibt editierbar — exakt der Fehler, den die Spec beheben soll. Der
Abschnitt wäre per Konstruktion rot gewesen. Deshalb: **erst der Editor, dann die Seite.**

### Abschnitt 6 (gültig)

#### Task 6a — Editor-Komponente: eigene Lock-Logik raus, Props rein, X-Button-Ablauf
- Branch: lock/task-6a-editor-seite (Name historisch, Inhalt: Editor-Komponente)
- Worktree: ../wt/task-6a
- Files:
  - `react-pc-frontend/src/components/document-editor/index.tsx`
  - `react-pc-frontend/src/components/document-editor/index.test.tsx`
  - `react-pc-frontend/src/components/lock/TabSchliessenHinweis.tsx` (neu) + Test
- Interfaces:
  - Produces: Props `readOnly?: boolean` (Default false; true ⇒ Editor verhält sich wie
    heute bei `isLocked`) und `onLockFreigeben?: () => Promise<void>` (wird im
    X-Button-Ablauf zwischen Speichern und Tab-Schließen aufgerufen). Bedeutung:
    `readOnly` = „die Seite hält kein Lock, der Nutzer darf nur lesen";
    `onLockFreigeben` = „gib die Sperre der Seite aktiv frei, warte, bis der Server es
    bestätigt hat".
  - Consumes: nichts Neues. Der Editor holt **kein** Lock mehr selbst — das macht die
    Seite (Abschnitt 7), und beim Anlegen eines neuen Dokuments holt es das Backend
    (`AusgangsGeschaeftsDokumentController.create`).
- Steps:
  1. Zweiten Heartbeat (Z. ~1142–1176) und eigenes Acquire (`tryAcquireLock`, Z. ~1182)
     entfernen. `isLocked` wird zu `readOnly || <bisherige fachliche Bedingungen>`.
     `index.test.tsx` Z. ~107 stubt `/api/dokument-locks/` — mitziehen. Roter Test:
     „nach Mount und Unmount geht kein Request an `/api/dokument-locks/` oder
     `/api/datensatz-locks/`".
  2. X-Button-Ablauf in fester Reihenfolge: (1) bestehende Warnung bei ungespeicherten
     Änderungen, (2) speichern, (3) `await onLockFreigeben?.()`, (4) `window.close()`
     versuchen, (5) ist der Tab nach ~150 ms noch da: `TabSchliessenHinweis` rendern
     („Dokument gespeichert und freigegeben — du kannst diesen Tab jetzt schließen").
     Roter Test: Aufruf-Reihenfolge save → onLockFreigeben → window.close per Spy; zweiter
     roter Test: `window.close` ist wirkungslos ⇒ Hinweis erscheint.
  3. Schlägt das Speichern fehl: **nicht** freigeben, **nicht** schließen, Toast mit
     Handwerker-Wording (`useToast`), Editor bleibt offen.
  4. Design: Skill `handwerkerprogramm-design` vor dem ersten Edit. rose/slate, Lucide,
     kein Emoji. Der Hinweis ist eine ruhige Vollbild-Seite, kein Modal.

#### Task 6b — LieferantDokumentModal auf das neue Fundament
- Branch: lock/task-6b-lieferant-modal
- Worktree: ../wt/task-6b
- Files: `react-pc-frontend/src/components/LieferantDokumentModal.tsx`,
  `react-pc-frontend/src/components/LieferantDokumentModal.test.tsx` (neu)
- Consumes: `useDatensatzLock`, `GesperrtHinweis`, `BearbeitenLeiste` (alle fertig).
  `BearbeitenLeiste.tsx` selbst **nicht** anfassen (Kommentar-Update macht 7a).
- Steps: `useDocumentLock`/`DocumentLockedModal` raus. `locked-by-other` ⇒ Formular
  gesperrt + `GesperrtHinweis` + `BearbeitenLeiste` im Modus `lesen` (Klick = Übernahme-
  versuch). `acquired` ⇒ Formular frei, `BearbeitenLeiste` im Modus `bearbeiten`.
  `error` ⇒ Hinweis + Toast. Schließen des Modals ⇒ `freigeben()`. Idle-Timer:
  `useIdleTimer` mit `onIdle` ⇒ `freigeben()` und zurück in `lesen`; Countdown in die
  Leiste. Automatisches Speichern beim Idle nur, wenn das Modal ein gefahrloses Speichern
  hat — sonst als Bedenken vermerken. Jeder Zustand mit rotem Test zuerst.

#### Task 6c — Ausgangs-Controller: Lock-Service tauschen, übrige Speicherwege nachziehen
- Branch: lock/task-6c-ausgangs-controller
- Worktree: ../wt/task-6c
- Files: `AusgangsGeschaeftsDokumentController.java` + `AusgangsGeschaeftsDokumentControllerTest.java`
- Unverändert gegenüber dem alten 6c (siehe oben): `DokumentLockService` →
  `DatensatzLockService`, `TYP_AUSGANG` → `SperrbarerTyp.AUSGANG`, `DokumentLockDto` →
  `DatensatzLockDto` in `create`; danach den Versionskonflikt-Durchgriff aus 4b auf
  `buchen`, `emailVersendet`, `pdfSpeichern`, `stornieren`, `delete` übertragen — je
  Speicherweg roter Test zuerst, verengen statt streichen.

### Abschnitt 7 (gültig) — in zwei Runden

> **Umgeschnitten am 04.09.2026, vor dem Start:** 7a (Seite) hätte gegen den Hook
> gebaut, den 7b gleichzeitig ändert (Modus nach Mount-Acquire). 7a's Spec müsste dann
> entweder den alten Zustand zusichern (kippt nach 7b's Merge) oder den neuen (rot vor
> 7b's Merge). Ein Task startet erst, wenn fertig ist, was er konsumiert — also:
> **Runde 7-1 = 7b + 7c parallel** (Hook und Leiste, disjunkt), **Runde 7-2 = 7a**
> danach, auf dem gemergten Stand. Design-Reviewer in 7-1 für 7c, in 7-2 für 7a.

#### Task 7a — DocumentEditorPage auf das neue Fundament (Runde 7-2)
- Files: `react-pc-frontend/src/pages/DocumentEditorPage.tsx`,
  `DocumentEditorPage.test.tsx` (neu), `e2e/dokument-editor-seite.spec.ts` (neu),
  **plus** `components/lock/TabSchliessenHinweis.tsx` (nur `text-balance`, Hinweis aus
  Design-Review 7-1: das „Sie" hängt allein am Zeilenende) und, falls die Seite einen
  Speicher-Auslöser braucht, minimal `document-editor/index.tsx` + `types.ts` (nur dafür,
  als Abweichung vermerken — niemand sonst arbeitet in Runde 7-2 daran).
  `BearbeitenLeiste.tsx` gehört **7c**, nicht 7a.
- Runde 7-1 hat geliefert, was 7a braucht: Hook öffnet direkt im Modus `bearbeiten`,
  Leiste hat `bearbeitenGesperrtGrund` und `zeigeNurLesenHinweis`.

> **Orchestrator, vor Runde 7-2 — erledigt:** Design-Review 7-1 meldete E2E-Flattern auf
> kaltem Dev-Server (`toBeVisible()` reißt nach 5 s, während Vite noch 19–21 s baut).
> `playwright.config.ts` hat jetzt `globalSetup: e2e/hilfen/aufwaermen.ts` (lädt
> Startseite, Dokument-Editor und Lieferanten einmal mit echtem Browser) und
> `expect.timeout: 15 s`. Gegen kalten Server ausgeführt: Leisten-Spec 5/5 in 29 s,
> einzelne Tests 2,7–6,6 s. Commit siehe Feature-Branch.
- Consumes: 6a (`readOnly`, `onLockFreigeben`), 5a, `useIdleTimer`, `GesperrtHinweis`.
- `useDocumentLock`/`DocumentLockedModal` raus. `locked-by-other` ⇒ Editor mit
  `readOnly`, darüber `GesperrtHinweis` + `BearbeitenLeiste` (`lesen`). `acquired` ⇒
  Editor editierbar, `BearbeitenLeiste` (`bearbeiten`), `onLockFreigeben={freigeben}`.
  `error` ⇒ Hinweis + Toast + Neu-laden. `useIdleTimer`: Countdown in die Leiste,
  `onIdle` ⇒ speichern lassen (Editor) und `freigeben()`. Zum Speichern beim Idle braucht
  die Seite einen Weg in den Editor — wenn 6a dafür keinen Prop vorgesehen hat, ist das
  ein Bedenken, kein stiller Umbau.

#### Task 7b — Hook nachziehen: Generationsprüfung + Modus nach Mount-Acquire
- Files: `useDatensatzLock.ts`, `useDatensatzLock.test.tsx`
- (1) Generationsprüfung nach `res.json()` im 409-Zweig, wie oben unter [alt] 7b.
- (2) **Befund aus Task 6b:** Der Hook bleibt nach erfolgreichem Acquire beim Mount im
  Modus `lesen`. Wer öffnet, hält damit das Lock, ohne bearbeiten zu dürfen — blockiert
  Kollegen und hat selbst nichts davon. 6b hat sich mit einem Modal-eigenen Effekt
  beholfen (`status === 'acquired' && modus === 'lesen'` ⇒ `onBearbeiten()`), 7a
  bräuchte denselben. Das gehört in den Hook: nach erfolgreichem **Mount**-Acquire ist
  `modus` direkt `'bearbeiten'` (so verhält sich der Editor heute, und die Spec ersetzt
  nur die harte Blockierung bei Fremdsperre durch Nur-Lesen). Nach `freigeben()`/`onFertig`
  bleibt es `lesen`, bis der Nutzer klickt. Roter Test zuerst; danach 6b's Effekt
  entfernen (gehört dann 7b, weil 6b abgeschlossen ist — Files entsprechend erweitern:
  `LieferantDokumentModal.tsx` nur für diese eine Zeile).

#### Task 7d — Lieferant-Modal: neue Leisten-Props verdrahten (Runde 7-2, parallel zu 7a)
- Files: `react-pc-frontend/src/components/LieferantDokumentModal.tsx`,
  `LieferantDokumentModal.test.tsx`, `e2e/lieferant-dokument-modal.spec.ts`
- Consumes: 7c (`bearbeitenGesperrtGrund`, `zeigeNurLesenHinweis`), 7b (Hook-Modus)
- Herkunft: Bedenken aus 7c — beide Props sind gebaut und getestet, aber noch von keinem
  Verbraucher benutzt. Ohne Verdrahtung tauchen Tooltip und „Sie lesen nur mit." nie auf.
- Steps: bei `status === 'loading'` bzw. `'error'` einen Grund an `bearbeitenGesperrtGrund`
  geben (Handwerker-Sprache, z.B. „Sperre wird gerade geholt" / „Sperre konnte nicht geholt
  werden — bitte neu laden"); `zeigeNurLesenHinweis` im Modus `lesen` ohne fremden Halter.
  Je Prop roter Test zuerst, Spec-Zusicherung + `designPruefung` für beide Zustände.

#### Task 7c — BearbeitenLeiste nachschärfen (Befunde aus Design-Review 6)
- Files: `react-pc-frontend/src/components/lock/BearbeitenLeiste.tsx`,
  `BearbeitenLeiste.test.tsx`, `e2e/bearbeiten-leiste.spec.ts` (neu)
- Consumes: 5a (`kannBearbeiten`-Semantik: „ein Klick ist gerade sinnvoll")
- Herkunft: 🟡-Hinweise des Design-Reviewers in Abschnitt 6, plus der offene
  Kommentar-Fix aus Review 5.
- Steps:
  1. Prop-Kommentar zu `kannBearbeiten` auf die neue Bedeutung umschreiben.
  2. Neuer optionaler Prop `bearbeitenGesperrtGrund?: string` ⇒ `title` (Tooltip) am
     deaktivierten Bearbeiten-Knopf. `FRONTEND_UI.md`: deaktivierte Knöpfe erklären, warum.
  3. Die drei Bänder unterscheidbar machen: Countdown = Warnung (`amber`, mit Icon —
     ihm fehlt als einzigem eins), Verbindung weg = Störung (kräftigeres Rot),
     Nur-Lesen-Hinweis bleibt rose-50. Farben aus `handwerkerprogramm-design`.
  4. Layout: Platz für das Countdown-Band reservieren, damit `Fertig` beim Erscheinen
     nicht ~540 px nach links springt.
  5. Im Lesen-Modus nach eigenem „Fertig" die leere linke Hälfte benennen
     („Sie lesen nur mit.").
  6. **Anrede vereinheitlichen — Sie.** Die PC-Oberfläche siezt in 185 Strings und duzt in
     34 (gezählt am 04.09.2026); die Sperr-Bausteine aus Abschnitt 3 siezen. Der neue
     `TabSchliessenHinweis` (6a) duzt, weil die Spec-Zusammenfassung so formuliert war —
     das ist die Ausnahme, nicht die Regel. Text dort auf „Dokument gespeichert und
     freigegeben — Sie können diesen Tab jetzt schließen." ändern; Files um
     `components/lock/TabSchliessenHinweis.tsx` + Test + die 6a-Spec-Zusicherung erweitern.
     Entscheidung nach Konvention; der Nutzer kann sie umdrehen.
  Jeder Punkt mit rotem Test; Screenshots nach dem Ausklingen der Übergänge.

### Abschnitt 8 — in zwei Runden

> **Umgeschnitten nach Code-Review 7-2:** `document-editor/index.test.tsx` hängt noch an
> `useDocumentLock` (Seiten-Nachbau im Test, stubbt `/api/dokument-locks/`). Das Löschen der
> alten Dateien wäre allein nicht grün. Deshalb **Runde 8-1 = 8a + 8b parallel** (disjunkte
> Dateien), **Runde 8-2 = Aufräumen durch den Orchestrator**, wenn nichts mehr auf die alten
> Klassen zeigt.

#### ~~Task 8b~~ — aufgegangen in **7a Nachbesserung 1** (Design-Review 7-2 hat Punkt 1 und 2 als 🔴 gewertet; derselbe Agent, dieselben Dateien, gleiche Runde). Runde 8-1 ist damit nur noch 8a.

#### (ehemals 8b) Editor-Kopf und Seiten-Layout — Inhalt zur Nachvollziehbarkeit
- Files: `react-pc-frontend/src/components/document-editor/DocumentEditorHeader.tsx`,
  `document-editor/index.tsx`, `document-editor/types.ts`, `document-editor/index.test.tsx`,
  `pages/DocumentEditorPage.tsx`, `pages/DocumentEditorPage.test.tsx`,
  `e2e/dokument-editor-seite.spec.ts`
- Consumes: alles aus 7-2 (gemerged).
- Steps, je roter Test zuerst:
  1. **„Gebucht"-Badge nur bei wirklich gebuchten Dokumenten.** Heute kommt es aus
     `isLocked`, in das seit 6a auch `readOnly` fließt — bei Fremdsperre und Lock-Fehler
     steht „Gebucht" auf einem ungebuchten Dokument (auch storniert/digital angenommen
     tragen es fälschlich). Der Editor rechnet `gebucht && invoiceTypes.includes(typ)`
     schon aus (`index.tsx:~428`); diesen Ausdruck als eigenen Prop an den Header geben,
     `isLocked` bleibt fürs Deaktivieren. Nachweisbar: Fremdsperre ⇒ kein „Gebucht";
     gebuchtes Dokument ⇒ „Gebucht"; storniert ⇒ „Storniert" ohne „Gebucht".
  2. **Layout ohne `transform`-Trick.** Der `[transform:translateZ(0)]`-Container der Seite
     macht sich zum Containing Block für **alle** `fixed`-Nachfahren des Editors — 12
     Vollbild-Dialoge decken die Leiste nicht mehr ab, „Fertig" bleibt bei offenem
     Ungespeichert-Dialog klickbar und gibt die Sperre frei. Stattdessen: der Editor
     (`fixed inset-0`) bekommt seinen oberen Versatz explizit (z.B. CSS-Variable
     `--lock-leiste-hoehe`, von der Seite gesetzt, im Editor `top-[var(--lock-leiste-hoehe,0px)]`),
     der Container verliert das `transform`. Nachweisbar (Spec): bei offenem
     Ungespeichert-Dialog ist der „Fertig"-Knopf **nicht** anklickbar (Backdrop liegt
     darüber, `elementFromPoint` trifft den Backdrop); Leiste und Editor überlappen sich
     weiterhin nicht (`designPruefung`).
  3. `setSchliesstGerade(true)` in `onLockFreigebenFuerSchliessen` **vor** dem `await`, nicht
     danach (Hinweis Code-Review 7-2).
  4. X-Knopf im Header bekommt ein `aria-label` („Editor schließen") — seit 6a offen.
  5. **`index.test.tsx` von `useDocumentLock` lösen:** der Seiten-Nachbau im Test und die
     Stubs auf `/api/dokument-locks/` auf `useDatensatzLock` / `/api/datensatz-locks/`
     umstellen. Voraussetzung dafür, dass Runde 8-2 die alten Dateien löschen kann.

#### Task 8a — Toast, Confirm-Dialog, Konfliktmeldung und Hook nachschärfen — Runde 8-1
- Files: `react-pc-frontend/src/components/ui/toast.tsx`, `toast.test.tsx`,
  `react-pc-frontend/src/components/ui/confirm-dialog.tsx` (+ Test),
  `react-pc-frontend/src/components/lock/useKonfliktMeldung.ts`, `useKonfliktMeldung.test.tsx`,
  `e2e/toast-bei-dialog.spec.ts` (neu)
- Consumes: nichts Neues (alles fertig).
- Steps:
  1. Toast bei offenem Dialog: heute `top-6`, nur 4 px Luft zum Schließen-X des Modals.
     Ein zweizeiliger Toast (46 → 86 px) verdeckt X und „Vorschau aktiv" auf beiden
     Größen — gemessen. Nachweisbar: bei offenem Dialog und zweizeiligem Toast trifft
     `elementFromPoint` in der Mitte des Schließen-X das X. Lösung ist frei (mehr
     Abstand, oder Toast unter dem Modal-Kopf, oder links statt rechts).
  2. `confirm-dialog.tsx` trägt kein `role="dialog"` — der Toast-Umzug greift dort nicht,
     und Screenreader sehen keinen Dialog. `role="dialog"` + `aria-modal` + `aria-labelledby`.
  3. `useKonfliktMeldung.ts`: `variant: 'warning'` färbt „Neu laden" amber-500 — gefüllte
     Primäraktionen sind im System rose (`UnsavedChangesModal` macht es vor). Auf `'info'`
     oder das rose-Muster angleichen. Dazu toter Code: `eigeneMeldung || body?.message || '…'`
     — `eigeneMeldung` ist nie leer, die Fallbacks greifen nie. Entweder die
     Server-Meldung wirklich bevorzugen oder die Fallbacks streichen; Kommentar dazu
     ehrlich machen.
  5. **Aus Code-Review 7-1, 2. Durchgang** (Files um `components/lock/useDatensatzLock.ts`
     + Test erweitern): `releaseKeepalive()` (Unmount/`pagehide`) setzt `heldRef=false`,
     zieht aber `modus`/`status` nicht nach. Auf dem Cleanup-Pfad folgenlos, auf dem
     `pagehide`-Pfad überlebt der Zustand eine bfcache-Rückkehr: Formular bearbeitbar ohne
     Sperre (Backend weist den PUT ab, kein DB-Schaden). Zwei Zeilen dort schließen die
     Invariante; dazu ein `pageshow`-Test (bfcache-Rückkehr ⇒ `lesen`/`idle`, Bearbeiten
     holt neu). Und `pruefeInvariante` im Kettentest ist ein manuell aufgerufener Helfer —
     wenn es billig geht, als `afterEach`-Invariante über alle Hook-Tests legen.
  4. **Aus Code-Review 6, 2. Durchgang:** die Mutationsprobe am `MutationObserver` in
     `toast.tsx` beißt nicht — beide Positionierungstests rendern den Dialog schon beim
     Mount und treffen nur den `useState`-Initializer. Test ergänzen, der den Dialog
     **nach** dem Mount öffnet und wieder schließt (Container wandert `bottom-6` →
     `top-6` → `bottom-6`), plus ein Test für `observer.disconnect()` beim Unmount.
  6. **Aus Design-Review 7-2** (Files um `components/LieferantDokumentModal.tsx` + Test
     erweitern): „Sie lesen nur mit." erscheint im Modal auch neben dem Fehler- und
     Ladeband und drängt das rote Band zusammen (x 61–1250 → 61–1132) — die dringende
     Meldung weicht der beiläufigen. Die Seite nutzt die bessere Regel
     (`lock.status === 'idle'`, `DocumentEditorPage.tsx:~109`); das Modal
     (`LieferantDokumentModal.tsx:~119`) daran angleichen. Roter Test zuerst.
  Jeder Punkt roter Test zuerst; e2e-Spec mit `designPruefung` bei stehendem Toast.

#### Task 8c — letzte Politur aus Design-Review 7-2/8-1 (Runde 8-2, parallel zum Aufräumen)
- Files: `react-pc-frontend/src/components/ui/toast.tsx`, `toast.test.tsx`,
  `react-pc-frontend/src/components/ui/confirm-dialog.tsx` (+ Test),
  `react-pc-frontend/src/components/lock/useKonfliktMeldung.ts`, `useKonfliktMeldung.test.tsx`,
  `react-pc-frontend/e2e/toast-bei-dialog.spec.ts`, `e2e/dokument-editor-seite.spec.ts`
- Disjunkt vom Aufräumen (das löscht nur alte Dateien und schreibt eine Migration).
- Steps, je roter Test zuerst:
  1. **Toast bei offenem Dialog nach unten links.** Oben links überlappt ein zweizeiliger
     Toast auf 14 Zoll den Modal-Titel um 12 px (Toast bis y 90, Titel ab y 78;
     `elementFromPoint` auf Titel und Eyebrow trifft den Toast). Nach oben ausweichen geht
     nicht, das Modal beginnt bei y 57. Unten links trägt weder das Lieferant-Modal noch
     der Confirm-Dialog eine Aktion, und die Ecke bleibt frei, egal wie lang der Text wird.
     Nachweisbar: zweizeiliger Toast bei offenem Dialog ⇒ `elementFromPoint` auf Titel,
     Eyebrow, Schließen-X, Abbrechen und Speichern trifft jeweils das Element — beide Größen.
  2. **Vierte Confirm-Variante für Fehlschläge.** `variant: 'info'` bringt ein
     `HelpCircle` in sky-100/sky-600 mit — „Ihre Änderungen wurden nicht übernommen"
     trägt ein freundliches blaues Fragezeichen. Neue Variante in `confirm-dialog.tsx`
     (amber-`AlertTriangle` + rose-600-Knopf, wie `UnsavedChangesModal`), von
     `useKonfliktMeldung` benutzt. Die fünf bestehenden `'info'`-Aufrufer bleiben unberührt.
  3. **E2E-Fall mit `gebucht: true`** in `dokument-editor-seite.spec.ts`: das Badge hängt
     bisher nur an Unit-Tests; ein Stub ist eine Zeile, plus `designPruefung`.
- Nicht hier: das inline „Speichern fehlgeschlagen"-Band, das im Modal Felder verdeckt —
  vorbestehend, in die Restpunkte-Liste für ein Folge-Vorhaben.

## Abschluss (nach Runde 8-2)

1. **Letzter Review-Durchgang** über den kompletten Feature-Branch: Code-Reviewer (volle
   Suiten, Grep auf Reste des alten Systems, Migration V365 gegen die Flyway-Regeln,
   Startfähigkeit: Entity weg **und** Tabelle weg) und Design-Reviewer (volle E2E mit der
   strengeren Hilfe, 8c-Screens: Toast unten links, Fehlschlag-Variante, Gebucht-Fall).
2. Orchestrator: `./graphify update .` einmal am Ende; Worktrees weg.
3. **Zielprüfung (Schritt 7 des Skills, Orchestrator selbst):** kompletten PR-Diff gegen die
   Spec aus Issue #82 lesen — nur Absicht, keine Stildetails. Löst er das X-Button-Problem
   vollständig? Idle-Timer 5 Min / 60 s Vorwarnung? `SperrbarerTyp` als einziger Hebel für
   neue Typen? `@Version` + 409 auf allen Speicherwegen? Fünf Bausteine? Beide Verbraucher
   umgestellt, altes System weg? Nichts Themenfremdes?
4. PR gegen `main`, verlinkt mit #82. Kein Merge ohne Marvins Blick — der PR ist groß.

## Abschnitt 8-2 (Aufräumen — macht der Orchestrator selbst, parallel zu 8c) — ERLEDIGT
`37c4ea61` + `de7a9c5e`, gemerged als `90b769d7`. Siehe Kontext-Log „Abschnitt 8-2 — Aufräumen".

Zusätzlich nimmt der Orchestrator dabei den Halbsatz aus Code-Review 6 mit: in
`document-editor/index.tsx`, `handleSave`, Create-Zweig — `setDokument(created)` muss vor
`syncDocumentIdInUrl(created.id)` stehen und beide im selben React-Batch; Kommentar
„Reihenfolge nicht tauschen" mit Grund.

**Und `e2e/hilfen/design.ts` erweitern** — Beobachtung des Nutzers aus einem parallelen
Lauf (14-Zoll-Layout, in `playwright-design-pruefung/SKILL.md` unversioniert ergänzt): die
automatischen Checks übersehen drei echte Abschneide-Fehler, die im Browser sofort auffallen.
(1) `documentElement.scrollWidth` wächst nicht, wenn ein `main` oder ein Container mit
`overflow-x: hidden` den Überlauf verschluckt — auch `main.scrollWidth <= main.clientWidth`
messen und jedes `overflow-x: hidden`-Element gegen seinen eigenen `scrollWidth`.
(2) Text-Abschneiden auf Blatt-Elementen: `scrollWidth > clientWidth` bei Elementen mit Text.
(3) Elemente mit Breite 0 nicht überspringen — genau die sind der Fall. Mit Wegwerf-Probe
gegen einen echten Abschneide-Fall ausführen, dann committen. Spec des Nutzers dazu:
`docs/superpowers/specs/2026-09-04-layout-14-zoll.md` im Haupt-Checkout.

Löschen der alten Klassen UND die `DROP TABLE`-Migration gehören in **eine** Auslieferung,
sonst entsteht ein Zwischenstand, der nicht startet (Entity mappt eine Tabelle, die es
nicht mehr gibt — das Testprofil sieht das nicht).

- `domain/DokumentLock.java`, `dto/DokumentLockDto.java`, `repository/DokumentLockRepository.java`,
  `service/DokumentLockService.java`, `controller/DokumentLockController.java`,
  `service/DokumentLockServiceTest.java`
- `react-pc-frontend/src/components/DocumentLockedModal.tsx`,
  `react-pc-frontend/src/components/useDocumentLock.ts`
- neue Migration `DROP TABLE dokument_lock`
- Voraussetzung: Abschnitte 5–7 abgenommen, kein Verweis mehr im Code.

## Log

Siehe eigene Kontext-Log-Datei.
