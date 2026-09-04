# Handwerkerprogramm — Design System

repo: Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe
branch: main

## Last sync

date: 2026-09-04T17:11:12Z

### Updated in this project

- Added the 5 new ribbon entries (Dokumente, E-Mail Vorlagen, Belege & Kasse, Kostenstellen, Neuigkeiten) to the navigation map and desktop UI kit.
- Documented the new Zahlungsziel chip as a third, interactive chip kind alongside the placeholder and preview chips.
- Added the document↔PDF WYSIWYG metric tokens (10pt / 1.3 / 2pt, two content widths) mirroring RechnungPdfService.
- Documented the ThumbnailImage skeleton→loaded→failed pattern, the `motion-safe:` motion gate, and the new a11y conventions.

## Screen map

| Screen / card | Built from |
|---|---|
| `colors_and_type.css` | `react-pc-frontend/src/index.css`, `react-zeiterfassung/src/index.css`, `react-pc-frontend/tailwind.config.js` |
| `preview/components-buttons.html` | `react-pc-frontend/src/components/ui/button.tsx`, `ui/ai-button.tsx` |
| `preview/components-inputs.html` | `react-pc-frontend/src/components/ui/input.tsx`, `ui/label.tsx` |
| `preview/components-select.html` | `react-pc-frontend/src/components/ui/select-custom.tsx` |
| `preview/components-cards.html` | `react-pc-frontend/src/components/ui/card.tsx` |
| `preview/components-page-header.html` | `react-pc-frontend/src/components/PageHeader.tsx` |
| `preview/components-chips.html` | `react-pc-frontend/src/index.css` (`[data-placeholder-chip]`, `[data-preview-placeholder]`, `[data-zahlungsziel-chip]`) |
| `preview/components-loading.html` | `react-pc-frontend/src/components/ui/ThumbnailImage.tsx`, `src/index.css` (`.skeleton-shimmer`) |
| `preview/components-badges.html` | derived from status styling across `pages/*Editor.tsx` |
| `preview/brand-icons.html` | `react-pc-frontend/src/components/layout/RibbonNav.tsx`, `layout/MobileBottomNav.tsx` |
| `preview/brand-logo.html` | `assets/app_logo.png` |
| `ui_kits/desktop-erp/` | `components/layout/RibbonNav.tsx`, `layout/MainLayout.tsx`, `components/PageHeader.tsx`, `components/ui/*`, `assets/pc_frontend.png` |
| `ui_kits/mobile-zeiterfassung/` | `react-zeiterfassung/src/pages/DashboardPage.tsx`, `src/index.css`, `assets/mobild_frontend.png` |
| `thumbnail.html` | `colors_and_type.css` |
