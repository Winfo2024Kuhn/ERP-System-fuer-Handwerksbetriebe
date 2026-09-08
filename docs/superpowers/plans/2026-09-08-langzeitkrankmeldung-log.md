# Kontext-Log: Langzeitkrankmeldung

Vorhaben: Langzeitkrankmeldung (Lohnfortzahlung → Krankengeld → Wiedereingliederung)
Issue: #91 — https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/91
Feature-Branch: `feature/langzeitkrankmeldung`
Spec: `docs/superpowers/specs/2026-09-08-langzeitkrankmeldung.md`
Design (abgenommen): `docs/superpowers/specs/2026-09-08-langzeitkrankmeldung-brainstorming.md`

**Append-only.** Nur unten anhängen, nie bestehenden Text ändern. Lock-Protokoll
siehe `.claude/skills/loese-problem/references/kontext-log-format.md`.

---

## Baseline (Orchestrator, vor dem ersten Coding-Agenten)

Zeit: 2026-09-08, gemessen auf dem unveränderten `feature/langzeitkrankmeldung`
(identisch mit `main` bei `f9d87afc`).

### Backend — `./mvnw -B test`

```
Tests run: 2461, Failures: 0, Errors: 4, Skipped: 0
```

**Vier vorbestehende Errors, alle umgebungsbedingt** (`CannotCreateTransaction:
Could not open JPA EntityManager for transaction` — diese Integrationstests
brauchen eine echte DB, die hier nicht läuft):

- `AuditChainRepairIntegrationTest.appendToChainHashIstNachRoundtripReproduzierbar`
- `AuditChainRepairIntegrationTest.rebuildMachtEchteLokaleKetteIntakt`
- `AuditHashRoundtripDiagnoseTest.getrimmterZeitstempelUeberlebtDbRoundtrip`
- `AuditHashRoundtripDiagnoseTest.rohNanosekundenUeberlebenDbRoundtripNicht`

**Abnahmeregel Backend: grün = genau diese 4 Errors.** Der fünfte ist neu und
gehört dir. Diese vier nicht "reparieren" — sie sind nicht kaputt, es fehlt nur
die Datenbank.

### Frontend PC — `react-pc-frontend`

- `npm run lint` → **exit 0**, 1 Problem: **0 Errors, 1 Warning**
  (`src/pages/BelegeKasseEditor.tsx:1204:8` — `react-hooks/exhaustive-deps`,
  fehlende Dependency `beleg`).
  **Abnahmeregel: 0 Errors und höchstens diese 1 Warning.** Eine zweite Warning
  ist neu.
- `npm run test` → **exit 0**, **88 Test-Dateien, alle grün.**

### Frontend Handy — `react-zeiterfassung`

- `npm ci` → exit 0
- `npm run lint` → **exit 0**
- `npm run test` → **exit 0**, **10 Test-Dateien, alle grün.**

### Flyway

Höchste vorhandene Migration: `V366__datensatz_lock_entitaet_typ_enum.sql`.
**`V367` ist frei** und für dieses Vorhaben reserviert.

---

## Umgebung — Workarounds, die jeder Agent kennen muss

### `react-pc-frontend/node_modules` war kaputt: `.bin` leer

Fehlerbild: `npm run lint` bzw. `npm run test` bricht ab mit
*"Der Befehl 'eslint' ist entweder falsch geschrieben oder konnte nicht gefunden
werden"* — obwohl `node_modules/eslint` und `node_modules/vitest` als Pakete
vorhanden sind. Ursache: `node_modules/.bin` war **leer** (0 Einträge), die
Wrapper-Skripte fehlten.

**`npm ci` repariert das hier nicht** — es scheitert mit
`EPERM: operation not permitted, unlink … @esbuild\win32-x64\esbuild.exe`, weil
ein laufender Prozess die Datei hält.

**Was funktioniert: `npm install`** (nicht `npm ci`, nicht `npm rebuild` — `npm
rebuild` lief zwar durch, ließ `.bin` aber leer). Danach 105 Einträge in `.bin`,
lint und test laufen. `package-lock.json` blieb dabei unverändert.

### Verwaiste Dev-Server sperren Dateien im Haupt-Checkout

Ursache des EPERM oben: Aus einem **früheren Pipeline-Lauf (05.09.2026)** liefen
noch zwei Vite-Dev-Server aus dem Worktree
`C:\Users\MarvinKuhn\dev\ERP-für-Handwerker\wt\review-design\react-pc-frontend`
sowie zwei `esbuild.exe` aus dem Haupt-Checkout.

Weil Worktrees ihr `node_modules` per Symlink/Junction auf das Haupt-Checkout
zeigen (so schreibt es `fallstricke.md` vor), sperrt ein Dev-Server **im
Worktree** die Binaries **im Haupt-Checkout**. Ein `npm ci` im Haupt-Checkout
scheitert dann, ohne dass man den Zusammenhang sieht.

Diese Prozesse laufen zum Zeitpunkt dieser Messung noch (der Kill war in dieser
Session nicht erlaubt). `npm install` kommt trotzdem durch. Wer einen Dev-Server
startet, beendet ihn am Ende seines Tasks wieder.

---

## Verlauf

## Setup (Orchestrator)

Zeit: 2026-09-08
Branch: feature/langzeitkrankmeldung
Status: fertig

Was gemacht wurde:
- Schritt 1: Spec geschrieben (`docs/superpowers/specs/2026-09-08-langzeitkrankmeldung.md`)
- Schritt 2: Issue #91 angelegt
- Baseline gemessen und oben festgehalten
- `react-pc-frontend/node_modules` repariert

Bedenken / Abweichungen vom Plan:
- Die Spec hat zwei Abweichungen zum abgenommenen Design gefunden:
  (1) `UrlaubsantragService.java:112` ruft `getSollstundenFuerTag` auf, steht im
  Design aber weder in der Umstellungs- noch in der Ausnahmeliste.
  (2) Die drei "wortgleich duplizierten" Feiertagsstellen sind **nicht**
  semantisch identisch — `ZeitkontoService` rechnet Feiertage als volle bezahlte
  Arbeitstage ins Periodensoll, die beiden anderen ermitteln eine separate
  Feiertagsgutschrift. Die Bündelung bleibt gewollt, darf aber kein Verhalten
  still ändern. Beides ist zur Entscheidung an den Grobplan gegangen.
