import { chromium, type FullConfig } from '@playwright/test';

/**
 * globalSetup: waermt den Vite-Dev-Server einmal auf, bevor der erste Test laeuft.
 *
 * Grund: Vite kompiliert Module erst beim ersten Browser-Zugriff. Auf einem
 * kalten Server brauchte die erste Seite 19-21 s statt 5-6 s, und
 * `toBeVisible()` gab nach seinen 5 s Standard auf -- vier Tests rot, obwohl
 * die Seite gleich darauf da war (Design-Review 7-1). Die Readiness-Pruefung
 * von `webServer.url` sieht das nicht: sie bekommt die index.html sofort,
 * die Module dahinter sind dann noch nicht gebaut.
 *
 * Deshalb: einmal die Startseite und den schwersten Editor mit einem echten
 * Browser laden und auf Netzruhe warten. Danach sind die Module im Cache,
 * und jeder Test sieht einen warmen Server -- so, wie es der Nutzer auch tut,
 * der nie den allerersten Request nach dem Serverstart abbekommt.
 *
 * Ohne Backend liefern die /api-Routen Fehler; das ist hier egal, es geht nur
 * um die Kompilierung der Frontend-Module.
 */
export default async function aufwaermen(config: FullConfig): Promise<void> {
    const baseURL = config.projects[0]?.use?.baseURL;
    if (!baseURL) return;

    const browser = await chromium.launch();
    try {
        const page = await browser.newPage();
        for (const pfad of ['/', '/dokument-editor', '/lieferanten']) {
            try {
                await page.goto(`${baseURL}${pfad}`, { waitUntil: 'networkidle', timeout: 90_000 });
            } catch {
                // Eine Route, die es nicht (mehr) gibt oder die ohne Backend haengt,
                // darf das Aufwaermen nicht abbrechen -- die Module sind trotzdem
                // kompiliert, sobald der Browser sie angefordert hat.
            }
        }
    } finally {
        await browser.close();
    }
}
