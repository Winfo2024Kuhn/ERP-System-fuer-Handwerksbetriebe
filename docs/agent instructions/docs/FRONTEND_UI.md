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

## 🔌 MCP-Pflicht für Komponenten

Baue Komponenten **nicht von Hand nach**, wenn ein MCP-Server sie liefern kann. Beide sind in `.mcp.json` eingetragen:

| MCP | Wofür |
| --- | --- |
| `shadcn` | Der Standard. Greift direkt auf shadcn-Bausteine und -Blöcke zu, zieht sie passend ins Projekt und installiert sie automatisch. Kein manuelles Kopieren aus dem Netz. |
| `magic` (21st.dev) | Moderne Layouts mit Animationen auf Tailwind-Basis. Braucht die Umgebungsvariable `TWENTYFIRST_API_KEY` (**niemals** den Key in `.mcp.json` schreiben — die Datei ist eingecheckt). |

**Wichtig:** Gezogene Komponenten immer auf unser Design-System umstellen (rose/slate statt der shadcn-Default-Farben) und die Pflicht-Komponenten unten haben Vorrang vor neu gezogenen.

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