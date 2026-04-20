# Fortschritts-Tracker: Preisanfrage an mehrere Lieferanten

**Zweck dieses Dokuments:** Laufender Status des Features. Jede Session/jeder Agent liest
hier den aktuellen Stand und hakt erledigte Punkte ab (bzw. ergänzt neue Aufgaben).

**Originaler Plan:** [preisanfrage-mehrere-lieferanten.md](preisanfrage-mehrere-lieferanten.md)
**Branch:** `feature/en1090-echeck` (wird vor Merge auf `feature/preisanfrage-multi-lieferant` umgebogen)
**Letzter Commit dieses Features:** `7072ffb` (Security-Fix, siehe "Parallel erledigt" unten)

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

### ⏳ Etappe 3: PDF-Variante + Firmenlogo-Upload (FirmaEditor)
**Commit-Ziel:** `feat(firma): Firmenlogo-Upload im FirmaEditor` + `feat(preisanfrage): PDF-Variante`

**Firmenlogo-Upload (eigener Sub-Commit vor der PDF-Variante!):**
- [ ] Endpoint `POST /api/firma/logo` (multipart) in `FirmaController`
- [ ] Endpoint `GET /api/firma/logo` (liefert Bild-Binary)
- [ ] Endpoint `DELETE /api/firma/logo`
- [ ] Upload-Verzeichnis `uploads/firma/logo/` (gitignored prüfen!)
- [ ] Service-Methode `FirmeninformationService.loadLogoImage()` → liefert iText-Image oder `null`
- [ ] `BestellungPdfService` Zeile 59 + 171: ersetze Hart-Link durch `loadLogoImage()`
- [ ] FirmaEditor.tsx: Upload-Feld mit Vorschau + Löschen-Button

**PDF-Variante Preisanfrage:**
- [ ] `BestellungPdfService.generatePdfForPreisanfrage(palId)` neue Methode
  - [ ] Rote Kopfzeile "PREISANFRAGE {nummer}" + Token-Box
  - [ ] Hinweis-Box "Bitte Code angeben"
  - [ ] Rückmeldefrist-Feld
  - [ ] Positionen-Tabelle mit leerer Spalte "Ihr Preis €/Einheit"
  - [ ] KEINE EN-1090-Infobox, KEINE Zeugnis-Blöcke
- [ ] Test `BestellungPdfServiceTest.generatePdfForPreisanfrage_enthaeltTokenUndNummer`
- [ ] Commit erstellen (zwei Commits, Logo und PDF getrennt)

### ⏳ Etappe 4: Auto-Zuordnung via parentEmail
**Commit-Ziel:** `feat(email): Preisanfrage-Auto-Zuordnung via parentEmail/Token`

- [ ] `service/PreisanfrageZuordnungService.java`
  - [ ] `tryMatch(Email incoming)` via `parentEmail.messageId` ODER Token-Regex im Betreff
  - [ ] Bei Match: `PreisanfrageLieferant.antwortEmail = incoming`, Status `BEANTWORTET`
- [ ] `EmailAutoAssignmentService.tryAutoAssign`: Regel `tryAssignToPreisanfrage` als ERSTE Regel einbauen
- [ ] Test `PreisanfrageZuordnungServiceTest` (3 Cases: inReplyTo, Token-Fallback, nicht gefunden)
- [ ] Commit erstellen

### ⏳ Etappe 5: Controller + DTOs + Mapper
**Commit-Ziel:** `feat(preisanfrage): Controller + DTOs + Mapper`

- [ ] DTOs unter `dto/Preisanfrage/`:
  - [ ] `PreisanfrageErstellenDto`
  - [ ] `PreisanfragePositionDto`
  - [ ] `PreisanfrageResponseDto`
  - [ ] `PreisanfrageVergleichDto`
  - [ ] `PreisanfrageAngebotEintragenDto`
- [ ] `mapper/PreisanfrageMapper.java`
- [ ] `controller/PreisanfrageController.java` (10 Endpoints, siehe Original-Plan 4.9)
- [ ] `UnifiedEmailDto` erweitern um `preisanfrageLieferantRef`
- [ ] `@WebMvcTest PreisanfrageControllerTest` (Happy-Paths + 404)
- [ ] Commit erstellen

