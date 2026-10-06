/**
 * Reine Hilfsfunktionen für die Bestellübersicht: Suche, Alter und Fortschritt
 * einer Dokumenten-Kette (Angebot → AB → Lieferschein → Rechnung).
 */

export type KettenDokumentTyp =
    | 'ANGEBOT'
    | 'AUFTRAGSBESTAETIGUNG'
    | 'LIEFERSCHEIN'
    | 'RECHNUNG'
    | 'GUTSCHRIFT'
    | 'SONSTIG';

export interface KettenDokument {
    typ: KettenDokumentTyp;
    dokumentNummer: string | null;
    dokumentDatum: string | null;
    /** Eingang im System – Ersatz, wenn kein Belegdatum erkannt wurde. */
    eingangsDatum?: string | null;
    betragBrutto: number | null;
    dateiname: string;
}

export interface Kette {
    lieferantName: string | null;
    dokumente: KettenDokument[];
}

/** Ab so vielen Tagen ohne neues Dokument gilt eine Kette als alt und wird eingeklappt. */
export const ALTER_GRENZE_TAGE = 60;

export const TYP_LABELS: Record<KettenDokumentTyp, string> = {
    ANGEBOT: 'Angebot',
    AUFTRAGSBESTAETIGUNG: 'AB',
    LIEFERSCHEIN: 'Lieferschein',
    RECHNUNG: 'Rechnung',
    GUTSCHRIFT: 'Gutschrift',
    SONSTIG: 'Sonstiges',
};

const TAG_MS = 24 * 60 * 60 * 1000;

/** Liest `yyyy-mm-dd` (auch mit Uhrzeit) als lokales Datum, damit keine Zeitzone den Tag verschiebt. */
export function parseIsoDatum(text: string | null | undefined): Date | null {
    if (!text) return null;
    const treffer = /^(\d{4})-(\d{2})-(\d{2})/.exec(text);
    if (!treffer) return null;
    // Vier-/zweistellige Ziffern ergeben immer ein gültiges Date; Unmögliches (Monat 13) rollt über.
    return new Date(Number(treffer[1]), Number(treffer[2]) - 1, Number(treffer[3]));
}

function dokumentDatum(dok: KettenDokument): Date | null {
    return parseIsoDatum(dok.dokumentDatum) ?? parseIsoDatum(dok.eingangsDatum);
}

/** Datum des jüngsten Dokuments der Kette – also wann sich zuletzt etwas bewegt hat. */
export function letzteBewegung(kette: Kette): Date | null {
    let neuestes: Date | null = null;
    for (const dok of kette.dokumente) {
        const datum = dokumentDatum(dok);
        if (datum && (!neuestes || datum > neuestes)) neuestes = datum;
    }
    return neuestes;
}

function tageZwischen(von: Date, bis: Date): number {
    const a = Date.UTC(von.getFullYear(), von.getMonth(), von.getDate());
    const b = Date.UTC(bis.getFullYear(), bis.getMonth(), bis.getDate());
    return Math.round((b - a) / TAG_MS);
}

/** Ohne bekanntes Datum gilt eine Kette nie als alt – lieber sichtbar lassen als verstecken. */
export function istAlt(kette: Kette, heute: Date, grenzeTage = ALTER_GRENZE_TAGE): boolean {
    const datum = letzteBewegung(kette);
    return datum != null && tageZwischen(datum, heute) > grenzeTage;
}

export function teileNachAlter<T extends Kette>(
    ketten: T[],
    heute: Date,
    grenzeTage = ALTER_GRENZE_TAGE,
): { aktuell: T[]; aelter: T[] } {
    const aktuell: T[] = [];
    const aelter: T[] = [];
    for (const kette of ketten) {
        (istAlt(kette, heute, grenzeTage) ? aelter : aktuell).push(kette);
    }
    return { aktuell, aelter };
}

