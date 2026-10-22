import { test, expect } from './hilfen/test'
import { designPruefung } from './hilfen/design'

// Klappt das Scannen nicht, tippt der Mitarbeiter den 36-stelligen Code aus dem ERP ab.
// Mit dem Augen-Symbol sieht er dabei, was er eingegeben hat. Erfundener Code (DSGVO).
const code = '0f8e2c1a-9b7d-4e3f-a6c5-1d2b3c4e5f60'

test('Token beim manuellen Eingeben per Augen-Symbol sichtbar machen und anmelden', async ({ page }, testInfo) => {
    let gepruefterCode: string | undefined
    await page.route('**/api/**', route => {
        const pfad = new URL(route.request().url()).pathname
        if (pfad.startsWith('/api/mitarbeiter/by-token/')) {
            gepruefterCode = decodeURIComponent(pfad.split('/').pop()!)
            return route.fulfill({ json: { id: 7, vorname: 'Max', nachname: 'Mustermann', aktiv: true } })
        }
        return route.fulfill({ json: [] })
    })
    await page.goto('./')
    await page.getByText('Token manuell eingeben', { exact: true }).click()

    const eingabe = page.getByPlaceholder('Token eingeben...')
    await eingabe.fill(code)
    await expect(eingabe).toHaveAttribute('type', 'password')

    await page.getByRole('button', { name: 'Token anzeigen' }).click()
    await expect(eingabe).toHaveAttribute('type', 'text')
    await expect(eingabe).toHaveValue(code)

    // Das Auge sitzt im Feld und überdeckt den Text nicht.
    const feld = (await eingabe.boundingBox())!
    const auge = (await page.getByRole('button', { name: 'Token verbergen' }).boundingBox())!
    expect(auge.x + auge.width).toBeLessThanOrEqual(feld.x + feld.width + 1)
    expect(auge.y).toBeGreaterThanOrEqual(feld.y - 1)
    expect(auge.width).toBeGreaterThanOrEqual(44)
    const textEndeAbstand = await eingabe.evaluate(el => parseFloat(getComputedStyle(el).paddingRight))
    expect(textEndeAbstand).toBeGreaterThanOrEqual(auge.width)

    await designPruefung(page, testInfo, 'token-manuell-sichtbar', {
        primaerAktion: page.getByRole('button', { name: 'Anmelden', exact: true }),
    })

    await page.getByRole('button', { name: 'Anmelden', exact: true }).click()
    await expect.poll(() => page.evaluate(() => localStorage.getItem('zeiterfassung_token'))).toBe(code)
    expect(gepruefterCode).toBe(code)
})
