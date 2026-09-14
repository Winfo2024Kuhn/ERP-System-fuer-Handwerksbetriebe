import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

const BELEG_ID = 501;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const SACHKONTEN = [
    { id: 1, nummer: '4930', bezeichnung: 'Bürobedarf', kontoTyp: 'AUFWAND', beschreibung: null, aktiv: true, sortierung: 1 },
    { id: 2, nummer: '8400', bezeichnung: 'Erlöse Leistungen', kontoTyp: 'ERTRAG', beschreibung: null, aktiv: true, sortierung: 1 },
    { id: 3, nummer: '1890', bezeichnung: 'Privatentnahme', kontoTyp: 'PRIVAT', beschreibung: null, aktiv: true, sortierung: 1 },
];

const ZAHLUNGSARTEN = [
    { id: 1, bezeichnung: 'Bar', aktiv: true, sortierung: 1 },
    { id: 2, bezeichnung: 'EC-Karte', aktiv: true, sortierung: 2 },
];

const BELEG = {
    id: BELEG_ID,
    belegKategorie: 'KASSE_AUSGABE',
    dokumentTyp: null,
    istUmbuchung: false,
    status: 'NEU',
    kiAnalyseStatus: 'DONE',
    belegDatum: '2026-09-01',
    belegNummer: null,
    beschreibung: 'Werkzeugkauf Baumarkt',
    betragNetto: 42.02,
    betragBrutto: 50.00,
    mwstSatz: 19,
    zahlungsart: 'Bar',
    lieferantId: null,
    lieferantName: null,
    sachkontoId: null,
    sachkontoBezeichnung: null,
    sachkontoNummer: null,
    sachkontoTyp: null,
    kiVorgeschlagenerLieferant: null,
    kiConfidence: null,
    kiVorgeschlagenerKostenstelleId: null,
    kiVorgeschlagenerKostenstelleBezeichnung: null,
    kiVorgeschlagenerSachkontoId: null,
    kiVorgeschlagenerSachkontoBezeichnung: null,
    kiKostenkontoConfidence: null,
    kiKostenkontoBegruendung: null,
    kiFehlerText: null,
    originalDateiname: 'quittung-baumarkt.jpg',
    mimeType: 'image/jpeg',
    uploadDatum: '2026-09-01T08:00:00',
    uploadedByName: 'Max Mustermann',
    validiertAm: null,
    validiertVonName: null,
    notiz: null,
    eingangsrechnungId: null,
    aufteilungsModus: null,
    betragFirmaNetto: null,
    betragFirmaBrutto: null,
    betragFirmaMwst: null,
    positionen: null,
    kostenstellenSplits: null,
    laufendeNummer: null,
    festgeschrieben: false,
    festgeschriebenAm: null,
    stornoFuerBelegId: null,
    storniertDurchBelegId: null,
    storniertAm: null,
    stornoGrund: null,
};

const KASSENBUCH = {
    saldoStart: 100, saldoEnde: 280,
    summeEinnahmen: 250, summeAusgaben: 50,
    summePrivateinlagen: 0, summePrivatentnahmen: 20,
    letzterAbschluss: null, offeneBewegungen: 4,
    bewegungen: [
        { belegId: 600, datum: '2026-09-01', kategorie: 'KASSE_EINNAHME', beschreibung: 'Kundenzahlung', betrag: 200, saldoNachher: 300, laufendeNummer: 91, sachkontoNummer: '8400' },
        { belegId: BELEG_ID, datum: '2026-09-02', kategorie: 'KASSE_AUSGABE', beschreibung: 'Werkzeugkauf Baumarkt', lieferantName: 'Musterbaustoffe GmbH', betrag: -50, saldoNachher: 250, storniertDurchBelegId: 602, sachkontoNummer: '4930' },
        { belegId: 602, datum: '2026-09-03', kategorie: 'KASSE_EINNAHME', beschreibung: 'Werkzeug zurückgegeben', betrag: 50, saldoNachher: 300, stornoFuerBelegId: BELEG_ID },
        { belegId: 603, datum: '2026-09-04', kategorie: 'PRIVATENTNAHME', beschreibung: 'Geld privat entnommen', betrag: -20, saldoNachher: 280 },
        { belegId: 604, datum: '2026-09-05', kategorie: 'KASSE_EINNAHME', beschreibung: 'Nullbuchung', betrag: 0, saldoNachher: 280 },
    ],
};

