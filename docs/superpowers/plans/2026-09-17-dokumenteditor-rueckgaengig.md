# Plan: Rückgängig & Wiederholen im Dokumenteditor

Issue: #160
Feature-Branch: feature/dokument-verlauf
Kontext-Log: /Users/marvinkuhn/dev/wt/verlauf-integration/docs/superpowers/plans/2026-09-17-dokumenteditor-rueckgaengig-log.md
Spec: docs/superpowers/specs/2026-09-17-dokumenteditor-rueckgaengig.md

4 Tasks. Task 1–3 sind voneinander unabhängig, Task 4 braucht alle drei.
Nur Frontend PC (`react-pc-frontend/`), kein Backend, keine Datenbank, keine
Änderung an `react-zeiterfassung/`.

---

## Global Constraints

### Pflichtlektüre vor dem ersten Edit

1. `docs/agent instructions/docs/FRONTEND_UI.md` (jede `.ts`/`.tsx`-Änderung).
2. `docs/agent instructions/docs/TESTING_SECURITY.md` (jede Testdatei).
3. `.claude/skills/loese-problem/references/kriterien.md` (Prüfmaßstab des Reviews).
4. Design-Skill `handwerkerprogramm-design` aufrufen (Pflicht bei Frontend-Arbeit,
   sonst blockt der Hook mit `DESIGN-SKILL-GUARD`).
5. Diese Spec: `docs/superpowers/specs/2026-09-17-dokumenteditor-rueckgaengig.md`.

### Gate-Befehle (jeweils in `react-pc-frontend/`)

- `npx vitest run <eigene Testdateien>` — nur die eigenen, nie die ganze Suite.
- `npm run lint`
- Typecheck + Build ohne Artefakte im Repo:
  `npx tsc -b && npx vite build --outDir "$(mktemp -d)" --emptyOutDir`
  (**nicht** `npm run build` — das schreibt nach `../src/main/resources/static/`
  mit `emptyOutDir: false`, die gehashten Bundles kollidieren beim Merge.)
- Eigene Playwright-Spec auf eigenem Port: `E2E_PORT=<port> npx playwright test e2e/<spec>`
- Testläufe: Timeout des Shell-Werkzeugs auf 600000 ms, synchron, Ausgabe in eine
  Datei umleiten, **keine Pipe vor dem Exit-Code**.

**Baseline (gemessen auf `feature/dokument-verlauf`):** lint grün · vitest 133
Dateien / 1467 Tests grün · tsc grün · E2E 627/627 grün (drei Größen).
Abnahmeregel: **jeder Fehler ist neu** und gehört dir. Backend wird nicht
angefasst, keine Maven-Gates.

### Plattform & Umgebung

- macOS, zsh/bash. Kein PowerShell, keine Windows-Pfade.
- `graphify` gibt es nur im Haupt-Checkout:
  `/Users/marvinkuhn/dev/ERP-System-fuer-Handwerksbetriebe/graphify query|path|explain "…"`.
  Der Plan enthält die Recherche schon — graphify nur für einzelne Rückfragen,
  nicht für einen neuen Überblick.
- Im Haupt-Checkout liegt fremde, uncommittete Arbeit: **dort nichts schreiben.**

### Projektregeln, die hier greifen

- **DSGVO:** In Tests nur Dummy-Daten (`Max Mustermann`, `Musterweg 1`), keine
  echten Adressen/E-Mails, auch nicht in E2E-Stubs.
- **Farben:** slate für neutrale Bedienelemente, rose für Akzente. Keine neuen
  Farbfamilien. `disabled` kommt über die `Button`-Basis
  (`src/components/ui/button.tsx:28` hat bereits `disabled:opacity-50
  disabled:cursor-not-allowed`) — nicht selbst nachbauen.
- **Wording:** Handwerker-Sprache, keine Buchhalter-Begriffe. Verbindliche
  Wortliste siehe Task 4.
- **Icons:** Lucide (`Undo2`, `Redo2`, `ChevronDown`). Keine Emojis.
- **Lint-Fallen (React-Compiler-Regeln sind als *Fehler* aktiv):**
  `react-hooks/set-state-in-effect`, `react-hooks/refs`, `react-hooks/purity`,
  `react-hooks/immutability`. Für **neue** Dateien gilt: kein `setState` direkt
  im Effekt-Körper, kein `ref.current` während des Renders, kein `Date.now()`
  im Render. Gemessen: `src/components/document-editor/index.tsx` löst diese
  Regeln heute nicht aus (der Compiler steigt dort aus), neue Dateien schon.
  Wenn es sich in einer neuen Datei nicht vermeiden lässt: das Projektmuster
  `// eslint-disable-next-line react-hooks/set-state-in-effect` **mit
  Begründung** verwenden (Vorbild: `src/components/lock/useDatensatzLock.ts:417`).
- **`react-refresh/only-export-components` ist Fehler:** aus `.tsx`-Dateien
  keine Nicht-Komponenten exportieren. Reine Funktionen gehören in `.ts`-Dateien.
- **StrictMode:** `src/main.tsx` rendert in `<StrictMode>`. **Keine
  Seiteneffekte in State-Updatern** (kein Toast, kein Verlaufs-Schritt, kein
  Logging in `setX(prev => …)`).

### Gemeinsame DOM-Verträge (Task 2 setzt sie, Task 4 liest sie)

| Attribut | Wo | Zweck |
|---|---|---|
| `data-block-id="<block.id>"` | Äußerer `div` in `SortableBlock.tsx` (Normalzweig) und am Kind-Wrapper in `SectionHeaderBlock.tsx` (`renderChild`) | „Stelle zeigen“ findet die Karte |
| `data-verlauf-feld="title\|quantity\|unit\|price\|description\|content\|sectionLabel"` | Am jeweiligen `input` bzw. am Tiptap-Wrapper-`div` | Cursor nach Rückgängig ins richtige Feld |
| `data-eigenes-rueckgaengig="true"` | Felder, die erst beim Übernehmen/Verlassen gelten: Bauabschnitts-Name (`SectionHeaderBlock`), Auswahl-Name (`AlternativGruppeBox`), Rechnungsadresse-Textarea (`index.tsx`) | Strg+Z bleibt dort das normale Rückgängig des Feldes |

### Testen

- Coding-Agenten fahren **nie** die komplette Suite — nur die eigenen Dateien,
  dazu lint und tsc/build.
- Jeder Task mit sichtbarer Änderung liefert eine Playwright-Spec für genau
  seinen Ablauf. Tasks ohne nutzerseitig erreichbaren Ablauf vermerken die
  Begründung im Kontext-Log (in diesem Plan pro Task schon vorformuliert).
- Vorbestehende Fehler aus der Baseline nicht reparieren, nicht überspringen,
  nicht deaktivieren.

### Abstimmung mit der parallelen Session

In einer anderen Session läuft „Zahlungsziel im Dokumenteditor mitspeichern“
(Branch `claude/competent-satoshi-5be572`). Sie fasst `handleSave` und die
Ungespeichert-Signatur in `index.tsx` an. Deshalb: Der Signatur-Umbau läuft hier
über **eine** Hilfsfunktion (`baueDokumentSignatur`, Task 1) statt über fünf
Einzeländerungen — das hält den Konflikt auf fünf Einzeiler. Der Orchestrator
gleicht vor dem PR mit `main` ab.

---

## Entscheidungen zu den fünf offenen Punkten der Spec

1. **Signaturen:** Kern in `src/lib/aenderungsVerlauf.ts` (generisch, React-frei),
   Bindung in `document-editor/useDokumentVerlauf.ts`, Tastatur in
   `document-editor/useVerlaufTastatur.ts`. Ein Schritt merkt sich *nur* die
   Top-Level-Felder, die er verändert hat (`blocks`, `globalRabatt`, `datum`,
   `zahlungsziel`, `rechnungsadresse`, `balkenAnzeigen`) — exakte Typen in Task 1.
   Die zentrale Änderungsfunktion liest den Stand **synchron aus Refs**, rechnet,
   schreibt Ref **und** State und legt danach den Schritt ab (Task 4). Alle
   Prototypen sind bereits gegen `tsc --noEmit` und `eslint` geprüft (beide grün).
2. **Wortliste:** vollständige Tabelle in Task 4, Abschnitt „Wortliste“. Der
   Positionstitel erscheint **nicht** im Schrittnamen (zu lang für Tooltip und
   Liste; welche Position gemeint war, zeigt die Hervorhebung).
3. **Tippen vs. Formatierung:** `src/components/tiptapVerlauf.ts` klassifiziert
   anhand der ProseMirror-Transaktion (nur `ReplaceStep` mit reinem Text/Absatz/
   Zeilenumbruch/Listenpunkt = `'tippen'`; Paste-/Drop-/Cut-Meta, angehängte
   Transaktionen, Mark-/Attr-Schritte, Bilder = `'sonstiges'`). Cursor nach
   Rückgängig: `Fragment.findDiffStart` auf altem/neuem Dokument, dann
   `TextSelection.near` — Details in Task 2.
4. **`TiptapEditor`-Sync:** **opt-in** über die neue Prop `verlaufsModus`
   (Standard `false`). Ohne die Prop ändert sich für die sechs anderen Seiten
   + `DocumentBuilder` nichts. Gemessen: heute meldet eine externe
   `value`-Änderung `onChange` — das bleibt im Standardmodus so (Regressionstest
   in Task 2).
5. **Task-Schnitt:** `index.tsx` liegt in genau **einem** Task (Task 4, letzte
   Runde). Tasks 1–3 legen alles an, was Task 4 braucht, und sind untereinander
   unabhängig; ihre neuen Props sind optional und abwärtskompatibel, damit das
   unveränderte `index.tsx` nach Runde 1 weiter baut **und** unverändert läuft.

---

## Abschnittsschnitt

**Ergebnis: 2 Abschnitte.** Abschnitt 1 = Task 1, 2, 3 (parallel, je
`Consumes: nichts`). Abschnitt 2 = Task 4 allein (konsumiert alle drei).

### Datei → Task (alle Pfade relativ zu `react-pc-frontend/`)

