import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardPage from './DashboardPage'
import { OfflineService, sendeBuchung, type SendeBuchungErgebnis } from '../services/OfflineService'
import { ToastProvider } from '../components/ui/toast'

// actual-Spread ist notwendig, weil DashboardPage createOperationId und
// startZeitAusErgebnis direkt aus dem Modul importiert – die müssen ihre
// echten Implementierungen behalten. sendeBuchung (Senden, Einreihen,
// Reparatur-Liste) ist in OfflineService.test.ts geprüft; hier zählt nur,
// wie das Dashboard auf das Ergebnis reagiert.
vi.mock('../services/OfflineService', async () => {
    const actual = await vi.importActual<typeof import('../services/OfflineService')>(
        '../services/OfflineService',
    )
    return {
        ...actual,
        sendeBuchung: vi.fn(),
        OfflineService: {
            getFailedEntries: vi.fn(),
            removeFailedEntry: vi.fn(),
            getHeuteGearbeitet: vi.fn(),
            getPendingCount: vi.fn(),
            getUnsyncedStopMinutes: vi.fn(),
            getProjekte: vi.fn(),
            getKategorien: vi.fn(),
            getArbeitsgaenge: vi.fn(),
            addPendingEntryWithOperationId: vi.fn(),
        },
    }
})

const mockedOfflineService = vi.mocked(OfflineService)
const mockedSendeBuchung = vi.mocked(sendeBuchung)

/** Antwortet je Buchungsart (start/stop/pause) mit dem vorgegebenen Ergebnis. */
const sendeBuchungAntwortet = (ergebnisse: Partial<Record<'start' | 'stop' | 'pause', SendeBuchungErgebnis>>) =>
    mockedSendeBuchung.mockImplementation(async (typ) => ergebnisse[typ] ?? { art: 'ok', daten: {} })

const buchungsAufrufe = (typ: 'start' | 'stop' | 'pause') =>
    mockedSendeBuchung.mock.calls.filter(([t]) => t === typ)

const configuredZeitkonto = { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }

const antwortMitZeitkontoStatus = (input: RequestInfo | URL, fallback: unknown = {}) => {
    const url = typeof input === 'string' ? input : input.toString()
    return new Response(JSON.stringify(url.includes('/buchungszeitfenster/') ? configuredZeitkonto : fallback), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
    })
}

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

const dashboardMitAbgleich = (abgleichNummer?: number) => (
    <ToastProvider>
        <MemoryRouter>
            <DashboardPage mitarbeiter={{ id: 1, vorname: 'Max', nachname: 'Mustermann' }} onLogout={() => undefined} abgleichNummer={abgleichNummer} />
        </MemoryRouter>
    </ToastProvider>
)

const renderDashboard = (abgleichNummer?: number) => render(dashboardMitAbgleich(abgleichNummer))

const buildActiveWorkSession = () => ({
    projektId: 77,
    projektName: 'Bauvorhaben Mustermann',
    kundenName: 'Max Mustermann',
    auftragsnummer: 'A-1',
    arbeitsgangId: 11,
    arbeitsgangName: 'Montage',
    produktkategorieId: null,
    produktkategorieName: null,
    startTime: new Date(Date.now() - 30 * 60_000).toISOString(),
    typ: null as const,
})

describe('DashboardPage – Pause-Button', () => {
    beforeEach(() => {
        vi.stubGlobal('localStorage', createMemoryStorage())
        localStorage.setItem('zeiterfassung_token', 'tok-test')
        localStorage.setItem(
            'zeiterfassung_active_session',
            JSON.stringify(buildActiveWorkSession()),
        )

        mockedOfflineService.getFailedEntries.mockResolvedValue([])
        mockedOfflineService.getHeuteGearbeitet.mockResolvedValue({
            stunden: 0,
            minuten: 0,
            fromCache: false,
        })
        // pendingCount=1 ist bewusst gewählt: loadActiveSession überspringt
        // den GET /aktiv-Server-Sync bei pendingCount>0 und behält die lokale
        // Session. Ohne dieses Skip würde die {}-Antwort des Fallback-Mocks
        // (s. einzelne Tests) die Session direkt wieder leeren – siehe
        // DashboardPage.tsx Zeile 212-216.
        mockedOfflineService.getPendingCount.mockResolvedValue(1)
        mockedOfflineService.getUnsyncedStopMinutes.mockResolvedValue(0)
        mockedOfflineService.addPendingEntryWithOperationId.mockResolvedValue(undefined)
    })

    afterEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        vi.unstubAllGlobals()
    })

    it('Pause-Tap zeigt sofort die Pause-Session, auch wenn der Server ablehnt (Reparatur-Liste)', async () => {
        // REGRESSION: zuvor wurde ein Server-4xx in handlePause stumm
        // in die Konsole geloggt – Handwerker sah nichts und konnte die
        // Pause nicht anstechen. Jetzt: optimistisches UI + Reparatur-Liste + Toast.
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
        sendeBuchungAntwortet({ pause: { art: 'abgelehnt', status: 400, meldung: 'Bereits laufende Pausenbuchung' } })

        const user = userEvent.setup()
        renderDashboard()

        const pauseButton = await screen.findByRole('button', { name: /pause/i })
        await user.click(pauseButton)

        // 1) Pause geht über sendeBuchung – eine Ablehnung landet in der Reparatur-Liste
        await waitFor(() => {
            expect(mockedSendeBuchung).toHaveBeenCalledWith(
                'pause',
                expect.objectContaining({ token: 'tok-test' }),
                expect.objectContaining({
                    ablehnungInReparaturListe: true,
                    durationMinutes: expect.any(Number), // beendete Arbeitszeit
                    operationId: expect.any(String),
                    originalTime: expect.any(String),
                }),
            )
        })

        // 2) UI zeigt jetzt die Pause-Session – das ist das Symptom, das vorher fehlte.
        //    Präziser Match auf den ☕-Badge, damit der Test nicht zufällig
        //    durch das Wort "Pause" in anderen Strings grün wird.
        expect(await screen.findByText('☕ Pause')).toBeInTheDocument()
        expect(await screen.findByText('Pause nicht übernommen: Bereits laufende Pausenbuchung')).toBeInTheDocument()

        // 3) localStorage hält die Pause-Session (Reload-fest)
        const stored = JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}')
        expect(stored.typ).toBe('PAUSE')

        // 4) Cooldown-Flag gesetzt, damit ein nachfolgendes loadActiveSession()
        //    die optimistische Pause nicht durch eine vom Server weiterhin
        //    aktive Arbeitsbuchung überschreibt (Reviewer-Finding).
        expect(localStorage.getItem('zeiterfassung_start_synced_at')).not.toBeNull()
        // 5) Reparatur-Liste wird neu gelesen, damit das Banner erscheint
        expect(mockedOfflineService.getFailedEntries.mock.calls.length).toBeGreaterThan(1)
    })

    it('Pause-Tap zeigt die Pause-Session mit Serverzeit (Happy Path)', async () => {
        const serverStartZeit = new Date().toISOString()
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
        sendeBuchungAntwortet({ pause: { art: 'ok', daten: { id: 42, startZeit: serverStartZeit, typ: 'PAUSE', status: 'gestartet' } } })

        const user = userEvent.setup()
        renderDashboard()

        const pauseButton = await screen.findByRole('button', { name: /pause/i })
        await user.click(pauseButton)

        await waitFor(() => {
            const stored = JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}')
            expect(stored.typ).toBe('PAUSE')
            expect(stored.startTime).toBe(serverStartZeit)
        })

        // UI rendert die Pause-Session
        expect(await screen.findByText('☕ Pause')).toBeInTheDocument()
        // Im Happy-Path kein Cooldown und kein Fehler
        expect(localStorage.getItem('zeiterfassung_start_synced_at')).toBeNull()
        expect(screen.queryByRole('alert')).toBeNull()
    })

    it('Pause wartend: Pause-Session mit eigenem Zeitpunkt, kein Fehler-Toast', async () => {
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
        sendeBuchungAntwortet({ pause: { art: 'eingereiht' } })

        const user = userEvent.setup()
        renderDashboard()
        await user.click(await screen.findByRole('button', { name: /pause/i }))

        expect(await screen.findByText('☕ Pause')).toBeInTheDocument()
        const [, , optionen] = buchungsAufrufe('pause')[0]
        const stored = JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}')
        expect(stored.startTime).toBe(optionen.originalTime)
        expect(localStorage.getItem('zeiterfassung_start_synced_at')).not.toBeNull()
        expect(screen.queryByRole('alert')).toBeNull()
    })

    it('Pause-Doppeltap erzeugt nur eine Pause-Operation', async () => {
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
        mockedSendeBuchung.mockImplementation(async () => {
            await new Promise(resolve => setTimeout(resolve, 25))
            return { art: 'ok', daten: { id: 42, startZeit: new Date().toISOString(), typ: 'PAUSE', status: 'gestartet' } }
        })

        renderDashboard()

        const pauseButton = await screen.findByRole('button', { name: /pause/i })
        pauseButton.click()
        pauseButton.click()

        await screen.findByText('☕ Pause')

        expect(buchungsAufrufe('pause')).toHaveLength(1)
    })
})

