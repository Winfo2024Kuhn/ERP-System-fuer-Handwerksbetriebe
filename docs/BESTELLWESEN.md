# Bestellwesen – Bedarf, Bestellungen, Bestell-PDF

> **Stand:** Oktober 2026

## Übersicht

Dieses Dokument beschreibt den Einkauf im Programm – vom Material, das ein Projekt braucht, über die Bestellung beim Lieferanten bis zur Lieferantenrechnung, die die Kosten ins Projekt bringt.

Im Menü liegen zwei Seiten unter **Projektmanagement › Einkauf**:

| Menüpunkt | Seite (Überschrift) | Wofür |
|---|---|---|
| **Bedarf** (`/bestellungen/bedarf`) | „Bestellungen“ – „Offene Bestellungen nach Lieferanten verwalten und versenden.“ | Material, das für Projekte noch bestellt werden muss. Von hier gehen Bestell-PDF und Bestell-E-Mail an den Lieferanten. |
| **Bestellungen** (`/bestellungen`) | „BESTELLUNGEN“ – „Angefragt, bestellt, geliefert – und was noch abgerechnet werden muss. Alles an einem Ort.“ | Die Unterlagen der Lieferanten (Angebot, Auftragsbestätigung, Lieferschein, Werkstoffzeugnis, Rechnung) als Kette. Hier werden Rechnungen Projekten oder Kostenstellen zugeordnet (Abschnitt 7). |

### So geht's (Kurzfassung)

1. **Material ins Projekt eintragen:** Projekt öffnen → Knopf **„Artikel aus Lager“** → im Fenster „Artikel aus Lager hinzufügen“ Artikel suchen, Menge eintragen und je Artikel wählen: **„Lager“** (kommt aus dem eigenen Lager) oder **„Bestellen“** (muss noch bestellt werden).
2. **Bestellen:** Alles mit „Bestellen“ steht danach unter **Einkauf › Bedarf**, sortiert nach Lieferant. Je Lieferant gibt es die Knöpfe **„PDF“** (Bestell-PDF ansehen) und **„E-Mail“** (Bestellung direkt an den Lieferanten schicken, das PDF hängt automatisch an).
3. **Abhaken:** Ist die Ware bestellt, das Häkchen in der Spalte **„Bestellt“** setzen. Die Position verschwindet aus dem Bedarf, ihr Preis wird festgeschrieben.
4. **Rechnung zuordnen:** Auftragsbestätigung, Lieferschein und Rechnung des Lieferanten kommen meist per E-Mail und erscheinen unter **Einkauf › Bestellungen**. Liegt die Rechnung vor, steht die Bestellung im Reiter „Rechnung zuordnen“ – mit **„Projekten zuordnen“** landen die Kosten im Projekt.

---

## 1. Bedarf (offene Bestellungen)

### 1.1 Prinzip

Material kommt über das Projekt in den Bedarf: Der Projekt-Dialog „Artikel aus Lager hinzufügen“ legt je gewähltem Artikel eine Position `ArtikelInProjekt` an (`ProjektManagementService.fuegeArtikelMaterialkosten`, `POST /api/projekte/{projektID}/materialkosten/artikel`). Lieferant und Preis stammen vom günstigsten Anbieter mit aktuellem Preisstand – genau dem, der in der Trefferliste danebensteht.

| Auswahl im Dialog | `ausLager` | `bestellt` | Folge |
|---|---|---|---|
| „Lager“ (Vorgabe) | `true` | `true` | Erscheint **nicht** im Bedarf, zählt sofort als Materialkosten des Projekts |
| „Bestellen“ | `false` | `false` | Erscheint im Bedarf, bis jemand „Bestellt“ abhakt |

Die Bedarfsliste zeigt alle Positionen mit `bestellt = false` (`ArtikelInProjektRepository.findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc`).

