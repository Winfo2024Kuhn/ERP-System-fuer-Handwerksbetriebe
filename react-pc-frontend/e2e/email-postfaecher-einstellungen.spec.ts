import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { json } from './hilfen/postfaecher';

/**
 * Einstellungen → E-Mail → Postfächer: anlegen, Verbindung testen,
 * Hauptpostfach wechseln. Nur Dummy-Daten, /api gestubbt (mit Zustand).
 */

interface Postfach {
    id: number; emailAdresse: string; anzeigename: string | null; aktiv: boolean; sortierung: number;
    hauptpostfach: boolean; fuerGeschaeftsdokumente: boolean; benutzername: string | null; passwortGesetzt: boolean;
    smtpHost: string | null; smtpPort: number | null; imapHost: string | null; imapPort: number | null;
    abrufAktiv: boolean; letzterAbrufAm: string | null; letzterAbrufFehler: string | null;
    zugewieseneBenutzer: { id: number; displayName: string }[];
}

const vorMinuten = (minuten: number) => {
    const d = new Date(Date.now() - minuten * 60_000);
    const zwei = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${zwei(d.getMonth() + 1)}-${zwei(d.getDate())}T${zwei(d.getHours())}:${zwei(d.getMinutes())}:00`;
};

function startbestand(): Postfach[] {
    const server = { smtpHost: 'mail.your-server.de', smtpPort: 465, imapHost: 'mail.your-server.de', imapPort: 993 };
    return [
        {
            id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', aktiv: true, sortierung: 0,
            hauptpostfach: true, fuerGeschaeftsdokumente: false, benutzername: 'info@musterbetrieb.example', passwortGesetzt: true,
            ...server, abrufAktiv: true, letzterAbrufAm: vorMinuten(2), letzterAbrufFehler: null, zugewieseneBenutzer: [],
        },
        {
            id: 4, emailAdresse: 'rechnungen@musterbetrieb.example', anzeigename: 'Musterbetrieb – Buchhaltung', aktiv: true, sortierung: 10,
            hauptpostfach: false, fuerGeschaeftsdokumente: true, benutzername: 'rechnungen@musterbetrieb.example', passwortGesetzt: true,
            ...server, abrufAktiv: true, letzterAbrufAm: vorMinuten(30), letzterAbrufFehler: 'Anmeldung fehlgeschlagen – Passwort prüfen',
            zugewieseneBenutzer: [{ id: 7, displayName: 'Max Mustermann' }],
        },
    ];
}

interface Mitschrift {
    angelegt: Record<string, unknown>[];
    geaendert: Record<string, unknown>[];
    tests: Record<string, unknown>[];
}

async function stubbeEinstellungen(page: Page): Promise<Mitschrift> {
    let bestand = startbestand();
    let naechsteId = 20;
    const mitschrift: Mitschrift = { angelegt: [], geaendert: [], tests: [] };
    const uebernehmen = (id: number, body: Record<string, unknown>): Postfach => {
        const alt = bestand.find(p => p.id === id);
        const neu: Postfach = {
            ...(alt ?? { letzterAbrufAm: null, letzterAbrufFehler: null, zugewieseneBenutzer: [], passwortGesetzt: false }),
            ...(body as unknown as Postfach),
            id,
            passwortGesetzt: !!body.passwort || !!alt?.passwortGesetzt,
            abrufAktiv: !!body.aktiv && !!body.smtpHost && !!body.imapHost && (!!body.passwort || !!alt?.passwortGesetzt),
        };
        if (neu.hauptpostfach) bestand = bestand.map(p => ({ ...p, hauptpostfach: false }));
        if (neu.fuerGeschaeftsdokumente) bestand = bestand.map(p => ({ ...p, fuerGeschaeftsdokumente: false }));
        bestand = alt ? bestand.map(p => (p.id === id ? neu : p)) : [...bestand, neu];
        return neu;
    };

    await page.route('**/api/**', async route => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        const methode = anfrage.method();
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, displayName: 'Max Mustermann', username: 'admin', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        }
        if (pfad === '/api/postfaecher/test' && methode === 'POST') {
            mitschrift.tests.push(anfrage.postDataJSON());
            return json(route, { versandOk: true, abrufOk: true, message: 'Anmeldung für Versand und Abruf hat geklappt.' });
        }
        if (pfad === '/api/postfaecher' && methode === 'GET') return json(route, bestand);
        if (pfad === '/api/postfaecher' && methode === 'POST') {
            const body = anfrage.postDataJSON();
            mitschrift.angelegt.push(body);
            return json(route, uebernehmen(naechsteId++, body), 201);
        }
        const treffer = pfad.match(/^\/api\/postfaecher\/(\d+)$/);
        if (treffer && methode === 'PUT') {
            const body = anfrage.postDataJSON();
            mitschrift.geaendert.push(body);
            return json(route, uebernehmen(Number(treffer[1]), body));
        }
        return json(route, {});
    });
    return mitschrift;
}

test('Postfächer: Liste mit Schildern und Abruf-Status, neues Postfach anlegen und testen', async ({ page }, info) => {
    const mitschrift = await stubbeEinstellungen(page);
    await page.goto('/einstellungen#email');

    const liste = page.getByRole('list', { name: 'Postfächer' });
    const infoZeile = liste.getByRole('listitem').filter({ hasText: 'info@musterbetrieb.example' });
    await expect(infoZeile.getByText('Hauptpostfach', { exact: true })).toBeVisible();
    await expect(infoZeile.getByText(/Abruf ok · vor \d+ Min\./)).toBeVisible();
    const rechnungenZeile = liste.getByRole('listitem').filter({ hasText: 'rechnungen@musterbetrieb.example' });
    await expect(rechnungenZeile.getByText('Rechnungen & Mahnungen')).toBeVisible();
    await expect(rechnungenZeile.getByText('Anmeldung fehlgeschlagen – Passwort prüfen')).toBeVisible();
    await expect(rechnungenZeile.getByText('Max Mustermann')).toBeVisible();
    await designPruefung(page, info, 'postfaecher-liste', { primaerAktion: page.getByRole('button', { name: 'Neues Postfach' }) });

    await page.getByRole('button', { name: 'Neues Postfach' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Neues Postfach', { exact: true })).toBeVisible();
    await dialog.getByLabel('E-Mail-Adresse *').fill('max@musterbetrieb.example');
    await dialog.getByLabel('Passwort', { exact: true }).fill('Dummy-Passwort-123');
    // Server-Angaben sind aus dem Hauptpostfach übernommen und eingeklappt.
    await expect(dialog.getByText('mail.your-server.de · Ports 465 / 993')).toBeVisible();
    await dialog.getByRole('button', { name: /Server-Einstellungen/ }).click();
    await expect(dialog.getByLabel('Server für den Versand (SMTP)')).toHaveValue('mail.your-server.de');

    await dialog.getByRole('button', { name: 'Verbindung testen' }).click();
    await expect(dialog.getByRole('status')).toContainText('Versand: ok · Abruf: ok');
    expect(mitschrift.tests[0]).toMatchObject({ id: null, benutzername: 'max@musterbetrieb.example', smtpHost: 'mail.your-server.de' });
    await designPruefung(page, info, 'postfach-dialog-neu', { primaerAktion: dialog.getByRole('button', { name: 'Speichern' }) });

    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(liste.getByText('max@musterbetrieb.example')).toBeVisible();
    expect(mitschrift.angelegt[0]).toMatchObject({
        emailAdresse: 'max@musterbetrieb.example', hauptpostfach: false, benutzername: 'max@musterbetrieb.example',
        smtpHost: 'mail.your-server.de', smtpPort: 465, imapHost: 'mail.your-server.de', imapPort: 993,
    });
});

test('Hauptpostfach wechseln: der Haken wandert, das alte verliert ihn', async ({ page }, info) => {
    const mitschrift = await stubbeEinstellungen(page);
    await page.goto('/einstellungen#email');
    const liste = page.getByRole('list', { name: 'Postfächer' });
    await expect(liste.getByRole('listitem')).toHaveCount(2);

    await page.getByRole('button', { name: 'Postfach rechnungen@musterbetrieb.example bearbeiten' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByPlaceholder('✓ gesetzt – leer lassen = unverändert')).toBeVisible();
    await dialog.getByRole('checkbox', { name: /^Hauptpostfach/ }).check();
    await designPruefung(page, info, 'postfach-dialog-bearbeiten', { primaerAktion: dialog.getByRole('button', { name: 'Speichern' }) });
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    expect(mitschrift.geaendert[0]).toMatchObject({ hauptpostfach: true, passwort: null });
    const rechnungenZeile = liste.getByRole('listitem').filter({ hasText: 'rechnungen@musterbetrieb.example' });
    const infoZeile = liste.getByRole('listitem').filter({ hasText: 'info@musterbetrieb.example' });
    await expect(rechnungenZeile.getByText('Hauptpostfach', { exact: true })).toBeVisible();
    await expect(infoZeile.getByText('Hauptpostfach', { exact: true })).toHaveCount(0);
    // Das neue Hauptpostfach lässt sich nicht löschen, das alte jetzt schon.
    await expect(page.getByRole('button', { name: 'Postfach rechnungen@musterbetrieb.example löschen' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Postfach info@musterbetrieb.example löschen' })).toBeEnabled();
});

test('Läuft aus: altes Postfach auslaufen lassen – Request und Schild in der Liste', async ({ page }, info) => {
    const mitschrift = await stubbeEinstellungen(page);
    await page.goto('/einstellungen#email');
    const liste = page.getByRole('list', { name: 'Postfächer' });
    const rechnungenZeile = liste.getByRole('listitem').filter({ hasText: 'rechnungen@musterbetrieb.example' });

    // Ein weiteres, altes Postfach anlegen (das Rechnungs-Postfach selbst darf nicht auslaufen).
    await page.getByRole('button', { name: 'Neues Postfach' }).click();
    let dialog = page.getByRole('dialog');
    await dialog.getByLabel('E-Mail-Adresse *').fill('alt@musterbetrieb.example');
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    const altZeile = liste.getByRole('listitem').filter({ hasText: 'alt@musterbetrieb.example' });
    await expect(altZeile.getByText('läuft aus', { exact: true })).toHaveCount(0);

    // Beim Rechnungs-Postfach ist der Schalter gesperrt und erklärt warum.
    await page.getByRole('button', { name: 'Postfach rechnungen@musterbetrieb.example bearbeiten' }).click();
    dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('checkbox', { name: /^Läuft aus/ })).toBeDisabled();
    await expect(dialog.getByText('Das Postfach für Rechnungen & Mahnungen kann nicht auslaufen.')).toBeVisible();
    await dialog.getByRole('button', { name: 'Abbrechen' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    await page.getByRole('button', { name: 'Postfach alt@musterbetrieb.example bearbeiten' }).click();
    dialog = page.getByRole('dialog');
    await dialog.getByRole('checkbox', { name: /^Läuft aus/ }).check();
    await expect(dialog.getByRole('checkbox', { name: /^Hauptpostfach/ })).toBeDisabled();
    await expect(dialog.getByRole('checkbox', { name: /^Für Rechnungen & Mahnungen/ })).toBeDisabled();
    await dialog.getByRole('checkbox', { name: /^Läuft aus/ }).scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'postfach-dialog-laeuft-aus', { primaerAktion: dialog.getByRole('button', { name: 'Speichern' }) });
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    expect(mitschrift.geaendert.at(-1)).toMatchObject({ emailAdresse: 'alt@musterbetrieb.example', laeuftAus: true });
    await expect(altZeile.getByText('läuft aus', { exact: true })).toBeVisible();
    await expect(rechnungenZeile.getByText('läuft aus', { exact: true })).toHaveCount(0);
    await designPruefung(page, info, 'postfaecher-liste-laeuft-aus', { primaerAktion: page.getByRole('button', { name: 'Neues Postfach' }) });
});
