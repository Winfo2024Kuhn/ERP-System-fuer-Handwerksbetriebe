import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from './ui/toast'
import { ConfirmProvider } from './ui/confirm-dialog'
import ScannerModal from './ScannerModal'
import { detectDocumentCorners, detectDocumentCornersLive, istEdgeDetectorBereit, verfeinereEcken } from '../services/DocumentEdgeDetector'
import { erzeugeSeitenBild, schneideZu } from '../services/scanSeite'
import { erstelleScanPdf } from '../services/scanPdf'

// Ein Blatt im verkleinerten Live-Bild (480 px breit) – Dummy-Koordinaten.
const LIVE_BLATT = [
    { x: 100, y: 50 },
    { x: 380, y: 50 },
    { x: 380, y: 220 },
    { x: 100, y: 220 },
]
// Ecken, die die Erkennung im Foto findet (Pixel des Fotos)
const FOTO_BLATT = [
    { x: 200, y: 100 },
    { x: 1700, y: 100 },
    { x: 1700, y: 1000 },
    { x: 200, y: 1000 },
]

const kameraAttrappe = vi.hoisted(() => ({
    fehler: new Error('Permission denied') as Error,
    bildBereit: true,
    // Kodieren des Kamerabilds dauert auf dem Handy etwas – im Test steuerbar
    kodiert: Promise.resolve() as Promise<void>,
}))

vi.mock('./Camera', async () => {
    const { forwardRef, useImperativeHandle } = await import('react')
    const video = { readyState: 4, videoWidth: 1920, videoHeight: 1080 }
    return {
        default: forwardRef<unknown, { onError: (error: Error) => void }>(function KameraAttrappe({ onError }, ref) {
            useImperativeHandle(ref, () => ({
                getScreenshotBlob: async () => {
                    await kameraAttrappe.kodiert
                    return kameraAttrappe.bildBereit ? new Blob(['foto'], { type: 'image/jpeg' }) : null
                },
                video,
            }))
            return <button type="button" onClick={() => onError(kameraAttrappe.fehler)}>Kamerafehler auslösen</button>
        }),
    }
})

vi.mock('../services/DocumentEdgeDetector', () => ({
    preloadEdgeDetector: vi.fn(async () => {}),
    istEdgeDetectorBereit: vi.fn(() => true),
    detectDocumentCorners: vi.fn(),
    detectDocumentCornersLive: vi.fn(),
    verfeinereEcken: vi.fn(async (_quelle: unknown, ecken: unknown) => ecken),
}))

vi.mock('../services/scanSeite', () => ({
    schneideZu: vi.fn(() => document.createElement('canvas')),
    erzeugeSeitenBild: vi.fn(async (_canvas: unknown, filter: string) => new Blob([filter], { type: 'image/jpeg' })),
}))

vi.mock('../services/scanBildHilfen', () => ({
    ladeBild: vi.fn(async () => ({ naturalWidth: 1920, naturalHeight: 1080 })),
    canvasZuBlob: vi.fn(async () => new Blob(['roh'], { type: 'image/jpeg' })),
    blobZuCanvas: vi.fn(async () => document.createElement('canvas')),
    gibCanvasFrei: vi.fn(),
}))

vi.mock('../services/scanPdf', () => ({
    erstelleScanPdf: vi.fn(async () => new File(['%PDF'], 'Scan_test.pdf', { type: 'application/pdf' })),
}))

/** Zeichenfläche als Attrappe: jsdom kann kein Video auf eine Canvas zeichnen. */
function zeichenflaecheAttrappe() {
    const leer = () => {}
    return {
        drawImage: leer, clearRect: leer, beginPath: leer, moveTo: leer, lineTo: leer,
        closePath: leer, fill: leer, stroke: leer, arc: leer,
        fillStyle: '', strokeStyle: '', lineWidth: 1,
    } as unknown as CanvasRenderingContext2D
}

