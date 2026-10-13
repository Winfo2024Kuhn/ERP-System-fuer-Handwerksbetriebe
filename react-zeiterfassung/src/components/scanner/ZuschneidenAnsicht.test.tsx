import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ZuschneidenAnsicht from './ZuschneidenAnsicht'
import { detectDocumentCorners } from '../../services/DocumentEdgeDetector'

vi.mock('../../services/DocumentEdgeDetector', () => ({ detectDocumentCorners: vi.fn() }))

/** jsdom kennt keine Bildmaße: Foto mit 1000 × 2000 Pixeln, angezeigt in 100 × 200 px. */
function ladeFoto() {
    const bild = screen.getByAltText('Aufgenommenes Foto zum Zuschneiden') as HTMLImageElement
    Object.defineProperty(bild, 'naturalWidth', { value: 1000 })
    Object.defineProperty(bild, 'naturalHeight', { value: 2000 })
    bild.getBoundingClientRect = () => ({ left: 0, top: 0, width: 100, height: 200, right: 100, bottom: 200, x: 0, y: 0, toJSON: () => ({}) })
    fireEvent.load(bild)
}

beforeEach(() => vi.clearAllMocks())

describe('ZuschneidenAnsicht', () => {
    it('setzt Standard-Ecken mit 10 % Rand, wenn die Erkennung kein Blatt findet', async () => {
        vi.mocked(detectDocumentCorners).mockResolvedValue(null)
        const onUebernehmen = vi.fn()
        render(<ZuschneidenAnsicht fotoUrl="blob:foto" startEcken={null} onUebernehmen={onUebernehmen} onZurueck={vi.fn()} />)
        ladeFoto()

        expect(await screen.findByText('Ecken an die Blattkanten ziehen')).toBeVisible()
        await userEvent.click(screen.getByRole('button', { name: 'Übernehmen' }))
        expect(onUebernehmen).toHaveBeenCalledWith([
            { x: 100, y: 200 }, { x: 900, y: 200 }, { x: 900, y: 1800 }, { x: 100, y: 1800 },
        ])
    })

    it('zieht eine Ecke mit dem Finger, zeigt dabei die Lupe und überschreibt sie nicht mit der späten Erkennung', async () => {
        let erkennungFertig: (ecken: null) => void = () => {}
        vi.mocked(detectDocumentCorners).mockReturnValue(new Promise(fertig => { erkennungFertig = fertig }))
        const onUebernehmen = vi.fn()
        render(<ZuschneidenAnsicht fotoUrl="blob:foto" startEcken={null} onUebernehmen={onUebernehmen} onZurueck={vi.fn()} />)
        ladeFoto()
        expect(screen.getByText('Blattkanten werden gesucht…')).toBeVisible()

        fireEvent.touchStart(screen.getByTestId('ecke-0'))
        fireEvent.touchMove(screen.getByTestId('ecke-0'), { touches: [{ clientX: 20, clientY: 30 }] })
        expect(screen.getByTestId('lupe')).toBeInTheDocument()
        fireEvent.touchEnd(screen.getByTestId('ecke-0'))
        expect(screen.queryByTestId('lupe')).not.toBeInTheDocument()

        erkennungFertig(null)
        expect(await screen.findByText('Ecken an die Blattkanten ziehen')).toBeVisible()
        await userEvent.click(screen.getByRole('button', { name: 'Übernehmen' }))
        expect(onUebernehmen.mock.calls[0][0][0]).toEqual({ x: 200, y: 300 })
    })

    it('zieht auch mit der Maus und begrenzt die Ecke auf das Foto', async () => {
        vi.mocked(detectDocumentCorners).mockResolvedValue(null)
        const onUebernehmen = vi.fn()
        render(<ZuschneidenAnsicht fotoUrl="blob:foto" startEcken={null} onUebernehmen={onUebernehmen} onZurueck={vi.fn()} />)
        ladeFoto()
        await screen.findByText('Ecken an die Blattkanten ziehen')

        fireEvent.mouseDown(screen.getByTestId('ecke-1'))
        fireEvent.mouseMove(screen.getByTestId('ecke-1'), { clientX: 500, clientY: -50 })
        fireEvent.mouseUp(screen.getByTestId('ecke-1'))
        await userEvent.click(screen.getByRole('button', { name: 'Übernehmen' }))
        expect(onUebernehmen.mock.calls[0][0][1]).toEqual({ x: 1000, y: 0 })
    })

    it('startet mit den gespeicherten Ecken einer Seite ohne neue Erkennung', async () => {
        const ecken = [{ x: 100, y: 200 }, { x: 800, y: 210 }, { x: 820, y: 1700 }, { x: 90, y: 1690 }]
        const onUebernehmen = vi.fn()
        const onZurueck = vi.fn()
        render(<ZuschneidenAnsicht fotoUrl="blob:foto" startEcken={ecken} onUebernehmen={onUebernehmen} onZurueck={onZurueck} />)
        ladeFoto()

        await userEvent.click(screen.getByRole('button', { name: 'Übernehmen' }))
        expect(onUebernehmen).toHaveBeenCalledWith(ecken)
        expect(detectDocumentCorners).not.toHaveBeenCalled()
        await userEvent.click(screen.getByRole('button', { name: 'Zurück' }))
        expect(onZurueck).toHaveBeenCalled()
    })

    it('sperrt „Übernehmen“ bei überkreuzten Ecken und erklärt warum', async () => {
        const ueberkreuzt = [{ x: 100, y: 200 }, { x: 820, y: 1700 }, { x: 800, y: 210 }, { x: 90, y: 1690 }]
        render(<ZuschneidenAnsicht fotoUrl="blob:foto" startEcken={ueberkreuzt} onUebernehmen={vi.fn()} onZurueck={vi.fn()} />)
        ladeFoto()

        const hinweis = await screen.findByText('Ecken überkreuzen sich – bitte so ziehen, dass ein Viereck entsteht')
        expect(hinweis.closest('[aria-live="polite"]')).not.toBeNull()
        const knopf = screen.getByRole('button', { name: 'Übernehmen' })
        expect(knopf).toBeDisabled()
        expect(knopf).toHaveAccessibleDescription(/Ecken überkreuzen sich/)
    })

    it('sperrt „Übernehmen“, wenn der Ausschnitt winzig ist', async () => {
        const winzig = [{ x: 100, y: 100 }, { x: 110, y: 100 }, { x: 110, y: 110 }, { x: 100, y: 110 }]
        render(<ZuschneidenAnsicht fotoUrl="blob:foto" startEcken={winzig} onUebernehmen={vi.fn()} onZurueck={vi.fn()} />)
        ladeFoto()
        expect(await screen.findByText(/Ausschnitt ist zu klein/)).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Übernehmen' })).toBeDisabled()
    })
})
