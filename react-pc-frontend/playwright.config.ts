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
 *   pc-14zoll   1440 x 900   -- 14-Zoll-MacBook, das Kleinste, was es geben soll
 *   pc-monitor  1920 x 1080  -- grosser Monitor am Arbeitsplatz
 * Jede Spec laeuft in beiden Groessen. Handy und Tablet sind fuer die
 * PC-App nicht vorgesehen und werden hier nicht geprueft.
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
