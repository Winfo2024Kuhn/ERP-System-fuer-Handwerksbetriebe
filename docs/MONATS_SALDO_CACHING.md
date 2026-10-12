# Monats-Saldo-Caching – Performance-Optimierung für Zeitkonto-Salden

> **Stand:** Oktober 2026

## Übersicht

Dieses Dokument beschreibt das Monats-Saldo-Caching-System, das die Performance bei der Abfrage monatlicher Zeitkonto-Salden optimiert. Der Cache speichert berechnete Saldenwerte für vergangene Monate und wird automatisch bei Datenänderungen invalidiert.

Seit dem Monatsabschluss hat die Tabelle `monats_saldo` eine zweite Aufgabe: Ein **abgeschlossener** (festgeschriebener) Monat bewahrt den vom Büro geprüften Stand. Er wird nicht mehr neu berechnet und ist Grundlage für den DATEV-Export (siehe `docs/benutzer/monatsabschluss.md` und `docs/benutzer/datev-lodas-export.md`).

---

## 1. Zweck

### 1.1 Problem

Die Berechnung monatlicher Zeitkonto-Salden erfordert die Aggregation mehrerer Datenquellen:
- Zeitbuchungen (Ist-Stunden)
- Zeitkonto-Versionen (Soll-Stunden je Wochentag, zeitlich versioniert)
- Abwesenheiten (Urlaub, Krankheit, Fortbildung, Zeitausgleich)
- Phasen einer Langzeitkrankmeldung (Wiedereingliederung senkt das Soll)
- Feiertage
- Korrekturen

Bei historischen Monaten ändert sich das Ergebnis selten, die Berechnung ist aber aufwändig.

### 1.2 Lösung

Ein **Performance-Cache** speichert berechnete Saldenwerte für vergangene Monate. Aktuelle und zukünftige Monate werden immer live berechnet. Abgeschlossene Monate werden immer aus der Tabelle gelesen.

> ⚠️ **Wichtig:** Für **offene** Monate ist der MonatsSaldo ein reiner Performance-Cache, **kein rechtlich bindendes Dokument**. Die Quelle der Wahrheit sind die Originaldaten (Zeitbuchungen, Abwesenheiten, Korrekturen) mit ihrem Audit-Trail. Für **abgeschlossene** Monate hält der Eintrag den geprüften Monatsstand fest; geändert werden kann er nur, indem der Monat wieder geöffnet wird.

---

## 2. Entity `MonatsSaldo`

### 2.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `version` | `Long` | Optimistische Sperre (`@Version`); wird auch bei jeder Invalidierung erhöht |
| `mitarbeiter` | `Mitarbeiter` (FK) | Zugehöriger Mitarbeiter |
| `jahr` | `Integer` | Jahr (z.B. 2026) |
| `monat` | `Integer` | Monat (1–12) |
| `istStunden` | `BigDecimal` | Tatsächlich geleistete Stunden (aus Zeitbuchungen, ohne PAUSE) |
| `sollStunden` | `BigDecimal` | Soll-Stunden (aus den Zeitkonto-Versionen, siehe 3.2) |
| `abwesenheitsStunden` | `BigDecimal` | Stunden durch Abwesenheit (Urlaub, Krankheit, Fortbildung, Zeitausgleich) |
| `urlaubStunden`, `krankheitStunden`, `fortbildungStunden`, `zeitausgleichStunden`, `krankengeldStunden`, `wiedereingliederungStunden` | `BigDecimal` (nullable) | Aufteilung der Abwesenheit; wird **nur beim Abschluss** gespeichert. `null` bei Abschlüssen vor Einführung der Aufteilung |
| `feiertagsStunden` | `BigDecimal` | Bezahlte Feiertags-Stunden (50 % für halbe Feiertage) |
| `korrekturStunden` | `BigDecimal` | Manuelle Stunden-Korrekturen (aus ZeitkontoKorrektur) |
| `gueltig` | `Boolean` | Cache-Gültigkeitsflag |
| `berechnetAm` | `LocalDateTime` | Zeitpunkt der letzten Berechnung |
| `festgeschrieben` | `Boolean` | Monat abgeschlossen (Monatsabschluss) |
| `festgeschriebenAm` | `LocalDateTime` | Zeitpunkt des Abschlusses |
| `festgeschriebenVon` | `Mitarbeiter` (FK) | Wer abgeschlossen hat |

### 2.2 Unique Constraint

`uk_monats_saldo_mitarbeiter_jahr_monat` auf `(mitarbeiter_id, jahr, monat)` – Pro Mitarbeiter und Monat existiert maximal ein Saldo-Eintrag.

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

