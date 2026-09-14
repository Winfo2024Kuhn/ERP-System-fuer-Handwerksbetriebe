# Kasse & Buchhaltung (Doppik-Variante A)

> **Zielgruppe:** Inhaber, Buchhalter, Steuerberater.
> **Modul:** Buchhaltung → Kasse (Pfad: `/buchhaltung/kasse`).
> **Status:** Live ab Migrationen V302–V320, um Kassenbuchungen und DATEV-Export erweitert in V372.

Das Kasse-Modul ist das doppisch geführte Bar-Kassenbuch des Handwerksbetriebs. Es führt jede Bar-Bewegung als Beleg mit Soll-/Haben-Buchung, validiert den Bar-Saldo gegen Negativ-Bestände und stellt monatlich ein steuerberater-fertiges Export-Paket bereit (Kassenbuch als PDF, DATEV-Buchungsstapel, Belegbilder).

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

## 2. Neue Buchung — sechs Kacheln

Der Button „Neue Buchung" im Kassenbuch-Tab öffnet sechs Kacheln. Jede
Kachel steht für einen Vorgang, den ein Handwerksbetrieb bar erledigt.
Alle sechs laufen über denselben Backend-Pfad
`KassenbuchungService.buche(...)`
(`POST /api/buchhaltung/kassenbuch/buchungen`, `multipart/form-data`, Teil
`daten` mit den Feldern, optionaler Teil `datei` mit dem Belegfoto).

| Kachel (`KassenbuchungDto.Art`) | Kategorie | Quelle | Sachkonto | Beleg |
|---|---|---|---|---|
| `GELD_EINGENOMMEN` | KASSE_EINNAHME | QUITTUNG | Pflicht, Standard „8400 Erlöse 19 %" | Quittung-PDF |
| `GELD_AUSGEGEBEN` (mit Datei) | KASSE_AUSGABE | SCAN | Pflicht | hochgeladene Datei |
| `GELD_AUSGEGEBEN` (kein Beleg) | KASSE_AUSGABE | EIGENBELEG | Pflicht | Ersatzbeleg-PDF, MwSt zwingend 0 % |
| `VON_BANK_GEHOLT` | KASSE_EINNAHME | TRANSFER | „1200 Bank-Kassen-Umbuchung" | Ersatzbeleg-PDF |
| `ZUR_BANK_GEBRACHT` | KASSE_AUSGABE | TRANSFER | „1200 Bank-Kassen-Umbuchung" | Ersatzbeleg-PDF |
| `EIGENES_GELD_EINGELEGT` | PRIVATEINLAGE | TRANSFER | `KasseEinstellung.privateinlageSachkonto`, sonst „1810 Privateinlage" | Ersatzbeleg-PDF |
| `GELD_PRIVAT_ENTNOMMEN` | PRIVATENTNAHME | TRANSFER | „1800 Privatentnahme" | Ersatzbeleg-PDF |

Die Spalte `quelle` (`beleg.quelle`, seit V372) hält fest, wie der Beleg
entstanden ist: `SCAN` = fotografiert oder hochgeladen, `QUITTUNG` = vom
Programm erzeugte Kundenquittung, `EIGENBELEG` = vom Programm erzeugter
Ersatzbeleg, `TRANSFER` = Bank↔Kasse oder privat, ohne Fremdbeleg. Jede
Kachel legt sofort einen validierten Beleg mit Belegdatei an — auch eine
Buchung ohne Fremdbeleg bekommt so ihre eigene Datei und einen
Fingerabdruck (SHA-256).

Der bisherige Endpoint `POST /api/buchhaltung/umbuchungen`
(`BelegController.createUmbuchung`) bleibt erhalten, ist für die vier
Kassenbewegungen (Bank↔Kasse, Privateinlage, Privatentnahme) aber nur noch
ein **Alias**: Er übersetzt seine Anfrage in einen Aufruf von
`KassenbuchungService.buche` und ändert an Statuscodes oder Antwortform
nichts. Bank- und Kreditkartenbuchungen (keine Kassenbewegungen) laufen
weiterhin über den ursprünglichen Pfad in `BelegService`.

Auth: Session-Cookie (PC-Frontend) oder `?token=…` (Mobile) – delegiert an `BelegService.findCaller()`.

### Shortcuts (weiterhin bestehend)

Neben den sechs Kacheln bleiben die bisherigen Schnellbuchungen unter
`/api/buchhaltung/kasse/*` (siehe `KasseShortcutController`) erhalten:

