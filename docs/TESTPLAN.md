# Testplan – ERP-System für Handwerksbetriebe

> **Ziel:** Systematische Erhöhung der Testabdeckung vom aktuellen Stand (~7 %) auf ein tragfähiges Niveau (≥ 60 % Backend / ≥ 40 % Frontend).

---

## 1. Ist-Zustand

| Kategorie | Quelldateien | Testdateien | Tests | Abdeckung |
|---|---|---|---|---|
| **Controller** | ~45 | 28 | 341 | ~62 % |
| **Service** | ~55 | 34 | 344 | ~62 % |
| **Repository** | ~80 | 6 | 8 | ~8 % |
| **Mapper** | 9 | 8 | 77 | ~89 % |
| **Utility / Helper** | ~10 | 2 | 9 | ~20 % |
| **Frontend (react-pc-frontend)** | ~50+ Komponenten | 11 | 77 | ~22 % |
| **Frontend (react-zeiterfassung)** | ~20+ Komponenten | 3 | 23 | ~15 % |
| **Gesamt** | | **92** | **879** | |

### Bereits getestete Module ✅

**Controller:** AngebotController, ArtikelController, AusgangsGeschaeftsDokumentController, DateiController, EmailController, FormularTemplateController, GeschaeftsdokumentController, KostenVerteilungController, KundeController, LeistungController, LieferantDokumentController, LieferantenController, MietabrechnungController, MietobjektController, MitarbeiterController, OffenePostenController, ProjektController

**Service:** AngebotService, BestellungService (+ Mapping + PDF), ArtikelImportService, ArtikelMatchingService, FeiertagService, EmailAiService, DateiSpeicherService, MonatsSaldoService, InquiryDetectionService, OfferPriceService, OutOfOfficeResponder, RechnungPdfService, StuecklistePdfService, ProjektManagementService (+IT), ZeitkontoService, MietabrechnungService, AusgangsGeschaeftsDokumentService (BezahltTest + vollständig), GeschaeftsdokumentService, ZugferdExtractorService, ZugferdErstellService, KostenVerteilungService, ZeitkontoKorrekturService, ZeitbuchungAutoStopService, SpamFilterService, EmailAutoAssignmentService, EmailImportService, EmailAttachmentProcessingService, VendorInvoiceIntegrationService, ArbeitsgangManagementService, BwaService, MietobjektService

**Repository:** ArtikelRepository (+Cascade), AngebotRepository (+Search), ProjektDokumentRepository, WerkstoffRepository

**Mapper:** ProjektMapper, AngebotMapper, KundeMapper, LieferantMapper, LeistungMapper, ArbeitsgangMapper, MieteMapper, ProduktkategorieMapper

**Utility:** EmailHtmlSanitizer, EmailAiPostProcessor

**Frontend (react-pc-frontend):** Button, Input, Card, Label, SelectCustom, DatePicker, ImageViewer, Dialog, ConfirmDialog, Toast, cn()-Utility

**Frontend (react-zeiterfassung):** MobileDatePicker, NetworkStatusBadge, ImageViewer

---

## 2. Priorisierung

Die Test-Erstellung erfolgt in **5 Phasen**, geordnet nach Geschäftsrisiko und Komplexität.

### Phase 1 – Kritische Geschäftslogik ✅ (91 Tests)

Abgeschlossen: 7 Testdateien, 91 Tests, alle bestanden (10.03.2026).

### Phase 2 – Email-System & Kommunikation ✅ (62 Tests)

Abgeschlossen: 5 Testdateien, 62 Tests, alle bestanden (10.03.2026).

### Phase 3 – Stammdaten-Services & Mapper ✅ (102 Tests)

Abgeschlossen: 10 Testdateien, 102 Tests, alle bestanden (10.03.2026).

### Phase 4 – Controller-Schicht (REST-APIs) ✅ (162 Tests)

Abgeschlossen: 11 Testdateien, 162 Tests, alle bestanden (10.03.2026).

### Phase 5 – Frontend-Tests ✅ (100 Tests)

Abgeschlossen: 14 Testdateien, 100 Tests, alle bestanden.

---

## 3. Detaillierter Testplan nach Phase

---

### Phase 1 – Kritische Geschäftslogik ✅ (91 Tests – abgeschlossen 10.03.2026)

#### 1.1 `AusgangsGeschaeftsDokumentService` (~800 Zeilen)

