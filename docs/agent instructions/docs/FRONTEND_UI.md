# 🎨 Frontend & UI-Guidelines

## 🧠 KI-Skill-Pflicht (UI/UX)
**WICHTIG:** Bevor du neue UI-Komponenten erstellst, Layouts entwirfst oder tiefgreifende UX-Entscheidungen triffst, mache dich zwingend mit diesem Skill vertraut und wende ihn an:
👉 `.claude\skills\ui-ux-pro-max\SKILL.md`

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