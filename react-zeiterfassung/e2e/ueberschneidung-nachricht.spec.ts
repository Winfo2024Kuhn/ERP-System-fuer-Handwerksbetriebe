import { expect, test } from './hilfen/test'
import { designPruefung } from './hilfen/design'

/**
 * „Handy gewinnt“: Hat eine (offline gestempelte) Handy-Buchung einen Eintrag im Büro
 * gekürzt, steht das als Nachricht in der Glocke. „Gelesen“ meldet es dem Server.
 * Dummy-Daten (DSGVO): Max Mustermann.
 */
test('Überschneidung erscheint in der Glocke und lässt sich als gelesen markieren', async ({ page }, testInfo) => {
    const gesehen: string[] = []
    let ueberschneidungen = [{
        id: 3,
        von: '2026-10-28T10:00:00',
        bis: '2026-10-28T12:00:00',
        titel: 'Überschneidung am 28.10.2026',
        text: 'Deine Handy-Buchung 10:00–12:00 hatte Vorrang. Büro-Eintrag „Musterhaus · Montage“ 09:00–11:00 → jetzt 09:00–10:00.',
        erstelltAm: '2026-10-28T14:03:11',
    }]

    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
    })
    await page.route('**/api/**', route => {
        const anfrage = route.request()
        const url = anfrage.url()
        if (url.includes('/api/zeiterfassung/ueberschneidungen/') && anfrage.method() === 'PUT') {
            gesehen.push(url)
            ueberschneidungen = []
            return route.fulfill({ status: 204 })
        }
        if (url.includes('/api/zeiterfassung/ueberschneidungen')) return route.fulfill({ json: ueberschneidungen })
        if (url.includes('/api/zeiterfassung/buchungszeitfenster/')) {
            return route.fulfill({ json: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null } })
        }
        if (url.includes('/api/zeiterfassung/aktiv/')) return route.fulfill({ json: { aktiv: false } })
        if (url.includes('/api/zeiterfassung/heute/')) return route.fulfill({ json: { stunden: 0, minuten: 0 } })
        if (url.includes('/api/urlaub/antraege/neue-entscheidungen')) return route.fulfill({ json: [] })
        if (/\/api\/zeiterfassung\/(projekte|kategorien|arbeitsgaenge)/.test(url)) return route.fulfill({ json: [] })
        return route.fulfill({ json: {} })
    })

    await page.goto('/')
    const glocke = page.getByRole('button', { name: 'Nachrichten' })
    await expect(page.getByTestId('nachrichten-zaehler')).toHaveText('1')
    await glocke.tap()

    const liste = page.getByRole('dialog', { name: 'Nachrichten' })
    await expect(liste.getByText('Überschneidung am 28.10.2026')).toBeVisible()
    await expect(liste.getByText(/Deine Handy-Buchung 10:00–12:00 hatte Vorrang/)).toBeVisible()
    await designPruefung(page, testInfo, 'ueberschneidung-nachricht', { strengePruefungen: false })

    await liste.getByRole('button', { name: 'Gelesen: Überschneidung am 28.10.2026' }).tap()
    await expect.poll(() => gesehen).toEqual([expect.stringContaining('/api/zeiterfassung/ueberschneidungen/3/gesehen')])
    await expect(liste.getByText('Überschneidung am 28.10.2026')).toHaveCount(0)
    await expect(page.getByTestId('nachrichten-zaehler')).toHaveCount(0)
})
