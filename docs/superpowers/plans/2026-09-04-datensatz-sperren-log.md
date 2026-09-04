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
