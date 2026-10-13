import type { Page } from '@playwright/test'
import { test, expect } from './hilfen/test'
import { designPruefung, uebergaengeAusklingenLassen } from './hilfen/design'

/**
 * Issue #270 (Handy): eigenen offenen Antrag zurückziehen und die Entscheidungen
 * des Büros in der Nachrichten-Glocke sehen.
 *
 * - „Zurückziehen" fragt über den eigenen Bestätigungsdialog zurück, ruft
 *   PUT /api/urlaub/antraege/{id}/storno und lädt die Liste neu; der Antrag
 *   steht danach unter „Storniert" (Klartext, kein Rohwert).
 * - Lehnt der Server ab, steht seine Meldung im schwebenden Toast.
 * - Die Glocke neben dem Zahnrad zählt neue Nachrichten (Entscheidungen +
 *   Urlaubsverfall). „Gelesen" ruft je Entscheidung
 *   PUT /api/urlaub/antraege/{id}/entscheidung-gesehen – auch „Alle als gelesen"
 *   geht einzeln über die angezeigten Einträge, nie über den Sammel-Endpunkt.
 *
 * DSGVO: nur Dummy-Daten (Max Mustermann). Ohne Backend, alles gestubbt.
 */

const ANTRAG_OFFEN = {
    id: 1, mitarbeiter: { id: 1, vorname: 'Max', nachname: 'Mustermann' },
    vonDatum: '2026-08-03', bisDatum: '2026-08-14', bemerkung: 'Sommerurlaub', status: 'OFFEN', typ: 'URLAUB',
    entschiedenAm: null, entscheidungGesehen: false,
}
const ANTRAG_GENEHMIGT = {
    ...ANTRAG_OFFEN, id: 2, vonDatum: '2026-05-04', bisDatum: '2026-05-08', bemerkung: '', status: 'GENEHMIGT',
    entschiedenAm: '2026-04-20T09:00:00', entscheidungGesehen: false,
}
// DTO von /neue-entscheidungen: nur id, typ, status, vonDatum, bisDatum, entschiedenAm.
const ENTSCHEIDUNGEN = [
    { id: 2, typ: 'URLAUB', status: 'GENEHMIGT', vonDatum: '2026-05-04', bisDatum: '2026-05-08', entschiedenAm: '2026-04-20T09:00:00' },
    { id: 3, typ: 'ZEITAUSGLEICH', status: 'ABGELEHNT', vonDatum: '2026-06-01', bisDatum: '2026-06-01', entschiedenAm: '2026-04-21T09:00:00' },
]

const URLAUBSVERFALL = { resturlaubTage: 3.5, verfallsDatum: '2027-03-31', verfallsJahr: 2027, tageVerbleibend: 20, dringend: false }

async function vorbereiten(page: Page, opts: { stornoFehler?: boolean; gelesenFehler?: boolean } = {}) {
    const statusById: Record<number, string> = { 1: 'OFFEN', 2: 'GENEHMIGT' }
    const puts: string[] = []
    const gesehen = new Set<number>()

    page.on('dialog', async dialog => {
        await dialog.dismiss()
        throw new Error(`Nativer Browserdialog: ${dialog.type()}`)
    })
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' }))
    })
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url())
        const path = url.pathname
        const methode = route.request().method()

        if (methode === 'PUT') {
            puts.push(path + url.search)
            const einzeln = path.match(/\/api\/urlaub\/antraege\/(\d+)\/entscheidung-gesehen$/)
            if (einzeln) {
                if (opts.gelesenFehler) return route.fulfill({ status: 500, json: { status: 500, message: 'Serverfehler' } })
                gesehen.add(Number(einzeln[1]))
                return route.fulfill({ status: 204, body: '' })
            }
            const storno = path.match(/\/api\/urlaub\/antraege\/(\d+)\/storno$/)
            if (storno) {
                if (opts.stornoFehler) return route.fulfill({ status: 400, json: { status: 400, message: 'Dieser Antrag ist bereits storniert.' } })
                statusById[Number(storno[1])] = 'STORNIERT'
                return route.fulfill({ json: { ...ANTRAG_OFFEN, status: 'STORNIERT' } })
            }
            return route.fulfill({ json: {} })
        }
        if (path.includes('/by-token/')) return route.fulfill({ json: { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' } })
        if (path.includes('buchungszeitfenster')) return route.fulfill({ json: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null } })
        if (path.endsWith('/neue-entscheidungen')) return route.fulfill({ json: ENTSCHEIDUNGEN.filter(e => !gesehen.has(e.id)) })
        if (path.includes('/urlaubsverfall/')) return route.fulfill({ json: URLAUBSVERFALL })
        if (path.endsWith('/resturlaub')) return route.fulfill({ json: { verbleibend: 12.5, jahr: 2026 } })
        if (path.endsWith('/api/urlaub/antraege')) {
            return route.fulfill({ json: [{ ...ANTRAG_OFFEN, status: statusById[1] }, { ...ANTRAG_GENEHMIGT, status: statusById[2] }] })
        }
        if (path.includes('/aktiv/')) return route.fulfill({ json: { aktiv: false } })
        return route.fulfill({ json: [] })
    })
    return puts
}

