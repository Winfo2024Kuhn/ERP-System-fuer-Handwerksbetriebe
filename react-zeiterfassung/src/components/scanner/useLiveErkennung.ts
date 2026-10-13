import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'
import type { CameraHandle } from '../Camera'
import { detectDocumentCornersLive, istEdgeDetectorBereit, preloadEdgeDetector } from '../../services/DocumentEdgeDetector'
import { clearOverlay, drawOverlayQuad, glaetteQuad, type Point } from '../../services/scannerOverlay'
import { ausloeserSchritt, sperreNachAufnahme, START_ZUSTAND, type AusloeserPhase, type AusloeserZustand } from '../../services/scanAusloeser'

/** ~8 Bilder pro Sekunde – reicht für einen ruhigen Rahmen und schont den Akku. */
const TAKT_MS = 120
/** Das Live-Bild wird für die Erkennung auf diese Breite verkleinert. */
const ERKENNUNG_BREITE = 480
/** Mindestanteil des Bildes, den ein Blatt bedecken muss. */
const MIN_FLAECHEN_ANTEIL = 0.18
/** So viele ruhige Bilder in Folge (≈ 0,7 s) lösen aus. */
const RUHIGE_BILDER_NOETIG = 6
/** Ecken dürfen von Bild zu Bild höchstens so weit wandern (Anteil der Bildbreite). */
const RUHIG_TOL = 0.025
/** Ab dieser Verschiebung (Anteil der Bildbreite) gilt das Blatt als gewechselt. */
const WECHSEL_TOL = 0.12
/** Springt der Rahmen weiter, wird er nicht geglättet, sondern sofort versetzt. */
const SPRUNG_TOL = 0.08
/** So viele Bilder ohne Blatt heben die Sperre nach einer Aufnahme auf. */
const LEERE_BILDER_ZUM_ENTSPERREN = 3

export interface LiveErkennungOptionen {
    /** Nur im Kamera-Schritt läuft die Erkennung. */
    aktiv: boolean
    kameraRef: RefObject<CameraHandle | null>
    overlayRef: RefObject<HTMLCanvasElement | null>
    /**
     * Das Blatt liegt ruhig – jetzt aufnehmen. Ecken in Pixeln des Live-Bildes
     * (`quelle`). Liefert (auch asynchron) `false`, wenn keine Aufnahme
     * gelang; dann wird der Auslöser wieder freigegeben.
     */
    onAusloesen: (ecken: Point[], quelle: { breite: number; hoehe: number }) => boolean | Promise<boolean>
}

/** „laedt“ = Erkennung wird geladen, „fehlt“ = konnte nicht geladen werden (nur Auslöser von Hand). */
export type ErkennungsStand = 'laedt' | 'bereit' | 'fehlt'

export interface LiveErkennung {
    erkennung: ErkennungsStand
    phase: AusloeserPhase
    /** Noch fehlende ruhige Bilder bis zur Aufnahme. */
    verbleibend: number
    /** Nach einer Aufnahme von Hand: dasselbe Blatt nicht gleich noch einmal automatisch aufnehmen. */
    sperren: () => void
}

/**
 * Sucht im Kamerabild laufend nach einem Blatt, zeichnet den (geglätteten)
 * Rahmen und löst automatisch aus, sobald das Blatt ruhig liegt. Nach einer
 * Aufnahme wartet sie, bis ein neues Blatt im Bild liegt.
 */
