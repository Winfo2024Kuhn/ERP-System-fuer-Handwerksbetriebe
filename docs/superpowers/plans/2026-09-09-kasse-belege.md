# Plan: Kasse & Belege

Issue: #<wird nachgetragen>
Feature-Branch: feature/kasse-belege
Kontext-Log: docs/superpowers/plans/2026-09-09-kasse-belege-log.md

Spec: `docs/superpowers/specs/2026-09-09-kasse-belege.md` (inklusive der sechs
Orchestrator-Entscheidungen am Ende — die sind verbindlich).
Recherche/Ist-Stand: `docs/superpowers/specs/2026-09-09-kasse-belege-brainstorming.md`.

Abschnitte, Branches und Worktrees sind vom Abschnitts-Agenten ergänzt
(09.09.2026): 4 Abschnitte, siehe "## Abschnittsübersicht" und
"## Datei-Tabelle" am Ende sowie `Branch:`/`Worktree:` in jedem `### Task N`.
Feature-Branch `feature/kasse-belege` existiert im Haupt-Checkout bereits und
wurde für diesen Lauf **nicht** neu angelegt. Worktrees legt der Orchestrator
an, nicht dieser Agent.

---

## Global Constraints

Diese Regeln gelten für **jeden** Task. Der Auftragstext eines Coding-Agenten
ist nur eine Zusammenfassung — **maßgeblich ist der `### Task N`-Block hier**.
Weicht der Auftrag vom Plan ab, gilt der Plan; die Abweichung melden.

### Pflichtlektüre vor dem ersten Edit

| Du änderst … | Vorher per `Read` laden |
| --- | --- |
| `*.java` | `docs/agent instructions/docs/BACKEND_ARCH.md` |
| `*.tsx`/`*.ts` in `react-pc-frontend/` oder `react-zeiterfassung/` | `docs/agent instructions/docs/FRONTEND_UI.md` **und** Design-Skill (siehe unten) |
| Testdateien (`*Test.java`, `*.test.tsx`, `*.spec.ts`) | `docs/agent instructions/docs/TESTING_SECURITY.md` |
| alle | `.claude/skills/loese-problem/references/kriterien.md` |

Ein PreToolUse-Hook (`.claude/hooks/check-doc-read.ps1`) blockt Edits mit
Exit 2, solange das passende Doc in der Session nicht gelesen wurde. Nicht
umgehen — lesen und den Edit danach erneut versuchen.

**Design-Skill bei Frontend-Arbeit:** `wt/<worktree-name>:handwerkerprogramm-design`
aufrufen (worktree-scoped, funktioniert). Der unscoped Name
`handwerkerprogramm-design` scheitert mit "Unknown skill". Fallback: den Inhalt
von `.claude/skills/handwerkerprogramm-design/SKILL.md` **und** `README.md` als
Datei lesen und zusätzlich den Skill `ui-ux-pro-max` (ohne Namespace-Präfix,
`ui-ux-pro-max:ui-ux-pro-max` scheitert) aufrufen, damit der Hook zufrieden ist.

### Projektregeln

- **Constructor Injection** (Lombok `@RequiredArgsConstructor`), keine
  Field-Injection. Controller enthalten keine Logik.
- **Named Params** in jeder `@Query` (`:param`), nie String-Concat.
- **JPQL:** Sobald eine Query eine nullable Beziehung prüft, expliziter
  `LEFT JOIN` statt implizitem Pfad — ein impliziter Pfad wird zum INNER JOIN
  und wirft still alle Zeilen ohne Bezug raus.
- **Flyway:** bestehende Migrationen niemals ändern. Die einzige neue Migration
  in diesem Vorhaben ist **V372** und gehört Task 1. Idempotent über
  `INFORMATION_SCHEMA`, Muster: `src/main/resources/db/migration/V351__kassenbuch_festschreibung.sql`
  (Zeilen 183-252 zeigen das Spalten-Muster).
- **Java-Enum-Spalten in MySQL sind native `ENUM`-Spalten, nicht VARCHAR.**
  Hibernate 6 mit MySQL-Dialekt erwartet das, sonst fällt `ddl-auto=validate`
  beim Start um. Vorbild: `V309__beleg_sachkonto_enums.sql`.
- **DSGVO:** In Tests, Fixtures, E2E-Stubs und Screenshots nur Dummy-Daten
  ("Max Mustermann", "Musterbetrieb GmbH", "muster@example.com"). Keine echten
  Namen, Adressen, Kontonummern. Logs: Entität + ID, nie Klarnamen.
- **Secrets** niemals in Code oder Commit. Vor dem Commit `git diff --staged`
  ansehen.
- **N+1 vermeiden:** Listen-Endpoints laden keine Zusatzdaten je Zeile. Wo ein
  Task Zusatzinformationen ins DTO hängt, gilt das nur für die Detail-Sicht
  (`BelegService.toDto(b, true)`), nie für `listBelege`.

### Wording (Handwerker-Sprache, C11 der Spec)

Kein Buchhalter-Wort ohne Erklärung. Verbindliche Tabelle:

| Buchhalter-Wort | In der Oberfläche |
| --- | --- |
| Sachkonto | Wofür? (Konto) |
| Kostenstelle | Baustelle / Bereich |
| Belegkategorie | (entfällt — ergibt sich aus "Wie bezahlt?") |
| Privateinlage / Privatentnahme | Eigenes Geld eingelegt / Geld privat entnommen |
| Festschreibung | Monat abschließen |
| Kassensturz | Kasse zählen |
| Buchungsstapel | Buchungen für den Steuerberater (DATEV-Datei) |
| Eigenbeleg | Ersatzbeleg (das Programm erstellt ihn) |
| Offener Posten | Noch nicht bezahlt |
| Soll/Haben | (kommt in der Oberfläche nicht mehr vor) |

Das Programm tritt **nie** wie eine Registrierkasse auf: kein "Verkauf
abschließen", kein "Kassenbon". Die Quittung ist der Beleg zur Buchung.

### Farben (nicht erfinden!)

Am 08.09.2026 haben zwei unabhängige Agenten für denselben Sachverhalt `teal`
gewählt — eine Farbe, die im Design-System nirgends vorkommt. Deshalb sind die
Rollen hier festgelegt:

| Rolle | Farbe |
| --- | --- |
| Marke / Primäraktion | `rose-600` Fläche, `rose-700` Text, `rose-200/300` Rahmen |
| Neutrale Information / Hinweis | indigo (`--info`), z.B. `bg-indigo-50 border-indigo-200 text-indigo-900` |
| Erfolg / Geld kommt rein | `emerald-*` |
| Warnung / offene Aufgabe | `amber-*` |
| Fehler / Sperre | `rose-*` bzw. `red-*` wie im Bestand |
| Flächen, Text, Rahmen | `slate-*` |

Vorbilder im Bestand: die Modals in `react-pc-frontend/src/components/kasse/`
(`ModalShell`, `FieldRow`, `ModalFooter` — `KasseShortcuts.tsx:643-695`), die
Kachel `KpiTile` (`BelegeKasseEditor.tsx:1148-1158`), die Konflikt-Box
(`BelegeKasseEditor.tsx:1687-1700`, amber) und die KI-Karte
(`BelegeKasseEditor.tsx:1944-1998`, rose).

**Gegenprobe vor dem Abschluss:**
`grep -rn "teal-" react-pc-frontend/src react-zeiterfassung/src` muss leer
bleiben. Dasselbe gilt für jede Farbfamilie, die oben nicht steht.

### Layout-Fallen, die kein Test von selbst findet

- `break-words` reicht bei Flex-/Grid-Items nicht — `min-w-0` gehört **an das
  Element selbst**, und zwar auf **jede** Ebene, nicht nur auf die unterste.
- `boundingBox()` ist kein Überlauf-Maß. Überlauf misst man mit
  `scrollWidth` gegen `clientWidth`.
- `space-y-*` schlägt `mt-auto` (Spezifität 0-3-0 gegen 0-1-0). Wer den letzten
  Block nach unten schieben will, nimmt `flex flex-col` + `gap-*`.
- Testdaten für Umbruch-Zusicherungen brauchen ein **langes Wort ohne
  Bindestriche** — Bindestriche und Punkte sind selbst Umbruchpunkte und
  verdecken den Fehler. Hilfe dafür: `erwarteteKartenspalten()` und
  `spacelosesWort()` in `react-pc-frontend/e2e/hilfen/testdaten.ts`.

### Tests und Gates — wer was fährt

**Coding-Agenten fahren nie die komplette Suite.** Nur die eigenen Tests plus
Lint/Build. Die Reviewer fahren alles.

- Backend: `./mvnw -B test -Dtest=KlasseA,KlasseB`
  (PowerShell: `.\mvnw.cmd -B test -Dtest=KlasseA,KlasseB`).
  **Mehrere Klassen trennt ein Komma, niemals ein Plus.** `+` trennt bei
  Surefire Methoden *innerhalb* einer Klasse; mit `+` läuft die halbe Menge.
- Frontend: `npx vitest run <datei>` **und** `npm run lint` **und**
  `npm run build`. Lint ist das am häufigsten gerissene Gate.
- Playwright: jeder Frontend-Task fährt **nur seine eigene Spec** auf seinem
  eigenen Port: `E2E_PORT=<port> npx playwright test e2e/<spec>`. Die Ports
  stehen bei den Tasks; zwei Agenten dürfen nie denselben Port benutzen.
- Nur **ein** Maven-Prozess je Worktree. Zwei parallele Läufe auf demselben
  `target/` erfinden Hunderte Phantom-Fehler (`class path resource ... cannot
  be opened because it does not exist`). Wer solche Zahlen sieht: erst an
  Selbstverschulden denken, dann melden.
- Keine E2E-Suite parallel zu einem Maven-Lauf.
- Shell-Aufrufe mit Timeout-Parameter **600000 ms** starten. Der Standard von
  120 s schickt lange Läufe still in den Hintergrund.

**Backend-Baseline (Kontext-Log, 09.09.2026):** 2747 Tests, 0 Failures,
**4 Errors** — `AuditChainRepairIntegrationTest` (2) und
`AuditHashRoundtripDiagnoseTest` (2), alle
`CannotCreateTransaction: Could not open JPA EntityManager` (brauchen eine
echte Datenbank). Grün heißt: genau diese vier, keine mehr. Nicht reparieren,
nicht überspringen, nicht deaktivieren.

### Build-Artefakte und Phantom-Diffs

- `npm run build` schreibt in das **versionierte** Verzeichnis
  `src/main/resources/static/` (PC, `emptyOutDir: false`) bzw.
  `src/main/resources/static/zeiterfassung/` (Handy) und schreibt die
  Script-Zeile in `index.html` um. Der Build ist ein Fail-Fast-Check: **vor dem
  Commit den Build-Output verwerfen** (`git checkout -- src/main/resources/static`).
  Sonst kollidieren parallele Tasks auf genau dieser Zeile.
- `src/main/resources/static/index.html` zeigt in **jedem** frischen Worktree
  einen Phantom-Diff (CRLF/LF). Kein Befund, nicht committen, nicht
  "reparieren", nicht im Report führen.

### Texte und Specs

Änderst du einen sichtbaren Text, `grep` vorher nach dem alten Wortlaut über
`react-pc-frontend/e2e/`, `react-pc-frontend/src/` und `react-zeiterfassung/`.
E2E-Specs prüfen Anzeigetexte buchstabengenau und liegen selten neben der
Komponente, die den Text erzeugt.

### Charakterisierungs- und Bestandstests

An einer fremden Testdatei darfst du die **Verkabelung** anpassen (Mock oder
`@MockBean` ergänzen, Stub setzen, Konstruktoraufruf nachziehen).
**Unverändert bleiben:** jede erwartete Zahl, jede erwartete Exception samt
Meldung, die Menge der geprüften Fälle. Stubs pro Fixture-Fall mit dem
konkreten Wert setzen, nicht pauschal `any()`.

### Suche

`./graphify query|path|explain` vor jeder breiten Codesuche (Wrapper im
Projektroot, Bash: `./graphify …`, PowerShell: `.\graphify.cmd …`). Für die in
diesem Plan genannten Stellen reicht `Read` mit der angegebenen Zeile.
**`./graphify update .` läuft nicht im Worktree** — das macht der Orchestrator
einmal am Ende im Haupt-Checkout.

### Referenzen

- Abnahme-Kriterien: `.claude/skills/loese-problem/references/kriterien.md`
- Fallstricke aus echten Läufen: `.claude/skills/loese-problem/references/fallstricke.md`
- Kontext-Log-Format: `.claude/skills/loese-problem/references/kontext-log-format.md`

### Pfad-Abkürzungen in diesem Plan

- **B** = `src/main/java/org/example/kalkulationsprogramm`
- **T** = `src/test/java/org/example/kalkulationsprogramm`
- **PC** = `react-pc-frontend/src`
- **PCE** = `react-pc-frontend/e2e`
- **MOB** = `react-zeiterfassung/src`

### Tasks mit Frontend-Berührung (Design-Reviewer läuft mit)

Task 9, 10, 11, 12, 13, 14, 15, 16.

---

## Tasks

## Abschnitt 1

Tasks ohne offene Abhängigkeit (Ebene 1 der Consumes-Kette): das Datenmodell
(Task 1), der PDF-Service (Task 2), die drei eigenständigen Frontend-Tasks
(Task 9, 10, 16) sowie die Dokumentation (Task 17).

**Frontend betroffen:** ja — Task 9 (`react-pc-frontend`, Port 5201), Task 10
(`react-pc-frontend`, Port 5202), Task 16 (`react-zeiterfassung`, Port 5261).
Design-Reviewer läuft für diesen Abschnitt mit.

**Task 17 vorgezogen:** Task 17 (Dokumentation) braucht laut
"Abhängigkeiten auf einen Blick" fachlich die Ergebnisse von Task 3, 7 und 8 —
aber nur als Textgrundlage, nicht als Datei-Abhängigkeit. Die dafür nötigen
Tabellen (sechs Kacheln, `ableitenKonten`-Soll/Haben, ZIP-Fünferpaket) stehen
bereits vollständig und verbindlich in den Task-3/7/8-Blöcken dieses
Plandokuments — der Umsetzer schreibt die Doku-Texte gegen **diese Spec**,
nicht gegen den erst in Runde 2/3 gemergten Code. Task 17s Dateien
(`docs/GOBD_COMPLIANCE.md`, `docs/KASSE_BUCHHALTUNG.md`,
`VerfahrensdokumentationService.java`, `VerfahrensdokumentationServiceTest.java`)
sind mit keinem anderen Task dieser oder einer späteren Runde geteilt (siehe
Datei-Tabelle unten) — die Vorzugsregel aus dem Auftrag greift.

### Task 1: Datenmodell V372, Entities, DTO-Felder, ZahlungsartMapper

Fundament für fast alles andere. Entity-Änderung und Migration liegen bewusst
im **selben** Task: das Testprofil fährt kein Flyway, ein getrennter Schnitt
fiele erst beim echten Start mit `ddl-auto=validate` auf.