| Shortcut | Endpoint | Wirkung |
|----------|----------|---------|
| Bank-Abhebung | `POST /bank-abhebung` | Bargeld vom Bankkonto in die Kasse (zählt **nicht** als Privateinlage) |
| Privateinlage | `POST /privateinlage` | Inhaber legt Bar aus dem Privaten in die Firma |
| Privatentnahme | `POST /privatentnahme` | Inhaber entnimmt Bar fürs Private |
| Lohn-Zahlung | `POST /lohn-zahlung` | Lohn aus der Kasse zahlen; bei Unterdeckung wird automatisch eine Privateinlage vorgeschaltet |
| Saldo abfragen | `GET /saldo` | Aktueller Bar-Bestand zum jetzigen Zeitpunkt |
| Einstellungen | `GET/PUT /einstellung` | Ehegattengehalt-Konfiguration, DATEV-Stammdaten und Kontonummern (siehe Abschnitt 4 und 6) |

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

### Kontonummern für den DATEV-Export

Für den DATEV-Buchungsstapel reicht eine Bezeichnung wie „1000 Kasse"
nicht — DATEV braucht reine Kontonummern. Dafür gibt es
`BuchungssatzAbleitung.ableitenKonten(beleg, einstellung)`: Sie liefert
ein `Konten`-Record mit `sollKontoNr` und `habenKontoNr`, abgeleitet aus
`BelegKategorie`, `beleg.quelle` und dem gewählten Sachkonto. Die
bestehende Methode `ableiten(...)` (Soll/Haben als Klartext-Labels, für
das Kassenbuch-PDF) bleibt davon unberührt.

