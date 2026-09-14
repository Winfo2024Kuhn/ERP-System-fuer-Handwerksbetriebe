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
            categories: closed ? [] : [{ type: 'MONATSABSCHLUSS', label: 'Monate abschließen', count: 1, icon: 'CalendarCheck', link: '/monatsabschluss?jahr=2025&monat=8' }],
            recentItems: closed ? [] : [{ type: 'MONATSABSCHLUSS', title: 'August 2025 abschließen', subtitle: '1 Mitarbeiter noch zu prüfen', timestamp: '2025-08-01T00:00:00', link: '/monatsabschluss?jahr=2025&monat=8' }] });
        if (path === '/api/mitarbeiter') return json([{ id: 1, vorname: 'Max', nachname: 'Mustermann' }]);
        if (path === '/api/mitarbeiter/1') return json({ id: 1 });
        if (path === '/api/abteilungen') return json([]);
        if (path === '/api/abteilungen/berechtigungen') return json([{ abteilungId: 1, abteilungName: 'Testabteilung', berechtigungen: [], darfMonatAbschliessen: allowed }]);
        if (path === '/api/abteilungen/1/berechtigungen' && method === 'PUT') {
            allowed = route.request().postDataJSON().darfMonatAbschliessen; writes.push('recht'); return json({ darfMonatAbschliessen: allowed });
        }
        if (path === '/api/zeitverwaltung/kalender') return json({ jahr: Number(url.searchParams.get('jahr')), monat: Number(url.searchParams.get('monat')),
            tage: Array.from({ length: 31 }, (_, n) => ({ datum: `2025-08-${String(n + 1).padStart(2, '0')}`, wochentag: (n + 4) % 7 + 1, istFeiertag: false, feiertagName: null, sollStunden: 8, istStunden: 8, buchungen: [] })), sollStundenMonat: 160, istStundenMonat: 168, differenz: 8 });
        if (path.endsWith('/monatsabschluesse/berechtigung') || path.endsWith('/monatsabschluss/berechtigung')) return json({ darfMonatAbschliessen: allowed });
        if (path.endsWith('/uebersicht')) {
            const jahr = Number(url.searchParams.get('jahr')) || 2025;
            const monat = Number(url.searchParams.get('monat')) || 8;
            return json({
                items: [{
                    referenz: { mitarbeiterId: 1, jahr, monat },
                    mitarbeiterName: 'Max Mustermann',
                    abteilungIds: [],
                    festgeschrieben: closed,
                    version: audit.length,
                    festgeschriebenAm: closed ? '2026-09-09T10:00:00' : null,
                    kennzahlen: { istStunden: 120, sollStunden: 120, abwesenheitsStunden: 0, feiertagsStunden: 0, korrekturStunden: 0, gesamtIst: 120, differenz: 0 }
                }],
                totalElements: 1,
                page: 0,
                size: 50,
                summen: { istStunden: 120, sollStunden: 120, abwesenheitsStunden: 0, feiertagsStunden: 0, korrekturStunden: 0, gesamtIst: 120, differenz: 0 },
                auswahl: [{ mitarbeiterId: 1, jahr, monat, version: audit.length, festgeschrieben: closed }]
            });
        }
        if (path.endsWith('/vergleich')) {
            return json([]);
        }
        if (path.endsWith('/sammelabschluss')) {
            writes.push(path);
            closed = true;
            audit = [...audit, { id: audit.length + 1, aktion: 'ABSCHLIESSEN', akteurName: 'Max Mustermann', zeitpunkt: '2026-09-09T10:00:00' }];
            return json({ ergebnisse: [{ referenz: { mitarbeiterId: 1, jahr: 2025, monat: 8 }, status: 'ABGESCHLOSSEN', meldung: 'Monat abgeschlossen.' }] });
        }
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
    await expect(page).toHaveURL(/monatsabschluss\?jahr=2025&monat=8/);
    await page.getByRole('checkbox', { name: 'Max Mustermann auswählen', exact: true }).check();
    const close = page.getByRole('button', { name: 'Monat jetzt abschließen', exact: true });
    await expect(close).toBeEnabled();
    await designPruefung(page, info, 'task9-offen', { primaerAktion: close });
    await close.click();
    await page.getByRole('button', { name: 'Abschließen', exact: true }).click();
    await page.getByRole('button', { name: 'Verlauf für Max Mustermann', exact: true }).click();
    const reopen = page.getByRole('button', { name: 'Monat wieder öffnen', exact: true });
    await expect(reopen).toBeEnabled();
    await reopen.scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'task9-abgeschlossen', { primaerAktion: reopen });
    await reopen.click();
    await page.getByRole('button', { name: 'Wieder öffnen', exact: true }).click();
    await page.getByRole('checkbox', { name: 'Max Mustermann auswählen', exact: true }).check();
    await expect(close).toBeEnabled();
    await close.scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'task9-wieder-offen', { primaerAktion: close });
    expect(writes).toEqual(['recht', '/api/zeitverwaltung/monatsabschluesse/sammelabschluss', '/api/zeitverwaltung/monatsabschluesse/1/2025/8/oeffnen']);
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
    const jetzt = new Date();
    await page.goto(`/monatsabschluss?jahr=${jetzt.getFullYear()}&monat=${jetzt.getMonth() + 1}`);
    await expect(page.getByText('Nur vergangene Monate können abgeschlossen werden.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Monat jetzt abschließen', exact: true })).toBeDisabled();
    await designPruefung(page, info, 'task9-laufender-monat');
    expect(writes).toHaveLength(0);
});
