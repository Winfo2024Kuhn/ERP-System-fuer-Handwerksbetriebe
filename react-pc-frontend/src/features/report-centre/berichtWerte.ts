import { formatDatum } from '../../lib/datum';
import type { BerichtMitarbeiter } from './api';
import type { FilterFeld, FilterWerte, MitarbeiterFeld } from './berichtTypen';
import { jahresZeitraum, monatsname, monatsZeitraum, pruefeZeitraum, vormonat, type Zeitraum } from './zeitraum';

/** Grundwerte: Vormonat, Zeitraum = dieser Monat, Mitarbeiter-Vorauswahl offen. */
export function grundwerte(heute: Date, schalter: Record<string, boolean>): FilterWerte {
    const { jahr, monat } = vormonat(heute);
    return { jahr, monat, zeitraumArt: 'monat', ...monatsZeitraum(jahr, monat), mitarbeiterIds: null, schalter };
}

export function mitarbeiterFeld(felder: FilterFeld[]): MitarbeiterFeld | undefined {
    return felder.find((feld): feld is MitarbeiterFeld => feld.typ === 'mitarbeiter');
}

/** Gewählte Mitarbeiter-IDs – vor der ersten Änderung die Vorauswahl des Feldes. */
export function gewaehlteIds(feld: MitarbeiterFeld | undefined, werte: FilterWerte, alle: BerichtMitarbeiter[]): number[] {
    if (werte.mitarbeiterIds !== null) return werte.mitarbeiterIds;
    return feld?.alleVorauswaehlen ? alle.map(m => m.id) : [];
}

/** Gewählte Mitarbeiter in Listenreihenfolge (IDs, die es nicht mehr gibt, fallen weg). */
export function gewaehlteMitarbeiter(ids: number[], alle: BerichtMitarbeiter[]): BerichtMitarbeiter[] {
    const gewaehlt = new Set(ids);
    return alle.filter(m => gewaehlt.has(m.id));
}

/** Der Zeitraum, den die Zeitraum-Auswahl gerade meint. */
export function zeitraumAus(werte: FilterWerte): Zeitraum {
    if (werte.zeitraumArt === 'monat') return monatsZeitraum(werte.jahr, werte.monat);
    if (werte.zeitraumArt === 'jahr') return jahresZeitraum(werte.jahr);
    return { von: werte.von, bis: werte.bis };
}

function pruefeFeld(feld: FilterFeld, werte: FilterWerte, anzahlMitarbeiter: number, gesamt: number): string | null {
    switch (feld.typ) {
        case 'monat':
            return werte.monat >= 1 && werte.monat <= 12 ? null : 'Bitte einen Monat wählen.';
        case 'zeitraum': {
            const { von, bis } = zeitraumAus(werte);
            return pruefeZeitraum(von, bis);
        }
        case 'mitarbeiter':
            if (anzahlMitarbeiter < feld.min) return 'Bitte mindestens einen Mitarbeiter wählen.';
            if (anzahlMitarbeiter <= feld.max) return null;
            if (feld.alleErlaubt) {
                return anzahlMitarbeiter === gesamt ? null : `Bitte höchstens ${feld.max} Mitarbeiter auswählen oder alle.`;
            }
            return `Bitte höchstens ${feld.max} Mitarbeiter auf einmal wählen.`;
        case 'schalter':
            return null;
    }
}

/** Erste Meldung für ungültige Filterwerte, sonst `null`. `gesamt` = Zahl der auswählbaren Mitarbeiter. */
export function pruefeWerte(felder: FilterFeld[], werte: FilterWerte, anzahlMitarbeiter: number, gesamt: number): string | null {
    for (const feld of felder) {
        const meldung = pruefeFeld(feld, werte, anzahlMitarbeiter, gesamt);
        if (meldung) return meldung;
    }
    return null;
}

/** Name bis drei Mitarbeiter, darüber „Max Mustermann, Erika Musterfrau und 3 weitere“. */
export function mitarbeiterText(mitarbeiter: BerichtMitarbeiter[]): string {
    if (mitarbeiter.length === 0) return 'keiner';
    const namen = mitarbeiter.map(m => m.name);
    if (namen.length <= 3) return namen.join(', ');
    return `${namen.slice(0, 2).join(', ')} und ${namen.length - 2} weitere`;
}

function zeitraumText(werte: FilterWerte): string {
    if (werte.zeitraumArt === 'monat') return `${monatsname(werte.monat)} ${werte.jahr}`;
    if (werte.zeitraumArt === 'jahr') return `Ganzes Jahr ${werte.jahr}`;
    return `${formatDatum(werte.von)} bis ${formatDatum(werte.bis)}`;
}

/** Zusammenfassung der Auswahl als Zeilen „Beschriftung – Wert“ für den letzten Schritt. */
export function zusammenfassung(felder: FilterFeld[], werte: FilterWerte, mitarbeiter: BerichtMitarbeiter[]): [string, string][] {
    return felder.map((feld): [string, string] => {
        switch (feld.typ) {
            case 'monat': return ['Monat', `${monatsname(werte.monat)} ${werte.jahr}`];
            case 'zeitraum': return ['Zeitraum', zeitraumText(werte)];
            case 'mitarbeiter': return [`Mitarbeiter (${mitarbeiter.length})`, mitarbeiterText(mitarbeiter)];
            case 'schalter': return [feld.beschriftung, werte.schalter[feld.schluessel] ? 'ja' : 'nein'];
        }
    });
}
