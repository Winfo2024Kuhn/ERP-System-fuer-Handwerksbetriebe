import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import 'fake-indexeddb/auto'
import {
    arbeiteWarteschlangeAb,
    mitWarteschlangenSperre,
    oeffneBuchungsDB,
} from './buchungsWarteschlange'

const antwort = (status: number, body: unknown = {}) =>
    ({ ok: status >= 200 && status < 300, status, json: async () => body }) as unknown as Response

async function reiheEin(id: string, data: Record<string, unknown>, originalTime = '2026-10-17T16:00:00Z') {
    const db = await oeffneBuchungsDB()
    await db.put('pending', {
        id, type: 'stop', data, timestamp: originalTime, originalTime, status: 'pending', attemptCount: 0,
    })
}

describe('buchungsWarteschlange', () => {
    beforeEach(async () => {
        const db = await oeffneBuchungsDB()
        await db.clear('pending')
        await db.clear('failed')
    })

    afterEach(() => {
        vi.restoreAllMocks()
        vi.unstubAllGlobals()
    })

    describe('arbeiteWarteschlangeAb', () => {
        // Im Service Worker fehlt der Token-Zusatz der App – der Zugang muss mit.
        it('schickt den Zugang des Eintrags mit und verbietet Weiterleitungen', async () => {
            const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => antwort(200))
            await reiheEin('op-1', { token: 'max-mustermann-token' })

            const r = await arbeiteWarteschlangeAb()

            expect(r).toMatchObject({ synced: 1, abgebrochen: false })
            const [url, init] = fetchSpy.mock.calls[0]
            expect(url).toBe('/api/zeiterfassung/stop')
            expect((init as RequestInit).headers).toMatchObject({
                'Content-Type': 'application/json',
                'X-Auth-Token': 'max-mustermann-token',
            })
            expect((init as RequestInit).redirect).toBe('error')
        })

        it('setzt ohne Token im Eintrag keinen Zugangs-Header', async () => {
            const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => antwort(200))
            await reiheEin('op-2', {})

            await arbeiteWarteschlangeAb()

            expect((fetchSpy.mock.calls[0][1] as RequestInit).headers).not.toHaveProperty('X-Auth-Token')
        })

        it('liefert bei leerer Warteschlange ein leeres Ergebnis ohne Anfrage', async () => {
            const fetchSpy = vi.spyOn(globalThis, 'fetch')

            const r = await arbeiteWarteschlangeAb()

            expect(r).toEqual({ synced: 0, failed: 0, discarded: 0, startSynced: false, abgebrochen: false })
            expect(fetchSpy).not.toHaveBeenCalled()
        })
    })

    describe('mitWarteschlangenSperre', () => {
        it('nutzt die gemeinsame Sperre von App und Service Worker, wenn es sie gibt', async () => {
            const request = vi.fn(async (_name: string, arbeit: () => Promise<unknown>) => arbeit())
            vi.stubGlobal('navigator', { locks: { request } })

            const r = await mitWarteschlangenSperre(async () => 'erledigt')

            expect(r).toBe('erledigt')
            expect(request).toHaveBeenCalledWith('zeiterfassung-buchungen-senden', expect.any(Function))
        })

        it('arbeitet ohne Web Locks direkt weiter', async () => {
            vi.stubGlobal('navigator', {})

            await expect(mitWarteschlangenSperre(async () => 42)).resolves.toBe(42)
        })

        it('lässt nie zwei Durchläufe gleichzeitig arbeiten', async () => {
            // Minimale Sperre wie im Browser: Wer zuerst kommt, arbeitet; der Nächste wartet.
            let kette: Promise<unknown> = Promise.resolve()
            vi.stubGlobal('navigator', {
                locks: {
                    request: (_name: string, arbeit: () => Promise<unknown>) => {
                        const lauf = kette.then(arbeit)
                        kette = lauf.catch(() => undefined)
                        return lauf
                    },
                },
            })
            let gleichzeitig = 0
            let hoechstens = 0
            const arbeit = async () => {
                gleichzeitig++
                hoechstens = Math.max(hoechstens, gleichzeitig)
                await new Promise(resolve => setTimeout(resolve, 5))
                gleichzeitig--
            }

            await Promise.all([mitWarteschlangenSperre(arbeit), mitWarteschlangenSperre(arbeit)])

            expect(hoechstens).toBe(1)
        })
    })
})
