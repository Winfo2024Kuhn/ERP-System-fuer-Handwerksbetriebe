# Kasse & Buchhaltung (Doppik-Variante A)

> **Zielgruppe:** Inhaber, Buchhalter, Steuerberater.
> **Modul:** Finanzen & Controlling › Buchhaltung › **Belege & Kasse**, Reiter **„Kassenbuch“** (Pfad: `/belege-kasse`).
> **Stand:** Oktober 2026. Die Kasse entstand in den Migrationen V302–V320 und wurde in V372 um Kassenbuchungen und DATEV-Export erweitert; alle diese Migrationen stecken heute im Basis-Schema `db/postgresql/basis/V1__basis_schema.sql`.

Das Kasse-Modul ist das doppisch geführte Bar-Kassenbuch des Handwerksbetriebs. Es führt jede Bar-Bewegung als Beleg mit Soll-/Haben-Buchung, verhindert, dass der Bar-Bestand unter den Mindestbestand fällt, schließt Monate lückenlos ab und stellt monatlich ein steuerberater-fertiges Paket bereit (Kassenbuch als PDF, DATEV-Buchungsstapel, Eingangsrechnungen, Belegdateien).

Die Seite „Belege & Kasse“ hat vier Reiter: **„Eingang (Validierung)“** (Handy-Scans prüfen), **„Alle Belege“**, **„Kassenbuch“** und **„Auswertung“**. Im Seitenkopf stehen „Aktualisieren“, im Kassenbuch zusätzlich „Monats-Export (PDF)“, dazu **„Für den Steuerberater“** und **„Beleg hochladen“**. Eine Kurzanleitung für Anwender steht in [KASSE_ANLEITUNG.md](KASSE_ANLEITUNG.md) und im Programm unter „So funktioniert die Kasse“ › „Mehr dazu“.

---

## 1. Bar-Saldo: die vier Kategorien

Der Bar-Saldo besteht ausschließlich aus Belegen mit einer der folgenden Kategorien (siehe `BelegKategorie.java`):

| Kategorie | Richtung | Beispiel |
|-----------|----------|----------|
| `KASSE_EINNAHME` | + Bar | Barzahlung von Kunde, Geld von der Bank geholt |
| `KASSE_AUSGABE` | − Bar | Material bar bezahlt, Geld zur Bank gebracht |
| `PRIVATEINLAGE` | + Bar | Inhaber legt Geld aus dem Privaten in die Firma |
| `PRIVATENTNAHME` | − Bar | Inhaber entnimmt Bargeld für privat |

Die übrigen Kategorien (`UNZUGEORDNET`, `BANK`, `KREDITKARTE`, `SONSTIGER_BELEG`) zählen nicht ins Bar-Kassenbuch.

Nur Belege im Status `VALIDIERT` zählen mit (Status: `NEU` = frisch gescannt oder hochgeladen, `VALIDIERT` = geprüft, `VERWORFEN` = ausgeblendet, z. B. doppelt gescannt). Die zentrale Berechnung liegt in `KasseSaldoService.berechneAktuellenSaldo()` bzw. `berechneSaldoBis(stichtag)` – andere Services dürfen die Saldo-Mathematik **nicht** selbst nachbauen, sondern müssen den Service aufrufen.

### Saldo-Validierung

Vor jeder Buchung, die den Saldo senkt, rechnet `KasseSaldoService.projiziereSaldo(...)` den neuen Bestand vorab aus. Liegt er unter dem **Mindestbestand** (`KasseEinstellung.mindestbestand`, Standard 0 €, einstellbar unter „Mindestbestand der Kasse“), wirft `assertSaldoMindestensMindestbestand` eine `KasseUnterdeckungException` – das Frontend zeigt eine Meldung, der Beleg wird nicht gespeichert. Belege im Status `NEU` werden nicht geprüft, weil sie noch nicht zählen.

Buchungen in einem bereits abgeschlossenen Monat lehnt `KassenbuchSchreibschutz` ab (siehe Abschnitt 5).

---

## 2. Neue Buchung — sechs Kacheln

