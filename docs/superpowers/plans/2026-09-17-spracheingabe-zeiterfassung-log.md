# Kontext-Log: Spracheingabe in der mobilen Zeiterfassung

**Append-only.** Niemand ändert oder löscht bestehenden Text. Jeder hängt
unten einen neuen Block an, gesichert über das Lock-Protokoll.

**Absoluter Pfad dieser Datei** (nicht die Kopie im eigenen Worktree
beschreiben, sonst sieht sie niemand):

    /Users/marvinkuhn/dev/wt/spracheingabe/docs/superpowers/plans/2026-09-17-spracheingabe-zeiterfassung-log.md

Lock:

    LOG="/Users/marvinkuhn/dev/wt/spracheingabe/docs/superpowers/plans/2026-09-17-spracheingabe-zeiterfassung-log.md"
    LOCK="$LOG.lock"
    for i in $(seq 1 20); do mkdir "$LOCK" 2>/dev/null && break || sleep 1; done
    cat >> "$LOG" <<'BLOCK'
    ...dein Block...
    BLOCK
    rmdir "$LOCK"

Das Log committen die Agenten **nicht** — das macht der Orchestrator nach
jedem Abschnitt, sonst kollidiert jeder Merge am Dateiende.

---

## Rahmen

| | |
| --- | --- |
| Issue | #163 |
| Spec | `docs/superpowers/specs/2026-09-17-spracheingabe-zeiterfassung.md` |
| Plan | `docs/superpowers/plans/2026-09-17-spracheingabe-zeiterfassung.md` |
| Feature-Branch | `feature/spracheingabe-zeiterfassung` |
| Orchestrator-Worktree | `/Users/marvinkuhn/dev/wt/spracheingabe` |
| Haupt-Checkout | `/Users/marvinkuhn/dev/ERP-System-fuer-Handwerksbetriebe` (Branch `main`, **nicht anfassen**) |

## Baseline — gemessen am 17.09.2026 auf dem unveränderten Feature-Branch

    Backend   ./mvnw test      554 Testklassen, 3126 Tests
                               0 Failures, 0 Errors, 17 Skipped, Exit 0
    Frontend  npm test         22 Testdateien, 217 Tests, alle grün, Exit 0
    Lint      npm run lint     0 Fehler, Exit 0

**Abnahmeregel: alles grün.** Es gibt keine bekannten Vorschäden. Jeder
Fehler ist neu und gehört dem, der ihn verursacht hat.

## Umgebung — geprüft, gilt für alle Agenten

- Java/Maven über den Wrapper `./mvnw` im Repo-Wurzelverzeichnis. Läuft.
- Node/npm vorhanden. `npm ci` ist **nicht** nötig und soll **nicht**
  laufen — jedes Frontend-Worktree bekommt einen Symlink auf das
  `node_modules` des Haupt-Checkouts, den der Orchestrator anlegt.
- Playwright-E2E-Tests: nur in Abschnitten mit sichtbarer Oberfläche
  (ab Abschnitt 4). Niemals parallel zu einem laufenden Maven-Lauf.
- `src/main/resources/static/index.html` zeigt in jedem frischen Worktree
  einen Phantom-Diff durch Zeilenende-Normalisierung. **Kein Befund.**
  Nicht committen, nicht reparieren, nicht im Report melden.
- Generierte Build-Artefakte unter `src/main/resources/static/zeiterfassung/`
  gehören **nicht** in die Task-Commits. `npm run build` ist Fail-Fast-Prüfung;
  die Artefakte danach verwerfen. Den echten Build macht der Orchestrator
  einmal am Ende.
- Frontend-Edits sind hook-geschützt: vorher `docs/agent instructions/docs/FRONTEND_UI.md`
  per Read laden UND den Skill `handwerkerprogramm-design` aufrufen
  (Skill-Name **ohne** Namespace-Präfix). Backend-Edits brauchen
  `BACKEND_ARCH.md`, Test-Dateien `TESTING_SECURITY.md`.

## Abschnitte

| Abschnitt | Tasks | Design-Review |
| --- | --- | --- |
| 1 | 1, 3, 5, 6 | nein |
| 2 | 2, 4 | nein |
| 3 | 7 | nein |
| 4 | 8, 9, 10 | ja |
| 5 | 11, 12, 13 | ja |

---

## Verlauf

### 17.09.2026 — Orchestrator, vor Abschnitt 1

Vorbereitung abgeschlossen. Spec (`eb202c76`), Issue #163 (`e8f98135`),
Grobplan (`8e3e0aac`) und Abschnittseinteilung liegen auf dem
Feature-Branch und sind nach `origin` gepusht.

`main` wurde in den Feature-Branch geholt, bevor die ersten Worktrees
entstanden. Grund: `main` hatte sich nach dem Abzweigen um zwei Commits
bewegt — PR #162 (Dokumenteditor) und den Merge des iOS-Safe-Area-Fixes.
Letzterer hat `DashboardPage.tsx` geändert, dieselbe Datei, in die Task 10
das Zahnrad setzt. Ohne diesen Schritt hätten die Coding-Agenten auf einem
veralteten Stand gearbeitet.

Abweichung vom Grobplan-Vorschlag: Die dort vorgeschlagene Runde `{2, 4, 7}`
verletzt Randbedingung 5 desselben Kapitels — Task 7 importiert das Modul,
das Task 4 erst anlegt. Task 7 hat deshalb eine eigene Runde bekommen.
Fünf Abschnitte statt vier.

## Abschnitt 1 — Task 6 (Coding-Agent)

Zeit: 2026-09-17T22:10:21Z
Branch: sprach/task-6-notification-bootstrap
Commit(s): f3de79a9
Status: fertig

