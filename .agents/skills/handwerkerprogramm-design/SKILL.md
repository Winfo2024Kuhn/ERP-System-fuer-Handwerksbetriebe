---
name: handwerkerprogramm-design
description: Design and improve Handwerkerprogramm interfaces using the existing Rose/Slate components, German input conventions, reusable UI behavior, and accessible dialogs and notifications. Applies to the desktop ERP, mobile time tracking, and product prototypes.
---

Für Produktionsänderungen zuerst die [Frontend-Richtlinien](../../../docs/agent%20instructions/docs/FRONTEND_UI.md) lesen. Die vorhandenen Komponenten in `react-pc-frontend/src/components/ui/` und `react-zeiterfassung/src/components/ui/` sind die verbindliche Implementierung. Dieser Skill funktioniert auch ohne zusätzliche lokale Design-Assets.

Optional locally available design resources (use when present and relevant):
- `README.md` — product context, content fundamentals, visual foundations, navigation map, iconography
- `colors_and_type.css` — CSS custom properties for colors, type, spacing, shadows, radii, motion, chips, PDF metrics
- `assets/` — real logos, file-type icons and product screenshots (use these, don't redraw)
- `ui_kits/desktop-erp/` — React/JSX recreation of the desktop ERP (ribbon nav, PageHeader, tables, dialogs, KI chat)
- `ui_kits/mobile-zeiterfassung/` — React/JSX recreation of the mobile PWA (dashboard, time tracking, bottom nav, sheets)
- `preview/` — small design-system specimen cards

For visual artifacts, reuse available product assets; do not invent a replacement logo. For production code, use the existing application components and the rules below.

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

## Wiederverwendung: verbindliche Nutzervorgabe vom 09.09.2026

Wiederkehrende UI und Verhalten immer in gemeinsame Komponenten, Hooks oder Hilfsfunktionen auslagern und wiederverwenden. Das erforderliche Refactoring ist vom Nutzer freigegeben. Insbesondere Eingaben, Zahlen-/Uhrzeitvalidierung, Auswahlfelder, Toasts und Bestätigungsdialoge zentral implementieren; keine abweichenden Kopien pro Seite. Vorhandene Bausteine zuerst nutzen oder gezielt erweitern. Fachliche Unterschiede über klare Schnittstellen erhalten.

## Eingabefelder: verbindliche Nutzervorgabe vom 09.09.2026

- Vorhandene gestaltete UI-Komponenten verwenden: `src/components/ui/input.tsx`, `select-custom.tsx` und `datepicker.tsx` im PC-Frontend. Auch Zahlen- und Uhrzeitfelder müssen vollständig zum eigenen Design-System passen. Keine sichtbaren nativen Browser-Spinner, Standard-Uhrzeit-/Datumspicker oder ungestalteten Dropdowns einsetzen. Bloßes Einfärben des äußeren Feldrands reicht nicht, wenn sich innen weiterhin Browser-Standardbedienelemente öffnen.
- Vor einer neuen Eingabekomponente vorhandene Bausteine prüfen. Im PC-Frontend bestehen `DecimalInput` (`components/ui/decimal-input.tsx`), `TimeInput` (`components/ui/time-input.tsx`) und `ColorInput` (`components/ui/color-input.tsx`). Mengen über `lib/numberInput.ts`, Uhrzeiten über `validateTimeInput` prüfen. Diese Bausteine wiederverwenden und fachliche Prüfungen gemeinsam kapseln. Eine Uhrzeiteingabe im verständlichen `HH:mm`-Format muss tastaturbedienbar und vollständig validiert sein; keine unnötige neue Komponentenbibliothek einführen.
- Mengen-/Dezimalfelder: Eine anfänglich sichtbare 0 (auch `0,00`) beim Fokus per Klick oder Tab leeren, damit direkt getippt werden kann. Nichtnullwerte erhalten. Während des Tippens leere/unvollständige Zwischenstände als Text zulassen, keine erzwungene Rücksetzung auf 0.
- Zahlen im deutschen Format eingeben und anzeigen: Dezimalkomma, formatierte Anzeigen mit `de-DE`. Pflichtfelder erst bei Übernahme auf vollständige Zahlen und fachliche Grenzen prüfen; leer ist nicht automatisch 0. Kennnummern wie Personalnummern und Lohnarten bleiben Ziffernstrings mit führenden Nullen, ohne Dezimal-/Tausenderformat und ohne Nullwert-Löschung.
- **Alle Meldungen im eigenen Design:** Den gemeinsamen Toast-Provider für Hinweise, Erfolg und Fehler verwenden; Bestätigungen und notwendige Texteingaben mit eigenen Dialogen darstellen. Kein `alert`, `confirm` oder `prompt` des Browsers. Fehler dürfen nicht nur in der Konsole landen. Erforderliche Angaben vor dem Speichern prüfen und den Fehler verständlich am Feld oder im Dialog anzeigen. Betriebssystem-Dialoge für Dateien, Kamera und Berechtigungen bleiben davon ausgenommen.
- **Bedienbarkeit vor bloßer Optik:** Toasts dürfen keine Aktionen verdecken. Dialoginhalt muss bei kleinen Höhen scrollen können, während Überschrift und Aktionen erreichbar bleiben. Bei gestapelten Dialogen den Hintergrund sperren und den Fokus im aktiven Dialog halten. Tastaturbedienung und sichtbaren Fokus erhalten.
- Bei der Browserprüfung auch geöffnete und fokussierte Eingabefelder ansehen: keine nativen Picker/Spinner, sichtbarer Fokus im Design-System, Nullwert per Klick/Tab leer, Nichtnullwert unverändert, Kommaeingabe und verständliche Pflichtfeldfehler.

## Arbeitsweise und Geschmack des Nutzers

- Vorhandenes schlichtes, modernes Handwerkerprogramm-Design konsequent fortführen; eigene Rose-/Slate-Komponenten statt generischer Browseroptik. Keine neue Designwelt pro Seite.
- Vollständig funktionierende Abläufe liefern: Eingabe, Validierung, Speichern, Fehlerfall und erneutes Öffnen berücksichtigen. Eine optisch passende Oberfläche allein genügt nicht.
- Wiederkehrende Muster zentral verbessern, damit alle Verwendungen profitieren. Mengen und Kennnummern sowie optionale und erforderliche Felder fachlich unterscheiden.
- Änderungen im Browser in den vorgesehenen Bildschirmgrößen prüfen, einschließlich aufgeklappter Auswahl, Fokus, leerer Pflichtfelder, Kommawerten und Meldungen. Screenshots nach Ende von Animationen beurteilen; verdeckte oder unerreichbare Aktionen tatsächlich korrigieren.

If the user invokes this skill without any other guidance, ask them what they want to build or design, ask some focused questions, and act as an expert designer who outputs HTML artifacts _or_ production code, depending on the need.
