import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { inhalt } from './hilfen/seite';
import { json, stubbeEmailCenter } from './hilfen/postfaecher';

/**
 * Signatur-Studio in den E-Mail-Einstellungen: Signatur mit Vorlage erstellen,
 * Foto hochladen (wird im Browser verkleinert), speichern und als Standard
 * setzen. Backend gestubbt, nur Dummy-Daten (DSGVO).
 */

const FIRMA = {
    firmenname: 'Mustermann Metallbau GmbH', strasse: 'Musterstraße 1', plz: '12345', ort: 'Musterstadt',
    telefon: '0931 123456', fax: null, email: 'info@example.com', website: 'www.mustermann-metallbau.example', firmenfarbe: '#7c2d12',
};

interface SignaturStub {
    gespeichert: Record<string, unknown>[];
    /** Aufrufe von PUT/DELETE /api/email/signatures/{id}/firmen-vorlage, z. B. „PUT 4“. */
    firmenVorlageAufrufe: string[];
}

async function stubbeSignaturen(page: Page, optionen: { admin?: boolean; signaturen?: Record<string, unknown>[] } = {}): Promise<SignaturStub> {
    await stubbeEmailCenter(page);
    const stub: SignaturStub = { gespeichert: [], firmenVorlageAufrufe: [] };
    const liste: Record<string, unknown>[] = [...(optionen.signaturen ?? [])];
    if (optionen.admin) {
        await page.route('**/api/auth/me', (route) => json(route, {
            id: 1, displayName: 'Erika Admin', username: 'erika', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
        }));
        await page.route('**/api/frontend-users', (route) => json(route, [
            { id: 1, displayName: 'Erika Admin', active: true },
            { id: 7, displayName: 'Max Mustermann', active: true },
        ]));
    }
    await page.route('**/api/firma', (route) => json(route, FIRMA));
    await page.route('**/api/email/**', async (route) => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        const methode = anfrage.method();
        // Wie das Backend: die Liste ohne Formulardaten, erst die Einzelabfrage liefert sie.
        if (pfad === '/api/email/signatures' && methode === 'GET') {
            // Mitarbeiter sehen keine fremden Signaturen – außer ihrer vom Betrieb festgelegten Standard-Signatur.
            const sichtbar = optionen.admin ? liste : liste.filter((s) => s.eigene === true || s.nurLesen === true);
            return json(route, sichtbar.map((s) => ({ ...s, vorlageDaten: null })));
        }
        if (pfad === '/api/email/signatures/firmen-vorlagen' && methode === 'GET') {
            const vorlagen = liste.filter((s) => s.firmenVorlage === true)
                .sort((a, b) => String(a.name).localeCompare(String(b.name), 'de'))
                // Wie das Backend: Nicht-Admins bekommen kein fertiges HTML und keine persönlichen Angaben der Vorlage (außer Telefon).
                .map((s) => (optionen.admin ? s : {
                    ...s, html: '', images: [], besitzerId: null, besitzerName: null, eigene: false, nurLesen: true, defaultSignature: false,
                    vorlageDaten: JSON.stringify({ ...JSON.parse(String(s.vorlageDaten)), name: '', position: '', abteilung: '', mobil: '', email: '', fotoUrl: '' }),
                }));
            return json(route, vorlagen);
        }
        const firmenVorlage = /^\/api\/email\/signatures\/(\d+)\/firmen-vorlage$/.exec(pfad);
        if (firmenVorlage && (methode === 'PUT' || methode === 'DELETE')) {
            stub.firmenVorlageAufrufe.push(`${methode} ${firmenVorlage[1]}`);
            const treffer = liste.find((s) => s.id === Number(firmenVorlage[1]));
            if (!treffer) return json(route, { message: 'Nicht gefunden' }, 404);
            treffer.firmenVorlage = methode === 'PUT';
            return json(route, treffer);
        }
        const einzeln = /^\/api\/email\/signatures\/(\d+)$/.exec(pfad);
        if (einzeln && methode === 'GET') return json(route, liste.find((s) => s.id === Number(einzeln[1])) ?? {});
        if (pfad === '/api/email/signatures' && methode === 'POST') {
            const body = anfrage.postDataJSON() as Record<string, unknown>;
            stub.gespeichert.push(body);
            const dto = {
                id: 50 + stub.gespeichert.length, name: body.name, html: body.html, vorlageDaten: body.vorlageDaten,
                defaultSignature: body.alsStandard === true, isSystemDefault: false, besitzerId: 7, besitzerName: 'Max Mustermann',
                eigene: true, firmenVorlage: false, firmenVorlageId: body.firmenVorlageId ?? null, nurLesen: false,
                createdAt: '2026-10-11T10:00:00', updatedAt: '2026-10-11T10:00:00', images: [],
            };
            liste.push(dto);
            return json(route, dto);
        }
        if (pfad === '/api/email/signatures/default') return route.fulfill({ status: 204, body: '' });
        return json(route, []);
    });
    return stub;
}

