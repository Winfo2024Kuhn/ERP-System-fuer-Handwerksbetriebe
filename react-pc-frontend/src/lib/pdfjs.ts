/**
 * PDF.js für die Canvas-Vorschau – lokal ausgeliefert und erst beim ersten PDF geladen.
 *
 * Früher kam PDF.js synchron aus dem CDN im <head>: Jeder Seitenaufruf wartete darauf, auch
 * ohne PDF, und ohne CDN-Zugang lief die Vorschau nicht. Jetzt lädt der erste Aufruf Bibliothek
 * und Worker als eigene Datei vom ERP selbst; danach liegt beides im Browser-Cache.
 *
 * Die Legacy-Fassung läuft auch auf älteren Browsern (z. B. Safari auf dem Büro-iPad). Sie ist
 * größer, wird aber nur beim Öffnen einer PDF geladen.
 */
export type PdfjsLib = typeof import('pdfjs-dist/legacy/build/pdf.mjs');

let laden: Promise<PdfjsLib | null> | null = null;

async function importiere(): Promise<PdfjsLib | null> {
    try {
        const [lib, worker] = await Promise.all([
            import('pdfjs-dist/legacy/build/pdf.mjs'),
            import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'),
        ]);
        lib.GlobalWorkerOptions.workerSrc = worker.default;
        return lib;
    } catch (fehler) {
        // Kein Toast: Die Aufrufer fallen auf die Browser-eigene PDF-Anzeige zurück.
        console.warn('PDF.js konnte nicht geladen werden – verwende Browser-Vorschau:', fehler);
        laden = null; // Beim nächsten PDF erneut versuchen (z. B. nach einem Funkloch).
        return null;
    }
}

/** Lädt PDF.js einmalig nach. `null`, wenn das Laden scheitert – dann bleibt die iframe-Vorschau. */
export function ladePdfjs(): Promise<PdfjsLib | null> {
    laden ??= importiere();
    return laden;
}
