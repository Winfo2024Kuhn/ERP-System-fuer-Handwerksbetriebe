import { act, renderHook, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../ui/toast'
import { useScanSeiten } from './useScanSeiten'
import { verfeinereEcken } from '../../services/DocumentEdgeDetector'
import { erzeugeSeitenBild, schneideZu } from '../../services/scanSeite'
import { blobZuCanvas, gibCanvasFrei, ladeBild } from '../../services/scanBildHilfen'

const ECKEN = [
    { x: 10, y: 10 },
    { x: 90, y: 10 },
    { x: 90, y: 140 },
    { x: 10, y: 140 },
]

vi.mock('../../services/DocumentEdgeDetector', () => ({
    verfeinereEcken: vi.fn(async (_quelle: unknown, ecken: unknown) => ecken),
}))
vi.mock('../../services/scanSeite', () => ({
    schneideZu: vi.fn(() => document.createElement('canvas')),
    erzeugeSeitenBild: vi.fn(async (_canvas: unknown, filter: string) => new Blob([filter], { type: 'image/jpeg' })),
}))
vi.mock('../../services/scanBildHilfen', () => ({
    ladeBild: vi.fn(async () => ({ naturalWidth: 200, naturalHeight: 300 })),
    canvasZuBlob: vi.fn(async () => new Blob(['roh'], { type: 'image/jpeg' })),
    blobZuCanvas: vi.fn(async () => document.createElement('canvas')),
    gibCanvasFrei: vi.fn(),
}))

const mitToasts = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>

let urlZaehler = 0
beforeEach(() => {
    vi.clearAllMocks()
    urlZaehler = 0
    URL.createObjectURL = vi.fn(() => `blob:bild-${++urlZaehler}`)
    URL.revokeObjectURL = vi.fn()
})

async function fertigeSeite() {
    const hook = renderHook(() => useScanSeiten(), { wrapper: mitToasts })
    act(() => { hook.result.current.fuegeSeiteHinzu({ fotoUrl: 'blob:foto-1', ecken: ECKEN, verfeinern: false }) })
    await waitFor(() => expect(hook.result.current.seiten[0].status).toBe('fertig'))
    return hook
}

describe('useScanSeiten', () => {
    it('legt eine Seite an, schneidet sie im Hintergrund zu und verbessert sie mit „Dokument“', async () => {
        const { result } = renderHook(() => useScanSeiten(), { wrapper: mitToasts })
        act(() => { result.current.fuegeSeiteHinzu({ fotoUrl: 'blob:foto-1', ecken: ECKEN, verfeinern: false }) })

        expect(result.current.seiten[0]).toMatchObject({ status: 'arbeitet', filter: 'dokument', vorschauUrl: null })
        await waitFor(() => expect(result.current.seiten[0].status).toBe('fertig'))
        expect(result.current.seiten[0]).toMatchObject({ ecken: ECKEN, vorschauUrl: 'blob:bild-1', breite: 300, hoehe: 150 })
        expect(result.current.seiten[0].roh).toBeInstanceOf(Blob)
        expect(erzeugeSeitenBild).toHaveBeenCalledWith(expect.anything(), 'dokument')
        expect(gibCanvasFrei).toHaveBeenCalled()
        expect(verfeinereEcken).not.toHaveBeenCalled()
    })

    it('rechnet Live-Ecken auf das Foto hoch und rastet sie ein', async () => {
        const { result } = renderHook(() => useScanSeiten(), { wrapper: mitToasts })
        act(() => {
            result.current.fuegeSeiteHinzu({ fotoUrl: 'blob:foto-1', ecken: ECKEN, eckenQuelle: { breite: 100, hoehe: 150 }, verfeinern: true })
        })
        await waitFor(() => expect(result.current.seiten[0].status).toBe('fertig'))
        const hochgerechnet = ECKEN.map(p => ({ x: p.x * 2, y: p.y * 2 }))
        expect(verfeinereEcken).toHaveBeenCalledWith(expect.anything(), hochgerechnet)
        expect(schneideZu).toHaveBeenCalledWith(expect.anything(), hochgerechnet)
    })

    it('nimmt die groben Ecken, wenn das Einrasten scheitert', async () => {
        vi.mocked(verfeinereEcken).mockRejectedValueOnce(new Error('kein Modell'))
        const { result } = renderHook(() => useScanSeiten(), { wrapper: mitToasts })
        act(() => { result.current.fuegeSeiteHinzu({ fotoUrl: 'blob:foto-1', ecken: ECKEN, verfeinern: true }) })
        await waitFor(() => expect(result.current.seiten[0].status).toBe('fertig'))
        expect(schneideZu).toHaveBeenCalledWith(expect.anything(), ECKEN)
    })

    it('wechselt den Filter aus dem ungefilterten Zwischenbild, ohne neu zuzuschneiden', async () => {
        const { result } = await fertigeSeite()
        const id = result.current.seiten[0].id

        act(() => { result.current.setzeFilter(id, 'original') })
        expect(result.current.seiten[0]).toMatchObject({ filter: 'original', status: 'arbeitet' })
        await waitFor(() => expect(result.current.seiten[0].status).toBe('fertig'))

        expect(blobZuCanvas).toHaveBeenCalledWith(result.current.seiten[0].roh)
        expect(erzeugeSeitenBild).toHaveBeenLastCalledWith(expect.anything(), 'original')
        expect(schneideZu).toHaveBeenCalledTimes(1)
        expect(result.current.seiten[0].vorschauUrl).toBe('blob:bild-2')
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:bild-1')
    })

    it('ignoriert denselben Filter noch einmal', async () => {
        const { result } = await fertigeSeite()
        act(() => { result.current.setzeFilter(result.current.seiten[0].id, 'dokument') })
        expect(result.current.seiten[0].status).toBe('fertig')
        expect(erzeugeSeitenBild).toHaveBeenCalledTimes(1)
    })

    it('übernimmt einen Filterwechsel während der ersten Verarbeitung und verwirft das veraltete Bild', async () => {
        const { result } = renderHook(() => useScanSeiten(), { wrapper: mitToasts })
        act(() => { result.current.fuegeSeiteHinzu({ fotoUrl: 'blob:foto-1', ecken: ECKEN, verfeinern: false }) })
        act(() => { result.current.setzeFilter(result.current.seiten[0].id, 'schwarzweiss') })

        await waitFor(() => expect(result.current.seiten[0].status).toBe('fertig'))
        expect(erzeugeSeitenBild).toHaveBeenNthCalledWith(1, expect.anything(), 'schwarzweiss')
        expect(result.current.seiten[0].filter).toBe('schwarzweiss')
        expect(result.current.seiten[0].roh).toBeInstanceOf(Blob)
        // Nur das aktuelle Bild bekam eine Vorschau-URL
        expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    })

    it('schneidet nach „Ecken anpassen“ mit den neuen Ecken und dem gewählten Filter neu zu', async () => {
        const { result } = await fertigeSeite()
        const id = result.current.seiten[0].id
        act(() => { result.current.setzeFilter(id, 'original') })
        await waitFor(() => expect(result.current.seiten[0].status).toBe('fertig'))

        const neu = ECKEN.map(p => ({ x: p.x + 5, y: p.y }))
        act(() => { result.current.passeEckenAn(id, neu) })
        await waitFor(() => expect(result.current.seiten[0].status).toBe('fertig'))

        expect(schneideZu).toHaveBeenLastCalledWith(expect.anything(), neu)
        expect(erzeugeSeitenBild).toHaveBeenLastCalledWith(expect.anything(), 'original')
        expect(result.current.seiten[0].ecken).toEqual(neu)
    })

    it('meldet eine fehlgeschlagene Seite als Toast und markiert sie', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {})
        vi.mocked(ladeBild).mockRejectedValueOnce(new Error('kaputt'))
        const { result } = renderHook(() => useScanSeiten(), { wrapper: mitToasts })
        act(() => { result.current.fuegeSeiteHinzu({ fotoUrl: 'blob:foto-1', ecken: ECKEN, verfeinern: false }) })

        await waitFor(() => expect(result.current.seiten[0].status).toBe('fehler'))
        expect(await screen.findByRole('alert')).toHaveTextContent('Seite konnte nicht verbessert werden.')
    })

    it('meldet einen Filterwechsel ohne Zuschnitt als Fehler', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {})
        vi.mocked(ladeBild).mockRejectedValueOnce(new Error('kaputt'))
        const { result } = renderHook(() => useScanSeiten(), { wrapper: mitToasts })
        act(() => { result.current.fuegeSeiteHinzu({ fotoUrl: 'blob:foto-1', ecken: ECKEN, verfeinern: false }) })
        await waitFor(() => expect(result.current.seiten[0].status).toBe('fehler'))

        act(() => { result.current.setzeFilter(result.current.seiten[0].id, 'original') })
        await waitFor(() => expect(result.current.seiten[0].status).toBe('fehler'))
        expect(schneideZu).not.toHaveBeenCalled()
    })

    it('schneidet beim Filterwechsel neu zu, wenn nur die Ecken bekannt sind', async () => {
        const { result } = await fertigeSeite()
        const id = result.current.seiten[0].id
        // Zwischenbild fehlt (z. B. Speichern schlug fehl), Ecken sind bekannt
        result.current.seiten[0].roh = null
        act(() => { result.current.setzeFilter(id, 'schwarzweiss') })
        await waitFor(() => expect(result.current.seiten[0].status).toBe('fertig'))
        expect(schneideZu).toHaveBeenCalledTimes(2)
        expect(erzeugeSeitenBild).toHaveBeenLastCalledWith(expect.anything(), 'schwarzweiss')
    })

    it('gibt beim Löschen Foto und Vorschau frei und verwirft laufende Arbeit', async () => {
        const { result } = await fertigeSeite()
        const id = result.current.seiten[0].id
        act(() => { result.current.setzeFilter(id, 'original') })
        act(() => { result.current.loescheSeite(id) })

        expect(result.current.seiten).toEqual([])
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:foto-1')
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:bild-1')
        expect(await result.current.warteAufAlle()).toEqual([])
        expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
        act(() => { result.current.loescheSeite('gibt-es-nicht') })
    })

    it('wartet auf alle Seiten, auch auf später eingereihte', async () => {
        const { result } = renderHook(() => useScanSeiten(), { wrapper: mitToasts })
        act(() => {
            result.current.fuegeSeiteHinzu({ fotoUrl: 'blob:foto-1', ecken: ECKEN, verfeinern: false })
            result.current.fuegeSeiteHinzu({ fotoUrl: 'blob:foto-2', ecken: ECKEN, verfeinern: false })
        })
        let alle: Awaited<ReturnType<typeof result.current.warteAufAlle>> = []
        await act(async () => { alle = await result.current.warteAufAlle() })
        expect(alle.map(s => s.status)).toEqual(['fertig', 'fertig'])
    })

    it('gibt beim Schließen alle Bilder frei', async () => {
        const { result, unmount } = await fertigeSeite()
        expect(result.current.seiten).toHaveLength(1)
        unmount()
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:foto-1')
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:bild-1')
    })
})
