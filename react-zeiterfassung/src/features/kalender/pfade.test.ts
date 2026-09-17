import { describe, expect, it } from 'vitest'
import { kalenderPfadMitSheet, terminPfad } from './pfade'

describe('pfade', () => {
    it('terminPfad kodiert Datum und Schlüssel', () => {
        expect(terminPfad({ datum: '2026-09-03', key: 't-13' })).toBe('/kalender/termin/2026-09-03/t-13')
        expect(terminPfad({ datum: '2026-10-03', key: 'f-2026-10-03-Tag der Deutschen Einheit' }))
            .toBe('/kalender/termin/2026-10-03/f-2026-10-03-Tag%20der%20Deutschen%20Einheit')
    })

    it('kalenderPfadMitSheet öffnet den Tag mit Sheet', () => {
        expect(kalenderPfadMitSheet('2026-09-03')).toBe('/kalender?datum=2026-09-03&sheet=tag')
    })
})
