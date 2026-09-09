import { expect, test } from './hilfen/test'

const configuredStatus = { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }

async function vorbereiten(page: import('@playwright/test').Page, status = configuredStatus) {
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
    })
    await page.route('**/api/**', route => {
        const url = route.request().url()
        if (url.includes('/api/zeiterfassung/buchungszeitfenster/')) return route.fulfill({ json: status })
        if (url.includes('/api/zeiterfassung/aktiv/')) return route.fulfill({ json: { aktiv: false } })
        if (url.includes('/api/zeiterfassung/heute/')) return route.fulfill({ json: { stunden: 0, minuten: 0 } })
        if (url.includes('/api/')) return route.fulfill({ json: {} })
        return route.continue()
    })
}

test('eingerichtetes Zeitkonto zeigt die Live-Saldo-Navigation', async ({ page }, testInfo) => {
    await vorbereiten(page)
    await page.goto('/')
    await expect(page.getByText('Zeit erfassen')).toBeVisible()
    await expect(page.getByText('Saldenauswertung')).toBeVisible()
    await expect(page.locator('body')).not.toHaveCSS('overflow-x', 'scroll')
    await page.screenshot({ path: testInfo.outputPath('zeitkonto-eingerichtet.png'), fullPage: true })
})

test('ausgeschaltetes Zeitkonto sperrt neue Starts und behält Projekt-Navigation', async ({ page }, testInfo) => {
    await vorbereiten(page, { fuehrtZeitkonto: false, eingerichtet: false, hinweis: 'Die Zeiterfassung ist ausgeschaltet.' })
    await page.goto('/')
    await expect(page.getByText('Zeiterfassung derzeit nicht verfügbar')).toBeVisible()
    await expect(page.getByText('Die Zeiterfassung ist ausgeschaltet.')).toBeVisible()
    await expect(page.getByText('Zeit erfassen')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Projekte' })).toBeVisible()
    await expect(page.getByText('Saldenauswertung')).toHaveCount(0)
    await page.screenshot({ path: testInfo.outputPath('zeitkonto-ausgeschaltet.png'), fullPage: true })
})
