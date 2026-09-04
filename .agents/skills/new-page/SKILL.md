---
name: new-page
description: Checkliste und Vorlage für neue React-Seiten im ERP-System (Desktop oder Mobile).
---

# Neue React-Seite erstellen

## 1. Kategorie-Label bestimmen

Aus `.github/DEVELOPMENT.md` – Beispiele:

| Seite | Kategorie-Label |
|-------|----------------|
| Stammdaten (Kunden, Lieferanten) | Stammdaten |
| Projekte | Projektmanagement |
| Artikel/Katalog | Katalog |
| Rechnungen, Offene Posten | Buchhaltung |
| Angebote | Angebotsmanagement |
| Bestellungen | Einkauf |
| Zeiterfassung | Zeiterfassung |
| E-Mail | Kommunikation |
| Berichte/Analyse | Controlling |

---

## 2. Datei anlegen

**Desktop:** `react-pc-frontend/src/pages/{SeitenName}.tsx`
**Mobile:** `react-zeiterfassung/src/pages/{SeitenName}.tsx`

---

## 3. Pflichtstruktur (Template)

```tsx
import React, { useState, useEffect } from 'react';
import { Select } from '../components/ui/select-custom';
import { DatePicker } from '../components/ui/datepicker';
// Weitere Imports...

export default function MeineSeite() {
  return (
    <div className="p-6">
      {/* Page Header – PFLICHT auf jeder Seite */}
      <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
        <div>
          <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
            Kategorie-Label
          </p>
          <h1 className="text-3xl font-bold text-slate-900">
            SEITENTITEL IN UPPERCASE
          </h1>
          <p className="text-slate-500 mt-1">
            Kurze Beschreibung der Seite
          </p>
        </div>
        <div className="flex gap-2">
          {/* Primär-Button: bg-rose-600 text-white border border-rose-600 hover:bg-rose-700 */}
        </div>
      </div>

      {/* Seiteninhalt */}
    </div>
  );
}
```

---

## 4. Design-Checkliste

- [ ] Page Header mit Kategorie-Label (`text-rose-600 uppercase`), Titel (`text-3xl font-bold text-slate-900`), Beschreibung (`text-slate-500`)
- [ ] Farbschema: ausschließlich Rose/Slate – kein indigo/blue
- [ ] Dropdowns: `Select` aus `select-custom.tsx` (kein nativer `<select>`)
- [ ] Datumsfelder: `DatePicker` aus `datepicker.tsx` (kein `<input type="date">`)
- [ ] Vollbild-Bilder: `ImageViewer` aus `image-viewer.tsx`
- [ ] Detailansichten: `DetailLayout` verwenden
- [ ] E-Mail-Listen: `EmailHistory` importieren
- [ ] Karten: `GoogleMapsEmbed` importieren
- [ ] Buttons: primär `bg-rose-600`, sekundär `outline`, ghost für Toolbars
- [ ] Icons: Lucide React, `w-4 h-4`, links vom Text

---

## 5. Router-Eintrag

In der App-Router-Datei eintragen:
```tsx
<Route path="/meine-seite" element={<MeineSeite />} />
```

---

## 6. Navigation-Link (falls nötig)

In der Sidebar/Navigation den Link mit passendem Lucide-Icon hinzufügen.

---

## 7. Build-Verifikation

```bash
cd react-pc-frontend && npm run build
# oder
cd react-zeiterfassung && npm run build
```

- [ ] Build erfolgreich (kein TypeScript-Fehler, kein Lint-Fehler)
- [ ] Seite im Browser manuell prüfen
- [ ] Responsive auf mobilen Breakpoints prüfen (`md:` Klassen)
