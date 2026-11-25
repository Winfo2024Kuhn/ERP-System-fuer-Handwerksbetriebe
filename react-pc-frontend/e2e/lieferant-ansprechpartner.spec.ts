import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { spacelosesWort } from './hilfen/testdaten';

/**
 * Reiter „Ansprechpartner“ in der Lieferanten-Detailansicht: anlegen,
 * bearbeiten, löschen (ein Ablauf) und das Layout mit 0 bzw. 30 Personen
 * und sehr langen Namen. /api ist gestubbt, nur Dummy-Daten (DSGVO).
 */

const LIEFERANT_ID = 42;

interface Ansprechpartner {
    id: number;
    anrede: string | null;
    vorname: string | null;
    nachname: string;
    aufgabe: string | null;
    telefon: string | null;
    mobiltelefon: string | null;
    email: string | null;
    notiz: string | null;
}

const leer = { anrede: null, vorname: null, aufgabe: null, telefon: null, mobiltelefon: null, email: null, notiz: null };
const MAX: Ansprechpartner = {
    ...leer, id: 1, anrede: 'HERR', vorname: 'Max', nachname: 'Mustermann', aufgabe: 'Außendienst',
    mobiltelefon: '0171 1234567', email: 'max@muster-stahl.example', notiz: 'Montags nicht erreichbar',
};

const json = (route: Route, body: unknown, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', body: body === null ? '' : JSON.stringify(body) });

/** Stubbt die Detailseite; die Ansprechpartner leben im Speicher und ändern sich mit POST/PUT/DELETE. */
async function stubbeLieferant(page: Page, start: Ansprechpartner[]) {
    const liste = [...start];
    let naechsteId = 100;
    await page.route('**/api/**', (route) => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        const methode = anfrage.method();
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max.buero', displayName: 'Max Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/telefon/berechtigung') return json(route, { darfTelefonSehen: false });
        if (pfad === `/api/lieferanten/${LIEFERANT_ID}`) {
            return json(route, {
                id: LIEFERANT_ID, lieferantenname: 'Musterstahl GmbH', rollen: [], strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt',
                kundenEmails: [], emailAnzahl: 0, dokumenteAnzahl: 0, notizenAnzahl: 0, ansprechpartnerAnzahl: liste.length,
                statistik: { gesamtKosten: 0, bestellungAnzahl: 0, artikelAnzahl: 0, lieferzeit: 0 },
            });
        }
        const basis = `/api/lieferanten/${LIEFERANT_ID}/ansprechpartner`;
        if (pfad === basis && methode === 'GET') return json(route, liste);
        if (pfad === basis && methode === 'POST') {
            const neu = { ...(anfrage.postDataJSON() as Omit<Ansprechpartner, 'id'>), id: naechsteId++ };
            liste.push(neu);
            return json(route, neu, 201);
        }
        const einzel = pfad.match(new RegExp(`^${basis}/(\\d+)$`));
        if (einzel) {
            const index = liste.findIndex(a => a.id === Number(einzel[1]));
            if (methode === 'DELETE') {
                liste.splice(index, 1);
                return route.fulfill({ status: 204 });
            }
            if (methode === 'PUT') {
                liste[index] = { ...(anfrage.postDataJSON() as Omit<Ansprechpartner, 'id'>), id: liste[index].id };
                return json(route, liste[index]);
            }
        }
        return json(route, []);
    });
}

const reiter = (page: Page) => page.getByRole('tab', { name: /Ansprechpartner/ });

