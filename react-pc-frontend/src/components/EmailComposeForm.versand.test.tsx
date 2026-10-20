import { render as rtlRender, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from './ui/toast';
import { EmailComposeForm } from './EmailComposeForm';

/**
 * Versand aus dem Dokument-Editor: Aktion vor dem Senden (Rechnung buchen),
 * gesperrte Vorschau der ungebuchten Rechnung, kein automatischer Entwurf und
 * das Ablösen alter Online-Links.
 *
 * Alle Daten sind Dummy-Daten (DSGVO).
 */

const render = (ui: ReactElement) => rtlRender(<ToastProvider>{ui}</ToastProvider>);

interface Aufruf { url: string; method: string; body: unknown }
let aufrufe: Aufruf[];
let sendeAntwort: () => Response;

function jsonResponse(data: unknown, status = 200) {
    return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
}

function mockFetch() {
    return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        const method = init?.method || 'GET';
        aufrufe.push({ url, method, body: init?.body ?? null });
        if (url === '/api/emails/send') return sendeAntwort();
        if (url.startsWith('/api/emails/drafts')) return jsonResponse({ id: 1 });
        if (url.startsWith('/api/email/signatures/default')) return new Response(null, { status: 204 });
        if (url.startsWith('/api/email/dokument-absender')) return jsonResponse({ aktiv: false, address: null });
        if (url.startsWith('/api/emails/absender-postfaecher')) {
            return jsonResponse([{ id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', eigenes: false, hauptpostfach: true }]);
        }
        return jsonResponse([]);
    });
}

const sendeAufrufe = () => aufrufe.filter(a => a.url === '/api/emails/send');

async function gesendetesDto(): Promise<Record<string, unknown>> {
    const formData = sendeAufrufe()[0].body as FormData;
    return JSON.parse(await (formData.get('dto') as Blob).text());
}

const rechnungsPdf = () => new File(['%PDF-1.4'], 'Rechnung_RE-2026-0001.pdf', { type: 'application/pdf' });

function renderFormular(props: Partial<React.ComponentProps<typeof EmailComposeForm>> = {}) {
    return render(
        <EmailComposeForm
            onClose={() => { }}
            initialRecipient="max.mustermann@example.com"
            initialSubject="Rechnung RE-2026-0001"
            initialAttachments={[rechnungsPdf()]}
            geschaeftsdokument
            variant="modal"
            {...props}
        />
    );
}

async function sendeAb() {
    await waitFor(() => expect(screen.getByDisplayValue('Rechnung RE-2026-0001')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: /E-Mail senden/i }));
}

describe('EmailComposeForm – Versand aus dem Dokument-Editor', () => {
    beforeEach(() => {
        aufrufe = [];
        sendeAntwort = () => jsonResponse({ id: 900 });
        vi.stubGlobal('fetch', mockFetch());
        vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:vorschau'), revokeObjectURL: vi.fn() }));
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('sendet nicht, wenn die Aktion vor dem Senden abbricht (z. B. Buchung gescheitert)', async () => {
        const vorDemSenden = vi.fn(async () => ({ weiter: false }));
        const onSuccess = vi.fn();
        renderFormular({ vorDemSenden, onSuccess });

        await sendeAb();

        await waitFor(() => expect(vorDemSenden).toHaveBeenCalledTimes(1));
        expect(sendeAufrufe()).toHaveLength(0);
        expect(onSuccess).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: /E-Mail senden/i })).toBeEnabled();
    });

    it('ruft die Aktion vor dem Senden auf und sendet danach', async () => {
        const reihenfolge: string[] = [];
        const vorDemSenden = vi.fn(async () => { reihenfolge.push('buchen'); return { weiter: true }; });
        const onSuccess = vi.fn(() => reihenfolge.push('erfolg'));
        renderFormular({ vorDemSenden, onSuccess });

        await sendeAb();

        await waitFor(() => expect(onSuccess).toHaveBeenCalled());
        expect(sendeAufrufe()).toHaveLength(1);
        expect(reihenfolge).toEqual(['buchen', 'erfolg']);
    });

    it('nennt bei einem Sendefehler nach der Buchung den Hinweis des Aufrufers', async () => {
        sendeAntwort = () => jsonResponse({ message: 'Postfach nicht erreichbar.' }, 500);
        renderFormular({
            vorDemSenden: async () => ({
                weiter: true,
                hinweisBeiSendefehler: 'Die Rechnung ist bereits gebucht. Bitte die E-Mail erneut senden.',
            }),
        });

        await sendeAb();

        const meldungen = await screen.findAllByText(/Die Rechnung ist bereits gebucht\. Bitte die E-Mail erneut senden\./);
        expect(meldungen.length).toBeGreaterThan(0);
    });

    it('gibt die bestätigte Ablösung alter Online-Links an den Versand mit', async () => {
        renderFormular({ freigabenAbloesenFuerDokumentId: 7 });

        await sendeAb();

        await waitFor(() => expect(sendeAufrufe()).toHaveLength(1));
        expect((await gesendetesDto()).freigabenAbloesenFuerDokumentId).toBe(7);
    });

    it('schickt ohne Ablösung kein Dokument zum Ablösen mit', async () => {
        renderFormular();

        await sendeAb();

        await waitFor(() => expect(sendeAufrufe()).toHaveLength(1));
        expect((await gesendetesDto()).freigabenAbloesenFuerDokumentId).toBeNull();
    });

    it('sperrt Vorschau und Download der ungebuchten Rechnung und zeigt den Grund', async () => {
        renderFormular({ anhangVorschauGesperrt: 'Vorschau und Download erst nach dem Senden.' });

        expect(await screen.findByTestId('anhang-vorschau-gesperrt')).toHaveTextContent('Vorschau und Download erst nach dem Senden.');
        expect(screen.queryByRole('button', { name: /PDF-Vorschau öffnen/i })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Vorschau für Rechnung_RE-2026-0001\.pdf/i })).not.toBeInTheDocument();
        // Entfernen bleibt möglich – nur Ansehen/Herunterladen ist gesperrt.
        expect(screen.getByRole('button', { name: /Rechnung_RE-2026-0001\.pdf entfernen/i })).toBeInTheDocument();
    });

    it('zeigt die Vorschau ohne Sperre wie bisher', async () => {
        renderFormular();

        expect(await screen.findByRole('button', { name: /PDF-Vorschau öffnen/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Vorschau für Rechnung_RE-2026-0001\.pdf/i })).toBeInTheDocument();
        expect(screen.queryByTestId('anhang-vorschau-gesperrt')).not.toBeInTheDocument();
    });

    it('legt ohne Entwurfs-Speicherung auch beim Schließen keinen Entwurf an', async () => {
        const onClose = vi.fn();
        renderFormular({ entwurfSpeichern: false, onClose });

        const betreff = await screen.findByDisplayValue('Rechnung RE-2026-0001');
        await userEvent.type(betreff, ' – Nachtrag');
        expect(screen.getByText('Wird nicht als Entwurf gespeichert')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: /^Schließen$/ }));

        await waitFor(() => expect(onClose).toHaveBeenCalled());
        expect(aufrufe.some(a => a.url.startsWith('/api/emails/drafts'))).toBe(false);
    });

    it('sperrt das Senden, wenn das Pflicht-PDF entfernt wurde, und nennt den Grund', async () => {
        const vorDemSenden = vi.fn(async () => ({ weiter: true }));
        renderFormular({ vorDemSenden, pflichtanhangHinweis: 'Das Rechnungs-PDF muss angehängt bleiben.' });

        await waitFor(() => expect(screen.getByDisplayValue('Rechnung RE-2026-0001')).toBeInTheDocument());
        expect(screen.queryByTestId('pflichtanhang-fehlt')).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: /Rechnung_RE-2026-0001\.pdf entfernen/i }));

        expect(await screen.findByTestId('pflichtanhang-fehlt')).toHaveTextContent('Das Rechnungs-PDF muss angehängt bleiben.');
        expect(screen.getByRole('button', { name: /E-Mail senden/i })).toBeDisabled();
        expect(vorDemSenden).not.toHaveBeenCalled();
        expect(sendeAufrufe()).toHaveLength(0);
    });

    it('meldet nach dem Senden einmal „E-Mail wurde gesendet.“', async () => {
        const onSuccess = vi.fn();
        renderFormular({ onSuccess });

        await sendeAb();

        await waitFor(() => expect(onSuccess).toHaveBeenCalled());
        expect(await screen.findAllByText('E-Mail wurde gesendet.')).toHaveLength(1);
    });

    it('nimmt einen eigenen Text für die Erfolgsmeldung', async () => {
        renderFormular({ erfolgsMeldung: 'Reklamation an Musterstahl GmbH verschickt.' });

        await sendeAb();

        expect(await screen.findByText('Reklamation an Musterstahl GmbH verschickt.')).toBeInTheDocument();
        expect(screen.queryByText('E-Mail wurde gesendet.')).toBeNull();
    });

    it('meldet bei einem Sendefehler keinen Erfolg', async () => {
        sendeAntwort = () => jsonResponse({ message: 'Postfach nicht erreichbar.' }, 500);
        renderFormular();

        await sendeAb();

        expect((await screen.findAllByText(/Postfach nicht erreichbar\./)).length).toBeGreaterThan(0);
        expect(screen.queryByText('E-Mail wurde gesendet.')).toBeNull();
    });
});