let urlZaehler = 0
beforeEach(() => {
    vi.clearAllMocks()
    kameraAttrappe.fehler = new Error('Permission denied')
    kameraAttrappe.bildBereit = true
    kameraAttrappe.kodiert = Promise.resolve()
    vi.mocked(detectDocumentCornersLive).mockResolvedValue(null)
    vi.mocked(istEdgeDetectorBereit).mockReturnValue(true)
    vi.mocked(detectDocumentCorners).mockResolvedValue(FOTO_BLATT)
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(() => zeichenflaecheAttrappe() as never)
    urlZaehler = 0
    URL.createObjectURL = vi.fn(() => `blob:test-${++urlZaehler}`)
    URL.revokeObjectURL = vi.fn()
})

afterEach(() => {
    vi.restoreAllMocks()
})

function oeffneScanner() {
    const onSave = vi.fn(async () => {})
    const onClose = vi.fn()
    render(<ToastProvider><ConfirmProvider><ScannerModal onClose={onClose} onSave={onSave} /></ConfirmProvider></ToastProvider>)
    return { onSave, onClose, user: userEvent.setup() }
}

/** Auslöser von Hand → Zuschneiden → Übernehmen; danach liegt eine Seite vor. */
async function seiteVonHandScannen(user: ReturnType<typeof userEvent.setup>, anzahlDanach: number) {
    await user.click(screen.getByRole('button', { name: 'Foto von Hand aufnehmen' }))
    fireEvent.load(await screen.findByAltText('Aufgenommenes Foto zum Zuschneiden'))
    expect(await screen.findByText('Ecken erkannt – bei Bedarf verschieben')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Übernehmen' }))
    expect(await screen.findByRole('button', { name: `Fertig (${anzahlDanach})` })).toBeEnabled()
}

it('zeigt Kamerafehler zusätzlich zum Inlinehinweis im eigenen Toast', async () => {
    const { user, onSave } = oeffneScanner()
    await user.click(screen.getByRole('button', { name: 'Kamerafehler auslösen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/Zugriff auf die Kamera/)
    expect(screen.getByText('Kamera nicht verfügbar')).toBeVisible()
    expect(onSave).not.toHaveBeenCalled()
})

it('zeigt bei belegter Kamera einen deutschen Hinweis statt der Browsermeldung', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    kameraAttrappe.fehler = new DOMException('Could not start video source', 'NotReadableError')
    const { user } = oeffneScanner()
    await user.click(screen.getByRole('button', { name: 'Kamerafehler auslösen' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Die Kamera wird gerade von einer anderen App benutzt.')
    expect(screen.queryByText(/Could not start video source/)).not.toBeInTheDocument()
})

describe('Automatische Aufnahme', () => {
    it('nimmt ein ruhig liegendes Blatt auf, bleibt in der Kamera und nimmt dasselbe Blatt nicht noch einmal auf', async () => {
        vi.mocked(detectDocumentCornersLive).mockResolvedValue(LIVE_BLATT)
        oeffneScanner()

        expect(screen.getByRole('button', { name: 'Fertig (0)' })).toBeDisabled()

        const vorschau = await screen.findByRole('button', { name: 'Seiten ansehen und bearbeiten (1 Seite)' }, { timeout: 4000 })
        expect(within(vorschau).getByText('1')).toBeInTheDocument()
        expect(await screen.findByRole('button', { name: 'Fertig (1)' })).toBeEnabled()
        expect(screen.getByText('Seite 2 scannen')).toBeVisible()
        expect(await screen.findByText('Seite 1 erfasst – nächstes Blatt hinlegen')).toBeVisible()

        // Live-Ecken (480 px breit) auf das Foto (1920 px) hochgerechnet und eingerastet
        const erwartet = LIVE_BLATT.map(p => ({ x: p.x * 4, y: p.y * 4 }))
        await waitFor(() => expect(schneideZu).toHaveBeenCalledTimes(1))
        expect(verfeinereEcken).toHaveBeenCalledWith(expect.anything(), erwartet)
        expect(schneideZu).toHaveBeenCalledWith(expect.anything(), erwartet)
        expect(erzeugeSeitenBild).toHaveBeenCalledWith(expect.anything(), 'dokument')
        await waitFor(() => expect(vorschau.querySelector('img')).toHaveAttribute('src', expect.stringMatching(/^blob:/)))

        // Dasselbe Blatt liegt weiter da: keine zweite Aufnahme
        const aufrufeBisher = vi.mocked(detectDocumentCornersLive).mock.calls.length
        await waitFor(() => expect(vi.mocked(detectDocumentCornersLive).mock.calls.length).toBeGreaterThan(aufrufeBisher + 8), { timeout: 4000 })
        expect(schneideZu).toHaveBeenCalledTimes(1)
        expect(screen.getByRole('button', { name: 'Fertig (1)' })).toBeInTheDocument()
    })
})

it('bietet den Auslöser von Hand an, wenn die Automatik nicht geladen werden kann', async () => {
    vi.mocked(istEdgeDetectorBereit).mockReturnValue(false)
    oeffneScanner()
    expect(await screen.findByText('Blatt ins Bild halten und auslösen')).toBeVisible()
    expect(detectDocumentCornersLive).not.toHaveBeenCalled()
})

describe('Automatische Aufnahme ohne Kamerabild', () => {
    it('gibt den Auslöser wieder frei, wenn das Kamerabild noch fehlt, und nimmt dann auf', async () => {
        kameraAttrappe.bildBereit = false
        vi.mocked(detectDocumentCornersLive).mockResolvedValue(LIVE_BLATT)
        oeffneScanner()

        // Mehr als zwei Auslöse-Runden (je 6 ruhige Bilder) ohne Bild: keine Seite
        await waitFor(() => expect(vi.mocked(detectDocumentCornersLive).mock.calls.length).toBeGreaterThan(14), { timeout: 4000 })
        expect(screen.getByRole('button', { name: 'Fertig (0)' })).toBeDisabled()

        kameraAttrappe.bildBereit = true
        expect(await screen.findByRole('button', { name: 'Fertig (1)' }, { timeout: 4000 })).toBeEnabled()
    })
})

describe('Auslöser von Hand', () => {
    it('führt über das Zuschneiden mit erkannten Ecken zurück zur Kamera', async () => {
        const { user } = oeffneScanner()
        await seiteVonHandScannen(user, 1)

        expect(screen.getByText('Seite 2 scannen')).toBeVisible()
        await waitFor(() => expect(schneideZu).toHaveBeenCalledWith(expect.anything(), FOTO_BLATT))
        expect(verfeinereEcken).not.toHaveBeenCalled()
        expect(screen.queryByRole('button', { name: 'Übernehmen' })).not.toBeInTheDocument()
    })

    it('legt kein Foto mehr an, wenn der Scanner während des Speicherns des Kamerabilds schließt', async () => {
        let fertigKodiert: () => void = () => {}
        kameraAttrappe.kodiert = new Promise<void>(resolve => { fertigKodiert = resolve })
        const onClose = vi.fn()
        const { unmount } = render(<ToastProvider><ConfirmProvider><ScannerModal onClose={onClose} onSave={vi.fn()} /></ConfirmProvider></ToastProvider>)
        fireEvent.click(screen.getByRole('button', { name: 'Foto von Hand aufnehmen' }))
        unmount()
        fertigKodiert()
        await new Promise(resolve => setTimeout(resolve, 0))
        expect(URL.createObjectURL).not.toHaveBeenCalled()
    })

    it('verwirft das Foto bei „Zurück“ ohne neue Seite', async () => {
        const { user } = oeffneScanner()
        await user.click(screen.getByRole('button', { name: 'Foto von Hand aufnehmen' }))
        await user.click(await screen.findByRole('button', { name: 'Zurück' }))
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test-1')
        expect(screen.getByRole('button', { name: 'Fertig (0)' })).toBeDisabled()
    })
})

describe('Seitenübersicht', () => {
    it('wechselt den Filter nur für die gezeigte Seite', async () => {
        const { user } = oeffneScanner()
        await seiteVonHandScannen(user, 1)
        await user.click(screen.getByRole('button', { name: /Seiten ansehen und bearbeiten/ }))

        expect(screen.getByText('Seite 1 von 1')).toBeVisible()
        expect(screen.getByRole('button', { name: 'Dokument' })).toHaveAttribute('aria-pressed', 'true')
        await waitFor(() => expect(screen.getByRole('img', { name: 'Seite 1' })).toBeVisible())

        await user.click(screen.getByRole('button', { name: 'Schwarz-Weiß' }))

        expect(screen.getByRole('button', { name: 'Schwarz-Weiß' })).toHaveAttribute('aria-pressed', 'true')
        await waitFor(() => expect(erzeugeSeitenBild).toHaveBeenLastCalledWith(expect.anything(), 'schwarzweiss'))
        expect(schneideZu).toHaveBeenCalledTimes(1)
    })

    it('erklärt die Bildverbesserung im Info-Knopf', async () => {
        const { user } = oeffneScanner()
        await seiteVonHandScannen(user, 1)
        await user.click(screen.getByRole('button', { name: /Seiten ansehen und bearbeiten/ }))
        await user.click(screen.getByRole('button', { name: 'Mehr zu: Bild verbessern' }))
        expect(await screen.findByText('Was passiert im Hintergrund?')).toBeVisible()
        expect(screen.getByText(/das Foto wird dafür nirgends hochgeladen/)).toBeVisible()
        expect(screen.getByText(/die anderen Seiten bleiben, wie sie sind/)).toBeVisible()
    })

    it('blättert mit den Pfeilen zwischen den Seiten', async () => {
        const { user } = oeffneScanner()
        await seiteVonHandScannen(user, 1)
        await seiteVonHandScannen(user, 2)
        await user.click(screen.getByRole('button', { name: /Seiten ansehen und bearbeiten \(2 Seiten\)/ }))

        expect(screen.getByText('Seite 2 von 2')).toBeVisible()
        expect(screen.queryByRole('button', { name: 'Nächste Seite' })).not.toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Vorige Seite' }))
        expect(screen.getByText('Seite 1 von 2')).toBeVisible()
    })

    it('fragt vor dem Löschen nach und kehrt ohne Seiten zur Kamera zurück', async () => {
        const { user } = oeffneScanner()
        await seiteVonHandScannen(user, 1)
        await user.click(screen.getByRole('button', { name: /Seiten ansehen und bearbeiten/ }))

        await user.click(screen.getByRole('button', { name: 'Löschen' }))
        const dialog = await screen.findByRole('dialog', { name: 'Seite löschen?' })
        await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }))
        expect(screen.getByText('Seite 1 von 1')).toBeVisible()

        await user.click(screen.getByRole('button', { name: 'Löschen' }))
        await user.click(within(await screen.findByRole('dialog', { name: 'Seite löschen?' })).getByRole('button', { name: 'Löschen' }))

        expect(await screen.findByText('Seite 1 scannen')).toBeVisible()
        expect(screen.getByRole('button', { name: 'Fertig (0)' })).toBeDisabled()
    })

    it('schneidet eine Seite mit angepassten Ecken neu zu', async () => {
        const { user } = oeffneScanner()
        await seiteVonHandScannen(user, 1)
        await waitFor(() => expect(schneideZu).toHaveBeenCalledTimes(1))
        await user.click(screen.getByRole('button', { name: /Seiten ansehen und bearbeiten/ }))

        await user.click(screen.getByRole('button', { name: 'Ecken anpassen' }))
        fireEvent.load(await screen.findByAltText('Aufgenommenes Foto zum Zuschneiden'))
        expect(screen.getByText('Ecken an die Blattkanten ziehen')).toBeVisible()
        await user.click(screen.getByRole('button', { name: 'Übernehmen' }))

        expect(await screen.findByText('Seite 1 von 1')).toBeVisible()
        await waitFor(() => expect(schneideZu).toHaveBeenCalledTimes(2))
        expect(schneideZu).toHaveBeenLastCalledWith(expect.anything(), FOTO_BLATT)
        expect(detectDocumentCorners).toHaveBeenCalledTimes(1)
    })
})

describe('Fertig', () => {
    it('erzeugt das PDF aus allen Seiten in Reihenfolge, übergibt es und schließt', async () => {
        const { user, onSave, onClose } = oeffneScanner()
        await seiteVonHandScannen(user, 1)
        await seiteVonHandScannen(user, 2)

        await user.click(screen.getByRole('button', { name: 'Fertig (2)' }))

        await waitFor(() => expect(onClose).toHaveBeenCalled())
        const bilder = vi.mocked(erstelleScanPdf).mock.calls[0][0]
        expect(bilder).toHaveLength(2)
        // Maße der zugeschnittenen Seiten (Attrappe: leere Canvas 300 × 150)
        expect(bilder[0]).toEqual({ bild: expect.any(Blob), breite: 300, hoehe: 150 })
        // Zweites Argument: die erste Seite als Bild für eine speicherschonende Vorschau
        expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ name: 'Scan_test.pdf', type: 'application/pdf' }), bilder[0].bild)
    })

    it('sperrt während PDF und Übergabe alle Änderungen und erklärt warum', async () => {
        const { user, onSave, onClose } = oeffneScanner()
        let freigeben: () => void = () => {}
        onSave.mockImplementationOnce(() => new Promise<void>(fertig => { freigeben = fertig }))
        await seiteVonHandScannen(user, 1)
        await user.click(screen.getByRole('button', { name: /Seiten ansehen und bearbeiten/ }))

        await user.click(screen.getByRole('button', { name: 'Fertig (1)' }))

        const pdfKnopf = await screen.findByRole('button', { name: 'PDF wird erstellt…' })
        expect(pdfKnopf).toBeDisabled()
        for (const name of ['Dokument', 'Schwarz-Weiß', 'Original', 'Ecken anpassen', 'Löschen', 'Weiter scannen', 'Scanner schließen']) {
            const knopf = screen.getByRole('button', { name })
            expect(knopf).toBeDisabled()
            expect(knopf).toHaveAttribute('title', 'PDF wird erstellt…')
        }
        // Auch die (verdeckte) Kamera nimmt nichts auf
        expect(screen.getByRole('button', { name: 'Foto von Hand aufnehmen', hidden: true })).toBeDisabled()

        freigeben()
        await waitFor(() => expect(onClose).toHaveBeenCalled())
        expect(erzeugeSeitenBild).toHaveBeenCalledTimes(1)
    })

    it('nimmt eine gerade laufende Aufnahme noch mit ins PDF', async () => {
        const { user, onSave } = oeffneScanner()
        await seiteVonHandScannen(user, 1)
        // Zweite Aufnahme läuft noch (Kamerabild wird gespeichert), da tippt der Nutzer auf „Fertig“
        let fertigKodiert: () => void = () => {}
        kameraAttrappe.kodiert = new Promise<void>(resolve => { fertigKodiert = resolve })
        vi.mocked(detectDocumentCornersLive).mockResolvedValue(LIVE_BLATT)
        await waitFor(() => expect(vi.mocked(detectDocumentCornersLive).mock.calls.length).toBeGreaterThan(8), { timeout: 4000 })
        await user.click(screen.getByRole('button', { name: 'Fertig (1)' }))
        fertigKodiert()
        await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1))
        expect(vi.mocked(erstelleScanPdf).mock.calls[0][0]).toHaveLength(2)
    })

    it('meldet einen Fehler beim Speichern als Toast und bleibt offen', async () => {
        const { user, onSave, onClose } = oeffneScanner()
        onSave.mockRejectedValueOnce(new Error('offline'))
        vi.spyOn(console, 'error').mockImplementation(() => {})
        await seiteVonHandScannen(user, 1)

        await user.click(screen.getByRole('button', { name: 'Fertig (1)' }))

        expect(await screen.findByRole('alert')).toHaveTextContent('Scan konnte nicht gespeichert werden.')
        expect(onClose).not.toHaveBeenCalled()
    })
})

describe('Schließen', () => {
    it('schließt ohne Seiten sofort', async () => {
        const { user, onClose } = oeffneScanner()
        await user.click(screen.getByRole('button', { name: 'Scanner schließen' }))
        expect(onClose).toHaveBeenCalled()
    })

    it('fragt nach, bevor gescannte Seiten verworfen werden', async () => {
        const { user, onClose } = oeffneScanner()
        await seiteVonHandScannen(user, 1)
        await user.click(screen.getByRole('button', { name: 'Scanner schließen' }))
        const dialog = await screen.findByRole('dialog', { name: 'Scan verwerfen?' })
        await user.click(within(dialog).getByRole('button', { name: 'Weiter scannen' }))
        expect(onClose).not.toHaveBeenCalled()

        await user.click(screen.getByRole('button', { name: 'Scanner schließen' }))
        await user.click(within(await screen.findByRole('dialog', { name: 'Scan verwerfen?' })).getByRole('button', { name: 'Verwerfen' }))
        expect(onClose).toHaveBeenCalled()
    })
})
