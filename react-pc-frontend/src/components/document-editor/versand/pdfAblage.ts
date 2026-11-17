import { fehlermeldungAusAntwort } from '../eRechnungPruefung';
import type { PdfFormat } from '../EmailFormatDialog';

/** Gemeinsame PDF-Schritte für Drucken, Herunterladen und E-Mail-Versand. */

export function pdfEndpunkt(format: PdfFormat): string {
    return format === 'zugferd' ? '/api/dokument-generator/zugferd-pdf' : '/api/dokument-generator/pdf';
}

/** Lässt das PDF erzeugen; wirft mit der lesbaren Meldung des Servers. */
export async function erzeugePdf(endpunkt: string, anfrage: unknown): Promise<Blob> {
    const response = await fetch(endpunkt, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(anfrage),
    });
    if (!response.ok) throw new Error(await fehlermeldungAusAntwort(response, 'PDF-Generierung fehlgeschlagen'));
    return response.blob();
}

/**
 * Legt das PDF beim Dokument ab (Offene Posten, Freigabe-Link). Ein Netzfehler
 * hält den Versand nicht auf: Ergebnis ist dann null, der Aufrufer entscheidet.
 */
export async function legePdfAb(dokumentId: number, pdf: Blob): Promise<Response | null> {
    try {
        return await fetch(`/api/ausgangs-dokumente/${dokumentId}/pdf-speichern`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/pdf' },
            body: await pdf.arrayBuffer(),
        });
    } catch (e) {
        console.warn('PDF konnte nicht serverseitig gespeichert werden:', e);
        return null;
    }
}

export function ladePdfHerunter(pdf: Blob, dateiname: string): void {
    const url = window.URL.createObjectURL(pdf);
    const a = document.createElement('a');
    a.href = url;
    a.download = dateiname;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
}

/** Druckt das PDF über ein unsichtbares iframe (das vorherige wird ersetzt). */
export function druckePdf(pdf: Blob): void {
    const alt = document.getElementById('print-iframe');
    if (alt) document.body.removeChild(alt);

    const iframe = document.createElement('iframe');
    iframe.id = 'print-iframe';
    iframe.style.position = 'fixed';
    iframe.style.left = '-9999px';
    iframe.style.width = '0';
    iframe.style.height = '0';
    iframe.src = URL.createObjectURL(pdf);
    document.body.appendChild(iframe);
    iframe.addEventListener('load', () => {
        iframe.contentWindow?.focus();
        iframe.contentWindow?.print();
    });
}
