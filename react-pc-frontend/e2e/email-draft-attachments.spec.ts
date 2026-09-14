import { test, expect } from './hilfen/test';
import type { Page, Route } from '@playwright/test';

interface Attachment { id: number; filename: string; contentType: string; size: number; bytes: Buffer }
interface Draft {
    id: number; recipient: string; cc: string; subject: string; body: string;
    fromAddress: string; projektId: number | null; anfrageId: number | null; replyEmailId: number | null;
    attachments: Attachment[]; updatedAt: string;
}
const plan = Buffer.from('%PDF-1.4\nPlaninhalt\n%%EOF');
const foto = Buffer.from('zweiter Anhang');
const json = (route: Route, body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

async function setup(page: Page, initial?: Draft) {
    const state = { draft: initial, writes: 0, failSave: false, sent: [] as { filename: string; bytes: Buffer }[], failDownload: false, saveDelay: 0, draftDeletes: 0 };
    await page.route('**/api/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/emails/drafts' && request.method() === 'GET') return json(route, state.draft ? [state.draft] : []);
        if (path === '/api/emails/drafts/count') return json(route, { count: state.draft ? 1 : 0 });
        if (path.includes('/drafts/') && path.includes('/attachments/')) {
            const attachment = state.draft?.attachments.find(file => path.endsWith(`/${file.id}`));
            return state.failDownload || !attachment ? json(route, {}, 404) : route.fulfill({ contentType: 'application/octet-stream', body: attachment.bytes });
        }
        if (path === '/api/emails/drafts/42' && request.method() === 'GET') return json(route, state.draft, state.draft ? 200 : 404);
        if (path === '/api/emails/drafts/42' && request.method() === 'DELETE') { state.draftDeletes++; state.draft = undefined; return json(route, {}); }
        if ((path === '/api/emails/drafts' || path === '/api/emails/drafts/42') && ['POST', 'PUT'].includes(request.method())) {
            state.writes++;
            if (state.failSave) return json(route, {}, 500);
            if (state.saveDelay) await new Promise(resolve => setTimeout(resolve, state.saveDelay));
            const multipart = await new Response(new Uint8Array(request.postDataBuffer()!), { headers: { 'Content-Type': request.headers()['content-type'] } }).formData();
            const content = JSON.parse(await (multipart.get('dto') as File).text()) as Draft;
            const attachments = await Promise.all(multipart.getAll('attachments').map(async (entry, index) => {
                const file = entry as File;
                return { id: index + 1, filename: file.name, contentType: file.type, size: file.size, bytes: Buffer.from(await file.arrayBuffer()) };
            }));
            state.draft = { ...content, id: 42, attachments, updatedAt: '2026-09-14T12:00:00' };
            return json(route, state.draft);
        }
        if (path === '/api/emails/send') {
            const multipart = await new Response(new Uint8Array(request.postDataBuffer()!), { headers: { 'Content-Type': request.headers()['content-type'] } }).formData();
            state.sent = await Promise.all(multipart.getAll('attachments').map(async entry => {
                const file = entry as File;
                return { filename: file.name, bytes: Buffer.from(await file.arrayBuffer()) };
            }));
            const dto = JSON.parse(await (multipart.get('dto') as File).text()) as { draftId?: number };
            if (dto.draftId === state.draft?.id) state.draft = undefined;
            return json(route, { id: 100 });
        }
        if (path.endsWith('/from-addresses')) return json(route, ['betrieb@example.com']);
        if (path.endsWith('/stats')) return json(route, { inboxCount: 0 });
        return json(route, []);
    });
    await page.goto(initial ? '/emails/drafts' : '/emails/inbox');
    return state;
}

function existingDraft(): Draft {
    return { id: 42, recipient: 'test@example.com', cc: '"Mustermann, Max" <kopie@example.com>',
        subject: 'Entwurf mit Plan', body: '<p>Bitte prüfen.</p>', fromAddress: 'betrieb@example.com',
        projektId: null, anfrageId: null, replyEmailId: null, updatedAt: '2026-09-14T12:00:00',
        attachments: [{ id: 1, filename: 'plan.pdf', contentType: 'application/pdf', size: plan.length, bytes: plan }] };
}

async function newMessage(page: Page) {
    await page.getByRole('button', { name: 'Neue E-Mail', exact: true }).click();
    await page.getByPlaceholder('Name, Firma oder E-Mail eingeben').fill('test@example.com');
    await page.getByPlaceholder('Betreff eingeben').fill('Plan zum Prüfen');
}

