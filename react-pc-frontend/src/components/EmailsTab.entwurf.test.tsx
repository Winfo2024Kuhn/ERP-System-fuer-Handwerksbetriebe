import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import EmailsTab, { type GenericEmail } from './EmailsTab';
import type { EmailThread } from './EmailThreadView';
import { ToastProvider } from './ui/toast';
import { RUECKGAENGIG_MS } from '../lib/rueckgaengig';

/**
 * Entwurf im Verlauf löschen: sofort weg, „Rückgängig“ in der Meldung, DELETE erst nach
 * Ablauf der Frist, danach neu laden; scheitert es, ist der Entwurf wieder da.
 * Dummy-Daten (DSGVO): Max Mustermann, max@example.com.
 */
const mail: GenericEmail = {
    id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
    direction: 'IN', sentAt: '2026-10-09T10:05:00', bodyHtml: '<p>Bitte um Angebot</p>',
};

function verlauf(mitEntwurf: boolean): EmailThread {
    const emails: EmailThread['emails'] = [{
        id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
        direction: 'IN', sentAt: '2026-10-09T10:05:00', htmlBody: '<p>Bitte um Angebot</p>', attachments: [],
    }];
    if (mitEntwurf) {
        emails.push({ id: 8, direction: 'OUT', isDraft: true, draftId: 99, snippet: 'Entwurf Antwort', sentAt: '2026-10-09T11:00:00', attachments: [] });
    }
    return { rootEmailId: 7, focusedEmailId: 7, emails };
}

function json(body: unknown, ok = true, status = ok ? 200 : 409) {
    return Promise.resolve({ ok, status, json: () => Promise.resolve(body) } as Response);
}

let loeschAntwort: () => Promise<Response>;
let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    loeschAntwort = () => json(null);
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
        if (url === '/api/emails/drafts/99' && init?.method === 'DELETE') return loeschAntwort();
        if (url === '/api/emails/7/thread') {
            const geloescht = fetchMock.mock.calls.some(c => c[0] === '/api/emails/drafts/99') && loeschAntwort !== fehler;
            return json(verlauf(!geloescht));
        }
        return json(null, false);
    });
    vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

const fehler = () => json({ message: 'Der Entwurf wird gerade gesendet.' }, false);
const loeschAufrufe = () => fetchMock.mock.calls.filter(c => c[0] === '/api/emails/drafts/99');
const threadAufrufe = () => fetchMock.mock.calls.filter(c => c[0] === '/api/emails/7/thread');

async function oeffneMitEntwurf() {
    render(<MemoryRouter><ToastProvider><EmailsTab emails={[mail]} projektId={3} /></ToastProvider></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Anfrage Geländer' }));
    expect(await screen.findByText('1 Nachricht im Verlauf, 1 Entwurf')).toBeInTheDocument();
    fireEvent.click(screen.getByTitle('Entwurf löschen'));
}

describe('EmailsTab – Entwurf löschen mit „Rückgängig“', () => {
    it('blendet den Entwurf sofort aus und löscht ihn erst nach Ablauf der Frist', async () => {
        await oeffneMitEntwurf();
        expect(screen.queryByText('Entwurf Antwort')).not.toBeInTheDocument();
        expect(screen.getByText('1 Nachricht im Verlauf')).toBeInTheDocument();
        expect(screen.getByText('Entwurf „Entwurf Antwort“ gelöscht.')).toBeInTheDocument();
        expect(loeschAufrufe()).toHaveLength(0);

        await act(async () => { await vi.advanceTimersByTimeAsync(RUECKGAENGIG_MS); });
        expect(loeschAufrufe()).toHaveLength(1);
        expect(loeschAufrufe()[0][1]).toEqual({ method: 'DELETE' });
        expect(threadAufrufe()).toHaveLength(2);
        expect(screen.queryByText('Entwurf Antwort')).not.toBeInTheDocument();
    });

    it('holt den Entwurf mit „Rückgängig“ zurück und löscht nichts', async () => {
        await oeffneMitEntwurf();
        fireEvent.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(screen.getByText('Entwurf Antwort')).toBeInTheDocument();
        expect(screen.getByText('1 Nachricht im Verlauf, 1 Entwurf')).toBeInTheDocument();

        await act(async () => { await vi.advanceTimersByTimeAsync(RUECKGAENGIG_MS * 2); });
        expect(loeschAufrufe()).toHaveLength(0);
    });

    it('zeigt die Meldung des Servers und den Entwurf wieder, wenn das Löschen scheitert', async () => {
        loeschAntwort = fehler;
        await oeffneMitEntwurf();
        await act(async () => { await vi.advanceTimersByTimeAsync(RUECKGAENGIG_MS); });
        expect(screen.getByText('Der Entwurf wird gerade gesendet.')).toBeInTheDocument();
        expect(screen.getByText('Entwurf Antwort')).toBeInTheDocument();
    });

    it('meldet einen Fehler auch, wenn der Verlauf schon geschlossen ist', async () => {
        loeschAntwort = fehler;
        await oeffneMitEntwurf();
        fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(await screen.findByText('Der Entwurf wird gerade gesendet.')).toBeInTheDocument();
    });

    it('löscht beim Schließen des Verlaufs sofort', async () => {
        await oeffneMitEntwurf();
        fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(loeschAufrufe()).toHaveLength(1);
        expect(loeschAufrufe()[0][1]).toEqual({ method: 'DELETE', keepalive: true });
        expect(screen.queryByRole('button', { name: 'Rückgängig' })).not.toBeInTheDocument();
    });
});
