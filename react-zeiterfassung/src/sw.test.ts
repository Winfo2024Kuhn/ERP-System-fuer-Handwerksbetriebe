import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import 'fake-indexeddb/auto'
import { IDBFactory } from 'fake-indexeddb'
import { NotificationService } from './services/NotificationService'

// Workbox transport is an external boundary; exercise our route selection and event handlers.
vi.mock('workbox-precaching', () => ({ cleanupOutdatedCaches: vi.fn(), precacheAndRoute: vi.fn() }))
vi.mock('workbox-core', () => ({ clientsClaim: vi.fn() }))
vi.mock('workbox-routing', () => ({ registerRoute: vi.fn() }))
vi.mock('workbox-strategies', () => ({
    CacheFirst: class {}, NetworkFirst: class {},
    NetworkOnly: class { options: unknown; constructor(options?: unknown) { this.options = options } },
}))
vi.mock('workbox-expiration', () => ({ ExpirationPlugin: class {} }))

type WorkerEvent = { data?: unknown; tag?: string; waitUntil: (work: Promise<unknown>) => void }
const handlers = new Map<string, (event: WorkerEvent) => void>()
let send: ReturnType<typeof vi.fn<typeof fetch>>

async function startWorker() {
    handlers.clear()
    vi.resetModules()
    await import('./sw')
}

async function dispatch(type: string, data?: unknown) {
    let work: Promise<unknown> | undefined
    handlers.get(type)!({ data, tag: 'check-appointments', waitUntil: promise => { work = promise } })
    await work
}

async function readStoredToken() {
    const db = await NotificationService._openSentDB()
    try {
        return await new Promise<unknown>((resolve, reject) => {
            const request = db.transaction('sent').objectStore('sent').get('__auth_token')
            request.onsuccess = () => resolve(request.result)
            request.onerror = () => reject(request.error)
        })
    } finally {
        db.close()
    }
}

beforeEach(async () => {
    vi.stubGlobal('indexedDB', new IDBFactory())
    send = vi.fn<typeof fetch>().mockImplementation(async () => new Response('[]'))
    vi.stubGlobal('fetch', send)
    vi.stubGlobal('self', {
        __WB_MANIFEST: [],
        skipWaiting: vi.fn(),
        registration: { showNotification: vi.fn().mockResolvedValue(undefined) },
        addEventListener: (type: string, handler: (event: WorkerEvent) => void) => handlers.set(type, handler),
    })
    await startWorker()
})

afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
})

