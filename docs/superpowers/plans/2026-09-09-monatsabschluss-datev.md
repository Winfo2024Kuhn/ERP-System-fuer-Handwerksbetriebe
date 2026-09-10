# Plan: Monatsabschluss und DATEV-Dateiexport

Issue: #102
Feature-Branch: codex/monatsabschluss-datev
Spec: docs/superpowers/specs/2026-09-09-monatsabschluss-datev.md
Kontext-Log: docs/superpowers/plans/2026-09-09-monatsabschluss-datev-log.md

## Global Constraints

- Dauerhafte Zahlenfeld-Nutzervorgabe in AGENTS.md verbindlich: Nullwerte bei Klick-/Tab-Fokus leeren, Nichtnullwerte erhalten, Texteingabe mit leeren Zwischenständen/deutschem Komma, vollständige Pflichtzahl beim Speichern validieren. Kennnummern bleiben Ziffernstrings mit führenden Nullen. UI-Tests der Tasks5/6 decken diese Regeln an betroffenen Feldern ab.
- DATEV-Export muss vollständig implementiert und mit offiziellen Formatregeln, Golden-Datei und echtem Browser-Download geprüft werden. Keine Platzhalter. Ein tatsächlicher Import in einer DATEV-Installation ist hier nicht verfügbar und wird im Abschluss ausdrücklich nicht als erfolgt behauptet.

- Pflichtlektüre: `AGENTS.md`, `docs/agent instructions/docs/BACKEND_ARCH.md`, `docs/agent instructions/docs/FRONTEND_UI.md`, `docs/agent instructions/docs/TESTING_SECURITY.md`, `.agents/skills/loese-problem/references/kriterien.md`. Frontend zusätzlich Skills `handwerkerprogramm-design`, `new-page`, `playwright-design-pruefung`. Mobile-Änderungen sind für die ausdrücklich freigegebene Ergänzung systemeigener Eingaben und Meldungen gemäß [Zusatzplan](2026-09-09-systemeigene-eingaben.md) erlaubt; keine Änderungen an mobiler Fachlogik außerhalb dieses UI-Scopes.
- Bestehende Dateien wurden direkt gelesen; Recherche mit `./graphify query 'MonatsSaldoService Monatsabschluss Berechtigung Kalender Mitarbeiter Abteilung'`, `./graphify explain MonatsSaldoService`, `./graphify path MonatsSaldoService ZeitverwaltungController` (gerichtet kein Pfad; Explain zeigt Referenz), zusätzlicher Graph-Abfrage für App und Tests. Zeilen beziehen sich auf Stand 2026-09-09 vor Umsetzung.
- Keine neuen Berechnungsregeln: Gesamtist = Arbeit + Abwesenheit + Feiertag + Korrektur; Differenz = Gesamtist − Soll. Geschlossene Werte gewinnen immer vor Livewerten. Keine alten Abschlüsse mit heute berechneten Abwesenheitsdetails anreichern.
- Konstruktor-Injection, DTOs, parametrisierte Abfragen. Neue idempotente Migrationen V372 (Snapshot) und V373 (DATEV); vor Anwendung auf Kollisionen prüfen, alte Migrationen unverändert lassen. Bestehende Einzel-API bleibt kompatibel.
- Neue Übersichts-, Sammel- und DATEV-Endpunkte verlangen serverseitig `MonatsabschlussBerechtigungService.verlangeAkteur(Authentication)`. Vorhandener Kalenderstatus behält seine bisherigen Rechte. CSRF über vorhandenen Fetch-Interceptor, keine Client-Identität aus Headern akzeptieren.
- Hauptscreen: ein Monat, Vorgabe letzter vergangener Monat. Vergleich: sechs Monate bis einschließlich gewähltem Monat, Mitarbeiter-/Abteilungsfilter übernehmen, Statusfilter ausdrücklich nicht (Vergleich aller Stände). Auswahl beim Filter-/Monatswechsel leeren, alte Antworten mit AbortController/Anfrageschlüssel verwerfen.
- Technische Grenzen: Seite 50 Zeilen, maximal 100; maximal 500 gefilterte Mitarbeiter und 500 explizite Mitarbeiter-Monatsreferenzen pro Aktion, maximal 12 Exportmonate. Größere Filtermenge wird klar mit Aufforderung zum Eingrenzen zurückgewiesen, niemals still abgeschnitten. Gefilterte Summen/Alle-Auswahl betreffen sämtliche Ergebnisse, nicht nur die Seite. Eine zusätzliche ID-Abfrage oder mitgelieferte `auswahl` enthält alle maximal 500 Referenzen.
- Keine Kalender-/Audit-Abfragen in Übersichts- oder Vergleichsschleifen. Vorhandene Salden mit einer Set-Abfrage laden. Nur fehlende/ungültige offene Monatscaches dürfen begrenzt über vorhandene `getOrBerechne`-Berechnung nachgeladen werden; Performance-Test weist nach, dass vollständig abgeschlossene Listen keine Liveberechnung und keine per-Zeile-Audits ausführen. Kalte Cache-Berechnung als begrenzte Bestandsoperation dokumentieren, nicht neue Kopie der Sollstundenlogik schreiben.
- Tests nur Dummy-Daten. Keine Inhalte der Nutzerabrechnung, Namen, Personalnummern oder Betriebsnummern übernehmen. Pro Task fokussierte Tests; am Ende Gesamt-Backendtests, PC-Tests/Build, Browserprüfung, Diffprüfung und einmal Graphify-Update. MySQL-Konkurrenztests ausschließlich gegen eigene wegwerfbare Testdatenbank (Bestand verwendet create-drop!).

