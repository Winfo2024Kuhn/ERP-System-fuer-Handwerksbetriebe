import { leseFehlermeldung } from '../../lib/apiFehler';

/**
 * Alle Aufrufe der IDS-Schnittstelle (Warenkorb aus dem Lieferanten-Shop) an
 * einer Stelle – mit den Typen genau nach dem API-Vertrag.
 *
 * <p>Fachneutral: Hier steht nichts über Dokumente. Der Dokumenteditor nutzt
 * diese Aufrufe heute, ein späteres Beschaffungsmodul kann sie genauso nutzen.</p>
 *
 * <p>Fehler kommen als {@link IdsApiFehler} mit dem deutschen Satz des Servers
 * (`{ status, message }`) heraus, damit die Oberfläche ihn direkt zeigen kann.</p>
 */

const BASIS = '/api/ids';

/**
 * Die Verfahren, die der Server kennt. Der Standard schickt zusätzlich
 * action=WKE und version=2.5 mit; manche Shops (laut Zugangsblatt z. B.
 * Würth) erwarten nur kndnr, name_kunde, pw_kunde und hookurl.
 */
export type IdsProtokoll = 'IDS_CONNECT_2_5' | 'IDS_NUR_ZUGANGSFELDER';

export interface IdsProtokollOption {
    wert: IdsProtokoll;
    text: string;
}

export const IDS_PROTOKOLLE: IdsProtokollOption[] = [
    { wert: 'IDS_CONNECT_2_5', text: 'IDS-Connect 2.5 (Standard)' },
    { wert: 'IDS_NUR_ZUGANGSFELDER', text: 'Nur Zugangsfelder (wie Würth-Zugangsblatt)' },
];

// ---------- Einrichtung (nur Admin) ----------

export interface IdsZugang {
    lieferantId: number;
    lieferantName: string;
    aktiv: boolean;
    protokoll: IdsProtokoll;
    shopUrl: string;
    kundennummer: string | null;
    benutzername: string | null;
    passwortGesetzt: boolean;
    /** ISO-Datum-Zeit der letzten Änderung. */
    geaendertAm: string | null;
}

export interface IdsEinstellungen {
    aktiv: boolean;
    verschluesselungEingerichtet: boolean;
    zugaenge: IdsZugang[];
}

export interface IdsZugangSpeichern {
    aktiv: boolean;
    protokoll: IdsProtokoll;
    shopUrl: string;
    kundennummer: string | null;
    benutzername: string | null;
    /** `null` oder leer = bisheriges Passwort bleibt. */
    passwort: string | null;
}

// ---------- Benutzung ----------

export interface IdsLieferant {
    lieferantId: number;
    lieferantName: string;
    /**
     * Ist für diesen Shop-Zugang ein Passwort gespeichert? Ohne lehnt der Shop
     * die Anmeldung meist ab – der Dialog warnt dann vorher.
     */
    passwortGesetzt: boolean;
}

export interface IdsVerfuegbarkeit {
    aktiv: boolean;
    lieferanten: IdsLieferant[];
}

/**
 * Das Anmeldeformular für den Shop. Enthält das Shop-Passwort im Klartext –
 * nie loggen, nie im Zustand halten, nie in Fehlermeldungen übernehmen.
 */
export interface IdsShopFormular {
    action: string;
    enctype: string;
    felder: Record<string, string>;
}

export interface IdsVorgangStart {
    vorgangId: number;
    formular: IdsShopFormular;
}

export type IdsVorgangStatus = 'WARTET' | 'EMPFANGEN' | 'ABGELAUFEN' | 'FEHLER';

export type IdsAufschlagQuelle = 'ARTIKEL' | 'KATEGORIE' | 'ALLGEMEIN' | 'KEINER';

export interface IdsStammArtikel {
    artikelId: number;
    bezeichnung: string;
    aktuellerPreis: number | null;
}

export interface IdsPosition {
    index: number;
    artikelnummer: string;
    bezeichnung: string;
    langtext: string | null;
    menge: number;
    /** Schon deutsch, z. B. „Stk“, „m“, „kg“. */
    einheit: string;
    /** Netto je 1 Einheit; `null`, wenn der Shop keinen Preis geschickt hat. */
    einkaufspreis: number | null;
    /** Hinweis des Shops – muss angezeigt werden. */
    hinweis: string | null;
    aufschlagProzent: number | null;
    aufschlagQuelle: IdsAufschlagQuelle;
    stamm: IdsStammArtikel | null;
}

