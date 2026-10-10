import { render as rtlRender, screen, waitFor, within } from '@testing-library/react';
import type { ReactElement } from 'react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from './ui/toast';
import { EmailComposeForm } from './EmailComposeForm';

/**
 * Absender-Postfach und Einzelversand im Schreiben-Fenster.
 * Alle Daten sind Dummy-Daten (DSGVO).
 */

const render = (ui: ReactElement) => rtlRender(<ToastProvider>{ui}</ToastProvider>);

const INFO = { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', eigenes: false, hauptpostfach: true };
const MAX = { id: 7, emailAdresse: 'max@musterbetrieb.example', anzeigename: 'Max Mustermann', eigenes: true, hauptpostfach: false };
const RECHNUNGEN = { id: 9, emailAdresse: 'rechnungen@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: false };

type Antwort = { status?: number; body?: unknown };
let absenderAntwort: Antwort;
let sendeAntwort: Antwort;
let originalAntwort: Antwort;
let entwurfAntwort: Antwort;
let gesendet: { url: string; dto: Record<string, unknown> }[];

function json(antwort: Antwort) {
    const status = antwort.status ?? 200;
    return new Response(status === 204 ? null : JSON.stringify(antwort.body ?? {}), {
        status, headers: { 'Content-Type': 'application/json' },
    });
}

async function dtoAus(body: unknown): Promise<Record<string, unknown>> {
    const teil = (body as FormData).get('dto') as Blob;
    return JSON.parse(await teil.text());
}

function stubFetch() {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url === '/api/emails/absender-postfaecher') return json(absenderAntwort);
        if (url.startsWith('/api/email/signatures/default')) return new Response(null, { status: 204 });
        if (url === '/api/emails/send' || /^\/api\/emails\/\d+\/reply$/.test(url)) {
            gesendet.push({ url, dto: await dtoAus(init?.body) });
            return json(sendeAntwort);
        }
        if (url === '/api/emails/drafts/55' && !init?.method) return json(entwurfAntwort);
        if (url.startsWith('/api/emails/drafts')) return json({ body: { id: 55 } });
        if (/^\/api\/emails\/\d+$/.test(url)) return json(originalAntwort);
        if (url.startsWith('/api/email/dokument-absender')) return json({ body: { aktiv: false, address: null } });
        return json({ body: [] });
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

beforeEach(() => {
    absenderAntwort = { body: [MAX, INFO, RECHNUNGEN] };
    sendeAntwort = { body: { id: 999 } };
    originalAntwort = { body: { id: 5, antwortPostfach: { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' } } };
    entwurfAntwort = { body: {} };
    gesendet = [];
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

async function sende() {
    await userEvent.click(screen.getByRole('button', { name: /E-Mail senden/ }));
}

describe('EmailComposeForm – Senden von (neue Mail)', () => {
    it('belegt mit dem eigenen Postfach vor und schickt das gewählte postfachId mit', async () => {
        stubFetch();
        const onSuccess = vi.fn();
        render(<EmailComposeForm onClose={() => {}} onSuccess={onSuccess} initialRecipient="kunde@example.org" initialSubject="Angebot Treppe" />);

        const auswahl = await screen.findByRole('combobox', { name: 'Senden von' });
        await waitFor(() => expect(auswahl).toHaveTextContent('Max Mustermann <max@musterbetrieb.example> – Ihr Postfach'));
        await userEvent.click(auswahl);
        await userEvent.click(screen.getByRole('option', { name: 'rechnungen@musterbetrieb.example' }));
        await sende();

        await waitFor(() => expect(gesendet).toHaveLength(1));
        expect(gesendet[0].url).toBe('/api/emails/send');
        expect(gesendet[0].dto).toMatchObject({ postfachId: 9, einzelversand: false, weitergeleitetVonEmailId: null, recipients: ['kunde@example.org'] });
        expect(gesendet[0].dto).not.toHaveProperty('sender');
        await waitFor(() => expect(onSuccess).toHaveBeenCalled());
    });

    it('nimmt ohne eigenes Postfach das Hauptpostfach', async () => {
        absenderAntwort = { body: [RECHNUNGEN, INFO] };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} initialRecipient="kunde@example.org" initialSubject="Termin" />);
        await waitFor(() => expect(screen.getByRole('combobox', { name: 'Senden von' })).toHaveTextContent('info@musterbetrieb.example'));
        await sende();
        await waitFor(() => expect(gesendet[0]?.dto).toMatchObject({ postfachId: 3 }));
    });

    it('schickt ohne Postfächer kein postfachId und erklärt das', async () => {
        absenderAntwort = { status: 500, body: { message: 'kaputt' } };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} initialRecipient="kunde@example.org" initialSubject="Termin" />);
        expect(await screen.findByText(/Postfächer konnten nicht geladen werden/)).toBeInTheDocument();
        await sende();
        await waitFor(() => expect(gesendet[0]?.dto).toMatchObject({ postfachId: null }));
    });

    it('zeigt die 400-Meldung des Servers beim Senden', async () => {
        sendeAntwort = { status: 400, body: { message: 'Dieses Postfach ist ausgeschaltet.' } };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} initialRecipient="kunde@example.org" initialSubject="Termin" />);
        await screen.findByRole('combobox', { name: 'Senden von' });
        await sende();
        expect((await screen.findAllByText('Dieses Postfach ist ausgeschaltet.')).length).toBeGreaterThan(0);
    });

    it('meldet Netzwerkfehler mit eigenem Text', async () => {
        stubFetch();
        const fetchMock = vi.mocked(fetch);
        const original = fetchMock.getMockImplementation()!;
        fetchMock.mockImplementation(async (input, init) => {
            if (String(input) === '/api/emails/send') throw new TypeError('Failed to fetch');
            return original(input, init);
        });
        render(<EmailComposeForm onClose={() => {}} initialRecipient="kunde@example.org" initialSubject="Termin" />);
        await screen.findByRole('combobox', { name: 'Senden von' });
        await sende();
        expect((await screen.findAllByText('E-Mail konnte nicht gesendet werden. Bitte erneut versuchen.')).length).toBeGreaterThan(0);
    });
});

describe('EmailComposeForm – fester Absender bei Antwort und Weiterleitung', () => {
    it('zeigt bei Antworten das mitgegebene Antwort-Postfach als Text, ohne Auswahl', async () => {
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} replyEmailId={5} initialRecipient="kunde@example.org" initialSubject="Re: Treppe"
            antwortPostfach={{ id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' }} />);

        expect(await screen.findByTestId('absender-fest')).toHaveTextContent('Musterbetrieb <info@musterbetrieb.example>');
        expect(screen.getByText('Antworten gehen über das Postfach raus, in dem die Mail ankam – sonst über Ihr eigenes Postfach oder das Hauptpostfach.')).toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Senden von' })).not.toBeInTheDocument();
        // Antworten können nicht einzeln verschickt werden.
        expect(screen.queryByLabelText(/Einzeln verschicken/)).not.toBeInTheDocument();
        await sende();
        await waitFor(() => expect(gesendet[0]?.url).toBe('/api/emails/5/reply'));
        expect(gesendet[0].dto).toMatchObject({ postfachId: null, einzelversand: false });
    });

    it('holt das Antwort-Postfach selbst, wenn der Aufrufer es nicht kennt', async () => {
        const fetchMock = stubFetch();
        render(<EmailComposeForm onClose={() => {}} replyEmailId={5} initialRecipient="kunde@example.org" initialSubject="Re: Treppe" />);
        expect(await screen.findByText('Musterbetrieb <info@musterbetrieb.example>')).toBeInTheDocument();
        expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/emails/5')).toBe(true);
    });

    it('fällt auf einen Klartext zurück, wenn die Original-Mail nicht ladbar ist', async () => {
        originalAntwort = { status: 404, body: {} };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} replyEmailId={5} initialRecipient="kunde@example.org" initialSubject="Re: Treppe" />);
        expect(await screen.findByText('Postfach, in dem die Mail ankam')).toBeInTheDocument();
    });

    it('nimmt bei fehlendem Antwort-Postfach den Standard-Absender', async () => {
        originalAntwort = { body: { id: 5, antwortPostfach: null } };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} replyEmailId={5} initialRecipient="kunde@example.org" initialSubject="Re: Treppe" />);
        expect(await screen.findByText('Standard-Absender des Betriebs')).toBeInTheDocument();
    });

    it('schickt beim Weiterleiten weitergeleitetVonEmailId und zeigt den festen Absender', async () => {
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} weitergeleitetVonEmailId={5} initialSubject="Fwd: Treppe"
            antwortPostfach={{ id: 7, emailAdresse: 'max@musterbetrieb.example', anzeigename: null }} />);
        expect(await screen.findByTestId('absender-fest')).toHaveTextContent('max@musterbetrieb.example');
        expect(screen.getByText('Weiterleitungen gehen über das Postfach raus, in dem die Mail ankam – sonst über Ihr eigenes Postfach oder das Hauptpostfach.')).toBeInTheDocument();
        await userEvent.type(screen.getByPlaceholderText('Name, Firma oder E-Mail eingeben'), 'kollege@example.org');
        await sende();
        await waitFor(() => expect(gesendet[0]?.url).toBe('/api/emails/send'));
        expect(gesendet[0].dto).toMatchObject({ weitergeleitetVonEmailId: 5, postfachId: null });
    });

    it('lädt beim Antwort-Entwurf das Postfach der Original-Mail nach', async () => {
        entwurfAntwort = { body: { id: 55, recipient: 'kunde@example.org', subject: 'Re: Treppe', body: '<p>x</p>', replyEmailId: 5, attachments: [] } };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} draftId={55} />);
        expect(await screen.findByText('Musterbetrieb <info@musterbetrieb.example>')).toBeInTheDocument();
    });
});

