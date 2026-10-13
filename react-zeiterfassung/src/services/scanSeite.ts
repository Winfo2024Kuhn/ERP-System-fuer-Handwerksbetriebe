/**
 * Macht aus einem Kamerafoto und vier Ecken eine fertige Scan-Seite:
 * perspektivisch geradeziehen (zuschneiden) und anschließend filtern.
 *
 * Bewusst getrennt: `schneideZu` liefert das ungefilterte Ergebnis. So kann
 * der Nutzer den Filter umschalten, ohne dass neu zugeschnitten werden muss.
 */
import type { Point } from './scannerOverlay'
import { wendeScanFilterAn, type ScanFilter } from './scanFilter'
import { canvasZuBlob, gibCanvasFrei } from './scanBildHilfen'

/** 2480 px ≈ A4 bei 200 dpi – reicht für Texterkennung und Ausdruck. */
const MAX_AUSGABE_PX = 2480
/** Größere Quellbilder vorher verkleinern (≈ 8–10 MP bleiben erhalten). */
const MAX_QUELLE_PX = 3500

/**
 * Löst die Homographie, die Zielpunkte (u, v) auf Quellpunkte (x, y) abbildet:
 * x = (h0·u + h1·v + h2) / (h6·u + h7·v + 1), y analog mit h3..h5.
 */
export function loeseHomographie(quelle: Point[], ziel: Point[]): number[] {
    const a: number[][] = []
    const b: number[] = []
    for (let i = 0; i < 4; i++) {
        const { x: u, y: v } = ziel[i]
        const { x, y } = quelle[i]
        a.push([u, v, 1, 0, 0, 0, -x * u, -x * v])
        a.push([0, 0, 0, u, v, 1, -y * u, -y * v])
        b.push(x, y)
    }

    // Gauß-Elimination mit Spalten-Pivotsuche
    const n = 8
    for (let i = 0; i < n; i++) {
        let maxZeile = i
        for (let k = i + 1; k < n; k++) {
            if (Math.abs(a[k][i]) > Math.abs(a[maxZeile][i])) maxZeile = k
        }
        ;[a[i], a[maxZeile]] = [a[maxZeile], a[i]]
        ;[b[i], b[maxZeile]] = [b[maxZeile], b[i]]
        for (let k = i + 1; k < n; k++) {
            const c = -a[k][i] / a[i][i]
            for (let j = i; j < n; j++) a[k][j] = i === j ? 0 : a[k][j] + c * a[i][j]
            b[k] += c * b[i]
        }
    }

    const h = new Array<number>(n).fill(0)
    for (let i = n - 1; i >= 0; i--) {
        let summe = 0
        for (let j = i + 1; j < n; j++) summe += a[i][j] * h[j]
        h[i] = (b[i] - summe) / a[i][i]
    }
    return [...h, 1]
}

/** Zielgröße der geradegezogenen Seite aus den vier Ecken (TL, TR, BR, BL). */
export function zielGroesse(ecken: Point[], maxPx = MAX_AUSGABE_PX): { breite: number; hoehe: number } {
    const [tl, tr, br, bl] = ecken
    const breite = Math.max(Math.hypot(tr.x - tl.x, tr.y - tl.y), Math.hypot(br.x - bl.x, br.y - bl.y))
    const hoehe = Math.max(Math.hypot(bl.x - tl.x, bl.y - tl.y), Math.hypot(br.x - tr.x, br.y - tr.y))
    const skala = Math.min(1, maxPx / breite, maxPx / hoehe)
    return { breite: Math.max(1, Math.floor(breite * skala)), hoehe: Math.max(1, Math.floor(hoehe * skala)) }
}

/**
 * Zieht den Bereich innerhalb der Ecken perspektivisch gerade (bilineare
 * Interpolation). Ecken in Pixeln von `quelle` (naturalWidth/Height bzw.
 * Canvas-Größe), Reihenfolge TL, TR, BR, BL.
 */