### 1.2 Entity `ArtikelInProjekt`

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `projekt` | `Projekt` (FK, Pflicht) | Zugehöriges Projekt |
| `artikel` | `Artikel` (FK, Pflicht) | Zugehöriger Artikel |
| `lieferant` | `Lieferanten` (FK) | Lieferant für die Bestellung |
| `lieferantenArtikelPreis` | `LieferantenArtikelPreise` (FK) | Preisstand aus der Preishistorie, mit dem kalkuliert wurde; wird beim Abhaken gelöst |
| `stueckzahl` | `Integer` | Anzahl Stück |
| `meter` | `BigDecimal` | Gesamtlänge in Metern (bei Profilen Stückzahl × Länge je Stück) |
| `kilogramm` | `BigDecimal` | Gesamtgewicht in Kilogramm |
| `preisProStueck` | `BigDecimal` (4 Nachkommastellen) | Zwei Bedeutungen: Preis **je Einheit** (offene Position, Lagerware) oder eingefrorener **Gesamtpreis** (abgehakte Bestellung), siehe `ProjektMapper.ermittleGesamtpreis` |
| `hinzugefuegtAm` | `LocalDate` | Tag, an dem die Position ins Projekt kam |
| `bestellt` | `boolean` | `false` = steht im Bedarf; `true` = bestellt oder aus dem Lager |
| `ausLager` | `boolean` | Ware kam aus dem eigenen Lager |
| `bestelltAm` | `LocalDate` | Datum des Abhakens (bei Lagerware leer) |
| `schnittForm`, `anschnittWinkelLinks`, `anschnittWinkelRechts` | `String` | Zuschnitt (Schnittbild, Winkel) – nur bei Artikeln aus den dafür freigegebenen Hauptkategorien |
| `kommentar` | `String` | Optionaler Kommentar |

---

## 2. Gruppierung

### 2.1 Nach Lieferant

Die Seite **Bedarf** zeigt je Lieferant eine Karte. Im Kopf stehen der Name, die Zahl der Projekte, die Zahl der Positionen und – wenn vorhanden – das Gesamtgewicht in kg. Die Tabelle hat die Spalten „Bestellt“, „Projektnummer“, „Projekt“, „Kunde“, „Artikelnummer“, „Produkt“, „Produkttext“, „Kommentar“, „Werkstoff“, „Kategorie“ und „Menge“. Die Positionen sind innerhalb eines Lieferanten nach Bauvorhaben sortiert; im Bestell-PDF werden sie nach Bauvorhaben gruppiert.

Zwei Sondergruppen:

- **„Werkstoffe“:** Artikel, deren Hauptkategorie die Werkstoff-Kategorie ist (fest Kategorie-ID 1), landen unabhängig vom Lieferanten in einer gemeinsamen Gruppe „Werkstoffe“. Die Menge steht dort in Metern.
- **„Ohne Lieferant“:** Positionen ohne Lieferant.

Für beide Sondergruppen sind die Knöpfe „PDF“ und „E-Mail“ ausgegraut, weil kein einzelner Lieferant dahintersteht.

### 2.2 Beispiel

```
Lieferant: Schrauben Schmidt (1 Projekt · 2 Positionen)
   └── Projekt A: Dachsanierung
        ├── Sechskantschrauben M12 – 200 Stück
        └── Unterlegscheiben M12 – 200 Stück

Werkstoffe (2 Projekte · 3 Positionen · 245,00 kg)
   ├── Projekt A: Dachsanierung
   │    ├── Flachstahl 40×5mm – 12 m
   │    └── Rundrohr 48,3×3,2mm – 50 m
   └── Projekt B: Carport
        └── Winkelstahl 50×50×5mm – 8 m
```

---

## 3. Preisberechnung nach Verrechnungseinheit

### 3.1 Verrechnungseinheiten (`Verrechnungseinheit`-Enum)

| Enum-Wert | Anzeigename | Berechnung beim Abhaken |
|---|---|---|
| `KILOGRAMM` | Kilogramm | `Preis × kilogramm` |
| `LAUFENDE_METER` | Laufende Meter | `Preis × meter` |
| `QUADRATMETER` | Quadratmeter | `Preis × meter` (das Feld `meter` trägt hier die Fläche) |
| `STUECK` | Stück | `Preis × stueckzahl` |

### 3.2 Endpreis-Berechnung

Beim Setzen von „Bestellt“ rechnet `BestellungService.setBestellt` den Gesamtpreis aus:

```
Gesamtpreis = Preis des hinterlegten Preisstands × Menge (in der Verrechnungseinheit des Artikels)
```

