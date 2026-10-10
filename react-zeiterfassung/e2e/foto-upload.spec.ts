import type { Page, Route } from '@playwright/test'
import { expect, test } from './hilfen/test'
import { designPruefung } from './hilfen/design'

// Dummy-Daten (DSGVO): Max Mustermann, Fotos sind ein winziges, gültiges JPEG.
const KLEINES_JPEG = Buffer.from(
    '/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=',
    'base64',
)

const datei = (name: string) => ({ name, mimeType: 'image/jpeg', buffer: KLEINES_JPEG })

interface Aufbau {
    /** Dateinamen, auf die der Server mit 413 antwortet (wie das Gateway bei > 25 MiB) */
    zuGross?: string[]
    /** Wartet mit der Antwort auf das n-te Foto (1-basiert), bis freigegeben wird */
    haltBeiFoto?: number
}

async function vorbereiten(page: Page, art: 'projekte' | 'anfragen', aufbau: Aufbau = {}) {
    const gesendet: string[] = []
    let freigeben: () => void = () => {}
    const freigabe = new Promise<void>(fertig => { freigeben = fertig })
    let bilderAbgerufen = 0

    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' }))
    })
    await page.route('**/api/**', async (route: Route) => {
        const anfrage = route.request()
        const pfad = new URL(anfrage.url()).pathname
        if (pfad.includes('/by-token/')) return route.fulfill({ json: { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' } })
        if (anfrage.method() === 'POST' && pfad === `/api/${art}/7/dokumente`) {
            const name = /filename="([^"]+)"/.exec(anfrage.postDataBuffer()?.toString('latin1') ?? '')?.[1] ?? '?'
            gesendet.push(name)
            if (aufbau.haltBeiFoto === gesendet.length) await freigabe
            if (aufbau.zuGross?.includes(name)) {
                // Das Gateway antwortet ohne JSON
                return route.fulfill({ status: 413, contentType: 'text/html', body: '<html>413 Request Entity Too Large</html>' })
            }
            return route.fulfill({ json: [{ id: gesendet.length }] })
        }
        if (pfad === '/api/zeiterfassung/projekte/7') {
            return route.fulfill({ json: { id: 7, name: 'Musterhaus Dach', projektNummer: 'P-2026-007', kundenName: 'Max Mustermann' } })
        }
        if (pfad === '/api/anfragen/7') {
            return route.fulfill({ json: { id: 7, bauvorhaben: 'Musterhaus Dach', kundenName: 'Max Mustermann' } })
        }
        if (pfad === '/api/zeiterfassung/projekte/7/bilder' || pfad === '/api/anfragen/7/dokumente') {
            bilderAbgerufen++
            return route.fulfill({ json: [] })
        }
        return route.fulfill({ json: [] })
    })

    await page.goto(`${art}?id=7`)
    await page.getByRole('button', { name: 'Fotos hinzufügen' }).tap()
    const modal = page.getByRole('dialog', { name: 'Fotos hochladen' })
    await expect(modal).toBeVisible()
    return { gesendet, freigeben, modal, bilderAbgerufen: () => bilderAbgerufen }
}