describe('DashboardPage – Zeitkonto-Status', () => {
    beforeEach(() => {
        vi.stubGlobal('localStorage', createMemoryStorage())
        localStorage.setItem('zeiterfassung_token', 'tok-test')
        mockedOfflineService.getFailedEntries.mockResolvedValue([])
        mockedOfflineService.getHeuteGearbeitet.mockResolvedValue({ stunden: 0, minuten: 0, fromCache: false })
        mockedOfflineService.getPendingCount.mockResolvedValue(0)
        mockedOfflineService.getUnsyncedStopMinutes.mockResolvedValue(0)
    })

    afterEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        vi.unstubAllGlobals()
    })

    it('blendet Zeitkonto-Navigation bei ausgeschalteter Zeiterfassung aus und lässt Projekte erreichbar', async () => {
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
            const url = typeof input === 'string' ? input : input.toString()
            if (url.includes('/buchungszeitfenster/')) {
                return new Response(JSON.stringify({ fuehrtZeitkonto: false, eingerichtet: false, hinweis: 'Die Zeiterfassung ist ausgeschaltet.' }))
            }
            return new Response('{}', { status: 200 })
        }))

        renderDashboard()

        expect(await screen.findByText('Zeiterfassung derzeit nicht verfügbar')).toBeInTheDocument()
        expect(screen.getByText('Projekte')).toBeInTheDocument()
        expect(screen.queryByText('Saldenauswertung')).not.toBeInTheDocument()
        expect(screen.queryByText('Zeit erfassen')).not.toBeInTheDocument()
    })
})