## Gemeinsame Verträge

Neue Java-Records als geschachtelte Typen in `dto/MonatsabschlussUebersichtDto.java`, Frontend spiegelt sie in `features/monatsabschluss/types.ts`. Java-Präfix für unten aufgeführte Pfade ist `src/main/java/org/example/kalkulationsprogramm/`, Testpräfix `src/test/java/org/example/kalkulationsprogramm/`; alle Files-Listen benennen vollständige Pfade.

```java
record Referenz(Long mitarbeiterId, int jahr, int monat) {}
record Stand(Long mitarbeiterId, int jahr, int monat, Long version) {}
record Kennzahlen(BigDecimal istStunden, BigDecimal sollStunden,
    BigDecimal abwesenheitsStunden, BigDecimal feiertagsStunden,
    BigDecimal korrekturStunden, BigDecimal gesamtIst, BigDecimal differenz) {}
record Zeile(Referenz referenz, String mitarbeiterName, List<Long> abteilungIds,
    boolean festgeschrieben, Long version, LocalDateTime festgeschriebenAm,
    Kennzahlen kennzahlen) {}
record Filter(int jahr, int monat, Long mitarbeiterId, Long abteilungId,
    String status, int page, int size) {} // ALLE | OFFEN | ABGESCHLOSSEN
record Uebersicht(List<Zeile> items, long totalElements, int page, int size,
    Kennzahlen summen, List<Stand> auswahl) {}
record Vergleichsmonat(int jahr, int monat, Kennzahlen summen,
    int offen, int abgeschlossen) {}
record SammelRequest(List<Referenz> auswahl) {}
record Einzelergebnis(Referenz referenz, String status, String meldung) {}
// status = ABGESCHLOSSEN | BEREITS_ABGESCHLOSSEN | FEHLGESCHLAGEN
record SammelResponse(List<Einzelergebnis> ergebnisse) {}
```

API-Basis `B=/api/zeitverwaltung/monatsabschluesse`:

- `GET B/uebersicht?jahr=&monat=&mitarbeiterId=&abteilungId=&status=&page=&size=` → Uebersicht.
- `GET B/vergleich?jahr=&monat=&mitarbeiterId=&abteilungId=` → List<Vergleichsmonat>, sechs Monate aufsteigend.
- `POST B/sammelabschluss` mit SammelRequest → SammelResponse (200 mit Teilergebnissen; ungültiger Gesamtrequest 400, fehlendes Recht 403).
- Bestehend `GET B/{mitarbeiterId}/{jahr}/{monat}` nur bei geöffnetem Verlauf nutzen; Kalenderlink `/zeitbuchungen?mitarbeiterId=…&jahr=…&monat=…`.

DATEV-Verträge in `dto/DatevDto.java`, TypeScript ebenfalls im gemeinsamen Types-Modul:

```java
record Zuordnung(String kategorie, boolean ausgeschlossen, String lohnart) {}
record Personalnummer(Long mitarbeiterId, String personalnummer) {}
record Konfiguration(Long version, String ziel, String beraterNr, String mandantenNr,
    List<Zuordnung> zuordnungen, List<Personalnummer> personalnummern) {}
record ExportRequest(List<Stand> auswahl, Long konfigurationVersion) {}
record Hinweis(Referenz referenz, String kategorie, BigDecimal stunden, String meldung) {}
record Vorpruefung(boolean gueltig, List<Hinweis> fehler, List<Hinweis> ausschluesse,
    List<Stand> auswahl, Long konfigurationVersion) {}
record Datei(String dateiname, String contentType, byte[] inhalt) {}
```

- `GET/PUT B/datev/konfiguration` → Konfiguration. PUT atomar, Version prüfen, 409 bei veraltetem Stand. GET liefert leere konfigurierbare Vorgabe (ziel LODAS, keine geratenen Nummern/Zuordnungen).
- `POST B/datev/vorpruefung` → Vorpruefung (fachliche Fehler im Body, kein Download).
- `POST B/datev/export` gleicher Request → TXT oder ZIP; Fehler 409 für veränderte Stände, sonst 400/403. Download prüft alles erneut; kein vertrauenswürdiges Client-Flag „bereits geprüft“.
- Kategorien: ARBEIT, FEIERTAG, URLAUB, KRANKHEIT, FORTBILDUNG, ZEITAUSGLEICH, KRANKENGELD, WIEDEREINGLIEDERUNG. KORREKTUR ist stets ausgeschlossen, keine Lohnart dafür. Für alte Daten zusätzlich Ausschlusshinweis ABWESENHEIT_UNGEGLIEDERT. Fehlende Entscheidung ist nicht gleich Ausschluss; jede unterstützte Kategorie muss ausdrücklich zugeordnet oder ausgeschlossen sein.

## Abschnittsübersicht und Review-Gates

