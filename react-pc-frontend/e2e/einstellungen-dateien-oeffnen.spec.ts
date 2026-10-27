import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Einstellungen -> Dateien: Hilfsprogramm zum direkten Öffnen von Excel-,
 * HiCAD- und Tenado-Dateien einrichten – Windows per Download, Mac per
 * Terminal-Befehl mit Einmal-Link. Dazu die Ordner-Freigabe aus dem ERP-Paket,
 * deren Adresse sich mit einem Klick übernehmen lässt.
 * Kein Backend: alle /api-Routen sind gestubbt. Nur Dummy-Daten (DSGVO).
 */

const MAC_BEFEHL = `/bin/bash -c "$(curl -fSsL 'https://erp.example.test/openfile-launcher/mac/AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd')"`;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubbe(
    page: Page,
    networkUrl = '\\\\server\\zeichnungen',
    freigabe: { adresse: string; pfad: string } | null = null,
): Promise<string[]> {
    const downloadAufrufe: string[] = [];
    await page.route('**/api/**', async (route) => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/settings/datei-ordner') {
            if (route.request().method() === 'PUT') return json(route, {});
            return json(route, {
                pfad: freigabe?.pfad ?? 'C:\\Zeichnungen',
                networkUrl,
                konfiguriert: true,
                containerFreigabe: freigabe?.adresse ?? null,
                containerFreigabePfad: freigabe?.pfad ?? null,
            });
        }
        if (pfad === '/api/openfile-launcher/einrichtung/mac/befehl') {
            downloadAufrufe.push(`${route.request().method()} mac${url.search}`);
            return json(route, { befehl: MAC_BEFEHL, gueltigMinuten: 10 });
        }
        if (pfad === '/api/openfile-launcher/einrichtung/windows') {
            downloadAufrufe.push(`${route.request().method()} windows${url.search}`);
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

test('Hilfsprogramm für Windows herunterladen und Befehl für den Mac erstellen', async ({ page }, info) => {
    const downloadAufrufe = await stubbe(page);
    await page.goto('/einstellungen#dateien');

    // Erst die Daten: das Feld zeigt die gespeicherte Netzwerk-Adresse.
    await expect(page.getByLabel('Netzwerk-Adresse für Kollegen (optional)')).toHaveValue('\\\\server\\zeichnungen');
    // Ganze Karte ins Bild: bis zum letzten Satz der Mac-Beschreibung rollen.
    await page.getByText('mit dem Programm, das dort dafür eingestellt ist.', { exact: false }).scrollIntoViewIfNeeded();
    await expect(page.getByRole('heading', { name: 'Dateien direkt am PC öffnen' })).toBeInViewport();
    const windows = page.getByRole('button', { name: 'Für Windows herunterladen' });
    const mac = page.getByRole('button', { name: 'Befehl für Mac erstellen' });
    await expect(windows).toBeEnabled();
    await expect(mac).toBeEnabled();
    await expect(page.getByText('Nur bei dieser Datei aus dem ERP: Warnt Windows', { exact: false })).toBeVisible();
    await expect(page.getByText('ohne Download und ohne Warnung von macOS', { exact: false })).toBeVisible();
    await expect(page.getByText('Administrator-Rechte sind nicht nötig.', { exact: false })).toHaveCount(2);
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

    await mac.click();
    const befehlFeld = page.getByLabel('Befehl für das Terminal');
    await expect(befehlFeld).toHaveValue(MAC_BEFEHL);
    await expect(page.getByText('Am Mac das Programm „Terminal“ öffnen', { exact: false })).toBeVisible();
    await expect(page.getByText('Befehl einfügen (⌘ + V) und Enter drücken.')).toBeVisible();
    await expect(page.getByText('Warten, bis „Fertig!“ erscheint – dann kann das Fenster zu.')).toBeVisible();
    await expect(page.getByText('Der Befehl gilt 10 Minuten und nur ein einziges Mal.', { exact: false })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Kopieren' })).toBeVisible();
    const neuerBefehl = page.getByRole('button', { name: 'Neuen Befehl erstellen' });
    await expect(neuerBefehl).toBeEnabled();
    expect(downloadAufrufe).toEqual([`GET windows${adresse}`, `POST mac${adresse}`]);
    await page.getByText('Der Befehl gilt 10 Minuten', { exact: false }).scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'einstellungen-dateien-oeffnen-mac-befehl');

    // Info-Knopf neben „Mac“ erklärt, warum es ein Befehl statt einer Datei ist.
    const macInfo = page.getByRole('button', { name: 'Mehr zu: Warum ein Befehl statt einer Datei?' });
    await macInfo.click();
    const macErklaerung = page.getByRole('dialog', { name: 'Warum ein Befehl statt einer Datei?' });
    await expect(macErklaerung).toContainText('Der Link gilt nur 10 Minuten und funktioniert genau einmal');
    await expect(macErklaerung).toContainText('Über das Terminal gibt es diese Sperre nicht.');
    await designPruefung(page, info, 'einstellungen-dateien-oeffnen-mac-info');
    expect(await macErklaerung.evaluate((el) => el.scrollHeight <= el.clientHeight)).toBe(true);
    await page.keyboard.press('Escape');
    await expect(macErklaerung).toHaveCount(0);
    await expect(macInfo).toBeFocused();
});

test('Ohne Netzwerk-Adresse bleiben die Knöpfe gesperrt, bis sie gespeichert ist', async ({ page }, info) => {
    const downloadAufrufe = await stubbe(page, '');
    await page.goto('/einstellungen#dateien');

    const feld = page.getByLabel('Netzwerk-Adresse für Kollegen (optional)');
    await expect(feld).toHaveValue('');
    const windows = page.getByRole('button', { name: 'Für Windows herunterladen' });
    const mac = page.getByRole('button', { name: 'Befehl für Mac erstellen' });
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

test('Ordner-Freigabe des ERP: Adresse übernehmen und speichern, Windows-Anleitung entfällt', async ({ page }, info) => {
    const freigabe = '\\\\erp-dateien.example.ts.net\\CADdrawings';
    const freigabePfad = '/app/uploads/CADdrawings';
    await stubbe(page, '\\\\BUERO-PC\\Zeichnungen', { adresse: freigabe, pfad: freigabePfad });
    await page.goto('/einstellungen#dateien');

    const feld = page.getByLabel('Netzwerk-Adresse für Kollegen (optional)');
    await expect(feld).toHaveValue('\\\\BUERO-PC\\Zeichnungen');
    const hinweis = page.getByRole('region', { name: 'Ordner-Freigabe des ERP ist eingeschaltet' });
    await expect(hinweis).toContainText(freigabe);
    await expect(page.getByRole('button', { name: 'So geben Sie den Ordner im Netzwerk frei' })).toHaveCount(0);
    await expect(page.getByLabel('Ordner auf diesem Rechner')).toHaveValue(freigabePfad);
    await expect(hinweis.getByText('So würden die Links ins Leere zeigen', { exact: false })).toHaveCount(0);
    // Gespeichert ist noch die alte Adresse: die Einrichtung hätte die Zugangsdaten noch nicht dabei.
    const launcherErstSpeichern = page.getByText('Erst oben die Adresse der Ordner-Freigabe eintragen und speichern', { exact: false });
    await expect(launcherErstSpeichern).toBeVisible();
    const eintragen = hinweis.getByRole('button', { name: 'Diese Adresse eintragen' });
    await designPruefung(page, info, 'einstellungen-dateien-container-freigabe', { primaerAktion: eintragen });

    // Info-Knopf an der Überschrift des Hinweises.
    const infoKnopf = hinweis.getByRole('button', { name: 'Mehr zu: Ordner-Freigabe des ERP' });
    await infoKnopf.click();
    const erklaerung = page.getByRole('dialog', { name: 'Ordner-Freigabe des ERP' });
    await expect(erklaerung).toContainText('Das ERP-Paket bringt eine eigene Ordner-Freigabe mit.');
    await expect(erklaerung).toContainText('nur im eigenen Tailscale-Netz erreichbar');
    await expect(erklaerung).toContainText('nur der Weg dorthin ändert sich');
    await designPruefung(page, info, 'einstellungen-dateien-container-freigabe-info');
    expect(await erklaerung.evaluate((el) => el.scrollHeight <= el.clientHeight)).toBe(true);
    await page.keyboard.press('Escape');
    await expect(erklaerung).toHaveCount(0);

    await eintragen.click();
    await expect(feld).toHaveValue(freigabe);
    await expect(hinweis.getByText('Eingetragen')).toBeVisible();
    await expect(eintragen).toHaveCount(0);
    await expect(page.getByText('Bitte erst speichern – das Hilfsprogramm bekommt die gespeicherte Adresse.')).toBeVisible();
    await launcherErstSpeichern.scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'einstellungen-dateien-container-freigabe-eingetragen');

    const speichern = page.waitForRequest((req) => req.method() === 'PUT' && new URL(req.url()).pathname === '/api/settings/datei-ordner');
    await page.getByRole('button', { name: 'Datei-Ordner speichern' }).click();
    expect((await speichern).postDataJSON()).toEqual({ pfad: freigabePfad, networkUrl: freigabe });
    await expect(page.getByText(/Bitte erst speichern/)).toHaveCount(0);
    // Jetzt ist die Adresse der Freigabe gespeichert: die Einrichtung bringt Benutzer und Passwort mit.
    await expect(page.getByText('Benutzer und Passwort für den Ordner stecken schon in der Einrichtung', { exact: false })).toBeVisible();
    await expect(launcherErstSpeichern).toHaveCount(0);
    await expect(page.getByText('Die Datei löscht sich nach der Einrichtung selbst', { exact: false })).toBeVisible();
});
