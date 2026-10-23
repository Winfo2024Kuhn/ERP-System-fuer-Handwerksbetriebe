# Testplan – ERP-System für Handwerksbetriebe

> **Ziel:** Systematische Erhöhung der Testabdeckung auf ein tragfähiges Niveau (≥ 60 % Backend / ≥ 40 % Frontend). Ausgangspunkt im März 2026 waren ~7 %.
>
> **Stand:** Oktober 2026. Die Phasen 1–5 (März 2026) sind abgeschlossen; seitdem ist die Testbasis stark gewachsen. Die Abdeckung in Prozent wird derzeit nicht automatisch geprüft (siehe Abschnitt 9).

Verbindliche Testregeln stehen in `docs/agent instructions/docs/TESTING_SECURITY.md` und `.github/DEVELOPMENT.md`.

---

## 1. Ist-Zustand (Stand 12.10.2026)

Gezählt wurden Testdateien und Testmethoden (`@Test` + `@ParameterizedTest`, jeder parametrisierte Test einmal) bzw. `it(`/`test(`-Aufrufe im Frontend.

| Kategorie | Quelldateien | Testdateien | Tests |
|---|---|---|---|
| **Controller** | 100 | 101 | 1.280 |
| **Service** | 246 | 230 | 3.173 |
| **Repository** | 135 | 41 | 149 |
| **Mapper** | 8 (+2 in `service/`) | 8 | 75 |
| **Config / Sicherheit** | 28 | 34 | 228 |
| **Utility / Helper** | 15 | 15 | 131 |
| **Domain** | 189 | 13 | 65 |
| **Sonstige** (event, tools, cli, dto, `org.example.email`) | – | 7 | 51 |
| **Backend gesamt** | **997** | **449** | **5.152** |
| **Frontend (react-pc-frontend)** – Unit | ~380 | 234 | ~2.540 |
| **Frontend (react-pc-frontend)** – E2E (Playwright) | | 74 Specs | ~337 |
| **Frontend (react-zeiterfassung)** – Unit | ~66 | 39 | ~400 |
| **Frontend (react-zeiterfassung)** – E2E (Playwright) | | 12 Specs | ~50 |

### Testarten im Backend

| Art | Umfang | Hinweis |
|---|---|---|
| Unit-Tests mit Mockito (`MockitoExtension`) | 147 Dateien | Schwerpunkt Service-Schicht |
| Controller-Tests (`@WebMvcTest` + MockMvc) | 91 Dateien | inkl. 10 Security-Konfigurations-Tests |
| Repository-/JPA-Tests (`@DataJpaTest`, H2) | 62 Dateien | auch für Services mit echten Abfragen |
| `@SpringBootTest` | 5 Dateien | z. B. `ActuatorHealthEndpointTest`, `OeffentlichePfadeCsrfTest` |
| Sicherheits-Tests (`*SecurityTest`) | 23 Dateien | siehe Abschnitt 4 |
| Integrationstests (`*IntegrationTest`) | 11 Dateien | laufen mit `./mvnw test` |
| PostgreSQL-Tests | `DatenbankKompatibilitaet*Test` (6), `MonatsabschlussPostgresTest`, `MonatsabschlussSammelPostgresTest`, `DatevExportPostgresTest` | nur mit System-Property, siehe Abschnitt 6 |
| Mail-Tests mit GreenMail | `PostfachMailserverIntegrationTest`, `FreigabeCodeIntegrationTest` | |

### Bereits getestete Module ✅ (Auswahl)

**Controller:** AnfrageController, ArtikelController, AusgangsGeschaeftsDokumentController, DateiController, EmailController, FormularTemplateController, KostenVerteilungController, KundeController, LeistungController, LieferantDokumentController, LieferantenController, MietabrechnungController, MietobjektController, MitarbeiterController, OffenePostenController, ProjektController, ZeitverwaltungController, UrlaubsantragController, LangzeitkrankmeldungController, VerrechnungslohnController

**Service:** AnfrageService, BestellungService (+ Mapping + PDF), ArtikelImportService, ArtikelMatchingService, FeiertagService, DateiSpeicherService, MonatsSaldoService (+ Warmup), InquiryDetectionService, OfferPriceService, OutOfOfficeResponder, RechnungPdfService, StuecklistePdfService, ProjektManagementService, ZeitkontoService, MietabrechnungService, AusgangsGeschaeftsDokumentService, ZugferdExtractorService, ZugferdErstellService, KostenVerteilungService, ZeitkontoKorrekturService, ZeitbuchungAutoStopService, SpamFilterService, EmailAutoAssignmentService, EmailImportService, EmailAttachmentProcessingService, VendorInvoiceIntegrationService, ArbeitsgangManagementService, BwaService, MietobjektService, ZeiterfassungApiService (Zeitkonto, Nebenläufigkeit, verspäteter Stop), TagesSollService, UrlaubsantragService, LangzeitkrankmeldungService, Monatsabschluss-Services, DatevExportService, LodasDateiWriter, VerrechnungslohnService

