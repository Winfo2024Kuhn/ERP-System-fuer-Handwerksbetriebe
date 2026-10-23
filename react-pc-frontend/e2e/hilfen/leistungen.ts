import type { Page, Request, Route } from '@playwright/test';
import { stubbeZeiterfassungSichtbarkeit } from './api';

/**
 * Gestubbtes Backend für die Seite „Leistungen & Kategorien“ (/leistungen).
 *
 * Hält einen kleinen Bestand im Speicher und verhält sich wie der API-Vertrag
 * (Anlegen mit leistungenMitnehmen, Verschieben, Löschprüfung, Löschen mit
 * Umziehen, Auswertung). Alle Namen sind Dummy-Daten (DSGVO).
 */

export interface OrdnerStub {
    id: number;
    bezeichnung: string;
    parentId: number | null;
    einheit: 'LAUFENDE_METER' | 'QUADRATMETER' | 'KILOGRAMM' | 'STUECK';
    beschreibung: string;
    bildUrl: string | null;
    projektAnzahl: number;
}

export interface LeistungStub {
    id: number;
    name: string;
    description: string;
    price: number;
    unit: string;
    folderId: number;
}

export interface LeistungenMitschrift {
    ordnerAngelegt: Record<string, string>[];
    ordnerGeaendert: { id: number; felder: Record<string, string> }[];
    verschoben: { id: number; parentId: number | null }[];
    loeschen: { id: number; body: Record<string, unknown> }[];
    geloescht: string[];
    leistungAngelegt: Record<string, unknown>[];
    leistungGeaendert: Record<string, unknown>[];
}

export interface LeistungenStand {
    ordner: OrdnerStub[];
    leistungen: LeistungStub[];
    mitschrift: LeistungenMitschrift;
}

const ANZEIGE: Record<OrdnerStub['einheit'], string> = {
    LAUFENDE_METER: 'Laufende Meter', QUADRATMETER: 'Quadratmeter', KILOGRAMM: 'Kilogramm', STUECK: 'Stück',
};

/** 1×1-PNG für Ordnerbilder. */
const PIXEL = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');

export function grundbestand(): Pick<LeistungenStand, 'ordner' | 'leistungen'> {
    return {
        ordner: [
            { id: 1, bezeichnung: 'Metallbau', parentId: null, einheit: 'LAUFENDE_METER', beschreibung: '<p>Ausführung durch unseren Meisterbetrieb.</p>', bildUrl: null, projektAnzahl: 5 },
            { id: 2, bezeichnung: 'Geländer', parentId: 1, einheit: 'LAUFENDE_METER', beschreibung: '<p>Geländer nach DIN 18065.</p>', bildUrl: null, projektAnzahl: 3 },
            { id: 3, bezeichnung: 'Treppen', parentId: 1, einheit: 'LAUFENDE_METER', beschreibung: '', bildUrl: null, projektAnzahl: 2 },
            { id: 4, bezeichnung: 'Holzbau', parentId: null, einheit: 'QUADRATMETER', beschreibung: '', bildUrl: null, projektAnzahl: 0 },
        ],
        leistungen: [
            { id: 10, name: 'Geländer Edelstahl rund', description: '<p>Rundrohr 42,4 mm</p>', price: 189.5, unit: 'LAUFENDE_METER', folderId: 2 },
            { id: 11, name: 'Geländer verzinkt', description: '<p>Flachstahl, feuerverzinkt</p>', price: 149, unit: 'LAUFENDE_METER', folderId: 2 },
            { id: 12, name: 'Handlauf Eiche', description: '<p>Eiche geölt</p>', price: 89, unit: 'LAUFENDE_METER', folderId: 2 },
            { id: 13, name: 'Glasbrüstung', description: '<p>VSG 16 mm</p>', price: 420, unit: 'LAUFENDE_METER', folderId: 2 },
            { id: 14, name: 'Wangentreppe', description: '<p>Stahlwangen mit Holzstufen</p>', price: 4800, unit: 'STUECK', folderId: 3 },
        ],
    };
}

