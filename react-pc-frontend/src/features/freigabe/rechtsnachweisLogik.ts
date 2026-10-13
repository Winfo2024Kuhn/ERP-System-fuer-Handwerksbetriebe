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
    bestaetigungText: string | null;
    knopfText: string | null;
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
