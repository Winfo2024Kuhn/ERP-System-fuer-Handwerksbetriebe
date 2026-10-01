import type { Page, Route } from '@playwright/test'
import { expect, test } from './hilfen/test'
import { designPruefung, keineUeberschneidungen, uebergaengeAusklingenLassen } from './hilfen/design'
import fs from 'node:fs'
import path from 'node:path'

// Dummy-Daten (DSGVO): nur Max Mustermann, Bilder sind gezeichnete Farbflächen.
const FARBEN = ['#64748b', '#0f766e', '#b45309']

/** Ein „Foto“ als SVG: Himmel, Boden, Hausform – erkennbar, aber ohne echte Inhalte. */
function svgFoto(farbe: string, breite: number, hoehe: number): string {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${breite}" height="${hoehe}" viewBox="0 0 400 300">`
        + `<rect width="400" height="300" fill="#cbd5e1"/><rect y="200" width="400" height="100" fill="${farbe}"/>`
        + `<polygon points="120,200 200,120 280,200" fill="#334155"/><rect x="140" y="200" width="120" height="70" fill="#e2e8f0"/></svg>`
}

type Variante = 'thumbnail' | 'anzeige' | 'original'

interface Aufbau {
    /** Basis der Bildadressen wie vom Backend geliefert */
    bildBasis: '/api/dokumente' | '/api/images'
    /** Anzeigegröße für dieses Bild verzögern (Ladezustand sichtbar machen) */
    langsam?: string
    /** Für dieses Bild liefert der Server weder Anzeigegröße noch Original */
    kaputt?: string
    /** Anzahl Fotos in der Notiz (Standard 3) */
    anzahl?: number
}

async function vorbereiten(page: Page, art: 'projekte' | 'anfragen', aufbau: Aufbau) {
    const bildAnfragen: { datei: string; variante: Variante }[] = []
    const dateien = Array.from({ length: aufbau.anzahl ?? 3 }, (_, i) => `baustelle-${i + 1}.jpg`)
    const bilder = dateien.map((datei, i) => ({
        id: i + 1,
        originalDateiname: datei,
        url: `${aufbau.bildBasis}/${datei}`,
        thumbnailUrl: `/api/dokumente/${datei}/thumbnail`,
        erstelltAm: '2026-09-30T10:00:00',
    }))
    const notiz = {
        id: 1, notiz: 'Dachrinne montiert, Fallrohr fehlt noch.', erstelltAm: '2026-09-30T10:00:00',
        mitarbeiterId: 1, mitarbeiterVorname: 'Max', mitarbeiterNachname: 'Mustermann',
        mobileSichtbar: true, nurFuerErsteller: false, canEdit: false, bilder,
    }

    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann', vorname: 'Max', nachname: 'Mustermann' }))
    })
    await page.route('**/api/**', async (route: Route) => {
        const pfad = new URL(route.request().url()).pathname
        const treffer = /^\/api\/(?:dokumente|images)\/([^/]+)(?:\/(thumbnail|anzeige))?$/.exec(pfad)
        if (treffer) {
            const datei = treffer[1]
            const variante = (treffer[2] ?? 'original') as Variante
            bildAnfragen.push({ datei, variante })
            if (datei === aufbau.kaputt && variante !== 'thumbnail') {
                return route.fulfill({ status: 404, json: { error: 'nicht gefunden' } })
            }
            if (datei === aufbau.langsam && variante === 'anzeige') {
                await new Promise(fertig => setTimeout(fertig, 4000))
            }
            const index = bilder.findIndex(b => b.originalDateiname === datei)
            const groesse = variante === 'thumbnail' ? [300, 225] : variante === 'anzeige' ? [1600, 1200] : [4000, 3000]
            return route.fulfill({
                contentType: 'image/svg+xml',
                headers: { 'Cache-Control': 'private, max-age=2592000' },
                body: svgFoto(FARBEN[Math.max(index, 0) % FARBEN.length], groesse[0], groesse[1]),
            })
        }
        if (pfad.endsWith('/notizen')) return route.fulfill({ json: [notiz] })
        if (pfad.includes('/by-token/')) return route.fulfill({ json: { id: 1, aktiv: true, vorname: 'Max', nachname: 'Mustermann' } })
        return route.fulfill({ json: [] })
    })

    await page.goto(`${art}/7/notizen`)
    await expect(page.getByText('Dachrinne montiert, Fallrohr fehlt noch.')).toBeVisible()
    return { bildAnfragen }
}

