# Lieferanten-Dokumente – Eingangsrechnungen, Erkennung, Genehmigung

> **Stand:** Oktober 2026

## Übersicht

Dieses Dokument beschreibt, wie Unterlagen von Lieferanten ins Programm kommen – vom E-Mail-Anhang oder Upload über das automatische Auslesen (ZUGFeRD, XRechnung, KI) bis zur Genehmigung, Bezahlung und Verteilung der Kosten auf Projekte.

### Wo finde ich was?

| Ort im Programm | Was man dort tut |
|---|---|
| **Vorlagen & Stammdaten › Kontakte › Lieferanten** → Lieferant öffnen → Reiter **„Dokumente“** | Alle Dokumente eines Lieferanten ansehen, suchen („Intelligente Suche“), bearbeiten, Knopf **„Dokument importieren“** |
| **Projektmanagement › Einkauf › Bestellungen** | Dokumente als Kette (Angebot → AB → Lieferschein → Rechnung), Rechnung Projekten zuordnen – siehe [BESTELLWESEN.md](BESTELLWESEN.md) |
| **Finanzen & Controlling › Buchhaltung › Offene Posten** → Reiter **„Eingangsrechnungen“** | Rechnungen genehmigen (Spalte „Genehmigt“) und als bezahlt abhaken |
| **Projektmanagement › Dokumente** → Reiter „Eingang“ und **Finanzen & Controlling › Buchhaltung › Rechnungen** | Übersichten über alle Dokumente bzw. Rechnungen |
| **Vorlagen & Stammdaten › Administration › Einstellungen** → Reiter **„Abteilungen & Rechte“** (nur Admin) | Je Abteilung festlegen, welche Dokumentarten sie sehen und scannen darf und wer Rechnungen genehmigt |

### So geht's (Kurzfassung)

1. **Dokumente kommen meist von selbst:** Schickt ein Lieferant eine E-Mail mit PDF- oder XML-Anhang, legt das Programm daraus ein Dokument beim Lieferanten an und liest es aus. Voraussetzung: Die E-Mail ist dem Lieferanten zugeordnet (siehe [email-system-refactoring.md](email-system-refactoring.md)).
2. **Papier oder Datei von Hand:** Lieferant öffnen → Reiter „Dokumente“ → **„Dokument importieren“** → Datei wählen → Daten prüfen und speichern. E-Rechnungen werden automatisch gelesen; bei anderen Dokumenten liest die KI (wenn eingerichtet) oder man trägt die Angaben selbst ein.
3. **Prüfen:** Steht bei einem Dokument **„Manuelle Prüfung erforderlich“**, fehlen Angaben – Dokument öffnen und ergänzen.
4. **Genehmigen und bezahlen:** Unter Offene Posten › Eingangsrechnungen setzt jemand mit dem Recht „Rechnungen genehmigen“ den Haken bei „Genehmigt“. Ist die Rechnung bezahlt, Haken bei „Bezahlt“ setzen.
5. **Kosten zuordnen:** Unter Einkauf › Bestellungen › „Rechnung zuordnen“ mit „Projekten zuordnen“ auf Projekte oder Kostenstellen verteilen.

---

## 1. Datei-Handling

### 1.1 Kernprinzip: Keine Datei-Duplikation

Lieferanten-Dokumente können aus drei Quellen stammen:

| Quelle | Handling |
|---|---|
| **E-Mail-Anhang** | Das Dokument zeigt auf die bereits gespeicherte Anhangsdatei (`gespeicherterDateiname` = Dateiname des `EmailAttachment`). Die Datei wird **nicht** kopiert. Der Anhang verweist umgekehrt über `EmailAttachment.lieferantDokument` auf das Dokument. |
| **Upload am PC / Handy** | Datei wird unter `uploads/lieferanten/{lieferantId}/{UUID}_{Originalname}` gespeichert. Auf dem Handy lassen sich Lieferscheine über die Lieferanten-Seite der Zeiterfassungs-App hochladen. |
| **Beleg-Scan** | Ein Beleg aus „Belege & Kasse“ (Handy-Scanner) kann über `LieferantDokument.beleg` ein Lieferanten-Dokument bekommen, damit eine Rechnung auch in den Rechnungsübersichten erscheint. |