export function useLiveErkennung({ aktiv, kameraRef, overlayRef, onAusloesen }: LiveErkennungOptionen): LiveErkennung {
    const [erkennung, setErkennung] = useState<ErkennungsStand>(() => (istEdgeDetectorBereit() ? 'bereit' : 'laedt'))
    const bereit = erkennung === 'bereit'
    const [phase, setPhase] = useState<AusloeserPhase>('suchen')
    const [verbleibend, setVerbleibend] = useState(RUHIGE_BILDER_NOETIG)
    const zustandRef = useRef<AusloeserZustand>(START_ZUSTAND)
    const glattRef = useRef<Point[] | null>(null)
    const ausloesenRef = useRef(onAusloesen)
    const erkennungCanvasRef = useRef<HTMLCanvasElement | null>(null)
    // Über Schleifen-Neustarts hinweg geteilt: Läuft beim Verlassen der Kamera
    // noch eine Erkennung, darf sie zu Ende laufen, aber nach dem Zurückkehren
    // startet keine zweite parallel dazu.
    const erkennungLaeuftRef = useRef(false)

    useEffect(() => { ausloesenRef.current = onAusloesen }, [onAusloesen])

    useEffect(() => {
        let abgebrochen = false
        // Lehnt nie ab: Fehlt die Erkennung, geht nur der Auslöser von Hand.
        void preloadEdgeDetector().then(() => {
            if (!abgebrochen) setErkennung(istEdgeDetectorBereit() ? 'bereit' : 'fehlt')
        })
        return () => { abgebrochen = true }
    }, [])

    useEffect(() => {
        if (!aktiv || !bereit) return
        // Ruhe-Zähler zurücksetzen, die Sperre der letzten Aufnahme aber behalten:
        // liegt nach dem Zurückkehren noch dasselbe Blatt da, wird es nicht noch einmal aufgenommen.
        zustandRef.current = { ...zustandRef.current, letzterQuad: null, ruhigeBilder: 0 }
        glattRef.current = null
        if (!erkennungCanvasRef.current) erkennungCanvasRef.current = document.createElement('canvas')

        let abgebrochen = false
        let letzterTakt = 0
        let rafId: number | null = null
        const overlayBeimStart = overlayRef.current

        const setzePhase = (naechste: AusloeserPhase, rest: number) => {
            setPhase(vorher => (vorher === naechste ? vorher : naechste))
            setVerbleibend(vorher => (vorher === rest ? vorher : rest))
        }

        const verarbeite = (erkannt: Point[] | null, skala: number, video: HTMLVideoElement) => {
            const overlay = overlayRef.current
            const quad = erkannt?.length === 4 ? erkannt.map(p => ({ x: p.x / skala, y: p.y / skala })) : null
            const schritt = ausloeserSchritt(zustandRef.current, quad, {
                ruhigTolPx: video.videoWidth * RUHIG_TOL,
                wechselTolPx: video.videoWidth * WECHSEL_TOL,
                ruhigeBilderNoetig: RUHIGE_BILDER_NOETIG,
                leereBilderZumEntsperren: LEERE_BILDER_ZUM_ENTSPERREN,
            })
            zustandRef.current = schritt.zustand

            if (!quad) {
                glattRef.current = null
                clearOverlay(overlay)
                setzePhase(schritt.phase, schritt.verbleibend)
                return
            }

            glattRef.current = glaetteQuad(glattRef.current, quad, { sprungPx: video.videoWidth * SPRUNG_TOL })
            drawOverlayQuad(overlay, video.videoWidth, video.videoHeight, glattRef.current, schritt.phase === 'ausloesen')

            if (schritt.phase === 'ausloesen') {
                setzePhase('gesperrt', RUHIGE_BILDER_NOETIG)
                void Promise.resolve(ausloesenRef.current(quad, { breite: video.videoWidth, hoehe: video.videoHeight }))
                    .catch(() => false)
                    .then(aufgenommen => {
                        if (aufgenommen) return
                        // Keine Aufnahme: Sperre aufheben, damit das Blatt erneut erkannt werden kann.
                        zustandRef.current = { ...zustandRef.current, sperrQuad: null, ruhigeBilder: 0 }
                        if (!abgebrochen) setzePhase('suchen', RUHIGE_BILDER_NOETIG)
                    })
                return
            }
            setzePhase(schritt.phase, schritt.verbleibend)
        }

        const takt = (jetzt: number) => {
            if (abgebrochen) return
            rafId = requestAnimationFrame(takt)
            // Die Erkennung läuft asynchron: nie zwei Bilder gleichzeitig.
            if (erkennungLaeuftRef.current || jetzt - letzterTakt < TAKT_MS) return
            const video = kameraRef.current?.video
            const canvas = erkennungCanvasRef.current
            if (!video || !canvas || video.readyState < 2 || !video.videoWidth || !video.videoHeight) return
            letzterTakt = jetzt

            const skala = ERKENNUNG_BREITE / video.videoWidth
            const hoehe = Math.round(video.videoHeight * skala)
            if (canvas.width !== ERKENNUNG_BREITE || canvas.height !== hoehe) {
                canvas.width = ERKENNUNG_BREITE
                canvas.height = hoehe
            }
            const ctx = canvas.getContext('2d', { willReadFrequently: true })
            if (!ctx) return
            ctx.drawImage(video, 0, 0, ERKENNUNG_BREITE, hoehe)

            erkennungLaeuftRef.current = true
            detectDocumentCornersLive(canvas, MIN_FLAECHEN_ANTEIL)
                .catch(() => null)
                .then(erkannt => {
                    if (!abgebrochen) verarbeite(erkannt, skala, video)
                })
                .finally(() => { erkennungLaeuftRef.current = false })
        }

        rafId = requestAnimationFrame(takt)
        return () => {
            abgebrochen = true
            if (rafId !== null) cancelAnimationFrame(rafId)
            clearOverlay(overlayBeimStart)
            glattRef.current = null
            setPhase('suchen')
            setVerbleibend(RUHIGE_BILDER_NOETIG)
        }
    }, [aktiv, bereit, kameraRef, overlayRef])

    const sperren = useCallback(() => {
        zustandRef.current = sperreNachAufnahme(zustandRef.current)
    }, [])

    return { erkennung, phase, verbleibend, sperren }
}
