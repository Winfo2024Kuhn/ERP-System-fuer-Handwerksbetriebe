import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const scanDocument = vi.fn()
vi.mock('scanic', () => ({ scanDocument }))

type Modul = typeof import('./DocumentEdgeDetector')

/** Frisches Modul pro Test: Ladezustand (KI ja/nein) ist modulweit. */
async function ladeModul(): Promise<Modul> {
    vi.resetModules()
    return import('./DocumentEdgeDetector')
}

const ECKEN = {
    topLeft: { x: 40, y: 50 },
    topRight: { x: 420, y: 60 },
    bottomRight: { x: 410, y: 600 },
    bottomLeft: { x: 30, y: 590 },
}

function canvas(breite = 480, hoehe = 640): HTMLCanvasElement {
    const c = document.createElement('canvas')
    c.width = breite
    c.height = hoehe
    return c
}

/** jsdom hat kein Canvas-Zeichnen: Kontext mit einfarbigem Bild nachbauen. */
function canvasZeichnenNachbauen() {
    return vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(function (this: HTMLCanvasElement) {
        const { width, height } = this
        return {
            drawImage: vi.fn(),
            getImageData: () => ({ data: new Uint8ClampedArray(width * height * 4).fill(128), width, height }),
        } as unknown as CanvasRenderingContext2D
    } as never)
}