### 1.2 Effektiver Dateiname

`getEffektiverDateiname()` liefert:
1. den Originalnamen des verknüpften Anhangs (`attachment.getOriginalFilename()`), falls `attachment` gesetzt ist,
2. sonst `originalDateiname`.

Für den Speicherort gibt es analog `getEffektiverGespeicherterDateiname()`. Das Feld `attachment` (`attachment_id`) wird beim heutigen E-Mail-Import nicht mehr gesetzt; es stammt aus älteren Datenbeständen.

### 1.3 Entity `LieferantDokument`

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `version` | `Long` | Optimistisches Sperren gegen gleichzeitiges Speichern |
| `lieferant` | `Lieferanten` (FK) | Zugehöriger Lieferant |
| `attachment` | `EmailAttachment` (FK) | Alt-Verweis auf einen E-Mail-Anhang (siehe 1.2) |
| `typ` | `LieferantDokumentTyp` (Enum) | Art des Dokuments |
| `originalDateiname` | `String` | Originaler Dateiname |
| `gespeicherterDateiname` | `String` | Dateiname im Speicher (bei Uploads mit UUID-Präfix) |
| `uploadDatum` | `LocalDateTime` | Zeitpunkt des Imports |
| `ausgeblendet` | `boolean` | In der Bestellübersicht ausgeblendet |
| `uploadedBy` | `Mitarbeiter` (FK) | Hochladender Mitarbeiter |
| `beleg` | `Beleg` (FK) | Optionaler Beleg aus „Belege & Kasse“ |
| `geschaeftsdaten` | `LieferantGeschaeftsdokument` (1:1) | Ausgelesene Geschäftsdaten |
| `projektAnteile` | `Set<LieferantDokumentProjektAnteil>` (1:N) | Aufteilung auf Projekte/Kostenstellen |
| `verknuepfteDokumente` / `verknuepftVon` | `Set<LieferantDokument>` (M:N) | Verknüpfte Dokumente der Kette |

---

## 2. Lieferant-Dokumenttypen (`LieferantDokumentTyp`)

| Typ | Anzeige im Programm | Beschreibung |
|---|---|---|
| `ANGEBOT` | Angebot | Angebot vom Lieferanten |
| `AUFTRAGSBESTAETIGUNG` | AB / Auftragsbestätigung | Auftragsbestätigung des Lieferanten |
| `LIEFERSCHEIN` | Lieferschein | Lieferschein zur Warenlieferung |
| `WERKSTOFFZEUGNIS` | Werkstoffzeugnis | Abnahmeprüfzeugnis 3.1/3.2 oder Werkszeugnis 2.2 zum Lieferschein, mit Werkstoff und Charge |
| `RECHNUNG` | Rechnung | Eingangsrechnung des Lieferanten |
| `GUTSCHRIFT` | Gutschrift | Gutschrift vom Lieferanten (Beträge werden negativ gespeichert) |
| `SONSTIG` | Sonstiges | Kein Geschäftsdokument (Katalog, Infoblatt …) |
| `BELEG` | Belege (Kasse, Bank) | Kein Dokumenttyp im engeren Sinn: steuert in den Berechtigungen den Zugriff auf „Belege & Kasse“ |

Der alte Wert `EINGANGSRECHNUNG` wird beim Lesen auf `RECHNUNG` abgebildet (`LieferantDokumentTypConverter`).

---

## 3. Auslesen der Daten

### 3.1 Reihenfolge

`GeminiDokumentAnalyseService` liest jedes Dokument in dieser Reihenfolge – beim E-Mail-Import (`analyzeAndReturnData`), beim Import am PC (`analyzeFile`) und bei einer Neuanalyse (`analysiereDokument`):

```
Datei
  │
  ├─[1] PDF mit eingebetteter ZUGFeRD-/Factur-X-XML? ──► ZugferdExtractorService
  │                                                      datenquelle = "ZUGFERD", verifiziert = true
  │
  ├─[2] XML-Datei (XRechnung UBL oder CII)? ───────────► XML-Auswertung
  │                                                      datenquelle = "XML", aiConfidence = 1.0
  │
  └─[3] Sonst: KI (Gemini), sofern eingerichtet ───────► Ergebnis mit aiConfidence (0.0–1.0)
                                                         datenquelle = null bei Erfolg
                                                         bzw. "AI_FAILED" / "AI_PARSE_FAILED" / "AI_ERROR"
```

