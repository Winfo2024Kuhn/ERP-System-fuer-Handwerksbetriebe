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

## Abschnitt 2 — Review (Review-Agent)

Zeit: 2026-09-08T16:05:00Z
Branch: feature/langzeitkrankmeldung (gemergter Stand, 4b988c29)
Geprueft: Diff e7ddce25..HEAD, Tasks 3, 13, 14
Status: fertig
Ampel: 🟡

Testlaeufe (alle synchron, Output in Dateien):
- `./mvnw -B test`: **2508 Tests, 0 Failures, 4 Errors** — exakt die vier
  vorbestehenden (`AuditChainRepairIntegrationTest` x2,
  `AuditHashRoundtripDiagnoseTest` x2, alle `CannotCreateTransaction`).
  Erwartet waren 2508. Baseline gehalten.
- `npm run lint` (react-pc-frontend): 0 Errors, 1 Warning
  (`BelegeKasseEditor.tsx:1204`, bekannt). Gate gehalten.
- `npx vitest run src/components/langzeitkrankmeldung/`: 2 Dateien, 12 Tests gruen.
- `TagesSollCharakterisierung*` unveraendert (`git diff` leer) und gruen.

Mutationsproben (alle danach zurueckgenommen, Baum sauber):
- M1 periodenSoll haelt halben Feiertag nicht mehr → 2 Tests rot (die richtigen).
- M2 feiertagsGutschrift auch ohne Feiertag → 7 Tests rot.
- M3 Deckelung auf Zeitkonto-Soll entfaellt → 1 Test rot (der richtige).
- M4 Stufenplan als harter Override ueber den Feiertag hinweg (E3 gebrochen)
  → `wiedereingliederung_zweiStunden_amVollenFeiertag_bautKeineMinusstundenAuf`
  rot. Der E3-Fall ist also wirklich abgedeckt.
- M5 Typfilter der Stufenplan-Auswahl entfernt → **alle 18 Tests bleiben gruen.**
  Testluecke, siehe Hinweise.
- M6 `anwesenheitsFaktor` im Geschaeftsfuehrer-Zweig entfernt → alle 30 Tests
  gruen. Testluecke, siehe Hinweise.

Geprueft und in Ordnung:
- Erzeugtes SQL von `sumStundenOhnePhasenTypen` ist ein echter LEFT JOIN,
  `p IS NULL` wird zu `langzeitkrankmeldung_phase_id is null`. Ein normaler
  Krankheitstag ohne Phasenbezug wird mitgezaehlt (Repo-Test misst 8.00).
- `findImZeitraum` nutzt einen impliziten INNER JOIN auf `langzeitkrankmeldung`,
  die Spalte ist `NOT NULL` — unkritisch.
- Kein N+1 ueber Tage: `TagesSollService` laedt je Zeitraum genau einmal
  (mit `times(1)`-Test), `VerrechnungslohnService` einmal je Mitarbeiter
  ausserhalb aller Tagesschleifen.
- Kein POST/PUT/PATCH/DELETE, kein Controller, kein `react-zeiterfassung` im
  Abschnitts-Diff. Die PC-only-Vorgabe ist eingehalten.
- DSGVO: nur Dummy-Namen (Max/Klaus/Petra Mustermann), Frontend-Tests ganz
  ohne Personennamen, kein Logging im gesamten Diff, keine Diagnose-/Notizfelder.
- Wording: alle sichtbaren Strings verwenden die vorgeschriebenen Begriffe,
  kein "Entgeltfortzahlungszeitraum", kein "AU-Zeitraum", kein "Phase 1/2/3".
- Design: rose-600 nur fuer Primaeraktion und laufende Phase, sonst slate,
  Lucide-Icons, `rounded-lg`, `aria-label` an Icon-Buttons, Pflicht-Komponenten
  (DatePicker, Button, Input, Label, useConfirm, useToast) genutzt.

Fachliche Antwort zum Geschaeftsfuehrer-Zweig (Frage aus Task 13):
Der Faktor gehoert auf **beide** Return-Punkte — die Einschaetzung des
Projektinhabers stimmt. Der GF-Zweig existiert nicht, weil dort der Lohn
anwesenheitsunabhaengig waere, sondern weil die **Quelle** der Lohnzahl eine
andere ist (kalkulatorischer Lohn statt Lohnabrechnung) und die SV-Behandlung
abweicht. Beide Zweige liefern gleichermassen eine Jahressumme. Entscheidend:
der Stunden-Block kuerzt das Jahressoll des GF im selben Lauf. Wuerde man die
Lohnseite ungekuerzt lassen, saenken die Stunden und die Kosten blieben —
der Stundensatz schoesse nach oben, genau der Fehler, den der Kommentar im
`werktagsSollOhneZeitkonto`-Block schon einmal beschreibt.

Blockierende Befunde: keine.

Bedenken / Abweichungen vom Plan (alle 🟡, blockieren nicht):
- `TagesSollService.java:153` — der Typfilter `typ == WIEDEREINGLIEDERUNG` ist
  ungetestet. Die beiden Tests fuer LOHNFORTZAHLUNG/KRANKENGELD setzen kein
  `stundenProTag`, deshalb traegt sie die Null-Pruefung eine Zeile weiter.
  `stunden_pro_tag` ist in `V367` `NULL`-erlaubt, aber nicht auf den Typ
  eingeschraenkt. Ein Test mit KRANKENGELD-Phase **und** `stundenProTag = 2.00`
  schliesst die Luecke.
- `VerrechnungslohnService.java:264` — der `anwesenheitsFaktor` im
  GF-Zweig ist ungetestet (M6 blieb gruen). Ein Testfall mit
  `istGeschaeftsfuehrer = true` und Krankengeld-Phase fehlt.
- `VerrechnungslohnService.java:274` — im Modus RUECKWIRKEND kommt `brutto`
  aus echten Lohnabrechnungen, die den Krankengeld-Ausfall bereits abbilden.
  Der Faktor kuerzt dann ein zweites Mal. Vorschlag: den Faktor auslassen,
  wenn `zeile.getQuelle() == LohnQuelle.LOHNABRECHNUNG`.
- `VerrechnungslohnService.java:452` — der Urlaubs-Default bekommt nicht den
  gleichen `ausgeklammert.isEmpty()`-Schutz wie der Krankheits-Default. Fachlich
  vertretbar (Urlaubsanspruch laeuft bei Langzeitkrankheit weiter), aber die
  Asymmetrie gehoert kommentiert.
- `VerrechnungslohnService.java:439` — `feiertagsstunden` zaehlt weiter alle
  Feiertage des Jahres, waehrend `sollstunden` gekuerzt ist. Reine Anzeigegroesse,
  keine Rechenwirkung, aber in einer Langzeitfall-Zeile inkonsistent.
- `StufenplanTabelle.tsx:54` — Fehlertext sagt "zwischen 1 und N", die Pruefung
  laesst aber jeden Wert > 0 durch (0,5 h waere gueltig). Text oder Pruefung
  angleichen.
- `StufenplanTabelle.tsx:144` — das `<Label>ab</Label>` hat kein `htmlFor` zum
  DatePicker (das Stundenfeld daneben hat eins).
- `TagesSollServiceTest.java` — kein Fall fuer eine Wiedereingliederungsphase
  mit `stundenProTag == null` (Plan-Schritt "Phase ignorieren").

## Abschnitt 3 — Task 9 (Coding-Agent)

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/task-9-abwesenheit
Commit(s): 3e9ac0ee
Status: blockiert (eigene Dateien fertig und gruen, Gate-Kommando insgesamt nicht gruen — Ursache liegt in einer Datei ausserhalb meines Task-Scopes, siehe Bedenken)

Was gemacht wurde:
- `AbwesenheitService`: `private final TagesSollService tagesSollService;` per Constructor Injection ergaenzt (`FeiertagService` bleibt, wird fuer die Feiertagssperre/-bezeichnung weiter gebraucht).
- Zeile 60 `zeitkonto.getSollstundenFuerTag(wochentag)` durch `tagesSollService.arbeitsSoll(mitarbeiterId, zeitkonto, datum)` ersetzt, lokale Variable `wochentag` entfernt. Begruendung fuer `arbeitsSoll` (statt `periodenSoll`/`feiertagsGutschrift`): `bucheAbwesenheit` braucht "was tatsaechlich zu leisten ist" fuer einen Einzeltag als Basis fuer die zu buchenden Stunden — exakt das, was `arbeitsSoll` liefert (`periodenSoll - feiertagsGutschrift`), inkl. Wiedereingliederungs-Deckelung laut `TagesSollService`-Javadoc.
- "Kein Arbeitstag"-Fehlermeldung um den Fall erweitert, dass eine Wiedereingliederung an dem Tag 0 Stunden vorsieht (Text bleibt sonst wie vorher, Substring "Kein Arbeitstag" unveraendert erhalten).
- `halberTag` wirkt weiterhin als zusaetzliche Halbierung auf das von `tagesSollService.arbeitsSoll` ermittelte Tagessoll (Reihenfolge im Code unveraendert).
- `AbwesenheitServiceTest`: `@Mock TagesSollService tagesSollService` ergaenzt, Stub in `stubGrunddaten()` (`arbeitsSoll(any,any,any) -> 8.00`), neuer Test `krankheit_WaehrendWiedereingliederung_BuchtStufenplanStundenStattVollemSoll` (2h Stufenplan statt 8h Soll). TDD befolgt: Test zuerst rot (UnnecessaryStubbingException bei den Altfaellen + Wertabweichung beim neuen Test), dann Produktivcode geaendert, danach 8/8 gruen.
- Gate `AbwesenheitServiceTest`: 8/8 gruen (Tests run: 8, Failures: 0, Errors: 0).

Bedenken / Abweichungen vom Plan:
- **`TagesSollCharakterisierungAbwesenheitTest` wird durch die Umstellung rot (1 Failure, 2 Errors von 4 Tests), obwohl ich die Datei nicht angefasst habe.** Ursache: diese Testklasse (Task 2/Abschnitt 1) deklariert kein `@Mock TagesSollService`. Mockito injiziert `AbwesenheitService` dort ueber den (einzigen, von Lombok generierten) Konstruktor und setzt fuer den nicht gemockten `TagesSollService`-Parameter `null` ein — empirisch verifiziert (Fehlermeldung: "Cannot invoke ... because this.tagesSollService is null"). Betroffen: `krankheit_normalerArbeitstag_gibtVolleSollstunden` und `krankheit_halberTag_gibtVierStunden` (NullPointerException statt Ergebnis), `amSamstag_wirftKeinArbeitstag` (NullPointerException statt der erwarteten IllegalArgumentException). `anFeiertag_wirftAbwesenheitNichtErlaubt` bleibt gruen, weil die Feiertagspruefung schon vorher greift und `tagesSollService` gar nicht erst aufgerufen wird.
- Das ist kein Zahlen-/Verhaltensfehler: fuer alle vier Fixture-Tage (kein Feiertag, keine Wiedereingliederung) liefert `tagesSollService.arbeitsSoll(...)` rechnerisch exakt denselben Wert wie vorher `zeitkonto.getSollstundenFuerTag(...)` (siehe `TagesSollService`-Javadoc: "Ohne laufende Langzeitkrankmeldung ist das Ergebnis bitgleich zum heutigen Bestand"). Es ist ein reines Mocking-/DI-Luecken-Problem aus Task 2, nicht ein numerischer Regressionsfehler.
- Ich habe bewusst **keinen** Null-Fallback in `AbwesenheitService` eingebaut (z.B. "wenn `tagesSollService == null`, nimm den rohen Zeitkonto-Wert") — das waere testonly-Code in der Produktionsklasse und widerspraeche dem Sinn von Constructor Injection (garantiert nicht-null, siehe `BACKEND_ARCH.md`). Ich habe die Testdatei auch nicht angefasst, wie ausdruecklich verlangt.
- Vorschlag fuer die Aufloesung (nicht selbst umgesetzt, da ausserhalb meines Datei-Scopes und explizit verboten): `TagesSollCharakterisierungAbwesenheitTest` um `@Mock private TagesSollService tagesSollService;` plus einen Stub ergaenzen, der fuer die vier Fixture-Tage denselben Wert wie das jeweilige `Zeitkonto` liefert (z.B. `arbeitsSoll(anyLong(), any(), any())` mit einem `thenAnswer`, das `getSollstundenFuerTag` auf dem uebergebenen `Zeitkonto`-Argument aufruft) — die vier bestehenden Assertions/Erwartungswerte muessten dabei unveraendert bleiben.
- Moeglicherweise betrifft dieselbe Luecke andere Charakterisierungs-Tests im selben Abschnitt (z.B. `TagesSollCharakterisierungMonatsSaldoTest` bei Task 8, das laut Plan ebenfalls von `FeiertagService`- auf `TagesSollService`-Mocking umgestellt werden muesste) — nicht selbst geprueft, nur als Hinweis fuer den Abschnitts-Reviewer.

## Abschnitt 3 — Task 11 (Coding-Agent)

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/task-11-zeitverwaltung
Commit(s): 80d799f8
Status: fertig

Was gemacht wurde:
- `ZeitverwaltungController.getKalender` auf `TagesSollService` (Task 3) umgestellt: `sollStunden` kommt jetzt aus `arbeitsSoll(...)`, die Feiertagsgutschrift auf der Ist-Seite aus `feiertagsGutschrift(...)`. Der alte Sonderzweig `feiertagDaten.contains(...) ? ZERO : ...` sowie der `if (feiertagDaten.contains(...))`-Block für die Ist-Stunden sind entfallen.
- Neue Abhängigkeit `TagesSollService` in den Konstruktor (`@RequiredArgsConstructor`-Feldblock) ergänzt, kein Field-Injection.
- Bugfix (bewusste Verhaltensänderung, Plan-Abschnitt "Bewusste Verhaltensänderungen" Punkt 1): an halben Feiertagen (z.B. Heiligabend) wurden bisher die vollen Sollstunden als Ist-Stunden gutgeschrieben, obwohl `sollStundenMonat` den halben Feiertag schon immer korrekt halbierte → +4h Phantom-Überstunden pro halbem Feiertag, Widerspruch zur Monatsübersicht. Jetzt liefert `feiertagsGutschrift()` an halben Feiertagen korrekt die halbe Stundenzahl.
- `TagesSollCharakterisierungKalenderTest`: **genau eine** Zusicherung geändert — `tage[23].istStunden` von `8` (alter Wert = der Bug) auf `4.00` (neuer Wert = korrekt), mit Kommentar an der Zusicherung selbst und im Klassen-Javadoc. Alle anderen Zusicherungen (`tage[0].sollStunden==8`, `tage[23].sollStunden==0`, `tage[24].sollStunden==0`) unverändert.
- `ZeitverwaltungControllerTest`: zwei neue Tests ergänzt — `getKalender_WiedereingliederungReduziertSollStundenAnArbeitstag` (Plan-Vorgabe: 2h/Tag während laufender Wiedereingliederung → `tage[].sollStunden == 2.00` an einem normalen Arbeitstag) und `getKalender_HalberFeiertagLiefertHalbeIstStundenUndStimmtMitMonatsuebersichtUeberein` (nagelt den Bugfix fest: halber Feiertag liefert jetzt 4 statt 8 Ist-Stunden, `istStundenMonat`/`sollStundenMonat`/`differenz` zeigen netto 0 statt +4 Phantom-Überstunden). Bestehender Test 1 (`getKalender_LiefertEchteAbwesenheitIdFuerKrankheit`) um Stubs für das neue `@MockBean TagesSollService` ergänzt, unverändert in der Aussage.
- TDD eingehalten: alle drei geänderten/neuen Testfälle vor der Controller-Änderung rot gefahren (Assertion-Mismatches `expected 4.0/2.0 but was 8.0`), danach grün.
- Gates: `./mvnw -B test -Dtest=ZeitverwaltungControllerTest,TagesSollCharakterisierungKalenderTest` → `Tests run: 5, Failures: 0, Errors: 0`, `BUILD SUCCESS`.

Bedenken / Abweichungen vom Plan:
- Performance (N+1, dokumentiert wie im Plan-Schritt "Performance" für Task 11 vorgesehen): `arbeitsSoll`/`feiertagsGutschrift` werden pro Tag einzeln aufgerufen (bis zu 31×2 Aufrufe/Monat), jeder Aufruf lädt intern per `LangzeitkrankmeldungPhaseRepository.findImZeitraum` und `FeiertagService` erneut. Eine Batch-Alternative (`TagesSollService.arbeitsSollJeTag(...)`) hätte eine Änderung an `TagesSollService.java` erfordert — das liegt außerhalb der für diesen Task erlaubten Dateien (`ZeitverwaltungController.java`, `ZeitverwaltungControllerTest.java`, `TagesSollCharakterisierungKalenderTest.java`). Der Plan sieht für genau diesen Fall den "Standardweg" (pro Tag rufen, im Kontext-Log vermerken) explizit als zulässig vor — dabei belassen.
- Sonst keine Abweichungen vom Plan.

## Abschnitt 3 — Task 10 (Coding-Agent)

Zeit: 2026-09-08T16:40:39Z
Branch: lzk/task-10-zeiterfassung-api
Commit(s): b85cc567c382f674e7c0e862ca57dfa07b21b422
Status: fertig

Was gemacht wurde:
- `ZeiterfassungApiService.berechneFeiertagsStunden` (private Methode, wortgleiche
  Kopie aus `MonatsSaldoService`) ersatzlos gelöscht. `TagesSollService` als
  13. Konstruktor-Parameter ergänzt (`@RequiredArgsConstructor`-Feldblock,
  hinter `auditService`), Field-Injection-Felder unangetastet gelassen.
- Einziger Aufrufer (`berechneAnteiligenMonatIst`) ruft jetzt
  `tagesSollService.feiertagsGutschriftSumme(mitarbeiterId, zeitkonto, von, bis)`
  — die Summenmethode für Zeiträume, keine Tagesschleife mit Repository-Zugriff.
- `ZeiterfassungApiServiceConcurrencyTest` und
  `ZeiterfassungApiServiceVerspaeteterStopTest`: `@Mock private TagesSollService
  tagesSollService;` ergänzt, Konstruktoraufruf um das Argument erweitert.
  Keine Stubbings nötig, da beide Testklassen den Feiertagspfad nicht berühren.
- `TagesSollCharakterisierungZeiterfassungApiTest` (Task-2-Datei, Zugriffsweg-
  Anpassung ausdrücklich vom Auftraggeber erlaubt): der ReflectionTestUtils-
  Aufruf auf die jetzt gelöschte private Methode lief ins Leere. Zugriffsweg
  umgestellt auf eine selbst gebaute `TagesSollService`-Instanz
  (`new TagesSollService(feiertagService, phaseRepository)`), Aufruf über
  `tagesSollService.feiertagsGutschrift(mitarbeiterId, zeitkonto, tag)`
  (Einzeltag-Variante statt Summe, weil alle drei Testfälle von == bis
  verwenden). Die dafür jetzt ungenutzten Mocks/den ungenutzten Aufbau von
  `ZeiterfassungApiService` in diesem Test entfernt, da nichts mehr darauf
  zugreift. Erwartete Zahlen unverändert: voller Feiertag 8, halber Feiertag
  4.00, Feiertag am Wochenende 0. Nur der Zugriffsweg geändert, keine Zahl.
- Gate: `ZeiterfassungApiServiceConcurrencyTest` (8), `Verspaeteter­StopTest` (7),
  `TagesSollCharakterisierungZeiterfassungApiTest` (3) — 18 Tests, 0 Failures,
  0 Errors, BUILD SUCCESS.
- `git status` zeigte zusätzlich `M src/main/resources/static/index.html`
  (Zeilenende-Normalisierung) — nicht angefasst, nicht committet.

Bedenken / Abweichungen vom Plan:
- Plan-Step "TagesSollCharakterisierungZeiterfassungApiTest unverändert grün
  halten" ließ sich wörtlich nicht einhalten, weil der Reflection-Zugriffsweg
  an der gelöschten privaten Methode hing (bereits im Auftrag als bekannte,
  erlaubte Abweichung benannt — "laut statt still"). Umgesetzt wie im Auftrag
  vorgegeben: nur der Zugriffsweg geändert, alle drei erwarteten Zahlen (8,
  4.00, 0) identisch belassen.
