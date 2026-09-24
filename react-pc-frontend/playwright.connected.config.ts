import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
    testDir: './e2e', testMatch: 'bedarf-connected.spec.ts', workers: 1, timeout: 45000,
    outputDir: '/tmp/bedarf-connected-e2e', reporter: 'list',
    webServer: {
        command: 'npm run preview:original',
        url: 'http://127.0.0.1:8104', reuseExistingServer: false, timeout: 30000,
        env: { EN1090_API_MODE: 'connected', EN1090_BACKEND_URL: 'http://127.0.0.1:8096',
            EN1090_PREVIEW_PORT: '8104', EN1090_IDS_SUPPLIER_ID: '42',
            IDS_PREVIEW_CONFIG_PATH: '/tmp/ids-connected-e2e/application-local.properties' },
    },
    use: { baseURL: 'http://127.0.0.1:8104', locale: 'de-DE' },
    projects: [
        { name: 'pc-14zoll', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
        { name: 'pc-uebergang', use: { ...devices['Desktop Chrome'], viewport: { width: 1536, height: 960 } } },
        { name: 'pc-monitor', use: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 } } },
    ],
});
