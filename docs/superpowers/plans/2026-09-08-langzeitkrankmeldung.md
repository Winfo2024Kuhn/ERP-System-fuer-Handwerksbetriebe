# Plan: Langzeitkrankmeldung

Issue: #91 — https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/91
Feature-Branch: `feature/langzeitkrankmeldung`
Kontext-Log: `docs/superpowers/plans/2026-09-08-langzeitkrankmeldung-log.md`
Spec: `docs/superpowers/specs/2026-09-08-langzeitkrankmeldung.md`
Design (abgenommen): `docs/superpowers/specs/2026-09-08-langzeitkrankmeldung-brainstorming.md`

Abschnitts-/Worktree-Einteilung: neun Abschnitte (siehe unten), Grenzen in
aufsteigender Task-Reihenfolge gezogen. Die `Consumes`-Angaben je Task bleiben
die harte Reihenfolge-Vorgabe, die Abschnittsgrenzen setzen sie um.

**Branch-Namen weichen von `feature/langzeitkrankmeldung/task-<N>` ab:**
`feature/langzeitkrankmeldung` existiert schon als Branch (siehe Kopfzeile
oben) — Git speichert Branches als Dateien, `feature/langzeitkrankmeldung/task-1`
lässt sich deshalb nicht anlegen (`cannot lock ref`, weil `langzeitkrankmeldung`
nicht gleichzeitig Datei und Verzeichnis sein kann; dokumentiert in
`.claude/skills/loese-problem/references/plan-format.md`, Abschnitt
„Branch-Namen"). Task-Branches heißen deshalb `lzk/task-<N>-<stichwort>`,
Worktrees `../wt/lzk-task-<N>` (= `C:\Users\MarvinKuhn\dev\ERP-für-Handwerker\wt\lzk-task-<N>`
— Präfix `lzk-` bewusst gewählt, damit nichts mit den 13 vorhandenen
Worktree-Verzeichnissen kollidiert). Jeder Task-Branch zweigt vom Stand von
`feature/langzeitkrankmeldung` **zu Beginn seines Abschnitts** ab, also nach
dem Merge aller Vorgänger-Abschnitte.

---

## Entscheidungen zu den fünf offenen Punkten der Spec

Diese fünf Punkte sind hier **entschieden**, nicht mehr offen. Coding-Agenten
setzen sie um und diskutieren sie nicht neu.

### E1 — `UrlaubsantragService.java:112` wird umgestellt

`UrlaubsantragService.approveAntrag()` (Zeile 231–286) schreibt beim Genehmigen
je Tag eine `Abwesenheit` mit `stunden = zeitkonto.getSollstundenFuerTag(...)`.
Diese Stunden sind die **Gegenbuchung zum Tagessoll**: im Saldo steht
`Ist + Abwesenheit + Feiertag + Korrektur − Soll` (siehe
`MonatsSaldo.getDifferenz()`, `domain/MonatsSaldo.java:105`).

Während einer Wiedereingliederung sinkt das Soll auf die Stufenplan-Stunden.
Bliebe die Urlaubsgutschrift bei 8 h, entstünden pro Urlaubstag 6 Phantom-
Überstunden — dasselbe Vorzeichen-Problem wie beim Krankheitsfall, nur
andersherum. Deshalb: **umstellen** auf `tagesSollService.arbeitsSoll(...)`.

`VerrechnungslohnService` bleibt dagegen ausgenommen, weil es dort um das
**Jahres-Normalsoll** geht (was ein Vollzeit-Mitarbeiter normalerweise leisten
würde), nicht um das tatsächliche Tagessoll — die Ausnahme aus dem Design
bleibt unverändert bestehen.

Nicht betroffen: das Urlaubs**kontingent**. `getResturlaub()` zählt
Urlaubs**tage** (`abwesenheitRepository.countByMitarbeiterIdAndTypAndJahr`),
nicht Stunden. Ein Urlaubstag in der Wiedereingliederung kostet weiterhin einen
vollen Urlaubstag. Das ist Absicht und gehört in den Javadoc-Kommentar.

Die Umstellungsliste hat damit **sechs** Aufrufer statt fünf:
`ZeitkontoService`, `MonatsSaldoService`, `AbwesenheitService`,
`ZeiterfassungApiService`, `ZeitverwaltungController`, `UrlaubsantragService`.

### E2 — Zuschnitt von `TagesSollService`: drei Begriffe, kein „einer für alles"

Die Spec hat recht: die drei Feiertagsblöcke sind **nicht** wortgleich. Es gibt
zwei verschiedene Zwecke, und ein naives „Feiertag → 0" würde die Saldo-Rechnung
zerstören (Soll sinkt um 8, die Gutschrift auf der Ist-Seite bleibt → +8
Phantom-Überstunden pro Feiertag). `TagesSollService` bietet deshalb **drei
benannte Größen** an:

| Begriff | Bedeutung | ersetzt heute |
| --- | --- | --- |
| `periodenSoll` | Was der Mitarbeiter laut Vertrag/Stufenplan schuldet. Feiertag zählt als bezahlter Arbeitstag, halber Feiertag 50 %. | `ZeitkontoService.berechneSollstundenFuerZeitraum` (Zeile 113–130) |
| `feiertagsGutschrift` | Bezahlte Feiertagsstunden auf der Ist-Seite. 0, wenn kein Feiertag oder kein Arbeitstag. | `MonatsSaldoService.berechneFeiertagsStunden` (258–274), `ZeiterfassungApiService.berechneFeiertagsStunden` (1137–1158) |
| `arbeitsSoll` | Was tatsächlich zu leisten ist: `periodenSoll − feiertagsGutschrift`. | `ZeitverwaltungController:502`, `AbwesenheitService:60`, `UrlaubsantragService:112` |

Rechenregel (eine gemeinsame Basis, daraus alle drei):

```
tagesBasis(tag):
    roh = konto.getSollstundenFuerTag(tag.getDayOfWeek().getValue())   // null -> 0
    wenn roh <= 0                       -> 0            // Wochenende bleibt Wochenende
    wenn WIEDEREINGLIEDERUNG am tag     -> min(phase.stundenProTag, roh)
    sonst                               -> roh

periodenSoll(tag)        = istHalberFeiertag ? tagesBasis / 2 (2 Nachkommastellen, HALF_UP) : tagesBasis
feiertagsGutschrift(tag) = (tagesBasis == 0 || !istFeiertag) ? 0
                           : istHalberFeiertag ? tagesBasis / 2 (2 NK, HALF_UP) : tagesBasis
arbeitsSoll(tag)         = periodenSoll(tag) − feiertagsGutschrift(tag)
```

Ohne laufende Langzeitkrankmeldung ist `tagesBasis == roh`, damit sind
`periodenSoll` und `feiertagsGutschrift` **bitgleich** zum heutigen Code
(inklusive der `divide(2, 2, HALF_UP)`-Rundung). Genau das nageln die
Charakterisierungs-Tests aus Task 2 fest.

Das `min(phase.stundenProTag, roh)` ist ein Sicherheitsnetz: die Validierung in
Task 4 verbietet Stufenplan-Stunden über dem Tagessoll ohnehin, aber ein
Altdatensatz darf das Soll nie **erhöhen**.

### E3 — Feiertag + Wiedereingliederung: der Stufenplan-Wert ist die Basis, der Feiertag reduziert ihn zusätzlich

Der Stufenplan-Wert ist **kein** fester Override über den Feiertag hinweg. Er
ersetzt nur den Zeitkonto-Wert als Tagesbasis; die Feiertagsbehandlung läuft
danach wie bei jedem anderen Tag.

Ergebnis bei 2 h/Tag Wiedereingliederung an einem vollen Feiertag:
`periodenSoll = 2`, `feiertagsGutschrift = 2`, `arbeitsSoll = 0`.
Der Mitarbeiter arbeitet nicht, baut aber auch keine Minusstunden auf — genau
das Ziel des Features. Ein „fester Override" (arbeitsSoll = 2 am Feiertag)
würde ihn an jedem Feiertag 2 Stunden ins Minus schieben, obwohl der Betrieb zu
hat. Fachlich falsch, deshalb verworfen.

Bei einem halben Feiertag (Heiligabend): `periodenSoll = 1`,
`feiertagsGutschrift = 1`, `arbeitsSoll = 0`. Das ist dieselbe Mechanik, die
der Bestand heute schon für den normalen Mitarbeiter fährt (Soll 4,
Gutschrift 4, netto 0) — keine neue Regel, nur konsequent weitergezogen.

### E4 — Statuswerte und Übergänge

`domain/LangzeitkrankmeldungStatus.java` (Java-Enum, in MySQL native
`ENUM(...)`-Spalte — Pflicht aus `BACKEND_ARCH.md`):

| Wert | Bedeutung in der Oberfläche |
| --- | --- |
| `LAUFEND` | „Läuft noch" — `ende` ist leer |
| `BEENDET` | „Wieder voll im Einsatz" — `ende` gesetzt |
| `ABGEBROCHEN` | „Zurückgenommen" — Fehleingabe, bleibt für die Nachvollziehbarkeit stehen |

Erlaubte Übergänge (alles andere wirft `IllegalStateException` mit Klartext):

- (neu) → `LAUFEND`
- `LAUFEND` → `BEENDET` (Büro klickt „Wieder voll im Einsatz", setzt `ende`;
  alle Phasen ohne `bisDatum` werden auf `ende` geschlossen)
- `LAUFEND` → `ABGEBROCHEN`
- `BEENDET` → `LAUFEND` (Korrektur: Rückkehr war zu früh; `ende` wird geleert,
  nur erlaubt, wenn danach keine überlappende andere Meldung existiert)
- `ABGEBROCHEN` → Endzustand, keine Rückkehr. Wer sich vertan hat, legt neu an.

Wirkung auf die Berechnung: `LAUFEND` und `BEENDET` zählen für
`TagesSollService` und Verrechnungslohn, `ABGEBROCHEN` **nie**. Die
Überlappungsprüfung ignoriert `ABGEBROCHEN` — Vorbild ist
`UrlaubsantragRepository.findOverlapping` (Zeile 20–25), die `ABGELEHNT`
genauso ausschließt.

Kein `GEPLANT`, kein `PAUSIERT`. Es gibt keinen Hintergrundjob (Nicht-Ziel der
Spec), also braucht es auch keine Zwischenzustände, die nur ein Job auflösen
könnte.

Phasentyp-Enum `domain/LangzeitkrankmeldungPhaseTyp.java`: `LOHNFORTZAHLUNG`,
`KRANKENGELD`, `WIEDEREINGLIEDERUNG`. Ein Phasenwechsel ist immer das Anlegen
einer neuen Phase, nie ein Statuswechsel an der Meldung.

### E5 — Menüposition und Icon

- **Route:** `/langzeitkrankmeldungen`
- **Icon:** `Stethoscope` (lucide-react). Bewusst kein neues Icon: das Projekt
  benutzt `Stethoscope` schon für Krankheit in
  `react-pc-frontend/src/pages/Urlaubsantraege.tsx:174` und
  `react-zeiterfassung/src/pages/AbwesenheitenPage.tsx:28`. Das Design-System
  (`.claude/skills/handwerkerprogramm-design/README.md:237–251`) verlangt
  Lucide und Wiederverwendung des vorhandenen Vokabulars.
- **Menüname:** „Lange Krankheit". Kurz, sofort verständlich, kein
  Fachchinesisch. „Langzeitkrankmeldungen" bleibt der Name im Code und in der
  Entität, taucht aber nicht in der Oberfläche auf.
- **Desktop-Ribbon** (`react-pc-frontend/src/components/layout/RibbonNav.tsx`):
  Kategorie „Zeiterfassung", die Untergruppe `label: 'Urlaub'` (Zeile 124–129)
  wird zu `label: 'Abwesenheiten'` umbenannt und bekommt einen zweiten Eintrag
  direkt unter „Anträge":
  `{ name: 'Lange Krankheit', href: '/langzeitkrankmeldungen', icon: Stethoscope }`.
  Die Umbenennung ist nötig, weil „Urlaub → Lange Krankheit" unsinnig wäre.
- **Mobile-Bottom-Nav** (`.../MobileBottomNav.tsx`): in
  `SUBMENU_ITEMS['/zeitbuchungen']` (Zeile 37–42) direkt nach dem Eintrag
  „Urlaub" derselbe Eintrag. Kein neuer Primär-Tab.
- **Seitenkopf:** Kategorie „ABWESENHEITEN", H1 „LANGE KRANKHEIT",
  Beschreibung „Lohnfortzahlung, Krankengeld und Wiedereingliederung im Blick"
  (Page-Header-Pattern aus `FRONTEND_UI.md`).

---

## Bewusste Verhaltensänderungen (müssen im Review sichtbar sein)

Die Bündelung darf nichts still verändern. Drei Stellen ändern sich trotzdem —
jede hier benannt, jede mit einem eigenen Test:

1. **`ZeitverwaltungController` /kalender, halbe Feiertage.** Heute liefert
   Zeile 512 an *jedem* Feiertag die **vollen** Sollstunden als Ist-Stunden,
   auch am Heiligabend, während `sollStundenMonat` (Zeile 543, aus
   `berechneSollstundenFuerMonat`) den halben Feiertag korrekt halbiert.
   Ergebnis heute: +4 h Phantom-Überstunden pro halbem Feiertag, und der
   Kalender widerspricht der Monatsübersicht (`MonatsSaldoService` rechnet
   Soll 4 / Gutschrift 4 → netto 0). Nach der Umstellung liefert
   `feiertagsGutschrift` auch hier 4 h. **Das ist ein Bugfix, kein Rückschritt**
   — er wird in Task 11 als eigener Test festgehalten und muss im Kontext-Log
   auftauchen.
2. **Urlaubsstunden während einer Wiedereingliederung** folgen dem Stufenplan
   (siehe E1). Ohne Langzeitkrankmeldung ändert sich nichts.
3. **Krankheitsstunden im Verrechnungslohn**: Krankengeld- und
   Wiedereingliederungstage fallen aus Jahressoll und Lohnkosten heraus
   (Task 13). Ohne Langzeitkrankmeldung ändert sich nichts.

Alles andere muss zahlengleich bleiben. Dafür sind die Charakterisierungs-Tests
in Task 2 da.

---

## Global Constraints

**Pflichtlektüre vor dem ersten Edit** (Hook `check-doc-read.ps1` blockt sonst
mit Exit 2):

| Task fasst an | Pflicht-`Read` vorher |
| --- | --- |
| `*.java` (auch Tests, auch Config) | `docs/agent instructions/docs/BACKEND_ARCH.md` |
| `*Test.java` | zusätzlich `docs/agent instructions/docs/TESTING_SECURITY.md` |
| `*.tsx`/`*.ts` in `react-pc-frontend/` oder `react-zeiterfassung/` | `docs/agent instructions/docs/FRONTEND_UI.md` **und** `Skill` → `handwerkerprogramm-design` |
| jeder Task | `.claude/skills/loese-problem/references/kriterien.md` |

**Projektregeln, die für jeden Task gelten:**

- Constructor Injection (`@RequiredArgsConstructor`/`@AllArgsConstructor`), keine
  neue Field-Injection. `ZeiterfassungApiService` mischt beides schon (Felder ab
  Zeile 1191) — neue Abhängigkeiten kommen trotzdem in den Konstruktor-Block.
- SQL nur parametrisiert (`@Query` mit `:param`).
- Flyway: neue Datei `V367__langzeitkrankmeldung.sql`, **idempotent**,
  bestehende Migrationen nie ändern. Java-Enums werden in MySQL als native
  `ENUM('A','B')`-Spalte angelegt, sonst scheitert `ddl-auto=validate` beim
  Start (`BACKEND_ARCH.md`, Vorbild `V366__datensatz_lock_entitaet_typ_enum.sql`).
- **Entity-Feld und Migrationsspalte gehören in denselben Task.** Das
  Testprofil (`src/test/resources/application.properties`) hat
  `spring.flyway.enabled=false` und `ddl-auto=create-drop` — eine fehlende
  Spalte fällt in **keinem** Test auf, sondern erst beim echten Start mit
  `validate`. Deshalb liegen alle neuen Entities, alle neuen Spalten an
  `Abwesenheit` und `V367` in Task 1.
- DSGVO: nur Dummy-Daten in Tests (`Max Mustermann`). **Keine Diagnose, kein
  Klarname, keine Notiz-Inhalte im Log** — Logs schreiben `meldungId` und
  `mitarbeiterId`, sonst nichts. Das Notizfeld heißt in der Oberfläche wörtlich
  „Interne Notiz — bitte keine Diagnosen eintragen".
- Performance: kein N+1. Repository-Abfragen über Zeiträume liefern **eine**
  Liste, die im Speicher pro Tag ausgewertet wird; Listen mit Kind-Entitäten
  nutzen `JOIN FETCH`.
- Wording in der Oberfläche: „Lohnfortzahlung durch den Betrieb", „Krankengeld
  der Krankenkasse", „Wiedereingliederung", „Wieder voll im Einsatz". Kein
  „Entgeltfortzahlungszeitraum", kein „AU-Zeitraum".

**Gates je Coding-Agent** (nie die volle Suite — das machen die Reviewer):

- Backend: `./mvnw -B test -Dtest=MeinTest` für die eigenen Testklassen.
- Frontend: `npx vitest run <datei>` **plus** `npm run lint` **plus**
  `npm run build` im jeweiligen Ordner. Alle drei sind Pflicht-Gates, `lint`
  wird am häufigsten gerissen.
- `react-pc-frontend`: zusätzlich die eigene Playwright-Spec auf eigenem Port:
  `E2E_PORT=<port> npx playwright test e2e/<spec>`.
- `react-zeiterfassung` hat **kein** Playwright (weder Dependency noch
  `playwright.config.ts`, verifiziert). Dort ist `lint` + `vitest` + `build`
  das vollständige Gate. Kein Task richtet Playwright dort neu ein.

**Baseline (gemessen, gilt für alle):**

- `./mvnw -B test`: 2461 Tests, **4 vorbestehende Errors** —
  `AuditChainRepairIntegrationTest` (2) und `AuditHashRoundtripDiagnoseTest` (2),
  alle `CannotCreateTransaction: Could not open JPA EntityManager` (brauchen
  eine echte DB). Grün heißt: genau diese 4. Der 5. ist neu und gehört dir.
- Vorbestehende Fehler nicht reparieren, nicht deaktivieren.
- Höchste Migration heute: `V366`. `V367` ist frei.

**Am Ende der Gesamtaufgabe** (nicht nach jedem Task): einmal
`./graphify update .`.

---

## Abschnitt 1 — Task 1, 2 (max. 3 Tasks, disjunkte Dateien)

Basis: `feature/langzeitkrankmeldung` (aktueller Stand vor diesem Vorhaben).
Frontend betroffen: nein.

### Task 1 — Datenmodell, Migration V367, Repositories
- Branch: `lzk/task-1-datenmodell`
- Worktree: `../wt/lzk-task-1`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/domain/Langzeitkrankmeldung.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/domain/LangzeitkrankmeldungPhase.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/domain/LangzeitkrankmeldungStatus.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/domain/LangzeitkrankmeldungPhaseTyp.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/domain/Abwesenheit.java` (geändert)
  - `src/main/java/org/example/kalkulationsprogramm/repository/LangzeitkrankmeldungRepository.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/repository/LangzeitkrankmeldungPhaseRepository.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/repository/AbwesenheitRepository.java` (geändert)
  - `src/main/resources/db/migration/V367__langzeitkrankmeldung.sql` (neu)
  - `src/test/java/org/example/kalkulationsprogramm/repository/LangzeitkrankmeldungRepositoryTest.java` (neu)
  - `src/test/java/org/example/kalkulationsprogramm/db/V367SchemaTest.java` (neu)
- Vorbild:
  - Entität mit `@ManyToOne` auf Mitarbeiter + Status-Enum:
    `domain/Urlaubsantrag.java` (ganze Datei, 47 Zeilen).
  - Kind-Entität und die Begründung „kein eigenes `@Version` am Kind":
    Kopfkommentar von `src/main/resources/db/migration/V364__aggregat_versionsspalten.sql`
    (Zeile 1–45).
  - Idempotenter `ALTER TABLE`-Block: `V364`, Zeile 47–53 (information_schema +
    `PREPARE`/`EXECUTE`/`DEALLOCATE`).
  - Native ENUM-Spalte: `V366__datensatz_lock_entitaet_typ_enum.sql`.
  - Repository mit Überlappungs-Query: `repository/UrlaubsantragRepository.java:20–25`.
  - `@DataJpaTest`-Stil: siehe `TESTING_SECURITY.md`, Abschnitt
    „Repository-Schicht: H2 In-Memory".
- Interfaces:
  - Produces:
    - `Langzeitkrankmeldung` — Felder: `Long id`, `Mitarbeiter mitarbeiter`
      (`@ManyToOne(fetch = LAZY, optional = false)`, `@JoinColumn(name = "mitarbeiter_id")`,
      dazu `@JsonIncludeProperties({"id","vorname","nachname"})` analog
      `Urlaubsantrag.java:19–22`), `LocalDate beginn` (not null),
      `LocalDate ende` (nullable), `LangzeitkrankmeldungStatus status`
      (`@Enumerated(STRING)`, not null, default `LAUFEND`),
      `LocalDate lohnfortzahlungBis` (not null), `String notiz`
      (`@Column(length = 500)`), `@Version Long version`,
      `@OneToMany(mappedBy = "langzeitkrankmeldung", cascade = ALL, orphanRemoval = true)
      List<LangzeitkrankmeldungPhase> phasen = new ArrayList<>()`.
    - `LangzeitkrankmeldungPhase` — `Long id`,
      `@ManyToOne(fetch = LAZY, optional = false) Langzeitkrankmeldung langzeitkrankmeldung`,
      `LangzeitkrankmeldungPhaseTyp typ` (`@Enumerated(STRING)`, not null),
      `LocalDate vonDatum` (not null), `LocalDate bisDatum` (nullable),
      `BigDecimal stundenProTag` (`@Column(precision = 4, scale = 2)`, nullable).
      **Kein** `@Version` (Speicherung über den Wurzel-Aggregat).
    - `enum LangzeitkrankmeldungStatus { LAUFEND, BEENDET, ABGEBROCHEN }`
    - `enum LangzeitkrankmeldungPhaseTyp { LOHNFORTZAHLUNG, KRANKENGELD, WIEDEREINGLIEDERUNG }`
    - `Abwesenheit` +2 Felder, exakt nach dem Muster der vorhandenen
      `urlaubsantrag`-Beziehung (`domain/Abwesenheit.java:31–33`):
      `@ManyToOne(fetch = LAZY) @JoinColumn(name = "langzeitkrankmeldung_id") Langzeitkrankmeldung langzeitkrankmeldung;`
      und
      `@ManyToOne(fetch = LAZY) @JoinColumn(name = "langzeitkrankmeldung_phase_id") LangzeitkrankmeldungPhase langzeitkrankmeldungPhase;`
    - `LangzeitkrankmeldungRepository extends JpaRepository<Langzeitkrankmeldung, Long>`:
      ```java
      List<Langzeitkrankmeldung> findByMitarbeiterIdOrderByBeginnDesc(Long mitarbeiterId);

      @Query("SELECT l FROM Langzeitkrankmeldung l JOIN FETCH l.mitarbeiter "
           + "LEFT JOIN FETCH l.phasen WHERE l.status IN :status ORDER BY l.beginn DESC")
      List<Langzeitkrankmeldung> findMitPhasen(@Param("status") Collection<LangzeitkrankmeldungStatus> status);

      @Query("SELECT l FROM Langzeitkrankmeldung l JOIN FETCH l.mitarbeiter "
           + "LEFT JOIN FETCH l.phasen WHERE l.id = :id")
      Optional<Langzeitkrankmeldung> findMitPhasenById(@Param("id") Long id);

      @Query("SELECT l FROM Langzeitkrankmeldung l WHERE l.mitarbeiter.id = :mitarbeiterId "
           + "AND l.status <> org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungStatus.ABGEBROCHEN "
           + "AND l.beginn <= :bis AND (l.ende IS NULL OR l.ende >= :von)")
      List<Langzeitkrankmeldung> findUeberlappende(@Param("mitarbeiterId") Long mitarbeiterId,
                                                   @Param("von") LocalDate von,
                                                   @Param("bis") LocalDate bis);
      ```
    - `LangzeitkrankmeldungPhaseRepository extends JpaRepository<LangzeitkrankmeldungPhase, Long>`:
      ```java
      List<LangzeitkrankmeldungPhase> findByLangzeitkrankmeldungIdOrderByVonDatumAsc(Long meldungId);

      /** Alle Phasen aktiver (nicht abgebrochener) Meldungen, die den Zeitraum beruehren.
       *  EINE Abfrage je Zeitraum - TagesSollService wertet sie im Speicher pro Tag aus. */
      @Query("SELECT p FROM LangzeitkrankmeldungPhase p "
           + "WHERE p.langzeitkrankmeldung.mitarbeiter.id = :mitarbeiterId "
           + "AND p.langzeitkrankmeldung.status <> org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungStatus.ABGEBROCHEN "
           + "AND p.vonDatum <= :bis AND (p.bisDatum IS NULL OR p.bisDatum >= :von) "
           + "ORDER BY p.vonDatum ASC")
      List<LangzeitkrankmeldungPhase> findImZeitraum(@Param("mitarbeiterId") Long mitarbeiterId,
                                                     @Param("von") LocalDate von,
                                                     @Param("bis") LocalDate bis);
      ```
    - `AbwesenheitRepository` +2 Methoden:
      ```java
      @Query("SELECT a FROM Abwesenheit a WHERE a.mitarbeiter.id = :mitarbeiterId AND a.typ = :typ "
           + "AND a.datum >= :von AND a.datum <= :bis ORDER BY a.datum ASC")
      List<Abwesenheit> findByMitarbeiterIdAndTypAndDatumBetween(@Param("mitarbeiterId") Long mitarbeiterId,
                                                                 @Param("typ") AbwesenheitsTyp typ,
                                                                 @Param("von") LocalDate von,
                                                                 @Param("bis") LocalDate bis);

      /** Summe wie sumStundenByMitarbeiterIdAndTypAndDatumBetween, aber ohne die Tage,
       *  die an einer Phase der genannten Typen haengen (Verrechnungslohn, Task 13). */
      @Query("SELECT COALESCE(SUM(a.stunden), 0) FROM Abwesenheit a WHERE a.mitarbeiter.id = :mitarbeiterId "
           + "AND a.typ = :typ AND a.datum >= :von AND a.datum <= :bis "
           + "AND (a.langzeitkrankmeldungPhase IS NULL OR a.langzeitkrankmeldungPhase.typ NOT IN :ausgeschlossen)")
      BigDecimal sumStundenOhnePhasenTypen(@Param("mitarbeiterId") Long mitarbeiterId,
                                           @Param("typ") AbwesenheitsTyp typ,
                                           @Param("von") LocalDate von,
                                           @Param("bis") LocalDate bis,
                                           @Param("ausgeschlossen") Collection<LangzeitkrankmeldungPhaseTyp> ausgeschlossen);
      ```
  - Consumes: nichts.
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md`, `TESTING_SECURITY.md`, `kriterien.md`.
  - [ ] `V367SchemaTest.java` zuerst schreiben (fällt anfangs rot): liest
        `src/main/resources/db/migration/V367__langzeitkrankmeldung.sql` als
        Text und sichert zu, dass jeder dieser Spaltennamen darin vorkommt —
        `langzeitkrankmeldung`, `langzeitkrankmeldung_phase`,
        `mitarbeiter_id`, `beginn`, `ende`, `status`, `lohnfortzahlung_bis`,
        `notiz`, `version`, `typ`, `von_datum`, `bis_datum`,
        `stunden_pro_tag`, `langzeitkrankmeldung_id`,
        `langzeitkrankmeldung_phase_id` — und zusätzlich, dass die beiden
        Enum-Spalten als `ENUM(` deklariert sind, nicht als `VARCHAR`. Grund
        im Javadoc festhalten: das Testprofil fährt ohne Flyway, ein
        vergessener `ALTER TABLE` fällt sonst erst beim Produktionsstart mit
        `ddl-auto=validate` auf.
  - [ ] Die vier Domain-Dateien anlegen (Lombok `@Getter @Setter`, wie
        `Urlaubsantrag.java`). Javadoc an `Langzeitkrankmeldung`: Aggregate
        Root, `@Version` nach der Konvention aus V364; an
        `LangzeitkrankmeldungPhase`: Kind-Entität, bewusst ohne `@Version`.
  - [ ] `Abwesenheit.java` um die zwei `@ManyToOne`-Felder ergänzen — direkt
        unter dem vorhandenen `urlaubsantrag`-Feld (Zeile 31–33), gleiche
        Formatierung. `AbwesenheitsTyp` bleibt **unverändert**.
  - [ ] Die zwei Repositories anlegen und `AbwesenheitRepository` um die zwei
        Methoden erweitern (Formatierung der Datei beibehalten — sie ist
        durchgehend mit 8er-Einrückung geschrieben).
  - [ ] `V367__langzeitkrankmeldung.sql` schreiben:
        `CREATE TABLE IF NOT EXISTS langzeitkrankmeldung (...)` mit
        `status ENUM('LAUFEND','BEENDET','ABGEBROCHEN') NOT NULL`,
        `version BIGINT NOT NULL DEFAULT 0`,
        FK auf `mitarbeiter(id)`, Index `(mitarbeiter_id, beginn)`.
        `CREATE TABLE IF NOT EXISTS langzeitkrankmeldung_phase (...)` mit
        `typ ENUM('LOHNFORTZAHLUNG','KRANKENGELD','WIEDEREINGLIEDERUNG') NOT NULL`,
        `stunden_pro_tag DECIMAL(4,2) NULL`, FK auf
        `langzeitkrankmeldung(id) ON DELETE CASCADE`, Index
        `(langzeitkrankmeldung_id, von_datum)`.
        Zwei idempotente `ALTER TABLE abwesenheit ADD COLUMN`-Blöcke nach dem
        `information_schema`-Muster aus V364 (Zeile 47–53) für
        `langzeitkrankmeldung_id BIGINT NULL` und
        `langzeitkrankmeldung_phase_id BIGINT NULL`, plus zwei ebenso
        geprüfte `ADD CONSTRAINT ... FOREIGN KEY`-Blöcke (Prüfung über
        `information_schema.table_constraints`).
        Kopfkommentar wie in V364: was, warum, warum idempotent.
  - [ ] `LangzeitkrankmeldungRepositoryTest` als `@DataJpaTest`: Meldung mit
        zwei Phasen speichern und laden, `findUeberlappende` findet eine
        offene Meldung (`ende == null`), findet **keine** `ABGEBROCHEN`-Meldung,
        `findImZeitraum` liefert nur die berührten Phasen. Dummy-Daten:
        `Max Mustermann`.
  - [ ] `./mvnw -B test -Dtest=LangzeitkrankmeldungRepositoryTest,V367SchemaTest`

---

### Task 2 — Charakterisierungs-Tests: das heutige Verhalten festnageln
- Branch: `lzk/task-2-charakterisierung`
- Worktree: `../wt/lzk-task-2`

Dieser Task ändert **keine** Produktionsdatei. Er sichert vor jeder Umstellung
zahlengenau, was der Bestand heute liefert. Die Tasks 7–12 dürfen diese Tests
nur dort anfassen, wo im Abschnitt „Bewusste Verhaltensänderungen" oben eine
Änderung benannt ist — jede andere rote Zusicherung ist eine Regression.

- Files (alle neu):
  - `src/test/java/org/example/kalkulationsprogramm/service/TagesSollCharakterisierungZeitkontoTest.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/TagesSollCharakterisierungMonatsSaldoTest.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/TagesSollCharakterisierungAbwesenheitTest.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/TagesSollCharakterisierungZeiterfassungApiTest.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/TagesSollCharakterisierungUrlaubsantragTest.java`
  - `src/test/java/org/example/kalkulationsprogramm/controller/TagesSollCharakterisierungKalenderTest.java`
- Vorbild:
  - Mockito-Service-Test mit `@ExtendWith(MockitoExtension.class)` +
    `@InjectMocks`: `src/test/java/.../service/ZeitkontoServiceTest.java`
    (Zeile 26–60) und `.../AbwesenheitServiceTest.java` (Zeile 28–78,
    inkl. `stubGrunddaten()`).
  - Service mit explizitem Konstruktor statt `@InjectMocks`:
    `.../ZeiterfassungApiServiceConcurrencyTest.java:74–83` (inkl.
    `ReflectionTestUtils.setField(service, "monatsSaldoService", ...)` für die
    Field-Injection-Felder).
  - MockMvc-Controllertest: `.../controller/ZeitverwaltungControllerTest.java`
    (Zeile 38–110), `@WebMvcTest(ZeitverwaltungController.class)` +
    `@AutoConfigureMockMvc(addFilters = false)` + `@MockBean` für alle 13
    Konstruktor-Abhängigkeiten (Liste in `ZeitverwaltungController.java:53–65`).
- Interfaces:
  - Produces: sechs Testklassen, die von den Tasks 7–12 unverändert grün
    gehalten werden müssen.
  - Consumes: nichts.
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] Gemeinsame Fixture in jeder Datei: Zeitkonto Mo–Fr 8,00 h, Sa/So 0,00,
        Mitarbeiter `Max Mustermann`, ID 1. Feste Daten:
        **Mo 2026-06-01** (normaler Arbeitstag), **Sa 2026-06-06** (Wochenende),
        **Do 2026-01-01** (voller Feiertag, Arbeitstag),
        **Do 2026-12-24** (halber Feiertag, Arbeitstag),
        **Sa 2026-12-26** (Feiertag am Wochenende).
  - [ ] `...ZeitkontoTest`: `ZeitkontoService.berechneSollstundenFuerZeitraum`
        für jeden dieser Einzeltage (von == bis). Erwartet: `8`, `0`, `8`,
        `4.00`, `0`. `feiertagService.istHalberFeiertag` entsprechend stubben.
        Zusätzlich eine volle Woche Mo–So mit einem Feiertag am Mittwoch → `40`.
  - [ ] `...MonatsSaldoTest`: `MonatsSaldoService` über
        `berechneMonatsSaldo` (privat → über `getOrBerechne` mit ungültigem
        Cache) oder direkt über die öffentliche Einstiegsmethode; geprüft wird
        `saldo.getFeiertagsStunden()` für einen Monat mit genau einem vollen
        Feiertag (`8`), einem halben Feiertag (`4.00`) und einem Feiertag am
        Wochenende (`0`). Vorbild für das Stubbing:
        `src/test/java/.../service/MonatsSaldoServiceTest.java` (917 Zeilen,
        dort ist der komplette Mock-Aufbau schon vorhanden — abschauen, nicht
        neu erfinden).
  - [ ] `...AbwesenheitTest`: `AbwesenheitService.bucheAbwesenheit(1L, MONTAG,
        KRANKHEIT, false)` → `stunden == 8.00`; mit `halberTag = true` →
        `4.00`; an einem Samstag → `IllegalArgumentException` („Kein
        Arbeitstag"); an einem Feiertag → `IllegalArgumentException` („An
        Feiertagen kann keine Abwesenheit gebucht werden").
  - [ ] `...ZeiterfassungApiTest`: `ZeiterfassungApiService` konstruieren wie
        in `ZeiterfassungApiServiceConcurrencyTest:76–83`; die private
        `berechneFeiertagsStunden` über `getGesamtSaldo(token)` mit einem
        Randmonat treffen (der Zweig `istErsterMonat || istLetzterMonat` ab
        Zeile 1084 ruft `berechneAnteiligenMonatIst`, das die Methode nutzt).
        Erwartet: voller Feiertag `8`, halber `4.00`, Feiertag am Wochenende `0`.
  - [ ] `...UrlaubsantragTest`: `UrlaubsantragService.approveAntrag` für einen
        Antrag Mo–Fr → fünf `Abwesenheit`-Objekte mit je `8.00`; liegt ein
        Feiertag im Zeitraum, entstehen nur vier. Über
        `ArgumentCaptor<Abwesenheit>` auf `abwesenheitRepository.save`.
  - [ ] `...KalenderTest`: `GET /api/zeitverwaltung/kalender?mitarbeiterId=1&jahr=2026&monat=12`
        mit gestubbten Feiertagen (24.12. halbTag=true, 25.12. halbTag=false).
        Festgehalten: `tage[23].sollStunden == 0`, `tage[24].sollStunden == 0`,
        `tage[0].sollStunden == 8` (1.12.2026 ist ein Dienstag) und —
        **ausdrücklich als „heutiger Stand, ändert sich in Task 11"
        kommentiert** — `tage[23].istStunden == 8`.
  - [ ] Alle sechs Klassen laufen lassen, **alle grün gegen den unveränderten
        Bestand**: `./mvnw -B test -Dtest='TagesSollCharakterisierung*'`.
        Wird eine rot, ist die Zusicherung falsch, nicht der Bestand.

---

## Abschnitt 2 — Task 3 (max. 3 Tasks, disjunkte Dateien)

Basis: `feature/langzeitkrankmeldung` nach Merge von Abschnitt 1.
Frontend betroffen: nein.

### Task 3 — `TagesSollService`
- Branch: `lzk/task-3-tagessoll`
- Worktree: `../wt/lzk-task-3`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/service/TagesSollService.java` (neu)
  - `src/test/java/org/example/kalkulationsprogramm/service/TagesSollServiceTest.java` (neu)
- Vorbild:
  - Die drei zu bündelnden Blöcke:
    `service/ZeitkontoService.java:113–130`,
    `service/MonatsSaldoService.java:258–274`,
    `service/ZeiterfassungApiService.java:1137–1158`.
  - Halbtags-Rundung wörtlich übernehmen:
    `tagesSoll.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)`.
  - Servicestil: `service/ZeitkontoService.java:19–25`
    (`@Service @RequiredArgsConstructor`, `private final`-Felder).
- Interfaces:
  - Produces:
    ```java
    package org.example.kalkulationsprogramm.service;

    @Service
    @RequiredArgsConstructor
    public class TagesSollService {
        private final FeiertagService feiertagService;
        private final LangzeitkrankmeldungPhaseRepository phaseRepository;

        public BigDecimal periodenSoll(Long mitarbeiterId, Zeitkonto konto, LocalDate tag);
        public BigDecimal feiertagsGutschrift(Long mitarbeiterId, Zeitkonto konto, LocalDate tag);
        public BigDecimal arbeitsSoll(Long mitarbeiterId, Zeitkonto konto, LocalDate tag);
        public BigDecimal periodenSollSumme(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis);
        public BigDecimal feiertagsGutschriftSumme(Long mitarbeiterId, Zeitkonto konto, LocalDate von, LocalDate bis);
    }
    ```
  - Consumes: Task 1 (`LangzeitkrankmeldungPhase`, `LangzeitkrankmeldungPhaseRepository.findImZeitraum`).
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] `TagesSollServiceTest` zuerst: die Rechenregel aus E2 Zeile für Zeile
        absichern, mit `@Mock FeiertagService` und
        `@Mock LangzeitkrankmeldungPhaseRepository`, `@InjectMocks`.
        Pflichtfälle:
        (a) ohne Phase = heutiger Bestand für alle fünf Tagestypen aus Task 2;
        (b) Wiedereingliederung 2 h an einem normalen Montag →
            `periodenSoll 2.00`, `feiertagsGutschrift 0`, `arbeitsSoll 2.00`;
        (c) Wiedereingliederung 2 h an einem vollen Feiertag →
            `2.00 / 2.00 / 0` (**E3**);
        (d) Wiedereingliederung 2 h am halben Feiertag →
            `1.00 / 1.00 / 0`;
        (e) Wiedereingliederung an einem Samstag (Zeitkonto 0) → alles `0`;
        (f) `stundenProTag = 10` bei Zeitkonto 8 → gedeckelt auf `8`
            (Sicherheitsnetz, siehe E2);
        (g) `LOHNFORTZAHLUNG`/`KRANKENGELD`-Phase ändert nichts am Tagessoll;
        (h) **Invariante**: für jeden Tag gilt
            `arbeitsSoll == periodenSoll − feiertagsGutschrift`;
        (i) **kein N+1**: `periodenSollSumme` über 31 Tage ruft
            `phaseRepository.findImZeitraum` genau **einmal** und
            `feiertagService.getFeiertageZwischen` genau **einmal**
            (`verify(..., times(1))`).
  - [ ] Service implementieren. Privat: `tagesBasis(List<Phase>, Zeitkonto, LocalDate)`
        nach der Regel aus E2; `wiedereingliederungAm(List<Phase>, LocalDate)`
        liefert die erste Phase mit `typ == WIEDEREINGLIEDERUNG`,
        `vonDatum <= tag` und (`bisDatum == null || bisDatum >= tag`).
  - [ ] Einzeltag-Methoden rufen `phaseRepository.findImZeitraum(mitarbeiterId, tag, tag)`
        und `feiertagService.istFeiertag/istHalberFeiertag`.
  - [ ] Zeitraum-Methoden laden **einmal**:
        `phaseRepository.findImZeitraum(mitarbeiterId, von, bis)` und
        `feiertagService.getFeiertageZwischen(von, bis)` → daraus eine
        `Map<LocalDate, Feiertag>`. **Wichtig:** die Map auf
        `"BY".equals(f.getBundesland())` filtern —
        `FeiertagService.getFeiertageZwischen` (Zeile 84–91) filtert **nicht**
        nach Bundesland, `istFeiertag` (Zeile 98–101) dagegen schon. Ohne den
        Filter würde die Summenmethode anders rechnen als die Einzeltag-Methode.
        Diesen Grund als Kommentar in den Code schreiben.
  - [ ] `null`-sicher: `konto.getSollstundenFuerTag` liefert nie `null`
        (Zeile 93–105), `phase.getStundenProTag()` kann `null` sein → dann
        Phase ignorieren und mit dem Zeitkonto-Wert weiterrechnen.
  - [ ] Klassen-Javadoc: die drei Begriffe aus E2 als Tabelle, plus der Satz
        „Wer `periodenSoll` senkt, muss `feiertagsGutschrift` mitsenken —
        sonst entstehen Phantom-Überstunden."
  - [ ] `./mvnw -B test -Dtest=TagesSollServiceTest`

---

## Abschnitt 3 — Task 4 (max. 3 Tasks, disjunkte Dateien)

Basis: `feature/langzeitkrankmeldung` nach Merge von Abschnitt 2.
Frontend betroffen: nein.

### Task 4 — `LangzeitkrankmeldungService` + DTOs
- Branch: `lzk/task-4-service`
- Worktree: `../wt/lzk-task-4`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/service/LangzeitkrankmeldungService.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/dto/Langzeitkrankmeldung/LangzeitkrankmeldungDto.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/dto/Langzeitkrankmeldung/LangzeitkrankmeldungPhaseDto.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/dto/Langzeitkrankmeldung/LangzeitkrankmeldungAnlegenRequest.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/dto/Langzeitkrankmeldung/LangzeitkrankmeldungPhaseRequest.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/dto/Langzeitkrankmeldung/StufenplanTagDto.java` (neu)
  - `src/test/java/org/example/kalkulationsprogramm/service/LangzeitkrankmeldungServiceTest.java` (neu)
