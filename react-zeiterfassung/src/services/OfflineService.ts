// Offline-First Data Service
// Caches data locally using IndexedDB and syncs when online
// PRINCIPLE: Client ALWAYS wins when pending entries exist.
// The local session is NEVER deleted by sync logic.
import { leseFehlermeldung } from '../lib/apiFehler'
import {
    arbeiteWarteschlangeAb,
    HINTERGRUND_SYNC_TAG,
    istVoruebergehenderFehler,
    buildBookingRequestPayload,
    mitWarteschlangenSperre,
    leseWartendeEintraege,
    normalizePendingEntry,
    oeffneBuchungsDB,
    schreibeFallsNochWartend,
    type BuchungsTyp,
    type FailedEntry,
    type PendingEntry,
} from './buchungsWarteschlange'

// Für bestehende Importe aus der App weiterhin hier erreichbar.
export { ABGLEICH_ANFRAGE_ZEITLIMIT_MS, buildBookingRequestPayload, istVoruebergehenderFehler } from './buchungsWarteschlange'
export type { BuchungsTyp, FailedEntry } from './buchungsWarteschlange'

interface ProjektCacheEntry {
    name: string
    projektNummer?: string
}

interface LieferantCacheEntry {
    firmenname: string
    /** Zweiter Name, unter dem die Firma im Betrieb bekannt ist */
    aliasName?: string
}

interface KundenCacheEntry {
    name: string
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
    /**
     * ISO-Zeitstempel der laufenden Buchung (oder null falls keine läuft).
     * Server zählt diese NICHT in stunden/minuten mit; das Frontend rechnet
     * sie selbst dazu. Damit gibt es keine Doppelzählung wenn der Wert
     * offline gecached wird und die Session weiterläuft.
     */
    aktiveBuchungStartZeit?: string | null
}

export function createOperationId() {
    return generateUUID()
}

const CACHE_KEYS = {
    projekte: 'projekte',
    kategorien: 'kategorien',
    arbeitsgaenge: 'arbeitsgaenge', // Global list
    arbeitsgaenge_personal: 'arbeitsgaenge_personal', // User specific list
    kunden: 'kunden',
    heute_gearbeitet: 'heute_gearbeitet', // Today's hours (cached)
    buchungszeitfenster: 'buchungszeitfenster', // Booking time window config
    tagesbuchungen: 'tagesbuchungen', // Daily bookings per employee/date
    lastSync: 'last_sync',
}

// ==================== EIN ABGLEICH ZUR ZEIT ====================
// online-Event, visibilitychange, Heartbeat und Anstöße feuern oft gleichzeitig.
// Früher bekam der zweite Aufrufer sofort ein erfundenes {success:true} – das
// Dashboard lud dann zu früh neu und nach dem echten Ende gar nicht mehr.
// Jetzt teilen sich alle Aufrufer den laufenden Abgleich und bekommen dasselbe
// Ergebnis. Es läuft nie mehr als ein Abgleich gleichzeitig.
interface LaufenderAbgleich {
    art: 'alles' | 'warteschlange'
    ergebnis: Promise<SyncResult>
}
let laufenderAbgleich: LaufenderAbgleich | null = null

/**
 * Buchungen, die sendeBuchung in diesem Moment selbst direkt sendet. Der Abgleich lässt
 * sie in Ruhe, bis die Antwort da ist – sonst gingen sie ein zweites Mal raus.
 */
const direktUnterwegs = new Set<string>()

function starteAbgleich(art: LaufenderAbgleich['art'], arbeit: () => Promise<SyncResult>): Promise<SyncResult> {
    const ergebnis: Promise<SyncResult> = arbeit().finally(() => {
        if (laufenderAbgleich?.ergebnis === ergebnis) laufenderAbgleich = null
    })
    laufenderAbgleich = { art, ergebnis }
    return ergebnis
}

const KEIN_ABGLEICH: SyncResult = {
    success: false, pendingSynced: 0, pendingFailed: 0, pendingDiscarded: 0, allPendingCleared: false, startSynced: false,
}

/**
 * Abstand zwischen zwei Kategorie-Abfragen beim Vorladen. Das Handy-Gateway
 * erlaubt pro Internetadresse nur 10 Anfragen pro Sekunde; mit 150 ms bleiben
 * wir klar darunter und lassen Platz für echte Buchungen (Feierabend-Tipp).
 */
