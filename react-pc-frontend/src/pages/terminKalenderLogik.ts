/**
 * Reine Rechenlogik des Terminkalenders (ohne React), damit sie sich einzeln testen lässt:
 * welcher Zeitraum gerade sichtbar ist, welche Stunden die Tagesansicht zeigt und wie
 * sich überlappende Termine nebeneinander aufteilen.
 */

export type KalenderAnsicht = 'monat' | 'woche' | 'tag';

export interface Zeitraum {
    /** Erster sichtbarer Tag, `YYYY-MM-DD`. */
    von: string;
    /** Letzter sichtbarer Tag (einschließlich), `YYYY-MM-DD`. */
    bis: string;
}

/** Lokales Datum als `YYYY-MM-DD` – ohne Verschiebung durch UTC. */
export function formatLocalDate(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

/** `YYYY-MM-DD` als lokales Datum (Mitternacht), ohne UTC-Verschiebung. */
export function parseLocalDate(text: string): Date {
    const [y, m, d] = text.split('-').map(Number);
    return new Date(y, m - 1, d);
}

export function addTage(date: Date, tage: number): Date {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate() + tage);
}

/** Montag der Woche, in der `date` liegt. */
export function montagDerWoche(date: Date): Date {
    const wochentag = date.getDay(); // 0 = Sonntag
    return addTage(date, wochentag === 0 ? -6 : 1 - wochentag);
}

/**
 * Sichtbarer Zeitraum der Ansicht rund um den Stichtag:
 * - Monat: das ganze Monatsraster, also ab dem Montag vor dem 1. bis zum Sonntag nach dem Letzten,
 * - Woche: Montag bis Sonntag,
 * - Tag: nur dieser Tag.
 */
export function sichtbarerZeitraum(ansicht: KalenderAnsicht, stichtag: Date): Zeitraum {
    if (ansicht === 'tag') {
        const tag = formatLocalDate(stichtag);
        return { von: tag, bis: tag };
    }
    if (ansicht === 'woche') {
        const montag = montagDerWoche(stichtag);
        return { von: formatLocalDate(montag), bis: formatLocalDate(addTage(montag, 6)) };
    }
    const erster = new Date(stichtag.getFullYear(), stichtag.getMonth(), 1);
    const letzter = new Date(stichtag.getFullYear(), stichtag.getMonth() + 1, 0);
    const sonntagNachLetztem = addTage(montagDerWoche(letzter), 6);
    return { von: formatLocalDate(montagDerWoche(erster)), bis: formatLocalDate(sonntagNachLetztem) };
}

/** Alle Tage des Zeitraums als `YYYY-MM-DD`, aufsteigend. */
export function tageImZeitraum({ von, bis }: Zeitraum): string[] {
    const tage: string[] = [];
    const ende = parseLocalDate(bis);
    for (let tag = parseLocalDate(von); tag <= ende; tag = addTage(tag, 1)) {
        tage.push(formatLocalDate(tag));
    }
    return tage;
}

/** Jahre, die der Zeitraum berührt (für die Feiertage, die je Jahr geladen werden). */
export function jahreImZeitraum({ von, bis }: Zeitraum): number[] {
    const erstes = Number(von.slice(0, 4));
    const letztes = Number(bis.slice(0, 4));
    const jahre: number[] = [];
    for (let jahr = erstes; jahr <= letztes; jahr++) jahre.push(jahr);
    return jahre;
}

/** Monat oder Jahr wechseln, ohne am Monatsende über den Monat hinauszulaufen (31. → 30./28.). */
export function wechsleMonat(stichtag: Date, jahr: number, monatIndex: number): Date {
    const letzterTag = new Date(jahr, monatIndex + 1, 0).getDate();
    return new Date(jahr, monatIndex, Math.min(stichtag.getDate(), letzterTag));
}

/** `HH:mm` oder `HH:mm:ss` in Minuten seit Mitternacht, sonst `null`. */
export function zeitInMinuten(zeit: string | null | undefined): number | null {
    if (!zeit) return null;
    const treffer = /^(\d{2}):(\d{2})/.exec(zeit);
    if (!treffer) return null;
    return Number(treffer[1]) * 60 + Number(treffer[2]);
}

/** Ende liegt vor dem Beginn (gleiche Uhrzeit ist erlaubt). Unlesbare Zeiten zählen nicht. */
export function endeVorBeginn(start: string, ende: string): boolean {
    const s = zeitInMinuten(start);
    const e = zeitInMinuten(ende);
    return s !== null && e !== null && e < s;
}

