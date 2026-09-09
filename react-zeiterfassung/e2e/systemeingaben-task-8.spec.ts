import { test, expect } from '@playwright/test'

test('Lieferschein: Belegdatum im eigenen mobilen Kalender wählen', async ({ page }) => {
    const nativeDialogs: string[] = []
    page.on('dialog', async dialog => { nativeDialogs.push(dialog.type()); await dialog.dismiss() })
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' }))
    })
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname
        const body = path.includes('/by-token/') ? { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' }
            : path.endsWith('/analyze') ? [{ pageRange: '1', analyzeResponse: { dokumentTyp: 'LIEFERSCHEIN', dokumentDatum: '2020-01-15', dokumentNummer: 'TEST-1' } }]
            : path === '/api/lieferanten/1' ? { id: 1, lieferantenname: 'Testbetrieb' } : []
        await route.fulfill({ json: body })
    })
    await page.goto('lieferanten/1/lieferscheine')
    await page.locator('input[type=file]').setInputFiles({ name: 'test.txt', mimeType: 'text/plain', buffer: Buffer.from('Dummy-Lieferschein') })
    const trigger = page.getByRole('button', { name: 'Belegdatum', exact: true })
    await trigger.click()
    const calendar = page.getByRole('dialog', { name: 'Belegdatum' })
    await expect(calendar).toBeVisible()
    await expect(page.locator('input[type=date], input[type=number], select')).toHaveCount(0)
    const bounds = await calendar.boundingBox()
    expect(bounds!.x).toBeGreaterThanOrEqual(0)
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(393)
    expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(852)
    await page.screenshot({ path: 'test-results/task8-kalender-handy.png' })
    await calendar.getByRole('button', { name: '20.01.2020' }).tap()
    await expect(trigger).toHaveText('20.01.2020')
    await expect(trigger).toBeFocused()
    await trigger.click()
    await page.keyboard.press('Escape')
    await expect(calendar).toBeHidden()
    expect(nativeDialogs).toEqual([])
})