- Branch: `kasse/task-1-datenmodell`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-1`
- Files:
  - `src/main/resources/db/migration/V372__kasse_buchungen_und_export.sql` (neu)
  - `B/domain/BelegQuelle.java` (neu)
  - `B/domain/Beleg.java`
  - `B/domain/KasseEinstellung.java`
  - `B/service/ZahlungsartMapper.java` (neu)
  - `B/dto/BelegDto.java`
  - `B/controller/KasseShortcutController.java`
  - `T/db/KasseBelegeMigrationTest.java` (neu)
  - `T/service/ZahlungsartMapperTest.java` (neu)
- Vorbild:
  - Migration idempotent: `V351__kassenbuch_festschreibung.sql:183-252`
    (`SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS …); SET @s = IF(@c = 0, 'ALTER TABLE …', 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;`)
  - Enum-Spalten: `V309__beleg_sachkonto_enums.sql` (komplette Datei)
  - Stammdaten-Seed idempotent: `V308__zahlungsart_stammdaten.sql:39-47`
    (`INSERT IGNORE INTO zahlungsart (bezeichnung, aktiv, sortierung) VALUES …`)
  - Migrationstest auf H2 im MySQL-Modus:
    `T/db/ZeitkontoMigrationTest.java` (zieht die `ALTER TABLE`-Statements per
    Regex aus den MySQL-`PREPARE`-Stringliteralen und führt sie auf H2 aus)
  - Entity-Stil: `B/domain/Beleg.java:243-305` (Festschreibungs-/Storno-Block)
- Interfaces:
  - Produces `B/domain/BelegQuelle.java`:
    ```java
    public enum BelegQuelle { SCAN, QUITTUNG, EIGENBELEG, TRANSFER }
    ```
  - Produces neue Felder an `Beleg` (Getter/Setter über Lombok `@Getter/@Setter`):
    ```java
    @Enumerated(EnumType.STRING)
    @Column(name = "quelle", nullable = false, length = 20)
    private BelegQuelle quelle = BelegQuelle.SCAN;

    @Column(name = "gegenpartei", length = 120)
    private String gegenpartei;

    @Column(name = "ausgangsrechnung_id")
    private Long ausgangsrechnungId;

    @Column(name = "ki_zahlungsart", length = 40)
    private String kiZahlungsart;

    @Column(name = "ki_belegdatum")
    private LocalDate kiBelegdatum;

    @Column(name = "ki_betrag_brutto", precision = 15, scale = 2)
    private BigDecimal kiBetragBrutto;

    @Column(name = "ki_kostenkonto_hinweis", length = 255)
    private String kiKostenkontoHinweis;
    ```
  - Produces neue Felder an `KasseEinstellung`:
    ```java
    @Column(name = "datev_beraternummer", length = 7)
    private String datevBeraternummer;

    @Column(name = "datev_mandantennummer", length = 5)
    private String datevMandantennummer;

    @Column(name = "wirtschaftsjahr_beginn_monat", nullable = false)
    private Integer wirtschaftsjahrBeginnMonat = 1;

    @Column(name = "kassenkonto_nummer", length = 8)
    private String kassenkontoNummer = "1000";

    @Column(name = "bankkonto_nummer", length = 8)
    private String bankkontoNummer = "1200";
    ```
  - Produces `B/service/ZahlungsartMapper.java` (Utility-Klasse, privater
    Konstruktor, rein statisch — kein Spring-Bean, damit sie auch aus der
    Migrationsdoku und aus Tests ohne Kontext nutzbar ist):
    ```java
    public final class ZahlungsartMapper {
        /** KI-Code (GeminiDokumentAnalyseService) -> Stammdaten-Bezeichnung aus `zahlungsart`.
         *  Liefert null, wenn kein eindeutiges Ziel existiert (SONSTIGE, leer, unbekannt). */
        public static String zuStammdaten(String kiCode);

        /** Stammdaten-Bezeichnung -> "Wo gezahlt" (BelegKategorie).
         *  richtungAusgabe = true -> Bar wird KASSE_AUSGABE, sonst KASSE_EINNAHME.
         *  Liefert null, wenn keine Ableitung moeglich ist. */
        public static BelegKategorie zuKategorie(String stammdatenBezeichnung, boolean richtungAusgabe);

        /** true, wenn eine Rechnung mit dieser Zahlungsart als bereits bezahlt gilt. */
        public static boolean giltAlsBezahlt(String kiCode);
    }
    ```
  - Produces neue Felder an `BelegDto.Response` (nur Deklaration, gefüllt wird
    in Task 5): `quelle` (String), `gegenpartei` (String), `ausgangsrechnungId`
    (Long), `kiZahlungsart` (String), `kiBelegdatum` (LocalDate),
    `kiBetragBrutto` (BigDecimal), `kiKostenkontoHinweis` (String),
    `eingangsrechnungBezahlt` (Boolean), `eingangsrechnungBezahltAm`
    (LocalDate), `vorschlagSachkonto` (`VorschlagDto`), `vorschlagKostenstelle`
    (`VorschlagDto`).
  - Produces `BelegDto.VorschlagDto` (neue statische Klasse, `@Data @Builder
    @NoArgsConstructor @AllArgsConstructor`, Vorbild `KostenstellenSplitDto`
    `BelegDto.java:107-122`):
    ```java
    private Long id;              // Sachkonto- bzw. Kostenstellen-ID
    private String nummer;        // nur beim Sachkonto gefuellt
    private String bezeichnung;
    private String quelle;        // "KI" | "HISTORIE" | "LIEFERANT_STANDARD"
    private String begruendung;   // ein Satz in Handwerker-Sprache
    ```
  - Produces neue Felder an `BelegDto.UpdateRequest`:
    `private String zahlungsstatus;` ("BEZAHLT" | "OFFEN") und
    `private LocalDate bezahltAm;`
  - Produces erweiterte Records in `KasseShortcutController`:
    `EinstellungRequest` und `EinstellungResponse` bekommen **hinten
    angehängt** (Reihenfolge der Bestandsfelder unverändert):
    `String datevBeraternummer, String datevMandantennummer,
    Integer wirtschaftsjahrBeginnMonat, String kassenkontoNummer,
    String bankkontoNummer`.
  - Consumes: nichts.
- Steps:
  - [ ] `B/domain/BelegQuelle.java` anlegen (vier Konstanten, Javadoc: SCAN =
        Foto/Upload, QUITTUNG = vom Programm erzeugte Kundenquittung,
        EIGENBELEG = vom Programm erzeugter Ersatzbeleg, TRANSFER =
        Bank↔Kasse/Privat ohne Fremdbeleg).
  - [ ] `V372__kasse_buchungen_und_export.sql` schreiben, Blöcke in dieser
        Reihenfolge, jeder einzeln idempotent nach dem V351-Muster:
        1. `beleg`: `quelle ENUM('SCAN','QUITTUNG','EIGENBELEG','TRANSFER') NOT NULL DEFAULT 'SCAN'`.
           **Native ENUM-Spalte, nicht VARCHAR** — sonst bricht
           `ddl-auto=validate` (siehe V309).
        2. `beleg`: `gegenpartei VARCHAR(120) NULL`.
        3. `beleg`: `ausgangsrechnung_id BIGINT NULL` plus FK
           `fk_beleg_ausgangsrechnung FOREIGN KEY (ausgangsrechnung_id)
           REFERENCES projekt_geschaeftsdokument(id) ON DELETE SET NULL`
           (Tabellenname bestätigt über `V327__projekt_geschaeftsdokument_system_generiert.sql`;
           JOINED-Inheritance, `id` = PK). FK-Anlage separat über
           `information_schema.TABLE_CONSTRAINTS` absichern.
        4. `beleg`: `ki_zahlungsart VARCHAR(40) NULL`, `ki_belegdatum DATE NULL`,
           `ki_betrag_brutto DECIMAL(15,2) NULL`,
           `ki_kostenkonto_hinweis VARCHAR(255) NULL`.
        5. `kasse_einstellung`: `datev_beraternummer VARCHAR(7) NULL`,
           `datev_mandantennummer VARCHAR(5) NULL`,
           `wirtschaftsjahr_beginn_monat INT NOT NULL DEFAULT 1`,
           `kassenkonto_nummer VARCHAR(8) NULL DEFAULT '1000'`,
           `bankkonto_nummer VARCHAR(8) NULL DEFAULT '1200'`.
           **Bewusste Abweichung von der Spec:** dort steht `TINYINT`. Hibernate
           mappt `Integer` auf `int`; `TINYINT` würde `ddl-auto=validate`
           reißen. Diese Begründung als Kommentar in die Migration schreiben.
        6. Stammdaten: `INSERT IGNORE INTO zahlungsart (bezeichnung, aktiv, sortierung)
           VALUES ('Online-Zahlung', TRUE, 65);` — neuer Eintrag für
           `AMAZON_PAY` und ähnliche Bezahldienste. Die Tabelle hat nur
           `bezeichnung/aktiv/sortierung` (V308), **keine** Kategorie-Spalte;
           die Zuordnung "Online-Zahlung → BANK" lebt in `ZahlungsartMapper`.
        7. Backfill `beleg.zahlungsart` per `UPDATE beleg SET zahlungsart = CASE
           UPPER(TRIM(zahlungsart)) WHEN 'BAR' THEN 'Bar' WHEN 'EC' THEN 'EC-Karte'
           WHEN 'EC_KARTE' THEN 'EC-Karte' WHEN 'GIROCARD' THEN 'EC-Karte'
           WHEN 'UEBERWEISUNG' THEN 'Überweisung' WHEN 'SEPA_LASTSCHRIFT' THEN 'Lastschrift'
           WHEN 'LASTSCHRIFT' THEN 'Lastschrift' WHEN 'KREDITKARTE' THEN 'Kreditkarte'
           WHEN 'PAYPAL' THEN 'PayPal' WHEN 'AMAZON_PAY' THEN 'Online-Zahlung'
           WHEN 'VORAUSKASSE' THEN 'Überweisung' WHEN 'RECHNUNG' THEN 'Rechnung'
           WHEN 'SONSTIGE' THEN NULL ELSE zahlungsart END
           WHERE zahlungsart IS NOT NULL;` — der `ELSE zahlungsart` lässt bereits
           korrekte Klartext-Werte ("Bar", "Überweisung") unangetastet.
           Idempotent, weil ein zweiter Lauf nur noch in den `ELSE`-Zweig fällt.
        8. Backfill `UPDATE beleg SET quelle = 'TRANSFER' WHERE ist_umbuchung = TRUE
           AND quelle = 'SCAN';`
  - [ ] `ZahlungsartMapper` implementieren. Verbindliche Tabelle (Entscheidung 1
        des Orchestrators):

        | KI-Code | Stammdaten-Bezeichnung | BelegKategorie | gilt als bezahlt |
        | --- | --- | --- | --- |
        | `BAR` | Bar | KASSE_AUSGABE / KASSE_EINNAHME | ja |
        | `EC`, `EC_KARTE`, `GIROCARD` | EC-Karte | BANK | ja |
        | `UEBERWEISUNG` | Überweisung | BANK | nein |
        | `SEPA_LASTSCHRIFT`, `LASTSCHRIFT` | Lastschrift | BANK | nein |
        | `KREDITKARTE` | Kreditkarte | KREDITKARTE | ja |
        | `PAYPAL` | PayPal | BANK | ja |
        | `AMAZON_PAY` | Online-Zahlung | BANK | ja |
        | `VORAUSKASSE` | Überweisung | BANK | **ja** |
        | `RECHNUNG` | Rechnung | SONSTIGER_BELEG | nein |
        | `SCHECK` | Scheck | BANK | nein |
        | `SONSTIGE`, leer, unbekannt | `null` | `null` | nein |

        Eingabe vorher trimmen, auf Großbuchstaben normalisieren, `-` und
        Leerzeichen zu `_`. `zuStammdaten` akzeptiert zusätzlich die
        Klartext-Bezeichnungen selbst ("Bar", "Überweisung", …) und gibt sie
        unverändert zurück — dann ist der Mapper auch für schon migrierte
        Werte idempotent.
        **Hinweis für den Umsetzer:** `GeminiDokumentAnalyseService.normalizeZahlungsart`
        (`GeminiDokumentAnalyseService.java:2634-2657`) faltet EC-Karte,
        Girocard und Maestro heute schon auf `KREDITKARTE`. `EC`/`EC_KARTE`/
        `GIROCARD` kommen deshalb aus der KI praktisch nicht an — die Zeilen
        gehören trotzdem in den Mapper, weil Altbestand und manuelle Eingaben
        sie enthalten können. `normalizeZahlungsart` **nicht** ändern (das ist
        Bestandsverhalten mit eigenen Tests).
  - [ ] Die neuen Felder an `Beleg` und `KasseEinstellung` ergänzen, Javadoc in
        einem Satz je Feld (warum es das Feld gibt, nicht was es enthält).
  - [ ] `BelegDto` erweitern: `Response`-Felder, `VorschlagDto`,
        `UpdateRequest.zahlungsstatus` + `bezahltAm`. **Nur Deklaration** —
        das Füllen macht Task 5.
  - [ ] `KasseShortcutController`: `EinstellungRequest`/`EinstellungResponse`
        um die fünf Felder erweitern, `toEinstellungDto`
        (`KasseShortcutController.java:229-238`) und `updateEinstellung`
        (`:167-198`) nachziehen, den Leer-Fallback in `getEinstellung`
        (`:161-165`) auf die neue Record-Länge anpassen (Standardwerte:
        `wirtschaftsjahrBeginnMonat = 1`, `kassenkontoNummer = "1000"`,
        `bankkontoNummer = "1200"`). Validierung in `updateEinstellung`:
        Monat 1-12, Beraternummer max. 7 und Mandantennummer max. 5 Zeichen,
        Kontonummern nur Ziffern und max. 8 Zeichen — bei Verstoß
        `IllegalArgumentException` mit einem Satz in Handwerker-Sprache (wird
        vom bestehenden `catch` zu HTTP 400).
  - [ ] `T/service/ZahlungsartMapperTest.java`: je eine Zeile der Tabelle oben
        als parametrisierter Fall, plus `null`/leer/Unbekannt, plus Idempotenz
        (`zuStammdaten("Überweisung")` == `"Überweisung"`), plus
        `zuKategorie("Bar", true) == KASSE_AUSGABE` und
        `zuKategorie("Bar", false) == KASSE_EINNAHME`.
  - [ ] `T/db/KasseBelegeMigrationTest.java` nach dem Muster von
        `T/db/ZeitkontoMigrationTest.java`: H2 in MySQL-Kompatibilität
        (`jdbc:h2:mem:kasseBelege;MODE=MySQL;DB_CLOSE_DELAY=-1`), Minimal-Tabellen
        `beleg`, `kasse_einstellung`, `zahlungsart`, `projekt_geschaeftsdokument`
        anlegen, die `ALTER TABLE`-/`UPDATE`-/`INSERT`-Anweisungen per Regex aus
        V372 ziehen und ausführen. Geprüft wird:
        * alle neun neuen Spalten existieren mit dem erwarteten Typ,
        * der Zahlungsart-Backfill schreibt für jeden KI-Code den Wert aus der
          Tabelle oben (mindestens `BAR`, `UEBERWEISUNG`, `SEPA_LASTSCHRIFT`,
          `AMAZON_PAY`, `VORAUSKASSE`, `SONSTIGE`, plus einen bereits korrekten
          Klartextwert, der unverändert bleibt),
        * `ist_umbuchung = TRUE` wird zu `quelle = 'TRANSFER'`, ein normaler
          Beleg bleibt `SCAN`,
        * `Online-Zahlung` ist in `zahlungsart` vorhanden, ein zweiter Lauf legt
          keinen zweiten Eintrag an.
        **Nicht** mit `datei.contains("spaltenname")` prüfen — das ist grün,
        sobald der Name im Kommentarkopf steht. Gegenprobe machen: eine
        `ALTER TABLE`-Zeile testweise löschen, der Test muss rot werden.
- Gate:
  - `./mvnw -B test -Dtest=KasseBelegeMigrationTest,ZahlungsartMapperTest,BelegServiceTest,BelegServiceKasseValidationTest,KasseShortcutControllerTest`

---

### Task 2: BelegPdfService — Quittung und Eigenbeleg

Entscheidung 3 und 4 des Orchestrators: **ein** Service mit zwei Methoden;
statt Unterschrift trägt das PDF "Erstellt von <Name> am <Zeit>" plus den
SHA-256 der Datei.

- Branch: `kasse/task-2-belegpdf`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-2`
- Files:
  - `B/service/BelegPdfService.java` (neu)
  - `T/service/BelegPdfServiceTest.java` (neu)
- Vorbild: `B/service/BelegeKasseExportPdfService.java` — dieselbe
  PDF-Bibliothek (`com.lowagie.text`, OpenPDF), derselbe Briefkopf
  (`addBriefkopf`, Zeile 158-230), dieselbe Farbpalette (Zeile 79-88), dieselbe
  Temp-Datei-Strategie unterhalb von `${upload.path}` (Zeile 112-118),
  `formatEuro` (Zeile 748) und `zelle`/`headerCell` (Zeile 622-660).
- Interfaces:
  - Produces:
    ```java
    @Service
    @RequiredArgsConstructor
    public class BelegPdfService {
        /** Kundenquittung zu einer Bareinnahme. Datei liegt unter
         *  {upload.path}/belege/<UUID>_Quittung-<datum>.pdf. */
        public ErzeugtesPdf erzeugeQuittung(QuittungDaten daten);

        /** Ersatzbeleg (Eigenbeleg) ohne Vorsteuer. */
        public ErzeugtesPdf erzeugeEigenbeleg(EigenbelegDaten daten);

        public record ErzeugtesPdf(String gespeicherterDateiname,
                                   String originalDateiname,
                                   String mimeType,      // immer "application/pdf"
                                   String sha256) {}

        public record QuittungDaten(BigDecimal bruttoBetrag,
                                    BigDecimal mwstSatz,
                                    LocalDate datum,
                                    String vonWem,            // Gegenpartei
                                    String wofuer,            // Beschreibung
                                    String sachkontoLabel,    // "8400 Erlöse 19 %", darf null sein
                                    String rechnungsNummer,   // zugeordnete Ausgangsrechnung, darf null sein
                                    Mitarbeiter ersteller) {}

        public record EigenbelegDaten(BigDecimal bruttoBetrag,
                                      LocalDate datum,
                                      String anWen,           // Gegenpartei, darf null sein
                                      String zweck,
                                      String grund,           // warum es keinen Fremdbeleg gibt
                                      String zahlungsart,     // "Bar"
                                      String sachkontoLabel,
                                      Mitarbeiter ersteller) {}
    }
    ```
  - Consumes: nichts (arbeitet nur mit den Records, nicht mit `Beleg`).
- Steps:
  - [ ] `BelegPdfService` anlegen, `@Value("${upload.path:uploads}") private String uploadPath;`
        und `FirmeninformationRepository` per Constructor Injection (wie
        `BelegeKasseExportPdfService.java:69-75`).
  - [ ] Gemeinsames Layout in einer privaten Methode
        `erzeuge(String titel, List<String[]> zeilen, String fusstext, Mitarbeiter ersteller)`:
        A4 hochkant (`new Document(PageSize.A4, 36, 36, 36, 36)` — anders als
        das Kassenbuch-PDF, das quer liegt), Briefkopf mit Logo und Firmendaten
        analog `addBriefkopf`, Titelzeile, zweispaltige Werte-Tabelle, Fußtext.
  - [ ] Quittung: Titel **"Quittung"**, Zeilen "Betrag", "davon MwSt (<satz> %)",
        "Datum", "Von wem", "Wofür", "Konto" (nur wenn gesetzt), "Zu Rechnung"
        (nur wenn gesetzt). Fußtext:
        "Diese Quittung ist der Beleg zur Buchung im Kassenbuch. Sie ist kein
        Kassenbon einer Registrierkasse." Bei Brutto > 250 € zusätzlich der
        Hinweis "Über 250 € braucht der Kunde eine Rechnung mit seinen
        vollständigen Angaben." (§ 33 UStDV).
  - [ ] Eigenbeleg: Titel **"Ersatzbeleg (Eigenbeleg)"**, Zeilen "Betrag",
        "Datum", "An wen", "Wofür", "Grund", "Bezahlt mit", "Konto".
        Fester Satz: **"Ohne Fremdbeleg gibt es keine Vorsteuer."** Kein
        MwSt-Ausweis, keine Unterschriftszeile.
  - [ ] Beide PDFs bekommen unten den Block
        `Erstellt von <Vorname Nachname> am <dd.MM.yyyy HH:mm> Uhr` und in der
        Zeile darunter `Fingerabdruck der Datei (SHA-256): <hash>`.
        Ablauf: PDF ohne Hash schreiben → SHA-256 der Datei berechnen → eine
        zweite Seite ist **nicht** gewünscht; stattdessen den Hash über den
        Inhalt **ohne** die Hash-Zeile berechnen und in einem eigenen Absatz
        ausgeben ist ein Zirkelschluss. Deshalb: den Hash über die fertige
        Datei berechnen, ihn im Record `ErzeugtesPdf.sha256` zurückgeben und im
        PDF nur "Erstellt von … am …" drucken. Der Hash landet über
        `Beleg.dateiHash` an der Buchung (das macht Task 3) — genau dort, wo
        ihn auch ein gescannter Beleg hat.
        SHA-256 blockweise berechnen, Vorbild `BelegService.berechneDateiHash`
        (`BelegService.java:788-812`) — Methode dort ist privat, hier eine
        eigene private Kopie anlegen (kein Umbau an `BelegService`).
  - [ ] Dateiname: `UUID.randomUUID() + "_Quittung-" + datum + ".pdf"` bzw.
        `"_Ersatzbeleg-" + datum + ".pdf"`, abgelegt unter
        `Paths.get(uploadPath, "belege")` — **derselbe Ordner wie hochgeladene
        Belege**, damit `BelegService.getBelegDatei` und der Download-Endpoint
        ohne Sonderfall funktionieren.
  - [ ] `T/service/BelegPdfServiceTest.java`: `@TempDir` als `upload.path`
        (Feld per `ReflectionTestUtils.setField` setzen),
        `FirmeninformationRepository` mocken (einmal mit Firmendaten, einmal
        `Optional.empty()`). Geprüft:
        * Quittung und Eigenbeleg legen eine lesbare PDF-Datei im Ordner
          `belege/` an (Datei existiert, > 1 kB, beginnt mit `%PDF`),
        * `sha256` ist 64 Zeichen und stimmt mit dem selbst berechneten
          SHA-256 der Datei überein,
        * der Eigenbeleg-Text enthält "Ohne Fremdbeleg gibt es keine Vorsteuer."
          und **keinen** MwSt-Betrag (PDF-Text mit
          `com.lowagie.text.pdf.parser.PdfTextExtractor` bzw. dem im Projekt
          vorhandenen Weg auslesen; falls kein Extractor verfügbar ist, statt
          dessen die erzeugte Zeilenliste über eine paketsichtbare Methode
          prüfen — dann aber zusätzlich sicherstellen, dass genau diese Liste
          ins PDF geht),
        * ohne Firmeninformation entsteht trotzdem ein gültiges PDF,
        * Dummy-Daten: Ersteller "Max Mustermann", Gegenpartei "Musterbetrieb GmbH".
- Gate:
  - `./mvnw -B test -Dtest=BelegPdfServiceTest`
---

### Task 9: Refactoring C9 — reine Verschiebung, kein Verhalten ändern (Frontend)

**Muss als erster Frontend-Task laufen.** Alle fachlichen Frontend-Tasks
bauen darauf auf und besitzen danach je eine eigene Datei. Der Task ändert
**kein** sichtbares Verhalten: gleiche Texte, gleiche Klassen, gleiche
Reihenfolge, gleiche Netzwerkaufrufe.

- Branch: `kasse/task-9-refactoring`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-9`
- Files:
  - `PC/pages/BelegeKasseEditor.tsx` (schrumpft auf die Seitenhülle)
  - `PC/types.ts` (Beleg-Typen zentral)
  - `PC/components/kasse/BelegDetailModal.tsx` (neu)
  - `PC/components/kasse/KassenbuchTab.tsx` (neu)
  - `PC/components/kasse/KassenbuchJournal.tsx` (neu)
  - `PC/components/kasse/VorschlagsChip.tsx` (neu)
  - `PC/components/kasse/NeueBuchungDialog.tsx` (neu)
  - `PC/components/kasse/KasseEinstellungenDialog.tsx` (neu)
  - `PC/components/kasse/KasseShortcuts.tsx`
  - `PC/components/kasse/belegFormat.ts` (neu)
  - `PC/components/kasse/belegFormat.test.ts` (neu)
  - `PCE/kasse-refactoring.spec.ts` (neu)
- Vorbild: der bestehende Zuschnitt in `PC/components/kasse/`
  (`KassenbuchAbschlussLeiste.tsx`, `StornoDialog.tsx`,
  `KostenstellenSplitsEditor.tsx`) — eine Komponente je Datei, Props oben,
  Typen lokal nur, wenn sie sonst niemand braucht.
- Interfaces:
  - Produces in `PC/types.ts` (alle `export`):
    `BelegStatus`, `SachkontoTyp`, `Sachkonto`, `Zahlungsart`,
    `BelegKategorie`, `KiStatus`, `AufteilungsModus`, `BelegPosition`,
    `Beleg`, `KassenBewegung`, `Kassenbuch`, `AuswertungZeile`, `Auswertung`
    — wortgleich aus `BelegeKasseEditor.tsx:24-183` übernommen, **plus** die
    neuen optionalen Felder aus der Spec (siehe Steps).
  - Produces `PC/components/kasse/belegFormat.ts`:
    ```ts
    export const KATEGORIE_LABELS: Record<BelegKategorie, string>;
    export const KATEGORIE_FARBE: Record<BelegKategorie, string>;
    export const SACHKONTO_TYP_LABEL: Record<SachkontoTyp, string>;
    export const KI_LABEL: Record<KiStatus, { label: string; cls: string }>;
    export const inputCls: string;
    export const gesperrtCls: string;
    export const formatEuro: (v: number | null | undefined) => string;
    export const isoDatum: (d: Date) => string;
    export const formatDate: (iso?: string | null) => string;
    export const formatDateTime: (iso?: string | null) => string;
    export function buildSachkontoOptions(sachkonten: Sachkonto[]): { value: string; label: string }[];
    export function buildZahlungsartOptions(zahlungsarten: Zahlungsart[], bestehenderWert?: string | null): { value: string; label: string }[];
    export function sicherheitsText(confidence: number | null | undefined): { text: string; cls: string };
    ```
  - Produces Komponenten mit **unveränderten** Props:
    * `BelegDetailModal` (heute `BelegeKasseEditor.tsx:1162-1797`) samt
      `AufteilungsSektion`, `Field`, `BelegPreview`
    * `KassenbuchTab` — neu zusammengesetzt aus dem `activeTab === 'kasse'`-Ast
      der Seite (`BelegeKasseEditor.tsx:530-548`): rendert `KasseShortcuts` und
      `KassenbuchJournal` und bekommt alle Werte per Props
    * `KassenbuchJournal` — heutiger `KassenbuchView` + `TKontoZeile` +
      `KassenbuchFilter` + `KpiTile`, **inhaltlich unverändert** (das T-Konto
      bleibt in diesem Task stehen und wird erst in Task 11 ersetzt)
    * `VorschlagsChip` — heutige `KiVorschlagKarte`
      (`BelegeKasseEditor.tsx:1921-1998`), Props unverändert
    * `NeueBuchungDialog` — die vier Buchungs-Modale aus `KasseShortcuts.tsx`
      (`BankAbhebungModal` 216-291, `EinfacheKasseModal` 295-408,
      `LohnZahlungModal` 414-513) samt `ModalShell`, `FieldRow`, `ModalFooter`;
      `KasseShortcuts.tsx` importiert sie und ruft sie wie bisher auf
    * `KasseEinstellungenDialog` — heutiges `KasseSettingsModal`
      (`KasseShortcuts.tsx:517-641`) plus `defaultEinstellung`
  - Consumes: nichts.
- Steps:
  - [ ] `PC/types.ts` unten um einen Block `// ===== Belege & Kasse =====`
        erweitern und die Typen 1:1 aus `BelegeKasseEditor.tsx:24-183`
        übernehmen. `KostenstellenSplit` bleibt in
        `components/kasse/KostenstellenSplitsEditor.tsx` und wird in `types.ts`
        von dort importiert und re-exportiert (kein Kopieren, sonst driften
        zwei Definitionen auseinander).
  - [ ] **Im selben Zug** die neuen optionalen Felder an `Beleg` ergänzen,
        damit spätere Tasks `types.ts` nicht mehr anfassen müssen. Das ändert
        kein Verhalten (alle optional):
        ```ts
        quelle?: 'SCAN' | 'QUITTUNG' | 'EIGENBELEG' | 'TRANSFER' | null;
        gegenpartei?: string | null;
        ausgangsrechnungId?: number | null;
        kiZahlungsart?: string | null;
        kiBelegdatum?: string | null;
        kiBetragBrutto?: number | null;
        kiKostenkontoHinweis?: string | null;
        eingangsrechnungBezahlt?: boolean | null;
        eingangsrechnungBezahltAm?: string | null;
        vorschlagSachkonto?: BelegVorschlag | null;
        vorschlagKostenstelle?: BelegVorschlag | null;
        ```
        plus
        ```ts
        export interface BelegVorschlag {
            id: number;
            nummer?: string | null;
            bezeichnung: string;
            quelle: 'KI' | 'HISTORIE' | 'LIEFERANT_STANDARD';
            begruendung?: string | null;
        }
        export interface KasseEinstellung {
            id?: number | null;
            mindestbestand: number;
            ehegattengehaltAktiv: boolean;
            ehegattengehaltBetrag?: number | null;
            ehegattengehaltTag?: number | null;
            ehegattengehaltEmpfaengerName?: string | null;
            privateinlageSachkontoId?: number | null;
            datevBeraternummer?: string | null;
            datevMandantennummer?: string | null;
            wirtschaftsjahrBeginnMonat?: number | null;
            kassenkontoNummer?: string | null;
            bankkontoNummer?: string | null;
        }
        ```
        Die lokale `KasseEinstellung`-Definition in `KasseShortcuts.tsx:34-42`
        entfällt zugunsten des Imports aus `types.ts`. Ebenso das lokale
        `Sachkonto` dort (Zeile 20-27).
  - [ ] `belegFormat.ts` anlegen und die Helfer aus
        `BelegeKasseEditor.tsx:185-288`, `:1893-1909` (`sicherheitsText`),
        `:1999` (`inputCls`) und `:2006` (`gesperrtCls`) dorthin verschieben.
        `KasseShortcuts.tsx` hat ein **eigenes** `formatEuro` (Zeile 49-52) und
        ein eigenes `inputCls` (Zeile 645) — beide ebenfalls durch den Import
        ersetzen; die Zeichenketten sind unterschiedlich, deshalb den
        `KasseShortcuts`-Wert als `modalInputCls` zusätzlich exportieren, damit
        sich optisch nichts ändert.
  - [ ] Komponenten in die genannten Dateien verschieben. Reihenfolge der
        JSX-Blöcke, alle Klassennamen, alle Texte und alle `fetch`-Aufrufe
        bleiben **zeichengleich**. Nur Importe werden angepasst.
  - [ ] `BelegeKasseEditor.tsx` behält: State, alle `load*`-Callbacks,
        `handleUpload`, `gefiltert`, `TabButton`, `BelegRow`,
        `MonatsExportModal`, `AuswertungView` und das Rendering der Tabs. Der
        `kasse`-Ast ruft nur noch `<KassenbuchTab … />`.
        Zielgröße: unter 900 Zeilen.
  - [ ] `belegFormat.test.ts` (vitest): `formatEuro` (null, 0, 1234.5,
        negativ), `isoDatum` (1. Januar in lokaler Zeit ergibt `YYYY-01-01`,
        **nicht** den 31.12. des Vorjahres — der Grund steht im Kommentar
        `BelegeKasseEditor.tsx:214-220`), `buildSachkontoOptions`
        (Gruppenreihenfolge Aufwand→Ertrag→Privat→Neutral, führender
        Leer-Eintrag), `buildZahlungsartOptions` (bestehender Fremdwert wird als
        "(nicht im Stamm)" angehängt), `sicherheitsText` an den Schwellen
        0.95/0.70/0.30.
  - [ ] `PCE/kasse-refactoring.spec.ts`: stubbt `/api/auth/me`,
        `/api/buchhaltung/belege`, `/api/buchhaltung/sachkonten`,
        `/api/buchhaltung/zahlungsarten`, `/api/buchhaltung/kassenbuch`,
        `/api/buchhaltung/kasse/saldo`, `/api/buchhaltung/kasse/einstellung`
        (Muster: `PCE/monatsabschluss-task9.spec.ts:5-40`, `test`/`expect`
        aus `./hilfen/test` importieren). Prüft, dass sich **nichts** geändert
        hat: `/belege-kasse` öffnet sich, die vier Tabs sind da, das Kassenbuch
        zeigt weiterhin "Eingang"/"Ausgang" und den Saldo, der Prüfen-Dialog
        öffnet sich per Klick auf eine Belegzeile und zeigt "Beleg prüfen &
        validieren", die vier Shortcut-Knöpfe sind sichtbar. Am Ende je Zustand
        `designPruefung(page, info, '<name>')`.
- Gate:
  - `cd react-pc-frontend && npx vitest run src/components/kasse/belegFormat.test.ts`
  - `cd react-pc-frontend && npm run lint`
  - `cd react-pc-frontend && npm run build` (danach `git checkout -- src/main/resources/static`)
  - `cd react-pc-frontend && E2E_PORT=5201 npx playwright test e2e/kasse-refactoring.spec.ts`

---

### Task 10: select-custom.tsx reparieren (Frontend)

Wird an 47 Stellen benutzt — reparieren, nicht ersetzen, und die
Props-Signatur bleibt rückwärtskompatibel.

- Branch: `kasse/task-10-select-custom`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-10`
- Files:
  - `PC/components/ui/select-custom.tsx`
  - `PC/components/ui/select-custom.test.tsx` (neu)
  - `PCE/dropdown-breite.spec.ts` (neu)
- Vorbild: keins im Bestand (Eigenbau). Für das Nachmessen im Test:
  `PCE/hilfen/design.ts:121-234` (`keinHorizontalerUeberlauf`,
  `keinTextLaeuftUeber` — beide vergleichen `scrollWidth` gegen `clientWidth`,
  genau die Technik, die hier gebraucht wird).
- Interfaces:
  - Produces (additiv, alle bestehenden Aufrufe bleiben gültig):
    ```ts
    interface Option { value: string; label: string; gruppe?: string; }
    interface SelectProps {
        options: Option[];
        value: string;
        onChange: (value: string) => void;
        placeholder?: string;
        className?: string;
        disabled?: boolean;
    }
    ```
    `gruppe` ist optional; sind Gruppen gesetzt, rendert das Dropdown je Gruppe
    eine nicht anklickbare Überschrift (`role="presentation"`) über den
    Optionen dieser Gruppe, in der Reihenfolge ihres ersten Auftretens.
  - Consumes: nichts.
- Steps:
  - [ ] Breite (heute `select-custom.tsx:29-37` und `:74`): statt hart
        `width: rect.width` einen State
        `{ top, left, minWidth, maxWidth, nachOben }`. Das Panel bekommt
        `minWidth = rect.width` und
        `maxWidth = Math.min(window.innerWidth * 0.9, 480)`, dazu
        `width: 'max-content'`. Damit wächst es mit dem Inhalt bis zur
        Deckelung und ist nie schmaler als der Auslöser.
  - [ ] Optionen dürfen umbrechen: in Zeile 92 `truncate` durch
        `whitespace-normal break-words` ersetzen und dem Zeilen-`<div>`
        zusätzlich `min-w-0` geben. `title={option.label}` ergänzen (Tooltip).
        **Beide Klassen zusammen sind die Rezeptur** — `break-words` allein
        wirkt bei einem Flex-Item nicht.
  - [ ] Position bei Scroll und Resize neu berechnen: die Berechnung aus dem
        `useEffect` in eine `useCallback`-Funktion `positioniere()` ziehen und
        bei offenem Dropdown `window.addEventListener('scroll', positioniere, true)`
        (capture = true, damit auch Scroll-Container innerhalb des Dialogs
        greifen) und `window.addEventListener('resize', positioniere)`
        registrieren; im Cleanup wieder abmelden.
  - [ ] Hochklappen: reicht der Platz unter dem Auslöser nicht für die
        gemessene Panel-Höhe (max. 240 px, `max-h-60`), aber darüber schon,
        dann `top = rect.top - panelHoehe - 4` und `nachOben = true`. Ist in
        beide Richtungen zu wenig Platz, nach unten öffnen und die Höhe auf den
        verbleibenden Raum begrenzen (`maxHeight`), damit das Panel nie aus dem
        Viewport ragt. Links/rechts genauso deckeln:
        `left = Math.max(8, Math.min(rect.left, window.innerWidth - breite - 8))`.
  - [ ] Gruppen rendern: `options` in der gegebenen Reihenfolge nach `gruppe`
        segmentieren; je Segment eine Überschrift
        `<div role="presentation" className="px-2 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-500">`.
        Optionen ohne `gruppe` stehen ohne Überschrift ganz oben.
  - [ ] Tastatur beibehalten/ergänzen: `Escape` schließt, `Enter`/`Space` auf
        dem Auslöser öffnet. Kein größerer Umbau — der Fokus dieses Tasks ist
        Breite und Position.
  - [ ] `select-custom.test.tsx` (vitest + Testing Library): Optionen mit
        Gruppen erzeugen zwei Überschriften in der richtigen Reihenfolge;
        Auswahl ruft `onChange` mit dem Wert; `disabled` öffnet nicht;
        jede Option trägt ein `title` mit dem vollen Label.
  - [ ] `PCE/dropdown-breite.spec.ts` — die **verbindliche Zusicherung**:
        Seite `/belege-kasse` mit gestubbten Routen öffnen (Stub-Muster wie
        Task 9), einen Beleg öffnen, das Konto-Dropdown aufklappen. Die
        Sachkonto-Optionen enthalten mindestens ein langes Label wie
        `Aufwand · 4930 Bürobedarf und Zeitschriften` **und** ein
        bindestrichloses Testwort (`spacelosesWort(48, 'Konto')` aus
        `PCE/hilfen/testdaten.ts`). Dann:
        ```ts
        const panel = page.getByRole('listbox');
        await expect(panel).toBeVisible();
        // 1) Keine Option ist abgeschnitten.
        const optionen = panel.getByRole('option');
        for (let i = 0; i < await optionen.count(); i++) {
            const masse = await optionen.nth(i).evaluate(el => ({
                scroll: el.scrollWidth, client: el.clientWidth, text: el.textContent,
            }));
            expect(masse.scroll, `Option "${masse.text}" laeuft ueber`).toBeLessThanOrEqual(masse.client);
        }
        // 2) Das Dropdown liegt vollstaendig im Viewport.
        const rahmen = await panel.boundingBox();
        const sicht = page.viewportSize()!;
        expect(rahmen!.x).toBeGreaterThanOrEqual(0);
        expect(rahmen!.y).toBeGreaterThanOrEqual(0);
        expect(rahmen!.x + rahmen!.width).toBeLessThanOrEqual(sicht.width);
        expect(rahmen!.y + rahmen!.height).toBeLessThanOrEqual(sicht.height);
        ```
        Beide Zusicherungen laufen automatisch bei **allen** konfigurierten
        Bildschirmgrößen — `playwright.config.ts` fährt jede Spec mit
        1440×900, 1536×960 und 1920×1080. Die geforderten 1440×900 und
        1920×1080 sind damit abgedeckt; die 1536er läuft mit, ohne dass die
        Spec etwas dafür tun muss.
        Zusätzlich: ein Dropdown, das am **unteren** Rand steht (Seite vorher
        scrollen bzw. ein Select im unteren Dialogbereich öffnen), klappt nach
        oben — geprüft über `rahmen.y + rahmen.height <= triggerRahmen.y`.
        Am Ende `designPruefung(page, info, 'dropdown-offen')`.
        **Gegenprobe verlangt:** `min-w-0` bzw. `whitespace-normal` testweise
        entfernen — die Spec muss rot werden. Beides einzeln probieren, damit
        keine Attrappe entsteht.
- Gate:
  - `cd react-pc-frontend && npx vitest run src/components/ui/select-custom.test.tsx`
  - `cd react-pc-frontend && npm run lint`
  - `cd react-pc-frontend && npm run build` (danach `git checkout -- src/main/resources/static`)
  - `cd react-pc-frontend && E2E_PORT=5202 npx playwright test e2e/dropdown-breite.spec.ts`

---

### Task 16: Handy — Ergebnis der KI kommt an (Frontend, react-zeiterfassung)

Unabhängig vom Rest: braucht keine neuen Backend-Felder, `BelegDto.Response`
liefert `betragBrutto`, `belegDatum` und `kiVorgeschlagenerLieferant` schon
heute.

- Branch: `kasse/task-16-handy-polling`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-16`
- Files:
  - `MOB/pages/BelegScannerPage.tsx`
  - `react-zeiterfassung/e2e/beleg-scanner-polling.spec.ts` (neu)
- Vorbild:
  - Polling mit Selbst-Neuplanung und `cancelled`-Flag: der Detail-Poller in
    `PC/components/kasse/BelegDetailModal.tsx` (nach Task 9; heute
    `BelegeKasseEditor.tsx:1181-1204`) — `let cancelled = false`, `setTimeout`
    im Erfolgsfall, Cleanup räumt beides auf.
  - Bestehender Reload-Pfad: `reloadServerBelege`
    (`BelegScannerPage.tsx:110-127`) und der `visibilitychange`-Effekt
    (`:129-138`).
  - Zeilendarstellung: `ServerBelegRow` (`BelegScannerPage.tsx:740-805`).
- Interfaces:
  - Produces: `ServerBeleg` wird um `betragBrutto?: number | null`,
    `belegDatum?: string | null` und `kiVorgeschlagenerLieferant?: string | null`
    erweitert (`BelegScannerPage.tsx:52-63`).
  - Consumes: nichts.
- Steps:
  - [ ] Polling ergänzen: neuer `useEffect`, der läuft, solange mindestens ein
        Eintrag in `serverBelege` `kiAnalyseStatus` `PENDING` oder `LAEUFT`
        hat. Alle **3 s** `reloadServerBelege()`, danach neu planen; sind alle
        fertig, kein neuer Timer. Cleanup mit `cancelled`-Flag und
        `clearTimeout`. **Achtung:** `ServerBelegRow` prüft heute auf
        `'RUNNING'` (`BelegScannerPage.tsx:745`) — der Server liefert
        `LAEUFT` (`BelegKiAnalyseStatus`). Beide Werte akzeptieren und den
        Fehler im Kommentar festhalten.
  - [ ] `ServerBelegRow` erweitert die Statuszeile: sobald
        `kiAnalyseStatus === 'DONE'` und ein Betrag da ist, steht dort
        `<Betrag> € · <Datum>` statt nur "Hochgeladen". Format: deutsche
        Zahl mit zwei Nachkommastellen, Datum `dd.MM.yyyy`.
  - [ ] Abweichender Lieferant: ist `kiVorgeschlagenerLieferant` gesetzt und
        ungleich `lieferantName`, erscheint unter der Lieferantenzeile
        `KI hat "<Name>" gelesen` in `text-xs text-amber-700`. Dazu der
        Zusatz "Am PC prüfen" — **kein** Bearbeiten am Handy (Nicht-Ziel).
  - [ ] Bundle-Größe im Blick behalten: keine neue Abhängigkeit, Formatierung
        mit `Intl.NumberFormat`/`toLocaleDateString`.
  - [ ] `react-zeiterfassung/e2e/beleg-scanner-polling.spec.ts` (Vorbild
        `react-zeiterfassung/e2e/zeitkonto-status.spec.ts` — `page.addInitScript`
        setzt `zeiterfassung_token`, `page.route('**/*')` blockt alles
        Fremde). Ablauf: erster `GET /api/buchhaltung/mobile/belege` liefert
        einen Beleg in `PENDING`, ab dem dritten Aufruf `DONE` mit Betrag,
        Datum und abweichendem `kiVorgeschlagenerLieferant`. Geprüft: die
        Zeile wechselt **ohne Zutun** von "KI liest Beleg…" auf
        `12,34 € · 02.08.2026`, der Satz `KI hat "Hornbach" gelesen` erscheint,
        und nach dem Wechsel kommen keine weiteren Abrufe mehr (Zähler
        mitschreiben, kurz warten, Zähler vergleichen). Screenshot über
        `page.screenshot({ path: testInfo.outputPath(...) })` wie im Vorbild.
        Dummy-Daten: Lieferant "Musterbaustoffe GmbH" gewählt, "Hornbach" ist
        als KI-Lesung fachlich neutral — falls das als echter Firmenname
        stört, stattdessen "Musterbaumarkt" verwenden.
- Gate:
  - `cd react-zeiterfassung && npm run lint`
  - `cd react-zeiterfassung && npm run build` (danach `git checkout -- src/main/resources/static`)
  - `cd react-zeiterfassung && E2E_PORT=5261 npx playwright test e2e/beleg-scanner-polling.spec.ts`
---

### Task 17: Dokumentation und Verfahrensdokumentation nachziehen

Kein Frontend. Läuft zuletzt, damit die Texte den gebauten Stand beschreiben
und nicht eine Absicht.

- Branch: `kasse/task-17-dokumentation`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-17`
- Files:
  - `docs/GOBD_COMPLIANCE.md`
  - `docs/KASSE_BUCHHALTUNG.md`
  - `B/service/VerfahrensdokumentationService.java`
  - `T/service/VerfahrensdokumentationServiceTest.java` (neu)
- Vorbild: `B/service/VerfahrensdokumentationService.java:38-120` — der Text
  wird im Code aus `StringBuilder`-Absätzen erzeugt, Abschnitte über die
  Helfer `titel(...)` und `abschnitt(...)`.
- Interfaces:
  - Produces: `erzeugeText()` bleibt in der Signatur, bekommt zwei neue
    Abschnitte.
  - Consumes: fachlich Task 3, 7 und 8 (die Texte beschreiben deren Ergebnis);
    technisch keine Datei aus einem anderen Task.
- Steps:
  - [ ] `docs/GOBD_COMPLIANCE.md`, Abschnitt **0.2** (Zeile 36-43): Die Zeile
        "**Barkasse mit TSE / DSFinV-K** | Separates Kassenprogramm …" ist seit
        V302-V351 überholt und wird **ersetzt**. Neuer Eintrag in derselben
        Tabelle:
        `| **Registrierkasse / POS mit TSE (§ 146a AO)** | Nicht abgebildet — wird auch nicht gebraucht | Das System führt ein *elektronisches Kassenbuch*, kein Aufzeichnungssystem mit Kassenfunktion. Wer eine offene Ladenkasse mit Einzelaufzeichnung führt, braucht keine TSE und muss nichts melden. Die bei einer Bareinnahme erzeugte Quittung ist der Beleg zur Buchung (§ 33 UStDV / § 14 UStG), kein Bon im Sinne von § 146a AO. |`
        Darunter ein kurzer Absatz mit der Abgrenzung aus der Spec
        (Abschnitt 0, "TSE/Kassengesetz gilt nicht") und dem Verweis auf
        `docs/KASSE_ANLEITUNG.md`.
  - [ ] `docs/GOBD_COMPLIANCE.md`, Abschnitt **0.3** (Zeile 45-53): Punkt 2
        ("Das Kassenprogramm liefert die DSFinV-K-Daten der Barkasse") streichen
        und durch "Dieses ERP liefert zusätzlich das Kassenbuch, den
        DATEV-Buchungsstapel und die Belegbilder als Monatspaket" ersetzen. Den
        Schlussabsatz entsprechend anpassen — die Aussage "verhindert, dass eine
        selbstgebaute Software in den zertifizierungspflichtigen Bereich
        hineingreift" bleibt für die **Finanzbuchhaltung** richtig und gilt
        weiter, nur nicht mehr für die Kasse.
  - [ ] `docs/KASSE_BUCHHALTUNG.md` aktualisieren:
        * Abschnitt 2 "Shortcuts": ersetzen durch "Neue Buchung — sechs
          Kacheln" mit der Tabelle aus Task 3 (Art → Kategorie → Quelle →
          Sachkonto → Beleg) und dem Hinweis, dass alle sechs über
          `KassenbuchungService.buche` laufen und
          `POST /api/buchhaltung/umbuchungen` nur noch ein Alias ist.
        * Abschnitt 4 "Doppik (Variante A)": um `ableitenKonten` ergänzen
          (Kontonummern statt Labels, Kasse/Bank aus `KasseEinstellung`) und
          festhalten, dass der DATEV-Writer immer `S` schreibt, mit
          Konto = Soll und Gegenkonto = Haben.
        * Abschnitt 5 "T-Konto-Monatsexport": umbenennen in "Was der
          Steuerberater bekommt" und den ZIP-Aufbau aus Task 8 beschreiben
          (die fünf Bausteine, die Vorprüfung, warum Bank- und
          Kreditkartenbuchungen bewusst fehlen). Der Begriff "T-Konto"
          verschwindet — die Oberfläche zeigt seit Task 11 ein Journal.
        * Abschnitt 6 "Datenmodell": die neun neuen `beleg`-Spalten und die
          fünf neuen `kasse_einstellung`-Spalten ergänzen, mit V372 als Quelle.
        * Neuer Abschnitt "Zahlungsart-Mapping": die Tabelle aus Task 1
          (KI-Code → Stammdaten → Kategorie → gilt als bezahlt) mit dem
          Hinweis, dass `ZahlungsartMapper` die einzige Quelle ist und die
          SQL-`CASE` in V372 sie spiegelt.
        * Abschnitt 7 "Frontend-Shortcuts": auf die neue Dateiaufteilung
          (`components/kasse/*`) umstellen.
        * Abschnitt 9 "Weiterführende Docs": `docs/KASSE_ANLEITUNG.md`
          aufnehmen.
  - [ ] `VerfahrensdokumentationService.erzeugeText()` um zwei Punkte
        erweitern, im Stil der bestehenden Absätze (ganze Sätze, keine
        Aufzählungswüste, keine Fachbegriffe ohne Erklärung):
        * In Abschnitt 1 ("Wie ein Beleg in die Buchhaltung kommt") den Weg c)
          umschreiben: Das Programm erzeugt bei einer Bareinnahme eine
          **Quittung** und bei fehlendem Fremdbeleg einen **Ersatzbeleg**,
          jeweils als PDF mit Ersteller, Zeitpunkt und Fingerabdruck. Damit hat
          auch eine belegfreie Buchung eine Datei — die Invariante "keine
          Buchung ohne Beleg" gilt lückenlos.
        * Neuer Abschnitt am Ende: **"Warum diese Kasse keine
          Registrierkasse ist"** — offene Ladenkasse plus Kassenbuch mit
          Einzelaufzeichnung, deshalb keine TSE, keine Kassenmeldung, keine
          Belegausgabepflicht (§ 146a AO gilt für Aufzeichnungssysteme mit
          Kassenfunktion). Die Quittung ist der Beleg zur Buchung, kein Bon.
        * Zweiter neuer Abschnitt: **"Was der Steuerberater monatlich
          bekommt"** — die fünf Bausteine des ZIP-Pakets, ein Satz je Baustein,
          plus der Hinweis, dass Bankumsätze bewusst nicht enthalten sind.
  - [ ] `T/service/VerfahrensdokumentationServiceTest.java` neu anlegen
        (bisher gibt es keinen): `FirmeninformationRepository` mocken (einmal
        mit "Musterbetrieb GmbH", einmal leer). Geprüft: der Text enthält die
        Überschriften der beiden neuen Abschnitte; er enthält den Satz zur
        fehlenden TSE-Pflicht; er enthält "Quittung" und "Ersatzbeleg"; ohne
        Firmendaten steht "(Firmenname nicht gepflegt)" statt einer Exception;
        der Text ist länger als 3000 Zeichen (Rauchtest, dass kein Abschnitt
        verloren ging).
  - [ ] Gegenprobe für die Doku: `grep -rn "TSE" docs/GOBD_COMPLIANCE.md` —
        jede verbliebene Fundstelle muss die **neue** Aussage tragen (TSE gilt
        für Registrierkassen, nicht für uns).
- Gate:
  - `./mvnw -B test -Dtest=VerfahrensdokumentationServiceTest`

---

## Abschnitt 2

Tasks, deren gesamte `Consumes`-Kette in Abschnitt 1 fertig ist: die vier
`ZahlungsartMapper`-Konsumenten im Backend (Task 4, 5, 6, 7), die
`KassenbuchungService` (Task 3, braucht Task 1 + 2) und das Kassenbuch-Journal
im Frontend (Task 11, braucht Task 9).

**Frontend betroffen:** ja — Task 11 (`react-pc-frontend`, Port 5203).
Design-Reviewer läuft mit.

### Task 3: KassenbuchungService — ein Pfad für alle sechs Kacheln

- Branch: `kasse/task-3-kassenbuchung`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-3`
- Files:
  - `B/service/KassenbuchungService.java` (neu)
  - `B/dto/KassenbuchungDto.java` (neu)
  - `B/controller/KassenbuchungController.java` (neu)
  - `B/controller/BelegController.java` (nur `createUmbuchung`, Zeile 246-270)
  - `T/service/KassenbuchungServiceTest.java` (neu)
  - `T/controller/KassenbuchungControllerTest.java` (neu)
- Vorbild:
  - Service-Aufbau, Mindestbestand-Prüfung, Audit: `B/service/KasseShortcutService.java`
    (`baseBeleg` Zeile 167-181, `speichere` Zeile 160-166, `privatEntnahme`
    Zeile 96-110 mit `kasseSaldoService.projiziereSaldo` +
    `assertSaldoMindestensMindestbestand`).
  - Validierung und Fehlermeldungen: `BelegService.createUmbuchung`
    (`BelegService.java:707-782`).
  - Controller-Aufbau, Auth und Fehler-Mapping: `B/controller/KasseShortcutController.java`
    (Zeile 51-107 sowie `saldoFehler` Zeile 222-227).
- Interfaces:
  - Produces `B/dto/KassenbuchungDto.java`:
    ```java
    public class KassenbuchungDto {
        /** Welche Kachel der Nutzer angetippt hat. */
        public enum Art {
            GELD_EINGENOMMEN, GELD_AUSGEGEBEN, VON_BANK_GEHOLT,
            ZUR_BANK_GEBRACHT, EIGENES_GELD_EINGELEGT, GELD_PRIVAT_ENTNOMMEN
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class CreateRequest {
            private String art;                 // Art.name()
            private LocalDate belegDatum;
            private BigDecimal betragBrutto;
            private BigDecimal mwstSatz;        // 19 | 7 | 0, nur bei EINGENOMMEN/AUSGEGEBEN
            private String gegenpartei;         // "Von wem" / "An wen"
            private String beschreibung;        // "Wofuer"
            private Long sachkontoId;
            private Long kostenstelleId;        // optional, schreibt einen 100-%-Split
            private Long ausgangsrechnungId;    // optional, nur bei GELD_EINGENOMMEN
            private Boolean keinBelegVorhanden; // nur bei GELD_AUSGEGEBEN
            private String grundOhneBeleg;      // Pflicht wenn keinBelegVorhanden
            private String notiz;
        }

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class OffeneRechnung {
            private Long id;
            private String dokumentNummer;
            private LocalDate datum;
            private BigDecimal bruttoBetrag;
            private String kundeName;
        }
    }
    ```
  - Produces `B/service/KassenbuchungService.java`:
    ```java
    @Transactional
    public Beleg buche(KassenbuchungDto.CreateRequest req, MultipartFile datei, Mitarbeiter ersteller);
    @Transactional(readOnly = true)
    public List<KassenbuchungDto.OffeneRechnung> offeneAusgangsrechnungen();
    ```
  - Produces Endpunkte in `KassenbuchungController`
    (`@RequestMapping("/api/buchhaltung/kassenbuch")`):
    * `POST /buchungen` — `multipart/form-data`, Teil `daten`
      (`application/json`, `CreateRequest`) und optionaler Teil `datei`
      (`MultipartFile`); Antwort `BelegDto.Response`.
    * `GET /offene-ausgangsrechnungen` — Liste `OffeneRechnung`.
  - Consumes: Task 1 (`BelegQuelle`, `Beleg.quelle/gegenpartei/ausgangsrechnungId`),
    Task 2 (`BelegPdfService`).
- Steps:
  - [ ] `KassenbuchungService` mit Constructor Injection auf `BelegRepository`,
        `SachkontoRepository`, `KostenstelleRepository`,
        `BelegKostenstellenAnteilRepository`, `ProjektDokumentRepository`,
        `KasseSaldoService`, `KassenbuchSchreibschutz`, `BelegAuditService`,
        `BelegPdfService`, `KasseEinstellungRepository`.
  - [ ] Ableitungstabelle je Kachel (eine private `switch`-Methode, keine
        `if`-Kette):

        | Art | BelegKategorie | Quelle | Sachkonto | Beleg-Datei |
        | --- | --- | --- | --- | --- |
        | `GELD_EINGENOMMEN` | KASSE_EINNAHME | QUITTUNG | Pflicht, Standard "8400 Erlöse 19 %" | Quittung-PDF |
        | `GELD_AUSGEGEBEN` (mit Datei) | KASSE_AUSGABE | SCAN | Pflicht | hochgeladene Datei |
        | `GELD_AUSGEGEBEN` (kein Beleg) | KASSE_AUSGABE | EIGENBELEG | Pflicht | Eigenbeleg-PDF, `mwstSatz` zwingend 0 |
        | `VON_BANK_GEHOLT` | KASSE_EINNAHME | TRANSFER | "1200 Bank-Kassen-Umbuchung" | Eigenbeleg-PDF |
        | `ZUR_BANK_GEBRACHT` | KASSE_AUSGABE | TRANSFER | "1200 Bank-Kassen-Umbuchung" | Eigenbeleg-PDF |
        | `EIGENES_GELD_EINGELEGT` | PRIVATEINLAGE | TRANSFER | `KasseEinstellung.privateinlageSachkonto`, sonst "1810 Privateinlage" | Eigenbeleg-PDF |
        | `GELD_PRIVAT_ENTNOMMEN` | PRIVATENTNAHME | TRANSFER | "1800 Privatentnahme" | Eigenbeleg-PDF |

        Die Sachkonten werden über `sachkontoRepository.findByNummer("8400"/"1200"/"1800"/"1810")`
        aufgelöst (dieselbe Technik wie
        `KasseShortcutController.lohnZahlung`, Zeile 121-127). Fehlt das Konto,
        HTTP 404 mit einem Satz, der sagt, welches Konto in den Stammdaten
        angelegt werden muss.
  - [ ] Validierung vor jeder Buchhaltungsregel (Reihenfolge wie in
        `createUmbuchung`: erst Eingabefehler, dann Monatsschutz, dann Saldo):
        `art` bekannt, `betragBrutto > 0`, `belegDatum` gesetzt, `gegenpartei`
        max. 120 Zeichen, `beschreibung` max. 500, `notiz` max. 1000,
        `grundOhneBeleg` max. 255 und Pflicht bei `keinBelegVorhanden`,
        `mwstSatz` aus {0, 7, 19}. Fehlermeldungen in Handwerker-Sprache
        ("Betrag fehlt oder ist nicht positiv").
  - [ ] `schreibschutz.assertMonatOffen(req.getBelegDatum())` aufrufen.
  - [ ] Mindestbestand: bei `kategorie.istKassenBewegung()`
        `kasseSaldoService.projiziereSaldo(null, null, kategorie, brutto)` und
        `assertSaldoMindestensMindestbestand(projiziert)` — die
        `KasseUnterdeckungException` fliegt durch bis zum Controller und wird
        dort zu HTTP 409 mit `message`, `projizierterSaldo`, `mindestbestand`
        (Format exakt wie `BelegController.java:259-263`).
  - [ ] Beleg anlegen: `status = VALIDIERT`, `kiAnalyseStatus = DONE`,
        `istUmbuchung = true` **nur** bei den vier Transfer-/Privat-Kacheln und
        bei `keinBelegVorhanden`; bei einer hochgeladenen Datei bleibt
        `istUmbuchung = false`. `quelle`, `gegenpartei`, `betragBrutto`,
        `mwstSatz`, `betragNetto` (über `schluesseleAuf`-Äquivalent:
        `netto = brutto / (1 + satz/100)`, `RoundingMode.HALF_UP`, Scale 2),
        `uploadDatum`, `uploadedBy`, `validiertAm`, `validiertVon` setzen.
  - [ ] Beleg-Datei: bei hochgeladener Datei denselben Validierungs- und
        Speicherpfad wie `BelegService.uploadBeleg` benutzen
        (`BelegService.java:159-205`: MIME-Whitelist, Endungs-Whitelist, 25 MB,
        Pfad-Traversal, UUID-Präfix, `berechneDateiHash`). **Nicht** duplizieren:
        eine paketsichtbare Methode
        `Beleg uebernehmeDatei(Beleg beleg, MultipartFile datei)` in
        `BelegService` wäre ein Umbau an fremdem Terrain — stattdessen in
        diesem Task eine eigene private Methode mit denselben Konstanten und
        einem Kommentar, der auf `BelegService.uploadBeleg` verweist.
        Sonst (Quittung/Eigenbeleg): `BelegPdfService` aufrufen und
        `gespeicherterDateiname`, `originalDateiname`, `mimeType`, `dateiHash`
        aus `ErzeugtesPdf` an den Beleg schreiben. **Damit hat jede Zeile im
        Kassenbuch eine Datei und einen Fingerabdruck.**
  - [ ] Kostenstelle: `kostenstelleId` gesetzt → `beleg.setKostenstelle(ks)`
        **und** einen `BelegKostenstellenAnteil` mit `prozent = 100` anlegen
        (`berechneAnteil(netto, brutto)` aufrufen, Vorbild
        `BelegService.persistiereSplits`, `BelegService.java:735ff.`).
  - [ ] Ausgangsrechnung: bei `GELD_EINGENOMMEN` mit `ausgangsrechnungId` das
        `ProjektGeschaeftsdokument` laden, `setBezahlt(true)` setzen und
        speichern; `beleg.setAusgangsrechnungId(id)`. Ist die Rechnung schon
        bezahlt oder existiert sie nicht: HTTP 400 mit klarem Satz.
  - [ ] Nach dem Speichern `auditService.protokolliereErfassung(gespeichert, ersteller, null)`
        aufrufen — genau wie `KasseShortcutService.speichere`. Ohne diesen
        Aufruf entstünde ein Kassenbuch-Eintrag ohne Protokollspur.
  - [ ] `offeneAusgangsrechnungen()` liefert
        `projektDokumentRepository.findOffeneGeschaeftsdokumente()`
        (`ProjektDokumentRepository.java:20-29`) gemappt auf `OffeneRechnung`,
        sortiert nach `rechnungsdatum` absteigend, auf 100 Einträge begrenzt.
        Kundenname über `g.getProjekt().getKunde()` — Lazy-Beziehung, deshalb
        die Methode `@Transactional(readOnly = true)` halten und `null`-sicher
        arbeiten.
  - [ ] `KassenbuchungController` anlegen: Auth über
        `belegService.findCaller(token, auth)` + `belegService.darfScannen(caller)`
        (Vorbild `KasseShortcutController.java:51-69`), `@RequestPart("daten")`
        + `@RequestPart(value = "datei", required = false)`. Fehler-Mapping:
        `KassenbuchGesperrtException` → 409 mit `message` + `hinweis`,
        `KasseUnterdeckungException` → 409 mit Saldo-Feldern,
        `IllegalArgumentException` → 400, sonst 500 mit Log.
  - [ ] `BelegController.createUmbuchung` (`BelegController.java:246-270`)
        auf den neuen Pfad umbiegen: `UmbuchungCreateRequest` in eine
        `KassenbuchungDto.CreateRequest` übersetzen (`belegKategorie` →
        `art`: KASSE_EINNAHME→`VON_BANK_GEHOLT`, KASSE_AUSGABE→`ZUR_BANK_GEBRACHT`,
        PRIVATEINLAGE→`EIGENES_GELD_EINGELEGT`, PRIVATENTNAHME→`GELD_PRIVAT_ENTNOMMEN`;
        BANK/KREDITKARTE bleiben auf dem alten `BelegService.createUmbuchung`,
        weil sie keine Kassenbewegung sind) und `kassenbuchungService.buche(...)`
        aufrufen. Der Endpunkt bleibt Wort für Wort kompatibel — Statuscodes und
        Response-Form ändern sich nicht. Ein Kommentar am Endpunkt hält fest,
        dass er nur noch Alias ist.
  - [ ] `T/service/KassenbuchungServiceTest.java` (Mockito, Vorbild
        `T/service/KasseShortcutServiceTest.java`): je ein Test pro Kachel, der
        Kategorie, Quelle, Sachkonto-Nummer, `istUmbuchung` und die gesetzte
        Belegdatei prüft; dazu:
        * "kein Beleg vorhanden" erzwingt `mwstSatz = 0` auch wenn 19 gesendet
          wurde,
        * Mindestbestand-Unterdeckung wirft `KasseUnterdeckungException`,
        * Buchung in einen abgeschlossenen Monat wirft
          `KassenbuchGesperrtException`,
        * `GELD_EINGENOMMEN` mit `ausgangsrechnungId` setzt `bezahlt = true`,
        * bei jeder erzeugten Buchung ist `gespeicherterDateiname` **und**
          `dateiHash` gesetzt,
        * `protokolliereErfassung` wird genau einmal aufgerufen.
  - [ ] `T/controller/KassenbuchungControllerTest.java` (`@WebMvcTest`, Vorbild
        `T/controller/KasseShortcutControllerTest.java`): 403 ohne Recht, 400
        bei fehlendem Betrag, 409 mit `projizierterSaldo`/`mindestbestand` bei
        Unterdeckung, 200 mit `BelegDto.Response` im Erfolgsfall,
        `GET /offene-ausgangsrechnungen` liefert die Liste.
- Gate:
  - `./mvnw -B test -Dtest=KassenbuchungServiceTest,KassenbuchungControllerTest,BelegServiceTest,BelegServiceKasseValidationTest`

---

### Task 4: KI-Analyse — Lieferant respektieren, Provenienz speichern, Zahlung ableiten

Behebt zwei der fünf gemeldeten Fehler: der am Handy gewählte Lieferant wird
überschrieben, und "Rechnung"/"bar bezahlt" landet ungefragt in den offenen
Posten.

- Branch: `kasse/task-4-ki-analyse`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-4`
- Files:
  - `B/service/BelegKiAnalyseService.java`
  - `T/service/BelegKiAnalyseServiceTest.java`
- Vorbild: die Datei selbst — der Übernahme-Block (Zeile 116-158) und
  `erstelleEingangsrechnungFallsRechnung` (Zeile 336-410).
- Interfaces:
  - Produces: keine neue öffentliche Signatur. `ableitenBelegKategorie`
    (`BelegKiAnalyseService.java:302-313`) wird **entfernt** und durch
    `ZahlungsartMapper.zuKategorie(...)` ersetzt; wer sie im Test aufruft
    (`BelegKiAnalyseServiceTest`), zieht mit.
  - Consumes: Task 1 (`ZahlungsartMapper`, `Beleg.kiZahlungsart`,
    `kiBelegdatum`, `kiBetragBrutto`).
- Steps:
  - [ ] **Provenienz sichern** (neuer Block direkt nach dem Übernehmen der
        Geschäftsdaten, heute Zeile 117-129): `beleg.setKiZahlungsart(ergebnis.getZahlungsart())`,
        `beleg.setKiBelegdatum(ergebnis.getDokumentDatum())`,
        `beleg.setKiBetragBrutto(ergebnis.getBetragBrutto())`. Diese drei Felder
        sind die unveränderte KI-Lesung und werden später nie überschrieben.
  - [ ] **Zahlungsart vereinheitlichen:** Zeile 122
        (`beleg.setZahlungsart(ergebnis.getZahlungsart())`) ersetzen durch
        `beleg.setZahlungsart(ZahlungsartMapper.zuStammdaten(ergebnis.getZahlungsart()))`.
        Ist das Ergebnis `null` (Code `SONSTIGE`/leer), bleibt das Feld leer —
        der Nutzer wählt beim Prüfen.
  - [ ] **Lieferant-Fix** (Zeile 143-144). Heute:
        ```java
        beleg.setKiVorgeschlagenerLieferant(
                beleg.getLieferant() != null ? beleg.getLieferant().getLieferantenname() : null);
        ```
        Das überschreibt die KI-Lesung mit dem Nutzerwert, sobald am Handy ein
        Lieferant gewählt wurde — die Lesung geht spurlos verloren. Neu:
        ```java
        // Immer die reine KI-Lesung, nie der schon gesetzte Nutzerwert.
        // Sonst sieht der Buchhalter am PC nie, dass die KI etwas anderes
        // gelesen hat als am Handy ausgewaehlt wurde.
        if (ergebnis.getLieferantName() != null && !ergebnis.getLieferantName().isBlank()) {
            beleg.setKiVorgeschlagenerLieferant(ergebnis.getLieferantName().trim());
        }
        ```
        Der Block darüber (Zeile 136-142), der einen **nicht** gesetzten
        Lieferanten aus den Stammdaten zuordnet, bleibt unverändert.
  - [ ] **Kategorie-Ableitung** (Zeile 146-157): `ableitenBelegKategorie`
        löschen und durch
        `ZahlungsartMapper.zuKategorie(beleg.getZahlungsart(), true)` ersetzen
        (`richtungAusgabe = true`, weil ein gescannter Lieferantenbeleg
        praktisch immer eine Ausgabe ist). Die Bedingung "nur wenn Kategorie
        null oder UNZUGEORDNET" bleibt.
  - [ ] **`bereitsGezahlt` ableiten** in `erstelleEingangsrechnungFallsRechnung`
        (Zeile 388, heute
        `lgd.setBereitsGezahlt(Boolean.TRUE.equals(ergebnis.getBereitsGezahlt()))`):
        ```java
        boolean gezahltLautZahlungsart = ZahlungsartMapper.giltAlsBezahlt(beleg.getKiZahlungsart());
        lgd.setBereitsGezahlt(Boolean.TRUE.equals(ergebnis.getBereitsGezahlt()) || gezahltLautZahlungsart);
        ```
        Damit taucht eine bar bezahlte Rechnung nicht mehr in den offenen
        Posten auf. Die Entscheidung im Prüfen-Dialog überschreibt das später
        (Task 5).
  - [ ] **Kommentar-Fix** Zeile 184: "High-Confidence-Treffer (>=0.80)" ist
        falsch — `BelegKiKostenkontoService.AUTO_APPLY_THRESHOLD` ist
        `0.95` (`BelegKiKostenkontoService.java:66`). Text korrigieren.
  - [ ] `T/service/BelegKiAnalyseServiceTest.java` erweitern:
        * am Handy gewählter Lieferant bleibt am Beleg, `kiVorgeschlagenerLieferant`
          trägt trotzdem die abweichende KI-Lesung ("OBI" gewählt, "Hornbach"
          gelesen),
        * ohne vorgewählten Lieferanten und mit Stammdaten-Treffer wird der
          Lieferant gesetzt **und** `kiVorgeschlagenerLieferant` gefüllt,
        * `zahlungsart` wird auf die Stammdaten-Bezeichnung gemappt
          (`UEBERWEISUNG` → "Überweisung"),
        * `SONSTIGE` lässt `zahlungsart` leer,
        * `ki_zahlungsart`/`ki_belegdatum`/`ki_betrag_brutto` tragen die
          Rohlesung,
        * `BAR` + Dokumenttyp RECHNUNG legt eine Eingangsrechnung mit
          `bereitsGezahlt = true` an; `UEBERWEISUNG` mit `false`.
        Vorhandene Fälle unverändert lassen, nur die Verkabelung nachziehen.
- Gate:
  - `./mvnw -B test -Dtest=BelegKiAnalyseServiceTest,ZahlungsartMapperTest`

---

### Task 5: BelegVorschlagService, Zahlungsstatus und DTO-Anbindung

Behebt die Punkte "Kostenstellen-Vorschläge kommen nie" (Fallback ohne KI) und
"Rechnung landet ungefragt in den offenen Posten" (Nutzerentscheidung ist
maßgeblich). Beide hängen an `BelegService.java`, deshalb ein Task — sonst
kollidieren zwei Agenten auf derselben Datei.

- Branch: `kasse/task-5-vorschlag-zahlstatus`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-5`
- Files:
  - `B/service/BelegVorschlagService.java` (neu)
  - `B/repository/BelegRepository.java`
  - `B/service/BelegService.java`
  - `T/service/BelegVorschlagServiceTest.java` (neu)
  - `T/service/BelegServiceZahlungsstatusTest.java` (neu)
- Vorbild:
  - Repository-Query mit `LEFT JOIN FETCH` und `Pageable`:
    `BelegRepository.findAehnlicheBelegeByLieferant` (Zeile 98-103).
  - Feld-für-Feld-Update mit Protokoll: `BelegService.updateBeleg`
    (Zeile 306-462), Helfer `merke` (Zeile 538-546).
  - DTO-Mapping: `BelegService.toDto(Beleg, boolean)` (Zeile 1053-1144).
- Interfaces:
  - Produces `B/service/BelegVorschlagService.java`:
    ```java
    @Service
    @RequiredArgsConstructor
    public class BelegVorschlagService {
        /** Sachkonto- und Kostenstellen-Vorschlag fuer einen Beleg.
         *  Reihenfolge: KI (falls vorhanden) > Standardkostenstelle des
         *  Lieferanten > Historie der letzten 20 geprueften Belege. */
        @Transactional(readOnly = true)
        public Vorschlaege ermittle(Beleg beleg);

        public record Vorschlaege(Vorschlag sachkonto, Vorschlag kostenstelle) {}
        public record Vorschlag(Long id, String nummer, String bezeichnung,
                                Quelle quelle, String begruendung) {}
        public enum Quelle { KI, HISTORIE, LIEFERANT_STANDARD }
    }
    ```
  - Produces in `BelegRepository`:
    ```java
    @Query("SELECT b FROM Beleg b LEFT JOIN FETCH b.sachkonto LEFT JOIN FETCH b.kostenstelle "
         + "WHERE b.lieferant.id = :lieferantId "
         + "  AND b.status = org.example.kalkulationsprogramm.domain.BelegStatus.VALIDIERT "
         + "  AND b.id <> :ausgenommenId "
         + "ORDER BY b.belegDatum DESC, b.id DESC")
    List<Beleg> findLetzteGepruefteByLieferant(@Param("lieferantId") Long lieferantId,
                                               @Param("ausgenommenId") Long ausgenommenId,
                                               Pageable pageable);
    ```
  - Consumes: Task 1 (`BelegDto.VorschlagDto`, `UpdateRequest.zahlungsstatus`,
    `Beleg.kiKostenkontoHinweis`, `quelle`, `gegenpartei`).
- Steps:
  - [ ] `BelegVorschlagService` anlegen (Injection: `BelegRepository`,
        `SachkontoRepository`, `KostenstelleRepository`).
        Ablauf in `ermittle(Beleg)`:
        1. **Sachkonto:** `beleg.getKiVorgeschlagenerSachkontoId()` gesetzt und
           Konto aktiv → `Quelle.KI`, Begründung
           `"Die KI schlägt das vor – " + beleg.getKiKostenkontoBegruendung()`
           (fehlt die Begründung: "Die KI schlägt das vor.").
           Sonst häufigstes Sachkonto der letzten 20 geprüften Belege desselben
           Lieferanten → `Quelle.HISTORIE`, Begründung
           `"Bei " + lieferantName + " hast du zuletzt " + n + "-mal dieses Konto genommen."`.
           Sonst `null`.
        2. **Kostenstelle:** KI-Vorschlag (`kiVorgeschlagenerKostenstelleId`,
           aktiv) → `Quelle.KI`. Sonst `lieferant.getStandardKostenstelle()`
           (`Lieferanten.java:85-86`), falls aktiv → `Quelle.LIEFERANT_STANDARD`,
           Begründung `"Beim Lieferanten " + name + " ist das als Standard hinterlegt."`.
           Sonst häufigste Kostenstelle der Historie → `Quelle.HISTORIE`.
           Sonst `null`.
        3. Ohne Lieferant am Beleg entfällt Schritt 2/3 der Historie —
           dann bleibt nur der KI-Vorschlag.
        Häufigkeit über `Collectors.groupingBy(..., counting())`, bei
        Gleichstand gewinnt der jüngste Beleg (Liste ist bereits absteigend
        sortiert, deshalb `LinkedHashMap`-stabil auswerten).
  - [ ] `BelegService`: `BelegVorschlagService` per Constructor Injection
        ergänzen. **Achtung Charakterisierungstests:** alle bestehenden
        `BelegService`-Tests, die den Konstruktor direkt aufrufen
        (`BelegServiceTest`, `BelegServiceKasseValidationTest`,
        `KassenbuchFestschreibungTest`), brauchen den neuen Parameter. Nur die
        Verkabelung anpassen, keine erwartete Zahl.
  - [ ] `BelegService.toDto(b, mitPositionen)` (Zeile 1053) erweitern:
        * `quelle`, `gegenpartei`, `ausgangsrechnungId`, `kiZahlungsart`,
          `kiBelegdatum`, `kiBetragBrutto`, `kiKostenkontoHinweis` immer
          durchreichen (billig, kein zusätzlicher Query).
        * `eingangsrechnungBezahlt` / `eingangsrechnungBezahltAm` aus dem
          bereits geladenen `lieferantDokumentRepository.findByBelegId(...)`
          ziehen — der Lookup existiert schon (Zeile 1060-1066), nur das
          Ergebnis wird heute weggeworfen. **Kein zweiter Query.**
        * `vorschlagSachkonto` / `vorschlagKostenstelle` **nur bei
          `mitPositionen == true`** füllen (Detail-Sicht). In der Liste bleibt
          beides `null` — sonst hätte `listBelege` ein N+1-Problem mit bis zu
          drei Queries je Zeile. Diesen Grund als Kommentar hinterlegen.
  - [ ] `BelegService.updateBeleg` (Zeile 306): nach dem Zahlungsart-Block
        (Zeile 398-401) einen neuen Block ergänzen:
        ```java
        // Die Entscheidung im Pruef-Dialog ist massgeblich und ueberschreibt,
        // was KI oder Lieferanten-Vorauskasse vorher gesetzt haben.
        if (req.getZahlungsstatus() != null) { ... }
        ```
        Verhalten: `"BEZAHLT"` → auf der verknüpften
        `LieferantGeschaeftsdokument` (`lieferantDokumentRepository.findByBelegId(id)`
        → `getGeschaeftsdaten()`) `bezahlt = true` und
        `bezahltAm = req.getBezahltAm() != null ? req.getBezahltAm() : LocalDate.now()`;
        `"OFFEN"` → `bezahlt = false`, `bezahltAm = null`. Änderung über
        `merke(aenderungen, "Bezahlt", alt, neu)` protokollieren. Gibt es keine
        verknüpfte Eingangsrechnung, passiert nichts (kein Fehler) — der Beleg
        ist dann kein offener Posten.
  - [ ] `T/service/BelegVorschlagServiceTest.java` (Mockito): KI schlägt vor →
        `Quelle.KI`; kein KI-Vorschlag, aber Standardkostenstelle →
        `LIEFERANT_STANDARD`; weder KI noch Standard, aber Historie →
        `HISTORIE` mit dem häufigsten Konto; Reihenfolge KI > Standard >
        Historie an einem Beleg, der alle drei hätte; Beleg ohne Lieferant →
        nur KI oder `null`; deaktiviertes Konto im KI-Vorschlag fällt auf die
        Historie zurück. Alle Lieferanten heißen "Musterbaustoffe GmbH".
  - [ ] `T/service/BelegServiceZahlungsstatusTest.java`: `BEZAHLT` schreibt
        `bezahlt = true` und das Datum auf die Eingangsrechnung; `BEZAHLT` ohne
        `bezahltAm` nimmt heute; `OFFEN` setzt zurück; ohne verknüpfte
        Eingangsrechnung wirft nichts; die Änderung steht im Protokolltext.
- Gate:
  - `./mvnw -B test -Dtest=BelegVorschlagServiceTest,BelegServiceZahlungsstatusTest,BelegServiceTest,BelegServiceKasseValidationTest,KassenbuchFestschreibungTest`

---

### Task 6: KI-Kostenkonto — Grund statt Schweigen, Belegbild in den Prompt

- Branch: `kasse/task-6-ki-kostenkonto`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-6`
- Files:
  - `B/service/BelegKiKostenkontoService.java`
  - `T/service/BelegKiKostenkontoServiceTest.java`
- Vorbild: die Datei selbst; für das Bild im Prompt
  `GeminiDokumentAnalyseService.rufGeminiApiMitPrompt`
  (`GeminiDokumentAnalyseService.java:546-580`, dort wird
  `parts.addObject().putObject("inline_data")` mit `mime_type` und
  Base64-`data` gefüllt).
- Interfaces:
  - Produces: `klassifiziereBeleg(Beleg)` bleibt in der Signatur, schreibt aber
    zusätzlich `beleg.setKiKostenkontoHinweis(String)` in **jedem** Abbruchpfad.
  - Consumes: Task 1 (`Beleg.kiKostenkontoHinweis`).
- Steps:
  - [ ] In `klassifiziereBeleg` (Zeile 101-171) an allen vier stillen
        Abbruchstellen einen Hinweissatz setzen (max. 255 Zeichen,
        Handwerker-Sprache, kein Fachjargon):
        | Stelle | heute | neuer Hinweis |
        | --- | --- | --- |
        | Zeile 107, Kostenstelle schon gesetzt | `return` | "Es war schon eine Baustelle zugeordnet – die KI hat nicht nachgeschaut." |
        | Zeile 114, kein Gemini-Key | `return` | "Kein KI-Schlüssel hinterlegt – das Programm kann nichts vorschlagen." |
        | Zeile 127, leere Antwort | `break` | "Die KI hat nicht geantwortet. Bitte von Hand wählen." |
        | Zeile 165, keine `finale_zuordnung` nach 6 Runden | `return` | "Die KI konnte sich nicht entscheiden. Bitte von Hand wählen." |
        Zusätzlich in `wendeErgebnisAn` (Zeile 286): bei Confidence unter 0,95
        `"KI unsicher (" + confidence + ") – bitte prüfen."`, bei Erfolg mit
        Auto-Übernahme den Hinweis auf `null` setzen.
  - [ ] `buildInitialPrompt` (Zeile 635) um die extrahierten Positionen
        erweitern: aus `beleg.getKiExtraktionJson()` die Positionsliste lesen
        (falls vorhanden) und als kompakte Aufzählung "Menge × Bezeichnung —
        Betrag" (max. 15 Zeilen) in den Prompt hängen. Kein zusätzlicher
        Datenbankzugriff.
  - [ ] Belegbild mitgeben: neue private Methode
        `ObjectNode userTurnMitBild(String text, Path datei, String mimeType)`,
        die neben dem `text`-Part einen `inline_data`-Part mit Base64 anhängt
        (Vorbild `GeminiDokumentAnalyseService.java:561-576`). Sie wird für den
        **ersten** Turn benutzt, wenn `beleg.getGespeicherterDateiname()`
        gesetzt und die Datei kleiner als 8 MB ist. PDF: die Datei wird
        unverändert als `application/pdf` mitgegeben (Gemini liest PDFs; eine
        eigene Seiten-Extraktion wäre neue Abhängigkeit und ist hier nicht
        nötig). Fehlt die Datei oder ist sie zu groß: still auf den bisherigen
        reinen Text-Turn zurückfallen und das im Log vermerken.
        `@Value("${upload.path:uploads}")` in den Service aufnehmen.
  - [ ] Kommentar Zeile 303 (`// 1. Confidence >= 0.95 (siehe AUTO_APPLY_THRESHOLD).`)
        prüfen und, falls nötig, an den tatsächlichen Wert angleichen.
  - [ ] `T/service/BelegKiKostenkontoServiceTest.java` erweitern (bestehende
        Fälle unverändert lassen): vier Tests für die vier Abbruchpfade, jeder
        prüft den **konkreten** Hinweistext und dass Sachkonto/Kostenstelle
        `null` bleiben; ein Test, dass bei gesetztem Bild ein `inline_data`-Part
        im ersten Turn steckt (paketsichtbare Methode direkt aufrufen, kein
        HTTP); ein Test, dass eine fehlende Datei nicht zum Fehler führt.
        `SystemSettingsService` wird gemockt (Key vorhanden / leer).
- Gate:
  - `./mvnw -B test -Dtest=BelegKiKostenkontoServiceTest`
---

### Task 7: DatevExportService — EXTF-700-Buchungsstapel als reine Funktion

- Branch: `kasse/task-7-datev-export`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-7`
- Files:
  - `B/service/DatevExportService.java` (neu)
  - `B/service/BuchungssatzAbleitung.java`
  - `B/service/Wirtschaftsjahr.java` (neu, reine Funktion)
  - `T/service/DatevExportServiceTest.java` (neu)
  - `T/service/BuchungssatzAbleitungTest.java`
  - `T/service/WirtschaftsjahrTest.java` (neu)
  - `src/test/resources/datev/buchungsstapel-golden.csv` (neu)
- Vorbild: `B/service/SteuerpruefungZ3ExportService.java` (CSV-Bau mit
  `StringBuilder`, `esc()`-Helfer Zeile 200ff.) und
  `B/service/BuchungssatzAbleitung.java` (bestehende `ableiten`-Methode,
  Zeile 47-72).
- Interfaces:
  - Produces `B/service/Wirtschaftsjahr.java`:
    ```java
    public final class Wirtschaftsjahr {
        /** WJ-Beginn fuer den Exportmonat. Ist der Monat >= Beginnmonat, faellt
         *  der WJ-Beginn in dasselbe Jahr, sonst ins Vorjahr. */
        public static LocalDate beginn(YearMonth exportMonat, int beginnMonat);
    }
    ```
  - Produces in `BuchungssatzAbleitung` (zusätzlich, `ableiten` bleibt
    unverändert — das PDF hängt daran):
    ```java
    /** Kontonummern statt Labels, fuer den DATEV-Export. */
    public static Konten ableitenKonten(Beleg beleg, KasseEinstellung einstellung);

    public record Konten(String sollKontoNr, String habenKontoNr) {}
    ```
  - Produces `B/service/DatevExportService.java`:
    ```java
    @Service
    @RequiredArgsConstructor
    public class DatevExportService {
        /** Kompletter EXTF-Buchungsstapel als Text (Kopfzeile, Spaltenzeile,
         *  je Buchung eine Zeile; bei Kostenstellen-Split eine Zeile je Anteil). */
        public String erzeugeCsv(Parameter p);

        /** Derselbe Text, kodiert in Windows-1252 (DATEV-Standard). */
        public byte[] erzeugeCsvBytes(Parameter p);

        public record Parameter(YearMonth monat,
                                List<Beleg> belege,
                                Map<Long, List<BelegKostenstellenAnteil>> splitsJeBeleg,
                                KasseEinstellung einstellung,
                                String erstellerName,
                                String firmenname,
                                LocalDateTime erzeugtAm,
                                boolean trotzLueckenExportieren) {}
    }
    ```
  - Consumes: Task 1 (`KasseEinstellung`-Felder, `Beleg.quelle`).
- Steps:
  - [ ] `Wirtschaftsjahr.beginn` implementieren: `beginnMonat` außerhalb 1-12
        wird auf 1 geklemmt; `M >= beginnMonat` → `LocalDate.of(J, beginnMonat, 1)`,
        sonst `LocalDate.of(J - 1, beginnMonat, 1)`.
        `WirtschaftsjahrTest`: Januar-Beginn (2026-08 → 2026-01-01), Juli-Beginn
        mit Monat August (2026-08 → 2026-07-01), Juli-Beginn mit Monat März
        (2026-03 → 2025-07-01), Jahreswechsel (2026-01 bei Beginn 1 → 2026-01-01),
        ungültiger Beginnmonat 0 und 13.
  - [ ] `BuchungssatzAbleitung.ableitenKonten` (Entscheidung 2 des
        Orchestrators). Kasse = `einstellung.getKassenkontoNummer()` (Standard
        "1000"), Bank = `getBankkontoNummer()` (Standard "1200"),
        Sachkonto = `beleg.getSachkonto().getNummer()`. Regeln:

        | Kategorie | Soll | Haben |
        | --- | --- | --- |
        | KASSE_EINNAHME, `quelle == TRANSFER` (von der Bank geholt) | Kasse | Bank |
        | KASSE_EINNAHME sonst | Kasse | Sachkonto (Ertrag) |
        | KASSE_AUSGABE, `quelle == TRANSFER` (zur Bank gebracht) | Bank | Kasse |
        | KASSE_AUSGABE sonst | Sachkonto (Aufwand) | Kasse |
        | PRIVATEINLAGE | Kasse | Sachkonto, sonst "1810" |
        | PRIVATENTNAHME | Sachkonto, sonst "1800" | Kasse |
        | BANK, KREDITKARTE, SONSTIGER_BELEG, UNZUGEORDNET | `null` | `null` |

        Fehlt das Sachkonto, steht dort `null` (nicht "?") — der Writer schreibt
        dann ein leeres Feld. Die Standardkonten 1800/1810 stammen aus
        `V303__sachkonto.sql:87-89`.
        `BuchungssatzAbleitungTest` um je einen Fall pro Zeile erweitern, dazu
        einen Fall mit abweichenden Einstellungen (Kasse "1600", Bank "1210").
        Die bestehenden `ableiten`-Tests bleiben **unverändert**.
  - [ ] `DatevExportService.erzeugeCsv` bauen. Zeilenende **CRLF** (`\r\n`),
        Trenner `;`, keine abschließende Leerzeile über das Dateiende hinaus.
  - [ ] **Kopfzeile** exakt nach Spec, 22 Felder:
        ```
        "EXTF";700;21;"Buchungsstapel";13;<erzeugtAm>;;"HW";"<Ersteller>";;<Beraternummer>;<Mandantennummer>;<WJ-Beginn>;4;<Datum von>;<Datum bis>;"Kasse <Monat>";;1;;<Festschreibung>;"EUR"
        ```
        * `<erzeugtAm>`: `yyyyMMddHHmmssSSS` aus `Parameter.erzeugtAm`.
        * `<Ersteller>`: `Parameter.erstellerName`, in Anführungszeichen, auf
          25 Zeichen gekürzt.
        * `<Beraternummer>`/`<Mandantennummer>`: ohne Anführungszeichen, leer
          wenn nicht gepflegt (DATEV erlaubt das).
        * `<WJ-Beginn>`: `Wirtschaftsjahr.beginn(monat, einstellung.getWirtschaftsjahrBeginnMonat())`
          als `yyyyMMdd`.
        * `<Datum von>`/`<Datum bis>`: erster und letzter Tag des Exportmonats
          als `yyyyMMdd`.
        * `"Kasse <Monat>"`: z.B. `"Kasse 2026-08"`.
        * `<Festschreibung>`: `1`, wenn **alle** Belege der Menge
          `festgeschrieben` sind, sonst `0`.
  - [ ] **Spaltenzeile**: genau 125 Felder, jedes in doppelte Anführungszeichen
        gesetzt, in dieser Reihenfolge:

        1 Umsatz (ohne Soll/Haben-Kz) · 2 Soll/Haben-Kennzeichen · 3 WKZ Umsatz ·
        4 Kurs · 5 Basis-Umsatz · 6 WKZ Basis-Umsatz · 7 Konto ·
        8 Gegenkonto (ohne BU-Schlüssel) · 9 BU-Schlüssel · 10 Belegdatum ·
        11 Belegfeld 1 · 12 Belegfeld 2 · 13 Skonto · 14 Buchungstext ·
        15 Postensperre · 16 Diverse Adressnummer · 17 Geschäftspartnerbank ·
        18 Sachverhalt · 19 Zinssperre · 20 Beleglink ·
        21-36 die acht Paare **Beleginfo - Art N** / **Beleginfo - Inhalt N**
        für N = 1…8 (also 21 "Beleginfo - Art 1", 22 "Beleginfo - Inhalt 1",
        23 "Beleginfo - Art 2", … 35 "Beleginfo - Art 8", 36 "Beleginfo - Inhalt 8") ·
        37 KOST1 - Kostenstelle · 38 KOST2 - Kostenstelle · 39 Kost-Menge ·
        40 EU-Land u. UStID (Bestimmung) · 41 EU-Steuersatz (Bestimmung) ·
        42 Abw. Versteuerungsart · 43 Sachverhalt L+L ·
        44 Funktionsergänzung L+L · 45 BU 49 Hauptfunktionstyp ·
        46 BU 49 Hauptfunktionsnummer · 47 BU 49 Funktionsergänzung ·
        48-87 die zwanzig Paare **Zusatzinformation - Art N** /
        **Zusatzinformation- Inhalt N** für N = 1…20 (48 "Zusatzinformation - Art 1",
        49 "Zusatzinformation- Inhalt 1", … 86 "Zusatzinformation - Art 20",
        87 "Zusatzinformation- Inhalt 20" — das fehlende Leerzeichen vor dem
        Bindestrich in "Zusatzinformation- Inhalt" ist im DATEV-Format so
        vorgesehen und wird **nicht** korrigiert) ·
        88 Stück · 89 Gewicht · 90 Zahlweise · 91 Forderungsart ·
        92 Veranlagungsjahr · 93 Zugeordnete Fälligkeit · 94 Skontotyp ·
        95 Auftragsnummer · 96 Buchungstyp · 97 USt-Schlüssel (Anzahlungen) ·
        98 EU-Land (Anzahlungen) · 99 Sachverhalt L+L (Anzahlungen) ·
        100 EU-Steuersatz (Anzahlungen) · 101 Erlöskonto (Anzahlungen) ·
        102 Herkunft-Kz · 103 Buchungs GUID · 104 KOST-Datum ·
        105 SEPA-Mandatsreferenz · 106 Skontosperre · 107 Gesellschaftername ·
        108 Beteiligtennummer · 109 Identifikationsnummer · 110 Zeichnernummer ·
        111 Postensperre bis · 112 Bezeichnung SoBil-Sachverhalt ·
        113 Kennzeichen SoBil-Buchung · 114 Festschreibung · 115 Leistungsdatum ·
        116 Datum Zuord. Steuerperiode · 117 Fälligkeit · 118 Generalumkehr (GU) ·
        119 Steuersatz · 120 Land · 121 Abrechnungsreferenz · 122 BVV-Position ·
        123 EU-Land u. UStID (Ursprung) · 124 EU-Steuersatz (Ursprung) ·
        125 Abw. Skontokonto

        Die beiden Wiederholblöcke im Code als Schleife erzeugen, nicht
        abtippen. Ein Test sichert `spaltenzeile.split(";", -1).length == 125`.
  - [ ] **Buchungszeilen.** Menge: alle übergebenen Belege mit
        `belegKategorie.istKassenBewegung()`, sortiert nach `laufendeNummer`,
        dann `belegDatum`, dann `id`. Ein Beleg mit Kostenstellen-Splits
        erzeugt **eine Zeile je Anteil** (`berechneterBetrag` als Umsatz), sonst
        genau eine Zeile mit dem vollen Brutto. Gefüllt werden:

        | Spalte | Wert |
        | --- | --- |
        | 1 Umsatz | Bruttobetrag, immer positiv, **Komma** als Dezimaltrenner, zwei Nachkommastellen, kein Tausendertrenner |
        | 2 Soll/Haben-Kz | immer `S` (Entscheidung 2: Konto = Soll, Gegenkonto = Haben) |
        | 3 WKZ Umsatz | `EUR` |
        | 7 Konto | `ableitenKonten(...).sollKontoNr()`, leer wenn null |
        | 8 Gegenkonto | `ableitenKonten(...).habenKontoNr()`, leer wenn null |
        | 9 BU-Schlüssel | siehe Tabelle unten |
        | 10 Belegdatum | `TTMM` (vierstellig, führende Nullen, z.B. `0208`) |
        | 11 Belegfeld 1 | `laufendeNummer`, leer wenn der Monat noch offen ist |
        | 14 Buchungstext | `beschreibung`, auf 60 Zeichen gekürzt; bei `trotzLueckenExportieren` und fehlendem Sachkonto mit Präfix `PRÜFEN: ` (Gesamtlänge bleibt 60) |
        | 37 KOST1 | Bezeichnung der Kostenstelle, auf 36 Zeichen gekürzt |
        | 114 Festschreibung | `1` wenn `beleg.festgeschrieben`, sonst `0` |

        Alle übrigen 118 Spalten bleiben leer (nur `;`).

        **BU-Schlüssel (Spalte 9):**
        | Fall | Wert |
        | --- | --- |
        | KASSE_AUSGABE, MwSt 19 % | `9` |
        | KASSE_AUSGABE, MwSt 7 % | `8` |
        | KASSE_EINNAHME, MwSt 19 % | `3` |
        | KASSE_EINNAHME, MwSt 7 % | `2` |
        | MwSt 0 %, Privat, Transfer (`quelle == TRANSFER`) | leer |
  - [ ] **Feld-Escaping:** Textfelder (14 Buchungstext, 37 KOST1) in doppelte
        Anführungszeichen setzen; enthaltene `"` verdoppeln; `;`, `\r` und `\n`
        vorher durch Leerzeichen ersetzen. Zahlenfelder und Kontonummern **ohne**
        Anführungszeichen. Vorbild `SteuerpruefungZ3ExportService.esc`.
  - [ ] **Zeichensatz: Windows-1252.** `erzeugeCsvBytes` kodiert mit
        `Charset.forName("windows-1252")` und einem `CharsetEncoder` mit
        `CodingErrorAction.REPLACE`. **Begründung, die als Kommentar in die
        Klasse gehört:** DATEV importiert sowohl Windows-1252 als auch UTF-8
        mit BOM; Windows-1252 ist der DATEV-Standard und der Weg mit den
        wenigsten Überraschungen — kein BOM, das ältere DATEV-Versionen als
        Teil des ersten Feldes lesen, und Umlaute in Buchungstexten kommen
        ohne Sonderbehandlung an. Zeichen außerhalb von Windows-1252 (z.B. ein
        Emoji in einer Notiz) werden ersetzt statt den Export zu sprengen.
  - [ ] `T/service/DatevExportServiceTest.java`:
        * **Golden-File-Test:** ein fester Datensatz (drei Belege — Bareinnahme
          19 %, Barausgabe 7 % mit Kostenstelle, Bank→Kasse-Transfer; dazu ein
          Beleg mit zwei Splits) wird gegen
          `src/test/resources/datev/buchungsstapel-golden.csv` verglichen.
          Die Golden-Datei liegt als **UTF-8** im Repo (lesbar im Diff); der
          Test vergleicht den **String** aus `erzeugeCsv`. `erzeugtAm` wird
          fest vorgegeben, damit die Datei stabil bleibt.
        * `erzeugeCsvBytes` → `new String(bytes, "windows-1252")` ergibt exakt
          denselben String (Umlaut-Roundtrip mit "Bürobedarf" und "Überweisung").
        * Jede Datenzeile hat genau 125 Felder (`split(";", -1)`), die
          Spaltenzeile auch.
        * Umsatz nutzt Komma: `12,34`, nie `12.34`.
        * Belegdatum ist `TTMM`: der 2. August wird `0208`.
        * S/H-Kennzeichen ist in **allen** Zeilen `S`.
        * BU-Schlüssel je Fall aus der Tabelle oben.
        * Ein Buchungstext mit `"`, `;` und Zeilenumbruch bricht die Datei
          nicht (Anführungszeichen verdoppelt, Trenner ersetzt).
        * Fehlendes Sachkonto: Konto-Feld leer, mit
          `trotzLueckenExportieren = true` steht `PRÜFEN: ` vor dem Buchungstext.
        * Festschreibung: 1 nur bei festgeschriebenen Belegen; Kopfzeilen-Feld
          21 ist 0, sobald ein Beleg noch offen ist.
        * Ein Beleg mit zwei Splits erzeugt zwei Zeilen, deren Umsatzsummen den
          Bruttobetrag ergeben.
        **Gegenprobe:** eine Spalte testweise verschieben, der Golden-Test muss
        rot werden.
- Gate:
  - `./mvnw -B test -Dtest=DatevExportServiceTest,BuchungssatzAbleitungTest,WirtschaftsjahrTest`

---

### Task 11: Kassenbuch als Journal, Erklärkasten, Hilfeseite (Frontend)

- Branch: `kasse/task-11-kassenbuch-journal`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-11`
- Files:
  - `PC/components/kasse/KassenbuchJournal.tsx`
  - `PC/components/kasse/KassenbuchTab.tsx`
  - `PC/components/kasse/KasseErklaerkasten.tsx` (neu)
  - `PC/components/kasse/KasseAnleitungDialog.tsx` (neu)
  - `PC/components/kasse/journalSaldo.ts` (neu)
  - `PC/components/kasse/journalSaldo.test.ts` (neu)
  - `docs/KASSE_ANLEITUNG.md` (neu)
  - `PCE/kassenbuch-journal.spec.ts` (neu)
  - `PCE/kasse-refactoring.spec.ts` (übernommen von Task 9 — die Spec sichert die alten T-Konto-Texte zu und muss mitgezogen werden)
- Vorbild:
  - Tabellenkopf, Zeilen und Summenfuß: der bestehende T-Konto-Block in
    `KassenbuchJournal.tsx` (nach Task 9; vorher
    `BelegeKasseEditor.tsx:902-1010`) — Farben, Rahmen und `tabular-nums`
    übernehmen, nur die Struktur wird einspaltig.
  - Große Zahl oben plus Knopfleiste: `KasseShortcuts.tsx:101-127`
    (Kassenstand als `text-2xl font-bold`) und
    `PC/components/kasse/KassenbuchAbschlussLeiste.tsx`.
  - Einklappbarer Block: `BelegeKasseEditor.tsx:1585-1600` ("Mehr Details"
    mit `aria-expanded` und Chevron).
  - Kachel: `KpiTile` (nach Task 9 in `KassenbuchJournal.tsx`).
- Interfaces:
  - Produces `PC/components/kasse/journalSaldo.ts`:
    ```ts
    export interface JournalZeile extends KassenBewegung {
        anzeigeNummer: number | null;
        vorlaeufigeNummer: number;
        einnahme: number | null;   // > 0 oder null
        ausgabe: number | null;    // > 0 oder null
    }
    /** Baut die Journal-Zeilen aus der Server-Antwort. Bestand danach kommt
     *  vom Server (saldoNachher) und wird NICHT neu gerechnet. */
    export function baueJournal(bewegungen: KassenBewegung[], suche: string): JournalZeile[];
    ```
  - Produces `KasseErklaerkasten` (Props: keine; Zustand im `localStorage`
    unter dem Schlüssel `kasse.erklaerkasten.zu`) und `KasseAnleitungDialog`
    (Props: `{ onClose: () => void }`).
  - Consumes: Task 9.
- Steps:
  - [ ] `journalSaldo.ts`: `baueJournal` übernimmt die Nummerierung aus dem
        heutigen `KassenbuchView` (feste `laufendeNummer`, sonst vorläufige
        Position in Klammern) und die Suchfilterung (Beschreibung, Lieferant,
        Kategorie-Label). `einnahme`/`ausgabe` aus dem Vorzeichen von `betrag`;
        0,00 € zählt als Einnahme, damit nichts stillschweigend verschwindet
        (so verhält sich der Bestand heute auch).
  - [ ] `KassenbuchJournal.tsx` auf eine **Journal-Tabelle** umbauen. Spalten:
        `Nr.` · `Datum` · `Was` · `Beleg` · `Einnahme` · `Ausgabe` ·
        `Bestand danach`. Kopf: `sticky top-0 bg-slate-50`, Zeilen
        `divide-y divide-slate-100`, Beträge `tabular-nums text-right`,
        Einnahme `text-emerald-700`, Ausgabe `text-amber-700`, Bestand
        `text-slate-700`. Klick auf die Zeile öffnet wie bisher den Beleg
        (`onSelectBeleg`). Storno-Markierungen ("Storniert", "Gegenbuchung")
        und die Konto-Nummer bleiben als Badges erhalten — Text, nicht nur
        Farbe. `TKontoZeile` und die zweispaltige Soll/Haben-Struktur samt
        Doppelstrich entfallen ersatzlos.
  - [ ] Über der Tabelle eine Kopfzeile: links der aktuelle Bestand als große
        Zahl (`text-3xl font-bold`, Label "Kasse jetzt"), rechts die
        bestehende `KassenbuchAbschlussLeiste` (Kasse zählen / Monat
        abschließen). Darunter unverändert der Zeitraumfilter.
        Der Summenfuß bleibt, aber in Handwerker-Sprache: "Summe Einnahmen",
        "Summe Ausgaben", "Anfangsbestand", "Bestand am Ende".
  - [ ] `KasseErklaerkasten.tsx`: einklappbarer Kasten, **Standard offen**,
        Zustand in `localStorage` (`kasse.erklaerkasten.zu` = `'1'`), Farbe
        indigo (`bg-indigo-50 border-indigo-200 text-indigo-900`, Rolle
        "neutrale Information"). Überschrift "So funktioniert die Kasse".
        Genau diese fünf Sätze, wörtlich aus der Spec:
        1. Jede Barbewegung sofort eintragen — auch Bank-Abhebung und eigenes Geld.
        2. Zu jeder Zeile gehört ein Beleg. Fehlt einer, macht das Programm einen Eigenbeleg (ohne Vorsteuer).
        3. Die Kasse darf nie unter null. Vorher eigenes Geld einlegen.
        4. Einmal im Monat: Kasse zählen, dann den Monat abschließen. Danach ist er fest.
        5. Falsch gebucht? Nicht löschen — stornieren. Das Original bleibt sichtbar.
        Darunter der Knopf **"Mehr dazu"** und ein Schließen-Knopf mit
        `aria-label="Erklärung ausblenden"`.
  - [ ] `KasseAnleitungDialog.tsx`: Dialog (Vorbild `ModalShell` in
        `NeueBuchungDialog.tsx` nach Task 9) mit dem ausführlichen Text.
        **Begründung für die Abweichung von der Spec** (gehört als Kommentar in
        die Datei und ins Kontext-Log): Die Spec sagt "Link auf eine Hilfeseite
        (Markdown in `docs/`)". Es gibt im Projekt keinen Doku-Server und keine
        Route, die Markdown ausliefert — ein Link auf eine Repo-Datei liefe im
        Browser ins Leere. Deshalb: Der Text lebt **zweimal** — als
        `docs/KASSE_ANLEITUNG.md` für Entwickler und Steuerberater, und als
        Dialog im Programm für den Nutzer. Beide Fassungen tragen dieselben
        Abschnittsüberschriften.
  - [ ] `docs/KASSE_ANLEITUNG.md` schreiben (eine Seite, Handwerker-Sprache,
        keine Aufzählungswüste). Abschnitte:
        "Was gehört ins Kassenbuch", "Was passiert bei jeder Buchung",
        "Wenn kein Beleg da ist", "Die Kasse darf nie unter null",
        "Kasse zählen und Monat abschließen", "Falsch gebucht — was jetzt",
        "Was der Steuerberater bekommt". Kein Wort aus der
        Buchhalter-Spalte der Wording-Tabelle ohne Erklärung.
  - [ ] `KassenbuchTab.tsx` einbinden: `KasseErklaerkasten` ganz oben, darunter
        `KasseShortcuts`, darunter `KassenbuchJournal`.
  - [ ] `journalSaldo.test.ts` (vitest): Nummerierung mit und ohne
        `laufendeNummer`; Suche filtert nach Beschreibung, Lieferant und
        Kategorie-Label; Einnahme/Ausgabe-Aufteilung inklusive 0,00 €; die
        Summe aus `einnahme` minus `ausgabe` plus `saldoStart` ergibt
        `saldoEnde` (Konsistenzprobe an einem festen Datensatz).
  - [ ] `PCE/kassenbuch-journal.spec.ts`: Kassenbuch-Tab öffnen (Stubs wie
        Task 9, mindestens fünf Bewegungen inkl. einer stornierten und einer
        Gegenbuchung). Geprüft: die sieben Spaltenüberschriften stehen da; das
        Wort "Soll" und das Wort "Haben" kommen **nicht** mehr vor; die
        Bestandsspalte der letzten Zeile zeigt denselben Wert wie "Bestand am
        Ende"; der Erklärkasten ist offen und verschwindet nach Klick auf
        Schließen auch nach `page.reload()`; "Mehr dazu" öffnet den Dialog mit
        der Überschrift "So funktioniert die Kasse"; Klick auf eine Zeile
        öffnet den Beleg-Dialog. Je Zustand `designPruefung(...)`.
        Vor dem Abschluss `grep -rn "T-Konto\|Soll" react-pc-frontend/e2e` —
        Specs aus Task 9 prüfen die alten Wörter und müssen mitgezogen werden;
        `PCE/kasse-refactoring.spec.ts` gehört dann in die Dateiliste dieses
        Tasks.
- Gate:
  - `cd react-pc-frontend && npx vitest run src/components/kasse/journalSaldo.test.ts`
  - `cd react-pc-frontend && npm run lint`
  - `cd react-pc-frontend && npm run build` (danach `git checkout -- src/main/resources/static`)
  - `cd react-pc-frontend && E2E_PORT=5203 npx playwright test e2e/kassenbuch-journal.spec.ts e2e/kasse-refactoring.spec.ts` (beide, weil die Refactoring-Spec die alten T-Konto-Texte zusichert und mitgezogen wird)
---

## Abschnitt 3

Tasks, deren `Consumes`-Kette erst mit Abschnitt 2 vollständig ist: der
Steuerberater-Export (Task 8, braucht Task 1 + 7), der Buchungsdialog
(Task 12, braucht Task 3 + 9 + 10) und der Prüfen-Dialog (Task 13, braucht
Task 4 + 5 + 9 + 10).

**Frontend betroffen:** ja — Task 12 (Port 5204) und Task 13 (Port 5205).
Design-Reviewer läuft mit.

### Task 8: SteuerberaterExportService — das ZIP-Paket mit Vorprüfung

- Branch: `kasse/task-8-steuerberater-export`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-8`
- Files:
  - `B/service/SteuerberaterExportService.java` (neu)
  - `B/controller/SteuerberaterExportController.java` (neu)
  - `B/dto/SteuerberaterPaketDto.java` (neu)
  - `T/service/SteuerberaterExportServiceTest.java` (neu)
  - `T/controller/SteuerberaterExportControllerTest.java` (neu)
- Vorbild: `B/service/SteuerpruefungZ3ExportService.java` (Zeile 50-108:
  `erzeugeZip(...)` mit `ZipOutputStream`, `writeEntry`, CSV-Bau,
  `StandardCharsets.UTF_8`) und
  `B/controller/AusgangsGeschaeftsDokumentAuditController.java:120-132`
  (Download-Endpoint mit `Content-Disposition`).
- Interfaces:
  - Produces `B/dto/SteuerberaterPaketDto.java`:
    ```java
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Vorpruefung {
        private int anzahlBelege;
        private List<OffenerPunkt> offenePunkte;   // leer = alles fertig
        private boolean beraternummerFehlt;
        private boolean mandantennummerFehlt;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OffenerPunkt {
        private Long belegId;
        private LocalDate belegDatum;
        private String bezeichnung;   // Lieferant oder Beschreibung
        private String wasFehlt;      // "Konto fehlt" | "Zahlungsart fehlt" | "Konto und Zahlungsart fehlen"
    }
    ```
  - Produces `B/service/SteuerberaterExportService.java`:
    ```java
    @Transactional(readOnly = true)
    public SteuerberaterPaketDto.Vorpruefung pruefe(int jahr, int monat);

    /** Baut das ZIP. trotzdem = true exportiert auch mit offenen Punkten. */
    @Transactional(readOnly = true)
    public byte[] erzeugeZip(int jahr, int monat, Mitarbeiter ersteller, boolean trotzdem);
    ```
  - Produces Endpunkte in `SteuerberaterExportController`
    (`@RequestMapping("/api/buchhaltung/steuerberater")`):
    * `GET /vorpruefung?jahr=&monat=` → `Vorpruefung`
    * `GET /paket?jahr=&monat=&trotzdem=` → `application/zip`,
      `Content-Disposition: attachment; filename="<jahr>-<monat>_Kasse_<Firma>.zip"`
  - Consumes: Task 1 (`KasseEinstellung`-Felder), Task 7 (`DatevExportService`),
    Task 5 (`Beleg`-Zahlstatus im DTO ist für CSV 03 nicht nötig — die
    Eingangsrechnung wird direkt über `LieferantDokumentRepository` gelesen).
- Steps:
  - [ ] `pruefe(jahr, monat)`: Belege über
        `belegRepository.findGeprueftImZeitraumNachNummer(von, bis)`
        (`BelegRepository.java:186-192`). Offener Punkt, wenn `sachkonto == null`
        oder `zahlungsart` leer ist. Zusätzlich melden, ob Berater- oder
        Mandantennummer in `KasseEinstellung` fehlt.
  - [ ] `erzeugeZip(...)`: `ByteArrayOutputStream` + `ZipOutputStream` mit
        `StandardCharsets.UTF_8`. Ordnerpräfix
        `<jahr>-<monat zweistellig>_Kasse_<Firmenname bereinigt>/`
        (Firmenname aus `FirmeninformationRepository`, alles außer
        `[A-Za-z0-9-_]` durch `_` ersetzen; ohne Firmendaten "Betrieb").
        Inhalte:
        1. `01_Kassenbuch_<jahr>-<monat>.pdf` — `belegeKasseExportPdfService.generatePdf(jahr, monat, ersteller, true)`
           liefert einen `Path`; Bytes einlesen und als Entry schreiben, danach
           die Temp-Datei löschen.
        2. `02_Buchungen_DATEV_<jahr>-<monat>.csv` —
           `datevExportService.erzeugeCsvBytes(...)`, Splits über
           `belegKostenstellenAnteilRepository.findByBelegId(...)` je Beleg in
           **einem** Aufruf vorladen (Map aufbauen, kein Query je Zeile).
        3. `03_Eingangsrechnungen_<jahr>-<monat>.csv` — Semikolon-CSV,
           UTF-8, Kopfzeile
           `Nr;Datum;Lieferant;Netto;MwSt;Brutto;Konto;Kostenstelle;Zahlungsart;Bezahlt am`.
           Eine Zeile je validiertem Beleg des Monats. "Bezahlt am" kommt aus
           der verknüpften `LieferantGeschaeftsdokument`
           (`lieferantDokumentRepository.findByBelegId(...).getGeschaeftsdaten()`):
           Datum wenn bezahlt, sonst der Text `offen`. Escaping wie in
           `SteuerpruefungZ3ExportService.esc`.
        4. `04_Belege/<laufendeNummer 4-stellig>_<belegDatum>_<Lieferant>.pdf|jpg|png`
           — die Originaldatei aus `${upload.path}/belege/<gespeicherterDateiname>`
           kopieren, Endung aus dem Originalnamen übernehmen. Ohne laufende
           Nummer `0000` plus Beleg-ID. Lieferantenname bereinigen wie oben,
           auf 40 Zeichen kürzen; fehlt er, `Beleg`. Fehlt die Datei, den Eintrag
           überspringen und in `LIESMICH.txt` unter "Fehlende Dateien" auflisten.
        5. `LIESMICH.txt` — Klartext, Handwerker-/Steuerberater-Sprache. Inhalt:
           was in welcher Datei steht; Kontenrahmen SKR03; Kassenkonto und
           Bankkonto aus den Einstellungen; der Satz "Bank- und
           Kreditkartenbuchungen sind bewusst **nicht** im DATEV-Stapel — das
           Programm kennt keine Bankumsätze. Beleg und Zahlstatus dazu stehen in
           Datei 03."; der Satz "Kostenstellen erscheinen mit ihrem Namen, weil
           das Programm keine Kostenstellen-Nummern führt."; Hinweis auf die
           Verfahrensdokumentation (`GET /api/buchhaltung/kassenbuch/verfahrensdokumentation`);
           bei `trotzdem = true` zusätzlich die Liste der Belege mit
           `PRÜFEN:`-Präfix.
  - [ ] Controller: Auth `belegService.findCaller(token, auth)` +
        `darfSehen(caller)`; `jahr` 2000-2999 und `monat` 1-12 validieren
        (Vorbild `SachkontoController.auswertungMonatPdf`, Zeile 190-217);
        bei offenen Punkten und `trotzdem = false` HTTP **409** mit der
        `Vorpruefung` als Body, damit das Frontend die Liste zeigen kann.
  - [ ] `T/service/SteuerberaterExportServiceTest.java`: ZIP entpacken und
        prüfen, dass alle fünf Bausteine mit den erwarteten Namen drin sind;
        `03_...csv` enthält für eine bezahlte Rechnung das Datum und für eine
        offene das Wort `offen`; ein Beleg ohne Datei landet in der
        Fehlliste statt das ZIP zu sprengen; `pruefe` findet Belege ohne Konto
        und ohne Zahlungsart mit dem richtigen `wasFehlt`-Text; ohne offene
        Punkte ist die Liste leer. Alle Lieferanten "Musterbaustoffe GmbH",
        Firma "Musterbetrieb GmbH".
  - [ ] `T/controller/SteuerberaterExportControllerTest.java` (`@WebMvcTest`):
        403 ohne Recht, 400 bei Monat 13, 409 mit Vorprüfungs-Body bei offenen
        Punkten, 200 mit `application/zip` und `Content-Disposition` bei
        `trotzdem=true`.
- Gate:
  - `./mvnw -B test -Dtest=SteuerberaterExportServiceTest,SteuerberaterExportControllerTest,DatevExportServiceTest`
---

### Task 12: "Neue Buchung" — sechs Kacheln (Frontend)

- Branch: `kasse/task-12-neue-buchung`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-12`
- Files:
  - `PC/components/kasse/NeueBuchungDialog.tsx`
  - `PC/components/kasse/KasseShortcuts.tsx`
  - `PC/components/kasse/neueBuchungRegeln.ts` (neu)
  - `PC/components/kasse/neueBuchungRegeln.test.ts` (neu)
  - `PCE/neue-buchung.spec.ts` (neu)
- Vorbild:
  - Dialograhmen, Felder, Fußzeile: `ModalShell` / `FieldRow` / `ModalFooter`
    in `NeueBuchungDialog.tsx` (nach Task 9).
  - 409-Behandlung mit Ein-Klick-Lösung: `EinfacheKasseModal`
    (`KasseShortcuts.tsx:295-408`, Zustand `konflikt`, Vorab-Privateinlage in
    Zeile 350-372).
  - Datei-Anhang: `handleUpload` in `BelegeKasseEditor.tsx:418-443`
    (`FormData`, `fd.append('datei', file)`).
  - Kachel-Optik: `KpiTile` (`BelegeKasseEditor.tsx:1148-1158`).
- Interfaces:
  - Produces `PC/components/kasse/neueBuchungRegeln.ts`:
    ```ts
    export type BuchungsArt =
        | 'GELD_EINGENOMMEN' | 'GELD_AUSGEGEBEN' | 'VON_BANK_GEHOLT'
        | 'ZUR_BANK_GEBRACHT' | 'EIGENES_GELD_EINGELEGT' | 'GELD_PRIVAT_ENTNOMMEN';

    export interface KachelDefinition {
        art: BuchungsArt;
        titel: string;          // z.B. "Geld eingenommen"
        untertitel: string;     // z.B. "Kunde hat bar bezahlt"
        icon: string;           // lucide-Name
        brauchtKonto: boolean;
        brauchtMwst: boolean;
        brauchtGegenpartei: boolean;
        erlaubtBelegdatei: boolean;
        erlaubtRechnung: boolean;
        kontoTyp?: 'AUFWAND' | 'ERTRAG';
    }
    export const KACHELN: KachelDefinition[];   // exakt sechs, in der Reihenfolge der Spec
    export function pflichtfelderFehlen(art: BuchungsArt, formular: NeueBuchungFormular): string | null;
    ```
  - Produces `NeueBuchungDialog` mit
    `{ offen: boolean; sachkonten: Sachkonto[]; onClose: () => void; onGebucht: (meldung: string) => void }`.
  - Consumes: Task 9, Task 3 (Endpunkte
    `POST /api/buchhaltung/kassenbuch/buchungen` und
    `GET /api/buchhaltung/kassenbuch/offene-ausgangsrechnungen`), Task 10
    (Select mit Gruppen).
- Steps:
  - [ ] `neueBuchungRegeln.ts` mit den sechs Kacheln in dieser Reihenfolge und
        genau diesen Texten (Handwerker-Sprache, Spec C1):

        | Titel | Untertitel | Icon |
        | --- | --- | --- |
        | Geld eingenommen | Kunde hat bar bezahlt | `Banknote` |
        | Geld ausgegeben | bar bezahlt | `ShoppingCart` |
        | Geld von der Bank geholt | Bargeld abgehoben | `ArrowDownToLine` |
        | Geld zur Bank gebracht | Bargeld eingezahlt | `ArrowUpFromLine` |
        | Eigenes Geld eingelegt | Privateinlage | `PiggyBank` |
        | Geld privat entnommen | Privatentnahme | `Wallet` |

        Pflichtfelder je Kachel wie in der Spec-Tabelle; `pflichtfelderFehlen`
        gibt den fehlenden Punkt als ganzen Satz zurück ("Bitte trag ein, von
        wem das Geld kam.") oder `null`.
  - [ ] `NeueBuchungDialog`: zwei Schritte in **einem** Dialog. Schritt 1 zeigt
        die sechs Kacheln als Raster (`grid grid-cols-1 sm:grid-cols-2 gap-3`),
        jede Kachel ein `<button>` mit Icon, Titel (`font-semibold`) und
        Untertitel (`text-sm text-slate-500`), Rahmen `border-slate-200`,
        Hover `border-rose-300`. Schritt 2 ist das Formular der gewählten
        Kachel mit einem Zurück-Knopf.
  - [ ] Formularfelder: Betrag (`type="number" step="0.01"`, `autoFocus`),
        Datum (`DatePicker` wie in `KasseShortcuts.tsx`), je nach Kachel
        "Von wem"/"An wen", "Wofür" (Freitext), Konto-Select (nur die passenden
        `kontoTyp`-Konten, gruppiert über die neue `gruppe`-Option aus Task 10;
        Standard bei "Geld eingenommen" ist das Konto mit Nummer `8400`),
        MwSt als drei Knöpfe 19/7/0 (Vorbild `BelegeKasseEditor.tsx:1531-1552`),
        optional Baustelle/Bereich (Kostenstellen-Select, Daten von
        `/api/bestellungen-uebersicht/kostenstellen` wie in
        `KostenstellenSplitsEditor.tsx:48`).
  - [ ] "Geld eingenommen": optionales Feld **"Zu welcher Rechnung?"** — lädt
        `GET /api/buchhaltung/kassenbuch/offene-ausgangsrechnungen` und zeigt
        `<Nummer> · <Datum> · <Betrag> €`. Hinweissatz darunter (indigo):
        "Wenn du eine Rechnung wählst, wird sie automatisch als bezahlt
        markiert." Nach dem Buchen der Hinweis "Das Programm hat eine Quittung
        erzeugt und an die Buchung gehängt."
  - [ ] "Geld ausgegeben": Umschalter zwischen **"Beleg anhängen"**
        (Datei-Auswahl, `accept="image/*,application/pdf"`) und **"Kein Beleg
        vorhanden"**. Bei "kein Beleg": Pflichtfeld "Warum gibt es keinen
        Beleg?" (`grundOhneBeleg`), MwSt wird auf 0 % gesetzt und gesperrt, mit
        dem festen Satz **"Ohne Fremdbeleg gibt es keine Vorsteuer."** in
        amber (Warnung).
  - [ ] Absenden: `FormData` mit Teil `daten` als
        `new Blob([JSON.stringify(req)], { type: 'application/json' })` und
        optionalem Teil `datei`; `POST /api/buchhaltung/kassenbuch/buchungen`.
        Antworten: 200 → `onGebucht(meldung)`; 400 → Meldung aus
        `body.message` inline (kein `alert`); 409 mit `projizierterSaldo` →
        dieselbe Ein-Klick-Lösung wie heute in `EinfacheKasseModal`
        (Privateinlage in Höhe der Lücke buchen, dann erneut senden);
        409 ohne Saldo-Felder (Monat gesperrt) → `message` + `hinweis` anzeigen.
  - [ ] `KasseShortcuts.tsx`: neuer **Primärknopf "Neue Buchung"**
        (`bg-rose-600 text-white`) links in der Knopfleiste, öffnet den Dialog.
        Die vier bestehenden Shortcut-Knöpfe entfallen — ihre Fälle stecken
        jetzt in den Kacheln. **Ausnahme: "Ehegattengehalt" bleibt** und wandert
        hinter das Zahnrad, weil es laut Spec unter "Einstellungen" gehört:
        Der Knopf verschwindet aus der Leiste, `LohnZahlungModal` wird aus dem
        `KasseEinstellungenDialog` heraus geöffnet — **das macht Task 14**.
        Damit dieser Task für sich lauffähig bleibt: den Ehegattengehalt-Knopf
        hier **stehen lassen** und in Task 14 entfernen.
  - [ ] `neueBuchungRegeln.test.ts` (vitest): `KACHELN` hat genau sechs
        Einträge in der Spec-Reihenfolge; `pflichtfelderFehlen` meldet für jede
        Kachel den erwarteten Satz bei fehlendem Betrag, fehlendem Datum,
        fehlendem Konto (nur wo Pflicht), fehlender Gegenpartei (nur wo
        Pflicht) und fehlendem Grund bei "kein Beleg"; vollständiges Formular
        liefert `null`.
  - [ ] `PCE/neue-buchung.spec.ts`: Kassenbuch-Tab öffnen, "Neue Buchung"
        klicken, **alle sechs Kacheln** durchspielen (je einmal ausfüllen und
        buchen, POST-Body mitschreiben und die `art` prüfen — Mitschrift-Muster
        wie `PCE/monatsabschluss-task9.spec.ts:5-10, 40`). Zusätzlich:
        "Geld ausgegeben" ohne Beleg setzt MwSt auf 0 und zeigt den
        Vorsteuer-Satz; ein 409 mit `projizierterSaldo` zeigt die
        Privateinlage-Lösung; Abbrechen schließt ohne POST. Je Kachel ein
        `designPruefung(...)`-Aufruf, mindestens für die Kachel-Auswahl und
        zwei Formulare.
- Gate:
  - `cd react-pc-frontend && npx vitest run src/components/kasse/neueBuchungRegeln.test.ts`
  - `cd react-pc-frontend && npm run lint`
  - `cd react-pc-frontend && npm run build` (danach `git checkout -- src/main/resources/static`)
  - `cd react-pc-frontend && E2E_PORT=5204 npx playwright test e2e/neue-buchung.spec.ts`

---

### Task 13: Prüfen-Dialog in fester Reihenfolge (Frontend)

Der größte UI-Task. Behebt drei gemeldete Fehler: "Wo gezahlt" ohne Erklärung,
"Rechnung" landet ungefragt in den offenen Posten, Vorschläge sind nicht
übernehmbar.

- Branch: `kasse/task-13-beleg-pruefen`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-13`
- Files:
  - `PC/components/kasse/BelegDetailModal.tsx`
  - `PC/components/kasse/VorschlagsChip.tsx`
  - `PC/components/kasse/zahlungsartRegeln.ts` (neu)
  - `PC/components/kasse/zahlungsartRegeln.test.ts` (neu)
  - `PCE/beleg-pruefen.spec.ts` (neu)
- Vorbild:
  - Formularblöcke, `Field`, MwSt-Knöpfe, Konflikt-Box: die Datei selbst
    (nach Task 9; heute `BelegeKasseEditor.tsx:1440-1797`).
  - Chip mit "Übernehmen": `VorschlagsChip` (heute `KiVorschlagKarte`,
    `BelegeKasseEditor.tsx:1944-1998`).
  - Hinweiskasten mit Icon und zwei Absätzen: der Festschreibungs-Hinweis
    (`BelegeKasseEditor.tsx:1469-1491`).
- Interfaces:
  - Produces `PC/components/kasse/zahlungsartRegeln.ts`:
    ```ts
    /** Ein Satz, der sagt, was die Zahlungsart bedeutet. */
    export function folgeSatz(zahlungsart: string | null | undefined): string;
    /** "Wo gezahlt" aus der Zahlungsart. null = keine Ableitung moeglich. */
    export function kategorieAusZahlungsart(
        zahlungsart: string | null | undefined,
        richtungAusgabe: boolean,
    ): BelegKategorie | null;
    /** true, wenn die Frage "Ist die Rechnung schon bezahlt?" gestellt wird. */
    export function fragtNachZahlung(zahlungsart: string | null | undefined, dokumentTyp: string | null | undefined): boolean;
    /** true, wenn diese Zahlungsart automatisch als bezahlt gilt. */
    export function giltAlsBezahlt(zahlungsart: string | null | undefined): boolean;
    ```
    **Die Tabelle muss zeichengleich zur Backend-Tabelle in
    `ZahlungsartMapper` (Task 1) sein** — ein Kommentar in beiden Dateien
    verweist auf die jeweils andere.
  - Produces `VorschlagsChip` mit neuer Signatur:
    ```ts
    interface VorschlagsChipProps {
        vorschlag: BelegVorschlag | null | undefined;
        hinweis?: string | null;          // beleg.kiKostenkontoHinweis
        aktuelleId: number | null;
        onUebernehmen: (id: number) => void;
        was: 'Konto' | 'Baustelle';
    }
    ```
  - Consumes: Task 9, Task 10 (Gruppen im Select), Task 5 (DTO-Felder
    `vorschlagSachkonto`, `vorschlagKostenstelle`, `eingangsrechnungBezahlt`,
    `kiKostenkontoHinweis`), Task 4 (`kiZahlungsart`, `kiBelegdatum`,
    `kiBetragBrutto`, `kiVorgeschlagenerLieferant`).
- Steps:
  - [ ] **Spaltenaufteilung drehen** (`BelegeKasseEditor.tsx:1458-1465` vor dem
        Refactoring): Vorschau bekommt `lg:col-span-1`, Formular
        `lg:col-span-2`. Konto- und Kostenstellen-Feld stehen in **voller**
        Formularbreite (kein `grid-cols-2` mehr um sie herum).
  - [ ] `zahlungsartRegeln.ts` mit dieser Tabelle:

        | Zahlungsart | Kategorie | Satz darunter |
        | --- | --- | --- |
        | Bar | KASSE_AUSGABE / KASSE_EINNAHME | Bar → landet im Kassenbuch |
        | EC-Karte | BANK | EC-Karte → läuft über die Bank, nicht über die Kasse |
        | Überweisung | BANK | Überweisung → läuft über die Bank, nicht über die Kasse |
        | Lastschrift | BANK | Lastschrift → wird von der Bank abgebucht, nicht aus der Kasse |
        | Kreditkarte | KREDITKARTE | Kreditkarte → läuft über die Kreditkarten-Abrechnung |
        | PayPal | BANK | PayPal → läuft über das PayPal-Konto, nicht über die Kasse |
        | Online-Zahlung | BANK | Online-Zahlung → läuft über die Bank, nicht über die Kasse |
        | Scheck | BANK | Scheck → wird über die Bank eingelöst |
        | Rechnung | SONSTIGER_BELEG | Rechnung → wird später bezahlt, nicht bar |
        | (leer) | null | Bitte wählen – daraus ergibt sich, wo die Buchung landet. |

        `giltAlsBezahlt`: Bar, EC-Karte, Kreditkarte, PayPal, Online-Zahlung.
        `fragtNachZahlung`: `!giltAlsBezahlt(za)` **und** `dokumentTyp` ist
        `RECHNUNG` oder `GUTSCHRIFT`.
  - [ ] Formular in **genau dieser Reihenfolge** aufbauen, jeder Block mit
        Überschrift als Frage und einem Hilfesatz:
        1. **"Wie viel und wann?"** — Betrag und Datum wie bisher. Stimmt der
           Wert mit `kiBetragBrutto`/`kiBelegdatum` überein, dahinter das Badge
           **"von KI gelesen"** (`bg-indigo-50 text-indigo-800 border-indigo-200`,
           `text-[10px] uppercase tracking-wide`).
        2. **"Wie wurde bezahlt?"** — Select mit den Stammdaten-Zahlungsarten
           (`buildZahlungsartOptions`), **Pflichtfeld**. Darunter
           `folgeSatz(...)` als Satz in `text-sm text-slate-600`. Badge
           "von KI erkannt", wenn der Wert `beleg.kiZahlungsart` entspricht
           (nach Mapping). Das alte Feld **"Wo gezahlt" entfällt komplett**;
           `belegKategorie` wird beim Speichern aus
           `kategorieAusZahlungsart(...)` gesetzt (Richtung: Ausgabe, außer der
           Beleg hat schon `KASSE_EINNAHME`).
        3. **"Ist die Rechnung schon bezahlt?"** — nur wenn
           `fragtNachZahlung(...)`. Zwei Radios: "Ja, bezahlt am …" (mit
           Datumsfeld, Vorbelegung heute) und "Nein, noch nicht bezahlt". Ist
           `beleg.eingangsrechnungId` gesetzt, darunter der Link "Zur
           Eingangsrechnung" (öffnet `/rechnungen?dokument=<id>` in einem neuen
           Tab; existiert die Route nicht, den Link auf die
           Eingangsrechnungs-Übersicht setzen — der Punkt ist, dass die
           Verknüpfung überhaupt sichtbar wird). Aktueller Stand aus
           `eingangsrechnungBezahlt`/`eingangsrechnungBezahltAm` vorbelegen.
           Gilt die Zahlungsart automatisch als bezahlt, steht statt der Frage
           der Satz "Bar und EC-Karte gelten als sofort bezahlt."
        4. **"Wofür war das?"** — Sachkonto-Select über die volle Breite, mit
           **Gruppen** (`gruppe: SACHKONTO_TYP_LABEL[typ]`) statt des heutigen
           Präfixes im Label; das Label ist nur noch
           `"4930 Bürobedarf"`. `buildSachkontoOptions` in `belegFormat.ts`
           entsprechend anpassen (Rückgabe bekommt `gruppe`). Darüber der
           `VorschlagsChip` mit `vorschlag={beleg.vorschlagSachkonto}`.
        5. **"Für welche Baustelle / welchen Bereich?"** — einfaches
           Kostenstellen-Select über die volle Breite (Daten von
           `/api/bestellungen-uebersicht/kostenstellen`), das beim Speichern
           einen 100-%-Split schreibt. Darüber der `VorschlagsChip` mit
           `vorschlag={beleg.vorschlagKostenstelle}`. Knopf **"Auf mehrere
           aufteilen"** klappt den bestehenden `KostenstellenSplitsEditor` auf;
           solange er offen ist, ist das einfache Select ausgeblendet.
        6. **"Von wem war der Beleg?"** (Lieferant) — Feld wie bisher, aber mit
           **Abweichungs-Hinweis**: ist `kiVorgeschlagenerLieferant` gesetzt und
           ungleich dem gewählten Lieferanten, erscheint der Kasten
           "Am Handy gewählt: <X>. Die KI hat gelesen: <Y>." plus Knopf
           "Übernehmen" (setzt den Lieferanten auf die KI-Lesung, sofern ein
           Stammdaten-Treffer existiert — sonst öffnet er die
           Lieferanten-Auswahl mit vorbelegtem Suchtext). Die heutige Bedingung
           `&& !form.lieferantName` (`BelegeKasseEditor.tsx:1436`) fällt weg —
           **genau sie blendet die Abweichung heute aus.**
        Alles Weitere (Beleg-Nr., Netto, MwSt-Satz-Feld, Notiz) bleibt unter
        "Mehr Details".
  - [ ] `VorschlagsChip` umbauen: zeigt `bezeichnung` (mit `nummer`, falls
        vorhanden), die Quelle als Satz — `KI` → "Die KI schlägt vor",
        `HISTORIE` → "Beim letzten Mal bei diesem Lieferanten",
        `LIEFERANT_STANDARD` → "Beim Lieferanten hinterlegt" — dazu die
        `begruendung`, und den Knopf **"Übernehmen"**. Ist der Vorschlag schon
        übernommen, steht "übernommen" mit `CheckCircle2` (wie heute). Gibt es
        keinen Vorschlag, aber einen `hinweis`, zeigt der Chip **ehrlich**
        "Kein Vorschlag: <hinweis>" in slate statt zu verschwinden. Ohne beides
        rendert er `null`.
  - [ ] Speichern (`save`): zusätzlich `zahlungsstatus`
        (`'BEZAHLT'`/`'OFFEN'`, nur wenn die Frage sichtbar war oder die
        Zahlungsart automatisch als bezahlt gilt), `bezahltAm` und die
        abgeleitete `belegKategorie` senden. Der Kostenstellen-Select schreibt
        `kostenstellenSplits: [{ kostenstelleId, prozent: 100 }]`, sofern der
        Split-Editor nicht offen ist.
  - [ ] `zahlungsartRegeln.test.ts` (vitest): jede Zeile der Tabelle als Fall
        (Satz und Kategorie); `Bar` mit `richtungAusgabe=false` ergibt
        `KASSE_EINNAHME`; `fragtNachZahlung` ist bei Bar+RECHNUNG `false`, bei
        Überweisung+RECHNUNG `true`, bei Überweisung+LIEFERSCHEIN `false`;
        unbekannte Zahlungsart liefert den Bitte-wählen-Satz und `null`.
  - [ ] `PCE/beleg-pruefen.spec.ts`: Beleg mit KI-Werten und abweichendem
        Lieferanten stubben. Geprüft:
        * die sechs Überschriften stehen in genau dieser Reihenfolge
          (`page.getByRole('heading')` einsammeln und die Reihenfolge
          vergleichen),
        * "Wo gezahlt" kommt **nicht** mehr vor,
        * Zahlungsart "Überweisung" blendet die Bezahlt-Frage ein, "Bar"
          blendet sie aus und zeigt den Bar-Satz,
        * der Abweichungs-Hinweis "Am Handy gewählt: … Die KI hat gelesen: …"
          ist sichtbar, obwohl ein Lieferant gesetzt ist,
        * "Übernehmen" am Konto-Chip setzt den Select-Wert,
        * ohne Vorschlag, aber mit Hinweis steht "Kein Vorschlag: …",
        * der PUT-Body enthält `zahlungsstatus`, `bezahltAm`, die abgeleitete
          `belegKategorie` und einen 100-%-Split,
        * Formular und Vorschau teilen sich 2:1 (Breitenverhältnis über
          `boundingBox()` prüfen, Toleranz 10 %).
        Je Zustand `designPruefung(...)`.