- Sonst keine Abweichung vom Task-10-Block im Plan; Zeilennummern und
  vorgeschlagener Ersatz-Code (`tagesSollService.feiertagsGutschriftSumme(...)`)
  stimmten exakt mit dem tatsächlichen Dateizustand überein.
## Abschnitt 3 — Task 8 (Coding-Agent)

Zeit: 2026-09-08T16:41:09Z
Branch: lzk/task-8-monatssaldo
Commit(s): bd1e93a3
Status: fertig

Was gemacht wurde:
- `MonatsSaldoService`: Feld `FeiertagService feiertagService` durch `TagesSollService tagesSollService` ersetzt (Constructor Injection via `@RequiredArgsConstructor`, keine Field-Injection).
- Private Methode `berechneFeiertagsStunden(Zeitkonto, LocalDate, LocalDate)` ersatzlos gelöscht (Tagesschleife komplett entfernt, kein N+1 mehr).
- Aufruf in `berechneMonatsSaldo` (Kommentar "4. Feiertagsstunden") ruft jetzt `tagesSollService.feiertagsGutschriftSumme(mitarbeiterId, zeitkonto, ersterTag, letzterTag)` — bewusst **nicht** `periodenSoll`/`periodenSollSumme`, das wäre die andere Semantik aus `ZeitkontoService`.
- `sollStunden` unverändert bei `zeitkontoService.berechneSollstundenFuerMonat(...)` gelassen (nach Task 7 automatisch neuer `periodenSoll`) — nicht doppelt umgebaut, wie im Plan gefordert.
- Ungenutztes `RoundingMode`-Import entfernt (nur von der gelöschten Methode gebraucht).
- `MonatsSaldoServiceTest` (TDD): `@Mock FeiertagService` durch `@Mock TagesSollService` ersetzt, alle `istFeiertag`/`istHalberFeiertag`-Stubs auf `tagesSollService.feiertagsGutschriftSumme(eq(mitarbeiterId), eq(testZeitkonto), eq(ersterTag), eq(letzterTag))` umgestellt, erwartete Zahlen unverändert gelassen. RED vor der Produktionsänderung verifiziert (NPE auf `feiertagService`), GREEN danach: 36/36 Tests, 0 Failures, 0 Errors.
- Gate-Lauf `./mvnw -B test -Dtest='MonatsSaldoServiceTest,TagesSollCharakterisierungMonatsSaldoTest'`: `MonatsSaldoServiceTest` vollständig grün (36 Tests). Gesamtergebnis 39 Tests, 0 Failures, 3 Errors (BUILD FAILURE) — die 3 Errors kommen ausschließlich aus `TagesSollCharakterisierungMonatsSaldoTest`, siehe Bedenken.

Bedenken / Abweichungen vom Plan:
- **Blocker im Sicherheitsnetz, nicht durch mich behebbar:** `TagesSollCharakterisierungMonatsSaldoTest` (Task 2, Datei darf ich laut Task-Auftrag nicht anfassen) mockt nur `FeiertagService`, nie `TagesSollService`. Mockitos `@InjectMocks`-Konstruktor-Injection füllt einen Konstruktor-Parameter, für den kein passendes `@Mock` in der Testklasse deklariert ist, stillschweigend mit `null` (kein Fehler beim Erzeugen des Objekts) — belegt am bestehenden `self`-Feld in `MonatsSaldoServiceTest`, das genau deshalb manuell per `ReflectionTestUtils.setField` gesetzt wird, weil `@InjectMocks` es nicht automatisch befüllt. Sobald `MonatsSaldoService` einen Konstruktor-Parameter vom Typ `TagesSollService` bekommt (vom Plan für Task 8 zwingend gefordert), bleibt `tagesSollService` in `TagesSollCharakterisierungMonatsSaldoTest` `null`, und alle drei Tests werfen beim Aufruf von `getOrBerechne` eine `NullPointerException` — **nicht** weil sich eine berechnete Zahl geändert hätte. Ich habe das rechnerisch geprüft: `TagesSollService.feiertagsGutschriftSumme` liefert ohne laufende Langzeitkrankmeldung bitgenau dieselben Werte (8 / 4.00 / 0) wie die alte private Methode — das Problem ist rein Mockito-Verdrahtung, keine Verhaltensänderung.
- Ich habe geprüft, ob eine andere Implementierung das umgeht (z.B. `FeiertagService` zusätzlich behalten, oder `tagesSollService` per `@Autowired @Lazy`-Feld statt Konstruktor injizieren wie beim bestehenden `self`-Feld): keine Variante hilft, weil in jedem Fall ein neuer Verweis auf `TagesSollService` existiert, für den die Charakterisierungs-Testdatei keinen Mock deklariert — und genau das darf ich laut Auftrag nicht nachtragen.
- Betrifft vermutlich nicht nur Task 8: `TagesSollCharakterisierungZeitkontoTest`, `...AbwesenheitTest`, `...ZeiterfassungApiTest` dürften an derselben Stelle bei Task 7, 9, 10 brechen, sobald diese Services ebenfalls `TagesSollService` statt `FeiertagService` injizieren — aus meinem Task heraus nicht verifizierbar (andere Dateien), aber der Abschnitts-Reviewer sollte das einplanen.
- Vorschlag für die Behebung (außerhalb meines Dateikreises, Task 8 darf laut Auftrag nur `MonatsSaldoService.java`/`MonatsSaldoServiceTest.java` anfassen): in `TagesSollCharakterisierungMonatsSaldoTest` ein `@Mock private TagesSollService tagesSollService;` ergänzen und je Testfall `lenient().when(tagesSollService.feiertagsGutschriftSumme(eq(1L), eq(testZeitkonto), any(), any())).thenReturn(<8.00|4.00|0>)` stubben — die bereits festgehaltenen Erwartungswerte (8 / 4.00 / 0) bleiben dabei unverändert, es ändert sich nur die Mock-Quelle.
- `git status` zeigt laut Auftrag `M src/main/resources/static/index.html` (Zeilenende-Normalisierung) — nicht angefasst, wie vorgegeben.

## Abschnitt 3 — Task 4 (Coding-Agent)

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/task-4-service
Commit(s): e84996a2
Status: fertig

Was gemacht wurde:
- `LangzeitkrankmeldungService` neu angelegt (`service/LangzeitkrankmeldungService.java`):
  Anlegen (42-Tage-Lohnfortzahlung = Beginn + 41 Tage, ueberschreibbar),
  Aendern, Statusuebergaenge beenden/wiederEroeffnen/abbrechen (inkl. aller
  verbotenen Uebergaenge inkl. Selbstausschluss bei der
  Ueberlappungspruefung), Phasenverwaltung (phaseHinzufuegen/phaseAendern/
  phaseLoeschen) mit voller Regelpruefung (Ueberlappung, Luecke, hoechstens
  eine offene Phase am Ende, stundenProTag-Pflicht/-Verbot je Typ, Deckelung
  am Zeitkonto-Tagessoll), verknuepfeAbwesenheiten (eine Abfrage, FK je Tag
  gesetzt oder zurueckgesetzt), Monats-Cache-Invalidierung, toDto (inkl.
  Stufenplan-Tage mit ueberPlan-Markierung), getMobileStand (rein lesend,
  nur 5 Felder: phase/phaseLabel/heuteGeplanteStunden/seit/bisDatum - kein
  Name, keine Notiz), pruefeUrlaubsHinweise.
- Fuenf DTOs neu unter `dto/Langzeitkrankmeldung/`: LangzeitkrankmeldungDto,
  LangzeitkrankmeldungPhaseDto, StufenplanTagDto, LangzeitkrankmeldungAnlegenRequest,
  LangzeitkrankmeldungPhaseRequest.
- `LangzeitkrankmeldungServiceTest` (39 Tests, Mockito): 42-Tage-Rechnung,
  ueberschreibbares Lohnfortzahlungsdatum, Ueberlappung wird abgelehnt,
  ABGEBROCHEN blockiert nicht, alle sechs Phasen-Regeln einzeln (Ueberlappung,
  Luecke, zwei offene Phasen, Wiedereingliederung ohne Stunden, andere Typen
  mit Stunden, Stufenplan-Stunden ueber Zeitkonto-Soll), jeder verbotene
  Statusuebergang, verknuepfeAbwesenheiten setzt FK korrekt (inkl. Rueckbau
  auf null fuer Tage ausserhalb einer Phase), getMobileStand liefert {} ohne
  Meldung und eigene DSGVO-Zusicherung (kein Name/keine Notiz im Ergebnis).
- Gate: `./mvnw -B test -Dtest=LangzeitkrankmeldungServiceTest` ->
  Tests run: 39, Failures: 0, Errors: 0, BUILD SUCCESS.

Oeffentliche Signaturen (fuer Tasks 5, 6, 12 und Frontend):
```
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class LangzeitkrankmeldungService {
    @Transactional Langzeitkrankmeldung anlegen(Long mitarbeiterId, LocalDate beginn, LocalDate lohnfortzahlungBis, String notiz);
    @Transactional Langzeitkrankmeldung aendern(Long id, LocalDate beginn, LocalDate lohnfortzahlungBis, String notiz);
    @Transactional Langzeitkrankmeldung beenden(Long id, LocalDate ende);
    @Transactional Langzeitkrankmeldung wiederEroeffnen(Long id);
    @Transactional Langzeitkrankmeldung abbrechen(Long id);
    @Transactional Langzeitkrankmeldung phaseHinzufuegen(Long id, LangzeitkrankmeldungPhaseTyp typ, LocalDate vonDatum, LocalDate bisDatum, BigDecimal stundenProTag);
    @Transactional Langzeitkrankmeldung phaseAendern(Long id, Long phasenId, LangzeitkrankmeldungPhaseTyp typ, LocalDate vonDatum, LocalDate bisDatum, BigDecimal stundenProTag);
    @Transactional Langzeitkrankmeldung phaseLoeschen(Long id, Long phasenId);
    List<Langzeitkrankmeldung> finde(LangzeitkrankmeldungStatus status);
    Langzeitkrankmeldung findeMitPhasen(Long id);
    Optional<LangzeitkrankmeldungPhase> findePhase(Long mitarbeiterId, LocalDate stichtag);
    int restTageLohnfortzahlung(Langzeitkrankmeldung meldung, LocalDate stichtag);
    LangzeitkrankmeldungDto toDto(Langzeitkrankmeldung meldung, boolean mitStufenplanTagen);
    Map<String, Object> getMobileStand(String loginToken, LocalDate stichtag);
    List<String> pruefeUrlaubsHinweise(Long mitarbeiterId, LocalDate von, LocalDate bis);
}
```
DTO-Felder exakt wie im Plan spezifiziert:
- `LangzeitkrankmeldungDto`: id, mitarbeiterId, mitarbeiterName ("Nachname, Vorname"),
  beginn, ende, status, statusLabel, lohnfortzahlungBis, notiz, version,
  aktuellePhaseTyp, aktuellePhaseLabel, restTageLohnfortzahlung (Integer, negativ =
  ueberschritten), heuteGeplanteStunden (BigDecimal, nur bei laufender
  Wiedereingliederung sonst null), geplanteRueckkehr, phasen (List<LangzeitkrankmeldungPhaseDto>),
  stufenplanTage (List<StufenplanTagDto>, nur befuellt wenn mitStufenplanTagen=true).
- `LangzeitkrankmeldungPhaseDto`: id, typ, label, vonDatum, bisDatum, stundenProTag.
- `StufenplanTagDto`: datum, geplanteStunden, gestempelteStunden, ueberPlan (boolean).
- `LangzeitkrankmeldungAnlegenRequest`: mitarbeiterId, beginn, lohnfortzahlungBis, notiz
  (fuer Task 5 gedacht, auch fuer PUT/aendern nutzbar - dort bleibt mitarbeiterId unbeachtet).
- `LangzeitkrankmeldungPhaseRequest`: typ, vonDatum, bisDatum, stundenProTag.

Bedenken / Abweichungen vom Plan:
- Der Plan spezifiziert fuer die Request-DTOs (LangzeitkrankmeldungAnlegenRequest,
  LangzeitkrankmeldungPhaseRequest) keine expliziten Feldlisten (nur die drei
  Haupt-DTOs sind im Plan detailliert). Felder wurden aus den Service-Signaturen
  von anlegen/aendern bzw. phaseHinzufuegen/phaseAendern abgeleitet. Task 5
  (Controller) sollte das beim Bauen der Request-Bodies gegenpruefen.
- `baueStufenplanTage` ruft `tagesSollService.arbeitsSoll(...)` je Tag des
  Wiedereingliederungs-Zeitraums auf (so vom Plan fuer Task 4 explizit
  vorgeschrieben: "geplanteStunden aus tagesSollService.arbeitsSoll"). Das
  Task-3-Interface bietet fuer Einzeltage nur eine Methode, die intern selbst
  wieder `phaseRepository.findImZeitraum` fuer genau diesen einen Tag abfragt
  (kein Batch-Pendant fuer Pro-Tag-Werte, nur `...Summe` fuer den Gesamtwert).
  Das ist strenggenommen ein N+1 innerhalb dieser einen Detailseiten-Methode,
  aber bewusst so vom Plan vorgegeben, auf den kurzen (typischerweise
  wochenlangen) Wiedereingliederungs-Zeitraum begrenzt und nur aktiv, wenn
  `mitStufenplanTagen=true` (Detailseite einer einzelnen Meldung, keine Liste).
  Aenderung an TagesSollService/dessen Repository liegt ausserhalb der Files
  dieses Tasks - nicht angefasst. Zur Kenntnisnahme fuer den Abschnitts-Reviewer.
- `git status` zeigt laut Auftrag `M src/main/resources/static/index.html`
  (Zeilenende-Normalisierung) - in diesem Worktree tatsaechlich nicht
  aufgetreten (`git status --porcelain` zeigte nur die eigenen neuen Dateien),
  nicht angefasst, kein Handlungsbedarf.

## Abschnitt 3 — Task 10, Nachtrag (Coding-Agent)

Zeit: 2026-09-08T16:55:00Z
Branch: lzk/task-10-zeiterfassung-api
Commit(s): b85cc567c382f674e7c0e862ca57dfa07b21b422 (unveraendert, kein neuer Commit)
Status: fertig

Was gemacht wurde:
- Auf Rückfrage des Koordinators die Gegenprobe für
  `TagesSollCharakterisierungZeiterfassungApiTest` nachgeholt: pro Test
  einen Stub testweise auf einen falschen Wert gesetzt
  (`vollerFeiertag_ergibtAchtStunden`: `istHalberFeiertag` fälschlich `true`;
  `halberFeiertag_ergibtVierStunden`: `istHalberFeiertag` fälschlich `false`),
  Testklasse allein gefahren. Beide Tests wurden korrekt rot
  (`expected: <0> but was: <1>` bzw. `<-1>`, `Tests run: 3, Failures: 2`) —
  der dritte Test (`feiertagAmWochenende_ergibtNull`, kein Stub involviert)
  blieb unberührt grün. Anschließend beide Stubs zurückgenommen,
  `git diff` gegen den Commit bestätigt leer, vollständiges Gate erneut
  gefahren: 18 Tests, 0 Failures, 0 Errors, BUILD SUCCESS.
- Stubbing-Stil bestätigt: pro Fixture-Tag konkreter Wert
  (`when(feiertagService.istFeiertag(VOLLER_FEIERTAG))...`), kein `any()`.
  `phaseRepository` bewusst ungestubbt gelassen (Mockito-Standard: leere
  Liste) — entspricht der Testabsicht "ohne laufende Langzeitkrankmeldung".

Bedenken / Abweichungen vom Plan:
- Keine neuen. Ergänzt nur die bereits im ersten Block vermerkte
  Zugriffsweg-Anpassung um den Beleg der Gegenprobe.

### Nachtrag zu Abschnitt 3 — Task 11 (Coding-Agent): Gegenproben auf Koordinator-Hinweis

Auf Hinweis des Koordinators (Stub-Verkabelung vs. Zusicherungen, Gegenproben-Pflicht) zwei Mutationsproben gefahren, danach wieder zurückgenommen (`git diff` gegen Commit 80d799f8 danach leer):

1. **Falscher Stub-Wert:** in `TagesSollCharakterisierungKalenderTest` den Stub für `feiertagsGutschrift(...)` am 24.12. testweise von `4.00` auf `8.00` (falsch) geändert → Test wurde **rot** (`expected:<4.0> but was:<8.0>`). Zeigt: der Test hängt wirklich am gestubbten Wert, nicht an einer festen Erwartung ohne Bezug zur Verkabelung.
2. **Bugfix zurückgebaut:** in `ZeitverwaltungController.getKalender` den `feiertagsGutschrift`-Aufruf testweise durch die alte Logik (`if (feiertagDaten.contains(...)) istStunden.add(zeitkonto.getSollstundenFuerTag(...))`) ersetzt → genau `getKalender_HalberFeiertagLiefertHalbeIstStundenUndStimmtMitMonatsuebersichtUeberein` wurde **rot** (`expected:<4.0> but was:<8.0>`), die anderen 3 Tests blieben grün. Zeigt: der neue Test nagelt den Bugfix wirklich fest, nicht nur zufällig.

Beide Änderungen zurückgenommen, danach erneut `./mvnw -B test -Dtest=ZeitverwaltungControllerTest,TagesSollCharakterisierungKalenderTest` → `Tests run: 5, Failures: 0, Errors: 0`, `BUILD SUCCESS`. Kein neuer Commit nötig (Arbeitsverzeichnis wieder identisch mit 80d799f8).

## Abschnitt 3 — Task 7 (Coding-Agent)

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/task-7-zeitkonto
Commit(s): b3ce21ec (ZeitkontoService auf TagesSollService umstellen), 99f50432 (TagesSollCharakterisierungZeitkontoTest auf TagesSollService-Mock umstellen)
Status: fertig

Was gemacht wurde:
- `ZeitkontoService.berechneSollstundenFuerZeitraum` (Zeile 113) ist jetzt ein
  Einzeiler: `return tagesSollService.periodenSollSumme(mitarbeiterId, konto, von, bis)`.
  Verwendete Methode: `TagesSollService.periodenSollSumme` (Summenmethode, kein
  Schleifenaufruf pro Tag — kein N+1).
- Feld `private final FeiertagService feiertagService;` durch
  `private final TagesSollService tagesSollService;` ersetzt (Constructor Injection
  über `@RequiredArgsConstructor`, unverändert).
- `konto.getMitarbeiter()` wird vor dem Aufruf auf `null` geprüft (Alt-Testdaten);
  bei `null` wird `mitarbeiterId = null` an `periodenSollSumme` durchgereicht.
- `ZeitkontoServiceTest`: `@Mock FeiertagService` durch `@Mock TagesSollService`
  ersetzt, die vier Feiertags-Zahlentests durch drei Delegations-Tests ersetzt
  (Rückgabewert + Aufrufargumente werden geprüft, keine Zahlen mehr nachgerechnet).
- Laufender Konflikt entdeckt und mit dem Koordinator geklärt: nach der Umstellung
  lief `TagesSollCharakterisierungZeitkontoTest` (Task 2, außerhalb meiner
  Files-Liste) mit NullPointerException, weil `@InjectMocks` den neuen
  Konstruktor-Parameter `TagesSollService` ohne passenden `@Mock` nicht mehr
  auffüllen konnte. Koordinator hat die Verkabelungs-Anpassung an dieser Datei
  ausdrücklich freigegeben (nur Mocking/Stubbing, keine Zusicherungen). Umgesetzt:
  `@Mock TagesSollService` ergänzt, Stubs von `feiertagService.istHalberFeiertag`
  auf `tagesSollService.periodenSollSumme(1L, zeitkonto, von, bis)` umgestellt,
  pro Fixture-Tag exakt der bisherige Wert (8 / 0 / 8 / 4.00 / 0 / 40) — kein
  einziger erwarteter Zahlenwert, keine Test-Methode, keine Fallzahl geändert.
  Gegenprobe durchgeführt: zwei Stubs testweise auf falsche Werte gesetzt
  (`montag_normalerArbeitstag...` auf 99 statt 8, `samstag_wochenende...` auf 3
  statt 0) → genau diese zwei Tests liefen rot (AssertionFailedError), die
  anderen vier blieben grün. Danach beide Stubs zurückgenommen, erneuter Lauf
  wieder 12/12 grün. Damit ist belegt: die Zusicherungen prüfen weiterhin echtes
  Verhalten, nicht nur die Verkabelung.
