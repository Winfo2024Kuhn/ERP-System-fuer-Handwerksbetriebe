/**
 * Reine Regeln für Links im Mail-Textfeld: Adresse prüfen und vervollständigen,
 * Anzeige kürzen, eingefügte Web-Adressen erkennen.
 *
 * Erlaubt sind nur `http:`, `https:` und `mailto:`. Alles andere
 * (`javascript:`, `data:`, `file:` …) wird abgelehnt – so landet nie ein
 * ausführbarer Link in einer verschickten Mail.
 */

export const LINK_ADRESSE_UNGUELTIG = 'Bitte eine Web-Adresse (https://…) oder E-Mail-Adresse eintragen.';

const ERLAUBTE_SCHEMATA = new Set(['http:', 'https:', 'mailto:']);
/** `name@example.com` ohne Schema. Keine verschachtelten Wiederholungen (ReDoS). */
const EMAIL_OHNE_SCHEMA = /^[^\s@/:]+@[^\s@/:]+\.[^\s@/:]+$/;
/** Schema am Anfang, z. B. `https:` oder `javascript:` – aber nicht `localhost:8080`. */
const SCHEMA_AM_ANFANG = /^([a-z][a-z0-9+.-]*):(?!\d)/i;
const LEERRAUM = /\s/;

export type LinkPruefung = { ok: true; href: string } | { ok: false; meldung: string };

function ungueltig(): LinkPruefung {
    return { ok: false, meldung: LINK_ADRESSE_UNGUELTIG };
}

function vervollstaendige(adresse: string): string {
    if (EMAIL_OHNE_SCHEMA.test(adresse)) return `mailto:${adresse}`;
    const schema = SCHEMA_AM_ANFANG.exec(adresse)?.[1];
    // Ein Punkt im „Schema“ heißt: das ist ein Rechnername mit Port, kein Schema.
    if (schema && !schema.includes('.')) return adresse;
    return `https://${adresse}`;
}

function pruefeVollstaendig(href: string): boolean {
    let url: URL;
    try {
        url = new URL(href);
    } catch {
        return false;
    }
    if (!ERLAUBTE_SCHEMATA.has(url.protocol)) return false;
    if (url.protocol === 'mailto:') return url.pathname.includes('@');
    return url.hostname === 'localhost' || url.hostname.includes('.');
}

/**
 * Prüft die eingetragene Link-Adresse und vervollständigt sie:
 * ohne Schema → `https://`, `name@example.com` → `mailto:`.
 */
export function normalisiereLinkAdresse(eingabe: string): LinkPruefung {
    const adresse = eingabe.trim();
    if (!adresse || LEERRAUM.test(adresse) || adresse.startsWith('/')) return ungueltig();
    const href = vervollstaendige(adresse);
    return pruefeVollstaendig(href) ? { ok: true, href } : ungueltig();
}

/** Darf diese Adresse geöffnet bzw. als Link stehen bleiben? */
export function istErlaubterLink(href: string | null | undefined): boolean {
    if (!href) return false;
    try {
        return ERLAUBTE_SCHEMATA.has(new URL(href).protocol);
    } catch {
        return false;
    }
}

/** Adresse so, wie ein Mensch sie liest: ohne `https://` bzw. `mailto:`. */
export function anzeigeAdresse(href: string): string {
    return href.replace(/^(https?:\/\/|mailto:)/i, '');
}

/** Gekürzte Anzeige für die kleine Leiste am Link. */
export function kurzeAdresse(href: string, maxZeichen = 40): string {
    const anzeige = anzeigeAdresse(href);
    return anzeige.length > maxZeichen ? `${anzeige.slice(0, maxZeichen - 1)}…` : anzeige;
}

/** Angezeigter Text des Links; ohne eigenen Text steht die Adresse da (E-Mail ohne `mailto:`). */
export function linkText(text: string, href: string): string {
    return text.trim() || href.replace(/^mailto:/i, '');
}

/**
 * Eingefügter Text, der genau eine Web-Adresse ist (`http(s)://…`). Dann macht
 * das Feld den markierten Text zum Link bzw. setzt die Adresse als Link ein.
 * Sonst `null` – normales Einfügen.
 */
export function webAdresseAusEinfuegen(text: string): string | null {
    const adresse = text.trim();
    if (!/^https?:\/\//i.test(adresse)) return null;
    const pruefung = normalisiereLinkAdresse(adresse);
    return pruefung.ok ? pruefung.href : null;
}
