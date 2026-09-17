/**
 * Generischer Aenderungsverlauf (Rueckgaengig/Wiederholen-Kern).
 *
 * Bewusst rein und React-frei: kennt weder den Dokument-Editor noch DOM/React
 * -- nur ein generisches `Stand`-Objekt (die Felder, die ein Schritt aendern
 * kann) und ein generisches `Ziel` (wohin die UI nach einem Sprung scrollen
 * soll). Die Bindung an den Editor-Zustand liegt in
 * `document-editor/useDokumentVerlauf.ts`.
 *
 * Ein Schritt merkt sich je Feld NUR vorher/nachher (nicht den ganzen Stand)
 * -- so bleibt Rueckgaengig feldgenau: ein zwischenzeitlich von aussen
 * geaendertes Feld, das der Schritt selbst nie beruehrt hat, bleibt beim
 * Zuruecknehmen unangetastet.
 */

/** Vorher/Nachher-Paar eines einzelnen Feldes. */
export interface FeldAenderung<Wert> {
    vorher: Wert;
    nachher: Wert;
}

/** Je Feld von `Stand` optional ein Vorher/Nachher-Paar -- nur tatsaechlich geaenderte Felder sind gesetzt. */
export type Feldaenderungen<Stand> = { [K in keyof Stand]?: FeldAenderung<Stand[K]> };

export interface Schritt<Stand, Ziel> {
    id: number;
    bezeichnung: string;
    aenderungen: Feldaenderungen<Stand>;
    buendelSchluessel: string | null;
    ziel: Ziel | null;
    /** ms (Date.now()) der letzten Eingabe dieses Schritts -- Grundlage der Buendel-Pause. */
    zeitpunkt: number;
}

export interface Verlauf<Stand, Ziel> {
    /** Aeltester zuerst, neuester zuletzt. Maximal MAX_SCHRITTE Eintraege. */
    schritte: readonly Schritt<Stand, Ziel>[];
    /** Zurueckgenommene Schritte; der zuletzt zurueckgenommene steht zuletzt. */
    wiederholbar: readonly Schritt<Stand, Ziel>[];
    naechsteId: number;
    /** false direkt nach Rueckgaengig/Wiederholen/Leeren: der naechste Tipp-Schritt buendelt nicht. */
    buendelnErlaubt: boolean;
}

/** Wie viele Schritte der Verlauf maximal vorhaelt, bevor der aelteste rausfaellt. */
export const MAX_SCHRITTE = 20;
/** Maximaler Abstand zwischen zwei Eingaben desselben Buendel-Schluessels, um noch als EIN Schritt zu gelten. */
export const BUENDEL_PAUSE_MS = 2000;

export function leererVerlauf<Stand, Ziel>(): Verlauf<Stand, Ziel> {
    return {
        schritte: [],
        wiederholbar: [],
        naechsteId: 1,
        buendelnErlaubt: true,
    };
}

/**
 * Strukturelle Gleichheit fuer Vergleichs-/Bereinigungszwecke (z.B. "hat sich
 * dieses Feld WIRKLICH geaendert?"). `a === b` zuerst: das ist der
 * Performance-Kern -- unveraenderte Bloecke behalten in `prev.map(...)` ihre
 * Referenz, verglichen wird dann nur noch der tatsaechlich geaenderte Block.
 */
export function tiefGleich(a: unknown, b: unknown): boolean {
    if (a === b) return true;
    if (a == null || b == null) return false;
    if (typeof a !== 'object' || typeof b !== 'object') return false;

    if (Array.isArray(a) || Array.isArray(b)) {
        if (!Array.isArray(a) || !Array.isArray(b)) return false;
        if (a.length !== b.length) return false;
        return a.every((eintrag, i) => tiefGleich(eintrag, b[i]));
    }

    const aRecord = a as Record<string, unknown>;
    const bRecord = b as Record<string, unknown>;
    const aKeys = Object.keys(aRecord);
    const bKeys = Object.keys(bRecord);
    if (aKeys.length !== bKeys.length) return false;
    return aKeys.every(key =>
        Object.prototype.hasOwnProperty.call(bRecord, key) && tiefGleich(aRecord[key], bRecord[key]));
}

/**
 * Vergleicht `nachher` (nur die tatsaechlich berechneten Felder) gegen
 * `vorher` und liefert je Feld ein Vorher/Nachher-Paar -- aber NUR fuer
 * Felder, die sich strukturell wirklich unterscheiden (`tiefGleich`).
 */
export function feldAenderungen<Stand extends object>(vorher: Stand, nachher: Partial<Stand>): Feldaenderungen<Stand> {
    const ergebnis: Feldaenderungen<Stand> = {};
    for (const key of Object.keys(nachher) as (keyof Stand)[]) {
        const neuerWert = nachher[key] as Stand[keyof Stand];
        const alterWert = vorher[key];
        if (tiefGleich(alterWert, neuerWert)) continue;
        ergebnis[key] = { vorher: alterWert, nachher: neuerWert };
    }
    return ergebnis;
}

