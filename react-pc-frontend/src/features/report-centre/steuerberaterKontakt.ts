/** Steuerberater-Kontakte für die Stundenübermittlung und reine Hilfen dazu (ohne React). */

export interface SteuerberaterAnsprechpartner {
    id: number;
    anrede: string | null;
    vorname: string;
    nachname: string;
    email: string;
    telefon: string;
    istLohnAnsprechpartner: boolean;
}

export interface SteuerberaterKontakt {
    id: number;
    name: string;
    email: string;
    telefon: string;
    weitereEmails: string[];
    ansprechpartnerListe: SteuerberaterAnsprechpartner[];
}

export interface Option {
    value: string;
    label: string;
}

/** Bevorzugt den Lohn-Ansprechpartner, sonst den ersten der Liste, sonst `null`. */
function bevorzugt(liste: SteuerberaterAnsprechpartner[]): SteuerberaterAnsprechpartner | null {
    return liste.find(ap => ap.istLohnAnsprechpartner) || liste[0] || null;
}

/** Lohn-Ansprechpartner eines Steuerberaters, sonst der erste, sonst `null`. */
export function lohnAnsprechpartner(sb: SteuerberaterKontakt | null): SteuerberaterAnsprechpartner | null {
    return sb ? bevorzugt(sb.ansprechpartnerListe || []) : null;
}

/** Alle Adressen eines Steuerberaters ohne Doppelte (Kanzlei, weitere, Ansprechpartner). */
export function adressenVon(sb: SteuerberaterKontakt | null): string[] {
    if (!sb) return [];
    const alle = [sb.email, ...(sb.weitereEmails || []), ...(sb.ansprechpartnerListe || []).map(ap => ap.email)];
    return Array.from(new Set(alle.filter(Boolean)));
}

const vergleichbar = (adresse: string | null | undefined) => (adresse ?? '').trim().toLowerCase();

/**
 * Ansprechpartner, dem die Empfänger-Adresse gehört (Groß-/Kleinschreibung und
 * Leerzeichen egal). Teilen sich mehrere die Adresse, gewinnt der
 * Lohn-Ansprechpartner, sonst der erste. Kanzlei-, weitere oder fremde Adresse: `null`.
 */
export function ansprechpartnerFuerAdresse(sb: SteuerberaterKontakt | null, adresse: string): SteuerberaterAnsprechpartner | null {
    const gesucht = vergleichbar(adresse);
    if (!sb || !gesucht) return null;
    return bevorzugt((sb.ansprechpartnerListe || []).filter(ap => vergleichbar(ap.email) === gesucht));
}

export function ansprechpartnerOption(ap: SteuerberaterAnsprechpartner): Option {
    const name = [ap.vorname, ap.nachname].filter(Boolean).join(' ') || '(ohne Namen)';
    return { value: String(ap.id), label: ap.istLohnAnsprechpartner ? `${name} · Löhne` : name };
}
