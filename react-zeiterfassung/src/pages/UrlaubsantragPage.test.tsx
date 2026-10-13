import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import { ConfirmProvider } from '../components/ui/confirm-dialog'
import UrlaubsantragPage from './UrlaubsantragPage'
import AbwesenheitenPage from './AbwesenheitenPage'
beforeEach(() => vi.stubGlobal('fetch', vi.fn(async url => new Response(JSON.stringify(String(url).includes('resturlaub') ? { verbleibend: 20 } : []), { status: 200 }))))
afterEach(() => vi.unstubAllGlobals())
it('wählt eigene Kalenderdaten und verhindert ein Ende vor dem Beginn', async () => {
 const user = userEvent.setup()
 render(<MemoryRouter><ToastProvider><ConfirmProvider><UrlaubsantragPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ConfirmProvider></ToastProvider></MemoryRouter>)
 await user.click(screen.getByRole('button', { name: 'Vom (Erster Tag)' }))
 const today = new Date()
 const label = new Date(today.getFullYear(), today.getMonth(), 15).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
 await user.click(screen.getByRole('button', { name: label }))
 expect(screen.getByRole('button', { name: 'Vom (Erster Tag)' })).toHaveTextContent(label)
 await user.click(screen.getByRole('button', { name: 'Bis (Letzter Tag)' }))
 const previous = new Date(today.getFullYear(), today.getMonth(), 14).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
 expect(screen.getByRole('button', { name: previous })).toBeDisabled()
})
it('ändert den Abwesenheitsfilter über eigene Optionen', async () => {
 const user = userEvent.setup()
 render(<MemoryRouter><ToastProvider><AbwesenheitenPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ToastProvider></MemoryRouter>)
 await user.click(screen.getByRole('combobox', { name: 'Status' }))
 await user.click(screen.getByRole('option', { name: 'Genehmigt' }))
 await waitFor(() => expect(fetch).toHaveBeenCalledWith(expect.stringContaining('status=GENEHMIGT')))
})
it.each([UrlaubsantragPage, AbwesenheitenPage])('meldet nicht erreichbare Abwesenheitsdaten', async Page => {
 vi.mocked(fetch).mockResolvedValue(new Response('{}', { status: 500 }))
 render(<MemoryRouter><ToastProvider><ConfirmProvider><Page mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ConfirmProvider></ToastProvider></MemoryRouter>)
 expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent(/geladen|laden/)
})

it('kann Zeitausgleich mit aktuellem Zeitkonto-Saldo auswählen und beantragen', async () => {
    localStorage.setItem('zeiterfassung_token', 'test-token')
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        if (url.includes('/api/zeiterfassung/saldo/')) {
            return new Response(JSON.stringify({
                gesamt: { saldo: 14.5 }
            }), { status: 200 })
        }
        if (url.includes('/api/zeitverwaltung/feiertage/zwischen')) {
            return new Response(JSON.stringify([]), { status: 200 })
        }
        if (url.includes('/api/urlaub/antraege') && init?.method === 'POST') {
            return new Response(JSON.stringify({ id: 99, typ: 'ZEITAUSGLEICH' }), { status: 200 })
        }
        return new Response(JSON.stringify([]), { status: 200 })
    })

    render(
        <MemoryRouter initialEntries={['/urlaub?typ=ZEITAUSGLEICH']}>
            <ToastProvider>
                <ConfirmProvider>
                    <UrlaubsantragPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} />
                </ConfirmProvider>
            </ToastProvider>
        </MemoryRouter>
    )

    await waitFor(() => expect(screen.getByText(/Aktuelles Zeitkonto:\s*\+14,5\s*Std\./)).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Zeitausgleich beantragen' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Vom (Erster Tag)' }))
    const today = new Date()
    const label = new Date(today.getFullYear(), today.getMonth(), 15).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
    await user.click(screen.getByRole('button', { name: label }))
    await user.click(screen.getByRole('button', { name: 'Bis (Letzter Tag)' }))
    await user.click(screen.getByRole('button', { name: label }))

    await user.click(screen.getByRole('button', { name: 'Zeitausgleich beantragen' }))

    await waitFor(() => {
        expect(fetch).toHaveBeenCalledWith('/api/urlaub/antraege', expect.objectContaining({
            method: 'POST',
            body: expect.stringContaining('"typ":"ZEITAUSGLEICH"')
        }))
    })

    expect(await screen.findByText('Dein Zeitausgleichsantrag wurde erfolgreich übermittelt.')).toBeInTheDocument()
})


vi.mock('../services/audioRecorderService', async () => {
    const echt = await vi.importActual<typeof import('../services/audioRecorderService')>('../services/audioRecorderService')
    return { ...echt, mikrofonWirdUnterstuetzt: () => true }
})

it('bietet am Bemerkungsfeld einen Diktier-Knopf an', async () => {
    render(<MemoryRouter><ToastProvider><ConfirmProvider><UrlaubsantragPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ConfirmProvider></ToastProvider></MemoryRouter>)

    // Der feldName landet im aria-label — so ist belegt, dass die Seite ihn
    // durchreicht und nicht den Standardwert "Text" stehen laesst.
    expect(await screen.findByRole('button', { name: 'Bemerkung diktieren' })).toBeInTheDocument()
})

