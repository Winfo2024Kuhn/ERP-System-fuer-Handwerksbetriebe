import { isoDatum } from '../../lib/datum';
import type { SteuerberaterAnsprechpartner, SteuerberaterEntwurf } from './firmaTypen';

// Reine Änderungen am Steuerberater im Dialog; jede liefert einen neuen Entwurf.
type Vorher = SteuerberaterEntwurf | null;

/** Vorbelegung für „Neuer Steuerberater“. */
export const neuerSteuerberater = (heute: Date): SteuerberaterEntwurf => ({
    aktiv: true, autoProcessEmails: true, gueltigAb: isoDatum(heute),
});

export const weitereEmailHinzufuegen = (prev: Vorher): SteuerberaterEntwurf => ({
    ...prev,
    weitereEmails: [...(prev?.weitereEmails || []), ''],
});

export function weitereEmailAendern(prev: Vorher, idx: number, wert: string): SteuerberaterEntwurf {
    const next = [...(prev?.weitereEmails || [])];
    next[idx] = wert;
    return { ...prev, weitereEmails: next };
}

export const weitereEmailEntfernen = (prev: Vorher, idx: number): SteuerberaterEntwurf => ({
    ...prev,
    weitereEmails: (prev?.weitereEmails || []).filter((_, i) => i !== idx),
});

/** Der erste Ansprechpartner wird gleich Empfänger der Stundenaufstellung. */
export function ansprechpartnerHinzufuegen(prev: Vorher): SteuerberaterEntwurf {
    const liste = prev?.ansprechpartnerListe || [];
    return {
        ...prev,
        ansprechpartnerListe: [...liste, {
            anrede: 'HERR', vorname: '', nachname: '', email: '', telefon: '',
            istLohnAnsprechpartner: liste.length === 0,
        }],
    };
}

export function ansprechpartnerAendern(prev: Vorher, idx: number, teil: Partial<SteuerberaterAnsprechpartner>): SteuerberaterEntwurf {
    const next = [...(prev?.ansprechpartnerListe || [])];
    next[idx] = { ...next[idx], ...teil };
    return { ...prev, ansprechpartnerListe: next };
}

export const ansprechpartnerEntfernen = (prev: Vorher, idx: number): SteuerberaterEntwurf => ({
    ...prev,
    ansprechpartnerListe: (prev?.ansprechpartnerListe || []).filter((_, i) => i !== idx),
});

/** Genau ein Ansprechpartner ist für die Löhne zuständig. */
export const lohnAnsprechpartnerSetzen = (prev: Vorher, idx: number): SteuerberaterEntwurf => ({
    ...prev,
    ansprechpartnerListe: (prev?.ansprechpartnerListe || []).map((a, i) => ({ ...a, istLohnAnsprechpartner: i === idx })),
});

/**
 * Prüft den Entwurf vor dem Speichern: Name und Haupt-E-Mail sind Pflicht
 * (das Backend lehnt eine fehlende E-Mail ab), „Gültig ab“ darf leer bleiben.
 * Liefert die Meldung für den Nutzer oder `null`, wenn alles passt.
 */
export function pruefeSteuerberaterEntwurf(entwurf: Vorher): string | null {
    if (!entwurf?.name?.trim()) return 'Bitte Namen eintragen.';
    if (!entwurf.email?.trim()) return 'Bitte Haupt-E-Mail eintragen.';
    return null;
}