export const KATEGORIEN_ABSTAND_MS = 150

const warte = (ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms))

/** Mindestabstand zwischen zwei Vorlade-Läufen – spart Datenvolumen und Gateway-Kontingent. */
const VORLADEN_MINDESTABSTAND_MS = 10 * 60 * 1000

let laufendesVorladen: Promise<void> | null = null
let letztesVorladenAm = 0

/**
 * Lädt die Kategorien je Projekt nacheinander mit Abstand, damit das Gateway
 * (10 Anfragen/s je Internetadresse) nicht überläuft. Läuft schon ein Vorladen
 * oder liegt das letzte keine 10 Minuten zurück, passiert nichts.
 */
function kategorienVorladen(projekte: Array<{ id: number }>) {
    if (laufendesVorladen || Date.now() - letztesVorladenAm < VORLADEN_MINDESTABSTAND_MS) return
    letztesVorladenAm = Date.now()
    laufendesVorladen = (async () => {
        console.log(`📦 Caching Kategorien für ${projekte.length} Projekte...`)
        for (let i = 0; i < projekte.length; i++) {
            if (i > 0) await warte(KATEGORIEN_ABSTAND_MS)
            await OfflineService.getKategorien(projekte[i].id)
        }
        console.log('✅ Alle Projekt-Kategorien gecached')
    })()
        .catch(err => console.warn('Kategorien konnten nicht vorgeladen werden', err))
        .finally(() => { laufendesVorladen = null })
}

// ==================== TEST HELPERS ====================
// These are exported ONLY for use in test files.
// Vergisst den laufenden Abgleich. Stores separat mit clearCache/clearPending leeren.
export function _resetForTesting() {
    laufenderAbgleich = null
    direktUnterwegs.clear()
    laufendesVorladen = null
    letztesVorladenAm = 0
}

/** Wartet in Tests, bis das Vorladen der Projekt-Kategorien im Hintergrund fertig ist. */
export async function _warteAufVorladenForTesting() {
    await laufendesVorladen
}

