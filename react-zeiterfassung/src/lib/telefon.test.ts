import { describe, expect, it } from 'vitest'
import { anrufbareNummer } from './telefon'

describe('anrufbareNummer', () => {
    it('blendet leere Felder aus', () => {
        expect(anrufbareNummer(undefined)).toBeUndefined()
        expect(anrufbareNummer(null)).toBeUndefined()
        expect(anrufbareNummer('')).toBeUndefined()
        expect(anrufbareNummer('   ')).toBeUndefined()
    })

    it('blendet die vom Büro vorgeschlagene Ortsvorwahl aus', () => {
        expect(anrufbareNummer('0931 ')).toBeUndefined()
        expect(anrufbareNummer('09321')).toBeUndefined()
        expect(anrufbareNummer('036601')).toBeUndefined()
        expect(anrufbareNummer('09721-')).toBeUndefined()
        expect(anrufbareNummer('0931 / ')).toBeUndefined()
    })

    it('zählt +49 und 0049 nicht als Teil der Rufnummer', () => {
        expect(anrufbareNummer('+49 931')).toBeUndefined()
        expect(anrufbareNummer('0049 9321')).toBeUndefined()
        expect(anrufbareNummer('+49')).toBeUndefined()
    })

    it('zeigt vollständige Nummern unverändert, nur ohne Leerzeichen am Rand', () => {
        expect(anrufbareNummer('0931 123456')).toBe('0931 123456')
        expect(anrufbareNummer(' 0151 12345678 ')).toBe('0151 12345678')
        expect(anrufbareNummer('+49 931 123456')).toBe('+49 931 123456')
        expect(anrufbareNummer('0049 (0)931/12 34 56')).toBe('0049 (0)931/12 34 56')
    })

    it('lässt kurze ausländische Nummern gelten, sobald sie mehr als eine Vorwahl haben', () => {
        expect(anrufbareNummer('+43 1 234567')).toBe('+43 1 234567')
        expect(anrufbareNummer('+43 1')).toBeUndefined()
    })

    it('zieht die Grenze bei sieben Ziffern', () => {
        expect(anrufbareNummer('093112')).toBeUndefined()
        expect(anrufbareNummer('0931123')).toBe('0931123')
    })
})