describe('EmailComposeForm – Entwürfe', () => {
    it('stellt Postfach und Einzelversand eines Entwurfs wieder her und verwirft dessen CC', async () => {
        entwurfAntwort = { body: { id: 55, recipient: 'a@example.org, b@example.org', cc: 'c@example.org', subject: 'Rundschreiben',
            body: '<p>x</p>', postfachId: 3, einzelversand: true, attachments: [] } };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} draftId={55} />);
        expect(await screen.findByLabelText(/Einzeln verschicken/)).toBeChecked();
        await waitFor(() => expect(screen.getByRole('combobox', { name: 'Senden von' })).toHaveTextContent('info@musterbetrieb.example'));
        expect(screen.queryByText('CC')).not.toBeInTheDocument();
    });

    it('speichert postfachId und einzelversand im Entwurf', async () => {
        const fetchMock = stubFetch();
        render(<EmailComposeForm onClose={() => {}} initialRecipient="a@example.org" initialSubject="Rundschreiben" />);
        await screen.findByRole('combobox', { name: 'Senden von' });
        await userEvent.click(screen.getByLabelText(/Einzeln verschicken/));
        await userEvent.click(screen.getByRole('button', { name: 'Schließen' }));
        await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/emails/drafts' && init?.method === 'POST')).toBe(true));
        const call = fetchMock.mock.calls.find(([url, init]) => String(url) === '/api/emails/drafts' && init?.method === 'POST')!;
        expect(await dtoAus(call[1]?.body)).toMatchObject({ postfachId: 7, einzelversand: true, fromAddress: 'max@musterbetrieb.example' });
    });
});

