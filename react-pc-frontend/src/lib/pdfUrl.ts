/**
 * PDF-Erkennung anhand der URL: greift alle üblichen Muster ab
 * (Dateiendung, API-Pfade, Download-Endpoints).
 *
 * Bewusst tolerant – Aufrufer, die den echten Dateityp kennen (z.B. aus `dateityp`),
 * sollten diese Heuristik über einen expliziten Parameter überstimmen, damit z.B.
 * DOCX/XLSX unter `/dokumente/` nicht fälschlich als PDF behandelt werden.
 */
export function isPdfUrl(url: string): boolean {
    // Query-String/Hash strippen, damit Endungen + Pfade unabhängig von Parametern matchen
    // (Beispiel: /api/.../mahnung-vorschau?stufe=ZAHLUNGSERINNERUNG)
    const path = url.toLowerCase().split('?')[0].split('#')[0];
    return path.includes('.pdf') ||
        path.includes('/dokumente/') ||
        path.includes('/attachments/') ||
        path.includes('/download') ||
        path.endsWith('/pdf') ||
        path.includes('vorschau');
}