| Abschnitt | Tasks parallel | Voraussetzung | Review |
| --- | --- | --- | --- |
| 1 | 1, 3 | bestehender main-Stand | Backend-Code und fokussierte Tests |
| 2 | 2, 4 | Abschnitt1 geprüft und gemergt | Backend-Code, Security, Konkurrenztests |
| 3 | 5 | Abschnitt2 geprüft und gemergt | Code plus separater Designreview der Hauptseite |
| 4 | 6 inklusive Page-Integration | Abschnitt3 geprüft und gemergt | Code plus separater Designreview einschließlich DATEV-End-to-End |

In der gemeinsamen Ausführung maximal drei Coding-Agenten gleichzeitig; jede Consumes-Leistung muss vor Taskstart abgenommen sein. Featurebranch und Taskbranches benutzen das vorgegebene `codex/`-Präfix. Taskworktrees werden jeweils erst vom geprüften Featurestand angelegt. Keine Task-Commits verändern fremde Dateien oder den Graph gemeinsam.

Designreview nach Abschnitten3 und4 jeweils durch einen eigenen Designreview-Agenten parallel zum Code-Reviewer, mit separatem Worktree `.Codex/worktrees/monatsabschluss-datev-design-3` bzw. `-design-4` und Branch `codex/monatsabschluss-datev-design-3` bzw. `-design-4`. Beide Reviews prüfen denselben zusammengeführten Abschnittsstand. Der nächste Abschnitt beginnt erst nach beiden Ergebnissen. Verbindliche Viewports/UX-Kriterien stehen im Design-Prüfskill. Keine gemeinsam beschriebenen Testberichte in Coding-Worktrees.

### Datei → Tasks (Schreibbesitz)

Die vollständigen Einzeldateien stehen zusätzlich unter jedem Task. Bei Verzeichniszeilen sind ausschließlich die dort ausdrücklich gelisteten Dateien freigegeben.

| Dateien | Task(s) | Konfliktauflösung |
| --- | --- | --- |
| MonatsSaldo.java, MonatsSaldoService.java, AbwesenheitRepository.java, V372, Snapshot-/Bestandsservice-Test | 1 | exklusiv Abschnitt1 |
| DatevDto, Konfigurations-/Personalnummer-Entities, Repositories, Service/Controller/Tests, V373 | 3 | exklusiv Abschnitt1 |
| neue MonatsabschlussUebersicht-/Sammel-DTO/Repository/Services/Controller/Tests | 2 | exklusiv Abschnitt2 |
| DatevExport-Service/Repository/Controller/Tests, LodasDateiWriter/Test, Golden-File | 4 | exklusiv Abschnitt2 |
| features/monatsabschluss/types.ts, api.ts, Monatsabschluss.test.tsx, App.tsx, RibbonNav.tsx, e2e/monatsabschluss.spec.ts | 5 | exklusiv Abschnitt3 |
| pages/Monatsabschluss.tsx | 5, 6 | getrennte Abschnitte3 und4; gleicher Agent, erst Hauptseite dann DATEV-Integration |
| DatevBereich.tsx, DatevBereich.test.tsx, e2e/monatsabschluss-datev.spec.ts | 6 | exklusiv Abschnitt4 |

Kontext-Log: append-only; alle Agenten benutzen den absoluten Logpfad im Hauptcheckout und das atomare mkdir-Lock aus `kontext-log-format.md`. Keine unabhängigen Worktree-Kopien parallel fortschreiben. Ausschließlich der Orchestrator/Reviewer übernimmt neue Logblöcke in den Featurebranch.

## Abschnitt 1: Snapshot und Konfiguration

### Task 1: Historische Abwesenheitsdetails beim Einzelabschluss sichern

