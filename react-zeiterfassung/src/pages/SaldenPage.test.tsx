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
