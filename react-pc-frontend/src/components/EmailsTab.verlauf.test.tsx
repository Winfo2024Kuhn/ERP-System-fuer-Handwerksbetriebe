import { useEffect } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmailsTab from './EmailsTab';
import { istReiterUrl, jsonAntwort, reiterEintrag, reiterSeite } from '../features/email/emailsTab/emailsTabTestApi';
import type { EmailThread } from './EmailThreadView';
import { ToastProvider } from './ui/toast';

/**
 * Verhalten des E-Mail-Reiters rund um den geöffneten Verlauf: per Tastatur öffnen,
 * laden, antworten, schließen. Das Formular selbst ist ersetzt, es geht um die Verdrahtung.
 * Dummy-Daten (DSGVO): Max Mustermann, max@example.com.
 */
const formular = vi.hoisted(() => ({
    speichernGelingt: true, gespeichert: 0,
    /** Speichern hängt, bis der Test `freigeben()` ruft. */
    warten: false, freigeben: undefined as (() => void) | undefined,
}));
vi.mock('./EmailComposeForm', () => ({
    EmailComposeForm: (props: {
        initialSubject?: string; initialRecipient?: string; replyEmailId?: number;
        onSuccess: () => void; onClose: () => void;
        onBeforeLeave?: (save: (() => Promise<boolean>) | null) => void;
        kopf?: string; schliessenText?: string;
        onKopfDaten?: (daten: { betreff: string; gesperrt: string | null } | null) => void;
    }) => {
        const { onBeforeLeave } = props;
        useEffect(() => {
            onBeforeLeave?.(() => {
                formular.gespeichert++;
                if (!formular.warten) return Promise.resolve(formular.speichernGelingt);
                return new Promise<boolean>(resolve => { formular.freigeben = () => resolve(formular.speichernGelingt); });
            });
            return () => onBeforeLeave?.(null);
        }, [onBeforeLeave]);
        return (
            <div data-testid="antwort-formular" data-kopf={props.kopf} data-schliessen={props.schliessenText}>
                <span>{`${props.initialSubject}|${props.initialRecipient}|${props.replyEmailId}`}</span>
                <button onClick={props.onSuccess}>Formular gesendet</button>
                <button onClick={props.onClose}>Formular abbrechen</button>
                <button onClick={() => props.onKopfDaten?.({ betreff: 'AW: Anfrage Geländer – Termin', gesperrt: null })}>Betreff ändern</button>
                <button onClick={() => props.onKopfDaten?.({ betreff: props.initialSubject ?? '', gesperrt: 'Wird gesendet…' })}>Formular sendet</button>
            </div>
        );
    },
}));

const mail = reiterEintrag(7, {
    subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
    sentAt: '2026-10-09T10:05:00', bodyHtml: '<p>Bitte um Angebot</p>',
});

function verlauf(): EmailThread {
    return {
        rootEmailId: 7, focusedEmailId: 7, emails: [{
            id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
            direction: 'IN', sentAt: '2026-10-09T10:05:00', htmlBody: '<p>Bitte um Angebot</p>', attachments: [],
        }],
    };
}

function json(body: unknown, ok = true) {
    return jsonAntwort(body, ok ? 200 : 500);
}

