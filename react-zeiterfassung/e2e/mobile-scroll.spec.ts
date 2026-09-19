import { expect, test } from './hilfen/test'
import { designPruefung } from './hilfen/design'

const projekte = Array.from({ length: 20 }, (_, index) => ({
    id: index + 1,
    name: `Auftrag Musterhaus ${index + 1}`,
    projektNummer: `P-2026-${String(index + 1).padStart(3, '0')}`,
    kundenName: 'Max Mustermann',
    abgeschlossen: false,
}))

test('Projektliste: Auf dem Handy lässt sich die lange Liste per Wischgeste vertikal scrollen', async ({ page }, testInfo) => {
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
    })
    await page.route('**/api/**', route => {
        const pathname = new URL(route.request().url()).pathname
        return route.fulfill({ json: pathname === '/api/zeiterfassung/projekte' ? projekte : [] })
    })

    await page.goto('projekte')
    await expect(page.getByText('Auftrag Musterhaus 20')).toBeVisible()

    const scrollContainer = page.locator('div.flex-1.overflow-y-auto').first()
    await expect.poll(
        () => scrollContainer.evaluate(element => element.scrollHeight > element.clientHeight),
        { message: 'Die lange Projektliste benötigt einen eigenen Scrollbereich.' },
    ).toBe(true)
    await designPruefung(page, testInfo, 'projektliste-vor-wischgeste', { strengePruefungen: false })

    const bounds = await scrollContainer.boundingBox()
    expect(bounds, 'Der Scrollbereich muss sichtbar sein.').not.toBeNull()

    const cdp = await page.context().newCDPSession(page)
    const x = bounds!.x + bounds!.width / 2
    const startY = bounds!.y + bounds!.height * 0.75
    await cdp.send('Input.dispatchTouchEvent', {
        type: 'touchStart',
        touchPoints: [{ x, y: startY, radiusX: 1, radiusY: 1, force: 1, id: 1 }],
    })
    await cdp.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [{ x, y: startY - 250, radiusX: 1, radiusY: 1, force: 1, id: 1 }],
    })
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })

    await expect.poll(
        () => scrollContainer.evaluate(element => element.scrollTop),
        { message: 'Die Wischgeste muss die Projektliste nach oben bewegen.' },
    ).toBeGreaterThan(0)
    await page.screenshot({ path: testInfo.outputPath('projektliste-nach-wischgeste.png') })
})
