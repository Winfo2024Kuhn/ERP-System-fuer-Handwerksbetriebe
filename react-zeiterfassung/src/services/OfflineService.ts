// Offline-First Data Service
// Caches data locally using IndexedDB and syncs when online
// PRINCIPLE: Client ALWAYS wins when pending entries exist.
// The local session is NEVER deleted by sync logic.
import { openDB, type DBSchema, type IDBPDatabase } from 'idb'

interface ZeiterfassungDB extends DBSchema {
    pending: {
        key: string
        value: PendingEntry
    }
    master_data: {
        key: string
        value: {
            key: string
            value: unknown
        }
    }
}

interface ProjektCacheEntry {
    name: string
    projektNummer?: string
}

interface LieferantCacheEntry {
    firmenname: string
}

interface KundenCacheEntry {
    name: string
}

interface PendingEntry {
    id: string // Also used as idempotency key on the server
    type: 'start' | 'stop' | 'pause'
    data: Record<string, unknown>
    timestamp: string
    originalTime: string // ISO-String des tatsächlichen Zeitpunkts (für Offline-Sync)
    status: 'pending' | 'inflight'
    attemptCount: number
    lastAttemptAt?: string
    lastError?: string
}

export interface SyncResult {
    success: boolean
    pendingSynced: number
    pendingFailed: number
    pendingDiscarded: number
    allPendingCleared: boolean // true = no pending entries remain
    startSynced: boolean // true = at least one 'start' entry was successfully synced
}

export interface HeuteGearbeitetResult {
    stunden: number
    minuten: number
    fromCache: boolean
}

export function createOperationId() {
    return generateUUID()
}

export function buildBookingRequestPayload(
    data: Record<string, unknown>,
    originalTime: string,
    operationId: string,
) {
    return {
        ...data,
        originalZeit: originalTime,
        idempotencyKey: operationId,
    }
}

const DB_NAME = 'zeiterfassung-db'
const DB_VERSION = 1

let dbPromise: Promise<IDBPDatabase<ZeiterfassungDB>>

const initDB = () => {
    if (!dbPromise) {
        dbPromise = openDB<ZeiterfassungDB>(DB_NAME, DB_VERSION, {
            upgrade(db) {
                if (!db.objectStoreNames.contains('pending')) {
                    db.createObjectStore('pending', { keyPath: 'id' })
                }
                if (!db.objectStoreNames.contains('master_data')) {
                    db.createObjectStore('master_data', { keyPath: 'key' })
                }
            },
        })
    }
    return dbPromise
}

const CACHE_KEYS = {
    projekte: 'projekte',
    kategorien: 'kategorien',
    arbeitsgaenge: 'arbeitsgaenge', // Global list
    arbeitsgaenge_personal: 'arbeitsgaenge_personal', // User specific list
    kunden: 'kunden',
    heute_gearbeitet: 'heute_gearbeitet', // Today's hours (cached)
    offline_heute_minuten: 'offline_heute_minuten', // Offline tracked minutes (per day)
    buchungszeitfenster: 'buchungszeitfenster', // Booking time window config
    lastSync: 'last_sync',
}

// ==================== SYNC LOCK ====================
// Prevents multiple sync operations from running in parallel.
// Without this, online-event + visibilitychange + heartbeat could all fire
// at once and send duplicate entries.
let _syncLock = false

async function acquireSyncLock(): Promise<boolean> {
    if (_syncLock) {
        console.log('🔒 Sync bereits aktiv - überspringe')
        return false
    }
    _syncLock = true
    return true
}

function releaseSyncLock() {
    _syncLock = false
}

function normalizePendingEntry(entry: PendingEntry): PendingEntry {
    return {
        ...entry,
        status: entry.status ?? 'pending',
        attemptCount: entry.attemptCount ?? 0,
        lastAttemptAt: entry.lastAttemptAt,
        lastError: entry.lastError,
    }
}

// ==================== TEST HELPERS ====================
// These are exported ONLY for use in test files.
// Resets sync lock only. Call clearCache/clearPending separately to wipe stores.
export function _resetForTesting() {
    _syncLock = false
}

