/**
 * Geteilte Typen und Wording/Farb-Zuordnung für die Phasen einer
 * Langzeitkrankmeldung (Lohnfortzahlung, Krankengeld, Wiedereingliederung).
 *
 * Wording ist wörtlich aus der Spec übernommen — kein Fachchinesisch wie
 * "Entgeltfortzahlungszeitraum" oder "AU-Zeitraum".
 */

export type PhasenTyp = 'LOHNFORTZAHLUNG' | 'KRANKENGELD' | 'WIEDEREINGLIEDERUNG';

export interface Phase {
    id: number;
    typ: PhasenTyp;
    label: string;
    vonDatum: string;
    bisDatum: string | null;
    stundenProTag: number | null;
}

export const PHASEN_LABEL: Record<PhasenTyp, string> = {
    LOHNFORTZAHLUNG: 'Lohnfortzahlung durch den Betrieb',
    KRANKENGELD: 'Krankengeld der Krankenkasse',
    WIEDEREINGLIEDERUNG: 'Wiedereingliederung',
};

// Dieselbe Badge-Familie, die Urlaubsantraege.tsx schon für Typ-Badges nutzt
// (bg-*-100 text-*-800). Rose bleibt der Primäraktion vorbehalten.
export const PHASEN_BADGE: Record<PhasenTyp, string> = {
    LOHNFORTZAHLUNG: 'bg-amber-100 text-amber-800',
    KRANKENGELD: 'bg-blue-100 text-blue-800',
    WIEDEREINGLIEDERUNG: 'bg-teal-100 text-teal-800',
};

/** Formatiert ein ISO-Datum ("2026-04-01" oder "2026-04-01T00:00:00") als "01.04.2026". */
export function formatDatum(iso: string): string {
    const [jahr, monat, tag] = iso.slice(0, 10).split('-');
    return `${tag}.${monat}.${jahr}`;
}
