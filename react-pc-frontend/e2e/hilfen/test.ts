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
 * Bekannte Grenze: `vite.config.ts` proxied `/api` -> `https://localhost:8080`
 * SERVERSEITIG im Vite-Dev-Server-Prozess -- der Browser sieht nach aussen nur
 * eine Anfrage an `localhost:<E2E_PORT>/api/...`, nie den eigentlichen Sprung
 * nach `:8080`. Diese Route (und jede andere `page.route('**\/api/**')`, die
 * eine Spec zusaetzlich registriert) bleibt davon unberuehrt -- sie sieht die
 * lokale Anfrage wie gewohnt und beantwortet sie aus dem Stub, ohne dass
 * `:8080` je erreicht werden muesste (es laeuft ohnehin kein Backend). Fuer
 * `page.route`/`context.route` ist der Proxy-Sprung schlicht unsichtbar --
 * beide sehen nur, was der Browser anfragt, nicht was der Dev-Server-Prozess
 * intern weiterleitet.
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
        await benutzeFixture(context);
    },
});

export { expect };
