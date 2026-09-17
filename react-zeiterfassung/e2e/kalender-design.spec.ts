import type { Page } from '@playwright/test'
import { expect, test } from './hilfen/test'
import { designPruefung } from './hilfen/design'

// Fester „Heute“-Zeitpunkt, damit Raster und Screenshots reproduzierbar sind.
const HEUTE = new Date(2026, 8, 17, 10, 0, 0)

// Dummy-Daten (DSGVO): nur Max Mustermann & Co.
const eintraege = [
    {
        id: 12, titel: 'Baustelle Musterstraße', beschreibung: 'Dachfenster einbauen, Material liegt bereit.', datum: '2026-09-17',
        startZeit: '09:00:00', endeZeit: '10:30:00', ganztaegig: false, farbe: '#7c3aed',
        projektId: 7, projektName: 'Dachsanierung Musterhaus', kundeId: 3, kundeName: 'Max Mustermann',
        lieferantId: null, lieferantName: null, anfrageId: null, anfrageBetreff: null,
        erstellerId: 2, erstellerName: 'Erika Musterfrau', teilnehmer: [{ id: 1, name: 'Max Mustermann' }, { id: 2, name: 'Erika Musterfrau' }],
    },
    {
        id: 13, titel: 'Abnahme Terrasse', beschreibung: null, datum: '2026-09-03',
        startZeit: '14:00:00', endeZeit: '15:00:00', ganztaegig: false, farbe: null,
        projektId: null, projektName: null, kundeId: 3, kundeName: 'Max Mustermann',
        lieferantId: null, lieferantName: null, anfrageId: null, anfrageBetreff: null,
        erstellerId: null, erstellerName: null, teilnehmer: [],
    },
    {
        id: 14, titel: 'Materiallieferung Fenster', beschreibung: null, datum: '2026-09-10',
        startZeit: null, endeZeit: null, ganztaegig: true, farbe: '#2563eb',
        projektId: null, projektName: null, kundeId: null, kundeName: null,
        lieferantId: 4, lieferantName: 'Musterbau GmbH', anfrageId: null, anfrageBetreff: null,
        erstellerId: null, erstellerName: null, teilnehmer: [],
    },
    {
        id: 15, titel: 'Besprechung Angebot', beschreibung: null, datum: '2026-09-22',
        startZeit: '11:00:00', endeZeit: null, ganztaegig: false, farbe: '#7c3aed',
        projektId: null, projektName: null, kundeId: null, kundeName: null,
        lieferantId: null, lieferantName: null, anfrageId: 9, anfrageBetreff: 'Carport Musterweg',
        erstellerId: null, erstellerName: null, teilnehmer: [],
    },
]
const abwesenheiten = [
    ...['2026-09-01', '2026-09-02', '2026-09-03', '2026-09-04'].map((datum, i) => ({ id: 100 + i, datum, typ: 'URLAUB', stunden: 8, mitarbeiterId: 2, mitarbeiterName: 'Erika Musterfrau' })),
    { id: 110, datum: '2026-09-17', typ: 'KRANKHEIT', stunden: 8, mitarbeiterId: 3, mitarbeiterName: 'Hans Beispiel' },
    { id: 111, datum: '2026-09-18', typ: 'FORTBILDUNG', stunden: 8, mitarbeiterId: 1, mitarbeiterName: 'Max Mustermann' },
    { id: 112, datum: '2026-09-25', typ: 'ZEITAUSGLEICH', stunden: 8, mitarbeiterId: 1, mitarbeiterName: 'Max Mustermann' },
]
const feiertage = [{ datum: '2026-10-03', bezeichnung: 'Tag der Deutschen Einheit' }]

/**
 * Die Legende muss vollständig unter der Rasterkarte liegen. Die automatischen
 * Überschneidungs-Checks sehen das nicht (Legende ist nicht interaktiv) – und genau
 * so lief die letzte Rasterzeile einmal über die Legende, als Toast und Hinweisband
 * dem Raster die Höhe nahmen.
 */
async function legendeLiegtUnterRaster(page: Page) {
    const raster = await page.getByRole('grid', { name: 'Monatsraster' }).boundingBox()
    const legende = await page.getByRole('list', { name: 'Legende' }).boundingBox()
    expect(raster, 'Raster sichtbar').not.toBeNull()
    expect(legende, 'Legende sichtbar').not.toBeNull()
    expect(legende!.y, 'Legende beginnt erst unter dem Raster').toBeGreaterThanOrEqual(raster!.y + raster!.height)
}