**Repository:** ArtikelRepository (+ `ArtikelCascadeDeletionTest`), AnfrageRepository (+ Search), ProjektDokumentRepository, WerkstoffRepository, KundeRepository (Max-Kundennummer, `KundenSucheTest`), LieferantenRepository (Name), MitarbeiterRepository (`MitarbeiterMenschenRepositoryTest`)

**Mapper:** ProjektMapper, AnfrageMapper, KundeMapper, LieferantMapper, LeistungMapper, ArbeitsgangMapper, MieteMapper, ProduktkategorieMapper

**Utility:** EmailHtmlSanitizer, EmailAiPostProcessor (jeweils + `…LueckenTest`)

**Frontend (react-pc-frontend):** u. a. Button, Input, Card, Label, SelectCustom, DatePicker, ImageViewer, Dialog, ConfirmDialog, Toast, cn()-Utility, Seiten wie Monatsabschluss, Zeitkonten, Langzeitkrankmeldungen, Urlaubsanträge, Verrechnungslohn-Rechner

**Frontend (react-zeiterfassung):** u. a. MobileDatePicker, NetworkStatusBadge, ImageViewer, OfflineService, Dashboard, Zeiterfassung, Tagesbuchungen, Urlaubsantrag, Spracheingabe (`VoiceInputButton`), Token-Transport (`mobileTransport`)

**Bekannte Lücken:** `EmailAiServiceTest` ist vollständig auskommentiert (0 aktive Tests); `ProjektManagementServiceIntegrationTest.java` ist eine leere Datei; `ProjektManagementServiceIT` (2 Tests) läuft nie, weil Surefire nur `*Test`/`*Tests`/`Test*`/`*TestCase` ausführt.

---

## 2. Priorisierung

Die Test-Erstellung erfolgte im März 2026 in **5 Phasen**, geordnet nach Geschäftsrisiko und Komplexität. Die Testklassen wurden seitdem erweitert; die Zahlen unten sind der Stand bei Abschluss der Phase.

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

Die Tabellen zeigen die im März 2026 geplanten Kernfälle. In Klammern steht die heutige Größe der Klasse und die heutige Zahl der Testmethoden.

---

### Phase 1 – Kritische Geschäftslogik ✅ (91 Tests – abgeschlossen 10.03.2026)

#### 1.1 `AusgangsGeschaeftsDokumentService` (~2.800 Zeilen, heute 114 Tests)

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
| 1.1.11 | `ensureAnfrageDokument()` erzeugt Dokument nur einmalig (früher `ensureAngebotDokument()`) | `ensureAnfrageDokument()` | Unit |

#### 1.2 `ZugferdExtractorService` (~600 Zeilen, heute 41 Tests)

**Testdatei:** `ZugferdExtractorServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.2.1 | Extrahiert Rechnungsdaten aus gültiger ZUGFeRD-PDF | `extract()` | Unit |
| 1.2.2 | Parst Positionen mit Menge, Einheit und Betrag | `extract()` | Unit |
| 1.2.3 | Erkennt und berechnet Skonto korrekt | `extract()` | Unit |
| 1.2.4 | Stellt Umlaute nach Encoding-Problemen wieder her | `extract()` | Unit |
| 1.2.5 | Gibt leere Daten zurück bei Nicht-ZUGFeRD-PDF | `extract()` | Unit |
| 1.2.6 | Fallback-XML-Parsing greift bei fehlerhaftem Hauptparser | `extract()` | Unit |

#### 1.3 `ZugferdErstellService` (~210 Zeilen, heute 22 Tests)

**Testdatei:** `ZugferdErstellServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.3.1 | Erzeugt ZUGFeRD-PDF mit eingebetteten Rechnungsdaten | `erzeuge()` | Unit |
| 1.3.2 | Berechnet MwSt. korrekt (19 %) | `erzeuge()` | Unit |
| 1.3.3 | Nutzt Fallback-Datum wenn kein Rechnungsdatum gesetzt | `erzeuge()` | Unit |

#### 1.4 `KostenVerteilungService` (Miet-Modul, ~230 Zeilen, heute 14 Tests)