/** Ein 800×800-Testbild (Farbverlauf), im Browser gemalt – wird beim Hochladen auf 300 px verkleinert. */
async function testFoto(page: Page): Promise<Buffer> {
    const base64 = await page.evaluate(() => {
        const canvas = document.createElement('canvas');
        canvas.width = 800;
        canvas.height = 800;
        const ctx = canvas.getContext('2d')!;
        const verlauf = ctx.createLinearGradient(0, 0, 800, 800);
        verlauf.addColorStop(0, '#94a3b8');
        verlauf.addColorStop(1, '#334155');
        ctx.fillStyle = verlauf;
        ctx.fillRect(0, 0, 800, 800);
        return canvas.toDataURL('image/png').split(',')[1];
    });
    return Buffer.from(base64, 'base64');
}

test('Signatur mit Vorlage erstellen, Foto hochladen und als Standard speichern', async ({ page }, info) => {
    const stub = await stubbeSignaturen(page);
    await page.goto('/email-einstellungen');

    await expect(inhalt(page).getByRole('heading', { name: 'E-Mail-Signaturen' })).toBeVisible();
    await expect(inhalt(page).getByText('Noch keine Signatur')).toBeVisible();
    // Normale Benutzer sehen den System-Signatur-Hinweis nicht.
    await expect(inhalt(page).getByText('System-Signatur für automatische E-Mails')).toHaveCount(0);
    await designPruefung(page, info, 'signatur-liste-leer', { primaerAktion: inhalt(page).getByRole('button', { name: 'Signatur erstellen' }) });

    await inhalt(page).getByRole('button', { name: 'Signatur erstellen' }).click();
    await expect(inhalt(page).getByRole('heading', { name: 'Neue Signatur gestalten' })).toBeVisible();

    // Vorausgefüllt aus Anmeldung und Firmendaten
    await expect(inhalt(page).getByLabel('Ihr Name')).toHaveValue('Max Mustermann');
    await expect(inhalt(page).getByLabel('Firma')).toHaveValue('Mustermann Metallbau GmbH');

    await expect(inhalt(page).getByRole('radio', { name: /Klassisch mit Foto/ })).toHaveAttribute('aria-checked', 'true');
    await inhalt(page).getByLabel('Position').fill('Metallbaumeister');
    await inhalt(page).getByLabel('Abteilung').fill('Technische Planung');
    await inhalt(page).getByLabel('Mobil').fill('0171 1234567');
    await inhalt(page).getByLabel('E-Mail').fill('max@example.com');

    const vorschau = page.frameLocator('iframe[title="Vorschau der Signatur"]');
    await expect(vorschau.getByText('Metallbaumeister')).toBeVisible();
    await expect(vorschau.getByText('Technische Planung')).toBeVisible();
    await expect(vorschau.locator('a[href="mailto:max@example.com"]')).toBeVisible();

    // Foto hochladen: wird im Browser auf 300 px verkleinert und eingebettet
    await page.getByTestId('bild-datei-foto').setInputFiles({ name: 'foto.png', mimeType: 'image/png', buffer: await testFoto(page) });
    await expect(inhalt(page).getByText(/Hochgeladenes Bild/)).toBeVisible();
    const foto = vorschau.locator('img[src^="data:image/jpeg;base64,"]');
    await expect(foto).toBeVisible();
    await expect(foto).toHaveAttribute('width', '130');

    await designPruefung(page, info, 'signatur-studio-klassisch', { primaerAktion: inhalt(page).getByRole('button', { name: 'Speichern', exact: true }) });

    // Handy-Vorschau und andere Vorlage
    await inhalt(page).getByRole('button', { name: 'Handy' }).click();
    await inhalt(page).getByRole('radio', { name: /Modern mit Farbbalken/ }).click();
    await expect(vorschau.getByText('Metallbaumeister')).toBeVisible();
    await designPruefung(page, info, 'signatur-studio-modern-handy');
    await inhalt(page).getByRole('radio', { name: /Klassisch mit Foto/ }).click();

    // Info-Knopf zu den Bildern
    await inhalt(page).getByRole('button', { name: 'Mehr zu: Bild als Link oder hochladen?' }).click();
    await expect(page.getByRole('dialog', { name: 'Bild als Link oder hochladen?' })).toContainText('verkleinert');
    await designPruefung(page, info, 'signatur-studio-info-bilder');
    await page.keyboard.press('Escape');

    await inhalt(page).getByRole('button', { name: 'Speichern & als meine Standard-Signatur' }).click();
    await expect.poll(() => stub.gespeichert.length).toBe(1);
    const body = stub.gespeichert[0];
    expect(body).toMatchObject({ id: null, name: 'Meine Signatur', alsStandard: true });
    expect(body).not.toHaveProperty('besitzerId');
    expect(String(body.html)).toContain('Metallbaumeister');
    expect(String(body.html)).toContain('data:image/jpeg;base64,');
    expect(String(body.html)).not.toMatch(/<script|<svg/i);
    expect(JSON.parse(String(body.vorlageDaten))).toMatchObject({ vorlage: 'klassisch-foto', position: 'Metallbaumeister', abteilung: 'Technische Planung' });

    // Zurück in der Liste, mit Badge
    await expect(inhalt(page).getByRole('heading', { name: 'E-Mail-Signaturen' })).toBeVisible();
    const karte = inhalt(page).getByRole('listitem').filter({ hasText: 'Meine Signatur' });
    await expect(karte.getByText('Meine Standard-Signatur')).toBeVisible();
    await expect(karte.frameLocator('iframe').getByText('Metallbaumeister')).toBeVisible();
    await designPruefung(page, info, 'signatur-liste-mit-eintrag');
});