/**
 * Issue #270: eigene offene Anträge lassen sich zurückziehen (PUT …/storno),
 * stornierte Anträge erscheinen mit Klartext „Storniert", der Resturlaub zeigt
 * halbe Tage mit Komma.
 */
const ANTRAG_OFFEN = { id: 1, mitarbeiter: { id: 1, vorname: 'Max', nachname: 'Mustermann' }, vonDatum: '2026-08-03', bisDatum: '2026-08-14', bemerkung: '', status: 'OFFEN', typ: 'URLAUB' }
const ANTRAG_STORNIERT = { ...ANTRAG_OFFEN, id: 2, vonDatum: '2026-07-01', bisDatum: '2026-07-02', status: 'STORNIERT' }

function mockUebersicht(storno: { status: number; body: unknown } = { status: 200, body: { ...ANTRAG_OFFEN, status: 'STORNIERT' } }) {
    const puts: string[] = []
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        if (init?.method === 'PUT') {
            puts.push(url)
            return new Response(JSON.stringify(storno.body), { status: storno.status })
        }
        if (url.includes('/api/urlaub/antraege?')) return new Response(JSON.stringify([ANTRAG_OFFEN, ANTRAG_STORNIERT]), { status: 200 })
        if (url.includes('resturlaub')) return new Response(JSON.stringify({ verbleibend: 12.5, jahr: 2026 }), { status: 200 })
        return new Response(JSON.stringify([]), { status: 200 })
    })
    return puts
}

function renderUrlaubsantrag() {
    return render(<MemoryRouter><ToastProvider><ConfirmProvider><UrlaubsantragPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ConfirmProvider></ToastProvider></MemoryRouter>)
}

it('zeigt halbe Resturlaubstage mit Komma', async () => {
    mockUebersicht()
    renderUrlaubsantrag()
    expect(await screen.findByText('12,5 Urlaubstage übrig')).toBeInTheDocument()
})

it('zieht einen offenen Antrag nach Rückfrage über PUT …/storno zurück und zeigt stornierte in Klartext', async () => {
    const user = userEvent.setup()
    const puts = mockUebersicht()
    renderUrlaubsantrag()

    await user.click(screen.getByRole('button', { name: 'Übersicht' }))
    // Filter-Chip plus Badge am stornierten Antrag – beide in Klartext, nirgends der Rohwert.
    expect(await screen.findAllByText('Storniert')).toHaveLength(2)
    expect(screen.queryByText('STORNIERT')).not.toBeInTheDocument()
    expect(screen.getAllByText('Offen')).toHaveLength(2)

    // Nur der offene Antrag hat „Zurückziehen".
    const zurueckziehen = screen.getAllByRole('button', { name: 'Zurückziehen' })
    expect(zurueckziehen).toHaveLength(1)

    await user.click(zurueckziehen[0])
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Antrag zurückziehen?')
    expect(dialog).toHaveTextContent('Deinen Urlaubsantrag vom 03.08.2026 bis 14.08.2026 zurückziehen?')
    await user.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(puts).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Zurückziehen' }))
    const dialogErneut = await screen.findByRole('dialog')
    await user.click(within(dialogErneut).getByRole('button', { name: 'Zurückziehen' }))

    await waitFor(() => expect(puts).toEqual(['/api/urlaub/antraege/1/storno']))
    expect((await screen.findAllByRole('status'))[0]).toHaveTextContent('Antrag zurückgezogen.')
})

it('zeigt beim Zurückziehen die Meldung des Servers', async () => {
    const user = userEvent.setup()
    mockUebersicht({ status: 400, body: { status: 400, message: 'Genehmigten Urlaub nimmt das Büro zurück. Bitte dort melden.' } })
    renderUrlaubsantrag()

    await user.click(screen.getByRole('button', { name: 'Übersicht' }))
    await user.click(await screen.findByRole('button', { name: 'Zurückziehen' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Zurückziehen' }))

    expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent('Genehmigten Urlaub nimmt das Büro zurück. Bitte dort melden.')
})

it('erklärt das Zurückziehen über einen Info-Knopf am Knopf', async () => {
    const user = userEvent.setup()
    mockUebersicht()
    renderUrlaubsantrag()

    await user.click(screen.getByRole('button', { name: 'Übersicht' }))
    await user.click(await screen.findByRole('button', { name: 'Mehr zu: Zurückziehen' }))
    const info = await screen.findByRole('dialog')
    expect(info).toHaveTextContent('Was passiert im Hintergrund?')
    expect(info).toHaveTextContent('Im Kalender war noch nichts eingetragen')
    expect(info).toHaveTextContent('Was bewirkt das?')
    expect(info).toHaveTextContent('dann meldest du dich im Büro')
})

it('öffnet mit ?ansicht=uebersicht direkt die Übersicht (Sprung aus der Nachrichten-Glocke)', async () => {
    mockUebersicht()
    render(<MemoryRouter initialEntries={['/urlaub?ansicht=uebersicht']}><ToastProvider><ConfirmProvider><UrlaubsantragPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ConfirmProvider></ToastProvider></MemoryRouter>)
    expect(await screen.findByRole('button', { name: 'Zurückziehen' })).toBeInTheDocument()
})
