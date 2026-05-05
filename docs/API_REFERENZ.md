# API-Referenz – REST-Endpoint-Übersicht

## Übersicht

Dieses Dokument listet alle REST-Endpoints des Kalkulationsprogramms nach Modul geordnet auf. Der Server läuft standardmäßig auf **Port 8082**.

Basis-URL: `http://localhost:8082`

---

## 1. Ausgangs-Geschäftsdokumente

**Controller:** `AusgangsGeschaeftsDokumentController`  
**Basis-URL:** `/api/ausgangs-dokumente`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{id}` | Einzelnes Dokument abrufen |
| `GET` | `/projekt/{projektId}` | Alle Dokumente eines Projekts |
| `GET` | `/angebot/{angebotId}` | Alle Dokumente eines Angebots |
| `POST` | `/` | Neues Dokument erstellen |
| `PUT` | `/{id}` | Dokument aktualisieren |
| `POST` | `/{id}/buchen` | Dokument buchen (sperrt Rechnungstypen) |
| `POST` | `/{id}/email-versendet` | Nach E-Mail-Versand buchen + Versanddatum |
| `POST` | `/{id}/storno` | Dokument stornieren |
| `DELETE` | `/{id}?begruendung=...` | Entwurf löschen (mit Begründung) |
| `GET` | `/{id}/abrechnungsverlauf` | Abrechnungsverlauf abrufen |

---

## 2. Rechnungsübersicht

**Controller:** `RechnungsuebersichtController`  
**Basis-URL:** `/api/rechnungen`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/ausgang` | Ausgangsrechnungen (gefiltert nach Jahr/Monat/Suche) |
| `GET` | `/eingang` | Eingangsrechnungen (gefiltert nach Jahr/Monat/Suche) |
| `POST` | `/merge-pdf` | Sammel-PDF aus ausgewählten Rechnungen |
| `POST` | `/analyze-upload` | Eingangsrechnung per KI analysieren |
| `POST` | `/import-upload` | Analysierte Eingangsrechnung importieren |

---

## 3. Offene Posten

**Controller:** `OffenePostenController`  
**Basis-URL:** `/api/offene-posten`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/eingang` | Offene Posten Eingang (abteilungsbasiert) |
| `GET` | `/eingang/alle` | Alle offenen Posten Eingang |

---

## 4. Bestellungen

**Controller:** `BestellungController`  
**Basis-URL:** `/api/bestellungen`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/offen` | Offene (unbestellte) Artikel |
| `GET` | `/projekt/{projektId}/pdf` | Bestellungs-PDF generieren |

**Controller:** `BestellungsUebersichtController`  
**Basis-URL:** `/api/bestellungen-uebersicht`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Bestellübersicht |
| `GET` | `/geschaeftsdaten/{dokId}` | Geschäftsdaten einer Bestellung |
| `PUT` | `/geschaeftsdaten/{dokId}` | Geschäftsdaten aktualisieren |

---

## 5. Zeiterfassung

**Controller:** `ZeiterfassungApiController`  
**Basis-URL:** `/api/zeiterfassung`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/stempeln` | Start/Stop-Stempelung |
| `GET` | `/aktiv` | Aktive Buchung des Mitarbeiters |
| `GET` | `/buchungen` | Buchungen für einen Zeitraum |
| `PUT` | `/{id}` | Buchung bearbeiten |
| `DELETE` | `/{id}` | Buchung stornieren |

**Controller:** `ZeitverwaltungController`  
**Basis-URL:** `/api/zeitverwaltung`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/mitarbeiter/{id}/monat` | Monatssaldo eines Mitarbeiters |
| `GET` | `/mitarbeiter/{id}/salden` | Saldenübersicht |
| `GET` | `/team/monat` | Team-Monatssalden |