- Gate:
  - `cd react-pc-frontend && npx vitest run src/components/kasse/zahlungsartRegeln.test.ts`
  - `cd react-pc-frontend && npm run lint`
  - `cd react-pc-frontend && npm run build` (danach `git checkout -- src/main/resources/static`)
  - `cd react-pc-frontend && E2E_PORT=5205 npx playwright test e2e/beleg-pruefen.spec.ts`

---

## Abschnitt 4

Die beiden letzten Tasks: Kasse-Einstellungen (Task 14, braucht Task 1 + 9 +
12) und das Steuerberater-Paket im Frontend (Task 15, braucht Task 8 + 9).
Beide hängen an Task 12 bzw. 8 aus Abschnitt 3 und können erst danach starten.

**Frontend betroffen:** ja — Task 14 (Port 5206) und Task 15 (Port 5207).
Design-Reviewer läuft mit.

### Task 14: Kasse-Einstellungen mit Abschnitt "Für den Steuerberater" (Frontend)

Entscheidung 6 des Orchestrators: kein neuer Reiter auf der
Einstellungsseite — die fünf DATEV-Felder kommen in den bestehenden Dialog
hinter dem Zahnrad.

- Branch: `kasse/task-14-kasse-einstellungen`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-14`
- Files:
  - `PC/components/kasse/KasseEinstellungenDialog.tsx`
  - `PC/components/kasse/KasseShortcuts.tsx`
  - `PCE/kasse-einstellungen.spec.ts` (neu)
- Vorbild: die Datei selbst (nach Task 9; heute `KasseSettingsModal`,
  `KasseShortcuts.tsx:517-641`) — Abschnittsüberschriften als
  `<h3 className="font-semibold text-slate-900 mb-2 text-sm pt-2 border-t border-slate-200">`,
  Felder über `FieldRow`, Erklärsatz darunter als `text-xs text-slate-500`.
- Interfaces:
  - Produces: keine neue Komponente; der `PUT`-Body an
    `/api/buchhaltung/kasse/einstellung` bekommt die fünf Felder
    `datevBeraternummer`, `datevMandantennummer`, `wirtschaftsjahrBeginnMonat`,
    `kassenkontoNummer`, `bankkontoNummer`.
  - Consumes: Task 1 (Backend-Felder in `EinstellungRequest`/`Response`),
    Task 9, Task 12 (der Ehegattengehalt-Knopf wandert erst jetzt).
- Steps:
  - [ ] Zweiter Abschnitt **"Für den Steuerberater"** unter den bestehenden.
        Einleitungssatz: "Diese Angaben stehen in der DATEV-Datei, die dein
        Steuerberater bekommt. Wenn du sie nicht kennst, frag ihn — der Export
        geht auch ohne."
        Felder in dieser Reihenfolge:
        * "Beraternummer" (`maxLength={7}`, Platzhalter "z.B. 1234567")
        * "Mandantennummer" (`maxLength={5}`, Platzhalter "z.B. 54321")
        * "Wirtschaftsjahr beginnt im" — Select mit den zwölf Monatsnamen,
          Standard "Januar"; Hilfesatz "Bei den meisten Betrieben ist das der
          Januar."
        * "Kassenkonto" (Standard 1000) und "Bankkonto" (Standard 1200),
          nebeneinander; Hilfesatz "Standard im SKR03. Nur ändern, wenn dein
          Steuerberater andere Nummern nutzt."
  - [ ] Eingaben prüfen, bevor gesendet wird (der Server prüft nochmal):
        Kontonummern nur Ziffern und max. 8 Zeichen, sonst der Inline-Hinweis
        "Bitte nur Ziffern, höchstens 8 Stellen." in amber. Leere Felder sind
        erlaubt.
  - [ ] Dritter Abschnitt **"Ehegattengehalt"**: den bisherigen
        Automatik-Block behalten **und** darunter den Knopf "Jetzt einmalig
        auszahlen", der das `LohnZahlungModal` öffnet (es lebt nach Task 9 in
        `NeueBuchungDialog.tsx`). Im selben Zug in `KasseShortcuts.tsx` den
        Knopf "Ehegattengehalt" aus der Leiste entfernen — laut Spec bleibt
        der Shortcut "unverändert unter Einstellungen".
  - [ ] `PCE/kasse-einstellungen.spec.ts`: Zahnrad öffnen; alle drei
        Abschnitte sind da; die fünf neuen Felder sind mit den Serverwerten
        vorbelegt (Stub liefert Beraternummer "1234567", Kassenkonto "1000");
        eine ungültige Kontonummer ("12x") zeigt den Hinweis und sendet nicht;
        Speichern schickt einen PUT, dessen Body alle fünf Felder enthält
        (Mitschrift); "Jetzt einmalig auszahlen" öffnet den Lohn-Dialog; in der
        Knopfleiste steht **kein** "Ehegattengehalt"-Knopf mehr.
        `designPruefung(...)` für den geöffneten Dialog (er ist lang — der
        Reviewer soll sehen, ob er scrollbar bleibt und nichts überlappt).
- Gate:
  - `cd react-pc-frontend && npm run lint`
  - `cd react-pc-frontend && npm run build` (danach `git checkout -- src/main/resources/static`)
  - `cd react-pc-frontend && E2E_PORT=5206 npx playwright test e2e/kasse-einstellungen.spec.ts`

---

### Task 15: Dialog "Für den Steuerberater" mit Vorprüfung und ZIP-Mail (Frontend)

- Branch: `kasse/task-15-steuerberater-paket`
- Worktree: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-15`
- Files:
  - `PC/components/kasse/SteuerberaterPaketDialog.tsx` (neu)
  - `PC/components/SteuerberaterBelegExportModal.tsx`
  - `PC/pages/BelegeKasseEditor.tsx` (nur die Knopfleiste)
  - `PCE/steuerberater-paket.spec.ts` (neu)
