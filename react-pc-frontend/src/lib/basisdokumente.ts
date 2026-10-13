import type { AusgangsGeschaeftsDokument } from '../types';

/**
 * Regeln für Basisdokumente (Dokumente ohne Vorgänger = eigene Vorgänge) im
 * Dialog „Dokument erstellen“. Spiegelt die Prüfung im Backend
 * (`AusgangsGeschaeftsDokumentService.validiereBasisdokument`).
 *
 * Stornierte Dokumente zählen nicht: Wurde eine direkt angelegte Rechnung
 * storniert, muss im Projekt wieder eine neue Rechnung möglich sein (Issue #262).
 * Stornorechnungen haben das Original als Vorgänger und sind nie Basisdokument.
 */
type BasisFelder = Pick<AusgangsGeschaeftsDokument, 'typ' | 'vorgaengerId' | 'storniert'>;

/** Nicht stornierte Dokumente ohne Vorgänger. */
export function aktiveBasisdokumente<T extends BasisFelder>(dokumente: readonly T[]): T[] {
    return dokumente.filter(d => !d.vorgaengerId && !d.storniert);
}

/** Gibt es schon einen aktiven (nicht stornierten) Vorgang? */
export function hatAktivesBasisdokument(dokumente: readonly BasisFelder[]): boolean {
    return aktiveBasisdokumente(dokumente).length > 0;
}

export interface BasisdokumentSperre {
    gesperrt: boolean;
    /** Erklärung für den Tooltip, nur gesetzt, wenn gesperrt. */
    grund?: string;
}

/**
 * Darf ein neues Basisdokument dieses Typs angelegt werden?
 * - Nachtragsangebot: nur, wenn ein aktives Angebot existiert.
 * - Alle anderen (Angebot, Auftragsbestätigung, Rechnung): nur als erster aktiver Vorgang.
 */
export function basisdokumentSperre(typ: string, dokumente: readonly BasisFelder[]): BasisdokumentSperre {
    const aktive = aktiveBasisdokumente(dokumente);
    if (typ === 'NACHTRAGSANGEBOT') {
        return aktive.some(d => d.typ === 'ANGEBOT')
            ? { gesperrt: false }
            : { gesperrt: true, grund: 'Zuerst ein Angebot anlegen' };
    }
    if (aktive.length === 0) {
        return { gesperrt: false };
    }
    return typ === 'ANGEBOT'
        ? { gesperrt: true, grund: 'Es existiert bereits ein Basisdokument – weitere nur als Nachtragsangebot' }
        : { gesperrt: true, grund: 'Es existiert bereits ein Basisdokument' };
}