/** Reiter-Liste mit der einen Mail; alles andere beantwortet `sonst`. */
function stubbe(sonst: (url: string) => Promise<Response>) {
    const fetchMock = vi.fn((url: string) => (istReiterUrl(url) ? jsonAntwort(reiterSeite([mail])) : sonst(url)));
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

afterEach(() => {
    vi.unstubAllGlobals();
    formular.speichernGelingt = true;
    formular.gespeichert = 0;
    formular.warten = false;
    formular.freigeben = undefined;
});

/** Öffnet den Verlauf und startet über den Kopfknopf eine Antwort. */
async function antwortOeffnen() {
    stubbe((url: string) => url.endsWith('/thread') ? json(verlauf()) : json(null, false));
    await rendere();
    fireEvent.click(zeile());
    fireEvent.click((await screen.findAllByRole('button', { name: /Antworten/ }))[0]);
    await screen.findByTestId('antwort-formular');
    return screen.getByRole('button', { name: 'Zurück zum Verlauf' });
}

async function rendere(onEmailSent = vi.fn()) {
    render(
        <MemoryRouter>
            <ToastProvider>
                <EmailsTab bezug="projekt" bezugId={3} projektId={3} onEmailSent={onEmailSent} />
            </ToastProvider>
        </MemoryRouter>
    );
    await screen.findByRole('button', { name: 'Anfrage Geländer' });
    return onEmailSent;
}

const zeile = () => screen.getByRole('button', { name: 'Anfrage Geländer' });

describe('EmailsTab – geöffneter Verlauf', () => {
    it('öffnet per Tastatur, antwortet und lädt danach Verlauf und Liste neu', async () => {
        const fetchMock = stubbe((url: string) => {
            if (url === '/api/emails/7/thread') return json(verlauf());
            if (url === '/api/emails/7') return json({ ...mail, subject: 'Anfrage Geländer' });
            return json(null, false);
        });
        const onEmailSent = await rendere();
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
        // Die Liste lädt ab Seite 1 neu (die Antwort ist jetzt die neueste Mail); der Dialog behält seinen Titel.
        await waitFor(() => expect(fetchMock.mock.calls.filter(c => istReiterUrl(c[0]))).toHaveLength(2));
        expect(fetchMock.mock.calls.filter(c => istReiterUrl(c[0])).at(-1)?.[0]).toBe('/api/emails/reiter/projekt/3?page=0&size=25');
        expect(screen.getByRole('dialog', { name: 'Anfrage Geländer' })).toBeInTheDocument();
    });

    it('bricht die Antwort ab und zeigt wieder den Verlauf', async () => {
        stubbe((url: string) => url.endsWith('/thread') ? json(verlauf()) : json(null, false));
        await rendere();
        fireEvent.click(zeile());
        fireEvent.click((await screen.findAllByRole('button', { name: /Antworten/ }))[0]);
        fireEvent.click(await screen.findByText('Formular abbrechen'));
        expect(screen.queryByTestId('antwort-formular')).not.toBeInTheDocument();
        expect(screen.getByText('1 Nachricht im Verlauf')).toBeInTheDocument();
    });

    it('schließt per Escape und gibt den Fokus an die Zeile zurück', async () => {
        stubbe(() => json(verlauf()));
        await rendere();
        const nutzer = userEvent.setup();
        zeile().focus();
        await nutzer.keyboard('{Enter}');
        await screen.findByText('1 Nachricht im Verlauf');

        await nutzer.keyboard('{Escape}');
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        await waitFor(() => expect(zeile()).toHaveFocus());
    });

    it('speichert beim Schließen erst die angefangene Antwort und bleibt offen, wenn das scheitert', async () => {
        stubbe((url: string) => url.endsWith('/thread') ? json(verlauf()) : json(null, false));
        await rendere();
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
        stubbe((url: string) => url.endsWith('/thread') ? json(verlauf()) : json(null, false));
        await rendere();
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
        stubbe((url: string) => url.endsWith('/thread') ? json(verlauf()) : json(null, false));
        await rendere();
        fireEvent.click(zeile());
        fireEvent.click((await screen.findAllByRole('button', { name: /Antworten/ }))[0]);
        expect(await screen.findByTestId('antwort-formular')).toHaveAttribute('data-kopf', 'ohne');
        expect(screen.getByTestId('antwort-formular')).toHaveAttribute('data-schliessen', 'Zurück zum Verlauf');
    });

    it('setzt den Fokus beim Antworten auf den Pfeil und beim Zurück auf „Antworten“', async () => {
        const pfeil = await antwortOeffnen();
        await waitFor(() => expect(document.activeElement).toBe(pfeil));

        fireEvent.click(pfeil);
        await waitFor(() => expect(screen.queryByTestId('antwort-formular')).not.toBeInTheDocument());
        await waitFor(() => expect(document.activeElement).toBe(screen.getAllByRole('button', { name: 'Antworten' })[0]));

        // Antwort über den Knopf an der Nachricht, zurück über den Fußknopf des Formulars
        fireEvent.click(screen.getAllByRole('button', { name: 'Antworten' })[1]);
        await screen.findByTestId('antwort-formular');
        await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Zurück zum Verlauf' })));
        fireEvent.click(screen.getByText('Formular abbrechen'));
        await waitFor(() => expect(document.activeElement).toBe(screen.getAllByRole('button', { name: 'Antworten' })[0]));
    });

    it('speichert bei Doppelklick auf den Pfeil nur einmal', async () => {
        const pfeil = await antwortOeffnen();
        formular.warten = true;
        fireEvent.click(pfeil);
        fireEvent.click(pfeil);
        expect(formular.gespeichert).toBe(1);
        formular.freigeben?.();
        await waitFor(() => expect(screen.queryByTestId('antwort-formular')).not.toBeInTheDocument());
        expect(formular.gespeichert).toBe(1);
    });

    it('speichert nur einmal, wenn während des Speicherns Escape kommt', async () => {
        const pfeil = await antwortOeffnen();
        formular.warten = true;
        fireEvent.click(pfeil);
        fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });
        expect(formular.gespeichert).toBe(1);
        formular.freigeben?.();
        await waitFor(() => expect(screen.queryByTestId('antwort-formular')).not.toBeInTheDocument());
        expect(formular.gespeichert).toBe(1);
        expect(screen.getByRole('dialog', { name: 'Anfrage Geländer' })).toBeInTheDocument();
    });

    it('sperrt den Pfeil mit Grund, solange das Formular sendet', async () => {
        const pfeil = await antwortOeffnen();
        fireEvent.click(screen.getByText('Formular sendet'));
        expect(pfeil).toBeDisabled();
        expect(pfeil).toHaveAttribute('aria-busy', 'true');
        expect(pfeil).toHaveAttribute('title', 'Wird gesendet…');
        fireEvent.click(pfeil);
        expect(formular.gespeichert).toBe(0);
        expect(screen.getByTestId('antwort-formular')).toBeInTheDocument();
    });

    it('zeigt im Kopf den Betreff, den das Formular gerade hat', async () => {
        await antwortOeffnen();
        fireEvent.click(screen.getByText('Betreff ändern'));
        expect(screen.getByRole('dialog', { name: 'Antwort' })).toHaveAccessibleDescription('AW: Anfrage Geländer – Termin');
    });

    it('lädt einen Verlauf nach einem Fehler per „Erneut laden“', async () => {
        let versuch = 0;
        stubbe(() => (++versuch === 1 ? json(null, false) : json(verlauf())));
        await rendere();
        fireEvent.click(zeile());
        fireEvent.click(await screen.findByRole('button', { name: 'Erneut laden' }));
        expect(await screen.findByText('1 Nachricht im Verlauf')).toBeInTheDocument();
        expect(screen.queryByText('Der Verlauf konnte nicht geladen werden.')).not.toBeInTheDocument();
    });

    it('sperrt „Antworten“ mit Hinweis, solange die Mail geladen wird', async () => {
        stubbe((url: string) => url.endsWith('/thread') ? json(verlauf()) : new Promise<Response>(() => {}));
        await rendere();
        fireEvent.click(zeile());
        const antworten = (await screen.findAllByRole('button', { name: /Antworten/ }))[0];
        fireEvent.click(antworten);
        expect(antworten).toBeDisabled();
        expect(antworten).toHaveAttribute('aria-busy', 'true');
        expect(antworten).toHaveAttribute('title', 'Mail wird geladen…');
    });

    it('meldet einen Verlauf, der nicht geladen werden kann, und schließt per Knopf', async () => {
        stubbe(() => json(null, false));
        await rendere();
        fireEvent.click(zeile());
        expect(await screen.findByText('Der Verlauf konnte nicht geladen werden.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Zurück/ })).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Fenster schließen' }));
        expect(screen.queryByText('Der Verlauf konnte nicht geladen werden.')).not.toBeInTheDocument();
    });
});
