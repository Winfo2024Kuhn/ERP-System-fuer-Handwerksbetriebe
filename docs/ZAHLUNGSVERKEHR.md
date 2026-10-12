# Zahlungsverkehr – Zahlungsverfolgung, Offene Posten, Mahnwesen

## Übersicht

Dieses Dokument beschreibt die Zahlungsverfolgung im Kalkulationsprogramm – von der Rechnungsbuchung über die Offene-Posten-Verwaltung und Skonto bis zum automatischen Mahnwesen.

**Kurz für die Praxis:**
- Menü: Finanzen & Controlling → Buchhaltung → **„Offene Posten“** (Seite „Offene Posten – Übersicht aller unbezahlten Rechnungen“).
- Reiter **„Ausgangsrechnungen“**: Ihre Rechnungen an Kunden. Ist das Geld da, setzen Sie das Häkchen **„Bezahlt“**.
- Reiter **„Eingangsrechnungen“**: Rechnungen Ihrer Lieferanten – genehmigen und als bezahlt markieren.
- Automatische Zahlungserinnerungen und Mahnungen schalten Sie unter Administration → **Firma** → „Automatisches Mahnverfahren“ ein.

---

## 1. Zahlungsstatus von Ausgangsrechnungen

### 1.1 Wo der Status liegt

Der Zahlungsstatus einer Ausgangsrechnung steht **nicht** am Geschäftsdokument selbst, sondern am zugehörigen Offenen-Posten-Eintrag (`ProjektGeschaeftsdokument`):

| Feld | Typ | Beschreibung |
|---|---|---|
| `dokumentid` | `String` | Rechnungsnummer (z. B. `RE-2026/10/00042`) |
| `geschaeftsdokumentart` | `String` | „Rechnung“, „Teilrechnung“, „Abschlagsrechnung“, „Schlussrechnung“ (bei Mahnungen „Mahnung“) |
| `rechnungsdatum` | `LocalDate` | Rechnungsdatum |
| `faelligkeitsdatum` | `LocalDate` | Fälligkeitsdatum |
| `bruttoBetrag` | `BigDecimal` | Bruttobetrag |
| `bezahlt` | `boolean` | `true` = bezahlt |
| `emailVersandDatum` | `LocalDate` | Versanddatum der Rechnung |
| `systemGeneriert` | `boolean` | `true` = automatisch beim Buchen angelegt (nur diese werden automatisch gemahnt) |
| `mahnstufe` | `Mahnstufe` | Nur bei Mahnungen gesetzt |
| `referenzDokument` | FK | Bei Mahnungen: Verweis auf die Original-Rechnung |

### 1.2 Bezahlt markieren

Offene Posten → Reiter „Ausgangsrechnungen“ → Häkchen **„Bezahlt“** in der Zeile der Rechnung (`PATCH /api/projekte/dokumente/{id}/bezahlt?bezahlt=true|false`). Das Häkchen lässt sich auch wieder entfernen.

Danach prüft das System automatisch, ob das ganze Projekt bezahlt ist (siehe 3.3).

---

## 2. Keine Teilzahlungen

Für Ausgangsrechnungen gibt es nur **bezahlt** oder **nicht bezahlt**. Einzelne Zahlungseingänge (Teilzahlungen) werden nicht erfasst und kein offener Restbetrag je Rechnung berechnet.

Wer in Raten abrechnen möchte, schreibt stattdessen **Abschlagsrechnungen** (siehe `RECHNUNGSWESEN.md`) – jede hat ihren eigenen Offenen Posten.

---

## 3. Offene-Posten-Verwaltung

### 3.1 Ausgangsrechnungen (Offene Posten Ausgang)

Beim Buchen einer Rechnung, Teil-, Abschlags- oder Schlussrechnung (Drucken & Buchen, PDF-Export oder E-Mail-Versand) wird automatisch ein `ProjektGeschaeftsdokument`-Eintrag erstellt – sofern die Rechnung einem Projekt zugeordnet ist:

| Feld | Quelle |
|---|---|
| `dokumentid` | Dokumentnummer des `AusgangsGeschaeftsDokuments` |
| `geschaeftsdokumentart` | Gemappter Typ („Rechnung“, „Abschlagsrechnung“ etc.) |
| `rechnungsdatum` | Datum des Dokuments |
| `bruttoBetrag` | Bruttobetrag des Dokuments |
| `bezahlt` | Initial `false` |
| `faelligkeitsdatum` | `datum + zahlungszielTage` (fehlt das, Zahlungsziel des Kunden) |
| `systemGeneriert` | `true` |

Wird eine Rechnung **storniert**, wird ihr Offener Posten automatisch als bezahlt markiert und verschwindet aus der Liste.

**Ansicht „Ausgangsrechnungen“:**
- Kacheln: **Offene Summe**, **Rechnungen**, **Überfällig**, **Mit Mahnung**
- Spalten: Projekt, Kunde, Rechnungsnr., Datum, Versandt, Fällig, Tage („noch X Tage“, „Heute fällig“, „Überfällig seit X Tagen“), Betrag, Dokument, Status („Bezahlt“)
- Mahnungen stehen eingerückt unter der jeweiligen Rechnung
- Knopf **„Aktualisieren“**

### 3.2 Rechnungen nacherfassen (derzeit ohne Oberfläche)

Den Knopf „Manuell erfassen“ in den Offenen Posten gibt es seit Oktober 2026 nicht mehr – Rechnungen und Belege kommen über **Finanzen & Controlling › Buchhaltung › Belege & Kasse** ins Programm. Die Schnittstelle dafür besteht weiter (`/api/offene-posten/ausgang/analyze` und `/import`):

- Rechnungsnummer und Betrag (> 0) sind Pflicht; eine bereits vorhandene Rechnungsnummer wird abgelehnt.
- Nacherfasste Rechnungen werden **nicht** automatisch gemahnt (`systemGeneriert = false`).
- Bei Projekten ohne Angebot/AB bildet die Summe der Rechnungen den Auftragspreis.

### 3.3 Projekt automatisch „bezahlt“ und „beendet“

Ein Projekt gilt erst als bezahlt (und wird automatisch beendet), wenn
1. die nicht stornierten Rechnungen den Auftragspreis inkl. Nachträgen abdecken,
2. keine Rechnung mehr ein ungebuchter Entwurf ist,
3. kein Offener Posten des Projekts mehr unbezahlt ist und
4. die bezahlten Rechnungen den Auftragspreis erreichen (Toleranz 1 Cent).

Hat jemand den Haken „Beendet“ am Projekt von Hand gesetzt oder entfernt, fasst die Automatik ihn nicht mehr an.

### 3.4 Eingangsrechnungen (Offene Posten Eingang)

Lieferantenrechnungen (`LieferantGeschaeftsdokument`) verwalten den Zahlungsstatus direkt:

| Feld | Beschreibung |
|---|---|
| `bezahlt` | `true` wenn bezahlt |
| `bezahltAm` | Datum der Zahlung |
| `tatsaechlichGezahlt` | Gezahlter Betrag (nach Skonto-Abzug) |
| `mitSkonto` | `true` wenn Skonto genutzt wurde |
| `bereitsGezahlt` | Laut Rechnung bereits bezahlt (von KI oder ZUGFeRD erkannt, z. B. Vorauskasse) – bei ZUGFeRD-Rechnungen wird die Rechnung dann direkt als bezahlt übernommen |
| `zahlungsart` | Z. B. Vorauskasse, SEPA-Lastschrift, Kreditkarte, PayPal, Überweisung |
| `genehmigt` | Freigabe durch eine berechtigte Abteilung |

**Ansicht „Eingangsrechnungen“:** Spalten Lieferant, Rechnungsnr., Datum, Fällig, Skonto („noch X Tage“, „abgelaufen“, „✓ genutzt“), Netto, Brutto, Dokument, Genehmigt, Status. Mit **„Bezahlte anzeigen“** werden auch bezahlte Rechnungen eingeblendet.

