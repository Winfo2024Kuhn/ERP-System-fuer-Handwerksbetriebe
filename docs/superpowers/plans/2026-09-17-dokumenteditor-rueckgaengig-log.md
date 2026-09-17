# Kontext-Log: Rückgängig & Wiederholen im Dokumenteditor

Append-only. Absoluter Pfad (Haupt-Checkout dieses Vorhabens, nicht dein
eigenes Task-Worktree):
`/Users/marvinkuhn/dev/wt/verlauf-integration/docs/superpowers/plans/2026-09-17-dokumenteditor-rueckgaengig-log.md`
Lock-Protokoll: siehe `.claude/skills/loese-problem/references/kontext-log-format.md`.

**Wichtig:** Immer diesen absoluten Pfad verwenden, nicht den relativen Pfad
aus dem eigenen Worktree — jedes Task-Worktree hat eine eigene Kopie dieser
Datei; ein relativer Schreibzugriff landet dort unsichtbar für alle anderen
Agenten und geht beim Merge verloren.

Issue: #160
Spec: `docs/superpowers/specs/2026-09-17-dokumenteditor-rueckgaengig.md`
Plan: `docs/superpowers/plans/2026-09-17-dokumenteditor-rueckgaengig.md`
Feature-Branch: `feature/dokument-verlauf` (existiert bereits, auch auf
`origin`, mit Spec- und Plan-Commits; abgezweigt von `main` @ 0ff937db,
17.09.2026 — nicht neu anlegen, kein `git checkout -b`)

Abschnitte: 2 (Abschnitt 1 = Task 1–3 parallel, Abschnitt 2 = Task 4).
Branch-/Worktree-Zuordnung je Task steht im Plan-Kopf der Task-Blöcke.

---

## Ausgangslage vor Abschnitt 1 (Orchestrator)

Zeit: 2026-09-17 (vor dem Start des ersten Coding-Agenten)
Stand: `feature/dokument-verlauf` @ 4b9be7c7 (nur Spec- und Plan-Commits, kein Produktivcode)

**Gemessene Baseline (alles grün):**

| Gate | Befehl (in `react-pc-frontend/`) | Ergebnis |
|---|---|---|
| Lint | `npm run lint` | exit 0, keine Meldung |
| Unit-Tests | `npm run test` (vitest run) | 133 Dateien, 1467 Tests, alle grün, ~25 s |
| Typen | `npx tsc -b` | exit 0 |
| E2E | `E2E_PORT=5311 npx playwright test` | 627/627 grün, 2,8 min, drei Größen (`pc-14zoll`, `pc-uebergang`, `pc-monitor`) |

**Abnahmeregel:** Es gibt **keine** vorbestehenden Fehler. Jeder rote Test,
jeder Lint-Fehler und jeder Typfehler ab hier ist neu und gehört zum Vorhaben.

**Backend:** wird nicht angefasst (reines Frontend-Vorhaben). Keine
Maven-Gates, keine Backend-Baseline. Reviewer prüfen per
`git diff --name-only main...HEAD`, dass nichts unter `src/main/java` oder
`src/test/java` liegt.

**Umgebung (macOS, nicht Windows — die Fallstricke-Datei ist windowslastig):**

- Shell: zsh/bash. Kein PowerShell, keine NTFS-Junctions.
- `node_modules` in jedem Worktree als Symlink:
  `ln -s /Users/marvinkuhn/dev/ERP-System-fuer-Handwerksbetriebe/react-pc-frontend/node_modules <worktree>/react-pc-frontend/node_modules`
  (legt der Orchestrator an). Vor `git worktree remove` erst den Symlink mit
  `rm <worktree>/react-pc-frontend/node_modules` lösen.
- Node v22.23.2, npm 10.9.8.
- `graphify` gibt es **nur** im Haupt-Checkout
  (`/Users/marvinkuhn/dev/ERP-System-fuer-Handwerksbetriebe/graphify`), nicht in
  Worktrees. `./graphify update .` läuft einmal am Ende, im Haupt-Checkout,
  durch den Orchestrator.
- Der graphify-Hook-Guard in `.claude/settings.json` zeigt auf Windows-Pfade und
  greift hier nicht.
- Der Skill `superpowers:test-driven-development` ist in dieser Umgebung **nicht
  installiert**. Testgetrieben wird trotzdem gearbeitet: roter Test, Grund des
  Fehlschlags verstehen, umsetzen, grün, committen.
- Build-Gate ohne Artefakte:
  `npx tsc -b && npx vite build --outDir "$(mktemp -d)" --emptyOutDir`.
  `npm run build` schreibt nach `../src/main/resources/static/`
  (`emptyOutDir: false`) und erzeugt Merge-Konflikte.

