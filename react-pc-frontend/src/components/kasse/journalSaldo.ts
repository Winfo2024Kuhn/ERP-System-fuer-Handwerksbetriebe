import type { KassenBewegung } from '../../types';
import { KATEGORIE_LABELS } from './belegFormat';

export interface JournalZeile extends KassenBewegung {
    anzeigeNummer: number | null;
    vorlaeufigeNummer: number;
    einnahme: number | null;
    ausgabe: number | null;
}

/** Handwerker-Sprache fuer Kategorie-Badge und Suche im Journal. */
export function journalKategorieLabel(kategorie: KassenBewegung['kategorie']): string {
    if (kategorie === 'PRIVATEINLAGE') return 'Eigenes Geld eingelegt';
    if (kategorie === 'PRIVATENTNAHME') return 'Geld privat entnommen';
    return KATEGORIE_LABELS[kategorie];
}

/** Nummeriert vor dem Filtern; der laufende Bestand kommt unverändert vom Server. */
export function baueJournal(bewegungen: KassenBewegung[], suche: string): JournalZeile[] {
    const term = suche.trim().toLocaleLowerCase('de-DE');
    return bewegungen.map((bewegung, index) => ({
        ...bewegung,
        anzeigeNummer: bewegung.laufendeNummer ?? null,
        vorlaeufigeNummer: index + 1,
        // Auch Nullbuchungen sichtbar halten, wie bisher auf der Einnahmenseite.
        einnahme: bewegung.betrag >= 0 ? bewegung.betrag : null,
        ausgabe: bewegung.betrag < 0 ? Math.abs(bewegung.betrag) : null,
    })).filter(bewegung => !term || [bewegung.beschreibung, bewegung.lieferantName,
        journalKategorieLabel(bewegung.kategorie), KATEGORIE_LABELS[bewegung.kategorie]].some(text => text?.toLocaleLowerCase('de-DE').includes(term)));
}