Ergänzend erkennt das Programm den Dokumenttyp aus dem Nummern-Präfix (z. B. RE, AB, LS, AN, GS), wenn die Auswertung keinen Typ liefert. Kommen PDF und XML derselben Rechnung zusammen in einer E-Mail, werden sie als Paar behandelt: Die Daten kommen aus der XML, angezeigt wird das PDF.

### 3.2 Datenquelle

| `datenquelle` | Bedeutung |
|---|---|
| `ZUGFERD` | Strukturierte Daten aus dem PDF |
| `XML` | XRechnung als XML-Datei |
| `null` | Erfolgreich per KI ausgelesen |
| `AI_FAILED` | Keine KI-Antwort – z. B. weil kein KI-Schlüssel eingerichtet ist |
| `AI_PARSE_FAILED` | KI-Antwort war nicht lesbar |
| `AI_ERROR` | Fehler beim KI-Aufruf |
| `ERROR_NO_RESULT` | Beim E-Mail-Import kam gar kein Ergebnis zurück |

### 3.3 Ohne KI

Die KI ist optional. Ohne eingerichteten KI-Schlüssel werden E-Rechnungen (ZUGFeRD, XRechnung) weiterhin vollständig gelesen; alle anderen Dokumente bekommen `datenquelle = "AI_FAILED"` und den Hinweis „Manuelle Prüfung erforderlich“. Im Import-Fenster steht dann: „E-Rechnungen werden automatisch gelesen, bei allen anderen Dokumenten tragen Sie die Angaben von Hand ein.“ Im Fenster „Projekten zuordnen“ ist der Knopf „Positionen auslesen“ gesperrt.

### 3.4 Was die KI ausliest

Dokumenttyp, Dokumentnummer, Datum, Beträge (netto/brutto), MwSt-Satz, Bestellnummer, Referenznummer, Liefertermin, Zahlungsbedingungen (Nettotage, Skontotage, Skontoprozent), Zahlungsart und – auf Wunsch – die einzelnen Positionen (bei Werkstoffzeugnissen mit Werkstoff, Charge und Abmessung). Das Roh-JSON der KI-Antwort steht in `aiRawJson`.

### 3.5 E-Mail-Import im Detail (`EmailAttachmentProcessingService`)

- Verarbeitet werden nur **eingehende** E-Mails, die einem Lieferanten zugeordnet sind, und nur PDF- und XML-Anhänge.
- Hat ein Anhang denselben Inhalt wie ein schon bekannter Anhang desselben Lieferanten (z. B. AGB, die jeder Mail beiliegen), wird er nicht noch einmal ausgelesen.
- Gibt es beim Lieferanten schon ein Dokument mit derselben Dokumentnummer, wird kein zweites angelegt.
- Nach dem Speichern sucht `BelegZuordnungService` die passende Kette (Abschnitt 8). Ist beim Lieferanten eine Standard-Kostenstelle hinterlegt, wird eine Rechnung automatisch zu 100 % dorthin gebucht. Ist der Lieferant auf **„Zahlung im Voraus“** gestellt, gilt die Rechnung sofort als bezahlt (`bereitsGezahlt`, Zahlungsart „VORAUSKASSE“) und steht nicht unter Offene Posten.

---

## 4. Entity `LieferantGeschaeftsdokument`

Die Geschäftsdaten teilen sich den Primärschlüssel mit dem Dokument (`@MapsId`, `id` = `LieferantDokument.id`).

### 4.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` / `dokument` | `Long` / `LieferantDokument` (1:1) | Zugehöriges Dokument |
| `dokumentNummer` | `String` | Rechnungs-/Dokumentnummer |
| `dokumentDatum` | `LocalDate` | Datum des Dokuments |
| `betragNetto` / `betragBrutto` | `BigDecimal` | Beträge (bei Gutschriften negativ) |
| `mwstSatz` | `BigDecimal` | MwSt-Satz |
| `liefertermin` | `LocalDate` | Liefertermin |
| `bestellnummer` | `String` | Eigene Bestellnummer (Referenz) |
| `referenzNummer` | `String` | Externe Referenznummer; beim Werkstoffzeugnis die Lieferschein-Nummer |
| `zahlungsart` | `String` | z. B. `UEBERWEISUNG`, `SEPA_LASTSCHRIFT`, `KREDITKARTE`, `VORAUSKASSE` |

