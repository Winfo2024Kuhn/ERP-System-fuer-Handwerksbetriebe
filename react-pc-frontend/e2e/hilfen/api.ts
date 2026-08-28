import type { Page, Route } from '@playwright/test';

/**
 * Stubbt alle /api-Routen, die der Bereich "Website - Neuigkeiten" anfasst.
 *
 * Bewusst ohne echtes Backend: der Beitragsteil spricht ueber das ERP mit
 * der externen Firmen-Website, die in einem Test weder erreichbar noch
 * beschreibbar sein soll. Alle Namen und Texte sind Dummy-Daten (DSGVO).
 */

/** Ein 1x1-PNG als Data-URL -- reicht als Bildquelle fuer die Auswahl. */
export const PIXEL_PNG =
    'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

export interface BeitragStand {
    id: number;
    slug: string;
    title: string;
    excerpt: string;
    content: string;
    status: 'draft' | 'published';
    publishedAt: string | null;
    coverImagePath: string | null;
    images: {
        id: number; postId: number; path: string;
        altText: string | null; sortOrder: number; isCover: boolean;
    }[];
}

/** Was der Stub waehrend eines Tests mitgeschrieben hat. */
export interface Mitschrift {
    /** Jeder POST auf /api/beitraege -- die Zahl deckt Doppelanlagen auf. */
    angelegt: { title: string; excerpt: string; content: string }[];
    /** Jeder Statuswechsel, z.B. das Veroeffentlichen. */
    statusWechsel: { id: number; status: string }[];
    /** Jedes hochgeladene Bild. */
    bildUploads: number[];
}

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(body),
    });
}

const BEISPIEL_BEITRAG: BeitragStand = {
    id: 7,
    slug: 'alte-dachrinne-erneuert',
    title: 'Alte Dachrinne erneuert',
    excerpt: 'In zwei Tagen von der undichten Rinne zur sauberen Lösung.',
    content: '<p>Die alte Rinne war an mehreren Stellen durchgerostet.</p>',
    status: 'published',
    publishedAt: '2026-05-04T09:00:00Z',
    coverImagePath: '/bilder/dachrinne.jpg',
    images: [],
};

/**
 * Haengt alle Stubs an die Seite. Die zurueckgegebene Mitschrift fuellt sich
 * waehrend des Tests und kann danach ausgewertet werden.
 *
 * @param optionen.beitraege Startbestand der Liste.
 * @param optionen.beitraegeFehler Laesst GET /api/beitraege mit 502 antworten
 *   (so meldet sich das ERP, wenn die Website nicht erreichbar ist).
 * @param optionen.ohneProjektBilder Projekt ohne Fotos, fuer den Leerzustand.
 */
