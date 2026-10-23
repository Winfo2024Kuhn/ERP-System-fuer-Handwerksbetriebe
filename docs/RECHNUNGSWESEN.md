# Rechnungswesen – Kompletter Rechnungsprozess

## Übersicht

Dieses Dokument beschreibt den vollständigen Rechnungsprozess im Kalkulationsprogramm – von der Erstellung über die Nummerierung und PDF-Generierung bis zum Versand, zur Buchung und zu den Offenen Posten.

**Kurz für die Praxis:**
- Rechnungen schreiben Sie im **Projekt** → Reiter **„Geschäftsdokumente“** → Angebot bzw. Auftragsbestätigung anklicken → **„Rechnung erstellen“** (oder „Dokument erstellen“ → „Rechnung“, wenn es noch kein anderes Dokument gibt).
- Eine Rechnung wird beim **Drucken („Drucken & Buchen“)**, beim **PDF-Export** oder beim **E-Mail-Versand** automatisch **gebucht und gesperrt**. Danach kann sie nur noch storniert werden.
- Alle versendeten Rechnungen finden Sie unter Finanzen & Controlling → Buchhaltung → **„Rechnungen“**, unbezahlte unter **„Offene Posten“**.

---

## 1. Dokumenttypen (`AusgangsGeschaeftsDokumentTyp`)

Alle ausgehenden Geschäftsdokumente werden in der Entity `AusgangsGeschaeftsDokument` verwaltet. Der Typ wird über das Enum `AusgangsGeschaeftsDokumentTyp` gesteuert:

| Typ | In der Oberfläche | Kürzel | Wird gebucht? | Gesperrt durch |
|---|---|---|---|---|
| `ANGEBOT` | Angebot | `AG` | Nein, nie | Digitale Annahme durch den Kunden |
| `NACHTRAGSANGEBOT` | Nachtragsangebot | `NA` | Nein, nie | Digitale Annahme durch den Kunden |
| `AUFTRAGSBESTAETIGUNG` | Auftragsbestätigung | `AB` | Nein, nie | Digitale Annahme (automatisch erzeugte AB) |
| `RECHNUNG` | Rechnung („Einfache Rechnung“) | `RE` | Ja | Buchung (GoBD) |
| `TEILRECHNUNG` | Teilrechnung | `TR` | Ja | Buchung (GoBD) |
| `ABSCHLAGSRECHNUNG` | Abschlagsrechnung (#1, #2, …) | `AR` | Ja | Buchung (GoBD) |
| `SCHLUSSRECHNUNG` | Schlussrechnung | `SR` | Ja | Buchung (GoBD) |
| `GUTSCHRIFT` | Gutschrift | `GU` | Ja | Buchung (GoBD) |
| `STORNO` | Stornorechnung | `ST` | Sofort bei Erstellung | Immer gesperrt |
| `ZAHLUNGSERINNERUNG`, `ERSTE_MAHNUNG`, `ZWEITE_MAHNUNG` | Zahlungserinnerung, 1. Mahnung, 2. Mahnung | – | – | **Nur virtuell**: Mahnungen werden als `ProjektGeschaeftsdokument` gespeichert und nur zur Anzeige unter der Rechnung eingeblendet |

Stornierte Dokumente sind immer gesperrt.

**Gutschriften** entstehen nur als Folgedokument einer gebuchten, nicht stornierten Rechnung (`RECHNUNG`, `TEILRECHNUNG`, `ABSCHLAGSRECHNUNG`, `SCHLUSSRECHNUNG`). Der Dialog „Dokument erstellen“ bietet sie bewusst nicht an; der Weg führt über das Aktionsmenü der Rechnung → „Gutschrift erstellen“ (Projekt-Dokumentliste und Dokumentbaum). Der Dialog lädt `GET /api/ausgangs-dokumente/{rechnungId}/gutschrift-rahmen` (Rechnungsbetrag, bereits gutgeschrieben, höchstens noch möglich) und legt per `POST /api/ausgangs-dokumente` mit `typ: GUTSCHRIFT` und `vorgaengerId` an:

- **Ganze Rechnung:** ohne `positionenJson` – der Server übernimmt Positionen und Text der Rechnung, im Editor anpassbar.
- **Teilbetrag:** das Frontend schickt `positionenJson` mit genau einer Leistungsposition „Gutschrift zu Rechnung RE-…: Grund“ (Menge 1, Einheit `psch`, Einzelpreis = Netto-Betrag, positiv).

Positionen werden positiv erfasst; der Server speichert `betragNetto`/`betragBrutto` der Gutschrift negativ (wie beim Storno), Listen zeigen sie deshalb mit Minus. Mehr als der noch offene Rechnungsbetrag lässt sich nicht gutschreiben (Prüfung beim Anlegen, Speichern und Buchen). Die gebuchte Gutschrift ist wie eine Rechnung gesperrt und lässt sich stornieren. Sie mindert den Offenen Posten der Rechnung (`gutgeschrieben`, `offenerBetrag`); Mahnungen fordern nur den Rest, eine Gutschrift über den vollen Betrag erledigt den Posten. Die E-Rechnung wird als Gutschrift (Typ 381) mit Bezug auf die Rechnung erzeugt. Der Auftragswert des Projekts bleibt unverändert.

---

## 2. Automatische Nummernvergabe

### 2.1 Format

```
{KÜRZEL}-JJJJ/MM/NNNNN
```

Beispiele: `AG-2026/10/00001`, `AB-2026/10/00002`, `RE-2026/10/00042`, `ST-2026/10/00043`, `GU-2026/10/00044` (Gutschrift)

Die Nummer wird beim ersten Speichern vergeben. Das Kürzel zeigt den Typ (siehe Tabelle oben).

### 2.2 Counter-Logik

Die Nummernvergabe erfolgt über die Entity `AusgangsGeschaeftsDokumentCounter` (Tabelle `ausgangs_geschaeftsdokument_counter`):

| Feld | Beschreibung |
|---|---|
| `monatKey` | Schlüssel im Format `JJJJ/MM`, z. B. `2026/10` |
| `zaehler` | Fortlaufender Zähler pro Monat, startet bei 0 |

**Wichtig:** Es gibt **einen gemeinsamen Zähler pro Monat für alle Dokumenttypen**. Die laufende Nummer ist deshalb über Angebote, Auftragsbestätigungen und Rechnungen hinweg fortlaufend – Rechnungsnummern allein sind innerhalb eines Monats nicht lückenlos aufeinanderfolgend.

**Ablauf der Nummernvergabe in `generiereNummer()`:**
1. Aktuellen Monat als Key bestimmen (`YearMonth.now()`)
2. Counter-Datensatz mit **pessimistischem Lock** laden (`findByMonatKeyForUpdate`)
3. Falls kein Counter existiert: neuen Counter mit Zähler 0 anlegen
4. Zähler um 1 erhöhen
5. Nummer formatieren: `String.format("%s-%s/%05d", praefix, monatKey, zaehler)`

**Thread-Sicherheit:** Durch pessimistisches Locking auf DB-Ebene sind parallele Anfragen serialisiert – keine doppelten Nummern möglich.

---

## 3. Dokumentenkette

### 3.1 Standard-Ablauf

```
Angebot (AG)                       Nachtragsangebot (NA)   ← nur im Projekt, beliebig viele
   └→ Auftragsbestätigung (AB)        └→ Auftragsbestätigung (AB)
       ├→ Abschlagsrechnung #1 (AR)       └→ Rechnung(en) …
       ├→ Abschlagsrechnung #2 (AR)
       ├→ Teilrechnung (TR)
       └→ Schlussrechnung (SR)  (verrechnet alle vorherigen Rechnungen)
```

Jedes Angebot und jedes Nachtragsangebot bildet einen eigenen Vorgang mit eigenem Abrechnungsverlauf.

### 3.2 Regeln

- **Erstes Dokument (Basisdokument):** höchstens ein Angebot pro Projekt/Anfrage. Weitere Basisdokumente nur als Nachtragsangebot. Eine eigenständige Auftragsbestätigung oder Rechnung ohne Angebot ist nur möglich, solange es noch gar kein Basisdokument gibt.
- **Eine AB pro Angebot:** Zu einem Angebot/Nachtragsangebot gibt es höchstens eine nicht stornierte Auftragsbestätigung.
- **Rechnungen am untersten Dokument:** Hat ein Angebot eine Auftragsbestätigung, wird an der AB abgerechnet („Rechnung bitte an der Auftragsbestätigung anlegen“). Ausnahme: Wurde schon vorher am Angebot abgerechnet, bleibt die Abrechnung dort.
- **Restbetrag:** Eine neue Rechnung darf den verbleibenden Restbetrag des Basisdokuments nicht übersteigen (Toleranz 1 Cent).

### 3.3 Verknüpfungen

Jedes Dokument kann folgende Beziehungen haben:

| Beziehung | FK-Feld | Beschreibung |
|---|---|---|
| Vorgänger | `vorgaenger_id` | Verweis auf das vorhergehende Dokument |
| Nachfolger | `@OneToMany(mappedBy = "vorgaenger")` | Alle aus diesem Dokument erstellten Dokumente |
| Projekt | `projekt_id` | Zugehöriges Projekt |
| Anfrage | `anfrage_id` | Zugehörige Anfrage (solange noch kein Projekt existiert) |
| Kunde | `kunde_id` | Kunde für Rechnungsadresse |
| Ersteller | `erstellt_von_id` | Benutzer, der das Dokument erstellt hat |

Kunde, Anfrage und Projekt werden – falls nicht angegeben – vom Vorgänger bzw. von Anfrage/Projekt übernommen.

### 3.4 Abschlagsnummer-Logik

Die `abschlagsNummer` wird automatisch hochgezählt. Beim Erstellen einer neuen Abschlagsrechnung wird über `countByVorgaengerIdAndTyp()` die Anzahl bestehender Abschlagsrechnungen am selben Vorgänger ermittelt und die nächste Nummer vergeben. In der Oberfläche erscheint sie als „Abschlagsrechnung #2“.

---

## 4. Rechnung erstellen und Umwandlung zwischen Dokumenttypen

### 4.1 Erlaubte Umwandlungen

```
ANGEBOT / NACHTRAGSANGEBOT  →  AUFTRAGSBESTAETIGUNG   (nur im Projekt oder automatisch bei digitaler Annahme)
ANGEBOT / NACHTRAGSANGEBOT / AUFTRAGSBESTAETIGUNG
                            →  RECHNUNG, TEILRECHNUNG, ABSCHLAGSRECHNUNG, SCHLUSSRECHNUNG   (nur im Projekt)
RECHNUNG / TEILRECHNUNG / ABSCHLAGSRECHNUNG / SCHLUSSRECHNUNG
                            →  STORNO   (über „Stornieren“, nur gebuchte Rechnungen)
```

### 4.2 Dialog „Rechnung erstellen“

Projekt → Geschäftsdokumente → Basisdokument anklicken → **„Rechnung erstellen“**. Der Dialog zeigt Basisdokument, Netto-Auftragssumme, bisherige Abrechnungen und den verfügbaren Restbetrag. Zur Wahl stehen:

| Rechnungstyp | Beschreibung im Dialog | Voraussetzung |
|---|---|---|
| **Teilrechnung** | Einzelne Leistungspositionen nach Fertigstellung abrechnen | Mindestens 2 Leistungspositionen; bereits abgerechnete Positionen sind nicht vorausgewählt |
| **Abschlagsrechnung** | Pauschaler Abschlag auf den Gesamtbetrag – Eingabe „Netto absolut“, „Brutto absolut“ oder „Prozentual“ | Betrag ≤ Restbetrag |
| **Schlussrechnung** | Verbleibenden Restbetrag abrechnen | Erst nach einer Teil- oder Abschlagsrechnung |
| **Einfache Rechnung** | Gesamtbetrag in einer Rechnung abrechnen | Nur solange es keine (nicht stornierte) Rechnung zu diesem Vorgang gibt |

### 4.3 Content-Vererbung bei Umwandlung

Bei der Umwandlung werden folgende Felder vom Vorgänger übernommen (falls nicht explizit mitgeschickt):
- `htmlInhalt` – HTML-Inhalt aus dem Dokument-Editor
- `positionenJson` – Leistungspositionen (bei Typwechsel ohne die Standard-Vor-/Nachtexte; der Editor lädt beim ersten Öffnen die Standardtexte des neuen Typs)
- `projekt`, `anfrage`, `kunde` – Verknüpfungen
- `rechnungsadresseOverride` – zuletzt bearbeitete Rechnungsanschrift des Vorgangs

Das Zahlungsziel kommt – falls nicht gesetzt – aus den Kundenstammdaten (`Kunde.zahlungsziel`).

### 4.4 Übernahme Anfrage → Projekt

Wird eine Anfrage in ein Projekt überführt (`migrateFromAnfrageToProjekt()`):
1. Alle Dokumente der Anfrage werden dem Projekt zugeordnet
2. Die Anfrage-Referenz wird entfernt (`anfrage = null`), die Anfrage wird danach gelöscht
3. Projektkategorien werden aus den Dokumenten-Positionen automatisch abgeleitet

Details: `ANFRAGEWESEN.md`.

---

## 5. MwSt-Berechnung

### 5.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `betragNetto` | `BigDecimal(12,2)` | Nettobetrag |
| `betragBrutto` | `BigDecimal(12,2)` | Bruttobetrag |
| `mwstSatz` | `BigDecimal(5,4)` | MwSt-Satz als Dezimalzahl (z. B. `0.19` für 19 %) |

### 5.2 Berechnung

```
Brutto = Netto × (1 + mwstSatz)   (kaufmännisch auf 2 Stellen gerundet)
MwSt-Betrag = Netto × mwstSatz
```

Die Methode `getMwstBetrag()` auf der Entity berechnet:
```java
betragNetto.multiply(mwstSatz)
```

Bei Stornorechnungen werden Netto und Brutto immer als Minderung (negativ) gespeichert.

### 5.3 Standardwert

Falls kein `mwstSatz` angegeben wird, gilt der Default: **19 %** (`0.19`) – beim Erstellen im Service und zusätzlich per `@PrePersist`.

---

## 6. Rechnungsadresse

### 6.1 Standard

Die Rechnungsadresse kommt standardmäßig aus den **Kundenstammdaten** (Entity `Kunde`, FK `kunde_id`): Name, Ansprechpartner, Straße, PLZ, Ort.

### 6.2 Override pro Dokument

Über das Feld `rechnungsadresseOverride` (max. 500 Zeichen) kann pro Dokument eine **abweichende Rechnungsadresse** hinterlegt werden. Dieses Override ändert **nicht** die Kundenstammdaten.

**Logik:**
- `rechnungsadresseOverride` gesetzt → Override-Adresse verwenden
- `rechnungsadresseOverride` leer → Adresse aus Kunde laden
- **Vererbung:** Ein neues Dokument im selben Projekt (bzw. derselben Anfrage) erbt die zuletzt bearbeitete Anschrift. Danach hat jedes Dokument seine eigene, unabhängig änderbare Kopie.

---

## 7. Positionen-JSON

### 7.1 Struktur

Die Positionen werden als JSON im Feld `positionenJson` (Text-Spalte) gespeichert – entweder als Liste von Blöcken oder als Objekt `{ blocks: [...], globalRabatt: … }`. Block-Typen:

| Block-Typ | Im Editor | Beschreibung |
|---|---|---|
| `TEXT` | Textbaustein | Freitext-Block |
| `SERVICE` | Leistung / Stundensätze / Material | Leistungsposition mit Menge, Einheit, Preis, optional Rabatt |
| `CLOSURE` | – | Zusammenfassung (Netto, MwSt, Brutto, Abrechnungsstand) |
| `SEPARATOR` | Trennlinie | Horizontale Trennlinie |
| `SECTION_HEADER` | Bauabschnitt | Abschnitts-Überschrift, kann Leistungen enthalten |
| `SUBTOTAL` | – | Zwischensumme |

Leistungen können als **optional** oder **alternativ** markiert sein (Knopf „Kunde wählt“); solche Positionen zählen nicht zur Nettosumme, bis sie beauftragt sind (siehe `ANGEBOTSWESEN.md`).

### 7.2 Netto-Berechnung aus Positionen

Falls `betragNetto` nicht gespeichert ist, wird der Nettobetrag aus dem `positionenJson` berechnet (`berechneNettoAusPositionenJson()`): Summe aller nicht-optionalen Leistungen (auch innerhalb von Bauabschnitten) inklusive Positionsrabatt, danach abzüglich Pauschalrabatt.

---

## 8. Abrechnungsverlauf

### 8.1 Zweck

Der Abrechnungsverlauf zeigt für ein Basisdokument (Angebot, Nachtragsangebot oder AB) alle direkt daraus erstellten Rechnungen und den verbleibenden **Restbetrag**.

### 8.2 Berechnung

```
Basisbetrag (Netto des Basisdokuments)
  − Abschlags-/Teilrechnungen (Netto, nicht storniert)
  − weitere Rechnungen (Netto, nicht storniert)
  = Restbetrag
```

Stornierte Dokumente werden aus der Summe **ausgenommen**. Bei Teilrechnungen merkt sich der Verlauf, welche Positionen schon abgerechnet sind (`bereitsAbgerechneteBlockIds`).

### 8.3 Schlussrechnung

- Die Schlussrechnung rechnet **ihre eigenen Positionen** ab und zieht die bereits gestellten Rechnungen ab. Entfallene Leistungen (aus der Schlussrechnung gelöscht) und zusätzlich angefallene Leistungen werden dadurch korrekt berücksichtigt; der Editor weist die Differenz zum Angebot aus.
- Solange die Schlussrechnung ein Entwurf ist, wird ihr Betrag bei jeder Anzeige neu abgeleitet (z. B. wenn danach noch eine Teilrechnung storniert wird).
- Beim Buchen (auch beim Buchen durch den E-Mail-Versand) wird genau dieser abgeleitete Betrag festgeschrieben. Weicht er vom gespeicherten ab, weil nach dem letzten Speichern eine Rechnung storniert oder gestellt wurde, wird er vorher übernommen und mit altem und neuem Betrag im Prüfprotokoll vermerkt.
- Ist sie gebucht oder versendet, gilt der gespeicherte Betrag unverändert (GoBD) – auch bei alten Schlussrechnungen ohne Positionen. Anzeige, Offene Posten und Beleg zeigen dann denselben Betrag, selbst wenn später eine andere Rechnung storniert wird.
- Wird eine Abschlagsrechnung storniert, werden zugehörige, nicht stornierte **gebuchte** Schlussrechnungen am selben Vorgänger **automatisch mitstorniert**. Ein Schlussrechnungs-Entwurf bleibt stehen; sein Betrag wird bei jeder Anzeige ohne den stornierten Abschlag abgeleitet. Deshalb zeigt das Projekt nach dem Stornieren einen Hinweis: „mitstorniert“ (Info) bzw. „Entwurf prüfen“ (Warnung).

Auf Rechnungen kann ein **Abrechnungsstand** (Auftragssumme, bereits gestellte Rechnungen, offener Rest, wahlweise mit Fortschrittsbalken) gedruckt werden.

### 8.4 Endpoint

```
GET /api/ausgangs-dokumente/{id}/abrechnungsverlauf
```

Gibt ein `AbrechnungsverlaufDto` zurück mit:
- Basisdokument-Daten (ID, Nummer, Typ, Datum, Netto-Betrag, Positionen)
- Liste der Rechnungen (Nummer, Typ, Datum, Netto-Betrag, Abschlagsnummer, storniert)
- `bereitsAbgerechnet`, `restbetrag`, `bereitsAbgerechneteBlockIds`

---

## 9. Buchung und Offene Posten

### 9.1 Buchungsvorgang

Gebucht wird automatisch aus dem Dokument-Editor:

| Aktion im Editor | Wirkung bei Rechnungstypen |
|---|---|
| **Drucken** → „Drucken & Buchen“ | Buchen + sperren, Datum = heute |
| **PDF** (Standard oder ZUGFeRD) | Buchen + sperren, Datum = heute |
| **E-Mail** | Buchen + sperren vor dem Versand, danach Versanddatum setzen |
| **Entwurf** | Kein Buchen – PDF mit Wasserzeichen „ENTWURF“ |

Technisch (`buchen()` bzw. `buchenNachEmailVersand()`):

1. `gebucht = true`, `gebuchtAm = heute` setzen (nur Rechnungstypen, Gutschrift, Storno – Angebote/ABs werden nie gebucht)
2. Für Rechnung, Teil-, Abschlags- und Schlussrechnung: Automatischer Eintrag in `ProjektGeschaeftsdokument` (Offene Posten) – nur wenn das Dokument einem Projekt zugeordnet ist
3. Bei E-Mail-Versand zusätzlich: `versandDatum = heute` (auch bei Angeboten/ABs)
4. Audit-Einträge `GEBUCHT` bzw. `VERSENDET` in der Hash-Kette (siehe `GOBD_COMPLIANCE.md`)

Das gedruckte bzw. exportierte PDF wird zusätzlich auf dem Server archiviert (`pdf-speichern`) und am Offenen Posten hinterlegt.

### 9.2 Offene-Posten-Eintrag

Für jede gebuchte Rechnung (mit Projekt) wird automatisch ein `ProjektGeschaeftsdokument`-Eintrag erstellt:

| Feld | Quelle |
|---|---|
| `dokumentid` | Dokumentnummer des AusgangsGeschaeftsDokuments |
| `geschaeftsdokumentart` | „Rechnung“, „Teilrechnung“, „Abschlagsrechnung“ oder „Schlussrechnung“ |
| `rechnungsdatum` | Datum des Dokuments |
| `bruttoBetrag` | Bruttobetrag des Dokuments |
| `bezahlt` | Initial `false` |
| `faelligkeitsdatum` | `datum + zahlungszielTage` (sonst Zahlungsziel des Kunden) |
| `emailVersandDatum` | Versanddatum, sobald per E-Mail versendet |
| `systemGeneriert` | `true` – nur solche Einträge werden automatisch gemahnt |

### 9.3 Mahnwesen

Unbezahlte Rechnungen können automatisch gemahnt werden (Zahlungserinnerung → 1. Mahnung → 2. Mahnung, `Mahnstufe`-Enum). Mahnungen werden als `ProjektGeschaeftsdokument` mit `referenzDokument` → Original-Rechnung gespeichert. Details: `ZAHLUNGSVERKEHR.md`.

---

## 10. PDF-Generierung (`RechnungPdfService`)

### 10.1 Architektur

Die PDF-Generierung verwendet **OpenPDF** (Fork von iText 2, Paket `org.openpdf`) mit dem **ColumnText-„Schütten“-Prinzip** (`generatePdfBytes(RechnungDto)`):
1. Inhalt wird in einen ColumnText-Container geschrieben
2. Bei Seitenüberlauf wird automatisch eine neue Seite angelegt
3. Folgeseiten erhalten einen eigenen Kopf (`renderFolgeSeitenKopf`)

Aufgerufen wird der Service über `POST /api/dokument-generator/pdf` bzw. `/preview`. Das Layout (Briefkopf, Adressfeld, Logo …) kommt aus der im **Formularwesen** zugewiesenen Vorlage.

### 10.2 PDF-Aufbau

```
┌─────────────────────────────┐
│  Hintergrundbild (optional) │
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
│  Netto, Rabatt, MwSt, Brutto│
├─────────────────────────────┤
│  Abrechnungsstand           │
│  (bei Folgerechnungen)      │
└─────────────────────────────┘
```

Schrift: Open Sans / Montserrat; Linien, Tabellenkopf und Summen in der Textfarbe des Dokuments. Beim Entwurfsversand trägt das PDF das Wasserzeichen „ENTWURF“; beim endgültigen Drucken/Buchen wird ein Wasserzeichen aus der Vorlage entfernt.

### 10.3 Content-Block-Typen im PDF

| Block-Typ | Rendering |
|---|---|
| `SERVICE` | Leistungszeile mit Menge × Preis = Gesamt (ggf. Rabatt) |
| `TEXT` | HTML-formatierter Freitext (fett, kursiv, Listen, Bilder, Farben) |
| `CLOSURE` | Netto/Rabatt/MwSt/Brutto-Aufschlüsselung, ggf. Abrechnungsstand |
| `SEPARATOR` | Horizontale Linie |
| `SECTION_HEADER` | Abschnitts-Überschrift (Bauabschnitt) |
| `SUBTOTAL` | Zwischensummen-Zeile |

### 10.4 HTML-Parsing

Der Service parst Rich-Text-HTML in PDF-Elemente und unterstützt:
- **Formatierung:** Fett, kursiv, unterstrichen
- **Farben:** Text- und Hintergrundfarben
- **Schriftgrößen:** Verschiedene Font-Sizes
- **Listen:** Aufzählungen und nummerierte Listen
- **Bilder:** Eingebettete Bilder
- **Formular-Blöcke:** Layout-Blöcke aus dem Formularwesen

### 10.5 ZUGFeRD-Integration

Beim Export oder Versand im Format **„ZUGFeRD PDF“** wird das PDF über `POST /api/dokument-generator/zugferd-pdf` mit maschinenlesbaren Rechnungsdaten (EN 16931) angereichert (siehe `ZUGFERD_E_INVOICING.md`). Vorher prüft das Programm, ob die Firmendaten vollständig sind – sonst wird **nicht** gebucht.

---

## 11. Rechnungsübersicht

### 11.1 Frontend-Ansicht

Menü: Finanzen & Controlling → Buchhaltung → **„Rechnungen“** (`/rechnungsuebersicht`, Seite „RECHNUNGSÜBERSICHT – Alle Eingangs- und Ausgangsrechnungen nach Monat“).

Als **Ausgangsrechnungen** erscheinen nur Rechnungen, Teil-, Abschlags- und Schlussrechnungen, Gutschriften und Stornorechnungen, die **gebucht, versendet oder storniert** sind – Entwürfe nicht. **Eingangsrechnungen** zeigen die Lieferantenrechnungen, die der angemeldete Benutzer laut Abteilungsrechten sehen darf.

Über „Rechnung/Gutschrift manuell importieren“ können Eingangsrechnungen (PDF, XML-E-Rechnung, JPG, PNG; max. 25 MB) hochgeladen und per KI ausgelesen werden.

### 11.2 API-Endpoints

| Endpoint | Beschreibung |
|---|---|
| `GET /api/rechnungsuebersicht/ausgang` | Ausgangsrechnungen (Parameter `year`, `month`, `search`) |
| `GET /api/rechnungsuebersicht/eingang` | Eingangsrechnungen (Parameter `year`, `month`, `search`) |
| `POST /api/rechnungsuebersicht/merge-pdf` | Sammel-PDF aus ausgewählten Rechnungen erstellen. Nimmt nur echte PDFs auf; Eingangsrechnungen ohne PDF-Datei (Foto, XML, Datei fehlt) bleiben draußen, ihre IDs stehen im Antwort-Header `X-Nicht-Enthalten` |
| `POST /api/rechnungsuebersicht/analyze-upload` | Eingangsrechnung per KI analysieren (derzeit ohne Oberfläche – Rechnungen kommen über „Belege & Kasse“) |
| `POST /api/rechnungsuebersicht/import-upload` | Analysierte Eingangsrechnung importieren (derzeit ohne Oberfläche) |

### 11.3 Filter

- **Jahr:** Filterung nach Rechnungsjahr
- **Monat:** „Alle Monate“ oder Januar–Dezember
- **Suche:** Ausgang: Dokumentnummer, Dokumentart, Datum, Betrag, Auftragsnummer, Kunde

### 11.4 Sammel-PDF-Export

Ausgewählte Rechnungen (Eingang und/oder Ausgang) können als zusammengefügtes PDF exportiert werden. Der Download heißt `Rechnungen_JJJJ.pdf` bzw. `Rechnungen_JJJJ_MM.pdf` (bei gewähltem Monat).

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
| `GET` | `/projekt/{projektId}` | Alle Dokumente eines Projekts (inkl. Mahnungen als virtuelle Einträge) |
| `GET` | `/anfrage/{anfrageId}` | Alle Dokumente einer Anfrage |
| `GET` | `/projekt/{projektId}/geerbte-rechnungsadresse` | Rechnungsanschrift, die ein neues Dokument erben würde |
| `GET` | `/anfrage/{anfrageId}/geerbte-rechnungsadresse` | dito für Anfragen |
| `POST` | `/` | Neues Dokument erstellen (vergibt Nummer und Bearbeitungssperre) |
| `PUT` | `/{id}` | Dokument aktualisieren (nur wenn bearbeitbar und Sperre gehalten, sonst 409) |
| `POST` | `/{id}/buchen` | Dokument buchen (sperrt Rechnungstypen) |
| `POST` | `/{id}/email-versendet` | Nach E-Mail-Versand buchen + Versanddatum |
| `POST` | `/{id}/pdf-speichern` | Erzeugtes PDF archivieren und am Offenen Posten hinterlegen |
| `POST` | `/{id}/storno?grund=…` | Gebuchte Rechnung stornieren (erstellt Stornorechnung; nicht für Stornorechnungen und Entwürfe) |
| `DELETE` | `/{id}?begruendung=…` | Entwurf löschen (nicht wenn gebucht, versandt, digital angenommen oder storniert) |
| `GET` | `/{id}/abrechnungsverlauf` | Abrechnungsverlauf abrufen |
| `GET` | `/{id}/historie` | Audit-Verlauf des Dokuments („Verlauf anzeigen“) |
| `GET` | `/historie/nummer/{dokumentNummer}` | Audit-Verlauf über die Nummer (auch für gelöschte Entwürfe) |
| `GET` | `/{id}/mahnung-vorschau?stufe=…` | PDF-Vorschau einer Mahnstufe |
| `GET` | `/freigabe-status?ids=…` | Status der digitalen Freigaben |
| `GET` | `/{id}/freigabe-audit` | Annahme-Nachweis |

### 12.3 Request/Response-DTOs

**Erstellen (`AusgangsGeschaeftsDokumentErstellenDto`):**
- `typ` – Dokumenttyp (Pflicht)
- `datum` – Dokumentdatum (Standard: heute)
- `projektId` / `anfrageId` – Kontext-Zuordnung
- `kundeId` – Kunde für Rechnungsadresse
- `betreff` – Betreff/Titel
- `htmlInhalt` – HTML-Inhalt
- `positionenJson` – Positionen als JSON
- `betragNetto`, `mwstSatz` – Beträge (Brutto wird berechnet)
- `zahlungszielTage` – Zahlungsziel in Tagen
- `vorgaengerId` – Vorgänger-Dokument für die Kette
- `erstelltVonId` – Ersteller
- `rechnungsadresseOverride` – Optionaler Adress-Override

**Response (`AusgangsGeschaeftsDokumentResponseDto`):**
- Alle wesentlichen Felder der Entity + berechnete Felder (`mwstBetrag`, bei Bedarf aus Positionen berechnete Beträge)
- `bearbeitbar` – Flag, ob das Dokument bearbeitet werden darf
- `gebucht`, `storniert`, `digitalAngenommen`, `versandDatum`
- `projektBauvorhaben`, `projektnummer` – Daten des verknüpften Projekts
- `kundennummer`, `kundenName`, `rechnungsadresse` – Kundendaten
- `vorgaengerNummer` – Dokumentnummer des Vorgängers
- `erstelltVonName` – Name des Erstellers