**KI- und Prüf-Felder:**

| Feld | Typ | Beschreibung |
|---|---|---|
| `aiRawJson` | `String` (TEXT) | Roh-JSON der KI-Analyse |
| `aiConfidence` | `Double` | Sicherheit der Erkennung (0.0–1.0) |
| `analysiertAm` | `LocalDateTime` | Zeitpunkt der Analyse |
| `datenquelle` | `String` | siehe 3.2 |
| `manuellePruefungErforderlich` | `boolean` | Pflichtangaben fehlen oder das Auslesen ist gescheitert → Hinweis „Manuelle Prüfung erforderlich“ |
| `verifiziert` | `Boolean` | Wird nur bei ZUGFeRD automatisch auf `true` gesetzt; es gibt keinen Knopf, um es von Hand zu setzen |

**Status-Flags:**

| Feld | Typ | Beschreibung |
|---|---|---|
| `genehmigt` | `Boolean` | Freigabe durch jemanden mit dem Recht „Rechnungen genehmigen“ |
| `lagerbestellung` | `Boolean` | Lagerbestellung (kein Projekt nötig) |

**Zahlungsfelder:**

| Feld | Typ | Beschreibung |
|---|---|---|
| `bezahlt` | `Boolean` | Vollständig bezahlt |
| `bezahltAm` | `LocalDate` | Datum der Zahlung |
| `tatsaechlichGezahlt` | `BigDecimal` | Gezahlter Betrag (nach Skonto) |
| `mitSkonto` | `Boolean` | Skonto genutzt |
| `bereitsGezahlt` | `Boolean` | Schon beim Eingang bezahlt (z. B. Vorkasse, Kreditkarte, Lieferant mit „Zahlung im Voraus“) |
| `zahlungsziel` | `LocalDate` | Fälligkeitsdatum |
| `skontoTage` | `Integer` | Tage für Skonto-Abzug |
| `skontoProzent` | `BigDecimal` | Skonto-Prozentsatz |
| `nettoTage` | `Integer` | Zahlungsziel in Tagen |

### 4.2 Positionen

Ausgelesene Positionen stehen in `LieferantDokumentPosition` (Positionsnummer, Artikelnummer, Bezeichnung, Werkstoff, Charge, Abmessung, Menge, Einheit, Einzelpreis, Gesamtpreis netto und optional Projekt oder Kostenstelle). Sie werden für die Suche („Nummer, Material, Charge, Kommission …“) und für die Kostenverteilung „nach Positionen“ genutzt.

---

## 5. Prüfen

Es gibt **keinen** eigenen Schritt „Verifizieren“. Entscheidend ist der Hinweis **„Manuelle Prüfung erforderlich“** (`manuellePruefungErforderlich`). Er erscheint, wenn

- je Dokumenttyp nötige Angaben fehlen (z. B. beim Werkstoffzeugnis „Nummer oder Lieferschein-Nr.“), oder
- das Auslesen per KI gescheitert ist (auch: keine KI eingerichtet).

Dann im Reiter „Dokumente“ des Lieferanten das Dokument öffnen, die Angaben ergänzen und speichern. Das Fenster „Dokument bearbeiten“ enthält auch „Bereits bezahlt“, „Bezahlt am“, „Tatsächlich gezahlt“ und „Mit Skonto bezahlt“.

---

## 6. Genehmigung

### 6.1 Ablauf

```
Rechnung kommt per E-Mail oder Import
   │
   ▼
Automatisch auslesen (ZUGFeRD / XML / KI)
   │
   ├─ „Manuelle Prüfung erforderlich“? → Angaben ergänzen
   │
   ▼
Offene Posten › Eingangsrechnungen: Haken „Genehmigt“
   (nur mit dem Recht „Rechnungen genehmigen“)
   │
   ▼
genehmigt = true
   → sichtbar für Abteilungen mit „Genehmigte Rechnungen sehen“
   → Haken „Bezahlt“ setzen, sobald überwiesen
```