Der Button **„Neue Buchung“** im Reiter „Kassenbuch“ öffnet sechs Kacheln. Jede Kachel steht für einen Vorgang, den ein Handwerksbetrieb bar erledigt. Alle sechs laufen über denselben Backend-Pfad `KassenbuchungService.buche(...)` (`POST /api/buchhaltung/kassenbuch/buchungen`, `multipart/form-data`, Teil `daten` mit den Feldern, optionaler Teil `datei` mit dem Belegfoto).

| Kachel im Programm (`KassenbuchungDto.Art`) | Kategorie | Quelle | Sachkonto | Beleg |
|---|---|---|---|---|
| „Geld eingenommen“ – Kunde hat bar bezahlt (`GELD_EINGENOMMEN`) | KASSE_EINNAHME | QUITTUNG | im Programm Pflicht; ohne Angabe „8400 Erlöse 19 %“ | Quittung-PDF |
| „Geld ausgegeben“ – bar bezahlt (`GELD_AUSGEGEBEN`, mit Datei) | KASSE_AUSGABE | SCAN | Pflicht („Wofür?“) | hochgeladene Datei (JPG, PNG, WEBP, HEIC, PDF, max. 25 MB) |
| „Geld ausgegeben“ mit „Kein Beleg vorhanden“ | KASSE_AUSGABE | EIGENBELEG | Pflicht | Ersatzbeleg-PDF, MwSt zwingend 0 %, Grund Pflicht |
| „Geld von der Bank geholt“ (`VON_BANK_GEHOLT`) | KASSE_EINNAHME | TRANSFER | „1200 Bank-Kassen-Umbuchung“ | Ersatzbeleg-PDF |
| „Geld zur Bank gebracht“ (`ZUR_BANK_GEBRACHT`) | KASSE_AUSGABE | TRANSFER | „1200 Bank-Kassen-Umbuchung“ | Ersatzbeleg-PDF |
| „Eigenes Geld eingelegt“ (`EIGENES_GELD_EINGELEGT`) | PRIVATEINLAGE | TRANSFER | `KasseEinstellung.privateinlageSachkonto`, sonst „1810 Privateinlage“ | Ersatzbeleg-PDF |
| „Geld privat entnommen“ (`GELD_PRIVAT_ENTNOMMEN`) | PRIVATENTNAHME | TRANSFER | „1800 Privatentnahme“ | Ersatzbeleg-PDF |

Weitere Regeln aus `KassenbuchungService`:

- Betrag positiv, höchstens zwei Nachkommastellen; MwSt nur 0, 7 oder 19 %. Bank-, Privat- und Ersatzbeleg-Buchungen haben immer 0 % MwSt.
- „Von wem?“ / „An wen?“ (`gegenpartei`) höchstens 120 Zeichen.
- Nur bei „Geld eingenommen“ lässt sich eine Kundenrechnung wählen („Zu welcher Rechnung?“); sie wird dann automatisch als bezahlt markiert.
- „Baustelle / Bereich (optional)“ setzt eine Kostenstelle (100 %); bei Bank- und Privatbewegungen ist das nicht erlaubt.
- Fehlt ein Standardkonto (z. B. 1200), antwortet das Backend „Konto … fehlt – bitte in den Stammdaten anlegen“.

Die Spalte `quelle` (`beleg.quelle`) hält fest, wie der Beleg entstanden ist: `SCAN` = fotografiert oder hochgeladen, `QUITTUNG` = vom Programm erzeugte Kundenquittung, `EIGENBELEG` = vom Programm erzeugter Ersatzbeleg, `TRANSFER` = Bank↔Kasse oder privat, ohne Fremdbeleg. Jede Kachel legt sofort einen validierten Beleg mit Belegdatei an — auch eine Buchung ohne Fremdbeleg bekommt so ihre eigene Datei und einen Fingerabdruck (SHA-256, `dateiHash`).

