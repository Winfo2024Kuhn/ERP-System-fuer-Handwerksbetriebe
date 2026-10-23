import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Einstellungen -> Dateien: Hilfsprogramm zum direkten Öffnen von Excel-,
 * HiCAD- und Tenado-Dateien herunterladen.
 * Kein Backend: alle /api-Routen sind gestubbt. Nur Dummy-Daten (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubbe(page: Page, networkUrl = '\\\\server\\zeichnungen'): Promise<string[]> {
    const downloadAufrufe: string[] = [];
    await page.route('**/api/**', async (route) => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/settings/datei-ordner') {
            return json(route, { pfad: 'C:\\Zeichnungen', networkUrl, konfiguriert: true });
        }
        if (pfad === '/api/openfile-launcher/einrichtung/mac') {
            downloadAufrufe.push(`mac${url.search}`);
            return route.fulfill({
                status: 200,
                contentType: 'application/zip',
                headers: { 'Content-Disposition': 'attachment; filename="ERP-Dateien-oeffnen-Mac.zip"' },
                body: 'PK',
            });
        }
        if (pfad === '/api/openfile-launcher/einrichtung/windows') {
            downloadAufrufe.push(`windows${url.search}`);
            return route.fulfill({
                status: 200,
                contentType: 'application/octet-stream',
                headers: { 'Content-Disposition': 'attachment; filename="ERP-Dateien-oeffnen-Windows.bat"' },
                body: '@echo off\r\n',
            });
        }
        if (pfad.startsWith('/api/settings/')) return json(route, {});
        return json(route, []);
    });
    return downloadAufrufe;
}

test('Hilfsprogramm für Windows und Mac herunterladen', async ({ page }, info) => {
    const downloadAufrufe = await stubbe(page);
    await page.goto('/einstellungen#dateien');

    // Erst die Daten: das Feld zeigt die gespeicherte Netzwerk-Adresse.
    await expect(page.getByLabel('Netzwerk-Adresse für Kollegen (optional)')).toHaveValue('\\\\server\\zeichnungen');
    // Ganze Karte ins Bild: bis zum letzten Satz der Mac-Anleitung rollen.
    await page.getByText('mit dem Programm, das dort dafür eingestellt ist.', { exact: false }).scrollIntoViewIfNeeded();
    await expect(page.getByRole('heading', { name: 'Dateien direkt am PC öffnen' })).toBeInViewport();
    const windows = page.getByRole('button', { name: 'Für Windows herunterladen' });
    const mac = page.getByRole('button', { name: 'Für Mac herunterladen' });
    await expect(windows).toBeEnabled();
    await expect(mac).toBeEnabled();
    await expect(page.getByText('Nur bei dieser Datei aus dem ERP: Warnt Windows', { exact: false })).toBeVisible();
    await expect(page.getByText('Nur bei dieser Datei aus dem ERP: Meldet macOS', { exact: false })).toBeVisible();
    await expect(page.getByText('Administrator-Rechte sind nicht nötig.', { exact: false })).toBeVisible();
    await expect(page.getByText(/Bitte erst speichern|Tragen Sie oben zuerst/)).toHaveCount(0);
    // vite preview läuft auf http: der dezente Hinweis zur https-Adresse steht in der Karte.
    await expect(page.getByText('Sie nutzen das Programm gerade über http.', { exact: false })).toBeVisible();
    await designPruefung(page, info, 'einstellungen-dateien-oeffnen-karte', { primaerAktion: windows });

    // Info-Knopf an der Überschrift erklärt Hintergrund und Wirkung.
    const infoKnopf = page.getByRole('button', { name: 'Mehr zu: Dateien direkt am PC öffnen' });
    await infoKnopf.click();
    const erklaerung = page.getByRole('dialog', { name: 'Dateien direkt am PC öffnen' });
    await expect(erklaerung).toContainText('ob es eine neuere Fassung gibt');
    await expect(erklaerung).toContainText('Geöffnet wird nur, was im gemeinsamen Ordner liegt.');
    await expect(erklaerung).toContainText('nur, wenn dieses Programm sie unterschrieben hat');
    await expect(erklaerung).toContainText('Unterwegs klappt es über Tailscale');
    await designPruefung(page, info, 'einstellungen-dateien-oeffnen-info');
    // Der ganze Text passt ohne Scrollen ins Popover.
    expect(await erklaerung.evaluate((el) => el.scrollHeight <= el.clientHeight)).toBe(true);
    await page.keyboard.press('Escape');
    await expect(erklaerung).toHaveCount(0);
    await expect(infoKnopf).toBeFocused();

    const adresse = `?adresse=${encodeURIComponent(new URL(page.url()).origin)}`;
    const windowsDownload = page.waitForEvent('download');
    await windows.click();
    expect((await windowsDownload).suggestedFilename()).toBe('ERP-Dateien-oeffnen-Windows.bat');
    // Erst wenn der erste Download fertig ist, sind beide Knöpfe wieder frei.
    await expect(mac).toBeEnabled();

    const macDownload = page.waitForEvent('download');
    await mac.click();
    expect((await macDownload).suggestedFilename()).toBe('ERP-Dateien-oeffnen-Mac.zip');
    expect(downloadAufrufe).toEqual([`windows${adresse}`, `mac${adresse}`]);
    await expect(windows).toBeEnabled();
});

test('Ohne Netzwerk-Adresse bleiben die Knöpfe gesperrt, bis sie gespeichert ist', async ({ page }, info) => {
    const downloadAufrufe = await stubbe(page, '');
    await page.goto('/einstellungen#dateien');

    const feld = page.getByLabel('Netzwerk-Adresse für Kollegen (optional)');
    await expect(feld).toHaveValue('');
    const windows = page.getByRole('button', { name: 'Für Windows herunterladen' });
    const mac = page.getByRole('button', { name: 'Für Mac herunterladen' });
    await expect(page.getByText('Tragen Sie oben zuerst die Netzwerk-Adresse ein', { exact: false })).toBeVisible();
    await expect(windows).toBeDisabled();
    await expect(mac).toBeDisabled();

    // Getippt, aber nicht gespeichert: weiterhin gesperrt, mit Hinweis aufs Speichern.
    await feld.fill('\\\\server\\zeichnungen');
    await expect(page.getByText('Bitte erst speichern – das Hilfsprogramm bekommt die gespeicherte Adresse.')).toBeVisible();
    await expect(windows).toBeDisabled();
    await page.getByText('mit dem Programm, das dort dafür eingestellt ist.', { exact: false }).scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'einstellungen-dateien-oeffnen-ungespeichert');
    expect(downloadAufrufe).toEqual([]);
});