Die Genehmigung setzt keine vorherige Prüfung voraus. Wird eine Rechnung innerhalb der Skontofrist als bezahlt markiert, berechnet das Programm den tatsächlich gezahlten Betrag mit Skonto.

### 6.2 Rechte pro Abteilung

Die Rechte hängen nicht an festen Abteilungsnummern, sondern an Haken je Abteilung unter **Einstellungen › Reiter „Abteilungen & Rechte“** (Gruppe „Eingangsrechnungen“):

| Recht | Wirkung |
|---|---|
| **„Rechnungen genehmigen“** | „Sieht alle Eingangsrechnungen und kann sie genehmigen.“ |
| **„Genehmigte Rechnungen sehen“** | „Sieht nur schon genehmigte Eingangsrechnungen (Buchhaltung).“ |
| keins von beiden | sieht unter Offene Posten keine Eingangsrechnungen |

Zusätzlich legt die Tabelle „Dokumente & Belege“ je Abteilung und Dokumentart fest, was jemand **„Sehen“** und **„Scannen“** darf. Administratoren sehen alle Dokumentarten, brauchen zum Genehmigen aber ebenfalls das Abteilungsrecht. Ohne Recht zeigt die Spalte „Genehmigt“ den Hinweis „Nur mit dem Abteilungsrecht „Rechnungen genehmigen““. Lehnt der Server das Genehmigen trotzdem ab (Recht inzwischen entzogen), erklärt eine Meldung, dass das Recht je Abteilung unter Einstellungen → Abteilungen & Rechte vergeben wird.

---

## 7. Projekt-Aufteilung

### 7.1 Entity `LieferantDokumentProjektAnteil`

Jede Rechnung kann **mehreren Projekten oder Kostenstellen** zugeordnet werden (Einkauf › Bestellungen › „Projekten zuordnen“).

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `dokument` | `LieferantDokument` (FK) | Zugehöriges Dokument |
| `projekt` | `Projekt` (FK, optional) | Zugeordnetes Projekt |
| `kostenstelle` | `Kostenstelle` (FK, optional) | Zugeordnete Kostenstelle |
| `prozent` | `Integer` | Prozentsatz (0–100) |
| `absoluterBetrag` | `BigDecimal` | Optionaler fester Betrag statt Prozent |
| `berechneterBetrag` | `BigDecimal` | Berechneter Anteil |
| `beschreibung` | `String` | Beschreibung der Zuordnung |
| `zugeordnetAm` | `LocalDateTime` | Zeitpunkt der Zuordnung |
| `zugeordnetVon` | `FrontendUserProfile` (FK) | Wer zugeordnet hat |
| `streckungJahre` | `Integer` | Verteilung über mehrere Jahre (Standard 1, nur Kostenstellen) |
| `streckungStartJahr` | `Integer` | Startjahr der Kostenstreckung |

Die Summe der Prozentanteile darf 100 % nicht überschreiten. Wird ein Projekt zugeordnet, legt das Programm das PDF der Rechnung zusätzlich im Projekt ab.

### 7.2 Berechnungslogik

`berechneAnteil(netto, brutto)`:
- Ist `absoluterBetrag` gesetzt → direkt verwenden.
- Sonst **Projekte:** `brutto × prozent / 100`.
- Sonst **Kostenstellen:** `netto × prozent / 100` (die Vorsteuer geht ans Finanzamt, nicht in die Gemeinkosten); fehlt der Nettobetrag, wird brutto genommen.

### 7.3 Kostenstreckung

Für wiederkehrende Kosten (z. B. eine Zertifizierung alle 4 Jahre) lassen sich Kostenstellen-Anteile über mehrere Jahre verteilen (Fenster „Projekten zuordnen“: „Kosten verteilen über … Jahre“, höchstens 20 Jahre). Projekt-Anteile werden nicht gestreckt.

```
Zertifizierung: 4.000 € netto alle 4 Jahre
  streckungJahre = 4
  streckungStartJahr = 2026

  getJahresanteil()                    → 1.000 € pro Jahr
  isStreckungAktivFuerJahr(2027)       → true
  isStreckungAktivFuerJahr(2030)       → false
```

### 7.4 Beispiel