Kassen- und Bankkontonummer stehen seit V372 nicht mehr fest im Code,
sondern in `KasseEinstellung.kassenkontoNummer` (Standard „1000") und
`.bankkontoNummer` (Standard „1200") — beide sind unter Einstellungen
änderbar.

Der DATEV-Writer (`DatevExportService`) schreibt für jede Zeile immer das
Soll/Haben-Kennzeichen `S`. Die Richtung steckt nicht im Kennzeichen,
sondern in der Reihenfolge der Konten: Das erste Konto der Zeile
(`ableitenKonten(...).sollKontoNr()`) steht im Soll, das zweite
(`.habenKontoNr()`) im Haben.

### Kostenstellen-Splits

Aufwands-Belege können auf mehrere Kostenstellen aufgeteilt werden (V313, V318). Beispiel: ein Tankbeleg über 120 € wird zu 60 % auf „Baustelle Müller" und zu 40 % auf „Werkstatt" gebucht. Die Anteile müssen in Summe 100 % ergeben – Validierung im Backend.

---

## 5. Was der Steuerberater bekommt

Über **Buchhaltung → Kasse → Für den Steuerberater** lässt sich für einen
Monat ein ZIP-Paket erzeugen (`SteuerberaterExportService.erzeugeZip`,
`GET /api/buchhaltung/steuerberater/paket?jahr=&monat=&trotzdem=`). Es
enthält fünf Bausteine:

| Datei | Inhalt |
|---|---|
| `01_Kassenbuch_<Monat>.pdf` | das Kassenbuch als PDF, wie bisher (`BelegeKasseExportPdfService`), je Zeile Datum, Beleg-Nr., Buchungstext, Sachkonto, Betrag und Bestand danach |
| `02_Buchungen_DATEV_<Monat>.csv` | der DATEV-Buchungsstapel (EXTF 700) aus `DatevExportService` |
| `03_Eingangsrechnungen_<Monat>.csv` | Nr, Datum, Lieferant, Netto, MwSt, Brutto, Konto, Kostenstelle, Zahlungsart, bezahlt am / offen |
| `04_Belege/` | die Originaldateien, benannt nach laufender Nummer, Belegdatum und Lieferant |
| `LIESMICH.txt` | Klartext-Erklärung, was in welcher Datei steht, Kontenrahmen, Kassen-/Bankkonto, Hinweis auf die Verfahrensdokumentation |

**Vorprüfung.** Vor dem Export prüft `SteuerberaterExportService.pruefe`,
ob im Monat Belege ohne Sachkonto oder ohne Zahlungsart stecken. Gibt es
solche, antwortet `GET /vorpruefung` bzw. `GET /paket` mit HTTP 409 und
der Liste der offenen Punkte; das Frontend zeigt sie an, der Nutzer kann
sie beheben oder „trotzdem" exportieren. Bei einem Export trotz offener
Punkte bleibt das Konto in der CSV leer, und der Buchungstext bekommt das
Präfix „PRÜFEN: ".

**Bewusst nicht dabei: Bank- und Kreditkartenbuchungen.** Das Programm
kennt keine Bankumsätze — die holt sich der Steuerberater weiterhin selbst
vom Kontoauszug. Ob und wann eine Eingangsrechnung bezahlt wurde, steht
trotzdem in Datei 03, damit beim Abgleich nichts fehlt.

Der Begriff „T-Konto" ist aus diesem Export verschwunden: Die Oberfläche
zeigt im Kassenbuch-Tab eine fortlaufende Journal-Tabelle (Nr., Datum,
Was, Beleg, Einnahme, Ausgabe, Bestand danach) statt der früheren
T-Konto-Darstellung — das PDF war ohnehin schon ein Journal, nur die
Bezeichnung im Menü hat sich geändert.

---

## 6. Datenmodell (vereinfacht)

```
Beleg
 ├─ belegKategorie         : BelegKategorie (Enum, V309)
 ├─ status                 : BelegStatus (ENTWURF | VALIDIERT | STORNIERT)
 ├─ betragBrutto           : BigDecimal
 ├─ sachkontoId            : FK Sachkonto (Pflicht)
 ├─ kostenstellenAnteile   : List<BelegKostenstellenAnteil> (V313, V318)
 ├─ uploadedBy             : FK Mitarbeiter (V315 Index)
 ├─ quelle                 : BelegQuelle (SCAN | QUITTUNG | EIGENBELEG | TRANSFER, V372)
 ├─ gegenpartei             : String, 120 Zeichen – „Von wem" / „An wen" (V372)
 ├─ ausgangsrechnungId      : FK ProjektGeschaeftsdokument, nullable (V372)
 ├─ kiZahlungsart           : String – Rohwert der KI-Lesung, unverändert (V372)
 ├─ kiBelegdatum            : LocalDate – Rohwert der KI-Lesung (V372)
 ├─ kiBetragBrutto          : BigDecimal – Rohwert der KI-Lesung (V372)
 └─ kiKostenkontoHinweis    : String, 255 Zeichen – Grund, warum kein Konto-Vorschlag kam (V372)

Sachkonto                    KasseEinstellung
 ├─ nummer (z.B. "4120")      ├─ ehegattengehaltAktiv
 ├─ bezeichnung               ├─ ehegattengehaltBetrag
 └─ typ (AUFWAND/ERTRAG/…)    ├─ stichtagImMonat
                              ├─ letzteBuchungJahrmonat     (Idempotenz-Lock)
                              ├─ datevBeraternummer         (V372, max. 7 Zeichen)
                              ├─ datevMandantennummer       (V372, max. 5 Zeichen)
                              ├─ wirtschaftsjahrBeginnMonat (V372, Standard 1 = Januar)
                              ├─ kassenkontoNummer          (V372, Standard "1000")
                              └─ bankkontoNummer            (V372, Standard "1200")
```

Relevante Migrationen: **V302** (Beleg-Tabelle), **V303** (Sachkonto), **V307** (Standard-Sachkonten Handwerker), **V308** (Zahlungsart-Stammdaten), **V309** (ENUM-Typen für Beleg+Sachkonto), **V310** (Privateinlage-Kategorie), **V312–V313** (Kostenstellen-Splits & KI-Vorschläge), **V318** (Anteile-Detail), **V319–V320** (Kasse-Einstellungen + Ehegattengehalt-Vereinfachung), **V372** (Kassenbuchungen und Export: sieben neue `beleg`-Spalten, fünf neue `kasse_einstellung`-Spalten, Backfill der Zahlungsart-Werte auf Klartext).

---

## 7. Zahlungsart-Mapping

Die KI liest am Beleg einen Rohcode (`ki_zahlungsart`), die Stammdaten-
Tabelle `zahlungsart` (V308) kennt dagegen feste Klartext-Bezeichnungen.
`ZahlungsartMapper` (statische Utility-Klasse, kein Spring-Bean) ist die
**einzige** Stelle, die zwischen KI-Code, Stammdaten-Bezeichnung,
Belegkategorie und Bezahlt-Status vermittelt:

| KI-Code | Stammdaten-Bezeichnung | BelegKategorie | gilt als bezahlt |
|---|---|---|---|
| `BAR` | Bar | KASSE_AUSGABE / KASSE_EINNAHME | ja |
| `EC`, `EC_KARTE`, `GIROCARD` | EC-Karte | BANK | ja |
| `UEBERWEISUNG` | Überweisung | BANK | nein |
| `SEPA_LASTSCHRIFT`, `LASTSCHRIFT` | Lastschrift | BANK | nein |
| `KREDITKARTE` | Kreditkarte | KREDITKARTE | ja |
| `PAYPAL` | PayPal | BANK | ja |
| `AMAZON_PAY` | Online-Zahlung | BANK | ja |
| `VORAUSKASSE` | Überweisung | BANK | ja |
| `RECHNUNG` | Rechnung | SONSTIGER_BELEG | nein |
| `SCHECK` | Scheck | BANK | nein |
| `SONSTIGE`, leer, unbekannt | `null` | `null` | nein |

`zuStammdaten` akzeptiert zusätzlich die Klartext-Bezeichnungen selbst
(„Bar", „Überweisung", …) und gibt sie unverändert zurück — der Mapper ist
damit auch für bereits migrierte Werte idempotent. Die Backfill-`CASE`-
Anweisung in V372 spiegelt exakt diese Tabelle; ändert sich das Mapping,
muss `ZahlungsartMapper` **und** eine neue Migration angepasst werden, nie
nur eine Seite.

---

## 8. Frontend-Shortcuts (PC-Frontend)

Im PC-Frontend (Pfad: `/buchhaltung/kasse`) ist die Kasse auf mehrere
Dateien unter `react-pc-frontend/src/components/kasse/` aufgeteilt:

| Datei | Zuständigkeit |
|---|---|
| `KassenbuchJournal.tsx` | die Journal-Tabelle (ersetzt das frühere `KassenbuchView` + `TKontoZeile`) |
| `NeueBuchungDialog.tsx` | die sechs Kacheln aus Abschnitt 2 (ersetzt die vier einzelnen Modals aus `KasseShortcuts.tsx`) |
| `BelegDetailModal.tsx` | der Prüfen-Dialog für einen einzelnen Beleg |
| `VorschlagsChip.tsx` | die KI-/Historie-Vorschlagskarte (früher `KiVorschlagKarte`) |

Gemeinsam genutzte Typen (`Beleg`, `Sachkonto`, `Zahlungsart`,
`BelegKategorie`, …) liegen zentral in `react-pc-frontend/src/types.ts`.
Tastenkürzel sind weiterhin in `react-pc-frontend/src/pages/KassePage.tsx`
(Suche nach `keydown`) gepflegt – siehe dort, falls sich Shortcuts ändern.

---

## 9. Häufige Fehler & Diagnose

| Symptom | Ursache | Lösung |
|---------|---------|--------|
| „Bar-Saldo wäre negativ" beim Buchen | `KasseUnterdeckungException` aus `KasseSaldoService` | Vorher Privateinlage oder Bank-Abhebung buchen |
| Scheduler bucht 2× | Theoretisch verhindert durch `letzteBuchungJahrmonat`; bei Test-Eingriff DB-Wert prüfen | `SELECT letzte_buchung_jahrmonat FROM kasse_einstellung;` |
| Sachkonto fehlt im Dropdown | Pflicht-`<select>` (Commit `6822932`) – Sachkonto erst anlegen | UI: **Buchhaltung → Sachkonten** |
| Export-PDF leer | Filter steht auf falschem Monat oder es gibt keine validierten Belege | Status der Belege prüfen (`ENTWURF` zählt nicht) |

---

## 10. Weiterführende Docs

- [KASSE_ANLEITUNG.md](KASSE_ANLEITUNG.md) – kurze Bedienungsanleitung für den Kassenbuch-Tab (Kacheln, Kassensturz, Monatsabschluss)
- [GOBD_COMPLIANCE.md](GOBD_COMPLIANCE.md) – Unveränderbarkeit & Audit-Trail aller Belege
- [ZAHLUNGSVERKEHR.md](ZAHLUNGSVERKEHR.md) – Bankkonto-Zahlungen & offene Posten
- [DOKUMENTEN_LIFECYCLE.md](DOKUMENTEN_LIFECYCLE.md) – Eingangsbelege & KI-Erkennung
- [API_REFERENZ.md](API_REFERENZ.md) – REST-Endpoints aller Module

📖 *Zurück zum [Doku-Index](README.md)*
