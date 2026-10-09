# Werkstoffzeugnis als Lieferanten-Dokumenttyp + Positionssuche

Stand: 09.10.2026 · Baut auf `feat/lieferant-dokument-positionen` (5de31b01, V404) auf.

## Ziel

1. Die KI erkennt Werkstoffzeugnisse (3.1/3.2 nach EN 10204, Abnahmeprüfzeugnis,
   2.2-Werkszeugnis, Materialzertifikat) als eigenen Typ `WERKSTOFFZEUGNIS` statt `SONSTIG`.
2. Die Zeugnis-Positionen (Erzeugnis, Werkstoff, Charge/Schmelze, Abmessung, Menge)
   landen in `lieferant_dokument_position`.
3. Das Zeugnis hängt in der Dokumentenkette am Lieferschein – sicher über Nummern/Charge,
   sonst über Punkte (Scoring) mit starkem Gewicht auf dem zeitlichen Abstand.
4. Suche nach Positionen („Flachstahl“ findet Zeugnis, Lieferschein, Rechnung) in der
   Dokumentübersicht (Projektmanagement → Dokumente) und im Lieferanten-Reiter „Dokumente“.
5. PC-Frontend: Beschriftung „Werkstoffzeugnis“, Typ-Filter, Rechte (ohne Vorbelegung).

Nicht im Umfang: Mobile-App, Volltextindex, Vorbelegung der Abteilungsrechte,
Rechteprüfung der Dokumentübersicht (bestehende Lücke, separat).

## 1. Datenmodell & Migration `V405__werkstoffzeugnis.sql`

- `LieferantDokumentTyp.WERKSTOFFZEUGNIS` (STRING-gemappt, Reihenfolge egal).
- Typ-Spalten `lieferant_dokument.typ`, `beleg.dokument_typ`,
  `abteilung_dokument_berechtigung.dokument_typ`: Ist die Spalte ein `ENUM` ohne den Wert,
  wird der vorhandene `COLUMN_TYPE` um `'WERKSTOFFZEUGNIS'` verlängert (Altwerte wie
  `EINGANGSRECHNUNG` bleiben erhalten), `NULL`/`NOT NULL` bleibt. `VARCHAR` < 16 Zeichen
  wird verbreitert. Fehlende Tabelle/Spalte → überspringen. Alles per `information_schema`.
- `lieferant_dokument_position` + `werkstoff`, `charge`, `abmessung` (`VARCHAR(100) NULL`)
  und `suchtext VARCHAR(1000) NULL`. Einmaliges Befüllen von `suchtext` für vorhandene
  Zeilen per SQL (gleiche Normalisierung wie Java, s. u.).
- Keine Rechte-Vorbelegung (Entscheidung User).

`suchtext` = normalisierte Verkettung aus Bezeichnung, Artikelnummer, Werkstoff, Charge,
Abmessung. Gebildet von `PositionsSuchtext.normalisiere(String)` (reine Klasse) beim
Speichern (`@PrePersist/@PreUpdate` an `LieferantDokumentPosition`). Normalisierung:
kleinschreiben, `×`/`*` → `x`, Leerraum um ein `x` zwischen Ziffern entfernen
(„50 x 5“ → „50x5“), Leerraum zusammenfassen. Regexe possessiv (ReDoS).
Die Spalten-Collation `utf8mb4_unicode_ci` macht `LIKE` ohnehin groß/klein-unabhängig.

## 2. KI-Erkennung, Positionen, Dokumentenkette

### Erkennung (Prompt `GeminiDokumentAnalyseService`)
- Neuer Abschnitt „7. WERKSTOFFZEUGNIS“ mit Erkennungsmerkmalen; „Zertifikate“ raus aus
  SONSTIG. Firmen-Zertifikate (ISO 9001, Schweißfachbetrieb, EN 1090) bleiben SONSTIG.
- Zeugnis = Geschäftsdokument ohne Beträge: `dokumentNummer` (Zeugnis-Nr.),
  `dokumentDatum`, `referenzNummer` (Lieferschein-Nr. vor Auftrags-Nr.),
  `weitereReferenzen`, `bestellnummer`, `kommission`. Pflichtfeld nur Dokumentnummer.
- `typAusKiAntwort`: Zeugnis-Kopie behält den Typ.
- Regel 14 / Schema-Zeile `dokumentTyp` um WERKSTOFFZEUGNIS ergänzen.

### Positionen
- Positions-JSON (Hauptprompt „ZWEI SCHRITTE“ + `PROMPT_NUR_POSITIONEN`) bekommt optional
  `werkstoff`, `charge`, `abmessung` – für alle Typen, wenn aufgedruckt.
- Zeugnis: jede Erzeugnis-Zeile = Position, `bezeichnung` „Flachstahl 50x5“, Preise null,
  `positionsArt` WARE.
- `AusgelesenePosition` + 3 Felder; `ausKiAntwort`, `mitPositionen`, ZUGFeRD-Weg (null),
  `ersetzePositionen` übernehmen sie. `hatPositionen(WERKSTOFFZEUGNIS) = true`.

### Dokumentenkette (`LieferantDokumentAbgleich`)
- `vorgaengerTypen(WERKSTOFFZEUGNIS)` = LIEFERSCHEIN, AUFTRAGSBESTAETIGUNG.
- `kettenRang`: Zeugnis direkt nach Lieferschein, vor Rechnung.
- **Sicher**: Nummernbezug, gleiche Bestellnummer, gemeinsame trennscharfe Nummer
  (Regel bisher nur Rechnung → AB/LS, gilt nun auch Zeugnis → LS/AB),
  **gleiche Charge** (≥ 5 Zeichen, normalisiert) auf LS- und Zeugnis-Position.
