# 🎨 Frontend & UI-Guidelines

## 🧠 KI-Skill-Pflicht (UI/UX) — HOOK-ERZWUNGEN

**Vor JEDER Frontend-Änderung** musst du einen der folgenden Skills per `Skill`-Tool aufrufen. Das ist keine Empfehlung: Der PreToolUse-Hook `.claude/hooks/check-doc-read.ps1` (auf macOS/Linux ohne `pwsh` die Bash-Fassung `check-doc-read.sh`, Auswahl über `run-hook.sh`) blockt deine Edit/Write-Aufrufe auf `react-pc-frontend/` und `react-zeiterfassung/` mit Exit 2 (DESIGN-SKILL-GUARD), solange in dieser Session keiner davon lief.

| Skill | Wofür |
| --- | --- |
| `handwerkerprogramm-design` | **Pflicht-Standard, immer zuerst.** Unser eigenes Design-System für dieses Produkt: Farben, Typografie, Icon-Vokabular (Lucide), Wording/Terminologie, fertige UI-Kits für Desktop-ERP + Mobile-PWA, echte Screenshots/Logos zum Wiederverwenden. Ersetzt keine der anderen Skills, geht ihnen aber vor. |
| `ui-ux-pro-max:ui-ux-pro-max` | Generisch. Styles, Farbpaletten, Typografie, 99 UX-Regeln (Accessibility, Touch, Performance, Animation) — nur ergänzend, wenn `handwerkerprogramm-design` etwas offenlässt |
| `frontend-design:frontend-design` | Visuelle Ausrichtung neuer UI, wenn es nicht nach Template aussehen soll |
| `ui-ux-pro-max:design-system` | Design-Tokens, Komponenten-Specs, systematische Skalen |
| `ui-ux-pro-max:design` | Logos, Banner, Icons, Präsentationen, Corporate Identity |

Das Flag gilt pro Session — ein Aufruf reicht für alle folgenden Frontend-Edits.

## 🧭 Kern-Prinzip: Gulf of Execution & Gulf of Evaluation

Jede UI-Entscheidung wird an diesen zwei Fragen gemessen. Beide MÜSSEN mit Ja beantwortbar sein, sonst ist die Umsetzung nicht fertig:

### Gulf of Execution — "Sieht der Nutzer sofort, WIE er sein Ziel erreicht?"
- Jede interaktive Fläche sieht klickbar aus (Cursor, Hover-State, Fokus-Ring). Nichts, was wie normaler Text aussieht, ist heimlich ein Button oder Link.
- Pro Screen genau **eine** klar erkennbare Primär-Aktion (gefüllter `bg-rose-600`-Button). Sekundäre und destruktive Aktionen sind visuell klar abgesetzt (siehe Button-Klassen unten).
- Icon-only-Buttons **immer** mit sichtbarem Tooltip/`aria-label` — nie ein Icon ohne Erklärung raten lassen.
- Deaktivierte Buttons/Felder erklären per Tooltip **warum** sie deaktiviert sind, statt einfach grau und stumm zu sein.
- Destruktive Aktionen (Löschen, Stornieren, unwiderrufliches Versenden) verlangen eine Bestätigung per Dialog (`useConfirm` aus `components/ui/confirm-dialog.tsx`, `variant: 'danger'`) — nie ein einzelner Klick, der sofort unwiderruflich ausführt.
  - Ausnahme (Nutzervorgabe 23.10.2026, Seite „Leistungen & Kategorien“): Hängt an einem Ordner oder einer Leistung nichts, wird ohne Dialog gelöscht – mit Toast „Rückgängig“ und erst danach gesendetem DELETE (`pages/leistungen/useVerzoegertesLoeschen.ts`). Hängt etwas daran, fragt ein Dialog nach und nennt die Bezüge.
- Formulare zeigen Pflichtfelder, Format-Hinweise (z. B. Datumsformat, Mengeneinheit) und Platzhalter **bevor** der Nutzer den Fehler macht, nicht erst danach in einer Fehlermeldung.