### 3.5 API-Endpoints

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/projekte/offene-posten` | Offene Posten Ausgang (inkl. Mahnungen) |
| `PATCH` | `/api/projekte/dokumente/{id}/bezahlt?bezahlt=…` | Ausgangsrechnung als bezahlt/unbezahlt markieren |
| `POST` | `/api/offene-posten/ausgang/analyze` | Hochgeladene Ausgangsrechnung per KI auslesen (Vorschau; derzeit ohne Oberfläche) |
| `POST` | `/api/offene-posten/ausgang/import` | Ausgangsrechnung nacherfassen (derzeit ohne Oberfläche) |
| `GET` | `/api/offene-posten/eingang` | Offene Eingangsrechnungen (gefiltert nach Abteilungsrechten) |
| `GET` | `/api/offene-posten/eingang/alle` | Alle Eingangsrechnungen, auch bezahlte (gefiltert nach Abteilungsrechten) |
| `PUT` | `/api/offene-posten/eingang/{id}/bezahlt` | Eingangsrechnung bezahlt/unbezahlt (mit Skonto-Berechnung) |
| `PATCH` | `/api/offene-posten/eingang/{id}/genehmigen` | Eingangsrechnung genehmigen (nur mit Recht „Rechnungen genehmigen“) |

---

## 4. Abteilungs-Berechtigungen

### 4.1 Sichtbarkeit

Die Rechte werden unter Einstellungen → **Berechtigungen** pro Abteilung vergeben:

| Recht | Wirkung |
|---|---|
| **Rechnungen genehmigen** | Sieht alle Eingangsrechnungen, kann genehmigen |
| **Genehmigte Rechnungen sehen** | Sieht nur genehmigte Eingangsrechnungen |
| keines von beiden | Sieht keine Eingangsrechnungen |

Zusätzlich gilt, welche Lieferanten-Dokumenttypen die Abteilung überhaupt sehen darf. Typische Aufteilung: Büro genehmigt, Buchhaltung sieht nur Genehmigtes.

### 4.2 Genehmigungsprozess

```
Eingangsrechnung eingegangen
   │
   └─[Häkchen "Genehmigt" durch berechtigte Abteilung]──► genehmigt = true
                              → sichtbar für "Genehmigte Rechnungen sehen"
                              → nur noch als PDF-Vorschau zu öffnen, nicht mehr bearbeitbar
```

---

## 5. Mahnwesen

### 5.1 Mahnstufen (`Mahnstufe`-Enum)

| Stufe | Enum-Wert | Beschreibung | Nummer der Mahnung |
|---|---|---|---|
| Stufe 1 | `ZAHLUNGSERINNERUNG` | Zahlungserinnerung (freundlicher Hinweis) | Rechnungsnummer + `-Z` |
| Stufe 2 | `ERSTE_MAHNUNG` | 1. Mahnung | Rechnungsnummer + `-M1` |
| Stufe 3 | `ZWEITE_MAHNUNG` | 2. Mahnung | Rechnungsnummer + `-M2` |

### 5.2 Automatisches Mahnverfahren einrichten

Vorlagen & Stammdaten → Administration → **Firma** → Reiter „Firmendaten“ → Block **„Automatisches Mahnverfahren“**:

| Einstellung | Bedeutung | Vorgabe |
|---|---|---|
| **Aktiv** | Mahnverfahren ein/aus | aus |
| **Tage nach Fälligkeit** | Abstand Fälligkeit → Zahlungserinnerung | 7 |
| **Tage nach der Zahlungserinnerung** | Abstand Zahlungserinnerung → 1. Mahnung | 7 |
| **Tage nach der 1. Mahnung** | Abstand 1. Mahnung → 2. Mahnung | 7 |
| **Neues Zahlungsziel auf der Mahnung (Tage)** | So viele Tage bekommt der Kunde auf jeder Mahnung | 7 |

Für den Versand braucht jede Stufe eine aktive E-Mail-Vorlage (Kommunikation → E-Mail → E-Mail Vorlagen).

### 5.3 Ablauf (`AutoMahnVersandService`)

Täglich um **9:00 Uhr** prüft das System alle offenen Rechnungen:

```
Rechnung offen, systemGeneriert, mit Fälligkeitsdatum und Projekt
   │
   ├─[X Tage nach Fälligkeit]────────────► ZAHLUNGSERINNERUNG
   │                                         (referenzDokument → Original-Rechnung)
   │
   ├─[Y Tage nach Versand der Erinnerung]─► ERSTE_MAHNUNG
   │
   └─[Z Tage nach Versand der 1. Mahnung]─► ZWEITE_MAHNUNG
