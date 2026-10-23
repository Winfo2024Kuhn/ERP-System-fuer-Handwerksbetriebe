import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Einstellungen → Abteilungen & Rechte: Abteilung anlegen, umbenennen und löschen.
 * Eine neue Abteilung erscheint sofort darunter als eigene Karte unter
 * „Rechte pro Abteilung“. Die Arbeitsgänge-Seite verweist nur noch hierher.
 * Backend gestubbt (kleiner Speicher-Server), nur Dummy-Daten.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

function rechte(id: number, name: string) {
    return {
        abteilungId: id, abteilungName: name, berechtigungen: [],
        darfMonatAbschliessen: false, darfZeiterfassungSehen: false, darfTelefonSehen: false,
        darfRechnungenGenehmigen: false, darfRechnungenSehen: false, darfFreigabeAnnahmePushen: false,
        darfWebseitenAnfragenPushen: false, darfAufschlaegeAendern: false,
    };
}

/** Auffangnetz zuerst, gezielte Antworten danach (zuletzt registrierte Route gewinnt). */
async function stubbe(page: Page, { admin = true }: { admin?: boolean } = {}) {
    let abteilungen = [{ id: 2, name: 'Büro' }];
    let naechsteId = 10;
    await page.route('**/api/**', route => json(route, []));
    await page.route('**/api/auth/me', route => json(route, {
        id: 1, displayName: 'Max Mustermann', username: 'max', active: true,
        roles: admin ? ['ADMIN'] : [], admin, requiresInitialSetup: false,
    }));
    await page.route('**/api/notifications/summary', route => json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await page.route('**/api/abteilungen/berechtigungen', route => json(route, abteilungen.map(a => rechte(a.id, a.name))));
    await page.route('**/api/abteilungen', route => {
        if (route.request().method() === 'POST') {
            const neu = { id: naechsteId++, name: route.request().postDataJSON().name };
            abteilungen = [...abteilungen, neu];
            return json(route, neu, 201);
        }
        return json(route, abteilungen);
    });
    await page.route(/\/api\/abteilungen\/\d+$/, route => {
        const id = Number(new URL(route.request().url()).pathname.split('/').pop());
        if (route.request().method() === 'PUT') {
            const name = route.request().postDataJSON().name;
            abteilungen = abteilungen.map(a => (a.id === id ? { ...a, name } : a));
            return json(route, { id, name });
        }
        if (route.request().method() === 'DELETE') {
            if (id === 2) {
                return json(route, { message: '„Büro“ kann nicht gelöscht werden: 1 Mitarbeiter ist noch zugeordnet. Bitte zuerst beim Mitarbeiter umstellen.' }, 409);
            }
            abteilungen = abteilungen.filter(a => a.id !== id);
            return route.fulfill({ status: 204 });
        }
        return route.fallback();
    });
}

test('Abteilung anlegen, umbenennen und löschen – die Rechte-Karte folgt sofort', async ({ page }, info) => {
    await stubbe(page);
    await page.goto('/einstellungen#berechtigungen');

    await expect(page.getByRole('tab', { name: /Abteilungen & Rechte/ })).toHaveAttribute('aria-selected', 'true');
    const liste = page.getByRole('list', { name: 'Abteilungen' });
    await expect(liste.getByText('Büro')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Büro' })).toBeVisible();

    // Info neben der Überschrift
    await page.getByRole('button', { name: 'Mehr zu: Abteilungen' }).click();
    const popover = page.getByRole('dialog', { name: 'Abteilungen' });
    await expect(popover.getByText(/Bereits gebuchte Zeiten bleiben/)).toBeVisible();
    await designPruefung(page, info, 'abteilungen-verwalten-info');
    await page.keyboard.press('Escape');
    await expect(popover).toBeHidden();

    // Anlegen → erscheint in der Liste und als eigene Rechte-Karte
    await page.getByRole('textbox', { name: 'Name der neuen Abteilung' }).fill('Montage');
    await page.getByRole('button', { name: 'Abteilung anlegen' }).click();
    await expect(liste.getByText('Montage')).toBeVisible();
    const montageKarte = page.getByRole('heading', { name: 'Montage' });
    await expect(montageKarte).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Name der neuen Abteilung' })).toHaveValue('');
    await designPruefung(page, info, 'abteilungen-verwalten-liste');

    // Umbenennen per Enter
    await page.getByRole('button', { name: '„Montage“ umbenennen' }).click();
    const feld = page.getByRole('textbox', { name: 'Neuer Name für „Montage“' });
    await expect(feld).toBeFocused();
    await designPruefung(page, info, 'abteilungen-verwalten-umbenennen');
    await feld.fill('Außenmontage');
    await feld.press('Enter');
    await expect(page.getByRole('heading', { name: 'Außenmontage' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Montage', exact: true })).toHaveCount(0);

    // Löschen gesperrt (409): Meldung des Servers erscheint wörtlich
    await page.getByRole('button', { name: '„Büro“ löschen' }).click();
    await page.getByRole('dialog', { name: 'Abteilung löschen?' }).getByRole('button', { name: 'Löschen' }).click();
    await expect(page.getByText('„Büro“ kann nicht gelöscht werden: 1 Mitarbeiter ist noch zugeordnet.', { exact: false })).toBeVisible();
    await expect(liste.getByText('Büro')).toBeVisible();

    // Löschen erlaubt: Zeile und Rechte-Karte verschwinden
    await page.getByRole('button', { name: '„Außenmontage“ löschen' }).click();
    await page.getByRole('dialog', { name: 'Abteilung löschen?' }).getByRole('button', { name: 'Löschen' }).click();
    await expect(page.getByRole('heading', { name: 'Außenmontage' })).toHaveCount(0);
    await expect(liste.getByText('Außenmontage')).toHaveCount(0);
});

test('Arbeitsgänge: kein Abteilung-Anlegen mehr, der Hinweis führt zu „Abteilungen & Rechte“', async ({ page }, info) => {
    await stubbe(page);
    await page.goto('/arbeitsgaenge');

    const hinweis = page.getByRole('link', { name: 'Einstellungen → Abteilungen & Rechte' });
    await expect(page.getByRole('heading', { name: 'Büro' })).toBeVisible();
    await expect(hinweis).toBeVisible();
    await expect(page.getByRole('button', { name: /Abteilung anlegen/ })).toHaveCount(0);
    await expect(page.getByPlaceholder(/Neue Abteilung/)).toHaveCount(0);
    await designPruefung(page, info, 'arbeitsgaenge-abteilungen-hinweis');

    await hinweis.click();
    await expect(page).toHaveURL(/\/einstellungen#berechtigungen$/);
    await expect(page.getByRole('tab', { name: /Abteilungen & Rechte/ })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('list', { name: 'Abteilungen' }).getByText('Büro')).toBeVisible();
});

test('Arbeitsgänge ohne Admin-Recht: Direktaufruf führt zurück zu den Projekten', async ({ page }) => {
    await stubbe(page, { admin: false });
    await page.goto('/arbeitsgaenge');

    await expect(page).toHaveURL(/\/projekte$/);
    await expect(page.getByRole('heading', { name: /ARBEITSGÄNGE/i })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Einstellungen → Abteilungen & Rechte' })).toHaveCount(0);
});