async function vorbereiten(page: Page, { kalenderStatus = 200 } = {}) {
    await page.clock.setFixedTime(HEUTE)
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
    })
    await page.route('**/api/**', route => {
        const url = new URL(route.request().url())
        if (url.pathname.endsWith('/api/kalender/mobile')) {
            if (kalenderStatus !== 200) return route.fulfill({ status: kalenderStatus, json: {} })
            return route.fulfill({ json: url.searchParams.get('monat') === '9' ? eintraege : [] })
        }
        if (url.pathname.endsWith('/api/abwesenheit/team')) {
            const von = url.searchParams.get('von') ?? ''
            const bis = url.searchParams.get('bis') ?? ''
            return route.fulfill({ json: abwesenheiten.filter(a => a.datum >= von && a.datum <= bis) })
        }
        if (url.pathname.endsWith('/api/zeiterfassung/feiertage')) return route.fulfill({ json: feiertage })
        return route.fulfill({ json: {} })
    })
}

test('Monat: großes Raster mit Legende, Tages-Sheet, Termin-Unterseite und Rückweg ins Sheet', async ({ page }, testInfo) => {
    await vorbereiten(page)
    await page.goto('kalender')
    await expect(page.getByRole('heading', { name: 'September 2026' })).toBeVisible()
    await expect(page.getByRole('button', { name: /Donnerstag, 17\. September 2026, 2 Einträge/ })).toHaveAttribute('aria-current', 'date')
    await expect(page.getByRole('list', { name: 'Legende' })).toBeVisible()
    // Keine Terminliste auf der Monatsseite – Einträge erst im Sheet.
    await expect(page.getByText('Baustelle Musterstraße')).toHaveCount(0)
    await legendeLiegtUnterRaster(page)
    await designPruefung(page, testInfo, 'kalender-monat', { primaerAktion: page.getByRole('button', { name: 'Heute' }) })

    // Tag antippen → Tages-Sheet mit der Liste des Tages
    await page.getByRole('button', { name: /Donnerstag, 3\. September 2026/ }).click()
    const sheet = page.getByRole('dialog')
    await expect(sheet.getByText('Donnerstag, 3. September')).toBeVisible()
    await expect(sheet.getByText('2 Einträge')).toBeVisible()
    await expect(sheet.getByText('Abnahme Terrasse')).toBeVisible()
    await expect(sheet.getByText('Erika Musterfrau')).toBeVisible()
    await designPruefung(page, testInfo, 'kalender-tages-sheet', { primaerAktion: sheet.getByRole('button', { name: 'Schließen' }) })

    // Termin antippen → eigene Unterseite
    await sheet.getByText('Abnahme Terrasse').click()
    await expect(page).toHaveURL(/\/kalender\/termin\/2026-09-03\/t-13$/)
    await expect(page.getByRole('heading', { name: 'Abnahme Terrasse' })).toBeVisible()
    await expect(page.getByText('14:00 – 15:00 Uhr')).toBeVisible()
    await expect(page.getByRole('button', { name: /Kunde.*Max Mustermann/ })).toBeVisible()
    await designPruefung(page, testInfo, 'kalender-termin-detail', { primaerAktion: page.getByRole('button', { name: 'Zurück zum Kalender' }) })

    // Zurück-Pfeil → Monatsansicht mit wieder geöffnetem Tages-Sheet
    await page.getByRole('button', { name: 'Zurück zum Kalender' }).click()
    await expect(page).toHaveURL(/\/kalender\?datum=2026-09-03&sheet=tag$/)
    await expect(page.getByRole('dialog').getByText('Abnahme Terrasse')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'September 2026' })).toBeVisible()

    // Sheet schließen, Monat blättern
    await page.getByRole('button', { name: 'Schließen' }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await page.getByRole('button', { name: 'Nächster Monat' }).click()
    await expect(page.getByRole('heading', { name: 'Oktober 2026' })).toBeVisible()
    await expect(page.getByRole('button', { name: /Samstag, 3\. Oktober 2026, 1 Eintrag/ })).toBeVisible()
    await designPruefung(page, testInfo, 'kalender-monat-oktober', { primaerAktion: page.getByRole('button', { name: 'Heute' }) })

    // Wischen nach rechts blättert zurück in den September.
    const raster = page.getByRole('grid', { name: 'Monatsraster' })
    const rasterElement = await raster.elementHandle()
    await raster.dispatchEvent('touchstart', { touches: [{ identifier: 1, target: rasterElement, clientX: 60, clientY: 400 }] })
    await raster.dispatchEvent('touchend', { changedTouches: [{ identifier: 1, target: rasterElement, clientX: 300, clientY: 405 }] })
    await expect(page.getByRole('heading', { name: 'September 2026' })).toBeVisible()
})

test('Deep-Link aus der Benachrichtigung öffnet die Unterseite, Zurück landet im Tages-Sheet', async ({ page }) => {
    await vorbereiten(page)
    await page.goto('kalender?termin=13')
    await expect(page).toHaveURL(/\/kalender\/termin\/2026-09-03\/t-13$/)
    await expect(page.getByRole('heading', { name: 'Abnahme Terrasse' })).toBeVisible()
    await page.getByRole('button', { name: 'Zurück zum Kalender' }).click()
    await expect(page).toHaveURL(/\/kalender\?datum=2026-09-03&sheet=tag$/)
    await expect(page.getByRole('dialog').getByText('Abnahme Terrasse')).toBeVisible()
})

