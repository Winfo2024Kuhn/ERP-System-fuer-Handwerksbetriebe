import type { Page } from '@playwright/test'
import { test, expect } from './hilfen/test'
import { designPruefung } from './hilfen/design'

/**
 * Issue #270, Punkt 4 (Handy): Rest aus dem Vorjahr getrennt anzeigen – im
 * Urlaubsantrag, in der Saldenauswertung – und zwei Monate vor dem Verfall in
 * der Glocke daran erinnern. `verfallsdatum` ist der letzte Tag, an dem der
 * Rest genommen werden kann.
 *
 * DSGVO: nur Dummy-Daten (Max Mustermann). Ohne Backend, alles gestubbt.
 */

const RESTURLAUB = { verbleibend: 33.5, jahr: 2027, uebertragVorjahr: 3.5, ausVorjahrOffen: 3.5, verfallen: 0, verfallsdatum: '2027-03-31' }
const SALDO = {
    jahr: 2027,
    urlaub: { jahresanspruch: 30, genommen: 0, geplant: 0, verbleibend: 33.5, korrektur: 0, krankheitsTage: 0, fortbildungsTage: 0,
        uebertragVorjahr: 3.5, ausVorjahrOffen: 3.5, verfallen: 0, verfallsdatum: '2027-03-31' },
    monat: { name: 'Februar', monatNummer: 2, sollStunden: 160, istStunden: 150, differenz: -10, festgeschrieben: false },
    gesamt: { istStunden: 900, sollStunden: 880, saldo: 20, startDatum: '2026-01-01', vorlaeufig: true, geprueftBis: null },
}
const URLAUBSVERFALL = { resturlaubTage: 3.5, verfallsDatum: '2027-03-31', verfallsJahr: 2027, tageVerbleibend: 20, dringend: true }

async function vorbereiten(page: Page) {
    page.on('dialog', async dialog => {
        await dialog.dismiss()
        throw new Error(`Nativer Browserdialog: ${dialog.type()}`)
    })
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' }))
    })
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname
        if (path.includes('/by-token/')) return route.fulfill({ json: { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' } })
        if (path.includes('buchungszeitfenster')) return route.fulfill({ json: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null } })
        if (path.includes('/zeiterfassung/saldo/')) return route.fulfill({ json: SALDO })
        if (path.endsWith('/resturlaub')) return route.fulfill({ json: RESTURLAUB })
        if (path.includes('/urlaubsverfall/')) return route.fulfill({ json: URLAUBSVERFALL })
        if (path.includes('/aktiv/')) return route.fulfill({ json: { aktiv: false } })
        return route.fulfill({ json: [] })
    })
}

test('Urlaubsantrag: Rest aus dem Vorjahr mit letztem Tag', async ({ page }, testInfo) => {
    await vorbereiten(page)
    await page.goto('urlaub')

    await expect(page.getByText('33,5 Urlaubstage übrig')).toBeVisible()
    const hinweis = page.getByText('davon 3,5 Tage aus dem Vorjahr – bis 31.03.2027 nehmen, danach verfallen sie')
    await expect(hinweis).toBeVisible()
    await designPruefung(page, testInfo, 'urlaubsantrag-resturlaub-vorjahr')
})

test('Saldenauswertung: freie Tage mit Rest aus dem Vorjahr', async ({ page }, testInfo) => {
    await vorbereiten(page)
    await page.goto('salden')

    await expect(page.getByText('33,5 Tage frei, davon 3,5 Tage aus dem Vorjahr – bis 31.03.2027 nehmen, danach verfallen sie')).toBeVisible()
    await designPruefung(page, testInfo, 'salden-resturlaub-vorjahr')
})

test('Glocke: Erinnerung vor dem Verfall', async ({ page }, testInfo) => {
    await vorbereiten(page)
    await page.goto('')

    await expect(page.getByTestId('nachrichten-zaehler')).toHaveText('1')
    await page.getByRole('button', { name: /^Nachrichten/ }).tap()
    const sheet = page.getByRole('dialog', { name: 'Nachrichten' })
    await expect(sheet.getByText('Dringend: Urlaub verfällt bald')).toBeVisible()
    await expect(sheet.getByText('Du hast noch 3,5 Resturlaubstage vom Vorjahr. Nimm sie bis 31.03.2027, danach verfallen sie (noch 20 Tage).')).toBeVisible()
    await designPruefung(page, testInfo, 'glocke-urlaubsverfall')
})
