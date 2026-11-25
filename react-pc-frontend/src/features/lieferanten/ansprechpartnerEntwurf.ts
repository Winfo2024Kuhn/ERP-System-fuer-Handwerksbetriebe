import { extractEmailAddress, isSingleEmailAddress } from '../../lib/emailAddress';

/** Ansprechpartner eines Lieferanten, wie ihn `/api/lieferanten/{id}/ansprechpartner` liefert. */
export interface LieferantAnsprechpartner {
    id: number;
    /** HERR, FRAU, DAMEN_HERREN, FAMILIE (wie `ANREDE_AUSWAHL`) oder null. */
    anrede: string | null;
    vorname: string | null;
    nachname: string;
    /** Freier Text, z. B. „Außendienst“, „Innendienst“, „Buchhaltung“. */
    aufgabe: string | null;
    telefon: string | null;
    mobiltelefon: string | null;
    email: string | null;
    notiz: string | null;
}

/** Body für POST/PUT: dieselben Felder ohne `id`, leere Felder als `null`. */
export type AnsprechpartnerAnfrage = Omit<LieferantAnsprechpartner, 'id' | 'nachname'> & { nachname: string };

/** Formularstand im Dialog: alles als Text, leer statt null. */
export type AnsprechpartnerEntwurf = { [K in keyof AnsprechpartnerAnfrage]: string };

export type EntwurfFeld = keyof AnsprechpartnerEntwurf;

/** Höchstlängen wie im Backend (API-Vertrag). */
export const MAX_LAENGE: Readonly<Record<Exclude<EntwurfFeld, 'anrede'>, number>> = {
    vorname: 100,
    nachname: 255,
    aufgabe: 100,
    telefon: 50,
    mobiltelefon: 50,
    email: 255,
    notiz: 1000,
};

const FELD_NAME: Readonly<Record<Exclude<EntwurfFeld, 'anrede'>, string>> = {
    vorname: 'Der Vorname',
    nachname: 'Der Nachname',
    aufgabe: 'Die Aufgabe',
    telefon: 'Die Telefonnummer',
    mobiltelefon: 'Die Handynummer',
    email: 'Die E-Mail-Adresse',
    notiz: 'Die Notiz',
};

/** Anrede vor dem Namen in Listen; DAMEN_HERREN passt zu keiner einzelnen Person. */
const ANREDE_VOR_NAME: Readonly<Record<string, string>> = { HERR: 'Herr', FRAU: 'Frau', FAMILIE: 'Familie' };

export function leererEntwurf(): AnsprechpartnerEntwurf {
    return { anrede: '', vorname: '', nachname: '', aufgabe: '', telefon: '', mobiltelefon: '', email: '', notiz: '' };
}

export function entwurfAus(ap: LieferantAnsprechpartner): AnsprechpartnerEntwurf {
    return {
        anrede: ap.anrede ?? '',
        vorname: ap.vorname ?? '',
        nachname: ap.nachname ?? '',
        aufgabe: ap.aufgabe ?? '',
        telefon: ap.telefon ?? '',
        mobiltelefon: ap.mobiltelefon ?? '',
        email: ap.email ?? '',
        notiz: ap.notiz ?? '',
    };
}

/** „Herr Max Mustermann“; leere Teile fallen weg. */
export function anzeigeName(ap: Pick<LieferantAnsprechpartner, 'anrede' | 'vorname' | 'nachname'>): string {
    const anrede = ANREDE_VOR_NAME[(ap.anrede ?? '').trim().toUpperCase()];
    return [anrede, ap.vorname?.trim(), ap.nachname?.trim()].filter(Boolean).join(' ');
}

/**
 * Genau eine Adresse ohne Anzeigenamen, z. B. `name@firma.de`, so streng wie das Backend:
 * Domain aus Teilen ohne Punkt-Folgen (kein `..`, kein Punkt am Ende), im lokalen Teil
 * kein `?` oder `&` (sonst könnte ein `mailto:`-Link Kopien oder Betreff einschleusen).
 */
const EMAIL_FORMAT = /^[^@\s?&]+@[^@\s.]+(\.[^@\s.]+)+$/;

function istEinzelneAdresse(wert: string): boolean {
    const adresse = wert.trim();
    return isSingleEmailAddress(adresse) && extractEmailAddress(adresse) === adresse && EMAIL_FORMAT.test(adresse);
}

export interface EntwurfFehler {
    feld: EntwurfFeld;
    meldung: string;
}

/**
 * Prüft den Entwurf vor dem Speichern: Nachname ist Pflicht, eine eingetragene
 * E-Mail-Adresse muss eine einzelne gültige Adresse sein, kein Feld ist länger
 * als im Backend erlaubt. Liefert den ersten Fehler oder `null`.
 */
export function pruefeEntwurf(entwurf: AnsprechpartnerEntwurf): EntwurfFehler | null {
    if (!entwurf.nachname.trim()) return { feld: 'nachname', meldung: 'Bitte einen Nachnamen eintragen.' };
    for (const feld of Object.keys(MAX_LAENGE) as (keyof typeof MAX_LAENGE)[]) {
        const max = MAX_LAENGE[feld];
        if (entwurf[feld].trim().length > max) {
            return { feld, meldung: `${FELD_NAME[feld]} ist zu lang (höchstens ${max} Zeichen).` };
        }
    }
    const email = entwurf.email.trim();
    if (email && !istEinzelneAdresse(email)) {
        return { feld: 'email', meldung: 'Die E-Mail-Adresse sieht nicht richtig aus (Beispiel: name@firma.de).' };
    }
    return null;
}

/** Baut den Request-Body: Text gekürzt, leere Felder als `null`. */
export function zuAnfrage(entwurf: AnsprechpartnerEntwurf): AnsprechpartnerAnfrage {
    const wert = (text: string) => text.trim() || null;
    return {
        anrede: wert(entwurf.anrede),
        vorname: wert(entwurf.vorname),
        nachname: entwurf.nachname.trim(),
        aufgabe: wert(entwurf.aufgabe),
        telefon: wert(entwurf.telefon),
        mobiltelefon: wert(entwurf.mobiltelefon),
        email: wert(entwurf.email),
        notiz: wert(entwurf.notiz),
    };
}
