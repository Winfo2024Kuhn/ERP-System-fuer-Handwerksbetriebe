# Rechnungswesen – Kompletter Rechnungsprozess

## Übersicht

Dieses Dokument beschreibt den vollständigen Rechnungsprozess im Kalkulationsprogramm – von der Erstellung über die Nummerierung und PDF-Generierung bis zum Versand und zur Buchung.

---

## 1. Dokumenttypen (`AusgangsGeschaeftsDokumentTyp`)

Alle ausgehenden Geschäftsdokumente werden in der Entity `AusgangsGeschaeftsDokument` verwaltet. Der Typ wird über das Enum `AusgangsGeschaeftsDokumentTyp` gesteuert:

| Typ | Beschreibung | Bearbeitbar nach Buchung? |
|---|---|---|
| `ANGEBOT` | Angebot an den Kunden | ✅ Ja |
| `AUFTRAGSBESTAETIGUNG` | Bestätigung nach Auftragserteilung | ✅ Ja |
| `RECHNUNG` | Standard-Einzelrechnung | ❌ Nein (gesperrt) |
| `TEILRECHNUNG` | Teilrechnung für Abschnitte | ❌ Nein (gesperrt) |
| `ABSCHLAGSRECHNUNG` | Abschlagsrechnung (1., 2., 3. ...) | ❌ Nein (gesperrt) |
| `SCHLUSSRECHNUNG` | Schlussrechnung (verrechnet Abschläge) | ❌ Nein (gesperrt) |
| `GUTSCHRIFT` | Gutschrift an den Kunden | ❌ Nein (gesperrt) |
| `STORNO` | Stornorechnung (negiert Original) | ❌ Nein (gesperrt) |

---

## 2. Automatische Nummernvergabe

### 2.1 Format

```
YYYY/MM/NNNNN
```

Beispiele: `2026/01/00001`, `2026/03/00042`

### 2.2 Counter-Logik

Die Nummernvergabe erfolgt über die Entity `AusgangsGeschaeftsDokumentCounter`:

| Feld | Beschreibung |
|---|---|
| `monatKey` | Schlüssel im Format `YYYY/MM`, z.B. `2026/03` |
| `zaehler` | Fortlaufender Zähler pro Monat, startet bei 0 |

**Ablauf der Nummernvergabe in `generiereNummer()`:**
1. Aktuellen Monat als Key bestimmen (`YearMonth.now()`)
2. Counter-Datensatz mit **pessimistischem Lock** laden (`findByMonatKeyForUpdate`)
3. Falls kein Counter existiert: neuen Counter mit Zähler 0 anlegen
4. Zähler um 1 erhöhen
5. Nummer formatieren: `String.format("%s/%05d", monatKey, zaehler)`

**Thread-Sicherheit:** Durch pessimistisches Locking auf DB-Ebene sind parallele Anfragen serialisiert – keine doppelten Nummern möglich.

---

## 3. Dokumentenkette

### 3.1 Standard-Ablauf

```
Angebot
   └→ Auftragsbestätigung (AB)
       ├→ Abschlagsrechnung 1 (abschlagsNummer = 1)
       ├→ Abschlagsrechnung 2 (abschlagsNummer = 2)
       ├→ Abschlagsrechnung 3 (abschlagsNummer = 3)
       └→ Schlussrechnung (verrechnet alle Abschläge)
```

### 3.2 Verknüpfungen

Jedes Dokument kann folgende Beziehungen haben:

| Beziehung | FK-Feld | Beschreibung |
|---|---|---|
| Vorgänger | `vorgaenger_id` | Verweis auf das vorhergehende Dokument |
| Nachfolger | `@OneToMany(mappedBy = "vorgaenger")` | Alle aus diesem Dokument erstellten Dokumente |
| Projekt | `projekt_id` | Zugehöriges Projekt |
| Angebot | `angebot_id` | Zugehöriges Angebot (bei Angebots-Kontext) |
| Kunde | `kunde_id` | Kunde für Rechnungsadresse |
| Ersteller | `erstellt_von_id` | Benutzer, der das Dokument erstellt hat |

### 3.3 Abschlagsnummer-Logik

Die `abschlagsNummer` wird automatisch hochgezählt. Beim Erstellen einer neuen Abschlagsrechnung wird über `countByVorgaengerIdAndTyp()` die Anzahl bestehender Abschlagsrechnungen ermittelt und die nächste Nummer vergeben.