/** Mindesthöhe eines Termins in der Tagesansicht, damit kurze Termine lesbar bleiben. */
export const MINDESTDAUER_MINUTEN = 30;

export interface ZeitTermin {
    id: number;
    startZeit: string | null;
    endeZeit: string | null;
}

/** Beginn und (angezeigtes) Ende eines Termins in Minuten; ohne Ende eine Stunde. */
export function anzeigeSpanne(termin: ZeitTermin): { start: number; ende: number } | null {
    const start = zeitInMinuten(termin.startZeit);
    if (start === null) return null;
    const endeRoh = zeitInMinuten(termin.endeZeit) ?? start + 60;
    const ende = Math.min(24 * 60, Math.max(endeRoh, start + MINDESTDAUER_MINUTEN));
    return { start, ende };
}

/**
 * Stundenbereich der Tagesansicht: normal 07:00–19:00, bei früheren oder späteren
 * Terminen so weit erweitert, dass alle vollständig sichtbar sind.
 */
export function stundenBereich(termine: ZeitTermin[], standardVon = 7, standardBis = 19): { von: number; bis: number } {
    let von = standardVon;
    let bis = standardBis;
    for (const termin of termine) {
        const spanne = anzeigeSpanne(termin);
        if (!spanne) continue;
        von = Math.min(von, Math.floor(spanne.start / 60));
        bis = Math.max(bis, Math.ceil(spanne.ende / 60));
    }
    return { von: Math.max(0, von), bis: Math.min(24, bis) };
}

export interface Spaltenplatz {
    /** Spalte des Termins innerhalb seiner Überlappungsgruppe, ab 0. */
    spalte: number;
    /** Anzahl Spalten der Gruppe – so breit wird jeder Termin der Gruppe geteilt. */
    spalten: number;
}

/**
 * Verteilt überlappende Termine auf Spalten nebeneinander. Termine, die sich (auch über
 * Dritte) überschneiden, bilden eine Gruppe und teilen sich deren Breite; jeder Termin
 * nimmt die erste freie Spalte.
 */
export function verteileUeberlappungen(termine: ZeitTermin[]): Map<number, Spaltenplatz> {
    const plaetze = new Map<number, Spaltenplatz>();
    const sortiert = termine
        .map(t => ({ id: t.id, spanne: anzeigeSpanne(t) }))
        .filter((t): t is { id: number; spanne: { start: number; ende: number } } => t.spanne !== null)
        .sort((a, b) => a.spanne.start - b.spanne.start || b.spanne.ende - a.spanne.ende);

    let gruppe: { id: number; spalte: number }[] = [];
    let spaltenEnden: number[] = [];
    let gruppenEnde = -1;

    const gruppeAbschliessen = () => {
        for (const eintrag of gruppe) plaetze.set(eintrag.id, { spalte: eintrag.spalte, spalten: spaltenEnden.length });
        gruppe = [];
        spaltenEnden = [];
    };

    for (const { id, spanne } of sortiert) {
        if (spanne.start >= gruppenEnde) gruppeAbschliessen();
        let spalte = spaltenEnden.findIndex(ende => ende <= spanne.start);
        if (spalte === -1) {
            spalte = spaltenEnden.length;
            spaltenEnden.push(spanne.ende);
        } else {
            spaltenEnden[spalte] = spanne.ende;
        }
        gruppe.push({ id, spalte });
        gruppenEnde = Math.max(gruppenEnde, spanne.ende);
    }
    gruppeAbschliessen();
    return plaetze;
}

/** Termine eines Tages: ganztägige zuerst, dann nach Beginn, bei Gleichstand nach Titel. */
export function sortiereTermine<T extends { ganztaegig: boolean; startZeit: string | null; titel: string }>(termine: T[]): T[] {
    return [...termine].sort((a, b) => {
        const aGanz = a.ganztaegig || !a.startZeit;
        const bGanz = b.ganztaegig || !b.startZeit;
        if (aGanz !== bGanz) return aGanz ? -1 : 1;
        const zeit = (zeitInMinuten(a.startZeit) ?? 0) - (zeitInMinuten(b.startZeit) ?? 0);
        return zeit || a.titel.localeCompare(b.titel, 'de');
    });
}