describe('DashboardPage – loadActiveSession Frisch-Guard', () => {
    // Stellt ein fetch-Mock bereit, bei dem GET /aktiv "keine aktive Buchung"
    // (leeres Objekt, kein .id) liefert. Genau diese verzögerte Server-Antwort
    // hat die frisch angestochene Buchung früher gelöscht.
    const stubFetchAktivLeer = () => {
        const fetchMock = vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input))
        vi.stubGlobal('fetch', fetchMock)
        return fetchMock
    }

    beforeEach(() => {
        vi.stubGlobal('localStorage', createMemoryStorage())
        localStorage.setItem('zeiterfassung_token', 'tok-test')

        mockedOfflineService.getFailedEntries.mockResolvedValue([])
        mockedOfflineService.getHeuteGearbeitet.mockResolvedValue({
            stunden: 0,
            minuten: 0,
            fromCache: false,
        })
        // pendingCount=0: voll synchronisiert -> loadActiveSession führt den
        // Server-Abgleich (GET /aktiv) wirklich aus. Das ist der Online-Start-Fall.
        mockedOfflineService.getPendingCount.mockResolvedValue(0)
        mockedOfflineService.getUnsyncedStopMinutes.mockResolvedValue(0)
        mockedOfflineService.addPendingEntryWithOperationId.mockResolvedValue(undefined)
    })

    afterEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        vi.unstubAllGlobals()
    })

    it('behält eine frisch angestochene Buchung, wenn GET /aktiv (verzögert) noch aktiv:false liefert', async () => {
        // REGRESSION: Nach dem Online-Anstechen verschwand die "Aktive Buchung"-
        // Karte, weil der Server die frisch committete Buchung noch nicht sah und
        // GET /aktiv aktiv:false lieferte -> lokale Session wurde gelöscht.
        const frischeSession = { ...buildActiveWorkSession(), startTime: new Date().toISOString() }
        localStorage.setItem('zeiterfassung_active_session', JSON.stringify(frischeSession))

        const fetchMock = stubFetchAktivLeer()
        renderDashboard()

        // Warten bis der Server-Abgleich tatsächlich gelaufen ist...
        await waitFor(() => {
            const aktivCall = fetchMock.mock.calls.some(([input]) => {
                const url = typeof input === 'string' ? input : input?.toString() ?? ''
                return url.includes('/api/zeiterfassung/aktiv/')
            })
            expect(aktivCall).toBe(true)
        })

        // ...die Karte bleibt sichtbar und die Session bleibt in localStorage.
        expect(await screen.findByText('Bauvorhaben Mustermann')).toBeInTheDocument()
        expect(localStorage.getItem('zeiterfassung_active_session')).not.toBeNull()
    })

    it('räumt eine alte (nicht frische) Session weiterhin auf, wenn der Server aktiv:false liefert', async () => {
        // NO-REGRESSION: Wurde die Buchung woanders (PC) beendet, muss eine alte
        // lokale Session beim Server-Abgleich verschwinden. Der Frisch-Guard darf
        // das nicht verhindern.
        const alteSession = {
            ...buildActiveWorkSession(),
            startTime: new Date(Date.now() - 60 * 60_000).toISOString(), // 60 Min alt
        }
        localStorage.setItem('zeiterfassung_active_session', JSON.stringify(alteSession))

        stubFetchAktivLeer()
        renderDashboard()

        await waitFor(() => {
            expect(localStorage.getItem('zeiterfassung_active_session')).toBeNull()
        })
        expect(screen.queryByText('Bauvorhaben Mustermann')).not.toBeInTheDocument()
    })

    it('behält eine NEUE Buchung, die nach einem Stop angestochen wurde (Pause -> Arbeit fortsetzen)', async () => {
        // REGRESSION: Pause -> "Arbeit fortsetzen" setzt zeiterfassung_stopped_at
        // (Stop-Cooldown). Stach der Nutzer danach ein Projekt an, löschte der
        // Stop-Cooldown die frische Buchung wieder - sie erschien erst nach 15s.
        const stopVor2s = Date.now() - 2000
        localStorage.setItem('zeiterfassung_stopped_at', stopVor2s.toString())
        const neueSession = {
            ...buildActiveWorkSession(),
            startTime: new Date(stopVor2s + 1500).toISOString(), // NACH dem Stop gestartet
        }
        localStorage.setItem('zeiterfassung_active_session', JSON.stringify(neueSession))

        stubFetchAktivLeer()
        renderDashboard()

        // Karte bleibt sichtbar, Session bleibt erhalten, Cooldown wird beendet.
        expect(await screen.findByText('Bauvorhaben Mustermann')).toBeInTheDocument()
        await waitFor(() => {
            expect(localStorage.getItem('zeiterfassung_stopped_at')).toBeNull()
        })
        expect(localStorage.getItem('zeiterfassung_active_session')).not.toBeNull()
    })

    it('räumt eine vor dem Stop gestartete Session im Cooldown weiterhin auf', async () => {
        // NO-REGRESSION: Eine Session, die VOR dem Stop lief (z.B. die gerade
        // beendete Pause), muss vom Stop-Cooldown weiterhin entfernt werden.
        const stopVor2s = Date.now() - 2000
        localStorage.setItem('zeiterfassung_stopped_at', stopVor2s.toString())
        const alteSession = {
            ...buildActiveWorkSession(),
            startTime: new Date(stopVor2s - 60_000).toISOString(), // VOR dem Stop gestartet
        }
        localStorage.setItem('zeiterfassung_active_session', JSON.stringify(alteSession))

        stubFetchAktivLeer()
        renderDashboard()

        await waitFor(() => {
            expect(localStorage.getItem('zeiterfassung_active_session')).toBeNull()
        })
        expect(screen.queryByText('Bauvorhaben Mustermann')).not.toBeInTheDocument()
    })
})

