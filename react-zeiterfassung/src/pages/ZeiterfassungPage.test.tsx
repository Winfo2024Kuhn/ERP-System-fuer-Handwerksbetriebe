import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import ZeiterfassungPage from './ZeiterfassungPage'
import { OfflineService } from '../services/OfflineService'
vi.mock('../services/OfflineService', async importOriginal => ({ ...await importOriginal<object>(), OfflineService: { getProjekte: vi.fn(async () => [{ id: 1, name: 'Testprojekt', projektNummer: 'TEST-1', kundenName: 'Testbetrieb' }]), getKategorien: vi.fn(async () => [{ id: 1, name: 'Testkategorie' }]), getArbeitsgaenge: vi.fn(async () => [{ id: 1, beschreibung: 'Montage' }]), addPendingEntryWithOperationId: vi.fn() } }))
beforeEach(() => { localStorage.setItem('zeiterfassung_token', 'test-token'); localStorage.removeItem('zeiterfassung_active_session'); vi.stubGlobal('alert', vi.fn()); vi.stubGlobal('fetch', vi.fn(async (_url, options) => new Response(JSON.stringify(options?.method === 'POST' ? { error: 'Buchung ist gesperrt' } : { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }), { status: options?.method === 'POST' ? 409 : 200 }))) })
afterEach(() => vi.unstubAllGlobals())
it('meldet abgelehnte Buchung als Toast und legt keine Offlinebuchung an', async () => {
 const user = userEvent.setup()
 render(<MemoryRouter><ToastProvider><ZeiterfassungPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ToastProvider></MemoryRouter>)
 await user.click(await screen.findByText('Testprojekt'))
 await user.click(await screen.findByText('Testkategorie'))
 await user.click(await screen.findByText('Montage'))
 await user.click(screen.getByRole('button', { name: /Starten|Zeit erfassen|Buchung starten/i }))
 expect(await screen.findByRole('alert')).toHaveTextContent('Buchung ist gesperrt')
 expect(window.alert).not.toHaveBeenCalled()
 expect(OfflineService.addPendingEntryWithOperationId).not.toHaveBeenCalled()
 expect(localStorage.getItem('zeiterfassung_active_session')).toBeNull()
})
