# Fortschritts-Tracker: Preisanfrage an mehrere Lieferanten

**Zweck dieses Dokuments:** Laufender Status des Features. Jede Session/jeder Agent liest
hier den aktuellen Stand und hakt erledigte Punkte ab (bzw. ergänzt neue Aufgaben).

**Originaler Plan:** [preisanfrage-mehrere-lieferanten.md](preisanfrage-mehrere-lieferanten.md)
**Branch:** `feature/en1090-echeck` (wird vor Merge auf `feature/preisanfrage-multi-lieferant` umgebogen)
**Letzter Commit dieses Features:** Etappe 8 (EmailCenter-Badge) — Hash siehe Session-Log

---

## 📋 Regeln für den nächsten Agent

1. **Erst diesen Tracker lesen**, dann den Original-Plan. Nicht neu von vorne anfangen.
2. **Jede erledigte Aufgabe abhaken** (`[x]`) mit Commit-Hash, falls committed.
3. **Neue Erkenntnisse** in den Abschnitten "Offene Entscheidungen" / "Abweichungen vom Plan" notieren.
4. **Architektur-Entscheidungen NIE still ändern** — immer unten dokumentieren, damit der nächste Agent nicht neu diskutiert.
5. **Commit-Strategie:** Kleine Commits pro Etappe (siehe Original-Plan Abschnitt 9).
6. **Vor dem Commit:** `./mvnw.cmd test -q | tail -20` muss grün sein.
7. Nach jeder Aufgabe Skill: /review-and-ship ausführen

---

## 🔑 Geklärte Entscheidungen (User-Input — nicht nochmal fragen!)

| Entscheidung | Wahl | Wann geklärt |
|---|---|---|
| Logo-Upload-Scope | **Option B** — alle PDFs nutzen hochgeladenes Firmenlogo, KEIN Fallback auf Software-Logo | 2026-04-20 |
| `/static/firmenlogo_icon.png` | Ist das **Software-Logo**, NICHT Handwerker-Logo. Muss ersetzt werden. | 2026-04-20 |
| Mail-Versand-Mechanik | Direkt `new EmailService(smtpHost,...).sendEmailAndReturnMessageId(...)` im `PreisanfrageService` (Pattern wie `EmailController.sendInvoiceEmail`) | 2026-04-20 |
| Vergabe-Logik | Bei Vergabe: bestehende `ArtikelInProjekt`-Zeilen auf Gewinner-Lieferanten **umrouten** (setze `lieferant_id` + übernehme `einzelpreis`), KEINE neuen Zeilen anlegen | 2026-04-20 |
| Email.inReplyTo-Feld | **NICHT nötig** — `EmailImportService` setzt bereits `parentEmail` via Header. Match läuft über `parentEmail.messageId == preisanfrage_lieferant.outgoing_message_id` | 2026-04-20 |
| Flyway-Version | V227 ist frei (letzte ist V226) | 2026-04-20 |
| Empfangs-Postfach Test | `info-bauschlosserei-kuhn@t-online.de` bleibt | 2026-04-20 |
| Rename `ArtikelInProjekt` | **NICHT jetzt** — separates Thema. Struktur wird in anderen Features ausgebaut (siehe unten "Folge-Features") | 2026-04-20 |
| Antwort-Zuordnung Strategie | **4-stufige Fallback-Kette**: (1) In-Reply-To, (2) To-Adresse `TOKEN@domain` (Config `preisanfrage.reply-to-domain`, optional), (3) Token-Regex im Betreff, (4) PA-Nummer + Absender-E-Mail-Abgleich | 2026-04-20 |
| Eigene Domain langfristig | **Ja** — User will eigene Domain (z.B. `bauschlosserei-kuhn.de`) mit Catch-All betreiben. Fallback 2 wird dann aktiviert. Bis dahin: leer in Properties = deaktiviert, Fallbacks 1/3/4 reichen. | 2026-04-20 |
| Lieferanten-Anweisung | **Mail-Body + PDF-Hinweisbox bitten explizit um E-Mail-Antwort mit PDF-Anhang.** Token/PA-Nummer immer im Betreff abbildbar. Handschriftlich ausgefuellte eingescannte PDFs bleiben manuell — unvermeidbar. | 2026-04-20 |
| KI-Aufrufe auf PDFs | **Immer PDF-Direkt** via `inline_data` mit `mime_type=application/pdf` (bestehende `GeminiDokumentAnalyseService.rufGeminiApiMitPrompt`). Niemals PDFTextStripper / Tika vorschalten. | 2026-04-20 |
| KI-Preisvergleich-Trigger | **Zwei Wege:** (1) Manueller Button „Angebote vergleichen" pro Preisanfrage, (2) Auto-Trigger beim Statuswechsel auf `VOLLSTAENDIG`. Beide rufen denselben Service. | 2026-04-20 |
| Artikelstamm-Update aus Angeboten | `ArtikelMatchingAgentService` wird wiederverwendet (DRY). Schnittkosten/Dienstleistungen werden per SYSTEM_PROMPT gefiltert (SKIPPED). Fallback-Suche bei geaenderter externer Nr. laeuft ueber `search_artikel(werkstoff+kategorie+abmessungen)`. | 2026-04-20 |
| Kategorie-Regeln fuer KI | **(A)** KI sieht rekursiven Kategorie-Pfad ("Metalle > Flachstahl > Baustahl") + `isLeaf`-Flag. **(B)** Artikel nur in Leaf-Kategorien einfuegen — Non-Leaf wird vom Tool abgelehnt. **(C)** Neue Kategorie anlegen via neues Tool `create_kategorie(beschreibung, parentKategorieId)` mit Duplikat-Check. | 2026-04-20 |

---

## 🗂️ Abweichungen vom Original-Plan

### Keine V228 für `Email.inReplyTo`
Der Original-Plan sah eine zweite Migration vor, um `email.in_reply_to` als Spalte
hinzuzufügen. **Nicht nötig:** Der `EmailImportService.findParentEmail()` setzt bereits
automatisch die `parentEmail`-Relation via In-Reply-To-Header. Wir matchen daher über
`preisanfrage_lieferant.outgoing_message_id == email.parentEmail.messageId`.
→ Spart einen Commit und eine Migration.

### V227 referenziert `artikel_in_projekt` statt `bestellung`
Original-Plan spricht von `bestellung(id)`. Die Tabelle heißt real `artikel_in_projekt`
(Entity `ArtikelInProjekt`). Spalte in V227 heißt `artikel_in_projekt_id`.