describe('DocumentEdgeDetector', () => {
    beforeEach(() => {
        scanDocument.mockReset()
        vi.spyOn(console, 'warn').mockImplementation(() => {})
    })
    afterEach(() => vi.restoreAllMocks())

    it('ist erst nach dem Laden bereit und liefert vorher nichts', async () => {
        const m = await ladeModul()
        expect(m.istEdgeDetectorBereit()).toBe(false)
        expect(await m.detectDocumentCornersLive(canvas())).toBeNull()
        scanDocument.mockResolvedValue({ success: false, score: 0.1, corners: null })
        await m.preloadEdgeDetector()
        expect(m.istEdgeDetectorBereit()).toBe(true)
    })

    it('nutzt die selbst ausgelieferte KI-Erkennung mit Mindest-Sicherheit', async () => {
        const m = await ladeModul()
        scanDocument.mockResolvedValue({ success: true, score: 0.95, corners: ECKEN })
        await m.preloadEdgeDetector()
        const ecken = await m.detectDocumentCornersLive(canvas())
        expect(ecken).toEqual([ECKEN.topLeft, ECKEN.topRight, ECKEN.bottomRight, ECKEN.bottomLeft])
        const optionen = scanDocument.mock.calls.at(-1)![1]
        expect(optionen.detector).toBe('ml')
        expect(optionen.ml.minScore).toBe(0.6)
        expect(optionen.ml.modelUrl).toMatch(/doccornernet_lean\.ort/)
        expect(optionen.ml.wasmPaths).toEqual({
            mjs: expect.stringMatching(/ort-wasm-simd-threaded\.mjs/),
            wasm: expect.stringMatching(/ort-wasm-simd-threaded\.wasm/),
        })
        expect(optionen.ml.modelUrl).not.toMatch(/^https?:/)
    })

    it('rät nicht, wenn die KI sicher kein Blatt sieht', async () => {
        const m = await ladeModul()
        scanDocument.mockResolvedValue({ success: false, score: 0.2, corners: ECKEN })
        await m.preloadEdgeDetector()
        scanDocument.mockClear()
        expect(await m.detectDocumentCornersLive(canvas())).toBeNull()
        expect(scanDocument).toHaveBeenCalledTimes(1)
    })

    it('fällt auf die klassische Erkennung zurück, wenn die KI nicht startet', async () => {
        const m = await ladeModul()
        scanDocument.mockImplementation(async (_bild, optionen) => {
            if (optionen.detector === 'ml') throw new Error('kein WASM')
            return { success: true, corners: ECKEN }
        })
        await m.preloadEdgeDetector()
        scanDocument.mockClear()
        const ecken = await m.detectDocumentCornersLive(canvas())
        expect(ecken).toHaveLength(4)
        expect(scanDocument).toHaveBeenCalledTimes(1)
        expect(scanDocument.mock.calls[0][1].detector).toBe('classical')
    })

    it('fällt pro Bild auf die klassische Erkennung zurück, wenn die KI dabei abbricht', async () => {
        const m = await ladeModul()
        scanDocument.mockResolvedValueOnce({ success: false, score: 0.1, corners: null })
        await m.preloadEdgeDetector()
        scanDocument
            .mockRejectedValueOnce(new Error('Speicher voll'))
            .mockResolvedValueOnce({ success: true, corners: ECKEN })
        expect(await m.detectDocumentCornersLive(canvas())).toHaveLength(4)
    })

    it('verwirft zu kleine Vierecke im Live-Bild', async () => {
        const m = await ladeModul()
        const klein = {
            topLeft: { x: 10, y: 10 }, topRight: { x: 60, y: 10 },
            bottomRight: { x: 60, y: 60 }, bottomLeft: { x: 10, y: 60 },
        }
        scanDocument.mockResolvedValue({ success: true, score: 0.9, corners: klein })
        await m.preloadEdgeDetector()
        expect(await m.detectDocumentCornersLive(canvas())).toBeNull()
    })

    it('rechnet die Ecken nach dem Foto auf die volle Auflösung hoch', async () => {
        canvasZeichnenNachbauen()
        const m = await ladeModul()
        // Arbeitsbild: 3200 × 2400 → 1600 × 1200, also Faktor 0,5
        scanDocument.mockImplementation(async (bild: HTMLCanvasElement) => ({
            success: true,
            score: 0.9,
            corners: {
                topLeft: { x: 100, y: 100 }, topRight: { x: bild.width - 100, y: 100 },
                bottomRight: { x: bild.width - 100, y: bild.height - 100 }, bottomLeft: { x: 100, y: bild.height - 100 },
            },
        }))
        const ecken = await m.detectDocumentCorners(canvas(3200, 2400))
        expect(ecken).toEqual([
            { x: 200, y: 200 }, { x: 3000, y: 200 }, { x: 3000, y: 2200 }, { x: 200, y: 2200 },
        ])
    })

    it('liefert nach dem Foto null, wenn nichts erkannt wird oder das Bild leer ist', async () => {
        canvasZeichnenNachbauen()
        const m = await ladeModul()
        scanDocument.mockResolvedValue({ success: false, score: 0.1, corners: null })
        expect(await m.detectDocumentCorners(canvas(800, 600))).toBeNull()
        expect(await m.detectDocumentCorners(canvas(0, 0))).toBeNull()
    })

    it('verfeinereEcken gibt bei einfarbigem Bild die Eingabe zurück', async () => {
        canvasZeichnenNachbauen()
        const m = await ladeModul()
        const ecken = [{ x: 100, y: 100 }, { x: 700, y: 100 }, { x: 700, y: 500 }, { x: 100, y: 500 }]
        expect(await m.verfeinereEcken(canvas(800, 600), ecken)).toEqual(ecken)
        // ohne Canvas-Unterstützung ebenfalls die Eingabe
        vi.restoreAllMocks()
        vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
        expect(await m.verfeinereEcken(canvas(800, 600), ecken)).toBe(ecken)
    })

    it('meldet „bereit“ erst nach dem Probelauf der KI', async () => {
        const m = await ladeModul()
        let probeFertig: (wert: unknown) => void = () => {}
        scanDocument.mockReturnValueOnce(new Promise(resolve => { probeFertig = resolve }))
        const laden = m.preloadEdgeDetector()
        await vi.waitFor(() => expect(scanDocument).toHaveBeenCalledTimes(1))
        expect(m.istEdgeDetectorBereit()).toBe(false)
        probeFertig({ success: false, score: 0.1, corners: null })
        await laden
        expect(m.istEdgeDetectorBereit()).toBe(true)
    })

    it('lässt nie zwei Erkennungen gleichzeitig auf das Modell los', async () => {
        const m = await ladeModul()
        scanDocument.mockResolvedValueOnce({ success: false, score: 0.1, corners: null })
        await m.preloadEdgeDetector()
        let gleichzeitig = 0
        let hoechstens = 0
        scanDocument.mockImplementation(async () => {
            gleichzeitig++
            hoechstens = Math.max(hoechstens, gleichzeitig)
            await new Promise(resolve => setTimeout(resolve, 5))
            gleichzeitig--
            return { success: true, score: 0.9, corners: ECKEN }
        })
        const ergebnisse = await Promise.all([
            m.detectDocumentCornersLive(canvas()),
            m.detectDocumentCornersLive(canvas()),
            m.detectDocumentCornersLive(canvas()),
        ])
        expect(ergebnisse.every(e => e?.length === 4)).toBe(true)
        expect(hoechstens).toBe(1)
    })

    it('versucht die KI nach einer Pause erneut, wenn sie anfangs nicht startete', async () => {
        const jetzt = vi.spyOn(Date, 'now').mockReturnValue(1_000_000)
        const m = await ladeModul()
        scanDocument.mockRejectedValueOnce(new Error('offline'))
        await m.preloadEdgeDetector()
        // innerhalb der Pause: nur klassisch
        scanDocument.mockResolvedValue({ success: true, score: 0.9, corners: ECKEN })
        await m.detectDocumentCornersLive(canvas())
        expect(scanDocument.mock.calls.slice(1).map(c => c[1].detector)).toEqual(['classical'])
        // nach der Pause: Probelauf, dann KI
        scanDocument.mockClear()
        jetzt.mockReturnValue(1_000_000 + 61_000)
        await m.detectDocumentCornersLive(canvas())
        expect(scanDocument.mock.calls.map(c => c[1].detector)).toEqual(['ml', 'ml'])
    })

    it('meldet einen Ladefehler nicht nach außen und versucht es später erneut', async () => {
        vi.doMock('scanic', () => { throw new Error('Netz weg') })
        const m = await ladeModul()
        await expect(m.preloadEdgeDetector()).resolves.toBeUndefined()
        expect(m.istEdgeDetectorBereit()).toBe(false)
        vi.doMock('scanic', () => ({ scanDocument }))
    })
})

describe('ordneEcken', () => {
    it('sortiert beliebig gemischte Ecken nach TL, TR, BR, BL', async () => {
        const { ordneEcken } = await ladeModul()
        const tl = { x: 10, y: 12 }, tr = { x: 300, y: 5 }, br = { x: 310, y: 400 }, bl = { x: 0, y: 390 }
        expect(ordneEcken([br, tl, bl, tr])).toEqual([tl, tr, br, bl])
        expect(ordneEcken([bl, br, tr, tl])).toEqual([tl, tr, br, bl])
    })
})

describe('viereckFlaeche', () => {
    it('berechnet die Fläche unabhängig von der Umlaufrichtung', async () => {
        const { viereckFlaeche } = await ladeModul()
        const quadrat = [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 }]
        expect(viereckFlaeche(quadrat)).toBe(100)
        expect(viereckFlaeche([...quadrat].reverse())).toBe(100)
    })
})
