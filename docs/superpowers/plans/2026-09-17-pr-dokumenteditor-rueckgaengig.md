# Rückgängig & Wiederholen im Dokumenteditor

Schließt #160

Im Dokumenteditor lassen sich die letzten **20 Arbeitsschritte zurücknehmen und
wiederherstellen** — mit zwei Knöpfen in der Kopfleiste und mit Strg+Z / Strg+Y,
so wie man es aus Word kennt. Bisher gab es Rückgängig nur *innerhalb eines
einzelnen Textfelds*; eine gelöschte Position, eine verschobene Leistung oder ein
geänderter Preis waren weg.

## Was der Nutzer jetzt tun kann

- **Alles zurücknehmen, was er selbst geändert hat:** Positionen einfügen
  (Textbaustein, Leistung, Stundensatz, Material, Bauabschnitt, Trennlinie,
  GAEB-Import), löschen, verschieben — auch in einen Bauabschnitt hinein und
  wieder heraus. Dazu Titel, Menge, Einheit, Preis, Texte, **jede Formatierung**,
  Wahlpositionen und Auswahlgruppen, Rabatt, Datum, Zahlungsziel,
  Rechnungsadresse und den Balken im Abrechnungsstand.
- **Zwei Knöpfe links neben „Textbaustein"**, mit Trennstrich abgesetzt. Der
  Tooltip sagt, was passiert: „Rückgängig: Position gelöscht (Strg+Z)".
- **Word-artige Liste** am kleinen Pfeil neben Rückgängig: die letzten Schritte,
  neuester oben. Überfahren markiert alles bis dorthin, unten steht
  „3 Schritte rückgängig machen", ein Klick nimmt sie gemeinsam zurück.
- **Tastatur überall im Dokument:** Strg+Z, Strg+Y bzw. Strg+Umschalt+Z (auf dem
  Mac mit Cmd). Auch wenn der Cursor gerade in einem Textfeld steht — es gibt nur
  **einen** Verlauf für das ganze Dokument, keine zwei konkurrierenden.
- **Die Stelle wird gezeigt:** Nach dem Zurücknehmen scrollt der Editor zur
  betroffenen Karte und hebt sie kurz hervor; bei Texten steht der Cursor an der
  geänderten Stelle.
- **Tippen wird sinnvoll gebündelt:** Ein zusammenhängend getippter Text ist
  *ein* Schritt (neuer Schritt nach ~2 s Pause, bei Feldwechsel oder einer
  anderen Aktion). Formatierungen sind immer ein eigener Schritt.

## Sperre und Versand

- **Gesperrte Dokumente haben kein Rückgängig**: gebuchte Rechnung, storniertes
  Dokument, digital angenommenes Angebot, fremdgesperrter Datensatz, eigenes
  „Fertig". Die Knöpfe sind dann nicht da, die Tastenkürzel wirken nicht, und der
  Verlauf wird geleert, sobald gesperrt wird. Das war die ausdrückliche Vorgabe
  wegen GoBD.
- **Nach dem endgültigen Versand beginnt der Verlauf neu** (E-Mail, PDF-Export,
  endgültiger Druck): Der verschickte Stand ist der neue Ausgangspunkt, damit das
  gespeicherte Angebot nicht unbemerkt vom PDF beim Kunden abweicht. „Entwurf
  senden" und der Vorschau-Druck lassen den Verlauf stehen.
- **Speichern lässt den Verlauf stehen.** Zurückgenommene Änderungen werden
  normal automatisch gespeichert.

## Was bewusst *nicht* drin ist

- Der Verlauf lebt nur in der geöffneten Seite: Nach Neuladen oder Schließen ist
  er weg (wie in Word nach dem Schließen des Dokuments).
- Serveraktionen werden nicht zurückgenommen: eine beim Einfügen bestätigte
  Kategoriezuordnung zum Projekt, Buchen, Versand, abgelegte PDFs.
