import { parseErrorMessage } from '../../settingsApi';
import type { Konfiguration } from '../../../../features/monatsabschluss/types';

/**
 * Schnittstelle des Reiters Einstellungen → Zeiterfassung.
 *
 * <p>Alle Endpunkte sind nur für Admins. Fehler kommen als `{ message }` in
 * Handwerker-Sprache und werden unverändert angezeigt.</p>
 */

/** Ab wann der Betrieb Zeiten in diesem Programm erfasst. `null` = keine Grenze. */
export interface ZeiterfassungStart {
    eingefuehrtAm: string | null;
}

/** Verfällt übertragener Resturlaub – und an welchem Tag im Folgejahr? Voreinstellung 31.03. */
export interface UrlaubsverfallRegel {
    verfaellt: boolean;
    tag: number;
    monat: number;
}

/** Mitarbeiter für die Personalnummern-Tabelle. */
export interface MitarbeiterEintrag {
    id: number;
    name: string;
    aktiv: boolean;
}

const VERBINDUNG = 'Verbindung zum Server fehlgeschlagen.';
export const DATEV_KONFLIKT = 'Die DATEV-Einstellungen wurden geändert. Bitte neu laden.';

/** Fehler mit HTTP-Status, damit die Karte einen Versionskonflikt (409) erkennt. */
export class EinstellungenFehler extends Error {
    readonly status: number;

    constructor(message: string, status: number) {
        super(message);
        this.status = status;
    }
}

async function anfrage<T>(url: string, fallback: string, init?: RequestInit): Promise<T> {
    let res: Response;
    try {
        res = await fetch(url, init);
    } catch {
        throw new EinstellungenFehler(VERBINDUNG, 0);
    }
    if (!res.ok) {
        const ersatz = res.status === 409 ? DATEV_KONFLIKT
            : res.status === 403 ? 'Diese Einstellungen dürfen nur Admins ändern.'
                : fallback;
        throw new EinstellungenFehler(await parseErrorMessage(res, ersatz), res.status);
    }
    return res.json() as Promise<T>;
}

function schreiben(body: unknown): RequestInit {
    return { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

export const zeiterfassungApi = {
    ladeStart: () =>
        anfrage<ZeiterfassungStart>('/api/settings/zeiterfassung', 'Der Start der Zeiterfassung konnte nicht geladen werden.'),
    speichereStart: (eingefuehrtAm: string | null) =>
        anfrage<ZeiterfassungStart>('/api/settings/zeiterfassung', 'Der Start der Zeiterfassung konnte nicht gespeichert werden.', schreiben({ eingefuehrtAm })),
    ladeUrlaubsverfall: () =>
        anfrage<UrlaubsverfallRegel>('/api/settings/urlaubsverfall', 'Die Regel für Resturlaub konnte nicht geladen werden.'),
    speichereUrlaubsverfall: (regel: UrlaubsverfallRegel) =>
        anfrage<UrlaubsverfallRegel>('/api/settings/urlaubsverfall', 'Die Regel für Resturlaub konnte nicht gespeichert werden.', schreiben(regel)),
    ladeDatev: () =>
        anfrage<Konfiguration>('/api/settings/datev', 'Die DATEV-Einstellungen konnten nicht geladen werden.'),
    speichereDatev: (konfiguration: Konfiguration) =>
        anfrage<Konfiguration>('/api/settings/datev', 'Die DATEV-Einstellungen konnten nicht gespeichert werden.', schreiben(konfiguration)),
    /** Wie im Monatsabschluss: alle Mitarbeiter, Name aus Vor- und Nachname. Deaktivierte stehen am Ende. */
    ladeMitarbeiter: async (): Promise<MitarbeiterEintrag[]> => {
        const liste = await anfrage<{ id: number; vorname: string; nachname: string; aktiv?: boolean | null }[]>(
            '/api/mitarbeiter', 'Die Mitarbeiter konnten nicht geladen werden.');
        return liste
            .map(m => ({ id: m.id, name: `${m.vorname} ${m.nachname}`.trim(), aktiv: m.aktiv !== false }))
            .sort((a, b) => Number(b.aktiv) - Number(a.aktiv));
    },
};
