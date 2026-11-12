import type { Page, Route } from '@playwright/test';
import { stubbeZeiterfassungSichtbarkeit } from './api';
import { multipartFelder } from './leistungen';

/**
 * Gestubbtes Backend für die Seite „Firmendokumente“ (/firmendokumente),
 * nach dem API-Vertrag `docs/firmendokumente-api.md`. Hält Ordner und
 * Dokumente im Speicher. Angemeldet ist ein normaler Nutzer (kein Admin),
 * außer `optionen.admin` ist gesetzt. Alle Namen sind Dummy-Daten (DSGVO).
 */

export interface OrdnerStub {
    id: number; name: string; parentId: number | null;
    /** Nur an obersten Ordnern: für bestimmte Abteilungen eingeschränkt (Unterordner erben). */
    eingeschraenkt?: boolean;
    /** Darf der Angemeldete hier ändern? Standard: wie der oberste Ordner, sonst `true`. */
    darfAendern?: boolean;
}

export type RechteStufe = 'KEINE' | 'ANSEHEN' | 'AENDERN';
export interface AbteilungStub { abteilungId: number; name: string }

export interface FirmendokumenteOptionen {
    /** Als Admin anmelden (sieht „Wer darf das sehen?“). */
    admin?: boolean;
    /** Alle Abteilungen der Firma (Dummy-Daten). */
    abteilungen?: AbteilungStub[];
}
export interface DokumentStub {
    id: number; ordnerId: number; name: string; originalDateiname: string; groesseBytes: number;
    hochgeladenAm: string; hochgeladenVon: string | null; faelligAm: string | null;
}

export interface FirmendokumenteStand {
    ordner: OrdnerStub[];
    dokumente: DokumentStub[];
    hochgeladen: Record<string, string>[];
    /** Jede Änderung (PUT/DELETE) mit Pfad und Body. */
    aenderungen: { methode: string; pfad: string; body: unknown }[];
    /** Jeder Abruf einer Datei (Pfad mit Query). */
    dateiAbrufe: string[];
    /** Gespeicherte Abteilungs-Rechte je oberstem Ordner. */
    rechte: Record<number, Record<number, RechteStufe>>;
    /** Jeder Abruf eines Vorschaubilds (Pfad). */
    vorschaubildAbrufe: string[];
}

/** Ein winziges, gültiges PNG (1×1 Pixel, rose) für die Bildvorschau. */
const MINI_PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==', 'base64');

const BILD_ENDUNGEN = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'];

/** Dateiart wie im Vertrag: aus der Endung des Originalnamens. */
export function dateiartVon(dateiname: string): 'PDF' | 'BILD' | 'SONSTIGE' {
    const endung = dateiname.includes('.') ? dateiname.split('.').pop()?.toLowerCase() ?? '' : '';
    if (endung === 'pdf') return 'PDF';
    return BILD_ENDUNGEN.includes(endung) ? 'BILD' : 'SONSTIGE';
}

/** Vorschaubild eines Fotos (48 × 36, Himmel, Geländer, Boden) – statt eines echten JPEGs. */
const VORSCHAU_FOTO = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAADAAAAAkCAIAAABAJy5dAAAASUlEQVR42u3OQREAEAAAQe0kEEILITTQQg4hxBDAywszO3Ove23oYz5VAAICAgICAgICAvoMFFN+KqAvQa2WvVsfCAgICAjo8C/yRKi0qI1thgAAAABJRU5ErkJggg==', 'base64');
/** Vorschaubild einer PDF-Seite (48 × 64, weiß mit Zeilen). */
const VORSCHAU_SEITE = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAADAAAABACAIAAADTQmMRAAAAXUlEQVR42u3SQQkAIBBFQZN4N4M1bWAJ2+nVALoIzrIBBv5L87FLQEAfgHKpRx4oDCRqIKDboNZH2JsMSNQmAxK1hoBEDSRqUQOJWtRAojYZkKg1BCRqDYla1ED7LYOtr6i+rnp+AAAAAElFTkSuQmCC', 'base64');

