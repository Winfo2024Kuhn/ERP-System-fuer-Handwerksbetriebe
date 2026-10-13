import { expect, type Page } from '@playwright/test'

/**
 * Schließt alle schwebenden Meldungen per X – so wie ein Handwerker es tut,
 * bevor er einen Knopf darunter antippt. Fehler und Warnungen bleiben stehen,
 * bis man sie schließt, und liegen oben über dem Inhalt (auch über
 * Kopfzeilen-Knöpfen). Über das Label statt über die Rolle gesucht: bei
 * offenem Dialog blendet der Dialog die Ebene für Screenreader aus.
 */
export async function meldungenSchliessen(page: Page): Promise<void> {
    const knoepfe = page.locator('[data-mobile-toasts] button[aria-label="Meldung schließen"]')
    while (await knoepfe.count() > 0) {
        await knoepfe.first().tap()
        // Erst ausblenden lassen, sonst tippt die Schleife dieselbe Karte noch einmal an.
        await expect(page.locator('[data-mobile-toasts] [data-geht]')).toHaveCount(0)
    }
    await expect(page.locator('[data-mobile-toasts]')).toBeHidden()
}
