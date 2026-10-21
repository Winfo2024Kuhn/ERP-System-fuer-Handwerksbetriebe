import type { Page, Request } from '@playwright/test'
import { expect, test } from './hilfen/test'
import { designPruefung, uebergaengeAusklingenLassen } from './hilfen/design'

/*
 * Dokumentenscanner im echten Browser: Kamera (Chromium-Testbild), Zuschneiden,
 * Bildverbesserung, Seitenübersicht und PDF – alles ohne Attrappen.
 *
 * Die automatische Blatt-Erkennung ist hier bewusst abgeschaltet: Das
 * Chromium-Testbild zeigt kein Blatt, ob die KI darin trotzdem eins „sieht“,
 * ist Zufall. Der Auslöser von Hand führt durch denselben Ablauf (Seite
 * anlegen, im Hintergrund zuschneiden und verbessern). Die Automatik selbst
 * prüfen die Vitest-Tests von ScannerModal und scanAusloeser.
 */

test.use({
    permissions: ['camera'],
    launchOptions: { args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] },
})

// Dummy-Daten (DSGVO): Lieferant „Musterbaustoffe GmbH“, Mitarbeiter Max Mustermann.
async function vorbereiten(page: Page): Promise<Request[]> {
    const analysen: Request[] = []
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' }))
        // Kamerastreams merken, damit der Test prüfen kann, ob die Kamera nach dem Scan aus ist.
        const streams: MediaStream[] = []
        ;(window as unknown as { kameraStreams: MediaStream[] }).kameraStreams = streams
        if (navigator.mediaDevices) {
            const original = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices)
            navigator.mediaDevices.getUserMedia = async constraints => {
                const stream = await original(constraints)
                streams.push(stream)
                return stream
            }
        }
    })
    // Erkennung nicht laden (siehe oben) – der Scanner fällt dann auf „von Hand“ zurück.
    await page.route(/\/(\.vite\/deps\/scanic\.js|node_modules\/scanic\/|assets\/scanic[-.][^/]*\.js)/, route => route.abort())
    await page.route('**/api/**', route => {
        const anfrage = route.request()
        const pfad = new URL(anfrage.url()).pathname
        if (pfad.includes('/by-token/')) return route.fulfill({ json: { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' } })
        if (pfad === '/api/lieferanten/3') return route.fulfill({ json: { id: 3, lieferantenname: 'Musterbaustoffe GmbH' } })
        if (pfad === '/api/lieferanten/3/dokumente/analyze' && anfrage.method() === 'POST') {
            analysen.push(anfrage)
            return route.fulfill({ json: [{ pageRange: '1', analyzeResponse: { dokumentTyp: 'LIEFERSCHEIN', dokumentNummer: 'LS-4711' } }] })
        }
        return route.fulfill({ json: [] })
    })
    return analysen
}

/** Auslöser von Hand → Zuschneiden → Übernehmen; wartet, bis die Seite links unten angekommen ist. */
async function seiteVonHandScannen(page: Page, anzahlDanach: number) {
    await expect.poll(() => page.locator('video').evaluate(v => (v as HTMLVideoElement).readyState)).toBeGreaterThanOrEqual(2)
    await page.getByRole('button', { name: 'Foto von Hand aufnehmen' }).tap()
    await expect(page.getByText('Ecken an die Blattkanten ziehen')).toBeVisible()
    await page.getByRole('button', { name: 'Übernehmen' }).tap()
    const name = `Seiten ansehen und bearbeiten (${anzahlDanach} ${anzahlDanach === 1 ? 'Seite' : 'Seiten'})`
    await expect(page.getByRole('button', { name }).locator('img')).toHaveAttribute('src', /^blob:/)
    await expect(page.getByRole('button', { name: `Fertig (${anzahlDanach})` })).toBeEnabled()
}

test('Seite von Hand scannen, Filter wählen und als PDF übergeben', async ({ page }, testInfo) => {
    const analysen = await vorbereiten(page)
    await page.goto('lieferanten/3/lieferscheine')
    await page.getByRole('button', { name: 'Scannen' }).tap()

    await expect(page.getByText('Blatt ins Bild halten und auslösen')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Fertig (0)' })).toBeDisabled()
    // Erst auslösen, wenn das Kamerabild läuft
    await expect.poll(() => page.locator('video').evaluate(v => (v as HTMLVideoElement).readyState)).toBeGreaterThanOrEqual(2)
    await designPruefung(page, testInfo, 'scanner-kamera-leer')

    await page.getByRole('button', { name: 'Foto von Hand aufnehmen' }).tap()
    await expect(page.getByText('Ecken an die Blattkanten ziehen')).toBeVisible()
    await designPruefung(page, testInfo, 'scanner-zuschneiden', { primaerAktion: page.getByRole('button', { name: 'Übernehmen' }) })
    await page.getByRole('button', { name: 'Übernehmen' }).tap()

    // Zurück in der Kamera: Vorschaubild der Seite links, „Fertig (1)“ rechts
    const vorschau = page.getByRole('button', { name: 'Seiten ansehen und bearbeiten (1 Seite)' })
    await expect(vorschau.locator('img')).toHaveAttribute('src', /^blob:/)
    await expect(page.getByRole('button', { name: 'Fertig (1)' })).toBeEnabled()
    await expect(page.getByText('Seite 2 scannen')).toBeVisible()
    await uebergaengeAusklingenLassen(page)
    await designPruefung(page, testInfo, 'scanner-kamera-eine-seite')

    await vorschau.tap()
    await expect(page.getByText('Seite 1 von 1')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Dokument' })).toHaveAttribute('aria-pressed', 'true')
    await page.getByRole('button', { name: 'Schwarz-Weiß' }).tap()
    await expect(page.getByRole('button', { name: 'Schwarz-Weiß' })).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByRole('status', { name: 'Seite 1 wird verbessert' })).toHaveCount(0)
    await expect(page.getByRole('img', { name: 'Seite 1' })).toBeVisible()
    await designPruefung(page, testInfo, 'scanner-seitenuebersicht', { primaerAktion: page.getByRole('button', { name: 'Fertig (1)' }) })

    await page.getByRole('button', { name: 'Mehr zu: Bild verbessern' }).tap()
    await expect(page.getByText('Was passiert im Hintergrund?')).toBeVisible()
    await designPruefung(page, testInfo, 'scanner-info-bildverbesserung')
    await page.keyboard.press('Escape')
    await expect(page.getByText('Was passiert im Hintergrund?')).toBeHidden()

    await page.getByRole('button', { name: 'Fertig (1)' }).tap()

    await expect.poll(() => analysen.length).toBe(1)
    const inhalt = analysen[0].postDataBuffer()?.toString('latin1') ?? ''
    expect(inhalt).toMatch(/filename="Scan_[^"]+\.pdf"/)
    expect(inhalt).toContain('%PDF-')
    await expect(page.getByRole('button', { name: 'Foto von Hand aufnehmen' })).toBeHidden()

    // Prüfen-Schritt: Seite als Bild statt eingebettetem PDF, Kamera aus – sonst lädt iOS die App
    // wegen Speichermangel neu und der Prüfen-Schritt verschwindet nach wenigen Sekunden.
    await expect(page.getByText('Prüfen & Speichern')).toBeVisible()
    await expect(page.getByPlaceholder('Lieferschein-Nr.')).toHaveValue('LS-4711')
    await expect(page.getByRole('img', { name: 'Gescannter Lieferschein, Seite 1' })).toBeVisible()
    await expect(page.locator('iframe')).toHaveCount(0)
    expect(await page.evaluate(() => (window as unknown as { kameraStreams: MediaStream[] }).kameraStreams.length)).toBeGreaterThan(0)
    const kameraLaeuft = await page.evaluate(() => (window as unknown as { kameraStreams: MediaStream[] }).kameraStreams
        .some(s => s.getTracks().some(t => t.readyState === 'live')))
    expect(kameraLaeuft).toBe(false)
    await designPruefung(page, testInfo, 'lieferschein-pruefen-nach-scan', { primaerAktion: page.getByRole('button', { name: 'Speichern' }) })
})

test('Seiten bearbeiten, löschen und den Scan verwerfen', async ({ page }, testInfo) => {
    await vorbereiten(page)
    await page.goto('lieferanten/3/lieferscheine')
    await page.getByRole('button', { name: 'Scannen' }).tap()
    const scanner = page.getByRole('dialog', { name: 'Dokument scannen' })

    await seiteVonHandScannen(page, 1)
    await seiteVonHandScannen(page, 2)
    await page.getByRole('button', { name: 'Seiten ansehen und bearbeiten (2 Seiten)' }).tap()
    await expect(page.getByText('Seite 2 von 2')).toBeVisible()

    // Ecken anpassen und abbrechen: zurück in die Übersicht, gleiche Seite
    await page.getByRole('button', { name: 'Ecken anpassen' }).tap()
    await expect(page.getByText('Ecken an die Blattkanten ziehen')).toBeVisible()
    await page.getByRole('button', { name: 'Zurück' }).tap()
    await expect(page.getByText('Seite 2 von 2')).toBeVisible()

    // Ecken anpassen und übernehmen: Seite wird neu zugeschnitten
    await page.getByRole('button', { name: 'Ecken anpassen' }).tap()
    await page.getByRole('button', { name: 'Übernehmen' }).tap()
    await expect(page.getByText('Seite 2 von 2')).toBeVisible()
    await expect(page.getByRole('img', { name: 'Seite 2' })).toBeVisible()
    await expect(page.getByRole('status', { name: 'Seite 2 wird verbessert' })).toHaveCount(0)

    // Seite löschen mit Rückfrage
    await page.getByRole('button', { name: 'Löschen' }).tap()
    const loeschen = page.getByRole('dialog', { name: 'Seite löschen?' })
    await expect(loeschen).toBeVisible()
    await designPruefung(page, testInfo, 'scanner-seite-loeschen')
    await loeschen.getByRole('button', { name: 'Löschen' }).tap()
    await expect(page.getByText('Seite 1 von 1')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Fertig (1)' })).toBeEnabled()

    // Schließen mit verbleibender Seite: erst abbrechen, dann verwerfen
    await page.getByRole('button', { name: 'Scanner schließen' }).tap()
    const verwerfen = page.getByRole('dialog', { name: 'Scan verwerfen?' })
    await verwerfen.getByRole('button', { name: 'Weiter scannen' }).tap()
    await expect(verwerfen).toBeHidden()
    await expect(page.getByText('Seite 1 von 1')).toBeVisible()

    await page.getByRole('button', { name: 'Scanner schließen' }).tap()
    await page.getByRole('dialog', { name: 'Scan verwerfen?' }).getByRole('button', { name: 'Verwerfen' }).tap()
    await expect(scanner).toBeHidden()
    await expect(page.getByRole('button', { name: 'Scannen' })).toBeVisible()
})