- Vorbild:
  - Validierung + Überlappungsprüfung + Klartext-Fehlermeldung:
    `service/UrlaubsantragService.java:29–65` (`createAntrag`).
  - Cache-Invalidierung über betroffene Monate:
    `service/UrlaubsantragService.java` → `invalidiereBetroffeneMonate(...)`
    (aufgerufen Zeile 281–283) und `MonatsSaldoService.invalidiereMonat`
    (Zeile 205–208).
  - DTO-Stil mit Lombok `@Data`: `dto/LieferantReklamationDto.java`.
  - Mockito-Servicetest: `service/AbwesenheitServiceTest.java:28–78`.
- Interfaces:
  - Produces:
    ```java
    @Service @RequiredArgsConstructor @Transactional(readOnly = true)
    public class LangzeitkrankmeldungService {
        @Transactional Langzeitkrankmeldung anlegen(Long mitarbeiterId, LocalDate beginn,
                                                    LocalDate lohnfortzahlungBis, String notiz);
        @Transactional Langzeitkrankmeldung aendern(Long id, LocalDate beginn,
                                                    LocalDate lohnfortzahlungBis, String notiz);
        @Transactional Langzeitkrankmeldung beenden(Long id, LocalDate ende);
        @Transactional Langzeitkrankmeldung wiederEroeffnen(Long id);
        @Transactional Langzeitkrankmeldung abbrechen(Long id);
        @Transactional Langzeitkrankmeldung phaseHinzufuegen(Long id, LangzeitkrankmeldungPhaseTyp typ,
                                                             LocalDate vonDatum, LocalDate bisDatum,
                                                             BigDecimal stundenProTag);
        @Transactional Langzeitkrankmeldung phaseAendern(Long id, Long phasenId, LangzeitkrankmeldungPhaseTyp typ,
                                                         LocalDate vonDatum, LocalDate bisDatum,
                                                         BigDecimal stundenProTag);
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
    DTO-Felder:
    `LangzeitkrankmeldungDto`: `Long id`, `Long mitarbeiterId`,
    `String mitarbeiterName` („Mustermann, Max"), `LocalDate beginn`,
    `LocalDate ende`, `LangzeitkrankmeldungStatus status`,
    `String statusLabel`, `LocalDate lohnfortzahlungBis`, `String notiz`,
    `Long version`, `LangzeitkrankmeldungPhaseTyp aktuellePhaseTyp`,
    `String aktuellePhaseLabel`, `Integer restTageLohnfortzahlung`
    (negativ = überschritten), `BigDecimal heuteGeplanteStunden` (nur bei
    Wiedereingliederung, sonst `null`), `LocalDate geplanteRueckkehr`,
    `List<LangzeitkrankmeldungPhaseDto> phasen`,
    `List<StufenplanTagDto> stufenplanTage`.
    `LangzeitkrankmeldungPhaseDto`: `Long id`,
    `LangzeitkrankmeldungPhaseTyp typ`, `String label`, `LocalDate vonDatum`,
    `LocalDate bisDatum`, `BigDecimal stundenProTag`.
    `StufenplanTagDto`: `LocalDate datum`, `BigDecimal geplanteStunden`,
    `BigDecimal gestempelteStunden`, `boolean ueberPlan`.
  - Consumes: Task 1 (Entities, Repositories), Task 3 (`TagesSollService`).
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] Abhängigkeiten: `LangzeitkrankmeldungRepository`,
        `LangzeitkrankmeldungPhaseRepository`, `MitarbeiterRepository`,
        `AbwesenheitRepository`, `ZeitbuchungRepository`, `ZeitkontoService`,
        `TagesSollService`, `MonatsSaldoService`. Alle `private final`.
  - [ ] `anlegen`: Mitarbeiter laden (sonst `IllegalArgumentException`);
        `findUeberlappende(mitarbeiterId, beginn, ende ?: LocalDate.MAX)`
        muss leer sein, sonst `IllegalStateException` mit Klartext
        („Für diesen Mitarbeiter läuft bereits eine Krankmeldung seit
        %s.") — Vorbild `UrlaubsantragService:47–54`;
        `lohnfortzahlungBis` default `beginn.plusDays(41)` (**42 Tage
        inklusive Beginn**, ausdrücklich als Kommentar), überschreibbar, muss
        `>= beginn` sein; Status `LAUFEND`; `notiz` trimmen, auf 500 Zeichen
        prüfen; automatisch Phase `LOHNFORTZAHLUNG` von `beginn` bis
        `lohnfortzahlungBis` anlegen.
  - [ ] `pruefePhasen(meldung)` — private Regelprüfung, nach jeder
        Phasenänderung aufgerufen, jede Verletzung als
        `IllegalStateException` mit Klartext:
        Phasen nach `vonDatum` sortiert, keine Überlappung
        (`vorherige.bisDatum >= naechste.vonDatum` → Fehler);
        keine Lücke (`vorherige.bisDatum.plusDays(1) != naechste.vonDatum` → Fehler);
        höchstens eine Phase ohne `bisDatum`, und zwar die letzte;
        `WIEDEREINGLIEDERUNG` braucht `stundenProTag > 0`
        („Bitte trage ein, wie viele Stunden pro Tag geplant sind.");
        die anderen Typen müssen `stundenProTag == null` haben;
        `stundenProTag` darf an keinem Wochentag der Phase über dem
        Zeitkonto-Tagessoll liegen (`zeitkontoService.getOrCreateZeitkonto` +
        `getSollstundenFuerTag` über die Wochentage, die in der Phase
        vorkommen); alle Phasen liegen innerhalb `[beginn, ende ?: offen]`.
  - [ ] `verknuepfeAbwesenheiten(meldung)` — private, nach jeder Schreib-
        operation: `abwesenheitRepository.findByMitarbeiterIdAndTypAndDatumBetween(
        mitarbeiterId, KRANKHEIT, meldung.getBeginn(), meldung.getEnde() ?: heute)`
        einmal laden, je Tag die passende Phase setzen (`langzeitkrankmeldung`
        + `langzeitkrankmeldungPhase`), Tage außerhalb auf `null` zurücksetzen,
        `saveAll`. Genau **eine** Abfrage, kein Aufruf im Schleifenkörper.
  - [ ] Nach jeder Schreiboperation die betroffenen Monate invalidieren:
        Schleife über `YearMonth` von `beginn` bis `ende ?: heute`,
        `monatsSaldoService.invalidiereMonat(mitarbeiterId, jahr, monat)`.
        Ohne das zeigt die Monatsübersicht weiter das alte Soll — der Cache
        merkt von der neuen Phase sonst nichts.
  - [ ] `beenden(id, ende)`: nur aus `LAUFEND`; `ende >= beginn`; alle Phasen
        mit `bisDatum == null` auf `ende` schließen; Status `BEENDET`.
        `wiederEroeffnen(id)`: nur aus `BEENDET`; `ende = null`; vorher
        `findUeberlappende` prüfen. `abbrechen(id)`: nur aus `LAUFEND`;
        Status `ABGEBROCHEN`, außerdem alle verknüpften Abwesenheiten
        entkoppeln. Jeder unerlaubte Übergang →
        `IllegalStateException("Eine zurückgenommene Krankmeldung lässt sich
        nicht wieder öffnen. Bitte neu anlegen.")` o.ä.
  - [ ] `restTageLohnfortzahlung`: `ChronoUnit.DAYS.between(stichtag,
        lohnfortzahlungBis)` — 0 = „läuft heute aus", negativ = überschritten.
  - [ ] `toDto(meldung, mitStufenplanTagen)`: Labels aus einer privaten
        `static final Map<LangzeitkrankmeldungPhaseTyp, String>` —
        `LOHNFORTZAHLUNG` → „Lohnfortzahlung durch den Betrieb",
        `KRANKENGELD` → „Krankengeld der Krankenkasse",
        `WIEDEREINGLIEDERUNG` → „Wiedereingliederung"; Status-Labels
        „Läuft noch" / „Wieder voll im Einsatz" / „Zurückgenommen".
        `geplanteRueckkehr` = `bisDatum` der letzten
        `WIEDEREINGLIEDERUNG`-Phase + 1 Tag, sonst `ende`, sonst `null`.
        `heuteGeplanteStunden` = `tagesSollService.arbeitsSoll(...)` für
        heute, nur wenn heute eine `WIEDEREINGLIEDERUNG`-Phase läuft.
        `stufenplanTage` nur wenn `mitStufenplanTagen`: über die
        Wiedereingliederungs-Zeiträume iterieren, `geplanteStunden` aus
        `tagesSollService.arbeitsSoll`, `gestempelteStunden` aus **einer**
        `zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween`-Abfrage
        über den Gesamtzeitraum (ohne `BuchungsTyp.PAUSE`, nach Tag gruppiert),
        `ueberPlan = gestempelt > geplant`. Kein Blocker, nur eine Markierung —
        so sieht der Chef es auf der Detailseite (Spec, Abschnitt 7).
  - [ ] `getMobileStand(loginToken, stichtag)`:
        `mitarbeiterRepository.findByLoginToken(token)` → keine Meldung →
        `Collections.emptyMap()` (genau wie
        `ZeiterfassungApiService.getUrlaubsverfallWarnung`, Zeile 1125–1130).
        Sonst `Map` mit `phase`, `phaseLabel`, `heuteGeplanteStunden`,
        `seit`, `bisDatum`. **Keine Notiz, kein Name** im Mobile-Ergebnis (DSGVO).
  - [ ] `pruefeUrlaubsHinweise(mitarbeiterId, von, bis)`: `findUeberlappende`
        → je Treffer ein Satz „In diesem Zeitraum läuft eine Krankmeldung
        (seit %s). Bitte prüfen, ob der Urlaub wirklich passt." Keine
        Exception — nur ein Hinweis (Spec, Abschnitt 7).
  - [ ] Logging: `log.info("Langzeitkrankmeldung {} angelegt für Mitarbeiter {}",
        meldungId, mitarbeiterId)` — **nie** Name oder Notiz.
  - [ ] `LangzeitkrankmeldungServiceTest` mit `@ExtendWith(MockitoExtension.class)`:
        42-Tage-Rechnung (Beginn 01.03. → `lohnfortzahlungBis` 11.04.),
        überschreibbares Datum, Überlappung wird abgelehnt, `ABGEBROCHEN`
        blockiert nicht, jede der vier Phasen-Regeln aus Abschnitt 7 der Spec
        einzeln, jeder verbotene Statusübergang, `verknuepfeAbwesenheiten`
        setzt die FK korrekt, `getMobileStand` liefert `{}` ohne Meldung.
        Dummy-Daten `Max Mustermann`.
  - [ ] `./mvnw -B test -Dtest=LangzeitkrankmeldungServiceTest`

---

## Abschnitt 4 — Task 5, 6, 7 (max. 3 Tasks, disjunkte Dateien)

Basis: `feature/langzeitkrankmeldung` nach Merge von Abschnitt 3.
Frontend betroffen: nein.

### Task 5 — Desktop-API: `LangzeitkrankmeldungController`
- Branch: `lzk/task-5-controller`
- Worktree: `../wt/lzk-task-5`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/controller/LangzeitkrankmeldungController.java` (neu)
  - `src/test/java/org/example/kalkulationsprogramm/controller/LangzeitkrankmeldungControllerTest.java` (neu)
- Vorbild:
  - Pfad-/Antwortschema und Fehlerbehandlung:
    `controller/AbwesenheitController.java` (ganze Datei) — `@RestController`,
    `@RequestMapping("/api/...")`, `@RequiredArgsConstructor`,
    `catch (IllegalArgumentException | IllegalStateException e) ->
    ResponseEntity.badRequest().body(Map.of("error", e.getMessage()))`.
  - DTO-Rückgabe statt Entity: `controller/LieferantReklamationController.java:38–46`.
  - MockMvc-Test: `controller/ZeitverwaltungControllerTest.java:38–60`.
- Interfaces:
  - Produces (alle unter `/api/langzeitkrankmeldungen`, damit greift die
    Standardregel `.anyRequest().authenticated()` der `apiFilterChain`,
    `config/SecurityConfig.java:225` — **keine** SecurityConfig-Änderung nötig,
    das gehört als Kommentar in den Klassen-Javadoc):
    ```
    GET    /api/langzeitkrankmeldungen?status=LAUFEND   -> 200 List<LangzeitkrankmeldungDto>
    GET    /api/langzeitkrankmeldungen/{id}             -> 200 LangzeitkrankmeldungDto (mit stufenplanTage)
    POST   /api/langzeitkrankmeldungen                  -> 201 LangzeitkrankmeldungDto
    PUT    /api/langzeitkrankmeldungen/{id}             -> 200 LangzeitkrankmeldungDto
    PUT    /api/langzeitkrankmeldungen/{id}/beenden     -> 200 LangzeitkrankmeldungDto
    PUT    /api/langzeitkrankmeldungen/{id}/oeffnen     -> 200 LangzeitkrankmeldungDto
    PUT    /api/langzeitkrankmeldungen/{id}/abbrechen   -> 200 LangzeitkrankmeldungDto
    POST   /api/langzeitkrankmeldungen/{id}/phasen      -> 201 LangzeitkrankmeldungDto
    PUT    /api/langzeitkrankmeldungen/{id}/phasen/{phasenId} -> 200 LangzeitkrankmeldungDto
    DELETE /api/langzeitkrankmeldungen/{id}/phasen/{phasenId} -> 204
    ```
    Fehlerformat einheitlich `{"error": "<Klartext>"}` mit Status 400,
    unbekannte ID → 404.
  - Consumes: Task 4.
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] Controller anlegen, **keine Logik** — jede Methode ruft genau eine
        Service-Methode und mappt über `service.toDto(...)`.
        `status`-Parameter default `LAUFEND`.
  - [ ] Request-Bodies über die DTOs aus Task 4 (`@RequestBody
        LangzeitkrankmeldungAnlegenRequest`), nicht über `Map<String,Object>` —
        `AbwesenheitController` nutzt zwar `Map`, `BACKEND_ARCH.md` verlangt
        aber DTOs; die neueren Controller (`LieferantReklamationController`)
        machen es richtig.
  - [ ] MockMvc-Test `@WebMvcTest(LangzeitkrankmeldungController.class)` +
        `@AutoConfigureMockMvc(addFilters = false)` + `@MockBean
        LangzeitkrankmeldungService`. Je Endpoint Happy-Path **und**
        Fehlerfall. Zusätzlich die Sicherheits-Pflichtcheckliste aus
        `TESTING_SECURITY.md`: `id = -1`, `id = Long.MAX_VALUE`, `id = 0`,
        Notiz mit `<script>alert(1)</script>` (wird unverändert gespeichert,
        aber als JSON escaped zurückgegeben — sichern), Notiz > 10.000
        Zeichen → 400, `'; DROP TABLE x; --` als Notiz → 400 wegen Länge/OK
        (Named Params, kein Concat). Dummy-Daten `Max Mustermann`.
  - [ ] `./mvnw -B test -Dtest=LangzeitkrankmeldungControllerTest`

---

### Task 6 — Mobile-API: `LangzeitkrankmeldungMobileController`
- Branch: `lzk/task-6-mobile-controller`
- Worktree: `../wt/lzk-task-6`

Eigener Controller statt einer Erweiterung von `ZeiterfassungApiService` —
sonst würde dieser Task dieselbe Datei anfassen wie Task 10.

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/controller/LangzeitkrankmeldungMobileController.java` (neu)
  - `src/test/java/org/example/kalkulationsprogramm/controller/LangzeitkrankmeldungMobileControllerTest.java` (neu)
- Vorbild:
  - Token-Endpoint, der bei „nichts zu zeigen" ein leeres Objekt liefert:
    `controller/ZeiterfassungApiController.java:286–289`
    (`GET /urlaubsverfall/{token}`) zusammen mit
    `ZeiterfassungApiService.getUrlaubsverfallWarnung` (Zeile 1125–1130).
- Interfaces:
  - Produces:
    ```
    GET /api/zeiterfassung/langzeitkrankmeldung/{token}
      -> 200 {}                                   // nichts zu zeigen
      -> 200 { "phase": "WIEDEREINGLIEDERUNG",
               "phaseLabel": "Wiedereingliederung",
               "heuteGeplanteStunden": 4.00,
               "seit": "2026-03-01",
               "bisDatum": "2026-04-30" }
    ```
    Liegt unter `/api/zeiterfassung/**` und ist damit schon in der
    Mobile-Whitelist (`config/SecurityConfig.java:115`) — **keine**
    SecurityConfig-Änderung. Als Kommentar in den Klassen-Javadoc.
  - Consumes: Task 4 (`getMobileStand`).
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] Controller mit `@RequestMapping("/api/zeiterfassung/langzeitkrankmeldung")`,
        eine Methode `@GetMapping("/{token}")`, delegiert an
        `service.getMobileStand(token, LocalDate.now())`.
  - [ ] MockMvc-Test: unbekanntes Token → `{}`; laufende Wiedereingliederung →
        die fünf Felder; Token mit Path-Traversal (`../../etc/passwd`) und
        SQL-Injection-Muster → `{}`, keine Exception (Named Params).
        **Sicherstellen, dass die Antwort keinen Namen und keine Notiz
        enthält** — eigene Zusicherung, DSGVO.
  - [ ] `./mvnw -B test -Dtest=LangzeitkrankmeldungMobileControllerTest`

---

### Task 7 — Umstellung `ZeitkontoService`
- Branch: `lzk/task-7-zeitkonto`
- Worktree: `../wt/lzk-task-7`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/service/ZeitkontoService.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/ZeitkontoServiceTest.java`
- Vorbild: die zu ersetzende Schleife steht in derselben Datei, Zeile 113–130.
- Interfaces:
  - Produces: `berechneSollstundenFuerZeitraum(Zeitkonto, LocalDate, LocalDate)`
    bleibt **signaturgleich** (Aufrufer:
    `ZeiterfassungApiService.java:1092`, `ZeitkontoService.java:88` und `:105`).
  - Consumes: Task 2, Task 3.
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] `private final FeiertagService feiertagService;` (Zeile 25) durch
        `private final TagesSollService tagesSollService;` ersetzen —
        `feiertagService` wird danach in dieser Klasse nicht mehr gebraucht
        (einzige Nutzung ist Zeile 122). **Achtung Zyklus:**
        `TagesSollService` darf **nicht** `ZeitkontoService` injizieren; es
        nimmt `Zeitkonto` als Parameter. Das ist in Task 3 so gebaut.
  - [ ] `berechneSollstundenFuerZeitraum` wird zum Einzeiler:
        ```java
        return tagesSollService.periodenSollSumme(
                konto.getMitarbeiter().getId(), konto, von, bis);
        ```
        Javadoc anpassen: Feiertage zählen weiter als bezahlte Arbeitstage,
        halbe Feiertage 50 %, zusätzlich richtet sich das Soll jetzt nach einer
        laufenden Wiedereingliederung. Verweis auf `TagesSollService`.
  - [ ] `konto.getMitarbeiter()` kann in Alt-Testdaten `null` sein → vorher
        prüfen und dann mit `null` als `mitarbeiterId` weiterreichen
        (`TagesSollService` behandelt das als „keine Meldung").
  - [ ] `ZeitkontoServiceTest`: `@Mock FeiertagService` durch
        `@Mock TagesSollService` ersetzen und die vorhandenen Zusicherungen so
        umstellen, dass sie das Delegieren prüfen. **Die Zahlen-Zusicherungen
        selbst wandern nicht** — die stehen jetzt in
        `TagesSollCharakterisierungZeitkontoTest` (Task 2) und in
        `TagesSollServiceTest` (Task 3).
  - [ ] `TagesSollCharakterisierungZeitkontoTest` **unverändert** grün halten.
        Wird sie rot, ist die Umstellung falsch.
  - [ ] `./mvnw -B test -Dtest=ZeitkontoServiceTest,TagesSollCharakterisierungZeitkontoTest`

---

## Abschnitt 5 — Task 8, 9, 10 (max. 3 Tasks, disjunkte Dateien)

Basis: `feature/langzeitkrankmeldung` nach Merge von Abschnitt 4.
Frontend betroffen: nein.

### Task 8 — Umstellung `MonatsSaldoService`
- Branch: `lzk/task-8-monatssaldo`
- Worktree: `../wt/lzk-task-8`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/service/MonatsSaldoService.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/MonatsSaldoServiceTest.java`
- Vorbild: die zu ersetzende Methode `berechneFeiertagsStunden` steht in
  derselben Datei, Zeile 253–275.
- Interfaces:
  - Produces: keine öffentliche Signaturänderung.
  - Consumes: Task 2, Task 3.
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] `private final FeiertagService feiertagService;` (Zeile 49) durch
        `private final TagesSollService tagesSollService;` ersetzen —
        `feiertagService` wird sonst nirgends in der Klasse genutzt (verifiziert).
  - [ ] `berechneFeiertagsStunden(Zeitkonto, LocalDate, LocalDate)`
        **ersatzlos löschen**; der Aufruf in `berechneMonatsSaldo` (Zeile 135)
        wird zu
        `BigDecimal feiertagsStunden = tagesSollService.feiertagsGutschriftSumme(
             mitarbeiterId, zeitkonto, ersterTag, letzterTag);`
  - [ ] `sollStunden` (Zeile 126) bleibt unverändert bei
        `zeitkontoService.berechneSollstundenFuerMonat(...)` — das ist nach
        Task 7 automatisch der neue `periodenSoll`. Nicht doppelt umbauen.
  - [ ] `MonatsSaldoServiceTest` (917 Zeilen): `@Mock FeiertagService` durch
        `@Mock TagesSollService` ersetzen, alle `when(feiertagService...)`-Stubs
        auf `when(tagesSollService.feiertagsGutschriftSumme(...))` umschreiben.
        Die erwarteten Saldo-Zahlen bleiben unverändert — ändert sich eine,
        ist es eine Regression.
  - [ ] `TagesSollCharakterisierungMonatsSaldoTest` unverändert grün halten.
  - [ ] `./mvnw -B test -Dtest=MonatsSaldoServiceTest,TagesSollCharakterisierungMonatsSaldoTest`

---

### Task 9 — Umstellung `AbwesenheitService`
- Branch: `lzk/task-9-abwesenheit`
- Worktree: `../wt/lzk-task-9`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/service/AbwesenheitService.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/AbwesenheitServiceTest.java`
- Vorbild: die Stelle steht in derselben Datei, Zeile 57–66.
- Interfaces:
  - Produces: `bucheAbwesenheit(Long, LocalDate, AbwesenheitsTyp, boolean)`
    bleibt signaturgleich.
  - Consumes: Task 2, Task 3.
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] `private final TagesSollService tagesSollService;` zu den Feldern
        (Zeile 22–27) ergänzen. `feiertagService` **bleibt** — es wird in
        Zeile 52–55 weiter für die Feiertagssperre und die Bezeichnung
        gebraucht.
  - [ ] Zeile 60
        `BigDecimal sollStunden = zeitkonto.getSollstundenFuerTag(wochentag);`
        ersetzen durch
        `BigDecimal sollStunden = tagesSollService.arbeitsSoll(mitarbeiterId, zeitkonto, datum);`
        Die lokale Variable `wochentag` (Zeile 59) entfällt damit.
  - [ ] Die Fehlermeldung „Kein Arbeitstag" (Zeile 62–66) um den Fall
        erweitern, dass eine Wiedereingliederung mit 0 Stunden greift — Text
        bleibt sonst gleich (Handwerker-Sprache, kein Fachjargon).
  - [ ] `AbwesenheitServiceTest`: `@Mock TagesSollService tagesSollService;`
        ergänzen (Mockito injiziert über `@InjectMocks` automatisch), in
        `stubGrunddaten()` (Zeile 70–77)
        `when(tagesSollService.arbeitsSoll(anyLong(), any(), any()))
             .thenReturn(new BigDecimal("8.00"));` ergänzen.
        Ein neuer Test: läuft eine Wiedereingliederung mit 2 h, bucht eine
        Krankmeldung `2.00` statt `8.00`.
  - [ ] `TagesSollCharakterisierungAbwesenheitTest` unverändert grün halten.
  - [ ] `./mvnw -B test -Dtest=AbwesenheitServiceTest,TagesSollCharakterisierungAbwesenheitTest`

---

### Task 10 — Umstellung `ZeiterfassungApiService`
- Branch: `lzk/task-10-zeiterfassung-api`
- Worktree: `../wt/lzk-task-10`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/service/ZeiterfassungApiService.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/ZeiterfassungApiServiceConcurrencyTest.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/ZeiterfassungApiServiceVerspaeteterStopTest.java`
- Vorbild: die zu ersetzende Methode steht in derselben Datei, Zeile 1127–1158.
- Interfaces:
  - Produces: keine öffentliche Signaturänderung; der **Konstruktor** bekommt
    einen 13. Parameter (siehe Steps).
  - Consumes: Task 2, Task 3.
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] `private final TagesSollService tagesSollService;` in den
        `@RequiredArgsConstructor`-Feldblock (Zeile 63–74) aufnehmen, **hinter**
        `auditService`, damit der Konstruktor-Parameter ans Ende wandert.
        `feiertagService` bleibt (wird an anderen Stellen genutzt).
  - [ ] `berechneFeiertagsStunden(Zeitkonto, LocalDate, LocalDate)`
        (Zeile 1127–1158, inkl. des doppelt vorhandenen Javadoc-Blocks
        darüber) **ersatzlos löschen**. Der einzige Aufruf steht in
        `berechneAnteiligenMonatIst`, Zeile 1185:
        ```java
        BigDecimal feiertagsStunden = tagesSollService.feiertagsGutschriftSumme(
                mitarbeiterId, zeitkonto, von, bis);
        ```
  - [ ] Zeile 1092 (`zeitkontoService.berechneSollstundenFuerZeitraum(...)`)
        **nicht anfassen** — die bekommt das neue Verhalten schon über Task 7.
  - [ ] Beide Testklassen bauen den Service von Hand
        (`ZeiterfassungApiServiceConcurrencyTest:76–83`,
        `ZeiterfassungApiServiceVerspaeteterStopTest:75ff`). In beiden
        `@Mock private TagesSollService tagesSollService;` ergänzen und den
        Konstruktoraufruf um das Argument erweitern. Ohne diese Anpassung
        compiliert `src/test` nicht mehr.
  - [ ] `TagesSollCharakterisierungZeiterfassungApiTest` unverändert grün halten.
  - [ ] `./mvnw -B test -Dtest='ZeiterfassungApiService*Test+TagesSollCharakterisierungZeiterfassungApiTest'`

---

## Abschnitt 6 — Task 11, 12, 13 (max. 3 Tasks, disjunkte Dateien)

Basis: `feature/langzeitkrankmeldung` nach Merge von Abschnitt 5.
Frontend betroffen: nein.

### Task 11 — Umstellung `ZeitverwaltungController` (mit benannter Verhaltensänderung)
- Branch: `lzk/task-11-zeitverwaltung`
- Worktree: `../wt/lzk-task-11`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/controller/ZeitverwaltungController.java`
  - `src/test/java/org/example/kalkulationsprogramm/controller/ZeitverwaltungControllerTest.java`
  - `src/test/java/org/example/kalkulationsprogramm/controller/TagesSollCharakterisierungKalenderTest.java` (**einzige erlaubte Anpassung an einer Task-2-Datei**)
- Vorbild: die beiden Stellen stehen im `getKalender`-Block, Zeile 490–515.
- Interfaces:
  - Produces: `GET /api/zeitverwaltung/kalender` — Antwortstruktur bleibt
    gleich, `tage[].istStunden` ändert sich an halben Feiertagen von voll auf
    halb (siehe „Bewusste Verhaltensänderungen", Punkt 1).
  - Consumes: Task 2, Task 3.
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] `private final TagesSollService tagesSollService;` in den Feldblock
        (Zeile 53–65) ergänzen. `feiertagService` bleibt (Zeile 484 lädt die
        Feiertagsliste für die Anzeige).
  - [ ] Zeile 501–502:
        ```java
        tagData.put("sollStunden",
                tagesSollService.arbeitsSoll(mitarbeiterId, zeitkonto, currentTag));
        ```
        Der Sonderzweig `feiertagDaten.contains(currentTag) ? ZERO : ...`
        entfällt — `arbeitsSoll` liefert an Feiertagen von sich aus 0.
  - [ ] Zeile 509–515: den `if (feiertagDaten.contains(currentTag))`-Block
        ersetzen durch
        ```java
        istStunden = istStunden.add(
                tagesSollService.feiertagsGutschrift(mitarbeiterId, zeitkonto, currentTag));
        ```
        Kommentar darüber, **warum** sich das ändert: bisher zählte auch ein
        halber Feiertag mit vollen Stunden als Ist, während `sollStundenMonat`
        (Zeile 543, aus `berechneSollstundenFuerMonat`) ihn halbiert — das
        ergab pro Heiligabend/Silvester +4 h Phantom-Überstunden und einen
        Widerspruch zur Monatsübersicht (`MonatsSaldoService`).
  - [ ] Performance: `arbeitsSoll`/`feiertagsGutschrift` werden hier pro Tag
        gerufen (bis 31×2 Aufrufe). Vor der Tagesschleife (Zeile 490) einmal
        `tagesSollService.periodenSollSumme`/`feiertagsGutschriftSumme` ist
        hier nicht möglich, weil je Tag ein Einzelwert gebraucht wird —
        stattdessen die Werte über zwei Aufrufe von `TagesSollService` je Tag
        akzeptieren, aber im Kontext-Log vermerken. Alternativ, wenn der
        Coding-Agent es sauber hinbekommt: `TagesSollService` um
        `Map<LocalDate, BigDecimal> arbeitsSollJeTag(...)` erweitern — **nur**
        wenn Task 3 abgeschlossen ist und der Zusatz dort nachgezogen wird.
        Standardweg ist der einfache: pro Tag rufen.
  - [ ] `TagesSollCharakterisierungKalenderTest`: **genau eine** Zusicherung
        ändern — `tage[23].istStunden` von `8` auf `4.00`, mit einem
        Kommentar, der auf diesen Task und den Plan-Abschnitt „Bewusste
        Verhaltensänderungen" verweist. Alle anderen Zusicherungen bleiben.
  - [ ] Zusätzlicher Test in `ZeitverwaltungControllerTest`: Bei laufender
        Wiedereingliederung mit 2 h liefert `tage[].sollStunden` an einem
        normalen Arbeitstag `2.00`.
  - [ ] `./mvnw -B test -Dtest=ZeitverwaltungControllerTest,TagesSollCharakterisierungKalenderTest`

---

### Task 12 — Umstellung `UrlaubsantragService` + Hinweis-Endpoint
- Branch: `lzk/task-12-urlaubsantrag`
- Worktree: `../wt/lzk-task-12`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/service/UrlaubsantragService.java`
  - `src/main/java/org/example/kalkulationsprogramm/controller/UrlaubsantragController.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/UrlaubsantragServiceTest.java` (neu — bisher gibt es keinen)
- Vorbild:
  - Die Stelle steht in derselben Datei, `approveAntrag`, Zeile 249–280.
  - Endpoint-Stil: `controller/UrlaubsantragController.java:95–109`
    (`GET /resturlaub` mit `@RequestParam`).
- Interfaces:
  - Produces:
    ```
    GET /api/urlaub/antraege/hinweise?mitarbeiterId=1&von=2026-03-01&bis=2026-03-05
      -> 200 { "warnungen": ["In diesem Zeitraum läuft eine Krankmeldung (seit 01.03.2026). ..."] }
    ```
    Bewusst ein **neuer** Endpoint statt eines zusätzlichen Feldes an
    `POST /api/urlaub/antraege` — die Mobile-App (`react-zeiterfassung`)
    postet dort hin, eine Strukturänderung wäre ein Breaking Change
    (`kriterien.md`, Abschnitt API-Design).
  - Consumes: Task 2, Task 3, Task 4 (`pruefeUrlaubsHinweise`).
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] `private final TagesSollService tagesSollService;` und
        `private final LangzeitkrankmeldungService langzeitkrankmeldungService;`
        zum Feldblock (Zeile 18–24) ergänzen.
  - [ ] `approveAntrag`, Zeile 111–112:
        ```java
        BigDecimal sollStunden = tagesSollService.arbeitsSoll(
                antrag.getMitarbeiter().getId(),
                zeitkontoService.getOrCreateZeitkonto(antrag.getMitarbeiter().getId()),
                date);
        ```
        `getOrCreateZeitkonto` **vor** die Schleife ziehen (Zeile 250) — heute
        wird es je Tag gerufen, das ist ein N+1 in einer Schleife
        (`kriterien.md`, Performance). Javadoc-Satz ergänzen: Urlaubsstunden
        folgen während einer Wiedereingliederung dem Stufenplan, der
        Urlaubs**tag** zählt weiterhin voll gegen das Kontingent
        (`getResturlaub` zählt Tage, nicht Stunden).
  - [ ] Neue Service-Methode
        `public List<String> pruefeHinweise(Long mitarbeiterId, LocalDate von, LocalDate bis)`,
        die an `langzeitkrankmeldungService.pruefeUrlaubsHinweise` delegiert.
  - [ ] Controller: `@GetMapping("/antraege/hinweise")`, gibt
        `Map.of("warnungen", service.pruefeHinweise(...))` zurück.
  - [ ] `UrlaubsantragServiceTest` neu anlegen (Mockito, Vorbild
        `AbwesenheitServiceTest`): `approveAntrag` erzeugt für Mo–Fr fünf
        Abwesenheiten à 8,00; bei laufender Wiedereingliederung à 2,00;
        Feiertage und Wochenenden werden übersprungen; `getOrCreateZeitkonto`
        wird genau **einmal** gerufen (`verify(..., times(1))`) — das sichert
        die N+1-Behebung ab.
  - [ ] `TagesSollCharakterisierungUrlaubsantragTest` unverändert grün halten.
  - [ ] `./mvnw -B test -Dtest=UrlaubsantragServiceTest,TagesSollCharakterisierungUrlaubsantragTest`

---

### Task 13 — Verrechnungslohn: Krankheitsphasen trennen
- Branch: `lzk/task-13-verrechnungslohn`
- Worktree: `../wt/lzk-task-13`

- Files:
  - `src/main/java/org/example/kalkulationsprogramm/service/VerrechnungslohnService.java`
  - `src/main/java/org/example/kalkulationsprogramm/dto/Verrechnungslohn/VerrechnungslohnErgebnisDto.java`
  - `src/test/java/org/example/kalkulationsprogramm/service/VerrechnungslohnServiceTest.java`
- Vorbild:
  - Tage aus dem Jahressoll ausklammern: die vorhandene Feiertagsschleife
    `jahresSollstundenAusZeitkonto`, Zeile 452–465
    (`if (feiertage.contains(d)) continue;`) — dieselbe Mechanik, zweite Menge.
  - Default-Markierung im DTO: `MitarbeiterStundenZeile.sollIstDefault`
    (`VerrechnungslohnErgebnisDto.java:79`) und ihre Anzeige.
- Interfaces:
  - Produces: `VerrechnungslohnErgebnisDto.MitarbeiterStundenZeile` +
    `private int ausgeklammerteTage;` und
    `private BigDecimal ausgeklammerteStunden = BigDecimal.ZERO;`
    `VerrechnungslohnErgebnisDto.MitarbeiterLohnZeile` +
    `private int ausgeklammerteTage;` und
    `private BigDecimal anwesenheitsFaktor = BigDecimal.ONE;`
    Konstruktor von `VerrechnungslohnService` bekommt einen 16. Parameter
    `LangzeitkrankmeldungPhaseRepository phaseRepository`.
  - Consumes: Task 1.
- Steps:
  - [ ] `Read` `BACKEND_ARCH.md` + `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] **Die vier Aufrufe von `getSollstundenFuerTag` (Zeile 462, 471, 506,
        535) bleiben unverändert.** `VerrechnungslohnService` bekommt
        **keinen** `TagesSollService` — die Ausnahme aus der Spec gilt, hier
        geht es um das Jahres-Normalsoll. Das gehört als Kommentar an den
        Feldblock.
  - [ ] Neue private Methode
        `private Set<LocalDate> ausgeklammerteTage(Long mitarbeiterId, LocalDate von, LocalDate bis)`:
        `phaseRepository.findImZeitraum(mitarbeiterId, von, bis)`, davon nur
        Phasen mit `typ IN (KRANKENGELD, WIEDEREINGLIEDERUNG)`, deren Tage
        (auf `[von, bis]` beschnitten) als `LocalDate`-Menge. **Eine** Abfrage
        je Mitarbeiter, außerhalb jeder Tagesschleife.
  - [ ] `berechneStundenZeile` (ab Zeile 369): die Menge einmal ermitteln und
        an `jahresSollstundenAusZeitkonto` und `werktagsSollOhneZeitkonto` als
        zusätzlichen Parameter durchreichen; dort neben dem Feiertagsfilter
        `if (ausgeklammert.contains(d)) continue;`.
        `zeile.setAusgeklammerteTage(menge.size())` und
        `setAusgeklammerteStunden(<Summe der übersprungenen Sollstunden>)`.
  - [ ] Krankheitsstunden (Zeile 407–409): statt
        `sumStundenByMitarbeiterIdAndTypAndDatumBetween` die neue Methode
        `abwesenheitRepository.sumStundenOhnePhasenTypen(ma.getId(), KRANKHEIT,
        jahresStart, jahresEnde, List.of(KRANKENGELD, WIEDEREINGLIEDERUNG))`.
        Begründung als Kommentar: die Tage sind schon aus dem Soll raus, sie
        dürfen nicht zusätzlich abgezogen werden. Lohnfortzahlungs-Wochen
        bleiben im Abzug (Spec, Abschnitt 4).
  - [ ] Den Default-Fallback (Zeile 414–417,
        `if (krank == 0) krank = KRANKHEITSTAGE_DEFAULT * stundenProTag`) nur
        noch anwenden, wenn `ausgeklammerteTage == 0`. Sonst würde ein
        vollständig ausgeklammerter Langzeitfall den 8-Tage-Standard
        zusätzlich abziehen.
  - [ ] `berechneLohnZeile` (ab Zeile 208): am Ende
        `anwesenheitsFaktor = (jahresTage − ausgeklammerteTage) / jahresTage`
        (4 Nachkommastellen, `HALF_UP`, `jahresTage = Year.of(jahr).length()`),
        `gesamtkosten = gesamtkosten × anwesenheitsFaktor`, beide Werte ins
        DTO. Hier gilt der Faktor aus dem Design — Lohnkosten sind eine
        Jahressumme ohne Tagesauflösung, exakt geht dort nicht. Beim Jahressoll
        wird dagegen exakt tageweise übersprungen (genauer und billiger).
        Reihenfolge in `berechne` (Zeile 130–137) beachten: die ausgeklammerten
        Tage werden einmal je Mitarbeiter ermittelt und an **beide**
        Zeilen-Methoden gereicht, nicht zweimal abgefragt.
  - [ ] `VerrechnungslohnServiceTest` (835 Zeilen): den Konstruktoraufruf
        (Zeile 107–123) um `phaseRepository` erweitern, Default-Stub
        `when(phaseRepository.findImZeitraum(any(), any(), any()))
             .thenReturn(Collections.emptyList())` in den `@BeforeEach`-Block —
        damit bleiben **alle vorhandenen Zahlen unverändert**. Rot heißt hier
        Regression.
        Neuer Test „Langzeitfall": Mitarbeiter mit 8 h/Tag, Krankengeld
        01.03.–30.06., erwartetes Jahressoll um genau die Werktagsstunden
        dieses Zeitraums niedriger, `ausgeklammerteTage == 122`,
        Lohnkosten × Faktor, Krankheitsstunden enthalten nur die
        Lohnfortzahlungs-Tage.
  - [ ] `./mvnw -B test -Dtest=VerrechnungslohnServiceTest`

---

## Abschnitt 7 — Task 14 (max. 3 Tasks, disjunkte Dateien)

Basis: `feature/langzeitkrankmeldung` nach Merge von Abschnitt 6.
Frontend betroffen: ja (`react-pc-frontend/`) — reine Bausteine ohne eigene
Seite/Route, noch nichts zum Anklicken, deshalb noch kein Playwright. Design-
Review hier nur visuell über die Vitest-Tests; die volle Playwright-Prüfung
folgt in Abschnitt 8, sobald die Seite existiert.

### Task 14 — Desktop-Bausteine: Phasen-Zeitleiste und Stufenplan-Tabelle
- Branch: `lzk/task-14-bausteine`
- Worktree: `../wt/lzk-task-14`

- Files:
  - `react-pc-frontend/src/components/langzeitkrankmeldung/phasen.ts` (neu)
  - `react-pc-frontend/src/components/langzeitkrankmeldung/PhasenZeitleiste.tsx` (neu)
  - `react-pc-frontend/src/components/langzeitkrankmeldung/StufenplanTabelle.tsx` (neu)
  - `react-pc-frontend/src/components/langzeitkrankmeldung/PhasenZeitleiste.test.tsx` (neu)
  - `react-pc-frontend/src/components/langzeitkrankmeldung/StufenplanTabelle.test.tsx` (neu)
- Vorbild:
  - Farbige Typ-Badges mit Icon: `src/pages/Urlaubsantraege.tsx:172–199`
    (`inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium
    bg-…-100 text-…-800` + `<Icon className="w-3 h-3" />`).
  - Status-Badge-Funktion: `src/pages/Urlaubsantraege.tsx:118–125`.
  - Komponenten-Test mit Testing Library: irgendeine der Dateien unter
    `src/components/document-editor/*.test.tsx`.
- Interfaces:
  - Produces:
    ```ts
    // phasen.ts
    export type PhasenTyp = 'LOHNFORTZAHLUNG' | 'KRANKENGELD' | 'WIEDEREINGLIEDERUNG';
    export interface Phase {
        id: number; typ: PhasenTyp; label: string;
        vonDatum: string; bisDatum: string | null; stundenProTag: number | null;
    }
    export const PHASEN_LABEL: Record<PhasenTyp, string>;   // "Lohnfortzahlung durch den Betrieb" usw.
    export const PHASEN_BADGE: Record<PhasenTyp, string>;   // Tailwind-Klassen
    export function formatDatum(iso: string): string;       // "01.04.2026"

    // PhasenZeitleiste.tsx
    export function PhasenZeitleiste(props: { phasen: Phase[]; heute?: string }): JSX.Element;

    // StufenplanTabelle.tsx
    export function StufenplanTabelle(props: {
        phasen: Phase[];
        onHinzufuegen: (p: { vonDatum: string; bisDatum: string | null; stundenProTag: number }) => Promise<void>;
        onLoeschen: (phasenId: number) => Promise<void>;
        maxStundenProTag: number;
        disabled?: boolean;
    }): JSX.Element;
    ```
  - Consumes: nichts (nur Props). Läuft parallel zum Backend.
- Steps:
  - [ ] `Read` `FRONTEND_UI.md`, `Skill` `handwerkerprogramm-design`,
        `Read` `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] `phasen.ts`: Labels wörtlich aus der Spec
        („Lohnfortzahlung durch den Betrieb", „Krankengeld der Krankenkasse",
        „Wiedereingliederung"). Badge-Klassen:
        `LOHNFORTZAHLUNG` → `bg-amber-100 text-amber-800`,
        `KRANKENGELD` → `bg-blue-100 text-blue-800`,
        `WIEDEREINGLIEDERUNG` → `bg-teal-100 text-teal-800` — dieselbe Familie,
        die `Urlaubsantraege.tsx` schon für Typ-Badges nutzt. Rose bleibt der
        Primäraktion vorbehalten.
  - [ ] `PhasenZeitleiste`: senkrechte Liste, je Phase ein Punkt
        (`w-2 h-2 rounded-full`) + Badge + Zeitraum („ab 01.04.2026" wenn
        `bisDatum === null`, sonst „01.04.2026 – 30.04.2026") + bei
        Wiedereingliederung „2 Std. pro Tag". Die heute laufende Phase wird
        mit `ring-2 ring-rose-400` hervorgehoben. Container `flex flex-col
        gap-3`, **nicht** `space-y-*` (siehe `kriterien.md`, Falle 4).
        Jedes Text-Element bekommt `min-w-0` **auf seiner eigenen Ebene**,
        weil die Zeile ein Flex-Item ist (Fallen 1 und 2 in `kriterien.md`).
  - [ ] `StufenplanTabelle`: schlichte Tabelle mit Spalten „ab", „bis",
        „Stunden pro Tag", Aktion. Darunter eine Zeile zum Hinzufügen mit
        `<DatePicker>` aus `src/components/ui/datepicker.tsx` (Pflicht-Komponente)
        und einem Zahlenfeld. Primärbutton „Zeile hinzufügen"
        (`bg-rose-600 text-white border border-rose-600 hover:bg-rose-700`,
        `size="sm"`, Icon `Plus w-4 h-4` links). `disabled`-Buttons
        bekommen ein `title` mit dem Grund (FRONTEND_UI: „Deaktivierte Buttons
        erklären warum"). Löschen ist destruktiv → `useConfirm` (der Aufrufer
        liefert `onLoeschen`, die Komponente fragt selbst über
        `useConfirm` nach). Validierung vor `onHinzufuegen`: Stunden > 0 und
        `<= maxStundenProTag`, sonst Inline-Fehlertext **und** `toast.error`.
  - [ ] Leerzustand: „Noch kein Stufenplan hinterlegt." — optisch klar
        unterscheidbar vom Ladezustand (FRONTEND_UI, Gulf of Evaluation).
  - [ ] Tests: Labels und Badge-Klassen je Typ; laufende Phase hervorgehoben;
        Stundenwert über `maxStundenProTag` löst keinen `onHinzufuegen`-Aufruf
        aus, sondern eine Fehlermeldung; Löschen fragt nach.
        Dummy-Daten, keine echten Namen.
  - [ ] `npm run lint`, `npx vitest run src/components/langzeitkrankmeldung`,
        `npm run build` — alle drei grün.

---

## Abschnitt 8 — Task 15, 16, 17 (max. 3 Tasks, disjunkte Dateien)

Basis: `feature/langzeitkrankmeldung` nach Merge von Abschnitt 7.
Frontend betroffen: ja (`react-pc-frontend/`) — alle drei Tasks bringen eine
eigene Playwright-Spec auf eigenem Port mit; Design-Reviewer läuft hier mit
voller E2E-Prüfung.

### Task 15 — Desktop-Seite „Lange Krankheit" + Route + Menü
- Branch: `lzk/task-15-seite`
- Worktree: `../wt/lzk-task-15`

- Files:
  - `react-pc-frontend/src/pages/Langzeitkrankmeldungen.tsx` (neu)
  - `react-pc-frontend/src/pages/Langzeitkrankmeldungen.test.tsx` (neu)
  - `react-pc-frontend/src/App.tsx` (geändert)
  - `react-pc-frontend/src/components/layout/RibbonNav.tsx` (geändert)
  - `react-pc-frontend/src/components/layout/MobileBottomNav.tsx` (geändert)
  - `react-pc-frontend/e2e/langzeitkrankmeldungen.spec.ts` (neu)
- Vorbild:
  - Ganze Seitenstruktur: `src/pages/Urlaubsantraege.tsx` (253 Zeilen) —
    `PageLayout`, Page-Header-Block (Zeile 128–152), `Select` aus
    `ui/select-custom`, Karten-Liste, Leerzustand (Zeile 155–159),
    `useConfirm` für Aktionen (Zeile 92, 103).
  - Route eintragen: `src/App.tsx:36` (Import `Urlaubsantraege`) und
    `src/App.tsx:110` (`<Route path="/urlaubsantraege" element={<ErrorBoundary>…}`).
  - Menüeintrag: `RibbonNav.tsx:124–129`, `MobileBottomNav.tsx:37–42`.
  - E2E mit gestubbten `/api`-Routen: `e2e/uebersichten-layout.spec.ts`
    (Kopf bis Zeile 60) und die Helfer in `e2e/hilfen/api.ts`, `e2e/hilfen/test.ts`.
- Interfaces:
  - Produces: Route `/langzeitkrankmeldungen`, Menüeinträge (siehe E5).
  - Consumes: Task 5 (Desktop-API), Task 14 (Bausteine).
- Steps:
  - [ ] `Read` `FRONTEND_UI.md`, `Skill` `handwerkerprogramm-design`,
        `Read` `TESTING_SECURITY.md` + `kriterien.md`. Für Bausteine, die es
        noch nicht gibt, den `shadcn`-MCP nutzen statt von Hand nachzubauen.
  - [ ] `Langzeitkrankmeldungen.tsx`: Aufbau 1:1 nach `Urlaubsantraege.tsx`.
        Header: Kategorie „ABWESENHEITEN", H1 „LANGE KRANKHEIT",
        Beschreibung „Lohnfortzahlung, Krankengeld und Wiedereingliederung im
        Blick". Rechts ein `Select` („Läuft noch" / „Abgeschlossen" /
        „Zurückgenommen", Default „Läuft noch") **und** der Primärbutton
        „Krankmeldung anlegen" (`bg-rose-600`, Icon `Plus`). Genau eine
        Primäraktion pro Screen.
  - [ ] Daten: `GET /api/langzeitkrankmeldungen?status=LAUFEND` beim Laden und
        bei Filterwechsel. Drei klar unterscheidbare Zustände: Ladeskelett,
        Leerzustand („Aktuell ist niemand langzeitkrank gemeldet."),
        Fehlerzustand. Jeder Fehler → `toast.error` über
        `useToast` aus `src/components/ui/toast` (Toast-Pflicht aus
        `FRONTEND_UI.md`), **kein** stiller `console.error` wie in
        `Urlaubsantraege.tsx:107` — das ist dort Altbestand, nicht Vorbild.
  - [ ] Karte je Meldung: Avatar-Kreis + `Stethoscope`, „Mustermann, Max",
        „krank seit 01.03.2026", Phasen-Badge (aus `phasen.ts`),
        „geplante Rückkehr: 01.05.2026" bzw. „noch offen".
        Rechts der 42-Tage-Hinweis:
        `restTageLohnfortzahlung > 0` → „Noch 12 Tage Lohnfortzahlung";
        `<= 0` → „Lohnfortzahlung endete am 14.03." mit Sekundärbutton
        „Auf Krankengeld umstellen" (`border-rose-300 text-rose-700
        hover:bg-rose-50`), der `POST /{id}/phasen` mit
        `typ: 'KRANKENGELD'` schickt.
  - [ ] Aufklappbares Detail in derselben Karte (kein eigener Screen — dann
        bleibt der Zeiterfassungs-Kalender und das 1417-Zeilen-Modal
        unangetastet): lädt `GET /api/langzeitkrankmeldungen/{id}` und zeigt
        `<PhasenZeitleiste>` + `<StufenplanTabelle>` (Task 14) plus die
        Stufenplan-Tage mit `ueberPlan`-Markierung („4,5 h gestempelt,
        4 h geplant") als Information, nicht als Fehler.
  - [ ] Anlegen-Dialog: Mitarbeiter-`Select`, Beginn-`DatePicker`,
        `lohnfortzahlungBis`-`DatePicker` (vorbelegt mit Beginn + 41 Tage,
        überschreibbar, Hinweistext „42 Tage ab Beginn — bei einer
        Fortsetzungserkrankung früher setzen"), Notiz-Textarea mit dem Label
        **„Interne Notiz — bitte keine Diagnosen eintragen"** (Pflicht aus der
        Spec, DSGVO). Beim Speichern Button `disabled` + Spinner gegen
        Doppelklick, danach Toast und Liste neu laden.
  - [ ] Zurücknehmen/Beenden über `useConfirm` (destruktiv bzw.
        endgültig), Erfolg per Toast.
  - [ ] `App.tsx`: Import `import Langzeitkrankmeldungen from './pages/Langzeitkrankmeldungen';`
        direkt unter Zeile 36 (`Urlaubsantraege`), Route
        `<Route path="/langzeitkrankmeldungen" element={<ErrorBoundary><Langzeitkrankmeldungen /></ErrorBoundary>} />`
        direkt unter Zeile 110.
  - [ ] `RibbonNav.tsx`: `Stethoscope` zum lucide-Import (Zeile 4–9) ergänzen,
        Untergruppe Zeile 124–129 von `label: 'Urlaub'` auf
        `label: 'Abwesenheiten'` umbenennen und den Eintrag
        `{ name: 'Lange Krankheit', href: '/langzeitkrankmeldungen', icon: Stethoscope }`
        hinter „Anträge" setzen.
  - [ ] `MobileBottomNav.tsx`: `Stethoscope` zum lucide-Import (Zeile 4–9)
        ergänzen, denselben Eintrag in `SUBMENU_ITEMS['/zeitbuchungen']`
        (Zeile 37–42) hinter „Urlaub".
  - [ ] Vitest `Langzeitkrankmeldungen.test.tsx`: Liste rendert die Karten;
        Filterwechsel löst einen neuen Fetch mit dem richtigen Status aus;
        API-Fehler zeigt einen Toast; der 42-Tage-Hinweis erscheint in beiden
        Varianten; das Notiz-Label enthält den Diagnose-Hinweis wörtlich.
        `fetch` mocken, Dummy-Daten.
  - [ ] Playwright `e2e/langzeitkrankmeldungen.spec.ts`: `test`/`expect` aus
        `./hilfen/test` (registriert `blockiereFremdeNetzwerkzugriffe`
        automatisch), alle `**/api/**`-Routen stubben (`/api/auth/me`,
        `/api/notifications`, `/api/langzeitkrankmeldungen*`), Seite über den
        Menüpunkt „Lange Krankheit" erreichen (Auffindbarkeit prüfen), Karte
        aufklappen, Zeitleiste sichtbar. Zusätzlich `keinHorizontalerUeberlauf`
        aus `e2e/hilfen/design.ts` und ein langer Nachname **ohne Bindestrich**
        (`kriterien.md`: Bindestriche verdecken Umbruchfehler) mit einer
        Zusicherung „Wert bleibt in seinem Kasten" über
        `scrollWidth`/`clientWidth`, nicht über `boundingBox()`.
        Läuft automatisch in allen drei Projektgrößen (1440/1536/1920).
  - [ ] `npm run lint`, `npx vitest run src/pages/Langzeitkrankmeldungen.test.tsx`,
        `npm run build`, `E2E_PORT=<eigener Port> npx playwright test e2e/langzeitkrankmeldungen.spec.ts`

---

### Task 16 — Verrechnungslohn-Dialog: ausgeklammerte Tage sichtbar machen
- Branch: `lzk/task-16-verrechnungslohn-dialog`
- Worktree: `../wt/lzk-task-16`

- Files:
  - `react-pc-frontend/src/components/VerrechnungslohnRechnerDialog.tsx` (geändert)
  - `react-pc-frontend/src/components/VerrechnungslohnRechnerDialog.test.tsx` (geändert)
  - `react-pc-frontend/e2e/verrechnungslohn-langzeitfall.spec.ts` (neu)
- Vorbild: die Stundentabelle in derselben Datei, Zeile 855–930 — jede Spalte
  ist ein `<td className="py-2 pr-4 text-right font-mono">` mit
  `formatHours(...)` und `title`-Erklärung bei Default-Werten
  (z.B. Zeile 876–884).
- Interfaces:
  - Produces: eine neue Tabellenspalte „Krankengeld/Wiedereingliederung"
    (Kopfzeile) mit dem Wert „122 Tage" und `title`
    „Diese Tage sind aus Jahressoll und Lohnkosten herausgerechnet."
  - Consumes: Task 13 (`ausgeklammerteTage` im DTO).
- Steps:
  - [ ] `Read` `FRONTEND_UI.md`, `Skill` `handwerkerprogramm-design`,
        `Read` `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] Das TypeScript-Interface der Stundenzeile (Datei ab Zeile 68) um
        `ausgeklammerteTage: number;` erweitern.
  - [ ] Neue Spalte in Kopf und Körper der Stundentabelle einfügen, direkt
        hinter „Krankheit". Wert `0` wird als „–" gezeigt, nicht als „0 Tage" —
        sonst rauscht die Spalte optisch bei allen Mitarbeitern mit.
  - [ ] Unter der Tabelle ein erklärender Satz in Handwerker-Sprache, wenn
        irgendein Mitarbeiter ausgeklammerte Tage hat: „Bei X Mitarbeitern
        sind Krankengeld- und Wiedereingliederungszeiten herausgerechnet —
        insgesamt N Tage."
  - [ ] Bestehenden Vitest um die neue Spalte erweitern (Fixture Zeile 49–53
        um `ausgeklammerteTage` ergänzen); ein Fall mit 0 → „–", ein Fall
        mit 122 → „122 Tage".
  - [ ] Playwright-Spec: `ArbeitsgangEditor` öffnen
        (`src/pages/ArbeitsgangEditor.tsx:540` hängt den Dialog ein),
        `/api/verrechnungslohn`-Antwort stubben, Dialog öffnen, Spalte prüfen,
        `keinHorizontalerUeberlauf` — die Tabelle bekommt eine Spalte mehr und
        ist bei 1440 px der wahrscheinlichste Überlaufkandidat.
  - [ ] `npm run lint`, `npx vitest run src/components/VerrechnungslohnRechnerDialog.test.tsx`,
        `npm run build`, `E2E_PORT=<eigener Port> npx playwright test e2e/verrechnungslohn-langzeitfall.spec.ts`

---

### Task 17 — Urlaubsanträge-Seite: Hinweis bei laufender Krankmeldung
- Branch: `lzk/task-17-urlaubsantraege-hinweis`
- Worktree: `../wt/lzk-task-17`

- Files:
  - `react-pc-frontend/src/pages/Urlaubsantraege.tsx` (geändert)
  - `react-pc-frontend/src/pages/Urlaubsantraege.test.tsx` (neu)
  - `react-pc-frontend/e2e/urlaubsantrag-krankmeldung-hinweis.spec.ts` (neu)
- Vorbild: die Typ-Badges in derselben Datei, Zeile 172–199, und der
  Bemerkungs-Kasten Zeile 213–218 (`p-2 bg-slate-50 rounded text-sm`).
- Interfaces:
  - Produces: nichts für andere Tasks.
  - Consumes: Task 12 (`GET /api/urlaub/antraege/hinweise`).
- Steps:
  - [ ] `Read` `FRONTEND_UI.md`, `Skill` `handwerkerprogramm-design`,
        `Read` `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] Für jeden Antrag mit `status === 'OFFEN'` beim Laden **gebündelt**
        `GET /api/urlaub/antraege/hinweise?...` abfragen — alle Anfragen über
        **ein** `Promise.all`, kein Fetch-Wasserfall in der Map-Schleife
        (`kriterien.md`, Performance).
  - [ ] Trifft ein Hinweis zu: ein amber-Kasten in der Karte, über den
        Aktionsknöpfen, mit `AlertTriangle w-4 h-4` und dem Text vom Server.
        Kein Blocker — „Genehmigen" bleibt klickbar (Spec, Abschnitt 7:
        Warnung, keine Ablehnung).
  - [ ] Vitest: Antrag mit Hinweis zeigt den Kasten, Antrag ohne nicht;
        „Genehmigen" bleibt aktiv; ein Fehler der Hinweis-Abfrage lässt die
        Liste stehen und zeigt keinen Kasten (der Hinweis ist Beiwerk, er darf
        die Seite nicht kaputt machen).
  - [ ] Playwright-Spec analog Task 15 (gestubbte `/api`-Routen, Dummy-Namen).
  - [ ] `npm run lint`, `npx vitest run src/pages/Urlaubsantraege.test.tsx`,
        `npm run build`, `E2E_PORT=<eigener Port> npx playwright test e2e/urlaubsantrag-krankmeldung-hinweis.spec.ts`

---

## Abschnitt 9 — Task 18, 19 (max. 3 Tasks, disjunkte Dateien)

Basis: `feature/langzeitkrankmeldung` nach Merge von Abschnitt 8.
Frontend betroffen: ja (`react-zeiterfassung/`) — dort gibt es kein
Playwright (weder Dependency noch Config, siehe Global Constraints); Gate ist
lint + vitest + build. Design-Review sinnvoll für UX/Konsistenz, ohne
E2E-Teil.

### Task 18 — Handy-App: Dashboard-Karte bei laufender Meldung
- Branch: `lzk/task-18-dashboard`
- Worktree: `../wt/lzk-task-18`

- Files:
  - `react-zeiterfassung/src/pages/DashboardPage.tsx` (geändert)
  - `react-zeiterfassung/src/pages/DashboardPage.test.tsx` (geändert)
- Vorbild: die Urlaubsverfall-Warnung in derselben Datei — Laden in
  `fetch('/api/zeiterfassung/urlaubsverfall/${token}')` bei Zeile 328,
  State-Deklaration Zeile 95–103, Darstellung Zeile 1206–1228 (Kasten mit
  Icon-Kachel links, Titel, Text, Link).
- Interfaces:
  - Produces: nichts für andere Tasks.
  - Consumes: Task 6 (`GET /api/zeiterfassung/langzeitkrankmeldung/{token}`).
- Steps:
  - [ ] `Read` `FRONTEND_UI.md`, `Skill` `handwerkerprogramm-design`,
        `Read` `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] Interface und State direkt neben `UrlaubsVerfallWarnung` (Zeile 95–103):
        ```ts
        interface LangzeitFall {
            phase: 'LOHNFORTZAHLUNG' | 'KRANKENGELD' | 'WIEDEREINGLIEDERUNG'
            phaseLabel: string
            heuteGeplanteStunden: number | null
            seit: string
            bisDatum: string | null
        }
        ```
        Leeres Objekt `{}` vom Server → `null` setzen (der Endpoint liefert
        `{}`, wenn nichts anliegt — genau wie `urlaubsverfall`).
  - [ ] Den Fetch in denselben `useEffect` wie die Urlaubsverfall-Abfrage
        (Zeile ~325–335) hängen und beide über `Promise.all` parallel laufen
        lassen — nicht nacheinander (Handwerker sind oft im Funkloch,
        Wasserfall kostet spürbar).
  - [ ] Karte über der Urlaubswarnung rendern, solange `langzeitFall !== null`.
        Text bei Wiedereingliederung: „Wiedereingliederung — heute 4 Stunden
        geplant", sonst nur `phaseLabel` + „seit 01.03.2026". Farben
        `bg-teal-50 border-teal-200`, Icon-Kachel `bg-teal-100`, Icon
        `Stethoscope` (zum lucide-Import in Zeile 3 ergänzen) — bewusst
        teal statt amber/rot, damit es nicht wie eine Warnung aussieht: es ist
        eine Information.
  - [ ] Keine neue Abhängigkeit, kein zusätzliches Bild — Bundle-Größe ist auf
        dem Handy ein echtes Kriterium (`kriterien.md`).
  - [ ] `DashboardPage.test.tsx` (432 Zeilen) erweitern: `fetch`-Mock um die
        neue Route ergänzen; Karte erscheint bei laufender Wiedereingliederung
        mit dem Stundentext; Karte fehlt bei `{}`; ein Fehler der neuen
        Abfrage lässt das Dashboard vollständig funktionieren (kein weißer
        Screen). Dummy-Daten.
  - [ ] `npm run lint`, `npx vitest run src/pages/DashboardPage.test.tsx`,
        `npm run build`. Kein Playwright — `react-zeiterfassung` hat keins
        (verifiziert: keine Dependency, keine `playwright.config.ts`).

---

### Task 19 — Handy-App: Phase im Abwesenheiten-Verlauf (nur lesen)
- Branch: `lzk/task-19-abwesenheiten-verlauf`
- Worktree: `../wt/lzk-task-19`

- Files:
  - `react-zeiterfassung/src/pages/AbwesenheitenPage.tsx` (geändert)
  - `react-zeiterfassung/src/pages/AbwesenheitenPage.test.tsx` (neu)
- Vorbild: `typConfig`/`statusConfig` und die Kartenliste in derselben Datei
  (Zeile 20–32 und 168–210); Stat-Kacheln Zeile 116–131.
- Interfaces:
  - Produces: nichts für andere Tasks.
  - Consumes: Task 6.
- Steps:
  - [ ] `Read` `FRONTEND_UI.md`, `Skill` `handwerkerprogramm-design`,
        `Read` `TESTING_SECURITY.md` + `kriterien.md`.
  - [ ] Beim Laden zusätzlich
        `GET /api/zeiterfassung/langzeitkrankmeldung/${token}` abfragen
        (Token wie im Dashboard aus dem Speicher), parallel zur bestehenden
        Antragsabfrage über `Promise.all` im `useEffect` (Zeile 44–71).
  - [ ] Läuft eine Meldung: ein Info-Banner unter dem Header, über den
        Stat-Kacheln — „Wiedereingliederung seit 01.03.2026 — heute 4 Stunden
        geplant", `bg-teal-50 border border-teal-200 rounded-xl p-3`, Icon
        `Stethoscope` (zum Import Zeile 3 ergänzen). **Nur lesend** — kein
        Button, keine Bearbeitung; bearbeitet wird ausschließlich im Büro
        (Spec, Abschnitt 6). Das gehört als Kommentar in den Code, damit es
        niemand später „hilfreich" ergänzt.
  - [ ] Der Plus-FAB (Zeile 222–228) bleibt unverändert — eine Krankmeldung
        legt weiterhin nur das Büro an.
  - [ ] Neuer Vitest `AbwesenheitenPage.test.tsx`: Banner erscheint bei
        laufender Meldung, fehlt bei `{}`; die Antragsliste rendert
        unabhängig davon; ein Fehler der neuen Abfrage bricht die Seite nicht.
        Dummy-Daten.
  - [ ] `npm run lint`, `npx vitest run src/pages/AbwesenheitenPage.test.tsx`,
        `npm run build`.

---

## Bewusst nicht in diesem Umfang

Damit es im Review nicht als „vergessen" auftaucht:

- **Der Zeiterfassungs-Kalender bekommt keinen sichtbaren Link in die
  Krankmeldung.** Die Spec listet unter „Betroffene Bereiche" nur drei
  Desktop-Dateien, und „kein neuer Code im 1417-Zeilen-Modal" ist ein
  Nicht-Ziel. Die Information, dass jemand über seinen Stufenplan hinaus
  gestempelt hat, ist stattdessen auf der Detailansicht der Krankmeldung
  sichtbar (`stufenplanTage` mit `ueberPlan`, Task 4/5/15) — dort schaut der
  Chef ohnehin hin.
- **Kein Hintergrundjob**, der Phasen automatisch weiterschaltet
  (Nicht-Ziel der Spec). Der 42-Tage-Countdown ist reine Anzeige plus ein
  Knopf.
- **Keine Diagnose, keine eAU, kein BEM** (Nicht-Ziele).
- **`AbwesenheitsTyp` bleibt unverändert.** `KRANKHEIT` bleibt `KRANKHEIT` —
  jede bestehende Abfrage auf diesen Wert bleibt gültig.

---

## Log

<wird nicht hier befüllt — siehe
`docs/superpowers/plans/2026-09-08-langzeitkrankmeldung-log.md`. Dieser
Abschnitt bleibt für die Abschluss-Zusammenfassung je Abschnitt durch den
Review-Agenten reserviert.>
