import { test as base, expect } from '@playwright/test'

/** Gemeinsame Netzwerkgrenze für alle mobilen Browserprüfungen, auch für Popups. */
export const test = base.extend({
    context: async ({ context }, benutzeFixture) => {
        await context.route('**/*', route => {
            const host = new URL(route.request().url()).hostname
            return host === 'localhost' || host === '127.0.0.1' || host === '[::1]'
                ? route.continue() : route.abort()
        })
        await benutzeFixture(context)
    },
})

export { expect }