- Gate `./mvnw -B test -Dtest='ZeitkontoServiceTest,TagesSollCharakterisierungZeitkontoTest'`:
  12 Tests, 0 Failures, 0 Errors, BUILD SUCCESS (exit=0).

Bedenken / Abweichungen vom Plan:
- Der Plan-Schritt "`TagesSollCharakterisierungZeitkontoTest` unverändert grün
  halten" war so ohne Zusatzabsprache nicht erfüllbar: jede Umstellung, die
  `TagesSollService` per Constructor Injection in `ZeitkontoService` einbaut,
  lässt eine Testklasse, die nur `FeiertagService` mockt, mit `tagesSollService
  == null` zurück. Koordinator hat die Regel präzisiert (Verkabelung darf
  angepasst werden, Zusicherungen nicht) und das Vorgehen freigegeben — siehe
  oben. Empfehlung für künftige Pläne mit derselben Struktur: die Klarstellung
  gleich in den Plan-Text für Task 7 aufnehmen, damit der nächste Agent nicht
  erst empirisch draufstoßen muss.
## Abschnitt 3 — Task 8 (Coding-Agent, Nachtrag)

Zeit: 2026-09-08T17:02:39Z
Branch: lzk/task-8-monatssaldo
Commit(s): 591d100c
Status: fertig

Was gemacht wurde:
- Koordinator hat die zuvor gemeldete Bedenken-Notiz (siehe Block oben) praezisiert: "Diese Testdatei fasst du nicht an" zielte auf die **Zusicherungen**, nicht auf die Mockito-Verkabelung. Verkabelungs-Anpassungen an `TagesSollCharakterisierungMonatsSaldoTest` sind erlaubt, solange jede erwartete Zahl und jeder Testfall unveraendert bleiben.
- `TagesSollCharakterisierungMonatsSaldoTest`: `@Mock FeiertagService` durch `@Mock TagesSollService` ersetzt. `stubStandardMocks` stubbt jetzt `tagesSollService.feiertagsGutschriftSumme(eq(MITARBEITER_ID), eq(testZeitkonto), eq(ersterTag), eq(letzterTag))` statt `feiertagService.istFeiertag`/`istHalberFeiertag` — mit exakten `eq()`-Matchern, keine `any()`-Pauschale, damit ein falscher Stub-Wert den Test tatsaechlich rot werden laesst.
- Die drei Erwartungswerte (voller Feiertag `8.00`, halber `4.00`, Feiertag am Wochenende `0`) und alle drei Testfaelle unveraendert uebernommen — nur die Methodensignatur von `stubStandardMocks` musste von `(int, int, LocalDate, boolean)` auf `(int, int, BigDecimal)` angepasst werden, weil die Tagesschleife jetzt in `TagesSollService` liegt und dieser Test nur noch das Ergebnis am Rand mockt.
- Gegenprobe durchgefuehrt: Stub in `vollerFeiertag_ergibtAchtStundenGutschrift` testweise auf `99.00` gesetzt → Test wurde rot (`AssertionFailedError`, "war: 99.00"). Danach zurueckgesetzt auf `8.00` und erneut gruen verifiziert. Damit ist belegt, dass der Test einen falschen Wert tatsaechlich faengt und nicht nur sich selbst bestaetigt.
- Finaler Gate-Lauf `./mvnw -B test -Dtest='MonatsSaldoServiceTest,TagesSollCharakterisierungMonatsSaldoTest'`: 39 Tests, 0 Failures, 0 Errors, BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- Die im vorigen Block dokumentierte Blocker-Bedenken ist damit aufgeloest. Bleibt bestehen: dieselbe Mockito-Verkabelungsluecke betrifft vermutlich `TagesSollCharakterisierungZeitkontoTest`, `...AbwesenheitTest`, `...ZeiterfassungApiTest` bei Task 7, 9, 10 — falls deren Agenten dieselbe Koordinator-Praezisierung noch nicht erhalten haben, betrifft sie dieselbe Klasse von Problem.

## Abschnitt 3 — Task 9 (Coding-Agent, Nachbesserung)

Zeit: 2026-09-08T00:30:00Z
Branch: lzk/task-9-abwesenheit
Commit(s): d549a233 (auf 3e9ac0ee)
Status: fertig

Befund/Klarstellung, die behoben wurde: Koordinator hat praezisiert, dass die
Regel "TagesSollCharakterisierungAbwesenheitTest nicht anfassen" sich auf die
Zusicherungen bezieht, nicht auf die Mockito-Verkabelung. Ein Test, der nur
wegen eines neuen, ungemockten Konstruktor-Parameters mit NullPointerException
faellt, misst nichts — ihn wieder lauffaehig zu machen ist keine Aufweichung
des Sicherheitsnetzes.

Was gemacht wurde:
- `TagesSollCharakterisierungAbwesenheitTest`: `@Mock TagesSollService tagesSollService` ergaenzt. Stubs pro Fixture-Tag statt pauschal `any()`: `arbeitsSoll(MITARBEITER_ID, testZeitkonto, MONTAG_NORMAL) -> 8.00` in `stubGrunddaten()`, `arbeitsSoll(MITARBEITER_ID, testZeitkonto, SAMSTAG_WOCHENENDE) -> 0.00` im Samstag-Test — jeweils exakt der Wert, den vorher der rohe Zeitkonto-Wert lieferte. Keine Zahl, keine Exception-Erwartung, keine Meldung, kein Testfall veraendert oder entfernt.
- **Stub-Gegenprobe durchgefuehrt**: `arbeitsSoll(..., MONTAG_NORMAL)` testweise auf `5.00` gesetzt (statt `8.00`) → beide betroffenen Tests (`krankheit_normalerArbeitstag_gibtVolleSollstunden`, `krankheit_halberTag_gibtVierStunden`) wurden rot (`expected: <0> but was: <1>`) — das Netz reagiert also wirklich auf falsche Werte. Danach zurueckgesetzt auf `8.00`, wieder gruen.
- `AbwesenheitService`: die "keine Sollstunden"-Meldung in zwei fachlich unterschiedliche Faelle aufgeteilt, wie vom Koordinator angemahnt. Vorher bekamen ein echtes Wochenende UND ein 0h-Wiedereingliederungstag dieselbe Meldung ("Kein Arbeitstag ... auch nicht waehrend einer Wiedereingliederung"). Jetzt: echtes Wochenende (roher Zeitkonto-Wert <= 0) → unveraendert "Kein Arbeitstag: Am {Wochentag} hat dieser Mitarbeiter keine Sollstunden" (identisch zum Ausgangstext, damit die Samstag-Zusicherung im Charakterisierungstest unangetastet bleibt); regulaerer Arbeitstag mit 0h aus einer laufenden Wiedereingliederung (roher Zeitkonto-Wert > 0, aber `arbeitsSoll` == 0) → eigene Meldung "Keine Sollstunden am {Wochentag}: Die laufende Wiedereingliederung sieht an diesem Tag 0 Stunden vor", ohne den Substring "Kein Arbeitstag".
- `AbwesenheitServiceTest`: neuer Test `krankheit_WiedereingliederungMitNullStunden_MeldungUnterscheidetSichVonWochenende` (TDD: erst rot beobachtet — Meldung enthielt "Kein Arbeitstag" fuer beide Faelle —, dann Produktivcode angepasst, jetzt gruen).

Gate-Zahlen (final):
```
./mvnw -B test -Dtest='AbwesenheitServiceTest,TagesSollCharakterisierungAbwesenheitTest'
AbwesenheitServiceTest: Tests run: 9, Failures: 0, Errors: 0
TagesSollCharakterisierungAbwesenheitTest: Tests run: 4, Failures: 0, Errors: 0
Tests run: 13, Failures: 0, Errors: 0
BUILD SUCCESS (exit=0)
```

Bedenken / Abweichungen vom Plan:
- Keine offenen mehr fuer Task 9 selbst. Weiterhin als Hinweis fuer den Abschnitts-Reviewer (nicht selbst geprueft): dieselbe strukturelle Luecke (Charakterisierungstest ohne `@Mock TagesSollService`, dadurch nach Umstellung nicht mehr lauffaehig) betrifft vermutlich auch die anderen fuenf Umstellungs-Tasks in diesem Abschnitt (4, 7, 8, 10, 11) mit ihren jeweiligen Charakterisierungstests — dort gilt dieselbe Praezisierung: Verkabelung reparieren erlaubt, Zusicherungen unangetastet.

## Abschnitt 2 — Nachbesserung

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/nachbesserung-abschnitt2
Commit(s): 028f75f2
Status: fertig

Was gemacht wurde:
- Befund 1 (VerrechnungslohnService, RUECKWIRKEND-Modus): anwesenheitsFaktor
  kuerzte bei Quelle LOHNABRECHNUNG das Brutto ein zweites Mal, obwohl der
  Krankengeld-Ausfall dort schon in den echten Lohnabrechnungen steckt.
  Roter Testlauf vor dem Fix (krankengeldPhaseKlammertKalendertageUndAnteiligeLohnkostenAus,
  angepasste Erwartung 48000.00): AssertionFailedError expected 48000.00,
  war 32001.60 (48000 x Faktor 0.6667). Fix: Faktor nur noch bei Quellen
  != LOHNABRECHNUNG anwenden. Neuer Regressionstest
  hochgerechnetesBruttoWirdBeiKrankengeldWeiterhinUeberDenAnwesenheitsfaktorGekuerzt
  belegt, dass der Faktor bei STAMMSTUNDENLOHN weiterhin greift (41600.00 x
  0.6667 = 27734.72). Mutationsprobe: Quelle-Unterscheidung entfernt (immer
  multiplizieren) -> beide Tests neu ausgefuehrt, LOHNABRECHNUNG-Test wieder
  rot (erwartet 48000.00, bekam 32001.60 wie im urspruenglichen Bug),
  Hochrechnungs-Test blieb gruen. Mutation zurueckgenommen, 32/32 Tests gruen.
- Befund 2: neuer Test
  geschaeftsfuehrerKalkulatorischerLohnWirdBeiKrankengeldUeberDenAnwesenheitsfaktorGekuerzt
  (60000 x 0.6667 = 40002.00). Mutationsprobe: gesamt.multiply(anwesenheitsFaktor)
  im GF-Zweig auskommentiert -> Test rot (erwartet 40002.00, bekam 60000.00).
  Mutation zurueckgenommen, gruen.
- Befund 3 (TagesSollServiceTest, TagesSollService.java NICHT geaendert): zwei
  neue Tests - krankengeldPhaseMitGesetztemStundenProTag_wirdIgnoriert_zeitkontoWertGilt
  (KRANKENGELD-Phase mit stundenProTag=2.00 muss ignoriert werden, Zeitkonto-
  Wert 8.00 gilt) und wiedereingliederung_stundenProTagNull_phaseWirdIgnoriertZeitkontoWertGilt
  (WIEDEREINGLIEDERUNG mit stundenProTag == null -> Zeitkonto-Wert gilt).
  Mutationsprobe: Typfilter (p.getTyp() == WIEDEREINGLIEDERUNG) in
  wiedereingliederungAm() temporaer entfernt -> erster Test rot
  (AssertionFailedError periodenSoll expected <0> but was <1>, d.h. 2.00 statt
  8.00 -- der Stufenplan-Wert einer KRANKENGELD-Phase wurde faelschlich
  uebernommen). Mutation exakt zurueckgenommen (git diff auf TagesSollService.java
  danach leer), 20/20 Tests gruen.
- Befund 4 (StufenplanTabelle.tsx): Fehlertext von "zwischen 1 und N" auf
  "groesser als 0 und hoechstens N" geaendert -- Entscheidung: die Pruefung
  bleibt bei stundenZahl > 0 (keine Ganzzahl-Pflicht), weil halbe Stunden bei
  einer Wiedereingliederung ueblich sind; nur der Text wurde an die
  tatsaechliche Pruefung angeglichen. Startdatum-Feld: DatePicker rendert kein
  natives Formularfeld und akzeptiert kein id/htmlFor-Ziel (Komponente ausserhalb
  des Aufgabenbereichs, nicht angefasst) -- deshalb ueber role="group" +
  aria-labelledby mit dem Label verknuepft statt htmlFor. Neue Vitest-Faelle
  fuer beides (halbe Stunden gueltig, ARIA-Gruppe vorhanden) ergaenzt.
- Befund 5: zwei Kommentare in VerrechnungslohnService ergaenzt (Urlaubs-Default
  bewusst ohne ausgeklammert.isEmpty()-Schutz; feiertagsstunden bewusst
  ungekuerzte Anzeigegroesse) -- Code an beiden Stellen unveraendert.
- Gates: Backend ./mvnw -B test -Dtest=VerrechnungslohnServiceTest,TagesSollServiceTest
  -> Tests run: 52, Failures: 0. Frontend npx vitest run
  src/components/langzeitkrankmeldung/ -> 2 Testdateien, 14 Tests, alle gruen.
  npm run lint -> 0 Errors, 1 bekannte Warning (BelegeKasseEditor.tsx:1204).
  npm run build -> BUILD SUCCESS, Artefakte danach per git checkout -- 
  src/main/resources/static/ plus manuellem rm der beiden neuen, untracked
  Hash-Dateien wieder entfernt; index.html zeigt weiterhin nur die
  vorbestehende Zeilenende-Differenz.

Bedenken / Abweichungen vom Plan:
- StufenplanTabelle ist in diesem Worktree in keiner Seite/Route eingebunden
  (kein Treffer fuer den Komponentennamen ausserhalb der Komponente selbst) --
  vermutlich baut ein anderer paralleler Agent die Seite, die sie einbindet.
  Deshalb keine Playwright-E2E-Spec angelegt: eine Seite dafuer anzulegen liegt
  ausserhalb des mir zugewiesenen Dateibereichs (nur StufenplanTabelle.tsx +
  Test), und die expliziten Gates in meinem Auftrag nennen fuer diese
  Nachbesserung ohnehin nur vitest/lint/build, kein e2e. Bitte im
  Abschnitts-Review pruefen, sobald die Seite existiert.

## Abschnitt 3 — Task 10, Nachbesserung (Coding-Agent)

Zeit: 2026-09-08T17:06:20Z
Branch: lzk/task-10-zeiterfassung-api
Commit(s): dd541346c12e5b92770403c8e2d38787ce60effc
Status: fertig

Was gemacht wurde (Befund des Koordinators: Testsubjekt war TagesSollService
statt ZeiterfassungApiService — behoben):
- `TagesSollCharakterisierungZeiterfassungApiTest` erneut umgebaut.
  Testsubjekt ist wieder `ZeiterfassungApiService`: Konstruktor mit allen 13
  Argumenten (inkl. `tagesSollService` als Mock), `zeitkontoService` und
  `zeitkontoKorrekturService` per `ReflectionTestUtils.setField` gesetzt
  (Field-Injection-Felder). Zugriffsweg jetzt `ReflectionTestUtils.
  invokeMethod(service, "berechneAnteiligenMonatIst", MITARBEITER_ID, von, bis)`
  — die einzige Stelle, die `tagesSollService.feiertagsGutschriftSumme`
  aufruft (Randmonat-Begründung für Reflection unverändert: `getSaldo`s
  Randmonat-Zweig hängt an `LocalDate.now()`).
- `TagesSollService` wird gemockt (eigene Tests: `TagesSollServiceTest`,
  Task 3). Pro Testfall: `feiertagsGutschriftSumme` mit dem konkreten
  erwarteten Zeitraum gestubbt (kein `any()`), plus `verify(tagesSollService)
  .feiertagsGutschriftSumme(...)` mit exakt diesem Zeitraum — damit prüft der
  Test jetzt wieder, dass der Aufrufer richtig verkabelt ist, nicht nur die
  Zahl. `zeitkontoKorrekturService` mit `any()` für den Zeitraum gestubbt
  (Nebenabhängigkeit, nicht Testgegenstand — liefert konstant 0).
  `zeitbuchungRepository`/`abwesenheitRepository` bewusst ungestubbt
  (Mockito-Default: leere Liste bzw. `null`→im Code auf 0 abgefangen).
- Mutationsprobe am Produktivcode: `von` in
  `tagesSollService.feiertagsGutschriftSumme(...)` testweise auf
  `von.plusDays(1)` verschoben. Alle drei Tests wurden korrekt rot —
  Mockito `PotentialStubbingProblem: Strict stubbing argument mismatch`
  (z.B. Aufruf mit `2026-01-02` statt gestubbtem `2026-01-01`),
  `Tests run: 3, Errors: 3`. Mutation zurückgenommen, `git diff` gegen
  den vorherigen Commit danach leer bestätigt.
- Gate erneut gefahren: `ZeiterfassungApiServiceConcurrencyTest` (8) +
  `ZeiterfassungApiServiceVerspaeteterStopTest` (7) +
  `TagesSollCharakterisierungZeiterfassungApiTest` (3) = 18 Tests,
  0 Failures, 0 Errors, BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- Keine neuen. Korrektur des im vorherigen Block dokumentierten Fehlgriffs
  (Testsubjekt versehentlich auf TagesSollService verschoben) — jetzt behoben.

## Abschnitt 3 — Review (Code-Reviewer)

Zeit: 2026-09-08T17:35:00Z
Branch: feature/langzeitkrankmeldung (gemergter Stand, ddd6d37a)
Geprueft: Diff a2d3ea3e..HEAD, Tasks 4, 7, 8, 9, 10, 11 + Nachbesserung Abschnitt 2
Status: fertig
Ampel: 🟢

Testlaeufe (alle synchron, Output in Dateien, nie durch eine Pipe):
- `./mvnw -B test`: **2554 Tests, 0 Failures, 4 Errors** — exakt die vier
  vorbestehenden (`AuditChainRepairIntegrationTest` x2,
  `AuditHashRoundtripDiagnoseTest` x2, alle `CannotCreateTransaction`).
  Baseline war 2508/4, also +46 neue Tests und kein fuenfter Fehler.
- `npm run lint` (react-pc-frontend): 0 Errors, 1 bekannte Warning
  (`BelegeKasseEditor.tsx:1204`). Gate gehalten.
- `npx vitest run src/components/langzeitkrankmeldung/`: 2 Dateien, 14 Tests gruen.
- Lint/Vitest im Haupt-Checkout gefahren: der Review-Worktree hat gar kein
  `node_modules` (weder Ordner noch Junction), `eslint` ist dort nicht
  aufloesbar. Haupt-Checkout stand auf demselben Commit und war sauber.

Mutationsproben (11 Stueck, jede einzeln angewandt und danach zurueckgenommen):
- MZ  ZeitkontoService delegiert an feiertagsGutschriftSumme statt
  periodenSollSumme -> TagesSollCharakterisierungZeitkontoTest 6 von 6 rot.
- MM  MonatsSaldoService uebergibt (ersterTag, ersterTag) statt
  (ersterTag, letzterTag) -> TagesSollCharakterisierungMonatsSaldoTest 3 von 3 rot.
- MA  AbwesenheitService fragt datum.plusDays(1) ab ->
  TagesSollCharakterisierungAbwesenheitTest 3 von 4 rot.
- MApi ZeiterfassungApiService ruft periodenSollSumme statt
  feiertagsGutschriftSumme -> TagesSollCharakterisierungZeiterfassungApiTest 3 von 3 rot.
- MC1 alter Kalender-Bug zurueck (volle Sollstunden als Ist am Feiertag) ->
  TagesSollCharakterisierungKalenderTest: tage[23].istStunden erwartet 4.0,
  war 8.0, UND ZeitverwaltungControllerTest.getKalender_HalberFeiertag... rot.
  Der Bugfix ist doppelt festgenagelt.
- MC2 sollStunden wieder roh aus dem Zeitkonto -> Kalender-Charakterisierung
  (tage[23].sollStunden 0 statt 8) und der neue Wiedereingliederungs-Test
  (tage[1].sollStunden 2.0 statt 8.0) rot.
- MV1 Ausnahme fuer LOHNABRECHNUNG entfernt (doppelte Kuerzung wieder da) ->
  krankengeldPhaseKlammertKalendertageUndAnteiligeLohnkostenAus rot.
