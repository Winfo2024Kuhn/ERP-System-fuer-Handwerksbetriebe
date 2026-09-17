import { describe, it, expect, vi, afterEach } from 'vitest'
import { transkribiere, SpracheingabeFehler } from './spracheingabeService'

/** Winziger Fake-Blob mit echtem `.type`, wie ihn `audioRecorderService.stoppe()` liefert. */
function macheAufnahme(type = 'audio/webm;codecs=opus'): Blob {
    return new Blob([new Uint8Array([1, 2, 3])], { type })
}

interface FakeResponse {
    ok: boolean
    status: number
    json: () => Promise<unknown>
}

function macheAntwort(status: number, body: unknown): FakeResponse {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: async () => body,
    }
}

describe('spracheingabeService', () => {
    afterEach(() => {
        vi.unstubAllGlobals()
    })

    describe('transkribiere() - Erfolg', () => {
        it('schickt den Blob als rohen Body an die kodierte Token-URL und liefert den Text', async () => {
            const fetchMock = vi.fn().mockResolvedValue(macheAntwort(200, { text: 'Heute Gelaender montiert.' }))
            vi.stubGlobal('fetch', fetchMock)
            const aufnahme = macheAufnahme()

            const text = await transkribiere(aufnahme, 'mein-token')

            expect(text).toBe('Heute Gelaender montiert.')
            expect(fetchMock).toHaveBeenCalledTimes(1)
            const [url, init] = fetchMock.mock.calls[0]
            expect(url).toBe('/api/spracheingabe/transkribieren?token=mein-token')
            expect(init.method).toBe('POST')
            // Kein FormData/Multipart (Abweichung A): der Blob selbst ist der Body.
            expect(init.body).toBe(aufnahme)
            expect(init.headers['Content-Type']).toBe('audio/webm;codecs=opus')
        })

        it('kodiert Sonderzeichen im Token', async () => {
            const fetchMock = vi.fn().mockResolvedValue(macheAntwort(200, { text: 'ok' }))
            vi.stubGlobal('fetch', fetchMock)

            await transkribiere(macheAufnahme(), 'a b&c')

            const [url] = fetchMock.mock.calls[0]
            expect(url).toBe('/api/spracheingabe/transkribieren?token=a%20b%26c')
        })

        it('faellt auf "audio/webm" zurueck, wenn der Blob keinen Typ hat', async () => {
            const fetchMock = vi.fn().mockResolvedValue(macheAntwort(200, { text: 'ok' }))
            vi.stubGlobal('fetch', fetchMock)
            const aufnahmeOhneTyp = new Blob([new Uint8Array([1])])

            await transkribiere(aufnahmeOhneTyp, 'token')

            const [, init] = fetchMock.mock.calls[0]
            expect(init.headers['Content-Type']).toBe('audio/webm')
        })

        it('gibt ein uebergebenes AbortSignal an fetch weiter', async () => {
            const fetchMock = vi.fn().mockResolvedValue(macheAntwort(200, { text: 'ok' }))
            vi.stubGlobal('fetch', fetchMock)
            const controller = new AbortController()

            await transkribiere(macheAufnahme(), 'token', controller.signal)

            const [, init] = fetchMock.mock.calls[0]
            expect(init.signal).toBe(controller.signal)
        })
    })

    describe('transkribiere() - leere/fehlende Antwort', () => {
        it('leerer Text in einer 200er-Antwort ist ein Fehler, kein leerer String', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(macheAntwort(200, { text: '' })))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Es wurde nichts verstanden. Bitte noch einmal diktieren.',
            })
        })

        it('fehlendes text-Feld in einer 200er-Antwort ist ein Fehler', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(macheAntwort(200, {})))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toBeInstanceOf(SpracheingabeFehler)
        })
    })

    describe('transkribiere() - Statuscodes des Endpunkt-Vertrags', () => {
        it('401 -> Anmeldung abgelaufen', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(macheAntwort(401, null)))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Die Anmeldung ist abgelaufen. Bitte den QR-Code neu scannen.',
                status: 401,
            })
        })

        it('413 -> Aufnahme zu lang', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
                macheAntwort(413, { success: false, message: 'Die Aufnahme ist zu lang. Bitte kuerzer diktieren.' }),
            ))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Die Aufnahme ist zu lang. Bitte kuerzer diktieren.',
                status: 413,
            })
        })

        it('400 -> Format nicht verstanden, unabhaengig vom genauen Servertext', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
                macheAntwort(400, { success: false, message: 'Die Aufnahme ist leer.' }),
            ))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Dieses Aufnahmeformat versteht das Programm nicht.',
                status: 400,
            })
        })

        it('415 -> dieselbe Meldung wie 400', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(macheAntwort(415, null)))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Dieses Aufnahmeformat versteht das Programm nicht.',
                status: 415,
            })
        })

        it('502 nutzt die Servermeldung, wenn vorhanden', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
                macheAntwort(502, { success: false, message: 'Die Spracherkennung ist gerade nicht erreichbar.' }),
            ))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Die Spracherkennung ist gerade nicht erreichbar.',
                status: 502,
            })
        })

        it('502 ohne Servermeldung faellt auf den Standardtext zurueck', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(macheAntwort(502, {})))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Das Diktat konnte nicht umgewandelt werden. Bitte noch einmal versuchen.',
                status: 502,
            })
        })

        it('eine leere/nur-Leerzeichen Servermeldung zaehlt als "nicht vorhanden"', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(macheAntwort(502, { message: '   ' })))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Das Diktat konnte nicht umgewandelt werden. Bitte noch einmal versuchen.',
            })
        })

        it('ein sonstiger 5xx-Status ohne JSON-Body wirft trotzdem den Standardfehler statt zu crashen', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
                ok: false,
                status: 503,
                json: async () => { throw new SyntaxError('Unexpected end of JSON input') },
            }))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Das Diktat konnte nicht umgewandelt werden. Bitte noch einmal versuchen.',
                status: 503,
            })
        })

        it('401 mit leerem Body (kein JSON) crasht nicht und bleibt bei der festen Meldung', async () => {
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
                ok: false,
                status: 401,
                json: async () => { throw new SyntaxError('Unexpected end of JSON input') },
            }))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Die Anmeldung ist abgelaufen. Bitte den QR-Code neu scannen.',
                status: 401,
            })
        })
    })

    describe('transkribiere() - Netzwerk und Abbruch', () => {
        it('AbortError (Zeitueberschreitung/Abbruch) -> eigene Meldung', async () => {
            const abortError = new DOMException('The operation was aborted.', 'AbortError')
            vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abortError))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Das Diktat hat zu lange gedauert. Bitte noch einmal versuchen.',
            })
        })

        it('Netzwerkfehler (TypeError aus fetch) -> Offline-Meldung', async () => {
            vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

            await expect(transkribiere(macheAufnahme(), 'token')).rejects.toMatchObject({
                message: 'Keine Verbindung. Spracheingabe braucht Internet.',
            })
        })
    })

    describe('transkribiere() - DSGVO', () => {
        it('loggt weder den Text noch dessen Inhalt jemals in die Konsole', async () => {
            const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
            const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
            const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
                macheAntwort(200, { text: 'Geheimnisvoller Text von Max Mustermann' }),
            ))
            await transkribiere(macheAufnahme(), 'token')

            vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
                macheAntwort(502, { message: 'Geheime Servermeldung' }),
            ))
            await transkribiere(macheAufnahme(), 'token').catch(() => { /* erwartet */ })

            const alles = [...logSpy.mock.calls, ...warnSpy.mock.calls, ...errorSpy.mock.calls]
                .flat()
                .map(arg => String(arg))
                .join(' ')
            expect(alles).not.toContain('Geheim')

            logSpy.mockRestore()
            warnSpy.mockRestore()
            errorSpy.mockRestore()
        })
    })
})
