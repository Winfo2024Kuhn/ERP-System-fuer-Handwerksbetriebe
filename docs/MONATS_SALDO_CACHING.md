# Monats-Saldo-Caching – Performance-Optimierung für Zeitkonto-Salden

## Übersicht

Dieses Dokument beschreibt das Monats-Saldo-Caching-System, das die Performance bei der Abfrage monatlicher Zeitkonto-Salden optimiert. Der Cache speichert berechnete Saldenwerte für vergangene Monate und wird automatisch bei Datenänderungen invalidiert.

---

## 1. Zweck

### 1.1 Problem

Die Berechnung monatlicher Zeitkonto-Salden erfordert die Aggregation mehrerer Datenquellen:
- Zeitbuchungen (Ist-Stunden)
- Arbeitszeiten (Soll-Stunden)
- Abwesenheiten (Urlaub, Krankheit, Fortbildung, Zeitausgleich)
- Feiertage
- Korrekturen

Bei historischen Monaten ändert sich das Ergebnis selten, die Berechnung ist aber aufwändig.

### 1.2 Lösung

Ein **Performance-Cache** speichert berechnete Saldenwerte für vergangene Monate. Aktuelle und zukünftige Monate werden immer live berechnet.

> ⚠️ **Wichtig:** Der MonatsSaldo ist ein reiner Performance-Cache, **kein rechtlich bindendes Dokument**. Die Quelle der Wahrheit sind immer die Originaldaten (Zeitbuchungen, Abwesenheiten, Korrekturen).

---

## 2. Entity `MonatsSaldo`

### 2.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `mitarbeiter` | `Mitarbeiter` (FK) | Zugehöriger Mitarbeiter |
| `jahr` | `Integer` | Jahr (z.B. 2026) |
| `monat` | `Integer` | Monat (1–12) |
| `istStunden` | `BigDecimal` | Tatsächlich geleistete Stunden (aus Zeitbuchungen, ohne PAUSE) |
| `sollStunden` | `BigDecimal` | Soll-Stunden (aus Zeitkonto-Konfiguration) |
| `abwesenheitsStunden` | `BigDecimal` | Stunden durch Abwesenheit (Urlaub, Krankheit, Fortbildung, Zeitausgleich) |
| `feiertagsStunden` | `BigDecimal` | Bezahlte Feiertags-Stunden (50% für halbe Feiertage) |
| `korrekturStunden` | `BigDecimal` | Manuelle Korrekturen (aus ZeitkontoKorrektur) |
| `gueltig` | `Boolean` | Cache-Gültigkeitsflag |
| `berechnetAm` | `LocalDateTime` | Zeitpunkt der letzten Berechnung |

### 2.2 Unique Constraint

`(mitarbeiter_id, jahr, monat)` – Pro Mitarbeiter und Monat existiert maximal ein Saldo-Eintrag.

### 2.3 Berechnete Methoden

```java
BigDecimal getGesamtIst()
    // = istStunden + abwesenheitsStunden + feiertagsStunden + korrekturStunden

BigDecimal getDifferenz()
    // = getGesamtIst() - sollStunden
```

---

## 3. Komponenten der Saldo-Berechnung

### 3.1 Ist-Stunden

- Quelle: `Zeitbuchung`-Entity
- Berechnung: Summe aller `anzahlInStunden` für den Monat
- Ausnahme: PAUSE-Buchungen werden **nicht** gezählt

### 3.2 Soll-Stunden

- Quelle: `Zeitkonto`-Konfiguration des Mitarbeiters
- Basiert auf den regulären Arbeitszeiten (Wochenarbeitsstunden / Arbeitstage)

### 3.3 Abwesenheits-Stunden

- Quelle: `Abwesenheit`-Entity
- Typen: Urlaub, Krankheit, Fortbildung, Zeitausgleich
- Berechnung: Stunden für genehmigte Abwesenheiten im Monat

### 3.4 Feiertags-Stunden

- Quelle: `Feiertagservice` (Bayern)
- Bezahlte Stunden für Feiertage, die auf Arbeitstage fallen
- Halbe Feiertage: 50% der Tagesstunden

### 3.5 Korrektur-Stunden

- Quelle: `ZeitkontoKorrektur`-Entity
- Manuelle Anpassungen (positiv oder negativ)
- Jede Korrektur hat einen eigenen Audit-Trail

---

## 4. Caching-Strategie

### 4.1 Entscheidungslogik

```
Abfrage: getOrBerechne(mitarbeiterId, jahr, monat)
    │
    ├── Aktueller oder zukünftiger Monat?
    │    └── JA → Immer live berechnen (kein Cache)
    │
    └── Vergangener Monat?
         │
         ├── Cache vorhanden + gueltig = true?
         │    └── JA → Cache verwenden ✅
         │
         └── Cache fehlt oder gueltig = false?
              └── Neuberechnung, Ergebnis speichern ♻️
```

### 4.2 Übersicht

| Zeitraum | Caching-Verhalten |
|---|---|
| **Aktueller Monat** | ❌ Immer live berechnen |
| **Zukünftiger Monat** | ❌ Immer live berechnen |
| **Vergangener Monat + Cache gültig** | ✅ Cache verwenden |
| **Vergangener Monat + Cache ungültig** | ♻️ Neuberechnung + Cache aktualisieren |
| **Vergangener Monat + kein Cache** | ♻️ Erstberechnung + Cache speichern |

