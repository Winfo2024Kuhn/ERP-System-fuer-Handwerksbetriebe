import { defineConfig, devices } from '@playwright/test';

const port = Number(process.env.EN1090_PREVIEW_PORT ?? 8098);
export default defineConfig({
    testDir: './preview/e2e',
    workers: 1,
    timeout: 60000,
    expect: { timeout: 15000 },
    reporter: 'list',
    outputDir: '/tmp/en1090-original-e2e',
    use: { baseURL: `http://127.0.0.1:${port}`, locale: 'de-DE', screenshot: 'only-on-failure' },
    projects: [
        { name: 'pc-14zoll', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
        { name: 'pc-uebergang', use: { ...devices['Desktop Chrome'], viewport: { width: 1536, height: 960 } } },
        { name: 'pc-monitor', use: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 } } },
    ],
});