- Quelle: `Zeitbuchung`-Entity (Startzeit im Monat)
- Berechnung: Summe aller `anzahlInStunden` für den Monat
- Ausnahmen: PAUSE-Buchungen und noch laufende Buchungen (`anzahlInStunden = null`) werden **nicht** gezählt

### 3.2 Soll-Stunden

- Quelle: `ZeitkontoVersion` des Mitarbeiters – für jeden Tag gilt die an diesem Tag gültige Version mit ihren Stunden je Wochentag
- Berechnung: `ZeitkontoService.berechneSollstundenFuerMonat()` → `TagesSollService.periodenSollSumme()`
- Feiertag auf einem Arbeitstag: Soll bleibt voll (bezahlter Arbeitstag); halber Feiertag: Soll 50 %
- Läuft eine Wiedereingliederung (Langzeitkrankmeldung), ersetzen deren Stunden pro Tag das Tages-Soll (höchstens bis zum Zeitkonto-Soll)

### 3.3 Abwesenheits-Stunden

- Quelle: `Abwesenheit`-Entity (`AbwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween`)
- Typen: Urlaub, Krankheit, Fortbildung, Zeitausgleich
- Berechnung: Summe der Stunden aller gebuchten Abwesenheiten im Monat. Abwesenheiten entstehen beim Genehmigen eines Antrags oder durch manuelles Buchen am PC; ein stornierter Antrag löscht seine Abwesenheiten wieder. Eine Langzeitkrankmeldung legt keine Abwesenheiten an, sondern ordnet vorhandene Krankheitstage ihren Phasen (Krankengeld, Wiedereingliederung) zu.
- Beim Abschluss wird die Summe zusätzlich nach Urlaub, Krankheit, Fortbildung, Zeitausgleich, Krankengeld und Wiedereingliederung aufgeteilt gespeichert (`sichereAbwesenheitsDetails`). Passt die Aufteilung nicht zur Summe, wird der Abschluss mit `409` abgelehnt.

### 3.4 Feiertags-Stunden

- Quelle: `TagesSollService.feiertagsGutschriftSumme()` mit den Feiertagen aus `FeiertagService` – **nur Bayern** (`"BY"`, fest im Code)
- Bezahlte Stunden für Feiertage, die auf Arbeitstage (Tages-Soll > 0) fallen
- Halbe Feiertage: 50 % der Tagesstunden

### 3.5 Korrektur-Stunden

- Quelle: `ZeitkontoKorrektur`-Entity
- Nur Korrekturen vom Typ `STUNDEN` (nicht `URLAUB`), nicht storniert, mit Datum im Monat
- Manuelle Anpassungen (positiv oder negativ)
- Jede Korrektur hat einen eigenen Audit-Trail (`ZeitkontoKorrekturAudit`)

---

## 4. Caching-Strategie

### 4.1 Entscheidungslogik

