/**
 * Vorschläge für eine Dokumentenkette: Welche Lieferanten-Dokumente gehören
 * wahrscheinlich noch dazu – egal welcher Art (Werkstoffzeugnis zum
 * Lieferschein, AB zum Angebot, Rechnung zur AB)? Typen, API-Aufrufe und
 * reine Hilfen für Suche, Texte und Rückfragen.
 */
import type { LieferantDokument } from '../../types';
import { TYP_LABELS, passtZurSuche, type KettenDokumentTyp } from './bestellungenListe';
import {
    antwortFehlerText,
    brauchtRueckfrage,
    formatiereQuote,
    type KettenBeleg,
    type Rueckfrage,
    type Treffer,
} from './rechnungsVorschlag';

/** Arten, die eine Kette bilden. Sonstiges gehört nie dazu. */
export type VorschlagsTyp = Exclude<KettenDokumentTyp, 'SONSTIG'>;

/** Reihenfolge der Filter im Dialog: Rechnung zuerst, weil sie am häufigsten fehlt. */
export const VORSCHLAGS_TYPEN: readonly VorschlagsTyp[] = [
    'RECHNUNG', 'LIEFERSCHEIN', 'WERKSTOFFZEUGNIS', 'AUFTRAGSBESTAETIGUNG', 'ANGEBOT', 'GUTSCHRIFT',
];

export function istVorschlagsTyp(typ: string): typ is VorschlagsTyp {
    return (VORSCHLAGS_TYPEN as readonly string[]).includes(typ);
}

export interface KettenVorschlag extends Treffer {
    dokument: KettenBeleg;
    lieferantName: string | null;
    /** Dokument der Kette, an das der Vorschlag gehängt wird. */
    kettenDokumentId: number;
    kettenDokumentTyp: KettenDokumentTyp;
    kettenDokumentNummer: string | null;
    sicher: boolean;
    gruende: string[];
    /** Hängt schon in einer anderen Kette, z. B. „Lieferschein 4711“. */
    gehoertSchonZu?: string | null;
}

/** Die Kette, zu der gesucht wird – aus der Bestellübersicht oder vom Lieferanten. */
export interface SuchKette {
    lieferantName: string | null;
    dokumente: KettenBeleg[];
}

interface Woerter {
    einzahl: string;
    mehrzahl: string;
    /** „keine Rechnung“, „kein Lieferschein“ */
    kein: string;
    /** Relativpronomen: „…, die passen könnte“ */
    relativ: 'der' | 'die' | 'das';
}

const DOKUMENT_WOERTER: Woerter = { einzahl: 'Dokument', mehrzahl: 'Dokumente', kein: 'kein Dokument', relativ: 'das' };

const TYP_WOERTER: Record<VorschlagsTyp, Woerter> = {
    RECHNUNG: { einzahl: 'Rechnung', mehrzahl: 'Rechnungen', kein: 'keine Rechnung', relativ: 'die' },
    LIEFERSCHEIN: { einzahl: 'Lieferschein', mehrzahl: 'Lieferscheine', kein: 'kein Lieferschein', relativ: 'der' },
    WERKSTOFFZEUGNIS: { einzahl: 'Werkstoffzeugnis', mehrzahl: 'Werkstoffzeugnisse', kein: 'kein Werkstoffzeugnis', relativ: 'das' },
    AUFTRAGSBESTAETIGUNG: { einzahl: 'AB', mehrzahl: 'ABs', kein: 'keine AB', relativ: 'die' },
    ANGEBOT: { einzahl: 'Angebot', mehrzahl: 'Angebote', kein: 'kein Angebot', relativ: 'das' },
    GUTSCHRIFT: { einzahl: 'Gutschrift', mehrzahl: 'Gutschriften', kein: 'keine Gutschrift', relativ: 'die' },
};

/** Wörter für eine Dokumentart – `null` heißt „alle Arten“ und ergibt „Dokument“. */
export function typWoerter(typ: VorschlagsTyp | null): Woerter {
    return typ ? TYP_WOERTER[typ] : DOKUMENT_WOERTER;
}

/** „3 Lieferscheine“, „1 Rechnung“, „0 Dokumente“ */
export function anzahlMitNomen(anzahl: number, typ: VorschlagsTyp | null): string {
    const woerter = typWoerter(typ);
    return `${anzahl} ${anzahl === 1 ? woerter.einzahl : woerter.mehrzahl}`;
}

/** „Lieferschein 4711“ – ohne Nummer mit dem Dateinamen. */
export function dokumentBezeichnung(dok: Pick<KettenBeleg, 'typ' | 'dokumentNummer' | 'dateiname'>): string {
    return `${TYP_LABELS[dok.typ]} ${dok.dokumentNummer ?? dok.dateiname}`;
}

