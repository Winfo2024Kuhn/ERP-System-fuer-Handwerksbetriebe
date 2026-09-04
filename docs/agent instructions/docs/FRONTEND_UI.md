# 🎨 Frontend & UI-Guidelines

## 🧠 KI-Skill-Pflicht (UI/UX) — HOOK-ERZWUNGEN

**Vor JEDER Frontend-Änderung** musst du einen der folgenden Skills per `Skill`-Tool aufrufen. Das ist keine Empfehlung: Der PreToolUse-Hook `.claude/hooks/check-doc-read.ps1` blockt deine Edit/Write-Aufrufe auf `react-pc-frontend/` und `react-zeiterfassung/` mit Exit 2 (DESIGN-SKILL-GUARD), solange in dieser Session keiner davon lief.

| Skill | Wofür |
| --- | --- |
| `ui-ux-pro-max:ui-ux-pro-max` | **Standard.** Styles, Farbpaletten, Typografie, 99 UX-Regeln (Accessibility, Touch, Performance, Animation) |
| `frontend-design:frontend-design` | Visuelle Ausrichtung neuer UI, wenn es nicht nach Template aussehen soll |
| `ui-ux-pro-max:design-system` | Design-Tokens, Komponenten-Specs, systematische Skalen |
| `ui-ux-pro-max:design` | Logos, Banner, Icons, Präsentationen, Corporate Identity |

Das Flag gilt pro Session — ein Aufruf reicht für alle folgenden Frontend-Edits.

## 🧭 Kern-Prinzip: Gulf of Execution & Gulf of Evaluation

Jede UI-Entscheidung wird an diesen zwei Fragen gemessen. Beide MÜSSEN mit Ja beantwortbar sein, sonst ist die Umsetzung nicht fertig:

### Gulf of Execution — "Sieht der Nutzer sofort, WIE er sein Ziel erreicht?"
- Jede interaktive Fläche sieht klickbar aus (Cursor, Hover-State, Fokus-Ring). Nichts, was wie normaler Text aussieht, ist heimlich ein Button oder Link.
- Pro Screen genau **eine** klar erkennbare Primär-Aktion (gefüllter `bg-rose-600`-Button). Sekundäre und destruktive Aktionen sind visuell klar abgesetzt (siehe Button-Klassen unten).
- Icon-only-Buttons **immer** mit sichtbarem Tooltip/`aria-label` — nie ein Icon ohne Erklärung raten lassen.
- Deaktivierte Buttons/Felder erklären per Tooltip **warum** sie deaktiviert sind, statt einfach grau und stumm zu sein.
- Destruktive Aktionen (Löschen, Stornieren, unwiderrufliches Versenden) verlangen eine Bestätigung per Dialog — nie ein einzelner Klick, der sofort unwiderruflich ausführt.
- Formulare zeigen Pflichtfelder, Format-Hinweise (z. B. Datumsformat, Mengeneinheit) und Platzhalter **bevor** der Nutzer den Fehler macht, nicht erst danach in einer Fehlermeldung.

### Gulf of Evaluation — "Sieht der Nutzer sofort, WAS passiert ist?"
- Jede asynchrone Aktion (Speichern, Löschen, Senden, Import, Export) zeigt einen sichtbaren Ladezustand (Spinner/Skeleton, Button per `disabled` gegen Doppel-Klick gesperrt) **und danach** ein sichtbares Ergebnis. Nie "Klick ins Leere".
- Erfolg → Toast (`toast.success(...)`), Fehler → **immer** Toast (`toast.error(...)`) — siehe Abschnitt „Toast-Pflicht" unten. Kein stiller Fail, kein Fehler, der nur in der Browser-Konsole landet.
- Gespeicherte/geänderte Daten aktualisieren sichtbar die UI, ohne dass der Nutzer neu laden oder raten muss, ob es geklappt hat.
- Lade-Zustand, leere Liste und Fehler-Zustand sind drei optisch klar unterscheidbare Zustände — eine leere Liste darf nie wie ein kaputter Ladevorgang aussehen (und umgekehrt).

## 🍞 Toast-Pflicht bei Fehlern

**Jede fehlgeschlagene Aktion MUSS eine Toast-Fehlermeldung zeigen** — API-Fehler, Validierungsfehler, Netzwerkfehler, alles. Kein `alert()`, kein stiller `catch`-Block, kein Fehler, der nur per `console.error` verschwindet.

Nutze die vorhandene Komponente `src/components/ui/toast.tsx` (nicht neu bauen):