/** Ein winziges, gültiges PDF für Vorschau und Druck. */
const MINI_PDF = Buffer.from(
    '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj '
    + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF',
);

/** Datum relativ zu heute als `yyyy-MM-dd` (lokale Zeit). */
export function tageAbHeute(tage: number): string {
    const d = new Date();
    d.setDate(d.getDate() + tage);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function grundbestand(): Pick<FirmendokumenteStand, 'ordner' | 'dokumente'> {
    return {
        ordner: [
            { id: 1, name: 'Prüfungen', parentId: null },
            { id: 2, name: 'Tore', parentId: 1 },
            { id: 3, name: 'Leitern', parentId: 1 },
            { id: 4, name: 'Arbeitsschutz', parentId: null },
            { id: 5, name: 'Versicherungen', parentId: null },
        ],
        dokumente: [
            { id: 10, ordnerId: 2, name: 'Torprüfung Halle 1 – 2025', originalDateiname: 'scan_0040.pdf', groesseBytes: 482_113, hochgeladenAm: '2025-10-20T09:14:00', hochgeladenVon: 'Max Mustermann', faelligAm: tageAbHeute(-10) },
            { id: 11, ordnerId: 3, name: 'Leiterprüfung 2026', originalDateiname: 'leitern.pdf', groesseBytes: 120_400, hochgeladenAm: '2026-03-02T08:00:00', hochgeladenVon: 'Max Mustermann', faelligAm: tageAbHeute(12) },
            { id: 12, ordnerId: 4, name: 'Gefährdungsbeurteilung Werkstatt', originalDateiname: 'gbu.pdf', groesseBytes: 2_400_000, hochgeladenAm: '2026-01-15T10:30:00', hochgeladenVon: null, faelligAm: null },
        ],
    };
}

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const nachName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name, 'de', { sensitivity: 'base' });

