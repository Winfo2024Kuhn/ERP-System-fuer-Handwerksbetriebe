import { ToastProvider } from '../components/ui/toast'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AbwesenheitenPage from './AbwesenheitenPage'

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

// Dummy-Daten (DSGVO): Antrag-Objekte tragen ohnehin keinen Namen, mitarbeiter
// nutzt den Standard-Dummy "Max Mustermann".
type FetchMockOptions = {
    antraege?: unknown[]
    langzeit?: unknown
    langzeitError?: boolean
}

function buildFetchMock({ antraege = [], langzeit = {}, langzeitError = false }: FetchMockOptions) {
    return vi.fn(async (input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString()
        if (url.includes('/api/urlaub/antraege')) {
            return new Response(JSON.stringify(antraege), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            })
        }
        if (url.includes('/api/zeiterfassung/langzeitkrankmeldung/')) {
            if (langzeitError) {
                throw new Error('Netzwerkfehler')
            }
            return new Response(JSON.stringify(langzeit), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            })
        }
        return new Response('{}', { status: 404 })
    })
}

const renderPage = () =>
    render(
        <MemoryRouter>
            <ToastProvider><AbwesenheitenPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ToastProvider>
        </MemoryRouter>,
    )

describe('AbwesenheitenPage – Langzeitkrankmeldung im Verlauf', () => {
    beforeEach(() => {
        vi.stubGlobal('localStorage', createMemoryStorage())
        localStorage.setItem('zeiterfassung_token', 'tok-test')
    })

    afterEach(() => {
        vi.unstubAllGlobals()
        vi.restoreAllMocks()
    })

    it('zeigt den Info-Banner bei laufender Wiedereingliederung', async () => {
        vi.stubGlobal(
            'fetch',
            buildFetchMock({
                antraege: [],
                langzeit: {
                    phase: 'WIEDEREINGLIEDERUNG',
                    phaseLabel: 'Wiedereingliederung',
                    heuteGeplanteStunden: 4,
                    seit: '2026-03-01',
                    bisDatum: '2026-04-30',
                },
            }),
        )

        renderPage()

        expect(await screen.findByText(/Wiedereingliederung seit 01\.03\.2026/)).toBeInTheDocument()
        expect(screen.getByText(/heute 4 Stunden geplant/)).toBeInTheDocument()
    })

    it('zeigt keinen Banner, wenn der Server ein leeres Objekt liefert', async () => {
        vi.stubGlobal('fetch', buildFetchMock({ antraege: [], langzeit: {} }))

        renderPage()

        await waitFor(() => expect(screen.getByText(/Keine Anträge/)).toBeInTheDocument())
        expect(screen.queryByText(/seit 01\.03\.2026/)).not.toBeInTheDocument()
        expect(screen.queryByText(/Stunden geplant/)).not.toBeInTheDocument()
    })

    it('rendert die Antragsliste unabhängig vom Banner', async () => {
        vi.stubGlobal(
            'fetch',
            buildFetchMock({
                antraege: [
                    { id: 1, vonDatum: '2026-01-10', bisDatum: '2026-01-10', typ: 'KRANKHEIT', status: 'GENEHMIGT' },
                ],
                langzeit: {
                    phase: 'LOHNFORTZAHLUNG',
                    phaseLabel: 'Lohnfortzahlung durch den Betrieb',
                    heuteGeplanteStunden: null,
                    seit: '2026-02-15',
                    bisDatum: null,
                },
            }),
        )

        renderPage()

        expect(await screen.findByText('Krankheit')).toBeInTheDocument()
        expect(
            screen.getByText(/Lohnfortzahlung durch den Betrieb seit 15\.02\.2026/),
        ).toBeInTheDocument()
        // Ohne heuteGeplanteStunden kein Stundenzusatz.
        expect(screen.queryByText(/Stunden geplant/)).not.toBeInTheDocument()
    })

    it('ein Fehler der Langzeit-Abfrage bricht die Seite nicht', async () => {
        vi.stubGlobal(
            'fetch',
            buildFetchMock({
                antraege: [
                    { id: 2, vonDatum: '2026-02-01', bisDatum: '2026-02-03', typ: 'URLAUB', status: 'OFFEN' },
                ],
                langzeitError: true,
            }),
        )

        renderPage()

        expect(await screen.findByText('Urlaub')).toBeInTheDocument()
        expect(screen.queryByText(/Stunden geplant/)).not.toBeInTheDocument()
        expect(screen.queryByText(/Wiedereingliederung/)).not.toBeInTheDocument()
    })

    // Zwei Schreibweisen für Stunden in derselben App sind ein Bug: das
    // Dashboard formatiert schon deutsch ("2,5"), der Banner hier gab bisher
    // die rohe Zahl aus ("2.5"). Beide sollen jetzt gleich schreiben.
    it('schreibt Stunden deutsch (Komma statt Punkt), wie im Dashboard', async () => {
        vi.stubGlobal(
            'fetch',
            buildFetchMock({
                antraege: [],
                langzeit: {
                    phase: 'WIEDEREINGLIEDERUNG',
                    phaseLabel: 'Wiedereingliederung',
                    heuteGeplanteStunden: 2.5,
                    seit: '2026-03-01',
                    bisDatum: '2026-04-30',
                },
            }),
        )

        renderPage()

        expect(await screen.findByText(/heute 2,5 Stunden geplant/)).toBeInTheDocument()
        expect(screen.queryByText(/heute 2\.5 Stunden geplant/)).not.toBeInTheDocument()
    })

    // Befund 1 (Design-Review): teal kommt im Design-System nicht vor. Der
    // Banner steht für neutrale Information -> indigo (--info-Rolle).
    it('nutzt indigo statt teal für den Info-Banner', async () => {
        vi.stubGlobal(
            'fetch',
            buildFetchMock({
                antraege: [],
                langzeit: {
                    phase: 'WIEDEREINGLIEDERUNG',
                    phaseLabel: 'Wiedereingliederung',
                    heuteGeplanteStunden: 4,
                    seit: '2026-03-01',
                    bisDatum: '2026-04-30',
                },
            }),
        )

        renderPage()

        const text = await screen.findByText(/Wiedereingliederung seit 01\.03\.2026/)
        const banner = text.closest('div.bg-indigo-50') as HTMLElement | null
        expect(banner).not.toBeNull()
        expect(banner!.className).not.toMatch(/teal/)
        expect(banner!.innerHTML).not.toMatch(/teal/)
    })
})
