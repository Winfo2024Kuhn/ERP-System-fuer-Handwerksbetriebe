/**
 * Pure, stateless overlay-/geometrie-helfer für die Live-Dokument-Erkennung
 * im Scanner. Hier ausgelagert (statt in ScannerModal.tsx), damit sie per
 * Vitest direkt testbar sind und der react-refresh-Lintchecker nicht
 * meckert (nur Komponenten-Exports in .tsx-Dateien erlaubt).
 */

export interface Point {
    x: number
    y: number
}

/**
 * Vergleicht zwei Quads (je 4 Ecken in identischer Reihenfolge) auf
 * räumliche Nähe. Liefert `true`, wenn JEDE Ecke innerhalb von
 * `tolerancePx` zur korrespondierenden Ecke des anderen Quads liegt.
 */
export function quadsAreClose(a: Point[], b: Point[], tolerancePx: number): boolean {
    if (!Array.isArray(a) || !Array.isArray(b) || a.length !== 4 || b.length !== 4) return false
    for (let i = 0; i < 4; i++) {
        if (Math.hypot(a[i].x - b[i].x, a[i].y - b[i].y) > tolerancePx) return false
    }
    return true
}

export function clearOverlay(canvas: HTMLCanvasElement | null) {
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.clearRect(0, 0, canvas.width, canvas.height)
}

export function drawOverlayQuad(
    canvas: HTMLCanvasElement | null,
    videoW: number,
    videoH: number,
    quad: Point[],
    locking: boolean,
) {
    if (!canvas) return
    // Interne Canvas-Auflösung an Video anpassen (object-cover zeigt das
    // dann identisch zum Video-Stream).
    if (canvas.width !== videoW || canvas.height !== videoH) {
        canvas.width = videoW
        canvas.height = videoH
    }
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.clearRect(0, 0, videoW, videoH)

    // Farbschema gemäß FRONTEND_UI.md: nur rose + slate. Locking = rose-600
    // (Primärfarbe, Aktion bestätigt), Tracking = neutrales Hellgrau, damit
    // der "Halte still"-Zustand klar vom Auslöser unterscheidbar bleibt.
    const stroke = locking ? '#e11d48' : '#e2e8f0'
    const fill = locking ? 'rgba(225,29,72,0.20)' : 'rgba(226,232,240,0.14)'

    ctx.beginPath()
    ctx.moveTo(quad[0].x, quad[0].y)
    for (let i = 1; i < 4; i++) ctx.lineTo(quad[i].x, quad[i].y)
    ctx.closePath()
    ctx.fillStyle = fill
    ctx.fill()
    ctx.strokeStyle = stroke
    ctx.lineWidth = Math.max(6, videoW / 240)
    ctx.stroke()

    // Eck-Marker
    ctx.fillStyle = stroke
    const r = Math.max(10, videoW / 150)
    for (const p of quad) {
        ctx.beginPath()
        ctx.arc(p.x, p.y, r, 0, Math.PI * 2)
        ctx.fill()
    }
}

export interface GlaettungsOptionen {
    /**
     * Wie stark der neue Wert zählt (0 < anteilNeu ≤ 1). Kleiner = ruhiger,
     * aber träger. 0,4 folgt einer Handbewegung noch flüssig, schluckt aber
     * das Zittern der Erkennung von Bild zu Bild.
     */
    anteilNeu?: number
    /**
     * Springt eine Ecke weiter als diese Strecke (Pixel), wird das neue
     * Viereck sofort übernommen – dann liegt ein anderes Blatt im Bild, und
     * ein langsam hinterherziehender Rahmen würde nur verwirren.
     */
    sprungPx: number
}

/**
 * Glättet das erkannte Viereck zwischen zwei Kamerabildern (exponentielles
 * Glätten je Ecke), damit der Live-Rahmen ruhig steht statt zu zittern.
 * Ohne Vorgänger oder bei einem großen Sprung zählt sofort das neue Viereck.
 * Liefert immer ein neues Array; die Eingaben bleiben unverändert.
 */
export function glaetteQuad(vorher: Point[] | null, neu: Point[], { anteilNeu = 0.4, sprungPx }: GlaettungsOptionen): Point[] {
    const anteil = Math.min(1, Math.max(0, anteilNeu))
    if (!vorher || vorher.length !== neu.length || !quadsAreClose(vorher, neu, sprungPx)) {
        return neu.map(p => ({ x: p.x, y: p.y }))
    }
    return neu.map((p, i) => ({
        x: vorher[i].x + (p.x - vorher[i].x) * anteil,
        y: vorher[i].y + (p.y - vorher[i].y) * anteil,
    }))
}

/** Warum sich ein Viereck nicht zum Zuschneiden eignet. */
export type ViereckFehler = 'ueberkreuzt' | 'zu-klein'

/** Fläche eines Vielecks (Gaußsche Trapezformel). */
function flaeche(ecken: Point[]): number {
    let summe = 0
    for (let i = 0; i < ecken.length; i++) {
        const a = ecken[i]
        const b = ecken[(i + 1) % ecken.length]
        summe += a.x * b.y - b.x * a.y
    }
    return Math.abs(summe) / 2
}

/**
 * Prüft die vier Ecken (TL, TR, BR, BL) vor dem Zuschneiden: Sie müssen ein
 * konvexes Viereck bilden (keine überkreuzten oder eingedellten Kanten) und
 * mindestens `minAnteil` der Bildfläche abdecken. Liefert `null`, wenn alles
 * passt, sonst den Grund.
 */
export function viereckFehler(ecken: Point[], bildBreite: number, bildHoehe: number, minAnteil = 0.01): ViereckFehler | null {
    if (ecken.length !== 4) return 'ueberkreuzt'
    let vorzeichen = 0
    for (let i = 0; i < 4; i++) {
        const a = ecken[i]
        const b = ecken[(i + 1) % 4]
        const c = ecken[(i + 2) % 4]
        const kreuz = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        if (kreuz === 0) return 'ueberkreuzt'
        const s = Math.sign(kreuz)
        if (vorzeichen === 0) vorzeichen = s
        else if (s !== vorzeichen) return 'ueberkreuzt'
    }
    if (flaeche(ecken) < bildBreite * bildHoehe * minAnteil) return 'zu-klein'
    return null
}

/** Kurzform: Taugt das Viereck zum Zuschneiden? */
export function istGueltigesViereck(ecken: Point[], bildBreite: number, bildHoehe: number, minAnteil = 0.01): boolean {
    return viereckFehler(ecken, bildBreite, bildHoehe, minAnteil) === null
}
