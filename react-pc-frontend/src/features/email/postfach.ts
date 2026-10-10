/**
 * Typen und reine Hilfsfunktionen rund um E-Mail-Postfächer.
 *
 * <p>Ein Postfach ist ein echtes Mail-Konto des Betriebs (info@, rechnungen@,
 * max@ …) mit eigenem Zugang. Die Feldnamen folgen exakt dem API-Vertrag aus
 * {@code docs/superpowers/specs/2026-10-10-email-postfaecher-design.md}
 * (Abschnitte 1.5 und 2.3) – bitte dort zuerst ändern, nie nur hier.</p>
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
    /**
     * Postfach läuft aus: Mails kommen weiter an, Antworten gehen über das Hauptpostfach,
     * für neue Mails nicht mehr wählbar. Fehlt bei älteren Antworten → `false`.
     */
    laeuftAus: boolean;
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
    /** Etappe 2: `true` = jeder im Betrieb sieht das Postfach. Beim Hauptpostfach immer `true`. */
    sichtbarFuerAlle: boolean;
    /** Freigegebene Abteilungen (wirken nur bei `sichtbarFuerAlle = false`). */
    sichtbarFuerAbteilungen: SichtbarAbteilung[];
    /** Einzeln freigegebene Benutzer (wirken nur bei `sichtbarFuerAlle = false`). */
    sichtbarFuerBenutzer: SichtbarBenutzer[];
}

/** Abteilung, die ein Postfach sehen darf. */
export interface SichtbarAbteilung {
    id: number;
    name: string;
}

/** Benutzer, der ein Postfach sehen darf. */
export interface SichtbarBenutzer {
    id: number;
    displayName: string;
}

