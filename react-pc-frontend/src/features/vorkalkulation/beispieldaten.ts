/**
 * Beispieldaten fuer den Click-Dummy.
 *
 * Die Werte stammen aus der Excel-Mappe des Betriebs, enthalten aber nur
 * Material und Arbeitsgaenge — keine Kunden-, Mitarbeiter- oder Zeitdaten
 * echter Personen (DSGVO). Wo ein Name noetig ist, steht ein Dummy-Name.
 *
 * Zweck: Der Dummy ist ohne laufendes Backend vollstaendig bedienbar und
 * zeigt sofort eine durchgerechnete Kalkulation, statt mit leeren Listen
 * anzufangen.
 */
import type {
    ArbeitszeitPosition,
    MaterialPosition,
    VorkalkulationDaten,
    VorkalkulationEintrag,
} from './types';

/** Laufende ID fuer neue Zeilen — im Dummy reicht ein Zaehler. */
let zaehler = 0;
export function neueId(praefix: string): string {
    zaehler += 1;
    return `${praefix}-${zaehler}`;
}

/**
 * Arbeitsgaenge als Rueckfallebene, wenn `/api/arbeitsgaenge` nicht erreichbar
 * ist. Die Nummern sind die Schritt-Schluessel aus der Zeit-Kalkulation.
 */
export const BEISPIEL_ARBEITSGAENGE: { id: number; beschreibung: string; stundensatz: number }[] = [
    { id: 600, beschreibung: 'Meister', stundensatz: 55 },
    { id: 601, beschreibung: 'Konstrukteur', stundensatz: 55 },
    { id: 1, beschreibung: 'Anfertigung', stundensatz: 50 },
    { id: 400, beschreibung: 'Richt- und Rüstzeit', stundensatz: 50 },
    { id: 401, beschreibung: 'Montage', stundensatz: 50 },
    { id: 402, beschreibung: 'Fahrzeit', stundensatz: 50 },
    { id: 300, beschreibung: 'Sonstiges', stundensatz: 50 },
    { id: 2, beschreibung: 'Puffer', stundensatz: 50 },
];

export function neueMaterialzeile(werte: Partial<MaterialPosition> = {}): MaterialPosition {
    return {
        id: neueId('mat'),
        artikelId: null,
        artikelnummer: '',
        bezeichnung: '',
        menge: '',
        einheit: 'STUECK',
        preis: '',
        preisbezug: 'EINHEIT',
        kgJeEinheit: '',
        qmJeEinheit: '',
        verzinkbar: false,
        pulverbeschichtbar: false,
        verzinken: false,
        pulverbeschichten: false,
        verzinkungsart: 'SCHLOSSERWARE',
        beschaffung: 'LAGER',
        ...werte,
    };
}

export function neueZeitzeile(werte: Partial<ArbeitszeitPosition> = {}): ArbeitszeitPosition {
    return {
        id: neueId('zeit'),
        arbeitsgangId: null,
        arbeitsgang: '',
        beschreibung: '',
        stunden: '',
        stundensatz: '',
        art: 'HAND',
        ...werte,
    };
}

/** Eine frische, leere Kalkulation mit den ueblichen Zuschlagssaetzen. */
export function leereKalkulation(): VorkalkulationDaten {
    return {
        material: [],
        gkzMaterialProzent: '20',
        arbeitszeit: [],
        gkzHandProzent: '0',
        gkzMaschineProzent: '0',
        zusatzkosten: [],
        verzinkenSchlosserwareJeKg: '1,48',
        verzinkenTraegerwareJeKg: '0,80',
        verzinkenFracht: '50,00',
        feinverputzen: false,
        feinverputzenAufschlagProzent: '55',
        pulverbeschichtenJeQm: '',
        verwaltungVertriebProzent: '3,5',
        wagnisGewinnProzent: '5',
        skontoProzent: '0',
    };
}

/**
 * Die durchgerechnete Beispiel-Kalkulation.
 *
 * Material: die beiden belegten Zeilen der Material-Kalkulation — sie zeigen
 * den Weg Meter → Gewicht → Kilo-Preis. Arbeitszeit: die sechs Zeilen der
 * Zeit-Kalkulation, die zusammen exakt die 5.584,90 EUR Lohnkosten der
 * Gesamt-Kalkulation ergeben.
 */