Was gemacht wurde:
- Neues Modul `react-zeiterfassung/src/services/notificationBootstrap.ts` angelegt: die bisher komponentenlokale Funktion `initializeNotifications` aus `App.tsx:183-217` herausgelöst und in zwei Funktionen aufgetrennt: `starteBenachrichtigungenFallsErlaubt(token)` (fragt NIE von sich aus, läuft nur wenn Status bereits `granted`) und `frageBenachrichtigungenAn(token)` (löst `requestPermission()` bewusst aus, für den künftigen "Erlauben"-Knopf der Einstellungsseite). Beide führen bei Erfolg dieselbe private `starteKette(token)` aus: `storeTokenForSW` → `subscribeToPush` → `registerPeriodicSync` → `loadAndCheck` → 5-Minuten-`setInterval`, das den jeweils aktuellen Token aus `localStorage` liest. Intervall-Id als Modul-Variable `intervallId`, Muster von `cameraStreamService.ts` (Modul-globaler Zustand statt Komponenten-Ref).
- `stoppeBenachrichtigungsIntervall()` ergänzt (beendet das Polling, mehrfach aufrufbar, für den künftigen Abmelde-Fall).
- `App.tsx` bewusst NICHT angefasst — das ist Task 9. Das neue Modul existiert bis dahin ungenutzt parallel zur alten Funktion, lauffähiger Zwischenstand.
- Charakterisierungstests `notificationBootstrap.test.ts` (10 Tests, `vi.mock('./NotificationService')` + `vi.useFakeTimers()`): kein `requestPermission`-Aufruf bei Status `default`/`denied`; komplette Kette inkl. aller vier NotificationService-Aufrufe + Rückgabe `true` bei `granted`; Intervall-Tick nach 5 Minuten mit dem dann aktuellen (auch gewechselten) Token; kein weiterer Tick nach `stoppeBenachrichtigungsIntervall()`; zweimaliger Start hinterlässt nur ein Intervall (kein doppelter Tick); `frageBenachrichtigungenAn` startet die Kette nur bei Zustimmung.
- Gates: `npm test` 23 Testdateien / 227 Tests, alle grün, Exit 0 (Baseline 22/217 + meine 1 Datei/10 Tests, keine sonstige Abweichung). `npm run lint` 0 Fehler, Exit 0. `npm run build` erfolgreich; Build-Artefakte danach geprüft/verworfen (git status zeigte dort ohnehin keine Änderung, weil das neue Modul von noch niemandem importiert wird und im Bundle nicht auftaucht).

Bedenken / Abweichungen vom Plan:
- Widerspruch innerhalb der Planungsunterlagen selbst (nicht mein Task-Inhalt, aber relevant für alle Frontend-Agenten): Die Plandatei behauptet unter "Global Constraints" (Zeile 98-102), `handwerkerprogramm-design` OHNE Namespace-Präfix scheitere mit "Unknown skill" und nur `wt/<worktree-name>:handwerkerprogramm-design` funktioniere. Das Gegenteil war der Fall: `Skill(handwerkerprogramm-design)` ganz ohne Präfix hat in dieser Session direkt funktioniert. Das deckt sich mit dem "Umgebung"-Abschnitt dieses Kontext-Logs weiter oben (Zeile 62-65: "Skill-Name OHNE Namespace-Präfix") und mit meinem eigenen Auftragstext. Empfehlung: den Satz in den Global Constraints der Plandatei korrigieren, bevor sich weitere Agenten daran orientieren und unnötig einen Fallback-Umweg nehmen.
- Der generische Pflicht-Skill `superpowers:test-driven-development` aus dem Coding-Agent-Auftragstext ist in diesem Projekt nicht registriert (`Unknown skill`). Nach TDD-Prinzip trotzdem manuell vorgegangen: Test zuerst geschrieben, roten Fehlschlag (Modul fehlt) verifiziert, dann implementiert, danach grün verifiziert.
- Inhaltlich keine Abweichung von Task 6: alle in "Produces" genannten Signaturen, die Steps-Reihenfolge und alle geforderten Testfälle sind wie im Plan umgesetzt. Einzige bewusste Nicht-1:1-Übernahme: zwei rein kosmetische `console.log`-Zeilen aus dem alten `App.tsx`-Code ("Notification permission granted" / "not granted", die quittierten, dass die einmalige Anfrage erfolgreich war) haben nach der Aufspaltung in zwei Aufrufer keinen eindeutigen Platz mehr und wurden weggelassen; die inhaltlich relevanten Logs (Web-Push-Abo aktiv vs. Fallback-Polling) blieben unverändert erhalten. Betrifft nur Konsolen-Ausgaben, keine geprüfte oder im Plan geforderte Verhaltenslogik.

## Abschnitt 1 — Task 1 (Coding-Agent)

Zeit: 2026-09-17T22:13:53Z
Branch: sprach/task-1-backend-service
Commit(s): 9f8536b8
Status: fertig