describe('EmailComposeForm – Einzelversand', () => {
    const viele = (anzahl: number) => Array.from({ length: anzahl }, (_, i) => `kunde${i + 1}@example.org`).join(', ');

    it('weist ab 4 Empfängern auf den Einzelversand hin und blendet beim Einschalten CC aus', async () => {
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} initialRecipient={viele(4)} initialSubject="Rundschreiben" initialCc={['kopie@example.org']} />);
        expect(await screen.findByText(/4 Empfänger sehen sich gegenseitig/)).toBeInTheDocument();
        expect(screen.getByText('CC')).toBeInTheDocument();

        await userEvent.click(screen.getByLabelText(/Einzeln verschicken/));
        expect(screen.queryByText('CC')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /\+ CC/ })).not.toBeInTheDocument();
        expect(screen.getByText(/4 Empfänger bekommen je eine eigene E-Mail/)).toBeInTheDocument();

        await sende();
        await waitFor(() => expect(gesendet).toHaveLength(1));
        expect(gesendet[0].dto).toMatchObject({ einzelversand: true, cc: [] });
        expect(gesendet[0].dto.recipients).toHaveLength(4);
    });

    it('sperrt das Senden über 50 Empfängern mit Hinweis', async () => {
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} initialRecipient={viele(51)} initialSubject="Rundschreiben" />);
        await userEvent.click(await screen.findByLabelText(/Einzeln verschicken/));
        expect(screen.getByText(/Höchstens 50 Empfänger pro Sammel-Mail\. Eingetragen sind 51\./)).toBeInTheDocument();
        const senden = screen.getByRole('button', { name: /E-Mail senden/ });
        expect(senden).toBeDisabled();
        expect(senden).toHaveAttribute('title', 'Höchstens 50 Empfänger pro Sammel-Mail.');
        // Ohne Einzelversand gilt die Grenze nicht.
        await userEvent.click(screen.getByLabelText(/Einzeln verschicken/));
        expect(senden).toBeEnabled();
    });

    it('schließt nach vollem Erfolg mit Meldung', async () => {
        sendeAntwort = { body: { verschickt: 3, fehlgeschlagen: [], emails: [] } };
        stubFetch();
        const onClose = vi.fn();
        const onSuccess = vi.fn();
        render(<EmailComposeForm onClose={onClose} onSuccess={onSuccess} initialRecipient={viele(3)} initialSubject="Rundschreiben" />);
        await userEvent.click(await screen.findByLabelText(/Einzeln verschicken/));
        await sende();
        await waitFor(() => expect(onClose).toHaveBeenCalled());
        expect(onSuccess).toHaveBeenCalled();
        expect(await screen.findByText(/3 verschickt – jeder Empfänger/)).toBeInTheDocument();
    });

    it('zeigt bei Teilfehlern, wer nichts bekam – mit Grund', async () => {
        sendeAntwort = { body: { verschickt: 28, fehlgeschlagen: [
            { adresse: 'kaputt@example.org', grund: 'Empfänger unbekannt' },
            { adresse: 'voll@example.org', grund: 'Postfach voll' },
        ], emails: [] } };
        stubFetch();
        const onClose = vi.fn();
        const onSuccess = vi.fn();
        render(<EmailComposeForm onClose={onClose} onSuccess={onSuccess} initialRecipient={viele(30)} initialSubject="Rundschreiben" />);
        await userEvent.click(await screen.findByLabelText(/Einzeln verschicken/));
        await sende();

        const ergebnis = await screen.findByTestId('einzelversand-ergebnis');
        expect(ergebnis).toHaveTextContent('28 verschickt, 2 nicht');
        expect(within(ergebnis).getByText('kaputt@example.org')).toBeInTheDocument();
        expect(within(ergebnis).getByText(/Empfänger unbekannt/)).toBeInTheDocument();
        expect(screen.getByText('28 verschickt, 2 nicht: kaputt@example.org, voll@example.org')).toBeInTheDocument();
        expect(onClose).not.toHaveBeenCalled();

        await userEvent.click(screen.getByRole('button', { name: 'Fertig' }));
        expect(onSuccess).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
    });

    it('warnt, wenn Mails verschickt, aber nicht abgelegt wurden – nicht erneut senden', async () => {
        sendeAntwort = { body: { verschickt: 3, fehlgeschlagen: [], nichtGespeichert: ['kunde2@example.org'], emails: [] } };
        stubFetch();
        const onClose = vi.fn();
        render(<EmailComposeForm onClose={onClose} initialRecipient={viele(3)} initialSubject="Rundschreiben" />);
        await userEvent.click(await screen.findByLabelText(/Einzeln verschicken/));
        await sende();

        const hinweis = await screen.findByTestId('einzelversand-nicht-abgelegt');
        expect(hinweis).toHaveTextContent('bitte NICHT erneut senden');
        expect(within(hinweis).getByText('kunde2@example.org')).toBeInTheDocument();
        expect(screen.getByTestId('einzelversand-ergebnis')).toHaveTextContent('3 verschickt');
        expect(onClose).not.toHaveBeenCalled();
    });

    it('lässt bei 502 (nichts verschickt) das Formular offen und nennt die Gründe', async () => {
        sendeAntwort = { status: 502, body: { message: 'Keine der E-Mails ging raus.', fehlgeschlagen: [{ adresse: 'kunde1@example.org', grund: 'Server lehnt ab' }] } };
        stubFetch();
        const onClose = vi.fn();
        render(<EmailComposeForm onClose={onClose} initialRecipient={viele(2)} initialSubject="Rundschreiben" />);
        await userEvent.click(await screen.findByLabelText(/Einzeln verschicken/));
        await sende();

        const ergebnis = await screen.findByTestId('einzelversand-ergebnis');
        expect(ergebnis).toHaveTextContent('0 verschickt, 1 nicht');
        expect(ergebnis).toHaveTextContent('Server lehnt ab');
        expect((await screen.findAllByText('Keine der E-Mails ging raus.')).length).toBeGreaterThan(0);
        expect(screen.getByRole('button', { name: /E-Mail senden/ })).toBeEnabled();
        expect(onClose).not.toHaveBeenCalled();
    });

    it('nutzt bei 502 ohne Meldung einen eigenen Text', async () => {
        sendeAntwort = { status: 502, body: {} };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} initialRecipient={viele(2)} initialSubject="Rundschreiben" />);
        await userEvent.click(await screen.findByLabelText(/Einzeln verschicken/));
        await sende();
        expect((await screen.findAllByText('Keine der E-Mails konnte verschickt werden.')).length).toBeGreaterThan(0);
    });
});

