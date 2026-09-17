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
