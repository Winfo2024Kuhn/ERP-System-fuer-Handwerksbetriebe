import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmailsTab, { type GenericEmail } from './EmailsTab';
import type { EmailThread } from './EmailThreadView';

/**
 * Verhalten des E-Mail-Reiters rund um den geöffneten Verlauf: laden, antworten,
 * Entwurf löschen, schließen. Das Formular selbst ist ersetzt, es geht um die Verdrahtung.
 * Dummy-Daten (DSGVO): Max Mustermann, max@example.com.
 */
vi.mock('./EmailComposeForm', () => ({
    EmailComposeForm: (props: {
        initialSubject?: string; initialRecipient?: string; replyEmailId?: number;
        onSuccess: () => void; onClose: () => void;
    }) => (
        <div data-testid="antwort-formular">
            <span>{`${props.initialSubject}|${props.initialRecipient}|${props.replyEmailId}`}</span>
            <button onClick={props.onSuccess}>Formular gesendet</button>
            <button onClick={props.onClose}>Formular abbrechen</button>
        </div>
    ),
}));

const mail: GenericEmail = {
    id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
    direction: 'IN', sentAt: '2026-10-09T10:05:00', bodyHtml: '<p>Bitte um Angebot</p>',
};

function verlauf(mitEntwurf = false): EmailThread {
    const emails: EmailThread['emails'] = [{
        id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
        direction: 'IN', sentAt: '2026-10-09T10:05:00', htmlBody: '<p>Bitte um Angebot</p>', attachments: [],
    }];
    if (mitEntwurf) {
        emails.push({ id: 8, direction: 'OUT', isDraft: true, draftId: 99, snippet: 'Entwurf Antwort', sentAt: '2026-10-09T11:00:00', attachments: [] });
    }
    return { rootEmailId: 7, focusedEmailId: 7, emails };
}

function json(body: unknown, ok = true) {
    return Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);
}

afterEach(() => vi.unstubAllGlobals());

function rendere(onEmailSent = vi.fn()) {
    render(
        <MemoryRouter>
            <EmailsTab emails={[mail]} projektId={3} onEmailSent={onEmailSent} />
        </MemoryRouter>
    );
    return onEmailSent;
}

describe('EmailsTab – geöffneter Verlauf', () => {
    it('lädt den Verlauf, antwortet und lädt danach neu', async () => {
        const fetchMock = vi.fn((url: string) => {
            if (url === '/api/emails/7/thread') return json(verlauf());
            if (url === '/api/emails/7') return json({ ...mail, subject: 'Anfrage Geländer' });
            return json(null, false);
        });
        vi.stubGlobal('fetch', fetchMock);
        const onEmailSent = rendere();

        fireEvent.click(screen.getByText('Anfrage Geländer'));
        expect(await screen.findByText('1 Nachricht im Thread')).toBeInTheDocument();

        // Erster Knopf „Antworten“ ist der im Kopf des Verlaufs (antwortet auf die letzte Nachricht)
        fireEvent.click(screen.getAllByRole('button', { name: /Antworten/ })[0]);
        expect(await screen.findByTestId('antwort-formular'))
            .toHaveTextContent(/^AW: Anfrage Geländer\|.*max@example\.com.*\|7/);
        expect(screen.queryAllByRole('button', { name: /Antworten/ })).toHaveLength(0);

        fireEvent.click(screen.getByText('Formular gesendet'));
        await waitFor(() => expect(screen.queryByTestId('antwort-formular')).not.toBeInTheDocument());
        expect(onEmailSent).toHaveBeenCalledTimes(1);
        await waitFor(() => expect(fetchMock.mock.calls.filter(c => c[0] === '/api/emails/7/thread')).toHaveLength(2));
    });

    it('bricht die Antwort ab und zeigt wieder den Verlauf', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => url.endsWith('/thread') ? json(verlauf()) : json(null, false)));
        rendere();
        fireEvent.click(screen.getByText('Anfrage Geländer'));
        fireEvent.click((await screen.findAllByRole('button', { name: /Antworten/ }))[0]);
        fireEvent.click(await screen.findByText('Formular abbrechen'));
        expect(screen.queryByTestId('antwort-formular')).not.toBeInTheDocument();
        expect(screen.getByText('1 Nachricht im Thread')).toBeInTheDocument();
    });

    it('löscht einen Entwurf und lädt den Verlauf neu', async () => {
        const fetchMock = vi.fn((url: string, init?: RequestInit) => {
            if (url === '/api/emails/drafts/99' && init?.method === 'DELETE') return json(null);
            if (url === '/api/emails/7/thread') {
                const schonGeloescht = fetchMock.mock.calls.some(c => c[0] === '/api/emails/drafts/99');
                return json(verlauf(!schonGeloescht));
            }
            return json(null, false);
        });
        vi.stubGlobal('fetch', fetchMock);
        rendere();
        fireEvent.click(screen.getByText('Anfrage Geländer'));
        expect(await screen.findByText('2 Nachrichten im Thread')).toBeInTheDocument();

        fireEvent.click(screen.getByTitle('Entwurf löschen'));
        expect(await screen.findByText('1 Nachricht im Thread')).toBeInTheDocument();
        expect(fetchMock).toHaveBeenCalledWith('/api/emails/drafts/99', { method: 'DELETE' });
    });

    it('meldet einen Verlauf, der nicht geladen werden kann, und schließt per Zurück', async () => {
        vi.stubGlobal('fetch', vi.fn(() => json(null, false)));
        rendere();
        fireEvent.click(screen.getByText('Anfrage Geländer'));
        expect(await screen.findByText('Thread konnte nicht geladen werden.')).toBeInTheDocument();

        // Zurück-Pfeil ist der erste Knopf im Kopf des Verlaufs
        const kopf = screen.getByText('Thread konnte nicht geladen werden.').closest('.rounded-2xl')!;
        fireEvent.click(kopf.querySelector('button')!);
        expect(screen.queryByText('Thread konnte nicht geladen werden.')).not.toBeInTheDocument();
    });

    it('filtert nach Ausgang und meldet „Keine E-Mails gefunden.“', () => {
        rendere();
        fireEvent.click(screen.getByRole('button', { name: 'Ausgang' }));
        expect(screen.getByText('Keine E-Mails gefunden.')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Alle' }));
        fireEvent.change(screen.getByPlaceholderText('E-Mails durchsuchen...'), { target: { value: 'geländer' } });
        expect(screen.getByText('Anfrage Geländer')).toBeInTheDocument();
    });
});