Der Gesamtpreis wird in `preisProStueck` gespeichert und damit eingefroren; der Bezug auf den Preisstand (`lieferantenArtikelPreis`) wird gelöst. Spätere Preisänderungen des Lieferanten ändern die abgehakte Position nicht mehr. Fehlt die Menge in der passenden Einheit, wird der Einzelpreis ohne Multiplikation übernommen. Hängt an der Position kein Preisstand, ändert das Abhaken am Preis nichts.

**Beispiele:**

| Artikel | VE | Einzelpreis | Menge | Gesamtpreis |
|---|---|---|---|---|
| Flachstahl 40×5mm | kg | 1,80 €/kg | 120 kg | 216,00 € |
| Rundrohr 48,3mm | lfd. m | 12,50 €/m | 50 m | 625,00 € |
| Trapezblech | m² | 18,00 €/m² | 45 m² | 810,00 € |
| Schrauben M12 | Stück | 0,35 €/Stk | 200 Stk | 70,00 € |

---

## 4. Bestellvorgang

### 4.1 Ablauf

```
Projekt: „Artikel aus Lager“ → Auswahl „Bestellen“
    │
    ▼
Bedarf (bestellt = false)
    │
    ├── „PDF“    → Bestell-PDF für diesen Lieferanten
    ├── „E-Mail“ → Fenster „Bestellung an <Lieferant> senden“
    │
    ▼
Häkchen „Bestellt“ (PATCH /api/bestellungen/{id}?bestellt=true)
    │
    ├── Gesamtpreis berechnen und einfrieren
    ├── bestellt = true, bestelltAm = heute
    │
    ▼
Position verschwindet aus dem Bedarf
```

In der Oberfläche verschwindet die Position nach dem Abhaken sofort aus der Liste. Zurücknehmen geht nur über die API (`bestellt=false`): Die Position steht dann wieder im Bedarf, `bestelltAm` wird geleert und der Preis auf den Preis je Einheit zurückgerechnet (siehe unten).

Beim Abhaken wird der Endpreis in `preisProStueck` **eingefroren** (Einzelpreis × Menge) und der Preisbezug (`lieferantenArtikelPreis`) gelöst. Grundlage ist der hängende Preisstand, sonst der festgehaltene Einzelpreis.

**„Bestellt“ zurücknehmen:** Der eingefrorene Endpreis wird wieder auf den Preis je Einheit zurückgerechnet (Endpreis ÷ Menge, 4 Nachkommastellen). Sonst würde die Projektansicht die Summe ein zweites Mal mit der Menge multiplizieren. Bei Menge 0 bleibt der Wert stehen. Lässt sich die Summe nicht glatt teilen (z. B. 100 € ÷ 3), entsteht beim erneuten Abhaken ein Rundungsrest im Cent-Bruchteil (33,3333 × 3 = 99,9999).

**Sonderfälle:**
- Wiederholtes Abhaken bzw. Zurücknehmen (Doppelklick) ändert nichts mehr.
- Lagerware (`ausLager`) trägt immer den Preis je Einheit und wird nie umgerechnet.
- Der Preis-Import aus Lieferanten-Angeboten (`OfferPriceService`) schreibt bei offenen Positionen ebenfalls den Preis je Einheit, nicht die Summe.

### 4.2 Bestellung per E-Mail

Der Knopf **„E-Mail“** öffnet das Fenster „Bestellung an <Lieferant> senden“:

- **Empfänger:** die beim Lieferanten hinterlegten E-Mail-Adressen; über „Andere E-Mail eingeben...“ auch eine neue Adresse. Nach dem Senden an eine neue Adresse fragt das Programm „E-Mail-Adresse speichern?“ und legt sie auf Wunsch beim Lieferanten ab.
- **CC**, **Von** (Absender-Adresse), **Betreff** (Vorschlag „Bestellanfrage: <Lieferant>“) und **Nachricht** mit Signatur. Der vorbelegte Text bittet um ein Angebot für die angehängten Positionen – vor dem Senden anpassen, wenn direkt bestellt werden soll.
- **„Automatisch angehängt: Bestellungs-PDF“** – das PDF aus Abschnitt 5; weitere Dateien lassen sich zusätzlich anhängen.
- Gesendet wird über den normalen E-Mail-Versand (`POST /api/emails/send`), die Mail ist danach im E-Mail-Center beim Lieferanten zu finden.