const KASSE_EINSTELLUNG = {
    id: 1,
    mindestbestand: 50.00,
    ehegattengehaltAktiv: false,
    ehegattengehaltBetrag: null,
    ehegattengehaltTag: null,
    ehegattengehaltEmpfaengerName: null,
    privateinlageSachkontoId: null,
};

async function stub(page: Page, saldo = { saldo: 777, mindestbestand: 50 }) {
    const anfragen = { saldoAbrufe: 0 };
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const path = url.pathname;
        const method = route.request().method();

        if (path === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'max.mustermann', displayName: 'Max Mustermann',
                active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
            });
        }
        if (path === '/api/notifications/summary') {
            return json(route, { totalCount: 0, categories: [], recentItems: [] });
        }
        if (path === '/api/buchhaltung/belege' && method === 'GET') {
            return json(route, [BELEG]);
        }
        if (path === `/api/buchhaltung/belege/${BELEG_ID}`) {
            return json(route, BELEG);
        }
        if (path === '/api/buchhaltung/sachkonten') {
            return json(route, SACHKONTEN);
        }
        if (path === '/api/buchhaltung/zahlungsarten') {
            return json(route, ZAHLUNGSARTEN);
        }
        if (path === '/api/buchhaltung/kassenbuch') {
            return json(route, KASSENBUCH);
        }
        if (path === '/api/buchhaltung/kasse/saldo') {
            anfragen.saldoAbrufe++;
            return json(route, saldo);
        }
        if (path === '/api/buchhaltung/kasse/bank-abhebung' && method === 'POST') {
            saldo.saldo += route.request().postDataJSON().betrag;
            return json(route, {});
        }
        if (path === '/api/buchhaltung/kassenbuch/buchungen' && method === 'POST') {
            const raw = route.request().postData() ?? '';
            const betrag = Number(/"betragBrutto"\s*:\s*([\d.]+)/.exec(raw)?.[1] ?? 0);
            if (/"art"\s*:\s*"VON_BANK_GEHOLT"/.test(raw)) saldo.saldo += betrag;
            return json(route, { id: 999 });
        }
        if (path === '/api/buchhaltung/kasse/einstellung') {
            return json(route, KASSE_EINSTELLUNG);
        }
        return json(route, []);
    });
    return anfragen;
}

async function oeffneKassenbuch(page: Page, saldo?: { saldo: number; mindestbestand: number }) {
    const anfragen = await stub(page, saldo);
    await page.goto('/belege-kasse');
    await page.getByRole('button', { name: 'Kassenbuch', exact: true }).click();
    return anfragen;
}

test('Journal zeigt sieben Spalten, Serverbestände und Stornos und öffnet einen Beleg', async ({ page }, info) => {
    await oeffneKassenbuch(page);
    const journal = page.getByRole('table', { name: 'Kassenbuch' });
    await expect(journal.getByRole('columnheader')).toHaveText([
        'Nr.', 'Datum', 'Was', 'Beleg', 'Einnahme', 'Ausgabe', 'Bestand danach',
    ]);
    await expect(page.getByText(/^(Soll|Haben)$/)).toHaveCount(0);
    await expect(journal.locator('tbody tr')).toHaveCount(5);
    await expect(journal.getByText('Storniert', { exact: true })).toBeVisible();
    await expect(journal.getByText('Gegenbuchung', { exact: true })).toBeVisible();
    await expect(journal.getByText('Konto 4930', { exact: true })).toBeVisible();
    await expect(journal.locator('tbody tr').first().getByRole('cell').first()).toHaveText('91');
    await expect(journal.locator('tbody tr').nth(1).getByRole('cell').first()).toHaveText('(2)');
    const letzterBestand = journal.locator('tbody tr').last().getByRole('cell').last();
    await expect(letzterBestand).toHaveText('280,00 €');
    await expect(page.getByTestId('bestand-am-ende')).toHaveText(await letzterBestand.innerText());
    await expect(page.getByTestId('kasse-jetzt')).toHaveText('777,00 €');
    await designPruefung(page, info, 'kassenbuch-journal-offen');
    await journal.scrollIntoViewIfNeeded();
    await expect(journal.locator('tbody tr').first()).toBeInViewport();
    await expect(journal.locator('tbody tr').last()).toBeInViewport();
    await designPruefung(page, info, 'kassenbuch-journal-tabelle');

    await page.getByRole('button', { name: /Werkzeugkauf Baumarkt/ }).click();
    await expect(page.getByRole('heading', { name: 'Beleg prüfen & validieren', exact: true })).toBeVisible();
    await designPruefung(page, info, 'kassenbuch-journal-beleg', {
        primaerAktion: page.getByRole('button', { name: 'Prüfen & Übernehmen' }),
    });
});

