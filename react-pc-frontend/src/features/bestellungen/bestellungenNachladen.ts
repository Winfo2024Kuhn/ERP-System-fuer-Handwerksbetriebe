/**
 * Reine Hilfsfunktionen für das stückweise Laden der Bestellübersicht:
 * Der Server liefert je Reiter nur die neuesten {@link SEITEN_GROESSE} Ketten
 * und zählt dazu, wie viele es insgesamt gibt („Mehr laden“ holt die nächsten).
 * Angebote und laufende Bestellungen kürzt er je Alter getrennt: bis zu
 * `anzahl` aktuelle und bis zu `anzahl` alte (über 60 Tage ohne Bewegung).
 */
import { istAlt, type Kette } from './bestellungenListe';

/** So viele Ketten lädt die Seite je Reiter auf einmal. */
export const SEITEN_GROESSE = 25;
/** Längere Suchbegriffe lehnt der Server ab. */
export const SUCHE_MAX_LAENGE = 200;
/** Kürzere Suchbegriffe behandelt der Server wie keine Suche – die Seite fragt dann gar nicht erst. */
export const SUCHE_MIN_LAENGE = 2;
/** So lange wartet die Suche nach dem letzten Tastendruck, bevor sie den Server fragt. */
export const SUCHE_VERZOEGERUNG_MS = 400;

/** Anzahl je Gruppe nach der Suche, vor dem Kürzen. */
export interface GruppenGesamt {
    offeneAnfragen: number;
    /** Angebote, die sich in den letzten 60 Tagen bewegt haben – Zähler am Reiter „Angebote“. */
    offeneAnfragenAktuell: number;
    laufendeBestellungen: number;
    /** Laufende Bestellungen, die sich in den letzten 60 Tagen bewegt haben. */
    laufendeBestellungenAktuell: number;
    abgeschlossen: number;
    zugeordnet: number;
    ausgeblendet: number;
}

export type GruppenSchluessel = Exclude<keyof GruppenGesamt, 'offeneAnfragenAktuell' | 'laufendeBestellungenAktuell'>;

/** Gruppen, die der Server je Alter getrennt kürzt, und ihr Zähler für die aktuellen. */
const AKTUELL_ZAEHLER = {
    offeneAnfragen: 'offeneAnfragenAktuell',
    laufendeBestellungen: 'laufendeBestellungenAktuell',
} as const satisfies Partial<Record<GruppenSchluessel, keyof GruppenGesamt>>;

export const GRUPPEN: GruppenSchluessel[] = ['offeneAnfragen', 'laufendeBestellungen', 'abgeschlossen', 'zugeordnet', 'ausgeblendet'];

export interface UebersichtListen<T> {
    offeneAnfragen: T[];
    laufendeBestellungen: T[];
    abgeschlossen: T[];
    zugeordnet: T[];
    ausgeblendet: T[];
    gesamt?: GruppenGesamt | null;
}

export interface LadeParameter {
    anzahl: number;
    suche: string;
}

/** Suchbegriff, wie er beim Server ankommt: getrimmt, unter {@link SUCHE_MIN_LAENGE} Zeichen leer. */
export function wirksameSuche(text: string): string {
    const begriff = text.trim();
    return begriff.length >= SUCHE_MIN_LAENGE ? begriff : '';
}

/** Adresse der Übersicht: `anzahl` immer, `suche` nur, wenn wirklich gesucht wird. */
export function uebersichtUrl({ anzahl, suche }: LadeParameter): string {
    const params = new URLSearchParams({ anzahl: String(anzahl) });
    const begriff = wirksameSuche(suche);
    if (begriff) params.set('suche', begriff);
    return `/api/bestellungen-uebersicht?${params.toString()}`;
}

/** Fehlende oder kaputte Listen werden zu leeren Listen, damit die Seite nie abstürzt. */
export function normalisiereUebersicht<T>(json: unknown): UebersichtListen<T> {
    const roh = (json && typeof json === 'object' ? json : {}) as Record<string, unknown>;
    const liste = (wert: unknown): T[] => (Array.isArray(wert) ? (wert as T[]) : []);
    const gesamt = roh.gesamt && typeof roh.gesamt === 'object' ? (roh.gesamt as GruppenGesamt) : null;
    return {
        offeneAnfragen: liste(roh.offeneAnfragen),
        laufendeBestellungen: liste(roh.laufendeBestellungen),
        abgeschlossen: liste(roh.abgeschlossen),
        zugeordnet: liste(roh.zugeordnet),
        ausgeblendet: liste(roh.ausgeblendet),
        gesamt,
    };
}

function zahlOder(wert: unknown, ersatz: number): number {
    return typeof wert === 'number' && Number.isFinite(wert) && wert >= 0 ? wert : ersatz;
}

/**
 * Anzahl je Gruppe laut Server. Schickt ein älterer Server kein `gesamt`,
 * gilt das Geladene als vollständig – dann zählt die Seite selbst.
 */
export function gesamtJeGruppe<T extends Kette>(daten: UebersichtListen<T>, heute: Date): GruppenGesamt {
    const g = daten.gesamt ?? null;
    const aktuellGeladen = (liste: T[]) => liste.filter(k => !istAlt(k, heute)).length;
    return {
        offeneAnfragen: zahlOder(g?.offeneAnfragen, daten.offeneAnfragen.length),
        offeneAnfragenAktuell: zahlOder(g?.offeneAnfragenAktuell, aktuellGeladen(daten.offeneAnfragen)),
        laufendeBestellungen: zahlOder(g?.laufendeBestellungen, daten.laufendeBestellungen.length),
        laufendeBestellungenAktuell: zahlOder(g?.laufendeBestellungenAktuell, aktuellGeladen(daten.laufendeBestellungen)),
        abgeschlossen: zahlOder(g?.abgeschlossen, daten.abgeschlossen.length),
        zugeordnet: zahlOder(g?.zugeordnet, daten.zugeordnet.length),
        ausgeblendet: zahlOder(g?.ausgeblendet, daten.ausgeblendet.length),
    };
}

