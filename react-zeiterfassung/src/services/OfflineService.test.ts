import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import 'fake-indexeddb/auto'
import {
    buildBookingRequestPayload,
    createOperationId,
    OfflineService,
    _resetForTesting,
    _setPendingEntryStatusForTesting,
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

    // ==================== REMOVE PENDING BY TYPE ====================
    describe('removePendingEntriesByType', () => {
        it('sollte nur Einträge des angegebenen Typs entfernen', async () => {
            await OfflineService.addPendingEntry('start', { token: 'a' })
            await OfflineService.addPendingEntry('stop', { token: 'a' })
            await OfflineService.addPendingEntry('start', { token: 'b' })

            await OfflineService.removePendingEntriesByType('start')

            const remaining = await OfflineService.getPendingEntries()
            expect(remaining).toHaveLength(1)
            expect(remaining[0].type).toBe('stop')
        })

        it('sollte bei leerer Queue nichts tun', async () => {
            await expect(OfflineService.removePendingEntriesByType('stop')).resolves.not.toThrow()
        })

        it('sollte idempotent sein (doppelter Aufruf kein Fehler)', async () => {
            await OfflineService.addPendingEntry('stop', { token: 'a' })
            await OfflineService.removePendingEntriesByType('stop')
            await expect(OfflineService.removePendingEntriesByType('stop')).resolves.not.toThrow()
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
            expect(r).toEqual({ synced: 0, failed: 0, discarded: 0, startSynced: false })
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

    // ==================== SYNC LOCK ====================
    describe('Sync Lock', () => {
        it('sollte parallele syncAll-Aufrufe blockieren', async () => {
            let resolveProjects!: () => void
            const projectsGate = new Promise<void>((resolve) => {
                resolveProjects = resolve
            })

            vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
                const requestUrl = typeof url === 'string' ? url : url.toString()

                if (init?.method === 'HEAD') {
                    return { ok: true, status: 200, json: async () => [] } as Response
                }

                if (requestUrl.includes('/api/zeiterfassung/projekte')) {
                    await projectsGate
                    return { ok: true, status: 200, json: async () => [] } as Response
                }

                return { ok: true, status: 200, json: async () => [] } as Response
            })

            const sync1 = OfflineService.syncAll()
            const sync2 = OfflineService.syncAll()

            // Give sync1 time to acquire the lock and enter the long-running branch.
            await Promise.resolve()
            resolveProjects()

            const [r1, r2] = await Promise.all([sync1, sync2])
            expect(r1.success).toBe(true)
            expect(r2.success).toBe(true)
            expect(r2.pendingSynced).toBe(0)
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

                if (requestUrl.includes('/api/zeiterfassung/projekte') && !injected) {
                    injected = true
                    await OfflineService.addPendingEntry('stop', { token: 'late' }, '2024-01-01T18:00:00Z')
                }

                return { ok: true, status: 200, json: async () => [] } as Response
            })

            await OfflineService.addPendingEntry('start', { token: 'early' }, '2024-01-01T08:00:00Z')

            const result = await OfflineService.syncAll()

            expect(result.success).toBe(true)
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
            const today = new Date().toISOString().split('T')[0]
            return `${today}T${time}`
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
})
