import type { Page } from '@playwright/test'
import { expect, test } from './hilfen/test'
import { designPruefung } from './hilfen/design'

/**
 * Handy-App: Die Lieferanten-Ansicht zeigt die Ansprechpartner mit Aufgabe und
 * Knöpfen zum Anrufen/Mailen (nur Anzeige). Dummy-Daten (DSGVO).
 */

const LANGER_NAME = 'Maximilian_Mustermann_Aussendienstbeauftragter_Sued'

const lieferanten = [
    {
        id: 31,
        lieferantenname: 'Muster Stahlhandel',
        ort: 'Würzburg',
        telefon: '0931 654321',
        ansprechpartner: [
            { id: 1, name: 'Max Mustermann', aufgabe: 'Außendienst', telefon: null, mobiltelefon: '0171 1234567', email: 'max@muster-stahl.example' },
            { id: 2, name: 'Erika Musterfrau', aufgabe: 'Innendienst', telefon: '0931 654321-12', mobiltelefon: null, email: null },
            { id: 3, name: LANGER_NAME, aufgabe: 'Buchhaltung und Mahnwesen für alle Niederlassungen', telefon: '0931 ', mobiltelefon: null, email: 'buchhaltung.niederlassungen@muster-stahl.example' },
        ],
    },
]

async function richteAppEin(page: Page) {
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
    })
    await page.route('**/api/**', route => {
        const pathname = new URL(route.request().url()).pathname
        if (pathname === '/api/zeiterfassung/lieferanten') return route.fulfill({ json: lieferanten })
        return route.fulfill({ json: [] })
    })
}

test('Lieferant zeigt Ansprechpartner mit Anruf- und Mail-Knöpfen', async ({ page }, testInfo) => {
    await richteAppEin(page)
    await page.goto('lieferanten?id=31')

    const karte = page.getByRole('region', { name: 'Ansprechpartner' })
    await expect(karte).toBeVisible()
    const max = karte.getByRole('listitem', { name: 'Max Mustermann' })
    await expect(max).toContainText('Außendienst')
    const handy = max.getByRole('link', { name: 'Max Mustermann anrufen: Handy 0171 1234567' })
    await expect(handy).toHaveAttribute('href', 'tel:0171 1234567')
    const hoehe = (await handy.boundingBox())?.height ?? 0
    expect(hoehe, 'Tippfläche mindestens 44 px').toBeGreaterThanOrEqual(44)
    await expect(max.getByRole('link', { name: /E-Mail an Max Mustermann/ })).toHaveAttribute('href', 'mailto:max@muster-stahl.example')
    // Reine Vorwahl ist keine Nummer.
    await expect(karte.getByRole('listitem', { name: LANGER_NAME }).getByRole('link', { name: /anrufen/ })).toHaveCount(0)

    await karte.scrollIntoViewIfNeeded()
    await designPruefung(page, testInfo, 'lieferant-ansprechpartner-handy')
})