```
Abfrage: getOrBerechne(mitarbeiterId, jahr, monat)
    │
    ├── Mitarbeiter sperren (PESSIMISTIC_WRITE), Saldo-Zeile sperren
    │
    ├── Monat abgeschlossen (festgeschrieben = true)?
    │    └── JA → Gespeicherten Stand zurückgeben 🔒
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

Daneben gibt es `berechneOhneSpeichern()` – eine schreibfreie Vorschau (abgeschlossene Monate kommen aus der Tabelle, alle anderen werden live berechnet). Sie wird z. B. bei der Vorschau eines Zeitkonto-Wechsels und im Kalender am PC genutzt.

### 4.2 Übersicht

| Zeitraum | Caching-Verhalten |
|---|---|
| **Abgeschlossener Monat** | 🔒 Festgehaltener Stand, nie neu berechnet |
| **Aktueller Monat** | ❌ Immer live berechnen |
| **Zukünftiger Monat** | ❌ Immer live berechnen |
| **Vergangener Monat + Cache gültig** | ✅ Cache verwenden |
| **Vergangener Monat + Cache ungültig** | ♻️ Neuberechnung + Cache aktualisieren |
| **Vergangener Monat + kein Cache** | ♻️ Erstberechnung + Cache speichern |

### 4.3 Wer liest den Cache?

- `ZeiterfassungApiService` – Saldo für die Mobile-App (`GET /api/zeiterfassung/saldo/{token}`)
- `MonatsabschlussUebersichtService` – Monatsübersicht im Monatsabschluss
- `MonatsSaldoService.berechneGesamtsaldo()` – Gesamtsaldo bis zu einem Stichtag (ab Eintrittsdatum bzw. erster Buchung; angebrochene erste/letzte Monate werden live gerechnet; Geschäftsführer → 0)
- `MonatsSaldoWarmupService` – Vorberechnung beim Start

---

## 5. Invalidierung

### 5.1 Prinzip

Wenn sich Quelldaten ändern, wird der zugehörige Cache als ungültig markiert (`gueltig = false`, `version + 1`). Die Neuberechnung erfolgt erst bei der nächsten Abfrage (**Lazy Invalidation**).

**Abgeschlossene Monate werden nie invalidiert** – die Update-Abfragen in `MonatsSaldoRepository` schließen `festgeschrieben = true` aus. Zusätzlich lehnen das Ändern/Stornieren von Zeitbuchungen am PC und das Buchen/Löschen von Abwesenheiten in einem abgeschlossenen Monat mit `409` ab („Dieser Monat ist bereits festgeschrieben. Bitte setzen Sie zuerst den Monatsabschluss zurück.“).

### 5.2 Invalidierungs-Trigger

| Änderung | Wo | Invalidierte Einträge |
|---|---|---|
| Zeitbuchung am Handy gestartet / gestoppt / Pause / Nachtrag | `ZeiterfassungApiService` | Monat der Buchung (Startzeit) |
| Zeitbuchung automatisch beendet | `ZeitbuchungAutoStopService` | Monat der Buchung |
| Zeitbuchung am PC erstellt / geändert / storniert | `ZeitverwaltungController` | Monat der Buchung |
| Abwesenheit manuell gebucht / gelöscht | `AbwesenheitService` | Monat der Abwesenheit |
| Urlaubsantrag genehmigt / storniert | `UrlaubsantragService` | Alle Monate von–bis |
| Langzeitkrankmeldung angelegt / geändert / beendet / abgebrochen, Phase geändert | `LangzeitkrankmeldungService` | Alle Monate von Beginn bis Ende |
| ZeitkontoKorrektur erstellt / geändert / storniert | `ZeitkontoKorrekturService` | Monat der Korrektur |
| Neue Arbeitszeit (Zeitkonto-Version) / Zeitkonto ausgeschaltet | `ZeitkontoService` | Alle nicht abgeschlossenen Monate des Mitarbeiters |
| Monat wieder geöffnet | `MonatsSaldoService.oeffnen()` | Dieser Monat (sofort neu berechnet) |

Eine neue Zeitkonto-Version darf nicht in einem bereits abgeschlossenen Monat beginnen (`ZeitkontoService.pruefeOffeneMonate`).

### 5.3 Invalidierungs-Methoden

| Methode | Beschreibung |
|---|---|
| `invalidiereMonat(mitarbeiterId, jahr, monat)` | Einzelnen Monat invalidieren |
| `invalidiereJahr(mitarbeiterId, jahr)` | Gesamtes Jahr invalidieren (derzeit ohne Aufrufer) |
| `invalidiereAlle(mitarbeiterId)` | Alle Einträge des Mitarbeiters invalidieren |
| `invalidiereFuerDatum(mitarbeiterId, datum)` | Monat basierend auf einem Datum invalidieren |
| `invalidiereFuerDateTime(mitarbeiterId, dateTime)` | Monat basierend auf einem DateTime invalidieren |

Alle Methoden sperren vorher den Mitarbeiter. `ZeitkontoService` ruft direkt `MonatsSaldoRepository.invalidiereAlle()` auf.

### 5.4 Transaktions-Handling

- `getOrBerechne()` läuft in einer **eigenen `REQUIRES_NEW`-Transaktion** und darf nicht aufgerufen werden, während der Aufrufer schon die Mitarbeitersperre hält.
- `saveMonatsSaldoCache()` läuft **in der Transaktion des Aufrufers** und sperrt Mitarbeiter und Saldo-Zeile (`PESSIMISTIC_WRITE`). So werden das erste Anlegen eines Eintrags und ein gleichzeitiger Monatsabschluss serialisiert; ein bereits abgeschlossener Monat wird nie überschrieben.
- Monatsabschluss und Wiedereröffnung rechnen und speichern unter derselben Mitarbeitersperre (ohne `REQUIRES_NEW`).

---

## 6. Warmup (`MonatsSaldoWarmupService`)

### 6.1 Zweck

Der Warmup-Service füllt den Cache beim Anwendungsstart vor, damit die ersten Abfragen nicht alle Saldenwerte live berechnen müssen. Er läuft im `ApplicationReadyEvent`-Listener (ohne `@Async`) und kann zusätzlich über `POST /api/zeitverwaltung/saldo-cache/warmup` angestoßen werden.

### 6.2 Ablauf

```
Anwendungsstart (ApplicationReadyEvent)
    │
    ▼