/** Body für `POST /api/postfaecher` und `PUT /api/postfaecher/{id}`. */
export interface PostfachSpeichernRequest {
    emailAdresse: string;
    anzeigename: string | null;
    aktiv: boolean;
    sortierung: number;
    hauptpostfach: boolean;
    fuerGeschaeftsdokumente: boolean;
    /** `null` = unverändert. */
    laeuftAus: boolean | null;
    benutzername: string | null;
    /** Leer bzw. `null` = gespeichertes Passwort bleibt unverändert. */
    passwort: string | null;
    smtpHost: string | null;
    smtpPort: number | null;
    imapHost: string | null;
    imapPort: number | null;
    /** `null` = bei neuen Postfächern `true`, beim Ändern unverändert. */
    sichtbarFuerAlle: boolean | null;
    /** `null` = unverändert, `[]` = leeren. */
    abteilungIds: number[] | null;
    /** `null` = unverändert, `[]` = leeren. */
    benutzerIds: number[] | null;
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

/** Was auf der Berechtigungen-Seite für die Sichtbarkeit eines Postfachs eingestellt wird. */
export interface SichtbarkeitsAuswahl {
    sichtbarFuerAlle: boolean;
    abteilungIds: number[];
    benutzerIds: number[];
}

/** Body für `PUT /api/postfaecher/{id}/sichtbarkeit`. */
export type PostfachSichtbarkeitRequest = SichtbarkeitsAuswahl;

/** Gespeicherte Sichtbarkeit eines Postfachs als Ausgangswert für die Häkchen. */
export function sichtbarkeitAusPostfach(postfach: PostfachDto): SichtbarkeitsAuswahl {
    return {
        sichtbarFuerAlle: postfach.sichtbarFuerAlle !== false,
        abteilungIds: (postfach.sichtbarFuerAbteilungen ?? []).map(a => a.id),
        benutzerIds: (postfach.sichtbarFuerBenutzer ?? []).map(b => b.id),
    };
}

const gleicheIds = (a: number[], b: number[]) =>
    a.length === b.length && a.every(id => b.includes(id));

/** Weicht die Auswahl von der gespeicherten Sichtbarkeit ab? Reihenfolge der Häkchen ist egal. */
export function sichtbarkeitGeaendert(postfach: PostfachDto, wert: SichtbarkeitsAuswahl): boolean {
    const gespeichert = sichtbarkeitAusPostfach(postfach);
    return gespeichert.sichtbarFuerAlle !== wert.sichtbarFuerAlle
        || !gleicheIds(gespeichert.abteilungIds, wert.abteilungIds)
        || !gleicheIds(gespeichert.benutzerIds, wert.benutzerIds);
}

/**
 * Liest ein einzelnes `PostfachDto` robust ein; ohne Id oder Adresse `null`.
 * Fehlt `laeuftAus` (ältere Antwort), gilt `false`.
 */
export function parsePostfach(data: unknown): PostfachDto | null {
    if (!data || typeof data !== 'object'
        || typeof (data as PostfachDto).id !== 'number'
        || typeof (data as PostfachDto).emailAdresse !== 'string') {
        return null;
    }
    return { ...(data as PostfachDto), laeuftAus: (data as PostfachDto).laeuftAus === true };
}

/** Liest `GET /api/postfaecher` robust ein – Einträge ohne Id oder Adresse fliegen raus. */
export function parsePostfaecher(data: unknown): PostfachDto[] {
    if (!Array.isArray(data)) return [];
    return data.map(parsePostfach).filter((p): p is PostfachDto => p !== null);
}

/** Eintrag der Häkchen-Liste „Benutzer“ auf der Berechtigungen-Seite. */
export interface SichtbarkeitsBenutzer extends SichtbarBenutzer {
    aktiv: boolean;
}

/**
 * Liest `GET /api/abteilungen/berechtigungen` (`{abteilungId, abteilungName, …}[]`)
 * als Abteilungsliste ein, alphabetisch sortiert. Unvollständige Einträge fliegen raus.
 */
export function parseSichtbarkeitsAbteilungen(data: unknown): SichtbarAbteilung[] {
    if (!Array.isArray(data)) return [];
    return data
        .filter((e): e is { abteilungId: number; abteilungName: string } =>
            !!e && typeof e === 'object'
            && typeof (e as { abteilungId?: unknown }).abteilungId === 'number'
            && typeof (e as { abteilungName?: unknown }).abteilungName === 'string')
        .map(e => ({ id: e.abteilungId, name: e.abteilungName }))
        .sort((a, b) => a.name.localeCompare(b.name, 'de'));
}

/**
 * Liest `GET /api/frontend-users` als Benutzerliste ein, alphabetisch sortiert.
 * `active` fehlt bei älteren Antworten – dann gilt der Benutzer als aktiv.
 */
export function parseSichtbarkeitsBenutzer(data: unknown): SichtbarkeitsBenutzer[] {
    if (!Array.isArray(data)) return [];
    return data
        .filter((e): e is { id: number; displayName: string; active?: boolean } =>
            !!e && typeof e === 'object'
            && typeof (e as { id?: unknown }).id === 'number'
            && typeof (e as { displayName?: unknown }).displayName === 'string')
        .map(e => ({ id: e.id, displayName: e.displayName, aktiv: e.active !== false }))
        .sort((a, b) => a.displayName.localeCompare(b.displayName, 'de'));
}

/** Groß/Klein egal, Leerzeichen am Rand egal; leere Suche liefert alles. */
export function passtZurSuche(text: string, suche: string): boolean {
    const begriff = suche.trim().toLocaleLowerCase('de');
    return !begriff || text.toLocaleLowerCase('de').includes(begriff);
}

export interface SichtbarkeitsAnzeige {
    /** Kurzer Text für die Liste, z. B. „alle“ oder „Büro, Max Mustermann“. */
    text: string;
    /** Wie viele Namen nicht mehr in den Text gepasst haben („+3“). */
    weitere: number;
    /** Vollständige Aufzählung für den Tooltip. */
    vollstaendig: string;
}

/**
 * Hauptpostfach und das Postfach für Rechnungen & Mahnungen sieht immer jeder im
 * Betrieb (Nutzervorgabe) – dort gibt es keine Auswahl „Nur bestimmte“.
 */
export function immerFuerAlleSichtbar(postfach: Pick<PostfachDto, 'hauptpostfach' | 'fuerGeschaeftsdokumente'>): boolean {
    return postfach.hauptpostfach || postfach.fuerGeschaeftsdokumente;
}

/**
 * Was in der Postfach-Liste hinter „Sichtbar:“ steht. Abteilungen zuerst, dann
 * Benutzer; ab `maxNamen` wird gekürzt und der Rest als Zahl angegeben.
 */
export function beschreibeSichtbarkeit(postfach: PostfachDto, maxNamen = 3): SichtbarkeitsAnzeige {
    if (immerFuerAlleSichtbar(postfach) || postfach.sichtbarFuerAlle !== false) {
        return { text: 'alle', weitere: 0, vollstaendig: 'Jeder im Betrieb sieht dieses Postfach.' };
    }
    const namen = [
        ...(postfach.sichtbarFuerAbteilungen ?? []).map(a => a.name),
        ...(postfach.sichtbarFuerBenutzer ?? []).map(b => b.displayName),
    ];
    if (namen.length === 0) {
        return {
            text: 'nur Inhaber und Admins', weitere: 0,
            vollstaendig: 'Keine Abteilung und kein Benutzer freigegeben – nur der Inhaber und Admins sehen es.',
        };
    }
    const sichtbar = namen.slice(0, maxNamen);
    return {
        text: sichtbar.join(', '),
        weitere: namen.length - sichtbar.length,
        vollstaendig: namen.join(', '),
    };
}