- **Hinweis (Scoring)**, Schwelle **60** (ohne jedes Datum: 80):
  - Kommission 40 (vorhanden), Positionen bis 70 (vorhanden, unverändert), **neu** gleicher
    Werkstoff *und* gleiche Abmessung in einer Position 30 (Werkstoff ohne Lieferzustand
    hinter „+“). Werkstoff/Abmessung bewusst NICHT im Wortvergleich: Die Jaccard-Ähnlichkeit
    würde durch einseitige Wörter sinken.
  - **Zeitabstand**: kleinerer Abstand von Zeugnisdatum *oder* Eingangsdatum
    (`uploadDatum`) zum LS-/AB-Datum. ≤ 3 Tage +30, ≤ 14 +20, ≤ 30 +10,
    > 60 Tage → kein Hinweis-Treffer. Nähe allein erreicht die Schwelle nie.
  - Bestehende Schutzregeln: nur eindeutig führender Kandidat, Hinweis füllt nur Lücke,
    Sperren von Hand gelöster Paare gelten.
  - Hinweis-Treffer nur zum Lieferschein; an die AB hängt ein Zeugnis nur über einen sicheren Bezug.
- Abgrenzung (Tests): Zeugnis zählt nie als Rechnung/„erledigt“, nicht in offenen Posten,
  Rechnungsvorschlägen, Beleg-Auto-Eingangsrechnung, Betragsvorzeichen.

## 3. Suche (Backend)

- `LieferantDokumentPositionRepository`: Suche über `suchtext LIKE` mit bis zu 5
  Suchwörtern (UND-verknüpft, je Wort `%wort%`, `%`/`_`/`\` escaped), optional
  Lieferant, Pflicht-Typliste (sichtbare Typen). Max. 500 Treffer-Positionen.
- `LieferantDokumentSucheService.suchePositionen(eingabe, lieferantId, typen)`
  → `Map<dokumentId, PositionsTreffer(trefferText, weitereTreffer)>`. Eingabe < 2 Zeichen
  oder > 200 Zeichen → leer bzw. abgeschnitten. `trefferText` = „Bezeichnung · Werkstoff ·
  Charge 123 · 12 Stück“ der ersten Trefferposition (nach Positionsnummer).
- **API-Vertrag**
  - `GET /api/lieferanten/{id}/dokumente/positionssuche?q=…&token=…`
    → `[{ "dokumentId": 1, "trefferText": "Flachstahl 50x5 · S235JR · Charge 123456", "weitereTreffer": 2 }]`.
    Nur mit Anmeldung (gültiger Token → nur sichtbare Typen; angemeldete PC-Sitzung → alle Typen
    wie `GET /{id}/dokumente`), sonst 401. Unbekannter Lieferant → 404.
  - `GET /api/dokumentuebersicht/eingang?search=…` findet zusätzlich Positionstreffer (mit denselben
    Jahr/Monat-, Typ- und Lieferantenfiltern wie die Liste) sowie
    Referenz-, Bestellnummer und Kommission. Jedes Element bekommt
    `positionsTreffer: string | null` und `weitereTreffer: number`.
  - Positionen-DTO (`/api/bestellungen-uebersicht/positionen/{gdId}`) liefert zusätzlich
    `werkstoff`, `charge`, `abmessung`.
  - `typ=WERKSTOFFZEUGNIS` funktioniert an allen bestehenden Typ-Parametern.

## 4. Frontend (react-pc-frontend)

- `types.ts`: Typ-Union + Label „Werkstoffzeugnis“ überall, wo Typen beschriftet/sortiert
  werden (LieferantDokumenteTab, DokumentHierarchie, KettenGabel/kettenGraph,
  BerechtigungenSection, LieferantDokumentModal, ImportModal, BestellungenUebersicht …).
  Reihenfolge: nach Lieferschein.
- Dokumentübersicht: Eingangs-Filter „Werkstoffzeugnis“; Fehler beheben (`SONSTIGES` →
  `SONSTIG`, `BESTELLUNG` entfernen). Unter einem Eingangs-Treffer die Trefferzeile
  (klein, Suchbegriff hervorgehoben, „und N weitere“).
- Lieferanten-Reiter „Dokumente“: Filter „Werkstoffzeugnis“; Suche bleibt sofort im Browser,
  zusätzlich (ab 2 Zeichen, 300 ms verzögert) `positionssuche`; Ergebnis = Vereinigung,
  Trefferzeile unter dem Dokument. Platzhalter: „Nummer, Material, Charge, Kommission …“.
- Zeugnis-Detail: Positionen als Tabelle (Erzeugnis, Werkstoff, Charge, Abmessung, Menge).
- Tests: Vitest für Zusammenführen/Hervorheben, Playwright-E2E für beide Suchen.

## Tests (Backend)

- `PositionsSuchtextTest` (100 %), `LieferantDokumentAbgleichTest` (Charge sicher,
  Zeitabstand-Stufen, Schwelle 60, > 60 Tage kein Hinweis, Gleichstand),
  `GeminiDokumentAnalyseServiceTest` (Typ, Kopie, Positionen mit Zeugnis-Feldern),
  `LieferantDokumentPositionServiceTest`, Repository-Suche (`@DataJpaTest`, H2),
  Controller-Tests inkl. Security-Checkliste (SQL-Injection, XSS, ungültige IDs, Überlänge).