test('Erklärung bleibt ausgeblendet und kann wieder geöffnet werden; Mehr dazu zeigt die Anleitung', async ({ page }, info) => {
    await oeffneKassenbuch(page);
    const ersterSatz = page.getByText('Jede Barbewegung sofort eintragen — auch Bank-Abhebung und eigenes Geld.');
    await expect(ersterSatz).toBeVisible();
    await page.getByRole('button', { name: 'Mehr dazu', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'So funktioniert die Kasse' });
    await expect(dialog).toBeVisible();
    for (const titel of ['Was gehört ins Kassenbuch', 'Was passiert bei jeder Buchung', 'Wenn kein Beleg da ist',
        'Die Kasse darf nie unter null', 'Kasse zählen und Monat abschließen', 'Falsch gebucht — was jetzt',
        'Was der Steuerberater bekommt']) {
        await expect(dialog.getByRole('heading', { name: titel, exact: true })).toBeVisible();
    }
    await designPruefung(page, info, 'kassenbuch-journal-anleitung');
    const letztesKapitel = dialog.getByRole('heading', { name: 'Was der Steuerberater bekommt', exact: true });
    await letztesKapitel.scrollIntoViewIfNeeded();
    await expect(letztesKapitel).toBeInViewport();
    await expect(dialog.getByRole('button', { name: 'Verstanden', exact: true })).toBeInViewport();
    await designPruefung(page, info, 'kassenbuch-journal-anleitung-ende');
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'Mehr dazu', exact: true })).toBeFocused();
    await page.getByRole('button', { name: 'Erklärung ausblenden' }).click();
    await expect(ersterSatz).not.toBeVisible();
    const toggle = page.getByRole('button', { name: 'So funktioniert die Kasse', exact: true });
    expect(await toggle.evaluate(el => document.getElementById(el.getAttribute('aria-controls')!)?.hidden)).toBe(true);
    await designPruefung(page, info, 'kassenbuch-journal-erklaerung-zu');
    await page.reload();
    await page.getByRole('button', { name: 'Kassenbuch', exact: true }).click();
    await expect(ersterSatz).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'So funktioniert die Kasse', exact: true })).toHaveAttribute('aria-expanded', 'false');
    await page.getByRole('button', { name: 'So funktioniert die Kasse', exact: true }).click();
    await expect(ersterSatz).toBeVisible();
    await designPruefung(page, info, 'kassenbuch-journal-erklaerung-wieder-offen');
});

