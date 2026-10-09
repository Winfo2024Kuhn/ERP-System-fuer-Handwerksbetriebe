import type { Locator, Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung, keinHorizontalerUeberlauf } from './hilfen/design';

/**
 * Bestellungen → „Projekten zuordnen“ → Modus „Nach Positionen“: jede
 * Warenposition einer Lieferanten-Rechnung geht an genau ein Projekt oder eine
 * Kostenstelle, Fracht und Rabatte verteilt das Programm anteilig.
 * /api ist vollständig gestubbt, nur Fantasienamen (DSGVO).
 */

const RECHNUNG_ID = 21;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

type Art = 'WARE' | 'NEBENKOSTEN' | 'RABATT';

function position(id: number, art: Art, bezeichnung: string, menge: number | null, einzelpreis: number | null, gesamt: number, ziel: { projektId?: number; projektName?: string } = {}) {
    return {
        id, positionNr: id, positionsArt: art, externeArtikelnummer: art === 'WARE' ? `4711-${String(id).padStart(3, '0')}` : null,
        bezeichnung, menge, mengeneinheit: menge != null ? 'Stk' : null, einzelpreis, preiseinheit: null, gesamtpreisNetto: gesamt,
        projektId: ziel.projektId ?? null, projektName: ziel.projektName ?? null, kostenstelleId: null, kostenstelleName: null,
    };
}

const ARTIKEL = ['Stahlträger HEB 200, 6 m', 'Sechskantschraube M12 x 80, verzinkt', 'Flachstahl 40 x 5', 'Winkel 50 x 50 x 5', 'Ankerplatte 200 x 200'];

/** 120 Warenpositionen + Fracht + Rabatt – lange Liste. */
function vielePositionen() {
    const liste = Array.from({ length: 120 }, (_, i) => {
        const id = i + 1;
        const menge = (i % 7) + 1;
        const preis = 12.5 + (i % 5) * 7.25;
        return position(id, 'WARE', ARTIKEL[i % ARTIKEL.length], menge, preis, Math.round(menge * preis * 100) / 100);
    });
    liste.push(position(121, 'NEBENKOSTEN', 'Frachtkosten', null, null, 45));
    liste.push(position(122, 'RABATT', 'Treuerabatt 2 %', null, null, -38.2));
    return liste;
}

const summe = (liste: { gesamtpreisNetto: number }[]) => Math.round(liste.reduce((s, p) => s + p.gesamtpreisNetto, 0) * 100) / 100;

function uebersicht(positionen: ReturnType<typeof vielePositionen>, extra: Record<string, unknown> = {}) {
    const s = summe(positionen);
    return {
        geschaeftsdokumentId: RECHNUNG_ID, dokumentTyp: 'RECHNUNG', auslesbar: true, betragNetto: s, betragBrutto: Math.round(s * 1.19 * 100) / 100,
        summePositionen: s, abweichung: 0, abweichungAuffaellig: false, nachPositionenAufgeteilt: false, positionen, ...extra,
    };
}

function vorschau(positionen: ReturnType<typeof vielePositionen>, body: { positionen: { positionId: number; projektId?: number; kostenstelleId?: number }[] }) {
    const preis = new Map(positionen.map(p => [p.id, p.gesamtpreisNetto]));
    const ware = positionen.filter(p => p.positionsArt === 'WARE');
    const warenwert = summe(ware);
    const netto = summe(positionen);
    const jeZiel = new Map<string, { projektId: number | null; kostenstelleId: number | null; warenwert: number }>();
    let offen = 0;
    for (const p of body.positionen) {
        if (p.projektId == null && p.kostenstelleId == null) { offen++; continue; }
        const key = `${p.projektId}/${p.kostenstelleId}`;
        const z = jeZiel.get(key) ?? { projektId: p.projektId ?? null, kostenstelleId: p.kostenstelleId ?? null, warenwert: 0 };
        z.warenwert += preis.get(p.positionId) ?? 0;
        jeZiel.set(key, z);
    }
    return {
        ziele: [...jeZiel.values()].map(z => {
            const anteil = z.warenwert / warenwert;
            const betragNetto = Math.round(netto * anteil * 100) / 100;
            const betragBrutto = Math.round(betragNetto * 119) / 100;
            return { ...z, anteilProzent: anteil * 100, betragNetto, betragBrutto, betrag: z.projektId != null ? betragBrutto : betragNetto };
        }),
        nichtZugeordnet: offen, warenwert, nebenkosten: Math.round((netto - warenwert) * 100) / 100, abweichung: 0,
        speicherbar: offen === 0, hinweis: offen > 0 ? `Noch ${offen} Warenpositionen ohne Projekt oder Kostenstelle.` : null,
    };
}

