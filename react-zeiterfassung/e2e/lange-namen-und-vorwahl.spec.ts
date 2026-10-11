import type { Page } from '@playwright/test'
import { expect, test } from './hilfen/test'
import { designPruefung, keinHorizontalerUeberlauf } from './hilfen/design'

// Lange Bauvorhaben-Namen, wie sie im Büro tatsächlich eingetragen werden: einmal
// mit Leerzeichen, einmal als ein einziges langes Wort ohne Umbruchstelle.
const LANGES_BV = 'Neubau Einfamilienhaus mit Doppelgarage – Treppengeländer, Balkongeländer und Vordach Musterstraße 12a'
const LANGES_WORT_BV = 'BV-Mustermann_Neubau_Einfamilienhaus_Treppengelaender_Balkongelaender_Vordach'
const LANGER_NAME = 'Erika_Musterfrau_Bauunternehmung_und_Projektentwicklung'
const LANGER_ORT = 'Musterstadt-an-der-Fränkischen-Saale-Oberhausen'

const projekte = [
    {
        id: 1,
        name: LANGES_BV,
        projektNummer: 'P-2026-001',
        kundenName: 'Max Mustermann',
        kundenOrt: LANGER_ORT,
        projektStrasse: 'Musterstraße 12a',
        projektPlz: '97070',
        projektOrt: LANGER_ORT,
        // Vom Büro beim Eingeben der Adresse vorgeschlagene Ortsvorwahl, keine echte Nummer.
        kundenTelefon: '0931 ',
        kundenMobil: '0151 12345678',
        projektArt: 'PAUSCHAL',
        abgeschlossen: false,
    },
    {
        id: 2,
        name: LANGES_WORT_BV,
        projektNummer: 'P-2026-002',
        kundenName: LANGER_NAME,
        kundenOrt: 'Würzburg',
        kundenStrasse: 'Musterweg 1',
        kundenPlz: '97070',
        kundenTelefon: '+49 931',
        abgeschlossen: false,
    },
]

const anfragen = [
    {
        id: 11,
        bauvorhaben: LANGES_BV,
        kundenName: 'Max Mustermann',
        kundenOrt: LANGER_ORT,
        projektStrasse: 'Musterstraße 12a',
        projektPlz: '97070',
        projektOrt: LANGER_ORT,
        kundenTelefon: '0931 ',
        kundenMobiltelefon: '0151 12345678',
        abgeschlossen: false,
    },
    {
        id: 12,
        bauvorhaben: LANGES_WORT_BV,
        kundenName: LANGER_NAME,
        kundenOrt: 'Würzburg',
        kundenStrasse: 'Musterweg 1',
        kundenPlz: '97070',
        kundenTelefon: '0931',
        abgeschlossen: false,
    },
]

const kunden = [
    { id: 21, name: LANGER_NAME, kundennummer: 'K-1001', strasse: 'Musterweg 1', plz: '97070', ort: LANGER_ORT, telefon: '0931 ' },
    { id: 22, name: 'Max Mustermann', kundennummer: 'K-1002', ort: 'Würzburg', telefon: '0931 123456' },
]

const lieferanten = [
    { id: 31, lieferantenname: LANGER_NAME, ort: LANGER_ORT, telefon: '09321 ' },
    { id: 32, lieferantenname: 'Muster Stahlhandel', ort: 'Würzburg', telefon: '0931 654321' },
]

async function richteAppEin(page: Page) {
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Max Mustermann' }))
    })
    await page.route('**/api/**', route => {
        const pathname = new URL(route.request().url()).pathname
        if (pathname === '/api/zeiterfassung/projekte') return route.fulfill({ json: projekte })
        if (pathname === '/api/anfragen') return route.fulfill({ json: anfragen })
        if (pathname === '/api/kunden') return route.fulfill({ json: { kunden } })
        if (pathname === '/api/zeiterfassung/lieferanten') return route.fulfill({ json: lieferanten })
        if (pathname.startsWith('/api/zeiterfassung/buchungszeitfenster/')) {
            return route.fulfill({ json: { fuehrtZeitkonto: true, eingerichtet: true, hinweis: null } })
        }
        if (pathname.startsWith('/api/zeiterfassung/kategorien/')) {
            return route.fulfill({ json: [{ id: 41, name: 'Montage_Treppengelaender_und_Balkongelaender_vor_Ort' }] })
        }
        return route.fulfill({ json: [] })
    })
}

