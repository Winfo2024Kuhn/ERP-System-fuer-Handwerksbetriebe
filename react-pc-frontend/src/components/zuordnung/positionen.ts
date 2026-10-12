/**
 * Typen und kleine reine Helfer für die Aufteilung einer Lieferanten-Rechnung
 * nach ihren Positionen (Bestellübersicht → „Projekt zuordnen“ → „Nach Positionen“).
 *
 * Die Beträge rechnet das Backend aus; hier wird nur zusammengestellt, welche
 * Warenposition zu welchem Projekt bzw. welcher Kostenstelle gehört.
 */

export type PositionsArt = 'WARE' | 'NEBENKOSTEN' | 'RABATT';

export interface DokumentPosition {
    id: number;
    positionNr: number;
    positionsArt: PositionsArt;
    externeArtikelnummer: string | null;
    bezeichnung: string | null;
    menge: number | null;
    mengeneinheit: string | null;
    einzelpreis: number | null;
    preiseinheit: string | null;
    gesamtpreisNetto: number | null;
    projektId: number | null;
    projektName: string | null;
    kostenstelleId: number | null;
    kostenstelleName: string | null;
    /** Werkstoff, z. B. „S235JR“ – vor allem bei Werkstoffzeugnissen. */
    werkstoff?: string | null;
    /** Charge bzw. Schmelze */
    charge?: string | null;
    /** Abmessung, z. B. „50 x 5“ */
    abmessung?: string | null;
    /**
     * Nur bei Nebenkosten und Rabatten: IDs der Warenpositionen, zu denen der
     * Zuschlag gehört. Leer (oder fehlend bei älteren Antworten) = gilt für alle.
     */
    bezugPositionIds?: number[] | null;
}

export interface PositionsUebersicht {
    geschaeftsdokumentId: number;
    dokumentTyp: string | null;
    auslesbar: boolean;
    betragNetto: number | null;
    betragBrutto: number | null;
    summePositionen: number | null;
    abweichung: number | null;
    abweichungAuffaellig: boolean;
    nachPositionenAufgeteilt: boolean;
    positionen: DokumentPosition[];
}

export interface PositionsZiel {
    positionId: number;
    projektId?: number;
    kostenstelleId?: number;
    /**
     * Nur bei Nebenkosten und Rabatten: `[]` = auf alle verteilen, `[ids]` = nur
     * auf die Ziele dieser Warenpositionen. Weggelassen = gespeicherten Bezug behalten.
     */
    bezugPositionIds?: number[] | null;
}

export interface ZielDetail {
    projektId?: number;
    kostenstelleId?: number;
    beschreibung?: string;
    streckungJahre?: number;
}

export interface AufteilungRequest {
    positionen: PositionsZiel[];
    ziele?: ZielDetail[];
}

export interface VorschauZiel {
    projektId: number | null;
    kostenstelleId: number | null;
    warenwert: number | null;
    anteilProzent: number | null;
    betragNetto: number | null;
    betragBrutto: number | null;
    /** Verbuchter Betrag: brutto bei Projekten, netto bei Kostenstellen. */
    betrag: number | null;
    /** Nebenkosten/Rabatte, die wegen ihres Bezugs bei diesem Ziel landen (in `betrag` schon enthalten). */
    nebenkostenMitBezug?: number | null;
}

export interface PositionsVorschau {
    ziele: VorschauZiel[];
    nichtZugeordnet: number;
    warenwert: number | null;
    /** Summe ALLER Nebenkosten und Rabatte. */
    nebenkosten: number | null;
    /** Davon: Nebenkosten und Rabatte, die nur zu bestimmten Positionen gehören. */
    nebenkostenMitBezug?: number | null;
    abweichung: number | null;
    speicherbar: boolean;
    hinweis: string | null;
}

/**
 * Schlüssel eines Ziels: `p:<id>` für Projekte, `k:<id>` für Kostenstellen.
 * Leerer String = (noch) kein Ziel.
 */
export type ZielSchluessel = string;

export interface ZielRef {
    projektId?: number | null;
    kostenstelleId?: number | null;
}

export function zielSchluessel(ziel: ZielRef): ZielSchluessel {
    if (ziel.projektId != null) return `p:${ziel.projektId}`;
    if (ziel.kostenstelleId != null) return `k:${ziel.kostenstelleId}`;
    return '';
}

