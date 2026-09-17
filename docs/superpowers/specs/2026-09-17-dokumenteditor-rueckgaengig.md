# Spec: Rückgängig & Wiederholen im Dokumenteditor

Issue: #160

Status: Grundlage ist das mit dem Nutzer am 17.09.2026 abgeschlossene und
freigegebene Brainstorming zu Rückgängig & Wiederholen im Dokumenteditor
(nicht als eigene Brainstorming-Datei abgelegt, das Ergebnis ist unten
vollständig übernommen). Diese Spec erfindet nichts neu, sie überführt das
abgenommene Design in eine Umsetzungsgrundlage für den Grobplan. Die
Auslagerung der Verlaufslogik in eigene, wiederverwendbare Module
(`react-pc-frontend/src/lib/aenderungsVerlauf.ts` und
`react-pc-frontend/src/components/document-editor/useDokumentVerlauf.ts`) ist
vom Nutzer ausdrücklich freigegeben und fester Bestandteil dieses Umfangs,
kein optionaler Vorschlag.

## Ziel

Im Dokumenteditor (`react-pc-frontend/src/components/document-editor/index.tsx`,
Seite `react-pc-frontend/src/pages/DocumentEditorPage.tsx`; für Angebote,
Auftragsbestätigungen, Rechnungen usw.) kann der Nutzer seine letzten bis zu
20 Änderungsschritte rückgängig machen und wiederherstellen — per Knöpfe und
Tastatur, "genau wie in Word" (O-Ton Nutzer). Das betrifft alle inhaltlichen
Änderungen am Dokument: Positionen einfügen/löschen/verschieben,
Mengen/Preise/Texte ändern, Formatierung, Rabatt, Datum, Zahlungsziel,
Rechnungsadresse, Balken im Abrechnungsstand — nicht nur Text innerhalb eines
einzelnen Textfelds wie heute (Tiptap-eigener Verlauf über die
StarterKit-Extension `UndoRedo`, Pfeil-Knöpfe in `TiptapToolbar`,
`react-pc-frontend/src/components/TiptapEditor.tsx` ~Z. 383–397). Gesperrte
Dokumente (GoBD: gebuchte Rechnung usw.) bieten das nicht an.

Zielgruppe: alle Nutzer, die im Dokumenteditor Angebote,
Auftragsbestätigungen oder Rechnungen erstellen und bearbeiten — die
Standard-Erwartung aus jeder Textverarbeitung (Strg+Z/Strg+Y) soll erfüllt
sein, ohne dass Buchhaltungs- oder GoBD-Wissen vorausgesetzt wird.

## Nicht-Ziele

- Verlauf über Neuladen/Schließen der Seite hinweg (kein Speichern im
  Browser oder auf dem Server — der Verlauf lebt nur im Arbeitsspeicher der
  geöffneten Seite).
- Zurücknehmen von Serveraktionen: Kategorie-Zuordnung zum Projekt,
  Speichern, Buchen, Versand, PDF-Ablage.
- Word-Sonderfunktion "letzte Aktion wiederholen", wenn nichts zum
  Wiederholen da ist.
- Andere Editoren (Textbaustein-, Leistungs-, E-Mail-, Website-Editor,
  Artikel, Arbeitszeitart, Produktkategorie …) und die Handy-App
  (`react-zeiterfassung/`) — deren Verhalten bleibt unverändert.
- Speichern des Zahlungsziels im Dokument — das ist ein separater, bereits
  laufender Task ("Zahlungsziel im Dokumenteditor mitspeichern", eigene
  Session), siehe Abschnitt "Ungespeichert-Erkennung" unten.
- Backend-Änderungen — das Vorhaben ist rein Frontend
  (`react-pc-frontend/`).
- Korrektur des Tooltip-Tippfehlers "Rükgängig (Ctrl+Z)" in `TiptapToolbar`
  auf den anderen Seiten (dort bleibt die Leiste unverändert).

## Architektur/Ablauf

### 0. Ausgangslage

- **Editor-Datei:** `react-pc-frontend/src/components/document-editor/index.tsx`
  (~3500 Zeilen, `forwardRef`-Komponente `DocumentEditor`, L227), Seite
  `react-pc-frontend/src/pages/DocumentEditorPage.tsx`.