- Branch: `codex/monatsabschluss-datev-task-1`
- Worktree: `.Codex/worktrees/monatsabschluss-datev-task-1`
- Files: `src/main/java/org/example/kalkulationsprogramm/domain/MonatsSaldo.java`; `src/main/java/org/example/kalkulationsprogramm/service/MonatsSaldoService.java`; `src/main/java/org/example/kalkulationsprogramm/repository/AbwesenheitRepository.java`; `src/main/resources/db/migration/V372__monatsabschluss_abwesenheit_snapshot.sql`; `src/test/java/org/example/kalkulationsprogramm/service/MonatsabschlussSnapshotTest.java`; `src/test/java/org/example/kalkulationsprogramm/service/MonatsabschlussServiceTest.java`.
- Vorbild: MonatsSaldo.java:52 (`@Version`), :79 (DECIMAL-Stunden); MonatsSaldoService.java:125 (Abschluss mit Mitarbeiterlock), :170 (DTO/Audit), :244 (Cache darf geschlossene Daten nicht überschreiben); AbwesenheitRepository.java:118 (LEFT JOIN auf Krankheitsphase); Migration V368__zeitkonto_datenfundament.sql:4 (idempotente Spalten). Testvorbild MonatsabschlussServiceTest.java:19.
- Produces: Nullable `BigDecimal`-Felder mit Lombok-Zugriff `urlaubStunden`, `krankheitStunden`, `fortbildungStunden`, `zeitausgleichStunden`, `krankengeldStunden`, `wiedereingliederungStunden` in MonatsSaldo. Alle null bedeutet alter Abschluss ohne Detailstand. Additive Methode `MonatsabschlussDto abschliessenOhneVerlauf(Long id, int jahr, int monat, Authentication auth)`.
- Consumes: bestehende `MonatsSaldoService.abschliessen(...)`, `AbwesenheitsTyp`, `LangzeitkrankmeldungPhaseTyp`.
- Steps:
  - [x] Zuerst Tests: vier normale Typen und drei Krankheitsphasen; unphasierte Krankheit und LOHNFORTZAHLUNG zählen zu krankheitStunden, KRANKENGELD/WIEDEREINGLIEDERUNG getrennt. Summe der sechs Detailwerte entspricht exakt vorhandenen abwesenheitsStunden. Alte geschlossene Nullwerte bleiben null, selbst wenn Live-Abwesenheiten existieren.
  - [x] Migration legt sechs nullable DECIMAL(10,2)-Spalten ohne Backfill an. Bei neuem Abschluss alle sechs Werte einschließlich Nullstunden als 0 speichern. Repository ergänzt eine gruppierte Aggregation nach Abwesenheitstyp und Phasentyp, LEFT JOIN erhält normale Abwesenheiten; kein Laden der gesamten Tagesentities.
  - [x] In abschliessen zwischen vorhandener Berechnung/Cache-Speicherung und `setFestgeschrieben(true)` Details unter demselben Mitarbeiterlock in derselben Transaktion setzen. Die Berechnung bestehender Gesamtstunden bleibt unverändert. Abweichende Detailsumme ist Konflikt und rollt Abschluss zurück.
  - [x] Beide öffentlichen Abschlusssignaturen delegieren innerhalb derselben Klasse auf eine gemeinsame private Abschlussimplementierung mit Flag `mitVerlauf`; `statusDto` erhält passende interne Variante. Neue Variante liefert audit=[] und fragt Audit nicht ab; bestehende API liefert weiter bisherigen Verlauf. Keine Änderung bestehender Lock-Reihenfolge und kein REQUIRES_NEW innerhalb Mitarbeiterlock.
  - [x] Wiederöffnung macht vorhandene Details für Export unbenutzbar (Statusprüfung); nächster Abschluss ersetzt vollständig. Test: Wiederöffnung/Änderung/erneuter Abschluss ergibt neue Details, CacheWrite kann geschlossene Details nie ändern. Fokussierte Tests und bestehende MonatsabschlussServiceTest ausführen.

### Task 3: DATEV-Konfiguration und Personalnummern persistieren

- Branch: `codex/monatsabschluss-datev-task-3`
- Worktree: `.Codex/worktrees/monatsabschluss-datev-task-3`
- Files: `src/main/java/org/example/kalkulationsprogramm/dto/DatevDto.java`; `src/main/java/org/example/kalkulationsprogramm/domain/DatevKonfiguration.java`; `src/main/java/org/example/kalkulationsprogramm/domain/DatevPersonalnummer.java`; `src/main/java/org/example/kalkulationsprogramm/repository/DatevKonfigurationRepository.java`; `src/main/java/org/example/kalkulationsprogramm/repository/DatevPersonalnummerRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/DatevKonfigurationService.java`; `src/main/java/org/example/kalkulationsprogramm/controller/DatevKonfigurationController.java`; `src/main/resources/db/migration/V373__datev_konfiguration.sql`; `src/test/java/org/example/kalkulationsprogramm/service/DatevKonfigurationServiceTest.java`; `src/test/java/org/example/kalkulationsprogramm/controller/DatevKonfigurationSecurityTest.java`.
- Vorbild: MonatsSaldo.java:52 (@Version), MonatsabschlussBerechtigungService.java:23 (serverseitiger Akteur), V368__zeitkonto_datenfundament.sql:64 (CREATE TABLE IF NOT EXISTS/FKs), MonatsabschlussSecurityTest.java:20. Kein vorhandenes DATEV-Personalnummernfeld in Mitarbeiter.java:17–135; eigene Zuordnung vermeidet Auswirkungen auf Stammdaten-PUTs.
- Produces: DatevDto-Verträge; `Konfiguration laden(Authentication auth)`; `Konfiguration speichern(Konfiguration dto,Authentication auth)`. Persistente Singleton-Konfiguration id=1 mit @Version; Zuordnungen als @ElementCollection oder validiertes JSON in TEXT, keine Java-Enum/VARCHAR-Mischung. Personalnummerntabelle mit Mitarbeiter-FK/PK und eindeutiger normalisierter Nummer.
- Consumes: BerechtigungService; BerechtigungService; DatevDto definiert eigene strukturell identische Referenz/Stand-Records, damit keine Abhängigkeit von Task2 entsteht.
- Steps:
  - [x] Tests für erstes Laden/Speichern, Versionskonflikt, unbekanntes Ziel, unbekannte Kategorien, vollständig explizite Entscheidungen, ungültige/doppelte Personalnummern. Nummern 1–5 Ziffern, Lohnart 1–4 Ziffern; führende Nullen zur Dublettenprüfung normalisieren. Nummern als Strings transportieren.
  - [x] Leere Vorgabe bleibt bearbeitbar, unvollständige Einrichtung darf gespeichert werden; Exportvorprüfung blockiert fehlende Pflichtangaben. Gespeicherte nichtleere Nummern/Formate dagegen sofort validieren. Nur LODAS zulassen. Berater-/Mandantennummer numerisch und offizielle LODAS-Grenzen anwenden, keine Standardwerte aus Beispielabrechnung übernehmen.
  - [x] PUT sperrt Singleton, prüft Version, ersetzt Konfiguration und Mitarbeiterzuordnungen atomar; prüft IDs gegen Menschenstamm. Jede Änderung an Nummern/Mapping erhöht dieselbe Konfigurationsversion, damit Exportvorschau veralten kann. Migration legt Singleton an, Initialversion 0.
  - [x] Controller implementiert GET/PUT mit identischem Abschlussrecht. Tests für CSRF/Rechte/überlange Strings/Injection/XSS, keine freien Dateipfade oder Secrets. DB-Unique schützt konkurrierende Dubletten.