export const ANALYSE = {
    projektAnzahl: 5, durchschnittlicheZeit: 12.3, fixzeit: 10.91, steigung: 1.41, verrechnungseinheit: 'Laufende Meter',
    rQuadrat: 0.85, residualStdAbweichung: 4.8, datenpunkte: 3,
    arbeitsgangAnalysen: [
        { arbeitsgangId: 1, arbeitsgangBeschreibung: 'Montage', durchschnittStundenProEinheit: 0.47 },
        { arbeitsgangId: 2, arbeitsgangBeschreibung: 'Anfertigung', durchschnittStundenProEinheit: 0.37 },
    ],
    projekte: [
        { id: 1, auftragsnummer: '2026-011', kunde: 'Max Mustermann', masseinheit: 11.5, zeitGesamt: 27.4, arbeitsgaenge: [] },
        { id: 2, auftragsnummer: '2026-014', kunde: 'Erika Mustermann', masseinheit: 4, zeitGesamt: 16.2, arbeitsgaenge: [] },
        { id: 3, auftragsnummer: '2026-019', kunde: 'Max Mustermann', masseinheit: 8, zeitGesamt: 22.9, arbeitsgaenge: [] },
    ],
};

/** Auswertungszahlen je Ordner (Rest: 0). */
const AUSWERTUNG: Record<number, { projektAnzahl: number; stunden: number; menge: number; stundenProEinheit: number | null }> = {
    1: { projektAnzahl: 14, stunden: 312.5, menge: 180, stundenProEinheit: 1.74 },
    2: { projektAnzahl: 12, stunden: 250, menge: 150, stundenProEinheit: 1.67 },
    3: { projektAnzahl: 2, stunden: 62.5, menge: 30, stundenProEinheit: 2.08 },
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

/** Textfelder einer multipart-Anfrage; Dateien erscheinen als `<datei>`. */
export function multipartFelder(request: Request): Record<string, string> {
    const typ = request.headers()['content-type'] ?? '';
    const grenze = typ.split('boundary=')[1];
    const roh = request.postDataBuffer()?.toString('utf8') ?? '';
    const felder: Record<string, string> = {};
    if (!grenze) return felder;
    for (const teil of roh.split(`--${grenze}`)) {
        const kopf = /name="([^"]+)"(; filename="[^"]*")?/.exec(teil);
        if (!kopf) continue;
        const start = teil.indexOf('\r\n\r\n') + 4;
        felder[kopf[1]] = kopf[2] ? '<datei>' : teil.slice(start, teil.lastIndexOf('\r\n'));
    }
    return felder;
}

export interface LeistungenStubOptionen {
    /** Antwort von GET /api/leistungen/{id}/verwendung je Leistung (Standard: in keinem Dokument). */
    verwendung?: Record<number, { dokumentAnzahl: number; dokumente: unknown[] }>;
}

