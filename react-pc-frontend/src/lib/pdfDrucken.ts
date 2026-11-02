import { toSafeResourceUrl } from './htmlSanitizer';

/**
 * Drucken von PDFs ohne sichtbare Vorschau: Das echte PDF (nicht eine
 * Canvas-Kopie) landet in einem unsichtbaren iframe, dessen Druckdialog
 * geöffnet wird. Gemeinsam genutzt von `PdfCanvasViewer` (Drucken-Knopf in der
 * Vorschau) und Listen mit einem „Drucken“-Knopf je Zeile.
 */

/** Beginnt der Inhalt wie eine PDF-Datei (`%PDF-`)? Schützt davor, HTML-Fehlerseiten als PDF zu behandeln. */
export async function istPdfBlob(blob: Blob): Promise<boolean> {
    try {
        if (!blob || blob.size < 5) return false;
        const headerBuffer = await blob.slice(0, 5).arrayBuffer();
        const header = new TextDecoder('latin1').decode(headerBuffer);
        return header.startsWith('%PDF-');
    } catch {
        return false;
    }
}

/** Aufräumfrist, falls der Browser kein `afterprint` meldet – großzügig, damit niemand mitten im Druckdialog abgeschnitten wird. */
const DRUCK_FENSTER_FRIST_MS = 10 * 60 * 1000;

/**
 * Öffnet den Druckdialog des Browsers für ein PDF – bewusst außerhalb des React-Lifecycles.
 *
 * Das unsichtbare iframe hängt direkt am `document.body` und überlebt deshalb, wenn die
 * Vorschau während des Druckdialogs geschlossen wird (z.B. Escape im Modal). Aufgeräumt
 * wird erst nach `afterprint` – vorher würde der Browser den Druck abbrechen.
 *
 * @param eigeneUrl true, wenn `src` eine eigens erzeugte Object-URL ist, die hier wieder freigegeben werden muss.
 */
export function oeffneDruckFenster(src: string, eigeneUrl: boolean): void {
    const frame = document.createElement('iframe');
    frame.setAttribute('aria-hidden', 'true');
    frame.style.position = 'fixed';
    frame.style.right = '0';
    frame.style.bottom = '0';
    frame.style.width = '0';
    frame.style.height = '0';
    frame.style.border = '0';
    frame.style.visibility = 'hidden';

    let fertig = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const aufraeumen = () => {
        if (fertig) return;
        fertig = true;
        if (timer !== undefined) clearTimeout(timer);
        frame.remove();
        if (eigeneUrl) URL.revokeObjectURL(src);
    };
    const fristStellen = () => {
        if (timer !== undefined) clearTimeout(timer);
        timer = setTimeout(aufraeumen, DRUCK_FENSTER_FRIST_MS);
    };

    frame.onload = () => {
        const win = frame.contentWindow;
        if (!win) { aufraeumen(); return; }
        win.addEventListener('afterprint', aufraeumen, { once: true });
        fristStellen();
        try {
            win.focus();
            win.print();
        } catch (fehler) {
            // Kein Toast hier – reine Hilfsfunktion ohne solche Abhängigkeit.
            // Der Hinweis in der Konsole verhindert immerhin, dass ein stiller Fehlschlag unbemerkt bleibt.
            console.warn('Drucken nicht möglich:', fehler);
            aufraeumen();
        }
    };
    const sichereSrc = toSafeResourceUrl(src);
    if (!sichereSrc) {
        aufraeumen();
        return;
    }
    frame.src = sichereSrc;
    document.body.appendChild(frame);
    // Bewusst schon hier scharfstellen: Bleibt `onload` aus (z.B. Content-Disposition
    // "attachment" oder ein geblockter Frame), gäbe es sonst nie ein Aufräumen.
    fristStellen();
}

/**
 * Lädt das PDF hinter `url` und öffnet dafür den Druckdialog.
 * Wirft einen `Error` mit einem Satz für die Nutzer, wenn das nicht geht.
 */
export async function druckePdfAusUrl(url: string): Promise<void> {
    let antwort: Response;
    try {
        antwort = await fetch(url);
    } catch {
        throw new Error('Keine Verbindung zum Server. Das Dokument konnte nicht gedruckt werden.');
    }
    if (!antwort.ok) throw new Error('Das Dokument konnte nicht geladen werden.');
    const blob = await antwort.blob();
    if (!(await istPdfBlob(blob))) throw new Error('Die Datei ist kein lesbares PDF und kann nicht gedruckt werden.');
    oeffneDruckFenster(URL.createObjectURL(blob), true);
}
