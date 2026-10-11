import type { Page, Route } from '@playwright/test'
import { expect, test } from './hilfen/test'
import { designPruefung } from './hilfen/design'

/**
 * Spracheingabe ist freiwillig: Ohne KI-Schlüssel im Betrieb zeigt die App
 * gar keinen Mikrofon-Knopf. Nur ein ausdrückliches `{ verfuegbar: false }`
 * blendet ihn aus; scheitert die Abfrage, bleibt er wie bisher da.
 * Kein Backend, nur Dummy-Daten (DSGVO).
 */

type Antwort = { verfuegbar: boolean } | 'fehler'

async function oeffneNeuenEintrag(page: Page, antwort: Antwort) {
    const abfragen: (string | null)[] = []
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' }))
        localStorage.setItem('zeiterfassung_spracheingabe_hinweis', '1')
    })
    await page.route('**/api/**', (route: Route) => {
        const url = new URL(route.request().url())
        if (url.pathname === '/api/spracheingabe/verfuegbar') {
            abfragen.push(url.searchParams.get('token'))
            if (antwort === 'fehler') return route.fulfill({ status: 500, json: { message: 'kaputt' } })
            return route.fulfill({ json: antwort })
        }
        if (url.pathname.includes('/by-token/')) return route.fulfill({ json: { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' } })
        return route.fulfill({ json: [] })
    })

    await page.goto('projekte/7/notizen')
    const verfuegbarkeit = page.waitForResponse('**/api/spracheingabe/verfuegbar**')
    await page.getByRole('button', { name: 'Eintrag hinzufügen' }).tap()
    await expect(page.getByRole('textbox')).toBeVisible()
    await verfuegbarkeit
    return { abfragen }
}

test('ohne KI-Schlüssel gibt es keinen Mikrofon-Knopf – das Tagebuch funktioniert trotzdem', async ({ page }, testInfo) => {
    const { abfragen } = await oeffneNeuenEintrag(page, { verfuegbar: false })

    expect(abfragen).toEqual(['test-token'])
    await expect(page.getByRole('button', { name: 'Eintrag diktieren' })).toHaveCount(0)
    await page.getByRole('textbox').fill('Dachrinne montiert, Fallrohr fehlt noch.')
    await expect(page.getByRole('textbox')).toHaveValue('Dachrinne montiert, Fallrohr fehlt noch.')
    await designPruefung(page, testInfo, 'spracheingabe-ohne-ki')
})

test('mit KI-Schlüssel steht der Mikrofon-Knopf am Feld', async ({ page }, testInfo) => {
    await oeffneNeuenEintrag(page, { verfuegbar: true })

    await expect(page.getByRole('button', { name: 'Eintrag diktieren' })).toBeEnabled()
    await designPruefung(page, testInfo, 'spracheingabe-mit-ki')
})

test('scheitert die Abfrage, bleibt der Mikrofon-Knopf wie bisher da', async ({ page }) => {
    await oeffneNeuenEintrag(page, 'fehler')

    await expect(page.getByRole('button', { name: 'Eintrag diktieren' })).toBeVisible()
})
