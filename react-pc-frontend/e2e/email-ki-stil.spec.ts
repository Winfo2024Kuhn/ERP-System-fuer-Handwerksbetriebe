import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { json, stubbeEmailCenter } from './hilfen/postfaecher';

/**
 * „KI-Optimierung“ im E-Mail-Formular fragt vor dem Formulieren nach dem Stil.
 * „Ton des Verlaufs übernehmen“ gibt es nur beim Antworten (zitierte Mail im Editor).
 * Nur Dummy-Daten (DSGVO).
 */

type KiAnfrage = { body: string; context: string | null; stil?: string };

async function stubbeKi(page: Page, antwort: { status: number; body: unknown }) {
    const anfragen: KiAnfrage[] = [];
    // Nach stubbeEmailCenter registriert – gewinnt also für diesen Pfad.
    await page.route('**/api/email/beautify', route => {
        anfragen.push(route.request().postDataJSON() as KiAnfrage);
        return json(route, antwort.body, antwort.status);
    });
    return anfragen;
}

/** Schreibt eigenen Text vor Signatur und Zitat, wie es der Nutzer tun würde. */
async function eigenenTextVoranstellen(page: Page, text: string) {
    const editor = page.getByRole('textbox', { name: 'Nachricht' });
    await editor.evaluate((el, t) => {
        const absatz = document.createElement('p');
        absatz.textContent = t;
        el.prepend(absatz);
        el.dispatchEvent(new Event('input', { bubbles: true }));
    }, text);
    return editor;
}

test('Antworten: Stil „Ton des Verlaufs übernehmen“ per Tastatur wählen, Vorschlag ersetzt nur den eigenen Text', async ({ page }, info) => {
    await stubbeEmailCenter(page);
    const anfragen = await stubbeKi(page, { status: 200, body: { body: 'Gern machen wir Ihnen ein Angebot.' } });
    await page.goto('/emails/inbox/701');
    await page.getByRole('button', { name: 'Antworten', exact: true }).first().click();
    const editor = page.getByRole('textbox', { name: 'Nachricht' });
    await expect(editor).toContainText('Können Sie uns ein Angebot machen?');
    await eigenenTextVoranstellen(page, 'ja machen wir');

    const knopf = page.getByRole('button', { name: 'KI-Optimierung' });
    await knopf.click();
    const menue = page.getByRole('menu');
    await expect(menue.getByRole('menuitem')).toHaveText([
        /Nur Rechtschreibung.*Fehler korrigieren, Stil bleibt/,
        /Förmlicher/,
        /Lockerer/,
        /Ton des Verlaufs übernehmen.*Anrede und Ton wie in der letzten Mail/,
    ]);
    await designPruefung(page, info, 'email-ki-stil-menue-antwort');

    // Menü per Tastatur: ans Ende springen (Verlauf) und mit Enter wählen
    await page.keyboard.press('End');
    await expect(menue.getByRole('menuitem', { name: /Ton des Verlaufs/ })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(menue).toHaveCount(0);

    await expect(editor).toContainText('Gern machen wir Ihnen ein Angebot.');
    await expect(editor).not.toContainText('ja machen wir');
    await expect(editor).toContainText('Können Sie uns ein Angebot machen?');
    expect(anfragen).toHaveLength(1);
    expect(anfragen[0]).toMatchObject({ body: 'ja machen wir', stil: 'VERLAUF' });
    expect(anfragen[0].context).toContain('Können Sie uns ein Angebot machen?');
});

test('Neue Mail: ohne Verlauf nur drei Stile, Info-Knopf erklärt, Ablehnung erscheint als Meldung', async ({ page }, info) => {
    await stubbeEmailCenter(page);
    const anfragen = await stubbeKi(page, { status: 400, body: { message: 'Unbekannter Schreibstil.' } });
    await page.goto('/emails/inbox');
    await page.getByRole('button', { name: 'Neue E-Mail' }).click();
    await eigenenTextVoranstellen(page, 'hallo, termin passt');

    await page.getByRole('button', { name: 'Mehr zu: Text mit KI überarbeiten' }).click();
    await expect(page.getByText(/Ihr Text wird an die KI geschickt/)).toBeVisible();
    await designPruefung(page, info, 'email-ki-stil-info');
    await page.keyboard.press('Escape');
    await expect(page.getByText(/Ihr Text wird an die KI geschickt/)).toHaveCount(0);

    await page.getByRole('button', { name: 'KI-Optimierung' }).click();
    const menue = page.getByRole('menu');
    await expect(menue.getByRole('menuitem')).toHaveCount(3);
    await expect(menue.getByRole('menuitem', { name: /Ton des Verlaufs/ })).toHaveCount(0);
    await designPruefung(page, info, 'email-ki-stil-menue-neu');
    await menue.getByRole('menuitem', { name: /Lockerer/ }).click();

    await expect(page.getByText('Unbekannter Schreibstil.')).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Nachricht' })).toContainText('hallo, termin passt');
    expect(anfragen).toEqual([{ body: 'hallo, termin passt', context: null, stil: 'LOCKER' }]);
});