const vorschauKnopf = (page: Page, datei: string) => page.locator(`button:has(img[alt="${datei}"])`)

for (const art of ['projekte', 'anfragen'] as const) {
    const bildBasis = art === 'projekte' ? '/api/dokumente' as const : '/api/images' as const

    test(`${art}: Tagebuch-Fotos laden als Vorschau und öffnen hell in Anzeigegröße`, async ({ page }, testInfo) => {
        const { bildAnfragen } = await vorbereiten(page, art, { bildBasis })

        // Die Liste lädt nur die kleinen Vorschaubilder, nie die mehrere MB großen Originale
        await expect(page.locator('img[alt="baustelle-1.jpg"]')).toHaveJSProperty('complete', true)
        expect(bildAnfragen.filter(a => a.variante === 'original')).toEqual([])
        expect(bildAnfragen.some(a => a.variante === 'thumbnail')).toBe(true)

        await vorschauKnopf(page, 'baustelle-1.jpg').tap()
        const dialog = page.getByRole('dialog')
        await expect(dialog).toBeVisible()
        const foto = dialog.getByRole('img', { name: 'baustelle-1.jpg', exact: true })
        await expect(foto).toHaveAttribute('src', '/api/dokumente/baustelle-1.jpg/anzeige')
        // Regression: Der Ladehinweis verschwindet, sobald das Bild da ist
        await expect(dialog.getByRole('status')).toHaveCount(0)
        await expect(foto).toHaveCSS('opacity', '1')

        // Hell wie die Statusleiste und die App – kein schwarzer Kasten mit grauem Verlauf oben
        await expect(dialog).toHaveCSS('background-color', 'oklch(0.984 0.003 247.858)') // slate-50
        // Bedienelemente stehen unterhalb des iOS-Weichzeichners (oberste ~35 pt)
        const schliessen = await dialog.getByRole('button', { name: 'Schließen' }).boundingBox()
        expect(schliessen!.y).toBeGreaterThanOrEqual(36)

        // Nachbarbild wird vorgeladen, das Original nie
        await expect.poll(() => bildAnfragen.some(a => a.datei === 'baustelle-2.jpg' && a.variante === 'anzeige')).toBe(true)
        expect(bildAnfragen.filter(a => a.variante === 'original')).toEqual([])

        await designPruefung(page, testInfo, `tagebuch-${art}-bildansicht`, {
            primaerAktion: dialog.getByRole('button', { name: 'Schließen' }),
        })

        // Blättern über die Leiste
        await dialog.getByRole('button', { name: 'Bild 3 anzeigen' }).tap()
        await expect(dialog.getByText('3 / 3')).toBeVisible()
        await expect(dialog.getByRole('img', { name: 'baustelle-3.jpg', exact: true })).toHaveCSS('opacity', '1')

        await dialog.getByRole('button', { name: 'Schließen' }).tap()
        await expect(dialog).toHaveCount(0)
    })
}

