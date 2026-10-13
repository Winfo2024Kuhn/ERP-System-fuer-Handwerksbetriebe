import { useCallback, useEffect, useRef, useState } from 'react'
import { useToast } from '../ui/toast'
import { verfeinereEcken } from '../../services/DocumentEdgeDetector'
import { erzeugeSeitenBild, schneideZu } from '../../services/scanSeite'
import { STANDARD_SCAN_FILTER, type ScanFilter } from '../../services/scanFilter'
import { blobZuCanvas, canvasZuBlob, gibCanvasFrei, ladeBild } from '../../services/scanBildHilfen'
import type { Point } from '../../services/scannerOverlay'

export interface ScanSeite {
    id: string
    /** Object-URL des Original-Fotos – Grundlage für „Ecken anpassen“. */
    fotoUrl: string
    /** Ecken (TL, TR, BR, BL) in Pixeln des Fotos; `null`, bis die Seite einmal zugeschnitten ist. */
    ecken: Point[] | null
    filter: ScanFilter
    /** Zugeschnitten, aber ungefiltert – so lässt sich der Filter ohne neues Zuschneiden wechseln. */
    roh: Blob | null
    /** Fertige Seite mit Filter, so kommt sie ins PDF. */
    bild: Blob | null
    /** Pixelmaße der zugeschnittenen Seite (für das PDF-Seitenformat); `null`, solange noch nicht zugeschnitten. */
    breite: number | null
    hoehe: number | null
    vorschauUrl: string | null
    status: 'arbeitet' | 'fertig' | 'fehler'
}

export interface NeueSeite {
    /** Object-URL des Fotos; gehört ab jetzt der Seitenliste (wird beim Löschen freigegeben). */
    fotoUrl: string
    ecken: Point[]
    /** Auflösung, in der `ecken` gemessen wurden, falls sie vom Foto abweicht (Live-Bild). */
    eckenQuelle?: { breite: number; hoehe: number }
    /** Ecken vorher an die echten Blattkanten im Foto einrasten (nach Auto-Aufnahme). */
    verfeinern: boolean
}

function skaliereEcken(ecken: Point[], bild: HTMLImageElement, quelle?: { breite: number; hoehe: number }): Point[] {
    if (!quelle || quelle.breite <= 0 || quelle.hoehe <= 0) return ecken
    const sx = bild.naturalWidth / quelle.breite
    const sy = bild.naturalHeight / quelle.hoehe
    return ecken.map(p => ({
        x: Math.max(0, Math.min(bild.naturalWidth, p.x * sx)),
        y: Math.max(0, Math.min(bild.naturalHeight, p.y * sy)),
    }))
}

interface ZuschnittOptionen {
    eckenQuelle?: NeueSeite['eckenQuelle']
    verfeinern: boolean
}

/** Fertiges Bild; nach einem Zuschnitt zusätzlich die Ecken und das ungefilterte Zwischenbild. */
interface Arbeitsergebnis {
    bild: Blob
    ecken?: Point[]
    roh?: Blob
    breite?: number
    hoehe?: number
}

/** Foto laden, Ecken ggf. einrasten, geradeziehen und mit dem Filter der Seite verbessern. */
async function schneideZuUndFiltere(seite: ScanSeite, ecken: Point[], optionen: ZuschnittOptionen): Promise<Arbeitsergebnis> {
    const foto = await ladeBild(seite.fotoUrl)
    let endEcken = skaliereEcken(ecken, foto, optionen.eckenQuelle)
    if (optionen.verfeinern) {
        const grob = endEcken
        endEcken = await verfeinereEcken(foto, grob).catch(() => grob)
    }
    const zugeschnitten = schneideZu(foto, endEcken)
    const { width: breite, height: hoehe } = zugeschnitten
    try {
        const [bild, roh] = await Promise.all([
            erzeugeSeitenBild(zugeschnitten, seite.filter),
            canvasZuBlob(zugeschnitten),
        ])
        return { ecken: endEcken, bild, roh, breite, hoehe }
    } finally {
        gibCanvasFrei(zugeschnitten)
    }
}

/**
 * Hält die Seiten eines Scans und verarbeitet sie im Hintergrund – eine nach
 * der anderen, damit das Handy nicht mehrere große Bilder gleichzeitig
 * rechnet. Jede Seite merkt sich Foto, Ecken, Filter und das ungefilterte
 * Ergebnis, damit Filterwechsel und „Ecken anpassen“ ohne neue Aufnahme gehen.
 */
