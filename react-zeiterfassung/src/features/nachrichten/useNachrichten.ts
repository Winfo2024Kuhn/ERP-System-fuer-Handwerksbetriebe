import { useCallback, useEffect, useRef, useState } from 'react'
import { entscheidungsText, formatIsoDatumDe, formatUrlaubstage, typLabel, type AntragStatus, type AntragTyp } from '../../lib/urlaubsantrag'

/**
 * Nachrichten der Glocke: Entscheidungen des Büros zu eigenen Anträgen
 * (genehmigt, abgelehnt, vom Büro storniert) und die Warnung vor
 * verfallendem Resturlaub – zusammengeführt zu einer Liste.
 */
export type NachrichtArt = 'GENEHMIGT' | 'ABGELEHNT' | 'STORNIERT' | 'URLAUBSVERFALL'

export interface Nachricht {
    key: string
    art: NachrichtArt
    titel: string
    text: string
    /** Bereits formatiertes Datum (z. B. Entscheidungstag) oder null. */
    datum: string | null
    dringend: boolean
    /** Wohin ein Tipp auf die Nachricht führt. */
    ziel: string
    /** Markiert die Nachricht als gelesen; wirft bei Fehlern, die Nachricht bleibt dann stehen. */
    onGelesen: () => Promise<void>
}

/** DTO von GET /api/urlaub/antraege/neue-entscheidungen. */
interface Entscheidung {
    id: number
    typ: AntragTyp
    status: AntragStatus
    vonDatum: string
    bisDatum: string
    entschiedenAm?: string | null
}

/** Ergebnis von „Alle als gelesen": wie viele Entscheidungen nicht gespeichert werden konnten. */
export interface AlleGelesenErgebnis {
    fehlgeschlagen: number
}

export interface UrlaubsVerfallWarnung {
    resturlaubTage: number
    verfallsDatum: string
    verfallsJahr: number
    tageVerbleibend: number
    dringend: boolean
}

/**
 * Schlüssel für „gelesen" der Urlaubsverfall-Warnung auf diesem Gerät. Jahr,
 * Resttage und Verfallsdatum gehören dazu: ändert sich die Warnung (z. B.
 * weniger Resttage), erscheint sie wieder.
 */
export function urlaubsverfallSchluessel(mitarbeiterId: number, w: UrlaubsVerfallWarnung): string {
    return `zeiterfassung_nachricht_gelesen:urlaubsverfall:${mitarbeiterId}:${w.verfallsJahr}:${w.resturlaubTage}:${w.verfallsDatum}`
}

function urlaubsverfallText(w: UrlaubsVerfallWarnung): string {
    const tage = formatUrlaubstage(w.resturlaubTage)
    const frist = w.tageVerbleibend > 0 ? ` (in ${w.tageVerbleibend} ${w.tageVerbleibend === 1 ? 'Tag' : 'Tagen'})` : ' (heute)'
    return `Du hast noch ${tage} ${tage === '1' ? 'Resturlaubstag' : 'Resturlaubstage'} vom Vorjahr. `
        + `Sie verfallen am ${formatIsoDatumDe(w.verfallsDatum)}${frist}.`
}

async function ladeJson<T>(url: string): Promise<T | null> {
    try {
        const res = await fetch(url)
        if (!res.ok) return null
        return await res.json() as T
    } catch {
        // Funkloch: die Glocke bleibt einfach leer, das Dashboard funktioniert weiter.
        return null
    }
}

async function pruefeAntwort(res: Response) {
    if (!res.ok) throw new Error(`Antwort ${res.status}`)
}

interface Ladeergebnis {
    entscheidungen: Entscheidung[] | null
    verfall: UrlaubsVerfallWarnung | null | undefined
}

/** Lädt beide Quellen parallel. `null`/`undefined` heißt: nicht erreichbar, bisherigen Stand behalten. */
async function ladeNachrichten(mitarbeiterId: number): Promise<Ladeergebnis> {
    const token = localStorage.getItem('zeiterfassung_token')
    const [neu, warnung] = await Promise.all([
        ladeJson<Entscheidung[]>(`/api/urlaub/antraege/neue-entscheidungen?mitarbeiterId=${encodeURIComponent(String(mitarbeiterId))}`),
        token ? ladeJson<UrlaubsVerfallWarnung>(`/api/zeiterfassung/urlaubsverfall/${encodeURIComponent(token)}`) : Promise.resolve(null),
    ])
    let verfall: UrlaubsVerfallWarnung | null | undefined
    if (warnung !== null) {
        const gueltig = !!warnung && warnung.resturlaubTage > 0
        const gelesen = gueltig && localStorage.getItem(urlaubsverfallSchluessel(mitarbeiterId, warnung)) !== null
        verfall = gueltig && !gelesen ? warnung : null
    }
    return {
        entscheidungen: neu === null ? null : (Array.isArray(neu) ? neu.filter(e => e && typeof e.id === 'number') : []),
        verfall,
    }
}

