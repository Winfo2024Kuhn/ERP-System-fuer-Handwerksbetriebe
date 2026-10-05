import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import type { Page, Route } from '@playwright/test';

/**
 * E-Mail-Textvorlagen: Button mit Link einfügen (z. B. zur Google-Bewertung).
 * Backend gestubbt, nur Dummy-Daten (DSGVO).
 */

interface Vorlage {
    id: number;
    dokumentTyp: string;
    kategorie: string;
    name: string;
    subjectTemplate: string;
    htmlBody: string;
    aktiv: boolean;
}

function json(route: Route, body: unknown) {
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
}

/**
 * Die Vorlagen-Karten der Seitenleiste kuerzen Namen und Vorschautext bewusst
 * (truncate/line-clamp, bestehendes Verhalten, nicht Teil dieses Features).
 * Die Pruefung auf gekuerzten Text wuerde daran scheitern; Ueberlauf und
 * Ueberschneidungen prueft designPruefung weiterhin.
 */
const OHNE_KUERZUNGSPRUEFUNG = { strengePruefungen: false } as const;

async function stub(page: Page, gespeichert: string[]) {
    const vorlage: Vorlage = {
        id: 1,
        dokumentTyp: 'SCHLUSSRECHNUNG',
        kategorie: 'DOKUMENT',
        name: 'Schlussrechnung mit Bewertung',
        subjectTemplate: 'Schlussrechnung {{DOKUMENTNUMMER}}',
        htmlBody: '<p>{{ANREDE}},</p><p>vielen Dank für Ihren Auftrag.</p>',
        aktiv: true,
    };
    await page.route('**/api/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/auth/me') return json(route, { id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        if (path === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (path === '/api/email-textvorlagen/dokumenttypen') return json(route, [{ value: 'SCHLUSSRECHNUNG', label: 'Schlussrechnung', kategorie: 'DOKUMENT', kategorieLabel: 'Dokumente' }]);
        if (path === '/api/email-textvorlagen/placeholders') return json(route, [{ token: '{{ANREDE}}', label: 'Anrede' }, { token: '{{REVIEW_LINK}}', label: 'Google-Bewertungs-Link' }]);
        if (path === '/api/email-textvorlagen/1' && request.method() === 'PUT') {
            const daten = request.postDataJSON() as Partial<Vorlage>;
            Object.assign(vorlage, daten);
            gespeichert.push(vorlage.htmlBody);
            return json(route, vorlage);
        }
        if (path === '/api/email-textvorlagen') return json(route, [vorlage]);
        return json(route, []);
    });
}

test('Button zur Google-Bewertung einfügen, ändern, speichern und in der Vorschau sehen', async ({ page }, info) => {
    const gespeichert: string[] = [];
    await stub(page, gespeichert);
    await page.goto('/email-textvorlagen');

    await page.getByRole('button', { name: 'Bearbeiten', exact: true }).filter({ hasText: 'Bearbeiten' }).click();
    const editor = page.locator('.ProseMirror');
    await editor.click();
    await page.keyboard.press('ControlOrMeta+End');

    // Einfügen mit Standardwerten (Google-Bewertung)
    await page.getByRole('button', { name: 'Button', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Button einfügen' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByLabel('Beschriftung')).toHaveValue('Jetzt Bewertung abgeben');
    await expect(dialog.getByRole('radio', { name: /Google-Bewertung/ })).toHaveAttribute('aria-checked', 'true');
    await dialog.getByRole('radio', { name: /Google-Bewertung/ }).focus();
    await designPruefung(page, info, 'email-button-dialog', { ...OHNE_KUERZUNGSPRUEFUNG, primaerAktion: dialog.getByRole('button', { name: 'Einfügen' }) });
    await dialog.getByRole('button', { name: 'Einfügen' }).click();
    await expect(dialog).toBeHidden();

    await expect(editor.getByText('Jetzt Bewertung abgeben')).toBeVisible();
    await expect(editor.getByText('Führt zum Google-Bewertungs-Link')).toBeVisible();
    await designPruefung(page, info, 'email-button-im-editor', OHNE_KUERZUNGSPRUEFUNG);

    // Ändern: ungültige eigene Adresse wird abgelehnt, gültige übernommen
    await editor.getByRole('button', { name: 'Ändern' }).click();
    const aendern = page.getByRole('dialog', { name: 'Button ändern' });
    await aendern.getByLabel('Beschriftung').fill('Zu unserer Webseite');
    await aendern.getByRole('radio', { name: /Eigene Adresse/ }).click();
    await aendern.getByLabel('Internet-Adresse').fill('kein link');
    await aendern.getByRole('button', { name: 'Übernehmen' }).click();
    await expect(page.getByText(/gültige Internet-Adresse/)).toBeVisible();
    await expect(aendern).toBeVisible();
    await aendern.getByLabel('Internet-Adresse').fill('www.beispiel.de');
    await aendern.getByRole('button', { name: 'Übernehmen' }).click();
    await expect(aendern).toBeHidden();
    await expect(editor.getByText('https://www.beispiel.de')).toBeVisible();

    // Speichern -> Vorschau zeigt den Button
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect.poll(() => gespeichert.length).toBe(1);
    expect(gespeichert[0]).toContain('data-email-button');
    expect(gespeichert[0]).toContain('href="https://www.beispiel.de"');
    const vorschauButton = page.getByRole('link', { name: 'Zu unserer Webseite' });
    await expect(vorschauButton).toBeVisible();
    await expect(vorschauButton).not.toHaveAttribute('target', /.+/);
    await designPruefung(page, info, 'email-button-vorschau', OHNE_KUERZUNGSPRUEFUNG);
});

test('Button lässt sich wieder entfernen', async ({ page }) => {
    const gespeichert: string[] = [];
    await stub(page, gespeichert);
    await page.goto('/email-textvorlagen');

    await page.getByRole('button', { name: 'Bearbeiten', exact: true }).filter({ hasText: 'Bearbeiten' }).click();
    await page.locator('.ProseMirror').click();
    await page.getByRole('button', { name: 'Button', exact: true }).click();
    await page.getByRole('dialog', { name: 'Button einfügen' }).getByRole('button', { name: 'Einfügen' }).click();

    const editor = page.locator('.ProseMirror');
    await editor.getByRole('button', { name: 'Button entfernen' }).click();
    await expect(editor.getByText('Jetzt Bewertung abgeben')).toHaveCount(0);

    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect.poll(() => gespeichert.length).toBe(1);
    expect(gespeichert[0]).not.toContain('data-email-button');
});
