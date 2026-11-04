import { render as rtlRender, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from './ui/toast';
import { EmailComposeForm } from './EmailComposeForm';

/**
 * Kopf und Fußleiste des Formulars: richtiger Titel für Antwort, Weiterleitung und
 * wiederaufgenommenen Entwurf; ohne eigenen Kopf, wenn ein Dialog ihn stellt.
 * Dummy-Daten (DSGVO): Max Mustermann.
 */

const render = (ui: ReactElement) => rtlRender(<ToastProvider>{ui}</ToastProvider>);
const POSTFACH = { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' };

function json(body: unknown, status = 200) {
    return new Response(status === 204 ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function stubFetch(entwurf: Record<string, unknown> = {}) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url === '/api/emails/absender-postfaecher') return json([{ ...POSTFACH, eigenes: true, hauptpostfach: true }]);
        if (url.startsWith('/api/email/signatures/default')) return json(null, 204);
        if (url === '/api/emails/drafts/55' && !init?.method) return json({ id: 55, attachments: [], ...entwurf });
        if (url.startsWith('/api/emails/drafts')) return json({ id: 55 });
        if (/^\/api\/emails\/\d+$/.test(url)) return json({ id: 5, antwortPostfach: POSTFACH, attachments: [] });
        return json([]);
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('EmailComposeForm – Kopf', () => {
    it('heißt bei einer Antwort „Antwort“', () => {
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} replyEmailId={5} initialSubject="AW: Angebot Treppengeländer"
            initialRecipient="max@example.com" antwortPostfach={POSTFACH} />);
        expect(screen.getByRole('heading', { name: 'Antwort' })).toBeInTheDocument();
        expect(screen.queryByText('Neue E-Mail')).not.toBeInTheDocument();
    });

    it('heißt bei einer Weiterleitung „Weiterleitung“', () => {
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} weitergeleitetVonEmailId={5} initialSubject="WG: Angebot Treppengeländer"
            antwortPostfach={POSTFACH} weiterleitungsAnhaenge={[]} />);
        expect(screen.getByRole('heading', { name: 'Weiterleitung' })).toBeInTheDocument();
    });

    it('heißt bei einem wiederaufgenommenen Antwort-Entwurf „Antwort“', async () => {
        stubFetch({ recipient: 'max@example.com', subject: 'AW: Angebot Treppengeländer', body: '<p>x</p>', replyEmailId: 5 });
        render(<EmailComposeForm onClose={() => {}} draftId={55} />);
        expect(await screen.findByRole('heading', { name: 'Antwort' })).toBeInTheDocument();
    });

    it('heißt sonst „Neue E-Mail“ und hat ein X mit Tooltip', () => {
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} />);
        expect(screen.getByRole('heading', { name: 'Neue E-Mail' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Fenster schließen' })).toHaveAttribute('title', 'Schließen');
        expect(screen.getByRole('button', { name: 'Schließen' })).toBeInTheDocument();
    });

    it('lässt den Kopf weg und führt mit „Zurück zum Verlauf“ nach dem Speichern zurück', async () => {
        const fetchMock = stubFetch();
        const onClose = vi.fn();
        render(<EmailComposeForm onClose={onClose} replyEmailId={5} initialSubject="AW: Angebot Treppengeländer"
            initialRecipient="max@example.com" antwortPostfach={POSTFACH} kopf="ohne" schliessenText="Zurück zum Verlauf" />);
        expect(screen.queryByRole('heading')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Fenster schließen' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Schließen' })).not.toBeInTheDocument();

        await userEvent.type(screen.getByRole('textbox', { name: 'Betreff' }), ' heute');
        await userEvent.click(screen.getByRole('button', { name: 'Zurück zum Verlauf' }));
        await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
        expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/emails/drafts' && init?.method === 'POST')).toBe(true);
    });

    it('meldet ohne eigenen Kopf den aktuellen Betreff und die Sperre beim Speichern nach oben', async () => {
        let entwurfFreigeben: (() => void) | undefined;
        stubFetch();
        const vorher = globalThis.fetch;
        vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
            if (String(input) === '/api/emails/drafts' && init?.method === 'POST') {
                return new Promise<Response>(resolve => { entwurfFreigeben = () => resolve(json({ id: 55 })); });
            }
            return vorher(input, init);
        }));
        const onKopfDaten = vi.fn();
        const onClose = vi.fn();
        const { unmount } = render(<EmailComposeForm onClose={onClose} replyEmailId={5} initialSubject="AW: Angebot Treppengeländer"
            initialRecipient="max@example.com" antwortPostfach={POSTFACH} kopf="ohne" onKopfDaten={onKopfDaten} />);
        expect(onKopfDaten).toHaveBeenLastCalledWith({ betreff: 'AW: Angebot Treppengeländer', gesperrt: null });

        await userEvent.type(screen.getByRole('textbox', { name: 'Betreff' }), ' heute');
        expect(onKopfDaten).toHaveBeenLastCalledWith({ betreff: 'AW: Angebot Treppengeländer heute', gesperrt: null });

        await userEvent.click(screen.getByRole('button', { name: 'Schließen' }));
        await waitFor(() => expect(onKopfDaten).toHaveBeenLastCalledWith({ betreff: 'AW: Angebot Treppengeländer heute', gesperrt: 'Wird gespeichert…' }));
        entwurfFreigeben?.();
        await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));

        onKopfDaten.mockClear();
        unmount();
        expect(onKopfDaten).toHaveBeenCalledWith(null);
    });
});