export async function _setPendingEntryStatusForTesting(id: string, status: 'pending' | 'inflight') {
    const db = await initDB()
    const entry = await db.get('pending', id)
    if (!entry) return
    await db.put('pending', {
        ...normalizePendingEntry(entry),
        status,
    })
}

// ==================== SERVER REACHABILITY ====================
// navigator.onLine is unreliable (can say "online" when behind captive portal).
// This does a real lightweight ping to verify the server is actually reachable.
async function isServerReachable(): Promise<boolean> {
    try {
        const controller = new AbortController()
        const timeoutId = setTimeout(() => controller.abort(), 2000)
        // Use a lightweight GET endpoint (kategorien is tiny & cached)
        const res = await fetch('/api/zeiterfassung/kategorien', {
            method: 'HEAD',
            signal: controller.signal,
        })
        clearTimeout(timeoutId)
        return res.ok || res.status === 304
    } catch {
        return false
    }
}

// Helper for safe UUID generation (crypto.randomUUID throws in insecure contexts)
function generateUUID() {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        try {
            return crypto.randomUUID();
        } catch {
            // Context might be insecure even if method exists
        }
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        const r = Math.random() * 16 | 0
        const v = c === 'x' ? r : (r & 0x3 | 0x8)
        return v.toString(16)
    });
}

// Generic fetch with IDB cache fallback
async function fetchWithCache<T>(url: string, cacheKey: string): Promise<T[]> {
    const db = await initDB()
    const isOnline = navigator.onLine

    if (isOnline) {
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 5000); // 5 seconds timeout

            const res = await fetch(url, { signal: controller.signal })
            clearTimeout(timeoutId);

            if (res.ok) {
                const data = await res.json()
                // Cache the fresh data in IDB
                await db.put('master_data', { key: cacheKey, value: data })
                return Array.isArray(data) ? data : []
            }
        } catch (error) {
            console.warn('Network request failed, falling back to cache', error)
        }
    }

    // Offline or error - return cached data from IDB
    const cached = await db.get('master_data', cacheKey)
    return (cached?.value as T[] | undefined) || []
}