---

## 4. Umwandlung zwischen Dokumenttypen

### 4.1 Erlaubte Konvertierungen

```
ANGEBOT             →  AUFTRAGSBESTAETIGUNG
AUFTRAGSBESTAETIGUNG → RECHNUNG, ABSCHLAGSRECHNUNG, SCHLUSSRECHNUNG
```

### 4.2 Content-Vererbung bei Konvertierung

Bei der Umwandlung werden folgende Felder vom Vorgänger übernommen:
- `htmlInhalt` – HTML-Inhalt aus dem Document-Builder
- `positionenJson` – JSON der Leistungspositionen
- `betragNetto`, `betragBrutto`, `mwstSatz` – Beträge
- `projekt`, `angebot`, `kunde` – Verknüpfungen
- `rechnungsadresseOverride` – Abweichende Rechnungsadresse

### 4.3 Migration Angebot → Projekt

Wird ein Angebot in ein Projekt überführt (`migrateFromAngebotToProjekt()`):
1. Alle Dokumente des Angebots werden dem neuen Projekt zugeordnet
2. Die Angebot-Referenz wird entfernt (`angebot = null`)
3. Projektkategorien werden aus den Dokumenten-Positionen automatisch abgeleitet

---

## 5. MwSt-Berechnung

### 5.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `betragNetto` | `BigDecimal(12,2)` | Nettobetrag |
| `betragBrutto` | `BigDecimal(12,2)` | Bruttobetrag |
| `mwstSatz` | `BigDecimal(5,4)` | MwSt-Satz als Dezimalzahl (z.B. `0.19` für 19%) |

### 5.2 Berechnung

```
Brutto = Netto × (1 + mwstSatz)
MwSt-Betrag = Netto × mwstSatz
```

Die Methode `getMwstBetrag()` auf der Entity berechnet:
```java
betragNetto.multiply(mwstSatz)
```

### 5.3 Standardwert

Falls kein `mwstSatz` angegeben wird, gilt der Default: **19%** (`0.19`), gesetzt via `@PrePersist`.

---

## 6. Rechnungsadresse

### 6.1 Standard

Die Rechnungsadresse kommt standardmäßig aus den **Kundenstammdaten** (Entity `Kunde`, FK `kunde_id`).

### 6.2 Override pro Dokument

Über das Feld `rechnungsadresseOverride` (max. 500 Zeichen) kann pro Dokument eine **abweichende Rechnungsadresse** hinterlegt werden. Dieses Override ändert **nicht** die Kundenstammdaten.

**Logik:**
- `rechnungsadresseOverride` gesetzt → Override-Adresse verwenden
- `rechnungsadresseOverride` leer → Adresse aus Kunde laden

---

## 7. Positionen-JSON

### 7.1 Struktur

Die Rechnungspositionen werden als JSON im Feld `positionenJson` (LONGTEXT) gespeichert. Das Format unterstützt verschiedene Block-Typen:

| Block-Typ | Beschreibung |
|---|---|
| `TEXT` | Freitext-Block |
| `SERVICE` | Leistungsposition mit Menge, Einheit, Preis |
| `CLOSURE` | Zusammenfassung/Abschluss mit Aufschlüsselung |
| `SEPARATOR` | Horizontale Trennlinie |
| `SECTION_HEADER` | Abschnitts-Überschrift (z.B. für Arbeitsgänge) |
| `SUBTOTAL` | Zwischensumme |

### 7.2 Netto-Berechnung aus Positionen

Falls `betragNetto` nicht explizit gesetzt ist, wird der Nettobetrag dynamisch aus dem `positionenJson` berechnet (`berechneNettoAusPositionenJson()`).

---

## 8. Abrechnungsverlauf

### 8.1 Zweck

Der Abrechnungsverlauf zeigt für ein Basisdokument (z.B. eine AB) alle daraus erstellten Rechnungen und den verbleibenden **Restbetrag**.

### 8.2 Berechnung

```
Basisbetrag (Netto der AB)
  − Abschlagsrechnung 1 (Netto)
  − Abschlagsrechnung 2 (Netto)
  − Abschlagsrechnung 3 (Netto)
  = Restbetrag (für Schlussrechnung)
```