Was gemacht wurde:
- `dto/SpracheingabeErgebnis.java` (neu): Record mit einem Feld `text`, Javadoc nach Muster von `dto/Beitraege/BeitragKiAnfrage.java`.
- `service/SpracheingabeService.java` (neu): Doppel-Konstruktor wie `BeitragKiService` (Autowired-Konstruktor baut den `HttpClient` selbst mit 15s Connect-Timeout, paketprivater Test-Konstruktor nimmt ihn entgegen). `SYSTEM_ANWEISUNG` wörtlich aus dem Plan übernommen (ASCII-Umlaute), inklusive der Regel `[unverstaendlich]` statt Raten. `baueKoerper()`: genau eine `user`-Runde mit genau einem `inlineData`-Part (camelCase wie `GeminiScannerService`, kein begleitender Text-Part), `generationConfig` mit `temperature 0.0` / `responseMimeType "text/plain"`, kein `responseSchema`. `normalisiereInhaltstyp()` ohne Regex (kein ReDoS-Risiko) — schneidet am ersten `;` ab, trimmt, lowercased. `leseHoechstens()` liest blockweise in einen `ByteArrayOutputStream` und wirft `AufnahmeZuGross` sofort beim Überschreiten von `MAX_AUDIO_BYTES`, ohne vorher alles zu puffern. `transkribiere()` prüft in der im Plan vorgegebenen Reihenfolge: Inhaltstyp → Größe/Leere → API-Schlüssel → Gemini-Aufruf. Bei Gemini-HTTP-Fehler wird ausschließlich `log.warn("[Spracheingabe] Gemini HTTP {} ({} Bytes, {})", status, audio.length, inhaltstyp)` geloggt, nie `antwort.body()` (das wäre das Transkript). Klassenkommentar mit Datenschutz-Absatz analog `BeitragKiService.java:33-46`, angepasst auf Sprachaufnahmen von Mitarbeitern im Arbeitsverhältnis.
- `application.properties`: neuer Schlüssel `ai.gemini.model.spracheingabe=gemini-flash-latest` direkt bei den anderen `ai.gemini.model.*`-Einträgen ergänzt (nach `ai.gemini.model.pro`, vor dem Kommentar zur E-Mail-KI). Zwei Zeilen, sonst keine Änderung an der Datei.
- `SpracheingabeServiceTest.java` (neu, 9 Tests): alle im Plan geforderten Fälle abgedeckt — Kernregeln der Systemanweisung, Aufbau des Gemini-Körpers (ein `inlineData`-Part, kein Text-Part), `generationConfig` ohne Schema, Codec-Abschneiden inkl. `null`, Ablehnung fremder Inhaltstypen (`image/png`, `application/pdf`), `AufnahmeZuGross` bei `MAX_AUDIO_BYTES + 1` Bytes, ehrlicher `IllegalStateException`-Fehler ohne API-Schlüssel (HttpClient bleibt dabei unangetastet, per `verifyNoInteractions` geprüft), Gemini-Fehlerkörper landet nicht im Klartext der eigenen Fehlermeldung, Antworttext kommt getrimmt zurück.
- TDD manuell durchgeführt (siehe Bedenken zum Skill): Stub-Service angelegt, dessen Methoden nur Platzhalter werfen/liefern; alle 9 Tests liefen rot mit den erwarteten, inhaltlich passenden Gründen; danach die echte Implementierung geschrieben; alle 9 Tests liefen grün.
- `./mvnw -B test -Dtest=SpracheingabeServiceTest`: 9 Tests, 0 Failures, 0 Errors, Exit 0.
- Zusätzlich zur regulären Abnahmeregel den vollen `./mvnw test` gefahren, weil der eigene Auftragstext das für diesen Task ausdrücklich verlangt: 3135 Tests, 0 Failures, 0 Errors, 17 Skipped, Exit 0, BUILD SUCCESS, 555 Testklassen (Baseline 554 + meine neue Klasse). Differenz zur Baseline exakt +9 Tests (meine eigenen), Skipped-Zahl unverändert bei 17 — keine Regression irgendwo im Backend.

Bedenken / Abweichungen vom Plan:
- Der generische Pflicht-Skill `superpowers:test-driven-development` aus dem Coding-Agent-Auftragstext ist in dieser Session nicht registriert (`Unknown skill`). Deckt sich mit dem bereits weiter oben in diesem Log dokumentierten Befund von Task 6. Stattdessen TDD-Disziplin manuell angewendet: Stub → roter Lauf mit Prüfung der Fehlgründe → echte Implementierung → grüner Lauf (siehe oben).
- `./graphify` existiert in meinem Worktree nicht (weder Wrapper-Skript noch `.graphify-venv/` — nur der Ordner `graphify-out/` selbst ist vorhanden). Konnte deshalb weder für gezielte Einzelfragen noch für das abschließende `graphify update .` genutzt werden. War hier auch nicht nötig: der Plan nannte für jede Vorbild-Referenz bereits konkrete Datei und Zeilenbereich, ich bin ausschließlich per `Read` (bzw. einmal `find` für die reine Pfadauflösung bekannter Dateinamen) auf exakt benannte Pfade zugegangen — die im CLAUDE.md explizit erlaubte Ausnahme "Du kennst den exakten Dateipfad → Read direkt", keine explorative Grep/Glob-Suche über die Codebase.
- Test-Detail (keine Plan-Abweichung, nur der Vollständigkeit halber): Im eigenen Test stubt `geminiFehlerAntwortLandetNichtInDerFehlermeldung` auch `antwort.body()`, das die Implementierung im Fehlerfall (HTTP ≥ 400) bewusst NIE liest (DSGVO-Vorgabe aus dem Plan, kein Transkript im Log/in der Exception). Mockito hat den ungenutzten Stub zunächst per Strict-Stubbing als `UnnecessaryStubbing` abgelehnt — tatsächlich der Beweis, dass die Implementierung korrekt ist. Mit `Mockito.lenient()` auf genau diesem einen Stub behoben (der 200-Erfolgsfall im selben Test-Helper braucht ihn echt).
- Inhaltlich keine Abweichung von Task 1: alle Signaturen aus "Interfaces", die Steps-Reihenfolge in `transkribiere()` und alle geforderten Testfälle sind wie im Plan umgesetzt. Wortlaut der `SYSTEM_ANWEISUNG` 1:1 aus dem Plan übernommen, keine redaktionelle Änderung.
- `src/main/resources/static/index.html` nicht angefasst und nicht committet (bekannter Phantom-Diff laut Auftrag, kein Befund).

## Abschnitt 1 — Task 3 (Coding-Agent)

Zeit: 2026-09-17T22:14:40Z
Branch: sprach/task-3-audio-recorder
Commit(s): cb612ee0
Status: fertig