Der Endpoint `POST /api/buchhaltung/umbuchungen` (`BelegController.createUmbuchung`) bleibt erhalten, ist für die vier Kassenbewegungen (Bank↔Kasse, Privateinlage, Privatentnahme) aber nur noch ein **Alias**: Er übersetzt seine Anfrage in einen Aufruf von `KassenbuchungService.buche` und ändert an Statuscodes oder Antwortform nichts. Bank- und Kreditkartenbuchungen (keine Kassenbewegungen) laufen weiterhin über den ursprünglichen Pfad in `BelegService`.

Auth: Session-Cookie (PC-Frontend) oder `?token=…` (Mobile) – delegiert an `BelegService.findCaller()`.

### Shortcuts (weiterhin bestehend)

Neben den sechs Kacheln bleiben die Schnellbuchungen unter `/api/buchhaltung/kasse/*` (siehe `KasseShortcutController`) erhalten:

| Shortcut | Endpoint | Wirkung |
|----------|----------|---------|
| Bank-Abhebung | `POST /bank-abhebung` | Bargeld vom Bankkonto in die Kasse (zählt **nicht** als Privateinlage) |
| Privateinlage | `POST /privateinlage` | Inhaber legt Bar aus dem Privaten in die Firma; nutzt auch der Buchungsdialog, wenn vor einer Ausgabe Geld fehlt („Privateinlage vor Buchung“) |
| Privatentnahme | `POST /privatentnahme` | Inhaber entnimmt Bar fürs Private |
| Lohn-Zahlung | `POST /lohn-zahlung` | Lohn aus der Kasse zahlen; bei Unterdeckung wird automatisch eine Privateinlage vorgeschaltet |
| Saldo abfragen | `GET /saldo` | Aktueller Bar-Bestand und Mindestbestand |
| Einstellungen | `GET/PUT /einstellung` | Mindestbestand, Ehegattengehalt, DATEV-Stammdaten und Kontonummern (siehe Abschnitt 3, 4 und 7) |

---

## 3. Ehegattengehalt-Scheduler

Monatliche Auto-Buchung des Ehegattengehalts (siehe `EhegattengehaltSchedulerService`).

| Property | Wert |
|----------|------|
| Cron | `0 30 6 * * *` (täglich 06:30, `Europe/Berlin`); gebucht wird, sobald der eingestellte Tag erreicht ist |
| Sachkonto | hart `4120 Löhne & Gehälter` |
| Kostenstelle | bewusst **keine** – reine Buchhaltung |
| Idempotenz | `KasseEinstellung.letzteBuchungJahrmonat` pro `YYYY-MM` |
| Bar-Sicherung | Bei Saldo-Unterdeckung wird vorher automatisch eine Privateinlage gebucht |

Konfiguriert wird der Scheduler im Reiter „Kassenbuch“ über das **Zahnrad** neben „Neue Buchung“ (Tooltip „Mindestbestand & Automatik einstellen“) → Fenster **„Kassen-Einstellungen“** → Abschnitt **„Ehegattengehalt-Automatik“**:

- „Jeden Monat automatisch buchen“ (an/aus)
- „Monatlicher Betrag“ (€)
- „Tag des Monats (1–28)“
- „Empfänger (Name)“
- Knopf „Jetzt einmalig auszahlen“ (bucht sofort über `POST /lohn-zahlung`)

Ziel: am Monatsende möglichst wenig Bar in der Kasse, ohne dass die Buchhaltung unter den Mindestbestand läuft.

---

## 4. Doppik (Variante A): Buchungssatz-Ableitung

Jeder validierte Beleg erhält automatisch einen Buchungssatz `Soll an Haben` (siehe `BuchungssatzAbleitung`). Variante A bedeutet: **Soll/Haben werden aus der Kombination `BelegKategorie + Sachkonto` abgeleitet**, der Anwender wählt nur das Sachkonto.

