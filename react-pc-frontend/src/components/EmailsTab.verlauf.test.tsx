import { useEffect } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmailsTab, { type GenericEmail } from './EmailsTab';
import type { EmailThread } from './EmailThreadView';
import { ToastProvider } from './ui/toast';

/**
 * Verhalten des E-Mail-Reiters rund um den geöffneten Verlauf: per Tastatur öffnen,
 * laden, antworten, schließen. Das Formular selbst ist ersetzt, es geht um die Verdrahtung.
 * Dummy-Daten (DSGVO): Max Mustermann, max@example.com.
 */
const formular = vi.hoisted(() => ({ speichernGelingt: true, gespeichert: 0 }));
vi.mock('./EmailComposeForm', () => ({
    EmailComposeForm: (props: {
        initialSubject?: string; initialRecipient?: string; replyEmailId?: number;
        onSuccess: () => void; onClose: () => void;
        onBeforeLeave?: (save: (() => Promise<boolean>) | null) => void;
        kopf?: string; schliessenText?: string;
    }) => {
        const { onBeforeLeave } = props;
        useEffect(() => {
            onBeforeLeave?.(() => { formular.gespeichert++; return Promise.resolve(formular.speichernGelingt); });
            return () => onBeforeLeave?.(null);
        }, [onBeforeLeave]);
        return (
            <div data-testid="antwort-formular" data-kopf={props.kopf} data-schliessen={props.schliessenText}>
                <span>{`${props.initialSubject}|${props.initialRecipient}|${props.replyEmailId}`}</span>
                <button onClick={props.onSuccess}>Formular gesendet</button>
                <button onClick={props.onClose}>Formular abbrechen</button>
            </div>
        );
    },
}));

const mail: GenericEmail = {
    id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
    direction: 'IN', sentAt: '2026-10-09T10:05:00', bodyHtml: '<p>Bitte um Angebot</p>',
};

function verlauf(): EmailThread {
    return {
        rootEmailId: 7, focusedEmailId: 7, emails: [{
            id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
            direction: 'IN', sentAt: '2026-10-09T10:05:00', htmlBody: '<p>Bitte um Angebot</p>', attachments: [],
        }],
    };
}

function json(body: unknown, ok = true) {
    return Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);
}

afterEach(() => {
    vi.unstubAllGlobals();
    formular.speichernGelingt = true;
    formular.gespeichert = 0;
});

function rendere(onEmailSent = vi.fn()) {
    render(
        <MemoryRouter>
            <ToastProvider>
                <EmailsTab emails={[mail]} projektId={3} onEmailSent={onEmailSent} />
            </ToastProvider>
        </MemoryRouter>
    );
    return onEmailSent;
}

const zeile = () => screen.getByRole('button', { name: 'Anfrage Geländer' });