Stornierte Dokumente werden aus der Berechnung **ausgenommen**.

### 8.3 Schlussrechnung

Die Schlussrechnung berechnet ihren Betrag automatisch als Restbetrag:

```
Schlussrechnungsbetrag = Basisbetrag − Summe(alle Abschlagsrechnungen)
```

Bei der Anzeige des Betrags der Schlussrechnung wird vermieden, dass die Schlussrechnung sich selbst abzieht.

### 8.4 Endpoint

```
GET /api/ausgangs-dokumente/{id}/abrechnungsverlauf
```

Gibt ein `AbrechnungsverlaufDto` zurück mit:
- Basisdokument-Daten (ID, Nummer, Typ, Betrag)
- Liste der Nachfolger-Rechnungen (Nummer, Typ, Betrag, Datum)
- Berechneter Restbetrag

---

## 9. Buchung und Offene Posten

### 9.1 Buchungsvorgang

Beim Buchen eines Dokuments (`buchen()` oder `buchenNachEmailVersand()`) passiert:

1. `gebucht = true`, `gebuchtAm = heute` setzen
2. Für Rechnungstypen: Automatischer Eintrag in `ProjektGeschaeftsdokument` (Offene Posten)
3. Bei E-Mail-Versand zusätzlich: `versandDatum = heute`

### 9.2 Offene-Posten-Eintrag

Für jeden gebuchten Rechnungstyp wird automatisch ein `ProjektGeschaeftsdokument`-Eintrag erstellt:

| Feld | Quelle |
|---|---|
| `dokumentid` | Dokumentnummer des AusgangsGeschaeftsDokuments |
| `geschaeftsdokumentart` | Gemappter Typ ("Rechnung", "Abschlagsrechnung" etc.) |
| `rechnungsdatum` | Datum des Dokuments |
| `bruttoBetrag` | Bruttobetrag des Dokuments |
| `bezahlt` | Initial `false` |
| `faelligkeitsdatum` | `datum + zahlungszielTage` (falls gesetzt) |

### 9.3 Mahnwesen

Die Entity `ProjektGeschaeftsdokument` unterstützt Mahnstufen (`Mahnstufe`-Enum):

| Stufe | Beschreibung |
|---|---|
| `ZAHLUNGSERINNERUNG` | Zahlungserinnerung (freundlicher Hinweis) |
| `ERSTE_MAHNUNG` | 1. Mahnung |
| `ZWEITE_MAHNUNG` | 2. Mahnung |

Mahnungen werden als Nachfolger-Dokumente verknüpft (`referenzDokument` → Original-Rechnung).

---

## 10. PDF-Generierung (`RechnungPdfService`)

### 10.1 Architektur

Die PDF-Generierung verwendet iText mit dem **ColumnText-Pouring-Prinzip**:
1. Inhalt wird in einen ColumnText-Container geschrieben
2. Bei Seitenüberlauf wird automatisch eine neue Seite angelegt
3. Folgeseiten erhalten einen eigenen Header (`renderFolgeSeitenKopf`)

### 10.2 PDF-Aufbau

```
┌─────────────────────────────┐
│  Hintergrundbild (Base64)   │
│  Briefkopf (Firmendaten)    │
│  Kundenadresse              │
│  Datum, Dokumentnummer      │
│  Betreff                    │
├─────────────────────────────┤
│  Positionen-Tabelle         │
│  (SERVICE, TEXT, SUBTOTAL)  │
│  ...                        │
├─────────────────────────────┤
│  Zusammenfassung (CLOSURE)  │
│  Netto, MwSt, Brutto       │
├─────────────────────────────┤
│  Abrechnungsverlauf         │
│  (bei Schlussrechnungen)    │
└─────────────────────────────┘
```

### 10.3 Content-Block-Typen im PDF

| Block-Typ | Rendering |
|---|---|
| `SERVICE` | Leistungszeile mit Menge × Preis = Gesamt |
| `TEXT` | HTML-formatierter Freitext (fett, kursiv, Listen, Bilder, Farben) |
| `CLOSURE` | Netto/MwSt/Brutto-Aufschlüsselung |
| `SEPARATOR` | Horizontale Linie |
| `SECTION_HEADER` | Fette Abschnitts-Überschrift |
| `SUBTOTAL` | Zwischensummen-Zeile |

### 10.4 HTML-Parsing