### Gulf of Evaluation — "Sieht der Nutzer sofort, WAS passiert ist?"
- Jede asynchrone Aktion (Speichern, Löschen, Senden, Import, Export) zeigt einen sichtbaren Ladezustand (Spinner/Skeleton, Button per `disabled` gegen Doppel-Klick gesperrt) **und danach** ein sichtbares Ergebnis. Nie "Klick ins Leere".
- Erfolg → Toast (`toast.success(...)`), Fehler → **immer** Toast (`toast.error(...)`) — siehe Abschnitt „Toast-Pflicht" unten. Kein stiller Fail, kein Fehler, der nur in der Browser-Konsole landet.
- Gespeicherte/geänderte Daten aktualisieren sichtbar die UI, ohne dass der Nutzer neu laden oder raten muss, ob es geklappt hat.
- Lade-Zustand, leere Liste und Fehler-Zustand sind drei optisch klar unterscheidbare Zustände — eine leere Liste darf nie wie ein kaputter Ladevorgang aussehen (und umgekehrt).

## 🍞 Toast-Pflicht bei Fehlern

**Jede fehlgeschlagene Aktion MUSS eine Toast-Fehlermeldung zeigen** — API-Fehler, Validierungsfehler, Netzwerkfehler, alles. Kein `alert()`, kein stiller `catch`-Block, kein Fehler, der nur per `console.error` verschwindet.

Nutze die vorhandene Komponente `src/components/ui/toast.tsx` (nicht neu bauen):

```tsx
import { useToast } from '../components/ui/toast'; // Pfad je nach Ordnertiefe anpassen

const toast = useToast();

try {
  await api.save(data);
  toast.success('Gespeichert.');
} catch (err) {
  toast.error(err instanceof Error ? err.message : 'Rechnung konnte nicht gespeichert werden.');
}
```

- Meldungstext ist konkret, deutsch, im Handwerker-Wording — nicht „Ein Fehler ist aufgetreten", sondern was genau fehlgeschlagen ist (z. B. „Rechnung konnte nicht gespeichert werden.").
- Erfolg wird nur getoastet, wenn er sonst nicht sichtbar ist (z. B. kein Redirect danach). Fehler werden **immer** getoastet, ausnahmslos.

## 🔌 MCP-Pflicht für Komponenten

Baue Komponenten **nicht von Hand nach**, wenn ein MCP-Server sie liefern kann. Beide sind in `.mcp.json` eingetragen:

| MCP | Wofür |
| --- | --- |
| `shadcn` | Der Standard. Greift direkt auf shadcn-Bausteine und -Blöcke zu, zieht sie passend ins Projekt und installiert sie automatisch. Kein manuelles Kopieren aus dem Netz. |
| `magic` (21st.dev) | Moderne Layouts mit Animationen auf Tailwind-Basis. Braucht die Umgebungsvariable `TWENTYFIRST_API_KEY` (**niemals** den Key in `.mcp.json` schreiben — die Datei ist eingecheckt). |

**Wichtig:** Gezogene Komponenten immer auf unser Design-System umstellen (rose/slate statt der shadcn-Default-Farben) und die Pflicht-Komponenten unten haben Vorrang vor neu gezogenen.

**Farbpaletten-Pflicht:** `ui-ux-pro-max`, `frontend-design` & Co. schlagen von sich aus oft fremde Paletten vor (blue/indigo/violet als Default-Theme). Diese Vorschläge sind nur Ausgangspunkt für Struktur, Spacing und Komponenten-Auswahl — Farben werden **immer** auf unser Schema unten (rose/slate) umgestellt, bevor Code committet wird. Kein Fallback auf Skill- oder MCP-Default-Farben, auch nicht "nur vorübergehend" oder in Mockups/Prototypen.