## Abschnitt 2: Backend-Übersicht und Export

### Task 2: Gefilterte Übersicht, Vergleich und transaktionsgetrennter Sammelabschluss

- Branch: `codex/monatsabschluss-datev-task-2`
- Worktree: `.Codex/worktrees/monatsabschluss-datev-task-2`
- Files: `src/main/java/org/example/kalkulationsprogramm/dto/MonatsabschlussUebersichtDto.java`; `src/main/java/org/example/kalkulationsprogramm/repository/MonatsabschlussUebersichtRepository.java`; `src/main/java/org/example/kalkulationsprogramm/service/MonatsabschlussUebersichtService.java`; `src/main/java/org/example/kalkulationsprogramm/service/MonatsabschlussSammelService.java`; `src/main/java/org/example/kalkulationsprogramm/controller/MonatsabschlussUebersichtController.java`; `src/test/java/org/example/kalkulationsprogramm/service/MonatsabschlussUebersichtServiceTest.java`; `src/test/java/org/example/kalkulationsprogramm/service/MonatsabschlussSammelServiceTest.java`; `src/test/java/org/example/kalkulationsprogramm/controller/MonatsabschlussUebersichtSecurityTest.java`; `src/test/java/org/example/kalkulationsprogramm/service/MonatsabschlussSammelMysqlTest.java`.
- Vorbild: MonatsSaldoRepository.java:40 (Zeitraumquery), MitarbeiterRepository.java:23 (alle Menschen inklusive ausgeschieden), MonatsabschlussController.java:10 (Routen/Auth), MonatsabschlussMysqlTest.java:30 (echte getrennte Transaktionen), MonatsabschlussSecurityTest.java:20 (MockMvc mit SecurityConfig).
- Produces: gemeinsame Records; `Uebersicht lade(Filter filter, Authentication auth)` und `List<Vergleichsmonat> vergleich(int jahr,int monat,Long mitarbeiterId,Long abteilungId,Authentication auth)`; `SammelResponse abschliessen(SammelRequest request,Authentication auth)`.
- Consumes: Task1 `abschliessenOhneVerlauf`; bestehende `MonatsSaldoService.getOrBerechne(Long,int,int)`, BerechtigungService, MonatsSaldo getters.
- Steps:
  - [x] Tests voranstellen für Filter, Gesamtmenge trotz Seite50, Status und Summen, ausgeschiedene Menschen/Systemausschluss, historische Salden ohne Livezugriff, sechs Monate über Jahresgrenze. Repositoryprojektions-Abfragen liefern Mitarbeiter-ID/Name/Abteilungs-IDs und Salden in Sets ohne EAGER-Entity-Stammdatenlisten.
  - [x] Mensch-Kandidaten nach Mitarbeiter/Abteilung bestimmen (keine Beschränkung auf heute aktiv/fuehrtZeitkonto). Über 500 klare 400-Meldung. Monatsstände gesammelt holen, nur Cache-Misses/offene ungültige Stände begrenzt über vorhandenen Service nachrechnen. Erst anschließend Status filtern, sortieren nach Name/ID, Summen und vollständige Referenzliste bilden, Seitenausschnitt liefern. Keine `status()`-/Kalenderaufrufe.
  - [x] Vergleich benutzt denselben Kennzahlenmapper und Snapshot-Vorrang, lädt alle sechs Monate gesammelt und nennt Anzahl offener/geschlossener Einträge. Fehlende offene Caches über Bestandsberechnung; keine neue Stundenformel.
  - [x] Sammelservice ist selbst ohne umspannende Schreib-TX. Request und Recht vor Schleife validieren, Duplikate ablehnen; pro Eintrag TransactionTemplate mit REQUIRES_NEW und Aufruf von Task1-Variante. Bereits geschlossen anhand unter Mitarbeiterlock gelesenem Saldo melden, ohne neue Abschlussberechnung. Technische Exception erst außerhalb des Templates abfangen, damit Rollback fertig ist. Andere Einträge fortsetzen; deutsche konkrete Meldungen ohne interne SQL-/Personendaten.
  - [x] Test gemischter Erfolge/Fehler sowie Wiederholung; MySQL-Latch-Test paralleler Einzel-/Sammelabschluss sichert genau ein Audit und dauerhaftes Teilergebnis trotz anderer Fehltransaktion. 401/403, CSRF, ungültige IDs/Monate, Größenlimits und Direktaufrufe mit MockMvc testen.