/** Umkehrung von {@link zielSchluessel}; unbekannte Schlüssel ergeben „kein Ziel“. */
export function zielAusSchluessel(schluessel: ZielSchluessel): { projektId?: number; kostenstelleId?: number } {
    const treffer = /^([pk]):(\d+)$/.exec(schluessel);
    if (!treffer) return {};
    const id = Number(treffer[2]);
    return treffer[1] === 'p' ? { projektId: id } : { kostenstelleId: id };
}

/** Dokumente ohne Preise – eine Aufteilung nach Positionen ergäbe keine Beträge (Sackgasse im Dialog). */
const TYPEN_OHNE_PREISE: ReadonlySet<string> = new Set(['WERKSTOFFZEUGNIS']);

/**
 * Lässt sich das Dokument nach Positionen aufteilen? Nur mit geladener Übersicht,
 * wenn Positionen da oder auslesbar sind – und nie bei Werkstoffzeugnissen.
 */
export function nachPositionenAufteilbar(uebersicht: Pick<PositionsUebersicht, 'dokumentTyp' | 'auslesbar' | 'positionen'> | null | undefined): boolean {
    if (!uebersicht) return false;
    if (uebersicht.dokumentTyp != null && TYPEN_OHNE_PREISE.has(uebersicht.dokumentTyp)) return false;
    return uebersicht.auslesbar || (uebersicht.positionen?.length ?? 0) > 0;
}

export function istWare(position: DokumentPosition): boolean {
    return position.positionsArt === 'WARE';
}

/** Ziele, die beim Laden schon an den Positionen hängen (gespeicherte Aufteilung). */
export function zuweisungenAusPositionen(positionen: DokumentPosition[]): Record<number, ZielSchluessel> {
    const ergebnis: Record<number, ZielSchluessel> = {};
    for (const position of positionen) {
        if (!istWare(position)) continue;
        const schluessel = zielSchluessel(position);
        if (schluessel) ergebnis[position.id] = schluessel;
    }
    return ergebnis;
}

/** Warenpositionen ohne Ziel. */
export function offeneWarenpositionen(positionen: DokumentPosition[], zuweisungen: Record<number, ZielSchluessel>): DokumentPosition[] {
    return positionen.filter(p => istWare(p) && !zuweisungen[p.id]);
}

/** Nebenkosten oder Rabatt – kann zu einzelnen Warenpositionen gehören. */
export function istZuschlag(position: DokumentPosition): boolean {
    return position.positionsArt === 'NEBENKOSTEN' || position.positionsArt === 'RABATT';
}

/** Bezug je Zuschlag: Position-ID → IDs der Warenpositionen (leer = gilt für alle). */
export type Bezuege = Record<number, number[]>;

/**
 * Bezüge, wie sie beim Laden an den Positionen hängen. Unbekannte IDs (keine
 * Warenposition dieses Belegs) fallen weg, damit das Backend nichts ablehnt.
 */
export function bezuegeAusPositionen(positionen: DokumentPosition[]): Bezuege {
    const warenIds = new Set(positionen.filter(istWare).map(p => p.id));
    const ergebnis: Bezuege = {};
    for (const position of positionen) {
        if (!istZuschlag(position)) continue;
        const ids = Array.isArray(position.bezugPositionIds) ? position.bezugPositionIds : [];
        ergebnis[position.id] = ids.filter(id => warenIds.has(id));
    }
    return ergebnis;
}

/**
 * Request-Teil „positionen“: jede Warenposition mit ihrem Ziel (oder ohne = offen),
 * jede Nebenkosten-/Rabattzeile mit ihrem aktuellen Bezug (immer ausdrücklich).
 */
export function baueAufteilung(positionen: DokumentPosition[], zuweisungen: Record<number, ZielSchluessel>, bezuege: Bezuege = {}): PositionsZiel[] {
    const ergebnis: PositionsZiel[] = [];
    for (const p of positionen) {
        if (istWare(p)) ergebnis.push({ positionId: p.id, ...zielAusSchluessel(zuweisungen[p.id] ?? '') });
        else if (istZuschlag(p)) ergebnis.push({ positionId: p.id, bezugPositionIds: [...(bezuege[p.id] ?? [])] });
    }
    return ergebnis;
}

/**
 * Text hinter „Gilt für:“ – „alle Positionen“ oder „Pos. 3, 5“ (nach Positionsnummer
 * sortiert). IDs, die es nicht als Warenposition gibt, werden übergangen.
 */