**Testdatei:** `AusgangsGeschaeftsDokumentServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.1.1 | Erstellt Rechnung mit korrekter Dokumentnummer | `erstellen()` | Unit |
| 1.1.2 | Nummernkreis wird pro Dokumenttyp korrekt hochgezählt | `erstellen()` | Unit |
| 1.1.3 | Bruttobetrag wird korrekt mit MwSt. berechnet | `erstellen()` | Unit |
| 1.1.4 | Teilrechnung berücksichtigt Vorgänger-Salden | `erstellen()` | Unit |
| 1.1.5 | Abschlagsrechnung validiert Betragsgrenzen | `erstellen()` | Unit |
| 1.1.6 | Schlussrechnung berechnet Restsaldo korrekt | `erstellen()` | Unit |
| 1.1.7 | Dokument-Update nach Buchung wird abgelehnt (GoBD) | `aktualisieren()` | Unit |
| 1.1.8 | Buchung sperrt Dokument gegen weitere Änderungen | `buchen()` | Unit |
| 1.1.9 | Produktkategorie wird aus Angebot übernommen | `erstellen()` | Unit |
| 1.1.10 | Vorgänger-/Nachfolger-Verkettung funktioniert | `erstellen()` | Unit |
| 1.1.11 | `ensureAngebotDokument()` erzeugt Dokument nur einmalig | `ensureAngebotDokument()` | Unit |

#### 1.2 `GeschaeftsdokumentService` (~330 Zeilen)

**Testdatei:** `GeschaeftsdokumentServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.2.1 | Dokument wird korrekt erstellt und gespeichert | `erstellen()` | Unit |
| 1.2.2 | Konvertierung erzeugt neuen Typ mit Vorgänger-Referenz | `konvertieren()` | Unit |
| 1.2.3 | Abschluss-Berechnung summiert Zahlungen korrekt | `berechneAbschluss()` | Unit |
| 1.2.4 | Zahlung wird korrekt erfasst und verbucht | `zahlungErfassen()` | Unit |
| 1.2.5 | Nummern-Generierung mit Jahr/Typ-Prefix funktioniert | `erstellen()` | Unit |
| 1.2.6 | Abschlagszahlung wird über Dokumentkette aggregiert | `berechneAbschluss()` | Unit |

#### 1.3 `ZugferdExtractorService` (~330 Zeilen)

**Testdatei:** `ZugferdExtractorServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.3.1 | Extrahiert Rechnungsdaten aus gültiger ZUGFeRD-PDF | `extract()` | Unit |
| 1.3.2 | Parst Positionen mit Menge, Einheit und Betrag | `extract()` | Unit |
| 1.3.3 | Erkennt und berechnet Skonto korrekt | `extract()` | Unit |
| 1.3.4 | Stellt Umlaute nach Encoding-Problemen wieder her | `extract()` | Unit |
| 1.3.5 | Gibt leere Daten zurück bei Nicht-ZUGFeRD-PDF | `extract()` | Unit |
| 1.3.6 | Fallback-XML-Parsing greift bei fehlerhaftem Hauptparser | `extract()` | Unit |

#### 1.4 `ZugferdErstellService` (~75 Zeilen)

**Testdatei:** `ZugferdErstellServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.4.1 | Erzeugt ZUGFeRD-PDF mit eingebetteten Rechnungsdaten | `erzeuge()` | Unit |
| 1.4.2 | Berechnet MwSt. korrekt (19 %) | `erzeuge()` | Unit |
| 1.4.3 | Nutzt Fallback-Datum wenn kein Rechnungsdatum gesetzt | `erzeuge()` | Unit |

#### 1.5 `KostenVerteilungService` (Miet-Modul, ~340 Zeilen)

**Testdatei:** `KostenVerteilungServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.5.1 | Erstellt Kostenstelle mit korrekter Zuordnung | `saveKostenstelle()` | Unit |
| 1.5.2 | Erstellt Kostenposition mit BigDecimal-Rundung | `saveKostenposition()` | Unit |
| 1.5.3 | Kopiert Vorjahres-Positionen korrekt | `copyKostenpositionenVonVorjahr()` | Unit |
| 1.5.4 | Verhindert Doppel-Kopie bei existierenden Positionen | `copyKostenpositionenVonVorjahr()` | Unit |
| 1.5.5 | Verteilungsschlüssel validiert Mietpartei-Zuordnung | `saveVerteilungsschluessel()` | Unit |
| 1.5.6 | Berechnungsmethode BETRAG vs. VERBRAUCHSFAKTOR korrekt | `saveVerteilungsschluessel()` | Unit |

#### 1.6 `ZeitkontoKorrekturService` (~270 Zeilen)

**Testdatei:** `ZeitkontoKorrekturServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.6.1 | Erstellt Korrektur mit Audit-Eintrag (ERSTELLT) | `erstelleKorrektur()` | Unit |
| 1.6.2 | Änderung erzeugt neuen Audit-Eintrag (GEAENDERT) | `aendereKorrektur()` | Unit |
| 1.6.3 | Stornierung setzt Status statt zu löschen (GoBD) | `storniereKorrektur()` | Unit |
| 1.6.4 | Versionsnummer wird bei Änderung inkrementiert | `aendereKorrektur()` | Unit |
| 1.6.5 | Lehnt Korrektur ohne Begründung ab | `erstelleKorrektur()` | Unit |
| 1.6.6 | Summiert aktive Korrekturen pro Jahr korrekt | `summiereAktiveKorrekturen()` | Unit |
| 1.6.7 | Audit-Historie enthält alle Einträge chronologisch | `getAuditHistorie()` | Unit |
| 1.6.8 | Stornierte Korrekturen werden bei Summierung ignoriert | `summiereAktiveKorrekturen()` | Unit |

