/**
 * Rechnungs-Vorschläge der Bestellübersicht: Typen, Aufrufe der API und kleine
 * reine Hilfen (Farbe der Trefferquote, Suche in der Vorschlagsliste).
 */
import { passtZurSuche } from './bestellungenListe';

export interface RechnungsDokument {
    id: number;
    typ: 'ANGEBOT' | 'AUFTRAGSBESTAETIGUNG' | 'LIEFERSCHEIN' | 'RECHNUNG' | 'GUTSCHRIFT' | 'SONSTIG';
    dokumentNummer: string | null;
    dokumentDatum: string | null;
    eingangsDatum?: string | null;
    betragBrutto: number | null;
    betragNetto: number | null;
    liefertermin: string | null;
    dateiname: string;
    pdfUrl: string | null;
}

export interface RechnungsVorschlag {
    rechnung: RechnungsDokument;
    lieferantName: string | null;
    bestellDokumentId: number;
    bestellDokumentTyp: 'AUFTRAGSBESTAETIGUNG' | 'LIEFERSCHEIN';
    bestellDokumentNummer: string | null;
    /** 0 bis 100 */
    trefferquote: number;
    sicher: boolean;
    /** false = eine andere Rechnung passt genauso gut */
    eindeutig: boolean;
    gruende: string[];
}

export type TrefferStufe = 'hoch' | 'mittel' | 'niedrig';

/** Ab dieser Quote wird ein Vorschlag im Fenster vorausgewählt (wie die Karte im Backend). */
export const MIN_QUOTE_VORAUSWAHL = 40;
/** Darunter – oder bei Gleichstand – fragt die Oberfläche vor dem Zuordnen nach. */
export const QUOTE_OHNE_RUECKFRAGE = 70;

/**
 * Eine Zuordnung lässt sich in der Oberfläche nicht lösen. Bei unsicheren
 * Vorschlägen deshalb nachfragen.
 */
export function brauchtRueckfrage(vorschlag: RechnungsVorschlag): boolean {
    return vorschlag.trefferquote < QUOTE_OHNE_RUECKFRAGE || !vorschlag.eindeutig;
}

export function rueckfrageText(vorschlag: RechnungsVorschlag): string {
    const nummer = vorschlag.rechnung.dokumentNummer ?? vorschlag.rechnung.dateiname;
    const grund = vorschlag.eindeutig
        ? `Die Trefferquote liegt nur bei ${formatiereQuote(vorschlag.trefferquote)}.`
        : 'Eine andere Rechnung passt genauso gut.';
    return `${grund} Rechnung ${nummer} trotzdem dieser Bestellung zuordnen? Das lässt sich hier nicht rückgängig machen.`;
}

/** Grün ab 70 %, Bernstein ab 40 %, darunter neutral. */
export function trefferStufe(quote: number): TrefferStufe {
    if (quote >= 70) return 'hoch';
    if (quote >= 40) return 'mittel';
    return 'niedrig';
}

export const TREFFER_KLASSEN: Record<TrefferStufe, string> = {
    hoch: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    mittel: 'bg-amber-50 text-amber-800 border-amber-200',
    niedrig: 'bg-slate-100 text-slate-600 border-slate-200',
};

export function formatiereQuote(quote: number): string {
    return `${Math.round(quote)} %`;
}

export function passtZurRechnungsSuche(vorschlag: RechnungsVorschlag, suchbegriff: string): boolean {
    return passtZurSuche(
        { lieferantName: vorschlag.lieferantName, dokumente: [vorschlag.rechnung] },
        suchbegriff,
    );
}

async function fehlerText(res: Response, standard: string): Promise<string> {
    try {
        const body = await res.json() as { message?: unknown };
        if (typeof body.message === 'string' && body.message.trim()) return body.message;
    } catch {
        // Antwort ohne Inhalt: es bleibt beim Standardtext
    }
    return standard;
}

/** Alle offenen Rechnungen, bewertet gegen die Bestellung (beste zuerst). */
export async function ladeRechnungsVorschlaege(dokumentIds: number[]): Promise<RechnungsVorschlag[]> {
    const params = new URLSearchParams();
    dokumentIds.forEach(id => params.append('dokumentIds', String(id)));
    const res = await fetch(`/api/bestellungen-uebersicht/rechnung-vorschlaege?${params.toString()}`);
    if (!res.ok) throw new Error(await fehlerText(res, 'Rechnungen konnten nicht geladen werden.'));
    const json: unknown = await res.json();
    return Array.isArray(json) ? json as RechnungsVorschlag[] : [];
}

export async function rechnungVerknuepfen(bestellDokumentId: number, rechnungDokumentId: number): Promise<void> {
    const res = await fetch('/api/bestellungen-uebersicht/rechnung-verknuepfen', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ bestellDokumentId, rechnungDokumentId }),
    });
    if (!res.ok) throw new Error(await fehlerText(res, 'Rechnung konnte nicht zugeordnet werden.'));
}
