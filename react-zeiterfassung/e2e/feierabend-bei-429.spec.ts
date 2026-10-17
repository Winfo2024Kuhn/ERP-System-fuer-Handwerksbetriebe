import { expect, test } from './hilfen/test'
import type { Page } from '@playwright/test'

// Dummy-Daten (DSGVO): Max Mustermann.
// Ablauf aus dem Alltag: Abends stechen mehrere Kollegen im Firmen-WLAN gleichzeitig
// ab, das Handy-Gateway antwortet mit 429 („Zu viele Anfragen“). Früher landete der
// Feierabend dann endgültig in der Reparatur-Liste und die Buchung lief am Server
// weiter, bis der automatische Stopp sie beendete.

interface Mitschnitt {
    stops: Array<{ idempotencyKey: string; originalZeit: string }>
}

async function vorbereiten(page: Page): Promise<Mitschnitt> {
    const mitschnitt: Mitschnitt = { stops: [] }
    let serverBuchungLaeuft = true
    const start = new Date(Date.now() - 2 * 60 * 60_000).toISOString()

    await page.addInitScript((startZeit) => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max' }))
        localStorage.setItem('zeiterfassung_active_session', JSON.stringify({
            projektId: 77, projektName: 'Bauvorhaben Mustermann', kundenName: 'Max Mustermann', auftragsnummer: 'A-1',
            arbeitsgangId: 11, arbeitsgangName: 'Montage', startTime: startZeit, typ: null,
        }))
    }, start)

    await page.route('**/api/**', route => {
        const url = route.request().url()
        if (url.includes('/api/zeiterfassung/stop')) {
            mitschnitt.stops.push(route.request().postDataJSON())
            if (mitschnitt.stops.length === 1) {
                return route.fulfill({
                    status: 429,
                    headers: { 'Retry-After': '1' },
                    json: { error: 'Zu viele Anfragen. Bitte kurz warten.', retryAfterSeconds: 1 },
                })
            }
            serverBuchungLaeuft = false
            return route.fulfill({ json: { id: 400, stunden: 2, status: 'gestoppt' } })
        }
        if (url.includes('/api/zeiterfassung/aktiv/')) {
            return route.fulfill({
                json: serverBuchungLaeuft
                    ? { id: 400, projektId: 77, projektName: 'Bauvorhaben Mustermann', arbeitsgangId: 11, arbeitsgangName: 'Montage', startZeit: start, typ: 'ARBEIT' }
                    : { aktiv: false },
            })
        }
        if (url.includes('/api/zeiterfassung/buchungszeitfenster/')) {
            return route.fulfill({ json: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null } })
        }
        if (url.includes('/api/zeiterfassung/projekte')) return route.fulfill({ json: [] })
        if (url.includes('/api/zeiterfassung/heute/')) return route.fulfill({ json: { stunden: 0, minuten: 0 } })
        if (url.includes('/api/mitarbeiter/by-token/')) return route.fulfill({ json: { id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true } })
        return route.fulfill({ json: [] })
    })
    return mitschnitt
}

/** Zählt die Einträge in einem Store der Handy-Datenbank (ohne App-Code, nur IndexedDB). */
function zaehleEintraege(page: Page, store: 'pending' | 'failed') {
    return page.evaluate((name) => new Promise<number>((resolve, reject) => {
        const anfrage = indexedDB.open('zeiterfassung-db')
        anfrage.onerror = () => reject(anfrage.error)
        anfrage.onsuccess = () => {
            const db = anfrage.result
            if (!db.objectStoreNames.contains(name)) { db.close(); resolve(0); return }
            const zaehlen = db.transaction(name, 'readonly').objectStore(name).count()
            zaehlen.onsuccess = () => { db.close(); resolve(zaehlen.result) }
            zaehlen.onerror = () => { db.close(); reject(zaehlen.error) }
        }
    }), store)
}

test('Feierabend bei „zu vielen Anfragen“ wird gespeichert und beim nächsten Abgleich übertragen', async ({ page }, testInfo) => {
    const mitschnitt = await vorbereiten(page)
    await page.goto('/')

    const feierabend = page.getByRole('button', { name: /Feierabend/ })
    await expect(feierabend).toBeVisible()
    await feierabend.click()

    // Der Handwerker sieht ehrlich, dass der Feierabend gespeichert ist und nachgereicht wird …
    await expect(page.getByText(/Feierabend um \d\d:\d\d gespeichert/)).toBeVisible()
    await expect(page.getByText('Bauvorhaben Mustermann')).toHaveCount(0)
    await page.screenshot({ path: testInfo.outputPath('feierabend-429-gespeichert.png'), fullPage: true })

    // … und der Abgleich überträgt ihn mit derselben Kennung (der Server erkennt Doppeltes).
    await expect.poll(() => mitschnitt.stops.length).toBe(2)
    expect(mitschnitt.stops[1].idempotencyKey).toBe(mitschnitt.stops[0].idempotencyKey)
    expect(mitschnitt.stops[1].originalZeit).toBe(mitschnitt.stops[0].originalZeit)
    await expect.poll(() => zaehleEintraege(page, 'pending')).toBe(0)

    // Nicht in der Reparatur-Liste gelandet, und nach dem Neuladen kommt die Buchung nicht zurück.
    expect(await zaehleEintraege(page, 'failed')).toBe(0)
    await expect(page.getByText(/konnte nicht gespeichert werden/)).toHaveCount(0)
    await expect(page.getByText('Zeit erfassen')).toBeVisible()
    await expect(page.getByText('Bauvorhaben Mustermann')).toHaveCount(0)
    await page.screenshot({ path: testInfo.outputPath('feierabend-429-uebertragen.png'), fullPage: true })
})