```

Pro Stufe passiert:
1. Mahn-PDF erzeugen – **ohne Leistungspositionen**, nur mit Verweis auf die offene Rechnung und einem neuen Zahlungsziel (heute + eingestellte Tage)
2. E-Mail an die erste E-Mail-Adresse des Kunden senden (ohne Adresse wird die Rechnung übersprungen)
3. Mahnung als `ProjektGeschaeftsdokument` (`geschaeftsdokumentart = "Mahnung"`, `mahnstufe`, `referenzDokument`) speichern und die Mail im Projekt archivieren

Regeln:
- Es wird immer nur die nächste fehlende Stufe verschickt – auch bei lange überfälligen Rechnungen folgen die Stufen nie im Tagesabstand aufeinander (Mindestabstand 1 Tag).
- Gemahnt werden nur Rechnungen, die mit dem Programm geschrieben wurden; nacherfasste Rechnungen nicht.
- Rechnung, Teil-, Abschlags- und Schlussrechnung sind mahnfähig.
- Ein Lauf kann nicht doppelt laufen; ein Admin kann ihn sofort starten mit `POST /api/mahnwesen/lauf` (Antwort nennt Anzahl versendeter und fehlgeschlagener Mahnungen).

### 5.4 Anzeige

- **Offene Posten**: Mahnungen eingerückt unter der Rechnung, Kachel „Mit Mahnung“, Kennzeichnung der letzten Mahnstufe
- **Projekt → Geschäftsdokumente**: Mahnungen als virtuelle Einträge (`ZAHLUNGSERINNERUNG`, `ERSTE_MAHNUNG`, `ZWEITE_MAHNUNG`) unter der Rechnung
- PDF-Vorschau einer Stufe: `GET /api/ausgangs-dokumente/{id}/mahnung-vorschau?stufe=ZAHLUNGSERINNERUNG`

### 5.5 Dokumenttypen für Mahnungen

Im `Dokumenttyp`-Enum (u. a. für Textbausteine) sind Mahnungen als eigene Typen abgebildet:

| Dokumenttyp | Beschreibung |
|---|---|
| `ZAHLUNGSERINNERUNG` | Zahlungserinnerung |
| `ERSTE_MAHNUNG` | 1. Mahnung |
| `ZWEITE_MAHNUNG` | 2. Mahnung |

---

## 6. Skonto-Verwaltung

### 6.1 Felder auf Lieferantenrechnungen

| Feld | Typ | Beschreibung |
|---|---|---|
| `skontoTage` | `Integer` | Tage für Skonto-Abzug ab Rechnungsdatum (z. B. 10 Tage) |
| `skontoProzent` | `BigDecimal` | Skonto-Prozentsatz (z. B. 2.00 für 2 %) |
| `nettoTage` | `Integer` | Zahlungsziel in Tagen (z. B. 30 Tage) |
| `mitSkonto` | `Boolean` | `true` wenn Skonto bei Zahlung genutzt wurde |
| `tatsaechlichGezahlt` | `BigDecimal` | Tatsächlich gezahlter Betrag nach Skonto |

Skonto-Angaben kommen aus der E-Rechnung (ZUGFeRD) oder aus der KI-Analyse.

### 6.2 Berechnung

Beim Setzen von „bezahlt“ prüft das System, ob **heute** noch innerhalb der Skontofrist liegt (`Rechnungsdatum + skontoTage`):

```
Innerhalb der Frist:
  Skonto-Betrag = Bruttobetrag × (skontoProzent / 100)
  tatsaechlichGezahlt = Bruttobetrag − Skonto-Betrag, mitSkonto = true
Sonst (oder ohne Skonto-Angaben):
  tatsaechlichGezahlt = Bruttobetrag, mitSkonto = false
```

Wird „bezahlt“ wieder entfernt, werden Zahldatum, gezahlter Betrag und Skonto-Kennzeichen zurückgesetzt.

### 6.3 Beispiel

```
Lieferantenrechnung: 5.000 € brutto
  Skontobedingungen: 2% bei Zahlung innerhalb 10 Tagen

  Als bezahlt markiert innerhalb der Skontofrist:
    tatsaechlichGezahlt = 4.900 €
    mitSkonto = true
