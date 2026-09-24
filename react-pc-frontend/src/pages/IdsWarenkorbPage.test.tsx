import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';

const mock = vi.hoisted(() => ({ toast: { error: vi.fn(), info: vi.fn(), warning: vi.fn(), success: vi.fn() } }));
vi.mock('../components/ui/toast', () => ({ useToast: () => mock.toast }));
vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: { children: ReactNode; actions?: ReactNode }) => <main>{actions}{children}</main> }));

import IdsWarenkorbPage from './IdsWarenkorbPage';

const item = { article: 'DEMO-001', name: 'Dummy Schraube', quantity: 2, unit: 'Stück', netPrice: 1, priceBasis: 1 };
const mitProjekt = { id: 'a1', number: 'B-2026-000001', ordered: false, reference: '', updatedAt: '2026-09-24T10:00:00Z', items: [item], projektId: 7, projektName: 'Musterhaus Max Mustermann' };
const ohneProjekt = { id: 'b2', number: 'B-2026-000002', ordered: false, reference: '', updatedAt: '2026-09-24T10:00:00Z', items: [item] };
const renderPage = (pfad: string) => render(<MemoryRouter initialEntries={[pfad]}><Routes><Route path="/bestellungen/ids/:id?" element={<IdsWarenkorbPage />} /></Routes></MemoryRouter>);

beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(url.endsWith('/a1') ? mitProjekt : url.endsWith('/b2') ? ohneProjekt : [mitProjekt, ohneProjekt]))));
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('Projekt an Shop-Warenkörben', () => {
    it('zeigt das Projekt in der Liste verlinkt auf den Projektbedarf', async () => {
        renderPage('/bestellungen/ids');
        const projekt = await screen.findByRole('link', { name: 'Projekt: Musterhaus Max Mustermann' });
        expect(projekt).toHaveAttribute('href', '/bestellungen/bedarf/projekt/7');
        expect(screen.getByRole('link', { name: 'Würth · B-2026-000001' })).toHaveAttribute('href', '/bestellungen/ids/a1');
        expect(screen.getAllByRole('link', { name: /^Projekt:/ })).toHaveLength(1);
    });

    it('führt im Detail eines Projekt-Warenkorbs zurück zum Projektbedarf', async () => {
        renderPage('/bestellungen/ids/a1');
        expect(await screen.findByRole('link', { name: 'Zurück zum Projektbedarf' })).toHaveAttribute('href', '/bestellungen/bedarf/projekt/7');
        expect(screen.getByRole('link', { name: 'Projekt: Musterhaus Max Mustermann' })).toBeInTheDocument();
        expect(screen.getByText('Am Projekt geparkt – noch nicht bestellt')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Bei Würth bestellen/ })).toBeInTheDocument();
    });

    it('rechnet NetPrice als Netto-Betrag der ganzen Position (wie der Würth-Shop)', async () => {
        // Würth-Rückgabe: Fixanker 50 Stück à 1,94 € = 97,00 € bei PriceBasis 1; Schrauben 100 Stück = 10,77 € bei PriceBasis 100.
        const warenkorb = { ...mitProjekt, currency: 'EUR', items: [
            { article: '5928410010 50', name: 'Fixanker', quantity: 50, unit: 'Stück', netPrice: 97, priceBasis: 1 },
            { article: '009802610 100', name: 'Schraube', quantity: 100, unit: 'Stück', netPrice: 10.77, priceBasis: 100 },
        ] };
        vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(warenkorb))));
        renderPage('/bestellungen/ids/a1');
        expect(await screen.findByText('Warenwert netto: 107,77 EUR')).toBeInTheDocument();
        expect(screen.getByText('97,00 EUR')).toBeInTheDocument();
        expect(screen.getByText('1,94 EUR')).toBeInTheDocument();
        expect(screen.getByText('10,77 EUR')).toBeInTheDocument();
        expect(screen.getByText('0,1077 EUR')).toBeInTheDocument();
    });

    it('behält ohne Projekt den bisherigen Zurück-Link', async () => {
        renderPage('/bestellungen/ids/b2');
        expect(await screen.findByRole('link', { name: 'Alle Shop-Warenkörbe' })).toHaveAttribute('href', '/bestellungen/ids');
        expect(screen.queryByRole('link', { name: /^Projekt:/ })).not.toBeInTheDocument();
    });

    it('lädt beim Fokus still nach: kein Skeleton, kein Toast, der Warenkorb bleibt sichtbar', async () => {
        let aufrufe = 0;
        vi.stubGlobal('fetch', vi.fn(async () => ++aufrufe === 1 ? new Response(JSON.stringify(mitProjekt)) : new Response('<html>Fehler</html>', { status: 502 })));
        renderPage('/bestellungen/ids/a1');
        expect(await screen.findByText('Dummy Schraube')).toBeInTheDocument();
        await act(async () => { window.dispatchEvent(new Event('focus')); });
        expect(screen.queryByText('Warenkorb wird geladen …')).not.toBeInTheDocument();
        await waitFor(() => expect(aufrufe).toBe(2));
        expect(screen.getByText('Dummy Schraube')).toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        expect(mock.toast.error).not.toHaveBeenCalled();
    });

    it('meldet eine Sende-Fehlerantwort ohne JSON auf Deutsch statt mit SyntaxError', async () => {
        vi.stubGlobal('fetch', vi.fn(async (_url: string, init?: RequestInit) => init?.method === 'POST'
            ? new Response('<html>Bad Gateway</html>', { status: 502, headers: { 'content-type': 'text/html' } })
            : new Response(JSON.stringify(mitProjekt))));
        renderPage('/bestellungen/ids/a1');
        const senden = await screen.findByRole('button', { name: /Bei Würth bestellen/ });
        expect(senden).toHaveClass('bg-rose-600', 'border', 'border-rose-600');
        await userEvent.click(senden);
        await waitFor(() => expect(mock.toast.error).toHaveBeenCalledWith('Warenkorb konnte nicht an Würth übergeben werden.'));
    });

    it('übernimmt die Meldung aus einer JSON-Fehlerantwort beim Senden', async () => {
        vi.stubGlobal('fetch', vi.fn(async (_url: string, init?: RequestInit) => init?.method === 'POST'
            ? new Response(JSON.stringify({ message: 'Dieser Warenkorb wurde bereits bestellt.' }), { status: 409 })
            : new Response(JSON.stringify(mitProjekt))));
        renderPage('/bestellungen/ids/a1');
        await userEvent.click(await screen.findByRole('button', { name: /Bei Würth bestellen/ }));
        await waitFor(() => expect(mock.toast.error).toHaveBeenCalledWith('Dieser Warenkorb wurde bereits bestellt.'));
    });
});
