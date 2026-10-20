import { describe, expect, it } from 'vitest'
import { bundeslandName, feiertageAnzahlText } from './bundeslaender'

describe('bundeslaender (Handy)', () => {
    it('liefert den Klartext zu allen 16 Kürzeln', () => {
        const kuerzel = ['BW', 'BY', 'BE', 'BB', 'HB', 'HH', 'HE', 'MV', 'NI', 'NW', 'RP', 'SL', 'SN', 'ST', 'SH', 'TH']
        expect(kuerzel.map(k => bundeslandName(k)).every(Boolean)).toBe(true)
        expect(bundeslandName('BY')).toBe('Bayern')
        expect(bundeslandName(' sn ')).toBe('Sachsen')
    })

    it('liefert null für leere oder unbekannte Kürzel', () => {
        expect(bundeslandName(undefined)).toBeNull()
        expect(bundeslandName(null)).toBeNull()
        expect(bundeslandName('')).toBeNull()
        expect(bundeslandName('XX')).toBeNull()
    })

    it('baut den Anzahl-Text mit und ohne Bundesland', () => {
        expect(feiertageAnzahlText(13, 'BY')).toBe('13 Feiertage in Bayern')
        expect(feiertageAnzahlText(1, 'HH')).toBe('1 Feiertag in Hamburg')
        expect(feiertageAnzahlText(9, null)).toBe('9 Feiertage')
        expect(feiertageAnzahlText(0, 'XX')).toBe('0 Feiertage')
    })
})