#### 1.7 `ZeitbuchungAutoStopService` (~110 Zeilen)

**Testdatei:** `ZeitbuchungAutoStopServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.7.1 | Stoppt offene Buchungen nach Mitternacht | `pruefUndStoppeOffeneBuchungen()` | Unit |
| 1.7.2 | Setzt Endzeit auf 23:59 bei Tagesüberschreitung | `autoStoppeWennNoetig()` | Unit |
| 1.7.3 | Berechnet Stunden korrekt aus Zeitspanne | `autoStoppeWennNoetig()` | Unit |
| 1.7.4 | Erzeugt Audit-Eintrag mit Quelle SYSTEM | `autoStoppeWennNoetig()` | Unit |
| 1.7.5 | Invalidiert Monatssaldo-Cache nach Stopp | `autoStoppeWennNoetig()` | Unit |

---

### Phase 2 – Email-System & Kommunikation ✅ (62 Tests – abgeschlossen 10.03.2026)

#### 2.1 `SpamFilterService` (~500 Zeilen)

**Testdatei:** `SpamFilterServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 2.1.1 | Erkennt Spam anhand von Spam-Keywords (Score > Schwelle) | `calculateSpamScore()` | Unit |
| 2.1.2 | Lieferanten-Domains werden gewhitelistet | `calculateSpamScore()` | Unit |
| 2.1.3 | E-Mails mit Zuordnung sind nie Spam | `analyzeAndMarkSpam()` | Unit |
| 2.1.4 | Erkennt Newsletter anhand von List-Unsubscribe | `analyzeAndMarkSpam()` | Unit |
| 2.1.5 | ALL-CAPS-Betreff erhöht Spam-Score | `calculateSpamScore()` | Unit |
| 2.1.6 | Hohe Link-Dichte erhöht Spam-Score (>10 Links) | `calculateSpamScore()` | Unit |
| 2.1.7 | noreply@-Absender werden als verdächtig erkannt | `calculateSpamScore()` | Unit |
| 2.1.8 | Kombinationstest: Rechnung + PDF bei Lieferant = kein Spam | `calculateSpamScore()` | Unit |
| 2.1.9 | Gefährliche Dateitypen (.exe, .bat) erhöhen Score | `calculateSpamScore()` | Unit |
| 2.1.10 | Domain-Blacklist blockiert bekannte Spam-Domains | `calculateSpamScore()` | Unit |

#### 2.2 `EmailAutoAssignmentService` (~300 Zeilen)

**Testdatei:** `EmailAutoAssignmentServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 2.2.1 | Ordnet E-Mail anhand Absender-Domain dem Lieferanten zu | `tryAssignToLieferant()` | Unit |
| 2.2.2 | Ordnet über Kunden-E-Mail dem Projekt zu | `tryAssignToKundeEntity()` | Unit |
| 2.2.3 | Keyword-Matching findet Projekt bei Übereinstimmung | `tryAssignByKeywords()` | Unit |
| 2.2.4 | Ignoriert Keywords mit weniger als Minimallänge | `tryAssignByKeywords()` | Unit |
| 2.2.5 | Zeitfenster-Filter: ±1 Monat wird eingehalten | `tryAutoAssign()` | Unit |
| 2.2.6 | Multi-Step-Fallback: Domain → Kunde → Keywords | `tryAutoAssign()` | Unit |
| 2.2.7 | `findPossibleAssignments()` gibt alle Kandidaten zurück | `findPossibleAssignments()` | Unit |
| 2.2.8 | Gibt `false` zurück wenn keine Zuordnung möglich | `tryAutoAssign()` | Unit |

#### 2.3 `EmailImportService` (~500 Zeilen)

**Testdatei:** `EmailImportServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 2.3.1 | Erkennt Duplikate anhand Message-ID | `importMessage()` | Unit |
| 2.3.2 | Verknüpft Antworten mit Eltern-E-Mail | `importMessage()` | Unit |
| 2.3.3 | Newsletter werden als solche markiert | `importMessage()` | Unit |
| 2.3.4 | Newsletter von Lieferanten werden nicht gefiltert | `importMessage()` | Unit |
| 2.3.5 | Verarbeitet IMAP-Ausgangsordner korrekt | `doImport()` | Unit |
| 2.3.6 | Setzt Processing-Status korrekt im Lifecycle | `importMessage()` | Unit |
| 2.3.7 | Behandelt fehlerhafte Messages ohne Abbruch | `doImport()` | Unit |

#### 2.4 `EmailAttachmentProcessingService` (~330 Zeilen)

