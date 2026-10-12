/**
 * Ein Ereignis aus einem Server-Sent-Events-Strom (SSE).
 */
export interface SseEreignis {
    /** Name aus der `event:`-Zeile, ohne sie „message“. */
    event: string;
    /** Inhalt der `data:`-Zeilen; mehrere Zeilen sind mit `\n` verbunden. */
    data: string;
}

/** Zeilenenden nach SSE-Regel: `\r\n`, `\r` oder `\n` (in dieser Reihenfolge prüfen). */
const ZEILENENDE = /\r\n|\r|\n/;

/** Sammelt die Zeilen eines Ereignisses, bis eine Leerzeile es abschließt. */
class EreignisSammler {
    private name = '';
    private daten: string[] = [];

    /** Verarbeitet eine Zeile; bei einer Leerzeile kommt das fertige Ereignis zurück. */
    zeile(zeile: string): SseEreignis | null {
        if (zeile === '') return this.abschliessen();
        // Zeilen mit „:“ am Anfang sind Kommentare, z. B. Pings gegen Zeitüberschreitungen.
        if (zeile.startsWith(':')) return null;
        const doppelpunkt = zeile.indexOf(':');
        const feld = doppelpunkt === -1 ? zeile : zeile.slice(0, doppelpunkt);
        const wert = doppelpunkt === -1 ? '' : zeile.slice(doppelpunkt + 1).replace(/^ /, '');
        if (feld === 'event') this.name = wert;
        else if (feld === 'data') this.daten.push(wert);
        // `id`, `retry` und unbekannte Felder braucht hier niemand.
        return null;
    }

    /** Gibt das gesammelte Ereignis heraus und fängt neu an. Ohne `data:`-Zeile gibt es keins. */
    abschliessen(): SseEreignis | null {
        const ereignis = this.daten.length > 0
            ? { event: this.name || 'message', data: this.daten.join('\n') }
            : null;
        this.name = '';
        this.daten = [];
        return ereignis;
    }
}

/**
 * Trennt die fertigen Zeilen vom Puffer ab. Steht ganz am Ende ein `\r`, bleibt
 * es liegen, solange noch Daten kommen – der nächste Block könnte mit dem `\n`
 * eines `\r\n` beginnen, und das darf nicht als zweite (leere) Zeile zählen.
 */
function trenneZeilen(puffer: string, stromZuEnde: boolean): { zeilen: string[]; rest: string } {
    const zurueckhalten = !stromZuEnde && puffer.endsWith('\r') ? 1 : 0;
    const zeilen = puffer.slice(0, puffer.length - zurueckhalten).split(ZEILENENDE);
    const rest = zeilen.pop() + puffer.slice(puffer.length - zurueckhalten);
    return { zeilen, rest };
}

/**
 * Liest Server-Sent Events aus einer `fetch`-Antwort, Ereignis für Ereignis.
 *
 * <p>Gedacht für Ströme, die per POST kommen – `EventSource` kann nur GET.
 * Der Leser kennt keine Fachlogik: Er zerlegt den Datenstrom nach den
 * SSE-Regeln und gibt jedes Ereignis weiter, sobald seine abschließende
 * Leerzeile da ist.</p>
 *
 * <ul>
 *   <li>Blöcke dürfen mitten in einer Zeile oder in einem UTF-8-Zeichen abreißen.</li>
 *   <li>Zeilenenden `\n`, `\r\n` und `\r`.</li>
 *   <li>Kommentarzeilen (`:` am Anfang) fallen weg.</li>
 *   <li>Ein Leerzeichen nach dem Doppelpunkt wird entfernt (`data: x` = `data:x`).</li>
 *   <li>Mehrere `data:`-Zeilen eines Ereignisses werden mit `\n` verbunden.</li>
 *   <li>Ohne `event:`-Zeile heißt das Ereignis „message“; ohne `data:`-Zeile gibt es keins.</li>
 * </ul>
 *
 * <p>Anders als `EventSource` geht das letzte Ereignis nicht verloren, wenn
 * der Strom ohne abschließende Leerzeile endet – so kommt eine Antwort auch
 * dann an, wenn unterwegs das letzte Zeilenende fehlt.</p>
 *
 * <p>Bricht der Aufrufer die Schleife vorzeitig ab, wird der Strom geschlossen.
 * Ein Abbruch über das `AbortSignal` des `fetch` kommt als Fehler aus der
 * Schleife heraus.</p>
 *
 * @example
 * for await (const { event, data } of leseSseEreignisse(antwort)) { … }
 */
export async function* leseSseEreignisse(antwort: Response): AsyncGenerator<SseEreignis, void, undefined> {
    if (!antwort.body) return;
    const leser = antwort.body.getReader();
    const decoder = new TextDecoder();
    const sammler = new EreignisSammler();
    let puffer = '';
    let stromZuEnde = false;
    try {
        while (!stromZuEnde) {
            const { value, done } = await leser.read();
            stromZuEnde = done;
            puffer += done ? decoder.decode() : decoder.decode(value, { stream: true });
            const { zeilen, rest } = trenneZeilen(puffer, done);
            puffer = rest;
            for (const zeile of zeilen) {
                const ereignis = sammler.zeile(zeile);
                if (ereignis) yield ereignis;
            }
        }
        // Strom zu Ende: letzte Zeile ohne Zeilenende und letztes Ereignis ohne Leerzeile.
        if (puffer) sammler.zeile(puffer);
        const letztes = sammler.abschliessen();
        if (letztes) yield letztes;
    } finally {
        // Vorzeitig verlassen (break oder Fehler): Strom schließen. Ein schon
        // fehlgeschlagener Strom lehnt das ab – das ist hier egal.
        if (!stromZuEnde) await leser.cancel().catch(() => undefined);
        leser.releaseLock();
    }
}
