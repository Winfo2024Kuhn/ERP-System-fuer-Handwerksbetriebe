import { describe, it, expect } from 'vitest'
import { loeseHomographie, zielGroesse } from './scanSeite'
import type { Point } from './scannerOverlay'

function bilde(h: number[], p: Point): Point {
    const n = h[6] * p.x + h[7] * p.y + 1
    return { x: (h[0] * p.x + h[1] * p.y + h[2]) / n, y: (h[3] * p.x + h[4] * p.y + h[5]) / n }
}

describe('loeseHomographie', () => {
    it('bildet die Zielecken exakt auf die Quellecken ab', () => {
        const quelle: Point[] = [{ x: 30, y: 20 }, { x: 410, y: 45 }, { x: 390, y: 600 }, { x: 15, y: 570 }]
        const ziel: Point[] = [{ x: 0, y: 0 }, { x: 400, y: 0 }, { x: 400, y: 560 }, { x: 0, y: 560 }]
        const h = loeseHomographie(quelle, ziel)
        expect(h).toHaveLength(9)
        ziel.forEach((p, i) => {
            const q = bilde(h, p)
            expect(q.x).toBeCloseTo(quelle[i].x, 6)
            expect(q.y).toBeCloseTo(quelle[i].y, 6)
        })
    })

    it('ist bei gleicher Quelle und Ziel die Identität', () => {
        const ecken: Point[] = [{ x: 0, y: 0 }, { x: 100, y: 0 }, { x: 100, y: 50 }, { x: 0, y: 50 }]
        const h = loeseHomographie(ecken, ecken)
        const q = bilde(h, { x: 37, y: 21 })
        expect(q.x).toBeCloseTo(37, 6)
        expect(q.y).toBeCloseTo(21, 6)
    })
})

describe('zielGroesse', () => {
    it('nimmt die längeren gegenüberliegenden Seiten', () => {
        const ecken: Point[] = [{ x: 0, y: 0 }, { x: 300, y: 0 }, { x: 280, y: 400 }, { x: 10, y: 420 }]
        const { breite, hoehe } = zielGroesse(ecken)
        expect(breite).toBe(300)
        expect(hoehe).toBe(Math.floor(Math.hypot(10, 420)))
    })

    it('begrenzt große Seiten und behält das Seitenverhältnis', () => {
        const ecken: Point[] = [{ x: 0, y: 0 }, { x: 3000, y: 0 }, { x: 3000, y: 4000 }, { x: 0, y: 4000 }]
        const { breite, hoehe } = zielGroesse(ecken, 2000)
        expect(hoehe).toBe(2000)
        expect(breite).toBe(1500)
    })

    it('liefert mindestens 1 × 1 Pixel', () => {
        const punkt: Point[] = [{ x: 5, y: 5 }, { x: 5, y: 5 }, { x: 5, y: 5 }, { x: 5, y: 5 }]
        expect(zielGroesse(punkt)).toEqual({ breite: 1, hoehe: 1 })
    })
})
