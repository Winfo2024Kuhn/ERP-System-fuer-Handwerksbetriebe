# Frontend & UI-Guidelines

## Design-System (Handwerker-Fokus: Schlicht, Modern & Klar)
- **Farbschema:** Rose/Rot als Primär- und Akzentfarbe (`#dc2626` / `rose-600`, kein generisches Blau/Indigo).
- **Text & Kontraste:** Slate-Palette (`slate-50` bis `slate-900`).
- **Wording:** Klare Handwerker-Sprache, keine kryptischen SAP-/Buchhalterbegriffe.

## Pflicht-Komponenten (NIE neu erfinden!)
- `<Select>` → `src/components/ui/select-custom.tsx`
- `<DatePicker>` → `src/components/ui/datepicker.tsx`
- `<ImageViewer>` → `src/components/ui/image-viewer.tsx`
- `<DetailLayout>` → `src/components/DetailLayout.tsx` (2-Spalten-Layout)
- `<DocumentPreviewModal>` → `src/components/DocumentPreviewModal.tsx` (für PDFs)
- `<ConfirmDialog>` / `useConfirm` → für Bestätigungsdialoge

## Button-Klassen
- **Primär:** `bg-rose-600 text-white border border-rose-600 hover:bg-rose-700`
- **Sekundär:** `border-rose-300 text-rose-700 hover:bg-rose-50`
- **Ghost:** `variant="ghost" text-rose-700 hover:bg-rose-100`

## Build & Coding-Regeln
- Nach Änderungen: `npm test` und `npm run build` im jeweiligen Frontend-Ordner ausführen.
- Kein `dangerouslySetInnerHTML` ohne Sanitizing.
- URL-Parameter immer mit `encodeURIComponent()`.
- Hierarchie: `src/components/ui/` (wiederverwendbare UI-Atome), `src/features/{name}/` (Domänenlogik).