describe('Service-Worker-Anmeldung für Kalenderprüfungen', () => {
    it('kodiert den Querytoken und sendet den eigenen Header ohne automatische Weiterleitungen', async () => {
        const token = 'test-token&monat=99 #?'
        await dispatch('message', { type: 'CHECK_NOTIFICATIONS', token })
        expect(send).toHaveBeenCalledTimes(2)
        for (const [url, init] of send.mock.calls) {
            const request = new Request(new URL(String(url), 'https://erp.example'), init)
            expect(new URL(request.url).searchParams.get('token')).toBe(token)
            expect(new URL(request.url).searchParams.getAll('monat')).toHaveLength(1)
            expect(request.headers.get('X-Auth-Token')).toBe(token)
            expect(request.redirect).toBe('error')
        }
    })

    it.each([401, 403])('stoppt nach HTTP %s auch wieder gespeicherte ungültige Tokens über Worker-Neustarts hinweg', async status => {
        await NotificationService.storeTokenForSW('invalid-test-token')
        send.mockImplementation(async () => new Response('{}', { status }))
        await dispatch('periodicsync')
        expect(send).toHaveBeenCalledTimes(1)
        expect(await readStoredToken()).toBeUndefined()

        // The still-open app can send its stale token again before noticing logout.
        await NotificationService.storeTokenForSW('invalid-test-token')
        await startWorker()
        await dispatch('message', { type: 'CHECK_NOTIFICATIONS', token: 'invalid-test-token' })
        await dispatch('periodicsync')
        expect(send).toHaveBeenCalledTimes(1)

        send.mockImplementation(async () => new Response('[]'))
        await dispatch('message', { type: 'CHECK_NOTIFICATIONS', token: 'new-test-token' })
        expect(send).toHaveBeenCalledTimes(3)
    })

    it('beachtet Retry-After nach Neustart auch für einen anderen Token und prüft erst nach Ablauf erneut', async () => {
        const now = vi.spyOn(Date, 'now').mockReturnValue(1_800_000_000_000)
        await NotificationService.storeTokenForSW('test-token')
        send.mockImplementation(async () => new Response('{}', { status: 429, headers: { 'Retry-After': '4' } }))
        await dispatch('periodicsync')
        expect(send).toHaveBeenCalledTimes(1)

        await startWorker()
        now.mockReturnValue(1_800_000_003_000)
        await dispatch('periodicsync')
        await dispatch('message', { type: 'CHECK_NOTIFICATIONS', token: 'other-test-token' })
        expect(send).toHaveBeenCalledTimes(1)

        now.mockReturnValue(1_800_000_004_000)
        send.mockImplementation(async () => new Response('[]'))
        await dispatch('periodicsync')
        expect(send).toHaveBeenCalledTimes(3)
    })

    it('führt gleichzeitige Hintergrundprüfungen nur einmal aus und stoppt vor dem zweiten Monat bei Ablehnung', async () => {
        let rejectToken!: () => void
        const pending = new Promise<Response>(resolve => {
            rejectToken = () => resolve(new Response('{}', { status: 401 }))
        })
        send.mockReturnValue(pending)
        const first = dispatch('message', { type: 'CHECK_NOTIFICATIONS', token: 'test-token' })
        const second = dispatch('message', { type: 'CHECK_NOTIFICATIONS', token: 'test-token' })
        await vi.waitFor(() => expect(send).toHaveBeenCalled())
        rejectToken()
        await Promise.all([first, second])
        expect(send).toHaveBeenCalledTimes(1)
    })

    it('behält einen inzwischen neu gespeicherten Token bei einer verspäteten Ablehnung des alten Tokens', async () => {
        await NotificationService.storeTokenForSW('old-test-token')
        let rejectOldToken!: () => void
        send.mockImplementationOnce(() => new Promise<Response>(resolve => {
            rejectOldToken = () => resolve(new Response('{}', { status: 401 }))
        }))
        const running = dispatch('periodicsync')
        await vi.waitFor(() => expect(send).toHaveBeenCalledTimes(1))
        await NotificationService.storeTokenForSW('new-test-token')
        rejectOldToken()
        await running
        expect(await readStoredToken()).toMatchObject({ value: 'new-test-token' })

        await dispatch('periodicsync')
        expect(send).toHaveBeenCalledTimes(3)
        expect(new Headers(send.mock.calls[1][1]?.headers).get('X-Auth-Token')).toBe('new-test-token')
    })

    it('hebt die Ablehnung desselben Tokens erst nach explizit bestätigter Vordergrund-Anmeldung auf', async () => {
        send.mockImplementationOnce(async () => new Response('{}', { status: 403 }))
        await dispatch('message', { type: 'CHECK_NOTIFICATIONS', token: 'test-token' })
        await dispatch('message', { type: 'AUTH_VALIDATED', token: 'other-token' })
        await dispatch('message', { type: 'CHECK_NOTIFICATIONS', token: 'test-token' })
        expect(send).toHaveBeenCalledTimes(1)

        await dispatch('message', { type: 'AUTH_VALIDATED', token: 'test-token' })
        await startWorker()
        await dispatch('message', { type: 'CHECK_NOTIFICATIONS', token: 'test-token' })
        expect(send).toHaveBeenCalledTimes(3)
    })

    it('lässt eine verspätete Ablehnung eine neu bestätigte Anmeldung desselben Tokens nicht wieder sperren', async () => {
        let rejectOldCheck!: () => void
        send.mockImplementationOnce(() => new Promise<Response>(resolve => {
            rejectOldCheck = () => resolve(new Response('{}', { status: 401 }))
        }))
        const running = dispatch('message', { type: 'CHECK_NOTIFICATIONS', token: 'test-token' })
        await vi.waitFor(() => expect(send).toHaveBeenCalledTimes(1))
        await dispatch('message', { type: 'AUTH_VALIDATED', token: 'test-token' })
        rejectOldCheck()
        await running
        await dispatch('message', { type: 'CHECK_NOTIFICATIONS', token: 'test-token' })
        expect(send).toHaveBeenCalledTimes(3)
    })
})

