# Kontext-Log: Kasse & Belege

Append-only. Absoluter Pfad (Haupt-Checkout, nicht Worktree):
`C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/ERP-System-fuer-Handwerksbetriebe/docs/superpowers/plans/2026-09-09-kasse-belege-log.md`
Lock-Protokoll: siehe `.claude/skills/loese-problem/references/kontext-log-format.md`.

Spec: `docs/superpowers/specs/2026-09-09-kasse-belege.md`
Brainstorming (Recherche + Design, freigegeben): `docs/superpowers/specs/2026-09-09-kasse-belege-brainstorming.md`
Feature-Branch: `feature/kasse-belege` (abgezweigt von `main` @ d0e76688, 09.09.2026)

## Umgebung (Orchestrator, 09.09.2026)

Zeit: 2026-09-09T19:10:00Z
Rolle: Orchestrator

- Werkzeuge im Haupt-Checkout: OpenJDK 23.0.2, Node v24.19.0, npm 11.17.0, Maven 3.9.10 (`./mvnw`, unter PowerShell `.\mvnw.cmd`).
- `node_modules` vorhanden: `react-pc-frontend` (306 Einträge), `react-zeiterfassung` (453 Einträge). **Kein `npm ci` in Worktrees** — NTFS-Junction auf das Haupt-`node_modules` (`mklink /J`), legt der Orchestrator an. Vor `git worktree remove` die Junction mit `rmdir` (ohne /s) lösen, sonst wird das echte `node_modules` gelöscht (siehe fallstricke.md).
- Worktree-Konvention: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/<kurzslug>-task-<N>`, Branch `<kurzslug>/task-<N>-<stichwort>` (nicht unter `feature/`).
- Skills im Worktree: `wt/<worktree-name>:handwerkerprogramm-design` (scoped) funktioniert, unscoped nicht. Für den Hook `ui-ux-pro-max` (ohne Namespace).
- `./graphify update .` nur im Haupt-Checkout, einmal am Ende — nicht in Worktrees, nicht je Agent.
- Shell-Werkzeug: Timeout-Parameter ausdrücklich auf 600000 ms setzen; Standard sind 120 s, danach rutscht ein Lauf still in den Hintergrund.
- Maven: mehrere Testklassen mit Komma trennen (`-Dtest=A,B`), nie `+`. Nur ein Maven-Prozess je Worktree.
- `src/main/resources/static/index.html` zeigt in jedem frischen Worktree einen Phantom-Diff (CRLF/LF) — kein Befund, nicht committen.
- Frontend-Build schreibt in das versionierte `src/main/resources/static/` — Artefakte vor dem Commit verwerfen (`git checkout -- src/main/resources/static && git clean -fdq src/main/resources/static`).
- MCP-Server `shadcn` und `magic` sind in dieser Session **nicht** verbunden; Komponenten aus dem bestehenden `components/ui/`-Bestand bauen (Vorbilder nennen), nicht neu erfinden.
- GitHub: kein MCP-Connector, `gh` nicht angemeldet. Issue/PR laufen über Token-Datei im Scratchpad (`references/github-ohne-mcp.md`), nur durch den Orchestrator.
- Upstream-Stand: `main` wurde vor dem Abzweigen per fast-forward auf origin gezogen (56 Commits, Thema Zeitkonto, Migrationen bis V371). Nächste freie Migration: **V372**. An Beleg-/Kasse-Dateien hat sich upstream nur `BelegService.findCaller` (3 Zeilen, `MitarbeiterArt.MENSCH`) geändert.

Baseline (Lint/Test/Build/Maven/E2E auf dem unveränderten Feature-Branch) folgt als eigener Block, sobald der Lauf durch ist.

## Baseline Backend (Orchestrator)

Zeit: 2026-09-09T19:05:00Z
Befehl: `.\mvnw.cmd -B test` auf `feature/kasse-belege` @ d0e76688, unverändert.
Ergebnis: **Tests run: 2747, Failures: 0, Errors: 4, Skipped: 6** (BUILD FAILURE, 171 s).

Vorbestehende Fehler (alle `CannotCreateTransaction: Could not open JPA EntityManager` — brauchen eine echte Datenbank, im Testprofil nicht vorhanden):
- `AuditChainRepairIntegrationTest.rebuildMachtEchteLokaleKetteIntakt`
- `AuditChainRepairIntegrationTest.appendToChainHashIstNachRoundtripReproduzierbar`
- `AuditHashRoundtripDiagnoseTest.getrimmterZeitstempelUeberlebtDbRoundtrip`
- `AuditHashRoundtripDiagnoseTest.rohNanosekundenUeberlebenDbRoundtripNicht`

**Abnahmeregel Backend:** grün = genau diese 4 Errors, 0 Failures. Ein 5. Error oder ein Failure ist neu und ein Befund. Diese vier nicht reparieren, nicht überspringen, nicht deaktivieren.

## Baseline Frontend Mobile (Orchestrator)

Zeit: 2026-09-09T20:15:00Z
`react-zeiterfassung` auf `feature/kasse-belege` @ f7974468, unverändert:
- `npm run lint`: exit 0 (keine Meldungen)
- `npm run test`: exit 0 — **11 Test-Dateien, 147 Tests, alle grün**
- `npm run build`: exit 0

**Abnahmeregel Mobile:** Lint 0 Meldungen, Vitest 147 + neue Tests grün, Build grün.

## Baseline Frontend PC (Orchestrator)

Zeit: 2026-09-09T20:25:00Z
`react-pc-frontend` auf `feature/kasse-belege` @ f7974468, unverändert — gemessen, **während sechs Coding-Agenten parallel liefen** (CPU-Last):
- `npm run lint`: exit 0 — **0 Errors, 4 Warnings** (vorbestehend)
- `npm run test`: exit 1 — **98 Test-Dateien (96 grün, 2 rot), 1172 Tests (1168 grün, 4 rot)**. Alle vier roten Fälle liefen in ~5 s in den Timeout und liegen in Dateien, die dieses Vorhaben nicht anfasst:
  - `src/pages/MitarbeiterEditor.task8.test.tsx` (3 Fälle: "zeigt die fehlende Einrichtung…", "verwirft eine verspätete Vorschau…", "behält die Arbeitszeit-Vorlage…")
  - `src/pages/ZeiterfassungZeitkontenTask10.test.tsx` (1 Fall: "verwirft eine verspätete Mehrfachvorschau…")
- `npm run build`: exit 0

**Abnahmeregel PC:** Lint 0 Errors (Warnings dürfen nicht zunehmen). Vitest: grün = höchstens diese 4 Timeout-Fälle in genau diesen zwei Dateien; fallen sie, prüft der Reviewer sie **einzeln ohne Last** nach (`npx vitest run src/pages/MitarbeiterEditor.task8.test.tsx src/pages/ZeiterfassungZeitkontenTask10.test.tsx`) — sind sie dann grün, war es Last, kein Befund. Jeder andere rote Fall ist neu. Build grün.
- **Playwright-Baseline (PC) fehlt noch** — der Lauf hing im ersten Anlauf 52 Minuten an einer npx-Rückfrage (falsches Arbeitsverzeichnis). Wird vom Orchestrator nach Abschluss der Coding-Agenten von Abschnitt 1 ohne CPU-Konkurrenz nachgeholt und hier ergänzt, **bevor** der Design-Reviewer startet.

## Abschnitt 1 — Task 2 (Coding-Agent)

Zeit: 2026-09-09T00:00:00Z
Branch: kasse/task-2-belegpdf
Commit(s): 7c8f70f4
Status: fertig

Was gemacht wurde:
- `BelegPdfService` neu angelegt (`erzeugeQuittung`, `erzeugeEigenbeleg`),
  OpenPDF, A4 hochkant, Briefkopf/Farbpalette als eigene private Kopie von
  `BelegeKasseExportPdfService` (kein Umbau am Vorbild).
- Records `ErzeugtesPdf`, `QuittungDaten`, `EigenbelegDaten` exakt wie im
  Plan-Interface. Constructor Injection von `FirmeninformationRepository`,
  `@Value("${upload.path:uploads}")`.
- Quittung: Titel "Quittung", Zeilen Betrag/MwSt/Datum/Von wem/Wofür/
  optional Konto/optional Zu Rechnung, Fußtext inkl. § 33 UStDV-Hinweis
  ab > 250 € brutto.
- Eigenbeleg: Titel "Ersatzbeleg (Eigenbeleg)", kein MwSt-Ausweis, keine
  Unterschriftszeile, fester Satz "Ohne Fremdbeleg gibt es keine
  Vorsteuer.".
- Beide: "Erstellt von <Vorname Nachname> am <dd.MM.yyyy HH:mm> Uhr" statt
  Unterschrift. SHA-256 wird über die fertige Datei berechnet (eigene
  Kopie von `BelegService.berechneDateiHash`) und nur im Record
  zurückgegeben, nicht ins PDF gedruckt (Zirkelschluss vermieden).
- Dateien unter `{upload.path}/belege/<UUID>_Quittung-<datum>.pdf` bzw.
  `..._Ersatzbeleg-<datum>.pdf`, MIME `application/pdf`.
- `BelegPdfServiceTest` (12 Tests, TDD: erst RED — Compile-Fehler mangels
  Klasse —, dann GREEN): Datei-Erzeugung (>1 kB, `%PDF`-Header), SHA-256-
  Konsistenz (Record vs. selbst berechnet), Verhalten ohne
  Firmeninformation, Briefkopf mit Firmendaten, Pflichtzeilen/-texte,
  bedingte Zeilen (Konto/Zu Rechnung), § 33 UStDV-Schwelle (250 €), sowie
  Eigenbeleg ohne MwSt/Unterschrift. Dummy-Daten: Ersteller
  "Max Mustermann", Gegenpartei "Musterbetrieb GmbH".
- Gate: `./mvnw -B test -Dtest=BelegPdfServiceTest` → Tests run: 12,
  Failures: 0, Errors: 0, BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- keine

## Abschnitt 1 — Task 17 (Coding-Agent)

Zeit: 2026-09-09T00:00:00Z
Branch: kasse/task-17-dokumentation
Commit(s): 19b4216c
Status: fertig

Was gemacht wurde:
- docs/GOBD_COMPLIANCE.md: Abschnitt 0.2 — Zeile "Barkasse mit TSE / DSFinV-K"
  durch die neue Zeile "Registrierkasse / POS mit TSE (§ 146a AO) — nicht
  abgebildet, wird auch nicht gebraucht" ersetzt, plus Abgrenzungsabsatz
  (offenes Ladenkassen-Modell, Verweis auf docs/KASSE_ANLEITUNG.md, das erst
  in Task 11 entsteht). Abschnitt 0.3: Punkt 2 auf "Dieses ERP liefert
  zusätzlich Kassenbuch, DATEV-Buchungsstapel und Belegbilder als
  Monatspaket" umgestellt, Schlussabsatz in Finanzbuchhaltung (Aussage
  bleibt) vs. Kasse (Aussage gilt nicht mehr) aufgeteilt. Gegenprobe
  `grep -n "TSE" docs/GOBD_COMPLIANCE.md` — alle vier Fundstellen tragen die
  neue Aussage.
- docs/KASSE_BUCHHALTUNG.md: Abschnitt 2 zu "Neue Buchung — sechs Kacheln"
  umgebaut (Tabelle Art→Kategorie→Quelle→Sachkonto→Beleg aus Task 3, Hinweis
  auf KassenbuchungService.buche und POST /api/buchhaltung/umbuchungen als
  Alias), bisherige Shortcuts als Unterabschnitt erhalten. Abschnitt 4 um
  ableitenKonten (Kontonummern aus KasseEinstellung, DATEV-Writer schreibt
  immer S) ergänzt. Abschnitt 5 zu "Was der Steuerberater bekommt" umbenannt
  (ZIP-Aufbau aus Task 8: fünf Bausteine, Vorprüfung, Begründung für
  fehlende Bank-/Kreditkartenbuchungen); Begriff "T-Konto" nur noch als
  Erklärung, warum er verschwunden ist. Abschnitt 6 (Datenmodell) um die
  V372-Spalten erweitert. Neuer Abschnitt 7 "Zahlungsart-Mapping" mit der
  vollständigen Tabelle aus Task 1 und Hinweis auf ZahlungsartMapper als
  einzige Quelle. Alter Abschnitt 7 (Frontend-Shortcuts) zu Abschnitt 8 auf
  die neue Dateiaufteilung components/kasse/* umgestellt; Abschnitte 8/9 zu
  9/10 durchnummeriert, docs/KASSE_ANLEITUNG.md in "Weiterführende Docs"
  aufgenommen.
- VerfahrensdokumentationService.erzeugeText(): Weg c) in Abschnitt 1
  umgeschrieben (Bareinnahme → Quittung-PDF, fehlender Fremdbeleg →
  Ersatzbeleg-PDF, beide mit Ersteller/Zeitpunkt/Fingerabdruck), Schlusssatz
  des Abschnitts entsprechend angepasst. Zwei neue Abschnitte "9. WARUM DIESE
  KASSE KEINE REGISTRIERKASSE IST" (offene Ladenkasse, § 146 AO, § 146a AO
  greift nur bei Aufzeichnungssystemen mit Kassenfunktion, Quittung nach
  § 33 UStDV / § 14 UStG ist kein Bon) und "10. WAS DER STEUERBERATER
  MONATLICH BEKOMMT" (die fünf ZIP-Bausteine, ein Satz je Baustein, Hinweis
  dass Bankumsätze bewusst fehlen) vor dem Schluss-Trenner ergänzt.
- T/service/VerfahrensdokumentationServiceTest.java neu angelegt: TDD
  (Test zuerst rot — 3 von 5 Fällen schlugen wie erwartet fehl, weil die
  neuen Abschnitte/Begriffe im Code noch fehlten — dann Service angepasst,
  Test grün). Deckt: beide neuen Überschriften, TSE-Satz + § 146a AO,
  "Quittung" und "Ersatzbeleg", Fallback "(Firmenname nicht gepflegt)" ohne
  Exception, Textlänge > 3000 Zeichen.
- Gate: `./mvnw -B test -Dtest=VerfahrensdokumentationServiceTest` —
  BUILD SUCCESS, Tests run: 5, Failures: 0, Errors: 0.

Bedenken / Abweichungen vom Plan:
- Task-17-Text (Zeile 979-980) nennt "neun neue beleg-Spalten" für V372.
  Task 1 (die maßgebliche Interface-Quelle, Zeilen 360-397) definiert dafür
  aber nur sieben: quelle, gegenpartei, ausgangsrechnung_id, ki_zahlungsart,
  ki_belegdatum, ki_betrag_brutto, ki_kostenkonto_hinweis. Die fünf neuen
  kasse_einstellung-Spalten stimmen dagegen exakt. In KASSE_BUCHHALTUNG.md
  habe ich die sieben tatsächlichen Spalten dokumentiert (Task 1 als
  fachliche Quelle vorgegeben) statt die Zahl "neun" aus dem Task-17-Text zu
  übernehmen — bitte im Abschnitts-Review gegenchecken, falls Task 1 zwischen
  Planerstellung und Umsetzung noch weitere Spalten bekommen haben sollte.
- docs/GOBD_COMPLIANCE.md, Abschnitt 0 (Zeile 21, außerhalb 0.2/0.3): Der
  Einleitungssatz "Dieses ERP ist ... kein Komplettsystem mit eigener
  Finanzbuchhaltung oder Kasse." widerspricht nach diesem Vorhaben der
  Realität (das System hat jetzt eine eigene Kasse/ein Kassenbuch). Nicht
  angefasst, weil außerhalb der explizit genannten Abschnitte 0.2/0.3 —
  zur Kenntnis für einen möglichen Folge-Task.
- docs/KASSE_ANLEITUNG.md existiert noch nicht (entsteht laut Plan in
  Task 11); beide Verweise darauf sind bewusst vorausgesetzte Links auf eine
  Datei, die erst in Abschnitt 2 entsteht.

## Abschnitt 1 — Task 16 (Coding-Agent)

Zeit: 2026-09-09T00:00:00Z
Branch: kasse/task-16-handy-polling
Commit(s): a495049c
Status: fertig

Was gemacht wurde:
- `BelegScannerPage.tsx`: neuer `useEffect` pollt alle 3s per `reloadServerBelege()`,
  solange ein eigener Beleg `kiAnalyseStatus` PENDING/LAEUFT/RUNNING hat (Neuplanung
  ergibt sich daraus, dass `reloadServerBelege` eine neue `serverBelege`-Referenz setzt
  und der Effekt danach neu entscheidet — sind alle fertig, kein neuer Timer). Vorbild:
  Detail-Poller in `BelegeKasseEditor.tsx` (BelegDetailModal), cancelled-Flag + clearTimeout.
- `ServerBeleg` um `betragBrutto`, `belegDatum`, `kiVorgeschlagenerLieferant` erweitert.
  Gemeinsame Helper-Funktion `kiIstOffen()` ersetzt den bisherigen Tippfehler-Check auf
  nur `'RUNNING'` in `ServerBelegRow` — akzeptiert jetzt `PENDING`/`LAEUFT`/`RUNNING`,
  Kommentar im Code haelt den historischen Fehler fest.
- Zeile zeigt bei `DONE` + Betrag `<Betrag> € · <Datum>` statt "Hochgeladen"
  (`Intl.NumberFormat('de-DE')` fuer den Betrag, `Date(jahr,monat-1,tag).toLocaleDateString('de-DE')`
  fuers Datum — Komponenten aus dem ISO-String selbst gebaut statt geparst, damit kein
  Zeitzonen-Off-by-one durch UTC-Interpretation entsteht).
- Abweichender Lieferant: `KI hat "<Name>" gelesen – Am PC prüfen` in `text-xs text-amber-700`
  unter der Lieferantenzeile, wenn `kiVorgeschlagenerLieferant` gesetzt und ungleich
  `lieferantName` ist. Kein Bearbeiten am Handy (Nicht-Ziel eingehalten).
- Neue Spec `e2e/beleg-scanner-polling.spec.ts` (Vorbild `zeitkonto-status.spec.ts`):
  1./2. Abruf PENDING, ab 3. Abruf DONE mit Betrag/Datum/abweichendem Lieferanten. Prueft
  automatischen Wechsel ohne Zutun, den Hinweistext, und dass nach DONE keine weiteren
  Abrufe mehr kommen (Zaehler ueber zwei volle Poll-Intervalle stabil). TDD eingehalten:
  Spec zuerst rot (fehlende Anzeige), dann Implementierung, dann gruen.
- Gates: `npm run lint` gruen, `npm run build` gruen (Build-Output danach verworfen),
  `E2E_PORT=5261 npx playwright test e2e/beleg-scanner-polling.spec.ts` gruen (1 passed).
- `grep -rn "teal-" react-pc-frontend/src react-zeiterfassung/src` leer fuer meine Datei
  (drei bestehende Treffer in `Urlaubsantraege.tsx`/`phasen.ts` sind nicht mein Task/nicht
  angefasst).

Bedenken / Abweichungen vom Plan:
- `react-zeiterfassung/node_modules` enthielt `@playwright/test` nicht, obwohl
  `package.json`/`package-lock.json` es korrekt fuehren (im Haupt-Checkout ebenso fehlend —
  kein Worktree-spezifisches Problem). `npm install --no-save` im Worktree war noetig, um
  das Gate ueberhaupt fahren zu koennen. Dabei hat npm die node_modules-**Junction** dieses
  Worktrees durch ein echtes, eigenstaendiges `node_modules`-Verzeichnis ersetzt (npm kann
  offenbar nicht in einen Junction-Root hinein installieren, ohne ihn zu ersetzen) — das
  Haupt-Checkout-`node_modules` selbst ist nachweislich unberuehrt und weiterhin von den
  Nachbar-Worktrees nutzbar, nur dieses eine Worktree hat jetzt eine eigene, groessere Kopie
  statt der Junction. Zusaetzlich per `npx playwright install chromium` die Browser-Binaries
  nachgezogen (fehlten ebenfalls). Wirkt sich auf keine andere Datei aus, nur relevant falls
  dieses Worktree spaeter aufgeraeumt wird (loescht dann eine echte Kopie statt nur einen
  Link) oder falls der Review-Agent das Gate in einem eigenen, frischen Worktree nachfahren
  will und dort auf dieselbe fehlende Abhaengigkeit stoesst.
- `react-zeiterfassung/.gitignore` fuehrt anders als `react-pc-frontend/.gitignore` kein
  `/test-results/` — nicht behoben (Datei steht nicht unter meinen Files), lokal entstandenes
  `test-results/` manuell geloescht statt committet.

## Abschnitt 1 — Task 1 (Coding-Agent)

Zeit: 2026-09-09T00:00:00Z
Branch: kasse/task-1-datenmodell
Commit(s): 3b12a989
Status: fertig

Was gemacht wurde:
- Migration `V372__kasse_buchungen_und_export.sql`: idempotent nach V351-Muster
  (INFORMATION_SCHEMA + PREPARE); `beleg.quelle` als native ENUM-Spalte
  (V309-Muster); `beleg.gegenpartei`, `ausgangsrechnung_id` + FK
  `fk_beleg_ausgangsrechnung` auf `projekt_geschaeftsdokument(id)` (separat per
  TABLE_CONSTRAINTS abgesichert, Muster V305); vier `ki_*`-Spalten;
  `kasse_einstellung` um 5 DATEV-Felder erweitert, `wirtschaftsjahr_beginn_monat`
  bewusst INT statt TINYINT (Kommentar in der Migration); Stammdaten-Seed
  `Online-Zahlung` per INSERT IGNORE; Backfill `zahlungsart` per CASE (idempotent
  über ELSE-Zweig); Backfill `quelle='TRANSFER'` für `ist_umbuchung=TRUE`.
- `BelegQuelle`-Enum (SCAN/QUITTUNG/EIGENBELEG/TRANSFER) neu angelegt.
- `ZahlungsartMapper` (rein statisch, kein Spring-Bean) mit `zuStammdaten`,
  `zuKategorie`, `giltAlsBezahlt` nach der verbindlichen Tabelle aus
  Entscheidung 1 der Spec. `GeminiDokumentAnalyseService.normalizeZahlungsart`
  nicht angefasst.
- Neue Felder an `Beleg` (quelle, gegenpartei, ausgangsrechnungId,
  kiZahlungsart, kiBelegdatum, kiBetragBrutto, kiKostenkontoHinweis) und
  `KasseEinstellung` (datevBeraternummer, datevMandantennummer,
  wirtschaftsjahrBeginnMonat, kassenkontoNummer, bankkontoNummer).
- `BelegDto`: neue Response-Felder, `VorschlagDto` (neu), `UpdateRequest`
  um `zahlungsstatus`/`bezahltAm` erweitert — nur Deklaration, Füllen macht Task 5.
- `KasseShortcutController`: `EinstellungRequest`/`EinstellungResponse` um die
  fünf DATEV-Felder erweitert (hinten angehängt), `toEinstellungDto` und
  `updateEinstellung` nachgezogen, Leer-Fallback in `getEinstellung` auf die
  neue Record-Länge angepasst (Defaults wirtschaftsjahrBeginnMonat=1,
  kassenkontoNummer="1000", bankkontoNummer="1200"). Validierung: Monat 1-12,
  Beraternummer ≤7, Mandantennummer ≤5, Kontonummern nur Ziffern ≤8 —
  `IllegalArgumentException` mit einem Satz in Handwerker-Sprache.
- Tests: `ZahlungsartMapperTest` (parametrisiert über die volle Tabelle, plus
  null/leer/unbekannt, Idempotenz, Bar-Richtung — 16 Tests) und
  `KasseBelegeMigrationTest` nach ZeitkontoMigrationTest-Muster (H2 im
  MySQL-Modus, ALTER-/INSERT-/UPDATE-Anweisungen per Regex aus V372 gezogen,
  Backfill zweimal ausgeführt für Idempotenz-Nachweis, keine
  `contains("spaltenname")`-Prüfung). Gegenprobe durchgeführt: eine
  ALTER-Zeile testweise entfernt, Test wurde rot, danach wiederhergestellt
  (verifiziert per diff — identisch zum Original).
- Gate `./mvnw -B test -Dtest=KasseBelegeMigrationTest,ZahlungsartMapperTest,BelegServiceTest,BelegServiceKasseValidationTest,KasseShortcutControllerTest`:
  70 Tests, 0 Failures, 0 Errors, BUILD SUCCESS.

Bedenken / Abweichungen vom Plan:
- Task-Text nennt "neun neue Spalten", die Interfaces-Sektion listet aber
  7 Felder an `Beleg` + 5 Felder an `KasseEinstellung` = 12 neue Spalten (plus
  1 FK-Constraint-ALTER ohne eigene Spalte = 13 extrahierte ALTER-Anweisungen
  im Migrationstest). Nach Interfaces-Sektion (maßgeblich) umgesetzt, nicht
  nach der Neun-Angabe im Fließtext.
- `KasseShortcutControllerTest.java` steht nicht in der Files-Liste von Task 1
  und wurde entsprechend nicht angefasst. Die neue Validierungslogik in
  `updateEinstellung` (Monat/Beraternummer/Mandantennummer/Kontonummern) hat
  dadurch keine dedizierte rote/grüne Testabdeckung aus diesem Task — nur die
  bestehenden Regressionstests (12 Tests, weiterhin grün) laufen dagegen.
  Empfehlung: in einem späteren Task oder im Abschnitts-Review gezielt
  Testfälle für diese Validierung ergänzen.
- `wirtschaftsjahr_beginn_monat` als INT statt TINYINT ist eine bewusste,
  im Task-Block selbst schon vorgegebene Abweichung von der Spec (dort
  TINYINT) — Begründung als SQL-Kommentar in der Migration hinterlegt, keine
  eigene Abweichung dieses Agenten.

## Abschnitt 1 — Task 9 (Coding-Agent)

Zeit: 2026-09-09T20:30:27Z
Branch: kasse/task-9-refactoring
Commit(s): d3804430
Status: fertig

Was gemacht wurde:
- BelegeKasseEditor.tsx (2138 Zeilen) in Komponenten-Dateien aufgeteilt, reine Verschiebung ohne Verhaltensaenderung (gleiche Texte/Klassen/Reihenfolge/fetch-Aufrufe). Nachher: 672 Zeilen.
- src/types.ts: Block "===== Belege & Kasse =====" mit BelegStatus, SachkontoTyp, Sachkonto, Zahlungsart, BelegKategorie, KiStatus, AufteilungsModus, BelegPosition, Beleg (+ neue optionale Felder aus der Spec), KassenBewegung, Kassenbuch, AuswertungZeile, Auswertung, BelegVorschlag, KasseEinstellung. KostenstellenSplit aus KostenstellenSplitsEditor.tsx re-exportiert (kein Kopieren).
- components/kasse/belegFormat.ts (neu): KATEGORIE_LABELS, KATEGORIE_FARBE, SACHKONTO_TYP_LABEL, KI_LABEL, inputCls, gesperrtCls, modalInputCls, formatEuro, isoDatum, formatDate, formatDateTime, buildSachkontoOptions, buildZahlungsartOptions, sicherheitsText.
- components/kasse/BelegDetailModal.tsx (neu, 788 Zeilen): heutiger BelegDetailModal samt AufteilungsSektion, Field (lokal), BelegPreview.
- components/kasse/VorschlagsChip.tsx (neu): heutige KiVorschlagKarte, Props unveraendert.
- components/kasse/KassenbuchJournal.tsx (neu): heutiger KassenbuchView (umbenannt) + TKontoZeile + KassenbuchFilter + KpiTile (KpiTile exportiert, da AuswertungView in BelegeKasseEditor.tsx es weiter braucht -- Import statt Duplikat).
- components/kasse/KassenbuchTab.tsx (neu): der bisherige activeTab==='kasse'-Ast, rendert KasseShortcuts + KassenbuchJournal per Props.
- components/kasse/NeueBuchungDialog.tsx (neu): BankAbhebungModal, EinfacheKasseModal, LohnZahlungModal samt ModalShell/FieldRow/ModalFooter (exportiert, da KasseEinstellungenDialog.tsx sie mitnutzt) und SaldoInfo-Typ (exportiert, da KasseShortcuts.tsx ihn weiter braucht).
- components/kasse/KasseEinstellungenDialog.tsx (neu): heutiges KasseSettingsModal (umbenannt) + defaultEinstellung.
- components/kasse/KasseShortcuts.tsx: lokale Typen/Helfer entfernt, importiert jetzt aus types.ts/belegFormat.ts/NeueBuchungDialog.tsx/KasseEinstellungenDialog.tsx.
- components/kasse/belegFormat.test.ts (neu, Vitest): formatEuro, isoDatum (Jahreswechsel), buildSachkontoOptions (Gruppenreihenfolge + Sortierung), buildZahlungsartOptions (Fremdwert-Anhang), sicherheitsText (Schwellen 0.95/0.70/0.30) -- 19 Tests, alle gruen.
- e2e/kasse-refactoring.spec.ts (neu): stubbt auth/me, notifications/summary, buchhaltung/belege, sachkonten, zahlungsarten, kassenbuch, kasse/saldo, kasse/einstellung. Prueft vier Tabs, Kassenbuch-Journal (Eingang/Ausgang/Saldo), die vier Shortcut-Knoepfe und den Pruefen-Dialog ("Beleg pruefen & validieren") per Klick auf eine Kassenbuch-Zeile. Gruen auf allen drei Bildschirmgroessen (E2E_PORT=5201).
- Alle vier Gates gruen: vitest (19/19), lint (0 Fehler, nur vorbestehende/unabhaengige Warnings), build (tsc+vite, Artefakte danach verworfen), Playwright (3/3 Projekte).
- grep nach "teal-" in react-pc-frontend/src: nur vorbestehende, von diesem Task nicht beruehrte Treffer (langzeitkrankmeldung/phasen.ts, Urlaubsantraege.tsx) -- in den Kasse-Dateien leer.

Bedenken / Abweichungen vom Plan:
- Plan listet KpiTile als Teil von KassenbuchJournal ("heutiger KassenbuchView + TKontoZeile + KassenbuchFilter + KpiTile"), tatsaechlich wird KpiTile im Bestand ausschliesslich von AuswertungView verwendet, die laut Plan in BelegeKasseEditor.tsx bleibt. Aufgeloest durch Export aus KassenbuchJournal.tsx und Re-Import in BelegeKasseEditor.tsx -- eine Definition, kein Duplikat, beide Plan-Vorgaben ("KpiTile gehoert in die Journal-Datei" und "AuswertungView bleibt in der Seite") damit gleichzeitig erfuellt.
- "Field" (kleine Label+Children-Wrapper-Komponente) wird in drei Dateien dupliziert (BelegDetailModal.tsx, KassenbuchJournal.tsx fuer KassenbuchFilter, BelegeKasseEditor.tsx fuer MonatsExportModal/AuswertungView), statt einmal zentral zu liegen -- der Plan nennt "Field" nur bei BelegDetailModal, listet es aber nicht in belegFormat.ts' Interface-Vertrag. Folgt damit dem bestehenden Codebase-Muster (KasseShortcuts.tsx hatte schon vorher ein eigenes FieldRow neben Field), keine Verhaltensaenderung.
- Playwright-Spec: fuer den Zustand "Beleg-Detail-Modal offen" wird designPruefung() nicht als Ganzes aufgerufen (Vorbild: rahmen-detailseite.spec.ts), sondern nur Screenshot + keinHorizontalerUeberlauf + keinTextLaeuftUeber + keinTextGekuerzt + Sichtbarkeit der Primaeraktion. Grund: BelegDetailModal.tsx (vorbestehend, wortgleich verschoben) traegt auf seinem Wrapper kein role="dialog". keineUeberschneidungen() blendet den Hintergrund eines offenen Dialogs nur aus, wenn sie dieses Attribut findet -- ohne es meldet die Pruefung Ueberschneidungen zwischen Hintergrund-Elementen (Kassenbuch-Filterfelder, Shortcut-Knoepfe -- durch bg-black/50 fuer den Nutzer unsichtbar) und Modal-Inhalten, die niemand sieht. Verifiziert: mit vollem designPruefung() schlagen alle drei Bildschirmgroessen konsistent an genau dieser Stelle fehl, an keiner anderen. Das ist ein vorbestehender Zustand von BelegDetailModal.tsx (kein role="dialog" schon vor Task 9) -- Beheben wuerde die Modal-JSX aendern, was der Task ausdruecklich verbietet ("JSX ... bleiben zeichengleich"). Empfehlung fuer einen spaeteren Task: role="dialog" + aria-modal="true" auf den Wrapper von BelegDetailModal.tsx ergaenzen (analog zum Dialog-Baustein in KassenbuchAbschlussLeiste.tsx).

## Baseline Playwright PC (Orchestrator)

Zeit: 2026-09-09T20:39:00Z
`react-pc-frontend`, `npx playwright test` (alle Specs, drei Bildschirmgrößen) auf `feature/kasse-belege` @ f7974468, ohne CPU-Konkurrenz außer einem einzelnen Coding-Agenten:
- **435 passed, 0 failed, 0 flaky** (7,7 min), exit 0.

**Abnahmeregel Playwright PC:** grün = alle bestehenden 435 plus die neuen Specs. Jede rote Spec ist erst ein Befund, wenn sie einen Nachlauf ohne Konkurrenz (`--workers=1`, keine parallele Maven-Suite) überlebt.

## Abschnitt 1 — Task 10 (Coding-Agent)

Zeit: 2026-09-09T22:55:00Z
Branch: kasse/task-10-select-custom
Commit(s): 0b8d6ba8, 5d696f2b
Status: fertig

Was gemacht wurde:
- `select-custom.tsx` repariert: Breite waechst mit dem Inhalt bis
  `min(90vw, 480px)` (`width: max-content`, `minWidth`/`maxWidth` aus neuem
  `positioniere()`-`useCallback`), Optionen brechen um (`whitespace-normal
  break-words` + `min-w-0`) statt abgeschnitten zu werden, `title` je Option.
  Position wird bei offenem Dropdown per `scroll`(capture)/`resize`-Listener
  neu berechnet. Hochklappen (`nachOben`), wenn unten kein Platz fuer die
  gemessene (auf 240px gedeckelte) Panel-Hoehe ist, sonst Hoehe auf den
  verbleibenden Platz begrenzt; links/rechts gegen den Viewport gedeckelt.
  Optionale Gruppen-Ueberschriften (`role="presentation"`, ungruppierte
  Optionen ohne Ueberschrift ganz oben, Gruppen in Reihenfolge des ersten
  Auftretens). Escape schliesst, Enter/Space auf dem Ausloeser oeffnet
  (Ausloeser jetzt `tabIndex`/`role="combobox"`, vorher gar nicht
  tastaturbedienbar). Props additiv (`gruppe?` optional), keine bestehende
  Aufrufstelle angefasst.
- `select-custom.test.tsx`: neue Tests fuer Gruppierung (zwei Ueberschriften,
  richtige Reihenfolge; ungruppiert oben), `title`-Attribut, Escape-Schliessen.
  13/13 gruen.
- `dropdown-breite.spec.ts` (neu): zwei Tests. (1) Konto-Dropdown mit langem
  Label + 120-Zeichen-Wort ohne Trennstellen — keine Option laeuft ueber
  (`scrollWidth<=clientWidth`), Panel vollstaendig im Viewport, an allen drei
  Projektgroessen (1440x900/1536x960/1920x1080). (2) Dropdown mit wenigen
  Optionen klappt bei verkuerztem Viewport (380px) am unteren Rand nach oben
  auf (`rahmen.y+rahmen.height <= triggerRahmen.y`), an allen drei Groessen
  gruen.
- **Gegenprobe (Pflicht) durchgefuehrt:** `min-w-0` und `whitespace-normal
  break-words` je einzeln aus dem Options-Span entfernt, Spec wurde beide
  Male auf `scrollWidth<=clientWidth` rot (Beweis: Wort lief mit 830px
  Scroll-Breite gegen 470px Client-Breite ueber), danach wiederhergestellt
  und erneut gruen bestaetigt.
- Gates: `npx vitest run src/components/ui/select-custom.test.tsx` 13/13
  gruen. `npm run lint` 0 Errors (nur vorbestehende Warnings in
  AbteilungBerechtigungenEditor.tsx/BelegeKasseEditor.tsx/
  ZeiterfassungSteuerberater.tsx, keine meiner Dateien). `npm run build`
  gruen, Build-Output danach verworfen. `grep -rn "teal-" react-pc-frontend/src`
  liefert nur vorbestehende, nicht von mir beruehrte Treffer
  (langzeitkrankmeldung/phasen.ts, PhasenZeitleiste.test.tsx,
  Urlaubsantraege.tsx) — in meinen drei Task-Dateien kein Treffer.
  `E2E_PORT=5202 npx playwright test e2e/dropdown-breite.spec.ts`: 3 von 6
  gruen (beide Hochklapp-Faelle an allen drei Groessen), 3 rot — siehe
  Bedenken, Ursache ausserhalb meiner Dateien.

Bedenken / Abweichungen vom Plan:
- **`min-w-0` wirkt im Plan-Wortlaut an der falschen Stelle.** Der Plan sagt
  "dem Zeilen-<div> zusaetzlich min-w-0 geben" (dem `role="option"`-Div).
  Per Mutationstest empirisch geprueft: `min-w-0` NUR auf diesem Zeilen-Div
  ist wirkungslos, weil dieses Div kein Flex-Item ist (sein Elternteil ist
  kein Flex-Container) — die `min-width:auto`-Falle trifft den
  Options-**Span** (Flex-Kind der Zeile selbst), nicht die Zeile. Ich habe
  `min-w-0` zusaetzlich auf den Span gesetzt (Zeile bleibt es aus Bestand
  ebenfalls, schadet nicht) und per Gegenprobe bestaetigt, dass jetzt beide
  Klassen (min-w-0 auf dem Span + whitespace-normal break-words) einzeln
  notwendig sind. Ohne diese Korrektur waere die im Plan beschriebene
  Rezeptur eine Attrappe gewesen (siehe kriterien.md-Warnung genau dazu).
- **`spacelosesWort(48, 'Konto')` reicht nicht, um die min-w-0-Gegenprobe
  ueberhaupt zu pruefen.** Bei 48 Zeichen (~340px bei text-sm) bleibt das
  Wort unter der festen 480px-Panel-Deckelung — der Browser muss dann nie
  unter die natuerliche Wortbreite schrumpfen, wodurch der Unterschied
  zwischen "min-w-0 vorhanden" und "min-w-0 entfernt" gar nicht zum Tragen
  kommt (empirisch verifiziert: mit 48 Zeichen blieb die Spec beim Entfernen
  von min-w-0 gruen). Ich habe die Laenge auf 120 Zeichen erhoeht (~830px,
  sicher ueber der Deckelung) — damit wird die Gegenprobe erst aussagekraeftig.
- **Missing `role="dialog"` in BelegeKasseEditor.tsx faelscht
  `keineUeberschneidungen`/`designPruefung` (out of scope, nicht meine
  Datei).** `e2e/hilfen/design.ts:405` filtert Hintergrund-Elemente nur
  heraus, wenn `document.querySelector('[role="dialog"]')` etwas findet.
  Beide Modal-Wrapper in BelegeKasseEditor.tsx (Zeile 672 und 1440, `<div
  className="fixed inset-0 bg-black/50 z-50 ...">`) haben kein
  `role="dialog"` — dadurch vergleicht der Check Hintergrund-Buttons/-Inputs
  (durch das Modal optisch verdeckt) gegen Modal-Inhalte und meldet
  Ueberschneidungen, die visuell gar nicht existieren (siehe Screenshot
  `dropdown-offen--pc-14zoll.png`: das Dropdown selbst sitzt sauber, alle
  gemeldeten "Ueberschneidungen" betreffen Elemente HINTER dem abgedunkelten
  Hintergrund). Dadurch ist `designPruefung(page, info, 'dropdown-offen')`
  in Test 1 an allen drei Bildschirmgroessen rot — meine eigenen
  Zusicherungen davor (kein Options-Ueberlauf, Panel im Viewport) sind an
  derselben Stelle nachweislich gruen durchgelaufen (Fehler tritt erst BEI
  designPruefung auf, nicht vorher). Empfehlung fuer wer BelegeKasseEditor.tsx
  naechstes anfasst (parallele Task-Bearbeitung oder Review): `role="dialog"`
  auf beide `fixed inset-0 bg-black/50`-Wrapper (Zeile 672, 1440) ergaenzen —
  einzeilige, risikofreie Aenderung, danach sollte dropdown-breite.spec.ts
  vollstaendig gruen laufen, ohne dass ich etwas an meinen Dateien aendern
  muesste.
- **`@testing-library/jest-dom` registriert seine Matcher in dieser
  Umgebung nachweislich nicht** (`toBeInTheDocument`/`toHaveClass`/
  `toHaveAttribute`/`toBeDisabled` -> "Invalid Chai property"), reproduziert
  auch an einer voellig unveraenderten Datei (`button.test.tsx`, 2/10 Tests
  rot, stabil bei mehrfachem Lauf, keine node_modules-Duplikate gefunden).
  Betrifft `vitest`^4.0.18 + `@testing-library/jest-dom`^6.9.1 global,
  nicht nur select-custom. `select-custom.test.tsx` umgeht das, indem alle
  Assertions auf native DOM-Eigenschaften bzw. eingebaute vitest-Matcher
  umgestellt sind (`toBeTruthy`/`el.getAttribute(...)`/`toBeNull` statt
  jest-dom-Matcher) — inhaltlich gleichwertig, nur ohne die kaputte
  Matcher-Registrierung. node_modules ist projektweit geteilt (Junction) —
  ich habe nichts daran repariert, das ist ausserhalb meiner Dateien und
  betrifft vermutlich jeden Frontend-Task in dieser Pipeline.

## Abschnitt 1 — Task 9, Nachbesserung (Coding-Agent)

Zeit: 2026-09-09T20:56:36Z
Branch: kasse/task-9-refactoring
Commit(s): b99f6762
Status: fertig

Was gemacht wurde:
- Auftrag des Orchestrators: role="dialog"/aria-modal="true" auf den Modal-Panels der eigenen Task-9-Dateien ergaenzen -- deckt sich mit der von mir schon im ersten Anlauf vermerkten Bedenken-Notiz und mit der unabhaengigen Beobachtung aus Task 10 (dropdown-breite.spec.ts wurde im gemergten Stand von genau derselben Ursache rot).
- Already up to date. (Fast-Forward, keine Konflikte) -- Branch enthaelt jetzt alle sechs Tasks von Abschnitt 1.
- role="dialog", aria-modal="true", aria-labelledby (per id auf die jeweilige Titelueberschrift verknuepft) ergaenzt auf: BelegDetailModal.tsx (Panel-Div), NeueBuchungDialog.tsx (ModalShell -- deckt damit alle vier Buchungsmodale UND KasseEinstellungenDialog.tsx ab, da beide ModalShell nutzen), MonatsExportModal in BelegeKasseEditor.tsx. Vorbild KassenbuchAbschlussLeiste.tsx:527, Attribute aufs Panel (weisse Karte), nicht aufs Overlay -- wie im Vorbild. Sonst nichts geaendert.
- e2e/kasse-refactoring.spec.ts: die Teil-Pruefung fuer den Zustand "Beleg-Detail-Modal offen" (aus dem ersten Anlauf, ohne keineUeberschneidungen) durch die volle designPruefung() ersetzt -- jetzt gruen mit dem Attribut.
- Gates: vitest (19/19 gruen), lint (0 Fehler, nur die schon vorher bekannten, unabhaengigen Warnings), build (tsc+vite gruen, Artefakte danach verworfen), Playwright E2E_PORT=5201 fuer kasse-refactoring.spec.ts (3/3 Bildschirmgroessen gruen) UND dropdown-breite.spec.ts (6/6 gruen).

Bedenken / Abweichungen vom Plan:
- keine

## Abschnitt 1 (Review-Agent, Code/Korrektheit)

Zeit: 2026-09-09T21:35:00Z
Branch: feature/kasse-belege
Commit(s): 370e761d (gemergt: 9fc3c935, 79210643, 31e26025, 9b367972, 22673c06, 79515b36, 06706eb8)
Status: fertig
Ampel: 🔴

Was geprueft wurde:
- Volle Suiten selbst gefahren. Backend `./mvnw -B test`: 2781 Tests, 0 Failures,
  genau die 4 Baseline-Errors (AuditChainRepair ×2, AuditHashRoundtrip ×2,
  CannotCreateTransaction) — +34 Tests gegenueber Baseline, keine neuen Fehler.
  PC: lint 0 Errors / 4 Warnings (Baseline), test 1195 Tests, nur die bekannten
  2 Timeouts in MitarbeiterEditor.task8.test.tsx — einzeln nachgefahren, gruen;
  build gruen. Mobile: lint 0, 147 Tests gruen, build gruen. Build-Artefakte
  unter src/main/resources/static verworfen, `git status` sauber.
- Mutationsproben (alle rot, danach restlos zurueckgenommen):
  V372 `ki_belegdatum`-ALTER entfernt -> KasseBelegeMigrationTest rot; Typ auf
  VARCHAR(10) geaendert -> rot (assertSpalte prueft echten Spaltentyp, nicht nur
  den Statement-Count); Backfill AMAZON_PAY -> 'PayPal' -> rot (assertZahlungsart);
  ZahlungsartMapper PAYPAL -> "Bar" -> ZahlungsartMapperTest rot;
  select-custom Gruppenreihenfolge gebrochen -> select-custom.test.tsx rot.
- Task 1: V372 gegen Plan und Spec durchgegangen (idempotent nach V351-Muster,
  native ENUM fuer quelle, INT fuer den Monat mit Begruendungskommentar, FK ueber
  TABLE_CONSTRAINTS abgesichert, Backfill-CASE vollstaendig, Online-Zahlung-Seed
  per INSERT IGNORE). Entity-Felder decken sich mit den Spalten (Typ, Laenge,
  Nullability, Defaults); ZahlungsartMapper stimmt Zeile fuer Zeile mit der
  verbindlichen Tabelle (Entscheidung 1) inkl. VORAUSKASSE = "gilt als bezahlt".
- Task 2: BelegPdfService — kein Nutzereingabe-Anteil im Dateinamen (UUID +
  konstanter Titel + Datum), Gegenpartei/Beschreibung nur im PDF-Inhalt, kein
  Path-Traversal (Logo-Pfad zusaetzlich per normalize+startsWith geprueft),
  SHA-256 blockweise korrekt, Eigenbeleg ohne MwSt-Ausweis (per Test belegt),
  keine Klarnamen im Log.
- Task 9: Verhalten unveraendert belegt — alle fetch-URLs identisch in Anzahl und
  Wert, alle String-Literale und Template-Literale identisch bis auf die drei
  neuen aria-labelledby-IDs; KpiTile exportiert, KostenstellenSplit in types.ts
  re-exportiert statt kopiert, modalInputCls bewusst getrennt von inputCls.
  Nachbesserung: role/aria-modal/aria-labelledby nur an den drei Panels.
- Task 10: kein Aufrufer musste geaendert werden (nur select-custom.tsx/.test.tsx
  + Spec im Task-Diff), Scroll/Resize-Listener sauber aufgeraeumt,
  Escape/Enter/Space vorhanden, Gruppenreihenfolge wie dokumentiert, title je
  Option. Die Behauptung "jest-dom-Matcher projektweit kaputt" stimmt nicht:
  src/components/ui/button.test.tsx nutzt toBeInTheDocument/toBeDisabled und
  laeuft gruen (setupTests.ts importiert @testing-library/jest-dom/vitest).
- Task 16: Polling stoppt, sobald kein Beleg mehr PENDING/LAEUFT ist;
  reloadServerBelege ist useCallback([token]) und damit stabil, Cleanup raeumt
  Timer und cancelled-Flag auf, keine Doppel-Timer. Keine neue Abhaengigkeit
  (Intl.NumberFormat). RUNNING/LAEUFT ueber kiIstOffen vereinheitlicht.
- Task 17: Rechtsaussagen zu § 146a AO, § 146 AO, § 33 UStDV und Eigenbeleg ohne
  Vorsteuer sind sachlich richtig.
- DSGVO/Secrets: nur Dummy-Daten (Max Mustermann, Musterbetrieb GmbH), keine
  Secrets im Abschnitts-Diff, keine Klarnamen in Logs.

Blockierende Befunde (🔴):
1. KasseShortcutController.updateEinstellung:206-209 — die vier neuen
   String-Felder werden bedingungslos aus dem Request uebernommen. Der heute
   ausgelieferte KasseEinstellungenDialog.tsx:47-54 sendet sie nicht mit, also
   setzt jedes Speichern der Kassen-Einstellungen datev_beraternummer,
   datev_mandantennummer, kassenkonto_nummer und bankkonto_nummer auf NULL —
   die von V372 gesetzten Defaults '1000'/'1200' gehen dabei verloren.
   wirtschaftsjahrBeginnMonat ist bereits korrekt per null-Check geschuetzt.
   Empfehlung: dieselbe `if (req.x() != null)`-Regel auf die vier String-Felder
   anwenden (oder den Dialog die Felder mitschicken lassen).
2. VerfahrensdokumentationService.java:79-82 — "Jede Zeile im Kassenbuch hat
   eine Datei ... Das ist auf Datenbankebene erzwungen; eine belegfreie Buchung
   kann in diesem System nicht entstehen." Der DB-CHECK
   `chk_beleg_datei_oder_umbuchung` (V304:71) nimmt `ist_umbuchung = 1`
   ausdruecklich aus, und V372 ist laut Plan (Zeile 54) die einzige neue
   Migration — die Aussage bleibt also auch nach Abschnitt 4 falsch. Die vier
   Schnellbuchungen legen heute Belege ohne Datei an (KasseShortcutService
   .baseBeleg), diese Altbestaende bleiben dateilos. Empfehlung: die
   DB-Erzwingung wieder auf a)/b) beschraenken und fuer c) sagen, dass das
   Programm die Datei selbst erzeugt.

Hinweise (nicht blockierend, 🟡):
- Keine dedizierten Tests fuer die neue Einstellungs-Validierung. Die Logik
  selbst ist korrekt (Monat 1-12, Laengen 7/5 passend zu den Spalten,
  Ziffern-only + max 8 schuetzt auch den spaeteren DATEV-Export) — daher 🟡.
- validateKontonummer laesst den Leerstring durch, damit landet "" in der
  Spalte statt des Defaults.
- ZahlungsartMapper.giltAlsBezahlt ist nicht idempotent fuer Klartext:
  "AMAZON_PAY" -> true, "Online-Zahlung" -> false. Vertragskonform (Parameter
  heisst kiCode), aber leicht ueberraschend.
- BelegPdfService.berechneDateiHash liefert bei Fehlern still null zurueck; der
  Fingerabdruck ist der GoBD-Nachweis, ein harter Fehler waere ehrlicher.
- `Field` liegt jetzt dreimal identisch (BelegDetailModal, KassenbuchJournal,
  BelegeKasseEditor) — vorher einmal. Bewusste Duplizierung statt Auslagerung.
- select-custom.test.tsx:6-15 traegt einen falschen Kommentar ueber angeblich
  kaputte jest-dom-Matcher; die Zusicherungen sind gleichwertig, aber der
  Kommentar fuehrt den naechsten Agenten in die Irre und sollte weg.
- select-custom.tsx:153-158: der setTimeout-Trick beim Click-Outside-Listener
  kann bei Unmount im selben Tick einen Listener zuruecklassen — vorbestehend,
  unveraendert.
- BelegScannerPage: haengt ein Beleg dauerhaft auf PENDING, pollt die Seite
  endlos alle 3s. Ein Abbruch nach N Versuchen waere freundlicher.
- Die Doku-Dateien beschreiben den Zielzustand nach Abschnitt 4 im Praesens.
  Vom Plan (Zeile 224-235) ausdruecklich so gewollt, deshalb nur als Hinweis.
