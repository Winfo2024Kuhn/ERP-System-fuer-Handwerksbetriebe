import { act, cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { ABGLEICH_ANSTOSSEN_EVENT, OfflineService, speicherDauerhaftAnfordern, type SyncResult } from './services/OfflineService'
import { ToastProvider } from './components/ui/toast'

// Die Startseite ersetzt ein Platzhalter, der nur zeigt, was App.tsx weiterreicht.
vi.mock('./pages/DashboardPage', () => ({
    default: ({ syncStatus, abgleichNummer }: { syncStatus?: string; abgleichNummer?: number }) => (
        <div data-testid="dashboard">{`${syncStatus}|${abgleichNummer}`}</div>
    ),
}))
vi.mock('./services/notificationBootstrap', () => ({
    starteBenachrichtigungenFallsErlaubt: vi.fn(),
    stoppeBenachrichtigungsIntervall: vi.fn(),
}))
vi.mock('./services/NotificationService', () => ({
    NotificationService: { loadAndCheck: vi.fn(), confirmValidatedTokenForSW: vi.fn() },
}))
vi.mock('./services/OfflineService', async () => {
    const actual = await vi.importActual<typeof import('./services/OfflineService')>('./services/OfflineService')
    return {
        ...actual,
        speicherDauerhaftAnfordern: vi.fn(async () => true),
        OfflineService: { syncAll: vi.fn(), getPendingCount: vi.fn() },
    }
})

const mocked = vi.mocked(OfflineService)

const ergebnis = (success: boolean, extra: Partial<SyncResult> = {}): SyncResult => ({
    success, pendingSynced: 0, pendingFailed: 0, pendingDiscarded: 0, allPendingCleared: success, startSynced: false, ...extra,
})

/** syncAll, dessen Ende der Test selbst bestimmt. */
function steuerbarerAbgleich() {
    const offen: Array<(r: SyncResult) => void> = []
    mocked.syncAll.mockImplementation(() => new Promise<SyncResult>(resolve => offen.push(resolve)))
    return {
        beenden: async (r: SyncResult) => {
            await act(async () => { offen.shift()?.(r) })
        },
        anzahlOffen: () => offen.length,
    }
}

const anzeige = () => screen.getByTestId('dashboard').textContent

async function starteApp() {
    render(<ToastProvider><MemoryRouter><App /></MemoryRouter></ToastProvider>)
    // Erste Prüfung (by-token) + erster Abgleich beim Start
    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
}

describe('App – ein zentraler Abgleich', () => {
    beforeEach(() => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] })
        localStorage.setItem('zeiterfassung_token', 'tok-test')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
        vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 1, aktiv: true }), { status: 200 })))
        mocked.getPendingCount.mockResolvedValue(0)
    })

    afterEach(() => {
        cleanup()
        vi.useRealTimers()
        vi.unstubAllGlobals()
        vi.clearAllMocks()
        localStorage.clear()
    })

    it('zählt erfolgreiche Abgleiche hoch und bittet um dauerhaften Speicher', async () => {
        mocked.syncAll.mockResolvedValue(ergebnis(true))
        await starteApp()

        expect(anzeige()).toBe('done|1')
        expect(speicherDauerhaftAnfordern).toHaveBeenCalledTimes(1)

        await act(async () => { window.dispatchEvent(new CustomEvent(ABGLEICH_ANSTOSSEN_EVENT)) })
        await act(async () => { await vi.advanceTimersByTimeAsync(0) })
        expect(mocked.syncAll).toHaveBeenCalledTimes(2)
        expect(anzeige()).toBe('done|2')
    })

    // Android: Der Service Worker hat Buchungen im Hintergrund gesendet – die App lädt neu.
    it('gleicht nach einer Meldung des Service Workers ab, ignoriert aber fremde Nachrichten', async () => {
        const worker = new EventTarget()
        Object.defineProperty(navigator, 'serviceWorker', { configurable: true, value: worker })
        try {
            mocked.syncAll.mockResolvedValue(ergebnis(true))
            await starteApp()
            expect(anzeige()).toBe('done|1')

            await act(async () => { worker.dispatchEvent(new MessageEvent('message', { data: { type: 'ETWAS_ANDERES' } })) })
            await act(async () => { await vi.advanceTimersByTimeAsync(0) })
            expect(mocked.syncAll).toHaveBeenCalledTimes(1)

            await act(async () => { worker.dispatchEvent(new MessageEvent('message', { data: { type: 'BUCHUNGEN_UEBERTRAGEN' } })) })
            await act(async () => { await vi.advanceTimersByTimeAsync(0) })
            expect(mocked.syncAll).toHaveBeenCalledTimes(2)
            expect(anzeige()).toBe('done|2')
        } finally {
            Reflect.deleteProperty(navigator, 'serviceWorker')
        }
    })

    it('gleichzeitige Auslöser teilen sich einen Abgleich und zählen nur einmal', async () => {
        const abgleich = steuerbarerAbgleich()
        await starteApp()
        expect(anzeige()).toBe('syncing|0')

        await act(async () => {
            window.dispatchEvent(new Event('online'))
            document.dispatchEvent(new Event('visibilitychange'))
        })
        mocked.getPendingCount.mockResolvedValue(2)
        await act(async () => { await vi.advanceTimersByTimeAsync(30_000) }) // Heartbeat
        expect(mocked.syncAll).toHaveBeenCalledTimes(1)

        await abgleich.beenden(ergebnis(true))
        expect(anzeige()).toBe('done|1')
    })

    it('ein Anstoß während eines laufenden Abgleichs gleicht danach noch einmal ab', async () => {
        const abgleich = steuerbarerAbgleich()
        await starteApp()

        await act(async () => { window.dispatchEvent(new CustomEvent(ABGLEICH_ANSTOSSEN_EVENT)) })
        expect(mocked.syncAll).toHaveBeenCalledTimes(1)

        await abgleich.beenden(ergebnis(true))
        await act(async () => { await vi.advanceTimersByTimeAsync(0) })
        expect(mocked.syncAll).toHaveBeenCalledTimes(2)
        await abgleich.beenden(ergebnis(true))
        expect(anzeige()).toBe('done|2')
    })

    it('versucht es nach einem Fehler per Heartbeat erneut, auch ohne wartende Buchungen', async () => {
        mocked.syncAll.mockResolvedValueOnce(ergebnis(false)).mockResolvedValue(ergebnis(true))
        await starteApp()
        expect(anzeige()).toBe('error|0')

        await act(async () => { await vi.advanceTimersByTimeAsync(30_000) })

        expect(mocked.syncAll).toHaveBeenCalledTimes(2)
        expect(anzeige()).toBe('done|1')

        // Danach: kein Fehler, nichts wartend → der Heartbeat bleibt still
        await act(async () => { await vi.advanceTimersByTimeAsync(30_000) })
        expect(mocked.syncAll).toHaveBeenCalledTimes(2)
    })

    it('fasst nach dem online-Event nach 3 s und 10 s nach, solange der Server nicht erreichbar ist', async () => {
        mocked.syncAll.mockResolvedValue(ergebnis(true))
        await starteApp()
        mocked.syncAll.mockReset()
        mocked.syncAll
            .mockResolvedValueOnce(ergebnis(false))
            .mockResolvedValueOnce(ergebnis(false))
            .mockResolvedValue(ergebnis(true))

        await act(async () => { window.dispatchEvent(new Event('online')) })
        await act(async () => { await vi.advanceTimersByTimeAsync(0) })
        expect(mocked.syncAll).toHaveBeenCalledTimes(1)
        expect(anzeige()).toBe('error|1')

        await act(async () => { await vi.advanceTimersByTimeAsync(3_000) })
        expect(mocked.syncAll).toHaveBeenCalledTimes(2)

        await act(async () => { await vi.advanceTimersByTimeAsync(7_000) })
        expect(mocked.syncAll).toHaveBeenCalledTimes(3)
        expect(anzeige()).toBe('done|2')
    })

    it('merkt sich einen gesyncten Start für den Schutz der lokalen Buchung', async () => {
        mocked.syncAll.mockResolvedValue(ergebnis(true, { startSynced: true }))
        await starteApp()
        expect(localStorage.getItem('zeiterfassung_start_synced_at')).not.toBeNull()
    })
})
