import { describe, expect, it } from 'vitest'
import { urlaubsverfallText } from './useNachrichten'

const warnung = { verfallsDatum: '2027-03-31', verfallsJahr: 2027, dringend: true }

describe('urlaubsverfallText', () => {
    it('nennt den letzten Tag und die verbleibenden Tage', () => {
        expect(urlaubsverfallText({ ...warnung, resturlaubTage: 3.5, tageVerbleibend: 20 }))
            .toBe('Du hast noch 3,5 Resturlaubstage vom Vorjahr. Nimm sie bis 31.03.2027, danach verfallen sie (noch 20 Tage).')
        expect(urlaubsverfallText({ ...warnung, resturlaubTage: 2, tageVerbleibend: 1 }))
            .toBe('Du hast noch 2 Resturlaubstage vom Vorjahr. Nimm sie bis 31.03.2027, danach verfallen sie (noch 1 Tag).')
    })

    it('spricht einen einzelnen Tag in der Einzahl an', () => {
        expect(urlaubsverfallText({ ...warnung, resturlaubTage: 1, tageVerbleibend: 5 }))
            .toBe('Du hast noch 1 Resturlaubstag vom Vorjahr. Nimm ihn bis 31.03.2027, danach verfällt er (noch 5 Tage).')
    })

    it('sagt am letzten Tag „bis heute"', () => {
        expect(urlaubsverfallText({ ...warnung, resturlaubTage: 3, tageVerbleibend: 0 }))
            .toBe('Du hast noch 3 Resturlaubstage vom Vorjahr. Nimm sie bis heute (31.03.2027), danach verfallen sie.')
    })
})
