import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import SaldenPage from './SaldenPage'

// Dummy-Daten (DSGVO): Max Mustermann.
beforeEach(() => {
    localStorage.setItem('zeiterfassung_token', 'tok-test')
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        const json = url.includes('/buchungszeitfenster/')
            ? { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }
            : {
                jahr: 2026,
                urlaub: { jahresanspruch: 30, genommen: 10.5, geplant: 7, verbleibend: 12.5, krankheitsTage: 0, fortbildungsTage: 0 },
                monat: { name: 'Oktober', monatNummer: 10, sollStunden: 160, istStunden: 150, differenz: -10, festgeschrieben: false },
                gesamt: { istStunden: 900, sollStunden: 880, saldo: 20, startDatum: '2026-01-01', vorlaeufig: true, geprueftBis: null },
            }
        return new Response(JSON.stringify(json), { status: 200, headers: { 'Content-Type': 'application/json' } })
    }))
})
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear() })

it('zeigt halbe Urlaubstage mit Komma statt Dezimalpunkt', async () => {
    render(<ToastProvider><MemoryRouter><SaldenPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></MemoryRouter></ToastProvider>)
    expect(await screen.findByText('10,5')).toBeInTheDocument()
    expect(screen.getByText('12,5')).toBeInTheDocument()
    expect(screen.getByText('7')).toBeInTheDocument()
    expect(screen.queryByText('10.5')).not.toBeInTheDocument()
    expect(screen.queryByText('12.5')).not.toBeInTheDocument()
})

it('nennt das eingestellte Bundesland und kennzeichnet halbe Feiertage', async () => {
    const saldoFetch = vi.mocked(fetch).getMockImplementation()!
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).startsWith('/api/zeiterfassung/feiertage')) {
            return new Response(JSON.stringify([
                { datum: '2026-12-24', bezeichnung: 'Heiligabend', halbTag: true, bundesland: 'HH' },
                { datum: '2026-01-01', bezeichnung: 'Neujahr', halbTag: false, bundesland: 'HH' },
            ]), { status: 200, headers: { 'Content-Type': 'application/json' } })
        }
        return saldoFetch(input, init)
    }))
    render(<ToastProvider><MemoryRouter><SaldenPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></MemoryRouter></ToastProvider>)
    expect(await screen.findByText('2 Feiertage in Hamburg')).toBeInTheDocument()
    expect(screen.queryByText(/Bayern/)).not.toBeInTheDocument()
    expect(screen.getByText('(halber Tag)')).toBeInTheDocument()
    expect(screen.getAllByText('(halber Tag)')).toHaveLength(1)
})
