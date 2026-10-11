import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { inhalt } from './hilfen/seite';
import { json, stubbeEmailCenter } from './hilfen/postfaecher';

/**
 * E-Mail-Einstellungen als eigene Seite (Kommunikation › E-Mail) und
 * Abwesenheitsnotiz pro Postfach. Backend gestubbt, nur Dummy-Daten (DSGVO).
 */

const EIGENES = { id: 7, emailAdresse: 'max.mustermann@example.com', anzeigename: 'Max Mustermann', eigenes: true };
const INFO = { id: 3, emailAdresse: 'info@example.com', anzeigename: 'Musterbetrieb', eigenes: false };

interface OooStub {
    gespeichert: Record<string, unknown>[];
}

async function stubbeEinstellungen(page: Page, optionen: { waehlbar?: unknown[] } = {}): Promise<OooStub> {
    await stubbeEmailCenter(page);
    const stub: OooStub = { gespeichert: [] };
    const notizen: Record<string, unknown>[] = [];
    const waehlbar = optionen.waehlbar ?? [EIGENES, INFO];
    // Später registriert = zuerst gefragt: diese Route beantwortet /api/email/** vor dem E-Mail-Center-Stub.
    await page.route('**/api/email/**', async (route) => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        const methode = anfrage.method();
        if (pfad === '/api/email/signatures') return json(route, []);
        if (pfad === '/api/email/signatures/default') return route.fulfill({ status: 204, body: '' });
        if (pfad === '/api/email/outofoffice/postfaecher') return json(route, waehlbar);
        if (pfad === '/api/email/outofoffice' && methode === 'GET') return json(route, notizen);
        if (pfad === '/api/email/outofoffice' && methode === 'POST') {
            const body = anfrage.postDataJSON() as Record<string, unknown>;
            stub.gespeichert.push(body);
            const ids = body.postfachIds as number[];
            const notiz = {
                id: 21, title: body.title, startAt: body.startDate, endAt: body.endDate, active: true,
                subjectTemplate: body.subject, bodyTemplate: body.message, signature: null,
                postfaecher: [EIGENES, INFO].filter((p) => ids.includes(p.id))
                    .map(({ id, emailAdresse, anzeigename }) => ({ id, emailAdresse, anzeigename })),
            };
            notizen.splice(0, notizen.length, notiz);
            return json(route, notiz);
        }
        return json(route, []);
    });
    return stub;
}

async function datumEingeben(page: Page, feld: string, datum: string) {
    await page.getByRole('button', { name: feld }).click();
    const kalender = page.getByRole('dialog', { name: `${feld} auswählen` });
    await kalender.getByLabel('Datum eingeben').fill(datum);
    await kalender.getByRole('button', { name: 'Übernehmen' }).click();
    await expect(kalender).toHaveCount(0);
}

test('E-Mail-Einstellungen über die Menüleiste öffnen und Abwesenheit für Postfächer anlegen', async ({ page }, info) => {
    const stub = await stubbeEinstellungen(page);
    await page.goto('/emails/inbox');

    // Im E-Mail-Center gibt es keinen Einstellungen-Knopf mehr.
    const ordner = page.getByRole('navigation', { name: 'E-Mail-Ordner' });
    await expect(ordner.getByRole('button', { name: 'Posteingang' })).toBeVisible();
    await expect(ordner.getByRole('button', { name: 'Einstellungen', exact: true })).toHaveCount(0);

    await page.getByRole('link', { name: /E-Mail-Einstellungen/ }).click();
    await expect(page).toHaveURL(/\/email-einstellungen$/);
    await expect(inhalt(page).getByRole('heading', { level: 1, name: 'E-Mail-Einstellungen' })).toBeVisible();
    await designPruefung(page, info, 'email-einstellungen-seite');

    await inhalt(page).getByRole('button', { name: /Abwesenheitsnotizen/ }).click();
    await inhalt(page).getByRole('button', { name: /Erste Abwesenheit planen/ }).click();

    const postfaecher = page.getByRole('group', { name: 'Gilt für diese Postfächer' });
    const eigenes = postfaecher.getByRole('checkbox', { name: /max\.mustermann@example\.com.*Ihr Postfach/ });
    const allgemein = postfaecher.getByRole('checkbox', { name: /info@example\.com/ });
    await expect(eigenes).toBeChecked();
    await expect(allgemein).not.toBeChecked();
    await postfaecher.scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'email-einstellungen-abwesenheit-formular');

    // Info-Knopf erklärt, was im Hintergrund passiert.
    await page.getByRole('button', { name: 'Mehr zu: Für welche Postfächer?' }).click();
    const erklaerung = page.getByRole('dialog', { name: 'Für welche Postfächer?' });
    await expect(erklaerung).toContainText('aus genau diesem Postfach');
    await designPruefung(page, info, 'email-einstellungen-abwesenheit-info');
    await page.keyboard.press('Escape');
    await expect(erklaerung).toHaveCount(0);

    // Ohne Häkchen geht es nicht.
    await eigenes.uncheck();
    await expect(postfaecher.getByText('Bitte mindestens ein Postfach auswählen.')).toBeVisible();

    await inhalt(page).getByPlaceholder('z.B. Sommerurlaub, Betriebsferien Weihnachten').fill('Sommerurlaub');
    await datumEingeben(page, 'Startdatum', '01.08.2099');
    await datumEingeben(page, 'Enddatum', '14.08.2099');
    await inhalt(page).getByRole('button', { name: 'Abwesenheit planen' }).click();
    await expect(page.getByText('Bitte mindestens ein Postfach auswählen.')).toHaveCount(2);
    expect(stub.gespeichert).toHaveLength(0);

    await eigenes.check();
    await allgemein.check();
    await inhalt(page).getByRole('button', { name: 'Abwesenheit planen' }).click();

    await expect.poll(() => stub.gespeichert.length).toBe(1);
    expect(stub.gespeichert[0]).toMatchObject({
        title: 'Sommerurlaub', startDate: '2099-08-01', endDate: '2099-08-14', active: true, postfachIds: [7, 3],
    });
    expect(stub.gespeichert[0]).not.toHaveProperty('frontendUserId');

    // Karte zeigt die Postfächer als Chips.
    const chips = inhalt(page).getByRole('list', { name: 'Gilt für diese Postfächer' });
    await expect(chips.getByText('max.mustermann@example.com')).toBeVisible();
    await expect(chips.getByText('info@example.com')).toBeVisible();
    await designPruefung(page, info, 'email-einstellungen-abwesenheit-liste');
});

test('Ohne wählbares Postfach: Hinweis statt Auswahl, „Neue Abwesenheit“ gesperrt', async ({ page }, info) => {
    await stubbeEinstellungen(page, { waehlbar: [] });
    await page.goto('/email-einstellungen');
    await inhalt(page).getByRole('button', { name: /Abwesenheitsnotizen/ }).click();

    await expect(inhalt(page).getByText('Für Sie ist kein Postfach eingerichtet. Bitte den Admin fragen.')).toBeVisible();
    await expect(inhalt(page).getByRole('button', { name: /Erste Abwesenheit planen/ })).toBeDisabled();
    await designPruefung(page, info, 'email-einstellungen-kein-postfach');
});
