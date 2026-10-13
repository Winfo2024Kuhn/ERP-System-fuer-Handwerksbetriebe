import { afterEach, describe, expect, it, vi } from 'vitest'
import { blobZuCanvas, canvasZuBlob, gibCanvasFrei, ladeBild } from './scanBildHilfen'

/** Ersatz für `Image`: jsdom lädt keine Bilder. Eine URL mit „kaputt“ schlägt fehl. */
class TestBild {
    onload: (() => void) | null = null
    onerror: (() => void) | null = null
    naturalWidth = 40
    naturalHeight = 30
    set src(wert: string) {
        queueMicrotask(() => (wert.includes('kaputt') ? this.onerror?.() : this.onload?.()))
    }
}

afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
})

describe('ladeBild', () => {
    it('liefert das Bild, sobald es geladen ist', async () => {
        vi.stubGlobal('Image', TestBild)
        const bild = await ladeBild('blob:seite-1')
        expect(bild.naturalWidth).toBe(40)
    })

    it('meldet einen verständlichen Fehler, wenn das Bild nicht lädt', async () => {
        vi.stubGlobal('Image', TestBild)
        await expect(ladeBild('blob:kaputt')).rejects.toThrow('Bild konnte nicht geladen werden.')
    })
})

describe('canvasZuBlob', () => {
    it('speichert die Canvas als JPEG mit der gewünschten Qualität', async () => {
        const blob = new Blob(['x'], { type: 'image/jpeg' })
        const toBlob = vi.fn((fertig: BlobCallback) => fertig(blob))
        const canvas = { toBlob } as unknown as HTMLCanvasElement
        await expect(canvasZuBlob(canvas, 0.8)).resolves.toBe(blob)
        expect(toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', 0.8)
    })

    it('nutzt 0,95 als Standard-Qualität', async () => {
        const toBlob = vi.fn((fertig: BlobCallback) => fertig(new Blob(['x'])))
        await canvasZuBlob({ toBlob } as unknown as HTMLCanvasElement)
        expect(toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', 0.95)
    })

    it('meldet einen Fehler, wenn der Browser kein Bild liefert', async () => {
        const canvas = { toBlob: (fertig: BlobCallback) => fertig(null) } as unknown as HTMLCanvasElement
        await expect(canvasZuBlob(canvas)).rejects.toThrow('Bild konnte nicht gespeichert werden.')
    })
})

describe('blobZuCanvas', () => {
    it('zeichnet das Bild in eine Canvas in Originalgröße und gibt die URL wieder frei', async () => {
        vi.stubGlobal('Image', TestBild)
        const erzeugen = vi.fn(() => 'blob:roh')
        const freigeben = vi.fn()
        vi.stubGlobal('URL', { ...URL, createObjectURL: erzeugen, revokeObjectURL: freigeben })
        const drawImage = vi.fn()
        vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage } as unknown as CanvasRenderingContext2D)

        const canvas = await blobZuCanvas(new Blob(['x']))

        expect(canvas.width).toBe(40)
        expect(canvas.height).toBe(30)
        expect(drawImage).toHaveBeenCalledWith(expect.any(TestBild), 0, 0)
        expect(freigeben).toHaveBeenCalledWith('blob:roh')
    })

    it('meldet einen Fehler ohne Zeichenfläche und gibt die URL trotzdem frei', async () => {
        vi.stubGlobal('Image', TestBild)
        const freigeben = vi.fn()
        vi.stubGlobal('URL', { ...URL, createObjectURL: () => 'blob:roh', revokeObjectURL: freigeben })
        vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)

        await expect(blobZuCanvas(new Blob(['x']))).rejects.toThrow('Bild konnte nicht gelesen werden.')
        expect(freigeben).toHaveBeenCalledWith('blob:roh')
    })

    it('gibt die URL auch frei, wenn das Bild nicht lädt', async () => {
        vi.stubGlobal('Image', TestBild)
        const freigeben = vi.fn()
        vi.stubGlobal('URL', { ...URL, createObjectURL: () => 'blob:kaputt', revokeObjectURL: freigeben })
        await expect(blobZuCanvas(new Blob(['x']))).rejects.toThrow()
        expect(freigeben).toHaveBeenCalledWith('blob:kaputt')
    })
})

describe('gibCanvasFrei', () => {
    it('setzt die Canvas auf 0 × 0 Pixel', () => {
        const canvas = document.createElement('canvas')
        canvas.width = 300
        canvas.height = 200
        gibCanvasFrei(canvas)
        expect(canvas.width).toBe(0)
        expect(canvas.height).toBe(0)
    })
})