Was gemacht wurde:
- Neues Modul `react-zeiterfassung/src/services/audioRecorderService.ts`: `mikrofonWirdUnterstuetzt()`, private `waehleMimeType()`, `starteAufnahme()` mit dem Interface `AufnahmeSitzung` (`mimeType`, `gestartetAm`, `stoppe()`, `brichAb()`) und `MikrofonNichtErlaubtError` — alle Signaturen 1:1 aus dem Plan übernommen.
- Zentrale Entscheidung des Tasks umgesetzt und als Kopfkommentar dokumentiert: KEIN Modul-Cache für den Mikrofon-Stream (Gegenbeispiel zu `cameraStreamService.ts`). Jede Aufnahme ruft `getUserMedia` frisch auf; `stoppe()` und `brichAb()` geben die Tracks sofort wieder frei (`getTracks().forEach(t => t.stop())`, in `try/catch` wie `cameraStreamService.releaseCameraStream()`). Zweiter Aufruf von `stoppe()` liefert dieselbe zwischengespeicherte Promise, ohne den Recorder erneut zu stoppen (verifiziert per `vi.spyOn(recorder, 'stop')` — genau 1 Aufruf).
- `waehleMimeType()` iteriert `['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/aac']` per `MediaRecorder.isTypeSupported`; liefert `''`, wenn `MediaRecorder` oder `isTypeSupported` fehlt (iOS/Safari) — Browser wählt dann selbst. Sitzungs-`mimeType` = `recorder.mimeType || gewaehlterTyp || 'audio/webm'`.
- Fehlerbehandlung in `starteAufnahme()`: `.name` wird über eine generische `'name' in fehler`-Prüfung gelesen, bewusst NICHT über `instanceof Error` — echte `DOMException` (das, was `getUserMedia` in echten Browsern bei Ablehnung wirft) erbt in keiner Browser-Implementierung von `Error`, ein `instanceof Error`-Gate hätte `NotAllowedError`/`PermissionDeniedError` also am echten Objekt vorbeilaufen lassen und nur in Tests mit einem angereicherten `new Error(...)` funktioniert. `NotAllowedError`/`PermissionDeniedError` → `MikrofonNichtErlaubtError`, alles andere unverändert weitergeworfen.
- `audioRecorderService.test.ts` mit Fake-`MediaRecorder` (statisches `instances`-Register, damit Tests an die intern erzeugte Instanz kommen, plus umschaltbares `isTypeSupported`) und Fake-Stream im Teststil von `cameraStreamService.test.ts` (`vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } })`, zusätzlich `vi.stubGlobal('MediaRecorder', FakeMediaRecorder)`): 15 Tests, decken alle im Plan geforderten Fälle ab (Blob mit gesammelten Chunks + gemeldetem mimeType; Kernprüfung Tracks `'ended'` nach `stoppe()`; zwei Aufnahmen → zwei `getUserMedia`-Aufrufe; `brichAb()` stoppt Tracks ohne Blob und ist mehrfach aufrufbar; `NotAllowedError`/`PermissionDeniedError` → `MikrofonNichtErlaubtError`; `mikrofonWirdUnterstuetzt() === false` ohne `MediaRecorder`) plus einige zusätzliche (mimeType-Auswahl inkl. Fallback, `gestartetAm`-Zeitstempel, andere Fehler unverändert weitergeworfen, `getUserMedia` fehlt).
- TDD manuell befolgt (siehe Bedenken): Testdatei zuerst geschrieben, roten Fehlschlag verifiziert (`Failed to resolve import "./audioRecorderService"`), dann implementiert, danach grün.
- Gates: `npx vitest run src/services/audioRecorderService.test.ts` 15/15 grün. Volles `npm test`: 23 Testdateien / 232 Tests, alle grün, Exit 0 (Baseline 22/217 + 1 Datei/15 Tests, keine sonstige Abweichung). `npm run lint` 0 Fehler, Exit 0. `npm run build` erfolgreich (`tsc -b && vite build`); Build-Artefakte danach per `git status` geprüft — keine Änderung vorhanden (nichts zu verwerfen), `index.html`-Phantom-Diff ebenfalls nicht aufgetreten.
- `./graphify update .` **nicht** ausgeführt — siehe Bedenken.

Bedenken / Abweichungen vom Plan:
- Die 2-Minuten-Obergrenze und die "Aufnahme unter 1 Sekunde verwerfen"-Regel aus meinem Auftragstext ("Weitere Festlegungen aus dem Brainstorming") habe ich bewusst NICHT in `audioRecorderService.ts` implementiert. Der maßgebliche Plan-Block zu Task 3 (Zeile 462-539) verlangt dafür nur das Feld `gestartetAm` ("Grundlage fuer die 1s- und 2min-Regel") — die tatsächliche Durchsetzung liegt laut Plan sichtbar bei einer anderen Komponente (Grep auf `gestartetAm`/`2 Minuten` im Plan trifft auf Zeile 809/823/835/837, außerhalb des Task-3-Blocks, mit `Date.now() - sitzung.gestartetAm`, `vi.useFakeTimers()` und explizitem Aufruf von `stoppe()` nach 120000ms — vermutlich Task 5/`VoiceInputButton.tsx`). Passt zum Auftragstext-Hinweis "Dieser Auftragstext ergänzt ihn nur" — der Plan-Block ist maßgeblich, dort steht dazu nichts für diese Datei. Bitte gegenprüfen, dass die konsumierende Komponente das tatsächlich abdeckt.
- `./graphify update .` konnte in diesem isolierten Task-Worktree nicht laufen: `./graphify`, `graphify.cmd` und `.graphify-venv/` sind laut `.gitignore` (Zeilen zu `.graphify-venv/`, `/graphify`, `/graphify.cmd`) bewusst nur lokale, ungetrackte Werkzeuge im Haupt-Checkout — ein per `git worktree add` frisch angelegtes Worktree bekommt sie nicht mit (nur `graphify-out/graph.json` und `GRAPH_REPORT.md` sind getrackt und damit vorhanden). Da ich laut Auftrag ausschließlich in diesem Worktree arbeiten und den Haupt-Checkout nicht anfassen soll, konnte ich den Graphen nicht selbst aktualisieren. Vermutlich muss das zentral (Haupt-Checkout oder Orchestrator-Worktree) nach dem Merge aller Task-Branches einmal laufen.
- Bestätige den Befund des Task-6-Agenten weiter oben in diesem Log: `Skill(handwerkerprogramm-design)` funktioniert in dieser Session direkt ohne Namespace-Präfix; die gegenteilige Behauptung in den Global Constraints der Plandatei (Zeile 98-102, "der unscoped Name scheitert mit 'Unknown skill'") trifft hier nicht zu.
- Ebenfalls bestätigt: Der im generischen Coding-Agent-Auftragstext verlangte Skill `superpowers:test-driven-development` (auch ohne Namespace-Präfix versucht) ist in dieser Session nicht registriert ("Unknown skill"). Nach TDD-Prinzip trotzdem manuell vorgegangen (rot → grün, siehe oben).
- Keine inhaltliche Abweichung von den in Task 3 vorgegebenen Signaturen, Steps oder geforderten Testfällen.

## Abschnitt 1 — Task 5 (Coding-Agent)

Zeit: 2026-09-17T22:16:07Z
Branch: sprach/task-5-permission-status
Commit(s): d206715d
Status: fertig

