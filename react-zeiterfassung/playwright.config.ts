import { defineConfig, devices } from '@playwright/test'

const port = Number(process.env.E2E_PORT || 5175)

export default defineConfig({
    testDir: './e2e',
    outputDir: 'test-results',
    use: {
        ...devices['iPhone 15'],
        baseURL: `https://127.0.0.1:${port}/zeiterfassung/`,
        ignoreHTTPSErrors: true,
    },
    projects: [{ name: 'handy', use: { ...devices['iPhone 15'], browserName: 'chromium' } }],
    webServer: {
        command: `vite --host 127.0.0.1 --port ${port} --strictPort`,
        url: `https://127.0.0.1:${port}/zeiterfassung/`,
        ignoreHTTPSErrors: true,
        reuseExistingServer: false,
    },
})
