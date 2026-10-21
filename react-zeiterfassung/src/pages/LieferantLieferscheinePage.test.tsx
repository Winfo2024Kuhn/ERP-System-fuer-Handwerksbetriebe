import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import LieferantLieferscheinePage from './LieferantLieferscheinePage'
import LieferantReklamationCreatePage from './LieferantReklamationCreatePage'
import { LieferantReklamationDetailPage } from './LieferantReklamationDetailPage'
import { releaseCameraStream } from '../services/cameraStreamService'
vi.mock('../services/cameraStreamService', () => ({ releaseCameraStream: vi.fn() }))
// Der echte Scanner braucht Kamera und Bildverarbeitung – hier liefert ein Knopf direkt das Ergebnis.
vi.mock('../components/ScannerModal', () => ({
 default: ({ onSave }: { onSave: (file: File, vorschau: Blob) => Promise<void> }) => <button onClick={() => onSave(new File(['%PDF'], 'Scan_test.pdf', { type: 'application/pdf' }), new Blob(['jpeg'], { type: 'image/jpeg' }))}>Scan fertig</button>,
}))
const reklamation = { id: 1, erstelltAm: '2026-09-01T10:00:00', beschreibung: 'Testreklamation', status: 'OFFEN', erstellerName: 'Max Mustermann', bilder: [] }
beforeEach(() => { vi.stubGlobal('alert', vi.fn()); vi.stubGlobal('fetch', vi.fn(async (url, options) => {
 if (options?.method === 'POST') return new Response('{}', { status: 500 })
 return new Response(JSON.stringify(String(url).includes('/api/reklamationen/1') ? reklamation : String(url).match(/lieferanten\/1\?/) ? { id: 1, lieferantenname: 'Testbetrieb' } : []), { status: 200 })
})); URL.createObjectURL = vi.fn(() => 'blob:test'); URL.revokeObjectURL = vi.fn() })
afterEach(() => vi.unstubAllGlobals())
it('zeigt Analysefehler beim Lieferschein als Toast', async () => {
 const { container } = render(<MemoryRouter initialEntries={['/lieferanten/1']}><ToastProvider><Routes><Route path="/lieferanten/:lieferantId" element={<LieferantLieferscheinePage />} /></Routes></ToastProvider></MemoryRouter>)
 fireEvent.change(container.querySelector('input[type=file]')!, { target: { files: [new File(['dummy'], 'test.txt', { type: 'text/plain' })] } })
 expect(await screen.findByRole('alert')).toHaveTextContent(/Analyse/)
 expect(window.alert).not.toHaveBeenCalled()
})
it('meldet fehlende Reklamationsbeschreibung vor dem Speichern', async () => {
 const user = userEvent.setup()
 render(<MemoryRouter initialEntries={['/lieferanten/1']}><ToastProvider><Routes><Route path="/lieferanten/:lieferantId" element={<LieferantReklamationCreatePage />} /></Routes></ToastProvider></MemoryRouter>)
 await user.click(screen.getByRole('button', { name: 'Speichern' }))
 expect(await screen.findByRole('alert')).toHaveTextContent(/Beschreibung/)
 expect(fetch).not.toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ method: 'POST' }))
 expect(window.alert).not.toHaveBeenCalled()
})
it('meldet auch HTTP-Fehler beim Bilderupload in einer Reklamation', async () => {
 const { container } = render(<MemoryRouter initialEntries={['/reklamation/1']}><ToastProvider><Routes><Route path="/reklamation/:id" element={<LieferantReklamationDetailPage />} /></Routes></ToastProvider></MemoryRouter>)
 await screen.findByText('Testreklamation')
 fireEvent.change(container.querySelector('input[type=file]')!, { target: { files: [new File(['dummy'], 'test.png', { type: 'image/png' })] } })
 expect(await screen.findByRole('alert')).toHaveTextContent(/Hochladen/)
 expect(window.alert).not.toHaveBeenCalled()
})
it.each([['Lieferscheine', LieferantLieferscheinePage], ['Reklamation', LieferantReklamationDetailPage], ['Neue Reklamation', LieferantReklamationCreatePage]] as const)('%s meldet einen Ladefehler sichtbar', async (_label, Page) => {
 vi.mocked(fetch).mockResolvedValue(new Response('{}', { status: 500 }))
 render(<MemoryRouter initialEntries={['/1/1']}><ToastProvider><Routes><Route path="/:lieferantId/:id" element={<Page />} /></Routes></ToastProvider></MemoryRouter>)
 expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent(/geladen|laden/)
})
it('meldet Teilfehler nach dem Anlegen und öffnet den bestehenden Datensatz statt ein Duplikat anzulegen', async () => {
 vi.mocked(fetch).mockImplementation(async (url, options) => new Response(JSON.stringify(options?.method === 'POST' ? (String(url).includes('/bilder') ? {} : { id: 1 }) : []), { status: options?.method === 'POST' && String(url).includes('/bilder') ? 500 : 200 }))
 const user = userEvent.setup()
 const { container } = render(<MemoryRouter initialEntries={['/lieferanten/1']}><ToastProvider><Routes><Route path="/lieferanten/:lieferantId" element={<LieferantReklamationCreatePage />} /><Route path="/reklamationen/:id" element={<div>Bestehende Reklamation</div>} /></Routes></ToastProvider></MemoryRouter>)
 await user.type(screen.getByPlaceholderText('Was ist beschädigt oder fehlt?'), 'Testschaden')
 fireEvent.change(container.querySelector('input[type=file]')!, { target: { files: [new File(['dummy'], 'test.png', { type: 'image/png' })] } })
 await user.click(screen.getByRole('button', { name: 'Speichern' }))
 expect(await screen.findByRole('alert')).toHaveTextContent(/Reklamation.*erstellt.*Bilder/)
 expect(screen.getByText('Bestehende Reklamation')).toBeVisible()
 expect(vi.mocked(fetch).mock.calls.filter(([url, options]) => options?.method === 'POST' && String(url).includes('/lieferant/'))).toHaveLength(1)
})
it('zeigt nach dem Scan die Seite als Bild statt als PDF und stoppt die Kamera', async () => {
 // Auf dem iPhone hat das eingebettete PDF plus weiterlaufende 4K-Kamera die App neu laden lassen – der Prüfen-Schritt war weg.
 vi.mocked(fetch).mockImplementation(async (url) => new Response(JSON.stringify(String(url).includes('/analyze') ? [{ pageRange: '1', analyzeResponse: { dokumentTyp: 'LIEFERSCHEIN', dokumentNummer: 'LS-4711' } }] : String(url).match(/lieferanten\/1\?/) ? { id: 1, lieferantenname: 'Testbetrieb' } : []), { status: 200 }))
 const user = userEvent.setup()
 const { container } = render(<MemoryRouter initialEntries={['/lieferanten/1']}><ToastProvider><Routes><Route path="/lieferanten/:lieferantId" element={<LieferantLieferscheinePage />} /></Routes></ToastProvider></MemoryRouter>)
 await user.click(screen.getByRole('button', { name: 'Scannen' }))
 vi.mocked(releaseCameraStream).mockClear()
 await user.click(screen.getByRole('button', { name: 'Scan fertig' }))
 expect(await screen.findByText('Prüfen & Speichern')).toBeVisible()
 expect(screen.getByDisplayValue('LS-4711')).toBeVisible()
 expect(releaseCameraStream).toHaveBeenCalled()
 expect(container.querySelector('iframe')).toBeNull()
 expect(screen.getByRole('img', { name: 'Gescannter Lieferschein, Seite 1' })).toBeVisible()
})
it('zeigt ein hochgeladenes PDF weiter als PDF-Vorschau', async () => {
 vi.mocked(fetch).mockImplementation(async (url) => new Response(JSON.stringify(String(url).includes('/analyze') ? [{ pageRange: '1', analyzeResponse: { dokumentTyp: 'LIEFERSCHEIN' } }] : []), { status: 200 }))
 const { container } = render(<MemoryRouter initialEntries={['/lieferanten/1']}><ToastProvider><Routes><Route path="/lieferanten/:lieferantId" element={<LieferantLieferscheinePage />} /></Routes></ToastProvider></MemoryRouter>)
 fireEvent.change(container.querySelector('input[type=file]')!, { target: { files: [new File(['%PDF'], 'lieferschein.pdf', { type: 'application/pdf' })] } })
 expect(await screen.findByText('Prüfen & Speichern')).toBeVisible()
 expect(container.querySelector('iframe')).not.toBeNull()
})
