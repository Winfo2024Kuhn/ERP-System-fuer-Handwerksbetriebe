# Einkauf – End-to-End-Workflows (Bestellungen · Bedarf · Preisanfragen)

> Zentraldokument für den kompletten Einkaufsprozess im ERP. Deckt alle drei
> Reiter unter Ribbon **Projektmanagement → Einkauf** ab und zeigt, wie sie
> zusammen den Lebenszyklus vom Materialbedarf bis zur eingebuchten Rechnung
> abbilden.
>
> Verwandte Einzel-Dokumente:
> [BESTELLWESEN.md](BESTELLWESEN.md) (Datenmodell Bestellungen/PDF),
> [LIEFERANTEN_DOKUMENTEN.md](LIEFERANTEN_DOKUMENTEN.md) (Eingangsrechnungen/KI),
> [DOKUMENTEN_LIFECYCLE.md](DOKUMENTEN_LIFECYCLE.md) (Dokumentenketten),
> `.claude/plans/preisanfrage-fortschritt.md` (Feature-Historie Preisanfrage).

---

## 1. Einkaufsprozess – Gesamtbild

```
 ┌─────────────────────────────────────────────────────────────────────┐
 │  Projekt                                                            │
 │     ├── ArtikelInProjekt (bestellt = false)        ──────────┐      │
 │     │                                                        ▼      │
 │     │                                                   [ Bedarf ]  │
 │     │                                                        │      │
 │     │              ┌─────────────┬─────────────────┬─────────┤      │
 │     │              ▼             ▼                 ▼         ▼      │
 │     │        direkt bestellen   Angebote    PDF/E-Mail an    …      │
 │     │        (bekannter Preis)  einholen    bekannten Lief.         │
 │     │              │             │                 │                │
 │     │              │             ▼                 │                │
 │     │              │      [ Preisanfragen ]        │                │
 │     │              │             │                 │                │
 │     │              │    1..N Lieferanten, PDF+Mail │                │
 │     │              │    mit Token je Lieferant     │                │
 │     │              │             │                 │                │
 │     │              │   Antworten (Mail+PDF)        │                │
 │     │              │   → KI-Preisextraktion        │                │
 │     │              │   → Vergleichsmatrix          │                │
 │     │              │   → Vergabe                   │                │
 │     │              │             │                 │                │
 │     │              ▼             ▼                 ▼                │
 │     │        ArtikelInProjekt.bestellt = true,                      │
 │     │        lieferant + preisProStueck gesetzt                     │
 │     │                      │                                        │
 │     ▼                      ▼                                        │
 │  [ Bestellungen ]  ◄── dok-lifecycle: Auftrags­bestätigung,         │
 │                         Lieferschein, Rechnung (per Mail/Upload)    │
 └─────────────────────────────────────────────────────────────────────┘
```

**Drei UI-Reiter, ein Workflow:**

| Reiter | Route | Seite | Fokus |
|---|---|---|---|
| Bestellungen | `/bestellungen` | `BestellungenUebersicht.tsx` | Verwaltung aller laufenden/abgeschlossenen Einkaufs­vorgänge + Zuordnung zu Projekten/Kostenstellen |
| Bedarf | `/bestellungen/bedarf` | `BestellungEditor.tsx` | Offene Materialpositionen gruppiert nach Lieferant/Projekt – Einstiegspunkt für Bestellung oder Preisanfrage |
| Preisanfragen | `/einkauf/preisanfragen` | `PreisanfragenPage.tsx` | Mehrere Lieferanten parallel anfragen, Angebote vergleichen, Auftrag vergeben |

---

## 2. Reiter „Bedarf" – Einstiegspunkt

### 2.1 Zweck
Zeigt alle `ArtikelInProjekt`-Zeilen mit `bestellt = false`. Hier entscheidet
der Handwerker, **wie** bestellt wird: direkt beim bekannten Lieferanten oder
über eine Preisanfrage an mehrere.

