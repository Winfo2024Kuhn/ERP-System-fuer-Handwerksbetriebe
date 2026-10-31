---
name: handwerkerprogramm-design
description: Der EINE Frontend-Skill für Handwerkerprogramm (react-pc-frontend, react-zeiterfassung). Pflicht vor jeder Frontend-Arbeit, vom Hook erzwungen. Enthält Design-System (Rose/Slate, Lucide, Wording), Bedienbarkeit (Gulf of Execution/Evaluation, Nielsen-Heuristiken), Bewegung (Animations-Entscheidungen, Dauer, Kurven), Handwerk-Feinschliff, Frontend-Sicherheit und die Playwright-Design-Prüfung. Bringt UI-Kits für Desktop und Handy, Design-Tokens und echte Screenshots mit.
user-invocable: true
---

# Frontend-Standard für Handwerkerprogramm

Das ist der einzige Frontend-Skill. Der Frontend-Agent liest ihn vollständig, bevor er etwas baut. Er ersetzt die früheren Skills `ui-ux-pro-max`, `playwright-design-pruefung` sowie die Kernideen aus `emil-design-eng` und `design-taste-frontend`.

Vorrang bei Widersprüchen: **`docs/agent instructions/docs/FRONTEND_UI.md`** (Pflicht-Komponenten, Regeln) > dieser Skill > Geschmack. Die Landingpage-Regeln aus `design-taste-frontend` (keine Schatten, keine Rundungen, große Hero-Typo) gelten hier **nicht**: Das ERP ist eine dichte Arbeitsoberfläche, keine Werbeseite.

Dateien in diesem Ordner:
- `README.md`: Produktkontext, Wording, visuelle Grundlagen, Navigation, Icons
- `colors_and_type.css`: Design-Tokens (Farben, Schrift, Abstände, Schatten, Radien, Bewegung, Chips, PDF-Maße)
- `assets/`: echte Logos, Datei-Icons, Screenshots (wiederverwenden, nicht nachzeichnen)
- `ui_kits/desktop-erp/`, `ui_kits/mobile-zeiterfassung/`: React-Nachbauten zum Abgucken
- `preview/`: kleine Muster-Karten
- `PRUEFUNG.md`: Playwright-Design-Prüfung (Pflicht nach jeder Frontend-Änderung)

## 1. Design-System (nicht verhandelbar)