```
Lieferantenrechnung (Brutto: 11.900 €, Netto: 10.000 €)
   ├── Projekt A: 60 %             → 7.140 € (brutto)
   ├── Projekt B: 30 %             → 3.570 € (brutto)
   └── Kostenstelle „Lager“: 10 %  → 1.000 € (netto)
```

---

## 8. Dokumentenkette und Verknüpfung

### 8.1 M:N-Verknüpfung

Lieferanten-Dokumente sind über die Tabelle `lieferant_dokument_verknuepfung` (`dokument_id`, `verknuepft_id`) miteinander verbunden:

```java
@ManyToMany verknuepfteDokumente    // → Verknüpfte Dokumente
@ManyToMany verknuepftVon           // ← Dokumente, die dieses verknüpft haben
```

Paare, die jemand von Hand getrennt hat („Gehört nicht dazu“), stehen in `lieferant_dokument_verknuepfung_gesperrt` und werden nie wieder automatisch verknüpft.

### 8.2 Typische Kette

```
Angebot ──► Auftragsbestätigung ──► Lieferschein ──► Rechnung
                                        │
                                        └── Werkstoffzeugnis (hängt seitlich am Lieferschein)
Gutschrift ──► Rechnung
```

Erlaubte Vorgänger: Rechnung → AB oder Lieferschein; Lieferschein → AB; Werkstoffzeugnis → Lieferschein oder AB; AB → Angebot; Angebot → Angebot (überarbeitete Fassung); Gutschrift → Rechnung.

**Teillieferungen und Teilrechnungen** sind möglich: eine Rechnung an mehreren Lieferscheinen, mehrere Rechnungen an einer AB.

### 8.3 Automatischer Abgleich (`LieferantDokumentAbgleich`)

Neue Dokumente werden per Punktwertung in ihre Kette eingeordnet (`BelegZuordnungService.ordneEin`):

- **Sicher** (wird automatisch verknüpft): Ein Dokument nennt die Nummer des anderen, beide tragen dieselbe Bestellnummer, oder – bei Rechnung zu AB/Lieferschein – eine gemeinsame mindestens fünfstellige Nummer, die nicht wie eine Kundennummer aussieht, bei passendem Datum. Beim Werkstoffzeugnis zählt die gleiche Charge.
- **Hinweis** (Kommission, Betrag, Positionen, gleicher Nummernstamm): reicht nicht für eine automatische Verknüpfung. Rechnungen werden nie nur auf Hinweise hin verknüpft – sie erscheinen in der Bestellübersicht als Vorschlag („Wahrscheinlich: Rechnung …“).

Beim Start läuft der Abgleich einmal je Programmversion über den Bestand (`LieferantDokumentAbgleichBackfillRunner`).

---

## 9. Zahlungsbedingungen

| Feld | Beschreibung | Beispiel |
|---|---|---|
| `nettoTage` | Zahlungsziel in Tagen | 30 Tage |
| `skontoTage` | Tage für Skonto-Abzug | 10 Tage |
| `skontoProzent` | Skonto-Prozentsatz | 2,00 % |
| `liefertermin` | Erwarteter Liefertermin | 2026-04-15 |
| `zahlungsziel` | Fälligkeitsdatum | 2026-04-30 |

In Offene Posten › Eingangsrechnungen zeigen die Spalten „Fällig“, „Skonto“ und „Status“ („Offen“, „Bezahlt“, „Überfällig“) den Stand.

---

## 10. API-Referenz

### 10.1 `/api/lieferant-dokumente`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{dokumentId}` | Ein Dokument laden |
| `PUT` | `/{dokumentId}` | Typ und Geschäftsdaten ändern (inkl. bezahlt/Skonto; nicht Genehmigung). Braucht die Bearbeitungssperre, sonst 409. |
| `DELETE` | `/{dokumentId}` | Dokument löschen (nur Admin). Rechnungen und Gutschriften sind aus GoBD-Gründen gesperrt (422) – vorher den Typ auf „Sonstiges“ ändern. |
| `GET` | `/{dokumentId}/download` | Dokument herunterladen |
| `POST` | `/{dokumentId}/reanalyze` | Ein Dokument neu auslesen |
| `POST` | `/lieferant/{lieferantId}/reanalyze` | Alle Dokumente eines Lieferanten neu auslesen (Admin) |
| `POST` | `/relink-all` | Dokumentenketten aller Lieferanten neu abgleichen (Admin) |
| `POST` | `/lieferant/{lieferantId}/relink` | Dokumentenketten eines Lieferanten neu abgleichen (Admin) |
| `POST` | `/process-assigned-emails` | Anhänge aller Lieferanten-E-Mails verarbeiten (Admin) |
| `POST` | `/process-email/{emailId}` | Anhänge einer E-Mail verarbeiten (Admin) |
| `POST` | `/lieferant/{lieferantId}/process-emails` | Anhänge der E-Mails eines Lieferanten verarbeiten (Admin) |
| `GET` / `DELETE` | `/duplicates` | Doppelte Dokumente auflisten / entfernen (Admin) |