**Testdatei:** `service/miete/KostenVerteilungServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.4.1 | Erstellt Kostenstelle mit korrekter Zuordnung | `saveKostenstelle()` | Unit |
| 1.4.2 | Erstellt Kostenposition mit BigDecimal-Rundung | `saveKostenposition()` | Unit |
| 1.4.3 | Kopiert Vorjahres-Positionen korrekt | `copyKostenpositionenVonVorjahr()` | Unit |
| 1.4.4 | Verhindert Doppel-Kopie bei existierenden Positionen | `copyKostenpositionenVonVorjahr()` | Unit |
| 1.4.5 | Verteilungsschlüssel validiert Mietpartei-Zuordnung | `saveVerteilungsschluessel()` | Unit |
| 1.4.6 | Berechnungsmethode BETRAG vs. VERBRAUCHSFAKTOR korrekt | `saveVerteilungsschluessel()` | Unit |

#### 1.5 `ZeitkontoKorrekturService` (~280 Zeilen, heute 21 Tests)

**Testdatei:** `ZeitkontoKorrekturServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.5.1 | Erstellt Korrektur mit Audit-Eintrag (ERSTELLT) | `erstelleKorrektur()` | Unit |
| 1.5.2 | Änderung erzeugt neuen Audit-Eintrag (GEAENDERT) | `aendereKorrektur()` | Unit |
| 1.5.3 | Stornierung setzt Status statt zu löschen (GoBD) | `storniereKorrektur()` | Unit |
| 1.5.4 | Versionsnummer wird bei Änderung inkrementiert | `aendereKorrektur()` | Unit |
| 1.5.5 | Lehnt Korrektur ohne Begründung ab | `erstelleKorrektur()` | Unit |
| 1.5.6 | Summiert aktive Korrekturen pro Jahr korrekt | `summiereAktiveKorrekturen()` | Unit |
| 1.5.7 | Audit-Historie enthält alle Einträge chronologisch | `getAuditHistorie()` | Unit |
| 1.5.8 | Stornierte Korrekturen werden bei Summierung ignoriert | `summiereAktiveKorrekturen()` | Unit |

#### 1.6 `ZeitbuchungAutoStopService` (~130 Zeilen, heute 13 Tests + `ZeitbuchungAutoStopRepositoryTest`)

**Testdatei:** `ZeitbuchungAutoStopServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 1.6.1 | Stoppt offene Buchungen nach Ende des Buchungszeitfensters bzw. nach Mitternacht | `pruefUndStoppeOffeneBuchungen()` | Unit |
| 1.6.2 | Ohne Zeitfenster: Endzeit 23:59 des Starttags bei Tagesüberschreitung | `autoStoppeWennNoetig()` | Unit |
| 1.6.3 | Berechnet Stunden korrekt aus Zeitspanne | `autoStoppeWennNoetig()` | Unit |
| 1.6.4 | Erzeugt Audit-Eintrag mit Quelle SYSTEM | `autoStoppeWennNoetig()` | Unit |
| 1.6.5 | Invalidiert Monatssaldo-Cache nach Stopp | `autoStoppeWennNoetig()` | Unit |

---

### Phase 2 – Email-System & Kommunikation ✅ (62 Tests – abgeschlossen 10.03.2026)

#### 2.1 `SpamFilterService` (~1.000 Zeilen, heute 59 Tests)

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

#### 2.2 `EmailAutoAssignmentService` (~340 Zeilen, heute 13 Tests)

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

#### 2.3 `EmailImportService` (~1.600 Zeilen, heute 49 Tests)

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

#### 2.4 `EmailAttachmentProcessingService` (~690 Zeilen, heute 25 Tests)

**Testdatei:** `EmailAttachmentProcessingServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 2.4.1 | Verarbeitet PDF-Anhänge mit Lieferant-Zuordnung | `processLieferantAttachments()` | Unit |
| 2.4.2 | Dokumenttyp-Erkennung: KI > Nummernmuster > Default | `processLieferantAttachments()` | Unit |
| 2.4.3 | Dateipfad-Auflösung mit 3 Fallback-Strategien | `processLieferantAttachments()` | Unit |
| 2.4.4 | Ignoriert bereits verarbeitete Anhänge | `processLieferantAttachments()` | Unit |
| 2.4.5 | Erstellt Dokument atomar (erst in-memory, dann DB) | `processLieferantAttachments()` | Unit |

#### 2.5 `VendorInvoiceIntegrationService` (~440 Zeilen, heute 6 Tests)

**Testdatei:** `VendorInvoiceIntegrationServiceTest.java`

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 2.5.1 | Erkennt doppelte Rechnungen anhand Dateiname | `fetchMicrosoftInvoices()` | Unit |
| 2.5.2 | Loggt fehlende Lieferanten-Zuordnung | `fetchMicrosoftInvoices()` | Unit |
| 2.5.3 | Status-Dashboard zeigt korrekte Konfigurations-Info | `getIntegrationStatus()` | Unit |

---

### Phase 3 – Stammdaten-Services & Mapper ✅ (102 Tests – abgeschlossen 10.03.2026)

#### 3.1 `ArbeitsgangManagementService` (~85 Zeilen, heute 10 Tests)

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

#### 3.2 `BwaService` (~115 Zeilen, heute 12 Tests)

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

#### 3.3 `MietobjektService` (~65 Zeilen, heute 13 Tests)

**Testdatei:** `service/miete/MietobjektServiceTest.java`

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

#### 3.4 `AnfrageMapper` (früher `AngebotMapper`)

**Testdatei:** `AnfrageMapperTest.java` (heute 5 Tests)

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.4.1 | Mappt alle Felder inkl. Kunde-Daten | `toAnfrageResponseDto()` | Unit |
| 3.4.2 | Gibt null bei null-Anfrage zurück | `toAnfrageResponseDto()` | Unit |
| 3.4.3 | Mappt Anfrage ohne Kunde | `toAnfrageResponseDto()` | Unit |
| 3.4.4 | Dedupliziert E-Mails aus Kunde und Anfrage | `toAnfrageResponseDto()` | Unit |
| 3.4.5 | Behandelt null-Anrede korrekt | `toAnfrageResponseDto()` | Unit |