export interface IdsVorgang {
    vorgangId: number;
    lieferantId: number;
    lieferantName: string;
    status: IdsVorgangStatus;
    fehlertext: string | null;
    mitBestellung: boolean;
    empfangenAm: string | null;
    standardAufschlagProzent: number | null;
    positionen: IdsPosition[];
}

export interface IdsArtikelstammAuftrag {
    aktualisieren: { index: number; einkaufspreis: number }[];
    neuAnlegen: { index: number; einkaufspreis: number | null; bezeichnung: string }[];
}

export interface IdsArtikelstammErgebnis {
    aktualisiert: number;
    angelegt: number;
    unveraendert: number;
    artikelIds: Record<string, number>;
}

/** Fehler mit HTTP-Status – z. B. um 404 („Vorgang unbekannt“) zu erkennen. */
export class IdsApiFehler extends Error {
    readonly status: number;

    constructor(status: number, message: string) {
        super(message);
        this.name = 'IdsApiFehler';
        this.status = status;
    }
}

async function anfrage(url: string, ersatz: string, init?: RequestInit): Promise<Response> {
    const res = await fetch(url, init);
    if (!res.ok) {
        throw new IdsApiFehler(res.status, await leseFehlermeldung(res, ersatz));
    }
    return res;
}

async function holeJson<T>(url: string, ersatz: string, init?: RequestInit): Promise<T> {
    const res = await anfrage(url, ersatz, init);
    return res.json() as Promise<T>;
}

function jsonInit(method: string, body: unknown): RequestInit {
    return {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    };
}

export function ladeIdsEinstellungen(): Promise<IdsEinstellungen> {
    return holeJson(`${BASIS}/einstellungen`, 'Die Einstellungen der Lieferanten-Shops konnten nicht geladen werden.');
}

export function speichereIdsGrundschalter(aktiv: boolean): Promise<IdsEinstellungen> {
    return holeJson(`${BASIS}/einstellungen`, 'Der Schalter für die Lieferanten-Shops konnte nicht gespeichert werden.',
        jsonInit('PUT', { aktiv }));
}

export function speichereIdsZugang(lieferantId: number, daten: IdsZugangSpeichern): Promise<IdsZugang> {
    return holeJson(`${BASIS}/einstellungen/zugaenge/${encodeURIComponent(lieferantId)}`,
        'Der Shop-Zugang konnte nicht gespeichert werden.', jsonInit('PUT', daten));
}

export async function entferneIdsZugang(lieferantId: number): Promise<void> {
    await anfrage(`${BASIS}/einstellungen/zugaenge/${encodeURIComponent(lieferantId)}`,
        'Der Shop-Zugang konnte nicht entfernt werden.', { method: 'DELETE' });
}

/**
 * Welche Lieferanten-Shops stehen gerade zur Verfügung? Liefert bei einer
 * unerwarteten Antwort lieber „nichts verfügbar“ als einen halben Knopf.
 */
export async function ladeIdsVerfuegbar(signal?: AbortSignal): Promise<IdsVerfuegbarkeit> {
    const daten: unknown = await holeJson(`${BASIS}/verfuegbar`, 'Die Lieferanten-Shops konnten nicht geladen werden.', { signal });
    if (!daten || typeof daten !== 'object') return { aktiv: false, lieferanten: [] };
    const { aktiv, lieferanten } = daten as Partial<IdsVerfuegbarkeit>;
    if (aktiv !== true || !Array.isArray(lieferanten)) return { aktiv: false, lieferanten: [] };
    return { aktiv: true, lieferanten };
}

export function starteIdsVorgang(lieferantId: number): Promise<IdsVorgangStart> {
    return holeJson(`${BASIS}/vorgaenge`, 'Der Shop konnte nicht geöffnet werden.', jsonInit('POST', { lieferantId }));
}

export function ladeIdsVorgang(vorgangId: number): Promise<IdsVorgang> {
    return holeJson(`${BASIS}/vorgaenge/${encodeURIComponent(vorgangId)}`, 'Der Warenkorb konnte nicht abgefragt werden.');
}

export function uebernimmIdsArtikelstamm(vorgangId: number, auftrag: IdsArtikelstammAuftrag): Promise<IdsArtikelstammErgebnis> {
    return holeJson(`${BASIS}/vorgaenge/${encodeURIComponent(vorgangId)}/artikelstamm`,
        'Der Artikelstamm konnte nicht aktualisiert werden.', jsonInit('POST', auftrag));
}
