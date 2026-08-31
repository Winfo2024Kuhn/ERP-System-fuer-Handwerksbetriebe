import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-End-Tests laufen gegen den Vite-Dev-Server. Das Backend bleibt
 * bewusst aussen vor: jeder Test stubbt die noetigen /api-Routen selbst
 * (siehe e2e/hilfen/api.ts). So laufen die Tests ohne Spring Boot, ohne
 * Datenbank und ohne die externe Firmen-Website -- und ohne echte
 * Personendaten, was hier ohnehin Pflicht ist (DSGVO).
 */
export default defineConfig({
    testDir: './e2e',
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 2 : 0,
    reporter: process.env.CI ? 'html' : 'list',
    use: {
        baseURL: 'http://localhost:5173',
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
        locale: 'de-DE',
    },
    projects: [
        { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    ],
    webServer: {
        command: 'npm run dev',
        url: 'http://localhost:5173',
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
    },
});
