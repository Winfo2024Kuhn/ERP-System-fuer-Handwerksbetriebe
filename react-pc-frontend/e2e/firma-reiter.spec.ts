import { type Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

// Sichert die Firma-Seite nach dem Aufteilen von FirmaEditor.tsx (Issue #425):
// alle Reiter, Unterreiter und der Steuerberater-Dialog erscheinen wie vorher.

const JAHR = new Date().getFullYear();

const FIRMA = {
    id: 1, firmenname: 'Mustermann Metallbau GmbH', geschaeftsfuehrer: 'Max Mustermann',
    strasse: 'Musterstraße 1', plz: '97070', ort: 'Würzburg', bundesland: 'BY',
    telefon: '0931 123456', email: 'info@example.org', website: 'www.example.org',
    steuernummer: '123/456/78901', ustIdNr: 'DE123456789', bankName: 'Musterbank', iban: 'DE00 0000 0000 0000 0000 00', bic: 'MUSTDEFFXXX',
    firmenfarbe: '#500010', logoDateiname: '', mahnverfahrenAktiv: true,
    tageBisZahlungserinnerung: 7, tageBisErsteMahnung: 14, tageBisZweiteMahnung: 14, mahnverfahrenNeuesZahlungszielTage: 7,
    gewerkId: 4, gewerkName: 'Metallbau', bgName: 'BGHM', bgSatzVorschlag: 3.6, bgSatzOverride: null,
};

const STEUERBERATER = [{
    id: 3, name: 'Kanzlei Mustermann', email: 'kanzlei@example.org', telefon: '0931 654321', ansprechpartner: 'Erika Musterfrau',
    autoProcessEmails: true, aktiv: true, notizen: '', gueltigAb: `${JAHR}-01-01`, gueltigBis: null,
    weitereEmails: ['lohn@example.org'],
    ansprechpartnerListe: [{ id: 1, anrede: 'FRAU', vorname: 'Erika', nachname: 'Musterfrau', email: 'erika@example.org', telefon: '', istLohnAnsprechpartner: true }],
}];

const LOHNABRECHNUNGEN = [{
    id: 9, mitarbeiterId: 1, mitarbeiterName: 'Max Mustermann', steuerberaterId: 3, steuerberaterName: 'Kanzlei Mustermann',
    jahr: JAHR, monat: 3, originalDateiname: 'lohn.pdf', downloadUrl: '/api/lohnabrechnungen/9/download',
    bruttolohn: 3200, nettolohn: 2100.5, importDatum: `${JAHR}-04-02`, status: 'NEU',
}];

const BWA = [{
    id: 5, typ: 'MONATLICH', jahr: JAHR, monat: 2, originalDateiname: 'bwa.pdf', pdfUrl: '/api/bwa/5/pdf', uploadDatum: `${JAHR}-03-10`,
    aiConfidence: 0.9, analysiert: true, freigegeben: true, gesamtGemeinkosten: 12345.67, steuerberaterName: 'Kanzlei Mustermann',
}];

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

test('Firma: alle Reiter, Steuerberater-Unterreiter und Dialog wie gewohnt', async ({ page }, testInfo) => {
    const aufrufe: string[] = [];
    await page.route('**/api/**', route => {
        const pfad = new URL(route.request().url()).pathname;
        aufrufe.push(`${route.request().method()} ${pfad}`);
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'test', displayName: 'Test Büro', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/firma') return json(route, FIRMA);
        if (pfad === '/api/firma/steuerberater') return json(route, STEUERBERATER);
        if (pfad === '/api/lohn-stammdaten/gewerke') return json(route, [{ id: 4, name: 'Metallbau', bgName: 'BGHM', bgSatzProzent: 3.6, aktiv: true }]);
        if (pfad === '/api/lohnabrechnungen/jahre' || pfad === '/api/bwa/jahre') return json(route, [JAHR]);
        if (pfad === `/api/lohnabrechnungen/jahr/${JAHR}`) return json(route, LOHNABRECHNUNGEN);
        if (pfad === `/api/bwa/jahr/${JAHR}`) return json(route, BWA);
        return json(route, []);
    });
    await page.goto('/firma');

    const speichern = page.getByRole('button', { name: 'Speichern', exact: true });
    await expect(page.getByRole('textbox', { name: 'Tage nach der Zahlungserinnerung' })).toHaveValue('14');
    await designPruefung(page, testInfo, 'firma-reiter-firmendaten', { ganzeSeite: true });
    await speichern.scrollIntoViewIfNeeded();
    await designPruefung(page, testInfo, 'firma-reiter-mahnverfahren', { primaerAktion: speichern });

    await page.getByRole('button', { name: /Unfallversicherung/ }).click();
    await expect(page.getByRole('heading', { name: 'Gewerk & Unfallversicherung' })).toBeVisible();
    await designPruefung(page, testInfo, 'firma-reiter-unfallversicherung', { primaerAktion: speichern });

    await page.getByRole('button', { name: 'Steuerberater (1)' }).click();
    await expect(page.getByRole('heading', { name: 'Kanzlei Mustermann' })).toBeVisible();
    await designPruefung(page, testInfo, 'firma-reiter-steuerberater', { primaerAktion: page.getByRole('button', { name: 'Neuer Steuerberater' }) });

    await page.getByRole('button', { name: 'Lohnabrechnungen im System' }).click();
    await expect(page.getByText('Max Mustermann')).toBeVisible();
    await expect(page.getByText('1 Dokumente gefunden')).toBeVisible();
    await designPruefung(page, testInfo, 'firma-reiter-lohnabrechnungen');

    await page.getByRole('button', { name: 'BWA / Auswertungen' }).click();
    await expect(page.getByRole('heading', { name: `BWA 2/${JAHR}` })).toBeVisible();
    await designPruefung(page, testInfo, 'firma-reiter-bwa');

    await page.getByRole('button', { name: 'Kontakte' }).click();
    await page.getByRole('button', { name: 'Neuer Steuerberater' }).click();
    const dialog = page.getByRole('dialog').filter({ hasText: 'Neuer Steuerberater' });
    await dialog.getByRole('button', { name: 'Ansprechpartner hinzufügen' }).click();
    await expect(dialog.getByText('Ansprechpartner 1')).toBeVisible();
    await designPruefung(page, testInfo, 'firma-steuerberater-dialog', { primaerAktion: dialog.getByRole('button', { name: 'Speichern' }) });

    expect(aufrufe).toContain(`GET /api/lohnabrechnungen/jahr/${JAHR}`);
    expect(aufrufe).toContain(`GET /api/bwa/jahr/${JAHR}`);
});