- Vorbild:
  - Monatsauswahl: `MonatsExportModal` (`BelegeKasseEditor.tsx:649-731`).
  - Datei-Download aus dem Frontend: derselbe Weg wie beim Monats-PDF
    (Link auf den Endpunkt, `window.open` bzw. `<a download>`).
  - E-Mail mit Anhang: `SteuerberaterBelegExportModal.handleSend`
    (`SteuerberaterBelegExportModal.tsx:492-521`) — `FormData` mit dem Teil
    `dto`; der Endpunkt `POST /api/emails/send` nimmt zusätzlich den Teil
    `attachments` als `MultipartFile[]`
    (`UnifiedEmailController.java:1365-1369`).
- Interfaces:
  - Produces `SteuerberaterPaketDialog` mit
    `{ offen: boolean; onClose: () => void }`.
  - Consumes: Task 8 (`GET /api/buchhaltung/steuerberater/vorpruefung`,
    `GET /api/buchhaltung/steuerberater/paket`), Task 9.
- Steps:
  - [ ] `SteuerberaterPaketDialog`: Monat und Jahr wählen (Vorbelegung wie
        `MonatsExportModal`: der Vormonat). Beim Öffnen und bei jeder Änderung
        `GET /vorpruefung?jahr=&monat=`.
  - [ ] Ergebnis der Vorprüfung anzeigen:
        * Keine offenen Punkte → grüner Satz "Alle <n> Belege sind fertig
          geprüft." und der Knopf "Paket erstellen" ist aktiv.
        * Offene Punkte → amber-Kasten "<n> Belege sind noch nicht fertig
          geprüft." mit der Liste (Datum · Bezeichnung · was fehlt), jede Zeile
          klickbar (öffnet den Beleg über die bestehende Route). Darunter zwei
          Knöpfe: **"Erst prüfen"** (schließt den Dialog) und **"Trotzdem
          erstellen"** (sekundär, mit dem Satz "In der DATEV-Datei steht dann
          PRÜFEN: vor dem Buchungstext und das Konto bleibt leer.").
        * Fehlende Berater-/Mandantennummer → indigo-Hinweis "Beraternummer
          fehlt – der Export geht trotzdem. Nachtragen kannst du sie in den
          Kassen-Einstellungen."
  - [ ] "Paket erstellen" lädt
        `GET /api/buchhaltung/steuerberater/paket?jahr=&monat=&trotzdem=`
        und bietet die ZIP zum Speichern an. Antwortet der Server mit 409,
        die Vorprüfung aus dem Body neu anzeigen statt eines generischen
        Fehlers.
  - [ ] Zweiter Knopf **"Per E-Mail an den Steuerberater"**: holt dieselbe ZIP
        als `Blob`, öffnet `SteuerberaterBelegExportModal` und übergibt sie als
        Anhang.
  - [ ] `SteuerberaterBelegExportModal` umbauen: neue Prop
        `anhang?: { dateiname: string; blob: Blob } | null`. Ist sie gesetzt,
        * entfällt die generierte HTML-Tabelle im Mailtext; stattdessen ein
          kurzer Text: Anrede, dann "anbei die Kassenunterlagen für <Monat>
          <Jahr> als ZIP-Datei. Sie enthält das Kassenbuch als PDF, die
          Buchungen als DATEV-Datei, die Belegliste und alle Belegbilder.",
          dann die Signatur.
        * `handleSend` hängt `formData.append('attachments', blob, dateiname)`
          an (der Endpunkt kennt den Teil bereits).
        Ohne die Prop bleibt das Modal **unverändert** — der alte Aufruf über
        "Belegliste per E-Mail" funktioniert weiter.
  - [ ] `BelegeKasseEditor.tsx`: den Knopf "Belegliste per E-Mail"
        (`BelegeKasseEditor.tsx:485-489`) durch **"Für den Steuerberater"**
        ersetzen, der den neuen Dialog öffnet. Den Knopf "Monats-Export (PDF)"
        stehen lassen — er ist der schnelle Weg zum reinen Kassenbuch.
        Vor dem Abschluss `grep -rn "Belegliste per E-Mail" react-pc-frontend`
        laufen lassen und jede Fundstelle mitziehen.
  - [ ] `PCE/steuerberater-paket.spec.ts`: Dialog öffnen; Fall "alles fertig"
        zeigt den grünen Satz und einen aktiven Knopf; Fall "3 offene Punkte"
        zeigt die Liste mit "Konto fehlt"/"Zahlungsart fehlt" und den Knopf
        "Trotzdem erstellen"; Klick darauf ruft den Paket-Endpunkt mit
        `trotzdem=true` auf (Mitschrift der URL); fehlende Beraternummer zeigt
        den indigo-Hinweis; "Per E-Mail" öffnet das Mail-Modal mit dem kurzen
        Text **ohne** HTML-Tabelle. Der ZIP-Download wird gestubbt (kleiner
        Blob), nicht wirklich geschrieben. Je Zustand `designPruefung(...)`.
