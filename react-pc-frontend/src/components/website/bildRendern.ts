import { zeichne, type Bildbearbeitung } from './bildbearbeitung';

/**
 * Rendert ein bearbeitetes Bild in einen JPEG-Blob.
 *
 * Liegt in einer eigenen Datei, damit der Assistent im Test ohne Canvas
 * auskommt. jsdom liefert keinen 2D-Kontext, ein echter Aufruf schluege
 * dort fehl.
 */
export async function rendereBlob(
    bildUrl: string,
    bearbeitung: Bildbearbeitung,
    maxBreite: number,
    guete = 0.92,
): Promise<Blob> {
    const bild = await ladeBild(bildUrl);
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Der Browser stellt keine Bildbearbeitung bereit.');

    zeichne(ctx, bild, bearbeitung, maxBreite);

    return new Promise<Blob>((erfuellen, ablehnen) => {
        canvas.toBlob(
            blob => blob ? erfuellen(blob) : ablehnen(new Error('Bild konnte nicht erzeugt werden.')),
            'image/jpeg',
            guete,
        );
    });
}

function ladeBild(url: string): Promise<HTMLImageElement> {
    return new Promise((erfuellen, ablehnen) => {
        const img = new Image();
        img.onload = () => erfuellen(img);
        img.onerror = () => ablehnen(new Error(`Bild konnte nicht geladen werden: ${url}`));
        img.src = url;
    });
}