- **Zustand:** `blocks: DocBlock[]` (Typ in `document-editor/types.ts`;
  TEXT/SERVICE/SECTION_HEADER mit `children`/SEPARATOR/CLOSURE, immutabel
  aktualisiert), `globalRabatt`, `datum` (+ `datumRef`), `kontextDaten` (u. a.
  `rechnungsadresse`, `zahlungsziel`; + `kontextDatenRef`,
  `adresseUserEditedRef`, Flag `adresseGeaendert`), `balkenAnzeigen`.
- **Sperre:** `isLocked` (index.tsx ~Z. 452) = `readOnly` (die Seite hält
  kein eigenes Soft-Lock-Feld — Fremdsperre, eigenes "Fertig", Sperrfehler
  laufen alle in `readOnly` zusammen) || `storniert` || `digitalAngenommen`
  || gebuchte Rechnung (`istGebuchteRechnung`). Die mittlere Aktionsgruppe
  der Kopfleiste (`DocumentEditorHeader.tsx`, L41; "Textbaustein",
  "Leistung", "Stundensätze", "Material" | "Bauabschnitt", "Trennlinie" |
  "Rabatt") wird nur bei `!isLocked` gerendert.
- **Automatische (Nicht-Nutzer-)Änderungen** am selben Zustand:
  CLOSURE-Sync-Effekt (~Z. 1779, fügt den virtuellen Abschluss-Block
  `__closure__` ein/entfernt ihn), Standard-Textbausteine je Dokumenttyp
  inkl. Bezugsdatum-Reparatur (~Z. 1040–1190), `loadDokument`/`loadKontext`
  (asynchron, können theoretisch nach der ersten Nutzeränderung eintreffen),
  `bumpDatumAufHeute` (~Z. 1412, vor endgültigem Versand in `executePrint`,
  `confirmExport`, `prepareAndOpenEmail` ohne Entwurf).
- **"Ungespeichert"-Signatur**
  `JSON.stringify({ blocks ohne CLOSURE, datum, betreff, dokumentTyp })` wird
  an ~5 Stellen gebaut (Laden ~Z. 944, `handleSave` 2×, `buchenUndSperren`,
  Change-Detection ~Z. 1423, Auto-Save alle 10 s ~Z. 1437). `globalRabatt`
  und `balkenAnzeigen` fehlen darin, obwohl sie in `positionenJson`
  gespeichert werden — eine reine Rabatt-/Balken-Änderung wird heute nicht
  zuverlässig als ungespeichert erkannt/auto-gespeichert.
- **Tiptap 3.13:** `editor.commands.setContent(value)` sendet standardmäßig
  ein Update (`emitUpdate = true`,
  `node_modules/@tiptap/core/src/commands/setContent.ts` Z. 52).
  `TiptapEditor` synchronisiert externe `value`-Änderungen genau so
  (~Z. 690–705) → ein von außen gesetzter Inhalt (z. B. nach Rückgängig)
  käme heute als neue `onChange`-Änderung zurück. StarterKit-Option zum
  Abschalten des Tiptap-Verlaufs: `undoRedo: false`. `TiptapEditor`/
  `TiptapToolbar` werden von rund zehn weiteren Seiten/Komponenten genutzt
  (u. a. `TextbausteinEditor.tsx`, `Leistungseditor.tsx`, `ArtikelDetail.tsx`,
  `EmailTextvorlagenEditor.tsx`, `ProduktkategorieEditor.tsx`,
  `ArbeitszeitartEditor.tsx`, `DocumentBuilder.tsx`,
  `website/BeitragRichtextEditor.tsx`) — deren Verhalten darf sich nicht
  ändern.
- `react-pc-frontend/src/main.tsx` rendert in `<StrictMode>` →
  State-Updater-Funktionen laufen in der Entwicklung doppelt; Seiteneffekte
  (z. B. "Schritt in den Verlauf schreiben") dürfen nicht in
  `setX(prev => …)`-Updatern stehen.
