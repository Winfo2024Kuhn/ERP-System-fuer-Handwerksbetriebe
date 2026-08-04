/**
 * Monats-Rechnerei rund um den Kassenbuch-Abschluss.
 *
 * Liegt bewusst neben der Komponente statt in ihr: Die React-Fast-Refresh-Regel
 * lässt in Komponenten-Dateien nur Komponenten-Exporte zu — und ganz nebenbei
 * ist die Logik hier einzeln testbar, was bei einer Monatsgrenze (Dezember →
 * Januar) auch nötig ist.
 */

export const MONATSNAMEN = [
    'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember',
] as const;

export function monatLabel(jahr: number, monat: number): string {
    return `${MONATSNAMEN[monat - 1]} ${jahr}`;
}

/**
 * Der Monat, der als Nächstes zum Abschluss ansteht.
 *
 * Die Monate müssen lückenlos aufeinander folgen, also ist es immer der Monat
 * direkt nach dem zuletzt abgeschlossenen. Wurde noch nie abgeschlossen, ist
 * der Vormonat der erste, der überhaupt vorbei ist — der laufende Monat lässt
 * sich erst ab dem Ersten des Folgemonats abschließen.
 *
 * @param letzterAbschluss "JJJJ-MM" des zuletzt abgeschlossenen Monats, oder null
 * @param heute            Bezugsdatum; nur für Tests abweichend zu setzen
 */
export function naechsterOffenerMonat(
    letzterAbschluss: string | null,
    heute: Date = new Date(),
): { jahr: number; monat: number } {
    if (letzterAbschluss) {
        const [jahr, monat] = letzterAbschluss.split('-').map(Number);
        if (Number.isFinite(jahr) && Number.isFinite(monat) && monat >= 1 && monat <= 12) {
            return monat === 12 ? { jahr: jahr + 1, monat: 1 } : { jahr, monat: monat + 1 };
        }
        // Unlesbarer Wert vom Server: lieber auf den Vormonat zurückfallen als
        // einen sinnlosen Monat anzubieten.
    }
    const vormonat = new Date(heute.getFullYear(), heute.getMonth() - 1, 1);
    return { jahr: vormonat.getFullYear(), monat: vormonat.getMonth() + 1 };
}

/** Deutsche Geldanzeige ohne Währungszeichen — das setzt die Oberfläche selbst. */
export function euro(betrag: number): string {
    return betrag.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