**Testdatei:** `EmailAttachmentProcessingServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 2.4.1 | Verarbeitet PDF-Anhänge mit Lieferant-Zuordnung | `processLieferantAttachments()` | Unit |
| 2.4.2 | Dokumenttyp-Erkennung: KI > Nummernmuster > Default | `processLieferantAttachments()` | Unit |
| 2.4.3 | Dateipfad-Auflösung mit 3 Fallback-Strategien | `processLieferantAttachments()` | Unit |
| 2.4.4 | Ignoriert bereits verarbeitete Anhänge | `processLieferantAttachments()` | Unit |
| 2.4.5 | Erstellt Dokument atomar (erst in-memory, dann DB) | `processLieferantAttachments()` | Unit |

#### 2.5 `VendorInvoiceIntegrationService` (~350 Zeilen)

**Testdatei:** `VendorInvoiceIntegrationServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 2.5.1 | Erkennt doppelte Rechnungen anhand Dateiname | `fetchMicrosoftInvoices()` | Unit |
| 2.5.2 | Loggt fehlende Lieferanten-Zuordnung | `fetchMicrosoftInvoices()` | Unit |
| 2.5.3 | Status-Dashboard zeigt korrekte Konfigurations-Info | `getIntegrationStatus()` | Unit |

---

### Phase 3 – Stammdaten-Services & Mapper ✅ (102 Tests – abgeschlossen 10.03.2026)

#### 3.1 `ArbeitsgangManagementService` (~90 Zeilen)

**Testdatei:** `ArbeitsgangManagementServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.1.1 | Erstellt Arbeitsgang mit allen Pflichtfeldern | `erstelleArbeitsgang()` | Unit |
| 3.1.2 | Erstellt Arbeitsgang und gibt gespeicherte Entity zurück | `erstelleArbeitsgang()` | Unit |
| 3.1.3 | Löschen wird bei referenzierten Buchungen abgelehnt | `loescheArbeitsgang()` | Unit |
| 3.1.4 | Löscht Arbeitsgang ohne Referenzen erfolgreich | `loescheArbeitsgang()` | Unit |
| 3.1.5 | Wirft Exception bei unbekanntem Arbeitsgang | `loescheArbeitsgang()` | Unit |
| 3.1.6 | Findet alle Arbeitsgänge | `findeAlle()` | Unit |
| 3.1.7 | Gibt leere Liste zurück wenn keine Arbeitsgänge | `findeAlle()` | Unit |
| 3.1.8 | Stundensatz-Update für existierendes Jahr | `aktualisiereStundensaetze()` | Unit |
| 3.1.9 | Stundensatz-Anlage für fehlendes Jahr (GetOrCreate) | `aktualisiereEinzelnenStundensatz()` | Unit |
| 3.1.10 | Wirft Exception bei unbekanntem Arbeitsgang für Stundensatz | `aktualisiereEinzelnenStundensatz()` | Unit |

#### 3.2 `BwaService` (~110 Zeilen)

**Testdatei:** `BwaServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.2.1 | Findet BWA-Uploads nach Jahr | `findByJahr()` | Unit |
| 3.2.2 | Gibt leere Liste zurück bei unbekanntem Jahr | `findByJahr()` | Unit |
| 3.2.3 | Findet BWA-Upload nach ID mit allen Feldern | `findById()` | Unit |
| 3.2.4 | Mappt FreigegebenVon korrekt | `findById()` | Unit |
| 3.2.5 | Mappt Steuerberater korrekt | `findById()` | Unit |
| 3.2.6 | Mappt Positionen korrekt | `findById()` | Unit |
| 3.2.7 | Gibt Optional.empty bei unbekannter ID | `findById()` | Unit |
| 3.2.8 | Löscht Upload vollständig | `delete()` | Unit |
| 3.2.9 | Gibt verfügbare Jahre sortiert und dedupliziert zurück | `findAvailableYears()` | Unit |
| 3.2.10 | Gibt leere Liste bei keinen Uploads | `findAvailableYears()` | Unit |
| 3.2.11 | Findet gespeicherten Dateinamen | `findStoredFilename()` | Unit |
| 3.2.12 | Gibt Optional.empty bei unbekanntem Upload für Dateiname | `findStoredFilename()` | Unit |

#### 3.3 `MietobjektService` (~95 Zeilen)

**Testdatei:** `MietobjektServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.3.1 | Findet alle Mietobjekte | `findAll()` | Unit |
| 3.3.2 | Findet Mietobjekt nach ID | `getById()` | Unit |
| 3.3.3 | Wirft Exception bei unbekanntem Mietobjekt | `getById()` | Unit |
| 3.3.4 | Erstellt Mietobjekt | `save()` | Unit |
| 3.3.5 | Löscht Mietobjekt | `delete()` | Unit |
| 3.3.6 | Wirft Exception bei Löschen von unbekanntem Mietobjekt | `delete()` | Unit |
| 3.3.7 | Speichert Mietpartei mit BigDecimal-Rundung | `savePartei()` | Unit |
| 3.3.8 | Nicht-MIETER erhält null Vorauszahlung | `savePartei()` | Unit |
| 3.3.9 | Negativer Vorschuss wird auf 0 gesetzt | `savePartei()` | Unit |
| 3.3.10 | Partei ohne Vorschuss wird akzeptiert | `savePartei()` | Unit |
| 3.3.11 | Löscht Mietpartei | `deletePartei()` | Unit |
| 3.3.12 | Wirft Exception bei Löschen unbekannter Partei | `deletePartei()` | Unit |
| 3.3.13 | Gibt Parteien eines Mietobjekts zurück | `getParteien()` | Unit |

#### 3.4 `AngebotMapper`

**Testdatei:** `AngebotMapperTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.4.1 | Mappt alle Felder inkl. Kunde-Daten | `toAngebotResponseDto()` | Unit |
| 3.4.2 | Gibt null bei null-Angebot zurück | `toAngebotResponseDto()` | Unit |
| 3.4.3 | Mappt Angebot ohne Kunde | `toAngebotResponseDto()` | Unit |
| 3.4.4 | Dedupliziert E-Mails aus Kunde und Projekt | `toAngebotResponseDto()` | Unit |
| 3.4.5 | Behandelt null-Anrede korrekt | `toAngebotResponseDto()` | Unit |

