# Desktop ERP — UI Kit

A pixel-accurate recreation of the **react-pc-frontend** desktop app at the `PROJEKTÜBERSICHT` screen.

## What's modeled

- **Top ribbon**: brand mark, 5 main tabs (`Vorlagen & Stammdaten`, `Projektmanagement`, `Zeiterfassung`, `Kommunikation`, `Finanzen & Controlling`), notification bell, user pill.
- **Subgroup ribbon**: the expandable second row that shows icon tiles grouped by subcategory (AUFTRÄGE, PLANUNG, EINKAUF…). Active tile = rose-600 icon on rose-100 tile with bold label underneath.
- **PageHeader**: rose eyebrow + uppercase title + muted subtitle + right-aligned primary/outline actions.
- **Filter bar card**: 3 inputs (Freitext, Kunde, Status select) + `Filtern` primary + `Reset` outline, with a 12px hint line.
- **Empty state** card: briefcase icon + "Keine Projekte gefunden."
- **Pagination**: text summary left + outline prev/next right.
- **Floating KI-Hilfe FAB**: rose-600 pill bottom-right with Gem icon.

## How to open

`index.html` — already wired to React 18 + Babel + Lucide via CDN.

## Files

- `index.html` — entry, pulls the JSX modules in order
- `app.jsx` — mounts `<DesktopApp/>` and composes everything
- `ribbon.jsx` — top tab bar + subgroup ribbon + tile
- `page.jsx` — `PageHeader`, `FilterBar`, `EmptyState`, `Pagination`
- `chrome.jsx` — `KIHilfeFab`, shared bits

All assets are referenced as `../../assets/app_logo.png` etc.
