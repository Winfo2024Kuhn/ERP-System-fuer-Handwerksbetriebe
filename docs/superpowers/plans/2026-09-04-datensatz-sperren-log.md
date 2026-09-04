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
