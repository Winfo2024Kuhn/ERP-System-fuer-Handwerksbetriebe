import { formatDatum, isoDatum, parseIsoDatum } from '../../lib/datum';

/**
 * Regeln für die Frage „Datum oben auf dem Dokument“ beim endgültigen
 * Drucken, Herunterladen und Senden. Reine Funktionen, alle Daten als
 * `YYYY-MM-DD` in lokaler Zeit (siehe `lib/datum.ts`).
 */

export type DatumWahlArt = 'heute' | 'bisher' | 'anders';

export interface DatumWahl {
    art: DatumWahlArt;
    /** Nur für `anders`: im Kalender gewähltes Datum, leer solange keins gewählt ist. */
    anderesDatum: string;
}

/** Vorauswahl beim Öffnen des Dialogs: heute. */
export const DATUM_WAHL_START: DatumWahl = { art: 'heute', anderesDatum: '' };

export interface DatumFrageLage {
    gespeichertesDatum: string | null | undefined;
    heute: string;
    /** Gebucht, angenommen, storniert oder nur lesend geöffnet. */
    gesperrt: boolean;
    /** Entwurf mit Wasserzeichen: ändert das Datum nie. */
    entwurf: boolean;
}

/** Nur fragen, wenn es etwas zu entscheiden gibt. */
export function mussDatumFragen({ gespeichertesDatum, heute, gesperrt, entwurf }: DatumFrageLage): boolean {
    return !gesperrt && !entwurf && !!gespeichertesDatum && gespeichertesDatum !== heute;
}

export function gewaehltesDatum(wahl: DatumWahl, gespeichertesDatum: string, heute: string): string {
    if (wahl.art === 'heute') return heute;
    if (wahl.art === 'bisher') return gespeichertesDatum;
    return wahl.anderesDatum;
}

export const ZUKUNFT_GESPERRT_TEXT = 'Eine Rechnung darf kein Datum in der Zukunft tragen. Bitte heute oder ein früheres Datum wählen.';

/** Grund, warum mit dieser Wahl nicht weitergemacht werden kann, sonst null. */
export function datumWahlFehler(wahl: DatumWahl, gespeichertesDatum: string, heute: string, istRechnung: boolean): string | null {
    const datum = gewaehltesDatum(wahl, gespeichertesDatum, heute);
    if (!datum) return 'Bitte ein Datum auswählen.';
    if (istRechnung && datum > heute) return ZUKUNFT_GESPERRT_TEXT;
    return null;
}

/** Fälligkeit als „TT.MM.JJJJ“: Datum plus Zahlungsziel in Tagen; leer ohne gültiges Datum. */
export function zahlbarBis(datumIso: string, zahlungszielTage: number): string {
    const datum = parseIsoDatum(datumIso);
    if (!datum) return '';
    datum.setDate(datum.getDate() + zahlungszielTage);
    return formatDatum(isoDatum(datum));
}
