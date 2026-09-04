# Handwerkerprogramm — Design System

A design system for **Handwerkerprogramm**, an open-source ERP for German craftsmen's businesses ("Handwerksbetriebe"). The product is built to solve real, everyday problems of small trade shops — quoting, time tracking, invoicing, and post-calculation — in one tightly integrated app with an AI assistant baked in.

> Das Open-Source-ERP, das für Handwerksbetriebe gebaut wurde – nicht an sie angepasst.

---

## Products in this system

| Product | What it is | Primary user | Tech |
|---|---|---|---|
| **Desktop Frontend** (`react-pc-frontend`) | The full ERP back office. Projects, calculation, invoicing, purchasing, receipts & petty cash, cost-centre controlling, document overview, email center + templates, website news, rent accounting, controlling dashboards, KI-Hilfe chat, document editor. | Master craftsman, office staff | React 18 + TypeScript + Vite + Tailwind CSS, lucide-react icons |
| **Mobile PWA** (`react-zeiterfassung`) | Site-first companion app. Time tracking, site diary with photos, delivery-slip scanning, vacation requests, balance overview. Offline-first via IndexedDB. | Field employees / monteurs | React 18 + TS + Vite + Tailwind, PWA + Service Worker |
| **Document Editor** | Block-based quote/invoice builder with live PDF preview, form designer with drag & drop layout on an A4 canvas. | Office staff | Tiptap + dnd-kit + OpenPDF |

Both frontends share a single visual vocabulary: **rose-600 brand + slate neutrals, system font stack, generous rounding (lg → 2xl), soft shadows, and Lucide icons.** That's what this design system encodes.

---

## Sources this was built from

- **GitHub repository** — [Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe) — branch `main`
  - Root `README.md` (full product overview in German)
  - `react-pc-frontend/src/` (App.tsx, components/, pages/, index.css, tailwind.config.js)
  - `react-zeiterfassung/src/` (App.tsx, index.css, pages/)
  - `assets/` — raster screenshots of Desktop, Document Editor, Mobile
- **Screenshots** (`assets/`): `pc_frontend.png`, `mobild_frontend.png`, `Dokumenteditor.png`, `app_logo.png`
- No Figma file was provided. Everything is reverse-engineered from the codebase + screenshots.

---

## Index / manifest

```
.
├── README.md                    ← you are here
├── SKILL.md                     ← Agent Skills entry point (usable in Claude Code)
├── github.md                    ← upstream repo association + screen map
├── thumbnail.html               ← homepage tile
├── colors_and_type.css          ← CSS custom properties: colors, type, spacing, shadows, radii, chips, PDF metrics
├── assets/                      ← logos, file-type icons, product screenshots
│   ├── app_logo.png             ← primary app icon
│   ├── firmenlogo_icon.png      ← in-app company logo (tenant-replaceable)
│   ├── hicad_logo.png / tenado_logo.jpg / pdf_icon.jpg / excel_image.jpg
│   ├── pc_frontend.png          ← desktop screenshot
│   ├── mobild_frontend.png      ← mobile screenshot
│   └── Dokumenteditor.png       ← document editor screenshot
├── preview/                     ← design-system specimen cards
│   ├── colors-brand / -slate / -semantic
│   ├── type-scale / -roles
│   ├── spacing-scale / -radii / -shadows
│   ├── components-buttons / -inputs / -select / -badges / -cards
│   │                 / -chips / -loading / -page-header
│   └── brand-logo / -icons
└── ui_kits/
    ├── desktop-erp/             ← high-fi recreation of the PC frontend
    │   ├── README.md
    │   ├── index.html
    │   └── *.jsx
    └── mobile-zeiterfassung/    ← high-fi recreation of the mobile PWA
        ├── README.md
        ├── index.html
        └── *.jsx
```

Raw `.tsx` product source is deliberately **not** vendored here — it only runs through the app's bundler. Read it upstream via the repo link when you need implementation detail; this system carries the extracted values.

---

## Content fundamentals

**Language: German only.** The product is written in and for German SMB trades. All UI copy, error messages, empty states, and tooltips are in formal-but-warm German.

**Address form: informal "du" ambient, but formal "Sie" in direct UI.** The README and marketing copy use "du" ("Gefällt dir das Projekt?"). The in-app UI uses neutral, instruction-style phrasing or "Sie" when addressing users — e.g. `"Bitte neuen QR-Code anfordern."`, `"Wird geladen..."`, `"Mitarbeiter ist deaktiviert."`