## Build & Coding-Regeln
- Nach JEDER Änderung: `npm run build` im jeweiligen Ordner ausführen (Fail Fast!). Der Build schreibt nach `src/main/resources/static/` (PC) bzw. `static/zeiterfassung/` (Handy-App). Diese Dateien sind eingecheckt und werden mitcommittet (siehe `review-and-ship`). Die CI (`.github/workflows/pr-ci.yml`) prüft je Frontend außerdem `npm run lint`, `npm test` und `npm run test:e2e`.
- Kein `dangerouslySetInnerHTML` ohne `sanitizeHtml()` aus `src/lib/htmlSanitizer.ts` (DOMPurify):
  - Gilt für jedes HTML aus Datenbank, API, KI oder Nutzereingabe, auch wenn das Backend es schon bereinigt hat. Bereinigt wird an der Stelle selbst oder sichtbar davor in derselben Datei (z. B. im `useMemo`, das die Vorschau baut).
  - Braucht eine Stelle engere Regeln, nimmt sie eine eigene DOMPurify-Instanz mit Positivliste (Vorbild `bereinigeBeitragsHtml` in `components/website/textumwandlung.ts`). Lockerer als `sanitizeHtml()` wird nie bereinigt; `DOMPurify.sanitize()` nicht direkt in Seiten aufrufen.
  - `EmailHtmlSanitizer` ist eine Backend-Klasse (Java) und bereinigt nur E-Mails. Im Frontend ersetzt sie `sanitizeHtml()` nicht. Fremdes E-Mail-HTML zeigt `EmailContentFrame` im iframe mit `sandbox` an.
  - Gleiches gilt für `element.innerHTML = …` und `insertAdjacentHTML` an angezeigten Elementen (z. B. contentEditable-Editoren).
  - HTML nur auslesen oder umbauen (Text, `querySelector`, `dataset`): `parseHtmlInert()` bzw. `htmlToText()`, nie `document.createElement('div').innerHTML = …`. In so einem div führt `<img onerror>` Code aus, auch wenn es nie angezeigt wird. Wird geparstes HTML danach wieder in die Seite geschrieben, gilt die Regel oben: vorher `sanitizeHtml()`.
  - Tags entfernen, Entities entpacken, URLs prüfen: ebenfalls nur über `src/lib/htmlSanitizer.ts` (siehe `TESTING_SECURITY.md`).
  - `react-zeiterfassung` hat kein `htmlSanitizer.ts` und derzeit keine HTML-Stelle. Vor der ersten das Gegenstück zur Freigabe stellen.
- URL-Parameter immer mit `encodeURIComponent()`.
- CSS-Klassen bedingt zusammensetzen mit `cn()` aus `src/lib/utils.ts`.
- Hierarchie:
  - `src/components/ui/`: Atome
  - `src/components/`: geteilte Fach-Bausteine
  - `src/features/{name}/`: Domänenlogik
  - `src/hooks/`: geteilte React-Hooks (z. B. `useHakenSpeichern` für Haken in Listen: Zeile sperren, Fehler-Toast, neu laden)
  - `src/pages/`: Seiten
  - `src/lib/`: reine Hilfsfunktionen

## Design-System (Handwerker-Fokus: Schlicht & Klar)
- **Farbschema:** Rose (ZWINGEND – kein indigo/blue, kein `red-*` als Primärfarbe).
- **Primärfarbe:** `rose-600` = `#e11d48` | Palette: `rose-50`–`rose-900` + `slate-50`–`slate-900`. `tailwind.config.js` überschreibt keine Farben; in der Zeiterfassung (Tailwind v4) ist `rose-600` ein OKLCH-Wert, die Klasse bleibt dieselbe. `#dc2626` ist `red-600` (Gefahr/Fehler), nicht die Primärfarbe.
- **Status-Farben:** nur für Zustände, nie als Markenfarbe. Erfolg `emerald`/`green`, Warnung `amber`, Gefahr/Löschen `red-600`. So machen es `toast.tsx` und `confirm-dialog.tsx` (`variant: 'danger'`).