export async function _setPendingEntryStatusForTesting(id: string, status: 'pending' | 'inflight') {
    const db = await oeffneBuchungsDB()
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
    const db = await oeffneBuchungsDB()
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
    // Prüft die Erreichbarkeit, sendet die Warteschlange und frischt die Stammdaten auf.
    // Läuft schon ein Abgleich, bekommt der Aufrufer dessen Ergebnis (kein zweiter Lauf).
    async syncAll(): Promise<SyncResult> {
        if (laufenderAbgleich?.art === 'alles') return laufenderAbgleich.ergebnis
        if (laufenderAbgleich) {
            // Ein reiner Warteschlangen-Lauf ist aktiv: abwarten, dann selbst abgleichen.
            await laufenderAbgleich.ergebnis.catch(() => undefined)
            return OfflineService.syncAll()
        }
        return starteAbgleich('alles', () => OfflineService._syncAllInternal())
    },

    async _syncAllInternal(): Promise<SyncResult> {
        try {
            console.log('🔄 Synchronisiere alle Daten...')

            // Real server reachability check (don't trust navigator.onLine)
            const reachable = await isServerReachable()
            if (!reachable) {
                console.log('📴 Server nicht erreichbar - Sync übersprungen')
                return { ...KEIN_ABGLEICH }
            }

            // First, sync pending entries
            const syncResult = await OfflineService._syncPendingInternal()
            if (syncResult.wartetAufDirektSendung) {
                // Kein Fehler: Die App sendet gerade selbst und stößt danach neu an.
                // Stammdaten jetzt nicht nachschieben – das Gateway bremst gerade womöglich.
                return {
                    ...KEIN_ABGLEICH,
                    success: true,
                    pendingSynced: syncResult.synced,
                    pendingDiscarded: syncResult.discarded,
                    startSynced: syncResult.startSynced,
                }
            }
            if (syncResult.abgebrochen) {
                // Server ausgelastet oder Verbindung weg: keine Stammdaten nachschieben,
                // sonst bekommt auch der nächste Feierabend-Tipp nur „zu viele Anfragen“.
                return {
                    ...KEIN_ABGLEICH,
                    pendingSynced: syncResult.synced,
                    pendingFailed: syncResult.failed,
                    pendingDiscarded: syncResult.discarded,
                    startSynced: syncResult.startSynced,
                }
            }

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

            // Kategorien je Projekt laufen danach im Hintergrund nach – bei 100 Projekten
            // dauert das gedrosselt ~15 s. Darauf soll weder die Anzeige noch der nächste
            // Abgleich warten.
            if (Array.isArray(projekte) && projekte.length > 0) {
                kategorienVorladen(projekte as Array<{ id: number }>)
            }

            // Nachlauf: Was während des Stammdaten-Abrufs eingereiht wurde (z. B. ein
            // Feierabend, während noch ältere Einträge warteten), gleich mitschicken.
            let nachlauf = { synced: 0, failed: 0, discarded: 0, startSynced: false, abgebrochen: false }
            if (await OfflineService.getPendingCount() > 0) {
                nachlauf = await OfflineService._syncPendingInternal()
            }

            // Update last sync timestamp
            const db = await oeffneBuchungsDB()
            await db.put('master_data', { key: CACHE_KEYS.lastSync, value: new Date().toISOString() })

            // Re-read the queue after the full sync cycle. New offline entries can be
            // added while cache refresh is still running, so deriving this purely from
            // syncResult.failed would incorrectly report the queue as empty.
            const remainingPending = await OfflineService.getPendingCount()

            console.log('✅ Synchronisation abgeschlossen')
            return {
                success: !nachlauf.abgebrochen,
                pendingSynced: syncResult.synced + nachlauf.synced,
                pendingFailed: nachlauf.failed,
                pendingDiscarded: syncResult.discarded + nachlauf.discarded,
                allPendingCleared: remainingPending === 0,
                startSynced: syncResult.startSynced || nachlauf.startSynced,
            }
        } catch (err) {
            console.error('❌ Synchronisation fehlgeschlagen:', err)
            return { ...KEIN_ABGLEICH }
        }
    },

    // Get last sync time
    async getLastSyncTime(): Promise<Date | null> {
        const db = await oeffneBuchungsDB()
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
            aliasName?: string;
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
        // Offline-Fallback: Name UND zweiter Name (Alias) durchsuchen, damit die
        // Suche ohne Netz die gleichen Treffer liefert wie der Server.
        const cached = await this.getLieferanten() as LieferantCacheEntry[]
        const needle = query.toLowerCase()
        return cached.filter((l) =>
            (l.firmenname || '').toLowerCase().includes(needle) ||
            (l.aliasName || '').toLowerCase().includes(needle)
        );
    },

    async getKunden() {
        // Special case for kunden structure
        const db = await oeffneBuchungsDB()
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
        const db = await oeffneBuchungsDB()
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
                return {
                    stunden: data.stunden || 0,
                    minuten: data.minuten || 0,
                    fromCache: false,
                    aktiveBuchungStartZeit: data.aktiveBuchungStartZeit || null,
                }
            }
        } catch {
            // Network error or timeout - that's fine, use cache
            console.log('📴 Heute gearbeitet: Server nicht erreichbar, nutze Cache')
        }

        // Offline or error - return cached
        const cached = await db.get('master_data', cacheKey)
        const cachedValue = cached?.value as { stunden?: number; minuten?: number; aktiveBuchungStartZeit?: string | null } | undefined
        return {
            stunden: cachedValue?.stunden || 0,
            minuten: cachedValue?.minuten || 0,
            fromCache: true,
            aktiveBuchungStartZeit: cachedValue?.aktiveBuchungStartZeit || null,
        }
    },

    async getTagesbuchungen(token: string, datum: string): Promise<{ buchungen: Record<string, unknown>[]; fromCache: boolean }> {
        const db = await oeffneBuchungsDB()
        const cacheKey = `${CACHE_KEYS.tagesbuchungen}_${token}_${datum}`

        try {
            const controller = new AbortController()
            const timeoutId = setTimeout(() => controller.abort(), 2000)

            const res = await fetch(`/api/zeiterfassung/buchungen/${encodeURIComponent(token)}?datum=${encodeURIComponent(datum)}`, {
                signal: controller.signal,
            })
            clearTimeout(timeoutId)

            if (res.ok) {
                const data = await res.json()
                const buchungen = Array.isArray(data) ? data : []
                await db.put('master_data', { key: cacheKey, value: buchungen })
                return { buchungen, fromCache: false }
            }
        } catch {
            console.log('📴 Tagesbuchungen: Server nicht erreichbar, nutze Cache')
        }

        const cached = await db.get('master_data', cacheKey)
        return {
            buchungen: Array.isArray(cached?.value) ? cached.value as Record<string, unknown>[] : [],
            fromCache: true,
        }
    },

    // Store time entry locally (will sync later)
    // originalTime: Der tatsächliche Zeitpunkt der Aktion (wichtig für Offline-Sync!)
    // durationMinutes: Bei stop/pause: Dauer der beendeten Buchung – fließt
    //                  direkt in die "heute gearbeitet"-Anzeige ein, auch wenn
    //                  der Server diesen Eintrag später ablehnt.
    async addPendingEntry(
        type: 'start' | 'stop' | 'pause',
        data: Record<string, unknown>,
        originalTime?: string,
        durationMinutes?: number,
    ) {
        const db = await oeffneBuchungsDB()
        const now = new Date().toISOString()
        const entry: PendingEntry = {
            id: generateUUID(),
            type,
            data,
            timestamp: now,
            originalTime: originalTime || now, // Fallback auf jetzt
            status: 'pending',
            attemptCount: 0,
            durationMinutes,
        }
        await db.put('pending', entry)
        return entry
    },

    async addPendingEntryWithOperationId(
        type: 'start' | 'stop' | 'pause',
        data: Record<string, unknown>,
        originalTime: string | undefined,
        operationId: string,
        durationMinutes?: number,
    ) {
        const db = await oeffneBuchungsDB()
        const now = new Date().toISOString()
        const entry: PendingEntry = {
            id: operationId,
            type,
            data,
            timestamp: now,
            originalTime: originalTime || now,
            status: 'pending',
            attemptCount: 0,
            durationMinutes,
        }
        await db.put('pending', entry)
        return entry
    },

    async getPendingEntries(): Promise<PendingEntry[]> {
        return leseWartendeEintraege()
    },

    // ==================== SYNC PENDING (public wrapper) ====================
    // Sendet nur die Warteschlange. Läuft schon ein Abgleich, wird dessen Ergebnis abgewartet.
    async syncPending() {
        const ergebnis = laufenderAbgleich
            ? await laufenderAbgleich.ergebnis
            : await starteAbgleich('warteschlange', async () => {
                if (!await isServerReachable()) return { ...KEIN_ABGLEICH }
                const r = await OfflineService._syncPendingInternal()
                return {
                    success: !r.abgebrochen || r.wartetAufDirektSendung === true,
                    pendingSynced: r.synced,
                    pendingFailed: r.failed,
                    pendingDiscarded: r.discarded,
                    allPendingCleared: await OfflineService.getPendingCount() === 0,
                    startSynced: r.startSynced,
                }
            })
        return {
            synced: ergebnis.pendingSynced,
            failed: ergebnis.pendingFailed,
            discarded: ergebnis.pendingDiscarded,
            skipped: !ergebnis.success,
            startSynced: ergebnis.startSynced,
        }
    },

    // ==================== SYNC PENDING (internal) ====================
    // Die Sende-Schleife selbst steht in buchungsWarteschlange.ts, damit der Service Worker
    // (Android, App geschlossen) dieselbe nutzt. Die Sperre verhindert, dass App und
    // Service Worker gleichzeitig senden.
    async _syncPendingInternal() {
        return mitWarteschlangenSperre(() => arbeiteWarteschlangeAb({ direktUnterwegs }))
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

    // ==================== REPARATUR-LISTE (Failed Entries) ====================
    // "Mobile gewinnt": 4xx vom Server löscht keine Buchungen mehr still,
    // sondern verschiebt sie hierher. Der Handwerker sieht sie in der App
    // und kann sie verwerfen oder zur Korrektur freigeben.

    async getFailedEntries(): Promise<FailedEntry[]> {
        const db = await oeffneBuchungsDB()
        return db.getAll('failed')
    },

    async getFailedCount(): Promise<number> {
        const db = await oeffneBuchungsDB()
        return db.count('failed')
    },

    async removeFailedEntry(id: string) {
        const db = await oeffneBuchungsDB()
        await db.delete('failed', id)
    },

    async clearFailed() {
        const db = await oeffneBuchungsDB()
        await db.clear('failed')
    },

    /**
     * Summiert alle Minuten aus unsynchronisierten Stop/Pause-Events
     * (pending + failed). Damit zeigt "heute gearbeitet" auch dann die
     * tatsächlich gearbeitete Zeit, wenn der Server eine Buchung noch
     * nicht hat oder abgelehnt hat. Pause-Events tragen hier ihre Arbeits-
     * Vor-Dauer bei (= Zeit, die durch den Pause-Start beendet wurde).
     *
     * Tagesgrenzen werden LOKAL bestimmt (Mitternacht in der Geräte-Zeitzone),
     * nicht UTC. Sonst wäre ein Stop um 00:30 Ortszeit (= 22:30/23:30 UTC am
     * Vortag, je nach DST) plötzlich kein "heute" mehr – kritisch für
     * Schichtdienst / Notdienst im Handwerk.
     */
    async getUnsyncedStopMinutes(): Promise<number> {
        const now = new Date()
        const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
        const endOfDay = startOfDay + 24 * 60 * 60 * 1000

        const inLocalToday = (iso: string): boolean => {
            const t = new Date(iso).getTime()
            return Number.isFinite(t) && t >= startOfDay && t < endOfDay
        }

        const pending = await OfflineService.getPendingEntries()
        const failed = await OfflineService.getFailedEntries()

        const sum = (entries: { type: string; originalTime: string; durationMinutes?: number }[]) =>
            entries
                .filter(e => (e.type === 'stop' || e.type === 'pause') && inLocalToday(e.originalTime))
                .reduce((acc, e) => acc + (e.durationMinutes ?? 0), 0)

        return sum(pending) + sum(failed)
    },

    // Clear all cached master data (projects, categories, etc.)
    // NOTE: This intentionally does NOT clear pending entries!
    // Pending entries contain unsent bookings that must survive cache clears.
    async clearCache() {
        const db = await oeffneBuchungsDB()
        await db.clear('master_data')
    },

    // Clear all pending entries - USE WITH CAUTION!
    // Only call this when you are absolutely sure all entries have been synced.
    async clearPending() {
        const db = await oeffneBuchungsDB()
        await db.clear('pending')
    },

    // === BUCHUNGSZEITFENSTER ===
    // Fetches and caches the allowed booking time window for the employee
    async getBuchungszeitfenster(token: string): Promise<{ buchungStartZeit: string | null; buchungEndeZeit: string | null }> {
        const db = await oeffneBuchungsDB()
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

// ==================== BUCHUNG SENDEN (Start, Pause, Feierabend) ====================

/** Event, mit dem jede Stelle der App einen Abgleich anstoßen kann (App.tsx hört darauf). */
export const ABGLEICH_ANSTOSSEN_EVENT = 'zeiterfassung:abgleich-anstossen'

/** Bittet die App, die Warteschlange so bald wie möglich zu übertragen. */
export function abgleichAnstossen() {
    window.dispatchEvent(new CustomEvent(ABGLEICH_ANSTOSSEN_EVENT))
    // Für den Fall, dass die App gleich geschlossen wird: Android sendet dann im Hintergrund.
    void hintergrundUebertragungAnmelden()
}

type RegistrierungMitSync = ServiceWorkerRegistration & {
    sync?: { register(tag: string): Promise<void> }
}

/**
 * Bittet den Browser, die Warteschlange zu senden, sobald wieder Netz da ist – auch
 * wenn die App dann schon geschlossen ist (Background Sync, z. B. Chrome auf Android).
 * iPhones können das nicht; dort sendet die App beim nächsten Öffnen. Best effort.
 */
export async function hintergrundUebertragungAnmelden(): Promise<boolean> {
    try {
        if (typeof navigator === 'undefined' || !navigator.serviceWorker?.getRegistration) return false
        const registrierung = await navigator.serviceWorker.getRegistration() as RegistrierungMitSync | undefined
        if (!registrierung?.sync) return false
        await registrierung.sync.register(HINTERGRUND_SYNC_TAG)
        return true
    } catch {
        return false
    }
}

/**
 * Bittet den Browser, die gespeicherten Daten (wartende Buchungen!) bei
 * Speichermangel nicht zu löschen. Android räumt sonst die IndexedDB ab.
 * Best effort: Ablehnung oder fehlende Unterstützung sind kein Fehler.
 */
export async function speicherDauerhaftAnfordern(): Promise<boolean> {
    try {
        const speicher = typeof navigator === 'undefined' ? undefined : navigator.storage
        if (!speicher?.persist) return false
        if (await speicher.persisted?.()) return true
        return await speicher.persist()
    } catch {
        return false
    }
}

export interface SendeBuchungOptionen {
    /** Tatsächlicher Zeitpunkt der Aktion (Standard: jetzt). */
    originalTime?: string
    /** Idempotenz-Schlüssel; bleibt beim Einreihen gleich (Standard: neu erzeugt). */
    operationId?: string
    /** Dauer der durch Stop/Pause beendeten Arbeit, für „heute gearbeitet“. */
    durationMinutes?: number
    /** So lange wartet die Oberfläche auf die Antwort (Standard 5000 ms). Die Anfrage läuft danach weiter. */
    timeoutMs?: number
    /**
     * true: Eine echte Ablehnung (4xx außer 408/425/429) landet sofort in der
     * Reparatur-Liste („Mobile gewinnt“). false: Es wird nichts gespeichert –
     * der Aufrufer entscheidet selbst (z. B. Wechsel ohne laufende Buchung).
     */
    ablehnungInReparaturListe: boolean
}

export type SendeBuchungErgebnis =
    | { art: 'ok'; daten: Record<string, unknown> }
    | { art: 'eingereiht' }
    | { art: 'abgelehnt'; status: number; meldung: string }

const STANDARD_WARTEZEIT_MS = 5000

/**
 * Schickt eine Buchung an den Server – aber nie an der Warteschlange vorbei.
 *
 * - Warten noch ältere Einträge, wird die Buchung hinten angestellt (Reihenfolge!).
 * - Sonst wird sie mit `keepalive` gesendet und nicht abgebrochen: Nach `timeoutMs`
 *   hört nur die Oberfläche auf zu warten, die Anfrage läuft weiter (auch wenn die
 *   App gleich geschlossen wird). Kommt sie doppelt an, fängt das der Server über
 *   den gleichen Idempotenz-Schlüssel ab.
 * - Zeitlimit, Netzwerkfehler, 408/425/429 und 5xx: einreihen und Abgleich anstoßen.
 */
export async function sendeBuchung(
    type: BuchungsTyp,
    data: Record<string, unknown>,
    optionen: SendeBuchungOptionen,
): Promise<SendeBuchungErgebnis> {
    const operationId = optionen.operationId ?? createOperationId()
    const originalTime = optionen.originalTime ?? new Date().toISOString()

    const einreihen = async (): Promise<SendeBuchungErgebnis> => {
        await OfflineService.addPendingEntryWithOperationId(type, data, originalTime, operationId, optionen.durationMinutes)
        abgleichAnstossen()
        return { art: 'eingereiht' }
    }

    let wartendeEintraege = 0
    try {
        wartendeEintraege = await OfflineService.getPendingCount()
    } catch {
        // Speicher nicht lesbar: dann wenigstens direkt senden
    }
    if (wartendeEintraege > 0) return einreihen()

    // Erst speichern, dann senden: Friert Android die App direkt nach dem Tipp ein
    // (Handy gesperrt) und scheitert auch die keepalive-Anfrage, steht die Buchung
    // trotzdem auf dem Handy. Beim nächsten Abgleich wird der Eintrag 'inflight'
    // wieder freigegeben und erneut gesendet – doppelt ankommen ist dank gleichem
    // Idempotenz-Schlüssel harmlos.
    const db = await oeffneBuchungsDB()
    const jetztGespeichert = new Date().toISOString()
    // Vor dem Speichern vormerken: Sonst könnte ein Abgleich den frisch gespeicherten
    // Eintrag in der Lücke bis zur Vormerkung schon parallel losschicken.
    direktUnterwegs.add(operationId)
    try {
        await db.put('pending', {
            id: operationId,
            type,
            data,
            timestamp: jetztGespeichert,
            originalTime,
            status: 'inflight',
            attemptCount: 1,
            lastAttemptAt: jetztGespeichert,
            durationMinutes: optionen.durationMinutes,
        })
    } catch (fehler) {
        direktUnterwegs.delete(operationId)
        throw fehler
    }
    // Gleich jetzt beim Browser anmelden, nicht erst nach dem Zeitlimit: Sperrt der
    // Mitarbeiter das Handy, bevor die Antwort da ist, läuft auf der eingefrorenen Seite
    // kein Timer mehr. Der Service Worker wartet frische Einträge ab (keine Doppel-Sendung).
    await hintergrundUebertragungAnmelden()
    const direktFertig = () => {
        direktUnterwegs.delete(operationId)
        // Hat ein Abgleich während der Sendung gewartet, jetzt weitermachen.
        void OfflineService.getPendingCount().then(n => { if (n > 0) abgleichAnstossen() }).catch(() => undefined)
    }
    const eigenenEintragEntfernen = async () => {
        try {
            await db.delete('pending', operationId)
        } finally {
            direktFertig()
        }
    }
    const spaeterNochmal = async (): Promise<SendeBuchungErgebnis> => {
        // Ein paralleler Abgleich kann den Eintrag schon übertragen und gelöscht haben –
        // dann darf er nicht zurückkommen (ein Schritt, siehe schreibeFallsNochWartend).
        try {
            await schreibeFallsNochWartend(operationId, gespeichert => ({ ...gespeichert, status: 'pending' }))
        } finally {
            direktUnterwegs.delete(operationId)
        }
        abgleichAnstossen()
        return { art: 'eingereiht' }
    }

    let zeitlimitTimer: ReturnType<typeof setTimeout> | undefined
    let antwort: Response | 'zeitlimit'
    try {
        const anfrage = fetch(`/api/zeiterfassung/${type}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(buildBookingRequestPayload(data, originalTime, operationId)),
            keepalive: true,
        })
        // Scheitert die Anfrage erst nach dem Zeitlimit, darf das kein unbehandelter Fehler sein.
        anfrage.catch(() => undefined)
        const zeitlimit = new Promise<'zeitlimit'>(resolve => {
            zeitlimitTimer = setTimeout(() => resolve('zeitlimit'), optionen.timeoutMs ?? STANDARD_WARTEZEIT_MS)
        })
        antwort = await Promise.race([anfrage, zeitlimit])
    } catch {
        return spaeterNochmal()
    } finally {
        clearTimeout(zeitlimitTimer)
    }

    if (antwort === 'zeitlimit') return spaeterNochmal()

    if (antwort.ok) {
        await eigenenEintragEntfernen()
        const daten: unknown = await antwort.json().catch(() => ({}))
        return { art: 'ok', daten: daten && typeof daten === 'object' ? daten as Record<string, unknown> : {} }
    }

    if (istVoruebergehenderFehler(antwort.status)) return spaeterNochmal()

    await eigenenEintragEntfernen()
    const meldung = await leseFehlermeldung(antwort, 'Der Server hat die Buchung nicht angenommen.')
    if (optionen.ablehnungInReparaturListe) {
        const jetzt = new Date().toISOString()
        await db.put('failed', {
            id: operationId,
            type,
            data,
            timestamp: jetztGespeichert,
            originalTime,
            failedAt: jetzt,
            httpStatus: antwort.status,
            serverError: meldung,
            attemptCount: 1,
            durationMinutes: optionen.durationMinutes,
        })
    }
    return { art: 'abgelehnt', status: antwort.status, meldung }
}

/** Startzeit für die lokale Anzeige: die vom Server, sonst der eigene Zeitpunkt der Aktion. */
export function startZeitAusErgebnis(ergebnis: SendeBuchungErgebnis, ersatz: string): string {
    if (ergebnis.art === 'ok' && typeof ergebnis.daten.startZeit === 'string' && ergebnis.daten.startZeit) {
        return ergebnis.daten.startZeit
    }
    return ersatz
}