describe('DashboardPage – Feierabend-Rückmeldung', () => {
    beforeEach(() => {
        vi.stubGlobal('localStorage', createMemoryStorage())
        localStorage.setItem('zeiterfassung_token', 'tok-test')
        localStorage.setItem(
            'zeiterfassung_active_session',
            JSON.stringify(buildActiveWorkSession()),
        )

        mockedOfflineService.getFailedEntries.mockResolvedValue([])
        mockedOfflineService.getHeuteGearbeitet.mockResolvedValue({
            stunden: 0,
            minuten: 0,
            fromCache: false,
        })
        // pendingCount=1: loadActiveSession überspringt den Server-Abgleich und
        // behält die lokale Session – sonst räumt die {}-Antwort des Fallback-
        // Mocks die Buchung weg, bevor der Feierabend-Button überhaupt da ist.
        mockedOfflineService.getPendingCount.mockResolvedValue(1)
        mockedOfflineService.getUnsyncedStopMinutes.mockResolvedValue(0)
        mockedOfflineService.addPendingEntryWithOperationId.mockResolvedValue(undefined)
    })

    afterEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        vi.unstubAllGlobals()
    })

    it('sagt dem Handwerker, wenn der Feierabend gespeichert, aber noch nicht übertragen ist', async () => {
        // REGRESSION 29.07.2026: Zwei Mitarbeiter haben Feierabend gestempelt,
        // hatten aber kein Netz. Der Stop landete stumm in der Offline-Queue, die
        // Karte verschwand – für beide sah es aus wie erledigt. Am Server lief die
        // Buchung weiter, bis der Auto-Stop sie um 20:00 schloss und mehrere
        // Stunden Arbeitszeit erfand. Ohne Rückmeldung merkt das niemand.
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
        sendeBuchungAntwortet({ stop: { art: 'eingereiht' } })

        const user = userEvent.setup()
        renderDashboard()

        const gehenButton = await screen.findByRole('button', { name: /feierabend/i })
        await user.click(gehenButton)

        // Feierabend geht über sendeBuchung, mit Arbeitsdauer für "heute gearbeitet" …
        await waitFor(() => {
            expect(mockedSendeBuchung).toHaveBeenCalledWith(
                'stop',
                expect.objectContaining({ token: 'tok-test' }),
                expect.objectContaining({ ablehnungInReparaturListe: true, durationMinutes: expect.any(Number) }),
            )
        })

        // … und der Handwerker erfährt davon, statt es nur in der Konsole zu sehen.
        expect(await screen.findByText(/Feierabend um \d\d:\d\d gespeichert/)).toBeInTheDocument()
        expect(screen.getByText(/automatisch ans Büro übertragen, sobald die Verbindung steht/i)).toBeInTheDocument()
        expect(localStorage.getItem('zeiterfassung_active_session')).toBeNull()
        expect(localStorage.getItem('zeiterfassung_stopped_at')).not.toBeNull()
    })

    it('zeigt keinen Hinweis, wenn der Feierabend sauber beim Server ankommt', async () => {
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
        sendeBuchungAntwortet({ stop: { art: 'ok', daten: { id: 1, stunden: 7.5, status: 'gestoppt' } } })

        const user = userEvent.setup()
        renderDashboard()

        const gehenButton = await screen.findByRole('button', { name: /feierabend/i })
        await user.click(gehenButton)

        await waitFor(() => {
            expect(localStorage.getItem('zeiterfassung_active_session')).toBeNull()
        })
        expect(screen.queryByText(/automatisch ans Büro übertragen/i)).not.toBeInTheDocument()
        expect(screen.queryByRole('alert')).toBeNull()
    })

    it('meldet einen abgelehnten Feierabend per Toast und zeigt die Reparatur-Liste', async () => {
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
        sendeBuchungAntwortet({ stop: { art: 'abgelehnt', status: 400, meldung: 'Keine aktive Buchung gefunden' } })
        mockedOfflineService.getFailedEntries
            .mockResolvedValueOnce([])
            .mockResolvedValue([{
                id: 'op-1', type: 'stop', data: { token: 'tok-test' }, timestamp: new Date().toISOString(),
                originalTime: new Date().toISOString(), failedAt: new Date().toISOString(),
                httpStatus: 400, serverError: 'Keine aktive Buchung gefunden', attemptCount: 1,
            }])

        const user = userEvent.setup()
        renderDashboard()
        await user.click(await screen.findByRole('button', { name: /feierabend/i }))

        expect(await screen.findByText('Feierabend nicht übernommen: Keine aktive Buchung gefunden')).toBeInTheDocument()
        expect(await screen.findByText('1 Buchung konnte nicht gespeichert werden')).toBeInTheDocument()
        expect(localStorage.getItem('zeiterfassung_active_session')).toBeNull()
    })

    it('behält die Buchung und meldet sich, wenn der Feierabend nicht einmal lokal gespeichert werden kann', async () => {
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
        mockedSendeBuchung.mockRejectedValue(new Error('IndexedDB nicht verfügbar'))

        const user = userEvent.setup()
        renderDashboard()
        await user.click(await screen.findByRole('button', { name: /feierabend/i }))

        expect(await screen.findByText(/Feierabend konnte auf dem Handy nicht gespeichert werden/)).toBeInTheDocument()
        expect(localStorage.getItem('zeiterfassung_active_session')).not.toBeNull()
    })

    it('Pause beenden geht über sendeBuchung und öffnet danach die Zeiterfassung', async () => {
        localStorage.setItem('zeiterfassung_active_session', JSON.stringify({ ...buildActiveWorkSession(), typ: 'PAUSE' }))
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
        sendeBuchungAntwortet({ stop: { art: 'eingereiht' } })

        const user = userEvent.setup()
        render(
            <ToastProvider>
                <MemoryRouter initialEntries={['/']}>
                    <Routes>
                        <Route path="/" element={<DashboardPage mitarbeiter={{ id: 1, vorname: 'Max', nachname: 'Mustermann' }} onLogout={() => undefined} />} />
                        <Route path="/zeiterfassung" element={<div>Zeiterfassung-Seite</div>} />
                    </Routes>
                </MemoryRouter>
            </ToastProvider>,
        )
        await user.click(await screen.findByRole('button', { name: /arbeit fortsetzen|pause beenden|weiter/i }))

        expect(await screen.findByText('Zeiterfassung-Seite')).toBeInTheDocument()
        expect(mockedSendeBuchung).toHaveBeenCalledWith('stop', { token: 'tok-test' }, expect.objectContaining({ ablehnungInReparaturListe: true }))
        expect(localStorage.getItem('zeiterfassung_active_session')).toBeNull()
    })
})

