# Plan: Preisanfrage an mehrere Lieferanten + automatische Angebots-Zuordnung

**Zielgruppe dieses Plans:** Ein neuer Claude-Code-Agent, der den Plan von vorne bis
hinten umsetzen soll. Der Plan ist bewusst selbsterklärend — keine Chat-Historie nötig.

**Branch-Vorschlag:** `feature/preisanfrage-multi-lieferant`
**Projekt:** ERP-System-fuer-Handwerksbetriebe
**Stack:** Spring Boot 3 + MySQL + Flyway, React 18 + Vite + TailwindCSS (Rose/Slate-Palette)

---

## 1. Geschäftsziel

Der Handwerker will für gleiche Materialpositionen (z. B. Stahlprofile) von
**mehreren Lieferanten parallel** Angebote einholen und **automatisiert sehen,
wer am günstigsten ist**. Bisher kann man nur **eine** Bestellung an **einen**
Lieferanten schicken ([BestellungEditor.tsx:861](react-pc-frontend/src/pages/BestellungEditor.tsx#L861)).

**End-to-End-Flow:**
1. User markiert im Bedarf Positionen + wählt N Lieferanten → klickt „Angebote einholen".
2. System erzeugt eine `Preisanfrage` mit eindeutiger Nummer `PA-YYYY-NNN` und
   pro Lieferant einen einmaligen Token `PA-YYYY-NNN-XXXXX`.
3. System versendet pro Lieferant eine E-Mail mit eigenem PDF (Token im Betreff,
   Body und PDF-Kopf) über die bestehende SMTP-Konfiguration.
4. Antworten der Lieferanten kommen ins bestehende EmailCenter. Der bestehende
   `EmailImportService` speichert `messageId`. Eine **neue Auto-Zuordnungsregel**
   verknüpft eingehende Mails via `In-Reply-To`-Header bzw. Token-Regex mit
   dem passenden `PreisanfrageLieferant`.
5. Auf einer neuen Seite **Einkauf → Preisanfragen** sieht der User pro Anfrage
   alle Lieferanten, deren Status (versendet / beantwortet), und pro Position
   eine Angebotstabelle. Der günstigste Preis ist farbig markiert.
6. Preise werden **manuell** in Version 1 eingetragen (KI-Extraktion kommt in
   Version 2, separat).

**Was NICHT in diesem Plan ist (explizit ausgeschlossen):**
- KI-Preis-Extraktion aus Angebots-PDFs (Folge-Feature, eigener Plan).
- Automatische Umwandlung „günstigstes Angebot → echte Bestellung". Stattdessen
  Button „Diesen Lieferanten bestellen" der die bestehende Bestell-Logik ruft.
- Plus-Adressing oder neuer Mail-Provider. Bestehende T-Online-SMTP +
  vorhandenes EmailCenter reichen völlig aus — `In-Reply-To`-Header + Token im
  Betreff liefern ~95 % Trefferquote.

---

## 2. Architektur-Entscheidungen (bereits getroffen, nicht erneut diskutieren)

| Entscheidung | Wahl | Begründung |
|---|---|---|
| Namensraum | **`Preisanfrage`** (nicht `Anfrage`!) | `Anfrage` existiert bereits als **Kunden-Anfrage** ([domain/Anfrage.java](src/main/java/org/example/kalkulationsprogramm/domain/Anfrage.java)). Kollision vermeiden. |
| Token-Format | `PA-YYYY-NNN-XXXXX` (5 alphanumerisch, ohne 0/O/1/I) | 5 Zeichen = ~60 Mio Kombinationen → kollisionsfrei. Verwechslungsfreie Zeichen wegen handschriftlicher Übernahme. |
| Zuordnungs-Strategie | **Primär `In-Reply-To`**, Fallback Token-Regex im Betreff, dritter Fallback Absender-Domain + KI | Outlook/alle Clients setzen `In-Reply-To` automatisch → 100 % sicher, wenn vorhanden. |
| Preis-Erfassung V1 | Manuell per Eingabefeld | KI-Parsing zu fehleranfällig für Start (Staffelpreise, MwSt., etc.). Erst Vertrauen aufbauen. |
| Auto-Zuordnungs-Bestätigung | User bestätigt im EmailCenter mit einem Klick | Kein Silent-Magic in V1 — User muss Kontrolle haben. |
| UI-Platzierung | Neue Seite **Einkauf → Preisanfragen** + Button **„Angebote einholen"** im `BestellungEditor` | Preisanfragen haben eigenen Lebenszyklus (offen/vergeben), brauchen eigene Liste. |
| PDF-Variante | Neue Methode `generatePdfForPreisanfrage(preisanfrageLieferantId)` in `BestellungPdfService` | DRY zu bestehenden `generatePdfFor*` — gleicher Stil, andere Kopfzeile + Token-Box, **keine** Auftragsnr.-Infobox. |

---

## 3. Datenbank-Schema (Flyway-Migration)

**Neue Datei:** [src/main/resources/db/migration/V227__preisanfrage.sql](src/main/resources/db/migration/V227__preisanfrage.sql)

> ⚠️ Versionsnummer prüfen: V226 ist aktuell die letzte. Falls in der Zwischenzeit
> eine weitere hinzukam, **nächste freie Nummer** verwenden.

```sql
-- ═══════════════════════════════════════════════════════════════
-- Preisanfrage: Einkaufs-Anfrage an mehrere Lieferanten
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS preisanfrage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nummer VARCHAR(20) NOT NULL UNIQUE COMMENT 'Format PA-YYYY-NNN, z. B. PA-2026-041',
    bauvorhaben VARCHAR(255),
    projekt_id BIGINT,
    erstellt_am DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    antwort_frist DATE COMMENT 'Rückmeldefrist für Lieferanten (Soft-Deadline)',
    status VARCHAR(40) NOT NULL DEFAULT 'OFFEN'
        COMMENT 'OFFEN | TEILWEISE_BEANTWORTET | VOLLSTAENDIG | VERGEBEN | ABGEBROCHEN',
    notiz TEXT,
    vergeben_an_preisanfrage_lieferant_id BIGINT NULL
        COMMENT 'Nach Entscheidung: welcher Lieferant bekam den Auftrag',
    CONSTRAINT fk_preisanfrage_projekt FOREIGN KEY (projekt_id) REFERENCES projekt(id)
        ON DELETE SET NULL,
    INDEX idx_preisanfrage_status (status),
    INDEX idx_preisanfrage_projekt (projekt_id),
    INDEX idx_preisanfrage_erstellt (erstellt_am)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_german2_ci;

-- Pro Lieferant ein eindeutiger Token + eigene Versand-Historie
CREATE TABLE IF NOT EXISTS preisanfrage_lieferant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    preisanfrage_id BIGINT NOT NULL,
    lieferant_id BIGINT NOT NULL,
    token VARCHAR(40) NOT NULL UNIQUE COMMENT 'PA-YYYY-NNN-XXXXX, eindeutig',
    versendet_an VARCHAR(255) COMMENT 'Tatsaechliche Empfaenger-E-Mail',
    versendet_am DATETIME(6),
    outgoing_message_id VARCHAR(512) COMMENT 'Message-ID der ausgehenden Mail (für In-Reply-To-Match)',
    antwort_erhalten_am DATETIME(6),
    antwort_email_id BIGINT NULL COMMENT 'FK auf email.id der eingegangenen Antwort',
    status VARCHAR(40) NOT NULL DEFAULT 'VORBEREITET'
        COMMENT 'VORBEREITET | VERSENDET | BEANTWORTET | ABGELEHNT',
    CONSTRAINT fk_pal_preisanfrage FOREIGN KEY (preisanfrage_id) REFERENCES preisanfrage(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pal_lieferant FOREIGN KEY (lieferant_id) REFERENCES lieferanten(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_pal_email FOREIGN KEY (antwort_email_id) REFERENCES email(id)
        ON DELETE SET NULL,
    INDEX idx_pal_token (token),
    INDEX idx_pal_outgoing_mid (outgoing_message_id),
    INDEX idx_pal_status (status),
    UNIQUE KEY uk_pal_preisanfrage_lieferant (preisanfrage_id, lieferant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_german2_ci;

-- Positionen in der Preisanfrage (Kopie der relevanten Bestellungs-Positionen)
CREATE TABLE IF NOT EXISTS preisanfrage_position (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    preisanfrage_id BIGINT NOT NULL,
    bestellung_id BIGINT NULL COMMENT 'Quelle: offene Bedarf-Position. NULL wenn freie Position.',
    artikel_id BIGINT NULL,
    externe_artikelnummer VARCHAR(100),
    produktname VARCHAR(255),
    produkttext TEXT,
    werkstoff_name VARCHAR(100),
    menge DECIMAL(12,3),
    einheit VARCHAR(20),
    kommentar TEXT,
    reihenfolge INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pap_preisanfrage FOREIGN KEY (preisanfrage_id) REFERENCES preisanfrage(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pap_bestellung FOREIGN KEY (bestellung_id) REFERENCES bestellung(id)
        ON DELETE SET NULL,
    INDEX idx_pap_preisanfrage (preisanfrage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_german2_ci;

-- Angebot pro Lieferant pro Position (Preise)
CREATE TABLE IF NOT EXISTS preisanfrage_angebot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    preisanfrage_lieferant_id BIGINT NOT NULL,
    preisanfrage_position_id BIGINT NOT NULL,
    einzelpreis DECIMAL(12,4) COMMENT 'Pro Einheit, netto',
    gesamtpreis DECIMAL(14,2) COMMENT 'Optional: Position gesamt, netto',
    mwst_prozent DECIMAL(5,2),
    lieferzeit_tage INT,
    gueltig_bis DATE,
    bemerkung TEXT,
    erfasst_am DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    erfasst_durch VARCHAR(100) COMMENT 'manuell | ki-extraktion',
    CONSTRAINT fk_paa_pal FOREIGN KEY (preisanfrage_lieferant_id) REFERENCES preisanfrage_lieferant(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_paa_position FOREIGN KEY (preisanfrage_position_id) REFERENCES preisanfrage_position(id)
        ON DELETE CASCADE,
    UNIQUE KEY uk_paa_pal_position (preisanfrage_lieferant_id, preisanfrage_position_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_german2_ci;

-- Foreign Key jetzt wo preisanfrage_lieferant existiert
ALTER TABLE preisanfrage
    ADD CONSTRAINT fk_preisanfrage_vergeben_pal
    FOREIGN KEY (vergeben_an_preisanfrage_lieferant_id)
    REFERENCES preisanfrage_lieferant(id) ON DELETE SET NULL;
```

**Idempotenz:** Alle `CREATE TABLE IF NOT EXISTS`, damit erneute Ausführung möglich
(gemäß Projekt-Flyway-Regel in [docs/agent instructions/docs/BACKEND_ARCH.md](docs/agent%20instructions/docs/BACKEND_ARCH.md)).

**Nummerngenerator für `nummer`:** Service-seitig. Format `PA-{YYYY}-{lfdNr}`,
wobei `lfdNr` pro Jahr hochgezählt wird (`MAX(nummer) WHERE nummer LIKE 'PA-YYYY-%'`).

---

## 4. Backend-Implementierung

### 4.1 Domain-Entities

Alle unter `src/main/java/org/example/kalkulationsprogramm/domain/`:

- **`Preisanfrage.java`** — Felder aus V227 + `@OneToMany` auf Lieferanten + Positionen, Lombok `@Getter/@Setter/@NoArgsConstructor`.
- **`PreisanfrageLieferant.java`** — `@ManyToOne` zu `Preisanfrage` + `Lieferanten`, nullable FK `antwortEmail` (`@ManyToOne Email`).
- **`PreisanfragePosition.java`** — optionaler FK auf `Bestellung`, Felder wie im SQL.
- **`PreisanfrageAngebot.java`** — `@ManyToOne` auf `PreisanfrageLieferant` + `PreisanfragePosition`.
- **`PreisanfrageStatus.java`** — Enum: `OFFEN, TEILWEISE_BEANTWORTET, VOLLSTAENDIG, VERGEBEN, ABGEBROCHEN`.
- **`PreisanfrageLieferantStatus.java`** — Enum: `VORBEREITET, VERSENDET, BEANTWORTET, ABGELEHNT`.

**Wichtig:** Keine Entities direkt in REST-Responses exponieren — immer über DTOs
(Regel aus CLAUDE.md).

### 4.2 Repositories

Alle unter `src/main/java/org/example/kalkulationsprogramm/repository/`:

- **`PreisanfrageRepository extends JpaRepository<Preisanfrage, Long>`**
  - `Optional<Preisanfrage> findByNummer(String nummer);`
  - `@Query("SELECT COALESCE(MAX(CAST(SUBSTRING(p.nummer, 9) AS int)), 0) FROM Preisanfrage p WHERE p.nummer LIKE :prefix%")` → höchste laufende Nr im Jahr
- **`PreisanfrageLieferantRepository extends JpaRepository<PreisanfrageLieferant, Long>`**
  - `Optional<PreisanfrageLieferant> findByToken(String token);`
  - `Optional<PreisanfrageLieferant> findByOutgoingMessageId(String messageId);`
  - `List<PreisanfrageLieferant> findByPreisanfrageIdOrderByLieferantLieferantenname(Long id);`
- **`PreisanfragePositionRepository extends JpaRepository<PreisanfragePosition, Long>`**
  - `List<PreisanfragePosition> findByPreisanfrageIdOrderByReihenfolge(Long id);`
- **`PreisanfrageAngebotRepository extends JpaRepository<PreisanfrageAngebot, Long>`**
  - `List<PreisanfrageAngebot> findByPreisanfrageLieferantId(Long id);`
  - `@Query("SELECT a FROM PreisanfrageAngebot a WHERE a.preisanfragePosition.preisanfrage.id = :id")`
    → alle Angebote einer kompletten Preisanfrage (für Vergleichsmatrix)

### 4.3 DTOs

Unter `src/main/java/org/example/kalkulationsprogramm/dto/Preisanfrage/`:

- **`PreisanfrageErstellenDto`** — `projektId`, `bauvorhaben`, `antwortFrist`, `lieferantIds: List<Long>`, `positionen: List<PreisanfragePositionDto>` (kopiert aus gewähltem Bedarf), `notiz`.
- **`PreisanfragePositionDto`** — die Position-Felder wie in der Tabelle.
- **`PreisanfrageResponseDto`** — vollständige Sicht inkl. Lieferanten-Status und Positionen.
- **`PreisanfrageVergleichDto`** — Matrix-Sicht: pro Position × Lieferant ein Preis (`null` wenn nicht beantwortet) + Markierung `guenstigster: boolean`.
- **`PreisanfrageAngebotEintragenDto`** — fürs manuelle Preis-Eintragen: `preisanfrageLieferantId`, `positionId`, `einzelpreis`, `mwstProzent`, `lieferzeitTage`, `gueltigBis`, `bemerkung`.

### 4.4 Mapper

**`PreisanfrageMapper.java`** — explizite Mapper-Klasse (kein MapStruct — entspricht
bestehendem Stil in [mapper/ProjektMapper.java](src/main/java/org/example/kalkulationsprogramm/mapper/ProjektMapper.java)).

### 4.5 Services

Unter `src/main/java/org/example/kalkulationsprogramm/service/`:

**`PreisanfrageService.java`** (zentraler Service)
- `Preisanfrage erstellen(PreisanfrageErstellenDto dto)` — generiert `nummer` (`PA-YYYY-NNN`),
  legt `Preisanfrage` + Positionen + `PreisanfrageLieferant`-Einträge (pro Lieferant Token via
  `TokenGenerator`) an. Status `OFFEN` / `VORBEREITET`.
- `void versendeAnAlleLieferanten(Long preisanfrageId)` — für jeden `PreisanfrageLieferant`:
  - PDF generieren via `BestellungPdfService.generatePdfForPreisanfrage(pal.id)`
  - E-Mail versenden via bestehendem `EmailService` / `/api/emails/send`-Mechanik,
    `outgoingMessageId` aus der versendeten Mail speichern.
  - Status → `VERSENDET`, `versendetAm = now()`.
- `void versendeAnEinzelnenLieferanten(Long preisanfrageLieferantId)` — für Retry/späteren Versand.
- `PreisanfrageVergleichDto getVergleich(Long preisanfrageId)` — Matrix bauen,
  günstigsten Preis pro Position markieren (kleinster `einzelpreis`).
- `PreisanfrageAngebot eintragen(PreisanfrageAngebotEintragenDto dto)` — manuelles Eintragen,
  aktualisiert `PreisanfrageLieferant.status` auf `BEANTWORTET` wenn alle Positionen
  einen Preis haben, aktualisiert `Preisanfrage.status` entsprechend.
- `void vergebeAuftrag(Long preisanfrageId, Long preisanfrageLieferantId)` —
  setzt `vergebenAnPreisanfrageLieferantId`, Status `VERGEBEN`. Legt dann für
  den Gewinner-Lieferanten **neue echte Bestellung(en)** an, indem pro Position
  ein Eintrag in `bestellung` mit `lieferant_id = gewinner` erzeugt wird (DRY:
  bestehende `BestellungService`-Methoden nutzen). **Die rote EN-1090-Infobox
  kommt dann auf der ECHTEN Bestellung zum Tragen** — Preisanfrage-PDF hat sie bewusst nicht.

**`TokenGenerator.java`** (Util unter `service/` oder neuer `util/TokenGenerator.java`)
- `String generateSuffix(int len)` — `SecureRandom` aus Alphabet **ohne** verwechselbare
  Zeichen (`A-Z` + `2-9`, ohne `0,1,I,O`). Retry bei Kollision in der DB.

**`PreisanfrageZuordnungService.java`**
- Wird vom bestehenden `EmailAutoAssignmentService` aufgerufen. Einzige öffentliche Methode:
- `Optional<PreisanfrageLieferant> tryMatch(Email incoming)` —
  1. `incoming.getInReplyTo()` → `PreisanfrageLieferantRepository.findByOutgoingMessageId()`
     ⚠️ **Das Feld `inReplyTo` existiert aktuell evtl. NICHT in `Email.java`.**
     Falls nicht: siehe Abschnitt 4.7 für Schema-Erweiterung.
  2. Regex `PA-\d{4}-\d{3}-[A-Z2-9]{5}` im Betreff → `findByToken()`
  3. Bei Match: `PreisanfrageLieferant.antwortEmail = incoming`, Status `BEANTWORTET`,
     `antwortErhaltenAm = now()`. **Nicht** automatisch Angebotspreise eintragen —
     User erfasst manuell nach Sichtung des PDFs.

### 4.6 Erweiterung `BestellungPdfService`

**Datei:** [src/main/java/org/example/kalkulationsprogramm/service/BestellungPdfService.java](src/main/java/org/example/kalkulationsprogramm/service/BestellungPdfService.java)

Neue öffentliche Methode:

```java
public Path generatePdfForPreisanfrage(Long preisanfrageLieferantId) { ... }
```

Unterschiede zum bestehenden `generatePdfForLieferant`:
- **Kopfbereich:** groß und rot: `„PREISANFRAGE {nummer}"` + darunter `„Ihr persönlicher Rückmelde-Code: {token}"` mit großer Schrift + Rahmen.
- **Hinweis-Box statt EN-1090-Infobox:** „Bitte geben Sie diesen Code in Ihrer Antwort
  (Betreff oder Body) an, damit wir Ihr Angebot automatisch zuordnen können."
- **Keine Auftragsnummer-Logik** (gibt's ja noch nicht, ist Anfrage nicht Auftrag).
- **Rückmeldefrist** als Feld anzeigen.
- **Positionen-Tabelle** wie bisher, aber mit zusätzlicher leerer Spalte „Ihr Preis €/Einheit"
  (lässt dem Lieferanten Platz, den Preis bei Bedarf handschriftlich einzutragen, falls
  er per Post/Fax antwortet).
- **Keine** Zeugnis-Block-Logik. **Keine** Schnittbild-Anhänge. Kürzer und fokussierter.

DRY-Gebot: **Helper `addCompanyLogo(doc)` extrahieren**, weil das in 3 PDFs gleich ist.
Ebenso `makeCell/makeCutCell` weiter nutzen.

### 4.7 Erweiterung Email-Entity (möglicherweise nötig)

**Prüfe zuerst:** Gibt es in [domain/Email.java](src/main/java/org/example/kalkulationsprogramm/domain/Email.java)
bereits ein Feld `inReplyTo`? Aktuell sucht `grep "inReplyTo"` keine Treffer → **Feld
muss ergänzt werden.**

**Wenn nicht vorhanden:** Eigene kleine Migration **V228__email_in_reply_to.sql**:
```sql
ALTER TABLE email ADD COLUMN IF NOT EXISTS in_reply_to VARCHAR(512) NULL
    COMMENT 'RFC 5322 In-Reply-To-Header, fuer Threading und Preisanfrage-Zuordnung';
ALTER TABLE email ADD INDEX IF NOT EXISTS idx_email_in_reply_to (in_reply_to);
```

Entity-Erweiterung:
```java
@Column(length = 512)
private String inReplyTo;
```

**IMAP-Import-Erweiterung:** In `EmailImportService` beim Mappen der IMAP-Message
den Header `"In-Reply-To"` lesen und ins Feld schreiben. (Der Service ist ~993 Zeilen
groß — zielgenau in der Map-Methode eintragen, kein Refactor nötig.)

### 4.8 Erweiterung `EmailAutoAssignmentService`

**Datei:** [src/main/java/org/example/kalkulationsprogramm/service/EmailAutoAssignmentService.java](src/main/java/org/example/kalkulationsprogramm/service/EmailAutoAssignmentService.java)

Neue private Methode `tryAssignToPreisanfrage(Email email)` als **erste Regel** vor
`tryAssignToLieferant` einbauen:

```java
@Transactional
public boolean tryAutoAssign(Email email) {
    if (email.getZuordnungTyp() != EmailZuordnungTyp.KEINE) return false;
    if (tryAssignToPreisanfrage(email)) return true;   // NEU
    if (tryAssignToLieferant(email)) return true;
    if (tryAssignToKundeEntity(email)) return true;
    return false;
}
```

`tryAssignToPreisanfrage(email)`:
- Ruft `preisanfrageZuordnungService.tryMatch(email)`.
- Bei Treffer: `email.assignToLieferant(pal.getLieferant())` (damit die Mail weiter
  in der Lieferanten-Ansicht landet) UND aktualisiert `PreisanfrageLieferant`
  (siehe 4.5).
- Neuer `EmailZuordnungTyp.PREISANFRAGE_ANTWORT`? **Nein — zu speziell.** Stattdessen
  weiter `LIEFERANT`-Zuordnung belassen, der Link zur Preisanfrage läuft über
  `PreisanfrageLieferant.antwortEmail`.

### 4.9 Controller

**`PreisanfrageController.java`** unter `controller/`:

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/preisanfragen` | Neue Preisanfrage anlegen (`PreisanfrageErstellenDto`) |
| `POST` | `/api/preisanfragen/{id}/versenden` | An alle zugeordneten Lieferanten versenden |
| `POST` | `/api/preisanfragen/lieferant/{palId}/versenden` | Einzelnen versenden (Retry) |
| `GET` | `/api/preisanfragen` | Liste alle, optional Filter `?status=OFFEN` |
| `GET` | `/api/preisanfragen/{id}` | Detail |
| `GET` | `/api/preisanfragen/{id}/vergleich` | Matrix für UI |
| `GET` | `/api/preisanfragen/lieferant/{palId}/pdf` | PDF-Download pro Lieferant |
| `POST` | `/api/preisanfragen/angebote` | Preis manuell eintragen (`PreisanfrageAngebotEintragenDto`) |
| `POST` | `/api/preisanfragen/{id}/vergeben/{palId}` | Lieferant X bekommt Auftrag, echte Bestellung wird erzeugt |
| `DELETE` | `/api/preisanfragen/{id}` | Preisanfrage abbrechen (soft: Status `ABGEBROCHEN`) |

**Security:** Keine neuen Berechtigungen. Gleiche Zugriffsebene wie `BestellungController`
(erfordert angemeldeten Nutzer, `SecurityConfig` lässt `/api/**` mit Session durch).

---

## 5. Frontend-Implementierung

### 5.1 Neue Seite `PreisanfragenPage.tsx`

**Datei:** `react-pc-frontend/src/pages/PreisanfragenPage.tsx`

**Routing:** In [App.tsx](react-pc-frontend/src/App.tsx) Route `/einkauf/preisanfragen` hinzufügen.

**Layout (Page-Header-Pattern einhalten):**
```
[Ribbon]
┌──────────────────────────────────────┐
│ EINKAUF                              │ ← rose-600, tracking-widest
│ PREISANFRAGEN                        │ ← UPPERCASE, bold, 3xl
│ Angebote von Lieferanten einholen … │ ← slate-500
└──────────────────────────────────────┘

[Filter: Status-Tabs] [Button „+ Neue Preisanfrage"]
[Liste von PreisanfrageCards]
```

**`PreisanfrageCard`** pro Preisanfrage:
```
PA-2026-041 · Stahlbau Musterstraße
Rückmeldefrist: 24.04.2026 · Status: TEILWEISE_BEANTWORTET (2/3)

┌ Stahlhandel A ─────────── VERSENDET ─ 15.04.─┐
┌ Stahlhandel B ─────────── BEANTWORTET ✓ ─────┐
┌ Stahlhandel C ─────────── BEANTWORTET ✓ ─────┐

[Vergleich öffnen] [PDF] [Abbrechen]
```

**Vergleichs-Modal (`PreisanfrageVergleichModal`):** Matrix-Tabelle mit Positionen
in den Zeilen und Lieferanten in den Spalten. Günstigster Preis pro Zeile
`bg-rose-50 font-bold`. Summenzeile unten. Button „Lieferant X beauftragen"
pro Spalte → Confirm-Dialog → Aufruf `/vergeben/{palId}`.

**Manuelles Preis-Eintragen** (`PreiseEintragenModal`): Pro Position ein Input
`Euro/Einheit`, optional MwSt/Lieferzeit/gültig bis. Nach Speichern aktualisiert
sich die Matrix.

### 5.2 Erweiterung `BestellungEditor.tsx`

In der Bedarf-Ansicht (Gruppierung „Nach Lieferant" oder „Nach Projekt"):

Neben dem bestehenden `E-Mail`-Button pro Gruppe ([BestellungEditor.tsx:859](react-pc-frontend/src/pages/BestellungEditor.tsx#L859))
einen zweiten Button:

```tsx
<Button size="sm" variant="outline"
        onClick={() => openAngeboteEinholenModal(gruppe)}>
  <Scale className="w-4 h-4 mr-1" /> Angebote einholen
</Button>
```

**Neues Modal `AngeboteEinholenModal.tsx`** (neue Datei in `components/`):
1. Positionen der Gruppe vorausgewählt (Checkboxen).
2. Lieferanten-Mehrfach-Auswahl: Tabelle mit Suche, Checkboxen, bestehendes
   `LieferantSearchModal` als Inspiration aber als inline Liste (Multi-Select).
3. Feld „Rückmeldefrist" (DatePicker-Komponente — **Pflicht!** laut CLAUDE.md
   Frontend-Regeln keinen `<input type="date">`).
4. Feld „Notiz" (optional, erscheint im PDF-Kommentar).
5. Button „Anfrage versenden" → `POST /api/preisanfragen` → sofort
   `POST /api/preisanfragen/{id}/versenden` → Toast „Preisanfrage an N Lieferanten
   versendet" + Navigation zu `/einkauf/preisanfragen`.

### 5.3 Erweiterung EmailCenter

In [EmailCenter.tsx](react-pc-frontend/src/pages/EmailCenter.tsx) pro E-Mail, die
einer `PreisanfrageLieferant` zugeordnet ist, ein kleines Badge:

```
🏷️ Preisanfrage PA-2026-041 · Stahlhandel B
[Preise aus dieser Mail eintragen]
```

Der Button öffnet das `PreiseEintragenModal` aus 5.1 mit vorselektierter
Preisanfrage + Lieferant.

**Erkennung frontend-seitig:** Backend liefert in `UnifiedEmailDto` ein neues optionales
Feld `preisanfrageLieferantRef: { preisanfrageId, preisanfrageNummer, palId } | null`.

### 5.4 Design-Regeln einhalten

- **Farben:** ausschließlich Rose/Slate, **kein indigo/blue** (CLAUDE.md).
- **Umlaute**: echte Umlaute in allen Texten (siehe CLAUDE.md Punkt 3a).
- **Komponenten**: `Select` statt `<select>`, `DatePicker` statt `<input type="date">`.
- **Sprache**: „Preisanfrage", „Angebot", „Rückmeldefrist" — Handwerker-deutsch. **NICHT**
  „Anfrage" verwenden (Kollision mit Kunden-Anfrage), **NICHT** „Ausschreibung" (klingt
  nach Vergabestelle). **NICHT** „Bestellung" im Preisanfrage-Kontext.

---

## 6. Tests

### 6.1 Backend (Pflicht — mind. Happy-Path + 1 Fehlerfall pro Service)

- **`PreisanfrageServiceTest`**
  - `erstellen_generiertNummerUndTokens_und_speichertPositionen`
  - `erstellen_kollision_bei_duplicateToken_wiederholtRetry`
  - `versende_setztOutgoingMessageIdUndStatus`
  - `eintragen_aktualisiertStatusAufBeantwortetWennAllePositionenAngebotHaben`
  - `vergebeAuftrag_erzeugtNeueBestellungen`
- **`PreisanfrageZuordnungServiceTest`**
  - `tryMatch_matchtViaInReplyTo`
  - `tryMatch_matchtViaTokenImBetreff_wennInReplyToFehlt`
  - `tryMatch_nichtsGefunden_returnsEmpty`
- **`PreisanfrageControllerTest` (`@WebMvcTest`)**
  - Happy-Paths für alle Endpoints + 404 bei falscher ID.
- **`BestellungPdfServiceTest`** (existiert schon) ergänzen:
  - `generatePdfForPreisanfrage_enthaeltTokenUndNummer`

**Security-Tests** (laut CLAUDE.md Pflicht bei neuen Endpoints):
- SQL-Injection-Payload in Notiz-Feld: `'; DROP TABLE preisanfrage; --`
- XSS-Payload: `<script>alert(1)</script>` → darf nicht im PDF als HTML, nicht
  unsanitiert in JSON-Response landen.
- Token-Erraten: `GET /vergeben/99999999` → 404 nicht 500.
- Negative IDs, `Long.MAX_VALUE`.

**Test-Daten:** NUR Dummy (`Max Mustermann`, `test@example.com`, `Musterstraße 1`).

### 6.2 Frontend

Vitest + Testing Library:
- `AngeboteEinholenModal.test.tsx` — Pflichtfelder-Validierung, Multi-Lieferant-Auswahl.
- `PreisanfragenPage.test.tsx` — Liste rendert, Filter wirkt.
- `PreisanfrageVergleichModal.test.tsx` — günstigster Preis korrekt markiert.

---

## 7. Kritische Dateien (Übersicht für Impact-Abschätzung)

### NEU
| Datei | Zweck |
|---|---|
| `src/main/resources/db/migration/V227__preisanfrage.sql` | Schema |
| `src/main/java/org/example/kalkulationsprogramm/domain/Preisanfrage.java` | Entity |
| `.../domain/PreisanfrageLieferant.java` | Entity |
| `.../domain/PreisanfragePosition.java` | Entity |
| `.../domain/PreisanfrageAngebot.java` | Entity |
| `.../domain/PreisanfrageStatus.java` | Enum |
| `.../domain/PreisanfrageLieferantStatus.java` | Enum |
| `.../repository/Preisanfrage*Repository.java` (4 Dateien) | JPA-Repos |
| `.../dto/Preisanfrage/*Dto.java` (5 DTOs) | DTOs |
| `.../mapper/PreisanfrageMapper.java` | Mapping |
| `.../service/PreisanfrageService.java` | Core-Logik |
| `.../service/PreisanfrageZuordnungService.java` | Zuordnungs-Regel |
| `.../util/TokenGenerator.java` | Token-Erzeugung |
| `.../controller/PreisanfrageController.java` | REST |
| `react-pc-frontend/src/pages/PreisanfragenPage.tsx` | Liste + Detail |
| `react-pc-frontend/src/components/AngeboteEinholenModal.tsx` | Anlage-Modal |
| `react-pc-frontend/src/components/PreisanfrageVergleichModal.tsx` | Vergleichs-Matrix |
| `react-pc-frontend/src/components/PreiseEintragenModal.tsx` | Preis manuell |
| jeweils `.test.tsx` für die 3 Frontend-Dateien | Tests |

### GEÄNDERT
| Datei | Änderung |
|---|---|
| [BestellungPdfService.java](src/main/java/org/example/kalkulationsprogramm/service/BestellungPdfService.java) | Neue Methode `generatePdfForPreisanfrage(palId)`, evtl. `addCompanyLogo`-Helper extrahieren |
| [EmailAutoAssignmentService.java](src/main/java/org/example/kalkulationsprogramm/service/EmailAutoAssignmentService.java) | `tryAssignToPreisanfrage` als erste Regel einbauen |
| [EmailImportService.java](src/main/java/org/example/kalkulationsprogramm/service/EmailImportService.java) | Header `In-Reply-To` lesen und in Entity-Feld schreiben |
| [domain/Email.java](src/main/java/org/example/kalkulationsprogramm/domain/Email.java) | Feld `inReplyTo` hinzufügen (falls noch nicht vorhanden) |
| [dto/Email/UnifiedEmailDto.java](src/main/java/org/example/kalkulationsprogramm/dto/Email/UnifiedEmailDto.java) | Feld `preisanfrageLieferantRef` hinzufügen |
| [BestellungEditor.tsx](react-pc-frontend/src/pages/BestellungEditor.tsx) | Neuer Button „Angebote einholen" + Modal-Öffner |
| [App.tsx](react-pc-frontend/src/App.tsx) | Route `/einkauf/preisanfragen` |
| [EmailCenter.tsx](react-pc-frontend/src/pages/EmailCenter.tsx) | Badge + Quick-Action „Preise eintragen" bei zugeordneten Mails |
| evtl. `V228__email_in_reply_to.sql` | Migration für `inReplyTo`-Feld |

### NICHT ANFASSEN
- `BestellungEmailModal` im `BestellungEditor` (bestehender Einzelversand bleibt).
- Entity `Anfrage` (Kunden-Anfrage, irrelevant für dieses Feature).
- SMTP-Konfiguration in `application-local.properties`.
- `addRueckverfolgbarkeitsInfobox` und `addEn1090ZeugnisBlock` — sind für echte
  Bestellungen, Preisanfrage bekommt eigene Kopfgrafik.

---

## 8. Verifikation (End-to-End)

1. **Migration**:
   `./mvnw.cmd flyway:info` → V227 (und ggf. V228) als `PENDING` sichtbar.
   Spring Boot Start → migration angewendet, keine Fehler.
2. **Backend-Tests**: `./mvnw.cmd test 2>&1 | tail -60` → alles grün.
3. **Frontend**:
   - `cd react-pc-frontend && npm run lint`
   - `cd react-pc-frontend && npm run build` → kein Fehler
   - `npm run test -- PreisanfragenPage`
4. **Manueller End-to-End-Test**:
   - Backend: `./mvnw.cmd spring-boot:run`
   - Frontend: `cd react-pc-frontend && npm run dev`
   - In **Einkauf → Bedarf** Lieferanten-Gruppe wählen → „Angebote einholen".
   - 2–3 Test-Lieferanten ankreuzen, Rückmeldefrist 7 Tage, „Senden".
   - Erwartung: Toast „Preisanfrage an 3 Lieferanten versendet".
   - Wechsle nach **Einkauf → Preisanfragen** → neue Preisanfrage sichtbar, Status `OFFEN`,
     3 Lieferanten `VERSENDET`.
   - Prüfe Ziel-Postfach (`info-bauschlosserei-kuhn@t-online.de`): 3 Mails mit je einem
     PDF-Anhang + eindeutigem Token im Betreff.
   - Antworte testhalber mit Outlook auf eine der Mails (Token bleibt im Betreff).
   - Im ERP: `EmailImportService` läuft alle N Minuten, oder manuell triggern.
     Warte auf Import → neue Mail erscheint in EmailCenter → Badge „PA-YYYY-NNN · Lieferant X".
   - Klick „Preise eintragen" → Modal → Preise eingeben → Speichern.
   - Zurück zu **Preisanfragen** → „Vergleich öffnen" → Matrix zeigt eingetragenen Preis,
     bei 2. Antwort: günstigster gelb markiert.
   - „Lieferant B beauftragen" → Confirm → neue Bestellung in `Bedarf` mit allen Positionen
     erscheint (mit EN-1090-Infobox auf dem Bestell-PDF, wie immer).
5. **Security-Smoke:**
   - In „Notiz" `<script>alert(1)</script>` → PDF-Ausgabe text-escaped, kein Code.
   - `/api/preisanfragen/lieferant/-1/pdf` → 404.

---

## 9. Commit-Strategie

**Nicht alles in einem Commit.** Der Agent splittet sinnvoll:

1. `feat(preisanfrage): DB-Schema + Entities + Repositories (V227)`
2. `feat(preisanfrage): Service + TokenGenerator + Tests`
3. `feat(preisanfrage): PDF-Variante + E-Mail-Versand`
4. `feat(email): inReplyTo-Feld + Preisanfrage-Auto-Zuordnung`
5. `feat(preisanfrage): Controller + DTOs + Mapper`
6. `feat(preisanfrage): Frontend-Seite + AngeboteEinholenModal`
7. `feat(preisanfrage): Vergleichs-Matrix + manuelle Preiserfassung`
8. `feat(email-center): Badge und Quick-Action für Preisanfragen-Antworten`

Nach jedem Commit **Tests lokal laufen lassen**, nicht stapeln.

---

## 10. Abschluss

Gemäß CLAUDE.md Punkt 4: zum Abschluss `/review-and-ship` ausführen (Pre-Merge-Checkliste,
Secrets-Scan, Tests, Commit, Push). **Nicht direkt nach main mergen** — erst Pull Request
öffnen, mindestens eine User-Review-Runde, manueller End-to-End-Test mit echten
Test-Lieferanten (2–3 Fake-Adressen an eigenes Postfach).

## 11. Offene Punkte für Folge-Features (bewusst nicht hier)

- **KI-Preis-Extraktion** aus Angebots-PDFs via `GeminiDokumentAnalyseService` → eigener
  Plan, nachdem manuelle Erfassung bewährt.
- **Automatische Erinnerungs-Mails** bei verstrichener Rückmeldefrist → Scheduled Task.
- **Verhandlungs-Runde** (zweite Runde an einzelne Lieferanten).
- **Preis-Historie pro Lieferant** (Trendlinie über mehrere Preisanfragen hinweg).
- **PDF-Signierung** des ausgehenden PDFs zur Manipulationssicherheit.

Diese Themen explizit **nicht** umsetzen, bis das Core-Feature im Produktivbetrieb
bewährt ist.
