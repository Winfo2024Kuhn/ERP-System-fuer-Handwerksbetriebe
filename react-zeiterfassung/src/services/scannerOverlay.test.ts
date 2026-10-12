import { describe, it, expect, vi, beforeEach } from 'vitest'
import { quadsAreClose, clearOverlay, drawOverlayQuad } from './scannerOverlay'

// Pixel-Koordinaten eines Quads (TL, TR, BR, BL) – Dummy-Daten, kein PII.
const baseQuad = [
    { x: 100, y: 100 },
    { x: 500, y: 100 },
    { x: 500, y: 700 },
    { x: 100, y: 700 },
]

describe('quadsAreClose', () => {
    it('liefert true für identische Quads', () => {
        expect(quadsAreClose(baseQuad, baseQuad, 1)).toBe(true)
    })

    it('liefert true, wenn jede Ecke innerhalb der Toleranz liegt', () => {
        const shifted = baseQuad.map(p => ({ x: p.x + 2, y: p.y - 1 }))
        // sqrt(2^2 + 1^2) ≈ 2.24, also liegt 3 px Toleranz drüber
        expect(quadsAreClose(baseQuad, shifted, 3)).toBe(true)
    })

    it('liefert false, sobald eine einzige Ecke außerhalb der Toleranz liegt', () => {
        const shifted = baseQuad.map((p, i) => i === 2 ? { x: p.x + 50, y: p.y } : p)
        expect(quadsAreClose(baseQuad, shifted, 10)).toBe(false)
    })

    it('liefert false bei exakter Übereinstimmung minus Epsilon (Grenzfall)', () => {
        const shifted = baseQuad.map((p, i) => i === 0 ? { x: p.x + 5.01, y: p.y } : p)
        expect(quadsAreClose(baseQuad, shifted, 5)).toBe(false)
    })

    it('liefert true an exakter Toleranz-Grenze', () => {
        const shifted = baseQuad.map((p, i) => i === 0 ? { x: p.x + 5, y: p.y } : p)
        expect(quadsAreClose(baseQuad, shifted, 5)).toBe(true)
    })

    it('liefert false bei zu kurzen Arrays', () => {
        expect(quadsAreClose(baseQuad, baseQuad.slice(0, 3), 1)).toBe(false)
        expect(quadsAreClose([], [], 1)).toBe(false)
    })
})

describe('clearOverlay', () => {
    it('wirft nicht, wenn Canvas null ist', () => {
        expect(() => clearOverlay(null)).not.toThrow()
    })

    it('ruft clearRect auf der vollen Canvas-Fläche auf', () => {
        const canvas = document.createElement('canvas')
        canvas.width = 800
        canvas.height = 600
        const ctx = canvas.getContext('2d')!
        const spy = vi.spyOn(ctx, 'clearRect')
        clearOverlay(canvas)
        expect(spy).toHaveBeenCalledWith(0, 0, 800, 600)
    })
})

describe('drawOverlayQuad', () => {
    let canvas: HTMLCanvasElement
    let ctx: CanvasRenderingContext2D

    beforeEach(() => {
        canvas = document.createElement('canvas')
        canvas.width = 10
        canvas.height = 10
        ctx = canvas.getContext('2d')!
    })

    it('wirft nicht, wenn Canvas null ist', () => {
        expect(() => drawOverlayQuad(null, 800, 600, baseQuad, false)).not.toThrow()
    })

    it('passt die Canvas-Auflösung an die Video-Dimensionen an', () => {
        drawOverlayQuad(canvas, 800, 600, baseQuad, false)
        expect(canvas.width).toBe(800)
        expect(canvas.height).toBe(600)
    })

    it('zeichnet einen geschlossenen Pfad inkl. Eckmarker für tracking-Zustand', () => {
        const strokeSpy = vi.spyOn(ctx, 'stroke')
        const fillSpy = vi.spyOn(ctx, 'fill')
        const arcSpy = vi.spyOn(ctx, 'arc')
        drawOverlayQuad(canvas, 800, 600, baseQuad, false)
        expect(strokeSpy).toHaveBeenCalled()
        // einmal fill für das Polygon + 4× fill für die Eckpunkte
        expect(fillSpy.mock.calls.length).toBeGreaterThanOrEqual(1)
        expect(arcSpy).toHaveBeenCalledTimes(4)
    })

    it('verwendet einen anderen Stroke-Style im locking-Zustand', () => {
        drawOverlayQuad(canvas, 800, 600, baseQuad, false)
        const trackingStroke = ctx.strokeStyle
        drawOverlayQuad(canvas, 800, 600, baseQuad, true)
        const lockingStroke = ctx.strokeStyle
        expect(trackingStroke).not.toEqual(lockingStroke)
    })

    it('zeichnet den locking-Rahmen in der Primärfarbe rose-600, nicht in red-600', () => {
        drawOverlayQuad(canvas, 800, 600, baseQuad, true)
        expect(String(ctx.strokeStyle).toLowerCase()).toBe('#e11d48')
    })
})