test('Admin sieht alle Signaturen mit Besitzer und legt eine Signatur ohne Besitzer an', async ({ page }, info) => {
    const html = (name: string) => `<table cellpadding="0" cellspacing="0" border="0"><tr><td style="font-family:Arial;font-size:14px;"><strong style="color:#500010;">${name}</strong><br>Mustermann Metallbau GmbH</td></tr></table>`;
    const stub = await stubbeSignaturen(page, {
        admin: true,
        signaturen: [
            { id: 3, name: 'Automatische E-Mails', html: html('Ihr Team von Mustermann Metallbau'), vorlageDaten: null, defaultSignature: false, isSystemDefault: true, besitzerId: null, besitzerName: null, eigene: false, createdAt: '', updatedAt: '', images: [] },
            { id: 4, name: 'Max', html: html('Max Mustermann'), vorlageDaten: null, defaultSignature: false, isSystemDefault: false, besitzerId: 7, besitzerName: 'Max Mustermann', eigene: false, createdAt: '', updatedAt: '', images: [] },
        ],
    });
    await page.goto('/email-einstellungen');

    await expect(inhalt(page).getByText('System-Signatur für automatische E-Mails')).toBeVisible();
    await expect(inhalt(page).getByText('gehört: Max Mustermann')).toBeVisible();
    await expect(inhalt(page).getByText('ohne Besitzer')).toBeVisible();
    await designPruefung(page, info, 'signatur-liste-admin', { primaerAktion: inhalt(page).getByRole('button', { name: 'Neue Signatur' }) });

    await inhalt(page).getByRole('button', { name: 'Neue Signatur' }).click();
    const gehoert = inhalt(page).getByRole('combobox', { name: /Gehört zu/ });
    await expect(gehoert).toContainText('Erika Admin (Sie)');
    await gehoert.click();
    await page.getByRole('option', { name: 'Niemand – nur Admins sehen sie' }).click();
    await inhalt(page).getByLabel('Name der Signatur').fill('Info-Postfach');
    await inhalt(page).getByRole('radio', { name: /Kompakt/ }).click();
    await designPruefung(page, info, 'signatur-studio-admin-kompakt');

    await inhalt(page).getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect.poll(() => stub.gespeichert.length).toBe(1);
    expect(stub.gespeichert[0]).toMatchObject({ name: 'Info-Postfach', besitzerId: null, alsStandard: false });
});

