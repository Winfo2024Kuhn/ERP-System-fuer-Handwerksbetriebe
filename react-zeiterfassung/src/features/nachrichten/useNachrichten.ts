import { useCallback, useEffect, useRef, useState } from 'react'
import { entscheidungsText, formatIsoDatumDe, formatUrlaubstage, typLabel, type AntragStatus, type AntragTyp } from '../../lib/urlaubsantrag'

/**
 * Nachrichten der Glocke: Entscheidungen des Büros zu eigenen Anträgen
 * (genehmigt, abgelehnt, vom Büro storniert), die Warnung vor
 * verfallendem Resturlaub und Überschneidungen zwischen einer Handy-Buchung
 * und einem Büro-Eintrag – zusammengeführt zu einer Liste.
 */
export type NachrichtArt = 'GENEHMIGT' | 'ABGELEHNT' | 'STORNIERT' | 'URLAUBSVERFALL' | 'UEBERSCHNEIDUNG'

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
    /** true: Schon der Tipp auf die Nachricht markiert sie als gelesen (reine Info, nichts zu erledigen). */
    gelesenBeimOeffnen?: boolean
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

/**
 * DTO von GET /api/zeiterfassung/ueberschneidungen: Eine Handy-Buchung hat einen
 * Büro-Eintrag gekürzt oder ersetzt. Titel und Text formuliert der Server fertig.
 */
interface Ueberschneidung {
    id: number
    von: string
    bis: string | null
    titel: string
    text: string
    erstelltAm: string
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

/**
 * „Du hast noch 3,5 Resturlaubstage vom Vorjahr. Nimm sie bis 31.03.2027, danach
 * verfallen sie (noch 20 Tage)." – verfallsDatum ist der letzte Tag, an dem der
 * Rest genommen werden kann; am Tag selbst heißt es „bis heute (31.03.2027)".
 */
export function urlaubsverfallText(w: UrlaubsVerfallWarnung): string {
    const tage = formatUrlaubstage(w.resturlaubTage)
    const einer = tage === '1'
    const datum = formatIsoDatumDe(w.verfallsDatum)
    const bis = w.tageVerbleibend > 0 ? datum : `heute (${datum})`
    const noch = w.tageVerbleibend > 0 ? ` (noch ${w.tageVerbleibend} ${w.tageVerbleibend === 1 ? 'Tag' : 'Tage'})` : ''
    return `Du hast noch ${tage} ${einer ? 'Resturlaubstag' : 'Resturlaubstage'} vom Vorjahr. `
        + `Nimm ${einer ? 'ihn' : 'sie'} bis ${bis}, danach ${einer ? 'verfällt er' : 'verfallen sie'}${noch}.`
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
    ueberschneidungen: Ueberschneidung[] | null
}

const istUeberschneidung = (u: unknown): u is Ueberschneidung =>
    !!u && typeof u === 'object'
    && typeof (u as Ueberschneidung).id === 'number'
    && typeof (u as Ueberschneidung).titel === 'string'
    && typeof (u as Ueberschneidung).text === 'string'

const ueberschneidungGesehenUrl = (id: number) => `/api/zeiterfassung/ueberschneidungen/${encodeURIComponent(String(id))}/gesehen`

/** Lädt beide Quellen parallel. `null`/`undefined` heißt: nicht erreichbar, bisherigen Stand behalten. */
async function ladeNachrichten(mitarbeiterId: number): Promise<Ladeergebnis> {
    const token = localStorage.getItem('zeiterfassung_token')
    const [neu, warnung, ueberschneidungen] = await Promise.all([
        ladeJson<Entscheidung[]>(`/api/urlaub/antraege/neue-entscheidungen?mitarbeiterId=${encodeURIComponent(String(mitarbeiterId))}`),
        token ? ladeJson<UrlaubsVerfallWarnung>(`/api/zeiterfassung/urlaubsverfall/${encodeURIComponent(token)}`) : Promise.resolve(null),
        // Der Server erkennt den Mitarbeiter am Anmelde-Kopf der Handy-App.
        token ? ladeJson<unknown[]>('/api/zeiterfassung/ueberschneidungen') : Promise.resolve(null),
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
        ueberschneidungen: ueberschneidungen === null ? null : (Array.isArray(ueberschneidungen) ? ueberschneidungen.filter(istUeberschneidung) : []),
    }
}

/**
 * Hält die Nachrichten der Glocke. Lädt neu, sobald sich `ausloeser` ändert
 * (Dashboard geöffnet, Sync fertig) oder `neuLaden()` gerufen wird.
 */
export function useNachrichten(mitarbeiterId: number | null | undefined, ausloeser?: unknown) {
    const [entscheidungen, setEntscheidungen] = useState<Entscheidung[]>([])
    const [verfall, setVerfall] = useState<UrlaubsVerfallWarnung | null>(null)
    const [ueberschneidungen, setUeberschneidungen] = useState<Ueberschneidung[]>([])
    const anfrage = useRef(0)

    const uebernehmen = useCallback((nummer: number, ergebnis: Ladeergebnis) => {
        if (nummer !== anfrage.current) return // eine neuere Anfrage läuft schon
        if (ergebnis.entscheidungen !== null) setEntscheidungen(ergebnis.entscheidungen)
        if (ergebnis.verfall !== undefined) setVerfall(ergebnis.verfall)
        if (ergebnis.ueberschneidungen !== null) setUeberschneidungen(ergebnis.ueberschneidungen)
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

    const ueberschneidungGesehen = useCallback(async (id: number) => {
        const res = await fetch(ueberschneidungGesehenUrl(id), { method: 'PUT' })
        await pruefeAntwort(res)
        setUeberschneidungen(alt => alt.filter(u => u.id !== id))
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

        const ueberschneidungsErgebnisse = await Promise.allSettled(ueberschneidungen.map(async u => {
            const res = await fetch(ueberschneidungGesehenUrl(u.id), { method: 'PUT' })
            await pruefeAntwort(res)
            return u.id
        }))
        const gesehen = new Set(ueberschneidungsErgebnisse.flatMap(e => e.status === 'fulfilled' ? [e.value] : []))
        if (gesehen.size > 0) setUeberschneidungen(alt => alt.filter(u => !gesehen.has(u.id)))
        fehlgeschlagen += ueberschneidungsErgebnisse.length - gesehen.size
        try {
            await verfallGelesen()
        } catch {
            fehlgeschlagen += 1
        }
        return { fehlgeschlagen }
    }, [mitarbeiterId, entscheidungen, ueberschneidungen, verfallGelesen])

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
        ...ueberschneidungen.map(u => ({
            key: `ueberschneidung-${u.id}`,
            art: 'UEBERSCHNEIDUNG' as const,
            titel: u.titel,
            text: u.text,
            datum: null,
            dringend: false,
            ziel: '/tagesbuchungen',
            gelesenBeimOeffnen: true,
            onGelesen: () => ueberschneidungGesehen(u.id),
        })),
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
