# Kasse & Buchhaltung (Doppik-Variante A)

> **Zielgruppe:** Inhaber, Buchhalter, Steuerberater.
> **Modul:** Buchhaltung → Kasse (Pfad: `/buchhaltung/kasse`).
> **Status:** Live ab Migrationen V302–V320.

Das Kasse-Modul ist das doppisch geführte Bar-Kassenbuch des Handwerksbetriebs. Es führt jede Bar-Bewegung als Beleg mit Soll-/Haben-Buchung, validiert den Bar-Saldo gegen Negativ-Bestände und exportiert monatlich ein steuerberater-fertiges T-Konto-PDF.

---

## 1. Bar-Saldo: die vier Kategorien

Der Bar-Saldo besteht ausschließlich aus Belegen mit einer der folgenden Kategorien (siehe `BelegKategorie.java`):

| Kategorie | Richtung | Beispiel |
|-----------|----------|----------|
| `KASSE_EINNAHME` | + Bar | Barzahlung von Kunde |
| `KASSE_AUSGABE` | − Bar | Material bar bezahlt |
| `PRIVATEINLAGE` | + Bar | Inhaber legt Geld aus dem Privaten in die Firma |
| `PRIVATENTNAHME` | − Bar | Inhaber entnimmt Bargeld für privat |

Nur Belege im Status `VALIDIERT` zählen mit. Die zentrale Berechnung liegt in `KasseSaldoService.berechneAktuellenSaldo()` – andere Services dürfen die Saldo-Mathematik **nicht** selbst nachbauen, sondern müssen den Service aufrufen.

### Saldo-Validierung

Vor jeder Buchung, die den Saldo senkt (`KASSE_AUSGABE`, `PRIVATENTNAHME`, Lohn-Zahlung), prüft `KasseSaldoService` per Vorschau, ob der Bar-Bestand nicht negativ wird. Bei Unterdeckung wirft der Service `KasseUnterdeckungException` – das Frontend zeigt einen Toast, der Beleg wird nicht persistiert.

---

## 2. Shortcuts (häufige Buchungen mit einem Klick)

Endpoints unter `/api/buchhaltung/kasse/*` (siehe `KasseShortcutController`).

| Shortcut | Endpoint | Wirkung |
|----------|----------|---------|
| Bank-Abhebung | `POST /bank-abhebung` | Bargeld vom Bankkonto in die Kasse (zählt **nicht** als Privateinlage) |
| Privateinlage | `POST /privateinlage` | Inhaber legt Bar aus dem Privaten in die Firma |
| Privatentnahme | `POST /privatentnahme` | Inhaber entnimmt Bar fürs Private |
| Lohn-Zahlung | `POST /lohn-zahlung` | Lohn aus der Kasse zahlen; bei Unterdeckung wird automatisch eine Privateinlage vorgeschaltet |
| Saldo abfragen | `GET /saldo` | Aktueller Bar-Bestand zum jetzigen Zeitpunkt |
| Einstellungen | `GET/PUT /einstellung` | Ehegattengehalt-Konfiguration (Höhe, Stichtag, aktiv/inaktiv) |

Auth: Session-Cookie (PC-Frontend) oder `?token=…` (Mobile) – delegiert an `BelegService.findCaller()`.

---

## 3. Ehegattengehalt-Scheduler

Monatliche Auto-Buchung des Ehegattengehalts (siehe `EhegattengehaltSchedulerService`).

| Property | Wert |
|----------|------|
| Cron | `0 30 6 * * *` (täglich 06:30, `Europe/Berlin`) |
| Sachkonto | hart `4120 Löhne & Gehälter` (SKR Handwerker, V307) |
| Kostenstelle | bewusst **keine** – reine Buchhaltung |
| Idempotenz | `KasseEinstellung.letzteBuchungJahrmonat` pro `YYYY-MM` |
| Bar-Sicherung | Bei Saldo-Unterdeckung wird vorher automatisch eine Privateinlage gebucht |

Konfiguriert wird der Scheduler im UI unter **Buchhaltung → Kasse → Einstellungen**:

- Aktiv ja/nein
- Höhe (€)
- Stichtag im Monat (z. B. der 25.)

Ziel: am Monatsende möglichst wenig Bar in der Kasse, ohne dass die Buchhaltung in den negativen Saldo läuft.

---

## 4. Doppik (Variante A): Buchungssatz-Ableitung

Jeder validierte Beleg erhält automatisch einen Buchungssatz `Soll an Haben` (siehe `BuchungssatzAbleitung`). Variante A bedeutet: **Soll/Haben werden aus der Kombination `BelegKategorie + Sachkonto` abgeleitet**, der Anwender wählt nur das Sachkonto (Pflichtfeld).

| Beleg-Kategorie | Soll (Aufwand/Aktiv) | Haben (Ertrag/Passiv) |
|-----------------|----------------------|------------------------|
| `KASSE_EINNAHME` | 1000 Kasse | gewähltes Ertrags-Sachkonto |
| `KASSE_AUSGABE` | gewähltes Aufwands-Sachkonto | 1000 Kasse |
| `PRIVATEINLAGE` | 1000 Kasse | 1890 Privateinlagen |
| `PRIVATENTNAHME` | 1800 Privatentnahmen | 1000 Kasse |
| Bank-Abhebung | 1000 Kasse | 1200 Bank |
| Lohn-Zahlung (Ehegatte) | 4120 Löhne & Gehälter | 1000 Kasse |

