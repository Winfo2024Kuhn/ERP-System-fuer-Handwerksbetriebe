import { isoDatum } from '../../lib/datum';
import { domainVon, extractEmailAddress, isSingleEmailAddress } from '../../lib/emailAddress';
import { istFreemailDomain } from '../../lib/freemailDomains';
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

const FORMAT_BEISPIEL = 'bitte prüfen (Beispiel: name@kanzlei.de).';

/** Genau eine Adresse ohne Anzeigenamen, z. B. `name@kanzlei.de`. */
function istEinzelneAdresse(wert: string): boolean {
    return isSingleEmailAddress(wert) && extractEmailAddress(wert) === wert.trim();
}

/**
 * Prüft den Entwurf vor dem Speichern: Name und Haupt-E-Mail sind Pflicht
 * (das Backend lehnt eine fehlende E-Mail ab), „Gültig ab“ darf leer bleiben.
 * Die Haupt-E-Mail und jede ausgefüllte weitere E-Mail muss genau eine Adresse
 * ohne Anzeigenamen sein; leere weitere Zeilen stören nicht.
 * Liefert die Meldung für den Nutzer oder `null`, wenn alles passt.
 */
export function pruefeSteuerberaterEntwurf(entwurf: Vorher): string | null {
    if (!entwurf?.name?.trim()) return 'Bitte Namen eintragen.';
    if (!entwurf.email?.trim()) return 'Bitte Haupt-E-Mail eintragen.';
    if (!istEinzelneAdresse(entwurf.email)) return `Die Haupt-E-Mail sieht nicht richtig aus, ${FORMAT_BEISPIEL}`;
    const falsch = (entwurf.weitereEmails || []).findIndex(e => e.trim() && !istEinzelneAdresse(e));
    if (falsch >= 0) return `Die weitere E-Mail-Adresse ${falsch + 1} sieht nicht richtig aus, ${FORMAT_BEISPIEL}`;
    return null;
}

/**
 * Haken „andere Adressen der Kanzlei erkennen“ ist gesetzt (Vorgabe: an),
 * die Haupt-E-Mail liegt aber bei einem Freemail-Anbieter. Dann wertet das
 * Backend nur die eingetragenen Adressen (Haupt- und weitere E-Mails) aus.
 */
export function kanzleiHakenGreiftNicht(entwurf: Vorher): boolean {
    if (!entwurf || entwurf.autoProcessEmails === false) return false;
    return istFreemailDomain(domainVon(entwurf.email));
}