```tsx
import { useToast } from '../components/ui/toast'; // Pfad je nach Ordnertiefe anpassen

const toast = useToast();

try {
  await api.save(data);
  toast.success('Gespeichert.');
} catch (err) {
  toast.error(err instanceof Error ? err.message : 'Rechnung konnte nicht gespeichert werden.');
}
```

- Meldungstext ist konkret, deutsch, im Handwerker-Wording — nicht „Ein Fehler ist aufgetreten", sondern was genau fehlgeschlagen ist (z. B. „Rechnung konnte nicht gespeichert werden.").
- Erfolg wird nur getoastet, wenn er sonst nicht sichtbar ist (z. B. kein Redirect danach). Fehler werden **immer** getoastet, ausnahmslos.

## 🔌 MCP-Pflicht für Komponenten

Baue Komponenten **nicht von Hand nach**, wenn ein MCP-Server sie liefern kann. Beide sind in `.mcp.json` eingetragen:

| MCP | Wofür |
| --- | --- |
| `shadcn` | Der Standard. Greift direkt auf shadcn-Bausteine und -Blöcke zu, zieht sie passend ins Projekt und installiert sie automatisch. Kein manuelles Kopieren aus dem Netz. |
| `magic` (21st.dev) | Moderne Layouts mit Animationen auf Tailwind-Basis. Braucht die Umgebungsvariable `TWENTYFIRST_API_KEY` (**niemals** den Key in `.mcp.json` schreiben — die Datei ist eingecheckt). |

**Wichtig:** Gezogene Komponenten immer auf unser Design-System umstellen (rose/slate statt der shadcn-Default-Farben) und die Pflicht-Komponenten unten haben Vorrang vor neu gezogenen.

**Farbpaletten-Pflicht:** `ui-ux-pro-max`, `frontend-design` & Co. schlagen von sich aus oft fremde Paletten vor (blue/indigo/violet als Default-Theme). Diese Vorschläge sind nur Ausgangspunkt für Struktur, Spacing und Komponenten-Auswahl — Farben werden **immer** auf unser Schema unten (rose/slate) umgestellt, bevor Code committet wird. Kein Fallback auf Skill- oder MCP-Default-Farben, auch nicht "nur vorübergehend" oder in Mockups/Prototypen.

## Build & Coding-Regeln
- Nach JEDER Änderung: `npm run build` im jeweiligen Ordner ausführen (Fail Fast!).
- Kein `dangerouslySetInnerHTML` ohne `EmailHtmlSanitizer`.
- URL-Parameter immer mit `encodeURIComponent()`.
- Hierarchie: `src/components/ui/` (Atome), `src/features/{name}/` (Domänenlogik).

## Design-System (Handwerker-Fokus: Schlicht & Klar)
- **Farbschema:** Rose/Rot (ZWINGEND – kein indigo/blue).
- **Primärfarbe:** `#dc2626` (rose-600) | Palette: `rose-50`–`rose-900` + `slate-50`–`slate-900`.

### Button-Klassen
- **Primär:** `bg-rose-600 text-white border border-rose-600 hover:bg-rose-700`
- **Sekundär:** `border-rose-300 text-rose-700 hover:bg-rose-50`
- **Ghost:** `variant="ghost" text-rose-700 hover:bg-rose-100`
- **Größe:** Standard `size="sm"`. Icons (`w-4 h-4`) links vom Text (Lucide React).

### Pflicht-Komponenten (NIE neu erfinden!)
- `<Select>` -> `src/components/ui/select-custom.tsx`
- `<DatePicker>` -> `src/components/ui/datepicker.tsx`
- `<ImageViewer>` -> `src/components/ui/image-viewer.tsx`
- `<DetailLayout>` -> `src/components/DetailLayout.tsx` (2-Spalten-Layout)
- `<EmailHistory>` -> `src/components/EmailHistory.tsx`
- `<GoogleMapsEmbed>` -> `src/components/GoogleMapsEmbed.tsx`
- `<DocumentPreviewModal>` -> `src/components/DocumentPreviewModal.tsx` (für PDFs)

### Page Header Pattern (Zwingend für alle Seiten)
```tsx
<div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
  <div>
    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Kategorie</p>
    <h1 className="text-3xl font-bold text-slate-900">SEITENTITEL</h1>
    <p className="text-slate-500 mt-1">Beschreibung</p>
  </div>
  <div className="flex gap-2">{/* Buttons */}</div>
</div>