- Statt 8 Aufrufstellen (Design) sind es real 11.

## Bewusste Verhaltensänderungen (Orchestrator, aus dem Grobplan)

Zeit: 2026-09-08
Status: zur Kenntnis für alle Coding- und Review-Agenten

Die Bündelung der Feiertagslogik darf nichts still ändern. Drei Stellen ändern
sich trotzdem — jede ist im Plan benannt und bekommt einen eigenen Test:

1. **`ZeitverwaltungController` /kalender, halbe Feiertage — Bugfix.**
   Heute liefert Zeile 512 an *jedem* Feiertag die **vollen** Sollstunden als
   Ist-Stunden, auch am Heiligabend, während `sollStundenMonat` (Zeile 543) den
   halben Feiertag korrekt halbiert. Das sind heute **+4 h Phantom-Überstunden
   pro halbem Feiertag**, und der Kalender widerspricht der Monatsübersicht.
   Nach der Umstellung liefert `feiertagsGutschrift` auch hier 4 h.
   Das ist ein **Bugfix, kein Rückschritt** — festgehalten in Task 11.
2. **Urlaubsstunden während einer Wiedereingliederung** folgen dem Stufenplan.
   Ohne Langzeitkrankmeldung ändert sich nichts.
3. **Krankheitsstunden im Verrechnungslohn**: Krankengeld- und
   Wiedereingliederungstage fallen aus Jahressoll und Lohnkosten heraus
   (Task 13). Ohne Langzeitkrankmeldung ändert sich nichts.

**Alles andere muss zahlengleich bleiben.** Dafür sind die
Charakterisierungs-Tests aus Task 2 da — sie nageln das heutige Verhalten aller
sechs Aufrufstellen fest, bevor umgestellt wird. Wer sie anfassen muss, hat
vermutlich einen Fehler gemacht; die einzige erlaubte Ausnahme ist die
Zusicherung zum halben Feiertag in Task 11.

## Abschnitt 1 — Task 1 (Coding-Agent)

Zeit: 2026-09-08T15:40:00Z
Branch: lzk/task-1-datenmodell
Commit(s): f5a78f90
Status: fertig

Was gemacht wurde:
- Neue Domain-Dateien: Langzeitkrankmeldung (Aggregate Root, @Version), LangzeitkrankmeldungPhase (Kind-Entitaet, bewusst ohne @Version), LangzeitkrankmeldungStatus, LangzeitkrankmeldungPhaseTyp.
- Abwesenheit um die zwei @ManyToOne-Felder langzeitkrankmeldung/langzeitkrankmeldungPhase ergaenzt (direkt unter dem urlaubsantrag-Feld), AbwesenheitsTyp unveraendert gelassen.
- Neue Repositories LangzeitkrankmeldungRepository und LangzeitkrankmeldungPhaseRepository mit den im Plan vorgegebenen Queries (findMitPhasen, findMitPhasenById, findUeberlappende, findImZeitraum).
- AbwesenheitRepository um findByMitarbeiterIdAndTypAndDatumBetween und sumStundenOhnePhasenTypen erweitert (8er-Einrueckung der Datei beibehalten).
- Migration V367__langzeitkrankmeldung.sql: CREATE TABLE IF NOT EXISTS fuer langzeitkrankmeldung und langzeitkrankmeldung_phase (status/typ als natives ENUM), idempotente ALTER-TABLE-Bloecke fuer die zwei neuen abwesenheit-Spalten inkl. idempotenter FK-Constraints (information_schema-Muster aus V364/V339).
- V367SchemaTest zuerst geschrieben und rot gesehen (Migration fehlte), danach durch Migration gruen gemacht. LangzeitkrankmeldungRepositoryTest (@DataJpaTest, Dummy-Daten "Max Mustermann") deckt Speichern/Laden mit zwei Phasen, findUeberlappende (offene Meldung gefunden, ABGEBROCHEN ausgeschlossen) und findImZeitraum (nur beruehrte Phase) ab.
- Gates gruen: `./mvnw -B test -Dtest=LangzeitkrankmeldungRepositoryTest,V367SchemaTest` -> Tests run: 6, Failures: 0, Errors: 0.

Bedenken / Abweichungen vom Plan:
- Der im Plan (Zeile 428) und im Auftragstext vorgegebene Gate-Befehl `-Dtest=LangzeitkrankmeldungRepositoryTest+V367SchemaTest` funktioniert nicht: Maven Surefire trennt mehrere Testklassen mit Komma, nicht mit Plus (Plus ist nur fuer Methodenfilter innerhalb einer Klasse). Mit Komma (`-Dtest=LangzeitkrankmeldungRepositoryTest,V367SchemaTest`) laeuft das Gate wie erwartet gruen. Reine Kommando-Syntax-Korrektur, kein inhaltlicher Unterschied.
- Fuer die zwei neuen FK-Spalten an abwesenheit (langzeitkrankmeldung_id, langzeitkrankmeldung_phase_id) macht der Plan keine Vorgabe zum ON-DELETE-Verhalten. Analog zum Vorbild V339 (Preisstand-Bezug) ON DELETE SET NULL gewaehlt, damit eine geloeschte Langzeitkrankmeldung bereits gebuchte Abwesenheitstage nicht mit sich reisst.
- Ausserhalb meiner Task-1-Dateien lag im Worktree bereits eine unstaged Aenderung an src/main/resources/static/index.html (vermutlich Build-Artefakt aus vorheriger Nutzung). Nicht angefasst, nicht committet -- nur zur Transparenz vermerkt.

## Abschnitt 1 — Task 2 (Coding-Agent)

