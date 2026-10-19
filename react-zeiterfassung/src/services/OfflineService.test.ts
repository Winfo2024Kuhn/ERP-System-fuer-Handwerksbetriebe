import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import 'fake-indexeddb/auto'
import { openDB } from 'idb'
import {
    ABGLEICH_ANSTOSSEN_EVENT,
    abgleichAnstossen,
    buildBookingRequestPayload,
    createOperationId,
    istVoruebergehenderFehler,
    OfflineService,
    hintergrundUebertragungAnmelden,
    sendeBuchung,
    speicherDauerhaftAnfordern,
    startZeitAusErgebnis,
    _resetForTesting,
    _setPendingEntryStatusForTesting,
    _warteAufVorladenForTesting,
} from './OfflineService'

function createMemoryStorage(): Storage {
    const store = new Map<string, string>()

    return {
        get length() {
            return store.size
        },
        clear() {
            store.clear()
        },
        getItem(key: string) {
            return store.has(key) ? store.get(key)! : null
        },
        key(index: number) {
            return Array.from(store.keys())[index] ?? null
        },
        removeItem(key: string) {
            store.delete(key)
        },
        setItem(key: string, value: string) {
            store.set(key, value)
        },
    }
}

// ==================== MOCK HELPERS ====================
const ok = (body: unknown = {}) => Promise.resolve({
    ok: true, status: 200, json: () => Promise.resolve(body),
} as Response)

const clientError = (status = 409, body: unknown = { error: 'Conflict' }) => Promise.resolve({
    ok: false, status, json: () => Promise.resolve(body),
} as Response)

const serverError = (status = 500) => Promise.resolve({
    ok: false, status, json: () => Promise.resolve({ error: 'ISE' }),
} as Response)