#### 3.5 `KundeMapper`

**Testdatei:** `KundeMapperTest.java` (heute 9 Tests)

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

**Testdatei:** `LieferantMapperTest.java` (heute 8 Tests)

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

**Testdatei:** `LeistungMapperTest.java` (heute 9 Tests)

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

**Testdatei:** `ArbeitsgangMapperTest.java` (heute 4 Tests)

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.8.1 | Mappt alle Felder inkl. Stundensatz | `toArbeitsgangResponseDto()` | Unit |
| 3.8.2 | Gibt null bei null-Entity | `toArbeitsgangResponseDto()` | Unit |
| 3.8.3 | Mappt ohne Abteilung | `toArbeitsgangResponseDto()` | Unit |
| 3.8.4 | Mappt ohne Stundensatz | `toArbeitsgangResponseDto()` | Unit |

#### 3.9 `MieteMapper` (~380 Zeilen, heute 24 Tests)

**Testdatei:** `MieteMapperTest.java`

Der Mapper bietet überladene `toDto(...)`-Methoden je Typ (Mietobjekt, Mietpartei, Raum, Verbrauchsgegenstand, Zählerstand, Kostenstelle, Verteilungsschlüssel, Kostenposition).

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.9.1 | Mappt Mietobjekt mit allen Feldern | `toDto(Mietobjekt)` | Unit |
| 3.9.2 | Gibt null bei null-Mietobjekt | `toDto(Mietobjekt)` | Unit |
| 3.9.3 | Mappt Mietobjekt ohne Räume | `toDto(Mietobjekt)` | Unit |
| 3.9.4 | Mappt Mietpartei mit allen Feldern | `toDto(Mietpartei)` | Unit |
| 3.9.5 | Gibt null bei null-Mietpartei | `toDto(Mietpartei)` | Unit |
| 3.9.6 | Mappt Raum mit allen Feldern | `toDto(Raum)` | Unit |
| 3.9.7 | Gibt null bei null-Raum | `toDto(Raum)` | Unit |
| 3.9.8 | Mappt Verbrauchsgegenstand | `toDto(Verbrauchsgegenstand)` | Unit |
| 3.9.9 | Gibt null bei null-Verbrauchsgegenstand | `toDto(Verbrauchsgegenstand)` | Unit |
| 3.9.10 | Mappt Zählerstand mit allen Feldern | `toDto(Zaehlerstand)` | Unit |
| 3.9.11 | Gibt null bei null-Zählerstand | `toDto(Zaehlerstand)` | Unit |
| 3.9.12 | Mappt Zählerstand ohne Verbrauchsgegenstand | `toDto(Zaehlerstand)` | Unit |
| 3.9.13 | Mappt Kostenstelle mit allen Feldern | `toDto(Kostenstelle)` | Unit |
| 3.9.14 | Gibt null bei null-Kostenstelle | `toDto(Kostenstelle)` | Unit |
| 3.9.15 | Mappt Kostenstelle ohne Positionen | `toDto(Kostenstelle)` | Unit |
| 3.9.16 | Mappt Kostenstelle ohne Schlüssel | `toDto(Kostenstelle)` | Unit |
| 3.9.17 | Mappt Verteilungsschlüssel mit Einträgen | `toDto(Verteilungsschluessel)` | Unit |
| 3.9.18 | Gibt null bei null-Schlüssel | `toDto(Verteilungsschluessel)` | Unit |
| 3.9.19 | Mappt Schlüssel ohne Einträge | `toDto(Verteilungsschluessel)` | Unit |
| 3.9.20 | Mappt Kostenposition mit berechnetem Betrag | `toDto(Kostenposition)` | Unit |
| 3.9.21 | Gibt null bei null-Position | `toDto(Kostenposition)` | Unit |
| 3.9.22 | Fängt Berechnungs-Exception ab (Fallback null) | `toDto(Kostenposition)` | Unit |
| 3.9.23 | Manueller Betrags-Override hat Vorrang | `toDto(Kostenposition)` | Unit |
| 3.9.24 | Default-Berechnungsmethode BETRAG | `toDto(Kostenposition)` | Unit |

#### 3.10 `ProduktkategorieMapper`

**Testdatei:** `ProduktkategorieMapperTest.java` (heute 8 Tests)

