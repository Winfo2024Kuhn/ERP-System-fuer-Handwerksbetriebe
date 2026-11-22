/** Reine Uhrzeit-Regeln der Tagesansicht (ohne React, mit Test). */
import { validateTimeInput } from '../../components/ui/time-input';
import { getNextTempBookingId, istAbwesenheitTyp, type Buchung } from './kalenderTypen';

export type ZeitFeld = 'startZeit' | 'endeZeit';
/** Liefert den angezeigten Wert eines Zeitfelds (Entwurf oder gespeicherter Stand). */
export type ZeitEntwurf = (buchung: Buchung, feld: ZeitFeld) => string;
export type ZeitEntwuerfe = Record<string, string>;

export const entwurfSchluessel = (id: number, feld: ZeitFeld) => `${id}:${feld}`;

/** „HH:mm“ (auch mit Sekunden) in Minuten seit Mitternacht, ungültig = -1. */
export function parseZeit(zeit: string | null | undefined): number {
    const geprueft = validateTimeInput(zeit?.substring(0, 5) ?? '', { label: 'Uhrzeit', required: true });
    if (!geprueft.valid || !geprueft.value) return -1;
    const [h, m] = geprueft.value.split(':').map(Number);
    return h * 60 + m;
}

/** Bestehende Sekunden bleiben im Payload erhalten, solange das Zeitfeld nicht geändert wird. */
export function zeitEntwurfAus(entwuerfe: ZeitEntwuerfe): ZeitEntwurf {
    return (buchung, feld) => {
        const bestand = buchung[feld] ?? '';
        return entwuerfe[entwurfSchluessel(buchung.id, feld)] ??
            (buchung.id > 0 && /^\d{2}:\d{2}:\d{2}$/.test(bestand) ? bestand.substring(0, 5) : bestand);
    };
}

const minutenAlsZeit = (minuten: number) =>
    `${String(Math.floor(minuten / 60)).padStart(2, '0')}:${String(minuten % 60).padStart(2, '0')}`;

/** Ende liegt nicht nach dem Beginn (beide Zeiten gültig) – Nachtschichten werden nicht unterstützt. */
export function endeVorBeginn(buchung: Buchung, zeitEntwurf: ZeitEntwurf): boolean {
    if (istAbwesenheitTyp(buchung.typ)) return false;
    const start = parseZeit(zeitEntwurf(buchung, 'startZeit'));
    const ende = parseZeit(zeitEntwurf(buchung, 'endeZeit'));
    return start >= 0 && ende >= 0 && ende <= start;
}

/** Text für das Feld „Dauer“; über Mitternacht wird nicht gerechnet. */
export function dauerAnzeige(buchung: Buchung, zeitEntwurf: ZeitEntwurf): string {
    if (endeVorBeginn(buchung, zeitEntwurf)) return 'Ende vor Beginn';
    const start = parseZeit(zeitEntwurf(buchung, 'startZeit'));
    const ende = parseZeit(zeitEntwurf(buchung, 'endeZeit'));
    if (start < 0 || ende < 0) return '–';
    const diff = ende - start;
    return `${Math.floor(diff / 60)}:${(diff % 60).toString().padStart(2, '0')}h`;
}

/** Zeitliche Überschneidung zwischen Arbeitszeiten und Pausen (Abwesenheiten zählen nicht). */
export function hatUeberschneidungen(buchungen: Buchung[], zeitEntwurf: ZeitEntwurf): boolean {
    const sortiert = buchungen
        .filter(b => !istAbwesenheitTyp(b.typ) && b.startZeit && b.endeZeit)
        .sort((a, b) => parseZeit(zeitEntwurf(a, 'startZeit')) - parseZeit(zeitEntwurf(b, 'startZeit')));
    for (let i = 0; i < sortiert.length - 1; i++) {
        const ende = parseZeit(zeitEntwurf(sortiert[i], 'endeZeit'));
        const naechsterStart = parseZeit(zeitEntwurf(sortiert[i + 1], 'startZeit'));
        if (ende > naechsterStart && ende !== -1 && naechsterStart !== -1) return true;
    }
    return false;
}