### Task 4: LODAS-Vorprüfung und standtreuer TXT-/ZIP-Download

- Branch: `codex/monatsabschluss-datev-task-4`
- Worktree: `.Codex/worktrees/monatsabschluss-datev-task-4`
- Files: `src/main/java/org/example/kalkulationsprogramm/service/DatevExportService.java`; `src/main/java/org/example/kalkulationsprogramm/service/LodasDateiWriter.java`; `src/main/java/org/example/kalkulationsprogramm/repository/DatevExportRepository.java`; `src/main/java/org/example/kalkulationsprogramm/controller/DatevExportController.java`; `src/test/java/org/example/kalkulationsprogramm/service/DatevExportServiceTest.java`; `src/test/java/org/example/kalkulationsprogramm/service/LodasDateiWriterTest.java`; `src/test/java/org/example/kalkulationsprogramm/controller/DatevExportSecurityTest.java`; `src/test/java/org/example/kalkulationsprogramm/service/DatevExportMysqlTest.java`; `src/test/resources/datev/lodas-stunden-erwartet.txt`.
- Vorbild: MonatsSaldoService.java:94 (Refresh verhindert alten Persistence-Context-Stand), MonatsSaldo.java:115 (Kennzahlensummen), MonatsabschlussMysqlTest.java:80 (Latch-Konkurrenztest). Writer: kein vergleichbarer LODAS-Writer; offizielle Specquelle maßgeblich.
- Produces: `Vorpruefung pruefen(ExportRequest request,Authentication auth)`; `Datei exportieren(ExportRequest request,Authentication auth)`; Writer `byte[] schreiben(Konfiguration config,int jahr,int monat,List<Buchung> buchungen)` mit internem `record Buchung(String personalnummer,String lohnart,BigDecimal stunden)`.
- Consumes: geprüfte Tasks1 und3 (Snapshotfelder, DatevDto mit eigenen Referenz-/Stand-Records, Konfigurationsservice/-repositories); keine Abhängigkeit von Task2.
- Steps:
  - [x] Tests zuerst für offene/veraltete/fehlende Stände, alte Abwesenheit ohne Details, expliziten Nur-Arbeit-Export, Korrekturausschluss, doppelte Personalnummer und negative/zu große Stunden. Keine Live-Abwesenheitsabfrage im Export, keine stillen Teil-Dateien.
  - [x] Alle ausgewählten Salden und Personalnummern per Set-Abfrage laden. Gegen jede explizite Version und festgeschrieben=true prüfen, Konfigurationsversion ebenfalls. Jede Null-Aufschlüsselung blockiert angeforderten Abwesenheitsexport; wenn sämtliche Abwesenheitskategorien ausgeschlossen sind, aggregierte alte Stunden einmal als ausgelassen melden (nicht sechsmal). Alle anderen Ausschlüsse und Korrekturen mit Stunden sichtbar zurückgeben.
  - [x] Export wiederholt Vorprüfung vollständig. Innerhalb derselben Transaktion Stände mit OPTIMISTIC-Lock registrieren und zum Commit prüfen; neue Session/Refresh verhindert stale Reads. Konfigurationsentity ebenso versionieren/prüfen. Datei als Bytes komplett erzeugen, erst nach erfolgreichem Commit über Controller zurückgeben, kein vorzeitiges HTTP-Streaming. Wiederöffnung oder Konfigurationsänderung während Erzeugung ergibt 409 und keine Bytes.
  - [x] Writer anhand offizieller DATEV-Quelle in Spec prüfen: `[Allgemein]`, `Ziel=LODAS`, BeraterNr/MandantenNr, `Datumsformat=TT/MM/JJJJ`, `Feldtrennzeichen=;`, `Zahlenkomma=,`; optionale Version_DB weglassen. Satzbeschreibung exakt `3;u_lod_bwd_buchung_standard;abrechnung_zeitraum#bwd;bs_wert_butab#bwd;bs_nr#bwd;la_eigene#bwd;pnr#bwd;`. Bewegungsdaten z.B. `3;01/01/2026;100,00;01;200;14;` (01 Stundenbuchung). BigDecimal zwei Nachkommastellen, Monatsanfang, CRLF, offizielle Windows-ANSI-Zeichenkodierung. Da nur numerische Werte/Header übertragen werden, keine Namen ausgeben. Feldlängen/Wertbereich mit offizieller Beschreibung belegen; keine stillen Rundungen oder Abschneidungen.
  - [x] Golden-File prüft tatsächliche Bytes inklusive Trennzeichen, Zeilenende, Reihenfolge, Monats-/Personalnummernsortierung, keine BOM; Summen pro Mitarbeiter/Lohnart sauber aggregieren, 0-Stunden nicht als Buchung erzeugen. Vorprüfung meldet leere resultierende Datei als Fehler.
  - [x] Ein Monat → `lodas-JJJJ-MM.txt`, mehrere → ZIP mit genau einer Datei pro Monat. Namen nur servergeneriert. Content-Disposition attachment, Cache-Control no-store. Tests lesen ZIP und vergleichen jede Datei. MySQL-Konkurrenztest: Wiederöffnung nach Vorprüfung und während Byteerzeugung verhindert Download.

