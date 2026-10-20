import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CreateArticleModal } from './CreateArticleModal';
import { ToastProvider } from './ui/toast';

/** Dummy-Lieferant (DSGVO: keine echten Firmen). */
const lieferanten = { lieferanten: [{ id: 5, lieferantenname: 'Musterfirma GmbH' }] };

let fetchMock: ReturnType<typeof vi.fn>;
let postAntwort: { ok: boolean; status: number; body: unknown };

function renderModal() {
    const onSave = vi.fn();
    const onClose = vi.fn();
    render(
        <ToastProvider>
            <CreateArticleModal onSave={onSave} onClose={onClose} />
        </ToastProvider>,
    );
    return { onSave, onClose };
}

const postBody = () => {
    const aufruf = fetchMock.mock.calls.find(([url, o]) => o?.method === 'POST' && url === '/api/artikel');
    return aufruf ? JSON.parse(String(aufruf[1]?.body)) : null;
};

async function lieferantWaehlen() {
    await userEvent.click(screen.getByRole('button', { name: /Lieferant \(optional\)/ }));
    await userEvent.click(await screen.findByText('Musterfirma GmbH'));
}

describe('CreateArticleModal', () => {
    beforeEach(() => {
        postAntwort = { ok: true, status: 200, body: { id: 1 } };
        fetchMock = vi.fn((url: string, options?: RequestInit) => {
            if (options?.method === 'POST') {
                return Promise.resolve({ ok: postAntwort.ok, status: postAntwort.status, json: () => Promise.resolve(postAntwort.body) });
            }
            if (url.startsWith('/api/lieferanten')) {
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(lieferanten) });
            }
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
        });
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => vi.unstubAllGlobals());

    it('sperrt Preis und Lieferanten-Nummer, solange kein Lieferant gewählt ist', async () => {
        renderModal();

        expect(screen.getByLabelText('Preis in Euro')).toBeDisabled();
        expect(screen.getByLabelText('Artikelnummer beim Lieferanten')).toBeDisabled();
        expect(screen.getByText(/Erst den Lieferanten wählen – Preis und Lieferanten-Nummer/)).toBeInTheDocument();
        // Kein Preis mit 0 vorbelegt
        expect(screen.getByLabelText('Preis in Euro')).toHaveValue('');

        await lieferantWaehlen();

        expect(screen.getByLabelText('Preis in Euro')).toBeEnabled();
        expect(screen.getByLabelText('Artikelnummer beim Lieferanten')).toBeEnabled();
        expect(screen.queryByText(/Erst den Lieferanten wählen/)).not.toBeInTheDocument();
    });

    it('schickt ohne Lieferant keinen Preis (null statt 0) und die eigene Artikel-Nr.', async () => {
        const { onSave } = renderModal();

        await userEvent.type(screen.getByLabelText('Produktname *'), 'Quadratrohr 40x40x2');
        await userEvent.type(screen.getByLabelText('Eigene Artikel-Nr.'), '4711');
        await userEvent.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        await waitFor(() => expect(postBody()).not.toBeNull());
        const body = postBody();
        expect(body.artikelnummer).toBe('4711');
        expect(body.preis).toBeNull();
        expect(body.lieferantId).toBeNull();
        expect(body.externeArtikelnummer).toBeNull();
        await waitFor(() => expect(onSave).toHaveBeenCalled());
    });

    it('liest den Preis im deutschen Format mit Lieferant', async () => {
        renderModal();

        await userEvent.type(screen.getByLabelText('Produktname *'), 'Quadratrohr 40x40x2');
        await lieferantWaehlen();
        await userEvent.type(screen.getByLabelText('Artikelnummer beim Lieferanten'), 'MF-100');
        await userEvent.type(screen.getByLabelText('Preis in Euro'), '1.234,50');
        await userEvent.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        await waitFor(() => expect(postBody()).not.toBeNull());
        expect(postBody()).toMatchObject({ lieferantId: 5, preis: 1234.5, externeArtikelnummer: 'MF-100', artikelnummer: null });
    });

    it('zeigt die Meldung des Servers bei einer vergebenen Artikelnummer', async () => {
        postAntwort = { ok: false, status: 400, body: { status: 400, message: 'Die Artikelnummer „4711“ ist schon vergeben.' } };
        const { onSave } = renderModal();

        await userEvent.type(screen.getByLabelText('Produktname *'), 'Quadratrohr');
        await userEvent.type(screen.getByLabelText('Eigene Artikel-Nr.'), '4711');
        await userEvent.click(screen.getByRole('button', { name: 'Artikel anlegen' }));

        expect(await screen.findByText('Die Artikelnummer „4711“ ist schon vergeben.')).toBeInTheDocument();
        expect(onSave).not.toHaveBeenCalled();
    });

    it('erklärt die eigene Artikel-Nr. im Info-Button', async () => {
        renderModal();
        await userEvent.click(screen.getByRole('button', { name: /Eigene Artikel-Nr\./ }));
        expect(await screen.findByText(/unabhängig vom Lieferanten/)).toBeInTheDocument();
        expect(screen.getByText(/muss eindeutig sein/)).toBeInTheDocument();
    });
});
