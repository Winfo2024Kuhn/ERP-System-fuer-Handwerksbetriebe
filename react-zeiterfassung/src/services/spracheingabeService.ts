/**
 * Schickt eine fertige Sprachaufnahme an den Backend-Endpunkt und liefert den von
 * der KI aufgeraeumten Text.
 *
 * Abweichung A des Plans ("Kein Multipart — die Aufnahme kommt als roher
 * Request-Body"): Der Blob geht UNVERAENDERT als Request-Body raus, nicht als
 * FormData/Multipart. Grund ist Datenschutz, nicht Geschmack — Springs
 * Multipart-Verarbeitung schreibt in diesem Projekt jeden Teil sofort auf die
 * Festplatte (`spring.servlet.multipart.file-size-threshold` ist nicht gesetzt,
 * Standardwert 0B), und die Sprachaufnahme darf die Platte nie beruehren. Der
 * vom Browser gewaehlte Inhaltstyp (`AufnahmeSitzung.mimeType`) geht deshalb im
 * `Content-Type`-Header mit statt in einem Multipart-Teil.
 *
 * DSGVO: Weder die Audio-Bytes noch der von der KI zurueckgegebene Text duerfen
 * jemals in einem Log landen — auch nicht gekuerzt, auch nicht im Fehlerfall,
 * auch nicht die Fehlermeldung des Servers. Diese Datei ruft deshalb bewusst
 * NIRGENDS `console.*` auf.
 */

/** Fehler mit einer Meldung, die so im Toast stehen kann (deutsche Handwerker-Sprache). */
export class SpracheingabeFehler extends Error {
    // Kein Parameter-Property (`readonly status?: number` direkt im Konstruktor-
    // Kopf) — `erasableSyntaxOnly` in tsconfig.app.json verbietet das, weil es
    // eine echte Zuweisung im Konstruktor-Koerper braucht statt nur Typen zu
    // entfernen. Deshalb Feld explizit deklarieren und im Body zuweisen.
    readonly status?: number

    constructor(message: string, status?: number) {
        super(message)
        this.name = 'SpracheingabeFehler'
        this.status = status
    }
}

const STANDARD_FEHLERTEXT = 'Das Diktat konnte nicht umgewandelt werden. Bitte noch einmal versuchen.'

/**
 * Liest die optionale `message` aus einer Fehlerantwort des Endpunkt-Vertrags
 * (`{ "success": false, "message": "..." }`). 401 liefert laut Vertrag gar
 * keinen Body — und auch bei jedem anderen kaputten/leeren JSON darf das hier
 * NICHT werfen, sonst verschluckt eine defekte Fehlerantwort die eigentliche
 * Fehlermeldung.
 */
async function leseServerMeldung(res: Response): Promise<string> {
    try {
        const daten = (await res.json()) as { message?: unknown } | null
        const meldung = daten?.message
        return typeof meldung === 'string' ? meldung.trim() : ''
    } catch {
        return ''
    }
}

/** Baut aus einer nicht-2xx-Antwort den passenden, deutschen Fehler nach dem Endpunkt-Vertrag. */
async function baueFehlerAusAntwort(res: Response): Promise<SpracheingabeFehler> {
    switch (res.status) {
        case 401:
            return new SpracheingabeFehler('Die Anmeldung ist abgelaufen. Bitte den QR-Code neu scannen.', res.status)
        case 413:
            return new SpracheingabeFehler('Die Aufnahme ist zu lang. Bitte kuerzer diktieren.', res.status)
        case 400:
        case 415:
            return new SpracheingabeFehler('Dieses Aufnahmeformat versteht das Programm nicht.', res.status)
        default: {
            // 502, 503 (kein KI-Schluessel hinterlegt) und alles Sonstige: die
            // Servermeldung ist bereits deutscher Klartext (siehe
            // SpracheingabeController) — nur bei leerem/fehlendem Text auf den
            // Standardtext zurueckfallen.
            const serverMeldung = await leseServerMeldung(res)
            return new SpracheingabeFehler(serverMeldung || STANDARD_FEHLERTEXT, res.status)
        }
    }
}

/**
 * Schickt die Aufnahme an das Backend und liefert den aufgeraeumten Text.
 *
 * @param aufnahme Der Blob aus `audioRecorderService.stoppe()`. `aufnahme.type`
 *                 bestimmt den `Content-Type`-Header.
 * @param token    Das Mitarbeiter-Login-Token, kommt kodiert als Query-Parameter mit.
 * @param signal   Optionales AbortSignal, z.B. fuer eine eigene Zeitueberschreitung.
 */
export async function transkribiere(
    aufnahme: Blob,
    token: string,
    signal?: AbortSignal,
): Promise<string> {
    const url = `/api/spracheingabe/transkribieren?token=${encodeURIComponent(token)}`

    let res: Response
    try {
        res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': aufnahme.type || 'audio/webm' },
            // Roher Body, bewusst KEIN FormData (siehe Abweichung A oben).
            body: aufnahme,
            signal,
        })
    } catch (fehler) {
        if (fehler instanceof DOMException && fehler.name === 'AbortError') {
            throw new SpracheingabeFehler('Das Diktat hat zu lange gedauert. Bitte noch einmal versuchen.')
        }
        if (fehler instanceof TypeError) {
            throw new SpracheingabeFehler('Keine Verbindung. Spracheingabe braucht Internet.')
        }
        throw fehler
    }

    if (!res.ok) {
        throw await baueFehlerAusAntwort(res)
    }

    const daten = (await res.json()) as { text?: unknown } | null
    const text = daten?.text
    if (typeof text !== 'string' || text.length === 0) {
        throw new SpracheingabeFehler('Es wurde nichts verstanden. Bitte noch einmal diktieren.')
    }
    return text
}