test('Zurückziehen: Rückfrage, PUT …/storno, danach „Storniert" in Klartext', async ({ page }, testInfo) => {
    const puts = await vorbereiten(page)
    await page.goto('urlaub')

    // Halbe Urlaubstage mit Komma.
    await expect(page.getByText('12,5 Urlaubstage übrig')).toBeVisible()

    await page.getByRole('button', { name: 'Übersicht' }).tap()
    const zurueckziehen = page.getByRole('button', { name: 'Zurückziehen', exact: true })
    await expect(zurueckziehen).toHaveCount(1)
    await expect(page.getByText('STORNIERT', { exact: true })).toHaveCount(0)

    // Info-Knopf erklärt die Grenze (nur offene Anträge).
    await page.getByRole('button', { name: 'Mehr zu: Zurückziehen' }).tap()
    const info = page.getByRole('dialog')
    await expect(info).toContainText('meldest du dich im Büro')
    await designPruefung(page, testInfo, 'urlaubsantrag-zurueckziehen-info', { primaerAktion: info.getByText('Zurückziehen').first() })
    await page.keyboard.press('Escape')
    await expect(info).toHaveCount(0)

    await zurueckziehen.tap()
    const rueckfrage = page.getByRole('dialog')
    await expect(rueckfrage).toContainText('Antrag zurückziehen?')
    await designPruefung(page, testInfo, 'urlaubsantrag-zurueckziehen-rueckfrage', { primaerAktion: rueckfrage.getByRole('button', { name: 'Zurückziehen' }) })
    await rueckfrage.getByRole('button', { name: 'Abbrechen' }).tap()
    expect(puts).toEqual([])

    await zurueckziehen.tap()
    await page.getByRole('dialog').getByRole('button', { name: 'Zurückziehen' }).tap()

    await expect(page.getByText('Antrag zurückgezogen.')).toBeVisible()
    expect(puts).toEqual(['/api/urlaub/antraege/1/storno'])
    // Liste neu geladen: kein offener Antrag mehr, dafür „Storniert" am Antrag.
    await expect(page.getByRole('button', { name: 'Zurückziehen', exact: true })).toHaveCount(0)
    await page.getByRole('button', { name: 'Storniert' }).tap()
    await expect(page.getByText('03.08.2026 - 14.08.2026')).toBeVisible()
    await expect(page.getByText('STORNIERT', { exact: true })).toHaveCount(0)
})

test('Zurückziehen zeigt die Meldung des Servers, wenn er ablehnt', async ({ page }) => {
    await vorbereiten(page, { stornoFehler: true })
    await page.goto('urlaub')
    await page.getByRole('button', { name: 'Übersicht' }).tap()

    await page.getByRole('button', { name: 'Zurückziehen', exact: true }).tap()
    await page.getByRole('dialog').getByRole('button', { name: 'Zurückziehen' }).tap()

    await expect(page.getByRole('alert').filter({ hasText: 'Dieser Antrag ist bereits storniert.' })).toBeVisible()
    // Der Antrag bleibt offen und der Knopf bedienbar.
    await expect(page.getByRole('button', { name: 'Zurückziehen', exact: true })).toBeEnabled()
})

const glocke = (page: Page) => page.getByRole('button', { name: /^Nachrichten/ })