### Button-Klassen
Immer `<Button>` aus `src/components/ui/button.tsx` (PC) verwenden; die Varianten bringen die Klassen mit:
- **Primär:** `<Button>` (Standard-Variante) = `bg-rose-600 text-white border border-rose-600 hover:bg-rose-700`
- **Sekundär:** `<Button variant="outline">` = `bg-white text-rose-700 border border-rose-200 hover:bg-rose-50`. Für einen kräftigeren Rand ist `className="border-rose-300 text-rose-700 hover:bg-rose-50"` verbreitet.
- **Ghost:** `variant="ghost"` = `text-rose-700 hover:bg-rose-100`
- **Neutral:** `variant="secondary"` = `bg-slate-100 text-slate-800`
- **Größe:** `size="sm"` setzen; die Komponente selbst hat `size="default"` als Vorgabe. Icons (`w-4 h-4`) links vom Text (Lucide React).

### Pflicht-Komponenten (NIE neu erfinden!)
PC-Frontend (`react-pc-frontend/src`):
- `<Button>`, `<Input>`, `<Label>`, `<Card>` -> `src/components/ui/button.tsx`, `input.tsx`, `label.tsx`, `card.tsx` (Grundbausteine)
- `<PageLayout>` -> `src/components/layout/PageLayout.tsx` (Seitenrahmen + Seitenkopf, siehe Page Header Pattern)
- `<Select>` -> `src/components/ui/select-custom.tsx`
- `<DatePicker>` -> `src/components/ui/datepicker.tsx`
- `<DecimalInput>` -> `src/components/ui/decimal-input.tsx` (Mengen/Beträge mit Dezimalkomma)
- `<TimeInput>` -> `src/components/ui/time-input.tsx` (Uhrzeit `HH:mm`)
- `<ColorInput>` -> `src/components/ui/color-input.tsx` (statt nativem Farbpicker)
- `Dialog`, `DialogContent`, `DialogHeader`, `DialogFooter`, `DialogTitle`, `DialogDescription` -> `src/components/ui/dialog.tsx`
- `useConfirm()` -> `src/components/ui/confirm-dialog.tsx` (Bestätigungsdialog statt `window.confirm`)
- `useToast()` -> `src/components/ui/toast.tsx` (siehe Toast-Pflicht)
- `<ImageViewer>` -> `src/components/ui/image-viewer.tsx`
- `<DetailLayout>` -> `src/components/DetailLayout.tsx` (2-Spalten-Layout)
- `<EmailsTab>` -> `src/components/EmailsTab.tsx` (E-Mail-Verlauf in Kunde, Projekt, Anfrage, Lieferant)
- `<GoogleMapsEmbed>` -> `src/components/GoogleMapsEmbed.tsx`
- `<DocumentPreviewModal>` -> `src/components/DocumentPreviewModal.tsx` (für PDFs)
- `<InfoButton>` -> `src/components/ui/info-button.tsx` (Erklärungen an komplexen Stellen, siehe unten)

Handy-App (`react-zeiterfassung/src`):
- `<Select>` -> `components/ui/select-custom.tsx`
- `<MobileDatePicker>` -> `components/MobileDatePicker.tsx`
- `<DecimalInput>` -> `components/ui/decimal-input.tsx`
- `useConfirm()` -> `components/ui/confirm-dialog.tsx`
- `useToast()` -> `components/ui/toast.tsx`
- `Drawer` (Bottom-Sheet) -> `components/ui/drawer.tsx`
- `<ImageViewer>` -> `components/ui/image-viewer.tsx`
- `<InfoButton>` -> `components/ui/info-button.tsx` (öffnet ein Bottom-Sheet, Gegenstück zum PC-Baustein)
- Nachrichten an den Mitarbeiter (Entscheidungen, Warnungen) gehören in die Glocke `features/nachrichten/NachrichtenGlocke.tsx`, nicht als Karte auf eine Seite

### Verbindliche Eingaben und Meldungen (Nutzervorgabe 09.09.2026)

