import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
    starteBenachrichtigungenFallsErlaubt,
    frageBenachrichtigungenAn,
    stoppeBenachrichtigungsIntervall,
} from './notificationBootstrap'
import { NotificationService } from './NotificationService'

vi.mock('./NotificationService', async () => {
    const actual = await vi.importActual<typeof import('./NotificationService')>(
        './NotificationService',
    )
    return {
        ...actual,
        NotificationService: {
            getPermissionStatus: vi.fn(),
            requestPermission: vi.fn(),
            storeTokenForSW: vi.fn(),
            subscribeToPush: vi.fn(),
            registerPeriodicSync: vi.fn(),
            loadAndCheck: vi.fn(),
        },
    }
})

const mockedNotificationService = vi.mocked(NotificationService)

const TOKEN = 'test-token'
const FUENF_MINUTEN_MS = 5 * 60 * 1000

describe('notificationBootstrap', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        mockedNotificationService.getPermissionStatus.mockReturnValue('default')
        mockedNotificationService.requestPermission.mockResolvedValue(false)
        mockedNotificationService.storeTokenForSW.mockResolvedValue(undefined)
        mockedNotificationService.subscribeToPush.mockResolvedValue(false)
        mockedNotificationService.registerPeriodicSync.mockResolvedValue(undefined)
        mockedNotificationService.loadAndCheck.mockResolvedValue(undefined)
        localStorage.setItem('zeiterfassung_token', TOKEN)
    })

    afterEach(() => {
        // Kein Test darf ein Intervall in den naechsten ueberleben lassen.
        stoppeBenachrichtigungsIntervall()
        vi.useRealTimers()
        vi.clearAllMocks()
        localStorage.clear()
    })

    describe('starteBenachrichtigungenFallsErlaubt()', () => {
        it('fragt NIE von sich aus nach, wenn der Status "default" (nicht gefragt) ist', async () => {
            mockedNotificationService.getPermissionStatus.mockReturnValue('default')

            const result = await starteBenachrichtigungenFallsErlaubt(TOKEN)

            expect(result).toBe(false)
            expect(mockedNotificationService.requestPermission).not.toHaveBeenCalled()
            expect(mockedNotificationService.storeTokenForSW).not.toHaveBeenCalled()
            expect(mockedNotificationService.subscribeToPush).not.toHaveBeenCalled()
            expect(mockedNotificationService.registerPeriodicSync).not.toHaveBeenCalled()
            expect(mockedNotificationService.loadAndCheck).not.toHaveBeenCalled()
        })

        it('fragt NIE von sich aus nach, wenn der Status "denied" (blockiert) ist', async () => {
            mockedNotificationService.getPermissionStatus.mockReturnValue('denied')

            const result = await starteBenachrichtigungenFallsErlaubt(TOKEN)

            expect(result).toBe(false)
            expect(mockedNotificationService.requestPermission).not.toHaveBeenCalled()
        })

        it('startet bei bereits erteilter Erlaubnis die komplette Kette und liefert true', async () => {
            mockedNotificationService.getPermissionStatus.mockReturnValue('granted')

            const result = await starteBenachrichtigungenFallsErlaubt(TOKEN)

            expect(result).toBe(true)
            expect(mockedNotificationService.requestPermission).not.toHaveBeenCalled()
            expect(mockedNotificationService.storeTokenForSW).toHaveBeenCalledTimes(1)
            expect(mockedNotificationService.storeTokenForSW).toHaveBeenCalledWith(TOKEN)
            expect(mockedNotificationService.subscribeToPush).toHaveBeenCalledTimes(1)
            expect(mockedNotificationService.subscribeToPush).toHaveBeenCalledWith(TOKEN)
            expect(mockedNotificationService.registerPeriodicSync).toHaveBeenCalledTimes(1)
            expect(mockedNotificationService.loadAndCheck).toHaveBeenCalledTimes(1)
            expect(mockedNotificationService.loadAndCheck).toHaveBeenCalledWith(TOKEN)
        })

        it('prueft danach alle 5 Minuten erneut, mit dem dann aktuellen Token aus localStorage', async () => {
            mockedNotificationService.getPermissionStatus.mockReturnValue('granted')
            await starteBenachrichtigungenFallsErlaubt(TOKEN)
            mockedNotificationService.loadAndCheck.mockClear()

            // Token-Wechsel zwischen Start und Intervall-Tick darf nicht ins Leere laufen.
            localStorage.setItem('zeiterfassung_token', 'neuer-token')
            await vi.advanceTimersByTimeAsync(FUENF_MINUTEN_MS)

            expect(mockedNotificationService.loadAndCheck).toHaveBeenCalledTimes(1)
            expect(mockedNotificationService.loadAndCheck).toHaveBeenCalledWith('neuer-token')
        })

        it('pollt nach stoppeBenachrichtigungsIntervall() nicht mehr weiter', async () => {
            mockedNotificationService.getPermissionStatus.mockReturnValue('granted')
            await starteBenachrichtigungenFallsErlaubt(TOKEN)
            mockedNotificationService.loadAndCheck.mockClear()

            stoppeBenachrichtigungsIntervall()
            await vi.advanceTimersByTimeAsync(FUENF_MINUTEN_MS)

            expect(mockedNotificationService.loadAndCheck).not.toHaveBeenCalled()
        })

        it('hinterlaesst nach zweimaligem Start nur ein einziges Intervall', async () => {
            mockedNotificationService.getPermissionStatus.mockReturnValue('granted')
            await starteBenachrichtigungenFallsErlaubt(TOKEN)
            await starteBenachrichtigungenFallsErlaubt(TOKEN)
            mockedNotificationService.loadAndCheck.mockClear()

            await vi.advanceTimersByTimeAsync(FUENF_MINUTEN_MS)

            // Waeren zwei Intervalle aktiv, liefe loadAndCheck zweimal.
            expect(mockedNotificationService.loadAndCheck).toHaveBeenCalledTimes(1)
        })
    })

    describe('frageBenachrichtigungenAn()', () => {
        it('loest den System-Dialog aus und startet die Kette bei Ablehnung nicht', async () => {
            mockedNotificationService.requestPermission.mockResolvedValue(false)

            const result = await frageBenachrichtigungenAn(TOKEN)

            expect(result).toBe(false)
            expect(mockedNotificationService.requestPermission).toHaveBeenCalledTimes(1)
            expect(mockedNotificationService.storeTokenForSW).not.toHaveBeenCalled()
            expect(mockedNotificationService.subscribeToPush).not.toHaveBeenCalled()
            expect(mockedNotificationService.registerPeriodicSync).not.toHaveBeenCalled()
            expect(mockedNotificationService.loadAndCheck).not.toHaveBeenCalled()
        })

        it('startet bei Zustimmung dieselbe Kette wie starteBenachrichtigungenFallsErlaubt', async () => {
            mockedNotificationService.requestPermission.mockResolvedValue(true)

            const result = await frageBenachrichtigungenAn(TOKEN)

            expect(result).toBe(true)
            expect(mockedNotificationService.storeTokenForSW).toHaveBeenCalledWith(TOKEN)
            expect(mockedNotificationService.subscribeToPush).toHaveBeenCalledWith(TOKEN)
            expect(mockedNotificationService.registerPeriodicSync).toHaveBeenCalledTimes(1)
            expect(mockedNotificationService.loadAndCheck).toHaveBeenCalledWith(TOKEN)
        })

        it('hinterlaesst nach Zustimmung ein pollendes Intervall', async () => {
            mockedNotificationService.requestPermission.mockResolvedValue(true)
            await frageBenachrichtigungenAn(TOKEN)
            mockedNotificationService.loadAndCheck.mockClear()

            await vi.advanceTimersByTimeAsync(FUENF_MINUTEN_MS)

            expect(mockedNotificationService.loadAndCheck).toHaveBeenCalledTimes(1)
        })
    })

    describe('stoppeBenachrichtigungsIntervall()', () => {
        it('ist auch ohne laufendes Intervall gefahrlos mehrfach aufrufbar', () => {
            expect(() => {
                stoppeBenachrichtigungsIntervall()
                stoppeBenachrichtigungsIntervall()
            }).not.toThrow()
        })
    })
})
