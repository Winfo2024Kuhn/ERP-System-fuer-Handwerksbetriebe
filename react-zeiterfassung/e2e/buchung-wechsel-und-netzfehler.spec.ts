import { expect, test } from './hilfen/test'
import type { Page, Request } from '@playwright/test'

// Dummy-Daten (DSGVO): Max Mustermann, Erika Musterfrau.
const projekte = [
    { id: 77, name: 'Bauvorhaben Mustermann', projektNummer: 'A-1', kundenName: 'Max Mustermann', abgeschlossen: false },
    { id: 88, name: 'Carport Musterweg', projektNummer: 'A-2', kundenName: 'Erika Musterfrau', abgeschlossen: false },
]
const arbeitsgaenge = [{ id: 11, beschreibung: 'Montage' }, { id: 12, beschreibung: 'Schweißen' }]

interface Buchung { id: number; projektId: number; projektName: string; arbeitsgangId: number; arbeitsgangName: string }

async function vorbereiten(page: Page, optionen: { startUndStopNetzfehler?: boolean; aktiv?: Buchung | null } = {}) {
    let aktiv: Buchung | null = optionen.aktiv ?? null
    const startRequests: Request[] = []

    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
    })
    await page.route('**/api/**', route => {
        const url = route.request().url()
        if (url.includes('/api/zeiterfassung/start') || url.includes('/api/zeiterfassung/stop')) {
            if (optionen.startUndStopNetzfehler) return route.abort('failed')
            if (url.includes('/start')) {
                startRequests.push(route.request())
                const body = route.request().postDataJSON()
                const projekt = projekte.find(p => p.id === body.projektId)!
                const ag = arbeitsgaenge.find(a => a.id === body.arbeitsgangId)!
                aktiv = { id: 500, projektId: projekt.id, projektName: projekt.name, arbeitsgangId: ag.id, arbeitsgangName: ag.beschreibung }
                return route.fulfill({ json: { id: 500 } })
            }
            aktiv = null
            return route.fulfill({ json: { status: 'gestoppt' } })
        }
        if (url.includes('/api/zeiterfassung/aktiv/')) {
            return route.fulfill({ json: aktiv ? { ...aktiv, startZeit: new Date().toISOString(), typ: 'ARBEIT' } : { aktiv: false } })
        }
        if (url.includes('/api/zeiterfassung/buchungszeitfenster/')) {
            return route.fulfill({ json: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null } })
        }
        if (url.includes('/api/zeiterfassung/projekte')) return route.fulfill({ json: projekte })
        if (url.includes('/api/zeiterfassung/kategorien')) return route.fulfill({ json: [] })
        if (url.includes('/api/zeiterfassung/arbeitsgaenge')) return route.fulfill({ json: arbeitsgaenge })
        if (url.includes('/api/zeiterfassung/heute/')) return route.fulfill({ json: { stunden: 0, minuten: 0 } })
        if (url.includes('/api/')) return route.fulfill({ json: {} })
        return route.continue()
    })
    return { startRequests }
}

test('Tätigkeit wählen: „Ändern“ startet die neue Buchung auf dem gewählten Projekt', async ({ page }, testInfo) => {
    const { startRequests } = await vorbereiten(page, {
        aktiv: { id: 400, projektId: 77, projektName: 'Bauvorhaben Mustermann', arbeitsgangId: 11, arbeitsgangName: 'Montage' },
    })
    await page.goto('/')
    await expect(page.getByText('Bauvorhaben Mustermann')).toBeVisible()

    await page.getByRole('button', { name: 'Tätigkeit' }).click()
    await expect(page.getByRole('heading', { name: 'Tätigkeit wählen' })).toBeVisible()
    await page.getByRole('button', { name: 'Ändern' }).click()
    await page.getByRole('button', { name: /Carport Musterweg/ }).click()
    // Zurück in der Tätigkeitsliste – das gewählte Projekt steht oben
    await expect(page.getByText('Carport Musterweg')).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('taetigkeit-projekt-gewaehlt.png'), fullPage: true })
    await page.getByRole('button', { name: 'Schweißen' }).click()

    await expect.poll(() => startRequests.length).toBe(1)
    expect(startRequests[0].postDataJSON()).toMatchObject({ projektId: 88, arbeitsgangId: 12, produktkategorieId: null })
    await expect(page.getByRole('heading', { name: 'Tätigkeit wählen' })).toHaveCount(0)
    await expect(page.getByText('Carport Musterweg')).toBeVisible()
    await expect(page.getByText('Schweißen')).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('taetigkeit-projekt-gewechselt.png'), fullPage: true })
})

test('Buchung starten: Netzfehler trotz „online“ speichert die Buchung offline', async ({ page }, testInfo) => {
    await vorbereiten(page, { startUndStopNetzfehler: true })
    await page.goto('/')

    await page.getByText('Zeit erfassen').click()
    await page.getByText('Carport Musterweg').click()
    await page.getByText('Ohne Kategorie fortfahren →').click()
    await page.getByText('Schweißen').click()
    await page.getByRole('button', { name: 'Zeiterfassung starten' }).click()

    // Kein Fehler-Toast, sondern zurück im Dashboard mit der laufenden
    // (noch nicht übertragenen) Buchung
    await expect(page.getByRole('button', { name: 'Tätigkeit' })).toBeVisible()
    await expect(page.getByText('Fehler beim Starten der Buchung')).toHaveCount(0)
    await expect(page.getByText('Carport Musterweg')).toBeVisible()
    await expect(page.getByText('Schweißen')).toBeVisible()
    const session = await page.evaluate(() => JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}'))
    expect(session.projektId).toBe(88)
    expect(String(session.id)).toMatch(/^offline-/)
    await page.screenshot({ path: testInfo.outputPath('start-offline-gespeichert.png'), fullPage: true })
})
