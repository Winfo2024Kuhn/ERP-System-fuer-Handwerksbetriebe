import { expect, test } from './hilfen/test'
import type { Page } from '@playwright/test'
import { designPruefung } from './hilfen/design'

// Dummy-Daten (DSGVO): Max Mustermann, Erika Musterfrau.
const projekte = [
    { id: 77, name: 'Bauvorhaben Mustermann', projektNummer: 'A-1', kundenName: 'Max Mustermann', abgeschlossen: false },
    { id: 88, name: 'Carport Musterweg', projektNummer: 'A-2', kundenName: 'Erika Musterfrau', abgeschlossen: false },
]
const arbeitsgaenge = [{ id: 11, beschreibung: 'Montage' }, { id: 12, beschreibung: 'Schweißen' }]
const laufend = {
    id: 400, projektId: 77, projektName: 'Bauvorhaben Mustermann', kundenName: 'Max Mustermann', auftragsnummer: 'A-1',
    arbeitsgangId: 11, arbeitsgangName: 'Montage', startZeit: new Date(Date.now() - 60 * 60_000).toISOString(), typ: 'ARBEIT',
}

/**
 * Stubbt den Server. `netz.weg = true` simuliert den Keller: Das Handy meldet sich
 * „online“, aber jede Anfrage scheitert („Balken da, nichts kommt durch“).
 */
async function vorbereiten(page: Page, netz: { weg: boolean }) {
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
    })
    await page.route('**/api/**', route => {
        if (netz.weg) return route.abort('failed')
        const url = route.request().url()
        if (url.includes('/api/zeiterfassung/aktiv/')) return route.fulfill({ json: laufend })
        if (url.includes('/api/zeiterfassung/buchungszeitfenster/')) {
            return route.fulfill({ json: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null } })
        }
        if (url.includes('/api/zeiterfassung/projekte')) return route.fulfill({ json: projekte })
        if (url.includes('/api/zeiterfassung/kategorien')) return route.fulfill({ json: [] })
        if (url.includes('/api/zeiterfassung/arbeitsgaenge')) return route.fulfill({ json: arbeitsgaenge })
        if (url.includes('/api/zeiterfassung/heute/')) return route.fulfill({ json: { stunden: 1, minuten: 0 } })
        if (url.includes('/api/zeiterfassung/ueberschneidungen')) return route.fulfill({ json: [] })
        if (url.includes('/api/')) return route.fulfill({ json: {} })
        return route.continue()
    })
}

/** Wartet, bis der erste Abgleich die Stammdaten auf dem Handy abgelegt hat. */
async function warteAufAbgleich(page: Page) {
    await expect.poll(() => page.evaluate(() => new Promise<boolean>(resolve => {
        const anfrage = indexedDB.open('zeiterfassung-db')
        anfrage.onerror = () => resolve(false)
        anfrage.onsuccess = () => {
            const db = anfrage.result
            if (!db.objectStoreNames.contains('master_data')) { db.close(); resolve(false); return }
            const lesen = db.transaction('master_data').objectStore('master_data').get('last_sync')
            lesen.onsuccess = () => { db.close(); resolve(!!lesen.result) }
            lesen.onerror = () => { db.close(); resolve(false) }
        }
    })), { timeout: 15_000 }).toBe(true)
}

test('ohne Verbindung: Auftrag wechseln mit Projekten vom letzten Abgleich', async ({ page }, testInfo) => {
    const netz = { weg: false }
    await vorbereiten(page, netz)
    await page.goto('/')
    await expect(page.getByText('Bauvorhaben Mustermann')).toBeVisible()
    await warteAufAbgleich(page)

    // Ab in den Keller
    netz.weg = true
    await page.getByRole('button', { name: 'Auftrag' }).click()

    await expect(page.getByText(/Ohne Verbindung – Stand .* Uhr\. Deine Buchung wird übertragen, sobald wieder Netz da ist\./)).toBeVisible()
    await expect(page.getByText('Carport Musterweg')).toBeVisible()
    await expect(page.getByText('Keine neue Zeitbuchung möglich')).toHaveCount(0)
    await designPruefung(page, testInfo, 'ohne-verbindung-projektliste')

    // Info-Knopf erklärt, was passiert
    await page.getByRole('button', { name: 'Mehr zu: Ohne Verbindung buchen' }).click()
    await expect(page.getByRole('dialog').getByText(/schickt die App die Buchung von selbst ans Büro/)).toBeVisible()
    await designPruefung(page, testInfo, 'ohne-verbindung-info', { strengePruefungen: false })
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toHaveCount(0)

    await page.getByText('Carport Musterweg').click()
    await page.getByText('Ohne Kategorie fortfahren →').click()
    await page.getByText('Schweißen').click()
    await page.getByRole('button', { name: 'Auftrag wechseln' }).click()

    // Zurück im Dashboard: neue Buchung läuft auf dem Handy, Pause und Wechsel bleiben da
    await expect(page.getByText('Carport Musterweg')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Pause' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Tätigkeit' })).toBeVisible()
    await expect(page.getByText('Zeiterfassung derzeit nicht verfügbar')).toHaveCount(0)
    const session = await page.evaluate(() => JSON.parse(localStorage.getItem('zeiterfassung_active_session') || '{}'))
    expect(session.projektId).toBe(88)
    expect(String(session.id)).toMatch(/^offline-/)
})

test('ohne Verbindung und ohne je abgeglichen zu haben: kein falsches „nicht eingerichtet“', async ({ page }, testInfo) => {
    await vorbereiten(page, { weg: true })
    await page.goto('/')

    await expect(page.getByText('Zeit erfassen')).toBeVisible()
    await expect(page.getByText('Zeiterfassung derzeit nicht verfügbar')).toHaveCount(0)
    await expect(page.getByText(/noch keine Arbeitszeit eingerichtet/)).toHaveCount(0)

    await page.getByText('Zeit erfassen').click()
    await expect(page.getByText(/Noch keine Projekte auf dem Handy gespeichert/)).toBeVisible()
    await expect(page.getByText('Keine neue Zeitbuchung möglich')).toHaveCount(0)
    await designPruefung(page, testInfo, 'ohne-verbindung-noch-nie-abgeglichen')
})