Zeit: 2026-09-08T15:50:00Z
Branch: lzk/task-2-charakterisierung
Commit(s): 2d86b623
Status: fertig

Was gemacht wurde:
- Sechs Charakterisierungstestklassen angelegt (keine Produktionsdatei
  angefasst): TagesSollCharakterisierungZeitkontoTest,
  TagesSollCharakterisierungMonatsSaldoTest,
  TagesSollCharakterisierungAbwesenheitTest,
  TagesSollCharakterisierungZeiterfassungApiTest,
  TagesSollCharakterisierungUrlaubsantragTest,
  TagesSollCharakterisierungKalenderTest.
- Alle sechs zusammen: 19 Tests, gruen gegen den unveraenderten Bestand
  (`./mvnw -B test -Dtest='TagesSollCharakterisierung*'`).
- Fixture wie im Plan vorgegeben: Zeitkonto Mo-Fr 8,00 h / Sa-So 0,00 h,
  Mitarbeiter Max Mustermann (ID 1), Testdaten Mo 2026-06-01, Sa 2026-06-06,
  Do 2026-01-01 (voller Feiertag), Do 2026-12-24 (halber Feiertag),
  Sa 2026-12-26 (Feiertag am Wochenende).
- KalenderTest haelt den bestehenden Bug (volle statt halbierte Ist-Stunden
  am halben Feiertag, tage[23].istStunden == 8) mit explizitem Kommentar
  "heutiger Stand, aendert sich in Task 11" fest - einzige Zusicherung, die
  ein spaeterer Task aendern darf.

Bedenken / Abweichungen vom Plan:
- ZeiterfassungApiTest: der Plan nennt als Zugriffsweg auf die private
  Methode `berechneFeiertagsStunden` die Methode "getGesamtSaldo(token)".
  Diese Methode existiert nicht - die tatsaechliche oeffentliche Methode
  heisst `getSaldo(String, Integer, Integer, Boolean)`. Deren
  "Randmonat"-Zweig (istErsterMonat || istLetzterMonat) haengt zusaetzlich
  unkontrollierbar am echten `LocalDate.now()`: das Enddatum wird je nach
  angefragtem Jahr entweder "heute" oder der 31.12. des angefragten Jahres -
  beides laesst sich nicht so parametrisieren, dass exakt unsere
  Fixture-Feiertage (01.01. und 24./26.12.2026) deterministisch in einem
  Randmonat-Zeitraum landen, ohne die Testzusicherung an das tatsaechliche
  Testdatum zu koppeln (Risiko: Test wird abhaengig vom Kalendertag, an dem
  er laeuft, gruen oder rot). Stattdessen habe ich die private Methode direkt
  per `ReflectionTestUtils.invokeMethod` aufgerufen - pruefbar identische
  Berechnung, aber zeitunabhaengig. Im Testklassen-Javadoc dokumentiert.
- MonatsSaldoTest: analoges Problem in kleinerem Rahmen - "Januar 2026" liegt
  zum Testzeitpunkt (08.09.2026) in der Vergangenheit (Cache-Pfad von
  getOrBerechne), "Dezember 2026" noch in der Zukunft (Live-Pfad). Damit die
  drei Tests nicht irgendwann kippen, sobald der Kalender ueber Dezember 2026
  hinauslaeuft, stubbt die gemeinsame Hilfsmethode IMMER zusaetzlich einen
  (ungueltigen) Cache-Eintrag samt Save-Pfad, mit `lenient()` - unabhaengig
  davon, ob der jeweilige Zweig ihn zum Testzeitpunkt tatsaechlich braucht.
  Keine Abweichung von den erwarteten Zahlen, nur von der im Plan knapp
  skizzierten Stubbing-Technik.
- Alle von der Spec erwarteten Zahlen (8 / 0 / 8 / 4,00 / 0 / 40 fuer
  ZeitkontoTest; 8,00 / 4,00 / 0 fuer MonatsSaldoTest und ZeiterfassungApiTest;
  5 bzw. 4 Abwesenheiten fuer UrlaubsantragTest; die vier Kalender-Werte)
  wurden 1:1 bestaetigt - keine Abweichung des tatsaechlichen Bestandsverhaltens
  vom im Plan vorgegebenen erwarteten Wert.

## Abschnitt 1 — Review (Code-Reviewer)

Zeit: 2026-09-08T14:35:00Z
Branch: feature/langzeitkrankmeldung (4b3b4744, gemergte Task-Branches ae6a6f9b + 73242dfe)
Commit(s): f5a78f90 (Task 1), 2d86b623 (Task 2)
Status: fertig
Ampel: 🔴

Was gemacht wurde:
- Volle Backend-Suite gefahren: `mvn -B clean test` -> Tests run: 2486,
  Failures: 0, Errors: 4. Die 4 Errors sind exakt die vier vorbestehenden aus
  der Baseline (AuditChainRepairIntegrationTest 2, AuditHashRoundtripDiagnoseTest 2,
  alle CannotCreateTransaction). 2461 + 25 neue = 2486, wie erwartet.
  Kompilat inklusive Testkompilat sauber. Frontend in diesem Abschnitt nicht
  betroffen, deshalb nicht geprueft.
- Mutationsprobe an allen sechs Aufrufstellen gleichzeitig (je eine Mutation
  pro Produktionsdatei): ZeitkontoService (halber Feiertag nicht mehr halbiert),
  MonatsSaldoService.berechneFeiertagsStunden (dito), ZeiterfassungApiService.
  berechneFeiertagsStunden (dito), AbwesenheitService (halberTag ignoriert),
  UrlaubsantragService (Feiertag nicht mehr uebersprungen),
  ZeitverwaltungController /kalender (Ist-Stunden am Feiertag halbiert).
  Ergebnis: `-Dtest='TagesSollCharakterisierung*'` -> 19 Tests, 6 Failures,
  1 Error - **alle sechs Testklassen sind umgefallen**, jede genau an ihrer
  eigenen Mutation. Das Sicherheitsnetz fuer die Tasks 7-12 traegt.
  Mutationen restlos zurueckgenommen, `git status` und `git diff` wieder leer,
  HEAD unveraendert 4b3b4744.