#### 3.5 `KundeMapper`

**Testdatei:** `KundeMapperTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.5.1 | Mappt alle Felder zu ListItem | `toListItem()` | Unit |
| 3.5.2 | Gibt null bei null-Kunde | `toListItem()` | Unit |
| 3.5.3 | HatProjekte true/false/null | `toListItem()` | Unit |
| 3.5.4 | Null-Anrede wird behandelt | `toListItem()` | Unit |
| 3.5.5 | Mappt alle Felder zu ResponseDto | `toResponseDto()` | Unit |
| 3.5.6 | Gibt null bei null-Kunde | `toResponseDto()` | Unit |
| 3.5.7 | Mappt null-Felder in ResponseDto | `toResponseDto()` | Unit |
| 3.5.8 | Leere KundenEmails bei fehlendem Kunde | `toResponseDto()` | Unit |
| 3.5.9 | HatProjekte null-Handling | `toListItem()` | Unit |

#### 3.6 `LieferantMapper`

**Testdatei:** `LieferantMapperTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.6.1 | Mappt alle Felder zu ListItem | `toListItem()` | Unit |
| 3.6.2 | Gibt null bei null-Lieferant | `toListItem()` | Unit |
| 3.6.3 | Null-Felder werden behandelt | `toListItem()` | Unit |
| 3.6.4 | Mappt alle Felder zu DetailItem mit DB-Werten | `toDetailItem()` | Unit |
| 3.6.5 | Gibt null bei null-Lieferant | `toDetailItem()` | Unit |
| 3.6.6 | Behandelt null-Ergebnisse von Repository-Abfragen | `toDetailItem()` | Unit |
| 3.6.7 | Fängt Repository-Exceptions ab | `toDetailItem()` | Unit |

#### 3.7 `LeistungMapper`

**Testdatei:** `LeistungMapperTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.7.1 | Mappt Entity zu Dto mit allen Feldern | `toDto()` | Unit |
| 3.7.2 | Gibt null bei null-Entity | `toDto()` | Unit |
| 3.7.3 | Mappt ohne Kategorie | `toDto()` | Unit |
| 3.7.4 | Mappt Dto zu Entity mit Kategorie-Lookup | `toEntity()` | Unit |
| 3.7.5 | Mappt mit null-FolderId | `toEntity()` | Unit |
| 3.7.6 | Mappt mit unbekannter FolderId | `toEntity()` | Unit |
| 3.7.7 | Aktualisiert bestehende Entity | `updateEntity()` | Unit |
| 3.7.8 | Entfernt Kategorie bei null-FolderId | `updateEntity()` | Unit |

#### 3.8 `ArbeitsgangMapper`

**Testdatei:** `ArbeitsgangMapperTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.8.1 | Mappt alle Felder inkl. Stundensatz | `toArbeitsgangResponseDto()` | Unit |
| 3.8.2 | Gibt null bei null-Entity | `toArbeitsgangResponseDto()` | Unit |
| 3.8.3 | Mappt ohne Abteilung | `toArbeitsgangResponseDto()` | Unit |
| 3.8.4 | Mappt ohne Stundensatz | `toArbeitsgangResponseDto()` | Unit |

#### 3.9 `MieteMapper` (~400 Zeilen)

