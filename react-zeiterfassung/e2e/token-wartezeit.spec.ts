import { test, expect } from '@playwright/test'

const validToken = '12345678-1234-4234-8234-123456789abc'

test('steigende Wartezeit bleibt nach Neuladen bestehen und erlaubt danach die Anmeldung', async ({ page }, testInfo) => {
    let attempts = 0
    let authenticatedApiRequest = false
    await page.clock.install()
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url())
        if (url.pathname.startsWith('/api/mitarbeiter/by-token/')) {
            attempts++
            if (url.pathname.endsWith(validToken)) {
                await route.fulfill({ json: { id: 7, vorname: 'Max', nachname: 'Mustermann', aktiv: true } })
            } else if (attempts >= 3) {
                await route.fulfill({ status: 429, headers: { 'Retry-After': attempts === 3 ? '2' : '4' }, json: { error: 'Bitte warten.' } })
            } else {
                await route.fulfill({ status: 401, json: { error: 'Token ungültig.' } })
            }
            return
        }
        if (route.request().headers()['x-auth-token'] === validToken) authenticatedApiRequest = true
        await route.fulfill({ json: [] })
    })
    await page.goto('./')
    await page.getByText('Token manuell eingeben', { exact: true }).click()
    for (let i = 0; i < 3; i++) {
        await page.getByPlaceholder('Token eingeben...').fill(`falscher-token-${i}`)
        await page.getByRole('button', { name: 'Anmelden', exact: true }).click()
        await expect(page.getByText(i < 2 ? /Token ungültig/ : /Zu viele Anmeldeversuche/).last()).toBeVisible()
    }
    await expect(page.getByRole('button', { name: 'Anmelden', exact: true })).toBeDisabled()
    await expect(page.getByRole('status')).toContainText('2 Sekunden')
    await page.reload()
    await expect(page.getByRole('button', { name: /Kamera öffnen/ })).toBeDisabled()
    await page.getByText('Token manuell eingeben', { exact: true }).click()
    await page.getByPlaceholder('Token eingeben...').fill('noch-falsch')
    await page.getByPlaceholder('Token eingeben...').press('Enter')
    expect(attempts).toBe(3)
    await page.screenshot({ path: testInfo.outputPath('anmeldesperre.png'), fullPage: true })
    await page.clock.fastForward(2_100)
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click()
    await expect(page.getByRole('status')).toContainText('4 Sekunden')
    await page.clock.fastForward(4_100)
    await page.getByPlaceholder('Token eingeben...').fill(validToken)
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click()
    await expect.poll(() => page.evaluate(() => localStorage.getItem('zeiterfassung_token'))).toBe(validToken)
    await expect.poll(() => authenticatedApiRequest).toBe(true)
})

test('fehlgeschlagene Anmeldung speichert keinen ungeprüften Token', async ({ page }) => {
    await page.route('**/api/mitarbeiter/by-token/**', route => route.abort('failed'))
    await page.goto('./?token=unverified-token')
    await expect(page.getByText('Server nicht erreichbar. Bitte später erneut versuchen.').last()).toBeVisible()
    expect(await page.evaluate(() => localStorage.getItem('zeiterfassung_token'))).toBeNull()
    await expect(page).not.toHaveURL(/token=/)
})

test('entfernt auch während einer bestehenden Wartezeit den QR-Token aus der URL', async ({ page }) => {
    await page.addInitScript(() => {
        sessionStorage.setItem('zeiterfassung_login_retry_until', String(Date.now() + 60_000))
    })
    let loginRequests = 0
    await page.route('**/api/mitarbeiter/by-token/**', route => {
        loginRequests++
        return route.fulfill({ status: 429, headers: { 'Retry-After': '60' }, json: {} })
    })
    await page.goto(`./?token=${validToken}`)
    await expect(page.getByRole('button', { name: /Kamera öffnen/ })).toBeDisabled()
    await expect(page).not.toHaveURL(/token=/)
    expect(loginRequests).toBe(0)
    expect(await page.evaluate(() => localStorage.getItem('zeiterfassung_token'))).toBeNull()
})

test('Wartezeit beim App-Start meldet nicht ab – sie gilt für das Netz, nicht für den Token', async ({ page }) => {
    await page.addInitScript(token => {
        localStorage.setItem('zeiterfassung_token', token)
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 7, name: 'Max Mustermann' }))
    }, validToken)
    await page.route('**/api/**', route => route.fulfill({
        status: 429, headers: { 'Retry-After': '60' }, json: { error: 'Bitte warten.' },
    }))
    await page.clock.install()
    let pruefungen = 0
    await page.route('**/api/mitarbeiter/by-token/**', route => {
        pruefungen++
        return pruefungen === 1
            ? route.fulfill({ status: 429, headers: { 'Retry-After': '60' }, json: { error: 'Bitte warten.' } })
            : route.fulfill({ json: { id: 7, vorname: 'Max', nachname: 'Mustermann', aktiv: true } })
    })
    await page.goto('./')
    await expect(page.getByText(/Die App gleicht in 60 Sekunden erneut ab/).last()).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sync-Fehler', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /Kamera öffnen/ })).toHaveCount(0)
    expect(await page.evaluate(() => localStorage.getItem('zeiterfassung_token'))).toBe(validToken)
    // Nach der Wartezeit gleicht die App genau einmal erneut ab.
    await page.clock.fastForward(61_000)
    await expect.poll(() => pruefungen).toBe(2)
})

