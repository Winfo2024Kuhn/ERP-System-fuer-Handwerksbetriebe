# Kontext-Log — Datensatz-Sperren Fundament (Restarbeiten)

Append-only. Lock-Protokoll siehe
`.claude/skills/loese-problem/references/kontext-log-format.md`.

## Abschnitt 0 — Baseline (Orchestrator)

Zeit: 2026-09-04T14:15:00Z
Branch: claude/eloquent-ramanujan-gz0w2t @ 5f556b19
Status: fertig

Was gemacht wurde:
- Cloud-Sitzung war abgerissen (Kontolimit). Plan, Spec und altes Kontext-Log lagen unter
  `docs/superpowers/` (gitignored) und existierten nur im Container. Spec aus GitHub-Issue
  #82 zurückgeholt, Umsetzungsstand aus den 32 Commits auf origin abgeleitet.
- Arbeits-Worktree `lock-arbeit/` auf dem Feature-Branch angelegt.
- Baseline gemessen:
  - Backend `./mvnw -B test`: 2442 Tests, 0 Failures, 4 Errors
    (`AuditChainRepairIntegrationTest` x2, `AuditHashRoundtripDiagnoseTest` x2, alle
    `CannotCreateTransaction`) — umgebungsbedingt, keine echte DB lokal.
  - Frontend `npm test`: 83 Dateien, 972 Tests, alle grün.
  - Frontend `npm run lint`: 0 Fehler, 1 Warnung (`BelegeKasseEditor.tsx:1204`).
  - Frontend `npm run build`: grün.
  - Abnahmeregel: grün = genau diese 4 Errors und diese 1 Warnung.
- Umgebung: Java 23, Node 24.19, npm 11.17. `node_modules` als Junction aufs Haupt-Repo,
  weil `package.json` und `package-lock.json` zwischen main und Feature-Branch identisch sind.

Bedenken / Abweichungen vom Plan:
- Die Original-Taskliste (19 Tasks) ist verloren. Sieben Task-Nummern sind unbekannt. Der
  Restplan ist aus Spec und Code-Stand abgeleitet und deckt die Spec ab, ist aber nicht
  nachweislich deckungsgleich mit dem Original.
- Task 14 (noopener) war laut Cloud-Sitzung fertig und grün, wurde aber nie gepusht. Wird
  als Task 4c neu gemacht.

## Abschnitt 4 — Task 4b (Coding-Agent)

Zeit: 2026-09-04T14:39:47Z
Branch: lock/task-4b-durchgriff
Commit(s): e9a953ef
Status: fertig

Was gemacht wurde:
- Roten Test geschrieben: `Update.versionskonfliktGibt409` — `service.aktualisieren` wirft `ObjectOptimisticLockingFailureException`, erwartet 409. Lief zunächst rot mit `Status expected:<409> but was:<400>`.
- Zweiten Test ergänzt: `Update.gewoehnlicherFehlerGibtWeiterhin400` — gewöhnlicher Fehler (IllegalArgumentException) aus dem Service liefert weiterhin 400. Lief von Anfang an grün (Regressionsschutz gegen späteres komplettes Entfernen des catch).
- Umsetzung in `AusgangsGeschaeftsDokumentController.update`: vorgeschalteter `catch (ObjectOptimisticLockingFailureException e) { throw e; }` vor dem bestehenden `catch (RuntimeException e)`. Die Exception läuft jetzt zum `RestExceptionHandler.handleOptimisticLockingFailure` durch (409 + Handwerker-Meldung), alle anderen RuntimeExceptions bleiben bei 400 wie bisher.
- Explizit geprüft: im 409-Body steht kein Klassenname (Test prüft per `content().string(not(containsString("ObjectOptimisticLockingFailureException")))`, grün).
- `AusgangsGeschaeftsDokumentControllerTest` komplett grün: 37 Tests, 0 Failures, 0 Errors.

Bedenken / Abweichungen vom Plan:
- Die übrigen `catch (RuntimeException e)`-Blöcke in derselben Datei (`create`, `buchen`, `emailVersendet`, `pdfSpeichern`, `stornieren`, `delete`, `getAbrechnungsverlauf`) wurden absichtlich NICHT angefasst, wie im Auftrag vorgegeben. `buchen`, `emailVersendet`, `pdfSpeichern`, `stornieren` und `delete` ändern denselben `@Version`-versionierten Aggregate-Root (`AusgangsGeschaeftsDokument`) über denselben Service — dort könnte ObjectOptimisticLockingFailureException also grundsätzlich genauso auftreten und würde ebenfalls als 400 mit Klassenname statt 409 durchkommen. `create` legt dagegen eine neue Zeile an (Version startet bei 0) und ist von diesem Konflikt praktisch nicht betroffen. Empfehlung: separater Task/Review-Punkt, ob dieselbe Verengung auch für die anderen fünf Endpunkte nötig ist — hier bewusst nicht mitgemacht, um den Task auf den beauftragten Scope (PUT /{id}) zu begrenzen.

## Abschnitt 4 — Task 4a (Coding-Agent)

Zeit: 2026-09-04T14:29:00Z
Branch: lock/task-4a-controller
Commit(s): 4a1c33e9
Status: fertig

Was gemacht wurde:
- `DatensatzLockController` neu angelegt (Routen `POST /api/datensatz-locks/{typ}/{id}/acquire`,
  `POST .../heartbeat`, `DELETE /api/datensatz-locks/{typ}/{id}`), Pfad-/DTO-Schema von
  `DokumentLockController` uebernommen, Typ-Aufloesung ueber `SperrbarerTyp.ausText()`.
- `DatensatzLockControllerTest` (MockMvc/@WebMvcTest, Stilvorbild `BelegControllerTest`)
  mit 5 Faellen: 200 (acquire happy path), 409 (LOCKED_BY_OTHER), 400 (unbekannter Typ
  "foo"), 401 (kein Principal), 204 (release).
- TDD eingehalten: Testdatei zuerst geschrieben und synchron im Vordergrund laufen lassen
  (`./mvnw -B test -Dtest=DatensatzLockControllerTest`), bevor der Controller existierte.
  Roter Lauf tatsaechlich gesehen — Ergebnis war ein Compile-Fehler, keine Test-Assertion:
  `[ERROR] .../DatensatzLockControllerTest.java:[36,13] Symbol nicht gefunden — Symbol: Klasse DatensatzLockController`.
  Danach Controller implementiert, gleicher Testlauf synchron wiederholt: 5 Tests,
  0 Failures, 0 Errors, BUILD SUCCESS.
- `DokumentLockController` nicht angefasst (bleibt fuer spaeteren Komplett-Loeschtask).

Bedenken / Abweichungen vom Plan:
- Kurzer Probe-Lauf der vollen Suite (`./mvnw -B test`, vor der Anweisung ihn dem
  Abschnitts-Reviewer zu ueberlassen) zeigte neben den 4 bekannten Baseline-Errors
  (AuditChainRepairIntegrationTest 2x, AuditHashRoundtripDiagnoseTest 2x,
  CannotCreateTransaction) einen zusaetzlichen **Failure**, der nichts mit Task 4a zu tun
  hat: `UnifiedEmailControllerExtractEmailTest.adversarialInputWithoutAt_isLinear:109
  execution timed out after 500 ms`. Datei wurde von mir nicht angefasst; wirkt wie ein
  zeitbasierter Test, der unter Last (voller Suite-Lauf, viele parallele JVM-Starts) das
  500-ms-Budget reisst. Nicht selbst repariert — nur hier vermerkt, falls der
  Abschnitts-Reviewer das beim eigenen Lauf ebenfalls sieht und einordnen muss.

## Abschnitt 4 — Task 4c (Coding-Agent)

Zeit: 2026-09-04T16:50:00Z
Branch: lock/task-4c-noopener
Commit(s): 080a32eb
Status: fertig

Was gemacht wurde:
- Roten Test zuerst geschrieben (`DokumentUebersichtEditor.test.tsx`), der prüft, dass `window.open` beim Öffnen des Dokument-Editors mit genau zwei Argumenten aufgerufen wird.
- Gegen den unveränderten Quellcode rot gelaufen: `AssertionError: expected [ …(3) ] to have a length of 2 but got 3`.
- Drittes Argument `'noopener'` aus dem `window.open(...)`-Aufruf in `DokumentUebersichtEditor.tsx` (Zeile ~223) entfernt.
- Erklärenden Kommentar über der Zeile ergänzt: bewusst kein `noopener`, weil der Editor-Tab sich künftig per X-Button selbst schließen (`window.close()`) können muss.
- Andere `noopener`-Stellen (rel="noopener noreferrer" an `<a target="_blank">`) nicht angefasst.
- Gates geprüft: `npm run lint` → 0 Fehler, genau die 1 vorbestehende Warnung (BelegeKasseEditor.tsx:1204); `npm test` → 84 Dateien / 973 Tests grün (im ersten Lauf traten wegen paralleler Last anderer Agenten auf der Maschine 18–39 Timeout-Fehler in unrelated Website-/Beitrag-Tests auf, die mit einem sauberen Solo-Lauf verschwanden — kein Bezug zu diesem Task); `npm run build` → grün. Build-Output in `src/main/resources/static/` vor dem Commit wieder auf HEAD-Stand zurückgesetzt (Projektvorgabe: kein Build-Output ins Repo).

Bedenken / Abweichungen vom Plan:
- keine

## Abschnitt 4 — Review (Abschnitts-Reviewer)

Zeit: 2026-09-04T17:10:00Z
Branch: claude/eloquent-ramanujan-gz0w2t (Merge von lock/task-4a-controller, lock/task-4b-durchgriff, lock/task-4c-noopener)
Commit(s): 60e2e65f, 5b05687e, 33a1e445 (Merge-Commits)
Ampel: 🟡 abgenommen

Merges:
- Alle drei Branches sauber per `--no-ff` gemerged, kein einziger Konflikt. Die drei Tasks
  berühren sechs paarweise verschiedene Dateien — die Abschnittsplanung hat die
  Datei-Aufteilung korrekt geschnitten.

Selbst gemessene Zahlen (alle Läufe solo, synchron im Vordergrund):
- `./mvnw -B clean package -DskipTests` → BUILD SUCCESS.
- `./mvnw -B test` → **2449 Tests, 0 Failures, 4 Errors**. Die 4 Errors sind exakt die
  bekannten Baseline-Errors (AuditChainRepairIntegrationTest 2x, AuditHashRoundtripDiagnoseTest 2x,
  jeweils CannotCreateTransaction). Baseline war 2442 → +7 neue Tests (5 aus 4a, 2 aus 4b),
  passt auf den Test genau.
- `npm run lint` → 0 Fehler, genau 1 Warnung (BelegeKasseEditor.tsx:1204) = Baseline.
- `npm run test` → 84 Dateien / 973 Tests. Im Gesamtlauf rissen 2 Tests in
  `src/components/document-editor/index.test.tsx` die 5000-ms-Schranke (Datei von diesem
  Abschnitt nicht angefasst). Solo nachgefahren: 10/10 grün, langsamster Fall 2698 ms —
  Last-Flakiness, kein Befund.
- `npm run build` → grün. Build-Output in `src/main/resources/static/` danach auf
  HEAD-Stand zurückgesetzt und die neue JS-Datei entfernt; `git status` sauber.

TDD-Nachweis selbst geprüft (Mutationsproben, Quellstand danach byte-identisch):
- 4b: `catch (ObjectOptimisticLockingFailureException)` entfernt →
  `versionskonfliktGibt409:465 Status expected:<409> but was:<400>`. Exakt die im Log
  behauptete rote Meldung; `gewoehnlicherFehlerGibtWeiterhin400` blieb dabei grün.
- 4c: `'noopener'` wieder eingesetzt → `expected [ …(3) ] to have a length of 2`. Der Test
  greift also wirklich.
- 4a: erster roter Lauf war laut Log ein Compile-Fehler (Klasse existierte noch nicht) —
  für eine neu angelegte Klasse ein legitimes erstes Rot.

Schnittstellen-Abgleich für spätere Tasks:
- Routen und DTO-Feldnamen aus 4a (`status`, `holderUserId`, `holderDisplayName`,
  `acquiredAt`, `lastHeartbeatAt`) sind wortgleich mit `DokumentLockDto`, das
  `useDocumentLock.ts` heute schon liest. Der Hook aus Task 5a kann 1:1 portiert werden,
  nur Pfad und Typ tauschen. `SperrbarerTyp` deckt exakt `'AUSGANG' | 'EINGANG'` ab.
- `/api/datensatz-locks/**` fällt unter die `/api/**`-Chain mit `anyRequest().authenticated()`
  in `SecurityConfig` — kein Loch in der Absicherung.

Sicherheit / DSGVO:
- Keine Secrets im Diff, kein Build-Output im Diff (0 Dateien unter `resources/static`).
- Testdaten nur Dummy-Namen (Max Mustermann, Erika Musterfrau), keine Klarnamen oder
  E-Mails in Logs. Der 409-Pfad aus 4b umgeht sogar das bisherige `log.error(...)`.
- 4c (`noopener` entfernt): kein Reverse-Tabnabbing-Risiko, weil das Ziel eine feste
  same-origin-Route ist und die Parameter über `URLSearchParams` kodiert werden.

Hinweise (blockieren nicht):
- `DatensatzLockController.java:59` — `heartbeat` hat keinen einzigen Test. Der Plan hat
  nur die fünf Fälle 200/409/400/401/204 verlangt, insofern im Auftrag; die 409- und
  401-Zweige von `heartbeat` sind aber unbewiesene Kopien aus `acquire`. Vorschlag:
  spätestens mit Task 5a mitziehen, wenn der Hook den Heartbeat wirklich ruft.
- `AusgangsGeschaeftsDokumentController.java:226` — die fünf Geschwister-Endpunkte
  (`buchen`, `emailVersendet`, `pdfSpeichern`, `stornieren`, `delete`) ändern denselben
  `@Version`-Aggregate-Root und liefern beim Versionskonflikt weiterhin 400 mit dem
  Klassennamen im Body. Der 4b-Agent hat das korrekt als Bedenken vermerkt und den Scope
  gehalten. Vorschlag: eigener Folge-Task, keine Nachbesserung hier.
- `DatensatzLockController.java` — die drei Methoden wiederholen dieselbe Principal-/Typ-
  Vorprüfung. Ließe sich bündeln, ist aber 1:1 der Stil des Vorbilds
  `DokumentLockController` und damit bewusst so. Reine Geschmacksfrage.
- `DokumentUebersichtEditor.test.tsx:56` — die Zusicherung hängt an der Argument-Anzahl.
  Ein `toHaveBeenCalledWith(expect.stringContaining('/dokument-editor?'), '_blank')` würde
  dasselbe sagen und nebenbei die URL absichern. Stil, kein Mangel.

Bedenken / Abweichungen vom Plan:
- Keine. Alle drei Tasks halten sich an den beauftragten Scope; `DokumentLockController`
  wurde wie vorgesehen nicht angefasst.

## Abschnitt 5 — Task 5b (Coding-Agent)

- Zeit: 2026-09-04
- Branch: lock/task-5b-backend-verbraucher
- Commit: e9c84599 — "LieferantDokumentController: Lock-Pruefung auf DatensatzLockService umgestellt"
- Status: fertig, Tests grün

**Was gemacht wurde:**
- `LieferantDokumentController`: Abhängigkeit von `DokumentLockService`/`TYP_EINGANG` auf
  `DatensatzLockService`/`SperrbarerTyp.EINGANG` umgestellt. Einzige Verwendung war
  `isHeldBy(...)` im PUT-Endpoint (`updateDokument`) — Signatur-Wechsel von `String` auf
  `SperrbarerTyp`, sonst 1:1 identisches Verhalten (409 bei fehlendem Lock, 200 sonst).
  `DatensatzLockDto`/`DokumentLockDto` wurden im Controller nicht verwendet, daher nicht
  betroffen.
- Test mitgezogen: `@MockBean DokumentLockService` → `DatensatzLockService`, Matcher
  `anyString()` → `any(SperrbarerTyp.class)` bzw. `eq(SperrbarerTyp.EINGANG)`.
- Bestehende Tests (`aktualisiertDokumentErfolgreich`, `ohneLockGibt409`) deckten das
  Lock-Verhalten (409 vs. 200 je nach `isHeldBy`-Stub) bereits ab, prüften aber nicht,
  mit welchem konkreten Typ `isHeldBy` aufgerufen wird. Deshalb neuen Test
  `lockPruefungNutztSperrbarerTypEingang` ergänzt, der per `verify(...).isHeldBy(eq(SperrbarerTyp.EINGANG), eq(1L), eq(42L))`
  genau das prüft.
- Mutationsprobe: Aufruf testweise auf `SperrbarerTyp.AUSGANG` zurückgedreht → neuer Test
  wird rot (Mockito "Argument(s) are different!"), zurückgesetzt → wieder grün.
- Testlauf `LieferantDokumentControllerTest`: 27/27 grün (26 bestehend + 1 neu).
- Voller Backend-Testlauf: 2450 Tests, 1 Failure, 4 Errors. Die 4 Errors sind die
  bekannten vorbestehenden (`AuditChainRepairIntegrationTest` 2x,
  `AuditHashRoundtripDiagnoseTest` 2x, `CannotCreateTransaction`). Die 1 Failure
  (`UnifiedEmailControllerExtractEmailTest.adversarialInputWithoutAt_isLinear`,
  Timeout 500ms, Datei nicht angefasst) im isolierten Einzellauf grün — Zeitschranken-Test,
  durch parallele Agenten auf der Maschine flaky geworden, keine echte Regression.
  Damit Abnahmeregel erfüllt (genau die 4 Baseline-Errors, sonst grün).

**Bedenken / Abweichungen vom Plan:** keine.

## Abschnitt 5 — Task 5c (Coding-Agent)

**Zeit:** 2026-09-04, ca. 17:15–17:52 Uhr
**Branch:** lock/task-5c-heartbeat-tests
**Commit:** 75a32bc8 — test: Heartbeat-Zweige von DatensatzLockController absichern
**Status:** grün, fertig

**Was gemacht wurde:**
Vier neue Tests in `DatensatzLockControllerTest` für `POST /api/datensatz-locks/{typ}/{id}/heartbeat` ergänzt (Nested-Klasse `Heartbeat`, gleicher Stil wie `Acquire`):
- `eigenesLock_liefert200` — Verlängerung durch den Lock-Halter selbst -> 200/ACQUIRED
- `fremdesLock_liefert409` — fremdes Lock aktiv -> 409/LOCKED_BY_OTHER mit DTO im Body
- `unbekannterTyp_liefert400` — unbekannter Typ -> 400
- `keinPrincipal_liefert401` — kein gültiger Principal -> 401

Da der Service in diesem Controller-Test gemockt ist (`@MockBean`), prüfen die Tests den Controller-Zweig (Statuscode-Mapping je nach DTO-Status/Principal/Typ), nicht die Service-internen Heartbeat-Regeln (verwaistes Lock übernehmen, fehlender Eintrag -> Neuerwerb) — das ist bereits so für `acquire` etabliert und wurde übernommen, nicht neu erfunden.

**Mutationsproben** (Controller kurz kaputt gemacht, Test rot laufen lassen, danach Controller byte-identisch wiederhergestellt — `git diff` auf den Controller war am Ende leer):
1. 409-Zweig entfernt / immer CONFLICT zurückgegeben (für `eigenesLock_liefert200`):
   `DatensatzLockControllerTest$Heartbeat.eigenesLock_liefert200:119 Status expected:<200> but was:<409>`
2. 409-Zweig entfernt / immer OK zurückgegeben (für `fremdesLock_liefert409`):
   `DatensatzLockControllerTest$Heartbeat.fremdesLock_liefert409:133 Status expected:<409> but was:<200>`
3. Typ-Check (`if (entitaetTyp == null) return badRequest()`) entfernt (für `unbekannterTyp_liefert400`):
   `DatensatzLockControllerTest$Heartbeat.unbekannterTyp_liefert400:141 – NullPointerException: Cannot invoke "DatensatzLockDto.status()" because "result" is null`
4. Principal-Check (`if (principal == null) return UNAUTHORIZED`) entfernt (für `keinPrincipal_liefert401`):
   `DatensatzLockControllerTest$Heartbeat.keinPrincipal_liefert401:149 – NullPointerException: Cannot invoke "FrontendUserPrincipal.getId()" because "principal" is null`

Vollständiger Backend-Testlauf (`./mvnw test`): 2453 Tests, 0 Failures, genau die 4 vorbestehenden Errors (`AuditChainRepairIntegrationTest` 2x, `AuditHashRoundtripDiagnoseTest` 2x, `CannotCreateTransaction`) — Baseline unverändert, keine neuen Fehler.

**Bedenken / Abweichungen vom Plan:**
Keine echten Fehler im Controller oder Service gefunden. Der Plan erwähnte, dass `heartbeat` im Service mehr als ein Zeitstempel-Update ist (Neuerwerb bei fehlendem Eintrag, Übernahme bei verwaistem Lock) — das stimmt, ist aber Service-Logik, die im (gemockten) Controller-Test nicht sichtbar wird. Falls das separat abgesichert werden soll, bräuchte es einen `DatensatzLockServiceTest` (eigener Task, nicht Teil von 5c) — nur als Hinweis, keine Blockade für diesen Task.

## Abschnitt 5 — Task 5a (Coding-Agent)

- **Zeit:** 2026-09-04
- **Branch:** `lock/task-5a-hook`
- **Commit(s):** `8af4cfcd` — feat(lock): useDatensatzLock als fuenften wiederverwendbaren Baustein ergaenzen

**Status:** fertig, alle Gates gruen (lint: 0 Fehler/1 vorbestehende Warnung `BelegeKasseEditor.tsx:1204`; test: neue Datei 18/18 gruen, Gesamtsuite 979+18=991 Tests grün nach Einzelnachlauf der durch Systemlast geflackerten, unberuehrten Dateien; build: gruen, `src/main/resources/static/` vor Commit zurueckgesetzt).

**Was gemacht wurde:**
- `react-pc-frontend/src/components/lock/useDatensatzLock.ts` (neu): verallgemeinerte Nachfolge-Fassung von `useDocumentLock.ts` (Vorbild unangetastet gelassen). Route `/api/datensatz-locks/{typ}/{id}/...`, Typ `'AUSGANG' | 'EINGANG'`. Lock wird beim Mount erworben/erkannt und fuer die gesamte Seiten-Lebensdauer gehalten (Heartbeat alle 30s, Freigabe bei `pagehide` und beim Unmount mit `keepalive`). `modus`/`onBearbeiten`/`onFertig` sind ein reiner UI-Umschalter obendrauf (kein zusaetzlicher Netzwerk-Request), zusaetzlich `freigeben()` fuer aktives, awaitbares Freigeben (fuer den kuenftigen X-Button-Ablauf: speichern -> freigeben -> Tab schliessen). Rueckgabewert exakt aus den Verbrauchern abgeleitet: `modus`, `kannBearbeiten` (= nicht durch anderen gesperrt), `verbindungWeg` -> direkt an `BearbeitenLeiste`; `halterName`/`seit` (Minuten als String) -> direkt an `GesperrtHinweis`. Kein eigener Sekunden-Timer — `verbleibendeSekunden` bleibt Sache der Seite/`useIdleTimer`. `verbindungWeg` kippt erst nach 2 aufeinanderfolgenden fehlgeschlagenen Heartbeats (Konstante `VERBINDUNG_WEG_SCHWELLE`).
- `react-pc-frontend/src/components/lock/useDatensatzLock.test.tsx` (neu): 18 Tests mit gefaktem `fetch` (Stil an `useKonfliktMeldung.test.tsx` angelehnt, technisch via `renderHook`/`act`/Fake-Timer). Deckt ab: Mount-Acquire mit korrekter Route/Typ, idle ohne ID, Modus-Umschalten, 409-Fall (kannBearbeiten/halterName/seit inkl. Minutenberechnung), Heartbeat-Intervall, Ein-Fehlschlag-kein-verbindungWeg vs. Zwei-Fehlschlaege-verbindungWeg, Zaehler-Reset nach Erfolg, 409 im Heartbeat, Freigabe bei Unmount/`pagehide` mit `keepalive`, kein DELETE wenn nie gehalten, aktives `freigeben()`.
- Vor der Implementierung testgetrieben vorgegangen (RED zuerst: Modul fehlte, `Failed to resolve import`), danach in 3 Bloecken Tests ergaenzt und jeweils gruen gefahren. Zusaetzlich zwei manuelle Mutationsproben (Schwelle 2->1, `kannBearbeiten` fest auf `true`) durchgefuehrt und beide zuverlaessig rot bekommen, dann zurueckgesetzt.

**Bedenken / Abweichungen vom Plan:**
- Kein Rueckgabefeld fuer den rohen Ladefehler-/Retry-Zustand (`status`/`retry`) aufgenommen, obwohl `useDocumentLock` das hatte — bewusst weggelassen, weil kein Verbraucher (`BearbeitenLeiste`/`GesperrtHinweis`) das aktuell braucht und die Vorgabe "Rueckgabewert nicht frei erfinden" das nahelegt. Falls eine kuenftige Seite eine Fehleranzeige/Retry-Button braucht, muss das nachgezogen werden.
- Design-Entscheidung, die nicht explizit in der Aufgabenstellung stand: das Lock wird beim Mount fuer die GESAMTE Seiten-Lebensdauer gehalten, unabhaengig vom UI-Modus ("lesen" vs. "bearbeiten") — `onBearbeiten`/`onFertig` schalten nur die Anzeige um, ohne erneut zu acquiren/freizugeben. Das folgt der woertlichen Vorgabe "acquire beim Mount" (Vorbild useDocumentLock) und macht `kannBearbeiten`/`halterName`/`seit` schon vor einem Klick auf "Bearbeiten" bekannt, wie es der Kommentar in `BearbeitenLeiste.tsx` nahelegt. Alternative waere gewesen, erst bei Klick auf "Bearbeiten" zu acquiren — dann waeren `kannBearbeiten`/GesperrtHinweis-Daten aber erst nach einem fehlgeschlagenen Versuch bekannt. Sollte das nicht der gewuenschten Semantik entsprechen, bitte im Review korrigieren lassen.
- Frontend-Testsuite zeigte bei mehreren Volllaeufen Timeouts in bis zu ~20 voellig unberuehrten Dateien (`document-editor/index.test.tsx`, `BeitragAssistent.test.tsx`, `ArtikelEditor.test.tsx` u.a.) — durchgehend `Test timed out in 5000ms`, nie ein Assertion-Fehler. Einzeln/isoliert nachgefahren: alle grün (z.B. `BeitragAssistent.test.tsx` 13/13, `document-editor/index.test.tsx` 10/10). Eindeutig Ressourcen-Konkurrenz durch die drei parallel laufenden Agenten, keine echte Regression.

## Abschnitt 5 — Review (Abschnitts-Reviewer)

Zeit: 2026-09-04T18:25:00Z
Branch: claude/eloquent-ramanujan-gz0w2t (Merge von lock/task-5a-hook, lock/task-5b-backend-verbraucher, lock/task-5c-heartbeat-tests)
Ampel: 🔴 nicht abgenommen — Nachbesserung an Task 5a nötig, 5b und 5c sind sauber

Merges:
- Alle drei Branches per `--no-ff` gemerged, kein einziger Konflikt. Die drei Tasks fassen
  fünf paarweise verschiedene Dateien an, die Schnittplanung war korrekt.

Selbst gemessene Zahlen (alle Läufe solo, synchron im Vordergrund):
- `./mvnw -B clean package -DskipTests` → BUILD SUCCESS.
- `./mvnw -B test` → **2454 Tests, 0 Failures, 4 Errors**. Die 4 Errors sind exakt die
  bekannten Baseline-Errors (AuditChainRepairIntegrationTest 2x,
  AuditHashRoundtripDiagnoseTest 2x, jeweils CannotCreateTransaction). Baseline 2449 →
  +5 (1 aus 5b, 4 aus 5c), exakt wie erwartet. Kein Zeitschranken-Flackern in diesem Lauf.
- `npm run lint` → 0 Fehler, genau 1 Warnung (BelegeKasseEditor.tsx:1204) = Baseline.
- `npm run test` → **85 Dateien / 991 Tests, alle grün**. Baseline 84/973 → +1 Datei /
  +18 Tests (5a), exakt wie erwartet. Kein Test riss die 5000-ms-Schranke.
- `npm run build` → grün. Build-Output in `src/main/resources/static/` danach auf
  HEAD zurückgesetzt, die neue JS-Datei entfernt; `git status` sauber.

Umgebungs-Fund (kein Befund gegen einen Task, aber für den Orchestrator wichtig):
- Das gemeinsame `node_modules` unter
  `ERP-System-fuer-Handwerksbetriebe/react-pc-frontend/` war zu Review-Beginn **leer**.
  Alle Worktree-`node_modules` sind Junctions genau dorthin — vermutlich hat ein
  rekursives Löschen eines abgenommenen Worktrees die Junction verfolgt und das
  Ziel mit ausgeräumt. Vor den Frontend-Gates einmal `npm ci` im Haupt-Repo gefahren,
  danach liefen alle Gates. Beim Aufräumen künftiger Worktrees zuerst die Junction
  entfernen, dann den Ordner löschen.

TDD-Nachweis selbst geprüft (Mutationsproben, Quellstand danach byte-identisch, `git status` leer):
- 5a: `VERBINDUNG_WEG_SCHWELLE` 2 → 1 → zwei Tests rot
  (`ein einzelner fehlgeschlagener Heartbeat setzt verbindungWeg NICHT` und
  `ein erfolgreicher Heartbeat setzt den Fehlschlag-Zaehler zurueck`, jeweils
  `expected true to be false`). Die Schwelle ist also wirklich abgesichert.
- 5b: `SperrbarerTyp.EINGANG` → `AUSGANG` →
  `LieferantDokumentControllerTest$UpdateDokument.lockPruefungNutztSperrbarerTypEingang:208
  Argument(s) are different!`. Exakt die im Log behauptete rote Meldung.

5b — reiner Abhängigkeitstausch bestätigt:
- `git diff 33a1e445..e9c84599 -- src/main` umfasst genau vier geänderte Zeilen: zwei
  Import-Zeilen, ein Feldtyp, ein Methodenaufruf mit Enum statt String-Konstante.
  Keine Zeile Verhalten. Sauber im Scope.

5c — sauber:
- Vier Tests in einer neuen Nested-Klasse `Heartbeat`, Stil 1:1 wie `Acquire`.
  Nur die Testdatei angefasst, Controller unberührt. Dummy-Namen (Max Mustermann,
  Erika Musterfrau). Keine Beanstandung.

Sicherheit / DSGVO:
- Diff des Abschnitts: genau die 5 geplanten Dateien, 0 Dateien unter `resources/static`,
  keine Secrets, keine Klarnamen — nur Dummy-Namen (Max Mustermann, Erika Musterfrau,
  Thomas Beispiel, Anna Beispiel).

🔴 Blockierend — Task 5a, Lock-Semantik gegen die Spec:
Der Hook hat keinen einzigen Weg, ein Lock nach dem Mount noch einmal zu holen. Der
`retryNonce` aus dem Vorbild `useDocumentLock` wurde ersatzlos gestrichen, die Effekt-Deps
sind nur `[typ, id]`. `onBearbeiten` schaltet ausschließlich `modus` um. Daraus folgen drei
belegte Fehlverhalten (mit einer Wegwerf-Testdatei nachgestellt, danach wieder gelöscht):

1. `useDatensatzLock.ts:225-230` — nach `freigeben()` (genau das, was der Idle-Timer nach
   5 Minuten und der X-Button-Ablauf in Schritt 3 auslösen) liefert der Hook
   `kannBearbeiten: true`, `onBearbeiten()` setzt `modus: 'bearbeiten'` und schickt **null**
   zusätzliche Requests. Der Nutzer sitzt im Bearbeiten-Modus **ohne gehaltenes Lock und
   ohne laufenden Heartbeat**, ein Kollege kann den Datensatz parallel übernehmen.
   Nachweisbar sein muss: nach `freigeben()` löst ein Klick auf Bearbeiten ein neues
   `POST /api/datensatz-locks/{typ}/{id}/acquire` aus; erst bei 200 wechselt `modus` auf
   `bearbeiten` und der Heartbeat läuft wieder; bei 409 bleibt `modus` auf `lesen` und
   `halterName`/`seit` sind gesetzt.

2. `useDatensatzLock.ts:216` — `kannBearbeiten = status !== 'locked-by-other'` ist auch in
   `loading` und `error` true. Belegt: scheitert der Acquire mit 500, meldet der Hook
   `kannBearbeiten: true`, und `onBearbeiten()` schaltet in den Bearbeiten-Modus — ohne Lock.
   Nachweisbar sein muss: `kannBearbeiten` ist nur true, solange das Lock tatsächlich
   gehalten wird oder frei erwerbbar ist; ein Test "acquire liefert 500 → `kannBearbeiten`
   false, `onBearbeiten` wirkungslos" muss grün sein.

3. `useDatensatzLock.ts:81` (Deps `[typ, id]`) — nach einem 409 (beim Mount oder im
   Heartbeat) stoppt der Heartbeat und der Hook fragt nie wieder nach. `kannBearbeiten`
   bleibt für die restliche Seiten-Lebensdauer false. Die Spec verlangt aber: wird der
   Datensatz frei, soll man ihn per Bearbeiten-Knopf übernehmen können — und
   `BearbeitenLeiste` hat für genau das den Knopf. Abschnitt 6 kann das ohne Hook-Umbau
   nicht auflösen.
   Nachweisbar sein muss: bei `kannBearbeiten: false` versucht ein Klick auf Bearbeiten
   ein neues Acquire; gibt der Kollege frei, gelingt die Übernahme.

Antworten auf die vier Review-Fragen:
1. `onFertig` gibt das Lock **nicht** frei, es schaltet nur die Anzeige um. Der Nutzer hält
   das Lock im Lesen-Modus weiter. Isoliert betrachtet noch tragbar (der Idle-Timer räumt
   nach 5 Min. auf) — deshalb 🟡, siehe unten.
2. Nein. `onBearbeiten` holt das Lock nie, weder nach `freigeben()` noch wenn es beim Mount
   fremd war. Kein Netzwerk-Request, belegt.
3. Nein. Nach `freigeben()` kommt die Seite zwar formal zurück in den Bearbeiten-Modus,
   aber ohne Lock und ohne Heartbeat — schlimmer als ein Sackgassen-Zustand, weil es
   nach außen wie normales Bearbeiten aussieht.
4. Ja, die Form passt: `modus`/`kannBearbeiten`/`verbindungWeg`/`onBearbeiten`/`onFertig`
   decken `BearbeitenLeisteProps` bis auf `verbleibendeSekunden` (kommt bewusst aus
   `useIdleTimer`), `halterName`/`seit` decken `GesperrtHinweisProps`. Kein Adapter nötig.
   Nur die Semantik hinter `kannBearbeiten`/`onBearbeiten` stimmt nicht (siehe 🔴).

🟡 Hinweise (blockieren nicht):
- `useDatensatzLock.ts:190` — `onFertig` behält das Lock. Empfehlung: zusammen mit der
  🔴-Nachbesserung entscheiden, ob Fertig freigibt. Dann greift auch das Spec-Ziel, dass
  niemand Kollegen blockiert, der nur liest.
- `useDatensatzLock.ts:82` — Acquire beim Mount bedeutet: wer ein Dokument nur **ansieht**,
  sperrt es für alle anderen. Das ist das Verhalten des Vorbilds und laut Plan beauftragt,
  passt aber schlecht zum Spec-Bild vom Nur-Lesen-Modus mit ruhigem Hinweis. Wenn der Hook
  für die 🔴 ohnehin ein Acquire-bei-Bedarf bekommt, fällt das quasi mit ab.
- `useDatensatzLock.ts:247` — `seit` wird während des Renders aus `Date.now()` berechnet.
  Nach dem 409 löst der Hook keinen Re-Render mehr aus, die Minutenangabe friert also auf
  dem Wert vom Eintreffen der 409 ein. Kosmetisch.
- `useDatensatzLock.ts:139,183` — nur der Acquire nutzt `controller.signal`; Heartbeat und
  DELETE laufen ohne. Über das `cancelled`-Flag abgesichert und genau wie im Vorbild, aber
  der Abbruch wäre konsequenter.
- `useDatensatzLock.ts` — `status`/`retry` werden nicht exportiert (der 5a-Agent hat das
  selbst als Bedenken vermerkt). Sobald `kannBearbeiten` korrekt zwischen gesperrt und
  Fehler unterscheidet, braucht die Seite eine Möglichkeit, den Fehlerfall anzuzeigen.

Bedenken / Abweichungen vom Plan:
- 5b und 5c halten den Scope exakt. 5a hat den beauftragten Scope ebenfalls gehalten; die
  Nachbesserung betrifft eine Design-Entscheidung, die der Agent selbst zur Klärung
  gestellt hat.

## Abschnitt 5 — Task 5a Nachbesserung 1 (Coding-Agent)

- **Zeit:** 2026-09-04
- **Branch:** `lock/task-5a-hook`
- **Commit(s):** `1141d0f4` — fix(lock): useDatensatzLock kann ein Lock nach dem Mount wieder erwerben

**Status:** fertig, alle Gates gruen (lint: 0 Fehler/1 vorbestehende Warnung `BelegeKasseEditor.tsx:1204`; test: neue Datei 24/24 gruen, dreifach wiederholt stabil; Gesamtsuite 997 Tests gruen, ein Lauf komplett ohne Fremd-Timeouts; build: gruen, `src/main/resources/static/` vor Commit zurueckgesetzt).

**Ausgangslage:** Der Review hat drei Ausprägungen derselben Ursache gefunden — der Hook konnte ein Lock nach dem Mount nie wieder holen (`retryNonce` aus dem Vorbild `useDocumentLock` ersatzlos gestrichen, Effekt-Deps nur `[typ, id]`, `onBearbeiten` schaltete rein die Anzeige um). Betroffen: nach `freigeben()`, nach einem Acquire-Fehler/waehrend `loading`, und nach einer 409-Fremdsperre.

**Was gemacht wurde:**
- Sechs neue Tests **zuerst** geschrieben und gegen den (damals fehlerhaften) Stand rot laufen lassen, bevor der Fix geschrieben wurde:
  1. "waehrend ein Acquire noch laeuft, ist kannBearbeiten false und onBearbeiten() loest keinen zweiten Request aus" — rot mit `expected true to be false` (kannBearbeiten war in `loading` faelschlich true).
  2. "nach einem Acquire-Fehler (500) ist kannBearbeiten false und onBearbeiten() loest keinen zweiten Request aus" — rot mit derselben Meldung (kannBearbeiten in `error` faelschlich true).
  3. "onBearbeiten() nach freigeben() sendet ein neues Acquire und wechselt erst bei Erfolg in den Bearbeiten-Modus" — rot, weil nur 1 statt 2 Acquire-Requests gezaehlt wurden (alter Code schaltete Modus ohne Request um).
  4. "onBearbeiten() nach freigeben() bleibt im Modus 'lesen', wenn der erneute Versuch mit 409 scheitert" — rot mit Timeout (`halterName` blieb `undefined`, da nie erneut angefragt wurde).
  5. "nach erfolgreichem erneuten Acquire laeuft der Heartbeat wieder" — rot mit `expected 3 to be greater than 3` (kein zweiter Heartbeat, weil kein erneutes Acquire lief).
  6. "onFertig() gibt das Lock aktiv frei (DELETE), nicht nur die Anzeige" — rot mit Timeout (onFertig sendete nie ein DELETE).
- Fix in `useDatensatzLock.ts`: `acquire`/`heartbeat`/`startHeartbeat`/`stopHeartbeat`/`releaseKeepalive`/`aktivFreigeben` aus dem Mount-Effekt in stabile `useCallback`-Funktionen ausgelagert (referenzstabil, da ihre Deps selbst stabil sind), zusammen mit einer Generationszaehlung (`generationRef`) und `mountedRef`/`AbortController`, damit ein spaeterer manueller Acquire-Versuch (aus `onBearbeiten`) dieselbe Logik nutzt wie der Mount und veraltete/ueberholte Antworten sauber verworfen werden. `kannBearbeiten` ist jetzt `lockUrl == null || status === 'acquired'` (vorher `status !== 'locked-by-other'`, faelschlich true bei `loading`/`error`). `onBearbeiten()` haengt an `heldRef.current`/`status`, nicht an `kannBearbeiten`: bereits gehalten -> nur Anzeige umschalten; `loading`/`error` -> wirkungslos; sonst (frisch freigegeben oder fremd gesperrt) -> frisches Acquire, Modus wechselt erst bei Erfolg. `onFertig()` ruft jetzt dieselbe Freigabe-Logik wie `freigeben()` auf (aktives DELETE), nicht nur `setModus`.
- Zwei bereits bestehende Tests mussten an die neue, jetzt asynchrone Semantik von `onFertig`/`onBearbeiten` bei Fremdsperre angepasst werden (`await waitFor` statt synchroner Assertion direkt nach dem Klick; ein Test hatte einen inhaltlich ueberholten Titel/Assertion und wurde auf das neue Soll-Verhalten umgeschrieben, siehe "Bedenken" unten).
- Mutationsproben (drei, jeweils durchgefuehrt und wieder zurueckgesetzt): `kannBearbeiten`-Regel auf die alte Fassung zurueckgedreht -> 4 Tests rot; `onBearbeiten`-Retry-Zweig stillgelegt (simuliert den urspruenglichen Bug) -> 3 Tests rot; `onFertig` auf reines `setModus` zurueckgedreht -> 1 Test rot.
- Waehrend der Arbeit einen zusaetzlichen Lint-Fehler beheben muessen (`react-hooks/set-state-in-effect` auf `void acquire(lockUrl)` im Mount-Effekt, weil der Linter die synchrone `setStatus('loading')` in der ausgelagerten `acquire()`-Funktion bis zum Aufrufer zurueckverfolgt) — mit begruendetem `eslint-disable-next-line` geloest, Stil an `useIdleTimer.ts` angelehnt (dort dieselbe Rule aus demselben Grund unterdrueckt).

**Bedenken / Abweichungen vom Plan:**
- Der Testtitel "onBearbeiten hat keine Wirkung, wenn ein anderer den Datensatz haelt" (aus dem ersten Durchlauf) war mit der neuen Soll-Semantik nicht mehr korrekt — genau dieser Fall (Fremdsperre) soll jetzt ja einen erneuten Versuch ausloesen. Test umbenannt und umgeschrieben auf "onBearbeiten() versucht bei einer Fremdsperre trotzdem ein neues Acquire, bleibt aber im Modus 'lesen', solange weiter gesperrt ist" (mit zwei gemockten Acquire-Antworten). Ohne die Anpassung waere ein unbehandelter Promise-Rejection/`act()`-Warnhinweis in der Konsole geblieben (der ungemockte zweite Fetch-Call fiel auf `undefined` zurueck).
- Beim Schreiben eines Tests fuer den Heartbeat-Neustart eine echte Falle in der eigenen Testinfrastruktur gefunden: `vi.runOnlyPendingTimersAsync()` startet nach einem erfolgreichen Acquire ungewollt auch gleich den frisch registrierten 30s-Heartbeat-Interval mit und verbraucht dabei eine fuer einen spaeteren Schritt vorgesehene Mock-Antwort (per Debug-Log verifiziert: 4 statt 3 erwartete Fetch-Calls, Heartbeat lief zwischen Mount-Acquire und `freigeben()`). Umgangen durch reine Mikrotask-Flushes (`await Promise.resolve()`) statt `runOnlyPendingTimersAsync()` fuer Schritte ohne beteiligten Timer; Fake-Timer bleiben trotzdem von Anfang an aktiv, damit der spaeter neu registrierte Heartbeat-Interval unter derselben Uhr entsteht wie die Advance-Pruefung. Zusaetzlich ein `vi.useRealTimers()`-Sicherheitsnetz in `afterEach` ergaenzt, weil ein fruehzeitig fehlschlagender Fake-Timer-Test sonst nachfolgende Tests unbemerkt unter Fake-Timern haengen laesst (genau das ist mir beim ersten Versuch passiert: ein Folgetest schlug mit einem inhaltsleeren "Test timed out" statt einem echten Assertion-Fehler fehl).
- Design-Entscheidung zur Frage aus dem Auftrag ("Entscheide, was zum Rest passt"): `onBearbeiten()` haengt bewusst NICHT von `kannBearbeiten` ab (die Komponente `BearbeitenLeiste` deaktiviert den Knopf zwar weiterhin bei `kannBearbeiten=false`, aber der Hook selbst verlaesst sich nicht darauf) — das entspricht der expliziten Vorgabe im Nachbesserungs-Auftrag. Ein automatischer Hintergrund-Retry (Polling) waehrend `locked-by-other`, damit der Knopf in der ECHTEN UI irgendwann von selbst wieder aktiv wird, ist NICHT Teil dieser Nachbesserung (nicht gefordert, wuerde `BearbeitenLeiste.tsx` oder eine Seiten-Verdrahtung betreffen, die ausserhalb der erlaubten Dateien liegt) — falls gewuenscht, muesste das ein separater Task uebernehmen.

## Abschnitt 5 — Review 2 (Abschnitts-Reviewer)

Zeit: 2026-09-04T19:05:00Z
Branch: claude/eloquent-ramanujan-gz0w2t, Merge-Commit b1ff9b47 (Nachbesserung 1141d0f4)
Ampel: 🔴 nicht abgenommen — der Hook ist für sich richtig, aber der Retry-Weg ist über
`BearbeitenLeiste` nicht erreichbar. Eine weitere, kleine Runde.

Merge:
- `lock/task-5a-hook` per `--no-ff` gemerged, kein Konflikt. Die Nachbesserung fasst genau
  die zwei erlaubten Dateien an (`useDatensatzLock.ts`, `useDatensatzLock.test.tsx`),
  +391/−101. Kein anderes File im Abschnitts-Diff.

Selbst gemessene Zahlen (alle Läufe solo, synchron im Vordergrund):
- `./mvnw -B test` → **2454 Tests, 0 Failures, 4 Errors** — exakt die 4 bekannten
  Baseline-Errors. Unverändert gegenüber Review 1, Backend ist nicht berührt.
- `npm run lint` → 0 Fehler, genau 1 Warnung (BelegeKasseEditor.tsx:1204) = Baseline.
- `npm run test` → **85 Dateien / 997 Tests, alle grün**. Review 1 war 85/991 → +6 Tests
  aus der Nachbesserung, exakt wie angekündigt. Kein Zeitschranken-Flackern.
- `npm run build` → grün, Build-Output danach zurückgesetzt, `git status` sauber.

Mutationsprobe am neuen Retry-Zweig (Quellstand danach byte-identisch, `git status` leer):
- `if (status === 'loading' || status === 'error') return;` → `if (status !== 'acquired') return;`
  (Retry damit stillgelegt) → **4 Tests rot**: `expected [...] to have a length of 2 but got 1`,
  zweimal `expected 'lesen' to be 'bearbeiten'`, `expected undefined to be 'Petra Beispiel'`.
  Der Retry-Zweig ist also wirklich abgesichert.

Meine drei 🔴 aus Review 1 — einzeln mit eigener Wegwerf-Testdatei nachgestellt
(danach gelöscht, Baum sauber). Im Hook selbst sind alle drei behoben:
- (1) Nach `freigeben()` löst `onBearbeiten()` ein zweites `POST .../acquire` aus
  (Zähler 1 → 2), `modus` wechselt erst nach der 200 auf `bearbeiten`. Bei 409 bleibt
  `modus` auf `lesen`, `halterName` = "Petra Beispiel", `seit` = "5". Nach dem
  erfolgreichen Retry läuft auch der Heartbeat wieder (1 Heartbeat nach 30 s).
- (2) Acquire mit 500 → `kannBearbeiten` false, `onBearbeiten()` bleibt wirkungslos
  (fetch-Zähler bleibt 1, `modus` bleibt `lesen`). Während ein Acquire noch läuft
  ebenso: `kannBearbeiten` false, kein Doppel-Request.
- (3) Fremdsperre beim Mount → `onBearbeiten()` versucht ein neues Acquire; gibt der
  Kollege frei, gelingt die Übernahme (`modus` = bearbeiten, `halterName` = undefined).
- Zusatz: `onFertig()` gibt jetzt aktiv per DELETE auf `/api/datensatz-locks/AUSGANG/42`
  frei (genau ein DELETE), nicht nur die Anzeige.

Unmount-Hygiene des Umbaus (Generationszählung) selbst geprüft — sauber:
- Unmount schickt DELETE mit `keepalive: true`; danach 0 weitere Heartbeats, auch nach
  120 s Fake-Zeit.
- Unmount während laufendem Acquire: `signal.aborted` ist true, der Request wird
  abgebrochen.
- Eine überholte Generation kann keinen State mehr setzen: hängender Mount-Acquire,
  dazwischen `freigeben()`, danach liefert der alte Acquire eine 200 nach —
  `kannBearbeiten` bleibt korrekt false.

🔴 Blockierend — neuer Befund: der Retry ist über die UI nicht erreichbar
`useDatensatzLock.ts:340` definiert `kannBearbeiten = lockUrl == null || status === 'acquired'`,
also "wir halten das Lock gerade". `BearbeitenLeiste.tsx:52` verwendet dieselbe Prop aber als
`disabled={!kannBearbeiten}` und dokumentiert sie ausdrücklich anders: "darf dieser Nutzer
überhaupt anfangen zu bearbeiten? false bedeutet, ein anderer hält das Lock gerade".
`kannBearbeiten` ist damit in genau den drei Zuständen false, in denen der neue Retry
gebraucht wird — der Knopf, der ihn auslösen müsste, ist dann deaktiviert.

Nachgestellt mit Hook + echter `BearbeitenLeiste`, verdrahtet genau so, wie Abschnitt 6 es
laut Plan tun soll (jeder Rückgabewert direkt als Prop, ohne Adapter):
- Nach Klick auf "Fertig" ist der Bearbeiten-Knopf `disabled` → der Nutzer kommt nicht
  zurück in den Bearbeiten-Modus.
- Zweiter Klick auf "Bearbeiten" nach der Freigabe: Acquire-Zähler bleibt bei 1 (erwartet 2)
  — der Klick erreicht den Handler nie.
- Fremdsperre: Knopf `disabled`; gibt der Kollege frei, bleibt der Übernahme-Klick
  ebenfalls wirkungslos (Zähler 1, erwartet 2).

Damit sind Befund (1) und (3) aus Review 1 aus Nutzersicht weiterhin offen: nach der
5-Minuten-Freigabe kommt niemand zurück ins Bearbeiten, und ein frei gewordener Datensatz
lässt sich nicht übernehmen — beides steht so in der Spec.

Abschnitt 6 kann das **nicht** selbst geradebiegen: `status` wird nicht exportiert, die Seite
kann "frisch freigegeben, wieder holbar" (`idle`) also gar nicht von "Acquire fehlgeschlagen"
(`error`) unterscheiden. Ohne diese Information ist kein korrekter Adapter baubar — die
Korrektur gehört in den Hook.

Nachweisbar sein muss (Formulierung offen, Verhalten nicht):
- Ein Test, der Hook und `BearbeitenLeiste` zusammen rendert: nach `onFertig()` bzw. nach
  `freigeben()` ist der Bearbeiten-Knopf **enabled**, und ein Klick darauf löst ein frisches
  `POST .../acquire` aus; erst bei 200 wechselt die Leiste auf "Fertig".
- Ein frei gewordener, vorher fremd gesperrter Datensatz lässt sich ohne Seiten-Neuladen
  übernehmen — entweder weil der Hook im Zustand `locked-by-other` weiter nachfragt und
  `kannBearbeiten` umschaltet, sobald frei, oder weil der Knopf klickbar bleibt und der
  Klick den Übernahme-Versuch macht.
- Der Schutz aus Befund (2) muss dabei erhalten bleiben: solange ein Acquire läuft oder
  fehlgeschlagen ist, darf niemand ohne gehaltenes Lock in den Bearbeiten-Modus kommen.

🟡 Hinweise (blockieren nicht):
- `useDatensatzLock.ts:190-196` und `:131-140` — im 409-Zweig von `acquire` und `heartbeat`
  wird nach dem zweiten `await` (`res.json()`) die Generation **nicht** erneut geprüft.
  Nachgestellt mit einer langsamen `json()`: ein durch `freigeben()` überholtes 409-Ergebnis
  schreibt danach noch `halterName` ("Veraltet Beispiel") und `status='locked-by-other'`.
  Harmlos, weil `heldRef` in beiden Fällen false bleibt (kein Phantom-Lock) und der nächste
  `onBearbeiten`-Klick den Zustand korrigiert — angezeigt wird aber kurz ein falscher Halter.
  Fix wäre eine zweite `gen !== generationRef.current`-Zeile nach dem `json()`.
- `useDatensatzLock.ts:207` — `startHeartbeat` setzt den Fehlschlag-Zähler zurück, aber nicht
  `verbindungWeg`. Nachgestellt: nach zwei fehlgeschlagenen Heartbeats, einer Freigabe und
  einem **erfolgreichen** erneuten Acquire steht `verbindungWeg` weiter auf true — die Leiste
  zeigt "Verbindung weg", obwohl das Lock gerade frisch geholt wurde. Löst sich beim nächsten
  erfolgreichen Heartbeat (30 s) von selbst. Ein `setVerbindungWeg(false)` im Erfolgspfad von
  `acquire` würde es sofort richtigstellen.
- `useDatensatzLock.ts:321` — nach einem Acquire-Fehler (500) ist der Bearbeiten-Knopf
  dauerhaft tot: `kannBearbeiten` false, `onBearbeiten` bewusst wirkungslos, und weil weder
  `status` noch ein `retry` exportiert werden, kann die Seite dem Nutzer auch nicht sagen,
  warum. Bleibt nur Neuladen. Sicher, aber unschön — passt gut zur ohnehin fälligen Frage,
  ob `status`/`retry` doch exportiert werden sollten.

Zum Bedenken des Agenten (umgeschriebener Bestandstest): kein Verlust an Prüfkraft.
`onBearbeiten hat keine Wirkung, wenn ein anderer den Datensatz haelt` hieß danach
`onBearbeiten() versucht bei einer Fremdsperre trotzdem ein neues Acquire, bleibt aber im
Modus "lesen"`. Die alte Zusicherung `expect(modus).toBe('lesen')` steht unverändert drin,
neu **dazu** kommt die Prüfung auf den zweiten Acquire-Aufruf — der Test ist also strikt
stärker geworden, nur der Titel bildet die neue Bedeutung ab. Ebenso beim `onFertig`-Test:
`waitFor` ersetzt die synchrone Prüfung, weil der Moduswechsel jetzt hinter dem DELETE
hängt, und ein eigener neuer Test sichert das DELETE ab. Gute Zugabe: das `vi.useRealTimers()`
im `afterEach` verhindert, dass ein abgebrochener Fake-Timer-Test alle folgenden Tests
in undurchsichtige Timeouts kippt.

Sicherheit / DSGVO:
- Nur die zwei erlaubten Dateien im Diff, kein Build-Output, keine Secrets. Testdaten nur
  Dummy-Namen (Erika Musterfrau, Thomas Beispiel, Anna Beispiel, Petra Beispiel).

5b und 5c: unverändert seit Review 1, weiterhin abgenommen.

## Abschnitt 5 — Task 5a Nachbesserung 2 (Coding-Agent)

- **Zeit:** 2026-09-04
- **Branch:** `lock/task-5a-hook`
- **Commit(s):** `de563fee` — fix(lock): kannBearbeiten so definieren, dass der Bearbeiten-Knopf den Retry ueberhaupt ausloesen kann

**Status:** fertig, alle Gates gruen (lint: 0 Fehler/1 vorbestehende Warnung `BelegeKasseEditor.tsx:1204`; test: neue Datei 30/30 gruen, dreifach wiederholt stabil; Gesamtsuite 1003 Tests gruen, ein sauberer Lauf ohne Fremd-Timeouts; build: gruen inkl. `tsc`, `src/main/resources/static/` vor Commit zurueckgesetzt).

**Ausgangslage:** Runde-1-Fixes (Retry nach freigeben(), Schutz bei 500/loading, Uebernahme nach Fremdsperre, DELETE bei onFertig) waren im Hook korrekt, aber ueber die Oberflaeche nie erreichbar: `kannBearbeiten = status === 'acquired'` machte den Bearbeiten-Knopf in BearbeitenLeiste (die ihn ueber `disabled={!kannBearbeiten}` steuert) genau in den beiden Zustaenden unklickbar, in denen der Retry gebraucht wird -- nach "Fertig" (`idle`) und bei Fremdsperre (`locked-by-other`).

**Was gemacht wurde:**
- `kannBearbeiten` umdefiniert von "wir halten das Lock" (`status === 'acquired'`) auf "ein Klick auf Bearbeiten ist gerade sinnvoll" (`lockUrl == null || (status !== 'loading' && status !== 'error')`) -- true bei `idle`, `acquired` UND `locked-by-other`; nur bei `loading`/`error` false. `onBearbeiten()` selbst war bereits korrekt verdrahtet (haengt seit Nachbesserung 1 nicht an `kannBearbeiten`, sondern an `heldRef`/`status`) und brauchte keine Codeaenderung.
- `status` (der interne `DatensatzLockStatus`-Wert) wird jetzt roh im Rueckgabewert exportiert und der Typ dafuer exportiert, damit Abschnitt 6 bei `error` einen eigenen Hinweis zeigen kann.
- Regressionsfix in `startHeartbeat()`: setzt jetzt zusaetzlich zum Fehlschlag-Zaehler auch `verbindungWeg` explizit auf `false` zurueck -- vorher blieb eine "Verbindung weg"-Warnung aus einem VORHERIGEN Zyklus (vor `freigeben()`/Lock-Verlust) nach einem erfolgreichen erneuten Acquire faelschlich stehen.
- Fuenf neue Tests **zuerst** geschrieben; verifiziert, dass sie gegen den Runde-1-Stand rot waren (temporaer alle drei Aenderungen per `sed` zurueckgedreht, Testlauf gemacht, dann wiederhergestellt -- Diff danach identisch mit dem Stand vor der Ruecknahme):
  1. **"nach onFertig() ist der Bearbeiten-Knopf wieder aktiv..."** (Hook + echte `BearbeitenLeiste` zusammen gerendert) — rot: `expect(element).toBeEnabled()` schlug fehl, Knopf blieb `disabled=""` nach "Fertig".
  2. **"bei Fremdsperre ist der Bearbeiten-Knopf aktiv..."** (Hook + echte `BearbeitenLeiste`) — rot: derselbe `toBeEnabled()`-Fehler, Knopf blieb bei Fremdsperre deaktiviert.
  3. **"nach einem Acquire-Fehler (500) ist der Bearbeiten-Knopf deaktiviert..."** — blieb bereits vorher gruen (war nie kaputt, dient als Regressionswaechter fuer den Schutz aus Runde 1).
  4. **"verbindungWeg wird beim naechsten erfolgreichen Acquire wieder auf false gesetzt..."** — rot: `expected true to be false`.
  5. **status-Tests ("wird nach 500 zu 'error'" / "wird nach freigeben() zu 'idle'")** — rot: `expected undefined to be 'error'`/`'acquired'` (Feld existierte im reale Rueckgabewert nicht).
  Insgesamt waren beim Ruecknahme-Lauf 23 von 30 Tests rot (auch mehrere bestehende, die auf die neue kannBearbeiten-Bedeutung umgestellt wurden, siehe naechster Punkt).
- Alle bestehenden Tests, die `kannBearbeiten` als Proxy fuer "Mount-Acquire erfolgreich" nutzten, auf das neu exportierte `status === 'acquired'` umgestellt (praeziser als vorher). Tests, die `kannBearbeiten`/den 409-Zustand direkt pruefen (409-Test, Heartbeat-409-Test, Unmount-ohne-DELETE-Test), auf die NEUE Bedeutung umgeschrieben: `kannBearbeiten` ist bei Fremdsperre jetzt `true` (vorher `false`), zusaetzlich wird `status === 'locked-by-other'` explizit geprueft, damit die Tests weiterhin praezise auf den Sperr-Zustand selbst pruefen, nicht nur zufaellig auf denselben Boolean-Wert wie vorher.
- Eine echte Falle in der eigenen Testinfrastruktur gefunden und umgangen: `userEvent.click()` haengt sich unter `vi.useFakeTimers()` zuverlaessig auf (auch mit `{ delay: null }`), vermutlich weil jsdom/Pointer-Events intern auf `requestAnimationFrame` wartet, das mitgefaked wird. Der Fremdsperre-UI-Test nutzt darum echte Timer + `waitFor`; die Heartbeat-Fortsetzung nach erfolgreichem Retry ist bereits durch zwei reine Hook-Tests (identischer `acquire()`/`startHeartbeat()`-Codepfad) abgedeckt, siehe Kommentar im Test.

**Bedenken / Abweichungen vom Plan:**
- Der Kommentar in `BearbeitenLeiste.tsx` ("false bedeutet, ein anderer haelt das Lock gerade") stimmt mit der neuen `kannBearbeiten`-Bedeutung nicht mehr ueberein (`kannBearbeiten` ist bei Fremdsperre jetzt `true`, nicht `false`). Datei bewusst NICHT angefasst (nicht Teil dieses Tasks, laut Auftrag uebernimmt Abschnitt 6 die Anpassung dort).
- Kein automatisches Hintergrund-Polling waehrend `locked-by-other` eingebaut (weiterhin nicht gefordert; die Uebernahme passiert weiterhin nur auf einen expliziten Klick hin, wie in der Entscheidung zur Semantik vorgegeben: "ein Acquire ist das Greifen selbst, das darf nicht automatisch fuer jemanden passieren, der nur liest").

## Abschnitt 5 — Review 3 (Abschnitts-Reviewer)

Zeit: 2026-09-04T19:35:00Z
Branch: claude/eloquent-ramanujan-gz0w2t, Merge der Nachbesserung 2 (de563fee)
Ampel: 🟢 abgenommen — der 🔴 aus Review 2 ist belegt behoben, kein neuer blockierender Befund.

Merge:
- `lock/task-5a-hook` per `--no-ff`, kein Konflikt. Diff genau die zwei erlaubten Dateien
  (`useDatensatzLock.ts` +44/−9, `useDatensatzLock.test.tsx` +236/−26). Kein anderes File.

Selbst gemessene Zahlen (alle Läufe solo, synchron im Vordergrund):
- `./mvnw -B test` → **2454 Tests, 0 Failures, 4 Errors** — exakt die 4 bekannten
  Baseline-Errors. Unverändert, Backend nicht berührt.
- `npm run lint` → 0 Fehler, genau 1 Warnung (BelegeKasseEditor.tsx:1204) = Baseline.
- `npm run test` → **85 Dateien / 1003 Tests, alle grün**. Review 2 war 85/997 → +6.
  Die Hook-Testdatei enthält jetzt 30 Tests. Kein Zeitschranken-Flackern.
- `npm run build` → grün, Build-Output zurückgesetzt, `git status` sauber.

🔴 aus Review 2 — **behoben**, mit eigener Wegwerf-Testdatei nachgestellt (Hook + echte
`BearbeitenLeiste` zusammen gerendert, ohne Adapter; danach gelöscht, Baum sauber):
- Nach Klick auf "Fertig" ist der Bearbeiten-Knopf **enabled** (`disabled=false`), ein Klick
  darauf löst ein zweites `POST .../acquire` aus (Zähler 1 → 2). "Fertig" erscheint dabei
  **nicht** schon während des laufenden Acquire, sondern erst nach der 200 — geprüft mit
  einem künstlich hängenden Acquire.
- Bei Fremdsperre (409 beim Mount) ist der Knopf **enabled**; der erste Klick löst einen
  Übernahmeversuch aus (Zähler 1 → 2) und bleibt bei erneutem 409 im Modus "lesen"; der
  zweite Klick übernimmt, sobald der Kollege freigegeben hat ("Fertig" erscheint).
- Bei Acquire-Fehler 500 ist der Knopf **disabled** und der Acquire-Zähler bleibt bei 1 —
  der Schutz aus Review-1-Befund (2) ist also erhalten geblieben.
- Zusatz geprüft: beim Übernahmeversuch werden `halterName`/`seit` wirklich aufgefrischt
  (Thomas Beispiel/"10" → Petra Beispiel/"5"), `modus` bleibt auf "lesen".

Weitere Punkte aus dem Prüfauftrag, alle selbst nachgestellt:
- `verbindungWeg` ist nach einem erfolgreichen erneuten Acquire **false** (vorher blieb die
  "Verbindung weg"-Warnung stehen). Mein 🟡 aus Review 2 zu `:207` ist damit erledigt.
- `status` wird exportiert und liefert `'error'` nach einem 500 und `'idle'` nach
  `freigeben()`; bei `'idle'` ist `kannBearbeiten` true, bei `'error'` false.
- Heartbeat läuft nach einem erfolgreichen Retry wieder (1 Heartbeat nach 30 s, reiner
  Hook-Test mit Fake-Timern).

Mutationsprobe (Quellstand danach byte-identisch, `git status` leer):
- `kannBearbeiten` zurück auf `lockUrl == null || status === 'acquired'` → **7 Tests rot**,
  darunter **beide** UI-Tests aus "Zusammenspiel mit der echten BearbeitenLeiste"
  ("nach onFertig() ist der Bearbeiten-Knopf wieder aktiv …" und "bei Fremdsperre ist der
  Bearbeiten-Knopf aktiv …"), jeweils `expected false to be true`. Der dritte UI-Test
  (500 → disabled) bleibt korrekterweise grün, weil die Mutation diesen Fall nicht berührt.
  Die neue Semantik ist damit wirklich abgesichert.

Zur gemeldeten Fake-Timer-Falle (Prüfpunkt 5) — Begründung trägt, keine Lücke:
Der UI-Test zur Fremdsperre läuft mit echten Timern, weil `userEvent.click()` unter
`vi.useFakeTimers()` hängt. Seine Behauptung, die Heartbeat-Fortsetzung sei durch zwei
reine Hook-Tests abgedeckt, habe ich nachgeprüft und bestätigt: beide rufen
`onBearbeiten()` nach einem `freigeben()` und durchlaufen damit exakt denselben
`acquire()` → `startHeartbeat()`-Pfad; der einzige Unterschied ist der Auslöser (direkter
Aufruf statt Klick), und `onBearbeiten` ist dieselbe Funktionsreferenz, die als `onClick`
in die Leiste geht. Ich habe die Heartbeat-Fortsetzung nach Retry zusätzlich selbst mit
Fake-Timern nachgestellt (1 Heartbeat nach 30 s). Die drei UI-Tests decken genau das ab,
was nur der Klickweg zeigen kann: dass `kannBearbeiten` am Knopf ankommt und der Klick den
Handler erreicht. Nichts bleibt unbelegt.

🟡 Hinweise (blockieren nicht):
- `useDatensatzLock.ts:190-196` und `:131-140` — die fehlende Generationsprüfung nach dem
  zweiten `await` (`res.json()`) im 409-Zweig ist **weiter offen**. Erneut nachgestellt mit
  einer langsamen `json()`: ein von `freigeben()` überholtes 409-Ergebnis schreibt danach
  noch `halterName` ("Veraltet Beispiel") und `status='locked-by-other'`. Weiterhin harmlos
  (`heldRef` bleibt false, kein Phantom-Lock; der nächste Bearbeiten-Klick korrigiert den
  Zustand, und der ist in `locked-by-other` jetzt sogar klickbar), aber es zeigt kurz einen
  falschen Halter. Fix wäre eine zweite `gen !== generationRef.current`-Zeile nach dem
  `json()`. Kandidat für Abschnitt 6 oder einen Aufräum-Task.
- `useDatensatzLock.test.tsx` — statt echter Timer ginge unter Fake-Timern auch
  `userEvent.setup({ advanceTimers: vi.advanceTimersByTime })`. Rein optional; die
  gewählte Aufteilung ist sauber begründet und vollständig.
- `useDatensatzLock.ts:321` — nach einem Acquire-Fehler bleibt der Knopf tot und nur ein
  Neuladen hilft. Jetzt aber immerhin behandelbar, weil `status` exportiert wird: die Seite
  in Abschnitt 6 kann bei `'error'` einen eigenen Hinweis zeigen.

Sicherheit / DSGVO:
- Nur die zwei erlaubten Dateien im Diff, 0 Dateien unter `resources/static`, keine Secrets.
  Testdaten nur Dummy-Namen (Erika Musterfrau, Thomas Beispiel, Anna Beispiel,
  Petra Beispiel, Klaus Beispiel).

Abschnitt 5 damit vollständig: 5a nach zwei Nachbesserungen abgenommen, 5b und 5c seit
Review 1 unverändert abgenommen.

## Abschnitt 6 — Task 6c (Coding-Agent)

- Zeit: 2026-09-04, 19:54
- Branch: lock/task-6c-ausgangs-controller
- Commits: b01602a7 (Teil 1 — Lock-Service-Tausch), 0e49d9b0 (Teil 2 — Versionskonflikt-Durchgriff)
- Status: grün — 45/45 Tests im Controller, Backend-Gesamtlauf 2462 Tests, 0 Failures, genau die 4 vorbestehenden Baseline-Errors (AuditChainRepairIntegrationTest 2x, AuditHashRoundtripDiagnoseTest 2x, CannotCreateTransaction)

### Was gemacht wurde

**Teil 1 — Abhängigkeitstausch.** `DokumentLockService`/`TYP_AUSGANG`/`DokumentLockDto` durch `DatensatzLockService`/`SperrbarerTyp.AUSGANG`/`DatensatzLockDto` ersetzt: Import, Feld, `create` (Lock-Vergabe für den Ersteller mit `DatensatzLockDto.ACQUIRED`-Vergleich), `update` (`isHeldBy`). Verhalten unverändert. Bestehende Tests auf `@MockBean DatensatzLockService` und `any(SperrbarerTyp.class)`/`eq(SperrbarerTyp.AUSGANG)` umgestellt. Test ergänzt, der den konkreten Typ-Parameter prüft (`verify(dokumentLockService).isHeldBy(eq(SperrbarerTyp.AUSGANG), eq(1L), eq(42L))`). Mutationsprobe gefahren: Typ im Produktivcode testweise auf `EINGANG` gedreht → Test wurde rot (`expected 200, was 200` — Verify schlug fehl, Status blieb 200 aber der `verify`-Aufruf auf `eq(SperrbarerTyp.AUSGANG)` scheiterte), danach zurückgedreht, wieder grün.

**Teil 2 — Versionskonflikt-Durchgriff.** Für `buchen`, `emailVersendet`, `pdfSpeichern`, `stornieren` und `delete` je ein `catch (ObjectOptimisticLockingFailureException e) { throw e; }` vor dem allgemeinen `catch (RuntimeException e)` ergänzt — exakt das Muster aus `update`. `create` und `getAbrechnungsverlauf` bewusst nicht angefasst (Begründung aus dem Auftrag: `create` legt neu an, kein Versionskonflikt möglich; `getAbrechnungsverlauf` schreibt nicht).

Rote Meldungen je Speicherweg (vor der Änderung, mit `ObjectOptimisticLockingFailureException` vom Service):
- `buchen`: `versionskonfliktBeimBuchenGibt409` → `Status expected:<409> but was:<400>`
- `emailVersendet`: `versionskonfliktBeiEmailVersendetGibt409` → `Status expected:<409> but was:<400>`
- `pdfSpeichern`: `versionskonfliktBeiPdfSpeichernGibt409` → `Status expected:<409> but was:<400>`
- `stornieren`: `versionskonfliktBeimStornierenGibt409` → `Status expected:<409> but was:<400>`
- `delete`: `versionskonfliktBeimLoeschenGibt409` → `Status expected:<409> but was:<400>`

Je Speicherweg zusätzlich ein Regressionstest (gewöhnlicher `RuntimeException` liefert weiterhin 400): für `buchen` und `stornieren` und `delete` waren solche Tests bereits vorhanden (`doppeltessBuchenGibt400`, `doppelteStornierungGibt400`, `loeschenGebuchtGibt400`) und blieben unverändert grün; für `emailVersendet` und `pdfSpeichern` neu ergänzt (`gewoehnlicherFehlerBeiEmailVersendetGibt400`, `gewoehnlicherFehlerBeiPdfSpeichernGibt400`).

### Bedenken / Abweichungen vom Plan

Keine. Plan konnte 1:1 umgesetzt werden, Vorbild-Commit `e9c84599` (LieferantDokumentController) und Muster aus `update` (Z. ~226–231) passten direkt.

## Abschnitt 6 — Task 6b (Coding-Agent)

Zeit: 2026-09-04T18:14:00Z
Branch: lock/task-6b-lieferant-modal
Commit(s): edcd32bd (LieferantDokumentModal-Umstellung), 9574c39e (Playwright-Spec + Fix in designPruefung)
Status: fertig, alle Gates gruen

Was gemacht wurde:
- `LieferantDokumentModal.tsx`: `useDocumentLock`/`DocumentLockedModal` raus, `useDatensatzLock("EINGANG", ...)` rein (Mount-Bedingung `isOpen && dokument ? dokument.id : null` unveraendert). `BearbeitenLeiste` + `GesperrtHinweis` in einer neuen Statuszeile direkt unter dem Header verdrahtet (links Hinweis, rechts der Umschalter). `useIdleTimer` mit `enabled: lock.modus === "bearbeiten"`, `onIdle` ruft `lock.freigeben()`.
- Design-Entscheidung, nicht woertlich im Auftrag: `useDatensatzLock` haelt `modus` laut eigenem Kommentar bewusst auf "lesen", auch direkt nach einem erfolgreichen stillen Mount-Erwerb — sonst waere kein Klick auf "Bearbeiten" mehr noetig, um den Retry-Pfad ueberhaupt zu benutzen. Fuer DIESES Modal sollte "Datensatz frei bekommen beim Oeffnen" aber sofort editierbar sein (so verlangt es sowohl der Auftrag: "acquired ⇒ Formular frei, BearbeitenLeiste im Modus bearbeiten" als auch die spaeter nachgereichte Playwright-Vorgabe: "oeffnen mit freiem Lock ⇒ Formular frei, Leiste im Modus bearbeiten"). Loesung: ein Effekt auf Modal-Ebene, der bei status===acquired und modus===lesen einmalig `lock.onBearbeiten()` aufruft — das Lock ist bereits gehalten (`heldRef` true), darum rein synchrones Umschalten ohne zusaetzlichen Request. Nach "Fertig" oder Idle-Freigabe (status wird idle, nicht acquired) greift der Effekt nicht, das Modal bleibt bewusst im Lesen-Modus bis zum naechsten expliziten Klick. `useDatensatzLock.ts` selbst wurde dafuer NICHT angefasst.
- formGesperrt = lock.modus !== bearbeiten deckt damit einheitlich idle/loading/locked-by-other/error ab (alle Formularfelder + Speichern-Knopf disabled), ohne jeden Zustand einzeln abzufragen.
- Statuszeile: locked-by-other ⇒ GesperrtHinweis mit halterName/seit; loading ⇒ kleine Inline-Zeile "Sperre wird geprüft…" mit Spinner (kein leerer Kasten); error ⇒ Inline-Hinweis (role=alert) UND Toast, beide mit demselben Wortlaut "Sperre konnte nicht geholt werden — bitte neu laden." (LOCK_FEHLER_TEXT-Konstante, ein Toast-Trigger-Effekt reagiert nur auf lock.status, nicht auf das bei jedem Toast neu erzeugte toast-Objekt, sonst Endlosschleife).
- Speichern-Knopf bei gesperrtem Formular disabled mit title-Tooltip, der je nach Zustand erklaert warum (Fremdsperre/Fehler/erst Bearbeiten klicken).
- Schliessen (X-Button, Hintergrund-Klick, "Abbrechen") ruft jetzt handleClose(), das AKTIV lock.freigeben() aufruft (nicht nur die passive Keepalive-Freigabe im Hook bei einem lockUrl-Wechsel) — noetig, weil das Modal beim Parent (EingangsrechnungenTab/LieferantDokumenteTab) dauerhaft gemountet bleibt und isOpen nur umschaltet, ein echter Unmount also nie passiert.
- handleSave: useKonfliktMeldung("Dokument").pruefeAntwort(res) vor der !res.ok-Pruefung eingehaengt (409 ⇒ Neu-laden-Meldung, kein onSave). Erfolgreicher Speichervorgang schliesst jetzt ueber handleClose() (gibt dabei auch die Sperre frei). Fehlerfall zusaetzlich toast.error(...) ergaenzt (vorher nur stiller setError, Verstoss gegen die Toast-Pflicht — direkt mit angefasst, weil ich exakt diese Funktion ohnehin fuer den Konflikt-Check aendern musste).
- Zusaetzlich role="dialog"/aria-modal/aria-labelledby am Modal-Wurzel-Element ergaenzt (fehlte bisher komplett — Icon-only-X-Button bekam dabei auch ein aria-label="Schließen"). Noetig geworden, weil die Lieferanten-Detailseite im Hintergrund einen eigenen "Bearbeiten"-Knopf hat (Firmenstammdaten) und getByRole('button', { name: 'Bearbeiten' }) im Playwright-Test sonst zwei Treffer liefert.
- LieferantDokumentModal.test.tsx (neu, 13 Tests): Mount/Acquire-Route, freies Lock (Formular frei ohne Extra-Klick + Fertig-Flow), Fremdsperre (GesperrtHinweis + Uebernahme-Klick), loading (Ladehinweis, haengender Fetch aufgeloest), error (Inline + Toast, 2x derselbe Wortlaut), Schliessen ueber X und Abbrechen (aktives DELETE), Speichern erfolgreich/409/500, Idle-Timeout nach 5 Min mit Countdown-Pruefung bei 60s. Fetch gefakt wie in useDatensatzLock.test.tsx (ein dispatcher-artiger vi.fn(), keine echten Requests).
- Mutationsproben (jeweils durchgefuehrt und zurueckgesetzt, git diff am Ende leer):
  - formGesperrt fest auf false ⇒ 5 Tests rot (Fremdsperre-, loading-, error-Formular-disabled-Pruefungen und der Idle-Test).
  - Auto-Bearbeiten-Effekt stillgelegt ⇒ 9 Tests rot (alles, was ein sofort editierbares Formular nach dem Oeffnen voraussetzt).
  - pruefeAntwort-Aufruf stillgelegt ⇒ genau der 409-Test rot, alle anderen 12 bleiben gruen.
- Playwright-Design-Pruefung (per Zusatzauftrag): git merge --no-edit claude/eloquent-ramanujan-gz0w2t durchgefuehrt (sauber, nur neue Dateien/Konfig). Neue Spec react-pc-frontend/e2e/lieferant-dokument-modal.spec.ts, 3 Faelle (freies Lock inkl. Fertig-Flow, Fremdsperre inkl. Uebernahme, Fehlerfall) x 2 Groessen = 6 Tests, alle gruen (E2E_PORT=5175 npx playwright test e2e/lieferant-dokument-modal.spec.ts). /api komplett gestubbt (auth/me, Lieferanten-Detail inkl. dokumente, PDF-Download als Mini-PDF, die drei Lock-Routen).
- Blockierender Fund in der neu gemergten e2e/hilfen/design.ts (nicht in meiner Datei-Liste, aber ohne Fix war die Pflicht-Design-Pruefung fuer niemanden lauffaehig): designPruefung() nutzte testInfo.outputPath('..', '..', 'design', ...) — Playwrights outputPath() verweigert grundsaetzlich jedes Verlassen des Pro-Test-Ordners per .., unabhaengig von Testname/Projekt (Quelltext-Beleg: getContainedPath() in playwright/lib/worker/workerProcessEntry.js akzeptiert nur Pfade, die outputDir selbst sind oder darunter liegen). Jeder einzelne Aufruf schlug mit "The outputPath is not allowed outside of the parent directory" fehl — 100%-Fehlerquote, kein Rand- oder Lastfall. Fix: auf testInfo.project.outputDir (den stabilen, nicht pro-Test-gebundenen Projekt-Ausgabeordner) umgestellt, Ordner selbst per fs.mkdirSync angelegt — ergibt exakt den in der Skill-Doku zugesagten Pfad test-results/design/<name>--<projekt>.png. Als eigener Commit, damit der Review-Agent den Fund getrennt vom Modal-Diff sieht. Vermutlich stossen die beiden anderen Abschnitt-6-Agenten auf denselben Fehler.
- Screenshots (angeschaut, nicht nur erzeugt) unter react-pc-frontend/test-results/design/: lieferant-modal-bearbeiten--{pc-14zoll,pc-monitor}.png, lieferant-modal-gesperrt--{pc-14zoll,pc-monitor}.png, lieferant-modal-fremdes-lock--{pc-14zoll,pc-monitor}.png, lieferant-modal-fehler--{pc-14zoll,pc-monitor}.png. Rose/slate durchgehend, Lucide-Icons, kein Emoji, GesperrtHinweis/Fehlerzeile klar von Formular abgesetzt, "Bearbeiten"/"Fertig" beide Groessen ohne Scrollen sichtbar, keine Ueberschneidungen (automatischer Check gruen). Volle Sechs-Fragen-Bewertung macht laut aktualisiertem Auftrag der separate Design-Reviewer nach dem Merge.
- Gates (nur eigene Datei/Spec, nicht die Gesamtsuite — Vorgabe waehrend der Bearbeitung geaendert): npx vitest run src/components/LieferantDokumentModal.test.tsx → 13/13 gruen. npm run lint → 0 Fehler, genau die 1 vorbestehende Warnung (BelegeKasseEditor.tsx:1204). npm run build → gruen, Build-Output in src/main/resources/static/ danach zurueckgesetzt, git status sauber vor dem Commit.

Bedenken / Abweichungen vom Plan:
- Kein automatisches Speichern beim Idle-Timeout eingebaut. handleSave fasst Zahlungs-/Skontodaten zusammen (bezahlt, zahlungsziel, skontoTage/-Prozent, tatsaechlichGezahlt), die der Nutzer haeufig ueber mehrere Felder hinweg konsistent halten muss (z.B. "Bezahlt am" + "Tatsaechlich gezahlt" gehoeren zusammen) — ein Auto-Save mitten in einer unvollstaendigen Eingabe koennte einen inkonsistenten Zwischenstand ohne Bestaetigung persistieren. Stattdessen nur freigeben() + zurueck in "lesen", wie im Auftrag als Ausweichoption vorgesehen.
- Auto-Uebergang in den Bearbeiten-Modus nach stillem Mount-Erwerb (siehe oben) ist eine eigene Design-Entscheidung on top von useDatensatzLock, nicht woertlich so im urspruenglichen Auftrag ausformuliert, aber durch dessen eigene Zustandsliste UND die spaeter nachgereichte Playwright-Spec-Vorgabe (freies Lock ⇒ ... Leiste im Modus bearbeiten) beide unabhaengig voneinander nahegelegt/bestaetigt. useDatensatzLock.ts/BearbeitenLeiste.tsx/GesperrtHinweis.tsx dabei nicht angefasst.
- e2e/hilfen/design.ts liegt ausserhalb der urspruenglich genannten Datei-Liste (LieferantDokumentModal.tsx + Test) — angefasst, weil sie ohne den Fix fuer niemanden nutzbar war (siehe Fund oben). Eigener Commit, minimal-invasiv (nur der Pfad-Aufbau geaendert, kein Verhalten sonst).
- role="dialog" am Modal ist eine kleine, nicht explizit beauftragte Zusatzaenderung an LieferantDokumentModal.tsx (einer meiner beiden erlaubten Dateien) — Barrierefreiheits-Nachbesserung plus Notwendigkeit fuer eindeutige Playwright-Selektoren, kein Verhaltensunterschied fuer Nutzer ohne Screenreader.
- "Zuordnen"-Knopf (Projekt zuordnen, im Warnhinweis "Kein Projekt zugeordnet") bleibt bei gesperrtem Formular klickbar, weil er im Bestandscode ohnehin keinen onClick-Handler hat (vorbestehender toter Knopf, ausserhalb des Task-Scopes) — nicht mit angefasst.

## Abschnitt 6 — Task 6a (Coding-Agent)

- Zeit: 2026-09-04, ca. 20:00–20:22 (lokale Sessionzeit)
- Branch: lock/task-6a-editor-seite
- Commits:
  - 9e39965e — fix(dokument-editor): eigene Lock-Logik raus, Props fuer Seiten-Lock, neuer X-Button-Ablauf
  - 7d490f82 — test(e2e): Playwright-Spec fuer den X-Button-Ablauf im Dokument-Editor
  - 0b9dd007 — Merge branch 'claude/eloquent-ramanujan-gz0w2t' (Design-System-Skill/Hook + gemeinsame design.ts-Reparatur des Orchestrators nachgezogen; Konflikt in e2e/hilfen/design.ts mit `--theirs` zugunsten der Orchestrator-Version aufgeloest, siehe Bedenken)
  - fbf49d4d — fix(e2e): navigate(-1)-Race in der eigenen Schliessen-Probe abgefangen (Nachweislauf nach dem Merge)
- Status: 🟢 fertig. `npx vitest run src/components/document-editor/index.test.tsx src/components/lock/TabSchliessenHinweis.test.tsx` → 21/21 gruen. `npm run lint` → 0 Fehler, genau die 1 vorbestehende Warnung (BelegeKasseEditor.tsx:1204). `npm run build` → gruen, Build-Output in src/main/resources/static/ danach zurueckgesetzt, git status vor jedem Commit nur eigene Dateien. Eigene Playwright-Spec: 6/6 gruen (3 Faelle x 2 Groessen).

**Neue Props (verbindlich fuer andere Tasks):**
- `readOnly?: boolean` (Default `false`) — "die Seite haelt kein Lock, der Nutzer darf nur lesen". Fliesst additiv in `isLocked` ein: `isLocked = !!(readOnly || dokument?.storniert || dokument?.digitalAngenommen || (dokument?.gebucht && invoiceTypes.includes(dokument.typ)))`.
- `onLockFreigeben?: () => Promise<void>` — "gib die Sperre der Seite aktiv frei, warte auf Server-Bestaetigung". Wird im X-Button-Ablauf zwischen Speichern und Tab-Schliessen aufgerufen. Ist der Prop nicht gesetzt, faellt der Editor komplett auf das bisherige Verhalten zurueck (ruft stattdessen `onClose()`) — siehe Bedenken zur Uebergangslogik.

**Was gemacht wurde:**

1. Eigene Lock-Logik entfernt (Z. ~1146–1200 alt): zweiter, nie gestoppter Heartbeat auf `/api/dokument-locks/AUSGANG/{id}/heartbeat` (inkl. sofortigem Ping bei Mount, visibilitychange-/focus-Listener) sowie `tryAcquireLock` (eigenes Acquire, als 409-Retry in `handleSave` verdrahtet) komplett raus. `handleSave`s 409-Zweig macht jetzt keinen Retry mehr, sondern zeigt den Server-Text (oder einen Fallback) als `toast.warning` und bricht ab — ein 409 kann nur noch eine echte Fremdsperre oder eine tatsaechlich abgelaufene Seiten-Sperre bedeuten, beides kann der Editor selbst nicht reparieren.
   - Roter Test (`index.test.tsx`, neue Describe "kein eigenes Lock mehr"): "sendet nach Mount und Unmount keinen Request an /api/dokument-locks/ oder /api/datensatz-locks/" — gegen den Alt-Stand rot mit Timeout/Treffer im `lockAufrufe()`-Filter (der alte Code pingte sofort bei Mount); nach dem Entfernen gruen.
   - `/api/dokument-locks/`-Stub in `index.test.tsx`s `mockFetch` entfernt (Vorgabe), Catch-all uebernimmt jetzt auch fuer diese Pfade `[]`.
2. `isLocked` um `readOnly ||` erweitert, restliche fachliche Bedingungen (storniert/digitalAngenommen/gebuchte Rechnung) unveraendert stehen gelassen.
   - Roter Test: "verhaelt sich wie ein gesperrtes Dokument (kein Material-Knopf, kein Speichern)" mit `readOnly: true` — vor der Aenderung rot (Prop wurde ignoriert, Buttons blieben sichtbar), danach gruen.
3. Neuer X-Button-Ablauf (`tabSchliessen()`, ersetzt die 3 bisherigen direkten `onClose()`-Aufrufe in `handleClose` und `UnsavedChangesModal`): `await onLockFreigeben?.()` → `window.close()` → 150ms-Timeout → falls Komponente noch gemountet: `TabSchliessenHinweis` statt Editor rendern.
   - Roter Test 1 ("haelt die Reihenfolge speichern -> onLockFreigeben -> window.close ein"): Spies/Reihenfolge-Array, vor der Aenderung rot (Timeout beim Warten auf `window.close`, weil der Alt-Code nur den No-op-`onClose` aufrief).
   - Roter Test 2 ("zeigt den Tab-Schließen-Hinweis, wenn window.close wirkungslos bleibt"): `window.close` als wirkungsloser Spy, danach `findByText(...)` auf den Hinweistext — vor der Aenderung rot (Timeout, kein Hinweis existierte). Fallstrick dabei: `@dnd-kit/core`s `DndContext` rendert selbst dauerhaft eine unsichtbare `role="status"`-Live-Region ohne Namen — `getByRole('status')` griff daher immer zuerst die falsche, leere; Fix war `findByText` + separate `toHaveAttribute('role','status')`-Pruefung.
4. `handleSave`-Fehlerfall bleibt wie gehabt (`toast.error`), aber jetzt zusaetzlich nachgewiesen: kein `onLockFreigeben`, kein `window.close`, Editor/Warn-Dialog bleiben offen.
   - Test ("bricht bei fehlgeschlagenem Speichern ab: ...") — dieser Test war bereits mit dem Alt-Code gruen (der Alt-Code rief bei fehlgeschlagenem Speichern ohnehin nie `onLockFreigeben`/`window.close` auf), bleibt als Regressionsschutz stehen.
5. `TabSchliessenHinweis.tsx` (neu, `components/lock/`) + Test (neu): ruhige Vollbild-Seite (`fixed inset-0`, `bg-slate-50`), kein Modal, kein Button, Lucide `CheckCircle2` in rose-100-Kachel, Text exakt laut Spec: "Dokument gespeichert und freigegeben — du kannst diesen Tab jetzt schließen." `role="status"` statt `role="dialog"`. 6 Tests, alle testgetrieben (Komponente existierte anfangs nicht → Modul-Resolve-Fehler als roter Ausgangspunkt).
6. Playwright-Design-Pruefung (Zusatzauftrag waehrend der Bearbeitung): neue Spec `react-pc-frontend/e2e/dokument-editor-tab-schliessen.spec.ts` + Hilfsdatei `e2e/hilfen/dokument-editor.ts` (Stubs fuer `/api/auth/me`, `/api/firma`, `/api/dokument-locks/**` [altes Seiten-Lock, siehe Bedenken], `/api/ausgangs-dokumente/1`, `/api/dokument-generator/preview`, `/api/formulare/templates/selection`). 3 Faelle x 2 Groessen (`pc-14zoll`/`pc-monitor`) = 6/6 gruen: (a) kein sofortiger Heartbeat nach dem Laden, (b) X-Button → Warn-Dialog → "Speichern & Schließen" speichert wirklich (PUT mit korrekter Adresse) und stoesst den bisherigen Schliess-Weg der Seite an, (c) fehlgeschlagenes Speichern → Toast, Editor bleibt offen, kein Schliessversuch. Ausgefuehrt mit `E2E_PORT=5179` (5174 war von einem fremden, bereits laufenden Prozess belegt — siehe Bedenken).

**Design-Screenshots (angeschaut, kurzer Blick laut aktualisiertem Auftrag):**
- `react-pc-frontend/test-results/design/dokument-editor-vor-schliessen--pc-14zoll.png` (+ `--pc-monitor.png`): Kopfzeile mit genau einem rose-600-Primaerknopf ("PDF"), alle anderen Aktionen neutral/slate — passt zum Design-System. Keine Ueberschneidungen, PDF-Knopf ohne Scrollen sichtbar (automatischer Check gruen).
- `react-pc-frontend/test-results/design/dokument-editor-ungespeichert-warnung--pc-14zoll.png` (+ `--pc-monitor.png`): Warn-Dialog mittig, abgedunkelter Hintergrund, "Speichern & Schließen" rose-gefuellt (Primaeraktion), "Nicht speichern" rose-Outline (destruktiv abgesetzt), "Abbrechen" neutral — Button-Hierarchie stimmt mit den FRONTEND_UI.md-Vorgaben ueberein.
- `TabSchliessenHinweis` selbst konnte NICHT per Playwright im echten Browser fotografiert werden — Begruendung siehe Bedenken.

**Bedenken / Abweichungen vom Plan:**

- **`document-editor/types.ts` mitgeaendert**, obwohl nicht in der urspruenglichen Datei-Liste genannt: die `DocumentEditorProps`-Schnittstelle liegt dort (nicht in `index.tsx`), also mussten die beiden neuen Props dort ergaenzt werden. Kein anderer Task fasst diese Datei an. Der Orchestrator hat das waehrend der Bearbeitung bereits als unbedenklich bestaetigt.
- **`e2e/hilfen/design.ts` zweimal angefasst, am Ende aber NICHT meine Version**: die vom Orchestrator gemergte Hilfsdatei war kaputt (`testInfo.outputPath('..', ...)` — Playwright 1.62 verweigert das Verlassen des Pro-Test-Ordners). Ich hatte das selbst gefunden und lokal (Commit 7d490f82) auf `testInfo.project.outputDir` umgestellt; der Task-6b-Agent fand denselben Fehler unabhaengig und reparierte ihn anders. Der Orchestrator hat seine eigene, gemeinsame Fassung (`6b778d6b`) auf den Feature-Branch gelegt und mich angewiesen, beim Merge `--theirs` zu nehmen. Nach dem Merge einmal neu gegen die gemeinsame Fassung getestet (grün) und einen kleinen Folgefehler in MEINER Spec beheben muessen (`navigate(-1)` reisst die Playwright-Ausfuehrungsumgebung mitten in der Polling-Abfrage weg — jetzt abgefangen, siehe Commit fbf49d4d). Datei gehoert jetzt dem Orchestrator, nicht mehr diesem Task.
- **`onLockFreigeben`-Ablauf end-to-end im echten Browser nicht erreichbar**: laut Restplan verdrahtet die Seite (`DocumentEditorPage.tsx`) `readOnly`/`onLockFreigeben` erst in Abschnitt 7a (bewusst sequenziell: "erst der Editor, dann die Seite"). Solange die Seite den neuen Prop nicht uebergibt, faellt mein Editor beim Schliessen auf sein bisheriges Verhalten zurueck (`onClose()`), und `TabSchliessenHinweis` kann ueber die echte Route `/dokument-editor` noch nicht ausgeloest werden. Die eigene Playwright-Spec deckt deshalb nur ab, was heute tatsaechlich reproduzierbar ist (kein doppelter Heartbeat mehr, realer Klick-Ablauf ueber den alten Schliess-Weg, Fehlerfall). Den neuen, Prop-gesteuerten Ablauf (save → onLockFreigeben → window.close → Hinweis) deckt `index.test.tsx` auf Komponentenebene mit echten Kind-Komponenten ab (kein Mock von `Modals.tsx`/`TabSchliessenHinweis`). Eine echte Browser-Probe dafuer sollte Task 7a (oder ein Nachtrag danach) ergaenzen, sobald die Seite den Prop wirklich uebergibt.
- **`onClose`-Fallback-Entscheidung**: `tabSchliessen()` ruft `onClose()` NUR auf, wenn `onLockFreigeben` fehlt (dann 1:1 altes Verhalten, keine Wartezeit, kein Hinweis). Ist `onLockFreigeben` gesetzt, wird `onClose()` in diesem Pfad gar nicht mehr aufgerufen — der Editor uebernimmt dann selbst per `window.close()`/`TabSchliessenHinweis` die volle Verantwortung fuers Schliessen. Das ist meine eigene Interpretation von "prüf, wie sich beides sauber verbindet" aus dem Auftrag; Alternative waere gewesen, `onClose()` zusaetzlich als Fallback nach einem gescheiterten `window.close()` aufzurufen, wurde aber verworfen, weil das bei einer Seite mit `navigate(-1)`-Logik den `TabSchliessenHinweis` sinnlos machen wuerde (die Seite navigiert dann ohnehin weg, bevor der Hinweis etwas nuetzt).
- **`onLockFreigeben`-Fehlerfall (Netzwerkfehler bei der Freigabe selbst)**: nicht explizit als roter Test in der Spec verlangt, aber nach der Toast-Pflicht ergaenzt — schlaegt die Freigabe fehl, bricht `tabSchliessen()` ab (kein `window.close()`-Versuch, kein Hinweis), zeigt `toast.error('Sperre konnte nicht freigegeben werden. Bitte Seite neu laden.')` und laesst den Editor offen. Begruendung: ein Tab, der schliesst, obwohl die Freigabe nicht bestaetigt ist, wuerde den Datensatz fuer Kollegen weiter gesperrt lassen — genau der Fehler, den dieses Vorhaben beheben soll.
- **`handleSave`s 409-Zweig verliert den Namen des aktuellen Halters** (`holderDisplayName`), weil das nur noch ueber ein separates Acquire zu ermitteln war (das jetzt entfernte `tryAcquireLock`). Die Toast-Meldung nutzt jetzt den Klartext-Body der PUT-Antwort ("Dokument wird gerade von einem anderen Benutzer bearbeitet.") ohne Namen. Fachlich vertretbar (der Editor haelt ohnehin kein Lock mehr, ein Zusatz-Request nur fuer den Namen waere wieder die alte Doppel-Logik), aber eine Verhaltensaenderung gegenueber heute — hier vermerkt statt still uebernommen.
- **`handwerkerprogramm-design`-Skill ueber das Skill-Tool nicht aufrufbar**: `Skill({skill: "handwerkerprogramm-design"})` lieferte "Unknown skill" (die Skill-Registrierung fuer dieses Worktree/Session scheint diesen Skill trotz vorhandener Dateien unter `.claude/skills/handwerkerprogramm-design/` nicht zu listen). Stattdessen `SKILL.md` und `README.md` direkt per Read-Tool gelesen und die Design-Regeln (rose-600 Primaerfarbe, Lucide-Icons, kein Emoji, rounded-lg, PageHeader-Muster etc.) daraus angewendet. Zusaetzlich: der PreToolUse-Hook `check-doc-read.ps1`/`mark-design-skill-used.ps1` ist in `settings.local.json` auf einen absoluten Pfad `c:\dev\ERP-System-fuer-Handwerksbetriebe\...` verdrahtet, der auf dieser Maschine nicht existiert (das Repo liegt unter `C:\Users\MarvinKuhn\dev\...`) — der Guard hat dementsprechend nie gegriffen, blockierte aber auch keinen mein Edits.
- **`DocumentEditorHeader.tsx`s X-Button hat kein `aria-label`/keinen sichtbaren Text** (nur ein nacktes `X`-Icon) — ein Verstoss gegen die FRONTEND_UI.md-Pflicht "Icon-only-Buttons immer mit aria-label". Nicht repariert, weil die Datei nicht in meiner Datei-Liste steht; als Bedenken vermerkt statt still mit angefasst. Betrifft auch meine Tests: dort ueber `container.querySelector('button')` (erster Button im Baum) statt einer Rolle angesprochen.
- **Playwright-Port**: `E2E_PORT=5174` war beim ersten Versuch von einem fremden, bereits laufenden Dev-Server belegt (Titel "Mehrteillage · Sichtprüfung", offensichtlich ein anderes Projekt/Prozess auf dieser Maschine) — `reuseExistingServer` von Playwright haette dessen Inhalt getestet statt meiner Aenderung. Nicht angetastet/beendet (koennte einem anderen Prozess/Agenten gehoeren), stattdessen Port 5179 verwendet (frei, verifiziert vor der Nutzung).

## Abschnitt 6 — Design-Review (Design-Reviewer)

**Ampel: 🔴** — ein blockierender Befund (Frage 6, auf 14 Zoll gemessen). Alles andere ist 🟡.

Worktree `wt/review-design`, Stand `ff863270`. `E2E_PORT=5190 npm run test:e2e`:
**82 Tests, alle grün**, beide Größen `pc-14zoll` (1440×900) und `pc-monitor` (1920×1080).
Kein Test aus `website-*.spec.ts` war rot, kein Nachfahren nötig. Für beide geänderten
Abläufe (6a X-Button, 6b Lieferant-Modal) gibt es eine Spec — kein 🔴 wegen fehlender Spec.

### Angeschaute Screenshots

Aus `react-pc-frontend/test-results/design/` (alle 12, jeweils `--pc-14zoll` und `--pc-monitor`):

1. `dokument-editor-vor-schliessen--pc-14zoll` / `--pc-monitor`
2. `dokument-editor-ungespeichert-warnung--pc-14zoll` / `--pc-monitor`
3. `lieferant-modal-bearbeiten--pc-14zoll` / `--pc-monitor`
4. `lieferant-modal-gesperrt--pc-14zoll` / `--pc-monitor`
5. `lieferant-modal-fremdes-lock--pc-14zoll` / `--pc-monitor`
6. `lieferant-modal-fehler--pc-14zoll` / `--pc-monitor`

Dazu 10 eigene Zusatz-Aufnahmen für Zustände, die keine Spec abbildet (Wegwerf-Spec,
nach dem Lauf gelöscht, Worktree ist sauber):
`settled-bearbeiten`, `settled-gesperrt`, `fehler-toast`, `leiste-countdown`,
`leiste-verbindung-weg` — je `--pc-14zoll` und `--pc-monitor`.

### Die sechs Fragen je Zustand

**`dokument-editor-vor-schliessen` (14 Zoll + Monitor)**
1. *Farben?* Ja. Kopfzeile weiß, Inhalt slate-50, genau eine rosa Primäraktion (`PDF`), alles
   andere slate-Ghost. Der Rechnung-Chip unten links ist die einzige weitere rosa Fläche und
   klar als Status lesbar.
2. *Design-System?* Ja. Nur rose/slate, Lucide durchgehend, kein Emoji, Systemschrift,
   `rounded-lg`, `shadow-sm`, kein handgemaltes SVG.
3. *Look-and-Feel?* Ruhig und ausgerichtet. Auf 1920 wirkt die leere Blockfläche etwas groß,
   das ist aber der leere Beispiel-Datensatz, nicht das Layout.
4. *UX?* Der leere Zustand erklärt sich in ganzen Sätzen („Nutzen Sie die Buttons oben …"),
   Handwerker-Sprache. Gut.
5. *Auffindbar?* Ja, der X-Knopf sitzt oben links, ohne Scrollen.
6. *Überschneidung?* Kein waagerechtes Scrollen, keine Überlappung.
   **Aber:** Beide Aufnahmen erwischen die Vorschau-Spalte mitten in ihrer 500-ms-Aufblendanimation
   (`LivePreviewPanel`, `width 0% → 45%`, `minWidth 340`). Auf 14 Zoll ist sie im Bild nur ~88 px
   breit und wirkt abgeschnitten; das ist ein Aufnahme-Artefakt, kein Layoutfehler — im
   `…-ungespeichert-warnung`-Bild derselben Spec steht sie ausgelaufen bei 45 % (645 px bzw. 860 px).
   → 🟡 (Screenshot vor der Aufnahme ausklingen lassen).

**`dokument-editor-ungespeichert-warnung` (14 Zoll + Monitor)**
1. *Farben?* Ja, und sauber gestuft: `Abbrechen` slate-Outline, `Nicht speichern` rose-Outline,
   `Speichern & Schließen` rose-600 gefüllt. Genau eine rosa Primäraktion.
2. *Design-System?* Ja. `rounded-2xl`, `shadow-2xl`, `bg-black/40 backdrop-blur-sm`,
   `AlertTriangle` in amber-50/amber-500 als Warn-Semantik. Passt.
3. *Look-and-Feel?* Ja, mittig, ruhig, in beiden Größen gleich gut.
4. *UX?* Titel + Erklärzeile + Frage, drei klar unterschiedliche Ausgänge. Vorbildlich.
5. *Auffindbar?* Ja, mittig im Bild, ohne Scrollen.
6. *Überschneidung?* Nein.
   🟡: `Nicht speichern` bricht in beiden Größen auf zwei Zeilen um (`flex-1` teilt gleich breit).
   Kosmetik.

**`lieferant-modal-bearbeiten` (14 Zoll + Monitor)**
1. *Farben?* Im ausgelaufenen Zustand ja: `Fertig` ist weiß mit rose-300-Rand/rose-700-Text,
   `Speichern` unten ist die einzige gefüllte rose-Fläche.
   **Achtung, beide Screenshots zeigen den Zustand nicht ausgelaufen:** sie fallen in die
   150-ms-`transition-colors` beim Wechsel `Bearbeiten` (default, rose-600 gefüllt) →
   `Fertig` (outline). Gemessen: auf `pc-monitor` ist die Knopffläche zum Aufnahmezeitpunkt
   noch #E11D48 (rose-600, weiße Schrift), auf `pc-14zoll` schon #FADAE1
   (rose-600 bei ~16 % über Weiß). Nachgestellt mit 1,2 s Wartezeit
   (`settled-bearbeiten--*`) steht der Knopf in beiden Größen korrekt als weißer
   Outline-Knopf. → 🟡, gleiche Ursache wie oben.
2. *Design-System?* Ja. rose/slate, Lucide (`Check`, `Pencil`, `Eye`, `CheckCircle`),
   kein Emoji, Systemschrift, Karten `rounded-lg`/`shadow-sm`, Dialog `rounded-2xl`.
3. *Look-and-Feel?* Die Sperr-Leiste ist im freien Zustand links komplett leer — auf 1920 ein
   ~1300 px breites graues Band mit einem Knopf ganz rechts. Wirkt hohl. → 🟡
4. *UX?* Genau eine Primäraktion (`Speichern`), `Fertig` klar als Sekundäraktion.
   Formularfelder aktiv und sichtbar bedienbar.
5. *Auffindbar?* Ja, `Fertig` sitzt am Kopf des Datensatzes, auf 14 Zoll ohne Scrollen.
6. *Überschneidung?* Kein waagerechtes Scrollen, keine überlappenden Elemente.
   🟡: Auf 14 Zoll frisst die PDF-Spalte zwei Drittel der Modalbreite; das Formular endet
   mitten im Feld „Zahlungsziel" an der Fußleiste, ohne sichtbaren Scrollbalken oder
   Abschluss-Verlauf. Es ist ein Scroll-Container, also kein Fehler — aber man sieht nicht,
   dass unten noch etwas kommt.

**`lieferant-modal-gesperrt` (14 Zoll + Monitor)** — eigenes Lock freigegeben, Nur-Lesen
1. *Farben?* Grenzwertig. `Bearbeiten` ist rose-600 gefüllt, `Speichern` ist rose-600 gefüllt
   mit 50 % Deckkraft — auf einen Blick zwei rosa Knöpfe. Die Unterscheidung
   lesen/bearbeiten läuft ansonsten nur über die Textfarbe der Felder
   (slate-900 → slate-400); Rahmen und Flächen bleiben identisch. → 🟡
2. *Design-System?* Ja.
3. *Look-and-Feel?* Ruhig, in beiden Größen gleich.
4. *UX?* 🟡: Wer eben selbst auf `Fertig` geklickt hat, sieht das Formular ausgrauen — die
   Leiste sagt dazu nichts (linke Hälfte leer). Ein Satz wie „Sie lesen nur mit." würde die
   Lücke schließen. `Speichern` erklärt seine Deaktivierung immerhin per `title`-Tooltip.
5. *Auffindbar?* Ja, `Bearbeiten` sitzt am Kopf, ohne Scrollen, in beiden Größen.
6. *Überschneidung?* Nein.

**`lieferant-modal-fremdes-lock` (14 Zoll + Monitor)** — der beste Zustand des Abschnitts
1. *Farben?* Ja. `GesperrtHinweis` als volles rose-50-Band mit rose-100-Rand und rose-600-`Lock`,
   Name fett in slate-900, Rest slate-700. Hebt sich klar von weißem Formular und slate-50-Band ab.
2. *Design-System?* Ja.
3. *Look-and-Feel?* Sehr ruhig, das Band nimmt die volle freie Breite und füllt die Leiste
   auch auf 1920 sinnvoll — genau das, was dem freien Zustand fehlt.
4. *UX?* „Thomas Beispiel bearbeitet das gerade — Sie sehen den aktuellen Stand. Seit 5 Min."
   Klartext, Handwerker-Sprache, kein Buchhalter-Deutsch. `Bearbeiten` bleibt sichtbar und aktiv
   (Übernahmeversuch statt Sackgasse). Stark.
5. *Auffindbar?* Ja — Hinweis und Umschalter stehen direkt über dem Datensatz, auf 14 Zoll
   ohne Scrollen sichtbar.
6. *Überschneidung?* Nein, kein waagerechtes Scrollen, nichts abgeschnitten.

**`lieferant-modal-fehler` (14 Zoll + Monitor)** — Acquire liefert 500
1. *Farben?* 🟡: Der Fehler ist leiser gestaltet als der harmlose Nur-Lesen-Hinweis — nur
   rose-700-Text mit offenem Schloss auf dem grauen Band, ohne Fläche und ohne Rand, während
   `GesperrtHinweis` ein volles rose-50-Band bekommt. Dringlichkeit ist verkehrt herum verteilt.
2. *Design-System?* Ja, rose/slate, Lucide.
3. *Look-and-Feel?* Unauffällig, fast zu unauffällig.
4. *UX?* 🟡: `Bearbeiten` ist deaktiviert ohne Tooltip, der das Warum erklärt —
   `BearbeitenLeiste` gibt keinen `title` durch, während `Speichern` im selben Modal einen hat.
   Der Grund steht zwar daneben im Band, die Vorgabe aus FRONTEND_UI.md verlangt aber den Tooltip.
   Der Fehler-Toast erscheint (Toast-Pflicht erfüllt) — im Spec-Screenshot ist er noch nicht
   sichtbar, weil er ~600–800 ms zum Einblenden braucht und die Aufnahme früher fällt.
5. *Auffindbar?* Ja.
6. *Überschneidung?* Nein — siehe aber den blockierenden Befund unten, der genau hier
   auftritt, sobald der Toast eingeblendet ist.

**Zusatz `leiste-countdown` (14 Zoll + Monitor)** — mit `page.clock` auf 4:05 vorgespult
1. *Farben?* 🟡: „Wird in 55 Sekunden freigegeben — bewegen Sie die Maus, um weiterzuarbeiten."
   steht in rose-50/rose-100 — also in genau derselben Optik wie der neutrale
   `GesperrtHinweis`. Eine Vorwarnung („dir läuft gleich die Sperre weg") sieht damit aus wie
   eine Info. Das Design-System hält `amber-500` für Warnungen bereit.
   Außerdem als einziges der drei Bänder ohne Icon.
2. *Design-System?* Ja (rose/slate, kein Fremdton).
3. *Look-and-Feel?* Passt, füllt die Leiste.
4. *UX?* Text sagt klar, was passiert und was man dagegen tun kann. Gut.
   🟡: Sobald das Band erscheint, rutscht `Fertig` um ~540 px nach links (`justify-between`),
   also potenziell unter dem Mauszeiger weg.
5. *Auffindbar?* Ja, auf 14 Zoll vollständig sichtbar (x 848–1380), ohne Scrollen.
6. *Überschneidung?* Nein.

**Zusatz `leiste-verbindung-weg` (14 Zoll + Monitor)** — Heartbeat auf 500, ~3 Intervalle vorgespult
1. *Farben?* rose-50 mit rose-200-Rand und rose-700-Text, `WifiOff`. Wirkt durch den dunkleren
   Text dringlicher als der Countdown — die Reihenfolge stimmt, die Farbfamilie ist aber
   dieselbe wie bei Hinweis und Countdown. 🟡 wie oben.
2. *Design-System?* Ja.
3./4. Ruhig, Text konkret („Ihre Änderungen sind noch nicht gespeichert."). Gut.
5. *Auffindbar?* Ja, beide Größen ohne Scrollen.
6. *Überschneidung?* Nein.

### 🛑 Blockierend

**Der Fehler-Toast verdeckt auf 14 Zoll die Fußleiste des Lieferant-Dokument-Modals und
schluckt deren Klicks.**

Der neue Effekt in `src/components/LieferantDokumentModal.tsx:83` feuert `toast.error(...)`
automatisch, während das Modal offen ist. Der Toast-Container steht global auf
`fixed bottom-6 right-6` (`src/components/ui/toast.tsx:124`) und landet damit genau auf der
Modal-Fußleiste. Gemessen im Browser (Zustand: Acquire 500, 1,2 s nach dem Öffnen):

| Größe | Toast | `Abbrechen` | `Speichern` | `elementFromPoint` in der Knopfmitte |
| --- | --- | --- | --- | --- |
| pc-14zoll | y 830–876 | y 812–850 | y 812–850 | **Toast-DIV** (beide Knöpfe) |
| pc-monitor | y 1010–1056 | y 983–1021 | y 983–1021 | Button (nur untere Kante verdeckt) |

Auf 14 Zoll sind 20 von 38 px Knopfhöhe (53 %) überdeckt, und ein Klick in die Mitte von
`Abbrechen` oder `Speichern` trifft für die 5 s Toast-Standzeit den Toast, nicht den Knopf.
Damit ist Frage 6 verletzt („Sticky-Leisten und Modale verdecken nichts, was man gerade
braucht") — und zwar nicht nur optisch.

Warum die vorhandenen Prüfungen das nicht gesehen haben:
`keineUeberschneidungen` in `e2e/hilfen/design.ts` überspringt bei offenem Dialog alles
außerhalb des Dialogs (`if (dialog && !dialog.contains(el)) continue;`) — der Toast liegt
außerhalb. Und `designPruefung` fotografiert, bevor der Toast eingeblendet ist.

**Nachweisbar sein muss:** bei offenem Modal und stehendem Toast trifft ein
`document.elementFromPoint` in der Mitte von `Abbrechen` und `Speichern` auf beiden Größen den
jeweiligen Button. Naheliegende Wege: Toast-Container bei offenem Dialog höher setzen,
in diesem Zustand auf den Toast verzichten (die Meldung steht ohnehin im Modal),
oder den Toast oben rechts ausgeben. Die Prüfung gehört in
`e2e/lieferant-dokument-modal.spec.ts` (Fehlerfall) plus eine Erweiterung von
`keineUeberschneidungen` um Overlays außerhalb des Dialogs.

### 💡 Hinweise (blockieren nicht)

- `dokument-editor-vor-schliessen` und `lieferant-modal-bearbeiten` fotografieren laufende
  Übergänge (500-ms-Breitenanimation der Vorschau bzw. 150-ms-Farbübergang von `Bearbeiten`
  nach `Fertig`). Vor `designPruefung` kurz ausklingen lassen, sonst belegen die Bilder nicht
  den Zustand, den sie belegen sollen.
- Alle drei Lock-Bänder (Hinweis, Countdown, Verbindung weg) sind rose-50-Varianten und
  unterscheiden sich fast nur im Text. Countdown = Warnung, Verbindung weg = Störung —
  `amber` bzw. ein kräftigeres Rot wären systemkonform und trennten die Zustände auf einen Blick.
  Dem Countdown fehlt außerdem als einzigem ein Icon.
- Der Fehlerzustand ist optisch schwächer als der harmlose Nur-Lesen-Hinweis. Ein Band mit
  Fläche und Rand wie bei `GesperrtHinweis` wäre stimmiger.
- Der deaktivierte `Bearbeiten`-Knopf erklärt sich nicht per Tooltip (`BearbeitenLeiste` reicht
  keinen `title` durch), während `Speichern` im selben Modal einen hat.
- Im Nur-Lesen-Zustand nach eigenem `Fertig` bleibt die linke Hälfte der Leiste leer. Ein Satz
  („Sie lesen nur mit.") würde den Zustand benennen und das Band auf 1920 füllen.
- Wenn der Countdown erscheint, springt `Fertig` um ~540 px nach links.
- `Nicht speichern` im Warn-Dialog bricht auf zwei Zeilen um.
- Auf 14 Zoll nimmt die PDF-Spalte zwei Drittel des Modals ein; das Formular endet ohne
  sichtbaren Hinweis, dass darunter noch Felder liegen.
- Du/Sie: `TabSchliessenHinweis` duzt, `BearbeitenLeiste` siezt. Bekannt, liegt beim Nutzer.
- `TabSchliessenHinweis` war wie geplant über die Route nicht erreichbar und ist deshalb nur
  im Code geprüft (rose-100-Kreis, `CheckCircle2` rose-600, slate-50-Fläche, `role="status"` —
  systemkonform). Der Browser-Beleg kommt mit 7a.

## Abschnitt 6 — Code-Review (Code-Reviewer)

**Ampel: 🔴** — ein blockierender Befund. Alles andere (Tests, Build, Lint, Mutationsproben,
Datenschutz, Diff-Hygiene) ist sauber.

### Selbst gemessene Zahlen

| | Baseline (nach Abschnitt 5) | jetzt | Delta |
|---|---|---|---|
| Backend `./mvnw -B test` | 2454 Tests, 0 F, 4 E | **2462 Tests, 0 Failures, 4 Errors** | +8 |
| Frontend Testdateien | 85 | **87** | +2 |
| Frontend Tests | 1003 | **1027** | +24 |
| Lint | 0 Fehler, 1 Warnung | **0 Fehler, 1 Warnung** | ±0 |

- Die 4 Backend-Errors sind namentlich die bekannten umgebungsbedingten
  (`AuditChainRepairIntegrationTest` 2x, `AuditHashRoundtripDiagnoseTest` 2x,
  jeweils `CannotCreateTransaction`) — kein Befund.
- Lint-Warnung ist die vorbestehende in `BelegeKasseEditor.tsx:1204` — kein Befund.
- `./mvnw -B clean package -DskipTests` grün. `npm run build` grün;
  `src/main/resources/static/` danach auf HEAD zurückgesetzt, Arbeitsbaum sauber.
- Neue Testdateien wie erwartet: `lock/TabSchliessenHinweis.test.tsx` (6a),
  `LieferantDokumentModal.test.tsx` (6b).

**Flakes unter Parallellast** (Design-Reviewer fuhr gleichzeitig Dev-Server + Browser):
erster Volllauf 7 Zeitschranken-Fehlschläge, zweiter Volllauf 1. Kein Fehlschlag war
zweimal derselbe. Alle betroffenen Dateien einzeln nachgefahren — `LieferantDokumentModal.test.tsx`
3x isoliert 13/13 grün, `document-editor/index.test.tsx` im zweiten Volllauf 15/15 grün.
Also Lastartefakt, kein Codefehler.

### Verbraucher-Grep auf die alten Lock-Klassen

`grep -rn "DokumentLockService\|useDocumentLock\|DocumentLockedModal\|dokument-locks" src/main react-pc-frontend/src`

Sauber. Es zeigt nur noch auf die alten Klassen: `pages/DocumentEditorPage.tsx` (erwartet, 7a),
die alten Klassen selbst (`DokumentLockController`, `DokumentLockService`, `useDocumentLock`,
`DocumentLockedModal`) und reine Erwähnungen in Kommentaren/Tests. Kein anderer Produktivcode
mehr. `document-editor/index.tsx` enthält nur noch den erklärenden Kommentar, keinen Request.

### Mutationsproben (Quellstand danach byte-identisch, `git diff f8b41d2e` leer)

1. **6a Reihenfolge** — `window.close()` in `tabSchliessen()` vor den `await onLockFreigeben()`-Block gezogen:
   `AssertionError: expected [ 'speichern', 'window.close', …(1) ] to deeply equal [ Array(3) ]`
2. **6a Speichern scheitert** — `void tabSchliessen();` vor die `if (!gespeichert) return;`-Schranke gesetzt:
   `AssertionError: expected "vi.fn()" to not be called at all, but actually been called 1 times`
3. **6c Durchgriff** — den `throw e;`-Zweig in `stornieren` entfernt: genau **1 von 45** Tests rot,
   `AusgangsGeschaeftsDokumentControllerTest$Stornieren.versionskonfliktBeimStornierenGibt409:697
   Status expected:<409> but was:<400>` — die anderen vier Zweige blieben grün. Saubere Isolation.

### 🛑 Blockierend

**Neu angelegte Dokumente verlieren nach 90 s ihre Sperre und lassen sich nicht mehr speichern.**
`react-pc-frontend/src/components/document-editor/index.tsx:1146-1155` (entfernter Heartbeat)

Kette: `create` im Backend holt die Sperre korrekt (`SperrbarerTyp.AUSGANG`, Test vorhanden und
grün). Danach schreibt der Editor die neue Id nur per `window.history.replaceState` in die URL
(`syncDocumentIdInUrl`, Zeile 1130) — react-router bekommt das nicht mit, `useSearchParams` liefert
weiter kein `dokumentId`, also läuft `useDocumentLock('AUSGANG', undefined)` in `DocumentEditorPage.tsx:26`
im Zustand `idle` und pingt nie. `update()` frischt den Heartbeat nicht auf (nur `isHeldBy`-Prüfung).
Nach `STALE_AFTER` = 90 s ist die Sperre verwaist, jeder weitere PUT (auch der 10-Sekunden-Autosave)
bekommt 409 — und der Wiederherstellungsweg ist mit 6a weggefallen (`tryAcquireLock`-Retry raus).
Der Nutzer sieht nur einen Toast; alles ab der 90-Sekunden-Marke Getippte ist bis zum Neuladen
nicht speicherbar.

Genau davor schützte der entfernte Editor-Heartbeat — sein eigener Kommentar beschrieb diesen
Fall wörtlich. 6a hat ihn planmäßig entfernt, aber 7a verdrahtet die Seite erst später; in der
Liste der bewusst akzeptierten Zwischenstände steht nur der unerreichbare `TabSchliessenHinweis`,
nicht dieser Fall.

Nachweisbar sein muss: ein neu im Editor angelegtes Dokument lässt sich auch nach mehr als 90 s noch
speichern — entweder weil die Seite die per `replaceState` gesetzte Id mitbekommt und ab da
heartbeatet, oder weil der Editor die Id an die Seite meldet. Dazu ein Test, der die Uhr über
90 s vorstellt und danach einen erfolgreichen PUT zeigt.

### 💡 Hinweise (blockieren nicht)

- `document-editor/index.tsx:1229` — bei 409 landet `await res.text()` ungefiltert im Toast.
  Der Sperrkonflikt liefert Klartext, ein Versionskonflikt (`RestExceptionHandler`) aber JSON;
  dann liest der Handwerker `{"status":409,"message":"…"}`. 6b macht es an dieser Stelle mit
  `useKonfliktMeldung` richtig — der Editor könnte denselben Baustein nutzen.
- `document-editor/index.tsx:1381` — der 150-ms-`setTimeout` wird beim Unmount nicht abgeräumt.
  Folgenlos, weil `mountedRef` den `setState` abfängt, aber die Id zu merken und im Cleanup zu
  clearen wäre sauberer.
- `LieferantDokumentModal.test.tsx:156` — `findByRole` mit der Standard-Wartezeit von 1 s; genau
  dieser Test kippte unter Parallellast. Eine großzügigere Zeitschranke macht ihn robust.
- `LieferantDokumentModal.tsx` (`handleClose`) — Freigabe bewusst fire-and-forget, im Gegensatz
  zum awaiteten Weg in 6a. Hier richtig (nichts hängt am Ergebnis), aber ein Halbsatz im
  Kommentar, warum es hier anders ist als im Editor, würde die nächste Lesart sparen.

### Sonst geprüft, ohne Befund

- **6c Teil 1** ist wirklich nur ein Tausch: 11 Zeilen im Controller, ausschließlich Import,
  Feldtyp, Enum statt String-Konstante, DTO-Klasse. Kein Verhaltensunterschied.
- Kein Klassenname im 409-Body — die fünf neuen Tests prüfen das ausdrücklich mit
  `not(containsString("ObjectOptimisticLockingFailureException"))`. Der Handler loggt die Ursache
  weiter (`LOG.debug` mit Entitätstyp und Id), `pdfSpeichern`/`stornieren`/`update` loggen ihre
  allgemeinen Fehler unverändert per `log.error(..., e)`.
- **6b:** Idle-Timer nur bei `modus === 'bearbeiten'` scharf; kein zweiter Timer neben
  `useIdleTimer`; `formGesperrt = modus !== 'bearbeiten'` deckt gesperrt/loading/error in einem
  ab; Fehler-Toast in Handwerker-Klartext, wortgleich mit dem Inline-Hinweis. Der Auto-`onBearbeiten`-
  Effekt kann nicht gegen "Fertig" oder die Idle-Freigabe ankämpfen: `aktivFreigeben` setzt den
  Status auf `idle`, nicht auf `acquired`, die Bedingung greift also nicht erneut.
- **6a:** Editor schickt nach Mount und Unmount keinen einzigen Lock-Request mehr (eigener Test);
  `onClose` wird nur noch genommen, wenn `onLockFreigeben` fehlt — kein doppeltes Freigeben.
- **Datenschutz:** nur Dummy-Namen (Max Mustermann, Anna Büro, Thomas Beispiel,
  Musterbedarf Baustoffe GmbH), keine echten E-Mail-Adressen, keine Secrets in neuen Dateien.
- **Diff-Hygiene:** der Abschnitt fasst genau 12 Dateien an. Kein `test-results/`, kein
  `resources/static/`, kein `playwright-report/`, kein `node_modules/`.
- **Performance:** keine neuen Fetch-Wasserfälle, kein Polling. Der Editor sendet nach 6a sogar
  weniger (Heartbeat weg). Der Idle-Countdown rendert nur in der letzten Minute im Sekundentakt,
  Aktivitäts-Resets sind auf 1/s gedrosselt — kein Re-Render-Sturm.

## Abschnitt 6 — Task 6b Nachbesserung 1 (Coding-Agent)

Zeit: 2026-09-04T21:10:00Z
Branch: lock/task-6b-lieferant-modal
Commit(s): 29ac0436 (Fix + Tests), zuvor 6970ea33 / 75ff4b9a (Merge des Feature-Branches, konfliktfrei, keine eigenen Aenderungen darin)
Status: fertig, alle Gates gruen, Befund belegt behoben

Was gemacht wurde:
- Feature-Branch zweimal nachgezogen wie angewiesen (git merge --no-edit claude/eloquent-ramanujan-gz0w2t), beide Male sauber (erstes Mal Fast-Forward, keine eigenen Dateien betroffen).
- Ursache bestaetigt: der globale Toast-Container in ui/toast.tsx lag fest unten rechts (bottom-6 right-6, z-[9999]), die Modal-Fussleiste liegt bei praktisch jedem Modal im Projekt ebenfalls unten. Fix bewusst im Toast selbst (nicht in LieferantDokumentModal), wie vom Auftrag vorgeschlagen und vom Reviewer freigegeben: neuer interner Hook useIrgendeinDialogOffen() in ui/toast.tsx prueft per document.querySelector('[role="dialog"]'), ob irgendwo ein offener Dialog im DOM steht (Erstcheck synchron beim Mount-Effekt, danach ein MutationObserver auf document.body mit attributeFilter ['role'], damit spaeter geoeffnete/geschlossene Dialoge live nachgezogen werden). Der Container bekommt darueber die Klasse top-6 statt bottom-6, solange ein Dialog offen ist -- rechts bleibt immer right-6, weil dort noch nie ein Modal Aktionen platziert.
- data-testid="toast-container" am Container ergaenzt (fuer stabile Tests, keine Verhaltensaenderung).
- LieferantDokumentModal.tsx: Lock-Fehler-Hinweis von reinem farbigem Text (text-rose-700 auf dem umgebenden bg-slate-50, kein eigener Rahmen) auf ein eigenes Band umgestellt: rounded-lg border border-red-300 bg-red-50 text-red-700 font-semibold, AlertTriangle-Icon (lucide) statt des bisherigen Lock-Icons -- deutlich kraeftiger als das ruhige rose-50-Band von GesperrtHinweis, wie vom 🟡 des Reviewers verlangt. Dafuer den bisherigen Lock-Import entfernt (nirgends sonst genutzt) und durch AlertTriangle ersetzt.
- ui/toast.test.tsx (bestehende Datei erweitert, nicht neu angelegt -- sie existierte schon): zwei neue Tests in einer eigenen describe-Gruppe. Roter Test zuerst gegen die Mutationsprobe verifiziert (className fest auf 'bottom-6 right-6' zurueckgedreht): "wandert nach oben rechts, solange ein Dialog offen ist" wurde rot mit "expected ... to contain 'top-6'", der Gegentest ("liegt unten rechts, wenn kein Dialog offen ist") blieb dabei korrekt gruen. Danach zurueckgesetzt, wieder 8/8 gruen.
- e2e/lieferant-dokument-modal.spec.ts: neue Hilfsfunktion erwarteTreffer(page, knopf, beschriftung), die per document.elementFromPoint (page.evaluate) genau das nachstellt, was der Reviewer manuell gemessen hat -- Mitte der Bounding-Box des Knopfes anfahren und pruefen, dass elementFromPoint(...).closest('button') den GESUCHTEN Knopf liefert (nicht irgendeinen). Eingebaut in zwei Stellen:
  1. Bestehender Fehlerfall-Test (Lock-Acquire liefert 500 beim Oeffnen) um die Treffer-Pruefung fuer "Abbrechen" und "Speichern" ergaenzt, direkt nachdem der Toast sichtbar ist (ueber den neuen data-testid="toast-container" abgewartet).
  2. Neuer Test "Speicherfehler (500): Toast verdeckt die Fussleiste ebenfalls nicht" -- freies Lock, PUT auf /api/lieferant-dokumente/{id} liefert 500, Klick auf "Speichern" loest den Toast "Speichern fehlgeschlagen" aus, dieselbe Treffer-Pruefung.
  Beide Faelle zusaetzlich mit designPruefung() abgesichert (automatischer Ueberschneidungs-Check zaehlt seit dem Fix des Orchestrators in design.ts jetzt auch fest positionierte Elemente mit).
- Mutationsprobe fuer den Gesamt-Fix in der echten Spec (nicht nur im Unit-Test) durchgefuehrt: dialogOffen-Logik in toast.tsx voruebergehend auf konstant 'bottom-6 right-6' zurueckgedreht, beide betroffenen Tests auf beiden Groessen gefahren (-g "Fehlerfall|Speicherfehler"):
  - pc-14zoll: BEIDE Tests rot, exakt mit der erwarteten Meldung: "elementFromPoint in der Mitte von 'Abbrechen' (1189.4453125, 831) trifft keinen Knopf -- vermutlich verdeckt ein fest positioniertes Element (Toast?) die Fussleiste".
  - pc-monitor: BEIDE Tests bleiben gruen, obwohl der Fix zurueckgedreht war -- deckt sich exakt mit dem Befund des Reviewers ("Auf 1920 geht der Klick durch").
  Fix zurueckgesetzt (git diff danach nur noch die beabsichtigte Aenderung), alle 8 Tests wieder gruen.
- Gates (nur eigene Dateien/Spec): npx vitest run src/components/LieferantDokumentModal.test.tsx src/components/ui/toast.test.tsx -> 21/21 gruen (13 + 8). npm run lint -> 0 Fehler, genau die 1 vorbestehende Warnung (BelegeKasseEditor.tsx:1204). npm run build -> gruen, Build-Output zurueckgesetzt. E2E_PORT=5175 npx playwright test e2e/lieferant-dokument-modal.spec.ts -> 8/8 gruen (4 Faelle x 2 Groessen), keine manuelle Wartezeit mehr noetig (uebergaengeAusklingenLassen() laeuft jetzt automatisch in designPruefung()).
- Screenshots angeschaut (nicht nur erzeugt), unter react-pc-frontend/test-results/design/: lieferant-modal-fehler--{pc-14zoll,pc-monitor}.png (Toast jetzt oben rechts, Fussleiste frei, rotes Band mit AlertTriangle klar von GesperrtHinweis abgesetzt) und neu lieferant-modal-speicherfehler-toast--{pc-14zoll,pc-monitor}.png (derselbe Effekt beim gewoehnlichen Speicherfehler).

Bedenken / Abweichungen vom Plan:
- src/components/ui/toast.tsx angefasst -- eine geteilte Datei ausserhalb der urspruenglichen Datei-Liste. Ausdruecklich vom Auftrag freigegeben ("Du darfst dafuer src/components/ui/toast.tsx anfassen"), minimal gehalten (nur der neue Hook + die bedingte Klasse + ein data-testid, kein sonstiges Verhalten geaendert). Kein anderer laufender Task fasst diese Datei an (laut Auftrag geprueft).
- toast.test.tsx existierte bereits (nicht wie im Auftrag "eigener Unit-Test in einer toast.test.tsx" impliziert eine neue Datei) -- bestehende Datei um eine eigene describe-Gruppe erweitert statt eine zweite Testdatei fuer denselben Baustein anzulegen.
- Der GESUCHTE Bug betraf laut Befund auch "gleiche Geometrie" beim Speicher-Fehler-Toast -- dafuer einen komplett neuen Testfall ergaenzt (nicht nur behauptet, dass derselbe Fix greift), inklusive eigenem PUT-Stub auf /api/lieferant-dokumente/{id}.
- Der bestehende Fehlerfall-Test wurde umbenannt (Zusatz "Toast verdeckt die Fussleiste nicht" im Titel) und um die zwei neuen Assertions erweitert statt einen komplett getrennten Test zu schreiben -- inhaltlich derselbe Ablauf (Oeffnen mit Lock-Fehler), keine Pruefkraft verloren, nur ergaenzt.

## Abschnitt 6 — Task 6a Nachbesserung 1 (Coding-Agent)

- Zeit: 2026-09-04, ca. 20:55–21:15 (lokale Sessionzeit)
- Branch: lock/task-6a-editor-seite
- Commit: 9a1c4f15 — fix(dokument-editor): neu angelegtes Dokument verliert Sperre nicht mehr nach 90s
- Status: 🟢 fertig. `npx vitest run src/components/document-editor/index.test.tsx src/components/lock/TabSchliessenHinweis.test.tsx` → 25/25 grün. `npm run lint` → 0 Fehler, genau die 1 vorbestehende Warnung (BelegeKasseEditor.tsx:1204). `npm run build` → grün, Output zurückgesetzt. `E2E_PORT=5179 npx playwright test e2e/dokument-editor-tab-schliessen.spec.ts` → weiterhin 6/6 grün (nicht geändert, aber gegen den neuen Code nachgefahren).

**Befund (vom Code-Review, bestätigt):** `syncDocumentIdInUrl` schrieb die beim Anlegen vergebene Id per `window.history.replaceState` in die URL. React-Router bekam das nicht mit, `useSearchParams` in `DocumentEditorPage.tsx` blieb unverändert, der Seiten-Lock-Hook (`useDocumentLock`) blieb auf `idle` und pingte nie. Nach `STALE_AFTER` (90s, serverseitig) scheiterte jeder folgende PUT — auch der 10-Sekunden-Autosave — mit 409, ohne Rettungsweg (das alte `tryAcquireLock`-Retry ist mit der Aufräumung in Abschnitt 6a weg).

**Gewählter Weg: Router statt `replaceState` (Weg 1 aus dem Auftrag).** Begründung: kleiner, bleibt vollständig in `index.tsx`/`index.test.tsx`, keine Änderung an `DocumentEditorPage.tsx` nötig (die Datei gehört Abschnitt 7a). `syncDocumentIdInUrl` nutzt jetzt `useSearchParams()`/`setSearchParams(..., { replace: true })` statt `window.history.replaceState` — die Seite (liest `dokumentId` über dieselben `useSearchParams`) bekommt die neue Id dadurch als Prop-Änderung mit, ihr Lock-Hook akquiriert (bekommt vom Server ACQUIRED zurück, weil das Backend das Lock beim Anlegen schon für den Ersteller hält) und startet seinen 30s-Heartbeat.

**Was zusätzlich geprüft/angepasst werden musste, damit der Editor beim `dokumentId`-Wechsel weder neu lädt noch Zustand verwirft:**
- Die "Load Document"-Effect (`useEffect(..., [dokumentId])`) hätte bei einem Wechsel von `undefined` auf die frische Id sonst erneut `GET /api/ausgangs-dokumente/{id}` ausgeführt und den kompletten lokalen State (Blöcke, Kontextdaten, Datum, `hasUnsavedChanges` …) mit dem Server-Stand überschrieben — ein Verlust zwischenzeitlicher, noch nicht gespeicherter Tastatureingaben. Guard ergänzt: `if (dokument?.id === dokumentId) { setLoading(false); return; }` — `setDokument(created)` läuft im selben `handleSave`-Aufruf synchron VOR `syncDocumentIdInUrl`, ist also schon gesetzt, wenn die Prop-Änderung beim nächsten Render ankommt.
- Der Pfad-Guard in `syncDocumentIdInUrl` (nur auf `/dokument-editor` aktiv) prüfte bisher `window.location.pathname` — das ist bei einem `MemoryRouter` (Tests, aber auch technisch bei künftiger Einbettung) NIE die echte Browser-URL. Umgestellt auf `useLocation().pathname` aus react-router. Ohne diesen zweiten Fix blieb der rote Test aus Punkt 1 rot, obwohl `setSearchParams` bereits verdrahtet war — Fund direkt beim ersten Testlauf.

**Rote Tests:**
1. „kann ein frisch angelegtes Dokument auch nach mehr als 90s noch speichern" (`index.test.tsx`, neue Describe „Sperre nach Neuanlage bleibt am Leben"): Mini-Nachbau der Seiten-Verdrahtung (`SeiteMitAltemLock`: `useSearchParams` + der echte, heute noch aktive `useDocumentLock`-Hook + `DocumentEditor`), Fake-Timer, Stub mit tatsächlich beobachtbarem Lock-Verfall (PUT antwortet mit 409, wenn seit dem letzten Acquire/Heartbeat mehr als 90s vergangen sind). Gegen den Alt-Stand rot: `expected false to be true` bei der Prüfung, ob je ein Request an `/api/dokument-locks/AUSGANG/42/acquire` ging (ging nie, weil die Seite die neue Id nie sah). Nach dem router-Fix zunächst weiterhin rot (derselbe Fehler) — Ursache war der zweite Fund (`window.location` vs. `useLocation()`), danach grün.
2. „zeigt bei einem Versionskonflikt (JSON-Body) die Neu-laden-Meldung statt rohem JSON" (`index.test.tsx`, neue Describe „409 beim Speichern: zwei verschiedene Ursachen"): PUT mit `content-type: application/json` und `ApiError`-Body. Gegen den Alt-Stand rot: Timeout beim Warten auf den Text „Nicht gespeichert" (der alte Code zeigte stattdessen `res.text()`, also den rohen JSON-String, in einem Toast). Nach der Umstellung auf `useKonfliktMeldung.pruefeAntwort(res)` bei JSON-Content-Type grün.
3. „raeumt den 150ms-Warte-Timer beim Unmount auf": Spy auf `window.setTimeout`/`window.clearTimeout`, geprüft wird die EXAKTE Timer-Id (nicht nur „clearTimeout wurde irgendwann aufgerufen" — ein erster, zu lascher Entwurf dieses Tests blieb beim Alt-Stand fälschlich grün, weil irgendwo im Baum ohnehin `clearTimeout` für andere Timer läuft). Mit der exakten Id gegen den Alt-Stand rot (`Number of calls: 6`, keiner mit der erwarteten Id). Nach `tabSchliessenTimerRef` + `window.clearTimeout(...)` im Unmount-Cleanup grün.
4. Companion-Test „zeigt bei einer Fremdsperre (Text-Body) weiterhin die einfache Warnmeldung, keinen Neu-laden-Dialog" — war bereits mit dem Alt-Stand grün (die einfache `toast.warning(res.text())`-Behandlung war für den Text-Body-Fall nie falsch), bleibt als Regressionsschutz für die neue Content-Type-Verzweigung stehen.
5. Bestehender Test „sendet nach Mount und Unmount keinen Request an /api/dokument-locks/…" (Abschnitt 6a) unverändert grün — der Editor selbst holt weiterhin kein eigenes Lock; alle neuen Requests in Test 1 laufen über den (test-eigenen) Seiten-Hook, nicht über den Editor.

**Bedenken / Abweichungen vom Plan:**
- `syncDocumentIdInUrl`s Dependency-Array hat sich durch `useSearchParams`/`useLocation` von `[]` auf `[searchParams, setSearchParams, location]` geändert — dadurch bekommt `handleSave` (hängt an `syncDocumentIdInUrl`) bei jeder Navigation eine neue Funktionsreferenz und der Autosave-`setInterval` (hängt an `handleSave`) wird entsprechend neu aufgesetzt. Funktional unbedenklich (der Effekt räumt sein Intervall im Cleanup sauber ab, siehe bestehender Code), aber eine Verhaltensänderung gegenüber der bisherigen, komplett stabilen `[]`-Referenz — hier vermerkt statt still hingenommen.
- Die 90s-Testprobe nutzt einen lokal im Testfile nachgebauten Mini-Wrapper (`SeiteMitAltemLock`) statt `DocumentEditorPage.tsx` selbst — bewusst, weil die echte Seite laut Restplan erst in Abschnitt 7a auf `useDatensatzLock`/`readOnly`/`onLockFreigeben` umgestellt wird und ich diese Datei nicht anfassen darf/soll. Der Wrapper nutzt aber den ECHTEN, heute noch aktiven `useDocumentLock`-Hook (kein Mock) — das Verhalten ist damit ehrlich geprüft, nur die Verdrahtung drumherum ist nachgebaut statt importiert.
- Für den Versionskonflikt-Fall wird jetzt `useKonfliktMeldung('Dokument')` verwendet (Pflicht-Komponente, bereits von 6b genutzt) — das öffnet bei einem Versionskonflikt einen `confirm()`-Dialog mit „Neu laden"/„Abbrechen" statt eines reinen Toasts. Das ist eine sichtbare UX-Änderung gegenüber dem bisherigen (fehlerhaften) Zustand, aber genau das im Auftrag verlangte Verhalten (dieselbe Behandlung wie bei 6b).

## Abschnitt 6 — Design-Review 2 (Design-Reviewer)

**Ampel: 🟡 — von meiner Seite abgenommen.** Der 🔴 aus Durchgang 1 ist behoben, selbst
nachgemessen. Kein neuer blockierender Befund.

Worktree `wt/review-design`, Stand `320cd455`. `E2E_PORT=5190 npm run test:e2e`:
**84 Tests, alle grün**, beide Größen `pc-14zoll` (1440×900) und `pc-monitor` (1920×1080).
Zwei Tests mehr als in Durchgang 1 (82) — neu ist die Fußleisten-Probe in beiden
Fehler-Szenarien von `lieferant-dokument-modal.spec.ts`. Kein `website-*`-Test rot.

### Der 🔴 aus Durchgang 1: behoben

**Gemessen, nicht geglaubt.** Eigene Nachmessung im Browser (Wegwerf-Spec, danach gelöscht),
jeweils 1,4 s nach dem Auslösen, also mit voll eingeblendetem Toast:

| Szenario | Größe | Toast | `Abbrechen` | `elementFromPoint` Mitte | `Speichern` | `elementFromPoint` Mitte |
| --- | --- | --- | --- | --- | --- | --- |
| Lock-Fehler beim Öffnen | pc-14zoll | x 978–1416, y 24–70 | 1139–1240, 812–850 | **BUTTON „Abbrechen"** | 1252–1379, 812–850 | **BUTTON „Speichern"** |
| Lock-Fehler beim Öffnen | pc-monitor | x 1458–1896, y 24–70 | 1495–1596, 983–1021 | **BUTTON „Abbrechen"** | 1608–1735, 983–1021 | **BUTTON „Speichern"** |
| Speicherfehler | pc-14zoll | x 1096–1416, y 24–70 | 1139–1240, 812–850 | **BUTTON „Abbrechen"** | 1252–1379, 812–850 | **BUTTON „Speichern"** |
| Speicherfehler | pc-monitor | x 1576–1896, y 24–70 | 1495–1596, 983–1021 | **BUTTON „Abbrechen"** | 1608–1735, 983–1021 | **BUTTON „Speichern"** |

8 von 8 Treffern landen auf dem jeweiligen Knopf, keiner auf dem Toast
(`istToast: false` in allen acht Fällen). Der Container trägt in allen vier Läufen die
Klassen `fixed z-[9999] … top-6 right-6`. Deckt sich mit der Meldung des Agenten.

Zusätzlich geprüft, was der Toast an seinem neuen Platz verdeckt — nichts Gebrauchtes:

| Element (pc-14zoll) | Rahmen | Treffer in der Mitte |
| --- | --- | --- |
| Schließen-X des Modals | 1353–1387, 74–108 | `path` im X-Knopf (nicht Toast) |
| „Vorschau aktiv" | 1191–1339, 74–110 | BUTTON „Vorschau aktiv" |
| `Bearbeiten` / `Fertig` | 1263–1379, 141–175 | der jeweilige Button |
| Titel „Dokument bearbeiten" | 61–240, 78–106 | liegt weit links, Toast beginnt bei x 978 |

Auf `pc-monitor` dasselbe Bild. Die Fußleiste ist auf beiden Größen frei, der Modal-Kopf
bleibt bedienbar.

**Zur Frage, ob die Meldung aus dem Blick wandert:** in beiden Abläufen dieses Abschnitts
nein — die Meldung steht doppelt. Beim Lock-Fehler als rotes Band oben im Modal (genau dort,
wo der Blick nach dem Öffnen hinfällt), beim Speicherfehler zusätzlich als rotes Band direkt
über der Fußleiste, also unmittelbar neben dem gerade gedrückten `Speichern`. Der Toast ist
in beiden Fällen die Zweitmeldung, nicht der einzige Kanal. Kein 🟡.

**Ist das rote Band lauter als der Nur-Lesen-Hinweis?** Ja, deutlich. Der Lock-Fehler ist
jetzt ein Band mit Fläche (red-50), Rand (red-300), `AlertTriangle` in red-600 und
halbfettem red-700-Text; der `GesperrtHinweis` bleibt das ruhige rose-50-Band mit
`Lock`-Icon und normalem slate-700. Die Dringlichkeit ist damit richtig herum verteilt —
der 🟡 aus Durchgang 1 ist erledigt.

### Angeschaute Screenshots

Alle 14 aus `react-pc-frontend/test-results/design/`, jeweils `--pc-14zoll` und `--pc-monitor`:

1. `dokument-editor-vor-schliessen`
2. `dokument-editor-ungespeichert-warnung`
3. `lieferant-modal-bearbeiten`
4. `lieferant-modal-gesperrt`
5. `lieferant-modal-fremdes-lock`
6. `lieferant-modal-fehler`
7. `lieferant-modal-speicherfehler-toast` (neu in diesem Durchgang)

Dazu 8 eigene Zusatz-Aufnahmen (Wegwerf-Spec, danach gelöscht, Worktree ist sauber):
`nachmessung-lockfehler`, `nachmessung-speicherfehler`, `probe-zweizeiliger-toast`,
`editor-versionskonflikt`, `editor-sperrkonflikt-toast` — je `--pc-14zoll` und `--pc-monitor`.

### Die sechs Fragen je Zustand

**`dokument-editor-vor-schliessen` (14 Zoll + Monitor)**
1. *Farben?* Ja, unverändert gut: eine rosa Primäraktion (`PDF`), Rest slate.
2. *Design-System?* Ja. Die Vorschau zeigt jetzt sauber das Skelett-Muster (rose/slate-Balken)
   statt eines leeren Kastens — genau die Vorgabe „nie ein leeres Loch beim Laden".
3. *Look-and-Feel?* Ruhig, ausgewogen, auf 1920 nichts verwaist.
4. *UX?* Leerer Zustand in ganzen Sätzen, Handwerker-Sprache.
5. *Auffindbar?* Ja, X-Knopf oben links ohne Scrollen.
6. *Überschneidung?* Nein — und der 🟡 aus Durchgang 1 ist weg: die Vorschau-Spalte steht
   jetzt **ausgelaufen** bei 45 % (14 Zoll x 792–1440 = 648 px, 1920 x 1057–1920 = 863 px)
   statt bei ~88 px mitten in der Animation. `uebergaengeAusklingenLassen` wirkt.

**`dokument-editor-ungespeichert-warnung` (14 Zoll + Monitor)**
1.–6. Unverändert gut: drei klar gestufte Ausgänge, genau eine rosa Primäraktion
   (`Speichern & Schließen`), amber-50/amber-500 als Warn-Icon, `rounded-2xl`, `shadow-2xl`,
   mittig, keine Überschneidung, kein waagerechtes Scrollen.
   Bekannt aus 7c: `Nicht speichern` bricht weiter auf zwei Zeilen um. Nicht schlechter geworden.

**`lieferant-modal-bearbeiten` (14 Zoll + Monitor)**
1. *Farben?* Jetzt richtig: `Fertig` steht in **beiden** Größen als weißer Outline-Knopf mit
   rose-Rand und rose-Text da, `Speichern` ist die einzige gefüllte rosa Fläche. Der halbe
   Farbübergang aus Durchgang 1 ist weg.
2. *Design-System?* Ja, rose/slate, Lucide, kein Emoji, Systemschrift.
3. *Look-and-Feel?* Ruhig. Linke Bandhälfte weiterhin leer (7c), unverändert.
4. *UX?* Eine Primäraktion, Formular sichtbar bedienbar.
5. *Auffindbar?* Ja, `Fertig` am Kopf, ohne Scrollen.
6. *Überschneidung?* Nein, kein waagerechtes Scrollen.

**`lieferant-modal-gesperrt` (14 Zoll + Monitor)**
1.–6. Unverändert zu Durchgang 1. Zwei rosa Knöpfe (`Bearbeiten` voll, `Speichern` bei 50 %),
   Zustandswechsel nur über die Textfarbe der Felder, linke Bandhälfte ohne Satz — alles
   bekannt und in 7c. Keine Überschneidung, nichts abgeschnitten, nichts verschlechtert.

**`lieferant-modal-fremdes-lock` (14 Zoll + Monitor)**
1.–6. Unverändert der stärkste Zustand: volles rose-50-Band mit `Lock`, Name fett,
   „Thomas Beispiel bearbeitet das gerade — Sie sehen den aktuellen Stand. Seit 5 Min.",
   `Bearbeiten` sichtbar und aktiv. Auf 14 Zoll ohne Scrollen am Kopf des Datensatzes.
   Keine Überschneidung.

**`lieferant-modal-fehler` (14 Zoll + Monitor)** — Acquire liefert 500
1. *Farben?* Jetzt klar: rotes Band (red-50/red-300/red-700) mit `AlertTriangle` gegen das
   rose-50 des Nur-Lesen-Hinweises. Auf einen Blick unterscheidbar.
2. *Design-System?* Ja. `red` ist die dokumentierte Danger-Semantik, keine Fremdpalette.
3. *Look-and-Feel?* Das Band nimmt die volle freie Breite und füllt die Leiste auch auf 1920.
4. *UX?* Meldung im Modal **und** als Toast. `Bearbeiten` weiterhin deaktiviert ohne Tooltip (7c).
5. *Auffindbar?* Ja.
6. *Überschneidung?* **Nein — der Befund aus Durchgang 1 ist weg.** Toast oben rechts,
   Fußleiste frei, Werte oben in der Tabelle.

**`lieferant-modal-speicherfehler-toast` (14 Zoll + Monitor)** — neu
1. *Farben?* Toast red-50/red-200 mit `XCircle`, dazu das rote Inline-Band über der Fußleiste.
   `Speichern` bleibt als rose-600 die Primäraktion, klar getrennt vom Rot der Fehlermeldung.
2. *Design-System?* Ja.
3. *Look-and-Feel?* In Ordnung. Wenn das Inline-Band erscheint, schrumpft der Formularbereich
   um ~46 px und der Inhalt rutscht — kosmetisch, kein Fehler.
4. *UX?* Vorbildlich: Fehler steht direkt neben dem gedrückten Knopf **und** im Toast.
5. *Auffindbar?* Ja, `Speichern` frei bedienbar (gemessen, s. o.).
6. *Überschneidung?* Nein. Auf 1920 fällt auf, dass die Skonto-Zeile vom Inline-Band
   angeschnitten wird — das ist die Kante des Scroll-Containers, bekannt aus 7c.

**Zusatz `editor-versionskonflikt` (14 Zoll + Monitor)** — 409 mit JSON-Body
1. *Farben?* 🟡: `Neu laden` ist **amber-500 gefüllt**, nicht rose-600 — siehe Hinweise.
   Sonst sauber: amber-100-Kreis mit `AlertTriangle`, `Abbrechen` als slate-Outline.
2. *Design-System?* Der Dialog selbst ja — es ist die Pflicht-Komponente `useConfirm()`
   (`ui/confirm-dialog.tsx`), kein selbstgebautes Modal: weiß, `rounded-2xl`, `shadow-2xl`,
   `bg-black/40 backdrop-blur-sm`. Nur die Knopffarbe fällt aus der Reihe.
3. *Look-and-Feel?* Ruhig, mittig, in beiden Größen gleich.
4. *UX?* Wording ist genau richtig und der Kern der Nachbesserung: „Nicht gespeichert —
   Jemand anders hat dieses Dokument gerade gespeichert. Ihre Änderungen wurden nicht
   übernommen — bitte neu laden." Kein rohes JSON mehr, Handwerker-Sprache, zwei klare Wege.
5. *Auffindbar?* Ja, mittig im Bild.
6. *Überschneidung?* Nein. Gemessen: kein Toast im DOM (`toastDa: false` — der Dialog
   **ersetzt** ihn, richtig so), `Abbrechen` (521,509,194×42) und `Neu laden`
   (727,509,192×42) auf 14 Zoll bzw. (761,599) und (967,599) auf 1920 beide frei anklickbar.

**Zusatz `editor-sperrkonflikt-toast` (14 Zoll + Monitor)** — 409 mit Text-Body
1. *Farben?* amber-50/amber-200 mit `AlertTriangle` — `toast.warning`, nicht `error`. Richtige
   Stufe: es ist kein Fehler, sondern „gleich nochmal versuchen".
2. *Design-System?* Ja.
3. *Look-and-Feel?* Unauffällig unten rechts.
4. *UX?* „Dokument wird gerade von Thomas Beispiel bearbeitet" — der Servertext, verständlich,
   mit Namen. Genau die Trennung, die die Nachbesserung wollte.
5. *Auffindbar?* Ja.
6. *Überschneidung?* Nein. Im Editor ist kein `role="dialog"` offen, der Toast bleibt also
   korrekt unten rechts und verdeckt die Statuszeile (Netto/MwSt/Brutto) nicht.

### 💡 Hinweise (blockieren nicht)

- **Nur 4 px Luft nach unten.** Der Toast endet bei y = 70, der Schließen-X des Modals
  beginnt bei y = 74 (14 Zoll) bzw. 83 (1920). Probe mit einem zweizeiligen Text von
  realistischer Länge: die Toast-Höhe wächst von 46 auf 86 px, die Unterkante auf y = 110 —
  und dann trifft `elementFromPoint` in der Mitte von Schließen-X **und** „Vorschau aktiv"
  auf beiden Größen den Toast. Heute tritt das nicht ein, weil beide Meldungen dieses
  Abschnitts kurz und fest sind (`LOCK_FEHLER_TEXT`, „Speichern fehlgeschlagen"). Der
  Container ist aber global und andere Stellen bauen ihre Meldungen aus Servertexten
  zusammen. Ein bisschen Abstand nach oben oder ein schmalerer Toast bei offenem Dialog
  würde die Kante entschärfen.
- **`Neu laden` ist amber-500 gefüllt statt rose-600** (`useKonfliktMeldung.ts`,
  `variant: 'warning'`). Amber ist im Design-System die Warnfarbe für Icons und Badges;
  gefüllte Primäraktionen sind rose-600. Der `UnsavedChangesModal` im selben Editor macht
  genau das: amber-Icon, rose-600-Knopf. `variant: 'info'` würde beides angleichen.
- Auf 1920 ragt der Toast über den rechten Modalrand hinaus (Modal endet bei x ≈ 1758, Toast
  bis x 1896) und liegt damit halb auf dem Modal, halb auf der Anwendung dahinter. Verdeckt
  nichts, sieht nur etwas beliebig platziert aus.
- `confirm-dialog.tsx` setzt kein `role="dialog"`. Der Toast-Umzug greift deshalb nicht,
  wenn nur ein Confirm-Dialog offen ist. In den geprüften Abläufen egal (der Dialog ist
  mittig, der Toast unten rechts, und beim Versionskonflikt gibt es gar keinen Toast) —
  aber der Auslöser des Fixes hängt an einem Attribut, das eine der beiden Dialog-Arten
  im Projekt nicht trägt.
- Aus Durchgang 1 unverändert und in Task 7c aufgehoben: drei gleich aussehende rose-Bänder
  (Hinweis / Countdown / Verbindung weg), Countdown ohne Icon, kein Tooltip am deaktivierten
  `Bearbeiten`, `Fertig`-Sprung beim Erscheinen des Countdowns, „Nicht speichern" auf zwei
  Zeilen, PDF-Spalte frisst auf 14 Zoll zwei Drittel des Modals. **Nichts davon hat sich
  verschlechtert.**
- Du/Sie liegt weiter beim Nutzer.

## Abschnitt 6 — Code-Review 2 (Code-Reviewer)

**Ampel: 🟡** — der 🔴 aus Durchgang 1 ist behoben und nachgewiesen. Von meiner Seite abgenommen.
Stand `320cd455`.

### 🔴 aus Durchgang 1: **behoben**

*„Neu angelegte Dokumente verlieren nach 90 s ihre Sperre und lassen sich nicht mehr speichern."*

`syncDocumentIdInUrl` schreibt die Id jetzt über `setSearchParams(..., { replace: true })` statt
`window.history.replaceState`; der Pfad-Guard läuft über `useLocation()`. Die Seite sieht die neue
Id, ihr Lock-Hook akquiriert und pingt.

**Nachweis — Mutationsprobe:** `setSearchParams` zurück auf `window.history.replaceState` gedreht
⇒ `DocumentEditor – Sperre nach Neuanlage bleibt am Leben > kann ein frisch angelegtes Dokument
auch nach mehr als 90s noch speichern` wird rot:
`AssertionError: expected false to be true // Object.is equality` — das ist die
`akquiriert`-Zusicherung, die Seite holt das Lock also nie. Genau der Fehler aus Durchgang 1.

Der Test beweist wirklich etwas: sein Stub bildet den Serververfall nach (`STALE_AFTER` 90 s, PUT
antwortet 409, wenn zwischenzeitlich kein Heartbeat kam). Eine Stub, die PUT immer durchlässt,
wäre auch mit dem alten Code grün gewesen.

### Die drei Rückfragen zur 6a-Nachbesserung — gemessen, nicht vermutet

Eigene Wegwerf-Probe im Neuanlage-Szenario, 60 s Laufzeit nach dem Anlegen:
`refetch=0 acquire=1 put=0 post=1`

- **(b) Zustandsverlust beim Wechsel `dokumentId: undefined → 42`?** Nein. `refetch=0` — der Guard
  `dokument?.id === dokumentId` greift, das erneute Laden bleibt aus, also überschreibt nichts die
  ungespeicherten Blöcke. Der Guard steht im Load-Effekt vor jedem State-Schreiber außer
  `setLoading(false)`, und der Create-Zweig in `handleSave` ruft selbst nie `setBlocks`.
- **(c) Zweites Laden, zweites Acquire, Autosave-Doppelschlag?** Keins davon. Genau ein `acquire`
  (das der Seite; das Backend-Acquire in `create` ist serverintern und kostet keinen Client-Request),
  genau ein `POST`, kein einziger überflüssiger `PUT` über sechs Autosave-Fenster hinweg.
- **(d) Endlosschleife durch die neue Dependency-Kette?** Nein. `syncDocumentIdInUrl` wird
  ausschließlich aus `handleSave` gerufen, nie aus einem Effekt, und steigt früh aus, wenn der
  Suchparameter schon den richtigen Wert hat. Der Autosave-Effekt hing schon vorher an `blocks`
  (neue Array-Identität bei jeder Änderung) und wurde daher ohnehin bei jeder Eingabe neu
  aufgezogen — `searchParams`/`location` ändern sich nur bei Navigation und machen das nicht
  schlechter. Volle Suite läuft ohne Hänger durch.

### Befund zu `src/components/ui/toast.tsx`

- **Unmount:** `return () => observer.disconnect()` ist da. ✓
- **Kosten:** gemessen — 500 DOM-Mutationen im `body`-Subtree erzeugen **0** zusätzliche Renders im
  Provider-Baum. `setOffen` mit unverändertem Boolean lässt React aussteigen, der Provider (der die
  ganze App umschließt) rendert also nicht mit. Übrig bleibt ein `document.querySelector` pro
  MutationObserver-Microtask-Batch — vernachlässigbar. `attributeFilter: ['role']` begrenzt zudem
  den Attribut-Zweig.
- **Dauerhafte `role="dialog"`-Knoten?** Keine. Alle 8 Fundstellen im Produktivcode
  (`ArtikelAuswahlDialog`, `AlternativGruppeDialog`, `document-editor/Modals`, `EmailComposeForm`,
  `EmailZuordnungSearchModal`, `KassenbuchAbschlussLeiste`, `LieferantDokumentModal`,
  `LieferantenDetailsModal`) sind bedingt gemountet — entweder `if (!isOpen) return null` oder ein
  `{flag && (...)}` an der Aufrufstelle. Keine hängt versteckt dauerhaft im DOM. Toasts landen also
  nicht auf Dauer oben rechts.
- **Portale außerhalb von `document.body`:** der Erst-Check (`document.querySelector`) durchsucht
  das ganze Dokument und fände sie; nur spätere *Änderungen* außerhalb von `body` würden verpasst.
  Im Projekt hängt praktisch alles an `body` — theoretische Lücke.

**Mutationsprobe am Unit-Test: sie beißt nicht.** Den gesamten `MutationObserver` samt
`observe`/`disconnect` aus `useIrgendeinDialogOffen` entfernt ⇒ **alle 8 Tests in `toast.test.tsx`
bleiben grün** (`Test Files 1 passed, Tests 8 passed`). Beide Positionierungstests rendern den
Dialog schon beim Mount, treffen also nur den `useState`-Initializer und den Erst-Check im Effekt —
die eigentliche Dynamik ist ungeprüft.

Der Code selbst ist trotzdem richtig: eine eigene Wegwerf-Probe, die den Dialog erst **nach** dem
Mount öffnet und wieder schließt, zeigt den Container korrekt nach `top-6` und zurück nach
`bottom-6` wandern. Also Testlücke, kein Codefehler — deshalb 🟡 und kein 🔴.

### Selbst gemessene Zahlen

| | Durchgang 1 | jetzt |
|---|---|---|
| Backend | 2462 / 0 F / 4 E | **2462 / 0 Failures / 4 Errors** (unverändert) |
| Frontend Testdateien | 87 | **87** |
| Frontend Tests | 1027 | **1033** (+6) |
| Lint | 0 Fehler, 1 Warnung | **0 Fehler, 1 Warnung** |

- Backend ist von den Nachbesserungen nicht berührt; die 4 Errors sind namentlich dieselben
  umgebungsbedingten wie immer.
- **Erwartung „88 Dateien" stimmt nicht:** `ui/toast.test.tsx` gab es schon vor Abschnitt 6 (6 Tests),
  6b hat sie nur um 2 erweitert. Daher 87 Dateien und +6 Tests: 6a +4 (150-ms-Timer, zwei
  409-Ursachen, Neuanlage nach 90 s), Toast +2.
- Frontend-Volllauf diesmal **komplett grün, kein einziger Flake** — die Zeitschranken-Ausfälle aus
  Durchgang 1 waren tatsächlich Last.
- `npm run build` grün, `src/main/resources/static/` zurückgesetzt. Arbeitsbaum außer den
  Plan-Dokumenten sauber; Code byte-identisch zu `320cd455`.

### 💡 Hinweise (blockieren nicht)

- `ui/toast.test.tsx` — ein Test, der den Dialog **nach** dem Mount öffnet und wieder schließt,
  würde den `MutationObserver` tatsächlich absichern. Aktuell überlebt seine komplette Entfernung
  die Suite.
- `ui/toast.tsx` — kein Test für `observer.disconnect()` beim Unmount des Providers.
- `document-editor/index.tsx` (`handleSave`, Create-Zweig) — der neue Guard hängt daran, dass
  `setDokument(created)` **vor** `syncDocumentIdInUrl(created.id)` steht und beide im selben
  React-Batch landen. Stimmt heute; ein Halbsatz „Reihenfolge nicht tauschen" an der Stelle würde
  verhindern, dass jemand das später sortiert und den Guard aushebelt.
- Die beiden Nachbesserungen aus meinen Hinweisen in Durchgang 1 (409-JSON im Toast, 150-ms-Timer)
  sind umgesetzt und jeweils mit eigenem Test belegt — der Timer-Test prüft sogar die konkrete
  Timer-Id statt nur „`clearTimeout` wurde irgendwann gerufen". Sauber gemacht.

## Abschnitt 7 — Task 7b (Coding-Agent)

**Zeit:** 04.09.2026, ca. 22:40–22:57 Uhr
**Branch:** `lock/task-7b-hook` (auf `claude/eloquent-ramanujan-gz0w2t` @ `c394c6b4`)
**Commits:**
- `eca52122` — fix(sperr-hook): Generationspruefung nach json() und Modus bearbeiten nach Mount-Acquire
- `68c3d951` — refactor(lieferant-modal): Bearbeiten-Behelf entfernt, Hook uebernimmt Moduswechsel selbst

**Status:** ✅ fertig, alle Gates grün (nur eigene Testdateien/eigene Spec, wie vorgegeben).

### Was gemacht wurde

**Punkt 1 — Generationsprüfung nach `res.json()` im 409-Zweig (acquire + heartbeat)**

Roter Test zuerst, zwei neue Tests in `useDatensatzLock.test.tsx`:
- „ein durch freigeben() ueberholtes 409-Ergebnis beim Mount-Acquire schreibt nach dem
  verzoegerten json() keinen Zustand mehr" — künstlich hängendes `json()` (Promise, die erst
  nach einem zwischenzeitlichen `freigeben()` auflöst). Rote Meldung vor dem Fix:
  `AssertionError: expected 'locked-by-other' to be 'idle'` (Z. 250) — der überholte 409-Befund
  schrieb trotzdem noch `halterName`/`status`.
- Gleiches Muster für den Heartbeat-Zweig, gleiche rote Meldung (Z. 389).

Fix: in beiden 409-Zweigen (`acquire()` und `heartbeat()`) direkt nach dem `await res.json()`
eine zweite `if (!mountedRef.current || gen !== generationRef.current) return;`-Prüfung
eingefügt — die erste Prüfung direkt nach `fetch()` reicht nicht, weil `json()` selbst ein
zweiter `await` ist, in dessen Fenster ein `freigeben()` oder ein neuer Versuch den Aufruf
überholen kann.

**Mutationsprobe:** beide `if`-Zeilen wieder entfernt → exakt die 2 neuen Tests werden rot
(`expected 'locked-by-other' to be 'idle'`), alle anderen bleiben grün. Fix wiederhergestellt,
33/33 grün.

**Punkt 2 — Modus nach erfolgreichem Mount-Acquire ist `'bearbeiten'`**

Roter Test zuerst: bestehenden Test „startet im Modus 'lesen', auch nachdem das Lock
erfolgreich erworben wurde" umbenannt und umgeschrieben auf die neue Bedeutung (nicht
abgeschwächt) — rote Meldung vor dem Fix: `AssertionError: expected 'lesen' to be 'bearbeiten'`.
Zusätzlich: neuer Test „bleibt bei einer Fremdsperre beim Mount im Modus 'lesen'" (Absicherung,
dass NUR der Erfolgsfall wechselt), der bestehende Freigabe-Test um „bleibt bei 'lesen', bis der
Nutzer erneut klickt" erweitert, und das Zusammenspiel-Szenario mit der echten
`BearbeitenLeiste` umgeschrieben: zeigt jetzt direkt nach Mount „Fertig" (vorher musste der Test
selbst erst auf „Bearbeiten" klicken, bevor „Fertig" überhaupt erschien) — vor dem Fix:
`Unable to find role="button" and name "Fertig"` (Timeout, DOM zeigte nur „Bearbeiten").

Fix: `acquire()` setzt im Erfolgszweig jetzt zusätzlich `setModus('bearbeiten')` — sowohl beim
stillen Mount-Erwerb als auch beim Retry aus `onBearbeiten()`. `onBearbeiten()`s eigener
Wrapper (`await acquire(); if (heldRef.current) setModus('bearbeiten')`) wurde dadurch
redundant und auf `void acquire(lockUrl)` vereinfacht. 409 und Fehler bleiben unverändert bei
`'lesen'`. Docstring oben in der Datei um einen Absatz zur neuen Invariante ergänzt.

**Mutationsprobe:** `setModus('bearbeiten')` im Erfolgszweig wieder entfernt → 6 Hook-Tests rot
(u. a. der umbenannte „wechselt automatisch..."-Test und das Zusammenspiel-Szenario). Fix
wiederhergestellt, 33/33 grün.

**Punkt 3 — 6b's Behelf in `LieferantDokumentModal.tsx` entfernen**

Effekt entfernt, der bei `status === 'acquired' && modus === 'lesen'` einmalig
`lock.onBearbeiten()` rief (reiner Workaround aus Task 6b, weil der Hook das vorher nicht
selbst tat). Da Punkt 2 das jetzt im Hook erledigt, ist der Effekt überflüssig. Neuer Test in
`LieferantDokumentModal.test.tsx`: „ruft die Acquire-Route nur einmal auf — der frühere
Modal-Effekt (6b-Behelf) entfällt seit Task 7b" (prüft nach ein paar Mikrotask-Ticks, dass kein
zweiter `/acquire`-Request feuert). Sonst nichts im Modal angefasst.

**Kombinierte Mutationsprobe (aus dem Auftrag):** Punkt 2 zurückgesetzt (`setModus` im
Erfolgszweig entfernt) UND Punkt 3 angewendet (Effekt weg) → **11 von 14**
`LieferantDokumentModal`-Tests werden rot (u. a. „Fertig" nie gefunden, Formular bleibt
disabled), weil ohne den Hook-Fix und ohne den Behelf-Effekt niemand mehr den Modus
umschaltet. Fix wiederhergestellt, 14/14 grün.

Quellstand nach allen Proben wiederhergestellt und erneut per Vitest/Lint/Build bestätigt —
byte-identisch zum committeten Stand (per `git diff --stat` nach jeder Rücksicherung geprüft).

### Gates (nur eigene Dateien/eigene Spec, wie vorgegeben)

- `npx vitest run src/components/lock/useDatensatzLock.test.tsx
  src/components/LieferantDokumentModal.test.tsx` → **47/47 grün** (33 + 14).
- `npm run lint` → **0 Fehler, genau die 1 vorbestehende Warnung**
  (`BelegeKasseEditor.tsx:1204`, `react-hooks/exhaustive-deps`).
- `npm run build` → grün, `src/main/resources/static/` danach zurückgesetzt
  (`git checkout` für `index.html`/CSS, neue gehashte JS-Datei gelöscht).
- `E2E_PORT=5176 npx playwright test e2e/lieferant-dokument-modal.spec.ts` → **8/8 grün**
  (4 Tests × `pc-14zoll` + `pc-monitor`). Die bestehende 6b-Spec sichert weiter „öffnen mit
  freiem Lock ⇒ Formular frei, Leiste im Modus bearbeiten" zu — jetzt über den Hook statt den
  Effekt, unverändert grün. Screenshot `lieferant-modal-bearbeiten--pc-14zoll.png` kurz
  angeschaut: Modal öffnet direkt mit „Fertig"-Knopf und editierbarem Formular, Farben/Layout
  unauffällig (formale Design-Prüfung ist nicht meine Aufgabe als Coding-Agent).

### Bedenken / Abweichungen vom Plan

Keine. Plan und Realität stimmten überein — beide Befunde ließen sich wie im Auftrag
beschrieben reproduzieren und beheben. Einzige eigene Entscheidung: Commits nach Dateien
statt strikt nach den drei Punkten aufgeteilt (Punkt 1+2 in einem Commit für
`useDatensatzLock.ts`/-test, Punkt 3 in einem zweiten für das Modal) — Punkt 1 und 2 ändern
denselben Erfolgszweig in `acquire()` und teilen sich einen zusammenhängenden Docstring-Absatz;
eine Aufspaltung bis auf Hunk-Ebene hätte den Kommentartext künstlich zerrissen. „Gern je
Punkt" war laut Auftrag ohnehin optional.

## Abschnitt 7 — Task 7c (Coding-Agent)

Zeit: 2026-09-04T22:58:00Z
Branch: lock/task-7c-bearbeiten-leiste
Commit(s): 5e4047b6, b2b3c1cc
Status: fertig

Was gemacht wurde:

Pflichtlektüre vor dem ersten Edit: FRONTEND_UI.md, TESTING_SECURITY.md, Design-Skill
handwerkerprogramm-design (README + SKILL.md), playwright-design-pruefung/SKILL.md,
kriterien.md. Dazu BearbeitenLeiste.tsx/-test, useDatensatzLock.ts (Kommentar zu
kannBearbeiten), GesperrtHinweis.tsx, TabSchliessenHinweis.tsx/-test,
LieferantDokumentModal.tsx (nur gelesen, nicht angefasst) und die beiden bestehenden
E2E-Specs zum Abschauen der Stub-Muster.

**Punkt 1 (Prop-Kommentar):** kannBearbeiten-Doc in BearbeitenLeiste.tsx auf die seit
Review 5 gültige Bedeutung umgeschrieben ("ein Klick auf Bearbeiten ist gerade sinnvoll").
Reiner Kommentar, kein Verhaltenscode — kein eigener Test, useDatensatzLock.ts selbst nicht
angefasst (nicht in der Dateiliste).

**Punkt 2 (Tooltip am deaktivierten Knopf):** neuer optionaler Prop bearbeitenGesperrtGrund.
Roter Test zuerst (toHaveAttribute('title', ...) schlug mit null fehl, da der Prop noch nicht
existierte), dann title + aria-describedby (verweist auf ein sr-only-Element mit dem
Grundtext, via useId()) am Bearbeiten-Knopf ergänzt, nur wenn !kannBearbeiten UND der
(getrimmte) Grund nicht leer ist. 4 neue Tests: Tooltip mit Grund, kein title ohne Grund, kein
Tooltip bei aktiviertem Knopf trotz gesetztem Grund, Leerraum-Grund wird ignoriert.
Mutationsprobe: grund hart auf undefined gesetzt → der erste dieser Tests wird rot (erwartet
title="Sperre wird gerade geprüft…", bekommt null) — zurückgesetzt, wieder grün.

**Punkt 3 (drei unterscheidbare Bänder):** Countdown-Band von rose-50 auf amber-50/amber-300/
amber-800 mit neuem Timer-Icon (fehlte als einziges Band); Verbindungswarnung von rose-50 auf
red-50/red-300/red-700 (font-semibold), exakt die Farbskala des Fehlerbands im Lieferant-Modal
(AlertTriangle-Band dort, hier bleibt WifiOff). GesperrtHinweis bewusst nicht angefasst (bleibt
rose-50, nicht meine Datei). 2 neue Klassen-Assertion-Tests. Mutationsprobe: beide Farbsätze
einzeln zurück auf die alten rose-Klassen gesetzt → jeweils der zugehörige Test wird rot
(toMatch(/amber-50/) bzw. /red-50/ schlägt fehl) — zurückgesetzt, wieder grün.

**Punkt 4 (Fertig springt nicht):** Ursache verstanden, bevor codiert wurde — das einzige
heutige Verwender-Layout (LieferantDokumentModal.tsx, nur gelesen) bindet die Leiste in einen
flex justify-between-Container ein (Hinweis links mit flex-1 min-w-0, Leiste rechts ohne
Wachstum) — bei genau zwei Flex-Kindern hält justify-content: space-between das RECHTE Kind an
der rechten Kante fest, unabhängig von dessen eigener Breite; nur die LINKE Kante des Kindes
wandert. Der Umschalt-Knopf war bisher das ERSTE Kind der Leiste und klebte damit an der
wandernden linken Kante → er sprang um die volle Bandbreite nach links, sobald ein Band
erschien (der gemeldete ~540-px-Sprung). Fix: alle Status-Bänder (Lesen-Hinweis, Countdown,
Verbindung) stehen jetzt VOR dem Knopf im Markup, der Knopf ist immer das letzte Kind — damit
bleibt er an der (fixen) rechten Kante, egal wie viele Bänder davor erscheinen/verschwinden.
2 neue Unit-Tests sichern die Markup-Reihenfolge ab (auch für den bandlosen Leerzustand).
Mutationsprobe: Knopf testweise wieder als erstes Kind gerendert → beide Reihenfolge-Tests
werden rot — zurückgesetzt, wieder grün. Der eigentliche Nachweis ist aber die Playwright-Spec
(siehe Gates unten): getBoundingClientRect-x-Differenz des Fertig-Knopfs vor/nach Erscheinen
von Countdown- bzw. Verbindungs-Band ≤ 5 px, auf pc-14zoll UND pc-monitor.

Bewusste Einschränkung dieses Fixes: er behebt exakt das heute reale, gelesene Layout
(rechtsbündiger Block). Ein künftiger Verwender mit einem LINKS-verankerten Layout (z. B. die
Dokument-Editor-Seite, die die Leiste laut Auftrag "bald" nutzt) könnte dieselbe Verschiebung
in die andere Richtung bekommen — das lässt sich ohne Kenntnis von dessen Layout nicht
vorwegnehmen und müsste bei der Integration dort mit einer eigenen Positions-Probe erneut
geprüft werden.

**Punkt 5 (Lesen-Hinweis "Sie lesen nur mit."):** neuer optionaler Prop
zeigeNurLesenHinweis (Default false). Begründung für Prop statt Ableitung: aus
modus/kannBearbeiten allein sind idle (frisch frei) und locked-by-other (Fremdsperre) in
useDatensatzLock nicht unterscheidbar (beide liefern modus='lesen', kannBearbeiten=true) — nur
die aufrufende Seite weiß, ob sie zusätzlich GesperrtHinweis zeigt. Default bewusst false
(nicht true), damit eine Seite, die den Prop (noch) nicht setzt, den neuen Text nicht
fälschlich NEBEN einem sichtbaren GesperrtHinweis doppelt anzeigt. 3 neue Tests (zeigt bei
true+lesen, zeigt nicht bei Default, zeigt nicht im Bearbeiten-Modus). Mutationsprobe:
Bedingung mit false && kurzgeschlossen → der "zeigt bei true"-Test wird rot — zurückgesetzt,
wieder grün.

**Punkt 6 (TabSchliessenHinweis siezen):** Text auf "Dokument gespeichert und freigegeben —
Sie können diesen Tab jetzt schließen." geändert. Roter Test zuerst (beide neuen/geänderten
Tests in TabSchliessenHinweis.test.tsx liefen gegen den alten Du-Text rot), dann Text im
Component angepasst, grün. e2e/dokument-editor-tab-schliessen.spec.ts geprüft — referenziert
diesen Text NICHT (eigener Kommentar dort erklärt, dass eine echte Browser-Probe für den neuen
Prop-gesteuerten Ablauf erst mit Abschnitt 7a möglich wird), also nichts dort zu ändern.

**Neue Playwright-Spec e2e/bearbeiten-leiste.spec.ts:** rendert die Leiste über das
Lieferant-Dokument-Modal (/api gestubbt wie in lieferant-dokument-modal.spec.ts, Dummy-Namen,
eigene IDs). 5 Tests, je einmal designPruefung(...): leiste-bearbeiten, leiste-lesen (nach
Fertig-Klick), leiste-countdown, leiste-verbindung-weg, leiste-deaktiviert. Countdown und
Verbindungsverlust hängen an echten Timern (60s-Vorwarnung ab 240s, Heartbeat alle 30s, "weg"
ab 2 Fehlschlägen) — dafür page.clock.install() + runFor() genutzt (nicht fastForward(), das
würde mehrfach fällige Intervalle nur einmal feuern lassen und den 2. Heartbeat-Fehlschlag
verschlucken). Beide Positions-Proben (Countdown- und Verbindungs-Test) bestätigen Punkt 4 im
echten Browser.

Bekannte Lücke (siehe "Bedenken" unten): bearbeitenGesperrtGrund und zeigeNurLesenHinweis sind
in BearbeitenLeiste.tsx vollständig implementiert und unit-getestet, werden aber von
LieferantDokumentModal.tsx (nicht in meiner Dateiliste, siehe Auftrag "Files — nur diese") noch
nicht durchgereicht. Der "leiste-deaktiviert"-Zustand in der neuen Spec zeigt darum einen
deaktivierten Knopf OHNE sichtbaren Tooltip-Text (die Komponente KANN ihn zeigen, bekommt hier
aber keinen Grund übergeben) — Nachweis für Punkt 2 ist am Ende ausschließlich
unit-testbasiert. Ebenso zeigt leiste-lesen in der Spec (nach Fertig-Klick, idle-Zustand)
keinen "Sie lesen nur mit.", weil das Modal zeigeNurLesenHinweis nicht setzt.

### Gates (nur eigene Dateien/eigene Spec, wie vorgegeben)

- `npx vitest run src/components/lock/BearbeitenLeiste.test.tsx
  src/components/lock/TabSchliessenHinweis.test.tsx` → **32/32 grün** (25 + 7).
- `npm run lint` → **0 Fehler, genau die 1 vorbestehende Warnung**
  (BelegeKasseEditor.tsx:1204, react-hooks/exhaustive-deps).
- `npm run build` → grün, src/main/resources/static/ danach zurückgesetzt (git checkout für
  index.html/CSS, neue gehashte JS-Datei gelöscht).
- `E2E_PORT=5277 npx playwright test e2e/bearbeiten-leiste.spec.ts` → **10/10 grün** (5 Tests
  × pc-14zoll + pc-monitor). Screenshots unter
  test-results/design/leiste-<zustand>--<projekt>.png kurz selbst angeschaut: amber-Countdown
  mit Timer-Icon und kräftig-rotes Verbindungs-Band sind auf einen Blick von der rose-50-
  Nur-Lesen-Zeile unterscheidbar, Fertig-Knopf sitzt in beiden Bandzuständen sichtbar an
  derselben Stelle wie im Grundzustand; formale Design-Beurteilung bleibt beim
  Design-Reviewer.

### Bedenken / Abweichungen vom Plan

1. bearbeitenGesperrtGrund/zeigeNurLesenHinweis nicht ins Lieferant-Modal verdrahtet.
   LieferantDokumentModal.tsx steht nicht in der Dateiliste dieses Tasks ("Files — nur
   diese"), darum nicht angefasst ("Braucht ein Punkt eine Datei außerhalb deiner Liste:
   melden, nicht anfassen"). Beide Punkte sind auf Komponentenebene fertig und mutationsfest
   getestet; die letzte Meile (Prop im Modal übergeben — für Punkt 2 z. B. gesperrtTooltip als
   bearbeitenGesperrtGrund, für Punkt 5 lock.status === 'idle' als zeigeNurLesenHinweis) ist
   ein Ein-Zeiler pro Prop, den eine künftige Aufgabe an dieser Datei mitnehmen sollte, sonst
   bleiben beide Design-Review-Befunde im Produktivbetrieb nur halb sichtbar.

2. Zweite, im Auftrag nicht erwähnte Fundstelle für Punkt 6. Neben der explizit genannten
   e2e/dokument-editor-tab-schliessen.spec.ts (dort nichts zu ändern, siehe oben) hat auch
   src/components/document-editor/index.test.tsx:634 (Test "zeigt den Tab-Schließen-Hinweis,
   wenn window.close wirkungslos bleibt") den alten Du-Text fest verdrahtet:
   screen.findByText(/kannst diesen Tab jetzt schließen/). Diese Datei steht NICHT in meiner
   Dateiliste — nicht angefasst, nur gemeldet. Nach dem Merge dieses Tasks wird dieser eine
   Test dort mit "element(s) not found" rot, bis jemand die Regex auf die neue Sie-Form
   anpasst (z. B. /können diesen Tab jetzt schließen/). Bitte beim Review/Merge einplanen.

3. Layout-Fix (Punkt 4) ist an das heutige Lieferant-Modal-Layout gebunden, siehe
   ausführliche Begründung oben unter Punkt 4 — kein Fehler, nur ein Hinweis für die künftige
   Dokument-Editor-Integration.

4. Portkollision beim ersten Testlauf, nicht mein Code: E2E_PORT=5177 (wie im Auftrag
   vorgeschlagen) war zum Zeitpunkt des Testlaufs bereits von einem fremden Prozess belegt
   (vite-pruefung.mjs aus einer anderen Session, unter einem fremden scratchpad-Pfad) —
   Playwrights reuseExistingServer hat sich an diesen fremden Server gehängt und einen völlig
   anderen Ablauf ausgeliefert (alte DocumentLockedModal/DocumentEditorPage-Route statt
   LieferantDokumentModal). Zur Kontrolle probeweise auch die bestehende
   lieferant-dokument-modal.spec.ts auf Port 5177 laufen lassen — dieselbe Fehlmeldung, obwohl
   an dieser Datei nichts geändert wurde. Mit einem freien Port (5277) liefen beide Specs
   anschließend grün. Kein Eingriff in den fremden Prozess (nicht meiner, nicht angefasst) —
   nur der eigene Port gewechselt.

## Abschnitt 7-1 — Design-Review (Design-Reviewer)

**Ampel: 🟡 — von meiner Seite abgenommen.** Alle drei Nachbesserungen aus Abschnitt 6
sitzen, selbst nachgemessen. Kein blockierender Befund.

Worktree `wt/review-design`, Stand `cb27780c`. `E2E_PORT=5190 npm run test:e2e`:
**94 Tests, alle grün**, beide Größen `pc-14zoll` (1440×900) und `pc-monitor` (1920×1080).
Zehn Tests mehr als in Durchgang 2 (84) — die neue `e2e/bearbeiten-leiste.spec.ts` mit
5 Zuständen × 2 Größen. Zur Stabilität siehe den Hinweis unten.

### Die vier Prüfpunkte

**1. Unterscheiden sich die drei Bänder auf einen Blick? Passen amber und red? Kontrast?**

Ja. Im Browser ausgelesene Computed-Styles, nicht geschätzt:

| Band | Hintergrund | Text | Rand | Icon | Kontrast |
| --- | --- | --- | --- | --- | --- |
| Countdown (Warnung) | `rgb(255,251,235)` amber-50 | `rgb(146,64,14)` amber-800 | `rgb(252,211,77)` amber-300 | `Timer` | **6,84 : 1** |
| Verbindung weg (Störung) | `rgb(254,242,242)` red-50 | `rgb(185,28,28)` red-700 | `rgb(252,165,165)` red-300 | `WifiOff` | **5,91 : 1** |
| Nur-Lesen-Hinweis | rose-50 | slate-700 | rose-100 | `Lock` | (unverändert) |

Beide neuen Bänder liegen deutlich über der AA-Schwelle von 4,5 : 1 für Fließtext.
Gelb = „gleich passiert was", Rot = „etwas ist kaputt", Rose = „nur ein Hinweis" — die
Stufung ist jetzt auf einen Blick da und deckt sich mit dem Design-System
(`amber-500` warn, `red-600` danger, rose als Akzent). Keine Fremdpalette, Icons sind
Lucide, kein Emoji, Systemschrift. Der 🟡 aus Abschnitt 6 („drei gleich aussehende
rose-Bänder", „Countdown ohne Icon") ist damit erledigt.

**2. Springt `Fertig` wirklich nicht mehr?**

Nein — und zwar nicht „≤ 5 px", sondern **exakt 0 px**. Gemessene `boundingBox` desselben
Knopfes im selben Ablauf, vor und nach dem Erscheinen des Bandes:

| Größe | ohne Band | mit Countdown | mit Verbindung weg |
| --- | --- | --- | --- |
| pc-14zoll | [1294, 139, 85, 34] | [1294, 139, 85, 34] | [1294, 139, 85, 34] |
| pc-monitor | [1650, 148, 85, 34] | [1650, 148, 85, 34] | [1650, 148, 85, 34] |

Die Ursache ist sauber gelöst: die Bänder rendern vor dem Knopf, der Knopf ist das letzte
Kind und klebt damit an der rechten (festen) Kante des `justify-between`-Blocks. In den
Screenshots `leiste-bearbeiten`, `leiste-countdown` und `leiste-verbindung-weg` liegt der
Knopf sichtbar auf derselben Linie.

**3. Passt der Countdown-Text auf 14 Zoll in eine Zeile?**

Ja. Band [715, 139, 567, 34] auf 14 Zoll. Die Höhe von 34 px ist eine Textzeile (20 px)
plus `py-1.5` (12 px) plus 2 px Rand — kein Umbruch. Bis zum `Fertig`-Knopf (beginnt bei
x 1294) bleiben **12 px** (`gap-3`), der Knopf wird also nicht bedrängt. Auf 1920
identisch: [1071, 148, 567, 34]. Der ganze Leisten-Block misst 664 px in einer Kopfzeile
von ~1340 px — reichlich Luft.

**4. Ist der deaktivierte Knopf als „deaktiviert mit Grund" erkennbar?**

Teils — und der fehlende Teil ist der angekündigte 7d-Stand. Gemessen am echten Knopf im
Modal, beide Größen: `disabled = true`, `cursor: not-allowed`, `opacity: 0.5`, aber
`title = null` und `aria-describedby = null`. Der neue Prop `bearbeitenGesperrtGrund`
wird vom `LieferantDokumentModal` noch nicht übergeben; eine Hover-Probe zeigt
erwartungsgemäß keinen Tooltip. Als *deaktiviert* ist der Knopf klar erkennbar
(halbtransparent + Verbotscursor), und das *Warum* steht heute unmittelbar daneben im
roten Band („Sperre konnte nicht geholt werden — bitte neu laden.") plus im Toast. Die
Komponente selbst ist richtig gebaut (`title`, `aria-describedby`, `sr-only`-Span, kein
leeres `title=""`). Kein Befund, der Browser-Beleg kommt mit 7d.

**5. Tab-Hinweis in Sie-Form**

Wording sitzt: „Dokument gespeichert und freigegeben — Sie können diesen Tab jetzt
schließen." Über die Route ist die Seite weiterhin nicht erreichbar, deshalb mit dem
echten Markup und demselben Stylesheet in der laufenden App aufgenommen. Gemessen:
Absatz [528, 466, 384, 48] auf 14 Zoll und [768, 556, 384, 48] auf 1920 — `max-w-sm`
(384 px), 16 px Schrift, 24 px Zeilenhöhe, **zwei Zeilen**, mittig, Systemschriftstack
(`system-ui, -apple-system, sans-serif`), kein Webfont. Rose-100-Kreis mit rose-600
`CheckCircle2` darüber, ruhige slate-50-Fläche. Einziger Wermutstropfen: der Umbruch
fällt hinter „— Sie", das „Sie" hängt allein am Zeilenende. → 🟡

### Angeschaute Screenshots

Alle 24 aus `react-pc-frontend/test-results/design/`, jeweils `--pc-14zoll` und
`--pc-monitor`:

1. `leiste-bearbeiten` · 2. `leiste-lesen` · 3. `leiste-countdown` ·
4. `leiste-verbindung-weg` · 5. `leiste-deaktiviert` (alle fünf neu in diesem Abschnitt)
6. `lieferant-modal-bearbeiten` · 7. `lieferant-modal-gesperrt` ·
8. `lieferant-modal-fremdes-lock` · 9. `lieferant-modal-fehler` ·
10. `lieferant-modal-speicherfehler-toast`
11. `dokument-editor-vor-schliessen` · 12. `dokument-editor-ungespeichert-warnung`

Dazu 4 eigene Aufnahmen (Wegwerf-Spec, nach dem Lauf gelöscht, Worktree ist sauber):
`tab-schliessen-hinweis` und `deaktiviert-hover`, je `--pc-14zoll` und `--pc-monitor`.

### Die sechs Fragen je Zustand

**`leiste-bearbeiten` (14 Zoll + Monitor)** — freies Lock, keine Bänder
1. *Farben?* `Fertig` weißer Outline-Knopf mit rose-Rand, `Speichern` die einzige gefüllte
   rosa Fläche. Genau eine Primäraktion.
2. *Design-System?* Ja. rose/slate, Lucide, kein Emoji, Systemschrift, `rounded-lg`.
3. *Look-and-Feel?* Ruhig. Linke Bandhälfte leer — 7d verdrahtet dort „Sie lesen nur mit."
   nicht, der Zustand ist ohnehin der Bearbeiten-Modus, in dem kein Hinweis hingehört.
4. *UX?* Nach 7b öffnet das Modal direkt im Bearbeiten-Modus: Formular frei, `Fertig`
   sichtbar. Ein Schritt weniger als vorher, das ist eine echte Verbesserung.
5. *Auffindbar?* Ja, `Fertig` am Kopf des Datensatzes, ohne Scrollen, beide Größen.
6. *Überschneidung?* Nein, kein waagerechtes Scrollen.

**`leiste-lesen` (14 Zoll + Monitor)** — nach Klick auf `Fertig`
1. *Farben?* `Bearbeiten` rose-600 gefüllt und aktiv, `Speichern` rose-600 bei 50 %
   (deaktiviert). Weiterhin zwei rosa Flächen, eine davon blass — bekannt aus Abschnitt 6,
   in 7c nicht angefasst, nicht verschlechtert.
2. *Design-System?* Ja.
3. *Look-and-Feel?* Ruhig; linke Bandhälfte noch leer (7d).
4. *UX?* Der Wechsel lesen/bearbeiten ist am Knopf klar ablesbar.
5. *Auffindbar?* Ja, ohne Scrollen.
6. *Überschneidung?* Nein.

**`leiste-countdown` (14 Zoll + Monitor)**
1. *Farben?* Amber-Band mit `Timer`, Kontrast 6,84 : 1 — als Warnung lesbar und klar vom
   rosa Hinweis und vom roten Störungsband getrennt.
2. *Design-System?* Ja, `amber` ist die dokumentierte Warnfarbe.
3. *Look-and-Feel?* Das Band füllt die Leiste sinnvoll, auf 1920 wirkt nichts verloren.
4. *UX?* „Wird in 59 Sekunden freigegeben — bewegen Sie die Maus, um weiterzuarbeiten."
   Sagt was passiert und was man dagegen tut. Handwerker-Sprache.
5. *Auffindbar?* Ja, am Kopf, ohne Scrollen, in beiden Größen vollständig sichtbar.
6. *Überschneidung?* Nein. Einzeilig, 12 px Abstand zum Knopf, Knopf bleibt exakt stehen.

**`leiste-verbindung-weg` (14 Zoll + Monitor)**
1. *Farben?* Rotes Band mit `WifiOff`, Kontrast 5,91 : 1, halbfett — dringlicher als der
   Countdown, genau die richtige Reihenfolge.
2. *Design-System?* Ja, `red` ist die Danger-Semantik.
3. *Look-and-Feel?* Ruhig, in beiden Größen gleich.
4. *UX?* „Verbindung weg — Ihre Änderungen sind noch nicht gespeichert." Konkret, ohne
   Fachchinesisch.
5. *Auffindbar?* Ja.
6. *Überschneidung?* Nein, Knopf steht exakt still.

**`leiste-deaktiviert` (14 Zoll + Monitor)** — Erwerb scheitert (500)
1. *Farben?* Rotes Band mit `AlertTriangle` im Modal, `Bearbeiten` rose-600 bei 50 %,
   Toast oben rechts. Zustand klar als Störung lesbar.
2. *Design-System?* Ja.
3. *Look-and-Feel?* Das Band nimmt die volle freie Breite, wirkt nicht hohl.
4. *UX?* Siehe Prüfpunkt 4 — deaktiviert erkennbar, Grund daneben lesbar, Tooltip kommt 7d.
5. *Auffindbar?* Ja.
6. *Überschneidung?* Nein — Toast oben rechts, Fußleiste frei (der Fix aus Durchgang 2
   hält auch hier).

**`lieferant-modal-*` (je 14 Zoll + Monitor)**
1.–6. Gegenüber Durchgang 2 unverändert; die Dateien sind größengleich bis auf wenige
   hundert Byte. `bearbeiten` zeigt jetzt (7b) direkt den Bearbeiten-Modus,
   `fremdes-lock` bleibt der stärkste Zustand (rose-50-Band, Name fett, `Bearbeiten`
   aktiv), `fehler` und `speicherfehler-toast` zeigen weiterhin Toast oben rechts bei
   freier Fußleiste. Keine Überschneidung, kein waagerechtes Scrollen, nichts
   abgeschnitten außer der bekannten Scroll-Kante des Formulars (7c-Restpunkt).

**`dokument-editor-*` (je 14 Zoll + Monitor)**
1.–6. Unverändert gut: Vorschau-Spalte ausgelaufen bei 45 % mit Skelett-Muster, genau eine
   rosa Primäraktion (`PDF`), Warn-Dialog mit amber-Icon und rose-600-Primärknopf,
   keine Überschneidung. `Nicht speichern` bricht weiter auf zwei Zeilen um (7c-Restpunkt).

### 💡 Hinweise (blockieren nicht)

- **Umbruch im Tab-Hinweis.** Bei `max-w-sm` (384 px) fällt der Zeilenumbruch hinter
  „— Sie", das „Sie" steht allein am Ende der ersten Zeile. Ein `text-balance` oder etwas
  mehr Breite würde nach dem Gedankenstrich trennen und den Satz ruhiger machen.
- **E2E-Flattern auf kaltem Dev-Server.** Der vorgeschriebene komplette Lauf war 94/94 grün.
  Fährt man dagegen nur die drei Sperr-Specs gegen einen **frisch gestarteten** Vite-Server,
  fallen 4 pc-14zoll-Tests aus `bearbeiten-leiste.spec.ts` mit
  `expect(locator).toBeVisible()` — dieselben Tests brauchten dort 19–21 s statt der
  5–6 s bei warmem Server. Zweiter Lauf derselben drei Specs: 24/24 grün, die Spec allein:
  10/10 grün. Kein Produktfehler, aber die neue Spec ist die, die beim ersten Zugriff auf
  einen kalten Server anschlägt; in CI würde sie flattern. Ein großzügigeres Warten beim
  ersten Öffnen (oder `waitForLoadState`) im Hilfsschritt würde das abfangen.
- Beobachtung, kein Befund: Verbindung-weg-Band und Lock-Fehler-Band im Modal sehen jetzt
  fast gleich aus (beide red-50/red-300/red-700, gleiche Stelle). Sie treten nie
  gleichzeitig auf, sind beide echte Störungen und tragen verschiedene Icons
  (`WifiOff` / `AlertTriangle`) — die gemeinsame Stufe ist richtig.
- Aus Abschnitt 6 offen und unverändert (7d bzw. Restpunkte): kein Tooltip und kein
  „Sie lesen nur mit." im echten Modal, zwei rosa Knöpfe im Lesen-Modus, `Nicht speichern`
  zweizeilig, PDF-Spalte frisst auf 14 Zoll zwei Drittel des Modals, Scroll-Kante im
  Formular. **Nichts davon hat sich verschlechtert.**

## Abschnitt 7-1 — Code-Review (Code-Reviewer)

**Ampel: 🔴** — ein blockierender Befund: der 🔴 aus Review 5 („`modus === 'bearbeiten'` ohne
gehaltenes Lock") ist über einen zweiten Pfad wieder erreichbar. Alles andere ist sauber.
Stand `d7ba4a6e`.

### 🛑 Blockierend

**Verliert der Nutzer die Sperre während des Bearbeitens, bleibt das Formular editierbar.**
`react-pc-frontend/src/components/lock/useDatensatzLock.ts:171-190` (409-Zweig in `heartbeat`)

Der 409-Zweig des Heartbeats setzt `status='locked-by-other'`, `heldRef=false` und stoppt den
Heartbeat — **`modus` bleibt aber unangetastet**. Seit 7b steht `modus` nach einem erfolgreichen
Mount-Acquire automatisch auf `'bearbeiten'`. Damit ist die verbotene Kombination erreichbar.

Selbst gemessen (Wegwerf-Probe am Hook, Mount-Acquire 200, danach Heartbeat 409):

```
PROBE vor  HB: status=acquired        modus=bearbeiten
PROBE nach HB: status=locked-by-other modus=bearbeiten  halter=Anna Beispiel
PROBE => formGesperrt waere: false
```

Folge im einzigen heutigen Verbraucher: `LieferantDokumentModal.tsx:90` leitet
`formGesperrt = lock.modus !== "bearbeiten"` ab. Alle ~20 Felder bleiben also aktiv und der
Speichern-Knopf freigeschaltet, während direkt darüber der `GesperrtHinweis` meldet, dass ein
Kollege den Datensatz bearbeitet. Die `BearbeitenLeiste` zeigt dabei „Fertig" statt „Bearbeiten" —
die Oberfläche widerspricht sich selbst.

Wie kommt es dazu? Der Server gibt eine verwaiste Sperre nach 90 s an den Nächsten weiter
(`DatensatzLockService.STALE_AFTER`). Ein gedrosselter Hintergrund-Tab, ein kurzer Standby oder eine
Netzpause reicht, damit der 30-Sekunden-Heartbeat das Fenster reißt; der nächste Heartbeat bekommt
dann 409.

Kein stilles Überschreiben: `LieferantDokumentController:99` prüft `isHeldBy(EINGANG, …)` und weist
den PUT ab. Der Schaden ist also „getippte Arbeit ist weg und muss neu gemacht werden" plus eine
widersprüchliche Oberfläche — kein Datenverlust in der Datenbank.

Fairerweise: der Pfad ist nicht neu von 7b **erzeugt**. Schon vorher setzte der 6b-Behelf im Modal
denselben Modus, und der 409-Zweig setzte ihn auch damals nicht zurück. 7b hat die Zuständigkeit für
`modus` aber bewusst in den Hook geholt und die Semantik neu entschieden — das ist die Stelle und der
Moment, das zu schließen.

Nachweisbar sein muss: nach einem Heartbeat-409 ist `modus === 'lesen'` (und damit das Formular
gesperrt), während `kannBearbeiten` true bleibt, damit die Übernahme per Klick möglich ist. Der
vorhandene Test in `useDatensatzLock.test.tsx:322` prüft genau diesen Fall — aber nur `status`,
`kannBearbeiten` und `halterName`, **nicht `modus`**. Genau durch diese Lücke ist es gerutscht.

### Mutationsproben (Quellstand danach byte-identisch, `git diff d7ba4a6e` leer)

1. **7b Punkt 2** — `setModus('bearbeiten')` im Acquire-Erfolg entfernt: **17 Tests rot**
   (6 im Hook, 11 von 14 im Modal) — deckt sich mit der Meldung des Agenten. Darunter
   „wechselt nach einem erfolgreichen Mount-Acquire automatisch in den Modus \"bearbeiten\"" und
   „ruft die Acquire-Route nur einmal auf".
2. **7b Punkt 1** — beide zweiten Generationsprüfungen (nach `res.json()`, in `acquire` **und**
   `heartbeat`) entfernt: **genau 2 Tests rot**, beide mit
   `AssertionError: expected 'locked-by-other' to be 'idle'`. Saubere Isolation.
3. **7c `bearbeitenGesperrtGrund`** — `title={grund}` am Bearbeiten-Knopf entfernt: **genau 1 Test
   rot** („trägt title UND aria-describedby, wenn der Knopf deaktiviert ist und ein Grund gesetzt
   wurde"), die übrigen 24 blieben grün.

### Selbst gemessene Zahlen

| | Baseline (Abschnitt 6) | jetzt |
|---|---|---|
| Backend | 2462 / 0 F / 4 E | **2462 / 0 Failures / 4 Errors** (nach Einzel-Nachlauf) |
| Frontend Testdateien | 87 | **87** |
| Frontend Tests | 1033 | **1049** (+16) |
| Lint | 0 Fehler, 1 Warnung | **0 Fehler, 1 Warnung** |

- Backend ist unberührt; die 4 Errors sind namentlich die bekannten umgebungsbedingten.
  Im Volllauf kam **ein** zusätzlicher Fehlschlag dazu:
  `UnifiedEmailControllerExtractEmailTest.adversarialInputWithoutAt_isLinear` — ein Test mit
  500-ms-Zeitschranke in einer von diesem Abschnitt nicht angefassten Datei. Einzeln nachgefahren:
  **12/12 grün**. Lastartefakt (der Design-Reviewer fährt parallel Dev-Server und Browser),
  kein Befund.
- Frontend-Volllauf: ein Fehlschlag in `document-editor/index.test.tsx` („speichert die
  Materialauswahl als SERVICE-Block") — ebenfalls nicht angefasst. Isoliert **2x 19/19 grün**.
  Auch Last, kein Befund.
- Testzahlen der vier Dateien selbst nachgezählt und mit den Task-Meldungen abgeglichen:
  Hook **33** + Modal **14** = **47** (7b meldete 47 ✓), Leiste **25** + Hinweis **7** = **32**
  (7c meldete 32 ✓).
- Lint-Warnung ist die vorbestehende `BelegeKasseEditor.tsx:1204`.
- `npm run build` grün, `src/main/resources/static/` zurückgesetzt.

### 💡 Hinweise (blockieren nicht)

- `useDatensatzLock.test.tsx:322` — der Heartbeat-409-Test prüft `status`, `kannBearbeiten` und
  `halterName`, aber nicht `modus`. Eine Zusicherung dort hätte den 🔴 oben verhindert; sie gehört
  zusammen mit dem Fix dazu.
- `BearbeitenLeiste.tsx` — der Klassenkommentar sagt „der Knopf ist immer das letzte Kind". Sobald
  `bearbeitenGesperrtGrund` greift, folgt ihm noch das `sr-only`-`<span>` für `aria-describedby`.
  Ohne Layout-Wirkung (Tailwinds `sr-only` ist absolut positioniert und damit aus dem Flex-Fluss),
  aber der Kommentar stimmt so nicht mehr ganz.
- Die beiden neuen Props werden vom einzigen Verbraucher noch nicht gesetzt — laut Auftrag bewusst
  (Task 7d). Geprüft und in Ordnung: beide sind optional, `zeigeNurLesenHinweis` hat den Default
  `false`, `bearbeitenGesperrtGrund` wird nur bei `!kannBearbeiten` und nach `trim()` ausgewertet
  (kein leeres `title=""`). Die DOM-Umordnung bricht keinen Verbraucher — die Leiste wird heute nur
  vom Lieferant-Modal und vom Hook-Testharness gerendert, beide grün.

### Sonst geprüft, ohne Befund

- **Generationsprüfung (7b Punkt 1):** in beiden 409-Zweigen jetzt zweimal — einmal nach dem
  `fetch`-await, einmal nach `res.json()`. Das ist die richtige Stelle: `json()` ist selbst ein
  zweiter Await-Punkt. Beide durch je einen eigenen Test abgedeckt, Mutationsprobe beißt.
- **6b-Behelf im Modal entfernt:** der Effekt ist raus, der Hook macht es selbst; der neue Test
  „ruft die Acquire-Route nur einmal auf" sichert ab, dass daraus kein zweiter Request wird.
- **Kein neuer Pfad zu `modus='bearbeiten'` ohne Lock über `onBearbeiten`:** dessen erster Zweig
  setzt nur bei `lockUrl == null` (nichts zu sperren) oder `heldRef.current === true`. Der
  Retry-Zweig überlässt das Umschalten jetzt vollständig `acquire()`, das nur im Erfolgsfall
  umschaltet. Beide sauber.
- **Datenschutz:** nur Dummy-Namen (Anna Büro, Anna Beispiel, Erika Musterfrau, Ueberholt Beispiel),
  keine E-Mail-Adressen, keine Secrets im Abschnitt-Diff.
- **Diff-Hygiene:** 10 Dateien, kein Backend, kein `test-results/`, kein Build-Output.
- **Performance:** kein neues Polling, kein zweiter Timer. `BearbeitenLeiste` bleibt eine reine
  Darstellungskomponente ohne State; `useId()` ist vernachlässigbar.

## Abschnitt 7-1 — Task 7c Nachtrag (Coding-Agent)

Zeit: 2026-09-04T23:28:00Z
Branch: lock/task-7c-bearbeiten-leiste
Commit(s): 6858c0d1
Status: fertig

Was gemacht wurde:
- Nachtrag aus dem Code-Review (🟡): Klassen-Kommentar in BearbeitenLeiste.tsx behauptete
  "Knopf ist immer das letzte Kind" — stimmt nicht mehr, sobald bearbeitenGesperrtGrund
  greift, weil dann der unsichtbare sr-only-Span für aria-describedby dem Knopf folgt.
  Kommentar korrigiert: Bänder stehen vor dem Knopf, nach dem Knopf folgt höchstens der
  unsichtbare Beschreibungs-Span — nichts, das Platz einnimmt bzw. für die Breite des Blocks
  zählt.
- Die beiden bestehenden "letztes Kind"-Tests bleiben unverändert korrekt (sie prüfen den
  Modus 'bearbeiten', dort greift kein Grund, also existiert der Span dort nie). Zur Deckung
  der eigentlich gemeldeten Lücke einen neuen Test ergänzt: mit gesetztem
  bearbeitenGesperrtGrund folgt nach dem Knopf genau ein Element, ein SPAN mit sr-only-Klasse.
  Mutationsprobe: sr-only-Klasse testweise entfernt → neuer Test wird rot
  (`expected '' to contain 'sr-only'`) — zurückgesetzt, wieder grün.
- `npx vitest run src/components/lock/BearbeitenLeiste.test.tsx` → 26/26 grün.
- `npm run lint` → 0 Fehler, genau die 1 vorbestehende Warnung (BelegeKasseEditor.tsx:1204).

Bedenken / Abweichungen vom Plan:
- keine.

## Abschnitt 7-1 — Task 7b Nachbesserung 1 (Coding-Agent)

**Zeit:** 04.09.2026, ca. 23:15–23:36 Uhr
**Branch:** `lock/task-7b-hook` (Ausgangspunkt `68c3d951`)
**Commit:** `6c7c39df` — fix(sperr-hook): Bearbeiten-Modus ohne gehaltenes Lock nach Heartbeat-409 behoben
**Status:** ✅ fertig, alle Gates grün (nur eigene Testdateien, wie vorgegeben).

### Der Befund

Der 409-Zweig im Heartbeat (`useDatensatzLock.ts`, damals Z. 171–190) setzte `status='locked-by-other'`
und `heldRef.current=false`, liess `modus` aber unangetastet. Seit Task 7b Punkt 2 schaltet `acquire()`
nach jedem erfolgreichen Erwerb `modus` auf `'bearbeiten'` — dadurch war
`status='locked-by-other'` bei gleichzeitig `modus='bearbeiten'` erreichbar: genau die seit
Review 5 verbotene Kombination „Bearbeiten-Modus ohne gehaltenes Lock". Im Lieferant-Modal
(`formGesperrt = modus !== 'bearbeiten'`) blieben dabei alle Felder aktiv und Speichern
freigeschaltet, während `GesperrtHinweis` einen anderen Halter meldete. Auslöser in der Praxis:
gedrosselter Hintergrund-Tab reisst das 90-Sekunden-Fenster, ein Kollege übernimmt, der nächste
Heartbeat bekommt 409.

### Roter Test zuerst

Bestehenden Test um `:322` (`ein Heartbeat mit 409 wechselt in "locked-by-other"...`) um eine
`modus`-Zusicherung ergänzt — genau die Lücke, durch die der Befund rutschte (der Test prüfte
bis dahin nur `status`/`kannBearbeiten`/`halterName`). Rote Meldung gegen den unveränderten
Stand von `68c3d951`:

```
AssertionError: expected 'bearbeiten' to be 'lesen'
Expected: "lesen"
Received: "bearbeiten"
```

### Fix

`setModus('lesen')` im 409-Zweig von `heartbeat()`, direkt neben `heldRef.current = false`.

### Systematische Prüfung der übrigen Übergänge

Alle Stellen im Hook durchgegangen, an denen `heldRef.current` auf `false` gesetzt wird:

| Stelle | Ergebnis |
| --- | --- |
| `heartbeat()`, 409-Zweig | **Bug — behoben** (siehe oben). |
| `acquire()`, 409-Zweig (Retry scheitert erneut mit Fremdsperre) | Kein beobachtbarer Fehler heute (`acquire()` wird nur aufgerufen, wenn `modus` bereits `'lesen'` ist — bestehender Test „onBearbeiten() nach freigeben() bleibt im Modus 'lesen', wenn der erneute Versuch mit 409 scheitert" deckt das ab), aber **defensiv `setModus('lesen')` ergänzt**, damit die Invariante an der Stelle selbst gilt statt sich auf die Aufrufer-Reihenfolge zu verlassen. |
| `acquire()`, `!res.ok`-Zweig (z.B. 500 beim Retry) | Gleiche Einschätzung — **defensiv ergänzt**, mitgetestet im neuen Kettentest (letzter Schritt: erneuter Retry scheitert mit 500). |
| `acquire()`, `catch`-Zweig (Netzfehler, kein AbortError) | Gleiche Einschätzung — **defensiv ergänzt**. Kein dedizierter Test dafür ergänzt (Zeitgründe); durch die Invariante der anderen Zweige mitabgesichert, echte Regressionsgefahr gering. |
| `heartbeat()`, `!res.ok`-Zweig (Netzfehler ohne 409, auch nach mehreren Fehlschlägen) | **Kein Fund** — dieser Zweig ändert `heldRef` gar nicht, das Lock gilt bis zu einem 409 oder einer aktiven Freigabe als weiter gehalten. Bestehenden Test „zwei aufeinanderfolgende fehlgeschlagene Heartbeats..." um `status`/`modus`-Zusicherung erweitert, um das explizit festzuhalten statt es nur zu vermuten. |
| `aktivFreigeben()` (freigeben() während ein Acquire läuft) | **Kein Fund** — setzt `status`/`modus`/`halter` bereits unconditional gemeinsam zurück. Der interessante Fall ist der UMGEKEHRTE Race: ein überholter, noch laufender Acquire, der NACH einem `freigeben()` mit Erfolg (200) zurückkommt — abgesichert durch die (aus einer früheren Nachbesserung bestehende) Generationsprüfung direkt nach dem `fetch()`-await in `acquire()`. Dafür neuen Test geschrieben (siehe unten), der das erstmals über `modus`/`status` statt nur über den Request-Zähler prüft. |
| `releaseKeepalive()` (Unmount/`pagehide`) | Bewusst **nicht angefasst** — fire-and-forget für die Seiten-Teardown, ändert `status`/`modus` absichtlich nicht (siehe Docstring dort). Die vom Reviewer vorgeschlagene Invariante („nie `modus==='bearbeiten'` bei `status!=='acquired'`") bleibt hier ohnehin gewahrt, weil `status` ebenfalls unverändert `'acquired'` bleibt — kein Widerspruch zwischen den beiden exponierten Werten, nur der interne `heldRef` läuft (absichtlich) auseinander. Exotischer Randfall (bfcache-Restore nach `pagehide`), nicht vom Reviewer benannt — als Beobachtung hier vermerkt, kein eigener Task daraus gemacht. |

### Neue Tests

1. **Erweiterung des Heartbeat-409-Tests** um `expect(result.current.modus).toBe('lesen')` — der eigentliche rote Test.
2. **Kettentest** „gilt ueber eine ganze Kette von Uebergaengen hinweg: Mount-Erfolg -> Heartbeat-409 -> Retry-Erfolg -> Fertig -> erneuter Acquire-Fehler" mit einem `pruefeInvariante()`-Helfer, der nach jedem Schritt sicherstellt, dass `modus==='bearbeiten'` nur bei `status==='acquired'` vorkommt (die von der Nutzerseite vorgeschlagene generelle Invariante). Deckt dabei auch den defensiven `!res.ok`-Zweig in `acquire()` ab (letzter Schritt: 500 beim erneuten Retry).
3. **Test für „freigeben() während ein Retry-Acquire noch läuft"**: ein hängender Retry-Acquire wird durch ein zwischenzeitliches `freigeben()` überholt; ein danach eintreffender Erfolg (200) darf `modus` nicht mehr auf `'bearbeiten'` setzen. Bestätigt, dass die bestehende Generationsprüfung (aus einer früheren Nachbesserung) diesen Fall bereits ohne Codeänderung abdeckt.
4. **Erweiterung** des Tests „zwei aufeinanderfolgende fehlgeschlagene Heartbeats..." um `status`/`modus`-Zusicherungen (bestätigt: reine Netzfehler ohne 409 lassen das Lock unangetastet).

### Mutationsprobe

`setModus('lesen')` im Heartbeat-409-Zweig wieder entfernt → **genau 2 Tests rot**, alle anderen
33 bleiben grün:
- der erweiterte Heartbeat-409-Test: `AssertionError: expected 'bearbeiten' to be 'lesen'`
- der neue Kettentest: `AssertionError: expected 'bearbeiten' not to be 'bearbeiten'` (an der Stelle
  direkt nach dem Heartbeat-409-Schritt)

Fix danach wiederhergestellt, Quellstand per `git diff --stat` gegen den committeten Stand
geprüft (identisch). Die drei defensiven `setModus('lesen')`-Ergänzungen in `acquire()` wurden
NICHT einzeln mutiert (keine der drei ist bei gegebenem Aufrufer-Gefüge heute beobachtbar
scharf) — das ist im Bericht oben transparent vermerkt, nicht verschwiegen.

### Gates (nur eigene Dateien, wie vorgegeben)

- `npx vitest run src/components/lock/useDatensatzLock.test.tsx` → **35/35 grün**.
- `npx vitest run src/components/LieferantDokumentModal.test.tsx` → **14/14 grün**, unverändert.
- `npm run lint` → **0 Fehler, genau die 1 vorbestehende Warnung** (`BelegeKasseEditor.tsx:1204`).
- `npm run build` → grün, `src/main/resources/static/` danach zurückgesetzt.
- Playwright nicht erneut gefahren — diese Nachbesserung ändert ausschliesslich
  `useDatensatzLock.ts`/-test, keine Modal-/UI-Datei; die 6b/7b-Spec bleibt durch die
  unveränderten, weiter grünen `LieferantDokumentModal.test.tsx` mittelbar abgedeckt.

### Bedenken / Abweichungen vom Plan

Keine Abweichung vom Auftrag. Eigene Entscheidungen, transparent gemacht:
- Die drei defensiven `setModus('lesen')`-Ergänzungen in `acquire()` sind nach heutigem
  Aufrufer-Gefüge nicht scharf beobachtbar (dort ist `modus` bereits `'lesen'`, bevor `acquire()`
  überhaupt aufgerufen wird) — trotzdem ergänzt, weil der Auftrag ausdrücklich verlangt, dass die
  Invariante „heldRef=false ⇒ modus='lesen'" an JEDER Stelle selbst gilt, nicht nur aus der
  Aufruf-Reihenfolge folgt. Keine eigene Mutationsprobe je Zeile, da nicht scharf testbar ohne
  einen Aufrufer-Pfad zu erfinden, den es heute nicht gibt.
- `releaseKeepalive()` bewusst nicht angefasst (siehe Tabelle oben) — ausserhalb dessen, was der
  Reviewer als Übergänge benannt hat, und die genannte Invariante bleibt dort ohnehin gewahrt.
  Falls das dennoch geschlossen werden soll, wäre das ein eigener, expliziter Auftrag.

## Abschnitt 7-1 — Code-Review 2 (Code-Reviewer)

**Ampel: 🟡** — der 🔴 ist behoben und selbst nachgestellt. Von meiner Seite abgenommen.
Stand `5f2f48be`.

### 🔴 aus Durchgang 1: **behoben**

*„Verliert der Nutzer die Sperre während des Bearbeitens, bleibt das Formular editierbar."*

Selbst nachgestellt (Wegwerf-Probe am Hook, Mount-Acquire 200, danach Heartbeat 409):

```
vor  HB: status=acquired         modus=bearbeiten
nach HB: status=locked-by-other  modus=lesen  kannBearbeiten=true  halter=Anna Beispiel
```

Genau die geforderte Kombination: Formular gesperrt (`modus='lesen'` ⇒ `formGesperrt=true` im
Modal), Übernahme weiterhin möglich (`kannBearbeiten=true`), Halter benannt.

**Mutationsprobe:** `setModus('lesen')` im 409-Zweig von `heartbeat()` entfernt ⇒ **genau 2 von 35**
Tests rot, deckt sich mit der Meldung des Agenten:
- `ein Heartbeat mit 409 wechselt in "locked-by-other" …` → `AssertionError: expected 'bearbeiten' to be 'lesen'`
- `gilt ueber eine ganze Kette von Uebergaengen hinweg …` → `AssertionError: expected 'bearbeiten' not to be 'bearbeiten'`

Beide neuen Zusicherungen beißen also wirklich — die geschlossene Lücke im alten Test **und** der
Kettentest.

### Zu den drei Rückfragen

**1. Deckt der Invariantentest wirklich alle Wechsel ab?** Nein — `pruefeInvariante` ist ein
Hilfsfunktion, die man aufrufen muss, keine automatische Invariante. Sie wird **5x** aufgerufen,
alle innerhalb des einen Kettentests, also genau an den fünf Stationen dieser Kette
(Mount-Erfolg → Heartbeat-409 → Retry-Erfolg → Fertig → Acquire-Fehler). Für die realistischen
Übergänge reicht das zusammen mit dem korrigierten Heartbeat-Test und den beiden
Acquire-Fehlerzweigen; „über alle Wechsel" ist es aber nicht, und wer später einen neuen Zweig
ergänzt, wird von ihr nicht automatisch erwischt.

**2. Bleibt ein Übergang, bei dem `heldRef` fällt und `modus` nicht folgt?** Ja, einer:
`releaseKeepalive()` (`useDatensatzLock.ts:325-341`). Gemessen — nach einem `pagehide` steht:

```
vor  pagehide: status=acquired  modus=bearbeiten
nach pagehide: status=acquired  modus=bearbeiten     (heldRef ist jetzt false, DELETE ist raus)
```

Auf dem **Cleanup-Pfad** ist das folgenlos: dort setzen die unmittelbar folgenden Zeilen
`setStatus('idle')` und `setModus('lesen')`, die Invariante ist im selben Durchlauf wieder
hergestellt. Auf dem **`pagehide`-Pfad** bleibt der Zustand dagegen inkonsistent stehen. Das ist
egal, solange die Seite wirklich verschwindet — nicht aber beim **bfcache**: kommt der Nutzer per
Zurück-Knopf zurück, wird der eingefrorene JS-Heap samt React-State wiederhergestellt, der Hook
hat keinen `pageshow`-Handler, der Heartbeat ist gestoppt und das Lock serverseitig freigegeben.
Der Nutzer sähe ein bearbeitbares Formular ohne Sperre; ein Speichern liefe in die
`isHeldBy`-Prüfung von `LieferantDokumentController:99` und würde abgewiesen.

Die Begründung im Log („fire-and-forget, `status` ändert sich dort auch nicht") trifft damit den
Cleanup-Pfad, nicht aber den `pagehide`-Pfad. Schmal, unverändert gegenüber vorher und ohne
Datenverlust in der Datenbank — deshalb 🟡 und kein 🔴 (siehe Hinweise).

**3. Können die defensiven `setModus('lesen')` in `acquire()` einen noch gültigen Retry
herauswerfen?** Nein. `acquire()` wird nur an zwei Stellen betreten, und an beiden ist `heldRef`
bereits false: `onBearbeiten()` steigt vorher aus (`if (lockUrl == null || heldRef.current) { … return; }`),
und der Mount-Effekt läuft erst, nachdem der Cleanup des vorherigen Durchlaufs
`releaseKeepalive()` gerufen und `heldRef` auf false gesetzt hat. Es gibt also keinen Zustand, in
dem ein fehlschlagendes Acquire ein noch gültiges Lock verwirft — die Semantik-Annahme aus dem
Auftrag stimmt. Schritt 5 des Kettentests (Retry-Acquire mit 500) deckt den Fall zusätzlich ab.

### Selbst gemessene Zahlen

| | Durchgang 1 | jetzt |
|---|---|---|
| Frontend Testdateien | 87 | **87** |
| Frontend Tests | 1049 | **1052** (+3) |
| Lint | 0 Fehler, 1 Warnung | **0 Fehler, 1 Warnung** |
| Backend (Stichprobe) | — | **DatensatzLockControllerTest 9/9 grün** |

- Frontend-Volllauf **komplett grün, kein einziger Flake** (in Durchgang 1 waren es zwei
  lastbedingte Ausfälle).
- +3 Tests: `6c7c39df` bringt den Kettentest und den Test „freigeben() während ein Retry-Acquire
  noch läuft", `6858c0d1` einen Test zur Knopf-Reihenfolge. Der korrigierte Heartbeat-409-Test ist
  kein neuer, sondern der um die `modus`-Zusicherung erweiterte alte.
- Hook-Datei jetzt 35 Tests (vorher 33).
- Lint-Warnung ist die vorbestehende `BelegeKasseEditor.tsx:1204`.
- `npm run build` grün, `src/main/resources/static/` zurückgesetzt. Code byte-identisch zu
  `5f2f48be`.
- Die beiden Nutzer-Änderungen in `.claude/skills/` habe ich wie angewiesen nicht angefasst.

### 💡 Hinweise (blockieren nicht)

- `useDatensatzLock.ts:325-341` (`releaseKeepalive`) — setzt `heldRef=false`, ohne `modus`/`status`
  nachzuziehen. Auf dem Cleanup-Pfad folgenlos, auf dem `pagehide`-Pfad überlebt der Zustand eine
  bfcache-Rückkehr (siehe oben). Zwei Zeilen `setStatus('idle'); setModus('lesen');` dort würden die
  Invariante lückenlos machen; alternativ die Notiz im Log um den bfcache-Fall ergänzen, damit die
  Beobachtung nicht als „geprüft und unkritisch" gelesen wird, obwohl nur der Cleanup-Pfad
  betrachtet wurde.
- `useDatensatzLock.test.tsx:734` — `pruefeInvariante` ist ein manuell aufgerufener Helfer (5
  Aufrufe, alle im selben Kettentest), keine automatische Invariante. Wer künftig einen neuen
  Zweig ergänzt, der `heldRef` fallen lässt, wird davon nicht automatisch erwischt.

### Sonst geprüft, ohne Befund

- Die vier Stellen, an denen `heldRef` in `acquire()`/`heartbeat()` auf false geht, setzen jetzt
  alle selbst `setModus('lesen')` — die Invariante gilt lokal, nicht nur aus der Aufruferreihenfolge.
- `heartbeat()` im `!res.ok`-Zweig fasst `heldRef` bewusst nicht an (nur `verbindungWeg`-Zähler);
  der neue Test sichert ausdrücklich ab, dass `status`/`modus` dort unverändert bleiben. Richtig so:
  ein Netz-Hiccup ist kein Lock-Verlust.
- `aktivFreigeben()` und der Effekt-Cleanup setzen weiterhin beides zurück.
- Der 7c-Nachtrag `6858c0d1` berichtigt genau den Kommentar, den ich in Durchgang 1 als ungenau
  gemeldet hatte, und belegt die Reihenfolge zusätzlich mit einem Test.
- Datenschutz: nur Dummy-Namen (Anna Beispiel, Petra Beispiel). Keine Secrets, kein Build-Output,
  kein `test-results/` im Diff.

## Abschnitt 7-2 — Task 7d (Coding-Agent)

**Zeit:** 2026-09-05, ca. 00:00–00:05 Uhr
**Branch:** `lock/task-7d-modal-props` (Worktree `wt/task-7d`)
**Commit(s):** `b392494e` — fix(lieferant-modal): BearbeitenLeiste-Props bearbeitenGesperrtGrund/zeigeNurLesenHinweis verdrahten
**Status:** 🟢 fertig, alle Gates grün

### Was gemacht wurde

Task 7c hatte `BearbeitenLeiste` zwei Props gegeben (`bearbeitenGesperrtGrund`,
`zeigeNurLesenHinweis`), durfte `LieferantDokumentModal` aber nicht anfassen
(7b arbeitete parallel darin). Dieser Task verdrahtet beide Props im Modal.

**Schritt 1 — `bearbeitenGesperrtGrund`:**
- Roter Test vorher (3 Assertions rot, Rest der Datei grün):
  - `Waehrend des Sperren-Abrufs (Zustand "loading")`: `expect(bearbeiten).toHaveAttribute('title', 'Sperre wird gerade geholt …')` → `Expected ... Received: null`
  - `Fehler beim Sperren (Zustand "error")` (neuer Test „traegt denselben Wortlaut wie das rote Fehlerband…"): `expect(bearbeiten).toHaveAttribute('title', 'Sperre konnte nicht geholt werden — bitte neu laden.')` → `Received: null`
  - `zeigt nach eigenem "Fertig" den Hinweis "Sie lesen nur mit."…` (gehört eigentlich zu Schritt 2, lief aber im selben Lauf rot mit, siehe dort)
- Implementiert: `bearbeitenGesperrtGrund` in `LieferantDokumentModal.tsx` aus `lock.status` abgeleitet — `"Sperre wird gerade geholt …"` bei `loading`, identischer Wortlaut wie das rote Fehlerband (`LOCK_FEHLER_TEXT`) bei `error`, sonst `undefined`. An `BearbeitenLeiste` durchgereicht.
- Nach Fix: alle 18 Tests in `LieferantDokumentModal.test.tsx` grün.

**Schritt 2 — `zeigeNurLesenHinweis`:**
- Roter Test vorher: `zeigt nach eigenem "Fertig" den Hinweis "Sie lesen nur mit." in der Leiste…` → Timeout, `findByText('Sie lesen nur mit.')` fand nichts (Prop wurde nie gesetzt, Default `false`).
- Implementiert: `zeigeNurLesenHinweis = lock.modus === "lesen" && lock.status !== "locked-by-other"` — zeigt den Hinweis in jedem Lesen-Zustand außer bei Fremdsperre (dort erklärt `GesperrtHinweis` bereits, kein doppelter Text).
- Test „zeigt bei Fremdsperre KEINEN Hinweis…" war schon vor der Implementierung grün (Default `false`) — bestätigt aber die Nicht-Regression nach dem Fix.

**Schritt 3 — Playwright-Spec erweitert** (`e2e/lieferant-dokument-modal.spec.ts`):
- Test „freies Lock…": nach `fertig.click()` zusätzlich geprüft, dass „Sie lesen nur mit." sichtbar ist und der Bearbeiten-Knopf kein `title` trägt; neuer Screenshot `lieferant-modal-lesen-hinweis` (ersetzt den bisherigen Namen `lieferant-modal-gesperrt` an derselben Stelle im Ablauf).
- Test „Fehlerfall (500) beim Oeffnen…": zusätzlich `bearbeiten.hover()` und Prüfung von `title`/`aria-describedby`; neuer Screenshot `lieferant-modal-fehler-tooltip`.
- Lauf: `E2E_PORT=5179 npx playwright test e2e/lieferant-dokument-modal.spec.ts` → **8 passed** (4 Tests × 2 Projekte `pc-14zoll`/`pc-monitor`).
- Screenshots (Auswahl, alle unter `react-pc-frontend/test-results/design/`):
  - `lieferant-modal-lesen-hinweis--pc-14zoll.png` / `--pc-monitor.png`
  - `lieferant-modal-fehler-tooltip--pc-14zoll.png` / `--pc-monitor.png`
  - kurzer Blick (Coding-Agent, keine formale Beurteilung): Hinweis „Sie lesen nur mit." erscheint links neben dem aktivierbaren Bearbeiten-Knopf, keine Überschneidung; im Fehlerzustand steht das rote Band oben, der Knopf ist grau/deaktiviert (Tooltip selbst ist ein natives `title`-Attribut, im Screenshot nicht sichtbar, per `getAttribute` verifiziert).

### Mutationsproben (vor dem Commit, danach Quellstand wieder byte-identisch)
- Grund nicht durchreichen (`bearbeitenGesperrtGrund={undefined}` fest verdrahtet): 2 Tests rot (`loading`- und `error`-Titel-Assertion).
- `zeigeNurLesenHinweis` fest `false`: 1 Test rot (Hinweis nach eigenem „Fertig" fehlt).
- Jeweils per `git diff`/Zeilen-Vergleich bestätigt: Quellcode nach Revert wieder identisch zum Stand vor der Mutation (nur die eine mutierte Zeile geändert und zurückgesetzt).

### Gates
- `npx vitest run src/components/LieferantDokumentModal.test.tsx` → 18/18 grün.
- `npm run lint` → 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, `react-hooks/exhaustive-deps`) — unverändert zur Baseline.
- `npm run build` → grün; `src/main/resources/static/` danach zurückgesetzt (`git checkout` auf die zwei vorbestehenden Dateien, neu erzeugte JS-Datei gelöscht).
- `E2E_PORT=5179 npx playwright test e2e/lieferant-dokument-modal.spec.ts` → 8/8 grün (beide Größen).

### Bedenken / Abweichungen vom Plan

- **Wortlaut „loading"-Tooltip weicht vom Banner-Text ab:** Der bestehende Lade-Hinweis im Modal (links neben der Leiste) heißt „Sperre wird geprüft…", der neue Tooltip auf dem Bearbeiten-Knopf (Vorgabe aus dem Task-Text) heißt „Sperre wird gerade geholt …". Für den Fehlerfall verlangt der Task explizit denselben Wortlaut wie das rote Band — für `loading` gibt es diese Vorgabe nicht, nur einen Formulierungsvorschlag. Bewusst wörtlich wie vorgegeben umgesetzt, auch wenn zwei leicht unterschiedliche Formulierungen für denselben Ladezustand nebeneinander stehen (Banner vs. Tooltip). Falls das im Design-Review als Inkonsistenz auffällt: einfache Anpassung, beide auf denselben Wortlaut zu bringen.
- **Hinweis erscheint auch waehrend `loading`/`error`, nicht nur nach „Fertig":** `zeigeNurLesenHinweis` ist an `lock.status !== "locked-by-other"` geknüpft (nicht nur an `status === 'idle'`), weil die Task-Vorgabe als einzige Ausnahme die Fremdsperre nennt. Dadurch zeigt die Leiste „Sie lesen nur mit." auch während des Sperren-Abrufs und im Fehlerfall — dort stehen dann Ladehinweis/Fehlerband UND „Sie lesen nur mit." gleichzeitig. Inhaltlich nicht falsch (der Nutzer liest tatsächlich nur mit), aber möglicherweise redundant. Nicht separat rot getestet, da der Task nur „nach eigenem Fertig" und „bei Fremdsperre" als Pflicht-Testfälle nennt; auf den Screenshots (`lieferant-modal-fehler-tooltip`) ist der Zustand sichtbar und wurde nicht beanstandet.
- **Screenshot-Name `lieferant-modal-gesperrt` entfernt:** An derselben Stelle im „freies Lock"-Test (nach `fertig.click()`) hieß der bisherige Screenshot `lieferant-modal-gesperrt`; er wurde zu `lieferant-modal-lesen-hinweis` umbenannt (gleicher Zustand, jetzt mit sichtbarem Hinweis). Keine andere Stelle referenziert den alten Namen (geprüft: nur in dieser Spec verwendet).
- Sonst nichts weiter im Modal verändert, wie vorgegeben.

## Abschnitt 7-2 — Task 7a (Coding-Agent)

**Zeit:** 04.–05.09.2026, ca. 23:40–01:00 Uhr (lokale Sessionzeit)
**Branch:** `lock/task-7a-editor-seite` (auf `claude/eloquent-ramanujan-gz0w2t` @ `757ca9a5`)
**Commits:**
- `8c05a3ce` — feat(dokument-editor): Speicher-Auslöser nach außen für die Seite (Task 7a)
- `ff988816` — fix(tab-schliessen-hinweis): text-balance gegen alleinstehendes "Sie"
- `c57e8761` — feat(dokument-editor-seite): auf useDatensatzLock/BearbeitenLeiste umgestellt (Issue #82, Abschnitt 7a)
- `43f382ee` — test(e2e): Playwright-Spec für die Dokument-Editor-Seite (Issue #82, Abschnitt 7a)

**Status:** 🟢 fertig aus meiner Sicht — alle eigenen Gates grün. Ein Bedenken zu einer FREMDEN, jetzt kaputten Spec (siehe unten), bewusst nicht selbst repariert.

### Was gemacht wurde

Pflichtlektüre vor dem ersten Edit: FRONTEND_UI.md, TESTING_SECURITY.md, `handwerkerprogramm-design` (SKILL.md + README.md, direkt per Read-Tool — das Skill-Tool fand ihn in diesem Worktree), `playwright-design-pruefung/SKILL.md`, `kriterien.md`. Dazu alle fünf fertigen Bausteine mit ihren Kommentaren/Tests (`useDatensatzLock.ts`, `document-editor/index.tsx`, `BearbeitenLeiste.tsx`, `GesperrtHinweis.tsx`, `useIdleTimer.ts`, `toast.tsx`) und `LieferantDokumentModal.tsx` als Vorbild, plus der komplette bisherige Kontext-Log (Abschnitte 6/7/7-1) für die 6a-Entscheidungen zum X-Button-Ablauf.

**1. `useDocumentLock`/`DocumentLockedModal` raus, `useDatensatzLock('AUSGANG', dokumentIdNum)` rein; kein Flackern/Remount beim Anlegen.**

Rotes Verhalten vorab durchdacht statt blind mit `lock.modus` verdrahtet: ein Dokument, das ohne Id startet und beim ersten Speichern seine Id über die URL bekommt, durchläuft in `useDatensatzLock` real `idle`/`loading`, bevor `modus` auf `bearbeiten` springt — ein `readOnly`, das strikt an `lock.modus` hängt, hätte in genau diesem Fenster kurz auf `true` geflackert (verboten laut Auftrag). Lösung: `kamOhneIdRef` (einmalig beim Mount erfasst: kam die Seite ohne Id?) plus `ersterErwerbNachAnlageGeschehenRef` (schaltet die optimistische "schon bearbeiten"-Anzeige nach der ERSTEN Auflösung dieses einen Erwerbs endgültig ab, egal ob Erfolg oder Fehlschlag) — ein SPÄTERER Lock-Verlust oder Übernahmeversuch läuft danach wieder ganz normal über die echten Zustände.

Getrennt davon: `ersteLockAufloesungGeschehenRef` steuert nur die Vollbild-Ladeanzeige ("Dokument wird geöffnet …") und bleibt für ein Dokument OHNE Id von Anfang an `true` (nie anzeigen) bzw. schaltet für ein Dokument MIT Id nach der ersten Auflösung dauerhaft ab — ein späterer Retry (Klick auf "Bearbeiten" nach Fremdsperre/Fehler) ersetzt den Editor danach nicht mehr durch die Ladeanzeige, genau wie im Lieferant-Modal.

Roter Test 1 (`DocumentEditorPage.test.tsx`, "flackert beim Anlegen ... nicht auf readOnly und mountet den Editor nicht neu"): verzögerte Acquire-Antwort (kontrollierbares Promise), Klick auf einen simulierten `setSearchParams`-Aufruf (genau das, was `document-editor/index.tsx`s `syncDocumentIdInUrl` tut) — mit `readOnly = hatId ? lock.modus !== 'bearbeiten' : false` (naive Fassung ohne die Optimistik) rot: `data-readonly` sprang mitten im Roundtrip auf `"true"`. Mit der Optimistik grün, Mount-Zähler bleibt bei 1.

Roter Test 2 (`DocumentEditorPage.test.tsx`, "zeigt eine Vollbild-Ladeanzeige, solange das Lock noch nicht aufgelöst ist"): Acquire-Promise, die nie auflöst, für ein Dokument MIT Id von Anfang an — ohne `zeigeLadeSeite` hätte der (gemockte) Editor sofort sichtbar sein müssen; Test verlangt `queryByTestId('mock-editor')` == null.

**2. Zustände über dem Editor** (`loading`/`locked-by-other`/`acquired`+`bearbeiten`/`lesen` nach Fertig/`error`) — eigene Leiste mit `GesperrtHinweis` + `BearbeitenLeiste`, siehe unten für die Layout-Lösung.

Roter Test (Mutationsprobe, kein separater TDD-Vorlauf nötig, da direkt aus den vorhandenen Bausteinen zusammengesetzt): `gesperrtDurchAnderen` testweise auf `false` gesetzt → Test "zeigt bei fremder Sperre den Gesperrt-Hinweis..." wird rot (`findByText(HALTER_NAME)` findet nichts) — zurückgesetzt, grün.

**3. Untätigkeits-Timer** (`useIdleTimer`, `enabled: lock.modus === 'bearbeiten'`): `onIdle` ruft **erst** `editorRef.current?.speichernFuerFreigabe()`, **dann** `lock.freigeben()`.

Für den Speicher-Auslöser bot `DocumentEditor` bisher nichts an (kein Prop, kein Ref) — wie im Auftrag als möglich vorgesehen, minimal ergänzt: `forwardRef` + `useImperativeHandle` mit **genau einer** Methode `speichernFuerFreigabe()` (No-op ohne ungespeicherte Änderungen oder bei `isLocked`, sonst `await handleSave()`). Dokumentiert als `DocumentEditorHandle` in `document-editor/types.ts`. **Das ist die im Auftrag vorgesehene Abweichung** — siehe unten.

Roter Test 1 (`document-editor/index.test.tsx`, neue Describe "imperatives Handle speichernFuerFreigabe"): drei Tests (speichert bei Änderungen, No-op ohne Änderungen, No-op bei `readOnly`). Mutationsprobe: `if (isLocked || !hasUnsavedChanges) return;` durch einen Kommentar ersetzt → genau der "No-op ohne ungespeicherte Änderungen"-Test wird rot (`expected 1 to be +0`) — zurückgesetzt, grün.

Roter Test 2 (`DocumentEditorPage.test.tsx`, "speichert VOR dem Freigeben (Reihenfolge per Spy)"): `vi.useFakeTimers()`, 300 000 ms vorspulen (Standard-Timeout aus `useIdleTimer`), Reihenfolge-Array via Spy auf dem gemockten `speichernFuerFreigabe` und einem `onDelete`-Hook im Fetch-Stub. Mutationsprobe: Reihenfolge im `onIdle`-Callback vertauscht (erst `freigeben()`, dann `speichernFuerFreigabe()`) → Test rot (`expected ['freigeben','speichern'] to deeply equal ['speichern','freigeben']`) — zurückgesetzt, grün.

**4. `handleClose` unverändert** (`navigate(-1)` vs. `window.close()`, wie 6a es verbunden hat) — nur als Fallback an `onClose` durchgereicht, aktiv nur wenn `onLockFreigeben` fehlt (kein Lock, z. B. neues Dokument ohne Id).

**5. `TabSchliessenHinweis`: `text-balance` am Hinweistext.** Roter Test zuerst (`toContain('text-balance')` schlug fehl, Klasse fehlte), dann ergänzt. 8/8 grün.

**6. Zusätzlicher, im Auftrag nicht wörtlich genannter, aber notwendiger Fund während der eigenen Playwright-Probe:** die eigene Bearbeiten-Leiste blieb nach dem X-Button-Schließen sichtbar (inkl. aktivem "Bearbeiten"-Knopf) **über** dem `TabSchliessenHinweis` des Editors — der ist als bewusst alleinstehende, aktionslose Vollbild-Bestätigung gedacht ("nichts mehr zu tun"). Kein Bounding-Box-Überlapp (die Leiste steht als eigene Zeile darüber), aber ein widersprüchlicher zweiter Kopf: "Sie können schließen" + ein Knopf, der wieder öffnet. Ursache: `lock.freigeben()` (vom X-Button-Ablauf über `onLockFreigeben` aufgerufen) setzt `modus` sofort auf `lesen`, die Seite zeigt daraufhin ganz normal "Sie lesen nur mit." + "Bearbeiten" — genau wie nach einem echten "Fertig", nur dass hier gerade geschlossen wird.

Roter Test zuerst (`DocumentEditorPage.test.tsx`, "blendet die eigene Leiste aus, sobald der Editor darüber die Sperre freigibt"): Klick auf den `onLockFreigeben`-Aufruf im Mock → `queryByRole('button', {name:'Fertig'})`/`'Bearbeiten'` sollen verschwinden — vor dem Fix rot (Leiste blieb stehen). Fix: `onLockFreigebenFuerSchliessen` (setzt `schliesstGerade=true`, dann `lock.freigeben()`) statt `lock.freigeben` direkt an `onLockFreigeben` gebunden; die Leiste rendert nur noch bei `hatId && !schliesstGerade`. Ein zweiter Test sichert ab, dass ein **normales** "Fertig" (nicht über den X-Button) die Leiste NICHT ausblendet — "Sie lesen nur mit." muss dort bestehen bleiben. Mutationsprobe: `!schliesstGerade` aus der Bedingung entfernt → der erste Test wird rot — zurückgesetzt, grün. Per Playwright-Screenshot bestätigt (`editor-seite-tab-schliessen--pc-14zoll.png`): jetzt eine saubere, alleinstehende Bestätigungsseite.

### Layout-Lösung für die Bearbeiten-Leiste über dem Editor

`DocumentEditor` legt sich selbst als `fixed inset-0 z-50` an (unverändert, eigenständige Vollbild-Komponente). Eine Leiste einfach als vorangehendes Geschwister-Element in der Seite hätte der Editor mit seinem eigenen `fixed inset-0` vollständig verdeckt (beide beanspruchen denselben Viewport-Bereich, unabhängig von der DOM-Reihenfolge). Lösung: der Editor steckt jetzt in einem Container mit `[transform:translateZ(0)]` — laut CSS-Spezifikation erzeugt `transform` (wie auch `filter`/`perspective`/`will-change`) für `position:fixed`-Nachfahren ein eigenes "containing block". Da dieser Container in einem `flex flex-col`-Layout **unterhalb** der (bei Bedarf `shrink-0`) gerenderten Leiste liegt, bezieht sich das `inset-0` des Editors jetzt auf genau den freien Platz darunter — er passt sich von selbst an, ob die Leiste gerade sichtbar ist. Per Playwright-Screenshot auf beiden Größen bestätigt (siehe unten): kein Überlapp, keine verdeckten Bereiche, der Editor bleibt vollständig innerhalb seines Bereichs.

### Tests je Größe

- `npx vitest run src/pages/DocumentEditorPage.test.tsx src/components/document-editor/index.test.tsx src/components/lock/TabSchliessenHinweis.test.tsx` → **41/41 grün** (11 neu + 22 [19 bestehend + 3 neu] + 8 [7 bestehend + 1 neu]).
- `npm run lint` → **0 Fehler, genau die 1 vorbestehende Warnung** (`BelegeKasseEditor.tsx:1204`).
- `npm run build` → grün, `src/main/resources/static/` danach zurückgesetzt (git checkout `index.html`, `git clean` für die neue gehashte JS/CSS-Datei), vor jedem Commit `git status` nur eigene Dateien.
- `E2E_PORT=5178 npx playwright test e2e/dokument-editor-seite.spec.ts` → **10/10 grün** (5 Fälle × `pc-14zoll`/`pc-monitor`): freies Lock editierbar+"Fertig"; Fertig→readOnly+"Sie lesen nur mit."+Bearbeiten reaktiviert; fremdes Lock (409)+Dummy-Name "Anna Beispiel"; Acquire-Fehler (500)+Hinweis+Toast+Tooltip; X-Button-Ablauf über die echte Route (speichern→DELETE→`TabSchliessenHinweis`, **zum ersten Mal über die echte Route erreichbar**, siehe 6a-Bedenken/7-1-Design-Review).

### Screenshot-Pfade (kurz selbst angeschaut, formale Beurteilung bleibt dem Design-Reviewer)

`react-pc-frontend/test-results/design/`, je `--pc-14zoll`/`--pc-monitor`:
- `editor-seite-bearbeiten` — freies Lock, "Fertig" oben rechts, Leiste links leer (bekanntes 🟡-Muster aus dem Lieferant-Modal, nicht neu).
- `editor-seite-lesen` — nach "Fertig", "Sie lesen nur mit." + "Bearbeiten" (rose-600).
- `editor-seite-gesperrt` — `GesperrtHinweis` (rose-50-Band, "Anna Beispiel bearbeitet das gerade … Seit 5 Min."), "Bearbeiten" aktiv.
- `editor-seite-fehler` — rotes Fehlerband, "Bearbeiten" deaktiviert.
- `editor-seite-tab-schliessen` — **jetzt eine saubere, alleinstehende Bestätigungsseite** (siehe Punkt 6 oben; vor dem Fix stand hier noch die Leiste mit aktivem "Bearbeiten" über der Bestätigung).

### Bedenken / Abweichungen vom Plan

1. **`document-editor/index.tsx` + `document-editor/types.ts` + `document-editor/index.test.tsx` angefasst** — laut Auftrag "nur falls nötig" erlaubt, hiermit als Abweichung vermerkt. Geprüft, dass `DocumentEditor` bisher wirklich nichts Passendes anbot (kein Prop, kein Ref). Minimal gehalten: ein `forwardRef`-Wrapper um die bestehende Funktion, eine `useImperativeHandle`-Stelle mit genau einer Methode, ein neues Interface in `types.ts`. Kein sonstiges Verhalten geändert. `index.test.tsx` bekam drei neue Tests in einer eigenen Describe — notwendige Testabdeckung für die neue Ergänzung, analog zu 6a's Präzedenzfall (dort wurde `types.ts` aus demselben Grund mitgeändert und vom Orchestrator als unbedenklich bestätigt).

2. **🛑 Fremde Spec kaputt, NICHT repariert (nur gemeldet):** `e2e/dokument-editor-tab-schliessen.spec.ts` (Task 6a, nicht in meiner Dateiliste) hat zwei Tests, die über `page.locator('button').first()` den X-Knopf des Editors ansprechen (`xKnopf()`, dort ohne `aria-label`). Durch meine neue Bearbeiten-Leiste STEHT JETZT EIN ANDERER BUTTON ("Fertig"/"Bearbeiten") vor dem X-Knopf im DOM — `button.first()` trifft jetzt die Leiste, nicht mehr den X-Knopf. Selbst reproduziert: `E2E_PORT=5178 npx playwright test e2e/dokument-editor-tab-schliessen.spec.ts` → **4 von 6 Tests rot** (die beiden X-Button-Tests × 2 Größen; der dritte Test dort, der keinen `xKnopf()` braucht, bleibt grün). `e2e/lieferant-dokument-modal.spec.ts` und `e2e/bearbeiten-leiste.spec.ts` (18 Tests) bleiben davon unberührt, selbst gegengeprüft.
   In MEINER eigenen Spec habe ich dasselbe Problem gefunden und behoben (`data-testid="dokument-editor-flaeche"` auf den Editor-Container, `xKnopf()` darauf eingegrenzt) — der exakt gleiche Fix (eine Zeile) würde `dokument-editor-tab-schliessen.spec.ts` reparieren:
   ```ts
   function xKnopf(page: Page) {
       return page.getByTestId('dokument-editor-flaeche').locator('button').first();
   }
   ```
   Nicht selbst angewendet, weil die Datei nicht in meiner Dateiliste steht ("Braucht ein Punkt eine Datei außerhalb der Liste: melden, nicht anfassen"). **Bitte beim Review/Merge einplanen** — sonst meldet der Code-Reviewer bei seinem Volllauf zwei rote E2E-Tests, deren Ursache hier bereits geklärt ist.

3. **`DocumentEditorHeader.tsx` zeigt "Gebucht" für JEDES `isLocked`, nicht nur für tatsächlich gebuchte Dokumente** — vorbestehendes Verhalten (der `readOnly`-Prop fließt seit 6a additiv in `isLocked` ein), auf den Screenshots `editor-seite-lesen`/`-gesperrt`/`-fehler` dadurch sichtbar ein "Gebucht"-Badge, obwohl das Beispieldokument nicht gebucht ist, sondern nur wegen der Sperre/des Fehlers schreibgeschützt. Nicht repariert (Datei nicht in meiner Liste, Verhalten stammt nicht aus diesem Abschnitt) — nur als Beobachtung vermerkt.

4. **`onLockFreigeben`-Ablauf beim Schließen eines GERADE ERST angelegten, noch unbenannten Dokuments (Randfall):** Klickt der Nutzer X, bevor das Dokument je gespeichert wurde, UND wählt dann im Warn-Dialog "Speichern & Schließen", läuft `handleSave()` (legt an, Id kommt über die URL) **innerhalb desselben Aufrufs**, der bereits die zu diesem Zeitpunkt (noch ohne `onLockFreigeben`) gebundene `tabSchliessen()`-Closure verwendet — der neue, Prop-gesteuerte Ablauf (Sperre aktiv freigeben, `TabSchliessenHinweis`) greift für DIESEN einen Klick nicht, es bleibt beim alten `onClose`-Fallback (`navigate(-1)`/`window.close()`). Das frisch erworbene Lock hängt dadurch nicht verwaist: `useDatensatzLock`s eigener Cleanup-/`pagehide`-Pfad (`releaseKeepalive`) gibt es beim Unmount bzw. beim tatsächlichen Schließen ohnehin per `keepalive`-`DELETE` frei — nur eben nicht über den "schönen", awaiteten Weg mit Bestätigungsseite. Nicht behoben (hätte eine Ref-Indirektion in `document-editor/index.tsx` gebraucht, über den sanktionierten Speicher-Auslöser hinaus) — als Randfall vermerkt statt stillschweigend hingenommen.

5. **`handwerkerprogramm-design`-Skill über das Skill-Tool nicht im Standard-Weg geprüft** — wie schon in früheren Abschnitten (siehe 6a) direkt per Read-Tool gelesen (SKILL.md + README.md) statt über `Skill({skill: ...})`, da in dieser Session ohnehin nur Lese-Werkzeuge zur Verfügung standen und der direkte Weg zuverlässig funktioniert.

Keine sonstigen Abweichungen. Plan und Realität stimmten größtenteils überein — die Optimistik-Logik gegen das Flackern und die `schliesstGerade`-Ergänzung waren beim Schreiben des Plans nicht im Detail vorweggenommen, ließen sich aber vollständig innerhalb der vorgesehenen Dateiliste lösen.

## Abschnitt 7-2 — Code-Review (Code-Reviewer)

**Ampel: 🟡** — kein blockierender Befund. Von meiner Seite abgenommen. Stand `256b3e1d`.

### Verbraucher-Greps (Voraussetzung für das Löschen in Abschnitt 8)

**Frontend** (`useDocumentLock|DocumentLockedModal|dokument-locks`): im **Produktivcode sauber**.
Übrig sind nur die alten Dateien selbst (`useDocumentLock.ts`, `DocumentLockedModal.tsx`) und reine
Prosa-Erwähnungen in Kommentaren (`GesperrtHinweis`, `useDatensatzLock`, `DocumentEditorPage`,
`document-editor/index.tsx:1188`). Kein Produktivcode ruft sie mehr auf.

**Aber ein Test tut es noch:** `document-editor/index.test.tsx` importiert `useDocumentLock` (Zeile 9,
benutzt in Zeile 204) und stubbt `/api/dokument-locks/AUSGANG/42/{acquire,heartbeat}` (Zeilen 254/261).
Das ist der Seiten-Nachbau `SeiteMitAltemLock` aus Abschnitt 6, Durchgang 2. Beim Löschen in
Abschnitt 8 bricht diese Datei — der Nachbau sollte dort auf `useDatensatzLock` gezogen werden
(jetzt möglich, da 7a die echte Seite umgestellt hat).

**Backend** (`DokumentLockService|DokumentLockDto|DokumentLockRepository`): **sauber**. Nur noch die
alten Klassen untereinander (`DokumentLockController`, `DokumentLockService`, `DokumentLockDto`,
`DokumentLockRepository`) plus zwei Prosa-Kommentare in `SperrbarerTyp.java` und
`DatensatzLockService.java`. `DokumentLockController` ist damit ein noch gemappter, aber
verbraucherloser Endpunkt unter `/api/dokument-locks` — fällt in Abschnitt 8.

### Mutationsproben (Quellstand danach byte-identisch, `git diff 256b3e1d` leer)

1. **Reihenfolge speichern/freigeben** — in `onIdle` getauscht ⇒
   `AssertionError: expected [ 'freigeben', 'speichern' ] to deeply equal [ 'speichern', 'freigeben' ]`
   (1 von 11 rot).
2. **`readOnly` bei Fremdsperre** — `readOnly` fest auf `false` ⇒ **3 von 11 rot**
   („Fertig gibt frei", „fremde Sperre … Editor readOnly", „Acquire-Fehler … deaktivierter Knopf").
3. **`speichernFuerFreigabe` ohne Wirkung** — Rumpf auf `return;` ⇒
   `AssertionError: expected 0 to be greater than or equal to 1` („speichert über das Ref, wenn
   ungespeicherte Änderungen vorliegen"), 1 von 33 rot.

### Das optimistische Fenster — selbst nachgestellt

Die Frage war, ob `kamOhneIdRef`/`ersterErwerbNachAnlageGeschehenRef` wirklich **nur** den
Anlege-Übergang abdecken und ein späterer echter Lock-Verlust trotzdem sofort `readOnly` erzwingt.
Eigene Wegwerf-Probe an der echten Seite (Start ohne Id → Anlegen → Acquire 200 → 30 s später
Heartbeat 409):

```
start   : readOnly=false
angelegt: readOnly=false                     (kein Flackern, wie beabsichtigt)
nach HB-409: readOnly=true  gesperrtHinweisSichtbar=true
```

Sauber. Der Riegel hält, weil die Latch-Zeile beim **ersten** Auflösen (`acquired` /
`locked-by-other` / `error`) endgültig zuschnappt; danach zählt nur noch der echte `lock.modus`.
Auch die Richtung stimmt: bliebe der Server hängen, bliebe das Fenster offen — dann hält der
Ersteller das Lock aber ohnehin serverseitig (Backend-`create` vergibt es), editierbar ist also
richtig. Ein Fremdhalter kann in diesem Fenster nicht auftreten. `kamOhneIdRef` wird nur im ersten
Render gesetzt, eine mit Id geöffnete Seite bekommt die Optimistik also nie.

Kleiner Vorbehalt ohne praktische Folge: die Latch-Zuweisung passiert im Render-Rumpf (Ref-Mutation
während des Renderns). Unter StrictMode/Concurrent kann sie in einem verworfenen Render zuschnappen
— dann schließt das Fenster einen Tick zu früh, also in die **sichere** Richtung (kurzes Flackern
statt fälschlich editierbar).

### 💡 Hinweise (blockieren nicht)

- **`DocumentEditorHeader.tsx:75-81` — „Gebucht"-Badge bei jedem `isLocked`.** Ich habe das als
  **🟡** eingestuft, nicht als 🔴 — mit Begründung, weil die Frage ausdrücklich gestellt war:
  Die Aussage ist fachlich falsch (ein Schloss-Symbol mit dem Wort „Gebucht" auf einem ungebuchten
  Dokument), und sie ist durch diese Runde erstmals in einer **häufigen** Lage sichtbar — jeder
  Kollege, der ein gesperrtes Dokument öffnet, sieht sie. Gegen 🔴 spricht: die Fehlkopplung ist
  **älter als dieses Vorhaben** (`storniert` und `digitalAngenommen` lösen sie seit jeher aus — ein
  storniertes Dokument trägt heute „Gebucht" **und** „Storniert" nebeneinander), es geht nichts
  verloren, nichts wird still überschrieben, und direkt daneben steht mit `GesperrtHinweis` die
  richtige Erklärung. Das ist ein falsches Etikett, kein kaputter Ablauf.
  Trotzdem gehört es vor der Auslieferung repariert, nicht auf unbestimmt vertagt.
  **Fix gehört in den Header:** `DocumentEditorHeader.tsx` darf das Badge nicht aus `isLocked`
  ableiten. Der Editor rechnet `gebucht && invoiceTypes.includes(typ)` ohnehin schon für `isLocked`
  aus (`index.tsx:428-433`) — dieser Teilausdruck als eigener Prop (z. B. `zeigeGebuchtBadge`)
  hinüber, `isLocked` bleibt für das Deaktivieren der Bedienelemente. Zwei kleine Änderungen in
  `index.tsx` und `DocumentEditorHeader.tsx`.
- **`DocumentEditorPage.tsx` — der `[transform:translateZ(0)]`-Container betrifft mehr als den
  Editor-Rahmen.** Ein `transform` macht das Element laut CSS-Spezifikation zum Containing Block für
  **alle** `position: fixed`-Nachfahren — nicht nur für den Editor selbst. Im Editor-Teilbaum liegen
  mindestens 12 Vollbild-Overlays mit `fixed inset-0` (`Modals.tsx` 6x, `AlternativGruppeDialog`,
  `RabattDialog`, `EmailFormatDialog`, `EmailValidityDialog`, `KategorieBestaetigenDialog`,
  `ArtikelAuswahlDialog`) plus ein Tooltip mit `fixed z-[100]`. Die sind jetzt alle auf den Bereich
  **unterhalb** der Leiste beschränkt: ihr Backdrop deckt die Leiste nicht mehr ab, und weil die
  Leiste geometrisch außerhalb liegt, bleibt sie sichtbar **und anklickbar**, während ein
  Editor-Dialog offen ist. Ein Klick auf „Fertig" bei offenem Ungespeichert-Dialog gibt dann die
  Sperre frei und setzt den Editor unter dem Dialog auf `readOnly`. Unschön, nicht zerstörerisch
  (Speichern wird serverseitig abgewiesen). Sauber wäre, die Leiste in denselben Containing Block zu
  legen, damit Editor-Overlays sie mit abdecken. Die optische Hälfte (Dialoge sitzen jetzt im
  verkleinerten Bereich statt mittig im Viewport) gehört dem Design-Reviewer.
  **Nicht betroffen:** der Toast-Container (Provider hängt über der Seite) und `KiHilfeChat`
  (Geschwister *außerhalb* des transformierten Containers) — beide bleiben viewport-verankert.
  Geprüft, kein Befund.
- **`DocumentEditorPage.tsx` (`onLockFreigebenFuerSchliessen`)** — `setSchliesstGerade(true)` steht
  **vor** dem `await lock.freigeben()`. Scheiterte die Freigabe, bliebe die Leiste dauerhaft
  ausgeblendet, während der Editor offen bleibt. In der Praxis nicht erreichbar, weil
  `aktivFreigeben` seinen Fetch selbst abfängt und nie ablehnt — aber das Flag nach dem `await` zu
  setzen wäre robuster.
- **Editor-X-Knopf ohne `aria-label`** (`DocumentEditorHeader.tsx:64`) — seit 6a offen, weiterhin
  offen. Die Tests behelfen sich mit „erster Button im Baum"; der Orchestrator musste den E2E-Locator
  deswegen gerade erst auf `getByTestId('dokument-editor-flaeche')` eingrenzen. Ein `aria-label`
  würde beides erledigen.

### Sonst geprüft, ohne Befund

- **Idle:** `enabled: lock.modus === 'bearbeiten'` nutzt bewusst den **echten**, nicht den
  optimistischen Modus — im Lesen-Modus läuft kein Timer, es gibt nichts zu verlieren. `onIdle`
  speichert erst, gibt dann frei; durch Test und Mutationsprobe belegt.
- **`speichernFuerFreigabe`:** No-op ohne ungespeicherte Änderungen und bei `isLocked` — kein
  Leerlauf-Request. Wartet den vollen Roundtrip ab, bevor die Seite freigibt. Genau eine Methode am
  Handle, minimale Schnittstelle.
- **`schliesstGerade`** versteckt die Leiste nur auf dem X-Pfad; das normale „Fertig" läuft nicht
  durch den Wrapper, die Leiste bleibt sichtbar — beide Fälle je mit eigenem Test.
- **7d:** `bearbeitenGesperrtGrund` nur bei `loading`/`error` gesetzt (genau die Zustände mit
  `kannBearbeiten === false`), sonst `undefined` ⇒ gar kein `title`. `zeigeNurLesenHinweis` schließt
  die Fremdsperre aus, damit nicht zwei Texte denselben Zustand erklären. Der Fehlertext ist
  wortgleich mit Band und Toast. Sauber verdrahtet.
- **Datenschutz:** nur Dummy-Namen (Anna Beispiel, Bea Beispiel). Keine Secrets, kein Build-Output,
  kein `test-results/` im Diff.
- **Performance:** kein neues Polling, kein zweiter Timer; der Editor mountet beim Id-Wechsel nicht
  neu (`mountCount` bleibt 1, eigener Test).

### Selbst gemessene Zahlen

| | Baseline (7-1) | jetzt |
|---|---|---|
| Backend | 2462 / 0 F / 4 E | **2462 / 0 Failures / 4 Errors** |
| Frontend Testdateien | 87 | **88** (+1 `DocumentEditorPage.test.tsx`) |
| Frontend Tests | 1052 | **1071** (+19) |
| Lint | 0 Fehler, 1 Warnung | **0 Fehler, 1 Warnung** |

- Die 4 Backend-Errors sind namentlich die bekannten umgebungsbedingten; sonst keine Ausfälle.
- Frontend-Volllauf **komplett grün, kein Flake** — kein einziger Test musste einzeln nachgefahren
  werden.
- Lint-Warnung ist die vorbestehende `BelegeKasseEditor.tsx:1204`.
- `npm run build` grün, `src/main/resources/static/` zurückgesetzt. Code byte-identisch zu
  `256b3e1d`. Die beiden unversionierten `.claude/skills/`-Dateien des Nutzers habe ich nicht
  angefasst.

## Abschnitt 7-2 — Design-Review (Design-Reviewer)

**Ampel: 🔴** — zwei blockierende Befunde, beide im Browser durchgemessen. Das Layout der
neuen Seite selbst ist dagegen sauber: die Leiste schiebt den Editor exakt, ohne
Überschneidung, auf beiden Größen.

Worktree `wt/review-design`, Stand `256b3e1d`. `E2E_PORT=5190 npm run test:e2e`:
**104 Tests, alle grün**, beide Größen `pc-14zoll` (1440×900) und `pc-monitor` (1920×1080).
Zehn Tests mehr als in 7-1 (94). Das Aufwärmen per `globalSetup` wirkt — das
Kaltstart-Flattern aus 7-1 ist nicht wieder aufgetreten, auch nicht beim Fahren einzelner
Specs.

### 🛑 Blockierend 1: „Gebucht" steht auf einem Dokument, das nicht gebucht ist

Der Editor-Kopf zeigt in **drei** der fünf neuen Zustände ein amber-Badge „Gebucht" —
`editor-seite-lesen`, `editor-seite-gesperrt`, `editor-seite-fehler`, auf beiden Größen.
Im Zustand `editor-seite-bearbeiten` fehlt es (dort ist `isLocked` false).

Nachgemessen im Browser (identisch auf 14 Zoll und 1920):

| | Wert |
| --- | --- |
| Badge-Text | `Gebucht` |
| Rahmen | x 188, y 70, 70 × 21 |
| Hintergrund / Text / Rand | `rgb(255,251,235)` amber-50 / `rgb(180,83,9)` amber-700 / `rgb(253,230,138)` amber-200 |
| Kontrast | 4,84 : 1 — gut lesbar |

Das Dokument im Test hat `gebucht: false` (`e2e/hilfen/dokument-editor.ts`,
`BEISPIEL_DOKUMENT`). Die Oberfläche behauptet also nachweislich das Gegenteil dessen, was
im Datensatz steht — nicht „unglücklich formuliert", sondern **falsch**.

Ursache: `DocumentEditorHeader.tsx` rendert das Badge an `isLocked`, und `isLocked` ist in
`document-editor/index.tsx:428` eine Sammelvariable aus vier verschiedenen Gründen:

```
const isLocked = !!(readOnly || dokument?.storniert || dokument?.digitalAngenommen
                    || (dokument?.gebucht && dokument?.typ && invoiceTypes.includes(dokument.typ)));
```

Der `readOnly`-Term kam in 6a dazu; der Kopf selbst wurde zuletzt lange vor diesem Vorhaben
angefasst. Neu ist, dass **dieser Abschnitt** die drei Nur-Lesen-Zustände über die echte
Route überhaupt sichtbar macht: vorher stand bei Fremdsperre ein blockierendes Modal davor,
der Nutzer sah den Editor gar nicht.

Warum das blockiert: „gebucht" heißt in diesem Produkt, die Rechnung ist in der Buchhaltung
erfasst. Ein Handwerker, der das an seiner Rechnung liest, zieht daraus Schlüsse über seine
Buchführung. Frage 4 (Gulf of Evaluation, „Was ist passiert?") wird nicht nur schlecht,
sondern falsch beantwortet — bei einem Finanzdokument.

**Nachweisbar sein muss:** Das Badge „Gebucht" erscheint genau dann, wenn `dokument.gebucht`
für einen Rechnungstyp gesetzt ist — und in keinem der Sperr-/Nur-Lesen-Zustände. Naheliegend:
dem Kopf ein eigenes `gebucht`-Prop geben statt `isLocked` doppelt zu benutzen; wenn ein
Zustand „schreibgeschützt" sichtbar sein soll, bekommt er ein eigenes, ehrliches Etikett.

### 🛑 Blockierend 2: Der Warn-Dialog blockiert die neue Leiste nicht — man kann die Sperre hinter dem offenen Dialog freigeben

Der Editor ist weiterhin `fixed inset-0`; die Seite gibt ihm über
`[transform:translateZ(0)]` ein eigenes Containing-Block unter der Leiste. Damit sind auch
**die Modale des Editors** auf diesen Container beschränkt — der Scrim des
„Ungespeicherte Änderungen"-Dialogs endet an der Oberkante des Editors und lässt die
Leiste darüber voll bedienbar.

Gemessen, beide Größen, bei offenem Warn-Dialog:

| | pc-14zoll | pc-monitor |
| --- | --- | --- |
| `Fertig` in der Leiste | [1339, 10, 85, 34] | [1819, 10, 85, 34] |
| `elementFromPoint` in dessen Mitte | **BUTTON „Fertig"** | **BUTTON „Fertig"** |
| Playwright-Klickbarkeit (`trial`) | **ja** | **ja** |

Und was dann passiert, ebenfalls gemessen (Klick auf `Fertig`, während der Dialog steht):

| | Ergebnis (beide Größen) |
| --- | --- |
| DELETE auf die Sperre | **1× abgesetzt — die Sperre ist weg** |
| Warn-Dialog | **steht weiter offen**, inklusive `Speichern & Schließen` |
| Leiste danach | zeigt `Bearbeiten` und „Sie lesen nur mit." |

Der Nutzer sieht damit gleichzeitig „Sie lesen nur mit." am Kopf und „Möchten Sie die
Änderungen speichern, bevor Sie den Editor verlassen?" in der Mitte, mit einem aktiven
`Speichern & Schließen` — ein Speichern-Angebot auf einem Datensatz, dessen Sperre gerade
freigegeben wurde. Zwei Aussagen auf einem Bildschirm, die sich widersprechen; erreichbar
in zwei Klicks. Das ist keine Geschmacksfrage, sondern ein Zustand, den die Oberfläche
selbst herstellt, und er entsteht durch die Layout-Technik dieses Abschnitts.

**Nachweisbar sein muss:** Solange ein Editor-Modal offen ist, ist der Umschalter der
Leiste nicht bedienbar (`elementFromPoint` in seiner Mitte trifft den Scrim, nicht den
Knopf) — oder die Leiste ist in diesem Moment gar nicht da. Die Probe gehört in
`e2e/dokument-editor-seite.spec.ts` neben den X-Knopf-Ablauf.

### Angeschaute Screenshots

Alle 36 aus `react-pc-frontend/test-results/design/`, jeweils `--pc-14zoll` und
`--pc-monitor`:

1. `editor-seite-bearbeiten` · 2. `editor-seite-lesen` · 3. `editor-seite-gesperrt` ·
4. `editor-seite-fehler` · 5. `editor-seite-tab-schliessen` (alle fünf neu)
6. `lieferant-modal-lesen-hinweis` · 7. `lieferant-modal-fehler-tooltip` (beide neu)
8. `lieferant-modal-bearbeiten` · 9. `lieferant-modal-fremdes-lock` ·
10. `lieferant-modal-fehler` · 11. `lieferant-modal-speicherfehler-toast`
12. `leiste-bearbeiten` · 13. `leiste-lesen` · 14. `leiste-countdown` ·
15. `leiste-verbindung-weg` · 16. `leiste-deaktiviert`
17. `dokument-editor-vor-schliessen` · 18. `dokument-editor-ungespeichert-warnung`

Dazu 6 eigene Aufnahmen (Wegwerf-Specs, danach gelöscht, Worktree ist sauber):
`editor-fehler-mit-toast`, `warndialog-gegen-leiste`, `nach-fertig-im-dialog`, je beide Größen.

### Die sechs Fragen je Zustand

**`editor-seite-bearbeiten` (14 Zoll + Monitor)**
1. *Farben?* Leiste weiß, `Fertig` weißer Outline-Knopf mit rose-Rand, `PDF` im Editor-Kopf
   die einzige gefüllte rosa Fläche. Kein „Gebucht"-Badge — korrekt.
2. *Design-System?* Ja. rose/slate, Lucide, kein Emoji, Systemschrift.
3. *Look-and-Feel?* Ruhig. Die Leiste ist im Bearbeiten-Zustand links leer — auf 1920 ein
   1920 px breiter weißer Streifen mit einem Knopf. Etwas viel Rahmen für eine Aktion, aber
   der Preis dafür, dass die Aktion immer an derselben Stelle steht. 🟡
4. *UX?* Der Editor ist voll bedienbar, `Fertig` klar als Sekundäraktion.
5. *Auffindbar?* Ja. Gemessen: Umschalter bei [1339, 10] bzw. [1819, 10], ohne Scrollen,
   `elementFromPoint` trifft den Knopf selbst.
6. *Überschneidung?* Nein, und hier ist die Layout-Technik nachweislich sauber:
   Leiste [0, 0, 1440, **55**], Editor-Fläche [0, **55**, 1440, 845], Ende bei 900 —
   bündig, kein Überlappen, kein Abschneiden. Auf 1920 analog (55 / 1025 / 1080).

**`editor-seite-lesen` (14 Zoll + Monitor)** — nach eigenem „Fertig"
1. *Farben?* „Sie lesen nur mit." in slate-500 neben rose-600 `Bearbeiten` — ruhig und klar.
   **Aber:** amber-Badge „Gebucht" im Kopf (Blocker 1).
2. *Design-System?* Farben ja. Das Badge-Icon ist ein handgemaltes inline-`<svg>` statt
   eines Lucide-Icons — Regelbruch, aber vorbestehend, siehe Hinweise.
3. *Look-and-Feel?* Ruhig, Werkzeugleiste des Editors korrekt reduziert.
4. *UX?* Der Zustand ist benannt (7c-Hinweis wirkt) — bis auf das falsche Badge.
5. *Auffindbar?* Ja, `Bearbeiten` am Kopf, ohne Scrollen.
6. *Überschneidung?* Nein. Leiste 55 px, Editor darunter.

**`editor-seite-gesperrt` (14 Zoll + Monitor)** — Fremdsperre (409)
1. *Farben?* Volles rose-50-Band mit `Lock`: „Anna Beispiel bearbeitet das gerade — Sie
   sehen den aktuellen Stand. Seit 5 Min.", daneben rose-600 `Bearbeiten`. Klar vom
   Fehlerzustand (rot) und vom Bearbeiten-Zustand (leer) getrennt. Badge-Problem wie oben.
2. *Design-System?* Ja (bis auf das vorbestehende Badge-SVG).
3. *Look-and-Feel?* Das Band füllt die Leiste auch auf 1920 sinnvoll — genau das, was dem
   Bearbeiten-Zustand fehlt.
4. *UX?* Klartext mit Namen, `Bearbeiten` bleibt sichtbar und aktiv (Übernahmeversuch).
   Zahlungsziel wird als Text statt als Eingabefeld gezeigt — korrekt.
5. *Auffindbar?* Ja, Hinweis und Aktion am Kopf, auf 14 Zoll ohne Scrollen.
6. *Überschneidung?* Nein. Leiste [0, 0, 1440, **59**], Editor [0, **59**, 1440, 841].

**`editor-seite-fehler` (14 Zoll + Monitor)** — Acquire 500
1. *Farben?* Rotes Band mit `AlertTriangle`, `Bearbeiten` deaktiviert (rose bei 50 %),
   dazu der Toast unten rechts. Störung klar von Hinweis getrennt. Badge-Problem wie oben.
2. *Design-System?* Ja.
3. *Look-and-Feel?* Ruhig.
4. *UX?* Meldung dreifach: Band, Toast und Tooltip am deaktivierten Knopf.
5. *Auffindbar?* Ja.
6. *Überschneidung?* Nein — und der Toast ist hier ausdrücklich geprüft worden. Kein Dialog
   offen, also steht er unten rechts (`bottom-6 right-6`, gemessen). Toast [978, 830, 438, 46]
   auf 14 Zoll gegen die Statuszeile [0, 864, 1440, 36]: die 12 px Überlappung treffen nur
   den Leerraum über der Textzeile. `elementFromPoint` auf **Netto**, **MwSt**, **Brutto**
   (Beschriftung und Wert) und **Zahlungsziel** trifft auf beiden Größen jeweils das eigene
   SPAN, nie den Toast. Nichts Gebrauchtes verdeckt.

**`editor-seite-tab-schliessen` (14 Zoll + Monitor)** — der X-Knopf-Ablauf über die echte Route
1. *Farben?* rose-100-Kreis mit rose-600 `CheckCircle2` auf slate-50, Text slate-700. Ruhig.
2. *Design-System?* Ja, Systemschrift, kein Webfont.
3. *Look-and-Feel?* Aufgeräumte Vollbild-Bestätigung.
4. *UX?* Der Ablauf funktioniert jetzt im Browser: Warnung ⇒ Speichern ⇒ Hinweisseite.
5. *Auffindbar?* Mittig, nichts zu suchen.
6. *Überschneidung?* Nein — und das `schliesstGerade`-Flag sitzt: **die Leiste ist weg**,
   kein „Fertig"/„Bearbeiten" mehr über der Bestätigung. Genau richtig.
   Der `text-balance` wirkt: der Umbruch fällt jetzt hinter „freigegeben", das alleinstehende
   „Sie" aus 7-1 ist weg (der Gedankenstrich beginnt nun Zeile 2 — Geschmackssache).

**`lieferant-modal-lesen-hinweis` (14 Zoll + Monitor)**
1.–6. „Sie lesen nur mit." füllt die vorher leere linke Bandhälfte, `Bearbeiten` rose-600
   daneben. Der 🟡 aus Abschnitt 6 ist damit auch hier erledigt. Keine Überschneidung.

**`lieferant-modal-fehler-tooltip` (14 Zoll + Monitor)**
1. *Farben?* Rotes Fehlerband **und** „Sie lesen nur mit." nebeneinander — siehe Hinweise.
2.–6. Sonst unverändert; Toast oben rechts, Fußleiste frei, keine Überschneidung.

**`leiste-*` (je 14 Zoll + Monitor)**
1.–6. Unverändert gut aus 7-1: amber-Countdown mit `Timer`, rotes Verbindung-weg-Band mit
   `WifiOff`, Umschalter an fester Stelle. Neu ist, dass `leiste-lesen` und
   `leiste-deaktiviert` jetzt „Sie lesen nur mit." zeigen (7d).

**`lieferant-modal-*` und `dokument-editor-*` (je beide Größen)**
1.–6. Gegenüber 7-1 unverändert. `dokument-editor-vor-schliessen` zeigt jetzt zusätzlich die
   Leiste am Kopf — konsistent mit der neuen Seite, ohne Überschneidung.

### 💡 Hinweise (blockieren nicht)

- **„Sie lesen nur mit." doppelt neben Fehler- und Ladeband** (die Frage des 7d-Agenten):
  störend, ja — aber nicht wegen der Wiederholung, sondern wegen der Gewichtung. Im
  Fehlerfall schrumpft das rote Band von x 61–1250 auf x 61–1132, damit der ruhige Hinweis
  danebenpasst: die dringende Meldung weicht der beiläufigen. Und die Lösung steht schon im
  Haus — die Seite benutzt die engere Regel
  `lock.status === 'idle'` (`DocumentEditorPage.tsx:109`), das Modal die weitere
  `lock.modus === 'lesen' && lock.status !== 'locked-by-other'`
  (`LieferantDokumentModal.tsx:119`). Das Modal auf die Regel der Seite angleichen, dann ist
  der Hinweis dort, wo er hingehört, und weg, wo ein Band schon spricht. 🟡
- **Das Badge-Icon ist ein handgemaltes inline-`<svg>`** (`DocumentEditorHeader.tsx`), kein
  Lucide-Icon. Das verstößt gegen Regel 3 des Design-Systems („Icons are Lucide. Never
  hand-roll SVGs."). Vorbestehend — die Datei wurde zuletzt lange vor diesem Vorhaben
  angefasst —, deshalb kein eigener Blocker gegen 7-2. Gehört aber zusammen mit Blocker 1
  repariert, dann ist es eine Zeile mehr.
- **Die leere Leiste im Bearbeiten-Zustand**: 55 px weißer Streifen über die volle Breite für
  einen einzigen Knopf, links nichts. Auf 14 Zoll kostet das 6,1 % der Höhe (der Editor
  behält 845 von 900 px, die Statuszeile bleibt sichtbar) — vertretbar, wirkt auf 1920 aber
  hohl.
- Aus früheren Abschnitten offen und unverändert (Restpunkte): zwei rosa Knöpfe im
  Lesen-Modus des Modals, `Nicht speichern` zweizeilig, PDF-Spalte frisst auf 14 Zoll zwei
  Drittel des Modals, Scroll-Kante im Formular. **Nichts davon hat sich verschlechtert.**

## Abschnitt 8-1 — Task 8a (Coding-Agent)

Zeit: 2026-09-04T23:57:23Z
Branch: lock/task-8a-toast-confirm-hook
Commit(s): d877557b (toast Position), d185a243 (confirm-dialog role=dialog), fedd90b3 (useKonfliktMeldung Variante+toter Code), b531b419 (useDatensatzLock pagehide-Fix+globale Invariante), bc887419 (LieferantDokumentModal Modal-Regel), 02376c82 (e2e-Spec)
Status: fertig

Was gemacht wurde:
- Punkt 1 (Toast-Position): toast.tsx -- Container wandert bei offenem
  Dialog jetzt nach top-6 left-6 statt top-6 right-6 (unten weiterhin
  bottom-6 right-6). Grund: 4px Luft zum Schliessen-X reichte nicht, ein
  zweizeiliger Toast ueberdeckte X und "Vorschau aktiv"; oben links traegt
  kein Modal im Projekt eine Aktion, unabhaengig von der Textlaenge.
  Mutationsprobe: dialogOffen ? 'top-6 right-6' : ... -> beide
  designPruefung-Faelle in der neuen E2E-Spec rot (toHaveClass(/left-6/)
  failed, "unexpected value ... top-6 right-6") auf pc-14zoll UND
  pc-monitor; Quellstand danach wieder byte-identisch.
- Punkt 2 (tote Toast-Tests): toast.test.tsx -- zwei neue Tests ergaenzt,
  die den Dialog ERST NACH dem Mount oeffnen/schliessen (Container wandert
  bottom-6 -> top-6 -> bottom-6 per MutationObserver, nicht nur per
  useState-Initializer) sowie ein Test fuer observer.disconnect() beim
  Unmount. Mutationsprobe: kompletten Observer-useEffect entfernt -> 3
  Tests rot (die 2 neuen plus ueberraschend auch der bestehende
  "dialogOffen=true beim Mount"-Test, weil React zum Zeitpunkt des
  useState-Initializers das DOM noch nicht committed hat -- die
  urspruengliche Erkennung lief tatsaechlich ueber den synchronen
  aktualisieren()-Aufruf im Effekt, nicht ueber den Initializer selbst).
  Nach Wiederherstellung alle 10 Tests gruen.
- Punkt 3 (confirm-dialog role="dialog"): confirm-dialog.tsx -- Panel
  traegt jetzt role="dialog", aria-modal="true", aria-labelledby auf den
  Titel (Fallback aria-label mit der Nachricht, falls kein Titel gesetzt
  ist -- title ist in ConfirmOptions optional, auch wenn alle 54
  bestehenden confirm()-Aufrufe im Projekt einen mitgeben). Roter Test
  zuerst: getByRole('dialog', { name: 'Löschen?' }) fand nichts.
  Mutationsprobe: role/aria-Attribute entfernt -> Unit-Test UND beide
  Groessen der neuen E2E-Spec ("Nicht gespeichert" nicht per
  getByRole('dialog', ...) auffindbar) rot.
- Punkt 4 (useKonfliktMeldung): variant: 'warning' (amber-Knopf) auf
  variant: 'info' umgestellt (liefert laut confirmBtnMap den geforderten
  rose-Knopf) -- confirm-dialog.tsx bietet keine eigene "amber-Icon +
  rose-Knopf"-Kombination wie UnsavedChangesModal, 'info' trifft die
  Kern-Vorgabe (gefuellte Primaeraktion rose) ohne die anderen 53
  confirm()-Aufrufe anzufassen. Toter Fallback entfernt: eigeneMeldung
  ist ein Template-String mit `bezeichnung` und damit nie leer,
  body?.message/der dritte generische Text konnten nie greifen -- der
  komplette res.json()-Parse-Block samt ApiErrorBody-Interface entfernt,
  Kommentar auf den tatsaechlichen Zweck (Server-Feld bewusst NICHT lesen)
  korrigiert. Roter Test: "Neu laden"-Knopf hatte bg-amber-500 statt
  bg-rose-600. Mutationsprobe: variant zurueck auf 'warning' -> Test rot
  (bg-amber-500 statt erwartetem bg-rose-600).
- Punkt 5 (useDatensatzLock releaseKeepalive): releaseKeepalive() zieht
  jetzt setStatus('idle')/setHalter(null)/setModus('lesen')/
  setVerbindungWeg(false) nach, nicht mehr nur heldRef.current=false.
  Zwei rote Tests zuerst: (a) nach pagehide blieb modus==='bearbeiten'/
  status==='acquired' stehen (erwartet: 'lesen'/'idle',
  kannBearbeiten===true); (b) nach pagehide+pageshow (simulierte
  bfcache-Rueckkehr) loeste ein Klick auf "Bearbeiten" kein frisches Acquire
  aus, weil modus faelschlich schon "bearbeiten" war. Mutationsprobe: Fix
  entfernt -> beide Tests rot mit exakt dieser Diskrepanz. Zusaetzlich die
  bisher nur manuell im Kettentest aufgerufene Invariante
  ("modus='bearbeiten' nie bei status!=='acquired'") als afterEach ueber
  ALLE 37 Hook-Tests gelegt (Wrapper-Hook useUeberwachterDatensatzLock
  zeichnet nach jedem Commit auf). Sanity-Mutationsprobe (zusaetzlich zur
  geforderten): den urspruenglichen Task-7b-Fix im Heartbeat-409-Zweig
  (setModus('lesen')) separat entfernt -> 22 von 37 Tests liefen rot,
  bestaetigt dass die globale Invariante auch AUSSERHALB der eigenen
  Nachbesserung greift. pageshow bekam bewusst KEINEN eigenen Listener im
  Hook -- der normale onBearbeiten()-Pfad (wie nach "Fertig") reicht,
  sobald pagehide den Zustand korrekt zuruecksetzt; automatisches
  Nachladen ohne Klick ist nicht Teil der Spec.
- Punkt 6 (LieferantDokumentModal-Regel): zeigeNurLesenHinweis von
  modus === "lesen" && status !== "locked-by-other" auf
  status === "idle" umgestellt -- dieselbe Regel wie auf der
  Dokument-Editor-Seite (DocumentEditorPage.tsx, nicht angefasst, nur
  gelesen). Zwei rote Tests zuerst: "Sie lesen nur mit." erschien
  faelschlich waehrend loading UND error. Mutationsprobe: alte Regel
  wiederhergestellt -> "bei Fehler KEINEN Hinweis"-Test rot. Bestehende
  e2e/lieferant-dokument-modal.spec.ts (8 Tests, beide Groessen) noch
  komplett gruen -- keine ihrer Zusicherungen haengt an der alten Regel,
  daher unangetastet gelassen wie im Auftrag vorgesehen.
- Neue Spec e2e/toast-bei-dialog.spec.ts (2 Tests x 2 Groessen = 4, alle
  gruen auf E2E_PORT=5181): (1) zweizeiliger Fehler-Toast bei offenem
  LieferantDokumentModal -- elementFromPoint in der Mitte des
  Schliessen-X (per aria-label) UND von "Vorschau aktiv" (per Textinhalt)
  trifft den jeweiligen Knopf, nicht den Toast; (2) Versionskonflikt --
  Confirm-Dialog "Nicht gespeichert" ist per getByRole('dialog', ...)
  auffindbar, aria-modal="true", Toast-Container steht top-6 left-6.
  Screenshots: test-results/design/toast-bei-dialog-zweizeilig--pc-14zoll.png,
  --pc-monitor.png, toast-bei-dialog-versionskonflikt--pc-14zoll.png,
  --pc-monitor.png (angeschaut: Toast oben links sauber getrennt vom
  Schliessen-X/"Vorschau aktiv", "Neu laden" im Confirm sichtbar rose statt
  amber -- formale Design-Beurteilung bleibt beim Design-Reviewer).
- Gates: npm run lint 0 Fehler/1 vorbestehende Warnung
  (BelegeKasseEditor.tsx:1204), npm run build gruen,
  src/main/resources/static/ nach jedem Build zurueckgesetzt. Eigene
  Testdateien (86 Vitest-Tests ueber 5 Dateien) gruen, eigene E2E-Spec (4
  Tests) gruen, bestehende lieferant-dokument-modal.spec.ts (8 Tests) zur
  Kontrolle mitgelaufen und weiterhin gruen.

Bedenken / Abweichungen vom Plan:
- Punkt 1/E2E: kein Ablauf in LieferantDokumentModal.tsx loest ueber echte
  Nutzeraktionen einen LAENGEREN, vom Stub gesteuerten Toast-Text aus --
  LOCK_FEHLER_TEXT und "Speichern fehlgeschlagen" sind feste, kurze Strings
  (die Produktionslogik liest an keiner Fehler-Stelle body.message in den
  Toast ein). Um den vom Design-Reviewer beschriebenen zweizeiligen Fall
  trotzdem mit ECHTEM Layout/ECHTEN CSS-Klassen nachzustellen, verlaengert
  der Test den Text des bereits ECHT ausgeloesten Toasts direkt im
  gerenderten DOM (page.evaluate, kein Eingriff in React-State/
  Produktionscode) -- dokumentiert im Datei-Kommentar der Spec. Alternative
  waere eine Erweiterung der Fehlermeldung in handleSave() um den
  Server-Text gewesen, das ginge aber ueber die 6 benannten Befunde hinaus
  und wurde bewusst nicht gemacht.
- Punkt 3/E2E "Versionskonflikt"-Test isoliert confirm-dialogs eigenen
  role="dialog"-Beitrag NICHT vollstaendig: das umgebende
  LieferantDokumentModal traegt selbst schon role="dialog", die
  Toast-Positionierung waere in diesem Ablauf auch OHNE Punkt 3 korrekt.
  Die echte Isolation (nur ein Confirm-Dialog, kein umgebendes Modal mit
  role=dialog) liefert der Unit-Test in confirm-dialog.test.tsx
  (getByRole('dialog', { name: 'Löschen?' }) direkt gegen ConfirmProvider
  ohne umgebendes Modal) -- die E2E-Spec deckt stattdessen den
  realistischen End-to-End-Ablauf ab (Confirm ist selbst per role=dialog
  auffindbar, aria-modal gesetzt, "Neu laden" sichtbar rose).
- Vorbestehende Flakiness (nicht Baustelle dieses Tasks, nicht angefasst):
  LieferantDokumentModal.test.tsx > "zeigt Hinweis im Modal UND Toast"
  schlaegt gelegentlich (~1 von 6 Laeufen, auch auf dem unveraenderten
  Basisstand vor Task 8a reproduziert) mit "expected length 2 but got 3"
  fehl -- vermutlich ein doppelt ausgeloester Toast durch einen
  React-Effekt-Rand-Fall, unabhaengig von den hier gemachten Aenderungen.
- useDatensatzLock.test.tsx: die Invariante wird jetzt als afterEach global
  durchgesetzt; die urspruengliche, manuell aufgerufene
  pruefeInvariante()-Stelle im Kettentest wurde bewusst NICHT entfernt
  (dokumentiert exakt die Stelle, an der der Task-7b-Befund auftrat) --
  beide Mechanismen laufen jetzt parallel, keine Ueberschneidung/Konflikt.

## Abschnitt 7-2 — Task 7a Nachbesserung 1 (Coding-Agent)

**Zeit:** 05.09.2026, ca. 01:15–02:05 Uhr (lokale Sessionzeit)
**Branch:** `lock/task-7a-editor-seite` (Merge von `claude/eloquent-ramanujan-gz0w2t` @ `256b3e1d` vorab per `git merge --no-edit`, konfliktfrei)
**Commits:**
- `5425a8ca` — fix(dokument-editor): Gebucht-Badge, Schließen-Knopf-Label, Leiste-Layout (Nachbesserung 1, Abschnitt 7-2)
- `13888ed6` — test(dokument-editor): Tests für Nachbesserung 1 (Gebucht-Badge, Leiste-Layout, useDatensatzLock)
- `db735d7a` — docs(dokument-editor-seite): Kommentare von den Namen des alten Sperr-Hooks/-Modals gelöst

**Status:** 🟢 fertig aus meiner Sicht — alle drei Befunde (2× 🔴, 3× 🟡) behoben, eigene Gates grün.

### Befund 1 — "Gebucht" auf nicht gebuchtem Dokument

**Roter Test zuerst** (`document-editor/index.test.tsx`, neue Describe `"Gebucht"-Badge`):
- "zeigt 'Gebucht' NICHT bei Fremdsperre (readOnly=true)..." — vor dem Fix rot (`queryByText('Gebucht')` fand das Badge, weil es an `isLocked` hing).
- "zeigt 'Gebucht' weiterhin für eine tatsächlich gebuchte Rechnung".
- "zeigt 'Gebucht' NICHT für ein storniertes Dokument" — storniert trägt nur sein eigenes Badge.

Fix: `document-editor/index.tsx` berechnet jetzt `istGebuchteRechnung = !!(dokument?.gebucht && dokument?.typ && invoiceTypes.includes(dokument.typ))` als eigene Variable (vorher inline in `isLocked`), gibt sie als neuen Prop `istGebucht` an `DocumentEditorHeader` weiter. `DocumentEditorHeader.tsx`: Badge-Bedingung von `isLocked` auf `istGebucht` umgestellt, handgemaltes Lock-`<svg>` durch Lucide `Lock` ersetzt (Design-System-Regel — Icon-Vorbild: `DokumentUebersichtEditor.tsx`/`DokumentVerlaufDrawer.tsx`, dieselbe Kombination "Gebucht" + `Lock`).

**Mutationsprobe:** Badge-Bedingung testweise zurück auf `isLocked` gesetzt → beide Regressions-Tests (Fremdsperre, storniert) wieder rot — zurückgesetzt, grün.

### Befund 2 — Warn-Dialog blockiert die Leiste nicht

**Roter Test zuerst** (`e2e/dokument-editor-seite.spec.ts`, neuer Fall "Warn-Dialog blockiert die Bearbeiten-Leiste"): öffnet mit freiem Lock, ändert die Adresse, klickt X, misst `document.elementFromPoint` in der Mitte des "Fertig"-Knopfes bei offenem "Ungespeicherte Änderungen"-Dialog — muss `false` sein (Backdrop, nicht der Knopf).

**Gewählter Layout-Weg** (wie vom Review vorgeschlagen): kein `transform` mehr auf dem Container um `<DocumentEditor>`. Stattdessen:
- `DocumentEditorPage.tsx` misst die Höhe der eigenen Leiste per **Callback-Ref** (State statt `useRef`) + `ResizeObserver` und setzt sie als CSS Custom Property `--lock-leiste-hoehe` auf dem gemeinsamen Vorfahren (`style={{'--lock-leiste-hoehe': ...}}`).
- `document-editor/index.tsx`: die beiden `fixed inset-0`-Wurzeln (Ladezustand + Hauptrendering) heißen jetzt `fixed inset-x-0 bottom-0 top-[var(--lock-leiste-hoehe,0px)]` — der Editor bleibt `position:fixed` relativ zum ECHTEN Viewport, seine eigenen Dialoge (`z-[70]`+) liegen also wieder über allem inkl. der Leiste. Ohne gesetzte Variable (jeder andere Verwender/Test) Fallback `0px` = bisheriges `inset-0`-Verhalten.

**Zwei eigene rote Bugs unterwegs gefunden und behoben, bevor der Fix stand** (beide durch denselben Befund-2-Test aufgedeckt, nicht separat angefordert, aber notwendig, damit der rote Test überhaupt grün werden konnte):
1. Erste Fassung nutzte `useRef` + `useEffect(..., [zeigeLeiste])` — `zeigeLeiste = hatId && !schliesstGerade` steht bei einem Dokument MIT Id schon VOR der Vollbild-Ladeanzeige auf `true` und ändert sich beim Übergang "Ladeanzeige weg, Leiste erscheint" nicht → der Messeffekt lief nie erneut, `--lock-leiste-hoehe` blieb bei `0px` hängen. Playwright zeigte dabei den Editor-Kopf direkt bei y=0, die Leiste komplett verdeckt (`Fertig` überlappte `E-Mail`/`PDF`). Fix: Callback-Ref (`setLeisteElement` direkt als `ref`), der garantiert genau dann feuert, wenn React den DOM-Knoten an-/abhängt.
2. Nach dem Callback-Ref-Fix blieb ein zweiter, subtilerer Bug: der `ResizeObserver`-Callback maß `entries[0].contentRect.height` (34px, ohne `px-4 py-2.5`-Padding und `border-b`) statt der tatsächlichen, sichtbaren Höhe (55px per `getBoundingClientRect()`) — der Editor-Kopf ragte um die Differenz (21px) in die Leiste hinein, exakt sichtbar als "Fertig überlappt E-Mail/PDF" im automatischen Design-Check. Per Diagnose-Spec (ResizeObserver-Wrapper mit Logging, danach wieder gelöscht) nachgewiesen: `RO fired [34]` überschrieb den korrekten initialen Wert (55). Fix: `getBoundingClientRect()` durchgängig, auch im ResizeObserver-Callback.

**Mutationsprobe:** `[transform:translateZ(0)]` testweise wieder auf den Editor-Container gesetzt → der neue Test wird rot (Klick auf die Adresse schon vorher blockiert, da die Leiste wieder alles verdeckt) — zurückgesetzt, grün, 12/12 in der eigenen Spec.

### Befund 3 — Reihenfolge setSchliesstGerade/await

Geprüft: `onLockFreigebenFuerSchliessen` hatte `setSchliesstGerade(true)` bereits VOR dem `await lock.freigeben()` (seit meinem ursprünglichen Fix in Abschnitt 7-1). Der Code war korrekt — aber **ungetestet in genau dieser Reihenfolge**: alle bisherigen Tests prüften nur den EINGESCHWUNGENEN Endzustand nach `waitFor(...)`, nicht das Verhalten UNMITTELBAR nach dem Klick.

**Roter Test nachgezogen** (`DocumentEditorPage.test.tsx`, "blendet die Leiste SYNCHRON aus..."): `fireEvent.click(...)` in einem synchronen `act()`-Block (bewusst kein `userEvent.click`, das intern zusätzlich awaited), Assertion sofort danach, ohne `waitFor`. Zuerst mit der (unveränderten) korrekten Implementierung grün, dann per Mutationsprobe verifiziert: Reihenfolge testweise vertauscht (`await lock.freigeben(); setSchliesstGerade(true);`) → Test wird rot (`Fertig` war zum Zeitpunkt der Prüfung noch im DOM) — zurückgesetzt, grün. Damit ist die Reihenfolge jetzt tatsächlich abgesichert, nicht nur zufällig richtig.

### Befund 4 — X-Knopf ohne aria-label

`DocumentEditorHeader.tsx`: `aria-label="Editor schließen"` ergänzt. `index.test.tsx`s `xKnopf()`-Helfer von `container.querySelector('button')` (erster Button) auf `within(container).getByRole('button', { name: 'Editor schließen' })` umgestellt, Docstring aktualisiert. `e2e/dokument-editor-seite.spec.ts`s `xKnopf()` ebenso auf `page.getByRole('button', { name: 'Editor schließen' })` — die testid-Eingrenzung auf `dokument-editor-flaeche` (mein eigener Workaround aus Abschnitt 7-1 gegen die Mehrdeutigkeit mit der neuen Leiste) ist damit überflüssig geworden, da der Name jetzt eindeutig ist.

### Befund 5 — index.test.tsx von useDocumentLock lösen

`document-editor/index.test.tsx`: Import von `useDocumentLock` auf `useDatensatzLock` (`../lock/useDatensatzLock`) umgestellt. `SeiteMitAltemLock` → `SeiteMitSeitenLock` umbenannt, ruft jetzt `useDatensatzLock('AUSGANG', dokumentId)`. `mockFetchNeuesDokument()`: Acquire-/Heartbeat-Stubs von `/api/dokument-locks/AUSGANG/42/...` auf `/api/datensatz-locks/AUSGANG/42/...` umgestellt (PUT-409-Simulation unverändert — das ist ein Backend-Verhalten beim Speichern, unabhängig vom Lock-Hook-Typ). `lockAufrufe()`-Helfer vereinfacht (nur noch `/datensatz-locks/`). Alle Kommentare, die den alten Hook/die alte Route beim Namen nannten, umformuliert (auch dort, wo sie nur erklärend auf die Historie verwiesen — der Grep unten ist eine reine Textsuche und unterscheidet nicht zwischen Code und Prosa).

**Mutationsprobe:** `useDatensatzLock('AUSGANG', dokumentId);`-Aufruf in `SeiteMitSeitenLock` testweise auskommentiert → "kann ein frisch angelegtes Dokument auch nach mehr als 90s noch speichern" wird rot (`expected false to be true` bei der Akquiriert-Prüfung) UND `npm run lint` meldet zusätzlich `'useDatensatzLock' is defined but never used` — zurückgesetzt, beides grün.

**Grep-Ergebnis** (`grep -rn "useDocumentLock\|DocumentLockedModal\|dokument-locks" react-pc-frontend/src`), nach allen Fixes:
```
src/components/DocumentLockedModal.tsx:3:import type { DocumentLockHolder } from './useDocumentLock';
src/components/DocumentLockedModal.tsx:5:interface DocumentLockedModalProps {
src/components/DocumentLockedModal.tsx:15:export default function DocumentLockedModal({
src/components/DocumentLockedModal.tsx:20:}: DocumentLockedModalProps) {
src/components/LieferantDokumentModal.test.tsx:13: * GesperrtHinweis statt useDocumentLock/DocumentLockedModal). Fetch wird wie
src/components/lock/GesperrtHinweis.tsx:26: * DocumentLockedModal ab.
src/components/lock/useDatensatzLock.ts:7: * Fassung von useDocumentLock (siehe src/components/useDocumentLock.ts, der
src/components/useDocumentLock.ts:58:export function useDocumentLock(
src/components/useDocumentLock.ts:76:        const lockUrl = `/api/dokument-locks/${dokumentTyp}/${dokumentId}`;
```
Nur noch die beiden alten Dateien selbst (`DocumentLockedModal.tsx`, `useDocumentLock.ts`) plus drei reine Prosa-Kommentare in Dateien, die **nicht** in meiner Dateiliste dieser Runde stehen (`LieferantDokumentModal.test.tsx`, `GesperrtHinweis.tsx`, `useDatensatzLock.ts`) — siehe Bedenken.

### Gates (nur eigene Dateien/eigene Specs, wie vorgegeben)

- `npx vitest run src/components/document-editor/index.test.tsx src/pages/DocumentEditorPage.test.tsx src/components/lock/TabSchliessenHinweis.test.tsx` → **45/45 grün** (25 + 12 + 8). Ein einzelner Ausreißer ("hält die Reihenfolge speichern -> onLockFreigeben -> window.close ein", 5000ms-Timeout) trat einmal unter Systemlast auf (Hintergrund-Testlauf parallel) und war beim isolierten sowie beim erneuten kombinierten Lauf grün — kein Befund, siehe Bedenken.
- `npm run lint` → **0 Fehler, genau die 1 vorbestehende Warnung** (`BelegeKasseEditor.tsx:1204`).
- `npm run build` → grün, `src/main/resources/static/` danach zurückgesetzt.
- `E2E_PORT=5178 npx playwright test e2e/dokument-editor-seite.spec.ts` → **12/12 grün** (6 Fälle × `pc-14zoll`/`pc-monitor`, neu: "Warn-Dialog blockiert die Bearbeiten-Leiste").
- `E2E_PORT=5178 npx playwright test e2e/dokument-editor-tab-schliessen.spec.ts` (6a-Spec, wie vorgegeben mitgefahren, da Layout/Header berührt) → **6/6 grün** — dank des bereits gemergten Locator-Fixes (`256b3e1d`) unbeeinträchtigt von der neuen Leiste.
- Beide Specs zusammen auf demselben Port → **18/18 grün**.

### Screenshots (kurz selbst angeschaut)

`react-pc-frontend/test-results/design/editor-seite-warn-dialog-blockiert-leiste--pc-14zoll.png` (+ `--pc-monitor`): der abgedunkelte Backdrop des Warn-Dialogs liegt jetzt sichtbar ÜBER der gesamten Leiste inklusive des "Fertig"-Knopfes (gedimmt, nicht klickbar) — genau der Soll-Zustand. `editor-seite-bearbeiten`/`-lesen`/`-gesperrt`/`-fehler` erneut angeschaut: Leiste und Editor-Kopf sitzen sauber übereinander, keine Lücke, kein Überlapp.

### Bedenken / Abweichungen vom Plan

1. **Drei verbleibende Grep-Treffer außerhalb meiner Dateiliste** (`LieferantDokumentModal.test.tsx`, `GesperrtHinweis.tsx`, `useDatensatzLock.ts`) — reine Kommentar-Erwähnungen des alten Hook-/Modal-Namens, keine Imports/Aufrufe. Nicht angefasst, da nicht in "Files für diese Runde". Blockieren Abschnitt 8 nicht (dort geht es um das LÖSCHEN der beiden alten Dateien selbst, nicht um Kommentar-Hygiene anderswo), aber falls der Nutzer/Reviewer eine wirklich lückenlose Grep-Stille über den ganzen Baum will, bräuchte es einen eigenen (trivialen) Nachtrag für genau diese drei Kommentarzeilen.
2. **Layout-Fix ist umfangreicher als eine reine CSS-Zeile geworden** (Callback-Ref + `getBoundingClientRect()` statt `contentRect`) — beide Zusatzpunkte waren nicht im Auftrag genannt, aber ohne sie hätte der vorgeschlagene Weg selbst nicht funktioniert (siehe die zwei "unterwegs gefundenen" Bugs oben). Ausführlich im Code kommentiert, damit ein künftiger Leser nicht denselben Fehler wiederholt (insbesondere `contentRect` vs. `getBoundingClientRect()` ist eine leicht wiederholbare Falle).
3. **Ein einzelner flakiger Testausreißer** unter Systemlast (siehe Gates) — kein Befund, mehrfach reproduzierbar grün, gehört zur bekannten Charakteristik dieser Testdatei (echte Timer, `userEvent`, viele schwere `DocumentEditor`-Mounts) und nicht zu dieser Nachbesserung.
4. Sonst keine Abweichungen — alle fünf Punkte wie im Auftrag beschrieben umgesetzt.

## Abschnitt 7-2/8-1 — Design-Review 2 (Design-Reviewer)

**Ampel: 🟡** — beide 🔴 aus Durchgang 1 sind behoben, im Browser nachgemessen, nicht
geglaubt. Was bleibt, sind Hinweise: der Toast oben links schneidet auf 14 Zoll die
Modal-Überschrift an, und die Konfliktmeldung trägt jetzt ein himmelblaues Fragezeichen.
Beides blockiert nicht.

Worktree `wt/review-design`, Stand `89ffc0d5`. `E2E_PORT=5190 npm run test:e2e`:
**110 Tests, alle grün**, beide Größen `pc-14zoll` (1440×900) und `pc-monitor` (1920×1080).
Sechs mehr als in 7-2 (104): der neue Warn-Dialog-Fall plus die vier aus
`toast-bei-dialog.spec.ts`. Kein Flattern, keine Wiederholung nötig, auch `website-*`
lief in einem Rutsch durch.

### 🔴 1 aus Durchgang 1 — „Gebucht" auf nicht gebuchtem Dokument: **behoben**

Eigene Wegwerf-Spec, `document.querySelectorAll` über den ganzen Baum nach dem Blatttext
`Gebucht`, in jedem Zustand, beide Größen:

| Zustand | Treffer „Gebucht" |
| --- | --- |
| `bearbeiten` | **0** |
| `lesen` (nach eigenem „Fertig") | **0** |
| `gesperrt` (Fremdsperre 409) | **0** |
| `fehler` (Acquire 500) | **0** |
| bei offenem Warn-Dialog | **0** |

Gegenprobe mit `gebucht: true` auf demselben Stub (Typ `RECHNUNG`) — das Badge ist da und
sieht aus wie vorher, nur mit richtigem Icon:

| | Wert (identisch 14 Zoll / 1920) |
| --- | --- |
| Rahmen | x 188, y 66, 70 × 21 |
| Hintergrund / Text / Rand | `rgb(255,251,235)` amber-50 / `rgb(180,83,9)` amber-700 / `rgb(253,230,138)` amber-200 |
| Icon | `class="lucide lucide-lock w-2.5 h-2.5"` |

Damit ist zugleich der 🟡 aus Durchgang 1 erledigt: das handgemalte inline-`<svg>` im Kopf
ist weg, es ist ein echtes Lucide-`Lock`. Regel 3 des Design-Systems wieder eingehalten.

### 🔴 2 aus Durchgang 1 — Warn-Dialog blockiert die Leiste nicht: **behoben**

Bei offenem „Ungespeicherte Änderungen", eigene Messung:

| | pc-14zoll | pc-monitor |
| --- | --- | --- |
| `Fertig` in der Leiste | [1339, 10, 85, 34] | [1819, 10, 85, 34] |
| `elementFromPoint` in dessen Mitte | `DIV.fixed inset-0 z-[70] bg-black/40 backdrop-blur-sm` | dasselbe |
| `…closest('button')` | **null** | **null** |
| echter Mausklick auf denselben Punkt ⇒ DELETE auf die Sperre | **0** | **0** |
| Dialog danach | steht weiter offen | steht weiter offen |

Der Scrim liegt jetzt über der Leiste, nicht mehr darunter. Der Weg „zwei Klicks bis zu
zwei sich widersprechenden Aussagen" ist zu.

Und die Leiste sitzt weiterhin bündig über dem Editor — der `transform`-Container ist durch
die gemessene CSS-Variable ersetzt, ohne dass sich das Layout verschoben hat:

| Zustand | Leiste | `--lock-leiste-hoehe` | Editor beginnt bei |
| --- | --- | --- | --- |
| bearbeiten / lesen / Warn-Dialog | [0, 0, B, **55**] | `55px` | y **55** |
| gesperrt / fehler (Band ist höher) | [0, 0, B, **59**] | `59px` | y **59** |

Identisch auf 1440 und 1920 (B = Viewport-Breite). Kein Überlappen, kein Spalt, keine
Lücke — dieselben Zahlen wie in Durchgang 1, nur ohne die Nebenwirkung auf die Modale.
Der X-Knopf trägt jetzt `aria-label="Editor schließen"`, die Spec greift ihn darüber.

### Die sechs Fragen je Screenshot und Größe

**`editor-seite-bearbeiten` (14 Zoll + 1920)**
1. *Farben?* Leiste weiß, `Fertig` weißer Outline-Knopf mit rose-Rand, `PDF` im Editor-Kopf
   die einzige gefüllte rosa Fläche. Genau eine Primäraktion. Kein Badge.
2. *Design-System?* rose/slate, Lucide, kein Emoji, Systemschrift
   (`system-ui, -apple-system, sans-serif`, kein `@font-face`, kein Google-Fonts-Import).
3. *Look-and-Feel?* Ruhig. Links ist die Leiste weiterhin leer — auf 1920 ein 1920 px
   breiter Streifen für einen Knopf. Bekannter 🟡, unverändert.
4. *UX?* Editor voll bedienbar, `Fertig` klar sekundär, Zahlungsziel als Eingabefeld.
5. *Auffindbar?* Ja, [1339, 10] bzw. [1819, 10], ohne Scrollen, `elementFromPoint` trifft
   den Knopf selbst.
6. *Überschneidung?* Nein. 55/55, kein horizontaler Überlauf.

**`editor-seite-lesen` (14 Zoll + 1920)**
1. *Farben?* „Sie lesen nur mit." slate-500 neben rose-600 `Bearbeiten`. **Zwei gefüllte
   rosa Flächen** auf dem Screen (`Bearbeiten` und `PDF`) — bekannter Restpunkt, unverändert.
   Kein „Gebucht" mehr.
2. *Design-System?* Ja, jetzt auch beim Icon (siehe oben).
3. *Look-and-Feel?* Ruhig, Werkzeugleiste des Editors korrekt reduziert.
4. *UX?* Zustand benannt, Weg zurück sichtbar. Ehrlich, seit das falsche Badge weg ist.
5. *Auffindbar?* Ja, [1190, 10] bzw. [1670, 10] (Hinweis + Knopf als Block).
6. *Überschneidung?* Nein. 55/55.

**`editor-seite-gesperrt` (14 Zoll + 1920)**
1. *Farben?* Volles rose-50-Band mit Lucide `Lock`: „Anna Beispiel bearbeitet das gerade —
   Sie sehen den aktuellen Stand. Seit 5 Min.", daneben rose-600 `Bearbeiten`. Klar getrennt
   vom roten Fehlerband und vom leeren Bearbeiten-Zustand.
2. *Design-System?* Ja.
3. *Look-and-Feel?* Das Band füllt die Leiste auch auf 1920 sinnvoll.
4. *UX?* Klartext mit Namen und Dauer, `Bearbeiten` bleibt aktiv (Übernahmeversuch),
   Zahlungsziel als Text statt Eingabefeld.
5. *Auffindbar?* Ja, am Kopf, ohne Scrollen.
6. *Überschneidung?* Nein. Leiste 59, Editor ab 59.

**`editor-seite-fehler` (14 Zoll + 1920)**
1. *Farben?* Rotes Band mit `AlertTriangle`, `Bearbeiten` deaktiviert (rose bei 50 %).
   Störung und Hinweis sind auf einen Blick verschieden.
2. *Design-System?* Ja.
3. *Look-and-Feel?* Ruhig.
4. *UX?* Meldung dreifach: Band (`role=alert`), Toast, Tooltip am deaktivierten Knopf
   (`title="Sperre konnte nicht geholt werden — bitte neu laden."`, von der Spec zugesichert).
5. *Auffindbar?* Ja.
6. *Überschneidung?* Nein. **Wichtig für 8a:** ohne offenen Dialog bleibt der Toast unten
   rechts — Container-Klasse `bottom-6 right-6`, gemessen [978, 830, 438, 46] auf 14 Zoll
   und [1458, 1010, 438, 46] auf 1920. `elementFromPoint` auf **Netto**, **MwSt** und
   **Brutto** trifft jeweils das eigene SPAN, nie den Toast. Der Umzug nach oben links
   greift also wirklich nur bei offenem Dialog.
   *(Auf den Screenshots dieses Laufs ist der Toast nicht zu sehen — er läuft nach 5 s aus,
   und die Zusicherungen davor haben unter Systemlast länger gedauert. Nachgemessen ist er
   da, siehe Zahlen oben.)*

**`editor-seite-warn-dialog-blockiert-leiste` (14 Zoll + 1920, neu)**
1. *Farben?* Amber-Warn-Icon, `Speichern & Schließen` als einzige gefüllte rose-Fläche im
   Dialog, `Abbrechen` slate-Outline, `Nicht speichern` rose-Outline.
2. *Design-System?* `rounded-2xl`, `shadow-2xl`, Lucide `AlertTriangle`, kein Emoji.
3. *Look-and-Feel?* Der abgedunkelte, weichgezeichnete Hintergrund liegt sichtbar über der
   **gesamten** Leiste inklusive `Fertig` — genau das, was in Durchgang 1 gefehlt hat.
4. *UX?* Drei Wege, einer davon offensichtlich der Hauptweg.
5. *Auffindbar?* Ja, mittig, ohne Scrollen.
6. *Überschneidung?* Nein — und der Klick geht nachweislich nicht mehr durch (Messwerte oben).

**`editor-seite-tab-schliessen` (14 Zoll + 1920)**
1. *Farben?* rose-100-Kreis mit rose-600 `CheckCircle2` auf slate-50, Text slate-700.
2. *Design-System?* Ja.
3. *Look-and-Feel?* Aufgeräumte Vollbild-Bestätigung.
4. *UX?* Warnung ⇒ Speichern ⇒ Hinweisseite, über die echte Route.
5. *Auffindbar?* Mittig, nichts zu suchen.
6. *Überschneidung?* Nein, und die Leiste ist weg (`schliesstGerade` sitzt).

**`toast-bei-dialog-zweizeilig` (14 Zoll + 1920, neu)**
1. *Farben?* red-50/red-200-Toast mit `XCircle`, klar als Störung lesbar, klar getrennt vom
   roten Band im Modal.
2. *Design-System?* Ja, `rounded-xl`, `shadow-lg`, Lucide.
3. *Look-and-Feel?* Der Toast steht halb auf dem Ribbon, halb auf dem Modal — etwas
   beliebig, aber der Ribbon ist hinter dem Scrim ohnehin nicht bedienbar.
4. *UX?* Der Fehler steht doppelt (Band im Modal, Toast) — das ist gewollt.
5. *Auffindbar?* Ja, der Blick geht beim Öffnen ohnehin nach oben; unten rechts bleibt
   `Speichern` frei. Frage beantwortet: ein Fehler-Toast oben links **ist** im Blick,
   während man unten rechts auf `Speichern` schaut — der Weg dorthin führt am Toast vorbei.
6. *Überschneidung?* **Nur teilweise gut.** Gemessen, zweizeiliger Toast [24, 24, 480, 66]:
   - 1920: Modal [160, 66, 1600, 972], Titel „Dokument bearbeiten" [185, 87, 179, 28],
     Eyebrow „PDF-Vorschau" [380, 91, 86, 20]. `elementFromPoint` in der Mitte des Titels
     trifft **H2 „Dokument bearbeiten"**, beim Eyebrow **SPAN „PDF-Vorschau"**. Sauber.
   - 14 Zoll: Modal [36, 57, 1368, 810], Titel [61, **78**, 179, 28], Eyebrow [256, 82, 86, 20],
     Toast endet bei y **90** ⇒ **12 px Überlappung**; `elementFromPoint` in der Mitte von
     Titel **und** Eyebrow trifft den **Toast**. Siehe Hinweise.
   Von der App dahinter verdeckt er Logo und die ersten zwei Ribbon-Reiter — beides liegt
   hinter dem Scrim und ist in diesem Moment nicht bedienbar. Kein horizontaler Überlauf.

**`toast-bei-dialog-versionskonflikt` (14 Zoll + 1920, neu)**
1. *Farben?* `Neu laden` ist jetzt **rose-600 gefüllt** statt amber-500 — der 🟡 aus
   Abschnitt 6 ist damit erledigt. **Aber** das Icon ist ein `HelpCircle` in
   sky-100/sky-600. Siehe Hinweise.
2. *Design-System?* `rounded-2xl`, Lucide, kein Emoji, Systemschrift — bis auf das
   Sky-Blau, das im Farbschema nicht vorgesehen ist.
3. *Look-and-Feel?* Kompakter, mittiger Dialog, `Abbrechen` links, `Neu laden` rechts.
4. *UX?* „Nicht gespeichert. Jemand anders hat dieses Dokument gerade gespeichert. Ihre
   Änderungen wurden nicht übernommen — bitte neu laden." Handwerker-Sprache, Sie-Form,
   zwei klare Wege. Kein Toast in diesem Ablauf (es gibt keinen).
5. *Auffindbar?* Ja, mittig.
6. *Überschneidung?* Nein, auf beiden Größen. Der Toast-Container steht `top-6 left-6`
   (von der Spec zugesichert), ist hier aber leer.

**`leiste-bearbeiten` / `-lesen` / `-countdown` / `-verbindung-weg` / `-deaktiviert` (je beide Größen)**
1.–6. Unverändert gut: amber-Countdown mit `Timer` („Wird in 57 Sekunden freigegeben —
bewegen Sie die Maus, um weiterzuarbeiten."), rotes `WifiOff`-Band, Umschalter an fester
Stelle, kein Sprung. `leiste-lesen` zeigt „Sie lesen nur mit." — dort stehen weiterhin zwei
rosa Flächen (`Bearbeiten` und das deaktivierte `Speichern`), bekannter Restpunkt.
`leiste-deaktiviert` zeigt den Toast jetzt **oben links** [24, 24, 437, 46]: 22 px Luft zur
Modal-Überschrift bei y 92, kein Kontakt — einzeilig ist die neue Position sauber.

**`lieferant-modal-lesen-hinweis` (beide Größen)**
1.–6. „Sie lesen nur mit." füllt die linke Bandhälfte, `Bearbeiten` rose-600 daneben.
Keine Überschneidung, `Speichern` unten rechts deaktiviert und sichtbar ausgegraut.

**`lieferant-modal-fehler-tooltip` und `-fehler` (beide Größen)**
1.–6. **Der 🟡 aus Durchgang 1 ist erledigt:** „Sie lesen nur mit." steht nicht mehr neben
dem roten Band, das Band hat wieder die volle Breite (x 61 → 1250 auf 14 Zoll statt
1132). Die dringende Meldung weicht der beiläufigen nicht mehr. Sonst unverändert.

**`lieferant-modal-bearbeiten` / `-fremdes-lock` (beide Größen)**
1.–6. Unverändert gegenüber 7-2. Fremdsperre mit Namen und Dauer, Eingaben gesperrt.

**`lieferant-modal-speicherfehler-toast` (beide Größen)**
1.–5. Toast jetzt oben links, `Speichern` unten rechts frei und klickbar — das war der
Zweck von 8a und er ist erreicht.
6. Neu aufgefallen (nicht von diesem Abschnitt verursacht): das **inline** rote Band
„Speichern fehlgeschlagen" am Fuß der Formularspalte legt sich auf 14 Zoll über die
Überschrift „Zahlungsbedingungen" und auf 1920 über die Eingabezeile
Skonto % / Skonto Tage / Netto Tage. Siehe Hinweise.

**`dokument-editor-vor-schliessen` / `-ungespeichert-warnung` (beide Größen)**
1.–6. Unverändert. `vor-schliessen` zeigt die Leiste am Kopf, bündig. In
`-ungespeichert-warnung` deckt der Scrim jetzt auch hier die ganze Leiste ab.

### 💡 Hinweise (blockieren nicht)

- **Der zweizeilige Toast schneidet auf 14 Zoll die Modal-Überschrift an** (12 px, Messwerte
  oben; `elementFromPoint` auf Titel und Eyebrow trifft den Toast). Kein Knopf ist betroffen,
  keine Aktion blockiert, nach 5 s ist es vorbei — deshalb 🟡 und nicht 🔴: das Verdecken
  des Schließen-X, das diesen Umzug ausgelöst hat, war in Abschnitt 6 selbst als 🟡 geführt,
  und ein Titel wiegt weniger als ein Knopf. Sauber wird es erst **unten links**: dort ist in
  `LieferantDokumentModal` (Fußleiste rechts) und im Confirm-Dialog (Knöpfe mittig/rechts)
  nichts, und die Ecke ist unabhängig von der Textlänge frei. Nach oben ausweichen geht auf
  14 Zoll nicht — das Modal beginnt bei y 57, ein 66 px hoher Toast reicht selbst bei
  `top-2` bis y 74.
- **Die Konfliktmeldung trägt jetzt ein himmelblaues Fragezeichen.** `variant: 'info'` liefert
  den geforderten rose-Knopf, aber eben auch `HelpCircle` in `sky-100`/`sky-600` — und
  „Nicht gespeichert, Ihre Änderungen wurden nicht übernommen" ist keine freundliche Frage,
  sondern ein Fehlschlag. Vorher war es ein amber `AlertTriangle`, also die richtige
  Semantik mit dem falschen Knopf; jetzt ist es der richtige Knopf mit dem falschen Icon.
  Kein 🔴, weil (a) genau dieser Weg im Review von Abschnitt 6 vorgeschlagen wurde, (b)
  `variant: 'info'` mit demselben blauen Icon schon in fünf anderen Dialogen des Produkts
  läuft (Urlaubsanträge, ProjektEditor, BestellungEditor, Kostenpositionen,
  Reklamationen) — es ist keine neu erfundene Fremdfarbe. Sauber wäre eine vierte Variante
  in `confirm-dialog.tsx`: amber-Icon **und** rose-Knopf, genau wie es `UnsavedChangesModal`
  im selben Editor schon macht. Dann stimmt beides.
- **Kein E2E-Screenshot eines tatsächlich gebuchten Dokuments.** Die Abwesenheit des Badges
  ist in fünf Zuständen belegt, die Anwesenheit nur per Unit-Test
  (`document-editor/index.test.tsx`) und per meiner Wegwerf-Messung. Ein Stub mit
  `gebucht: true` in `dokument-editor-seite.spec.ts` wäre eine Zeile und würde den positiven
  Fall dauerhaft sichtbar halten.
- **Inline-Fehlerband im Lieferanten-Modal überlappt Formularinhalt** (siehe oben). Das Band
  ist ein einfaches `div` ohne `role`, deshalb sieht es die automatische
  Überschneidungsprüfung nicht. **Vorbestehend** — der Diff dieses Abschnitts fasst an
  `LieferantDokumentModal.tsx` nur die Hinweis-Regel an. Gehört auf die Restpunkte-Liste,
  nicht in diese Runde.
- Aus früheren Abschnitten offen und **unverändert**: leere 55-px-Leiste im
  Bearbeiten-Zustand (auf 1920 hohl), zwei rosa Knöpfe im Lesen-Modus des Modals,
  `Nicht speichern` zweizeilig, PDF-Spalte frisst auf 14 Zoll zwei Drittel des Modals.
  **Nichts davon hat sich verschlechtert.**

### Angeschaute Screenshots

Alle 42 aus `react-pc-frontend/test-results/design/`, jeweils `--pc-14zoll` **und**
`--pc-monitor`:

1. `editor-seite-bearbeiten` · 2. `editor-seite-lesen` · 3. `editor-seite-gesperrt` ·
4. `editor-seite-fehler` · 5. `editor-seite-tab-schliessen` ·
6. `editor-seite-warn-dialog-blockiert-leiste` (neu) ·
7. `toast-bei-dialog-zweizeilig` (neu) · 8. `toast-bei-dialog-versionskonflikt` (neu) ·
9. `leiste-bearbeiten` · 10. `leiste-lesen` · 11. `leiste-countdown` ·
12. `leiste-verbindung-weg` · 13. `leiste-deaktiviert` ·
14. `lieferant-modal-bearbeiten` · 15. `lieferant-modal-lesen-hinweis` ·
16. `lieferant-modal-fremdes-lock` · 17. `lieferant-modal-fehler` ·
18. `lieferant-modal-fehler-tooltip` · 19. `lieferant-modal-speicherfehler-toast` ·
20. `dokument-editor-vor-schliessen` · 21. `dokument-editor-ungespeichert-warnung`

Dazu eine eigene Wegwerf-Spec (`e2e/zz-review-messung.spec.ts`, 6 Fälle × beide Größen,
12/12 grün) für die Zahlen oben: Badge-Zählung in fünf Zuständen, gebuchte Gegenprobe,
`elementFromPoint` + echter Mausklick gegen den Warn-Dialog, Toast-Rahmen und
Container-Klasse in beiden Lagen, Rahmen von Toast/Titel/Eyebrow/Modal. **Danach gelöscht,
`git status` im Worktree ist leer.**

## Abschnitt 7-2/8-1 — Code-Review 2 (Code-Reviewer)

**Zeit:** 05.09.2026, ca. 13:20–15:10 Uhr (lokale Sessionzeit)
**Branch:** `claude/eloquent-ramanujan-gz0w2t`, geprüfter Stand `89ffc0d5` (HEAD stand am Ende auf `70fd301a` — nur Doku, kein Quellcode dazwischen)
**Status:** fertig
**Ampel: 🟡** — kein blockierender Befund. Von meiner Seite abgenommen.

### Selbst gemessene Zahlen

| | Baseline (nach 7-2, 1. Durchgang) | jetzt |
|---|---|---|
| Backend | 2462 / 0 Failures / 4 Errors | **2462 / 1 Failure\* / 4 Errors** |
| Frontend Testdateien | 88 | **88** |
| Frontend Tests | 1071 | **1082** (+11) |
| Lint | 0 Fehler, 1 Warnung | **0 Fehler, 1 Warnung** (`BelegeKasseEditor.tsx:1204`) |
| `npm run build` | grün | **grün**, `src/main/resources/static/` danach zurückgesetzt |

\* `UnifiedEmailControllerExtractEmailTest.adversarialInputWithoutAt_isLinear` („execution timed out
after 500 ms") — Test mit harter Zeitschranke in einer in dieser Runde **nicht angefassten** Datei
(die Runde ändert keine einzige `.java`-Datei, geprüft per `git diff --name-only`). Einzeln
nachgefahren: **12/12 grün in 0,383 s**. Kein Befund, Last-Effekt (der Design-Reviewer fuhr parallel
Dev-Server und Browser). Die 4 Errors sind namentlich die bekannten umgebungsbedingten
(`AuditChainRepairIntegrationTest` 2×, `AuditHashRoundtripDiagnoseTest` 2×, `CannotCreateTransaction`).

Frontend-Volllauf lief zweimal, beide Male mit **reinen 5000-ms-Timeouts** in
`document-editor/index.test.tsx`, `ArtikelEditor.test.tsx` und (im ersten Lauf)
`useDatensatzLock.test.tsx`. Einzeln nachgefahren: **35/35 grün in 32 s**. Kein Befund, aber die
Suite ist unter paralleler Last spürbar zeitfragil geworden — siehe Hinweis 6.

### Die fünf 7-2-Befunde: Stand

| Befund | Stand | Nachweis |
|---|---|---|
| 🔴 Design 1 — „Gebucht" bei Fremdsperre | **behoben** | Badge hängt an neuem Prop `istGebucht` (= `istGebuchteRechnung` aus `index.tsx:431`), nicht mehr an `isLocked`; drei Tests (Fremdsperre / echte gebuchte Rechnung / storniert). Mutationsprobe unten. |
| 🔴 Design 2 — Dialog deckt die Leiste nicht ab | **behoben** | Kein `transform` mehr; Seite misst per Callback-Ref + `ResizeObserver`/`getBoundingClientRect()` und reicht `--lock-leiste-hoehe` durch, Editor-Wurzeln auf `top-[var(--lock-leiste-hoehe,0px)]`. Mutationsprobe unten. |
| 🟡 Code 3 — `setSchliesstGerade` vor `await` | **behoben** (Code war schon richtig, jetzt abgesichert) | Neuer synchroner Test in `DocumentEditorPage.test.tsx` (`fireEvent` in `act()`, Assertion ohne `waitFor`). Mutationsprobe unten. |
| 🟡 Code 4 — X-Knopf ohne `aria-label` | **behoben** | `aria-label="Editor schließen"`; `xKnopf()` in `index.test.tsx` **und** `e2e/dokument-editor-seite.spec.ts` auf `getByRole('button', { name: 'Editor schließen' })` — die testid-Eingrenzung ist damit entbehrlich geworden. |
| 🟡 Code 5 — `index.test.tsx` an `useDocumentLock` | **behoben** | Grep unten. |

### Verbraucher-Greps (Voraussetzung für Runde 8-2)

**Frontend `src/`** (`useDocumentLock|DocumentLockedModal|dokument-locks`, `*.ts`/`*.tsx`): **sauber**.
Übrig sind nur die beiden alten Dateien selbst (`useDocumentLock.ts`, `DocumentLockedModal.tsx`)
plus drei reine Prosa-Kommentare (`LieferantDokumentModal.test.tsx:13`, `lock/GesperrtHinweis.tsx:26`,
`lock/useDatensatzLock.ts:7`). **Kein Test hängt mehr am alten Hook** — die Voraussetzung fürs
Löschen in 8-2 ist erfüllt.

**Frontend `e2e/`** (vom Auftrag nicht verlangt, aber für 8-2 relevant): **nicht** sauber.
`e2e/hilfen/dokument-editor.ts:101/105/112` stubbt weiterhin **aktiv** (`page.route`)
`/api/dokument-locks/**/acquire`, `/heartbeat` und den DELETE-Pfad. Das sind echte Aufrufe, keine
Kommentare — tote Test-Kulisse für einen Endpunkt, den 8-2 entfernt. Blockiert das Löschen nicht
(nicht bediente Routen laufen ins Leere), sollte aber mitgehen. Siehe Hinweis 5.

**Backend** (`DokumentLockService|DokumentLockDto|DokumentLockRepository|DokumentLockController`
über `src/main/java` + `src/test/java`): **sauber**. Nur die alten Klassen untereinander
(`DokumentLockController`, `DokumentLockService`, `DokumentLockDto`, `DokumentLockRepository`,
`DokumentLockServiceTest`) plus drei Prosa-Kommentare in `SperrbarerTyp.java`,
`DatensatzLockService.java` und `DatensatzLockServiceTest.java`.

### Mutationsproben (Quellstand danach byte-identisch, `git status` nur die zwei Nutzer-Dateien)

1. **A1 — Badge zurück auf `isLocked`** (`DocumentEditorHeader.tsx:84`) ⇒ **2 von 25 rot**:
   „zeigt 'Gebucht' NICHT bei Fremdsperre (readOnly=true)…" und „…NICHT für ein storniertes
   Dokument". Der Positiv-Test („weiterhin für eine tatsächlich gebuchte Rechnung") blieb grün —
   die Probe trifft also genau die Fehlkopplung.
2. **A2 — `[transform:translateZ(0)]` wieder auf den Editor-Container** (`DocumentEditorPage.tsx`)
   ⇒ Playwright-Spec `e2e/dokument-editor-seite.spec.ts -g "Warn-Dialog blockiert"` auf **beiden**
   Größen rot:
   `Error: Der Warn-Dialog muss die Bearbeiten-Leiste ueberdecken (elementFromPoint darf NICHT den
   Knopf treffen), solange er offen ist / Expected: false / Received: true`.
   Vorher als Baseline die volle Spec grün gefahren: **12/12 in 1,5 min** (eigener Port
   `E2E_PORT=5199`, `npm run test:e2e` habe ich nicht angefasst).
3. **A3 — Reihenfolge `setSchliesstGerade`/`await` getauscht** ⇒ **1 von 12 rot**
   („blendet die Leiste SYNCHRON aus…").
4. **B — kompletter `MutationObserver`-Effekt aus `toast.tsx` entfernt** ⇒ **3 von 10 rot**:
   `expected 'fixed z-[9999] flex flex-col gap-2 po…' to contain 'top-6'` (2×) und
   `expected "disconnect" to be called at least once`. Deckt sich mit dem Bericht des Agenten,
   inklusive des überraschenden dritten Treffers beim Mount-Test.
5. **B — die vier `setState`-Zeilen aus `releaseKeepalive` entfernt** ⇒ **2 von 37 rot**, beide mit
   `AssertionError: expected 'bearbeiten' to be 'lesen'` — genau die zwei neuen `pagehide`/
   `pageshow`-Tests.
6. **Zusatzprobe (Sanity, Behauptung des Agenten nachgestellt): `setModus('lesen')` im
   Heartbeat-409-Zweig entfernt** ⇒ **22 von 37 rot**. Die globale `afterEach`-Invariante greift
   also tatsächlich weit über die eigene Nachbesserung hinaus. Dabei ist mir Hinweis 1 aufgefallen.

### Selbst nachgeprüft, ohne Befund

- **Containing-Block-Kette über dem Editor ist frei.** Weder `#root`/`body` (kein `transform`,
  `filter`, `will-change`, `contain`, `isolation` in `index.css`) noch `RequireAuth` (gibt `children`
  direkt zurück, kein Wrapper-Element) noch `ErrorBoundary` (dito im Gutfall) noch die Seite selbst
  erzeugen einen. Grep über `document-editor/` + `DocumentEditorPage.tsx` nach
  `translateZ|will-change|backdrop-filter|[filter:|[contain:|perspective|[transform:` — **keine
  Treffer**. Der Editor bleibt `position:fixed` zum echten Viewport, seine `z-[70]`-Dialoge liegen
  wieder über der Leiste (E2E-Nachweis oben).
- **`ResizeObserver` läuft beim Unmount aus** — `return () => beobachter.disconnect()` im selben
  Effekt; feuert auch beim Wechsel `leisteElement → null` (dann zusätzlich `setLeisteHoehe(0)`).
- **Variable ohne Leiste** (neues Dokument ohne Id, oder `schliesstGerade`): `leisteElement` ist
  `null` ⇒ `--lock-leiste-hoehe: 0px` ⇒ der Editor fällt exakt auf das alte `inset-0`-Verhalten
  zurück. `DocumentEditor` hat genau **einen** Verwender (`DocumentEditorPage`), sonst setzt niemand
  die Variable — der Fallback greift überall.
- **Tailwind erzeugt die Klasse**: im gebauten CSS steht `top:var(--lock-leiste-hoehe,0px)`
  (nachgesehen, danach `static/` zurückgesetzt).
- **`releaseKeepalive` und React-Warnungen:** React 19 — `setState` nach dem Unmount ist ein
  stiller No-op, die Warnung gibt es seit React 18 nicht mehr. Kein Befund. Auf dem
  Unmount-Cleanup-Pfad setzt der Effekt-Cleanup dieselben vier Werte direkt danach nochmal;
  redundant, aber folgenlos.
- **Bearbeiten nach bfcache holt wirklich neu:** nach `pagehide` steht `heldRef=false`,
  `status='idle'`, `modus='lesen'` ⇒ `onBearbeiten()` fällt in den `void acquire(lockUrl)`-Zweig
  (nicht in den `heldRef`-Kurzschluss, nicht in die `loading`/`error`-Sperre). Durch den neuen
  `pageshow`-Test und Mutationsprobe 5 belegt.
- **`role="dialog"` im Confirm kippt nichts anderes:** voller Frontend-Lauf zweimal ohne einen
  einzigen `getByRole('dialog')`-Fehlschlag; die einzigen Ausfälle waren Zeitüberschreitungen
  (siehe oben).
- **Server-Meldung im Konflikt-Fall wirklich entbehrlich:** `useKonfliktMeldung` ist der einzige
  Leser der 409-Antwort, `eigeneMeldung` war ein Template-String mit `bezeichnung` und damit nie
  leer — die entfernten Fallbacks konnten tatsächlich nie greifen. `variant: 'info'` folgt einem
  im Projekt bereits fünfmal genutzten Muster und liefert laut `confirmBtnMap` den rose-Knopf.
- **`zeigeNurLesenHinweis` im Lieferant-Modal:** `status === 'idle'` deckt genau „frisch
  freigegeben" und „noch nie geholt" ab; `acquired` ohne `bearbeiten` ist seit 7b nicht mehr
  erreichbar, `locked-by-other` erklärt `GesperrtHinweis`, `loading`/`error` haben ihre eigenen
  Bänder. Deckungsgleich mit der Regel auf der Editor-Seite.
- **`istGebucht` als Pflicht-Prop:** `DocumentEditorHeader` hat genau einen Verwender, TS-Build grün.
- **Datenschutz/Secrets:** keine E-Mail-Adressen, keine echten Namen in den neuen Dateien (nur
  „Musterbedarf/Musterweg/Musterstadt", „Erika Musterfrau"), keine Secrets, kein Build-Output und
  kein `test-results/` im Diff, keine Java-/Endpoint-Änderung, also keine neue Angriffsfläche.
- **Performance:** kein neues Polling, kein zweiter Timer; ein `ResizeObserver` auf genau einem
  Element.

### 💡 Hinweise (blockieren nicht)

1. **`useDatensatzLock.test.tsx:78-97` — die neue `afterEach`-Invariante macht aus einem Fehler 22.**
   Die Schleife mit `expect(...)` steht **vor** dem Aufräumen. Schlägt sie an, werden
   `beobachteteZustaende.length = 0`, `vi.restoreAllMocks()` und `vi.useRealTimers()` nie erreicht —
   der Zustandspuffer bleibt gefüllt und **jeder** nachfolgende Test in der Datei fällt über dieselben
   alten Aufzeichnungen. In Mutationsprobe 6 war genau **ein** Test echt kaputt
   („Heartbeat 409 …", `expected 'bearbeiten' to be 'lesen'`); die anderen 21 meldeten
   `expected 'bearbeiten' not to be 'bearbeiten'` oder — wegen der hängengebliebenen Fake-Timer —
   `Test timed out in 5000ms`, darunter völlig unbeteiligte wie „gibt das Lock beim Unmount per
   DELETE frei" und „status wird nach einem Acquire-Fehler (500) zu 'error'". Ironie: der Kommentar
   an `vi.useRealTimers()` nennt es ausdrücklich ein „Sicherheitsnetz" gegen genau dieses Symptom —
   die Assertion darüber reißt das Netz weg. **Nachweisbar wäre:** Aufräumen in ein `finally`
   (oder erst zurücksetzen, dann auf einer Kopie prüfen), danach dieselbe Mutationsprobe ⇒ genau
   **ein** roter Test mit der echten Ursache.

2. **`LieferantDokumentModal.test.tsx:308-310` — der bekannte Wackler ist kein Wackler, sondern eine
   falsche Zusicherung; und er ist in dieser Runde häufiger geworden.** Der Test wartet darauf, dass
   der Text „Sperre konnte nicht geholt werden — bitte neu laden." **genau zweimal** im Dokument
   steht (rotes Band + Toast). Im eingeschwungenen Zustand steht er aber **dreimal**: dazu kommt der
   `sr-only`-Span, den `BearbeitenLeiste.tsx:143-147` für `aria-describedby` rendert, sobald
   `bearbeitenGesperrtGrund` gesetzt ist (seit Task 7d). Der Test geht also nur durch, wenn
   `waitFor` zufällig **vor** dem Commit des Toasts abtastet — er prüft das Gegenteil dessen, was
   sein Kommentar behauptet. Gemessen, isoliert je 6 Läufe: auf dem Basisstand `256b3e1d`
   **1 von 6 rot**, auf dem jetzigen Stand **3 von 6 rot** (plus 3 von 4 in einem früheren Block).
   Im Volllauf blieb er beide Male grün — das Zeitfenster ist dort ein anderes. Die Vermutung des
   Agenten („doppelt ausgelöster Toast durch einen React-Effekt-Randfall") trifft nicht zu, es
   feuert genau ein Toast. **Nachweisbar wäre:** `toHaveLength(3)` mit benannter Herkunft der drei
   Vorkommen, oder gezielter je Rolle prüfen (`getByRole('alert')`, Toast-Container, `sr-only`-Span)
   statt über einen nackten Textzähler.

3. **Toast liegt bei offenem Confirm-Dialog *hinter* dessen Backdrop.** `toast.tsx:188` ist
   `z-[9999]`, `confirm-dialog.tsx:106` ist `z-[10000]` (Panel `z-[10001]`) — beide `position:fixed`
   im selben Stacking-Kontext (weder `ToastProvider` noch `ConfirmProvider` erzeugen ein
   DOM-Element). Damit liegt der Toast unter `bg-black/40 backdrop-blur-sm`: abgedunkelt und
   unscharf, und ein Klick darauf trifft den Backdrop, dessen `onClick` `handleCancel()` ist —
   wer den Toast wegklicken will, bricht den Dialog ab. Betrifft genau die Kombination, die diese
   Runde neu verdrahtet hat (Punkt 3 lässt den Toast wegen des Confirm-Dialogs umziehen; für
   Modale mit `z-50` wie `LieferantDokumentModal` funktioniert der Umzug dagegen wie gedacht).
   Die neue E2E-Zusicherung prüft nur die Klassen, nicht die Stapelung. Z-Reihenfolge ist
   vorbestehend, durch Punkt 3 aber erstmals im Spiel. **Nachweisbar wäre:** `elementFromPoint` in
   der Mitte des Toasts trifft den Toast, nicht den Backdrop — dafür müsste der Toast-Container
   über `10001` liegen.

4. **Das ganze `--lock-leiste-hoehe`-Messwerk ist direkt ungetestet.** Weder
   `DocumentEditorPage.test.tsx` noch die E2E-Spec erwähnen die Variable oder den `ResizeObserver`;
   abgesichert ist nur die *Wirkung* (Backdrop deckt „Fertig", `designPruefung`-Überlappungsprüfung).
   Genau die zwei Fehler, die der Agent unterwegs selbst gebaut hat (Variable bleibt bei `0px`;
   `contentRect` 34 px statt `getBoundingClientRect()` 55 px), hängen damit an einer
   Browser-Messung. Ein Unit-Test wäre billig: `setupTests.ts` mockt `ResizeObserver` bereits, und
   `vi.spyOn(MockResizeObserver.prototype, 'disconnect')` funktioniert genauso wie der neue
   `MutationObserver`-Test in `toast.test.tsx`. **Nachweisbar wäre:** Style-Attribut trägt eine
   Höhe > 0 mit Leiste und `0px` ohne, und `disconnect()` läuft beim Unmount.

5. **`e2e/hilfen/dokument-editor.ts:101/105/112` stubbt weiter den alten Sperr-Endpunkt.** Aktive
   `page.route()`-Aufrufe auf `/api/dokument-locks/**`, kein Kommentar. Tote Kulisse, seit die
   Seite auf `/api/datensatz-locks/` liegt. Gehört in Runde 8-2 mit weg, sonst bleibt eine Test-Hilfe
   stehen, die einen nicht mehr existierenden Endpunkt bedient.

6. **Zeitfragilität der Frontend-Suite.** Zwei Vollläufe, beide mit reinen 5000-ms-Timeouts
   (Lauf 1: 5 Tests in `useDatensatzLock.test.tsx` + `index.test.tsx`; Lauf 2: 4 Tests in
   `index.test.tsx` + `ArtikelEditor.test.tsx`), alle beim Einzellauf grün. Dazu backendseitig
   `UnifiedEmailControllerExtractEmailTest` mit seiner 500-ms-Schranke. Nichts davon ist ein
   Regressionsbefund, aber die Zahlen oben gelten nur mit Nachfahren — ein Volllauf allein ist auf
   diesem Rechner unter paralleler Browser-Last nicht mehr aussagekräftig.

7. **`releaseKeepalive` erhöht `generationRef` nicht** (anders als `aktivFreigeben`), und der
   Heartbeat-`fetch` hängt an keinem `AbortSignal`. Ein Heartbeat, der nach `pagehide` noch
   eintrifft, kann daher `verbindungWeg` setzen, nachdem der Reset es gerade auf `false` gezogen
   hat. Die Invariante bricht das nicht (`modus`/`status` fasst der Heartbeat in dem Zweig nicht
   an), also rein kosmetisch — eine Zeile `generationRef.current += 1` würde es schließen.

8. **`confirm-dialog.tsx:94` — feste ID `'confirm-dialog-titel'`.** Mit dem einen `ConfirmProvider`
   in `App.tsx` unkritisch, aber `useId()` wäre gegen doppelte IDs robust (in Tests werden schon
   heute mehrere Provider gleichzeitig gerendert).

9. **`variant: 'info'` bringt einen himmelblauen Icon-Kreis** (`bg-sky-100`/`text-sky-600`,
   `HelpCircle`) in einen Sperr-Konflikt — `CLAUDE.md` verbietet Blau ausdrücklich. Die Variante ist
   vorbestehend und wird von fünf weiteren `confirm()`-Aufrufen genutzt, der rose-Knopf war das
   erklärte Ziel und ist erreicht. Gehört formal dem Design-Reviewer; hier nur als Notiz, damit es
   nicht untergeht.


## Abschnitt 8-2 — Task 8c (Coding-Agent)

**Zeit:** 2026-09-05, ca. 14:20
**Branch:** `lock/task-8c-politur` (Worktree `wt/task-8c`, Basis `claude/eloquent-ramanujan-gz0w2t` @ `89ffc0d5`)
**Commits:**
- `907985c5` — fix(toast): Container bei offenem Dialog nach unten links statt oben links
- `427c51a8` — fix(confirm-dialog): neue Variante 'fehlschlag' fuer die Konfliktmeldung
- `58ec9588` — test(e2e): Fall mit gebucht:true fuer das "Gebucht"-Badge

**Status:** 🟢 alle drei Punkte umgesetzt, testgetrieben, Mutationsproben rot→grün belegt.

### Was gemacht wurde

**Punkt 1 — Toast bei offenem Dialog nach unten links.** `toast.tsx`: Container-Klasse
bei offenem Dialog von `top-6 left-6` auf `bottom-6 left-6` geaendert (sonst-Fall bleibt
`bottom-6 right-6` unveraendert). Kommentare an Funktion und Container um die Herleitung
aus dem Kontext-Log ergaenzt (12px-Ueberlappung auf 14 Zoll, warum nach oben nicht
ausweichbar, warum unten links frei bleibt).

Roter Test zuerst: `toast.test.tsx` — beide betroffenen Tests ("wandert nach unten LINKS
...", MutationObserver-Test) auf die neuen Klassen umgeschrieben; vor der Quelltext-
Aenderung waeren sie mit den alten `top-6`-Erwartungen ohnehin rot gewesen (TDD-Reihenfolge
hier: Quelltext und Test in einem Schritt, da die Aenderung eine reine Ein-Wort-Klassen-
Vertauschung ist). Mutationsprobe (Klassen zurueck auf `top-6 left-6`) ⇒ 2 von 10 Tests in
`toast.test.tsx` rot (`AssertionError: expected ... to contain 'bottom-6'`), danach wieder
grün.

E2E (`toast-bei-dialog.spec.ts`): Klassen-Zusicherungen auf `bottom-6`/`left-6` umgestellt.
Zusaetzlich neue Zusicherungen fuer Modal-Titel, Eyebrow, "Abbrechen" und "Speichern" (bisher
deckte die Spec nur Schliessen-X und "Vorschau" ab). Für Titel/Eyebrow **kein**
`elementFromPoint`-Klicktest, sondern ein Bounding-Box-Vergleich
(`erwarteKeineUeberlappungMitToast`): beim Schreiben des ersten Entwurfs mit
`elementFromPoint` am Box-Mittelpunkt fiel auf, dass die vom Design-Reviewer gemessene
12px-Ueberlappung [Toast endet y=90, Titel beginnt y=78] nur den oberen Rand des 28px hohen
Titel-Elements trifft — dessen geometrische Mitte (y=92) liegt bereits unterhalb des
Toast-Endes. Mutationsprobe bestaetigte das: mit `top-6` reproduziert, `elementFromPoint`
an der Box-Mitte des Titels blieb **grün** (falsch-negativ). Umgestellt auf einen
Rechteck-Ueberlappungstest (dieselbe Methode wie die manuelle Messung im Review) — damit
wurde dieselbe Mutation zuverlaessig rot: `Toast [24,24,480,66] ueberlappt "Dokument
bearbeiten (Modal-Titel)" [61,78,179,28]`. Für Schliessen-X/Vorschau/Abbrechen/Speichern
blieb der bestehende `elementFromPoint`-Klicktest (dort sinnvoll, weil es echte Knoepfe
sind). `E2E_PORT=5182 npx playwright test e2e/toast-bei-dialog.spec.ts`: 2 Tests × 2
Groessen = 4 grün.

**Punkt 2 — Vierte Confirm-Variante 'fehlschlag'.** `confirm-dialog.tsx`: neue Variante
`'fehlschlag'` (amber-100/amber-600 `AlertTriangle`-Icon wie bei "Ungespeicherte
Aenderungen", rose-600-Knopf wie `'info'`). `useKonfliktMeldung.ts` nutzt sie jetzt statt
`'info'`. Grep bestaetigt: die fünf bestehenden `'info'`-Aufrufer
(Urlaubsantraege.tsx, ProjektEditor.tsx, BestellungEditor.tsx,
KostenpositionenView.tsx, LieferantReklamationenTab.tsx) sind unveraendert.

Roter Test zuerst: neuer Test in `confirm-dialog.test.tsx` ("Variante 'fehlschlag' zeigt
ein amber-AlertTriangle-Icon und einen rose-Bestaetigungsknopf") und in
`useKonfliktMeldung.test.tsx` ("zeigt ein amber-AlertTriangle-Icon statt des blauen
Fragezeichens"). Mutationsprobe (`useKonfliktMeldung.ts` zurueck auf `variant: 'info'`) ⇒
neuer Test in `useKonfliktMeldung.test.tsx` rot: `expected 'lucide lucide-circle-question-
mark h-6 w-6 text-sky-600' to contain 'text-amber-600'`. Danach wieder grün. Sichtbar auch
im Screenshot `toast-bei-dialog-versionskonflikt--pc-14zoll.png`: amber-Warndreieck statt
blauem Fragezeichen, "Neu laden" weiterhin rose-600 gefüllt.

**Punkt 3 — E2E-Fall mit `gebucht: true`.** `dokument-editor-seite.spec.ts`: neuer Test
"gebuchte Rechnung zeigt das 'Gebucht'-Badge", Stub ueberschreibt NACH
`stubbeDokumentEditorApi` die GET-Antwort auf `/api/ausgangs-dokumente/1` mit
`{ ...BEISPIEL_DOKUMENT, gebucht: true }` (Route-Ueberschreibung, gemeinsame Hilfsdatei
unveraendert). Zusicherung auf Badge-Text "Gebucht", Klassen `bg-amber-50`/`text-amber-700`,
Lucide `svg.lucide-lock`. Lock bleibt `'frei'` — "gebucht" ist eine Dokument-Eigenschaft,
unabhaengig vom Datensatz-Lock der Seite; die Seiten-Leiste zeigt darum weiterhin "Fertig"
(nicht "Bearbeiten", wie ein erster Entwurf faelschlich annahm, bevor der Blick in
`DocumentEditorPage.tsx` zeigte, dass `readOnly` dort ausschliesslich vom Lock-Modus
abhaengt, nicht von `dokument.gebucht`).

Gegenprobe: die drei bestehenden Tests (`-lesen`, `-gesperrt`, `-fehler`) sichern jetzt
explizit `getByText('Gebucht', { exact: true })` → `toHaveCount(0)` zu, statt die
Abwesenheit nur implizit über den Stub (`gebucht: false`) anzunehmen.

Mutationsprobe: `istGebuchteRechnung` in `document-editor/index.tsx` testweise auf `false`
gesetzt ⇒ neuer Test in beiden Groessen rot (`element(s) not found` für
`getByText('Gebucht', { exact: true })`), danach exakt zurueckgesetzt — `git diff`/`git
status` bestaetigen `document-editor/index.tsx` danach byte-identisch (keine Datei
ausserhalb der erlaubten Liste angefasst).

**Tests je Größe:** `E2E_PORT=5182 npx playwright test e2e/toast-bei-dialog.spec.ts
e2e/dokument-editor-seite.spec.ts` — 18 Tests × (`pc-14zoll` + `pc-monitor`) = 18 grün
(9 Testfaelle je Groesse). Unit: `npx vitest run toast.test.tsx confirm-dialog.test.tsx
useKonfliktMeldung.test.tsx` — 32 grün. `npm run lint`: 0 Fehler, genau die eine
vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`). `npm run build`: grün,
`src/main/resources/static/` danach zurueckgesetzt (`git status` dort leer).

**Screenshots** (`react-pc-frontend/test-results/design/`):
- `toast-bei-dialog-zweizeilig--pc-14zoll.png` / `--pc-monitor.png`
- `toast-bei-dialog-versionskonflikt--pc-14zoll.png` / `--pc-monitor.png`
- `editor-seite-gebucht--pc-14zoll.png` / `--pc-monitor.png`
- unveraendert erneut erzeugt: `editor-seite-bearbeiten/-lesen/-gesperrt/-fehler/
  -tab-schliessen/-warn-dialog-blockiert-leiste--{pc-14zoll,pc-monitor}.png`

Kurzer eigener Blick (keine formale Design-Abnahme, die bleibt beim Design-Reviewer):
`toast-bei-dialog-zweizeilig` zeigt den Toast jetzt unten links, ohne Beruehrung von
Modal-Titel/Eyebrow oben oder "Abbrechen"/"Speichern" unten rechts.
`toast-bei-dialog-versionskonflikt` zeigt das amber-Warndreieck statt des blauen
Fragezeichens. `editor-seite-gebucht` zeigt das Badge korrekt neben der Dokumentnummer.

### Bedenken / Abweichungen vom Plan

Keine. Alle drei Punkte wie geplant in den genannten Dateien umgesetzt, keine Datei
ausserhalb der Liste angefasst. Einzige Praezisierung gegenüber dem Wortlaut des Plans:
Punkt 1 verlangte für Titel/Eyebrow "elementFromPoint ... trifft jeweils das Element
selbst" — das haette den Ausgangsbefund nicht zuverlaessig erkannt (siehe oben), daher
stattdessen ein Bounding-Box-Ueberlappungstest, der nachweislich denselben Fall faengt und
inhaltlich dasselbe zusichert ("der Toast liegt nicht auf dem Titel/der Eyebrow").

## Abschnitt 8-2 — Aufräumen (Orchestrator)

Zeit: 2026-09-05T13:05:00Z
Branch: lock/task-8b-aufraeumen (Worktree ../wt/task-8b)
Commit(s): 37c4ea61 (altes Sperr-System + V365), Folgecommit (design.ts)
Status: fertig

Was gemacht wurde:
- Acht alte Dateien gelöscht: DokumentLock (Entity), DokumentLockDto,
  DokumentLockRepository, DokumentLockService, DokumentLockController,
  DokumentLockServiceTest, useDocumentLock.ts, DocumentLockedModal.tsx — in
  derselben Auslieferung wie Migration V365 (DROP TABLE IF EXISTS dokument_lock),
  weil ein Zwischenstand mit Entity ohne Tabelle nicht startet.
- e2e/hilfen/dokument-editor.ts stubbt /api/datensatz-locks/ statt der toten alten
  Route; useDatensatzLock.test.tsx: Invariante und Aufräumen per try/finally
  getrennt; LieferantDokumentModal.test.tsx: Zusicherung auf die tatsächlichen drei
  Vorkommen (Band, Toast, sr-only-Span); index.tsx: Reihenfolge-Kommentar im
  Create-Zweig; Javadoc-@link auf gelöschte Klasse zu @code; Kommentare ohne Pfade
  auf gelöschte Dateien.
- e2e/hilfen/design.ts: keinAbschneiden() neu, keinHorizontalerUeberlauf() misst
  Container gegen eigenen scrollWidth (Beobachtung des Nutzers, 14-Zoll-Lauf).
  Wegwerf-Probe mit den drei Fällen: alle erkannt; volle E2E-Suite danach 110/110.
- Geprüft: mvn test-compile grün; lint 0 Fehler / 1 bekannte Warnung; 93 gezielte
  Unit-Tests grün; Editor-Specs 18/18; volle E2E 110/110.

Bedenken / Abweichungen vom Plan:
- Verbleibende Erwähnungen der alten Namen sind reine Historie in Kommentaren
  (GesperrtHinweis, SperrbarerTyp, DatensatzLockService, zwei Spec-Köpfe) — bewusst
  gelassen, sie erklären, was ersetzt wurde.
- Gebaute Bundles unter src/main/resources/static/assets enthalten noch
  /api/dokument-locks — historischer Build-Output, wird beim nächsten
  Produktions-Update neu erzeugt; nicht angefasst.
