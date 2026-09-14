import { describe, expect, it, vi, afterEach } from 'vitest';
import { EmailDraftSession, loadEmailDraft, type EmailDraftContent } from './emailDraftPersistence';

const content: EmailDraftContent = {
    recipient: 'test@example.com', cc: 'kopie@example.com', subject: 'Plan', body: '<p>Hallo</p>',
    fromAddress: 'firma@example.com', replyEmailId: null, projektId: null, anfrageId: null,
    geschaeftsdokument: false,
};
const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status });
afterEach(() => vi.unstubAllGlobals());

describe('Email draft persistence', () => {
    it('saves actual attachments with the content', async () => {
        const requests: RequestInit[] = [];
        vi.stubGlobal('fetch', async (_url: string, init: RequestInit) => {
            requests.push(init);
            return response({ id: 42 });
        });
        const session = new EmailDraftSession();
        const file = new File(['Planinhalt'], 'plan.pdf', { type: 'application/pdf' });
        await session.save({ content, files: [file] });
        const data = requests[0].body as FormData;
        expect(data.getAll('attachments')).toEqual([file]);
        expect(session.id).toBe(42);
    });

    it('serializes a pending create before deleting so no draft can reappear after sending', async () => {
        const requests: string[] = [];
        let finishCreate!: (value: Response) => void;
        vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
            requests.push(`${init.method} ${url}`);
            if (init.method === 'POST') return new Promise<Response>(resolve => { finishCreate = resolve; });
            return response({});
        });
        const session = new EmailDraftSession();
        const saving = session.save({ content, files: [] });
        await vi.waitFor(() => expect(requests).toHaveLength(1));
        const deleting = session.remove();
        finishCreate(response({ id: 42 }));
        await Promise.all([saving, deleting]);
        await session.save({ content, files: [] });
        expect(requests).toEqual(['POST /api/emails/drafts', 'DELETE /api/emails/drafts/42']);
    });

    it('queues updates behind creation and sends an empty attachment list after removal', async () => {
        const requests: { url: string; data: FormData }[] = [];
        vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
            requests.push({ url, data: init.body as FormData });
            return response({ id: 42 });
        });
        const session = new EmailDraftSession();
        const file = new File(['Plan'], 'plan.pdf');
        await Promise.all([
            session.save({ content, files: [file] }),
            session.save({ content: { ...content, subject: 'Geändert' }, files: [] }),
        ]);
        expect(requests.map(request => request.url)).toEqual(['/api/emails/drafts', '/api/emails/drafts/42']);
        expect(requests[1].data.getAll('attachments')).toEqual([]);
    });

    it('rejects HTTP failures and allows a later retry', async () => {
        const fetchMock = vi.fn().mockResolvedValueOnce(response({}, 500)).mockResolvedValueOnce(response({ id: 42 }));
        vi.stubGlobal('fetch', fetchMock);
        const session = new EmailDraftSession();
        await expect(session.save({ content, files: [] })).rejects.toThrow('Entwurf');
        expect(session.id).toBeNull();
        await session.save({ content, files: [] });
        expect(session.id).toBe(42);
    });

    it('completes a server-deleted draft without another network request or future saves', async () => {
        const fetchMock = vi.fn().mockResolvedValue(response({ id: 42 }));
        vi.stubGlobal('fetch', fetchMock);
        const session = new EmailDraftSession();
        await session.save({ content, files: [] });
        await session.complete();
        session.resume();
        await session.save({ content, files: [] });
        expect(session.id).toBeNull();
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('restores attachment bytes and refuses partial loading', async () => {
        vi.stubGlobal('fetch', async (url: string) => url.endsWith('/attachments/7')
            ? new Response('PDF-Inhalt')
            : response({ ...content, id: 42, attachments: [{ id: 7, filename: 'plan.pdf', contentType: 'application/pdf', size: 10 }] }));
        const loaded = await loadEmailDraft(42);
        expect(loaded.files[0].name).toBe('plan.pdf');
        expect(loaded.files[0].size).toBe(10);
        expect(loaded.content.cc).toBe('kopie@example.com');
        vi.stubGlobal('fetch', async (url: string) => url.endsWith('/attachments/7')
            ? response({}, 404)
            : response({ ...content, id: 42, attachments: [{ id: 7, filename: 'plan.pdf', contentType: 'application/pdf', size: 10 }] }));
        await expect(loadEmailDraft(42)).rejects.toThrow('Anhang');
    });
});
