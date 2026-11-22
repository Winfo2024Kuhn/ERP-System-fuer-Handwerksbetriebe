/** Gemeinsame Typen und Konstanten des Zeiterfassungs-Kalenders und seiner Tagesansicht. */

export interface Mitarbeiter {
    id: number;
    vorname: string;
    nachname: string;
    aktiv?: boolean;
    fuehrtZeitkonto?: boolean;
    istGeschaeftsfuehrer?: boolean;
}

export interface Projekt {
    id: number;
    bauvorhaben: string;
    auftragsnummer: string;
    kunde?: string;
    abgeschlossen?: boolean;
}

export interface Arbeitsgang {
    id: number;
    beschreibung: string;
}

export type AbwesenheitTyp = 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH';
/** Der Server schickt gespeicherte Arbeitszeit als 'ARBEIT', neue Zeilen haben keinen Typ. */
export type BuchungTyp = AbwesenheitTyp | 'PAUSE' | 'ARBEIT';

export interface Buchung {
    id: number;
    projektId: number;
    projektName: string;
    arbeitsgangId?: number;
    arbeitsgangName: string;
    produktkategorieId?: number | null;
    produktkategorieName?: string | null;
    startZeit: string; // HH:mm:ss formatiert vom Backend für Anzeige, oder HH:mm für Input
    endeZeit: string | null;
    dauerMinuten: number | null;
    dauerFormatiert: string | null;
    notiz: string | null;
    typ?: BuchungTyp | null;
    abwesenheitId?: number; // ID in der Abwesenheit-Tabelle (für korrektes Löschen)
}

export interface KalenderTag {
    datum: string;
    wochentag: number;
    istFeiertag: boolean;
    feiertagName: string | null;
    sollStunden: number;
    istStunden: number;
    buchungen: Buchung[];
}

export interface KalenderData {
    jahr: number;
    monat: number;
    tage: KalenderTag[];
    sollStundenMonat: number;
    istStundenMonat: number;
    differenz: number;
    /** Nur im Browser: für welchen Mitarbeiter/Jahr/Monat geladen („id/jahr/monat“). */
    schluessel?: string;
}

export interface Monatsabschluss {
    mitarbeiterId: number; jahr: number; monat: number; festgeschrieben: boolean;
    version: number | null; festgeschriebenAm: string | null; festgeschriebenVonMitarbeiterId: number | null;
    istStunden: number; sollStunden: number; abwesenheitsStunden: number; feiertagsStunden: number;
    korrekturStunden: number; gesamtIst: number; differenz: number;
    audit: { id: number; aktion: 'ABSCHLIESSEN' | 'OEFFNEN'; akteurMitarbeiterId: number; akteurName: string; zeitpunkt: string }[];
}

export const ABWESENHEIT_LABEL: Record<AbwesenheitTyp, string> = {
    URLAUB: 'Urlaub',
    KRANKHEIT: 'Krankheit',
    FORTBILDUNG: 'Fortbildung',
    ZEITAUSGLEICH: 'Zeitausgleich',
};

export const ENDE_VOR_BEGINN_MELDUNG = 'Bei einer Buchung liegt das Ende vor dem Beginn. Buchungen über Mitternacht bitte auf zwei Tage aufteilen.';

const ABWESENHEIT_TYPEN: readonly string[] = ['URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH'];

/** Urlaub, Krankheit, Fortbildung, Zeitausgleich – nie Arbeit ('ARBEIT' oder ohne Typ) und Pause. */
export function istAbwesenheitTyp(typ: Buchung['typ']): typ is AbwesenheitTyp {
    return !!typ && ABWESENHEIT_TYPEN.includes(typ);
}

let nextTempBookingCounter = 0;
/** Vorläufige (negative) ID für eine noch nicht gespeicherte Buchung. */
export function getNextTempBookingId(): number {
    nextTempBookingCounter += 1;
    return -nextTempBookingCounter;
}