**Testdatei:** `MieteMapperTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.9.1 | Mappt Mietobjekt mit allen Feldern | `toMietobjektDto()` | Unit |
| 3.9.2 | Gibt null bei null-Mietobjekt | `toMietobjektDto()` | Unit |
| 3.9.3 | Mappt Mietobjekt ohne Räume | `toMietobjektDto()` | Unit |
| 3.9.4 | Mappt Mietpartei mit allen Feldern | `toMietparteiDto()` | Unit |
| 3.9.5 | Gibt null bei null-Mietpartei | `toMietparteiDto()` | Unit |
| 3.9.6 | Mappt Raum mit allen Feldern | `toRaumDto()` | Unit |
| 3.9.7 | Gibt null bei null-Raum | `toRaumDto()` | Unit |
| 3.9.8 | Mappt Verbrauchsgegenstand | `toVerbrauchsgegenstandDto()` | Unit |
| 3.9.9 | Gibt null bei null-Verbrauchsgegenstand | `toVerbrauchsgegenstandDto()` | Unit |
| 3.9.10 | Mappt Zählerstand mit allen Feldern | `toZaehlerstandDto()` | Unit |
| 3.9.11 | Gibt null bei null-Zählerstand | `toZaehlerstandDto()` | Unit |
| 3.9.12 | Mappt Zählerstand ohne Verbrauchsgegenstand | `toZaehlerstandDto()` | Unit |
| 3.9.13 | Mappt Kostenstelle mit allen Feldern | `toKostenstelleDto()` | Unit |
| 3.9.14 | Gibt null bei null-Kostenstelle | `toKostenstelleDto()` | Unit |
| 3.9.15 | Mappt Kostenstelle ohne Positionen | `toKostenstelleDto()` | Unit |
| 3.9.16 | Mappt Kostenstelle ohne Schlüssel | `toKostenstelleDto()` | Unit |
| 3.9.17 | Mappt Verteilungsschlüssel mit Einträgen | `toVerteilungsschluesselDto()` | Unit |
| 3.9.18 | Gibt null bei null-Schlüssel | `toVerteilungsschluesselDto()` | Unit |
| 3.9.19 | Mappt Schlüssel ohne Einträge | `toVerteilungsschluesselDto()` | Unit |
| 3.9.20 | Mappt Kostenposition mit berechnetem Betrag | `toKostenpositionDto()` | Unit |
| 3.9.21 | Gibt null bei null-Position | `toKostenpositionDto()` | Unit |
| 3.9.22 | Fängt Berechnungs-Exception ab (Fallback null) | `toKostenpositionDto()` | Unit |
| 3.9.23 | Manueller Betrags-Override hat Vorrang | `toKostenpositionDto()` | Unit |
| 3.9.24 | Default-Berechnungsmethode BETRAG | `toKostenpositionDto()` | Unit |

#### 3.10 `ProduktkategorieMapper`

**Testdatei:** `ProduktkategorieMapperTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.10.1 | Mappt alle Felder inkl. isLeaf | `toDto()` | Unit |
| 3.10.2 | Gibt null bei null-Entity | `toDto()` | Unit |
| 3.10.3 | Erkennt Blatt-Kategorie korrekt | `toDto()` | Unit |
| 3.10.4 | Baut hierarchischen Pfad (3 Ebenen) | `toDto()` | Unit |
| 3.10.5 | Einzelner Pfad ohne Eltern | `toDto()` | Unit |
| 3.10.6 | BildUrl mit Slash-Prefix | `toDto()` | Unit |
| 3.10.7 | BildUrl ohne Slash-Prefix | `toDto()` | Unit |
| 3.10.8 | Null/Blank BildUrl wird behandelt | `toDto()` | Unit |

---

### Phase 4 – Controller-Schicht (REST-APIs) ✅ (162 Tests – abgeschlossen 10.03.2026)

Für jeden Controller sind sowohl Happy-Path- als auch Sicherheitstests erforderlich.

#### 4.1 Untestete Controller mit hoher Priorität

| Controller | Testdatei | Kernmethoden |
|---|---|---|
| **EmailController** | `EmailControllerTest.java` | GET /api/emails, POST /api/emails/send |
| **ZeiterfassungController** | `ZeiterfassungControllerTest.java` | POST /api/zeitbuchungen, GET /api/zeitkonto |
| **GeschaeftsdokumentController** | `GeschaeftsdokumentControllerTest.java` | CRUD + Konvertierung |
| **AusgangsGeschaeftsDokumentController** | `AusgangsGDControllerTest.java` | Erstellen, Buchen, Aktualisieren |
| **MietabrechnungController** | `MietabrechnungControllerTest.java` | Abrechnung erstellen/exportieren |
| **MietobjektController** | `MietobjektControllerTest.java` | CRUD Mietobjekte + Parteien |
| **KostenVerteilungController** | `KostenVerteilungControllerTest.java` | CRUD + Vorjahres-Kopie |
| **FormularTemplateController** | `FormularTemplateControllerTest.java` | Template-CRUD |
| **LeistungController** | `LeistungControllerTest.java` | CRUD + Textbausteine |
| **MitarbeiterController** | `MitarbeiterControllerTest.java` | CRUD Mitarbeiter |
| **OffenePostenController** | `OffenePostenControllerTest.java` | Offene Posten Übersicht |
| **LieferantDokumentController** | `LieferantDokumentControllerTest.java` | Dokument-Upload/-Download |

#### 4.2 Controller-Testschema (pro Controller)

Jeder Controller-Test muss folgende Fälle abdecken (`@WebMvcTest`):

