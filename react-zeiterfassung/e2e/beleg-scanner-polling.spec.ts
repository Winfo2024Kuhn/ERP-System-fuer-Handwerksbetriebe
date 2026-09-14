import { expect, test, type Page } from '@playwright/test'

// Simuliert einen einzelnen eigenen Beleg, der beim Mount noch PENDING ist
// und erst ab dem dritten GET /mobile/belege auf DONE springt — mit Betrag,
// Datum und einer KI-Lesung, die vom gewaehlten Lieferanten abweicht.
// Dummy-Daten (DSGVO): "Musterbaustoffe GmbH" gewaehlt, "Musterbaumarkt" als
// KI-Lesung.
function vorbereiten(page: Page): () => number {
    let belegeCalls = 0

    page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
    })

    page.route('**/*', route => {
        const req = route.request()
        const url = req.url()
        if (!url.includes('127.0.0.1') && !url.includes('localhost')) return route.abort()

        if (url.includes('/api/buchhaltung/mobile/me/permissions')) {
            return route.fulfill({ json: { darfScannen: true, darfSehen: true } })
        }

        if (url.includes('/api/buchhaltung/mobile/belege') && req.method() === 'GET') {
            belegeCalls++
            const fertig = belegeCalls >= 3
            return route.fulfill({
                json: [{
                    id: 1,
                    originalDateiname: 'musterbeleg.pdf',
                    uploadDatum: '2026-08-02T08:00:00',
                    status: 'ERFASST',
                    kiAnalyseStatus: fertig ? 'DONE' : 'PENDING',
                    aufteilungsModus: 'VOLLSTAENDIG',
                    lieferantName: 'Musterbaustoffe GmbH',
                    betragBrutto: fertig ? 12.34 : null,
                    belegDatum: fertig ? '2026-08-02' : null,
                    kiVorgeschlagenerLieferant: fertig ? 'Musterbaumarkt' : null,
                }],
            })
        }

        if (url.includes('/api/')) return route.fulfill({ json: {} })
        return route.continue()
    })

    return () => belegeCalls
}

test('Beleg-Zeile aktualisiert sich ohne Zutun nach der KI-Analyse und stoppt dann das Polling', async ({ page }, testInfo) => {
    const anzahlAbrufe = vorbereiten(page)

    // Kein fuehrender Slash: baseURL traegt schon /zeiterfassung/, sonst
    // sprengt ein absoluter Pfad den Base-Präfix (Vite meldet dann "did you
    // mean to visit /zeiterfassung/belege instead?").
    await page.goto('belege')

    await expect(page.getByText('KI liest Beleg…')).toBeVisible()

    // Kein Klick, kein Reload — die Zeile wechselt allein durchs Polling.
    await expect(page.getByText('12,34 € · 02.08.2026')).toBeVisible({ timeout: 20000 })
    await expect(page.getByText('KI hat "Musterbaumarkt" gelesen')).toBeVisible()
    await expect(page.getByText('Am PC prüfen')).toBeVisible()

    const abrufeNachDone = anzahlAbrufe()
    // Zwei volle Poll-Intervalle abwarten — bleibt der Zaehler stehen, ist
    // das Polling wirklich gestoppt und nicht nur zufaellig noch nicht dran.
    await page.waitForTimeout(7000)
    expect(anzahlAbrufe()).toBe(abrufeNachDone)

    await page.screenshot({ path: testInfo.outputPath('beleg-scanner-polling.png'), fullPage: true })
})