- Die Schritte tragen **keinen Positionstitel** im Namen („Position gelöscht",
  nicht „Position ‚Dachrinne' gelöscht") — bewusste Entscheidung: zu lang für
  Tooltip und Liste, und welche Position gemeint war, zeigt die Hervorhebung.
- Word-Sonderfall „letzte Aktion wiederholen", wenn nichts zum Wiederholen da
  ist, gibt es nicht.
- Andere Editoren (Textbaustein, Leistung, E-Mail, Website …) und die Handy-App
  bleiben unverändert.

## Technisch

- **Neu:** `src/lib/aenderungsVerlauf.ts` (reine, wiederverwendbare
  Verlaufslogik ohne React), `document-editor/useDokumentVerlauf.ts` (Bindung an
  den Editor-Zustand), `document-editor/useVerlaufTastatur.ts`,
  `document-editor/VerlaufKnoepfe.tsx` (Knöpfe + Liste),
  `components/tiptapVerlauf.ts`.
- **Ein Schritt merkt sich nur die Felder, die er verändert hat.** Sonst würde
  das Zurücknehmen einer Positionsänderung asynchron nachgeladene Kopfdaten
  (Adresse, Zahlungsziel) oder ein automatisch gesetztes Datum überschreiben.
- **Der geteilte `TiptapEditor` bleibt für alle anderen Seiten unverändert**: Der
  Verlaufsmodus ist eine zuschaltbare Prop (`verlaufsModus`), die nur der
  Dokumenteditor setzt. Dort ist der Tiptap-eigene Verlauf aus, damit es nur
  einen gibt.
- **Keine Phantom-Schritte:** Tiptap meldet beim Mounten normalisiertes HTML
  zurück (`''` → `<p></p>`, `<ul><li>x</li></ul>` → `<ul><li><p>x</p></li></ul>`).
  Ungefiltert stünde nach dem bloßen Öffnen eines Dokuments ein Schritt im
  Verlauf, den niemand gemacht hat. Änderungen aus einem noch nie fokussierten
  Editor erzeugen deshalb keinen Schritt — in beide Richtungen getestet.
- **Nebenbei repariert:** Pauschalrabatt und der Balken im Abrechnungsstand
  zählen jetzt zur Ungespeichert-Erkennung (zentral in `baueDokumentSignatur`);
  vorher konnte eine reine Rabattänderung unbemerkt liegen bleiben. Dazu der
  Tippfehler „Rükgängig" in der Formatierungsleiste.
- **Backend, Datenbank und Migrationen: unverändert.** Reine Frontend-Änderung.

## Geprüft

| Gate | Vorher | Nachher |
|---|---|---|
| Lint | grün | grün |
| Typen (`tsc -b`) | grün | grün |
| Unit-Tests | 133 Dateien / 1467 Tests | **141 Dateien / 1628 Tests** |
| E2E (3 Bildschirmgrößen) | 627 grün | **669 grün** |

- **Code-Review** beider Abschnitte: 🟡, keine Blocker. Mutationsproben belegen,
  dass die neuen Tests echt greifen (20er-Grenze, Bündelung, Redo-Verwurf,
  Phantom-Gate in beide Richtungen, `verlauf.leeren()` bei Sperre).
- **Design-Review** im Browser in 1440 / 1536 / 1920: ein Blocker gefunden und
  behoben — die neue Knopfgruppe (76 px) hatte die Dokumentnummer auf 14 Zoll
  abgeschnitten („RE-2026/09/…"). Die Nummer schrumpft jetzt nicht mehr mit, eine
  E2E-Zusicherung hält das fest (gegengeprüft: ohne den Fix wird sie rot).
  Außerdem nachgebessert: Kontrast der Listen-Fußzeile (4,76:1 statt 2,56:1),
  Zielfläche des kleinen Pfeils (24 px), Wortlaut „Leistung eingefügt".

## Deploy

Keine Migration, keine Konfiguration, kein Backend-Neustart nötig. Das gebaute
Frontend-Bundle liegt wie üblich mit im Commit
(`src/main/resources/static/`).

## Bekannte Kleinigkeiten (kein Blocker)

- Die Liste zeigt bei mehreren gleichartigen Schritten dreimal „Position
  gelöscht" — welche gemeint ist, zeigt die Hervorhebung nach dem Klick.
- Das Öffnen der Liste hat eine 150-ms-Animation; ein Screenshot exakt währenddessen
  kann eine Markierung von vorhin zeigen. Rein optisch, das Verhalten stimmt.
- `e2e/dokument-editor-tab-schliessen.spec.ts` hat ein vorbestehendes Zeitrennen
  gegen die 500-ms-Animation der Vorschau-Spalte (nicht von diesem Vorhaben
  verursacht, in einem Lauf von dreien einmal aufgetreten).

Spec und Umsetzungsplan liegen unter `docs/superpowers/` im Repository.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
