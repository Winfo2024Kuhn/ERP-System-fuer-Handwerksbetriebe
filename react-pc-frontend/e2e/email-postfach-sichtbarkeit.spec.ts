import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { json, stubbeEmailCenter } from './hilfen/postfaecher';

/**
 * Etappe 2 der Postfach-Spec – Sichtbarkeit:
 *   - Einstellungen → Berechtigungen: Postfach auf „Nur bestimmte“, Abteilung und Benutzer
 *     anhaken, Request prüfen; Einstellungen → E-Mail: Zeile „Sichtbar: …“ und Link dorthin.
 *   - E-Mail-Center: Senden über ein nicht erlaubtes Postfach → verständliche Meldung.
 * Nur Dummy-Daten, /api gestubbt (mit Zustand).
 */

interface Postfach {
    id: number; emailAdresse: string; anzeigename: string | null; aktiv: boolean; sortierung: number;
    hauptpostfach: boolean; fuerGeschaeftsdokumente: boolean; benutzername: string | null; passwortGesetzt: boolean;
    smtpHost: string | null; smtpPort: number | null; imapHost: string | null; imapPort: number | null;
    abrufAktiv: boolean; letzterAbrufAm: string | null; letzterAbrufFehler: string | null;
    zugewieseneBenutzer: { id: number; displayName: string }[];
    sichtbarFuerAlle: boolean;
    sichtbarFuerAbteilungen: { id: number; name: string }[];
    sichtbarFuerBenutzer: { id: number; displayName: string }[];
}

const ABTEILUNGEN = [
    { abteilungId: 2, abteilungName: 'Büro', berechtigungen: [] },
    { abteilungId: 3, abteilungName: 'Werkstatt', berechtigungen: [] },
    { abteilungId: 4, abteilungName: 'Montage', berechtigungen: [] },
];
const BENUTZER = [
    { id: 7, displayName: 'Max Mustermann', active: true },
    { id: 8, displayName: 'Erika Musterfrau', active: true },
    { id: 9, displayName: 'Moritz Muster', active: true },
];

function startbestand(): Postfach[] {
    const basis = {
        aktiv: true, fuerGeschaeftsdokumente: false, passwortGesetzt: true,
        smtpHost: 'mail.your-server.de', smtpPort: 465, imapHost: 'mail.your-server.de', imapPort: 993,
        abrufAktiv: true, letzterAbrufAm: null, letzterAbrufFehler: null,
        sichtbarFuerAlle: true, sichtbarFuerAbteilungen: [], sichtbarFuerBenutzer: [],
    };
    return [
        {
            ...basis, id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', sortierung: 0,
            hauptpostfach: true, benutzername: 'info@musterbetrieb.example', zugewieseneBenutzer: [],
        },
        {
            ...basis, id: 5, emailAdresse: 'max@musterbetrieb.example', anzeigename: 'Max Mustermann', sortierung: 10,
            hauptpostfach: false, benutzername: 'max@musterbetrieb.example',
            zugewieseneBenutzer: [{ id: 7, displayName: 'Max Mustermann' }],
        },
        {
            ...basis, id: 4, emailAdresse: 'rechnungen@musterbetrieb.example', anzeigename: null, sortierung: 20,
            hauptpostfach: false, fuerGeschaeftsdokumente: true, benutzername: 'rechnungen@musterbetrieb.example',
            zugewieseneBenutzer: [],
        },
    ];
}

async function stubbeEinstellungen(page: Page) {
    let bestand = startbestand();
    const sichtbarkeit: Record<string, unknown>[] = [];
    const postfachGespeichert: Record<string, unknown>[] = [];
    await page.route('**/api/**', async route => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        const methode = anfrage.method();
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, displayName: 'Max Mustermann', username: 'admin', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        }
        if (pfad === '/api/postfaecher' && methode === 'GET') return json(route, bestand);
        if (pfad === '/api/abteilungen/berechtigungen') return json(route, ABTEILUNGEN);
        if (pfad === '/api/frontend-users') return json(route, BENUTZER);
        const sicht = pfad.match(/^\/api\/postfaecher\/(\d+)\/sichtbarkeit$/);
        if (sicht && methode === 'PUT') {
            const body = anfrage.postDataJSON() as { sichtbarFuerAlle: boolean; abteilungIds: number[]; benutzerIds: number[] };
            sichtbarkeit.push(body);
            const id = Number(sicht[1]);
            bestand = bestand.map(p => (p.id !== id ? p : {
                ...p,
                sichtbarFuerAlle: body.sichtbarFuerAlle,
                sichtbarFuerAbteilungen: ABTEILUNGEN.filter(a => body.abteilungIds.includes(a.abteilungId))
                    .map(a => ({ id: a.abteilungId, name: a.abteilungName })),
                sichtbarFuerBenutzer: BENUTZER.filter(b => body.benutzerIds.includes(b.id))
                    .map(b => ({ id: b.id, displayName: b.displayName })),
            }));
            return json(route, bestand.find(p => p.id === id));
        }
        const treffer = pfad.match(/^\/api\/postfaecher\/(\d+)$/);
        if (treffer && methode === 'PUT') {
            postfachGespeichert.push(anfrage.postDataJSON());
            return json(route, bestand.find(p => p.id === Number(treffer[1])));
        }
        return json(route, {});
    });
    return { sichtbarkeit, postfachGespeichert };
}

