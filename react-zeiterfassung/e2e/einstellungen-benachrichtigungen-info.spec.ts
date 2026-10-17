import { test, expect } from './hilfen/test'
import { designPruefung } from './hilfen/design'

/**
 * Einstellungen → Benachrichtigungen: Der Info-Button erklärt, welche Nachrichten
 * das Büro-Programm von sich aus schickt (Pausen-Erinnerung nach Arbeitszeitgesetz,
 * Hinweis bei vergessenem Feierabend). Prüft Zeile und geöffnetes Sheet am Handy.
 *
 * DSGVO: nur Dummy-Daten (Max Mustermann). Ohne Backend, alles gestubbt.
 */
test('Info zu Benachrichtigungen erklärt Pausen- und Feierabend-Hinweise', async ({ page }, testInfo) => {
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' }))
    })
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname
        if (path.includes('/by-token/')) return route.fulfill({ json: { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' } })
        if (path.includes('/aktiv/')) return route.fulfill({ json: { aktiv: false } })
        return route.fulfill({ json: [] })
    })

    await page.goto('einstellungen')
    await expect(page.getByText('Pausen-Erinnerungen', { exact: false })).toBeVisible()
    const info = page.getByRole('button', { name: 'Mehr zu: Benachrichtigungen' })
    await expect(info).toBeVisible()
    await designPruefung(page, testInfo, 'einstellungen-benachrichtigungen')

    await info.click()
    const sheet = page.getByRole('dialog')
    await expect(sheet.getByText('Was passiert im Hintergrund?')).toBeVisible()
    await expect(sheet.getByText(/6 bzw\. 9 Stunden/)).toBeVisible()
    await expect(sheet.getByText(/keinen Feierabend gestempelt/)).toBeVisible()
    await expect(sheet.getByText('Was bewirkt das?')).toBeVisible()
    await designPruefung(page, testInfo, 'einstellungen-benachrichtigungen-info')

    await page.keyboard.press('Escape')
    await expect(sheet).toBeHidden()
    await expect(info).toBeFocused()
})