test('projekte: zeigt sofort die Vorschau, solange die Anzeigegröße lädt', async ({ page }, testInfo) => {
    await vorbereiten(page, 'projekte', { bildBasis: '/api/dokumente', langsam: 'baustelle-2.jpg' })

    await vorschauKnopf(page, 'baustelle-2.jpg').tap()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('status', { name: 'Bild wird geladen' })).toBeVisible()
    const platzhalter = dialog.locator('img[aria-hidden="true"][src="/api/dokumente/baustelle-2.jpg/thumbnail"]')
    await expect(platzhalter).toBeVisible()
    await expect(platzhalter).toHaveJSProperty('complete', true)

    await designPruefung(page, testInfo, 'tagebuch-bildansicht-laedt', {
        primaerAktion: dialog.getByRole('button', { name: 'Schließen' }),
    })

    await expect(dialog.getByRole('status')).toHaveCount(0, { timeout: 8000 })
    await expect(platzhalter).toHaveCount(0)
})

test('anfragen: meldet ein fehlendes Bild verständlich statt endlos zu laden', async ({ page }, testInfo) => {
    await vorbereiten(page, 'anfragen', { bildBasis: '/api/images', kaputt: 'baustelle-1.jpg' })

    await vorschauKnopf(page, 'baustelle-1.jpg').tap()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByText('Bild konnte nicht geladen werden.')).toBeVisible()
    await expect(dialog.getByRole('status')).toHaveCount(0)

    await designPruefung(page, testInfo, 'tagebuch-bildansicht-fehler', {
        primaerAktion: dialog.getByRole('button', { name: 'Erneut versuchen' }),
    })

    // Ein anderes Bild der Galerie lädt trotzdem
    await dialog.getByRole('button', { name: 'Bild 2 anzeigen' }).tap()
    await expect(dialog.getByRole('img', { name: 'baustelle-2.jpg', exact: true })).toHaveCSS('opacity', '1')
})

/** Wischgeste auf dem Bild – Playwright kennt nur Tippen, also echte TouchEvents auslösen. */
async function wische(page: Page, vonX: number, nachX: number) {
    await page.getByRole('dialog').locator('img[alt]:not([alt=""])').first().evaluate(async (bild, [start, ende]) => {
        const flaeche = bild.parentElement!
        const y = flaeche.getBoundingClientRect().top + flaeche.clientHeight / 2
        const punkt = (x: number) => new Touch({ identifier: 1, target: flaeche, clientX: x, clientY: y })
        // Ein echter Finger liefert die Events über mehrere Frames verteilt – dazwischen rendert React
        const naechsterFrame = () => new Promise(fertig => requestAnimationFrame(fertig))
        const sende = async (typ: string, x: number, aktiv: boolean) => {
            flaeche.dispatchEvent(new TouchEvent(typ, {
                bubbles: true, cancelable: true,
                touches: aktiv ? [punkt(x)] : [], targetTouches: aktiv ? [punkt(x)] : [], changedTouches: [punkt(x)],
            }))
            await naechsterFrame()
        }
        await sende('touchstart', start, true)
        for (let schritt = 1; schritt <= 5; schritt++) await sende('touchmove', start + (ende - start) * schritt / 5, true)
        await sende('touchend', ende, false)
    }, [vonX, nachX])
}

