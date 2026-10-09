/**
 * Artikelpositionen eines Lieferanten-Dokuments zum Aufklappen (Kette, Dokumentübersicht).
 *
 * Geladen wird erst beim ersten Aufklappen. Ergebnisse mit Positionen bleiben im
 * Speicher, damit Zuklappen und wieder Aufklappen – oder dasselbe Dokument an
 * einer zweiten Stelle der Seite – nicht neu lädt. Leere Ergebnisse werden nicht
 * gemerkt: Das Auslesen läuft nach dem Hochladen im Hintergrund, die Positionen
 * können also kurz danach noch kommen.
 */
import type { DokumentPosition } from '../../components/zuordnung/positionen';
import { normalisiere, suchwoerter } from '../../lib/positionsTreffer';

export const POSITIONEN_FEHLER_TEXT = 'Artikelpositionen konnten nicht geladen werden.';

const gemerkt = new Map<number, DokumentPosition[]>();
const laufend = new Map<number, Promise<DokumentPosition[]>>();
/**
 * Stand je Dokument: steigt bei jedem Vergessen. Eine Antwort, die vor dem
 * Vergessen losgeschickt wurde, landet danach nicht mehr im Speicher.
 */
const stand = new Map<number, number>();
let standAlle = 0;

const zuhoerer = new Set<() => void>();

/** Stand eines Dokuments – ändert sich, sobald seine Positionen vergessen werden. */
export function positionenStand(dokumentId: number): string {
    return `${standAlle}:${stand.get(dokumentId) ?? 0}`;
}

/** Meldet jedes Vergessen, damit offene Listen neu laden (für useSyncExternalStore). */
export function abonnierePositionen(zuhoererNeu: () => void): () => void {
    zuhoerer.add(zuhoererNeu);
    return () => { zuhoerer.delete(zuhoererNeu); };
}

const aktuellerStand = positionenStand;

/** Schon geladene Positionen (oder undefined, wenn noch nichts gemerkt ist). */
export function gemerktePositionen(dokumentId: number): DokumentPosition[] | undefined {
    return gemerkt.get(dokumentId);
}

/**
 * Vergisst die Positionen eines Dokuments (nach Auslesen oder Aufteilen) – ohne
 * Angabe alle (z. B. beim Abmelden). Beim nächsten Aufklappen wird neu geladen.
 */
export function vergissDokumentPositionen(dokumentId?: number): void {
    if (dokumentId === undefined) {
        standAlle++;
        gemerkt.clear();
        laufend.clear();
    } else {
        stand.set(dokumentId, (stand.get(dokumentId) ?? 0) + 1);
        gemerkt.delete(dokumentId);
        laufend.delete(dokumentId);
    }
    zuhoerer.forEach(melde => melde());
}

/** Lädt die Positionen eines Dokuments; 404 heißt „keine Positionen“. Gleichzeitige Anfragen teilen sich einen Abruf. */
export function ladeDokumentPositionen(dokumentId: number): Promise<DokumentPosition[]> {
    const vorhanden = gemerkt.get(dokumentId);
    if (vorhanden) return Promise.resolve(vorhanden);
    const offen = laufend.get(dokumentId);
    if (offen) return offen;

    const standBeimStart = aktuellerStand(dokumentId);
    const abruf: Promise<DokumentPosition[]> = fetch(`/api/bestellungen-uebersicht/positionen/${encodeURIComponent(String(dokumentId))}`)
        .then(async res => {
            if (res.status === 404) return [];
            if (!res.ok) throw new Error(POSITIONEN_FEHLER_TEXT);
            const daten = await res.json() as { positionen?: unknown };
            return Array.isArray(daten?.positionen) ? (daten.positionen as DokumentPosition[]) : [];
        })
        .then(positionen => {
            if (positionen.length > 0 && aktuellerStand(dokumentId) === standBeimStart) gemerkt.set(dokumentId, positionen);
            return positionen;
        })
        .finally(() => {
            if (laufend.get(dokumentId) === abruf) laufend.delete(dokumentId);
        });
    laufend.set(dokumentId, abruf);
    return abruf;
}

/** Sonstiges hat keine Artikelpositionen – dort gibt es nichts aufzuklappen. */
export function kannPositionenHaben(typ: string | null | undefined): boolean {
    return typ !== 'SONSTIG';
}

/** Enthält die Position alle Suchwörter (normalisiert wie im Backend, z. B. „50x5“ = „50 x 5“)? */
export function positionTrifftSuche(position: DokumentPosition, suchbegriff: string | null | undefined): boolean {
    const woerter = suchwoerter(suchbegriff);
    if (woerter.length === 0) return false;
    const text = normalisiere([
        position.externeArtikelnummer, position.bezeichnung, position.werkstoff, position.charge, position.abmessung,
    ].filter(Boolean).join(' '));
    return woerter.every(wort => text.includes(wort));
}
