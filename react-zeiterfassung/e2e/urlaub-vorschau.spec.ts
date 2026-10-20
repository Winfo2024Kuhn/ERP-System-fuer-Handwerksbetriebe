import type { Page } from '@playwright/test'
import { test, expect } from './hilfen/test'
import { designPruefung } from './hilfen/design'

/**
 * Issue #270 (Handy): Die Urlaubstage im Antrag kommen vom Server
 * (`/api/urlaub/vorschau`, Feiertage und Tage ohne Soll abgezogen). Nur wenn
 * der Server sagt, dass der Urlaub nicht reicht, ist der Absenden-Knopf gesperrt.
 *
 * DSGVO: nur Dummy-Daten (Max Mustermann). Ohne Backend, alles gestubbt.
 */

type Vorschau = { arbeitstage: number; verbleibend: number | null; reicht: boolean }

async function vorbereiten(page: Page, vorschau: Vorschau) {
    const vorschauAufrufe: string[] = []
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
        if (path.includes('/by-token/')) return route.fulfill({ json: { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' } })
        if (path.includes('buchungszeitfenster')) return route.fulfill({ json: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null } })
        if (path.endsWith('/resturlaub')) return route.fulfill({ json: { verbleibend: vorschau.verbleibend ?? 0 } })
        if (path.endsWith('/urlaub/vorschau')) {
            vorschauAufrufe.push(url.search)
            return route.fulfill({ json: vorschau })
        }
        if (path.includes('/aktiv/')) return route.fulfill({ json: { aktiv: false } })
        return route.fulfill({ json: [] })
    })
    return vorschauAufrufe
}

async function zeitraumWaehlen(page: Page) {
    const heute = new Date()
    const tag = (d: number) => new Date(heute.getFullYear(), heute.getMonth(), d).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
    await page.getByRole('button', { name: 'Vom (Erster Tag)' }).tap()
    await page.getByRole('dialog', { name: 'Vom (Erster Tag)' }).getByRole('button', { name: tag(10), exact: true }).tap()
    await page.getByRole('button', { name: 'Bis (Letzter Tag)' }).tap()
    await page.getByRole('dialog', { name: 'Bis (Letzter Tag)' }).getByRole('button', { name: tag(16), exact: true }).tap()
}

test('Urlaubsantrag: genaue Urlaubstage vom Server, Knopf frei, wenn der Urlaub reicht', async ({ page }, testInfo) => {
    const aufrufe = await vorbereiten(page, { arbeitstage: 3, verbleibend: 10, reicht: true })
    await page.goto('urlaub')
    await expect(page.getByText('10 Urlaubstage übrig')).toBeVisible()
    await zeitraumWaehlen(page)

    await expect(page.getByText('Beantragt: 3 Urlaubstage')).toBeVisible()
    await expect(page.getByText(/ungefähr/)).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Antrag senden' })).toBeEnabled()
    expect(aufrufe).toHaveLength(1)
    expect(aufrufe[0]).toContain('mitarbeiterId=1')
    await designPruefung(page, testInfo, 'urlaub-vorschau-reicht')
})

test('Urlaubsantrag: Absenden gesperrt, wenn der Server sagt, dass der Urlaub nicht reicht', async ({ page }, testInfo) => {
    await vorbereiten(page, { arbeitstage: 5, verbleibend: 2, reicht: false })
    await page.goto('urlaub')
    await expect(page.getByText('2 Urlaubstage übrig')).toBeVisible()
    await zeitraumWaehlen(page)

    await expect(page.getByText(/Beantragt: 5 Urlaubstage/)).toBeVisible()
    await expect(page.getByText(/Nicht genügend Urlaubstage/)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Antrag senden' })).toBeDisabled()
    await designPruefung(page, testInfo, 'urlaub-vorschau-reicht-nicht')
})
