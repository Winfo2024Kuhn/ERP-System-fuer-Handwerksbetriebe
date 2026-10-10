import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