| # | Testfall | Methode | Typ |
|---|---|---|---|
| 3.10.1 | Mappt alle Felder inkl. isLeaf | `toProduktkategorieResponseDto()` | Unit |
| 3.10.2 | Gibt null bei null-Entity | `toProduktkategorieResponseDto()` | Unit |
| 3.10.3 | Erkennt Blatt-Kategorie korrekt | `toProduktkategorieResponseDto()` | Unit |
| 3.10.4 | Baut hierarchischen Pfad (3 Ebenen) | `toProduktkategorieResponseDto()` | Unit |
| 3.10.5 | Einzelner Pfad ohne Eltern | `toProduktkategorieResponseDto()` | Unit |
| 3.10.6 | BildUrl mit Slash-Prefix | `toProduktkategorieResponseDto()` | Unit |
| 3.10.7 | BildUrl ohne Slash-Prefix | `toProduktkategorieResponseDto()` | Unit |
| 3.10.8 | Null/Blank BildUrl wird behandelt | `toProduktkategorieResponseDto()` | Unit |

---

### Phase 4 – Controller-Schicht (REST-APIs) ✅ (162 Tests – abgeschlossen 10.03.2026)

Für jeden Controller sind sowohl Happy-Path- als auch Sicherheitstests erforderlich.

#### 4.1 In Phase 4 abgedeckte Controller

| Controller | Testdatei | Kernmethoden | Tests heute |
|---|---|---|---|
| **EmailController** | `EmailControllerTest.java` | GET /api/email/from-addresses, POST /api/email/preview, POST /api/email/send | 13 |
| **AusgangsGeschaeftsDokumentController** | `AusgangsGeschaeftsDokumentControllerTest.java` | Erstellen, Buchen, Aktualisieren | 45 |
| **MietabrechnungController** | `controller/miete/MietabrechnungControllerTest.java` | Abrechnung erstellen/exportieren | 3 |
| **MietobjektController** | `controller/miete/MietobjektControllerTest.java` | CRUD Mietobjekte + Parteien | 12 |
| **KostenVerteilungController** | `controller/miete/KostenVerteilungControllerTest.java` | CRUD + Vorjahres-Kopie | 17 |
| **FormularTemplateController** | `FormularTemplateControllerTest.java` (+ `…SecurityTest`) | Template-CRUD | 21 |
| **LeistungController** | `LeistungControllerTest.java` | CRUD + Textbausteine | 12 |
| **MitarbeiterController** | `MitarbeiterControllerTest.java` | CRUD Mitarbeiter | 23 |
| **OffenePostenController** | `OffenePostenControllerTest.java` | Offene Posten Übersicht | 23 |
| **LieferantDokumentController** | `LieferantDokumentControllerTest.java` | Dokument-Upload/-Download | 29 |

Hinweis: Der ursprünglich geplante `ZeiterfassungControllerTest` (POST /api/zeitbuchungen, GET /api/zeitkonto) wurde nie angelegt – diese Endpoints gibt es nicht. `ZeiterfassungController` leitet nur die Seite `/zeiterfassung` an die Mobile-App weiter. Zeitbuchungen und Zeitkonten am PC laufen über `ZeitverwaltungController` (`/api/zeitverwaltung/*`), getestet in `ZeitverwaltungControllerTest` (seit Juni 2026, 8 Tests). Die Handy-API (`/api/zeiterfassung/*`, `ZeiterfassungApiController`) hat keinen eigenen Controller-Test; sie ist über `ZeiterfassungApiService*Test` sowie `MobileApiAuthenticationTest`/`MobileApiPolicyTest` abgedeckt.

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

#### 5.1 react-pc-frontend (Phase 5: 11 Testdateien, 77 Tests – heute 234 Testdateien)

**Setup:** Vitest 4.1, @testing-library/react 16.3, @testing-library/jest-dom, @testing-library/user-event, jsdom 28.1, @vitest/coverage-v8 4.1 (`vitest.config.ts`, Setup-Datei `src/setupTests.ts`)

| Testdatei | Komponente | Tests heute | Schwerpunkte |
|---|---|---|---|
| `button.test.tsx` | Button | 10 | Varianten (default/outline/ghost/secondary), Größen, onClick, disabled |
| `input.test.tsx` | Input | 8 | CSS-Klassen, Attribute, Ref-Forwarding, disabled/readonly |
| `card.test.tsx` | Card | 3 | Children-Rendering, CSS-Klassen, className-Merge |
| `label.test.tsx` | Label | 4 | Text, CSS, className, htmlFor |
| `select-custom.test.tsx` | SelectCustom | 19 | Platzhalter, Auswahl, Dropdown öffnen/schließen, onChange, disabled |
| `datepicker.test.tsx` | DatePicker | 18 | Platzhalter, formatiertes Datum, Kalender, deutsche Monate, Löschen/Heute |
| `image-viewer.test.tsx` | ImageViewer | 14 | Null-Handling, Bildanzeige, Schließen, Galerie-Navigation |
| `dialog.test.tsx` | Dialog | 15 | Open/Closed-State, Inhalt, Schließen, onOpenChange, Footer |
| `confirm-dialog.test.tsx` | ConfirmDialog | 9 | Dialog-Anzeige, Custom Labels, Bestätigung/Abbruch |
| `toast.test.tsx` | Toast | 10 | Success/Error/Warning/Info, Provider, mehrere gleichzeitig |
| `utils.test.ts` | cn()-Utility | 7 | Klassen-Merge, Konflikte, bedingte Klassen, Tailwind-Merge |

