import { describe, it, expect } from 'vitest'
import { wendeScanFilterAn, SCAN_FILTER, STANDARD_SCAN_FILTER, type Pixelbild } from './scanFilter'

const B = 240
const H = 320

/**
 * Papier mit Lichtverlauf (links dunkel, rechts hell) und Farbstich,
 * darauf eine schwarze Schriftzeile in der Mitte.
 */
function blattMitSchatten(farbstich: [number, number, number] = [1, 1, 1]): Pixelbild {
    const data = new Uint8ClampedArray(B * H * 4)
    for (let y = 0; y < H; y++) {
        for (let x = 0; x < B; x++) {
            const licht = 0.5 + (0.5 * x) / (B - 1)
            const schrift = y >= 150 && y < 156 && x % 12 < 8
            const i = (y * B + x) * 4
            for (let c = 0; c < 3; c++) {
                data[i + c] = schrift ? 20 : Math.round(240 * licht * farbstich[c])
            }
            data[i + 3] = 255
        }
    }
    return { data, width: B, height: H }
}

function pixel(daten: Uint8ClampedArray, x: number, y: number): number[] {
    const i = (y * B + x) * 4
    return [daten[i], daten[i + 1], daten[i + 2], daten[i + 3]]
}

describe('wendeScanFilterAn', () => {
    it('„Dokument“ macht das Papier trotz Schatten gleichmäßig weiß', () => {
        const aus = wendeScanFilterAn(blattMitSchatten(), 'dokument')
        for (const x of [5, 60, 120, 230]) {
            const [r, g, b, a] = pixel(aus, x, 40)
            expect(Math.min(r, g, b)).toBeGreaterThanOrEqual(245)
            expect(a).toBe(255)
        }
    })

    it('„Dokument“ lässt Schrift dunkel – auch im Schatten', () => {
        const aus = wendeScanFilterAn(blattMitSchatten(), 'dokument')
        expect(pixel(aus, 2, 152)[0]).toBeLessThan(80)
        expect(pixel(aus, 230, 152)[0]).toBeLessThan(40)
    })

    it('„Dokument“ entfernt einen Farbstich vom Licht', () => {
        const aus = wendeScanFilterAn(blattMitSchatten([1, 0.92, 0.75]), 'dokument')
        const [r, g, b] = pixel(aus, 120, 40)
        expect(Math.max(r, g, b) - Math.min(r, g, b)).toBeLessThanOrEqual(6)
    })

    it('„Dokument“ hellt große dunkle Flächen (Fotos) nicht zu Weiß auf', () => {
        const bild = blattMitSchatten()
        for (let y = 200; y < H; y++) {
            for (let x = 0; x < B; x++) {
                const i = (y * B + x) * 4
                bild.data[i] = bild.data[i + 1] = bild.data[i + 2] = 50
            }
        }
        const aus = wendeScanFilterAn(bild, 'dokument')
        expect(pixel(aus, 120, 290)[0]).toBeLessThan(140)
    })

    it('„Schwarz-Weiß“ liefert Graustufen mit klarem Kontrast', () => {
        const aus = wendeScanFilterAn(blattMitSchatten([1, 0.9, 0.8]), 'schwarzweiss')
        const papier = pixel(aus, 60, 40)
        const schrift = pixel(aus, 60, 152)
        expect(papier[0]).toBe(papier[1])
        expect(papier[1]).toBe(papier[2])
        expect(papier[0]).toBeGreaterThan(240)
        expect(schrift[0]).toBeLessThan(15)
    })

    it('„Original“ liefert eine unveränderte Kopie', () => {
        const bild = blattMitSchatten()
        const aus = wendeScanFilterAn(bild, 'original')
        expect(aus).not.toBe(bild.data)
        expect(aus).toEqual(bild.data)
    })

    it('verändert die Eingabe nicht', () => {
        const bild = blattMitSchatten()
        const vorher = new Uint8ClampedArray(bild.data)
        wendeScanFilterAn(bild, 'dokument')
        expect(bild.data).toEqual(vorher)
    })

    it('kommt mit Bildern kleiner als eine Kachel zurecht', () => {
        const data = new Uint8ClampedArray([200, 200, 200, 255, 10, 10, 10, 255])
        const aus = wendeScanFilterAn({ data, width: 2, height: 1 }, 'dokument')
        expect(aus[0]).toBe(255)
        expect(aus[4]).toBeLessThan(20)
    })
})

describe('Filterliste', () => {
    it('bietet drei Filter an, „Dokument“ ist voreingestellt', () => {
        expect(SCAN_FILTER.map(f => f.wert)).toEqual(['dokument', 'schwarzweiss', 'original'])
        expect(STANDARD_SCAN_FILTER).toBe('dokument')
    })
})