const UEBERSICHT_BESTELLUNGEN = {
    offeneAnfragen: [], laufendeBestellungen: [], zugeordnet: [], ausgeblendet: [],
    abgeschlossen: [{
        id: 'a1', lieferantId: 1, lieferantName: 'Musterstahl Handel GmbH', rechnungsVorschlag: null, verbindungen: [],
        dokumente: [{
            id: RECHNUNG_ID, typ: 'RECHNUNG', dokumentNummer: 'RE-2026-0815', dokumentDatum: '2026-09-20', betragBrutto: 1190, betragNetto: 1000,
            liefertermin: null, eingangsDatum: '2026-09-20', dateiname: 'RE-2026-0815.pdf', pdfUrl: '/api/test-pdf/21',
        }],
    }],
};

interface Optionen {
    positionen: ReturnType<typeof vielePositionen>;
    uebersichtExtra?: Record<string, unknown>;
    zuordnungen?: unknown[];
    /** Antwort auf „Positionen auslesen“ (verzögert, damit der Ladezustand sichtbar ist). */
    ausgelesen?: ReturnType<typeof vielePositionen>;
}

async function stubApi(page: Page, optionen: Optionen, mitschrift: { pfad: string; body: unknown }[]) {
    let aktuell = optionen.positionen;
    let auslesenFreigeben: (() => void) | null = null;
    const auslesenGestartet = new Promise<void>(resolve => { auslesenFreigeben = resolve; });
    let auslesenAntworten: (() => void) | null = null;
    const auslesenFertig = new Promise<void>(resolve => { auslesenAntworten = resolve; });

    await page.clock.setFixedTime(new Date('2026-10-05T10:00:00'));
    await page.route('**/api/**', async route => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad.startsWith('/api/test-pdf/')) {
            return route.fulfill({ status: 200, contentType: 'application/pdf', body: '%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF' });
        }
        if (pfad === '/api/bestellungen-uebersicht' && request.method() === 'GET') return json(route, UEBERSICHT_BESTELLUNGEN);
        if (pfad === `/api/bestellungen-uebersicht/geschaeftsdaten/${RECHNUNG_ID}`) {
            const netto = Number(optionen.uebersichtExtra?.betragNetto ?? summe(aktuell.length ? aktuell : vielePositionen()));
            return json(route, { id: RECHNUNG_ID, dokumentNummer: 'RE-2026-0815', dokumentDatum: '2026-09-20', betragNetto: netto, betragBrutto: Math.round(netto * 119) / 100, mwstSatz: 0.19, lieferantName: 'Musterstahl Handel GmbH' });
        }
        if (pfad === `/api/bestellungen-uebersicht/zuordnungen/${RECHNUNG_ID}`) return json(route, optionen.zuordnungen ?? []);
        const basis = `/api/bestellungen-uebersicht/positionen/${RECHNUNG_ID}`;
        if (pfad === basis) return json(route, uebersicht(aktuell, optionen.uebersichtExtra));
        if (pfad === `${basis}/auslesen`) {
            auslesenFreigeben?.();
            await auslesenFertig;
            aktuell = optionen.ausgelesen ?? [];
            return json(route, uebersicht(aktuell, optionen.uebersichtExtra));
        }
        if (pfad === `${basis}/vorschau`) return json(route, vorschau(aktuell, request.postDataJSON()));
        if (request.method() === 'POST') {
            mitschrift.push({ pfad, body: request.postDataJSON() });
            return json(route, { success: true, message: 'Erfolgreich 2 Zuordnung(en) gespeichert', zuordnungen: 2 });
        }
        return json(route, []);
    });
    return {
        auslesenGestartet,
        auslesenBeantworten: () => auslesenAntworten?.(),
    };
}

async function oeffneZuordnung(page: Page) {
    await page.goto('/bestellungen');
    await expect(page.getByRole('heading', { name: 'BESTELLUNGEN' })).toBeVisible();
    await page.getByRole('tab', { name: /^Rechnung zuordnen/ }).click();
    await page.getByRole('button', { name: 'Projekten zuordnen' }).click();
    const dialog = page.getByRole('dialog', { name: 'Rechnung RE-2026-0815' });
    await expect(dialog.getByText('Verteilungsmodus:')).toBeVisible();
    return dialog;
}