#### 5.2 react-zeiterfassung (Phase 5: 3 Testdateien, 23 Tests – heute 39 Testdateien)

**Setup:** Identisch zu react-pc-frontend + navigator.onLine Mock

| Testdatei | Komponente | Tests heute | Schwerpunkte |
|---|---|---|---|
| `MobileDatePicker.test.tsx` | MobileDatePicker | 14 | Platzhalter, Formatierung, Label, Kalender, deutsche Monate, Heute/Schließen |
| `NetworkStatusBadge.test.tsx` | NetworkStatusBadge | 9 | Online/Offline/Syncing/Error-States, onSync, Compact-Modus |
| `image-viewer.test.tsx` | ImageViewer | 15 | Null-Rendering, Bildanzeige, Galerie-Zähler, Zoom-Controls |

#### 5.3 E2E-Tests mit Playwright (seit Phase 5 hinzugekommen)

| App | Ort | Umfang | Ausführung |
|---|---|---|---|
| react-pc-frontend | `react-pc-frontend/e2e/` | 74 Specs, ~337 Tests | `npm run test:e2e`; drei Bildschirmgrößen (1440×900, 1536×960, 1920×1080); gegen `vite build` + `vite preview`, `/api`-Aufrufe sind gestubbt (kein Backend nötig) |
| react-zeiterfassung | `react-zeiterfassung/e2e/` | 12 Specs, ~50 Tests | `npm run test:e2e`; Projekt „handy“ (iPhone 15, Chromium) gegen den Vite-Dev-Server |

---

## 4. Sicherheitstests (OWASP Top 10)

Gemäß `docs/agent instructions/docs/TESTING_SECURITY.md` müssen alle neuen Endpoints mit Sicherheitstests abgedeckt werden (SQL-Injection, XSS, ungültige IDs, leere/überlange Eingaben, Path-Traversal und gefährliche Dateitypen bei Datei-Endpoints).

### Pflicht-Sicherheitstests pro Endpoint

| OWASP | Testfall | Wo integrieren |
|---|---|---|
| **A01 – Broken Access Control** | Path-Traversal bei Datei-Endpoints; Rechte-/Rollenprüfung | DateiController, LieferantDokumentController, `*SecurityTest` |
| **A03 – Injection (SQL)** | `'; DROP TABLE x; --` in allen Suchparametern | Alle Controller mit Suchfunktion |
| **A03 – Injection (XSS)** | `<script>alert(1)</script>` in Textfeldern | Alle Controller mit Textfeldern |
| **A04 – Insecure Design** | Mass-Assignment: unbekannte Felder ignoriert | Alle POST/PUT-Endpoints |
| **A08 – Data Integrity** | Ungültige Pflichtfelder → 400 | Alle POST/PUT-Endpoints |

### Vorhandene Sicherheits-Testklassen

Die früher geplanten Sammelklassen `DateiUploadSecurityTest`, `InputValidationSecurityTest` und `EmailSanitizationSecurityTest` gibt es nicht. Stattdessen prüfen gezielte Klassen je Bereich:

| Bereich | Testklassen (Auswahl) |
|---|---|
| Rechte pro Endpoint (`*SecurityTest`, 23 Klassen) | z. B. `DatevExportSecurityTest`, `DatevKonfigurationSecurityTest`, `MonatsabschlussSecurityTest`, `ZeiterfassungEinstellungenSecurityTest`, `FormularTemplateControllerSecurityTest`, `LieferantDokumentRechteSecurityTest`, `TelefonControllerSecurityTest`, `InternalApiSecurityTest` |
| Mobile-App / Token | `MobileApiAuthenticationTest`, `MobileApiPolicyTest`, `TokenAttemptLimiterTest`, `MobileBelegSecurityTest`, `MobileDateiObjectSecurityTest`, `MobileUrlaubsantragSecurityTest`, `PublicMobileIngressTest` |
| CSRF, CORS, Cookies, Netzgrenze | `OeffentlichePfadeCsrfTest`, `LoginCsrfUndDnsRebindingTest`, `CorsSicherheitTest`, `ApiCorsConfigurationSourceTest`, `SessionCookieSameSiteTest`, `NetzgrenzeVorAnmeldungTest`, `ZeiterfassungNetworkBoundaryTest` |
| HTML-/E-Mail-Bereinigung | `EmailHtmlSanitizerTest` (+ `…LueckenTest`) |

---

## 5. Repository-Tests (Ergänzungen)

Repositories mit Custom Queries oder komplexen Finder-Methoden benötigen `@DataJpaTest`-Tests (H2). Heute gibt es 41 Testdateien unter `repository/`.

