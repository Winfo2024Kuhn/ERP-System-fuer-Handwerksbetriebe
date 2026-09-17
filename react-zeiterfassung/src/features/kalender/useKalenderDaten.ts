import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { Bereich, Feiertag, KalenderEintrag, KalenderTermin, TeamAbwesenheit } from './typen'
import {
    gruppiereProTag,
    jahreImBereich,
    monateImBereich,
    monatsGrenzen,
    monatsKey,
    terminAusAbwesenheit,
    terminAusEintrag,
    terminAusFeiertag,
} from './kalenderDaten'

export interface KalenderDaten {
    /** Termine je Tag für den sichtbaren Bereich; `null`, solange der Bereich noch lädt. */
    termineProTag: Map<string, KalenderTermin[]> | null
    laedt: boolean
    /** Fehlermeldung, wenn ein Teil des sichtbaren Bereichs zuletzt nicht geladen werden konnte. */
    fehler: string | null
    /** Verwirft den Zwischenspeicher und lädt den sichtbaren Bereich neu. */
    neuLaden: () => void
}

interface Zwischenspeicher {
    /** Termine + Team-Abwesenheiten je Monat (`YYYY-MM`). */
    monate: Map<string, KalenderTermin[]>
    /** Feiertage je Jahr. */
    feiertage: Map<number, KalenderTermin[]>
    /** Schlüssel (`YYYY-MM` bzw. `jahr-YYYY`), deren letzter Ladeversuch fehlschlug. */
    fehlgeschlagen: Set<string>
}

export const LADEFEHLER_TEXT = 'Termine konnten nicht geladen werden. Bitte später erneut versuchen.'

const leererSpeicher = (): Zwischenspeicher => ({ monate: new Map(), feiertage: new Map(), fehlgeschlagen: new Set() })

const jahrKey = (jahr: number) => `jahr-${jahr}`

async function ladeJson(url: string): Promise<unknown> {
    const res = await fetch(url)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
}

const alsListe = <T,>(wert: unknown): T[] => (Array.isArray(wert) ? (wert as T[]) : [])

/** `YYYY-MM` → { jahr, monat } (1-basiert). */
function monatAusKey(key: string): { jahr: number; monat: number } {
    const [jahr, monat] = key.split('-').map(Number)
    return { jahr, monat }
}

function ohne(menge: Set<string>, key: string): Set<string> {
    if (!menge.has(key)) return menge
    const neu = new Set(menge)
    neu.delete(key)
    return neu
}

/**
 * Lädt Termine, Team-Abwesenheiten und Feiertage für den sichtbaren Bereich und
 * hält sie monatsweise im Speicher, damit Zurückblättern sofort reagiert.
 *
 * - Laufende Anfragen stehen in einem Set, damit jede Antwort (die den Speicher
 *   ändert und den Effekt erneut anstößt) keine zweite Anfrage für einen noch
 *   offenen Monat auslöst.
 * - `neuLaden` zählt eine Epoche hoch; Antworten aus einer alten Epoche werden
 *   verworfen, damit „Aktualisieren“ während des Ladens wirklich frische Daten bringt.
 * - Fehlschläge werden je Monat/Jahr gemerkt: `fehler` gilt nur, wenn der gerade
 *   sichtbare Bereich betroffen ist.
 *
 * Ein `null`-Token schaltet das Laden ab.
 */