| Datei | Task |
|---|---|
| `src/lib/aenderungsVerlauf.ts` (+ `.test.ts`) | 1 |
| `src/components/document-editor/useDokumentVerlauf.ts` (+ `.test.ts`) | 1 |
| `src/components/document-editor/useVerlaufTastatur.ts` (+ `.test.ts`) | 1 |
| `src/components/document-editor/helpers.ts` (+ `.test.ts`, ändern) | 1 |
| `src/components/tiptapVerlauf.ts` (+ `.test.ts`) | 2 |
| `src/components/TiptapEditor.tsx` (ändern) + `.test.tsx` (neu) | 2 |
| `src/components/document-editor/TextBlock.tsx` (+ `.test.tsx`, ändern) | 2 |
| `src/components/document-editor/ServiceBlock.tsx` (+ `.test.tsx`, ändern) | 2 |
| `src/components/document-editor/SectionHeaderBlock.tsx` (+ `.test.tsx`, ändern) | 2 |
| `src/components/document-editor/SortableBlock.tsx` (ändern) | 2 |
| `src/components/document-editor/AlternativGruppeBox.tsx` (ändern) | 2 |
| `src/components/document-editor/VerlaufKnoepfe.tsx` (+ `.test.tsx`) | 3 |
| `src/components/document-editor/DocumentEditorHeader.tsx` (ändern) + `.test.tsx` (neu) | 3 |
| `src/components/document-editor/index.tsx` (+ `.test.tsx`, ändern) | 4 |
| `src/index.css` (ändern) | 4 |
| `e2e/hilfen/dokument-editor.ts` (ändern) | 4 |
| `e2e/dokument-editor-rueckgaengig.spec.ts` (neu) | 4 |

**Prüfung 1 — Datei-Disjunktheit in Abschnitt 1:** 20 Dateien über Task 1–3
(Zeilen oben), keine kommt in zwei Zeilen vor. Insbesondere:
`helpers.ts`/`helpers.test.ts` gehören ausschließlich Task 1; `TiptapEditor.tsx`
und alle sechs Block-Dateien (`TextBlock`, `ServiceBlock`,
`SectionHeaderBlock` je mit Test, `SortableBlock`, `AlternativGruppeBox`)
ausschließlich Task 2; `DocumentEditorHeader.tsx` ausschließlich Task 3.
Task 4 (Abschnitt 2) fasst mit `index.tsx`/`index.test.tsx`/`index.css`/den
zwei E2E-Dateien fünf weitere Dateien an, die in keinem Task aus Abschnitt 1
vorkommen.

**Prüfung 2 — Consumes erfüllt:** Task 1, 2 und 3 haben je `Consumes: nichts`
→ alle drei starten sofort parallel. Task 4 konsumiert `useDokumentVerlauf`/
`baueDokumentSignatur` (Task 1), `verlaufsModus`/`TiptapAenderungsArt`/die
drei `data-*`-Attribute (Task 2) und `VerlaufKnoepfe` über die Header-Prop
`verlauf` (Task 3) — startet also erst, wenn Abschnitt 1 komplett fertig
**und** review-grün ist.

**Prüfung 3 — jeder Abschnitt für sich baubar und lauffähig:** Abschnitt 1
lässt `index.tsx` unangetastet. Alle neuen Props sind optional und
abwärtskompatibel: `TiptapEditorProps.verlaufsModus` (Default `false`,
Sync-Effekt im Standardmodus unverändert), die erweiterten
`onChange`/`onUpdate`-Signaturen (eine bestehende Funktion mit weniger
Parametern bleibt auf einen Typ mit mehr Parametern zuweisbar —
TypeScript-Standardverhalten) und `DocumentEditorHeader.verlauf` (Default
`undefined`, Header rendert dann exakt wie heute). Task 1s neue Dateien
werden von niemandem importiert, solange `index.tsx` unangetastet bleibt —
reine Additive. Abschnitt 1 baut, lintet und läuft damit identisch zur
Baseline (627/627 E2E unverändert, siehe die „Keine Playwright-Spec“-
Begründungen in Task 1–3). Abschnitt 2 (Task 4) verdrahtet alles und liefert
die einzige neue Playwright-Spec dieses Plans.

---

## Abschnitt 1 — Task 1–3 parallel (dateidisjunkt, ohne Abhängigkeiten untereinander)

### Task 1 — Verlaufskern, Editor-Hook, Tastatur-Hook, Signatur-Helfer

- Branch: `verlauf/task-1-kern`
- Worktree: `/Users/marvinkuhn/dev/wt/verlauf-task-1`
- Files:
  - `react-pc-frontend/src/lib/aenderungsVerlauf.ts` (neu)
  - `react-pc-frontend/src/lib/aenderungsVerlauf.test.ts` (neu)
  - `react-pc-frontend/src/components/document-editor/useDokumentVerlauf.ts` (neu)
  - `react-pc-frontend/src/components/document-editor/useDokumentVerlauf.test.ts` (neu)
  - `react-pc-frontend/src/components/document-editor/useVerlaufTastatur.ts` (neu)
  - `react-pc-frontend/src/components/document-editor/useVerlaufTastatur.test.ts` (neu)
  - `react-pc-frontend/src/components/document-editor/helpers.ts` (ändern, ans Ende anhängen)
  - `react-pc-frontend/src/components/document-editor/helpers.test.ts` (ändern, neuer `describe`-Block)
- Vorbild:
  - Hook-Aufbau + Refs-Spiegel ohne Dep-Array: `src/hooks/useIdleTimer.ts:64-75`
    („Callbacks in Refs spiegeln … in einem Effekt OHNE Dep-Array“).
  - Hook-Test mit `renderHook`/Fake-Timers: `src/hooks/useIdleTimer.test.ts` (ganze Datei).
  - Reine Logik + Unit-Test im Editor-Ordner: `document-editor/blockOps.ts` /
    `blockOps.test.ts`.
- Interfaces:
  - Produces (`src/lib/aenderungsVerlauf.ts`):
    ```ts
    export interface FeldAenderung<Wert> { vorher: Wert; nachher: Wert; }
    export type Feldaenderungen<Stand> = { [K in keyof Stand]?: FeldAenderung<Stand[K]> };

    export interface Schritt<Stand, Ziel> {
        id: number;
        bezeichnung: string;
        aenderungen: Feldaenderungen<Stand>;
        buendelSchluessel: string | null;
        ziel: Ziel | null;
        /** ms (Date.now()) der letzten Eingabe dieses Schritts — Grundlage der Bündel-Pause. */
        zeitpunkt: number;
    }

    export interface Verlauf<Stand, Ziel> {
        /** Ältester zuerst, neuester zuletzt. Maximal MAX_SCHRITTE Einträge. */
        schritte: readonly Schritt<Stand, Ziel>[];
        /** Zurückgenommene Schritte; der zuletzt zurückgenommene steht zuletzt. */
        wiederholbar: readonly Schritt<Stand, Ziel>[];
        naechsteId: number;
        /** false direkt nach Rückgängig/Wiederholen/Leeren: der nächste Tipp-Schritt bündelt nicht. */
        buendelnErlaubt: boolean;
    }

    export const MAX_SCHRITTE = 20;
    export const BUENDEL_PAUSE_MS = 2000;

    export function leererVerlauf<Stand, Ziel>(): Verlauf<Stand, Ziel>;
    export function tiefGleich(a: unknown, b: unknown): boolean;
    export function feldAenderungen<Stand extends object>(vorher: Stand, nachher: Partial<Stand>): Feldaenderungen<Stand>;
    export function istLeer<Stand>(aenderungen: Feldaenderungen<Stand>): boolean;
    export function nachherWerte<Stand>(aenderungen: Feldaenderungen<Stand>): Partial<Stand>;

    export interface SchrittEntwurf<Stand, Ziel> {
        bezeichnung: string;
        aenderungen: Feldaenderungen<Stand>;
        buendelSchluessel?: string | null;
        ziel?: Ziel | null;
    }
    export function schrittAufnehmen<Stand, Ziel>(
        verlauf: Verlauf<Stand, Ziel>, entwurf: SchrittEntwurf<Stand, Ziel>, jetzt: number,
    ): Verlauf<Stand, Ziel>;

    export interface VerlaufsSprung<Stand, Ziel> {
        verlauf: Verlauf<Stand, Ziel>;
        /** Zusammengeführte Werte, die der Aufrufer setzen muss. */
        werte: Partial<Stand>;
        /** Die bewegten Schritte in Ausführungsreihenfolge (Rückgängig: neuester zuerst). */
        schritte: readonly Schritt<Stand, Ziel>[];
    }
    export function rueckgaengig<Stand, Ziel>(verlauf: Verlauf<Stand, Ziel>, anzahl?: number): VerlaufsSprung<Stand, Ziel> | null;
    export function wiederholen<Stand, Ziel>(verlauf: Verlauf<Stand, Ziel>, anzahl?: number): VerlaufsSprung<Stand, Ziel> | null;
    export function verlaufGeleert<Stand, Ziel>(verlauf: Verlauf<Stand, Ziel>): Verlauf<Stand, Ziel>;
    ```
  - Produces (`document-editor/useDokumentVerlauf.ts`):
    ```ts
    export interface DokumentStand {
        blocks: DocBlock[];
        globalRabatt: number;
        datum: string;
        /** Effektive Tage (kontextDaten.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE) — nie undefined. */
        zahlungsziel: number;
        /** Nie undefined; leer = ''. */
        rechnungsadresse: string;
        balkenAnzeigen: boolean;
    }
    export interface VerlaufsZiel {
        blockId: string;
        /** Gesetzt, wenn der Block ein Kind eines Bauabschnitts ist. */
        sectionId?: string;
        feld?: 'title' | 'quantity' | 'unit' | 'price' | 'description' | 'content' | 'sectionLabel';
    }
    export type DokumentSchritt = Schritt<DokumentStand, VerlaufsZiel>;
    export interface VerlaufsMeldung { bezeichnung: string; anzahl: number; ziel: VerlaufsZiel | null; }
    export interface AenderungsAuftrag {
        bezeichnung: string;
        berechne: (stand: DokumentStand) => Partial<DokumentStand>;
        buendelSchluessel?: string | null;
        ziel?: VerlaufsZiel | null;
    }
    export interface DokumentVerlaufOptionen {
        leseStand: () => DokumentStand;
        schreibeStand: (werte: Partial<DokumentStand>) => void;
        gesperrt: boolean;
    }
    export interface DokumentVerlauf {
        /** true = der Stand hat sich geändert und ein Schritt wurde geschrieben. */
        aendern: (auftrag: AenderungsAuftrag) => boolean;
        rueckgaengig: (anzahl?: number) => VerlaufsMeldung | null;
        wiederholen: () => VerlaufsMeldung | null;
        leeren: () => void;
        kannRueckgaengig: boolean;
        kannWiederholen: boolean;
        /** Neuester Schritt zuerst — genau die Reihenfolge der Dropdown-Liste. */
        schritte: { id: number; bezeichnung: string }[];
        naechstesRueckgaengig: string | null;
        naechstesWiederholen: string | null;
    }
    export function useDokumentVerlauf(optionen: DokumentVerlaufOptionen): DokumentVerlauf;
    ```
  - Produces (`document-editor/useVerlaufTastatur.ts`):
    ```ts
    export interface VerlaufTastaturOptionen {
        /** false = gesperrtes Dokument ODER ein Dialog des Editors ist offen. */
        aktiv: boolean;
        wurzelRef: RefObject<HTMLElement | null>;
        onRueckgaengig: () => void;
        onWiederholen: () => void;
    }
    export function darfVerlaufTasteGreifen(ziel: EventTarget | null, wurzel: HTMLElement | null): boolean;
    export function useVerlaufTastatur(optionen: VerlaufTastaturOptionen): void;
    ```
  - Produces (`document-editor/helpers.ts`):
    ```ts
    export interface DokumentSignaturDaten {
        blocks: DocBlock[]; datum: string; betreff: string;
        dokumentTyp: string; globalRabatt: number; balkenAnzeigen: boolean;
    }
    /** Vergleichswert für "Ungespeichert": CLOSURE-Marker raus, Rabatt und Balken rein. */
    export function baueDokumentSignatur(daten: DokumentSignaturDaten): string;
    ```
  - Consumes: nichts.
