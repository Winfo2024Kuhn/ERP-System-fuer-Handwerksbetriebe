import { test, expect } from './hilfen/test'
import type { Page, TestInfo } from '@playwright/test'

async function photo(page: Page, info: TestInfo, name: string) {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.waitForTimeout(200)
    await expect(page.locator('input[type=date],input[type=number],input[type=time],select')).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: info.outputPath(`${name}.png`) })
}

test.beforeEach(async ({ page }) => {
    page.on('dialog', async dialog => {
        await dialog.dismiss()
        throw new Error(`Nativer Browserdialog: ${dialog.type()}`)
    })
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' }))
    })
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url())
        const path = url.pathname

        if (path.includes('/by-token/')) {
            return route.fulfill({ json: { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' } })
        }
        if (path.includes('buchungszeitfenster')) {
            return route.fulfill({ json: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null } })
        }
        if (path.includes('/saldo/')) {
            return route.fulfill({
                json: {
                    jahr: 2026,
                    urlaub: { jahresanspruch: 30, genommen: 10, geplant: 2, verbleibend: 18, krankheitsTage: 0, fortbildungsTage: 0 },
                    monat: { name: 'September', monatNummer: 9, sollStunden: 160, istStunden: 176.5, differenz: 16.5, festgeschrieben: false },
                    gesamt: { istStunden: 500, sollStunden: 483.5, saldo: 16.5, startDatum: '2026-01-01', vorlaeufig: true, geprueftBis: null }
                }
            })
        }
        if (path.endsWith('/feiertage/zwischen')) {
            return route.fulfill({ json: [] })
        }
        if (path.endsWith('/feiertage')) {
            return route.fulfill({ json: [] })
        }
        if (path.endsWith('/resturlaub')) {
            return route.fulfill({ json: { verbleibend: 18 } })
        }
        if (path.endsWith('/antraege')) {
            return route.fulfill({ json: [] })
        }
        return route.fulfill({ json: [] })
    })
})

test('Zeitausgleichsantrag: Saldo-Anzeige, Datumswahl und Absenden mit Typ ZEITAUSGLEICH', async ({ page }, info) => {
    const postRequests: Array<{ url: string; body: Record<string, unknown> }> = []
    await page.route('**/api/urlaub/antraege', async route => {
        if (route.request().method() === 'POST') {
            const body = route.request().postDataJSON()
            postRequests.push({ url: route.request().url(), body })
            return route.fulfill({ json: { id: 42, ...body, status: 'OFFEN' } })
        }
        return route.fulfill({ json: [] })
    })

    // 1. Navigation über /zeitausgleich Redirect
    await page.goto('zeitausgleich')
    await expect(page).toHaveURL(/.*\/urlaub\?typ=ZEITAUSGLEICH/)

    // 2. Zeitkonto Saldo Kachel prüfen
    await expect(page.getByText('Aktuelles Zeitkonto: +16,5 Std.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Zeitausgleich beantragen' })).toBeVisible()

    await photo(page, info, 'zeitausgleich-formular')

    // 3. Datumsbereich auswählen
    const startBtn = page.getByRole('button', { name: 'Vom (Erster Tag)' })
    await startBtn.tap()
    const startDialog = page.getByRole('dialog', { name: 'Vom (Erster Tag)' })

    const today = new Date()
    const labelDay = (d: number) => new Date(today.getFullYear(), today.getMonth(), d).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
    await startDialog.getByRole('button', { name: labelDay(18), exact: true }).tap()

    const endBtn = page.getByRole('button', { name: 'Bis (Letzter Tag)' })
    await endBtn.tap()
    const endDialog = page.getByRole('dialog', { name: 'Bis (Letzter Tag)' })
    await endDialog.getByRole('button', { name: labelDay(18), exact: true }).tap()

    // 4. Bemerkung eingeben
    const bemerkung = page.getByPlaceholder('z.B. Sommerurlaub...')
    await bemerkung.fill('Überstundenabbau wegen Terminen')

    // 5. Absenden
    const submitBtn = page.getByRole('button', { name: 'Zeitausgleich beantragen' })
    await submitBtn.tap()

    // 6. Prüfen, dass POST mit typ === 'ZEITAUSGLEICH' gesendet wurde
    await expect.poll(() => postRequests.length).toBe(1)
    expect(postRequests[0].body).toMatchObject({
        mitarbeiterId: 1,
        typ: 'ZEITAUSGLEICH',
        bemerkung: 'Überstundenabbau wegen Terminen'
    })

    // 7. Erfolgsmeldung prüfen
    await expect(page.getByText('Antrag gesendet!')).toBeVisible()
    await expect(page.getByText('Dein Zeitausgleichsantrag wurde erfolgreich übermittelt.')).toBeVisible()

    await photo(page, info, 'zeitausgleich-erfolg')
})

test('Saldenauswertung: Zeitausgleich beantragen Button leitet zu /urlaub?typ=ZEITAUSGLEICH weiter', async ({ page }, info) => {
    await page.goto('salden')

    // Button im Gesamtsaldo prüfen
    const btn = page.getByRole('button', { name: 'Zeitausgleich beantragen' })
    await expect(btn).toBeVisible()
    await photo(page, info, 'salden-zeitausgleich-button')

    // Klick auf Zeitausgleich beantragen
    await btn.tap()

    // Weiterleitung zu /urlaub?typ=ZEITAUSGLEICH prüfen
    await expect(page).toHaveURL(/.*\/urlaub\?typ=ZEITAUSGLEICH/)
    await expect(page.getByText('Aktuelles Zeitkonto: +16,5 Std.')).toBeVisible()
})
