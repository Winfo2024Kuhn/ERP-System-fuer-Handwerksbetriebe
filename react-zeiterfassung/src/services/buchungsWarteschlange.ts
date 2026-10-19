// Warteschlange für Buchungen (Start, Pause, Feierabend), die noch zum Server müssen.
//
// Läuft in der App UND im Service Worker: Die App sendet, solange sie offen ist; der
// Service Worker übernimmt auf Android (Background Sync), wenn die App geschlossen ist.
// Deshalb hier nichts, was es nur im Fenster gibt – kein window, kein localStorage.
import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import { leseFehlermeldung } from '../lib/apiFehler'

export type BuchungsTyp = 'start' | 'stop' | 'pause'

export interface PendingEntry {
    id: string // Also used as idempotency key on the server
    type: BuchungsTyp
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
    type: BuchungsTyp
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

export interface ZeiterfassungDB extends DBSchema {
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

const DB_NAME = 'zeiterfassung-db'
// Version 2: 'failed' Store für Reparatur-Liste hinzugefügt (Mobile-First).
const DB_VERSION = 2

let dbPromise: Promise<IDBPDatabase<ZeiterfassungDB>> | undefined

export function oeffneBuchungsDB() {
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

/**
 * Antworten, bei denen ein späterer Versuch klappen kann: Zeitüberschreitung,
 * „zu früh“, zu viele Anfragen (Gateway oder gesperrte Internetadresse) und
 * Serverfehler. Alles andere aus dem 4xx-Bereich ist eine echte Ablehnung.
 */
export function istVoruebergehenderFehler(status: number): boolean {
    return status === 408 || status === 425 || status === 429 || status >= 500
}

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

export function normalizePendingEntry(entry: PendingEntry): PendingEntry {
    return {
        ...entry,
        status: entry.status ?? 'pending',
        attemptCount: entry.attemptCount ?? 0,
        lastAttemptAt: entry.lastAttemptAt,
        lastError: entry.lastError,
    }
}

/** Name der Sperre, die App und Service Worker teilen. */
const SPERRE = 'zeiterfassung-buchungen-senden'

/**
 * Sorgt dafür, dass nie App und Service Worker gleichzeitig die Warteschlange
 * abarbeiten – sonst könnten beide denselben Eintrag senden und sich gegenseitig
 * den Status überschreiben. Ohne Web Locks (alte Browser) läuft die Arbeit direkt;
 * doppelt gesendet wäre dank Idempotenz-Schlüssel trotzdem harmlos.
 */
export async function mitWarteschlangenSperre<T>(arbeit: () => Promise<T>): Promise<T> {
    const locks = typeof navigator === 'undefined' ? undefined : navigator.locks
    if (!locks?.request) return arbeit()
    return locks.request(SPERRE, () => arbeit()) as Promise<T>
}

/** Alle wartenden Buchungen, auf den aktuellen Datenstand gebracht. */
export async function leseWartendeEintraege(): Promise<PendingEntry[]> {
    const db = await oeffneBuchungsDB()
    return (await db.getAll('pending')).map(normalizePendingEntry)
}

/**
 * Schreibt einen wartenden Eintrag zurück – aber nur, wenn es ihn noch gibt. Lesen und
 * Schreiben laufen in EINEM Datenbankschritt: Sonst kann ein paralleler Abgleich den
 * Eintrag dazwischen übertragen und löschen, und er stünde danach wieder in der
 * Warteschlange (und ginge ein weiteres Mal raus).
 *
 * @returns der geschriebene Eintrag, oder null, wenn es ihn nicht mehr gab
 */
export async function schreibeFallsNochWartend(
    id: string,
    aendern: (gespeichert: PendingEntry) => PendingEntry,
): Promise<PendingEntry | null> {
    const db = await oeffneBuchungsDB()
    const tx = db.transaction('pending', 'readwrite')
    const gespeichert = await tx.store.get(id)
    const neu = gespeichert ? aendern(normalizePendingEntry(gespeichert)) : null
    if (neu) await tx.store.put(neu)
    await tx.done
    return neu
}

/**
 * So lange gilt eine Buchung, die die App gerade selbst sendet, als „noch unterwegs“.
 * Der Service Worker fasst sie in dieser Zeit nicht an (sonst ginge sie doppelt raus).
 */
export const FRISCH_UNTERWEGS_MS = 60_000

function istFrischUnterwegs(entry: PendingEntry): boolean {
    if (entry.status !== 'inflight' || !entry.lastAttemptAt) return false
    const alter = Date.now() - new Date(entry.lastAttemptAt).getTime()
    // Negativ = Handy-Uhr wurde zurückgestellt: dann nicht ewig warten.
    return alter >= 0 && alter < FRISCH_UNTERWEGS_MS
}

/**
 * Gibt Einträge frei, die noch als „wird gerade gesendet“ markiert sind – etwa weil
 * die App mitten im Senden eingefroren oder beendet wurde.
 */
export async function gibHaengendeEintraegeFrei(ausnehmen: ReadonlySet<string> = new Set()) {
    // Ein Datenbankschritt: Ein gerade gelöschter (übertragener) Eintrag kommt nicht zurück.
    const db = await oeffneBuchungsDB()
    const tx = db.transaction('pending', 'readwrite')
    for (let cursor = await tx.store.openCursor(); cursor; cursor = await cursor.continue()) {
        const entry = normalizePendingEntry(cursor.value)
        // Was die App gerade selbst direkt sendet, ist nicht hängengeblieben.
        if (entry.status === 'inflight' && !ausnehmen.has(entry.id)) {
            await cursor.update({
                ...entry,
                status: 'pending',
                lastError: entry.lastError || 'Recovered inflight entry for retry',
            })
        }
    }
    await tx.done
}

export interface WarteschlangenErgebnis {
    synced: number
    failed: number
    discarded: number
    startSynced: boolean
    /** true = wegen Netz-/Serverproblem beendet; der Rest bleibt in der Warteschlange */
    abgebrochen: boolean
    /**
     * true = nur gewartet, weil die App eine Buchung gerade selbst direkt sendet. Kein
     * Fehler: Nach der Antwort stößt sendeBuchung den nächsten Abgleich an.
     */
    wartetAufDirektSendung?: boolean
}

/**
 * Sendet alle wartenden Buchungen in zeitlicher Reihenfolge.
 *
 * - 2xx: Eintrag ist erledigt.
 * - 408/425/429/5xx oder Netzfehler: Eintrag bleibt stehen, Schleife bricht ab
 *   (sonst überholt ein späterer Start einen älteren Feierabend).
 * - übrige 4xx: Eintrag wandert in die Reparatur-Liste (nie still löschen).
 *
 * Fasst NIE localStorage/die lokale Session an – die gehört dem Nutzer.
 * Ohne Sperre: Aufrufer nutzen {@link mitWarteschlangenSperre}.
 */
export async function arbeiteWarteschlangeAb(
    optionen: {
        frischUnterwegsAbwarten?: boolean
        /**
         * IDs, die die App in diesem Moment selbst direkt sendet (sendeBuchung). Solange
         * eine davon wartet, sendet der Abgleich gar nichts: Er würde sie sonst doppelt
         * schicken – oder eine spätere Buchung an ihr vorbei.
         */
        direktUnterwegs?: ReadonlySet<string>
    } = {},
): Promise<WarteschlangenErgebnis> {
    // Service Worker: Sendet die App gerade selbst (Handy eben erst gesperrt), nicht
    // dazwischenfunken – abbrechen, der Browser versucht es später erneut.
    if (optionen.frischUnterwegsAbwarten) {
        const unterwegs = (await leseWartendeEintraege()).filter(istFrischUnterwegs)
        if (unterwegs.length > 0) {
            console.log(`⏳ ${unterwegs.length} Buchung(en) gerade unterwegs - später erneut`)
            return { synced: 0, failed: unterwegs.length, discarded: 0, startSynced: false, abgebrochen: true }
        }
    }
    const direktUnterwegs = optionen.direktUnterwegs ?? new Set<string>()
    // Nur frische zählen: Friert Android die Seite mitten im Senden ein, soll eine solche
    // Buchung nach spätestens FRISCH_UNTERWEGS_MS wieder vom Abgleich gesendet werden.
    const veralteteDirektSendungen = new Set<string>()
    if (direktUnterwegs.size > 0) {
        const vorgemerkt = (await leseWartendeEintraege()).filter(e => direktUnterwegs.has(e.id))
        for (const e of vorgemerkt) if (!istFrischUnterwegs(e)) veralteteDirektSendungen.add(e.id)
        const unterwegs = vorgemerkt.filter(e => !veralteteDirektSendungen.has(e.id))
        if (unterwegs.length > 0) {
            // Nach der Direkt-Sendung stößt sendeBuchung den nächsten Abgleich selbst an.
            console.log(`⏳ ${unterwegs.length} Buchung(en) werden gerade direkt gesendet - danach weiter`)
            return { synced: 0, failed: 0, discarded: 0, startSynced: false, abgebrochen: true, wartetAufDirektSendung: true }
        }
    }
    const wirdDirektGesendet = (id: string) => direktUnterwegs.has(id) && !veralteteDirektSendungen.has(id)
    await gibHaengendeEintraegeFrei(new Set([...direktUnterwegs].filter(wirdDirektGesendet)))
    const db = await oeffneBuchungsDB()
    const pending = await leseWartendeEintraege()
    if (pending.length === 0) return { synced: 0, failed: 0, discarded: 0, startSynced: false, abgebrochen: false }

    console.log(`🔄 Sync: ${pending.length} ausstehende Einträge`)

    let synced = 0
    let failed = 0
    let discarded = 0
    let startSynced = false
    let abgebrochen = false
    let wartetAufDirektSendung = false

    // Nur zurückschreiben, wenn der Eintrag noch da ist: sendeBuchung kann dieselbe
    // Buchung gleichzeitig erfolgreich übertragen und schon gelöscht haben.
    const zurueckInDieWarteschlange = async (eintrag: PendingEntry) => {
        await schreibeFallsNochWartend(eintrag.id, () => eintrag)
    }
    const mitServerfehler = (eintrag: PendingEntry): PendingEntry => ({
        ...eintrag,
        serverfehlerVersuche: (eintrag.serverfehlerVersuche ?? 0) + 1,
        ersterServerfehlerAm: eintrag.ersterServerfehlerAm ?? new Date().toISOString(),
    })
    const inDieReparaturListe = async (entry: PendingEntry, httpStatus: number, serverError: string, attemptCount: number) => {
        await db.put('failed', {
            id: entry.id,
            type: entry.type,
            data: entry.data,
            timestamp: entry.timestamp,
            originalTime: entry.originalTime,
            failedAt: new Date().toISOString(),
            httpStatus,
            serverError,
            attemptCount,
            durationMinutes: entry.durationMinutes,
        })
        await db.delete('pending', entry.id)
        discarded++
    }

    // Sort by originalTime to replay actions in chronological order
    // Bei gleichem Zeitpunkt (Wechsel = Stop + Start in derselben Millisekunde)
    // zuerst beenden, dann starten.
    const reihenfolge = { stop: 0, pause: 1, start: 2 } as const
    pending.sort((a, b) =>
        new Date(a.originalTime).getTime() - new Date(b.originalTime).getTime()
        || reihenfolge[a.type] - reihenfolge[b.type])

    for (let i = 0; i < pending.length; i++) {
        const entry = pending[i]

        // Hat die App diese Buchung inzwischen selbst direkt losgeschickt: anhalten, damit
        // sie nicht doppelt rausgeht und nichts Späteres an ihr vorbeizieht.
        if (wirdDirektGesendet(entry.id)) {
            wartetAufDirektSendung = true
            abgebrochen = true
            break
        }

        // Nur senden, wenn der Eintrag noch da ist (ein anderer Weg kann ihn schon
        // übertragen haben) – prüfen und als „wird gesendet“ markieren in einem Schritt.
        const inflightEntry = await schreibeFallsNochWartend(entry.id, gespeichert => ({
            ...gespeichert,
            status: 'inflight',
            attemptCount: gespeichert.attemptCount + 1,
            lastAttemptAt: new Date().toISOString(),
            lastError: undefined,
        }))
        if (!inflightEntry) {
            console.log(`⏭️ Entry bereits verarbeitet: ${entry.type} (${entry.id.substring(0, 8)})`)
            continue
        }

        // Der Service Worker läuft ohne den Token-Zusatz der App (auth/mobileTransport),
        // deshalb den Zugang hier selbst mitschicken. In der App setzt der Zusatz ihn erneut.
        const headers: Record<string, string> = { 'Content-Type': 'application/json' }
        if (typeof entry.data.token === 'string' && entry.data.token) headers['X-Auth-Token'] = entry.data.token

        // Hängt eine Anfrage (z. B. beim Wechsel WLAN → Mobilfunk), würde sonst der
        // ganze Abgleich und damit jede neue Buchung dahinter warten. Abbrechen ist
        // gefahrlos: Der nächste Versuch trägt denselben Idempotenz-Schlüssel.
        const abbruch = new AbortController()
        const abbruchTimer = setTimeout(() => abbruch.abort(), ABGLEICH_ANFRAGE_ZEITLIMIT_MS)
        try {
            const res = await fetch(`/api/zeiterfassung/${entry.type}`, {
                method: 'POST',
                headers,
                body: JSON.stringify(buildBookingRequestPayload(entry.data, entry.originalTime, entry.id)),
                // Der Token-Header darf bei einer Weiterleitung nicht den Ursprung verlassen.
                redirect: 'error',
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
                await inDieReparaturListe(entry, res.status,
                    'Der Server hat diese Buchung über einen Tag lang nicht angenommen.', inflightEntry.attemptCount)
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
                // Handwerker!) verschieben wir ihn in die Reparatur-Liste. Der Handwerker
                // sieht in der App, dass etwas nicht durchkam, und kann die Buchung
                // verwerfen oder ein Admin daraus eine Korrekturbuchung machen.
                const serverError = await leseFehlermeldung(res, `HTTP ${res.status}`)
                console.warn(`🔧 Sync abgelehnt (${res.status}), verschiebe in Reparatur-Liste: ${entry.type} - ${serverError}`)
                await inDieReparaturListe(entry, res.status, serverError, inflightEntry.attemptCount)
            }
        } catch {
            // Netzwerkfehler (oder Zeitlimit) mitten im Abgleich: alle übrigen Einträge bleiben stehen
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
    return { synced, failed, discarded, startSynced, abgebrochen, wartetAufDirektSendung }
}

// ==================== HINTERGRUND-ÜBERTRAGUNG (Android) ====================

/** Tag der Background-Sync-Anmeldung; der Service Worker hört genau darauf. */
export const HINTERGRUND_SYNC_TAG = 'buchungen-senden'

/** Nachricht des Service Workers an offene App-Fenster, nachdem er Buchungen gesendet hat. */
export const BUCHUNGEN_UEBERTRAGEN_NACHRICHT = 'BUCHUNGEN_UEBERTRAGEN'