- Steps:
  - [ ] **Roter Test zuerst:** `src/lib/aenderungsVerlauf.test.ts` anlegen mit einem
        Mini-Stand `type Probe = { text: string; zahl: number }` und Fällen:
        (a) `schrittAufnehmen` mit `feldAenderungen(stand, {text: 'a'})` legt einen
        Schritt an; (b) unveränderter Wert ⇒ `istLeer(aenderungen)` ⇒ `schrittAufnehmen`
        gibt **dieselbe Verlauf-Referenz** zurück; (c) 21 Schritte ⇒ `schritte.length === 20`,
        der älteste ist raus; (d) Bündelung: gleicher `buendelSchluessel`, `jetzt`-Abstand
        1000 ms ⇒ ein Schritt mit `vorher` des ersten und `nachher` des letzten;
        (e) Abstand 2500 ms ⇒ zwei Schritte; (f) anderer Schlüssel ⇒ zwei Schritte;
        (g) `buendelSchluessel: null` ⇒ nie bündeln; (h) nach `rueckgaengig` bündelt
        der nächste Schritt mit gleichem Schlüssel **nicht** (`buendelnErlaubt === false`);
        (i) Bündel, das per Saldo nichts ändert (tippen + zurücklöschen) ⇒ Schritt fällt weg;
        (j) `rueckgaengig` liefert `werte` mit den `vorher`-Werten und schiebt den Schritt
        nach `wiederholbar`; (k) `wiederholen` kehrt das um; (l) neue Änderung nach
        `rueckgaengig` leert `wiederholbar`; (m) `rueckgaengig(3)`: die Werte des
        **ältesten** zurückgenommenen Schritts gewinnen je Feld; (n) feldgenau: ein
        Schritt, der nur `text` geändert hat, liefert in `werte` **kein** `zahl`;
        (o) `verlaufGeleert` gibt bei leerem Verlauf dieselbe Referenz zurück;
        (p) `tiefGleich`: gleiche Objekte/Arrays true, unterschiedliche Länge false,
        Referenzgleichheit kurzschließt.
  - [ ] `src/lib/aenderungsVerlauf.ts` implementieren (rein, keine React-Importe).
        `tiefGleich` beginnt mit `if (a === b) return true;` — das ist der
        Performance-Kern: unveränderte Blöcke behalten in `prev.map(...)` ihre
        Referenz, verglichen wird nur der geänderte Block.
        `schrittAufnehmen`: bündelt nur, wenn `entwurf.buendelSchluessel` gesetzt ist,
        `verlauf.buendelnErlaubt` true ist, der letzte Schritt denselben Schlüssel hat
        und `jetzt - letzter.zeitpunkt < BUENDEL_PAUSE_MS`. Beim Zusammenführen gilt je
        Feld `vorher` des alten, `nachher` des neuen; Felder mit `tiefGleich(vorher,nachher)`
        fallen aus dem Schritt, und bleibt nichts übrig, fällt der Schritt ganz weg.
        Jeder aufgenommene Schritt leert `wiederholbar` und setzt `buendelnErlaubt = true`.
        `rueckgaengig`/`wiederholen` setzen `buendelnErlaubt = false`.
  - [ ] **Roter Test:** `document-editor/useDokumentVerlauf.test.ts` mit `renderHook`
        (Vorbild `useIdleTimer.test.ts`) und einem Fake-Stand in einem `let`-Objekt:
        `leseStand: () => stand`, `schreibeStand: w => { stand = { ...stand, ...w }; }`.
        Fälle: `aendern` schreibt den Stand und meldet `true`; unveränderte Berechnung
        meldet `false` und erzeugt keinen Schritt; `gesperrt: true` ⇒ `aendern` false,
        `rueckgaengig` null; Bündelung über `vi.useFakeTimers()` +
        `vi.advanceTimersByTime(...)` (Vitests Fake-Timers stellen auch `Date.now`);
        `rueckgaengig()` setzt nur die betroffenen Felder zurück (ein zwischenzeitlich
        von außen geändertes `datum` bleibt stehen — das ist die Kernaussage
        „feldgenau“); `rueckgaengig(3)` liefert `{ anzahl: 3, bezeichnung: '3 Schritte' }`;
        `wiederholen()` nach `rueckgaengig()`; `leeren()` setzt `kannRueckgaengig` auf false.
  - [ ] `useDokumentVerlauf.ts` implementieren. Muster (gegen `tsc` und Lint geprüft):
        `useState` für den Verlauf **plus** `verlaufRef` als synchroner Spiegel; jeder
        Schreibvorgang läuft über ein `setzeVerlauf(neu)`, das erst das Ref und dann den
        State setzt. `leseStand`/`schreibeStand`/`gesperrt` in Refs spiegeln, die ein
        `useEffect` **ohne Dep-Array** aktualisiert (Vorbild `useIdleTimer.ts:64-75`).
        `aendern` ruft `auftrag.berechne(stand)`, bildet `feldAenderungen`, steigt bei
        `istLeer` ohne State-Änderung aus, schreibt sonst `nachherWerte(...)` über
        `schreibeStand` und danach den Schritt mit `Date.now()`. Kein `setState` im
        Effekt-Körper, kein `ref.current` im Render.
  - [ ] **Roter Test:** `useVerlaufTastatur.test.ts`: Hook in `renderHook` mit einer
        selbst gebauten Wurzel (`document.createElement('div')` + `document.body.appendChild`)
        montieren und `window.dispatchEvent(new KeyboardEvent('keydown', {...}))` bzw.
        `element.dispatchEvent(...)` (mit `bubbles: true`) feuern. Fälle: `ctrlKey+z` ⇒
        `onRueckgaengig`; `metaKey+z` ⇒ dito; `ctrlKey+y` und `ctrlKey+shiftKey+z` und
        `metaKey+shiftKey+z` ⇒ `onWiederholen`; `altKey` zusätzlich ⇒ nichts; `aktiv: false`
        ⇒ nichts; Ziel außerhalb der Wurzel ⇒ nichts; Ziel innerhalb, aber mit Vorfahr
        `[data-eigenes-rueckgaengig]` ⇒ nichts; Ziel `document.body` ⇒ greift (nach einem
        Löschen hat kein Element mehr den Fokus — genau der Hauptfall); ein Element mit
        `aria-modal="true"` irgendwo im Dokument ⇒ nichts; `preventDefault` wurde
        aufgerufen (Event-Objekt vorher bauen und `defaultPrevented` prüfen);
        Unmount meldet den Listener ab.
  - [ ] `useVerlaufTastatur.ts` implementieren: Listener auf `window` mit
        **Capture** (`addEventListener('keydown', handler, true)`), damit er vor
        ProseMirror und vor dem Browser-eigenen Rückgängig greift; bei Zuständigkeit
        `preventDefault()` **und** `stopPropagation()`. Tastenerkennung über
        `e.key.toLowerCase()` (nicht `e.code` — auf QWERTZ liefert `key` den logischen
        Buchstaben, genau wie Word es meint). `darfVerlaufTasteGreifen` exportieren, damit
        der Test sie direkt prüfen kann.
  - [ ] **Roter Test:** in `document-editor/helpers.test.ts` einen `describe('baueDokumentSignatur')`
        ergänzen: CLOSURE-Marker fliegt raus (`{ id: CLOSURE_BLOCK_ID }` und
        `{ type: 'CLOSURE' }`), gleiche Daten ⇒ gleiche Signatur, geänderter
        `globalRabatt` ⇒ andere Signatur, geändertes `balkenAnzeigen` ⇒ andere Signatur.
  - [ ] `baueDokumentSignatur` in `helpers.ts` ergänzen (Import `CLOSURE_BLOCK_ID` aus
        `./blockOps` — zyklusfrei, `blockOps.ts` importiert nur `./types`). Reihenfolge
        der Schlüssel im `JSON.stringify` exakt wie in der Aufzählung der Signatur
        festhalten, damit die Signatur stabil ist.
  - [ ] Gates: `npx vitest run src/lib/aenderungsVerlauf.test.ts src/components/document-editor/useDokumentVerlauf.test.ts src/components/document-editor/useVerlaufTastatur.test.ts src/components/document-editor/helpers.test.ts`,
        `npm run lint`, `npx tsc -b && npx vite build --outDir "$(mktemp -d)" --emptyOutDir`.
  - [ ] **Keine Playwright-Spec.** Begründung fürs Kontext-Log: reine Logik ohne
        nutzerseitig erreichbaren Ablauf — die Module sind erst nach Task 4 in der
        Oberfläche verdrahtet, ihr Verhalten deckt die Haupt-Spec von Task 4 ab.

---

### Task 2 — Tiptap-Verlaufsmodus, Änderungsart und Block-Komponenten

- Branch: `verlauf/task-2-tiptap`
- Worktree: `/Users/marvinkuhn/dev/wt/verlauf-task-2`
- Files:
  - `react-pc-frontend/src/components/tiptapVerlauf.ts` (neu)
  - `react-pc-frontend/src/components/tiptapVerlauf.test.ts` (neu)
  - `react-pc-frontend/src/components/TiptapEditor.tsx` (ändern)
  - `react-pc-frontend/src/components/TiptapEditor.test.tsx` (neu — es gibt heute keine)
  - `react-pc-frontend/src/components/document-editor/TextBlock.tsx` (ändern)
  - `react-pc-frontend/src/components/document-editor/TextBlock.test.tsx` (ändern)
  - `react-pc-frontend/src/components/document-editor/ServiceBlock.tsx` (ändern)
  - `react-pc-frontend/src/components/document-editor/ServiceBlock.test.tsx` (ändern)
  - `react-pc-frontend/src/components/document-editor/SectionHeaderBlock.tsx` (ändern)
  - `react-pc-frontend/src/components/document-editor/SectionHeaderBlock.test.tsx` (ändern)
  - `react-pc-frontend/src/components/document-editor/SortableBlock.tsx` (ändern)
  - `react-pc-frontend/src/components/document-editor/AlternativGruppeBox.tsx` (ändern)
