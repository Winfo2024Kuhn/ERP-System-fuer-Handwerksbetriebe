/**
 * Typen und reine Hilfsfunktionen rund um E-Mail-Postfächer.
 *
 * <p>Ein Postfach ist ein echtes Mail-Konto des Betriebs (info@, rechnungen@,
 * max@ …) mit eigenem Zugang. Die Feldnamen folgen exakt dem API-Vertrag aus
 * {@code docs/superpowers/specs/2026-10-10-email-postfaecher-design.md}
 * (Abschnitt 1.5) – bitte dort zuerst ändern, nie nur hier.</p>
 *
 * <p>Bewusst ohne JSX: Komponenten und normale Funktionen in einer Datei
 * brechen das Hot-Reload (react-refresh).</p>
 */

/** Kurzform, wie sie an jeder Mail hängt (`postfaecher`, `antwortPostfach`). */
export interface PostfachKurz {
    id: number;
    emailAdresse: string;
    anzeigename: string | null;
}

/** Eintrag aus `GET /api/emails/absender-postfaecher`. */
export interface AbsenderPostfach extends PostfachKurz {
    /** Das dem angemeldeten Benutzer zugeordnete Postfach. */
    eigenes: boolean;
    hauptpostfach: boolean;
}

/** Vollständiges Postfach aus `GET /api/postfaecher` (nur Admin). */
export interface PostfachDto extends PostfachKurz {
    aktiv: boolean;
    sortierung: number;
    hauptpostfach: boolean;
    fuerGeschaeftsdokumente: boolean;
    benutzername: string | null;
    passwortGesetzt: boolean;
    smtpHost: string | null;
    smtpPort: number | null;
    imapHost: string | null;
    imapPort: number | null;
    /** Zugang vollständig und Postfach aktiv – nur dann wird abgerufen. */
    abrufAktiv: boolean;
    letzterAbrufAm: string | null;
    /** Klartext für Handwerker; `null` = letzter Abruf ok. */
    letzterAbrufFehler: string | null;
    zugewieseneBenutzer: { id: number; displayName: string }[];
}

/** Body für `POST /api/postfaecher` und `PUT /api/postfaecher/{id}`. */
export interface PostfachSpeichernRequest {
    emailAdresse: string;
    anzeigename: string | null;
    aktiv: boolean;
    sortierung: number;
    hauptpostfach: boolean;
    fuerGeschaeftsdokumente: boolean;
    benutzername: string | null;
    /** Leer bzw. `null` = gespeichertes Passwort bleibt unverändert. */
    passwort: string | null;
    smtpHost: string | null;
    smtpPort: number | null;
    imapHost: string | null;
    imapPort: number | null;
}

/** Body für `POST /api/postfaecher/test`. */
export interface PostfachTestRequest {
    id: number | null;
    benutzername: string | null;
    /** Leer = gespeichertes Passwort des Postfachs `id`. */
    passwort: string | null;
    smtpHost: string | null;
    smtpPort: number | null;
    imapHost: string | null;
    imapPort: number | null;
    testEmpfaenger: string | null;
}

export interface PostfachTestErgebnis {
    versandOk: boolean;
    abrufOk: boolean;
    message: string;
}

/** Lokaler Teil der Adresse mit @, z. B. „info@“ – für das kleine Postfach-Schild. */
export function lokalerTeil(adresse: string): string {
    const at = adresse.indexOf('@');
    return at > 0 ? adresse.slice(0, at + 1) : adresse;
}

/** „Anzeigename <adresse>“ bzw. nur die Adresse, wenn kein Name hinterlegt ist. */
export function formatPostfach(postfach: PostfachKurz): string {
    const name = postfach.anzeigename?.trim();
    return name ? `${name} <${postfach.emailAdresse}>` : postfach.emailAdresse;
}

/**
 * Vorbelegung für „Senden von“: eigenes Postfach, sonst Hauptpostfach,
 * sonst das erste der Liste. `null` nur bei leerer Liste.
 */
export function waehleStandardAbsender(postfaecher: AbsenderPostfach[]): AbsenderPostfach | null {
    return postfaecher.find(p => p.eigenes)
        ?? postfaecher.find(p => p.hauptpostfach)
        ?? postfaecher[0]
        ?? null;
}

/** Liest die Antwort von `absender-postfaecher` robust ein (unbekannte Einträge fliegen raus). */
export function parseAbsenderPostfaecher(data: unknown): AbsenderPostfach[] {
    if (!Array.isArray(data)) return [];
    return data.filter((eintrag): eintrag is AbsenderPostfach =>
        !!eintrag && typeof eintrag === 'object'
        && typeof (eintrag as AbsenderPostfach).id === 'number'
        && typeof (eintrag as AbsenderPostfach).emailAdresse === 'string');
}

/**
 * Relative Zeitangabe für den Abruf-Status, z. B. „gerade eben“, „vor 2 Min.“,
 * „vor 3 Std.“, „vor 2 Tagen“.
 */
export function formatVorZeit(iso: string, jetzt: Date = new Date()): string {
    const zeitpunkt = new Date(iso).getTime();
    if (Number.isNaN(zeitpunkt)) return '';
    const minuten = Math.floor((jetzt.getTime() - zeitpunkt) / 60_000);
    if (minuten < 1) return 'gerade eben';
    if (minuten < 60) return `vor ${minuten} Min.`;
    const stunden = Math.floor(minuten / 60);
    if (stunden < 24) return `vor ${stunden} Std.`;
    const tage = Math.floor(stunden / 24);
    return tage === 1 ? 'vor 1 Tag' : `vor ${tage} Tagen`;
}

export type AbrufStatusArt = 'ok' | 'fehler' | 'aus' | 'unvollstaendig' | 'wartet';

/** Was neben einem Postfach über den Abruf steht. */
export function beschreibeAbruf(postfach: PostfachDto, jetzt: Date = new Date()): { art: AbrufStatusArt; text: string } {
    if (!postfach.aktiv) return { art: 'aus', text: 'Ausgeschaltet' };
    if (!postfach.abrufAktiv) {
        return { art: 'unvollstaendig', text: 'Kein Abruf – Zugang unvollständig, nur Absender-Adresse' };
    }
    if (postfach.letzterAbrufFehler) return { art: 'fehler', text: postfach.letzterAbrufFehler };
    if (!postfach.letzterAbrufAm) return { art: 'wartet', text: 'Noch nicht abgerufen' };
    const vor = formatVorZeit(postfach.letzterAbrufAm, jetzt);
    return { art: 'ok', text: vor ? `Abruf ok · ${vor}` : 'Abruf ok' };
}
