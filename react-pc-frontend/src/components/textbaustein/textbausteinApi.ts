import type { DokumenttypGruppe, Platzhalter, TextbausteinDto, TextbausteinSpeichernDaten } from './typen';

const BASIS = '/api/textbausteine';

/** Fehler mit verständlicher Meldung aus der Antwort; `felder` nennt betroffene Eingaben (z. B. `name`). */
export class TextbausteinFehler extends Error {
    readonly felder: string[];

    constructor(meldung: string, felder: string[] = []) {
        super(meldung);
        this.name = 'TextbausteinFehler';
        this.felder = felder;
    }
}

/** Meldung für den Toast: die des Servers, sonst (z. B. ohne Netz) der verständliche Rückfall. */
export function fehlerMeldung(fehler: unknown, rueckfall: string): string {
    return fehler instanceof TextbausteinFehler ? fehler.message : rueckfall;
}

interface FehlerAntwort {
    message?: string;
    fields?: { field?: string; label?: string; message?: string }[];
}

/** Baut aus der Fehler-Antwort (`{ message, fields: [{ field, message }] }`) eine Meldung. */
async function fehlerAus(antwort: Response, rueckfall: string): Promise<TextbausteinFehler> {
    const daten = (await antwort.json().catch(() => null)) as FehlerAntwort | null;
    const felder = Array.isArray(daten?.fields) ? daten.fields : [];
    const feldMeldungen = felder.map((feld) => feld.message).filter((m): m is string => Boolean(m));
    const meldung = feldMeldungen.length > 0 ? feldMeldungen.join(' ') : daten?.message || rueckfall;
    return new TextbausteinFehler(meldung, felder.map((feld) => feld.field ?? '').filter(Boolean));
}

async function ladeListe<T>(url: string, rueckfall: string): Promise<T[]> {
    const antwort = await fetch(url);
    if (!antwort.ok) throw await fehlerAus(antwort, rueckfall);
    const daten: unknown = await antwort.json();
    return Array.isArray(daten) ? (daten as T[]) : [];
}

/** Sorgt dafür, dass Listenfelder nie fehlen – ältere Datensätze haben sie teils nicht. */
function vervollstaendige(dto: TextbausteinDto): TextbausteinDto {
    return {
        ...dto,
        dokumenttypen: Array.isArray(dto.dokumenttypen) ? dto.dokumenttypen.map(String) : [],
        placeholders: Array.isArray(dto.placeholders) ? dto.placeholders : [],
    };
}

export async function ladeTextbausteine(): Promise<TextbausteinDto[]> {
    const liste = await ladeListe<TextbausteinDto>(BASIS, 'Die Textbausteine konnten nicht geladen werden.');
    return liste.map(vervollstaendige);
}

export function ladePlatzhalter(): Promise<Platzhalter[]> {
    return ladeListe<Platzhalter>(`${BASIS}/placeholders`, 'Die Platzhalter konnten nicht geladen werden.');
}

export function ladeDokumenttypGruppen(): Promise<DokumenttypGruppe[]> {
    return ladeListe<DokumenttypGruppe>(`${BASIS}/dokumenttyp-gruppen`, 'Die Dokumentarten konnten nicht geladen werden.');
}

/** Legt an (`id` null) oder ändert und liefert den gespeicherten Stand zurück. */
export async function speichereTextbaustein(id: number | null, daten: TextbausteinSpeichernDaten): Promise<TextbausteinDto> {
    const antwort = await fetch(id === null ? BASIS : `${BASIS}/${id}`, {
        method: id === null ? 'POST' : 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(daten),
    });
    if (!antwort.ok) throw await fehlerAus(antwort, 'Der Textbaustein konnte nicht gespeichert werden.');
    return vervollstaendige(await antwort.json());
}

export async function loescheTextbaustein(id: number): Promise<void> {
    const antwort = await fetch(`${BASIS}/${id}`, { method: 'DELETE' });
    if (!antwort.ok) throw await fehlerAus(antwort, 'Der Textbaustein konnte nicht gelöscht werden.');
}