for (const art of ['projekte', 'anfragen'] as const) {
    test(`${art}: Fotos gehen einzeln raus und zeigen den Fortschritt`, async ({ page }, testInfo) => {
        const { gesendet, freigeben, modal, bilderAbgerufen } = await vorbereiten(page, art, { haltBeiFoto: 2 })

        await page.locator('input[type="file"][multiple]').setInputFiles([datei('dach-1.jpg'), datei('dach-2.jpg'), datei('dach-3.jpg')])
        await expect(modal.getByText('3 Bilder bereit')).toBeVisible()
        const vorher = bilderAbgerufen()

        await modal.getByRole('button', { name: 'Hochladen (3)' }).tap()

        // Das zweite Foto hängt: das erste ist schon angekommen und nicht mehr in der Auswahl
        await expect(modal.getByRole('status')).toContainText('Foto 2 von 3 wird hochgeladen')
        await expect(modal.getByText('2 Bilder bereit')).toBeVisible()
        expect(gesendet).toEqual(['dach-1.jpg', 'dach-2.jpg'])
        await expect(modal.getByRole('button', { name: 'Schließen' })).toBeDisabled()
        await designPruefung(page, testInfo, `foto-upload-${art}-fortschritt`, {
            primaerAktion: modal.getByRole('button', { name: 'Abbrechen' }),
        })

        freigeben()
        await expect(modal).toHaveCount(0)
        expect(gesendet).toEqual(['dach-1.jpg', 'dach-2.jpg', 'dach-3.jpg'])
        // Galerie wurde danach neu geladen
        await expect.poll(bilderAbgerufen).toBeGreaterThan(vorher)
    })

    test(`${art}: zu großes Foto wird gemeldet, die anderen kommen an und nichts geht doppelt raus`, async ({ page }, testInfo) => {
        const { gesendet, modal } = await vorbereiten(page, art, { zuGross: ['riesig.jpg'] })

        await page.locator('input[type="file"][multiple]').setInputFiles([datei('dach-1.jpg'), datei('riesig.jpg'), datei('dach-3.jpg')])
        await modal.getByRole('button', { name: 'Hochladen (3)' }).tap()

        // Fehler in Handwerker-Sprache, Dialog bleibt mit dem fehlgeschlagenen Foto offen
        await expect(page.getByRole('alert')).toContainText('1 Foto konnte nicht hochgeladen werden (2 von 3 sind angekommen): Foto zu groß')
        await expect(modal.getByText('1 Bild bereit')).toBeVisible()
        await expect(modal.getByText('Foto zu groß')).toBeVisible()
        expect(gesendet).toEqual(['dach-1.jpg', 'riesig.jpg', 'dach-3.jpg'])
        await designPruefung(page, testInfo, `foto-upload-${art}-zu-gross`, {
            primaerAktion: modal.getByRole('button', { name: 'Erneut versuchen (1)' }),
        })

        // Erneut versuchen sendet nur das fehlgeschlagene Foto
        await modal.getByRole('button', { name: 'Erneut versuchen (1)' }).tap()
        await expect.poll(() => gesendet.length).toBe(4)
        expect(gesendet.slice(3)).toEqual(['riesig.jpg'])

        // Foto entfernen schließt den Fall ab
        await modal.getByRole('button', { name: 'Bild entfernen' }).tap()
        await expect(modal.getByRole('heading', { name: 'Bild hinzufügen' })).toBeVisible()
    })

    test(`${art}: beim Verlassen mitten im Upload kommen alle Fotos an, der Fehler wird gemeldet und das Fenster schließt`, async ({ page }) => {
        const { gesendet, freigeben, modal } = await vorbereiten(page, art, { haltBeiFoto: 1, zuGross: ['riesig.jpg'] })
        await page.locator('input[type="file"][multiple]').setInputFiles([datei('dach-1.jpg'), datei('riesig.jpg'), datei('dach-3.jpg')])
        await modal.getByRole('button', { name: 'Hochladen (3)' }).tap()
        await expect.poll(() => gesendet.length).toBe(1)

        // Zurück zur Liste, während Foto 1 noch unterwegs ist
        await page.evaluate((pfad) => {
            history.pushState({}, '', `/zeiterfassung/${pfad}`)
            dispatchEvent(new PopStateEvent('popstate'))
        }, art)
        await expect(page.getByText('Musterhaus Dach')).toHaveCount(0)

        freigeben()
        await expect.poll(() => gesendet.length).toBe(3)
        const meldung = page.getByRole('alert')
        await expect(meldung).toContainText('1 Foto konnte nicht hochgeladen werden (2 von 3 sind angekommen): Foto zu groß.')
        await expect(meldung).not.toContainText('bleiben ausgewählt')
        await expect(modal).toHaveCount(0)
    })
}

// ---------------------------------------------------------------------------
// Bau-Tagebuch (Notizen): gleiche Schleife, Auswahl pro Notiz

async function notizenVorbereiten(page: Page, art: 'projekte' | 'anfragen', aufbau: Aufbau = {}) {
    const gesendet: string[] = []
    let freigeben: () => void = () => {}
    const freigabe = new Promise<void>(fertig => { freigeben = fertig })
    const notizen = [1, 2].map(id => ({
        id, notiz: `Eintrag ${id}: Dachrinne montiert.`, erstelltAm: '2026-09-30T10:00:00',
        mitarbeiterId: 1, mitarbeiterVorname: 'Max', mitarbeiterNachname: 'Mustermann',
        mobileSichtbar: true, nurFuerErsteller: false, canEdit: true, bilder: [],
    }))

    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' }))
    })
    await page.route('**/api/**', async (route: Route) => {
        const anfrage = route.request()
        const pfad = new URL(anfrage.url()).pathname
        if (pfad.includes('/by-token/')) return route.fulfill({ json: { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' } })
        if (anfrage.method() === 'POST' && pfad === `/api/${art}/7/notizen/1/bilder`) {
            const name = /filename="([^"]+)"/.exec(anfrage.postDataBuffer()?.toString('latin1') ?? '')?.[1] ?? '?'
            gesendet.push(name)
            if (aufbau.haltBeiFoto === gesendet.length) await freigabe
            if (aufbau.zuGross?.includes(name)) return route.fulfill({ status: 413, contentType: 'text/html', body: '<html>413</html>' })
            return route.fulfill({ json: { id: gesendet.length } })
        }
        if (pfad === `/api/${art}/7/notizen`) return route.fulfill({ json: notizen })
        return route.fulfill({ json: [] })
    })

    await page.goto(`${art}/7/notizen`)
    const eintrag = page.locator('div').filter({ hasText: 'Eintrag 1: Dachrinne montiert.' }).last()
    const fotosWaehlen = async (namen: string[]) => {
        const [auswahl] = await Promise.all([
            page.waitForEvent('filechooser'),
            page.getByRole('button', { name: 'Galerie' }).first().tap(),
        ])
        await auswahl.setFiles(namen.map(datei))
    }
    return { gesendet, freigeben, eintrag, fotosWaehlen }
}

