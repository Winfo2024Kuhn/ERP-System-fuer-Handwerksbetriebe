/** Reine Regeln für „Alle Speichern“ in der Tagesansicht (ohne React, mit Test). */
import { validateTimeInput } from '../../components/ui/time-input';
import { ENDE_VOR_BEGINN_MELDUNG, istAbwesenheitTyp, type Buchung } from './kalenderTypen';
import { entwurfSchluessel, parseZeit, type ZeitEntwuerfe } from './buchungZeiten';

export const PROJEKT_UND_TAETIGKEIT_MELDUNG = 'Bitte für jede geänderte Buchung ein Projekt und eine Tätigkeit wählen.';

const hat = (entwuerfe: ZeitEntwuerfe, schluessel: string) => Object.prototype.hasOwnProperty.call(entwuerfe, schluessel);

/** Prüft eine neue oder geänderte Buchung; liefert eine Meldung oder die bereinigte Buchung. */
function pruefeBuchung(buchung: Buchung, entwuerfe: ZeitEntwuerfe): { meldung: string } | { buchung: Buchung } {
    if (buchung.typ !== 'PAUSE' && (!buchung.projektId || buchung.projektId <= 0 || !buchung.arbeitsgangId || buchung.arbeitsgangId <= 0)) {
        return { meldung: PROJEKT_UND_TAETIGKEIT_MELDUNG };
    }
    const startSchluessel = entwurfSchluessel(buchung.id, 'startZeit');
    const endeSchluessel = entwurfSchluessel(buchung.id, 'endeZeit');
    const hatStart = hat(entwuerfe, startSchluessel);
    const hatEnde = hat(entwuerfe, endeSchluessel);
    const start = validateTimeInput((hatStart ? entwuerfe[startSchluessel] : buchung.startZeit).substring(0, 5), { label: 'Beginn', required: true, forgiving: true });
    const ende = validateTimeInput((hatEnde ? entwuerfe[endeSchluessel] : (buchung.endeZeit || '')).substring(0, 5), { label: 'Ende', forgiving: true });
    if (!start.valid) return { meldung: start.message };
    if (!ende.valid) return { meldung: ende.message };
    // Nachtschichten über Mitternacht gibt es nicht: Ende muss nach dem Beginn liegen.
    if (start.value && ende.value && parseZeit(ende.value) <= parseZeit(start.value)) return { meldung: ENDE_VOR_BEGINN_MELDUNG };
    const startGeaendert = hatStart && start.value !== buchung.startZeit.substring(0, 5);
    const endeGeaendert = hatEnde && ende.value !== (buchung.endeZeit || '').substring(0, 5);
    if (!startGeaendert && !endeGeaendert) return { buchung };
    return {
        buchung: {
            ...buchung,
            ...(startGeaendert ? { startZeit: start.value ?? buchung.startZeit } : {}),
            ...(endeGeaendert ? { endeZeit: ende.value ?? null } : {}),
        },
    };
}

/**
 * Prüft vor dem ersten Schreiben sämtliche neuen und geänderten Buchungen.
 * Gleiche Meldungen (z. B. mehrere Buchungen ohne Tätigkeit) kommen nur einmal.
 */
export function pruefeVorDemSpeichern(buchungen: Buchung[], geaenderteIds: Set<number>, entwuerfe: ZeitEntwuerfe): { meldungen: string[]; buchungen: Buchung[] } {
    const meldungen = new Set<string>();
    const normalisiert = buchungen.map(buchung => {
        if (istAbwesenheitTyp(buchung.typ) || (buchung.id > 0 && !geaenderteIds.has(buchung.id))) return buchung;
        const ergebnis = pruefeBuchung(buchung, entwuerfe);
        if ('meldung' in ergebnis) { meldungen.add(ergebnis.meldung); return buchung; }
        return ergebnis.buchung;
    });
    return { meldungen: [...meldungen], buchungen: normalisiert };
}

/** Wird diese Buchung bei „Alle Speichern“ geschickt? */
export function mussGespeichertWerden(buchung: Buchung, geaenderteIds: Set<number>): boolean {
    const neuOderGeaendert = buchung.id < 0 || geaenderteIds.has(buchung.id);
    const projektOk = buchung.typ === 'PAUSE' || (!!buchung.projektId && buchung.projektId > 0);
    return neuOderGeaendert && projektOk && !!buchung.startZeit && !istAbwesenheitTyp(buchung.typ);
}

const mitSekunden = (zeit: string) => (zeit.length === 5 ? `${zeit}:00` : zeit);

/** Inhalt für POST (neu) bzw. PUT (geändert) einer Buchung. */
export function speicherInhalt(buchung: Buchung, mitarbeiterId: number, datum: string): Record<string, unknown> {
    const inhalt: Record<string, unknown> = {
        mitarbeiterId,
        projektId: buchung.projektId,
        arbeitsgangId: buchung.arbeitsgangId,
        startZeit: `${datum}T${mitSekunden(buchung.startZeit)}`,
        endeZeit: buchung.endeZeit?.trim() ? `${datum}T${mitSekunden(buchung.endeZeit)}` : null,
        notiz: buchung.notiz,
        produktkategorieId: buchung.produktkategorieId,
        typ: buchung.typ || 'ARBEIT', // PAUSE oder ARBEIT
    };
    // Für Updates (PUT): GoBD erfordert einen Änderungsgrund
    if (buchung.id > 0) inhalt.aenderungsgrund = 'Korrektur im Zeiterfassungskalender';
    return inhalt;
}

/** Gibt es geänderte, neue oder halb getippte Buchungen, die noch nicht gespeichert sind? */
export function hatUngespeicherteAenderungen(buchungen: Buchung[], geaenderteIds: Set<number>, entwuerfe: ZeitEntwuerfe): boolean {
    if (geaenderteIds.size > 0) return true;
    // Abwesenheiten tragen ebenfalls negative Anzeige-IDs, sind aber schon gespeichert.
    if (buchungen.some(b => b.id < 0 && !istAbwesenheitTyp(b.typ))) return true;
    return buchungen.some(b => (['startZeit', 'endeZeit'] as const).some(feld => {
        const schluessel = entwurfSchluessel(b.id, feld);
        return hat(entwuerfe, schluessel) && entwuerfe[schluessel].substring(0, 5) !== (b[feld] ?? '').substring(0, 5);
    }));
}