/*
 * Ist die Spracheingabe im Betrieb eingeschaltet?
 *
 * Die KI ist freiwillig: Ohne hinterlegten KI-Schluessel kann das Backend
 * nichts aufschreiben, und der Mikrofon-Knopf soll dann gar nicht erst
 * erscheinen. Nur ein ausdrueckliches `{ "verfuegbar": false }` heisst "aus".
 * Netzwerkfehler, Offline, 401 oder eine unerwartete Antwort zaehlen als
 * "an" — dann bleibt der Knopf wie bisher sichtbar, und die vorhandene
 * Offline- bzw. Fehlerbehandlung des Knopfes greift.
 *
 * Mehrere Mikrofon-Knoepfe auf einer Seite sollen nur EINE Abfrage ausloesen:
 * Die Antwort wird pro Token im Modul gemerkt, eine laufende Abfrage geteilt.
 * Nach ein paar Minuten wird neu gefragt, damit ein spaeter eingetragener
 * Schluessel auch in einer lange offenen App ankommt.
 */

/** So lange gilt eine bekannte Antwort, bevor neu gefragt wird. */
const VERFUEGBARKEIT_FRISCH_MS = 5 * 60_000

/** So lange darf die Abfrage dauern, bevor sie abgebrochen wird und der Rueckfall greift. */
const VERFUEGBARKEIT_ZEITLIMIT_MS = 5_000

interface BekannteVerfuegbarkeit {
    verfuegbar: boolean
    abgefragtAm: number
}

const bekannteVerfuegbarkeiten = new Map<string, BekannteVerfuegbarkeit>()
const laufendeAbfragen = new Map<string, Promise<boolean>>()

/**
 * Die zuletzt bekannte Antwort fuer dieses Token, ohne Netzwerk. `undefined`,
 * solange noch nie gefragt wurde. So zeigt ein neu eingehaengter Knopf sofort
 * den richtigen Zustand, statt kurz zu flackern.
 */
export function bekannteSpracheingabeVerfuegbarkeit(token: string): boolean | undefined {
    return bekannteVerfuegbarkeiten.get(token)?.verfuegbar
}

/**
 * Fragt `GET /api/spracheingabe/verfuegbar` — hoechstens einmal gleichzeitig
 * und nicht oefter als alle paar Minuten pro Token. Wirft nie.
 */
export function pruefeSpracheingabeVerfuegbar(token: string): Promise<boolean> {
    const bekannt = bekannteVerfuegbarkeiten.get(token)
    if (bekannt && Date.now() - bekannt.abgefragtAm < VERFUEGBARKEIT_FRISCH_MS) {
        return Promise.resolve(bekannt.verfuegbar)
    }
    const laufend = laufendeAbfragen.get(token)
    if (laufend) return laufend

    const abfrage = frageVerfuegbarkeitAb(token).finally(() => laufendeAbfragen.delete(token))
    laufendeAbfragen.set(token, abfrage)
    return abfrage
}

async function frageVerfuegbarkeitAb(token: string): Promise<boolean> {
    // Klappt die Abfrage nicht, gilt die letzte bekannte Antwort weiter —
    // ein Funkloch soll einen ausgeschalteten Knopf nicht wieder hervorholen.
    const rueckfall = bekannteVerfuegbarkeiten.get(token)?.verfuegbar ?? true
    // Haengt die Leitung (schwaches Netz auf der Baustelle), wartet der Knopf
    // nicht ewig unsichtbar: nach kurzer Zeit abbrechen, dann greift der Rueckfall.
    // Bewusst AbortController + setTimeout statt AbortSignal.timeout (fehlt in jsdom).
    const abbruch = new AbortController()
    const uhr = setTimeout(() => abbruch.abort(), VERFUEGBARKEIT_ZEITLIMIT_MS)
    try {
        const res = await fetch(`/api/spracheingabe/verfuegbar?token=${encodeURIComponent(token)}`, {
            signal: abbruch.signal,
        })
        if (!res.ok) return rueckfall
        const daten = (await res.json()) as { verfuegbar?: unknown } | null
        if (typeof daten?.verfuegbar !== 'boolean') return rueckfall
        bekannteVerfuegbarkeiten.set(token, { verfuegbar: daten.verfuegbar, abgefragtAm: Date.now() })
        return daten.verfuegbar
    } catch {
        return rueckfall
    } finally {
        clearTimeout(uhr)
    }
}

/** Vergisst alle gemerkten Antworten. Fuer Tests. */
export function setzeSpracheingabeVerfuegbarkeitZurueck(): void {
    bekannteVerfuegbarkeiten.clear()
    laufendeAbfragen.clear()
}