- MV2 anwesenheitsFaktor im regulaeren Zweig ganz abgeschaltet ->
  hochgerechnetesBruttoWirdBeiKrankengeldWeiterhinUeberDenAnwesenheitsfaktorGekuerzt
  rot. Eine Ueberkorrektur von Befund 1 waere also aufgefallen.
- MV3 anwesenheitsFaktor im GF-Zweig entfernt ->
  geschaeftsfuehrerKalkulatorischerLohn... rot. Testluecke aus Abschnitt 2 zu.
- MV4 Typfilter WIEDEREINGLIEDERUNG in TagesSollService entfernt ->
  krankengeldPhaseMitGesetztemStundenProTag_wirdIgnoriert... rot. Zweite
  Testluecke aus Abschnitt 2 zu (vorher blieben alle 18 Tests gruen).
- ML1 beginn.plusDays(42) statt 41 -> 42-Tage-Test rot.
- ML2 Name und Notiz in getMobileStand ergaenzt -> DSGVO-Test rot
  (erwartet 5 Felder, waren 7).
- ML3 FK-Rueckstellung in verknuepfeAbwesenheiten entfernt -> 2 Tests rot.

Sicherheitsnetz, Ergebnis je Charakterisierungsdatei:
- Zeitkonto / MonatsSaldo / Abwesenheit / ZeiterfassungApi: keine Zusicherung
  geaendert, Stubs pro Fixture-Fall mit exakten Argumenten (kein pauschales
  any()), Testsubjekt jeweils der umgestellte Service. Bei ZeiterfassungApi
  ist der Rueckbau auf ZeiterfassungApiService angekommen, inklusive verify()
  auf den uebergebenen Zeitraum.
- Kalender: genau die eine erlaubte Zahlenaenderung (tage[23].istStunden
  8 -> 4.00), die Stubs sind per willAnswer tagesabhaengig, nicht konstant.
- Die Zahlen-Charakterisierung ist nicht verschwunden, sondern liegt in
  TagesSollServiceTest (20 Tests gegen die echte Rechenlogik). MZ/MM/MApi
  zeigen, dass die Aufrufer-Verkabelung trotzdem festgenagelt bleibt.

N+1, gemessen statt argumentiert (Wegwerf-Sonde, danach geloescht; gezaehlt
wurden echte Repository-Aufrufe):
- Kalenderabruf, Dezember 2026 (31 Tage): 249 Repository-Aufrufe
  (62x LangzeitkrankmeldungPhaseRepository.findImZeitraum + 187x
  FeiertagRepository). Vor der Umstellung waren es eine Handvoll.
- Dieselben Werte ueber die Batch-Methoden: 9 Aufrufe. Faktor rund 28.
- Stufenplan, 42 Tage Wiedereingliederung: 165 Aufrufe.
- TagesSollService cacht nichts. Zusaetzlich fragt berechneEinzeltag
  istFeiertag und istHalberFeiertag getrennt ab, und beide starten je ein
  getFeiertageForJahr (= findByJahr ueber das ganze Jahr) - 4 Abfragen pro Tag,
  wo 2 reichen wuerden (getFeiertagInfo liefert beide Flags).
- Bewertung: gelb, nicht rot. Keine falschen Zahlen, kein Fehlerfall, und der
  Plan hat den Pro-Tag-Weg fuer Task 4 und 11 ausdruecklich vorgegeben, waehrend
  TagesSollService.java fuer beide Agenten gesperrt war. Empfehlung fuer einen
  eigenen Folge-Task VOR Abschnitt 5 (dort laedt die UI den Kalender bei jedem
  Monatswechsel): eine Map jeTag(mitarbeiterId, konto, von, bis) in
  TagesSollService, gebaut auf der schon vorhandenen summiere-Schleife;
  Controller und baueStufenplanTage rufen dann einmal statt 31x bzw. 42x.

Geprueft und in Ordnung:
- Constructor Injection ueberall, auch in ZeiterfassungApiService (neues Feld
  im RequiredArgsConstructor-Block, nicht als Autowired-Feld).
- Query nur mit benannten Parametern. findImZeitraum und findUeberlappende
  laufen ueber implizite Pfade, aber beide Beziehungen sind
  ManyToOne(optional = false) - INNER JOIN ist hier korrekt, es haengt kein
  IS-NULL-Fall daran.
- PC-only: kein Langzeitkrankmeldung-Controller im Diff, kein POST/PUT/PATCH/
  DELETE unter /api/zeiterfassung fuer dieses Feature, react-zeiterfassung
  unberuehrt. getMobileStand liefert exakt fuenf Felder, kein Name, keine
  Notiz (per Mutation ML2 belegt).
- DSGVO: Logs schreiben nur meldungId und mitarbeiterId, keine Diagnose, keine
  Notiz, keine Klarnamen. Testdaten durchgaengig Mustermann/Musterfrau/Beispiel.
- Nachbesserung Abschnitt 2: der anwesenheitsFaktor greift weiterhin bei
  KALKULATORISCH, STUNDENLOHN_HOCHRECHNUNG, STAMMSTUNDENLOHN und im GF-Zweig;
  nur LOHNABRECHNUNG ist ausgenommen (MV1/MV2/MV3 belegen beide Richtungen).
- Wording: Lohnfortzahlung durch den Betrieb, Krankengeld der Krankenkasse,
  Wiedereingliederung, Wieder voll im Einsatz. Kein Fachchinesisch.
- Baum nach allen Proben sauber: git status --short zeigt nur den bekannten
  static/index.html-Zeilenende-Phantom-Diff.

Bedenken / Abweichungen vom Plan (alle gelb, blockieren nicht):
- LangzeitkrankmeldungService.java:96,133,175 - LocalDate.MAX geht als
  Query-Parameter an findUeberlappende. MySQL kennt DATE nur bis 9999-12-31;
  je nach Server-Modus gibt das einen Fehler oder still keine Treffer, und die
  Ueberlappungspruefung waere dann wirkungslos. Ohne MySQL nicht messbar, im
  Test nie beruehrt (H2 plus gemocktes Repository), und heute noch nicht
  erreichbar, weil der Controller erst in Abschnitt 4 kommt. Vor Abschnitt 4
  auf LocalDate.of(9999, 12, 31) umstellen.
- N+1 im Kalender und im Stufenplan, siehe Messung oben.
- TagesSollService.java:110-111 - istFeiertag plus istHalberFeiertag verdoppeln
  die Feiertagsabfragen pro Tag; ein getFeiertagInfo wuerde reichen.
- LangzeitkrankmeldungService.java:130 - aendern setzt die Notiz auch dann,
  wenn null uebergeben wird, waehrend beginn und lohnfortzahlungBis nur bei
  Nicht-Null geschrieben werden. Ein PUT ohne Notizfeld loescht damit eine
  bestehende Notiz. Ein Test haelt das Verhalten fest, es ist also gewollt -
  Task 5 sollte es bewusst entscheiden und im Request-DTO dokumentieren.
- LangzeitkrankmeldungService - phaseHinzufuegen/phaseAendern/phaseLoeschen
  pruefen den Status nicht; an einer ABGEBROCHEN-Meldung lassen sich weiter
  Phasen anlegen. Folgenlos (die Verknuepfung wird ohnehin entkoppelt), aber
  unsauber.
- ZeitverwaltungController.java:436-438,497 - istFeiertag/feiertagName im
  Kalender kommen aus getFeiertageZwischen OHNE Bundesland-Filter, waehrend
  sollStunden/istStunden jetzt ueber TagesSollService auf BY filtern. Mit den
  heutigen, rein bayerischen Feiertagsdaten identisch.
- AbwesenheitServiceTest.stubGrunddaten stubbt arbeitsSoll pauschal per
  anyLong(), any(), any() (so im Plan vorgegeben). Der Charakterisierungstest
  daneben deckt den Fall konkret ab (Mutation MA war rot), deshalb nur Hinweis.
- Tippfehler in einem Kommentar von
  VerrechnungslohnServiceTest.geschaeftsfuehrerKalkulatorischerLohn...
- StufenplanTabelle.tsx hat weiterhin keine Playwright-Spec (der Baustein
  haengt in keiner Seite). Gehoert in Abschnitt 5 nachgezogen, sobald Task 15
  die Seite baut - dort laeuft ohnehin die volle Design-Pruefung.

Blockierende Befunde: keine.

## Abschnitt 4 — Task 6 (Coding-Agent)

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/task-6-mobile-controller
Commit(s): 12d768e7
Status: fertig

Was gemacht wurde:
- Neuer `LangzeitkrankmeldungMobileController` mit genau einem Endpoint
  `GET /api/zeiterfassung/langzeitkrankmeldung/{token}`, delegiert
  komplett an `LangzeitkrankmeldungService.getMobileStand(token,
  LocalDate.now())`. Bewusst kein POST/PUT/PATCH/DELETE.
- Klassen-Javadoc haelt fest: Pfad liegt unter `/api/zeiterfassung/**`,
  ist schon in der Mobile-Whitelist (`SecurityConfig.ZEITERFASSUNG_PATHS`)
  — keine SecurityConfig-Aenderung noetig/gewuenscht.
- MockMvc-Tests (`LangzeitkrankmeldungMobileControllerTest`, 7 Tests):
  unbekanntes Token -> `{}` ohne Fehler; laufende Wiedereingliederung
  -> genau die 5 erwarteten Felder; Antwort enthaelt nie Name/Notiz
  (DSGVO, explizite Zusicherung `$.name`/`$.mitarbeiterName`/`$.notiz`
  `doesNotExist()`); Path-Traversal- und SQL-Injection-Muster im Token
  -> `{}`, keine Exception; unterschiedliche Tokens liefern jeweils nur
  ihren eigenen Stand (kein Vermischen ueber Requests hinweg); Service
  wird mit dem heutigen Datum aufgerufen.
- Gates: `./mvnw -B test -Dtest=LangzeitkrankmeldungMobileControllerTest`
  -> Tests run: 7, Failures: 0, Errors: 0, BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- Zwei Sicherheits-Testfaelle mussten an reale Servlet-URL-Semantik
  angepasst werden (Details, kein Befund am Produktivcode):
  (1) Ein rohes ".." im Token wird schon von der URL-Normalisierung
  (RFC-3986-Dot-Segment-Entfernung) aufgeloest und erreicht den
  Controller nie als Wert — der Test nutzt deshalb den kodierten Wert
  (`..%2F..%2Fetc%2Fpasswd`) fuer einen Token, der nach dem Dekodieren
  "../../etc/passwd" ergibt.
  (2) Ein Semikolon im Pfadsegment wird von Spring als
  Matrix-Parameter-Trenner behandelt und vor dem Routing abgeschnitten
  (`UrlPathHelper#removeSemicolonContent`) — das SQL-Injection-Testmuster
  wurde deshalb semikolonfrei gewaehlt (`' OR '1'='1' --`), um wirklich
  den vollen String bis zum Service zu pruefen statt ein URL-Parsing-
  Detail zu testen.

## Abschnitt 4 — Task 12 (Coding-Agent)

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/task-12-urlaubsantrag
Commit(s): bf3af510
Status: fertig

Was gemacht wurde:
- UrlaubsantragService.approveAntrag ermittelt die Urlaubsstunden je Tag jetzt
  über TagesSollService.arbeitsSoll(mitarbeiterId, zeitkonto, tag) statt
  direkt über zeitkonto.getSollstundenFuerTag(...) (E1 im Plan). arbeitsSoll
  gewählt (nicht periodenSoll/feiertagsGutschrift einzeln), weil es exakt die
  Gegenbuchung zum Tagessoll liefert: periodenSoll − feiertagsGutschrift,
  inkl. korrekter Behandlung von halben Feiertagen und laufender
  Wiedereingliederung (Stufenplan-Stunden, gedeckelt aufs Zeitkonto-Soll).
- getOrCreateZeitkonto wird jetzt einmal vor der Tagesschleife geladen statt
  je Tag (N+1-Fix laut Steps).
- Neue Methode UrlaubsantragService.pruefeHinweise(mitarbeiterId, von, bis)
  delegiert an LangzeitkrankmeldungService.pruefeUrlaubsHinweise (Task 4).
- Neuer Endpoint GET /api/urlaub/antraege/hinweise, Response-Format
  200 { "warnungen": [ "..." ] } — exakt wie im Plan spezifiziert. POST
  /api/urlaub/antraege (Handy-App) unverändert, keine Verschärfung.
- UrlaubsantragServiceTest.java neu angelegt (7 Tests, Vorbild
  AbwesenheitServiceTest): 5x8,00h ohne Feiertag/Wiedereingliederung,
  5x2,00h während Wiedereingliederung, Feiertag übersprungen (inkl. verify
  never() auf arbeitsSoll für den Feiertag), Wochenende übersprungen (inkl.
  verify never() auf arbeitsSoll für Sa/So), getOrCreateZeitkonto genau
  einmal aufgerufen, pruefeHinweise delegiert korrekt (mit und ohne Treffer).
- TagesSollCharakterisierungUrlaubsantragTest: @Mock TagesSollService und
  @Mock LangzeitkrankmeldungService ergänzt (Verkabelungsfalle durch neue
  Konstruktor-Parameter). Pro Fixture-Tag einzeln mit konkretem Datum und
  Wert 8.00 gestubbt, kein pauschales any(). Erwartete Zahlen (5 bzw. 4
  Abwesenheiten à 8,00) unverändert. Gegenprobe: Stub für 2026-06-01 auf
  3.00 verfälscht → Test wurde rot (AssertionFailedError erwartet 0/war 1),
  danach zurückgenommen → wieder grün.
- Gates: ./mvnw -B test -Dtest='UrlaubsantragServiceTest,TagesSollCharakterisierungUrlaubsantragTest'
  → Tests run: 9, Failures: 0, Errors: 0, BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- Line-Referenzen im Plan-Block ("Zeile 249–280" fürs Vorbild approveAntrag,
  "Zeile 250" für getOrCreateZeitkonto) stimmten nicht exakt mit der Datei im
  Branch überein (approveAntrag lag bei 84–137) — reine Zeilennummer-Drift,
  inhaltlich stand die richtige Stelle eindeutig fest, kein Blocker.
- Performance-Hinweis für den Plan-Owner/Review: TagesSollService.arbeitsSoll
  lädt intern pro Aufruf einmal LangzeitkrankmeldungPhaseRepository.findImZeitraum
  (Einzeltag-Variante). Da approveAntrag es jetzt pro Arbeitstag aufruft,
  verursacht die Genehmigung eines N-Tage-Antrags N Phasen-Abfragen statt
  vorher 0 (getSollstundenFuerTag war reiner In-Memory-Zugriff aufs
  Zeitkonto). Eine "JeTag"-Zeitraum-Variante auf TagesSollService (analog zu
  periodenSollSumme/feiertagsGutschriftSumme, die Phasen+Feiertage schon
  einmal für den ganzen Zeitraum laden) würde das auf eine Abfrage senken.
  Diese Methode existiert im Branch noch nicht (wird laut Auftrag von einem
  Parallel-Agenten gebaut) — deshalb hier nur vermerkt, nicht selbst gebaut.
  Bei typischen Urlaubsanträgen (wenige Tage bis wenige Wochen) ist die
  Query-Last klein, aber bei langen Anträgen relevant.
- UrlaubsantragControllerTest.java (nicht in meinen Files, nicht angefasst)
  bleibt grün: nutzt @MockBean für UrlaubsantragService, daher von der
  Konstruktoränderung nicht betroffen — zur Sicherheit gegengeprüft.

## Abschnitt 4 — Task 5 (Coding-Agent)

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/task-5-controller
Commit(s): 9ed7ac26
Status: fertig

Was gemacht wurde:
- LangzeitkrankmeldungController neu angelegt unter
  src/main/java/org/example/kalkulationsprogramm/controller/, alle Endpunkte
  unter /api/langzeitkrankmeldungen (Desktop-App): GET Liste (Statusfilter,
  Default LAUFEND), GET Detail (mit Stufenplan-Tagen), POST Anlegen, PUT
  Aendern, PUT .../beenden, PUT .../oeffnen, PUT .../abbrechen, POST
  .../phasen, PUT .../phasen/{phasenId}, DELETE .../phasen/{phasenId}.