for (const art of ['projekte', 'anfragen'] as const) {
    test(`${art}/notizen: zu großes Foto wird markiert und gemeldet, Erneut versuchen sendet nur dieses`, async ({ page }, testInfo) => {
        const { gesendet, fotosWaehlen } = await notizenVorbereiten(page, art, { zuGross: ['riesig.jpg'] })
        await fotosWaehlen(['dach-1.jpg', 'riesig.jpg', 'dach-3.jpg'])
        await page.getByRole('button', { name: 'Hochladen (3)' }).tap()

        await expect(page.getByRole('alert')).toContainText('1 Foto konnte nicht hochgeladen werden (2 von 3 sind angekommen): Foto zu groß')
        await expect(page.getByText('Foto zu groß', { exact: true })).toBeVisible()
        expect(gesendet).toEqual(['dach-1.jpg', 'riesig.jpg', 'dach-3.jpg'])
        await designPruefung(page, testInfo, `foto-upload-${art}-notizen-zu-gross`, {
            primaerAktion: page.getByRole('button', { name: 'Erneut versuchen (1)' }),
        })

        await page.getByRole('button', { name: 'Erneut versuchen (1)' }).tap()
        await expect.poll(() => gesendet.length).toBe(4)
        expect(gesendet.slice(3)).toEqual(['riesig.jpg'])
    })

    test(`${art}/notizen: Fortschritt sichtbar, Auswahl anderer Einträge währenddessen gesperrt`, async ({ page }) => {
        const { gesendet, freigeben, fotosWaehlen } = await notizenVorbereiten(page, art, { haltBeiFoto: 2 })
        await fotosWaehlen(['dach-1.jpg', 'dach-2.jpg'])
        await page.getByRole('button', { name: 'Hochladen (2)' }).tap()

        await expect(page.getByRole('status').filter({ hasText: 'Foto 2 von 2 wird hochgeladen' })).toBeVisible()
        // Beim anderen Eintrag sind Kamera und Galerie gesperrt
        await expect(page.getByRole('button', { name: 'Galerie' }).first()).toBeDisabled()
        await expect(page.getByRole('button', { name: 'Kamera' }).first()).toBeDisabled()

        freigeben()
        await expect(page.getByRole('button', { name: /Hochladen \(/ })).toHaveCount(0)
        expect(gesendet).toEqual(['dach-1.jpg', 'dach-2.jpg'])
    })

    test(`${art}/notizen: beim Verlassen der Seite läuft der Upload zu Ende und Fehler werden trotzdem gemeldet`, async ({ page }) => {
        const { gesendet, freigeben, fotosWaehlen } = await notizenVorbereiten(page, art, { haltBeiFoto: 1, zuGross: ['riesig.jpg'] })
        await fotosWaehlen(['dach-1.jpg', 'riesig.jpg', 'dach-3.jpg'])
        await page.getByRole('button', { name: 'Hochladen (3)' }).tap()
        await expect.poll(() => gesendet.length).toBe(1)

        // Zurück zur Startseite, während Foto 1 noch unterwegs ist
        await page.evaluate(() => {
            history.pushState({}, '', '/zeiterfassung/')
            dispatchEvent(new PopStateEvent('popstate'))
        })
        await expect(page.getByText('Eintrag 1: Dachrinne montiert.')).toHaveCount(0)

        freigeben()
        await expect.poll(() => gesendet.length).toBe(3)
        expect(gesendet).toEqual(['dach-1.jpg', 'riesig.jpg', 'dach-3.jpg'])
        const meldung = page.getByRole('alert')
        await expect(meldung).toContainText('1 Foto konnte nicht hochgeladen werden (2 von 3 sind angekommen): Foto zu groß.')
        await expect(meldung).not.toContainText('bleiben ausgewählt')
    })
}