| Beleg-Kategorie | Soll (Aufwand/Aktiv) | Haben (Ertrag/Passiv) |
|-----------------|----------------------|------------------------|
| `KASSE_EINNAHME` | Kasse (1000) | gewähltes Ertrags-Sachkonto; bei „Geld von der Bank geholt“ Bank (1200) |
| `KASSE_AUSGABE` | gewähltes Aufwands-Sachkonto; bei „Geld zur Bank gebracht“ Bank (1200) | Kasse (1000) |
| `PRIVATEINLAGE` | Kasse (1000) | gewähltes Sachkonto, Standard 1810 Privateinlage |
| `PRIVATENTNAHME` | gewähltes Sachkonto, Standard 1800 Privatentnahme | Kasse (1000) |
| Lohn-Zahlung (Ehegatte) | 4120 Löhne & Gehälter | Kasse (1000) |

Fehlt bei einer Einnahme das Sachkonto, steht im Kassenbuch-PDF „Bank“, bei einer Ausgabe „?“ – so sieht man sofort, wo nachgepflegt werden muss.

Die Standard-Sachkonten (SKR03, handwerker-typisch, u. a. 8400, 8300, 1800, 1810, 1200, 4120) bringt das Basis-Schema mit. Eine eigene Seite zum Pflegen der Sachkonten gibt es nicht; neue Konten lassen sich nur über die API anlegen (`POST /api/buchhaltung/sachkonten`, `PUT /api/buchhaltung/sachkonten/{id}`, nur für Mitarbeiter mit Scan-Recht für Belege).

### Kontonummern für den DATEV-Export

Für den DATEV-Buchungsstapel reicht eine Bezeichnung wie „1000 Kasse“ nicht — DATEV braucht reine Kontonummern. Dafür gibt es `BuchungssatzAbleitung.ableitenKonten(beleg, einstellung)`: Sie liefert ein `Konten`-Record mit `sollKontoNr` und `habenKontoNr`, abgeleitet aus `BelegKategorie`, `beleg.quelle` und dem gewählten Sachkonto. Fehlt bei einer Privatbuchung das Sachkonto, nimmt der Export 1810 (Einlage) bzw. 1800 (Entnahme). Die Methode `ableiten(...)` (Soll/Haben als Klartext-Labels, für das Kassenbuch-PDF) bleibt davon unberührt.

Kassen- und Bankkontonummer stehen nicht fest im Code, sondern in `KasseEinstellung.kassenkontoNummer` (Standard „1000“) und `.bankkontoNummer` (Standard „1200“) — beide sind in den Kassen-Einstellungen unter „Für den Steuerberater“ („Kassenkonto“, „Bankkonto“) änderbar.

Der DATEV-Writer (`KasseDatevExportService`, unabhängig vom Lohn-Export `DatevExportService`) schreibt für jede Zeile immer das Soll/Haben-Kennzeichen `S`. Die Richtung steckt nicht im Kennzeichen, sondern in der Reihenfolge der Konten: Das erste Konto der Zeile (`ableitenKonten(...).sollKontoNr()`) steht im Soll, das zweite (`.habenKontoNr()`) im Haben.

### Kostenstellen-Splits

Aufwands-Belege können auf mehrere Kostenstellen aufgeteilt werden (`BelegKostenstellenAnteil`). Beispiel: ein Tankbeleg über 120 € wird zu 60 % auf „Baustelle Müller“ und zu 40 % auf „Werkstatt“ gebucht. Je Anteil gilt entweder ein Prozentsatz oder ein fester Betrag. Das Backend (`BelegService`) prüft: Die Prozent-Anteile dürfen zusammen 100 % nicht überschreiten, die festen Beträge nicht den zu buchenden Nettobetrag. Kassen-Umbuchungen bekommen keine Kostenstelle.

Mischbelege („Mischbeleg – nur ein Teil ist betrieblich“, z. B. privater Einkauf und Büro-Kaffee auf einem Bon) lassen sich positionsweise aufteilen (`BelegAufteilungsModus.TEILWEISE`). Das Kassenbuch rechnet trotzdem mit dem vollen Betrag, weil das ganze Bargeld abgeflossen ist.

---

## 5. Kasse zählen, Monatsabschluss und Storno

### Kasse zählen (`KassenzaehlungService`)