test('zeigt nach erfolgreicher Anmeldung einen gesperrten Datenabgleich als Fehler an', async ({ page }) => {
    await page.route('**/api/**', route => {
        if (new URL(route.request().url()).pathname.startsWith('/api/mitarbeiter/by-token/')) {
            return route.fulfill({ json: { id: 7, vorname: 'Max', nachname: 'Mustermann', aktiv: true } })
        }
        return route.fulfill({ status: 429, headers: { 'Retry-After': '60' }, json: {} })
    })
    await page.goto(`./?token=${validToken}`)
    await expect(page.getByRole('button', { name: 'Sync-Fehler', exact: true })).toBeVisible()
    expect(await page.evaluate(() => localStorage.getItem('zeiterfassung_token'))).toBe(validToken)
})

test('sendet den gespeicherten Token nicht an fremde Ursprünge', async ({ page }) => {
    let forwardedToken: string | undefined
    let foreignRequestSeen = false
    await page.route('https://external.example/api/target', route => {
        if (route.request().method() !== 'OPTIONS') {
            foreignRequestSeen = true
            forwardedToken = route.request().headers()['x-auth-token']
        }
        return route.fulfill({ headers: {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Headers': 'X-Auth-Token',
            'Access-Control-Allow-Methods': 'GET, OPTIONS',
        }, json: {} })
    })
    await page.goto('./')
    await expect(page.getByRole('button', { name: /Kamera öffnen/ })).toBeVisible()
    await page.evaluate(async token => {
        localStorage.setItem('zeiterfassung_token', token)
        await fetch('https://external.example/api/target')
    }, validToken)
    expect(foreignRequestSeen).toBe(true)
    expect(forwardedToken).toBeUndefined()
})

test('zeigt bei einer laufenden QR-Anmeldung den Ladezustand und erlaubt nach einem Fehler einen neuen Versuch', async ({ page }) => {
    await page.addInitScript(() => {
        const messages: unknown[] = []
        Object.assign(window, { validatedWorkerMessages: messages })
        Object.defineProperty(navigator.serviceWorker, 'getRegistration', {
            value: async () => ({ active: { postMessage: (message: unknown) => messages.push(message) } }),
        })
    })
    let releaseLogin!: () => void
    const pendingLogin = new Promise<void>(resolve => { releaseLogin = resolve })
    let attempts = 0
    await page.route('**/api/**', async route => {
        if (new URL(route.request().url()).pathname.startsWith('/api/mitarbeiter/by-token/')) {
            attempts++
            if (attempts === 1) {
                await pendingLogin
                return route.fulfill({ status: 503, json: {} })
            }
            return route.fulfill({ json: { id: 7, vorname: 'Max', nachname: 'Mustermann', aktiv: true } })
        }
        return route.fulfill({ json: [] })
    })
    await page.goto(`./?token=${validToken}`)
    try {
        await expect.poll(() => attempts).toBe(1)
        await expect(page.getByText('Wird geladen...', { exact: true })).toBeVisible()
        expect(await page.evaluate(() => localStorage.getItem('zeiterfassung_token'))).toBeNull()
    } finally {
        releaseLogin()
    }
    await expect(page.getByText('Server nicht erreichbar. Bitte später erneut versuchen.').last()).toBeVisible()
    expect(await page.evaluate(() => (window as unknown as { validatedWorkerMessages: unknown[] }).validatedWorkerMessages)).toEqual([])
    await page.getByText('Token manuell eingeben', { exact: true }).click()
    await page.getByPlaceholder('Token eingeben...').fill(validToken)
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click()
    await expect.poll(() => page.evaluate(() => localStorage.getItem('zeiterfassung_token'))).toBe(validToken)
    await expect.poll(() => page.evaluate(() => (window as unknown as { validatedWorkerMessages: unknown[] }).validatedWorkerMessages))
        .toEqual([{ type: 'AUTH_VALIDATED', token: validToken }])
    expect(attempts).toBe(2)
})

test('ein neuer QR-Code wechselt das Konto erst nach erfolgreicher Prüfung ohne den bisherigen Token zu senden', async ({ page }) => {
    await page.addInitScript(() => {
        localStorage.setItem('zeiterfassung_token', 'bisheriger-test-token')
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 1, name: 'Erika Musterfrau' }))
    })
    let loginHeader: string | undefined
    let loginRequests = 0
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname
        if (path.startsWith('/api/mitarbeiter/by-token/')) {
            loginRequests++
            loginHeader = route.request().headers()['x-auth-token']
            return route.fulfill({ json: { id: 7, vorname: 'Max', nachname: 'Mustermann', aktiv: true } })
        }
        return route.fulfill({ json: [] })
    })
    await page.goto(`./?token=${validToken}`)
    await expect.poll(() => page.evaluate(() => localStorage.getItem('zeiterfassung_token'))).toBe(validToken)
    await expect(page.getByRole('button', { name: 'Online', exact: true })).toBeVisible()
    expect(await page.evaluate(() => JSON.parse(localStorage.getItem('zeiterfassung_mitarbeiter')!).id)).toBe(7)
    expect(loginHeader).toBeUndefined()
    expect(loginRequests).toBe(1)
    await expect(page).not.toHaveURL(/token=/)
})

test('eine bereits geprüfte Anmeldung bleibt bei einem echten Netzwerkausfall nutzbar', async ({ page }) => {
    await page.addInitScript(token => {
        localStorage.setItem('zeiterfassung_token', token)
        localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify({ id: 7, name: 'Max Mustermann' }))
    }, validToken)
    await page.route('**/api/**', route => route.abort('failed'))
    await page.goto('./')
    await expect(page.getByRole('button', { name: 'Projekte', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sync-Fehler', exact: true })).toBeVisible()
    expect(await page.evaluate(() => localStorage.getItem('zeiterfassung_token'))).toBe(validToken)
    await expect(page.getByRole('button', { name: /Kamera öffnen/ })).toHaveCount(0)
})