| Repository | Testfälle | Priorität | Stand |
|---|---|---|---|
| **EmailRepository** | findByProcessingStatus, Volltextsuche, Zeitraum-Filter | 🔴 Hoch | offen |
| **ZeitbuchungRepository** | findOffeneBuchungen, Monats-Aggregation | 🔴 Hoch | offen (nur `ZeitbuchungAutoStopRepositoryTest` mit 1 Test) |
| **KundeRepository** | Suche nach Name, Kundennummer, Freitextsuche | 🟠 Mittel | teilweise (`KundeRepositoryMaxKundennummerTest`, `KundenSucheTest`) |
| **LieferantenRepository** | findByDomain, Freitextsuche | 🟠 Mittel | teilweise (`LieferantenRepositoryNameTest`) |
| **MitarbeiterRepository** | findByAbteilung, aktive Mitarbeiter | 🟡 Mittel | teilweise (`MitarbeiterMenschenRepositoryTest`) |
| **ZeitkontoKorrekturRepository** | findAktiveByMitarbeiter, Jahres-Summe | 🟡 Mittel | offen |

---

## 6. Integrationstests

Neben Unit-Tests sind für kritische Workflows Integrationstests notwendig. Sie heißen `*IntegrationTest.java` (laufen mit `./mvnw test`). Klassen mit Endung `*IT.java` werden **nicht** ausgeführt, weil kein Failsafe-Plugin konfiguriert ist.

### Geplante Workflow-Tests (noch nicht umgesetzt)

| IT-Klasse | Workflow | Priorität |
|---|---|---|
| `RechnungsWorkflowIT.java` | Anfrage → Rechnung → Zahlung → Abschluss | 🔴 Hoch |
| `EmailImportWorkflowIT.java` | Import → Spam-Check → Auto-Assign → Anhang-Verarbeitung | 🟠 Mittel |
| `ZeiterfassungWorkflowIT.java` | Buchung → Auto-Stop → Korrektur → Monatssaldo | 🟠 Mittel |
| `MietabrechnungWorkflowIT.java` | Kostenverteilung → Abrechnung → Export | 🟡 Mittel |

Bei der Umsetzung als `*IntegrationTest.java` benennen, sonst laufen sie nicht.

### Vorhandene Integrationstests

`BelegscannerZuordnungIntegrationTest`, `DateiControllerBildIntegrationTest`, `EmailThreadBackfillIntegrationTest`, `FreigabeCodeIntegrationTest`, `FritzBoxTelefonAnlageIntegrationTest`, `PostfachMailserverIntegrationTest` (GreenMail), `RechnungLieferscheinZuordnungIntegrationTest`, `TelefonDatenIntegrationTest`, `ZeitkontoPausenIntegrationTest`, `ZeitkontoWechselIntegrationTest` (`ProjektManagementServiceIntegrationTest.java` ist leer).

### Tests gegen echtes PostgreSQL 16

Standardmäßig laufen alle Tests auf H2 (`src/test/resources/application.properties`, Flyway aus). Gegen ein echtes PostgreSQL laufen nur diese Klassen – und nur, wenn die passende System-Property gesetzt ist (sonst werden sie übersprungen):

| Klassen | Property | Inhalt |
|---|---|---|
| `DatenbankKompatibilitaet*Test` (6 Klassen) | `-Dkompat.db.url` (+ `.user`, `.pass`) | Basis-Schema + Flyway-Migrationen, `ddl-auto=validate`, Abfragen und Suche |
| `MonatsabschlussPostgresTest`, `MonatsabschlussSammelPostgresTest` | `-Dzeitkonto.pg.url` | Monatsabschluss unter konkurrierenden Zugriffen |
| `DatevExportPostgresTest` | `-Ddatev.pg.url` | DATEV-Export bei konkurrierenden Änderungen |

Beispiel: `./mvnw test "-Dtest=DatenbankKompatibilitaet*Test" -Dkompat.db.url=jdbc:postgresql://localhost:5432/kompat -Dkompat.db.user=kompat -Dkompat.db.pass=kompat` (Wegwerf-Container `postgres:16`).

### CI (`.github/workflows/pr-ci.yml`, „PR Quality Checks“)

Läuft bei jedem Pull Request auf `main`. Das Merge-Gate „PR Quality Gate“ verlangt, dass alle Jobs grün sind:

| Job | Befehl |
|---|---|
| Backend Tests | `./mvnw test` (JDK 25) |
| Datenbank-Kompatibilitaet (PostgreSQL) | Service `postgres:16`; `DatenbankKompatibilitaet*Test`, danach `MonatsabschlussPostgresTest`, `MonatsabschlussSammelPostgresTest`, `DatevExportPostgresTest`; Prüfschritt: Tests > 0 und nichts übersprungen |
| PC Frontend Lint / Unit Tests / Build | `npm run lint`, `npm test`, `npm run build` (Node 24) |
| PC Frontend E2E (3 Shards) | `npm run typecheck:e2e`, `npm run test:e2e -- --shard=N/3` |
| Zeiterfassung Lint / Unit Tests / Build / E2E | `npm run lint`, `npm test`, `npm run build`, `npm run test:e2e` |

`docker-image.yml` baut nach dem Merge das Image und prüft mit `deployment/docker-server/starttest.sh` ein Upgrade von `:stable` (PostgreSQL + Flyway, `/actuator/health`); eigene Tests führt es nicht aus.