export function useKalenderDaten(token: string | null, bereich: Bereich): KalenderDaten {
    const { von, bis } = bereich
    const [speicher, setSpeicher] = useState<Zwischenspeicher>(leererSpeicher)
    const laufend = useRef(new Set<string>())
    const epoche = useRef(0)

    const monate = useMemo(() => monateImBereich({ von, bis }), [von, bis])
    const jahre = useMemo(() => jahreImBereich({ von, bis }), [von, bis])
    // Stabile Stringschlüssel statt Array-Identitäten als Effekt-Abhängigkeit.
    const fehlendeMonateKey = monate.map(monatsKey).filter(key => !speicher.monate.has(key)).join(',')
    const fehlendeJahreKey = jahre.filter(j => !speicher.feiertage.has(j)).join(',')
    const laedt = Boolean(token) && (fehlendeMonateKey !== '' || fehlendeJahreKey !== '')

    const sichtbarFehlgeschlagen = monate.some(m => speicher.fehlgeschlagen.has(monatsKey(m)))
        || jahre.some(j => speicher.fehlgeschlagen.has(jahrKey(j)))
    const fehler = !laedt && sichtbarFehlgeschlagen ? LADEFEHLER_TEXT : null

    const termineProTag = useMemo(() => {
        if (laedt) return null
        const alle: KalenderTermin[] = []
        for (const m of monate) alle.push(...(speicher.monate.get(monatsKey(m)) ?? []))
        for (const j of jahre) alle.push(...(speicher.feiertage.get(j) ?? []))
        return gruppiereProTag(alle.filter(t => t.datum >= von && t.datum <= bis))
    }, [laedt, monate, jahre, speicher, von, bis])

    useEffect(() => {
        if (!token) return
        const monateZuLaden = fehlendeMonateKey === '' ? [] : fehlendeMonateKey.split(',').filter(key => !laufend.current.has(key))
        const jahreZuLaden = fehlendeJahreKey === '' ? [] : fehlendeJahreKey.split(',').map(Number).filter(jahr => !laufend.current.has(jahrKey(jahr)))
        if (monateZuLaden.length === 0 && jahreZuLaden.length === 0) return

        const meineEpoche = epoche.current
        const nochAktuell = () => meineEpoche === epoche.current
        const tokenParam = encodeURIComponent(token)

        const ladeMonat = async (key: string) => {
            laufend.current.add(key)
            try {
                const m = monatAusKey(key)
                const grenzen = monatsGrenzen(m)
                const [eintraege, abwesenheiten] = await Promise.all([
                    ladeJson(`/api/kalender/mobile?token=${tokenParam}&jahr=${encodeURIComponent(m.jahr)}&monat=${encodeURIComponent(m.monat)}`),
                    ladeJson(`/api/abwesenheit/team?von=${encodeURIComponent(grenzen.von)}&bis=${encodeURIComponent(grenzen.bis)}`),
                ])
                if (!nochAktuell()) return
                const termine = [
                    ...alsListe<KalenderEintrag>(eintraege).map(terminAusEintrag),
                    ...alsListe<TeamAbwesenheit>(abwesenheiten).map(terminAusAbwesenheit),
                ]
                setSpeicher(alt => ({ ...alt, monate: new Map(alt.monate).set(key, termine), fehlgeschlagen: ohne(alt.fehlgeschlagen, key) }))
            } catch (err) {
                if (!nochAktuell()) return
                // Als leer eintragen, damit das Raster nicht im Skeleton hängen bleibt, und den Fehlschlag merken.
                setSpeicher(alt => ({
                    ...alt,
                    monate: alt.monate.has(key) ? alt.monate : new Map(alt.monate).set(key, []),
                    fehlgeschlagen: new Set(alt.fehlgeschlagen).add(key),
                }))
                throw err
            } finally {
                if (nochAktuell()) laufend.current.delete(key)
            }
        }

        const ladeJahr = async (jahr: number) => {
            const key = jahrKey(jahr)
            laufend.current.add(key)
            try {
                const feiertage = await ladeJson(`/api/zeiterfassung/feiertage?jahr=${encodeURIComponent(jahr)}`)
                if (!nochAktuell()) return
                const termine = alsListe<Feiertag>(feiertage).map(terminAusFeiertag)
                setSpeicher(alt => ({ ...alt, feiertage: new Map(alt.feiertage).set(jahr, termine), fehlgeschlagen: ohne(alt.fehlgeschlagen, key) }))
            } catch (err) {
                if (!nochAktuell()) return
                setSpeicher(alt => ({
                    ...alt,
                    feiertage: alt.feiertage.has(jahr) ? alt.feiertage : new Map(alt.feiertage).set(jahr, []),
                    fehlgeschlagen: new Set(alt.fehlgeschlagen).add(key),
                }))
                throw err
            } finally {
                if (nochAktuell()) laufend.current.delete(key)
            }
        }

        Promise.allSettled([...monateZuLaden.map(ladeMonat), ...jahreZuLaden.map(ladeJahr)]).then(ergebnisse => {
            const fehlgeschlagen = ergebnisse.filter(e => e.status === 'rejected')
            // Nur die Fehlerobjekte („HTTP 500“), keine URLs – die trügen das Token.
            if (fehlgeschlagen.length > 0 && nochAktuell()) console.error('Kalender konnte nicht geladen werden:', fehlgeschlagen.map(e => e.reason))
        })
    }, [token, fehlendeMonateKey, fehlendeJahreKey])

    const neuLaden = useCallback(() => {
        epoche.current += 1
        laufend.current.clear()
        setSpeicher(leererSpeicher())
    }, [])

    return { termineProTag, laedt, fehler, neuLaden }
}
