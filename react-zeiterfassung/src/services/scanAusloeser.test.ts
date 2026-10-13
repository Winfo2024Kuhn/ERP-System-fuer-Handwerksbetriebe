import { describe, it, expect } from 'vitest'
import { ausloeserSchritt, sperreNachAufnahme, START_ZUSTAND, type AusloeserZustand } from './scanAusloeser'
import type { Point } from './scannerOverlay'

// Pixel-Koordinaten eines Blatts (TL, TR, BR, BL) – Dummy-Daten.
const blatt: Point[] = [
    { x: 100, y: 100 },
    { x: 500, y: 100 },
    { x: 500, y: 700 },
    { x: 100, y: 700 },
]
const verschoben = (dx: number): Point[] => blatt.map(p => ({ x: p.x + dx, y: p.y }))
const OPTIONEN = { ruhigTolPx: 10, wechselTolPx: 60, ruhigeBilderNoetig: 3, leereBilderZumEntsperren: 2 }

function folge(start: AusloeserZustand, quads: Array<Point[] | null>) {
    let zustand = start
    return quads.map(quad => {
        const schritt = ausloeserSchritt(zustand, quad, OPTIONEN)
        zustand = schritt.zustand
        return schritt
    })
}

describe('ausloeserSchritt', () => {
    it('sucht, solange kein Blatt erkannt wird', () => {
        const schritt = ausloeserSchritt(START_ZUSTAND, null, OPTIONEN)
        expect(schritt.phase).toBe('suchen')
        expect(schritt.verbleibend).toBe(3)
    })

    it('zählt ruhige Bilder herunter und löst nach genug ruhigen Bildern aus', () => {
        const schritte = folge(START_ZUSTAND, [blatt, verschoben(2), verschoben(4), verschoben(5)])
        expect(schritte.map(s => s.phase)).toEqual(['halten', 'halten', 'halten', 'ausloesen'])
        expect(schritte.map(s => s.verbleibend)).toEqual([3, 2, 1, 0])
    })

    it('fängt bei Bewegung wieder von vorn an', () => {
        const schritte = folge(START_ZUSTAND, [blatt, verschoben(2), verschoben(40), verschoben(42)])
        expect(schritte.map(s => s.verbleibend)).toEqual([3, 2, 3, 2])
    })

    it('löst dieselbe Seite nach einer Aufnahme nicht noch einmal aus', () => {
        const schritte = folge(START_ZUSTAND, [blatt, blatt, blatt, blatt, blatt, blatt, blatt, blatt])
        expect(schritte.filter(s => s.phase === 'ausloesen')).toHaveLength(1)
        expect(schritte.at(-1)?.phase).toBe('gesperrt')
    })

    it('entsperrt, wenn das Blatt einige Bilder lang verschwunden war', () => {
        const schritte = folge(START_ZUSTAND, [blatt, blatt, blatt, blatt, null, null, blatt, blatt, blatt, blatt])
        expect(schritte.map(s => s.phase)).toEqual([
            'halten', 'halten', 'halten', 'ausloesen', 'suchen', 'suchen', 'halten', 'halten', 'halten', 'ausloesen',
        ])
    })

    it('bleibt gesperrt, wenn die Erkennung nur ein einzelnes Bild aussetzt', () => {
        const schritte = folge(START_ZUSTAND, [blatt, blatt, blatt, blatt, null, blatt, blatt, blatt, blatt])
        expect(schritte.filter(s => s.phase === 'ausloesen')).toHaveLength(1)
        expect(schritte.at(-1)?.phase).toBe('gesperrt')
    })

    it('entsperrt, wenn sich das Blatt deutlich verschiebt (neues Blatt)', () => {
        const neu = verschoben(150)
        const schritte = folge(START_ZUSTAND, [blatt, blatt, blatt, blatt, neu, neu, neu, neu])
        expect(schritte.map(s => s.phase)).toEqual([
            'halten', 'halten', 'halten', 'ausloesen', 'halten', 'halten', 'halten', 'ausloesen',
        ])
    })
})

describe('sperreNachAufnahme', () => {
    it('sperrt das zuletzt erkannte Blatt nach einer Aufnahme von Hand', () => {
        const zustand = ausloeserSchritt(START_ZUSTAND, blatt, OPTIONEN).zustand
        const gesperrt = sperreNachAufnahme(zustand)
        expect(gesperrt.sperrQuad).toBe(blatt)
        expect(ausloeserSchritt(gesperrt, blatt, OPTIONEN).phase).toBe('gesperrt')
    })

    it('behält eine bestehende Sperre, wenn gerade kein Blatt erkannt wird', () => {
        const zustand: AusloeserZustand = { letzterQuad: null, ruhigeBilder: 2, sperrQuad: blatt, leereBilder: 1 }
        expect(sperreNachAufnahme(zustand)).toEqual({ letzterQuad: null, ruhigeBilder: 0, sperrQuad: blatt, leereBilder: 1 })
    })

    it('lässt den Auslöser frei, wenn nie ein Blatt erkannt wurde', () => {
        expect(sperreNachAufnahme(START_ZUSTAND).sperrQuad).toBeNull()
    })
})