Was gemacht wurde:
- Neue Datei `react-zeiterfassung/src/services/permissionStatusService.ts` mit exakt den im Plan genannten fünf Exports: `leseBenachrichtigungsStatus()` (sync, mappt `NotificationService.isSupported()`/`getPermissionStatus()` auf `BerechtigungsStatus`), private `leseGeraeteStatus(name: 'microphone'|'camera')` + die beiden öffentlichen Wrapper `leseMikrofonStatus()`/`leseKameraStatus()` (async, mappen `navigator.permissions.query({name})`), `frageMikrofonAn()` (`getUserMedia({audio:true})` → alle Tracks sofort wieder stoppen, kein Cache, Fehler unverändert weitergeworfen) und `frageKameraAn()` (`acquireCameraStream`/`releaseCameraStream` aus dem bestehenden `cameraStreamService`, Release in `finally`).
- Zentrale Entscheidung 3 umgesetzt und im Code kommentiert: JEDER unbrauchbare Fall bei Mikro/Kamera — API fehlt, `query()` wirft, unbekannter `state`-Wert, Auflösung mit `undefined` — mappt auf `'nicht-gefragt'`, niemals auf `'erlaubt'` oder `'blockiert'`. Fehlt nur `navigator.mediaDevices.getUserMedia`, ist das `'nicht-verfuegbar'` (Gerät kann es grundsätzlich nicht), fehlt nur `permissions.query`, bleibt es `'nicht-gefragt'` (Gerät könnte es, wir wissen es nur nicht) — dieser Unterschied ist die eigentliche Pointe des Tasks.
- Testdatei mit 19 Tests: alle drei `permissions.query`-States, die geforderten Unglücksfälle (API fehlt ganz, wirft, liefert `{state:'irgendwas'}`, löst mit `undefined`), `mediaDevices` fehlt komplett vs. nur `getUserMedia` fehlt, `frageMikrofonAn` stoppt alle Tracks auch wenn ein einzelner `stop()` wirft, Fehlerpropagation bei Ablehnung für Mikro und Kamera, und alle vier Benachrichtigungs-Abbildungen inkl. `'unsupported'`.
- Für die Benachrichtigungs-Mappings `vi.spyOn(NotificationService, ...)` statt `vi.stubGlobal('navigator', …)` verwendet: `isSupported()` und `getPermissionStatus()` prüfen intern beide dieselbe Bedingung (`'Notification' in window`), d.h. der `'unsupported'`-Zweig in `getPermissionStatus()` ist über echte Browser-Globals hinter dem `isSupported()`-Vorcheck praktisch nie erreichbar. Spy auf die beiden Methoden entkoppelt das und macht den vierten Mapping-Zweig direkt testbar — inhaltlich deckungsgleich mit dem Plan ("alle vier Abbildungen inkl. unsupported"), nur mit dem direkteren Werkzeug.
- Da der im Auftragstext geforderte Skill `superpowers:test-driven-development` nicht registriert ist (siehe Bedenken), TDD-Geist ersatzweise per gezielter Mutation nachgewiesen: für die drei zentralen Zweige (catch-Handler, "permissions fehlt"-Zweig, "alle Tracks stoppen"-Schleife) je eine falsche Version eingesetzt, verifiziert dass genau die erwarteten Tests rot werden (2, 1, 2 Treffer — keine Überraschungen), dann auf die korrekte Version zurückgesetzt.
- Gates: `npm test` 23 Testdateien / 236 Tests, alle grün, Exit 0 (Baseline 22/217 + meine 1 Datei/19 Tests, keine sonstige Abweichung). `npm run lint` 0 Fehler, Exit 0. `npm run build` erfolgreich — bestätigt nebenbei, dass der erschöpfende `switch` ohne `default` in `leseBenachrichtigungsStatus()` unter `strict` fehlerfrei compiliert. Build-Output zeigte keinen Diff (Service wird von niemandem importiert, das folgt erst in Task 7/8).

Bedenken / Abweichungen vom Plan:
- Bestätige unabhängig den bereits von Task 6 gemeldeten Befund: `superpowers:test-driven-development` ist in dieser Session nicht registriert (`Unknown skill`, auch ohne Namespace-Präfix probiert). Ebenso bestätigt: `handwerkerprogramm-design` OHNE Namespace-Präfix hat direkt funktioniert, entgegen der Behauptung in den Global Constraints der Plandatei (Zeile ~100), der unscoped Name scheitere. Beides deckt sich 1:1 mit dem Abschnitt-1-Block von Task 6 weiter oben — zwei unabhängige Bestätigungen für dieselben zwei Korrekturen an Plandatei/Auftragstext.
- Inhaltlich keine Abweichung von Task 5: alle fünf Exports, die Steps-Reihenfolge und alle im Plan aufgezählten Testfälle sind wie beschrieben umgesetzt. Einzige nicht wörtliche Übernahme: die beiden Wrapper `leseMikrofonStatus`/`leseKameraStatus` sind im Plan als `async function` skizziert, delegieren bei mir aber lediglich per `return leseGeraeteStatus(...)` an die private Funktion — verhält sich identisch (kein doppeltes Promise-Wrapping), ist aber kürzer als eine eigene `await`-Zeile pro Wrapper.

## Abschnitt 1 — Review (Abschnitts-Reviewer)

Zeit: 2026-09-18T00:47:00Z
Branch: feature/spracheingabe-zeiterfassung (gemergter Stand, 4 Merge-Commits d6c2acb0/975ab3f3/b011228a/2fcdd3c6)
Commit(s): —
Status: 🔴 — ein blockierender Befund (Task 3)

Selbst gefahrene Gates:
- Backend `./mvnw -B test`: 555 Testklassen, 3135 Tests, 0 Failures, 0 Errors, 17 Skipped, Exit 0 — exakt die Erwartung (Baseline 554/3126 + 1/9).
- Frontend `npm test`: 25 Dateien, 261 Tests, alle grün, Exit 0 — exakt die Erwartung (22/217 + 15 + 19 + 10).
- Frontend `npm run lint`: 0 Fehler, Exit 0.
- `App.tsx` im Abschnitts-Diff nachweislich unangetastet (`git diff ca470a94..HEAD -- react-zeiterfassung/src/App.tsx` leer) — Task 6 hat sich korrekt darauf beschränkt, nur herauszulösen.