- Gate:
  - `cd react-pc-frontend && npm run lint`
  - `cd react-pc-frontend && npm run build` (danach `git checkout -- src/main/resources/static`)
  - `cd react-pc-frontend && E2E_PORT=5207 npx playwright test e2e/steuerberater-paket.spec.ts`

---

## Abhängigkeiten auf einen Blick

Der Abschnitts-Agent schneidet nach der topologischen Ebene, nicht nach der
Reihenfolge im Dokument.

| Task | Consumes | Frontend? |
| --- | --- | --- |
| 1 Datenmodell V372 + ZahlungsartMapper | — | nein |
| 2 BelegPdfService | — | nein |
| 9 Refactoring C9 | — | **ja** |
| 10 select-custom | — | **ja** |
| 16 Handy-Polling | — | **ja** |
| 3 KassenbuchungService | 1, 2 | nein |
| 4 KI-Analyse | 1 | nein |
| 5 BelegVorschlag + Zahlungsstatus | 1 | nein |
| 6 KI-Kostenkonto | 1 | nein |
| 7 DatevExportService | 1 | nein |
| 11 Kassenbuch-Journal | 9 | **ja** |
| 8 SteuerberaterExportService | 1, 7 | nein |
| 12 Neue Buchung | 3, 9, 10 | **ja** |
| 13 Prüfen-Dialog | 4, 5, 9, 10 | **ja** |
| 14 Kasse-Einstellungen | 1, 9, 12 | **ja** |
| 15 Steuerberater-Paket | 8, 9 | **ja** |
| 17 Dokumentation | fachlich 3, 7, 8 | nein |