describe('DashboardPage – Langzeitkrankmeldung-Karte', () => {
    beforeEach(() => {
        vi.stubGlobal('localStorage', createMemoryStorage())
        localStorage.setItem('zeiterfassung_token', 'tok-test')

        mockedOfflineService.getFailedEntries.mockResolvedValue([])
        mockedOfflineService.getHeuteGearbeitet.mockResolvedValue({
            stunden: 0,
            minuten: 0,
            fromCache: false,
        })
        // pendingCount=1: loadActiveSession überspringt den Server-Abgleich (kein
        // aktives Arbeits-Session-Setup nötig für diese Tests).
        mockedOfflineService.getPendingCount.mockResolvedValue(1)
        mockedOfflineService.getUnsyncedStopMinutes.mockResolvedValue(0)
        mockedOfflineService.addPendingEntryWithOperationId.mockResolvedValue(undefined)
    })

    afterEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        vi.unstubAllGlobals()
    })

    it('zeigt die Karte mit Stundentext, solange eine Wiedereingliederung läuft', async () => {
        const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
            const url = typeof input === 'string' ? input : input.toString()
            if (url.includes('/api/zeiterfassung/langzeitkrankmeldung/')) {
                return new Response(
                    JSON.stringify({
                        phase: 'WIEDEREINGLIEDERUNG',
                        phaseLabel: 'Wiedereingliederung',
                        heuteGeplanteStunden: 4.0,
                        seit: '2026-03-01',
                        bisDatum: '2026-04-30',
                    }),
                    { status: 200, headers: { 'Content-Type': 'application/json' } },
                )
            }
            return antwortMitZeitkontoStatus(input)
        })
        vi.stubGlobal('fetch', fetchMock)

        renderDashboard()

        expect(await screen.findByText('Wiedereingliederung — heute 4 Stunden geplant')).toBeInTheDocument()
        // DSGVO: kein Name, keine Notiz auf der Karte - nur die fünf erlaubten Felder.
        expect(screen.queryByText('Max Mustermann')).not.toBeInTheDocument()
    })

    it('zeigt keine Karte, wenn der Endpunkt {} liefert (Normalfall)', async () => {
        const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
            const url = typeof input === 'string' ? input : input.toString()
            if (url.includes('/api/zeiterfassung/langzeitkrankmeldung/')) {
                return new Response('{}', { status: 200 })
            }
            return antwortMitZeitkontoStatus(input)
        })
        vi.stubGlobal('fetch', fetchMock)

        renderDashboard()

        // Dashboard ist fertig geladen (Start-Button sichtbar) …
        await screen.findByText('Zeit erfassen')
        // … aber keine Krankmeldungs-Karte, kein Stethoscope-Hinweis.
        expect(screen.queryByText(/Wiedereingliederung/i)).not.toBeInTheDocument()
        expect(screen.queryByText(/Lohnfortzahlung/i)).not.toBeInTheDocument()
        expect(screen.queryByText(/Krankengeld/i)).not.toBeInTheDocument()
    })

    it('bleibt vollständig nutzbar, wenn die Langzeitkrankmeldung-Abfrage fehlschlägt', async () => {
        // REGRESSION-Schutz: Der neue Endpunkt ist ein zusätzlicher Fetch neben
        // dem bestehenden Dashboard-Ablauf. Schlägt er fehl, darf das Dashboard
        // nicht kaputtgehen (kein weißer Screen) - die Karte bleibt einfach weg.
        const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
            const url = typeof input === 'string' ? input : input.toString()
            if (url.includes('/api/zeiterfassung/langzeitkrankmeldung/')) {
                throw new TypeError('Failed to fetch')
            }
            return antwortMitZeitkontoStatus(input)
        })
        vi.stubGlobal('fetch', fetchMock)

        renderDashboard()

        expect(await screen.findByText('Zeit erfassen')).toBeInTheDocument()
        expect(screen.queryByText(/Wiedereingliederung/i)).not.toBeInTheDocument()
    })

    // Befund 1 (Design-Review): teal kommt im Design-System nicht vor. Die
    // Karte folgt jetzt dem Muster der Nachbarkarten (Kalender, Abwesenheit
    // beantragen, Saldenauswertung): weiße Karte mit getönter Icon-Kachel,
    // Farbe indigo (--info-Rolle) statt teal.
    it('nutzt die Nachbarkarten-Optik (weiße Karte, indigo Icon-Kachel) statt teal', async () => {
        const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
            const url = typeof input === 'string' ? input : input.toString()
            if (url.includes('/api/zeiterfassung/langzeitkrankmeldung/')) {
                return new Response(
                    JSON.stringify({
                        phase: 'WIEDEREINGLIEDERUNG',
                        phaseLabel: 'Wiedereingliederung',
                        heuteGeplanteStunden: 4.0,
                        seit: '2026-03-01',
                        bisDatum: '2026-04-30',
                    }),
                    { status: 200, headers: { 'Content-Type': 'application/json' } },
                )
            }
            return antwortMitZeitkontoStatus(input)
        })
        vi.stubGlobal('fetch', fetchMock)

        renderDashboard()

        const text = await screen.findByText('Wiedereingliederung — heute 4 Stunden geplant')
        const card = text.closest('div.bg-white') as HTMLElement | null
        expect(card).not.toBeNull()
        expect(card!.className).toContain('border-slate-200')
        expect(card!.className).not.toMatch(/teal/)
        expect(card!.querySelector('.bg-indigo-50')).not.toBeNull()
        expect(card!.innerHTML).not.toMatch(/teal/)
    })
})

describe('DashboardPage – Zahnrad zu den Einstellungen', () => {
    it('führt links neben dem Online-Abzeichen in die Einstellungen', async () => {
        const user = userEvent.setup()
        render(
            <ToastProvider>
                <MemoryRouter initialEntries={['/']}>
                    <Routes>
                        <Route
                            path="/"
                            element={<DashboardPage mitarbeiter={{ id: 1, vorname: 'Max', nachname: 'Mustermann' }} onLogout={() => undefined} />}
                        />
                        <Route path="/einstellungen" element={<div>Einstellungs-Seite</div>} />
                    </Routes>
                </MemoryRouter>
            </ToastProvider>,
        )

        const zahnrad = await screen.findByRole('button', { name: 'Einstellungen' })
        // Die Nachrichten-Glocke sitzt direkt links neben dem Zahnrad.
        const glocke = screen.getByRole('button', { name: /^Nachrichten/ })
        expect(glocke.nextElementSibling).toBe(zahnrad)
        await user.click(zahnrad)

        expect(await screen.findByText('Einstellungs-Seite')).toBeInTheDocument()
    })

    it('trägt ein aria-label, weil es ein reiner Icon-Knopf ist', async () => {
        renderDashboard()

        const zahnrad = await screen.findByRole('button', { name: 'Einstellungen' })
        expect(zahnrad).toHaveAttribute('aria-label', 'Einstellungen')
    })
})