- V367 gegen MySQL 8.0 hart geprueft: Hibernate selbst das erwartete MySQL-DDL
  erzeugen lassen (SchemaExport mit MySQLDialect) und Spalte fuer Spalte gegen
  die Migration gehalten. Ergebnis: **alle Spalten und Typen stimmen** -
  langzeitkrankmeldung (8 Spalten), langzeitkrankmeldung_phase (6 Spalten),
  abwesenheit +2 Spalten, beide ENUM-Listen wertgleich und in gleicher
  Reihenfolge. `ddl-auto=validate` wird beim Produktionsstart durchgehen.
  Idempotenz: CREATE TABLE IF NOT EXISTS, beide CREATE INDEX und alle vier
  ALTER/ADD-CONSTRAINT-Bloecke ueber information_schema + PREPARE/EXECUTE/
  DEALLOCATE gefuehrt - zweiter Lauf ist ein No-Op. Kein `IF NOT EXISTS` an
  einer Stelle, an der MySQL es nicht kennt.
- Zeitbomben-Pruefung: keine der 25 neuen Zusicherungen haengt an
  `LocalDate.now()`. berechneSollstundenFuerZeitraum und
  MonatsSaldoService.berechneMonatsSaldo rechnen datumsunabhaengig; der
  lenient()-Cache-Stub im MonatsSaldo-Test deckt beide Zweige von
  getOrBerechne ab und ist genau richtig gesetzt; der Kalender-Endpoint hat
  im Tage-Aufbau kein now().

Bedenken / Abweichungen vom Plan:
- 🔴 BLOCKIEREND: `AbwesenheitRepository.sumStundenOhnePhasenTypen`
  (AbwesenheitRepository.java:109-118) liefert falsche Zahlen. Der implizite
  Pfad `a.langzeitkrankmeldungPhase.typ` im WHERE erzeugt bei Hibernate 6
  einen INNER JOIN; das gemessene SQL lautet
  `... from abwesenheit a1_0 join langzeitkrankmeldung_phase lp1_0 on
  lp1_0.id=a1_0.langzeitkrankmeldung_phase_id where ... and
  (a1_0.langzeitkrankmeldung_phase_id is null or lp1_0.typ not in (?,?))`.
  Der Join wirft alle Abwesenheiten ohne Phasenbezug raus, bevor das
  `IS NULL` greifen kann - also den Normalfall. Gemessen mit einer
  Wegwerf-Sonde: ein Krankheitstag ohne Phase (8,00 h) plus ein
  KRANKENGELD-Tag ergibt **0** statt 8,00. Fix (ebenfalls gemessen, liefert
  8,00): explizites `LEFT JOIN a.langzeitkrankmeldungPhase p` und im WHERE
  `(p IS NULL OR p.typ NOT IN :ausgeschlossen)`. Die Query hat heute keinen
  Aufrufer, Task 13 wuerde sie aber uebernehmen und still falsche
  Verrechnungsloehne rechnen. Dazu ein Test in
  LangzeitkrankmeldungRepositoryTest, der genau den Tag ohne Phasenbezug
  mitzaehlt. Die Query stand so im Plan (Zeile ~390) - der Plan-Block gehoert
  mitkorrigiert, sonst baut Task 13 sie erneut so.