/** Formulardaten einer Firmen-Vorlage. Die persönlichen Angaben darin (Erika Musterfrau) dürfen nie bei Mitarbeitern landen. */
function vorlagenDaten(teil: Record<string, unknown>) {
    return JSON.stringify({
        version: 1, vorlage: 'modern-balken', name: 'Erika Musterfrau', position: 'Geschäftsführerin', abteilung: '', firma: 'Mustermann Metallbau GmbH',
        telefon: '0931 123456', mobil: '', fax: '', email: 'erika@example.com', website: 'www.mustermann-metallbau.example',
        adresse: 'Musterstraße 1, 12345 Musterstadt', slogan: 'Metallbau mit Herz', zusatz: 'Amtsgericht Musterstadt HRB 12345',
        fotoUrl: '', logoUrl: '', bannerUrl: '', bannerLink: '', social: { facebook: 'mustermann.metallbau', instagram: '', linkedin: '', whatsapp: '' },
        akzentfarbe: '#1e3a8a', schriftart: 'Georgia', ...teil,
    });
}

const signaturHtml = (text: string) => `<table cellpadding="0" cellspacing="0" border="0"><tr><td style="font-family:Arial;font-size:14px;color:#1e3a8a;"><strong>${text}</strong><br>Mustermann Metallbau GmbH</td></tr></table>`;

function dto(teil: Record<string, unknown>): Record<string, unknown> {
    return {
        html: signaturHtml(String(teil.name)), vorlageDaten: null, defaultSignature: false, isSystemDefault: false, besitzerId: 1,
        besitzerName: 'Erika Admin', eigene: false, firmenVorlage: false, firmenVorlageId: null, nurLesen: false,
        createdAt: '', updatedAt: '', images: [], ...teil,
    };
}

