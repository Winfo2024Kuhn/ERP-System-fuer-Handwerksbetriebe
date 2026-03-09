# Zahlungsverkehr – Zahlungsverfolgung, Offene Posten, Mahnwesen

## Übersicht

Dieses Dokument beschreibt die Zahlungsverfolgung im Kalkulationsprogramm – von der Rechnungsbuchung über Teilzahlungen und Offene-Posten-Verwaltung bis zum Mahnwesen.

---

## 1. Zahlungs-Entity (`Zahlung`)

### 1.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `geschaeftsdokument` | `Geschaeftsdokument` (FK) | Zugehöriges Geschäftsdokument |
| `zahlungsdatum` | `LocalDate` | Datum der Zahlung |
| `betrag` | `BigDecimal` | Gezahlter Betrag |
| `zahlungsart` | `String` | Art der Zahlung (Überweisung, Bar, PayPal etc.) |
| `verwendungszweck` | `String` | Verwendungszweck |
| `notiz` | `String` | Optionale Notiz zur Zahlung |

### 1.2 Zuordnung

Jede Zahlung ist einem `Geschaeftsdokument` zugeordnet. Pro Dokument können **mehrere Zahlungen** erfasst werden (Teilzahlungen).

---

## 2. Teilzahlungen

### 2.1 Prinzip

Ein Geschäftsdokument kann mehrere Zahlungen enthalten. Die Entity `Geschaeftsdokument` bietet dafür folgende Methoden:

```java
List<Zahlung> zahlungen;              // Alle Zahlungen zum Dokument

BigDecimal getSummeZahlungen()        // Summe aller Zahlungen
BigDecimal getOffenerBetrag()         // betragBrutto - Summe(Zahlungen)
boolean istBezahlt()                  // offenerBetrag <= 0
```

### 2.2 Berechnung des offenen Betrags

```
Offener Betrag = Bruttobetrag − Summe(alle Zahlungen)
```

Sobald der offene Betrag ≤ 0 ist, gilt das Dokument als **vollständig bezahlt**.

### 2.3 Beispiel

```
Rechnung: 10.000 € brutto
  ├── Zahlung 1: 3.000 € (15.03.2026)  → Offen: 7.000 €
  ├── Zahlung 2: 5.000 € (01.04.2026)  → Offen: 2.000 €
  └── Zahlung 3: 2.000 € (15.04.2026)  → Offen: 0 € ✅ Bezahlt
```

---

## 3. Offene-Posten-Verwaltung

### 3.1 Ausgangsrechnungen (Offene Posten Ausgang)

Beim Buchen einer Ausgangsrechnung wird automatisch ein `ProjektGeschaeftsdokument`-Eintrag erstellt:

| Feld | Quelle |
|---|---|
| `dokumentid` | Dokumentnummer des `AusgangsGeschaeftsDokuments` |
| `geschaeftsdokumentart` | Gemappter Typ ("Rechnung", "Abschlagsrechnung" etc.) |
| `rechnungsdatum` | Datum des Dokuments |
| `bruttoBetrag` | Bruttobetrag des Dokuments |
| `bezahlt` | Initial `false` |
| `faelligkeitsdatum` | `datum + zahlungszielTage` (falls gesetzt) |

### 3.2 Eingangsrechnungen (Offene Posten Eingang)

Lieferantenrechnungen (`LieferantGeschaeftsdokument`) verwalten den Zahlungsstatus direkt:

| Feld | Beschreibung |
|---|---|
| `bezahlt` | `true` wenn vollständig bezahlt |
| `bezahltAm` | Datum der Zahlung |
| `tatsaechlichGezahlt` | Gezahlter Betrag (nach Skonto-Abzug) |
| `mitSkonto` | `true` wenn Skonto genutzt wurde |
| `bereitsGezahlt` | Von KI erkannt (z.B. Vorauskasse) |

### 3.3 API-Endpoints

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/offene-posten/eingang` | Offene Posten Eingang (abteilungsbasiert gefiltert) |
| `GET` | `/api/offene-posten/eingang/alle` | Alle offenen Posten Eingang |

---

## 4. Abteilungs-Berechtigungen

### 4.1 Sichtbarkeit

| Abteilung | Rechte |
|---|---|
| **Abt. 3 (Büro)** | Sieht alle Dokumente, kann genehmigen |
| **Abt. 2 (Buchhaltung)** | Sieht nur genehmigte Dokumente |

### 4.2 Genehmigungsprozess

```
Dokument eingegangen
   │
   └─[Büro-Genehmigung]──► genehmigt = true
                              → Sichtbar für Buchhaltung
                              → Zahlungsverfolgung aktiv