const ZWEI_PROJEKTE = [
    { projektId: 10, projektName: 'Hallenanbau Mustermann', prozentanteil: 60, betrag: 600, beschreibung: '' },
    { projektId: 20, projektName: 'Carport Musterfrau', prozentanteil: 40, betrag: 400, beschreibung: '' },
];

test.describe('Rechnung nach Positionen auf Projekte aufteilen', () => {
    /** Griff (Ziehpunkt) einer Positionszeile. */
    const griff = (dialog: Locator, nr: number, name: string) =>
        dialog.getByRole('listitem', { name: `Position ${nr}: ${name}` }).locator('[data-griff]');

    test('Positionen per Ziehen auf Projekte verteilen, markierte wandern gemeinsam', async ({ page }, testInfo) => {
        const positionen = vielePositionen().slice(0, 8);
        await stubApi(page, { positionen, zuordnungen: ZWEI_PROJEKTE }, []);
        const dialog = await oeffneZuordnung(page);
        await dialog.getByRole('button', { name: 'Nach Positionen' }).click();
        const carport = dialog.getByRole('group', { name: 'Ziel Carport Musterfrau' });
        const halle = dialog.getByRole('group', { name: 'Ziel Hallenanbau Mustermann' });
        const ziel = (nr: number) => dialog.getByRole('combobox', { name: `Ziel für Position ${nr}`, exact: true });

        // Eine Zeile am Griff auf den Carport ziehen
        await griff(dialog, 1, ARTIKEL[0]).dragTo(carport, { steps: 12 });
        await expect(ziel(1)).toHaveText('Carport Musterfrau');
        await expect(carport).toContainText('1 Position');

        // Zwei markierte Zeilen gemeinsam auf die Halle – Zustand während des Ziehens prüfen.
        // dnd-kit schluckt Klicks noch 50 ms nach dem Ablegen (damit der Drop nichts anklickt);
        // ein Mensch ist nie so schnell, Playwright schon.
        await page.waitForTimeout(100);
        await dialog.getByRole('checkbox', { name: 'Position 2 markieren' }).check();
        await dialog.getByRole('checkbox', { name: 'Position 3 markieren' }).check();
        const start = (await griff(dialog, 3, ARTIKEL[2]).boundingBox())!;
        const ende = (await halle.boundingBox())!;
        await page.mouse.move(start.x + start.width / 2, start.y + start.height / 2);
        await page.mouse.down();
        await page.mouse.move(start.x + start.width / 2, start.y - 10, { steps: 4 });
        await page.mouse.move(ende.x + ende.width / 3, ende.y + ende.height / 2, { steps: 12 });
        await expect(halle).toContainText('2 Positionen hierher');
        await expect(dialog.getByRole('group', { name: 'Nicht zugeordnet' })).toContainText('Hierher ziehen hebt die Zuordnung auf');
        await designPruefung(page, testInfo, 'zuordnung-positionen-ziehen');
        await page.mouse.up();
        await expect(ziel(2)).toHaveText('Hallenanbau Mustermann');
        await expect(ziel(3)).toHaveText('Hallenanbau Mustermann');
        await expect(dialog.getByRole('checkbox', { name: 'Position 2 markieren' })).not.toBeChecked();
        await expect(dialog.getByText('Noch 5 Positionen nicht zugeordnet', { exact: true })).toBeVisible();

        // Zurück auf „Nicht zugeordnet“ hebt die Zuordnung auf
        await griff(dialog, 1, ARTIKEL[0]).dragTo(dialog.getByRole('group', { name: 'Nicht zugeordnet' }), { steps: 12 });
        await expect(ziel(1)).toHaveText('Zuordnen zu …');
        await expect(dialog.getByText('Noch 6 Positionen nicht zugeordnet', { exact: true })).toBeVisible();
        await designPruefung(page, testInfo, 'zuordnung-positionen-gezogen');
    });

    test('ohne Projekte: Hinweis und Projektsuche, gewähltes Projekt wird Ablagefläche', async ({ page }, testInfo) => {
        await stubApi(page, { positionen: vielePositionen().slice(0, 4), zuordnungen: [] }, []);
        await page.route('**/api/projekte/simple**', route => json(route, [
            { id: 30, bauvorhaben: 'Garage Musterweg', auftragsnummer: 'A-2026-030', kunde: 'Max Mustermann', abgeschlossen: false },
            { id: 31, bauvorhaben: 'Vordach Beispielstraße', auftragsnummer: 'A-2026-031', kunde: 'Erika Musterfrau', abgeschlossen: false },
        ]));
        const dialog = await oeffneZuordnung(page);
        await dialog.getByRole('button', { name: 'Nach Positionen' }).click();

        await expect(dialog.getByText('Zuerst die Projekte auswählen, die zu diesem Beleg gehören.')).toBeVisible();
        await designPruefung(page, testInfo, 'zuordnung-positionen-ohne-projekt', { primaerAktion: dialog.getByRole('button', { name: 'Projekte auswählen' }) });
        await dialog.getByRole('button', { name: 'Projekte auswählen' }).click();
        await expect(page.getByRole('heading', { name: 'Projekt auswählen' })).toBeVisible();
        await page.getByRole('button', { name: /Garage Musterweg/ }).click();

        await expect(dialog.getByRole('group', { name: 'Ziel Garage Musterweg' })).toBeVisible();
        await expect(dialog.getByText('Alle 4 zugeordnet')).toBeVisible();

        // weiteres Projekt über „Projekt hinzufügen…“
        await dialog.getByRole('button', { name: /Projekt hinzufügen/ }).click();
        await page.getByRole('button', { name: /Vordach Beispielstraße/ }).click();
        await expect(dialog.getByRole('group', { name: 'Ziel Vordach Beispielstraße' })).toContainText('Noch keine Position');
    });

    test('120 Positionen per Auswahlliste: markieren, Rest zuordnen, Live-Vorschau, speichern', async ({ page }, testInfo) => {
        const mitschrift: { pfad: string; body: unknown }[] = [];
        const positionen = vielePositionen();
        await stubApi(page, { positionen, zuordnungen: ZWEI_PROJEKTE }, mitschrift);
        const dialog = await oeffneZuordnung(page);

        await dialog.getByRole('button', { name: 'Nach Positionen' }).click();
        const liste = dialog.getByRole('list', { name: 'Positionen der Rechnung' });
        await expect(liste.getByRole('listitem')).toHaveCount(122);
        await expect(dialog.getByText('Noch 120 Positionen nicht zugeordnet', { exact: true })).toBeVisible();
        const speichern = dialog.getByRole('button', { name: 'Zuordnen & Abschließen' });
        await expect(speichern).toBeDisabled();
        await expect(dialog.getByText('Noch 120 Positionen nicht zugeordnet.')).toBeVisible();
        await expect(liste.getByRole('listitem', { name: 'Position 121: Frachtkosten' })).toContainText('wird anteilig verteilt');
        await keinHorizontalerUeberlauf(page);
        await designPruefung(page, testInfo, 'zuordnung-positionen-offen', { primaerAktion: dialog.getByRole('button', { name: 'Nach Positionen' }) });

        // Drei Positionen an den Carport, den Rest an die Halle
        for (const nr of [2, 3, 5]) await dialog.getByRole('checkbox', { name: `Position ${nr} markieren` }).check();
        await dialog.getByRole('combobox', { name: 'Ausgewählte zuordnen zu' }).click();
        await page.getByRole('option', { name: 'Carport Musterfrau' }).click();
        await expect(dialog.getByText('Noch 117 Positionen nicht zugeordnet', { exact: true })).toBeVisible();
        await expect(dialog.getByRole('combobox', { name: 'Ziel für Position 3', exact: true })).toHaveText('Carport Musterfrau');

        // Eine Zeile einzeln, ganz unten in der langen Liste
        const letzte = dialog.getByRole('combobox', { name: 'Ziel für Position 120', exact: true });
        await letzte.scrollIntoViewIfNeeded();
        await letzte.click();
        await page.getByRole('option', { name: 'Carport Musterfrau' }).click();
        await expect(letzte).toHaveText('Carport Musterfrau');
        // Werkzeugleiste und Spaltenkopf bleiben beim Scrollen der langen Liste stehen
        await expect(dialog.getByRole('combobox', { name: 'Alle übrigen zu' })).toBeInViewport();
        await designPruefung(page, testInfo, 'zuordnung-positionen-gescrollt', { primaerAktion: letzte });

        await dialog.getByRole('combobox', { name: 'Alle übrigen zu' }).click();
        await page.getByRole('option', { name: 'Hallenanbau Mustermann' }).click();
        await expect(dialog.getByText('Alle 120 zugeordnet')).toBeVisible();
        await expect(speichern).toBeEnabled();
        // Live-Vorschau je Ziel: Betrag brutto + Anteil an der Ware
        await expect(dialog.getByText(/ % der Ware$/)).toHaveCount(2);
        await dialog.getByRole('list', { name: 'Positionen der Rechnung' }).scrollIntoViewIfNeeded();
        await dialog.getByRole('heading', { name: 'Rechnungsdaten' }).scrollIntoViewIfNeeded();
        await designPruefung(page, testInfo, 'zuordnung-positionen-fertig', { primaerAktion: speichern });

        await speichern.click();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        expect(mitschrift).toHaveLength(1);
        expect(mitschrift[0].pfad).toBe(`/api/bestellungen-uebersicht/positionen/${RECHNUNG_ID}/zuordnen`);
        const body = mitschrift[0].body as { positionen: { positionId: number; projektId?: number }[]; ziele: unknown[] };
        expect(body.positionen).toHaveLength(120);
        expect(body.positionen.filter(p => p.projektId === 20).map(p => p.positionId)).toEqual([2, 3, 5, 120]);
        expect(body.positionen.filter(p => p.projektId === 10)).toHaveLength(116);
        expect(body.ziele).toEqual([{ projektId: 10, beschreibung: '' }, { projektId: 20, beschreibung: '' }]);
    });

    test('ohne Positionen: KI liest aus, Hinweis auf Differenz, ein Ziel wird vorbelegt', async ({ page }, testInfo) => {
        const mitschrift: { pfad: string; body: unknown }[] = [];
        const ausgelesen = vielePositionen().slice(0, 8);
        const stub = await stubApi(page, {
            positionen: [],
            ausgelesen,
            zuordnungen: [ZWEI_PROJEKTE[0]],
            uebersichtExtra: { betragNetto: 1500, abweichung: 45.3, abweichungAuffaellig: true },
        }, mitschrift);
        const dialog = await oeffneZuordnung(page);

        await dialog.getByRole('button', { name: 'Nach Positionen' }).click();
        await expect(dialog.getByText('Für diese Rechnung sind noch keine Positionen ausgelesen.')).toBeVisible();
        await expect(dialog.getByRole('button', { name: 'Zuordnen & Abschließen' })).toBeDisabled();
        await designPruefung(page, testInfo, 'zuordnung-positionen-leer', { primaerAktion: dialog.getByRole('button', { name: 'Positionen auslesen' }) });

        await dialog.getByRole('button', { name: 'Positionen auslesen' }).click();
        await stub.auslesenGestartet;
        await expect(dialog.getByText('Die KI liest die Positionen …')).toBeVisible();
        await expect(dialog.getByText('Das kann bis zu einer Minute dauern.')).toBeVisible();
        await designPruefung(page, testInfo, 'zuordnung-positionen-auslesen');
        stub.auslesenBeantworten();

        await expect(dialog.getByRole('list', { name: 'Positionen der Rechnung' }).getByRole('listitem')).toHaveCount(8);
        await expect(dialog.getByText('Alle 8 zugeordnet')).toBeVisible();
        await expect(dialog.getByRole('note')).toContainText('die Rechnung 1.500,00 € netto – die Differenz wird anteilig verteilt.');
        await expect(dialog.getByRole('button', { name: 'Zuordnen & Abschließen' })).toBeEnabled();
        await designPruefung(page, testInfo, 'zuordnung-positionen-ausgelesen', { primaerAktion: dialog.getByRole('button', { name: 'Zuordnen & Abschließen' }) });
    });

    test('gespeicherte Aufteilung öffnet direkt im Positionsmodus', async ({ page }) => {
        const positionen = vielePositionen().slice(0, 4).map((p, i) => ({
            ...p,
            ...(i < 2 ? { projektId: 10, projektName: 'Hallenanbau Mustermann' } : { projektId: 20, projektName: 'Carport Musterfrau' }),
        }));
        await stubApi(page, { positionen, zuordnungen: ZWEI_PROJEKTE, uebersichtExtra: { nachPositionenAufgeteilt: true } }, []);
        const dialog = await oeffneZuordnung(page);

        await expect(dialog.getByRole('button', { name: 'Nach Positionen' })).toHaveClass(/text-rose-600/);
        await expect(dialog.getByRole('combobox', { name: 'Ziel für Position 1', exact: true })).toHaveText('Hallenanbau Mustermann');
        await expect(dialog.getByRole('combobox', { name: 'Ziel für Position 4', exact: true })).toHaveText('Carport Musterfrau');
        await expect(dialog.getByRole('button', { name: 'Zuordnen & Abschließen' })).toBeEnabled();
    });
});