Das Senden setzt **nicht** automatisch „Bestellt“ – das Häkchen setzt man selbst, wenn die Bestellung wirklich raus ist.

### 4.3 Service-Methoden

| Methode | Beschreibung |
|---|---|
| `BestellungService.findeOffeneBestellungen()` | Alle Positionen mit `bestellt = false`, sortiert nach Lieferant und Bauvorhaben, als `BestellungResponseDto` |
| `BestellungService.setBestellt(id, bestellt)` | Position als bestellt/unbestellt markieren, beim Abhaken Gesamtpreis einfrieren |
| `BestellungPdfService.generatePdfForLieferant(lieferantId)` | Bestell-PDF für einen Lieferanten |
| `BestellungPdfService.generatePdfForProjekt(projektId)` | Bestell-PDF der Werkstoffe eines Projekts |

---

## 5. Bestell-PDF (`BestellungPdfService`)

### 5.1 Aufbau (je Lieferant)

Das PDF ist A4 quer und enthält:

- **Kopf:** Firmenlogo.
- **Hinweistext:** je Auftrag eine eigene Rechnung, Lieferungen möglichst zusammenfassen, Zuschnitte auf die Lagerlängen optimieren.
- **Je Bauvorhaben eine Tabelle** („Bauvorhaben: <Name>“) mit den Spalten Projektnummer, Kunde, Artikelnummer, Produkt, Produkttext, Form (Schnittbild-Symbol), Winkel L, Winkel R, Kommentar, Werkstoff, Kategorie, Menge.
- **Am Ende:** eine feste Übersicht der Schnittbilder, wenn sie im Programm hinterlegt ist.

Bei Werkstoffen steht die Menge als „<Stück> Stk à <Länge> mm (Gesamt: <Meter> m)“.

Lieferantenadresse, Bestelldatum oder ein Fußbereich mit Lieferbedingungen sind **nicht** Teil des PDFs.

### 5.2 Variante je Projekt

`generatePdfForProjekt` erzeugt ein PDF nur mit den offenen **Werkstoffen** eines Projekts, gruppiert nach Kategorie (Spalten Artikelnummer, Produkt, Produkttext, Form, Winkel L, Winkel R, Werkstoff, Kommentar, Menge). Der Endpunkt besteht, die Oberfläche bietet ihn derzeit nicht an.

---

## 6. Materialkosten im Projekt

### 6.1 Was zählt

In der Projektansicht setzen sich die Materialkosten so zusammen:

```
Materialkosten = manuell erfasste Materialkosten
               + Artikel aus dem Lager (ausLager = true)
               + zugeordnete Lieferantenrechnungen (Projektanteile)
```

Bestellte Positionen zählen **nicht** in die Materialkosten. Sie stehen in der Liste „Artikel aus Lager“ mit blassem Preis und dem Vermerk **„kommt per Rechnung“**: Ihre Kosten kommen später über die Lieferantenrechnung ins Projekt (Abschnitt 7). Beides zu zählen hieße doppelt zählen.

### 6.2 Kilogramm-Berechnung

Das Gewicht wird beim Hinzufügen ins Projekt berechnet: bei Werkstoffen mit hinterlegter Masse je Meter als `Masse × Gesamtlänge`, bei Artikeln mit Verrechnungseinheit Kilogramm aus der eingegebenen Menge. Die Bedarfsseite summiert die kg je Lieferant. Das Feld `gesamtKilogramm` der API-Antwort enthält die Summe aller offenen Positionen (über alle Lieferanten) oder `null`, wenn keine Position ein Gewicht hat.

---

## 7. Bestellübersicht (Einkauf › Bestellungen)

Die Bestellübersicht baut aus den Lieferanten-Dokumenten Ketten wie `Angebot → Auftragsbestätigung → Lieferschein (+ Werkstoffzeugnis) → Rechnung`. Wie Dokumente erkannt und verknüpft werden, steht in [LIEFERANTEN_DOKUMENTEN.md](LIEFERANTEN_DOKUMENTEN.md).

### 7.1 Reiter

