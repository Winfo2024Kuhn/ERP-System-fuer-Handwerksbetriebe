# Frontend & UI-Guidelines

**Vor jeder Frontend-Änderung zusätzlich lesen:** `.claude/skills/handwerkerprogramm-design/README.md` — unser eigenes Design-System (Farben, Typografie, Icon-Vokabular, Wording/Terminologie, UI-Kits für Desktop-ERP + Mobile-PWA). Die Kurzfassung unten reicht für kleine Änderungen, für alles Neue/Sichtbare zählt das Design-System.

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

## Fokus-Ringe und Rahmen nie abschneiden (Nutzervorgabe 29.09.2026)
Ist in letzter Zeit mehrfach passiert: Ein gewähltes oder fokussiertes Element in einer Scroll-Liste bekommt einen `ring-2`, und die Liste schneidet ihn oben, links und rechts ab – es sieht aus, als fehle der Rahmen.

- **Ursache:** `ring-*`, `outline`, `ring-offset-*` und `shadow-*` werden *außerhalb* der Box gezeichnet. Jeder Vorfahre mit `overflow-hidden`, `overflow-auto` oder `overflow-y-auto` (Listen mit `max-h-*`, Dialog-Inhalte, Tabellen-Wrapper, Karten mit `rounded-* overflow-hidden`) schneidet alles ab, was über seine Innenkante ragt.
- **Regel:** Wer einem Kind in einem Overflow-Container einen Ring oder Schatten gibt, gibt dem Container innen genau so viel Luft und gleicht außen aus, damit sich das Layout nicht verschiebt:
  ```tsx
  // ring-2 → 1 Einheit (4 px) Luft; bei ring-offset-2 entsprechend p-2 -m-2
  <div className="-m-1 max-h-60 space-y-2 overflow-y-auto p-1">…</div>
  ```
  Alternativ am Element `ring-inset` (Ring nach innen) – dann ist keine Luft nötig.
- **Nicht** als Lösung: `overflow` entfernen (die Liste wächst dann aus dem Dialog) oder den Fokus-Ring weglassen (Barrierefreiheit).
- **Prüfen:** Im Playwright-Screenshot das **erste und das letzte** Element der Liste gewählt bzw. per Tab fokussiert zeigen und bei 14-Zoll-Größe hineinzoomen – der Rahmen muss rundum geschlossen sein.

## Build & Coding-Regeln
- Nach Änderungen: `npm test` und `npm run build` im jeweiligen Frontend-Ordner ausführen.
- Kein `dangerouslySetInnerHTML` ohne Sanitizing.
- URL-Parameter immer mit `encodeURIComponent()`.
- Hierarchie: `src/components/ui/` (wiederverwendbare UI-Atome), `src/features/{name}/` (Domänenlogik).
