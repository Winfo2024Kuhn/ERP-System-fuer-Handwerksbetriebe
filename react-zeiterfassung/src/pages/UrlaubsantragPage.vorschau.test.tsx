import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import { ConfirmProvider } from '../components/ui/confirm-dialog'
import UrlaubsantragPage from './UrlaubsantragPage'

// Dummy-Daten (DSGVO): Max Mustermann.
const heute = new Date()
const tagLabel = (tag: number) => new Date(heute.getFullYear(), heute.getMonth(), tag).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
const iso = (tag: number) => `${heute.getFullYear()}-${String(heute.getMonth() + 1).padStart(2, '0')}-${String(tag).padStart(2, '0')}`

type Vorschau = { arbeitstage: number; verbleibend: number | null; reicht: boolean } | 'fehler' | 'offline'

function stubFetch(vorschau: Vorschau, { verbleibend = 20, geschaeftsfuehrer = false } = {}) {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url.includes('/api/urlaub/vorschau')) {
            if (vorschau === 'offline') throw new TypeError('Failed to fetch')
            if (vorschau === 'fehler') return new Response(JSON.stringify({ status: 500, message: 'Kaputt' }), { status: 500 })
            return new Response(JSON.stringify(vorschau), { status: 200 })
        }
        if (url.includes('/api/urlaub/resturlaub')) return new Response(JSON.stringify({ verbleibend }), { status: 200 })
        if (url.includes('/buchungszeitfenster/')) return new Response(JSON.stringify({ fuehrtZeitkonto: true, eingerichtet: true, istGeschaeftsfuehrer: geschaeftsfuehrer }), { status: 200 })
        return new Response(JSON.stringify([]), { status: 200 })
    }))
}

async function zeitraumWaehlen() {
    const user = userEvent.setup()
    render(<MemoryRouter><ToastProvider><ConfirmProvider><UrlaubsantragPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ConfirmProvider></ToastProvider></MemoryRouter>)
    await user.click(screen.getByRole('button', { name: 'Vom (Erster Tag)' }))
    await user.click(screen.getByRole('button', { name: tagLabel(10) }))
    await user.click(screen.getByRole('button', { name: 'Bis (Letzter Tag)' }))
    await user.click(screen.getByRole('button', { name: tagLabel(16) }))
    return user
}

const vorschauAufrufe = () => vi.mocked(fetch).mock.calls.map(([url]) => String(url)).filter(url => url.includes('/api/urlaub/vorschau'))

describe('UrlaubsantragPage – genaue Urlaubstage vom Server', () => {
    beforeEach(() => localStorage.setItem('zeiterfassung_token', 'tok-test'))
    afterEach(() => { vi.unstubAllGlobals(); localStorage.clear() })

    it('zeigt die Tage vom Server und sperrt den Knopf, wenn der Urlaub nicht reicht', async () => {
        stubFetch({ arbeitstage: 4, verbleibend: 3, reicht: false }, { verbleibend: 3 })
        await zeitraumWaehlen()
        expect(await screen.findByText(/Beantragt: 4 Urlaubstage/)).toBeInTheDocument()
        expect(screen.getByText(/Nicht genügend Urlaubstage/)).toBeInTheDocument()
        expect(screen.queryByText(/ungefähr/)).not.toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Antrag senden' })).toBeDisabled()
        // Entprellt: ein Aufruf für den fertigen Zeitraum, nicht schon für „von“ allein.
        expect(vorschauAufrufe()).toEqual([`/api/urlaub/vorschau?mitarbeiterId=1&von=${iso(10)}&bis=${iso(16)}`])
    })

    it('lässt den Knopf frei, wenn der Server sagt, dass der Urlaub reicht – auch wenn die Schätzung mehr Tage zählt', async () => {
        // Die lokale Schätzung zählt Mo–Fr (mind. 5 Tage); der Server zieht Feiertage ab.
        stubFetch({ arbeitstage: 2, verbleibend: 2, reicht: true }, { verbleibend: 2 })
        await zeitraumWaehlen()
        expect(await screen.findByText(/Beantragt: 2 Urlaubstage/)).toBeInTheDocument()
        expect(screen.queryByText(/Nicht genügend Urlaubstage/)).not.toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Antrag senden' })).toBeEnabled()
    })

    it.each(['offline', 'fehler'] as const)('fällt %s auf die Schätzung zurück, schreibt „ungefähr“ und sperrt nicht', async fall => {
        stubFetch(fall, { verbleibend: 0 })
        await zeitraumWaehlen()
        await waitFor(() => expect(vorschauAufrufe()).toHaveLength(1))
        expect(await screen.findByText(/Beantragt: ungefähr [5-7] Urlaubstage/)).toBeInTheDocument()
        expect(screen.getByText('Die genaue Zahl prüft das Büro beim Absenden.')).toBeInTheDocument()
        expect(screen.queryByText(/Nicht genügend Urlaubstage/)).not.toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Antrag senden' })).toBeEnabled()
    })

    it('sperrt bei Geschäftsführern nie', async () => {
        stubFetch({ arbeitstage: 4, verbleibend: null, reicht: false }, { geschaeftsfuehrer: true })
        await zeitraumWaehlen()
        await waitFor(() => expect(vorschauAufrufe()).toHaveLength(1))
        expect(await screen.findByRole('button', { name: 'Urlaub eintragen' })).toBeEnabled()
    })
})
