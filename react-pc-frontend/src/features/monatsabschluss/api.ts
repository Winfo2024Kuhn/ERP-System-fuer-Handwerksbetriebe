import type { Filter, Uebersicht, Vergleichsmonat, Referenz, SammelResponse, Verlauf, Konfiguration, ExportRequest, Vorpruefung } from './types';
const basis = '/api/zeitverwaltung/monatsabschluesse';
async function antwort(url: string, init?: RequestInit): Promise<Response> {
    const response = await fetch(url, init);
    if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(response.status === 403 ? 'Keine Berechtigung für den Monatsabschluss.' : data.message || data.detail || (response.status === 409 ? 'Der Stand hat sich geändert. Bitte erneut prüfen.' : 'Die Anfrage konnte nicht abgeschlossen werden. Bitte erneut versuchen.'));
    }
    return response;
}
async function json<T>(url: string, init?: RequestInit): Promise<T> { return (await antwort(url, init)).json(); }
function schreiben(method: string, body: unknown): RequestInit { return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }; }
function parameter(filter: Filter, vergleich = false) {
    const params = new URLSearchParams({ jahr: String(filter.jahr), monat: String(filter.monat) });
    if (filter.mitarbeiterId) params.set('mitarbeiterId', String(filter.mitarbeiterId));
    if (filter.abteilungId) params.set('abteilungId', String(filter.abteilungId));
    if (!vergleich) { params.set('status', filter.status); params.set('page', String(filter.page)); params.set('size', String(filter.size)); }
    return params;
}
export const api = {
    ladeBerechtigung: (signal?: AbortSignal) => json<{ darfMonatAbschliessen: boolean }>(`${basis}/berechtigung`, { signal }),
    ladeMitarbeiter: (signal?: AbortSignal) => json<{ id: number; vorname: string; nachname: string }[]>('/api/mitarbeiter', { signal }),
    ladeAbteilungen: (signal?: AbortSignal) => json<{ id: number; name: string }[]>('/api/abteilungen', { signal }),
    ladeUebersicht: (filter: Filter, signal?: AbortSignal) => json<Uebersicht>(`${basis}/uebersicht?${parameter(filter)}`, { signal }),
    ladeVergleich: (filter: Filter, signal?: AbortSignal) => json<Vergleichsmonat[]>(`${basis}/vergleich?${parameter(filter, true)}`, { signal }),
    sammelabschluss: (auswahl: Referenz[]) => json<SammelResponse>(`${basis}/sammelabschluss`, schreiben('POST', { auswahl })),
    ladeVerlauf: (referenz: Referenz, signal?: AbortSignal) => json<Verlauf>(`${basis}/${referenz.mitarbeiterId}/${referenz.jahr}/${referenz.monat}`, { signal }),
    ladeDatevKonfiguration: () => json<Konfiguration>(`${basis}/datev/konfiguration`),
    speichereDatevKonfiguration: (config: Konfiguration) => json<Konfiguration>(`${basis}/datev/konfiguration`, schreiben('PUT', config)),
    pruefeDatev: (request: ExportRequest) => json<Vorpruefung>(`${basis}/datev/vorpruefung`, schreiben('POST', request)),
    exportiereDatev: async (request: ExportRequest): Promise<{ blob: Blob; dateiname: string }> => {
        const response = await antwort(`${basis}/datev/export`, schreiben('POST', request));
        const disposition = response.headers.get('Content-Disposition') ?? '';
        const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
        const name = encoded ? decodeURIComponent(encoded) : /filename="?([^";]+)"?/i.exec(disposition)?.[1];
        if (!name || !/^lodas-\d{4}-\d{2}(?:-bis-\d{4}-\d{2})?\.(txt|zip)$/.test(name)) throw new Error('Der Server hat keinen gültigen DATEV-Dateinamen geliefert.');
        return { blob: await response.blob(), dateiname: name };
    },
};