test('Glocke: Zähler neben dem Zahnrad, Nachrichten einzeln und alle als gelesen', async ({ page }, testInfo) => {
    const puts = await vorbereiten(page)
    await page.goto('')

    // Keine Entscheidungskarten mehr auf dem Dashboard – alles steckt in der Glocke.
    await expect(page.getByTestId('nachrichten-zaehler')).toHaveText('3')
    await expect(page.getByText('Dein Urlaub vom 04.05.2026 bis 08.05.2026 wurde genehmigt.')).toHaveCount(0)
    const glockeBox = (await glocke(page).boundingBox())!, zahnradBox = (await page.getByRole('button', { name: 'Einstellungen' }).boundingBox())!
    expect(glockeBox.x + glockeBox.width).toBeLessThanOrEqual(zahnradBox.x)
    expect(glockeBox.width).toBeGreaterThanOrEqual(44)
    expect(glockeBox.height).toBeGreaterThanOrEqual(44)
    await designPruefung(page, testInfo, 'dashboard-glocke', { primaerAktion: glocke(page) })

    await glocke(page).tap()
    const sheet = page.getByRole('dialog', { name: 'Nachrichten' })
    await expect(sheet.getByTestId('nachricht')).toHaveCount(3)
    await expect(sheet.getByText('Dein Urlaub vom 04.05.2026 bis 08.05.2026 wurde genehmigt.')).toBeVisible()
    await expect(sheet.getByText('Dein Zeitausgleich am 01.06.2026 wurde abgelehnt.')).toBeVisible()
    await expect(sheet.getByText(/Du hast noch 3,5 Resturlaubstage vom Vorjahr/)).toBeVisible()
    await designPruefung(page, testInfo, 'glocke-nachrichten-sheet', { primaerAktion: sheet.getByRole('button', { name: 'Alle als gelesen markieren' }) })

    await sheet.getByRole('button', { name: 'Gelesen: Urlaub genehmigt' }).tap()
    await expect(sheet.getByTestId('nachricht')).toHaveCount(2)
    expect(puts).toEqual(['/api/urlaub/antraege/2/entscheidung-gesehen'])

    await sheet.getByRole('button', { name: 'Alle als gelesen markieren' }).tap()
    await expect(sheet.getByText('Keine neuen Nachrichten.')).toBeVisible()
    expect(puts).toEqual(['/api/urlaub/antraege/2/entscheidung-gesehen', '/api/urlaub/antraege/3/entscheidung-gesehen'])
    await designPruefung(page, testInfo, 'glocke-leer')
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('nachrichten-zaehler')).toHaveCount(0)

    // Der gelesene Urlaubsverfall bleibt auf dem Gerät gelesen, auch nach Neuladen.
    await page.reload()
    await expect(page.getByText('Zeit erfassen')).toBeVisible()
    await expect(page.getByTestId('nachrichten-zaehler')).toHaveCount(0)
})

test('Glocke: Tipp auf eine Entscheidung führt in die Antragsübersicht', async ({ page }) => {
    await vorbereiten(page)
    await page.goto('')
    await glocke(page).tap()
    await page.getByRole('dialog', { name: 'Nachrichten' }).getByText('Dein Zeitausgleich am 01.06.2026 wurde abgelehnt.').tap()
    await expect(page).toHaveURL(/\/urlaub\?ansicht=uebersicht$/)
    await expect(page.getByRole('button', { name: 'Zurückziehen', exact: true })).toBeVisible()
})

test('Schwebender Toast: „Gelesen" schlägt fehl, Nachricht bleibt, nichts verschiebt sich', async ({ page }, testInfo) => {
    await vorbereiten(page, { gelesenFehler: true })
    await page.goto('')
    await glocke(page).tap()
    const sheet = page.getByRole('dialog', { name: 'Nachrichten' })
    await expect(sheet.getByTestId('nachricht')).toHaveCount(3)
    await uebergaengeAusklingenLassen(page) // Sheet fertig hereingeglitten
    const sheetVorher = (await sheet.boundingBox())!
    const kopfVorher = (await page.locator('header').boundingBox())!

    await sheet.getByRole('button', { name: 'Gelesen: Urlaub genehmigt' }).tap()

    const toast = page.locator('[data-mobile-toasts]')
    await expect(toast).toContainText('Das konnte gerade nicht gespeichert werden. Bitte später noch einmal versuchen.')
    expect(await toast.evaluate(el => getComputedStyle(el).position)).toBe('fixed')
    await expect(sheet.getByTestId('nachricht')).toHaveCount(3)
    // Sheet und Kopfzeile bleiben, wo sie waren – der Toast schwebt darüber.
    expect((await sheet.boundingBox())!.y).toBe(sheetVorher.y)
    expect((await page.locator('header').boundingBox())!.y).toBe(kopfVorher.y)
    // Bei offenem Sheet blendet der modale Dialog den Rest für Screenreader aus (aria-hidden),
    // sichtbar und bedienbar bleibt das X trotzdem – daher über das Label statt über die Rolle.
    const schliessen = toast.locator('button[aria-label="Meldung schließen"]')
    await designPruefung(page, testInfo, 'toast-schwebt-ueber-sheet', { primaerAktion: schliessen })

    // Fehler bleibt stehen, bis man ihn per X schließt.
    await schliessen.tap()
    await expect(toast).toBeHidden()
})