test('Ansprechpartner anlegen, bearbeiten und löschen', async ({ page }, testInfo) => {
    await stubbeLieferant(page, [MAX]);
    await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=ansprechpartner`);

    const max = page.getByRole('listitem', { name: 'Herr Max Mustermann' });
    await expect(max).toBeVisible();
    await expect(reiter(page)).toHaveText(/Ansprechpartner\s*1/);
    await expect(max.getByRole('link', { name: '0171 1234567' })).toHaveAttribute('href', 'tel:0171 1234567');

    // Info-Knopf geöffnet: nichts abgeschnitten, nichts überdeckt.
    await page.getByRole('button', { name: 'Mehr zu: Ansprechpartner' }).click();
    await expect(page.getByText(/kennt auch die Anruferkennung/)).toBeVisible();
    await designPruefung(page, testInfo, 'lieferant-ansprechpartner-info', { strengePruefungen: false });
    await page.keyboard.press('Escape');

    // Anlegen: Speichern ist gesperrt, bis der Nachname da ist.
    await page.getByRole('button', { name: 'Ansprechpartner hinzufügen' }).click();
    const dialog = page.getByRole('dialog', { name: 'Ansprechpartner hinzufügen' });
    await expect(dialog.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    await expect(dialog.getByText('Bitte einen Nachnamen eintragen.')).toBeVisible();
    await designPruefung(page, testInfo, 'lieferant-ansprechpartner-dialog-leer', { strengePruefungen: false });

    await dialog.getByLabel('Vorname').fill('Erika');
    await dialog.getByLabel('Nachname *').fill('Musterfrau');
    await dialog.getByLabel('Aufgabe').fill('Innendienst');
    await dialog.getByLabel('Telefon').fill('0511 123-12');
    await dialog.getByLabel('E-Mail').fill('erika@muster-stahl');
    await dialog.getByLabel('Notiz').click();
    await expect(dialog.getByText('Die E-Mail-Adresse sieht nicht richtig aus (Beispiel: name@firma.de).').first()).toBeVisible();
    await dialog.getByLabel('E-Mail').fill('erika@muster-stahl.example');
    const angelegt = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/ansprechpartner'));
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    expect((await angelegt).request().postDataJSON()).toMatchObject({ vorname: 'Erika', nachname: 'Musterfrau', anrede: null, mobiltelefon: null });
    await expect(dialog).toBeHidden();
    const erika = page.getByRole('listitem', { name: 'Erika Musterfrau' });
    await expect(erika).toContainText('Innendienst');
    await expect(reiter(page)).toHaveText(/Ansprechpartner\s*2/);

    // Bearbeiten
    await max.getByRole('button', { name: 'Max Mustermann bearbeiten' }).click();
    const bearbeiten = page.getByRole('dialog', { name: 'Ansprechpartner bearbeiten' });
    await expect(bearbeiten.getByLabel('Vorname')).toHaveValue('Max');
    await bearbeiten.getByLabel('Aufgabe').fill('Vertreter');
    await bearbeiten.getByRole('button', { name: 'Speichern' }).click();
    await expect(bearbeiten).toBeHidden();
    await expect(max).toContainText('Vertreter');
    await designPruefung(page, testInfo, 'lieferant-ansprechpartner-liste', {
        primaerAktion: page.getByRole('button', { name: 'Ansprechpartner hinzufügen' }),
    });

    // Löschen nach Rückfrage
    await erika.getByRole('button', { name: 'Erika Musterfrau löschen' }).click();
    const rueckfrage = page.getByRole('dialog').filter({ hasText: 'Erika Musterfrau löschen?' });
    await expect(rueckfrage).toContainText('FRITZ!Box-Telefonbuch');
    await rueckfrage.getByRole('button', { name: 'Ansprechpartner löschen' }).click();
    await expect(erika).toBeHidden();
    await expect(reiter(page)).toHaveText(/Ansprechpartner\s*1/);
});

test('Leer-Zustand und 30 Ansprechpartner mit sehr langen Namen', async ({ page }, testInfo) => {
    const lang: Ansprechpartner[] = Array.from({ length: 30 }, (_, i) => ({
        ...leer,
        id: i + 1,
        anrede: i % 2 ? 'FRAU' : 'HERR',
        vorname: i === 0 ? spacelosesWort(60, 'Vorname') : 'Max',
        nachname: i === 0 ? spacelosesWort(80, 'Nachname') : `Mustermann ${String(i + 1).padStart(2, '0')}`,
        aufgabe: i === 0 ? spacelosesWort(100, 'Aufgabe') : 'Außendienst Region Nord und Süd',
        telefon: '0511 1234567-12',
        mobiltelefon: '0171 1234567',
        email: i === 0 ? `${spacelosesWort(60, 'adresse')}@muster-stahl.example` : 'max@muster-stahl.example',
        notiz: i === 0 ? 'Sehr lange Notiz '.repeat(20) : null,
    }));
    await stubbeLieferant(page, lang);
    await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=ansprechpartner`);
    await expect(page.getByRole('listitem')).toHaveCount(30);
    await expect(reiter(page)).toHaveText(/Ansprechpartner\s*30/);
    await designPruefung(page, testInfo, 'lieferant-ansprechpartner-30-lang', {
        primaerAktion: page.getByRole('button', { name: 'Ansprechpartner hinzufügen' }),
    });

    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await stubbeLieferant(page, []);
    await page.reload();
    await expect(page.getByText('Noch keine Ansprechpartner.')).toBeVisible();
    await expect(reiter(page)).toHaveText(/Ansprechpartner\s*0/);
    await designPruefung(page, testInfo, 'lieferant-ansprechpartner-leer', {
        primaerAktion: page.getByRole('button', { name: 'Ansprechpartner hinzufügen' }),
    });
});