- **Tests:** Vitest (`index.test.tsx`, `blockOps.test.ts`, `helpers.test.ts`
  im Editor-Ordner), Hook-Test-Vorbild
  `react-pc-frontend/src/hooks/useIdleTimer.test.ts`, E2E-Stubs
  `react-pc-frontend/e2e/hilfen/dokument-editor.ts`, E2E-Vorbild
  `react-pc-frontend/e2e/dokument-editor-seite.spec.ts` (Größen `pc-14zoll`,
  `pc-monitor`). Für `TiptapEditor` gibt es noch keine Testdatei.

### 1. Sichtbares Verhalten

**Platzierung:** Kopfleiste (`DocumentEditorHeader.tsx`), mittlere
Aktionsgruppe, links neben "Textbaustein", durch Trennstrich abgesetzt.
Symbol-Knöpfe (Lucide, z. B. `Undo2`/`Redo2`) statt Text, weil die
Kopfleiste auf dem 14-Zoll-Laptop sonst überläuft; Beschriftung über Tooltip
+ `aria-label`. Ausgegraut (disabled), wenn nichts zurückzunehmen/
wiederherzustellen ist. Bei gesperrtem Dokument ausgeblendet, wie die
übrigen Bearbeiten-Knöpfe der Gruppe.

**Sperre (`isLocked`):** kein Rückgängig/Wiederholen, Tastenkürzel
wirkungslos. Der Verlauf wird geleert, sobald das Dokument gesperrt wird —
auch bei einem reinen Soft-Lock-Wechsel (nach "Fertig", Fremdsperre,
Untätigkeit), weil der Stand danach fremd verändert sein kann.

**Verlauf-Reset nach endgültigem Versand:** betrifft vor allem
Angebote/Auftragsbestätigungen, die danach weiter bearbeitbar bleiben
(Rechnungen sind ohnehin gesperrt). Der Verlauf startet neu:

- nach erfolgreichem E-Mail-Versand (`EmailComposeModal` `onSuccess`, nicht
  bei Entwurf),
- nach erfolgreichem PDF-Export (Download),
- nach endgültigem Druck (`executePrint` mit `shouldBook`).

"Entwurf senden" und Vorschau-Druck (mit Wasserzeichen) lassen den Verlauf
unberührt. Bricht der Nutzer das E-Mail-Fenster ab, bleibt der Verlauf
ebenfalls erhalten.

### 2. "Genau wie in Word" — Detailverhalten

- **Ein gemeinsamer Verlauf** für alles, auch für Text in Textfeldern. Der
  Tiptap-eigene Verlauf wird im Dokumenteditor abgeschaltet; die Text-Pfeile
  in der Formatierungsleiste (`TiptapToolbar` im Editor, index.tsx
  ~Z. 3079) entfallen dort. Auf allen anderen Seiten, die `TiptapToolbar`
  nutzen, bleiben die Pfeile wie bisher (inkl. des heutigen
  Tooltip-Tippfehlers "Rükgängig (Ctrl+Z)" — außerhalb dieses Vorhabens,
  siehe Nicht-Ziele).
- **Tastatur:** Strg+Z / Cmd+Z = Rückgängig; Strg+Y, Strg+Umschalt+Z,
  Cmd+Umschalt+Z = Wiederholen. Wirkt überall im Dokument, auch wenn der
  Cursor in einem Textfeld/Eingabefeld steht, und verhindert dabei das
  Browser-/Tiptap-eigene Rückgängig. Ausnahmen (dort bleibt das normale
  Rückgängig des Feldes): offene Dialoge (`role="dialog"`/`aria-modal`:
  Rabatt, Auswahlgruppe, Picker, E-Mail, Export, Druck …), alles außerhalb
  des Editors (KI-Hilfe-Chat, Bearbeiten-Leiste der Seite), Felder, die erst
  beim Übernehmen/Verlassen gelten (Rechnungsadresse im Bearbeiten-Modus,
  Name des Bauabschnitts während der Eingabe).
- **Tooltip nennt den Schritt:** "Rückgängig: Position gelöscht (Strg+Z)" /
  "Wiederholen: Position gelöscht (Strg+Y)".
