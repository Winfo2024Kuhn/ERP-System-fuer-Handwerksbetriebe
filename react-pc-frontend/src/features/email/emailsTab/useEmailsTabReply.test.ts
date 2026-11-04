import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { EmailThreadEntry } from '../../../components/EmailThreadView';
import { useEmailsTabReply } from './useEmailsTabReply';

/** Dummy-Daten (DSGVO): Max Mustermann, max@example.com. */
const eintrag: EmailThreadEntry = {
    id: 42,
    subject: 'Anfrage Geländer',
    fromAddress: 'Max Mustermann <max@example.com>',
    recipient: 'firma@example.com',
    cc: 'erika@example.com',
    replyToAddress: undefined,
    sentAt: '2026-10-09T10:05:00',
    direction: 'IN',
    htmlBody: '<p>Text aus dem Verlauf</p>',
    snippet: 'Text aus dem Verlauf',
    attachments: [],
};

afterEach(() => vi.unstubAllGlobals());

describe('useEmailsTabReply', () => {
    it('startet ohne Antwort', () => {
        const { result } = renderHook(() => useEmailsTabReply());
        expect(result.current.isReplying).toBe(false);
        expect(result.current.replyToEmail).toBeNull();
        expect(result.current.replyToEmailId).toBeUndefined();
        expect(result.current.replyQuote).toBeUndefined();
        expect(result.current.replyInitialRecipient).toBe('');
        expect(result.current.replyInitialSubject).toBe('AW: ');
    });

    it('holt die vollständige Mail und baut daraus Zitat, Empfänger und Betreff', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: () => Promise.resolve({
                id: 42, subject: 'Anfrage Treppe', fromAddress: 'Max Mustermann <max@example.com>',
                direction: 'IN', sentAt: '2026-10-09T10:05:00', bodyHtml: '<p>Vollständiger Text</p>',
            }),
        });
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useEmailsTabReply());

        await act(() => result.current.startReply(eintrag));

        expect(fetchMock).toHaveBeenCalledWith('/api/emails/42');
        expect(result.current.isReplying).toBe(true);
        expect(result.current.replyToEmailId).toBe(42);
        expect(result.current.replyQuote).toContain('<p>Vollständiger Text</p>');
        expect(result.current.replyInitialRecipient).toContain('max@example.com');
        expect(result.current.replyInitialSubject).toBe('AW: Anfrage Treppe');
    });

    it('nimmt die Daten aus dem Verlauf, wenn die Mail nicht geladen werden kann', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
        const { result } = renderHook(() => useEmailsTabReply());

        await act(() => result.current.startReply(eintrag));

        expect(result.current.isReplying).toBe(true);
        expect(result.current.replyToEmail).toEqual({
            id: 42, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
            to: 'firma@example.com', recipient: 'firma@example.com', cc: 'erika@example.com', replyToAddress: undefined,
            bodyHtml: '<p>Text aus dem Verlauf</p>', body: 'Text aus dem Verlauf',
            direction: 'IN', sentAt: '2026-10-09T10:05:00',
        });
        expect(result.current.replyInitialSubject).toBe('AW: Anfrage Geländer');
        expect(result.current.replyQuote).toContain('<p>Text aus dem Verlauf</p>');
    });

    it('nimmt die Daten aus dem Verlauf auch, wenn der Server ablehnt', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: () => Promise.resolve(null) }));
        const { result } = renderHook(() => useEmailsTabReply());

        await act(() => result.current.startReply(eintrag));

        expect(result.current.replyToEmail?.body).toBe('Text aus dem Verlauf');
    });

    it('zeigt das Laden und verwirft die späte Mail, wenn die Antwort inzwischen abgebrochen wurde', async () => {
        let spaet: (r: Response) => void = () => {};
        vi.stubGlobal('fetch', vi.fn().mockReturnValueOnce(new Promise<Response>(resolve => { spaet = resolve; })));
        const { result } = renderHook(() => useEmailsTabReply());

        let laeuft: Promise<void> = Promise.resolve();
        act(() => { laeuft = result.current.startReply(eintrag); });
        expect(result.current.replyLaedt).toBe(true);
        act(() => result.current.cancelReply());
        expect(result.current.replyLaedt).toBe(false);

        await act(async () => { spaet({ ok: true, json: () => Promise.resolve({ id: 42, subject: 'Spät' }) } as Response); await laeuft; });
        expect(result.current.isReplying).toBe(false);
        expect(result.current.replyToEmail).toBeNull();
    });

    it('übernimmt bei zwei schnellen Klicks nur die zuletzt gewählte Mail', async () => {
        let ersteAntwort: (r: Response) => void = () => {};
        vi.stubGlobal('fetch', vi.fn()
            .mockReturnValueOnce(new Promise<Response>(resolve => { ersteAntwort = resolve; }))
            .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ id: 43, subject: 'Zweite Mail' }) }));
        const { result } = renderHook(() => useEmailsTabReply());

        let erste: Promise<void> = Promise.resolve();
        act(() => { erste = result.current.startReply(eintrag); });
        await act(() => result.current.startReply({ ...eintrag, id: 43 }));
        expect(result.current.replyToEmailId).toBe(43);
        expect(result.current.replyLaedt).toBe(false);

        await act(async () => { ersteAntwort({ ok: true, json: () => Promise.resolve({ id: 42, subject: 'Erste Mail' }) } as Response); await erste; });
        expect(result.current.replyToEmailId).toBe(43);
        expect(result.current.replyToEmail?.subject).toBe('Zweite Mail');
    });

    it('bricht die Antwort ab und vergisst die Mail', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
        const { result } = renderHook(() => useEmailsTabReply());
        await act(() => result.current.startReply(eintrag));

        act(() => result.current.cancelReply());

        expect(result.current.isReplying).toBe(false);
        expect(result.current.replyToEmail).toBeNull();
        expect(result.current.replyToEmailId).toBeUndefined();
        expect(result.current.replyQuote).toBeUndefined();
    });
});
