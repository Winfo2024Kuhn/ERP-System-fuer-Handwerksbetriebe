/**
 * Wo die Hilfsdateien von PDF.js liegen – gemeinsam genutzt vom Build (`vite.config.ts`)
 * und von der Vorschau (`pdfjs.ts`), damit beide sicher denselben Pfad meinen.
 *
 * Bewusst ohne Abhängigkeiten: `vite.config.ts` läuft in Node und darf weder PDF.js
 * noch Browser-Code mitziehen.
 */

/** Bild-Decoder (z. B. für Fax-Bilder), Farbprofil, Zeichentabellen und Standardschriften. */
export const PDFJS_HILFSORDNER = ['wasm', 'iccs', 'cmaps', 'standard_fonts'] as const;

/**
 * Ordner der Hilfsdateien. Die Version steckt im Pfad, weil `/assets/` ein Jahr lang im
 * Browser-Cache bleibt – nach einem PDF.js-Update holt der Browser so die neuen Dateien.
 */
export function pdfjsHilfsordner(version: string): string {
    return `/assets/pdfjs-${version}/`;
}

/**
 * Ob eine Datei aus den Hilfsordnern mit ausgeliefert wird. Die Skript-Umgebung für
 * PDF-Formulare (QuickJS) bleibt draußen: Die Vorschau führt keine PDF-Skripte aus.
 */
export function istPdfjsHilfsdatei(datei: string): boolean {
    return !datei.startsWith('quickjs');
}

/**
 * Zerlegt eine angefragte URL in Ordner und Dateinamen einer Hilfsdatei.
 * `null` für alles andere – nur bekannte Ordner und reine Dateinamen, kein Weg nach oben.
 */
export function pdfjsHilfsdateiAusUrl(url: string, version: string): { ordner: string; datei: string } | null {
    const praefix = pdfjsHilfsordner(version);
    const pfad = url.split('?')[0];
    if (!pfad.startsWith(praefix)) return null;
    const [ordner, datei, ...rest] = pfad.slice(praefix.length).split('/');
    if (!(PDFJS_HILFSORDNER as readonly string[]).includes(ordner) || rest.length > 0) return null;
    if (!datei || datei === '.' || datei === '..' || /[\\:]/.test(datei)) return null;
    if (!istPdfjsHilfsdatei(datei)) return null;
    return { ordner, datei };
}
