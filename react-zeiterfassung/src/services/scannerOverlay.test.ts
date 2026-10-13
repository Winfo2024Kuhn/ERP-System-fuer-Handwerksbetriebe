import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { quadsAreClose, clearOverlay, drawOverlayQuad, glaetteQuad, istGueltigesViereck, viereckFehler } from './scannerOverlay'

// Pixel-Koordinaten eines Quads (TL, TR, BR, BL) – Dummy-Daten, kein PII.
const baseQuad = [
    { x: 100, y: 100 },
    { x: 500, y: 100 },
    { x: 500, y: 700 },
    { x: 100, y: 700 },
]

/**
 * jsdom zeichnet ohne die native `canvas`-Bibliothek nicht (die CI installiert
 * sie nicht). Deshalb pro Canvas ein nachgebauter 2D-Kontext, der Aufrufe und
 * gesetzte Farben festhält.
 */
const kontexte = new WeakMap<HTMLCanvasElement, CanvasRenderingContext2D>()
beforeEach(() => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(function (this: HTMLCanvasElement) {
        let ctx = kontexte.get(this)
        if (!ctx) {
            ctx = {
                clearRect: vi.fn(), beginPath: vi.fn(), moveTo: vi.fn(), lineTo: vi.fn(), closePath: vi.fn(),
                fill: vi.fn(), stroke: vi.fn(), arc: vi.fn(),
                fillStyle: '', strokeStyle: '', lineWidth: 1,
            } as unknown as CanvasRenderingContext2D
            kontexte.set(this, ctx)
        }
        return ctx
    } as never)
})
afterEach(() => vi.restoreAllMocks())

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

describe('glaetteQuad', () => {
    it('übernimmt ohne Vorgänger das neue Viereck als Kopie', () => {
        const ergebnis = glaetteQuad(null, baseQuad, { sprungPx: 50 })
        expect(ergebnis).toEqual(baseQuad)
        expect(ergebnis).not.toBe(baseQuad)
        expect(ergebnis[0]).not.toBe(baseQuad[0])
    })

    it('zieht jede Ecke nur anteilig zum neuen Wert (exponentielles Glätten)', () => {
        const neu = baseQuad.map(p => ({ x: p.x + 10, y: p.y - 20 }))
        const ergebnis = glaetteQuad(baseQuad, neu, { anteilNeu: 0.5, sprungPx: 50 })
        expect(ergebnis[0]).toEqual({ x: 105, y: 90 })
        expect(ergebnis[2]).toEqual({ x: 505, y: 690 })
    })

    it('nutzt 0,4 als Standard-Anteil', () => {
        const neu = baseQuad.map(p => ({ x: p.x + 10, y: p.y }))
        expect(glaetteQuad(baseQuad, neu, { sprungPx: 50 })[0].x).toBeCloseTo(104)
    })

    it('übernimmt das neue Viereck sofort, wenn eine Ecke weiter als sprungPx springt', () => {
        const neu = baseQuad.map((p, i) => (i === 1 ? { x: p.x + 80, y: p.y } : p))
        expect(glaetteQuad(baseQuad, neu, { anteilNeu: 0.2, sprungPx: 50 })).toEqual(neu)
    })

    it('übernimmt das neue Viereck, wenn der Vorgänger unvollständig ist', () => {
        expect(glaetteQuad(baseQuad.slice(0, 3), baseQuad, { sprungPx: 50 })).toEqual(baseQuad)
    })

    it('begrenzt den Anteil auf 0 bis 1', () => {
        const neu = baseQuad.map(p => ({ x: p.x + 10, y: p.y }))
        expect(glaetteQuad(baseQuad, neu, { anteilNeu: 5, sprungPx: 50 })).toEqual(neu)
        expect(glaetteQuad(baseQuad, neu, { anteilNeu: -1, sprungPx: 50 })).toEqual(baseQuad)
    })

    it('verändert die Eingaben nicht', () => {
        const vorher = baseQuad.map(p => ({ ...p }))
        const neu = baseQuad.map(p => ({ x: p.x + 4, y: p.y + 4 }))
        glaetteQuad(vorher, neu, { sprungPx: 50 })
        expect(vorher).toEqual(baseQuad)
        expect(neu[0]).toEqual({ x: 104, y: 104 })
    })
})

describe('viereckFehler / istGueltigesViereck', () => {
    // baseQuad deckt 400 × 600 = 240.000 px² ab
    it('akzeptiert ein normales Viereck im Uhrzeigersinn', () => {
        expect(viereckFehler(baseQuad, 1000, 1000)).toBeNull()
        expect(istGueltigesViereck(baseQuad, 1000, 1000)).toBe(true)
    })

    it('akzeptiert auch ein Viereck gegen den Uhrzeigersinn', () => {
        expect(viereckFehler([...baseQuad].reverse(), 1000, 1000)).toBeNull()
    })

    it('erkennt überkreuzte Ecken (zwei Ecken vertauscht)', () => {
        const ueberkreuzt = [baseQuad[0], baseQuad[2], baseQuad[1], baseQuad[3]]
        expect(viereckFehler(ueberkreuzt, 1000, 1000)).toBe('ueberkreuzt')
        expect(istGueltigesViereck(ueberkreuzt, 1000, 1000)).toBe(false)
    })

    it('erkennt eine eingedellte Ecke (nicht konvex)', () => {
        const eingedellt = [baseQuad[0], baseQuad[1], { x: 200, y: 200 }, baseQuad[3]]
        expect(viereckFehler(eingedellt, 1000, 1000)).toBe('ueberkreuzt')
    })

    it('erkennt drei Ecken auf einer Linie', () => {
        const flach = [baseQuad[0], { x: 300, y: 100 }, baseQuad[1], baseQuad[3]]
        expect(viereckFehler(flach, 1000, 1000)).toBe('ueberkreuzt')
    })

    it('lehnt unvollständige Ecken ab', () => {
        expect(viereckFehler(baseQuad.slice(0, 3), 1000, 1000)).toBe('ueberkreuzt')
    })

    it('erkennt einen zu kleinen Ausschnitt (unter 1 % der Bildfläche)', () => {
        const klein = [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 }]
        expect(viereckFehler(klein, 100, 100)).toBeNull()
        expect(viereckFehler(klein, 1000, 1000)).toBe('zu-klein')
    })

    it('nutzt einen eigenen Mindestanteil', () => {
        expect(viereckFehler(baseQuad, 1000, 1000, 0.3)).toBe('zu-klein')
        expect(istGueltigesViereck(baseQuad, 1000, 1000, 0.2)).toBe(true)
    })
})