## Abschnitt 3: Hauptseite und geprüfte Frontend-Verträge

### Task 5: Hauptseite mit Filtern, Sammelauswahl, Vergleich und Verlauf

- Branch: `codex/monatsabschluss-datev-task-5`
- Worktree: `.Codex/worktrees/monatsabschluss-datev-task-5`
- Files: `react-pc-frontend/src/features/monatsabschluss/types.ts`; `react-pc-frontend/src/features/monatsabschluss/api.ts`; `react-pc-frontend/src/pages/Monatsabschluss.tsx`; `react-pc-frontend/src/pages/Monatsabschluss.test.tsx`; `react-pc-frontend/src/App.tsx`; `react-pc-frontend/src/components/layout/RibbonNav.tsx`; `react-pc-frontend/e2e/monatsabschluss.spec.ts`.
- Vorbild: App.tsx:106 (Zeit-Routen), RibbonNav.tsx:105 (Zeiterfassungsgruppe), ZeiterfassungKalender.tsx:84 (URL-Parameter), :112 (AbortController), :135 (Bestätigung/Busy-Ref), ZeiterfassungKalender.test.tsx:1 (fetch-Mock/MemoryRouter/Toast).
- Produces: gemeinsame TS-Typen exakt zu API; `api.ladeUebersicht(filter,signal?)`, `api.ladeVergleich(filter,signal?)`, `api.sammelabschluss(auswahl)`, `api.ladeVerlauf(referenz,signal?)`, `api.ladeDatevKonfiguration()`, `api.speichereDatevKonfiguration(config)`, `api.pruefeDatev(request)`, `api.exportiereDatev(request):Promise<{blob:Blob;dateiname:string}>`; alle JSON-Methoden Promise des entsprechenden DTO. Page Default-Export `Monatsabschluss`.
- Consumes: geprüfte Task2 HTTP-Verträge und geprüfte Tasks3/4 DATEV-HTTP-Verträge. Keine Abhängigkeit von Task6; keine DatevBereich-Imports oder Platzhalterintegration in diesem Abschnitt.
- Steps:
  - [x] Tests für Seite50/Alle500, Abteilungs-/Statusfilter, Auswahlreset, veraltete Antworten, Abbruch, Doppelclick, Teilergebnisse, Monatsvergleich und Kalenderlink schreiben.
  - [x] Route `/monatsabschluss` mit ErrorBoundary ergänzen, Ribbon-Link „Monatsabschluss“ neben Kalender mit FileCheck. Header nach Pflichtmuster; Monats-/Jahreswahl, Mitarbeiter und Abteilung über vorhandenes Select. Berechtigung vor Datenabrufen prüfen; 403 erklärt fehlendes Recht.
  - [x] Tabelle mit allen Kennzahlen, Status/Abschlussdatum, Checkboxen und Pagination; Summen ausdrücklich „Alle gefilterten Mitarbeiter“. Headercheckbox verwendet vollständige `auswahl`, einzelne Checkbox Referenzschlüssel, verständliche aria-labels. Filter-/Monatswechsel löschen Auswahl synchron und verhindern Ausführung alter Dialoge.
  - [x] Sammelbestätigung nennt Monat und Anzahl. Busy-Ref blockiert Doppelklick; pro Referenz Ergebnis darstellen, Fehler auch toasten. Nach Erfolg Übersicht/Vergleich neu laden, fehlgeschlagene Auswahl erkennbar behalten, `notifications:refresh` auslösen. Aktueller/zukünftiger Monat deaktiviert mit Erklärung.
  - [x] Vergleich als kompakte Tabelle mit sechs Monaten und „vorläufig“ bei offenen Einträgen; Detailverlauf erst auf Klick laden, Kalenderlink trägt exakt ID/Jahr/Monat. Kein Sammelwiederöffnen.
  - [x] Für die spätere DATEV-Integration die Auswahl aus vollständigen Stand-Referenzen bereitstellen; die eigentliche Einbindung gehört ausschließlich Task6. Ziel-Props `auswahl={ausgewaehlteZeilenAlsStand}`, `mitarbeiter={verfuegbareMitarbeiter}` vorbereiten. Gefilterte Auswahl kann mehr als Seite umfassen: `Uebersicht.auswahl` liefert deshalb Stand-Referenzen einschließlich Version für alle gefilterten Ergebnisse. Offene Stände bleiben nicht exportierbar; keine Version erfinden. Tests/Build ausführen und Seite samt Download später durch Browserprüfung abnehmen.

## Abschnitt 4: DATEV-Komponente und finale Integration

### Task 6: DATEV-Einrichtung, Integration und Download auf der Seite

