/**
 * Erkennt die vier Ecken eines Dokuments im Kamerabild.
 *
 * 1. Ein kleines neuronales Netz (scanic, DocCornerNet, ~1,9 MB) schätzt die
 *    Ecken – robust auch bei viel Licht, schwachem Kontrast und unruhigem
 *    Hintergrund. Startet es nicht (sehr alte Geräte), übernimmt die
 *    klassische Kantensuche von scanic.
 * 2. `rasteKantenEin` legt jede Seite an die echte Blattkante.
 *
 * Modell und Laufzeit (WASM) werden selbst ausgeliefert (Vite-Assets aus
 * `scanic-ml`) und vom Service-Worker vorgehalten: kein CDN, keine Daten an
 * Dritte, offline nutzbar. Das Bild verlässt das Gerät nie.
 */
import type { CornerPoints, DetectionOptions, ScannerResult } from 'scanic'
import modellUrl from 'scanic-ml/dist/doccornernet_lean.ort?url'
import ortMjsUrl from 'scanic-ml/dist/ort-wasm-simd-threaded.mjs?url'
import ortWasmUrl from 'scanic-ml/dist/ort-wasm-simd-threaded.wasm?url'
import type { Point } from './scannerOverlay'
import { rasteKantenEin, zuGraubild } from './kantenEinrasten'
import { gibCanvasFrei } from './scanBildHilfen'

export type { Point } from './scannerOverlay'

type ScanFn = (bild: HTMLCanvasElement, optionen: DetectionOptions) => Promise<ScannerResult>

/** Arbeitsauflösung nach dem Foto: genau genug zum Einrasten, schnell genug fürs Handy. */
const ARBEITS_PX = 1600
/** Mindest-Sicherheit des Modells, dass überhaupt ein Blatt im Bild ist. */
const MIN_SICHERHEIT_FOTO = 0.5
const MIN_SICHERHEIT_LIVE = 0.6
/** Nach einem Fehlschlag (z. B. offline vor dem ersten Laden) die KI erst nach dieser Pause erneut versuchen. */
const KI_WIEDERHOLEN_NACH_MS = 60_000

const ML_OPTIONEN = {
    modelUrl: modellUrl,
    // onnxruntime-web nimmt auch getrennte Pfade für Lader und WASM an;
    // die Typen von scanic kennen nur den String-Fall.
    wasmPaths: { mjs: ortMjsUrl, wasm: ortWasmUrl } as unknown as string,
}

let ladeVersprechen: Promise<void> | null = null
let scan: ScanFn | null = null
let mlVerfuegbar = false
let naechsterKiVersuch = 0
/** Eine Modell-Sitzung verträgt keine gleichzeitigen Läufe: Erkennungen nacheinander abarbeiten. */
let warteschlange: Promise<unknown> = Promise.resolve()

/** Probelauf auf leerem Bild: lädt Modell + Laufzeit und wärmt sie an. */
async function starteKi(scanFn: ScanFn): Promise<void> {
    const probe = document.createElement('canvas')
    probe.width = probe.height = 64
    try {
        await scanFn(probe, { mode: 'detect', detector: 'ml', ml: ML_OPTIONEN })
        mlVerfuegbar = true
    } catch (err) {
        console.warn('[Scanner] KI-Erkennung nicht verfügbar, nutze klassische Erkennung:', err)
        mlVerfuegbar = false
        naechsterKiVersuch = Date.now() + KI_WIEDERHOLEN_NACH_MS
    } finally {
        gibCanvasFrei(probe)
    }
}

async function lade(): Promise<void> {
    const scanic = await import('scanic')
    const scanFn = scanic.scanDocument as ScanFn
    await starteKi(scanFn)
    // Erst jetzt „bereit“: vorher liefe nur die klassische Erkennung.
    scan = scanFn
}

/**
 * Lädt die Erkennung (einmalig). Wird nie abgelehnt: Schlägt das Laden
 * fehl, liefern die Erkennungsfunktionen `null`, und der Nutzer setzt die
 * Ecken von Hand.
 */
export function preloadEdgeDetector(): Promise<void> {
    if (!ladeVersprechen) {
        ladeVersprechen = lade().catch(err => {
            console.warn('[Scanner] Erkennung konnte nicht geladen werden:', err)
            ladeVersprechen = null
        })
    }
    return ladeVersprechen
}

export function istEdgeDetectorBereit(): boolean {
    return scan !== null
}

function alsListe(c: CornerPoints | null | undefined): Point[] | null {
    if (!c?.topLeft || !c.topRight || !c.bottomRight || !c.bottomLeft) return null
    const liste = [c.topLeft, c.topRight, c.bottomRight, c.bottomLeft]
    return liste.every(p => Number.isFinite(p.x) && Number.isFinite(p.y)) ? ordneEcken(liste) : null
}

/** Bringt vier Punkte in die Reihenfolge TL, TR, BR, BL (im Uhrzeigersinn ab oben links). */
export function ordneEcken(punkte: Point[]): Point[] {
    const mx = punkte.reduce((s, p) => s + p.x, 0) / punkte.length
    const my = punkte.reduce((s, p) => s + p.y, 0) / punkte.length
    const sortiert = [...punkte].sort(
        (a, b) => Math.atan2(a.y - my, a.x - mx) - Math.atan2(b.y - my, b.x - mx),
    )
    // Nach atan2 beginnt die Liste links; oben links = kleinste Summe x + y.
    let start = 0
    for (let i = 1; i < 4; i++) {
        if (sortiert[i].x + sortiert[i].y < sortiert[start].x + sortiert[start].y) start = i
    }
    return [0, 1, 2, 3].map(k => sortiert[(start + k) % 4])
}