**Controller:** `ZeitkontoKorrekturController`  
**Basis-URL:** `/api/zeitkonto-korrektur`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/` | Neue Korrektur erstellen |
| `GET` | `/mitarbeiter/{id}` | Korrekturen eines Mitarbeiters |

---

## 6. Angebote

**Controller:** `AngebotController`  
**Basis-URL:** `/api/angebote`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Angebote |
| `GET` | `/{id}` | Einzelnes Angebot |
| `GET` | `/jahre` | Verfügbare Jahre (Filter) |
| `POST` | `/` | Neues Angebot erstellen |
| `GET` | `/{id}/projekt-vorlage` | Projekt-Vorlage generieren |
| `GET` | `/{angebotID}/dokumente` | Angebotsdokumente |
| `POST` | `/{angebotID}/dokumente` | Dokument hochladen |
| `GET` | `/{angebotID}/email-dokumente` | E-Mail-Dokumente |
| `POST` | `/{angebotId}/emails` | E-Mail versenden |
| `GET` | `/{angebotId}/notizen` | Notizen abrufen |
| `POST` | `/{angebotId}/notizen` | Neue Notiz erstellen |
| `POST` | `/{angebotId}/notizen/{notizId}/bilder` | Bild hochladen |
| `POST` | `/zugferd/extract` | ZUGFeRD-Daten extrahieren |
| `POST` | `/zugferd/extract-ai` | KI-basierte Extraktion |
| `POST` | `/{angebotID}/zugferd` | ZUGFeRD-PDF erstellen |

---

## 7. E-Mail

**Controller:** `EmailController`  
**Basis-URL:** `/api/email`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/beautify` | E-Mail-Text per KI verbessern |
| `POST` | `/preview` | E-Mail-Vorschau generieren |
| `POST` | `/preview/angebot` | Angebots-E-Mail-Vorschau |

**Controller:** `EmailSignatureController`  
**Basis-URL:** `/api/email/signatures`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Signaturen |
| `GET` | `/default` | Standard-Signatur |
| `POST` | `/` | Neue Signatur erstellen |

**Controller:** `UnifiedEmailController`  
**Basis-URL:** `/api/unified-email`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Einheitliche E-Mail-Integration |

---

## 8. Lieferanten-Dokumente

**Controller:** `LieferantDokumentController`  
**Basis-URL:** `/api/lieferant-dokumente`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/lieferant/{lieferantId}/reanalyze` | KI-Reanalyse für Lieferant |
| `POST` | `/{dokumentId}/reanalyze` | KI-Reanalyse eines Dokuments |
| `POST` | `/relink-all` | Alle E-Mail-Anhänge neu verknüpfen |
| `POST` | `/lieferant/{lieferantId}/relink` | Anhänge für Lieferant neu verknüpfen |
| `POST` | `/process-assigned-emails` | Zugewiesene E-Mails verarbeiten |
| `POST` | `/process-email/{emailId}` | Einzelne E-Mail verarbeiten |
| `POST` | `/lieferant/{lieferantId}/process-emails` | E-Mails eines Lieferanten verarbeiten |
| `GET` | `/duplicates` | Duplikate finden |
| `GET` | `/{dokumentId}/download` | Dokument herunterladen |

---

## 9. Projekte & Kunden

**Controller:** `ProjektController`  
**Basis-URL:** `/api/projekte`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Projekte |
| `GET` | `/{id}` | Einzelnes Projekt |
| `POST` | `/` | Neues Projekt erstellen |
| `PUT` | `/{id}` | Projekt aktualisieren |

**Controller:** `KundeController`  
**Basis-URL:** `/api/kunden`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Kunden |
| `GET` | `/{id}` | Einzelner Kunde |
| `POST` | `/` | Neuen Kunden erstellen |
| `PUT` | `/{id}` | Kunden aktualisieren |

---

## 10. Lieferanten

**Controller:** `LieferantenController`  
**Basis-URL:** `/api/lieferanten`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Lieferanten |
| `GET` | `/{id}` | Einzelner Lieferant |
| `POST` | `/` | Neuen Lieferanten erstellen |

**Controller:** `LieferantReklamationController`  
**Basis-URL:** `/api/lieferant-reklamationen`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Lieferanten-Reklamationsverwaltung |

---

## 11. Mitarbeiter & Abwesenheit

**Controller:** `MitarbeiterController`  
**Basis-URL:** `/api/mitarbeiter`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Mitarbeiter |
| `GET` | `/{id}` | Einzelner Mitarbeiter |
| `POST` | `/` | Neuen Mitarbeiter erstellen |

**Controller:** `AbwesenheitController`  
**Basis-URL:** `/api/abwesenheit`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/` | Neue Abwesenheit erstellen |
| `GET` | `/mitarbeiter/{mitarbeiterId}` | Abwesenheiten eines Mitarbeiters |
| `GET` | `/team` | Team-Abwesenheiten |