export function istLeer<Stand>(aenderungen: Feldaenderungen<Stand>): boolean {
    return Object.keys(aenderungen).length === 0;
}

/** Extrahiert die nachher-Werte -- genau das, was ein Aufrufer nach einer Aenderung schreiben muss. */
export function nachherWerte<Stand>(aenderungen: Feldaenderungen<Stand>): Partial<Stand> {
    const ergebnis: Partial<Stand> = {};
    for (const key of Object.keys(aenderungen) as (keyof Stand)[]) {
        const eintrag = aenderungen[key];
        if (eintrag) ergebnis[key] = eintrag.nachher;
    }
    return ergebnis;
}

/**
 * Fuehrt zwei Feldaenderungen-Objekte zusammen (Buendelung): je Feld gilt
 * `vorher` des ALTEN Eintrags (bzw. `vorher` des neuen, wenn das Feld im alten
 * Schritt noch gar nicht vorkam) und `nachher` des NEUEN. Felder, die sich per
 * Saldo nicht mehr unterscheiden, fallen komplett raus (tippen + zuruecklöschen).
 */
function mergeFelder<Stand>(alt: Feldaenderungen<Stand>, neu: Feldaenderungen<Stand>): Feldaenderungen<Stand> {
    const ergebnis: Feldaenderungen<Stand> = { ...alt };
    for (const key of Object.keys(neu) as (keyof Stand)[]) {
        const neuEintrag = neu[key];
        if (!neuEintrag) continue;
        const altEintrag = alt[key];
        const vorher = altEintrag ? altEintrag.vorher : neuEintrag.vorher;
        const nachher = neuEintrag.nachher;
        if (tiefGleich(vorher, nachher)) {
            delete ergebnis[key];
        } else {
            ergebnis[key] = { vorher, nachher };
        }
    }
    return ergebnis;
}

export interface SchrittEntwurf<Stand, Ziel> {
    bezeichnung: string;
    aenderungen: Feldaenderungen<Stand>;
    buendelSchluessel?: string | null;
    ziel?: Ziel | null;
}

/**
 * Nimmt einen Entwurf als neuen Schritt auf -- oder buendelt ihn mit dem
 * letzten Schritt, wenn `buendelSchluessel` uebereinstimmt, Buendeln gerade
 * erlaubt ist und die Pause seit der letzten Eingabe unter BUENDEL_PAUSE_MS
 * liegt. Ein leerer Entwurf (istLeer) veraendert den Verlauf nicht und liefert
 * dieselbe Referenz zurueck -- Aufrufer koennen das per `===` erkennen, um
 * einen unnoetigen Re-Render zu vermeiden.
 */
export function schrittAufnehmen<Stand, Ziel>(
    verlauf: Verlauf<Stand, Ziel>,
    entwurf: SchrittEntwurf<Stand, Ziel>,
    jetzt: number,
): Verlauf<Stand, Ziel> {
    if (istLeer(entwurf.aenderungen)) return verlauf;

    const buendelSchluessel = entwurf.buendelSchluessel ?? null;
    const letzter = verlauf.schritte.length > 0 ? verlauf.schritte[verlauf.schritte.length - 1] : null;
    const kannBuendeln = buendelSchluessel != null
        && verlauf.buendelnErlaubt
        && letzter != null
        && letzter.buendelSchluessel === buendelSchluessel
        && (jetzt - letzter.zeitpunkt) < BUENDEL_PAUSE_MS;

    if (kannBuendeln && letzter != null) {
        const ohneLetzten = verlauf.schritte.slice(0, -1);
        const zusammengefuehrt = mergeFelder(letzter.aenderungen, entwurf.aenderungen);

        if (istLeer(zusammengefuehrt)) {
            // Per Saldo keine Aenderung mehr (z.B. getippt und wieder geloescht):
            // der ganze Schritt faellt weg, nicht nur das betroffene Feld.
            return { ...verlauf, schritte: ohneLetzten, wiederholbar: [], buendelnErlaubt: true };
        }

        const aktualisiert: Schritt<Stand, Ziel> = {
            ...letzter,
            bezeichnung: entwurf.bezeichnung,
            aenderungen: zusammengefuehrt,
            ziel: entwurf.ziel !== undefined ? entwurf.ziel : letzter.ziel,
            zeitpunkt: jetzt,
        };
        return { ...verlauf, schritte: [...ohneLetzten, aktualisiert], wiederholbar: [], buendelnErlaubt: true };
    }

    const neuerSchritt: Schritt<Stand, Ziel> = {
        id: verlauf.naechsteId,
        bezeichnung: entwurf.bezeichnung,
        aenderungen: entwurf.aenderungen,
        buendelSchluessel,
        ziel: entwurf.ziel ?? null,
        zeitpunkt: jetzt,
    };
    const schritte = [...verlauf.schritte, neuerSchritt];
    return {
        schritte: schritte.length > MAX_SCHRITTE ? schritte.slice(schritte.length - MAX_SCHRITTE) : schritte,
        wiederholbar: [],
        naechsteId: verlauf.naechsteId + 1,
        buendelnErlaubt: true,
    };
}

