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
    /** Ausgeblendete Belege bleiben in der Kette sichtbar, nur gedämpft. */
    ausgeblendet?: boolean;
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
    /** Rechnung ist schon einer anderen Bestellung zugeordnet, z. B. „Lieferschein 4711“. */
    gehoertSchonZu?: string | null;
}

export type TrefferStufe = 'hoch' | 'mittel' | 'niedrig';

/** Ab dieser Quote wird ein Vorschlag im Fenster vorausgewählt (wie die Karte im Backend). */
export const MIN_QUOTE_VORAUSWAHL = 40;
/** Darunter – oder bei Gleichstand – fragt die Oberfläche vor dem Zuordnen nach. */
export const QUOTE_OHNE_RUECKFRAGE = 70;

/** Bei unsicheren Vorschlägen vor dem Zuordnen nachfragen. */
export function brauchtRueckfrage(vorschlag: RechnungsVorschlag): boolean {
    return vorschlag.trefferquote < QUOTE_OHNE_RUECKFRAGE || !vorschlag.eindeutig;
}

/** Rückfrage vor dem Zuordnen – oder null, wenn ohne Nachfrage zugeordnet werden darf. */
export function rueckfrage(vorschlag: RechnungsVorschlag): { title: string; message: string; confirmLabel: string; variant: 'warning' } | null {
    if (vorschlag.gehoertSchonZu) {
        return {
            title: 'Rechnung gehört schon zu einer Bestellung',
            message: `Diese Rechnung gehört schon zu ${vorschlag.gehoertSchonZu}. Ist das eine Teillieferung? Dann wird beides zu einer Bestellung zusammengefasst.`,
            confirmLabel: 'Zusammenfassen',
            variant: 'warning',
        };
    }
    if (!brauchtRueckfrage(vorschlag)) return null;
    return { title: 'Rechnung wirklich zuordnen?', message: rueckfrageText(vorschlag), confirmLabel: 'Zuordnen', variant: 'warning' };
}

export function rueckfrageText(vorschlag: RechnungsVorschlag): string {
    const nummer = vorschlag.rechnung.dokumentNummer ?? vorschlag.rechnung.dateiname;
    const grund = vorschlag.eindeutig
        ? `Die Trefferquote liegt nur bei ${formatiereQuote(vorschlag.trefferquote)}.`
        : 'Eine andere Rechnung passt genauso gut.';
    return `${grund} Rechnung ${nummer} trotzdem dieser Bestellung zuordnen? Falls es nicht passt, lässt sie sich später wieder abhängen.`;
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
        const body = await res.json() as { message?: unknown; error?: unknown };
        if (typeof body.message === 'string' && body.message.trim()) return body.message;
        if (typeof body.error === 'string' && body.error.trim()) return body.error;
    } catch {
        // Antwort ohne Inhalt: es bleibt beim Standardtext
    }
    return standard;
}

/**
 * Rechnungen, bewertet gegen die Bestellung (beste zuerst). Standard: nur
 * Rechnungen desselben Lieferanten – auch bezahlte, ausgeblendete und schon zugeordnete.
 */
export async function ladeRechnungsVorschlaege(
    dokumentIds: number[],
    optionen: { alleLieferanten?: boolean } = {},
): Promise<RechnungsVorschlag[]> {
    const params = new URLSearchParams();
    dokumentIds.forEach(id => params.append('dokumentIds', String(id)));
    params.append('alleLieferanten', String(optionen.alleLieferanten === true));
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

/** Löst alle Verknüpfungen eines Belegs; er wird danach nicht mehr automatisch zugeordnet. */
export async function dokumentAbhaengen(dokumentId: number): Promise<number> {
    const res = await fetch('/api/bestellungen-uebersicht/abhaengen', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dokumentId }),
    });
    if (!res.ok) throw new Error(await fehlerText(res, 'Der Beleg konnte nicht abgehängt werden.'));
    try {
        const body = await res.json() as { geloest?: unknown };
        return typeof body.geloest === 'number' ? body.geloest : 0;
    } catch {
        return 0;
    }
}

const RECHNUNGS_DATEI_ENDUNGEN = ['.pdf', '.jpg', '.jpeg', '.png'];
const RECHNUNGS_DATEI_TYPEN = ['application/pdf', 'image/jpeg', 'image/png'];
/** Für `<input type="file" accept=…>` */
export const RECHNUNGS_DATEI_ACCEPT = [...RECHNUNGS_DATEI_ENDUNGEN, ...RECHNUNGS_DATEI_TYPEN].join(',');

/** Nur PDF, JPG und PNG – andere Dateien lehnt schon die Oberfläche ab. */
export function istErlaubteRechnungsDatei(datei: { name: string; type: string }): boolean {
    const name = datei.name.toLocaleLowerCase('de-DE');
    const endungPasst = RECHNUNGS_DATEI_ENDUNGEN.some(endung => name.endsWith(endung));
    return endungPasst && (datei.type === '' || RECHNUNGS_DATEI_TYPEN.includes(datei.type));
}

/** Lädt eine Rechnung hoch und hängt sie sofort an das Bestelldokument. Das Auslesen läuft im Hintergrund. */
export async function rechnungHochladen(bestellDokumentId: number, datei: File): Promise<RechnungsDokument> {
    const formData = new FormData();
    formData.append('datei', datei);
    formData.append('bestellDokumentId', String(bestellDokumentId));
    const res = await fetch('/api/bestellungen-uebersicht/rechnung-hochladen', { method: 'POST', body: formData });
    if (!res.ok) throw new Error(await fehlerText(res, 'Die Rechnung konnte nicht hochgeladen werden.'));
    return await res.json() as RechnungsDokument;
}