- **Dropdown-Liste am Rückgängig-Knopf** (wie Word): kleiner Pfeil neben dem
  Rückgängig-Symbol öffnet die Liste der zurücknehmbaren Schritte, neuester
  oben. Überfahren markiert alle Schritte bis zur Mausposition, Fußzeile
  z. B. "3 Schritte rückgängig machen"; Klick nimmt alle markierten Schritte
  auf einmal zurück. Mit Tastatur bedienbar (Pfeiltasten, Enter, Escape).
  Wiederholen hat keine Liste (wie Word).
- **Stelle zeigen:** Nach Rückgängig/Wiederholen wird die betroffene Stelle
  sichtbar — die betroffene Karte (bzw. bei zugeklappter/innenliegender
  Position der umgebende sichtbare Bauabschnitt) wird in den sichtbaren
  Bereich gescrollt und kurz hervorgehoben (Rose-Akzent,
  `prefers-reduced-motion` beachten). Bei Textänderungen steht der Cursor an
  der geänderten Stelle. Existiert die Stelle nach dem Schritt nicht mehr
  (z. B. Einfügen zurückgenommen), wird nicht gescrollt.
- **Speichern/Auto-Speichern lässt den Verlauf stehen.** Zurückgenommene
  Änderungen werden normal (auto-)gespeichert und als "Ungespeichert"
  angezeigt.
