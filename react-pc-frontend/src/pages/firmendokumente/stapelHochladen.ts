/**
 * Reine Logik zum Hochladen mehrerer Dateien auf einmal (ohne Dialog):
 * wie viele gleichzeitig und die Meldung am Ende. Ohne React und ohne Netzwerk.
 */

/** Höchstens so viele Dateien gehen gleichzeitig zum Server. */
export const GLEICHZEITIG = 3;
/** So viele fehlgeschlagene Dateien nennt die Meldung beim Namen. */
const MAX_GENANNT = 5;

export interface StapelFehler {
    /** Dateiname, wie er ausgewählt wurde. */
    name: string;
    /** Meldung des Servers bzw. Ersatztext. */
    grund: string;
}

const dateien = (n: number) => `${n} ${n === 1 ? 'Datei' : 'Dateien'}`;

interface StapelErgebnis {
    gesamt: number;
    fehler: readonly StapelFehler[];
    ordnerName: string | null;
    /** So viele Dateien kamen nach „Abbrechen“ nicht mehr dran. */
    abgebrochen?: number;
}

/**
 * Meldung nach dem letzten Upload eines Stapels: Erfolg mit Anzahl (und
 * Ordner, wenn alle in denselben gingen), nach „Abbrechen“ ein Hinweis, wie
 * viele abgelegt sind, und bei Fehlern, welche Dateien nicht klappten, mit Grund.
 */
export function stapelMeldung({ gesamt, fehler, ordnerName, abgebrochen = 0 }: StapelErgebnis): { art: 'erfolg' | 'hinweis' | 'fehler'; text: string } {
    const abgelegt = gesamt - fehler.length - abgebrochen;
    if (abgebrochen > 0) {
        const text = `${abgelegt} von ${gesamt} abgelegt, Rest abgebrochen.`;
        return fehler.length === 0 ? { art: 'hinweis', text } : { art: 'fehler', text: `${text} Nicht geklappt: ${fehlerListe(fehler)}` };
    }
    if (fehler.length === 0) {
        return { art: 'erfolg', text: ordnerName ? `${dateien(gesamt)} in „${ordnerName}“ abgelegt.` : `${dateien(gesamt)} abgelegt.` };
    }
    return { art: 'fehler', text: `${abgelegt} von ${gesamt} abgelegt. Nicht geklappt: ${fehlerListe(fehler)}` };
}

/** „a.jpg, b.jpg (Grund)“ bzw. je Datei mit eigenem Grund; lange Listen gekürzt. */
function fehlerListe(fehler: readonly StapelFehler[]): string {
    const genannt = fehler.slice(0, MAX_GENANNT);
    const rest = fehler.length - genannt.length;
    const einGrund = fehler.every((f) => f.grund === fehler[0].grund);
    const namen = genannt.map((f) => (einGrund ? f.name : `${f.name} (${f.grund})`)).join(', ') + (rest > 0 ? ` und ${rest} weitere` : '');
    return `${namen}${einGrund ? ` (${fehler[0].grund})` : ''}`;
}