/** „heute“, „vor 3 Wochen“, „vor 2 Jahren“ – grob genug, um auf einen Blick zu sehen, wie alt etwas ist. */
export function formatiereAlter(datum: Date | null, heute: Date): string {
    if (!datum) return 'Datum unbekannt';
    const tage = tageZwischen(datum, heute);
    if (tage <= 0) return 'heute';
    if (tage === 1) return 'gestern';
    if (tage < 14) return `vor ${tage} Tagen`;
    if (tage < 60) return `vor ${Math.floor(tage / 7)} Wochen`;
    if (tage < 365) return `vor ${Math.floor(tage / 30)} Monaten`;
    const jahre = Math.floor(tage / 365);
    return jahre === 1 ? 'vor über einem Jahr' : `vor ${jahre} Jahren`;
}

const STUFE: Partial<Record<KettenDokumentTyp, number>> = {
    ANGEBOT: 1,
    AUFTRAGSBESTAETIGUNG: 2,
    LIEFERSCHEIN: 3,
    RECHNUNG: 4,
    GUTSCHRIFT: 4,
};

/** Wie weit die Bestellung ist: 1 Angebot, 2 bestellt, 3 geliefert, 4 Rechnung da (0 = unklar). */
export function fortschrittsStufe(kette: Kette): number {
    return kette.dokumente.reduce((max, dok) => Math.max(max, STUFE[dok.typ] ?? 0), 0);
}

const BETRAG_VORRANG: KettenDokumentTyp[] = ['RECHNUNG', 'AUFTRAGSBESTAETIGUNG', 'ANGEBOT', 'LIEFERSCHEIN', 'GUTSCHRIFT'];

/**
 * Der aussagekräftigste Bruttobetrag der Kette: Rechnung vor AB vor Angebot.
 * Mehrere Rechnungen (Teilrechnungen) werden zusammengezählt; `anzahl` sagt, wie viele.
 */
export function kettenBetrag(kette: Kette): { betrag: number; typ: KettenDokumentTyp; anzahl: number } | null {
    for (const typ of BETRAG_VORRANG) {
        const betraege = kette.dokumente
            .filter(d => d.typ === typ && d.betragBrutto != null && Number.isFinite(d.betragBrutto))
            .map(d => d.betragBrutto as number);
        if (betraege.length === 0) continue;
        if (typ !== 'RECHNUNG') return { betrag: betraege[0], typ, anzahl: 1 };
        const summe = Math.round(betraege.reduce((a, b) => a + b, 0) * 100) / 100;
        return { betrag: summe, typ, anzahl: betraege.length };
    }
    return null;
}

function formatEuroText(wert: number): string {
    return wert.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDatumText(datum: Date): string {
    return datum.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function normalisiere(text: string): string {
    return text.toLocaleLowerCase('de-DE').replace(/€/g, ' ').trim();
}

function suchText(kette: Kette): string {
    const teile: string[] = [kette.lieferantName ?? ''];
    for (const dok of kette.dokumente) {
        teile.push(TYP_LABELS[dok.typ], dok.dokumentNummer ?? '', dok.dateiname ?? '');
        if (dok.betragBrutto != null && Number.isFinite(dok.betragBrutto)) {
            const betrag = formatEuroText(dok.betragBrutto);
            // „1.234,50“ und „1234,50“ sollen beide treffen
            teile.push(betrag, betrag.replace(/\./g, ''));
        }
        const datum = dokumentDatum(dok);
        if (datum) teile.push(formatDatumText(datum));
    }
    return normalisiere(teile.join(' '));
}

/** Alle Suchwörter müssen irgendwo in Lieferant, Nummer, Dateiname, Betrag oder Datum vorkommen. */
export function passtZurSuche(kette: Kette, suchbegriff: string): boolean {
    const woerter = normalisiere(suchbegriff).split(/\s+/).filter(Boolean);
    if (woerter.length === 0) return true;
    const text = suchText(kette);
    return woerter.every(wort => text.includes(wort));
}