**Tone: competent, direct, a bit craft-proud.** Sentences are short. No marketing fluff inside the app. Emoji show up heavily in the README ("🧠", "⏱️", "📱") as structural markers for marketing content, and **almost never in the product UI** — the product UI relies on Lucide icons instead.

**Casing:**
- Page titles are **UPPERCASE, bold, with a rose eyebrow above** — e.g. eyebrow `PROJEKTMANAGEMENT`, title `PROJEKTÜBERSICHT`. See `PageHeader.tsx`.
- Buttons are sentence case ("Neues Projekt", "Aktualisieren", "Filtern").
- Subgroup labels inside the ribbon are `UPPERCASE, 9px, tracking-wider` ("AUFTRÄGE", "PLANUNG").
- Compound German nouns are preserved as-is — never split them with spaces.

**Vocabulary (terminology you must reuse, not invent):**

| Term | Meaning |
|---|---|
| Angebot | Quote / offer |
| Rechnung / Teilrechnung / Schlussrechnung / Gutschrift / Storno | Invoice / partial / final / credit note / reversal |
| Projekt | Project (an accepted job) |
| Anfrage | Inquiry (pre-project) |
| Bauvorhaben | Construction project (often a project's human name) |
| Zeiterfassung / Zeitbuchung | Time tracking / time entry |
| Arbeitsgang | Work step |
| Produktkategorie | Product category (used for time analysis) |
| Nachkalkulation | Post-calculation (actual vs. planned) |
| Lieferant | Supplier |
| Bestellung / Bedarf | Order / demand |
| Stammdaten | Master data |
| Leistung | Service line item |
| Textbaustein / Textvorlage | Text snippet / template |
| Offene Posten | Open receivables |
| Belege & Kasse | Receipts & petty cash |
| Kostenstelle | Cost centre |
| Zahlungsziel | Payment term (an interactive chip in text templates) |
| E-Mail Vorlage | Email template |
| Neuigkeiten | News (public website posts) |
| KI-Hilfe | AI help (the in-app assistant) |

**Sample UI copy (from live code):**
- Page subtitle: `"Übersicht und Verwaltung Ihrer Projekte."`
- Empty state: `"Keine Projekte gefunden."`
- Hint under filter: `"Für Performance werden immer nur 12 Einträge auf einmal geladen."`
- Toasts / sync messages: `"🌐 App ist online -> Sync pending entries"` (console), `"Server nicht erreichbar. Bitte später erneut versuchen."`
- Auth: `"Token ungültig. Bitte neuen QR-Code anfordern."`
- Dashboard greeting: `"Hallo, Max Mustermann!"` / `"Dienstag, 10.3.2026"`

**Do:** use precise trade terminology. Write short action labels. Pair any state badge with a number or time.
**Don't:** translate German terms to English in UI. Add decorative emoji inside the product. Use exclamation points outside the mobile dashboard greeting.

---

## Visual foundations

### Colors
- **Brand: rose-600 (`#e11d48`)**, with rose-700 on press, rose-100/50 for backgrounds, rose-200 for soft borders. The brand accent is used sparingly — on primary buttons, active tab indicators, the KI-Hilfe FAB, page eyebrows, and the mobile primary CTA card.
- **Neutrals: Tailwind slate.** App background is `slate-50 (#f8fafc)`, primary text `slate-900`, secondary text `slate-500`, borders `slate-200`. The desktop chrome is white on slate-50.
- **Semantic:** `green-600` success (online badge), `amber-500` warn, `red-600` danger (also used as the mobile brand — `--rose-primary: #dc2626`, a half-step darker than desktop's `#e11d48`).
- **AI accent:** `purple-500` used for KI moments (Gem icons are rose, purple used behind Gemini API notes). Placeholder chips in the document editor are `indigo-100 / indigo-700`.
- No gradients as page background. One gradient is used: `gradient-text-rose` (rose-600 → rose-400) and the hero glow under KPI cards.

### Typography
- **System font stack** everywhere: `system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`. No webfonts are loaded. (A prior iteration may have used a custom font — flag if one appears.)
- Weights used: **400 / 500 / 600 / 700**. 800+ not used.
- Uppercase is a device, not decoration: page titles, eyebrow labels, ribbon subgroup labels.
- Monospace (`ui-monospace`) only in code chips and placeholder tokens (`{{KUNDENNAME}}`).
- `tabular-nums` on money, minutes, and KPI numbers.

### Backgrounds
- App bg: flat `slate-50`. No illustrations, patterns, or hand-drawn textures.
- Cards: flat white with `border-slate-200 rounded-lg shadow-sm`.
- Mobile sheets: white, rounded-t-3xl, backdrop-blur on the scrim.
- Dialogs: `bg-black/50 backdrop-blur-sm` scrim + white rounded-2xl panel.
- The KI chat widget and mobile bottom nav use **glassmorphism**: `bg-white/80 backdrop-blur-xl` over content.

### Animation
- Easing: the canonical curve is `cubic-bezier(0.32, 0.72, 0, 1)` (used on dialogs, mobile sheets). Spring easing `cubic-bezier(0.34, 1.56, 0.64, 1)` is used for image-drop settles and subtle pops.
- Durations: 150ms for hover, 200ms default, 300–350ms for sheet slide-up.
- Motion library: named keyframes in `index.css` — `fadeInUp`, `scaleIn`, `slideUp`, `fadeIn`, `tabBounce`, `skeletonShimmer`, `syncSpin`, `elasticSpin`, `gradientSpin`, `glowSpin`, `ringSpin`.
- Staggered entry: `.delay-1` through `.delay-7` (50ms steps) for dashboard card reveals.
- Reduced-motion: all animations are disabled under `prefers-reduced-motion: reduce` on mobile. Newer components go further and gate motion at the utility level with Tailwind's **`motion-safe:`** prefix (`motion-safe:animate-pulse`, `motion-safe:transition-opacity`) — prefer this in anything new.

### Loading & skeleton states
The product never shows an empty hole where content will land. Two established patterns:

- **Image skeleton** (`ThumbnailImage.tsx`): a `bg-slate-200 motion-safe:animate-pulse rounded` block fills the tile while the thumbnail loads, then the image fades in over 200ms (`opacity-0` → `opacity-100`). Layout never shifts. On failure it falls back once to the original, then renders a `bg-slate-100` tile with a centred `ImageOff` icon (`w-6 h-6 text-slate-400`) plus `role="img"` and an `aria-label`.
- **PDF skeleton**: the `.skeleton-shimmer` gradient (`slate-100 → pink-100 → slate-100`, 200% background, 2s ease-in-out loop) sweeps across the placeholder page while the preview renders.

Always load the server's downscaled thumbnail (max 300px) rather than the original — on phone photos that saves megabytes per tile.

### Accessibility conventions
Newer code holds a consistent bar; match it:
- Icon-only buttons get an `aria-label` and, when they toggle, `aria-expanded` (see the ribbon collapse button).
- Decorative icons get `aria-hidden="true"`; icons that *are* the content get `role="img"` + `aria-label`.
- Custom selects use `role="listbox"` / `role="option"` with `aria-selected`.
- Focus is always visible — a 2px rose-500 ring, never a focus-visible-only trick.
- Gate animation behind `motion-safe:`.

### Document ↔ PDF WYSIWYG metrics
The document editor is not styled to taste — it is calibrated so on-screen line breaks match the printed PDF exactly. `.doc-pdf-metrics` sets `10pt / 1.3 line-height / 2pt paragraph gap`, mirroring `RechnungPdfService`. Two content widths exist: `--doc-pdf-w-spalte` (`257pt + 24px`, the position-table label column) and `--doc-pdf-w-voll` (`515pt + 24px`, full page width). The `+24px` compensates the editor's own 12px horizontal padding. **Never retune these by eye** — a template with a different table width shifts the break and the values must be recomputed from the PDF service.

### Hover / press states
- **Hover:** primary buttons go from rose-600 → rose-700. Outline/ghost buttons get a rose-50/100 wash. Cards with `stat-card-hover` lift `-4px` and deepen shadow to `shadow-lg`. Table rows get a gradient left-to-transparent wash in rose-500/5%.
- **Press:** mobile buttons scale `0.95` (`button:active { transform: scale(0.95) }` — global in mobile index.css). Primary mobile buttons scale `0.97` with an inset shadow. Desktop buttons don't scale; they just swap color.
- **Focus:** all inputs get a 2px rose-500 focus ring. No focus-visible-only tricks — always visible.

### Borders & shadows
- Default border: `1px solid slate-200`. Strong border: `slate-300`. Soft divider: `slate-100`.
- Brand-tinted borders on active tabs: `border-rose-200`.
- Shadow scale (matches Tailwind): `shadow-sm` (cards), `shadow` (default), `shadow-md`, `shadow-lg` (hover), `shadow-2xl` (dialogs, sheets, dropdowns).
- Special: rose glow `0 10px 40px -10px rgb(225 29 72 / 0.3)` on hero/KPI elements (`animate-subtlePulse`).
- No inner shadows except mobile primary button press feedback.
- No "protection gradients" over imagery.

### Corner radii
- `rounded` / `rounded-md` (4–6px): dense UI — inputs, small buttons, chips.
- `rounded-lg` (8px): **default for buttons, cards, nav pills**.
- `rounded-xl` (12px): secondary cards, menu surfaces.
- `rounded-2xl` (16px): dialogs, mobile submenu sheets, floating premium cards.
- `rounded-3xl` (24px): mobile bottom-sheet top corners.
- `rounded-full`: avatars, icon circles, FAB, active underline pills.

### Layout rules
- Desktop: fixed-height `h-screen` frame. Top chrome = 64px ribbon header + up to 160px expandable subgroup ribbon. Content scrolls in the `<main>`.
- Mobile: full viewport. `h-dvh` to respect iOS chrome. Fixed bottom nav (64px + safe-area). Content pads `pb-20` on mobile, `pb-8` on desktop.
- Page content pads `px-4 md:px-8`, `pt-4 md:pt-8`.
- `PageHeader` gives every page the same rose eyebrow + uppercase title + subtitle + right-side actions pattern. Don't break it.
- No sidebars — the Microsoft-ribbon-style top nav is load-bearing and defines the product.

### Navigation map
Five ribbon tabs, each with labelled subgroups. This is the current shape — new pages join an existing subgroup rather than inventing a tab (**bold** = added since the last revision):

| Tab | Subgroups |
|---|---|
| **Vorlagen & Stammdaten** | Dokumente (Textvorlagen, Leistungen, Stundensätze, Formularwesen) · Kontakte (Kunden, Mitarbeiter, Lieferanten) · Katalog (Artikel, Arbeitsgänge, Kategorien) · Administration (Dokumentenrechte, Firma, Einstellungen) |
| **Projektmanagement** | Aufträge (Projekte, Anfragen) · **Dokumente (Dokumente)** · Planung (Kalender) · Einkauf (Bestellungen, Bedarf) |
| **Zeiterfassung** | Übersicht (Kalender) · Berichte (Auswertung, Steuerberater) · Einstellungen (Zeitkonten, Feiertage) · Urlaub (Anträge) |
| **Kommunikation** | E-Mail (E-Mail Center, **E-Mail Vorlagen**) |
| **Finanzen & Controlling** | Buchhaltung (Offene Posten, Rechnungen, **Belege & Kasse**, Mietabrechnung) · Auswertung (Erfolgsanalyse, **Kostenstellen**) · **Website (Neuigkeiten)** |

Admin-only routes: `/abteilung-berechtigungen`, `/firma`, `/einstellungen`, `/benutzer`, `/website`.

### Transparency & blur
- Used sparingly: mobile nav, KI chat widget, dialog scrims, and the "glass-card" utility (white/85% + blur 12px).
- Never used inside a form or on text-heavy surfaces — they stay opaque.

### Imagery
- Product imagery = real screenshots of the app, warm-neutral, no filters.
- No stock photography, no illustrations in-app. Two marketing screenshots (`pc_frontend.png`, `mobild_frontend.png`) serve as "what the product looks like" proof shots in the README.

### Cards
- **Default card:** `bg-white border border-slate-200 rounded-lg shadow-sm` — see `components/ui/card.tsx`. That's it. No gradient fills, no colored left borders.
- **Stat card (dashboard):** white, rounded-lg, shadow-sm base; hover lifts + shadow-lg via `.stat-card-hover`.
- **Mobile action card:** white rounded-xl with a rose icon tile (soft rose-100 bg, rose-600 icon) on the left and `text-slate-900` title + `text-slate-500` subtitle.
- **Hero CTA card** (mobile dashboard "Zeit erfassen"): solid rose-600 bg, white text, rounded-2xl, shadow-rose glow.

**Chips are three distinct things** — never swap them:
- **Placeholder token** `{{KUNDENNAME}}` — indigo-100 bg / indigo-700 text, mono, radius 6. Inert.
- **Live preview value** — yellow-200 bg / slate-900 text, mono, radius 6. Shows the resolved value in the preview.
- **Zahlungsziel** — rose-50 bg / rose-200 border / rose-700 text, 600 weight, radius 6. **Interactive**: `cursor: pointer`, hover deepens to rose-100 / rose-300. A protected, clickable token inside a text template.

---

## Iconography

**Icon system: [lucide-react](https://lucide.dev/)** is the single source. It's already a project dependency (`lucide-react: ^0.555.0`). Always import from it; never draw SVGs by hand.

Common icons used across the app (exact names — copy-paste):

- Navigation: `Briefcase` (Projekte), `FileCheck` (Anfragen), `ShoppingCart` (Bestellungen), `Calendar` (Kalender), `Clock` (Zeit), `Mail` (E-Mail), `MailPlus` (E-Mail Vorlagen), `Package` (Artikel), `Truck` (Lieferanten), `User` (Kunden/Mitarbeiter), `Building2` (Firma), `Home` (Miete), `Settings`, `Shield` (Berechtigungen), `Layers` (Kategorien), `List`, `FileText` (Rechnungen / Dokumente), `FileJson`, `BarChart3` (Analyse), `Euro`, `Receipt` (Belege & Kasse), `Wallet` (Kostenstellen), `Globe` (Neuigkeiten), `Plane` (Urlaub), `CalendarDays`.
- Controls: `ChevronUp/Down/Right`, `X`, `Check` (selected option), `MoreHorizontal`, `LogOut`.
- States: `ImageOff` (failed image tile).
- Brand moments: `Gem` — used anywhere the KI-Hilfe assistant shows up (ribbon, FAB, ai-button). Gem + rose-600 = "AI is here".

**Icon usage rules:**
- Stroke weight: Lucide default (1.5 / 2 px). Do not change.
- Sizes: `w-4 h-4` inline with text, `w-5 h-5` in nav tiles, `w-6 h-6` on empty-state hero.
- Color: inherits from the enclosing tile. Active nav icons are `text-rose-600` on `bg-rose-100`. Inactive are `text-slate-500` on `bg-slate-100`.
- Icons nearly always sit inside a **rounded circle or rounded-2xl tile** with a tinted background.

**Emoji:** only in README-style marketing content and a handful of console logs (`🌐 App ist online`). Don't use emoji in new UI.

**Unicode:** only mathematical (`×`, `∑`, `€`) and arrows (`→`) occasionally in labels. Always prefer a Lucide icon.

**Logos** (in `assets/`):
- `app_logo.png` — the app icon: a stylized hammer crossed over a gear tool, rendered in soft rose tones on a pale pink rounded-square tile. Warm, craft-forward, matches the rose brand.
- `firmenlogo_icon.png` — the tenant company logo shown in the ribbon. This is a placeholder that customers replace.
- `hicad_logo.png`, `tenado_logo.jpg`, `pdf_icon.jpg`, `excel_image.jpg` — file-association icons used next to linked CAD / Office files in the project view.

---

## Known caveats / open questions

- **No custom typeface.** The product relies on the system stack. If the user wants to mature the brand with a wordmark or display face, that's a design decision still to make.
- **Two slightly different reds.** Desktop uses `rose-600 (#e11d48)`. Mobile defines `--rose-primary: #dc2626` (Tailwind `red-600`). This system standardizes on **`#e11d48`** as brand; the mobile value is treated as a legacy variable that should converge.
- **No slide template was attached,** so no `slides/` directory is created.
- Several in-app illustrations (app_logo) are raster-only — vector versions would help the rebrand.
- **`AngebotEditor` is gone upstream.** Quotes now flow through `AnfrageEditor` and the new document overview. If a screen still says "Angebote", check which surface it belongs to before building on it.

---

## Working with this system

See **SKILL.md** for a ready-to-use Agent Skills entry point. For humans: pull `colors_and_type.css` into any prototype, use `assets/` for real logos, and build on top of the JSX components in `ui_kits/`.
