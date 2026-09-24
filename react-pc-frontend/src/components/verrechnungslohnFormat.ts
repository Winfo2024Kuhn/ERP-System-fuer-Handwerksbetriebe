/**
 * Zahlen-Ein- und Ausgabe für den Stundensatz-Rechner
 * ("Was muss meine Stunde kosten?").
 *
 * Bewusst ohne React, damit die Rechenregeln einzeln testbar sind.
 */

const eur = new Intl.NumberFormat('de-DE', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
});

const stunden = new Intl.NumberFormat('de-DE', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 1,
});

const dezimal = new Intl.NumberFormat('de-DE', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
});

export const formatEur = (v: number): string => eur.format(Number.isFinite(v) ? v : 0);

export const formatHours = (v: number): string =>
    `${stunden.format(Number.isFinite(v) ? v : 0)} h`;

/** Wert für ein Eingabefeld: "1.234,56" – gleiche Schreibweise wie die Anzeige. */
export const formatEingabe = (v: number): string => dezimal.format(Number.isFinite(v) ? v : 0);

/** "1.234" bzw. "1.234.567" – Punkte als Tausendertrenner, ohne Nachkommastelle. */
const NUR_TAUSENDERPUNKTE = /^-?\d{1,3}(\.\d{3})+$/;

/**
 * Liest eine vom Nutzer getippte Zahl. Akzeptiert deutsche Schreibweise
 * ("1.234,56"), englische ("1234.56") und reine Ziffern.
 *
 * Wichtig: Punkte dürfen nur dann als Tausendertrenner entfernt werden, wenn
 * sie auch welche sind. Vorher wurde jeder Punkt gelöscht – dadurch machte
 * schon Reinklicken und Rausklicken aus "1.234,56" den Wert 123456.
 *
 * @returns die Zahl oder null, wenn die Eingabe unbrauchbar ist
 */
export const parseDecimal = (raw: string): number | null => {
    const roh = raw.trim();
    if (!roh) return null;

    let normalisiert: string;
    if (roh.includes(',')) {
        // Deutsches Format: Komma ist das Dezimalzeichen, Punkte sind Trenner.
        normalisiert = roh.replace(/\./g, '').replace(',', '.');
    } else if (NUR_TAUSENDERPUNKTE.test(roh)) {
        // "1.234" bzw. "1.234.567" – hier sind die Punkte Tausendertrenner.
        normalisiert = roh.replace(/\./g, '');
    } else {
        // "1234", "1234.56" – unverändert übernehmen.
        normalisiert = roh;
    }

    if (!/^-?\d*\.?\d*$/.test(normalisiert) || !/\d/.test(normalisiert)) return null;
    const n = Number(normalisiert);
    return Number.isFinite(n) ? n : null;
};

/**
 * Holt die verständliche Fehlermeldung aus einer fehlgeschlagenen Antwort.
 *
 * Das Backend liefert bei fachlicher Ablehnung einen ApiError mit `message`
 * (bzw. `reason`/`error`). Ohne das sieht der Nutzer nur "Status 400" und
 * weiß nicht, was er ändern soll.
 */
export const leseFehlermeldung = async (res: Response, fallback: string): Promise<string> => {
    try {
        const body = await res.json();
        const text = body?.message ?? body?.reason ?? body?.error;
        if (typeof text === 'string' && text.trim()) return text.trim();
    } catch {
        // kein oder kaputtes JSON – dann eben der Fallback
    }
    return `${fallback} (Status ${res.status}).`;
};

/** Hält einen Prozentwert aus einem Zahlenfeld zwischen 0 und 100. */
export const clampPercent = (raw: string): number => {
    const n = Number(raw);
    if (!Number.isFinite(n)) return 0;
    return Math.max(0, Math.min(100, Math.round(n)));
};