describe('EmailsTab – geöffneter Verlauf', () => {
    it('öffnet per Tastatur, antwortet und lädt danach neu', async () => {
        const fetchMock = vi.fn((url: string) => {
            if (url === '/api/emails/7/thread') return json(verlauf());
            if (url === '/api/emails/7') return json({ ...mail, subject: 'Anfrage Geländer' });
            return json(null, false);
        });
        vi.stubGlobal('fetch', fetchMock);
        const onEmailSent = rendere();
        const nutzer = userEvent.setup();

        zeile().focus();
        await nutzer.keyboard('{Enter}');
        const dialog = await screen.findByRole('dialog', { name: 'Anfrage Geländer' });
        expect(await screen.findByText('1 Nachricht im Verlauf')).toBeInTheDocument();
        expect(dialog).toHaveAccessibleDescription('1 Nachricht im Verlauf');

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
        fireEvent.click(zeile());
        fireEvent.click((await screen.findAllByRole('button', { name: /Antworten/ }))[0]);
        fireEvent.click(await screen.findByText('Formular abbrechen'));
        expect(screen.queryByTestId('antwort-formular')).not.toBeInTheDocument();
        expect(screen.getByText('1 Nachricht im Verlauf')).toBeInTheDocument();
    });

    it('schließt per Escape und gibt den Fokus an die Zeile zurück', async () => {
        vi.stubGlobal('fetch', vi.fn(() => json(verlauf())));
        rendere();
        const nutzer = userEvent.setup();
        zeile().focus();
        await nutzer.keyboard('{Enter}');
        await screen.findByText('1 Nachricht im Verlauf');

        await nutzer.keyboard('{Escape}');
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        await waitFor(() => expect(zeile()).toHaveFocus());
    });

    it('speichert beim Schließen erst die angefangene Antwort und bleibt offen, wenn das scheitert', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => url.endsWith('/thread') ? json(verlauf()) : json(null, false)));
        rendere();
        const nutzer = userEvent.setup();
        fireEvent.click(zeile());
        fireEvent.click((await screen.findAllByRole('button', { name: /Antworten/ }))[0]);
        await screen.findByTestId('antwort-formular');

        formular.speichernGelingt = false;
        await nutzer.keyboard('{Escape}');
        expect(formular.gespeichert).toBe(1);
        expect(screen.getByRole('dialog')).toBeInTheDocument();

        formular.speichernGelingt = true;
        fireEvent.click(screen.getByRole('button', { name: 'Fenster schließen' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(formular.gespeichert).toBe(2);
    });

    it('zeigt beim Antworten einen Kopf „Antwort“ mit Betreff, genau ein X und führt per Pfeil zurück', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => url.endsWith('/thread') ? json(verlauf()) : json(null, false)));
        rendere();
        fireEvent.click(zeile());
        fireEvent.click((await screen.findAllByRole('button', { name: /Antworten/ }))[0]);
        await screen.findByTestId('antwort-formular');

        const dialog = screen.getByRole('dialog', { name: 'Antwort' });
        expect(dialog).toHaveAccessibleDescription('AW: Anfrage Geländer');
        expect(screen.getAllByRole('button', { name: 'Fenster schließen' })).toHaveLength(1);
        const zurueck = screen.getByRole('button', { name: 'Zurück zum Verlauf' });
        expect(zurueck).toHaveAttribute('title', 'Zurück zum Verlauf');

        // Speichern scheitert: die Antwort bleibt offen
        formular.speichernGelingt = false;
        fireEvent.click(zurueck);
        await waitFor(() => expect(formular.gespeichert).toBe(1));
        expect(screen.getByTestId('antwort-formular')).toBeInTheDocument();

        formular.speichernGelingt = true;
        fireEvent.click(zurueck);
        await waitFor(() => expect(screen.queryByTestId('antwort-formular')).not.toBeInTheDocument());
        expect(formular.gespeichert).toBe(2);
        expect(screen.getByRole('dialog', { name: 'Anfrage Geländer' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Zurück zum Verlauf' })).not.toBeInTheDocument();
    });

    it('gibt dem Formular im Verlauf keinen eigenen Kopf und „Zurück zum Verlauf“ als Knopf', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => url.endsWith('/thread') ? json(verlauf()) : json(null, false)));
        rendere();
        fireEvent.click(zeile());
        fireEvent.click((await screen.findAllByRole('button', { name: /Antworten/ }))[0]);
        expect(await screen.findByTestId('antwort-formular')).toHaveAttribute('data-kopf', 'ohne');
        expect(screen.getByTestId('antwort-formular')).toHaveAttribute('data-schliessen', 'Zurück zum Verlauf');
    });

    it('lädt einen Verlauf nach einem Fehler per „Erneut laden“', async () => {
        let versuch = 0;
        vi.stubGlobal('fetch', vi.fn(() => (++versuch === 1 ? json(null, false) : json(verlauf()))));
        rendere();
        fireEvent.click(zeile());
        fireEvent.click(await screen.findByRole('button', { name: 'Erneut laden' }));
        expect(await screen.findByText('1 Nachricht im Verlauf')).toBeInTheDocument();
        expect(screen.queryByText('Der Verlauf konnte nicht geladen werden.')).not.toBeInTheDocument();
    });

    it('sperrt „Antworten“ mit Hinweis, solange die Mail geladen wird', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => url.endsWith('/thread') ? json(verlauf()) : new Promise<Response>(() => {})));
        rendere();
        fireEvent.click(zeile());
        const antworten = (await screen.findAllByRole('button', { name: /Antworten/ }))[0];
        fireEvent.click(antworten);
        expect(antworten).toBeDisabled();
        expect(antworten).toHaveAttribute('aria-busy', 'true');
        expect(antworten).toHaveAttribute('title', 'Mail wird geladen…');
    });

    it('meldet einen Verlauf, der nicht geladen werden kann, und schließt per Knopf', async () => {
        vi.stubGlobal('fetch', vi.fn(() => json(null, false)));
        rendere();
        fireEvent.click(zeile());
        expect(await screen.findByText('Der Verlauf konnte nicht geladen werden.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Zurück/ })).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Fenster schließen' }));
        expect(screen.queryByText('Der Verlauf konnte nicht geladen werden.')).not.toBeInTheDocument();
    });

    it('filtert nach Ausgang und meldet „Keine E-Mails gefunden.“', () => {
        rendere();
        fireEvent.click(screen.getByRole('radio', { name: 'Ausgang' }));
        expect(screen.getByText('Keine E-Mails gefunden.')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('radio', { name: 'Alle' }));
        fireEvent.change(screen.getByRole('textbox', { name: 'E-Mails durchsuchen' }), { target: { value: 'geländer' } });
        expect(zeile()).toBeInTheDocument();
    });
});