- Vorbild:
  - `instanceof ReplaceStep` mit Import aus `@tiptap/pm/transform`:
    `node_modules/@tiptap/core/src/helpers/selectionToInsertionEnd.ts:3` und `:15`.
  - Externe Wert-Synchronisation, die geändert wird: `TiptapEditor.tsx:666-683`.
  - Pfeil-Knöpfe, die ausgeblendet werden: `TiptapEditor.tsx:383-397` (exportierte
    `TiptapToolbar`) und `TiptapEditor.tsx:731-745` (interne Kopie derselben Leiste).
  - Test mit gemocktem Tiptap: `document-editor/TextBlock.test.tsx:15-17`.
- Interfaces:
  - Produces (`src/components/tiptapVerlauf.ts`):
    ```ts
    import type { Editor } from '@tiptap/core';
    import type { Transaction } from '@tiptap/pm/state';

    export type TiptapAenderungsArt = 'tippen' | 'sonstiges';

    /** Tippen = nur Text-/Absatz-Ersetzungen ohne Paste/Drop/Cut und ohne angehängte Transaktionen. */
    export function aenderungsArtVon(
        transaction: Transaction,
        appendedTransactions: readonly Transaction[],
    ): TiptapAenderungsArt;

    /** Übernimmt einen von außen gesetzten Wert. Im Verlaufsmodus ohne Update-Meldung
     *  und mit Cursor an der ersten abweichenden Stelle. */
    export function setzeInhaltVonAussen(editor: Editor, wert: string, verlaufsModus: boolean): void;
    ```
  - Produces (`TiptapEditor.tsx`):
    ```ts
    interface TiptapEditorProps {
        value: string;
        /** `art` ist nur im Verlaufsmodus aussagekräftig; im Standardmodus immer 'sonstiges'. */
        onChange: (value: string, art: TiptapAenderungsArt) => void;
        hideToolbar?: boolean;
        compactMode?: boolean;
        readOnly?: boolean;
        onFocus?: () => void;
        onEditorReady?: (editor: ReturnType<typeof useEditor>) => void;
        /** Standard false. true = eigener Tiptap-Verlauf aus, externe Werte ohne
         *  Rückmeldung, Cursor an die geänderte Stelle. Wird beim Erzeugen des
         *  Editors ausgewertet; jede rufende Stelle setzt sie konstant. */
        verlaufsModus?: boolean;
    }
    ```
  - Produces (Block-Komponenten, jeweils **optional und abwärtskompatibel**):
    ```ts
    // TextBlock.tsx
    onUpdate: (id: string, updates: Partial<DocBlock>, art?: TiptapAenderungsArt) => void;
    verlaufsModus?: boolean;
    // ServiceBlock.tsx: dieselben beiden
    // SectionHeaderBlock.tsx
    onUpdate: (id: string, updates: Partial<DocBlock>, art?: TiptapAenderungsArt) => void;
    onUpdateChild: (sectionId: string, childId: string, updates: Partial<DocBlock>, art?: TiptapAenderungsArt) => void;
    verlaufsModus?: boolean;
    ```
    Eine 2-Parameter-Funktion aus dem heutigen `index.tsx` bleibt auf diese Typen
    zuweisbar — Runde 1 baut und läuft ohne Änderung an `index.tsx`.
  - Consumes: nichts.
- Steps:
  - [ ] **Roter Test zuerst:** `src/components/tiptapVerlauf.test.ts`. Kopflosen Editor
        bauen (läuft in jsdom, verifiziert):
        ```ts
        const el = document.createElement('div'); document.body.appendChild(el);
        const editor = new Editor({ element: el, extensions: [StarterKit.configure({ undoRedo: false })], content: '<p>Hallo</p>' });
        const arten: TiptapAenderungsArt[] = [];
        editor.on('update', ({ transaction, appendedTransactions }) => arten.push(aenderungsArtVon(transaction, appendedTransactions)));
        ```
        Fälle: `editor.view.dispatch(editor.state.tr.insertText('x', 6))` ⇒ `'tippen'`;
        `editor.commands.splitBlock()` ⇒ `'tippen'`; Löschen per
        `editor.commands.deleteRange({from, to})` ⇒ `'tippen'`;
        `editor.chain().setTextSelection({from:1,to:3}).toggleBold().run()` ⇒ `'sonstiges'`
        (AddMarkStep); Einfügen per Zwischenablage simulieren mit
        `editor.view.dispatch(editor.state.tr.insertText('P').setMeta('paste', true).setMeta('uiEvent', 'paste'))`
        ⇒ `'sonstiges'`; Bild über `insertContent({ type: 'image', attrs: { src: 'data:image/png;base64,iVBORw0KGgo=' } })`
        (Image-Extension mitladen) ⇒ `'sonstiges'`. Dazu `setzeInhaltVonAussen`:
        im Verlaufsmodus feuert **kein** `update` und `editor.state.selection.from`
        steht an der ersten abweichenden Stelle; ohne Verlaufsmodus feuert `update`.
  - [ ] `tiptapVerlauf.ts` implementieren.
        `aenderungsArtVon`: `'sonstiges'`, wenn `transaction.getMeta('paste')` oder
        `transaction.getMeta('uiEvent')` gesetzt ist (ProseMirror setzt `uiEvent` auf
        `paste`/`drop`/`cut`), wenn eine angehängte Transaktion `docChanged` ist
        (Eingaberegeln wie „- “ → Liste), wenn keine Schritte vorliegen oder wenn
        ein Schritt **kein** `ReplaceStep` ist. Sonst pro `ReplaceStep` prüfen, ob
        `step.slice` ausschließlich Knoten der Typen `text`, `paragraph`, `hardBreak`,
        `listItem` enthält (rekursiv über `slice.content.descendants`) — dann `'tippen'`.
        `setzeInhaltVonAussen`: ohne Verlaufsmodus wie heute
        `editor.commands.setContent(wert)`. Mit Verlaufsmodus:
        ```ts
        const alterInhalt = editor.state.doc.content;
        editor.commands.setContent(wert, { emitUpdate: false });
        const diff = alterInhalt.findDiffStart(editor.state.doc.content);
        if (diff == null) return;
        const ziel = Math.min(Math.max(diff, 0), editor.state.doc.content.size);
        editor.view.dispatch(editor.state.tr.setSelection(TextSelection.near(editor.state.doc.resolve(ziel))));
        ```
        (`TextSelection.near` statt `setTextSelection`, weil die Diff-Stelle auch auf
        einer Knotengrenze liegen kann. Die reine Auswahl-Transaktion ändert das
        Dokument nicht und löst deshalb kein `update` aus.)
  - [ ] **Roter Test:** `src/components/TiptapEditor.test.tsx` (neue Datei, echtes
        Tiptap in jsdom — verifiziert lauffähig, ~200 ms). Fälle:
        (a) **Regressionsschutz Standardmodus:** Rerender mit geändertem `value` ⇒
        `onChange` wird aufgerufen (heutiges Verhalten der sechs anderen Seiten);
        (b) Standardmodus + `hideToolbar={false}` ⇒ `getByTitle('Rückgängig (Ctrl+Z)')`
        existiert; (c) `verlaufsModus` ⇒ Rerender mit geändertem `value` ruft `onChange`
        **nicht** auf; (d) `verlaufsModus` ⇒ der Editor hat keinen eigenen Verlauf:
        ```ts
        const pm = container.querySelector('.ProseMirror') as HTMLElement & { editor: Editor };
        expect(typeof pm.editor.commands.undo).toBe('undefined');
        ```
        (Tiptap hängt die Instanz selbst ans DOM, `Editor.ts:626`);
        (e) `verlaufsModus` + `hideToolbar={false}` ⇒ keine Pfeil-Knöpfe, kein Absturz;
        (f) `<TiptapToolbar editor={editorOhneVerlauf} />` rendert ohne Absturz und
        ohne Pfeile (heute würde `editor.can().undo()` eine TypeError werfen);
        (g) `verlaufsModus` ⇒ `onChange` meldet `'tippen'` bei
        `pm.editor.commands.insertContent('x')` und `'sonstiges'` bei
        `pm.editor.chain().selectAll().toggleBold().run()`.
  - [ ] `TiptapEditor.tsx` ändern:
        1. `verlaufsModus?: boolean` in `TiptapEditorProps` aufnehmen (Default `false`)
           und `onChange` auf `(value: string, art: TiptapAenderungsArt) => void` erweitern.
        2. In `useEditor` (`:571`) `StarterKit.configure({ …, ...(verlaufsModus ? { undoRedo: false } : {}) })`.
        3. `onUpdate` (`:603`) auf
           `onUpdate: ({ editor: ed, transaction, appendedTransactions }) => onChange(ed.getHTML(), verlaufsModus ? aenderungsArtVon(transaction, appendedTransactions) : 'sonstiges')`.
        4. Den Sync-Effekt (`:668-683`) auf `setzeInhaltVonAussen(editor, value, !!verlaufsModus)`
           umstellen, beide Zweige (Erstsync und Folgesync), `verlaufsModus` in die Dep-Liste.
        5. **Pfeil-Knöpfe:** in `TiptapToolbar` (`:384-397`) und in der internen
           Leiste (`:731-745`) die beiden `ToolbarButton`s samt folgendem Trennstrich
           nur rendern, wenn der Editor überhaupt einen eigenen Verlauf hat:
           `const hatEigenenVerlauf = typeof editor.commands.undo === 'function';`
           Das ist zwingend: ohne diese Prüfung stürzt die globale Leiste des
           Dokumenteditors ab, sobald ein Verlaufsmodus-Editor aktiv ist
           (`editor.can().undo()` existiert dann nicht). Den Tippfehler
           „Rükgängig (Ctrl+Z)“ in `TiptapToolbar` **nicht** anfassen (Nicht-Ziel der Spec).
           Geprüft: kein Test im Projekt sichert diese Tooltips zu
           (`grep -rn "Rükgängig\|Rückgängig (Ctrl" src e2e` findet nur
           `TiptapEditor.tsx` selbst und die unabhängige `BeitragRichtextEditor`).
           Vor dem Commit erneut greppen.
  - [ ] **Roter Test:** `TextBlock.test.tsx` erweitern: der gemockte `TiptapEditor`
        ruft `onChange('<p>neu</p>', 'sonstiges')` und die Komponente reicht die Art an
        `onUpdate` durch (`expect(onUpdate).toHaveBeenCalledWith('t1', { content: '<p>neu</p>' }, 'sonstiges')`);
        außerdem: der Tiptap-Wrapper trägt `data-verlauf-feld="content"`.
        Mock-Vorbild: `TextBlock.test.tsx:15-17`, erweitert um Props-Durchreichung:
        ```ts
        vi.mock('../TiptapEditor', () => ({
            TiptapEditor: ({ onChange, verlaufsModus }: { onChange: (v: string, a: string) => void; verlaufsModus?: boolean }) => (
                <button data-testid="tiptap" data-verlaufsmodus={String(!!verlaufsModus)}
                        onClick={() => onChange('<p>neu</p>', 'sonstiges')} />
            ),
        }));
        ```
  - [ ] `TextBlock.tsx`: `onUpdate`-Typ erweitern, `verlaufsModus?: boolean` ergänzen
        und an `TiptapEditor` durchreichen (`:83-94`), `onChange={(val, art) => onUpdate(block.id, { content: serializeContent(val) }, art)}`,
        am Wrapper-`div` (`:82`, Klasse `doc-pdf-metrics--voll`) `data-verlauf-feld="content"`.
  - [ ] **Roter Test:** `ServiceBlock.test.tsx` erweitern: Titel-, Mengen-, Einheit-
        und EP-Eingabe tragen `data-verlauf-feld` (Karte aufklappen wie in
        `ServiceBlock.test.tsx:44`), der Beschreibungs-Wrapper trägt
        `data-verlauf-feld="description"`, und die Änderungsart wird durchgereicht.
  - [ ] `ServiceBlock.tsx`: `onUpdate`-Typ erweitern, `verlaufsModus` an `TiptapEditor`
        (`:209-220`) durchreichen, `data-verlauf-feld="title"` (`:153`),
        `="quantity"` (`:233`), `="unit"` (`:251`), `="price"` (`:267`),
        `="description"` am Wrapper (`:208`).
  - [ ] **Roter Test:** `SectionHeaderBlock.test.tsx` erweitern: der Kind-Wrapper trägt
        `data-block-id` des Kindes; das Namensfeld des Bauabschnitts trägt
        `data-verlauf-feld="sectionLabel"` **und** `data-eigenes-rueckgaengig="true"`
        (erst zum Bearbeiten klicken, `SectionHeaderBlock.tsx:190-201`).
  - [ ] `SectionHeaderBlock.tsx`: `onUpdate`/`onUpdateChild`-Typen erweitern, `verlaufsModus`
        aufnehmen und an die Kind-`ServiceBlock`s durchreichen (`:123-137`), dort auch
        `onUpdate={(id, updates, art) => onUpdateChild(block.id, id, updates, art)}`;
        `data-block-id={child.id}` an den Wrapper in `renderChild` (`:94`);
        die beiden Attribute ans Namensfeld (`:179-188`).
  - [ ] `SortableBlock.tsx`: `data-block-id={block.id}` an den äußeren `div` des
        Normalzweigs (`:53-58`). Der DragOverlay-Zweig (`:45-51`) bleibt ohne Attribut,
        sonst gäbe es die Id zweimal im DOM.
  - [ ] `AlternativGruppeBox.tsx`: `data-eigenes-rueckgaengig="true"` an das
        Umbenennen-Eingabefeld (`:42-51`) — es gilt ebenfalls erst beim Verlassen.
  - [ ] Gates: `npx vitest run src/components/tiptapVerlauf.test.ts src/components/TiptapEditor.test.tsx src/components/document-editor/TextBlock.test.tsx src/components/document-editor/ServiceBlock.test.tsx src/components/document-editor/SectionHeaderBlock.test.tsx`,
        `npm run lint`, `npx tsc -b && npx vite build --outDir "$(mktemp -d)" --emptyOutDir`.
  - [ ] **Keine Playwright-Spec.** Begründung fürs Kontext-Log: alle neuen Props sind
        opt-in und werden erst von Task 4 gesetzt — sichtbar ändert sich in dieser Runde
        nichts, weder im Dokumenteditor noch auf den anderen Tiptap-Seiten
        (`ArbeitszeitartEditor`, `DocumentBuilder`, `EmailTextvorlagenEditor`,
        `Leistungseditor`, `ProduktkategorieEditor`, `TextbausteinEditor`). Der
        Regressionsschutz für diese Seiten liegt in den Standardmodus-Fällen von
        `TiptapEditor.test.tsx`.

