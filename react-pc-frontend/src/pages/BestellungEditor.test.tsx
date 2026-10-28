/**
 * Vitest-Suite fuer den Mail-Dialog der Bestellung (BestellungEmailModal) –
 * Schwerpunkt: Nachrichtentext (XSS).
 *
 * Die Signatur kommt als HTML vom Server und landet im contentEditable-Feld.
 * Angezeigt werden darf sie nur bereinigt; Formatierung und die
 * email-signature-Klasse bleiben erhalten.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BestellungEditor, { BestellungEmailModal } from './BestellungEditor';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

vi.mock('../components/ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf">{url}</div>,
}));

const SIGNATUR_HTML =
    '<p><strong>Max Mustermann</strong></p><img src="x" onerror="alert(1)"><script>alert(2)</script>';

function antwort(daten: unknown) {
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(daten) });
}

beforeEach(() => {
    localStorage.setItem('frontendUserSelection', JSON.stringify({ id: 1, displayName: 'Max Mustermann' }));
    vi.stubGlobal('fetch', vi.fn((url: string) => {
        if (url.startsWith('/api/email/signatures/default')) return antwort({ html: SIGNATUR_HTML });
        if (url.startsWith('/api/lieferanten/')) return antwort({ kundenEmails: ['anfrage@example.com'] });
        return antwort([]);
    }));
});

afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('BestellungEmailModal Nachrichtentext', () => {
    it('zeigt die Signatur im Editor nur bereinigt an', async () => {
        render(
            <ToastProvider><ConfirmProvider>
                <BestellungEmailModal isOpen onClose={vi.fn()} lieferantId={1}
                    lieferantName="Mustermann Stahlhandel" onSuccess={vi.fn()} />
            </ConfirmProvider></ToastProvider>,
        );

        const name = await screen.findByText('Max Mustermann', { selector: 'strong' });
        const editor = name.closest('[contenteditable]')!;

        expect(editor).toHaveTextContent('bitte erstellen Sie uns ein Angebot');
        expect(editor.querySelector('.email-signature')).not.toBeNull();
        expect(editor.querySelector('script')).toBeNull();
        expect(editor.querySelector('[onerror]')).toBeNull();
    });
});

describe('BestellungEmailModal KI-Formulierung', () => {
    const oeffneDialog = () => render(
        <ToastProvider><ConfirmProvider>
            <BestellungEmailModal isOpen onClose={vi.fn()} lieferantId={1}
                lieferantName="Mustermann Stahlhandel" onSuccess={vi.fn()} />
        </ConfirmProvider></ToastProvider>,
    );

    it('schickt den eigenen Text an die KI und ersetzt ihn, die Signatur bleibt', async () => {
        const fetchMock = vi.mocked(fetch);
        fetchMock.mockImplementation(((url: string) => {
            if (url === '/api/email/beautify') return antwort({ body: 'Guten Tag,\n\nwir bitten um ein Angebot bis Freitag.' });
            if (url.startsWith('/api/email/signatures/default')) return antwort({ html: '<p>Max Mustermann</p>' });
            return antwort([]);
        }) as unknown as typeof fetch);
        oeffneDialog();
        await screen.findByText('Max Mustermann');

        await userEvent.click(screen.getByRole('button', { name: /KI Formulierung/ }));

        const editor = (await screen.findByText(/bis Freitag/)).closest('[contenteditable]')!;
        expect(Array.from(editor.querySelectorAll(':scope > p'), p => p.textContent))
            .toEqual(['Guten Tag,', 'wir bitten um ein Angebot bis Freitag.']);
        expect(editor).not.toHaveTextContent('bitte erstellen Sie uns ein Angebot');
        expect(editor.querySelector('.email-signature')).toHaveTextContent('Max Mustermann');

        const anfrage = fetchMock.mock.calls.find(([url]) => url === '/api/email/beautify')!;
        expect(JSON.parse(String((anfrage[1] as RequestInit).body))).toEqual({
            body: 'Sehr geehrte Damen und Herren,\n\nbitte erstellen Sie uns ein Angebot für die angehängten Positionen.',
            context: 'Bestellung beim Lieferanten Mustermann Stahlhandel',
        });
    });

    it('meldet einen Fehler der KI und laesst den Text stehen', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        vi.mocked(fetch).mockImplementation(((url: string) => {
            if (url === '/api/email/beautify') return Promise.resolve({ ok: false, status: 502, json: () => Promise.resolve({}) });
            return antwort([]);
        }) as unknown as typeof fetch);
        oeffneDialog();
        await screen.findByText(/bitte erstellen Sie uns ein Angebot/);

        await userEvent.click(screen.getByRole('button', { name: /KI Formulierung/ }));

        expect(await screen.findByText('Formulierung fehlgeschlagen. Bitte erneut versuchen.')).toBeInTheDocument();
        expect(screen.getByText(/bitte erstellen Sie uns ein Angebot/)).toBeInTheDocument();
    });

    it('bittet um Text, wenn ausser der Signatur nichts im Editor steht', async () => {
        oeffneDialog();
        const name = await screen.findByText('Max Mustermann', { selector: 'strong' });
        const editor = name.closest('[contenteditable]')!;
        editor.innerHTML = editor.querySelector('.email-signature')!.outerHTML;

        await userEvent.click(screen.getByRole('button', { name: /KI Formulierung/ }));

        expect(await screen.findByText('Bitte zuerst eine Nachricht schreiben.')).toBeInTheDocument();
        expect(vi.mocked(fetch).mock.calls.some(([url]) => url === '/api/email/beautify')).toBe(false);
    });

    it('meldet, wenn die KI keine andere Formulierung liefert', async () => {
        vi.mocked(fetch).mockImplementation(((url: string) => {
            if (url === '/api/email/beautify') return antwort({ body: '  ' });
            return antwort([]);
        }) as unknown as typeof fetch);
        oeffneDialog();
        await screen.findByText(/bitte erstellen Sie uns ein Angebot/);

        await userEvent.click(screen.getByRole('button', { name: /KI Formulierung/ }));

        expect(await screen.findByText('Die KI hat keine andere Formulierung geliefert.')).toBeInTheDocument();
        expect(screen.getByText(/bitte erstellen Sie uns ein Angebot/)).toBeInTheDocument();
    });

    it('zeigt eine begruendete Ablehnung als Fehler und schickt keinen Stil mit', async () => {
        vi.mocked(fetch).mockImplementation(((url: string) => {
            if (url === '/api/email/beautify') {
                return Promise.resolve(new Response(JSON.stringify({ message: 'Unbekannter Schreibstil.' }), { status: 400 }));
            }
            return antwort([]);
        }) as unknown as typeof fetch);
        oeffneDialog();
        await screen.findByText(/bitte erstellen Sie uns ein Angebot/);

        await userEvent.click(screen.getByRole('button', { name: /KI Formulierung/ }));

        expect(await screen.findByText('Unbekannter Schreibstil.')).toBeInTheDocument();
        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
        const anfrage = vi.mocked(fetch).mock.calls.find(([url]) => url === '/api/email/beautify')!;
        expect(JSON.parse(String((anfrage[1] as RequestInit).body))).not.toHaveProperty('stil');
        expect(screen.getByText(/bitte erstellen Sie uns ein Angebot/)).toBeInTheDocument();
    });
});

describe('Bedarf-Seite', () => {
    it('blendet die noch nicht fertige Bestellanfrage per E-Mail aus', async () => {
        vi.mocked(fetch).mockImplementation(((url: string) => {
            if (url === '/api/bestellungen/offen') {
                return antwort([{ id: 1, artikelId: 10, produktname: 'Flachstahl 40x5', stueckzahl: 4, bestellt: false, lieferantId: 7, lieferantName: 'Mustermann Stahlhandel' }]);
            }
            return antwort([]);
        }) as unknown as typeof fetch);
        render(<ToastProvider><ConfirmProvider><BestellungEditor /></ConfirmProvider></ToastProvider>);

        expect(await screen.findByRole('button', { name: /PDF/ })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /^E-Mail$/ })).not.toBeInTheDocument();
    });
});
