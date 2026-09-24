import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mock = vi.hoisted(() => ({ submit: vi.fn(), toast: { error: vi.fn(), info: vi.fn(), warning: vi.fn(), success: vi.fn() } }));
vi.mock('./ui/toast', () => ({ useToast: () => mock.toast }));
vi.mock('../lib/idsPunchout', () => ({ submitPunchoutForm: mock.submit }));

import { IdsLieferantenAuswahlModal } from './IdsLieferantenAuswahlModal';

const form = { action: 'https://shop.example.test', enctype: 'multipart/form-data', fields: { hookurl: 'x' } };
let fetchMock: ReturnType<typeof vi.fn>;
beforeEach(() => {
    vi.clearAllMocks();
    fetchMock = vi.fn(async (url: string) => url === '/api/ids/lieferanten'
        ? new Response(JSON.stringify([{ id: 203, name: 'Würth' }]))
        : new Response(JSON.stringify(form)));
    vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('IdsLieferantenAuswahlModal', () => {
    it('schickt beim Start aus dem Projektbedarf das Projekt mit und nennt es im Hinweis', async () => {
        const onClose = vi.fn();
        render(<IdsLieferantenAuswahlModal isOpen onClose={onClose} projektId={7} projektName="Musterhaus Max Mustermann" />);
        expect(screen.getByText('Musterhaus Max Mustermann')).toBeInTheDocument();
        expect(screen.getByText(/geparkt/)).toBeInTheDocument();
        await userEvent.click(await screen.findByRole('button', { name: /Würth/ }));
        await waitFor(() => expect(mock.submit).toHaveBeenCalledWith(form));
        const [url, init] = fetchMock.mock.calls.find(([u]) => String(u).endsWith('/start'))!;
        expect(url).toBe('/api/ids/punchout/203/start');
        expect(init.method).toBe('POST');
        expect(init.headers).toEqual({ 'Content-Type': 'application/json' });
        expect(JSON.parse(init.body)).toEqual({ projektId: 7, projektName: 'Musterhaus Max Mustermann' });
        expect(onClose).toHaveBeenCalled();
    });

    it('startet ohne Projekt wie bisher ohne Body und ohne Hinweis', async () => {
        render(<IdsLieferantenAuswahlModal isOpen onClose={vi.fn()} />);
        expect(screen.queryByText(/geparkt/)).not.toBeInTheDocument();
        await userEvent.click(await screen.findByRole('button', { name: /Würth/ }));
        await waitFor(() => expect(mock.submit).toHaveBeenCalled());
        const [, init] = fetchMock.mock.calls.find(([u]) => String(u).endsWith('/start'))!;
        expect(init).toEqual({ method: 'POST' });
    });

    it('meldet eine abgelehnte Projektzuordnung per Toast', async () => {
        fetchMock.mockImplementation(async (url: string) => url === '/api/ids/lieferanten'
            ? new Response(JSON.stringify([{ id: 203, name: 'Würth' }]))
            : new Response(JSON.stringify({ message: 'x' }), { status: 400 }));
        render(<IdsLieferantenAuswahlModal isOpen onClose={vi.fn()} projektId={7} />);
        expect(screen.getByText('#7')).toBeInTheDocument();
        await userEvent.click(await screen.findByRole('button', { name: /Würth/ }));
        await waitFor(() => expect(mock.toast.error).toHaveBeenCalledWith('Das Projekt konnte dem Warenkorb nicht zugeordnet werden.'));
        expect(mock.submit).not.toHaveBeenCalled();
    });
});
