import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

async function stub(page: Page, options: { allowed?: boolean; error?: boolean; conflict?: boolean } = {}) {
    let allowed = options.allowed ?? true;
    let closed = false;
    let audit: { id: number; aktion: string; akteurName: string; zeitpunkt: string }[] = [];
    const writes: string[] = [];
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const path = url.pathname;
        const method = route.request().method();
        const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
        if (path === '/api/auth/me') return json({ id: 70, username: 'test@example.com', displayName: 'Max Mustermann', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        if (path === '/api/notifications/summary') return json({ totalCount: closed ? 0 : 1,
            categories: closed ? [] : [{ type: 'MONATSABSCHLUSS', label: 'Monate abschließen', count: 1, icon: 'CalendarCheck', link: '/zeitbuchungen?jahr=2025&monat=8' }],
            recentItems: closed ? [] : [{ type: 'MONATSABSCHLUSS', title: 'August 2025 abschließen', subtitle: '1 Mitarbeiter noch zu prüfen', timestamp: '2025-08-01T00:00:00', link: '/zeitbuchungen?jahr=2025&monat=8' }] });
        if (path === '/api/mitarbeiter') return json([{ id: 1, vorname: 'Max', nachname: 'Mustermann' }]);
        if (path === '/api/mitarbeiter/1') return json({ id: 1 });
        if (path === '/api/abteilungen/berechtigungen') return json([{ abteilungId: 1, abteilungName: 'Testabteilung', berechtigungen: [], darfMonatAbschliessen: allowed }]);
        if (path === '/api/abteilungen/1/berechtigungen' && method === 'PUT') {
            allowed = route.request().postDataJSON().darfMonatAbschliessen; writes.push('recht'); return json({ darfMonatAbschliessen: allowed });
        }
        if (path === '/api/zeitverwaltung/kalender') return json({ jahr: Number(url.searchParams.get('jahr')), monat: Number(url.searchParams.get('monat')),
            tage: Array.from({ length: 31 }, (_, n) => ({ datum: `2025-08-${String(n + 1).padStart(2, '0')}`, wochentag: (n + 4) % 7 + 1, istFeiertag: false, feiertagName: null, sollStunden: 8, istStunden: 8, buchungen: [] })), sollStundenMonat: 160, istStundenMonat: 168, differenz: 8 });
        if (path.endsWith('/monatsabschluesse/berechtigung')) return json({ darfMonatAbschliessen: allowed });
        if (path.includes('/monatsabschluesse/')) {
            if (options.error) return json({ message: 'Monatsabschluss konnte nicht geladen werden.' }, 500);
            if (method === 'POST') {
                writes.push(path);
                if (options.conflict) return json({ message: 'Der Monatsstand hat sich geändert. Bitte erneut prüfen.' }, 409);
                closed = path.endsWith('/abschliessen');
                audit = [...audit, { id: audit.length + 1, aktion: closed ? 'ABSCHLIESSEN' : 'OEFFNEN', akteurName: 'Max Mustermann', zeitpunkt: '2026-09-09T10:00:00' }];
            }
            const [, id, year, month] = path.match(/monatsabschluesse\/(\d+)\/(\d+)\/(\d+)/) ?? [];
            return json({ mitarbeiterId: Number(id), jahr: Number(year), monat: Number(month), festgeschrieben: closed, version: audit.length,
                istStunden: 120, sollStunden: 120, gesamtIst: 125, differenz: 5, audit });
        }
        return json([]);
    });
    return writes;
}

test('Recht speichern, Glockenlink, Abschluss und Wiederöffnung mit Verlauf', async ({ page }, info) => {
    const writes = await stub(page, { allowed: false });
    await page.goto('/abteilung-berechtigungen');
    const checkbox = page.getByRole('checkbox', { name: /Monate abschließen und wieder öffnen/ });
    await checkbox.check();
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Gespeichert', exact: true })).toBeVisible();
    await designPruefung(page, info, 'task9-rechte');
    await page.getByTitle('Benachrichtigungen', { exact: true }).click();
    await page.getByText('August 2025 abschließen', { exact: true }).click();
    await expect(page).toHaveURL(/jahr=2025&monat=8/);
    const close = page.getByRole('button', { name: 'Monat abschließen', exact: true });
    await expect(close).toBeEnabled();
    await expect(page.getByText('168,0h', { exact: true })).toBeVisible();
    await designPruefung(page, info, 'task9-offen', { primaerAktion: close });
    await close.click();
    await page.getByRole('button', { name: 'Abschließen', exact: true }).click();
    const reopen = page.getByRole('button', { name: 'Monat wieder öffnen', exact: true });
    await expect(reopen).toBeEnabled();
    await expect(page.getByText('125,0h', { exact: true })).toBeVisible();
    await page.getByText('Verlauf der Monatsabschlüsse (1)', { exact: true }).click();
    await designPruefung(page, info, 'task9-abgeschlossen', { primaerAktion: reopen });
    await reopen.click();
    await page.getByRole('button', { name: 'Wieder öffnen', exact: true }).click();
    await expect(close).toBeEnabled();
    await expect(page.getByText('168,0h', { exact: true })).toBeVisible();
    await expect(page.getByText('Verlauf der Monatsabschlüsse (2)', { exact: true })).toBeVisible();
    await designPruefung(page, info, 'task9-wieder-offen', { primaerAktion: close });
    expect(writes).toEqual(['recht', '/api/zeitverwaltung/monatsabschluesse/1/2025/8/abschliessen', '/api/zeitverwaltung/monatsabschluesse/1/2025/8/oeffnen']);
});

test('Ohne Abschlussrecht bleiben offene Stunden lesbar', async ({ page }, info) => {
    const writes = await stub(page, { allowed: false });
    await page.goto('/zeitbuchungen?jahr=2025&monat=8');
    await expect(page.getByText('Noch offen – die Stunden werden weiterhin aktuell angezeigt.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Monat abschließen', exact: true })).toHaveCount(0);
    await expect(page.getByText('168,0h', { exact: true })).toBeVisible();
    await designPruefung(page, info, 'task9-ohne-recht');
    expect(writes).toHaveLength(0);
});

test('Fehler im Monatsstatus sperrt die Stundenanzeige nicht', async ({ page }, info) => {
    await stub(page, { error: true });
    await page.goto('/zeitbuchungen?jahr=2025&monat=8');
    await expect(page.getByRole('button', { name: 'Monatsstand erneut laden' })).toBeVisible();
    await expect(page.getByText('168,0h', { exact: true })).toBeVisible();
    await designPruefung(page, info, 'task9-statusfehler');
});

test('Laufender Monat kann nicht abgeschlossen werden', async ({ page }, info) => {
    const writes = await stub(page);
    await page.goto('/zeitbuchungen');
    await expect(page.getByText('Nur vergangene Monate können abgeschlossen werden.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Monat abschließen', exact: true })).toBeDisabled();
    await designPruefung(page, info, 'task9-laufender-monat');
    expect(writes).toHaveLength(0);
});