**Parallele fremde Arbeit (nicht anfassen):**

- Im Haupt-Checkout liegt uncommittete Arbeit des Nutzers (Vorkalkulation:
  `react-pc-frontend/src/features/vorkalkulation/`, `VorkalkulationDummy.tsx`,
  `e2e/vorkalkulation.spec.ts`, Änderung an `App.tsx`).
- In einer anderen Session läuft „Zahlungsziel im Dokumenteditor mitspeichern“
  (Branch `claude/competent-satoshi-5be572`, Worktree
  `.claude/worktrees/competent-satoshi-5be572`). Sie ändert `handleSave` und die
  Ungespeichert-Signatur in derselben `index.tsx` wie Task 4. Deshalb läuft der
  Signatur-Umbau hier über die eine Hilfsfunktion `baueDokumentSignatur`
  (Task 1). Der Orchestrator gleicht vor dem PR mit `main` ab.

## Abschnitt 1 — Task 3 (Coding-Agent)

Zeit: 2026-09-17T20:38:00Z
Branch: verlauf/task-3-knoepfe
Commit(s): 3d3e807a
Status: fertig

Was gemacht wurde:
- Neue Komponente `react-pc-frontend/src/components/document-editor/VerlaufKnoepfe.tsx`:
  zwei Symbol-Knöpfe (Lucide `Undo2`/`Redo2`, Stil wie die Nachbarn in der
  Kopfleiste) plus Dropdown am Rückgängig-Knopf (`ChevronDown`), Portal an
  `document.body` wie `WahlpositionMenu` (fixed, linksbündig unter dem Pfeil,
  gegen den Fensterrand geklemmt, `MENU_BREITE = 260`, `max-h-72
  overflow-y-auto`). Zeigt die letzten Schritte (neuester oben), markiert
  beim Überfahren/Fokussieren alle Einträge bis zur Position (`bg-rose-50`),
  Fußzeile `<N> Schritte rückgängig machen` (Einzahl `1 Schritt …`). Klick
  oder Enter auf einen Eintrag ruft `onRueckgaengig(anzahl)`. Tastatur:
  ArrowDown/ArrowUp/Home/End wandern durch die Einträge, Enter löst aus,
  Escape schließt + Fokus zurück zum Pfeil, Tab schließt. Text im Eintrag
  über `break-words` (nicht `truncate`).
- Tooltips (`title`) nach Plan-Wortlaut: `Rückgängig: <Bezeichnung>
  (Strg+Z)` / `Wiederholen: <Bezeichnung> (Strg+Y)`, ohne Schritte „Nichts
  zum Rückgängigmachen"/„Nichts zum Wiederholen". `aria-label` bleibt kurz
  (`"Rückgängig"`/`"Wiederholen"`) — beides exakt wie im Plan-Testkatalog
  (Task 3, nicht der kürzere Wortlaut aus der Auftrags-Zusammenfassung).
- `DocumentEditorHeader.tsx`: neue optionale Prop `verlauf?:
  VerlaufKnoepfeProps` (Typ-Import aus `./VerlaufKnoepfe`). Gesetzt, rendert
  sie `<VerlaufKnoepfe>` links von „Textbaustein" mit Trennstrich
  (`w-px h-5 bg-slate-200 mx-0.5`), innerhalb des bestehenden
  `!isLocked`-Blocks. Ungesetzt (Default `undefined`) ändert sich am Header
  nichts.
- Testgetrieben je Schritt: `VerlaufKnoepfe.test.tsx` zuerst rot (Modul
  fehlte), dann Komponente gebaut → grün (17 Tests). Anschließend
  `DocumentEditorHeader.test.tsx` rot (1 von 3 Fällen, da `verlauf`-Prop
  fehlte), dann Header erweitert → grün (3 Tests).

Gate-Ergebnisse:
- `npx vitest run src/components/document-editor/VerlaufKnoepfe.test.tsx
  src/components/document-editor/DocumentEditorHeader.test.tsx`: 2 Dateien /
  20 Tests grün.
- `npx vitest run src/components/document-editor/index.test.tsx`
  (Zusatzgate): unverändert 1 Datei / 25 Tests grün.
- `npm run lint`: grün, keine Warnungen/Fehler.
- `npx tsc -b && npx vite build --outDir "$(mktemp -d)" --emptyOutDir`:
  grün (2114 Module transformiert, keine neuen Build-Artefakte im Repo).