**Datei-Überschneidungen, auf die der Abschnittsschnitt achten muss:**

- `B/controller/BelegController.java` → nur Task 3.
- `B/service/BelegService.java` → nur Task 5.
- `B/dto/BelegDto.java` → nur Task 1 (alle Felder werden dort auf einmal
  deklariert, gefüllt wird in Task 5).
- `B/controller/KasseShortcutController.java` → nur Task 1.
- `B/service/BuchungssatzAbleitung.java` → nur Task 7.
- `PC/types.ts` → nur Task 9 (deshalb legt Task 9 alle neuen optionalen Felder
  gleich mit an).
- `PC/pages/BelegeKasseEditor.tsx` → Task 9, danach nur noch Task 15.
- `PC/components/kasse/KasseShortcuts.tsx` → Task 9, dann Task 12, dann
  Task 14 (drei verschiedene Runden, nie parallel).
- `PC/components/kasse/KassenbuchJournal.tsx` und `KassenbuchTab.tsx` →
  Task 9, dann Task 11.
- `PC/components/kasse/NeueBuchungDialog.tsx` → Task 9, dann Task 12; das darin
  liegende `LohnZahlungModal` wird von Task 14 nur **importiert**, nicht
  geändert.
- `PC/components/kasse/BelegDetailModal.tsx` und `VorschlagsChip.tsx` →
  Task 9, dann Task 13.
