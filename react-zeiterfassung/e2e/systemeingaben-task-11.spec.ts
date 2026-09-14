import { test, expect } from './hilfen/test'
import type { Page, TestInfo, Locator } from '@playwright/test'

const note = { id: 1, notiz: 'Testnotiz', erstelltAm: '2026-09-01T10:00:00', mitarbeiterId: 1, mitarbeiterVorname: 'Max', mitarbeiterNachname: 'Mustermann', mobileSichtbar: true, nurFuerErsteller: false, canEdit: true, bilder: [{ id: 2, originalDateiname: 'test.png', url: '/test.png', erstelltAm: '2026-09-01T10:00:00' }] }
async function photo(page: Page, info: TestInfo, name: string, action?: Locator) {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    if (action) await expect(action).toBeEnabled()
    await page.waitForTimeout(250) // Auch laufende CSS-Übergänge vor dem Screenshot beenden.
    await expect(page.locator('input[type=date],input[type=number],input[type=time],select')).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    if (action) { const box = await action.boundingBox(); expect(box).not.toBeNull(); expect(box!.x).toBeGreaterThanOrEqual(0); expect(box!.x + box!.width).toBeLessThanOrEqual(393); expect(box!.y + box!.height).toBeLessThanOrEqual(852) }
    await page.screenshot({ path: info.outputPath(`${name}.png`) })
}
test.beforeEach(async ({ page }) => {
    page.on('dialog', async dialog => { await dialog.dismiss(); throw new Error(`Nativer Browserdialog: ${dialog.type()}`) })
    await page.addInitScript(() => { localStorage.setItem('zeiterfassung_token', 'test-token'); localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' })) })
    await page.route('**/test.png*', route => route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j1ioAAAAASUVORK5CYII=', 'base64') }))
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname
        const body = path.includes('/by-token/') ? { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' }
            : path.includes('buchungszeitfenster') ? { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }
            : path.endsWith('/resturlaub') ? { verbleibend: 20 }
            : path === '/api/lieferanten/1' ? { id: 1, lieferantenname: 'Testbetrieb' }
            : path.endsWith('/notizen') ? [note]
            : path === '/api/zeiterfassung/projekte' ? [{ id: 1, name: 'Testprojekt', projektNummer: 'TEST-1', kundenName: 'Testbetrieb' }]
            : path.includes('/kategorien/') ? [{ id: 1, name: 'Testkategorie' }]
            : path.includes('/arbeitsgaenge/') ? [{ id: 1, beschreibung: 'Montage' }] : []
        await route.fulfill({ json: body })
    })
})
test('Mehrwertsteuer: Null leeren, Komma berechnen, unvollständige Zahl ablehnen', async ({ page }, info) => {
    const payloads: unknown[] = []
    await page.route('**/api/buchhaltung/mobile/mwst-rechner*', async route => { payloads.push(route.request().postDataJSON()); await route.fulfill({ json: { netto: 12.5, brutto: 14.88, satzProzent: 19, mwstBetrag: 2.38 } }) })
    await page.goto('mwst-rechner')
    const netto = page.getByRole('textbox', { name: 'Netto (ohne MwSt)' })
    await netto.fill('0,00'); await page.getByText('Mehrwertsteuer', { exact: true }).tap(); await netto.tap(); await expect(netto).toHaveValue('')
    await netto.fill('12,'); await page.getByRole('button', { name: 'Rechnen' }).tap(); await expect(page.getByRole('alert')).toContainText('Netto'); expect(payloads).toEqual([])
    await page.locator('header button').first().click({ trial: true, timeout: 2000 })
    const notice = await page.getByRole('alert').boundingBox(), header = await page.locator('header').boundingBox()
    expect(notice!.y + notice!.height).toBeLessThanOrEqual(header!.y)
    await photo(page, info, 'mwst-fehler', page.getByRole('button', { name: 'Rechnen' }))
    await page.getByRole('button', { name: 'Meldung schließen' }).tap(); await netto.fill('12,50'); await page.getByRole('button', { name: 'Rechnen' }).tap()
    await expect(netto).toHaveValue('12,50'); expect(payloads).toEqual([{ netto: 12.5, brutto: null, satzProzent: 19 }])
    await page.keyboard.press('Tab'); await page.keyboard.press('Shift+Tab'); await expect(netto).toHaveValue('12,50')
    await photo(page, info, 'mwst-ergebnis', page.getByRole('button', { name: 'Rechnen' }))
})
test('Urlaub: eigene Datumswahl, Zeitraumgrenze und Jahresauswahl', async ({ page }, info) => {
    await page.goto('urlaub')
    const start = page.getByRole('button', { name: 'Vom (Erster Tag)' })
    await start.tap(); const dialog = page.getByRole('dialog', { name: 'Vom (Erster Tag)' })
    await photo(page, info, 'urlaub-kalender', dialog.getByRole('button', { name: 'Schließen' }))
    const today = new Date(); const label = (day: number) => new Date(today.getFullYear(), today.getMonth(), day).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
    await dialog.getByRole('button', { name: label(15), exact: true }).tap()
    await page.getByRole('button', { name: 'Bis (Letzter Tag)' }).tap()
    const end = page.getByRole('dialog', { name: 'Bis (Letzter Tag)' }); await expect(end.getByRole('button', { name: label(14), exact: true })).toBeDisabled(); await end.getByRole('button', { name: label(16), exact: true }).tap()
    await page.getByRole('button', { name: 'Übersicht', exact: true }).tap(); const year = page.getByRole('combobox', { name: 'Jahr' }); await year.focus(); await page.keyboard.press('ArrowDown')
    await expect(page.getByRole('listbox')).toBeVisible(); await photo(page, info, 'urlaub-jahr'); await page.keyboard.press('Escape'); await expect(year).toBeFocused()
    await page.goto('abwesenheiten'); await page.getByRole('combobox', { name: 'Status' }).tap(); await photo(page, info, 'abwesenheiten-status'); await page.getByRole('option', { name: 'Genehmigt' }).tap(); await expect(page.getByRole('combobox', { name: 'Status' })).toHaveText('Genehmigt')
})
for (const base of ['projekte', 'anfragen']) test(`${base}: Notiz und Bild mit eigenem Dialog löschen, Fehler im offenen Formular`, async ({ page }, info) => {
    const deleted: string[] = []
    await page.route(`**/api/${base}/1/notizen/**`, async route => { if (route.request().method() === 'DELETE') { deleted.push(route.request().url()); await route.fulfill({ status: 500, json: {} }) } else await route.fallback() })
    await page.goto(`${base}/1/notizen`)
    for (const label of ['Notiz', 'Bild']) {
        await page.getByRole('button', { name: `${label} löschen`, exact: true }).tap()
        const dialog = page.getByRole('dialog', { name: `${label} löschen` }); await photo(page, info, `${base}-${label}-bestaetigung`, dialog.getByRole('button', { name: 'Löschen', exact: true }))
        const count = deleted.length; await dialog.getByRole('button', { name: 'Abbrechen' }).tap(); expect(deleted).toHaveLength(count)
        await page.getByRole('button', { name: `${label} löschen`, exact: true }).tap(); await dialog.getByRole('button', { name: 'Löschen', exact: true }).tap(); await expect(page.getByRole('alert')).toBeVisible(); expect(deleted).toHaveLength(count + 1)
        await page.getByRole('button', { name: `${label} löschen`, exact: true }).tap()
        const message = await page.getByRole('alert').boundingBox(), panel = await dialog.boundingBox()
        expect(message!.y + message!.height).toBeLessThan(panel!.y)
        await photo(page, info, `${base}-${label}-toast-dialog`, dialog.getByRole('button', { name: 'Abbrechen' }))
        await dialog.getByRole('button', { name: 'Abbrechen' }).tap()
        await page.getByRole('button', { name: 'Meldung schließen' }).tap()
    }
    await page.route(`**/api/${base}/1/notizen?*`, async route => route.request().method() === 'POST' ? route.fulfill({ status: 500, json: {} }) : route.fallback())
    await page.getByRole('button', { name: 'Eintrag hinzufügen' }).tap(); await page.getByRole('dialog', { name: 'Neuer Eintrag' }).getByRole('textbox').fill('Neue Testnotiz'); await page.getByRole('button', { name: 'Eintrag speichern' }).tap(); await expect(page.getByRole('alert')).toContainText('Speichern')
    await photo(page, info, `${base}-speicherfehler`, page.getByRole('button', { name: 'Eintrag speichern' }))
    const toast = await page.getByRole('alert').boundingBox(), save = await page.getByRole('button', { name: 'Eintrag speichern' }).boundingBox(); expect(toast!.y + toast!.height).toBeLessThan(save!.y)
})
test('Lieferschein: Offlinefehler als Toast', async ({ page }, info) => {
    await page.route('**/api/**/analyze*', route => route.abort('internetdisconnected'))
    await page.goto('lieferanten/1/lieferscheine'); await page.locator('input[type=file]').setInputFiles({ name: 'test.txt', mimeType: 'text/plain', buffer: Buffer.from('dummy') }); await expect(page.getByRole('alert')).toContainText('Netzwerkfehler'); await photo(page, info, 'lieferschein-offline')
})
test('Zeitbuchung: Serverfehler bleibt sichtbar und startet keine Sitzung', async ({ page }, info) => {
    await page.route('**/api/zeiterfassung/start', route => route.fulfill({ status: 409, json: { error: 'Buchung ist gesperrt' } }))
    await page.goto('zeiterfassung'); await page.getByText('Testprojekt', { exact: true }).tap(); await page.getByText('Testkategorie', { exact: true }).tap(); await page.getByText('Montage', { exact: true }).tap(); await page.getByRole('button', { name: /Starten|Zeit erfassen|Buchung starten/i }).tap(); await expect(page.getByRole('alert')).toContainText('Buchung ist gesperrt'); expect(await page.evaluate(() => localStorage.getItem('zeiterfassung_active_session'))).toBeNull(); await photo(page, info, 'zeitbuchung-fehler', page.getByRole('button', { name: /Starten|Zeit erfassen|Buchung starten/i }))
})
test('Gestapelte Meldungen geben dem Kalender Platz und verschwinden ohne Leerraum', async ({ page }, info) => {
    await page.goto('urlaub')
    await page.getByRole('button', { name: 'Antrag senden' }).tap()
    await page.getByRole('button', { name: 'Antrag senden' }).tap()
    await expect(page.getByRole('alert')).toHaveCount(2)
    await page.getByRole('button', { name: 'Vom (Erster Tag)' }).tap()
    const dialog = page.getByRole('dialog', { name: 'Vom (Erster Tag)' })
    const notices = await page.locator('[data-mobile-toasts]').boundingBox(), panel = await dialog.boundingBox()
    expect(notices!.y + notices!.height).toBeLessThan(panel!.y)
    await photo(page, info, 'kalender-mit-meldungen', dialog.getByRole('button', { name: 'Schließen' }))
    await dialog.getByRole('button', { name: 'Schließen' }).tap()
    await page.getByRole('button', { name: 'Meldung schließen' }).first().tap(); await page.getByRole('button', { name: 'Meldung schließen' }).tap()
    await expect(page.locator('[data-mobile-toasts]')).toBeHidden()
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--mobile-toast-height'))).toBe('0px')
    expect((await page.locator('header').boundingBox())!.y).toBe(0)
})
test('Lieferscheinformular: Toasts lassen Schließen und Speichern erreichbar', async ({ page }, info) => {
    await page.route('**/api/**/analyze*', route => route.fulfill({ json: [{ pageRange: '1', analyzeResponse: { dokumentTyp: 'LIEFERSCHEIN', dokumentDatum: '2026-09-01', dokumentNummer: 'TEST-1' } }] }))
    await page.route('**/api/lieferanten/1/dokumente/import*', route => route.fulfill({ status: 500, json: { message: 'Testbeleg konnte nicht gespeichert werden.' } }))
    await page.goto('lieferanten/1/lieferscheine'); await page.locator('input[type=file]').setInputFiles({ name: 'test.txt', mimeType: 'text/plain', buffer: Buffer.from('dummy') })
    await page.getByRole('button', { name: 'Speichern', exact: true }).tap(); await expect(page.getByRole('alert')).toContainText('Testbeleg')
    const close = page.getByRole('heading', { name: 'Prüfen & Speichern' }).locator('..').getByRole('button')
    await close.click({ trial: true })
    const reference = page.getByPlaceholder('z.B. AB-12345'); await reference.scrollIntoViewIfNeeded()
    expect((await reference.boundingBox())!.y + (await reference.boundingBox())!.height).toBeLessThanOrEqual((await page.getByRole('button', { name: 'Speichern', exact: true }).boundingBox())!.y)
    await photo(page, info, 'lieferschein-formular-fehler', page.getByRole('button', { name: 'Speichern', exact: true }))
    await page.getByRole('button', { name: 'Belegdatum', exact: true }).tap(); const calendar = page.getByRole('dialog', { name: 'Belegdatum' }); await photo(page, info, 'lieferschein-formular-kalender-toast', calendar.getByRole('button', { name: 'Schließen' })); await calendar.getByRole('button', { name: 'Schließen' }).tap()
})
test('Reklamation: Meldung lässt die Speichern-Aktion frei', async ({ page }, info) => {
    await page.goto('lieferanten/1/reklamation/neu'); const save = page.getByRole('button', { name: 'Speichern', exact: true }); await save.tap(); await expect(page.getByRole('alert')).toContainText('Beschreibung'); await save.click({ trial: true }); await photo(page, info, 'reklamation-pflichtfehler', save)
})
test('Scanner: Kamerafehler bleibt sichtbar, Schließen bleibt erreichbar', async ({ page }, info) => {
    await page.addInitScript(() => { navigator.mediaDevices.getUserMedia = async () => { throw new DOMException('Permission denied', 'NotAllowedError') } })
    await page.goto('lieferanten/1/lieferscheine'); await page.getByRole('button', { name: 'Scannen', exact: true }).tap()
    await expect(page.getByRole('alert')).toContainText('Zugriff auf die Kamera')
    const close = page.locator('button').filter({ has: page.locator('svg.lucide-x') }).first()
    await close.click({ trial: true }); await photo(page, info, 'scanner-kamerafehler', close)
})