---

### Task 3 — Knöpfe „Rückgängig/Wiederholen“ samt Dropdown und Einbindung in die Kopfleiste

- Branch: `verlauf/task-3-knoepfe`
- Worktree: `/Users/marvinkuhn/dev/wt/verlauf-task-3`
- Files:
  - `react-pc-frontend/src/components/document-editor/VerlaufKnoepfe.tsx` (neu)
  - `react-pc-frontend/src/components/document-editor/VerlaufKnoepfe.test.tsx` (neu)
  - `react-pc-frontend/src/components/document-editor/DocumentEditorHeader.tsx` (ändern)
  - `react-pc-frontend/src/components/document-editor/DocumentEditorHeader.test.tsx` (neu)
- Vorbild:
  - Portal-Menü mit `fixed`-Positionierung, Tastaturführung, Schließen bei Scroll/Klick:
    `document-editor/WahlpositionMenu.tsx` (ganze Datei, besonders `:60-80` Positionierung
    im Ref-Callback und `:82-137` Tastatur/Außenklick).
  - Zugehörige Testdatei als Vorbild: `document-editor/WahlpositionMenu.test.tsx`.
  - Knopfstil der Nachbarn in der Kopfleiste:
    `DocumentEditorHeader.tsx:117-126` (`variant="ghost" size="sm"`,
    `className="h-7 px-2 text-[11px] gap-1 rounded-md text-slate-500 hover:text-slate-700"`).
  - Trennstrich: `DocumentEditorHeader.tsx:157` (`<div className="w-px h-5 bg-slate-200 mx-0.5" />`).
- Interfaces:
  - Produces (`VerlaufKnoepfe.tsx`):
    ```ts
    export interface VerlaufsEintrag { id: number; bezeichnung: string; }

    export interface VerlaufKnoepfeProps {
        kannRueckgaengig: boolean;
        kannWiederholen: boolean;
        /** Bezeichnung des nächsten Rückgängig-Schritts, z. B. "Position gelöscht". */
        naechstesRueckgaengig: string | null;
        naechstesWiederholen: string | null;
        /** Neuester Schritt zuerst. */
        schritte: VerlaufsEintrag[];
        /** anzahl = wie viele Schritte auf einmal zurückgenommen werden (Liste). */
        onRueckgaengig: (anzahl: number) => void;
        onWiederholen: () => void;
    }
    export function VerlaufKnoepfe(props: VerlaufKnoepfeProps): JSX.Element;
    ```
    Der Typ ist absichtlich eigenständig (kein Import aus `useDokumentVerlauf.ts`) —
    er ist strukturell zu dem kompatibel, was der Hook liefert.
  - Produces (`DocumentEditorHeader.tsx`): neue **optionale** Prop
    `verlauf?: VerlaufKnoepfeProps` (Import des Typs aus `./VerlaufKnoepfe`).
    Ohne die Prop rendert der Header exakt wie heute.
  - Consumes: nichts.
- Steps:
  - [ ] **Roter Test zuerst:** `VerlaufKnoepfe.test.tsx` (Vorbild `WahlpositionMenu.test.tsx`).
        Fälle: beide Knöpfe tragen `aria-label="Rückgängig"` bzw. `"Wiederholen"`;
        Tooltip (`title`) lautet `Rückgängig: Position gelöscht (Strg+Z)` bzw.
        `Wiederholen: Position gelöscht (Strg+Y)`; ohne Schritte sind beide Knöpfe und
        der Listen-Pfeil `disabled` und die Tooltips lauten
        „Nichts zum Rückgängigmachen“ / „Nichts zum Wiederholen“;
        Klick ruft `onRueckgaengig(1)` bzw. `onWiederholen()`;
        Pfeil öffnet eine Liste (`role="menu"`) am `document.body` (Portal, wie
        `WahlpositionMenu.test.tsx:33-40`) mit den Schritten in gegebener Reihenfolge;
        Überfahren des dritten Eintrags markiert die ersten drei und die Fußzeile sagt
        „3 Schritte rückgängig machen“ (bei einem Eintrag „1 Schritt rückgängig machen“);
        Klick auf den dritten Eintrag ruft `onRueckgaengig(3)`;
        `ArrowDown`/`ArrowUp`/`Home`/`End` wandern durch die Einträge, `Enter` löst aus,
        `Escape` schließt und gibt den Fokus an den Pfeil zurück, `Tab` schließt.
  - [ ] `VerlaufKnoepfe.tsx` bauen. Aufbau:
        ```tsx
        <div className="flex items-center gap-0.5">
          <Button variant="ghost" size="sm" aria-label="Rückgängig" title={…}
                  disabled={!kannRueckgaengig} onClick={() => onRueckgaengig(1)}
                  className="h-7 w-7 p-0 rounded-md text-slate-500 hover:text-slate-700">
            <Undo2 className="w-3.5 h-3.5" />
          </Button>
          <button type="button" aria-label="Liste der letzten Änderungen" aria-haspopup="menu"
                  aria-expanded={offen} disabled={!kannRueckgaengig} … >
            <ChevronDown className={cn("w-3 h-3 transition-transform duration-200", offen && "rotate-180")} />
          </button>
          <Button variant="ghost" size="sm" aria-label="Wiederholen" … >
            <Redo2 className="w-3.5 h-3.5" />
          </Button>
        </div>
        ```
        Symbol-Knöpfe statt Text, weil die Kopfleiste auf dem 14-Zoll-Laptop sonst
        überläuft (Spec, Abschnitt 1). `disabled:opacity-50` kommt aus der
        `Button`-Basis; der schmale Pfeil-Knopf (kein `Button`) bekommt
        `disabled:opacity-50 disabled:cursor-not-allowed` explizit.
  - [ ] Dropdown-Liste als Portal an `document.body`, `position: fixed`, Breite
        `const MENU_BREITE = 260`, Positionierung im Ref-Callback wie
        `WahlpositionMenu.tsx:60-80`, **linksbündig unter dem Pfeil**
        (`left: rect.left`, gegen den Fensterrand geklemmt) — so bleibt die Liste
        auf der linken Bildschirmhälfte und legt sich nicht über die Vorschau.
        Stil wie `WahlpositionMenu.tsx:150-157`:
        `bg-white rounded-xl border border-slate-200 shadow-lg p-1 animate-in fade-in zoom-in-95 duration-150`,
        dazu `max-h-72 overflow-y-auto` (20 Einträge passen sonst nicht).
        Einträge: `role="menuitem"`,
        `w-full flex items-start gap-2 px-2.5 py-1.5 rounded-lg text-left text-xs transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500/40`,
        markiert (Index ≤ Mausposition/Fokus) `bg-rose-50 text-rose-700`, sonst
        `text-slate-700 hover:bg-slate-50`; der Text steht in
        `<span className="flex-1 min-w-0 break-words leading-snug">` — **nicht**
        `truncate`, sonst schlägt `keinTextGekuerzt` in der Design-Prüfung an.
        Fußzeile: `border-t border-slate-100 mt-1 pt-1.5 px-2.5 pb-1 text-[10px] text-slate-400`.
        Markierung über `useState<number>(markiertBis)`, gesetzt bei `onMouseEnter`
        und `onFocus` des Eintrags.
  - [ ] **Roter Test:** `DocumentEditorHeader.test.tsx` (neue Datei): mit `verlauf`-Prop
        stehen die beiden Knöpfe im DOM **vor** dem Knopf „Textbaustein“
        (`compareDocumentPosition` oder Reihenfolge aus `container.querySelectorAll('button')`);
        ohne `verlauf`-Prop gibt es keinen Knopf „Rückgängig“ (Abwärtskompatibilität);
        mit `isLocked` wird die ganze mittlere Gruppe inkl. Verlaufsknöpfe nicht gerendert.
        Ein vollständiges Props-Objekt für den Header bauen (alle Callbacks `vi.fn()`,
        `fileInputRef: { current: null }`).
  - [ ] `DocumentEditorHeader.tsx`: `verlauf?: VerlaufKnoepfeProps` in die Props
        aufnehmen und in der mittleren Gruppe (`:115-116`) **vor** dem
        Textbaustein-Knopf rendern:
        ```tsx
        {verlauf && (<>
            <VerlaufKnoepfe {...verlauf} />
            <div className="w-px h-5 bg-slate-200 mx-0.5" />
        </>)}
        ```
  - [ ] Gates: `npx vitest run src/components/document-editor/VerlaufKnoepfe.test.tsx src/components/document-editor/DocumentEditorHeader.test.tsx`,
        `npm run lint`, `npx tsc -b && npx vite build --outDir "$(mktemp -d)" --emptyOutDir`.
  - [ ] **Keine Playwright-Spec.** Begründung fürs Kontext-Log: die Komponente wird
        erst von Task 4 eingebunden (`verlauf`-Prop ist optional und wird in dieser
        Runde von niemandem gesetzt) — es gibt keinen nutzerseitig erreichbaren Ablauf.
        Der sichtbare Ablauf inkl. Platzierung, Größen und Screenshots liegt in der
        Haupt-Spec von Task 4.

