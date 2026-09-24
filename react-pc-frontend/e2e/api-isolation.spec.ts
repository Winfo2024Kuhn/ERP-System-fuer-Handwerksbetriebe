import { test, expect } from './hilfen/test';

test('nicht simulierte API-Anfragen erreichen keinen lokalen Backendprozess', async ({ page, baseURL }) => {
  const fehlgeschlagen: string[] = [];
  page.on('requestfailed', request => fehlgeschlagen.push(request.url()));
  await page.route('**/api/isolation-simuliert', route => route.fulfill({ json: { dummy: true } }));
  await page.goto(baseURL!);
  const ergebnis = await page.evaluate(async () => {
    const gesperrt = await Promise.all(['GET', 'POST'].map(async method => {
      try { await fetch(`/api/isolation-nicht-simuliert?method=${method}`, { method }); return false; }
      catch { return true; }
    }));
    const simuliert = await fetch('/api/isolation-simuliert').then(response => response.json());
    return { gesperrt, simuliert };
  });
  expect(ergebnis).toEqual({ gesperrt: [true, true], simuliert: { dummy: true } });
  expect(fehlgeschlagen.filter(url => url.includes('/api/isolation-nicht-simuliert'))).toHaveLength(2);
});
