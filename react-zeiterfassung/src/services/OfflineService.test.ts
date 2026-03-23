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

        it('sollte 4xx als discarded zählen und Entry löschen', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementation(() => clientError(409))
            await OfflineService.addPendingEntry('stop', { token: 'a' })

            const r = await OfflineService._syncPendingInternal()
            expect(r.discarded).toBe(1)
            expect(r.synced).toBe(0)
            expect(await OfflineService.getPendingCount()).toBe(0)
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

    // ==================== OFFLINE HOURS TRACKING ====================
    describe('Offline Hours Tracking', () => {
        it('sollte Offline-Minuten korrekt addieren', async () => {
            await OfflineService.addOfflineWorkedMinutes(30)
            await OfflineService.addOfflineWorkedMinutes(45)
            expect(await OfflineService.getOfflineHeuteMinuten()).toBe(75)
        })

        it('sollte clearOfflineHeuteMinuten auf 0 setzen', async () => {
            await OfflineService.addOfflineWorkedMinutes(120)
            await OfflineService.clearOfflineHeuteMinuten()
            expect(await OfflineService.getOfflineHeuteMinuten()).toBe(0)
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
            expect(r).toEqual({ stunden: 3, minuten: 45, fromCache: false })
        })

        it('sollte {0,0} wenn kein Cache und offline', async () => {
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))
            const r = await OfflineService.getHeuteGearbeitet('tok')
            expect(r).toEqual({ stunden: 0, minuten: 0, fromCache: true })
        })

        it('sollte gecachte Werte mit fromCache=true zurückgeben wenn offline', async () => {
            vi.spyOn(globalThis, 'fetch').mockImplementationOnce(() => ok({ stunden: 2, minuten: 15 }))
            await OfflineService.getHeuteGearbeitet('tok')

            vi.restoreAllMocks()
            vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))

            const r = await OfflineService.getHeuteGearbeitet('tok')
            expect(r).toEqual({ stunden: 2, minuten: 15, fromCache: true })
        })
    })
})