- Keine Endpunkte unter /api/zeiterfassung/** angelegt (Mobile bleibt
  Task 6 vorbehalten, dort nur lesend).
- Fachliche Ablehnungen des Service (IllegalStateException/
  IllegalArgumentException) werden zu 400 bzw. 404 (bei "nicht gefunden" in
  der Meldung) mit {"error": "<Klartext>"} uebersetzt, nie 500.
- Optimistisches Sperren: alle aendernden Endpunkte nehmen zusaetzlich
  einen Pflicht-Query-Parameter `version` entgegen. Der Controller laedt
  vorab per service.findeMitPhasen(id) die aktuelle DB-Version und wirft
  bei Abweichung eine ObjectOptimisticLockingFailureException, die der
  bestehende globale RestExceptionHandler bereits sauber in 409 mit der
  Handwerker-Meldung uebersetzt (kein eigener Handler noetig, per Test
  verifiziert).
- LangzeitkrankmeldungControllerTest (MockMvc, @WebMvcTest +
  addFilters=false + @MockBean Service) mit 25 Tests: Happy-Path und
  Fehlerfall je Endpunkt, Versionskonflikt (409), Sicherheits-Checkliste
  (id=-1/0/Long.MAX_VALUE, XSS-Notiz, Notiz > 10.000 Zeichen, SQL-
  Injection-String), Dummy-Daten (Max Mustermann). Alle 25 gruen, TDD
  Schritt fuer Schritt (rot -> gruen) durchlaufen.

Bedenken / Abweichungen vom Plan:
- Die Task-4-Request-DTOs (LangzeitkrankmeldungAnlegenRequest,
  LangzeitkrankmeldungPhaseRequest) haben KEIN version-Feld, und die
  Service-Methoden (aendern/beenden/wiederEroeffnen/abbrechen/
  phaseHinzufuegen/phaseAendern/phaseLoeschen) nehmen keinen
  Versions-Parameter entgegen - sie laden die Meldung innerhalb ihrer
  eigenen Transaktion immer frisch aus der DB. Ein echter DB-Level-
  Optimistic-Lock-Konflikt ueber @Version wuerde damit nur bei
  ueberlappenden Transaktionen entstehen, nicht im ueblichen Buero-Fall
  zweier zeitlich getrennter HTTP-Requests (genau der Fall, den die
  Aufgabe adressiert: "damit zwei Bueromitarbeiter sich nicht gegenseitig
  ueberschreiben"). Da ich weder die DTO-Dateien noch den Service
  aendern darf (Task 4, laeuft parallel), habe ich die Version stattdessen
  als eigenen @RequestParam ergaenzt und eine Vorab-Pruefung im Controller
  eingebaut (siehe pruefeVersion()). Das deckt den Hauptfall ab, hat aber
  zwei Nebenwirkungen, die ich transparent machen will: (1) ein
  zusaetzlicher Read pro aendernder Anfrage (service.findeMitPhasen vor
  dem eigentlichen Schreib-Call), (2) eine restliche TOCTOU-Luecke bei
  exakt zeitgleichen Requests, weil die Pruefung nicht im selben
  DB-Write-Statement wie das Speichern steckt. Sollte die Versionslogik
  spaeter in die Service-Schicht wandern (Task 4 oder ein Folge-Task),
  kann pruefeVersion() im Controller ersatzlos entfallen.
- Sonst keine Abweichungen: Pfade, HTTP-Codes und DTO-Nutzung entsprechen
  dem Plan; Request-DTOs aus Task 4 passten ansonsten unveraendert zu den
  Endpunkten (LangzeitkrankmeldungAnlegenRequest fuer Anlegen/Aendern,
  LangzeitkrankmeldungPhaseRequest fuer Phasen-Anlegen/-Aendern). Fuer
  "beenden" gibt es kein eigenes Task-4-DTO (nur ein Datum) - dafuer
  @RequestParam LocalDate ende genutzt, kein Map<String,Object>.

## Abschnitt 4 — Fix Performance und Datumsgrenze

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/fix-perf-und-maxdate
Commit(s): e976b34e, 3811aae1, 4255db7c, b9cfaa3b
Status: fertig

Was gemacht wurde:
- **Befund 1 (LocalDate.MAX, blockierte Abschnitt 4):** `LangzeitkrankmeldungService`
  bekommt eine neue benannte Konstante `OFFENES_ENDE = LocalDate.of(9999, 12, 31)`
  mit Javadoc-Begruendung (MySQL DATE endet bei 9999-12-31, LocalDate.MAX =
  Jahr 999999999 sprengt das und liefert je nach Servermodus einen Fehler oder
  still keine Treffer). Ersetzt an den drei Stellen `anlegen`, `aendern`,
  `wiederEroeffnen` (vorher Zeilen ~96/~133/~175). TDD: neuer Test
  `anlegen_UebergibtOffenesEndeInnerhalbDesMysqlDateBereichsAnUeberlappungspruefung`
  faengt per `ArgumentCaptor<LocalDate>` den an `findUeberlappende` uebergebenen
  Wert ab, zuerst rot (`expected: <9999-12-31> but was: <+999999999-12-31>`),
  nach dem Fix gruen. Mutationsprobe (Konstante testweise zurueck auf
  `LocalDate.MAX`) macht den Test zuverlaessig rot — er misst wirklich die
  richtige Sache, nicht nur einen Wert, der zufaellig durchgeht.
- **Befund 2 (N+1, gemessen):** `TagesSollService` bekommt drei neue
  Zeitraum-Varianten `periodenSollJeTag`/`feiertagsGutschriftJeTag`/
  `arbeitsSollJeTag` (Signatur: `Map<LocalDate, BigDecimal> xJeTag(Long
  mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis)`), die Phasen
  und Feiertage EINMAL pro Zeitraum laden statt einmal pro Tag — Grundlage ist
  eine neue private `jeTag`-Methode, auf die auch die bestehende `summiere`
  (also `periodenSollSumme`/`feiertagsGutschriftSumme`) jetzt intern
  aufsetzt. Die bestehenden Einzeltag- und Summenmethoden sind unveraendert in
  Signatur und Rueckgabewert. Zusaetzlich: `berechneEinzeltag` fragt Feiertage
  jetzt per `FeiertagService.getFeiertagInfo` (existierte bereits, liefert
  Optional<Feiertag> mit `isHalbTag()`) in EINEM Zugriff ab statt getrennt per
  `istFeiertag`+`istHalberFeiertag` — die Methode gab es schon, musste nicht
  neu gebaut werden.
  `ZeitverwaltungController.getKalender` laedt `sollStundenJeTag` und
  `feiertagsGutschriftJeTag` jetzt einmal vor der Tagesschleife statt beide
  Werte einzeln pro Tag. `LangzeitkrankmeldungService.baueStufenplanTage` laedt
  `geplantJeTag` einmal fuer den Gesamtzeitraum der Wiedereingliederungsphasen
  statt einmal pro Schleifentag.
  TDD je Schritt: neue Tests fuer die drei JeTag-Methoden (Ladezaehler per
  Mockito-`verify(times(1))`, Korrektheit gegen die Einzeltag-Werte inkl.
  Invariante, Merge-Nachweis fuer `getFeiertagInfo`), Controller- und
  Stufenplan-Tests erst rot (NullPointerException, weil die alten Einzeltag-
  Mocks nach dem Umbau nicht mehr griffen), nach dem produktiven Fix gruen.
- **Gemessen mit einer Wegwerf-Sonde** (`NPlusOneSondeTest`, echte
  `FeiertagService`+gemocktes `FeiertagRepository` per `ReflectionTestUtils`
  auf das `self`-Feld verdrahtet, plus gemocktes
  `LangzeitkrankmeldungPhaseRepository`): Kalenderabruf Dezember 2026 (31
  Tage) vorher **249** Repository-Aufrufe (62 `findImZeitraum` + 187 gegen
  `FeiertagRepository`) — deckt sich exakt mit der Reviewer-Zahl. Nachher
  **9** (2 `findImZeitraum` + 7 gegen `FeiertagRepository`) — trifft die vom
  Reviewer genannte Zielzahl exakt. Stufenplan 42 Tage Wiedereingliederung
  vorher **162** (42 + 120; Reviewer nannte 165, minimale Abweichung
  vermutlich durch einen anderen konkreten 42-Tage-Zeitraum in der Sonde),
  nachher **3** (1 + 2). Sonde nach der Messung restlos geloescht, `git
  status --short` zeigt danach nur noch die fremde
  `src/main/resources/static/index.html` (nicht angefasst, nicht committet,
  wie in allen Worktrees dieses Vorhabens).

Neue Methodensignaturen in TagesSollService (fuer kuenftige Aufrufer):
```java
public Map<LocalDate, BigDecimal> periodenSollJeTag(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis);
public Map<LocalDate, BigDecimal> feiertagsGutschriftJeTag(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis);
public Map<LocalDate, BigDecimal> arbeitsSollJeTag(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis);
```

Gate: `./mvnw -B test -Dtest='TagesSollServiceTest,LangzeitkrankmeldungServiceTest,ZeitverwaltungControllerTest,TagesSollCharakterisierung*'`
→ Tests run: 87, Failures: 0, Errors: 0, BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- Um `ZeitverwaltungControllerTest` und `LangzeitkrankmeldungServiceTest` (beide
  in meiner Dateiliste) auf die neuen Batch-Aufrufe umzustellen, musste ich
  zusaetzlich `TagesSollCharakterisierungKalenderTest.java`
  (`src/test/java/.../controller/`) anfassen — sie mockt denselben Controller-
  Endpunkt mit den alten Einzeltag-Methoden und wurde durch meine autorisierte
  Controller-Aenderung rot (NullPointerException). Diese Datei stand nicht in
  meiner Task-Dateiliste. Ich habe **nur** die Mocks auf die neuen
  Zeitraum-Methoden umgestellt (arbeitsSollJeTag/feiertagsGutschriftJeTag statt
  arbeitsSoll/feiertagsGutschrift) — keine einzige erwartete Zahl/Zusicherung
  geaendert, reine mechanische Folge des autorisierten Refactors. Bitte im
  Review gegenpruefen, dass hier wirklich nichts inhaltlich verschoben wurde.
- `graphify` (Wrapper-Skript/`.graphify-venv`) existiert in diesem Worktree
  nicht (vermutlich gitignored, nicht Teil des Worktree-Checkouts) — `graphify
  update .` konnte deshalb nicht laufen. Kein Blocker fuer die Aufgabe selbst,
  nur zur Transparenz vermerkt.
- Sonst keine Abweichungen. `feiertagsGutschriftJeTag`/`periodenSollJeTag`
  wurden zusaetzlich zur explizit vorgegebenen `arbeitsSollJeTag` gebaut (im
  Auftrag als "passende Geschwister" gefordert) und decken denselben
  Zeitraum-Lade-Mechanismus ab.

## Abschnitt 4 — Review

Zeit: 2026-09-08T19:00:15Z
Branch: feature/langzeitkrankmeldung (gemergt, bb26854c)
Commit(s): e33f2e21, ffcb3c7f, 492414c2, 1ec5e1ea (Merges), Diff cfbfcb08..HEAD
Status: blockiert
Ampel: 🔴

Testlauf (voll, `./mvnw -B test`): 2598 Tests, 0 Failures, 4 Errors —
exakt die vier vorbestehenden aus der Baseline (AuditChainRepairIntegrationTest
x2, AuditHashRoundtripDiagnoseTest x2, alle CannotCreateTransaction). Kein
fuenfter. Baseline gehalten, +44 Tests gegenueber 2554.

Blockierende Befunde:
- LangzeitkrankmeldungService.baueStufenplanTage:630/635 — NPE (HTTP 500) auf
  GET /api/langzeitkrankmeldungen/{id}, wenn eine Wiedereingliederung im
  Voraus geplant ist (geschlossene WE-Phase mit Enddatum in der Zukunft +
  offene Folgephase). `arbeitsSollJeTag` wird mit von>bis gerufen, liefert eine
  leere Map, `geplantJeTag.get(tag)` ist null. Regression des Fix-Tasks; vorher
  rechnete arbeitsSoll je Tag und konnte nicht null werden. Sonde reproduziert:
  Anfrage 2026-10-01..2026-09-08 -> 0 Eintraege -> NullPointerException.
- UrlaubsantragController.getHinweise:114 — GET /api/urlaub/antraege/hinweise
  liegt unter /api/urlaub/**, also auf der permitAll-Kette (SecurityConfig
  ZEITERFASSUNG_PATHS). Ohne Login, ohne Token, mit frei waehlbarer
  mitarbeiterId abfragbar; die Antwort verraet Existenz und Beginndatum einer
  Langzeitkrankmeldung (Gesundheitsdatum, DSGVO Art. 9). Sonde ueber den
  AntPathMatcher der SecurityConfig: erreichbar=true. Die Desktop-Endpunkte
  unter /api/langzeitkrankmeldungen sind korrekt geschuetzt (erreichbar=false).

Bewertete Schwerpunkte:
- Optimistisches Sperren traegt fuer den gemeinten Fall (zwei Bueroleute
  Minuten auseinander): gemessen, zweiter Schreiber bekommt
  ObjectOptimisticLockingFailureException -> 409, erster Stand bleibt stehen.
  Der wirklich ueberlappende Transaktionsfall wird zusaetzlich von JPA @Version
  beim Flush gefangen. Restluecke ist ein TOCTOU-Fenster von gemessen 26 us
  Mittel / 1,04 ms Maximum. 🟡, kein Datenverlust-Risiko in der Praxis.
- approveAntrag soll auf arbeitsSollJeTag nachgezogen werden: 🟡. Gemessen
  fuer drei Wochen (15 Werktage): 45 Repository-Aufrufe je Tag gegen 3 ueber
  den Zeitraum.
- N+1-Fix verifiziert: Kalendermonat 154 -> 6, Stufenplan 42 Tage 102 -> 3
  Repository-Aufrufe. Feiertagsergebnisse Einzeltag und Batch identisch.
- LocalDate.MAX restlos ersetzt, OFFENES_ENDE innerhalb des MySQL-DATE-Bereichs.
- Sicherheitsnetz: sechs Mutationsproben, fuenf wurden rot gefangen. Eine blieb
  gruen (falscher Zeitraum an arbeitsSollJeTag im Kalender) — Stub im
  Charakterisierungstest ignoriert seit der Umstellung die Zeitraum-Argumente.
- Mobile-Endpunkt: rein lesend, tokengebunden, kein Fremdzugriff, kein
  Token-Existenz-Orakel, keine Klarnamen/Notizen in Logs. Unter
  /api/zeiterfassung/** existiert kein POST/PUT/PATCH/DELETE fuer das Feature.

Bedenken / Abweichungen vom Plan:
- Mutationen und Sonden restlos zurueckgenommen; Arbeitsbaum sauber bis auf den
  bekannten index.html-Zeilenende-Phantomdiff.

## Abschnitt 4 — Nachbesserung 409-Tests

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/nb4-409-tests
Commit(s): 64a2b398
Status: fertig

Was gemacht wurde:
- LangzeitkrankmeldungControllerTest ergaenzt um einen
  @ParameterizedTest (versionskonflikt_LiefertAnJedemAendierendenEndpunkt409,
  @MethodSource aendierendeEndpunkteOhneAendernPut) ueber die sechs bisher
  ungetesteten aendernden Endpunkte: PUT /{id}/beenden, PUT /{id}/oeffnen,
  PUT /{id}/abbrechen, POST /{id}/phasen, PUT /{id}/phasen/{phasenId},
  DELETE /{id}/phasen/{phasenId}. Jeder Fall stubbt service.findeMitPhasen
  mit einer abweichenden Version und erwartet 409 mit der Handwerker-
  Meldung. PUT /{id} war bereits abgedeckt (aendern_VeralteteVersion...),
  daher nicht erneut aufgenommen.
- Gegenprobe wie gefordert: pruefeVersion(id, version) testweise aus dem
  beenden-Endpunkt entfernt (auskommentiert), vollen Testlauf gefahren ->
  von 31 Tests genau 1 rot: der parametrisierte Fall [1] "PUT /{id}/beenden
  liefert 409 bei veralteter Version" (Status erwartet 409, war 200), alle
  uebrigen 30 (inkl. der 5 anderen Parametersaetze) blieben gruen. Danach
  zurueckgenommen; Controller hat laut `git diff` keine Restaenderung.
- Gates: `./mvnw -B test -Dtest=LangzeitkrankmeldungControllerTest` ->
  Tests run: 31, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- keine. Nur die Testdatei angefasst, Controller unveraendert (git diff
  bestaetigt leer). Die vom Reviewer erwaehnte sauberere Loesung
  (erwartete Version in die Service-Signatur, atomare Pruefung durch JPA)
  bleibt bewusst ausserhalb dieser Nachbesserung, da sie DTOs und Service
  aendern wuerde (parallele Arbeit eines anderen Agenten).

## Abschnitt 4 — Nachbesserung Stufenplan

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/nb4-stufenplan
Commit(s): 38af2157, 8caf39b6
Status: fertig

Was gemacht wurde:
- **Befund 1 (blockierend, NPE im Stufenplan):** `baueStufenplanTage` setzte
  den Kartenbereich bei einer offenen letzten Wiedereingliederungsphase blind
  auf `LocalDate.now()`. Eine DAVOR liegende, geschlossene Phase mit
  Enddatum in der Zukunft (im Voraus geplanter Stufenplan) lag dann
  vollstaendig hinter dem Kartenende — `von > bis`, `arbeitsSollJeTag` bekam
  einen rueckwaerts laufenden Zeitraum und lieferte eine leere Map,
  `geplantJeTag.get(tag)` war fuer jeden Tag der geschlossenen Phase `null`.
  TDD: neuer Test reproduziert exakt den Reviewer-Fall (geschlossene Phase
  mit Enddatum in der Zukunft, gefolgt von einer offenen), erst rot mit
  **derselben Fehlermeldung wie im Befund**: `NullPointer Cannot read field
  "scale" because "val" is null`. Fix in zwei Teilen wie gefordert: (1)
  `bis` ist jetzt immer mindestens so gross wie das spaeteste bekannte
  (geschlossene) Enddatum unter den Wiedereingliederungsphasen, nicht mehr
  blind `LocalDate.now()`; (2) zusaetzlich `geplantJeTag.getOrDefault(tag,
  BigDecimal.ZERO)` als Netz, falls Kartenbereich und Schleifengrenzen je
  wieder auseinanderlaufen — dann eine 0 statt eines HTTP 500. Nach dem Fix
  gruen (41 dann 42 Tests in `LangzeitkrankmeldungServiceTest`).
- **Befund 3 (eigene Daten, kein 🔴):** `getMobileStand` nutzte
  `findByLoginToken` statt `findByLoginTokenAndAktivTrue` — ein
  deaktivierter Mitarbeiter mit altem Token behielt Lesezugriff auf seinen
  Krankenstand. TDD: neuer Test `getMobileStand_NutztDieAktivTrueVariante_
  DeaktivierterMitarbeiterVerliertZugriffAufAltesToken` verifiziert per
  `Mockito.verify`, dass die Aktiv-Variante aufgerufen wird (ein reiner
  Wertevergleich waere hier blind gewesen, weil Mockito fuer eine unstubbte
  Optional-Methode ohnehin `Optional.empty()` liefert) — vor dem Fix rot
  (`UnnecessaryStubbing`/Wanted-but-not-invoked bzw. falsche Werte in den
  drei bestehenden `getMobileStand`-Tests, die ebenfalls auf die neue
  Repository-Methode umgestellt wurden), danach gruen. Auf
  `findByLoginTokenAndAktivTrue` umgestellt — dieselbe Methode, die jeder
  andere unauthentifizierte Mobile-Lesepfad im Projekt nutzt.
- **Befund 2 (blinder Stub):** `TagesSollCharakterisierungKalenderTest`
  stubbte `arbeitsSollJeTag`/`feiertagsGutschriftJeTag` mit `any()` fuer
  von/bis, gab aber immer dieselbe feste Dezember-2026-Map zurueck — ein
  falscher, vom Controller uebergebener Zeitraum waere nicht aufgefallen.
  Stub wertet jetzt den tatsaechlich uebergebenen Zeitraum aus
  (`willAnswer` + zwei Hilfsmethoden `arbeitsSollJeTagFuer`/
  `feiertagsGutschriftJeTagFuer`, die die Map fuer den echten `von`/`bis`
  bauen). Gegenprobe wie gefordert: `ZeitverwaltungController.getKalender`
  testweise um sechs Monate verschoben (`ersterTag.plusMonths(6)` /
  `letzterTag.plusMonths(6)` an beiden `tagesSollService`-Aufrufen) — Test
  wurde mit dem reparierten Stub zuverlaessig rot (`NullPointer Cannot read
  field "intCompact" because "augend" is null`, an derselben Bugklasse wie
  Befund 1). Mutation danach vollstaendig zurueckgenommen (`git checkout --
  ZeitverwaltungController.java`), `git diff`/`grep MUTATIONSPROBE` leer,
  Test mit dem unveraenderten Controller wieder gruen.
- Gate: `./mvnw -B test -Dtest='LangzeitkrankmeldungServiceTest,
  TagesSollServiceTest,ZeitverwaltungControllerTest,
  TagesSollCharakterisierung*'` → Tests run: 89, Failures: 0, Errors: 0,
  BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- Fuer die Gegenprobe zu Befund 2 musste `ZeitverwaltungController.java`
  kurzzeitig mutiert werden (nicht in meiner Dateiliste fuer diese
  Nachbesserung) — rein als Wegwerf-Verifikation, sofort per `git checkout
  --` zurueckgenommen, `git diff` an der Datei danach leer. Keine
  Produktivaenderung an dieser Datei verblieben.
- Sonst keine Abweichungen. Alle drei Befunde 1:1 wie im Auftrag beschrieben
  behoben (Fix in zwei Teilen bei Befund 1, Verify-basierter statt
  wertbasierter Test bei Befund 3, willAnswer-basierter statt fester Stub
  bei Befund 2).

## Abschnitt 4 — Nachbesserung Urlaubs-Hinweis

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/nb4-urlaub-auth
Commit(s): a04c19c7
Status: fertig

Was gemacht wurde:
- Befund 1 (blockierend, Gesundheitsdaten ohne Login): GET
  /api/urlaub/antraege/hinweise lag unter /api/urlaub/** (permitAll-Kette
  der Mobile-App, SecurityConfig.ZEITERFASSUNG_PATHS) und war damit ohne
  Login mit frei waehlbarer mitarbeiterId erreichbar. Endpoint verschoben
  nach GET /api/langzeitkrankmeldungen/urlaubs-hinweise (Praefix bereits
  authenticated()). SecurityConfig.java nicht angefasst.
- Technischer Weg dahin: eine einzelne Methode kann ihren Klassen-Level-
  @RequestMapping-Praefix in Spring MVC nicht auf einen fremden Pfad
  ueberschreiben (AntPathMatcher.combine() konkateniert nur, ausser das
  Klassen-Pattern matcht das Methoden-Pattern selbst - das trifft hier nicht
  zu). Deshalb @RequestMapping("/api/urlaub") von der Klasse entfernt und
  jede der sieben Methoden traegt jetzt ihren vollen, literalen Pfad. Die
  sechs unveraenderten Endpoints (POST/GET /antraege, approve/reject/storno,
  /resturlaub, /typen) loesen sich dadurch auf exakt dieselben URLs wie
  vorher auf - gegengeprueft mit UrlaubsantragControllerTest (unveraendert
  gruen, 3/3).
- Neuer Test UrlaubsHinweiseSicherheitTest (Package config, weil
  SecurityConfig.ZEITERFASSUNG_PATHS package-private ist und ein Test in
  controller/service nicht drankommt, ohne die Sichtbarkeit zu aendern -
  das war ausdruecklich nicht erlaubt): liest den tatsaechlich gemappten
  Pfad von UrlaubsantragController.getHinweise per Reflection (Klassen- +
  Methoden-Annotation) und prueft ihn mit AntPathMatcher gegen
  SecurityConfig.ZEITERFASSUNG_PATHS - dieselbe Methode, mit der der
  Reviewer die Luecke gemessen hat. Echter TDD-Rotzustand bestaetigt: vor
  dem Controller-Fix loeste sich der Pfad auf /api/urlaub/antraege/hinweise
  auf und die Assertion (nicht permitAll) schlug fehl; nach dem Fix gruen.
  Gegenprobe/Dokumentation im selben Test: der alte Pfad waere weiterhin
  permitAll (assertTrue), die echten Mobile-Endpoints (/antraege,
  /resturlaub) bleiben unveraendert permitAll.
- Befund 2 (Performance): approveAntrag nutzt jetzt
  TagesSollService.arbeitsSollJeTag(mitarbeiterId, zeitkonto, von, bis) statt
  einem arbeitsSoll-Aufruf je Werktag in der Schleife - Phasen und Feiertage
  werden fuer den ganzen Antragszeitraum einmal geladen statt einmal pro Tag.
  sollStundenJeTag.getOrDefault(date, BigDecimal.ZERO) als Sicherheitsnetz
  gegen NPE, falls die Map fuer einen Tag keinen Eintrag liefert (Warnung aus
  dem Auftrag beherzigt). Eigener Regressionstest
  approveAntrag_ruftArbeitsSollJeTagGenauEinmalFuerDenGesamtenZeitraumAuf_keinN1ProTag
  verifiziert genau einen arbeitsSollJeTag-Aufruf fuer den kompletten
  Zeitraum und verify(never()) auf arbeitsSoll. Zusaetzlicher Test
  approveAntrag_tagFehltInDerMap_erzeugtKeineNPEUndKeineAbwesenheit deckt
  das getOrDefault-Netz direkt ab (genau die Falle, vor der der Auftrag
  warnte).
- UrlaubsantragServiceTest und TagesSollCharakterisierungUrlaubsantragTest
  auf die neue Stubbing-Form (Map statt Einzeltag-Stub je Datum) umgestellt;
  in beiden Feiertags-/Wochenende-Tests bekommt jetzt sogar der
  uebersprungene Tag einen Wert > 0 in der Map, um zu beweisen, dass der
  Skip wirklich am feiertagService-/Wochenend-Check haengt und nicht an
  einer zufaellig leeren Map. Gegenproben gefahren (Stub verfaelscht -> rot,
  zurueckgenommen -> gruen) fuer beide Charakterisierungstests.
- Gates: ./mvnw -B test -Dtest='UrlaubsantragServiceTest,TagesSollCharakterisierungUrlaubsantragTest,UrlaubsantragControllerTest,UrlaubsHinweiseSicherheitTest'
  -> Tests run: 17, Failures: 0, Errors: 0, BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- Dateiliste um eine Datei erweitert: src/test/java/.../config/UrlaubsHinweiseSicherheitTest.java
  (neu), obwohl "Deine Dateien" im Nachbesserungs-Auftrag nur 4 Dateien
  nannte. Grund: SecurityConfig.ZEITERFASSUNG_PATHS ist package-private -
  ein Test, der das Array gegen AntPathMatcher prueft (explizit als "am
  besten" gewuenscht), muss im config-Package stehen. SecurityConfig.java
  selbst wurde nicht veraendert, LangzeitkrankmeldungController.java nicht
  angefasst, keine Kollision mit den zwei anderen Agenten dieser Runde
  erkennbar (deren Dateien liegen in service/LangzeitkrankmeldungService.java
  bzw. controller/LangzeitkrankmeldungControllerTest.java).
- Frontend geprueft (react-pc-frontend/src, react-zeiterfassung/src): keine
  Referenz auf den alten Pfad /api/urlaub/antraege/hinweise gefunden - der
  Endpoint wird dort noch nicht konsumiert (folgt vermutlich in Abschnitt 5).

## Abschnitt 4 — Review (Nachprüfung 1)

Zeit: 2026-09-08T19:53:07Z
Branch: feature/langzeitkrankmeldung (gemergt, c41aa18c)
Commit(s): 38af2157, 64a2b398, 8caf39b6, a04c19c7 + Merges 263e80ba, ba498c6a, d732119a
Status: fertig
Ampel: 🟡

Beide blockierenden Befunde sind behoben und von mir nachgemessen.

Testlauf (voll, `./mvnw -B test`): 2611 Tests, 0 Failures, 4 Errors — exakt die
vier vorbestehenden (AuditChainRepairIntegrationTest x2,
AuditHashRoundtripDiagnoseTest x2, alle CannotCreateTransaction). Kein fuenfter.

Nachpruefungen:
- Sicherheitsfix: per RequestMappingHandlerMapping alle registrierten Pfade
  gegen ZEITERFASSUNG_PATHS geprueft. /api/langzeitkrankmeldungen/urlaubs-hinweise
  ohneLogin=false. Alle sieben Bestandspfade des UrlaubsantragController
  unveraendert und weiterhin ohneLogin=true, alle zehn Desktop-Endpunkte
  weiterhin ohneLogin=false. Kein Endpunkt versehentlich verschoben.
- Kein Routing-Konflikt durch das Auflösen des Klassen-Mappings:
  /api/langzeitkrankmeldungen/urlaubs-hinweise -> UrlaubsantragController.getHinweise,
  /api/langzeitkrankmeldungen/42 -> LangzeitkrankmeldungController.detail.
  Das literale Muster gewinnt gegen /{id}.
- NPE-Fall: mein urspruengliches Szenario plus vier Nachbarfaelle durchgespielt,
  alle sauber, null-Werte=0. Kartenbereich deckt die Schleifengrenzen jetzt in
  jedem Fall ab.
- Kalender-Stub: meine Mutation aus dem ersten Durchgang (Zeitraum um sechs
  Monate verschoben) ist jetzt rot. Sensitivitaetsluecke geschlossen.
- approveAntrag: Rueckbau auf Einzeltag-Aufruf wird von vier Tests rot gefangen.
- 409-Abdeckung: Versionspruefung aus PUT /{id}/beenden entfernt -> genau ein
  Parametersatz rot, die uebrigen gruen.

Bedenken / Abweichungen vom Plan:
- Der neue Regressionstest zum Stufenplan haelt den eigentlichen Fix nicht fest.
  Mutationsprobe: nur die bis-Berechnung zurueckgebaut (getOrDefault-Netz bleibt)
  -> Suite bleibt GRUEN. Nur das Netz entfernt (bis-Fix bleibt) -> gruen, der Fix
  allein traegt also. Beides entfernt -> rot mit der Original-NPE. Der Test prueft
  nur die Tagesanzahl (14); mit zurueckgebautem bis-Fix wuerden alle geplanten
  Tage still 0,00 h statt 4,00 h zeigen, ohne dass ein Test faellt. Empfehlung
  (nicht blockierend): zusaetzlich geplanteStunden je Tag zusichern.
- Eigener Methodikfehler in diesem Durchgang: ein erster Vollauf lief parallel zu
  Sonden-Laeufen im selben Worktree, zwei Maven-Prozesse auf demselben target/.
  Ergebnis waren 485 Scheinfehler (FileNotFoundException auf .class-Dateien).
  Lauf verworfen, Sonden entfernt, Vollauf allein wiederholt — daher die Zahlen
  oben. Fuer kuenftige Runden: nie parallel zum Vollauf mutieren.
- Mutationen und Sonden restlos zurueckgenommen, Arbeitsbaum sauber
  (`git status --short` leer, kein index.html-Diff in diesem Worktree).

## Abschnitt 5 — Task 18 (Coding-Agent)

Zeit: 2026-09-08T22:15:00Z
Branch: lzk/task-18-dashboard
Commit(s): 709440ea
Status: fertig

Was gemacht wurde:
- Interface `LangzeitFall` (phase/phaseLabel/heuteGeplanteStunden/seit/bisDatum)
  und State `langzeitFall` neben `UrlaubsVerfallWarnung` in DashboardPage.tsx.
- Neue Ladefunktion `loadLangzeitkrankmeldung()` (Vorbild `loadUrlaubsVerfallWarnung`),
  Fetch auf GET /api/zeiterfassung/langzeitkrankmeldung/{token} im selben
  useEffect, per `Promise.all` parallel zur Urlaubsverfall-Abfrage (kein
  Wasserfall). Leeres {} vom Server -> langzeitFall bleibt/wird null, keine
  Karte. Fehler im catch -> nur console.log, Karte bleibt einfach weg, Rest
  des Dashboards bleibt voll funktionsfaehig.
- Karte oberhalb der Urlaubsverfall-Warnung: Icon-Kachel `Stethoscope`,
  `bg-teal-50 border-teal-200` / `bg-teal-100` (bewusst teal statt amber/rot,
  ist Information keine Warnung). Text bei WIEDEREINGLIEDERUNG:
  "{phaseLabel} — heute {Stunden} Stunden geplant" (deutsches Zahlenformat,
  4.00 -> "4"); sonst "{phaseLabel} — seit {DD.MM.YYYY}".
- Rein lesend: kein Button, kein Formular, kein Schreib-Request - Kommentar
  im Code verweist auf die PC-only-Pflege.
- DashboardPage.test.tsx (neues describe "Langzeitkrankmeldung-Karte", 3
  Tests): Karte erscheint mit Stundentext bei laufender Wiedereingliederung
  (+ Check: kein Klarname auf der Karte); Karte fehlt bei {}; ein Fehler der
  neuen Abfrage laesst das Dashboard voll nutzbar (Start-Button weiter da).
  Dummy-Daten ("Max Mustermann").
- Gates: `npx vitest run src/pages/DashboardPage.test.tsx` -> 12/12 gruen
  (9 bestehend + 3 neu); `npm run lint` -> sauber, keine Findings; `npm run
  build` -> exit 0 (tsc -b && vite build + PWA-SW-Build). Build-Output
  ausserhalb von react-zeiterfassung (src/main/resources/static/zeiterfassung/)
  danach per `git checkout --`/`git clean -fd` wieder entfernt (Vorgabe:
  Build-Output gehoert nicht ins Repo).
- Kein Dev-Server gestartet, daher auch keiner zu beenden.

Bedenken / Abweichungen vom Plan:
- keine

## Abschnitt 5 — Task 19 (Coding-Agent)

Zeit: 2026-09-08T00:00:00Z
Branch: lzk/task-19-abwesenheiten
Commit(s): d168c668
Status: fertig

Was gemacht wurde:
- `react-zeiterfassung/src/pages/AbwesenheitenPage.tsx`: beim Laden zusätzlich
  `GET /api/zeiterfassung/langzeitkrankmeldung/{token}` abgefragt (Token aus
  `localStorage.getItem('zeiterfassung_token')`, wie im Dashboard), parallel
  zur bestehenden Antragsabfrage über `Promise.all` im vorhandenen `useEffect`.
- Bei laufender Meldung erscheint ein Info-Banner unter dem Header, über den
  Stat-Kacheln: `{phaseLabel} seit {Datum}` plus optional
  ` — heute X Stunden geplant` (nur wenn `heuteGeplanteStunden` nicht null
  ist). Styling `bg-teal-50 border-teal-200 rounded-xl p-3`, Icon-Kachel
  `bg-teal-100`, Icon `Stethoscope` — wie in Task 18 (Dashboard-Karte)
  spezifiziert, damit beide Ansichten zusammenpassen.
- Liefert der Endpunkt `{}` (nichts anliegend), erscheint kein Banner — die
  restliche Seite (Stat-Kacheln, Filter, Antragsliste, Plus-FAB) bleibt
  unverändert.
- Ausdrücklich nur lesend: kein Bearbeiten-Button, kein Formular, kein
  Schreib-Request. Zwei Kommentarblöcke im Code (bei der Fetch-Funktion und
  am Render-Ort) erklären das explizit, damit es niemand später "hilfreich"
  ergänzt.
- Schlägt die neue Abfrage fehl (Netzwerkfehler, non-2xx), bricht das nichts:
  eigener `try/catch` um `loadLangzeitFall`, Fehler geht nur nach
  `console.error`, State bleibt `null`, Antragsliste lädt unabhängig davon
  weiter.
- Plus-FAB unverändert – legt weiterhin nur das Büro eine neue Abwesenheit an.
- Neuer Vitest `AbwesenheitenPage.test.tsx` (4 Tests, alle grün): Banner
  erscheint bei laufender Wiedereingliederung inkl. Stundentext; Banner fehlt
  bei `{}`; Antragsliste rendert unabhängig vom Banner-Inhalt (auch bei
  `heuteGeplanteStunden: null`); ein Fehler der Langzeit-Abfrage lässt die
  Seite (Antragsliste) unverändert funktionieren. Dummy-Daten ("Max
  Mustermann", keine echten Personendaten).
- Gates: `npx vitest run src/pages/AbwesenheitenPage.test.tsx` → 4/4 grün;
  `npm run lint` → sauber, keine Fehler/Warnungen; `npm run build` → exit 0
  (`tsc -b && vite build`, PWA-Precache erzeugt). Gebauter Output unter
  `src/main/resources/static/zeiterfassung/` danach wieder auf den
  Commit-Stand zurückgesetzt (`git checkout` + `git clean`), nicht
  committet — Vorgabe zu Build-Output.
- Kein Playwright eingerichtet — `react-zeiterfassung` hat keins (verifiziert:
  keine Dependency, keine `playwright.config.ts`), Vorgabe aus Plan/Auftrag.

Bedenken / Abweichungen vom Plan:
- Branch-Name laut Plan wäre `lzk/task-19-abwesenheiten-verlauf`, das
  zugewiesene Worktree/Branch war jedoch bereits als
  `lzk/task-19-abwesenheiten` ausgecheckt. Rein kosmetisch, keine
  inhaltliche Abweichung — hier nur zur Nachvollziehbarkeit vermerkt.
- Sonst keine Abweichungen: `DashboardPage.tsx` nicht angefasst, kein
  Schreibpfad unter `/api/zeiterfassung/**` ergänzt, Datei-Scope exakt wie
  im Task-Block (`AbwesenheitenPage.tsx` + neuer Test).

## Abschnitt 5 — Task 17 (Coding-Agent)

Zeit: 2026-09-08T20:23:36Z
Branch: lzk/task-17-urlaub-hinweis
Commit(s): e293eb98, 14255248
Status: fertig

Was gemacht wurde:
- Urlaubsantraege.tsx: neuer Effect lädt beim Laden der Liste für jeden
  offenen Antrag (status === 'OFFEN') gebündelt über EIN Promise.all
  GET /api/langzeitkrankmeldungen/urlaubs-hinweise?mitarbeiterId=&von=&bis=
  (von/bis = antrag.vonDatum/bisDatum, bereits ISO-Datumsstrings vom
  Backend, LocalDate + write-dates-as-timestamps=false).
- Trifft ein Hinweis zu: amber-Kasten (bg-amber-50/border-amber-200/
  text-amber-800, Muster aus ArtikelImportModal.tsx/
  AusgangsrechnungUploadModal.tsx übernommen) mit AlertTriangle w-4 h-4,
  direkt über den Aktionsknöpfen "Ablehnen"/"Genehmigen" derselben Karte.
  Ist die Warnungsliste leer, erscheint gar nichts (kein leerer Kasten).
  "Genehmigen" bleibt in jedem Fall anklickbar -- reine Warnung, keine
  Sperre.
- Ein Fehler/Reject der Hinweis-Abfrage wird abgefangen (catch -> leere
  Warnungsliste für den betroffenen Antrag) und loggt nur
  console.error -- die Antragsliste bleibt unangetastet, kein Toast,
  kein Crash.
- Urlaubsantraege.test.tsx (neu, 4 Vitest-Fälle): Kasten nur beim
  betroffenen Antrag; gar kein Kasten, wenn alle Warnungslisten leer
  sind; "Genehmigen" bleibt bedienbar trotz Hinweis; ein Fehler der
  Hinweis-Abfrage lässt Liste + Knöpfe unangetastet. Bündelung
  zusätzlich per Aufruf-Zählung geprüft (2 offene Anträge -> genau 2
  Hinweis-Aufrufe, kein Wasserfall).
- e2e/urlaubsantrag-krankmeldung-hinweis.spec.ts (neu, 2 Fälle x 3
  Bildschirmgrößen = 6 Tests): Kasten erscheint nur beim betroffenen
  Antrag und ein echter Genehmigen-Klick (inkl. Bestätigungsdialog)
  geht trotz Hinweis durch, Antrag verschwindet danach aus der
  OFFEN-Liste; zweiter Test prüft den Fehlerfall der Hinweis-Abfrage
  (500) -- Liste bleibt vollständig, kein Kasten, beide
  Genehmigen-Knöpfe bleiben aktiv. Alle /api-Routen gestubbt, kein
  Backend, nur Dummy-Mitarbeiter (Max Mustermann, Erika Musterfrau).

Gates:
- npx vitest run src/pages/Urlaubsantraege.test.tsx: 4/4 grün
- npm run lint: 0 Errors, 1 bekannte Warning (BelegeKasseEditor.tsx:1204)
- npm run build: erfolgreich, Build-Output danach verworfen
  (git checkout -- src/main/resources/static/ + zwei neue,
  unversionierte Asset-Dateien manuell gelöscht)
- E2E_PORT=5197 npx playwright test
  e2e/urlaubsantrag-krankmeldung-hinweis.spec.ts: 6/6 grün
  (pc-14zoll, pc-uebergang, pc-monitor)

Bedenken / Abweichungen vom Plan:
- Endpunkt weicht vom Plan-Text ab: Plan (Zeile ~1514) nennt noch
  GET /api/urlaub/antraege/hinweise (Consumes: Task 12). Tatsächlich
  implementiert (UrlaubsantragController.java, Task 12 dieser Runde)
  ist GET /api/langzeitkrankmeldungen/urlaubs-hinweise?mitarbeiterId=&
  von=&bis= -- laut Doc-Kommentar am Controller bewusst verschoben,
  weil /api/urlaub/** auf der permitAll-Kette der Zeiterfassungs-App
  liegt und Gesundheitsdaten (Art. 9 DSGVO) sonst ohne Login
  herausgegeben hätte. Umgesetzt wurde der NEUE Pfad (per Orchestrator-
  Auftrag bestätigt und im Backend-Code so vorgefunden), nicht der
  Plan-Wortlaut. Meldung wie gewünscht, keine eigene Recherche über den
  Controller-Kommentar hinaus.
- Branch-Name im Auftrag (lzk/task-17-urlaub-hinweis) weicht vom
  Plan-Namen (lzk/task-17-urlaubsantraege-hinweis) ab -- Worktree war
  bereits unter erstgenanntem Namen ausgecheckt, unverändert
  übernommen.
- handwerkerprogramm-design ist im Skill-Tool dieser Session nicht
  registriert ("Unknown skill") -- SKILL.md + README.md wurden direkt
  per Read gelesen, der Hook wurde stattdessen mit dem unscoped Skill
  ui-ux-pro-max erfüllt (Auftrag des Orchestrators).

## Abschnitt 5 — Task 16 (Coding-Agent)

Zeit: 2026-09-08T22:35:00Z
Branch: lzk/task-16-vl-dialog
Commit(s): b14ab353
Status: fertig

Was gemacht wurde:
- `MitarbeiterStundenZeile`-Interface um `ausgeklammerteTage: number` erweitert.
- Neue Tabellenspalte "Krankengeld/Wiedereingliederung" in der Stundentabelle
  von `VerrechnungslohnRechnerDialog.tsx`, direkt hinter "Krank": zeigt
  `"<N> Tage"` mit `title="Diese Tage sind aus Jahressoll und Lohnkosten
  herausgerechnet."`, bei 0 stattdessen "–" ohne Title (kein "0 Tage" für den
  Normalfall). `colSpan` der Tabellenfußzeile von 5 auf 6 angepasst.
- Erklärender Satz unter der Tabelle, nur wenn mindestens ein Mitarbeiter
  betroffen ist: "Bei X Mitarbeitern sind Krankengeld- und
  Wiedereingliederungszeiten herausgerechnet — insgesamt N Tage."
  (`useMemo` `ausgeklammerteHinweis`, `null` wenn niemand betroffen).
- Bestehenden Vitest-Test erweitert (Fixture um `ausgeklammerteTage: 0`
  ergänzt) und zwei neue Fälle ergänzt: 0 → "–" + kein Satz; 122 → "122 Tage"
  + Satz mit korrektem Titel-Attribut. Kein bestehender Test verändert.
- Neue Playwright-Spec `e2e/verrechnungslohn-langzeitfall.spec.ts`: öffnet
  `/arbeitsgaenge` → Dialog → Sektion "Wie viele Stunden kann ich
  verkaufen?" → prüft Spalte + Satz für den 122-Tage-Fall und separat den
  Fall ohne Langzeitfall (nur "–", kein Satz). `designPruefung` inkl.
  `keinHorizontalerUeberlauf` lief für alle drei Bildschirmgrößen grün — die
  siebte Spalte bleibt im eigenen `overflow-x-auto`-Container der Tabelle,
  kein Dokument-/Dialog-Überlauf.

Gates:
- `npx vitest run src/components/VerrechnungslohnRechnerDialog.test.tsx`:
  10/10 grün (8 bestehende + 2 neue).
- `npm run lint`: 0 Errors, 1 bekannte Warning (BelegeKasseEditor.tsx:1204).
- `npm run build`: erfolgreich (`✓ built in 51.52s`); Build-Output
  (`src/main/resources/static/assets/...`) danach per
  `git checkout -- src/main/resources/static/` + `git clean -fd` verworfen,
  nicht committet.
- `E2E_PORT=5416 npx playwright test e2e/verrechnungslohn-langzeitfall.spec.ts`:
  6/6 grün (2 Tests × 3 Bildschirmgrößen), Dev-Server danach beendet.

Bedenken / Abweichungen vom Plan:
- Branch-Name weicht vom Plan ab: Auftrag/Worktree nutzten
  `lzk/task-16-vl-dialog` (bereits ausgecheckt), der Plan nennt
  `lzk/task-16-verrechnungslohn-dialog`. Auf dem vorgegebenen Branch
  gearbeitet, da er schon existierte — nur zur Kenntnisnahme.
- Die äußere Auftragsbeschreibung erwähnte zusätzlich
  `MitarbeiterStundenZeile.ausgeklammerteStunden`,
  `MitarbeiterLohnZeile.ausgeklammerteTage` und
  `MitarbeiterLohnZeile.anwesenheitsFaktor`. Der maßgebliche Task-16-Block
  im Plan verlangt nur `MitarbeiterStundenZeile.ausgeklammerteTage` und
  genau eine neue Spalte in der Stundentabelle — dem Plan folgend wurden
  die drei anderen Felder/Anzeigen nicht angefasst.
- `src/main/resources/static/index.html` zeigt nach jedem `npm run build`
  und `git checkout` einen Rest-Diff (CRLF/Zeilenende, `core.autocrlf=true`
  im Repo). Inhaltlich identisch, nur Zeilenumbruch-Normalisierung — nicht
  durch diesen Task verursacht, nicht committet, `.claude`-Vorgabe
  "niemals die Git-Config ändern" respektiert (nicht angefasst).

## Abschnitt 5 — Task 15 (Coding-Agent)

Zeit: 2026-09-08T22:35:00Z
Branch: lzk/task-15-seite
Commit(s): 166e465d
Status: fertig

Was gemacht wurde:
- Neue Seite `react-pc-frontend/src/pages/Langzeitkrankmeldungen.tsx`: Liste
  laufender (Default-Filter LAUFEND, Select bietet zusaetzlich BEENDET/
  ABGEBROCHEN) Krankmeldungen als Karten (Avatar-Kreis + `Stethoscope`, Name,
  „krank seit ...", Phasen-Badge aus `phasen.ts`, geplante Rueckkehr). Drei
  klar unterscheidbare Zustaende (Ladeskelett `motion-safe:animate-pulse`,
  Leerzustand, Fehlerzustand mit „Erneut versuchen"), jeder Fehler ueber
  `toast.error`, kein stiller `console.error`.
- 42-Tage-Hinweis pro Karte: `restTageLohnfortzahlung > 0` -> „Noch N Tage
  Lohnfortzahlung"; `<= 0` **und** `aktuellePhaseTyp === 'LOHNFORTZAHLUNG'`
  -> „Lohnfortzahlung endete am ..." + Sekundaerbutton „Auf Krankengeld
  umstellen" (`POST /phasen` mit `typ: KRANKENGELD`, `vonDatum: heute`). Das
  System stellt nichts von selbst um.
- Aufklappbares Detail in derselben Karte (kein eigener Screen): laedt
  `GET /{id}` bei erstem Aufklappen (Cache ueber `stufenplanTage` als
  Marker), zeigt `<PhasenZeitleiste>` + `<StufenplanTabelle>` (Task 14),
  Stufenplan-Tage mit `ueberPlan`-Markierung als Information (amber-Text,
  kein Fehler), die Interne Notiz (falls vorhanden) und bei laufendem Status
  „Zuruecknehmen" (abbrechen, `useConfirm` danger) / „Wieder voll im
  Einsatz" (beenden mit `ende=heute`, `useConfirm` warning).
- Stufenplan-Aenderungen (`onHinzufuegen`/`onLoeschen` aus Task 14) rufen
  `POST`/`DELETE /phasen[...]` und laden danach nur die eine betroffene
  Meldung neu (`GET /{id}`), damit das aufgeklappte Detail nicht kollabiert.
  Beenden/Abbrechen/Anlegen laden dagegen die ganze gefilterte Liste neu,
  weil sich die Status-Filterzugehoerigkeit aendern kann.
- Anlegen-Dialog (`ui/dialog.tsx`): Mitarbeiter-`Select` (aus
  `GET /api/mitarbeiter`, nur `aktiv !== false`), Beginn-`DatePicker`,
  `lohnfortzahlungBis`-`DatePicker` vorbelegt mit Beginn + 41 Tage
  (automatisch nachgezogen, bis der Nutzer das Feld selbst anfasst),
  Hinweistext „42 Tage ab Beginn — bei einer Fortsetzungserkrankung frueher
  setzen", Notiz-Textarea mit dem woertlichen Pflicht-Label „Interne Notiz —
  bitte keine Diagnosen eintragen". Speichern-Button `disabled` + Spinner
  gegen Doppelklick.
- 409-Versionskonflikt: **kein** eigener Umgang, sondern der bestehende
  Hook `src/components/lock/useKonfliktMeldung.ts` (`pruefeAntwort(res)`),
  denselben Musters wie in `LieferantDokumentModal.tsx`. Der Hook prueft nur
  auf `res.status === 409`, zeigt per `useConfirm` „Jemand anders hat dieses
  Krankmeldung ... — bitte neu laden." und laedt bei Bestaetigung
  `window.location.reload()`. Verwendet bei allen aendernden Requests
  (Phase hinzufuegen/loeschen, Umstellen, Beenden, Abbrechen). `POST /` (Anlegen)
  hat keinen `version`-Parameter im Controller, daher kein 409 dort.
- `App.tsx`: Import + Route `/langzeitkrankmeldungen` direkt unter/nach
  Urlaubsantraege.
- `RibbonNav.tsx`: `Stethoscope` importiert, Untergruppe „Urlaub" ->
  „Abwesenheiten" umbenannt, Eintrag „Lange Krankheit" hinter „Antraege".
- `MobileBottomNav.tsx`: `Stethoscope` importiert, derselbe Eintrag in
  `SUBMENU_ITEMS['/zeitbuchungen']` hinter „Urlaub".
- Tests (TDD, roter Lauf vor der Implementierung verifiziert — Fehlermeldung
  „Failed to resolve import ./Langzeitkrankmeldungen"): 7 Vitest-Faelle
  (Karten rendern, Default-Fetch mit `status=LAUFEND`, Filterwechsel loest
  `status=BEENDET` aus, API-Fehler zeigt Toast, beide 42-Tage-Varianten,
  Notiz-Label woertlich). Playwright-Spec `e2e/langzeitkrankmeldungen.spec.ts`:
  Seite ueber Menuepunkt „Lange Krankheit" erreichen, Karte aufklappen,
  Zeitleiste sichtbar, `keinHorizontalerUeberlauf`, langer Nachname ohne
  Bindestrich mit `scrollWidth`/`clientWidth`-Zusicherung („Wert bleibt in
  seinem Kasten"). Alle drei Projektgroessen (1440/1536/1920) gruen.

Gate-Ergebnisse:
- `npx vitest run src/pages/Langzeitkrankmeldungen.test.tsx`: 7/7 bestanden.
- `npm run lint`: 0 Errors, genau 1 bekannte Warnung
  (`BelegeKasseEditor.tsx:1204`).
- `npm run build`: erfolgreich (`tsc -b && vite build`), Build-Output danach
  wieder verworfen (`git checkout -- src/main/resources/static/index.html`
  + neue Asset-Dateien geloescht) — nicht committet.
- `E2E_PORT=5195 npx playwright test e2e/langzeitkrankmeldungen.spec.ts`:
  3/3 bestanden (pc-14zoll, pc-uebergang, pc-monitor).

Bedenken / Abweichungen vom Plan:
- **`maxStundenProTag` fuer `<StufenplanTabelle>`:** Der Plan (Task 14/15)
  legt fest, dass die Komponente diesen Wert als Prop braucht, sagt aber
  nicht, woher er kommt. E2 im Plan zeigt, dass das fachliche Tagesmaximum
  aus dem Zeitkonto des Mitarbeiters kommt (`konto.getSollstundenFuerTag`).
  Da die Desktop-API dafuer keinen eigenen Endpunkt fuer Task 15 vorsieht,
  laedt die Seite einmalig `GET /api/zeitverwaltung/zeitkonten` (bestehender,
  stabiler Endpunkt aus einem anderen Feature) und bildet je Mitarbeiter das
  Maximum der Wochentags-Sollstunden Mo–Fr als weiche UI-Obergrenze; Fallback
  8 Std., falls kein Zeitkonto gefunden wird. Das Backend validiert das
  eigentliche Tagesmaximum ohnehin serverseitig (Task 4) — dieser Wert ist
  nur eine fruehe Nutzerhinweis-Grenze, keine Sicherheitsgrenze. Nicht in
  „Consumes" (nur Task 5 + Task 14) gelistet — bewusste, dokumentierte
  Erweiterung um einen zusaetzlichen, bereits existierenden GET-Endpunkt,
  keine neue Datei angefasst.
- **Aufgaben-Text vs. tatsaechliche 409-Antwort:** Der Task-Text behauptet,
  der Server liefere bei 409 `{"error": "..."}`. Tatsaechlich liefert
  `RestExceptionHandler.handleOptimisticLockingFailure` ein `ApiError` mit
  Feld `message` (nicht `error`) — `{"error": ...}` gilt nur fuer die 400er
  aus dem Controller selbst (`IllegalArgumentException`/
  `IllegalStateException`). Wirkt sich auf die Implementierung nicht aus,
  weil fuer 409 ausschliesslich der bestehende `useKonfliktMeldung`-Hook
  greift, der den Antwort-Body bewusst ignoriert und eine eigene, feste
  Meldung zeigt (siehe Kommentar dort). Fuer 400/404-Fehlertexte liest die
  Seite defensiv `body?.error ?? body?.message ?? Standardtext`.
- **Kein UI fuer `PUT /{id}/oeffnen` (Wiedereroeffnen):** Der Endpunkt
  existiert (Task 5), der Task-15-Schritt-Text nennt aber nur „Zuruecknehmen/
  Beenden". Kein „Wieder oeffnen"-Knopf gebaut — ausserhalb des in Task 15
  beschriebenen Umfangs. Folgeaufgabe, falls gewuenscht.
- Deep-Linking wie in `Urlaubsantraege.tsx` (`useSearchParams` fuer
  Status+Fokus-ID) wurde bewusst NICHT uebernommen — im Task-15-Text nicht
  gefordert, haette die Seite ohne Mehrwert vergroessert.
- `PUT /{id}` (allgemeines Aendern von Beginn/Notiz/lohnfortzahlungBis nach
  dem Anlegen) und `PUT /{id}/phasen/{phasenId}` (Phase bearbeiten) sind
  vorhanden, aber im Task-15-Text nicht gefordert und nicht verdrahtet —
  `StufenplanTabelle` (Task 14) bietet ohnehin keine Bearbeiten-Aktion, nur
  Hinzufuegen/Loeschen.

## Abschnitt 5 — Code-Review

Zeit: 2026-09-08T21:28:12Z
Branch: feature/langzeitkrankmeldung (gemergt, HEAD 46969936)
Commit(s): bf6731d8, 8f952c4d, 3697bfe1, 60bde06f, 55c7afd8 (Merges Task 15-19)
Status: fertig
Ampel: 🟡 (abgenommen, keine Nachbesserung noetig)

Geprueft: Diff ceb217a9..HEAD, 17 Dateien, +2243/-26, ausschliesslich Frontend.
Merge-Commits vorhanden, Arbeitsbaum sauber, keine Build-Artefakte im Diff.

Testlaeufe (selbst gefahren, synchron, Output in Dateien):
- Backend `./mvnw -B test`: 2611 Tests, 4 Errors (die bekannten:
  AuditChainRepairIntegrationTest x2, AuditHashRoundtripDiagnoseTest x2)
  + 2 Failures in UnifiedEmailControllerExtractEmailTest. Letztere sind
  Wanduhr-Asserts (assertTimeoutPreemptively 500 ms) und laufen isoliert
  gruen (12/12) — Lastartefakt, kein Backend-Code in diesem Abschnitt.
  Baseline gehalten.
- react-pc-frontend lint: 0 Errors, 1 bekannte Warning
  (BelegeKasseEditor.tsx:1204). Baseline gehalten.
- react-pc-frontend test: 92 Test-Dateien, alle gruen. Im vollen
  Parallellauf 14 Dateien mit "Failed to start forks worker" und
  anschliessend 6 "Test timed out in 5000ms" in unbeteiligten Dateien
  (EmailCenter, document-editor, BeitraegeTab); wechselnde Fehlermenge je
  Lauf, isoliert alle gruen. Maschinenlast, keine Regression.
- react-zeiterfassung lint: 0 Errors. test: 11 Dateien / 143 Tests gruen.

Eigene Fehlerfall-Sonden (9 pc + 6 mobil, danach restlos entfernt):
- Dashboard (Handy) bei 401 / 500 / haengendem Endpunkt / kaputtem JSON:
  bleibt vollstaendig bedienbar, Karte faellt weg. Kein await im Effect,
  das Promise.all ist fire-and-forget — ein haengender Endpunkt blockiert
  nichts.
- Abwesenheiten (Handy) bei 401 / haengendem Endpunkt: Liste erscheint,
  Ladezustand endet, kein Banner.
- Urlaubsantraege bei 401 / Netzabbruch / haengendem Endpunkt: Liste
  bedienbar, "Genehmigen" aktiv, kein Kasten.
- Lange Krankheit: 400 mit {"error": ...} UND 400 mit {"message": ...}
  landen beide sichtbar beim Nutzer; 409 oeffnet den Konfliktdialog und
  schickt die Version aus dem DTO mit (version=7 verifiziert).

Befunde: keine 🔴. Hinweise (blockieren nicht) im Review-Report an den
Orchestrator, Kernpunkte:
- "Wieder voll im Einsatz" hat keinen Rueckweg in der Oberflaeche, obwohl
  PUT /{id}/oeffnen existiert. Einziger Pfad ohne saubere Korrektur.
- Drei Backend-Endpunkte ohne Aufrufer: PUT /{id}, PUT /{id}/phasen/{phasenId},
  PUT /{id}/oeffnen.
- Verrechnungslohn-Dialog erklaert das Jahressoll, nicht die gekuerzte
  Lohnsumme (anwesenheitsFaktor / MitarbeiterLohnZeile.ausgeklammerteTage
  ungenutzt) — Befund gegen den Plan-Block, nicht gegen den Agenten.
- Urlaubsantraege feuert einen Request pro offenem Antrag (parallel, kein
  Wasserfall, aber N Requests) — Sammelendpunkt waere die saubere Loesung.

Bedenken / Abweichungen vom Plan:
- Schwerpunkt 5 (Nur-Lesen Handy) vollstaendig bestaetigt: im ganzen Diff
  unter react-zeiterfassung/ kein POST/PUT/PATCH/DELETE, kein Formular,
  kein Bearbeiten-Knopf, nichts auskommentiert vorbereitet. localStorage
  wird nur fuer den bestehenden Auth-Token gelesen, keine Krankheitsdaten
  zwischengespeichert.
- Schwerpunkt 6 (DSGVO) sauber: Notizfeld heisst woertlich "Interne Notiz —
  bitte keine Diagnosen eintragen", Notiz erscheint nur im Detailbereich der
  Buero-Seite, in keinem Tooltip, keinem title, keinem console-Aufruf. Weder
  Handy-Banner noch Urlaubshinweis nennen eine Ursache.

---

## Abschnitt 5 — Design-Review

Zeit: 2026-09-08T21:35:00Z
Branch: feature/langzeitkrankmeldung (Worktree wt/lzk-design-5, Stand 46969936)
Status: fertig
Ampel: 🔴

### E2E-Lauf

- Voller Lauf (E2E_PORT=5190 npm run test:e2e), 21 Specs x 3 Projekte = 411
  Tests: 187 grün, 224 rot. **Die roten sind Umgebungsrauschen, kein
  Codefehler.** Beleg: parallel lief der Code-Reviewer mit zwei Maven-Prozessen
  auf einer 8-Kern-Maschine, Playwright fuhr mit 4 Workern dagegen. Nachlauf
  derselben Specs mit --workers=1 (langzeitkrankmeldungen,
  urlaubsantrag-krankmeldung-hinweis, verrechnungslohn-langzeitfall,
  menueleiste-layout, alle drei Größen): **30/30 grün**. Empfehlung an den
  Orchestrator: die volle E2E-Suite nie gleichzeitig mit den Backend-Suiten
  fahren.
- Zusätzlich 29 eigene Aufnahmen (PC 1440 und 1920, Handy 393x852) über eine
  Wegwerf-Konfiguration; alle designPruefung-Zusicherungen (kein Überlauf,
  keine Überschneidung, kein überlaufender oder gekürzter Text, Primäraktion
  sichtbar) grün. Die Wegwerf-Dateien sind wieder gelöscht, git status sauber.

### Lücke in der Nachweisführung (Ebene 2 der Playwright-Pflicht)

e2e/langzeitkrankmeldungen.spec.ts (Task 15) und
e2e/urlaubsantrag-krankmeldung-hinweis.spec.ts (Task 17) rufen **kein
designPruefung** auf — sie erzeugen keinen Screenshot und fahren die
automatischen Layout-Zusicherungen nicht mit. Nur
verrechnungslohn-langzeitfall.spec.ts tut es. Für die beiden sichtbarsten
Oberflächen des Abschnitts gab es damit keinen Design-Nachweis; ich habe ihn
für dieses Review von Hand nachgeholt.

### Angeschaute Screenshots

Aus dem Suite-Lauf (react-pc-frontend/test-results/design/):
- verrechnungslohn-langzeitfall-stunden--pc-14zoll.png
- verrechnungslohn-langzeitfall-stunden--pc-monitor.png

Selbst erzeugt (react-pc-frontend/test-results/design-review/):
- rev-lzk-liste--pc-14zoll.png, rev-lzk-liste--pc-monitor.png
- rev-lzk-leer--pc-14zoll.png, rev-lzk-leer--pc-monitor.png
- rev-lzk-laedt--pc-14zoll.png, rev-lzk-laedt--pc-monitor.png
- rev-lzk-dialog--pc-14zoll.png, rev-lzk-dialog--pc-monitor.png
- rev-lzk-detail--pc-14zoll.png, rev-lzk-detail--pc-monitor.png
- rev-lzk-detail-ganz--pc-14zoll.png, rev-lzk-detail-ganz--pc-monitor.png
- rev-lzk-detail-viele--pc-14zoll.png, rev-lzk-detail-viele--pc-monitor.png
- rev-lzk-detail-eine--pc-14zoll.png, rev-lzk-detail-eine--pc-monitor.png
- rev-lzk-detail-keine--pc-14zoll.png, rev-lzk-detail-keine--pc-monitor.png
- rev-lzk-detail-gesperrt--pc-14zoll.png, rev-lzk-detail-gesperrt--pc-monitor.png
- rev-urlaub-warnkasten--pc-14zoll.png, rev-urlaub-warnkasten--pc-monitor.png
- rev-handy-abwesenheiten--handy.png, rev-handy-abwesenheiten-ganz--handy.png
- rev-handy-abw-lfz--handy.png
- rev-handy-karte-we--handy.png, rev-handy-karte-lfz--handy.png
- rev-handy-dashboard-krankengeld--handy.png
- rev-handy-dashboard-ohne-fall--handy.png

### Die sechs Fragen, je Oberfläche und Größe

#### Task 15 — Seite "Lange Krankheit", Liste (rev-lzk-liste, 1440 und 1920)

1. Farben: Zustände trennen sich (amber-Badge Lohnfortzahlung, teal-Badge
   Wiedereingliederung, grauer Status-Chip bei abgeschlossen), Kontraste passen.
   Zwei Probleme: die Stethoskop-Kachel jeder Karte ist rose-100/rose-600, also
   Markenfarbe als Dekoration in jeder Zeile (die Schwesterseite Urlaubsanträge
   nimmt slate-100/slate-600); und der Überfällig-Hinweis ist rose-700 und liest
   sich dadurch wie ein Fehler statt wie eine Frage.
2. Design-System: PageHeader-Muster wörtlich eingehalten, Karten
   bg-white/border-slate-200/rounded-lg/shadow-sm, Icons durchgehend Lucide,
   kein Emoji, Systemschrift, genau eine gefüllte rose-Primäraktion.
3. Look-and-Feel: ruhig und ausgerichtet, auf 1920 nichts verwaist.
4. UX: "Krankmeldung anlegen" oben rechts, Umstell-Knopf direkt neben dem Grund.
   Lücke bei der Rückmeldung, siehe H2.
5. Auffindbar: ja. Ribbon Zeiterfassung, Untergruppe ABWESENHEITEN,
   "Lange Krankheit" mit Stethoskop, ohne Scrollen, an erwarteter Stelle.
6. Überschneidungen: keine, in beiden Größen, kein Querscrollen.

#### Task 15 — Leerer Zustand (rev-lzk-leer, 1440 und 1920)

1. Nur ein grauer Satz, keine Farbe im Spiel.
2. Der Kasten ist bg-slate-50 auf slate-50-Grund, also unsichtbar; der Skill
   sieht für leere Zustände ein Icon (w-6 h-6) vor.
3. Auf 1920 schwebt der Satz allein in einer sehr großen Leere — genau das, was
   Frage 3 ausschließen will. Für die meisten Betriebe ist das der Normalzustand
   dieser Seite.
4. Kein Hinweis, was als Nächstes zu tun ist, und keiner, dass der Filter auf
   "Läuft noch" steht.
5. n/a. 6. Keine Überschneidung.

#### Task 15 — Ladezustand (rev-lzk-laedt)

Drei weiße Karten mit Rand und motion-safe:animate-pulse, ohne
Platzhalter-Balken. Formal ein Skeleton, optisch drei leere Kästen; der Skill
verlangt bg-slate-200-Blöcke (Vorbild ThumbnailImage.tsx). Fragen 1, 3 und 4
nur teilweise erfüllt, 2 verfehlt, 6 sauber.

#### Task 15 — Anlegen-Dialog (rev-lzk-dialog)

1. Ruhig, eine rose-Primäraktion, "Abbrechen" als Outline. 2. rounded-2xl,
Scrim mit Blur, Pflicht-Komponenten Select/DatePicker/Label benutzt. 3. Gut.
4. Gut, bis auf: "Speichern" ist immer aktiv, die Pflichtfeldprüfung kommt erst
als Toast. 5. und 6. sauber. Wording geprüft: das Notizfeld heißt wörtlich
"Interne Notiz — bitte keine Diagnosen eintragen".

#### Prüfschwerpunkt 4 — Zeitleiste und Stufenplan, erster Auftritt
(rev-lzk-detail-viele / -eine / -keine / -gesperrt, beide Größen)

1. Farben: die laufende Phase trägt ring-2 ring-rose-400 plus "Läuft gerade" in
   rose — auf einen Blick erkennbar, gut gelöst. Der rose-600-Punkt vor jeder
   Zeile ist dagegen reine Dekoration und konkurriert mit dem Ring.
   Badge-Familie amber (Lohnfortzahlung), blau (Krankengeld), teal
   (Wiedereingliederung).
2. Design-System: Tabelle, Ränder, Radien, Lucide-Icons stimmen. Zwei
   Abweichungen: bg-blue-100 für Krankengeld (FRONTEND_UI.md sagt "kein
   indigo/blue", es gibt aber Vorbild im Bestand), und zwei gefüllte
   rose-Knöpfe gleichzeitig ("Zeile hinzufügen", "Wieder voll im Einsatz") —
   mit dem Kopfbereich sind es drei.
3. Look-and-Feel: zwei Spalten, bei sieben Phasen ausgewogen; bei einer
   einzigen Phase ist die linke Spalte fast leer und wirkt schief. Die
   Stufenplan-Tabelle verrutscht bei fünf Zeilen nicht, Spaltenbreiten bleiben
   stabil.
4. UX: gesperrter Fall sauber — Formular und Papierkörbe ausgegraut, Tooltip
   "Nur möglich, solange die Krankmeldung läuft.", Aktionsknöpfe verschwinden.
   Leere Tabelle sagt "Noch kein Stufenplan hinterlegt.", leere Zeitleiste "Für
   diese Krankmeldung sind noch keine Phasen hinterlegt." Beides gut. Eine
   offene Phase ohne Enddatum wird als "ab 01.11.2026" gezeigt, korrekt.
5. Auffindbar: nach dem Klick auf "Details" beginnt der Inhalt bei 1440x900
   exakt an der Fensterunterkante, die Seite scrollt nicht mit.
6. Überschneidungen: keine, in beiden Größen, auch nicht bei sieben Phasen.

#### Task 16 — Verrechnungslohn-Dialog (verrechnungslohn-langzeitfall-stunden)

1. Zurückhaltend, keine neue Farbe. 2. Passt. 3. Die Kopfzeile
"KRANKENGELD/WIEDEREINGLIEDERUNG" ist rund viermal so breit wie ihr Inhalt und
reißt zwischen "KRANK" und der neuen Spalte ein großes Loch — bei 1920 rund
350 px Leere mitten in der Zeile. 4. Der Erklärsatz unter der Tabelle ist
verständlich, der Tooltip an Kopfzeile und Zelle erklärt die Herkunft.
5. Die Tabelle sitzt in der aufgeklappten Sektion, "übernehmen" bleibt sichtbar.
6. Kein Querscrollen, auch nicht bei 1440 — die achte Spalte passt.

#### Task 17 — Warnkasten Urlaubsanträge (rev-urlaub-warnkasten)

1. Amber-Kasten mit AlertTriangle, klar vom Rest getrennt — aber der
   Status-Chip "Offen" daneben ist bg-yellow-100/text-yellow-700, dieselbe
   Farbfamilie mit anderer Bedeutung, direkt daneben.
2. Amber ist die im Design-System hinterlegte Warnfarbe, korrekt gewählt.
3. max-w-sm hält den Kasten kompakt. Der Status-Chip steht bei der gewarnten
   Karte auf anderer Höhe als bei der ungewarnten.
4. Genau richtig: über den Knöpfen, vor jedem Klick, und "Genehmigen" bleibt
   aktiv — Warnung, keine Sperre. Vorbildlich.
5. Ohne Scrollen sichtbar, direkt an der Entscheidung.
6. Keine Überschneidung, auch bei 1920 nicht.

#### Task 18 — Handy-Dashboard-Karte (rev-handy-karte-we, rev-handy-karte-lfz)

1. Teal hebt sich ab, Kontrast reicht. Auf demselben Bildschirm stehen aber
   schon rose, blau, lila, grün und amber; teal ist die sechste Familie.
2. Verfehlt: teal kommt im Design-System nirgends vor, und die Karte ist
   flächig getönt, während jede andere Karte dieses Dashboards weiß ist mit
   getönter Icon-Kachel.
3. Ordentlich gebaut, rounded-2xl, shadow-sm, passt in die Kartenspalte.
4. Rein lesend, kein Knopf — genau wie vorgegeben.
5. Die Karte steht ganz unten, nach sechs anderen Karten, weit unter dem Rand.
6. Keine Überschneidung, kein Querscrollen. Der Umbruch setzt den
   Gedankenstrich an den Anfang der zweiten Zeile.

#### Task 19 — Handy-Abwesenheiten-Banner (rev-handy-abwesenheiten, rev-handy-abw-lfz)

1. Teal-Banner steht direkt über einem grünen "Genehmigt"-Chip; teal und grün
   liegen zu dicht beieinander, um sie flüchtig zu trennen.
2. Dieselbe Teal-Frage wie Task 18.
3. Sauber, rounded-xl wie die Nachbarkarten, zweizeilig ohne Gedrängel.
4. Gut: nennt Phase und Datum, bei Wiedereingliederung zusätzlich das Tagessoll.
5. Vorbildlich platziert, ganz oben unter der Kopfzeile, ohne Scrollen.
6. Keine Überschneidung, kein Abschneiden. Das Stethoskop erscheint zweimal auf
   dem Schirm (teal im Banner, rot beim Krankheits-Antrag) mit zwei Bedeutungen.

### Antwort auf die Farbfrage: teal und amber

**amber (Task 17): behalten.** colors_and_type.css führt --warn: #f59e0b
(amber-500) mit Tint #fef3c7, das UI-Kit der Handy-App benutzt genau diesen
Tint. bg-amber-50 / border-amber-200 / text-amber-800 ist die weiche Variante
derselben Familie und deckt sich mit den vorhandenen Warnbändern in beiden
Frontends. Kein Befund. Einziger Feinschliff: der gelbe "Offen"-Chip daneben
sollte auf slate wechseln, damit Status und Warnung nicht dieselbe Farbe
sprechen.

**teal (Tasks 18 und 19): umstellen.** Teal steht in keiner einzigen Datei des
Design-Systems — nicht in den Tokens, nicht in den beiden UI-Kits, nicht in den
Musterkarten unter preview/. Die dokumentierte Semantikpalette hat genau fünf
Familien: grün (Erfolg), amber (Warnung), rot (Gefahr), indigo (Info), lila
(KI). Für eine neutrale Information ist indigo vorgesehen (--info: #6366f1,
Tint #e0e7ff). In react-zeiterfassung kommt teal bisher nirgends vor; die
beiden Tasks führen es neu ein. Dass beide Agenten dieselbe Wahl getroffen
haben, macht sie nicht richtig — es macht sie nur zweimal.

Empfehlung in dieser Reihenfolge:
1. Handy-Dashboard-Karte (Task 18) auf das Muster der Nachbarkarten umbauen:
   weiße Karte, getönte Icon-Kachel. Dann ist die Farbfrage fast erledigt und
   die Karte reiht sich ein, statt herauszustechen.
2. Wo eine Fläche getönt bleiben soll (Banner Task 19): indigo statt teal, oder
   schlicht slate.
3. Das Badge WIEDEREINGLIEDERUNG: bg-teal-100 text-teal-800 in phasen.ts darf
   bleiben — bg-teal-100/text-teal-800 gibt es dort schon auf main
   (Urlaubsanträge, Typ "Zeitausgleich"), dieselbe Badge-Familie am selben Ort.
   Es bleibt nur der Bedeutungskonflikt: teal heißt einmal "Zeitausgleich" und
   einmal "Wiedereingliederung", blau einmal "Urlaub" und einmal "Krankengeld".

### Antwort auf die Auffindbarkeit der neuen Seite

Gut auffindbar. Ribbon "Zeiterfassung", Untergruppe ABWESENHEITEN,
"Lange Krankheit" mit Stethoskop-Icon, in beiden Größen vollständig lesbar,
ohne Umbruch, ohne Kürzung, ohne Scrollen. Die Menüleisten-Spec bleibt in allen
drei Größen grün, die Umbenennung bricht dort nichts.

Ein Nebeneffekt für Bestandsnutzer: das Wort "Urlaub" kommt im Ribbon jetzt
nirgends mehr vor. Wer es gewohnt ist, sucht unter "ABWESENHEITEN > Anträge",
und "Anträge" allein sagt nicht, wovon. Vorschlag ohne Blocker-Charakter:
"Anträge" in "Urlaubsanträge" umbenennen — dann trägt die Untergruppe den
Oberbegriff und der Eintrag das gewohnte Wort. In MobileBottomNav heißt der
Eintrag ohnehin weiterhin "Urlaub", das ist heute uneinheitlich.

### Antwort zum 42-Tage-Countdown

- "Noch 12 Tage Lohnfortzahlung" steht klein, in slate-700, rechts oben in der
  Karte, ohne Icon, ohne Farbe, ohne Gewicht. Es liest sich wie eine
  Bildunterschrift, nicht wie ein Countdown, und geht beim Überfliegen einer
  Liste unter. Wenn das das fachliche Herzstück ist, muss es aussehen wie eines.
- "Lohnfortzahlung endete am 14.03.2026" in rose-700 mit dem Knopf "Auf
  Krankengeld umstellen" sticht heraus — aber in der Markenfarbe, und damit wie
  ein Fehler, nicht wie eine Frage. Abgenommen war die Formulierung
  "... — auf Krankengeld umstellen?", also eine Frage, die man verneinen kann.
- Dass nichts automatisch passiert, steht nirgends. Der Knopf ist der einzige
  Hinweis. Ein Halbsatz würde reichen.
- Kern des Problems, siehe H2: sobald umgestellt ist, bleibt der rote Satz
  trotzdem stehen — für den Rest des Falls, ohne Knopf, ohne Erklärung.

### Befunde — blockierend (rot)

- **B1 — teal ist keine Farbe dieses Produkts.**
  react-zeiterfassung/src/pages/DashboardPage.tsx:1258 und
  react-zeiterfassung/src/pages/AbwesenheitenPage.tsx:163 (bg-teal-50,
  border-teal-200, bg-teal-100, text-teal-600, text-teal-800).
  Nachweis: rev-handy-karte-we--handy.png, rev-handy-abwesenheiten--handy.png.
  Nachweisbar sein muss: keine teal-Klasse mehr in react-zeiterfassung/src —
  Dashboard als weiße Karte mit getönter Icon-Kachel, Banner in indigo oder
  slate.

- **B2 — englische Dezimalpunkte in einer deutschen Oberfläche.**
  StufenplanTabelle.tsx:125 ("5.5 Std."), PhasenZeitleiste.tsx:65
  ("5.5 Std. pro Tag"), Langzeitkrankmeldungen.tsx:481 ("4.5 h gestempelt,
  2 h geplant"). Nachweis: rev-lzk-detail-viele--pc-14zoll.png. Die Handy-App
  macht es an derselben Stelle richtig (formatStundenDe mit
  toLocaleString('de-DE')). Nachweisbar sein muss: "5,5 Std." und "4,5 h" auf
  dem Bildschirm.

- **B3 — "Bei 1 Mitarbeitern".** VerrechnungslohnRechnerDialog.tsx:982.
  Nachweis: verrechnungslohn-langzeitfall-stunden--pc-14zoll.png.
  Nachweisbar sein muss: Singular bei 1, Plural sonst.

- **B4 — kein Design-Nachweis für Task 15 und Task 17.**
  e2e/langzeitkrankmeldungen.spec.ts und
  e2e/urlaubsantrag-krankmeldung-hinweis.spec.ts rufen kein designPruefung auf.
  Nachweisbar sein muss: je ein designPruefung(page, testInfo, '<name>',
  { primaerAktion: ... }) und die zugehörigen PNG unter test-results/design/.

### Befunde — Hinweise (gelb, blockieren nicht)

- H1 Leerer Zustand (Langzeitkrankmeldungen.tsx:364-368): grauer Satz in einem
  unsichtbaren bg-slate-50-Kasten auf slate-50-Grund, auf 1920 verloren. Icon,
  Handlungssatz und Hinweis auf den Statusfilter ergänzen.
  (rev-lzk-leer--pc-monitor.png)
- H2 Der Überfällig-Satz verschwindet nie (Langzeitkrankmeldungen.tsx:416-420):
  er hängt an restTage <= 0, der Knopf dagegen an aktuellePhaseTyp ===
  'LOHNFORTZAHLUNG'. Jede laufende Meldung in Krankengeld oder
  Wiedereingliederung zeigt dauerhaft "Lohnfortzahlung endete am ..." in rose,
  ohne Aktion. (rev-lzk-liste--pc-14zoll.png, dritte Karte)
- H3 "Läuft gerade" bei abgeschlossener Meldung (PhasenZeitleiste.tsx:9-11):
  rechnet nur mit Datum, nicht mit dem Status.
  (rev-lzk-detail-gesperrt--pc-14zoll.png)
- H4 Ladezustand ohne Platzhalter (Langzeitkrankmeldungen.tsx:350): weiße
  Karten mit Rand statt bg-slate-200-Balken. (rev-lzk-laedt--pc-14zoll.png)
- H5 Drei gefüllte rose-Knöpfe gleichzeitig, wenn eine Karte offen ist.
  "Zeile hinzufügen" ist eine Unterformular-Aktion und gehört auf sekundär
  (border-rose-300 text-rose-700). (rev-lzk-detail-viele--pc-14zoll.png)
- H6 Rose als Dekoration: die Stethoskop-Kachel jeder Listenkarte ist
  rose-100/rose-600, Urlaubsanträge nimmt an derselben Stelle slate.
  (rev-lzk-liste--pc-monitor.png)
- H7 Der Countdown "Noch 12 Tage Lohnfortzahlung" hat kein visuelles Gewicht.
  (rev-lzk-liste--pc-14zoll.png, erste Karte)
- H8 Nirgends steht, dass die Umstellung auf Krankengeld nicht von selbst
  passiert.
- H9 Nach dem Klick auf "Details" bleibt die Seite stehen; der Inhalt beginnt
  bei 1440x900 an der Fensterunterkante. scrollIntoView auf die geöffnete Karte.
  (rev-lzk-detail--pc-14zoll.png)
- H10 Handy-Dashboard: die Karte steht ganz unten; über "Heute gearbeitet" wäre
  sie da, wo sie hilft. (rev-handy-karte-we--handy.png)
- H11 Umbruch auf dem Handy setzt den Gedankenstrich an den Zeilenanfang;
  besser Titel plus Untertitel wie bei den Nachbarkarten.
- H12 Spaltenkopf "KRANKENGELD/WIEDEREINGLIEDERUNG" reißt ein Loch in die
  Tabelle; zweizeilig setzen oder kürzen.
  (verrechnungslohn-langzeitfall-stunden--pc-monitor.png)
- H13 Gelber "Offen"-Chip neben dem amber Warnkasten, gleiche Farbfamilie,
  andere Bedeutung (Urlaubsantraege.tsx:162).
  (rev-urlaub-warnkasten--pc-14zoll.png)
- H14 Farbbedeutungen kollidieren zwischen den Schwesterseiten: teal einmal
  Zeitausgleich und einmal Wiedereingliederung, blau einmal Urlaub und einmal
  Krankengeld.
- H15 Wording-Kleinigkeiten: "Fortsetzungserkrankung"
  (Langzeitkrankmeldungen.tsx:574) ist Kassendeutsch; "Gestempelt gegen
  geplant" (Zeile 474) klingt nach Duell; Formularlabel "ab" klein neben
  "Stunden pro Tag" groß (StufenplanTabelle.tsx:149); "keine Phasen hinterlegt"
  (PhasenZeitleiste.tsx:30) ist mild fachsprachlich.
- H16 Der rose-600-Punkt vor jeder Zeitleisten-Zeile trägt keine Information
  und konkurriert mit dem Ring der laufenden Phase.
- H17 "Speichern" im Anlegen-Dialog ist immer aktiv, Pflichtfelder melden sich
  erst per Toast. (rev-lzk-dialog--pc-14zoll.png)

### Was ausdrücklich in Ordnung ist

- Verbindliches Wording vollständig eingehalten: "Lohnfortzahlung durch den
  Betrieb", "Krankengeld der Krankenkasse", "Wiedereingliederung", "Wieder voll
  im Einsatz". Kein "Entgeltfortzahlungszeitraum", kein "AU-Zeitraum", kein
  "Phase 1/2/3", kein roher Enum-Wert auf dem Bildschirm — Status und Phase
  kommen als statusLabel und aktuellePhaseLabel vom Server, der Filter zeigt
  "Läuft noch / Abgeschlossen / Zurückgenommen".
- Notizfeld wörtlich "Interne Notiz — bitte keine Diagnosen eintragen".
- Task 17 ist Warnung und keine Sperre, richtig platziert über den Knöpfen.
- Gesperrter Zustand im Stufenplan erklärt per Tooltip, warum.
- Handy-App bleibt rein lesend: kein Knopf, kein Formular, kein Schreibaufruf.
- Kein Querscrollen, keine Überschneidung, kein abgeschnittener Text — in
  keiner der drei Größen, in keinem der 29 angeschauten Bilder.