### 2.2 UI-Elemente
- **Gruppierungs-Umschalter** (Pill-Toggle oben):
  - *Lieferant* – Standard, Export (PDF/E-Mail) nur hier möglich.
  - *Projekt* – reine Ansicht, kein Export.
- **Positionen-Tabelle** pro Gruppe mit Artikel, Menge, Einheit, Kommentar, Projekt.
- **Aktionen pro Gruppe (Lieferanten-Ansicht):**
  - „E-Mail" – Bestell-PDF generieren und per Mail versenden.
  - „Angebote einholen" – öffnet `AngeboteEinholenModal` mit vorausgewählten
    Positionen.
  - „Als bestellt markieren" (nach Versand).
- **Aktionen oben:**
  - „Position hinzufügen" – manuelle Zeile.
  - „HiCAD-Stückliste importieren" – Stahlbauteilliste als Bedarf erzeugen.

### 2.3 End-to-End-Workflow A: Direkt bestellen (bekannter Lieferant/Preis)

```
Bedarf (Ansicht: Lieferant)
    │
    ├── Gruppe „Stahlhandel Müller" auswählen
    │
    ├── [E-Mail]  ──►  BestellungPdfService.generatePdf(lieferantId)
    │                   → PDF mit allen Positionen
    │                   → EmailService.sendEmailAndReturnMessageId(…)
    │
    └── [Bestellt markieren]
              └── ArtikelInProjekt.bestellt = true
              └── ArtikelInProjekt.bestelltAm = heute
              └── Preis = lieferantenArtikelPreis × Menge
```

Zugehörige Endpoints:
- `GET  /api/bestellungen/offen`
- `PATCH /api/bestellungen/{id}` (bestellt-Flag)
- `GET  /api/bestellungen/lieferant/{lieferantId}/pdf`
- `POST /api/bestellungen/lieferant/{lieferantId}/markiere-exportiert`

### 2.4 End-to-End-Workflow B: Angebote einholen (mehrere Lieferanten)

```
Bedarf → Gruppe markieren → [Angebote einholen]
    │
    ▼
AngeboteEinholenModal (vorausgewählte Positionen)
    │
    ├── Lieferanten auswählen (Multi-Select, Suche)
    ├── Antwortfrist (Pflicht)
    ├── Notiz (optional)
    │
    ├── POST /api/preisanfragen            (Create)
    └── POST /api/preisanfragen/{id}/versenden   (Versand an alle)
              │
              ▼
         Preisanfrage-Flow (siehe Abschnitt 4)
```

### 2.5 HiCAD-Import (Kurz)
- `POST /api/bestellungen/import/hicad/preview` (multipart) → Preview-DTO.
- `POST /api/bestellungen/import/hicad/confirm`  (JSON)    → erzeugt Bedarfs­zeilen
  inkl. automatischer Artikel- und Werkstoff-Zuordnung (siehe
  `ArtikelMatchingAgentService`).

---

## 3. Reiter „Bestellungen" – Verwaltung & Zuordnung

### 3.1 Zweck
Übersicht **aller Einkaufs-Dokumentenketten** (Anfrage → Auftrags­bestätigung →
Lieferschein → Rechnung). Zeigt in vier Tabs den aktuellen Zustand je Kette
und erlaubt die Kostenzuordnung zu Projekten/Kostenstellen.

### 3.2 Die vier Tabs

| Tab | Inhalt | Beispiel |
|---|---|---|
| Offene Anfragen | Dokumentenketten ohne AB/Lieferschein/Rechnung | PDF raus, Lieferant hat noch nicht reagiert |
| Laufende Bestellungen | Mindestens AB, noch keine Schlussrechnung | Auftrag bestätigt, Ware unterwegs |
| Abgeschlossen | Rechnung vorhanden, **noch nicht** einem Projekt zugeordnet | wartet auf Kostenzuordnung |
| Zugeordnet | Rechnung ist Projekt/Kostenstelle zugeordnet | archiviert, zählt in Projekt-Materialkosten |

