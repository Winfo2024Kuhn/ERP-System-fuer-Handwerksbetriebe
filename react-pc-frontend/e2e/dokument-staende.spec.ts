import type { BrowserContext, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, oeffneDokumentEditor } from './hilfen/dokument-editor';

/**
 * Stände eines Dokuments („Versionskontrolle“): Verlauf im Editor öffnen, Stände
 * sehen, einen Stand im neuen Tab schreibgeschützt ansehen und wiederherstellen.
 * Der offene Editor im ersten Tab lädt danach neu. Kein Backend, nur Dummy-Daten.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const STAND_3 = {
    id: 3, nummer: 3, art: 'EXPORT', festgehaltenAm: '2026-11-12T14:32:00', festgehaltenVon: 'Max Mustermann',
    bearbeitetVon: ['Erika Musterfrau'], betragNetto: 1234.5,
    aenderungen: [
        'Pos. 3 „Geländer Edelstahl“ gelöscht', 'Rabatt 5 % gegeben', 'Preis Pos. 2: 120,00 € → 95,00 €',
        'Menge Pos. 1: 2 → 3 Stück', 'Betreff geändert', 'Zahlungsziel: 14 → 30 Tage', 'Pos. 5 „Montage“ neu',
    ],
    wiederhergestelltAusNummer: null, aktuell: false, pdfVorhanden: true,
};
const STAND_4 = {
    ...STAND_3, id: 4, nummer: 4, art: 'VOR_WIEDERHERSTELLEN', festgehaltenAm: '2026-11-14T09:05:00', bearbeitetVon: [],
    betragNetto: 1100, aenderungen: ['Preis Pos. 2: 95,00 € → 110,00 €'], aktuell: true, pdfVorhanden: false,
    wiederhergestelltAusNummer: null,
};
const STAND_1 = {
    ...STAND_3, id: 1, nummer: 1, festgehaltenAm: '2026-11-02T08:00:00', bearbeitetVon: [], betragNetto: 1300,
    aenderungen: ['Erste Fassung'],
};

const STAND_3_DETAIL = {
    ...STAND_3, betreff: 'Geländer Edelstahl', datum: '2026-11-12', mwstSatz: 19, zahlungszielTage: 14,
    rechnungsadresseOverride: 'Max Mustermann\nAlter Weg 3\n12345 Musterstadt',
    positionenJson: JSON.stringify({
        blocks: [
            { id: 'pos-1', type: 'SERVICE', title: 'Treppengeländer Stahl verzinkt', quantity: 3, unit: 'Stk', price: 280, fontSize: 10 },
            { id: 'pos-2', type: 'SERVICE', title: 'Handlauf Edelstahl', quantity: 1, unit: 'Stk', price: 95, fontSize: 10 },
            { id: 'pos-3', type: 'SERVICE', title: 'Montage vor Ort', quantity: 4, unit: 'Std.', price: 75, fontSize: 10 },
        ],
        globalRabatt: 5,
    }),
    htmlInhalt: '',
    dokumentBearbeitbar: true,
};

interface Mitschrift { wiederherstellen: number }

async function stubbeStaende(context: BrowserContext): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { wiederherstellen: 0 };
    await context.route(/\/api\/ausgangs-dokumente\/1\/staende\?/, route => json(route, {
        staende: [STAND_4, STAND_3, STAND_1], gesamt: 3, seite: 0, seitenGroesse: 25, hatMehr: false,
        offeneAenderungen: ['Pos. 2 „Handlauf“: Preis 110,00 € → 105,00 €', 'Betreff geändert'],
        dokumentBearbeitbar: true,
    }));
    await context.route('**/api/ausgangs-dokumente/1/staende/3', route => json(route, STAND_3_DETAIL));
    await context.route('**/api/ausgangs-dokumente/1/staende/3/wiederherstellen', route => {
        mitschrift.wiederherstellen += 1;
        return json(route, { id: 1 });
    });
    return mitschrift;
}

