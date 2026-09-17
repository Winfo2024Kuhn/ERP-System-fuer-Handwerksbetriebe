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
            // 502 und alles Sonstige: die Servermeldung ist bereits deutscher
            // Klartext (siehe SpracheingabeController) — nur bei leerem/fehlendem
            // Text auf den Standardtext zurueckfallen.
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
