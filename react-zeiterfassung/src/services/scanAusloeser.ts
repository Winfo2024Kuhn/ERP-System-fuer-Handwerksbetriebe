/**
 * Entscheidet Bild für Bild, ob der Scanner automatisch auslösen soll –
 * als reine Funktion, damit der Ablauf ohne Kamera testbar ist.
 *
 * Regeln wie in der Apple-Scanner-App:
 * - Ausgelöst wird erst, wenn das erkannte Blatt einige Bilder lang ruhig liegt.
 * - Nach einer Aufnahme ist der Auslöser gesperrt, bis das Blatt aus dem Bild
 *   verschwindet oder sich deutlich bewegt. So wird dieselbe Seite nicht
 *   mehrmals hintereinander aufgenommen.
 */
import { quadsAreClose, type Point } from './scannerOverlay'

export type AusloeserPhase = 'suchen' | 'halten' | 'ausloesen' | 'gesperrt'

export interface AusloeserZustand {
    /** Viereck des vorigen Bildes – Vergleich für „liegt ruhig“. */
    letzterQuad: Point[] | null
    /** Wie viele Bilder in Folge das Viereck ruhig lag. */
    ruhigeBilder: number
    /** Viereck der letzten Aufnahme; solange gesetzt, ist der Auslöser gesperrt. */
    sperrQuad: Point[] | null
    /** Wie viele Bilder in Folge kein Blatt erkannt wurde. */
    leereBilder: number
}

export interface AusloeserOptionen {
    /** Höchstens so weit (Pixel) darf eine Ecke von Bild zu Bild wandern, um als ruhig zu gelten. */
    ruhigTolPx: number
    /** Ab dieser Verschiebung (Pixel) einer Ecke gilt das Blatt nach einer Aufnahme als gewechselt. */
    wechselTolPx: number
    /** So viele ruhige Bilder in Folge lösen aus. */
    ruhigeBilderNoetig: number
    /** So viele Bilder ohne Blatt in Folge heben die Sperre auf (einzelne Aussetzer der Erkennung zählen nicht). */
    leereBilderZumEntsperren: number
}

export interface AusloeserSchritt {
    zustand: AusloeserZustand
    phase: AusloeserPhase
    /** Noch fehlende ruhige Bilder bis zur Aufnahme (nur in Phase „halten“ sinnvoll). */
    verbleibend: number
}

export const START_ZUSTAND: AusloeserZustand = { letzterQuad: null, ruhigeBilder: 0, sperrQuad: null, leereBilder: 0 }

export function ausloeserSchritt(zustand: AusloeserZustand, quad: Point[] | null, optionen: AusloeserOptionen): AusloeserSchritt {
    const { ruhigTolPx, wechselTolPx, ruhigeBilderNoetig, leereBilderZumEntsperren } = optionen

    if (!quad) {
        const leereBilder = zustand.leereBilder + 1
        const sperrQuad = leereBilder >= leereBilderZumEntsperren ? null : zustand.sperrQuad
        return {
            zustand: { letzterQuad: null, ruhigeBilder: 0, sperrQuad, leereBilder },
            phase: 'suchen',
            verbleibend: ruhigeBilderNoetig,
        }
    }

    if (zustand.sperrQuad && quadsAreClose(zustand.sperrQuad, quad, wechselTolPx)) {
        return {
            zustand: { letzterQuad: quad, ruhigeBilder: 0, sperrQuad: zustand.sperrQuad, leereBilder: 0 },
            phase: 'gesperrt',
            verbleibend: ruhigeBilderNoetig,
        }
    }

    const ruhig = zustand.letzterQuad !== null && quadsAreClose(zustand.letzterQuad, quad, ruhigTolPx)
    const ruhigeBilder = ruhig ? zustand.ruhigeBilder + 1 : 0

    if (ruhigeBilder >= ruhigeBilderNoetig) {
        return {
            zustand: { letzterQuad: quad, ruhigeBilder: 0, sperrQuad: quad, leereBilder: 0 },
            phase: 'ausloesen',
            verbleibend: 0,
        }
    }

    return {
        zustand: { letzterQuad: quad, ruhigeBilder, sperrQuad: null, leereBilder: 0 },
        phase: 'halten',
        verbleibend: ruhigeBilderNoetig - ruhigeBilder,
    }
}

/**
 * Sperrt den Auslöser nach einer Aufnahme von Hand: Das gerade erkannte Blatt
 * soll nicht gleich danach noch einmal automatisch aufgenommen werden.
 */
export function sperreNachAufnahme(zustand: AusloeserZustand): AusloeserZustand {
    return { ...zustand, ruhigeBilder: 0, sperrQuad: zustand.letzterQuad ?? zustand.sperrQuad }
}