Der Service parst Rich-Text-HTML in iText-Elemente und unterstützt:
- **Formatierung:** Fett, kursiv, unterstrichen
- **Farben:** Text- und Hintergrundfarben
- **Schriftgrößen:** Verschiedene Font-Sizes
- **Listen:** Aufzählungen und nummerierte Listen
- **Bilder:** Eingebettete Bilder
- **Formular-Blöcke:** Spezielle Layout-Blöcke aus dem Document-Builder

### 10.5 ZUGFeRD-Integration

Nach der PDF-Generierung kann das PDF mit strukturierten ZUGFeRD-Metadaten angereichert werden (siehe `ZUGFERD_E_INVOICING.md`).

---

## 11. Rechnungsübersicht

### 11.1 Frontend-Ansicht

Die Rechnungsübersicht (`/rechnungsuebersicht`) zeigt alle Eingangs- und Ausgangsrechnungen gruppiert nach Monat/Jahr.

### 11.2 API-Endpoints

| Endpoint | Beschreibung |
|---|---|
| `GET /api/rechnungsuebersicht/ausgang` | Ausgangsrechnungen (gefiltert nach Jahr/Monat/Suche) |
| `GET /api/rechnungsuebersicht/eingang` | Eingangsrechnungen (gefiltert nach Jahr/Monat/Suche) |
| `POST /api/rechnungsuebersicht/merge-pdf` | Sammel-PDF aus ausgewählten Rechnungen erstellen |
| `POST /api/rechnungsuebersicht/analyze-upload` | Eingangsrechnung per KI analysieren |
| `POST /api/rechnungsuebersicht/import-upload` | Analysierte Eingangsrechnung importieren |

### 11.3 Filter

- **Jahr:** Filterung nach Rechnungsjahr
- **Monat:** Filterung nach Rechnungsmonat
- **Suche:** Volltextsuche über Dokumentnummern, Betreff, Kundennamen

### 11.4 Sammel-PDF-Export

Ausgewählte Rechnungen (Eingang und/oder Ausgang) können als zusammengefügtes PDF exportiert werden. Der Dateiname folgt dem Schema: `Rechnungen_YYYY_MM.pdf`.

---

## 12. API-Referenz (Ausgangs-Geschäftsdokumente)

### 12.1 Basis-URL

```
/api/ausgangs-dokumente
```

### 12.2 Endpoints

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{id}` | Einzelnes Dokument abrufen |
| `GET` | `/projekt/{projektId}` | Alle Dokumente eines Projekts |
| `GET` | `/angebot/{angebotId}` | Alle Dokumente eines Angebots |
| `POST` | `/` | Neues Dokument erstellen |
| `PUT` | `/{id}` | Dokument aktualisieren (nur wenn bearbeitbar) |
| `POST` | `/{id}/buchen` | Dokument buchen (sperrt Rechnungstypen) |
| `POST` | `/{id}/email-versendet` | Nach E-Mail-Versand buchen + Versanddatum |
| `POST` | `/{id}/storno` | Dokument stornieren (erstellt Gegendokument) |
| `DELETE` | `/{id}?begruendung=...` | Entwurf löschen (nur ungebuchte) |
| `GET` | `/{id}/abrechnungsverlauf` | Abrechnungsverlauf abrufen |

### 12.3 Request/Response-DTOs

**Erstellen (`AusgangsGeschaeftsDokumentErstellenDto`):**
- `typ` – Dokumenttyp (Pflicht)
- `projektId` / `angebotId` – Kontext-Zuordnung
- `kundeId` – Kunde für Rechnungsadresse
- `betreff` – Betreff/Titel
- `htmlInhalt` – HTML-Inhalt
- `positionenJson` – Positionen als JSON
- `betragNetto`, `betragBrutto`, `mwstSatz` – Beträge
- `vorgaengerId` – Vorgänger-Dokument für Kette
- `rechnungsadresseOverride` – Optionaler Adress-Override

**Response (`AusgangsGeschaeftsDokumentResponseDto`):**
- Alle Felder der Entity + berechnete Felder
- `istBearbeitbar` – Flag ob das Dokument bearbeitet werden darf
- `projektName` – Name des verknüpften Projekts
- `kundeName` – Name des Kunden
- `vorgaengerNummer` – Dokumentnummer des Vorgängers