---

## Abschnitt 2 — Task 4 (startet erst, wenn Abschnitt 1 fertig und review-grün ist)

### Task 4 — Integration im Dokumenteditor (`index.tsx`), Tests und E2E

- Branch: `verlauf/task-4-integration`
- Worktree: `/Users/marvinkuhn/dev/wt/verlauf-task-4`
- Files:
  - `react-pc-frontend/src/components/document-editor/index.tsx` (ändern — Hauptdatei)
  - `react-pc-frontend/src/components/document-editor/index.test.tsx` (ändern)
  - `react-pc-frontend/src/index.css` (ändern — eine Klasse)
  - `react-pc-frontend/e2e/hilfen/dokument-editor.ts` (ändern — eine Stub-Option)
  - `react-pc-frontend/e2e/dokument-editor-rueckgaengig.spec.ts` (neu)
- E2E-Port: **5321** (`E2E_PORT=5321 npx playwright test e2e/dokument-editor-rueckgaengig.spec.ts`)
- Vorbild:
  - Scrollen mit `prefers-reduced-motion` und jsdom-Schutz:
    `src/components/EmailThreadView.tsx:243-248`; passender Test-Mock:
    `src/components/EmailThreadView.test.tsx:14-27`.
  - Ref-Spiegel für stale-closure-freie Lesezugriffe: `index.tsx:252-257` (`datumRef`)
    und `:259-265` (`kontextDatenRef`).
  - E2E-Aufbau, Stubs, Design-Prüfung: `e2e/dokument-editor-seite.spec.ts` (ganze Datei,
    besonders `:179-214` für das Überschreiben der Dokument-Antwort) und
    `e2e/hilfen/dokument-editor.ts`.
  - Vitest-Aufbau mit Fetch-Stubs: `document-editor/index.test.tsx:90-139`
    (`mockFetch(dokumentAbweichung)` nimmt bereits ein eigenes `positionenJson` an).
- Interfaces:
  - Consumes aus Task 1: `useDokumentVerlauf`, `DokumentStand`, `VerlaufsZiel`,
    `VerlaufsMeldung`, `useVerlaufTastatur`, `baueDokumentSignatur`.
  - Consumes aus Task 2: `verlaufsModus`-Prop und `art`-Parameter der Block-Komponenten,
    `TiptapAenderungsArt`, die DOM-Attribute `data-block-id` / `data-verlauf-feld` /
    `data-eigenes-rueckgaengig`.
  - Consumes aus Task 3: `VerlaufKnoepfe` über die Header-Prop `verlauf`.
  - Produces: kein neues Modul-Interface.
- Wortliste (verbindlich, Handwerker-Sprache, ohne Positionstitel):

  | Aufrufstelle in `index.tsx` | Bezeichnung |
  |---|---|
  | `addBlock('TEXT', …)` (Textbaustein-Picker, `:3311`) | `Textbaustein eingefügt` |
  | `addBlock('SERVICE', …)` (Leistungs-Picker, `:3331`) | `Leistung eingefügt` |
  | `addBlock('SERVICE', …)` (Stundensatz-Picker, `:3358`) | `Stundensatz eingefügt` |
  | `addBlock('SECTION_HEADER')` (`:3060`) | `Bauabschnitt eingefügt` |
  | `addBlock('SEPARATOR')` (`:3056`) | `Trennlinie eingefügt` |
  | `uebernehmeMaterial` (`:1859`) | `Material eingefügt` |
  | `handleFileChange` GAEB (`:593`) | `GAEB-Positionen eingefügt` |
  | `handleKategorieBestaetigt` / `…Ueberspringen` (`:1878`, `:1904`) | `Leistung eingefügt` |
  | `removeBlock` (`:1918`) | `<Textbaustein\|Position\|Bauabschnitt\|Trennlinie> gelöscht` |
  | `removeSectionChild` (`:1952`) | `Position gelöscht` |
  | `handleDragEnd` (`:1620`) | `<Textbaustein\|Position\|Bauabschnitt\|Trennlinie\|Abschluss> verschoben` |
  | `moveServiceToSection` (`:1689`) | `Position in Bauabschnitt verschoben` |
  | `ejectChildFromSection` (`:1740`) | `Position aus Bauabschnitt geholt` |
  | `updateBlock`/`updateSectionChild` `title` | `Titel geändert` |
  | … `quantity` | `Menge geändert` |
  | … `unit` | `Einheit geändert` |
  | … `price` | `Preis geändert` |
  | … `content`/`description` mit `art === 'tippen'` | `Text geändert` |
  | … `content`/`description` mit `art === 'sonstiges'` | `Formatierung geändert` |
  | … `sectionLabel` | `Bauabschnitt umbenannt` |
  | `modusWechsel`/`childModusWechsel` (`:1973`, `:1982`) | `Position auf Optional gestellt` / `Position auf Fest beauftragt gestellt` |
  | `alternativGruppeSpeichern` (`:1994`) | `Auswahl gespeichert` |
  | `gruppeAufloesen` (`:2001`) | `Auswahl aufgelöst` |
  | `gruppeUmbenennen` (`:2006`) | `Auswahl umbenannt` |
  | `RabattDialog onApply` (`:3380`) | `Rabatt geändert` |
  | `SummenFooter onDatumChange` (`:3217`) | `Datum geändert` |
  | `handleZahlungszielChange` (`:553`) | `Zahlungsziel geändert` |
  | `RechnungsadresseBlock onChange` (`:3090`) | `Rechnungsadresse geändert` |
  | `ClosureBlock onBalkenAnzeigenChange` (`:2937`) | `Balken eingeblendet` / `Balken ausgeblendet` |
  | mehrere Schritte auf einmal | `<N> Schritte` |

  Tooltips: `Rückgängig: <Bezeichnung> (Strg+Z)` / `Wiederholen: <Bezeichnung> (Strg+Y)`.
  Ansage (aria-live): `Rückgängig: <Bezeichnung>` bzw. `Wiederholen: <Bezeichnung>`.
  Dropdown-Fußzeile: `<N> Schritte rückgängig machen` (Einzahl: `1 Schritt …`).

