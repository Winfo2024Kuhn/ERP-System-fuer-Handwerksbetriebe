import { chromium, type FullConfig } from '@playwright/test';
import { blockiereFremdeNetzwerkzugriffe } from './api';

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
 *
 * Nachtrag Abschnitt 10 (Code-Reviewer, Abschnitt 4: "die groesste
 * verbleibende Flake-Quelle"): Dieses globalSetup lief bisher OHNE jedes
 * Routing -- anders als jede Spec (die seit diesem Abschnitt automatisch ueber
 * e2e/hilfen/test.ts abgeriegelt ist) hat es gar keinen eigenen Context mit
 * einer Spec-Fixture, sondern startet Browser/Page von Hand. `waitUntil:
 * 'networkidle'` wartet auf Netzruhe -- haengt oder trödelt eine ECHTE externe
 * Anfrage (z.B. AddressAutocomplete gegen nominatim.openstreetmap.org/
 * photon.komoot.io, falls eine der drei aufgewaermten Seiten sowas laedt, oder
 * Google Maps/cdnjs), verzoegert das jeden Lauf, abhaengig von einer echten
 * Internetverbindung. Denselben Riegel wie in den Specs davorschalten.
 */
export default async function aufwaermen(config: FullConfig): Promise<void> {
    const baseURL = config.projects[0]?.use?.baseURL;
    if (!baseURL) return;

    const browser = await chromium.launch();
    try {
        const page = await browser.newPage();
        await blockiereFremdeNetzwerkzugriffe(page);
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