/**
 * Wischt quer über den Inhalt und prüft, dass sich dabei nichts seitlich verschiebt –
 * auf dem Handy gibt es kein seitliches Scrollen. Läuft nur zusätzlich mit: Chromium
 * scrollt overflow-x-hidden ohnehin nicht per Geste (iOS schon). Den eigentlichen Schutz
 * liefern keinHorizontalerUeberlauf und nichtsRagtUeberDenRand.
 */
async function seitlichesWischenBewegtNichts(page: Page) {
    const inhalt = page.locator('div.flex-1.overflow-y-auto').first()
    const bounds = await inhalt.boundingBox()
    expect(bounds, 'Der Inhaltsbereich muss sichtbar sein.').not.toBeNull()

    const cdp = await page.context().newCDPSession(page)
    const y = bounds!.y + 40
    const startX = bounds!.x + bounds!.width * 0.8
    await cdp.send('Input.dispatchTouchEvent', {
        type: 'touchStart',
        touchPoints: [{ x: startX, y, radiusX: 1, radiusY: 1, force: 1, id: 1 }],
    })
    await cdp.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [{ x: startX - 250, y, radiusX: 1, radiusY: 1, force: 1, id: 1 }],
    })
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
    // Zwei Frames abwarten, damit eine ausgelöste Scrollbewegung auch angekommen ist.
    await page.evaluate(() => new Promise(fertig => requestAnimationFrame(() => requestAnimationFrame(fertig))))

    const verschoben = await page.evaluate(() =>
        Array.from(document.querySelectorAll<HTMLElement>('*'))
            .filter(el => el.scrollLeft > 0)
            .map(el => `${el.tagName.toLowerCase()}.${Array.from(el.classList).join('.')}: ${el.scrollLeft}px`)
            .concat(window.scrollX > 0 ? [`Fenster: ${window.scrollX}px`] : []),
    )
    expect(verschoben, 'Nach seitlichem Wischen darf nichts seitlich verschoben sein.').toEqual([])
}

/** Kein Element ragt rechts aus dem Bildschirm – auch nicht im Kopf, der selbst nichts abschneidet. */
async function nichtsRagtUeberDenRand(page: Page) {
    const zuBreit = await page.evaluate(() => {
        const breite = document.documentElement.clientWidth
        return Array.from(document.querySelectorAll<HTMLElement>('body *'))
            .filter(el => el.getBoundingClientRect().right > breite + 1)
            .map(el => `${el.tagName.toLowerCase()}.${Array.from(el.classList).join('.')}: ${Math.round(el.getBoundingClientRect().right)}px`)
    })
    expect(zuBreit, 'Kein Element darf breiter als der Bildschirm sein.').toEqual([])
}

async function pruefeBildschirmbreite(page: Page) {
    await keinHorizontalerUeberlauf(page)
    await nichtsRagtUeberDenRand(page)
    await seitlichesWischenBewegtNichts(page)
}

