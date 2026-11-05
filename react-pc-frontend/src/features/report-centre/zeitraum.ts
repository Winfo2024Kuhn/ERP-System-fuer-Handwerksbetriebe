/**
 * Reine Hilfsfunktionen für Zeiträume im Report Centre (ohne React, voll getestet).
 *
 * Alle Daten sind ISO-Strings `YYYY-MM-DD` im Kalender (keine Zeitzonen-Rechnung):
 * gerechnet wird in UTC, damit Sommer-/Winterzeit keinen Tag verschluckt.
 */

export const MONATSNAMEN = [
    'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember',
] as const;

/** Längster Zeitraum für einen Mitarbeiter-Bericht (ein Schaltjahr). */
export const MAX_TAGE_ZEITRAUM = 366;

export interface MonatJahr {
    jahr: number;
    monat: number;
}

export interface Zeitraum {
    von: string;
    bis: string;
}

/** „Oktober“ für 10 – leer bei ungültigem Monat. */
export function monatsname(monat: number): string {
    return MONATSNAMEN[monat - 1] ?? '';
}

/** Der Monat vor dem von `heute` (im Januar: Dezember des Vorjahres). */
export function vormonat(heute: Date): MonatJahr {
    const monat = heute.getMonth(); // 0-basiert: aktueller Monat - 1 als 1-basierter Wert
    return monat === 0
        ? { jahr: heute.getFullYear() - 1, monat: 12 }
        : { jahr: heute.getFullYear(), monat };
}

/** Wählbare Jahre: vier Jahre zurück bis ein Jahr voraus. */
export function waehlbareJahre(aktuellesJahr: number): number[] {
    return Array.from({ length: 6 }, (_, index) => aktuellesJahr - 4 + index);
}

function iso(jahr: number, monat: number, tag: number): string {
    return `${String(jahr).padStart(4, '0')}-${String(monat).padStart(2, '0')}-${String(tag).padStart(2, '0')}`;
}

/** Erster bis letzter Tag eines Monats. */
export function monatsZeitraum(jahr: number, monat: number): Zeitraum {
    const letzterTag = new Date(Date.UTC(jahr, monat, 0)).getUTCDate();
    return { von: iso(jahr, monat, 1), bis: iso(jahr, monat, letzterTag) };
}

/** 1. Januar bis 31. Dezember. */
export function jahresZeitraum(jahr: number): Zeitraum {
    return { von: iso(jahr, 1, 1), bis: iso(jahr, 12, 31) };
}

const ISO_DATUM = /^(\d{4})-(\d{2})-(\d{2})$/;

/** Tage seit 1970 für ein gültiges ISO-Datum, sonst `null` (auch für 2026-02-30). */
function tagNummer(datum: string): number | null {
    const teile = ISO_DATUM.exec(datum);
    if (!teile) return null;
    const [jahr, monat, tag] = [Number(teile[1]), Number(teile[2]), Number(teile[3])];
    const zeit = Date.UTC(jahr, monat - 1, tag);
    const geprueft = new Date(zeit);
    if (geprueft.getUTCFullYear() !== jahr || geprueft.getUTCMonth() !== monat - 1 || geprueft.getUTCDate() !== tag) {
        return null;
    }
    return zeit / 86_400_000;
}

/** Anzahl Tage von–bis einschließlich beider Tage; `null` bei ungültigem Datum. */
export function tageImZeitraum(von: string, bis: string): number | null {
    const start = tagNummer(von);
    const ende = tagNummer(bis);
    if (start === null || ende === null) return null;
    return ende - start + 1;
}

/**
 * Prüft einen frei gewählten Zeitraum und liefert eine Meldung in einfacher
 * Sprache – oder `null`, wenn alles passt.
 */
export function pruefeZeitraum(von: string, bis: string): string | null {
    if (!von || !bis) return 'Bitte Anfang und Ende des Zeitraums wählen.';
    const tage = tageImZeitraum(von, bis);
    if (tage === null) return 'Bitte ein gültiges Datum wählen.';
    if (tage < 1) return 'Das Ende darf nicht vor dem Anfang liegen.';
    if (tage > MAX_TAGE_ZEITRAUM) return `Der Zeitraum darf höchstens ${MAX_TAGE_ZEITRAUM} Tage lang sein.`;
    return null;
}