| Reiter | Was steht drin |
|---|---|
| **Angebote** | „Angebote von Lieferanten, zu denen noch keine Bestellung kam.“ Ältere Angebote sind eingeklappt. |
| **Bestellt** | „Bestellt oder geliefert – die Rechnung fehlt noch.“ Getrennt nach aktuellen Bestellungen und „Seit über 2 Monaten keine Rechnung“. |
| **Rechnung zuordnen** | „Die Rechnung ist da – ordne die Kosten jetzt einem Projekt oder einer Kostenstelle zu.“ |
| **Erledigt** | „Bestellungen, deren Kosten schon zugeordnet sind.“ |
| **Ausgeblendet** | „Ausgeblendete Einträge. Sie lassen sich jederzeit wieder einblenden.“ |

Oben sucht das Feld „Lieferant, Nummer, Betrag oder Datum suchen …“ über alle Reiter.

### 7.2 Was man auf einer Karte tun kann

- **Rechnungsvorschlag:** Bei laufenden Bestellungen schlägt das Programm die wahrscheinlichste Rechnung vor („Wahrscheinlich: Rechnung …“) – mit „Vorschau“ ansehen, mit „Übernehmen“ anhängen.
- **„Suchen“ / „Rechnung hochladen“:** Am offenen Ende der Kette lässt sich die fehlende Rechnung selbst aussuchen (Fenster „Rechnung suchen“) oder eine Papierrechnung (PDF, JPG, PNG bis 25 MB) hochladen; sie wird beim Lieferanten angelegt und im Hintergrund ausgelesen.
- **„Dokument hinzufügen“:** ein fehlendes Dokument – z. B. ein Werkstoffzeugnis oder einen Lieferschein – nachträglich in die Kette hängen (Fenster „Dokument zur Kette hinzufügen“).
- **„Projekten zuordnen“** (Reiter „Rechnung zuordnen“): die Rechnung prozentual, mit festen Beträgen oder Position für Position auf Projekte und Kostenstellen verteilen. Bei Kostenstellen lassen sich Kosten über mehrere Jahre verteilen („Kosten verteilen über …“).
- **„Ausblenden“ / „Wieder einblenden“.**

Die Markierung einer Rechnung als **Lagerbestellung** (kein Projekt nötig) gibt es nur über die API (`POST /lagerbestellung/{dokId}`), nicht als Knopf in der Oberfläche.

Der Knopf **„Belegkosten zuordnen (n)“** im Seitenkopf zeigt Belege aus „Belege & Kasse“, die nicht aus dem E-Mail-Import stammen, und verteilt ihre Kosten ebenso auf Projekte und Kostenstellen.

Welche Dokumentarten jemand in der Übersicht sieht, richtet sich nach den Rechten seiner Abteilung (Einstellungen › Reiter „Berechtigungen“, Spalte „Sehen“); Administratoren sehen alle.

---

## 8. API-Referenz

