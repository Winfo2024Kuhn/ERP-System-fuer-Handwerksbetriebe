import type { EmailItem, FolderType } from './emailCenterModel';

/** Ab so vielen Zeichen fragt die Suche den Server. */
export const SUCHE_MIN_ZEICHEN = 2;
/** Längster Suchbegriff, den der Server annimmt. */
export const SUCHE_MAX_ZEICHEN = 200;
/** Treffer je Seite. */
export const SUCHE_SEITENGROESSE = 25;
/** So lange wartet die Suche nach dem letzten Tastendruck. */
export const SUCHE_ENTPRELLEN_MS = 400;

/** „In diesem Ordner“ oder „Alle Ordner“. */
export type SuchModus = 'ordner' | 'alle';

/**
 * Wert für `ordner=` der Serversuche – oder `null`, wenn der Server nicht gefragt wird:
 * Im Entwürfe-Ordner filtert die Seite ihre Entwurfsliste selbst.
 */
export function suchOrdner(modus: SuchModus, aktiverOrdner: FolderType): string | null {
    if (modus === 'alle') return 'alle';
    return aktiverOrdner === 'drafts' ? null : aktiverOrdner;
}

/** Adresse einer Trefferseite. `URLSearchParams` maskiert den Begriff, damit er keine Parameter einschleust. */
export function buildEmailSucheUrl(begriff: string, ordner: string, seite: number, groesse = SUCHE_SEITENGROESSE): string {
    const params = new URLSearchParams({ q: begriff, ordner, page: String(seite), size: String(groesse) });
    return `/api/emails/suche?${params.toString()}`;
}

/** Liest eine Trefferseite `{ eintraege, hatMehr }`; Unerwartetes ergibt eine leere Seite. */
export function leseSuchSeite(daten: unknown): { eintraege: EmailItem[]; hatMehr: boolean } {
    if (!daten || typeof daten !== 'object') return { eintraege: [], hatMehr: false };
    const { eintraege, hatMehr } = daten as { eintraege?: unknown; hatMehr?: unknown };
    return { eintraege: Array.isArray(eintraege) ? eintraege as EmailItem[] : [], hatMehr: hatMehr === true };
}

export interface SuchStand {
    /** Zu welcher Suche (Ordner, Begriff, Durchlauf) der Stand gehört; Antworten anderer Suchen fallen weg. */
    schluessel: string;
    eintraege: EmailItem[];
    seite: number;
    hatMehr: boolean;
    status: 'aus' | 'laedt' | 'fertig' | 'fehler';
    laedtMehr: boolean;
    /** Nachladen ist gescheitert: Liste bleibt, automatisches Nachladen pausiert bis zum nächsten Versuch. */
    nachladeFehler: boolean;
}

export const LEERER_SUCHSTAND: SuchStand = {
    schluessel: '', eintraege: [], seite: 0, hatMehr: false, status: 'aus', laedtMehr: false, nachladeFehler: false,
};

export type SuchAktion =
    | { typ: 'neu'; schluessel: string; aktiv: boolean }
    | { typ: 'seite'; schluessel: string; seite: number; eintraege: EmailItem[]; hatMehr: boolean }
    | { typ: 'fehler'; schluessel: string }
    | { typ: 'mehrStart'; schluessel: string }
    | { typ: 'mehrFehler'; schluessel: string }
    | { typ: 'aendern'; aendern: (eintraege: EmailItem[]) => EmailItem[] };

/** Zustand der Trefferliste. Antworten einer älteren Suche (anderer Schlüssel) ändern nichts. */
export function suchReducer(stand: SuchStand, aktion: SuchAktion): SuchStand {
    if (aktion.typ === 'neu') {
        return { ...LEERER_SUCHSTAND, schluessel: aktion.schluessel, status: aktion.aktiv ? 'laedt' : 'aus' };
    }
    if (aktion.typ === 'aendern') return { ...stand, eintraege: aktion.aendern(stand.eintraege) };
    if (aktion.schluessel !== stand.schluessel) return stand;
    switch (aktion.typ) {
        case 'seite': {
            const bekannt = new Set(aktion.seite === 0 ? [] : stand.eintraege.map(e => e.id));
            const neue = aktion.eintraege.filter(e => !bekannt.has(e.id));
            return {
                ...stand, status: 'fertig', laedtMehr: false, seite: aktion.seite, hatMehr: aktion.hatMehr,
                eintraege: aktion.seite === 0 ? neue : [...stand.eintraege, ...neue],
            };
        }
        case 'fehler': return { ...stand, status: 'fehler', laedtMehr: false };
        case 'mehrStart': return { ...stand, laedtMehr: true, nachladeFehler: false };
        case 'mehrFehler': return { ...stand, laedtMehr: false, nachladeFehler: true };
    }
}