export function gueltigFuerText(bezug: readonly number[] | null | undefined, positionen: DokumentPosition[]): string {
    if (!bezug || bezug.length === 0) return 'alle Positionen';
    const gewaehlt = new Set(bezug);
    const nummern = positionen
        .filter(p => istWare(p) && gewaehlt.has(p.id))
        .map(p => p.positionNr)
        .sort((a, b) => a - b);
    return nummern.length === 0 ? 'alle Positionen' : `Pos. ${nummern.join(', ')}`;
}

/** Nebenkosten/Rabatte, die anteilig auf alle verteilt werden (Summe minus die mit Bezug). */
export function nebenkostenAufAlle(vorschau: Pick<PositionsVorschau, 'nebenkosten' | 'nebenkostenMitBezug'> | null | undefined): number | null {
    if (vorschau?.nebenkosten == null) return null;
    return Math.round((vorschau.nebenkosten - (vorschau.nebenkostenMitBezug ?? 0)) * 100) / 100;
}

/** Ab einem Cent zählt ein Betrag als vorhanden (Rabatte mit Bezug sind negativ). */
export function istBetragDa(wert: number | null | undefined): wert is number {
    return wert != null && Number.isFinite(wert) && Math.abs(wert) >= 0.005;
}

export const formatEuro = (value: number | null | undefined): string => {
    if (value == null || !Number.isFinite(value)) return '–';
    return value.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

export const formatMenge = (value: number | null | undefined): string => {
    if (value == null || !Number.isFinite(value)) return '–';
    return value.toLocaleString('de-DE', { maximumFractionDigits: 3 });
};

export const formatProzent = (value: number | null | undefined): string => {
    if (value == null || !Number.isFinite(value)) return '–';
    return value.toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
};

export interface PositionsZielName {
    projektId?: number;
    projektName?: string;
    kostenstelleId?: number;
    kostenstelleName?: string;
}

/** Alle Ziele, die an Warenpositionen hängen – je Ziel einmal, in Positionsreihenfolge. */
export function zieleAusPositionen(positionen: DokumentPosition[]): PositionsZielName[] {
    const gesehen = new Set<string>();
    const ergebnis: PositionsZielName[] = [];
    for (const p of positionen) {
        if (!istWare(p)) continue;
        const schluessel = zielSchluessel(p);
        if (!schluessel || gesehen.has(schluessel)) continue;
        gesehen.add(schluessel);
        ergebnis.push(p.projektId != null
            ? { projektId: p.projektId, projektName: p.projektName ?? `Projekt ${p.projektId}` }
            : { kostenstelleId: p.kostenstelleId ?? undefined, kostenstelleName: p.kostenstelleName ?? `Kostenstelle ${p.kostenstelleId}` });
    }
    return ergebnis;
}

// ---------------------------------------------------------------- Ziehen & Ablegen

/** Präfix der Ablageflächen-IDs; `ablage:` ohne Ziel = „Nicht zugeordnet“. */
const ABLAGE_PRAEFIX = 'ablage:';

export function ablageId(ziel: ZielSchluessel): string {
    return `${ABLAGE_PRAEFIX}${ziel}`;
}

/** Ziel einer Ablagefläche; `''` = Zuordnung aufheben, `null` = keine Ablagefläche. */
export function zielAusAblage(id: string | number | null | undefined): ZielSchluessel | null {
    if (typeof id !== 'string' || !id.startsWith(ABLAGE_PRAEFIX)) return null;
    const ziel = id.slice(ABLAGE_PRAEFIX.length);
    return ziel === '' || zielSchluessel(zielAusSchluessel(ziel)) === ziel ? ziel : null;
}

/**
 * Welche Positionen wandern beim Ablegen? Ist die gezogene Zeile markiert,
 * alle markierten Warenpositionen (in Listenreihenfolge), sonst nur sie selbst.
 */
export function gezogenePositionen(gezogenId: number, markiert: ReadonlySet<number>, positionen: DokumentPosition[]): number[] {
    if (markiert.has(gezogenId)) {
        return positionen.filter(p => istWare(p) && markiert.has(p.id)).map(p => p.id);
    }
    const position = positionen.find(p => p.id === gezogenId);
    return position && istWare(position) ? [position.id] : [];
}