describe('DashboardPage – Tätigkeit wählen mit Projektwechsel', () => {
    const projekte = [
        { id: 77, name: 'Bauvorhaben Mustermann', projektNummer: 'A-1', kundenName: 'Max Mustermann' },
        { id: 88, name: 'Carport Musterweg', projektNummer: 'A-2', kundenName: 'Erika Musterfrau' },
    ]

    beforeEach(() => {
        vi.stubGlobal('localStorage', createMemoryStorage())
        localStorage.setItem('zeiterfassung_token', 'tok-test')
        localStorage.setItem(
            'zeiterfassung_active_session',
            JSON.stringify({ ...buildActiveWorkSession(), produktkategorieId: 5, produktkategorieName: 'Geländer' }),
        )

        mockedOfflineService.getFailedEntries.mockResolvedValue([])
        mockedOfflineService.getHeuteGearbeitet.mockResolvedValue({ stunden: 0, minuten: 0, fromCache: false })
        mockedOfflineService.getPendingCount.mockResolvedValue(1)
        mockedOfflineService.getUnsyncedStopMinutes.mockResolvedValue(0)
        mockedOfflineService.getProjekte.mockResolvedValue(projekte)
        mockedOfflineService.getArbeitsgaenge.mockResolvedValue([
            { id: 11, beschreibung: 'Montage' },
            { id: 12, beschreibung: 'Schweißen' },
        ])
        mockedOfflineService.addPendingEntryWithOperationId.mockResolvedValue(undefined)
    })

    afterEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        vi.unstubAllGlobals()
    })

    const wechsleProjektUndTaetigkeit = async () => {
        const user = userEvent.setup()
        renderDashboard()
        await user.click(await screen.findByRole('button', { name: /tätigkeit/i }))
        await user.click(await screen.findByRole('button', { name: 'Ändern' }))
        await user.click(await screen.findByRole('button', { name: /Carport Musterweg/ }))
        await user.click(await screen.findByRole('button', { name: 'Schweißen' }))
    }

    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
    })

    it('startet die neue Buchung auf dem über „Ändern“ gewählten Projekt', async () => {
        const serverStart = new Date().toISOString()
        sendeBuchungAntwortet({ stop: { art: 'ok', daten: { status: 'gestoppt' } }, start: { art: 'ok', daten: { id: 999, startZeit: serverStart } } })

        await wechsleProjektUndTaetigkeit()

        await waitFor(() => expect(buchungsAufrufe('start')).toHaveLength(1))
        const [, daten] = buchungsAufrufe('start')[0]
        expect(daten.projektId).toBe(88)
        expect(daten.arbeitsgangId).toBe(12)
        // Kategorien hängen am Projekt – die alte darf nicht mitwandern
        expect(daten.produktkategorieId).toBeNull()
        // Erst beenden, dann starten
        expect(mockedSendeBuchung.mock.calls.map(([typ]) => typ)).toEqual(['stop', 'start'])

        await waitFor(() => {
            const stored = JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}')
            expect(stored.projektId).toBe(88)
            expect(stored.projektName).toBe('Carport Musterweg')
            expect(stored.kundenName).toBe('Erika Musterfrau')
            expect(stored.auftragsnummer).toBe('A-2')
            expect(stored.produktkategorieId).toBeNull()
            expect(stored.startTime).toBe(serverStart)
        })
    })

    it('reiht offline die neue Buchung ebenfalls auf dem gewählten Projekt ein', async () => {
        sendeBuchungAntwortet({ stop: { art: 'eingereiht' }, start: { art: 'eingereiht' } })

        await wechsleProjektUndTaetigkeit()

        await waitFor(() => expect(buchungsAufrufe('start')).toHaveLength(1))
        expect(buchungsAufrufe('start')[0][1]).toMatchObject({ projektId: 88, arbeitsgangId: 12, produktkategorieId: null })
        // Die Dauer der beendeten Arbeit wandert mit dem Stop in die Warteschlange
        expect(buchungsAufrufe('stop')[0][2]).toMatchObject({ durationMinutes: expect.any(Number), ablehnungInReparaturListe: false })
        await waitFor(() => {
            const stored = JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}')
            expect(stored.projektId).toBe(88)
            expect(stored.projektName).toBe('Carport Musterweg')
            expect(String(stored.id)).toMatch(/^offline-/)
        })
    })

    it('startet trotzdem, wenn am Server keine Buchung mehr lief (Stop abgelehnt)', async () => {
        sendeBuchungAntwortet({
            stop: { art: 'abgelehnt', status: 400, meldung: 'Keine aktive Buchung gefunden' },
            start: { art: 'ok', daten: { id: 1000 } },
        })

        await wechsleProjektUndTaetigkeit()

        await waitFor(() => expect(buchungsAufrufe('start')).toHaveLength(1))
        await waitFor(() => {
            const stored = JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}')
            expect(stored.projektId).toBe(88)
        })
        expect(screen.queryByRole('alert')).toBeNull()
    })

    it('stellt die alte Buchung wieder her, wenn der Start nach dem Stop abgelehnt wird', async () => {
        mockedSendeBuchung.mockImplementation(async (typ, daten) => {
            if (typ === 'stop') return { art: 'ok', daten: {} }
            if (daten.projektId === 88) return { art: 'abgelehnt', status: 400, meldung: 'Projekt ist abgeschlossen' }
            return { art: 'ok', daten: { id: 401 } }
        })

        await wechsleProjektUndTaetigkeit()

        expect(await screen.findByText('Wechsel nicht möglich: Projekt ist abgeschlossen')).toBeInTheDocument()
        // Neuer Start, dann Wiederherstellung der alten Buchung auf Projekt 77
        await waitFor(() => expect(buchungsAufrufe('start')).toHaveLength(2))
        expect(buchungsAufrufe('start')[1][1]).toMatchObject({ projektId: 77, arbeitsgangId: 11, produktkategorieId: 5 })
        const stored = JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}')
        expect(stored.projektId).toBe(77)
        expect(stored.produktkategorieName).toBe('Geländer')
    })

    it('behält Projekt und Kategorie, wenn nur die Tätigkeit gewechselt wird', async () => {
        sendeBuchungAntwortet({ start: { art: 'ok', daten: { id: 999 } } })

        const user = userEvent.setup()
        renderDashboard()
        await user.click(await screen.findByRole('button', { name: /tätigkeit/i }))
        await user.click(await screen.findByRole('button', { name: 'Schweißen' }))

        await waitFor(() => expect(buchungsAufrufe('start')).toHaveLength(1))
        expect(buchungsAufrufe('start')[0][1]).toMatchObject({ projektId: 77, arbeitsgangId: 12, produktkategorieId: 5 })
    })
})