test('Termin-Unterseite: Ladefehler zeigt Toast und Hinweisband statt „nicht gefunden“', async ({ page }, testInfo) => {
    await vorbereiten(page, { kalenderStatus: 500 })
    await page.goto('kalender/termin/2026-09-17/t-12')
    await expect(page.getByRole('alert')).toContainText('Termine konnten nicht geladen werden')
    await expect(page.getByText('Termin konnte nicht geladen werden.')).toBeVisible()
    await expect(page.getByText(/nicht gefunden/)).toHaveCount(0)
    await designPruefung(page, testInfo, 'kalender-termin-detail-fehler', { primaerAktion: page.getByRole('button', { name: 'Erneut versuchen' }) })
})

test('Termin-Unterseite direkt aufgerufen lädt den Tag nach und führt zurück ins Sheet', async ({ page }, testInfo) => {
    await vorbereiten(page)
    await page.goto('kalender/termin/2026-09-17/t-12')
    await expect(page.getByRole('heading', { name: 'Baustelle Musterstraße' })).toBeVisible()
    await expect(page.getByText('Dachfenster einbauen, Material liegt bereit.')).toBeVisible()
    // Erika Musterfrau erscheint zweimal: als Erstellerin und in der Teilnehmerliste.
    await expect(page.getByRole('definition').filter({ hasText: 'Erika Musterfrau' })).toBeVisible()
    await expect(page.getByText('Erika Musterfrau', { exact: true })).toHaveCount(2)
    await designPruefung(page, testInfo, 'kalender-termin-detail-voll', { primaerAktion: page.getByRole('button', { name: 'Zurück zum Kalender' }) })

    await page.getByRole('button', { name: 'Zurück zum Kalender' }).click()
    await expect(page).toHaveURL(/\/kalender\?datum=2026-09-17&sheet=tag$/)
    await expect(page.getByRole('dialog').getByText('Hans Beispiel')).toBeVisible()
})

test('Leerer Tag im Sheet und leerer Monat im Raster', async ({ page }, testInfo) => {
    await vorbereiten(page)
    await page.goto('kalender')
    await expect(page.getByRole('button', { name: /Donnerstag, 17\. September 2026, 2 Einträge/ })).toBeVisible()

    await page.getByRole('button', { name: /Montag, 14\. September 2026, 0 Einträge/ }).click()
    const sheet = page.getByRole('dialog')
    await expect(sheet.getByText('Montag, 14. September')).toBeVisible()
    await expect(sheet.getByText('Keine Termine an diesem Tag.')).toBeVisible()
    await designPruefung(page, testInfo, 'kalender-sheet-leer', { primaerAktion: sheet.getByRole('button', { name: 'Schließen' }) })
    await sheet.getByRole('button', { name: 'Schließen' }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)

    await page.getByRole('button', { name: 'Vorheriger Monat' }).click()
    await expect(page.getByRole('heading', { name: 'August 2026' })).toBeVisible()
    await expect(page.getByRole('button', { name: /Montag, 3\. August 2026, 0 Einträge/ })).toBeVisible()
    await designPruefung(page, testInfo, 'kalender-monat-leer', { primaerAktion: page.getByRole('button', { name: 'Heute' }) })
})

test('Ladefehler: Toast plus Hinweisband, Seite bleibt bedienbar, Sheet liegt unter der Meldungsfläche', async ({ page }, testInfo) => {
    await vorbereiten(page, { kalenderStatus: 500 })
    await page.goto('kalender')
    await expect(page.getByRole('alert')).toContainText('Termine konnten nicht geladen werden')
    await expect(page.getByRole('heading', { name: 'September 2026' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Erneut versuchen' })).toBeVisible()
    await legendeLiegtUnterRaster(page)
    await designPruefung(page, testInfo, 'kalender-fehler', { primaerAktion: page.getByRole('button', { name: 'Erneut versuchen' }) })

    // Toast offen + Sheet offen: die Meldungsfläche darf Griff, Titel und „Schließen“ nicht verdecken.
    await page.getByRole('button', { name: /Donnerstag, 17\. September 2026, 0 Einträge/ }).click()
    const sheet = page.getByRole('dialog')
    await expect(sheet.getByText('Keine Termine an diesem Tag.')).toBeVisible()
    // Bei offenem Sheet blendet der modale Dialog den Rest der Seite für Screenreader aus (aria-hidden);
    // sichtbar bleibt die Meldung trotzdem – deshalb über das Datenattribut statt über die Rolle prüfen.
    await expect(page.locator('[data-mobile-toasts]')).toContainText('Termine konnten nicht geladen werden')
    await designPruefung(page, testInfo, 'kalender-fehler-sheet', { primaerAktion: sheet.getByRole('button', { name: 'Schließen' }) })
})