1. **Nur Deutsch.** Alle UI-Texte in Handwerker-Sprache („Kundenrechnungen“, nicht „Debitoren“). Etablierte Begriffe nie übersetzen (Angebot, Rechnung, Nachkalkulation, KI-Hilfe, Zeiterfassung).
2. **Markenfarbe `rose-600` (#e11d48)**, sparsam: Primärknopf, aktive Navigation, Seiten-Eyebrow, KI-Momente. Alles andere Slate. Statusfarben nur für Zustände (emerald Erfolg, amber Warnung, red-600 Gefahr).
3. **Icons nur Lucide.** Nie SVGs von Hand. `Gem` kennzeichnet KI-Momente.
4. **Seitenkopf** immer als PageHeader: Eyebrow + Titel + Untertitel + Aktionen.
5. **Kein Emoji** in der Produkt-UI.
6. **Systemschrift**, keine Webfonts.
7. **`rounded-lg`** ist Standard, `rounded-2xl` für Dialoge, `rounded-3xl` für Handy-Sheets.
8. **`shadow-sm`** für Karten, `shadow-lg` bei Hover, Glühen nur bei Hero/Kennzahl.
9. **Drei Chip-Arten:** indigo Platzhalter, gelb Vorschauwert, rose *anklickbares* Zahlungsziel. Nur das letzte ist klickbar.
10. **Nie eine leere Box beim Laden.** Skeleton (`motion-safe:animate-pulse`) → einblenden → `ImageOff`-Kachel bei Fehler.
11. **Animation hinter `motion-safe:`**; Icon-Knöpfe mit `aria-label` (+ `aria-expanded` beim Auf-/Zuklappen).
12. **PDF-Maße des Dokumenteditors nicht nach Gefühl ändern** (10pt / 1.3 / 2pt spiegelt den PDF-Dienst).
13. **Info-Button an erklärungsbedürftigen Stellen** (Nutzerregel 10.10.2026): wo eine Einstellung anderswo wirkt, im Hintergrund etwas auslöst, nicht rückgängig zu machen ist oder einen Fachbegriff braucht. Gemeinsamer `<InfoButton>` aus `components/ui/info-button.tsx`, zwei kurze Teile: „Was passiert im Hintergrund?“ und „Was bewirkt das?“. Nie nur Hover, nie gesammelt am Seitenende, keiner an Selbstverständlichem.

Immer zuerst Bestehendes nutzen: Pflicht-Komponenten aus `FRONTEND_UI.md` (`Select`, `DatePicker`, `Button`, `DetailLayout`, `useConfirm`, `useToast` …) und die MCP-Server `shadcn` für fehlende Bausteine.

## 2. Bedienbarkeit: jede UI-Entscheidung gegen diese Fragen

Beide Kluften müssen mit **Ja** beantwortbar sein, sonst ist die Umsetzung nicht fertig. Die ausführlichen Regeln stehen in `FRONTEND_UI.md`.

- **Gulf of Execution:** Sieht der Nutzer sofort, *wie* er ans Ziel kommt? Eine Primäraktion pro Screen, Klickbares sieht klickbar aus, Icon-Knöpfe haben Tooltip, deaktivierte Knöpfe erklären *warum*, Löschen fragt nach, Pflichtfelder und Formate stehen *vorher* da.
- **Gulf of Evaluation:** Sieht der Nutzer sofort, *was passiert ist*? Ladezustand, danach Ergebnis; Erfolg und **jeder** Fehler als Toast; Daten aktualisieren sich sichtbar; Laden, leer und Fehler sehen verschieden aus.

Die zehn Nielsen-Heuristiken als Prüfliste (Handwerker-Fassung):

1. **Sichtbarkeit des Systemstatus:** Der Nutzer weiß jederzeit, was gerade läuft (Spinner, Fortschritt, „Gespeichert“).
2. **Sprache der echten Welt:** Wörter des Handwerkers, keine Buchhalter- oder SAP-Begriffe („Kunde“ statt „Debitor“).
3. **Nutzerkontrolle und Freiheit:** Jeder Dialog hat „Abbrechen“, Aktionen lassen sich rückgängig machen (Toast „Rückgängig“), Esc schließt.
4. **Konsistenz und Standards:** Gleiche Sache, gleiche Komponente und gleiches Wort überall. Nicht pro Seite eine Sonderlösung.
5. **Fehlervermeidung:** Besser verhindern als melden: Knopf gesperrt, bis Pflichtfelder gefüllt sind; Format automatisch; Bestätigung bei Unwiderruflichem.
6. **Wiedererkennen statt Erinnern:** Namen in Auswahllisten statt IDs zum Abtippen; zuletzt Benutztes anbieten; nichts, was man sich merken muss.
7. **Flexibilität und Effizienz:** Häufiges ist schnell (Tastatur, Vorschläge, Vorbelegung); Seltenes darf einen Klick tiefer liegen.
8. **Ästhetisches, minimalistisches Design:** Nur, was jetzt gebraucht wird. Eine Primäraktion, ruhige Flächen, kein Schmuck.
9. **Fehler erkennen, verstehen, beheben:** Fehlermeldung sagt in einfachen Worten, *was* schiefging und *was* man tun kann („Rechnung konnte nicht gespeichert werden. Kunde fehlt.“).
10. **Hilfe und Dokumentation:** Hilfe an der Stelle, nicht im Handbuch: `InfoButton` neben dem Feld, Platzhalter und Beispiele im Feld.

## 3. Bewegung (aus der Design-Engineering-Schule, auf das ERP zugeschnitten)

Das ERP wird acht Stunden am Tag benutzt. Bewegung ist hier Rückmeldung, nie Show.

**Erst entscheiden, ob überhaupt:**

| Wie oft sieht der Nutzer es? | Entscheidung |
| --- | --- |
| Dutzende Male am Tag (Listen, Tabs, Hover, Tastatur) | keine oder fast keine Animation |
| Gelegentlich (Dialog, Schublade, Toast) | kurze Standard-Animation |
| Selten (erstes Mal, Erfolgsmoment) | darf ein bisschen Freude haben |

Durch Tastatur ausgelöste Aktionen nie animieren. Jede Bewegung braucht einen Grund: Rückmeldung (Knopf wurde gedrückt), Raumgefühl (Toast kommt von dort, wo er wieder hingeht), Zustandswechsel, oder verhindern, dass etwas hart aufspringt.

**Werte:**
- Dauer: Knopf-Druck 100–160 ms, Tooltip 125–200 ms, Dropdown 150–250 ms, Dialog/Schublade 200–300 ms. **Alles über 300 ms ist zu langsam.** Ausblenden schneller als Einblenden.
- Kurve: Einblenden und Ausblenden `ease-out`, Bewegung auf dem Bildschirm `ease-in-out`, Hover/Farbe `ease`. **Nie `ease-in`** (wirkt träge). Die eingebauten Kurven sind schwach: stärkere nutzen, z. B. `cubic-bezier(0.23, 1, 0.32, 1)`.
- Druck-Rückmeldung: `active:scale-[0.97]` an jedem drückbaren Element.
- Nie bei `scale(0)` starten: ab `scale-95` plus `opacity-0`. Popover wachsen aus ihrem Auslöser (`transform-origin`), Dialoge bleiben mittig.
- Nur `transform` und `opacity` animieren, nie `width`, `height`, `margin`, `padding`. Nie `transition-all`, immer die Eigenschaft nennen (`transition-[transform,opacity]`).
- Für Dinge, die schnell hintereinander ausgelöst werden (Toasts, Umschalter): CSS-Transitions statt Keyframes, weil sie sich unterbrechen lassen.
- Mehrere Elemente gleichzeitig: leicht staffeln (30–80 ms), nie die Bedienung dabei blockieren.
- Hover-Effekte nur mit `@media (hover: hover) and (pointer: fine)`, sonst bleiben sie auf dem Handy hängen (Tailwind: `hover:` wirkt in v4 bereits so, in v3 `[@media(hover:hover)]:`).
- `prefers-reduced-motion`: Bewegung weg, Überblenden bleibt (`motion-safe:` / `motion-reduce:`).

## 4. Handwerk-Feinschliff (aus dem Taste-Skill, nur das Passende)

- **Ein Raster, eine Abstandsskala.** Abstände nur aus der Tailwind-Skala, gleiche Ränder überall, Text bündig an wenigen gemeinsamen Linien. Nichts klebt am Rand, nichts schwebt verloren auf 1920 px.
- **Typografie:** wenige Größen, die Hierarchie trägt (Titel, Abschnitt, Text, Hinweis). Fließtext in voller Tinte (`slate-900`/`slate-700`), Grau nur für Nebensächliches. Zahlen in Tabellen mit `tabular-nums`, rechtsbündig.
- **Flächen trennen** durch einen kleinen Farbschritt (`slate-50` auf weiß), nicht durch mehr Linien und Rahmen.
- **Handy ist eine eigene Komposition**, nicht die verkleinerte PC-Seite: Daumenzone, mindestens 44 × 44 px Tippfläche, nur oben Safe-Area, unten nichts (siehe Handy-PWA-Regeln im Projekt-Gedächtnis).
- **Zustände vollständig bauen:** Laden, leer, Fehler, gesperrt, Fokus, aktiv, deaktiviert. Echte Links und Knöpfe, sichtbarer Fokus-Ring (nie abschneiden), Alt-Texte.
- **Ein Wiedererkennungsmoment pro Seite** reicht (zum Beispiel die Primäraktion in Rose). Kein Verlauf, keine Glasflächen, keine dekorativen Formen, keine Platzhalter-Dashboards.
- **Mit Worst-Case-Daten prüfen:** sehr langer Firmenname, leere Felder, 0 und 500 Einträge, Umlaute, große Beträge.

## 5. Frontend-Sicherheit (nur Frontend)

Details und Begründung: Abschnitt „Sicherheit im Frontend“ in `FRONTEND_UI.md`. Kurzfassung:

- HTML anzeigen: nur über `sanitizeHtml()` (`src/lib/htmlSanitizer.ts`), auch bei serverseitig bereinigten Daten. Gilt auch für `innerHTML` und `insertAdjacentHTML`.
- Tags entfernen: `stripHtmlTags()`. Entities entpacken: `unescapeHtmlEntities()`. URLs für `<iframe>`/`<img>`: `toSafeResourceUrl()`. HTML nur auslesen: `parseHtmlInert()`/`htmlToText()`.
- URL-Parameter mit `encodeURIComponent()`. Keine Geheimnisse im Frontend-Code, keine echten Personendaten in Tests (`Max Mustermann`).

## 6. Prüfen und abgeben

Nach jeder Frontend-Änderung: `PRUEFUNG.md` lesen und befolgen (Spec für den geänderten Ablauf, Screenshots in 1440 und 1920 bzw. Handy, sechs Fragen schriftlich beantworten). Danach `npm run lint`, `npm run build`, gezielte Tests. Nicht committen: das erledigt der Hauptagent im gemeinsamen `review-and-ship`.

Ohne weitere Vorgabe fragt dieser Skill bei Prototypen und Mockups zuerst, was gebaut werden soll, und liefert dann HTML-Artefakte oder Produktionscode.