/**
 * Hält die Nachrichten der Glocke. Lädt neu, sobald sich `ausloeser` ändert
 * (Dashboard geöffnet, Sync fertig) oder `neuLaden()` gerufen wird.
 */
export function useNachrichten(mitarbeiterId: number | null | undefined, ausloeser?: unknown) {
    const [entscheidungen, setEntscheidungen] = useState<Entscheidung[]>([])
    const [verfall, setVerfall] = useState<UrlaubsVerfallWarnung | null>(null)
    const anfrage = useRef(0)

    const uebernehmen = useCallback((nummer: number, ergebnis: Ladeergebnis) => {
        if (nummer !== anfrage.current) return // eine neuere Anfrage läuft schon
        if (ergebnis.entscheidungen !== null) setEntscheidungen(ergebnis.entscheidungen)
        if (ergebnis.verfall !== undefined) setVerfall(ergebnis.verfall)
    }, [])

    const neuLaden = useCallback(async () => {
        if (!mitarbeiterId) return
        const nummer = ++anfrage.current
        uebernehmen(nummer, await ladeNachrichten(mitarbeiterId))
    }, [mitarbeiterId, uebernehmen])

    useEffect(() => {
        if (!mitarbeiterId) return
        const nummer = ++anfrage.current
        ladeNachrichten(mitarbeiterId).then(ergebnis => uebernehmen(nummer, ergebnis))
    }, [mitarbeiterId, ausloeser, uebernehmen])

    const entscheidungGesehen = useCallback(async (id: number) => {
        const res = await fetch(`/api/urlaub/antraege/${id}/entscheidung-gesehen`, { method: 'PUT' })
        await pruefeAntwort(res)
        setEntscheidungen(alt => alt.filter(e => e.id !== id))
    }, [])

    const verfallGelesen = useCallback(async () => {
        if (!mitarbeiterId || !verfall) return
        localStorage.setItem(urlaubsverfallSchluessel(mitarbeiterId, verfall), new Date().toISOString())
        setVerfall(null)
    }, [mitarbeiterId, verfall])

    /**
     * Markiert jede ANGEZEIGTE Entscheidung einzeln als gesehen – bewusst nicht über den
     * Sammel-Endpunkt, der auch inzwischen neu hinzugekommene, nie gesehene Entscheidungen
     * wegmarkieren würde. Erfolgreiche verschwinden, fehlgeschlagene bleiben stehen.
     */
    const alleGelesen = useCallback(async (): Promise<AlleGelesenErgebnis> => {
        if (!mitarbeiterId) return { fehlgeschlagen: 0 }
        const angezeigt = entscheidungen.map(e => e.id)
        const ergebnisse = await Promise.allSettled(angezeigt.map(async id => {
            const res = await fetch(`/api/urlaub/antraege/${id}/entscheidung-gesehen`, { method: 'PUT' })
            await pruefeAntwort(res)
            return id
        }))
        const erledigt = new Set(ergebnisse.flatMap(e => e.status === 'fulfilled' ? [e.value] : []))
        if (erledigt.size > 0) setEntscheidungen(alt => alt.filter(e => !erledigt.has(e.id)))
        let fehlgeschlagen = ergebnisse.length - erledigt.size
        try {
            await verfallGelesen()
        } catch {
            fehlgeschlagen += 1
        }
        return { fehlgeschlagen }
    }, [mitarbeiterId, entscheidungen, verfallGelesen])

    const nachrichten: Nachricht[] = [
        ...(verfall ? [{
            key: 'urlaubsverfall',
            art: 'URLAUBSVERFALL' as const,
            titel: verfall.dringend ? 'Dringend: Urlaub verfällt bald' : 'Urlaub verfällt bald',
            text: urlaubsverfallText(verfall),
            datum: null,
            dringend: verfall.dringend,
            ziel: '/salden',
            onGelesen: verfallGelesen,
        }] : []),
        ...entscheidungen.map(e => {
            const art: NachrichtArt = e.status === 'ABGELEHNT' ? 'ABGELEHNT' : e.status === 'STORNIERT' ? 'STORNIERT' : 'GENEHMIGT'
            const ergebnis = art === 'ABGELEHNT' ? 'abgelehnt' : art === 'STORNIERT' ? 'storniert' : 'genehmigt'
            return {
                key: `entscheidung-${e.id}`,
                art,
                titel: `${typLabel(e.typ)} ${ergebnis}`,
                text: entscheidungsText(e),
                datum: e.entschiedenAm ? formatIsoDatumDe(e.entschiedenAm) : null,
                dringend: false,
                ziel: '/urlaub?ansicht=uebersicht',
                onGelesen: () => entscheidungGesehen(e.id),
            }
        }),
    ]

    return { nachrichten, neuLaden, alleGelesen }
}