Über **„Kasse zählen“** (Leiste über dem Kassenbuch) gibt man das gezählte Geld nach Scheinen und Münzen (200 € bis 1 ct) ein. Das Programm vergleicht mit dem Bestand laut Kassenbuch zum Stichtag (`GET /api/buchhaltung/kassenbuch/zaehlungen/erwartet`). Gibt es einen Unterschied, ist eine Begründung Pflicht. Mit dem Haken „Unterschied gleich ausbuchen, damit Kasse und Kassenbuch wieder übereinstimmen.“ entsteht eine zusätzliche Buchung (Einnahme bei Überschuss, Ausgabe bei Fehlbetrag). Jede Zählung wird gespeichert (`Kassenzaehlung`, `POST /zaehlungen`).

### Monatsabschluss (`KassenbuchAbschlussService`)

Der Knopf **„<Monat> abschließen“** zeigt vorher eine Vorschau (Kassenstand am Monatsanfang und -ende, „Buchungen, die fest werden“). Der Abschluss
1. gibt jeder geprüften Buchung des Monats eine dauerhafte, lückenlose Nummer (`laufendeNummer`),
2. schreibt diese Buchungen fest (`festgeschrieben`): Datum, Betrag, MwSt, Kategorie, Zahlungsart und Verwendungszweck sind danach gesperrt; Sachkonto, Kostenstelle, Lieferant und Notiz bleiben änderbar, jede Änderung wird protokolliert,
3. hält Anfangs- und Endbestand fest (`KassenbuchMonatsabschluss`).

Ein abgeschlossener Monat wird nie wieder geöffnet. Der Abschluss ist blockiert, solange
- der Monat noch nicht vorbei ist (erst ab dem Ersten des Folgemonats),
- ein früherer Monat noch offen ist (Monate müssen lückenlos aufeinander folgen),
- im Zeitraum ungeprüfte Belege liegen,
- Belege kein Datum haben.

Vor dem Abschluss zeigt das Kassenbuch eine vorläufige Position in Klammern, danach die feste Nummer.

### Storno

Falsch gebuchte, festgeschriebene Belege werden **storniert** (`POST /api/buchhaltung/kassenbuch/belege/{id}/storno`, Fenster „Buchung stornieren“ mit Grund): Eine Gegenbuchung im laufenden Monat hebt den Betrag auf. Das Original bleibt sichtbar und trägt im Kassenbuch die Markierung „Storniert“, die neue Zeile „Gegenbuchung“ (`stornoFuerBelegId`, `storniertDurchBelegId`, `storniertAm`, `stornoGrund`). So verlangt es § 146 Abs. 4 AO. Noch nicht geprüfte Belege lassen sich stattdessen „verwerfen“ (Status `VERWORFEN`, z. B. „Doppelt gescannt“).

### Protokoll und Verfahrensdokumentation

Jede Änderung an einem Beleg landet in einer verketteten Protokollkette (`BelegAudit`). Per API abrufbar, ohne eigenen Knopf in der Oberfläche:

| Endpoint | Inhalt |
|---|---|
| `GET /api/buchhaltung/kassenbuch/belege/{id}/verlauf` | Wer hat wann was an einem Beleg geändert |
| `GET /api/buchhaltung/kassenbuch/pruefung` | Rechnet die Protokollkette nach – Nachweis, dass nichts nachträglich verändert wurde |
| `GET /api/buchhaltung/kassenbuch/verfahrensdokumentation` | Verfahrensdokumentation (GoBD) zum Herunterladen, aus dem Code erzeugt |
| `GET /api/buchhaltung/kassenbuch/abschluss/vorschau`, `GET /abschluesse`, `POST /abschluss` | Monatsabschluss |

---

## 6. Was der Steuerberater bekommt

Über den Knopf **„Für den Steuerberater“** im Seitenkopf von „Belege & Kasse“ lässt sich für einen Monat ein ZIP-Paket erzeugen (`SteuerberaterExportService.erzeugeZip`, `GET /api/buchhaltung/steuerberater/paket?jahr=&monat=&trotzdem=`, Dateiname `JJJJ-MM_Kasse_<Firma>.zip`). Im Fenster wählt man Monat und Jahr; danach „Paket erstellen“ (Download) oder „Per E-Mail an den Steuerberater“. Das Paket enthält fünf Bausteine:

| Datei | Inhalt |
|---|---|
| `01_Kassenbuch_<JJJJ-MM>.pdf` | das Kassenbuch als PDF (`BelegeKasseExportPdfService`), je Zeile Datum, Beleg-Nr., Buchungstext, Sachkonto, Betrag und Bestand danach |
| `02_Buchungen_DATEV_<JJJJ-MM>.csv` | der DATEV-Buchungsstapel (EXTF 700) aus `KasseDatevExportService` |
| `03_Eingangsrechnungen_<JJJJ-MM>.csv` | Nr, Datum, Lieferant, Netto, MwSt, Brutto, Konto, Kostenstelle, Zahlungsart, bezahlt am / offen |
| `04_Belege/` | die Originaldateien, benannt nach laufender Nummer, Belegdatum und Lieferant |
| `LIESMICH.txt` | Klartext-Erklärung, was in welcher Datei steht, Kontenrahmen, Kassen-/Bankkonto, Hinweis auf die Verfahrensdokumentation |

**Vorprüfung.** Vor dem Export prüft `SteuerberaterExportService.pruefe`, ob im Monat Belege ohne Sachkonto oder ohne Zahlungsart stecken (Bank- und Privatbewegungen brauchen keine Zahlungsart). `GET /api/buchhaltung/steuerberater/vorpruefung` liefert die Liste der offenen Punkte und ob Berater- oder Mandantennummer fehlen; das Fenster zeigt sie mit Link zum Beleg („… Belege sind noch nicht fertig geprüft.“). `GET /paket` antwortet bei offenen Punkten mit HTTP 409, solange nicht `trotzdem=true` gesetzt ist (Knopf „Trotzdem erstellen“). Bei einem Export trotz offener Punkte bleibt das Konto in der CSV leer, und der Buchungstext bekommt das Präfix „PRÜFEN: “. Fehlende Berater- oder Mandantennummer blockieren den Export nicht.

**Bewusst nicht dabei: Bank- und Kreditkartenbuchungen.** Das Programm kennt keine Bankumsätze — die holt sich der Steuerberater weiterhin selbst vom Kontoauszug. Ob und wann eine Eingangsrechnung bezahlt wurde, steht trotzdem in Datei 03, damit beim Abgleich nichts fehlt.

Daneben gibt es im Reiter „Kassenbuch“ den Knopf **„Monats-Export (PDF)“** (Fenster „Kassenbuch-Export (Steuerberater)“), der nur das Kassenbuch-PDF eines Monats erzeugt.

Die Oberfläche zeigt im Reiter „Kassenbuch“ eine fortlaufende Journal-Tabelle (Nr., Datum, Was, Beleg, Einnahme, Ausgabe, Bestand danach) mit Anfangsbestand, Summen und „Bestand am Ende“ statt der früheren T-Konto-Darstellung. Zeitraum-Schnellwahl: „Dieser Monat“, „Letzter Monat“, „Dieses Jahr“. Die Suche blendet Zeilen aus; Summen und Bestände gelten weiter für den ganzen Zeitraum.

---

## 7. Datenmodell (vereinfacht)

