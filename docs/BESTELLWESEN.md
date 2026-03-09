# Bestellwesen – Einkauf, Bestellungen, PDF-Generierung

## Übersicht

Dieses Dokument beschreibt das Bestellwesen im Kalkulationsprogramm – von der Erfassung offener Artikel über die Gruppierung nach Lieferant und Projekt bis zur Bestellmarkierung und PDF-Generierung.

---

## 1. Offene Bestellungen

### 1.1 Prinzip

Artikel, die einem Projekt zugewiesen aber noch nicht bestellt sind (`bestellt = false`), erscheinen in der offenen Bestellliste. Die Bestellung erfolgt projektbezogen und lieferantengruppiert.

### 1.2 Entity `ArtikelInProjekt`

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `projekt` | `Projekt` (FK) | Zugehöriges Projekt |
| `artikel` | `Artikel` (FK) | Zugehöriger Artikel |
| `lieferant` | `Lieferant` (FK) | Lieferant für die Bestellung |
| `lieferantenArtikelPreis` | `LieferantenArtikelPreis` (FK) | Preisreferenz des Lieferanten |
| `bestellt` | `Boolean` | `true` wenn bestellt |
| `bestelltAm` | `LocalDate` | Datum der Bestellung |
| `kilogramm` | `BigDecimal` | Gewicht in Kilogramm |
| `meter` | `BigDecimal` | Länge in Metern |
| `stueckzahl` | `BigDecimal` | Anzahl Stück |
| `preis` | `BigDecimal` | Berechneter Endpreis |
| `kommentar` | `String` | Optionaler Kommentar |

---

## 2. Gruppierung

### 2.1 Nach Lieferant + Projekt

Offene Bestellungen werden gruppiert dargestellt:
1. **Primär** nach Lieferant (alle Artikel desselben Lieferanten zusammen)
2. **Sekundär** nach Projekt (innerhalb eines Lieferanten nach Projekt sortiert)

### 2.2 Beispiel

```
Lieferant: Stahlhandel Müller
   ├── Projekt A: Dachsanierung
   │    ├── Flachstahl 40×5mm – 120 kg
   │    └── Rundrohr 48,3×3,2mm – 50 lfd. Meter
   │
   └── Projekt B: Carport
        └── Winkelstahl 50×50×5mm – 30 kg

Lieferant: Schrauben Schmidt
   └── Projekt A: Dachsanierung
        ├── Sechskantschrauben M12 – 200 Stück
        └── Unterlegscheiben M12 – 200 Stück
```

---

## 3. Preisberechnung nach Verrechnungseinheit

### 3.1 Verrechnungseinheiten (`Verrechnungseinheit`-Enum)

| Enum-Wert | Beschreibung | Berechnung |
|---|---|---|
| `KILOGRAMM` | Preis pro Kilogramm | `Preis × kg` |
| `LAUFENDE_METER` | Preis pro laufenden Meter | `Preis × Meter` |
| `QUADRATMETER` | Preis pro Quadratmeter | `Preis × m²` |
| `STUECK` | Stückpreis | `Preis × Stückzahl` |

### 3.2 Endpreis-Berechnung

Der Endpreis wird bei der Bestellung automatisch berechnet:

```
Endpreis = Einzelpreis × Menge (in der jeweiligen Verrechnungseinheit)
```

**Beispiele:**

| Artikel | VE | Einzelpreis | Menge | Endpreis |
|---|---|---|---|---|
| Flachstahl 40×5mm | kg | 1,80 €/kg | 120 kg | 216,00 € |
| Rundrohr 48,3mm | lfd. m | 12,50 €/m | 50 m | 625,00 € |
| Trapezblech | m² | 18,00 €/m² | 45 m² | 810,00 € |
| Schrauben M12 | Stück | 0,35 €/Stk | 200 Stk | 70,00 € |

---

## 4. Bestellvorgang

### 4.1 Ablauf