---

## 7. Umsetzungs-Reihenfolge

| Schritt | Phase | Neue Testdateien | Geschätzte Testfälle |
|---|---|---|---|
| 1 | **Phase 1** – Kritische Geschäftslogik ✅ | 7 Dateien | 91 Tests |
| 2 | **Phase 2** – Email-System ✅ | 5 Dateien | 62 Tests |
| 3 | **Phase 3** – Stammdaten & Mapper ✅ | 10 Dateien | 102 Tests |
| 4 | **Phase 4** – Controller + Sicherheit ✅ | 11 Dateien | 162 Tests |
| 5 | **Phase 5** – Frontend ✅ | 14 Dateien | 100 Tests |
| 6 | **Ergänzend** – Repositories (Abschnitt 5) – teilweise | ~7 Dateien | ~25 Tests |
| 7 | **Ergänzend** – Workflow-Integrationstests (Abschnitt 6) – offen | ~4 Dateien | ~15 Tests |
| | **Gesamt Plan** | **~58 Testdateien** | **~557 Tests** |

---

## 8. Testbare Architektur – Richtlinien

Damit neue Tests effizient geschrieben werden können:

1. **Constructor Injection** beibehalten – ermöglicht einfaches Mocking
2. **Keine statischen Abhängigkeiten** – Services immer über DI beziehen
3. **Kleine Methoden** – komplexe Logik in private Hilfsmethoden aufteilen
4. **DTOs an Systemgrenzen** – Entities nie direkt exponieren
5. **External Services mocken** – IMAP, KI-Dienste, REST-APIs immer via Interface/Mock testen (Mailserver: GreenMail)
6. **Nur Dummy-Daten** (z. B. Max Mustermann) – Zeit- und Mitarbeiterdaten sind personenbezogen (DSGVO)

---

## 9. Coverage-Ziele

Die Abdeckung wird gemessen, aber nicht erzwungen: `./mvnw test` schreibt mit JaCoCo einen Bericht nach `target/site/jacoco`, `npm run test:coverage` erzeugt den Vitest-Bericht. Es gibt **keine Schwellwerte** (kein JaCoCo-`check`, keine Vitest-Thresholds) und keinen Coverage-Job in der CI. Aktuelle Prozentwerte sind deshalb nicht hinterlegt; die früheren Schätzungen (März 2026) sind veraltet.

| Schicht | Ziel Phase 1–2 | Ziel Phase 3–4 | Endziel |
|---|---|---|---|
| **Service** | ≥60 % | ≥ 75 % | ≥ 80 % |
| **Controller** | – | ≥60 % | ≥ 70 % |
| **Repository** | – | ≥ 30 % | ≥ 40 % |
| **Mapper** | ≥80 % | ≥ 80 % | ≥ 90 % |
| **Frontend** | – | ≥ 20 % | ≥ 40 % |

`TESTING_SECURITY.md` verlangt für die Service-Schicht ≥ 80 % Branch-Coverage je neuer Klasse und für Utilities 100 %; eine projektweite Schwelle gibt es bewusst nicht.

---

## 10. Tooling & Frameworks

| Tool | Zweck | Schicht |
|---|---|---|
| **JUnit 5** | Test-Framework | Backend |
| **Mockito** | Mocking (`@Mock`, `@InjectMocks`, `@MockitoBean`) | Backend – Unit |
| **MockMvc** | REST-Controller-Tests (`@WebMvcTest`) | Backend – Controller |
| **Spring Security Test** | Tests mit Benutzern/Rollen, CSRF | Backend – Sicherheit |
| **AssertJ** | Fluent Assertions | Backend |
| **H2** | In-Memory-DB (`@DataJpaTest`, Standard für alle Tests) | Backend – Repository |
| **PostgreSQL 16** | Kompatibilitäts- und Nebenläufigkeitstests (nur mit System-Property) | Backend – Repository/Integration |
| **GreenMail** | Test-Mailserver (SMTP/IMAP) | Backend – E-Mail |
| **Awaitility** | Asynchrone Tests | Backend – Scheduled Tasks |
| **JaCoCo** | Coverage-Bericht (ohne Schwellwert) | Backend |
| **Vitest** | Test-Runner | Frontend |
| **@testing-library/react** (+ jest-dom, user-event) | Komponenten-Tests | Frontend |
| **vi.fn()** | Mock-Funktionen | Frontend |
| **Playwright** | E2E-Tests im Browser | Frontend |

Befehle: Backend `./mvnw test`; Frontend (jeweils in `react-pc-frontend/` und `react-zeiterfassung/`) `npm test`, `npm run test:coverage`, `npm run test:e2e`, `npm run lint`.

---

*Erstellt am: 10.03.2026*
*Zuletzt aktualisiert: 12.10.2026 – Ist-Zustand, Testklassen, PostgreSQL-Tests, E2E und CI an den Code angeglichen*
*Basiert auf Codebase-Analyse mit 997 Java-Quelldateien (Backend) und ~450 Frontend-Quelldateien.*
