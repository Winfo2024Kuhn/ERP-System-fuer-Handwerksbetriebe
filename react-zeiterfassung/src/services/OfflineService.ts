// Offline-First Data Service
// Caches data locally using IndexedDB and syncs when online
// PRINCIPLE: Client ALWAYS wins when pending entries exist.
// The local session is NEVER deleted by sync logic.
import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import { leseFehlermeldung } from '../lib/apiFehler'

interface ZeiterfassungDB extends DBSchema {
    pending: {
        key: string
        value: PendingEntry
    }
    /**
     * "Reparatur-Liste": Buchungen, die der Server beim Sync mit 4xx
     * abgelehnt hat (z.B. "keine aktive Buchung gefunden" für ein Stop).
     * Stille Löschung wäre Datenverlust für den Handwerker - stattdessen
     * landen sie hier mit Fehlerkontext, bis der User sie manuell verwirft
     * oder ein Admin sie als Korrekturbuchung übernimmt.
     */
    failed: {
        key: string
        value: FailedEntry
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
    /** Zweiter Name, unter dem die Firma im Betrieb bekannt ist */
    aliasName?: string
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
    /**
     * Dauer der durch dieses Event beendeten Buchung in Minuten.
     * Wird bei 'stop' und 'pause' gesetzt (= Arbeitszeit, die der Mitarbeiter
     * gerade beendet hat). Bei 'start' bleibt das Feld leer.
     *
     * Quelle für die "heute gearbeitet"-Anzeige, solange der Server die Buchung
     * noch nicht kennt. Damit stimmt die Tagesanzeige auch dann, wenn ein
     * Stop offline passiert oder vom Server abgelehnt wird (Reparatur-Liste).
     */
    durationMinutes?: number
    /**
     * Nur echte Serverfehler (5xx) zählen hierfür – nicht 429 („zu viele Anfragen“)
     * und nicht Funklöcher. Grundlage für {@link haengtDauerhaft}.
     */
    serverfehlerVersuche?: number
    ersterServerfehlerAm?: string
}

/**
 * Buchung, deren Sync vom Server final abgelehnt wurde (4xx).
 * "Mobile gewinnt": Statt diese Daten zu löschen, behalten wir sie
 * mit vollständigem Fehlerkontext in der Reparatur-Liste, damit der
 * Handwerker (oder Admin) entscheiden kann was passieren soll.
 */
export interface FailedEntry {
    id: string
    type: 'start' | 'stop' | 'pause'
    data: Record<string, unknown>
    timestamp: string       // Wann ursprünglich offline eingereiht
    originalTime: string    // Tatsächlicher Zeitpunkt der Aktion
    failedAt: string        // Wann der Server final abgelehnt hat
    httpStatus: number      // 4xx Status-Code
    serverError: string     // Fehlertext vom Server
    attemptCount: number    // Wie oft probiert wurde
    /** Dauer der durch dieses Event beendeten Buchung in Minuten (stop/pause). */
    durationMinutes?: number
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
// Version 2: 'failed' Store für Reparatur-Liste hinzugefügt (Mobile-First).
const DB_VERSION = 2

let dbPromise: Promise<IDBPDatabase<ZeiterfassungDB>>

const initDB = () => {
    if (!dbPromise) {
        dbPromise = openDB<ZeiterfassungDB>(DB_NAME, DB_VERSION, {
            upgrade(db) {
                if (!db.objectStoreNames.contains('pending')) {
                    db.createObjectStore('pending', { keyPath: 'id' })
                }
                if (!db.objectStoreNames.contains('failed')) {
                    db.createObjectStore('failed', { keyPath: 'id' })
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

/** Höchstens so lange wartet der Abgleich auf eine einzelne Buchungs-Anfrage. */
export const ABGLEICH_ANFRAGE_ZEITLIMIT_MS = 20_000

/** Ab hier hält ein immer wieder scheiternder Eintrag die Warteschlange nicht mehr auf. */
const DAUERHAFT_HAENGEND_VERSUCHE = 20
const DAUERHAFT_HAENGEND_MS = 24 * 60 * 60 * 1000

/**
 * Antwortet der Server auf genau diesen Eintrag seit über einem Tag mit Serverfehlern?
 * 408/425/429 und Netzfehler zählen bewusst nicht: Ein Feierabend vom Freitag darf
 * nicht in der Reparatur-Liste landen, nur weil am Montag früh alle gleichzeitig abgleichen.
 */
function haengtDauerhaft(entry: PendingEntry): boolean {
    if (!entry.ersterServerfehlerAm) return false
    const dauer = Date.now() - new Date(entry.ersterServerfehlerAm).getTime()
    return (entry.serverfehlerVersuche ?? 0) >= DAUERHAFT_HAENGEND_VERSUCHE && dauer >= DAUERHAFT_HAENGEND_MS
}

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

/**
 * Antworten, bei denen ein späterer Versuch klappen kann: Zeitüberschreitung,
 * „zu früh“, zu viele Anfragen (Gateway oder gesperrte Internetadresse) und
 * Serverfehler. Alles andere aus dem 4xx-Bereich ist eine echte Ablehnung.
 */
export function istVoruebergehenderFehler(status: number): boolean {
    return status === 408 || status === 425 || status === 429 || status >= 500
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
// Vergisst den laufenden Abgleich. Stores separat mit clearCache/clearPending leeren.
export function _resetForTesting() {
    laufenderAbgleich = null
    laufendesVorladen = null
    letztesVorladenAm = 0
}

/** Wartet in Tests, bis das Vorladen der Projekt-Kategorien im Hintergrund fertig ist. */
export async function _warteAufVorladenForTesting() {
    await laufendesVorladen
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
            const db = await initDB()
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
        const db = await initDB()
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
            durationMinutes,
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

    // ==================== SYNC PENDING (public wrapper) ====================
    // Sendet nur die Warteschlange. Läuft schon ein Abgleich, wird dessen Ergebnis abgewartet.
    async syncPending() {
        const ergebnis = laufenderAbgleich
            ? await laufenderAbgleich.ergebnis
            : await starteAbgleich('warteschlange', async () => {
                if (!await isServerReachable()) return { ...KEIN_ABGLEICH }
                const r = await OfflineService._syncPendingInternal()
                return {
                    success: !r.abgebrochen,
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

    // ==================== SYNC PENDING (internal - no lock) ====================
    // CRITICAL RULE: This method NEVER touches localStorage/zeiterfassung_active_session.
    // The local session belongs to the user. Only explicit user actions (stop, pause, switch) may clear it.
    // A sync rejection (4xx) means the SERVER rejected an old queued entry - that's not reason
    // to destroy the user's current active booking display.
    async _syncPendingInternal() {
        await OfflineService._requeueInflightEntries()
        const pending = await OfflineService.getPendingEntries()
        if (pending.length === 0) return { synced: 0, failed: 0, discarded: 0, startSynced: false, abgebrochen: false }

        console.log(`🔄 Sync: ${pending.length} ausstehende Einträge`)

        let synced = 0
        let failed = 0
        let discarded = 0
        let startSynced = false
        // true = Schleife wegen Netz-/Serverproblem beendet; Rest bleibt in der Warteschlange
        let abgebrochen = false
        const db = await initDB()

        // Nur zurückschreiben, wenn der Eintrag noch da ist: sendeBuchung kann dieselbe
        // Buchung gleichzeitig erfolgreich übertragen und schon gelöscht haben.
        const zurueckInDieWarteschlange = async (eintrag: PendingEntry) => {
            if (await db.get('pending', eintrag.id)) await db.put('pending', eintrag)
        }
        const mitServerfehler = (eintrag: PendingEntry): PendingEntry => ({
            ...eintrag,
            serverfehlerVersuche: (eintrag.serverfehlerVersuche ?? 0) + 1,
            ersterServerfehlerAm: eintrag.ersterServerfehlerAm ?? new Date().toISOString(),
        })

        // Sort by originalTime to replay actions in chronological order
        // Bei gleichem Zeitpunkt (Wechsel = Stop + Start in derselben Millisekunde)
        // zuerst beenden, dann starten.
        const reihenfolge = { stop: 0, pause: 1, start: 2 } as const
        pending.sort((a, b) =>
            new Date(a.originalTime).getTime() - new Date(b.originalTime).getTime()
            || reihenfolge[a.type] - reihenfolge[b.type])

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

            // Hängt eine Anfrage (z. B. beim Wechsel WLAN → Mobilfunk), würde sonst der
            // ganze Abgleich und damit jede neue Buchung dahinter warten. Abbrechen ist
            // gefahrlos: Der nächste Versuch trägt denselben Idempotenz-Schlüssel.
            const abbruch = new AbortController()
            const abbruchTimer = setTimeout(() => abbruch.abort(), ABGLEICH_ANFRAGE_ZEITLIMIT_MS)
            try {
                const res = await fetch(`/api/zeiterfassung/${entry.type}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(buildBookingRequestPayload(entry.data, entry.originalTime, entry.id)),
                    signal: abbruch.signal,
                })

                if (res.ok) {
                    await db.delete('pending', entry.id)
                    synced++
                    if (entry.type === 'start') startSynced = true
                    console.log(`✅ Sync OK: ${entry.type} (${entry.id.substring(0, 8)})`)
                } else if (res.status >= 500 && haengtDauerhaft(mitServerfehler(inflightEntry))) {
                    // Antwortet der Server seit über einem Tag immer wieder mit einem Fehler,
                    // darf dieser eine Eintrag nicht alle späteren Buchungen für immer
                    // aufhalten. Er wandert (nicht gelöscht!) in die Reparatur-Liste.
                    console.warn(`🔧 Sync hängt seit über einem Tag (${res.status}), verschiebe in Reparatur-Liste: ${entry.type}`)
                    await db.put('failed', {
                        id: entry.id,
                        type: entry.type,
                        data: entry.data,
                        timestamp: entry.timestamp,
                        originalTime: entry.originalTime,
                        failedAt: new Date().toISOString(),
                        httpStatus: res.status,
                        serverError: 'Der Server hat diese Buchung über einen Tag lang nicht angenommen.',
                        attemptCount: inflightEntry.attemptCount,
                        durationMinutes: entry.durationMinutes,
                    })
                    await db.delete('pending', entry.id)
                    discarded++
                } else if (istVoruebergehenderFehler(res.status)) {
                    // 408/425/429/5xx: später nochmal. Eintrag bleibt in der Warteschlange
                    // und die Schleife bricht ab – sonst überholt z. B. ein späterer
                    // Start einen älteren Feierabend und die Reihenfolge am Server stimmt nicht.
                    console.warn(`🔁 Sync später erneut (${res.status}): ${entry.type}`)
                    await zurueckInDieWarteschlange({
                        ...(res.status >= 500 ? mitServerfehler(inflightEntry) : inflightEntry),
                        status: 'pending',
                        lastError: `HTTP ${res.status}`,
                    })
                    failed += pending.length - i
                    abgebrochen = true
                    break
                } else {
                    // Echte Ablehnung (übrige 4xx) - dieser Eintrag wird so NIEMALS
                    // erfolgreich syncen. Statt ihn STILL zu löschen (= Datenverlust für den
                    // Handwerker!) verschieben wir ihn in die Reparatur-Liste.
                    // Der Handwerker sieht in der App, dass etwas nicht durchkam,
                    // und kann die Buchung manuell verwerfen oder ein Admin daraus
                    // eine Korrekturbuchung machen.
                    // WICHTIG: Wir berühren localStorage NIE - die lokale Session
                    // gehört dem User und darf nur durch explizite User-Aktionen
                    // gelöscht werden.
                    const serverError = await leseFehlermeldung(res, `HTTP ${res.status}`)
                    console.warn(`🔧 Sync abgelehnt (${res.status}), verschiebe in Reparatur-Liste: ${entry.type} - ${serverError}`)

                    await db.put('failed', {
                        id: entry.id,
                        type: entry.type,
                        data: entry.data,
                        timestamp: entry.timestamp,
                        originalTime: entry.originalTime,
                        failedAt: new Date().toISOString(),
                        httpStatus: res.status,
                        serverError,
                        attemptCount: inflightEntry.attemptCount,
                        durationMinutes: entry.durationMinutes,
                    })
                    await db.delete('pending', entry.id)
                    discarded++
                }
            } catch {
                // Netzwerkfehler mitten im Abgleich: alle übrigen Einträge bleiben stehen
                await zurueckInDieWarteschlange({
                    ...inflightEntry,
                    status: 'pending',
                    lastError: 'Network error during sync',
                })
                const remaining = pending.length - i
                console.log(`📴 Netzwerkfehler während Sync - ${remaining} verbleibende Einträge werden übersprungen`)
                failed += remaining
                abgebrochen = true
                break
            } finally {
                clearTimeout(abbruchTimer)
            }
        }

        console.log(`📊 Sync-Ergebnis: ${synced} OK, ${discarded} verworfen, ${failed} fehlgeschlagen`)
        return { synced, failed, discarded, startSynced, abgebrochen }
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
        const db = await initDB()
        return db.getAll('failed')
    },

    async getFailedCount(): Promise<number> {
        const db = await initDB()
        return db.count('failed')
    },

    async removeFailedEntry(id: string) {
        const db = await initDB()
        await db.delete('failed', id)
    },

    async clearFailed() {
        const db = await initDB()
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
        const db = await initDB()
        await db.clear('master_data')
    },

    // Clear all pending entries - USE WITH CAUTION!
    // Only call this when you are absolutely sure all entries have been synced.
    async clearPending() {
        const db = await initDB()
        await db.clear('pending')
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

// ==================== BUCHUNG SENDEN (Start, Pause, Feierabend) ====================

/** Event, mit dem jede Stelle der App einen Abgleich anstoßen kann (App.tsx hört darauf). */
export const ABGLEICH_ANSTOSSEN_EVENT = 'zeiterfassung:abgleich-anstossen'

/** Bittet die App, die Warteschlange so bald wie möglich zu übertragen. */
export function abgleichAnstossen() {
    window.dispatchEvent(new CustomEvent(ABGLEICH_ANSTOSSEN_EVENT))
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

export type BuchungsTyp = 'start' | 'stop' | 'pause'

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
    const db = await initDB()
    const jetztGespeichert = new Date().toISOString()
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
    const eigenenEintragEntfernen = () => db.delete('pending', operationId)
    const spaeterNochmal = async (): Promise<SendeBuchungErgebnis> => {
        // Ein paralleler Abgleich kann den Eintrag schon übertragen und gelöscht haben.
        const gespeichert = await db.get('pending', operationId)
        if (gespeichert) await db.put('pending', { ...normalizePendingEntry(gespeichert), status: 'pending' })
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