- `PC/components/kasse/KasseEinstellungenDialog.tsx` → Task 9, dann Task 14.
- `PCE/kasse-refactoring.spec.ts` → Task 9, dann Task 11 (die Spec sichert die
  alten T-Konto-Texte zu).
- `T/service/BelegServiceTest.java`, `BelegServiceKasseValidationTest.java`,
  `KassenbuchFestschreibungTest.java` → gehören Task 5 (dort ändert sich die
  Konstruktor-Signatur). Task 1 und Task 3 fahren sie nur als Gate, ändern sie
  aber nicht.

---

## Abschnittsübersicht

Vom Abschnitts-Agenten ergänzt (09.09.2026), auf Basis der Consumes-Kette aus
der Tabelle oben und der Datei-Tabelle unten. 4 Abschnitte, 17 Tasks — die
Ketten erzwingen nicht mehr, jeder Abschnitt ist so breit wie die
Datei-Trennung erlaubt.

| Abschnitt | Tasks | Frontend? | Design-Reviewer | Playwright-Ports |
| --- | --- | --- | --- | --- |
| 1 | 1, 2, 9, 10, 16, 17 | ja (9, 10, 16) | ja | 5201 (9), 5202 (10), 5261 (16, react-zeiterfassung) |
| 2 | 3, 4, 5, 6, 7, 11 | ja (11) | ja | 5203 (11) |
| 3 | 8, 12, 13 | ja (12, 13) | ja | 5204 (12), 5205 (13) |
| 4 | 14, 15 | ja (14, 15) | ja | 5206 (14), 5207 (15) |