test('Anhänge überleben sofortiges Schließen und erneutes Öffnen; Entfernen und Senden übernehmen echte Dateien', async ({ page }, info) => {
    const state = await setup(page);
    await newMessage(page);
    await page.locator('input[type=file]').setInputFiles([
        { name: 'plan.pdf', mimeType: 'application/pdf', buffer: plan },
        { name: 'hinweis.txt', mimeType: 'text/plain', buffer: foto },
    ]);
    await expect(page.getByText('plan.pdf', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Schließen', exact: true }).click();
    await expect.poll(() => state.draft?.attachments.length).toBe(2);
    expect(state.draft!.attachments[0].bytes.equals(plan)).toBe(true);
    await page.getByRole('button', { name: 'Entwürfe', exact: true }).click();
    await page.getByText('Plan zum Prüfen', { exact: true }).click();
    await expect(page.getByRole('button', { name: 'plan.pdf entfernen' })).toBeVisible();
    await page.getByRole('button', { name: 'hinweis.txt entfernen' }).click();
    await page.getByRole('button', { name: 'Schließen', exact: true }).click();
    await expect.poll(() => state.draft?.attachments.length).toBe(1);
    await page.getByText('Plan zum Prüfen', { exact: true }).click();
    await expect(page.getByRole('button', { name: 'plan.pdf entfernen' })).toBeVisible();
    await page.screenshot({ path: info.outputPath('entwurf-mit-anhang.png') });
    const send = page.getByRole('button', { name: 'E-Mail senden', exact: true });
    const box = await send.boundingBox();
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    expect(box!.y + box!.height).toBeLessThanOrEqual(page.viewportSize()!.height);
    const editorBox = await page.getByRole('textbox', { name: 'Nachricht', exact: true }).boundingBox();
    expect(editorBox!.y).toBeGreaterThan(0);
    expect(box!.y - editorBox!.y).toBeGreaterThanOrEqual(180);
    await send.click();
    await page.getByRole('button', { name: 'Trotzdem senden', exact: true }).click();
    await expect.poll(() => state.sent.length).toBe(1);
    expect(state.sent[0].filename).toBe('plan.pdf');
    expect(state.sent[0].bytes.equals(plan)).toBe(true);
    await expect.poll(() => state.draft).toBeUndefined();
    expect(state.draftDeletes).toBe(0);
    const writesAfterSend = state.writes;
    await page.waitForTimeout(2200);
    expect(state.writes).toBe(writesAfterSend);
});

test('Fehlgeschlagenes Speichern bleibt sichtbar und hält den Entwurf für Wiederholung offen', async ({ page }) => {
    const state = await setup(page);
    await newMessage(page);
    state.failSave = true;
    await page.locator('input[type=file]').setInputFiles({ name: 'plan.pdf', mimeType: 'application/pdf', buffer: plan });
    await expect(page.getByText('Entwurf nicht gespeichert', { exact: false })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Meldungen' })).toContainText('Entwurf konnte nicht gespeichert werden');
    await page.getByRole('button', { name: 'Schließen', exact: true }).click();
    await expect(page.getByRole('button', { name: 'plan.pdf entfernen' })).toBeVisible();
    state.failSave = false;
    await page.getByRole('button', { name: 'Erneut speichern', exact: true }).click();
    await expect(page.getByText('Entwurf gespeichert', { exact: true })).toBeVisible();
    expect(state.draft!.attachments[0].bytes.equals(plan)).toBe(true);
});

test('CC mit Komma im Namen wird wiederhergestellt; CC entfernen wird allein gespeichert', async ({ page }) => {
    const state = await setup(page, existingDraft());
    await page.getByText('Entwurf mit Plan', { exact: true }).click();
    await expect(page.getByPlaceholder('CC Empfänger hinzufügen')).toHaveValue('"Mustermann, Max" <kopie@example.com>');
    await page.getByRole('button', { name: 'Keine', exact: true }).click();
    await expect.poll(() => state.draft?.cc).toBe('');
    expect(state.draft!.attachments[0].bytes.equals(plan)).toBe(true);
});

test('Fehlender Anhang sperrt Bearbeiten und Versand statt einen unvollständigen Entwurf zu überschreiben', async ({ page }) => {
    const state = await setup(page, existingDraft());
    state.failDownload = true;
    await page.getByText('Entwurf mit Plan', { exact: true }).click();
    await expect(page.getByRole('button', { name: 'Erneut laden', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'E-Mail senden', exact: true })).toHaveCount(0);
    expect(state.writes).toBe(0);
    state.failDownload = false;
    await page.getByRole('button', { name: 'Erneut laden', exact: true }).click();
    await expect(page.getByRole('button', { name: 'plan.pdf entfernen' })).toBeVisible();
});


test('Browser-Zurück wartet auf Anhänge im Entwurf und verhindert Eingaben während des Speicherns', async ({ page }) => {
    const state = await setup(page);
    await page.getByRole('button', { name: 'Entwürfe', exact: true }).click();
    await expect(page).toHaveURL(/emails\/drafts$/);
    await newMessage(page);
    state.saveDelay = 700;
    await page.locator('input[type=file]').setInputFiles({ name: 'plan.pdf', mimeType: 'application/pdf', buffer: plan });
    await expect(page.getByRole('button', { name: 'plan.pdf entfernen' })).toBeVisible();
    await page.goBack();
    await expect.poll(() => state.draft?.attachments.length).toBe(1);
    expect(state.draft!.attachments[0].bytes.equals(plan)).toBe(true);
    await expect(page).toHaveURL(/emails\/inbox$/);
    await page.getByRole('button', { name: 'Entwürfe', exact: true }).click();
    await page.getByText('Plan zum Prüfen', { exact: true }).click();
    await expect(page.getByRole('button', { name: 'plan.pdf entfernen' })).toBeVisible();
});