describe('Private API-Antworten werden nicht vom Service Worker zwischengespeichert', () => {
    it.each([
        '/api/images/test.jpg/thumbnail',
        '/api/images/test.jpg/anzeige',
        '/api/dokumente/test.pdf',
        '/api/buchhaltung/mobile/belege/40',
        '/api/mitarbeiter/by-token/test-token',
        '/api/kalender/mobile?token=test-token',
    ])('holt %s immer vom Server und umgeht auch den Browsercache', async path => {
        const { registerRoute } = await import('workbox-routing')
        const { NetworkOnly } = await import('workbox-strategies')
        const url = new URL(path, 'https://erp.example')
        const request = new Request(url)
        Object.defineProperty(request, 'destination', { value: path.includes('/images/') ? 'image' : '' })
        const route = vi.mocked(registerRoute).mock.calls.find(([match]) => typeof match === 'function'
            && (match as (params: { request: Request; url: URL; sameOrigin: boolean }) => boolean)({ request, url, sameOrigin: true }))
        expect(route).toBeDefined()
        expect(route![1]).toBeInstanceOf(NetworkOnly)
        expect(route![1]).toMatchObject({ options: { fetchOptions: { cache: 'no-store' } } })
    })

    it('entfernt beim Aktivieren alte private Caches und behält die offlinefähige Anwendungshülle', async () => {
        const cacheNames = new Set(['bilder-cache', 'api-cache', 'workbox-precache-v2'])
        vi.stubGlobal('caches', { delete: async (name: string) => cacheNames.delete(name) })
        expect(handlers.has('activate')).toBe(true)
        await dispatch('activate')
        expect([...cacheNames]).toEqual(['workbox-precache-v2'])
    })
})