- Branch: `codex/monatsabschluss-datev-task-6`
- Worktree: `.Codex/worktrees/monatsabschluss-datev-task-6`
- Files: `react-pc-frontend/src/features/monatsabschluss/DatevBereich.tsx`; `react-pc-frontend/src/features/monatsabschluss/DatevBereich.test.tsx`; `react-pc-frontend/src/pages/Monatsabschluss.tsx`; `react-pc-frontend/e2e/monatsabschluss-datev.spec.ts`.
- Vorbild: ZeiterfassungKalender.tsx:135 (Bestätigung, Busy, Fehler), bestehende `components/ui/select-custom.tsx`, `components/ui/confirm-dialog.tsx:28`, `components/ui/toast.tsx:28`; kein bestehender LODAS-Einrichtungsdialog.
- Produces: `DatevBereich({auswahl,mitarbeiter}:{auswahl:Stand[];mitarbeiter:{id:number;name:string}[]}):ReactElement`.
- Consumes: Task5 Types/API; Tasks3/4 Endpoints.
- Ownership: denselben Coding-Agenten wie Task5 wiederverwenden, jedoch neuer Task6-Branch/Worktree vom abgenommenen Featurestand. Die Page-Integration ist in diesem Abschnitt exklusiv sein Besitz.
- Steps:
  - [ ] Fertigen DatevBereich auf Monatsabschluss.tsx unterhalb der Übersicht mit vollständiger Stand-Auswahl und Mitarbeitern einbinden; erst hier den realen Komponentenimport ergänzen.
  - [ ] Tests für Einrichtung speichern/409, nicht ausgewählte Mitarbeiter, Vorprüfung mit Fehlern/Ausschlüssen, Multi-Monat, geänderte Auswahl und Downloadkonflikt. Blob/ObjectURL-Test prüft Dateiname und revokeObjectURL.
  - [ ] Sekundäraktion „Für DATEV exportieren“, darunter ein aufklappbarer Bereich „DATEV einrichten“ mit Ziel LODAS, Berater-/Mandantennummer, Personalnummer je Mitarbeiter, pro Kategorie explizit „Nicht exportieren“ oder Lohnart. Korrekturen fest mit Erklärung „Zeitkontokorrekturen werden nicht ausgezahlt“. Keine Beispielnummern als echte Defaults. Unvollständige Einstellungen speicherbar, Exportvorprüfung zeigt fehlende Angaben.
  - [ ] Dialog übernimmt explizite ausgewählte Mitarbeiter und gewählten Monat. Der erste UI-Umfang exportiert nur den ausgewählten Hauptmonat. Auswahl der tatsächlichen Mitarbeiter-Monatsstände im Dialog sichtbar bestätigen; niemals alle Mitarbeiter unbemerkt hinzufügen. Der Backendvertrag unterstützt mehrere explizite Monate mit ZIP; dafür sind Backendtests ausreichend, keine zusätzliche Monatsauswahl im Dialog nötig.
  - [ ] Vorprüfung sendet aktuelle Versionen und Konfigurationsversion. Fehler/Ausschlüsse mit Mitarbeiter, Monat, Kategorie und Stunden anzeigen. Download nur bei gültiger unveränderter Vorprüfung und expliziter Bestätigung der sichtbaren Ausschlüsse; jede Auswahl-/Konfigurationsänderung verwirft Freigabe. Keine automatische Neuvorprüfung mit unbemerkt neuen Werten nach 409.
  - [ ] Download über API als Blob, servergenerierten Dateinamen verwenden, ObjectURL anschließend freigeben. Meldung „Datei heruntergeladen – bitte im Steuerbüro importieren“, keine Importbestätigung behaupten. Laden/Fehler/Leere/Speichern unterscheiden und Fehler toasten.
  - [ ] Vitest und PC-Build; Browser-End-to-End mit Dummy-Daten: Filter → Alle-Auswahl → Teilerfolg → Verlauf/Kalender → Konfiguration → Vorprüfung → TXT-Download → zwischenzeitliche Wiederöffnung/409. Feste Viewports aus Design-Prüfskill, Tastatur und kein abgeschnittener Dialog/Seitenoverflow.

## Freigegebene Ergänzung: systemeigene Eingaben und Meldungen

Der [Zusatzplan mit Tasks 7–14](2026-09-09-systemeigene-eingaben.md) gehört zur gleichen freigegebenen Aufgabe und zum Featurebranch. Er erweitert den Abschluss um beide Frontends, eigene Picker/Toasts sowie deutsche Zahlenfelder. Die Ergänzung wird gemäß der gemeinsamen Ausführungsreihenfolge unten mit den Haupttasks verzahnt und vor Gesamtprüfung/PR vollständig abgeschlossen. Kein weiterer Designfreigabepunkt.

## Log

Abschlusszusammenfassungen werden nach den Abschnittsreviews ergänzt.

## Gemeinsame Ausführungsreihenfolge nach Scope-Erweiterung

Die Nutzervorgabe zu systemeigenen Eingaben wird mit den Featuretasks verflochten: Abschnitt 1 [1,3], Abschnitt 2 [2,4,7], Abschnitt 3 [5,8,9], Abschnitt 4 [6,10,11], Abschnitt 5 [12,13,14]. Tasknummern7–14 und Dateibesitz stehen im verlinkten Zusatzplan. Pro Abschnitt max3 disjunkte Tasks; alle erforderlichen Produzenten sind vorher geprüft. Task6 bleibt beim Frontend-Agenten von Task5. Backend-only Abschnitt1 hat keinen Designreview, weitere Abschnitte bekommen den separaten Designreview.
