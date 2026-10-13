import { describe, expect, it } from 'vitest'
import { kameraFehlerText } from './kameraFehler'

const domFehler = (name: string) => new DOMException('englische Browsermeldung', name)

describe('kameraFehlerText', () => {
    it.each(['NotAllowedError', 'SecurityError'])('meldet verweigerten Zugriff bei %s', name => {
        expect(kameraFehlerText(domFehler(name))).toMatch(/^Kein Zugriff auf die Kamera\./)
    })

    it('erkennt verweigerten Zugriff auch an der Meldung', () => {
        expect(kameraFehlerText(new Error('Permission denied'))).toMatch(/^Kein Zugriff auf die Kamera\./)
    })

    it.each(['NotFoundError', 'OverconstrainedError'])('meldet fehlende Kamera bei %s', name => {
        expect(kameraFehlerText(domFehler(name))).toMatch(/^Keine passende Kamera gefunden\./)
    })

    it.each(['NotReadableError', 'AbortError'])('meldet belegte Kamera bei %s', name => {
        expect(kameraFehlerText(domFehler(name))).toMatch(/^Die Kamera wird gerade von einer anderen App benutzt\./)
    })

    it('meldet alles andere allgemein und ohne englische Browsermeldung', () => {
        const text = kameraFehlerText(domFehler('TypeError'))
        expect(text).toBe('Kamera konnte nicht gestartet werden. Bitte App neu öffnen.')
        expect(kameraFehlerText('irgendwas')).toBe(text)
        expect(kameraFehlerText(new Error('Could not start video source'))).toBe(text)
    })
})