### 8.1 Bedarf (`/api/bestellungen`)

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/bestellungen/offen` | Alle offenen (unbestellten) Positionen |
| `PATCH` | `/api/bestellungen/{id}?bestellt=true\|false` | Position als bestellt/unbestellt markieren |
| `GET` | `/api/bestellungen/lieferant/{lieferantId}/pdf` | Bestell-PDF für einen Lieferanten (wird von „PDF“ und „E-Mail“ genutzt) |
| `GET` | `/api/bestellungen/projekt/{projektId}/pdf` | Bestell-PDF der offenen Werkstoffe eines Projekts |

### 8.2 Bestellübersicht (`/api/bestellungen-uebersicht`)

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Dokumentenketten, gruppiert nach Status |
| `POST` | `/rechnung-verknuepfen` | Rechnung an ein Bestelldokument hängen (auch Teillieferungen und Teilrechnungen) |
| `GET` | `/ketten-vorschlaege` | Passende Dokumente für „Dokument zur Kette hinzufügen“ |
| `POST` | `/ketten-verknuepfen` | Dokument an ein Kettenglied hängen |
| `POST` | `/abhaengen` | „Gehört nicht dazu“: Verknüpfungen eines Dokuments lösen |
| `POST` | `/rechnung-hochladen` | Papierrechnung zu einer Bestellung hochladen |
| `POST` | `/ausblenden`, `/einblenden` | Kette aus- bzw. wieder einblenden |
| `GET` / `PUT` | `/geschaeftsdaten/{dokId}` | Geschäftsdaten eines Dokuments lesen / ändern |
| `POST` | `/zuordnen` | Rechnung anteilig Projekten und Kostenstellen zuordnen |
| `POST` | `/lagerbestellung/{dokId}` | Rechnung als Lagerbestellung markieren |
| `DELETE` | `/zuordnung/{dokId}` | Zuordnung einer Rechnung aufheben |
| `GET` | `/zuordnungen/{dokId}`, `/zuordnungen/kostenstelle/{id}` | Bestehende Zuordnungen |
| `GET` | `/kostenstellen`, `/kostenstellen/auswertung` | Aktive Kostenstellen, Jahresauswertung mit Vorjahresvergleich |
| `GET` | `/belege-offen`, `/belegdaten/{belegId}`, `/beleg-zuordnungen/{belegId}` | Belege für „Belegkosten zuordnen“ |
| `POST` | `/beleg-zuordnen` | Beleg-Kosten zuordnen |
| `GET` | `/positionen/{geschaeftsdokumentId}` | Ausgelesene Positionen eines Dokuments |
| `POST` | `/positionen/{id}/auslesen`, `/positionen/{id}/vorschau`, `/positionen/{id}/zuordnen` | Positionen auslesen, Aufteilung berechnen, Positionen zuordnen |

### 8.3 Response-DTO (`BestellungResponseDto`)

| Feld | Beschreibung |
|---|---|
| `id` | ID der Position (`ArtikelInProjekt`) |
| `artikelId` | Artikel-ID |
| `externeArtikelnummer` | Artikelnummer des Lieferanten |
| `produktname` | Artikelname |
| `produkttext` | Artikelbeschreibung |
| `kommentar` | Kommentar der Position |
| `werkstoffName` | Werkstoff-Bezeichnung |
| `kategorieName` | Produktkategorie |
| `rootKategorieId` / `rootKategorieName` | Hauptkategorie (ID 1 = Werkstoffe) |
| `stueckzahl` | Stückzahl (0, wenn leer) |
| `menge` / `einheit` | Angezeigte Menge: bei Werkstoffen mit Länge in „m“, sonst die Stückzahl in „Stück“ |
| `projektId`, `projektName`, `projektNummer`, `kundenName` | Projekt (Bauvorhaben, Auftragsnummer) und Kunde |
| `lieferantId`, `lieferantName` | Lieferant; bei Werkstoffen `null` bzw. „Werkstoffe“ |
| `bestellt`, `bestelltAm` | Bestellstatus |
| `kilogramm` | Gewicht der Position |
| `gesamtKilogramm` | Summe der kg aller offenen Positionen |
| `schnittForm`, `anschnittWinkelLinks`, `anschnittWinkelRechts` | Zuschnitt |

---

## 9. Entitäten-Übersicht

| Entity / Enum | Zweck |
|---|---|
| `ArtikelInProjekt` | Material im Projekt mit Bestell- bzw. Lagerstatus |
| `Verrechnungseinheit` (Enum) | Einheiten: Laufende Meter, Quadratmeter, Kilogramm, Stück |
| `LieferantenArtikelPreise` | Preisstand eines Artikels bei einem Lieferanten (mit Preishistorie) |
| `LieferantDokument` / `LieferantGeschaeftsdokument` | Unterlagen der Lieferanten, Grundlage der Bestellübersicht |

| Service | Verantwortung |
|---|---|
| `BestellungService` | Offene Positionen laden, Bestellstatus setzen, Gesamtpreis einfrieren |
| `BestellungPdfService` | Bestell-PDF je Lieferant bzw. je Projekt |
| `ProjektManagementService` | Material ins Projekt aufnehmen (`fuegeArtikelMaterialkosten`) |
| `BestellungsUebersichtService` | Dokumentenketten bilden und nach Status gruppieren |
| `RechnungsVorschlagService`, `KettenVorschlagService` | Rechnungs- und Dokumentvorschläge für die Ketten |
| `LieferantDokumentZuordnungService` | Kosten einer Rechnung auf Projekte und Kostenstellen verteilen |
