import { describe, expect, it } from 'vitest'
import { leseFehlermeldung } from './apiFehler'

const antwort = (body: string, status = 400) => new Response(body, { status })

describe('leseFehlermeldung', () => {
    it('nimmt message vor error', async () => {
        expect(await leseFehlermeldung(antwort(JSON.stringify({ status: 400, message: 'Monat festgeschrieben.', error: 'alt' })), 'Ersatz')).toBe('Monat festgeschrieben.')
    })

    it('fällt auf error zurück (ältere Endpunkte)', async () => {
        expect(await leseFehlermeldung(antwort(JSON.stringify({ error: 'Buchung ist gesperrt' })), 'Ersatz')).toBe('Buchung ist gesperrt')
    })

    it('nimmt den Ersatztext bei Springs Standardfehler, leerem Text oder ohne JSON', async () => {
        expect(await leseFehlermeldung(antwort(JSON.stringify({ timestamp: 'x', status: 500, error: 'Internal Server Error', path: '/api/x' }), 500), 'Ersatz')).toBe('Ersatz')
        expect(await leseFehlermeldung(antwort(JSON.stringify({ message: '  ' })), 'Ersatz')).toBe('Ersatz')
        expect(await leseFehlermeldung(antwort('kein json'), 'Ersatz')).toBe('Ersatz')
        expect(await leseFehlermeldung(antwort('null'), 'Ersatz')).toBe('Ersatz')
    })
})