export function beispielKalkulation(): VorkalkulationDaten {
    return {
        ...leereKalkulation(),
        material: [
            neueMaterialzeile({
                artikelnummer: 'FL-100-10',
                bezeichnung: 'Normalflachstahl Fl 100x10',
                menge: '1,872',
                einheit: 'METER',
                preis: '1,20',
                preisbezug: 'KILOGRAMM',
                kgJeEinheit: '7,85',
                qmJeEinheit: '0,22',
                verzinkbar: true,
                pulverbeschichtbar: true,
                verzinken: true,
                verzinkungsart: 'SCHLOSSERWARE',
            }),
            neueMaterialzeile({
                artikelnummer: 'HQ-50-50-3',
                bezeichnung: 'Quadratrohr HQ 50x50x3',
                menge: '18,3888',
                einheit: 'METER',
                preis: '1,20',
                preisbezug: 'KILOGRAMM',
                kgJeEinheit: '4,25',
                qmJeEinheit: '0,20',
                verzinkbar: true,
                pulverbeschichtbar: true,
                verzinken: true,
                verzinkungsart: 'TRAEGERWARE',
            }),
        ],
        arbeitszeit: [
            neueZeitzeile({ arbeitsgangId: 600, arbeitsgang: 'Meister', beschreibung: 'Aufmaß + Angebot', stunden: '3,7', stundensatz: '55' }),
            neueZeitzeile({ arbeitsgangId: 601, arbeitsgang: 'Konstrukteur', beschreibung: 'Zeichnung Planung', stunden: '12,78', stundensatz: '55' }),
            neueZeitzeile({ arbeitsgangId: 1, arbeitsgang: 'Anfertigung', beschreibung: 'Anfertigung', stunden: '47,4', stundensatz: '50' }),
            neueZeitzeile({ arbeitsgangId: 402, arbeitsgang: 'Fahrzeit', beschreibung: 'Fahrzeit', stunden: '8,52', stundensatz: '50' }),
            neueZeitzeile({ arbeitsgangId: 401, arbeitsgang: 'Montage', beschreibung: 'Montage', stunden: '28,65', stundensatz: '50' }),
            neueZeitzeile({ arbeitsgangId: 2, arbeitsgang: 'Puffer', beschreibung: 'Puffer', stunden: '9', stundensatz: '50' }),
        ],
        zusatzkosten: [
            { id: neueId('zus'), bezeichnung: 'Materiallieferung', betrag: '70,00' },
            { id: neueId('zus'), bezeichnung: 'Sprit', betrag: '30,00' },
            { id: neueId('zus'), bezeichnung: 'Werkstoffzeugnisse', betrag: '' },
        ],
        feinverputzen: true,
    };
}

/** Beispielzeilen fuer den Reiter "Vor-Kalkulation". Nur Dummy-Namen. */
export const BEISPIEL_EINTRAEGE: VorkalkulationEintrag[] = [
    {
        id: 'vk-1',
        dokument: 'Angebot 2026-0148',
        position: '1',
        titel: 'Stahltreppe außen, feuerverzinkt',
        herstellkosten: 10233.73,
        verkaufspreis: 11121.51,
        geaendertAm: '16.09.2026',
        herkunft: 'ANFRAGE',
    },
    {
        id: 'vk-2',
        dokument: 'Angebot 2026-0148',
        position: '2',
        titel: 'Geländer Podest, 4,20 m',
        herstellkosten: 2184.5,
        verkaufspreis: 2374.2,
        geaendertAm: '16.09.2026',
        herkunft: 'ANFRAGE',
    },
    {
        id: 'vk-3',
        dokument: 'Angebot 2026-0131',
        position: '1',
        titel: 'Vordach Eingang, pulverbeschichtet',
        herstellkosten: 5940.0,
        verkaufspreis: 6455.7,
        geaendertAm: '02.09.2026',
        herkunft: 'PROJEKT',
    },
];