/**
 * Wie viel ist da und wie viel geladen – je getrennt gekürztem Teil.
 * Angebote und laufende Bestellungen bestehen aus zwei Teilen (aktuell, alt),
 * alle anderen Gruppen aus einem.
 */
export function ladeStand<T extends Kette>(
    daten: UebersichtListen<T>,
    gruppe: GruppenSchluessel,
    heute: Date,
): { geladen: number; gesamt: number; vollstaendig: boolean; alt: { geladen: number; gesamt: number } | null } {
    const gesamt = gesamtJeGruppe(daten, heute);
    const liste = daten[gruppe];
    const zaehler = gruppe === 'offeneAnfragen' || gruppe === 'laufendeBestellungen' ? AKTUELL_ZAEHLER[gruppe] : null;
    if (!zaehler) {
        return { geladen: liste.length, gesamt: gesamt[gruppe], vollstaendig: liste.length >= gesamt[gruppe], alt: null };
    }
    const altGeladen = liste.filter(k => istAlt(k, heute)).length;
    const aktuellGeladen = liste.length - altGeladen;
    const altGesamt = Math.max(gesamt[gruppe] - gesamt[zaehler], altGeladen);
    const aktuellGesamt = Math.max(gesamt[zaehler], aktuellGeladen);
    return {
        geladen: liste.length,
        gesamt: Math.max(gesamt[gruppe], liste.length),
        vollstaendig: aktuellGeladen >= aktuellGesamt && altGeladen >= altGesamt,
        alt: { geladen: altGeladen, gesamt: altGesamt },
    };
}

/**
 * Teilt die angezeigten Ketten in Blöcke nach der Lade-Runde, in der sie kamen.
 * `teil` ist genau ein vom Server getrennt gekürzter Teil (aktuell oder alt bzw.
 * die ganze Gruppe), neueste zuerst – die Runde ist also Position / Seitengröße.
 * So bleiben schon sichtbare Karten im Mauerwerk stehen, wenn „Mehr laden“ neue
 * anhängt – sonst würden die Spalten neu aufgeteilt und alles verrutscht.
 */
export function inLadeBloecke<T>(teil: T[], seite = SEITEN_GROESSE): T[][] {
    const bloecke: T[][] = [];
    for (let i = 0; i < teil.length; i += seite) bloecke.push(teil.slice(i, i + seite));
    return bloecke;
}

/**
 * Sofort-Anzeige beim Ausblenden: Ketten wandern nach „Ausgeblendet“, die
 * Zähler ziehen mit. Der Server-Stand kommt danach mit dem stillen Neuladen.
 */
export function ausblendenOptimistisch<T extends Kette & { id: string }>(
    daten: UebersichtListen<T>,
    ketten: T[],
    heute: Date,
): UebersichtListen<T> {
    const ids = new Set(ketten.map(k => k.id));
    const gesamt = gesamtJeGruppe(daten, heute);
    const neu = { ...gesamt };
    for (const gruppe of GRUPPEN) {
        if (gruppe === 'ausgeblendet') continue;
        const entfernt = daten[gruppe].filter(k => ids.has(k.id));
        neu[gruppe] = Math.max(0, gesamt[gruppe] - entfernt.length);
        if (gruppe === 'offeneAnfragen' || gruppe === 'laufendeBestellungen') {
            const zaehler = AKTUELL_ZAEHLER[gruppe];
            const aktuell = entfernt.filter(k => !istAlt(k, heute)).length;
            neu[zaehler] = Math.max(0, gesamt[zaehler] - aktuell);
        }
    }
    const schonAusgeblendet = daten.ausgeblendet.filter(k => ids.has(k.id)).length;
    neu.ausgeblendet = gesamt.ausgeblendet + ketten.length - schonAusgeblendet;
    return {
        offeneAnfragen: daten.offeneAnfragen.filter(k => !ids.has(k.id)),
        laufendeBestellungen: daten.laufendeBestellungen.filter(k => !ids.has(k.id)),
        abgeschlossen: daten.abgeschlossen.filter(k => !ids.has(k.id)),
        zugeordnet: daten.zugeordnet.filter(k => !ids.has(k.id)),
        ausgeblendet: [...ketten, ...daten.ausgeblendet.filter(k => !ids.has(k.id))],
        gesamt: neu,
    };
}

/**
 * Sofort-Anzeige beim Einblenden: Die Kette verschwindet aus „Ausgeblendet“.
 * In welchen Reiter sie zurückkehrt, weiß erst der Server (stilles Neuladen).
 */
export function einblendenOptimistisch<T extends Kette & { id: string }>(
    daten: UebersichtListen<T>,
    kette: T,
    heute: Date,
): UebersichtListen<T> {
    const gesamt = gesamtJeGruppe(daten, heute);
    const war = daten.ausgeblendet.some(k => k.id === kette.id);
    return {
        ...daten,
        ausgeblendet: daten.ausgeblendet.filter(k => k.id !== kette.id),
        gesamt: { ...gesamt, ausgeblendet: Math.max(0, gesamt.ausgeblendet - (war ? 1 : 0)) },
    };
}
