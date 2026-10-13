import { describe, it, expect } from 'vitest'
import { rasteKantenEin, zuGraubild, type Graubild } from './kantenEinrasten'
import type { Point } from './scannerOverlay'

/** Punkt-in-Viereck-Test (konvex, Ecken im Uhrzeigersinn). */
function innen(ecken: Point[], x: number, y: number): boolean {
    for (let i = 0; i < 4; i++) {
        const a = ecken[i]
        const b = ecken[(i + 1) % 4]
        if ((b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x) < 0) return false
    }
    return true
}

/** Zeichnet ein Blatt mit 4×4-Überabtastung (weiche Kanten wie im Foto). */
function zeichneBlatt(breite: number, hoehe: number, ecken: Point[], papier: number, grund: number): Graubild {
    const daten = new Float32Array(breite * hoehe)
    for (let y = 0; y < hoehe; y++) {
        for (let x = 0; x < breite; x++) {
            let treffer = 0
            for (let sy = 0; sy < 4; sy++) {
                for (let sx = 0; sx < 4; sx++) {
                    if (innen(ecken, x + (sx + 0.5) / 4, y + (sy + 0.5) / 4)) treffer++
                }
            }
            const anteil = treffer / 16
            daten[y * breite + x] = papier * anteil + grund * (1 - anteil)
        }
    }
    return { daten, breite, hoehe }
}

const ECHT: Point[] = [
    { x: 62, y: 48 },
    { x: 338, y: 60 },
    { x: 326, y: 452 },
    { x: 50, y: 440 },
]

function verschiebe(ecken: Point[], versatz: Point[]): Point[] {
    return ecken.map((p, i) => ({ x: p.x + versatz[i].x, y: p.y + versatz[i].y }))
}

const GROB = verschiebe(ECHT, [
    { x: 6, y: -5 }, { x: -7, y: 4 }, { x: 5, y: 6 }, { x: -4, y: -7 },
])

function maxFehler(a: Point[], b: Point[]): number {
    return Math.max(...a.map((p, i) => Math.hypot(p.x - b[i].x, p.y - b[i].y)))
}

describe('rasteKantenEin', () => {
    it('legt grob geschätzte Ecken an die echten Blattkanten', () => {
        const bild = zeichneBlatt(400, 500, ECHT, 230, 60)
        const fein = rasteKantenEin(bild, GROB)
        expect(maxFehler(GROB, ECHT)).toBeGreaterThan(7)
        expect(maxFehler(fein, ECHT)).toBeLessThan(1.5)
    })

    it('findet die Kante auch bei hellem Blatt auf hellem Grund (wenig Kontrast)', () => {
        const bild = zeichneBlatt(400, 500, ECHT, 245, 215)
        const fein = rasteKantenEin(bild, GROB)
        expect(maxFehler(fein, ECHT)).toBeLessThan(1.5)
    })

    it('lässt sich von einer dunklen Schriftzeile nahe der Kante nicht ablenken', () => {
        const bild = zeichneBlatt(400, 500, ECHT, 230, 60)
        // Schriftzeile 6 px unterhalb der oberen Kante, parallel dazu
        for (let x = 90; x < 310; x++) {
            const yOben = 48 + ((x - 62) * 12) / 276
            for (let dy = 6; dy < 9; dy++) bild.daten[Math.round(yOben + dy) * 400 + x] = 40
        }
        const fein = rasteKantenEin(bild, GROB)
        expect(maxFehler(fein, ECHT)).toBeLessThan(1.5)
    })

    it('findet die schwache Kante auf hellem Grund, obwohl Schrift dahinter kräftiger ist', () => {
        const bild = zeichneBlatt(400, 500, ECHT, 245, 215)
        for (let x = 90; x < 310; x++) {
            const yOben = 48 + ((x - 62) * 12) / 276
            for (let dy = 6; dy < 9; dy++) bild.daten[Math.round(yOben + dy) * 400 + x] = 30
        }
        const fein = rasteKantenEin(bild, GROB)
        expect(maxFehler(fein, ECHT)).toBeLessThan(1.5)
    })

    it('rastet auch bei verrauschtem Foto mit schwachem Kontrast ein', () => {
        const bild = zeichneBlatt(400, 500, ECHT, 240, 205)
        let zufall = 42
        for (let i = 0; i < bild.daten.length; i++) {
            zufall = (zufall * 1103515245 + 12345) % 2147483648
            bild.daten[i] += (zufall / 2147483648 - 0.5) * 16
        }
        const fein = rasteKantenEin(bild, GROB)
        expect(maxFehler(fein, ECHT)).toBeLessThan(2)
    })

    it('lässt die Ecken unverändert, wenn keine Kante zu sehen ist', () => {
        const bild = zeichneBlatt(400, 500, ECHT, 200, 200)
        expect(rasteKantenEin(bild, GROB)).toEqual(GROB)
    })

    it('rastet nur die Seiten ein, die eine Kante haben', () => {
        // Blatt ragt unten aus dem Bild: untere Seite hat keine Kante
        const echt: Point[] = [{ x: 62, y: 48 }, { x: 338, y: 60 }, { x: 338, y: 560 }, { x: 62, y: 560 }]
        const bild = zeichneBlatt(400, 500, echt, 230, 60)
        const grob: Point[] = [{ x: 66, y: 44 }, { x: 333, y: 64 }, { x: 333, y: 495 }, { x: 66, y: 495 }]
        const fein = rasteKantenEin(bild, grob)
        expect(Math.hypot(fein[0].x - 62, fein[0].y - 48)).toBeLessThan(1.5)
        expect(Math.hypot(fein[1].x - 338, fein[1].y - 60)).toBeLessThan(1.5)
        // Unten: Lage der Seite bleibt, links/rechts sind eingerastet
        expect(Math.abs(fein[2].x - 338)).toBeLessThan(1.5)
        expect(Math.abs(fein[3].x - 62)).toBeLessThan(1.5)
        expect(fein[2].y).toBeGreaterThan(490)
    })

    it('gibt ungültige Eingaben unverändert zurück', () => {
        const bild = zeichneBlatt(400, 500, ECHT, 230, 60)
        const drei = GROB.slice(0, 3)
        expect(rasteKantenEin(bild, drei)).toBe(drei)
        const winzig: Graubild = { daten: new Float32Array(4), breite: 2, hoehe: 2 }
        expect(rasteKantenEin(winzig, GROB)).toBe(GROB)
    })

    it('verwirft ein Ergebnis, das kein konvexes Viereck mehr wäre', () => {
        const bild = zeichneBlatt(400, 500, ECHT, 230, 60)
        // Übers Kreuz liegende Ecken: Seiten schneiden sich
        const kreuz = [GROB[0], GROB[2], GROB[1], GROB[3]]
        expect(rasteKantenEin(bild, kreuz)).toBe(kreuz)
    })
})

describe('zuGraubild', () => {
    it('rechnet RGBA in Helligkeit um', () => {
        const data = new Uint8ClampedArray([255, 255, 255, 255, 0, 0, 0, 255, 255, 0, 0, 255])
        const grau = zuGraubild({ data, width: 3, height: 1 })
        expect(grau.breite).toBe(3)
        expect(grau.hoehe).toBe(1)
        expect(grau.daten[0]).toBeCloseTo(255, 0)
        expect(grau.daten[1]).toBe(0)
        expect(grau.daten[2]).toBeCloseTo(76.2, 0)
    })
})