Bedenken / Abweichungen vom Plan:
- Keine inhaltliche Abweichung. Einzige Klarstellung: die Auftrags-
  Zusammenfassung (nicht der Plan selbst) schlug vor, `aria-label` trage
  denselben langen Wortlaut wie `title`. Der Plan-Testkatalog (Zeilen
  674–678 der Plan-Datei) verlangt explizit kurze `aria-label`-Werte
  („Rückgängig"/„Wiederholen") und einen langen `title`. Nach Plan
  umgesetzt, wie von der Auftragsnotiz selbst („Wortlaut nach Plan")
  vorgesehen — hier zur Sicherheit vermerkt, falls Task 4 beim Verdrahten
  etwas anderes erwartet.
- Keine Playwright-Spec für diesen Task (wie im Plan vorgesehen): die
  `verlauf`-Prop ist optional und wird in dieser Runde von niemandem
  gesetzt (`index.tsx` ist tabu, gehört Task 4) — es gibt keinen
  nutzerseitig erreichbaren Ablauf. Sichtbarer Ablauf inkl. Screenshots
  liegt in der Haupt-Spec von Task 4.
- Nur die vier zugewiesenen Dateien angefasst: `VerlaufKnoepfe.tsx` (neu),
  `VerlaufKnoepfe.test.tsx` (neu), `DocumentEditorHeader.tsx` (geändert),
  `DocumentEditorHeader.test.tsx` (neu). `index.tsx` nicht angefasst.
- Keine laufenden Dev-/Test-Prozesse aus diesem Worktree zurückgelassen
  (geprüft per `pgrep -fl "vite|vitest|playwright" | grep verlauf-task-3`,
  keine Treffer). `./graphify update` bewusst nicht ausgeführt (Vorgabe für
  Coding-Agenten in diesem Task).

## Abschnitt 1 — Task 1 (Coding-Agent)

Zeit: 2026-09-17T18:42:11Z
Branch: verlauf/task-1-kern
Commit(s): 6592d52e, b494290c, 5b62cdb9, 23a89fd4
Status: fertig

Was gemacht wurde:
- `src/lib/aenderungsVerlauf.ts` (+ `.test.ts`, 24 Tests): generischer,
  React-freier Verlaufskern. `Schritt<Stand, Ziel>` merkt sich je Schritt nur
  die tatsächlich geänderten Top-Level-Felder (`Feldaenderungen<Stand>` =
  Map von Feld auf `{vorher, nachher}`). `schrittAufnehmen` bündelt
  aufeinanderfolgende Schritte mit gleichem `buendelSchluessel` innerhalb
  von `BUENDEL_PAUSE_MS` (2000 ms), sofern `buendelnErlaubt` — das wird nach
  jedem `rueckgaengig`/`wiederholen` auf `false` gesetzt und erst durch den
  nächsten aufgenommenen Schritt wieder auf `true`. Ein Bündel, das per
  Saldo nichts mehr ändert (tippen + zurücklöschen), fällt komplett weg.
  Deckelung bei `MAX_SCHRITTE` (20), ältester fliegt raus. `rueckgaengig`/
  `wiederholen` akzeptieren eine `anzahl` (Dropdown-Liste in Task 3): je
  betroffenem Feld gewinnt bei `rueckgaengig` der VORHER-Wert des ältesten,
  bei `wiederholen` der NACHHER-Wert des neuesten der bewegten Schritte —
  feldgenau, ein von der Bewegung nicht betroffenes Feld taucht in `werte`
  gar nicht auf. `tiefGleich` beginnt mit `a === b` (Performance-Kern plus
  Kurzschluss bei Werten wie `NaN`, die strukturell nie gleich wären).
- `src/components/document-editor/useDokumentVerlauf.ts` (+ `.test.ts`,
  8 Tests): bindet den Kern an `DokumentStand` (`blocks`, `globalRabatt`,
  `datum`, `zahlungsziel`, `rechnungsadresse`, `balkenAnzeigen`) und
  `VerlaufsZiel` (`blockId`, optional `sectionId`/`feld`). Ref-Spiegel für
  Verlauf/`leseStand`/`schreibeStand`/`gesperrt` nach Vorbild
  `useIdleTimer.ts:64-75` (Refs werden in einem Effekt OHNE Dep-Array
  aktualisiert, kein `setState` im Effekt-Körper). `aendern` liest den Stand
  synchron aus dem Ref, berechnet, steigt bei `istLeer` ohne Schreibvorgang
  aus, schreibt sonst Ref **und** Aufrufer-State und legt den Schritt mit
  `Date.now()` ab. `gesperrt: true` lässt `aendern` `false` und
  `rueckgaengig`/`wiederholen` `null` liefern, ohne den Stand anzufassen.
- `src/components/document-editor/useVerlaufTastatur.ts` (+ `.test.ts`,
  24 Tests): globaler `keydown`-Listener auf `window` im CAPTURE-Modus
  (vor ProseMirror und dem Browser-eigenen Rückgängig), Tastenerkennung über
  `e.key.toLowerCase()` (QWERTZ-fest). Strg/Cmd+Z → Rückgängig; Strg+Y,
  Strg/Cmd+Shift+Z → Wiederholen; zusätzliches Alt unterdrückt beides.
  `darfVerlaufTasteGreifen` separat exportiert und getestet: Ziel muss
  innerhalb der Editor-Wurzel liegen (Sonderfall `document.body` — nach dem
  Löschen des fokussierten Elements hängt der Fokus dort, das muss weiter
  greifen), kein offener modaler Dialog (`[aria-modal="true"]`) irgendwo im
  Dokument, kein Vorfahre mit `data-eigenes-rueckgaengig`. Bei Zuständigkeit
  `preventDefault()` und `stopPropagation()`.
- `src/components/document-editor/helpers.ts` (+ 6 neue Tests in
  `helpers.test.ts`, ans Ende angehängt): `baueDokumentSignatur` ergänzt.
  Ersetzt künftig (Verdrahtung folgt in Task 4) die fünf identischen
  `JSON.stringify({ blocks: persistedBlocks, datum, betreff, dokumentTyp })`-
  Aufrufe in `index.tsx` (Change-Detection-Effekt, Auto-Save-Intervall,
  zwei Stellen beim Löschen-Fluss, initiales `lastSavedStateRef`) durch
  einen einzigen Aufruf, der zusätzlich `globalRabatt` und `balkenAnzeigen`
  einschließt — beide flossen bisher NICHT in den „Ungespeichert"-Vergleich
  ein. CLOSURE-Marker wird über **beide** Merkmale gefiltert (`id !==
  CLOSURE_BLOCK_ID && type !== 'CLOSURE'`) — Vorbild ist der defensivere der
  beiden in `index.tsx` bereits vorhandenen Filter (Zeile ~943), nicht der
  einfachere (Zeilen 1237/1424/1441). Import von `CLOSURE_BLOCK_ID` aus
  `./blockOps` (zyklusfrei geprüft: `blockOps.ts` importiert nur `./types`).

Gate-Ergebnisse (alle in `react-pc-frontend/`):
- `npx vitest run src/lib/aenderungsVerlauf.test.ts
  src/components/document-editor/useDokumentVerlauf.test.ts
  src/components/document-editor/useVerlaufTastatur.test.ts
  src/components/document-editor/helpers.test.ts`: 4 Dateien / 146 Tests
  grün (24 + 8 + 24 + 90, davon 84 vorbestehende in `helpers.test.ts`
  unverändert grün geblieben).
- `npm run lint`: grün, keine Meldung.
- `npx tsc -b && npx vite build --outDir "$(mktemp -d)" --emptyOutDir`:
  grün, 2113 Module transformiert, keine neuen Dateien im Repo (Build-Ziel
  war ein Temp-Verzeichnis).

Bedenken / Abweichungen vom Plan:
- Keine inhaltliche Abweichung von den im Plan vorgegebenen Signaturen
  (Abschnitt „Interfaces" von Task 1 wortwörtlich übernommen, inkl. der
  bereits gegen tsc/eslint geprüften Prototypen).
- Eine Stelle war im Plan bewusst offengelassen und hier entschieden:
  `schrittAufnehmen`/`rueckgaengig`/`wiederholen` übernehmen bzw. mergen
  `ziel` nach der Regel „explizit gesetzt (auch `null`) überschreibt,
  `undefined` (Feld weggelassen) behält den bisherigen Wert" — betrifft nur
  das Bündeln aufeinanderfolgender Tipp-Schritte im selben Feld. Keiner der
  im Plan aufgezählten Testfälle (a)–(p) für `aenderungsVerlauf.test.ts`
  spezifiziert dieses Detail; Task 4 (einziger Konsument von `ziel`) sollte
  das beim Verdrahten im Hinterkopf behalten, falls „Stelle zeigen" während
  eines laufenden Bündels ein abweichendes Ziel erwarten würde.
- Keine Playwright-Spec für diesen Task (wie im Plan vorgesehen): reine
  Logik ohne nutzerseitig erreichbaren Ablauf. Alle vier Module werden von
  niemandem importiert, solange `index.tsx` unangetastet bleibt (rein
  additiv) — die Module sind erst nach Task 4 in der Oberfläche verdrahtet;
  ihr Verhalten deckt dann die Haupt-Spec von Task 4 ab.
- Nur die acht zugewiesenen Dateien angefasst (4 neue Modul-Paare +
  Erweiterung von `helpers.ts`/`helpers.test.ts`). `index.tsx` nicht
  angefasst, `./graphify update` bewusst nicht ausgeführt (macht der
  Orchestrator am Ende im Haupt-Checkout).
- Keine laufenden Dev-/Test-Prozesse aus diesem Worktree zurückgelassen
  (geprüft per `pgrep -fl "vite|vitest|playwright" | grep verlauf-task-1`,
  keine Treffer) — in dieser Runde wurde ohnehin nur `vitest run`
  (Single-Shot), `tsc -b`, `vite build` und `eslint` ausgeführt, kein
  Watch-/Dev-Server gestartet.

## Abschnitt 1 — Task 2 (Coding-Agent)

Zeit: 2026-09-17T18:45:45Z
Branch: verlauf/task-2-tiptap
Commit(s): 783ba1ce, dce15c59
Status: fertig

Was gemacht wurde:
- `src/components/tiptapVerlauf.ts` (neu, React-frei) + `.test.ts` (8 Tests,
  echter Tiptap-Editor in jsdom statt Mock): `aenderungsArtVon(transaction,
  appendedTransactions)` klassifiziert `'tippen'` vs `'sonstiges'` anhand der
  ProseMirror-Transaktion — `'sonstiges'` bei `paste`/`uiEvent`-Meta, bei
  einer angehängten Transaktion mit `docChanged`, bei 0 Steps, oder wenn
  irgendein Step kein `ReplaceStep` ist bzw. sein `slice.content` (rekursiv
  über `Fragment.descendants`) einen Knotentyp außerhalb von
  `{text,paragraph,hardBreak,listItem}` enthält. Verifiziert gegen
  `splitBlock()`, `deleteRange()`, `toggleBold()` (AddMarkStep), simulierten
  Paste, und `insertContent({type:'image',...})`. `setzeInhaltVonAussen`
  übernimmt einen extern gesetzten Wert: im Standardmodus wie bisher
  `setContent(wert)`; im Verlaufsmodus `setContent(wert,{emitUpdate:false})`
  gefolgt von `alterInhalt.findDiffStart(neuerInhalt)` und
  `TextSelection.near(doc.resolve(diff))` — kein Update-Event, Cursor an der
  ersten abweichenden Stelle.
- `src/components/TiptapEditor.tsx` (geändert) + `.test.tsx` (neu, 7 Tests,
  echter Editor): neue optionale Prop `verlaufsModus` (Default `false`).
  `useEditor`: `StarterKit.configure({..., ...(verlaufsModus ? {undoRedo:
  false} : {})})`. `onChange`-Signatur erweitert auf `(value, art) => void`;
  `onUpdate` meldet `verlaufsModus ? aenderungsArtVon(transaction,
  appendedTransactions) : 'sonstiges'`. Sync-Effekt nutzt jetzt
  `setzeInhaltVonAussen(editor, value, verlaufsModus)` (beide Zweige:
  Erstsync und Folgesync), `verlaufsModus` in der Dep-Liste. Beide Toolbars
  (exportiertes `TiptapToolbar` und die interne Kopie in `TiptapEditor`
  selbst) blenden Undo/Redo-Knöpfe samt folgendem Trennstrich nur, wenn
  `typeof editor.commands.undo === 'function'` — ohne diese Prüfung stürzt
  `editor.can().undo()` im Verlaufsmodus mit TypeError ab (verifiziert:
  Test rendert `<TiptapToolbar>` mit einem Verlaufsmodus-Editor und prüft
  `.not.toThrow()`). Tippfehler „Rükgängig (Ctrl+Z)" in `TiptapToolbar`
  bewusst nicht angefasst (Nicht-Ziel der Spec); vor dem Commit erneut
  gegrept (`grep -rn "Rükgängig\|Rückgängig (Ctrl" src e2e`) — einzige
  Treffer sind die Quelle selbst und meine neuen Tests.
- `TextBlock.tsx`/`.test.tsx`, `ServiceBlock.tsx`/`.test.tsx`,
  `SectionHeaderBlock.tsx`/`.test.tsx` (alle geändert): `onUpdate`
  (bzw. zusätzlich `onUpdateChild` bei SectionHeaderBlock) nimmt neu einen
  optionalen dritten Parameter `art?: TiptapAenderungsArt` und reicht ihn
  von `TiptapEditor.onChange` durch. Alle drei bekommen die optionale Prop
  `verlaufsModus`, die 1:1 an die eingebetteten `TiptapEditor`-Instanzen
  (bzw. bei SectionHeaderBlock zusätzlich an die Kind-`ServiceBlock`s)
  durchgereicht wird. Jeweils per rotem Test abgesichert (gemockter
  `TiptapEditor`/`ServiceBlock` meldet `onChange('<p>neu</p>','sonstiges')`
  bzw. `onUpdate('k1',{title:'neu'},'sonstiges')`).
- `SortableBlock.tsx`, `AlternativGruppeBox.tsx` (beide geändert, keine
  neue Testdatei — so auch im Plan vorgesehen): reine Attribut-Ergänzungen.
  Bestehende `AlternativGruppeBox.test.tsx` zur Sicherheit trotzdem
  gegengeprüft (2/2 weiter grün), obwohl nicht offiziell Gate dieses Tasks.

Gesetzte DOM-Verträge (für Task 4):
- `data-block-id="<block.id>"` am äußeren `div` des Normalzweigs in
  `SortableBlock.tsx` (NICHT am DragOverlay-Zweig, sonst doppelt im DOM)
  und am Kind-Wrapper-`div` in `SectionHeaderBlock.renderChild`.
- `data-verlauf-feld="…"` gesetzt: `"title"` am Titel-Input,
  `"quantity"`/`"unit"`/`"price"` an den drei Kalkulationsfeldern und
  `"description"` am Beschreibungs-Wrapper — alle vier in `ServiceBlock.tsx`;
  `"content"` am Tiptap-Wrapper in `TextBlock.tsx`; `"sectionLabel"` am
  Namensfeld (nur im Bearbeiten-Zustand vorhanden — vorher reiner Text) in
  `SectionHeaderBlock.tsx`.
- `data-eigenes-rueckgaengig="true"` am Bauabschnitts-Namensfeld
  (`SectionHeaderBlock.tsx`, selbes Input wie oben) und am
  Umbenennen-Eingabefeld der Auswahl-Gruppe (`AlternativGruppeBox.tsx`).

Gate-Ergebnisse (alle in `react-pc-frontend/`):
- `npx vitest run src/components/tiptapVerlauf.test.ts
  src/components/TiptapEditor.test.tsx
  src/components/document-editor/TextBlock.test.tsx
  src/components/document-editor/ServiceBlock.test.tsx
  src/components/document-editor/SectionHeaderBlock.test.tsx`:
  5 Dateien / 44 Tests grün.
- `npm run lint`: grün, keine Meldung.
- `npx tsc -b && npx vite build --outDir "$(mktemp -d)" --emptyOutDir`:
  grün, 2114 Module transformiert, keine neuen Dateien im Repo.
- Zusatzgate (bestehende `TiptapEditor`-Konsumenten):
  `npx vitest run src/pages/TextbausteinEditor.test.ts
  src/components/document-editor/index.test.tsx`: 2 Dateien / 36 Tests
  unverändert grün.

Bedenken / Abweichungen vom Plan:
- Keine inhaltliche Abweichung von den im Plan vorgegebenen Interfaces/
  Steps — alle Signaturen, Datei- und Zeilenangaben (`:571`, `:603`,
  `:668-683`, `:384-397`, `:731-745` im Plan) haben mit ±1 Zeile exakt
  gepasst.
- Wichtige Beobachtung für Task 4 (kein Bug, aber nicht offensichtlich):
  `TiptapEditor.tsx` ruft in einem **vorbestehenden**, von mir nicht
  geänderten Effekt `editor.setEditable(!readOnly)` auf. Tiptaps
  `setEditable(editable, emitUpdate=true)` feuert dabei IMMER ein
  `update`-Event mit einer leeren, frischen Transaktion (`this.state.tr`,
  0 Steps) — unabhängig vom Verlaufsmodus, schon bei jedem Mount. Das
  bedeutet: `onChange(wert, art)` wird auch im Verlaufsmodus einmal beim
  Mounten (und bei jedem `readOnly`-Wechsel) aufgerufen, mit dem
  unveränderten aktuellen `wert` und `art='sonstiges'` (leere Transaktion
  → `aenderungsArtVon` liefert `'sonstiges'`, siehe die entsprechende
  Fallunterscheidung). Harmlos für Task 1s `useDokumentVerlauf.aendern`
  (meldet bei unverändertem Wert `false` und legt keinen Schritt an — die
  Werte sind ja identisch), aber falls Task 4 aus jedem `onChange`-Aufruf
  ungeprüft einen Verlaufs-Schritt bauen wollte, bitte diesen Fall im Kopf
  behalten. Mein `TiptapEditor.test.tsx` musste den Mock-Call-Count deshalb
  vor dem eigentlichen Rerender-Test per `onChange.mockClear()`
  zurücksetzen (kommentiert im Test), um genau die value-Sync-Aussage zu
  isolieren.
- `index.tsx` nicht angefasst (tabu, Task 4). `./graphify update` bewusst
  nicht ausgeführt. Keine Build-Artefakte committet.
- Keine Playwright-Spec (wie im Plan vorgesehen): alle neuen Props sind
  opt-in und werden erst von Task 4 gesetzt — sichtbar ändert sich in
  dieser Runde nichts, weder im Dokumenteditor noch auf den sechs anderen
  Tiptap-Seiten. Regressionsschutz für diese Seiten liegt in den
  Standardmodus-Fällen von `TiptapEditor.test.tsx` plus dem Zusatzgate.
- Keine laufenden Dev-/Test-Prozesse aus diesem Worktree zurückgelassen
  (`pgrep -fl "vite|vitest|playwright|esbuild" | grep verlauf-task-2` ohne
  Treffer) — nur Single-Shot-Kommandos (`vitest run`, `tsc -b`,
  `vite build`, `eslint`) ausgeführt, kein Watch-/Dev-Server gestartet.

## Abschnitt 1 — Review (Abschnitts-Reviewer, loese-problem)

Zeit: 2026-09-17T18:56:01Z
Branch: feature/dokument-verlauf (gemergter Stand b966c741)
Commit(s) geprüft: 9a263d93 (Task 1), f20c8739 (Task 2), f6316ba8 (Task 3)
Status: fertig — **Ampel 🟡 (abgenommen)**

Gates (selbst gefahren, Worktree /Users/marvinkuhn/dev/wt/verlauf-integration):
- Backend: `git diff --name-only main...HEAD | grep "^src/(main|test)/java"` ohne
  Treffer → keine Maven-Gates, wie im Plan vorgesehen.
- `npm run lint` exit=0 · `npx tsc -b` exit=0
- `npm run test` exit=0 — **140 Dateien / 1576 Tests grün**
  (Baseline 133/1467 ⇒ +7 Dateien, +109 Tests; keine vorbestehenden und keine
  neuen Fehler). E2E bewusst nicht gefahren (Design-Reviewer).

Mutationsproben (alle danach restlos zurückgenommen, `git status --short` leer):
- `MAX_SCHRITTE` 20→100 ⇒ genau `(c) kappt bei MAX_SCHRITTE` rot ✓
- Bündel-Pause ignoriert ⇒ genau `(e) buendelt NICHT ausserhalb der Pause` rot ✓
- `schrittAufnehmen` leert `wiederholbar` nicht ⇒ genau `(l)` rot ✓
- `istLeer`-Guard entfernt ⇒ `(b)` rot ✓ (Hook-Tests bleiben grün, weil `aendern`
  einen eigenen Guard hat — beide Ebenen sind getrennt abgesichert)
- `data-block-id` am Kind-Wrapper in `SectionHeaderBlock` entfernt ⇒ rot ✓
- `data-block-id` in `SortableBlock.tsx` entfernt ⇒ **nichts rot** (Lücke, 🟡)
- `setzeInhaltVonAussen` unterdrückt das Update auch im Standardmodus ⇒
  `tiptapVerlauf.test.ts` rot ✓, `TiptapEditor.test.tsx` bleibt grün (🟡, s.u.)

Befunde:
- **Keine 🔴.** Korrektheit, Sicherheit, DSGVO (nur Dummy-Daten), Architektur,
  Dateigrenzen (jeder Task exakt innerhalb seiner `Files`-Liste) sind sauber.
- 🟡 **Für Abschnitt 2 wichtig — die Anmerkung aus dem Task-2-Block trifft nicht
  in allen Fällen zu.** Gemessen mit einem echten Tiptap in jsdom: der
  `setEditable`-Effekt feuert beim Mount genau ein `onChange`, und der gemeldete
  Wert ist Tiptaps **normalisiertes** HTML. Für `''` → `<p></p>`, `Nur Text ohne
  Tag` → `<p>Nur Text…</p>`, `<ul><li>Punkt</li></ul>` →
  `<ul><li><p>Punkt</p></li></ul>`, `text-align: center` → `…center;` weicht er
  vom gespeicherten Wert ab. Der Verlaufskern fängt das dann **nicht** als
  „keine Änderung“ ab (`feldAenderungen` sieht einen echten Diff) — Task 4 würde
  beim Öffnen eines Dokuments Phantom-Schritte erzeugen. Empfehlung für
  Abschnitt 2: `onChange` aus dem Editor erst nach der ersten echten
  Nutzer-Interaktion in einen Verlaufsschritt überführen (z.B. Gate über
  `onFocus`/`art`), nicht ungeprüft jeden Aufruf. Kein Fehler im hier gelieferten
  Code — `index.tsx` ist unangetastet, nichts davon ist heute sichtbar.
- 🟡 `TiptapEditor.test.tsx:28-35` (Standardmodus-Regression) ist tautologisch:
  ohne `onChange.mockClear()` nach dem ersten `render` ist
  `expect(onChange).toHaveBeenCalled()` schon durch den Mount-Aufruf erfüllt.
  Belegt: mit Mutation bleibt der Test grün, mit einer zusätzlichen Zeile
  `onChange.mockClear()` wird er rot. Die Zusicherung selbst ist über
  `tiptapVerlauf.test.ts` abgedeckt, das Verhalten ist korrekt.
- 🟡 `SortableBlock.tsx` trägt `data-block-id`, aber kein Test hält das fest
  (für `SectionHeaderBlock` schon). Task 4 hängt an diesem DOM-Vertrag.
- 🟡 Der Chevron-Knopf in `VerlaufKnoepfe.tsx:230-235` baut
  `disabled:opacity-50 disabled:cursor-not-allowed` von Hand nach, statt über die
  `Button`-Basis zu kommen (Plan-Vorgabe). Optisch identisch.
- Geprüft und in Ordnung: beide Undo/Redo-Leisten (exportierte `TiptapToolbar`
  **und** die interne Kopie) prüfen `typeof editor.commands.undo === 'function'`;
  Verlaufskern (20er-Grenze, Bündelung inkl. Pause/Feldwechsel/nach Rückgängig,
  Redo-Stapel-Verwurf, feldgenaues Zurücknehmen, Referenzgleichheit bei
  Leer-Schritt); StrictMode (keine Seiteneffekte in State-Updatern, Listener
  sauber ab-/angemeldet); Tastatur-Hook (`preventDefault` nur nach bestandener
  Prüfung, alle modalen Dialoge des Editors tragen `aria-modal="true"`).

## Abschnitt 1 — Abnahme (Orchestrator)

Zeit: 2026-09-17
Stand nach Merge: `feature/dokument-verlauf` @ 02675b0f
Ampel: 🟢 abgenommen (Code-Review 🟡, keine 🔴)

- Task-Branches konfliktfrei gemergt (`9a263d93`, `f20c8739`, `f6316ba8`).
- **Design-Review wurde abgebrochen und zählt nicht zur Abnahme.** Begründung
  des Nutzers: Abschnitt 1 verändert für den Nutzer nichts Sichtbares (alles
  opt-in, `index.tsx` unangetastet) — dann gibt es auch nichts zu begutachten.
  Die Regression fängt die volle E2E-Suite in Abschnitt 2, die ohnehin läuft.
  Die Regel steht jetzt im Skill (`SKILL.md` Schritt 5.3 und
  `references/fallstricke.md`).
- **Fremder Stand hereingeholt:** PR #161 („Zahlungsziel speichern und vor
  langen Fristen nachfragen") wurde vom Nutzer direkt in
  `feature/dokument-verlauf` gemergt (Commit `02675b0f`, Fast-Forward, keine
  Konflikte). Er ändert `index.tsx` (`handleSave` schickt jetzt
  `zahlungszielTage`, neues Flag `zahlungszielGeaendert` analog zu
  `adresseGeaendert`), `helpers.ts`, `TextBlock.tsx`, `SummenFooter.tsx`, bringt
  die neue Komponente `ZahlungszielTageEingabe.tsx` und die Spec
  `e2e/dokument-editor-zahlungsziel.spec.ts` mit.
- **Neue Baseline für Abschnitt 2** (auf 02675b0f gemessen): lint grün · tsc
  grün · vitest **141 Dateien / 1609 Tests** grün. E2E unverändert erwartet
  (627 + die neue Zahlungsziel-Spec). Jeder Fehler ab hier ist neu.
- Offene 🟡 aus dem Code-Review, die Abschnitt 2 miterledigt: Phantom-Schritte
  durch das Mount-`onChange` (Tiptap normalisiert HTML), tautologischer
  Regressionstest in `TiptapEditor.test.tsx`, fehlender Test für
  `data-block-id` in `SortableBlock.tsx`, handgebaute `disabled`-Klassen am
  Chevron-Knopf.