### 3.3 End-to-End-Workflow C: Bestellung anlegen, belegen, zuordnen

```
1. Bedarf  ──►  PDF + E-Mail an Lieferant          Tab: Offene Anfragen
                 │
                 │  (Lieferant schickt AB per Mail)
                 ▼
2. E-Mail-Import: AB erkannt/zugeordnet            Tab: Laufende Bestellungen
   (LieferantDokumentController, KI-Erkennung,
    siehe LIEFERANTEN_DOKUMENTEN.md)
                 │
                 │  (Ware kommt, Lieferschein + Rechnung per Mail)
                 ▼
3. Dokumentenkette vollständig, Rechnung da        Tab: Abgeschlossen
                 │
                 │  User klickt „Zuordnen" → ProjectSelectModal /
                 │  KostenstelleSelectModal
                 ▼
4. ProjektAnteile eingetragen                      Tab: Zugeordnet
   POST /api/bestellungen-uebersicht/zuordnen
              │
              └── Materialkosten fließen in
                  Projekt-/Kostenstellen-Kalkulation
```

### 3.4 Aktionen pro Kette
- **Geschäftsdaten bearbeiten:** Belegnummer, Datum, Beträge, Liefertermin, MwSt.
  - `GET/PUT /api/bestellungen-uebersicht/geschaeftsdaten/{dokId}`
- **Zuordnen / Aufteilen:** eine Rechnung auf mehrere Projekte + Kostenstellen
  prozentual splitten.
  - `POST /api/bestellungen-uebersicht/zuordnen` (Body: `ProjektAnteil[]`)
  - `DELETE /api/bestellungen-uebersicht/zuordnung/{dokId}`
  - `GET /api/bestellungen-uebersicht/zuordnungen/{dokId}`
  - `GET /api/bestellungen-uebersicht/zuordnungen/kostenstelle/{kostenstelleId}`
- **Lagerbestellung markieren:** Rechnung ohne konkretes Projekt (Bestand­sauffüllung).
  - `POST /api/bestellungen-uebersicht/lagerbestellung/{dokId}`
- **PDF-Viewer:** `PdfCanvasViewer` zeigt die zugrundeliegende PDF direkt im Tab.