### ⏳ Etappe 6: Frontend Seite + AngeboteEinholenModal
**Commit-Ziel:** `feat(preisanfrage): Frontend-Seite + AngeboteEinholenModal`

- [ ] `react-pc-frontend/src/pages/PreisanfragenPage.tsx`
- [ ] Route `/einkauf/preisanfragen` in `App.tsx`
- [ ] Navigation-Eintrag (vermutlich in Sidebar)
- [ ] `react-pc-frontend/src/components/AngeboteEinholenModal.tsx`
- [ ] Button "Angebote einholen" in `BestellungEditor.tsx` (Zeile ~859, neben "E-Mail")
- [ ] Vitest `AngeboteEinholenModal.test.tsx`
- [ ] `npm run build` grün
- [ ] Commit erstellen

### ⏳ Etappe 7: Vergleichs-Matrix + Preiserfassung
**Commit-Ziel:** `feat(preisanfrage): Vergleichs-Matrix + manuelle Preiserfassung`

- [ ] `components/PreisanfrageVergleichModal.tsx` (Matrix-Tabelle)
- [ ] `components/PreiseEintragenModal.tsx`
- [ ] Günstigster Preis pro Zeile markiert (`bg-rose-50 font-bold`)
- [ ] Button "Lieferant X beauftragen" → Confirm → `/vergeben/{palId}`
- [ ] Vitest für beide Modals
- [ ] Commit erstellen

### ⏳ Etappe 8: EmailCenter-Badge
**Commit-Ziel:** `feat(email-center): Badge und Quick-Action für Preisanfragen-Antworten`

- [ ] Badge in `EmailCenter.tsx` für zugeordnete Preisanfrage-Mails
- [ ] Quick-Action "Preise eintragen" (öffnet `PreiseEintragenModal`)
- [ ] Commit erstellen

### ⏳ Etappe 9: Pre-Merge + Review
- [ ] `/pre-merge` Skill ausführen
- [ ] Manueller End-to-End-Test (siehe Original-Plan Abschnitt 8)
- [ ] Security-Smoke (XSS/SQLi)
- [ ] Commits optional zu PR aufräumen
- [ ] **Nicht direkt nach main mergen** — PR öffnen

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

- [ ] Build-Artefakte im Git (`src/main/resources/static/assets/*.js`) enthalten E-Mail-Adressen. Sollten aus Git entfernt werden, aber **nicht im Rahmen dieses Features**.
- [ ] `EmailService` ist kein Spring-Bean, wird in Controllern `new`-instanziiert. Refactor zu `@Service` wäre sinnvoll, aber **separates Thema**.

---

## 📝 Session-Log (nur Ereignisse die für den nächsten Agent wichtig sind)

| Datum | Agent | Was passiert |
|---|---|---|
| 2026-04-20 | Opus 4.7 | Erkundung (Etappe 0) abgeschlossen, alle Entscheidungen geklärt, V227 SQL geschrieben, Security-Fix vorab (Commit `7072ffb`). Stopp nach Diskussion über Bedarf/Bestellung-Trennung: bleibt für diesen Scope wie ist, Folge-Features B+C separat. |
| 2026-04-20 | Opus 4.7 | Pläne ins Repo verschoben (Commit `3ef5f98`). Etappe 1 fertig: 4 Entities + 2 Enums + 4 Repositories angelegt, `./mvnw.cmd compile` grün. Commit folgt. |
| 2026-04-20 | Opus 4.7 | Etappe 2 fertig (Commit `0e52f4a`): TokenGenerator + PreisanfrageService (6 Methoden) + 4 DTOs + PreisanfragePdfGenerator-Strategy-Interface + 23 neue Tests. Komplette Suite grün: 1176 Tests, 0 Failures. Architektur-Entscheidungen: `Optional<PreisanfragePdfGenerator>` entkoppelt von Etappe 3, `EmailServiceFactory` für Test-Injection, explizites `@Autowired` bei Dual-Constructor. |