**Controller:** `UrlaubsantragController`  
**Basis-URL:** `/api/urlaubsantraege`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Urlaubsanträge mit Genehmigungsworkflow |

---

## 12. Dokument-Generator

**Controller:** `DokumentGeneratorController`  
**Basis-URL:** `/api/dokument-generator`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/pdf` | PDF aus Template generieren |
| `POST` | `/preview` | Vorschau generieren |
| `POST` | `/zugferd-pdf` | ZUGFeRD-PDF generieren |

---

## 13. Artikel & Produktkategorien

**Controller:** `ArtikelController`  
**Basis-URL:** `/api/artikel`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/` | Neuen Artikel erstellen |
| `POST` | `/import/headers` | Import-Header analysieren |
| `POST` | `/import/analyze` | Import-Daten analysieren |

**Controller:** `ProduktkategorieController`  
**Basis-URL:** `/api/produktkategorien`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Produktkategorie-Verwaltung |

**Controller:** `ArtikelKategorieController`  
**Basis-URL:** `/artikel/kategorien`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/haupt` | Hauptkategorien |
| `GET` | `/alle` | Alle Kategorien |
| `GET` | `/{parentId}/unterkategorien` | Unterkategorien |

---

## 14. Mietverwaltung

**Controller:** `MietobjektController`  
**Basis-URL:** `/api/miete/mietobjekte`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Mietobjekt-Verwaltung |

**Controller:** `MietabrechnungController`  
**Basis-URL:** `/api/miete/mietabrechnung`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Mietabrechnungs-Verwaltung |

**Controller:** `KostenVerteilungController`  
**Basis-URL:** `/api/miete/kostenverteilung`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Kostenverteilungs-Berechnung |

**Controller:** `RaumVerbrauchController`  
**Basis-URL:** `/api/miete/raumverbrauch`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Zählerstanderfassung und Verbrauchsübersicht |

---

## 15. System & Sonstiges

**Controller:** `FirmaController`  
**Basis-URL:** `/api/firma`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Firmenstammdaten-Verwaltung |

**Controller:** `BwaController`  
**Basis-URL:** `/api/bwa`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/jahre` | Verfügbare BWA-Jahre |
| `GET` | `/jahr/{jahr}` | BWA für ein Jahr |
| `GET` | `/{id}` | Einzelne BWA |

**Controller:** `KiHilfeController`  
**Basis-URL:** `/api/ki-hilfe`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | KI-Assistenz und Chat-Funktionen |

**Controller:** `NotificationController`  
**Basis-URL:** `/api/notifications`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Benachrichtigungs-System |

**Controller:** `SystemUtilityController`  
**Basis-URL:** `/api/system`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | System-Utilities (Wartung, Diagnostik) |

**Controller:** `DateiController`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/images/{dateiname:.+}` | Bild herunterladen |
| `GET` | `/api/dokumente/{dateiname:.+}` | Dokument herunterladen |
| `GET` | `/api/dokumente/{dateiname:.+}/thumbnail` | Dokument-Thumbnail |

---

## 16. Authentifizierung & Berechtigungen

**Controller:** `AbteilungBerechtigungController`  
**Basis-URL:** `/api/abteilungen`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/berechtigungen` | Alle Berechtigungen |
| `GET` | `/{id}/berechtigungen` | Berechtigungen einer Abteilung |
| `PUT` | `/{id}/berechtigungen` | Berechtigungen aktualisieren |

### 17.1 Abteilungsbasierte Zugriffssteuerung

