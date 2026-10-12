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