🔴 Blockierend:
- Task 3, `audioRecorderService.ts:63-86`: Wirft irgendetwas NACH dem erfolgreichen
  `getUserMedia`, wird der Mikrofon-Stream nie freigegeben. Betrifft drei reale Pfade:
  `new MediaRecorder(...)` wirft `NotSupportedError` (Zeile 76), `recorder.start()` wirft
  (Zeile 85), und `MediaRecorder` global nicht vorhanden (altes iOS/Safari — `starteAufnahme()`
  prüft `mikrofonWirdUnterstuetzt()` nicht selbst). Empirisch mit einer Wegwerf-Testdatei
  belegt: Track bleibt in allen drei Fällen auf `'live'` statt `'ended'`. Genau das Symptom,
  das Entscheidung 1 des Plans verhindern soll (oranger iOS-Aufnahmepunkt bleibt stehen).
  Kein bestehender Test deckt diesen Pfad ab.

🟡 Hinweise (blockieren nicht):
- Task 1, `SpracheingabeServiceTest.java:112-117`: `fremderInhaltstypWirdAbgewiesen` prüft mit
  einem LEEREN Stream — ein durchgelassener Inhaltstyp würde an „Die Aufnahme ist leer."
  scheitern, ebenfalls `IllegalArgumentException`. Mutationsprobe (`image/png` in die Whitelist)
  überlebt alle 9 Tests. Die Whitelist ist damit faktisch ungetestet.