export async function stubbeFirmendokumenteApi(page: Page, bestand = grundbestand(), optionen: FirmendokumenteOptionen = {}): Promise<FirmendokumenteStand> {
    const stand: FirmendokumenteStand = {
        ordner: bestand.ordner.map((o) => ({ ...o })), dokumente: bestand.dokumente.map((d) => ({ ...d })), hochgeladen: [], aenderungen: [], dateiAbrufe: [], rechte: {}, vorschaubildAbrufe: [],
    };
    const abteilungen = optionen.abteilungen ?? [];
    let naechsteId = 100;
    const heute = tageAbHeute(0);
    const grenze = tageAbHeute(30);
    const oberster = (o: OrdnerStub): OrdnerStub => {
        let aktuell = o;
        for (let i = 0; aktuell.parentId !== null && i < 50; i += 1) {
            const eltern = stand.ordner.find((x) => x.id === aktuell.parentId);
            if (!eltern) break;
            aktuell = eltern;
        }
        return aktuell;
    };
    const darfAendern = (o: OrdnerStub | undefined) => !!o && (o.darfAendern ?? oberster(o).darfAendern ?? true);
    const ordnerVon = (id: number) => stand.ordner.find((o) => o.id === id);
    const dto = (d: DokumentStub) => ({
        ...d, url: `/api/firmendokumente/${d.id}/datei`, dateiart: dateiartVon(d.originalDateiname), darfAendern: darfAendern(ordnerVon(d.ordnerId)),
    });
    const seite = (liste: DokumentStub[], url: URL) => {
        const nr = Number(url.searchParams.get('page') ?? 0);
        const groesse = Math.min(50, Number(url.searchParams.get('size') ?? 50));
        return { eintraege: liste.slice(nr * groesse, (nr + 1) * groesse).map(dto), gesamt: liste.length, seite: nr, seitenGroesse: groesse };
    };
    const ordnerDto = (o: OrdnerStub) => {
        const darin = stand.dokumente.filter((d) => d.ordnerId === o.id);
        return {
            ...o, darfAendern: darfAendern(o), eingeschraenkt: !!oberster(o).eingeschraenkt, anzahlDokumente: darin.length,
            anzahlUeberfaellig: darin.filter((d) => d.faelligAm !== null && d.faelligAm < heute).length,
            anzahlBaldFaellig: darin.filter((d) => d.faelligAm !== null && d.faelligAm >= heute && d.faelligAm <= grenze).length,
        };
    };

    // Auffangnetz zuerst – die zuletzt registrierte Route gewinnt.
    await page.route('**/api/**', (route) => json(route, []));
    await page.route('**/api/auth/me', (route) => json(route, {
        id: 2, username: 'max', displayName: 'Max Mustermann', active: true, roles: optionen.admin ? ['ADMIN'] : ['USER'], admin: !!optionen.admin, requiresInitialSetup: false,
    }));
    await page.route('**/api/notifications/summary', (route) => json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await stubbeZeiterfassungSichtbarkeit(page);

    const nachfahren = (id: number): number[] => stand.ordner.filter((o) => o.parentId === id).flatMap((k) => [k.id, ...nachfahren(k.id)]);
    const rechteDto = (o: OrdnerStub) => ({
        ordnerId: o.id, ordnerName: o.name, eingeschraenkt: !!o.eingeschraenkt,
        abteilungen: [...abteilungen].sort(nachName).map((a) => ({ ...a, stufe: stand.rechte[o.id]?.[a.abteilungId] ?? 'KEINE' })),
    });
    const rechteSpeichern = (route: Route, id: number, body: { eingeschraenkt: boolean; abteilungen: { abteilungId: number; stufe: RechteStufe }[] }) => {
        const ordner = ordnerVon(id);
        if (!ordner) return json(route, { status: 404, message: 'Den Ordner gibt es nicht mehr.', fields: {} }, 404);
        ordner.eingeschraenkt = body.eingeschraenkt;
        stand.rechte[id] = Object.fromEntries(body.abteilungen.map((a) => [a.abteilungId, a.stufe]));
        return json(route, rechteDto(ordner));
    };
    /** Wie der Server: aus einem eingeschränkten Zweig nur Admins in einen anderen Hauptordner. */
    const zweigVerlassen = (vonOrdnerId: number, zielId: number | null) => {
        const von = ordnerVon(vonOrdnerId);
        const ziel = zielId === null ? undefined : ordnerVon(zielId);
        return !optionen.admin && !!von && !!oberster(von).eingeschraenkt && !!ziel && oberster(ziel).id !== oberster(von).id;
    };
    const zweigGesperrt = (route: Route) => json(route, { status: 403, message: 'Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.', fields: {} }, 403);
    const keinRecht = (route: Route) => json(route, { status: 403, message: 'Du darfst in diesem Ordner nichts ändern.', fields: {} }, 403);

    /** PUT/DELETE auf Ordner und Dokumente, mit den Fehlern aus dem API-Vertrag. */
    const aendern = (route: Route, pfad: string, methode: string) => {
        const body = methode === 'PUT' ? route.request().postDataJSON() : null;
        stand.aenderungen.push({ methode, pfad, body });
        const rechteTreffer = /^\/ordner\/(\d+)\/rechte$/.exec(pfad);
        if (rechteTreffer) return rechteSpeichern(route, Number(rechteTreffer[1]), body as { eingeschraenkt: boolean; abteilungen: { abteilungId: number; stufe: RechteStufe }[] });
        const ordnerTreffer = /^\/ordner\/(\d+)(\/verschieben)?$/.exec(pfad);
        if (ordnerTreffer) {
            const id = Number(ordnerTreffer[1]);
            const ordner = stand.ordner.find((o) => o.id === id);
            if (!ordner) return json(route, { status: 404, message: 'Den Ordner gibt es nicht mehr.', fields: {} }, 404);
            if (methode === 'DELETE') {
                if (stand.ordner.some((o) => o.parentId === id) || stand.dokumente.some((d) => d.ordnerId === id)) {
                    return json(route, { status: 400, message: 'Der Ordner ist nicht leer. Bitte zuerst die Dokumente und Unterordner verschieben oder löschen.', fields: {} }, 400);
                }
                stand.ordner = stand.ordner.filter((o) => o.id !== id);
                return route.fulfill({ status: 204 });
            }
            if (ordnerTreffer[2]) {
                const { parentId } = body as { parentId: number | null };
                if (zweigVerlassen(id, parentId)) return zweigGesperrt(route);
                if (parentId === id || (parentId !== null && nachfahren(id).includes(parentId))) {
                    return json(route, { status: 400, message: 'Ein Ordner kann nicht in sich selbst oder einen seiner Unterordner verschoben werden.', fields: {} }, 400);
                }
                ordner.parentId = parentId;
            } else {
                ordner.name = (body as { name: string }).name;
            }
            return json(route, ordnerDto(ordner));
        }
        const dokTreffer = /^\/(\d+)$/.exec(pfad);
        const dok = dokTreffer ? stand.dokumente.find((d) => d.id === Number(dokTreffer[1])) : undefined;
        if (!dok) return json(route, { status: 404, message: 'Das Dokument gibt es nicht mehr.', fields: {} }, 404);
        if (methode === 'DELETE') {
            stand.dokumente = stand.dokumente.filter((d) => d !== dok);
            return route.fulfill({ status: 204 });
        }
        Object.assign(dok, body as Pick<DokumentStub, 'name' | 'ordnerId' | 'faelligAm'>);
        return json(route, dto(dok));
    };

    await page.route(/\/api\/firmendokumente(\/.*)?(\?.*)?$/, async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname.replace(/^\/api\/firmendokumente/, '');
        const methode = request.method();

        if (pfad === '/ordner' && methode === 'GET') return json(route, [...stand.ordner].sort(nachName).map(ordnerDto));
        const rechteLesen = /^\/ordner\/(\d+)\/rechte$/.exec(pfad);
        if (rechteLesen && methode === 'GET') {
            const ordner = ordnerVon(Number(rechteLesen[1]));
            if (!optionen.admin) return json(route, { status: 403, message: 'Nur Admins dürfen das.', fields: {} }, 403);
            return ordner ? json(route, rechteDto(ordner)) : json(route, { status: 404, message: 'Den Ordner gibt es nicht mehr.', fields: {} }, 404);
        }
        if (pfad === '/ordner' && methode === 'POST') {
            const { name, parentId } = request.postDataJSON() as { name: string; parentId: number | null };
            const neu = { id: naechsteId++, name: name.trim(), parentId };
            stand.ordner.push(neu);
            return json(route, ordnerDto(neu), 201);
        }
        if (pfad === '/faellig') {
            const faellig = stand.dokumente.filter((d) => d.faelligAm !== null && d.faelligAm <= grenze)
                .sort((a, b) => (a.faelligAm as string).localeCompare(b.faelligAm as string) || nachName(a, b));
            return json(route, seite(faellig, url));
        }
        if (pfad === '/suche') {
            const q = (url.searchParams.get('q') ?? '').trim().toLocaleLowerCase('de');
            if (q.length < 2) return json(route, { status: 400, message: 'Bitte mindestens 2 Zeichen eingeben.', fields: {} }, 400);
            const treffer = stand.dokumente.filter((d) => `${d.name} ${d.originalDateiname}`.toLocaleLowerCase('de').includes(q)).sort(nachName);
            return json(route, seite(treffer, url));
        }
        if (pfad === '' && methode === 'GET') {
            const ordnerId = Number(url.searchParams.get('ordnerId'));
            return json(route, seite(stand.dokumente.filter((d) => d.ordnerId === ordnerId).sort(nachName), url));
        }
        if (pfad === '' && methode === 'POST') {
            const felder = multipartFelder(request);
            stand.hochgeladen.push(felder);
            const dateiname = /filename="([^"]*)"/.exec(request.postDataBuffer()?.toString('utf8') ?? '')?.[1] ?? 'hochgeladen.pdf';
            if (!darfAendern(ordnerVon(Number(felder.ordnerId)))) return keinRecht(route);
            if (!felder.name?.trim()) return json(route, { status: 400, message: 'Bitte einen Namen für das Dokument eingeben.', fields: {} }, 400);
            const neu: DokumentStub = {
                id: naechsteId++, ordnerId: Number(felder.ordnerId), name: felder.name.trim(), originalDateiname: dateiname,
                groesseBytes: MINI_PDF.length, hochgeladenAm: `${heute}T12:00:00`, hochgeladenVon: 'Max Mustermann', faelligAm: felder.faelligAm || null,
            };
            stand.dokumente.push(neu);
            return json(route, dto(neu), 201);
        }
        if (/^\/\d+\/vorschaubild$/.test(pfad)) {
            // Wie der Server: nur für PDF und Bild, sonst 404 mit Meldung.
            stand.vorschaubildAbrufe.push(url.pathname);
            const dok = stand.dokumente.find((d) => d.id === Number(/\d+/.exec(pfad)?.[0]));
            const art = dok ? dateiartVon(dok.originalDateiname) : 'SONSTIGE';
            if (art === 'SONSTIGE') return json(route, { status: 404, message: 'Für diese Datei gibt es kein Vorschaubild.', fields: {} }, 404);
            return route.fulfill({ status: 200, contentType: 'image/png', body: art === 'BILD' ? VORSCHAU_FOTO : VORSCHAU_SEITE, headers: { 'Cache-Control': 'private, max-age=86400' } });
        }
        if (/^\/\d+\/datei$/.test(pfad)) {
            stand.dateiAbrufe.push(`${url.pathname}${url.search}`);
            const dok = stand.dokumente.find((d) => d.id === Number(/\d+/.exec(pfad)?.[0]));
            const art = dok ? dateiartVon(dok.originalDateiname) : 'PDF';
            const anhang = url.searchParams.get('download') === 'true' || art === 'SONSTIGE';
            return route.fulfill({
                status: 200,
                contentType: art === 'BILD' ? 'image/png' : art === 'PDF' ? 'application/pdf' : 'application/octet-stream',
                body: art === 'BILD' ? MINI_PNG : MINI_PDF,
                headers: anhang ? { 'Content-Disposition': 'attachment; filename="dokument.pdf"' } : { 'Content-Disposition': 'inline' },
            });
        }
        if (methode === 'PUT' || methode === 'DELETE') {
            // Rechte am Ziel prüfen wie der Server: Dokument verschieben braucht „Ändern“ am alten und neuen Ordner.
            const dokTreffer = /^\/(\d+)$/.exec(pfad);
            if (dokTreffer) {
                const dok = stand.dokumente.find((d) => d.id === Number(dokTreffer[1]));
                const ziel = methode === 'PUT' ? (request.postDataJSON() as { ordnerId: number }).ordnerId : dok?.ordnerId;
                if (dok && (!darfAendern(ordnerVon(dok.ordnerId)) || !darfAendern(ordnerVon(ziel ?? dok.ordnerId)))) return keinRecht(route);
                if (dok && ziel !== undefined && zweigVerlassen(dok.ordnerId, ziel)) return zweigGesperrt(route);
            }
            return aendern(route, pfad, methode);
        }
        return json(route, { status: 404, message: 'Gibt es nicht.', fields: {} }, 404);
    });

    return stand;
}

/** Inhalt des Mini-PDFs zum Hochladen über die Dateiauswahl. */
export function pdfDatei(name: string) {
    return { name, mimeType: 'application/pdf', buffer: MINI_PDF };
}

/** Ein Mini-Bild zum Hochladen über die Dateiauswahl oder per Ziehen. */
export function bildDatei(name: string) {
    return { name, mimeType: 'image/png', buffer: MINI_PNG };
}

/** Inhalt des Mini-Bilds als Base64 – zum Bauen einer Datei im Browser (Ziehen aus dem Explorer). */
export const MINI_PNG_BASE64 = MINI_PNG.toString('base64');