// Android: Die App ist zu, der Feierabend wartet noch auf dem Handy. Sobald wieder
// Netz da ist, weckt der Browser den Service Worker mit einem Sync-Ereignis.
describe('Hintergrund-Sync für wartende Buchungen', () => {
    const postMessage = vi.fn()

    async function reiheFeierabendEin(id: string, extra: { status?: 'pending' | 'inflight'; lastAttemptAt?: string } = {}) {
        const { oeffneBuchungsDB } = await import('./services/buchungsWarteschlange')
        const db = await oeffneBuchungsDB()
        await db.put('pending', {
            id, type: 'stop', data: { token: 'max-mustermann-token' },
            timestamp: '2026-10-17T16:00:00Z', originalTime: '2026-10-17T16:00:00Z',
            status: 'pending', attemptCount: 0, durationMinutes: 480, ...extra,
        })
        return db
    }

    async function sync(tag: string) {
        let work: Promise<unknown> | undefined
        handlers.get('sync')!({ tag, waitUntil: promise => { work = promise } })
        return work
    }

    beforeEach(() => {
        postMessage.mockReset()
        ;(self as unknown as { clients: unknown }).clients = {
            matchAll: vi.fn().mockResolvedValue([{ postMessage }]),
        }
    })

    it('sendet den Feierabend mit dem Zugang des Mitarbeiters und sagt offenen Fenstern Bescheid', async () => {
        const db = await reiheFeierabendEin('op-feierabend')
        send.mockImplementation(async () => new Response('{}'))

        await sync('buchungen-senden')

        expect(send).toHaveBeenCalledTimes(1)
        const [url, init] = send.mock.calls[0]
        expect(url).toBe('/api/zeiterfassung/stop')
        expect(new Headers(init?.headers).get('X-Auth-Token')).toBe('max-mustermann-token')
        expect(JSON.parse(String(init?.body))).toMatchObject({ idempotencyKey: 'op-feierabend', originalZeit: '2026-10-17T16:00:00Z' })
        expect(await db.count('pending')).toBe(0)
        expect(postMessage).toHaveBeenCalledWith({ type: 'BUCHUNGEN_UEBERTRAGEN' })
    })

    it('meldet bei 429 einen Fehlschlag, damit der Browser es später erneut versucht, und behält den Eintrag', async () => {
        const db = await reiheFeierabendEin('op-voll')
        send.mockImplementation(async () => new Response('{"error":"Zu viele Anfragen. Bitte kurz warten."}', { status: 429 }))

        await expect(sync('buchungen-senden')).rejects.toThrow('später erneut')

        expect(await db.count('pending')).toBe(1)
        expect(await db.count('failed')).toBe(0)
        expect(postMessage).not.toHaveBeenCalled()
    })

    it('meldet bei Netzfehler einen Fehlschlag und behält den Eintrag', async () => {
        const db = await reiheFeierabendEin('op-funkloch')
        send.mockRejectedValue(new TypeError('Failed to fetch'))

        await expect(sync('buchungen-senden')).rejects.toThrow('später erneut')

        expect(await db.count('pending')).toBe(1)
    })

    it('zählt bei Serverfehlern mit und versucht es später erneut', async () => {
        const db = await reiheFeierabendEin('op-500')
        send.mockImplementation(async () => new Response('{}', { status: 500 }))

        await expect(sync('buchungen-senden')).rejects.toThrow('später erneut')

        expect(await db.get('pending', 'op-500')).toMatchObject({ serverfehlerVersuche: 1, status: 'pending' })
    })

    // Echte Ablehnung (z. B. widerrufener Zugang): nicht ewig wiederholen, sondern
    // in die Reparatur-Liste – und die App soll es beim nächsten Öffnen sehen.
    it.each([400, 401])('legt eine echte Ablehnung (HTTP %i) in die Reparatur-Liste, ohne den Browser erneut versuchen zu lassen', async (status) => {
        const db = await reiheFeierabendEin('op-abgelehnt')
        send.mockImplementation(async () => new Response('{"error":"Keine aktive Buchung gefunden"}', { status }))

        await sync('buchungen-senden')

        expect(await db.count('pending')).toBe(0)
        expect(await db.get('failed', 'op-abgelehnt')).toMatchObject({ httpStatus: status, durationMinutes: 480 })
        expect(postMessage).toHaveBeenCalledWith({ type: 'BUCHUNGEN_UEBERTRAGEN' })
    })

    // Handy eben gesperrt, die App sendet gerade selbst (keepalive): nicht doppelt senden.
    it('wartet eine Buchung ab, die die App gerade erst selbst sendet', async () => {
        const db = await reiheFeierabendEin('op-unterwegs', { status: 'inflight', lastAttemptAt: new Date().toISOString() })

        await expect(sync('buchungen-senden')).rejects.toThrow('später erneut')

        expect(send).not.toHaveBeenCalled()
        expect(await db.get('pending', 'op-unterwegs')).toMatchObject({ status: 'inflight' })
    })

    it('sendet eine liegengebliebene Buchung, wenn die App beim Senden beendet wurde', async () => {
        const vorZweiMinuten = new Date(Date.now() - 2 * 60 * 1000).toISOString()
        const db = await reiheFeierabendEin('op-liegengeblieben', { status: 'inflight', lastAttemptAt: vorZweiMinuten })
        send.mockImplementation(async () => new Response('{}'))

        await sync('buchungen-senden')

        expect(send).toHaveBeenCalledTimes(1)
        expect(await db.count('pending')).toBe(0)
    })

    it('ignoriert fremde Sync-Ereignisse', async () => {
        await reiheFeierabendEin('op-fremd')

        expect(await sync('etwas-anderes')).toBeUndefined()
        expect(send).not.toHaveBeenCalled()
    })
})