- Task 1, `SpracheingabeServiceTest.java:64/139-146`: Mutationsprobe „`antwort.body()` in die
  `log.warn`-Zeile" überlebt alle 9 Tests. `Mockito.lenient()` sichert das NICHT ab — es
  unterdrückt nur die `UnnecessaryStubbingException`. Die Selbstauskunft in diesem Log
  (Task 1, „Test-Detail") ist insoweit zu korrigieren. Die Implementierung selbst ist korrekt.
- Task 1: Mutationsprobe „Größenprüfung erst NACH dem Vollpuffern" überlebt — der
  Streaming-Charakter der 10-MB-Grenze ist ungetestet. Implementierung korrekt
  (`SpracheingabeService.java:198-213`, Abbruch in der Leseschleife).
- Task 1: In `SYSTEM_ANWEISUNG` sind die Reintext-Verbotszeile („Keine Sternchen, keine Rauten,
  keine HTML-Tags, keine Markdown-Syntax") und der Metallbau-Vokabelblock (Feuerverzinkung, VSG,
  ESG …) ungetestet — beide Mutationen überleben. `HEB 200` trifft nur die Profile-Zeile.
- Task 6: Mutationsprobe „`intervallId = null` weglassen" überlebt (9 von 10 gefangen). Harmlos.

Geprüft und in Ordnung befunden:
- Systemanweisung wörtlich identisch mit der Plan-Vorgabe (mechanischer Diff) — `[unverstaendlich]`,
  Reintext-Regel und Metallbau-Vokabular sind alle drin.
- DSGVO: einzige Log-Zeile des Backend-Dienstes enthält nur Status/Bytes/Inhaltstyp. Zusätzlich
  geprüft, ob das Transkript über die Jackson-Parse-Ausnahme entkommen kann (Jackson 2.15.4 hat
  `INCLUDE_SOURCE_IN_LOCATION` an): nicht ausnutzbar — abgeschnittenes JSON liefert
  `JsonEOFException` ohne Quelltext-Auszug, gültiges Präfix parst durch. Keine Secrets im Diff,
  keine `console.log` mit Nutzinhalt, nur Dummy-Daten in den Tests.
- 10-MB-Grenze greift beim Lesen, nicht danach (an der Implementierung geprüft).
- Task 5: 6 eigene Mutationsproben, alle gefangen. Kein Pfad behauptet je „erlaubt"/„blockiert",
  wenn der Status nicht ermittelbar war.
- Task 6: Kette 1:1 gegen `App.tsx:186-212` verglichen — Reihenfolge, Schlüssel
  `zeiterfassung_token`, 5-Minuten-Intervall mit frisch gelesenem Token identisch. 9 von 10
  eigenen Mutationsproben gefangen.

## Abschnitt 1 — Task 3, Nachbesserung (Coding-Agent)

Zeit: 2026-09-17T22:38:25Z
Branch: sprach/task-3-audio-recorder
Commit(s): 39a7ebf7 (auf cb612ee0, dem urspruenglichen Task-3-Commit)
Status: fertig

Befund (🔴, blockierend): Mikrofon-Stream blieb offen, wenn zwischen erfolgreichem
`getUserMedia` und dem Zurückgeben der `AufnahmeSitzung` etwas wirft (Konstruktor
`new MediaRecorder()` → `NotSupportedError`, `recorder.start()` →
`InvalidStateError`/`SecurityError`, oder `MediaRecorder` fehlt global auf altem
iOS) sowie gleiche Ursache in `stoppe()`, wenn `recorder.stop()` synchron wirft.

Was gemacht wurde:
- Testgetrieben nachgebessert: zuerst die drei geforderten Tests gegen den
  unveränderten Code geschrieben, Rot verifiziert (`expected 'live' to be 'ended'`
  in allen drei Fällen, exakt wie im Befund beschrieben), dann den Fix umgesetzt,
  danach Grün verifiziert.
- `starteAufnahme()`: der Block von `waehleMimeType()`-Ergebnis bis
  `recorder.start()` steht jetzt in `try/catch`. Im `catch`: `gibMikrofonFrei(stream)`
  aufrufen, dann den Originalfehler unverändert weiterwerfen (`throw fehler`, kein
  eigener Fehlertyp). `recorder`, `mimeType`, `gestartetAm` dafür als `let` ohne
  Initializer deklariert und im try-Block zugewiesen (gleiches Muster wie bereits
  bei `stream` im ursprünglichen Code).
- `stoppe()`: `recorder.stop()` steht jetzt in einem eigenen `try/catch` innerhalb
  des Promise-Executors; im Fehlerfall `gibMikrofonFrei(stream)` **und** `reject(fehler)`
  (Promise bekam dafür einen zweiten Parameter `reject`).
- Drei neue Tests in `audioRecorderService.test.ts` (neue `describe`-Gruppe
  "Mikrofon-Freigabe, wenn nach getUserMedia noch etwas wirft"): werfender
  MediaRecorder-Konstruktor, werfendes `start()` (Subklasse von `FakeMediaRecorder`),
  `MediaRecorder` global `undefined`. Jeder Test prüft sowohl `rejects.toBe(<Original-
  fehlerobjekt>)` (bzw. bei Fall C nur `rejects.toThrow()`, da der Fehler dort aus der
  JS-Runtime selbst kommt) als auch `readyState === 'ended'` auf dem per
  `getUserMedia`-Mock erzeugten Fake-Stream.
- Gates in diesem Worktree: `npx vitest run src/services/audioRecorderService.test.ts`
  18/18 grün (15 vorher + 3 neu). Volles `npm test`: **23 Testdateien / 235 Tests**,
  alle grün, Exit 0. `npm run lint` 0 Fehler, Exit 0. `npm run build` erfolgreich;
  Artefakte danach per `git status` geprüft — keine Änderung, nichts zu verwerfen.

Bedenken / Hinweis zu den Zahlen:
- Die Abnahme-Vorgabe nannte als Vorher-Stand "25 Testdateien, 261 Tests". In diesem
  Worktree zeigt `npm test` nur 23/235 (22/217-Ausgangsbasis + meine jetzt 18 Tests in
  1 Datei). Das ist kein neuer Fehler: mein Branch wurde laut Auftrag zwar in den
  Feature-Branch **hinein**gemerged, aber die Merges der drei anderen Abschnitt-1-Tasks
  (1, 5, 6) kamen nicht in die **andere** Richtung zurück in dieses Worktree/diesen
  Branch. Die genannten 25/261 sind vermutlich der Stand des Feature-Branchs nach allen
  vier Merges, nicht der Stand, den ein einzelner Task-Branch isoliert zeigen kann. Bitte
  beim finalen Zusammenführen gegenprüfen, nicht als Regression werten.
- Inhaltlich keine weitere Abweichung; nur der genannte Befund behoben, sonst nichts
  an Task 3 geändert.

## Abschnitt 1 — Task 1, Nachbesserung (Coding-Agent)

Zeit: 2026-09-17T22:41:11Z
Branch: sprach/task-1-backend-service
Commit(s): 62473742
Status: fertig

Was gemacht wurde:
- Vier vom Review per Mutationsproben gefundene Testlücken in `SpracheingabeServiceTest.java` geschlossen. `SpracheingabeService.java` selbst **nicht geändert** (`git diff` bestätigt das) — der Reviewer hatte die Implementierung inhaltlich bereits als korrekt bestätigt, es fehlte nur der Regressionsschutz in den Tests.
- `fremderInhaltstypWirdAbgewiesen`: nutzte bisher einen leeren Stream, wodurch die `IllegalArgumentException` auch bei durchgelassener Whitelist über die "Aufnahme ist leer"-Prüfung erklärbar war. Jetzt ein nicht-leerer Stream, zusätzlich Prüfung der konkreten Meldung ("Dieses Audioformat wird nicht unterstuetzt.").
- `geminiFehlerAntwortLandetNichtInDerFehlermeldung`: `mockGeminiAntwort()` gibt jetzt den Mock zurück; Test ergänzt um `Mockito.verify(antwort, never()).body()` — pflichten fest, dass der Fehlerfall den Antwortkörper (das Transkript) nicht einmal liest, statt nur die Fehlermeldung zu prüfen.
- Neuer Test `grenzeBrichtDasLesenSofortAbStattAllesVorherZuPuffern` mit einem neuen, byte-zählenden Fake-Stream (`ZaehlenderStrom`, liefert bis zu `MAX_AUDIO_BYTES + 5_000_000` Bytes): pflichtet fest, dass `leseHoechstens()` beim Überschreiten von `MAX_AUDIO_BYTES` sofort abbricht (gelesene Bytes bleiben deutlich unter dem Angebot), statt erst alles zu puffern und die Größe erst danach zu prüfen.
- `systemanweisungEnthaeltDieKernregeln`: zwei weitere `contains()`-Zusicherungen für die Reintext-Verbotszeile ("Keine Sternchen, keine Rauten, keine HTML-Tags, keine Markdown-Syntax.") und den Metallbau-Vokabelblock ("Feuerverzinkung, Pulverbeschichtung, VSG, ESG, Schwerlastanker,") — beide vorher von keiner Zusicherung erfasst, `HEB 200` deckte nur die Profile-Zeile ab.
- Jede neue/verschärfte Zusicherung per Mutationsprobe selbst verifiziert (Vorgabe der Nachbesserung): Whitelist-Bypass (`image/png` in `ERLAUBTE_INHALTSTYPEN` aufgenommen) → genau `fremderInhaltstypWirdAbgewiesen` rot; `antwort.body()` an die `log.warn`-Zeile angehängt → genau `geminiFehlerAntwortLandetNichtInDerFehlermeldung` rot (Mockito `NeverWantedButInvoked`); Größenprüfung in `leseHoechstens()` hinter die Leseschleife verschoben (erst puffern, dann prüfen) → genau der neue Streaming-Test rot (gelesene Bytes = 15.485.760 statt < 11.485.760); je eine der beiden neuen Systemanweisungs-Zeilen einzeln verändert → genau `systemanweisungEnthaeltDieKernregeln` rot, an der jeweils neuen Zeile. Nach jeder Probe zurückgesetzt, `git diff` auf `SpracheingabeService.java` danach leer.
- `./mvnw -B test -Dtest=SpracheingabeServiceTest`: 10 Tests, 0 Failures, 0 Errors, Exit 0.
- `./mvnw test` (voll): 3136 Tests, 0 Failures, 0 Errors, 17 Skipped, Exit 0, BUILD SUCCESS. Gegenüber dem in der Nachbesserungs-Anfrage genannten Zwischenstand (555 Testklassen/3135 Tests) exakt +1 Test (der neue Streaming-Test), keine Regression.

Bedenken / Abweichungen vom Plan:
- **Korrektur zu meinem eigenen Block vom 17.09.2026 22:13:53Z weiter oben in diesem Log** (Log ist append-only, deshalb hier als neuer Block statt Änderung): Dort hatte ich im Bedenken-Abschnitt sinngemäß behauptet, der `Mockito.lenient()`-Stub auf `antwort.body()` würde absichern, dass die Implementierung den Antwortkörper im Fehlerfall nie liest. Das ist falsch — `lenient()` unterdrückt ausschließlich Mockitos `UnnecessaryStubbingException`, wenn ein Stub ungenutzt bleibt. Es beweist nichts über das tatsächliche Verhalten des Dienstes; ein Mutant, der `antwort.body()` zusätzlich mitloggt, wäre mit der damaligen Testfassung nicht aufgefallen. Der tatsächliche Beweis ist die jetzt ergänzte `Mockito.verify(antwort, Mockito.never()).body()` (siehe oben und Mutationsprobe 2). Danke an den Review-Agenten für den präzisen Befund samt Belegen.
- Keine sonstigen Abweichungen. Alle vier Befunde 1:1 wie im Nachbesserungsauftrag beschrieben umgesetzt und einzeln per Mutationsprobe bestätigt.

## Abschnitt 1 — Nachprüfung der Nachbesserungen (Abschnitts-Reviewer)

Zeit: 2026-09-18T01:31:00Z
Branch: feature/spracheingabe-zeiterfassung (Merge-Commits adece6ab, f750d23b)
Commit(s): —
Status: 🟡 — beide Nachbesserungen belegt wirksam, Abschnitt 1 abgenommen

Gates (selbst gefahren):
- Backend `./mvnw -B test`: 555 Klassen, 3136 Tests, 0 Failures, 0 Errors, 17 Skipped, Exit 0 — Erwartung erfüllt (3135 + 1).
- Frontend `npm test`: 25 Dateien, 264 Tests, Exit 0. `npm run lint`: 0 Fehler, Exit 0.
- `SpracheingabeService.java` im Nachbesserungs-Commit nachweislich unverändert (`git diff 62473742^..HEAD -- src/main/java/` leer) — Nachbesserung 1 war rein testseitig, wie angegeben.

🔴-Befund Task 3 (Stream-Freigabe) — erledigt. Mit derselben Wegwerf-Testdatei nachgestellt
wie beim Erstbefund; alle drei ursprünglich belegten Pfade bringen den Track jetzt auf
`'ended'`:
- A werfender `MediaRecorder`-Konstruktor ✓
- B werfendes `recorder.start()` ✓
- C `MediaRecorder` global `undefined` ✓
- zusätzlich geprüft: `stoppe()` mit synchron werfendem `recorder.stop()` gibt frei und
  rejected ✓ (war Teil desselben Befunds)

Vier Hinweise Task 1 — alle erledigt. Die fünf Mutationen, die vorher sämtlich überlebten,
werden jetzt alle gefangen, jede mit genau einem Failure und jeweils vom thematisch
zuständigen Test (kein Kollateraltreffer, keine Tautologie):
- `antwort.body()` in die `log.warn`-Zeile → `geminiFehlerAntwortLandetNichtInDerFehlermeldung:232`
- `"image/png"` in die Whitelist → `fremderInhaltstypWirdAbgewiesen:172`
- Größenprüfung erst nach dem Vollpuffern → rot
- Markdown-Verbotszeile aus `SYSTEM_ANWEISUNG` entfernt → rot
- Metallbau-Vokabelblock aus `SYSTEM_ANWEISUNG` entfernt → rot

🟡 Neu gefunden, blockiert nicht — `audioRecorderService.ts:75`:
`const gewaehlterTyp = waehleMimeType()` steht weiterhin eine Zeile **oberhalb** des neuen
try-Blocks. Wirft `MediaRecorder.isTypeSupported()` beim Aufruf, bleibt der Stream offen —
empirisch belegt (Track bleibt `'live'`). Dieselbe Lücke wie A/B/C, nur eine Zeile höher.
Nicht blockierend, weil `isTypeSupported()` laut Spec einen Boolean liefert und in keinem
realen Browser wirft; A und C waren dagegen Safari- bzw. Alt-iOS-Realität. Behebung ist ein
Einzeiler (try-Block eine Zeile früher beginnen lassen) und passt gut in einen späteren Task,
der die Datei ohnehin anfasst.

🟡 Randnotiz, gleiche Datei: In `recorder.onstop` wird erst `new Blob(chunks, …)` gebaut und
danach `gibMikrofonFrei(stream)` gerufen. Umgekehrte Reihenfolge wäre robuster. Rein
theoretisch, `new Blob()` wirft praktisch nie.

## Abschnitt 1 — Orchestrator, Abnahme

Ampel 🟡, Abschnitt abgenommen. Beide Nachbesserungen vom Reviewer
nachgestellt und als wirksam belegt: die drei Stream-Pfade aus Task 3 bringen
den Track jetzt auf `'ended'`, und alle fünf Mutationen bei Task 1, die beim
ersten Durchgang überlebt hatten, werden gefangen — jede von genau einem Test.

**Vom Orchestrator selbst nachgezogen (eine Zeile):**
`react-zeiterfassung/src/services/audioRecorderService.ts` — der Aufruf
`waehleMimeType()` stand weiterhin oberhalb des neuen `try`-Blocks. Er ruft
`MediaRecorder.isTypeSupported()` auf; wirft der, bliebe der Stream offen.
Der Reviewer hatte das als 🟡 eingestuft und vorgeschlagen, es einem späteren
Task mitzugeben, "der die Datei ohnehin anfasst — Task 7".

Diese Begründung trifft nicht zu: Task 7 legt ausschließlich
`VoiceInputButton.tsx` an. Er baut auf `audioRecorderService.ts` auf, ändert
sie aber nicht. Kein späterer Task im Plan fasst die Datei an — die Lücke wäre
also nie geschlossen worden. Deshalb hier erledigt: Aufruf in den try-Block
gezogen, Kommentar an den Blockanfang gesetzt und um die Begründung ergänzt.

Nach Spec wirft `isTypeSupported()` nicht, das Risiko ist theoretisch. Die
Datei sagt aber zu, dass das Mikrofon nach `getUserMedia` auf keinem Pfad
offen bleibt — und eine Zusage mit Ausnahme ist keine.

Stand nach Abschnitt 1:

    Backend   555 Testklassen, 3136 Tests, 0 Failures, 0 Errors, 17 Skipped
    Frontend  25 Testdateien, 264 Tests, alle grün
    Lint      0 Fehler
    Build     übersetzt, keine Artefakte im Commit

Offen aus dem Review, bewusst nicht behoben: In `recorder.onstop` wird erst
der Blob gebaut, dann freigegeben. Umgekehrt wäre robuster, ist aber rein
theoretisch und ändert am Ergebnis nichts.
