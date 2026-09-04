---
name: handwerkerprogramm-design
description: Use this skill to generate well-branded interfaces and assets for Handwerkerprogramm, an open-source ERP for German craftsmen's businesses, either for production or throwaway prototypes/mocks. Contains essential design guidelines (colors, type, tone, iconography), the full design token CSS, a Lucide icon vocabulary, and two pixel-accurate UI kits (desktop ERP + mobile PWA) ready to compose into new screens.
user-invocable: true
---

Read the README.md file within this skill, and explore the other available files.

Key files:
- `README.md` — product context, content fundamentals, visual foundations, navigation map, iconography
- `colors_and_type.css` — CSS custom properties for colors, type, spacing, shadows, radii, motion, chips, PDF metrics
- `assets/` — real logos, file-type icons and product screenshots (use these, don't redraw)
- `ui_kits/desktop-erp/` — React/JSX recreation of the desktop ERP (ribbon nav, PageHeader, tables, dialogs, KI chat)
- `ui_kits/mobile-zeiterfassung/` — React/JSX recreation of the mobile PWA (dashboard, time tracking, bottom nav, sheets)
- `preview/` — small design-system specimen cards

If creating visual artifacts (slides, mocks, throwaway prototypes), copy assets out and create static HTML files for the user to view. If working on production code, you can copy assets and read the rules here to become an expert in designing with this brand.

Rules to internalize before producing anything:
1. **German only.** All UI copy. Never translate established terms (Angebot, Rechnung, Nachkalkulation, KI-Hilfe, Zeiterfassung…).
2. **Brand color is `rose-600` (#e11d48).** Sparingly — primary button, active nav state, page eyebrow, KI moments. Slate for everything else.
3. **Icons are Lucide.** Never hand-roll SVGs. `Gem` marks KI moments.
4. **Page headers** use the eyebrow + uppercase title + subtitle + actions pattern from `PageHeader.tsx`.
5. **No emoji in product UI.** README-style marketing is the only place they appear.
6. **System font stack.** No webfont imports.
7. **Rounded-lg** is the default. Rounded-2xl for dialogs/premium. Rounded-3xl for mobile sheets.
8. **Shadow-sm** for cards. Shadow-lg on hover. Glow only on hero/KPI moments.
9. **Three chip kinds, not one:** indigo placeholder token, yellow preview value, rose *interactive* Zahlungsziel. Only the last is clickable.
10. **Never render an empty box while loading.** Skeleton (`motion-safe:animate-pulse`) → fade in → `ImageOff` tile on failure.
11. **Gate animation behind `motion-safe:`** and give icon-only buttons an `aria-label` (+ `aria-expanded` when toggling).
12. **Don't retune the document editor's PDF metrics by eye** — 10pt / 1.3 / 2pt mirrors the PDF service so line breaks match the print output.

If the user invokes this skill without any other guidance, ask them what they want to build or design, ask some focused questions, and act as an expert designer who outputs HTML artifacts _or_ production code, depending on the need.