test.describe('Lange Namen: kein seitliches Scrollen auf dem Handy', () => {
    test('Projekte: Liste und Detail bleiben bildschirmbreit, reine Vorwahl gilt nicht als Nummer', async ({ page }, testInfo) => {
        await richteAppEin(page)
        await page.goto('projekte')
        await expect(page.getByText(LANGES_WORT_BV)).toBeVisible()
        await pruefeBildschirmbreite(page)
        await designPruefung(page, testInfo, 'projekte-liste-lange-bv')

        await page.goto('projekte?id=1')
        await expect(page.getByRole('heading', { level: 1, name: LANGES_BV })).toBeVisible()
        await pruefeBildschirmbreite(page)
        // Festnetz ist nur die Vorwahl: Die Handynummer wird direkt angeboten, keine Auswahl „2 Nummern“.
        const anrufen = page.getByRole('button', { name: /Anrufen/ })
        await expect(anrufen).toContainText('0151 12345678')
        await expect(page.getByText('2 Nummern verfügbar')).toHaveCount(0)
        await designPruefung(page, testInfo, 'projekte-detail-lange-bv')

        await page.goto('projekte?id=2')
        await expect(page.getByRole('heading', { level: 1, name: LANGES_WORT_BV })).toBeVisible()
        await pruefeBildschirmbreite(page)
        await expect(page.getByText('Keine Telefonnummer hinterlegt')).toBeVisible()
        await expect(page.getByRole('button', { name: /Anrufen/ })).toHaveCount(0)
        await designPruefung(page, testInfo, 'projekte-detail-langes-wort')
    })

    test('Anfragen: Liste und Detail bleiben bildschirmbreit, reine Vorwahl gilt nicht als Nummer', async ({ page }, testInfo) => {
        await richteAppEin(page)
        await page.goto('anfragen')
        await expect(page.getByText(LANGES_WORT_BV)).toBeVisible()
        await pruefeBildschirmbreite(page)
        await designPruefung(page, testInfo, 'anfragen-liste-lange-bv')

        await page.goto('anfragen?id=11')
        await expect(page.getByRole('heading', { level: 1, name: LANGES_BV })).toBeVisible()
        await pruefeBildschirmbreite(page)
        await expect(page.getByRole('button', { name: /Anrufen/ })).toContainText('0151 12345678')
        await expect(page.getByText('2 Nummern verfügbar')).toHaveCount(0)
        await designPruefung(page, testInfo, 'anfragen-detail-lange-bv')

        await page.goto('anfragen?id=12')
        await expect(page.getByRole('heading', { level: 1, name: LANGES_WORT_BV })).toBeVisible()
        await pruefeBildschirmbreite(page)
        await expect(page.getByText('Keine Telefonnummer hinterlegt')).toBeVisible()
        await expect(page.getByRole('button', { name: /Anrufen/ })).toHaveCount(0)
        await designPruefung(page, testInfo, 'anfragen-detail-langes-wort')
    })

    test('Kunden: langer Name bleibt bildschirmbreit, reine Vorwahl wird nicht angezeigt', async ({ page }, testInfo) => {
        await richteAppEin(page)
        await page.goto('kunden')
        await expect(page.getByText(LANGER_NAME)).toBeVisible()
        await pruefeBildschirmbreite(page)
        // Nur die vollständige Nummer erscheint in der Liste, die Vorwahl „0931 “ nicht.
        await expect(page.getByText('0931 123456')).toBeVisible()
        await expect(page.getByText(/^0931\s*$/)).toHaveCount(0)
        await designPruefung(page, testInfo, 'kunden-liste-langer-name')

        await page.goto('kunden?id=21')
        await expect(page.getByRole('heading', { level: 1, name: LANGER_NAME })).toBeVisible()
        await pruefeBildschirmbreite(page)
        await expect(page.getByText('Keine Telefonnummer hinterlegt')).toBeVisible()
        await expect(page.getByRole('button', { name: /Anrufen/ })).toHaveCount(0)
        await designPruefung(page, testInfo, 'kunden-detail-langer-name')
    })

    test('Lieferanten: langer Name bleibt bildschirmbreit, reine Vorwahl wird nicht angezeigt', async ({ page }, testInfo) => {
        await richteAppEin(page)
        await page.goto('lieferanten')
        await expect(page.getByText(LANGER_NAME)).toBeVisible()
        await pruefeBildschirmbreite(page)
        await expect(page.getByText('0931 654321')).toBeVisible()
        await expect(page.getByText(/^09321\s*$/)).toHaveCount(0)
        await designPruefung(page, testInfo, 'lieferanten-liste-langer-name')

        await page.goto('lieferanten?id=31')
        await expect(page.getByRole('heading', { level: 1, name: LANGER_NAME })).toBeVisible()
        await pruefeBildschirmbreite(page)
        await expect(page.getByText('Keine Telefonnummer hinterlegt')).toBeVisible()
        await expect(page.getByRole('button', { name: /Anrufen/ })).toHaveCount(0)
        await designPruefung(page, testInfo, 'lieferanten-detail-langer-name')
    })

    test('Zeiterfassung: Projektauswahl und Kopf bleiben bei langem Bauvorhaben bildschirmbreit', async ({ page }, testInfo) => {
        await richteAppEin(page)
        await page.goto('zeiterfassung')
        await expect(page.getByText(LANGES_WORT_BV)).toBeVisible()
        await pruefeBildschirmbreite(page)
        await designPruefung(page, testInfo, 'zeiterfassung-projektauswahl-lange-bv')

        await page.getByRole('button', { name: new RegExp(LANGES_WORT_BV) }).click()
        await expect(page.getByRole('heading', { level: 1, name: 'Kategorie auswählen' })).toBeVisible()
        await expect(page.getByText('Montage_Treppengelaender_und_Balkongelaender_vor_Ort')).toBeVisible()
        await pruefeBildschirmbreite(page)
        await designPruefung(page, testInfo, 'zeiterfassung-kategorie-lange-bv')
    })
})