export const OfflineService = {
    // ==================== SYNC ALL ====================
    // Acquires lock, checks reachability, syncs pending, then refreshes cache.
    async syncAll(): Promise<SyncResult> {
        // Acquire lock - only one sync at a time!
        if (!await acquireSyncLock()) {
            console.log('🔒 syncAll übersprungen - Lock aktiv')
            return { success: true, pendingSynced: 0, pendingFailed: 0, pendingDiscarded: 0, allPendingCleared: false, startSynced: false }
        }

        try {
            console.log('🔄 Synchronisiere alle Daten...')

            // Real server reachability check (don't trust navigator.onLine)
            const reachable = await isServerReachable()
            if (!reachable) {
                console.log('📴 Server nicht erreichbar - Sync übersprungen')
                return { success: false, pendingSynced: 0, pendingFailed: 0, pendingDiscarded: 0, allPendingCleared: false, startSynced: false }
            }

            // First, sync pending entries
            const syncResult = await OfflineService._syncPendingInternal()

            // Then refresh all cached data (only if server is reachable)
            const projekte = await OfflineService.getProjekte()
            
            const token = localStorage.getItem('zeiterfassung_token') || undefined
            
            await Promise.all([
                OfflineService.getKategorien(),
                OfflineService.getArbeitsgaenge(), // Global
                OfflineService.getArbeitsgaenge(token), // Personal (für Zeiterfassung!)
                OfflineService.getKunden(),
                OfflineService.getLieferanten(),
            ])

            // Pre-cache categories for EACH project
            if (Array.isArray(projekte) && projekte.length > 0) {
                console.log(`📦 Caching Kategorien für ${projekte.length} Projekte...`)
                const batchSize = 10
                for (let i = 0; i < projekte.length; i += batchSize) {
                    const batch = projekte.slice(i, i + batchSize) as Array<{ id: number }>
                    await Promise.all(batch.map((p) => OfflineService.getKategorien(p.id)))
                }
                console.log('✅ Alle Projekt-Kategorien gecached')
            }

            // Update last sync timestamp
            const db = await initDB()
            await db.put('master_data', { key: CACHE_KEYS.lastSync, value: new Date().toISOString() })

            // Re-read the queue after the full sync cycle. New offline entries can be
            // added while cache refresh is still running, so deriving this purely from
            // syncResult.failed would incorrectly report the queue as empty.
            const remainingPending = await OfflineService.getPendingCount()

            console.log('✅ Synchronisation abgeschlossen')
            return {
                success: true,
                pendingSynced: syncResult.synced,
                pendingFailed: syncResult.failed,
                pendingDiscarded: syncResult.discarded,
                allPendingCleared: remainingPending === 0,
                startSynced: syncResult.startSynced,
            }
        } catch (err) {
            console.error('❌ Synchronisation fehlgeschlagen:', err)
            return { success: false, pendingSynced: 0, pendingFailed: 0, pendingDiscarded: 0, allPendingCleared: false, startSynced: false }
        } finally {
            releaseSyncLock()
        }
    },

    // Get last sync time
    async getLastSyncTime(): Promise<Date | null> {
        const db = await initDB()
        const entry = await db.get('master_data', CACHE_KEYS.lastSync)
        return entry ? new Date(entry.value as string) : null
    },

    // Fetch with automatic caching - increased limit for time tracking
    async getProjekte() {
        return fetchWithCache('/api/zeiterfassung/projekte?limit=100', CACHE_KEYS.projekte)
    },

    async searchProjekte(query: string) {
        if (!query || query.length < 2) return this.getProjekte();
        // Search is usually online-only or returns non-cached results
        try {
            const res = await fetch(`/api/zeiterfassung/projekte?search=${encodeURIComponent(query)}`)
            if (res.ok) return await res.json();
        } catch (error) {
            console.warn('Search projects failed', error);
        }
        // Fallback to local filter if offline
        const cached = await this.getProjekte() as ProjektCacheEntry[]
        return cached.filter((p) =>
            p.name.toLowerCase().includes(query.toLowerCase()) ||
            p.projektNummer?.toLowerCase().includes(query.toLowerCase())
        );
    },

    async getKategorien(projektId?: number) {
        if (projektId) {
            return fetchWithCache(`/api/zeiterfassung/kategorien/${projektId}`, `kategorien_projekt_${projektId}`)
        }
        return fetchWithCache('/api/zeiterfassung/kategorien', CACHE_KEYS.kategorien)
    },

    async getArbeitsgaenge(token?: string) {
        if (token) {
            return fetchWithCache(`/api/zeiterfassung/arbeitsgaenge/${token}`, CACHE_KEYS.arbeitsgaenge_personal)
        }
        return fetchWithCache('/api/arbeitsgaenge', CACHE_KEYS.arbeitsgaenge)
    },

    async getLieferanten() {
        return fetchWithCache<{
            id: number;
            firmenname: string;
            strasse?: string;
            plz?: string;
            ort?: string;
            telefon?: string;
            mobiltelefon?: string;
            kundenEmails?: string[];
            lieferantenTyp?: string;
            vertreter?: string;
            eigeneKundennummer?: string;
        }>('/api/zeiterfassung/lieferanten?limit=15', 'lieferanten')
    },

    async searchLieferanten(query: string) {
        if (!query || query.length < 2) return this.getLieferanten();
        try {
            const res = await fetch(`/api/zeiterfassung/lieferanten?search=${encodeURIComponent(query)}`)
            if (res.ok) return await res.json();
        } catch (error) {
            console.warn('Search suppliers failed', error);
        }
        const cached = await this.getLieferanten() as LieferantCacheEntry[]
        return cached.filter((l) => l.firmenname.toLowerCase().includes(query.toLowerCase()));
    },

    async getKunden() {
        // Special case for kunden structure
        const db = await initDB()
        if (navigator.onLine) {
            try {
                const res = await fetch('/api/kunden?size=15')
                if (res.ok) {
                    const data = await res.json()
                    const kunden = data.kunden || data || []
                    await db.put('master_data', { key: CACHE_KEYS.kunden, value: kunden })
                    return kunden
                }
            } catch (error) {
                console.warn('Fetch customers failed', error)
            }
        }
        const cached = await db.get('master_data', CACHE_KEYS.kunden)
        return (cached?.value as KundenCacheEntry[] | undefined) || []
    },

    async searchKunden(query: string) {
        if (!query || query.length < 2) return this.getKunden();
        try {
            const res = await fetch(`/api/kunden?q=${encodeURIComponent(query)}&size=20`)
            if (res.ok) {
                const data = await res.json();
                return data.kunden || data || [];
            }
        } catch (error) {
            console.warn('Search customers failed', error);
        }
        const cached = await this.getKunden() as KundenCacheEntry[]
        return cached.filter((k) => k.name.toLowerCase().includes(query.toLowerCase()));
    },

    // Heute gearbeitet - cached for offline
    async getHeuteGearbeitet(token: string): Promise<HeuteGearbeitetResult> {
        const db = await initDB()
        const cacheKey = `${CACHE_KEYS.heute_gearbeitet}_${token}`

        // Always try fetch first (with aggressive timeout) - navigator.onLine is unreliable
        try {
            const controller = new AbortController()
            const timeoutId = setTimeout(() => controller.abort(), 2000) // Aggressive 2s timeout

            const res = await fetch(`/api/zeiterfassung/heute/${token}`, { signal: controller.signal })
            clearTimeout(timeoutId)

            if (res.ok) {
                const data = await res.json()
                // Cache the fresh data
                await db.put('master_data', { key: cacheKey, value: data })
                return { stunden: data.stunden || 0, minuten: data.minuten || 0, fromCache: false }
            }
        } catch {
            // Network error or timeout - that's fine, use cache
            console.log('📴 Heute gearbeitet: Server nicht erreichbar, nutze Cache')
        }

        // Offline or error - return cached
        const cached = await db.get('master_data', cacheKey)
        const cachedValue = cached?.value as { stunden?: number; minuten?: number } | undefined
        return {
            stunden: cachedValue?.stunden || 0,
            minuten: cachedValue?.minuten || 0,
            fromCache: true,
        }
    },

    // Store time entry locally (will sync later)
    // originalTime: Der tatsächliche Zeitpunkt der Aktion (wichtig für Offline-Sync!)
    async addPendingEntry(type: 'start' | 'stop' | 'pause', data: Record<string, unknown>, originalTime?: string) {
        const db = await initDB()
        const now = new Date().toISOString()
        const entry: PendingEntry = {
            id: generateUUID(),
            type,
            data,
            timestamp: now,
            originalTime: originalTime || now, // Fallback auf jetzt
            status: 'pending',
            attemptCount: 0,
        }
        await db.put('pending', entry)
        return entry
    },

    async addPendingEntryWithOperationId(
        type: 'start' | 'stop' | 'pause',
        data: Record<string, unknown>,
        originalTime: string | undefined,
        operationId: string,
    ) {
        const db = await initDB()
        const now = new Date().toISOString()
        const entry: PendingEntry = {
            id: operationId,
            type,
            data,
            timestamp: now,
            originalTime: originalTime || now,
            status: 'pending',
            attemptCount: 0,
        }
        await db.put('pending', entry)
        return entry
    },


    async getPendingEntries(): Promise<PendingEntry[]> {
        const db = await initDB()
        const entries = await db.getAll('pending')
        return entries.map(normalizePendingEntry)
    },

    async _requeueInflightEntries() {
        const db = await initDB()
        const entries = (await db.getAll('pending')).map(normalizePendingEntry)
        for (const entry of entries) {
            if (entry.status === 'inflight') {
                await db.put('pending', {
                    ...entry,
                    status: 'pending',
                    lastError: entry.lastError || 'Recovered inflight entry for retry',
                })
            }
        }
    },

    // Entfernt alle Pending-Entries eines bestimmten Typs (z.B. 'stop').
    // Wird genutzt um verwaiste Offline-Stops zu bereinigen, wenn ein Online-Start
    // beweist, dass der Server den Stop bereits verarbeitet hat.
    // ATOMIC: Snapshots IDs at read time, only deletes those exact IDs.
    // This prevents deleting entries that were added AFTER the read.
    async removePendingEntriesByType(type: 'start' | 'stop' | 'pause') {
        const db = await initDB()
        const all = await db.getAll('pending')
        // Snapshot: collect IDs to delete BEFORE any async work
        const idsToDelete = all
            .filter(entry => entry.type === type)
            .map(entry => entry.id)

        for (const id of idsToDelete) {
            // Verify entry still exists before deleting (idempotent)
            const existing = await db.get('pending', id)
            if (existing) {
                await db.delete('pending', id)
                console.log(`🗑️ Pending ${type}-Entry entfernt (${id.substring(0, 8)})`)
            }
        }
    },

    // ==================== SYNC PENDING (public wrapper) ====================
    // Public API - acquires lock and does reachability check.
    async syncPending() {
        if (!await acquireSyncLock()) {
            return { synced: 0, failed: 0, discarded: 0, skipped: true }
        }
        try {
            const reachable = await isServerReachable()
            if (!reachable) {
                return { synced: 0, failed: 0, discarded: 0, skipped: true }
            }
            return await OfflineService._syncPendingInternal()
        } finally {
            releaseSyncLock()
        }
    },

    // ==================== SYNC PENDING (internal - no lock) ====================
    // CRITICAL RULE: This method NEVER touches localStorage/zeiterfassung_active_session.
    // The local session belongs to the user. Only explicit user actions (stop, pause, switch) may clear it.
    // A sync rejection (4xx) means the SERVER rejected an old queued entry - that's not reason
    // to destroy the user's current active booking display.
    async _syncPendingInternal() {
        await OfflineService._requeueInflightEntries()
        const pending = await OfflineService.getPendingEntries()
        if (pending.length === 0) return { synced: 0, failed: 0, discarded: 0, startSynced: false }

        console.log(`🔄 Sync: ${pending.length} ausstehende Einträge`)

        let synced = 0
        let failed = 0
        let discarded = 0
        let startSynced = false
        const db = await initDB()


        // Sort by originalTime to replay actions in chronological order
        pending.sort((a, b) => new Date(a.originalTime).getTime() - new Date(b.originalTime).getTime())

        for (let i = 0; i < pending.length; i++) {
            const entry = pending[i]

            // Verify entry still exists in IDB (another sync might have processed it)
            const storedEntry = await db.get('pending', entry.id)
            const stillExists = storedEntry ? normalizePendingEntry(storedEntry) : null
            if (!stillExists) {
                console.log(`⏭️ Entry bereits verarbeitet: ${entry.type} (${entry.id.substring(0, 8)})`)
                continue
            }

            const inflightEntry: PendingEntry = {
                ...stillExists,
                status: 'inflight',
                attemptCount: stillExists.attemptCount + 1,
                lastAttemptAt: new Date().toISOString(),
                lastError: undefined,
            }
            await db.put('pending', inflightEntry)

            try {
                const res = await fetch(`/api/zeiterfassung/${entry.type}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(buildBookingRequestPayload(entry.data, entry.originalTime, entry.id)),
                })

                if (res.ok) {
                    await db.delete('pending', entry.id)
                    synced++
                    if (entry.type === 'start') startSynced = true
                    console.log(`✅ Sync OK: ${entry.type} (${entry.id.substring(0, 8)})`)
                } else if (res.status >= 400 && res.status < 500) {
                    // Client error (4xx) - this entry will NEVER succeed, discard it.
                    // BUT: We NEVER touch the local session here!
                    // The user might have started a completely different booking since this
                    // entry was queued. Deleting their session would be catastrophic.
                    const errorBody = await res.json().catch(() => ({}))
                    console.warn(`⚠️ Sync verworfen (${res.status}): ${entry.type} - ${errorBody.error || 'Unbekannt'}`)
                    await db.delete('pending', entry.id)
                    discarded++
                } else {
                    // Server error (5xx) - transient, keep for retry
                    console.warn(`🔁 Sync retry later (${res.status}): ${entry.type}`)
                    await db.put('pending', {
                        ...inflightEntry,
                        status: 'pending',
                        lastError: `HTTP ${res.status}`,
                    })
                    failed++
                }
            } catch {
                // Network error mid-sync - count ALL remaining entries as failed and stop
                await db.put('pending', {
                    ...inflightEntry,
                    status: 'pending',
                    lastError: 'Network error during sync',
                })
                const remaining = pending.length - i
                console.log(`📴 Netzwerkfehler während Sync - ${remaining} verbleibende Einträge werden übersprungen`)
                failed += remaining
                break
            }
        }

        console.log(`📊 Sync-Ergebnis: ${synced} OK, ${discarded} verworfen, ${failed} fehlgeschlagen`)
        return { synced, failed, discarded, startSynced }
    },

    // Check online status
    isOnline() {
        return navigator.onLine
    },

    // Get count of pending entries
    async getPendingCount() {
        const entries = await this.getPendingEntries()
        return entries.length
    },

    // Clear all cached master data (projects, categories, etc.)
    // NOTE: This intentionally does NOT clear pending entries!
    // Pending entries contain unsent bookings that must survive cache clears.
    async clearCache() {
        const db = await initDB()
        await db.clear('master_data')
    },

    // Clear all pending entries - USE WITH CAUTION!
    // Only call this when you are absolutely sure all entries have been synced.
    async clearPending() {
        const db = await initDB()
        await db.clear('pending')
    },

    // === OFFLINE HOURS TRACKING ===
    // Track minutes worked offline today (stored per day)
    async addOfflineWorkedMinutes(minutes: number) {
        if (minutes <= 0) return
        const db = await initDB()
        const today = new Date().toISOString().split('T')[0]
        const currentKey = `${CACHE_KEYS.offline_heute_minuten}_${today}`
        const existing = await db.get('master_data', currentKey)
        const total = ((existing?.value as number | undefined) || 0) + minutes
        await db.put('master_data', { key: currentKey, value: total })
        console.log(`📊 Offline-Minuten gespeichert: +${minutes} (Gesamt heute: ${total})`)
    },

    // Get total offline worked minutes for today
    async getOfflineHeuteMinuten(): Promise<number> {
        const db = await initDB()
        const today = new Date().toISOString().split('T')[0]
        const currentKey = `${CACHE_KEYS.offline_heute_minuten}_${today}`
        const existing = await db.get('master_data', currentKey)
        return (existing?.value as number | undefined) || 0
    },

    // Clear offline minutes after successful sync
    async clearOfflineHeuteMinuten() {
        const db = await initDB()
        const today = new Date().toISOString().split('T')[0]
        const currentKey = `${CACHE_KEYS.offline_heute_minuten}_${today}`
        await db.delete('master_data', currentKey)
        console.log('🧹 Offline-Minuten gelöscht nach Sync')
    },

    // === BUCHUNGSZEITFENSTER ===
    // Fetches and caches the allowed booking time window for the employee
    async getBuchungszeitfenster(token: string): Promise<{ buchungStartZeit: string | null; buchungEndeZeit: string | null }> {
        const db = await initDB()
        const cacheKey = `${CACHE_KEYS.buchungszeitfenster}_${token}`

        try {
            const controller = new AbortController()
            const timeoutId = setTimeout(() => controller.abort(), 2000)
            const res = await fetch(`/api/zeiterfassung/buchungszeitfenster/${encodeURIComponent(token)}`, {
                signal: controller.signal,
            })
            clearTimeout(timeoutId)
            if (res.ok) {
                const data = await res.json()
                await db.put('master_data', { key: cacheKey, value: data })
                return data
            }
        } catch {
            // Offline / timeout - use cache
        }
        const cached = await db.get('master_data', cacheKey)
        return (cached?.value as { buchungStartZeit: string | null; buchungEndeZeit: string | null } | undefined)
            || { buchungStartZeit: null, buchungEndeZeit: null }
    },
}