export async function stubbeLeistungenApi(page: Page, bestand = grundbestand(), optionen: LeistungenStubOptionen = {}): Promise<LeistungenStand> {
    const stand: LeistungenStand = {
        ordner: bestand.ordner.map((o) => ({ ...o })),
        leistungen: bestand.leistungen.map((l) => ({ ...l })),
        mitschrift: { ordnerAngelegt: [], ordnerGeaendert: [], verschoben: [], loeschen: [], geloescht: [], leistungAngelegt: [], leistungGeaendert: [] },
    };
    let naechsteId = 100;

    const kinder = (id: number) => stand.ordner.filter((o) => o.parentId === id);
    const nachfahren = (id: number): number[] => kinder(id).flatMap((k) => [k.id, ...nachfahren(k.id)]);
    const dto = (o: OrdnerStub) => ({
        id: o.id, bezeichnung: o.bezeichnung, bildUrl: o.bildUrl, beschreibung: o.beschreibung,
        verrechnungseinheit: { name: o.einheit, anzeigename: ANZEIGE[o.einheit] }, pfad: o.bezeichnung,
        leaf: kinder(o.id).length === 0, projektAnzahl: o.projektAnzahl, parentId: o.parentId,
    });
    const auswertungZeile = (o: OrdnerStub) => {
        const ids = [o.id, ...nachfahren(o.id)];
        const werte = AUSWERTUNG[o.id] ?? { projektAnzahl: 0, stunden: 0, menge: 0, stundenProEinheit: null };
        return {
            kategorieId: o.id, bezeichnung: o.bezeichnung, bildUrl: o.bildUrl, verrechnungseinheit: ANZEIGE[o.einheit],
            leaf: kinder(o.id).length === 0, ...werte, leistungAnzahl: stand.leistungen.filter((l) => ids.includes(l.folderId)).length,
        };
    };

    // Auffangnetz zuerst – die zuletzt registrierte Route gewinnt.
    await page.route('**/api/**', (route) => json(route, []));
    await page.route('**/api/auth/me', (route) => json(route, {
        id: 1, username: 'chef', displayName: 'Max Mustermann', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
    }));
    await page.route('**/api/notifications/summary', (route) => json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await stubbeZeiterfassungSichtbarkeit(page);
    await page.route('**/bilder/ordner-*.png', (route) => route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL }));

    await page.route(/\/api\/produktkategorien(\/.*)?(\?.*)?$/, async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname.replace(/^\/api\/produktkategorien/, '');
        const methode = request.method();
        const id = Number(pfad.split('/')[1]);
        const ordner = stand.ordner.find((o) => o.id === id);

        if (pfad === '' && methode === 'GET') return json(route, stand.ordner.map(dto));
        if (pfad === '/auswertung') {
            const parent = url.searchParams.get('parentId');
            const liste = stand.ordner.filter((o) => o.parentId === (parent ? Number(parent) : null)).map(auswertungZeile);
            return json(route, liste.sort((a, b) => b.stunden - a.stunden || a.bezeichnung.localeCompare(b.bezeichnung)));
        }
        if (pfad === '' && methode === 'POST') {
            const felder = multipartFelder(request);
            stand.mitschrift.ordnerAngelegt.push(felder);
            if (!felder.bezeichnung?.trim()) return json(route, { status: 400, message: 'Bitte geben Sie dem Ordner einen Namen.' }, 400);
            const parentId = felder.parentId ? Number(felder.parentId) : null;
            const eltern = stand.ordner.find((o) => o.id === parentId);
            const direkte = stand.leistungen.filter((l) => l.folderId === parentId);
            if (eltern && direkte.length && felder.leistungenMitnehmen !== 'true') {
                return json(route, { status: 400, message: `In „${eltern.bezeichnung}“ liegen ${direkte.length} Leistungen. Ein Unterordner geht nur, wenn sie mit in den neuen Ordner wandern.` }, 400);
            }
            const neu: OrdnerStub = {
                id: naechsteId++, bezeichnung: felder.bezeichnung.trim(), parentId, einheit: eltern?.einheit ?? 'STUECK',
                beschreibung: felder.beschreibung ?? '', bildUrl: null, projektAnzahl: 0,
            };
            stand.ordner.push(neu);
            if (felder.leistungenMitnehmen === 'true') direkte.forEach((l) => { l.folderId = neu.id; });
            return json(route, dto(neu), 201);
        }
        if (!ordner) return json(route, { status: 404, message: 'Der Ordner existiert nicht mehr.' }, 404);

        if (pfad === `/${id}` && methode === 'PUT') {
            const felder = multipartFelder(request);
            stand.mitschrift.ordnerGeaendert.push({ id, felder });
            ordner.bezeichnung = felder.bezeichnung ?? ordner.bezeichnung;
            if (felder.verrechnungseinheit) ordner.einheit = felder.verrechnungseinheit as OrdnerStub['einheit'];
            ordner.beschreibung = felder.beschreibung ?? '';
            if (felder.bild) ordner.bildUrl = `/bilder/ordner-${id}.png`;
            if (felder.bildEntfernen === 'true') ordner.bildUrl = null;
            return json(route, dto(ordner));
        }
        if (pfad === `/${id}` && methode === 'DELETE') {
            stand.mitschrift.geloescht.push(url.pathname);
            stand.ordner = stand.ordner.filter((o) => o.id !== id);
            return route.fulfill({ status: 204 });
        }
        if (pfad === `/${id}/verschieben`) {
            const { parentId } = request.postDataJSON() as { parentId: number | null };
            stand.mitschrift.verschoben.push({ id, parentId });
            ordner.parentId = parentId;
            return json(route, dto(ordner));
        }
        if (pfad === `/${id}/loeschpruefung`) {
            const ids = [id, ...nachfahren(id)];
            const unterordner = stand.ordner.filter((o) => ids.slice(1).includes(o.id));
            const leistungen = stand.leistungen.filter((l) => ids.includes(l.folderId));
            const projekte = ordner.projektAnzahl > 0
                ? [{ id: 9, bauvorhaben: 'Neubau Mustermann', auftragsnummer: '2026-014', kunde: 'Max Mustermann' },
                    { id: 10, bauvorhaben: 'Umbau Musterstraße 5', auftragsnummer: null, kunde: 'Erika Mustermann' }].slice(0, ordner.projektAnzahl)
                : [];
            return json(route, {
                kategorieId: id, bezeichnung: ordner.bezeichnung, pfad: ordner.bezeichnung,
                hatBezuege: unterordner.length + leistungen.length + ordner.projektAnzahl > 0,
                unterordnerAnzahl: unterordner.length, leistungAnzahl: leistungen.length, projektAnzahl: ordner.projektAnzahl,
                zeitbuchungAnzahl: ordner.projektAnzahl * 12,
                unterordner: unterordner.map((u) => ({ id: u.id, bezeichnung: u.bezeichnung, pfad: u.bezeichnung })),
                leistungen: leistungen.map((l) => ({ id: l.id, name: l.name, ordnerId: l.folderId })),
                projekte,
            });
        }
        if (pfad === `/${id}/loeschen`) {
            const body = request.postDataJSON() as { modus: string; zielKategorieId?: number };
            stand.mitschrift.loeschen.push({ id, body });
            if (body.modus === 'UMZIEHEN') {
                const ziel = stand.ordner.find((o) => o.id === body.zielKategorieId);
                if (!ziel) return json(route, { status: 400, message: 'Bitte wählen Sie einen Ziel-Ordner.' }, 400);
                kinder(id).forEach((k) => { k.parentId = ziel.id; });
                stand.leistungen.filter((l) => l.folderId === id).forEach((l) => { l.folderId = ziel.id; });
                ziel.projektAnzahl += ordner.projektAnzahl;
                stand.ordner = stand.ordner.filter((o) => o.id !== id);
            } else {
                const ids = [id, ...nachfahren(id)];
                stand.ordner = stand.ordner.filter((o) => !ids.includes(o.id));
                stand.leistungen = stand.leistungen.filter((l) => !ids.includes(l.folderId));
            }
            return route.fulfill({ status: 204 });
        }
        if (pfad === `/${id}/analyse`) return json(route, ANALYSE);
        return json(route, []);
    });

    await page.route(/\/api\/leistungen(\/\d+)?(\/verwendung)?$/, async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const teile = url.pathname.split('/');
        const id = Number(teile[3]);
        const methode = request.method();
        if (url.pathname.endsWith('/verwendung')) return json(route, optionen.verwendung?.[id] ?? { dokumentAnzahl: 0, dokumente: [] });
        if (!teile[3] && methode === 'GET') return json(route, stand.leistungen);
        if (!teile[3] && methode === 'POST') {
            const body = request.postDataJSON() as Omit<LeistungStub, 'id'>;
            stand.mitschrift.leistungAngelegt.push(body);
            const neu = { ...body, id: naechsteId++ };
            stand.leistungen.push(neu);
            return json(route, neu);
        }
        const leistung = stand.leistungen.find((l) => l.id === id);
        if (!leistung) return json(route, { status: 404, message: 'Die Leistung gibt es nicht mehr.' }, 404);
        if (methode === 'PUT') {
            const body = request.postDataJSON() as Omit<LeistungStub, 'id'>;
            stand.mitschrift.leistungGeaendert.push(body);
            Object.assign(leistung, body);
            return json(route, leistung);
        }
        if (methode === 'DELETE') {
            stand.mitschrift.geloescht.push(url.pathname);
            stand.leistungen = stand.leistungen.filter((l) => l.id !== id);
            return route.fulfill({ status: 204 });
        }
        return json(route, leistung);
    });

    return stand;
}