export async function stubbeWebsiteApi(page: Page, optionen: {
    beitraege?: BeitragStand[];
    beitraegeFehler?: boolean;
    ohneProjektBilder?: boolean;
} = {}): Promise<Mitschrift> {
    const bestand: BeitragStand[] = optionen.beitraege ?? [{ ...BEISPIEL_BEITRAG }];
    const mitschrift: Mitschrift = { angelegt: [], statusWechsel: [], bildUploads: [] };
    let naechsteId = 100;
    let naechsteBildId = 500;

    // Angemeldeter Admin -- /website haengt hinter RequireAdmin.
    await page.route('**/api/auth/me', route => json(route, {
        id: 1, username: 'admin', email: 'admin@example.invalid',
        vorname: 'Max', nachname: 'Mustermann',
        admin: true, roles: ['ADMIN'], requiresInitialSetup: false,
    }));

    // Projektsuche im ersten Schritt des Assistenten.
    await page.route('**/api/projekte/simple**', route => json(route, [
        { id: 5, bauvorhaben: 'Balkonanlage Musterstraße', auftragsnummer: 'A-2026-005', kunde: 'Mustermann GmbH', abgeschlossen: false },
        { id: 6, bauvorhaben: 'Dachsanierung Beispielweg', auftragsnummer: 'A-2026-006', kunde: 'Beispiel AG', abgeschlossen: true },
    ]));

    // Bildquelle 1: Bautagebuch.
    await page.route('**/api/projekte/*/notizen', route => json(route,
        optionen.ohneProjektBilder ? [] : [{
            notiz: 'Unterkonstruktion gestellt und ausgerichtet.',
            erstelltAm: '2026-04-02T07:30:00Z',
            bilder: [
                { id: 11, originalDateiname: 'balkon-vorher.jpg', url: PIXEL_PNG, thumbnailUrl: PIXEL_PNG, erstelltAm: '2026-04-02T07:31:00Z' },
                { id: 12, originalDateiname: 'balkon-nachher.jpg', url: PIXEL_PNG, thumbnailUrl: PIXEL_PNG, erstelltAm: '2026-04-03T15:05:00Z' },
            ],
        }]));

    // Bildquelle 2: Projektdokumente (nur die Gruppe BILDER zaehlt).
    await page.route('**/api/projekte/*/dokumente', route => json(route,
        optionen.ohneProjektBilder ? [] : [{
            id: 21, originalDateiname: 'geplantes-gelaender.jpg', dateityp: 'image/jpeg',
            url: PIXEL_PNG, thumbnailUrl: PIXEL_PNG,
            dokumentGruppe: 'BILDER', uploadDatum: '2026-03-28T10:00:00Z',
        }]));

    // Analytics-Reiter "Zahlen der Website": 204 heisst "noch kein Stand da".
    await page.route('**/api/website-analytics/**', route =>
        route.fulfill({ status: 204, body: '' }));

    // Der Beitrags-Endpunkt selbst. Eine Route fuer alle Methoden und
    // Unterpfade, damit der Stub den Zustand ueber den ganzen Test haelt.
    await page.route('**/api/beitraege**', async route => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        const methode = anfrage.method();

        if (pfad.endsWith('/api/beitraege') && methode === 'GET') {
            if (optionen.beitraegeFehler) {
                return json(route, { message: 'Die Website war nicht erreichbar.' }, 502);
            }
            return json(route, bestand.map(({ images, content, ...rest }) => {
                void images; void content;
                return rest;
            }));
        }

        if (pfad.endsWith('/api/beitraege') && methode === 'POST') {
            const daten = anfrage.postDataJSON() as { title: string; excerpt: string; content: string };
            mitschrift.angelegt.push(daten);
            const neu: BeitragStand = {
                id: naechsteId++,
                slug: daten.title.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
                title: daten.title, excerpt: daten.excerpt, content: daten.content,
                status: 'draft', publishedAt: null, coverImagePath: null, images: [],
            };
            bestand.unshift(neu);
            return json(route, neu);
        }

        const treffer = /\/api\/beitraege\/(\d+)/.exec(pfad);
        const beitrag = treffer ? bestand.find(b => b.id === Number(treffer[1])) : undefined;
        if (!beitrag) return json(route, { message: 'Nicht gefunden.' }, 404);

        if (pfad.endsWith('/status')) {
            const { status } = anfrage.postDataJSON() as { status: 'draft' | 'published' };
            mitschrift.statusWechsel.push({ id: beitrag.id, status });
            beitrag.status = status;
            beitrag.publishedAt = status === 'published' ? '2026-08-28T12:00:00Z' : null;
            return json(route, beitrag);
        }

        if (pfad.endsWith('/bilder') && methode === 'POST') {
            mitschrift.bildUploads.push(beitrag.id);
            const bildId = naechsteBildId++;
            beitrag.images.push({
                id: bildId, postId: beitrag.id, path: `/bilder/${bildId}.jpg`,
                altText: null, sortOrder: beitrag.images.length, isCover: false,
            });
            return json(route, beitrag);
        }

        if (pfad.endsWith('/titelbild')) {
            const { imageId } = anfrage.postDataJSON() as { imageId: number };
            beitrag.images.forEach(b => { b.isCover = b.id === imageId; });
            beitrag.coverImagePath = `/bilder/${imageId}.jpg`;
            return json(route, beitrag);
        }

        if (/\/bilder\/\d+$/.test(pfad)) {
            // Alt-Text setzen (PATCH) oder Bild loeschen (DELETE).
            if (methode === 'DELETE') {
                const bildId = Number(/\/bilder\/(\d+)$/.exec(pfad)![1]);
                beitrag.images = beitrag.images.filter(b => b.id !== bildId);
            } else {
                const { altText } = anfrage.postDataJSON() as { altText: string };
                const letztes = beitrag.images.at(-1);
                if (letztes) letztes.altText = altText;
            }
            return json(route, beitrag);
        }

        if (methode === 'PATCH') {
            Object.assign(beitrag, anfrage.postDataJSON());
            return json(route, beitrag);
        }

        return json(route, beitrag);
    });

    return mitschrift;
}

/** Oeffnet den Bereich Website - Neuigkeiten mit gestubbtem Backend. */
export async function oeffneNeuigkeiten(page: Page) {
    await page.goto('/website');
    await page.getByRole('heading', { name: 'NEUIGKEITEN' }).waitFor();
}