| Kategorie | Testfall | Pflicht |
|---|---|---|
| **Happy Path** | GET gibt 200 mit korrekten Daten zurück | ✅ |
| **Happy Path** | POST erstellt und gibt 200/201 zurück | ✅ |
| **Happy Path** | PUT aktualisiert erfolgreich | ✅ |
| **Happy Path** | DELETE löscht und gibt 200/204 zurück | ✅ |
| **Fehlerfall** | POST/PUT mit leerem Body → 400 | ✅ |
| **Fehlerfall** | GET/PUT/DELETE mit unbekannter ID → 404 | ✅ |
| **SQL Injection** | `'; DROP TABLE x; --` in String-Params | ✅ |
| **XSS** | `<script>alert(1)</script>` in Textfeldern | ✅ |
| **Überlange Eingaben** | Strings > 10.000 Zeichen | ✅ |
| **Ungültige IDs** | Negative IDs, Long.MAX_VALUE, 0 | ✅ |
| **Content-Type** | JSON-Endpoint mit XML aufrufen | ✅ |
| **Path Traversal** | `../../etc/passwd` (bei Datei-Endpoints) | Bei Datei-Endpoints |
| **Upload-Sicherheit** | .exe, .bat, .sh blockiert | Bei Upload-Endpoints |

---

### Phase 5 – Frontend-Tests ✅ (100 Tests – abgeschlossen)

#### 5.1 react-pc-frontend (11 Testdateien, 77 Tests)

**Setup:** Vitest 4.0.18, @testing-library/react 16.3.2, jsdom 28.1.0, @vitest/coverage-v8 4.0.18

| Testdatei | Komponente | Tests | Schwerpunkte |
|---|---|---|---|
| `button.test.tsx` | Button | 10 | Varianten (default/outline/ghost/secondary), Größen, onClick, disabled |
| `input.test.tsx` | Input | 8 | CSS-Klassen, Attribute, Ref-Forwarding, disabled/readonly |
| `card.test.tsx` | Card | 3 | Children-Rendering, CSS-Klassen, className-Merge |
| `label.test.tsx` | Label | 4 | Text, CSS, className, htmlFor |
| `select-custom.test.tsx` | SelectCustom | 9 | Platzhalter, Auswahl, Dropdown öffnen/schließen, onChange, disabled |
| `datepicker.test.tsx` | DatePicker | 11 | Platzhalter, formatiertes Datum, Kalender, deutsche Monate, Löschen/Heute |
| `image-viewer.test.tsx` | ImageViewer | 8 | Null-Handling, Bildanzeige, Schließen, Galerie-Navigation |
| `dialog.test.tsx` | Dialog | 5 | Open/Closed-State, Inhalt, Schließen, onOpenChange, Footer |
| `confirm-dialog.test.tsx` | ConfirmDialog | 6 | Dialog-Anzeige, Custom Labels, Bestätigung/Abbruch |
| `toast.test.tsx` | Toast | 6 | Success/Error/Warning/Info, Provider, mehrere gleichzeitig |
| `utils.test.ts` | cn()-Utility | 7 | Klassen-Merge, Konflikte, bedingte Klassen, Tailwind-Merge |

#### 5.2 react-zeiterfassung (3 Testdateien, 23 Tests)

**Setup:** Identisch zu react-pc-frontend + navigator.onLine Mock

| Testdatei | Komponente | Tests | Schwerpunkte |
|---|---|---|---|
| `MobileDatePicker.test.tsx` | MobileDatePicker | 10 | Platzhalter, Formatierung, Label, Kalender, deutsche Monate, Heute/Schließen |
| `NetworkStatusBadge.test.tsx` | NetworkStatusBadge | 9 | Online/Offline/Syncing/Error-States, onSync, Compact-Modus |
| `image-viewer.test.tsx` | ImageViewer | 4 | Null-Rendering, Bildanzeige, Galerie-Zähler, Zoom-Controls |

---

## 4. Sicherheitstests (OWASP Top 10)

Gemäß DEVELOPMENT.md müssen alle neuen Endpoints mit Sicherheitstests abgedeckt werden. Diese Tests werden in Phase 4 (Controller) integriert.

### Pflicht-Sicherheitstests pro Endpoint

| OWASP | Testfall | Wo integrieren |
|---|---|---|
| **A01 – Broken Access Control** | Path-Traversal bei Datei-Endpoints | DateiController, LieferantDokumentController |
| **A03 – Injection (SQL)** | `'; DROP TABLE x; --` in allen Suchparametern | Alle Controller mit Suchfunktion |
| **A03 – Injection (XSS)** | `<script>alert(1)</script>` in Textfeldern | Alle Controller mit Textfeldern |
| **A04 – Insecure Design** | Mass-Assignment: unbekannte Felder ignoriert | Alle POST/PUT-Endpoints |
| **A08 – Data Integrity** | Ungültige Pflichtfelder → 400 | Alle POST/PUT-Endpoints |

### Dedizierte Sicherheits-Testklassen

| Testklasse | Inhalt |
|---|---|
| `DateiUploadSecurityTest.java` | Gefährliche Dateitypen, Größenlimits, Path-Traversal |
| `InputValidationSecurityTest.java` | Überlange Eingaben, SQL-Injection, XSS über alle Endpoints |
| `EmailSanitizationSecurityTest.java` | HTML-Injection in E-Mail-Inhalten, Anhang-Manipulation |

---

## 5. Repository-Tests (Ergänzungen)