```
Offene Artikel (bestellt = false)
    │
    ▼
[setBestellt()] aufrufen
    │
    ├── Endpreis berechnen (basierend auf Verrechnungseinheit)
    ├── bestellt = true setzen
    ├── bestelltAm = heute setzen
    │
    ▼
Artikel als bestellt markiert
```

### 4.2 Service-Methoden

| Methode | Beschreibung |
|---|---|
| `findeOffeneBestellungen()` | Alle unbestellten Artikel gruppiert nach Lieferant/Projekt |
| `setBestellt(id, bestellt)` | Artikel als bestellt/unbestellt markieren |

---

## 5. Bestellungs-PDF (`BestellungPdfService`)

### 5.1 Aufbau

Das Bestellungs-PDF enthält:
- **Kopfbereich**: Firmenlogo, Lieferantenadresse, Datum
- **Positionen-Tabelle**: Gruppiert nach Projekt
  - Artikelbezeichnung
  - Externe Artikelnummer
  - Werkstoff
  - Menge und Verrechnungseinheit
  - Kilogrammgewicht (bei kg-Berechnung)
  - Kommentar
- **Fußbereich**: Ansprechpartner, Lieferbedingungen

### 5.2 Generierung

```
Bestelldaten (nach Lieferant gruppiert)
    │
    ▼
BestellungPdfService.erzeugePdf()
    │
    ▼
Bestellungs-PDF (pro Lieferant/Projekt)
```

---

## 6. Materialkosten-Tracking

### 6.1 Projektzuordnung

Jede Bestellung ist einem Projekt zugeordnet. Die Materialkosten fließen in die Projektkalkulation ein:

```
Projekt-Materialkosten = Summe(alle bestellten ArtikelInProjekt.preis)
```

### 6.2 Kilogramm-Berechnung

Für Werkstoff-Artikel wird das Gesamtgewicht automatisch berechnet. Das Feld `gesamtKilogramm` in der Response enthält das berechnete Gewicht basierend auf Artikeldaten und Mengenangabe.

---

## 7. API-Referenz

### 7.1 Bestellungen

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/bestellungen/offen` | Alle offenen (unbestellten) Artikel |
| `GET` | `/api/bestellungen/projekt/{projektId}/pdf` | Bestellungs-PDF für ein Projekt |

### 7.2 Bestellübersicht

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/bestellungen-uebersicht/` | Bestellübersicht |
| `GET` | `/api/bestellungen-uebersicht/geschaeftsdaten/{dokId}` | Geschäftsdaten einer Bestellung |
| `PUT` | `/api/bestellungen-uebersicht/geschaeftsdaten/{dokId}` | Geschäftsdaten aktualisieren |

### 7.3 Response-DTO (`BestellungResponseDto`)

| Feld | Beschreibung |
|---|---|
| `id` | Artikel-in-Projekt-ID |
| `artikelId` | Artikel-ID |
| `externeArtikelnummer` | Artikelnummer des Lieferanten |
| `produktname` | Artikelname |
| `produkttext` | Artikelbeschreibung |
| `kommentar` | Bestellkommentar |
| `werkstoffName` | Werkstoff-Bezeichnung |
| `kategorieName` | Produktkategorie |
| `rootKategorieId` | ID der Hauptkategorie |
| `rootKategorieName` | Name der Hauptkategorie |
| `kilogramm` | Kilogramm pro Stück |
| `gesamtKilogramm` | Berechnetes Gesamtgewicht |

---

## 8. Entitäten-Übersicht

| Entity / Enum | Zweck |
|---|---|
| `ArtikelInProjekt` | Zuordnung Artikel → Projekt mit Bestellstatus |
| `Verrechnungseinheit` (Enum) | Einheiten: Kilogramm, Laufende Meter, Quadratmeter, Stück |
| `LieferantenArtikelPreis` | Preisreferenz eines Artikels bei einem Lieferanten |

| Service | Verantwortung |
|---|---|
| `BestellungService` | Offene Bestellungen finden, Bestellstatus setzen, Preisberechnung |
| `BestellungPdfService` | PDF-Generierung für Bestellungen |