Standard-Sachkonten (handwerker-typisch) werden per V307 vorbefüllt; weitere lassen sich über `SachkontoController` pflegen.

### Kostenstellen-Splits

Aufwands-Belege können auf mehrere Kostenstellen aufgeteilt werden (V313, V318). Beispiel: ein Tankbeleg über 120 € wird zu 60 % auf „Baustelle Müller" und zu 40 % auf „Werkstatt" gebucht. Die Anteile müssen in Summe 100 % ergeben – Validierung im Backend.

---

## 5. T-Konto-Monatsexport für Steuerberater

`BelegeKasseExportPdfService` erzeugt pro Monat eine PDF im klassischen T-Konto-Layout: links **Soll** (Belastungen), rechts **Haben** (Gutschriften), je Zeile Datum, Beleg-Nr., Buchungstext, Sachkonto, Betrag. Aufruf über die UI: **Buchhaltung → Kasse → Export Steuerberater**.

| Filter | Wirkung |
|--------|---------|
| Zeitraum (Monat) | Nur Belege im gewählten Monat |
| Kassen-Filter | Nur die vier Bar-Kategorien werden gerendert (kein Bank, keine Eingangsrechnungen) |
| Firmenlogo | Aus `Firma.logo` ins PDF-Header eingebettet (Commit `ac8ab9f`) |

---

## 6. Datenmodell (vereinfacht)

```
Beleg
 ├─ belegKategorie       : BelegKategorie (Enum, V309)
 ├─ status               : BelegStatus (ENTWURF | VALIDIERT | STORNIERT)
 ├─ betragBrutto         : BigDecimal
 ├─ sachkontoId          : FK Sachkonto (Pflicht)
 ├─ kostenstellenAnteile : List<BelegKostenstellenAnteil> (V313, V318)
 └─ uploadedBy           : FK Mitarbeiter (V315 Index)

Sachkonto                  KasseEinstellung
 ├─ nummer (z.B. "4120")    ├─ ehegattengehaltAktiv
 ├─ bezeichnung             ├─ ehegattengehaltBetrag
 └─ typ (AUFWAND/ERTRAG/…)  ├─ stichtagImMonat
                            └─ letzteBuchungJahrmonat  (Idempotenz-Lock)
```

Relevante Migrationen: **V302** (Beleg-Tabelle), **V303** (Sachkonto), **V307** (Standard-Sachkonten Handwerker), **V308** (Zahlungsart-Stammdaten), **V309** (ENUM-Typen für Beleg+Sachkonto), **V310** (Privateinlage-Kategorie), **V312–V313** (Kostenstellen-Splits & KI-Vorschläge), **V318** (Anteile-Detail), **V319–V320** (Kasse-Einstellungen + Ehegattengehalt-Vereinfachung).

---

## 7. Frontend-Shortcuts (PC-Frontend)

Im PC-Frontend (Pfad: `/buchhaltung/kasse`) liegen die häufigen Buchungen als Tastatur-Shortcuts und Schnell-Buttons. Die genaue Tastenbelegung ist in `react-pc-frontend/src/pages/KassePage.tsx` (Suche nach `keydown`) gepflegt – siehe dort, falls sich Shortcuts ändern.

---

## 8. Häufige Fehler & Diagnose

| Symptom | Ursache | Lösung |
|---------|---------|--------|
| „Bar-Saldo wäre negativ" beim Buchen | `KasseUnterdeckungException` aus `KasseSaldoService` | Vorher Privateinlage oder Bank-Abhebung buchen |
| Scheduler bucht 2× | Theoretisch verhindert durch `letzteBuchungJahrmonat`; bei Test-Eingriff DB-Wert prüfen | `SELECT letzte_buchung_jahrmonat FROM kasse_einstellung;` |
| Sachkonto fehlt im Dropdown | Pflicht-`<select>` (Commit `6822932`) – Sachkonto erst anlegen | UI: **Buchhaltung → Sachkonten** |
| Export-PDF leer | Filter steht auf falschem Monat oder es gibt keine validierten Belege | Status der Belege prüfen (`ENTWURF` zählt nicht) |

---

## 9. Weiterführende Docs

- [GOBD_COMPLIANCE.md](GOBD_COMPLIANCE.md) – Unveränderbarkeit & Audit-Trail aller Belege
- [ZAHLUNGSVERKEHR.md](ZAHLUNGSVERKEHR.md) – Bankkonto-Zahlungen & offene Posten
- [DOKUMENTEN_LIFECYCLE.md](DOKUMENTEN_LIFECYCLE.md) – Eingangsbelege & KI-Erkennung
- [API_REFERENZ.md](API_REFERENZ.md) – REST-Endpoints aller Module

📖 *Zurück zum [Doku-Index](README.md)*