export function schneideZu(quelle: HTMLImageElement | HTMLCanvasElement, ecken: Point[]): HTMLCanvasElement {
    if (ecken.length !== 4) throw new Error('Für das Zuschneiden werden genau vier Ecken gebraucht.')
    const quelleB = quelle instanceof HTMLImageElement ? quelle.naturalWidth : quelle.width
    const quelleH = quelle instanceof HTMLImageElement ? quelle.naturalHeight : quelle.height
    const quellSkala = Math.min(1, MAX_QUELLE_PX / quelleB, MAX_QUELLE_PX / quelleH)
    const srcW = Math.floor(quelleB * quellSkala)
    const srcH = Math.floor(quelleH * quellSkala)

    const srcCanvas = document.createElement('canvas')
    srcCanvas.width = srcW
    srcCanvas.height = srcH
    const srcCtx = srcCanvas.getContext('2d', { willReadFrequently: true })
    if (!srcCtx) throw new Error('Bild konnte nicht gelesen werden.')
    srcCtx.drawImage(quelle, 0, 0, srcW, srcH)
    const src = srcCtx.getImageData(0, 0, srcW, srcH).data
    // iOS begrenzt den Canvas-Speicher; die Pixel stecken jetzt in `src`.
    gibCanvasFrei(srcCanvas)

    const skalierteEcken = ecken.map(p => ({ x: p.x * quellSkala, y: p.y * quellSkala }))
    const { breite, hoehe } = zielGroesse(skalierteEcken)
    const ziel = document.createElement('canvas')
    ziel.width = breite
    ziel.height = hoehe
    const zielCtx = ziel.getContext('2d')
    if (!zielCtx) throw new Error('Bild konnte nicht erzeugt werden.')
    const zielBild = zielCtx.createImageData(breite, hoehe)
    const dst = zielBild.data

    const h = loeseHomographie(skalierteEcken, [
        { x: 0, y: 0 }, { x: breite, y: 0 }, { x: breite, y: hoehe }, { x: 0, y: hoehe },
    ])

    for (let v = 0; v < hoehe; v++) {
        for (let u = 0; u < breite; u++) {
            const nenner = h[6] * u + h[7] * v + 1
            const sx = (h[0] * u + h[1] * v + h[2]) / nenner
            const sy = (h[3] * u + h[4] * v + h[5]) / nenner
            const x0 = Math.floor(sx)
            const y0 = Math.floor(sy)
            const ziel4 = (v * breite + u) * 4
            if (x0 < 0 || x0 >= srcW - 1 || y0 < 0 || y0 >= srcH - 1) {
                dst[ziel4] = dst[ziel4 + 1] = dst[ziel4 + 2] = 255
                dst[ziel4 + 3] = 255
                continue
            }
            const dx = sx - x0
            const dy = sy - y0
            const i00 = (y0 * srcW + x0) * 4
            const i10 = i00 + 4
            const i01 = i00 + srcW * 4
            const i11 = i01 + 4
            for (let c = 0; c < 3; c++) {
                const oben = src[i00 + c] * (1 - dx) + src[i10 + c] * dx
                const unten = src[i01 + c] * (1 - dx) + src[i11 + c] * dx
                dst[ziel4 + c] = oben * (1 - dy) + unten * dy
            }
            dst[ziel4 + 3] = 255
        }
    }
    zielCtx.putImageData(zielBild, 0, 0)
    return ziel
}

/**
 * Wendet den Filter auf die zugeschnittene (ungefilterte) Seite an und
 * liefert das fertige JPEG für die PDF-Seite. `zugeschnitten` bleibt
 * unverändert, damit der Filter später gewechselt werden kann.
 */
export async function erzeugeSeitenBild(zugeschnitten: HTMLCanvasElement, filter: ScanFilter): Promise<Blob> {
    const ausgabe = document.createElement('canvas')
    try {
        ausgabe.width = zugeschnitten.width
        ausgabe.height = zugeschnitten.height
        const ctx = ausgabe.getContext('2d', { willReadFrequently: true })
        if (!ctx) throw new Error('Bild konnte nicht erzeugt werden.')
        ctx.drawImage(zugeschnitten, 0, 0)
        if (filter !== 'original') {
            const bild = ctx.getImageData(0, 0, ausgabe.width, ausgabe.height)
            bild.data.set(wendeScanFilterAn(bild, filter))
            ctx.putImageData(bild, 0, 0)
        }
        return await canvasZuBlob(ausgabe, 0.92)
    } finally {
        gibCanvasFrei(ausgabe)
    }
}