Repositories mit Custom Queries oder komplexen Finder-Methoden benötigen `@DataJpaTest`-Tests.

| Repository | Testfälle | Priorität |
|---|---|---|
| **EmailRepository** | findByProcessingStatus, Volltextsuche, Zeitraum-Filter | 🔴 Hoch |
| **ZeitbuchungRepository** | findOffeneBuchungen, Monats-Aggregation | 🔴 Hoch |
| **KundeRepository** | Suche nach Name, Kundennummer, Freitextsuche | 🟠 Mittel |
| **LieferantenRepository** | findByDomain, Freitextsuche | 🟠 Mittel |
| **MitarbeiterRepository** | findByAbteilung, aktive Mitarbeiter | 🟡 Mittel |
| **GeschaeftsdokumentRepository** | findByProjekt, Nummernsuche | 🟡 Mittel |
| **ZeitkontoKorrekturRepository** | findAktiveByMitarbeiter, Jahres-Summe | 🟡 Mittel |

---

## 6. Integrationstests

Neben Unit-Tests sind für kritische Workflows Ende-zu-Ende-Integrationstests notwendig.

| IT-Klasse | Workflow | Priorität |
|---|---|---|
| `RechnungsWorkflowIT.java` | Angebot → Rechnung → Zahlung → Abschluss | 🔴 Hoch |
| `EmailImportWorkflowIT.java` | Import → Spam-Check → Auto-Assign → Anhang-Verarbeitung | 🟠 Mittel |
| `ZeiterfassungWorkflowIT.java` | Buchung → Auto-Stop → Korrektur → Monatssaldo | 🟠 Mittel |
| `MietabrechnungWorkflowIT.java` | Kostenverteilung → Abrechnung → Export | 🟡 Mittel |

---

## 7. Umsetzungs-Reihenfolge

| Schritt | Phase | Neue Testdateien | Geschätzte Testfälle |
|---|---|---|---|
| 1 | **Phase 1** – Kritische Geschäftslogik ✅ | 7 Dateien | 91 Tests |
| 2 | **Phase 2** – Email-System ✅ | 5 Dateien | 62 Tests |
| 3 | **Phase 3** – Stammdaten & Mapper ✅ | 10 Dateien | 102 Tests |
| 4 | **Phase 4** – Controller + Sicherheit ✅ | 11 Dateien | 162 Tests |
| 5 | **Phase 5** – Frontend ✅ | 14 Dateien | 100 Tests |
| 6 | **Ergänzend** – Repositories | ~7 Dateien | ~25 Tests |
| 7 | **Ergänzend** – Integrationstests | ~4 Dateien | ~15 Tests |
| | **Gesamt** | **~65 Testdateien** | **~360 Tests** |

---

## 8. Testbare Architektur – Richtlinien

Damit neue Tests effizient geschrieben werden können:

1. **Constructor Injection** beibehalten – ermöglicht einfaches Mocking
2. **Keine statischen Abhängigkeiten** – Services immer über DI beziehen
3. **Kleine Methoden** – komplexe Logik in private Hilfsmethoden aufteilen
4. **DTOs an Systemgrenzen** – Entities nie direkt exponieren
5. **External Services mocken** – IMAP, Gemini-KI, REST-APIs immer via Interface/Mock testen

---

## 9. Coverage-Ziele

| Schicht | Aktuell (geschätzt) | Ziel Phase 1–2 | Ziel Phase 3–4 | Endziel |
|---|---|---|---|---|
| **Service** | ~55 % | ✅ ≥60 % | ≥ 75 % | ≥ 80 % |
| **Controller** | ~62 % | ~62 % | ✅ ≥60 % | ≥ 70 % |
| **Repository** | ~8 % | ~8 % | ≥ 30 % | ≥ 40 % |
| **Mapper** | ~11 % | ✅ ≥80 % | ≥ 80 % | ≥ 90 % |
| **Frontend** | ~20 % | ~20 % | ≥ 20 % | ≥ 40 % |

---

## 10. Tooling & Frameworks

| Tool | Zweck | Schicht |
|---|---|---|
| **JUnit 5** | Test-Framework | Backend |
| **Mockito** | Mocking (`@Mock`, `@InjectMocks`) | Backend – Unit |
| **MockMvc** | REST-Controller-Tests (`@WebMvcTest`) | Backend – Controller |
| **AssertJ** | Fluent Assertions | Backend |
| **H2** | In-Memory-DB (`@DataJpaTest`) | Backend – Repository |
| **Awaitility** | Asynchrone Tests | Backend – Scheduled Tasks |
| **Vitest** | Test-Runner | Frontend |
| **@testing-library/react** | Komponenten-Tests | Frontend |
| **vi.fn()** | Mock-Funktionen | Frontend |

---

*Erstellt am: 10.03.2026*
*Zuletzt aktualisiert: 10.03.2026 – Phase 1–5 abgeschlossen (517 neue Tests, davon 162 Controller + 100 Frontend)*
*Basiert auf Codebase-Analyse mit ~509 Java-Quelldateien und ~70 Frontend-Komponenten.*