Branch- und Worktree-Namen stehen je Task im eigenen `### Task N`-Block
(`Branch:`/`Worktree:` direkt vor `Files:`). Muster:
`kasse/task-<N>-<stichwort>` bzw.
`C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/kasse-task-<N>`. Die Worktrees
legt der Orchestrator an, nicht die Coding-Agenten.

**Warum genau diese vier Ebenen:** Ebene 1 sind alle Tasks ohne `Consumes`
(1, 2, 9, 10, 16) plus Task 17, der laut Spec nur fachlich (nicht dateilich)
an Task 3/7/8 hängt und dessen vier Dateien mit keinem anderen Task geteilt
sind (Begründung im Kopftext von Abschnitt 1). Ebene 2 sind alle Tasks, deren
komplette `Consumes`-Kette in Ebene 1 steckt (3 braucht 1+2, 4/5/6/7 brauchen
1, 11 braucht 9). Ebene 3 sind die Tasks, die zusätzlich etwas aus Ebene 2
brauchen (8 braucht 7, 12 braucht 3, 13 braucht 4+5). Ebene 4 sind die beiden
Tasks, die auf Ebene 3 aufbauen (14 braucht 12, 15 braucht 8). Innerhalb jeder
Ebene teilt sich kein Dateipaar zwei Tasks — siehe Datei-Tabelle.

## Datei-Tabelle

Programmatisch aus den `- Files:`-Blöcken aller 17 Tasks gebaut (Skript lief
über den kompletten Plan, ein Fund pro Zeile mit Backtick-Pfad). 75 Dateien
insgesamt, davon 9 mit mehr als einem Task — genau diese 9 sind der Grund für
die Abschnittsgrenzen 1|2, 2|3 bzw. 3|4 bei den jeweiligen Tasks. Jede
Mehrfach-Datei steht laut Spalte "Abschnitte" in **unterschiedlichen**
Abschnitten, nie zweimal im selben — das ist die programmatische Bestätigung
der Datei-Trennungsregel.

| Datei | Tasks | Abschnitte |
| --- | --- | --- |
| `B/controller/BelegController.java` | 3 | 2 |
| `B/controller/KasseShortcutController.java` | 1 | 1 |
| `B/controller/KassenbuchungController.java` | 3 | 2 |
| `B/controller/SteuerberaterExportController.java` | 8 | 3 |
| `B/domain/Beleg.java` | 1 | 1 |
| `B/domain/BelegQuelle.java` | 1 | 1 |
| `B/domain/KasseEinstellung.java` | 1 | 1 |
| `B/dto/BelegDto.java` | 1 | 1 |
| `B/dto/KassenbuchungDto.java` | 3 | 2 |
| `B/dto/SteuerberaterPaketDto.java` | 8 | 3 |
| `B/repository/BelegRepository.java` | 5 | 2 |
| `B/service/BelegKiAnalyseService.java` | 4 | 2 |
| `B/service/BelegKiKostenkontoService.java` | 6 | 2 |
| `B/service/BelegPdfService.java` | 2 | 1 |
| `B/service/BelegService.java` | 5 | 2 |
| `B/service/BelegVorschlagService.java` | 5 | 2 |
| `B/service/BuchungssatzAbleitung.java` | 7 | 2 |
| `B/service/DatevExportService.java` | 7 | 2 |
| `B/service/KassenbuchungService.java` | 3 | 2 |
| `B/service/SteuerberaterExportService.java` | 8 | 3 |
| `B/service/VerfahrensdokumentationService.java` | 17 | 1 |
| `B/service/Wirtschaftsjahr.java` | 7 | 2 |
| `B/service/ZahlungsartMapper.java` | 1 | 1 |
| `MOB/pages/BelegScannerPage.tsx` | 16 | 1 |
| `PC/components/SteuerberaterBelegExportModal.tsx` | 15 | 4 |
| `PC/components/kasse/BelegDetailModal.tsx` | **9**, **13** (mehrfach, siehe Hinweis unten) | 1, 3 |
| `PC/components/kasse/KasseAnleitungDialog.tsx` | 11 | 2 |
| `PC/components/kasse/KasseEinstellungenDialog.tsx` | **9**, **14** (mehrfach, siehe Hinweis unten) | 1, 4 |
| `PC/components/kasse/KasseErklaerkasten.tsx` | 11 | 2 |
| `PC/components/kasse/KasseShortcuts.tsx` | **9**, **12**, **14** (mehrfach, siehe Hinweis unten) | 1, 3, 4 |
| `PC/components/kasse/KassenbuchJournal.tsx` | **9**, **11** (mehrfach, siehe Hinweis unten) | 1, 2 |
| `PC/components/kasse/KassenbuchTab.tsx` | **9**, **11** (mehrfach, siehe Hinweis unten) | 1, 2 |
| `PC/components/kasse/NeueBuchungDialog.tsx` | **9**, **12** (mehrfach, siehe Hinweis unten) | 1, 3 |
| `PC/components/kasse/SteuerberaterPaketDialog.tsx` | 15 | 4 |
| `PC/components/kasse/VorschlagsChip.tsx` | **9**, **13** (mehrfach, siehe Hinweis unten) | 1, 3 |
| `PC/components/kasse/belegFormat.test.ts` | 9 | 1 |
| `PC/components/kasse/belegFormat.ts` | 9 | 1 |
| `PC/components/kasse/journalSaldo.test.ts` | 11 | 2 |
| `PC/components/kasse/journalSaldo.ts` | 11 | 2 |
| `PC/components/kasse/neueBuchungRegeln.test.ts` | 12 | 3 |
| `PC/components/kasse/neueBuchungRegeln.ts` | 12 | 3 |
| `PC/components/kasse/zahlungsartRegeln.test.ts` | 13 | 3 |
| `PC/components/kasse/zahlungsartRegeln.ts` | 13 | 3 |
| `PC/components/ui/select-custom.test.tsx` | 10 | 1 |
| `PC/components/ui/select-custom.tsx` | 10 | 1 |
| `PC/pages/BelegeKasseEditor.tsx` | **9**, **15** (mehrfach, siehe Hinweis unten) | 1, 4 |
| `PC/types.ts` | 9 | 1 |
| `PCE/beleg-pruefen.spec.ts` | 13 | 3 |
| `PCE/dropdown-breite.spec.ts` | 10 | 1 |
| `PCE/kasse-einstellungen.spec.ts` | 14 | 4 |
| `PCE/kasse-refactoring.spec.ts` | **9**, **11** (mehrfach, siehe Hinweis unten) | 1, 2 |
| `PCE/kassenbuch-journal.spec.ts` | 11 | 2 |
| `PCE/neue-buchung.spec.ts` | 12 | 3 |
| `PCE/steuerberater-paket.spec.ts` | 15 | 4 |
| `T/controller/KassenbuchungControllerTest.java` | 3 | 2 |
| `T/controller/SteuerberaterExportControllerTest.java` | 8 | 3 |
| `T/db/KasseBelegeMigrationTest.java` | 1 | 1 |
| `T/service/BelegKiAnalyseServiceTest.java` | 4 | 2 |
| `T/service/BelegKiKostenkontoServiceTest.java` | 6 | 2 |
| `T/service/BelegPdfServiceTest.java` | 2 | 1 |
| `T/service/BelegServiceZahlungsstatusTest.java` | 5 | 2 |
| `T/service/BelegVorschlagServiceTest.java` | 5 | 2 |
| `T/service/BuchungssatzAbleitungTest.java` | 7 | 2 |
| `T/service/DatevExportServiceTest.java` | 7 | 2 |
| `T/service/KassenbuchungServiceTest.java` | 3 | 2 |
| `T/service/SteuerberaterExportServiceTest.java` | 8 | 3 |
| `T/service/VerfahrensdokumentationServiceTest.java` | 17 | 1 |
| `T/service/WirtschaftsjahrTest.java` | 7 | 2 |
| `T/service/ZahlungsartMapperTest.java` | 1 | 1 |
| `docs/GOBD_COMPLIANCE.md` | 17 | 1 |
| `docs/KASSE_ANLEITUNG.md` | 11 | 2 |
| `docs/KASSE_BUCHHALTUNG.md` | 17 | 1 |
| `react-zeiterfassung/e2e/beleg-scanner-polling.spec.ts` | 16 | 1 |
| `src/main/resources/db/migration/V372__kasse_buchungen_und_export.sql` | 1 | 1 |
| `src/test/resources/datev/buchungsstapel-golden.csv` | 7 | 2 |

**Hinweis zu den Mehrfach-Dateien:** jede der neun Dateien mit mehr als einem
Task gehört zur Kette `KasseShortcuts.tsx`/`BelegeKasseEditor.tsx` und den
daraus per Task 9 extrahierten Komponenten (`BelegDetailModal.tsx`,
`KasseEinstellungenDialog.tsx`, `KassenbuchJournal.tsx`, `KassenbuchTab.tsx`,
`NeueBuchungDialog.tsx`, `VorschlagsChip.tsx`) sowie der Spec-sichernden
`PCE/kasse-refactoring.spec.ts`. Task 9 legt diese Dateien in Abschnitt 1 neu
an bzw. schneidet sie zu; die fachlichen Folge-Tasks (11, 12, 13, 14, 15)
ändern sie erst in einer **späteren** Runde weiter — nie in derselben wie
Task 9 oder untereinander. Das deckt sich mit den bereits oben in
"Abhängigkeiten auf einen Blick" dokumentierten Datei-Überschneidungen.

---

## Log

<wird während der Ausführung NICHT hier befüllt — siehe
`docs/superpowers/plans/2026-09-09-kasse-belege-log.md`. Dieser Abschnitt
bleibt für eine kurze Abschluss-Zusammenfassung pro Abschnitt durch den
Review-Agenten reserviert.>
