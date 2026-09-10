import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import MwstRechnerPage from './MwstRechnerPage'
beforeEach(() => vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ netto: 12.5, brutto: 14.88, satzProzent: 19, mwstBetrag: 2.38 }), { status: 200 }))))
afterEach(() => vi.unstubAllGlobals())
const open = () => render(<MemoryRouter><ToastProvider><MwstRechnerPage /></ToastProvider></MemoryRouter>)
it('leert Null bei Fokus und sendet den vollständigen Kommawert als Zahl', async () => {
    const user = userEvent.setup(); open()
    const netto = screen.getByLabelText('Netto (ohne MwSt)')
    await user.type(netto, '0,00'); await user.tab(); await user.click(netto)
    expect(netto).toHaveValue('')
    await user.type(netto, '12,50'); await user.click(screen.getByRole('button', { name: 'Rechnen' }))
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ body: JSON.stringify({ netto: 12.5, brutto: null, satzProzent: 19 }) })))
    expect(await screen.findByDisplayValue('12,50')).toBeVisible()
})
it.each(['12,', '1e2', '12.50', '12x'])('blockiert %s auch wenn zwei andere Zahlen vorhanden sind', async draft => {
    const user = userEvent.setup(); open()
    await user.type(screen.getByLabelText('Netto (ohne MwSt)'), draft)
    await user.type(screen.getByLabelText('Brutto (mit MwSt)'), '119')
    await user.click(screen.getByRole('button', { name: 'Rechnen' }))
    expect(fetch).not.toHaveBeenCalled()
    expect(await screen.findByRole('alert')).toHaveTextContent(/Netto/)
})
it('zeigt Netzwerkfehler als eigene Meldung', async () => {
    vi.mocked(fetch).mockRejectedValue(new Error('offline'))
    const user = userEvent.setup(); open()
    await user.type(screen.getByLabelText('Netto (ohne MwSt)'), '100')
    await user.click(screen.getByRole('button', { name: 'Rechnen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/Berechnung/)
})
it.each([['Netto (ohne MwSt)', '1000000001'], ['Brutto (mit MwSt)', '-1000000001'], ['MwSt-Satz', '101'], ['MwSt-Satz', '-1']])('blockiert fachliche Grenze bei %s', async (label, draft) => {
 const user = userEvent.setup(); open()
 await user.type(screen.getByLabelText('Netto (ohne MwSt)'), '100')
 await user.clear(screen.getByLabelText(label)); await user.type(screen.getByLabelText(label), draft)
 await user.click(screen.getByRole('button', { name: 'Rechnen' }))
 expect(fetch).not.toHaveBeenCalled()
 expect(await screen.findByRole('alert')).toHaveTextContent(/mindestens|höchstens/)
})
it('leert eine Null auch bei Tabfokus und erhält einen negativen Betrag', async () => {
 const user = userEvent.setup(); open()
 const netto = screen.getByLabelText('Netto (ohne MwSt)')
 await user.type(netto, '0'); await user.tab(); await user.tab({ shift: true }); expect(netto).toHaveValue('')
 await user.type(netto, '-12,50'); await user.tab(); await user.tab({ shift: true }); expect(netto).toHaveValue('-12,50')
})