test('Sichtbarkeit unter Berechtigungen: Postfach auf „Nur bestimmte“, Abteilung + Benutzer anhaken, Liste zeigt „Sichtbar: …“', async ({ page }, info) => {
    const mitschrift = await stubbeEinstellungen(page);
    await page.goto('/einstellungen#email');

    const liste = page.getByRole('list', { name: 'Postfächer' });
    const maxZeile = liste.getByRole('listitem').filter({ hasText: 'max@musterbetrieb.example' });
    await expect(maxZeile.getByTestId('postfach-sichtbarkeit')).toContainText('Sichtbar: alle');

    // Im Postfach-Dialog gibt es keine Auswahl mehr, nur den Weg zu den Berechtigungen.
    await page.getByRole('button', { name: 'Postfach max@musterbetrieb.example bearbeiten' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByTestId('postfach-sichtbarkeit-hinweis'))
        .toHaveText('Wer dieses Postfach sehen darf, legen Sie unter Einstellungen → Berechtigungen fest.');
    await expect(dialog.getByRole('radio')).toHaveCount(0);
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(mitschrift.postfachGespeichert[0]).toMatchObject({ sichtbarFuerAlle: null, abteilungIds: null, benutzerIds: null });

    // Ungespeicherte Eingaben gehen beim Klick auf den Link nicht still verloren.
    await page.getByRole('button', { name: 'Postfach max@musterbetrieb.example bearbeiten' }).click();
    const postfachDialog = page.getByRole('dialog').filter({ hasText: 'Postfach bearbeiten' });
    await postfachDialog.getByLabel('Angezeigter Name').fill('Max Mustermann – Musterbetrieb');
    await postfachDialog.getByRole('link', { name: 'Einstellungen → Berechtigungen' }).click();
    await expect(page.getByText('Sie haben noch nicht gespeichert. Trotzdem zu den Berechtigungen wechseln?')).toBeVisible();
    await designPruefung(page, info, 'postfach-dialog-verwerfen-rueckfrage', { primaerAktion: page.getByRole('button', { name: 'Weiter bearbeiten' }) });
    await page.getByRole('button', { name: 'Weiter bearbeiten' }).click();
    await expect(page.getByText('Änderungen verwerfen?')).toHaveCount(0);
    await expect(postfachDialog.getByLabel('Angezeigter Name')).toHaveValue('Max Mustermann – Musterbetrieb');
    await expect(page.getByRole('tab', { name: /E-Mail/ })).toHaveAttribute('aria-selected', 'true');

    await postfachDialog.getByRole('link', { name: 'Einstellungen → Berechtigungen' }).click();
    await page.getByRole('button', { name: 'Verwerfen' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByRole('tab', { name: /Berechtigungen/ })).toHaveAttribute('aria-selected', 'true');

    const karte = page.getByRole('list', { name: 'Sichtbarkeit der Postfächer' });
    const haupt = karte.getByRole('listitem').filter({ hasText: 'info@musterbetrieb.example' });
    await expect(haupt.getByText('Das Hauptpostfach sieht jeder im Betrieb.')).toBeVisible();
    await expect(haupt.getByRole('radio')).toHaveCount(0);
    // Das Postfach für Rechnungen & Mahnungen sieht wie das Hauptpostfach immer jeder.
    const rechnungenZeile = karte.getByRole('listitem').filter({ hasText: 'rechnungen@musterbetrieb.example' });
    await expect(rechnungenZeile.getByText('Das Postfach für Rechnungen & Mahnungen sieht jeder im Betrieb.')).toBeVisible();
    await expect(rechnungenZeile.getByText('Rechnungen & Mahnungen', { exact: true })).toBeVisible();
    await expect(rechnungenZeile.getByRole('radio')).toHaveCount(0);
    await expect(rechnungenZeile.getByRole('button')).toHaveCount(0);

    const zeile = karte.getByRole('listitem').filter({ hasText: 'max@musterbetrieb.example' });
    const speichern = zeile.getByRole('button', { name: 'Sichtbarkeit für max@musterbetrieb.example speichern' });
    await expect(speichern).toBeDisabled();
    await zeile.getByRole('radio', { name: /Nur bestimmte/ }).check();
    await expect(zeile.getByText('Der Inhaber des Postfachs und Admins sehen es immer.')).toBeVisible();
    const abteilungen = zeile.getByRole('group', { name: 'Abteilungen' });
    const benutzer = zeile.getByRole('group', { name: 'Benutzer' });
    await abteilungen.getByRole('checkbox', { name: 'Büro' }).check();
    await benutzer.getByRole('checkbox', { name: 'Max Mustermann' }).check();
    await expect(abteilungen.getByText('1 gewählt')).toBeVisible();
    await expect(speichern).toBeEnabled();

    // Per Tastatur fokussieren, damit :focus-visible greift – der Ring darf nicht abgeschnitten werden.
    await abteilungen.getByRole('checkbox', { name: 'Büro' }).focus();
    await page.keyboard.press('Shift+Tab');
    await page.keyboard.press('Tab');
    await expect(abteilungen.getByRole('checkbox', { name: 'Büro' })).toBeFocused();
    // Der Speichern-Knopf steht unter den Häkchen der Zeile – so weit scrollt man beim Anhaken ohnehin.
    await speichern.scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'postfach-sichtbarkeit-berechtigungen', { primaerAktion: speichern });

    await speichern.click();
    await expect(page.getByText('Sichtbarkeit für max@musterbetrieb.example gespeichert.')).toBeVisible();
    await expect(speichern).toBeDisabled();
    expect(mitschrift.sichtbarkeit[0]).toEqual({ sichtbarFuerAlle: false, abteilungIds: [2], benutzerIds: [7] });

    // Zurück in der Postfach-Liste steht die neue Sichtbarkeit.
    await page.getByRole('tab', { name: /E-Mail/ }).click();
    await expect(maxZeile.getByTestId('postfach-sichtbarkeit')).toContainText('Sichtbar: Büro, Max Mustermann');
    const rechnungenListe = liste.getByRole('listitem').filter({ hasText: 'rechnungen@musterbetrieb.example' });
    await expect(rechnungenListe.getByTestId('postfach-sichtbarkeit')).toHaveText('Sichtbar: alle');
    await expect(rechnungenListe.getByRole('link')).toHaveCount(0);
    await designPruefung(page, info, 'postfach-sichtbarkeit-liste', { primaerAktion: page.getByRole('button', { name: 'Neues Postfach' }) });
    await maxZeile.getByRole('link', { name: /unter Berechtigungen ändern/ }).click();
    await expect(page.getByRole('tab', { name: /Berechtigungen/ })).toHaveAttribute('aria-selected', 'true');
});

test('E-Mail-Center: Senden über ein nicht erlaubtes Postfach zeigt die Meldung, das Formular bleibt offen', async ({ page }, info) => {
    const stub = await stubbeEmailCenter(page, {
        sendeAntwort: { status: 403, body: { message: 'Über dieses Postfach dürfen Sie nicht senden.' } },
    });
    await page.goto('/emails/inbox');
    await page.getByRole('button', { name: 'Neue E-Mail' }).click();

    await expect(page.getByRole('combobox', { name: 'Senden von' })).toContainText('max@musterbetrieb.example');
    await page.getByPlaceholder('Name, Firma oder E-Mail eingeben').fill('kunde@example.org');
    await page.getByLabel('Betreff').fill('Angebot Balkongeländer');
    await page.getByRole('button', { name: 'E-Mail senden' }).click();
    const trotzdem = page.getByRole('button', { name: 'Trotzdem senden' });
    if (await trotzdem.isVisible().catch(() => false)) await trotzdem.click();

    await expect.poll(() => stub.gesendet.length).toBe(1);
    expect(stub.gesendet[0].dto).toMatchObject({ postfachId: 7 });
    // Toast + Meldung im Formular; das Formular mit Betreff bleibt stehen.
    await expect(page.getByText('Über dieses Postfach dürfen Sie nicht senden.')).toHaveCount(2);
    await expect(page.getByLabel('Betreff')).toHaveValue('Angebot Balkongeländer');
    await expect(page.getByRole('button', { name: 'E-Mail senden' })).toBeEnabled();
    await designPruefung(page, info, 'postfach-senden-verboten', { primaerAktion: page.getByRole('button', { name: 'E-Mail senden' }) });
});