test('Suche erhält Nummern und Bestände, lange Wörter bleiben lesbar', async ({ page }, info) => {
    await oeffneKassenbuch(page);
    const suche = page.getByPlaceholder('Beschreibung, Lieferant, Art…');
    await suche.fill('Musterbaustoffe');
    const zeile = page.getByRole('table', { name: 'Kassenbuch' }).locator('tbody tr');
    await expect(zeile).toHaveCount(1);
    await expect(zeile.getByRole('cell').first()).toHaveText('(2)');
    await expect(zeile.getByRole('cell').last()).toHaveText('250,00 €');
    await expect(page.getByTestId('bestand-am-ende')).toHaveText('280,00 €');
    await expect(page.getByText(/Summen und Bestände.*ganzen Zeitraum/)).toBeVisible();
    await zeile.scrollIntoViewIfNeeded();
    await expect(zeile).toBeInViewport();
    await designPruefung(page, info, 'kassenbuch-journal-suche');
    await suche.fill('unbekannt');
    await expect(page.getByText('Keine passenden Buchungen gefunden.')).toBeVisible();
    await designPruefung(page, info, 'kassenbuch-journal-keine-treffer');
    await suche.fill('');
    const lang = 'Musterbaustoffe'.repeat(12);
    await page.route('**/api/buchhaltung/kassenbuch?**', route => json(route, {
        ...KASSENBUCH, bewegungen: KASSENBUCH.bewegungen.map((b, i) => i === 0 ? { ...b, beschreibung: lang, lieferantName: lang } : b),
    }));
    await page.getByRole('button', { name: 'Letzter Monat', exact: true }).click();
    const langerText = page.getByRole('table', { name: 'Kassenbuch' }).getByText(lang, { exact: true }).first();
    await langerText.scrollIntoViewIfNeeded();
    await expect(langerText).toBeInViewport();
    const masse = await langerText.evaluate(el => ({ innen: el.clientWidth, inhalt: el.scrollWidth }));
    expect(masse.inhalt).toBeLessThanOrEqual(masse.innen);
    await designPruefung(page, info, 'kassenbuch-journal-lange-woerter');
});


test('ein gemeinsamer Kassenstand warnt und aktualisiert sich nach einer Bank-Abhebung', async ({ page }, info) => {
    const anfragen = await oeffneKassenbuch(page, { saldo: 20, mindestbestand: 50 });
    const stand = page.getByTestId('kasse-jetzt');
    await expect(stand).toHaveText('20,00 €');
    expect(anfragen.saldoAbrufe).toBe(1);
    await expect(page.getByText('Aktueller Kassenstand', { exact: true })).toHaveCount(0);
    await expect(page.getByText('20,00 €', { exact: true })).toHaveCount(2); // Journal-Ausgabe plus aktueller Stand
    const anzeige = stand.locator('..');
    await expect(anzeige.getByText('Mindestbestand: 50,00 €', { exact: true })).toBeVisible();
    await expect(anzeige.getByText('unter Mindestbestand', { exact: true })).toBeVisible();
    await stand.scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'kassenbuch-journal-mindestbestand');
    await page.getByRole('button', { name: 'Neue Buchung', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: /^Geld von der Bank geholt/ }).click();
    await dialog.getByRole('textbox', { name: 'Betrag (€)', exact: true }).fill('40');
    expect(anfragen.saldoAbrufe).toBe(1);
    await designPruefung(page, info, 'kassenbuch-journal-gemeinsamer-stand-dialog');
    await dialog.getByRole('button', { name: 'Buchen', exact: true }).click();
    await expect(stand).toHaveText('60,00 €');
    await expect(anzeige.getByText('unter Mindestbestand', { exact: true })).toHaveCount(0);
    expect(anfragen.saldoAbrufe).toBe(2);
    await stand.scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'kassenbuch-journal-mindestbestand-behoben');
});

test('gewählter Lieferant ist auch ohne KI-Lesung der Belegtitel', async ({ page }, info) => {
    await stub(page);
    await page.route('**/api/buchhaltung/belege', route => json(route, [{
        ...BELEG, lieferantId: 7, lieferantName: 'Musterbaustoffe GmbH', kiVorgeschlagenerLieferant: null,
    }]));
    await page.goto('/belege-kasse');
    const beleg = page.getByRole('button', { name: /Musterbaustoffe GmbH.*Zu prüfen/ });
    await expect(beleg.locator('span.font-semibold').first()).toHaveText('Musterbaustoffe GmbH');
    await expect(beleg).not.toContainText('quittung-baumarkt.jpg');
    await designPruefung(page, info, 'kassenbuch-gewaehlter-lieferant');
});
