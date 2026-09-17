import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { ToastProvider } from '../components/ui/toast'
import LieferantReklamationCreatePage from './LieferantReklamationCreatePage'

vi.mock('../services/audioRecorderService', async () => {
    const echt = await vi.importActual<typeof import('../services/audioRecorderService')>('../services/audioRecorderService')
    return { ...echt, mikrofonWirdUnterstuetzt: () => true }
})

beforeEach(() => vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 1, name: 'Muster Stahlhandel', lieferscheine: [] }), { status: 200 }))))
afterEach(() => vi.unstubAllGlobals())

it('bietet an der Problembeschreibung einen Diktier-Knopf an', async () => {
    render(
        <MemoryRouter initialEntries={['/lieferanten/1/reklamation/neu']}>
            <ToastProvider>
                <Routes>
                    <Route path="/lieferanten/:lieferantId/reklamation/neu" element={<LieferantReklamationCreatePage />} />
                </Routes>
            </ToastProvider>
        </MemoryRouter>,
    )

    // Der feldName landet im aria-label — so ist belegt, dass die Seite ihn
    // durchreicht und nicht den Standardwert "Text" stehen laesst.
    expect(await screen.findByRole('button', { name: 'Problembeschreibung diktieren' })).toBeInTheDocument()
})