/** Fläche des Vierecks (Gaußsche Trapezformel). */
export function viereckFlaeche(ecken: Point[]): number {
    let summe = 0
    for (let i = 0; i < 4; i++) {
        const j = (i + 1) % 4
        summe += ecken[i].x * ecken[j].y - ecken[j].x * ecken[i].y
    }
    return Math.abs(summe) / 2
}

function erkenneGrob(bild: HTMLCanvasElement, minSicherheit: number): Promise<Point[] | null> {
    const lauf = warteschlange.then(() => erkenneGrobJetzt(bild, minSicherheit))
    warteschlange = lauf.catch(() => undefined)
    return lauf
}

async function erkenneGrobJetzt(bild: HTMLCanvasElement, minSicherheit: number): Promise<Point[] | null> {
    if (!scan) return null
    if (!mlVerfuegbar && Date.now() >= naechsterKiVersuch) await starteKi(scan)
    if (mlVerfuegbar) {
        try {
            const ml = await scan(bild, {
                mode: 'detect', detector: 'ml', ml: { ...ML_OPTIONEN, minScore: minSicherheit },
            })
            if (ml.success) return alsListe(ml.corners)
            // Modell ist sicher, dass kein Blatt im Bild ist → nicht raten.
            if (ml.score != null) return null
        } catch (err) {
            console.warn('[Scanner] KI-Erkennung fehlgeschlagen:', err)
        }
    }
    try {
        const klassisch = await scan(bild, { mode: 'detect', detector: 'classical' })
        return klassisch.success ? alsListe(klassisch.corners) : null
    } catch {
        return null
    }
}

function graubildVon(canvas: HTMLCanvasElement) {
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) return null
    return zuGraubild(ctx.getImageData(0, 0, canvas.width, canvas.height))
}

/** Verkleinert die Quelle auf höchstens `maxPx` an der langen Seite. */
function arbeitsbild(quelle: HTMLImageElement | HTMLCanvasElement, maxPx: number): { canvas: HTMLCanvasElement; skala: number } | null {
    const b = quelle instanceof HTMLImageElement ? quelle.naturalWidth : quelle.width
    const h = quelle instanceof HTMLImageElement ? quelle.naturalHeight : quelle.height
    if (!b || !h) return null
    const skala = Math.min(1, maxPx / Math.max(b, h))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(b * skala)
    canvas.height = Math.round(h * skala)
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) {
        gibCanvasFrei(canvas)
        return null
    }
    ctx.drawImage(quelle, 0, 0, canvas.width, canvas.height)
    return { canvas, skala }
}

function grossGenug(ecken: Point[], breite: number, hoehe: number, minAnteil: number): boolean {
    return viereckFlaeche(ecken) >= breite * hoehe * minAnteil
}

/**
 * Nach dem Foto: erkennt die Ecken und rastet sie ein. Ecken in Pixeln von
 * `quelle` (naturalWidth/Height bzw. Canvas-Größe), TL, TR, BR, BL; `null`,
 * wenn kein Blatt gefunden wurde.
 */
export async function detectDocumentCorners(quelle: HTMLImageElement | HTMLCanvasElement): Promise<Point[] | null> {
    try {
        await preloadEdgeDetector()
        const arbeit = arbeitsbild(quelle, ARBEITS_PX)
        if (!arbeit) return null
        const { canvas, skala } = arbeit
        try {
            const grob = await erkenneGrob(canvas, MIN_SICHERHEIT_FOTO)
            if (!grob || !grossGenug(grob, canvas.width, canvas.height, 0.1)) return null
            const grau = graubildVon(canvas)
            const fein = grau ? rasteKantenEin(grau, grob) : grob
            return fein.map(p => ({ x: p.x / skala, y: p.y / skala }))
        } finally {
            gibCanvasFrei(canvas)
        }
    } catch (err) {
        console.warn('[Scanner] Ecken-Erkennung fehlgeschlagen:', err)
        return null
    }
}

/**
 * Live-Bild: wie `detectDocumentCorners`, aber direkt auf dem schon
 * verkleinerten Canvas des Kamerabilds. Liefert `null`, solange die
 * Erkennung noch lädt, kein Blatt erkannt ist oder es zu klein im Bild ist.
 */
export async function detectDocumentCornersLive(canvas: HTMLCanvasElement, minFlaechenAnteil = 0.15): Promise<Point[] | null> {
    if (!scan || !canvas.width || !canvas.height) return null
    try {
        const grob = await erkenneGrob(canvas, MIN_SICHERHEIT_LIVE)
        if (!grob || !grossGenug(grob, canvas.width, canvas.height, minFlaechenAnteil)) return null
        const grau = graubildVon(canvas)
        return grau ? rasteKantenEin(grau, grob) : grob
    } catch {
        return null
    }
}

/**
 * Rastet bereits bekannte Ecken (z. B. aus dem Live-Bild, hochgerechnet)
 * an der vollen Auflösung von `quelle` ein. Gibt bei Problemen die Eingabe zurück.
 */
export async function verfeinereEcken(quelle: HTMLImageElement | HTMLCanvasElement, ecken: Point[]): Promise<Point[]> {
    try {
        const arbeit = arbeitsbild(quelle, ARBEITS_PX)
        if (!arbeit) return ecken
        const { canvas, skala } = arbeit
        const grau = graubildVon(canvas)
        gibCanvasFrei(canvas)
        if (!grau) return ecken
        const fein = rasteKantenEin(grau, ecken.map(p => ({ x: p.x * skala, y: p.y * skala })))
        return fein.map(p => ({ x: p.x / skala, y: p.y / skala }))
    } catch {
        return ecken
    }
}
