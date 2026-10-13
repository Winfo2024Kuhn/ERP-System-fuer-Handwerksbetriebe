/**
 * Baut aus den gescannten Seiten (JPEG, in Reihenfolge) ein PDF.
 * Jede PDF-Seite bekommt genau das Seitenverhältnis ihres Scans – sonst
 * würden Sonderformate (Tankbons, schmale Kassenzettel) auf A4-Breite
 * gestreckt und unten abgeschnitten. Die Apple-Scanner-App macht es genauso.
 */
import { jsPDF } from 'jspdf'

/** Längsseite jeder PDF-Seite in mm (A4-Höhe); die kurze Seite folgt dem Seitenverhältnis. */
const LAENGSSEITE_MM = 297

export interface SeitenGeometrie {
    /** Immer in Hochformat-Reihenfolge [kurz, lang] – so verlangt es jsPDF. */
    format: [number, number]
    orientation: 'p' | 'l'
    bildBreite: number
    bildHoehe: number
}

/**
 * jsPDF-Eigenheit: Das `format`-Array MUSS in Hochformat-Reihenfolge
 * ([kurz, lang]) stehen, `orientation` entscheidet über Quer-/Hochformat.
 * Wer [lang, kurz] + 'l' übergibt, bekommt eine intern gedrehte Seite, und
 * `addImage(0, 0, w, h)` zeichnet dann über den Seitenrand hinaus.
 */
export function seitenGeometrie(breitePx: number, hoehePx: number): SeitenGeometrie {
    const lang = LAENGSSEITE_MM
    const kurz = (Math.min(breitePx, hoehePx) / Math.max(breitePx, hoehePx)) * LAENGSSEITE_MM
    const quer = breitePx >= hoehePx
    return {
        format: [kurz, lang],
        orientation: quer ? 'l' : 'p',
        bildBreite: quer ? lang : kurz,
        bildHoehe: quer ? kurz : lang,
    }
}

function leseAlsDataUrl(blob: Blob, seite: number): Promise<string> {
    return new Promise((resolve, reject) => {
        const leser = new FileReader()
        leser.onloadend = () => resolve(leser.result as string)
        leser.onerror = () => reject(new Error(`Seite ${seite} konnte nicht gelesen werden.`))
        leser.readAsDataURL(blob)
    })
}

/** Eine fertige Seite fürs PDF: JPEG und seine Pixelmaße (vom Zuschneiden bekannt, kein erneutes Dekodieren). */
export interface PdfSeite {
    bild: Blob
    breite: number
    hoehe: number
}

/**
 * Erzeugt das PDF als Datei mit Zeitstempel im Namen. Die Seiten werden
 * nacheinander eingelesen, damit nie alle Bilder gleichzeitig als Text im
 * Speicher liegen.
 */
export async function erstelleScanPdf(seiten: PdfSeite[], jetzt: Date = new Date()): Promise<File> {
    if (seiten.length === 0) throw new Error('Es gibt noch keine Seite für das PDF.')

    let pdf: jsPDF | null = null
    for (const [index, seite] of seiten.entries()) {
        if (!(seite.breite > 0) || !(seite.hoehe > 0)) throw new Error(`Seite ${index + 1} hat keine gültige Größe.`)
        const dataUrl = await leseAlsDataUrl(seite.bild, index + 1)
        const g = seitenGeometrie(seite.breite, seite.hoehe)
        if (!pdf) pdf = new jsPDF({ orientation: g.orientation, unit: 'mm', format: g.format })
        else pdf.addPage(g.format, g.orientation)
        pdf.addImage(dataUrl, 'JPEG', 0, 0, g.bildBreite, g.bildHoehe)
    }

    const pdfBlob = (pdf as jsPDF).output('blob')
    const dateiname = `Scan_${jetzt.toISOString().replace(/[:.]/g, '-')}.pdf`
    return new File([pdfBlob], dateiname, { type: 'application/pdf' })
}
