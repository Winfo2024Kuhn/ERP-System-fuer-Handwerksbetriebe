import type { Locator, Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung, keinHorizontalerUeberlauf } from './hilfen/design';

/**
 * Bestellungen → „Projekten zuordnen“ → Modus „Nach Positionen“: jede
 * Warenposition einer Lieferanten-Rechnung geht an genau ein Projekt oder eine
 * Kostenstelle, Fracht und Rabatte verteilt das Programm anteilig – auf alle
 * oder nur auf die Ziele bestimmter Positionen („Gilt für“). Dazu: eine
 * Bestellung mit mehreren Rechnungen (Rechnungs-Leiste, Sprung zur nächsten).
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
        projektId: ziel.projektId ?? null as number | null, projektName: ziel.projektName ?? null as string | null, kostenstelleId: null, kostenstelleName: null,
        bezugPositionIds: [] as number[],
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

type VorschauAnfrage = { positionen: { positionId: number; projektId?: number; kostenstelleId?: number; bezugPositionIds?: number[] | null }[] };

/** Vorschau wie das Backend: Zuschläge ohne Bezug anteilig auf alle, mit Bezug nur auf die Ziele ihrer Positionen. */
function vorschau(positionen: ReturnType<typeof vielePositionen>, body: VorschauAnfrage) {
    const preis = new Map(positionen.map(p => [p.id, p.gesamtpreisNetto]));
    const ware = positionen.filter(p => p.positionsArt === 'WARE');
    const warenwert = summe(ware);
    const netto = summe(positionen);
    const jeZiel = new Map<string, { projektId: number | null; kostenstelleId: number | null; warenwert: number; mitBezug: number }>();
    const zielVon = new Map<number, string>();
    let offen = 0;
    for (const p of body.positionen) {
        if (p.bezugPositionIds !== undefined) continue;
        if (p.projektId == null && p.kostenstelleId == null) { offen++; continue; }
        const key = `${p.projektId}/${p.kostenstelleId}`;
        const z = jeZiel.get(key) ?? { projektId: p.projektId ?? null, kostenstelleId: p.kostenstelleId ?? null, warenwert: 0, mitBezug: 0 };
        z.warenwert += preis.get(p.positionId) ?? 0;
        jeZiel.set(key, z);
        zielVon.set(p.positionId, key);
    }
    let aufAlle = netto - warenwert;
    let mitBezug = 0;
    for (const p of body.positionen) {
        const ids = (p.bezugPositionIds ?? []).filter(id => zielVon.has(id));
        if (ids.length === 0) continue;
        const betrag = preis.get(p.positionId) ?? 0;
        const basis = ids.reduce((s, id) => s + (preis.get(id) ?? 0), 0);
        for (const id of ids) jeZiel.get(zielVon.get(id)!)!.mitBezug += betrag * (preis.get(id) ?? 0) / basis;
        aufAlle -= betrag;
        mitBezug += betrag;
    }
    return {
        ziele: [...jeZiel.values()].map(({ mitBezug: eigene, ...z }) => {
            const anteil = z.warenwert / warenwert;
            const betragNetto = Math.round((z.warenwert + aufAlle * anteil + eigene) * 100) / 100;
            const betragBrutto = Math.round(betragNetto * 119) / 100;
            return {
                ...z, anteilProzent: anteil * 100, betragNetto, betragBrutto, betrag: z.projektId != null ? betragBrutto : betragNetto,
                nebenkostenMitBezug: Math.round(eigene * 100) / 100,
            };
        }),
        nichtZugeordnet: offen, warenwert, nebenkosten: Math.round((netto - warenwert) * 100) / 100,
        nebenkostenMitBezug: Math.round(mitBezug * 100) / 100, abweichung: 0,
        speicherbar: offen === 0, hinweis: offen > 0 ? `Noch ${offen} Warenpositionen ohne Projekt oder Kostenstelle.` : null,
    };
}

function rechnung(id: number, nummer: string, datum: string, extra: Record<string, unknown> = {}) {
    return {
        id, typ: 'RECHNUNG', dokumentNummer: nummer, dokumentDatum: datum, betragBrutto: 1190, betragNetto: 1000,
        liefertermin: null, eingangsDatum: datum, dateiname: `${nummer}.pdf`, pdfUrl: `/api/test-pdf/${id}`,
        zugeordnet: false, lagerbestellung: false, ausgeblendet: false, ...extra,
    };
}