```
Beleg
 ├─ belegKategorie         : BelegKategorie (Enum)
 ├─ status                 : BelegStatus (NEU | VALIDIERT | VERWORFEN)
 ├─ betragBrutto / betragNetto / mwstSatz
 ├─ zahlungsart            : String (Klartext, z. B. "Bar")
 ├─ sachkonto              : FK Sachkonto
 ├─ kostenstelle           : FK Kostenstelle (optional)
 ├─ kostenstellenAnteile   : BelegKostenstellenAnteil (Splits)
 ├─ aufteilungsModus       : VOLLSTAENDIG | TEILWEISE (Mischbeleg)
 ├─ uploadedBy / validiertVon : FK Mitarbeiter
 ├─ quelle                 : BelegQuelle (SCAN | QUITTUNG | EIGENBELEG | TRANSFER)
 ├─ gegenpartei            : String, 120 Zeichen – „Von wem" / „An wen"
 ├─ ausgangsrechnungId     : FK ProjektGeschaeftsdokument, nullable
 ├─ kiZahlungsart / kiBelegdatum / kiBetragBrutto : Rohwerte der KI-Lesung
 ├─ kiKostenkontoHinweis   : String, 255 Zeichen – Grund, warum kein Konto-Vorschlag kam
 ├─ laufendeNummer         : feste Nummer ab Monatsabschluss
 ├─ festgeschrieben / festgeschriebenAm / monatsabschlussId
 ├─ stornoFuerBelegId / storniertDurchBelegId / storniertAm / stornoGrund
 └─ dateiHash              : SHA-256 der Belegdatei

Sachkonto                    KasseEinstellung
 ├─ nummer (z.B. "4120")      ├─ mindestbestand              (Standard 0)
 ├─ bezeichnung               ├─ ehegattengehaltAktiv
 └─ kontoTyp                  ├─ ehegattengehaltBetrag
    (AUFWAND/ERTRAG/          ├─ ehegattengehaltTag          (Tag des Monats 1–28)
     PRIVAT/NEUTRAL)          ├─ ehegattengehaltEmpfaengerName
                              ├─ privateinlageSachkonto      (FK Sachkonto)
                              ├─ letzteBuchungJahrmonat      (Idempotenz-Lock)
                              ├─ datevBeraternummer          (max. 7 Ziffern)
                              ├─ datevMandantennummer        (max. 5 Ziffern)
                              ├─ wirtschaftsjahrBeginnMonat  (Standard 1 = Januar)
                              ├─ kassenkontoNummer           (Standard "1000")
                              └─ bankkontoNummer             (Standard "1200")

Kassenzaehlung: stichtag, gezaehlterBestand, rechnerischerBestand, differenz,
                stueckelungJson, bemerkung, ausgleichBelegId
KassenbuchMonatsabschluss: jahr, monat, anfangsbestand, endbestand, Summen,
                erste/letzte laufende Nummer, Hash der Protokollkette
```

Historische Migrationen (heute im Basis-Schema): **V302** (Beleg-Tabelle), **V303** (Sachkonto), **V307** (Standard-Sachkonten Handwerker), **V308** (Zahlungsart-Stammdaten), **V309** (Typen für Beleg+Sachkonto), **V310** (Privateinlage-Kategorie), **V312–V313** (Kostenstellen-Splits & KI-Vorschläge), **V318** (Anteile-Detail), **V319–V320** (Kasse-Einstellungen + Ehegattengehalt-Vereinfachung), **V372** (Kassenbuchungen und Export: neue `beleg`- und `kasse_einstellung`-Spalten, Backfill der Zahlungsart-Werte auf Klartext). Neue Änderungen kommen als eigene Migration nach `db/postgresql/migration/` (ab V407).

---

## 8. Zahlungsart-Mapping

Die KI liest am Beleg einen Rohcode (`ki_zahlungsart`), die Stammdaten-Tabelle `zahlungsart` kennt dagegen feste Klartext-Bezeichnungen. `ZahlungsartMapper` (statische Utility-Klasse, kein Spring-Bean) ist die **einzige** Stelle, die zwischen KI-Code, Stammdaten-Bezeichnung, Belegkategorie und Bezahlt-Status vermittelt:

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

`zuStammdaten` akzeptiert zusätzlich die Klartext-Bezeichnungen selbst („Bar“, „Überweisung“, …) und gibt sie unverändert zurück — der Mapper ist damit auch für bereits migrierte Werte idempotent. Die Backfill-`CASE`-Anweisung aus V372 spiegelt exakt diese Tabelle; ändert sich das Mapping, muss `ZahlungsartMapper` **und** eine neue Migration angepasst werden, nie nur eine Seite.

---

## 9. Frontend (PC)

Die Seite ist `react-pc-frontend/src/pages/BelegeKasseEditor.tsx`; die Kasse ist auf mehrere Dateien unter `react-pc-frontend/src/components/kasse/` aufgeteilt:

| Datei | Zuständigkeit |
|---|---|
| `KassenbuchTab.tsx` | der Reiter „Kassenbuch“ (Erklärkasten, Knöpfe, Journal) |
| `KasseErklaerkasten.tsx`, `KasseAnleitungDialog.tsx` | „So funktioniert die Kasse“ und „Mehr dazu“ (Text identisch mit `docs/KASSE_ANLEITUNG.md`) |
| `KasseShortcuts.tsx` | Knopf „Neue Buchung“ und Zahnrad für die Einstellungen |
| `NeueBuchungDialog.tsx` | die sechs Kacheln aus Abschnitt 2 |
| `KasseEinstellungenDialog.tsx` | „Kassen-Einstellungen“: Mindestbestand, Ehegattengehalt-Automatik, „Für den Steuerberater“ (DATEV-Nummern, Wirtschaftsjahr, Kassen-/Bankkonto) |
| `KassenbuchJournal.tsx` | die Journal-Tabelle |
| `KassenbuchAbschlussLeiste.tsx` | „Kasse zählen“ und Monatsabschluss |
| `StornoDialog.tsx`, `VerwerfenDialog.tsx` | „Buchung stornieren“, „Beleg verwerfen“ |
| `SteuerberaterPaketDialog.tsx` | „Für den Steuerberater“ – ZIP-Paket |
| `BelegDetailModal.tsx` | der Prüfen-Dialog für einen einzelnen Beleg |
| `VorschlagsChip.tsx` | die KI-/Historie-Vorschlagskarte |

Gemeinsam genutzte Typen (`Beleg`, `Sachkonto`, `Zahlungsart`, `BelegKategorie`, …) liegen zentral in `react-pc-frontend/src/types.ts`. Eigene Tastenkürzel hat die Kasse nicht.

---

## 10. Häufige Fehler & Diagnose

| Symptom | Ursache | Lösung |
|---------|---------|--------|
| Meldung, dass der Kassenbestand unter den Mindestbestand fallen würde | `KasseUnterdeckungException` aus `KasseSaldoService` | Vorher „Geld von der Bank geholt“ oder „Eigenes Geld eingelegt“ buchen – mit dem echten Betrag |
| Buchung wird abgelehnt, Monat ist abgeschlossen | `KassenbuchSchreibschutz` | Im laufenden Monat buchen; falsche Buchungen stornieren |
| „<Monat> abschließen“ geht nicht | Monat noch nicht vorbei, Vormonat offen, ungeprüfte Belege oder Belege ohne Datum | Hinweise in der Vorschau abarbeiten |
| Scheduler bucht 2× | Theoretisch verhindert durch `letzteBuchungJahrmonat`; bei Test-Eingriff DB-Wert prüfen | `SELECT letzte_buchung_jahrmonat FROM kasse_einstellung;` |
| „Konto … fehlt – bitte in den Stammdaten anlegen“ | Standardkonto (z. B. 1200, 1800, 1810, 8400) gelöscht oder nie angelegt | Konto über `POST /api/buchhaltung/sachkonten` anlegen (keine eigene Seite im Programm) |
| Export-PDF leer | Falscher Monat oder keine validierten Belege | Status der Belege prüfen (`NEU` zählt nicht) |

---

## 11. Weiterführende Docs

- [KASSE_ANLEITUNG.md](KASSE_ANLEITUNG.md) – kurze Bedienungsanleitung für den Kassenbuch-Reiter (Kacheln, Kassensturz, Monatsabschluss)
- [GOBD_COMPLIANCE.md](GOBD_COMPLIANCE.md) – Unveränderbarkeit & Audit-Trail aller Belege
- [ZAHLUNGSVERKEHR.md](ZAHLUNGSVERKEHR.md) – Bankkonto-Zahlungen & offene Posten
- [DOKUMENTEN_LIFECYCLE.md](DOKUMENTEN_LIFECYCLE.md) – Eingangsbelege & KI-Erkennung
- [API_REFERENZ.md](API_REFERENZ.md) – REST-Endpoints aller Module

📖 *Zurück zum [Doku-Index](README.md)*