/** Vorbelegung einer neuen Buchung: beginnt am Ende der letzten Buchung mit Ende. */
export function zeitenFuerNeueBuchung(buchungen: Buchung[], zeitEntwurf: ZeitEntwurf): { start: string; ende: string } {
    const mitEnde = buchungen
        .filter(b => b.endeZeit && b.endeZeit.trim() !== '' && !istAbwesenheitTyp(b.typ))
        .sort((a, b) => parseZeit(zeitEntwurf(a, 'endeZeit')) - parseZeit(zeitEntwurf(b, 'endeZeit')));
    const letzte = mitEnde[mitEnde.length - 1];
    if (!letzte) return { start: '08:00', ende: '16:00' };
    const start = zeitEntwurf(letzte, 'endeZeit').substring(0, 5);
    if (!/^\d{2}:\d{2}$/.test(start)) return { start: '08:00', ende: '16:00' };
    const minuten = parseZeit(start);
    if (minuten < 0 || minuten < 16 * 60) return { start, ende: '16:00' };
    if (minuten < 17 * 60) return { start, ende: '17:00' };
    return { start, ende: minutenAlsZeit(Math.min(23 * 60 + 59, minuten + 60)) };
}

export interface PausenSchnitt {
    buchungen: Buchung[];
    /** Gespeicherte Buchungen, die der Schnitt verändert hat. */
    geaenderteIds: number[];
    entwuerfe: ZeitEntwuerfe;
}

interface PausenSchnittEingabe {
    buchungen: Buchung[];
    pauseStart: string;
    pauseEnde: string;
    pauseId: number;
    neuePause?: Buchung;
    zeitEntwurf: ZeitEntwurf;
}

/**
 * Schneidet eine Pause in überlappende Arbeitszeiten ein: liegt sie mitten in einer Buchung,
 * wird diese geteilt; überlappt sie Anfang oder Ende, wird die Buchung gekürzt.
 */
export function schneidePauseEin({ buchungen, pauseStart, pauseEnde, pauseId, neuePause, zeitEntwurf }: PausenSchnittEingabe): PausenSchnitt {
    const pStart = parseZeit(pauseStart);
    const pEnde = parseZeit(pauseEnde);
    if (pStart < 0 || pEnde <= pStart) {
        return { buchungen: neuePause ? [...buchungen, neuePause] : buchungen, geaenderteIds: [], entwuerfe: {} };
    }
    const ergebnis: Buchung[] = [];
    const geaenderteIds: number[] = [];
    const entwuerfe: ZeitEntwuerfe = {};
    const merke = (b: Buchung, feld: ZeitFeld, wert: string) => {
        if (b.id > 0) geaenderteIds.push(b.id);
        entwuerfe[entwurfSchluessel(b.id, feld)] = wert;
    };
    let pauseEingefuegt = !neuePause;

    for (const b of buchungen) {
        if (b.id === pauseId || b.typ === 'PAUSE' || istAbwesenheitTyp(b.typ)) { ergebnis.push(b); continue; }
        const bEndeText = zeitEntwurf(b, 'endeZeit');
        const bStart = parseZeit(zeitEntwurf(b, 'startZeit'));
        const bEnde = parseZeit(bEndeText);
        if (bStart < 0 || bEnde <= bStart) { ergebnis.push(b); continue; }

        if (bStart < pStart && bEnde > pEnde) {
            // Pause liegt mitten in der Buchung: vorderer Teil behält die ID, hinterer wird neu.
            ergebnis.push({ ...b, endeZeit: pauseStart, dauerMinuten: pStart - bStart, dauerFormatiert: null });
            merke(b, 'endeZeit', pauseStart);
            if (neuePause && !pauseEingefuegt) { ergebnis.push(neuePause); pauseEingefuegt = true; }
            const fortsetzung: Buchung = { ...b, id: getNextTempBookingId(), startZeit: pauseEnde, endeZeit: bEndeText, dauerMinuten: bEnde - pEnde, dauerFormatiert: null };
            ergebnis.push(fortsetzung);
            entwuerfe[entwurfSchluessel(fortsetzung.id, 'startZeit')] = pauseEnde;
            entwuerfe[entwurfSchluessel(fortsetzung.id, 'endeZeit')] = bEndeText;
        } else if (bStart < pStart && bEnde > pStart && bEnde <= pEnde) {
            ergebnis.push({ ...b, endeZeit: pauseStart, dauerMinuten: pStart - bStart });
            merke(b, 'endeZeit', pauseStart);
        } else if (bStart >= pStart && bStart < pEnde && bEnde > pEnde) {
            ergebnis.push({ ...b, startZeit: pauseEnde, dauerMinuten: bEnde - pEnde });
            merke(b, 'startZeit', pauseEnde);
        } else {
            ergebnis.push(b);
        }
    }
    if (neuePause && !pauseEingefuegt) ergebnis.push(neuePause);

    const startVon = (b: Buchung) => (entwuerfe[entwurfSchluessel(b.id, 'startZeit')] || zeitEntwurf(b, 'startZeit') || b.startZeit || '').substring(0, 5);
    ergebnis.sort((a, b) => parseZeit(startVon(a)) - parseZeit(startVon(b)));
    return { buchungen: ergebnis, geaenderteIds, entwuerfe };
}