test('Mitarbeiter erstellt seine Signatur aus der Firmen-Vorlage', async ({ page }, info) => {
    const stub = await stubbeSignaturen(page, {
        signaturen: [
            dto({ id: 6, name: 'Vom Büro', html: signaturHtml('Max Mustermann'), defaultSignature: true, nurLesen: true }),
            dto({ id: 20, name: 'Hauptsitz', firmenVorlage: true, vorlageDaten: vorlagenDaten({}) }),
            dto({ id: 21, name: 'Filiale Nord', firmenVorlage: true, vorlageDaten: vorlagenDaten({ vorlage: 'schlicht', firma: 'Mustermann Metallbau GmbH – Filiale Nord', telefon: '040 777000' }) }),
        ],
    });
    await page.goto('/email-einstellungen');

    // Bisher zugewiesene Standard-Signatur: sichtbar, aber nicht änderbar
    const zugewiesen = inhalt(page).getByRole('listitem').filter({ hasText: 'Vom Büro' });
    await expect(zugewiesen.getByText('Vom Betrieb festgelegt')).toBeVisible();
    await expect(zugewiesen.getByText('Erstellen Sie Ihre eigene aus der Firmen-Vorlage.', { exact: false })).toBeVisible();
    await expect(zugewiesen.getByRole('button', { name: 'Bearbeiten' })).toHaveCount(0);
    await expect(zugewiesen.getByRole('button', { name: /löschen/ })).toHaveCount(0);
    await expect(zugewiesen.getByRole('button', { name: 'Nicht mehr Standard' })).toBeVisible();
    await designPruefung(page, info, 'signatur-liste-vom-betrieb', { primaerAktion: inhalt(page).getByRole('button', { name: 'Neue Signatur' }) });

    await inhalt(page).getByRole('button', { name: 'Neue Signatur' }).click();
    await expect(inhalt(page).getByRole('heading', { name: 'Firmen-Vorlage' })).toBeVisible();
    // Sortiert nach Name: „Filiale Nord“ zuerst
    const auswahl = inhalt(page).getByRole('combobox', { name: 'Welche Firmen-Vorlage?' });
    await expect(auswahl).toContainText('Filiale Nord');
    await expect(inhalt(page).getByLabel('Ihr Name')).toHaveValue('Max Mustermann');
    await expect(inhalt(page).getByLabel('Telefon')).toHaveValue('040 777000');
    await expect(inhalt(page).getByLabel('Position')).toHaveValue('');
    await expect(inhalt(page).getByLabel('Firma')).toBeDisabled();
    await expect(inhalt(page).getByRole('radio', { name: /Klassisch mit Foto/ })).toHaveCount(0);
    await expect(inhalt(page).getByRole('switch', { name: 'HTML direkt bearbeiten' })).toHaveCount(0);

    await auswahl.click();
    await page.getByRole('option', { name: 'Hauptsitz' }).click();
    await expect(inhalt(page).getByLabel('Firma')).toHaveValue('Mustermann Metallbau GmbH');
    await expect(inhalt(page).getByLabel('Telefon')).toHaveValue('0931 123456');
    await inhalt(page).getByLabel('Telefon').fill('0931 123456-12');
    await inhalt(page).getByLabel('Position').fill('Metallbaumeister');
    await inhalt(page).getByLabel('E-Mail').fill('max@example.com');

    const vorschau = page.frameLocator('iframe[title="Vorschau der Signatur"]');
    await expect(vorschau.getByText('Metallbaumeister')).toBeVisible();
    await expect(vorschau.getByText('Metallbau mit Herz')).toBeVisible();
    await designPruefung(page, info, 'signatur-studio-firmen-vorlage', { primaerAktion: inhalt(page).getByRole('button', { name: 'Speichern', exact: true }) });
    // Gesperrte Abschnitte: sichtbar, aber nicht änderbar
    await inhalt(page).getByRole('heading', { name: 'Aussehen und Zusatztexte' }).scrollIntoViewIfNeeded();
    await expect(inhalt(page).getByLabel('Kleingedrucktes (optional)')).toBeDisabled();
    await expect(inhalt(page).getByRole('combobox', { name: 'Schriftart' })).toBeDisabled();
    await designPruefung(page, info, 'signatur-studio-firmen-vorlage-gesperrt');

    await inhalt(page).getByRole('button', { name: 'Mehr zu: Was ist die Firmen-Vorlage?' }).click();
    await expect(page.getByRole('dialog', { name: 'Was ist die Firmen-Vorlage?' })).toContainText('persönlichen Angaben');
    await designPruefung(page, info, 'signatur-studio-firmen-vorlage-info');
    await page.keyboard.press('Escape');

    await inhalt(page).getByRole('button', { name: 'Speichern & als meine Standard-Signatur' }).click();
    await expect.poll(() => stub.gespeichert.length).toBe(1);
    const body = stub.gespeichert[0];
    expect(body).toMatchObject({ id: null, firmenVorlageId: 20, alsStandard: true });
    expect(body).not.toHaveProperty('besitzerId');
    const daten = JSON.parse(String(body.vorlageDaten));
    expect(daten).toMatchObject({
        vorlage: 'modern-balken', firma: 'Mustermann Metallbau GmbH', slogan: 'Metallbau mit Herz', akzentfarbe: '#1e3a8a',
        name: 'Max Mustermann', position: 'Metallbaumeister', telefon: '0931 123456-12', email: 'max@example.com',
    });
    expect(String(body.html)).not.toContain('Erika Musterfrau');
    await expect(inhalt(page).getByRole('heading', { name: 'E-Mail-Signaturen' })).toBeVisible();
});