| Abteilung | Rechte |
|---|---|
| **Abt. 2 (Buchhaltung)** | Nur genehmigte Dokumente, Zahlungen |
| **Abt. 3 (Büro)** | Alle Dokumente, Genehmigungsrecht |

**Controller:** `FrontendUserController`  
**Basis-URL:** `/api/user`

| Methode | Pfad | Beschreibung |
|---|---|---|
| (diverse) | (diverse) | Benutzerprofil und Einstellungen |

---

## Controller-Gesamtübersicht

| # | Controller | Basis-URL |
|---|---|---|
| 1 | AbteilungBerechtigungController | `/api/abteilungen` |
| 2 | AbwesenheitController | `/api/abwesenheit` |
| 3 | AngebotController | `/api/angebote` |
| 4 | ArbeitsgangController | `/api` |
| 5 | ArbeitszeitartController | `/api/arbeitszeitarten` |
| 6 | ArtikelController | `/api/artikel` |
| 7 | ArtikelKategorieController | `/artikel/kategorien` |
| 8 | AusgangsGeschaeftsDokumentController | `/api/ausgangs-dokumente` |
| 9 | BestellungController | `/api/bestellungen` |
| 10 | BestellungsUebersichtController | `/api/bestellungen-uebersicht` |
| 11 | BwaController | `/api/bwa` |
| 12 | DateiController | `/api/images`, `/api/dokumente` |
| 13 | DocumentScannerController | `/api/documents` |
| 14 | DokumentGeneratorController | `/api/dokument-generator` |
| 15 | EmailController | `/api/email` |
| 16 | EmailSignatureController | `/api/email/signatures` |
| 17 | EmailTemplateController | `/api/email/template` |
| 18 | FirmaController | `/api/firma` |
| 19 | FormularTemplateController | `/api/formular-templates` |
| 20 | FrontendUserController | `/api/user` |
| 21 | GaebImportController | `/api/gaeb-import` |
| 22 | KalenderController | `/api/kalender` |
| 23 | KiHilfeController | `/api/ki-hilfe` |
| 24 | KostenVerteilungController | `/api/miete/kostenverteilung` |
| 25 | KundeController | `/api/kunden` |
| 26 | LeistungController | `/api/leistungen` |
| 27 | LieferantDokumentController | `/api/lieferant-dokumente` |
| 28 | LieferantReklamationController | `/api/lieferant-reklamationen` |
| 29 | LieferantenController | `/api/lieferanten` |
| 30 | LohnabrechnungController | `/api/lohnabrechnungen` |
| 31 | MietabrechnungController | `/api/miete/mietabrechnung` |
| 32 | MietobjektController | `/api/miete/mietobjekte` |
| 33 | MitarbeiterController | `/api/mitarbeiter` |
| 34 | NotificationController | `/api/notifications` |
| 35 | OffenePostenController | `/api/offene-posten` |
| 36 | OutOfOfficeController | `/api/out-of-office` |
| 37 | ProduktkategorieController | `/api/produktkategorien` |
| 38 | ProjektController | `/api/projekte` |
| 39 | RaumVerbrauchController | `/api/miete/raumverbrauch` |
| 40 | RechnungsuebersichtController | `/api/rechnungen` |
| 41 | SchnittbilderController | `/api/schnittbilder` |
| 42 | SystemUtilityController | `/api/system` |
| 43 | TextbausteinController | `/api/textbausteine` |
| 44 | UnifiedEmailController | `/api/unified-email` |
| 45 | UrlaubsantragController | `/api/urlaubsantraege` |
| 46 | UtilsController | `/api/utils` |
| 47 | VendorInvoiceController | `/api/vendor-invoices` |
| 48 | ZeiterfassungApiController | `/api/zeiterfassung` |
| 49 | ZeiterfassungController | `/zeiterfassung` |
| 50 | ZeitkontoKorrekturController | `/api/zeitkonto-korrektur` |
| 51 | ZeitverwaltungController | `/api/zeitverwaltung` |
| 52 | SpaController | (SPA Routing) |