function bestellungen(dokumente: ReturnType<typeof rechnung>[]) {
    return {
        offeneAnfragen: [], laufendeBestellungen: [], zugeordnet: [], ausgeblendet: [],
        abgeschlossen: [{ id: 'a1', lieferantId: 1, lieferantName: 'Musterstahl Handel GmbH', rechnungsVorschlag: null, verbindungen: [], dokumente }],
        gesamt: { offeneAnfragen: 0, offeneAnfragenAktuell: 0, laufendeBestellungen: 0, laufendeBestellungenAktuell: 0, abgeschlossen: 1, zugeordnet: 0, ausgeblendet: 0 },
    };
}

interface Optionen {
    /** Rechnungen der Bestellung; Standard: nur RE-2026-0815. */
    rechnungen?: ReturnType<typeof rechnung>[];
    positionen: ReturnType<typeof vielePositionen>;
    uebersichtExtra?: Record<string, unknown>;
    zuordnungen?: unknown[];
    /** Antwort auf „Positionen auslesen“ (verzögert, damit der Ladezustand sichtbar ist). */
    ausgelesen?: ReturnType<typeof vielePositionen>;
}

async function stubApi(page: Page, optionen: Optionen, mitschrift: { pfad: string; body: unknown }[]) {
    let aktuell = optionen.positionen;
    const rechnungen = optionen.rechnungen ?? [rechnung(RECHNUNG_ID, 'RE-2026-0815', '2026-09-20')];
    let auslesenFreigeben: (() => void) | null = null;
    const auslesenGestartet = new Promise<void>(resolve => { auslesenFreigeben = resolve; });
    let auslesenAntworten: (() => void) | null = null;
    const auslesenFertig = new Promise<void>(resolve => { auslesenAntworten = resolve; });

    await page.clock.setFixedTime(new Date('2026-10-05T10:00:00'));
    await page.route('**/api/**', async route => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        if (pfad === '/api/auth/me') {
            // kiAktiv: „Positionen auslesen“ ist ohne KI-Schlüssel gesperrt
            return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false, kiAktiv: true });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad.startsWith('/api/test-pdf/')) {
            return route.fulfill({ status: 200, contentType: 'application/pdf', body: '%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF' });
        }
        if (pfad === '/api/bestellungen-uebersicht' && request.method() === 'GET') return json(route, bestellungen(rechnungen));
        const geschaeftsdaten = /^\/api\/bestellungen-uebersicht\/geschaeftsdaten\/(\d+)$/.exec(pfad);
        if (geschaeftsdaten) {
            const id = Number(geschaeftsdaten[1]);
            const netto = Number(optionen.uebersichtExtra?.betragNetto ?? summe(aktuell.length ? aktuell : vielePositionen()));
            const nummer = rechnungen.find(r => r.id === id)?.dokumentNummer ?? null;
            return json(route, { id, dokumentNummer: nummer, dokumentDatum: '2026-09-20', betragNetto: netto, betragBrutto: Math.round(netto * 119) / 100, mwstSatz: 0.19, lieferantName: 'Musterstahl Handel GmbH' });
        }
        if (/^\/api\/bestellungen-uebersicht\/zuordnungen\/\d+$/.test(pfad)) return json(route, optionen.zuordnungen ?? []);
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
        await expect(liste.getByRole('listitem', { name: 'Position 121: Frachtkosten' })).toContainText('Gilt für: alle Positionen');
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
        const body = mitschrift[0].body as { positionen: { positionId: number; projektId?: number; bezugPositionIds?: number[] }[]; ziele: unknown[] };
        // 120 Warenpositionen plus Fracht und Rabatt mit ihrem Bezug (hier: alle)
        expect(body.positionen).toHaveLength(122);
        expect(body.positionen.slice(120)).toEqual([{ positionId: 121, bezugPositionIds: [] }, { positionId: 122, bezugPositionIds: [] }]);
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

    test('Fracht gehört nur zu einer Position: „Gilt für“ ändern, Vorschau und Summen rechnen neu', async ({ page }, testInfo) => {
        const mitschrift: { pfad: string; body: unknown }[] = [];
        const alle = vielePositionen();
        // Positionen 1–2 an die Halle, 3–4 an den Carport (gespeichert), dazu Fracht und Rabatt
        const positionen = [...alle.slice(0, 4), ...alle.slice(120)].map(p => (p.positionsArt !== 'WARE' ? p : p.id <= 2
            ? { ...p, projektId: 10, projektName: 'Hallenanbau Mustermann' }
            : { ...p, projektId: 20, projektName: 'Carport Musterfrau' }));
        await stubApi(page, { positionen, zuordnungen: ZWEI_PROJEKTE, uebersichtExtra: { nachPositionenAufgeteilt: true } }, mitschrift);
        const dialog = await oeffneZuordnung(page);
        const fracht = dialog.getByRole('listitem', { name: 'Position 121: Frachtkosten' });
        await expect(fracht).toContainText('Gilt für: alle Positionen');
        const zusammenfassung = dialog.locator('[aria-live="polite"]');
        await expect(zusammenfassung).toContainText('(anteilig auf alle verteilt)6,80 €');
        await expect(zusammenfassung).not.toContainText('nur für bestimmte Positionen');

        // Escape schließt nur den kleinen Dialog, die Zuordnung bleibt offen
        const aendern = fracht.getByRole('button', { name: 'Wofür die Nebenkosten „Frachtkosten“ gilt, ändern' });
        await aendern.click();
        const bezug = page.getByRole('dialog', { name: 'Wofür gilt dieser Zuschlag?' });
        await expect(bezug.getByRole('radio', { name: /Auf alle Positionen verteilen/ })).toBeChecked();
        await page.keyboard.press('Escape');
        await expect(bezug).toHaveCount(0);
        await expect(dialog).toBeVisible();
        await expect(aendern).toBeFocused();

        // Nur für Pos. 3 – Häkchen per Tastatur
        await page.keyboard.press('Enter');
        await bezug.getByRole('checkbox', { name: `Pos. 3: ${ARTIKEL[2]}` }).focus();
        await page.keyboard.press('Space');
        await expect(bezug.getByRole('radio', { name: /Nur für diese Positionen/ })).toBeChecked();
        await designPruefung(page, testInfo, 'zuordnung-zuschlag-bezug-dialog', { primaerAktion: bezug.getByRole('button', { name: 'Übernehmen' }) });
        const neueVorschau = page.waitForRequest(r => r.url().endsWith('/vorschau')
            && (r.postDataJSON() as VorschauAnfrage).positionen.some(p => p.positionId === 121 && p.bezugPositionIds?.[0] === 3));
        await bezug.getByRole('button', { name: 'Übernehmen' }).click();
        await neueVorschau;

        await expect(bezug).toHaveCount(0);
        await expect(fracht).toContainText('Gilt für: Pos. 3');
        await expect(zusammenfassung).toContainText('(anteilig auf alle verteilt)-38,20 €');
        await expect(zusammenfassung).toContainText('(nur für bestimmte Positionen)45,00 €');
        await expect(dialog.getByRole('group', { name: 'Ziel Carport Musterfrau' })).toContainText('inkl. 45,00 € Zuschläge für eigene Positionen');
        await expect(dialog.getByRole('group', { name: 'Ziel Hallenanbau Mustermann' })).not.toContainText('Zuschläge für eigene Positionen');

        // Info-Button an der Summe erklärt, was passiert
        await zusammenfassung.getByRole('button', { name: 'Mehr zu: Wofür gilt ein Zuschlag?' }).click();
        await expect(page.getByRole('dialog', { name: 'Wofür gilt ein Zuschlag?' })).toContainText('Der Rechnungsbetrag ändert sich nicht');
        await designPruefung(page, testInfo, 'zuordnung-zuschlag-bezug-info');
        await page.keyboard.press('Escape');
        await expect(page.getByRole('dialog', { name: 'Wofür gilt ein Zuschlag?' })).toHaveCount(0);
        await expect(dialog).toBeVisible();

        await dialog.getByRole('button', { name: 'Zuordnen & Abschließen' }).click();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        const body = mitschrift[0].body as { positionen: unknown[] };
        expect(body.positionen).toContainEqual({ positionId: 121, bezugPositionIds: [3] });
        expect(body.positionen).toContainEqual({ positionId: 122, bezugPositionIds: [] });
    });

    test('Mehrere Rechnungen: Leiste oben, nach dem Speichern weiter zur nächsten, nach der letzten zu', async ({ page }, testInfo) => {
        const mitschrift: { pfad: string; body: unknown }[] = [];
        await stubApi(page, {
            positionen: [],
            zuordnungen: ZWEI_PROJEKTE,
            rechnungen: [
                rechnung(RECHNUNG_ID, 'RE-2026-0815', '2026-09-20'),
                rechnung(22, 'RE-2026-0816', '2026-09-28', { betragBrutto: 595 }),
            ],
        }, mitschrift);
        await page.goto('/bestellungen');
        await expect(page.getByRole('heading', { name: 'BESTELLUNGEN' })).toBeVisible();
        await page.getByRole('tab', { name: /^Rechnung zuordnen/ }).click();
        await page.getByRole('button', { name: 'Projekten zuordnen (2 offen)' }).click();

        const erste = page.getByRole('dialog', { name: 'Rechnung RE-2026-0815' });
        await expect(erste.getByText('Verteilungsmodus:')).toBeVisible();
        const leiste = erste.getByRole('group', { name: 'Rechnungen dieser Bestellung' });
        await expect(leiste.getByRole('button')).toHaveCount(2);
        await expect(leiste.getByRole('button', { name: /^RE-2026-0815/ })).toHaveAttribute('aria-pressed', 'true');
        await expect(leiste.getByRole('button', { name: /^RE-2026-0816/ })).toContainText('595,00 €');
        await expect(erste.getByText('noch 2 offen')).toBeVisible();
        await erste.getByRole('button', { name: 'Mehr zu: Mehrere Rechnungen zu einer Bestellung' }).click();
        await expect(page.getByRole('dialog', { name: 'Mehrere Rechnungen zu einer Bestellung' })).toContainText('Reiter ‚Erledigt‘');
        await designPruefung(page, testInfo, 'zuordnung-mehrere-rechnungen', { primaerAktion: erste.getByRole('button', { name: 'Zuordnen & Abschließen' }) });
        await page.keyboard.press('Escape');
        await expect(page.getByRole('dialog', { name: 'Mehrere Rechnungen zu einer Bestellung' })).toHaveCount(0);

        await erste.getByRole('button', { name: 'Zuordnen & Abschließen' }).click();
        const zweite = page.getByRole('dialog', { name: 'Rechnung RE-2026-0816' });
        await expect(zweite.getByText('Verteilungsmodus:')).toBeVisible();
        await expect(page.getByText('Rechnung RE-2026-0815 zugeordnet – weiter mit RE-2026-0816.')).toBeVisible();
        const leiste2 = zweite.getByRole('group', { name: 'Rechnungen dieser Bestellung' });
        await expect(leiste2.getByRole('button', { name: /^RE-2026-0815/ })).toContainText('zugeordnet');
        await expect(leiste2.getByRole('button', { name: /^RE-2026-0816/ })).toHaveAttribute('aria-pressed', 'true');
        await expect(zweite.getByText('noch 1 offen')).toBeVisible();
        await designPruefung(page, testInfo, 'zuordnung-mehrere-rechnungen-zweite', { primaerAktion: zweite.getByRole('button', { name: 'Zuordnen & Abschließen' }) });

        await zweite.getByRole('button', { name: 'Zuordnen & Abschließen' }).click();
        await expect(page.getByRole('dialog', { name: /^Rechnung RE-/ })).toHaveCount(0);
        expect(mitschrift.map(m => [m.pfad, (m.body as { geschaeftsdokumentId: number }).geschaeftsdokumentId])).toEqual([
            ['/api/bestellungen-uebersicht/zuordnen', RECHNUNG_ID],
            ['/api/bestellungen-uebersicht/zuordnen', 22],
        ]);
    });
});