```

---

## 5. Mahnwesen

### 5.1 Mahnstufen (`Mahnstufe`-Enum)

| Stufe | Enum-Wert | Beschreibung |
|---|---|---|
| Stufe 1 | `ZAHLUNGSERINNERUNG` | Zahlungserinnerung (freundlicher Hinweis) |
| Stufe 2 | `ERSTE_MAHNUNG` | 1. Mahnung |
| Stufe 3 | `ZWEITE_MAHNUNG` | 2. Mahnung |

### 5.2 Mahnstufen-Tracking

Die Mahnstufe wird auf der Entity `ProjektGeschaeftsdokument` verwaltet:

| Feld | Typ | Beschreibung |
|---|---|---|
| `mahnstufe` | `Mahnstufe` (Enum) | Aktuelle Mahnstufe des Dokuments |
| `referenzDokument` | `ProjektGeschaeftsdokument` (FK) | Verweis auf die Original-Rechnung |
| `mahnungen` | `List<ProjektGeschaeftsdokument>` (1:N) | Liste aller Mahnungen zur Rechnung |

### 5.3 Eskalationsprozess

```
Rechnung fällig (faelligkeitsdatum überschritten)
   │
   ├─[Stufe 1]──► ZAHLUNGSERINNERUNG erstellt
   │                (referenzDokument → Original-Rechnung)
   │
   ├─[Stufe 2]──► ERSTE_MAHNUNG erstellt
   │                (referenzDokument → Original-Rechnung)
   │
   └─[Stufe 3]──► ZWEITE_MAHNUNG erstellt
                    (referenzDokument → Original-Rechnung)
```

### 5.4 Dokumenttypen für Mahnungen

Im `Dokumenttyp`-Enum sind Mahnungen als eigene Typen abgebildet:

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
| `skontoTage` | `Integer` | Tage für Skonto-Abzug (z.B. 10 Tage) |
| `skontoProzent` | `BigDecimal` | Skonto-Prozentsatz (z.B. 2.00%) |
| `nettoTage` | `Integer` | Zahlungsziel in Tagen (z.B. 30 Tage) |
| `mitSkonto` | `Boolean` | `true` wenn Skonto bei Zahlung genutzt wurde |
| `tatsaechlichGezahlt` | `BigDecimal` | Tatsächlich gezahlter Betrag nach Skonto |

### 6.2 Berechnung

```
Skonto-Betrag = Bruttobetrag × (skontoProzent / 100)
Zahlung mit Skonto = Bruttobetrag − Skonto-Betrag
```

### 6.3 Beispiel

```
Lieferantenrechnung: 5.000 € brutto
  Skontobedingungen: 2% bei Zahlung innerhalb 10 Tagen
  
  Zahlung innerhalb Skontofrist:
    tatsaechlichGezahlt = 4.900 €
    mitSkonto = true
```

---

## 7. Zahlungsbedingungen

### 7.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `zahlungsziel` | `LocalDate` | Fälligkeitsdatum |
| `zahlungszielTage` | `Integer` | Zahlungsziel in Tagen ab Rechnungsdatum |
| `skontoTage` | `Integer` | Tage für Skonto-Abzug |
| `skontoProzent` | `BigDecimal` | Skonto-Prozentsatz |
| `nettoTage` | `Integer` | Netto-Zahlungsziel in Tagen |

### 7.2 Fälligkeitsberechnung

```
faelligkeitsdatum = rechnungsdatum + zahlungszielTage
```

---

## 8. API-Referenz

### 8.1 Zahlungen

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/geschaeftsdokumente/{id}/zahlungen` | Neue Zahlung zu einem Geschäftsdokument hinzufügen |
| `GET` | `/api/geschaeftsdokumente/{id}` | Geschäftsdokument mit Zahlungsdetails abrufen |
| `GET` | `/api/geschaeftsdokumente/{id}/abschluss` | Abschluss-Info inkl. Zahlungsstatus |

### 8.2 Offene Posten

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/offene-posten/eingang` | Offene Posten Eingang (abteilungsbasiert) |
| `GET` | `/api/offene-posten/eingang/alle` | Alle offenen Posten Eingang |

---

## 9. Entitäten-Übersicht

| Entity | Zweck |
|---|---|
| `Zahlung` | Einzelne Zahlung zu einem Geschäftsdokument |
| `Geschaeftsdokument` | Zentrales Geschäftsdokument mit Zahlungsverfolgung |
| `ProjektGeschaeftsdokument` | Projekt-Offene-Posten mit Mahnstufen-Tracking |
| `Mahnstufe` (Enum) | Mahnstufen: Zahlungserinnerung, 1. Mahnung, 2. Mahnung |
| `Dokumenttyp` (Enum) | Dokumenttypen inkl. Mahnungsdokumente |
| `LieferantGeschaeftsdokument` | Eingangsrechnungen mit Skonto-/Zahlungsverwaltung |