- **Screenreader:** kurze Ansage über eine `aria-live`-Region ("Rückgängig:
  Position gelöscht").

### 3. Was ein Schritt ist

Ein Schritt entsteht durch eine Nutzeraktion, nie durch automatische
Änderungen. Die folgenden Aufrufstellen in `index.tsx` erzeugen Schritte:

| Kategorie | Aktionen | Stelle in `index.tsx` |
|---|---|---|
| Einfügen | Textbaustein, Leistung (auch über `KategorieBestaetigenDialog` → `handleKategorieBestaetigt`/`handleKategorieUeberspringen`: der Schritt entsteht erst, wenn der Block wirklich im Dokument landet), Stundensatz, Bauabschnitt, Trennlinie | `addBlock` |
| Einfügen | Material mit mehreren Artikeln (= 1 Schritt) | `uebernehmeMaterial` |
| Einfügen | GAEB-Import (= 1 Schritt) | `handleFileChange` |
| Löschen | Position, auch Bauabschnitt samt Auskippen der Kinder | `removeBlock` |
| Löschen | Kind aus Bauabschnitt | `removeSectionChild` |
| Verschieben | Drag & Drop, Root und innerhalb Bauabschnitt, auch Abschluss-Block | `handleDragEnd` |
| Verschieben | Position in Bauabschnitt hinein | `moveServiceToSection` |
| Verschieben | Position aus Bauabschnitt heraus | `ejectChildFromSection` |
| Inhalte | Titel, Menge, Einheit, Einzelpreis, Beschreibung, Textbaustein-Text, Name des Bauabschnitts, sowie jede Formatierung (fett, kursiv, unterstrichen, Größe, Farbe, Markierung, Ausrichtung, Listen, Bild, "Formatierung entfernen") | `updateBlock` / `updateSectionChild` |
| Wahlpositionen/Auswahlgruppen | Modus wechseln, Gruppe speichern/auflösen/umbenennen | `modusWechsel`, `childModusWechsel`, `alternativGruppeSpeichern`, `gruppeAufloesen`, `gruppeUmbenennen` |
| Rabatt | Positionsrabatte + Pauschalrabatt zusammen = 1 Schritt | `RabattDialog` `onApply` |
| Kopf/Fuß | Datum, Zahlungsziel, Rechnungsadresse, Balken im Abrechnungsstand | `SummenFooter` `onDatumChange`, `handleZahlungszielChange`, `RechnungsadresseBlock` `onChange`, `ClosureBlock` `onBalkenAnzeigenChange` |

**Keine Schritte:** alle automatischen Änderungen (CLOSURE-Sync,
Standard-Textbausteine/Bezugsdatum-Reparatur, Laden, `bumpDatumAufHeute`),
reine Ansichtssachen (Auf-/Zuklappen, aktiver Editor, Vorschau),
Serveraktionen. Eine Aktion, die den Stand nicht verändert (z. B. ungültiges
Verschieben, das zurückgesetzt wird; "Übernehmen" ohne echte Änderung),
erzeugt keinen Schritt.

**Bündelung:**

- Tippen im selben Feld (gleicher Block + gleiche Eigenschaft, bzw.
  dasselbe Kopf-/Fußfeld) wird zu einem Schritt zusammengefasst, solange
  keine andere Aktion dazwischenkam, kein Rückgängig/Wiederholen dazwischen
  lag und die Pause zwischen zwei Eingaben unter 2 Sekunden liegt (als
  Konstante).
- Formatierungen, Einfügen per Zwischenablage und Bilder sind immer ein
  eigener Schritt, nie gebündelt. Dafür meldet `TiptapEditor` die Art der
  Änderung (Tippen vs. Formatierung/Sonstiges, z. B. anhand der
  Transaktionsschritte).
- Einzelaktionen (Datum wählen, Adresse übernehmen, Balken umschalten,
  Rabatt übernehmen, Einfügen/Löschen/Verschieben) werden nie gebündelt.

**Grenze & Verhalten:** maximal 20 Schritte, bei Überlauf fällt der älteste
raus. Eine neue Änderung nach Rückgängig verwirft die Wiederholen-Schritte.
Der Verlauf lebt nur im Arbeitsspeicher der geöffneten Seite.

**Wiederherstellen feldgenau:** Ein Schritt merkt sich nur die Felder, die
er verändert hat (vorher/nachher); Rückgängig/Wiederholen setzt nur diese
zurück. Grund: Asynchron nachladender Kontext (Adresse, Zahlungsziel) oder
ein automatisch gesetztes Datum dürfen nicht durch das Zurücknehmen einer
Positionsänderung überschrieben werden. Randfall: Treffen
Standard-Textbausteine erst ein, nachdem schon Schritte existieren, beginnt
der Verlauf neu.

**Rechnungsadresse:** Nach Rückgängig/Wiederholen einer Adressänderung
bleibt das Dokument auf "eigene Adresse" (`adresseUserEditedRef` bleibt
gesetzt), der wiederhergestellte Text wird als Override gespeichert
(`adresseGeaendert = true`). Bewusste Entscheidung im freigegebenen Design:
gleicher sichtbarer Stand, kein Sonderpfad zum Zurücksetzen des Overrides.

### 4. Ungespeichert-Erkennung

`globalRabatt` und `balkenAnzeigen` werden in die "Ungespeichert"-Signatur
aufgenommen — an allen ~5 Stellen gleich (Laden, `handleSave` 2×,
`buchenUndSperren`, Change-Detection, Auto-Save), Ladebaseline
eingeschlossen — damit zurückgenommene Rabatt-/Balken-Änderungen
zuverlässig auto-gespeichert werden und frisch geladene Dokumente nicht
sofort als geändert gelten. Das Zahlungsziel wird heute gar nicht
gespeichert; das behebt ein separater, bereits laufender Task
("Zahlungsziel im Dokumenteditor mitspeichern", eigene Session). Er fasst
dieselbe Signatur-/`handleSave`-Stelle an — beim Zusammenführen vor dem PR
mit `main` abgleichen.

### 5. Architektur der Umsetzung (Auslagerung vom Nutzer freigegeben)

- **Neu `react-pc-frontend/src/lib/aenderungsVerlauf.ts`:** reine,
  generische Verlaufslogik ohne React (Schritt aufnehmen inkl. Bündelung und
  20er-Grenze, Rückgängig, Wiederholen, mehrere Schritte auf einmal zurück,
  leeren). Wiederverwendbar, vollständig unit-getestet.
- **Neu `react-pc-frontend/src/components/document-editor/useDokumentVerlauf.ts`:**
  React-Hook, der den Verlauf an den Editor-Zustand bindet. Alle
  Nutzeränderungen in `index.tsx` laufen über eine zentrale Funktion
  (Bezeichnung + Änderung + optional Bündel-Schlüssel + Ziel für "Stelle
  zeigen"); automatische Änderungen setzen den Zustand weiterhin direkt.
  Keine Seiteneffekte in State-Updatern (StrictMode).
- **Neue Anzeige-Komponente** im Ordner `document-editor/` für Knöpfe +
  Dropdown, eingebunden in `DocumentEditorHeader.tsx` (Dateiname ist im
  Grobplan festzulegen).
- **`TiptapEditor.tsx` (geteilt, rund zehn weitere Seiten nutzen es
  unverändert):** nur zuschaltbare, abwärtskompatible Erweiterungen —
  eigener Verlauf abschaltbar (`undoRedo: false`), Art der Änderung an
  `onChange` melden, externe `value`-Synchronisation im Verlaufsmodus ohne
  Rückmeldung als Änderung und mit sinnvoller Cursorposition;
  `TiptapToolbar` kann seine Pfeil-Knöpfe ausblenden.
- **`TextBlock.tsx`, `ServiceBlock.tsx`, `SectionHeaderBlock.tsx`:** Art der
  Änderung durchreichen, Entwurfsfelder markieren, Karten für "Stelle
  zeigen" auffindbar machen.
- **Backend:** keine Änderungen.

## Betroffene Bereiche

Rein Frontend PC (`react-pc-frontend/`); Backend, Datenbank und
`react-zeiterfassung/` bleiben unangetastet.

**Neu:**

- `react-pc-frontend/src/lib/aenderungsVerlauf.ts` — Verlaufslogik (reine
  Funktionen).
- `react-pc-frontend/src/components/document-editor/useDokumentVerlauf.ts`
  — Bindung an den Editor-Zustand.
- Anzeige-Komponente für Rückgängig-/Wiederholen-Knöpfe + Dropdown-Liste im
  Ordner `document-editor/`.

**Geändert:**

- `react-pc-frontend/src/components/document-editor/index.tsx` —
  Hauptkonfliktdatei: alle Aufrufstellen der Nutzeränderungen (siehe
  Tabelle "Was ein Schritt ist" in Abschnitt 3) laufen über die zentrale
  Änderungsfunktion des Hooks; Signatur-Fix `globalRabatt`/`balkenAnzeigen`
  (Abschnitt 4); Tastaturbehandlung Strg+Z/Strg+Y mit den genannten
  Ausnahmen; Verlauf-Reset bei Sperre und nach endgültigem Versand.
- `react-pc-frontend/src/components/document-editor/DocumentEditorHeader.tsx`
  — neue Knöpfe links neben "Textbaustein", Trennstrich, nur bei
  `!isLocked` sichtbar.
- `react-pc-frontend/src/components/TiptapEditor.tsx` und die darin
  enthaltene `TiptapToolbar` — zuschaltbarer Verlaufsmodus (siehe
  Abschnitt 5), Standardverhalten für alle anderen rufenden Seiten
  unverändert.
- `react-pc-frontend/src/components/document-editor/TextBlock.tsx`,
  `ServiceBlock.tsx`, `SectionHeaderBlock.tsx` — Änderungsart durchreichen,
  "Stelle zeigen" ermöglichen.

**Nicht betroffen (Verhalten muss unverändert bleiben):** alle weiteren
Seiten/Komponenten, die `TiptapEditor`/`TiptapToolbar` nutzen, u. a.
`TextbausteinEditor.tsx`, `Leistungseditor.tsx`, `ArtikelDetail.tsx`,
`EmailTextvorlagenEditor.tsx`, `ProduktkategorieEditor.tsx`,
`ArbeitszeitartEditor.tsx`, `DocumentBuilder.tsx`,
`website/BeitragRichtextEditor.tsx`; Backend
(`src/main/java/org/example/kalkulationsprogramm/`); `react-zeiterfassung/`.

## Tests

- **Vitest, reine Logik** (`aenderungsVerlauf.ts`): 20er-Grenze, Bündelung
  (Pause/Feldwechsel/nach Rückgängig), Wiederholen wird nach neuer Änderung
  verworfen, feldgenaues Wiederherstellen, mehrere Schritte auf einmal
  zurück, leeren, No-op erzeugt keinen Schritt.
- **Vitest, Hook** (`useDokumentVerlauf.ts`, `renderHook`, Vorbild
  `react-pc-frontend/src/hooks/useIdleTimer.test.ts`).
- **Editor-Tests in `index.test.tsx`:** Position löschen → Strg+Z → wieder
  da → Strg+Y → weg; Verschieben; Rabatt als ein Schritt; Tippen gebündelt
  vs. Formatierung eigener Schritt; Dropdown-Mehrfach-Rückgängig; gebuchte
  Rechnung ohne Knöpfe und Strg+Z wirkungslos; Sperre leert Verlauf;
  erfolgreicher E-Mail-Versand leert den Verlauf, Entwurf nicht; Strg+Z im
  Dialog/Adress-Entwurf wird nicht abgefangen; Auto-Save nach Rückgängig
  (auch bei reiner Rabattänderung).
- **`TiptapEditor`-Tests** (neue Testdatei, es gibt heute keine):
  Verlaufsmodus schaltet eigenen Verlauf ab, externe Synchronisation meldet
  im Verlaufsmodus keine Änderung, Standard-Props verhalten sich wie bisher
  (Regressionsschutz für die rund zehn anderen Seiten).
- **Playwright-Spec** für den Ablauf (Vorbild
  `react-pc-frontend/e2e/dokument-editor-seite.spec.ts`, Stubs
  `react-pc-frontend/e2e/hilfen/dokument-editor.ts`): Knöpfe links neben
  "Textbaustein" in allen Playwright-Größen (`pc-14zoll`, `pc-uebergang`,
  `pc-monitor`) ohne Überlappung,
  Strg+Z/Strg+Y, Dropdown, gebuchte Rechnung ohne Knöpfe, Screenshots für
  den Design-Review.

## Offene Punkte für den Grobplan

1. **Exakte Signaturen von Kern und Hook:** wie `aenderungsVerlauf.ts` und
   `useDokumentVerlauf.ts` typisiert sind (Datenstruktur eines Schritts:
   Bezeichnung, Vorher/Nachher je Feld, Bündel-Schlüssel, Ziel für "Stelle
   zeigen"), und wie die zentrale Änderungsfunktion in `index.tsx` den
   aktuellen Stand liest (Refs vs. State), ohne Seiteneffekte in
   State-Updatern (StrictMode-Doppelausführung beachten).
2. **Wortliste der Schritt-Bezeichnungen** in Handwerker-Sprache (Beispiele
   aus dem Brainstorming: "Leistung eingefügt", "Position gelöscht",
   "Position verschoben", "Menge geändert", "Text geändert", "Formatierung
   geändert", "Rabatt geändert", "Datum geändert") — die vollständige Liste
   für alle Aktionen aus der Tabelle in Abschnitt 3 fehlt noch, ebenso die
   Entscheidung, ob und wie der Positionstitel (gekürzt) im Namen erscheint.
3. **Erkennung Tippen vs. Formatierung in Tiptap:** auf welchen Signalen
   (ProseMirror-Transaktionsschritte, Paste-Meta) `TiptapEditor` die
   Unterscheidung trifft, die es an `onChange` meldet; dazu das genaue
   Vorgehen zum Setzen der Cursorposition nach einem Text-Rückgängig/
   -Wiederholen.
4. **Reichweite der geänderten `value`-Synchronisation in `TiptapEditor`:**
   ob der Verlaufsmodus (externe Werte ohne Rückmeldung als Änderung) über
   eine neue, opt-in gesetzte Prop läuft oder sich als für alle rufenden
   Seiten sicheres Standardverhalten erweisen lässt — die rund zehn anderen
   Seiten dürfen sich dabei nicht ändern.
5. **Task-Schnitt für `index.tsx`:** wie die Umsetzung so in Tasks
   geschnitten wird, dass die Hauptkonfliktdatei (~3500 Zeilen, gleichzeitig
   vom separaten Zahlungsziel-Task an der Signatur-/`handleSave`-Stelle
   berührt) in möglichst wenigen Tasks liegt, und wie die Integration der
   zentralen Änderungsfunktion an allen Aufrufstellen mit diesem parallelen
   Task zeitlich abgestimmt wird.