export function useScanSeiten() {
    const toast = useToast()
    const [seiten, setSeitenState] = useState<ScanSeite[]>([])
    const seitenRef = useRef<ScanSeite[]>([])
    const ketteRef = useRef<Promise<void>>(Promise.resolve())
    const versionRef = useRef(new Map<string, number>())
    const zaehlerRef = useRef(0)
    const abgebautRef = useRef(false)

    const setzeSeiten = useCallback((naechste: ScanSeite[]) => {
        seitenRef.current = naechste
        setSeitenState(naechste)
    }, [])

    const aendereSeite = useCallback((id: string, aenderung: Partial<ScanSeite>) => {
        setzeSeiten(seitenRef.current.map(s => (s.id === id ? { ...s, ...aenderung } : s)))
    }, [setzeSeiten])

    useEffect(() => {
        abgebautRef.current = false
        return () => {
            abgebautRef.current = true
            for (const s of seitenRef.current) {
                URL.revokeObjectURL(s.fotoUrl)
                if (s.vorschauUrl) URL.revokeObjectURL(s.vorschauUrl)
            }
        }
    }, [])

    /**
     * Stellt eine Arbeit für eine Seite in die Warteschlange. Die Arbeit
     * bekommt den Stand der Seite zum Zeitpunkt, an dem sie drankommt.
     * Ändert sich die Seite in der Zwischenzeit (neuer Filter, neue Ecken),
     * wird das veraltete Bild verworfen; Zuschnitt und Ecken bleiben aber
     * erhalten – die Warteschlange läuft der Reihe nach, also gewinnt immer
     * der neueste Zuschnitt.
     */
    const planeArbeit = useCallback((id: string, arbeit: (seite: ScanSeite) => Promise<Arbeitsergebnis>) => {
        const version = (versionRef.current.get(id) ?? 0) + 1
        versionRef.current.set(id, version)
        const aktuell = () => versionRef.current.get(id) === version
        const vorhanden = () => !abgebautRef.current && seitenRef.current.some(s => s.id === id)

        ketteRef.current = ketteRef.current.then(async () => {
            const seite = seitenRef.current.find(s => s.id === id)
            if (!seite || abgebautRef.current) return
            try {
                const { bild, ...zuschnitt } = await arbeit(seite)
                if (!vorhanden()) return
                if (!aktuell()) {
                    if (zuschnitt.roh) aendereSeite(id, zuschnitt)
                    return
                }
                const vorschauUrl = URL.createObjectURL(bild)
                const alt = seitenRef.current.find(s => s.id === id)?.vorschauUrl
                aendereSeite(id, { ...zuschnitt, bild, vorschauUrl, status: 'fertig' })
                if (alt) URL.revokeObjectURL(alt)
            } catch (err) {
                if (!vorhanden() || !aktuell()) return
                console.error('[Scanner] Seite konnte nicht verarbeitet werden', err)
                aendereSeite(id, { status: 'fehler' })
                toast.error('Seite konnte nicht verbessert werden. Bitte Ecken anpassen oder die Seite neu scannen.')
            }
        })
    }, [aendereSeite, toast])

    const schneideUndFiltere = useCallback((id: string, ecken: Point[], optionen: ZuschnittOptionen) => {
        planeArbeit(id, seite => schneideZuUndFiltere(seite, ecken, optionen))
    }, [planeArbeit])

    const fuegeSeiteHinzu = useCallback((neu: NeueSeite): string => {
        zaehlerRef.current += 1
        const id = `seite-${zaehlerRef.current}`
        setzeSeiten([...seitenRef.current, {
            id,
            fotoUrl: neu.fotoUrl,
            ecken: null,
            filter: STANDARD_SCAN_FILTER,
            roh: null,
            bild: null,
            breite: null,
            hoehe: null,
            vorschauUrl: null,
            status: 'arbeitet',
        }])
        schneideUndFiltere(id, neu.ecken, { eckenQuelle: neu.eckenQuelle, verfeinern: neu.verfeinern })
        return id
    }, [schneideUndFiltere, setzeSeiten])

    const passeEckenAn = useCallback((id: string, ecken: Point[]) => {
        aendereSeite(id, { status: 'arbeitet' })
        schneideUndFiltere(id, ecken, { verfeinern: false })
    }, [aendereSeite, schneideUndFiltere])

    const setzeFilter = useCallback((id: string, filter: ScanFilter) => {
        const seite = seitenRef.current.find(s => s.id === id)
        if (!seite || seite.filter === filter) return
        aendereSeite(id, { filter, status: 'arbeitet' })
        planeArbeit(id, async aktuell => {
            // Ohne ungefiltertes Zwischenbild (erster Zuschnitt schlug fehl) neu zuschneiden.
            if (!aktuell.roh) {
                if (!aktuell.ecken) throw new Error('Die Seite ist noch nicht zugeschnitten.')
                return schneideZuUndFiltere(aktuell, aktuell.ecken, { verfeinern: false })
            }
            const zugeschnitten = await blobZuCanvas(aktuell.roh)
            try {
                return { bild: await erzeugeSeitenBild(zugeschnitten, aktuell.filter) }
            } finally {
                gibCanvasFrei(zugeschnitten)
            }
        })
    }, [aendereSeite, planeArbeit])

    const loescheSeite = useCallback((id: string) => {
        const seite = seitenRef.current.find(s => s.id === id)
        if (!seite) return
        versionRef.current.delete(id)
        setzeSeiten(seitenRef.current.filter(s => s.id !== id))
        URL.revokeObjectURL(seite.fotoUrl)
        if (seite.vorschauUrl) URL.revokeObjectURL(seite.vorschauUrl)
    }, [setzeSeiten])

    /** Wartet, bis alle Seiten fertig verarbeitet sind, und liefert den Stand danach. */
    const warteAufAlle = useCallback(async (): Promise<ScanSeite[]> => {
        let kette: Promise<void>
        do {
            kette = ketteRef.current
            await kette
        } while (kette !== ketteRef.current)
        return seitenRef.current
    }, [])

    return { seiten, fuegeSeiteHinzu, passeEckenAn, setzeFilter, loescheSeite, warteAufAlle }
}