describe('DashboardPage – Nachrichten-Glocke (#270)', () => {
    beforeEach(() => {
        vi.stubGlobal('localStorage', createMemoryStorage())
        localStorage.setItem('zeiterfassung_token', 'tok-test')

        mockedOfflineService.getFailedEntries.mockResolvedValue([])
        mockedOfflineService.getHeuteGearbeitet.mockResolvedValue({ stunden: 0, minuten: 0, fromCache: false })
        mockedOfflineService.getPendingCount.mockResolvedValue(1)
        mockedOfflineService.getUnsyncedStopMinutes.mockResolvedValue(0)
        mockedOfflineService.addPendingEntryWithOperationId.mockResolvedValue(undefined)
    })

    afterEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        vi.unstubAllGlobals()
    })

    const ENTSCHEIDUNGEN = [
        { id: 11, typ: 'URLAUB', status: 'GENEHMIGT', vonDatum: '2026-08-03', bisDatum: '2026-08-14', entschiedenAm: '2026-07-20T09:00:00' },
        { id: 12, typ: 'ZEITAUSGLEICH', status: 'ABGELEHNT', vonDatum: '2026-09-01', bisDatum: '2026-09-01', entschiedenAm: '2026-07-21T09:00:00' },
    ]
    const VERFALL = { resturlaubTage: 3.5, verfallsDatum: '2027-03-31', verfallsJahr: 2027, tageVerbleibend: 20, dringend: true }

    function stubNachrichten(opts: { entscheidungen?: unknown[]; verfall?: unknown; putStatus?: number; fehlerBeiId?: number } = {}) {
        const puts: string[] = []
        const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
            const url = typeof input === 'string' ? input : input.toString()
            if (init?.method === 'PUT' && url.includes('/api/urlaub/antraege/')) {
                puts.push(url)
                if (opts.fehlerBeiId !== undefined && url.includes(`/antraege/${opts.fehlerBeiId}/`)) return new Response(null, { status: 500 })
                return new Response(null, { status: opts.putStatus ?? 204 })
            }
            if (url.includes('/api/urlaub/antraege/neue-entscheidungen')) {
                return new Response(JSON.stringify(opts.entscheidungen ?? ENTSCHEIDUNGEN), { status: 200, headers: { 'Content-Type': 'application/json' } })
            }
            if (url.includes('/api/zeiterfassung/urlaubsverfall/')) {
                return new Response(JSON.stringify(opts.verfall ?? {}), { status: 200, headers: { 'Content-Type': 'application/json' } })
            }
            return antwortMitZeitkontoStatus(input)
        })
        vi.stubGlobal('fetch', fetchMock)
        return { fetchMock, puts }
    }

    const glocke = () => screen.getByRole('button', { name: /^Nachrichten/ })
    // Klicks im Sheet per fireEvent: vaul nutzt Pointer-APIs, die jsdom nicht hat (siehe KalenderPage.test).
    const oeffneGlocke = async () => {
        fireEvent.click(glocke())
        return screen.findByRole('dialog')
    }

    it('zeigt die Zahl neuer Nachrichten an der Glocke statt Karten auf dem Dashboard', async () => {
        const { fetchMock } = stubNachrichten()
        renderDashboard()

        expect(await screen.findByTestId('nachrichten-zaehler')).toHaveTextContent('2')
        expect(glocke()).toHaveAccessibleName('Nachrichten, 2 neu')
        expect(fetchMock).toHaveBeenCalledWith('/api/urlaub/antraege/neue-entscheidungen?mitarbeiterId=1')
        // Auf dem Dashboard selbst steht keine Entscheidungskarte mehr.
        expect(screen.queryByText('Dein Urlaub vom 03.08.2026 bis 14.08.2026 wurde genehmigt.')).not.toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Verstanden' })).not.toBeInTheDocument()
    })

    it('zeigt ab zehn Nachrichten „9+"', async () => {
        stubNachrichten({ entscheidungen: Array.from({ length: 12 }, (_, i) => ({ ...ENTSCHEIDUNGEN[0], id: 100 + i })) })
        renderDashboard()
        expect(await screen.findByTestId('nachrichten-zaehler')).toHaveTextContent('9+')
    })

    it('markiert eine Entscheidung einzeln als gelesen – PUT …/{id}/entscheidung-gesehen, Zeile weg', async () => {
        const { puts } = stubNachrichten()
        renderDashboard()
        await screen.findByTestId('nachrichten-zaehler')

        const sheet = await oeffneGlocke()
        expect(within(sheet).getByText('Dein Urlaub vom 03.08.2026 bis 14.08.2026 wurde genehmigt.')).toBeInTheDocument()
        expect(within(sheet).getByText('Dein Zeitausgleich am 01.09.2026 wurde abgelehnt.')).toBeInTheDocument()
        expect(within(sheet).getByText('Entschieden am 20.07.2026')).toBeInTheDocument()

        fireEvent.click(within(sheet).getByRole('button', { name: 'Gelesen: Urlaub genehmigt' }))

        await waitFor(() => expect(puts).toEqual(['/api/urlaub/antraege/11/entscheidung-gesehen']))
        await waitFor(() => expect(within(sheet).queryByText('Dein Urlaub vom 03.08.2026 bis 14.08.2026 wurde genehmigt.')).not.toBeInTheDocument())
        expect(within(sheet).getAllByTestId('nachricht')).toHaveLength(1)
        expect(screen.getByTestId('nachrichten-zaehler')).toHaveTextContent('1')
    })

    it('markiert alle angezeigten einzeln als gelesen (nicht über den Sammel-Endpunkt) und zeigt danach den leeren Zustand', async () => {
        const { puts } = stubNachrichten({ verfall: VERFALL })
        renderDashboard()
        expect(await screen.findByTestId('nachrichten-zaehler')).toHaveTextContent('3')

        const sheet = await oeffneGlocke()
        fireEvent.click(within(sheet).getByRole('button', { name: 'Alle als gelesen markieren' }))

        await waitFor(() => expect([...puts].sort()).toEqual([
            '/api/urlaub/antraege/11/entscheidung-gesehen',
            '/api/urlaub/antraege/12/entscheidung-gesehen',
        ]))
        expect(puts.some(url => url.includes('entscheidungen-gesehen'))).toBe(false)
        expect(await within(sheet).findByText('Keine neuen Nachrichten.')).toBeInTheDocument()
        expect(screen.queryByTestId('nachrichten-zaehler')).not.toBeInTheDocument()
    })

    it('„Alle als gelesen": erfolgreiche verschwinden, eine fehlgeschlagene bleibt stehen und meldet sich', async () => {
        stubNachrichten({ fehlerBeiId: 12 })
        renderDashboard()
        await screen.findByTestId('nachrichten-zaehler')

        const sheet = await oeffneGlocke()
        fireEvent.click(within(sheet).getByRole('button', { name: 'Alle als gelesen markieren' }))

        expect(await screen.findByText('Das konnte gerade nicht gespeichert werden. Bitte später noch einmal versuchen.')).toBeInTheDocument()
        await waitFor(() => expect(within(sheet).getAllByTestId('nachricht')).toHaveLength(1))
        expect(within(sheet).getByText('Dein Zeitausgleich am 01.09.2026 wurde abgelehnt.')).toBeInTheDocument()
    })

    it('zeigt eine Storno-Entscheidung des Büros neutral mit „vom Büro storniert"', async () => {
        stubNachrichten({ entscheidungen: [{ id: 13, typ: 'URLAUB', status: 'STORNIERT', vonDatum: '2026-08-03', bisDatum: '2026-08-14', entschiedenAm: '2026-07-22T09:00:00' }] })
        renderDashboard()
        await screen.findByTestId('nachrichten-zaehler')

        const sheet = await oeffneGlocke()
        expect(within(sheet).getByText('Urlaub storniert')).toBeInTheDocument()
        expect(within(sheet).getByText('Dein Urlaub vom 03.08.2026 bis 14.08.2026 wurde vom Büro storniert.')).toBeInTheDocument()
        expect(within(sheet).getByTestId('nachricht').querySelector('.lucide-ban')).not.toBeNull()
    })

    it('lässt die Nachricht stehen und zeigt einen Toast, wenn „Gelesen" nicht gespeichert werden kann', async () => {
        stubNachrichten({ putStatus: 500 })
        renderDashboard()
        await screen.findByTestId('nachrichten-zaehler')

        const sheet = await oeffneGlocke()
        fireEvent.click(within(sheet).getByRole('button', { name: 'Gelesen: Urlaub genehmigt' }))

        expect(await screen.findByText('Das konnte gerade nicht gespeichert werden. Bitte später noch einmal versuchen.')).toBeInTheDocument()
        expect(within(sheet).getAllByTestId('nachricht')).toHaveLength(2)
    })

    it('merkt sich die gelesene Urlaubsverfall-Warnung auf dem Gerät, zeigt sie bei geänderten Daten wieder', async () => {
        stubNachrichten({ entscheidungen: [], verfall: VERFALL })
        const { unmount } = renderDashboard()
        expect(await screen.findByTestId('nachrichten-zaehler')).toHaveTextContent('1')

        const sheet = await oeffneGlocke()
        expect(within(sheet).getByText('Dringend: Urlaub verfällt bald')).toBeInTheDocument()
        expect(within(sheet).getByText(/Du hast noch 3,5 Resturlaubstage vom Vorjahr\. Nimm sie bis 31\.03\.2027, danach verfallen sie \(noch 20 Tage\)\./)).toBeInTheDocument()
        fireEvent.click(within(sheet).getByRole('button', { name: 'Gelesen: Dringend: Urlaub verfällt bald' }))
        expect(await within(sheet).findByText('Keine neuen Nachrichten.')).toBeInTheDocument()
        unmount()

        // Gleiche Warnung → bleibt gelesen.
        renderDashboard()
        await screen.findByText('Zeit erfassen')
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledWith(expect.stringContaining('/api/zeiterfassung/urlaubsverfall/')))
        expect(screen.queryByTestId('nachrichten-zaehler')).not.toBeInTheDocument()
        cleanup()

        // Weniger Resttage → neue Warnung, erscheint wieder.
        stubNachrichten({ entscheidungen: [], verfall: { ...VERFALL, resturlaubTage: 2 } })
        renderDashboard()
        expect(await screen.findByTestId('nachrichten-zaehler')).toHaveTextContent('1')
    })

    it('zeigt im leeren Zustand einen freundlichen Hinweis und keinen Zähler', async () => {
        stubNachrichten({ entscheidungen: [] })
        renderDashboard()
        await screen.findByText('Zeit erfassen')
        expect(screen.queryByTestId('nachrichten-zaehler')).not.toBeInTheDocument()

        const sheet = await oeffneGlocke()
        expect(within(sheet).getByText('Keine neuen Nachrichten.')).toBeInTheDocument()
        expect(within(sheet).queryByRole('button', { name: 'Alle als gelesen markieren' })).not.toBeInTheDocument()
    })
})

