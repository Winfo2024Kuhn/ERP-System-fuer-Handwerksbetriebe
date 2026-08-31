import { expect, type Page } from '@playwright/test';

/**
 * Gemeinsame Locator-Helfer.
 *
 * Wichtig: Die Anwendung zeigt neben dem Seiteninhalt dauerhaft die
 * Menueleiste (RibbonNav) mit Breadcrumb. Woerter wie "Website" oder ein
 * Beitragstitel stehen dadurch zweimal im Dokument. Alle Zusicherungen
 * laufen deshalb ueber inhalt(page) statt ueber die ganze Seite.
 */
export function inhalt(page: Page) {
    return page.getByRole('main');
}

/**
 * Die Projektsuche. Ihre Ueberschrift "Projekt auswählen" gibt es zweimal --
 * einmal im Modal, einmal in der Kopfzeile des Assistenten, solange noch
 * kein Projekt gewaehlt ist. Das Suchfeld gibt es dagegen nur einmal.
 */
export function projektsuche(page: Page) {
    return page.getByPlaceholder(/Freitext suchen/);
}

export async function warteAufProjektsuche(page: Page) {
    await expect(projektsuche(page)).toBeVisible();
}