### Security-Fix vorab (Commit `7072ffb`)
Während der Erkundung wurde Klartext-SMTP-Passwort in [EmailService.java:737](../../src/main/java/org/example/email/EmailService.java#L737) und
[ListFolders.java:10](../../src/main/java/org/example/email/ListFolders.java#L10) gefunden. User hat Passwort rotiert, Commit `7072ffb`
entfernt die Klartext-Stellen. **Alte Passwörter bleiben in Git-History** (nicht rewritten).

---

## 🚦 Etappen-Fortschritt

Legende: `[ ]` offen · `[~]` in Arbeit · `[x]` erledigt · `[!]` blockiert

### ✅ Etappe 0: Erkundung + Voraussetzungs-Check
- [x] Flyway-Stand verifiziert (V226 letzte, V227 frei)
- [x] `Email.inReplyTo`-Status verifiziert (nicht nötig, siehe Abweichungen)
- [x] Mail-Versand-Mechanik verstanden (`EmailService.sendEmailAndReturnMessageId`)
- [x] Firmenlogo-Upload-Situation geklärt (gibt's nicht → neues Feature)
- [x] `util/`-Package existiert
- [x] Tabelle `artikel_in_projekt` bestätigt (kein `bestellung`!)

### ✅ Etappe 1: DB-Schema V227 + Entities + Repositories
**Commit-Ziel:** `feat(preisanfrage): DB-Schema + Entities + Repositories (V227)`

- [x] `V227__preisanfrage.sql` mit 4 Tabellen + idempotentem FK — angelegt
- [x] Entity `Preisanfrage.java`
- [x] Entity `PreisanfrageLieferant.java`
- [x] Entity `PreisanfragePosition.java`
- [x] Entity `PreisanfrageAngebot.java`
- [x] Enum `PreisanfrageStatus.java`
- [x] Enum `PreisanfrageLieferantStatus.java`
- [x] Repo `PreisanfrageRepository.java`
- [x] Repo `PreisanfrageLieferantRepository.java`
- [x] Repo `PreisanfragePositionRepository.java`
- [x] Repo `PreisanfrageAngebotRepository.java`
- [x] `./mvnw.cmd compile` grün (BUILD SUCCESS)
- [ ] Spring Boot startet lokal ohne Migration-Fehler (user-seitig zu verifizieren)
- [x] Commit erstellen — Hash siehe unten im Session-Log

### ✅ Etappe 2: Service + TokenGenerator + Backend-Tests
**Commit-Ziel:** `feat(preisanfrage): Service + TokenGenerator + Tests`

- [x] `util/TokenGenerator.java` (Alphabet `A-Z` + `2-9`, ohne 0,O,1,I) — mit Retry-Helper
- [x] `service/PreisanfragePdfGenerator.java` — funktionales Interface, wird in Etappe 3 von `BestellungPdfService` implementiert
- [x] DTOs minimal (da Service sie in Signaturen braucht):
  - [x] `PreisanfrageErstellenDto`
  - [x] `PreisanfragePositionInputDto`
  - [x] `PreisanfrageAngebotEintragenDto`
  - [x] `PreisanfrageVergleichDto` (inkl. `LieferantSpalte`, `PositionZeile`, `AngebotsZelle`)
- [x] `service/PreisanfrageService.java` mit Methoden:
  - [x] `erstellen(PreisanfrageErstellenDto)`
  - [x] `versendeAnAlleLieferanten(Long)`
  - [x] `versendeAnEinzelnenLieferanten(Long)`
  - [x] `getVergleich(Long)` — gibt `PreisanfrageVergleichDto` zurück
  - [x] `eintragen(PreisanfrageAngebotEintragenDto)` — aktualisiert autom. Status
  - [x] `vergebeAuftrag(Long, Long)` — routet `ArtikelInProjekt.lieferant`/`preisProStueck` auf Gewinner um
  - [x] `listeLieferanten(Long)` Bonus-Methode für spätere Views
- [x] Test `TokenGeneratorTest.java` (6 Tests — Alphabet, Retry, Null-Guard)
- [x] Test `PreisanfrageServiceTest.java` (17 Tests — 6 Happy-Paths + 5 Fehlerfälle + 5 Security + Stub-Factory für `EmailService`)
- [x] Security-Tests (SQLi in Notiz, XSS in Produktname + Lieferantenname/Mail-Body, negative IDs, null-IDs, negative Einzelpreise)
- [x] `./mvnw.cmd test` grün — **1176 Tests, 0 Failures** (inkl. bestehender Integration-Tests)
- [x] Commit erstellen — Hash siehe Session-Log

**Architektur-Notizen (für nachfolgende Agenten):**
- `PreisanfrageService` nutzt `Optional<PreisanfragePdfGenerator>` — Spring injiziert `Optional.empty()`,
  solange Etappe 3 die Implementierung nicht geliefert hat. Versand schlägt bis dahin mit klarer
  `IllegalStateException` fehl; Test nutzt Mock.
- Versand via package-private `EmailServiceFactory` (default: `EmailService::new`),
  austauschbar im Test — kein echter SMTP-Zugriff in Tests, keine Secrets.
- `@Autowired` explizit am Produktiv-Konstruktor, weil der Service zwei Konstruktoren hat
  (zweiter ist nur für Tests, package-private).
- Nummer-Format final: `PA-{YYYY}-{3-stellige lfdNr}`. Token-Format: `PA-{YYYY}-{lfdNr}-{5 Zeichen aus A-Z+2-9}`.
- HTML-Mail-Body HTML-escaped Lieferantenname und Token — XSS-Test deckt das ab.

### ✅ Etappe 3: PDF-Variante + Firmenlogo-Upload (FirmaEditor)
**Commit-Ziel:** `feat(firma): Firmenlogo-Upload im FirmaEditor` + `feat(preisanfrage): PDF-Variante`

**Firmenlogo-Upload (eigener Sub-Commit vor der PDF-Variante!) — Commit `c4e14c3`:**
- [x] Endpoint `POST /api/firma/logo` (multipart) in `FirmaController`
- [x] Endpoint `GET /api/firma/logo` (liefert Bild-Binary)
- [x] Endpoint `DELETE /api/firma/logo`
- [x] Upload-Verzeichnis `uploads/firma/logo/` (gitignored prüfen!) — `/uploads` bereits in `.gitignore`
- [x] Service-Methode `FirmeninformationService.loadLogoImage()` → liefert iText-Image oder `null`
- [x] `BestellungPdfService` Zeile 59 + 171: ersetze Hart-Link durch `loadLogoImage()`
- [x] FirmaEditor.tsx: Upload-Feld mit Vorschau + Löschen-Button

**PDF-Variante Preisanfrage — Commit `d0d1590`:**
- [x] `BestellungPdfService.generatePdfForPreisanfrage(palId)` neue Methode
  - [x] Rote Kopfzeile "PREISANFRAGE {nummer}" + Token-Box
  - [x] Hinweis-Box "Bitte Code angeben"
  - [x] Rückmeldefrist-Feld
  - [x] Positionen-Tabelle mit leerer Spalte "Ihr Preis €/Einheit"
  - [x] KEINE EN-1090-Infobox, KEINE Zeugnis-Blöcke
- [x] `BestellungPdfService implements PreisanfragePdfGenerator` (Interface aus Etappe 2)
- [x] `PreisanfrageLieferantRepository` + `PreisanfragePositionRepository` via Konstruktor injiziert (expliziter Konstruktor statt `@AllArgsConstructor`)
- [x] Test `BestellungPdfServiceTest.generatePdfForPreisanfrage_enthaeltTokenUndNummer` — assertet Nummer + Token im PDF-Text
- [x] Bestehende 3 Tests auf neue Konstruktor-Signatur umgestellt (Factory-Helper `newService()`)
- [x] `./mvnw.cmd test` grün — **1195 Tests, 0 Failures**
- [x] Commit (Logo und PDF getrennt: `c4e14c3` + `d0d1590`)

### ✅ Etappe 4: Auto-Zuordnung via parentEmail
**Commit-Ziel:** `feat(email): Preisanfrage-Auto-Zuordnung via parentEmail/Token`

- [x] `service/PreisanfrageZuordnungService.java`
  - [x] `tryMatch(Email incoming)` via `parentEmail.messageId` ODER Token-Regex im Betreff
  - [x] Bei Match: `PreisanfrageLieferant.antwortEmail = incoming`, Status `BEANTWORTET`, `antwortErhaltenAm = now()`
- [x] `EmailAutoAssignmentService.tryAutoAssign`: Regel `tryAssignToPreisanfrage` als ERSTE Regel eingebaut (vor `tryAssignToLieferant`); bei Treffer zusätzlich `email.assignToLieferant(pal.lieferant)`, damit Mail weiter im Lieferanten-Postfach landet.
- [x] Test `PreisanfrageZuordnungServiceTest` — 6 Cases: parentEmail-Match, Token-Fallback, kein Match, Status/Timestamp-Verify, SQL/HTML-Payload-Isolation, null-Guard
- [x] `EmailAutoAssignmentServiceTest` um Happy-Path für neue First-Rule erweitert (`@Nested PreisanfrageAntwort`) — verifiziert Vorrang vor Domain-Match
- [x] Commit `18c53d6`

**Architektur-Notizen:**
- Token-Pattern als `static final Pattern TOKEN_PATTERN` im Service — kompilierter Regex, einmalig geladen.
- `tryMatch` ist defensiv: `null`-Guard auf `incoming`, leere/null-Prüfung auf `parentMessageId` und `subject`.
- Token-Regex extrahiert immer nur den sauberen Token — SQL-/HTML-Payloads drumrum landen nie im Repository-Call (explizit getestet).
- KEIN neuer `EmailZuordnungTyp.PREISANFRAGE_ANTWORT` (bewusste Entscheidung aus Original-Plan 4.8): Die Mail bleibt `LIEFERANT`-zugeordnet, der Link zur Preisanfrage läuft rückwärts über `PreisanfrageLieferant.antwortEmail`.

### ✅ Etappe 4b: 4-stufige Fallback-Kette + Reply-To-Domain
**Commit-Ziel:** `feat(preisanfrage): 4-stufige Fallback-Kette fuer Antwort-Zuordnung` (Commit `8b5bb05`)

Erweitert Etappe 4 um zwei zusätzliche Matching-Stufen und macht den Reply-To-Versand konfigurierbar, damit auch Lieferanten zugeordnet werden können die **nicht** auf "Antworten" klicken.

- [x] Neuer Config-Key `preisanfrage.reply-to-domain=` in `application.properties` (leer = deaktiviert, via `application-local.properties` überschreibbar)
- [x] `EmailService.sendEmailAndReturnMessageId` erhält neuen Overload mit `replyTo`-Parameter (alter Overload delegiert mit `null` — vollständig rückwärtskompatibel für bestehende Caller wie `EmailController`)
- [x] `PreisanfrageService` setzt bei Versand `Reply-To: TOKEN@domain`, **sofern Domain konfiguriert**; Mail-Body zeigt dann zusätzlich die volle Antwortadresse prominent an
- [x] Mail-Body und PDF-Hinweisbox bitten explizit um E-Mail-Antwort mit PDF-Anhang (Umlaute gemäß CLAUDE.md)
- [x] `PreisanfrageZuordnungService` mit expliziter Konstruktor-Injection (statt `@RequiredArgsConstructor`) + 4 Matching-Stufen:
  1. **In-Reply-To** (wie Etappe 4)
  2. **To-Adresse** `TOKEN@reply-to-domain` — nur aktiv wenn Domain konfiguriert
  3. **Token-Regex im Betreff** (wie Etappe 4)
  4. **PA-Nummer im Betreff** (`PA-YYYY-NNN`) + Absender-E-Mail-Abgleich gegen `pal.versendetAn` der zugehörigen Preisanfrage
- [x] 4 neue Tests: Fallback 2 mit Domain + ohne Domain, Fallback 4 mit Absender-Match + ohne Absender-Match
- [x] `PreisanfrageServiceTest` Stubs auf neuen 8-Parameter-Overload umgestellt
- [x] `./mvnw.cmd test` grün — **1242 Tests, 0 Failures** (+4)
- [x] Commit `8b5bb05` + Push

**Architektur-Notizen:**
- **Pattern `PA_NUMMER_PATTERN`** nutzt negativen Lookahead `(?!-[A-Z2-9]{5})`, damit Fallback 4 nicht auf eine Volltoken-Zeile triggert und mit Fallback 3 kollidiert (saubere Priorisierung).
- **Fallback 2 case-insensitiv**: Manche Mail-Clients normalisieren die Empfänger-Adresse (`max.MUSTERMANN@...`), daher `Pattern.CASE_INSENSITIVE` + `.toUpperCase()` vor dem Repository-Call.
- **Fallback 4 konservativ**: nur Match wenn Absender-Mail genau auf `pal.getVersendetAn()` passt (`equalsIgnoreCase`). Wird eine Antwort von einer anderen Adresse geschickt (häufig bei "info@..."-Postfächern), bleibt es manuell — bewusste Entscheidung, um Fehl-Zuordnungen zu vermeiden.
- **Domain-Feature ausschaltbar**: Alle 3 Non-Reply-To-Fallbacks (1/3/4) funktionieren auch ohne konfigurierte Domain. Die Domain-Einführung ist additiv, nicht disruptiv.
- **Langfristig**: Wenn eigene Domain `bauschlosserei-kuhn.de` steht + Catch-All-IMAP läuft → Config-Key setzen → Fallback 2 wird automatisch aktiv. Kein Code-Change nötig.

### ✅ Etappe 5: Controller + DTOs + Mapper
**Commit-Ziel:** `feat(preisanfrage): Controller + DTOs + Mapper` (Commit `12fbeae`)

- [x] DTOs unter `dto/Preisanfrage/`:
  - [x] `PreisanfrageErstellenDto` (bereits in Etappe 2 angelegt)
  - [x] `PreisanfragePositionDto` — neu (Output-DTO für Positionen)
  - [x] `PreisanfrageResponseDto` — neu (Detail-Ansicht inkl. Lieferanten + Positionen)
  - [x] `PreisanfrageLieferantDto` — neu (Helper für Response, kein Entity-Leak)
  - [x] `PreisanfrageVergleichDto` (bereits in Etappe 2 angelegt)
  - [x] `PreisanfrageAngebotEintragenDto` (bereits in Etappe 2 angelegt)
- [x] `mapper/PreisanfrageMapper.java` — expliziter `@Component` im `ProjektMapper`-Stil (kein MapStruct), null-safe, Lazy-Relations-freundlich
- [x] `controller/PreisanfrageController.java` — 10 Endpoints mit `@Valid`, DTO-only Responses, `X-Error-Reason`-Header bei 400/404/409, PDF-Download via `InputStreamResource`
- [x] Service um `findeById` / `listeAlle(filterStatus)` / `abbrechen(id)` erweitert
- [x] `UnifiedEmailDto` erweitert um `preisanfrageLieferantRef` (inner DTO `PreisanfrageLieferantRef` mit `preisanfrageId/nummer/palId/lieferantId/lieferantenname`); `UnifiedEmailController` injiziert `PreisanfrageLieferantRepository` und mappt rückwärts via `findByAntwortEmail_Id` in `toListDto`/`toDto`
- [x] Neuer Repo-Lookup `PreisanfrageLieferantRepository.findByAntwortEmail_Id(Long emailId)`
- [x] `@WebMvcTest PreisanfrageControllerTest` — 27 Tests: Happy-Paths aller 10 Endpoints, 404 bei `IllegalArgumentException`, 409 bei `IllegalStateException`, 400 bei Bean-Validation-Failures, Security-Cases (XSS in Notiz passt durch — Controller ist Pass-Through, Sanitizing ist Frontend-Rolle; SQLi in Status-Filter → `parseStatus` liefert `null` statt 500; negative/null IDs → 400/404)
- [x] 7 Service-Unit-Tests für die neuen Methoden (`findeById`/`listeAlle` mit und ohne Status-Filter / `abbrechen` Happy-Path + IllegalState bei VERGEBEN + IllegalArgument bei unbekannter ID)
- [x] `./mvnw.cmd test` grün — **1238 Tests, 0 Failures**
- [x] Commit erstellt: `12fbeae`

**Architektur-Notizen (für nachfolgende Agenten):**
- **Kein `@PreAuthorize`**: Das Projekt nutzt durchgängig keine Method-Security-Annotationen (0 Treffer im Grep). Zugriffsschutz läuft global über `SecurityConfig` (`/api/**` mit Session-Auth). Der neue Controller folgt dem Muster; keine neue Security-Abstraktion einführen.
- **DTO-Strategy**: `PreisanfrageResponseDto` enthält gezielt NICHT die Angebote/Preise pro Lieferant — die gehören in den `PreisanfrageVergleichDto` (eigener Endpoint `/vergleich`). Sauber getrennt, damit Listen-Views leichtgewichtig bleiben.
- **Fehler-Mapping im Controller**: `IllegalArgumentException` → 404 (not found), `IllegalStateException` → 409 (conflict), Bean-Validation-Failure → 400. Grund: der Service wirft bewusst nur diese zwei Exception-Typen — so entfallen Custom-Exceptions.
- **`parseStatus(String)` toleriert Müll**: unbekannter Enum-Wert im Query-Param liefert `null` (= kein Filter) statt 500. Das verhindert, dass ein SQLi-Versuch (`?status=';DROP TABLE--`) zum Server-Error wird.
- **UnifiedEmailDto-Ruecklink**: Das Feld `preisanfrageLieferantRef` ist **optional** (`null` wenn die E-Mail nichts mit einer Preisanfrage zu tun hat). Für Etappe 8 (EmailCenter-Badge) kann das Frontend direkt darauf mappen, ohne zusätzliche Roundtrips.

### ✅ Etappe 6: Frontend Seite + AngeboteEinholenModal
**Commit-Ziel:** `feat(preisanfrage): Frontend-Seite + AngeboteEinholenModal`

- [x] `react-pc-frontend/src/pages/PreisanfragenPage.tsx` — Status-Tabs, Liste, Empty-State, Abbrechen-Action, Vergleichs-Platzhalter (Toast, kommt in Etappe 7)
- [x] Route `/einkauf/preisanfragen` in `App.tsx`
- [x] Navigation-Eintrag in `RibbonNav.tsx` unter Projektmanagement → Einkauf direkt hinter "Bedarf" (Icon: `Scale`)
- [x] `react-pc-frontend/src/components/AngeboteEinholenModal.tsx` — vorausgewaehlte Positionen ODER Fallback `GET /api/bestellungen/offen`, Multi-Lieferant-Liste mit Suche, Pflicht-`DatePicker`, Notiz, sequenzielles `POST /preisanfragen` + `/versenden`
- [x] Button "Angebote einholen" in `BestellungEditor.tsx` neben "E-Mail"-Button, uebergibt Gruppe via `toBedarfPosition`-Mapper als `vorausgewaehltePositionen`
- [x] Vitest `AngeboteEinholenModal.test.tsx` — 7 Tests (Submit-Disabled ohne Lieferant, Multi-Select, Reihenfolge POST create→versenden, vorausgewaehlte Positionen, Disabled bei leerer Position-Liste, Lieferanten-Suche, Pflichtfeld-Markierung)
- [x] `npm install` fehlende `@xyflow/react` + `@dagrejs/dagre` (pre-existing, nicht durch dieses Feature ausgeloest)
- [x] `npm run lint` grün (1 unrelated warning in `ArtikelVorschlaegeModal.tsx`)
- [x] `npm run build` grün (nach npm install)
- [x] `./mvnw.cmd test` grün — **1242 Tests, 0 Failures** (Backend unveraendert)
- [x] Commit erstellt: `9013a7c`

### ✅ Etappe 7: KI-Preisvergleich + Vergleichs-Matrix + Preiserfassung
**Commit-Ziel:** mehrere Splits (siehe unten)

**User-Entscheidung (2026-04-20):** `create_kategorie` legt Kategorien **direkt in der DB** an (Option a), nicht via KategorieVorschlag-Pattern. Keine V228 noetig.

- [x] `ArtikelMatchingToolService` erweitert: `listKategorien()` liefert `id | parentId | isLeaf | pfad`, Non-Leaf-Check in `proposeNewArtikel` (unbekannte Kategorie-ID bleibt silent ignore, non-leaf existierend → Fehler), neues Tool `create_kategorie(beschreibung, parentKategorieId, konfidenz, begruendung)` mit Duplikat-Check (case-insensitive) + Konfidenz-Schwelle 0.9 fuer Hauptkategorien.
- [x] `ToolSideEffect.KategorieAngelegt(id, pfad, bereitsExistiert)` hinzugefuegt.
- [x] `ArtikelMatchingAgentService.SYSTEM_PROMPT` um 4a/b/c/d-Regeln erweitert (Pfad lesen, Leaf-only, `create_kategorie` bei Bedarf).
- [x] Matching-Agent-Modell auf `gemini-3.1-pro-preview` umgestellt (Default in `application.properties` + `@Value`-Default).
- [x] `PreisanfrageAngebotsExtraktionService` (neu, ~420 Zeilen) — synchron `extrahiereFuerPreisanfrage(id)` + async `extrahiereAsync(id)` fuer Auto-Trigger. Pro PAL: PDF-Bytes laden (eigene Pfad-Resolution analog `EmailAttachmentProcessingService`), Prompt bauen (Positionen mit exakter `positionId`), `rufGeminiApiMitPrompt(..., "application/pdf", ..., true)`, JSON parsen, pro gematchter Position `PreisanfrageAngebot` speichern (erfasstDurch="ki-extraktion"), parallel `ArtikelMatchingAgentService.matcheOderSchlageAn(...)` pro Material-Position (DRY). Zusatzpositionen werden NICHT im Artikelstamm gelernt (nur geloggt). Idempotent: bestehende Angebote werden nicht ueberschrieben.
- [x] REST-Endpoint `POST /api/preisanfragen/{id}/angebote/extrahieren` im Controller (404/409/OK mit ExtraktionsErgebnis-DTO).
- [x] Auto-Trigger-Hook in `PreisanfrageService.aktualisiereStatus(...)`: beim Uebergang auf `VOLLSTAENDIG` wird `angebotsExtraktion.extrahiereAsync(preisanfrageId)` gefeuert. Injection via Setter (`@Autowired(required=false)`) → keine Zirkel-Dependency, Tests laufen ohne KI-Stack.
- [x] `components/PreisanfrageVergleichModal.tsx` (Matrix-Tabelle) — Positionen in Zeilen (sortiert via `reihenfolge`), Lieferanten in Spalten (sortiert via Name), guenstigster Preis `bg-rose-50 font-bold` + Trophy-Icon, Summenzeile (netto), Action-Zeile mit „Preise eintragen" + „Beauftragen" + Header-Button „KI-Preise auslesen". Sticky Position-Spalte fuer breite Matrizen.
- [x] `components/PreiseEintragenModal.tsx` — manuelles Eintragen/Korrigieren pro PAL. KI-Vorschlag-Markierung mit Sparkle-Icon + „KI-Vorschlag, bitte pruefen". POST pro Position mit Preis → `/api/preisanfragen/angebote`.
- [x] `PreisanfragenPage.tsx`: Button „Vergleich oeffnen" triggert jetzt echt (statt Toast-Platzhalter). Modal-State + Preise-Eintragen-Sub-Modal eingebunden, Liste neu laden bei Speichern/Vergeben.
- [x] Schema-Fix: `Preisanfrage.status` + `PreisanfrageLieferant.status` mit `columnDefinition = "VARCHAR(40)"` — Hibernate 6 erwartete sonst MySQL-ENUM und scheiterte bei Schema-Validation.
- [x] Backend-Tests: `ArtikelMatchingToolServiceTest` +9 Tests (Leaf-Check happy/non-happy + `create_kategorie` Happy/Duplikat/unbekannter Parent/zu-niedrige-Konfidenz/leere-Beschreibung/Hauptkategorie), `PreisanfrageAngebotsExtraktionServiceTest` neu (11 Tests: Prompt-Build, Happy-Path, Zusatzpositionen isoliert, Idempotenz, leer/truncated JSON, fehlender PDF-Anhang, negative/unbekannte IDs, ueberspringen bei bereits vorhandenen Angeboten), `PreisanfrageControllerTest` +3 Tests (Extraktions-Endpoint happy/404/409). `listKategorien`-Test auf neues Format umgeschrieben + neuer Dreifach-Pfad-Test.
- [x] Frontend-Vitest: `PreisanfrageVergleichModal.test.tsx` (5 Tests: Matrix-Render + Guenstigster-Markierung, Beauftragen + Confirm, KI-Preise auslesen, onPreiseEintragen-Callback, Summenzeile), `PreiseEintragenModal.test.tsx` (4 Tests: KI-Badge, Submit-Disabled ohne Preise, Multi-Position-POST, Fehler-Toast).
- [x] `./mvnw.cmd test` grün — **1264 Tests, 0 Failures** (+22 neue, existierende bleiben stabil).
- [x] `npm run lint` + `npm run build` + Vitest grün (9/9 neue Modal-Tests).
- [x] Commits erstellt (4 Splits):
  - `238f9f5` feat(artikel-matching): Kategorie-Pfad + Leaf-Check + create_kategorie Tool
  - `c500f70` feat(preisanfrage): KI-Angebotsextraktion + Auto-Trigger + Schema-Fix
  - `33d41d2` feat(preisanfrage): Vergleichs-Matrix + Preise-eintragen-Modal
  - `b620597` docs(preisanfrage): Tracker-Update fuer Etappe 7 (komplett)
- [x] Push zu `feature/en1090-echeck` (alle 4 Commits auf `origin`)

### ✅ Etappe 8: EmailCenter-Badge
**Commit-Ziel:** `feat(email-center): Badge und Quick-Action für Preisanfragen-Antworten`

- [x] Badge in `EmailCenter.tsx` für zugeordnete Preisanfrage-Mails (Listeneintrag + Detail-Banner, rose-Palette + `Scale`-Icon)
- [x] Quick-Action "Preise eintragen" (öffnet `PreiseEintragenModal` mit `preisanfrageId`/`palId` aus `preisanfrageLieferantRef`)
- [x] Zusätzlicher "Öffnen"-Button im Detail-Banner → springt zu `/einkauf/preisanfragen?open={id}` (Deep-Link-Param)
- [x] 2 neue Vitest-Cases (`EmailCenter.test.tsx`) — Badge-Render + Modal-Öffnen via Quick-Action
- [x] `npm run lint` grün (nur 1 pre-existing Warnung), `npm run build` grün, Vitest **18/18** grün
- [x] Commit erstellen

**Architektur-Notizen:**
- `preisanfrageLieferantRef` wird vom Backend bereits seit Etappe 5 in `UnifiedEmailDto` geliefert — **kein Backend-Change nötig**.
- Kein zusätzlicher Fetch: alles, was der Badge/das Banner braucht (Nummer + Lieferantenname), kommt im bestehenden Inbox-Listing mit.
- `PreiseEintragenModal` ruft selbst `/api/preisanfragen/{id}/vergleich` — erlaubt Mehrfach-Öffnen ohne State-Leck im EmailCenter.

### ✅ Etappe 9: Pre-Merge-Review (ohne PR)
**User-Entscheidung (2026-04-20):** Kein eigener PR — Preisanfrage-Feature bleibt auf
`feature/en1090-echeck` und wird **zusammen mit EN-1090** in einem Rutsch nach `main`
gemergt. Grund: beide Features teilen sich bereits den Branch, getrennte PRs wären
nur Merge-Churn.

Pre-Merge-Checks gemäß `/pre-merge`-Skill:
- [x] **Secrets:** `git diff origin/main...HEAD -- "*.properties"` zeigt nur strukturelle
      Properties-Änderungen (Logging-Level, Feature-Flags, `preisanfrage.reply-to-domain=`
      als leerer Default). Kein `application-local.properties` im Diff. Grep nach
      `password|apiKey|Bearer|sk-|AIza` findet nur `application-local.properties`
      (ist gitignored, nicht committed).
- [x] **Frontend:** `npm run lint` (1 pre-existing Warnung in
      `ArtikelVorschlaegeModal.tsx`), `npm run build` grün, Vitest **178/178 grün**
      (inkl. 9 Modal-Tests aus Etappe 7 + 2 EmailCenter-Tests aus Etappe 8).
- [x] **Backend Preisanfrage-Scope:** Isoliert (172/172) grün —
      `PreisanfrageService`, `PreisanfrageZuordnungService`, `PreisanfrageController`,
      `PreisanfrageAngebotsExtraktionService`, `TokenGenerator`,
      `ArtikelMatchingAgentService`, `ArtikelMatchingToolService`,
      `BestellungPdfService`, `FirmeninformationService`,
      `EmailAutoAssignmentService`.
- [x] **Backend Gesamtsuite:** 1268 Tests, 0 Failures, 0 Errors (Stand 2026-04-21,
      ./mvnw.cmd test 01:36 min). Die zuvor gemeldete Context-Contamination wurde
      durch einen der Refactor-Commits `ac11aa8`/`008b390` beseitigt — Gesamt-Suite
      ist wieder grün, Merge-Blocker für EN-1090 entfällt.
- [x] **Code-Qualität:** Keine TODOs, `System.out.println`, `console.log` oder
      auskommentierten Code in den Preisanfrage-Dateien (grep im Feature-Scope
      leer).
- [x] **DSGVO:** Tests nutzen ausschließlich Dummy-Daten (`Max Mustermann`,
      `test@example.com`, Musterstraße — verifiziert in Etappe 2/4/5/7).
- [ ] **Manueller End-to-End-Test** (siehe Original-Plan Abschnitt 8) — bleibt
      dem User vor dem Haupt-Merge, nicht in Claude-Scope.
- [x] Commit erstellen — Tracker-Update mit Etappe 9 abgehakt.

**Nächste Schritte** (für den nächsten Agent / den User):
1. EN-1090-Feature parallel abschließen (andere Etappen auf diesem Branch).
2. Context-Contamination in der Gesamtsuite lokalisieren (siehe `[!]` oben).
3. Dann Merge `feature/en1090-echeck` → `main` (User-Entscheidung: ohne PR).

---

## 🔮 Folge-Features (bewusst nicht in diesem Feature, Plan für später)

User hat während der Planung zwei **separate** Features erwähnt:

### Feature B: Gleitender Durchschnittspreis pro Artikel
- Neue Spalten `artikel.durchschnittspreis_netto`, `artikel.durchschnittspreis_menge`
- Update-Trigger: wenn `ArtikelInProjekt.bestellt=true` + `preisProStueck` gesetzt → Durchschnitt neu berechnen
- Wird in Kalkulation/Angebot genutzt statt tagesaktueller Lieferantenpreis
- **Eigener Plan, eigener Branch** — nicht in Preisanfrage mischen

### Feature C: Bedarf-Checkliste + "aus Werkstatt übernehmen"-Workflow
User-Zitat: *„Bedarf-Modul soll dienen alles aus dem Kopf abzuschreiben und dann als Liste ausdruckbar zu machen. Mitarbeiter läuft durch Werkstatt und hakt ab was da ist. Das soll dann in ProjektEditor übernommen werden als Materialkosten mit gleitendem Durchschnittspreis und der Rest soll bestellt werden."*

- PDF der offenen Bedarfspositionen (druckbare Checkliste)
- UI-Button "aus Werkstatt verfügbar" pro Position → Position wird geschlossen, Preis aus Artikel-Durchschnittspreis übernommen, im Projekt als Materialkosten eingetragen
- Setzt Feature B voraus
- **Eigener Plan, eigener Branch** — nicht in Preisanfrage mischen

### Nicht-Features in diesem Scope (aus Original-Plan)
- KI-Preis-Extraktion aus Angebots-PDFs (später, nachdem manuelle Erfassung bewährt)
- Automatische Erinnerungsmails bei verstrichener Frist
- Verhandlungs-Runden / Preis-Historie / PDF-Signierung
- Rename `ArtikelInProjekt` → `Bestellposition` (user-seitig entschieden, "separat")

---

## 🐛 Bekannte Issues / Tech-Debt


- [ ] `EmailService` ist kein Spring-Bean, wird in Controllern `new`-instanziiert. Refactor zu `@Service` wäre sinnvoll, aber **separates Thema**.

---

## 📝 Session-Log (nur Ereignisse die für den nächsten Agent wichtig sind)

| Datum | Agent | Was passiert |
|---|---|---|
| 2026-04-20 | Opus 4.7 | Erkundung (Etappe 0) abgeschlossen, alle Entscheidungen geklärt, V227 SQL geschrieben, Security-Fix vorab (Commit `7072ffb`). Stopp nach Diskussion über Bedarf/Bestellung-Trennung: bleibt für diesen Scope wie ist, Folge-Features B+C separat. |
| 2026-04-20 | Opus 4.7 | Pläne ins Repo verschoben (Commit `3ef5f98`). Etappe 1 fertig: 4 Entities + 2 Enums + 4 Repositories angelegt, `./mvnw.cmd compile` grün. Commit folgt. |
| 2026-04-20 | Opus 4.7 | Etappe 2 fertig (Commit `0e52f4a`): TokenGenerator + PreisanfrageService (6 Methoden) + 4 DTOs + PreisanfragePdfGenerator-Strategy-Interface + 23 neue Tests. Komplette Suite grün: 1176 Tests, 0 Failures. Architektur-Entscheidungen: `Optional<PreisanfragePdfGenerator>` entkoppelt von Etappe 3, `EmailServiceFactory` für Test-Injection, explizites `@Autowired` bei Dual-Constructor. |
| 2026-04-20 | Opus 4.7 | Etappe 3a fertig (Commit `c4e14c3`): Firmenlogo-Upload mit 3 Endpoints (POST/GET/DELETE `/api/firma/logo`), MIME-Whitelist (PNG/JPEG/WebP), 2-MB-Limit, Pfad-Traversal-Schutz (serverseitiger Zielname `logo.<ext>`, `startsWith`-Check), FirmeninformationService + FirmaController erweitert. BestellungPdfService nutzt jetzt `loadLogoImage()` statt Hard-Link (Option B: kein Software-Fallback). FirmaEditor.tsx: Upload/Vorschau/Delete mit Cache-Busting via `logoVersion`. 18 neue Tests (7 Controller + 11 Service), volle Suite grün: **1194 Tests, 0 Failures**. TypeScript-Check grün (Exit 0). |
| 2026-04-20 | Opus 4.7 | Etappe 3b fertig (Commit `d0d1590`): `BestellungPdfService implements PreisanfragePdfGenerator`, neue Methode `generatePdfForPreisanfrage(palId)` mit Firmenlogo, roter Kopfzeile "PREISANFRAGE {nummer}", Token-Box, Hinweis "Bitte Code angeben", Rückmeldefrist (deutsches Datumsformat) und Positionen-Tabelle mit leerer Spalte "Ihr Preis €/Einheit". Bewusst ohne EN-1090-Infobox/Zeugnis-Block. Konstruktor um `PreisanfrageLieferantRepository` + `PreisanfragePositionRepository` erweitert, `@AllArgsConstructor` durch expliziten Konstruktor ersetzt. Tests auf Factory-Helper `newService()` umgestellt, neuer Test `generatePdfForPreisanfrage_enthaeltTokenUndNummer` assertet Nummer + Token im PDF. **1195 Tests, 0 Failures**. |
| 2026-04-20 | Opus 4.7 | Etappe 4 fertig (Commit `18c53d6`): neuer `PreisanfrageZuordnungService` mit `tryMatch(Email)` — Primary-Match über `parentEmail.messageId`, Fallback Token-Regex `PA-\d{4}-\d{3}-[A-Z2-9]{5}` im Betreff. Bei Treffer: `antwortEmail` setzen, Status `BEANTWORTET`, `antwortErhaltenAm = now()`, save. `EmailAutoAssignmentService.tryAutoAssign` ruft `tryAssignToPreisanfrage` als erste Regel vor `tryAssignToLieferant` — bei Match zusätzlich `email.assignToLieferant(pal.lieferant)` (Mail bleibt im Lieferanten-Postfach sichtbar). 6 neue Tests im `PreisanfrageZuordnungServiceTest` (inkl. Security-Case für SQL/HTML-Payload-Isolation im Betreff) + 1 neuer Happy-Path im `EmailAutoAssignmentServiceTest` (Vorrang vor Domain-Match). Konstruktor des AutoAssignmentService um `PreisanfrageZuordnungService` erweitert. **1204 Tests, 0 Failures**. |
| 2026-04-20 | Opus 4.7 | Etappe 5 fertig (Commit `12fbeae`): REST-Schicht komplett — `PreisanfrageController` (10 Endpoints) mit `@Valid`-Input-Validierung, DTO-only-Responses (`PreisanfrageResponseDto`, `PreisanfragePositionDto`, `PreisanfrageLieferantDto`), expliziter `PreisanfrageMapper` im `ProjektMapper`-Stil (kein MapStruct). Service um `findeById`/`listeAlle(filterStatus)`/`abbrechen` erweitert. `UnifiedEmailDto` zeigt jetzt `preisanfrageLieferantRef` → EmailCenter kann Badge "Preisanfrage PA-YYYY-NNN" + Quick-Action "Preise eintragen" rendern (Etappe 8). Neuer Repo-Lookup `PreisanfrageLieferantRepository.findByAntwortEmail_Id`. Fehler-Mapping: `IllegalArgumentException`→404, `IllegalStateException`→409, Bean-Validation→400, `X-Error-Reason`-Header. `parseStatus` liefert bei unbekanntem Enum-Wert `null` (kein 500 bei SQLi-Probe). 27 Controller-Tests + 7 neue Service-Tests (`findeById`/`listeAlle`/`abbrechen` mit Happy-Path + Fehlerfällen). **1238 Tests, 0 Failures**. **Kein `@PreAuthorize` eingeführt** — Projekt nutzt durchgängig globale `SecurityConfig`. |
| 2026-04-20 | Opus 4.7 | Etappe 4b fertig (Commit `8b5bb05`): User wollte Lieferanten auch dann zuordnen können, wenn sie nicht auf "Antworten" klicken sondern eine neue Mail schreiben. Lösung: 4-stufige Fallback-Kette in `PreisanfrageZuordnungService` (In-Reply-To → To-Adresse `TOKEN@domain` → Token-Regex im Betreff → PA-Nummer + Absender-E-Mail-Abgleich). Neuer Config-Key `preisanfrage.reply-to-domain=` in `application.properties` (leer = deaktiviert), in `application-local.properties` überschreibbar. `EmailService` bekommt neuen Overload `sendEmailAndReturnMessageId(..., replyTo, ...)`; alter 7-Parameter-Overload bleibt rückwärtskompatibel. Mail-Body + PDF-Hinweisbox bitten jetzt explizit um E-Mail-Antwort mit PDF-Anhang (Umlaute gemäß CLAUDE.md). User-Entscheidung: Eigene Domain `bauschlosserei-kuhn.de` kommt langfristig, bis dahin reichen Fallbacks 1/3/4. 4 neue Tests (Fallback 2 mit/ohne Domain, Fallback 4 mit/ohne Absender-Match), 3 `PreisanfrageServiceTest`-Stubs auf neuen 8-Parameter-Overload umgestellt. **1242 Tests, 0 Failures** (+4). `PreisanfrageZuordnungService` wechselt von `@RequiredArgsConstructor` auf expliziten Konstruktor wegen `@Value`-Injection. |
| 2026-04-20 | Opus 4.7 | Nach Etappe 6: Strategische Entscheidungen fuer Etappe 7+ (KI-Preisvergleich) festgehalten. **PDF-Direkt an Gemini** ist bereits im `GeminiDokumentAnalyseService` so implementiert (`inline_data` + `application/pdf` an 8 Aufrufstellen) — kein Code-Change noetig, nur Policy im Plan zementiert. **Trigger:** Button "Angebote vergleichen" + Auto bei Status `VOLLSTAENDIG`. **Artikelstamm-Update** nutzt bestehenden `ArtikelMatchingAgentService` (DRY), der Dienstleistungen filtert + Fallback-Suche kann. Drei **User-ergaenzte Regeln** fuer Etappe 7: (A) rekursiver Kategorie-Pfad + `isLeaf`-Flag in `list_kategorien`, (B) Leaf-Only-Validierung in `propose_new_artikel`, (C) neues Tool `create_kategorie(beschreibung, parentKategorieId)` mit Duplikat-Check. Offene Frage: direktes Anlegen vs. `KategorieVorschlag`. Siehe Plan-Abschnitt 12. |
| 2026-04-20 | Opus 4.7 (1M) | Etappe 7 fertig: KI-Preisvergleich + Vergleichs-Matrix + Preiserfassung. **User-Entscheidung:** `create_kategorie` legt direkt in DB an (Option a, keine Vorschlaegen-UI). **Modell-Umstellung:** Matching-Agent nutzt jetzt `gemini-3.1-pro-preview` (Default in `application.properties` + `@Value`-Default). Schema-Fix: Enum-Spalten in `Preisanfrage` + `PreisanfrageLieferant` bekommen `columnDefinition="VARCHAR(40)"`, weil Hibernate 6 sonst gegen MySQL-ENUM validiert und den Start scheitert. **Tool-Service:** `listKategorien()` liefert jetzt `id | parentId | isLeaf | pfad` (rekursiv, In-Memory-Index, zyklussicher), `proposeNewArtikel` Leaf-Check (non-leaf existierend → Fehler, unbekannt → silent ignore), neues Tool `create_kategorie` mit Duplikat-Check + Konfidenz-Schwelle 0.9 fuer Hauptkategorien + `ToolSideEffect.KategorieAngelegt`. **Extraktions-Service:** neuer `PreisanfrageAngebotsExtraktionService` (~420 Zeilen) — pro PAL PDF direkt an Gemini (inline_data, kein Text-Preprocessing), Gemini bekommt Positionsliste mit exakter `positionId` → deterministische Zuordnung ohne Fuzzy-Match. Pro gematchter Material-Position wird zusaetzlich `ArtikelMatchingAgentService.matcheOderSchlageAn(...)` getriggert (DRY, lernt Preise/externe Nr. im Stamm). Zusatzpositionen (Zuschnitt/Fracht/...) werden geloggt aber NICHT im Stamm gelernt. Idempotent: bestehende Angebote werden nicht ueberschrieben. **Hook:** Auto-Trigger in `PreisanfrageService.aktualisiereStatus(...)` beim Uebergang auf `VOLLSTAENDIG` (via `@Autowired(required=false)` Setter-Injection, keine Zirkel-Dependency). **REST:** `POST /api/preisanfragen/{id}/angebote/extrahieren` → 404/409/OK mit `ExtraktionsErgebnis`-Record. **Frontend:** `PreisanfrageVergleichModal` mit Matrix (Positionen Zeilen / Lieferanten Spalten, guenstigster `bg-rose-50 font-bold` + Trophy-Icon, sticky Position-Spalte, Summenzeile, Action-Zeile mit „Preise eintragen" + „Beauftragen", Header-Button „KI-Preise auslesen"). `PreiseEintragenModal` mit Sparkle-Icon + „KI-Vorschlag, bitte pruefen"-Badge. `PreisanfragenPage` bindet beide Modals statt Toast-Platzhalter ein. **Tests:** Backend 1264/1264 (+22: 9 Tool-Service, 11 Extraktions-Service, 3 Controller-Endpoint). Frontend 9/9 neu (5 Vergleich + 4 PreiseEintragen). lint + build grün. |
| 2026-04-20 | Opus 4.7 | Etappe 9 (Pre-Merge) fertig **ohne PR**. User-Entscheidung: Preisanfrage-Feature wird zusammen mit EN-1090 auf `feature/en1090-echeck` gemergt, kein separater PR. Pre-Merge-Checkliste: Secrets 0, Frontend lint/build/178 Tests grün, Preisanfrage-Backend-Tests 172/172 grün (isoliert), keine TODOs im Feature-Code. **Bekanntes Problem (nicht Feature-verursacht):** Gesamt-Backend-Suite hat 144 Context-Contamination-Errors — `FormularTemplateControllerTest` einzeln grün, aber im Gesamt-Run wird der Spring-Context verseucht und alle nachfolgenden Tests scheitern kaskadierend. Das blockiert den späteren EN-1090-Merge nach `main` und muss separat untersucht werden (wahrscheinlich `@DirtiesContext` fehlt an einer seit Etappe 7 hinzugefügten Test-Klasse). | 
| 2026-04-20 | Opus 4.7 | Etappe 8 fertig: EmailCenter-Badge + Quick-Action fuer Preisanfrage-Antworten. `EmailCenter.tsx` erweitert um (a) Detail-Banner mit `Scale`-Icon, Nummer + Lieferantenname, "Öffnen"-Link zur Preisanfragen-Seite (`?open={id}`), und "Preise eintragen"-Button der `PreiseEintragenModal` oeffnet; (b) kompaktes Badge in der Listenansicht neben dem Zuordnung-Typ. `EmailItem` um `preisanfrageLieferantRef`-Feld erweitert (Backend liefert das seit Etappe 5). 2 neue Vitest-Cases (Badge-Render + Modal-Öffnen). **Kein Backend-Change** — `UnifiedEmailDto.preisanfrageLieferantRef` war bereits vorhanden. lint + build grün, Vitest 18/18 grün. |
| 2026-04-20 | Opus 4.7 | Etappe 6 fertig: Frontend-Seite `/einkauf/preisanfragen` + `AngeboteEinholenModal`. Neue Dateien: `PreisanfragenPage.tsx` (Status-Tabs Alle/Offen/Teilweise/Vollständig/Vergeben/Abgebrochen, Card-Liste pro Preisanfrage mit Lieferanten-Pills + PDF-Download, Abbrechen-Action, Vergleichs-Button als Platzhalter-Toast bis Etappe 7), `AngeboteEinholenModal.tsx` (Positionen via Vorauswahl ODER `/api/bestellungen/offen`-Fallback, Multi-Lieferant-Liste mit Suche, `DatePicker` Pflichtfeld, sequenzieller `POST /api/preisanfragen` → `POST /versenden` mit Toast + Navigation), `AngeboteEinholenModal.test.tsx` (7 Vitest-Tests inkl. Reihenfolge der beiden POSTs). Eingegriffen: `App.tsx` (neue Route), `RibbonNav.tsx` (neuer Sidebar-Eintrag "Preisanfragen" mit `Scale`-Icon unter Einkauf hinter "Bedarf"), `BestellungEditor.tsx` (neuer Button "Angebote einholen" neben "E-Mail" pro Bedarf-Gruppe, `toBedarfPosition`-Mapper für DRY). Build-Fix unterwegs: `npm install` weil `@xyflow/react` + `@dagrejs/dagre` aus `package.json` nicht im `node_modules` lagen (pre-existing, nicht durch dieses Feature verursacht — ohne Install schlägt der main-Branch genauso fehl). **Frontend:** lint grün, `npm run build` grün, Vitest `AngeboteEinholenModal` 7/7 grün. **Backend:** unveraendert, 1242 Tests weiterhin grün. |