// Push vom Server: erscheint im Notification Center, auch wenn die App zu ist.
describe('Push-Nachrichten zur Zeiterfassung', () => {
    function pushDaten(payload: Record<string, unknown>) {
        return { json: () => payload }
    }

    function gezeigteNachricht() {
        const zeige = vi.mocked((self as unknown as ServiceWorkerGlobalScope).registration.showNotification)
        expect(zeige).toHaveBeenCalledTimes(1)
        const [titel, optionen] = zeige.mock.calls[0]
        return { titel, optionen: optionen as NotificationOptions & { data: { url: string } } }
    }

    it('zeigt einen automatisch beendeten Feierabend mit eigenem Tag je Tag und Link zu den Tagesbuchungen', async () => {
        await dispatch('push', pushDaten({
            title: 'Kein Feierabend gestempelt',
            body: 'Deine Arbeitszeit vom 16.10. wurde automatisch um 20:00 Uhr beendet.',
            url: '/zeiterfassung/tagesbuchungen',
            appointmentId: null,
            type: 'zeit-auto-beendet',
            timestamp: 1760637600000,
        }))

        const { titel, optionen } = gezeigteNachricht()
        expect(titel).toBe('🕗 Kein Feierabend gestempelt')
        expect(optionen.body).toContain('automatisch um 20:00 Uhr beendet')
        expect(optionen.tag).toBe('zeit-auto-beendet-1760637600000')
        expect((optionen as { renotify?: boolean }).renotify).toBeUndefined()
        expect(optionen.data.url).toBe('/zeiterfassung/tagesbuchungen')
    })

    it('öffnet beim automatisch beendeten Feierabend ohne Link die Tagesbuchungen', async () => {
        await dispatch('push', pushDaten({
            title: 'Kein Feierabend gestempelt', body: 'Bitte prüf die Zeit.', url: '',
            appointmentId: null, type: 'zeit-auto-beendet', timestamp: 1,
        }))

        expect(gezeigteNachricht().optionen.data.url).toBe('/zeiterfassung/tagesbuchungen')
    })

    it('zeigt die Pausen-Erinnerung mit festem Tag, damit eine neue die alte ersetzt', async () => {
        await dispatch('push', pushDaten({
            title: 'Zeit für eine Pause',
            body: 'Du arbeitest heute schon 5:45 Std.',
            url: '/zeiterfassung/zeiterfassung',
            appointmentId: null,
            type: 'pause',
            timestamp: 1760620000000,
        }))

        const { titel, optionen } = gezeigteNachricht()
        expect(titel).toBe('☕ Zeit für eine Pause')
        expect(optionen.tag).toBe('pause-erinnerung')
        // Ohne renotify ersetzt die 9-Stunden-Erinnerung die alte stumm.
        expect((optionen as { renotify?: boolean }).renotify).toBe(true)
        expect(optionen.data.url).toBe('/zeiterfassung/zeiterfassung')
    })

    it('öffnet bei der Pausen-Erinnerung ohne Link die Zeiterfassung', async () => {
        await dispatch('push', pushDaten({
            title: 'Zeit für eine Pause', body: 'Ab 6 Stunden sind 30 Minuten Pause Pflicht.', url: '',
            appointmentId: null, type: 'pause', timestamp: 2,
        }))

        expect(gezeigteNachricht().optionen.data.url).toBe('/zeiterfassung/zeiterfassung')
    })

    it('lässt allgemeine Mitarbeiter-Nachrichten unverändert', async () => {
        await dispatch('push', pushDaten({
            title: 'Urlaub genehmigt', body: 'Dein Urlaub wurde genehmigt.', url: '',
            appointmentId: null, type: 'mitarbeiter', timestamp: 3,
        }))

        const { titel, optionen } = gezeigteNachricht()
        expect(titel).toBe('🔔 Urlaub genehmigt')
        expect(optionen.tag).toBe('push-3')
        expect(optionen.data.url).toBe('/zeiterfassung/')
    })
})