test.describe('Dokument – Stände', () => {
    test('Verlauf öffnen, Stand im neuen Tab ansehen und wiederherstellen', async ({ page, context }, testInfo) => {
        await stubbeDokumentEditorApi(context, { dokument: { typ: 'ANGEBOT', dokumentNummer: 'AG-2026/11/00001' } });
        const mitschrift = await stubbeStaende(context);
        await oeffneDokumentEditor(page);

        // 1. Verlauf aus dem Editor
        await designPruefung(page, testInfo, 'staende-editor-kopf', {
            primaerAktion: page.getByRole('button', { name: 'PDF', exact: true }),
        });
        await page.getByRole('button', { name: 'Verlauf' }).click();
        const dialog = page.getByRole('dialog', { name: /Verlauf/ });
        await expect(dialog.getByRole('article', { name: 'Stand 3' })).toBeVisible();
        await expect(dialog.getByRole('tab', { name: 'Stände' })).toHaveAttribute('aria-selected', 'true');
        await expect(dialog.getByRole('region', { name: 'Seit dem letzten Export geändert' })).toBeVisible();
        await expect(dialog.getByText('aktueller Inhalt')).toBeVisible();
        await expect(dialog.getByRole('button', { name: 'Stand 4 wiederherstellen' })).toBeDisabled();
        await designPruefung(page, testInfo, 'staende-verlauf');

        await dialog.getByRole('button', { name: 'Alle 7 Änderungen anzeigen' }).click();
        await expect(dialog.getByText('Pos. 5 „Montage“ neu')).toBeVisible();
        await dialog.getByRole('button', { name: 'Mehr zu: Stände' }).click();
        await expect(page.getByText(/Das automatische Speichern zwischendurch zählt nicht als neuer Stand/)).toBeVisible();
        await designPruefung(page, testInfo, 'staende-verlauf-info');
        await page.keyboard.press('Escape');

        // 2. Stand 3 im neuen Tab ansehen
        const [ansicht] = await Promise.all([
            context.waitForEvent('page'),
            dialog.getByRole('article', { name: 'Stand 3' }).getByRole('link', { name: 'Im Editor ansehen' }).click(),
        ]);
        await expect(ansicht.getByText('Sie sehen Stand 3 vom 12.11.2026, 14:32 Uhr, exportiert von Max Mustermann. Dieser Stand ist eingefroren.')).toBeVisible();
        await expect(ansicht.getByText(/Alter Weg 3/)).toBeVisible();
        await expect(ansicht.getByText('Rabatt 5,00 %')).toBeVisible();
        for (const name of ['PDF', 'E-Mail', 'Speichern', 'Verlauf']) {
            await expect(ansicht.getByRole('button', { name, exact: true })).toHaveCount(0);
        }
        await designPruefung(ansicht, testInfo, 'staende-ansicht', {
            primaerAktion: ansicht.getByRole('button', { name: 'Stand 3 wiederherstellen' }),
        });
        await ansicht.getByRole('button', { name: 'Mehr zu: Stand wiederherstellen' }).click();
        await expect(ansicht.getByText(/Das Datum des Dokuments bleibt/)).toBeVisible();
        await designPruefung(ansicht, testInfo, 'staende-ansicht-info');
        await ansicht.keyboard.press('Escape');

        // 3. Wiederherstellen
        await ansicht.getByRole('button', { name: 'Stand 3 wiederherstellen' }).click();
        const frage = ansicht.getByRole('dialog', { name: 'Stand 3 wiederherstellen?' });
        await expect(frage.getByText(/Gespeicherte, noch nicht exportierte Änderungen sichert das Programm vorher als eigenen Stand/)).toBeVisible();
        await designPruefung(ansicht, testInfo, 'staende-bestaetigung', {
            primaerAktion: frage.getByRole('button', { name: 'Stand wiederherstellen' }),
        });
        await frage.getByRole('button', { name: 'Stand wiederherstellen' }).click();

        await expect(ansicht).toHaveURL(/\/dokument-editor\?dokumentId=1$/);
        await expect(ansicht.getByText('Stand 3 wurde wiederhergestellt. Das Dokument ist neu geladen.')).toBeVisible();
        await expect(ansicht.getByRole('button', { name: 'PDF', exact: true })).toBeVisible();
        expect(mitschrift.wiederherstellen).toBe(1);

        // 4. Der erste Tab lädt das Dokument neu
        await expect(page.getByText('Stand 3 wurde wiederhergestellt. Das Dokument ist neu geladen.')).toBeVisible();
        await expect(page.getByRole('dialog', { name: /Verlauf/ })).toHaveCount(0);
    });
});
