import { render as rtlRender, screen, waitFor, within } from '@testing-library/react';
import type { ReactElement } from 'react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from './ui/toast';
import { EmailComposeForm } from './EmailComposeForm';
import type { EmailAttachment } from '../features/email/emailCenterModel';

/**
 * Weiterleiten mit den Anhängen der Original-Mail.
 * Alle Daten sind Dummy-Daten (DSGVO).
 */

const render = (ui: ReactElement) => rtlRender(<ToastProvider>{ui}</ToastProvider>);

const POSTFACH = { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' };
const ANHAENGE: EmailAttachment[] = [
    { id: 11, originalFilename: 'Angebot-Mustermann.pdf', mimeType: 'application/pdf', fileSize: 240 * 1024, inline: false },
    { id: 12, originalFilename: 'Aufmass.jpg', mimeType: 'image/jpeg', fileSize: 1.5 * 1024 * 1024 },
    { id: 13, originalFilename: 'logo.png', mimeType: 'image/png', fileSize: 8 * 1024, contentId: 'logo', inline: true },
];

type Antwort = { status?: number; body?: unknown };
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
        if (url === '/api/emails/absender-postfaecher') return json({ body: [{ ...POSTFACH, eigenes: true, hauptpostfach: true }] });
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
    sendeAntwort = { body: { id: 999 } };
    originalAntwort = { body: { id: 5, antwortPostfach: POSTFACH, attachments: ANHAENGE } };
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

function zeigeWeiterleitung(props: Partial<Parameters<typeof EmailComposeForm>[0]> = {}) {
    return render(<EmailComposeForm onClose={() => {}} weitergeleitetVonEmailId={5} initialSubject="Fwd: Treppe"
        initialRecipient="kollege@example.org" antwortPostfach={POSTFACH} weiterleitungsAnhaenge={ANHAENGE} {...props} />);
}

describe('EmailComposeForm – Anhänge beim Weiterleiten', () => {
    it('zeigt die Anhänge der Original-Mail ohne eingebettete Bilder und schickt alle mit', async () => {
        stubFetch();
        zeigeWeiterleitung();

        const liste = screen.getByTestId('weiterleitung-anhaenge');
        expect(within(liste).getByText('Anhänge der Original-Mail')).toBeInTheDocument();
        expect(within(liste).getByText('Angebot-Mustermann.pdf')).toBeInTheDocument();
        expect(within(liste).getByText('240 KB')).toBeInTheDocument();
        expect(within(liste).getByText('Aufmass.jpg')).toBeInTheDocument();
        expect(within(liste).getByText('1.5 MB')).toBeInTheDocument();
        expect(within(liste).queryByText('logo.png')).not.toBeInTheDocument();

        await sende();
        await waitFor(() => expect(gesendet[0]?.url).toBe('/api/emails/send'));
        expect(gesendet[0].dto).toMatchObject({ weitergeleitetVonEmailId: 5, weiterleitungsAnhangIds: [11, 12] });
    });

    it('entfernt per X einen Anhang aus dem Versand und hängt ihn auf Wunsch wieder an', async () => {
        stubFetch();
        zeigeWeiterleitung();

        await userEvent.click(screen.getByRole('button', { name: 'Aufmass.jpg entfernen' }));
        expect(screen.queryByText('Aufmass.jpg')).not.toBeInTheDocument();
        expect(screen.getByText('1 Anhang wird nicht mitgesendet.')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Angebot-Mustermann.pdf entfernen' }));
        expect(screen.getByText('2 Anhänge werden nicht mitgesendet.')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Wieder anhängen' }));
        expect(screen.getByText('Aufmass.jpg')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Angebot-Mustermann.pdf entfernen' }));
        await sende();
        await waitFor(() => expect(gesendet[0]?.url).toBe('/api/emails/send'));
        expect(gesendet[0].dto).toMatchObject({ weiterleitungsAnhangIds: [12] });
    });

    it('rechnet die weitergeleiteten Anhänge in die Größenanzeige ein', async () => {
        stubFetch();
        zeigeWeiterleitung();
        // 240 KB + 1,5 MB = 1,7 MB
        expect(screen.getByText(/^1\.7 MB von/)).toBeInTheDocument();
    });

    it('erklärt per (i), dass die Dateien direkt aus der Original-Mail kommen', async () => {
        stubFetch();
        zeigeWeiterleitung();

        await userEvent.click(screen.getByRole('button', { name: /Anhänge der Original-Mail/ }));
        const erklaerung = await screen.findByRole('dialog');
        expect(erklaerung).toHaveTextContent('Was passiert im Hintergrund?');
        expect(erklaerung).toHaveTextContent('direkt aus der ursprünglichen E-Mail angehängt');
        expect(erklaerung).toHaveTextContent('die Original-Mail bleibt unverändert');
    });

    it('zeigt keine Liste, wenn die Original-Mail nur eingebettete Bilder hat', async () => {
        stubFetch();
        zeigeWeiterleitung({ weiterleitungsAnhaenge: [ANHAENGE[2]] });
        expect(screen.queryByTestId('weiterleitung-anhaenge')).not.toBeInTheDocument();

        await sende();
        await waitFor(() => expect(gesendet[0]?.url).toBe('/api/emails/send'));
        expect(gesendet[0].dto).toMatchObject({ weiterleitungsAnhangIds: [] });
    });

    it('zeigt die Meldung des Servers, wenn ein Anhang nicht mehr da ist', async () => {
        sendeAntwort = { status: 400, body: { message: "Der Anhang 'Aufmass.jpg' ist nicht mehr vorhanden." } };
        stubFetch();
        const onClose = vi.fn();
        zeigeWeiterleitung({ onClose });

        await sende();
        await waitFor(() => expect(screen.getAllByText("Der Anhang 'Aufmass.jpg' ist nicht mehr vorhanden.")).toHaveLength(2));
        expect(onClose).not.toHaveBeenCalled();
    });

    it('lädt beim Wiederöffnen eines Weiterleitungs-Entwurfs die Anhänge nach und belegt alle vor', async () => {
        entwurfAntwort = { body: { id: 55, recipient: 'kollege@example.org', subject: 'WG: Treppe', body: '<p>x</p>',
            replyEmailId: null, weitergeleitetVonEmailId: 5, attachments: [] } };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} draftId={55} weitergeleitetVonEmailId={5} />);

        expect(await screen.findByText('Angebot-Mustermann.pdf')).toBeInTheDocument();
        expect(screen.getByText('Aufmass.jpg')).toBeInTheDocument();
        expect(screen.queryByText('logo.png')).not.toBeInTheDocument();

        await sende();
        await waitFor(() => expect(gesendet[0]?.url).toBe('/api/emails/send'));
        expect(gesendet[0].dto).toMatchObject({ weitergeleitetVonEmailId: 5, draftId: 55, weiterleitungsAnhangIds: [11, 12] });
    });

    it('meldet, wenn die Anhänge der Original-Mail nicht geladen werden können', async () => {
        originalAntwort = { status: 404, body: { message: 'Die weitergeleitete E-Mail gibt es nicht mehr.' } };
        stubFetch();
        render(<EmailComposeForm onClose={() => {}} weitergeleitetVonEmailId={5} initialSubject="Fwd: Treppe"
            initialRecipient="kollege@example.org" antwortPostfach={POSTFACH} />);

        expect(await screen.findByText('Die Anhänge der Original-Mail konnten nicht geladen werden.')).toBeInTheDocument();
        expect(screen.queryByTestId('weiterleitung-anhaenge')).not.toBeInTheDocument();
    });

    it('schickt bei Antworten und neuen Mails keine Weiterleitungs-Anhänge', async () => {
        stubFetch();
        const { unmount } = render(<EmailComposeForm onClose={() => {}} replyEmailId={5} initialRecipient="kunde@example.org"
            initialSubject="Re: Treppe" antwortPostfach={POSTFACH} />);
        await sende();
        await waitFor(() => expect(gesendet[0]?.url).toBe('/api/emails/5/reply'));
        expect(gesendet[0].dto).toMatchObject({ weiterleitungsAnhangIds: null });
        expect(screen.queryByTestId('weiterleitung-anhaenge')).not.toBeInTheDocument();
        unmount();

        render(<EmailComposeForm onClose={() => {}} initialRecipient="kunde@example.org" initialSubject="Angebot" />);
        await screen.findByRole('combobox', { name: 'Senden von' });
        await sende();
        await waitFor(() => expect(gesendet[1]?.url).toBe('/api/emails/send'));
        expect(gesendet[1].dto).toMatchObject({ weiterleitungsAnhangIds: null });
    });
});
