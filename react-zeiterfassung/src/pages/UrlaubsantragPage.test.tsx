import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import UrlaubsantragPage from './UrlaubsantragPage'
import AbwesenheitenPage from './AbwesenheitenPage'
beforeEach(() => vi.stubGlobal('fetch', vi.fn(async url => new Response(JSON.stringify(String(url).includes('resturlaub') ? { verbleibend: 20 } : []), { status: 200 }))))
afterEach(() => vi.unstubAllGlobals())
it('wählt eigene Kalenderdaten und verhindert ein Ende vor dem Beginn', async () => {
 const user = userEvent.setup()
 render(<MemoryRouter><ToastProvider><UrlaubsantragPage mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ToastProvider></MemoryRouter>)
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
 render(<MemoryRouter><ToastProvider><Page mitarbeiter={{ id: 1, name: 'Max Mustermann' }} /></ToastProvider></MemoryRouter>)
 expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent(/geladen|laden/)
})
