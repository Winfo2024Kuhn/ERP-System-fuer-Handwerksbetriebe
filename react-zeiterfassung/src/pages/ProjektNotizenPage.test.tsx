import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import { ConfirmProvider } from '../components/ui/confirm-dialog'
import ProjektNotizenPage from './ProjektNotizenPage'
import AnfrageNotizenPage from './AnfrageNotizenPage'
const notiz = { id: 1, notiz: 'Testnotiz', erstelltAm: '2026-09-01T10:00:00', mitarbeiterId: 1, mitarbeiterVorname: 'Max', mitarbeiterNachname: 'Mustermann', mobileSichtbar: true, nurFuerErsteller: false, canEdit: true, bilder: [{ id: 2, originalDateiname: 'test.png', url: '/test.png', erstelltAm: '2026-09-01T10:00:00' }] }
beforeEach(() => { vi.stubGlobal('confirm', vi.fn(() => false)); vi.stubGlobal('fetch', vi.fn(async (_url, options) => new Response(JSON.stringify(options?.method === 'DELETE' ? {} : [notiz]), { status: 200 }))) })
afterEach(() => vi.unstubAllGlobals())
describe.each([['projekte', ':projektId', ProjektNotizenPage], ['anfragen', ':anfrageId', AnfrageNotizenPage]] as const)('%s eigene Bestätigungen', (base, param, Page) => {
    it.each(['Notiz', 'Bild'])('%s löscht erst nach Bestätigung, Abbruch sendet nichts', async label => {
        const user = userEvent.setup()
        render(<MemoryRouter initialEntries={[`/${base}/1`]}><ToastProvider><ConfirmProvider><Routes><Route path={`/${base}/${param}`} element={<Page />} /></Routes></ConfirmProvider></ToastProvider></MemoryRouter>)
        const action = await screen.findByRole('button', { name: `${label} löschen` })
        await user.click(action)
        let dialog = screen.getByRole('dialog')
        expect(fetch).not.toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ method: 'DELETE' }))
        await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }))
        expect(fetch).not.toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ method: 'DELETE' }))
        await user.click(action); dialog = screen.getByRole('dialog')
        await user.click(within(dialog).getByRole('button', { name: 'Löschen', exact: true }))
        await waitFor(() => expect(fetch).toHaveBeenCalledWith(expect.stringContaining(label === 'Bild' ? '/1/bilder/2?' : '/1?'), { method: 'DELETE' }))
        expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'DELETE')).toHaveLength(1)
        expect(window.confirm).not.toHaveBeenCalled()
    })
})
it.each([ProjektNotizenPage, AnfrageNotizenPage])('meldet einen fehlgeschlagenen Notizabruf', async Page => {
 vi.mocked(fetch).mockResolvedValue(new Response('{}', { status: 500 }))
 render(<MemoryRouter initialEntries={['/1/1']}><ToastProvider><ConfirmProvider><Routes><Route path="/:projektId/:anfrageId" element={<Page />} /></Routes></ConfirmProvider></ToastProvider></MemoryRouter>)
 expect(await screen.findByRole('alert')).toHaveTextContent(/Notizen.*geladen/)
})

// Die Komponente bewusst NICHT gemockt — hier soll die echte Kette laufen.
// Gemockt sind nur die beiden Dienste darunter, damit kein Mikrofon und kein
// Netz gebraucht wird.
vi.mock('../services/audioRecorderService', async () => {
    const echt = await vi.importActual<typeof import('../services/audioRecorderService')>('../services/audioRecorderService')
    return {
        ...echt,
        mikrofonWirdUnterstuetzt: () => true,
        starteAufnahme: vi.fn(async () => ({
            mimeType: 'audio/webm',
            gestartetAm: Date.now() - 5000,
            stoppe: async () => new Blob(['x'], { type: 'audio/webm' }),
            brichAb: () => undefined,
        })),
    }
})
vi.mock('../services/spracheingabeService', () => ({
    transkribiere: vi.fn(async () => 'Geländer montiert.'),
    SpracheingabeFehler: class extends Error {},
}))

describe.each([
    ['projekte', ':projektId', ProjektNotizenPage, 'Eintrag'],
    ['anfragen', ':anfrageId', AnfrageNotizenPage, 'Notiz'],
] as const)('%s Spracheingabe', (base, param, Page, feldName) => {
    it('lässt getippten Text stehen und hängt das Diktat mit Leerzeile an', async () => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_spracheingabe_hinweis', '1')
        const user = userEvent.setup()
        render(
            <MemoryRouter initialEntries={[`/${base}/1`]}>
                <ToastProvider><ConfirmProvider>
                    <Routes><Route path={`/${base}/${param}`} element={<Page />} /></Routes>
                </ConfirmProvider></ToastProvider>
            </MemoryRouter>,
        )

        await user.click(await screen.findByRole('button', { name: 'Eintrag hinzufügen' }))

        const feld = await screen.findByRole('textbox')
        await user.type(feld, 'Von Hand getippt')

        await user.click(screen.getByRole('button', { name: `${feldName} diktieren` }))
        await user.click(await screen.findByRole('button', { name: 'Aufnahme beenden' }))

        await waitFor(() => expect(feld).toHaveValue('Von Hand getippt\n\nGeländer montiert.'))

        localStorage.clear()
    })
})