- 🟡 `V367SchemaTest` haelt nicht, was sein Javadoc verspricht ("das einzige
  Sicherheitsnetz" gegen eine vergessene Spalte). Der Test prueft nur
  `sql.contains("<spaltenname>")` gegen die gesamte Datei inklusive
  Kopfkommentar - und der Kopfkommentar nennt langzeitkrankmeldung_id,
  langzeitkrankmeldung_phase_id, status, typ und version woertlich. Gemessen:
  beide `ALTER TABLE abwesenheit ADD COLUMN`-Bloecke geloescht -> der Test
  bleibt **gruen** (Tests run: 2, Failures: 0). Vorschlag: vor dem Assert die
  Kommentarzeilen wegschneiden, oder auf die wirksame Anweisung pruefen
  (`ADD COLUMN langzeitkrankmeldung_id`) statt auf den blossen Namen.
- 🟡 ON DELETE SET NULL an den zwei neuen FKs ist fachlich richtig gewaehlt
  (gebuchte Abwesenheitstage duerfen beim Loeschen einer Meldung nicht
  mitgerissen werden). Nur ein Hinweis fuer den Task, der das Loeschen baut:
  MySQL setzt die Spalte auf DB-Ebene, der Persistence-Context sieht das
  nicht - dort muss die Referenz explizit geleert oder das Entity refreshed
  werden.
- 🟡 Der ZeiterfassungApi-Charakterisierungstest ruft die private
  berechneFeiertagsStunden per ReflectionTestUtils auf. Die Begruendung des
  Task-2-Agenten stimmt und ist nachgeprueft: getGesamtSaldo existiert nicht,
  das reale getSaldo koppelt das Enddatum ueber LocalDate.now() und braucht
  drei nicht gesetzte Field-Injection-Abhaengigkeiten. Reflection ist hier
  der richtige Kompromiss. Nur bewusst sein: Task 3 loescht diese private
  Methode, dann muss die Klasse mit umgezogen werden - der Aufruf faellt dabei
  laut auf die Nase, nicht still.
- 🟡 findMitPhasen hat kein DISTINCT. Nachgemessen: Hibernate 6 dedupliziert
  Wurzel-Entitaeten beim JOIN FETCH von selbst (1 Meldung mit 1 Phase -> 1
  Ergebnis). Kein Handlungsbedarf, nur damit es niemand spaeter "repariert".

## Abschnitt 1 — Task 1 (Nachbesserung 1)

Zeit: 2026-09-08T16:20:00Z
Branch: lzk/task-1-datenmodell
Commit(s): ad31b81d
Status: fertig

Was gemacht wurde:
- Befund 1 (blockierend) behoben: `AbwesenheitRepository.sumStundenOhnePhasenTypen` nutzte den impliziten Pfad `a.langzeitkrankmeldungPhase.typ`, den Hibernate 6 zu einem INNER JOIN uebersetzt und damit Abwesenheiten ohne Phasenbezug (Normalfall) stumm ausschloss. Reproduziert mit neuem Test in `LangzeitkrankmeldungRepositoryTest` (rot: 0 statt 8,00 h), dann auf expliziten `LEFT JOIN a.langzeitkrankmeldungPhase p` umgestellt (gruen).
- Befund 2 behoben: `V367SchemaTest` prüfte gegen den Rohtext inkl. Kopfkommentar, der dieselben Spaltennamen nennt wie die Migration selbst — ein Test, der trotz geloeschter ALTER-TABLE-Bloecke gruen bleibt. Fix: alle Zusicherungen laufen jetzt gegen kommentarbereinigten Text, zusaetzlich eine eigene Pruefung auf die tatsaechliche `ADD COLUMN langzeitkrankmeldung_id` / `ADD COLUMN langzeitkrankmeldung_phase_id`-Anweisung (reiner Namens-Check reicht nicht, der Name taucht legitim auch als FK-Spalte in langzeitkrankmeldung_phase auf).
- Beide Luecken vor dem Fix von Hand reproduziert: Migration testweise um die zwei ALTER-TABLE-Bloecke gekuerzt -> alter Test blieb gruen (Luecke bestaetigt) -> neuer Test wurde rot (Luecke geschlossen) -> Migration wiederhergestellt (git diff leer).
- Gate: `./mvnw -B test -Dtest=LangzeitkrankmeldungRepositoryTest,V367SchemaTest` -> Tests run: 8, Failures: 0, Errors: 0.

Bedenken / Abweichungen vom Plan:
- keine

## Abschnitt 1 — Review (Nachprüfung 1)

Zeit: 2026-09-08T15:20:00Z
Branch: feature/langzeitkrankmeldung (cdc67d2d, vorher geprueft 4b3b4744)
Commit(s): ad31b81d (Nachbesserung), c2fbec54 (Doku), cdc67d2d (Merge)
Status: fertig
Ampel: 🟢

Was gemacht wurde:
- Geprueft wurde nur der Diff 4b3b4744..cdc67d2d (3 Code-Dateien). Alles aus
  dem ersten Durchgang (V367 gegen Hibernate-DDL, ENUM-Werte, Idempotenz,
  ON DELETE SET NULL, findMitPhasen, die sechs Charakterisierungs-Mutationen,
  Zeitbomben, DSGVO) wurde nicht neu aufgerollt und ist unveraendert.
- Volle Suite: `mvn -B clean test` -> **Tests run: 2488, Failures: 0,
  Errors: 4**. Die vier sind exakt die bekannten Baseline-Errors
  (AuditChainRepairIntegrationTest 2, AuditHashRoundtripDiagnoseTest 2).
  Anmerkung zur Erwartung "2487": es sind zwei neue Tests dazugekommen, nicht
  einer - der Repository-Regressionstest UND die neue Methode
  abwesenheitBekommtDieNeuenSpaltenPerAlterTable in V367SchemaTest.
  V367SchemaTest 2 -> 3, LangzeitkrankmeldungRepositoryTest 4 -> 5.
  2486 + 2 = 2488, passt.
- Nachpruefung 1 (erzeugtes SQL selbst angesehen): Lauf mit
  -Dspring.jpa.show-sql=true. Das SQL lautet jetzt
  `select coalesce(sum(a1_0.stunden),0) from abwesenheit a1_0
   **left join** langzeitkrankmeldung_phase lp1_0 on
   lp1_0.id=a1_0.langzeitkrankmeldung_phase_id where ... and
   (a1_0.langzeitkrankmeldung_phase_id is null or lp1_0.typ not in (?))`.
   Aus dem INNER JOIN ist ein LEFT JOIN geworden, die Abwesenheit ohne
   Phasenbezug zaehlt mit - der Test steht auf 8,00 und ist gruen.
- Nachpruefung 2 (Mutationsprobe auf den neuen Regressionstest): Query
  testweise wieder auf den impliziten Pfad
  `a.langzeitkrankmeldungPhase.typ` zurueckgebaut ->
  `sumStundenOhnePhasenTypen_zaehltTageOhnePhasenbezugMit` faellt um mit
  **expected: 8.00 but was: 0**. Exakt der Wert, den die Review-Sonde im
  ersten Durchgang gemessen hatte. Der Test schliesst die Luecke wirklich.
  Er ist auch in die Gegenrichtung scharf: die Fixture enthaelt zusaetzlich
  einen KRANKENGELD-Tag mit 6,00 h, ein Wegfall der Ausschluss-Bedingung
  ergaebe 14,00 statt 8,00.
- Nachpruefung 3 (Mutationsprobe auf V367SchemaTest), zwei Mutationen:
  (a) `ALTER TABLE abwesenheit ADD COLUMN langzeitkrankmeldung_id`-Block
      geloescht -> `abwesenheitBekommtDieNeuenSpaltenPerAlterTable` rot
      ("to contain: ADD COLUMN langzeitkrankmeldung_id"). Genau die Luecke,
      die im ersten Durchgang gruen geblieben war, ist zu.
  (b) Spalte `version BIGINT NOT NULL DEFAULT 0` aus dem CREATE TABLE
      geloescht, waehrend der Kopfkommentar das Wort "version" weiterhin
      nennt -> `enthaeltAlleErwartetenSpalten` rot ("to contain: version").
      Das ist der direkte Beweis, dass die Kommentarbereinigung greift: ohne
      sie waere diese Mutation gruen durchgelaufen.
  Beide Mutationen zurueckgenommen.
- Gegenprobe "hat die Umstellung etwas entschaerft?": nein. Die zwei
  Zusicherungen langzeitkrankmeldung_id / langzeitkrankmeldung_phase_id sind
  aus der schwachen contains-Liste in den strikteren
  ADD-COLUMN-Test gewandert - das ist strikt staerker, keine ist
  weggefallen. `ohneKommentare` entfernt nur ganze Zeilen, die nach Trim mit
  "--" beginnen; in V367 steht auf keiner Zeile SQL vor einem Kommentar
  (geprueft), es geht also kein pruefbarer Text verloren.
- Alle Mutationen restlos zurueckgenommen: `git status --porcelain
  --untracked-files=all` leer, `git diff` leer, HEAD unveraendert cdc67d2d.

Bedenken / Abweichungen vom Plan:
- Keine. Beide 🔴-Befunde aus dem ersten Durchgang sind sachlich behoben und
  jeweils durch eine eigene Mutationsprobe als wirksam nachgewiesen. Die
  🟡-Hinweise aus dem ersten Durchgang (ON DELETE SET NULL im
  Persistence-Context beim spaeteren Loeschpfad, Reflection-Aufruf im
  ZeiterfassungApi-Charakterisierungstest, findMitPhasen ohne DISTINCT)
  bleiben als Hinweise stehen und blockieren nicht.
- Abschnitt 1 ist aus Sicht des Code-Reviews abgenommen.

---

# ⏸ HIER GEHT ES WEITER (Stand 08.09.2026, 16:35)

Pause wegen Nutzungslimit. **Nichts ist verloren, nichts halb fertig.**

## Wo wir stehen

- **Abschnitt 1: 🟢 abgenommen**, gemerged und gepusht.
  `origin/feature/langzeitkrankmeldung` = `a1e628b8`, lokal identisch.
  Enthält Datenmodell, Migration V367, Repositories und die sechs
  Charakterisierungs-Testklassen. Suite: 2488 Tests, genau die 4 bekannten
  Baseline-Errors.
- **Abschnitt 2 war gestartet und wurde sauber gestoppt**, bevor irgendein
  Agent eine Datei geschrieben hat. Die drei Worktrees sind **leer** und auf
  dem richtigen Branch — kein Halbzustand, nichts zu heilen.

## Der nächste Schritt, ohne Nachdenken

Drei Coding-Agenten (`loese-problem-coding`) parallel in **einer** Nachricht
starten. Worktrees und Branches existieren bereits, **nicht** neu anlegen:

| Task | Worktree | Branch |
| --- | --- | --- |
| 3 — `TagesSollService` | `../wt/lzk-task-3` | `lzk/task-3-tagessoll` |
| 13 — Verrechnungslohn | `../wt/lzk-task-13` | `lzk/task-13-verrechnungslohn` |
| 14 — Desktop-Bausteine | `../wt/lzk-task-14` | `lzk/task-14-bausteine` |

`react-pc-frontend/node_modules` in `lzk-task-14` ist bereits als Symlink
gesetzt und geprüft.

**Wichtig für die Aufträge:**

- Task 3 ist der Flaschenhals — er liefert die drei Größen `periodenSoll`,
  `feiertagsGutschrift` und `arbeitsSoll`, und die
  `TagesSollCharakterisierung*`-Tests müssen grün bleiben. Ohne ihn geht in
  Abschnitt 3 nichts.
- Task 13 und 14 hängen von niemandem ab. Wenn das Budget knapp ist: **Task 3
  allein starten**, die anderen beiden später nachziehen.
- Task 14: **kein** `npm ci`/`npm install` (Symlink!), und `ui-ux-pro-max`
  **ohne** Namespace-Präfix aufrufen.

Danach: mergen (`git merge --no-ff`), Code-Reviewer in eigenem Worktree,
dann Abschnitt 3 mit den Tasks 4, 7, 8, 9, 10, 11 zusammen.

## Danach noch offen

Abschnitt 3 (Tasks 4, 7–11) → Abschnitt 4 (Tasks 5, 6, 12) →
Abschnitt 5 (Tasks 15–19, Frontend, mit Design-Reviewer) → PR → Merge.
Die Einteilung steht im Plan unter „## Abschnitts-Einteilung (Runden)".

## Abschnitt 2 — Task 3 (Coding-Agent)

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/task-3-tagessoll
Commit(s): ef32b9ad
Status: fertig

Was gemacht wurde:
- `TagesSollService` neu angelegt (`src/main/java/org/example/kalkulationsprogramm/service/TagesSollService.java`), buendelt die bisher an drei Stellen duplizierte Feiertags-/Sollstunden-Logik (`ZeitkontoService.berechneSollstundenFuerZeitraum`, `MonatsSaldoService.berechneFeiertagsStunden`, `ZeiterfassungApiService.berechneFeiertagsStunden`).
- Drei benannte Groessen exakt nach E2/Task-3-Interface: `periodenSoll`, `feiertagsGutschrift`, `arbeitsSoll = periodenSoll - feiertagsGutschrift`, jeweils Einzeltag- und Zeitraum-Variante (`periodenSollSumme`, `feiertagsGutschriftSumme`).
- Gemeinsame `tagesBasis(phasen, konto, tag)`: Zeitkonto-Soll, bei laufender Wiedereingliederungsphase ersetzt durch `stundenProTag`, gedeckelt auf das Zeitkonto-Soll (Sicherheitsnetz). Feiertagsbehandlung laeuft danach unveraendert weiter (E3 — kein fester Override, 2h/Tag am vollen Feiertag ergibt 2.00/2.00/0).
- Zeitraum-Methoden laden `phaseRepository.findImZeitraum` und `feiertagService.getFeiertageZwischen` je genau einmal (kein N+1), Feiertags-Liste wird auf `"BY".equals(bundesland)` gefiltert (Kommentar im Code, eigener Test dafuer) — sonst wuerde die Summenmethode anders rechnen als die Einzeltag-Methode, weil `getFeiertageZwischen` anders als `istFeiertag`/`istHalberFeiertag` nicht selbst nach Bundesland filtert.
- `TagesSollServiceTest` (18 Tests, TDD: erst rot wegen fehlender Klasse, dann gruen) deckt alle neun Pflichtfaelle aus dem Plan ab (a–i) plus N+1-Nachweis fuer beide Summenmethoden, Korrektheits- und Bundesland-Filter-Tests.
- Alle sechs `TagesSollCharakterisierung*`-Testklassen aus Task 2 (19 Tests) liefen unveraendert mit und blieben gruen — nichts an ihnen angefasst.
- Gate: `./mvnw -B test -Dtest='TagesSollServiceTest,TagesSollCharakterisierung*'` → Tests run: 37, Failures: 0, Errors: 0, BUILD SUCCESS.

Finale Methodensignaturen (fuer die sechs Folge-Tasks in Abschnitt 3):
```java
package org.example.kalkulationsprogramm.service;

@Service
@RequiredArgsConstructor
public class TagesSollService {
    public BigDecimal periodenSoll(Long mitarbeiterId, Zeitkonto konto, LocalDate tag);
    public BigDecimal feiertagsGutschrift(Long mitarbeiterId, Zeitkonto konto, LocalDate tag);
    public BigDecimal arbeitsSoll(Long mitarbeiterId, Zeitkonto konto, LocalDate tag);
    public BigDecimal periodenSollSumme(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis);
    public BigDecimal feiertagsGutschriftSumme(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis);
}
```
Es gibt bewusst kein `arbeitsSollSumme` — Aufrufer, die das Zeitraumsoll brauchen, bilden `periodenSollSumme(...) - feiertagsGutschriftSumme(...)` selbst (so auch im Plan-Interface vorgesehen).

Bedenken / Abweichungen vom Plan:
- keine. Interface, Rechenregel (E2/E3) und Steps 1:1 wie im Plan-Block "Task 3" umgesetzt. `src/main/resources/static/index.html` zeigt wie in allen Worktrees eine reine Zeilenenden-Normalisierung — nicht angefasst, nicht committet.

## Abschnitt 2 — Task 13 (Coding-Agent)

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/task-13-verrechnungslohn
Commit(s): 4a7ae021
Status: fertig

Was gemacht wurde:
- VerrechnungslohnService: neue private Methode `ausgeklammerteTage(mitarbeiterId, von, bis)` liest einmal je Mitarbeiter ueber `LangzeitkrankmeldungPhaseRepository.findImZeitraum` die Krankengeld-/Wiedereingliederungs-Kalendertage; das Ergebnis wird in der `berechne()`-Schleife einmal ermittelt und an `berechneLohnZeile` UND `berechneStundenZeile` durchgereicht (keine doppelte Abfrage).
- `jahresSollstundenAusZeitkonto` und `werktagsSollOhneZeitkonto` bekommen den zusaetzlichen Parameter `ausgeklammert`, ueberspringen diese Tage neben dem Feiertagsfilter und liefern zusaetzlich die uebersprungenen Sollstunden zurueck (neuer interner Ergebnistyp `SollUndAusklammerung`).
- Krankheitsstunden nutzen jetzt `AbwesenheitRepository.sumStundenOhnePhasenTypen(...)` statt der alten Typ-Summe; der KRANKHEITSTAGE_DEFAULT-Fallback greift nur noch, wenn `ausgeklammerteTage == 0`.
- `berechneLohnZeile`: neuer `anwesenheitsFaktor = (Jahrestage - ausgeklammerteTage) / Jahrestage` (4 Nachkommastellen, HALF_UP), multipliziert auf `gesamtkosten` in beiden Zweigen (GF und regulaer).
- Die vier bestehenden `getSollstundenFuerTag`-Aufrufe (Zeile 462/471/506/535 vor der Aenderung) bleiben unveraendert beim rohen Zeitkonto-Wert -- kein TagesSollService, wie im Plan als Ausnahme festgehalten. Kommentar dazu direkt am Feldblock ergaenzt.
- `VerrechnungslohnErgebnisDto`: `MitarbeiterStundenZeile` bekommt `ausgeklammerteTage` (int) und `ausgeklammerteStunden` (BigDecimal, Default ZERO); `MitarbeiterLohnZeile` bekommt `ausgeklammerteTage` (int) und `anwesenheitsFaktor` (BigDecimal, Default ONE).
- Konstruktor von `VerrechnungslohnService` um 16. Parameter `LangzeitkrankmeldungPhaseRepository phaseRepository` erweitert.
- Tests (TDD, rot vor der Umsetzung wegen fehlendem Konstruktor-Parameter/fehlenden Feldern): `VerrechnungslohnServiceTest` um Default-Stub `phaseRepository.findImZeitraum(any(), any(), any()) -> emptyList()` in `@BeforeEach` ergaenzt (haelt alle 28 bestehenden Tests unveraendert gruen), plus zwei neue Tests: `krankengeldPhaseKlammertKalendertageUndAnteiligeLohnkostenAus` (Krankengeld 01.03.-30.06.2024, 122 Kalendertage / 86 Werktage, Jahressoll 2096->1408 h, anwesenheitsFaktor 0,6667, Lohnkosten anteilig) und `normalerKrankheitstagOhneLangzeitkrankmeldungWirdWeiterhinVollGezaehlt` (Normalfall ohne jeden Phasenbezug: die neue Query liefert die Stunden unveraendert durch, kein Default-Fallback).
- Gate: `./mvnw -B test -Dtest=VerrechnungslohnServiceTest` -> 30 Tests, 0 Failures, 0 Errors, BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- Keine inhaltliche Abweichung. Eine Interpretationsentscheidung, die der Plantext offenliess: der `anwesenheitsFaktor` wird sowohl im Geschaeftsfuehrer- als auch im regulaeren Zweig von `berechneLohnZeile` angewendet (Plan sagt nur "am Ende von berechneLohnZeile", die Methode hat aber zwei Rueckgabepunkte). Falls ein GF explizit von der Ausklammerung ausgenommen bleiben soll, bitte im naechsten Review vermerken.
- `src/main/resources/static/index.html` zeigt im Worktree weiterhin als geaendert (reine Zeilenenden-Normalisierung) -- nicht angefasst, nicht committet, wie in der Aufgabenbeschreibung vermerkt.

Feldnamen im erweiterten VerrechnungslohnErgebnisDto (fuer Task 16):
- `MitarbeiterStundenZeile.ausgeklammerteTage` (int)
- `MitarbeiterStundenZeile.ausgeklammerteStunden` (BigDecimal, Default ZERO)
- `MitarbeiterLohnZeile.ausgeklammerteTage` (int)
- `MitarbeiterLohnZeile.anwesenheitsFaktor` (BigDecimal, Default ONE)

## Abschnitt 2 — Task 14 (Coding-Agent)

Zeit: 2026-09-08T15:38:24Z
Branch: lzk/task-14-bausteine
Commit(s): 0b701bba
Status: fertig

Was gemacht wurde:
- `react-pc-frontend/src/components/langzeitkrankmeldung/phasen.ts` (neu):
  `PhasenTyp`, `Phase`, `PHASEN_LABEL`, `PHASEN_BADGE`, `formatDatum` — Wording
  wörtlich aus der Spec ("Lohnfortzahlung durch den Betrieb", "Krankengeld der
  Krankenkasse", "Wiedereingliederung"), Badge-Farben amber/blue/teal wie im
  Plan vorgegeben.
- `PhasenZeitleiste.tsx` (neu): senkrechte Liste, Punkt + Badge + Zeitraum
  ("ab DD.MM.YYYY" bzw. "DD.MM.YYYY – DD.MM.YYYY"), Stunden pro Tag bei
  Wiedereingliederung, heute laufende Phase mit `ring-2 ring-rose-400`
  hervorgehoben (`flex flex-col gap-3`, `min-w-0` auf jeder Text-Ebene laut
  kriterien.md). Props exakt wie im Plan: `{ phasen: Phase[]; heute?: string }`.
- `StufenplanTabelle.tsx` (neu): Tabelle mit Spalten ab/bis/Stunden pro
  Tag/Aktion, gefiltert auf `typ === 'WIEDEREINGLIEDERUNG'`; Zeile zum
  Hinzufügen mit `<DatePicker>` + Zahlenfeld, Validierung (Stunden > 0 und
  <= maxStundenProTag) mit Inline-Fehlertext (`role="alert"`) und
  `toast.error`; Löschen destruktiv über `useConfirm`
  ("Ja, löschen"/"Abbrechen"); `disabled`-Buttons mit `title`-Begründung.
  Props exakt wie im Plan: `{ phasen: Phase[]; onHinzufuegen: (p: {
  vonDatum: string; bisDatum: string | null; stundenProTag: number }) =>
  Promise<void>; onLoeschen: (phasenId: number) => Promise<void>;
  maxStundenProTag: number; disabled?: boolean }`.
- Tests (TDD, erst rot dann grün): `PhasenZeitleiste.test.tsx` (5 Tests:
  Wording/Badge je Typ, offener/geschlossener Zeitraum, Stunden pro Tag,
  Hervorhebung der laufenden Phase, Leerhinweis) und
  `StufenplanTabelle.test.tsx` (7 Tests: Leerzustand, bestehende Stufen,
  Filterung auf Wiedereingliederung, Ablehnung über dem Stundenlimit ohne
  onHinzufuegen-Aufruf, erfolgreiches Hinzufügen, Rückfrage vor dem Löschen,
  disabled-Begründung). Ausschließlich Dummy-Daten.
- Gates: `npx vitest run src/components/langzeitkrankmeldung` — 2 Testdateien,
  12/12 grün. `npm run lint` — exit 0, 0 Errors, genau die eine bekannte
  Warning (`BelegeKasseEditor.tsx:1204`), keine neue. `npm run build` — exit 0
  (`tsc -b && vite build` durch); Build-Artefakte danach verworfen
  (`git checkout -- src/main/resources/static/index.html` +
  neu erzeugte, noch ungetrackte Bundle-Dateien manuell gelöscht).
- Kein Playwright in diesem Task (laut Plan/Auftrag — Bausteine ohne
  Task-15-Seite nicht sinnvoll im Browser prüfbar). Kein Dev-Server gestartet.

Bedenken / Abweichungen vom Plan:
- Der Plan spezifiziert das Wording für die Phasen und für den
  Meldung-Status ("Läuft noch"/"Wieder voll im Einsatz"/"Zurückgenommen"),
  aber nicht den genauen Text für zwei Stellen, die die Interfaces offen
  lassen: (1) den Leerhinweis in `PhasenZeitleiste`, falls `phasen` leer ist
  (im Plan nur für `StufenplanTabelle` wörtlich vorgegeben: "Noch kein
  Stufenplan hinterlegt." — das ist umgesetzt); ich habe für
  `PhasenZeitleiste` ergänzend "Für diese Krankmeldung sind noch keine
  Phasen hinterlegt." gewählt (eigene Formulierung, Praxisfall dürfte selten
  sein, da eine laufende Meldung i.d.R. immer mindestens eine Phase hat).
  (2) den `title`-Text für deaktivierte Buttons in `StufenplanTabelle`, wenn
  `disabled=true` übergeben wird — die Komponente kennt den konkreten Grund
  nicht (kommt von Task 15 je nach Meldung-Status), ich habe "Nur möglich,
  solange die Krankmeldung läuft." als generische Begründung gewählt. Task 15
  kann/sollte das bei Bedarf spezifischer machen (z.B. eigene Prop für den
  Grund), falls der Review das für nötig hält.
- `Phase.label` ist Teil des Interfaces (wie im Plan vorgegeben), wird von
  den beiden Komponenten selbst aber nicht gerendert — sie leiten das Wording
  intern aus `PHASEN_LABEL[typ]` ab (deterministisch, nicht vom Aufrufer
  abhängig). Das Feld bleibt im Typ für Task 15 nutzbar, falls dort ein
  Feld direkt vom Backend gebraucht wird.
- `git status` zeigt weiterhin `M src/main/resources/static/index.html`
  (Zeilenend-Normalisierung, laut Auftrag nicht anfassen) — nicht committet.