- In beiden Frontends ausschließlich die eigenen gestalteten Eingaben, Toasts und Bestätigungsdialoge verwenden. Das gilt auch für geöffnete Auswahllisten und Picker; ein gestalteter Rahmen um einen nativen Browserdialog genügt nicht. Kein `alert`, `window.confirm`, `prompt` oder nativer Datums-, Uhrzeit-, Zahlen-Spinner- oder Farbpicker. Betriebssystem-Dateiauswahl und Berechtigungsdialoge bleiben echte Systemfunktionen.
- Mengen und Beträge als String bearbeiten und im deutschen Zahlenformat mit Dezimalkomma anzeigen und annehmen. Eine angezeigte `0` oder `0,00` beim Fokus durch Klick oder Tab leeren; andere Werte erhalten. Leere Zwischenstände dürfen beim Tippen bestehen bleiben und dürfen nicht durch `Number(value) || 0` ersetzt werden.
- Vor Speichern, Vorschau und Berechnen vollständig validieren, auch bei Aktionen ohne Formular. Leere Pflichtwerte, unvollständige Dezimalzahlen und verletzte Fachgrenzen mit eigener Meldung ablehnen. Erst nach erfolgreicher Prüfung in numerische API-Werte umwandeln; optional leere Felder nach ihrem fachlichen Vertrag behandeln.
- Kennnummern wie Personalnummern und Lohnarten bleiben Ziffernstrings mit führenden Nullen. Die Nullfokus- und Dezimalformatierung gilt für sie nicht. Uhrzeiten werden als `HH:mm` geprüft.
- Die gemeinsamen Bausteine unter `components/ui/` wiederverwenden. Geöffnete Picker, Tastaturbedienung, Fehler und Toasts vor offenen Dialogen im Browser prüfen.

### Zahlungsziel im Dokumenteditor (Nutzervorgabe 17.09.2026)

- Erlaubt sind **1 bis 365 Tage**. Alles andere wird mit eigener Meldung abgelehnt, der geltende Wert bleibt stehen.
- **Über 8 Tagen** fragt der Editor über den gemeinsamen Bestätigungsdialog (`useConfirm`) nach, bevor der Wert übernommen wird – ein langes Zahlungsziel heißt später an das eigene Geld kommen und soll eine bewusste Entscheidung sein.
- Die Eingabe wird erst beim **Abschluss** übernommen (Enter oder Feld verlassen, Escape verwirft), sonst stünde die Rückfrage bei jedem Tastendruck im Weg. Beide Eingabestellen – Summenzeile und Chip-Popover – nutzen dafür `components/document-editor/ZahlungszielTageEingabe.tsx`.
- Der Wert geht beim Speichern als `zahlungszielTage` mit ans Backend, damit Fälligkeit in Offenen Posten und Mahnwesen zur verschickten Rechnung passt.

### Gemeinsame Zahlenprüfung und Meldungsfläche

- PC: `lib/numberDrafts.ts` prüft mehrere Zahlenentwürfe vollständig vor der ersten Aktion. Fachliche Geldregeln liegen in `features/finanzen/moneyDrafts.ts`; keine Kopien in Seiten anlegen. Zulässige Genauigkeit folgt dem API-/Datenbankvertrag. Fehlende gespeicherte Zahlen ergeben einen leeren Entwurf, keinen erfundenen Nullwert.
- PC-Toasts schweben auf Nutzerwunsch vom 14.09.2026 als kompakte Meldungen oben rechts über der Anwendung. Sie reservieren keinen Platz und verschieben weder Anwendung noch Dialoge. Nur die Meldungskarten nehmen Zeigerereignisse an; der begrenzte Meldungsstapel bleibt scrollbar. Neue Dialoge bevorzugt mit dem gemeinsamen `Dialog` bauen und dessen Fokusverhalten wiederverwenden.
- Mobile-Toasts schweben auf Nutzerwunsch vom 13.10.2026 oben über dem Inhalt (unter der Safe-Area oben) und verschieben weder Seite noch Dialoge; es gibt keine reservierte Meldungsfläche mehr. Erfolg und Info verschwinden nach etwa 4 s, Warnung und Fehler bleiben bis zum X. Gleiche Meldungen werden nicht gestapelt. Weil Fehler-Toasts dabei Knöpfe am oberen Rand verdecken können, im E2E-Test die Meldung erst per X schließen und dann weiterbedienen. Hinweise nach einer Aktion als Toast zeigen, nicht als Karte, die den Inhalt nach unten schiebt.
- Toasts dürfen auch bei offenem Dialog per Tastatur erreichbar sein. Dabei bleibt der übrige Seitenhintergrund gesperrt; Escape aus der Meldungsfläche darf keinen ungespeicherten Dialog verwerfen.