/** „Lieferschein 4711“ – das Kettendokument, an das der Vorschlag passt. */
export function kettenDokumentBezeichnung(vorschlag: Pick<KettenVorschlag, 'kettenDokumentTyp' | 'kettenDokumentNummer'>): string {
    const label = TYP_LABELS[vorschlag.kettenDokumentTyp] ?? 'Dokument';
    return vorschlag.kettenDokumentNummer ? `${label} ${vorschlag.kettenDokumentNummer}` : label;
}

export function passtZurKettenSuche(vorschlag: KettenVorschlag, suchbegriff: string): boolean {
    return passtZurSuche({ lieferantName: vorschlag.lieferantName, dokumente: [vorschlag.dokument] }, suchbegriff);
}

/** Rückfrage vor dem Zuordnen – oder null, wenn ohne Nachfrage zugeordnet werden darf. */
export function kettenRueckfrage(vorschlag: KettenVorschlag): Rueckfrage | null {
    const bezeichnung = dokumentBezeichnung(vorschlag.dokument);
    if (vorschlag.gehoertSchonZu) {
        return {
            title: 'Dokument hängt schon an einer anderen Bestellung',
            message: `Dieses Dokument (${bezeichnung}) hängt schon an ${vorschlag.gehoertSchonZu}. Beide Bestellungen werden dann zusammengefasst.`,
            confirmLabel: 'Zusammenfassen',
            variant: 'warning',
        };
    }
    if (!brauchtRueckfrage(vorschlag)) return null;
    const grund = vorschlag.eindeutig
        ? `Die Trefferquote liegt nur bei ${formatiereQuote(vorschlag.trefferquote)}.`
        : 'Ein anderes Dokument passt genauso gut.';
    return {
        title: 'Wirklich zur Kette hinzufügen?',
        message: `${grund} ${bezeichnung} trotzdem an ${kettenDokumentBezeichnung(vorschlag)} hängen? Falls es nicht passt, lässt es sich später wieder abhängen.`,
        confirmLabel: 'Hinzufügen',
        variant: 'warning',
    };
}

/**
 * Dokumente, die zur Kette passen könnten (beste zuerst). Eine Dokument-ID
 * reicht, der Server sammelt die ganze Kette. Standard: nur derselbe Lieferant.
 */
export async function ladeKettenVorschlaege(
    dokumentIds: number[],
    optionen: { alleLieferanten?: boolean; typ?: VorschlagsTyp | null } = {},
): Promise<KettenVorschlag[]> {
    const params = new URLSearchParams();
    dokumentIds.forEach(id => params.append('dokumentIds', String(id)));
    params.append('alleLieferanten', String(optionen.alleLieferanten === true));
    if (optionen.typ) params.append('typ', optionen.typ);
    const res = await fetch(`/api/bestellungen-uebersicht/ketten-vorschlaege?${params.toString()}`);
    if (!res.ok) throw new Error(await antwortFehlerText(res, 'Die Vorschläge konnten nicht geladen werden.'));
    const json: unknown = await res.json();
    return Array.isArray(json) ? json as KettenVorschlag[] : [];
}

/** Hängt ein Dokument an ein Dokument der Kette. Die Richtung bestimmt der Server. */
export async function inKetteVerknuepfen(kettenDokumentId: number, dokumentId: number): Promise<void> {
    const res = await fetch('/api/bestellungen-uebersicht/ketten-verknuepfen', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ kettenDokumentId, dokumentId }),
    });
    if (!res.ok) throw new Error(await antwortFehlerText(res, 'Das Dokument konnte nicht zugeordnet werden.'));
}

/**
 * Lieferanten-Dokument (Reiter „Dokumente“) in die Form, die der Suchdialog
 * braucht. Ohne `url` dient der Download-Pfad des Lieferanten als Vorschau.
 */
export function lieferantDokumentAlsBeleg(dok: LieferantDokument, lieferantId?: number | string): KettenBeleg {
    const download = lieferantId != null && String(lieferantId) !== ''
        ? `/api/lieferanten/${encodeURIComponent(String(lieferantId))}/dokumente/${dok.id}/download`
        : null;
    const daten = dok.geschaeftsdaten;
    return {
        id: dok.id,
        typ: dok.typ,
        dokumentNummer: daten?.dokumentNummer ?? null,
        dokumentDatum: daten?.dokumentDatum ?? null,
        eingangsDatum: dok.uploadDatum ?? null,
        betragBrutto: daten?.betragBrutto ?? null,
        betragNetto: daten?.betragNetto ?? null,
        liefertermin: daten?.liefertermin ?? null,
        dateiname: dok.originalDateiname,
        pdfUrl: dok.url || download,
    };
}