```

---

## 7. Zahlungsbedingungen

### 7.1 Felder

| Feld | Typ | Wo | Beschreibung |
|---|---|---|---|
| `zahlungsziel` | `Integer` | `Kunde` | Standard-Zahlungsziel des Kunden in Tagen (Vorgabe 8), im Kundenformular „Zahlungsziel (Tage)“ |
| `zahlungszielTage` | `Integer` | `AusgangsGeschaeftsDokument` | Zahlungsziel der Rechnung in Tagen; ohne Angabe wird das des Kunden übernommen |
| `faelligkeitsdatum` | `LocalDate` | `ProjektGeschaeftsdokument` | Fälligkeit des Offenen Postens |
| `zahlungsziel` | `LocalDate` | `LieferantGeschaeftsdokument` | Fälligkeitsdatum der Lieferantenrechnung |
| `skontoTage` / `skontoProzent` / `nettoTage` | | `LieferantGeschaeftsdokument` | Skonto- und Netto-Konditionen |

### 7.2 Fälligkeitsberechnung

```
faelligkeitsdatum = rechnungsdatum + zahlungszielTage
```

Beim endgültigen Drucken, PDF-Export oder E-Mail-Versand wird das Rechnungsdatum auf den heutigen Tag gesetzt – das Zahlungsziel läuft also ab dem tatsächlichen Versandtag.

---

## 8. API-Referenz

### 8.1 Offene Posten

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/projekte/offene-posten` | Offene Posten Ausgang |
| `PATCH` | `/api/projekte/dokumente/{id}/bezahlt?bezahlt=…` | Ausgang bezahlt/unbezahlt |
| `POST` | `/api/offene-posten/ausgang/analyze` | Nacherfassung: Datei per KI auslesen (derzeit ohne Oberfläche) |
| `POST` | `/api/offene-posten/ausgang/import` | Nacherfassung: speichern (derzeit ohne Oberfläche) |
| `GET` | `/api/offene-posten/eingang` | Offene Eingangsrechnungen (abteilungsbasiert) |
| `GET` | `/api/offene-posten/eingang/alle` | Alle Eingangsrechnungen (abteilungsbasiert) |
| `PUT` | `/api/offene-posten/eingang/{id}/bezahlt` | Eingang bezahlt/unbezahlt |
| `PATCH` | `/api/offene-posten/eingang/{id}/genehmigen` | Eingang genehmigen |

### 8.2 Mahnwesen

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/mahnwesen/lauf` | Mahnlauf sofort starten (nur Admins) |
| `GET` | `/api/ausgangs-dokumente/{id}/mahnung-vorschau?stufe=…` | PDF-Vorschau einer Mahnstufe |

---

## 9. Entitäten-Übersicht

| Entity | Zweck |
|---|---|
| `AusgangsGeschaeftsDokument` | Ausgangsdokumente (Angebot, AB, Rechnungen) mit Zahlungsziel |
| `ProjektGeschaeftsdokument` | Offener Posten mit Bezahlt-Status und Mahnstufen-Tracking |
| `Mahnstufe` (Enum) | Mahnstufen: Zahlungserinnerung, 1. Mahnung, 2. Mahnung |
| `Dokumenttyp` (Enum) | Dokumenttypen inkl. Mahnungsdokumente |
| `LieferantGeschaeftsdokument` | Eingangsrechnungen mit Skonto-/Zahlungsverwaltung |
| `Firmeninformation` | Einstellungen des automatischen Mahnverfahrens |
| `Zahlungsart` | Stammdaten der Zahlungsarten für Belege/Kasse (Bar, EC-Karte, Überweisung …) |

| Service | Verantwortung |
|---|---|
| `AusgangsGeschaeftsDokumentService` | Offene Posten beim Buchen anlegen, bei Storno als bezahlt markieren, Projekt-Bezahlt-Status |
| `DateiSpeicherService` | Offene Posten lesen, bezahlt setzen |
| `AutoMahnVersandService` | Tägliches automatisches Mahnverfahren |