---

## 5. Invalidierung

### 5.1 Prinzip

Wenn sich Quelldaten ändern, wird der zugehörige Cache als ungültig markiert (`gueltig = false`). Die Neuberechnung erfolgt erst bei der nächsten Abfrage (**Lazy Invalidation**).

### 5.2 Invalidierungs-Trigger

| Änderung | Invalidierte Einträge |
|---|---|
| Zeitbuchung erstellt/geändert/storniert | Monat der Buchung |
| Abwesenheit erstellt/geändert/gelöscht | Betroffene Monate |
| ZeitkontoKorrektur erstellt/geändert | Monat der Korrektur |
| Zeitkonto-Konfiguration geändert | Gesamtes Jahr |
| Manuelle Invalidierung (Admin) | Alle Einträge |

### 5.3 Invalidierungs-Methoden

| Methode | Beschreibung |
|---|---|
| `invalidiereMonat(mitarbeiterId, jahr, monat)` | Einzelnen Monat invalidieren |
| `invalidiereJahr(mitarbeiterId, jahr)` | Gesamtes Jahr invalidieren |
| `invalidiereAlle(mitarbeiterId)` | Alle Einträge des Mitarbeiters invalidieren |
| `invalidiereFuerDatum(mitarbeiterId, datum)` | Monat basierend auf einem Datum invalidieren |
| `invalidiereFuerDateTime(mitarbeiterId, dateTime)` | Monat basierend auf einem DateTime invalidieren |

### 5.4 Transaktions-Handling

Das Speichern des Caches erfolgt in einer **separaten `REQUIRES_NEW`-Transaktion** (`saveMonatsSaldoCache()`). Dadurch werden Race Conditions bei parallelen Zugriffen vermieden – ein fehlgeschlagenes Cache-Update blockiert nicht die eigentliche Geschäftslogik.

---

## 6. Warmup (`MonatsSaldoWarmupService`)

### 6.1 Zweck

Der Warmup-Service füllt den Cache beim Anwendungsstart vor, damit die ersten Abfragen nicht alle Saldenwerte live berechnen müssen.

### 6.2 Ablauf

```
Anwendungsstart (ApplicationReadyEvent)
    │
    ▼
MonatsSaldoWarmupService.warmupCache()
    │
    ├── Alle aktiven Mitarbeiter laden
    │
    ├── Pro Mitarbeiter:
    │    ├── Erste Zeitbuchung finden (Start-Monat)
    │    ├── Bis zum vorherigen Monat iterieren
    │    ├── Bereits gültige Einträge überspringen
    │    └── Fehlende/ungültige Einträge berechnen + speichern
    │
    ▼
Cache aufgewärmt ✅
    └── Log: "[Warmup] X Mitarbeiter, Y Monate berechnet in Z ms"
```

### 6.3 Performance-Logging

Der Warmup-Service protokolliert:
- Anzahl verarbeiteter Mitarbeiter
- Anzahl berechneter Monate
- Gesamtdauer in Millisekunden

---

## 7. Saldo-Berechnung im Detail

### 7.1 Methode `berechneMonatsSaldo()`

```
Input: mitarbeiterId, jahr, monat

1. Ist-Stunden berechnen:
   → Summe(Zeitbuchung.anzahlInStunden) WHERE monat = X, PAUSE ausgenommen

2. Soll-Stunden berechnen:
   → Aus Zeitkonto-Konfiguration (Wochenstunden / Arbeitstage × Arbeitstage im Monat)

3. Abwesenheits-Stunden berechnen:
   → Summe(genehmigte Abwesenheiten im Monat)

4. Feiertags-Stunden berechnen:
   → Feiertagservice.getFeiertagsStunden(mitarbeiter, monat)
   → Nur Feiertage auf Arbeitstagen
   → 50% für halbe Feiertage

5. Korrektur-Stunden berechnen:
   → Summe(ZeitkontoKorrektur.stunden) WHERE monat = X

6. MonatsSaldo erstellen:
   → gueltig = true
   → berechnetAm = jetzt

Output: MonatsSaldo
```

### 7.2 Gesamt-Ist und Differenz

```
Gesamt-Ist = Ist-Stunden + Abwesenheits-Stunden + Feiertags-Stunden + Korrektur-Stunden
Differenz  = Gesamt-Ist − Soll-Stunden
```

**Interpretation der Differenz:**
- Differenz > 0 → **Überstunden** (mehr geleistet als erforderlich)
- Differenz = 0 → **Soll erfüllt** (exakt)
- Differenz < 0 → **Minusstunden** (weniger geleistet als erforderlich)

---

## 8. Entitäten-Übersicht

| Entity / Service | Zweck |
|---|---|
| `MonatsSaldo` | Cache-Entity für berechnete Monatssalden |
| `MonatsSaldoService` | Caching-Logik, Berechnung, Invalidierung |
| `MonatsSaldoWarmupService` | Vorberechnung beim Anwendungsstart |
| `MonatsSaldoRepository` | Datenbankzugriff mit Invalidierungs-Methoden |
