import { describe, it, expect, vi, beforeEach } from 'vitest'
import { NotificationService } from './NotificationService'

describe('NotificationService', () => {
    beforeEach(() => {
        vi.restoreAllMocks()
    })

    describe('isSupported()', () => {
        it('gibt true zurück wenn Notification und serviceWorker vorhanden', () => {
            vi.stubGlobal('Notification', { permission: 'default' })
            vi.stubGlobal('navigator', {
                serviceWorker: { ready: Promise.resolve({}) }
            })
            expect(NotificationService.isSupported()).toBe(true)
        })

        it('gibt false zurück wenn Notification fehlt', () => {
            const orig = window.Notification
            // @ts-expect-error: remove Notification from window
            delete window.Notification
            try {
                expect(NotificationService.isSupported()).toBe(false)
            } finally {
                window.Notification = orig
            }
        })
    })

    describe('isPushSupported()', () => {
        it('gibt true zurück wenn PushManager vorhanden', () => {
            vi.stubGlobal('PushManager', {})
            vi.stubGlobal('navigator', {
                serviceWorker: { ready: Promise.resolve({}) }
            })
            expect(NotificationService.isPushSupported()).toBe(true)
        })

        it('gibt false zurück wenn PushManager fehlt', () => {
            // Remove PushManager
            const orig = (globalThis as Record<string, unknown>).PushManager;
            delete (globalThis as Record<string, unknown>).PushManager;
            vi.stubGlobal('navigator', {
                serviceWorker: { ready: Promise.resolve({}) }
            })
            expect(NotificationService.isPushSupported()).toBe(false)
            if (orig) vi.stubGlobal('PushManager', orig)
        })
    })

    describe('getPermissionStatus()', () => {
        it('gibt "granted" zurück wenn Notification.permission granted', () => {
            vi.stubGlobal('Notification', { permission: 'granted' })
            expect(NotificationService.getPermissionStatus()).toBe('granted')
        })

        it('gibt "denied" zurück wenn Notification.permission denied', () => {
            vi.stubGlobal('Notification', { permission: 'denied' })
            expect(NotificationService.getPermissionStatus()).toBe('denied')
        })

        it('gibt "unsupported" zurück wenn kein Notification', () => {
            const orig = window.Notification
            // @ts-expect-error: remove Notification from window
            delete window.Notification
            try {
                expect(NotificationService.getPermissionStatus()).toBe('unsupported')
            } finally {
                window.Notification = orig
            }
        })
    })

    describe('requestPermission()', () => {
        it('gibt true zurück wenn bereits granted', async () => {
            vi.stubGlobal('Notification', { permission: 'granted', requestPermission: vi.fn() })
            const result = await NotificationService.requestPermission()
            expect(result).toBe(true)
        })

        it('gibt false zurück wenn denied', async () => {
            vi.stubGlobal('Notification', { permission: 'denied', requestPermission: vi.fn() })
            const result = await NotificationService.requestPermission()
            expect(result).toBe(false)
        })

        it('fragt Berechtigung an wenn default', async () => {
            vi.stubGlobal('Notification', {
                permission: 'default',
                requestPermission: vi.fn().mockResolvedValue('granted')
            })
            const result = await NotificationService.requestPermission()
            expect(result).toBe(true)
        })

        it('gibt false zurück wenn Nutzer ablehnt', async () => {
            vi.stubGlobal('Notification', {
                permission: 'default',
                requestPermission: vi.fn().mockResolvedValue('denied')
            })
            const result = await NotificationService.requestPermission()
            expect(result).toBe(false)
        })

        it('gibt false zurück wenn Notification nicht unterstützt', async () => {
            const orig = window.Notification
            // @ts-expect-error: remove Notification from window
            delete window.Notification
            try {
                const result = await NotificationService.requestPermission()
                expect(result).toBe(false)
            } finally {
                window.Notification = orig
            }
        })
    })

    describe('subscribeToPush()', () => {
        it('gibt false zurück wenn PushManager nicht unterstützt', async () => {
            delete (globalThis as Record<string, unknown>).PushManager
            vi.stubGlobal('navigator', {
                serviceWorker: { ready: Promise.resolve({}) }
            })
            const result = await NotificationService.subscribeToPush('test-token')
            expect(result).toBe(false)
        })

        it('gibt false zurück wenn VAPID-Key-Anfrage fehlschlägt', async () => {
            vi.stubGlobal('PushManager', {})
            vi.stubGlobal('navigator', {
                serviceWorker: { ready: Promise.resolve({}) }
            })
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
                ok: false,
                status: 500
            }))

            const result = await NotificationService.subscribeToPush('test-token')
            expect(result).toBe(false)
        })

        it('gibt false zurück wenn Push auf Server deaktiviert', async () => {
            vi.stubGlobal('PushManager', {})
            vi.stubGlobal('navigator', {
                serviceWorker: { ready: Promise.resolve({}) }
            })
            vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
                ok: true,
                json: () => Promise.resolve({ publicKey: '', enabled: 'false' })
            }))

            const result = await NotificationService.subscribeToPush('test-token')
            expect(result).toBe(false)
        })

        it('encodiert Token im URL-Parameter', async () => {
            vi.stubGlobal('PushManager', {})
            vi.stubGlobal('navigator', {
                serviceWorker: { ready: Promise.resolve({}) }
            })
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true,
                json: () => Promise.resolve({ publicKey: '', enabled: 'false' })
            })
            vi.stubGlobal('fetch', mockFetch)

            await NotificationService.subscribeToPush('token with spaces&special')
            expect(mockFetch).toHaveBeenCalledWith(
                expect.stringContaining(encodeURIComponent('token with spaces&special'))
            )
        })
    })
})