export interface VerlaufsSprung<Stand, Ziel> {
    verlauf: Verlauf<Stand, Ziel>;
    /** Zusammengefuehrte Werte, die der Aufrufer setzen muss. */
    werte: Partial<Stand>;
    /** Die bewegten Schritte in Ausfuehrungsreihenfolge (Rueckgaengig: neuester zuerst). */
    schritte: readonly Schritt<Stand, Ziel>[];
}

/**
 * Nimmt die letzten `anzahl` Schritte zurueck (Default 1). Je betroffenem Feld
 * gewinnt der VORHER-Wert des AELTESTEN der zurueckgenommenen Schritte --
 * das ist der Zustand, bevor irgendeiner der zurueckgenommenen Schritte dieses
 * Feld beruehrt hat. Felder, die keiner der zurueckgenommenen Schritte
 * beruehrt hat, tauchen in `werte` gar nicht auf (feldgenau).
 */
export function rueckgaengig<Stand, Ziel>(
    verlauf: Verlauf<Stand, Ziel>,
    anzahl = 1,
): VerlaufsSprung<Stand, Ziel> | null {
    const n = Math.min(Math.max(anzahl, 1), verlauf.schritte.length);
    if (n <= 0) return null;

    const betroffene = verlauf.schritte.slice(verlauf.schritte.length - n); // aeltester...neuester
    const werte: Partial<Stand> = {};
    for (const schritt of betroffene) {
        for (const key of Object.keys(schritt.aenderungen) as (keyof Stand)[]) {
            if (Object.prototype.hasOwnProperty.call(werte, key)) continue; // aeltester Treffer gewinnt
            const eintrag = schritt.aenderungen[key];
            if (eintrag) werte[key] = eintrag.vorher;
        }
    }

    const neuerVerlauf: Verlauf<Stand, Ziel> = {
        schritte: verlauf.schritte.slice(0, verlauf.schritte.length - n),
        wiederholbar: [...verlauf.wiederholbar, ...[...betroffene].reverse()],
        naechsteId: verlauf.naechsteId,
        buendelnErlaubt: false,
    };

    return { verlauf: neuerVerlauf, werte, schritte: [...betroffene].reverse() };
}

/**
 * Stellt die letzten `anzahl` zurueckgenommenen Schritte wieder her (Default 1).
 * Symmetrisch zu `rueckgaengig`: je betroffenem Feld gewinnt der NACHHER-Wert
 * des NEUESTEN der wiederhergestellten Schritte.
 */
export function wiederholen<Stand, Ziel>(
    verlauf: Verlauf<Stand, Ziel>,
    anzahl = 1,
): VerlaufsSprung<Stand, Ziel> | null {
    const n = Math.min(Math.max(anzahl, 1), verlauf.wiederholbar.length);
    if (n <= 0) return null;

    // wiederholbar ist [..., neuester-zuerst-zurueckgenommen? nein -- "der
    // zuletzt zurueckgenommene steht zuletzt"], also stehen die letzten n
    // Eintraege dort in der Reihenfolge, in der sie zurueckgenommen wurden
    // (neuester zuerst). Fuer die Ausfuehrungsreihenfolge (chronologisch
    // vorwaerts) wird das umgedreht.
    const zuletztZurueckgenommen = verlauf.wiederholbar.slice(verlauf.wiederholbar.length - n);
    const betroffene = [...zuletztZurueckgenommen].reverse(); // aeltester...neuester

    const werte: Partial<Stand> = {};
    for (const schritt of betroffene) {
        for (const key of Object.keys(schritt.aenderungen) as (keyof Stand)[]) {
            const eintrag = schritt.aenderungen[key];
            if (eintrag) werte[key] = eintrag.nachher; // neuester Treffer gewinnt (ueberschreibt bewusst)
        }
    }

    const neuerVerlauf: Verlauf<Stand, Ziel> = {
        schritte: [...verlauf.schritte, ...betroffene],
        wiederholbar: verlauf.wiederholbar.slice(0, verlauf.wiederholbar.length - n),
        naechsteId: verlauf.naechsteId,
        buendelnErlaubt: false,
    };

    return { verlauf: neuerVerlauf, werte, schritte: betroffene };
}

/** Leert schritte UND wiederholbar. Bei einem bereits leeren Verlauf dieselbe Referenz (kein Re-Render-Anlass). */
export function verlaufGeleert<Stand, Ziel>(verlauf: Verlauf<Stand, Ziel>): Verlauf<Stand, Ziel> {
    if (verlauf.schritte.length === 0 && verlauf.wiederholbar.length === 0) return verlauf;
    return { schritte: [], wiederholbar: [], naechsteId: verlauf.naechsteId, buendelnErlaubt: true };
}