describe('DashboardPage – Neuladen nach Abgleich', () => {
    beforeEach(() => {
        vi.stubGlobal('localStorage', createMemoryStorage())
        localStorage.setItem('zeiterfassung_token', 'tok-test')
        mockedOfflineService.getFailedEntries.mockResolvedValue([])
        mockedOfflineService.getHeuteGearbeitet.mockResolvedValue({ stunden: 0, minuten: 0, fromCache: false })
        mockedOfflineService.getPendingCount.mockResolvedValue(0)
        mockedOfflineService.getUnsyncedStopMinutes.mockResolvedValue(0)
        vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => antwortMitZeitkontoStatus(input)))
    })

    afterEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
        vi.unstubAllGlobals()
    })

    const aktivAbrufe = () => vi.mocked(fetch).mock.calls.filter(([input]) => String(input).includes('/api/zeiterfassung/aktiv/')).length

    it('lädt nach jedem erfolgreichen Abgleich neu – auch zweimal hintereinander', async () => {
        const { rerender } = renderDashboard(3)
        await screen.findByText('Zeit erfassen')
        await waitFor(() => expect(aktivAbrufe()).toBe(1))
        const heuteVorher = mockedOfflineService.getHeuteGearbeitet.mock.calls.length

        rerender(dashboardMitAbgleich(4))
        await waitFor(() => expect(aktivAbrufe()).toBe(2))
        expect(mockedOfflineService.getHeuteGearbeitet.mock.calls.length).toBeGreaterThan(heuteVorher)

        rerender(dashboardMitAbgleich(5))
        await waitFor(() => expect(aktivAbrufe()).toBe(3))
    })

    it('lädt ohne neuen Abgleich nicht erneut', async () => {
        const { rerender } = renderDashboard(2)
        await screen.findByText('Zeit erfassen')
        await waitFor(() => expect(aktivAbrufe()).toBe(1))

        rerender(dashboardMitAbgleich(2))
        await screen.findByText('Zeit erfassen')
        expect(aktivAbrufe()).toBe(1)
    })
})
