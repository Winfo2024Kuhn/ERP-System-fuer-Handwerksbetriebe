/**
 * Kleine Bild-Helfer für den Dokumentenscanner: Bilder laden, Canvas ↔ Blob
 * umwandeln und Canvas-Speicher freigeben. Alles läuft auf dem Gerät.
 */

/** Lädt ein Bild (Object-URL oder Data-URL) und wartet, bis es dekodiert ist. */
export function ladeBild(src: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
        const bild = new Image()
        bild.onload = () => resolve(bild)
        bild.onerror = () => reject(new Error('Bild konnte nicht geladen werden.'))
        bild.src = src
    })
}

/** Speichert eine Canvas als JPEG. */
export function canvasZuBlob(canvas: HTMLCanvasElement, qualitaet = 0.95): Promise<Blob> {
    return new Promise((resolve, reject) => {
        canvas.toBlob(blob => {
            if (blob) resolve(blob)
            else reject(new Error('Bild konnte nicht gespeichert werden.'))
        }, 'image/jpeg', qualitaet)
    })
}

/** Zeichnet ein gespeichertes Bild wieder in eine neue Canvas. */
export async function blobZuCanvas(blob: Blob): Promise<HTMLCanvasElement> {
    const url = URL.createObjectURL(blob)
    try {
        const bild = await ladeBild(url)
        const canvas = document.createElement('canvas')
        canvas.width = bild.naturalWidth
        canvas.height = bild.naturalHeight
        const ctx = canvas.getContext('2d')
        if (!ctx) throw new Error('Bild konnte nicht gelesen werden.')
        ctx.drawImage(bild, 0, 0)
        return canvas
    } finally {
        URL.revokeObjectURL(url)
    }
}

/**
 * Gibt den Speicher einer Canvas sofort frei. iOS Safari begrenzt den
 * gesamten Canvas-Speicher; bei mehrseitigen Scans in voller Auflösung
 * ist die Grenze sonst schnell erreicht.
 */
export function gibCanvasFrei(canvas: HTMLCanvasElement): void {
    canvas.width = 0
    canvas.height = 0
}
