import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-End-Tests laufen gegen den Vite-Dev-Server. Das Backend bleibt
 * bewusst aussen vor: jeder Test stubbt die noetigen /api-Routen selbst
 * (siehe e2e/hilfen/api.ts). So laufen die Tests ohne Spring Boot, ohne
 * Datenbank und ohne die externe Firmen-Website -- und ohne echte
 * Personendaten, was hier ohnehin Pflicht ist (DSGVO).
 *
 * Port: E2E_PORT (Standard 5173). Laufen mehrere Agenten gleichzeitig,
 * bekommt jeder einen eigenen Port, sonst testet einer den Code des
 * anderen. --strictPort sorgt dafuer, dass Vite nicht still auf den
 * naechsten freien Port ausweicht.
 *
 * Bildschirmgroessen (siehe .claude/skills/playwright-design-pruefung):
 *   pc-14zoll    1440 x 900   -- 14-Zoll-MacBook, das Kleinste, was es geben soll
 *   pc-uebergang 1536 x 960   -- Luecke zwischen den beiden Pflichtgroessen (Task 10b,
 *                                Design-Review Abschnitt 6, Befund c): "2xl:"-Klassen
 *                                greifen technisch schon ab 1536px, aber bis dahin war
 *                                nie ein Playwright-Projekt gelaufen. Ein 55-Zeichen-
 *                                Anzeigename sprengte die Menueleisten-Kategorie-Leiste
 *                                dort um 119px, ohne dass irgendein bestehender Check
 *                                anschlug.
 *                                Absichtlich per "testMatch" (siehe unten) auf die
 *                                Menueleisten-Spec begrenzt: ein Probelauf ueber alle
 *                                Specs ("npx playwright test --project=pc-uebergang",
 *                                vor der Begrenzung ausgefuehrt) ergab 18 rote Faelle in
 *                                7 fremden Spec-Dateien -- zwei echte, unabhaengige
 *                                Fundstellen, keine davon in den Files dieses Tasks:
 *                                (1) Kartenraster-Specs (anfrage-/kunde-/lieferant-layout,
 *                                uebersichten-layout) nehmen "3 Karten bei 1440, 4 bei
 *                                1920" an -- Tailwinds "2xl:"-Breakpoint (min-width:
 *                                1536px) greift bei genau 1536px aber schon, die Karten
 *                                springen dort also bereits auf 4 um, was keine dieser
 *                                Specs erwartet.
 *                                (2) bearbeiten-leiste.spec.ts und
 *                                lieferant-dokument-modal.spec.ts: bei 960px Hoehe (weder
 *                                900 noch 1080, beides bisher ungeprueft) ueberlappt ein
 *                                Eingabefeld die Knoepfe "Abbrechen"/"Speichern" der
 *                                Bearbeiten-Leiste -- ein bislang unentdeckter, von der
 *                                Fensterbreite unabhaengiger Layoutfehler.
 *                                Beides bleibt bewusst fremde Baustelle (siehe Kontext-Log
 *                                Abschnitt 7) -- nicht Teil der Task-10b-Files.
 *   pc-monitor   1920 x 1080  -- grosser Monitor am Arbeitsplatz
 * pc-14zoll und pc-monitor laufen fuer jede Spec, pc-uebergang nur fuer die
 * Menueleisten-Spec (siehe testMatch). Handy und Tablet sind fuer die PC-App
 * nicht vorgesehen und werden hier nicht geprueft.
 */
const port = Number(process.env.E2E_PORT ?? 5173);
const baseURL = `http://localhost:${port}`;

export default defineConfig({
    testDir: './e2e',
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 2 : 0,
    reporter: process.env.CI ? 'html' : 'list',
    // Kalter Dev-Server: die erste Seite braucht bis zu 20 s, weil Vite die
    // Module erst beim ersten Zugriff baut. globalSetup waermt einmal auf
    // (siehe e2e/hilfen/aufwaermen.ts), und Zusicherungen bekommen 15 s statt
    // 5 s, damit ein einzelner langsamer Modulbau keinen Test rot dreht.
    globalSetup: './e2e/hilfen/aufwaermen.ts',
    expect: { timeout: 15_000 },
    use: {
        baseURL,
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
        locale: 'de-DE',
    },
    projects: [
        {
            name: 'pc-14zoll',
            use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
        },
        {
            name: 'pc-uebergang',
            use: { ...devices['Desktop Chrome'], viewport: { width: 1536, height: 960 } },
            // Begrenzt auf die Menueleisten-Spec -- siehe Kommentar oben zum
            // Vollstaendigkeits-Lauf, der 18 rote Faelle in 7 fremden Specs ergab.
            testMatch: 'menueleiste-layout.spec.ts',
        },
        {
            name: 'pc-monitor',
            use: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 } },
        },
    ],
    webServer: {
        command: `npm run dev -- --port ${port} --strictPort`,
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
    },
});