### 10.2 Weitere Endpunkte

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/lieferanten/{id}/dokumente` | Dokumente eines Lieferanten |
| `GET` | `/api/lieferanten/{id}/dokumente/positionssuche` | „Intelligente Suche“ in Nummern, Material, Charge, Kommission |
| `POST` | `/api/lieferanten/{id}/dokumente/analyze` | Datei vorab auslesen (Import-Fenster, erkennt auch mehrere Rechnungen in einer Datei) |
| `POST` | `/api/lieferanten/{id}/dokumente/import` | Geprüftes Dokument speichern |
| `POST` | `/api/lieferanten/{lid}/dokumente/{did}/verknuepfen` | Dokumente von Hand verknüpfen |
| `GET` | `/api/lieferanten/berechtigungen` | Rechte des Angemeldeten je Dokumentart |
| `GET` | `/api/offene-posten/eingang`, `/api/offene-posten/eingang/alle` | Offene bzw. alle Eingangsrechnungen (nach Rechten gefiltert) |
| `PATCH` | `/api/offene-posten/eingang/{id}/genehmigen` | Genehmigung setzen/entfernen (403 ohne Recht) |
| `PUT` | `/api/offene-posten/eingang/{id}/bezahlt` | Als bezahlt/offen markieren |

Die Endpunkte der Bestellübersicht (`/api/bestellungen-uebersicht/...`) stehen in [BESTELLWESEN.md](BESTELLWESEN.md).

---

## 11. Entitäten- und Service-Übersicht

| Entity | Zweck |
|---|---|
| `LieferantDokument` | Dateicontainer für Lieferanten-Dokumente |
| `LieferantGeschaeftsdokument` | Ausgelesene Geschäftsdaten (1:1 mit LieferantDokument) |
| `LieferantDokumentPosition` | Ausgelesene Positionen |
| `LieferantDokumentProjektAnteil` | Aufteilung auf Projekte und Kostenstellen |
| `LieferantDokumentTyp` (Enum) | Angebot, AB, Lieferschein, Werkstoffzeugnis, Rechnung, Gutschrift, Sonstiges, Beleg |

| Service | Verantwortung |
|---|---|
| `GeminiDokumentAnalyseService` | Auslesen: ZUGFeRD → XML → KI |
| `ZugferdExtractorService` | ZUGFeRD-Daten aus PDFs |
| `EmailAttachmentProcessingService` | E-Mail-Anhänge zu Lieferanten-Dokumenten machen |
| `LieferantDokumentService` | Upload, Rechte je Abteilung, Löschen (GoBD) |
| `LieferantDokumentZugriffService` | Welche Dokumentarten jemand sehen darf |
| `BelegZuordnungService`, `LieferantDokumentAbgleich` | Neues Dokument in die Kette einordnen |
| `RechnungsVorschlagService`, `KettenVorschlagService` | Vorschläge in der Bestellübersicht |
| `LieferantDokumentZuordnungService` | Kosten auf Projekte/Kostenstellen verteilen |
| `LieferantDokumentPositionService` | Positionen auslesen und zuordnen |
| `LieferantStandardKostenstelleAutoAssigner` | Rechnung automatisch der Standard-Kostenstelle des Lieferanten zuordnen |
| `LieferantVorauskasseAutoAssigner` | Rechnungen von Lieferanten mit „Zahlung im Voraus“ als bezahlt markieren |
| `LieferantDokumentDuplikatService` | Doppelte Dokumente finden |