### Fokus-Ringe nie abschneiden (Nutzervorgabe 29.09.2026)

- `ring-*`, `outline` und `shadow-*` liegen außerhalb der Box – ein Vorfahre mit `overflow-*` (Scroll-Listen mit `max-h-*`, Dialog-Inhalte, Tabellen-Wrapper) schneidet sie ab.
- Deshalb: Overflow-Container, deren Kinder einen Ring/Schatten bekommen, innen Luft geben und außen ausgleichen (`-m-1 p-1` bei `ring-2`), oder `ring-inset` am Element. Nicht `overflow` entfernen, nicht den Fokus-Ring weglassen.
- Im Playwright-Screenshot erstes und letztes Element gewählt/fokussiert prüfen. Details: `.agents/rules/frontend.md`.

### Info-Buttons an komplexen Stellen (Nutzervorgabe 10.10.2026)

Der Nutzer möchte bei komplexeren Dingen **immer** einen Info-Button, der erklärt, was im Hintergrund passiert und welche Auswirkungen etwas hat. Gilt für jede Frontend-Arbeit – neue Seiten, Dialoge, Einstellungen, auch bei kleineren Änderungen an solchen Stellen.

- **Wann:** Einstellungen mit Folgen für andere Bereiche (Export, Lohn, Glocke, Rechte, Versand), Aktionen, die etwas im Hintergrund auslösen oder nicht rückgängig zu machen sind, unvermeidbare Fachbegriffe (Lohnart, Personalnummer, Soll/Ist). **Nicht** bei selbsterklärenden Feldern (Name, Telefon) – zu viele (i) machen die Seite unruhig.
- **Wo:** direkt neben dem Feld bzw. der Überschrift, auf die sich die Info bezieht – nie gesammelt am Seitenende oder auf einer separaten Hilfeseite.
- **Inhalt:** zwei kurze Teile in Handwerker-Sprache: „Was passiert im Hintergrund?“ und „Was bewirkt das?“ – inklusive dessen, was sich *nicht* ändert (z. B. „bereits abgeschlossene Monate bleiben unverändert“). Wenige Sätze, keine internen Begriffe (Endpoint, Cache, Tabelle). Der Text muss zum tatsächlichen Backend-Verhalten passen – im Zweifel im Backend-Code nachsehen.
- **Baustein:** immer `<InfoButton>` aus `src/components/ui/info-button.tsx` (Klick öffnet ein Popover, Tastatur + Touch, Escape schließt) – nicht pro Seite neu bauen und kein reiner Hover-Tooltip. In `react-zeiterfassung/` das Gegenstück `components/ui/info-button.tsx` (Bottom-Sheet) nutzen.
- Im Playwright-Durchlauf den geöffneten Info-Button mit prüfen: nichts abgeschnitten, nichts überdeckt, auch am Bildschirmrand.

### Page Header Pattern (Zwingend für alle Seiten)
Im PC-Frontend erzeugt `<PageLayout ribbonCategory="Kategorie" title="Seitentitel" subtitle="Beschreibung" actions={…}>` aus `src/components/layout/PageLayout.tsx` genau diesen Kopf. Der Titel wird dabei automatisch großgeschrieben. Dazu kommt der Standard-Seitenrahmen (`max-w-[1600px]`, Innenabstände). Neue Seiten nutzen `PageLayout`. Nur wo das nicht passt, das Muster von Hand:
```tsx
<div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
  <div>
    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Kategorie</p>
    <h1 className="text-3xl font-bold text-slate-900">SEITENTITEL</h1>
    <p className="text-slate-500 mt-1">Beschreibung</p>
  </div>
  <div className="flex gap-2">{/* Buttons */}</div>
</div>
