import { test as base, expect } from '@playwright/test';
import { blockiereFremdeNetzwerkzugriffe } from './api';

/**
 * Gemeinsames `test` fuer ALLE Specs (Nachtrag Abschnitt 10, Empfehlung des
 * Code-Reviewers aus Abschnitt 4).
 *
 * Bisher hing `blockiereFremdeNetzwerkzugriffe` (siehe ./api.ts fuer die
 * Begruendung: echte Anfragen an z.B. google.com/maps, cdnjs.cloudflare.com,
 * nominatim.openstreetmap.org, photon.komoot.io machten Screenshots
 * nichtreproduzierbar und waren eine plausible Ursache sporadisch leerer
 * Seiten unter parallelen Workern) in genau einer von 17 Specs und wurde dort
 * per Hand auf `page.route` registriert.
 *
 * Zwei Probleme daran: (1) 16 Specs hatten gar keinen Riegel, (2)
 * `page.route` sieht keine Popups (window.open oeffnet eine NEUE Page im
 * selben BrowserContext, die ihre eigene Route-Liste mitbringt). Diese Datei
 * loest beides: der `context`-Fixture wird ueberschrieben und riegelt VOR
 * jeder Navigation ab, bevor `page` (das intern denselben `context` benutzt)
 * ueberhaupt entsteht -- jede aus diesem Context geoeffnete Page, auch ein
 * Popup, erbt die Route automatisch.
 *
 * Jede Spec importiert `test`/`expect` von HIER statt von '@playwright/test'
 * -- Typ-Importe (Page, Route, Locator, ...) bleiben unveraendert bei
 * '@playwright/test', die exportiert dieselben Typen.
 *
 * Zusätzlich werden nicht simulierte /api-Anfragen blockiert, bevor Vite sie
 * an einen lokal laufenden Backendprozess weiterleiten kann. Die getrennte
 * Real-E2E-Suite nutzt diese Mock-Fixture nicht.
 */
export const test = base.extend({
    // Playwrights Fixture-API nennt den zweiten Parameter ueblicherweise "use"
    // (siehe Playwright-Doku) -- das kollidiert mit eslint-plugin-react-hooks,
    // das jeden Aufruf einer Funktion namens "use(...)" fuer den React-18-Hook
    // haelt und ausserhalb einer Komponente/eines Hooks meldet. Playwright
    // selbst ist der Name egal (rein positionell), deshalb hier umbenannt statt
    // die Regel abzuschalten.
    context: async ({ context }, benutzeFixture) => {
        await blockiereFremdeNetzwerkzugriffe(context);
        // Fach-APIs dieser Suite müssen explizit simuliert sein. Sonst würde
        // Vite sie serverseitig an eine eventuell laufende echte App senden.
        // Spezifische page.route-Stubs der Tests haben Vorrang vor diesem Riegel.
        await context.route('**/api/**', route => route.abort('blockedbyclient'));
        await benutzeFixture(context);
    },
});

export { expect };