### 3.5 Datenmodell
Siehe [BESTELLWESEN.md §1–6](BESTELLWESEN.md#1-offene-bestellungen) für
`ArtikelInProjekt`, Verrechnungs­einheiten und PDF-Generierung.

---

## 4. Reiter „Preisanfragen" – parallele Einkaufs-Anfrage

### 4.1 Zweck
Eine **Preisanfrage** (nicht zu verwechseln mit der Kunden-`Anfrage`!) ist
ein Einkaufs­vorgang, der **n Lieferanten gleichzeitig** um ein Angebot für
dieselben Positionen bittet. Ziel: echte Preisvergleichs­grundlage bevor ein
Auftrag vergeben wird.

### 4.2 Datenmodell (Kurzüberblick)

| Entity | Rolle |
|---|---|
| `Preisanfrage` | Kopf, Nummer `PA-YYYY-NNN`, Status, Antwortfrist, Notiz |
| `PreisanfrageLieferant` | 1 Zeile pro angefragtem Lieferant, individueller Token + Versand-/Antwort-Metadaten |
| `PreisanfragePosition` | 1 Zeile pro Artikel/Menge (für alle Lieferanten identisch) |
| `PreisanfrageAngebot` | Preis eines Lieferanten für eine Position (manuell oder KI-extrahiert) |

| Enum | Werte |
|---|---|
| `PreisanfrageStatus` | OFFEN, TEILWEISE_BEANTWORTET, VOLLSTAENDIG, VERGEBEN, ABGEBROCHEN |
| `PreisanfrageLieferantStatus` | VORBEREITET, VERSENDET, BEANTWORTET, ABGELEHNT |

Tabellen siehe Flyway `V227__preisanfrage.sql`. `Preisanfrage.vergebenAn` zeigt
nach Vergabe auf den Gewinner-`PreisanfrageLieferant`.

### 4.3 Nummer und Token
- **Nummer** (Preisanfrage): `PA-{YYYY}-{NNN}` (3-stellig, laufend pro Jahr).
- **Token** (je Lieferant): `PA-{YYYY}-{NNN}-{5 Zeichen aus A–Z+2–9}` — ohne
  `0,O,1,I`, damit Handschrift eindeutig bleibt.
- Token steht in Mail-Betreff, Mail-Body und auf dem PDF (Hinweisbox) und dient
  zur Rück-Zuordnung eingehender Antworten.

### 4.4 End-to-End-Workflow D: Preisanfrage komplett

```
  1) ERSTELLEN
     Bedarf → „Angebote einholen"        ODER          Preisanfragen → „Neu"
         │                                                   │
         ▼                                                   ▼
   AngeboteEinholenModal                             (vorausgewählt = leer)
   (Positionen vorbelegt)                                    │
         │                                                   │
         └───────────────► POST /api/preisanfragen
                            { bauvorhaben?, projektId?,
                              antwortFrist, notiz?,
                              positionen[], lieferanten[] }
                            → Preisanfrage (OFFEN)
                            → PreisanfrageLieferant (VORBEREITET) × n
                            → PreisanfragePosition × m

  2) VERSENDEN
     POST /api/preisanfragen/{id}/versenden               (alle Lieferanten)
     POST /api/preisanfragen/lieferant/{palId}/versenden  (einzeln / Nachfassen)
         │
         ├── Pro Lieferant:
         │     ├── PDF-Generierung (BestellungPdfService.generatePdfForPreisanfrage)
         │     │   rote Kopfzeile, Token-Box, Hinweis „Bitte Code angeben",
         │     │   Positionen-Tabelle mit leerer Spalte „Ihr Preis €/Einheit"
         │     │
         │     ├── EmailService.sendEmailAndReturnMessageId(
         │     │       to = lieferant.email,
         │     │       subject = „Preisanfrage PA-YYYY-NNN (Token TOKEN)",
         │     │       replyTo = optional TOKEN@reply-to-domain,
         │     │       attachments = [PDF])
         │     │
         │     └── PreisanfrageLieferant:
         │           status = VERSENDET
         │           outgoingMessageId = Message-ID der versendeten Mail
         │           versendetAn + versendetAm

  3) ANTWORT-ZUORDNUNG (vollautomatisch, 4-stufige Fallback-Kette)
     Eingehende E-Mail ──► EmailAutoAssignmentService.tryAutoAssign
                              │
                              └── 1. Regel: tryAssignToPreisanfrage
                                   PreisanfrageZuordnungService.tryMatch
                                    (1) parentEmail.messageId == outgoingMessageId
                                    (2) To-Adresse TOKEN@reply-to-domain  (optional)
                                    (3) Token-Regex im Betreff
                                        PA-\d{4}-\d{3}-[A-Z2-9]{5}
                                    (4) PA-Nummer im Betreff + Absender-Mail
                                        gegen PreisanfrageLieferant.versendetAn
                                 │
                                 ▼
                         Match → PAL.antwortEmail = incoming
                                 PAL.status = BEANTWORTET
                                 PAL.antwortErhaltenAm = now()
                                 Email zusätzlich dem Lieferanten zugeordnet
                                 (sichtbar im Lieferanten-Postfach)

     Gesamt-Status der Preisanfrage wird neu gesetzt:
         alle BEANTWORTET → VOLLSTAENDIG
         mindestens 1     → TEILWEISE_BEANTWORTET

  4) PREISE ERFASSEN  (zwei Wege, nicht exklusiv)
     a) MANUELL
        Vergleichs-Modal → „Preise eintragen" → PreiseEintragenModal
        → POST /api/preisanfragen/angebote  pro Position
     b) KI-EXTRAKTION
        - Manuell: Vergleichs-Modal → „KI-Preise auslesen"
        - Auto:    Beim Statuswechsel auf VOLLSTAENDIG wird
                   PreisanfrageAngebotsExtraktionService.extrahiereAsync
                   per Setter-Injection getriggert.
        Pro PAL:
          ├── PDF-Anhang der antwortEmail laden
          ├── Gemini inline_data (application/pdf) mit Positionsliste +
          │   exakter positionId  → deterministische Zuordnung
          ├── Pro Material-Position zusätzlich ArtikelMatchingAgentService
          │   (DRY): lernt externe Nr., Preis, ggf. neue Kategorie.
          ├── Zusatzpositionen (Zuschnitt, Fracht) werden NUR geloggt,
          │   NICHT in den Artikelstamm übernommen.
          └── Idempotent: bestehende Angebote werden nicht überschrieben.

  5) VERGLEICHEN
     GET /api/preisanfragen/{id}/vergleich
     PreisanfrageVergleichModal:
         Zeilen = Positionen (sortiert nach reihenfolge)
         Spalten = Lieferanten
         günstigster Preis pro Zeile: bg-rose-50 + Trophy-Icon
         KI-Vorschläge: Sparkle-Icon + „KI-Vorschlag, bitte prüfen"
         Summenzeile netto pro Lieferant
         Sticky Position-Spalte

  6) VERGEBEN
     POST /api/preisanfragen/{id}/vergeben/{palId}
         │
         ├── Preisanfrage.vergebenAn = gewinner
         ├── Preisanfrage.status = VERGEBEN
         ├── Für jede Position: zugehörige ArtikelInProjekt-Zeile
         │   wird UMGEROUTET:
         │      lieferant         = gewinner.lieferant
         │      preisProStueck    = Angebots-Einzelpreis
         │   (KEINE neuen Zeilen, nur Update!)
         └── Rest-PALs behalten Status, ihre Angebote bleiben als Historie.

  7) BESTELLEN
     → Bedarfs-Seite zeigt die umgerouteten Positionen jetzt unter
       dem Gewinner-Lieferanten mit dem neuen Preis.
     → Normaler Bestell-Flow (Workflow A) übernimmt ab hier.

  ABBRECHEN jederzeit möglich:
     DELETE /api/preisanfragen/{id}  →  status = ABGEBROCHEN
     (Soft-Cancel; Historie bleibt, blockiert nicht VERGEBEN.)
```

### 4.5 REST-Endpoints (Preisanfrage)

| Methode | Pfad | Fehler-Mapping |
|---|---|---|
| `POST` | `/api/preisanfragen` | 400 Bean-Validation |
| `POST` | `/api/preisanfragen/{id}/versenden` | 404/409 |
| `POST` | `/api/preisanfragen/lieferant/{palId}/versenden` | 404/409 |
| `GET`  | `/api/preisanfragen?status={enum}` | Ungültiger Enum-Wert = kein Filter (statt 500) |
| `GET`  | `/api/preisanfragen/{id}` | 404 |
| `GET`  | `/api/preisanfragen/{id}/vergleich` | 404 |
| `GET`  | `/api/preisanfragen/lieferant/{palId}/pdf` | 404, `application/pdf` |
| `POST` | `/api/preisanfragen/{id}/angebote/extrahieren` | 404/409, KI-Trigger manuell |
| `POST` | `/api/preisanfragen/angebote` | 400/404 – manuelle/korrigierte Preise |
| `POST` | `/api/preisanfragen/{id}/vergeben/{palId}` | 409 wenn nicht beantwortet |
| `DELETE` | `/api/preisanfragen/{id}` | 409 wenn bereits VERGEBEN |

**Konventionen Controller:**
- `IllegalArgumentException` → **404** (not found)
- `IllegalStateException` → **409** (conflict)
- Bean-Validation-Failure → **400**
- Response-Header `X-Error-Reason` mit menschlich lesbarer Ursache.

### 4.6 Reply-To-Domain (optional, skalierbar)
Config-Key `preisanfrage.reply-to-domain=` (leer = deaktiviert).
Sobald gesetzt (z. B. `bauschlosserei-kuhn.de` + Catch-All-IMAP), setzt der
Service beim Mail-Versand den Header `Reply-To: TOKEN@domain`. Damit greift
Fallback-Stufe 2 der Zuordnung auch dann, wenn Lieferanten eine **neue Mail
schreiben** statt zu antworten. Kein Code-Change nötig – reine Konfiguration.

### 4.7 Integration EmailCenter
`UnifiedEmailDto` enthält das Feld `preisanfrageLieferantRef`
(`{ preisanfrageId, nummer, palId, lieferantId, lieferantenname }`), sobald
eine eingegangene Mail einem PAL zugeordnet wurde (Rückwärts-Lookup
`findByAntwortEmail_Id`).

Im EmailCenter erscheint daraufhin:
- **Listen-Badge** „Preisanfrage PA-YYYY-NNN" (`Scale`-Icon, rose-Palette).
- **Detail-Banner** mit „Öffnen" (Deep-Link `/einkauf/preisanfragen?open={id}`)
  und „Preise eintragen" (öffnet `PreiseEintragenModal` direkt aus der Mail).
- Kein zusätzlicher Fetch, alle Daten liegen schon im bestehenden Inbox-Listing.

---

## 5. Frontend-Architektur

### 5.1 Komponenten-Übersicht

| Datei | Zweck |
|---|---|
| `pages/BestellungenUebersicht.tsx` | Reiter „Bestellungen", 4 Tabs, Zuordnung |
| `pages/BestellungEditor.tsx` | Reiter „Bedarf", Gruppierungs-Umschalter, Aktionen |
| `pages/PreisanfragenPage.tsx` | Reiter „Preisanfragen", Status-Tabs, Liste |
| `components/AngeboteEinholenModal.tsx` | Preisanfrage anlegen + sofort versenden |
| `components/PreisanfrageVergleichModal.tsx` | Matrix-Vergleich, günstigster Preis, Summenzeile |
| `components/PreiseEintragenModal.tsx` | Manuell/KI-korrigierend Preise erfassen |
| `components/MaterialbestellungModal.tsx` | Einzel-Bedarf bearbeiten |
| `components/HicadImportModal.tsx` | Stückliste einlesen |
| `components/ProjectSelectModal.tsx` / `KostenstelleSelectModal.tsx` | Zuordnungs-Dialog |

### 5.2 Routing

```tsx
<Route path="/bestellungen"         element={<BestellungenUebersicht />} />
<Route path="/bestellungen/bedarf"  element={<BestellungEditor />} />
<Route path="/einkauf/preisanfragen" element={<PreisanfragenPage />} />
```

### 5.3 Ribbon-Navigation
`components/layout/RibbonNav.tsx` → Kategorie **Projektmanagement** →
Untergruppe **Einkauf** mit drei Items:
`Bestellungen` (`ShoppingCart`), `Bedarf` (`List`), `Preisanfragen` (`Scale`).

---

## 6. Backend-Services (Einkauf)

| Service | Verantwortung |
|---|---|
| `BestellungService` | Offene Bedarfs­positionen, Bestellstatus, Preisberechnung nach Verrechnungs­einheit |
| `BestellungPdfService` | PDF für Bestellung **und** für Preisanfrage (`generatePdfForPreisanfrage`). Implementiert `PreisanfragePdfGenerator`. |
| `BestellungsUebersichtService` | Dokumentenketten gruppieren, 4-Tab-Response, Zuordnungen speichern |
| `PreisanfrageService` | CRUD + Versand + Vergabe (routet `ArtikelInProjekt` um) |
| `PreisanfrageZuordnungService` | 4-stufige Fallback-Kette zur Antwort-Zuordnung |
| `PreisanfrageAngebotsExtraktionService` | KI-Extraktion Preise aus Antwort-PDFs (Gemini `inline_data`, `application/pdf`). Auto-Trigger bei VOLLSTAENDIG. |
| `EmailAutoAssignmentService` | Ruft `PreisanfrageZuordnungService` als **erste Regel** vor `tryAssignToLieferant` |
| `ArtikelMatchingAgentService` | Lernt externe Artikelnummern + Preise, kann neue Kategorien anlegen (Leaf-Only) |
| `FirmeninformationService` | Firmenlogo für alle PDFs (Upload via `POST /api/firma/logo`) |

---

## 7. Konventionen & Fallstricke

1. **Preisanfrage ≠ Anfrage.** `Anfrage` = Kunden-Anfrage (Verkauf),
   `Preisanfrage` = Einkaufs-Anfrage an Lieferanten. Nicht verwechseln,
   getrennte Tabellen/Controller.
2. **Vergabe routet um, legt nicht neu an.** Beim Auftrag an den Gewinner
   werden bestehende `ArtikelInProjekt`-Zeilen auf den Gewinner-Lieferanten
   geändert – es entstehen **keine** neuen Zeilen. Grund: Materialkosten
   bleiben der ursprünglichen Projekt-Position zugeordnet.
3. **Kein `@PreAuthorize`.** Zugriffsschutz zentral in `SecurityConfig`
   (`/api/**` Session-Auth). Keine Method-Security-Annotationen einführen.
4. **Enum-Spalten mit `columnDefinition = "VARCHAR(40)"`.** Hibernate 6
   validiert sonst gegen MySQL-ENUM → Startup-Fehler.
5. **PDF direkt an Gemini.** Kein PDFTextStripper/Tika vorschalten.
   `inline_data` + `mime_type=application/pdf` (siehe
   `GeminiDokumentAnalyseService`).
6. **Token-Alphabet ohne 0/O/1/I.** Reduziert Fehl-Zuordnungen bei
   handschriftlich ausgefüllten/eingescannten Angeboten.
7. **Umlaute Pflicht.** Alle für den Menschen sichtbaren Texte (Mail-Body,
   PDF, UI-Labels, Exception-Messages an den User) mit echten Umlauten.
   ASCII nur für Identifier/Logs.
8. **Antwort-Zuordnung konservativ.** Fallback 4 matcht Absender-Mail gegen
   `versendetAn` (case-insensitive). Mail von abweichender Adresse bleibt
   manuell – bewusste Entscheidung, um Fehl-Zuordnungen zu vermeiden.

---

## 8. Zusammenfassung

| Reiter | Hauptentität | Zweck | Endpoint-Prefix |
|---|---|---|---|
| Bedarf | `ArtikelInProjekt` (bestellt=false) | Auswahl, Gruppieren, Bestellen **oder** Angebote einholen | `/api/bestellungen` |
| Preisanfragen | `Preisanfrage` + `PreisanfrageLieferant` + `PreisanfragePosition` + `PreisanfrageAngebot` | Parallele Anfrage, Vergleich, Vergabe | `/api/preisanfragen` |
| Bestellungen | Dokumentenketten (`AusgangsGeschaeftsDokument` + `LieferantDokument`) | Verwaltung, Zuordnung zu Projekt/Kostenstelle | `/api/bestellungen-uebersicht` |

Der komplette Einkaufsprozess lässt sich damit lückenlos vom ersten
Materialbedarf bis zur eingebuchten Lieferantenrechnung abbilden – entweder
mit bekanntem Lieferanten in einem Rutsch (Bedarf → Bestellungen) oder mit
Preisvergleich in der erweiterten Form (Bedarf → Preisanfragen → Bestellungen).
