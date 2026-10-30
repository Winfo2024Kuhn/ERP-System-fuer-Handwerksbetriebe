/** Reine Logik zum Annahme-Nachweis (ohne React), getrennt für Fast Refresh und Tests. */

/**
 * Rechtsnachweis einer digitalen Freigabe, wie ihn das Backend im Audit liefert
 * (siehe `FreigabeAuditDto`). Alle Felder fehlen bei älteren Annahmen.
 */
export interface FreigabeRechtsnachweisDaten {
    akzeptiertAm: string | null;
    codeBestaetigtAm: string | null;
    codeBestaetigtEmail: string | null;
    /** true = Beginn vor Fristablauf verlangt, false = nicht verlangt, null = keine Angabe. */
    vorzeitigerBeginn: boolean | null;
    widerrufStand: string | null;
    datenschutzStand: string | null;
    /** Wortlaut des Pflicht-Häkchens („Ich beauftrage … verbindlich …“). */
    bestaetigungText: string | null;
    knopfText: string | null;
    /** Wortlaut des freiwilligen Häkchens zum Arbeitsbeginn; `null` bei älteren Annahmen. */
    vorzeitigerBeginnText: string | null;
    /** Verbindlicher Bruttobetrag; nur gesetzt, wenn Wahlpositionen mitbeauftragt wurden. */
    akzeptierterBetrag: number | null;
    /** Mitbeauftragte Wahlpositionen, z. B. „3.2 Geländer Edelstahl“; leer = keine. */
    mitbeauftragteAlternativen: string[];
}

/** Ein Häkchen, das der Kunde bei der Annahme gesehen hat. */
export interface Haekchen {
    art: 'pflicht' | 'freiwillig';
    angehakt: boolean;
    /** Exakter Wortlaut; `null`, wenn er bei dieser Annahme nicht gespeichert wurde. */
    wortlaut: string | null;
}

/** Länge der gesetzlichen Widerrufsfrist in Tagen. */
const WIDERRUFSFRIST_TAGE = 14;

export const datumFormat: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit', year: 'numeric' };

/** `2026-10-13` → `13.10.2026`; Unlesbares bleibt, wie es ist. */
export function formatiereStand(stand: string | null): string | null {
    if (!stand) return null;
    const treffer = /^(\d{4})-(\d{2})-(\d{2})$/.exec(stand.trim());
    return treffer ? `${treffer[3]}.${treffer[2]}.${treffer[1]}` : stand;
}

/**
 * Letzter Tag der Widerrufsfrist: Annahmetag + 14 Tage (die Frist beginnt mit dem
 * Vertragsschluss). `null`, wenn kein gültiges Annahmedatum vorliegt.
 */
export function fristEnde(akzeptiertAm: string | null): Date | null {
    if (!akzeptiertAm) return null;
    const start = new Date(akzeptiertAm);
    if (Number.isNaN(start.getTime())) return null;
    const ende = new Date(start.getFullYear(), start.getMonth(), start.getDate());
    ende.setDate(ende.getDate() + WIDERRUFSFRIST_TAGE);
    return ende;
}

/** Text und Farbe für den Arbeitsbeginn; `null` bei Annahmen ohne Angabe. */
export function arbeitsbeginn(daten: Pick<FreigabeRechtsnachweisDaten, 'vorzeitigerBeginn' | 'akzeptiertAm'>):
    { text: string; ton: 'erlaubt' | 'warten' } | null {
    if (daten.vorzeitigerBeginn === true) {
        return { text: 'Sofort beginnen erlaubt – der Kunde hat es ausdrücklich verlangt.', ton: 'erlaubt' };
    }
    if (daten.vorzeitigerBeginn === false) {
        const ende = fristEnde(daten.akzeptiertAm);
        const bis = ende ? ` (bis ${ende.toLocaleDateString('de-DE', datumFormat)})` : '';
        return { text: `Erst nach Ablauf der Widerrufsfrist beginnen${bis}.`, ton: 'warten' };
    }
    return null;
}

/**
 * Häkchen, die der Kunde bei der Annahme gesehen hat, in der Reihenfolge der Annahmeseite.
 * Das Pflicht-Häkchen ist immer angehakt (ohne es geht die Annahme nicht) und steht nur da,
 * wenn sein Wortlaut gespeichert ist. Das freiwillige Häkchen zum Arbeitsbeginn steht da,
 * sobald die Entscheidung gespeichert ist – auch ohne Wortlaut (ältere Annahmen).
 */
export function haekchenListe(
    daten: Pick<FreigabeRechtsnachweisDaten, 'bestaetigungText' | 'vorzeitigerBeginn' | 'vorzeitigerBeginnText'>,
): Haekchen[] {
    const liste: Haekchen[] = [];
    const pflicht = daten.bestaetigungText?.trim();
    if (pflicht) liste.push({ art: 'pflicht', angehakt: true, wortlaut: pflicht });
    if (daten.vorzeitigerBeginn != null) {
        liste.push({
            art: 'freiwillig',
            angehakt: daten.vorzeitigerBeginn,
            wortlaut: daten.vorzeitigerBeginnText?.trim() || null,
        });
    }
    return liste;
}

/** `4950.5` → `4.950,50 €`. */
export function formatiereBetrag(betrag: number): string {
    return betrag.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });
}