test('projekte: Wischen blättert, Hineinzoomen lädt das Original für Details nach', async ({ page }, testInfo) => {
    const { bildAnfragen } = await vorbereiten(page, 'projekte', { bildBasis: '/api/dokumente' })

    await vorschauKnopf(page, 'baustelle-1.jpg').tap()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('img', { name: 'baustelle-1.jpg', exact: true })).toHaveCSS('opacity', '1')

    // Nach links wischen → nächstes Bild, nach rechts → zurück
    await wische(page, 320, 80)
    await expect(dialog.getByText('2 / 3')).toBeVisible()
    await expect(dialog.getByRole('img', { name: 'baustelle-2.jpg', exact: true })).toHaveCSS('opacity', '1')
    await wische(page, 80, 320)
    await expect(dialog.getByText('1 / 3')).toBeVisible()

    // Zoomen: erst jetzt wird das Original geholt und gegen die Anzeigegröße getauscht
    expect(bildAnfragen.filter(a => a.variante === 'original')).toEqual([])
    await dialog.getByRole('button', { name: 'Vergrößern' }).tap()
    const foto = dialog.getByRole('img', { name: 'baustelle-1.jpg', exact: true })
    await expect(foto).toHaveAttribute('src', '/api/dokumente/baustelle-1.jpg')
    await expect(foto).toHaveCSS('opacity', '1')
    expect(bildAnfragen.filter(a => a.variante === 'original').map(a => a.datei)).toEqual(['baustelle-1.jpg'])

    // Kopfleiste im Zoom: zusätzlicher Zurücksetzen-Knopf, nichts läuft über, nichts überlappt.
    // designPruefung passt hier nicht: Das vergrößerte Bild ragt gewollt über den
    // Bildbereich hinaus und wird von overflow-hidden abgeschnitten.
    await expect(dialog.getByRole('button', { name: 'Zoom zurücksetzen' })).toBeVisible()
    await expect(dialog.getByText('150%')).toBeVisible()
    await uebergaengeAusklingenLassen(page)
    for (const name of ['Schließen', 'Verkleinern', 'Vergrößern', 'Zoom zurücksetzen']) {
        const knopf = dialog.getByRole('button', { name })
        const box = await knopf.boundingBox()
        expect(box!.x, `${name} links im Bild`).toBeGreaterThanOrEqual(0)
        expect(box!.x + box!.width, `${name} rechts im Bild`).toBeLessThanOrEqual(393)
        // Layoutbreite statt boundingBox: die enthält die Drück-Animation (active:scale-90)
        expect(await knopf.evaluate(el => (el as HTMLElement).offsetWidth), `${name} hat 44-pt-Tippfläche`).toBeGreaterThanOrEqual(44)
    }
    await keineUeberschneidungen(page)
    const zielOrdner = path.join(testInfo.project.outputDir, 'design')
    fs.mkdirSync(zielOrdner, { recursive: true })
    await page.screenshot({ path: path.join(zielOrdner, `tagebuch-bildansicht-zoom--${testInfo.project.name}.png`) })

    // Die Vorschauleiste springt beim Zoomen nicht
    const leisteImZoom = await dialog.getByRole('button', { name: 'Bild 1 anzeigen' }).boundingBox()

    await dialog.getByRole('button', { name: 'Zoom zurücksetzen' }).tap()
    await expect(dialog.getByText('100%')).toBeVisible()
    await expect(dialog.getByRole('button', { name: 'Zoom zurücksetzen' })).toHaveCount(0)
    await uebergaengeAusklingenLassen(page)
    const leisteOhneZoom = await dialog.getByRole('button', { name: 'Bild 1 anzeigen' }).boundingBox()
    expect(leisteOhneZoom!.y).toBe(leisteImZoom!.y)
})

test('projekte: bei vielen Fotos läuft die Vorschauleiste beim Wischen mit', async ({ page }) => {
    await vorbereiten(page, 'projekte', { bildBasis: '/api/dokumente', anzahl: 12 })

    await vorschauKnopf(page, 'baustelle-1.jpg').tap()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('img', { name: 'baustelle-1.jpg', exact: true })).toHaveCSS('opacity', '1')

    for (let i = 0; i < 9; i++) await wische(page, 320, 80)
    await expect(dialog.getByText('10 / 12')).toBeVisible()

    // Das aktive Vorschaubild muss sichtbar sein, nicht irgendwo rechts außerhalb
    const aktiv = dialog.getByRole('button', { name: 'Bild 10 anzeigen' })
    await expect(aktiv).toHaveAttribute('aria-current', 'true')
    await expect(aktiv).toBeInViewport({ ratio: 1 })

    // Die Leiste selbst lässt sich mit dem Finger verschieben (Dialog sperrt sonst jede Geste)
    await expect(dialog.getByRole('button', { name: 'Bild 1 anzeigen' }).locator('xpath=../..')).toHaveCSS('touch-action', 'pan-x')
})