describe('EmailComposeForm – Sichtbarkeit der Postfächer (Etappe 2)', () => {
    it('meldet 403 beim Senden über ein nicht erlaubtes Postfach als Toast und lässt das Formular offen', async () => {
        sendeAntwort = { status: 403, body: { message: 'Über dieses Postfach dürfen Sie nicht senden.' } };
        stubFetch();
        const onClose = vi.fn();
        const onSuccess = vi.fn();
        render(<EmailComposeForm onClose={onClose} onSuccess={onSuccess} initialRecipient="kunde@example.org" initialSubject="Termin" />);
        await screen.findByRole('combobox', { name: 'Senden von' });
        await sende();

        // Einmal im Formular, einmal als Toast.
        await waitFor(() => expect(screen.getAllByText('Über dieses Postfach dürfen Sie nicht senden.')).toHaveLength(2));
        expect(onClose).not.toHaveBeenCalled();
        expect(onSuccess).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: /E-Mail senden/ })).toBeEnabled();
        expect(screen.getByDisplayValue('Termin')).toBeInTheDocument();
    });

    it('zeigt ohne erlaubtes Postfach einen verständlichen Hinweis statt einer leeren Auswahl', async () => {
        absenderAntwort = { body: [] };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} initialRecipient="kunde@example.org" initialSubject="Termin" />);
        expect(await screen.findByText(/Kein Postfach zur Auswahl/)).toBeInTheDocument();
        expect(screen.getByTestId('absender-fest')).toHaveTextContent('Standard-Absender des Betriebs');
        expect(screen.queryByRole('combobox', { name: 'Senden von' })).not.toBeInTheDocument();
        await sende();
        await waitFor(() => expect(gesendet[0]?.dto).toMatchObject({ postfachId: null }));
    });

    it('speichert beim Weiterleiten weitergeleitetVonEmailId im Entwurf', async () => {
        const fetchMock = stubFetch();
        render(<EmailComposeForm onClose={() => {}} weitergeleitetVonEmailId={5} initialSubject="WG: Treppe"
            antwortPostfach={{ id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' }} />);
        await userEvent.type(screen.getByPlaceholderText('Name, Firma oder E-Mail eingeben'), 'kollege@example.org');
        await userEvent.click(screen.getByRole('button', { name: 'Schließen' }));
        await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/emails/drafts' && init?.method === 'POST')).toBe(true));
        const call = fetchMock.mock.calls.find(([url, init]) => String(url) === '/api/emails/drafts' && init?.method === 'POST')!;
        expect(await dtoAus(call[1]?.body)).toMatchObject({ weitergeleitetVonEmailId: 5, postfachId: null, fromAddress: null, replyEmailId: null });
    });

    it('zeigt beim Wiederöffnen eines Weiterleitungs-Entwurfs den festen Absender und schickt die Weiterleitung mit', async () => {
        entwurfAntwort = { body: { id: 55, recipient: 'kollege@example.org', subject: 'WG: Treppe', body: '<p>x</p>',
            replyEmailId: null, weitergeleitetVonEmailId: 5, attachments: [] } };
        const fetchMock = stubFetch();
        render(<EmailComposeForm onClose={() => {}} draftId={55} />);

        expect(await screen.findByText('Musterbetrieb <info@musterbetrieb.example>')).toBeInTheDocument();
        expect(screen.getByText(/Weiterleitungen gehen über das Postfach raus/)).toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Senden von' })).not.toBeInTheDocument();
        expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/emails/5')).toBe(true);

        await sende();
        await waitFor(() => expect(gesendet[0]?.url).toBe('/api/emails/send'));
        expect(gesendet[0].dto).toMatchObject({ weitergeleitetVonEmailId: 5, postfachId: null, draftId: 55 });
    });

    it('nimmt die Weiterleitung vom Aufrufer, wenn ein älterer Entwurf das Feld nicht kennt', async () => {
        entwurfAntwort = { body: { id: 55, recipient: 'kollege@example.org', subject: 'WG: Treppe', body: '<p>x</p>', attachments: [] } };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} draftId={55} weitergeleitetVonEmailId={5} />);
        expect(await screen.findByText(/Weiterleitungen gehen über das Postfach raus/)).toBeInTheDocument();
        await sende();
        await waitFor(() => expect(gesendet[0]?.dto).toMatchObject({ weitergeleitetVonEmailId: 5 }));
    });
});

describe('EmailComposeForm – auslaufendes Postfach', () => {
    it('meldet 400 „läuft aus“ als Toast und lässt das Formular offen', async () => {
        sendeAntwort = { status: 400, body: { message: 'Dieses Postfach läuft aus. Bitte ein anderes Postfach wählen.' } };
        stubFetch();
        const onClose = vi.fn();
        render(<EmailComposeForm onClose={onClose} initialRecipient="kunde@example.org" initialSubject="Termin" />);
        await screen.findByRole('combobox', { name: 'Senden von' });
        await sende();

        await waitFor(() => expect(screen.getAllByText('Dieses Postfach läuft aus. Bitte ein anderes Postfach wählen.')).toHaveLength(2));
        expect(onClose).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: /E-Mail senden/ })).toBeEnabled();
        expect(screen.getByDisplayValue('Termin')).toBeInTheDocument();
    });
});