- Steps:
  - [ ] **Roter Test zuerst** (`index.test.tsx`, neuer `describe`-Block „Rückgängig &
        Wiederholen“). Fixture: `mockFetch({ positionenJson: JSON.stringify({ blocks: BLOECKE, globalRabatt: 0 }) })`
        mit
        ```ts
        const BLOECKE = [
            { id: 't1', type: 'TEXT', content: '<p>Vielen Dank für Ihre Anfrage.</p>', fontSize: 10 },
            { id: 's1', type: 'SERVICE', title: 'Dachrinne reinigen', quantity: 2, unit: 'Stk', price: 100, fontSize: 10 },
            { id: 's2', type: 'SERVICE', title: 'Ziegel ersetzen', quantity: 5, unit: 'Stk', price: 20, fontSize: 10 },
        ];
        ```
        (verifiziert: der komplette Editor rendert mit echten Tiptap-Blöcken in jsdom in
        ~100 ms). In `beforeEach` zusätzlich `window.HTMLElement.prototype.scrollIntoView = vi.fn()`
        setzen (jsdom kennt es nicht) — Vorbild `EmailThreadView.test.tsx:14-16`.
        Löschen-Knopf einer Karte:
        ```ts
        const muelleimer = (blockId: string) => Array.from(
            container.querySelectorAll<HTMLButtonElement>(`[data-block-id="${blockId}"] button`)
        ).find(b => b.querySelector('svg.lucide-trash2'))!;
        ```
        Strg+Z/Strg+Y:
        `fireEvent.keyDown(document.body, { key: 'z', ctrlKey: true })` bzw. `{ key: 'y', ctrlKey: true }`.
        Fälle:
        1. Position löschen ⇒ Karte weg; Strg+Z ⇒ Karte wieder da (an derselben Stelle);
           Strg+Y ⇒ wieder weg.
        2. Tippen wird gebündelt: `await user.type(titelfeld, 'XY')` (zwei Anschläge)
           ⇒ ein Strg+Z stellt den ursprünglichen Titel her.
        3. Feldwechsel trennt: Titel ändern, dann Menge ändern ⇒ das erste Strg+Z
           betrifft nur die Menge.
        4. Formatierung ist ein eigener Schritt: über
           `(container.querySelector('[data-block-id="t1"] .ProseMirror') as HTMLElement & { editor: Editor }).editor`
           in `act()` erst `commands.insertContent(' Danke.')` (= Tippen), dann
           `commands.toggleBulletList()` (= Formatierung) ⇒ ein Strg+Z nimmt nur die
           Liste zurück, der getippte Text bleibt.
        5. Rabatt ist ein Schritt: Rabatt-Dialog öffnen, „Gesamtes Dokument“, 10 %,
           „Rabatt übernehmen“ ⇒ Fußzeile zeigt −10 %; Strg+Z ⇒ −10 % weg.
        6. Auto-Speichern nach Rückgängig bei reiner Rabattänderung: nach dem Rabatt
           steht „Ungespeichert“, nach Strg+Z **immer noch** „Ungespeichert“, und
           „Speichern“ schickt `positionenJson.globalRabatt === 0`.
        7. Dropdown-Mehrfach-Rückgängig: drei Positionen löschen, Liste öffnen, dritten
           Eintrag klicken ⇒ alle drei sind zurück.
        8. Gebuchte Rechnung (`mockFetch({ gebucht: true, positionenJson: … })`): keine
           Knöpfe „Rückgängig“/„Wiederholen“, Strg+Z lässt das Dokument unverändert.
        9. Sperre leert den Verlauf: Position löschen, dann per `rerender` auf
           `readOnly` umschalten und zurück ⇒ die Knöpfe sind wieder da, aber `disabled`
           (kein Schritt mehr vorhanden).
        10. Strg+Z im Adress-Entwurf wird nicht abgefangen: Adresse bearbeiten öffnen,
            Strg+Z in der Textarea ⇒ die zuvor gelöschte Position bleibt gelöscht.
        11. Strg+Z bei offenem Dialog wirkt nicht: Textbaustein-Picker öffnen, Strg+Z ⇒
            gelöschte Position bleibt gelöscht.
        12. Speichern lässt den Verlauf stehen: nach „Speichern“ ist der
            Rückgängig-Knopf weiterhin aktiv.
        13. Erfolgreicher E-Mail-Versand leert den Verlauf, „Entwurf“ nicht (über
            `onSuccess`/`lastSendWasDraftRef`-Pfad; falls der volle Mail-Ablauf in
            jsdom zu aufwendig ist, diesen Fall auf den Entwurfs-/Versand-Unterschied
            im Handler beschränken und im Kontext-Log vermerken).
        14. „Stelle zeigen“: nach Strg+Z trägt die betroffene Karte kurzzeitig die
            Klasse `verlauf-hervorgehoben` und `scrollIntoView` wurde aufgerufen.
  - [ ] **Refs und Ref-first-Setter** in `index.tsx` anlegen (direkt unter den
        bestehenden State-Deklarationen, Vorbild `:252-257`):
        `blocksRef`, `globalRabattRef`, `balkenAnzeigenRef`, jeweils mit
        `useLayoutEffect(() => { ref.current = wert; }, [wert])` (Layout-Effekt, damit
        das Ref schon beim nächsten Ereignis stimmt), dazu
        `const setzeBlocks = useCallback((neu: DocBlock[]) => { blocksRef.current = neu; setBlocks(neu); }, [])`
        und dasselbe für `setzeGlobalRabatt`/`setzeBalkenAnzeigen`.
        **Regel: ab jetzt schreibt niemand mehr direkt `setBlocks`.** Alle 23 heutigen
        `setBlocks`-Stellen laufen entweder über `verlauf.aendern` (Nutzeraktionen) oder
        über `setzeBlocks(...)` mit einem aus `blocksRef.current` berechneten Wert
        (automatische Änderungen: Laden `:927`, Bezugsdatum-Reparatur `:1116`,
        Standard-Textbausteine `:1158`, CLOSURE-Sync `:1781`). Das ist der Grund:
        der Updater-Stil und der Ref-Stil dürfen sich nicht mischen, sonst liest die
        zentrale Änderungsfunktion einen Stand, den ein noch nicht gerenderter
        Updater gleich überschreibt.
  - [ ] `leseStand`/`schreibeStand` als `useCallback` ergänzen:
        ```ts
        const leseStand = useCallback((): DokumentStand => ({
            blocks: blocksRef.current,
            globalRabatt: globalRabattRef.current,
            datum: datumRef.current,
            zahlungsziel: kontextDatenRef.current.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE,
            rechnungsadresse: kontextDatenRef.current.rechnungsadresse ?? '',
            balkenAnzeigen: balkenAnzeigenRef.current,
        }), []);
        ```
        `schreibeStand(werte)` setzt nur die enthaltenen Felder: `blocks` über
        `setzeBlocks`, `globalRabatt`/`balkenAnzeigen` über ihre Setter, `datum` über
        `datumRef.current = …; setDatum(…)`, und `zahlungsziel`/`rechnungsadresse` über
        ein gemeinsames, aus `kontextDatenRef.current` abgeleitetes neues Objekt
        (`kontextDatenRef.current = naechste; setKontextDaten(naechste)`).
        Bei gesetzter `rechnungsadresse` zusätzlich `adresseUserEditedRef.current = true`
        und `setAdresseGeaendert(true)` — bewusste Entscheidung der Spec (Abschnitt 3,
        „Rechnungsadresse“): gleicher sichtbarer Stand, kein Sonderpfad.
  - [ ] Hook einhängen:
        `const verlauf = useDokumentVerlauf({ leseStand, schreibeStand, gesperrt: isLocked });`
  - [ ] **Alle Nutzeraktionen auf `verlauf.aendern` umstellen** (Tabelle „Wortliste“ für
        die Bezeichnungen). Muster:
        ```ts
        const updateBlock = (id: string, updates: Partial<DocBlock>, art?: TiptapAenderungsArt) => {
            if (isLocked) return;
            verlauf.aendern({
                bezeichnung: bezeichnungFuerAenderung(updates, art),
                berechne: stand => ({ blocks: stand.blocks.map(b => b.id === id ? { ...b, ...updates } : b) }),
                buendelSchluessel: buendelSchluesselFuer(id, updates, art),
                ziel: { blockId: id, feld: verlaufsFeldVon(updates) },
            });
        };
        ```
        Dazu drei kleine Hilfsfunktionen **auf Modulebene** (über der Komponente, neben
        `applyInsert` `:65`): `bezeichnungFuerAenderung(updates, art)`,
        `buendelSchluesselFuer(id, updates, art)` (`block:<id>:<feld>`, aber `null`, sobald
        `art !== 'tippen'` bei `content`/`description` — Formatierung, Einfügen und Bilder
        werden nie gebündelt) und `blockName(block)` für „Position/Textbaustein/
        Bauabschnitt/Trennlinie/Abschluss“. Die Block-Komponenten schicken pro Aufruf
        immer genau **ein** Feld in `updates` (geprüft), darauf darf sich die Ableitung
        stützen.
        Umzustellende Stellen: `handleFileChange` `:593` (nach erfolgreichem Import,
        ein Schritt für alle Positionen), `handleZahlungszielChange` `:553`
        (Bündel-Schlüssel `kopf:zahlungsziel`), `handleDragEnd` `:1620`,
        `moveServiceToSection` `:1689`, `ejectChildFromSection` `:1740`, `addBlock` `:1796`
        (neuer optionaler dritter Parameter `bezeichnung`, damit Leistung und Stundensatz
        auseinandergehalten werden; Standard aus dem Typ abgeleitet),
        `uebernehmeMaterial` `:1859`, `handleKategorieBestaetigt` `:1878`,
        `handleKategorieUeberspringen` `:1904`, `updateBlock` `:1913`, `removeBlock` `:1918`,
        `updateSectionChild` `:1938`, `removeSectionChild` `:1952`, `childModusWechsel` `:1973`,
        `modusWechsel` `:1982`, `alternativGruppeSpeichern` `:1994`, `gruppeAufloesen` `:2001`,
        `gruppeUmbenennen` `:2006`, `ClosureBlock onBalkenAnzeigenChange` `:2937`,
        `RechnungsadresseBlock onChange` `:3090`, `SummenFooter onDatumChange` `:3217`,
        `RabattDialog onApply` `:3380`.
        Ziele (`ziel`): Einfügen ⇒ `{ blockId: <neue Id> }` (+ `sectionId` bei Anker
        `in-section`); Löschen/Verschieben ⇒ `{ blockId: <betroffene Id> }` (+ `sectionId`
        bei Kindern); Inhalte ⇒ `{ blockId, feld, sectionId? }`; Balken ⇒
        `{ blockId: CLOSURE_BLOCK_ID }`; Datum/Zahlungsziel/Adresse/Rabatt ⇒ `null`
        (Kopf- und Fußbereich sind ohnehin immer sichtbar).
  - [ ] **Rabatt als ein Schritt** (`:3380`): `blocks` **und** `globalRabatt` in einem
        einzigen `berechne`-Rückgabewert. Das bisherige `setHasUnsavedChanges(true)`
        (`:3388`) und das am Balken (`:2939`) entfallen — nach dem Signatur-Fix erkennt
        die Change-Detection beide Fälle selbst.
  - [ ] **`handleDragEnd` entschärfen** (`:1646-1671`): die `toast.warning`-Aufrufe
        stehen heute **innerhalb** des `setBlocks`-Updaters und feuern im StrictMode
        doppelt. Neue Form: `blocksRef.current` lesen, `arrayMove` + `validateRootReorder`
        **vor** dem Setzen ausführen, bei ungültigem Ergebnis nur den Toast zeigen und
        ohne Schritt aussteigen (eine Aktion, die den Stand nicht verändert, erzeugt
        laut Spec keinen Schritt), sonst `verlauf.aendern({ … })`.
  - [ ] **Automatische Änderungen** (kein Schritt, nur Ref-first): Laden `:927`,
        CLOSURE-Sync `:1779-1793` (liest `blocksRef.current` statt `prev`; nur setzen,
        wenn sich die Referenz ändert), Bezugsdatum-Reparatur `:1116`,
        Standard-Textbausteine `:1158`. Bei den letzten beiden nach dem Setzen
        `verlauf.leeren()` aufrufen — Spec: treffen Standard-Textbausteine erst ein,
        nachdem schon Schritte existieren, beginnt der Verlauf neu.
  - [ ] **Verlauf-Reset:** in den Sperr-Effekt `:1526-1537` `verlauf.leeren()` ergänzen
        (Sperre, auch Soft-Lock-Wechsel); nach erfolgreichem PDF-Export in
        `confirmExport` `:2283` (nach dem Download), nach endgültigem Druck in
        `executePrint` `:2202` (nur im `shouldBook`-Zweig) und im `onSuccess` des
        `EmailComposeModal` `:3469` (nur wenn `!wasDraft`). Vorschau-Druck und
        „Entwurf senden“ lassen den Verlauf stehen.
  - [ ] **Ungespeichert-Signatur** an fünf Stellen auf `baueDokumentSignatur` umstellen:
        Ladebaseline `:942-951` (dort ein lokales `let geladenerBalken = true;` im
        Parse-Zweig `:913` mitführen und zusammen mit `loadedGlobalRabatt` übergeben —
        die State-Variablen sind zu diesem Zeitpunkt noch nicht aktualisiert),
        `handleSave` `:1310` und `:1363`, Change-Detection `:1423-1434` (Dep-Liste um
        `globalRabatt` und `balkenAnzeigen` erweitern), Auto-Save `:1437-1447`
        (Dep-Liste ebenso), `buchenUndSperren` `:2164-2166`.
        `adresseGeaendert` bleibt additiv wie heute.
  - [ ] **Tastatur:** Wurzel-Ref `editorWurzelRef` an den äußeren `div` des Editors
        (`:2982`) hängen, den Dialog-Zustand einmal im Render bündeln
        ```ts
        const einDialogOffen = showExportWarning || showExportFormatDialog || showPrintOptions
            || showUnsavedWarning || showAddTypeDialog || showTextbausteinPicker
            || showLeistungPicker || showStundensatzPicker || showRabattDialog
            || alternativDialogAnker !== null || pendingLeistungInsert !== null
            || showFormatDialog || showValidityDialog || showEmailModal || materialDialogOffen;
        ```
        (nötig, weil die meisten dieser Dialoge **kein** `role="dialog"` tragen —
        geprüft: nur `AddTypeDialog`, `AlternativGruppeDialog`, `ArtikelAuswahlDialog`
        und der globale Bestätigungsdialog haben es) und
        ```ts
        useVerlaufTastatur({
            aktiv: !isLocked && !einDialogOffen,
            wurzelRef: editorWurzelRef,
            onRueckgaengig: () => zeigeSprung('Rückgängig', verlauf.rueckgaengig()),
            onWiederholen: () => zeigeSprung('Wiederholen', verlauf.wiederholen()),
        });
        ```
        Den bestehenden Strg+S-Effekt `:1514-1524` unverändert lassen.
  - [ ] **„Stelle zeigen“ + Ansage:** `const [sprung, setSprung] = useState<{ text: string; ziel: VerlaufsZiel | null; nummer: number } | null>(null);`
        `zeigeSprung(richtung, meldung)` setzt `{ text: \`${richtung}: ${meldung.bezeichnung}\`, ziel: meldung.ziel, nummer: n+1 }`
        (nichts tun bei `meldung === null`). Ein `useEffect` auf `sprung`:
        Karte über `editorWurzelRef.current?.querySelectorAll('[data-block-id]')` suchen
        (Vergleich über `dataset.blockId`, kein Selector-Escaping nötig), bei fehlender
        Karte auf `ziel.sectionId` ausweichen, sonst nichts tun (Spec: existiert die
        Stelle nicht mehr, wird nicht gescrollt). Dann
        ```ts
        const reduziert = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (typeof karte.scrollIntoView === 'function') karte.scrollIntoView({ block: 'center', behavior: reduziert ? 'auto' : 'smooth' });
        karte.classList.add('verlauf-hervorgehoben');
        const timer = window.setTimeout(() => karte.classList.remove('verlauf-hervorgehoben'), 1200);
        return () => window.clearTimeout(timer);
        ```
        Cursor: bei `ziel.feld === 'content'` bzw. `'description'` den Editor aus
        `editorRefs.current[blockId]` bzw. `editorRefs.current[\`${blockId}-desc\`]` holen
        und `if (ed && !ed.isDestroyed) ed.commands.focus()` — die Cursorstelle hat
        `TiptapEditor` beim Sync schon gesetzt (Task 2). Bei den übrigen Feldern
        `karte.querySelector<HTMLInputElement>('[data-verlauf-feld="…"]')?.focus()`
        (kein `setSelectionRange` bei `type="number"` — das wirft in Browsern).
        Ansage: als **erstes** Kind des Editor-`div`
        `<p aria-live="polite" aria-atomic="true" className="sr-only" data-testid="verlauf-ansage">{sprung?.text ?? ''}</p>`.
        Bewusst **ohne** `role="status"`: die E2E-Überschneidungsprüfung
        (`e2e/hilfen/design.ts:407`) sammelt `[role="status"]` mit ein, ein
        sr-only-Element würde dort als überlappendes Bedienelement auftauchen.
  - [ ] `src/index.css`: neue Klasse im Stil der bestehenden Editor-Klassen
        (neben `.doc-pdf-metrics`, `:154`):
        ```css
        /* Kurze Hervorhebung der Stelle, die ein Rückgängig/Wiederholen verändert hat.
           Gleicher Rose-Ring wie die aktive Karte (ring-2 ring-rose-500/30). */
        .verlauf-hervorgehoben {
          box-shadow: 0 0 0 2px rgb(244 63 94 / 0.3);
          border-radius: 0.75rem;
          transition: box-shadow 200ms ease;
        }
        @media (prefers-reduced-motion: reduce) {
          .verlauf-hervorgehoben { transition: none; }
        }
        ```
  - [ ] **Kopfleiste und Blöcke verdrahten:** `DocumentEditorHeader` (`:3028`) bekommt
        `verlauf={{ kannRueckgaengig: verlauf.kannRueckgaengig, kannWiederholen: …,
        naechstesRueckgaengig: …, naechstesWiederholen: …, schritte: verlauf.schritte,
        onRueckgaengig: (anzahl) => zeigeSprung('Rückgängig', verlauf.rueckgaengig(anzahl)),
        onWiederholen: () => zeigeSprung('Wiederholen', verlauf.wiederholen()) }}`
        (gern in ein `useMemo`). `TextBlock` (`:2892`), `ServiceBlock` (`:2908`) und
        `SectionHeaderBlock` (`:2867`) bekommen `verlaufsModus`. Die globale
        `TiptapToolbar` (`:3079`) bleibt unverändert — sie blendet ihre Pfeile ab jetzt
        selbst aus (Task 2).
        `RechnungsadresseBlock` (`:121-225`): Textarea (`:168`) bekommt
        `data-eigenes-rueckgaengig="true"`.
  - [ ] **E2E-Stub erweitern** (`e2e/hilfen/dokument-editor.ts`): `DokumentEditorStubOptionen`
        um `dokument?: Partial<AusgangsDokumentStand>` ergänzen und beim Initialisieren
        (`:83`) einmischen: `let dokument = { ...BEISPIEL_DOKUMENT, ...(optionen.dokument ?? {}) };`.
        Sonst nichts an der Datei ändern — sie wird von zwei bestehenden Specs benutzt.
  - [ ] **Playwright-Spec** `e2e/dokument-editor-rueckgaengig.spec.ts` (Vorbild
        `e2e/dokument-editor-seite.spec.ts`, `test`/`expect` aus `./hilfen/test`,
        `designPruefung` aus `./hilfen/design`). Dokument mit drei Positionen
        (`data-block-id="pos-1|pos-2|pos-3"`, Dummy-Daten) über die neue Stub-Option.
        Tests:
        1. „Knöpfe stehen links neben Textbaustein, ohne Überlappung“ — Rechtecke von
           `getByRole('button', { name: 'Rückgängig' })` und
           `getByRole('button', { name: 'Textbaustein' })` vergleichen (rechte Kante
           des Rückgängig-Knopfs ≤ linke Kante von „Textbaustein“, gleiche Zeile:
           Differenz der Mittelpunkte < 4 px) und `designPruefung(page, testInfo,
           'dokument-editor-rueckgaengig-kopfleiste', { primaerAktion: rueckgaengig })`.
           Läuft automatisch in allen drei Größen (`pc-14zoll`, `pc-uebergang`, `pc-monitor`).
        2. „Löschen → Strg+Z → Strg+Y“ über die echte Oberfläche:
           `page.locator('[data-block-id="pos-2"] button:has(svg.lucide-trash2)').click()`,
           `expect(page.locator('[data-block-id="pos-2"]')).toHaveCount(0)`,
           `page.keyboard.press('Control+z')` ⇒ wieder da,
           `page.keyboard.press('Control+y')` ⇒ wieder weg.
        3. „Tippen wird zu einem Schritt“: in das Titelfeld von `pos-1` tippen,
           Strg+Z ⇒ der ursprüngliche Titel steht wieder da (belegt zugleich, dass das
           Kürzel auch mit Cursor im Eingabefeld greift).
        4. „Liste nimmt drei Schritte auf einmal zurück“: drei Positionen löschen,
           `getByRole('button', { name: 'Liste der letzten Änderungen' })` öffnen,
           Fußzeile „3 Schritte rückgängig machen“ prüfen, dritten Eintrag klicken,
           alle drei Karten wieder da. Screenshot des **offenen** Menüs mit
           `designPruefung`. Hinweis: das Menü darf keine Bedienelemente verdecken —
           schlägt `keineUeberschneidungen` an, ist das ein echter Befund; dann den
           Screenshot in dem Zustand aufnehmen, in dem unter dem Menü nichts
           Interaktives liegt (nach den drei Löschungen ist das Dokument leer), und
           die Prüfung **nicht** abschalten.
        5. „Gebuchte Rechnung“: Stub mit `gebucht: true` ⇒ weder „Rückgängig“ noch
           „Wiederholen“ sichtbar, Strg+Z ändert nichts; Screenshot.
  - [ ] Gates: `npx vitest run src/components/document-editor/index.test.tsx`,
        `npm run lint`, `npx tsc -b && npx vite build --outDir "$(mktemp -d)" --emptyOutDir`,
        `E2E_PORT=5321 npx playwright test e2e/dokument-editor-rueckgaengig.spec.ts`.
  - [ ] Vor dem Abschluss prüfen, ob die parallele Session „Zahlungsziel mitspeichern“
        bereits in `main` gelandet ist; wenn ja, `handleSave` und die Signatur-Stellen
        zusammenführen (die Signatur läuft hier über `baueDokumentSignatur`, das
        Zahlungsziel wäre dort ein weiteres Feld) und das Ergebnis im Kontext-Log
        vermerken.

---

## Log

<wird nicht hier befüllt — siehe Kontext-Log-Datei. Dieser Abschnitt bleibt für
die kurze Abschluss-Zusammenfassung pro Abschnitt durch den Review-Agenten
reserviert.>