MonatsSaldoWarmupService.warmupCache()
    │
    ├── Alle aktiven Mitarbeiter laden
    │    └── nur Art MENSCH mit fuehrtZeitkonto = true
    │
    ├── Pro Mitarbeiter:
    │    ├── Start-Monat: erste Zeitbuchung, sonst Eintrittsdatum (ohne beides: nichts tun)
    │    ├── Bis zum Vormonat iterieren
    │    ├── Abgeschlossene oder gültige Einträge überspringen
    │    └── Fehlende/ungültige Einträge über getOrBerechne() berechnen + speichern
    │
    ▼
Cache aufgewärmt ✅
    └── Log: "MonatsSaldo-Cache Warmup abgeschlossen: X Mitarbeiter, Y Monate berechnet, Z übersprungen, Dauer: N ms"
```

Fehler bei einem Mitarbeiter werden geloggt und brechen den Warmup für die anderen nicht ab.

### 6.3 Performance-Logging

Der Warmup-Service protokolliert:
- Anzahl aktiver Mitarbeiter
- Anzahl berechneter und übersprungener Monate
- Gesamtdauer in Millisekunden

---

## 7. Saldo-Berechnung im Detail

### 7.1 Methode `berechneMonatsSaldo()`

```
Input: mitarbeiterId, jahr, monat

1. Ist-Stunden berechnen:
   → Summe(Zeitbuchung.anzahlInStunden) WHERE startZeit im Monat, PAUSE und offene Buchungen ausgenommen

2. Soll-Stunden berechnen:
   → ZeitkontoService.berechneSollstundenFuerMonat()
   → je Tag: Stunden der gültigen Zeitkonto-Version für den Wochentag
     (Wiedereingliederung ersetzt das Tages-Soll, halber Feiertag = 50 %)

3. Abwesenheits-Stunden berechnen:
   → Summe(Abwesenheit.stunden) im Monat

4. Feiertags-Stunden berechnen:
   → TagesSollService.feiertagsGutschriftSumme(mitarbeiterId, erster, letzter Tag)
   → Nur Feiertage (Bayern) auf Arbeitstagen
   → 50% für halbe Feiertage

5. Korrektur-Stunden berechnen:
   → Summe(ZeitkontoKorrektur.stunden) WHERE datum im Monat, Typ STUNDEN, nicht storniert

6. MonatsSaldo erstellen (transient):
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

## 8. Monatsabschluss (Festschreiben)

| Methode | Wirkung |
|---|---|
| `abschliessen()` / `abschliessenOhneVerlauf()` | Nur vergangene Monate. Abgelehnt (`409`) für System-Mitarbeiter, Geschäftsführer, Mitarbeiter ohne Zeiterfassung und bereits abgeschlossene Monate. Rechnet frisch, speichert die Abwesenheits-Aufteilung, setzt `festgeschrieben`, `festgeschriebenAm`, `festgeschriebenVon` und schreibt `MonatsabschlussAudit` (`ABSCHLIESSEN`). |
| `oeffnen()` | Hebt den Abschluss auf, setzt `gueltig = false`, rechnet sofort neu und schreibt `MonatsabschlussAudit` (`OEFFNEN`). |
| `status()` | Monatsstand inkl. Verlauf (Abschlüsse/Wiedereröffnungen). |

Abschließen und Wiederöffnen darf nur, wessen Mitarbeiter in einer Abteilung mit dem Recht „Monate abschließen und wieder öffnen“ ist (`MonatsabschlussBerechtigungService`). Der Sammelabschluss für bis zu 500 Mitarbeiter läuft über `MonatsabschlussSammelService` (eine Transaktion je Mitarbeiter).

---

## 9. Entitäten-Übersicht

| Entity / Service | Zweck |
|---|---|
| `MonatsSaldo` | Cache-Entity für berechnete Monatssalden und festgehaltene Monatsstände |
| `MonatsSaldoService` | Caching-Logik, Berechnung, Invalidierung, Abschluss/Wiedereröffnung |
| `MonatsSaldoWarmupService` | Vorberechnung beim Anwendungsstart |
| `MonatsSaldoRepository` | Datenbankzugriff mit Invalidierungs-Methoden (ohne abgeschlossene Monate) |
| `TagesSollService` | Tages-Soll, Feiertagsgutschrift, Wiedereingliederung |
| `MonatsabschlussAudit` | Verlauf von Abschluss und Wiedereröffnung |
| `MonatsabschlussSammelService` / `MonatsabschlussUebersichtService` | Sammelabschluss und Monatsübersicht |
