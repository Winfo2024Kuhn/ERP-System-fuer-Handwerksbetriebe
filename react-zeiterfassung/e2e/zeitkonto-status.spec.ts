import { expect, test } from './hilfen/test'

const configuredStatus = { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null }

async function vorbereiten(page: import('@playwright/test').Page, status = configuredStatus, saldoData?: unknown) {
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
    })
    await page.route('**/api/**', route => {
        const url = route.request().url()
        if (url.includes('/api/zeiterfassung/buchungszeitfenster/')) return route.fulfill({ json: status })
        if (url.includes('/api/zeiterfassung/aktiv/')) return route.fulfill({ json: { aktiv: false } })
        if (url.includes('/api/zeiterfassung/heute/')) return route.fulfill({ json: { stunden: 0, minuten: 0 } })
        if (url.includes('/api/zeiterfassung/saldo/')) return route.fulfill({ json: saldoData || {
            urlaub: { jahresanspruch: 30, genommen: 10, geplant: 0, verbleibend: 20, krankheitsTage: 0, fortbildungsTage: 0 },
            monat: { name: 'August', monatNummer: 8, sollStunden: 160, istStunden: 168, differenz: 8 },
            gesamt: { istStunden: 168, sollStunden: 160, saldo: 8 },
            mitarbeiterName: 'Max Mustermann',
            jahr: 2026
        } })
        if (url.includes('/api/zeiterfassung/urlaub/')) return route.fulfill({ json: { anspruchTage: 30, genommenTage: 10, restTage: 20 } })
        if (url.includes('/api/feiertage')) return route.fulfill({ json: [] })
        if (url.includes('/api/abwesenheiten')) return route.fulfill({ json: [] })
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

test('Geschäftsführer ohne festes Zeitkonto kann stempeln, sieht Arbeitszeit auf Saldenpage und hat Direkturlaub', async ({ page }, testInfo) => {
    await vorbereiten(
        page,
        { fuehrtZeitkonto: true, eingerichtet: false, istGeschaeftsfuehrer: true, kontenGefuehrt: false, hinweis: null },
        // Exakt der Payload, den ZeiterfassungApiService fuer die Geschaeftsfuehrung
        // liefert: ohne jahresanspruch/verbleibend/korrektur, ohne sollStunden,
        // differenz und festgeschrieben - und ganz ohne gesamt-Block.
        {
            urlaub: {
                genommen: 10,
                geplant: 0,
                krankheitsTage: 0,
                fortbildungsTage: 0
            },
            monat: { name: 'August', monatNummer: 8, istStunden: 42.5 },
            mitarbeiterName: 'Max Mustermann',
            jahr: 2026
        }
    )
    await page.goto('/')
    await expect(page.getByText('Zeit erfassen')).toBeVisible()
    await expect(page.getByText('Saldenauswertung')).toBeVisible()

    // Zur Saldenpage navigieren
    await page.goto('salden')
    await expect(page.getByText('Erfasste Arbeitszeit')).toBeVisible()
    await expect(page.getByText('42.5h')).toBeVisible()
    await expect(page.getByText('Gesamtsaldo')).toHaveCount(0)
    await expect(page.getByText('Zeitausgleich beantragen')).toHaveCount(0)
    await expect(page.getByText('Anspruch')).toHaveCount(0)
    await expect(page.getByText('Frei')).toHaveCount(0)
    await expect(page.getByText('Monatsabschluss noch nicht erfolgt')).toHaveCount(0)

    // Zur Urlaubsseite navigieren
    await page.goto('urlaub')
    await expect(page.getByRole('button', { name: 'Urlaub eintragen' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Zeitausgleich' })).toHaveCount(0)
    await page.screenshot({ path: testInfo.outputPath('gf-mobile-ansicht.png'), fullPage: true })
})