test('Admin legt eine Signatur als Firmen-Vorlage fest', async ({ page }, info) => {
    const stub = await stubbeSignaturen(page, {
        admin: true,
        signaturen: [
            dto({ id: 3, name: 'Automatische E-Mails', isSystemDefault: true, besitzerId: null, besitzerName: null }),
            dto({ id: 4, name: 'Muster für alle', vorlageDaten: vorlagenDaten({}), besitzerId: 1, eigene: true }),
            dto({ id: 5, name: 'Max', besitzerId: 7, besitzerName: 'Max Mustermann', firmenVorlageId: 4 }),
        ],
    });
    await page.goto('/email-einstellungen');

    await expect(inhalt(page).getByText('Firmen-Vorlagen für Mitarbeiter')).toBeVisible();
    await expect(inhalt(page).getByText(/Noch keine Firmen-Vorlage/)).toBeVisible();
    const karte = inhalt(page).getByRole('listitem').filter({ hasText: 'Muster für alle' });
    await karte.getByRole('button', { name: 'Als Firmen-Vorlage' }).click();
    await expect.poll(() => stub.firmenVorlageAufrufe).toEqual(['PUT 4']);
    await expect(page.getByText('„Muster für alle“ ist jetzt eine Firmen-Vorlage.')).toBeVisible();
    await expect(karte.getByText('Firmen-Vorlage', { exact: true })).toBeVisible();
    await expect(inhalt(page).getByText('Aktiv:').filter({ hasText: 'Muster für alle' })).toBeVisible();
    await expect(karte.getByRole('button', { name: 'Keine Firmen-Vorlage mehr' })).toBeVisible();
    await designPruefung(page, info, 'signatur-liste-admin-firmen-vorlage');

    await inhalt(page).getByRole('button', { name: 'Mehr zu: Was ist eine Firmen-Vorlage?' }).click();
    await expect(page.getByRole('dialog', { name: 'Was ist eine Firmen-Vorlage?' })).toContainText('frei gestaltete Signaturen bleiben bestehen');
    await designPruefung(page, info, 'signatur-liste-admin-firmen-vorlage-info');
    await page.keyboard.press('Escape');

    // Admins gestalten weiter frei – auch die Firmen-Vorlage selbst
    await karte.getByRole('button', { name: 'Bearbeiten' }).click();
    await expect(inhalt(page).getByRole('radio', { name: /Modern mit Farbbalken/ })).toHaveAttribute('aria-checked', 'true');
    await expect(inhalt(page).getByLabel('Firma')).toBeEnabled();
    await inhalt(page).getByLabel('Slogan (optional)').fill('Metallbau mit Herz und Verstand');
    await inhalt(page).getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect.poll(() => stub.gespeichert.length).toBe(1);
    expect(stub.gespeichert[0]).toMatchObject({ id: 4, firmenVorlageId: null, besitzerFestlegen: true });
});