// ==================== SETUP ====================
describe('OfflineService', () => {
    beforeEach(async () => {
        // Vorladen aus dem letzten Test auslaufen lassen, bevor die Stores geleert werden
        await _warteAufVorladenForTesting()
        vi.stubGlobal('localStorage', createMemoryStorage())

        // Clear all stores for clean test state (fast, no DB close needed)
        try {
            await OfflineService.clearCache()
            await OfflineService.clearPending()
            await OfflineService.clearFailed()
        } catch {
            // First test: DB doesn't exist yet, that's fine - addPendingEntry will create it
        }

        // Reset sync lock only (don't close DB!)
        await _resetForTesting()

        localStorage.clear()

        // Default: online
        Object.defineProperty(navigator, 'onLine', { writable: true, value: true })
    })

    afterEach(() => {
        localStorage.clear()
        vi.unstubAllGlobals()
        vi.restoreAllMocks()
    })

    // ==================== PENDING ENTRY CRUD ====================
    describe('Pending Entry CRUD', () => {
        it('sollte Pending-Eintrag hinzufügen und abrufen', async () => {
            const entry = await OfflineService.addPendingEntry('start', { token: 'abc' })
            expect(entry.type).toBe('start')
            expect(entry.data.token).toBe('abc')
            expect(entry.id).toBeTruthy()

            const all = await OfflineService.getPendingEntries()
            expect(all).toHaveLength(1)
            expect(all[0].id).toBe(entry.id)
        })

        it('sollte mehrere Einträge verschiedener Typen speichern', async () => {
            await OfflineService.addPendingEntry('start', { token: 'a' })
            await OfflineService.addPendingEntry('stop', { token: 'a' })
            await OfflineService.addPendingEntry('pause', { token: 'a' })
            expect(await OfflineService.getPendingCount()).toBe(3)
        })

        it('getPendingCount bei leerer Queue = 0', async () => {
            expect(await OfflineService.getPendingCount()).toBe(0)
        })

        it('sollte originalTime korrekt setzen wenn angegeben', async () => {
            const t = '2024-06-15T10:30:00.000Z'
            const entry = await OfflineService.addPendingEntry('start', { token: 'a' }, t)
            expect(entry.originalTime).toBe(t)
        })

        it('sollte originalTime auf jetzt setzen wenn nicht angegeben', async () => {
            const before = Date.now()
            const entry = await OfflineService.addPendingEntry('start', { token: 'a' })
            const after = Date.now()
            const ts = new Date(entry.originalTime).getTime()
            expect(ts).toBeGreaterThanOrEqual(before - 100)
            expect(ts).toBeLessThanOrEqual(after + 100)
        })

        it('sollte eine vorgegebene operationId für queued retries beibehalten', async () => {
            const entry = await OfflineService.addPendingEntryWithOperationId('stop', { token: 'a' }, '2024-06-15T10:00:00Z', 'op-123')
            expect(entry.id).toBe('op-123')
            expect(entry.status).toBe('pending')
            expect(entry.attemptCount).toBe(0)
        })
    })

    describe('Operation Helpers', () => {
        it('createOperationId liefert eine ID', () => {
            expect(createOperationId()).toBeTruthy()
        })

        it('buildBookingRequestPayload baut idempotenten Request-Body', () => {
            expect(buildBookingRequestPayload({ token: 'tok' }, '2024-06-15T10:00:00Z', 'op-1')).toEqual({
                token: 'tok',
                originalZeit: '2024-06-15T10:00:00Z',
                idempotencyKey: 'op-1',
            })
        })
    })

    // ==================== BUG #4: clearCache darf pending NICHT löschen ====================
    describe('clearCache (Bug #4)', () => {
        it('darf Pending-Einträge NICHT löschen', async () => {
            await OfflineService.addPendingEntry('start', { token: 'a' })
            await OfflineService.addPendingEntry('stop', { token: 'a' })

            await OfflineService.clearCache()

            const pending = await OfflineService.getPendingEntries()
            expect(pending).toHaveLength(2)
        })
    })

    describe('clearPending', () => {
        it('sollte alle Pending-Einträge löschen', async () => {
            await OfflineService.addPendingEntry('start', { token: 'a' })
            await OfflineService.addPendingEntry('stop', { token: 'a' })
            await OfflineService.clearPending()
            expect(await OfflineService.getPendingCount()).toBe(0)
        })
    })

    // ==================== SYNC PENDING INTERNAL ====================
    describe('_syncPendingInternal', () => {
        it('sollte bei leerer Queue sofort zurückkehren', async () => {
            const r = await OfflineService._syncPendingInternal()
            expect(r).toEqual({ synced: 0, failed: 0, discarded: 0, startSynced: false, abgebrochen: false })
        })

        it('sollte erfolgreiche Einträge synchen und aus IDB löschen', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => ok())
            await OfflineService.addPendingEntry('start', { token: 'a' })
            await OfflineService.addPendingEntry('stop', { token: 'a' })

            const r = await OfflineService._syncPendingInternal()
            expect(r.synced).toBe(2)
            expect(r.failed).toBe(0)
            expect(await OfflineService.getPendingCount()).toBe(0)
        })

        it('sollte startSynced=true setzen wenn Start gesynct', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => ok())
            await OfflineService.addPendingEntry('start', { token: 'a' })
            const r = await OfflineService._syncPendingInternal()
            expect(r.startSynced).toBe(true)
        })

        it('sollte startSynced=false bei nur stop/pause', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => ok())
            await OfflineService.addPendingEntry('stop', { token: 'a' })
            const r = await OfflineService._syncPendingInternal()
            expect(r.startSynced).toBe(false)
        })

        it('sollte Einträge chronologisch sortiert verarbeiten', async () => {
            const callOrder: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
                callOrder.push(typeof url === 'string' ? url : url.toString())
                return { ok: true, status: 200, json: () => Promise.resolve({}) } as Response
            })

            // Absichtlich in falscher Reihenfolge
            await OfflineService.addPendingEntry('stop', { token: 'a' }, '2024-06-15T12:00:00Z')
            await OfflineService.addPendingEntry('start', { token: 'a' }, '2024-06-15T08:00:00Z')

            await OfflineService._syncPendingInternal()
            expect(callOrder[0]).toContain('/start')
            expect(callOrder[1]).toContain('/stop')
        })

        it('sollte Offline-Start vor Offline-Pause replayen, damit Pause die laufende Buchung schliesst', async () => {
            const callOrder: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
                callOrder.push(typeof url === 'string' ? url : url.toString())
                return { ok: true, status: 200, json: () => Promise.resolve({}) } as Response
            })

            await OfflineService.addPendingEntry('pause', { token: 'a' }, '2024-06-15T09:00:00Z', 60)
            await OfflineService.addPendingEntry('start', { token: 'a', projektId: 1, arbeitsgangId: 2 }, '2024-06-15T08:00:00Z')

            const result = await OfflineService._syncPendingInternal()

            expect(result.synced).toBe(2)
            expect(callOrder[0]).toContain('/start')
            expect(callOrder[1]).toContain('/pause')
        })

        it('sollte 4xx als discarded zählen und Entry in Reparatur-Liste verschieben', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(409))
            await OfflineService.addPendingEntry('stop', { token: 'a' })

            const r = await OfflineService._syncPendingInternal()
            expect(r.discarded).toBe(1)
            expect(r.synced).toBe(0)
            expect(await OfflineService.getPendingCount()).toBe(0)
            // "Mobile gewinnt": Eintrag wird NICHT still gelöscht, sondern landet
            // in der Reparatur-Liste, damit der Handwerker ihn sehen kann.
            expect(await OfflineService.getFailedCount()).toBe(1)
        })

        it('sollte 5xx als failed zählen und Entry behalten', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => serverError())
            await OfflineService.addPendingEntryWithOperationId('start', { token: 'a' }, '2024-06-15T10:00:00Z', 'op-500')

            const r = await OfflineService._syncPendingInternal()
            expect(r.failed).toBe(1)
            const pending = await OfflineService.getPendingEntries()
            expect(pending).toHaveLength(1)
            expect(pending[0].id).toBe('op-500')
            expect(pending[0].status).toBe('pending')
            expect(pending[0].attemptCount).toBe(1)
            expect(pending[0].lastError).toBe('HTTP 500')
        })

        // BUG #2: Netzwerkfehler mid-sync korrekt zählen
        it('sollte bei Netzwerkfehler verbleibende Entries korrekt zählen', async () => {
            let callCount = 0
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () => {
                callCount++
                if (callCount === 1) return { ok: true, status: 200, json: () => Promise.resolve({}) } as Response
                throw new Error('Network error')
            })

            await OfflineService.addPendingEntry('start', { token: 'a' }, '2024-01-01T08:00:00Z')
            await OfflineService.addPendingEntry('stop', { token: 'a' }, '2024-01-01T12:00:00Z')
            await OfflineService.addPendingEntry('start', { token: 'b' }, '2024-01-01T13:00:00Z')
            await OfflineService.addPendingEntry('stop', { token: 'b' }, '2024-01-01T17:00:00Z')

            const r = await OfflineService._syncPendingInternal()
            expect(r.synced).toBe(1)
            expect(r.failed).toBe(3) // current + 2 remaining
            expect(await OfflineService.getPendingCount()).toBe(3)
        })

        // CRITICAL: _syncPendingInternal darf Session NIEMALS berühren
        it('darf bei 4xx-Fehler localStorage Session NICHT löschen', async () => {
            localStorage.setItem('zeiterfassung_active_session', JSON.stringify({
                id: 'user-session', projektName: 'Aktiv', startTime: new Date().toISOString(),
            }))

            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(409))
            await OfflineService.addPendingEntry('stop', { token: 'a' })
            await OfflineService._syncPendingInternal()

            const session = JSON.parse(localStorage.getItem('zeiterfassung_active_session')!)
            expect(session.id).toBe('user-session')
        })

        it('sollte idempotencyKey und originalZeit senden', async () => {
            let sentBody: Record<string, unknown> | null = null
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (_, init) => {
                sentBody = JSON.parse((init as RequestInit).body as string)
                return { ok: true, status: 200, json: () => Promise.resolve({}) } as Response
            })

            const t = '2024-06-15T10:00:00.000Z'
            const entry = await OfflineService.addPendingEntry('start', { token: 'x' }, t)
            await OfflineService._syncPendingInternal()

            expect(sentBody.idempotencyKey).toBe(entry.id)
            expect(sentBody.originalZeit).toBe(t)
            expect(sentBody.token).toBe('x')
        })

        it('sollte gemischte Ergebnisse (ok/4xx/5xx) korrekt zählen', async () => {
            let callCount = 0
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () => {
                callCount++
                if (callCount === 1) return { ok: true, status: 200, json: () => Promise.resolve({}) } as Response
                if (callCount === 2) return { ok: false, status: 400, json: () => Promise.resolve({ error: 'Bad' }) } as Response
                return { ok: false, status: 503, json: () => Promise.resolve({}) } as Response
            })

            await OfflineService.addPendingEntry('start', { token: 'a' }, '2024-01-01T08:00:00Z')
            await OfflineService.addPendingEntry('stop', { token: 'a' }, '2024-01-01T12:00:00Z')
            await OfflineService.addPendingEntry('start', { token: 'b' }, '2024-01-01T13:00:00Z')

            const r = await OfflineService._syncPendingInternal()
            expect(r.synced).toBe(1)
            expect(r.discarded).toBe(1)
            expect(r.failed).toBe(1)
            expect(await OfflineService.getPendingCount()).toBe(1)
        })

        it('sollte inflight-Einträge beim nächsten sync wieder auf pending zurücksetzen', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => ok())
            const entry = await OfflineService.addPendingEntryWithOperationId('start', { token: 'a' }, '2024-06-15T10:00:00Z', 'op-inflight')
            await _setPendingEntryStatusForTesting(entry.id, 'inflight')

            const result = await OfflineService._syncPendingInternal()

            expect(result.synced).toBe(1)
            expect(await OfflineService.getPendingCount()).toBe(0)
        })
    })

    // ==================== EIN ABGLEICH ZUR ZEIT ====================
    describe('Gemeinsamer Abgleich', () => {
        it('zwei gleichzeitige syncAll-Aufrufe senden einen Eintrag nur einmal und liefern dasselbe Ergebnis', async () => {
            let resolveProjects!: () => void
            const projectsGate = new Promise<void>((resolve) => {
                resolveProjects = resolve
            })
            const posts: string[] = []

            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
                const requestUrl = typeof url === 'string' ? url : url.toString()
                if (init?.method === 'POST') posts.push(requestUrl)
                if (requestUrl.includes('/api/zeiterfassung/projekte')) {
                    await projectsGate
                }
                return { ok: true, status: 200, json: async () => [] } as unknown as Response
            })

            await OfflineService.addPendingEntry('stop', { token: 'max-mustermann' })
            const sync1 = OfflineService.syncAll()
            const sync2 = OfflineService.syncAll()
            resolveProjects()

            const [r1, r2] = await Promise.all([sync1, sync2])
            expect(posts).toHaveLength(1)
            // Kein erfundener Erfolg für den zweiten Aufrufer: dasselbe echte Ergebnis.
            expect(r2).toBe(r1)
            expect(r1.success).toBe(true)
            expect(r1.pendingSynced).toBe(1)
        })

        it('startet nach einem beendeten Abgleich einen neuen', async () => {
            const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() => ok([]))
            await OfflineService.syncAll()
            const aufrufeNachErstem = fetchSpy.mock.calls.length
            await OfflineService.syncAll()
            expect(fetchSpy.mock.calls.length).toBeGreaterThan(aufrufeNachErstem)
        })

        it('syncPending wartet auf einen laufenden syncAll und sendet nicht doppelt', async () => {
            const posts: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
                if (init?.method === 'POST') posts.push(String(url))
                return { ok: true, status: 200, json: async () => [] } as unknown as Response
            })
            await OfflineService.addPendingEntry('start', { token: 'max-mustermann' })

            const [alles, warteschlange] = await Promise.all([OfflineService.syncAll(), OfflineService.syncPending()])

            expect(posts).toHaveLength(1)
            expect(warteschlange).toMatchObject({ synced: alles.pendingSynced, skipped: false, startSynced: true })
        })

        it('syncPending sendet ohne laufenden Abgleich nur die Warteschlange', async () => {
            const urls: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
                urls.push(String(url))
                return { ok: true, status: 200, json: async () => ({}) } as unknown as Response
            })
            await OfflineService.addPendingEntry('stop', { token: 'max-mustermann' })

            const r = await OfflineService.syncPending()

            expect(r).toMatchObject({ synced: 1, failed: 0, discarded: 0, skipped: false })
            expect(urls.some(u => u.includes('/projekte'))).toBe(false)
        })

        it('syncPending meldet skipped, wenn der Server nicht erreichbar ist', async () => {
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))
            await OfflineService.addPendingEntry('stop', { token: 'max-mustermann' })

            const r = await OfflineService.syncPending()

            expect(r.skipped).toBe(true)
            expect(await OfflineService.getPendingCount()).toBe(1)
        })
    })

    // ==================== SYNC ALL ====================
    describe('syncAll', () => {
        it('sollte false bei nicht erreichbarem Server', async () => {
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('Network error'))
            const r = await OfflineService.syncAll()
            expect(r.success).toBe(false)
        })

        it('sollte Pending synchen und Caches auffrischen', async () => {
            const urls: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
                urls.push(typeof url === 'string' ? url : url.toString())
                return { ok: true, status: 200, json: () => Promise.resolve([]) } as Response
            })

            await OfflineService.addPendingEntry('start', { token: 'a' })
            const r = await OfflineService.syncAll()
            expect(r.success).toBe(true)
            expect(r.pendingSynced).toBe(1)
            expect(urls.some(u => u.includes('/projekte'))).toBe(true)
        })

        it('sollte allPendingCleared=false melden wenn während sync neue Pending-Entries entstehen', async () => {
            let injected = false

            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
                const requestUrl = typeof url === 'string' ? url : url.toString()

                if (init?.method === 'HEAD') {
                    return { ok: true, status: 200, json: async () => [] } as Response
                }

                if (requestUrl.includes('/api/zeiterfassung/start') && init?.method === 'POST') {
                    return { ok: true, status: 200, json: async () => ({}) } as Response
                }
                // Der spät eingereihte Stop scheitert im Nachlauf vorübergehend
                if (requestUrl.includes('/api/zeiterfassung/stop') && init?.method === 'POST') {
                    return { ok: false, status: 503, json: async () => ({}) } as Response
                }

                if (requestUrl.includes('/api/zeiterfassung/projekte') && !injected) {
                    injected = true
                    await OfflineService.addPendingEntry('stop', { token: 'late' }, '2024-01-01T18:00:00Z')
                }

                return { ok: true, status: 200, json: async () => [] } as Response
            })

            await OfflineService.addPendingEntry('start', { token: 'early' }, '2024-01-01T08:00:00Z')

            const result = await OfflineService.syncAll()

            expect(result.success).toBe(false)
            expect(result.pendingSynced).toBe(1)
            expect(result.allPendingCleared).toBe(false)
            expect(await OfflineService.getPendingCount()).toBe(1)
        })
    })

    // ==================== isOnline ====================
    describe('isOnline', () => {
        it('sollte navigator.onLine zurückgeben', () => {
            Object.defineProperty(navigator, 'onLine', { writable: true, value: true })
            expect(OfflineService.isOnline()).toBe(true)
            Object.defineProperty(navigator, 'onLine', { writable: true, value: false })
            expect(OfflineService.isOnline()).toBe(false)
        })
    })

    // ==================== FETCH WITH CACHE ====================
    describe('Fetch with Cache', () => {
        it('sollte bei Fetch-Erfolg Daten cachen', async () => {
            const data = [{ id: 1, name: 'Projekt A' }]
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => ok(data))
            const result = await OfflineService.getProjekte()
            expect(result).toEqual(data)
        })

        it('sollte bei Offline auf Cache zurückfallen', async () => {
            const data = [{ id: 1, name: 'Test' }]
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => ok(data))
            await OfflineService.getProjekte()

            // Jetzt offline
            vi.restoreAllMocks()
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))
            const cached = await OfflineService.getProjekte()
            expect(cached).toEqual(data)
        })

        it('sollte leeres Array wenn kein Cache und offline', async () => {
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))
            const result = await OfflineService.getProjekte()
            expect(result).toEqual([])
        })
    })

    // ==================== HEUTE GEARBEITET ====================
    describe('getHeuteGearbeitet', () => {
        it('sollte Server-Daten zurückgeben wenn online', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => ok({ stunden: 3, minuten: 45 }))
            const r = await OfflineService.getHeuteGearbeitet('tok')
            expect(r).toEqual({ stunden: 3, minuten: 45, fromCache: false, aktiveBuchungStartZeit: null })
        })

        it('sollte aktiveBuchungStartZeit durchreichen wenn vom Server geliefert', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => ok({
                stunden: 1, minuten: 30, aktiveBuchungStartZeit: '2024-06-15T08:00:00',
            }))
            const r = await OfflineService.getHeuteGearbeitet('tok')
            expect(r).toEqual({
                stunden: 1, minuten: 30, fromCache: false,
                aktiveBuchungStartZeit: '2024-06-15T08:00:00',
            })
        })

        it('sollte {0,0} wenn kein Cache und offline', async () => {
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))
            const r = await OfflineService.getHeuteGearbeitet('tok')
            expect(r).toEqual({ stunden: 0, minuten: 0, fromCache: true, aktiveBuchungStartZeit: null })
        })

        it('sollte gecachte Werte mit fromCache=true zurückgeben wenn offline', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() => ok({ stunden: 2, minuten: 15 }))
            await OfflineService.getHeuteGearbeitet('tok')

            vi.restoreAllMocks()
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))

            const r = await OfflineService.getHeuteGearbeitet('tok')
            expect(r).toEqual({ stunden: 2, minuten: 15, fromCache: true, aktiveBuchungStartZeit: null })
        })
    })

    describe('getTagesbuchungen', () => {
        it('sollte Tagesbuchungen online laden und cachen', async () => {
            const data = [{ id: 1, startMinuten: 480, endeMinuten: 540 }]
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => ok(data))

            const result = await OfflineService.getTagesbuchungen('tok', '2024-06-15')

            expect(result).toEqual({ buchungen: data, fromCache: false })
        })

        it('sollte gecachte Tagesbuchungen zurückgeben wenn offline', async () => {
            const data = [{ id: 7, startMinuten: 600, endeMinuten: 660 }]
            vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() => ok(data))
            await OfflineService.getTagesbuchungen('tok', '2024-06-15')

            vi.restoreAllMocks()
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))

            const result = await OfflineService.getTagesbuchungen('tok', '2024-06-15')

            expect(result).toEqual({ buchungen: data, fromCache: true })
        })

        it('sollte leere Tagesbuchungen aus Cache-Fallback liefern wenn kein Cache existiert', async () => {
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))

            const result = await OfflineService.getTagesbuchungen('tok', '2024-06-15')

            expect(result).toEqual({ buchungen: [], fromCache: true })
        })
    })

    // ==================== REPARATUR-LISTE ("Mobile gewinnt") ====================
    describe('Reparatur-Liste (Failed Entries)', () => {
        it('sollte 4xx-Eintrag in die Reparatur-Liste verschieben statt zu löschen', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(409, { error: 'Keine aktive Buchung gefunden' }))
            await OfflineService.addPendingEntryWithOperationId('stop', { token: 'a' }, '2024-06-15T17:00:00Z', 'op-failed', 60)

            const r = await OfflineService._syncPendingInternal()

            expect(r.discarded).toBe(1)
            expect(await OfflineService.getPendingCount()).toBe(0)
            const failed = await OfflineService.getFailedEntries()
            expect(failed).toHaveLength(1)
            expect(failed[0].id).toBe('op-failed')
            expect(failed[0].httpStatus).toBe(409)
            expect(failed[0].serverError).toBe('Keine aktive Buchung gefunden')
            expect(failed[0].durationMinutes).toBe(60)
        })

        it('sollte mehrere 4xx-Einträge in der Reparatur-Liste sammeln', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(404))
            await OfflineService.addPendingEntry('stop', { token: 'a' })
            await OfflineService.addPendingEntry('stop', { token: 'b' })

            await OfflineService._syncPendingInternal()

            expect(await OfflineService.getFailedCount()).toBe(2)
        })

        it('sollte 5xx-Einträge NICHT in die Reparatur-Liste verschieben', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => serverError(503))
            await OfflineService.addPendingEntry('stop', { token: 'a' })

            await OfflineService._syncPendingInternal()

            expect(await OfflineService.getFailedCount()).toBe(0)
            expect(await OfflineService.getPendingCount()).toBe(1)
        })

        it('removeFailedEntry sollte einen einzelnen Eintrag entfernen', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(404))
            const e1 = await OfflineService.addPendingEntry('stop', { token: 'a' })
            await OfflineService.addPendingEntry('stop', { token: 'b' })
            await OfflineService._syncPendingInternal()

            await OfflineService.removeFailedEntry(e1.id)

            const remaining = await OfflineService.getFailedEntries()
            expect(remaining).toHaveLength(1)
            expect(remaining[0].id).not.toBe(e1.id)
        })

        it('clearFailed sollte alle Einträge entfernen', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(404))
            await OfflineService.addPendingEntry('stop', { token: 'a' })
            await OfflineService.addPendingEntry('stop', { token: 'b' })
            await OfflineService._syncPendingInternal()

            await OfflineService.clearFailed()

            expect(await OfflineService.getFailedCount()).toBe(0)
        })

        it('darf bei 4xx-Fehler localStorage Session NICHT löschen', async () => {
            localStorage.setItem('zeiterfassung_active_session', JSON.stringify({
                projektName: 'Aktive Buchung', startTime: new Date().toISOString(),
            }))
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(409))
            await OfflineService.addPendingEntry('stop', { token: 'a' })

            await OfflineService._syncPendingInternal()

            const session = JSON.parse(localStorage.getItem('zeiterfassung_active_session')!)
            expect(session.projektName).toBe('Aktive Buchung')
            expect(await OfflineService.getFailedCount()).toBe(1)
        })
    })

    // ==================== HEUTE GEARBEITET (immer korrekt) ====================
    describe('getUnsyncedStopMinutes', () => {
        function isoToday(time: string): string {
            const parts = time.replace('Z', '').split(':').map(Number)
            const now = new Date()
            return new Date(now.getFullYear(), now.getMonth(), now.getDate(), parts[0] || 0, parts[1] || 0, parts[2] || 0).toISOString()
        }

        it('sollte 0 zurückgeben bei leeren Queues', async () => {
            expect(await OfflineService.getUnsyncedStopMinutes()).toBe(0)
        })

        it('sollte Minuten von pending Stop-Einträgen für heute summieren', async () => {
            await OfflineService.addPendingEntry('stop', { token: 'a' }, isoToday('12:00:00Z'), 90)
            await OfflineService.addPendingEntry('stop', { token: 'a' }, isoToday('17:00:00Z'), 240)

            expect(await OfflineService.getUnsyncedStopMinutes()).toBe(330)
        })

        it('sollte Minuten von pause-Einträgen ebenfalls zählen (= beendete Arbeitszeit)', async () => {
            await OfflineService.addPendingEntry('stop', { token: 'a' }, isoToday('12:00:00Z'), 120)
            await OfflineService.addPendingEntry('pause', { token: 'a' }, isoToday('15:00:00Z'), 60)

            expect(await OfflineService.getUnsyncedStopMinutes()).toBe(180)
        })

        it('sollte Start-Einträge ignorieren', async () => {
            await OfflineService.addPendingEntry('start', { token: 'a' }, isoToday('08:00:00Z'), 0)
            await OfflineService.addPendingEntry('stop', { token: 'a' }, isoToday('12:00:00Z'), 240)

            expect(await OfflineService.getUnsyncedStopMinutes()).toBe(240)
        })

        it('sollte auch Minuten aus der Reparatur-Liste summieren', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(409))
            await OfflineService.addPendingEntry('stop', { token: 'a' }, isoToday('12:00:00Z'), 150)
            await OfflineService._syncPendingInternal()

            // Eintrag ist jetzt in 'failed', nicht mehr in 'pending'
            expect(await OfflineService.getFailedCount()).toBe(1)
            expect(await OfflineService.getUnsyncedStopMinutes()).toBe(150)
        })

        it('sollte pending und failed gemeinsam summieren', async () => {
            // Eintrag, der gleich failed → 90 Minuten
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(409))
            await OfflineService.addPendingEntry('stop', { token: 'a' }, isoToday('11:00:00Z'), 90)
            await OfflineService._syncPendingInternal()

            // Weiterer Eintrag bleibt pending → 60 Minuten
            await OfflineService.addPendingEntry('stop', { token: 'a' }, isoToday('14:00:00Z'), 60)

            expect(await OfflineService.getUnsyncedStopMinutes()).toBe(150)
        })

        it('sollte Einträge von gestern NICHT mitzählen', async () => {
            const gestern = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()
            await OfflineService.addPendingEntry('stop', { token: 'a' }, gestern, 480)

            expect(await OfflineService.getUnsyncedStopMinutes()).toBe(0)
        })

        it('sollte Einträge ohne durationMinutes mit 0 werten', async () => {
            await OfflineService.addPendingEntry('stop', { token: 'a' }, isoToday('12:00:00Z'))
            expect(await OfflineService.getUnsyncedStopMinutes()).toBe(0)
        })
    })

    // ==================== Pending-Entry trägt durationMinutes ====================
    describe('Pending Entry durationMinutes', () => {
        it('addPendingEntry sollte durationMinutes speichern', async () => {
            const e = await OfflineService.addPendingEntry('stop', { token: 'a' }, undefined, 120)
            expect(e.durationMinutes).toBe(120)
        })

        it('addPendingEntryWithOperationId sollte durationMinutes speichern', async () => {
            const e = await OfflineService.addPendingEntryWithOperationId(
                'stop', { token: 'a' }, '2024-06-15T17:00:00Z', 'op-1', 180)
            expect(e.durationMinutes).toBe(180)
        })

        it('durationMinutes überlebt das Verschieben in die Reparatur-Liste', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(404))
            await OfflineService.addPendingEntryWithOperationId(
                'stop', { token: 'a' }, '2024-06-15T17:00:00Z', 'op-keep', 240)

            await OfflineService._syncPendingInternal()

            const failed = await OfflineService.getFailedEntries()
            expect(failed[0].durationMinutes).toBe(240)
        })
    })
    // ==================== VORÜBERGEHENDE FEHLER: REIHENFOLGE HALTEN ====================
    describe('Vorübergehende Fehler beim Abgleich', () => {
        it.each([408, 425, 429, 500, 503])('HTTP %i lässt den Eintrag in der Warteschlange und sendet nichts Späteres', async (status) => {
            const gesendet: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
                gesendet.push(String(url))
                return {
                    ok: false,
                    status,
                    json: async () => ({ error: 'Zu viele Anfragen. Bitte kurz warten.', retryAfterSeconds: 1 }),
                } as unknown as Response
            })

            await OfflineService.addPendingEntryWithOperationId('stop', { token: 'max-mustermann' }, '2026-10-16T16:00:00Z', 'op-feierabend', 480)
            await OfflineService.addPendingEntryWithOperationId('start', { token: 'max-mustermann' }, '2026-10-17T07:00:00Z', 'op-start')

            const r = await OfflineService._syncPendingInternal()

            // Nur der älteste Eintrag wurde versucht – der Start überholt den Feierabend nicht.
            expect(gesendet).toEqual(['/api/zeiterfassung/stop'])
            expect(r).toMatchObject({ synced: 0, discarded: 0, failed: 2, abgebrochen: true })
            expect(await OfflineService.getFailedCount()).toBe(0)
            const pending = await OfflineService.getPendingEntries()
            const feierabend = pending.find(e => e.id === 'op-feierabend')!
            expect(feierabend.status).toBe('pending')
            expect(feierabend.lastError).toBe(`HTTP ${status}`)
            expect(pending.map(e => e.id).sort()).toEqual(['op-feierabend', 'op-start'])
        })

        it('bricht eine hängende Anfrage über ein Zeitlimit ab und lässt den Eintrag wartend', async () => {
            let signal: AbortSignal | undefined
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (_url, init) => {
                signal = (init as RequestInit).signal ?? undefined
                // So meldet fetch den Abbruch durch das Zeitlimit.
                throw new DOMException('The operation was aborted.', 'AbortError')
            })
            await OfflineService.addPendingEntryWithOperationId('stop', { token: 'max-mustermann' }, '2026-10-16T16:00:00Z', 'op-haengt')

            const r = await OfflineService._syncPendingInternal()

            expect(signal).toBeInstanceOf(AbortSignal)
            expect(r).toMatchObject({ synced: 0, abgebrochen: true })
            expect((await OfflineService.getPendingEntries()).map(e => e.status)).toEqual(['pending'])
        })

        it('verschiebt einen Eintrag, der seit über einem Tag scheitert, in die Reparatur-Liste und sendet den Rest', async () => {
            const gesendet: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
                const id = JSON.parse(String((init as RequestInit).body)).idempotencyKey
                gesendet.push(`${String(url)}#${id}`)
                return id === 'op-kaputt'
                    ? { ok: false, status: 500, json: async () => ({}) } as unknown as Response
                    : { ok: true, status: 200, json: async () => ({}) } as unknown as Response
            })
            const db = await openDB('zeiterfassung-db', 2)
            const vorZweiTagen = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString()
            await db.put('pending', {
                id: 'op-kaputt', type: 'stop', data: { token: 'max-mustermann' }, timestamp: vorZweiTagen,
                originalTime: vorZweiTagen, status: 'pending', attemptCount: 30, durationMinutes: 45,
                serverfehlerVersuche: 19, ersterServerfehlerAm: vorZweiTagen,
            })
            db.close()
            await OfflineService.addPendingEntryWithOperationId('start', { token: 'max-mustermann' }, new Date().toISOString(), 'op-neu')

            const r = await OfflineService._syncPendingInternal()

            expect(gesendet).toEqual(['/api/zeiterfassung/stop#op-kaputt', '/api/zeiterfassung/start#op-neu'])
            expect(r).toMatchObject({ synced: 1, discarded: 1, abgebrochen: false })
            const failed = await OfflineService.getFailedEntries()
            expect(failed).toMatchObject([{ id: 'op-kaputt', httpStatus: 500, attemptCount: 31, durationMinutes: 45 }])
            expect(await OfflineService.getPendingCount()).toBe(0)
        })

        // Feierabend Freitag im Funkloch, Montag früh gleichen alle im Firmen-WLAN ab:
        // 429 ist immer vorübergehend – auch ein alter, oft versuchter Eintrag bleibt wartend.
        it('verschiebt einen alten Eintrag bei 429 nie in die Reparatur-Liste', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
                ({ ok: false, status: 429, json: async () => ({ error: 'Zu viele Anfragen. Bitte kurz warten.' }) }) as unknown as Response)
            const db = await openDB('zeiterfassung-db', 2)
            const vorDreiTagen = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString()
            await db.put('pending', {
                id: 'op-freitag', type: 'stop', data: { token: 'max-mustermann' }, timestamp: vorDreiTagen,
                originalTime: vorDreiTagen, status: 'pending', attemptCount: 50, durationMinutes: 480,
            })
            db.close()

            const r = await OfflineService._syncPendingInternal()

            expect(r).toMatchObject({ discarded: 0, abgebrochen: true })
            expect(await OfflineService.getFailedCount()).toBe(0)
            const [eintrag] = await OfflineService.getPendingEntries()
            expect(eintrag).toMatchObject({ id: 'op-freitag', status: 'pending', lastError: 'HTTP 429' })
            expect(eintrag.serverfehlerVersuche).toBeUndefined()
        })

        it('zählt Serverfehler mit, ohne den Eintrag beim ersten Mal zu verschieben', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
                ({ ok: false, status: 503, json: async () => ({}) }) as unknown as Response)
            await OfflineService.addPendingEntryWithOperationId('stop', { token: 'max-mustermann' }, '2026-10-16T16:00:00Z', 'op-503')

            await OfflineService._syncPendingInternal()

            const [eintrag] = await OfflineService.getPendingEntries()
            expect(eintrag.serverfehlerVersuche).toBe(1)
            expect(eintrag.ersterServerfehlerAm).toEqual(expect.any(String))
            expect(await OfflineService.getFailedCount()).toBe(0)
        })

        it('syncAll meldet bei 429 keinen Erfolg und lädt keine Stammdaten nach', async () => {
            const urls: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
                urls.push(String(url))
                if (init?.method === 'POST') return { ok: false, status: 429, json: async () => ({}) } as unknown as Response
                return { ok: true, status: 200, json: async () => [] } as unknown as Response
            })
            await OfflineService.addPendingEntry('stop', { token: 'max-mustermann' })

            const r = await OfflineService.syncAll()

            expect(r.success).toBe(false)
            expect(r.allPendingCleared).toBe(false)
            expect(urls.some(u => u.includes('/projekte'))).toBe(false)
            expect(await OfflineService.getPendingCount()).toBe(1)
        })

        it('verschiebt eine echte Ablehnung (400) in die Reparatur-Liste und macht mit dem Nächsten weiter', async () => {
            const gesendet: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
                gesendet.push(String(url))
                if (String(url).includes('/stop')) return clientError(400, { error: 'Keine aktive Buchung gefunden' })
                return ok({ id: 1 })
            })
            await OfflineService.addPendingEntryWithOperationId('stop', { token: 'max-mustermann' }, '2026-10-16T16:00:00Z', 'op-stop', 30)
            await OfflineService.addPendingEntryWithOperationId('start', { token: 'max-mustermann' }, '2026-10-17T07:00:00Z', 'op-start')

            const r = await OfflineService._syncPendingInternal()

            expect(gesendet).toEqual(['/api/zeiterfassung/stop', '/api/zeiterfassung/start'])
            expect(r).toMatchObject({ synced: 1, discarded: 1, failed: 0, abgebrochen: false })
            const failed = await OfflineService.getFailedEntries()
            expect(failed).toHaveLength(1)
            expect(failed[0]).toMatchObject({ id: 'op-stop', httpStatus: 400, serverError: 'Keine aktive Buchung gefunden', durationMinutes: 30 })
        })

        it('sendet bei gleichem Zeitpunkt erst den Stop, dann den Start (Wechsel)', async () => {
            const gesendet: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
                gesendet.push(String(url))
                return ok({})
            })
            const zeit = '2026-10-17T10:00:00.000Z'
            await OfflineService.addPendingEntryWithOperationId('start', { token: 'max-mustermann' }, zeit, '00000000-start')
            await OfflineService.addPendingEntryWithOperationId('stop', { token: 'max-mustermann' }, zeit, 'ffffffff-stop')

            await OfflineService._syncPendingInternal()

            expect(gesendet).toEqual(['/api/zeiterfassung/stop', '/api/zeiterfassung/start'])
        })

        it('istVoruebergehenderFehler trennt „später nochmal“ von echten Ablehnungen', () => {
            expect([408, 425, 429, 500, 502, 503].every(istVoruebergehenderFehler)).toBe(true)
            expect([400, 401, 403, 404, 409, 422].some(istVoruebergehenderFehler)).toBe(false)
        })
    })

    // ==================== STAMMDATEN: GATEWAY NICHT FLUTEN ====================
    describe('Kategorien-Vorladen', () => {
        it('lädt die Kategorien je Projekt nacheinander im Hintergrund, ohne den Abgleich aufzuhalten', async () => {
            let gleichzeitig = 0
            let hoechstens = 0
            const projektKategorien: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
                const u = String(url)
                if (u.includes('/api/zeiterfassung/projekte')) {
                    return { ok: true, status: 200, json: async () => [{ id: 1 }, { id: 2 }, { id: 3 }] } as unknown as Response
                }
                if (/\/api\/zeiterfassung\/kategorien\/\d+$/.test(u)) {
                    projektKategorien.push(u)
                    gleichzeitig++
                    hoechstens = Math.max(hoechstens, gleichzeitig)
                    await new Promise(resolve => setTimeout(resolve, 5))
                    gleichzeitig--
                }
                return { ok: true, status: 200, json: async () => [] } as unknown as Response
            })

            const r = await OfflineService.syncAll()

            expect(r.success).toBe(true)
            // Der Abgleich ist fertig, bevor alle Projekt-Kategorien geladen sind.
            expect(projektKategorien.length).toBeLessThan(3)
            await _warteAufVorladenForTesting()
            expect(projektKategorien).toEqual([
                '/api/zeiterfassung/kategorien/1',
                '/api/zeiterfassung/kategorien/2',
                '/api/zeiterfassung/kategorien/3',
            ])
            expect(hoechstens).toBe(1)
        })

        it('lädt die Projekt-Kategorien höchstens alle 10 Minuten vor', async () => {
            const projektKategorien: string[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
                const u = String(url)
                if (u.includes('/api/zeiterfassung/projekte')) {
                    return { ok: true, status: 200, json: async () => [{ id: 1 }] } as unknown as Response
                }
                if (/\/api\/zeiterfassung\/kategorien\/\d+$/.test(u)) projektKategorien.push(u)
                return { ok: true, status: 200, json: async () => [] } as unknown as Response
            })

            await OfflineService.syncAll()
            await _warteAufVorladenForTesting()
            await OfflineService.syncAll()
            await _warteAufVorladenForTesting()

            expect(projektKategorien).toEqual(['/api/zeiterfassung/kategorien/1'])
        })

        it('schickt im Nachlauf mit, was während des Stammdaten-Abrufs eingereiht wurde', async () => {
            const posts: string[] = []
            let eingereiht = false
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
                const u = String(url)
                if (init?.method === 'POST') posts.push(u)
                if (u.includes('/api/zeiterfassung/projekte')) {
                    return { ok: true, status: 200, json: async () => [{ id: 1 }] } as unknown as Response
                }
                if (u.startsWith('/api/kunden') && !eingereiht) {
                    eingereiht = true
                    await OfflineService.addPendingEntry('stop', { token: 'max-mustermann' })
                }
                return { ok: true, status: 200, json: async () => [] } as unknown as Response
            })

            const r = await OfflineService.syncAll()

            expect(posts).toEqual(['/api/zeiterfassung/stop'])
            expect(r).toMatchObject({ success: true, pendingSynced: 1, allPendingCleared: true })
        })
    })

    // ==================== sendeBuchung ====================
    describe('sendeBuchung', () => {
        const antwort = (status: number, body: unknown) =>
            ({ ok: status >= 200 && status < 300, status, json: async () => body }) as unknown as Response

        it('sendet direkt mit keepalive und liefert die Serverdaten', async () => {
            const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => antwort(200, { id: 42, startZeit: '2026-10-17T07:00:00' }))

            const r = await sendeBuchung('start', { token: 'max-mustermann', projektId: 7 }, {
                originalTime: '2026-10-17T07:00:00Z', operationId: 'op-1', ablehnungInReparaturListe: false,
            })

            expect(r).toEqual({ art: 'ok', daten: { id: 42, startZeit: '2026-10-17T07:00:00' } })
            const [url, init] = fetchSpy.mock.calls[0]
            expect(url).toBe('/api/zeiterfassung/start')
            expect(init).toMatchObject({ method: 'POST', keepalive: true })
            expect((init as RequestInit).signal).toBeUndefined()
            expect(JSON.parse((init as RequestInit).body as string)).toEqual({
                token: 'max-mustermann', projektId: 7, originalZeit: '2026-10-17T07:00:00Z', idempotencyKey: 'op-1',
            })
            expect(await OfflineService.getPendingCount()).toBe(0)
        })

        it('liefert ok mit leeren Daten, wenn die Erfolgsantwort kein JSON ist', async () => {
            vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, status: 200, json: async () => { throw new SyntaxError('kein json') } } as unknown as Response)

            const r = await sendeBuchung('stop', { token: 'max-mustermann' }, { ablehnungInReparaturListe: true })

            expect(r).toEqual({ art: 'ok', daten: {} })
            expect(await OfflineService.getPendingCount()).toBe(0)
        })

        it('stellt sich hinter wartende Einträge, statt sie zu überholen', async () => {
            const fetchSpy = vi.spyOn(globalThis, 'fetch')
            const anstoesse = vi.fn()
            window.addEventListener(ABGLEICH_ANSTOSSEN_EVENT, anstoesse)
            await OfflineService.addPendingEntryWithOperationId('start', { token: 'max-mustermann' }, '2026-10-17T07:00:00Z', 'op-alt')

            const r = await sendeBuchung('stop', { token: 'max-mustermann' }, {
                originalTime: '2026-10-17T16:00:00Z', operationId: 'op-feierabend', durationMinutes: 540, ablehnungInReparaturListe: true,
            })
            window.removeEventListener(ABGLEICH_ANSTOSSEN_EVENT, anstoesse)

            expect(r).toEqual({ art: 'eingereiht' })
            expect(fetchSpy).not.toHaveBeenCalled()
            expect(anstoesse).toHaveBeenCalledTimes(1)
            const neu = (await OfflineService.getPendingEntries()).find(e => e.id === 'op-feierabend')
            expect(neu).toMatchObject({ type: 'stop', originalTime: '2026-10-17T16:00:00Z', durationMinutes: 540 })
        })

        it('reiht nach dem Zeitlimit mit derselben ID ein und bricht die Anfrage nicht ab', async () => {
            let signal: AbortSignal | null | undefined
            let antworten!: (r: Response) => void
            vi.spyOn(globalThis, 'fetch').mockImplementation((_url, init) => {
                signal = (init as RequestInit).signal
                return new Promise<Response>(resolve => { antworten = resolve })
            })

            const r = await sendeBuchung('stop', { token: 'max-mustermann' }, {
                originalTime: '2026-10-17T16:00:00Z', operationId: 'op-langsam', timeoutMs: 20, ablehnungInReparaturListe: true,
            })

            expect(r).toEqual({ art: 'eingereiht' })
            expect(signal).toBeUndefined()
            const pending = await OfflineService.getPendingEntries()
            expect(pending.map(e => e.id)).toEqual(['op-langsam'])
            // Die Anfrage darf später noch ankommen – das ist dank Idempotenz harmlos.
            antworten(antwort(200, {}))
        })

        it.each([429, 503])('reiht bei HTTP %i ein und stößt einen Abgleich an', async (status) => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () => antwort(status, { error: 'Zu viele Anfragen. Bitte kurz warten.' }))
            const anstoesse = vi.fn()
            window.addEventListener(ABGLEICH_ANSTOSSEN_EVENT, anstoesse)

            const r = await sendeBuchung('stop', { token: 'max-mustermann' }, { operationId: 'op-429', ablehnungInReparaturListe: true })
            window.removeEventListener(ABGLEICH_ANSTOSSEN_EVENT, anstoesse)

            expect(r).toEqual({ art: 'eingereiht' })
            expect(anstoesse).toHaveBeenCalledTimes(1)
            expect((await OfflineService.getPendingEntries()).map(e => e.id)).toEqual(['op-429'])
            expect(await OfflineService.getFailedCount()).toBe(0)
        })

        // Android friert die App direkt nach dem Feierabend-Tipp ein: Die Buchung muss
        // schon VOR der Antwort auf dem Handy stehen, sonst ist sie beim Beenden weg.
        it('speichert die Buchung vor dem Senden und entfernt sie nach Erfolg', async () => {
            let waehrendDesSendens: Array<{ id: string; status: string }> = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () => {
                waehrendDesSendens = (await OfflineService.getPendingEntries()).map(e => ({ id: e.id, status: e.status }))
                return antwort(200, {})
            })

            const r = await sendeBuchung('stop', { token: 'max-mustermann' }, {
                operationId: 'op-zuerst', durationMinutes: 30, ablehnungInReparaturListe: true,
            })

            expect(r.art).toBe('ok')
            expect(waehrendDesSendens).toEqual([{ id: 'op-zuerst', status: 'inflight' }])
            expect(await OfflineService.getPendingCount()).toBe(0)
        })

        it('gibt eine beim Senden abgebrochene Buchung beim nächsten Abgleich frei und sendet sie erneut', async () => {
            // App wurde während der Anfrage beendet: Eintrag bleibt 'inflight' liegen.
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(() => {}))
            void sendeBuchung('stop', { token: 'max-mustermann' }, { operationId: 'op-eingefroren', ablehnungInReparaturListe: true })
            await vi.waitFor(async () => expect(await OfflineService.getPendingCount()).toBe(1))
            vi.restoreAllMocks()
            // Neustart der App: Was sie gerade direkt sendete, weiß sie danach nicht mehr.
            _resetForTesting()

            const posts: unknown[] = []
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (_url, init) => {
                if (init?.method === 'POST') posts.push(JSON.parse(String(init.body)).idempotencyKey)
                return antwort(200, {})
            })
            const r = await OfflineService._syncPendingInternal()

            expect(r.synced).toBe(1)
            expect(posts).toEqual(['op-eingefroren'])
            expect(await OfflineService.getPendingCount()).toBe(0)
        })

        it('ein Abgleich während der Direkt-Sendung schickt die Buchung nicht ein zweites Mal', async () => {
            // CI-Befund 19.10.2026: Der Feierabend ging dreimal raus statt zweimal.
            const posts: string[] = []
            let antworten!: (r: Response) => void
            vi.spyOn(globalThis, 'fetch').mockImplementation((_url, init) => {
                posts.push(JSON.parse(String((init as RequestInit).body)).idempotencyKey)
                return posts.length === 1
                    ? new Promise<Response>(resolve => { antworten = resolve })
                    : Promise.resolve(antwort(200, {}))
            })
            const anstoesse = vi.fn()
            window.addEventListener(ABGLEICH_ANSTOSSEN_EVENT, anstoesse)

            const direkt = sendeBuchung('stop', { token: 'max-mustermann' }, {
                originalTime: '2026-10-17T16:00:00Z', operationId: 'op-feierabend', ablehnungInReparaturListe: true,
            })
            await vi.waitFor(() => expect(posts).toHaveLength(1))

            // Online-Ereignis o. Ä. startet mittendrin einen Abgleich – der wartet ab.
            const abgleich = await OfflineService._syncPendingInternal()
            expect(abgleich).toMatchObject({ synced: 0, failed: 0, wartetAufDirektSendung: true })
            expect(posts).toEqual(['op-feierabend'])

            // Während der Sendung tippt der Handwerker schon „Start“: das reiht sich ein.
            await sendeBuchung('start', { token: 'max-mustermann' }, {
                originalTime: '2026-10-17T16:01:00Z', operationId: 'op-start', ablehnungInReparaturListe: true,
            })
            anstoesse.mockClear()

            antworten(antwort(200, {}))
            expect(await direkt).toEqual({ art: 'ok', daten: {} })
            // Danach stößt die App den Abgleich für den Rest selbst an.
            await vi.waitFor(() => expect(anstoesse).toHaveBeenCalled())
            window.removeEventListener(ABGLEICH_ANSTOSSEN_EVENT, anstoesse)

            const rest = await OfflineService._syncPendingInternal()
            expect(rest.synced).toBe(1)
            expect(posts).toEqual(['op-feierabend', 'op-start'])
            expect(await OfflineService.getPendingCount()).toBe(0)
        })

        it('ein Abgleich, der nur auf die Direkt-Sendung wartet, gilt nicht als Fehler', async () => {
            let antworten!: (r: Response) => void
            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
                if (String(url).includes('/api/zeiterfassung/stop')) return new Promise<Response>(resolve => { antworten = resolve })
                return antwort(200, [])
            })
            const direkt = sendeBuchung('stop', { token: 'max-mustermann' }, { operationId: 'op-warten', ablehnungInReparaturListe: true })
            await vi.waitFor(async () => expect(await OfflineService.getPendingCount()).toBe(1))

            const ergebnis = await OfflineService.syncAll()

            expect(ergebnis).toMatchObject({ success: true, pendingFailed: 0 })
            antworten(antwort(200, {}))
            await direkt
        })

        it('holt eine schon übertragene Buchung nach 429 nicht zurück in die Warteschlange', async () => {
            let antworten!: (r: Response) => void
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(resolve => { antworten = resolve }))

            const direkt = sendeBuchung('stop', { token: 'max-mustermann' }, { operationId: 'op-429-spaet', ablehnungInReparaturListe: true })
            await vi.waitFor(async () => expect(await OfflineService.getPendingCount()).toBe(1))
            // Ein anderer Weg (z. B. der Hintergrund-Abgleich) hat sie inzwischen übertragen.
            await OfflineService.clearPending()

            antworten(antwort(429, { error: 'Zu viele Anfragen. Bitte kurz warten.' }))
            expect(await direkt).toEqual({ art: 'eingereiht' })
            expect(await OfflineService.getPendingCount()).toBe(0)
        })

        it('entfernt die vorab gespeicherte Buchung bei einer echten Ablehnung aus der Warteschlange', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () => antwort(400, { error: 'Keine aktive Buchung gefunden' }))

            await sendeBuchung('stop', { token: 'max-mustermann' }, { operationId: 'op-nein', ablehnungInReparaturListe: false })

            expect(await OfflineService.getPendingCount()).toBe(0)
            expect(await OfflineService.getFailedCount()).toBe(0)
        })

        it('reiht bei Netzwerkfehler ein', async () => {
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))

            const r = await sendeBuchung('pause', { token: 'max-mustermann' }, { operationId: 'op-netz', ablehnungInReparaturListe: true })

            expect(r).toEqual({ art: 'eingereiht' })
            expect((await OfflineService.getPendingEntries())[0]).toMatchObject({ id: 'op-netz', type: 'pause' })
        })

        it('schreibt eine echte Ablehnung in die Reparatur-Liste, wenn gewünscht', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () => antwort(400, { error: 'Keine aktive Buchung gefunden' }))

            const r = await sendeBuchung('stop', { token: 'max-mustermann' }, {
                originalTime: '2026-10-17T16:00:00Z', operationId: 'op-abgelehnt', durationMinutes: 120, ablehnungInReparaturListe: true,
            })

            expect(r).toEqual({ art: 'abgelehnt', status: 400, meldung: 'Keine aktive Buchung gefunden' })
            expect(await OfflineService.getPendingCount()).toBe(0)
            const failed = await OfflineService.getFailedEntries()
            expect(failed).toHaveLength(1)
            expect(failed[0]).toMatchObject({
                id: 'op-abgelehnt', type: 'stop', httpStatus: 400, serverError: 'Keine aktive Buchung gefunden',
                originalTime: '2026-10-17T16:00:00Z', durationMinutes: 120,
            })
        })

        it('speichert bei einer Ablehnung nichts, wenn die Reparatur-Liste nicht gewünscht ist', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () => antwort(400, { error: 'Es läuft bereits eine Buchung. Bitte erst stoppen.' }))

            const r = await sendeBuchung('start', { token: 'max-mustermann' }, { ablehnungInReparaturListe: false })

            expect(r).toEqual({ art: 'abgelehnt', status: 400, meldung: 'Es läuft bereits eine Buchung. Bitte erst stoppen.' })
            expect(await OfflineService.getPendingCount()).toBe(0)
            expect(await OfflineService.getFailedCount()).toBe(0)
        })

        it('nimmt eine Ersatzmeldung, wenn der Server keine lesbare Meldung schickt', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () => antwort(403, {}))

            const r = await sendeBuchung('start', { token: 'max-mustermann' }, { ablehnungInReparaturListe: false })

            expect(r).toEqual({ art: 'abgelehnt', status: 403, meldung: 'Der Server hat die Buchung nicht angenommen.' })
        })

        it('startZeitAusErgebnis nimmt die Serverzeit, sonst den eigenen Zeitpunkt', () => {
            expect(startZeitAusErgebnis({ art: 'ok', daten: { startZeit: '2026-10-17T07:01:00' } }, 'ersatz')).toBe('2026-10-17T07:01:00')
            expect(startZeitAusErgebnis({ art: 'ok', daten: {} }, 'ersatz')).toBe('ersatz')
            expect(startZeitAusErgebnis({ art: 'eingereiht' }, 'ersatz')).toBe('ersatz')
        })
    })

    // ==================== HINTERGRUND-ÜBERTRAGUNG (Android) ====================
    describe('hintergrundUebertragungAnmelden', () => {
        const setzeServiceWorker = (wert: unknown) =>
            Object.defineProperty(navigator, 'serviceWorker', { configurable: true, value: wert })

        afterEach(() => {
            Reflect.deleteProperty(navigator, 'serviceWorker')
        })

        it('meldet beim Browser einen Hintergrund-Sync für die Buchungen an', async () => {
            const register = vi.fn().mockResolvedValue(undefined)
            setzeServiceWorker({ getRegistration: vi.fn().mockResolvedValue({ sync: { register } }) })

            await expect(hintergrundUebertragungAnmelden()).resolves.toBe(true)
            expect(register).toHaveBeenCalledWith('buchungen-senden')
        })

        it('liefert false, wenn der Browser keinen Hintergrund-Sync kann (iPhone)', async () => {
            setzeServiceWorker({ getRegistration: vi.fn().mockResolvedValue({}) })
            await expect(hintergrundUebertragungAnmelden()).resolves.toBe(false)
        })

        it('liefert false ohne Service Worker oder bei Fehlern', async () => {
            await expect(hintergrundUebertragungAnmelden()).resolves.toBe(false)
            setzeServiceWorker({ getRegistration: vi.fn().mockRejectedValue(new Error('kaputt')) })
            await expect(hintergrundUebertragungAnmelden()).resolves.toBe(false)
        })

        // Handy wird gesperrt, bevor die Antwort da ist: Die Anmeldung muss schon stehen.
        it('sendeBuchung meldet die Hintergrund-Übertragung schon vor dem Senden an', async () => {
            const ablauf: string[] = []
            const register = vi.fn(async () => { ablauf.push('angemeldet') })
            setzeServiceWorker({ getRegistration: vi.fn().mockResolvedValue({ sync: { register } }) })
            vi.spyOn(globalThis, 'fetch').mockImplementation(async () => {
                await vi.waitFor(() => expect(register).toHaveBeenCalled())
                ablauf.push('gesendet')
                return { ok: true, status: 200, json: async () => ({}) } as unknown as Response
            })

            await sendeBuchung('stop', { token: 'max-mustermann' }, { operationId: 'op-frueh', ablehnungInReparaturListe: true })

            expect(ablauf).toEqual(['angemeldet', 'gesendet'])
        })

        it('abgleichAnstossen meldet die Hintergrund-Übertragung gleich mit an', async () => {
            const register = vi.fn().mockResolvedValue(undefined)
            setzeServiceWorker({ getRegistration: vi.fn().mockResolvedValue({ sync: { register } }) })

            abgleichAnstossen()

            await vi.waitFor(() => expect(register).toHaveBeenCalledWith('buchungen-senden'))
        })
    })

    describe('abgleichAnstossen & speicherDauerhaftAnfordern', () => {
        it('abgleichAnstossen löst das Anstoß-Event aus', () => {
            const horcher = vi.fn()
            window.addEventListener(ABGLEICH_ANSTOSSEN_EVENT, horcher)
            abgleichAnstossen()
            window.removeEventListener(ABGLEICH_ANSTOSSEN_EVENT, horcher)
            expect(horcher).toHaveBeenCalledTimes(1)
        })

        it('bittet den Browser um dauerhaften Speicher', async () => {
            const persist = vi.fn(async () => true)
            vi.stubGlobal('navigator', { ...navigator, storage: { persist, persisted: async () => false } })
            expect(await speicherDauerhaftAnfordern()).toBe(true)
            expect(persist).toHaveBeenCalledTimes(1)
        })

        it('fragt nicht erneut, wenn der Speicher schon dauerhaft ist', async () => {
            const persist = vi.fn(async () => true)
            vi.stubGlobal('navigator', { ...navigator, storage: { persist, persisted: async () => true } })
            expect(await speicherDauerhaftAnfordern()).toBe(true)
            expect(persist).not.toHaveBeenCalled()
        })

        it('schluckt fehlende Unterstützung und Fehler', async () => {
            vi.stubGlobal('navigator', { ...navigator, storage: undefined })
            expect(await speicherDauerhaftAnfordern()).toBe(false)
            vi.stubGlobal('navigator', { ...navigator, storage: { persist: async () => { throw new Error('nicht erlaubt') } } })
            expect(await speicherDauerhaftAnfordern()).toBe(false)
        })
    })
})
