import { useCallback, useEffect, useRef, useState } from 'react'
import { OfflineService } from '../services/OfflineService'
import { hatEingerichtetesZeitkonto, zeitbuchungGesperrt, type ZeitkontoStatus } from '../types/zeitkonto'

export interface ZeitkontoStatusZustand {
    /** Stand vom Server oder – ohne Netz – der auf dem Handy gemerkte; null = unbekannt. */
    status: ZeitkontoStatus | null
    /** Die erste Prüfung ist durch. Ein späteres Nachladen setzt das nicht zurück (kein Flackern). */
    geprueft: boolean
    /** Der Stand kommt aus dem Handy-Speicher, weil der Server nicht erreichbar war. */
    ausCache: boolean
    /** Neue Zeitbuchungen sperren – nur wenn ausdrücklich „nicht eingerichtet“ (unbekannt sperrt nicht). */
    gesperrt: boolean
    /** Arbeitszeit ist sicher eingerichtet (z. B. für Salden, die es nur dann gibt). */
    eingerichtet: boolean
    /** Eine über {@link neuLaden} angestoßene Prüfung läuft (Knopf sperren, Drehsymbol zeigen). */
    laedt: boolean
    /** Erneut prüfen (Knopf „Erneut prüfen“). */
    neuLaden: () => void
}

/**
 * Ist für den angemeldeten Mitarbeiter Arbeitszeit eingerichtet? Gemeinsam für alle Seiten der
 * Zeiterfassung, damit keine ohne Netz fälschlich „nicht eingerichtet“ meldet: Ohne Verbindung gilt
 * der zuletzt gemerkte Stand ({@link OfflineService.getZeitkontoStatus}), ohne gemerkten Stand ist er
 * unbekannt – und unbekannt sperrt nicht.
 *
 * @param neuLadenAusloeser ändert sich dieser Wert (z. B. die Nummer des letzten Abgleichs), wird neu geprüft
 */
export function useZeitkontoStatus(neuLadenAusloeser?: unknown): ZeitkontoStatusZustand {
    const [status, setStatus] = useState<ZeitkontoStatus | null>(null)
    const [ausCache, setAusCache] = useState(false)
    const [geprueft, setGeprueft] = useState(false)
    const [laedt, setLaedt] = useState(false)
    // Nur die Antwort der jüngsten Prüfung zählt – eine langsame ältere darf sie nicht überschreiben.
    const laufendePruefung = useRef(0)

    const pruefen = useCallback(() => {
        const nummer = ++laufendePruefung.current
        const token = localStorage.getItem('zeiterfassung_token')
        const ergebnis = token
            ? OfflineService.getZeitkontoStatus(token)
            : Promise.resolve({ status: null, ausCache: false })
        return ergebnis
            .catch(() => ({ status: null, ausCache: false })) // Speicher nicht lesbar: unbekannt, nicht sperren
            .then(neu => {
                if (nummer !== laufendePruefung.current) return
                setStatus(neu.status)
                setAusCache(neu.ausCache)
                setGeprueft(true)
                setLaedt(false)
            })
    }, [])

    useEffect(() => {
        void pruefen()
    }, [pruefen, neuLadenAusloeser])

    // Nur der Knopf zeigt das Laden an; das Nachladen nach einem Abgleich läuft still (kein Flackern).
    const neuLaden = useCallback(() => {
        setLaedt(true)
        void pruefen()
    }, [pruefen])

    return {
        status,
        geprueft,
        ausCache,
        gesperrt: zeitbuchungGesperrt(status),
        eingerichtet: hatEingerichtetesZeitkonto(status),
        laedt,
        neuLaden,
    }
}
